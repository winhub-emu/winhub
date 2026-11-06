package com.winhub.hernandez.core;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;

/**
 * "DirectInput vibration": DirectInput games can make the controller vibrate. With it on, the evdev
 * layer (libfakeinput.so) shows Wine the pads' force-feedback effects, so winebus offers DirectInput
 * force feedback (rumble, and the steady, periodic, ramp and spring-type forces racing and flight games
 * use). HERMES's effect engine mixes the effects into the two rumble motors and sends them to the app
 * like XInput rumble, so Vibration's target, intensity and motors apply as usual. A steady force fades
 * after a couple of seconds, so the pad doesn't buzz all through a race.
 * <ul>
 *   <li>Per container: the extra {@value #EXTRA}, stored only when off ("0"); absent = on.</li>
 *   <li>Per game: the same key, "1" / "0"; absent or "" follows the container.</li>
 *   <li>{@value #ENV}=1 / 0 in the game's env vars (else the container's), which is what the guest
 *       gets, wins over both. The setting's rows say so and can't be changed then.</li>
 * </ul>
 * Written into HERMES's session control at launch ({@link HermesSessionControl#writeFfMode}). A
 * reader takes the mode when a pad is opened, so an in-game change reconnects the controller.
 */
public final class DInputVibration {
    public static final String EXTRA = "dinputVibration";
    public static final String ENV = "HERMES_DINPUT_FF";
    /** What the rows say while {@value #ENV} decides. */
    public static final String ENV_LOCKED = "Set by " + ENV + " in the env vars.";

    /** HermesWriter.setFfMode: unset (= on), on, off. */
    public static final int FF_MODE_UNSET = 0;
    public static final int FF_MODE_ON = 1;
    public static final int FF_MODE_OFF = 2;

    private DInputVibration() {}

    // ── Pure mapping (unit-tested) ───────────────────────────────────────────────────────────────

    /** "1" / "0" for a stored value we understand, else null (= no choice). */
    public static String normalize(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.equals("1") || v.equals("0") ? v : null;
    }

    /** The container's choice: on unless it says "0". */
    public static boolean containerOn(String extra) {
        return !"0".equals(normalize(extra));
    }

    /** The setting for a game: its own "1" / "0", else the container's (default on). */
    public static boolean requested(String containerExtra, String shortcutExtra) {
        String own = normalize(shortcutExtra);
        return own != null ? own.equals("1") : containerOn(containerExtra);
    }

    /**
     * What {@value #ENV} decides, from the raw env strings: the game's value if it sets one (the
     * launch merges the game's env vars over the container's), else the container's. True for 1, false
     * for 0, null when it isn't set or holds another value (the guest then goes by the setting).
     */
    public static Boolean envValue(String gameEnv, String containerEnv) {
        String v = envGet(gameEnv);
        if (v == null) v = envGet(containerEnv);
        if (v == null) return null;
        v = v.trim();
        if (v.equals("1")) return Boolean.TRUE;
        if (v.equals("0")) return Boolean.FALSE;
        return null;
    }

    /** On or off for a launch: {@value #ENV} > the game's value > the container's > on. */
    public static boolean effective(String containerExtra, String shortcutExtra, String gameEnv, String containerEnv) {
        Boolean env = envValue(gameEnv, containerEnv);
        return env != null ? env : requested(containerExtra, shortcutExtra);
    }

    /** The control-block mode for a resolved setting. */
    public static int ffMode(boolean on) {
        return on ? FF_MODE_ON : FF_MODE_OFF;
    }

    /** The value a container stores: nothing while on (the default), "0" when off. */
    public static String containerExtra(boolean on) {
        return on ? null : "0";
    }

    // ── Container / game ─────────────────────────────────────────────────────────────────────────

    public static boolean forContainer(Container container) {
        return container == null || containerOn(container.getExtra(EXTRA));
    }

    /** The setting for a game, without the env var. */
    public static boolean forGame(Container container, Shortcut shortcut) {
        return requested(container != null ? container.getExtra(EXTRA) : null,
                shortcut != null ? shortcut.getExtra(EXTRA) : null);
    }

    /** {@value #ENV} from the game's or the container's env vars (see {@link #envValue}), or null. */
    public static Boolean envOverride(Container container, Shortcut shortcut) {
        return envValue(shortcut != null ? shortcut.getExtra("envVars") : null,
                container != null ? container.getEnvVars() : null);
    }

    /** What this launch gets: the env var when it decides, else the setting. */
    public static boolean effective(Container container, Shortcut shortcut) {
        Boolean env = envOverride(container, shortcut);
        return env != null ? env : forGame(container, shortcut);
    }

    /** Non-null ({@link #ENV_LOCKED}) when {@value #ENV} decides for this game. */
    public static String lockedReason(Container container, Shortcut shortcut) {
        return envOverride(container, shortcut) != null ? ENV_LOCKED : null;
    }

    private static String envGet(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            EnvVars e = new EnvVars(raw);
            return e.has(ENV) ? e.get(ENV) : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
