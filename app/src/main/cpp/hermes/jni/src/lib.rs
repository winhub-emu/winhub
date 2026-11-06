//! libhermes.so: native side of com.winhub.hernandez.inputcontrols.HermesWriter.
//!
//! One `SlotWriter` per slot, process-wide (it outlives the Java writer objects, which come and
//! go with connects, so state and edge counters survive reconnects and sessions), each behind its
//! own mutex because several Java writer objects can address the same slot. Every entry point
//! takes primitives only; the publish path does no allocation, no JNI callbacks and no file I/O.

use std::ffi::{c_char, c_void};
use std::sync::{Mutex, MutexGuard};

use hermes_core::layout::{Identity, ID_KEY_LEN, ID_NAME_LEN};
use hermes_core::pad::{overlay_from_bits, PadInput};
use hermes_core::writer::SlotWriter;

type JEnv = *mut *const *const c_void;
type JClass = *mut c_void;
type JString = *mut c_void;
type JBoolean = u8;
type JInt = i32;
type JLong = i64;
type JFloat = f32;

// Indices into the JNINativeInterface function table (stable JNI ABI).
const JNI_GET_STRING_LENGTH: usize = 164;
const JNI_GET_STRING_UTF_CHARS: usize = 169;
const JNI_RELEASE_STRING_UTF_CHARS: usize = 170;
const JNI_GET_STRING_REGION: usize = 220;

static SLOTS: [Mutex<SlotWriter>; 4] = [
    Mutex::new(SlotWriter::new(0)),
    Mutex::new(SlotWriter::new(1)),
    Mutex::new(SlotWriter::new(2)),
    Mutex::new(SlotWriter::new(3)),
];

fn slot(slot: JInt) -> Option<MutexGuard<'static, SlotWriter>> {
    let m = SLOTS.get(usize::try_from(slot).ok()?)?;
    Some(m.lock().unwrap_or_else(|p| p.into_inner()))
}

/// Copies a Java string (modified UTF-8) into `out`; None for null or over-long strings.
unsafe fn jstring(env: JEnv, s: JString, out: &mut [u8; 1024]) -> Option<usize> {
    if env.is_null() || s.is_null() {
        return None;
    }
    let fns = *env;
    let get: unsafe extern "system" fn(JEnv, JString, *mut JBoolean) -> *const c_char =
        std::mem::transmute(*fns.add(JNI_GET_STRING_UTF_CHARS));
    let rel: unsafe extern "system" fn(JEnv, JString, *const c_char) =
        std::mem::transmute(*fns.add(JNI_RELEASE_STRING_UTF_CHARS));
    let p = get(env, s, std::ptr::null_mut());
    if p.is_null() {
        return None;
    }
    let mut n = 0usize;
    while n < out.len() - 1 && *p.add(n) != 0 {
        out[n] = *p.add(n) as u8;
        n += 1;
    }
    let complete = *p.add(n) == 0;
    rel(env, s, p);
    if complete && n > 0 {
        Some(n)
    } else {
        None
    }
}

/// UTF-16 to UTF-8, whole characters only, as many as fit in `out` (a lone surrogate becomes
/// U+FFFD). Returns the bytes written.
fn utf16_to_utf8(src: &[u16], out: &mut [u8]) -> usize {
    let mut n = 0;
    let mut i = 0;
    while i < src.len() {
        let u = src[i] as u32;
        i += 1;
        let c = if (0xD800..0xDC00).contains(&u) && i < src.len() && (0xDC00..0xE000).contains(&(src[i] as u32)) {
            let lo = src[i] as u32;
            i += 1;
            0x10000 + ((u - 0xD800) << 10) + (lo - 0xDC00)
        } else if (0xD800..0xE000).contains(&u) {
            0xFFFD
        } else {
            u
        };
        let mut b = [0u8; 4];
        let len = match c {
            0..=0x7F => {
                b[0] = c as u8;
                1
            }
            0x80..=0x7FF => {
                b[0] = 0xC0 | (c >> 6) as u8;
                b[1] = 0x80 | (c & 0x3F) as u8;
                2
            }
            0x800..=0xFFFF => {
                b[0] = 0xE0 | (c >> 12) as u8;
                b[1] = 0x80 | ((c >> 6) & 0x3F) as u8;
                b[2] = 0x80 | (c & 0x3F) as u8;
                3
            }
            _ => {
                b[0] = 0xF0 | (c >> 18) as u8;
                b[1] = 0x80 | ((c >> 12) & 0x3F) as u8;
                b[2] = 0x80 | ((c >> 6) & 0x3F) as u8;
                b[3] = 0x80 | (c & 0x3F) as u8;
                4
            }
        };
        if c == 0 || n + len > out.len() {
            break;
        }
        out[n..n + len].copy_from_slice(&b[..len]);
        n += len;
    }
    n
}

/// A Java string as UTF-8 (proper UTF-8, not JNI's modified form), cut to whole characters that
/// fit in `out`. Null is the empty string. Reads at most out.len() UTF-16 units; no allocation.
unsafe fn jstring_utf8<const N: usize>(env: JEnv, s: JString, out: &mut [u8; N]) -> usize {
    if env.is_null() || s.is_null() {
        return 0;
    }
    let fns = *env;
    let len: unsafe extern "system" fn(JEnv, JString) -> JInt = std::mem::transmute(*fns.add(JNI_GET_STRING_LENGTH));
    let region: unsafe extern "system" fn(JEnv, JString, JInt, JInt, *mut u16) =
        std::mem::transmute(*fns.add(JNI_GET_STRING_REGION));
    let mut units = [0u16; 128];
    let n = (len(env, s).max(0) as usize).min(units.len()).min(N);
    region(env, s, 0, n as JInt, units.as_mut_ptr());
    utf16_to_utf8(&units[..n], out)
}

/// An identity key: printable ASCII only (anything else becomes '_').
fn ascii_key(b: &[u8]) -> [u8; ID_KEY_LEN] {
    let mut k = [0u8; ID_KEY_LEN];
    let mut n = 0;
    let mut i = 0;
    while i < b.len() && n < ID_KEY_LEN - 1 {
        let c = b[i];
        // skip a whole multi-byte character as one '_'
        let w = match c {
            0..=0x7F => 1,
            0xC0..=0xDF => 2,
            0xE0..=0xEF => 3,
            _ => 4,
        };
        k[n] = if (0x21..=0x7E).contains(&c) { c } else { b'_' };
        n += 1;
        i += w;
    }
    k
}

fn jbool(b: bool) -> JBoolean {
    b as JBoolean
}

/// `static native boolean nativePrepare(String backingDir, int slot)`
#[no_mangle]
pub unsafe extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativePrepare(
    env: JEnv,
    _cls: JClass,
    dir: JString,
    s: JInt,
) -> JBoolean {
    let mut buf = [0u8; 1024];
    let n = match jstring(env, dir, &mut buf) {
        Some(n) => n,
        None => return 0,
    };
    match slot(s) {
        Some(mut w) => jbool(w.prepare(&buf[..n]).is_ok()),
        None => 0,
    }
}

/// `static native void nativeRelease(int slot)`
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeRelease(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
) {
    if let Some(mut w) = slot(s) {
        w.release();
    }
}

/// `static native boolean nativeOpen(int slot, String backingDir)`: the slot has a controller.
/// Creates the backing first if it does not exist yet (backingDir may be null).
#[no_mangle]
pub unsafe extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeOpen(
    env: JEnv,
    _cls: JClass,
    s: JInt,
    dir: JString,
) -> JBoolean {
    let mut buf = [0u8; 1024];
    let n = jstring(env, dir, &mut buf);
    match slot(s) {
        Some(mut w) => {
            if !w.has_backing() {
                if let Some(n) = n {
                    let _ = w.prepare(&buf[..n]);
                }
            }
            w.open();
            1
        }
        None => 0,
    }
}

/// `static native boolean nativePublish(int slot, int buttons, float lx, float ly, float rx,
/// float ry, float lt, float rt, int dpad)`: the pad's complete state. False if it could not be
/// made visible to readers (the state is kept and published by the next successful call).
#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativePublish(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
    buttons: JInt,
    lx: JFloat,
    ly: JFloat,
    rx: JFloat,
    ry: JFloat,
    lt: JFloat,
    rt: JFloat,
    dpad: JInt,
) -> JBoolean {
    match slot(s) {
        Some(mut w) => jbool(w.set_pad(PadInput::from_java(buttons, lx, ly, rx, ry, lt, rt, dpad))),
        None => 0,
    }
}

/// `static native boolean nativeSetOverlay(int slot, int bits)`: bit 0 Guide, bit 1 A.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetOverlay(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
    bits: JInt,
) -> JBoolean {
    match slot(s) {
        Some(mut w) => jbool(w.set_overlay(overlay_from_bits(bits))),
        None => 0,
    }
}

/// `static native boolean nativeReset(int slot)`: neutral, still connected.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeReset(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
) -> JBoolean {
    match slot(s) {
        Some(mut w) => jbool(w.reset()),
        None => 0,
    }
}

/// `static native boolean nativeUnplug(int slot)`: neutralise, then removal for every reader.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeUnplug(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
) -> JBoolean {
    match slot(s) {
        Some(mut w) => jbool(w.unplug()),
        None => 0,
    }
}

/// `static native void nativeGameFrame()`: the game presented a frame (called from the app's
/// present threads). Wait-free: the frame-interval estimator and a few atomic stores into the
/// live slot files; no allocation, no lock, no JNI callback.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGameFrame(
    _env: JEnv,
    _cls: JClass,
) {
    hermes_core::writer::game_frame(hermes_core::sys::mono_ns());
}

/// `static native boolean nativeSetSlotIdentity(int slot, int bustype, int vendor, int product,
/// int version, String key, String name)`: the controller identity readers of this slot report
/// from their next attach on (ids are cut to 16 bits; key: ASCII, at most 15 characters; name:
/// UTF-8, at most 63 bytes, cut at a character boundary). Stored for files created later too.
/// Must have returned before the slot's discovery node is created. False for a bad slot.
#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub unsafe extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetSlotIdentity(
    env: JEnv,
    _cls: JClass,
    s: JInt,
    bustype: JInt,
    vendor: JInt,
    product: JInt,
    version: JInt,
    key: JString,
    name: JString,
) -> JBoolean {
    let mut kb = [0u8; ID_KEY_LEN * 4];
    let kn = jstring_utf8(env, key, &mut kb);
    let mut nb = [0u8; ID_NAME_LEN];
    let nn = jstring_utf8(env, name, &mut nb);
    let k = ascii_key(&kb[..kn]);
    let id = Identity::new(bustype as u16, vendor as u16, product as u16, version as u16, &k, &nb[..nn]);
    match slot(s) {
        Some(mut w) => {
            w.set_identity(Some(id));
            1
        }
        None => 0,
    }
}

/// `static native boolean nativeClearSlotIdentity(int slot)`: no identity (readers use their own
/// default from their next attach on). False for a bad slot.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeClearSlotIdentity(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
) -> JBoolean {
    match slot(s) {
        Some(mut w) => {
            w.set_identity(None);
            1
        }
        None => 0,
    }
}

/// `static native void nativeSetSessionControl(int axisMode, int axisHz, int autoMax,
/// int xinputMode, int xinputFlags)`: session settings, written into all four slots (live files
/// now, later files when they are created). Readers apply pacing at their next decision.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetSessionControl(
    _env: JEnv,
    _cls: JClass,
    axis_mode: JInt,
    axis_hz: JInt,
    auto_max: JInt,
    xinput_mode: JInt,
    xinput_flags: JInt,
) {
    let u = |v: JInt| v.max(0) as u32;
    for i in 0..SLOTS.len() as JInt {
        if let Some(mut w) = slot(i) {
            w.set_session(u(axis_mode), u(axis_hz), u(auto_max), u(xinput_mode), xinput_flags as u32);
        }
    }
}

/// `static native void nativeSetFfMode(int mode)`: DirectInput force feedback for all four slots
/// (0 unset = on, 1 on, 2 off: rumble effects only; anything else = unset), written into the live
/// files now and into later files when they are created. A guest handle reads it when it
/// attaches; HERMES_DINPUT_FF in the guest overrides it.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetFfMode(
    _env: JEnv,
    _cls: JClass,
    mode: JInt,
) {
    for i in 0..SLOTS.len() as JInt {
        if let Some(mut w) = slot(i) {
            w.set_ff_mode(mode.max(0) as u32);
        }
    }
}

/// `static native long nativeGetControlGen(int slot)`: the slot's control gen (bumped by every
/// change to its control record), 0 for a bad slot.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
) -> JLong {
    match slot(s) {
        Some(w) => w.control().0 as JLong,
        None => 0,
    }
}

/// `static native long nativeStat(int slot, int which)`: see `hermes_core::writer::STAT_*`.
#[no_mangle]
pub extern "system" fn Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeStat(
    _env: JEnv,
    _cls: JClass,
    s: JInt,
    which: JInt,
) -> JLong {
    match slot(s) {
        Some(w) => w.stat(which as u32) as JLong,
        None => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bad_slots_are_refused() {
        assert!(slot(-1).is_none());
        assert!(slot(4).is_none());
        assert!(slot(0).is_some());
        assert_eq!(Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeReset(std::ptr::null_mut(), std::ptr::null_mut(), 7), 0);
        assert_eq!(Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeStat(std::ptr::null_mut(), std::ptr::null_mut(), 9, 0), 0);
    }

    #[test]
    fn utf16_conversion_and_key_filter() {
        let enc = |s: &str| s.encode_utf16().collect::<Vec<u16>>();
        let mut out = [0u8; 64];
        let n = utf16_to_utf8(&enc("Xbox 360 Controller (0)"), &mut out);
        assert_eq!(&out[..n], b"Xbox 360 Controller (0)");
        let s = "Pad \u{e9}\u{4e2d}\u{1F3AE}";
        let n = utf16_to_utf8(&enc(s), &mut out);
        assert_eq!(&out[..n], s.as_bytes(), "BMP and supplementary characters as real UTF-8");
        let n = utf16_to_utf8(&[0x41, 0xD800, 0x42], &mut out);
        assert_eq!(&out[..n], "A\u{FFFD}B".as_bytes(), "lone surrogate");
        let mut small = [0u8; 5];
        let n = utf16_to_utf8(&enc("ab\u{4e2d}\u{4e2d}"), &mut small);
        assert_eq!(&small[..n], "ab\u{4e2d}".as_bytes(), "whole characters only");
        assert_eq!(Identity::text(&ascii_key(b"xbox360")), b"xbox360");
        assert_eq!(Identity::text(&ascii_key("a b\u{e9}c".as_bytes())), b"a_b_c");
    }

    #[test]
    fn control_entry_points_refuse_bad_slots_and_bump_gens() {
        fn z<T>() -> *mut T {
            std::ptr::null_mut()
        }
        assert_eq!(Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeClearSlotIdentity(z(), z(), 9), 0);
        assert_eq!(Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(z(), z(), -1), 0);
        let g0 = Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(z(), z(), 2);
        assert!(g0 >= 1);
        Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetSessionControl(z(), z(), 2, 30, 0, 1, 0);
        let g1 = Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(z(), z(), 2);
        assert_eq!(g1, g0 + 1);
        // the same settings again change nothing
        Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetSessionControl(z(), z(), 2, 30, 0, 1, 0);
        assert_eq!(Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(z(), z(), 2), g1);
        let (_, st) = slot(1).unwrap().control();
        assert_eq!((st.axis_mode, st.axis_hz, st.xinput_mode), (2, 30, 1), "session settings reach every slot");
    }

    #[test]
    fn the_ff_mode_reaches_every_slot_and_bumps_the_gen() {
        fn z<T>() -> *mut T {
            std::ptr::null_mut()
        }
        let gens: Vec<JLong> = (0..4).map(|s| Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(z(), z(), s)).collect();
        Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetFfMode(z(), z(), 2);
        for s in 0..4 {
            assert_eq!(Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(z(), z(), s), gens[s as usize] + 1);
            assert_eq!(slot(s).unwrap().control().1.ff_mode, 2);
        }
        Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetFfMode(z(), z(), -5);
        assert_eq!(slot(3).unwrap().control().1.ff_mode, 0, "out of range: unset");
        Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeSetFfMode(z(), z(), 0);
        assert_eq!(Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativeGetControlGen(z(), z(), 0), gens[0] + 2, "the same mode again changes nothing");
    }

    #[test]
    fn publish_without_backing_reports_failure_but_keeps_state() {
        let r = Java_com_winhub_hernandez_inputcontrols_HermesWriter_nativePublish(
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            3,
            1,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0,
        );
        assert_eq!(r, 0, "no backing has been prepared for slot 3 in this process");
        assert_eq!(slot(3).unwrap().buttons(), 1, "the state is kept for the next backing");
    }
}
