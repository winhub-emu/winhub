package com.winhub.hernandez.xserver;

import android.util.SparseArray;

import com.winhub.hernandez.core.CursorLocker;
import com.winhub.hernandez.renderer.HostRenderer;
import com.winhub.hernandez.winhandler.WinHandler;
import com.winhub.hernandez.xserver.extensions.BigReqExtension;
import com.winhub.hernandez.xserver.extensions.DRI3Extension;
import com.winhub.hernandez.xserver.extensions.Extension;
import com.winhub.hernandez.xserver.extensions.GLXExtension;
import com.winhub.hernandez.xserver.extensions.MITSHMExtension;
import com.winhub.hernandez.xserver.extensions.PresentExtension;
import com.winhub.hernandez.xserver.extensions.RandrExtension;
import com.winhub.hernandez.xserver.extensions.SyncExtension;
import com.winhub.hernandez.xserver.extensions.XComposite;

import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.concurrent.locks.ReentrantLock;

public class XServer {
    public enum Lockable {WINDOW_MANAGER, PIXMAP_MANAGER, DRAWABLE_MANAGER, GRAPHIC_CONTEXT_MANAGER, INPUT_DEVICE, CURSOR_MANAGER, SHMSEGMENT_MANAGER}
    public static final short VERSION = 11;
    public static final String VENDOR_NAME = "Elbrus Technologies, LLC";
    public static final Charset LATIN1_CHARSET = Charset.forName("latin1");
    public final SparseArray<Extension> extensions = new SparseArray<>();
    public final ScreenInfo screenInfo;
    public final PixmapManager pixmapManager;
    public final ColormapManager colormapManager = new ColormapManager();
    public final ResourceIDs resourceIDs = new ResourceIDs(128);
    public final GraphicsContextManager graphicsContextManager = new GraphicsContextManager();
    public final SelectionManager selectionManager;
    public final DrawableManager drawableManager;
    public final WindowManager windowManager;
    public final CursorManager cursorManager;
    public final Keyboard keyboard = Keyboard.createKeyboard(this);
    public final Pointer pointer = new Pointer(this);
    public final InputDeviceManager inputDeviceManager;
    public final GrabManager grabManager;
    public final CursorLocker cursorLocker;
    private SHMSegmentManager shmSegmentManager;
    private HostRenderer renderer;
    private WinHandler winHandler;
    private final EnumMap<Lockable, ReentrantLock> locks = new EnumMap<>(Lockable.class);
    private boolean relativeMouseMovement = false;
    private boolean simulateTouchScreen = false;
    private boolean isGrabbed = false;
    private XClient grabbingClient = null;

    public XServer(ScreenInfo screenInfo) {
        this.screenInfo = screenInfo;
        cursorLocker = new CursorLocker(this);
        for (Lockable lockable : Lockable.values()) locks.put(lockable, new ReentrantLock());

        pixmapManager = new PixmapManager();
        drawableManager = new DrawableManager(this);
        cursorManager = new CursorManager(drawableManager);
        windowManager = new WindowManager(screenInfo, drawableManager);
        selectionManager = new SelectionManager(windowManager);
        inputDeviceManager = new InputDeviceManager(this);
        grabManager = new GrabManager(this);

        DesktopHelper.attachTo(this);
        setupExtensions();
    }

    /** Relative (delta) mouse delivery: the user's Relative Mouse toggle, or — Wayland mode only —
     *  a program holding a pointer lock in the compositor, which needs deltas the same way. */
    public boolean isRelativeMouseMovement() {
        return relativeMouseMovement || externalRelativeMode;
    }

    public void setRelativeMouseMovement(boolean relativeMouseMovement) {
        cursorLocker.setEnabled(!relativeMouseMovement);
        this.relativeMouseMovement = relativeMouseMovement;
    }

    private volatile boolean externalRelativeMode;

    /** Wayland mode: a program locked the pointer (zwp_pointer_constraints_v1), so the input path
     *  must deliver deltas while it holds. Leaves the X11 cursor locker alone (no X clients here). */
    public void setExternalRelativeMode(boolean on) {
        externalRelativeMode = on;
    }

    public boolean isSimulateTouchScreen() { return simulateTouchScreen; }

    public void setSimulateTouchScreen(boolean simulateTouchScreen) {
        this.simulateTouchScreen = simulateTouchScreen;
    }

    public HostRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(HostRenderer renderer) {
        this.renderer = renderer;
    }

    public void setRenderingEnabled(boolean enabled) {
        windowManager.setRenderingEnabled(enabled);
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public void setWinHandler(WinHandler winHandler) {
        this.winHandler = winHandler;
    }

    public SHMSegmentManager getSHMSegmentManager() {
        return shmSegmentManager;
    }

    public void setSHMSegmentManager(SHMSegmentManager shmSegmentManager) {
        this.shmSegmentManager = shmSegmentManager;
    }

    private class SingleXLock implements XLock {
        private final ReentrantLock lock;

        private SingleXLock(Lockable lockable) {
            this.lock = locks.get(lockable);
            lock.lock();
        }

        @Override
        public void close() {
            lock.unlock();
        }
    }

    private class MultiXLock implements XLock {
        private final Lockable[] lockables;

        private MultiXLock(Lockable[] lockables) {
            this.lockables = lockables;
            for (Lockable lockable : lockables) locks.get(lockable).lock();
        }

        @Override
        public void close() {
            for (int i = lockables.length - 1; i >= 0; i--) {
                locks.get(lockables[i]).unlock();
            }
        }
    }

    public XLock lock(Lockable lockable) {
        return new SingleXLock(lockable);
    }

    public XLock lock(Lockable... lockables) {
        return new MultiXLock(lockables);
    }

    public XLock lockAll() {
        return new MultiXLock(Lockable.values());
    }

    public Extension getExtensionByName(String name) {
        for (int i = 0; i < extensions.size(); i++) {
            Extension extension = extensions.valueAt(i);
            if (extension.getName().equals(name)) return extension;
        }
        return null;
    }

    /** Wayland mode: no X client is connected, so the input the app injects (on-screen controls,
     *  mouse, mapped keys) is handed to the Wayland compositor as well. Coordinates are in screen
     *  pixels (the virtual desktop), keys are evdev codes. */
    public interface InputSink {
        void onPointerMove(int x, int y);
        void onPointerButton(Pointer.Button button, boolean pressed);
        void onKey(int evdev, boolean pressed);
    }

    private volatile InputSink inputSink;

    public void setInputSink(InputSink sink) {
        inputSink = sink;
    }

    private void sinkPointerMove() {
        InputSink sink = inputSink;
        if (sink != null) sink.onPointerMove(pointer.getX(), pointer.getY());
    }

    private void sinkPointerButton(Pointer.Button button, boolean pressed) {
        InputSink sink = inputSink;
        if (sink != null) sink.onPointerButton(button, pressed);
    }

    private void sinkKey(XKeycode xKeycode, boolean pressed) {
        InputSink sink = inputSink;
        if (sink != null) sink.onKey((xKeycode.id & 0xff) - 8, pressed);
    }

    public void injectPointerMove(int x, int y) {
        try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
            pointer.setPosition(x, y);
        }
        sinkPointerMove();
    }

    public void injectPointerMoveDelta(int dx, int dy) {
        try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
            pointer.setPosition(pointer.getX() + dx, pointer.getY() + dy);
        }
        sinkPointerMove();
    }

    public void injectPointerButtonPress(Pointer.Button buttonCode) {
        try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
            pointer.setButton(buttonCode, true);
        }
        sinkPointerButton(buttonCode, true);
    }

    public void injectPointerButtonRelease(Pointer.Button buttonCode) {
        try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
            pointer.setButton(buttonCode, false);
        }
        sinkPointerButton(buttonCode, false);
    }

    public void injectPointerButtonPulse(Pointer.Button buttonCode) {
        try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
            pointer.setButton(buttonCode, true);
            pointer.setButton(buttonCode, false);
        }
        sinkPointerButton(buttonCode, true);
        sinkPointerButton(buttonCode, false);
    }

    public void injectKeyPress(XKeycode xKeycode) {
        injectKeyPress(xKeycode, 0);
    }

    public void injectKeyPress(XKeycode xKeycode, int keysym) {
        try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
            keyboard.setKeyPress(xKeycode.id, keysym);
        }
        sinkKey(xKeycode, true);
    }

    public void injectKeyRelease(XKeycode xKeycode) {
        try (XLock lock = lock(Lockable.WINDOW_MANAGER, Lockable.INPUT_DEVICE)) {
            keyboard.setKeyRelease(xKeycode.id);
        }
        sinkKey(xKeycode, false);
    }

    private void setupExtensions() {
        extensions.put(BigReqExtension.MAJOR_OPCODE, new BigReqExtension());
        extensions.put(MITSHMExtension.MAJOR_OPCODE, new MITSHMExtension());
        extensions.put(DRI3Extension.MAJOR_OPCODE, new DRI3Extension());
        extensions.put(PresentExtension.MAJOR_OPCODE, new PresentExtension());
        extensions.put(SyncExtension.MAJOR_OPCODE, new SyncExtension());
        extensions.put(RandrExtension.MAJOR_OPCODE, new RandrExtension(screenInfo));
    }

    private volatile boolean serverGlx = false;

    /** Opt-in server-side GLX (Fast OpenGL, GLX route): registers the GLX + Composite extensions and
     *  makes DRI3/Present report 1.2, so a DRI-mode Mesa libGL (GLX + Zink via kopper) finds its
     *  fbconfigs. Off by default; when never called the extension list and versions are exactly as
     *  before. Must run before the first client connects (the extension list is read at
     *  QueryExtension time). */
    public void enableServerGlx() {
        if (serverGlx) return;
        extensions.put(GLXExtension.MAJOR_OPCODE, new GLXExtension(this));
        extensions.put(XComposite.MAJOR_OPCODE, new XComposite(this));
        serverGlx = true;
    }

    public boolean isServerGlxEnabled() {
        return serverGlx;
    }

    public <T extends Extension> T getExtension(int opcode) {
        return (T)extensions.get(opcode);
    }

    public synchronized void setGrabbed(boolean grabbed, XClient client) {
        this.isGrabbed = grabbed;
        this.grabbingClient = client;
    }

    public synchronized boolean isGrabbedBy(XClient client) {
        return isGrabbed && grabbingClient == client;
    }
}
