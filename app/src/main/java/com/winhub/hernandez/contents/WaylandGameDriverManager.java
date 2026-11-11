package com.winhub.hernandez.contents;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.winhub.hernandez.core.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Imported Wayland GAME drivers: Linux/bionic Vulkan ICDs (Turnip built with the Wayland WSI) that
 * the game renders on when a container runs on the Wayland display backend. Sibling of
 * {@link AdrenotoolsManager}, which holds the Android-style (adrenotools-loaded) drivers the X11 game
 * path and the Wayland COMPOSITOR use — the two kinds are not interchangeable, so they live apart.
 *
 * Layout (under the app's files dir, which the guest sees by its full host path):
 * <pre>
 *   files/wayland_game_drivers/&lt;id&gt;/
 *       libvulkan_freedreno.so   the driver (renamed from whatever libvulkan_freedreno*.so the zip had)
 *       libdrm.so                optional, copied when the zip ships one
 *       icd.json                 generated Vulkan ICD manifest; library_path = the .so's ABSOLUTE path
 *       meta.json                name / driverVersion / libraryName (+ hasWaylandWsi), our own schema
 * </pre>
 * The launch path hands {@code icd.json}'s path to the Proton via {@code BANNER_WAYLAND_VK_ICD}
 * (see {@link com.winhub.hernandez.core.WaylandGameDriver}).
 *
 * Import accepts a .zip with a {@code libvulkan_freedreno*.so} entry that is a 64-bit little-endian
 * AArch64 ELF; an optional {@code libdrm.so}; an optional {@code meta.json} in the Android
 * driver-zip schema (name / driverVersion). A zip with no libvulkan_freedreno*.so (e.g. a pure
 * Android {@code vulkan.adXXXX.so} package) is rejected with a message.
 */
public class WaylandGameDriverManager {
    private static final String TAG = "WaylandGameDriver";
    public static final String DIR_NAME = "wayland_game_drivers";
    public static final String LIB_NAME = "libvulkan_freedreno.so";
    public static final String LIBDRM_NAME = "libdrm.so";
    public static final String ICD_NAME = "icd.json";
    public static final String META_NAME = "meta.json";

    private final Context context;
    private final File rootDir;

    public WaylandGameDriverManager(Context context) {
        this.context = context;
        this.rootDir = new File(context.getFilesDir(), DIR_NAME);
        if (!rootDir.exists()) rootDir.mkdirs();
    }

    public File getDriverDir(String id) {
        return new File(rootDir, id);
    }

    /** Installed = has the .so AND the generated manifest (a half-extracted dir is never offered). */
    public boolean isInstalled(String id) {
        if (id == null || id.isEmpty() || id.contains("/") || id.contains("..")) return false;
        File dir = getDriverDir(id);
        return new File(dir, LIB_NAME).isFile() && new File(dir, ICD_NAME).isFile();
    }

    /** Absolute path of the driver's ICD manifest, or null when the id isn't installed. */
    public String getIcdPath(String id) {
        return isInstalled(id) ? new File(getDriverDir(id), ICD_NAME).getAbsolutePath() : null;
    }

    public List<String> enumerateInstalledDrivers() {
        ArrayList<String> ids = new ArrayList<>();
        File[] dirs = rootDir.listFiles();
        if (dirs == null) return ids;
        for (File d : dirs) if (d.isDirectory() && isInstalled(d.getName())) ids.add(d.getName());
        Collections.sort(ids);
        return ids;
    }

    private JSONObject readMeta(String id) {
        try {
            File meta = new File(getDriverDir(id), META_NAME);
            if (!meta.isFile()) return null;
            return new JSONObject(FileUtils.readString(meta));
        } catch (Exception e) {
            return null;
        }
    }

    public String getDriverName(String id) {
        JSONObject m = readMeta(id);
        String name = m != null ? m.optString("name", "") : "";
        return name.isEmpty() ? id : name;
    }

    public String getDriverVersion(String id) {
        JSONObject m = readMeta(id);
        return m != null ? m.optString("driverVersion", "") : "";
    }

    /** False when the import's .so carried no "wayland" string at all (probably not a Wayland build). */
    public boolean hasWaylandWsi(String id) {
        JSONObject m = readMeta(id);
        return m == null || m.optBoolean("hasWaylandWsi", true);
    }

    public void removeDriver(String id) {
        if (id == null || id.isEmpty() || id.contains("/") || id.contains("..")) return;
        Log.d(TAG, "removing imported Wayland game driver " + id);
        FileUtils.delete(getDriverDir(id));
        // Containers/shortcuts still set to imported:<id> fall back to Auto at launch (logged there).
    }

    /**
     * Import a driver zip. Returns the new driver id.
     * @throws IllegalArgumentException with a user-facing reason when the zip is not an importable
     *         Wayland game driver; {@link IOException} on read/extract failures.
     */
    public String installDriver(Uri zipUri, String displayName) throws IOException {
        File tmpDir = new File(rootDir, ".tmp-" + System.currentTimeMillis());
        FileUtils.delete(tmpDir);
        if (!tmpDir.mkdirs()) throw new IOException("cannot create " + tmpDir);
        boolean keep = false;
        try {
            String soName = null;
            boolean hasDrm = false;
            JSONObject zipMeta = null;
            try (InputStream is = context.getContentResolver().openInputStream(zipUri);
                 ZipInputStream zis = new ZipInputStream(is)) {
                if (is == null) throw new IOException("cannot open " + zipUri);
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    // Flatten: only the base name matters, and it also defeats zip-slip paths.
                    String base = new File(entry.getName()).getName();
                    if (base.isEmpty()) continue;
                    if (base.startsWith("libvulkan_freedreno") && base.endsWith(".so")) {
                        if (soName != null) Log.w(TAG, "zip has several libvulkan_freedreno*.so; using the first (" + soName + ")");
                        else {
                            Files.copy(zis, new File(tmpDir, LIB_NAME).toPath(), StandardCopyOption.REPLACE_EXISTING);
                            soName = base;
                        }
                    } else if (base.equals(LIBDRM_NAME)) {
                        Files.copy(zis, new File(tmpDir, LIBDRM_NAME).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        hasDrm = true;
                    } else if (base.equals(META_NAME)) {
                        try {
                            zipMeta = new JSONObject(new String(readAll(zis), StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            Log.w(TAG, "meta.json unreadable, ignoring: " + e.getMessage());
                        }
                    }
                    // Anything else (vulkan.adXXXX.so, READMEs, …) is deliberately dropped.
                }
            }
            if (soName == null) {
                throw new IllegalArgumentException("No libvulkan_freedreno*.so in this zip. A Turnip zip made for "
                        + "Android (vulkan.adXXXX.so) is not a Wayland game driver.");
            }
            File so = new File(tmpDir, LIB_NAME);
            if (!isAarch64Elf(so)) {
                throw new IllegalArgumentException(soName + " is not a 64-bit AArch64 ELF shared library.");
            }
            boolean hasWaylandWsi = containsAscii(so, "wayland");
            if (!hasWaylandWsi) Log.w(TAG, soName + " has no \"wayland\" string: probably not a Wayland build");

            // Name/version: meta.json when present, else the zip's file name.
            String name = zipMeta != null ? zipMeta.optString("name", "") : "";
            String version = zipMeta != null ? zipMeta.optString("driverVersion", "") : "";
            if (name.isEmpty()) {
                name = displayName != null ? displayName : "wayland-driver";
                if (name.toLowerCase().endsWith(".zip")) name = name.substring(0, name.length() - 4);
            }
            String id = uniqueId(sanitizeId(name));
            File dir = getDriverDir(id);

            // ICD manifest: absolute library_path so the loader never depends on cwd/VK_ICD paths.
            JSONObject icd = new JSONObject();
            icd.put("file_format_version", "1.0.0");
            JSONObject icdBody = new JSONObject();
            icdBody.put("library_path", new File(dir, LIB_NAME).getAbsolutePath());
            icdBody.put("api_version", "1.3.0");
            icd.put("ICD", icdBody);
            if (!FileUtils.writeString(new File(tmpDir, ICD_NAME), icd.toString(2).replace("\\/", "/"))) throw new IOException("cannot write icd.json");

            JSONObject meta = new JSONObject();
            meta.put("schemaVersion", 1);
            meta.put("kind", "wayland-game-driver");
            meta.put("name", name);
            meta.put("driverVersion", version);
            meta.put("libraryName", LIB_NAME);
            meta.put("sourceLibraryName", soName);
            meta.put("hasLibdrm", hasDrm);
            meta.put("hasWaylandWsi", hasWaylandWsi);
            meta.put("importedAt", System.currentTimeMillis());
            if (!FileUtils.writeString(new File(tmpDir, META_NAME), meta.toString(2))) throw new IOException("cannot write meta.json");

            if (!tmpDir.renameTo(dir)) throw new IOException("cannot move into " + dir);
            keep = true;
            Log.i(TAG, "imported Wayland game driver " + id + " (" + soName + ", libdrm=" + hasDrm
                    + ", waylandWsi=" + hasWaylandWsi + ") -> " + dir);
            return id;
        } catch (org.json.JSONException e) {
            throw new IOException("manifest write failed: " + e.getMessage());
        } finally {
            if (!keep) FileUtils.delete(tmpDir);
        }
    }

    private String uniqueId(String base) {
        String id = base;
        int n = 2;
        while (getDriverDir(id).exists()) id = base + "-" + (n++);
        return id;
    }

    static String sanitizeId(String name) {
        String s = name.trim().replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._]+", "");
        if (s.length() > 64) s = s.substring(0, 64);
        return s.isEmpty() ? "wayland-driver" : s;
    }

    /** ELF magic, EI_CLASS = 64-bit, EI_DATA = little-endian, e_machine = 0xB7 (AArch64). */
    static boolean isAarch64Elf(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] h = new byte[20];
            int n = 0;
            while (n < h.length) {
                int r = in.read(h, n, h.length - n);
                if (r < 0) break;
                n += r;
            }
            if (n < 20) return false;
            if (h[0] != 0x7f || h[1] != 'E' || h[2] != 'L' || h[3] != 'F') return false;
            if (h[4] != 2 || h[5] != 1) return false;
            int machine = (h[18] & 0xff) | ((h[19] & 0xff) << 8);
            return machine == 0xB7;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsAscii(File f, String needle) {
        byte[] nb = needle.getBytes(StandardCharsets.US_ASCII);
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 16];
            int carry = 0;
            int r;
            while ((r = in.read(buf, carry, buf.length - carry)) > 0) {
                int len = carry + r;
                for (int i = 0; i + nb.length <= len; i++) {
                    int j = 0;
                    while (j < nb.length && (buf[i + j] | 0x20) == nb[j]) j++;
                    if (j == nb.length) return true;
                }
                carry = Math.min(nb.length - 1, len);
                System.arraycopy(buf, len - carry, buf, 0, carry);
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toByteArray();
    }
}
