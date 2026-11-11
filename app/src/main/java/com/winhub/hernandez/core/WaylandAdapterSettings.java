package com.winhub.hernandez.core;

import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Wayland adapter settings (the gear next to the Wayland game driver): a profile plus per-switch
 * overrides, stored as graphicsDriverConfig keys like the present mode is — in the container's string, and
 * in a shortcut's for a per-game override where an empty key inherits the container's. Nothing here is
 * read by the guest directly; at launch the keys are resolved ({@link #resolve}) and either pushed to the
 * compositor (XServerDisplayActivity.startWaylandCompositor) or written into the guest environment
 * ({@link #applyToGuestEnv}) as the env vars the adapter, Mesa and the compositor already understand.
 *
 * <h3>Keys</h3>
 * <pre>
 *   waylandProfile      "" | auto | smooth | fast | compat
 *   waylandPresent      "" | mailbox | fifo             → MESA_VK_WSI_PRESENT_MODE
 *   waylandAsyncCopy    "" | 1 | 0                      → BANNER_WAYLAND_ASYNC_COPY
 *   waylandZeroCopy     "" | 1 | 0                      → BANNER_WAYLAND_ZERO_COPY (+ BANNER_WSI_AHB for the guest)
 *   waylandClientFence  "" | 1 | 0                      → BANNER_WAYLAND_ZC_CLIENT_FENCE
 *   waylandUbwc         "" | 1 | 0                      → BANNER_WAYLAND_UBWC
 *   waylandModifiers    "" | 1 | 0                      → 0 = BANNER_WSI_NO_MODIFIERS=1
 *   waylandKgslShim     "" | 1 | 0                      → BANNER_KGSL_POLL_FIX
 *   waylandSubgroups    "" | auto | 0                   → 0 = BANNER_WRAPPER_NO_SUBGROUPS=1
 * </pre>
 * "" = Default = don't inject (the launch behaves as it did before this existed).
 *
 * <h3>Precedence, per switch</h3>
 * an env var the user typed (container or shortcut Env vars) &gt; the shortcut's key &gt; the container's
 * key &gt; the profile (the shortcut's, else the container's) &gt; nothing. The Auto profile reads the cached
 * {@link WaylandDriverProbe.Report} of the current graphics driver; with none cached it injects nothing.
 */
public final class WaylandAdapterSettings {
    private WaylandAdapterSettings() {}

    public static final String KEY_PROFILE = "waylandProfile";
    public static final String KEY_PRESENT = "waylandPresent";
    public static final String KEY_ASYNC_COPY = "waylandAsyncCopy";
    public static final String KEY_ZERO_COPY = "waylandZeroCopy";
    public static final String KEY_CLIENT_FENCE = "waylandClientFence";
    public static final String KEY_UBWC = "waylandUbwc";
    public static final String KEY_MODIFIERS = "waylandModifiers";
    public static final String KEY_KGSL_SHIM = "waylandKgslShim";
    /** Auto (""/auto) = the adapter reports the driver's subgroup support on Mesa drivers; 0 = hide it. */
    public static final String KEY_SUBGROUPS = "waylandSubgroups";
    /** Every key this class owns, in dialog order (the editors blank these for a shortcut that inherits). */
    public static final List<String> KEYS = Arrays.asList(KEY_PROFILE, KEY_PRESENT, KEY_ASYNC_COPY, KEY_ZERO_COPY,
            KEY_CLIENT_FENCE, KEY_UBWC, KEY_MODIFIERS, KEY_KGSL_SHIM, KEY_SUBGROUPS);

    public static final String PROFILE_AUTO = "auto";
    public static final String PROFILE_SMOOTH = "smooth";
    public static final String PROFILE_FAST = "fast";
    public static final String PROFILE_COMPAT = "compat";
    public static final List<String> PROFILES = Arrays.asList(PROFILE_AUTO, PROFILE_SMOOTH, PROFILE_FAST, PROFILE_COMPAT);

    public static final String ENV_PRESENT = "MESA_VK_WSI_PRESENT_MODE";
    public static final String ENV_ASYNC_COPY = "BANNER_WAYLAND_ASYNC_COPY";
    public static final String ENV_ZERO_COPY = "BANNER_WAYLAND_ZERO_COPY";
    public static final String ENV_CLIENT_FENCE = "BANNER_WAYLAND_ZC_CLIENT_FENCE";
    public static final String ENV_UBWC = "BANNER_WAYLAND_UBWC";
    public static final String ENV_NO_MODIFIERS = "BANNER_WSI_NO_MODIFIERS";
    public static final String ENV_KGSL_SHIM = "BANNER_KGSL_POLL_FIX";
    public static final String ENV_NO_SUBGROUPS = "BANNER_WRAPPER_NO_SUBGROUPS";

    public static String profileLabel(String profile) {
        switch (normalizeProfile(profile)) {
            case PROFILE_SMOOTH: return "Smooth";
            case PROFILE_FAST: return "Fast";
            case PROFILE_COMPAT: return "Compatibility";
            default: return "Auto (recommended)";
        }
    }

    /** One line under each profile choice in the dialog. */
    public static String profileHelp(String profile) {
        switch (normalizeProfile(profile)) {
            case PROFILE_SMOOTH: return "Even frame pacing: vsync (fifo), frames copied in the background, no zero-copy.";
            case PROFILE_FAST: return "Lowest latency: mailbox, zero-copy with compressed buffers, client render fences.";
            case PROFILE_COMPAT: return "Safest: vsync, every shortcut off, plain buffers, timing shim on. For games that flicker or hang.";
            default: return "Picks from the adapter report of your driver; with no report it changes nothing.";
        }
    }

    public static String normalizeProfile(String v) {
        if (v == null) return PROFILE_AUTO;
        String t = v.trim().toLowerCase();
        return PROFILES.contains(t) ? t : PROFILE_AUTO;
    }

    // ── Resolution ───────────────────────────────────────────────────────────────────────────────

    /** Where a resolved switch came from (for the summary and the launch log). */
    public enum Source { ENV, SHORTCUT, CONTAINER, PROFILE, PROBE, NONE }

    /** The resolved decisions. null = Default: nothing injected, the compositor keeps its built-in default. */
    public static final class Effective {
        public String profile = PROFILE_AUTO;   // the profile that applied (shortcut's, else container's)
        public Source profileSource = Source.NONE;
        public boolean probeUsed;              // Auto had a report to read
        public String presentMode;             // "mailbox" | "fifo" | null
        public Boolean asyncCopy, zeroCopy, clientFence, ubwc, modifiers, kgslShim;
        public boolean subgroupsOff;          // Subgroups: Off (the adapter hides subgroup support)
        public final Map<String, Source> sources = new HashMap<>();

        private Source src(String key) { Source s = sources.get(key); return s == null ? Source.NONE : s; }

        /** True when the user typed the env var for this key (the launch must not touch it). */
        public boolean fromEnv(String key) { return src(key) == Source.ENV; }

        /** "fifo · async copy · zero-copy off · UBWC on" — only what will be injected; {@code fallbackPresent}
         *  names the present mode the stored X11 key exports when none is decided here (may be null). */
        public String summary(String fallbackPresent) {
            List<String> parts = new ArrayList<>();
            if (presentMode != null) parts.add(presentMode);
            else if (fallbackPresent != null && !fallbackPresent.isEmpty()) parts.add(fallbackPresent + " (as stored)");
            if (asyncCopy != null) parts.add(asyncCopy ? "async copy" : "sync copy");
            if (zeroCopy != null) parts.add("zero-copy " + (zeroCopy ? "on" : "off"));
            if (clientFence != null) parts.add("client fences " + (clientFence ? "on" : "off"));
            if (ubwc != null) parts.add("UBWC " + (ubwc ? "on" : "off"));
            if (modifiers != null) parts.add("modifiers " + (modifiers ? "on" : "off"));
            if (kgslShim != null) parts.add("KGSL shim " + (kgslShim ? "on" : "off"));
            if (subgroupsOff) parts.add("subgroups off");
            if (parts.isEmpty()) return "nothing changed (compositor and driver defaults)";
            return String.join(" · ", parts);
        }

        /** The same, with where each came from, for the launch log. */
        public String describe() {
            StringBuilder sb = new StringBuilder("profile=").append(profile).append('/').append(profileSource.name().toLowerCase());
            if (probeUsed) sb.append(" (probe)");
            for (String k : KEYS) {
                if (KEY_PROFILE.equals(k)) continue;
                Source s = sources.get(k);
                if (s != null && s != Source.NONE) sb.append(' ').append(k.substring("wayland".length())).append('=').append(s.name().toLowerCase());
            }
            return sb.toString();
        }
    }

    private static Map<String, String> parse(String cfg) {
        if (cfg == null || cfg.isEmpty()) return new HashMap<>();
        try { return GraphicsDriverConfigDialog.parseGraphicsDriverConfig(cfg); }
        catch (Exception e) { return new HashMap<>(); }
    }

    private static String nonEmpty(Map<String, String> m, String key) {
        String v = m.get(key);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    /** "1"/true/on → TRUE, "0"/false/off → FALSE, anything else → null. */
    public static Boolean parseFlag(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.equals("1") || t.equalsIgnoreCase("true") || t.equalsIgnoreCase("on")) return Boolean.TRUE;
        if (t.equals("0") || t.equalsIgnoreCase("false") || t.equalsIgnoreCase("off")) return Boolean.FALSE;
        return null;
    }

    /**
     * Resolve for a launch (or the dialog's preview). {@code shortcutCfg} may be null (container launch);
     * {@code userEnv} is the merged container+shortcut Env vars (null = none); {@code probe} the cached
     * report of the graphics driver this session uses (null = none).
     */
    public static Effective resolve(String containerCfg, String shortcutCfg, EnvVars userEnv,
                                    WaylandDriverProbe.Report probe) {
        Map<String, String> c = parse(containerCfg);
        Map<String, String> s = shortcutCfg != null ? parse(shortcutCfg) : new HashMap<>();
        Effective e = new Effective();

        // Profile: the shortcut's, else the container's; "" = Auto.
        String p = nonEmpty(s, KEY_PROFILE);
        if (p != null) { e.profile = normalizeProfile(p); e.profileSource = Source.SHORTCUT; }
        else {
            p = nonEmpty(c, KEY_PROFILE);
            if (p != null) { e.profile = normalizeProfile(p); e.profileSource = Source.CONTAINER; }
            else { e.profile = PROFILE_AUTO; e.profileSource = Source.NONE; }
        }

        // The profile's defaults, then the explicit keys over them, then the user's env over everything.
        String present = null;
        Boolean async = null, zc = null, cf = null, ubwc = null, mods = null, kgsl = null;
        Source profSrc = Source.PROFILE;
        switch (e.profile) {
            case PROFILE_SMOOTH: present = "fifo"; async = true; zc = false; cf = true; break;
            case PROFILE_FAST: present = "mailbox"; async = true; zc = true; ubwc = true; cf = true; break;
            case PROFILE_COMPAT: present = "fifo"; async = false; zc = false; ubwc = false; mods = false; kgsl = true; break;
            default:
                if (probe != null && probe.ok) {
                    e.probeUsed = true;
                    profSrc = Source.PROBE;
                    // Zero-copy only when the driver exports sync_fd fences (client render fences are always
                    // available on the adapter; the dma-buf sync ioctl is the alternative). UBWC when the
                    // driver speaks DRM format modifiers. The KGSL shim is the adapter's own detection.
                    zc = probe.zeroCopySafe();
                    ubwc = probe.drmModifiers;
                    async = true;
                }
        }
        if (present != null) e.sources.put(KEY_PRESENT, profSrc);
        if (async != null) e.sources.put(KEY_ASYNC_COPY, profSrc);
        if (zc != null) e.sources.put(KEY_ZERO_COPY, profSrc);
        if (cf != null) e.sources.put(KEY_CLIENT_FENCE, profSrc);
        if (ubwc != null) e.sources.put(KEY_UBWC, profSrc);
        if (mods != null) e.sources.put(KEY_MODIFIERS, profSrc);
        if (kgsl != null) e.sources.put(KEY_KGSL_SHIM, profSrc);

        // Explicit keys: shortcut over container.
        String v;
        v = pick(s, c, KEY_PRESENT, e);
        if (v != null && (v.equals("mailbox") || v.equals("fifo"))) present = v;
        if ((v = pick(s, c, KEY_ASYNC_COPY, e)) != null && parseFlag(v) != null) async = parseFlag(v);
        if ((v = pick(s, c, KEY_ZERO_COPY, e)) != null && parseFlag(v) != null) zc = parseFlag(v);
        if ((v = pick(s, c, KEY_CLIENT_FENCE, e)) != null && parseFlag(v) != null) cf = parseFlag(v);
        if ((v = pick(s, c, KEY_UBWC, e)) != null && parseFlag(v) != null) ubwc = parseFlag(v);
        if ((v = pick(s, c, KEY_MODIFIERS, e)) != null && parseFlag(v) != null) mods = parseFlag(v);
        if ((v = pick(s, c, KEY_KGSL_SHIM, e)) != null && parseFlag(v) != null) kgsl = parseFlag(v);
        // Subgroups: only Off (0) changes anything; Auto is the adapter's own driver check.
        if ((v = pick(s, c, KEY_SUBGROUPS, e)) != null) e.subgroupsOff = Boolean.FALSE.equals(parseFlag(v));

        // Env vars the user typed win; the compositor side reads the resolved value, so parse them here.
        if (userEnv != null) {
            if (userEnv.has(ENV_PRESENT)) {
                String pm = userEnv.get(ENV_PRESENT);
                present = pm != null && !pm.trim().isEmpty() ? pm.trim() : null;
                e.sources.put(KEY_PRESENT, Source.ENV);
            }
            if (userEnv.has(ENV_ASYNC_COPY)) { async = envOn(userEnv.get(ENV_ASYNC_COPY), true); e.sources.put(KEY_ASYNC_COPY, Source.ENV); }
            if (userEnv.has(ENV_ZERO_COPY)) { zc = envOn(userEnv.get(ENV_ZERO_COPY), false); e.sources.put(KEY_ZERO_COPY, Source.ENV); }
            if (userEnv.has(ENV_CLIENT_FENCE)) { cf = envOn(userEnv.get(ENV_CLIENT_FENCE), true); e.sources.put(KEY_CLIENT_FENCE, Source.ENV); }
            if (userEnv.has(ENV_UBWC)) { ubwc = envOn(userEnv.get(ENV_UBWC), true); e.sources.put(KEY_UBWC, Source.ENV); }
            if (userEnv.has(ENV_NO_MODIFIERS)) { mods = !envOn(userEnv.get(ENV_NO_MODIFIERS), false); e.sources.put(KEY_MODIFIERS, Source.ENV); }
            if (userEnv.has(ENV_KGSL_SHIM)) { kgsl = envOn(userEnv.get(ENV_KGSL_SHIM), false); e.sources.put(KEY_KGSL_SHIM, Source.ENV); }
            if (userEnv.has(ENV_NO_SUBGROUPS)) { e.subgroupsOff = envOn(userEnv.get(ENV_NO_SUBGROUPS), false); e.sources.put(KEY_SUBGROUPS, Source.ENV); }
        }

        e.presentMode = present;
        e.asyncCopy = async; e.zeroCopy = zc; e.clientFence = cf; e.ubwc = ubwc; e.modifiers = mods; e.kgslShim = kgsl;
        return e;
    }

    /** The shortcut's non-empty value, else the container's; records the source. */
    private static String pick(Map<String, String> s, Map<String, String> c, String key, Effective e) {
        String v = nonEmpty(s, key);
        if (v != null) { e.sources.put(key, Source.SHORTCUT); return v; }
        v = nonEmpty(c, key);
        if (v != null) { e.sources.put(key, Source.CONTAINER); return v; }
        return null;
    }

    /** How the launch has always read these flags: {@code defaultOn} ones are off only on 0/false/off,
     *  the others on only on 1/true(/on). */
    private static boolean envOn(String v, boolean defaultOn) {
        Boolean f = parseFlag(v);
        if (f != null) return f;
        return defaultOn;
    }

    // ── Launch ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Write the decided switches into the guest environment, skipping every key the user typed an env var
     * for (those were merged into {@code envVars} already and must win). Call after both user env merges.
     * The compositor's half (nativeSet*) reads the same {@link Effective} in startWaylandCompositor; the
     * zero-copy guest half (BANNER_WSI_AHB) stays with the caller, which also folds in the HDR forcing.
     */
    public static void applyToGuestEnv(EnvVars envVars, Effective e) {
        if (e.presentMode != null && !e.fromEnv(KEY_PRESENT)) envVars.put(ENV_PRESENT, e.presentMode);
        putFlag(envVars, e, KEY_ASYNC_COPY, ENV_ASYNC_COPY, e.asyncCopy);
        putFlag(envVars, e, KEY_ZERO_COPY, ENV_ZERO_COPY, e.zeroCopy);
        putFlag(envVars, e, KEY_CLIENT_FENCE, ENV_CLIENT_FENCE, e.clientFence);
        putFlag(envVars, e, KEY_UBWC, ENV_UBWC, e.ubwc);
        if (e.modifiers != null && !e.fromEnv(KEY_MODIFIERS)) {
            if (e.modifiers) envVars.remove(ENV_NO_MODIFIERS);
            else envVars.put(ENV_NO_MODIFIERS, "1");
        }
        putFlag(envVars, e, KEY_KGSL_SHIM, ENV_KGSL_SHIM, e.kgslShim);
        if (e.subgroupsOff && !e.fromEnv(KEY_SUBGROUPS)) envVars.put(ENV_NO_SUBGROUPS, "1");
    }

    private static void putFlag(EnvVars envVars, Effective e, String key, String env, Boolean value) {
        if (value == null || e.fromEnv(key)) return;
        envVars.put(env, value ? "1" : "0");
    }
}
