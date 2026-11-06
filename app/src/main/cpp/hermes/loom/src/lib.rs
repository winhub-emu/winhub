//! loom adapters for the generic protocol in `hermes_core::proto` (see tests/protocol.rs).

use hermes_core::proto::{Fence, Word32, Word64};
use loom::sync::atomic::{AtomicU32, AtomicU64};
use std::sync::atomic::Ordering;

pub struct L32(pub AtomicU32);
pub struct L64(pub AtomicU64);

impl L32 {
    pub fn new(v: u32) -> L32 {
        L32(AtomicU32::new(v))
    }
}

impl L64 {
    pub fn new(v: u64) -> L64 {
        L64(AtomicU64::new(v))
    }
}

impl Word32 for L32 {
    fn ld(&self, o: Ordering) -> u32 {
        self.0.load(o)
    }
    fn st(&self, v: u32, o: Ordering) {
        self.0.store(v, o)
    }
}

impl Word64 for L64 {
    fn ld(&self, o: Ordering) -> u64 {
        self.0.load(o)
    }
    fn st(&self, v: u64, o: Ordering) {
        self.0.store(v, o)
    }
}

/// loom's fence, so the control seqlock's explicit fences are part of the model.
pub struct LoomFence;

impl Fence for LoomFence {
    fn fence(o: Ordering) {
        loom::sync::atomic::fence(o)
    }
}
