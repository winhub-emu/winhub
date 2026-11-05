package com.winhub.hernandez.core;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * OpenGL on X11 through the bundled Mesa (Zink presenting via kopper) instead of imagefs' XMesa,
 * by either route {@link FastOpenGL} picks: Wine's EGL backend, or winex11's GLX on the X server's
 * opt-in server-side GLX.
 *
 * <p>On X11 a GL game presents through imagefs' xlib {@code libGL.so.1.5.0} (XMesa): every
 * SwapBuffers waits for the GPU, maps the frame back and XPutImages it. Measured on a Pocket FIT
 * (AIO standalone GL cube, the same 310 MHz GPU cap for both): 222 fps, against 2023 on Wayland,
 * where Wine reaches Mesa's EGL and Zink presents through Vulkan. winex11 has an EGL backend too
 * (win32u {@code egl_init}, gated by {@code WINE_USE_EGL=1}); it only needs an EGL built with the
 * X11 platform, which the Proton layers' Wayland EGL is not. With this one, Zink presents through
 * kopper (a Vulkan swapchain on the X11 window, the wrapper's X11 WSI, the same path DXVK takes):
 * 1250 fps on the same cube, 401 to 1712 in SGI's GLUT skyfly, DirectDraw (WineD3D on GL) 351 to 1086.
 *
 * <p>Layers without the EGL backend (Wine 10 and older, every x86_64 layer through box64) take the
 * GLX route instead: the same Mesa tree's libGL (DRI mode) against the X server's server-side GLX
 * (XServer.enableServerGlx). Pocket FIT, Wine 9.5 x86_64: GL cube 385 to 2072 fps, skyfly 706 to
 * 2662, DirectDraw 601 to 1242.
 *
 * <p>Ships inside the APK under {@value #ASSET_DIR} (Mesa EGL + GLX + Zink from one tree, built with
 * {@code -Dplatforms=x11,wayland} for the Termux-style bionic userland, plus the two libraries it
 * links that imagefs lacks) and is installed on first use to
 * <pre>
 *   files/x11_egl/&lt;first 12 hex of sha256(x11egl.json + libEGL.so.1)&gt;/
 * </pre>
 * so a new build lands in a new folder by itself (older folders are removed). The json names the
 * build, so a set whose libEGL did not change still gets a new folder.
 *
 * <p>Driven by the "Fast OpenGL" setting ({@link FastOpenGL}: container, per-game override, support
 * check, route); the launch calls {@link #apply} once it has decided. {@value #ENV_OVERRIDE}=0/1 and
 * {@value #ENV_GLX}=1 in the environment variables are power-user overrides of that decision (they
 * still need these bundled libraries).
 */
public final class X11Egl {
    private X11Egl() {}

    private static final String TAG = "X11Egl";

    public static final String ASSET_DIR = "x11/egl";
    public static final String ASSET_META = ASSET_DIR + "/x11egl.json";
    public static final String DIR_NAME = "x11_egl";
    public static final String ENV_OVERRIDE = "WINHUB_X11_EGL";
    public static final String ENV_GLX = "WINHUB_X11_GLX";
    private static final String KEY_LIB = "libEGL.so.1";

    private static String cachedDir;

    /**
     * Install the bundled libraries and point Wine's OpenGL at them. The caller has already decided this
     * X11 launch uses Fast OpenGL and by which route (setting, support check, layer or an override);
     * this reads no gate. Runs after both user environment merges, so a value the user typed still wins.
     * Returns a one-line summary for the log, or null when the APK has no X11 GL set or it could not be
     * installed (logged; nothing is exported and the game stays on imagefs' XMesa).
     *
     * <ul>
     *   <li>{@code WINE_USE_EGL=1} (EGL route only): win32u loads libEGL and winex11 takes its EGL
     *       surfaces. The GLX route leaves it unset, so winex11 loads libGL.so.1 from this folder.</li>
     *   <li>{@code MESA_LOADER_DRIVER_OVERRIDE=zink}: EGL's X11 platform picks Zink.</li>
     *   <li>{@code LIBGL_KOPPER_DRI2=1} (EGL route only): Mesa only takes Zink's kopper path on X11
     *       when the server has DRI3 1.2 + Present 1.2 multibuffers; ours reports 1.0 then, and without
     *       this Mesa falls back to its software copy path, whose frames the X server never shows. The
     *       GLX route turns the server's 1.2 on instead.</li>
     *   <li>{@code GALLIUM_THREAD=0}: Mesa's threaded-context thread has no Wine TEB, and a fault on
     *       it kills the process with no trace (DirectDraw through WineD3D died this way on EGL, on
     *       both backends). Costs ~5% in a GL-bound test.</li>
     *   <li>{@code LD_LIBRARY_PATH}: this folder first. It must come before imagefs/usr/lib, which may
     *       hold an older {@code libgallium-26.3.0-devel.so} (the freedreno GL driver) under the same
     *       name.</li>
     * </ul>
     */
    public static String apply(Context context, EnvVars envVars, String imageFsLibDir, boolean eglRoute) {
        String dir = ensureInstalled(context);
        if (dir == null) return null;
        StringBuilder said = new StringBuilder("X11 " + (eglRoute ? "EGL" : "GLX") + " on (" + version(context) + "):");
        if (eglRoute) putIfUnset(envVars, "WINE_USE_EGL", "1", said);
        putIfUnset(envVars, "MESA_LOADER_DRIVER_OVERRIDE", "zink", said);
        if (eglRoute) putIfUnset(envVars, "LIBGL_KOPPER_DRI2", "1", said);
        putIfUnset(envVars, "GALLIUM_THREAD", "0", said);
        envVars.put("LD_LIBRARY_PATH", dir + ":" + imageFsLibDir + ":/system/lib64");
        said.append(" LD_LIBRARY_PATH=").append(dir).append(":...");
        return said.toString();
    }

    private static void putIfUnset(EnvVars envVars, String name, String value, StringBuilder said) {
        if (envVars.has(name)) {
            said.append(' ').append(name).append('=').append(envVars.get(name)).append(" (yours, kept)");
        } else {
            envVars.put(name, value);
            said.append(' ').append(name).append('=').append(value);
        }
    }

    /**
     * The power-user override in the environment variables: {@code TRUE}/{@code FALSE} for
     * {@value #ENV_OVERRIDE}=1/0, null when it is unset or anything else.
     */
    public static Boolean envOverride(EnvVars envVars) {
        if (!envVars.has(ENV_OVERRIDE)) return null;
        String v = envVars.get(ENV_OVERRIDE).trim();
        return v.equals("1") ? Boolean.TRUE : v.equals("0") ? Boolean.FALSE : null;
    }

    /** {@value #ENV_GLX}=1 in the environment variables: force the GLX route. */
    public static boolean glxForced(EnvVars envVars) {
        if (!envVars.has(ENV_GLX)) return false;
        String v = envVars.get(ENV_GLX).trim();
        return v.equals("1") || v.equalsIgnoreCase("true");
    }

    /** Whether the APK carries the X11 GL set at all (cheap; no install). */
    public static boolean isBundled(Context context) {
        try (InputStream in = context.getAssets().open(ASSET_META)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** The build's label from {@value #ASSET_META} ("" when the asset carries none). */
    public static String version(Context context) {
        try {
            return new JSONObject(FileUtils.readString(context, ASSET_META)).optString("version", "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Install the bundled EGL if this APK's copy isn't in place yet and return the folder's absolute
     * path, or null when the APK carries none or the install failed (logged). File I/O on the first
     * call per process: the launch path is already off the UI thread.
     */
    public static synchronized String ensureInstalled(Context context) {
        if (cachedDir != null && new File(cachedDir, KEY_LIB).isFile()) return cachedDir;
        File root = new File(context.getFilesDir(), DIR_NAME);
        try {
            String[] files = files(context);
            String id = assetId(context);
            if (id == null || files.length == 0) return null;
            File dir = new File(root, id);
            boolean complete = true;
            for (String f : files) if (!new File(dir, f).isFile()) complete = false;
            if (!complete) {
                if (!root.isDirectory() && !root.mkdirs()) throw new IOException("cannot create " + root);
                File tmp = new File(root, ".tmp-" + System.currentTimeMillis());
                FileUtils.delete(tmp);
                if (!tmp.mkdirs()) throw new IOException("cannot create " + tmp);
                try {
                    for (String f : files) {
                        File out = new File(tmp, f);
                        try (InputStream in = context.getAssets().open(ASSET_DIR + "/" + f);
                             OutputStream os = new FileOutputStream(out)) {
                            byte[] buf = new byte[1 << 16];
                            int r;
                            while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                        }
                        // The guest dlopen()s them: readable + executable for the app's uid.
                        out.setReadable(true, false);
                        out.setExecutable(true, false);
                    }
                    FileUtils.delete(dir);
                    if (!tmp.renameTo(dir)) throw new IOException("cannot move into " + dir);
                } finally {
                    FileUtils.delete(tmp);
                }
                Log.i(TAG, "installed X11 GL set " + version(context) + " (" + id + ") -> " + dir);
            }
            File[] old = root.listFiles();
            if (old != null) for (File f : old) if (!f.getName().equals(id)) FileUtils.delete(f);
            cachedDir = dir.getAbsolutePath();
            return cachedDir;
        } catch (Exception e) {
            Log.e(TAG, "could not install the X11 GL set", e);
            return null;
        }
    }

    private static String[] files(Context context) {
        try {
            JSONArray a = new JSONObject(FileUtils.readString(context, ASSET_META)).getJSONArray("files");
            String[] out = new String[a.length()];
            for (int i = 0; i < a.length(); i++) out[i] = a.getString(i);
            return out;
        } catch (Exception e) {
            Log.w(TAG, "no bundled X11 GL set (" + ASSET_META + ")");
            return new String[0];
        }
    }

    /** First 12 hex of sha256(x11egl.json + libEGL.so.1), or null when the APK has no X11 GL asset. */
    private static String assetId(Context context) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String asset : new String[] {ASSET_META, ASSET_DIR + "/" + KEY_LIB}) {
            try (InputStream in = context.getAssets().open(asset)) {
                byte[] buf = new byte[1 << 16];
                int r;
                while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            } catch (IOException e) {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder();
        byte[] d = md.digest();
        for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i] & 0xff));
        return sb.toString();
    }
}
