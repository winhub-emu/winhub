package com.winhub.hernandez.core;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;

import java.util.Locale;

/**
 * "Stick updates": how often stick-only frames reach the game (HERMES's reader pacing).
 * <ul>
 *   <li>{@code auto:N}: one stick frame per frame the game presents, at most N per second (20..240;
 *       the default is auto:60, the cap that is device-proven)</li>
 *   <li>{@code fixed:N}: N stick frames per second (30..1000), whatever the game does</li>
 *   <li>{@code off}: every change goes out at once (unpaced)</li>
 * </ul>
 * Per container ({@link #EXTRA} in extraData) with a per-game override of the same key. Applied
 * through HERMES's session control at launch and live from the in-game menu (no reconnect).
 * HERMES_AXIS_HZ / HERMES_AXIS_AUTO_MAX in the game's or the container's env vars win over this
 * setting, as they always did; the setting's rows say so and can't be changed then.
 */
public final class StickPacing {
    public static final String EXTRA = "stickPacing";
    public static final int MODE_AUTO = 1;
    public static final int MODE_FIXED = 2;
    public static final int MODE_OFF = 3;

    public static final int AUTO_MIN = 20, AUTO_MAX = 240, AUTO_DEFAULT = 60;
    public static final int FIXED_MIN = 30, FIXED_MAX = 1000, FIXED_DEFAULT = 125;

    public static final String ENV_AXIS_HZ = "HERMES_AXIS_HZ";
    public static final String ENV_AXIS_AUTO_MAX = "HERMES_AXIS_AUTO_MAX";

    public static final StickPacing DEFAULT = new StickPacing(MODE_AUTO, AUTO_DEFAULT);

    public final int mode;
    /** auto: the ceiling; fixed: the rate; off: 0. */
    public final int hz;

    public StickPacing(int mode, int hz) {
        if (mode == MODE_FIXED) {
            this.mode = MODE_FIXED;
            this.hz = clamp(hz, FIXED_MIN, FIXED_MAX);
        } else if (mode == MODE_OFF) {
            this.mode = MODE_OFF;
            this.hz = 0;
        } else {
            this.mode = MODE_AUTO;
            this.hz = clamp(hz <= 0 ? AUTO_DEFAULT : hz, AUTO_MIN, AUTO_MAX);
        }
    }

    /** "auto:60" / "fixed:125" / "off"; null for anything else (absent, blank, garbage). */
    public static StickPacing parse(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("off")) return new StickPacing(MODE_OFF, 0);
        int colon = v.indexOf(':');
        String kind = colon < 0 ? v : v.substring(0, colon);
        int n = -1;
        if (colon >= 0) {
            try {
                n = Integer.parseInt(v.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (kind.equals("auto")) return new StickPacing(MODE_AUTO, n < 0 ? AUTO_DEFAULT : n);
        if (kind.equals("fixed")) return new StickPacing(MODE_FIXED, n < 0 ? FIXED_DEFAULT : n);
        return null;
    }

    public String format() {
        switch (mode) {
            case MODE_FIXED: return "fixed:" + hz;
            case MODE_OFF: return "off";
            default: return "auto:" + hz;
        }
    }

    public String describe() {
        switch (mode) {
            case MODE_FIXED: return "Fixed " + hz + "/s";
            case MODE_OFF: return "Unpaced";
            default: return "Auto (up to " + hz + "/s)";
        }
    }

    /** The rate line under the mode: "Up to 60 per second" / "125 per second" / "" (Unpaced). */
    public String rateLabel() {
        switch (mode) {
            case MODE_FIXED: return hz + " per second";
            case MODE_OFF: return "";
            default: return "Up to " + hz + " per second";
        }
    }

    /** The Fixed rates the editors' − / + step through. */
    private static final int[] FIXED_STEPS = {30, 40, 50, 60, 75, 90, 100, 125, 150, 200, 250, 300, 400, 500, 750, 1000};

    /**
     * One − (dir < 0) or + (dir > 0) step of the editors' rate buttons, which replaced the sliders (a
     * scroll that began on a slider moved it). Auto moves in tens, a value between two tens going to
     * the next one in that direction; Fixed moves along FIXED_STEPS. This pacing itself at the end of
     * the range, for Unpaced and for dir 0.
     */
    public StickPacing step(int dir) {
        if (mode == MODE_OFF || dir == 0) return this;
        int next = hz;
        if (mode == MODE_AUTO) {
            next = clamp(dir > 0 ? (hz / 10 + 1) * 10 : ((hz + 9) / 10 - 1) * 10, AUTO_MIN, AUTO_MAX);
        } else if (dir > 0) {
            for (int s : FIXED_STEPS) if (s > hz) { next = s; break; }
        } else {
            for (int i = FIXED_STEPS.length - 1; i >= 0; i--) if (FIXED_STEPS[i] < hz) { next = FIXED_STEPS[i]; break; }
        }
        return next == hz ? this : new StickPacing(mode, next);
    }

    public static StickPacing forContainer(Container container) {
        StickPacing p = container != null ? parse(container.getExtra(EXTRA, "")) : null;
        return p != null ? p : DEFAULT;
    }

    /** The game's own value over the container's (over the default). */
    public static StickPacing forGame(Container container, Shortcut shortcut) {
        StickPacing own = shortcut != null ? parse(shortcut.getExtra(EXTRA)) : null;
        return own != null ? own : forContainer(container);
    }

    /** True when HERMES_AXIS_HZ / HERMES_AXIS_AUTO_MAX is set in the game's or the container's
     *  env vars: they win, and the setting is shown but can't be changed. */
    public static boolean envOverrides(Container container, Shortcut shortcut) {
        return envHas(shortcut != null ? shortcut.getExtra("envVars") : null)
                || envHas(container != null ? container.getEnvVars() : null);
    }

    private static boolean envHas(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        try {
            EnvVars e = new EnvVars(raw);
            return e.has(ENV_AXIS_HZ) || e.has(ENV_AXIS_AUTO_MAX);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StickPacing)) return false;
        StickPacing p = (StickPacing) o;
        return p.mode == mode && p.hz == hz;
    }

    @Override
    public int hashCode() {
        return mode * 31 + hz;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
