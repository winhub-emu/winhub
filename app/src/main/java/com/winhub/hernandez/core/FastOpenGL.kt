package com.winhub.hernandez.core

import android.content.Context
import android.util.Log
import com.winhub.hernandez.contents.AdrenotoolsManager
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * The "Fast OpenGL" setting: OpenGL (and WineD3D's DirectDraw / older Direct3D, which sit on it)
 * through the bundled Mesa and Zink, presented by a Vulkan swapchain, instead of XMesa's
 * finish + readback + XPutImage per frame. See [X11Egl] for the X11 half and the measured numbers.
 *
 *  - X11     → on when asked for AND [unavailableReason] is null: the graphics driver is an installed
 *              Turnip (not "System", not Qualcomm's blob: Zink needs Mesa's Vulkan) and the APK carries
 *              the X11 GL libraries. Any layer: the launch picks the [Route] ([routeFor]):
 *      - [Route.EGL] when the layer's aarch64-unix win32u.so knows WINE_USE_EGL (arm64ec Wine 11):
 *        Wine's EGL backend loads the bundled libEGL.
 *      - [Route.GLX] otherwise (Wine 10 and older, every x86_64 layer, where box64 wraps the native
 *        libGL): winex11's GLX loads the bundled libGL, and the X server turns on its server-side GLX
 *        and DRI3/Present 1.2 for that session (XServer.enableServerGlx).
 *
 * Stored as the extra [EXTRA] on the container ("1" / "0", absent = on) and on a shortcut only as a
 * per-game override (absent or "" = follow the container). The route is chosen when the game process
 * starts, so there is no in-game drawer item. In the environment variables WINHUB_X11_EGL=0 forces it
 * off, WINHUB_X11_GLX=1 forces the GLX route and WINHUB_X11_EGL=1 the EGL route ([decideX11]).
 */
object FastOpenGL {
    private const val TAG = "FastOpenGL"

    const val EXTRA = "fastOpenGL"
    const val TITLE = "Fast OpenGL"
    const val HINT = "Faster OpenGL games, and DirectDraw / older Direct3D games through WineD3D."
    const val NEEDS_TURNIP = "Needs a Turnip driver"
    const val NOT_BUNDLED = "Not in this build"

    // Two plain OpenGL launch switches that sit with Fast OpenGL in the editors. Independent of it:
    // both reach Mesa on either path (GLX or EGL), so they are never greyed.
    /** "Show OpenGL FPS": GALLIUM_HUD=fps, Mesa's fps counter in the game's top-left corner. */
    @JvmField val FPS_HUD = GlLaunchSwitch(
        "glFpsHud", "Show OpenGL FPS", "Mesa's fps counter in the corner of OpenGL games.",
        defaultOn = false, envName = "GALLIUM_HUD", envValue = "fps")

    /** "OpenGL vsync off": vblank_mode=0, so a GL game's SwapBuffers stops waiting for the refresh. */
    @JvmField val VSYNC_OFF = GlLaunchSwitch(
        "glVsyncOff", "OpenGL vsync off",
        "Lets OpenGL games run above the screen's refresh rate. Very old games that tie game speed to " +
            "frame rate may run too fast: use the FPS limiter.",
        defaultOn = false, envName = "vblank_mode", envValue = "0")

    // ── Pure mapping (unit-tested) ───────────────────────────────────────────────────────────────

    /** "1" / "0" for a stored or imported value we understand, else null (= no choice). */
    @JvmStatic
    fun normalize(value: String?): String? = value?.trim()?.takeIf { it == "1" || it == "0" }

    /** The container's choice: on unless it says "0". */
    @JvmStatic
    fun containerOn(extra: String?): Boolean = normalize(extra) != "0"

    /** What a launch asks for: the shortcut's override, else the container's (default on). */
    @JvmStatic
    fun requested(containerExtra: String?, shortcutExtra: String?): Boolean =
        normalize(shortcutExtra)?.let { it == "1" } ?: containerOn(containerExtra)

    /** The one reason an X11 launch can't use it, or null when it can. First that applies wins. */
    @JvmStatic
    fun unavailableReason(driverUsable: Boolean, bundled: Boolean): String? = when {
        !driverUsable -> NEEDS_TURNIP
        !bundled -> NOT_BUNDLED
        else -> null
    }

    /** How an X11 launch reaches the bundled Mesa. */
    enum class Route(@JvmField val label: String) { EGL("egl"), GLX("glx") }

    /** Wine's EGL backend when the layer has it (arm64ec Wine 11), else GLX on the server-side GLX. */
    @JvmStatic
    fun routeFor(layerHasEgl: Boolean): Route = if (layerHasEgl) Route.EGL else Route.GLX

    /** One X11 launch's verdict: [route] is null when off; [why] is what the log line says. */
    class X11Decision(@JvmField val route: Route?, @JvmField val why: String) {
        val on: Boolean get() = route != null
        override fun toString(): String =
            "fast opengl: " + (if (route != null) "on ($why, ${route.label} route)" else "off ($why)")
    }

    /**
     * Resolve an X11 launch. The environment overrides come first, in this order: WINHUB_X11_EGL=0
     * (off), WINHUB_X11_GLX=1 (GLX route), WINHUB_X11_EGL=1 (EGL route); they skip the support check
     * (the bundled libraries are still needed, which the launch checks when it installs them). Else
     * the setting ([requested], [requestedBy] = "game" / "container"), then [unavailable], then the
     * layer picks the route.
     */
    @JvmStatic
    fun decideX11(
        requested: Boolean, requestedBy: String, eglOverride: Boolean?, glxForced: Boolean,
        unavailable: String?, layerHasEgl: Boolean,
    ): X11Decision = when {
        eglOverride == false -> X11Decision(null, "env override")
        glxForced -> X11Decision(Route.GLX, "env override")
        eglOverride == true -> X11Decision(Route.EGL, "env override")
        !requested -> X11Decision(null, requestedBy)
        unavailable != null -> X11Decision(null, "unsupported: $unavailable")
        else -> X11Decision(routeFor(layerHasEgl), requestedBy)
    }

    // ── Driver + APK ─────────────────────────────────────────────────────────────────────────────

    /**
     * Whether [driverId] (graphicsDriverConfig's "version") is a driver Zink can run on: an installed
     * Turnip. "System" / empty is the system Vulkan, i.e. the Qualcomm blob on Adreno; an imported
     * blob (v819) names Qualcomm as the vendor in its meta.json.
     */
    @JvmStatic
    fun driverUsable(context: Context, driverId: String?): Boolean =
        !driverId.isNullOrEmpty() && driverId != DefaultVersion.WRAPPER && !isProprietaryBlob(context, driverId)

    private fun isProprietaryBlob(context: Context, driverId: String): Boolean = try {
        AdrenotoolsManager(context).getDriverVendor(driverId).lowercase().contains("qualcomm")
    } catch (e: Exception) {
        Log.w(TAG, "vendor of $driverId: ${e.message}")
        false
    }

    /** The reason an X11 launch with [driverId] can't use it, or null. No layer scan. */
    @JvmStatic
    fun unavailableReason(context: Context, driverId: String?): String? =
        unavailableReason(driverUsable(context, driverId), X11Egl.isBundled(context))

    // ── Layer detection ──────────────────────────────────────────────────────────────────────────

    // The getenv in win32u's egl_init. Only a layer whose winex11 has the EGL backend carries it.
    private const val MARKER = "WINE_USE_EGL"

    private class Entry(val stamp: String, val hasEgl: Boolean)
    private val byPath = HashMap<String, Entry>()

    /**
     * Whether the layer installed at [layerPath] takes the EGL route: its aarch64-unix win32u.so
     * contains "WINE_USE_EGL". Only that file counts: an x86_64 layer runs under box64, which wraps the
     * native libGL, so it always takes the GLX route. Cached per directory and re-probed when
     * win32u.so changes (mtime/size). Blocking file I/O. Unreadable or missing reads as false (GLX).
     */
    @JvmStatic
    fun layerHasEgl(layerPath: String?): Boolean {
        if (layerPath.isNullOrEmpty()) return false
        val win32u = unixWin32u(File(layerPath))
        val stamp = "${win32u?.lastModified() ?: 0}:${win32u?.length() ?: 0}"
        synchronized(byPath) {
            byPath[layerPath]?.takeIf { it.stamp == stamp }?.let { return it.hasEgl }
        }
        val has = if (win32u == null) false else try {
            BufferedInputStream(FileInputStream(win32u)).use {
                SyncSupport.scanForMarkers(it, listOf(MARKER.toByteArray(Charsets.US_ASCII)))[0]
            }
        } catch (e: Exception) {
            Log.w(TAG, "scan ${win32u.path}: ${e.message}")
            false
        }
        Log.i(TAG, "layer $layerPath: egl=$has (${win32u?.path ?: "no win32u.so"})")
        synchronized(byPath) { byPath[layerPath] = Entry(stamp, has) }
        return has
    }

    /** Drop every cached verdict — after a layer is installed or removed. */
    @JvmStatic
    fun invalidate() {
        synchronized(byPath) { byPath.clear() }
    }

    private fun unixWin32u(dir: File): File? =
        File(dir, "lib/wine/aarch64-unix/win32u.so").takeIf { it.isFile }
}

/**
 * One on/off OpenGL launch switch stored like Fast OpenGL: the container extra [extra] ("1" / "0",
 * absent = [defaultOn]) and the same-named shortcut extra as a per-game override (absent or "" =
 * follow the container). When on, the launch exports [envName]=[envValue] unless the environment
 * variables already set [envName] (the user's value wins).
 */
class GlLaunchSwitch(
    @JvmField val extra: String,
    @JvmField val title: String,
    @JvmField val hint: String,
    @JvmField val defaultOn: Boolean,
    @JvmField val envName: String,
    @JvmField val envValue: String,
) {
    /** The container's choice: [defaultOn] unless it says "1" or "0". */
    fun containerOn(stored: String?): Boolean = FastOpenGL.normalize(stored)?.let { it == "1" } ?: defaultOn

    /** What a launch asks for: the shortcut's override, else the container's. */
    fun requested(containerExtra: String?, shortcutExtra: String?): Boolean =
        FastOpenGL.normalize(shortcutExtra)?.let { it == "1" } ?: containerOn(containerExtra)
}
