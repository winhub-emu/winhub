package com.winhub.hernandez.core;

import android.content.Context;
import android.util.Log;

import com.winhub.hernandez.contents.AdrenotoolsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Arm Mali detection shared by the Mali-only options (PanVK driver sources, BCn layer, HUD counters),
 * and the PanVK driver variables the graphics driver dialog offers on a Mali GPU. Nothing here runs on
 * any other GPU, even when a config copied from a Mali phone carries these keys.
 */
public final class MaliPanvk {
    private static final String TAG = "XServerVulkan";

    public static final int VENDOR_ARM = 0x13B5;

    public static final String KEY_HEAP = "panvkHeapMb";
    public static final String KEY_TILER_HEAP = "panvkTilerHeapMb";
    public static final String KEY_POLY_HEAP = "panvkPolyHeapMb";
    public static final String KEY_ATOM_STRIDE = "panvkAtomStride";
    public static final String KEY_TRACE = "panvkTrace";
    /** "1" = hide the extensions PanVK handles badly (see {@link #HIDDEN_EXTENSIONS}). Opt-in. */
    public static final String KEY_HIDE_EXTS = "maliHideExts";
    /**
     * Wayland adapter "Mali mode" ("1"/"0"; absent = on when the driver's name contains "panvk"). Its
     * sub-switches: KEY_HIDE_EXTS ("0" = off, anything else = on) and the two below ("0" = off).
     */
    public static final String KEY_MODE = "maliMode";
    public static final String KEY_NO_SUBMIT_WAITS = "maliNoSubmitWaits";
    public static final String KEY_NO_ACQUIRE_SIGNAL = "maliNoAcquireSignal";
    /** Every key Mali mode owns (dialogs that rebuild graphicsDriverConfig carry these through). */
    public static final List<String> KEYS = Arrays.asList(
            KEY_MODE, KEY_HIDE_EXTS, KEY_NO_SUBMIT_WAITS, KEY_NO_ACQUIRE_SIGNAL,
            KEY_HEAP, KEY_TILER_HEAP, KEY_POLY_HEAP, KEY_ATOM_STRIDE, KEY_TRACE);

    private static final String[] HIDDEN_EXTENSIONS = {
        "VK_KHR_present_id", "VK_KHR_present_wait", "VK_KHR_dynamic_rendering"
    };

    // Ranges (MB) the PanVK driver accepts; empty or out-of-range = unset = not exported.
    public static final int HEAP_MIN = 256, HEAP_MAX = 16384;
    public static final int TILER_HEAP_MIN = 16, TILER_HEAP_MAX = 2048;
    public static final int POLY_HEAP_MIN = 4, POLY_HEAP_MAX = 512;
    /** Atom strides PANVK_ATOM_STRIDE takes besides auto (auto = unset). */
    public static final List<String> ATOM_STRIDES = Arrays.asList("56", "64");

    /** App setting (Settings > General): treat this device as Mali even if the probe says otherwise. */
    public static final String PREF_FORCE = "force_mali_mode";

    private static volatile Integer vendorId;
    private static volatile boolean forced;

    private MaliPanvk() {}

    /** Set from the saved setting at startup and whenever the user flips it. */
    public static void setForced(boolean force) {
        forced = force;
    }

    /** True on an Arm Mali GPU, or when the user forced Mali mode. The probe runs once per process. */
    public static boolean isMaliGpu() {
        if (forced) return true;
        Integer v = vendorId;
        if (v == null) {
            try {
                v = GPUInformation.getVendorID(null, null);
            } catch (Throwable t) {
                return false; // probe unavailable: not Mali, and not cached so a later call retries
            }
            vendorId = v;
        }
        return v == VENDOR_ARM;
    }

    /** Whether the AdrenoTools driver {@code driverId} (its id or meta.json name) mentions "panvk". */
    public static boolean looksLikePanvk(Context context, String driverId) {
        if (driverId == null || driverId.isEmpty()) return false;
        if (driverId.toLowerCase().contains("panvk")) return true;
        try {
            String name = new AdrenotoolsManager(context).getDriverName(driverId);
            return name != null && name.toLowerCase().contains("panvk");
        } catch (Exception e) {
            return false;
        }
    }

    /** Mali mode for this config: the stored switch, else on for a PanVK driver. */
    public static boolean modeOn(Context context, Map<String, String> cfg, String driverId) {
        String v = cfg.get(KEY_MODE);
        if ("1".equals(v)) return true;
        if ("0".equals(v)) return false;
        return looksLikePanvk(context, driverId);
    }

    /** A sub-switch's state: on unless stored "0". */
    public static boolean subOn(Map<String, String> cfg, String key) {
        return !"0".equals(cfg.get(key));
    }

    /**
     * Wayland launch env for Mali mode: the Wayland adapter's three Mali switches (BANNER_MALI_*), each
     * with its own sub-switch. Mali only. With no stored mode switch it also sets bcnEmulation=none in
     * {@code cfg} before the BCn block reads it. Returns what was applied, or null when nothing was.
     */
    public static String applyWaylandMode(Context context, EnvVars envVars, Map<String, String> cfg, String driverId) {
        if (!isMaliGpu() || !modeOn(context, cfg, driverId)) return null;
        List<String> applied = new ArrayList<>();
        boolean hide = subOn(cfg, KEY_HIDE_EXTS);
        boolean noWaits = subOn(cfg, KEY_NO_SUBMIT_WAITS);
        boolean noAcquire = noWaits && subOn(cfg, KEY_NO_ACQUIRE_SIGNAL);
        if (hide) envVars.put("BANNER_MALI_HIDE_EXTS", "1");
        if (noWaits) envVars.put("BANNER_MALI_NO_SUBMIT_WAITS", "1");
        if (noAcquire) envVars.put("BANNER_MALI_NO_ACQUIRE_SIGNAL", "1");
        applied.add("mali mode (HIDE_EXTS=" + (hide ? 1 : 0) + " NO_SUBMIT_WAITS=" + (noWaits ? 1 : 0)
                + " NO_ACQUIRE_SIGNAL=" + (noAcquire ? 1 : 0) + ")");
        if (cfg.get(KEY_MODE) == null || cfg.get(KEY_MODE).isEmpty()) {
            cfg.put("bcnEmulation", "none");
            applied.add("bcnEmulation=none (default)");
        }
        String summary = "Mali (PanVK): " + String.join(", ", applied) + " (driver " + driverId + ")";
        Log.i(TAG, summary);
        return summary;
    }

    /** {@code value} when it is a whole number within [min, max], else null. */
    public static String validMb(String value, int min, int max) {
        if (value == null) return null;
        try {
            int n = Integer.parseInt(value.trim());
            return n >= min && n <= max ? String.valueOf(n) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Exports each PANVK_* variable that is set in {@code cfg}. Mali only. Returns what was applied for
     * the Wine debug log, or null when nothing was.
     */
    public static String applyToLaunchEnv(EnvVars envVars, Map<String, String> cfg) {
        if (!isMaliGpu()) return null;
        List<String> applied = new ArrayList<>();
        if ("1".equals(cfg.get(KEY_HIDE_EXTS))) {
            // Added to whatever the user already blacklisted, so their list is kept.
            List<String> hidden = new ArrayList<>();
            String current = envVars.get("WRAPPER_EXTENSION_BLACKLIST");
            if (current != null) {
                for (String e : current.split(",")) if (!e.trim().isEmpty()) hidden.add(e.trim());
            }
            for (String e : HIDDEN_EXTENSIONS) if (!hidden.contains(e)) hidden.add(e);
            envVars.put("WRAPPER_EXTENSION_BLACKLIST", String.join(",", hidden));
            envVars.put("WRAPPER_DISABLE_PRESENT_WAIT", "1");
            applied.add("hidden extensions (present_id, present_wait, dynamic_rendering)");
        }
        String heap = validMb(cfg.get(KEY_HEAP), HEAP_MIN, HEAP_MAX);
        if (heap != null) { envVars.put("PANVK_HEAP_MB", heap); applied.add("PANVK_HEAP_MB=" + heap); }
        String tiler = validMb(cfg.get(KEY_TILER_HEAP), TILER_HEAP_MIN, TILER_HEAP_MAX);
        if (tiler != null) { envVars.put("PANVK_TILER_HEAP_MB", tiler); applied.add("PANVK_TILER_HEAP_MB=" + tiler); }
        String poly = validMb(cfg.get(KEY_POLY_HEAP), POLY_HEAP_MIN, POLY_HEAP_MAX);
        if (poly != null) { envVars.put("PANVK_POLY_HEAP_MB", poly); applied.add("PANVK_POLY_HEAP_MB=" + poly); }
        String atom = cfg.get(KEY_ATOM_STRIDE);
        if (atom != null && ATOM_STRIDES.contains(atom)) {
            envVars.put("PANVK_ATOM_STRIDE", atom);
            applied.add("PANVK_ATOM_STRIDE=" + atom);
        }
        if ("1".equals(cfg.get(KEY_TRACE))) { envVars.put("PANVK_TRACE", "1"); applied.add("PANVK_TRACE=1"); }
        if (applied.isEmpty()) return null;
        String summary = "Mali (PanVK): " + String.join(", ", applied);
        Log.i(TAG, summary);
        return summary;
    }
}
