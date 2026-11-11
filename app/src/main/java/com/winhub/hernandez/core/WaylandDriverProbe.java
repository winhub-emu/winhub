package com.winhub.hernandez.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winhub.hernandez.BuildConfig;
import com.winhub.hernandez.XServerDisplayActivity;
import com.winhub.hernandez.contents.AdrenotoolsManager;
import com.winhub.hernandez.wayland.WaylandCompositor;

import org.json.JSONObject;

import java.io.File;

/**
 * The Wayland driver capability probe, Java half. {@link WaylandCompositor#nativeProbeDriver} loads a Turnip
 * the way the compositor loads its own, measures what the Wayland path (compositor + adapter) depends on and
 * answers one JSON object (schema: waylandcomp/src/driver_probe.h). This class
 * <ul>
 *   <li>resolves the container's AdrenoTools driver id to the .so the probe wants (the same
 *       {@link AdrenotoolsManager#getDriverPath} + {@link AdrenotoolsManager#getLibraryName} the compositor
 *       start uses);</li>
 *   <li>runs the probe off the UI thread under a 5 s watchdog (the native side is bounded at ~2 s; the
 *       watchdog only covers a driver that hangs in dlopen or vkCreateDevice);</li>
 *   <li>caches the answer per driver id + app versionCode in {@code files/wayland_probe/<driverId>.json},
 *       so one driver is measured once per app build;</li>
 *   <li>never runs beside a game session in the foreground ({@link XServerDisplayActivity#isSessionInForeground}),
 *       and never throws: every failure is a {@link Report} with {@code ok == false} and an {@code error}.</li>
 * </ul>
 * {@link WaylandAdapterSettings} reads the cached report to decide the Auto profile at launch; the editors'
 * "Wayland adapter settings" dialog shows it as the "Adapter report".
 */
public final class WaylandDriverProbe {
    private WaylandDriverProbe() {}

    private static final String TAG = "WaylandDriverProbe";
    public static final String DIR_NAME = "wayland_probe";
    /** Java-side bound on one probe; the native side stops by itself after ~2 s. */
    public static final long WATCHDOG_MS = 5000;
    /** What the dialog says when a session is on screen and the probe is skipped. */
    public static final String SESSION_RUNNING_NOTE =
            "A game is running, so the driver was not checked. Open this again after the game has closed.";

    /** The parsed report. Field names mirror the JSON keys; a key the probe left out reads as its "no" value. */
    public static final class Report {
        public final String driverId;
        public final JSONObject json;
        public final boolean ok;
        public final String error;         // null when ok
        public final String deviceName;    // "Turnip Adreno (TM) 750"
        public final String driverName;    // "turnip"
        public final String driverVersion; // "26.0.0"
        public final boolean syncFdFence;
        public final boolean syncFdSemaphore;
        public final boolean drmModifiers;
        public final boolean ahbExport;
        public final Boolean highPriorityAccepted; // null = the probe could not tell (no global-priority ext)
        public final String highPriorityRefusal;
        public final Boolean kgslZeroTimeoutBug;   // null = the test did not run / was inconclusive
        public final boolean dmabufSyncFileIoctl;
        public final int probeMs;
        public final boolean fromCache;

        Report(String driverId, JSONObject json, boolean fromCache) {
            this.driverId = driverId;
            this.json = json;
            this.fromCache = fromCache;
            ok = json.optBoolean("ok", false);
            String err = json.isNull("error") ? null : json.optString("error", null);
            error = ok ? null : (err == null || err.isEmpty() ? "the driver could not be started" : err);
            deviceName = json.optString("device_name", "");
            driverName = json.optString("driver_name", "");
            driverVersion = json.optString("driver_version", "");
            syncFdFence = json.optBoolean("sync_fd_fence", false);
            syncFdSemaphore = json.optBoolean("sync_fd_semaphore", false);
            drmModifiers = json.optBoolean("drm_modifiers", false);
            ahbExport = json.optBoolean("ahb_export", false);
            JSONObject gp = json.optJSONObject("global_priority");
            highPriorityAccepted = gp != null && gp.has("high_accepted") ? gp.optBoolean("high_accepted", false) : null;
            highPriorityRefusal = gp != null ? gp.optString("refusal", "") : "";
            kgslZeroTimeoutBug = json.has("kgsl_zero_timeout_bug") && !json.isNull("kgsl_zero_timeout_bug")
                    ? json.optBoolean("kgsl_zero_timeout_bug", false) : null;
            dmabufSyncFileIoctl = json.optBoolean("dmabuf_sync_file_ioctl", false);
            probeMs = json.optInt("probe_ms", 0);
        }

        /** "Turnip Adreno (TM) 750 · turnip 26.0.0", or what is known of it. */
        public String driverLine() {
            StringBuilder sb = new StringBuilder();
            if (!deviceName.isEmpty()) sb.append(deviceName);
            String dv = (driverName + " " + driverVersion).trim();
            if (!dv.isEmpty()) sb.append(sb.length() > 0 ? " · " : "").append(dv);
            return sb.length() > 0 ? sb.toString() : "(driver not identified)";
        }

        /**
         * Zero-copy presentation needs the game's driver to hand the compositor a sync_fd fence per frame
         * (client render fences, banner_ahb_v1 v3); the dma-buf sync_file ioctl is the alternative when the
         * kernel offers it. Client fences are always available on the adapter, so the fence export decides.
         */
        public boolean zeroCopySafe() {
            return ok && syncFdFence;
        }

        /** One short reason for {@link #zeroCopySafe}'s answer, for the dialog. */
        public String zeroCopyReason() {
            if (!ok) return "driver could not be checked";
            if (!syncFdFence) return "the driver cannot export sync-file fences, so frames could tear or show early";
            return dmabufSyncFileIoctl ? "sync-file fences and the dma-buf sync ioctl both work"
                    : "sync-file fences work (client render fences)";
        }
    }

    // ── Driver → library path ────────────────────────────────────────────────────────────────────

    /**
     * The absolute .so path the probe wants for {@code driverId}, or null when the adapter cannot sit on it
     * anyway (System, a Qualcomm blob, missing — {@link WaylandAdapter#unusableReason}).
     */
    public static String libPath(Context context, String driverId) {
        if (WaylandAdapter.unusableReason(context, driverId) != null) return null;
        if (WaylandAdapter.isMaliSystem(driverId)) return null; // no AdrenoTools .so to load
        try {
            AdrenotoolsManager atm = new AdrenotoolsManager(context);
            String lib = atm.getLibraryName(driverId);
            if (lib == null || lib.isEmpty()) return null;
            File f = new File(atm.getDriverPath(driverId), lib);
            return f.isFile() ? f.getAbsolutePath() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────────────────────────────

    private static File cacheFile(Context context, String driverId) {
        // Driver ids are directory names already (contents/adrenotools/<id>), so they are file-name safe.
        return new File(new File(context.getFilesDir(), DIR_NAME), driverId + ".json");
    }

    /** The cached report for {@code driverId} from this app build, or null (no cache, other build, unreadable). */
    public static Report cached(Context context, String driverId) {
        if (driverId == null || driverId.isEmpty()) return null;
        try {
            File f = cacheFile(context, driverId);
            if (!f.isFile()) return null;
            String raw = FileUtils.readString(f);
            if (raw == null || raw.isEmpty()) return null;
            JSONObject wrapper = new JSONObject(raw);
            if (wrapper.optInt("app_version_code", -1) != BuildConfig.VERSION_CODE) return null;
            JSONObject report = wrapper.optJSONObject("report");
            if (report == null) return null;
            return new Report(driverId, report, true);
        } catch (Exception e) {
            Log.w(TAG, "unreadable probe cache for " + driverId, e);
            return null;
        }
    }

    /** Drop the cached report (the dialog's "Re-check"). */
    public static void forget(Context context, String driverId) {
        if (driverId == null || driverId.isEmpty()) return;
        try { FileUtils.delete(cacheFile(context, driverId)); } catch (Exception ignored) {}
    }

    private static void store(Context context, String driverId, JSONObject report) {
        try {
            File f = cacheFile(context, driverId);
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            JSONObject wrapper = new JSONObject();
            wrapper.put("app_version_code", BuildConfig.VERSION_CODE);
            wrapper.put("driver_id", driverId);
            wrapper.put("probed_at", System.currentTimeMillis());
            wrapper.put("report", report);
            FileUtils.writeString(f, wrapper.toString(2));
        } catch (Exception e) {
            Log.w(TAG, "could not store the probe report for " + driverId, e);
        }
    }

    // ── Running it ───────────────────────────────────────────────────────────────────────────────

    private static Report failed(String driverId, String error) {
        JSONObject j = new JSONObject();
        try { j.put("ok", false); j.put("error", error); } catch (Exception ignored) {}
        return new Report(driverId, j, false);
    }

    /**
     * Probe {@code driverId} now (blocking, up to {@link #WATCHDOG_MS}; call off the UI thread) and cache the
     * answer. Never throws. A game session in the foreground skips the probe (uncached, so it runs next
     * time); a driver the adapter cannot use answers {@code ok == false} with the reason.
     */
    public static Report probe(Context context, String driverId) {
        if (driverId == null || driverId.isEmpty()) return failed(driverId, "no graphics driver is set");
        if (XServerDisplayActivity.isSessionInForeground()) return failed(driverId, SESSION_RUNNING_NOTE);
        String why = WaylandAdapter.unusableReason(context, driverId);
        if (why != null) {
            Report r = failed(driverId, "the adapter does not run on it: " + why);
            store(context, driverId, r.json);
            return r;
        }
        // The probe loads an AdrenoTools .so by path; the Mali System driver has none. Not stored, and
        // with no report the Auto profile injects nothing (WaylandAdapterSettings.resolve).
        if (WaylandAdapter.isMaliSystem(driverId)) return failed(driverId, "the probe does not measure the System driver");
        String libPath = libPath(context, driverId);
        if (libPath == null) {
            Report r = failed(driverId, "the driver's library file is missing");
            store(context, driverId, r.json);
            return r;
        }
        final String[] out = new String[1];
        final Throwable[] err = new Throwable[1];
        Thread t = new Thread(() -> {
            try { out[0] = WaylandCompositor.nativeProbeDriver(libPath); }
            catch (Throwable e) { err[0] = e; }
        }, "wayland-driver-probe");
        t.setDaemon(true);
        long t0 = System.currentTimeMillis();
        t.start();
        try { t.join(WATCHDOG_MS); } catch (InterruptedException ignored) {}
        Report r;
        if (t.isAlive()) {
            // The native thread is abandoned (it cannot be killed); it is daemon and bounded on its own side.
            Log.w(TAG, "probe of " + driverId + " did not answer within " + WATCHDOG_MS + " ms");
            r = failed(driverId, "the driver did not answer within 5 seconds");
        } else if (err[0] != null) {
            Log.e(TAG, "probe of " + driverId + " threw", err[0]);
            r = failed(driverId, "the probe failed: " + err[0].getClass().getSimpleName());
        } else {
            JSONObject j;
            try { j = new JSONObject(out[0] == null ? "" : out[0]); }
            catch (Exception e) {
                Log.w(TAG, "probe of " + driverId + " returned no usable JSON: " + out[0]);
                j = failed(driverId, "the probe returned an unreadable report").json;
            }
            r = new Report(driverId, j, false);
        }
        store(context, driverId, r.json);
        Log.i(TAG, "probed " + driverId + " in " + (System.currentTimeMillis() - t0) + " ms: ok=" + r.ok
                + " fence=" + r.syncFdFence + " modifiers=" + r.drmModifiers + " kgslBug=" + r.kgslZeroTimeoutBug
                + " dmabufSync=" + r.dmabufSyncFileIoctl + " highPrio=" + r.highPriorityAccepted
                + (r.error != null ? " error=" + r.error : ""));
        return r;
    }

    /** Callback shape for {@link #probeAsync}. */
    public interface Callback { void onReport(Report report); }

    /** {@link #probe} on a background thread; {@code callback} runs on the main thread. */
    public static void probeAsync(Context context, String driverId, Callback callback) {
        final Context app = context.getApplicationContext();
        Thread t = new Thread(() -> {
            Report r;
            try { r = probe(app, driverId); }
            catch (Throwable e) { r = failed(driverId, "the probe failed: " + e.getClass().getSimpleName()); }
            final Report fr = r;
            new Handler(Looper.getMainLooper()).post(() -> callback.onReport(fr));
        }, "wayland-driver-probe-async");
        t.setDaemon(true);
        t.start();
    }
}
