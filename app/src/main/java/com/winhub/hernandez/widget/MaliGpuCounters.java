package com.winhub.hernandez.widget;

import android.os.SystemClock;
import android.util.Log;

import com.winhub.hernandez.core.GPUInformation;
import com.winhub.hernandez.core.MaliPanvk;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mali GPU usage and clock from the GPU's own hardware counters (/dev/mali0), for phones whose sysfs
 * GPU nodes are blocked from apps. From WinlatorMali (GunaCharanTeja, f5a8e92 + 82fada3); the reader is
 * cpp/winlator/mali_counters.cpp.
 *
 * HudMetrics asks for it only after every sysfs path failed. It runs only on an Arm Mali GPU whose name
 * is a CSF-generation part: a three-digit Mali-G (G310/G510/G610/G615/G710/G715/G720/G925 ...) or an
 * Immortalis. Job-manager parts (G31-G78, two digits) never open /dev/mali0: WinlatorMali found that the
 * counter or time-info ioctls reboot old MediaTek Bifrost kernels (Helio G95). The native side checks the
 * CSF interface version as well.
 *
 * One reader for the whole process (several HUD views each own a HudMetrics); samples closer together
 * than {@link #MIN_INTERVAL_MS} return the last one. Thread-safe.
 */
public final class MaliGpuCounters {
    private static final String TAG = "MaliGpuCounters";
    private static final long MIN_INTERVAL_MS = 500;
    private static final Pattern CSF_MODEL =
            Pattern.compile("(mali[- ]?g[1-9]\\d\\d(?!\\d))|immortalis", Pattern.CASE_INSENSITIVE);

    private static final Object LOCK = new Object();
    private static Boolean eligible;
    private static long handle;
    private static boolean unavailable;
    private static long lastSampleMs;
    private static final int[] last = {-1, -1};
    private static final int[] sample = new int[2];

    private MaliGpuCounters() {}

    /** True for a renderer name of a CSF-generation Mali (three-digit Mali-G or Immortalis). */
    static boolean isCsfMaliName(String renderer) {
        return renderer != null && CSF_MODEL.matcher(renderer.toLowerCase(Locale.US)).find();
    }

    /** GPU usage 0..100, or null when the counters can't be read here. */
    public static Integer usagePercent() {
        int v = read()[0];
        return v >= 0 ? v : null;
    }

    /** Current GPU clock in MHz, or null when unknown. */
    public static Integer clockMhz() {
        int v = read()[1];
        return v > 0 ? v : null;
    }

    private static int[] read() {
        synchronized (LOCK) {
            if (unavailable) return new int[]{-1, -1};
            long now = SystemClock.elapsedRealtime();
            if (handle != 0 && now - lastSampleMs < MIN_INTERVAL_MS) return last.clone();
            try {
                if (handle == 0) {
                    if (!isEligible()) {
                        unavailable = true;
                        return new int[]{-1, -1};
                    }
                    handle = nativeOpen();
                    if (handle == 0) {
                        unavailable = true;
                        Log.i(TAG, "hardware counters not available on this GPU/kernel");
                        return new int[]{-1, -1};
                    }
                    Log.i(TAG, "reading Mali GPU usage/clock from hardware counters");
                }
                if (!nativeSample(handle, sample)) {
                    nativeClose(handle);
                    handle = 0;
                    unavailable = true;
                    return new int[]{-1, -1};
                }
                lastSampleMs = now;
                last[0] = sample[0];
                last[1] = sample[1];
                return last.clone();
            } catch (Throwable t) {
                unavailable = true;
                Log.w(TAG, "counter reader failed", t);
                return new int[]{-1, -1};
            }
        }
    }

    private static boolean isEligible() {
        if (eligible == null) {
            boolean ok = false;
            try {
                ok = MaliPanvk.isMaliGpu() && isCsfMaliName(GPUInformation.getRenderer(null, null));
            } catch (Throwable ignored) {}
            eligible = ok;
        }
        return eligible;
    }

    private static native long nativeOpen();
    private static native boolean nativeSample(long handle, int[] out);
    private static native void nativeClose(long handle);
}
