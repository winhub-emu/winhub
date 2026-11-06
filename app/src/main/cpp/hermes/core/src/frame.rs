//! Per-reader cursor: turns published snapshots into evdev frames.
//!
//! The cursor remembers what this reader last delivered (button edge counters, the journal
//! position and the axis values). Each call to [`Cursor::next_frame`] produces at most one
//! SYN-terminated frame:
//!
//! 1. **Full state** when the reader is new, re-attached, or the snapshot is inconsistent with the
//!    cursor: EV_KEY for all 11 buttons, EV_ABS for all 8 axes, SYN_REPORT. The cursor is rebased
//!    on that snapshot, so nothing older than it is ever replayed.
//! 2. **Journal replay** when button edges happened and the journal still holds them: one frame per
//!    writer publish, in publish order (MSC_SCAN + EV_KEY per edge). A press and release between
//!    two reads therefore arrive as two frames, and edges of different buttons keep their order.
//! 3. **Counter replay** when the reader fell further behind than the journal: each button's
//!    missed edges are replayed from its edge counter, one edge per button per frame, so per-button
//!    order and the final state are exact. A button that fell more than [`REPLAY_CAP_EDGES`] edges
//!    behind keeps the newest edges (parity preserved) and the dropped pairs are counted.
//! 4. **Axis changes** otherwise. Axes are coalesced: the latest value is delivered, intermediate
//!    positions may be skipped. Axis changes ride on the last frame of a replay.
//!
//! **Pacing.** With [`Cursor::next_frame_at`] a frame that carries only axis changes is emitted
//! at most once per interval T: if one is due less than T after the last frame this cursor
//! emitted, it is held ([`Due::At`] names when it becomes due) and later delivered with the
//! latest values. Full-state and button frames are never held; they carry the current axes and
//! restart the interval. (A guest HID stack that queues every report, Wine's among them, is fed
//! at most 1/T stick-only reports per second instead of one per pad report.)

use crate::abi::*;
use crate::layout::{Snapshot, JOURNAL_LEN, J_BUTTON_MASK, J_GROUP, J_PRESS};

/// One evdev event without its timestamp.
#[derive(Clone, Copy, Default, Debug, PartialEq, Eq)]
#[repr(C)]
pub struct Ev {
    pub kind: u16,
    pub code: u16,
    pub value: i32,
}

/// Largest frame: 11 x (MSC_SCAN + KEY) + 8 ABS + SYN = 31.
pub const FRAME_CAP: usize = 32;

/// The journal span: a reader within this many edges of the writer replays from the journal.
pub const JOURNAL_SPAN: u32 = JOURNAL_LEN as u32;

/// Per-button replay bound for a reader that fell behind the journal (64 taps).
pub const REPLAY_CAP_EDGES: u32 = 128;

/// Differences at or above this are treated as corruption (full-state resync).
const SANE: u32 = 1 << 30;

pub struct Frame {
    pub ev: [Ev; FRAME_CAP],
    pub len: usize,
}

impl Frame {
    pub const fn new() -> Frame {
        Frame { ev: [Ev { kind: 0, code: 0, value: 0 }; FRAME_CAP], len: 0 }
    }

    #[inline]
    fn push(&mut self, kind: u16, code: u16, value: i32) {
        if self.len < FRAME_CAP {
            self.ev[self.len] = Ev { kind, code, value };
            self.len += 1;
        }
    }

    pub fn events(&self) -> &[Ev] {
        &self.ev[..self.len]
    }
}

impl Default for Frame {
    fn default() -> Self {
        Frame::new()
    }
}

#[derive(Clone, Copy, Default, Debug, PartialEq, Eq)]
pub struct FrameStats {
    pub frames: u64,
    pub events: u64,
    /// Press edges delivered.
    pub taps: u64,
    pub full_frames: u64,
    pub journal_frames: u64,
    pub counter_frames: u64,
    /// Taps (press + release pairs) dropped because one button fell more than
    /// `REPLAY_CAP_EDGES` behind.
    pub coalesced_taps: u64,
    /// Full-state frames forced by an inconsistent snapshot.
    pub resyncs: u64,
    /// Axis-only frames that were held back by pacing before being delivered.
    pub paced_frames: u64,
    /// Publishes whose state reached the reader merged into a later frame instead of a frame of
    /// their own (stick coalescing, with or without pacing).
    pub merged_axis_publishes: u64,
}

impl FrameStats {
    pub fn add(&mut self, o: &FrameStats) {
        self.frames += o.frames;
        self.events += o.events;
        self.taps += o.taps;
        self.full_frames += o.full_frames;
        self.journal_frames += o.journal_frames;
        self.counter_frames += o.counter_frames;
        self.coalesced_taps += o.coalesced_taps;
        self.resyncs += o.resyncs;
        self.paced_frames += o.paced_frames;
        self.merged_axis_publishes += o.merged_axis_publishes;
    }
}

/// When a cursor's next frame is due.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Due {
    /// Nothing new to deliver.
    Nothing,
    /// A frame is due now.
    Now,
    /// Only axis changes are pending and pacing holds them until this time (ns, the caller's
    /// clock).
    At(u64),
    /// Only axis changes are pending and they wait for the game's next frame, or this time (the
    /// stall bound), whichever comes first. Produced by the guest's frame-credit pacing, never by
    /// the cursor itself.
    Frame(u64),
}

/// Source of journal entries (the mapped file, or nothing).
pub trait Journal {
    fn get(&self, idx: u32) -> Option<u32>;
}

pub struct NoJournal;

impl Journal for NoJournal {
    fn get(&self, _idx: u32) -> Option<u32> {
        None
    }
}

#[derive(Clone, Copy)]
struct Plan {
    rem: [u32; BUTTONS],
    counts: [u32; BUTTONS],
    edges: u32,
}

#[derive(Clone, Copy)]
pub struct Cursor {
    pub need_full: bool,
    pub edges: u32,
    pub counts: [u32; BUTTONS],
    pub buttons: u16,
    pub axes: [i32; AXES],
    plan: Option<Plan>,
    /// When this cursor last emitted a frame (the caller's clock; pacing only).
    last_emit: u64,
    /// pub_seq of the snapshot the cursor last caught up with, and frames emitted since.
    last_pub: u32,
    frames_since: u32,
    /// An axis-only frame was held by pacing since the axes were last current.
    hold_seen: bool,
}

impl Default for Cursor {
    fn default() -> Self {
        Cursor::new()
    }
}

impl Cursor {
    /// A new cursor owes its reader a full-state frame.
    pub const fn new() -> Cursor {
        Cursor {
            need_full: true,
            edges: 0,
            counts: [0; BUTTONS],
            buttons: 0,
            axes: [0; AXES],
            plan: None,
            last_emit: 0,
            last_pub: 0,
            frames_since: 0,
            hold_seen: false,
        }
    }

    /// The next frame will be a full-state frame of whatever the source then shows.
    pub fn force_full(&mut self) {
        self.need_full = true;
        self.plan = None;
    }

    pub fn replaying(&self) -> bool {
        self.plan.is_some()
    }

    /// A snapshot equal to what this cursor has delivered (used when a fresh snapshot could not
    /// be taken: it produces no frames).
    pub fn as_snapshot(&self) -> Snapshot {
        Snapshot {
            pub_seq: 0,
            flags: 0,
            unplug: 0,
            edges: self.edges,
            counts: self.counts,
            axes: self.axes,
            time_ns: 0,
        }
    }

    /// Would `next_frame(s, ..)` produce a frame? Consumes nothing.
    pub fn pending(&self, s: &Snapshot) -> bool {
        if self.need_full || self.plan.is_some() {
            return true;
        }
        if s.edges != self.edges || s.counts != self.counts {
            return true;
        }
        (0..AXES).any(|i| clamp_axis(i, s.axes[i]) != self.axes[i])
    }

    fn axes_differ(&self, s: &Snapshot) -> bool {
        (0..AXES).any(|i| clamp_axis(i, s.axes[i]) != self.axes[i])
    }

    /// Only axis changes separate this cursor from `s`.
    fn axis_only(&self, s: &Snapshot) -> bool {
        !(self.need_full || self.plan.is_some() || s.edges != self.edges || s.counts != self.counts)
    }

    /// Only axis changes separate this cursor from `s` (the next frame would be axis-only).
    pub fn only_axes_pending(&self, s: &Snapshot) -> bool {
        self.axis_only(s) && self.axes_differ(s)
    }

    /// When is the next frame for `s` due, at time `now`, with axis-only frames paced to one per
    /// `interval` (0 = unpaced)? Consumes nothing.
    pub fn due(&mut self, s: &Snapshot, now: u64, interval: u64) -> Due {
        if !self.axis_only(s) {
            return Due::Now;
        }
        if !self.axes_differ(s) {
            return Due::Nothing;
        }
        if interval == 0 || now.wrapping_sub(self.last_emit) >= interval {
            return Due::Now;
        }
        self.hold_seen = true;
        Due::At(self.last_emit.wrapping_add(interval))
    }

    /// Unpaced: build the next frame into `f`. Returns false (and leaves `f` empty) if there is
    /// nothing new to deliver.
    pub fn next_frame<J: Journal>(
        &mut self,
        s: &Snapshot,
        j: &J,
        f: &mut Frame,
        st: &mut FrameStats,
    ) -> bool {
        self.next_frame_at(s, j, f, st, 0, 0) == Due::Now
    }

    /// Paced: build the next frame into `f` if one is due at `now` (returns `Due::Now`);
    /// otherwise leave `f` empty and return why not (`Nothing`, or `At(t)` for a held axis-only
    /// frame).
    pub fn next_frame_at<J: Journal>(
        &mut self,
        s: &Snapshot,
        j: &J,
        f: &mut Frame,
        st: &mut FrameStats,
        now: u64,
        interval: u64,
    ) -> Due {
        f.len = 0;
        match self.due(s, now, interval) {
            Due::Now => {}
            other => return other,
        }
        let axis_only = self.axis_only(s);
        if !self.build(s, j, f, st) {
            f.len = 0;
            return Due::Nothing;
        }
        st.frames += 1;
        st.events += f.len as u64;
        self.last_emit = now;
        self.frames_since = self.frames_since.saturating_add(1);
        let caught_up = self.axis_only(s) && !self.axes_differ(s);
        if caught_up {
            if axis_only && self.hold_seen {
                st.paced_frames += 1;
            }
            self.hold_seen = false;
            let covered = s.pub_seq.wrapping_sub(self.last_pub);
            if covered < SANE {
                st.merged_axis_publishes += covered.saturating_sub(self.frames_since) as u64;
            }
            self.last_pub = s.pub_seq;
            self.frames_since = 0;
        }
        Due::Now
    }

    fn build<J: Journal>(&mut self, s: &Snapshot, j: &J, f: &mut Frame, st: &mut FrameStats) -> bool {
        if self.need_full {
            self.full(s, f, st);
            return true;
        }
        if self.plan.is_some() {
            self.plan_round(s, f, st);
            return true;
        }
        if s.edges == self.edges && s.counts == self.counts {
            return self.axes_only(s, f);
        }
        let de = s.edges.wrapping_sub(self.edges);
        let mut sum: u64 = 0;
        let mut sane = de < SANE;
        for b in 0..BUTTONS {
            let d = s.counts[b].wrapping_sub(self.counts[b]);
            if d >= SANE {
                sane = false;
                break;
            }
            sum += d as u64;
        }
        if !sane || sum != de as u64 {
            st.resyncs += 1;
            self.full(s, f, st);
            return true;
        }
        if de <= JOURNAL_SPAN && self.journal_group(s, j, f, st) {
            return true;
        }
        self.start_plan(s, st);
        self.plan_round(s, f, st);
        true
    }

    fn full(&mut self, s: &Snapshot, f: &mut Frame, st: &mut FrameStats) {
        let buttons = s.buttons();
        for b in 0..BUTTONS {
            f.push(EV_KEY, BUTTON_CODES[b], ((buttons >> b) & 1) as i32);
        }
        for i in 0..AXES {
            let v = clamp_axis(i, s.axes[i]);
            f.push(EV_ABS, AXIS_CODES[i], v);
            self.axes[i] = v;
        }
        f.push(EV_SYN, SYN_REPORT, 0);
        self.counts = s.counts;
        self.edges = s.edges;
        self.buttons = buttons;
        self.need_full = false;
        self.plan = None;
        // A rebase: nothing before this snapshot counts as merged.
        self.last_pub = s.pub_seq;
        self.frames_since = 0;
        st.full_frames += 1;
    }

    fn push_axes(&mut self, s: &Snapshot, f: &mut Frame) {
        for i in 0..AXES {
            let v = clamp_axis(i, s.axes[i]);
            if v != self.axes[i] {
                f.push(EV_ABS, AXIS_CODES[i], v);
                self.axes[i] = v;
            }
        }
    }

    fn axes_only(&mut self, s: &Snapshot, f: &mut Frame) -> bool {
        self.push_axes(s, f);
        if f.len == 0 {
            return false;
        }
        f.push(EV_SYN, SYN_REPORT, 0);
        true
    }

    /// Replays the next publish group from the journal. Returns false, changing nothing, if the
    /// journal no longer holds it or does not agree with the cursor.
    fn journal_group<J: Journal>(
        &mut self,
        s: &Snapshot,
        j: &J,
        f: &mut Frame,
        st: &mut FrameStats,
    ) -> bool {
        let de = s.edges.wrapping_sub(self.edges);
        let mut picked = [0u8; BUTTONS];
        let mut press = [false; BUTTONS];
        let mut n = 0usize;
        let mut buttons = self.buttons;
        let mut touched = 0u16;
        let mut i = 1u32;
        while i <= de {
            let p = match j.get(self.edges.wrapping_add(i)) {
                Some(p) => p,
                None => return false,
            };
            let b = (p & J_BUTTON_MASK) as usize;
            let down = p & J_PRESS != 0;
            let group = p & J_GROUP != 0;
            if i == 1 && !group {
                return false;
            }
            if i > 1 && group {
                break;
            }
            if b >= BUTTONS || n >= BUTTONS || touched & (1 << b) != 0 {
                return false;
            }
            if ((buttons >> b) & 1 != 0) == down {
                return false; // not an edge from where this reader stands
            }
            if s.counts[b].wrapping_sub(self.counts[b]) == 0 {
                return false;
            }
            buttons ^= 1 << b;
            touched |= 1 << b;
            picked[n] = b as u8;
            press[n] = down;
            n += 1;
            i += 1;
        }
        if n == 0 {
            return false;
        }
        for k in 0..n {
            let b = picked[k] as usize;
            f.push(EV_MSC, MSC_SCAN, BUTTON_CODES[b] as i32);
            f.push(EV_KEY, BUTTON_CODES[b], press[k] as i32);
            self.counts[b] = self.counts[b].wrapping_add(1);
            if press[k] {
                st.taps += 1;
            }
        }
        self.buttons = buttons;
        self.edges = self.edges.wrapping_add(n as u32);
        if self.edges == s.edges {
            self.push_axes(s, f);
        }
        f.push(EV_SYN, SYN_REPORT, 0);
        st.journal_frames += 1;
        true
    }

    fn start_plan(&mut self, s: &Snapshot, st: &mut FrameStats) {
        let mut rem = [0u32; BUTTONS];
        for b in 0..BUTTONS {
            let mut d = s.counts[b].wrapping_sub(self.counts[b]);
            if d > REPLAY_CAP_EDGES {
                let keep = if (d ^ REPLAY_CAP_EDGES) & 1 == 0 {
                    REPLAY_CAP_EDGES
                } else {
                    REPLAY_CAP_EDGES - 1
                };
                st.coalesced_taps += ((d - keep) / 2) as u64;
                d = keep;
            }
            rem[b] = d;
        }
        self.plan = Some(Plan { rem, counts: s.counts, edges: s.edges });
    }

    fn plan_round(&mut self, s: &Snapshot, f: &mut Frame, st: &mut FrameStats) {
        let mut done = true;
        if let Some(p) = self.plan.as_mut() {
            for b in 0..BUTTONS {
                if p.rem[b] > 0 {
                    p.rem[b] -= 1;
                    self.buttons ^= 1 << b;
                    let v = ((self.buttons >> b) & 1) as i32;
                    f.push(EV_MSC, MSC_SCAN, BUTTON_CODES[b] as i32);
                    f.push(EV_KEY, BUTTON_CODES[b], v);
                    if v == 1 {
                        st.taps += 1;
                    }
                }
            }
            done = p.rem.iter().all(|r| *r == 0);
            if done {
                self.counts = p.counts;
                self.edges = p.edges;
            }
        }
        if done {
            self.plan = None;
            self.buttons = parity(&self.counts);
            self.push_axes(s, f);
        }
        f.push(EV_SYN, SYN_REPORT, 0);
        st.counter_frames += 1;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::layout::{JOURNAL_LEN, J_GROUP, J_PRESS};

    /// A tiny in-memory model of the writer: publishes snapshots and journal entries exactly the
    /// way `writer::SlotWriter` does.
    struct Model {
        snap: Snapshot,
        journal: Vec<u64>,
    }

    impl Journal for Model {
        fn get(&self, idx: u32) -> Option<u32> {
            let v = self.journal[(idx as usize) % JOURNAL_LEN];
            if (v >> 32) as u32 == idx {
                Some(v as u32)
            } else {
                None
            }
        }
    }

    impl Model {
        fn new() -> Model {
            Model { snap: Snapshot::NEUTRAL, journal: vec![0; JOURNAL_LEN] }
        }
        fn set(&mut self, buttons: u16, axes: [i32; AXES]) {
            let have = self.snap.buttons();
            let diff = (have ^ buttons) & ALL_BUTTONS;
            let mut first = true;
            for b in 0..BUTTONS {
                if diff & (1 << b) != 0 {
                    self.snap.edges = self.snap.edges.wrapping_add(1);
                    self.snap.counts[b] = self.snap.counts[b].wrapping_add(1);
                    let mut p = b as u32;
                    if buttons & (1 << b) != 0 {
                        p |= J_PRESS;
                    }
                    if first {
                        p |= J_GROUP;
                    }
                    first = false;
                    let idx = self.snap.edges;
                    self.journal[(idx as usize) % JOURNAL_LEN] = ((idx as u64) << 32) | p as u64;
                }
            }
            self.snap.axes = axes;
            self.snap.pub_seq += 1;
        }
        fn buttons(&mut self, b: u16) {
            let a = self.snap.axes;
            self.set(b, a);
        }
    }

    fn drain(c: &mut Cursor, m: &Model, st: &mut FrameStats) -> Vec<Vec<Ev>> {
        let mut frames = Vec::new();
        let mut f = Frame::new();
        for _ in 0..100_000 {
            if !c.next_frame(&m.snap, m, &mut f, st) {
                break;
            }
            frames.push(f.events().to_vec());
        }
        assert!(!c.pending(&m.snap), "pending after drain");
        frames
    }

    fn keys(frame: &[Ev]) -> Vec<(u16, i32)> {
        frame.iter().filter(|e| e.kind == EV_KEY).map(|e| (e.code, e.value)).collect()
    }

    fn fresh(m: &Model) -> (Cursor, FrameStats) {
        let mut c = Cursor::new();
        let mut st = FrameStats::default();
        let frames = drain(&mut c, m, &mut st);
        assert_eq!(frames.len(), 1, "a new cursor delivers exactly one full-state frame first");
        (c, st)
    }

    #[test]
    fn full_state_frame_layout_includes_mode() {
        let mut m = Model::new();
        m.set((1 << B_MODE) | (1 << B_A), [100, -100, 5, -5, 255, 7, -1, 1]);
        let mut c = Cursor::new();
        let mut st = FrameStats::default();
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 1);
        let f = &frames[0];
        assert_eq!(f.len(), 20);
        for b in 0..BUTTONS {
            assert_eq!(f[b].kind, EV_KEY);
            assert_eq!(f[b].code, BUTTON_CODES[b]);
        }
        assert_eq!(f[B_MODE].value, 1, "BTN_MODE is reported truthfully in full state");
        assert_eq!(f[B_A].value, 1);
        assert_eq!(f[B_B].value, 0);
        for i in 0..AXES {
            assert_eq!(f[BUTTONS + i].kind, EV_ABS);
            assert_eq!(f[BUTTONS + i].code, AXIS_CODES[i]);
        }
        assert_eq!(f[BUTTONS + A_LT].value, 255);
        assert_eq!(f[19], Ev { kind: EV_SYN, code: SYN_REPORT, value: 0 });
        assert_eq!(st.full_frames, 1);
    }

    #[test]
    fn unchanged_publish_emits_nothing() {
        let mut m = Model::new();
        m.set(1 << B_X, [1, 2, 3, 4, 5, 6, 0, 0]);
        let (mut c, mut st) = fresh(&m);
        m.set(1 << B_X, [1, 2, 3, 4, 5, 6, 0, 0]);
        assert!(!c.pending(&m.snap));
        assert!(drain(&mut c, &m, &mut st).is_empty());
    }

    #[test]
    fn tap_between_reads_is_two_frames_with_scan_codes() {
        let mut m = Model::new();
        let (mut c, mut st) = fresh(&m);
        m.buttons(1 << B_A);
        m.buttons(0);
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 2);
        assert_eq!(
            frames[0],
            vec![
                Ev { kind: EV_MSC, code: MSC_SCAN, value: BTN_A as i32 },
                Ev { kind: EV_KEY, code: BTN_A, value: 1 },
                Ev { kind: EV_SYN, code: SYN_REPORT, value: 0 },
            ]
        );
        assert_eq!(keys(&frames[1]), vec![(BTN_A, 0)]);
        assert_eq!(st.taps, 1);
        assert_eq!(st.journal_frames, 2);
    }

    #[test]
    fn quick_access_sequence_keeps_cross_button_order() {
        let mut m = Model::new();
        let (mut c, mut st) = fresh(&m);
        m.buttons(1 << B_MODE); // Guide down
        m.buttons((1 << B_MODE) | (1 << B_A)); // A down
        m.buttons(1 << B_MODE); // A up
        m.buttons(0); // Guide up
        let frames = drain(&mut c, &m, &mut st);
        let seq: Vec<_> = frames.iter().map(|f| keys(f)).collect();
        assert_eq!(
            seq,
            vec![vec![(BTN_MODE, 1)], vec![(BTN_A, 1)], vec![(BTN_A, 0)], vec![(BTN_MODE, 0)]]
        );
    }

    #[test]
    fn one_publish_with_several_buttons_is_one_frame_in_code_order() {
        let mut m = Model::new();
        let (mut c, mut st) = fresh(&m);
        m.set((1 << B_Y) | (1 << B_A) | (1 << B_START), [0, 0, 0, 0, 0, 0, 1, 0]);
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 1);
        assert_eq!(keys(&frames[0]), vec![(BTN_A, 1), (BTN_Y, 1), (BTN_START, 1)]);
        let f = &frames[0];
        assert_eq!(f[f.len() - 2], Ev { kind: EV_ABS, code: ABS_HAT0X, value: 1 });
    }

    #[test]
    fn axes_coalesce_to_latest_and_clamp() {
        let mut m = Model::new();
        let (mut c, mut st) = fresh(&m);
        for v in 0..1000 {
            m.set(0, [v, -v, 0, 0, 0, 0, 0, 0]);
        }
        m.set(0, [40000, -40000, 0, 0, 999, -5, 7, -7]);
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 1);
        assert_eq!(
            frames[0],
            vec![
                Ev { kind: EV_ABS, code: ABS_X, value: 32767 },
                Ev { kind: EV_ABS, code: ABS_Y, value: -32768 },
                Ev { kind: EV_ABS, code: ABS_BRAKE, value: 255 },
                Ev { kind: EV_ABS, code: ABS_HAT0X, value: 1 },
                Ev { kind: EV_ABS, code: ABS_HAT0Y, value: -1 },
                Ev { kind: EV_SYN, code: SYN_REPORT, value: 0 },
            ]
        );
    }

    #[test]
    fn lapped_reader_replays_from_counters_exactly() {
        let mut m = Model::new();
        let (mut c, mut st) = fresh(&m);
        // 200 taps of A interleaved with 50 of B: 500 edges, beyond the 256-entry journal.
        for i in 0..200 {
            let b = if i % 4 == 0 { 1 << B_B } else { 0 };
            m.buttons((1 << B_A) | b);
            m.buttons(0);
        }
        let frames = drain(&mut c, &m, &mut st);
        let mut a = Vec::new();
        let mut b = Vec::new();
        for f in &frames {
            for (code, v) in keys(f) {
                if code == BTN_A {
                    a.push(v)
                } else if code == BTN_B {
                    b.push(v)
                }
            }
            // never two edges of one button inside one frame
            let ks = keys(f);
            let mut seen = Vec::new();
            for (code, _) in ks {
                assert!(!seen.contains(&code));
                seen.push(code);
            }
        }
        // A: 400 edges, capped to the newest 128 (64 taps); B: 100 edges, all replayed.
        assert_eq!(a.len(), 128);
        assert_eq!(b.len(), 100);
        assert!(a.chunks(2).all(|p| p == [1, 0]));
        assert!(b.chunks(2).all(|p| p == [1, 0]));
        assert_eq!(st.coalesced_taps, 136);
        assert_eq!(st.taps + st.coalesced_taps, 250, "every tap is delivered or accounted for");
        assert_eq!(c.buttons, 0);
        assert_eq!(c.edges, m.snap.edges);
    }

    #[test]
    fn replay_ends_in_current_state_even_when_held() {
        let mut m = Model::new();
        let (mut c, mut st) = fresh(&m);
        for _ in 0..300 {
            m.buttons(1 << B_X);
            m.buttons(0);
        }
        m.buttons(1 << B_X); // finally held
        let frames = drain(&mut c, &m, &mut st);
        let x: Vec<i32> = frames.iter().flat_map(|f| keys(f)).map(|(_, v)| v).collect();
        assert_eq!(*x.last().unwrap(), 1, "the last edge delivered is the press that is held");
        assert_eq!(x.len() % 2, 1);
        assert_eq!(c.buttons, 1 << B_X);
    }

    #[test]
    fn inconsistent_snapshot_forces_full_state() {
        let mut m = Model::new();
        m.buttons(1 << B_A);
        let (mut c, mut st) = fresh(&m);
        // counters that do not add up to the edge total (a foreign or damaged writer)
        m.snap.counts[B_B] = m.snap.counts[B_B].wrapping_add(3);
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 1);
        assert_eq!(frames[0].len(), 20);
        assert_eq!(st.resyncs, 1);
        // a counter that went backwards
        m.snap.counts[B_A] = m.snap.counts[B_A].wrapping_sub(2);
        m.snap.edges = m.snap.edges.wrapping_sub(2);
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 1);
        assert_eq!(frames[0].len(), 20);
    }

    #[test]
    fn force_full_never_replays_older_edges() {
        let mut m = Model::new();
        let (mut c, mut st) = fresh(&m);
        m.buttons(1 << B_A);
        m.buttons(0);
        c.force_full();
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 1, "only the full state, never the older press");
        assert!(keys(&frames[0]).iter().all(|(_, v)| *v == 0));
    }

    #[test]
    fn counters_wrap() {
        let mut m = Model::new();
        m.snap.edges = u32::MAX - 3;
        m.snap.counts[B_A] = u32::MAX - 3; // even: released
        let (mut c, mut st) = fresh(&m);
        for _ in 0..4 {
            m.buttons(1 << B_A);
            m.buttons(0);
        }
        let frames = drain(&mut c, &m, &mut st);
        assert_eq!(frames.len(), 8);
        assert_eq!(st.resyncs, 0);
    }

    const T: u64 = 8_000_000;
    const MS: u64 = 1_000_000;

    fn step(c: &mut Cursor, m: &Model, st: &mut FrameStats, now: u64) -> (Due, Vec<Ev>) {
        let mut f = Frame::new();
        let d = c.next_frame_at(&m.snap, m, &mut f, st, now, T);
        (d, f.events().to_vec())
    }

    #[test]
    fn axis_only_frames_are_paced_buttons_and_full_state_are_not() {
        let t0 = 100 * MS;
        let mut m = Model::new();
        let mut c = Cursor::new();
        let mut st = FrameStats::default();
        let (d, f) = step(&mut c, &m, &mut st, t0);
        assert_eq!(d, Due::Now);
        assert_eq!(f.len(), 20, "full state is never held");
        // a stick change 1 ms after the last frame is held until t0 + T, readiness not set
        m.set(0, [100, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(c.due(&m.snap, t0 + MS, T), Due::At(t0 + T));
        assert_eq!(step(&mut c, &m, &mut st, t0 + MS).0, Due::At(t0 + T));
        m.set(0, [200, 0, 0, 0, 0, 0, 0, 0]);
        m.set(0, [300, 0, 0, 0, 0, 0, 0, 0]);
        // at the deadline the latest value goes out, the intermediate ones merged
        let (d, f) = step(&mut c, &m, &mut st, t0 + T);
        assert_eq!(d, Due::Now);
        assert_eq!(f, vec![Ev { kind: EV_ABS, code: ABS_X, value: 300 }, Ev { kind: EV_SYN, code: SYN_REPORT, value: 0 }]);
        assert_eq!(st.paced_frames, 1);
        assert_eq!(st.merged_axis_publishes, 2, "three publishes, one frame");
        assert_eq!(step(&mut c, &m, &mut st, t0 + T).0, Due::Nothing);
        // a stick change, then a button 1 ms later: the button frame goes out at once, carries
        // the current stick value and restarts the interval
        m.set(0, [400, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(c.due(&m.snap, t0 + T + MS, T), Due::At(t0 + 2 * T));
        m.set(1 << B_A, [500, 0, 0, 0, 0, 0, 0, 0]);
        let tb = t0 + T + 2 * MS;
        let (d, f) = step(&mut c, &m, &mut st, tb);
        assert_eq!(d, Due::Now);
        assert_eq!(keys(&f), vec![(BTN_A, 1)]);
        assert!(f.contains(&Ev { kind: EV_ABS, code: ABS_X, value: 500 }));
        assert_eq!(st.paced_frames, 1, "a held stick change delivered by a button frame is not a paced frame");
        m.set(1 << B_A, [600, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(c.due(&m.snap, tb + MS, T), Due::At(tb + T), "the button frame restarted the interval");
        // a release is never held either
        m.set(0, [600, 0, 0, 0, 0, 0, 0, 0]);
        let (d, f) = step(&mut c, &m, &mut st, tb + MS);
        assert_eq!(d, Due::Now);
        assert_eq!(keys(&f), vec![(BTN_A, 0)]);
        // the first change after a quiet period is not delayed
        m.set(0, [700, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(step(&mut c, &m, &mut st, tb + 50 * MS).0, Due::Now);
        // resync / re-attach is immediate even inside the interval
        m.set(0, [800, 0, 0, 0, 0, 0, 0, 0]);
        c.force_full();
        let (d, f) = step(&mut c, &m, &mut st, tb + 50 * MS + 1);
        assert_eq!((d, f.len()), (Due::Now, 20));
    }

    #[test]
    fn zero_interval_is_unpaced() {
        let mut m = Model::new();
        let mut c = Cursor::new();
        let mut st = FrameStats::default();
        let mut f = Frame::new();
        assert_eq!(c.next_frame_at(&m.snap, &m, &mut f, &mut st, 1, 0), Due::Now);
        for v in 1..50 {
            m.set(0, [v, 0, 0, 0, 0, 0, 0, 0]);
            assert_eq!(c.next_frame_at(&m.snap, &m, &mut f, &mut st, 1 + v as u64, 0), Due::Now);
        }
        assert_eq!((st.paced_frames, st.merged_axis_publishes), (0, 0));
    }
}
