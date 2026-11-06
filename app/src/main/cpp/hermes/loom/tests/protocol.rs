//! Exhaustive interleaving checks (within loom's preemption bound) of the exact publish / read
//! code the transport uses, instantiated over loom's atomics.

use hermes_core::proto::{ctl_read, ctl_write, journal_get, journal_put, publish, snapshot, StateCopy, Word32, Word64};
use hermes_loom::{LoomFence, L32, L64};
use loom::sync::Arc;
use loom::thread;
use std::sync::atomic::Ordering::{Acquire, Release};

const W: usize = 2;

struct Ring {
    copies: [StateCopy<L32, W>; 2],
    published: L32,
}

fn copy() -> StateCopy<L32, W> {
    StateCopy { seq: L32::new(0), words: [L32::new(0), L32::new(0)] }
}

fn ring() -> Ring {
    Ring { copies: [copy(), copy()], published: L32::new(0) }
}

/// A reader racing a writer that laps the (two-copy) ring never accepts a torn copy, and once the
/// writer is done the latest publish is what it reads.
#[test]
fn snapshot_is_never_torn_even_when_the_writer_laps() {
    loom::model(|| {
        let r = Arc::new(ring());
        let w = {
            let r = r.clone();
            thread::spawn(move || {
                for n in 1..=3u32 {
                    publish(&r.copies, &r.published, n, &[n, n.wrapping_mul(7) + 1]);
                }
            })
        };
        let mut out = [0u32; W];
        if snapshot(&r.copies, &r.published, &mut out, 3) {
            if out[0] == 0 {
                assert_eq!(out, [0, 0], "initial state");
            } else {
                assert_eq!(out[1], out[0].wrapping_mul(7) + 1, "torn copy {:?}", out);
                assert!(out[0] <= 3);
            }
        }
        w.join().unwrap();
        assert!(snapshot(&r.copies, &r.published, &mut out, 1), "quiescent snapshot must succeed");
        assert_eq!(out, [3, 22]);
    });
}

/// Two successive snapshots by one reader never go backwards.
#[test]
fn snapshots_are_monotonic() {
    loom::model(|| {
        let r = Arc::new(ring());
        let w = {
            let r = r.clone();
            thread::spawn(move || {
                for n in 1..=2u32 {
                    publish(&r.copies, &r.published, n, &[n, n]);
                }
            })
        };
        let mut a = [0u32; W];
        let mut b = [0u32; W];
        if snapshot(&r.copies, &r.published, &mut a, 2) && snapshot(&r.copies, &r.published, &mut b, 2) {
            assert!(b[0] >= a[0], "went backwards: {:?} then {:?}", a, b);
            assert_eq!(a[0], a[1]);
            assert_eq!(b[0], b[1]);
        }
        w.join().unwrap();
    });
}

/// A journal entry is visible once a reader has acquired a count that includes it; an entry that
/// was overwritten by a later lap is reported missing, never misread.
#[test]
fn journal_entries_are_visible_once_counted_and_laps_are_detected() {
    loom::model(|| {
        let ring = Arc::new([L64::new(0), L64::new(0)]);
        let count = Arc::new(L32::new(0));
        let w = {
            let ring = ring.clone();
            let count = count.clone();
            thread::spawn(move || {
                for i in 1..=3u32 {
                    journal_put(&ring[..], i, i * 10);
                    count.st(i, Release);
                }
            })
        };
        let n = count.ld(Acquire);
        if n >= 1 {
            // the newest counted entry is either there or already overwritten by entry n + 2
            let tag = (ring[(n as usize) % 2].ld(Acquire) >> 32) as u32;
            assert!(tag == n || tag == n + 2, "stale entry {} for count {}", tag, n);
            for i in 1..=n {
                if let Some(p) = journal_get(&ring[..], i) {
                    assert_eq!(p, i * 10, "entry {} misread", i);
                }
            }
        }
        w.join().unwrap();
    });
}

/// The control block's fence-based seqlock: a reader racing two updates either gets a whole record
/// with the gen that wrote it, or gives up (and keeps its last good copy); never a mix of two
/// records. Once the writer is done the reader sees the last record.
#[test]
fn control_record_is_never_torn() {
    loom::model(|| {
        struct Ctl {
            seq: L32,
            gen: L32,
            words: [L32; 3],
        }
        let c = Arc::new(Ctl { seq: L32::new(0), gen: L32::new(0), words: [L32::new(0), L32::new(0), L32::new(0)] });
        let w = {
            let c = c.clone();
            thread::spawn(move || {
                for n in 1..=2u32 {
                    let g = ctl_write::<_, LoomFence>(&c.seq, &c.gen, &c.words, &[n, n * 10, n * 100]);
                    assert_eq!(g, n);
                }
            })
        };
        let mut out = [0u32; 3];
        if let Some(g) = ctl_read::<_, LoomFence>(&c.seq, &c.gen, &c.words, &mut out, 2, |_| thread::yield_now()) {
            assert_eq!(out, [g, g * 10, g * 100], "torn control record (gen {})", g);
        }
        w.join().unwrap();
        let g = ctl_read::<_, LoomFence>(&c.seq, &c.gen, &c.words, &mut out, 1, |_| {});
        assert_eq!(g, Some(2), "quiescent read must succeed");
        assert_eq!(out, [2, 20, 200]);
    });
}
