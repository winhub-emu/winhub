package com.winhub.hernandez.renderer;

import com.winhub.hernandez.widget.XServerView;

public interface HostRenderer {
    XServerView getXServerView();
    void setRenderingEnabled(boolean enabled);
    void requestRender();
    void forceCleanup();
    void setCursorVisible(boolean visible);
    boolean isCursorVisible();
    void setUnviewableWMClasses(String wmClasses);
    void setFilterMode(int mode);
    void setMagnifierZoom(float zoom);
    float getMagnifierZoom();
    void toggleFullscreen();
    boolean isFullscreen();
    // Fullscreen aspect-ratio mode (issue #71): Container.FULLSCREEN_OFF/FIT/STRETCH/...
    // isFullscreen() stays == (mode != OFF) so existing upscaler/magnifier gates behave as before.
    void setFullscreenMode(int mode);
    int getFullscreenMode();
    // Screen alignment (issue #413): Container.ALIGN_CENTER/TOP/BOTTOM — vertical placement of the
    // letterbox rect. CENTER is the historical behavior; only moves the bar, never the scale.
    void setScreenAlignment(int alignment);
    int getScreenAlignment();
    void setScreenOffsetYRelativeToCursor(boolean b);
    boolean isScreenOffsetYRelativeToCursor();
    void setFpsWindowId(int id);
    int getFpsLimit();
    void setFpsLimit(int limit);
    int getSurfaceWidth();
    int getSurfaceHeight();
}
