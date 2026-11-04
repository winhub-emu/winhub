package com.winhub.hernandez.core;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.Map;

/**
 * "BCn layer: Fast" — Fcharan's build of leegao's BCn layer (WinlatorMali 1.2, 2026-10-22: "improved
 * speeds, memory usage same as native BCN"), offered beside the bundled leegao layer on Arm Mali GPUs.
 *
 * The asset installs as its own implicit layer (libbcn_layer_fast.so, soname changed to match, manifest
 * VK_LAYER_BCN_BCnLayerFast with enable var {@value #ENABLE_VAR}), so it never overwrites the bundled
 * libbcn_layer.so. At launch, when the user picked Fast ({@value #KEY}={@value #FAST}) and the BCn block
 * has already decided the layer runs (ENABLE_BCN_COMPUTE=1), the env is switched over: the Fast layer's
 * enable var on, the bundled layer forced off (DISABLE_BCN_COMPUTE=1), so only one decoder ever loads.
 * The variables both builds read (BCN_COMPUTE_AUTO, BCN_COMPUTE_IMAGE_VIEW, the debug log pair) carry
 * over as set; the transcode targets (BCN_TRANSCODE_TO_*) are ignored by this build.
 *
 * Mali only (MaliPanvk.isMaliGpu): on any other GPU nothing is extracted and the env is not touched,
 * even when a config copied from a Mali phone carries the key.
 */
public final class BcnFastLayer {
    private static final String TAG = "GraphicsDriverExtraction";

    public static final String KEY = "bcnLayerBuild";
    public static final String FAST = "fast";
    public static final String ENABLE_VAR = "ENABLE_BCN_FAST";

    private static final String ASSET = "graphics_driver/bcn_layer_fast.tzst";
    private static final String LIB = "usr/lib/libbcn_layer_fast.so";
    private static final String MANIFEST = "usr/share/vulkan/implicit_layer.d/libbcn_layer_fast.json";
    private static final String MARKER = "usr/lib/.bcn_layer_fast_version";
    /** Bump when the asset changes, so an app update re-extracts it. */
    private static final int VERSION = 1;

    private BcnFastLayer() {}

    public static boolean isPicked(Map<String, String> cfg) {
        return cfg != null && FAST.equals(cfg.get(KEY));
    }

    /**
     * Switches the launch env from the bundled BCn layer to the Fast one when it applies. Returns a
     * line for the Wine debug log, or null when nothing changed.
     */
    public static String applyToLaunchEnv(Context context, File rootDir, EnvVars envVars, Map<String, String> cfg) {
        if (!isPicked(cfg) || !MaliPanvk.isMaliGpu()) return null;
        if (!"1".equals(envVars.get("ENABLE_BCN_COMPUTE"))) return null; // the layer is not running this launch
        if (!ensureInstalled(context, rootDir)) {
            Log.w(TAG, "BCn layer Fast: install failed, keeping the bundled layer");
            return "BCn layer Fast: install failed, bundled layer kept";
        }
        envVars.remove("ENABLE_BCN_COMPUTE");
        envVars.put("DISABLE_BCN_COMPUTE", "1");
        envVars.put(ENABLE_VAR, "1");
        Log.i(TAG, "BCn layer Fast (Fcharan) active instead of the bundled leegao layer");
        return "BCn layer: Fast (Fcharan build) instead of the bundled leegao layer";
    }

    private static boolean ensureInstalled(Context context, File rootDir) {
        File lib = new File(rootDir, LIB);
        File manifest = new File(rootDir, MANIFEST);
        File marker = new File(rootDir, MARKER);
        if (lib.isFile() && manifest.isFile() && String.valueOf(VERSION).equals(readMarker(marker))) return true;
        boolean ok = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, ASSET, rootDir);
        ok = ok && lib.isFile() && manifest.isFile();
        if (ok) FileUtils.writeString(marker, String.valueOf(VERSION));
        Log.i(TAG, "BCn layer Fast: extracted " + ASSET + (ok ? "" : " (FAILED)"));
        return ok;
    }

    private static String readMarker(File marker) {
        if (!marker.isFile()) return null;
        try {
            String s = FileUtils.readString(marker);
            return s != null ? s.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
