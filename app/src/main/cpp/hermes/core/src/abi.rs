//! evdev constants and the canonical button / axis order the transport carries.
//!
//! Buttons are indexed 0..11 in ascending evdev code order; that order is used for full-state
//! frames, for the change frames and for the journal. Axes use the order the change frames must
//! follow: X, Y, RX, RY, BRAKE (left trigger), GAS (right trigger), HAT0X, HAT0Y.

pub const EV_SYN: u16 = 0x00;
pub const EV_KEY: u16 = 0x01;
pub const EV_ABS: u16 = 0x03;
pub const EV_MSC: u16 = 0x04;
pub const SYN_REPORT: u16 = 0x00;
pub const MSC_SCAN: u16 = 0x04;

pub const BUTTONS: usize = 11;
pub const AXES: usize = 8;
pub const ALL_BUTTONS: u16 = (1 << BUTTONS) - 1;

pub const BTN_A: u16 = 0x130;
pub const BTN_B: u16 = 0x131;
pub const BTN_X: u16 = 0x133;
pub const BTN_Y: u16 = 0x134;
pub const BTN_TL: u16 = 0x136;
pub const BTN_TR: u16 = 0x137;
pub const BTN_SELECT: u16 = 0x13a;
pub const BTN_START: u16 = 0x13b;
pub const BTN_MODE: u16 = 0x13c;
pub const BTN_THUMBL: u16 = 0x13d;
pub const BTN_THUMBR: u16 = 0x13e;

/// Canonical button index -> evdev code.
pub const BUTTON_CODES: [u16; BUTTONS] = [
    BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR, BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL,
    BTN_THUMBR,
];

pub const B_A: usize = 0;
pub const B_B: usize = 1;
pub const B_X: usize = 2;
pub const B_Y: usize = 3;
pub const B_TL: usize = 4;
pub const B_TR: usize = 5;
pub const B_SELECT: usize = 6;
pub const B_START: usize = 7;
pub const B_MODE: usize = 8;
pub const B_THUMBL: usize = 9;
pub const B_THUMBR: usize = 10;

pub const ABS_X: u16 = 0x00;
pub const ABS_Y: u16 = 0x01;
pub const ABS_RX: u16 = 0x03;
pub const ABS_RY: u16 = 0x04;
pub const ABS_GAS: u16 = 0x09;
pub const ABS_BRAKE: u16 = 0x0a;
pub const ABS_HAT0X: u16 = 0x10;
pub const ABS_HAT0Y: u16 = 0x11;

/// Canonical axis index -> evdev code.
pub const AXIS_CODES: [u16; AXES] =
    [ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_BRAKE, ABS_GAS, ABS_HAT0X, ABS_HAT0Y];

pub const A_LX: usize = 0;
pub const A_LY: usize = 1;
pub const A_RX: usize = 2;
pub const A_RY: usize = 3;
pub const A_LT: usize = 4;
pub const A_RT: usize = 5;
pub const A_HATX: usize = 6;
pub const A_HATY: usize = 7;

/// The ranges the evdev layer advertises (EVIOCGABS). Nothing outside them is ever delivered.
pub const AXIS_MIN: [i32; AXES] = [-32768, -32768, -32768, -32768, 0, 0, -1, -1];
pub const AXIS_MAX: [i32; AXES] = [32767, 32767, 32767, 32767, 255, 255, 1, 1];

#[inline]
pub fn clamp_axis(i: usize, v: i32) -> i32 {
    if v < AXIS_MIN[i] {
        AXIS_MIN[i]
    } else if v > AXIS_MAX[i] {
        AXIS_MAX[i]
    } else {
        v
    }
}

/// Pressed-button mask from per-button edge counters: every button starts released, so an odd
/// number of edges means pressed.
#[inline]
pub fn parity(counts: &[u32; BUTTONS]) -> u16 {
    let mut m = 0u16;
    for b in 0..BUTTONS {
        m |= ((counts[b] & 1) as u16) << b;
    }
    m
}

/// Size of `struct input_event` on LP64 Linux (aarch64 and x86_64): timeval + u16 + u16 + s32.
pub const EVENT_SIZE: usize = 24;
