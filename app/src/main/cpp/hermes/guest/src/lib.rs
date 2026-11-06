//! libhermes_guest.a: the C ABI declared in include/hermes.h, over `hermes_core::guest`.
//!
//! no_std, panic = abort, no allocation. Only the `hermes_*` functions below are meant to be
//! called; the archive is linked with `--exclude-libs` so none of its symbols is exported from the
//! interposer.
#![cfg_attr(not(test), no_std)]
#![allow(clippy::missing_safety_doc)]

use core::ffi::{c_int, c_short, c_void};

use hermes_core::abi::{AXES, BUTTONS};
use hermes_core::ff::FfEffect;
use hermes_core::guest::{self, IdentityView, LogFn, SlotStatsView, StatsView};
use hermes_core::sys;

/// `struct hermes_state`.
#[repr(C)]
pub struct HermesState {
    pub buttons: [i32; BUTTONS],
    pub axes: [i32; AXES],
}

#[inline]
fn neg(e: i32) -> c_int {
    -e
}

#[no_mangle]
pub extern "C" fn hermes_init() -> c_int {
    guest::init() as c_int
}

#[no_mangle]
pub unsafe extern "C" fn hermes_attach(slot: c_int, open_flags: c_int, out_handle: *mut u32) -> c_int {
    if out_handle.is_null() {
        return neg(sys::EINVAL);
    }
    match guest::attach(slot, open_flags) {
        Ok((fd, h)) => {
            *out_handle = h;
            fd
        }
        Err(e) => neg(e),
    }
}

#[no_mangle]
pub extern "C" fn hermes_detach(h: u32) -> c_int {
    match guest::detach(h) {
        Ok(()) => 0,
        Err(e) => neg(e),
    }
}

#[no_mangle]
pub extern "C" fn hermes_fd(h: u32) -> c_int {
    guest::fd(h).unwrap_or_else(neg)
}

#[no_mangle]
pub extern "C" fn hermes_slot(h: u32) -> c_int {
    guest::slot(h).unwrap_or_else(neg)
}

#[no_mangle]
pub extern "C" fn hermes_is_removed(h: u32) -> c_int {
    match guest::is_removed(h) {
        Ok(r) => r as c_int,
        Err(e) => neg(e),
    }
}

#[no_mangle]
pub extern "C" fn hermes_has_pending(h: u32) -> c_int {
    match guest::has_pending(h) {
        Ok(p) => p as c_int,
        Err(e) => neg(e),
    }
}

#[no_mangle]
pub extern "C" fn hermes_poll_revents(h: u32, events: c_short) -> c_short {
    guest::poll_revents(h, events)
}

#[no_mangle]
pub unsafe extern "C" fn hermes_read(h: u32, buf: *mut c_void, len: usize, nonblocking: c_int) -> isize {
    match guest::read(h, buf as *mut u8, len, nonblocking != 0) {
        Ok(n) => n as isize,
        Err(e) => -(e as isize),
    }
}

#[no_mangle]
pub unsafe extern "C" fn hermes_current_state(h: u32, out: *mut HermesState) -> c_int {
    if out.is_null() {
        return neg(sys::EINVAL);
    }
    let mut b = [0i32; BUTTONS];
    let mut a = [0i32; AXES];
    match guest::current_state(h, &mut b, &mut a) {
        Ok(()) => {
            (*out).buttons = b;
            (*out).axes = a;
            0
        }
        Err(e) => neg(e),
    }
}

unsafe fn copy_out<T>(v: &T, out: *mut T, size: usize) {
    let n = size.min(core::mem::size_of::<T>());
    core::ptr::copy_nonoverlapping(v as *const T as *const u8, out as *mut u8, n);
}

#[no_mangle]
pub unsafe extern "C" fn hermes_get_stats(out: *mut StatsView, size: usize) -> c_int {
    if out.is_null() {
        return neg(sys::EINVAL);
    }
    copy_out(&guest::stats(), out, size);
    0
}

#[no_mangle]
pub unsafe extern "C" fn hermes_slot_stats(slot: c_int, out: *mut SlotStatsView, size: usize) -> c_int {
    if out.is_null() {
        return neg(sys::EINVAL);
    }
    match guest::slot_stats(slot) {
        Ok(v) => {
            copy_out(&v, out, size);
            0
        }
        Err(e) => neg(e),
    }
}

/// `int hermes_identity(hermes_handle h, struct hermes_identity *out, size_t size)`
#[no_mangle]
pub unsafe extern "C" fn hermes_identity(h: u32, out: *mut IdentityView, size: usize) -> c_int {
    if out.is_null() {
        return neg(sys::EINVAL);
    }
    match guest::identity(h) {
        Ok(v) => {
            copy_out(&v, out, size);
            0
        }
        Err(e) => neg(e),
    }
}

/// `int hermes_slot_identity(int slot, struct hermes_identity *out, size_t size)`
#[no_mangle]
pub unsafe extern "C" fn hermes_slot_identity(slot: c_int, out: *mut IdentityView, size: usize) -> c_int {
    if out.is_null() {
        return neg(sys::EINVAL);
    }
    match guest::slot_identity(slot) {
        Ok(v) => {
            copy_out(&v, out, size);
            0
        }
        Err(e) => neg(e),
    }
}

#[no_mangle]
pub extern "C" fn hermes_set_logger(f: Option<LogFn>) {
    guest::set_logger(f)
}

#[no_mangle]
pub extern "C" fn hermes_api_version() -> u32 {
    guest::API_VERSION
}

#[no_mangle]
pub extern "C" fn hermes_axis_hz() -> u32 {
    guest::axis_hz()
}

#[no_mangle]
pub extern "C" fn hermes_axis_auto() -> c_int {
    (guest::axis_config() == guest::AXIS_AUTO) as c_int
}

/// `int hermes_ff_bits(hermes_handle h, uint8_t *bits, size_t len)`
#[no_mangle]
pub unsafe extern "C" fn hermes_ff_bits(h: u32, bits: *mut u8, len: usize) -> c_int {
    let b = match guest::ff_bits(h) {
        Ok(b) => b,
        Err(e) => return neg(e),
    };
    let n = len.min(b.len());
    if n > 0 {
        if bits.is_null() {
            return neg(sys::EINVAL);
        }
        core::ptr::copy_nonoverlapping(b.as_ptr(), bits, n);
    }
    n as c_int
}

/// `int hermes_ff_upload(hermes_handle h, struct hermes_ff_effect *e, size_t size)`
#[no_mangle]
pub unsafe extern "C" fn hermes_ff_upload(h: u32, e: *mut FfEffect, size: usize) -> c_int {
    if e.is_null() || size < core::mem::size_of::<FfEffect>() {
        return neg(sys::EINVAL);
    }
    let mut v = core::ptr::read_unaligned(e);
    match guest::ff_upload(h, &mut v) {
        Ok(()) => {
            core::ptr::write_unaligned(core::ptr::addr_of_mut!((*e).id), v.id);
            0
        }
        Err(err) => neg(err),
    }
}

/// `int hermes_ff_erase(hermes_handle h, int id)`
#[no_mangle]
pub extern "C" fn hermes_ff_erase(h: u32, id: c_int) -> c_int {
    match guest::ff_erase(h, id) {
        Ok(()) => 0,
        Err(e) => neg(e),
    }
}

/// `int hermes_ff_play(hermes_handle h, int id, int32_t count)`
#[no_mangle]
pub extern "C" fn hermes_ff_play(h: u32, id: c_int, count: i32) -> c_int {
    match guest::ff_play(h, id, count) {
        Ok(()) => 0,
        Err(e) => neg(e),
    }
}

/// `int hermes_ff_set_gain(hermes_handle h, uint16_t gain)`
#[no_mangle]
pub extern "C" fn hermes_ff_set_gain(h: u32, gain: u16) -> c_int {
    match guest::ff_set_gain(h, gain) {
        Ok(()) => 0,
        Err(e) => neg(e),
    }
}

/// `int hermes_ff_set_autocenter(hermes_handle h, uint16_t value)`
#[no_mangle]
pub extern "C" fn hermes_ff_set_autocenter(h: u32, value: u16) -> c_int {
    match guest::ff_set_autocenter(h, value) {
        Ok(()) => 0,
        Err(e) => neg(e),
    }
}

#[cfg(not(test))]
#[panic_handler]
fn panic(_info: &core::panic::PanicInfo) -> ! {
    sys::abort()
}

// Never called (panic = abort); defined so an unwind table left in a precompiled `core` object
// can never turn into an undefined symbol when the archive is linked.
#[cfg(not(test))]
#[no_mangle]
pub extern "C" fn rust_eh_personality() {}

#[cfg(test)]
mod tests;
