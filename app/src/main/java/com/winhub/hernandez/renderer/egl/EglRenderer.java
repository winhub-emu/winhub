package com.winhub.hernandez.renderer.egl;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.renderer.GPUImage;
import com.winhub.hernandez.renderer.HostRenderer;
import com.winhub.hernandez.widget.FrameRating;
import com.winhub.hernandez.widget.XServerView;
import com.winhub.hernandez.xserver.Bitmask;
import com.winhub.hernandez.xserver.Cursor;
import com.winhub.hernandez.xserver.Drawable;
import com.winhub.hernandez.xserver.Window;
import com.winhub.hernandez.xserver.WindowAttributes;
import com.winhub.hernandez.xserver.WindowManager;
import com.winhub.hernandez.xserver.Pointer;
import com.winhub.hernandez.xserver.XResource;
import com.winhub.hernandez.xserver.XResourceManager;
import com.winhub.hernandez.xserver.XServer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * OpenGL ES host renderer (ported from Winlator-Ludashi). The scene lives in native code: this class only
 * mirrors the X server's windows, cursors and pointer into it and forwards the surface lifecycle.
 *
 * <p>Window contents are backed by AHardwareBuffers so the native side can sample them without a copy.
 * Anything that cannot get such a buffer is simply not drawn, it never reads memory the renderer does not own.</p>
 *
 * <p>{@link SurfaceDirectRenderer} reuses all of this but hands the buffers to SurfaceFlinger instead of drawing them.</p>
 */
public class EglRenderer implements HostRenderer, WindowManager.OnWindowModificationListener,
        Pointer.OnPointerMotionListener, XResourceManager.OnResourceLifecycleListener {
    private static final String TAG = "EglRenderer";
    private static final int FILTER_LINEAR = 0;
    private static final int FILTER_NEAREST = 1;

    static {
        System.loadLibrary("winlator");
    }

    private final XServerView xServerView;
    private final XServer xServer;
    private final SparseArray<GPUImage> cursorImages = new SparseArray<>();
    // (windowId << 32 | drawableId) of every pixmap handed to the native side as direct window content.
    private final HashSet<Long> directContents = new HashSet<>();

    private volatile boolean started = false;
    private volatile boolean destroyed = false;
    private boolean cursorVisible = true;
    private boolean screenOffsetYRelativeToCursor = false;
    private boolean nativeFullscreen = false;
    private float magnifierZoom = 1.0f;
    private int fullscreenMode = Container.FULLSCREEN_OFF;
    private int screenAlignment = Container.ALIGN_CENTER;
    private int fpsWindowId = -1;
    private int fpsLimit = 0;
    private int surfaceWidth = 0;
    private int surfaceHeight = 0;
    private String unviewableWMClass = null;
    private FrameRating classicHudRef = null;
    private IntConsumer hudFrameTick = null;

    // Only meaningful for SurfaceDirect, which the subclass configures.
    private final boolean surfaceDirect;
    private final boolean performanceMode;
    private final boolean presentAtRefreshRate;
    private final boolean backPressure;
    private final boolean precisePresentation;

    public EglRenderer(XServerView xServerView, XServer xServer) {
        this(xServerView, xServer, false, false, false, false, false);
    }

    EglRenderer(XServerView xServerView, XServer xServer, boolean surfaceDirect, boolean performanceMode,
                boolean presentAtRefreshRate, boolean backPressure, boolean precisePresentation) {
        this.xServerView = xServerView;
        this.xServer = xServer;
        this.surfaceDirect = surfaceDirect;
        this.performanceMode = performanceMode;
        this.presentAtRefreshRate = presentAtRefreshRate;
        this.backPressure = backPressure;
        this.precisePresentation = precisePresentation;
    }

    /**
     * Starts the native renderer. Returns false (and leaves nothing registered) when the native side cannot
     * run here, so the caller can fall back to another renderer.
     */
    public boolean start() {
        Context context = xServerView.getContext().getApplicationContext();
        // SurfaceDirect shows the root window's buffer too, so give it one before the native side looks.
        if (surfaceDirect) attachGpuImage(xServer.windowManager.rootWindow.getContent());
        boolean ok;
        try {
            ok = nativeInit(context, xServer, FILTER_LINEAR, surfaceDirect, performanceMode, presentAtRefreshRate,
                    backPressure, precisePresentation, getRefreshRate(context));
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "native EGL renderer is not available", e);
            return false;
        }
        if (!ok) {
            Log.e(TAG, "native EGL renderer failed to initialise");
            return false;
        }

        started = true;
        xServer.windowManager.addOnWindowModificationListener(this);
        xServer.windowManager.addOnResourceLifecycleListener(this);
        xServer.cursorManager.addOnResourceLifecycleListener(this);
        xServer.pointer.addOnPointerMotionListener(this);
        return true;
    }

    private static float getRefreshRate(Context context) {
        DisplayManager displays = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = displays != null ? displays.getDisplay(Display.DEFAULT_DISPLAY) : null;
        float rate = display != null ? display.getRefreshRate() : 60.0f;
        return rate > 0 ? rate : 60.0f;
    }

    private void shutdown() {
        if (destroyed) return;
        destroyed = true;
        if (!started) return;

        xServer.windowManager.removeOnWindowModificationListener(this);
        xServer.windowManager.removeOnResourceLifecycleListener(this);
        xServer.cursorManager.removeOnResourceLifecycleListener(this);
        xServer.pointer.removeOnPointerMotionListener(this);
        nativeStop();

        synchronized (cursorImages) {
            for (int i = 0; i < cursorImages.size(); i++) cursorImages.valueAt(i).destroy();
            cursorImages.clear();
        }
        synchronized (directContents) {
            directContents.clear();
        }
    }

    // ---- Surface lifecycle (driven by XServerView's SurfaceHolder callbacks) ----

    public void onSurfaceCreated(Surface surface) {
        if (destroyed || !started) return;
        nativeCreateSurface(surface);
    }

    public void onSurfaceChanged(int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        if (destroyed || !started) return;
        nativeChangeSurface(width, height);
    }

    public void onSurfaceDestroyed() {
        if (destroyed || !started) return;
        nativeDestroySurface();
    }

    // ---- Window mirroring ----

    @Override
    public void onCreateResource(XResource resource) {
        if (destroyed) return;
        if (resource instanceof Window) {
            Window window = (Window) resource;
            if (window.isInputOutput()) attachGpuImage(window.getContent());
            Window parent = window.getParent();
            nativeCreateWindow(window, parent != null ? parent.id : -1);
        }
        else if (resource instanceof Cursor) {
            registerCursor((Cursor) resource);
        }
    }

    @Override
    public void onFreeResource(XResource resource) {
        if (destroyed) return;
        if (resource instanceof Window) {
            Window window = (Window) resource;
            forgetDirectContents(window.id);
            nativeDestroyWindow(window.id);
        }
        else if (resource instanceof Cursor) {
            Cursor cursor = (Cursor) resource;
            nativeFreeCursor(cursor.id);
            GPUImage image;
            synchronized (cursorImages) {
                image = cursorImages.get(cursor.id);
                cursorImages.remove(cursor.id);
            }
            // The native side holds its own reference to the buffer until the free above has run.
            if (image != null) image.destroy();
        }
    }

    @Override
    public void onMapWindow(Window window) {
        if (destroyed) return;
        if (unviewableWMClass != null) {
            String wmClass = window.getClassName();
            if (wmClass != null && wmClass.contains(unviewableWMClass) && window.attributes.isEnabled())
                window.disableAllDescendants();
        }
        nativeMapWindow(window.id);
    }

    @Override
    public void onUnmapWindow(Window window) {
        if (destroyed) return;
        nativeUnmapWindow(window.id);
    }

    @Override
    public void onChangeWindowZOrder(Window window) {
        if (destroyed) return;
        Window parent = window.getParent();
        if (parent == null) return;

        // Re-expressed as "above this sibling" / "bottom" from the order the X server settled on.
        List<Window> siblings = parent.getChildren();
        int index = siblings.indexOf(window);
        if (index < 0) return;

        if (index == siblings.size() - 1) nativeChangeWindowZOrder(1, window.id, -1);
        else if (index == 0) nativeChangeWindowZOrder(0, window.id, -1);
        else nativeChangeWindowZOrder(1, window.id, siblings.get(index - 1).id);
    }

    @Override
    public void onUpdateWindowContent(Window window) {
        if (destroyed) return;
        nativeUpdateWindowContent(window.id);
    }

    @Override
    public void onUpdateWindowGeometry(Window window, boolean resized) {
        if (destroyed) return;
        // A resize swaps in a fresh content drawable, which has to be buffer-backed before the native side reads it.
        if (resized && window.isInputOutput()) attachGpuImage(window.getContent());
        nativeUpdateWindowGeometry(window.id, window.getWidth(), window.getHeight(), window.getX(), window.getY(), resized);
    }

    @Override
    public void onUpdateWindowAttributes(Window window, Bitmask mask) {
        if (destroyed) return;
        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) {
            Cursor cursor = window.attributes.getCursor();
            if (cursor == null) return;
            registerCursor(cursor);
            syncCursorPixels(cursor);
            nativeBindCursor(window.id, cursor.id, cursor.isVisible());
        }
    }

    @Override
    public void onReparentWindow(Window window, Window newParent) {
        if (destroyed) return;
        nativeReparentWindow(window.id, newParent.id, window.getX(), window.getY());
    }

    @Override
    public void onPointerMove(short x, short y) {
        if (destroyed) return;
        nativePointerMove(x, y);
    }

    /**
     * Called from the native guest-connection thread for every frame a guest presents over the SurfaceDirect
     * socket. Those frames never touch the X server, so the HUD has to be ticked from here.
     */
    public void onGuestPresent(Window window) {
        if (destroyed || window == null) return;
        if (hudFrameTick != null) hudFrameTick.accept(window.id);
        if (window.id == fpsWindowId && classicHudRef != null && classicHudRef.isAttachedToWindow())
            classicHudRef.post(classicHudRef);
    }

    // ---- Direct (zero copy) presentation, used by the Present extension ----

    /** True when {@code pixmap} can be shown through the native direct-content path instead of a CPU copy. */
    public boolean canPresentDirect(Window window, Drawable pixmap) {
        if (destroyed || !started || !(pixmap.getTexture() instanceof GPUImage)) return false;
        GPUImage image = (GPUImage) pixmap.getTexture();
        return image.getHardwareBufferPtr() != 0
                && image.getWidth() == window.getWidth()
                && image.getHeight() == window.getHeight();
    }

    public void onUpdateWindowContentDirect(Window window, Drawable pixmap, short xOff, short yOff) {
        if (destroyed) return;
        if (hudFrameTick != null) hudFrameTick.accept(window.id);
        if (window.id == fpsWindowId && classicHudRef != null && classicHudRef.isAttachedToWindow())
            classicHudRef.post(classicHudRef);

        long key = directKey(window.id, pixmap.id);
        boolean added;
        synchronized (directContents) {
            added = directContents.add(key);
        }
        if (added) {
            nativeAddDirectContent(window.id, pixmap);
            hookDirectContentRelease(pixmap);
        }
        nativeUpdateDirectContent(window.id, pixmap.id);
    }

    private static long directKey(int windowId, int drawableId) {
        return ((long) windowId << 32) | (drawableId & 0xffffffffL);
    }

    // The pixmap's destroy listener is free for buffer-backed pixmaps; when something else owns it, the
    // content is retired together with its window instead.
    private void hookDirectContentRelease(Drawable pixmap) {
        if (pixmap.getOnDestroyListener() != null) return;
        pixmap.setOnDestroyListener(drawable -> {
            List<Integer> windowIds = new ArrayList<>();
            synchronized (directContents) {
                for (java.util.Iterator<Long> it = directContents.iterator(); it.hasNext(); ) {
                    long key = it.next();
                    if ((int) key == drawable.id) {
                        windowIds.add((int) (key >>> 32));
                        it.remove();
                    }
                }
            }
            if (destroyed) return;
            for (int windowId : windowIds) nativeRemoveDirectContent(windowId, drawable.id);
        });
    }

    private void forgetDirectContents(int windowId) {
        synchronized (directContents) {
            directContents.removeIf(key -> (int) (key >>> 32) == windowId);
        }
    }

    // ---- Buffer backing ----

    /** Moves a drawable's pixels into an AHardwareBuffer. Returns false when no buffer could be allocated. */
    private boolean attachGpuImage(Drawable drawable) {
        if (drawable == null) return false;
        if (drawable.getTexture() instanceof GPUImage) return ((GPUImage) drawable.getTexture()).getHardwareBufferPtr() != 0;

        GPUImage image = new GPUImage(drawable.width, drawable.height);
        if (image.getHardwareBufferPtr() == 0 || image.getVirtualData() == null) {
            Log.w(TAG, "No hardware buffer for " + drawable.width + "x" + drawable.height + " drawable " + drawable.id);
            image.destroy();
            return false;
        }
        synchronized (drawable.renderLock) {
            drawable.setTexture(image);
        }
        return true;
    }

    private void registerCursor(Cursor cursor) {
        Drawable source = cursor.cursorImage;
        if (source == null || source.width <= 0 || source.height <= 0) return;

        synchronized (cursorImages) {
            if (cursorImages.get(cursor.id) != null) return;
        }
        GPUImage image = new GPUImage(source.width, source.height);
        if (image.getHardwareBufferPtr() == 0 || image.getVirtualData() == null) {
            Log.w(TAG, "No hardware buffer for cursor " + cursor.id);
            image.destroy();
            return;
        }
        synchronized (cursorImages) {
            cursorImages.put(cursor.id, image);
        }
        copyCursorPixels(source, image);
        nativeCreateCursor(cursor, image.getHardwareBufferPtr());
    }

    // The cursor image is drawn (and recoloured) after the cursor is created, so refresh the buffer whenever it is bound.
    private void syncCursorPixels(Cursor cursor) {
        GPUImage image;
        synchronized (cursorImages) {
            image = cursorImages.get(cursor.id);
        }
        if (image != null && cursor.cursorImage != null) copyCursorPixels(cursor.cursorImage, image);
    }

    private static void copyCursorPixels(Drawable source, GPUImage target) {
        ByteBuffer src;
        ByteBuffer dst = target.getVirtualData();
        synchronized (source.renderLock) {
            ByteBuffer data = source.getData();
            src = data != null ? data.duplicate() : null;
        }
        if (src == null || dst == null) return;

        int width = source.width;
        int height = source.height;
        int stride = target.getStride() > 0 ? target.getStride() : width;
        int rowBytes = width * 4;
        if (src.capacity() < rowBytes * height || dst.capacity() < (height - 1) * stride * 4 + rowBytes) return;

        ByteBuffer out = dst.duplicate();
        byte[] row = new byte[rowBytes];
        for (int y = 0; y < height; y++) {
            src.position(y * rowBytes);
            src.get(row, 0, rowBytes);
            out.position(y * stride * 4);
            out.put(row, 0, rowBytes);
        }
    }

    // ---- HostRenderer ----

    @Override
    public XServerView getXServerView() {
        return xServerView;
    }

    @Override
    public void setRenderingEnabled(boolean enabled) {
        xServer.setRenderingEnabled(enabled);
    }

    @Override
    public void requestRender() {
        // The native render thread redraws on its own whenever the scene changes.
    }

    @Override
    public void forceCleanup() {
        shutdown();
    }

    @Override
    public void setCursorVisible(boolean visible) {
        cursorVisible = visible;
        if (!destroyed && started) nativeSetCursorVisible(visible);
    }

    @Override
    public boolean isCursorVisible() {
        return cursorVisible;
    }

    @Override
    public void setUnviewableWMClasses(String wmClasses) {
        unviewableWMClass = (wmClasses == null || wmClasses.isEmpty()) ? null : wmClasses;
        if (!destroyed && started) nativeSetUnviewableWMClass(unviewableWMClass);
    }

    @Override
    public void setFilterMode(int mode) {
        // SurfaceFlinger does the sampling for SurfaceDirect, there is no filter to set.
        if (!destroyed && started && !surfaceDirect) nativeSetTextureFilter(mode == 2 ? FILTER_NEAREST : FILTER_LINEAR);
    }

    @Override
    public void setMagnifierZoom(float zoom) {
        magnifierZoom = zoom;
        if (!destroyed && started) nativeSetMagnifierZoom(zoom);
    }

    @Override
    public float getMagnifierZoom() {
        return magnifierZoom;
    }

    @Override
    public void toggleFullscreen() {
        setFullscreenMode(Container.nextFullscreenMode(fullscreenMode));
    }

    @Override
    public boolean isFullscreen() {
        return fullscreenMode != Container.FULLSCREEN_OFF;
    }

    @Override
    public void setFullscreenMode(int mode) {
        fullscreenMode = mode;
        // The native scene only knows "letterboxed" and "fill the surface", so the fill-type modes share the latter.
        boolean fill = mode == Container.FULLSCREEN_STRETCH || mode == Container.FULLSCREEN_FILL;
        if (fill != nativeFullscreen) {
            nativeFullscreen = fill;
            if (!destroyed && started) nativeToggleFullscreen();
        }
    }

    @Override
    public int getFullscreenMode() {
        return fullscreenMode;
    }

    @Override
    public void setScreenAlignment(int alignment) {
        // Kept for the settings UI; the native scene always centres the letterbox.
        screenAlignment = alignment;
    }

    @Override
    public int getScreenAlignment() {
        return screenAlignment;
    }

    @Override
    public void setScreenOffsetYRelativeToCursor(boolean b) {
        screenOffsetYRelativeToCursor = b;
        if (!destroyed && started) nativeSetScreenOffsetYRelativeToCursor(b);
    }

    @Override
    public boolean isScreenOffsetYRelativeToCursor() {
        return screenOffsetYRelativeToCursor;
    }

    @Override
    public void setFpsWindowId(int id) {
        fpsWindowId = id;
    }

    @Override
    public void setFrameRating(Object fr) {
        classicHudRef = fr instanceof FrameRating ? (FrameRating) fr : null;
    }

    /** Ticks the performance HUD for frames that bypass the CPU copy path. */
    public void setHudFrameTick(IntConsumer tick) {
        hudFrameTick = tick;
    }

    @Override
    public int getFpsLimit() {
        return fpsLimit;
    }

    @Override
    public void setFpsLimit(int limit) {
        fpsLimit = limit;
    }

    @Override
    public int getSurfaceWidth() {
        return surfaceWidth;
    }

    @Override
    public int getSurfaceHeight() {
        return surfaceHeight;
    }

    // ---- Native (renderer_jni.cpp) ----

    private native boolean nativeInit(Context context, XServer xServer, int textureFilter, boolean surfaceDirect,
                                      boolean performanceMode, boolean presentAtRefreshRate, boolean backPressure,
                                      boolean precisePresentation, float refreshRate);
    private native void nativeCreateSurface(Surface surface);
    private native void nativeDestroySurface();
    private native void nativeChangeSurface(int width, int height);
    private native void nativeCreateWindow(Window window, int parentId);
    private native void nativeDestroyWindow(int id);
    private native void nativeMapWindow(int id);
    private native void nativeUnmapWindow(int id);
    private native void nativeReparentWindow(int id, int parentId, short x, short y);
    private native void nativeChangeWindowZOrder(int stackMode, int id, int siblingId);
    private native void nativeUpdateWindowGeometry(int id, int width, int height, int x, int y, boolean resized);
    private native void nativeUpdateWindowContent(int id);
    private native void nativeCreateCursor(Cursor cursor, long hardwareBufferPtr);
    private native void nativeFreeCursor(int id);
    private native void nativeBindCursor(int windowId, int cursorId, boolean visible);
    private native void nativePointerMove(int x, int y);
    private native void nativeToggleFullscreen();
    private native void nativeSetCursorVisible(boolean visible);
    private native void nativeSetScreenOffsetYRelativeToCursor(boolean cond);
    private native void nativeSetMagnifierZoom(float magnifierZoom);
    private native void nativeSetUnviewableWMClass(String unviewableWMName);
    private native void nativeSetTextureFilter(int textureFilter);
    private native void nativeStop();
    private native void nativeAddDirectContent(int windowId, Drawable drawable);
    private native void nativeUpdateDirectContent(int windowId, int drawableId);
    private native void nativeRemoveDirectContent(int windowId, int drawableId);
}
