//! On-disk layout of one slot file (format 1).
//!
//! ```text
//! 0x000  Header (256 bytes)
//!          0x00  magic "HERMESIO" (u64), format major/minor, sizes, slot, instance id,
//!                retired flag, writer pid, creation time
//!          0x40  bell (futex word, bumped on every publish and on retirement), published index,
//!                the game frame feed (interval, last frame time; minor 2)
//!          0x80  writer statistics (+ two reader pacing counters at 0xa8, 0xb0 since minor 1)
//!          0xc0  reader statistics (readers add to these)
//! 0x100  4 state copies x 128 bytes (sequence word + 31 data words, see W_*)
//! 0x300  journal: 256 x u64 button-edge entries
//! 0xb00  control block (minor 5, 256 bytes; `ff_mode` and the force-feedback counters since
//!        minor 6, see `Control`)
//! 0xc00  end of the layout; the file is FILE_SIZE (4096) bytes and never grows
//! ```
//!
//! Compatibility: readers accept major == 1 and a file of at least FILE_SIZE bytes. A compatible
//! extension appends a new region after 0xb00 (inside the same 4 KiB, or by growing FILE_SIZE for
//! new files) and bumps `minor`; format-1 readers ignore it. Anything incompatible bumps `major`.

use core::mem::{offset_of, size_of};
use core::sync::atomic::Ordering::{Acquire, Relaxed};
use core::sync::atomic::{AtomicU32, AtomicU64};

use crate::abi::*;
use crate::proto::{self, StateCopy};

pub const MAGIC: u64 = u64::from_le_bytes(*b"HERMESIO");
pub const FORMAT_MAJOR: u32 = 1;
/// Minor 1 added the pacing counters `r_paced_frames` / `r_merged_axis` (formerly padding);
/// minor 2 the game frame feed (`frame_interval_ns`, `frame_time_ns`) and `r_axis_hz` /
/// `r_auto_fallbacks`; minor 3 the frame credits (`frame_count`, `frame_waiters`, `r_axis_mode`)
/// and `r_helper_wakes`; minor 4 `r_auto_max` and AXIS_MODE_CEILING; minor 5 the control block
/// at 0xB00 (live stick pacing and controller identity, see `Control`); minor 6 the
/// force-feedback mode (`ff_mode`, 0xB24) and four reader force-feedback counters (0xBCC..0xBD8).
/// All zero in older files, which readers treat as "no frame feed" / "no control block" /
/// "force feedback unset (on)".
pub const FORMAT_MINOR: u32 = 6;

/// `r_axis_mode` values: how a reader last paced stick-only frames.
pub const AXIS_MODE_UNKNOWN: u32 = 0;
/// One stick-only frame per new game frame (auto, fresh feed).
pub const AXIS_MODE_CREDITS: u32 = 1;
/// Auto, but no fresh frame feed: a fixed 60 Hz.
pub const AXIS_MODE_FALLBACK: u32 = 2;
/// HERMES_AXIS_HZ = N.
pub const AXIS_MODE_FIXED: u32 = 3;
/// HERMES_AXIS_HZ = 0.
pub const AXIS_MODE_UNPACED: u32 = 4;
/// Auto with a fresh feed, but the game presents faster than the auto ceiling
/// (HERMES_AXIS_AUTO_MAX): the ceiling's minimum interval governs, credits still apply.
pub const AXIS_MODE_CEILING: u32 = 5;

pub const HEADER_SIZE: usize = 256;
pub const COPY_WORDS: usize = 31;
pub const COPY_SIZE: usize = 128;
pub const COPY_COUNT: usize = 4;
pub const JOURNAL_LEN: usize = 256;
pub const COPIES_OFF: usize = HEADER_SIZE;
pub const JOURNAL_OFF: usize = COPIES_OFF + COPY_COUNT * COPY_SIZE;
/// The control block (minor 5): right after the journal, 256 bytes.
pub const CTL_OFF: usize = JOURNAL_OFF + JOURNAL_LEN * 8; // 0xB00
pub const CTL_SIZE: usize = 256;
pub const LAYOUT_END: usize = CTL_OFF + CTL_SIZE; // 0xC00; 0x1000 and beyond stay free
/// Size of every slot file, and of every mapping of one.
pub const FILE_SIZE: usize = 4096;

// Data words of one state copy.
pub const W_PUB: usize = 0;
pub const W_FLAGS: usize = 1;
pub const W_UNPLUG: usize = 2;
pub const W_EDGES: usize = 3;
pub const W_COUNTS: usize = 4;
pub const W_AXES: usize = W_COUNTS + BUTTONS; // 15
pub const W_TIME_LO: usize = W_AXES + AXES; // 23
pub const W_TIME_HI: usize = W_TIME_LO + 1; // 24

/// `W_FLAGS`: a writer has opened the slot (a controller is assigned to it).
pub const FLAG_CONNECTED: u32 = 1;

// Journal payload bits.
pub const J_BUTTON_MASK: u32 = 0xff;
pub const J_PRESS: u32 = 1 << 8;
/// First edge of one publish: edges up to the next entry carrying this bit form one frame.
pub const J_GROUP: u32 = 1 << 9;

#[repr(C)]
pub struct Header {
    pub magic: AtomicU64,
    pub major: AtomicU32,
    pub minor: AtomicU32,
    pub file_size: AtomicU32,
    pub header_size: AtomicU32,
    pub copy_size: AtomicU32,
    pub copy_count: AtomicU32,
    pub journal_len: AtomicU32,
    pub slot: AtomicU32,
    pub instance: AtomicU64,
    /// Set by the writer when it abandons this file (replaced or released). Readers then reopen
    /// the path, or show a neutral pad until a new file appears.
    pub retired: AtomicU32,
    pub writer_pid: AtomicU32,
    pub created_ns: AtomicU64,
    // 0x40: the wake line
    pub bell: AtomicU32,
    pub published: AtomicU32,
    /// The game's last observed frame interval (ns, 0 = unknown, or a gap over 1 s), written by
    /// the app's frame feed (minor 2), and when the game last presented a frame (CLOCK_MONOTONIC).
    pub frame_interval_ns: AtomicU64,
    pub frame_time_ns: AtomicU64,
    /// The stick-frame rate a reader last paced to (Hz; in credit mode the game's frame rate).
    pub r_axis_hz: AtomicU32,
    /// How that reader paced (AXIS_MODE_*, minor 3).
    pub r_axis_mode: AtomicU32,
    /// Game frames presented so far (+1 per real game frame; the stick-frame credits, minor 3).
    pub frame_count: AtomicU64,
    /// Reader wake threads currently waiting for the next game frame: the feed wakes the bell for
    /// a frame only while this is non-zero (minor 3).
    pub frame_waiters: AtomicU32,
    /// The auto-mode ceiling a reader paces under (Hz, HERMES_AXIS_AUTO_MAX; minor 4).
    pub r_auto_max: AtomicU32,
    /// Wake-ups of the readers' per-slot wake threads, summed over every reader process (minor 3):
    /// what an idle pad costs in CPU wake-ups.
    pub r_helper_wakes: AtomicU64,
    pub _pad1: [AtomicU32; 2],
    // 0x80: writer statistics
    pub w_publishes: AtomicU64,
    pub w_wake_calls: AtomicU64,
    pub w_woken: AtomicU64,
    pub w_unplugs: AtomicU64,
    pub w_edges: AtomicU64,
    // Reader counters that did not fit the reader line (minor 1; zero in minor-0 files):
    /// Axis-only frames readers held back by pacing before delivering them.
    pub r_paced_frames: AtomicU64,
    /// Publishes readers merged into a later frame instead of delivering one each.
    pub r_merged_axis: AtomicU64,
    /// Times a reader in auto pacing lost a fresh frame feed and fell back to the fixed rate.
    pub r_auto_fallbacks: AtomicU64,
    // 0xc0: reader statistics, summed over every reader of this file
    pub r_attaches: AtomicU64,
    pub r_reattaches: AtomicU64,
    pub r_taps: AtomicU64,
    pub r_coalesced: AtomicU64,
    pub r_resyncs: AtomicU64,
    pub r_frames: AtomicU64,
    pub r_events: AtomicU64,
    pub r_removals: AtomicU64,
}

pub type CopyBlock = StateCopy<AtomicU32, COPY_WORDS>;

#[repr(C)]
pub struct Shared {
    pub header: Header,
    pub copies: [CopyBlock; COPY_COUNT],
    pub journal: [AtomicU64; JOURNAL_LEN],
    pub ctl: Control,
}

// ---------------------------------------------------------------------------------------------
// Control block (minor 5): session settings the app can change live (stick pacing, the XInput
// DLL's mode, and since minor 6 the force-feedback mode) and the slot's controller identity. Every field is a 32-bit word so that a 32-bit
// reader without 64-bit atomics (the i386 XInput DLL) reads it the same way. One writer (the app,
// per slot); readers use the seqlock: `seq` odd while a record is being written, `gen` bumped by
// every completed update. A control update never rings the bell: readers pick it up at their
// next pacing decision, and the identity only at attach.

pub const CTL_MAGIC: u32 = u32::from_le_bytes(*b"HCTL");
/// The first header minor that carries a control block.
pub const CTL_FIRST_MINOR: u32 = 5;
/// The first header minor whose control block carries `ff_mode` and the force-feedback counters
/// (both reserved, 0, in a minor-5 block: force feedback unset, i.e. on).
pub const FF_FIRST_MINOR: u32 = 6;
/// Seqlock read attempts before keeping the last good copy.
pub const CTL_READ_ATTEMPTS: u32 = 64;
/// Words of the record the seqlock protects: control offsets 0x10 .. 0xA4.
pub const CTL_REC_WORDS: usize = 37;

/// Indices into `Control::rec` (control offset = 0x10 + 4 * index).
pub mod rec {
    pub const AXIS_MODE: usize = 0; // 0xB10
    pub const AXIS_HZ: usize = 1; // 0xB14
    pub const AXIS_AUTO_MAX: usize = 2; // 0xB18
    pub const XINPUT_MODE: usize = 3; // 0xB1C
    pub const XINPUT_FLAGS: usize = 4; // 0xB20
    pub const FF_MODE: usize = 5; // 0xB24 (minor 6)
    // 6 ..= 11: 0xB28 .. 0xB3F reserved, 0
    pub const ID_FLAGS: usize = 12; // 0xB40
    pub const ID_BUSTYPE: usize = 13; // 0xB44
    pub const ID_VENDOR: usize = 14; // 0xB48
    pub const ID_PRODUCT: usize = 15; // 0xB4C
    pub const ID_VERSION: usize = 16; // 0xB50
    pub const ID_KEY: usize = 17; // 0xB54, 16 bytes = 4 words
    pub const ID_NAME: usize = 21; // 0xB64, 64 bytes = 16 words, up to 0xBA4
}

/// `axis_mode` values.
pub const CTL_AXIS_UNSET: u32 = 0; // follow HERMES_AXIS_HZ / HERMES_AXIS_AUTO_MAX as before
pub const CTL_AXIS_AUTO: u32 = 1; // frame credits + ceiling (axis_auto_max)
pub const CTL_AXIS_FIXED: u32 = 2; // a fixed axis_hz
pub const CTL_AXIS_UNPACED: u32 = 3;
/// `xinput_mode` values (read by the XInput DLL, not by this library's reader).
pub const CTL_XINPUT_UNSET: u32 = 0; // = direct
pub const CTL_XINPUT_DIRECT: u32 = 1;
pub const CTL_XINPUT_FORWARD: u32 = 2;
/// `xinput_flags` bits.
pub const CTL_XI_FORWARD_AVAILABLE: u32 = 1;
pub const CTL_XI_HIDDEN: u32 = 2;
pub const CTL_XI_FLAGS_KNOWN: u32 = CTL_XI_FORWARD_AVAILABLE | CTL_XI_HIDDEN;
/// `ff_mode` values (minor 6): DirectInput force feedback for the slot's handles, read when a
/// handle attaches. HERMES_DINPUT_FF in the guest overrides it.
pub const CTL_FF_UNSET: u32 = 0; // = on
pub const CTL_FF_ON: u32 = 1; // every effect type offered
pub const CTL_FF_OFF: u32 = 2; // rumble effects only
/// `id_flags` bit 0: the identity fields hold a controller identity.
pub const CTL_ID_VALID: u32 = 1;
pub const ID_KEY_LEN: usize = 16;
pub const ID_NAME_LEN: usize = 64;

#[repr(C)]
pub struct Control {
    pub magic: AtomicU32, // 0x00 "HCTL", stored with Release once initialised
    pub size: AtomicU32,  // 0x04 256
    pub seq: AtomicU32,   // 0x08 seqlock word, odd while writing
    pub gen: AtomicU32,   // 0x0C +1 per completed update, nonzero once initialised
    pub rec: [AtomicU32; CTL_REC_WORDS], // 0x10 .. 0xA4, see `rec`
    pub _res1: [AtomicU32; 7], // 0xA4 .. 0xBF reserved
    pub r_ctl_gen_applied: AtomicU32, // 0xC0 reader stat: the control gen a reader last applied
    pub r_id_gen_attached: AtomicU32, // 0xC4 reader stat: the gen whose identity an attach took
    pub r_identity_reads: AtomicU32,  // 0xC8 reader stat: identity queries answered
    // minor 6, readers add to these (force feedback, all reader processes):
    pub r_ff_uploads: AtomicU32,     // 0xCC effects uploaded (new or updated)
    pub r_ff_plays: AtomicU32,       // 0xD0 effects started (play with a count)
    pub r_ff_datagrams: AtomicU32,   // 0xD4 rumble datagrams sent to the app
    pub r_ff_guard_fades: AtomicU32, // 0xD8 anti-buzz guard fades begun
    pub _res2: [AtomicU32; 9], // 0xDC .. 0xFF reserved
}

/// A controller identity as the control block carries it.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct Identity {
    pub bustype: u16,
    pub vendor: u16,
    pub product: u16,
    pub version: u16,
    /// ASCII, NUL-terminated (at most 15 characters).
    pub key: [u8; ID_KEY_LEN],
    /// UTF-8, NUL-terminated (at most 63 bytes, cut at a character boundary).
    pub name: [u8; ID_NAME_LEN],
}

/// Copy `src` into a NUL-terminated field, cut so it fits and (`utf8`) never mid-character.
fn put_str<const N: usize>(src: &[u8], utf8: bool) -> [u8; N] {
    let mut out = [0u8; N];
    let mut n = src.iter().position(|c| *c == 0).unwrap_or(src.len()).min(N - 1);
    if utf8 {
        // never end inside a multi-byte sequence: back off over continuation bytes
        while n > 0 && n < src.len() && (src[n] & 0xC0) == 0x80 {
            n -= 1;
        }
    }
    out[..n].copy_from_slice(&src[..n]);
    out
}

impl Identity {
    pub fn new(bustype: u16, vendor: u16, product: u16, version: u16, key: &[u8], name: &[u8]) -> Identity {
        Identity { bustype, vendor, product, version, key: put_str::<ID_KEY_LEN>(key, false), name: put_str::<ID_NAME_LEN>(name, true) }
    }
    /// The text of a NUL-terminated field.
    pub fn text(field: &[u8]) -> &[u8] {
        &field[..field.iter().position(|c| *c == 0).unwrap_or(field.len())]
    }
}

/// The record behind the control block's seqlock.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct ControlState {
    pub axis_mode: u32,
    pub axis_hz: u32,
    pub axis_auto_max: u32,
    pub xinput_mode: u32,
    pub xinput_flags: u32,
    /// CTL_FF_* (minor 6; 0 in older blocks).
    pub ff_mode: u32,
    pub id: Option<Identity>,
}

fn pack(bytes: &[u8], words: &mut [u32]) {
    for (i, w) in words.iter_mut().enumerate() {
        let b = |k: usize| bytes.get(4 * i + k).copied().unwrap_or(0) as u32;
        *w = b(0) | (b(1) << 8) | (b(2) << 16) | (b(3) << 24);
    }
}

fn unpack<const N: usize>(words: &[u32]) -> [u8; N] {
    let mut out = [0u8; N];
    for (i, c) in out.iter_mut().enumerate() {
        *c = (words[i / 4] >> (8 * (i % 4))) as u8;
    }
    out[N - 1] = 0; // always NUL-terminated, whatever the file says
    out
}

impl ControlState {
    pub const DEFAULT: ControlState =
        ControlState { axis_mode: 0, axis_hz: 0, axis_auto_max: 0, xinput_mode: 0, xinput_flags: 0, ff_mode: 0, id: None };

    pub fn encode(&self) -> [u32; CTL_REC_WORDS] {
        let mut w = [0u32; CTL_REC_WORDS];
        w[rec::AXIS_MODE] = self.axis_mode;
        w[rec::AXIS_HZ] = self.axis_hz;
        w[rec::AXIS_AUTO_MAX] = self.axis_auto_max;
        w[rec::XINPUT_MODE] = self.xinput_mode;
        w[rec::XINPUT_FLAGS] = self.xinput_flags;
        w[rec::FF_MODE] = self.ff_mode;
        if let Some(id) = &self.id {
            w[rec::ID_FLAGS] = CTL_ID_VALID;
            w[rec::ID_BUSTYPE] = id.bustype as u32;
            w[rec::ID_VENDOR] = id.vendor as u32;
            w[rec::ID_PRODUCT] = id.product as u32;
            w[rec::ID_VERSION] = id.version as u32;
            pack(&id.key, &mut w[rec::ID_KEY..rec::ID_KEY + ID_KEY_LEN / 4]);
            pack(&id.name, &mut w[rec::ID_NAME..rec::ID_NAME + ID_NAME_LEN / 4]);
        }
        w
    }

    pub fn decode(w: &[u32; CTL_REC_WORDS]) -> ControlState {
        let id = if w[rec::ID_FLAGS] & CTL_ID_VALID != 0 {
            Some(Identity {
                bustype: w[rec::ID_BUSTYPE] as u16,
                vendor: w[rec::ID_VENDOR] as u16,
                product: w[rec::ID_PRODUCT] as u16,
                version: w[rec::ID_VERSION] as u16,
                key: unpack::<ID_KEY_LEN>(&w[rec::ID_KEY..rec::ID_KEY + ID_KEY_LEN / 4]),
                name: unpack::<ID_NAME_LEN>(&w[rec::ID_NAME..rec::ID_NAME + ID_NAME_LEN / 4]),
            })
        } else {
            None
        };
        ControlState {
            axis_mode: w[rec::AXIS_MODE],
            axis_hz: w[rec::AXIS_HZ],
            axis_auto_max: w[rec::AXIS_AUTO_MAX],
            xinput_mode: w[rec::XINPUT_MODE],
            xinput_flags: w[rec::XINPUT_FLAGS],
            ff_mode: w[rec::FF_MODE],
            id,
        }
    }
}

const _: () = {
    assert!(size_of::<Header>() == HEADER_SIZE);
    assert!(offset_of!(Header, instance) == 40);
    assert!(offset_of!(Header, bell) == 64);
    assert!(offset_of!(Header, published) == 68);
    assert!(offset_of!(Header, frame_interval_ns) == 72);
    assert!(offset_of!(Header, frame_time_ns) == 80);
    assert!(offset_of!(Header, r_axis_hz) == 88);
    assert!(offset_of!(Header, r_axis_mode) == 92);
    assert!(offset_of!(Header, frame_count) == 96);
    assert!(offset_of!(Header, frame_waiters) == 104);
    assert!(offset_of!(Header, r_auto_max) == 108);
    assert!(offset_of!(Header, r_helper_wakes) == 112);
    assert!(offset_of!(Header, r_auto_fallbacks) == 184);
    assert!(offset_of!(Header, w_publishes) == 128);
    assert!(offset_of!(Header, r_paced_frames) == 168);
    assert!(offset_of!(Header, r_merged_axis) == 176);
    assert!(offset_of!(Header, r_attaches) == 192);
    assert!(size_of::<CopyBlock>() == COPY_SIZE);
    assert!(offset_of!(Shared, copies) == COPIES_OFF);
    assert!(offset_of!(Shared, journal) == JOURNAL_OFF);
    assert!(size_of::<Shared>() == LAYOUT_END);
    // the control block, exactly as specified
    assert!(offset_of!(Shared, ctl) == 0xB00);
    assert!(size_of::<Control>() == CTL_SIZE);
    assert!(offset_of!(Control, magic) == 0x00);
    assert!(offset_of!(Control, size) == 0x04);
    assert!(offset_of!(Control, seq) == 0x08);
    assert!(offset_of!(Control, gen) == 0x0C);
    assert!(offset_of!(Control, rec) == 0x10);
    assert!(0x10 + 4 * rec::AXIS_MODE == 0x10);
    assert!(0x10 + 4 * rec::AXIS_HZ == 0x14);
    assert!(0x10 + 4 * rec::AXIS_AUTO_MAX == 0x18);
    assert!(0x10 + 4 * rec::XINPUT_MODE == 0x1C);
    assert!(0x10 + 4 * rec::XINPUT_FLAGS == 0x20);
    assert!(0x10 + 4 * rec::FF_MODE == 0x24);
    assert!(0x10 + 4 * rec::ID_FLAGS == 0x40);
    assert!(0x10 + 4 * rec::ID_BUSTYPE == 0x44);
    assert!(0x10 + 4 * rec::ID_VENDOR == 0x48);
    assert!(0x10 + 4 * rec::ID_PRODUCT == 0x4C);
    assert!(0x10 + 4 * rec::ID_VERSION == 0x50);
    assert!(0x10 + 4 * rec::ID_KEY == 0x54);
    assert!(0x10 + 4 * rec::ID_NAME == 0x64);
    assert!(0x10 + 4 * (rec::ID_NAME + ID_NAME_LEN / 4) == 0xA4);
    assert!(0x10 + 4 * CTL_REC_WORDS == 0xA4);
    assert!(offset_of!(Control, r_ctl_gen_applied) == 0xC0);
    assert!(offset_of!(Control, r_id_gen_attached) == 0xC4);
    assert!(offset_of!(Control, r_identity_reads) == 0xC8);
    assert!(offset_of!(Control, r_ff_uploads) == 0xCC);
    assert!(offset_of!(Control, r_ff_plays) == 0xD0);
    assert!(offset_of!(Control, r_ff_datagrams) == 0xD4);
    assert!(offset_of!(Control, r_ff_guard_fades) == 0xD8);
    assert!(offset_of!(Control, _res2) == 0xDC);
    assert!(LAYOUT_END <= 0x1000);
    assert!(LAYOUT_END <= FILE_SIZE);
    assert!(W_TIME_HI < COPY_WORDS);
    assert!(JOURNAL_LEN.is_power_of_two());
};

/// One consistent published state.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct Snapshot {
    pub pub_seq: u32,
    pub flags: u32,
    /// Number of unplugs (`destroy()`) of this slot so far. A reader that saw a different value
    /// when it attached has been unplugged.
    pub unplug: u32,
    /// Total button edges so far (index of the newest journal entry).
    pub edges: u32,
    /// Per-button edge counters; parity = pressed.
    pub counts: [u32; BUTTONS],
    pub axes: [i32; AXES],
    pub time_ns: u64,
}

impl Snapshot {
    /// What a reader shows while it has no usable backing: every button up, every axis 0.
    pub const NEUTRAL: Snapshot = Snapshot {
        pub_seq: 0,
        flags: 0,
        unplug: 0,
        edges: 0,
        counts: [0; BUTTONS],
        axes: [0; AXES],
        time_ns: 0,
    };

    #[inline]
    pub fn buttons(&self) -> u16 {
        parity(&self.counts)
    }

    pub fn encode(&self) -> [u32; COPY_WORDS] {
        let mut w = [0u32; COPY_WORDS];
        w[W_PUB] = self.pub_seq;
        w[W_FLAGS] = self.flags;
        w[W_UNPLUG] = self.unplug;
        w[W_EDGES] = self.edges;
        w[W_COUNTS..W_COUNTS + BUTTONS].copy_from_slice(&self.counts);
        for i in 0..AXES {
            w[W_AXES + i] = self.axes[i] as u32;
        }
        w[W_TIME_LO] = self.time_ns as u32;
        w[W_TIME_HI] = (self.time_ns >> 32) as u32;
        w
    }

    pub fn decode(w: &[u32; COPY_WORDS]) -> Snapshot {
        let mut counts = [0u32; BUTTONS];
        counts.copy_from_slice(&w[W_COUNTS..W_COUNTS + BUTTONS]);
        let mut axes = [0i32; AXES];
        for i in 0..AXES {
            axes[i] = w[W_AXES + i] as i32;
        }
        Snapshot {
            pub_seq: w[W_PUB],
            flags: w[W_FLAGS],
            unplug: w[W_UNPLUG],
            edges: w[W_EDGES],
            counts,
            axes,
            time_ns: (w[W_TIME_LO] as u64) | ((w[W_TIME_HI] as u64) << 32),
        }
    }
}

impl Shared {
    /// # Safety
    /// `p` must point at a live mapping of at least `FILE_SIZE` bytes.
    #[inline]
    pub unsafe fn at<'a>(p: *const u8) -> &'a Shared {
        &*(p as *const Shared)
    }

    /// True if this is a format-1 slot file for `slot` (magic checked first, with Acquire, so the
    /// rest of the header the writer initialised before it is visible).
    pub fn valid_for(&self, slot: u32) -> bool {
        let h = &self.header;
        h.magic.load(Acquire) == MAGIC
            && h.major.load(Relaxed) == FORMAT_MAJOR
            && h.header_size.load(Relaxed) as usize == HEADER_SIZE
            && h.copy_size.load(Relaxed) as usize == COPY_SIZE
            && h.copy_count.load(Relaxed) as usize == COPY_COUNT
            && h.journal_len.load(Relaxed) as usize == JOURNAL_LEN
            && h.file_size.load(Relaxed) as usize >= FILE_SIZE
            && h.slot.load(Relaxed) == slot
    }

    #[inline]
    pub fn is_retired(&self) -> bool {
        self.header.retired.load(Acquire) != 0
    }

    /// A consistent copy of the latest published state.
    #[inline]
    pub fn snapshot(&self, attempts: u32) -> Option<Snapshot> {
        let mut w = [0u32; COPY_WORDS];
        if proto::snapshot(&self.copies, &self.header.published, &mut w, attempts) {
            Some(Snapshot::decode(&w))
        } else {
            None
        }
    }

    /// This file carries a usable control block (minor >= 5, magic and size right). Otherwise the
    /// reader behaves exactly as before the control block existed.
    pub fn control_present(&self) -> bool {
        self.header.minor.load(Relaxed) >= CTL_FIRST_MINOR
            && self.ctl.magic.load(Acquire) == CTL_MAGIC
            && self.ctl.size.load(Relaxed) as usize == CTL_SIZE
    }

    /// The control block also carries the force-feedback counters (minor >= 6).
    pub fn ff_stats_present(&self) -> bool {
        self.control_present() && self.header.minor.load(Relaxed) >= FF_FIRST_MINOR
    }

    /// One consistent control record and the gen it belongs to, or None if the block is absent or
    /// the writer kept it busy for CTL_READ_ATTEMPTS reads (the caller keeps its last good copy).
    pub fn read_control(&self) -> Option<(u32, ControlState)> {
        if !self.control_present() {
            return None;
        }
        let c = &self.ctl;
        let mut w = [0u32; CTL_REC_WORDS];
        let pause = |i: u32| {
            if i < 16 {
                core::hint::spin_loop();
            } else {
                crate::sys::sched_yield();
            }
        };
        let gen = proto::ctl_read::<_, proto::NativeFence>(&c.seq, &c.gen, &c.rec, &mut w, CTL_READ_ATTEMPTS, pause)?;
        Some((gen, ControlState::decode(&w)))
    }

    #[inline]
    pub fn journal_get(&self, idx: u32) -> Option<u32> {
        proto::journal_get(&self.journal, idx)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn snapshot_roundtrip() {
        let s = Snapshot {
            pub_seq: 7,
            flags: FLAG_CONNECTED,
            unplug: 2,
            edges: 99,
            counts: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
            axes: [-32768, 32767, -1, 1, 0, 255, -1, 1],
            time_ns: 0x1234_5678_9abc_def0,
        };
        assert_eq!(Snapshot::decode(&s.encode()), s);
        assert_eq!(s.buttons(), 0b101_0101_0101);
    }

    #[test]
    fn control_state_roundtrip_and_string_fields() {
        let id = Identity::new(3, 0x045e, 0x028e, 0x0114, b"xbox360", "Xbox 360 Controller (0)".as_bytes());
        let st = ControlState { axis_mode: CTL_AXIS_FIXED, axis_hz: 30, axis_auto_max: 0, xinput_mode: 2, xinput_flags: 3, ff_mode: CTL_FF_OFF, id: Some(id) };
        let w = st.encode();
        assert_eq!(ControlState::decode(&w), st);
        assert_eq!(w[rec::FF_MODE], CTL_FF_OFF, "ff_mode is the word at 0xB24");
        assert_eq!(w[rec::ID_KEY], u32::from_le_bytes(*b"xbox"), "key bytes are little-endian in the words");
        assert_eq!(Identity::text(&id.name), b"Xbox 360 Controller (0)");
        // overlong strings are cut, NUL-terminated, never mid-character
        let long = Identity::new(0, 0, 0, 0, b"0123456789abcdefXYZ", "é".repeat(40).as_bytes());
        assert_eq!(Identity::text(&long.key), b"0123456789abcde");
        let name = Identity::text(&long.name);
        assert_eq!(name.len(), 62);
        assert!(core::str::from_utf8(name).is_ok());
        // no identity: the id words stay zero
        assert_eq!(ControlState::decode(&ControlState::DEFAULT.encode()), ControlState::DEFAULT);
    }

    #[test]
    fn magic_is_the_ascii_tag() {
        assert_eq!(&MAGIC.to_le_bytes(), b"HERMESIO");
    }
}
