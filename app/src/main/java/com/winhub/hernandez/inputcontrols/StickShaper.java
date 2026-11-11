package com.winhub.hernandez.inputcontrols;

/**
 * Deadzone + sensitivity for the physical gamepad sticks. Pure maths, no Android classes, so it is
 * unit-testable and cheap enough to run for every input sample.
 *
 * <p>Two edge styles:
 * <ul>
 *   <li>Classic (default): per axis, exactly the old hard cut ("below the deadzone is 0, otherwise the
 *       value passes"), now with a configurable deadzone. At 15% / 1.00x the output is identical to
 *       what the fixed {@link ControlElement#STICK_DEAD_ZONE} gate produced.</li>
 *   <li>Smooth: radial (both axes together) and rescaled so the output starts at 0 just past the
 *       deadzone and still reaches 1.0 at full push. No jump, no square dead area.</li>
 * </ul>
 * Sensitivity is a response curve (output = input^(1/sensitivity)), not a multiplier, so 0 stays 0
 * and full push stays exactly 1.0. Above 1.00x the stick reacts more for a small push, below it the
 * stick is finer.
 *
 * <p>The tuning in effect is one process-wide immutable snapshot, set when a game session starts.
 * ExternalController instances are created in many places (profiles, Steam pads, binding screens),
 * so per-instance settings would not reach them all.
 */
public final class StickShaper {
    // Extra keys, shared by Container, shortcuts, the editors and the config exporters.
    public static final String KEY_LEFT_DEADZONE = "stickLDeadzone";
    public static final String KEY_LEFT_SENSITIVITY = "stickLSensitivity";
    public static final String KEY_RIGHT_DEADZONE = "stickRDeadzone";
    public static final String KEY_RIGHT_SENSITIVITY = "stickRSensitivity";
    public static final String KEY_SMOOTH_EDGE = "stickEdgeStyle";

    public static final float DEADZONE_DEFAULT = ControlElement.STICK_DEAD_ZONE;
    public static final float DEADZONE_MIN = 0.0f;
    // Never 100%: full push must always be reachable.
    public static final float DEADZONE_MAX = 0.5f;
    public static final float SENSITIVITY_DEFAULT = 1.0f;
    public static final float SENSITIVITY_MIN = 0.5f;
    public static final float SENSITIVITY_MAX = 2.0f;
    public static final boolean SMOOTH_EDGE_DEFAULT = false;

    private StickShaper() {}

    /** Immutable, so a reader always sees a consistent set of values. */
    public static final class Tuning {
        public final float leftDeadzone;
        public final float leftSensitivity;
        public final float rightDeadzone;
        public final float rightSensitivity;
        public final boolean smoothEdge;

        public Tuning(float leftDeadzone, float leftSensitivity,
                      float rightDeadzone, float rightSensitivity, boolean smoothEdge) {
            this.leftDeadzone = clampDeadzone(leftDeadzone);
            this.leftSensitivity = clampSensitivity(leftSensitivity);
            this.rightDeadzone = clampDeadzone(rightDeadzone);
            this.rightSensitivity = clampSensitivity(rightSensitivity);
            this.smoothEdge = smoothEdge;
        }
    }

    public static final Tuning DEFAULT = new Tuning(
            DEADZONE_DEFAULT, SENSITIVITY_DEFAULT, DEADZONE_DEFAULT, SENSITIVITY_DEFAULT, SMOOTH_EDGE_DEFAULT);

    private static volatile Tuning active = DEFAULT;

    public static Tuning getActive() {
        return active;
    }

    public static void setActive(Tuning tuning) {
        active = tuning != null ? tuning : DEFAULT;
    }

    public static void reset() {
        active = DEFAULT;
    }

    public static float clampDeadzone(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return DEADZONE_DEFAULT;
        return Math.min(DEADZONE_MAX, Math.max(DEADZONE_MIN, v));
    }

    public static float clampSensitivity(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return SENSITIVITY_DEFAULT;
        return Math.min(SENSITIVITY_MAX, Math.max(SENSITIVITY_MIN, v));
    }

    /** Parses an extra; missing, empty or unparsable text gives {@code fallback}. */
    public static float parseDeadzone(String text, float fallback) {
        if (text == null || text.isEmpty()) return fallback;
        try {
            return clampDeadzone(Float.parseFloat(text));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static float parseSensitivity(String text, float fallback) {
        if (text == null || text.isEmpty()) return fallback;
        try {
            return clampSensitivity(Float.parseFloat(text));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean parseSmoothEdge(String text, boolean fallback) {
        if ("1".equals(text)) return true;
        if ("0".equals(text)) return false;
        return fallback;
    }

    /**
     * Shapes one stick. {@code out} receives the shaped (x, y); it must have length 2. Non-finite
     * input gives (0, 0). {@code deadzone} and {@code sensitivity} are clamped to their ranges.
     */
    public static void shape(float x, float y, float deadzone, float sensitivity,
                             boolean smoothEdge, float[] out) {
        if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)) {
            out[0] = 0f;
            out[1] = 0f;
            return;
        }
        float dz = clampDeadzone(deadzone);
        float sens = clampSensitivity(sensitivity);

        if (!smoothEdge) {
            out[0] = classicAxis(x, dz, sens);
            out[1] = classicAxis(y, dz, sens);
            return;
        }

        float m = (float) Math.sqrt(x * x + y * y);
        if (m < dz || m == 0f) {
            out[0] = 0f;
            out[1] = 0f;
            return;
        }
        float t = (m - dz) / (1f - dz);
        if (t > 1f) t = 1f;
        if (sens != 1f) t = (float) Math.pow(t, 1.0 / sens);
        float k = t / m;
        out[0] = x * k;
        out[1] = y * k;
    }

    private static float classicAxis(float v, float dz, float sens) {
        float a = Math.abs(v);
        if (a < dz) return 0f;
        if (sens == 1f) return v;
        if (a > 1f) a = 1f;
        a = (float) Math.pow(a, 1.0 / sens);
        return v < 0f ? -a : a;
    }
}
