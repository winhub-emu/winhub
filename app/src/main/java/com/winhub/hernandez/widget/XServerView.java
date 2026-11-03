package com.winhub.hernandez.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winhub.hernandez.renderer.HostRenderer;
import com.winhub.hernandez.renderer.egl.EglRenderer;
import com.winhub.hernandez.renderer.vulkan.VulkanRenderer;
import com.winhub.hernandez.xserver.XServer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("ViewConstructor")
public class XServerView extends FrameLayout {
    private HostRenderer renderer;
    private SurfaceView vulkanSurfaceView;
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();
    private XServer xServer;

    // VRR / refresh-rate matching. The last frame-rate vote requested by the activity is remembered
    // here and re-asserted in surfaceChanged() so it survives calls made before the surface exists
    // and any surface recreation. 0f means "no vote" (clears any previous request). The compatibility
    // is Surface.FRAME_RATE_COMPATIBILITY_DEFAULT (== 0) so SurfaceFlinger picks the seamless mapping
    // (e.g. 60 -> 59.94) itself.
    private float lastFrameRate = 0f;
    private int lastFrameRateCompat = 0;
    // Flip to true to only switch the panel mode when it can be done seamlessly (no black frame).
    // API 31+ only; default keeps it simple and lets SurfaceFlinger decide.
    private static final boolean FRAME_RATE_SEAMLESS_ONLY = false;

    public XServerView(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void initRenderer(String rendererType) {
        vulkanSurfaceView = new SurfaceView(getContext());
        vulkanSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addView(vulkanSurfaceView);
        if ("egl".equalsIgnoreCase(rendererType)) {
            EglRenderer egl = new EglRenderer(this, xServer);
            // The native side can refuse to start on a device; Vulkan is the safe fallback.
            if (egl.start()) renderer = egl;
            else Log.w("XServerView", "EGL renderer unavailable, falling back to Vulkan");
        }
        if (renderer == null) renderer = new VulkanRenderer(this, xServer);
        final VulkanRenderer vulkan = renderer instanceof VulkanRenderer ? (VulkanRenderer) renderer : null;
        final EglRenderer eglRenderer = renderer instanceof EglRenderer ? (EglRenderer) renderer : null;
        vulkanSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (vulkan != null) vulkan.onSurfaceCreated(holder.getSurface());
                else eglRenderer.onSurfaceCreated(holder.getSurface());
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (vulkan != null) vulkan.onSurfaceChanged(width, height);
                else eglRenderer.onSurfaceChanged(width, height);
                reassertFrameRate();
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (vulkan != null) vulkan.onSurfaceDestroyed();
                else eglRenderer.onSurfaceDestroyed();
            }
        });
    }

    public HostRenderer getRenderer() {
        return renderer;
    }

    public SurfaceHolder getHolder() {
        return vulkanSurfaceView != null ? vulkanSurfaceView.getHolder() : null;
    }

    public void requestRender() {
        if (renderer != null) renderer.requestRender();
    }

    public void queueEvent(Runnable r) {
        eventExecutor.execute(r);
    }

    public void onPause() {
    }

    public void onResume() {
    }

    /** True if this renderer uses a SurfaceView we can fully tear down + rebuild (Vulkan). */
    public boolean canRecreateSurface() {
        return vulkanSurfaceView != null;
    }

    // Force a REAL Android surface teardown by hiding the game SurfaceView. GONE removes it from the
    // layout, which destroys its underlying android.view.Surface and fires SurfaceHolder.Callback
    // surfaceDestroyed -> renderer.onSurfaceDestroyed() (Vulkan: nativeDetachSurface; full
    // full destroy). This is the load-bearing difference vs a swapchain-only recreate on the same
    // surface: it replicates what a background/foreground cycle does to the window surface, which is
    // the ONLY thing device-proven to clear the LSFG black-frame flicker. Paired with rebuildSurface().
    public void teardownSurface() {
        if (vulkanSurfaceView != null && vulkanSurfaceView.getVisibility() != GONE)
            vulkanSurfaceView.setVisibility(GONE);
    }

    // Bring the game SurfaceView back (VISIBLE). Android allocates a FRESH android.view.Surface and
    // fires surfaceCreated -> renderer.onSurfaceCreated() (nativeReattachSurface + swapchain recreate)
    // then surfaceChanged -> onSurfaceChanged() (resize). The callbacks arrive on a later traversal,
    // exactly as on a real foreground; the guest can be resumed immediately (the renderer buffers
    // presents until the surface is back). Paired with teardownSurface().
    public void rebuildSurface() {
        if (vulkanSurfaceView != null && vulkanSurfaceView.getVisibility() != VISIBLE)
            vulkanSurfaceView.setVisibility(VISIBLE);
    }

    public Object getSurfaceControl() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (vulkanSurfaceView != null) return vulkanSurfaceView.getSurfaceControl();
        }
        return null;
    }

    // Vote a target display refresh rate for the active host renderer's window (VRR / refresh-rate
    // matching). ONE Surface-level vote covers both renderers: SurfaceFlinger aggregates
    // frame-rate votes over a layer subtree, so a vote on the parent SurfaceView also covers the
    // Vulkan native child SurfaceControls. fps == 0f clears the vote (lets the panel run free).
    // The request is remembered and re-asserted in surfaceChanged() (see reassertFrameRate()).
    // Safe to call before the surface exists. Surface.setFrameRate is API 30+, so it is a no-op
    // below that (minSdk is 26).
    public void setDisplayFrameRate(float fps, int compatibility) {
        lastFrameRate = fps;
        lastFrameRateCompat = compatibility;
        applyFrameRateToSurface(fps, compatibility);
    }

    // Re-apply the last requested vote; called from the surfaceChanged callbacks so the vote
    // survives surface (re)creation and any early call made before the surface was valid.
    private void reassertFrameRate() {
        applyFrameRateToSurface(lastFrameRate, lastFrameRateCompat);
    }

    private void applyFrameRateToSurface(float fps, int compatibility) {
        if (Build.VERSION.SDK_INT < 30) return;
        SurfaceHolder holder = null;
        if (vulkanSurfaceView != null) holder = vulkanSurfaceView.getHolder();
        if (holder == null) return;
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                // The 2-arg setFrameRate() defaults to CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS, which a
                // peak-refresh panel (e.g. locked at 144) ignores — it won't drop to the cap. To make the
                // refresh rate actually follow the FPS we pass the strategy explicitly: ALWAYS (force the
                // switch, brief flash OK) unless FRAME_RATE_SEAMLESS_ONLY opts into seamless-only.
                int strategy = FRAME_RATE_SEAMLESS_ONLY
                        ? Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
                        : Surface.CHANGE_FRAME_RATE_ALWAYS;
                surface.setFrameRate(fps, compatibility, strategy);
            } else {
                // API 30 only has the 2-arg overload (no strategy parameter).
                surface.setFrameRate(fps, compatibility);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Surface was released, or the rate/compat is rejected — ignore, vote is best-effort.
        }
    }

    /** True if this display can actually do VRR (refresh-rate matching): Surface.setFrameRate exists
     *  (API 30+) AND the panel exposes more than one distinct refresh rate to switch between. */
    public static boolean isDisplayVrrCapable(android.view.Display display) {
        if (Build.VERSION.SDK_INT < 30 || display == null) return false;
        java.util.HashSet<Integer> rates = new java.util.HashSet<>();
        for (float r : getSupportedRefreshRatesPrecise(display)) rates.add(Math.round(r));
        return rates.size() > 1;
    }

    /** Refresh rates the display offers at its CURRENT resolution, exact (e.g. 59.94, 120.00001),
     *  distinct, ascending. Some panels list rates that only exist at another resolution; asking
     *  for one of those would be ignored or switch resolution, so they are left out. Empty below
     *  API 30 (no Surface.setFrameRate to ask with). */
    public static float[] getSupportedRefreshRatesPrecise(android.view.Display display) {
        if (Build.VERSION.SDK_INT < 30 || display == null) return new float[0];
        android.view.Display.Mode cur = display.getMode();
        java.util.TreeSet<Float> rates = new java.util.TreeSet<>();
        for (android.view.Display.Mode m : display.getSupportedModes()) {
            if (cur != null && (m.getPhysicalWidth() != cur.getPhysicalWidth()
                    || m.getPhysicalHeight() != cur.getPhysicalHeight())) continue;
            float r = m.getRefreshRate();
            boolean dup = false;
            for (float have : rates) if (Math.abs(have - r) < 0.5f) { dup = true; break; }
            if (!dup) rates.add(r);
        }
        float[] out = new float[rates.size()];
        int i = 0;
        for (float r : rates) out[i++] = r;
        return out;
    }

    /** The display's current (live) refresh rate, rounded. 0 if unavailable. Reflects the panel's
     *  active mode, so it shows what VRR/Auto actually landed on (not just what we requested). */
    public static int getCurrentRefreshRate(android.view.Display display) {
        if (display == null) return 0;
        android.view.Display.Mode mode = display.getMode();
        float rate = mode != null ? mode.getRefreshRate() : display.getRefreshRate();
        return Math.round(rate);
    }

    /** Distinct supported refresh rates (rounded, ascending). Empty if <2 (nothing to pick). */
    public static java.util.List<Integer> getSupportedRefreshRates(android.view.Display display) {
        java.util.TreeSet<Integer> rates = new java.util.TreeSet<>();
        for (float r : getSupportedRefreshRatesPrecise(display)) rates.add(Math.round(r));
        return rates.size() > 1 ? new java.util.ArrayList<>(rates) : new java.util.ArrayList<>();
    }
}
