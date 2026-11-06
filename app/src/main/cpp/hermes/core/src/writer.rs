//! App-side writer engine for one pad slot.
//!
//! The writer keeps the slot's complete logical state (pad buttons and axes, the system-button
//! overlay, connection, unplug count, per-button edge counters) in process memory, so a publish
//! that cannot reach a backing file loses nothing: the next file it can write starts from the full
//! state. A publish is O(1): diff, at most 11 journal stores, one state copy, one futex wake. It
//! never allocates, never blocks and does no file I/O (file work happens in `prepare`, `release`
//! and, rate-limited to once a second, when a publish finds the backing missing).
//!
//! Backing lifecycle:
//! * `prepare` creates `<dir>/pad<slot>` if needed: a new file is fully initialised under a
//!   temporary name, locked (flock, so readers can tell a dead writer from an idle one) and then
//!   renamed over the path, so a reader can never see a half-built file. The file it replaces (an
//!   older one of ours, or one left by an earlier app process) is marked retired and its sleepers
//!   are woken, which makes every reader re-attach to the new file.
//! * Control block (format minor 5): the writer keeps the slot's control record (session pacing,
//!   XInput DLL mode, force-feedback mode since minor 6, controller identity) in process memory too, and every file it creates gets
//!   that record and its gen BEFORE the file's magic is published, so a reader can never attach to
//!   a new or healed file and see a default identity first. Ordering contract for the app: a
//!   `set_identity` must have returned before the slot's discovery node is created (before the
//!   game can enumerate the device), or the first attach may still see the previous identity.
//! * `release` retires the file and unlinks it. Readers keep their mapping (an unlinked file stays
//!   valid while mapped), show a neutral pad, and re-attach when the next `prepare` creates a new
//!   file. Files are never truncated while in use, so a reader's mapping can never fault.

use core::sync::atomic::Ordering::{Relaxed, Release, SeqCst};
use core::sync::atomic::{AtomicPtr, AtomicU32, AtomicU64};

use crate::abi::*;
use crate::layout::*;
use crate::pad::PadInput;
use crate::proto;
use crate::sys::{self, PathBuf};

const DIR_CAP: usize = 1024;
const HEAL_INTERVAL_NS: u64 = 1_000_000_000;

pub const STAT_PUBLISHES: u32 = 0;
pub const STAT_EDGES: u32 = 1;
pub const STAT_WAKE_CALLS: u32 = 2;
pub const STAT_WOKEN: u32 = 3;
pub const STAT_UNPLUGS: u32 = 4;
pub const STAT_READER_ATTACHES: u32 = 5;
pub const STAT_READER_REATTACHES: u32 = 6;
pub const STAT_READER_TAPS: u32 = 7;
pub const STAT_READER_COALESCED: u32 = 8;
pub const STAT_READER_RESYNCS: u32 = 9;
pub const STAT_READER_FRAMES: u32 = 10;
pub const STAT_READER_EVENTS: u32 = 11;
pub const STAT_READER_REMOVALS: u32 = 12;
pub const STAT_INSTANCE: u32 = 13;
pub const STAT_FAILED_PUBLISHES: u32 = 14;
pub const STAT_HAS_BACKING: u32 = 15;
pub const STAT_READER_PACED_FRAMES: u32 = 16;
pub const STAT_READER_MERGED_AXIS: u32 = 17;
pub const STAT_READER_AXIS_HZ: u32 = 18;
pub const STAT_READER_AUTO_FALLBACKS: u32 = 19;
pub const STAT_FRAME_INTERVAL_NS: u32 = 20;
pub const STAT_GAME_FRAMES: u32 = 21;

pub const STAT_READER_AXIS_MODE: u32 = 22;
pub const STAT_FRAME_WAKES: u32 = 23;
pub const STAT_READER_HELPER_WAKES: u32 = 24;
pub const STAT_READER_AUTO_MAX: u32 = 25;
/// Control block (format 1.5): the control gen a reader last applied (0 = none / older reader).
pub const STAT_READER_CTL_GEN_APPLIED: u32 = 26;
/// The control gen whose identity the last reader attach took.
pub const STAT_READER_ID_GEN_ATTACHED: u32 = 27;
/// Identity queries readers answered from this file.
pub const STAT_READER_IDENTITY_READS: u32 = 28;
/// The writer's own control gen (what readers converge to).
pub const STAT_CONTROL_GEN: u32 = 29;
/// Force feedback (format 1.6), summed over every reader: effects uploaded (new or updated)...
pub const STAT_READER_FF_UPLOADS: u32 = 30;
/// ...effects started (play with a count)...
pub const STAT_READER_FF_PLAYS: u32 = 31;
/// ...rumble datagrams the guest's force-feedback engine sent to the app...
pub const STAT_READER_FF_DATAGRAMS: u32 = 32;
/// ...and anti-buzz guard fades it began (a constant force held steady for 2 s).
pub const STAT_READER_FF_GUARD_FADES: u32 = 33;

// ---------------------------------------------------------------------------------------------
// Game frame feed: the app's present threads report every frame the game presents. Each one is
// counted into the header of every live slot file; readers in auto pacing may send one stick-only
// frame per new count (a frame credit), so a game that takes one input report per rendered frame
// is never fed more than it drains.

/// A gap this long (pause, alt-tab, loading) is not reported as a frame interval.
pub const FRAME_STALE_GAP_NS: u64 = 1_000_000_000;
/// Presents closer than this are one frame presented twice (and credits never exceed 1 kHz).
pub const FRAME_MIN_INTERVAL_NS: u64 = 1_000_000;

struct FrameFeed {
    last: AtomicU64,
    gap: AtomicU64,
    count: AtomicU64,
    wakes: AtomicU64,
}

static FRAME_FEED: FrameFeed =
    FrameFeed { last: AtomicU64::new(0), gap: AtomicU64::new(0), count: AtomicU64::new(0), wakes: AtomicU64::new(0) };

/// A slot file the feed writes into. `users` lets the owner wait out a frame thread that is
/// storing into the mapping before it unmaps it; the frame thread itself never waits.
struct Target {
    ptr: AtomicPtr<u8>,
    users: AtomicU32,
}

const TARGET: Target = Target { ptr: AtomicPtr::new(core::ptr::null_mut()), users: AtomicU32::new(0) };
static TARGETS: [Target; 4] = [TARGET; 4];

fn target_set(slot: u32, p: *mut u8) {
    if let Some(t) = TARGETS.get(slot as usize) {
        t.ptr.store(p, SeqCst);
    }
}

/// Stop the feed writing into `p` and wait until no frame thread is inside it.
fn target_clear(slot: u32, p: *mut u8) {
    if let Some(t) = TARGETS.get(slot as usize) {
        if t.ptr.compare_exchange(p, core::ptr::null_mut(), SeqCst, SeqCst).is_ok() {
            let mut n = 0u32;
            while t.users.load(SeqCst) != 0 {
                n += 1;
                if n < 64 {
                    core::hint::spin_loop();
                } else {
                    sys::sched_yield();
                }
            }
        }
    }
}

/// One frame presented by the game, at `now` (CLOCK_MONOTONIC ns). Wait-free: a few atomic stores
/// into each live slot header, plus a futex wake only for a file a reader is waiting on.
pub fn game_frame(now: u64) {
    let f = &FRAME_FEED;
    let last = f.last.load(Relaxed);
    let gap = if last == 0 { 0 } else { now.wrapping_sub(last) };
    if last != 0 && gap < FRAME_MIN_INTERVAL_NS {
        return;
    }
    let gap = if gap > FRAME_STALE_GAP_NS { 0 } else { gap };
    f.gap.store(gap, Relaxed);
    f.last.store(now, Relaxed);
    let count = f.count.fetch_add(1, Relaxed).wrapping_add(1);
    for t in TARGETS.iter() {
        t.users.fetch_add(1, SeqCst);
        let p = t.ptr.load(SeqCst);
        if !p.is_null() {
            let h = &unsafe { Shared::at(p) }.header;
            h.frame_interval_ns.store(gap, Relaxed);
            h.frame_time_ns.store(now, Relaxed);
            // SeqCst against the readers' frame_waiters registration: either they see this
            // count when they re-check after registering, or this sees them and rings.
            h.frame_count.store(count, SeqCst);
            if h.frame_waiters.load(SeqCst) != 0 {
                h.bell.fetch_add(1, Release);
                sys::futex_wake(&h.bell, i32::MAX, true);
                f.wakes.fetch_add(1, Relaxed);
            }
        }
        t.users.fetch_sub(1, Release);
    }
}

/// The feed's (last frame interval, last frame time, frame count); zeros before any frame.
pub fn frame_info() -> (u64, u64, u64) {
    (FRAME_FEED.gap.load(Relaxed), FRAME_FEED.last.load(Relaxed), FRAME_FEED.count.load(Relaxed))
}

/// Futex wakes the feed has issued for frames (only while some reader waited for one).
pub fn frame_wakes() -> u64 {
    FRAME_FEED.wakes.load(Relaxed)
}

struct Backing {
    ptr: *mut u8,
    fd: i32,
    dev: u64,
    ino: u64,
    instance: u64,
}

impl Backing {
    fn shared<'a>(&self) -> &'a Shared {
        unsafe { Shared::at(self.ptr) }
    }
}

fn drop_backing(slot: u32, b: Backing) {
    target_clear(slot, b.ptr);
    sys::munmap(b.ptr, FILE_SIZE);
    sys::close(b.fd); // also drops the flock
}

/// Tell every reader of `sh` to move on: retired flag, bell, wake.
fn retire_shared(sh: &Shared) {
    sh.header.retired.store(1, Release);
    sh.header.bell.fetch_add(1, Release);
    sys::futex_wake(&sh.header.bell, i32::MAX, true);
}

/// Map whatever file currently sits at `path`, if it is one of ours, so it can be retired once
/// the replacement is in place.
fn open_existing(path: &PathBuf) -> Option<*mut u8> {
    let fd = sys::openat(path.as_ptr(), sys::O_RDWR | sys::O_CLOEXEC, 0).ok()?;
    let ok = matches!(sys::fstat(fd), Ok(st) if st.is_regular() && st.size >= FILE_SIZE as i64);
    let p = if ok { sys::mmap(FILE_SIZE, sys::PROT_READ | sys::PROT_WRITE, sys::MAP_SHARED, fd).ok() } else { None };
    sys::close(fd);
    let p = p?;
    if unsafe { Shared::at(p) }.header.magic.load(core::sync::atomic::Ordering::Acquire) == MAGIC {
        Some(p)
    } else {
        sys::munmap(p, FILE_SIZE);
        None
    }
}

fn make_instance(slot: u32, nonce: u32) -> u64 {
    let (sec, usec) = sys::realtime_us();
    let v = sys::mono_ns()
        ^ (((sec as u64).wrapping_mul(1_000_000).wrapping_add(usec as u64)) << 7)
        ^ ((sys::getpid() as u64) << 40)
        ^ ((slot as u64) << 61)
        ^ ((nonce as u64) << 20);
    if v == 0 {
        1
    } else {
        v
    }
}

fn init_header(sh: &Shared, slot: u32, instance: u64) {
    let h = &sh.header;
    h.major.store(FORMAT_MAJOR, Relaxed);
    h.minor.store(FORMAT_MINOR, Relaxed);
    h.file_size.store(FILE_SIZE as u32, Relaxed);
    h.header_size.store(HEADER_SIZE as u32, Relaxed);
    h.copy_size.store(COPY_SIZE as u32, Relaxed);
    h.copy_count.store(COPY_COUNT as u32, Relaxed);
    h.journal_len.store(JOURNAL_LEN as u32, Relaxed);
    h.slot.store(slot, Relaxed);
    h.instance.store(instance, Relaxed);
    h.writer_pid.store(sys::getpid() as u32, Relaxed);
    h.created_ns.store(sys::mono_ns(), Relaxed);
}

/// The writer for one slot. Not internally synchronised: the owner serialises calls.
pub struct SlotWriter {
    slot: u32,
    pad: PadInput,
    overlay: u16,
    connected: bool,
    counts: [u32; BUTTONS],
    edges: u32,
    unplug: u32,
    pub_axes: [i32; AXES],
    pub_flags: u32,
    pub_unplug: u32,
    pub_seq: u32,
    dirty: bool,
    backing: Option<Backing>,
    dir: [u8; DIR_CAP],
    dir_len: usize,
    released: bool,
    last_heal: u64,
    nonce: u32,
    failures: u64,
    /// The control record every file of this slot carries, and its gen (1 = the initial record).
    ctl: ControlState,
    ctl_gen: u32,
}

// The raw pointer is to a MAP_SHARED mapping owned by this writer; moving the writer between
// threads is fine (the JNI layer keeps each one behind a mutex).
unsafe impl Send for SlotWriter {}

impl SlotWriter {
    pub const fn new(slot: u32) -> SlotWriter {
        SlotWriter {
            slot,
            pad: PadInput::NEUTRAL,
            overlay: 0,
            connected: false,
            counts: [0; BUTTONS],
            edges: 0,
            unplug: 0,
            pub_axes: [0; AXES],
            pub_flags: 0,
            pub_unplug: 0,
            pub_seq: 0,
            dirty: false,
            backing: None,
            dir: [0; DIR_CAP],
            dir_len: 0,
            released: false,
            last_heal: 0,
            nonce: 0,
            failures: 0,
            ctl: ControlState::DEFAULT,
            ctl_gen: 1,
        }
    }

    pub fn slot(&self) -> u32 {
        self.slot
    }

    pub fn has_backing(&self) -> bool {
        self.backing.is_some()
    }

    pub fn instance(&self) -> u64 {
        self.backing.as_ref().map(|b| b.instance).unwrap_or(0)
    }

    pub fn is_connected(&self) -> bool {
        self.connected
    }

    /// The visible pressed-button mask (pad OR overlay), as last committed.
    pub fn buttons(&self) -> u16 {
        parity(&self.counts)
    }

    fn dir_path(&self) -> Option<PathBuf> {
        let mut p = PathBuf::new();
        if self.dir_len == 0 || !p.push(&self.dir[..self.dir_len]) {
            return None;
        }
        Some(p)
    }

    /// `<dir>/pad<slot>`.
    pub fn pad_path(&self) -> Option<PathBuf> {
        let mut p = self.dir_path()?;
        if p.push(b"/pad") && p.push_u(self.slot as u64) {
            Some(p)
        } else {
            None
        }
    }

    /// Make sure `<dir>/pad<slot>` exists, is ours and is live. Idempotent; cheap when it is.
    pub fn prepare(&mut self, dir: &[u8]) -> Result<(), i32> {
        let mut d = dir;
        while d.len() > 1 && d[d.len() - 1] == b'/' {
            d = &d[..d.len() - 1];
        }
        if d.is_empty() || d.len() > DIR_CAP - 64 || d.contains(&0) {
            return Err(sys::EINVAL);
        }
        if self.backing.is_some() && &self.dir[..self.dir_len] != d {
            self.release(); // moving to another directory: give up the old file first
        }
        self.dir[..d.len()].copy_from_slice(d);
        self.dir_len = d.len();
        self.released = false;
        if let Some(dp) = self.dir_path() {
            let _ = sys::mkdirat(dp.as_ptr(), 0o700);
        }
        let path = self.pad_path().ok_or(sys::ENAMETOOLONG)?;
        if let Some(b) = &self.backing {
            if !b.shared().is_retired() {
                if let Ok(st) = sys::stat(path.as_ptr()) {
                    if st.dev == b.dev && st.ino == b.ino {
                        return Ok(());
                    }
                }
            }
        }
        self.create_backing(&path)
    }

    fn create_backing(&mut self, path: &PathBuf) -> Result<(), i32> {
        let mut tmp = self.dir_path().ok_or(sys::ENAMETOOLONG)?;
        self.nonce = self.nonce.wrapping_add(1);
        let named = tmp.push(b"/.pad")
            && tmp.push_u(self.slot as u64)
            && tmp.push(b".")
            && tmp.push_u(sys::getpid() as u64)
            && tmp.push(b".")
            && tmp.push_u(self.nonce as u64)
            && tmp.push(b".tmp");
        if !named {
            return Err(sys::ENAMETOOLONG);
        }
        let fd = sys::openat(tmp.as_ptr(), sys::O_RDWR | sys::O_CREAT | sys::O_EXCL | sys::O_CLOEXEC, 0o600)?;
        let fail = |e: i32, p: *mut u8| -> i32 {
            sys::munmap(p, FILE_SIZE);
            sys::close(fd);
            let _ = sys::unlinkat(tmp.as_ptr());
            e
        };
        if let Err(e) = sys::ftruncate(fd, FILE_SIZE) {
            return Err(fail(e, core::ptr::null_mut()));
        }
        let p = match sys::mmap(FILE_SIZE, sys::PROT_READ | sys::PROT_WRITE, sys::MAP_SHARED, fd) {
            Ok(p) => p,
            Err(e) => return Err(fail(e, core::ptr::null_mut())),
        };
        let st = match sys::fstat(fd) {
            Ok(s) => s,
            Err(e) => return Err(fail(e, p)),
        };
        let sh = unsafe { Shared::at(p) };
        let instance = make_instance(self.slot, self.nonce);
        init_header(sh, self.slot, instance);
        // The control record, complete, before anything is published: nobody can map this file
        // yet, so plain stores do; the Release stores of the two magics order them for readers.
        let c = &sh.ctl;
        c.size.store(CTL_SIZE as u32, Relaxed);
        c.seq.store(0, Relaxed);
        for (w, v) in c.rec.iter().zip(self.ctl.encode().iter()) {
            w.store(*v, Relaxed);
        }
        c.gen.store(self.ctl_gen, Relaxed);
        c.magic.store(CTL_MAGIC, Release);
        let (iv, ft, fc) = frame_info();
        sh.header.frame_interval_ns.store(iv, Relaxed);
        sh.header.frame_time_ns.store(ft, Relaxed);
        sh.header.frame_count.store(fc, Relaxed);
        // A new file starts from the complete logical state; its journal starts empty (readers
        // re-attaching to it begin with a full-state frame anyway).
        self.pub_seq = 0;
        self.write_copy(sh);
        sh.header.magic.store(MAGIC, Release);
        // Held for as long as this process keeps the file: a reader that can take a shared lock
        // knows the writer is gone (crashed app) and shows a neutral pad instead of frozen input.
        let _ = sys::flock(fd, sys::LOCK_EX | sys::LOCK_NB);
        // Whatever sits at the path now (normally our own previous file, or one an earlier app
        // process left behind), mapped before the rename so it can be retired after it.
        let previous = open_existing(path);
        if let Err(e) = sys::renameat(tmp.as_ptr(), path.as_ptr()) {
            if let Some(q) = previous {
                sys::munmap(q, FILE_SIZE);
            }
            return Err(fail(e, p));
        }
        if let Some(old) = self.backing.take() {
            retire_shared(old.shared());
            drop_backing(self.slot, old);
        }
        if let Some(q) = previous {
            let prev = unsafe { Shared::at(q) };
            if !prev.is_retired() {
                retire_shared(prev);
            }
            sys::munmap(q, FILE_SIZE);
        }
        self.backing = Some(Backing { ptr: p, fd, dev: st.dev, ino: st.ino, instance });
        target_set(self.slot, p);
        self.dirty = false;
        Ok(())
    }

    /// Retire and unlink the backing (session end). The logical state is kept; the next
    /// `prepare` publishes it into a new file.
    pub fn release(&mut self) {
        if let Some(b) = self.backing.take() {
            retire_shared(b.shared());
            if let Some(path) = self.pad_path() {
                if let Ok(st) = sys::stat(path.as_ptr()) {
                    if st.dev == b.dev && st.ino == b.ino {
                        let _ = sys::unlinkat(path.as_ptr());
                    }
                }
            }
            drop_backing(self.slot, b);
        }
        self.released = true;
    }

    fn write_copy(&mut self, sh: &Shared) {
        self.pub_seq = self.pub_seq.wrapping_add(1);
        if self.pub_seq == 0 {
            self.pub_seq = 1;
        }
        let snap = Snapshot {
            pub_seq: self.pub_seq,
            flags: self.pub_flags,
            unplug: self.pub_unplug,
            edges: self.edges,
            counts: self.counts,
            axes: self.pub_axes,
            time_ns: sys::mono_ns(),
        };
        proto::publish(&sh.copies, &sh.header.published, self.pub_seq, &snap.encode());
        sh.header.bell.fetch_add(1, Release);
        let woken = sys::futex_wake(&sh.header.bell, i32::MAX, true);
        let h = &sh.header;
        h.w_publishes.fetch_add(1, Relaxed);
        h.w_wake_calls.fetch_add(1, Relaxed);
        h.w_woken.fetch_add(woken as u64, Relaxed);
        h.w_edges.store(self.edges as u64, Relaxed);
    }

    /// Store a changed control record: into the live file if there is one (seqlock, no bell:
    /// readers pick it up at their next decision), and in any case into every file created later.
    fn update_control(&mut self, st: ControlState) {
        if st == self.ctl {
            return;
        }
        self.ctl = st;
        let next = match self.ctl_gen.wrapping_add(1) {
            0 => 1,
            g => g,
        };
        if let Some(b) = &self.backing {
            let c = &b.shared().ctl;
            let g = proto::ctl_write::<_, proto::NativeFence>(&c.seq, &c.gen, &c.rec, &st.encode());
            debug_assert_eq!(g, next);
        }
        self.ctl_gen = next;
    }

    /// The controller identity readers of this slot report from their next attach on (None = no
    /// identity: readers use their own default). Call it before the slot's device node is made.
    pub fn set_identity(&mut self, id: Option<Identity>) {
        let mut st = self.ctl;
        st.id = id;
        self.update_control(st);
    }

    /// Session settings (`CTL_AXIS_*` mode, fixed rate, auto ceiling, XInput DLL mode and flags).
    /// Out-of-range values are normalised here; readers clamp again whatever they read.
    pub fn set_session(&mut self, axis_mode: u32, axis_hz: u32, auto_max: u32, xinput_mode: u32, xinput_flags: u32) {
        let mut st = self.ctl;
        st.axis_mode = if axis_mode <= CTL_AXIS_UNPACED { axis_mode } else { CTL_AXIS_UNSET };
        use crate::guest::{AUTO_MAX_MAX, AUTO_MAX_MIN, AXIS_HZ_MAX, AXIS_HZ_MIN};
        st.axis_hz = if axis_hz == 0 { 0 } else { axis_hz.clamp(AXIS_HZ_MIN, AXIS_HZ_MAX) };
        st.axis_auto_max = if auto_max == 0 { 0 } else { auto_max.clamp(AUTO_MAX_MIN, AUTO_MAX_MAX) };
        st.xinput_mode = if xinput_mode <= CTL_XINPUT_FORWARD { xinput_mode } else { CTL_XINPUT_UNSET };
        st.xinput_flags = xinput_flags & CTL_XI_FLAGS_KNOWN;
        self.update_control(st);
    }

    /// DirectInput force feedback for this slot's handles (`CTL_FF_*`, anything else = unset).
    /// A guest handle reads it when it attaches, so it applies to handles opened after the call.
    pub fn set_ff_mode(&mut self, mode: u32) {
        let mut st = self.ctl;
        st.ff_mode = if mode <= CTL_FF_OFF { mode } else { CTL_FF_UNSET };
        self.update_control(st);
    }

    pub fn control(&self) -> (u32, ControlState) {
        (self.ctl_gen, self.ctl)
    }

    fn try_heal(&mut self) {
        if self.released || self.dir_len == 0 {
            return;
        }
        let now = sys::mono_ns();
        if self.last_heal != 0 && now.wrapping_sub(self.last_heal) < HEAL_INTERVAL_NS {
            return;
        }
        self.last_heal = now;
        if let Some(path) = self.pad_path() {
            let _ = self.create_backing(&path);
        }
    }

    /// Diff the logical state against what was last published and publish the difference.
    /// Returns true if the state is now visible to readers.
    fn commit(&mut self, force: bool) -> bool {
        let want = (self.pad.buttons | self.overlay) & ALL_BUTTONS;
        let diff = want ^ parity(&self.counts);
        let flags = if self.connected { FLAG_CONNECTED } else { 0 };
        let changed = force
            || self.dirty
            || diff != 0
            || self.pad.axes != self.pub_axes
            || flags != self.pub_flags
            || self.unplug != self.pub_unplug;
        if !changed {
            return self.backing.is_some();
        }
        let shp: *const u8 = match &self.backing {
            Some(b) => b.ptr,
            None => core::ptr::null(),
        };
        let sh: Option<&Shared> = if shp.is_null() { None } else { Some(unsafe { Shared::at(shp) }) };
        let mut first = true;
        for b in 0..BUTTONS {
            if diff & (1 << b) != 0 {
                self.edges = self.edges.wrapping_add(1);
                self.counts[b] = self.counts[b].wrapping_add(1);
                if let Some(sh) = sh {
                    let mut p = b as u32;
                    if want & (1 << b) != 0 {
                        p |= J_PRESS;
                    }
                    if first {
                        p |= J_GROUP;
                    }
                    proto::journal_put(&sh.journal, self.edges, p);
                }
                first = false;
            }
        }
        self.pub_axes = self.pad.axes;
        self.pub_flags = flags;
        self.pub_unplug = self.unplug;
        match sh {
            Some(sh) => {
                self.write_copy(sh);
                self.dirty = false;
                true
            }
            None => {
                self.dirty = true;
                self.failures += 1;
                self.try_heal();
                self.backing.is_some()
            }
        }
    }

    /// The pad's own complete state (absolute, not a delta).
    pub fn set_pad(&mut self, p: PadInput) -> bool {
        let mut p = p;
        p.buttons &= ALL_BUTTONS;
        for i in 0..AXES {
            p.axes[i] = clamp_axis(i, p.axes[i]);
        }
        self.pad = p;
        self.commit(false)
    }

    /// Buttons the app itself holds on top of the pad (Guide / A for the Quick Access Menu).
    /// ORed with the pad, so clearing it never releases a button the user is holding.
    pub fn set_overlay(&mut self, buttons: u16) -> bool {
        self.overlay = buttons & ALL_BUTTONS;
        self.commit(false)
    }

    /// Neutral: every button up (pad and overlay), every axis 0. Stays connected.
    pub fn reset(&mut self) -> bool {
        self.pad = PadInput::NEUTRAL;
        self.overlay = 0;
        self.commit(false)
    }

    /// A controller is assigned to this slot.
    pub fn open(&mut self) -> bool {
        self.connected = true;
        self.commit(false)
    }

    /// The controller was unplugged: neutralise, then make every attached reader see a removal.
    pub fn unplug(&mut self) -> bool {
        let a = self.reset();
        self.connected = false;
        self.unplug = self.unplug.wrapping_add(1);
        let b = self.commit(true);
        if let Some(bk) = &self.backing {
            bk.shared().header.w_unplugs.fetch_add(1, Relaxed);
        }
        a && b
    }

    pub fn stat(&self, which: u32) -> u64 {
        match which {
            STAT_EDGES => return self.edges as u64,
            STAT_FAILED_PUBLISHES => return self.failures,
            STAT_HAS_BACKING => return self.backing.is_some() as u64,
            STAT_INSTANCE => return self.instance(),
            STAT_FRAME_INTERVAL_NS => return FRAME_FEED.gap.load(Relaxed),
            STAT_GAME_FRAMES => return FRAME_FEED.count.load(Relaxed),
            STAT_FRAME_WAKES => return FRAME_FEED.wakes.load(Relaxed),
            STAT_CONTROL_GEN => return self.ctl_gen as u64,
            _ => {}
        }
        let sh = match &self.backing {
            Some(b) => b.shared(),
            None => return 0,
        };
        let h = &sh.header;
        let v = match which {
            STAT_PUBLISHES => &h.w_publishes,
            STAT_WAKE_CALLS => &h.w_wake_calls,
            STAT_WOKEN => &h.w_woken,
            STAT_UNPLUGS => &h.w_unplugs,
            STAT_READER_ATTACHES => &h.r_attaches,
            STAT_READER_REATTACHES => &h.r_reattaches,
            STAT_READER_TAPS => &h.r_taps,
            STAT_READER_COALESCED => &h.r_coalesced,
            STAT_READER_RESYNCS => &h.r_resyncs,
            STAT_READER_FRAMES => &h.r_frames,
            STAT_READER_EVENTS => &h.r_events,
            STAT_READER_REMOVALS => &h.r_removals,
            STAT_READER_PACED_FRAMES => &h.r_paced_frames,
            STAT_READER_MERGED_AXIS => &h.r_merged_axis,
            STAT_READER_AUTO_FALLBACKS => &h.r_auto_fallbacks,
            STAT_READER_HELPER_WAKES => &h.r_helper_wakes,
            STAT_READER_AXIS_HZ => return h.r_axis_hz.load(Relaxed) as u64,
            STAT_READER_AXIS_MODE => return h.r_axis_mode.load(Relaxed) as u64,
            STAT_READER_AUTO_MAX => return h.r_auto_max.load(Relaxed) as u64,
            STAT_READER_CTL_GEN_APPLIED => return sh.ctl.r_ctl_gen_applied.load(Relaxed) as u64,
            STAT_READER_ID_GEN_ATTACHED => return sh.ctl.r_id_gen_attached.load(Relaxed) as u64,
            STAT_READER_IDENTITY_READS => return sh.ctl.r_identity_reads.load(Relaxed) as u64,
            STAT_READER_FF_UPLOADS => return sh.ctl.r_ff_uploads.load(Relaxed) as u64,
            STAT_READER_FF_PLAYS => return sh.ctl.r_ff_plays.load(Relaxed) as u64,
            STAT_READER_FF_DATAGRAMS => return sh.ctl.r_ff_datagrams.load(Relaxed) as u64,
            STAT_READER_FF_GUARD_FADES => return sh.ctl.r_ff_guard_fades.load(Relaxed) as u64,
            _ => return 0,
        };
        v.load(Relaxed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::frame::{Cursor, Frame, FrameStats, Journal};
    use core::sync::atomic::Ordering::Acquire;

    fn tmpdir(tag: &str) -> std::path::PathBuf {
        use std::sync::atomic::{AtomicU32, Ordering};
        static N: AtomicU32 = AtomicU32::new(0);
        let d = std::env::temp_dir().join(format!(
            "hermes-w-{}-{}-{}",
            tag,
            std::process::id(),
            N.fetch_add(1, Ordering::Relaxed)
        ));
        std::fs::create_dir_all(&d).unwrap();
        d
    }

    struct Map(*mut u8);
    impl Map {
        fn open(path: &std::path::Path) -> Map {
            let mut p = PathBuf::new();
            assert!(p.push(path.to_str().unwrap().as_bytes()));
            let fd = sys::openat(p.as_ptr(), sys::O_RDONLY | sys::O_CLOEXEC, 0).unwrap();
            let m = sys::mmap(FILE_SIZE, sys::PROT_READ, sys::MAP_SHARED, fd).unwrap();
            sys::close(fd);
            Map(m)
        }
        fn sh(&self) -> &Shared {
            unsafe { Shared::at(self.0) }
        }
    }
    impl Journal for Map {
        fn get(&self, idx: u32) -> Option<u32> {
            self.sh().journal_get(idx)
        }
    }

    fn pad(buttons: u16) -> PadInput {
        PadInput { buttons, axes: [0; AXES] }
    }

    #[test]
    fn the_control_record_is_in_every_file_the_slot_gets() {
        let dir = tmpdir("ctl");
        let ds = dir.to_str().unwrap().as_bytes().to_vec();
        let mut w = SlotWriter::new(1);
        assert_eq!(w.control().0, 1, "the initial record is gen 1");
        // set before any file exists: kept in process memory
        let id = Identity::new(3, 0x045e, 0x028e, 0x0114, b"xbox360", b"Xbox 360 Controller (1)");
        w.set_identity(Some(id));
        w.set_session(CTL_AXIS_FIXED, 90, 0, CTL_XINPUT_FORWARD, CTL_XI_FORWARD_AVAILABLE | 0x80);
        let (gen, st) = w.control();
        assert_eq!(gen, 3);
        assert_eq!(st.xinput_flags, CTL_XI_FORWARD_AVAILABLE, "unknown flag bits dropped");
        w.prepare(&ds).unwrap();
        let m = Map::open(&dir.join("pad1"));
        let sh = m.sh();
        assert!(sh.control_present());
        assert_eq!(sh.header.minor.load(Relaxed), FORMAT_MINOR);
        assert_eq!(FORMAT_MINOR, 6);
        assert_eq!(sh.read_control(), Some((gen, st)), "a new file starts with the full record");
        assert_eq!(sh.ctl.seq.load(Relaxed) & 1, 0);

        // an update reaches the live file, gen + 1, without ringing the bell
        let bell = sh.header.bell.load(Relaxed);
        w.set_identity(None);
        assert_eq!(sh.read_control().map(|(g, s)| (g, s.id)), Some((gen + 1, None)));
        assert_eq!(sh.header.bell.load(Relaxed), bell, "control updates never ring the bell");
        // an identical update is not a change
        w.set_identity(None);
        assert_eq!(w.control().0, gen + 1);
        assert_eq!(w.stat(STAT_CONTROL_GEN), (gen + 1) as u64);

        // a recreated file (session end, then a new session) carries the same record and gen
        let id2 = Identity::new(3, 0x054c, 0x0ce6, 0x8111, b"ds5", "DualSense \u{2013} P2".as_bytes());
        w.set_identity(Some(id2));
        let expect = w.control();
        w.release();
        w.prepare(&ds).unwrap();
        let m2 = Map::open(&dir.join("pad1"));
        assert_ne!(m2.sh().header.instance.load(Relaxed), sh.header.instance.load(Relaxed));
        assert_eq!(m2.sh().read_control(), Some(expect));
        assert_eq!(m2.sh().read_control().unwrap().1.id, Some(id2));
        // and so does a replacement made while the old file is still mapped (the heal path:
        // the file vanished behind the writer's back)
        std::fs::remove_file(dir.join("pad1")).unwrap();
        w.prepare(&ds).unwrap();
        let m3 = Map::open(&dir.join("pad1"));
        assert_ne!(m3.sh().header.instance.load(Relaxed), m2.sh().header.instance.load(Relaxed));
        assert_eq!(m3.sh().read_control(), Some(expect));
    }

    #[test]
    fn the_ff_mode_reaches_the_live_file_and_bumps_the_gen() {
        let dir = tmpdir("ffmode");
        let mut w = SlotWriter::new(0);
        w.prepare(dir.to_str().unwrap().as_bytes()).unwrap();
        let m = Map::open(&dir.join("pad0"));
        let sh = m.sh();
        assert!(sh.ff_stats_present());
        assert_eq!(sh.read_control().map(|(_, st)| st.ff_mode), Some(CTL_FF_UNSET));
        let g = w.control().0;
        w.set_ff_mode(CTL_FF_OFF);
        assert_eq!(sh.read_control(), Some((g + 1, ControlState { ff_mode: CTL_FF_OFF, ..ControlState::DEFAULT })));
        assert_eq!(sh.ctl.rec[rec::FF_MODE].load(Relaxed), CTL_FF_OFF, "the word at 0xB24");
        w.set_ff_mode(CTL_FF_OFF);
        assert_eq!(w.control().0, g + 1, "the same mode again is not a change");
        w.set_ff_mode(7);
        assert_eq!(w.control(), (g + 2, ControlState::DEFAULT), "unknown values are unset");
        // the reader counters (through the writer's own read-write mapping; Map is read-only)
        w.backing.as_ref().unwrap().shared().ctl.r_ff_datagrams.fetch_add(5, Relaxed);
        assert_eq!(w.stat(STAT_READER_FF_DATAGRAMS), 5);
        assert_eq!(w.stat(STAT_READER_FF_UPLOADS), 0);
    }

    #[test]
    fn session_values_are_normalised() {
        let mut w = SlotWriter::new(2);
        w.set_session(7, 5, 1000, 9, u32::MAX);
        let (_, st) = w.control();
        assert_eq!((st.axis_mode, st.axis_hz, st.axis_auto_max, st.xinput_mode, st.xinput_flags), (CTL_AXIS_UNSET, 30, 240, CTL_XINPUT_UNSET, CTL_XI_FLAGS_KNOWN));
        w.set_session(CTL_AXIS_AUTO, 0, 0, CTL_XINPUT_DIRECT, 0);
        let (_, st) = w.control();
        assert_eq!((st.axis_mode, st.axis_hz, st.axis_auto_max, st.xinput_mode), (CTL_AXIS_AUTO, 0, 0, CTL_XINPUT_DIRECT));
    }

    #[test]
    fn prepare_creates_a_valid_file_and_is_idempotent() {
        let d = tmpdir("prep");
        let mut w = SlotWriter::new(2);
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        let inst = w.instance();
        assert_ne!(inst, 0);
        let m = Map::open(&d.join("pad2"));
        assert!(m.sh().valid_for(2));
        assert!(!m.sh().valid_for(1));
        assert!(!m.sh().is_retired());
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        assert_eq!(w.instance(), inst, "a second prepare reuses the live file");
        assert_eq!(std::fs::metadata(d.join("pad2")).unwrap().len(), FILE_SIZE as u64);
        // no temporary files left behind
        let names: Vec<_> = std::fs::read_dir(&d).unwrap().map(|e| e.unwrap().file_name()).collect();
        assert_eq!(names.len(), 1, "{:?}", names);
    }

    #[test]
    fn noop_publish_does_not_publish_or_wake() {
        let d = tmpdir("noop");
        let mut w = SlotWriter::new(0);
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        w.open();
        assert!(w.set_pad(PadInput { buttons: 1, axes: [5, 0, 0, 0, 0, 0, 0, 0] }));
        let m = Map::open(&d.join("pad0"));
        let bell = m.sh().header.bell.load(Acquire);
        let pubs = w.stat(STAT_PUBLISHES);
        for _ in 0..100 {
            assert!(w.set_pad(PadInput { buttons: 1, axes: [5, 0, 0, 0, 0, 0, 0, 0] }));
        }
        assert_eq!(m.sh().header.bell.load(Acquire), bell);
        assert_eq!(w.stat(STAT_PUBLISHES), pubs);
    }

    #[test]
    fn overlay_never_releases_a_held_button() {
        let d = tmpdir("ovl");
        let mut w = SlotWriter::new(0);
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        w.set_pad(pad(1 << B_A));
        w.set_overlay((1 << B_MODE) | (1 << B_A));
        assert_eq!(w.buttons(), (1 << B_A) | (1 << B_MODE));
        w.set_overlay(0);
        assert_eq!(w.buttons(), 1 << B_A, "the pad still holds A");
        w.set_pad(pad(0));
        assert_eq!(w.buttons(), 0);
    }

    #[test]
    fn reader_follows_the_file_through_publishes() {
        let d = tmpdir("follow");
        let mut w = SlotWriter::new(1);
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        w.open();
        w.set_pad(PadInput { buttons: 1 << B_B, axes: [1000, -1000, 0, 0, 10, 0, 0, 1] });
        let m = Map::open(&d.join("pad1"));
        let mut c = Cursor::new();
        let mut f = Frame::new();
        let mut st = FrameStats::default();
        let s = m.sh().snapshot(16).unwrap();
        assert_eq!(s.flags & FLAG_CONNECTED, FLAG_CONNECTED);
        assert!(c.next_frame(&s, &m, &mut f, &mut st));
        assert_eq!(f.events()[B_B].value, 1);
        // tap X between reads
        w.set_pad(PadInput { buttons: (1 << B_B) | (1 << B_X), axes: [1000, -1000, 0, 0, 10, 0, 0, 1] });
        w.set_pad(PadInput { buttons: 1 << B_B, axes: [0, 0, 0, 0, 0, 0, 0, 0] });
        let s = m.sh().snapshot(16).unwrap();
        let mut frames = Vec::new();
        while c.next_frame(&s, &m, &mut f, &mut st) {
            frames.push(f.events().to_vec());
        }
        assert_eq!(frames.len(), 2);
        assert!(frames[0].iter().any(|e| e.kind == EV_KEY && e.code == BTN_X && e.value == 1));
        assert!(frames[1].iter().any(|e| e.kind == EV_KEY && e.code == BTN_X && e.value == 0));
        assert!(frames[1].iter().any(|e| e.kind == EV_ABS && e.code == ABS_X && e.value == 0));
    }

    #[test]
    fn unplug_bumps_the_count_after_neutralising() {
        let d = tmpdir("unplug");
        let mut w = SlotWriter::new(0);
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        w.open();
        w.set_pad(pad(1 << B_Y));
        let m = Map::open(&d.join("pad0"));
        let before = m.sh().snapshot(16).unwrap();
        assert!(w.unplug());
        let after = m.sh().snapshot(16).unwrap();
        assert_eq!(after.unplug, before.unplug + 1);
        assert_eq!(after.buttons(), 0);
        assert_eq!(after.flags & FLAG_CONNECTED, 0);
        assert_eq!(w.stat(STAT_UNPLUGS), 1);
    }

    #[test]
    fn release_retires_and_unlinks_then_prepare_recreates_with_full_state() {
        let d = tmpdir("rel");
        let mut w = SlotWriter::new(3);
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        w.set_pad(pad(1 << B_START));
        let old = Map::open(&d.join("pad3"));
        w.release();
        assert!(old.sh().is_retired());
        assert!(!d.join("pad3").exists());
        assert!(!w.set_pad(pad((1 << B_START) | (1 << B_A))), "no backing after release");
        assert!(!d.join("pad3").exists(), "a publish after release does not recreate the file");
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        let new = Map::open(&d.join("pad3"));
        let s = new.sh().snapshot(16).unwrap();
        assert_eq!(s.buttons(), (1 << B_START) | (1 << B_A), "the new file carries the full state");
    }

    #[test]
    fn a_new_writer_retires_a_file_left_by_an_earlier_one() {
        let d = tmpdir("takeover");
        let mut a = SlotWriter::new(0);
        a.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        let first = Map::open(&d.join("pad0"));
        let bell = first.sh().header.bell.load(Acquire);
        let mut b = SlotWriter::new(0);
        b.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        assert!(first.sh().is_retired());
        assert_ne!(first.sh().header.bell.load(Acquire), bell, "sleepers on the old file are woken");
        let second = Map::open(&d.join("pad0"));
        assert!(!second.sh().is_retired());
        assert_ne!(second.sh().header.instance.load(Acquire), first.sh().header.instance.load(Acquire));
    }

    #[test]
    fn lost_backing_heals_with_full_state() {
        let d = tmpdir("heal");
        let mut w = SlotWriter::new(0);
        w.prepare(d.to_str().unwrap().as_bytes()).unwrap();
        // simulate the backing vanishing out from under the writer
        let b = w.backing.take().unwrap();
        drop_backing(0, b);
        std::fs::remove_file(d.join("pad0")).unwrap();
        assert!(w.set_pad(pad(1 << B_TL)), "the publish heals by recreating the file");
        let m = Map::open(&d.join("pad0"));
        assert_eq!(m.sh().snapshot(16).unwrap().buttons(), 1 << B_TL);
        assert_eq!(w.stat(STAT_FAILED_PUBLISHES), 1);
    }
}
