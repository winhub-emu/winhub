package com.winhub.hernandez

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.wayland.WaylandCompositor

/**
 * Experimental Wayland display path (parallel to [XServerDisplayActivity], never on the
 * default X11 path). Hosts a SurfaceView and starts the embedded Wayland compositor
 * rendering into it; games committed by winewayland.drv are composited to this Surface
 * by the native Vulkan backend.
 *
 * Routing: a launch whose effective Display backend is "wayland" comes here instead of
 * XServerDisplayActivity. The Wine guest launch (WAYLAND_DISPLAY + registry
 * Drivers\Graphics=wayland, reusing the container/imagefs/box64-FEX setup, pointed at the
 * winewayland wcp) is the remaining M4 integration step — see WAYLAND_RUNTIME.md.
 */
class WaylandDisplayActivity : Activity(), SurfaceHolder.Callback {
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        setContentView(surfaceView)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!started) {
            started = true
            // Resolve the container's Turnip driver so the compositor imports dmabufs via
            // Turnip (not the system Adreno driver, which lacks drm_format_modifier).
            var driverPath: String? = null
            var libraryName: String? = null
            try {
                val containerId = intent.getIntExtra("container_id", 0)
                val container = ContainerManager(this).getContainerById(containerId)
                // The adrenotools driver id is the graphicsDriverConfig "version" (e.g.
                // "Mesa Turnip v…"), not the DX-wrapper graphicsDriver field.
                val driverId = container?.let {
                    GraphicsDriverConfigDialog.getVersion(it.graphicsDriverConfig)
                }
                if (!driverId.isNullOrEmpty() && driverId != "System") {
                    val atm = AdrenotoolsManager(this)
                    driverPath = atm.getDriverPath(driverId)
                    libraryName = atm.getLibraryName(driverId)
                }
            } catch (_: Exception) { /* fall back to system libvulkan */ }
            WaylandCompositor.nativeStartWithSurface(
                holder.surface, filesDir.absolutePath,
                driverPath, libraryName, applicationInfo.nativeLibraryDir
            )
            // TODO(M4): launch the Wine guest here with the wayland display env, reusing
            // the container/imagefs/box64-FEX machinery and the winewayland wcp.
        } else {
            WaylandCompositor.nativeSetSurface(holder.surface)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        WaylandCompositor.nativeSetSurface(null)
    }
}
