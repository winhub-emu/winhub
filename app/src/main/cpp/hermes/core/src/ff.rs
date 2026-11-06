//! Force feedback (API 3): the effects a guest's evdev layer uploads for a HERMES pad (the Linux
//! EV_FF interface: upload, erase, play, gain), mixed into the pad's two motors and sent to the app
//! as HXR1 rumble datagrams, the same datagram the HERMES-XInput DLL sends.
//!
//! **Effects.** Per slot up to `MAX_EFFECTS`, shared by every handle of the slot; the handle that
//! uploaded one owns it, and detaching that handle stops and erases it. Rumble, periodic (square,
//! triangle, sine, saw up, saw down), constant and ramp effects drive the motors; the four
//! condition effects (spring, friction, damper, inertia) are stored and playable but contribute
//! nothing (a pad has no positional force). A play runs an effect `count` times: replay_delay, then
//! replay_length ms, again; length 0 runs until stopped. Envelopes shape periodic, constant and
//! ramp amplitudes. Values are evdev's: levels -0x7FFF..0x7FFF, rumble magnitudes 0..0xFFFF.
//!
//! **Mixer.** Every TICK_NS while anything plays, each effect's value v becomes a motor share
//! s = |v| / 0x7FFF: constant and ramp on the strong (low-frequency) motor; a periodic at least
//! SLOW_PERIOD_MS long there too, sampled (the wave is felt); a faster one on the weak motor as a
//! steady buzz of |offset| + 0.7 |magnitude| (motor updates cannot follow it); rumble on both motors
//! as given. Each motor is the clamped sum times the slot's gain.
//!
//! **Anti-buzz guard.** A constant or ramp whose share stays within GUARD_BAND of one value for
//! GUARD_HOLD_NS fades out over GUARD_FADE_NS and stays off until it moves by more than the band or
//! is uploaded or played again: a racing game's steady centring force would otherwise run a rumble
//! motor flat out for the whole race.
//!
//! **Output.** One datagram per slot when a motor moved by SEND_STEP or crossed to or from zero, at
//! most one non-zero datagram per SEND_GAP_NS (a pending change goes out when the gap allows), a
//! refresh every REFRESH_NS while the motors run unchanged (the app plays one-shots of DURATION_MS,
//! so a guest that dies mid-rumble stops by itself), and a stop at once, once.
//!
//! [`Engine`] is the whole state machine with the clock passed in: tests drive it with an injected
//! clock and look at what it would send. The runtime below it keeps one engine per process behind a
//! short spin lock and one thread that ticks it, only while something plays (idle: no wake-ups), and
//! sends the datagrams with no lock held. Nothing here allocates; API calls never block on I/O.

use core::cell::UnsafeCell;
use core::ffi::c_void;
use core::ptr;
use core::sync::atomic::Ordering::{AcqRel, Acquire, Relaxed, Release};
use core::sync::atomic::{AtomicI32, AtomicU32, AtomicU64};

use crate::guest::{self, MAX_SLOTS, STATS};
use crate::layout::{CTL_FF_OFF, CTL_FF_ON, CTL_FF_UNSET};
use crate::msg::Msg;
use crate::sys;

pub const MAX_EFFECTS: usize = 16;

// Effect types, waveforms and the two device controls: the codes of linux/input-event-codes.h.
pub const FF_RUMBLE: u16 = 0x50;
pub const FF_PERIODIC: u16 = 0x51;
pub const FF_CONSTANT: u16 = 0x52;
pub const FF_SPRING: u16 = 0x53;
pub const FF_FRICTION: u16 = 0x54;
pub const FF_DAMPER: u16 = 0x55;
pub const FF_INERTIA: u16 = 0x56;
pub const FF_RAMP: u16 = 0x57;
pub const FF_SQUARE: u16 = 0x58;
pub const FF_TRIANGLE: u16 = 0x59;
pub const FF_SINE: u16 = 0x5a;
pub const FF_SAW_UP: u16 = 0x5b;
pub const FF_SAW_DOWN: u16 = 0x5c;
pub const FF_GAIN: u16 = 0x60;
pub const FF_AUTOCENTER: u16 = 0x61;
/// Bytes of the EV_FF capability bitmap (codes 0..0x7f).
pub const BITS_LEN: usize = 16;

const MS: u64 = 1_000_000;

// The engine's timing and thresholds, all in one place.
/// Mixer period while anything plays.
pub const TICK_NS: u64 = 10 * MS;
/// A periodic effect at least this slow (ms) is felt as a wave on the strong motor; a faster one is
/// a steady buzz on the weak motor.
pub const SLOW_PERIOD_MS: u16 = 80;
/// Anti-buzz guard: a constant / ramp share steady within GUARD_BAND for GUARD_HOLD_NS...
pub const GUARD_HOLD_NS: u64 = 2000 * MS;
/// ...fades to 0 over GUARD_FADE_NS.
pub const GUARD_FADE_NS: u64 = 500 * MS;
/// 0.02 of full scale, in motor units (FULL).
pub const GUARD_BAND: u32 = 1311;
/// Output: a motor change worth a datagram (1/32 of full scale).
pub const SEND_STEP: u32 = 2048;
/// At most one non-zero datagram per SEND_GAP_NS (25 a second); a pending change waits for it.
pub const SEND_GAP_NS: u64 = 40 * MS;
/// While the motors run unchanged: a refresh this often.
pub const REFRESH_NS: u64 = 1000 * MS;
/// The one-shot duration every datagram asks the app for (ms).
pub const DURATION_MS: u16 = 1500;
/// Failed sends: logged the first time, then at most once per FAIL_LOG_NS.
pub const FAIL_LOG_NS: u64 = 10_000 * MS;
/// The app's rumble listener on 127.0.0.1 (HERMES_XINPUT_RUMBLE_PORT overrides).
pub const DEFAULT_PORT: u16 = 7947;
pub const HXR1_CODE: u8 = 14;
pub const HXR1_SIZE: usize = 12;

/// Motor scale: 0..=FULL (the datagram's u16 per motor).
pub const FULL: u32 = 0xFFFF;
/// Effect values and envelope levels: -LEVEL_MAX..=LEVEL_MAX.
pub const LEVEL_MAX: i32 = 0x7FFF;
pub const GAIN_DEFAULT: u16 = 0xFFFF;

pub const ENV_FF: &[u8] = b"HERMES_DINPUT_FF\0";
pub const ENV_PORT: &[u8] = b"HERMES_XINPUT_RUMBLE_PORT\0";

const THREAD_STACK: usize = 128 * 1024;

// ---------------------------------------------------------------------------------------------
// the C structure and its decoding

/// `struct hermes_ff_envelope`: lengths in ms, levels absolute 0..0x7FFF.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Envelope {
    pub attack_length: u16,
    pub attack_level: u16,
    pub fade_length: u16,
    pub fade_level: u16,
}

impl Envelope {
    pub const NONE: Envelope = Envelope { attack_length: 0, attack_level: 0, fade_length: 0, fade_level: 0 };

    fn from_words(w: &[u16]) -> Envelope {
        let level = |v: u16| v.min(LEVEL_MAX as u16);
        Envelope { attack_length: w[0], attack_level: level(w[1]), fade_length: w[2], fade_level: level(w[3]) }
    }

    fn words(&self) -> [u16; 4] {
        [self.attack_length, self.attack_level, self.fade_length, self.fade_level]
    }
}

/// `struct hermes_ff_effect` (44 bytes, 2-byte aligned). `u` is the C union as 16 words:
/// rumble [strong, weak]; periodic [waveform, period, magnitude, offset, phase, envelope x 4];
/// constant [level, envelope x 4]; ramp [start, end, envelope x 4]; conditions: not read.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct FfEffect {
    pub kind: u16,
    pub id: i16,
    pub direction: u16,
    pub replay_length: u16,
    pub replay_delay: u16,
    pub reserved: u16,
    pub u: [u16; 16],
}

const _: () = {
    use core::mem::{align_of, offset_of, size_of};
    assert!(size_of::<FfEffect>() == 44);
    assert!(align_of::<FfEffect>() == 2);
    assert!(offset_of!(FfEffect, id) == 2);
    assert!(offset_of!(FfEffect, direction) == 4);
    assert!(offset_of!(FfEffect, replay_length) == 6);
    assert!(offset_of!(FfEffect, replay_delay) == 8);
    assert!(offset_of!(FfEffect, u) == 12);
};

impl FfEffect {
    /// A new effect (id -1) of `kind` with the union words `w`; plays until stopped, no delay.
    pub fn new(kind: u16, w: &[u16]) -> FfEffect {
        let mut e = FfEffect { kind, id: -1, ..FfEffect::default() };
        e.u[..w.len()].copy_from_slice(w);
        e
    }
    pub fn rumble(strong: u16, weak: u16) -> FfEffect {
        FfEffect::new(FF_RUMBLE, &[strong, weak])
    }
    pub fn periodic(wave: u16, period_ms: u16, magnitude: i16, offset: i16, phase: u16, env: Envelope) -> FfEffect {
        let e = env.words();
        FfEffect::new(FF_PERIODIC, &[wave, period_ms, magnitude as u16, offset as u16, phase, e[0], e[1], e[2], e[3]])
    }
    pub fn constant(level: i16, env: Envelope) -> FfEffect {
        let e = env.words();
        FfEffect::new(FF_CONSTANT, &[level as u16, e[0], e[1], e[2], e[3]])
    }
    pub fn ramp(start: i16, end: i16, env: Envelope) -> FfEffect {
        let e = env.words();
        FfEffect::new(FF_RAMP, &[start as u16, end as u16, e[0], e[1], e[2], e[3]])
    }
    /// The same effect with replay_length / replay_delay (ms).
    pub fn replay(mut self, length_ms: u16, delay_ms: u16) -> FfEffect {
        self.replay_length = length_ms;
        self.replay_delay = delay_ms;
        self
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Shape {
    None,
    Rumble { strong: u16, weak: u16 },
    Periodic { wave: u16, period: u16, magnitude: i16, offset: i16, phase: u16, env: Envelope },
    Constant { level: i16, env: Envelope },
    Ramp { start: i16, end: i16, env: Envelope },
    Condition,
}

/// The effect's parameters, or None for an unknown type or waveform.
fn decode(e: &FfEffect) -> Option<Shape> {
    let u = &e.u;
    let s = |i: usize| u[i] as i16;
    match e.kind {
        FF_RUMBLE => Some(Shape::Rumble { strong: u[0], weak: u[1] }),
        FF_PERIODIC => match u[0] {
            FF_SQUARE | FF_TRIANGLE | FF_SINE | FF_SAW_UP | FF_SAW_DOWN => Some(Shape::Periodic {
                wave: u[0],
                period: u[1],
                magnitude: s(2),
                offset: s(3),
                phase: u[4],
                env: Envelope::from_words(&u[5..9]),
            }),
            _ => None,
        },
        FF_CONSTANT => Some(Shape::Constant { level: s(0), env: Envelope::from_words(&u[1..5]) }),
        FF_RAMP => Some(Shape::Ramp { start: s(0), end: s(1), env: Envelope::from_words(&u[2..6]) }),
        FF_SPRING | FF_FRICTION | FF_DAMPER | FF_INERTIA => Some(Shape::Condition),
        _ => None,
    }
}

/// The EV_FF capability bitmap (EVIOCGBIT layout: bit n of byte n / 8 = code n) for a handle in
/// mode On (`all`: every effect type) or Off (rumble only). Gain in both; autocenter in neither (it
/// is accepted and ignored).
pub fn bits(all: bool) -> [u8; BITS_LEN] {
    let mut b = [0u8; BITS_LEN];
    let mut set = |c: u16| b[(c / 8) as usize] |= 1u8 << (c % 8);
    set(FF_RUMBLE);
    set(FF_GAIN);
    if all {
        for c in [
            FF_PERIODIC, FF_SQUARE, FF_TRIANGLE, FF_SINE, FF_SAW_UP, FF_SAW_DOWN, FF_CONSTANT, FF_RAMP, FF_SPRING,
            FF_FRICTION, FF_DAMPER, FF_INERTIA,
        ] {
            set(c);
        }
    }
    b
}

/// HERMES_DINPUT_FF: "1" on, "0" off (CTL_FF_*); unset or anything else leaves it to the app.
pub fn parse_env_mode(v: Option<&[u8]>) -> u32 {
    match v.map(guest::trim) {
        Some(b"1") => CTL_FF_ON,
        Some(b"0") => CTL_FF_OFF,
        _ => CTL_FF_UNSET,
    }
}

/// On (true: every effect type) or Off (rumble only) for a handle: the environment's choice wins,
/// then the control block's ff_mode (None: the file has no control block); unset or an unknown
/// value means on.
pub fn mode_on(env: u32, ctl: Option<u32>) -> bool {
    match env {
        CTL_FF_ON => true,
        CTL_FF_OFF => false,
        _ => ctl != Some(CTL_FF_OFF),
    }
}

/// HERMES_XINPUT_RUMBLE_PORT: a port 1..65535, else DEFAULT_PORT.
pub fn parse_port(v: Option<&[u8]>) -> u16 {
    let v = match v {
        Some(v) => guest::trim(v),
        None => return DEFAULT_PORT,
    };
    if v.is_empty() || v.len() > 5 || !v.iter().all(|c| c.is_ascii_digit()) {
        return DEFAULT_PORT;
    }
    match v.iter().fold(0u32, |n, c| n * 10 + (c - b'0') as u32) {
        p @ 1..=65535 => p as u16,
        _ => DEFAULT_PORT,
    }
}

// ---------------------------------------------------------------------------------------------
// evaluation

/// One cycle of `wave` at phase `x` (0..0x10000 = one cycle), -LEVEL_MAX..=LEVEL_MAX. Square
/// +1 then -1; triangle and sine 0, +1, 0, -1 at the quarters; saw up -1 -> 1; saw down 1 -> -1.
pub fn waveform(wave: u16, x: u32) -> i32 {
    let x = x & 0xFFFF;
    let l = LEVEL_MAX as i64;
    let (q, r) = (x >> 14, x & 0x3FFF);
    match wave {
        FF_SQUARE => {
            if x < 0x8000 {
                LEVEL_MAX
            } else {
                -LEVEL_MAX
            }
        }
        FF_TRIANGLE => {
            let up = (r as i64 * l / 0x4000) as i32;
            let down = ((0x4000 - r as i64) * l / 0x4000) as i32;
            match q {
                0 => up,
                1 => down,
                2 => -up,
                _ => -down,
            }
        }
        FF_SINE => match q {
            0 => quarter_sine(r),
            1 => quarter_sine(0x4000 - r),
            2 => -quarter_sine(r),
            _ => -quarter_sine(0x4000 - r),
        },
        FF_SAW_UP => (x as i64 * 2 * l / 0x10000 - l) as i32,
        FF_SAW_DOWN => (l - x as i64 * 2 * l / 0x10000) as i32,
        _ => 0,
    }
}

/// sin(pi/2 * r / 0x4000) * LEVEL_MAX for r in 0..=0x4000, in integers (the guest has no libm): an
/// odd 7th-order polynomial in Q30 (Taylor to the 5th power, the 7th term chosen so that the peak
/// is exact); off by less than one unit everywhere.
fn quarter_sine(r: u32) -> i32 {
    const C1: i64 = 1_686_629_713; // pi/2
    const C3: i64 = -693_598_668; // -(pi/2)^3 / 3!
    const C5: i64 = 85_569_306; // (pi/2)^5 / 5!
    const C7: i64 = -4_858_527; // 1 - (C1 + C3 + C5)
    let u = (r.min(0x4000) as i64) << 16; // r / 0x4000 in Q30
    let u2 = (u * u) >> 30;
    let mut p = C7;
    p = C5 + ((p * u2) >> 30);
    p = C3 + ((p * u2) >> 30);
    p = C1 + ((p * u2) >> 30);
    let s = (p * u) >> 30;
    ((s * LEVEL_MAX as i64 + (1 << 29)) >> 30).clamp(0, LEVEL_MAX as i64) as i32
}

/// |level| (0..=LEVEL_MAX) shaped by `env` at `t` ns into a run of `length` ns (0 = until
/// stopped: no fade). Attack: from attack_level to the level over the first attack_length ms. Fade
/// (finite runs): over the last fade_length ms, from the (attack-shaped) level to fade_level.
fn enveloped(env: &Envelope, level: i64, t: u64, length: u64) -> i64 {
    let mut a = level;
    let att = env.attack_length as u64 * MS;
    if t < att {
        let from = env.attack_level as i64;
        a = from + (level - from) * t as i64 / att as i64;
    }
    let fade = (env.fade_length as u64 * MS).min(length);
    if length != 0 && fade != 0 && t + fade >= length {
        let into = (t + fade - length) as i64;
        a += (env.fade_level as i64 - a) * into / fade as i64;
    }
    a.clamp(0, LEVEL_MAX as i64)
}

/// A motor share (0..=FULL) of a level's magnitude.
fn share(level_abs: i64) -> u32 {
    (level_abs.clamp(0, LEVEL_MAX as i64) as u64 * FULL as u64 / LEVEL_MAX as u64) as u32
}

/// What one effect adds at `t` ns into a run.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
struct Share {
    /// The signed value (-LEVEL_MAX..=LEVEL_MAX; 0 for rumble and conditions).
    v: i32,
    strong: u32,
    weak: u32,
    /// The strong share is subject to the anti-buzz guard.
    guarded: bool,
}

/// A constant-like level: the envelope shapes |l| and the sign follows l; the strong motor, guarded.
fn steady(l: i64, env: &Envelope, t: u64, length: u64) -> Share {
    let l = l.clamp(-(LEVEL_MAX as i64), LEVEL_MAX as i64);
    let a = enveloped(env, l.abs(), t, length);
    Share { v: if l < 0 { -a as i32 } else { a as i32 }, strong: share(a), weak: 0, guarded: true }
}

fn eval(shape: &Shape, t: u64, length: u64) -> Share {
    let lmax = LEVEL_MAX as i64;
    match *shape {
        Shape::Rumble { strong, weak } => Share { v: 0, strong: strong as u32, weak: weak as u32, guarded: false },
        Shape::Constant { level, env } => steady(level as i64, &env, t, length),
        Shape::Ramp { start, end, env } => {
            let (a, b) = (start as i64, end as i64);
            let l = if length == 0 { a } else { a + (b - a) * t.min(length) as i64 / length as i64 };
            steady(l, &env, t, length)
        }
        Shape::Periodic { wave, period, magnitude, offset, phase, env } => {
            if period == 0 {
                return steady(offset as i64 + magnitude as i64, &env, t, length);
            }
            let amp = enveloped(&env, (magnitude as i64).abs().min(lmax), t, length);
            let signed = if magnitude < 0 { -amp } else { amp };
            let p = period as u64 * MS;
            let x = (phase as u64 + (t % p) * 0x10000 / p) as u32;
            let v = (offset as i64 + signed * waveform(wave, x) as i64 / lmax).clamp(-lmax, lmax);
            if period >= SLOW_PERIOD_MS {
                Share { v: v as i32, strong: share(v.abs()), weak: 0, guarded: false }
            } else {
                Share { v: v as i32, strong: 0, weak: share((offset as i64).abs() + amp * 7 / 10), guarded: false }
            }
        }
        Shape::Condition | Shape::None => Share::default(),
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Pos {
    /// In a replay delay: nothing until the run starts at this time.
    Wait(u64),
    /// This many ns into a run.
    Run(u64),
    /// Every run is over.
    Done,
}

#[derive(Clone, Copy, Debug)]
struct Guard {
    /// `anchor` / `since` hold a value (reset by upload, play, and a replay delay).
    armed: bool,
    anchor: u32,
    since: u64,
    /// The fade of this steady stretch was counted.
    faded: bool,
}

impl Guard {
    const RESET: Guard = Guard { armed: false, anchor: 0, since: 0, faded: false };

    /// The share that reaches the motor: `s` until it has been steady (within GUARD_BAND of the
    /// value it started at) for GUARD_HOLD_NS, then fading to 0 over GUARD_FADE_NS, then 0 until it
    /// moves out of the band. `fades` counts the fades that begin.
    fn pass(&mut self, s: u32, now: u64, fades: &mut u32) -> u32 {
        if !self.armed || s.abs_diff(self.anchor) > GUARD_BAND {
            *self = Guard { armed: true, anchor: s, since: now, faded: false };
            return s;
        }
        let held = now.saturating_sub(self.since);
        if held < GUARD_HOLD_NS || s == 0 {
            return s;
        }
        if !self.faded {
            self.faded = true;
            *fades += 1;
        }
        let into = held - GUARD_HOLD_NS;
        if into >= GUARD_FADE_NS {
            0
        } else {
            (s as u64 * (GUARD_FADE_NS - into) / GUARD_FADE_NS) as u32
        }
    }
}

#[derive(Clone, Copy, Debug)]
struct Fx {
    used: bool,
    /// The uploading handle.
    owner: u32,
    kind: u16,
    shape: Shape,
    /// ns; 0 = until stopped.
    length: u64,
    delay: u64,
    playing: bool,
    /// When play() last (re)started it, and how many runs it was asked for.
    start: u64,
    count: u32,
    guard: Guard,
}

impl Fx {
    const EMPTY: Fx = Fx {
        used: false,
        owner: 0,
        kind: 0,
        shape: Shape::None,
        length: 0,
        delay: 0,
        playing: false,
        start: 0,
        count: 0,
        guard: Guard::RESET,
    };

    /// Where a playing effect is at `now`: run k starts delay after k x (delay + length).
    fn position(&self, now: u64) -> Pos {
        let el = now.saturating_sub(self.start);
        if self.length == 0 {
            return if el < self.delay { Pos::Wait(self.start + self.delay) } else { Pos::Run(el - self.delay) };
        }
        let cycle = self.delay + self.length;
        let k = el / cycle;
        if k >= self.count as u64 {
            return Pos::Done;
        }
        let within = el - k * cycle;
        if within < self.delay {
            Pos::Wait(self.start + k * cycle + self.delay)
        } else {
            Pos::Run(within - self.delay)
        }
    }
}

/// What the app was last told for a slot.
#[derive(Clone, Copy, Debug)]
struct Out {
    /// The motors as last sent (0, 0 also before anything was sent: the app's motors are off).
    strong: u16,
    weak: u16,
    /// When a datagram, and a non-zero one, was last handed out.
    at: Option<u64>,
    nonzero_at: Option<u64>,
    /// When one last went out successfully (the app's one-shot runs DURATION_MS from it).
    ok_at: Option<u64>,
    /// No datagram before this (after a failed send).
    hold_until: u64,
}

impl Out {
    const IDLE: Out = Out { strong: 0, weak: 0, at: None, nonzero_at: None, ok_at: None, hold_until: 0 };

    fn is_zero(&self) -> bool {
        self.strong == 0 && self.weak == 0
    }
}

/// A motor change worth a datagram: SEND_STEP or more, or to / from zero.
fn moved(a: u16, b: u16) -> bool {
    a.abs_diff(b) as u32 >= SEND_STEP || (a == 0) != (b == 0)
}

#[derive(Clone, Copy, Debug)]
struct SlotFx {
    fx: [Fx; MAX_EFFECTS],
    gain: u16,
    out: Out,
    /// `out` before the datagram the last tick handed out (restored if sending it failed).
    undo: Out,
    /// The last mix, and when the slot needs its next evaluation (None: nothing plays).
    motors: (u16, u16),
    next: Option<u64>,
}

impl SlotFx {
    const EMPTY: SlotFx =
        SlotFx { fx: [Fx::EMPTY; MAX_EFFECTS], gain: GAIN_DEFAULT, out: Out::IDLE, undo: Out::IDLE, motors: (0, 0), next: None };

    /// Evaluate every playing effect at `now`: the two motor levels (gain applied) and when the
    /// slot needs its next evaluation. Ends effects whose runs are over.
    fn mix(&mut self, now: u64, fades: &mut u32) -> (u16, u16, Option<u64>) {
        let (mut strong, mut weak, mut next) = (0u32, 0u32, None);
        for f in self.fx.iter_mut() {
            if !f.used || !f.playing {
                continue;
            }
            let t = match f.position(now) {
                Pos::Done => {
                    f.playing = false;
                    continue;
                }
                Pos::Wait(at) => {
                    // The motor is off between runs: a steady stretch starts again with the run.
                    f.guard = Guard::RESET;
                    next = earliest(next, Some(at));
                    continue;
                }
                Pos::Run(t) => t,
            };
            next = earliest(next, Some(now + TICK_NS));
            let sh = eval(&f.shape, t, f.length);
            strong += if sh.guarded { f.guard.pass(sh.strong, now, fades) } else { sh.strong };
            weak += sh.weak;
        }
        let g = self.gain as u64;
        let motor = |v: u32| (v.min(FULL) as u64 * g / FULL as u64) as u16;
        (motor(strong), motor(weak), next)
    }

    /// The output policy for the motors `(strong, weak)` at `now`. A datagram it hands out is
    /// committed as sent (`undo` keeps the state before it, for a failed send).
    fn decide(&mut self, slot: u8, strong: u16, weak: u16, now: u64) -> Option<Datagram> {
        let o = self.out;
        if now < o.hold_until {
            return None;
        }
        let zero = strong == 0 && weak == 0;
        let send = if zero {
            !o.is_zero() // the stop: at once, once
        } else {
            let gap_ok = o.nonzero_at.map_or(true, |t| now.saturating_sub(t) >= SEND_GAP_NS);
            let refresh = o.at.map_or(true, |t| now.saturating_sub(t) >= REFRESH_NS);
            gap_ok && (moved(strong, o.strong) || moved(weak, o.weak) || refresh)
        };
        if !send {
            return None;
        }
        self.undo = o;
        self.out = Out { strong, weak, at: Some(now), nonzero_at: if zero { o.nonzero_at } else { Some(now) }, ..o };
        Some(Datagram { slot, strong, weak })
    }
}

fn earliest(a: Option<u64>, b: Option<u64>) -> Option<u64> {
    match (a, b) {
        (Some(x), Some(y)) => Some(x.min(y)),
        (x, None) => x,
        (None, y) => y,
    }
}

/// One HXR1 rumble datagram.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Datagram {
    pub slot: u8,
    /// The low-frequency motor.
    pub strong: u16,
    /// The high-frequency motor.
    pub weak: u16,
}

impl Datagram {
    /// The 12 bytes on the wire: code 14, "HXR1", slot, strong, weak, duration (ms); u16 LE.
    pub fn bytes(&self) -> [u8; HXR1_SIZE] {
        let mut p = [0u8; HXR1_SIZE];
        p[0] = HXR1_CODE;
        p[1..5].copy_from_slice(b"HXR1");
        p[5] = self.slot;
        p[6..8].copy_from_slice(&self.strong.to_le_bytes());
        p[8..10].copy_from_slice(&self.weak.to_le_bytes());
        p[10..12].copy_from_slice(&DURATION_MS.to_le_bytes());
        p
    }
}

/// What one tick produced.
#[derive(Clone, Copy, Debug, Default)]
pub struct Tick {
    out: [Datagram; MAX_SLOTS],
    n: usize,
    /// Anti-buzz guard fades that began, per slot.
    pub fades: [u32; MAX_SLOTS],
    /// When the engine wants its next tick (None: idle until an API call).
    pub wake: Option<u64>,
}

impl Tick {
    pub fn datagrams(&self) -> &[Datagram] {
        &self.out[..self.n]
    }
}

/// Every slot's effects, mixer, guard and output state. No clock of its own: every call that needs
/// the time is given it.
pub struct Engine {
    slots: [SlotFx; MAX_SLOTS],
    /// The last wake_at asked to be woken within a tick: changes are picked up without a kick.
    ticking: bool,
}

impl Default for Engine {
    fn default() -> Self {
        Engine::new()
    }
}

impl Engine {
    pub const fn new() -> Engine {
        Engine { slots: [SlotFx::EMPTY; MAX_SLOTS], ticking: false }
    }

    fn effect(&mut self, slot: usize, id: i32) -> Result<&mut Fx, i32> {
        let s = self.slots.get_mut(slot).ok_or(sys::EINVAL)?;
        let i = usize::try_from(id).map_err(|_| sys::EINVAL)?;
        match s.fx.get_mut(i) {
            Some(f) if f.used => Ok(f),
            _ => Err(sys::EINVAL),
        }
    }

    /// Upload `e` for `owner` on `slot`. id -1: a new effect in the lowest free id (written back
    /// into `e.id`), ENOSPC when all are in use; id >= 0: update that effect in place (EINVAL if it
    /// does not exist or the type changes; a playing effect keeps playing with the new parameters).
    /// EINVAL for an unknown type or waveform, and (`all` false, mode Off) for any type but rumble.
    pub fn upload(&mut self, slot: usize, owner: u32, e: &mut FfEffect, all: bool) -> Result<(), i32> {
        let shape = decode(e).ok_or(sys::EINVAL)?;
        if !all && e.kind != FF_RUMBLE {
            return Err(sys::EINVAL);
        }
        let (length, delay) = (e.replay_length as u64 * MS, e.replay_delay as u64 * MS);
        if e.id == -1 {
            let s = self.slots.get_mut(slot).ok_or(sys::EINVAL)?;
            let id = s.fx.iter().position(|f| !f.used).ok_or(sys::ENOSPC)?;
            s.fx[id] = Fx { used: true, owner, kind: e.kind, shape, length, delay, ..Fx::EMPTY };
            e.id = id as i16;
            return Ok(());
        }
        let f = self.effect(slot, e.id as i32)?;
        if f.kind != e.kind {
            return Err(sys::EINVAL);
        }
        f.shape = shape;
        f.length = length;
        f.delay = delay;
        f.guard = Guard::RESET;
        Ok(())
    }

    /// Stop and erase; EINVAL for an unknown id.
    pub fn erase(&mut self, slot: usize, id: i32) -> Result<(), i32> {
        *self.effect(slot, id)? = Fx::EMPTY;
        Ok(())
    }

    /// count > 0: (re)start, `count` runs; 0: stop. EINVAL for an unknown id or a negative count.
    pub fn play(&mut self, slot: usize, id: i32, count: i32, now: u64) -> Result<(), i32> {
        let f = self.effect(slot, id)?;
        if count < 0 {
            return Err(sys::EINVAL);
        }
        if count == 0 {
            f.playing = false;
        } else {
            f.playing = true;
            f.start = now;
            f.count = count as u32;
            f.guard = Guard::RESET;
        }
        Ok(())
    }

    pub fn set_gain(&mut self, slot: usize, gain: u16) -> Result<(), i32> {
        self.slots.get_mut(slot).ok_or(sys::EINVAL)?.gain = gain;
        Ok(())
    }

    /// Stop and erase every effect `owner` uploaded on `slot`; returns how many.
    pub fn detach(&mut self, slot: usize, owner: u32) -> u32 {
        let mut n = 0;
        if let Some(s) = self.slots.get_mut(slot) {
            for f in s.fx.iter_mut().filter(|f| f.used && f.owner == owner) {
                *f = Fx::EMPTY;
                n += 1;
            }
        }
        n
    }

    /// Evaluate every slot at `now` and hand out the datagrams the output policy wants sent; report
    /// each one's fate with `sent`.
    pub fn tick(&mut self, now: u64) -> Tick {
        let mut t = Tick::default();
        for (i, s) in self.slots.iter_mut().enumerate() {
            let (strong, weak, next) = s.mix(now, &mut t.fades[i]);
            s.motors = (strong, weak);
            s.next = next;
            if let Some(d) = s.decide(i as u8, strong, weak, now) {
                t.out[t.n] = d;
                t.n += 1;
            }
        }
        t.wake = self.wake_at(now);
        t
    }

    /// The fate of a datagram the last tick handed out for `slot`. A failed one is tried again once
    /// the gap allows; a failed stop is given up once the app's last one-shot has run out anyway.
    pub fn sent(&mut self, slot: usize, ok: bool, now: u64) {
        let s = match self.slots.get_mut(slot) {
            Some(s) => s,
            None => return,
        };
        if ok {
            s.out.ok_at = Some(now);
            return;
        }
        let tried = s.out;
        s.out = Out { hold_until: now + SEND_GAP_NS, ..s.undo };
        let expired = s.out.ok_at.map_or(true, |t| now.saturating_sub(t) >= DURATION_MS as u64 * MS);
        if tried.is_zero() && expired {
            s.out = Out { hold_until: 0, ..tried };
        }
    }

    /// When the engine needs its next tick: within TICK_NS while anything runs, at the next run of
    /// an effect in its replay delay, or when a stop that failed may be retried; None = idle.
    pub fn wake_at(&mut self, now: u64) -> Option<u64> {
        let mut w = None;
        for s in self.slots.iter() {
            w = earliest(w, s.next);
            if s.next.is_none() && !s.out.is_zero() {
                w = earliest(w, Some(s.out.hold_until.max(now)));
            }
        }
        self.ticking = matches!(w, Some(t) if t <= now + TICK_NS);
        w
    }

    /// An API call changed something the thread must see now: it is not ticking anyway, and there is
    /// something to evaluate or a stop to send.
    pub fn wants_kick(&self) -> bool {
        !self.ticking && self.slots.iter().any(|s| !s.out.is_zero() || s.fx.iter().any(|f| f.used && f.playing))
    }

    /// The motors of `slot` as the last tick mixed them.
    pub fn motors(&self, slot: usize) -> (u16, u16) {
        self.slots.get(slot).map_or((0, 0), |s| s.motors)
    }

    /// The effect's signed value at `now` (0 in a replay delay), None unless it exists and plays.
    pub fn value(&self, slot: usize, id: usize, now: u64) -> Option<i32> {
        let f = self.slots.get(slot)?.fx.get(id).filter(|f| f.used && f.playing)?;
        match f.position(now) {
            Pos::Run(t) => Some(eval(&f.shape, t, f.length).v),
            Pos::Wait(_) => Some(0),
            Pos::Done => None,
        }
    }

    pub fn is_playing(&self, slot: usize, id: usize) -> bool {
        self.slots.get(slot).and_then(|s| s.fx.get(id)).is_some_and(|f| f.used && f.playing)
    }

    /// Effects stored on `slot`.
    pub fn effects(&self, slot: usize) -> usize {
        self.slots.get(slot).map_or(0, |s| s.fx.iter().filter(|f| f.used).count())
    }
}

// ---------------------------------------------------------------------------------------------
// runtime: one engine per process, ticked by one thread

struct Rt {
    lock: AtomicU32,
    engine: UnsafeCell<Engine>,
    /// 0 = no thread, 1 = running, 2 = start failed (retried after `retry_at`).
    thread: AtomicU32,
    retry_at: AtomicU64,
    /// Bumped and woken by an API call that needs the thread now (see `Engine::wants_kick`).
    kick: AtomicU32,
    /// The thread's UDP socket (-1 until the first datagram).
    sock: AtomicI32,
    port: AtomicU32,
    failures: AtomicU64,
    fail_logged: AtomicU64,
}
unsafe impl Sync for Rt {}

static RT: Rt = Rt {
    lock: AtomicU32::new(0),
    engine: UnsafeCell::new(Engine::new()),
    thread: AtomicU32::new(0),
    retry_at: AtomicU64::new(0),
    kick: AtomicU32::new(0),
    sock: AtomicI32::new(-1),
    port: AtomicU32::new(DEFAULT_PORT as u32),
    failures: AtomicU64::new(0),
    fail_logged: AtomicU64::new(0),
};

/// Per-slot counters not yet added to a slot file (the handles flush them into its control block:
/// they hold the mapping, the engine thread does not). Indices P_*.
pub const P_UPLOADS: usize = 0;
pub const P_PLAYS: usize = 1;
pub const P_DATAGRAMS: usize = 2;
pub const P_FADES: usize = 3;
const PZ: AtomicU32 = AtomicU32::new(0);
const PENDING_SLOT: [AtomicU32; 4] = [PZ; 4];
pub static PENDING: [[AtomicU32; 4]; MAX_SLOTS] = [PENDING_SLOT; MAX_SLOTS];

fn count(slot: usize, which: usize, n: u32) {
    if let Some(p) = PENDING.get(slot) {
        p[which].fetch_add(n, Relaxed);
    }
}

/// The engine under its lock (held only for in-memory work: never across I/O, a sleep or the
/// logger).
fn locked<R>(f: impl FnOnce(&mut Engine) -> R) -> R {
    let mut n = 0u32;
    while RT.lock.compare_exchange_weak(0, 1, Acquire, Relaxed).is_err() {
        n += 1;
        if n < 64 {
            core::hint::spin_loop();
        } else {
            sys::sched_yield();
        }
    }
    let r = f(unsafe { &mut *RT.engine.get() });
    RT.lock.store(0, Release);
    r
}

/// An API call: starts the thread on first use, runs `f` under the lock, and wakes the thread if
/// it is not ticking anyway and has something to do now.
fn api<R>(f: impl FnOnce(&mut Engine) -> R) -> R {
    ensure_thread();
    let (r, kick) = locked(|en| {
        let r = f(en);
        (r, en.wants_kick())
    });
    if kick {
        RT.kick.fetch_add(1, Release);
        sys::futex_wake(&RT.kick, 1, false);
    }
    r
}

fn ensure_thread() {
    let st = RT.thread.load(Acquire);
    if st == 1 {
        return;
    }
    if st == 2 && sys::mono_ns() < RT.retry_at.load(Relaxed) {
        return;
    }
    if RT.thread.compare_exchange(st, 1, AcqRel, Relaxed).is_err() {
        return;
    }
    if !sys::spawn_detached(engine_main, ptr::null_mut(), THREAD_STACK) {
        RT.retry_at.store(sys::mono_ns().wrapping_add(1_000_000_000), Relaxed);
        RT.thread.store(2, Release);
    }
}

pub fn set_port(port: u16) {
    RT.port.store(port as u32, Relaxed);
}

pub fn upload(slot: usize, owner: u32, e: &mut FfEffect, all: bool) -> Result<(), i32> {
    let r = api(|en| en.upload(slot, owner, e, all));
    if r.is_ok() {
        count(slot, P_UPLOADS, 1);
        STATS.ff_uploads.fetch_add(1, Relaxed);
    }
    r
}

pub fn erase(slot: usize, id: i32) -> Result<(), i32> {
    api(|en| en.erase(slot, id))
}

pub fn play(slot: usize, id: i32, n: i32) -> Result<(), i32> {
    let now = sys::mono_ns();
    let r = api(|en| en.play(slot, id, n, now));
    if r.is_ok() && n > 0 {
        count(slot, P_PLAYS, 1);
        STATS.ff_plays.fetch_add(1, Relaxed);
    }
    r
}

pub fn set_gain(slot: usize, gain: u16) -> Result<(), i32> {
    api(|en| en.set_gain(slot, gain))
}

/// A handle went away: stop and erase what it uploaded.
pub fn detach(slot: usize, owner: u32) {
    api(|en| en.detach(slot, owner));
}

fn send(d: &Datagram) -> Result<(), i32> {
    let mut fd = RT.sock.load(Relaxed);
    if fd < 0 {
        fd = sys::udp_socket()?;
        RT.sock.store(fd, Relaxed);
    }
    sys::send_loopback(fd, &d.bytes(), RT.port.load(Relaxed) as u16).map(|_| ())
}

/// Count a failed send; log the first, then at most one line per FAIL_LOG_NS.
fn note_failure(d: &Datagram, err: i32, now: u64) {
    let n = RT.failures.fetch_add(1, Relaxed) + 1;
    STATS.ff_send_failures.fetch_add(1, Relaxed);
    let last = RT.fail_logged.load(Relaxed);
    if n != 1 && now.saturating_sub(last) < FAIL_LOG_NS {
        return;
    }
    RT.fail_logged.store(now, Relaxed);
    let mut m = Msg::new();
    m.s(b"hermes: force feedback: datagram to 127.0.0.1:")
        .u(RT.port.load(Relaxed) as u64)
        .s(b" for slot ")
        .u(d.slot as u64)
        .s(b" failed, errno ")
        .u(err as u64)
        .s(b" (")
        .u(n)
        .s(b" failure(s) so far)");
    guest::log(1, &mut m);
}

extern "C" fn engine_main(_arg: *mut c_void) -> *mut c_void {
    sys::set_thread_name(b"hermes-ff\0");
    loop {
        // Read before ticking: a kick that lands after the tick makes the wait return at once.
        let k = RT.kick.load(Acquire);
        let now = sys::mono_ns();
        let t = locked(|en| en.tick(now));
        for (slot, f) in t.fades.iter().enumerate() {
            if *f != 0 {
                count(slot, P_FADES, *f);
                STATS.ff_guard_fades.fetch_add(*f as u64, Relaxed);
            }
        }
        let mut wake = t.wake;
        if !t.datagrams().is_empty() {
            let mut ok = [false; MAX_SLOTS];
            for (i, d) in t.datagrams().iter().enumerate() {
                match send(d) {
                    Ok(()) => {
                        ok[i] = true;
                        count(d.slot as usize, P_DATAGRAMS, 1);
                        STATS.ff_datagrams.fetch_add(1, Relaxed);
                    }
                    Err(e) => note_failure(d, e, now),
                }
            }
            wake = locked(|en| {
                for (i, d) in t.datagrams().iter().enumerate() {
                    en.sent(d.slot as usize, ok[i], now);
                }
                en.wake_at(now)
            });
        }
        // Idle: no timeout, the next API call that matters kicks.
        let timeout = wake.map(|w| w.saturating_sub(sys::mono_ns()).max(100_000));
        let _ = sys::futex_wait(&RT.kick, k, timeout, false);
    }
}

/// fork(): the child starts clean (no effects, no thread, its own socket on first use).
///
/// # Safety
/// Only from the pthread_atfork child handler (the child is single-threaded there).
pub unsafe fn atfork_child() {
    RT.lock.store(0, Relaxed);
    *RT.engine.get() = Engine::new();
    RT.thread.store(0, Relaxed);
    RT.retry_at.store(0, Relaxed);
    // The parent's socket (a shared open file description): the child's thread opens its own.
    sys::close(RT.sock.swap(-1, Relaxed));
    for p in PENDING.iter() {
        for c in p.iter() {
            c.store(0, Relaxed);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// An arbitrary clock origin (1000 s), so "never" and "at 0" cannot be confused.
    const T0: u64 = 1_000_000 * MS;

    fn up(en: &mut Engine, owner: u32, e: FfEffect) -> Result<i16, i32> {
        let mut e = e;
        en.upload(0, owner, &mut e, true).map(|_| e.id)
    }

    fn sent(t: &Tick) -> Vec<(u16, u16)> {
        t.datagrams().iter().map(|d| (d.strong, d.weak)).collect()
    }

    /// Tick at `now` and report every datagram as delivered.
    fn tick_ok(en: &mut Engine, now: u64) -> Tick {
        let t = en.tick(now);
        for d in t.datagrams() {
            en.sent(d.slot as usize, true, now);
        }
        t
    }

    fn strong_at(en: &mut Engine, now: u64) -> u16 {
        tick_ok(en, now);
        en.motors(0).0
    }

    fn near(a: i64, b: i64, tol: i64) -> bool {
        (a - b).abs() <= tol
    }

    #[test]
    fn hxr1_encoding() {
        let d = Datagram { slot: 3, strong: 0x1234, weak: 0xABCD };
        assert_eq!(d.bytes(), [14, b'B', b'X', b'R', b'1', 3, 0x34, 0x12, 0xCD, 0xAB, 0xDC, 0x05]);
        assert_eq!(DURATION_MS, 1500);
    }

    #[test]
    fn waveforms() {
        let l = LEVEL_MAX;
        assert_eq!((waveform(FF_SQUARE, 0), waveform(FF_SQUARE, 0x7FFF), waveform(FF_SQUARE, 0x8000)), (l, l, -l));
        assert_eq!(waveform(FF_SQUARE, 0xFFFF), -l);
        let tri: Vec<i32> = [0u32, 0x2000, 0x4000, 0x8000, 0xC000, 0xE000].iter().map(|x| waveform(FF_TRIANGLE, *x)).collect();
        assert_eq!(tri, vec![0, l / 2, l, 0, -l, -(l / 2)]);
        assert_eq!(waveform(FF_SAW_UP, 0), -l);
        assert_eq!(waveform(FF_SAW_UP, 0x8000), 0);
        assert!(waveform(FF_SAW_UP, 0xFFFF) >= l - 2);
        assert_eq!(waveform(FF_SAW_DOWN, 0), l);
        assert_eq!(waveform(FF_SAW_DOWN, 0x8000), 0);
        assert!(waveform(FF_SAW_DOWN, 0xFFFF) <= -l + 2);
        // a whole cycle wraps
        assert_eq!(waveform(FF_SAW_UP, 0x10000 + 0x8000), 0);
        for k in 0..=256u32 {
            let x = k * 256;
            let want = (2.0 * std::f64::consts::PI * x as f64 / 65536.0).sin() * l as f64;
            let got = waveform(FF_SINE, x) as f64;
            assert!((got - want).abs() <= 1.5, "sine at {:#x}: {} vs {}", x, got, want);
        }
        assert_eq!((waveform(FF_SINE, 0x4000), waveform(FF_SINE, 0xC000)), (l, -l), "exact peaks");
    }

    #[test]
    fn periodic_effects_follow_their_wave_phase_sign_and_offset() {
        let mut en = Engine::new();
        let sine = up(&mut en, 1, FfEffect::periodic(FF_SINE, 1000, 0x7FFF, 0, 0, Envelope::NONE)).unwrap();
        let neg = up(&mut en, 1, FfEffect::periodic(FF_SINE, 1000, -0x4000, 0, 0, Envelope::NONE)).unwrap();
        let quarter = up(&mut en, 1, FfEffect::periodic(FF_SINE, 1000, 0x7FFF, 0, 0x4000, Envelope::NONE)).unwrap();
        let off = up(&mut en, 1, FfEffect::periodic(FF_SQUARE, 200, 0x1000, 0x2000, 0, Envelope::NONE)).unwrap();
        let saw = up(&mut en, 1, FfEffect::periodic(FF_SAW_DOWN, 400, 0x7FFF, 0, 0, Envelope::NONE)).unwrap();
        for id in [sine, neg, quarter, off, saw] {
            en.play(0, id as i32, 1, T0).unwrap();
        }
        let v = |en: &Engine, id: i16, ms: u64| en.value(0, id as usize, T0 + ms * MS).unwrap() as i64;
        let l = LEVEL_MAX as i64;
        assert!(near(v(&en, sine, 0), 0, 1) && near(v(&en, sine, 250), l, 1) && near(v(&en, sine, 750), -l, 1));
        assert!(near(v(&en, sine, 1250), l, 1), "the wave repeats");
        assert!(near(v(&en, neg, 250), -0x4000, 1), "a negative magnitude flips the wave");
        assert!(near(v(&en, quarter, 0), l, 1), "phase 0x4000 = a quarter cycle ahead");
        assert_eq!((v(&en, off, 50), v(&en, off, 150)), (0x3000, 0x1000), "offset +- magnitude");
        assert!(near(v(&en, saw, 0), l, 1) && near(v(&en, saw, 200), 0, 1) && near(v(&en, saw, 399), -l, 400));
        // period 0: a constant of offset + magnitude
        let flat = up(&mut en, 1, FfEffect::periodic(FF_TRIANGLE, 0, 0x1000, 0x0800, 0, Envelope::NONE)).unwrap();
        en.play(0, flat as i32, 1, T0).unwrap();
        assert_eq!(v(&en, flat, 123), 0x1800);
    }

    #[test]
    fn the_80_ms_rule_puts_fast_waves_on_the_weak_motor() {
        let mut en = Engine::new();
        // slow (80 ms): sampled on the strong motor
        let slow = up(&mut en, 1, FfEffect::periodic(FF_SQUARE, 80, 0x7FFF, 0, 0, Envelope::NONE)).unwrap();
        en.play(0, slow as i32, 1, T0).unwrap();
        tick_ok(&mut en, T0 + 10 * MS);
        assert_eq!(en.motors(0), (0xFFFF, 0));
        en.erase(0, slow as i32).unwrap();
        // fast (79 ms): a steady buzz of |offset| + 0.7 |magnitude| on the weak motor
        let fast = up(&mut en, 1, FfEffect::periodic(FF_SINE, 79, -0x4000, 0x1000, 0, Envelope::NONE)).unwrap();
        en.play(0, fast as i32, 1, T0).unwrap();
        let want = ((0x1000u64 + 0x4000 * 7 / 10) * 0xFFFF / 0x7FFF) as u16;
        for ms in [0u64, 13, 37, 61] {
            tick_ok(&mut en, T0 + ms * MS);
            assert_eq!(en.motors(0), (0, want), "at {} ms", ms);
        }
    }

    #[test]
    fn mixer_mapping_gain_and_clamping() {
        let mut en = Engine::new();
        let r = up(&mut en, 1, FfEffect::rumble(0xC000, 0x3000)).unwrap();
        en.play(0, r as i32, 1, T0).unwrap();
        assert_eq!({ tick_ok(&mut en, T0); en.motors(0) }, (0xC000, 0x3000), "rumble: both motors as given");
        let c = up(&mut en, 1, FfEffect::constant(-0x4000, Envelope::NONE)).unwrap();
        en.play(0, c as i32, 1, T0).unwrap();
        assert_eq!({ tick_ok(&mut en, T0); en.motors(0) }, (0xFFFF, 0x3000), "constant on strong, the sum clamped");
        en.play(0, r as i32, 0, T0).unwrap();
        let half = (0x4000u64 * 0xFFFF / 0x7FFF) as u16;
        assert_eq!({ tick_ok(&mut en, T0); en.motors(0) }, (half, 0), "a negative level drives the motor by its size");
        assert_eq!(en.value(0, c as usize, T0), Some(-0x4000), "the sign follows the level");
        // conditions are stored and playable, and contribute nothing
        for kind in [FF_SPRING, FF_FRICTION, FF_DAMPER, FF_INERTIA] {
            let id = up(&mut en, 1, FfEffect::new(kind, &[1, 2, 3])).unwrap();
            en.play(0, id as i32, 1, T0).unwrap();
            assert!(en.is_playing(0, id as usize));
        }
        assert_eq!({ tick_ok(&mut en, T0); en.motors(0) }, (half, 0));
        // gain scales both motors
        en.set_gain(0, 0x8000).unwrap();
        en.play(0, r as i32, 1, T0).unwrap();
        tick_ok(&mut en, T0);
        assert_eq!(en.motors(0), (0x8000, (0x3000u64 * 0x8000 / 0xFFFF) as u16));
        en.set_gain(0, 0).unwrap();
        assert_eq!({ tick_ok(&mut en, T0); en.motors(0) }, (0, 0));
    }

    #[test]
    fn envelope_attack_and_fade() {
        let mut en = Engine::new();
        let env = Envelope { attack_length: 100, attack_level: 0, fade_length: 200, fade_level: 0x2000 };
        let c = up(&mut en, 1, FfEffect::constant(0x7FFF, env).replay(1000, 0)).unwrap();
        en.play(0, c as i32, 1, T0).unwrap();
        let v = |en: &Engine, ms: u64| en.value(0, c as usize, T0 + ms * MS).unwrap() as i64;
        let l = LEVEL_MAX as i64;
        assert_eq!(v(&en, 0), 0, "attack starts at attack_level");
        assert!(near(v(&en, 50), l / 2, 2), "halfway up: {}", v(&en, 50));
        assert_eq!(v(&en, 100), l);
        assert_eq!(v(&en, 799), l, "full until the fade");
        assert!(near(v(&en, 900), (l + 0x2000) / 2, 2), "halfway down to fade_level: {}", v(&en, 900));
        assert!(near(v(&en, 999), 0x2000, 150), "ends at fade_level: {}", v(&en, 999));
        // a negative level keeps its sign through the envelope; an endless run has no fade
        let n = up(&mut en, 1, FfEffect::constant(-0x6000, env)).unwrap();
        en.play(0, n as i32, 1, T0).unwrap();
        assert!(near(en.value(0, n as usize, T0 + 50 * MS).unwrap() as i64, -0x3000, 2));
        assert_eq!(en.value(0, n as usize, T0 + 999_000 * MS), Some(-0x6000));
        // attack from above: the level rises from a louder attack_level down to its own
        let loud = Envelope { attack_length: 100, attack_level: 0x7FFF, fade_length: 0, fade_level: 0 };
        let p = up(&mut en, 1, FfEffect::periodic(FF_SQUARE, 1000, 0x1000, 0, 0, loud)).unwrap();
        en.play(0, p as i32, 1, T0).unwrap();
        assert_eq!(en.value(0, p as usize, T0), Some(0x7FFF), "the envelope scales the periodic's magnitude");
        assert_eq!(en.value(0, p as usize, T0 + 100 * MS), Some(0x1000));
    }

    #[test]
    fn ramp_goes_from_start_to_end_over_its_length() {
        let mut en = Engine::new();
        let r = up(&mut en, 1, FfEffect::ramp(-0x4000, 0x4000, Envelope::NONE).replay(1000, 0)).unwrap();
        en.play(0, r as i32, 1, T0).unwrap();
        let v = |en: &Engine, ms: u64| en.value(0, r as usize, T0 + ms * MS).unwrap() as i64;
        assert_eq!((v(&en, 0), v(&en, 250), v(&en, 500), v(&en, 750)), (-0x4000, -0x2000, 0, 0x2000));
        assert!(near(v(&en, 999), 0x4000, 40));
        tick_ok(&mut en, T0 + 250 * MS);
        assert_eq!(en.motors(0).0, (0x2000u64 * 0xFFFF / 0x7FFF) as u16, "|v| on the strong motor");
        // length 0: start_level for as long as it plays
        let s = up(&mut en, 1, FfEffect::ramp(0x1234, 0x7000, Envelope::NONE)).unwrap();
        en.play(0, s as i32, 1, T0).unwrap();
        assert_eq!(en.value(0, s as usize, T0 + 5000 * MS), Some(0x1234));
    }

    #[test]
    fn delay_length_count_and_endless_runs() {
        let mut en = Engine::new();
        let r = up(&mut en, 1, FfEffect::rumble(0x8000, 0).replay(200, 100)).unwrap();
        en.play(0, r as i32, 2, T0).unwrap();
        let s = |en: &mut Engine, ms: u64| strong_at(en, T0 + ms * MS);
        assert_eq!(s(&mut en, 50), 0, "waits replay_delay first");
        assert_eq!(s(&mut en, 150), 0x8000);
        assert_eq!(s(&mut en, 299), 0x8000);
        assert_eq!(s(&mut en, 310), 0, "the delay again before the second run");
        assert_eq!(s(&mut en, 450), 0x8000);
        assert!(en.is_playing(0, r as usize));
        assert_eq!(s(&mut en, 600), 0, "two runs, then it ends");
        assert!(!en.is_playing(0, r as usize), "an effect that ends stops");
        assert_eq!(en.effects(0), 1, "and stays uploaded");
        // played again: (re)starts from now
        en.play(0, r as i32, 1, T0 + 1000 * MS).unwrap();
        assert_eq!(s(&mut en, 1050), 0);
        assert_eq!(s(&mut en, 1100), 0x8000);
        // length 0: until stopped, whatever the count
        let e = up(&mut en, 1, FfEffect::rumble(0, 0x4000).replay(0, 10)).unwrap();
        en.play(0, e as i32, 1, T0).unwrap();
        tick_ok(&mut en, T0 + 3_600_000 * MS);
        assert_eq!(en.motors(0), (0, 0x4000));
        en.play(0, e as i32, 0, T0 + 3_600_000 * MS).unwrap();
        assert!(!en.is_playing(0, e as usize));
        assert_eq!(en.play(0, e as i32, -1, T0), Err(sys::EINVAL));
    }

    #[test]
    fn the_guard_fades_a_steady_constant_and_lets_go_when_it_moves() {
        let mut en = Engine::new();
        let full = (0x4000u64 * 0xFFFF / 0x7FFF) as u32;
        let c = up(&mut en, 1, FfEffect::constant(0x4000, Envelope::NONE)).unwrap();
        en.play(0, c as i32, 1, T0).unwrap();
        let mut fades = 0;
        let mut at = |en: &mut Engine, ms: u64| {
            let t = tick_ok(en, T0 + ms * MS);
            fades += t.fades[0];
            en.motors(0).0 as u32
        };
        for ms in (0..2000).step_by(10) {
            assert_eq!(at(&mut en, ms), full, "untouched for 2 s ({} ms)", ms);
        }
        assert!(near(at(&mut en, 2250) as i64, full as i64 / 2, 2), "halfway through the 500 ms fade");
        assert_eq!(at(&mut en, 2500), 0);
        assert_eq!(at(&mut en, 9000), 0, "held at 0");
        // played again: back at once, and fades again 2 s later
        en.play(0, c as i32, 1, T0 + 9000 * MS).unwrap();
        assert_eq!(at(&mut en, 9000), full);
        assert_eq!(at(&mut en, 11600), 0);
        // uploaded again (same level): back at once
        let mut e = FfEffect::constant(0x4000, Envelope::NONE);
        e.id = c;
        en.upload(0, 1, &mut e, true).unwrap();
        assert_eq!(at(&mut en, 11610), full);
        drop(at);
        assert_eq!(fades, 2, "two fades began");

        // a move within the band (a ramp creeping 0.008 in 60 s) does not wake it...
        let mut en = Engine::new();
        let r = up(&mut en, 1, FfEffect::ramp(0x4000, 0x4100, Envelope::NONE).replay(60_000, 0)).unwrap();
        en.play(0, r as i32, 1, T0).unwrap();
        for ms in (0..=2600).step_by(10) {
            tick_ok(&mut en, T0 + ms * MS);
        }
        assert_eq!(en.motors(0).0, 0, "a creeping ramp is steady");
        // ...a real move does: a fade envelope at the end of a 5 s constant
        let mut en = Engine::new();
        let env = Envelope { attack_length: 0, attack_level: 0, fade_length: 2000, fade_level: 0 };
        let c = up(&mut en, 1, FfEffect::constant(0x7FFF, env).replay(5000, 0)).unwrap();
        en.play(0, c as i32, 1, T0).unwrap();
        let mut seen = Vec::new();
        for ms in (0..4000).step_by(10) {
            tick_ok(&mut en, T0 + ms * MS);
            seen.push((ms, en.motors(0).0));
        }
        assert!(seen.iter().any(|(ms, s)| (2500..3000).contains(ms) && *s == 0), "faded while steady");
        let back = seen.iter().find(|(ms, s)| *ms >= 3000 && *s > 0).expect("never came back");
        assert!(back.0 <= 3100 && back.1 > 0xE000, "moved out of the band: back at {:?}", back);
        // rumble and periodic effects are never guarded
        let mut en = Engine::new();
        let r = up(&mut en, 1, FfEffect::rumble(0x8000, 0x8000)).unwrap();
        let p = up(&mut en, 1, FfEffect::periodic(FF_SQUARE, 30, 0, 0x2000, 0, Envelope::NONE)).unwrap();
        en.play(0, r as i32, 1, T0).unwrap();
        en.play(0, p as i32, 1, T0).unwrap();
        for ms in (0..=6000).step_by(10) {
            tick_ok(&mut en, T0 + ms * MS);
        }
        assert_eq!(en.motors(0), (0x8000, 0x8000 + (0x2000u64 * 0xFFFF / 0x7FFF) as u16));
    }

    #[test]
    fn output_policy() {
        let mut en = Engine::new();
        let r = up(&mut en, 1, FfEffect::rumble(0x8000, 0)).unwrap();
        let set = |en: &mut Engine, strong: u16, weak: u16| {
            let mut e = FfEffect::rumble(strong, weak);
            e.id = r;
            en.upload(0, 1, &mut e, true).unwrap();
        };
        let at = |en: &mut Engine, ms: u64| sent(&tick_ok(en, T0 + ms * MS));
        assert!(at(&mut en, 0).is_empty(), "nothing plays, nothing to stop: no datagram");
        en.play(0, r as i32, 1, T0).unwrap();
        assert_eq!(at(&mut en, 0), vec![(0x8000, 0)]);
        assert!(at(&mut en, 10).is_empty(), "unchanged");
        set(&mut en, 0x8000 + 2047, 0);
        assert!(at(&mut en, 50).is_empty(), "below 1/32 of full scale");
        set(&mut en, 0x8000 + 3000, 0);
        assert_eq!(at(&mut en, 60), vec![(0x8000 + 3000, 0)], "1/32 or more");
        set(&mut en, 0x8000 + 6000, 0);
        assert!(at(&mut en, 70).is_empty() && at(&mut en, 99).is_empty(), "within 40 ms of the last non-zero one");
        assert_eq!(at(&mut en, 100), vec![(0x8000 + 6000, 0)], "the pending change, once the gap allows");
        set(&mut en, 0x8000 + 6000, 1);
        assert!(at(&mut en, 110).is_empty(), "a motor leaving zero is a change, but within the gap");
        assert_eq!(at(&mut en, 140), vec![(0x8000 + 6000, 1)]);
        for ms in (150..1140).step_by(10) {
            assert!(at(&mut en, ms).is_empty(), "no refresh before 1000 ms ({} ms)", ms);
        }
        assert_eq!(at(&mut en, 1140), vec![(0x8000 + 6000, 1)], "the 1000 ms refresh");
        en.play(0, r as i32, 0, T0 + 1145 * MS).unwrap();
        assert_eq!(at(&mut en, 1145), vec![(0, 0)], "the stop goes at once, inside the gap");
        assert!(at(&mut en, 1155).is_empty() && at(&mut en, 5000).is_empty(), "once");
        assert_eq!(en.wake_at(T0 + 5000 * MS), None, "idle: no wake-ups");
        // at most 25 non-zero datagrams a second, however fast the game changes things
        en.play(0, r as i32, 1, T0 + 6000 * MS).unwrap();
        let mut n = 0;
        for ms in 6000..7000u64 {
            set(&mut en, if ms % 2 == 0 { 0xFFFF } else { 0x1000 }, 0);
            n += at(&mut en, ms).len();
        }
        assert_eq!(n, 25);
    }

    #[test]
    fn failed_sends_are_retried_after_the_gap_and_a_stop_given_up_once_moot() {
        let mut en = Engine::new();
        let r = up(&mut en, 1, FfEffect::rumble(0x8000, 0)).unwrap();
        en.play(0, r as i32, 1, T0).unwrap();
        let t = en.tick(T0);
        assert_eq!(sent(&t), vec![(0x8000, 0)]);
        en.sent(0, false, T0);
        assert!(sent(&en.tick(T0 + 10 * MS)).is_empty(), "held for the gap");
        let t = en.tick(T0 + 40 * MS);
        assert_eq!(sent(&t), vec![(0x8000, 0)], "retried");
        en.sent(0, true, T0 + 40 * MS);
        // a failed stop is retried (and woken for) while the app may still be running a one-shot
        en.play(0, r as i32, 0, T0 + 100 * MS).unwrap();
        assert_eq!(sent(&en.tick(T0 + 100 * MS)), vec![(0, 0)]);
        en.sent(0, false, T0 + 100 * MS);
        assert_eq!(en.wake_at(T0 + 100 * MS), Some(T0 + 140 * MS), "woken to retry the stop");
        assert_eq!(sent(&en.tick(T0 + 140 * MS)), vec![(0, 0)]);
        en.sent(0, false, T0 + 140 * MS);
        // ... and given up once the last one-shot (1500 ms from the last delivered datagram) is over
        assert_eq!(sent(&en.tick(T0 + 1600 * MS)), vec![(0, 0)]);
        en.sent(0, false, T0 + 1600 * MS);
        assert_eq!(en.wake_at(T0 + 1600 * MS), None);
        assert!(sent(&en.tick(T0 + 2000 * MS)).is_empty());
    }

    #[test]
    fn wake_ups_only_while_needed() {
        let mut en = Engine::new();
        assert!(!en.wants_kick(), "nothing to do");
        let r = up(&mut en, 1, FfEffect::rumble(0x8000, 0).replay(100, 500)).unwrap();
        assert!(!en.wants_kick(), "uploaded, not playing");
        en.play(0, r as i32, 1, T0).unwrap();
        assert!(en.wants_kick(), "a play while the thread sleeps");
        let t = tick_ok(&mut en, T0);
        assert_eq!(t.wake, Some(T0 + 500 * MS), "in the replay delay: sleeps until the run starts");
        assert!(en.wants_kick(), "not ticking: a change must wake it");
        let t = tick_ok(&mut en, T0 + 500 * MS);
        assert_eq!(t.wake, Some(T0 + 510 * MS), "running: every tick");
        assert!(!en.wants_kick(), "ticking anyway: no kick needed");
        let t = tick_ok(&mut en, T0 + 600 * MS);
        assert_eq!(sent(&t), vec![(0, 0)]);
        assert_eq!(t.wake, None, "over: idle");
    }

    #[test]
    fn ids_errors_and_detach() {
        let mut en = Engine::new();
        for i in 0..16 {
            assert_eq!(up(&mut en, 1 + (i % 2), FfEffect::rumble(1, 1)), Ok(i as i16), "lowest free id");
        }
        assert_eq!(up(&mut en, 1, FfEffect::rumble(1, 1)), Err(sys::ENOSPC));
        en.erase(0, 3).unwrap();
        en.erase(0, 9).unwrap();
        assert_eq!(up(&mut en, 1, FfEffect::rumble(1, 1)), Ok(3), "a freed id is reused, lowest first");
        // update in place: the type must not change, the id must exist
        let mut e = FfEffect::constant(5, Envelope::NONE);
        e.id = 0;
        assert_eq!(en.upload(0, 1, &mut e, true), Err(sys::EINVAL), "type change");
        let mut e = FfEffect::rumble(7, 7);
        e.id = 9;
        assert_eq!(en.upload(0, 1, &mut e, true), Err(sys::EINVAL), "erased id");
        e.id = 16;
        assert_eq!(en.upload(0, 1, &mut e, true), Err(sys::EINVAL), "id out of range");
        e.id = -2;
        assert_eq!(en.upload(0, 1, &mut e, true), Err(sys::EINVAL), "negative id");
        // a playing effect keeps playing with the new parameters
        en.play(0, 0, 1, T0).unwrap();
        let mut e = FfEffect::rumble(0x9000, 0x100);
        e.id = 0;
        en.upload(0, 2, &mut e, true).unwrap();
        assert!(en.is_playing(0, 0));
        tick_ok(&mut en, T0 + 10 * MS);
        assert_eq!(en.motors(0), (0x9000, 0x100));
        // unknown types and waveforms, unknown ids
        assert_eq!(up(&mut en, 1, FfEffect::new(0x5d, &[])), Err(sys::EINVAL), "FF_CUSTOM is not offered");
        assert_eq!(up(&mut en, 1, FfEffect::new(0x40, &[])), Err(sys::EINVAL));
        assert_eq!(up(&mut en, 1, FfEffect::periodic(0x5d, 100, 1, 0, 0, Envelope::NONE)), Err(sys::EINVAL));
        assert_eq!(en.erase(0, 9), Err(sys::EINVAL));
        assert_eq!(en.erase(0, -1), Err(sys::EINVAL));
        assert_eq!(en.play(0, 99, 1, T0), Err(sys::EINVAL));
        assert_eq!(en.play(7, 0, 1, T0), Err(sys::EINVAL), "bad slot");
        // detach stops and erases the owner's effects only (owner 1: the even ids, and 3)
        en.play(0, 3, 1, T0).unwrap();
        assert_eq!(en.detach(0, 1), 9);
        assert_eq!(en.effects(0), 6);
        assert!(!en.is_playing(0, 3) && en.value(0, 3, T0).is_none());
        assert!(!en.is_playing(0, 0), "owner 2 updated effect 0, but owner 1 uploaded it: gone with owner 1");
        assert!(en.detach(0, 2) == 6 && en.effects(0) == 0);
        assert_eq!(up(&mut en, 3, FfEffect::rumble(1, 1)), Ok(0), "its ids are free again");
    }

    #[test]
    fn mode_off_offers_rumble_only() {
        let mut en = Engine::new();
        let mut e = FfEffect::rumble(0x8000, 0x8000);
        assert_eq!(en.upload(1, 9, &mut e, false), Ok(()));
        for mut e in [
            FfEffect::periodic(FF_SINE, 100, 1, 0, 0, Envelope::NONE),
            FfEffect::constant(1, Envelope::NONE),
            FfEffect::ramp(1, 2, Envelope::NONE),
            FfEffect::new(FF_SPRING, &[]),
        ] {
            assert_eq!(en.upload(1, 9, &mut e, false), Err(sys::EINVAL), "{:#x} in mode Off", e.kind);
            assert_eq!(e.id, -1, "no id written on failure");
        }
        let has = |b: &[u8; BITS_LEN], c: u16| b[(c / 8) as usize] & (1u8 << (c % 8)) != 0;
        let on = bits(true);
        let all = [
            FF_RUMBLE, FF_PERIODIC, FF_SQUARE, FF_TRIANGLE, FF_SINE, FF_SAW_UP, FF_SAW_DOWN, FF_CONSTANT, FF_RAMP, FF_SPRING,
            FF_FRICTION, FF_DAMPER, FF_INERTIA, FF_GAIN,
        ];
        for c in 0..128u16 {
            assert_eq!(has(&on, c), all.contains(&c), "On, code {:#x}", c);
            assert_eq!(has(&bits(false), c), c == FF_RUMBLE || c == FF_GAIN, "Off, code {:#x}", c);
        }
        assert_eq!(on[10], 0b1111_1111, "0x50..0x57");
        assert_eq!(on[11], 0b0001_1111, "0x58..0x5c");
        assert_eq!(on[12], 0b0000_0001, "gain, no autocenter");
    }

    #[test]
    fn mode_and_port_parsing() {
        assert_eq!(parse_env_mode(None), CTL_FF_UNSET);
        assert_eq!(parse_env_mode(Some(b"1")), CTL_FF_ON);
        assert_eq!(parse_env_mode(Some(b" 0 ")), CTL_FF_OFF);
        assert_eq!(parse_env_mode(Some(b"yes")), CTL_FF_UNSET);
        assert_eq!(parse_env_mode(Some(b"")), CTL_FF_UNSET);
        // the environment wins; then the control block; unset / unknown / no block = on
        assert!(mode_on(CTL_FF_ON, Some(CTL_FF_OFF)));
        assert!(!mode_on(CTL_FF_OFF, Some(CTL_FF_ON)));
        assert!(!mode_on(CTL_FF_OFF, None));
        assert!(!mode_on(CTL_FF_UNSET, Some(CTL_FF_OFF)));
        assert!(mode_on(CTL_FF_UNSET, Some(CTL_FF_ON)));
        assert!(mode_on(CTL_FF_UNSET, Some(CTL_FF_UNSET)));
        assert!(mode_on(CTL_FF_UNSET, Some(9)));
        assert!(mode_on(CTL_FF_UNSET, None));
        assert_eq!(parse_port(None), 7947);
        assert_eq!(parse_port(Some(b" 9000 ")), 9000);
        assert_eq!(parse_port(Some(b"0")), 7947);
        assert_eq!(parse_port(Some(b"65536")), 7947);
        assert_eq!(parse_port(Some(b"65535")), 65535);
        assert_eq!(parse_port(Some(b"x1")), 7947);
    }

    #[test]
    fn c_struct_words() {
        let e = FfEffect::periodic(FF_SAW_UP, 250, -2, 3, 0x8000, Envelope { attack_length: 4, attack_level: 5, fade_length: 6, fade_level: 0xFFFF })
            .replay(700, 80);
        assert_eq!((e.kind, e.id, e.replay_length, e.replay_delay), (FF_PERIODIC, -1, 700, 80));
        assert_eq!(&e.u[..9], &[FF_SAW_UP, 250, 0xFFFE, 3, 0x8000, 4, 5, 6, 0xFFFF]);
        assert_eq!(
            decode(&e),
            Some(Shape::Periodic {
                wave: FF_SAW_UP,
                period: 250,
                magnitude: -2,
                offset: 3,
                phase: 0x8000,
                env: Envelope { attack_length: 4, attack_level: 5, fade_length: 6, fade_level: 0x7FFF },
            }),
            "envelope levels are clamped to 0x7FFF"
        );
    }
}
