//! The publish / read protocol.
//!
//! **State copies.** The writer never touches the copy readers are directed to. It keeps a ring
//! of N copies; publish number `n` goes into copy `n % N`, guarded by that copy's own sequence
//! word (odd while being written), and only then is `published` advanced to `n`. A reader loads
//! `published`, reads that copy, and accepts it if the copy's sequence word was even and
//! unchanged across the read. The writer never waits for readers and readers never wait for the
//! writer: a reader can only be forced to retry if the writer laps the whole ring during one read,
//! and the number of attempts is bounded, so no reader spins forever.
//!
//! The ordering is release/acquire only (no fences): the writer marks the copy odd, stores every
//! data word with Release and stores the even sequence with Release; the reader loads the sequence
//! with Acquire, every data word with Acquire, then re-loads the sequence. If any data word came
//! from a newer write, that Acquire load synchronises with it, so the re-load sees the odd marker
//! (or later) and the read is rejected. This is what the loom model in `loom/` checks.
//!
//! **Journal.** A power-of-two ring of 64-bit entries `(edge index << 32) | payload`. The writer
//! stores an entry before publishing the copy that counts it; a reader asks for an index it saw
//! counted in a snapshot and accepts the entry only if the tag matches, so an entry that was
//! overwritten by a later lap is detected instead of being misread.

use core::sync::atomic::Ordering::{self, Acquire, Relaxed, Release};
use core::sync::atomic::{AtomicU32, AtomicU64};

/// A 32-bit atomic word (real atomics in shared memory, or loom's in the model).
pub trait Word32: Sync {
    fn ld(&self, o: Ordering) -> u32;
    fn st(&self, v: u32, o: Ordering);
}

/// A 64-bit atomic word.
pub trait Word64: Sync {
    fn ld(&self, o: Ordering) -> u64;
    fn st(&self, v: u64, o: Ordering);
}

impl Word32 for AtomicU32 {
    #[inline(always)]
    fn ld(&self, o: Ordering) -> u32 {
        self.load(o)
    }
    #[inline(always)]
    fn st(&self, v: u32, o: Ordering) {
        self.store(v, o)
    }
}

impl Word64 for AtomicU64 {
    #[inline(always)]
    fn ld(&self, o: Ordering) -> u64 {
        self.load(o)
    }
    #[inline(always)]
    fn st(&self, v: u64, o: Ordering) {
        self.store(v, o)
    }
}

/// One versioned copy of the published state: a sequence word and `W` data words.
#[repr(C)]
pub struct StateCopy<T, const W: usize> {
    pub seq: T,
    pub words: [T; W],
}

/// Writer side: publish `data` as publish number `n` (`data[0]` must be `n`). Must only ever be
/// called by one writer at a time for a given ring (the app serialises each slot).
#[inline]
pub fn publish<T: Word32, const W: usize>(
    copies: &[StateCopy<T, W>],
    published: &T,
    n: u32,
    data: &[u32; W],
) {
    let c = &copies[(n as usize) % copies.len()];
    // Even = stable. Masking keeps the invariant even if an earlier writer died mid-copy.
    let base = c.seq.ld(Relaxed) & !1;
    c.seq.st(base.wrapping_add(1), Relaxed);
    for (w, v) in c.words.iter().zip(data.iter()) {
        w.st(*v, Release);
    }
    c.seq.st(base.wrapping_add(2), Release);
    published.st(n, Release);
}

/// Reader side: copy the most recently published state into `out`. Returns false only if every
/// one of `attempts` reads was overtaken by the writer lapping the ring.
///
/// Data word 0 must carry the publish number. A copy is accepted only if it holds exactly the
/// publish `published` named when it was loaded: a copy the writer has already rewritten with a
/// newer publish (it lapped the ring) is consistent but not yet published, and accepting it would
/// let the next snapshot, of the copy `published` actually names, go backwards. With this rule
/// successive snapshots by one reader are monotonic, because successive loads of `published` are.
#[inline]
pub fn snapshot<T: Word32, const W: usize>(
    copies: &[StateCopy<T, W>],
    published: &T,
    out: &mut [u32; W],
    attempts: u32,
) -> bool {
    let mut i = 0;
    while i < attempts {
        i += 1;
        let n = published.ld(Acquire);
        let c = &copies[(n as usize) % copies.len()];
        let s1 = c.seq.ld(Acquire);
        if s1 & 1 == 1 {
            core::hint::spin_loop();
            continue;
        }
        for (o, w) in out.iter_mut().zip(c.words.iter()) {
            *o = w.ld(Acquire);
        }
        if c.seq.ld(Relaxed) == s1 && out.first().map_or(true, |v| *v == n) {
            return true;
        }
        core::hint::spin_loop();
    }
    false
}

/// A memory fence (real, or loom's in the model).
pub trait Fence {
    fn fence(o: Ordering);
}

pub struct NativeFence;

impl Fence for NativeFence {
    #[inline(always)]
    fn fence(o: Ordering) {
        core::sync::atomic::fence(o)
    }
}

/// Control block, writer side (one writer): `seq` odd, Release fence, the record words, `gen`
/// + 1 (never 0), `seq` even with Release. Returns the new gen. This is the classic fence-based
/// seqlock, the form a 32-bit reader without 64-bit atomics can mirror exactly (the XInput DLL).
pub fn ctl_write<T: Word32, F: Fence>(seq: &T, gen: &T, words: &[T], data: &[u32]) -> u32 {
    let s = seq.ld(Relaxed) & !1;
    seq.st(s.wrapping_add(1), Relaxed);
    F::fence(Release);
    for (w, v) in words.iter().zip(data.iter()) {
        w.st(*v, Relaxed);
    }
    let g = match gen.ld(Relaxed).wrapping_add(1) {
        0 => 1,
        g => g,
    };
    gen.st(g, Relaxed);
    seq.st(s.wrapping_add(2), Release);
    g
}

/// Control block, reader side: copy the record and its gen into `out`, consistently. `seq` with
/// Acquire, the words (and gen) Relaxed, an Acquire fence, `seq` again: equal and even means no
/// write overlapped (if one had, the writer's Release fence before its words synchronises with our
/// Acquire fence and the second load sees the odd value or later). At most `attempts` tries,
/// `pause(i)` between them; None if the writer kept it busy that long: never blocks.
pub fn ctl_read<T: Word32, F: Fence>(
    seq: &T,
    gen: &T,
    words: &[T],
    out: &mut [u32],
    attempts: u32,
    mut pause: impl FnMut(u32),
) -> Option<u32> {
    for i in 0..attempts {
        let s1 = seq.ld(Acquire);
        if s1 & 1 == 0 {
            for (o, w) in out.iter_mut().zip(words.iter()) {
                *o = w.ld(Relaxed);
            }
            let g = gen.ld(Relaxed);
            F::fence(Acquire);
            if seq.ld(Relaxed) == s1 {
                return Some(g);
            }
        }
        pause(i);
    }
    None
}

/// Writer side: record journal entry `idx`.
#[inline]
pub fn journal_put<T: Word64>(ring: &[T], idx: u32, payload: u32) {
    ring[(idx as usize) % ring.len()].st(((idx as u64) << 32) | payload as u64, Release);
}

/// Reader side: the payload of journal entry `idx`, or None if it has been overwritten (or was
/// never written).
#[inline]
pub fn journal_get<T: Word64>(ring: &[T], idx: u32) -> Option<u32> {
    let v = ring[(idx as usize) % ring.len()].ld(Acquire);
    if (v >> 32) as u32 == idx {
        Some(v as u32)
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::AtomicBool;
    use std::sync::Arc;

    struct Ring {
        copies: [StateCopy<AtomicU32, 6>; 4],
        published: AtomicU32,
    }

    fn ring() -> Ring {
        Ring {
            copies: core::array::from_fn(|_| StateCopy {
                seq: AtomicU32::new(0),
                words: core::array::from_fn(|_| AtomicU32::new(0)),
            }),
            published: AtomicU32::new(0),
        }
    }

    #[test]
    fn no_torn_snapshot_under_contention() {
        let r = Arc::new(ring());
        let stop = Arc::new(AtomicBool::new(false));
        let w = {
            let r = r.clone();
            let stop = stop.clone();
            std::thread::spawn(move || {
                let mut n = 0u32;
                while !stop.load(Relaxed) {
                    n = n.wrapping_add(1);
                    let v = n.wrapping_mul(2654435761);
                    publish(&r.copies, &r.published, n, &[n, v, v, v, v, v]);
                }
                n
            })
        };
        let readers: Vec<_> = (0..3)
            .map(|_| {
                let r = r.clone();
                std::thread::spawn(move || {
                    let mut out = [0u32; 6];
                    let mut last = 0u32;
                    let mut ok = 0u32;
                    for _ in 0..200_000 {
                        if snapshot(&r.copies, &r.published, &mut out, 64) {
                            let v = out[0].wrapping_mul(2654435761);
                            assert!(out[1..].iter().all(|x| *x == v), "torn copy {:?}", out);
                            assert!(out[0] >= last, "went backwards {} < {}", out[0], last);
                            last = out[0];
                            ok += 1;
                        }
                    }
                    ok
                })
            })
            .collect();
        for r in readers {
            assert!(r.join().unwrap() > 0);
        }
        stop.store(true, Relaxed);
        assert!(w.join().unwrap() > 0);
    }

    #[test]
    fn control_seqlock_is_never_torn_under_contention() {
        let seq = Arc::new(AtomicU32::new(0));
        let gen = Arc::new(AtomicU32::new(0));
        let words: Arc<[AtomicU32; 8]> = Arc::new(core::array::from_fn(|_| AtomicU32::new(0)));
        let stop = Arc::new(AtomicBool::new(false));
        let w = {
            let (seq, gen, words, stop) = (seq.clone(), gen.clone(), words.clone(), stop.clone());
            std::thread::spawn(move || {
                let mut n = 0u32;
                while !stop.load(Relaxed) {
                    n = n.wrapping_add(1);
                    let g = ctl_write::<_, NativeFence>(&*seq, &*gen, &words[..], &[n; 8]);
                    assert_eq!(g, n);
                }
            })
        };
        let mut out = [0u32; 8];
        let mut ok = 0;
        for _ in 0..200_000 {
            if let Some(g) = ctl_read::<_, NativeFence>(&*seq, &*gen, &words[..], &mut out, 64, |_| core::hint::spin_loop()) {
                assert!(out.iter().all(|v| *v == out[0]), "torn control record {:?}", out);
                assert_eq!(g, out[0], "gen read with its record");
                ok += 1;
            }
        }
        stop.store(true, Relaxed);
        w.join().unwrap();
        assert!(ok > 0);
    }

    #[test]
    fn journal_detects_overwrite() {
        let ring: [AtomicU64; 8] = core::array::from_fn(|_| AtomicU64::new(0));
        for i in 1..=8u32 {
            journal_put(&ring, i, i * 10);
        }
        assert_eq!(journal_get(&ring, 3), Some(30));
        journal_put(&ring, 11, 110);
        assert_eq!(journal_get(&ring, 3), None, "lapped entry must not be accepted");
        assert_eq!(journal_get(&ring, 11), Some(110));
        assert_eq!(journal_get(&ring, 19), None);
    }
}
