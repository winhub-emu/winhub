//! Conversion of the Java `GamepadState` fields into the canonical pad state.

use crate::abi::*;

/// Canonical button index -> bit in `GamepadState.buttons` (`ExternalController.IDX_BUTTON_*`).
/// The digital triggers (L2 = 10, R2 = 11) are deliberately absent: the analog triggers carry them.
pub const JAVA_BUTTON_BITS: [u8; BUTTONS] = [
    0,  // A
    1,  // B
    2,  // X
    3,  // Y
    4,  // L1 -> BTN_TL
    5,  // R1 -> BTN_TR
    6,  // SELECT
    7,  // START
    12, // MODE
    8,  // L3 -> BTN_THUMBL
    9,  // R3 -> BTN_THUMBR
];

/// `dpad` bits as packed by the Java writer: up, right, down, left (the `GamepadState.dpad` order).
pub const DPAD_UP: i32 = 1;
pub const DPAD_RIGHT: i32 = 2;
pub const DPAD_DOWN: i32 = 4;
pub const DPAD_LEFT: i32 = 8;

/// The overlay bits of `setSystemButtons`.
pub const OVERLAY_GUIDE: i32 = 1;
pub const OVERLAY_A: i32 = 2;

/// One complete pad state in canonical form.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct PadInput {
    /// Pressed buttons, bit = canonical index.
    pub buttons: u16,
    /// Axis values in canonical order, already inside the advertised ranges.
    pub axes: [i32; AXES],
}

impl PadInput {
    pub const NEUTRAL: PadInput = PadInput { buttons: 0, axes: [0; AXES] };

    /// Builds the canonical state from the raw `GamepadState` fields.
    #[allow(clippy::too_many_arguments)]
    pub fn from_java(
        buttons: i32,
        lx: f32,
        ly: f32,
        rx: f32,
        ry: f32,
        lt: f32,
        rt: f32,
        dpad: i32,
    ) -> PadInput {
        PadInput {
            buttons: buttons_from_java(buttons),
            axes: [
                stick(lx),
                stick(ly),
                stick(rx),
                stick(ry),
                trigger(lt),
                trigger(rt),
                hat(dpad & DPAD_LEFT != 0, dpad & DPAD_RIGHT != 0),
                hat(dpad & DPAD_UP != 0, dpad & DPAD_DOWN != 0),
            ],
        }
    }
}

/// `GamepadState.buttons` -> canonical mask.
pub fn buttons_from_java(mask: i32) -> u16 {
    let mut out = 0u16;
    for (i, bit) in JAVA_BUTTON_BITS.iter().enumerate() {
        if (mask >> *bit) & 1 != 0 {
            out |= 1 << i;
        }
    }
    out
}

/// Overlay bits (`OVERLAY_GUIDE` / `OVERLAY_A`) -> canonical mask.
pub fn overlay_from_bits(bits: i32) -> u16 {
    let mut out = 0u16;
    if bits & OVERLAY_GUIDE != 0 {
        out |= 1 << B_MODE;
    }
    if bits & OVERLAY_A != 0 {
        out |= 1 << B_A;
    }
    out
}

/// Stick float in [-1, 1] -> `(int)(v * 32767)`, truncating toward zero like the Java cast.
pub fn stick(v: f32) -> i32 {
    if v.is_nan() {
        return 0;
    }
    let v = v.clamp(-1.0, 1.0);
    clamp_axis(A_LX, (v * 32767.0) as i32)
}

/// Trigger float in [0, 1] -> `(int)(v * 255)`.
pub fn trigger(v: f32) -> i32 {
    if v.is_nan() {
        return 0;
    }
    let v = v.clamp(0.0, 1.0);
    clamp_axis(A_LT, (v * 255.0) as i32)
}

/// D-pad axis from its two opposite directions: -1, +1, or 0 when neither or both are held.
pub fn hat(negative: bool, positive: bool) -> i32 {
    match (negative, positive) {
        (true, false) => -1,
        (false, true) => 1,
        _ => 0,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_bits_map_to_canonical_and_drop_digital_triggers() {
        assert_eq!(buttons_from_java(1 << 0), 1 << B_A);
        assert_eq!(buttons_from_java(1 << 12), 1 << B_MODE);
        assert_eq!(buttons_from_java(1 << 8), 1 << B_THUMBL);
        assert_eq!(buttons_from_java(1 << 9), 1 << B_THUMBR);
        assert_eq!(buttons_from_java((1 << 10) | (1 << 11)), 0, "L2/R2 bits are carried by the analog triggers");
        assert_eq!(buttons_from_java(0x1fff), ALL_BUTTONS);
    }

    #[test]
    fn scaling_matches_java_casts_and_stays_in_range() {
        assert_eq!(stick(1.0), 32767);
        assert_eq!(stick(-1.0), -32767);
        assert_eq!(stick(0.5), 16383);
        assert_eq!(stick(-0.5), -16383);
        assert_eq!(stick(3.0), 32767);
        assert_eq!(stick(f32::NAN), 0);
        assert_eq!(trigger(1.0), 255);
        assert_eq!(trigger(0.5), 127);
        assert_eq!(trigger(-1.0), 0);
        assert_eq!(trigger(9.0), 255);
    }

    #[test]
    fn dpad_conflicts_are_neutral() {
        let p = PadInput::from_java(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, DPAD_LEFT | DPAD_RIGHT | DPAD_UP | DPAD_DOWN);
        assert_eq!(p.axes[A_HATX], 0);
        assert_eq!(p.axes[A_HATY], 0);
        let p = PadInput::from_java(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, DPAD_LEFT | DPAD_UP);
        assert_eq!((p.axes[A_HATX], p.axes[A_HATY]), (-1, -1));
        let p = PadInput::from_java(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, DPAD_RIGHT | DPAD_DOWN);
        assert_eq!((p.axes[A_HATX], p.axes[A_HATY]), (1, 1));
    }

    #[test]
    fn overlay_bits() {
        assert_eq!(overlay_from_bits(OVERLAY_GUIDE), 1 << B_MODE);
        assert_eq!(overlay_from_bits(OVERLAY_A), 1 << B_A);
        assert_eq!(overlay_from_bits(3), (1 << B_MODE) | (1 << B_A));
    }
}
