//! Guest-side runtime behind the C ABI: one handle per fake evdev fd.
//!
//! **Handles.** A fixed table (no allocation, nothing ever freed back to libc, no constructors or
//! destructors: everything is a const-initialised static) of `MAX_HANDLES` entries. A handle value
//! is `generation << 8 | index`, so a stale handle is refused with EBADF. Every operation takes a
//! reference on the entry for its whole duration, so `detach` from another thread never frees
//! state under a reader; the last reference frees it. The per-entry lock is only ever held for
//! short, non-blocking work, never while sleeping and never across a call back into the host.
//!
//! **The guest fd** is one end of an AF_UNIX stream socketpair. It carries no data a program could
//! mistake for input: it is used purely as a level-triggered readiness flag. Exactly one byte is
//! queued on it while this handle has undelivered events, and none otherwise, so a plain kernel
//! `poll`/`select`/`epoll` on it works without any help from the interposer. When the controller
//! is unplugged the peer end is closed, which the kernel reports as POLLHUP. A raw `read` of the
//! fd (say, by a program that inherited it across `exec`) returns at most one byte, never anything
//! shaped like an `input_event`.
//!
//! **Wake-up.** The writer bumps a futex word (the "bell") in the shared file and wakes it on every
//! publish. A blocked `hermes_read` sleeps on that bell directly. For native readiness, one
//! helper thread per slot (started lazily, all signals blocked) sleeps on the bell and re-levels
//! the readiness byte of every handle of that slot when it rings; it also re-checks every 4 ms in
//! case a wake is ever missed. Every 500 ms it checks whether the path now names a different file
//! and whether the writer process is still alive (it holds an flock on the file).
//!
//! **fork.** A pthread_atfork child handler resets every lock, forgets the helper threads (they do
//! not exist in the child; the next call into the library restarts them), and gives each open
//! handle a fresh socketpair dup3()ed onto the same fd number, so parent and child keep the same
//! fd numbers with independent readiness and independent read positions. The child's first read
//! is a full-state frame. The force-feedback engine starts clean in the child (no effects).
//!
//! **Force feedback** (API 3, `crate::ff`): the `ff_*` calls below take the handle's slot and mode
//! (captured when it attaches: HERMES_DINPUT_FF, else the control block's ff_mode) under the
//! entry lock and hand the call to the process's engine.

use core::cell::UnsafeCell;
use core::ffi::{c_char, c_int, c_void};
use core::ptr;
use core::sync::atomic::Ordering::{AcqRel, Acquire, Relaxed, Release, SeqCst};
use core::sync::atomic::{AtomicU32, AtomicU64, AtomicUsize};

use crate::abi::*;
use crate::ff;
use crate::frame::{Cursor, Due, Ev, Frame, FrameStats, Journal, NoJournal};
use crate::layout::{self, ControlState, Identity, Shared, Snapshot, FILE_SIZE};
use crate::msg::Msg;
use crate::sys;

pub const MAX_HANDLES: usize = 128;
pub const MAX_SLOTS: usize = 4;
pub const PATH_CAP: usize = 1024;
pub const ENV_PATHS: &[u8] = b"FAKE_EVDEV_MEMFD_PATHS\0";
pub const ENV_AXIS_HZ: &[u8] = b"HERMES_AXIS_HZ\0";
pub const ENV_AXIS_AUTO_MAX: &[u8] = b"HERMES_AXIS_AUTO_MAX\0";
/// Auto mode never sends stick-only frames faster than this per handle, whatever the game's frame
/// rate (HERMES_AXIS_AUTO_MAX, clamped to AUTO_MAX_MIN..=AUTO_MAX_MAX): some games draw far faster
/// than they read input and queue every report.
pub const AUTO_MAX_DEFAULT: u32 = 60;
pub const AUTO_MAX_MIN: u32 = 20;
pub const AUTO_MAX_MAX: u32 = 240;
/// `HERMES_AXIS_HZ` value meaning "follow the game's frame rate" (unset, or `auto`).
pub const AXIS_AUTO: u32 = u32::MAX;
pub const AXIS_HZ_MIN: u32 = 30;
pub const AXIS_HZ_MAX: u32 = 1000;
/// Auto pacing sends one stick-only frame per new game frame (a frame credit), and at this fixed
/// rate while there is no fresh frame feed (menus, a client UI, a GDI app, a session without the
/// feed, an older app).
pub const AUTO_FALLBACK_HZ: u64 = 60;
/// A last frame older than this means the feed is not fresh.
pub const AUTO_STALE_NS: u64 = 250_000_000;
/// A change held for a frame credit goes out anyway after max(2 frame intervals, this): a game
/// that freezes still gets the rest position.
pub const AUTO_STALL_MIN_NS: u64 = 50_000_000;
/// 2: the control block (live pacing, controller identity: `identity`, `slot_identity`).
/// 3: force feedback (`ff_*`).
pub const API_VERSION: u32 = 3;

/// Upper bound on how long a missed wake could delay input while a slot is active (the
/// fallback re-check).
const FALLBACK_NS: u64 = 4_000_000;
/// A slot is idle once nothing has been published and nothing held for this long...
const IDLE_AFTER_NS: u64 = 250_000_000;
/// ...and then its wake thread (and a blocked read) sleeps on the bell with this timeout. Every
/// publish, game frame a reader waits for, retirement and detach rings the bell, so the timeout is
/// not needed for input; it only paces the writer-liveness / file-replacement check (a crashed
/// writer cannot ring), which runs on those wakes.
const IDLE_SLEEP_NS: u64 = 1_000_000_000;
/// See deliver(): how late a ceiling-paced stick frame may be and still keep the grid.
const CEILING_SLACK_NS: u64 = 1_000_000;
/// Cadence of path / writer-liveness checks and of re-attach attempts for orphaned handles.
const SLOW_NS: u64 = 500_000_000;
const SNAP_ATTEMPTS: u32 = 64;
const HELPER_STACK: usize = 256 * 1024;

// ---------------------------------------------------------------------------------------------
// configuration (FAKE_EVDEV_MEMFD_PATHS)

struct Config {
    state: AtomicU32, // 0 = not parsed, 1 = parsing, 2 = ready
    mask: AtomicU32,
    paths: UnsafeCell<[[u8; PATH_CAP]; MAX_SLOTS]>,
}
unsafe impl Sync for Config {}

static CONFIG: Config = Config {
    state: AtomicU32::new(0),
    mask: AtomicU32::new(0),
    paths: UnsafeCell::new([[0; PATH_CAP]; MAX_SLOTS]),
};

pub(crate) fn trim(mut s: &[u8]) -> &[u8] {
    while let [b' ' | b'\t' | b'\n' | b'\r', rest @ ..] = s {
        s = rest;
    }
    while let [rest @ .., b' ' | b'\t' | b'\n' | b'\r'] = s {
        s = rest;
    }
    s
}

/// Parses `<slot>=<path>[;<slot>=<path>...]`. Returns the mask of slots configured; malformed
/// entries are skipped, the first entry for a slot wins.
pub fn parse_paths(s: &[u8], out: &mut [[u8; PATH_CAP]; MAX_SLOTS]) -> u32 {
    let mut mask = 0u32;
    for entry in s.split(|c| *c == b';') {
        let entry = trim(entry);
        let eq = match entry.iter().position(|c| *c == b'=') {
            Some(e) => e,
            None => continue,
        };
        let key = trim(&entry[..eq]);
        let path = trim(&entry[eq + 1..]);
        if key.len() != 1 || !(b'0'..=b'3').contains(&key[0]) {
            continue;
        }
        let slot = (key[0] - b'0') as usize;
        if path.is_empty() || path.len() >= PATH_CAP || path.contains(&0) || mask & (1 << slot) != 0 {
            continue;
        }
        out[slot][..path.len()].copy_from_slice(path);
        out[slot][path.len()] = 0;
        mask |= 1 << slot;
    }
    mask
}

/// Lazy, idempotent, safe before constructors and after destructors (static data only).
pub fn init() -> u32 {
    let mut spins = 0u32;
    loop {
        match CONFIG.state.load(Acquire) {
            2 => return CONFIG.mask.load(Relaxed),
            0 => {
                if CONFIG.state.compare_exchange(0, 1, Acquire, Relaxed).is_ok() {
                    let paths = unsafe { &mut *CONFIG.paths.get() };
                    let mask = match sys::env(ENV_PATHS) {
                        Some(v) => parse_paths(v, paths),
                        None => 0,
                    };
                    CONFIG.mask.store(mask, Relaxed);
                    AUTO_MAX.store(parse_auto_max(sys::env(ENV_AXIS_AUTO_MAX)), Relaxed);
                    let hz = parse_axis_hz(sys::env(ENV_AXIS_HZ));
                    AXIS_HZ.store(hz, Relaxed);
                    let ns = if hz == 0 || hz == AXIS_AUTO { 0 } else { 1_000_000_000 / hz as u64 };
                    AXIS_NS.store(ns, Relaxed);
                    FF_ENV.store(ff::parse_env_mode(sys::env(ff::ENV_FF)), Relaxed);
                    ff::set_port(ff::parse_port(sys::env(ff::ENV_PORT)));
                    CONFIG.state.store(2, Release);
                    return mask;
                }
            }
            _ => {
                spins += 1;
                if spins > 200_000 {
                    return 0; // re-entered from a signal handler mid-parse: report nothing
                }
                if spins % 64 == 0 {
                    sys::sched_yield();
                } else {
                    core::hint::spin_loop();
                }
            }
        }
    }
}

/// `HERMES_AXIS_HZ`: unset, `auto` or unparsable = AXIS_AUTO; 0 = unpaced; a number = that
/// fixed rate, clamped to AXIS_HZ_MIN..=AXIS_HZ_MAX.
pub fn parse_axis_hz(v: Option<&[u8]>) -> u32 {
    let v = match v {
        Some(v) => trim(v),
        None => return AXIS_AUTO,
    };
    if v.is_empty() || v.len() > 9 || !v.iter().all(|c| c.is_ascii_digit()) {
        return AXIS_AUTO; // "auto", or anything else that is not a number
    }
    let n = v.iter().fold(0u32, |n, c| n * 10 + (c - b'0') as u32);
    if n == 0 {
        0
    } else {
        n.clamp(AXIS_HZ_MIN, AXIS_HZ_MAX)
    }
}

/// `HERMES_AXIS_AUTO_MAX`: unset or not a number = AUTO_MAX_DEFAULT, else clamped to
/// AUTO_MAX_MIN..=AUTO_MAX_MAX.
pub fn parse_auto_max(v: Option<&[u8]>) -> u32 {
    let v = match v {
        Some(v) => trim(v),
        None => return AUTO_MAX_DEFAULT,
    };
    if v.is_empty() || v.len() > 9 || !v.iter().all(|c| c.is_ascii_digit()) {
        return AUTO_MAX_DEFAULT;
    }
    v.iter().fold(0u32, |n, c| n * 10 + (c - b'0') as u32).clamp(AUTO_MAX_MIN, AUTO_MAX_MAX)
}

static AUTO_MAX: AtomicU32 = AtomicU32::new(AUTO_MAX_DEFAULT);

/// Auto pacing at `now`, from a slot file's frame feed: Some((frame count, stall bound)) while the
/// feed is fresh, None when it is not (the caller falls back to AUTO_FALLBACK_HZ).
pub fn credit_feed(count: u64, frame_time: u64, interval: u64, now: u64) -> Option<(u64, u64)> {
    if count == 0 || frame_time == 0 || now.saturating_sub(frame_time) > AUTO_STALE_NS {
        return None;
    }
    Some((count, interval.saturating_mul(2).max(AUTO_STALL_MIN_NS)))
}

static AXIS_HZ: AtomicU32 = AtomicU32::new(AXIS_AUTO);
static AXIS_NS: AtomicU64 = AtomicU64::new(0);
static CONFIG_LOGGED: AtomicU32 = AtomicU32::new(0);

/// The pacing a control block last set in this process (EFF_SET = 0: none, the env is in force).
/// The app writes the same session settings into every slot, so the last one applied is the
/// session's.
static EFF_SET: AtomicU32 = AtomicU32::new(0);
static EFF_HZ: AtomicU32 = AtomicU32::new(AXIS_AUTO);
static EFF_AUTO_MAX: AtomicU32 = AtomicU32::new(AUTO_MAX_DEFAULT);

/// The stick pacing in force: 0 = unpaced, AXIS_AUTO, or a fixed rate. The app's control block
/// when it sets one, else HERMES_AXIS_HZ.
pub fn axis_config() -> u32 {
    init();
    if EFF_SET.load(Acquire) != 0 {
        EFF_HZ.load(Relaxed)
    } else {
        AXIS_HZ.load(Relaxed)
    }
}

/// The auto ceiling in force (control block, else HERMES_AXIS_AUTO_MAX).
pub fn auto_max() -> u32 {
    init();
    if EFF_SET.load(Acquire) != 0 {
        EFF_AUTO_MAX.load(Relaxed)
    } else {
        AUTO_MAX.load(Relaxed)
    }
}

/// Pacing from a control record: (axis config as in `axis_config`, auto ceiling), or None for
/// axis_mode 0 / unknown (the environment decides, exactly as without a control block). Every
/// value is clamped: the file is not trusted.
pub fn control_pacing(st: &ControlState) -> Option<(u32, u32)> {
    let max = match st.axis_auto_max {
        0 => AUTO_MAX_DEFAULT,
        m => m.clamp(AUTO_MAX_MIN, AUTO_MAX_MAX),
    };
    match st.axis_mode {
        layout::CTL_AXIS_AUTO => Some((AXIS_AUTO, max)),
        layout::CTL_AXIS_FIXED => Some((st.axis_hz.clamp(AXIS_HZ_MIN, AXIS_HZ_MAX), max)),
        layout::CTL_AXIS_UNPACED => Some((0, max)),
        _ => None,
    }
}

fn axis_ns(hz: u32) -> u64 {
    if hz == 0 || hz == AXIS_AUTO {
        0
    } else {
        1_000_000_000 / hz as u64
    }
}

/// A handle's pacing back to the environment's (attach, or a new file before its control block
/// is read).
fn env_cfg(d: &mut Data) {
    d.cfg_hz = AXIS_HZ.load(Relaxed);
    d.cfg_ns = AXIS_NS.load(Relaxed);
    d.cfg_auto_max = AUTO_MAX.load(Relaxed);
}

/// Pick up a changed control record (one atomic load when nothing changed). Called before every
/// pacing decision; never blocks: a record the writer keeps busy is skipped (the last good one, or
/// the environment, stays in force) and retried at the next decision.
fn apply_control(d: &mut Data, notes: &mut Notes) {
    let sh = match shared(d) {
        Some(sh) => sh,
        None => return,
    };
    if d.ctl_inst != d.instance {
        // Another file (re-attach): its gens are its own. Start from the environment.
        d.ctl_inst = d.instance;
        d.ctl_gen = 0;
        env_cfg(d);
    }
    if !sh.control_present() {
        return; // format <= 1.4, or no valid block: behave exactly as before control blocks
    }
    if sh.ctl.gen.load(Acquire) == d.ctl_gen {
        return;
    }
    let (gen, st) = match sh.read_control() {
        Some(r) => r,
        None => {
            STATS.ctl_busy.fetch_add(1, Relaxed);
            return;
        }
    };
    d.ctl_gen = gen;
    match control_pacing(&st) {
        Some((hz, max)) => {
            d.cfg_hz = hz;
            d.cfg_ns = axis_ns(hz);
            d.cfg_auto_max = max;
            EFF_HZ.store(hz, Relaxed);
            EFF_AUTO_MAX.store(max, Relaxed);
            EFF_SET.store(1, Release);
        }
        None => {
            env_cfg(d);
            EFF_SET.store(0, Release);
        }
    }
    STATS.ctl_applied.fetch_add(1, Relaxed);
    if d.writable {
        sh.ctl.r_ctl_gen_applied.store(gen, Relaxed);
    }
    // One log line per gen per slot and file (not one per handle).
    let key = d.instance.wrapping_mul(0x9E37_79B9_7F4A_7C15) ^ gen as u64;
    if SLOTS.s[d.slot].ctl_logged.swap(key, AcqRel) != key {
        notes.ctl = Some((gen, st.axis_mode, d.cfg_hz, d.cfg_auto_max));
    }
}

/// The stick-frame rate in force: the fixed rate, 0 when unpaced, or in auto mode the rate a
/// reader in this process last derived (the fallback rate before any).
pub fn axis_hz() -> u32 {
    match axis_config() {
        AXIS_AUTO => match STATS.axis_hz.load(Relaxed) {
            0 => AUTO_FALLBACK_HZ as u32,
            hz => hz as u32,
        },
        hz => hz,
    }
}

/// How a handle paces its stick-only frames right now.
#[derive(Clone, Copy)]
enum Pace {
    /// At most one per interval (fixed N Hz, the 60 Hz fallback, or 0 = unpaced).
    Interval(u64),
    /// One per new game frame and at least `min` ns apart (the auto ceiling): `count` is the
    /// slot file's frame counter, `stall` how long a held change may wait for the next frame.
    Credit { count: u64, stall: u64, min: u64 },
}

/// Record how this handle paces (r_axis_mode / r_axis_hz, process stats) when that changes.
fn note_mode(d: &mut Data, sh: Option<&Shared>, mode: u32, hz: u32) {
    let fed = d.pace_mode == layout::AXIS_MODE_CREDITS || d.pace_mode == layout::AXIS_MODE_CEILING;
    if fed && mode == layout::AXIS_MODE_FALLBACK {
        STATS.auto_fallbacks.fetch_add(1, Relaxed);
        if d.writable {
            if let Some(sh) = sh {
                sh.header.r_auto_fallbacks.fetch_add(1, Relaxed);
            }
        }
    }
    // The frame rate jitters by a hertz or so; do not rewrite the shared line for that.
    if mode == d.pace_mode && hz.abs_diff(d.pace_hz) <= 1 && d.pace_max == d.cfg_auto_max {
        return;
    }
    d.pace_mode = mode;
    d.pace_hz = hz;
    d.pace_max = d.cfg_auto_max;
    STATS.axis_mode.store(mode as u64, Relaxed);
    STATS.axis_hz.store(hz as u64, Relaxed);
    if d.writable {
        if let Some(sh) = sh {
            sh.header.r_axis_mode.store(mode, Relaxed);
            sh.header.r_axis_hz.store(hz, Relaxed);
            sh.header.r_auto_max.store(d.cfg_auto_max, Relaxed);
        }
    }
}

/// This handle's pacing at `now`: the configured fixed rate, or in auto mode frame credits from
/// the slot file's feed while it is fresh, else the fixed fallback. "Configured" = the control
/// block's setting when it has one (applied by `refresh` before each decision), else the env.
fn pacing(d: &mut Data, now: u64) -> Pace {
    let cfg = d.cfg_hz;
    let sh = match shared(d) {
        Some(sh) if !d.dead => Some(sh),
        _ => None,
    };
    if cfg != AXIS_AUTO {
        let mode = if cfg == 0 { layout::AXIS_MODE_UNPACED } else { layout::AXIS_MODE_FIXED };
        note_mode(d, sh, mode, cfg);
        return Pace::Interval(d.cfg_ns);
    }
    let feed = sh.and_then(|sh| {
        let h = &sh.header;
        let count = h.frame_count.load(Acquire);
        credit_feed(count, h.frame_time_ns.load(Relaxed), h.frame_interval_ns.load(Relaxed), now)
            .map(|c| (c, h.frame_interval_ns.load(Relaxed)))
    });
    match feed {
        Some(((count, stall), interval)) => {
            let max = d.cfg_auto_max.clamp(AUTO_MAX_MIN, AUTO_MAX_MAX);
            let min = 1_000_000_000 / max as u64;
            // Which limit governs: the game's frames (credits), or the ceiling when it presents
            // faster than that.
            let (mode, hz) = if interval != 0 && interval < min {
                (layout::AXIS_MODE_CEILING, max)
            } else {
                (layout::AXIS_MODE_CREDITS, if interval == 0 { 0 } else { (1_000_000_000 / interval) as u32 })
            };
            note_mode(d, sh, mode, hz);
            Pace::Credit { count, stall, min }
        }
        None => {
            note_mode(d, sh, layout::AXIS_MODE_FALLBACK, AUTO_FALLBACK_HZ as u32);
            Pace::Interval(1_000_000_000 / AUTO_FALLBACK_HZ)
        }
    }
}

/// A pending stick-only frame under frame credits: due once a game frame arrived since this
/// handle's last stick-only frame (credits never accumulate past one) AND at least `min` has passed
/// since that frame (the auto ceiling); a change still waiting for a frame goes anyway after
/// `stall`. Held until then: for the next frame (Frame) or just the ceiling (At).
fn credit_due(d: &mut Data, count: u64, stall: u64, min: u64, now: u64) -> Due {
    // A new frame, or the counter restarted with a new app process.
    let credit = count != d.credit_count;
    let ceiling_at = if d.last_axis_emit == 0 { 0 } else { d.last_axis_emit.wrapping_add(min) };
    if credit && now >= ceiling_at {
        return Due::Now;
    }
    if d.hold_since == 0 {
        d.hold_since = now;
    }
    if credit {
        return Due::At(ceiling_at);
    }
    let deadline = d.hold_since.wrapping_add(stall).max(ceiling_at);
    if now >= deadline {
        Due::Now
    } else {
        Due::Frame(deadline)
    }
}

/// Log the effective pacing once, the first time a logger is there to hear it.
fn log_config_once() {
    if LOGGER.load(Acquire) == 0 || CONFIG.state.load(Acquire) != 2 {
        return;
    }
    if CONFIG_LOGGED.compare_exchange(0, 1, AcqRel, Relaxed).is_err() {
        return;
    }
    let hz = AXIS_HZ.load(Relaxed);
    let mut m = Msg::new();
    if hz == 0 {
        m.s(b"hermes: stick-only frames unpaced (HERMES_AXIS_HZ=0)");
    } else if hz == AXIS_AUTO {
        m.s(b"hermes: stick-only frames paced by frame credits (one per game frame, at most ")
            .u(AUTO_MAX.load(Relaxed) as u64)
            .s(b"/s; ")
            .u(AUTO_FALLBACK_HZ)
            .s(b" Hz without a fresh frame feed); button and full-state frames are never delayed");
    } else {
        m.s(b"hermes: stick-only frames paced to a fixed ")
            .u(hz as u64)
            .s(b" Hz per handle (T = ")
            .u(AXIS_NS.load(Relaxed) / 1000)
            .s(b" us); button and full-state frames are never delayed");
    }
    log(0, &mut m);
}

fn slot_path(slot: usize) -> *const c_char {
    unsafe { (*CONFIG.paths.get())[slot].as_ptr() as *const c_char }
}

// ---------------------------------------------------------------------------------------------
// statistics and logging

pub struct Stats {
    pub attaches: AtomicU64,
    pub detaches: AtomicU64,
    pub reattaches: AtomicU64,
    pub orphaned: AtomicU64,
    pub removals: AtomicU64,
    pub writer_dead: AtomicU64,
    pub frames: AtomicU64,
    pub events: AtomicU64,
    pub taps: AtomicU64,
    pub full_frames: AtomicU64,
    pub journal_frames: AtomicU64,
    pub counter_frames: AtomicU64,
    pub coalesced_taps: AtomicU64,
    pub resyncs: AtomicU64,
    pub helper_wakes: AtomicU64,
    pub helper_timeouts: AtomicU64,
    pub ready_signals: AtomicU64,
    pub snapshot_busy: AtomicU64,
    pub fork_rebinds: AtomicU64,
    pub paced_frames: AtomicU64,
    pub merged_axis_publishes: AtomicU64,
    pub axis_hz: AtomicU64,
    pub auto_fallbacks: AtomicU64,
    pub axis_mode: AtomicU64,
    pub ctl_applied: AtomicU64,
    pub ctl_busy: AtomicU64,
    pub identity_reads: AtomicU64,
    pub ff_uploads: AtomicU64,
    pub ff_plays: AtomicU64,
    pub ff_datagrams: AtomicU64,
    pub ff_send_failures: AtomicU64,
    pub ff_guard_fades: AtomicU64,
}

const Z: AtomicU64 = AtomicU64::new(0);
pub static STATS: Stats = Stats {
    attaches: Z,
    detaches: Z,
    reattaches: Z,
    orphaned: Z,
    removals: Z,
    writer_dead: Z,
    frames: Z,
    events: Z,
    taps: Z,
    full_frames: Z,
    journal_frames: Z,
    counter_frames: Z,
    coalesced_taps: Z,
    resyncs: Z,
    helper_wakes: Z,
    helper_timeouts: Z,
    ready_signals: Z,
    snapshot_busy: Z,
    fork_rebinds: Z,
    paced_frames: Z,
    merged_axis_publishes: Z,
    axis_hz: Z,
    auto_fallbacks: Z,
    axis_mode: Z,
    ctl_applied: Z,
    ctl_busy: Z,
    identity_reads: Z,
    ff_uploads: Z,
    ff_plays: Z,
    ff_datagrams: Z,
    ff_send_failures: Z,
    ff_guard_fades: Z,
};

/// Process-wide reader statistics (`struct hermes_stats`).
#[repr(C)]
#[derive(Clone, Copy, Default, Debug)]
pub struct StatsView {
    pub attaches: u64,
    pub detaches: u64,
    pub reattaches: u64,
    pub orphaned: u64,
    pub removals: u64,
    pub writer_dead: u64,
    pub frames: u64,
    pub events: u64,
    pub taps: u64,
    pub full_frames: u64,
    pub journal_frames: u64,
    pub counter_frames: u64,
    pub coalesced_taps: u64,
    pub resyncs: u64,
    pub helper_wakes: u64,
    pub helper_timeouts: u64,
    pub ready_signals: u64,
    pub snapshot_busy: u64,
    pub fork_rebinds: u64,
    /// Axis-only frames held back by pacing before being delivered.
    pub paced_frames: u64,
    /// Publishes merged into a later frame instead of delivered one each.
    pub merged_axis_publishes: u64,
    /// The stick-frame rate a reader in this process last paced to (in credit mode the game's
    /// frame rate; 0 = none yet).
    pub axis_hz: u64,
    /// Times a reader in auto mode lost a fresh frame feed and fell back to the fixed rate.
    pub auto_fallbacks: u64,
    /// How a reader last paced (AXIS_MODE_*: 1 credits, 2 fallback, 3 fixed, 4 unpaced).
    pub axis_mode: u64,
    /// API 2: control block records applied (pacing changes the app made live).
    pub ctl_applied: u64,
    /// Control reads that found the writer mid-update 64 times running (retried next decision).
    pub ctl_busy: u64,
    /// hermes_identity / hermes_slot_identity calls answered.
    pub identity_reads: u64,
    /// API 3, force feedback: effects uploaded (new or updated), effects started (play with a
    /// count), rumble datagrams sent to the app, sends that failed, anti-buzz guard fades begun.
    pub ff_uploads: u64,
    pub ff_plays: u64,
    pub ff_datagrams: u64,
    pub ff_send_failures: u64,
    pub ff_guard_fades: u64,
}

pub fn stats() -> StatsView {
    let s = &STATS;
    StatsView {
        attaches: s.attaches.load(Relaxed),
        detaches: s.detaches.load(Relaxed),
        reattaches: s.reattaches.load(Relaxed),
        orphaned: s.orphaned.load(Relaxed),
        removals: s.removals.load(Relaxed),
        writer_dead: s.writer_dead.load(Relaxed),
        frames: s.frames.load(Relaxed),
        events: s.events.load(Relaxed),
        taps: s.taps.load(Relaxed),
        full_frames: s.full_frames.load(Relaxed),
        journal_frames: s.journal_frames.load(Relaxed),
        counter_frames: s.counter_frames.load(Relaxed),
        coalesced_taps: s.coalesced_taps.load(Relaxed),
        resyncs: s.resyncs.load(Relaxed),
        helper_wakes: s.helper_wakes.load(Relaxed),
        helper_timeouts: s.helper_timeouts.load(Relaxed),
        ready_signals: s.ready_signals.load(Relaxed),
        snapshot_busy: s.snapshot_busy.load(Relaxed),
        fork_rebinds: s.fork_rebinds.load(Relaxed),
        paced_frames: s.paced_frames.load(Relaxed),
        merged_axis_publishes: s.merged_axis_publishes.load(Relaxed),
        axis_hz: s.axis_hz.load(Relaxed),
        auto_fallbacks: s.auto_fallbacks.load(Relaxed),
        axis_mode: s.axis_mode.load(Relaxed),
        ctl_applied: s.ctl_applied.load(Relaxed),
        ctl_busy: s.ctl_busy.load(Relaxed),
        identity_reads: s.identity_reads.load(Relaxed),
        ff_uploads: s.ff_uploads.load(Relaxed),
        ff_plays: s.ff_plays.load(Relaxed),
        ff_datagrams: s.ff_datagrams.load(Relaxed),
        ff_send_failures: s.ff_send_failures.load(Relaxed),
        ff_guard_fades: s.ff_guard_fades.load(Relaxed),
    }
}

/// Counters kept in a slot file, summed over its writer and every reader (`struct hermes_slot_stats`).
#[repr(C)]
#[derive(Clone, Copy, Default, Debug)]
pub struct SlotStatsView {
    pub instance: u64,
    pub publishes: u64,
    pub wake_calls: u64,
    pub woken: u64,
    pub unplugs: u64,
    pub edges: u64,
    pub reader_attaches: u64,
    pub reader_reattaches: u64,
    pub reader_taps: u64,
    pub reader_coalesced: u64,
    pub reader_resyncs: u64,
    pub reader_frames: u64,
    pub reader_events: u64,
    pub reader_removals: u64,
    pub writer_pid: u32,
    pub retired: u32,
    pub reader_paced_frames: u64,
    pub reader_merged_axis_publishes: u64,
    pub reader_axis_hz: u64,
    pub reader_auto_fallbacks: u64,
    pub frame_interval_ns: u64,
    pub frame_time_ns: u64,
    pub frame_count: u64,
    pub reader_axis_mode: u64,
    pub frame_waiters: u64,
    pub reader_helper_wakes: u64,
    pub reader_auto_max: u64,
    /// Format 1.5 (zero in older files): the file's control gen, and the reader stats beside it.
    pub control_gen: u64,
    pub reader_ctl_gen_applied: u64,
    pub reader_id_gen_attached: u64,
    pub reader_identity_reads: u64,
    /// Format 1.6 (zero in older files): force feedback, summed over every reader process.
    pub reader_ff_uploads: u64,
    pub reader_ff_plays: u64,
    pub reader_ff_datagrams: u64,
    pub reader_ff_guard_fades: u64,
}

pub type LogFn = unsafe extern "C" fn(level: c_int, line: *const c_char);
static LOGGER: AtomicUsize = AtomicUsize::new(0);

pub fn set_logger(f: Option<LogFn>) {
    LOGGER.store(f.map_or(0, |f| f as usize), Release);
    log_config_once();
}

pub(crate) fn log(level: c_int, m: &mut Msg) {
    let f = LOGGER.load(Acquire);
    if f != 0 {
        let f: LogFn = unsafe { core::mem::transmute::<usize, LogFn>(f) };
        unsafe { f(level, m.c_str()) };
    }
}

// ---------------------------------------------------------------------------------------------
// mappings

#[derive(Clone, Copy)]
struct Mapped {
    ptr: *mut u8,
    fd: i32,
    dev: u64,
    ino: u64,
    instance: u64,
    writable: bool,
}

impl Mapped {
    const NONE: Mapped = Mapped { ptr: ptr::null_mut(), fd: -1, dev: 0, ino: 0, instance: 0, writable: false };

    fn shared<'a>(&self) -> Option<&'a Shared> {
        if self.ptr.is_null() {
            None
        } else {
            Some(unsafe { Shared::at(self.ptr) })
        }
    }

    fn release(&mut self) {
        sys::munmap(self.ptr, FILE_SIZE);
        sys::close(self.fd);
        *self = Mapped::NONE;
    }
}

/// Map the file `FAKE_EVDEV_MEMFD_PATHS` names for `slot`, if it is a live format-1 file for that
/// slot. The size is checked before mapping, so a short or foreign file is refused, never touched.
fn map_slot(slot: usize, keep_fd: bool) -> Result<Mapped, i32> {
    let path = slot_path(slot);
    let (fd, writable) = match sys::openat(path, sys::O_RDWR | sys::O_CLOEXEC, 0) {
        Ok(fd) => (fd, true),
        Err(_) => (sys::openat(path, sys::O_RDONLY | sys::O_CLOEXEC, 0)?, false),
    };
    let st = match sys::fstat(fd) {
        Ok(s) => s,
        Err(e) => {
            sys::close(fd);
            return Err(e);
        }
    };
    if !st.is_regular() || st.size < FILE_SIZE as i64 {
        sys::close(fd);
        return Err(sys::EINVAL);
    }
    let prot = sys::PROT_READ | if writable { sys::PROT_WRITE } else { 0 };
    let p = match sys::mmap(FILE_SIZE, prot, sys::MAP_SHARED, fd) {
        Ok(p) => p,
        Err(e) => {
            sys::close(fd);
            return Err(e);
        }
    };
    let sh = unsafe { Shared::at(p) };
    if !sh.valid_for(slot as u32) || sh.is_retired() {
        sys::munmap(p, FILE_SIZE);
        sys::close(fd);
        return Err(sys::EINVAL);
    }
    let instance = sh.header.instance.load(Relaxed);
    let fd = if keep_fd {
        fd
    } else {
        sys::close(fd);
        -1
    };
    Ok(Mapped { ptr: p, fd, dev: st.dev, ino: st.ino, instance, writable })
}

// ---------------------------------------------------------------------------------------------
// per-slot runtime (helper thread state)

struct SlotRt {
    helper: AtomicU32, // 0 = not running, 1 = running, 2 = start failed (retry later)
    retry_at: AtomicU64,
    file_gen: AtomicU32,
    instance: AtomicU64,
    dead_instance: AtomicU64,
    handles: AtomicU32,
    kick: AtomicU32,
    /// Consecutive slow checks that found the writer's lock free (helper-owned).
    dead_votes: AtomicU32,
    /// This process's helper counts itself in hm's frame_waiters (helper-owned).
    frame_reg: AtomicU32,
    /// The (file, control gen) last logged for this slot: one line per applied gen.
    ctl_logged: AtomicU64,
    hm: UnsafeCell<Mapped>, // owned by the slot's helper thread
}

struct SlotTable {
    s: [SlotRt; MAX_SLOTS],
}
unsafe impl Sync for SlotTable {}

const SLOT_RT: SlotRt = SlotRt {
    helper: AtomicU32::new(0),
    retry_at: AtomicU64::new(0),
    file_gen: AtomicU32::new(0),
    instance: AtomicU64::new(0),
    dead_instance: AtomicU64::new(0),
    handles: AtomicU32::new(0),
    kick: AtomicU32::new(0),
    dead_votes: AtomicU32::new(0),
    frame_reg: AtomicU32::new(0),
    ctl_logged: AtomicU64::new(0),
    hm: UnsafeCell::new(Mapped::NONE),
};
static SLOTS: SlotTable = SlotTable { s: [SLOT_RT; MAX_SLOTS] };

/// A word nobody ever changes: sleeping on it is a plain timed sleep.
static IDLE: AtomicU32 = AtomicU32::new(0);
static ATFORK: AtomicU32 = AtomicU32::new(0);

// ---------------------------------------------------------------------------------------------
// handle table

const FREE: u32 = 0;
const ALLOC: u32 = 1;
const ACTIVE: u32 = 2;
const CLOSED: u32 = 3;
const FREEING: u32 = 4;

struct Data {
    slot: usize,
    guest_fd: i32,
    drain_fd: i32,
    wake_fd: i32,
    map: *mut u8,
    writable: bool,
    instance: u64,
    map_gen: u32,
    base_pending: bool,
    unplug_base: u32,
    removed: bool,
    dead: bool,
    last_retry: u64,
    cursor: Cursor,
    frame: Frame,
    fpos: usize,
    ftime: (i64, i64),
    ready: bool,
    pace_mode: u32,
    pace_hz: u32,
    /// Blocking reads: when this handle last saw input or a publish, and the bell it last saw.
    last_active: u64,
    last_bell: u32,
    /// Frame counter value when this handle last sent a stick-only frame under frame credits.
    credit_count: u64,
    /// When the pending stick change started waiting (0 = not waiting).
    hold_since: u64,
    /// When this handle last sent a stick-only frame under frame credits (the ceiling's clock).
    last_axis_emit: u64,
    /// The auto ceiling last recorded by note_mode.
    pace_max: u32,
    /// Pacing in force for this handle: the axis config (as `axis_config`), its interval, and the
    /// auto ceiling. The environment's, or the control block's once one is applied.
    cfg_hz: u32,
    cfg_ns: u64,
    cfg_auto_max: u32,
    /// The file instance and control gen those came from (0 = none read yet).
    ctl_inst: u64,
    ctl_gen: u32,
    /// The controller identity this handle reports, captured when it attached to its current
    /// file (never changed under the open handle), and the control gen it came from.
    id: Option<Identity>,
    id_gen: u32,
    /// Force feedback: mode On (every effect type) or Off (rumble only), captured with the
    /// identity; fixed once the guest made an ff call (it has seen the capabilities by then).
    ff_on: bool,
    ff_fixed: bool,
    /// This handle uploaded effects (detach erases them).
    ff_owner: bool,
}

impl Data {
    const EMPTY: Data = Data {
        slot: 0,
        guest_fd: -1,
        drain_fd: -1,
        wake_fd: -1,
        map: ptr::null_mut(),
        writable: false,
        instance: 0,
        map_gen: 0,
        base_pending: false,
        unplug_base: 0,
        removed: false,
        dead: false,
        last_retry: 0,
        cursor: Cursor::new(),
        frame: Frame::new(),
        fpos: 0,
        ftime: (0, 0),
        ready: false,
        pace_mode: layout::AXIS_MODE_UNKNOWN,
        pace_hz: 0,
        last_active: 0,
        last_bell: 0,
        credit_count: 0,
        hold_since: 0,
        last_axis_emit: 0,
        pace_max: 0,
        cfg_hz: AXIS_AUTO,
        cfg_ns: 0,
        cfg_auto_max: AUTO_MAX_DEFAULT,
        ctl_inst: 0,
        ctl_gen: 0,
        id: None,
        id_gen: 0,
        ff_on: true,
        ff_fixed: false,
        ff_owner: false,
    };
}

struct Entry {
    state: AtomicU32,
    gen: AtomicU32,
    refs: AtomicU32,
    lock: AtomicU32,
    data: UnsafeCell<Data>,
}

struct Table {
    e: [Entry; MAX_HANDLES],
}
unsafe impl Sync for Table {}

const ENTRY: Entry = Entry {
    state: AtomicU32::new(FREE),
    gen: AtomicU32::new(0),
    refs: AtomicU32::new(0),
    lock: AtomicU32::new(0),
    data: UnsafeCell::new(Data::EMPTY),
};
static TABLE: Table = Table { e: [ENTRY; MAX_HANDLES] };

#[inline]
fn lock(e: &Entry) {
    let mut n = 0u32;
    while e.lock.compare_exchange_weak(0, 1, Acquire, Relaxed).is_err() {
        n += 1;
        if n < 64 {
            core::hint::spin_loop();
        } else {
            sys::sched_yield();
        }
    }
}

#[inline]
fn unlock(e: &Entry) {
    e.lock.store(0, Release);
}

/// # Safety
/// The caller holds the entry lock (or has exclusive use of the entry).
#[allow(clippy::mut_from_ref)]
unsafe fn data(e: &Entry) -> &mut Data {
    &mut *e.data.get()
}

fn acquire(h: u32) -> Option<&'static Entry> {
    let idx = (h & 0xff) as usize;
    let gen = h >> 8;
    if idx >= MAX_HANDLES || gen == 0 {
        return None;
    }
    let e = &TABLE.e[idx];
    e.refs.fetch_add(1, Acquire);
    if e.state.load(Acquire) == ACTIVE && e.gen.load(Acquire) == gen {
        Some(e)
    } else {
        release(e);
        None
    }
}

fn release(e: &Entry) {
    if e.refs.fetch_sub(1, AcqRel) == 1 && e.state.compare_exchange(CLOSED, FREEING, AcqRel, Relaxed).is_ok() {
        unsafe { free_data(data(e)) };
        e.state.store(FREE, Release);
    }
}

fn alloc() -> Option<(usize, &'static Entry)> {
    for (i, e) in TABLE.e.iter().enumerate() {
        if e.state.load(Relaxed) == FREE && e.state.compare_exchange(FREE, ALLOC, Acquire, Relaxed).is_ok() {
            return Some((i, e));
        }
    }
    None
}

fn next_gen(e: &Entry) -> u32 {
    let g = e.gen.load(Relaxed).wrapping_add(1) & 0x00ff_ffff;
    if g == 0 {
        1
    } else {
        g
    }
}

fn shared<'a>(d: &Data) -> Option<&'a Shared> {
    if d.map.is_null() {
        None
    } else {
        Some(unsafe { Shared::at(d.map) })
    }
}

fn unmap_data(d: &mut Data) {
    sys::munmap(d.map, FILE_SIZE);
    d.map = ptr::null_mut();
    d.writable = false;
    d.instance = 0;
}

fn free_data(d: &mut Data) {
    sys::close(d.drain_fd);
    sys::close(d.wake_fd);
    unmap_data(d);
    *d = Data::EMPTY;
}

// ---------------------------------------------------------------------------------------------
// per-handle state machine (all under the entry lock)

#[derive(Default)]
struct Notes {
    reattached: bool,
    orphaned: bool,
    removed: bool,
    dead: bool,
    resyncs: u64,
    coalesced: u64,
    /// A control gen applied: (gen, axis_mode as written, axis config now in force, auto ceiling).
    ctl: Option<(u32, u32, u32, u32)>,
}

struct ShJournal<'a>(&'a Shared);

impl Journal for ShJournal<'_> {
    fn get(&self, idx: u32) -> Option<u32> {
        self.0.journal_get(idx)
    }
}

/// The identity a new attachment to `sh` reports: the file's, if its control block has a valid
/// one. A record the writer keeps busy through every attempt leaves `prev` (the identity this
/// handle had on its previous file, None for a new handle). Also the record's ff_mode (None: no
/// control block, or the record stayed busy).
fn capture_identity(sh: &Shared, prev: (Option<Identity>, u32)) -> (Option<Identity>, u32, Option<u32>) {
    if !sh.control_present() {
        return (None, 0, None);
    }
    for _ in 0..4 {
        if let Some((gen, st)) = sh.read_control() {
            return (st.id, gen, Some(st.ff_mode));
        }
        STATS.ctl_busy.fetch_add(1, Relaxed);
    }
    (prev.0, prev.1, None)
}

fn install(d: &mut Data, m: Mapped, first: bool) {
    d.map = m.ptr;
    d.writable = m.writable;
    d.instance = m.instance;
    if let Some(sh) = shared(d) {
        let (id, gen, ff_mode) = capture_identity(sh, (d.id, d.id_gen));
        d.id = id;
        d.id_gen = gen;
        if d.writable && sh.control_present() {
            sh.ctl.r_id_gen_attached.store(gen, Relaxed);
        }
        if !d.ff_fixed {
            // A busy record keeps the mode the handle had; a file without a block: the default.
            if ff_mode.is_some() || !sh.control_present() {
                d.ff_on = ff::mode_on(FF_ENV.load(Relaxed), ff_mode);
            }
        }
    }
    d.base_pending = true;
    d.dead = SLOTS.s[d.slot].dead_instance.load(Acquire) == m.instance;
    d.cursor.force_full();
    if d.writable {
        if let Some(sh) = shared(d) {
            if first {
                sh.header.r_attaches.fetch_add(1, Relaxed);
            } else {
                sh.header.r_reattaches.fetch_add(1, Relaxed);
            }
        }
    }
    if !first {
        STATS.reattaches.fetch_add(1, Relaxed);
    }
}

fn reattach(d: &mut Data, now: u64, notes: &mut Notes) {
    d.last_retry = now;
    match map_slot(d.slot, false) {
        Ok(mut m) if m.instance == d.instance => m.release(), // same live file: keep ours
        Ok(m) => {
            unmap_data(d);
            install(d, m, false);
            notes.reattached = true;
        }
        Err(_) => {
            if !d.map.is_null() {
                unmap_data(d);
                d.dead = false;
                d.cursor.force_full(); // neutral full state: nothing stays held
                notes.orphaned = true;
                STATS.orphaned.fetch_add(1, Relaxed);
            }
        }
    }
}

/// Follow the backing: re-attach after retirement / replacement, go neutral when the writer is
/// gone, retry an orphaned handle.
fn refresh(d: &mut Data, now: u64, notes: &mut Notes) {
    if d.removed {
        return;
    }
    follow(d, now, notes);
    apply_control(d, notes);
}

fn follow(d: &mut Data, now: u64, notes: &mut Notes) {
    let rt = &SLOTS.s[d.slot];
    let gen = rt.file_gen.load(Acquire);
    match shared(d) {
        Some(sh) => {
            let moved = gen != d.map_gen && rt.instance.load(Acquire) != d.instance;
            d.map_gen = gen;
            if sh.is_retired() || moved {
                reattach(d, now, notes);
            } else {
                let dead = rt.dead_instance.load(Acquire) == d.instance;
                if dead != d.dead {
                    d.dead = dead;
                    d.cursor.force_full();
                    if dead {
                        notes.dead = true;
                        STATS.writer_dead.fetch_add(1, Relaxed);
                    }
                }
            }
        }
        None => {
            if gen != d.map_gen || now.wrapping_sub(d.last_retry) >= SLOW_NS {
                d.map_gen = gen;
                d.last_retry = now;
                if let Ok(m) = map_slot(d.slot, false) {
                    install(d, m, false);
                    notes.reattached = true;
                }
            }
        }
    }
}

/// The state this handle should currently show. Detects removal.
fn observe(d: &mut Data, notes: &mut Notes) -> Snapshot {
    let sh = match shared(d) {
        Some(sh) if !d.dead && !d.removed => sh,
        _ => return Snapshot::NEUTRAL,
    };
    for k in 0..8 {
        if let Some(s) = sh.snapshot(SNAP_ATTEMPTS) {
            if d.base_pending {
                d.unplug_base = s.unplug;
                d.base_pending = false;
            } else if s.unplug != d.unplug_base {
                d.removed = true;
                notes.removed = true;
                STATS.removals.fetch_add(1, Relaxed);
                if d.writable {
                    sh.header.r_removals.fetch_add(1, Relaxed);
                }
            }
            return s;
        }
        STATS.snapshot_busy.fetch_add(1, Relaxed);
        if k > 0 {
            sys::sched_yield();
        }
    }
    d.cursor.as_snapshot() // the writer kept lapping us: deliver nothing new this time
}

/// When this handle's next events are due at `now` (a partly delivered frame is due at once).
fn due(d: &mut Data, snap: &Snapshot, now: u64) -> Due {
    if d.fpos < d.frame.len {
        return Due::Now;
    }
    match pacing(d, now) {
        Pace::Credit { count, stall, min } if d.cursor.only_axes_pending(snap) => credit_due(d, count, stall, min, now),
        Pace::Credit { .. } => {
            d.hold_since = 0;
            d.cursor.due(snap, now, 0)
        }
        Pace::Interval(t) => {
            d.hold_since = 0;
            d.cursor.due(snap, now, t)
        }
    }
}

fn earlier(a: Option<u64>, b: Option<u64>) -> Option<u64> {
    match (a, b) {
        (Some(x), Some(y)) => Some(x.min(y)),
        (x, None) => x,
        (None, y) => y,
    }
}

/// How long to sleep: the fallback re-check, or less if a held frame falls due sooner.
fn wait_ns(base: u64, deadline: Option<u64>) -> u64 {
    match deadline {
        Some(t) => base.min(t.saturating_sub(sys::mono_ns()).max(1_000)),
        None => base,
    }
}

/// What a handle is waiting for after sync_ready: the time its held frame falls due, and whether
/// it waits for the game's next frame (frame credits).
#[derive(Clone, Copy, Default)]
struct Hold {
    deadline: Option<u64>,
    frame: bool,
}

/// Keep exactly one readiness byte queued while events are due; close the peer on removal.
fn sync_ready(d: &mut Data, snap: &Snapshot, now: u64) -> Hold {
    if d.removed {
        if d.wake_fd >= 0 {
            if !d.ready {
                let _ = sys::send_byte(d.wake_fd);
                d.ready = true;
            }
            sys::close(d.wake_fd);
            d.wake_fd = -1;
        }
        return Hold::default();
    }
    let due = due(d, snap, now);
    let held = match due {
        Due::At(t) => Hold { deadline: Some(t), frame: false },
        Due::Frame(t) => Hold { deadline: Some(t), frame: true },
        _ => Hold::default(),
    };
    if d.wake_fd < 0 {
        return held;
    }
    let want = due == Due::Now;
    if want && !d.ready {
        match sys::send_byte(d.wake_fd) {
            Ok(_) | Err(sys::EAGAIN) => {
                d.ready = true;
                STATS.ready_signals.fetch_add(1, Relaxed);
            }
            Err(_) => {}
        }
    } else if !want && d.ready {
        sys::drain(d.drain_fd);
        d.ready = false;
    }
    held
}

#[repr(C)]
#[allow(dead_code)] // written to the caller's buffer, never read back
struct InputEvent {
    sec: i64,
    usec: i64,
    kind: u16,
    code: u16,
    value: i32,
}
const _: () = assert!(core::mem::size_of::<InputEvent>() == EVENT_SIZE);

unsafe fn deliver(d: &mut Data, snap: &Snapshot, out: *mut u8, max: usize, fs: &mut FrameStats, now: u64) -> usize {
    let pace = pacing(d, now);
    let mut n = 0usize;
    while n < max {
        if d.fpos >= d.frame.len {
            // Under frame credits a stick-only frame needs one; anything else goes at once.
            let (t, credit) = match pace {
                Pace::Interval(t) => (t, None),
                Pace::Credit { count, stall, min } if d.cursor.only_axes_pending(snap) => {
                    if credit_due(d, count, stall, min, now) != Due::Now {
                        break;
                    }
                    (0, Some((count, min)))
                }
                Pace::Credit { .. } => (0, None),
            };
            let r = match shared(d) {
                Some(sh) if !d.dead => d.cursor.next_frame_at(snap, &ShJournal(sh), &mut d.frame, fs, now, t),
                _ => d.cursor.next_frame_at(snap, &NoJournal, &mut d.frame, fs, now, t),
            };
            d.fpos = 0;
            if r != Due::Now {
                d.frame.len = 0;
                break;
            }
            if let Some((count, min)) = credit {
                d.credit_count = count; // spent; a button frame never spends it
                // Keep the ceiling on its own grid: a frame sent a wake latency after its slot
                // starts the next interval from the slot, not from now, or the cadence would drift
                // behind the game's frames (at 60 fps under a 60/s ceiling it lost a credit every
                // few dozen frames). Only within 1 ms, so the spacing never shrinks by more.
                let slot = d.last_axis_emit.wrapping_add(min);
                d.last_axis_emit = if d.last_axis_emit != 0 && now >= slot && now - slot < CEILING_SLACK_NS {
                    slot
                } else {
                    now
                };
                if d.hold_since != 0 {
                    fs.paced_frames += 1;
                }
                d.hold_since = 0;
            }
            d.ftime = sys::realtime_us();
        }
        let e: Ev = d.frame.ev[d.fpos];
        d.fpos += 1;
        ptr::write_unaligned(
            out.add(n * EVENT_SIZE) as *mut InputEvent,
            InputEvent { sec: d.ftime.0, usec: d.ftime.1, kind: e.kind, code: e.code, value: e.value },
        );
        n += 1;
    }
    n
}

fn account(d: &Data, fs: &FrameStats, notes: &mut Notes) {
    notes.resyncs += fs.resyncs;
    notes.coalesced += fs.coalesced_taps;
    if fs.frames == 0 && fs.coalesced_taps == 0 {
        return;
    }
    let s = &STATS;
    s.frames.fetch_add(fs.frames, Relaxed);
    s.events.fetch_add(fs.events, Relaxed);
    s.taps.fetch_add(fs.taps, Relaxed);
    s.full_frames.fetch_add(fs.full_frames, Relaxed);
    s.journal_frames.fetch_add(fs.journal_frames, Relaxed);
    s.counter_frames.fetch_add(fs.counter_frames, Relaxed);
    s.coalesced_taps.fetch_add(fs.coalesced_taps, Relaxed);
    s.resyncs.fetch_add(fs.resyncs, Relaxed);
    s.paced_frames.fetch_add(fs.paced_frames, Relaxed);
    s.merged_axis_publishes.fetch_add(fs.merged_axis_publishes, Relaxed);
    if d.writable && !d.dead {
        if let Some(sh) = shared(d) {
            let h = &sh.header;
            h.r_frames.fetch_add(fs.frames, Relaxed);
            h.r_events.fetch_add(fs.events, Relaxed);
            h.r_taps.fetch_add(fs.taps, Relaxed);
            h.r_coalesced.fetch_add(fs.coalesced_taps, Relaxed);
            h.r_resyncs.fetch_add(fs.resyncs, Relaxed);
            h.r_paced_frames.fetch_add(fs.paced_frames, Relaxed);
            h.r_merged_axis.fetch_add(fs.merged_axis_publishes, Relaxed);
        }
    }
}

fn emit_notes(slot: usize, fd: i32, n: &Notes) {
    if LOGGER.load(Relaxed) == 0 {
        return;
    }
    let head = |m: &mut Msg| {
        m.s(b"hermes: slot ").u(slot as u64).s(b" fd ").i(fd as i64).s(b": ");
    };
    if n.reattached {
        let mut m = Msg::new();
        head(&mut m);
        log(0, m.s(b"attached to a new backing file, full state follows"));
    }
    if n.orphaned {
        let mut m = Msg::new();
        head(&mut m);
        log(1, m.s(b"backing file gone or not ours, showing a neutral pad until it returns"));
    }
    if n.dead {
        let mut m = Msg::new();
        head(&mut m);
        log(1, m.s(b"writer process gone, showing a neutral pad"));
    }
    if n.removed {
        let mut m = Msg::new();
        head(&mut m);
        log(0, m.s(b"controller unplugged, handle removed"));
    }
    if n.resyncs > 0 {
        let mut m = Msg::new();
        head(&mut m);
        log(1, m.s(b"inconsistent state, resynced with a full-state frame"));
    }
    if n.coalesced > 0 {
        let mut m = Msg::new();
        head(&mut m);
        log(1, m.s(b"reader fell behind, taps coalesced: ").u(n.coalesced));
    }
    if let Some((gen, mode, hz, max)) = n.ctl {
        let mut m = Msg::new();
        m.s(b"hermes: slot ").u(slot as u64).s(b": control gen ").u(gen as u64).s(b" applied, stick pacing ");
        if mode == layout::CTL_AXIS_UNSET || mode > layout::CTL_AXIS_UNPACED {
            m.s(b"from the environment (");
        } else {
            m.s(b"set by the app (");
        }
        if hz == AXIS_AUTO {
            m.s(b"auto, at most ").u(max as u64).s(b"/s)");
        } else if hz == 0 {
            m.s(b"unpaced)");
        } else {
            m.s(b"fixed ").u(hz as u64).s(b" Hz)");
        }
        log(0, &mut m);
    }
}

fn wait_target(d: &Data) -> (*const AtomicU32, u32, bool) {
    match shared(d) {
        Some(sh) => (&sh.header.bell as *const AtomicU32, sh.header.bell.load(Acquire), true),
        None => (&IDLE as *const AtomicU32, IDLE.load(Relaxed), false),
    }
}

// ---------------------------------------------------------------------------------------------
// helper threads

fn ensure_helper(slot: usize) {
    let rt = &SLOTS.s[slot];
    let st = rt.helper.load(Acquire);
    if st == 1 {
        return;
    }
    if st == 2 && sys::mono_ns() < rt.retry_at.load(Relaxed) {
        return;
    }
    if rt.helper.compare_exchange(st, 1, AcqRel, Relaxed).is_err() {
        return;
    }
    if !sys::spawn_detached(helper_main, slot as *mut c_void, HELPER_STACK) {
        rt.retry_at.store(sys::mono_ns().wrapping_add(1_000_000_000), Relaxed);
        rt.helper.store(2, Release);
    }
}

/// Give up the helper's mapping, first taking this process out of its frame waiters.
fn release_hm(rt: &SlotRt, hm: &mut Mapped) {
    if rt.frame_reg.swap(0, Relaxed) != 0 && hm.writable {
        if let Some(sh) = hm.shared() {
            sh.header.frame_waiters.fetch_sub(1, SeqCst);
        }
    }
    hm.release();
}

/// Count this process in (or out of) the frame waiters of the helper's file, so the app's frame
/// feed rings the bell for a game frame only while some reader waits for one. Returns true if a
/// frame arrived since `count_before` (the caller then services again instead of sleeping).
fn frame_register(rt: &SlotRt, wants: bool, count_before: u64) -> bool {
    let hm = unsafe { &*rt.hm.get() };
    let sh = match hm.shared() {
        Some(sh) if hm.writable => sh,
        _ => return false,
    };
    let registered = rt.frame_reg.load(Relaxed) != 0;
    if wants && !registered {
        sh.header.frame_waiters.fetch_add(1, SeqCst);
        rt.frame_reg.store(1, Relaxed);
    } else if !wants && registered {
        sh.header.frame_waiters.fetch_sub(1, SeqCst);
        rt.frame_reg.store(0, Relaxed);
    }
    // SeqCst against the feed's count store: a frame that missed our registration is seen here.
    wants && sh.header.frame_count.load(SeqCst) != count_before
}

/// Every 500 ms: does the path still name the file we know, and is its writer alive?
fn slow_check(slot: usize, rt: &SlotRt) {
    let hm = unsafe { &mut *rt.hm.get() };
    let st = sys::stat(slot_path(slot));
    let remap = match (&st, hm.shared()) {
        (Ok(s), Some(sh)) => s.dev != hm.dev || s.ino != hm.ino || sh.is_retired(),
        (Ok(_), None) => true,
        (Err(_), Some(_)) => {
            release_hm(rt, hm);
            rt.instance.store(0, Release);
            rt.file_gen.fetch_add(1, Release);
            false
        }
        (Err(_), None) => false,
    };
    if remap {
        match map_slot(slot, true) {
            Ok(m) => {
                release_hm(rt, hm);
                *hm = m;
                rt.dead_votes.store(0, Relaxed);
                rt.instance.store(m.instance, Release);
                rt.file_gen.fetch_add(1, Release);
            }
            Err(_) => {
                if hm.shared().is_some() {
                    release_hm(rt, hm);
                    rt.instance.store(0, Release);
                    rt.file_gen.fetch_add(1, Release);
                }
            }
        }
    }
    if hm.shared().is_some() {
        // The writer holds an exclusive flock for as long as it lives, so getting a shared one
        // means it is gone. Neutralising a live pad is worse than noticing a dead writer a little
        // later, so the verdict needs two consecutive checks (0.5 - 1 s), and a free lock only
        // counts if the probed fd really is the mapped file. Any other answer means alive.
        let free = matches!(sys::flock(hm.fd, sys::LOCK_SH | sys::LOCK_NB), Ok(()));
        if free {
            let _ = sys::flock(hm.fd, sys::LOCK_UN);
        }
        let ours = !free || matches!(sys::fstat(hm.fd), Ok(st) if st.dev == hm.dev && st.ino == hm.ino);
        if free && !ours {
            // Not the file we think it is: forget it, the next check maps the path again.
            release_hm(rt, hm);
            rt.dead_votes.store(0, Relaxed);
            return;
        }
        if free {
            let votes = rt.dead_votes.load(Relaxed).saturating_add(1);
            rt.dead_votes.store(votes, Relaxed);
            if votes >= 2 {
                rt.dead_instance.store(hm.instance, Release);
            }
        } else {
            rt.dead_votes.store(0, Relaxed);
            if rt.dead_instance.load(Acquire) == hm.instance {
                rt.dead_instance.store(0, Release);
            }
        }
    }
}

/// Re-level every handle of `slot`.
fn service(slot: usize, now: u64, verify: bool) -> Hold {
    let mut all = Hold::default();
    for e in TABLE.e.iter() {
        if e.state.load(Acquire) != ACTIVE {
            continue;
        }
        e.refs.fetch_add(1, Acquire);
        if e.state.load(Acquire) != ACTIVE {
            release(e);
            continue;
        }
        let mut notes = Notes::default();
        lock(e);
        let d = unsafe { data(e) };
        let mine = d.slot == slot;
        let fd = d.guest_fd;
        if mine {
            refresh(d, now, &mut notes);
            let snap = observe(d, &mut notes);
            // Self-heal a readiness byte someone consumed behind our back (a raw read).
            if verify && d.ready && d.wake_fd >= 0 && matches!(sys::fionread(d.drain_fd), Ok(0)) {
                d.ready = false;
            }
            let h = sync_ready(d, &snap, sys::mono_ns());
            all.deadline = earlier(all.deadline, h.deadline);
            all.frame |= h.frame;
            ff_flush(d);
        }
        unlock(e);
        release(e);
        if mine {
            emit_notes(slot, fd, &notes);
        }
    }
    all
}

extern "C" fn helper_main(arg: *mut c_void) -> *mut c_void {
    let slot = arg as usize;
    sys::set_thread_name(b"hermes-wake\0");
    let rt = &SLOTS.s[slot];
    let mut last_slow = 0u64;
    let mut first = true;
    let mut last_bell = 0u32;
    let mut last_active = sys::mono_ns();
    loop {
        let now = sys::mono_ns();
        let slow = first || now.wrapping_sub(last_slow) >= SLOW_NS;
        if slow {
            slow_check(slot, rt);
            last_slow = now;
            first = false;
        }
        if rt.handles.load(Acquire) == 0 {
            // No handle on this slot in this process: nothing to deliver or check. Stop asking
            // for frame wakes and block until attach kicks (no timeout, no periodic wake).
            frame_register(rt, false, 0);
            let k = rt.kick.load(Acquire);
            if rt.handles.load(Acquire) == 0 {
                let _ = sys::futex_wait(&rt.kick, k, None, false);
            }
            first = true; // re-check the file when a handle comes back
            last_active = sys::mono_ns();
            continue;
        }
        let target = unsafe { (*rt.hm.get()).shared() }.map(|sh| {
            let h = &sh.header;
            (&h.bell as *const AtomicU32, h.bell.load(Acquire), h.frame_count.load(SeqCst))
        });
        // Wake on the bell (a publish, or a game frame while some handle here waits for one), at
        // the earliest held frame falling due, or after the fallback.
        let hold = service(slot, now, slow);
        if frame_register(rt, hold.frame, target.map_or(0, |t| t.2)) {
            continue; // a game frame landed while we were looking: hand out its credit now
        }
        // Active while something was published lately or a change is held: the short fallback
        // re-check. Idle otherwise: a long sleep, which also paces the liveness check. Either way
        // the wait compares against the bell value read BEFORE servicing, so a publish that lands
        // while we decide makes the wait return at once instead of being slept through.
        if let Some((_, v, _)) = target {
            if v != last_bell {
                last_bell = v;
                last_active = now;
            }
        }
        if hold.deadline.is_some() || hold.frame {
            last_active = now;
        }
        let idle = now.wrapping_sub(last_active) >= IDLE_AFTER_NS;
        let base = if idle || target.is_none() { IDLE_SLEEP_NS } else { FALLBACK_NS };
        let r = match target {
            Some((w, v, _)) => sys::futex_wait(w, v, Some(wait_ns(base, hold.deadline)), true),
            None => {
                let k = rt.kick.load(Acquire);
                sys::futex_wait(&rt.kick, k, Some(wait_ns(base, hold.deadline)), false)
            }
        };
        match r {
            Err(sys::ETIMEDOUT) => STATS.helper_timeouts.fetch_add(1, Relaxed),
            _ => STATS.helper_wakes.fetch_add(1, Relaxed),
        };
        let hm = unsafe { &*rt.hm.get() };
        if hm.writable {
            if let Some(sh) = hm.shared() {
                sh.header.r_helper_wakes.fetch_add(1, Relaxed);
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// fork

/// Give a handle inherited by a forked child its own readiness socket on the same fd number.
unsafe fn rebind_child(d: &mut Data) -> bool {
    d.frame.len = 0;
    d.fpos = 0;
    d.cursor.force_full();
    d.ready = false;
    let sv = match sys::socketpair() {
        Ok(sv) => sv,
        Err(_) => return false,
    };
    let nonblock = matches!(sys::fcntl(d.guest_fd, sys::F_GETFL, 0), Ok(f) if f & sys::O_NONBLOCK as usize != 0);
    let cloexec = matches!(sys::fcntl(d.guest_fd, sys::F_GETFD, 0), Ok(f) if f & sys::FD_CLOEXEC != 0);
    if d.guest_fd < 0 || sys::dup3(sv[0], d.guest_fd, if cloexec { sys::O_CLOEXEC } else { 0 }).is_err() {
        sys::close(sv[0]);
        sys::close(sv[1]);
        return false;
    }
    if nonblock {
        if let Ok(f) = sys::fcntl(d.guest_fd, sys::F_GETFL, 0) {
            let _ = sys::fcntl(d.guest_fd, sys::F_SETFL, f | sys::O_NONBLOCK as usize);
        }
    }
    // The inherited private ends belong to the parent's socket; holding them here would keep the
    // parent's peer alive (no POLLHUP on unplug).
    sys::close(d.drain_fd);
    sys::close(d.wake_fd);
    d.drain_fd = sv[0]; // same socket as the guest fd now
    d.wake_fd = sv[1];
    if sys::send_byte(sv[1]).is_ok() {
        d.ready = true; // the child owes its reader a full-state frame
    }
    if d.removed {
        sys::close(sv[1]);
        d.wake_fd = -1;
    }
    true
}

unsafe extern "C" fn atfork_child() {
    if CONFIG.state.load(Relaxed) == 1 {
        CONFIG.state.store(0, Relaxed);
    }
    for rt in SLOTS.s.iter() {
        rt.helper.store(0, Relaxed);
        rt.retry_at.store(0, Relaxed);
        rt.dead_votes.store(0, Relaxed);
        // The helper's mapping and fd belonged to the parent's helper thread, which may have been
        // in the middle of replacing them; the child's helper opens its own on its first check.
        // (Its fd shares the parent's open file description, which must not be probed from here.)
        // Any frame-waiter registration on it is the parent helper's, not ours to undo.
        rt.frame_reg.store(0, Relaxed);
        let hm = &mut *rt.hm.get();
        hm.release();
    }
    let mut counts = [0u32; MAX_SLOTS];
    let mut rebound = 0u64;
    for e in TABLE.e.iter() {
        e.lock.store(0, Relaxed);
        let d = &mut *e.data.get();
        match e.state.load(Relaxed) {
            ACTIVE => {
                e.refs.store(1, Relaxed);
                if rebind_child(d) {
                    rebound += 1;
                }
                if d.slot < MAX_SLOTS {
                    counts[d.slot] += 1;
                }
            }
            CLOSED | FREEING => {
                free_data(d);
                e.refs.store(0, Relaxed);
                e.state.store(FREE, Relaxed);
            }
            ALLOC => {
                e.refs.store(0, Relaxed);
                e.state.store(FREE, Relaxed);
            }
            _ => e.refs.store(0, Relaxed),
        }
    }
    for (s, rt) in SLOTS.s.iter().enumerate() {
        rt.handles.store(counts[s], Relaxed);
    }
    STATS.fork_rebinds.fetch_add(rebound, Relaxed);
    ff::atfork_child();
}

fn register_atfork() {
    if ATFORK.load(Acquire) == 0 && ATFORK.compare_exchange(0, 1, AcqRel, Relaxed).is_ok() {
        sys::register_atfork_child(atfork_child);
    }
}

// ---------------------------------------------------------------------------------------------
// public API (wrapped 1:1 by the C ABI in the guest crate)

/// Attach a new handle to `slot`. Returns (guest fd, handle).
pub fn attach(slot: i32, open_flags: i32) -> Result<(i32, u32), i32> {
    let mask = init();
    if !(0..MAX_SLOTS as i32).contains(&slot) || mask & (1 << slot) == 0 {
        return Err(sys::ENODEV);
    }
    let slot = slot as usize;
    register_atfork();
    let (idx, e) = alloc().ok_or(sys::EMFILE)?;
    let sv = match sys::socketpair() {
        Ok(sv) => sv,
        Err(err) => {
            e.state.store(FREE, Release);
            return Err(err);
        }
    };
    let (guest, wake) = (sv[0], sv[1]);
    let drain = match sys::fcntl(guest, sys::F_DUPFD_CLOEXEC, 0) {
        Ok(fd) => fd as i32,
        Err(err) => {
            sys::close(guest);
            sys::close(wake);
            e.state.store(FREE, Release);
            return Err(err);
        }
    };
    if open_flags & sys::O_NONBLOCK != 0 {
        if let Ok(f) = sys::fcntl(guest, sys::F_GETFL, 0) {
            let _ = sys::fcntl(guest, sys::F_SETFL, f | sys::O_NONBLOCK as usize);
        }
    }
    if open_flags & sys::O_CLOEXEC == 0 {
        let _ = sys::fcntl(guest, sys::F_SETFD, 0);
    }
    let rt = &SLOTS.s[slot];
    let now = sys::mono_ns();
    let mut notes = Notes::default();
    let d = unsafe { data(e) };
    *d = Data::EMPTY;
    env_cfg(d);
    d.ff_on = ff::mode_on(FF_ENV.load(Relaxed), None); // until a file's control block says more
    d.slot = slot;
    d.guest_fd = guest;
    d.drain_fd = drain;
    d.wake_fd = wake;
    d.map_gen = rt.file_gen.load(Acquire);
    d.last_retry = now;
    match map_slot(slot, false) {
        Ok(m) => install(d, m, true),
        Err(_) => notes.orphaned = true,
    }
    apply_control(d, &mut notes);
    d.cursor = Cursor::new(); // the first thing this handle delivers is the full state
    if sys::send_byte(wake).is_ok() {
        d.ready = true;
    }
    let gen = next_gen(e);
    e.gen.store(gen, Relaxed);
    e.refs.fetch_add(1, AcqRel); // the open reference, dropped by detach
    e.state.store(ACTIVE, Release);
    rt.handles.fetch_add(1, AcqRel);
    rt.kick.fetch_add(1, Release);
    sys::futex_wake(&rt.kick, 1, false);
    ensure_helper(slot);
    STATS.attaches.fetch_add(1, Relaxed);
    log_config_once();
    if LOGGER.load(Relaxed) != 0 {
        let mut m = Msg::new();
        m.s(b"hermes: slot ").u(slot as u64).s(b" fd ").i(guest as i64);
        if notes.orphaned {
            m.s(b": attached, no usable backing yet (neutral pad)");
        } else {
            m.s(b": attached");
        }
        log(0, &mut m);
        if notes.ctl.is_some() {
            emit_notes(slot, guest, &Notes { ctl: notes.ctl, ..Notes::default() });
        }
    }
    Ok((guest, (gen << 8) | idx as u32))
}

/// Release a handle and close its guest fd.
pub fn detach(h: u32) -> Result<(), i32> {
    let e = acquire(h).ok_or(sys::EBADF)?;
    if e.state.compare_exchange(ACTIVE, CLOSED, AcqRel, Acquire).is_err() {
        release(e);
        return Err(sys::EBADF);
    }
    lock(e);
    let d = unsafe { data(e) };
    let fd = d.guest_fd;
    d.guest_fd = -1;
    let slot = d.slot;
    let ff_owner = d.ff_owner;
    // A read blocked on this handle may be in a long idle sleep: wake it (FUTEX_WAKE needs no
    // write access and changes nothing; other sleepers on the bell just go back to sleep).
    match shared(d) {
        Some(sh) => {
            sys::futex_wake(&sh.header.bell, i32::MAX, true);
        }
        None => {
            sys::futex_wake(&IDLE, i32::MAX, false);
        }
    }
    unlock(e);
    if ff_owner {
        ff::detach(slot, h); // stops and erases the effects this handle uploaded
    }
    SLOTS.s[slot].handles.fetch_sub(1, AcqRel);
    sys::close(fd);
    STATS.detaches.fetch_add(1, Relaxed);
    release(e); // the open reference
    release(e); // ours
    Ok(())
}

fn with_handle<R>(h: u32, f: impl FnOnce(&mut Data, &mut Notes) -> R) -> Result<R, i32> {
    let e = acquire(h).ok_or(sys::EBADF)?;
    let mut notes = Notes::default();
    lock(e);
    let d = unsafe { data(e) };
    let slot = d.slot;
    let fd = d.guest_fd;
    refresh(d, sys::mono_ns(), &mut notes);
    let r = f(d, &mut notes);
    unlock(e);
    release(e);
    ensure_helper(slot);
    emit_notes(slot, fd, &notes);
    Ok(r)
}

pub fn fd(h: u32) -> Result<i32, i32> {
    with_handle(h, |d, _| d.guest_fd)
}

pub fn slot(h: u32) -> Result<i32, i32> {
    with_handle(h, |d, _| d.slot as i32)
}

pub fn is_removed(h: u32) -> Result<bool, i32> {
    with_handle(h, |d, notes| {
        let snap = observe(d, notes);
        sync_ready(d, &snap, sys::mono_ns());
        d.removed
    })
}

pub fn has_pending(h: u32) -> Result<bool, i32> {
    with_handle(h, |d, notes| {
        let snap = observe(d, notes);
        let now = sys::mono_ns();
        sync_ready(d, &snap, now);
        if d.removed {
            Err(sys::ENODEV)
        } else {
            // A stick change held by pacing is not pending yet.
            Ok(due(d, &snap, now) == Due::Now)
        }
    })?
}

/// What `poll` should report for this handle, evdev-style.
pub fn poll_revents(h: u32, events: i16) -> i16 {
    let rd = events & (sys::POLLIN | sys::POLLRDNORM);
    let wr = events & (sys::POLLOUT | sys::POLLWRNORM);
    match has_pending(h) {
        Ok(true) => rd | wr,
        Ok(false) => wr,
        Err(sys::ENODEV) => sys::POLLHUP | sys::POLLERR,
        Err(_) => sys::POLLNVAL,
    }
}

/// Read whole events into `buf` (`len` bytes). Blocking unless `nonblocking`.
///
/// # Safety
/// `buf` must be valid for `len` bytes of writes.
pub unsafe fn read(h: u32, buf: *mut u8, len: usize, nonblocking: bool) -> Result<usize, i32> {
    if len < EVENT_SIZE {
        return Err(sys::EINVAL);
    }
    if buf.is_null() {
        return Err(sys::EFAULT);
    }
    let max = len / EVENT_SIZE;
    let e = acquire(h).ok_or(sys::EBADF)?;
    let result = loop {
        if e.state.load(Acquire) != ACTIVE {
            break Err(sys::EBADF);
        }
        let mut notes = Notes::default();
        let mut fs = FrameStats::default();
        lock(e);
        let d = data(e);
        let slot = d.slot;
        let fd = d.guest_fd;
        refresh(d, sys::mono_ns(), &mut notes);
        let (word, val, shared_word) = wait_target(d);
        let snap = observe(d, &mut notes);
        let now = sys::mono_ns();
        let outcome = if d.removed {
            Err(sys::ENODEV)
        } else {
            let n = deliver(d, &snap, buf, max, &mut fs, now);
            account(d, &fs, &mut notes);
            Ok(n)
        };
        let held = sync_ready(d, &snap, now);
        if val != d.last_bell || matches!(outcome, Ok(n) if n > 0) || held.deadline.is_some() {
            d.last_bell = val;
            d.last_active = now;
        }
        let idle = now.wrapping_sub(d.last_active) >= IDLE_AFTER_NS;
        unlock(e);
        ensure_helper(slot);
        emit_notes(slot, fd, &notes);
        match outcome {
            Err(err) => break Err(err),
            Ok(n) if n > 0 => break Ok(n * EVENT_SIZE),
            Ok(_) => {}
        }
        if nonblocking {
            break Err(sys::EAGAIN);
        }
        // Sleep until a publish, the moment a held stick frame falls due, or the fallback.
        // (A wait for a game frame is announced by the slot's wake thread, which services this
        // handle too; the frame rings the same bell.)
        let base = if idle { IDLE_SLEEP_NS } else { FALLBACK_NS };
        if let Err(sys::EINTR) = sys::futex_wait(word, val, Some(wait_ns(base, held.deadline)), shared_word) {
            break Err(sys::EINTR);
        }
    };
    release(e);
    result
}

/// The slot's current state: 11 buttons (0/1) and 8 axes, canonical order, never torn.
pub fn current_state(h: u32, buttons: &mut [i32; BUTTONS], axes: &mut [i32; AXES]) -> Result<(), i32> {
    with_handle(h, |d, notes| {
        let s = observe(d, notes);
        sync_ready(d, &s, sys::mono_ns());
        if d.removed {
            return Err(sys::ENODEV);
        }
        let b = s.buttons();
        for i in 0..BUTTONS {
            buttons[i] = ((b >> i) & 1) as i32;
        }
        for i in 0..AXES {
            axes[i] = clamp_axis(i, s.axes[i]);
        }
        Ok(())
    })?
}

/// `struct hermes_identity`: a controller identity and the control gen it came from.
#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct IdentityView {
    pub gen: u32,
    pub bustype: u16,
    pub vendor: u16,
    pub product: u16,
    pub version: u16,
    pub key: [u8; layout::ID_KEY_LEN],
    pub name: [u8; layout::ID_NAME_LEN],
}

const _: () = {
    use core::mem::{offset_of, size_of};
    assert!(size_of::<IdentityView>() == 92);
    assert!(offset_of!(IdentityView, bustype) == 4);
    assert!(offset_of!(IdentityView, version) == 10);
    assert!(offset_of!(IdentityView, key) == 12);
    assert!(offset_of!(IdentityView, name) == 28);
};

impl IdentityView {
    fn of(id: &Identity, gen: u32) -> IdentityView {
        IdentityView {
            gen,
            bustype: id.bustype,
            vendor: id.vendor,
            product: id.product,
            version: id.version,
            key: id.key,
            name: id.name,
        }
    }
}

/// The identity this handle reports: the one its current file carried when the handle attached
/// to it. ENODATA when that file had none (or predates control blocks): use your default.
pub fn identity(h: u32) -> Result<IdentityView, i32> {
    with_handle(h, |d, _| {
        STATS.identity_reads.fetch_add(1, Relaxed);
        if d.writable {
            if let Some(sh) = shared(d) {
                if sh.control_present() {
                    sh.ctl.r_identity_reads.fetch_add(1, Relaxed);
                }
            }
        }
        match &d.id {
            Some(id) => Ok(IdentityView::of(id, d.id_gen)),
            None => Err(sys::ENODATA),
        }
    })?
}

/// The identity `slot`'s current file carries right now (for naming a device before any open).
/// ENODEV: slot not configured, or no live file for it; ENODATA: the file has no identity;
/// EAGAIN: the writer kept the record busy through every attempt (practically never).
pub fn slot_identity(slot: i32) -> Result<IdentityView, i32> {
    if !(0..MAX_SLOTS as i32).contains(&slot) || init() & (1 << slot) == 0 {
        return Err(sys::ENODEV);
    }
    let mut m = map_slot(slot as usize, false).map_err(|_| sys::ENODEV)?;
    let r = match m.shared() {
        Some(sh) if sh.control_present() => {
            let mut r = Err(sys::EAGAIN);
            for _ in 0..8 {
                if let Some((gen, st)) = sh.read_control() {
                    r = st.id.map(|id| IdentityView::of(&id, gen)).ok_or(sys::ENODATA);
                    break;
                }
                STATS.ctl_busy.fetch_add(1, Relaxed);
            }
            if m.writable {
                sh.ctl.r_identity_reads.fetch_add(1, Relaxed);
            }
            r
        }
        _ => Err(sys::ENODATA),
    };
    STATS.identity_reads.fetch_add(1, Relaxed);
    m.release();
    r
}

/// Counters from the slot's current file.
pub fn slot_stats(slot: i32) -> Result<SlotStatsView, i32> {
    if !(0..MAX_SLOTS as i32).contains(&slot) || init() & (1 << slot) == 0 {
        return Err(sys::ENODEV);
    }
    let mut m = map_slot(slot as usize, false)?;
    let mut v = SlotStatsView::default();
    if let Some(sh) = m.shared() {
        let h = &sh.header;
        v.instance = h.instance.load(Relaxed);
        v.publishes = h.w_publishes.load(Relaxed);
        v.wake_calls = h.w_wake_calls.load(Relaxed);
        v.woken = h.w_woken.load(Relaxed);
        v.unplugs = h.w_unplugs.load(Relaxed);
        v.edges = h.w_edges.load(Relaxed);
        v.reader_attaches = h.r_attaches.load(Relaxed);
        v.reader_reattaches = h.r_reattaches.load(Relaxed);
        v.reader_taps = h.r_taps.load(Relaxed);
        v.reader_coalesced = h.r_coalesced.load(Relaxed);
        v.reader_resyncs = h.r_resyncs.load(Relaxed);
        v.reader_frames = h.r_frames.load(Relaxed);
        v.reader_events = h.r_events.load(Relaxed);
        v.reader_removals = h.r_removals.load(Relaxed);
        v.writer_pid = h.writer_pid.load(Relaxed);
        v.retired = h.retired.load(Relaxed);
        v.reader_paced_frames = h.r_paced_frames.load(Relaxed);
        v.reader_merged_axis_publishes = h.r_merged_axis.load(Relaxed);
        v.reader_axis_hz = h.r_axis_hz.load(Relaxed) as u64;
        v.reader_auto_fallbacks = h.r_auto_fallbacks.load(Relaxed);
        v.frame_interval_ns = h.frame_interval_ns.load(Relaxed);
        v.frame_time_ns = h.frame_time_ns.load(Relaxed);
        v.frame_count = h.frame_count.load(Relaxed);
        v.reader_axis_mode = h.r_axis_mode.load(Relaxed) as u64;
        v.frame_waiters = h.frame_waiters.load(Relaxed) as u64;
        v.reader_helper_wakes = h.r_helper_wakes.load(Relaxed);
        v.reader_auto_max = h.r_auto_max.load(Relaxed) as u64;
        if sh.control_present() {
            let c = &sh.ctl;
            v.control_gen = c.gen.load(Acquire) as u64;
            v.reader_ctl_gen_applied = c.r_ctl_gen_applied.load(Relaxed) as u64;
            v.reader_id_gen_attached = c.r_id_gen_attached.load(Relaxed) as u64;
            v.reader_identity_reads = c.r_identity_reads.load(Relaxed) as u64;
        }
        if sh.ff_stats_present() {
            let c = &sh.ctl;
            v.reader_ff_uploads = c.r_ff_uploads.load(Relaxed) as u64;
            v.reader_ff_plays = c.r_ff_plays.load(Relaxed) as u64;
            v.reader_ff_datagrams = c.r_ff_datagrams.load(Relaxed) as u64;
            v.reader_ff_guard_fades = c.r_ff_guard_fades.load(Relaxed) as u64;
        }
    }
    m.release();
    Ok(v)
}

// ---------------------------------------------------------------------------------------------
// force feedback (API 3): the handle side of crate::ff

/// HERMES_DINPUT_FF as init parsed it (layout::CTL_FF_*; UNSET: the control block decides).
static FF_ENV: AtomicU32 = AtomicU32::new(layout::CTL_FF_UNSET);
/// Slots whose force-feedback mode this process has logged (one line each).
static FF_LOGGED: AtomicU32 = AtomicU32::new(0);

/// Add the slot's force-feedback counters to its file (control block, format 1.6). Entry lock
/// held. They wait while the handle has no writable live file of that format.
fn ff_flush(d: &Data) {
    let p = match ff::PENDING.get(d.slot) {
        Some(p) => p,
        None => return,
    };
    let sh = match shared(d) {
        Some(sh) if d.writable && !d.dead && sh.ff_stats_present() => sh,
        _ => return,
    };
    let c = &sh.ctl;
    let dst = [&c.r_ff_uploads, &c.r_ff_plays, &c.r_ff_datagrams, &c.r_ff_guard_fades];
    for (i, w) in [ff::P_UPLOADS, ff::P_PLAYS, ff::P_DATAGRAMS, ff::P_FADES].iter().enumerate() {
        if p[*w].load(Relaxed) != 0 {
            dst[i].fetch_add(p[*w].swap(0, Relaxed), Relaxed);
        }
    }
}

/// A force-feedback call on `h`: under the entry lock, without the refresh other calls do (no
/// file I/O), given the handle's data and value. The engine's lock nests inside the entry lock
/// (its thread never takes an entry lock); the logger is never called from here.
fn with_ff<R>(h: u32, f: impl FnOnce(&mut Data, u32) -> R) -> Result<R, i32> {
    let e = acquire(h).ok_or(sys::EBADF)?;
    lock(e);
    let d = unsafe { data(e) };
    d.ff_fixed = true;
    let r = f(d, h);
    ff_flush(d);
    unlock(e);
    release(e);
    Ok(r)
}

/// The EV_FF capability bitmap for this handle's mode (`ff::bits`).
pub fn ff_bits(h: u32) -> Result<[u8; ff::BITS_LEN], i32> {
    let (slot, on) = with_ff(h, |d, _| (d.slot, d.ff_on))?;
    let bit = 1u32 << slot;
    if LOGGER.load(Relaxed) != 0 && FF_LOGGED.fetch_or(bit, AcqRel) & bit == 0 {
        let mut m = Msg::new();
        m.s(b"hermes: slot ").u(slot as u64).s(b": force feedback ");
        if on {
            m.s(b"on (rumble, periodic, constant, ramp, conditions; gain)");
        } else {
            m.s(b"off (rumble only)");
        }
        if FF_ENV.load(Relaxed) == layout::CTL_FF_UNSET {
            m.s(b" by the app's setting");
        } else {
            m.s(b" by HERMES_DINPUT_FF");
        }
        log(0, &mut m);
    }
    Ok(ff::bits(on))
}

/// Upload a new effect (`e.id` -1: the chosen id is written back) or update one (see
/// `ff::Engine::upload`).
pub fn ff_upload(h: u32, e: &mut ff::FfEffect) -> Result<(), i32> {
    with_ff(h, |d, h| {
        let r = ff::upload(d.slot, h, e, d.ff_on);
        if r.is_ok() {
            d.ff_owner = true;
        }
        r
    })?
}

pub fn ff_erase(h: u32, id: i32) -> Result<(), i32> {
    with_ff(h, |d, _| ff::erase(d.slot, id))?
}

pub fn ff_play(h: u32, id: i32, count: i32) -> Result<(), i32> {
    with_ff(h, |d, _| ff::play(d.slot, id, count))?
}

pub fn ff_set_gain(h: u32, gain: u16) -> Result<(), i32> {
    with_ff(h, |d, _| ff::set_gain(d.slot, gain))?
}

/// Accepted, no effect (a pad has no centring spring).
pub fn ff_set_autocenter(h: u32, _value: u16) -> Result<(), i32> {
    with_ff(h, |_, _| ())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::layout::{CTL_AXIS_AUTO, CTL_AXIS_FIXED, CTL_AXIS_UNPACED, CTL_AXIS_UNSET, CTL_MAGIC, CTL_SIZE};
    use crate::pad::PadInput;
    use crate::writer::SlotWriter;

    fn tmpdir(tag: &str) -> std::path::PathBuf {
        use std::sync::atomic::{AtomicU32, Ordering};
        static N: AtomicU32 = AtomicU32::new(0);
        let d = std::env::temp_dir().join(format!(
            "hermes-c-{}-{}-{}",
            tag,
            std::process::id(),
            N.fetch_add(1, Ordering::Relaxed)
        ));
        std::fs::create_dir_all(&d).unwrap();
        d
    }

    /// A writer for `slot` in a fresh directory, and a handle's Data mapped onto its file the way
    /// install() maps it (writable, so the reader stats land in the file).
    fn rig(tag: &str, slot: u32, setup: impl FnOnce(&mut SlotWriter)) -> (SlotWriter, std::path::PathBuf, Data) {
        let dir = tmpdir(tag);
        let mut w = SlotWriter::new(slot);
        setup(&mut w);
        w.prepare(dir.to_str().unwrap().as_bytes()).unwrap();
        w.open();
        let d = attach_data(&dir, slot);
        (w, dir, d)
    }

    fn attach_data(dir: &std::path::Path, slot: u32) -> Data {
        let mut p = sys::PathBuf::new();
        assert!(p.push(dir.join(format!("pad{}", slot)).to_str().unwrap().as_bytes()));
        let fd = sys::openat(p.as_ptr(), sys::O_RDWR | sys::O_CLOEXEC, 0).unwrap();
        let ptr = sys::mmap(FILE_SIZE, sys::PROT_READ | sys::PROT_WRITE, sys::MAP_SHARED, fd).unwrap();
        sys::close(fd);
        let instance = unsafe { Shared::at(ptr) }.header.instance.load(Relaxed);
        let mut d = Data::EMPTY;
        env_cfg(&mut d);
        d.slot = slot as usize;
        install(&mut d, Mapped { ptr, fd: -1, dev: 0, ino: 0, instance, writable: true }, true);
        d.cursor = Cursor::new();
        d
    }

    #[test]
    fn a_pacing_change_applies_at_the_next_decision_not_the_old_deadline() {
        let (mut w, _dir, mut d) = rig("ctl-pace", 2, |w| w.set_session(CTL_AXIS_FIXED, 30, 0, 0, 0));
        let mut notes = Notes::default();
        apply_control(&mut d, &mut notes);
        assert_eq!(d.cfg_hz, 30);
        assert!(notes.ctl.is_some(), "an applied gen is logged");
        let sh = shared(&d).unwrap();
        assert_eq!(sh.ctl.r_ctl_gen_applied.load(Relaxed), w.control().0);
        // fake clock: the full-state frame at t0
        let t0 = 50_000_000_000u64;
        let mut buf = [0u8; EVENT_SIZE * 64];
        let mut fs = FrameStats::default();
        let snap = sh.snapshot(64).unwrap();
        assert_eq!(unsafe { deliver(&mut d, &snap, buf.as_mut_ptr(), 64, &mut fs, t0) }, 20);
        // a stick move 5 ms later is held to the 30 Hz grid
        w.set_pad(PadInput { buttons: 0, axes: [9000, 0, 0, 0, 0, 0, 0, 0] });
        let snap = sh.snapshot(64).unwrap();
        let t1 = t0 + 5_000_000;
        assert_eq!(due(&mut d, &snap, t1), Due::At(t0 + 1_000_000_000 / 30));
        // the app goes unpaced; the very next decision (1 ms on) sends it, 27 ms before the old
        // deadline
        w.set_session(CTL_AXIS_UNPACED, 0, 0, 0, 0);
        let t2 = t1 + 1_000_000;
        let mut notes = Notes::default();
        refresh(&mut d, t2, &mut notes);
        assert_eq!(d.cfg_hz, 0);
        assert!(notes.ctl.is_some());
        assert_eq!(due(&mut d, &snap, t2), Due::Now);
        let n = unsafe { deliver(&mut d, &snap, buf.as_mut_ptr(), 64, &mut fs, t2) };
        assert_eq!(n, 2, "ABS_X + SYN_REPORT");
        assert_eq!(axis_config(), 0, "the accessors report the effective setting");
        // no change: one atomic load, nothing re-read or logged
        let applied = STATS.ctl_applied.load(Relaxed);
        let mut notes = Notes::default();
        refresh(&mut d, t2 + 1, &mut notes);
        assert!(notes.ctl.is_none());
        assert_eq!(STATS.ctl_applied.load(Relaxed), applied);
        // fixed 1000 Hz, then back to the environment (mode 0)
        w.set_session(CTL_AXIS_FIXED, 1000, 0, 0, 0);
        refresh(&mut d, t2 + 2, &mut Notes::default());
        assert_eq!((d.cfg_hz, d.cfg_ns), (1000, 1_000_000));
        w.set_session(CTL_AXIS_UNSET, 0, 0, 0, 0);
        refresh(&mut d, t2 + 3, &mut Notes::default());
        assert_eq!((d.cfg_hz, d.cfg_auto_max), (AXIS_HZ.load(Relaxed), AUTO_MAX.load(Relaxed)));
        assert_eq!(sh.ctl.r_ctl_gen_applied.load(Relaxed), w.control().0);
    }

    #[test]
    fn an_old_or_garbage_control_block_leaves_the_environment_in_force() {
        let id = Identity::new(3, 1, 2, 3, b"k", b"n");
        let (mut w, dir, _) = rig("ctl-old", 3, |w| {
            w.set_session(CTL_AXIS_FIXED, 30, 0, 0, 0);
            w.set_identity(Some(id));
        });
        let env = (AXIS_HZ.load(Relaxed), AUTO_MAX.load(Relaxed));
        let check_env = |what: &str| {
            let mut d = attach_data(&dir, 3);
            apply_control(&mut d, &mut Notes::default());
            assert_eq!((d.cfg_hz, d.cfg_auto_max), env, "{}: environment pacing", what);
            assert!(d.id.is_none(), "{}: no identity", what);
            d
        };
        let d = attach_data(&dir, 3);
        let sh = shared(&d).unwrap();
        // a format 1.4 file (whatever bytes sit at 0xB00)
        sh.header.minor.store(4, Relaxed);
        check_env("minor 4");
        sh.header.minor.store(5, Relaxed);
        // garbage magic, then a wrong size
        sh.ctl.magic.store(0xDEAD_BEEF, Relaxed);
        check_env("bad magic");
        sh.ctl.magic.store(CTL_MAGIC, Relaxed);
        sh.ctl.size.store(128, Relaxed);
        check_env("bad size");
        sh.ctl.size.store(CTL_SIZE as u32, Relaxed);
        // a writer stuck mid-update (odd seq): never blocks, keeps the environment / last good
        sh.ctl.seq.fetch_add(1, Relaxed);
        let busy = STATS.ctl_busy.load(Relaxed);
        let d2 = check_env("stuck seqlock");
        assert!(STATS.ctl_busy.load(Relaxed) > busy);
        sh.ctl.seq.fetch_add(1, Relaxed);
        // intact again: the same handle picks it up at its next decision
        let mut d2 = d2;
        apply_control(&mut d2, &mut Notes::default());
        assert_eq!(d2.cfg_hz, 30);
        // out-of-range values in the file are clamped
        let st = |mode, hz, max| ControlState { axis_mode: mode, axis_hz: hz, axis_auto_max: max, ..ControlState::DEFAULT };
        assert_eq!(control_pacing(&st(CTL_AXIS_FIXED, 5, 0)), Some((AXIS_HZ_MIN, AUTO_MAX_DEFAULT)));
        assert_eq!(control_pacing(&st(CTL_AXIS_FIXED, 99_999, 0)), Some((AXIS_HZ_MAX, AUTO_MAX_DEFAULT)));
        assert_eq!(control_pacing(&st(CTL_AXIS_AUTO, 0, 1)), Some((AXIS_AUTO, AUTO_MAX_MIN)));
        assert_eq!(control_pacing(&st(CTL_AXIS_AUTO, 0, 9999)), Some((AXIS_AUTO, AUTO_MAX_MAX)));
        assert_eq!(control_pacing(&st(CTL_AXIS_UNPACED, 77, 0)).map(|p| p.0), Some(0));
        assert_eq!(control_pacing(&st(9, 60, 60)), None, "unknown mode = the environment");
        assert_eq!(control_pacing(&st(CTL_AXIS_UNSET, 60, 60)), None);
        w.set_identity(None);
    }

    #[test]
    fn the_ff_mode_is_taken_from_the_control_block_when_a_handle_attaches() {
        let (mut w, dir, d) = rig("ff-mode", 1, |w| w.set_ff_mode(layout::CTL_FF_OFF));
        assert!(!d.ff_on, "the app's Off");
        w.set_ff_mode(layout::CTL_FF_ON);
        assert!(attach_data(&dir, 1).ff_on, "a new attach takes On");
        w.set_ff_mode(layout::CTL_FF_UNSET);
        assert!(attach_data(&dir, 1).ff_on, "unset = on");
        w.set_ff_mode(layout::CTL_FF_OFF);
        // fixed once the guest made an ff call: a re-attach to another file keeps it
        let mut d = attach_data(&dir, 1);
        assert!(!d.ff_on);
        d.ff_fixed = true;
        w.set_ff_mode(layout::CTL_FF_ON);
        w.release();
        w.prepare(dir.to_str().unwrap().as_bytes()).unwrap();
        let fresh = attach_data(&dir, 1);
        assert!(fresh.ff_on, "the new file says On");
        install(&mut d, Mapped { ptr: fresh.map, fd: -1, dev: 0, ino: 0, instance: fresh.instance, writable: true }, false);
        assert!(!d.ff_on, "kept Off across the re-attach");
        assert_eq!(FF_ENV.load(Relaxed), layout::CTL_FF_UNSET, "HERMES_DINPUT_FF is not set in the tests");
    }

    #[test]
    fn env_grammar() {
        let mut p = [[0u8; PATH_CAP]; MAX_SLOTS];
        let m = parse_paths(b"0=/a/pad0;1=/b/pad1; 3 = /d/pad3 ;x=/bad;7=/bad;2=;0=/dup", &mut p);
        assert_eq!(m, 0b1011);
        assert_eq!(&p[0][..9], b"/a/pad0\0\0");
        assert_eq!(&p[1][..8], b"/b/pad1\0");
        assert_eq!(&p[3][..8], b"/d/pad3\0");
        let mut q = [[0u8; PATH_CAP]; MAX_SLOTS];
        assert_eq!(parse_paths(b"", &mut q), 0);
        assert_eq!(parse_paths(b";;;", &mut q), 0);
        assert_eq!(parse_paths(b"2=/only", &mut q), 0b100);
    }

    #[test]
    fn axis_hz_parsing() {
        assert_eq!(parse_axis_hz(None), AXIS_AUTO);
        assert_eq!(parse_axis_hz(Some(b"")), AXIS_AUTO);
        assert_eq!(parse_axis_hz(Some(b"auto")), AXIS_AUTO);
        assert_eq!(parse_axis_hz(Some(b" AUTO ")), AXIS_AUTO);
        assert_eq!(parse_axis_hz(Some(b"abc")), AXIS_AUTO);
        assert_eq!(parse_axis_hz(Some(b"-5")), AXIS_AUTO);
        assert_eq!(parse_axis_hz(Some(b"0")), 0);
        assert_eq!(parse_axis_hz(Some(b" 60 ")), 60);
        assert_eq!(parse_axis_hz(Some(b"250")), 250);
        assert_eq!(parse_axis_hz(Some(b"10")), 30);
        assert_eq!(parse_axis_hz(Some(b"5000")), 1000);
        assert_eq!(parse_axis_hz(Some(b"99999999999")), AXIS_AUTO);
    }

    #[test]
    fn auto_max_parsing() {
        assert_eq!(parse_auto_max(None), 60);
        assert_eq!(parse_auto_max(Some(b"")), 60);
        assert_eq!(parse_auto_max(Some(b"abc")), 60);
        assert_eq!(parse_auto_max(Some(b" 120 ")), 120);
        assert_eq!(parse_auto_max(Some(b"5")), 20);
        assert_eq!(parse_auto_max(Some(b"0")), 20);
        assert_eq!(parse_auto_max(Some(b"999")), 240);
    }

    #[test]
    fn credit_feed_freshness_and_stall_bound() {
        const MS: u64 = 1_000_000;
        let now = 10_000 * MS;
        // fresh: the counter, and a stall bound of two frame intervals but at least 50 ms
        assert_eq!(credit_feed(7, now - MS, 16 * MS, now), Some((7, 50 * MS)));
        assert_eq!(credit_feed(7, now, 40 * MS, now), Some((7, 80 * MS)));
        assert_eq!(credit_feed(7, now, 0, now), Some((7, 50 * MS)));
        // not fresh: no frames yet, never stamped, or the last frame 250+ ms ago
        assert_eq!(credit_feed(0, now, 16 * MS, now), None);
        assert_eq!(credit_feed(7, 0, 16 * MS, now), None);
        assert_eq!(credit_feed(7, now - 251 * MS, 16 * MS, now), None);
        assert!(credit_feed(7, now - 249 * MS, 16 * MS, now).is_some());
        // a frame stamped a moment after the reader's clock read is fresh
        assert!(credit_feed(7, now + MS, 16 * MS, now).is_some());
    }

    #[test]
    fn handle_encoding_rejects_stale_and_garbage() {
        assert!(acquire(0).is_none());
        assert!(acquire(0xff).is_none());
        assert!(acquire((5 << 8) | 200).is_none());
        assert!(acquire((5 << 8) | 3).is_none());
    }
}
