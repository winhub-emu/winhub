package com.winhub.hernandez.core;

import android.util.Log;

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

    // Ranges (MB) the PanVK driver accepts; empty or out-of-range = unset = not exported.
    public static final int HEAP_MIN = 256, HEAP_MAX = 16384;
    public static final int TILER_HEAP_MIN = 16, TILER_HEAP_MAX = 2048;
    public static final int POLY_HEAP_MIN = 4, POLY_HEAP_MAX = 512;
    /** Atom strides PANVK_ATOM_STRIDE takes besides auto (auto = unset). */
    public static final List<String> ATOM_STRIDES = Arrays.asList("56", "64");

    private static volatile Integer vendorId;

    private MaliPanvk() {}

    /** True on an Arm Mali GPU. The probe runs once per process. */
    public static boolean isMaliGpu() {
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
