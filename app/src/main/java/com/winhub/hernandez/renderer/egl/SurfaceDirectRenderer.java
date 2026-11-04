package com.winhub.hernandez.renderer.egl;

import com.winhub.hernandez.widget.XServerView;
import com.winhub.hernandez.xserver.XServer;

/**
 * SurfaceDirect host renderer. Windows are not drawn at all: each one becomes a
 * SurfaceFlinger layer showing its AHardwareBuffer, so presentation costs no GPU composition pass.
 *
 * <p>It shares the window mirroring of {@link EglRenderer}; only the native back end differs. Needs Android 10 or
 * newer, and {@link EglRenderer#start()} reports false on anything older so the caller can fall back.</p>
 */
public class SurfaceDirectRenderer extends EglRenderer {
    /** User-facing SurfaceDirect settings. */
    public static final class Options {
        public boolean performanceMode = true;
        public boolean presentAtRefreshRate = true;
        public boolean backPressure = false;
        public boolean precisePresentation = false;
    }

    public SurfaceDirectRenderer(XServerView xServerView, XServer xServer, Options options) {
        super(xServerView, xServer, true, options.performanceMode, options.presentAtRefreshRate,
                options.backPressure, options.precisePresentation);
    }
}
