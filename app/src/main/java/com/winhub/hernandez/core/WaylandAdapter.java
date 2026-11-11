package com.winhub.hernandez.core;

import android.content.Context;
import android.util.Log;

import com.winhub.hernandez.contents.AdrenotoolsManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * The Wayland "adapter": the bionic Vulkan wrapper (Pipetto-crypto wrapper-25 lineage, the same
 * family as imagefs' X11 {@code libvulkan_wrapper.so}) rebuilt with the Wayland WSI. Handed to the
 * game as its Vulkan ICD on Wayland, it loads the container's AdrenoTools driver underneath
 * (ADRENOTOOLS_DRIVER_PATH / NAME, exported by the launch for every non-System pick), so the ONE
 * graphics driver the user picks drives both the compositor and the game.
 *
 * Ships inside the APK as {@value #ASSET_LIB} (+ an optional {@value #ASSET_META} naming the build).
 * Swapping the build is a one-file change: replace that asset. It is installed on first use to
 * <pre>
 *   files/wayland_adapter/&lt;first 12 hex of the asset's sha256&gt;/
 *       libvulkan_wrapper.so   the asset, 0755
 *       icd.json               generated; library_path = the .so's ABSOLUTE path
 * </pre>
 * so a new asset lands in a new folder by itself (older folders are removed) and nothing needs a
 * version bump. The launch hands {@code icd.json} to the Proton through {@code BANNER_WAYLAND_VK_ICD}
 * exactly like an imported Wayland game driver (see {@link WaylandGameDriver}).
 */
public final class WaylandAdapter {
    private WaylandAdapter() {}

    private static final String TAG = "WaylandAdapter";

    public static final String ASSET_DIR = "wayland/adapter";
    public static final String ASSET_LIB = ASSET_DIR + "/libvulkan_wrapper.so";
    public static final String ASSET_META = ASSET_DIR + "/adapter.json";
    public static final String DIR_NAME = "wayland_adapter";
    public static final String LIB_NAME = "libvulkan_wrapper.so";
    public static final String ICD_NAME = "icd.json";

    private static String cachedIcdPath;
    private static String cachedVersion;

    /** The build's label from {@value #ASSET_META} ("" when the asset carries none). */
    public static synchronized String version(Context context) {
        if (cachedVersion == null) {
            String v = "";
            try {
                v = new JSONObject(FileUtils.readString(context, ASSET_META)).optString("version", "");
            } catch (Exception ignored) {}
            cachedVersion = v;
        }
        return cachedVersion;
    }

    /**
     * Install the bundled adapter if this APK's copy isn't in place yet, and return its icd.json's
     * absolute path — or null when the APK carries no adapter or the install failed (logged; the
     * caller falls back to Auto). Does file I/O on first call per process: call it off the UI thread
     * (the launch path already is).
     */
    public static synchronized String ensureInstalled(Context context) {
        if (cachedIcdPath != null && new File(cachedIcdPath).isFile()) return cachedIcdPath;
        File root = new File(context.getFilesDir(), DIR_NAME);
        try {
            String id = assetId(context);
            if (id == null) return null;
            File dir = new File(root, id);
            File lib = new File(dir, LIB_NAME);
            File icd = new File(dir, ICD_NAME);
            if (!lib.isFile() || !icd.isFile()) {
                if (!root.isDirectory() && !root.mkdirs()) throw new IOException("cannot create " + root);
                File tmp = new File(root, ".tmp-" + System.currentTimeMillis());
                FileUtils.delete(tmp);
                if (!tmp.mkdirs()) throw new IOException("cannot create " + tmp);
                try {
                    File tmpLib = new File(tmp, LIB_NAME);
                    try (InputStream in = context.getAssets().open(ASSET_LIB);
                         OutputStream out = new FileOutputStream(tmpLib)) {
                        byte[] buf = new byte[1 << 16];
                        int r;
                        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                    }
                    // The guest dlopen()s it by absolute path: readable + executable for the app's uid.
                    tmpLib.setReadable(true, false);
                    tmpLib.setExecutable(true, false);
                    writeIcd(new File(tmp, ICD_NAME), lib);
                    FileUtils.delete(dir);
                    if (!tmp.renameTo(dir)) throw new IOException("cannot move into " + dir);
                } finally {
                    FileUtils.delete(tmp);
                }
                Log.i(TAG, "installed the Wayland adapter " + version(context) + " (" + id + ") -> " + dir);
            } else if (MaliPanvk.isMaliGpu()) {
                // Mali only: an icd.json written escaped (an older build, same asset hash) is rewritten
                // plain so winewayland can pin it. Elsewhere an existing icd.json is never touched.
                try {
                    if (FileUtils.readString(icd).contains("\\/")) {
                        writeIcd(icd, lib);
                        Log.i(TAG, "rewrote " + icd + " with plain slashes");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "could not rewrite " + icd, e);
                }
            }
            // Only the current build is ever used; older folders are dead weight.
            File[] old = root.listFiles();
            if (old != null) for (File f : old) if (!f.getName().equals(id)) FileUtils.delete(f);
            cachedIcdPath = icd.getAbsolutePath();
            return cachedIcdPath;
        } catch (Exception e) {
            Log.e(TAG, "could not install the Wayland adapter", e);
            return null;
        }
    }

    /**
     * The adapter's icd.json. JSONObject writes '/' as "\/": the Vulkan loader reads either form, but
     * winewayland's pin_icd_library does not ("could not pin .../\/data\/..."), and pinning is what
     * keeps the driver resident in the game. On a Mali GPU (FristOneRR PanVK) the slashes are written
     * plain, as WaylandGameDriverManager does, so the pin succeeds. Every other GPU keeps the escaped
     * form the adapter was device-proven with on Adreno (unpinned), so nothing changes there.
     */
    private static void writeIcd(File icd, File lib) throws Exception {
        JSONObject body = new JSONObject();
        body.put("library_path", lib.getAbsolutePath());
        body.put("api_version", "1.3.0");
        JSONObject manifest = new JSONObject();
        manifest.put("file_format_version", "1.0.0");
        manifest.put("ICD", body);
        String json = manifest.toString(2);
        if (MaliPanvk.isMaliGpu()) json = json.replace("\\/", "/");
        if (!FileUtils.writeString(icd, json))
            throw new IOException("cannot write icd.json");
    }

    /** First 12 hex of the asset's sha256, or null when the APK has no adapter asset. */
    private static String assetId(Context context) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = context.getAssets().open(ASSET_LIB)) {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
        } catch (IOException e) {
            Log.w(TAG, "no bundled Wayland adapter (" + ASSET_LIB + ")");
            return null;
        }
        StringBuilder sb = new StringBuilder();
        byte[] d = md.digest();
        for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i] & 0xff));
        return sb.toString();
    }

    /** Whether the APK carries an adapter at all (cheap; no install). */
    public static boolean isBundled(Context context) {
        try (InputStream in = context.getAssets().open(ASSET_LIB)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ── Which graphics drivers the adapter can sit on ────────────────────────────────────────────

    /**
     * "System" picked explicitly on an Arm Mali GPU: the phone's own Mali driver for both the compositor
     * (XServerDisplayActivity's Wayland resolve, maliSystemPick) and the game. An empty id is not this
     * pick. False on every other GPU.
     */
    public static boolean isMaliSystem(String driverId) {
        return "System".equals(driverId) && MaliPanvk.isMaliGpu();
    }

    /** True for a driver whose meta.json names Qualcomm as vendor (the proprietary blob, e.g. v819). */
    public static boolean isProprietaryBlob(Context context, String driverId) {
        if (driverId == null || driverId.isEmpty() || driverId.equals("System")) return false;
        try {
            String vendor = new AdrenotoolsManager(context).getDriverVendor(driverId);
            return vendor != null && vendor.toLowerCase().contains("qualcomm");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Why the adapter can't run the game on {@code driverId} (the container's / shortcut's graphics
     * driver), or null when it can. It needs an installed AdrenoTools Mesa driver underneath:
     * <ul>
     *   <li>"System" — no ADRENOTOOLS_* reaches the guest, so the wrapper would sit on the system
     *       Qualcomm driver, which exports no DRM format modifiers → the compositor can't import the
     *       frames;</li>
     *   <li>a Qualcomm blob (v819) — the same missing dma-buf/modifier support;</li>
     *   <li>an id that isn't installed / names no library.</li>
     * </ul>
     * In all three the launch keeps the old Auto (bundled Wayland Turnip) for the game.
     * <p>
     * Exception: "System" picked on purpose on an Arm Mali GPU ({@link #isMaliSystem}). No bundled
     * Turnip runs there, so the adapter sits on the phone's own driver (the wrapper loads the system
     * libvulkan when no ADRENOTOOLS_* is set), the same driver the compositor uses for that pick.
     */
    public static String unusableReason(Context context, String driverId) {
        if (isMaliSystem(driverId)) return null;
        if (driverId == null || driverId.isEmpty() || driverId.equals("System"))
            return "the graphics driver is System";
        if (isProprietaryBlob(context, driverId))
            return "the graphics driver is the proprietary Qualcomm driver (" + driverId + ")";
        try {
            String lib = new AdrenotoolsManager(context).getLibraryName(driverId);
            if (lib == null || lib.isEmpty()) return "the graphics driver " + driverId + " is not installed";
        } catch (Exception e) {
            return "the graphics driver " + driverId + " is unreadable";
        }
        return null;
    }
}
