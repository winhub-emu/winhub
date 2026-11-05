package com.winhub.hernandez.xserver.extensions;

import static com.winhub.hernandez.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import android.util.SparseArray;

import com.winhub.hernandez.renderer.GPUImage;
import com.winhub.hernandez.xconnector.XInputStream;
import com.winhub.hernandez.xconnector.XOutputStream;
import com.winhub.hernandez.xconnector.XStreamLock;
import com.winhub.hernandez.xserver.Bitmask;
import com.winhub.hernandez.xserver.Drawable;
import com.winhub.hernandez.xserver.Pixmap;
import com.winhub.hernandez.xserver.Window;
import com.winhub.hernandez.xserver.XClient;
import com.winhub.hernandez.xserver.XLock;
import com.winhub.hernandez.xserver.XServer;
import com.winhub.hernandez.xserver.errors.BadImplementation;
import com.winhub.hernandez.xserver.errors.BadMatch;
import com.winhub.hernandez.xserver.errors.BadPixmap;
import com.winhub.hernandez.xserver.errors.BadWindow;
import com.winhub.hernandez.xserver.errors.XRequestError;
import com.winhub.hernandez.xserver.events.PresentCompleteNotify;
import com.winhub.hernandez.xserver.events.PresentIdleNotify;

import java.io.IOException;

public class PresentExtension implements Extension {
    public static final byte MAJOR_OPCODE = -103;
    private static final int FAKE_INTERVAL = 1000000 / 60;
    public enum Kind {PIXMAP, MSC_NOTIFY}
    public enum Mode {COPY, FLIP, SKIP}
    private final SparseArray<Event> events = new SparseArray<>();
    private SyncExtension syncExtension;

    // ─────────────────────────── Present listener (frame counting) ───────────────────────────
    // Fired once per actually-presented guest frame (any renderer/API — every present passes through
    // presentPixmap). Used by the ReShade "freeze-frame preview" pulse to count real presents so it can
    // re-freeze after N frames render the committed change. Single volatile slot (one observer at a time).
    public interface PresentListener { void onPresent(); }
    private volatile PresentListener presentListener;
    public void setPresentListener(PresentListener l) { this.presentListener = l; }

    private static abstract class ClientOpcodes {
        private static final byte QUERY_VERSION = 0;
        private static final byte PRESENT_PIXMAP = 1;
        private static final byte SELECT_INPUT = 3;
    }

    private static class Event {
        private Window window;
        private XClient client;
        private int id;
        private Bitmask mask;
    }

    @Override
    public String getName() {
        return "Present";
    }

    @Override
    public byte getMajorOpcode() {
        return MAJOR_OPCODE;
    }

    @Override
    public byte getFirstErrorId() {
        return 0;
    }

    @Override
    public byte getFirstEventId() {
        return 0;
    }

    private void sendIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence) {
        if (idleFence != 0) syncExtension.setTriggered(idleFence);

        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event event = events.valueAt(i);
                if (event.window == window && event.mask.isSet(PresentIdleNotify.getEventMask())) {
                    event.client.sendEvent(new PresentIdleNotify(event.id, window, pixmap, serial, idleFence));
                }
            }
        }
    }

    // ─────────────────────────── FPS limiter (guest-side) ───────────────────────────
    // The guest (DXVK/vkd3d/...) blocks waiting for the IdleNotify that says its presented buffer
    // is free to reuse. By DELAYING that IdleNotify to a paced cadence we throttle the GAME itself
    // (not just the display) — so the in-game HUD reflects the cap and GPU load drops. Live + all
    // host renderers + all APIs (every guest present goes through here).
    // Mechanism (IdleNotify-delay pacing + WindowTiming) ported from GameNative
    // (https://github.com/utkarshdalal/GameNative). See README Credits.
    private volatile int frameRateLimit = 0;
    // Pacing interval. Normally 1e9 / frameRateLimit; the float overload sets a fractional rate
    // (native frame gen runs the game a hair under an exact display fit - see
    // XServerDisplayActivity.pacedLimitWithSlack). 0 = derive from frameRateLimit.
    private volatile long frameIntervalNs = 0;
    public void setFrameRateLimit(int limit) {
        this.frameRateLimit = Math.max(0, limit);
        this.frameIntervalNs = limit > 0 ? 1_000_000_000L / limit : 0;
    }
    public void setFrameRateLimit(float fps) {
        if (!(fps > 0f)) { setFrameRateLimit(0); return; }
        this.frameIntervalNs = Math.round(1_000_000_000.0 / fps);
        this.frameRateLimit = Math.max(1, (int) Math.ceil(fps));
    }

    private static final long FIRE_EARLY_NS = 700_000L; // 0.7 ms

    private static class PendingIdle {
        Window window; Pixmap pixmap; int serial; int idleFence; long targetNs;
        PendingIdle(Window w, Pixmap p, int s, int f, long t) {
            window = w; pixmap = p; serial = s; idleFence = f; targetNs = t;
        }
    }
    private final java.util.concurrent.ConcurrentHashMap<Integer, PendingIdle> pendingIdles =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static class WindowTiming { long nextIdleNs = 0; }
    private final java.util.concurrent.ConcurrentHashMap<Integer, WindowTiming> windowTimings =
        new java.util.concurrent.ConcurrentHashMap<>();

    private volatile android.view.Choreographer choreographer = null;
    private volatile boolean choreographerChecked = false;
    private volatile boolean choreographerPosted = false;
    private final Object choreographerLock = new Object();

    private Thread cpuPacerThread = null;
    private final java.util.concurrent.PriorityBlockingQueue<PendingIdle> cpuQueue =
        new java.util.concurrent.PriorityBlockingQueue<>(11,
            java.util.Comparator.comparingLong(p -> p.targetNs));

    public void close() {
        if (cpuPacerThread != null) { cpuPacerThread.interrupt(); cpuPacerThread = null; }
    }

    // Send the idle now, or schedule it for the paced time when a limit is active.
    private void emitIdleNotify(Window window, Pixmap pixmap, int serial, int idleFence,
                                 int targetFps, com.winhub.hernandez.renderer.vulkan.VulkanRenderer renderer) {
        if (targetFps <= 0) { sendIdleNotify(window, pixmap, serial, idleFence); return; }

        final long interval = frameIntervalNs;
        final long frameNs = interval > 0 ? interval : 1_000_000_000L / targetFps;
        long now = System.nanoTime();
        WindowTiming wt = windowTimings.computeIfAbsent(window.id, k -> new WindowTiming());
        if (wt.nextIdleNs <= now - frameNs) wt.nextIdleNs = now + frameNs;
        else wt.nextIdleNs += frameNs;
        long fireTime = wt.nextIdleNs - FIRE_EARLY_NS;

        if (tryGetChoreographer(renderer) != null) {
            pendingIdles.put(window.id, new PendingIdle(window, pixmap, serial, idleFence, fireTime));
            postChoreographerCallback();
        } else {
            cpuQueue.offer(new PendingIdle(window, pixmap, serial, idleFence, fireTime));
        }
    }

    private android.view.Choreographer tryGetChoreographer(com.winhub.hernandez.renderer.vulkan.VulkanRenderer renderer) {
        if (choreographerChecked) return choreographer;
        synchronized (choreographerLock) {
            if (choreographerChecked) return choreographer;
            choreographerChecked = true;
            try {
                choreographer = android.view.Choreographer.getInstance();
            } catch (Exception ignored) {
                android.util.Log.w("PresentExtension", "Choreographer unavailable, using CPU pacer");
            }
            if (choreographer == null) startCpuPacer();
            return choreographer;
        }
    }

    private final android.view.Choreographer.FrameCallback vsyncCallback = frameTimeNs -> {
        choreographerPosted = false;
        boolean anyRemaining = false;
        for (java.util.Iterator<java.util.Map.Entry<Integer, PendingIdle>> it =
                pendingIdles.entrySet().iterator(); it.hasNext(); ) {
            PendingIdle p = it.next().getValue();
            if (frameTimeNs >= p.targetNs) {
                it.remove();
                sendIdleNotify(p.window, p.pixmap, p.serial, p.idleFence);
            } else anyRemaining = true;
        }
        if (anyRemaining) postChoreographerCallback();
    };

    private void postChoreographerCallback() {
        if (choreographer == null || choreographerPosted) return;
        choreographerPosted = true;
        choreographer.postFrameCallback(vsyncCallback);
    }

    private void startCpuPacer() {
        if (cpuPacerThread != null) return;
        cpuPacerThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                PendingIdle p = cpuQueue.peek();
                if (p == null) { java.util.concurrent.locks.LockSupport.parkNanos(500_000L); continue; }
                long now = System.nanoTime();
                if (now >= p.targetNs) {
                    cpuQueue.poll();
                    pendingIdles.remove(p.window.id, p);
                    sendIdleNotify(p.window, p.pixmap, p.serial, p.idleFence);
                } else {
                    long diff = p.targetNs - now;
                    if (diff > 2_000_000L) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                    else Thread.yield();
                }
            }
        }, "PresentPacer-CPU");
        cpuPacerThread.setDaemon(true);
        cpuPacerThread.setPriority(Thread.MAX_PRIORITY);
        cpuPacerThread.start();
    }

    private void sendCompleteNotify(Window window, int serial, Kind kind, Mode mode, long ust, long msc) {
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Event event = events.valueAt(i);
                if (event.window == window && event.mask.isSet(PresentCompleteNotify.getEventMask())) {
                    event.client.sendEvent(new PresentCompleteNotify(event.id, window, serial, kind, mode, ust, msc));
                }
            }
        }
    }

    private static void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        inputStream.skip(8);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeInt(1);
            outputStream.writeInt(client.xServer.isServerGlxEnabled() ? 2 : 0); // Present 1.2 only with the opt-in server GLX
            outputStream.writePad(16);
        }
    }

    private void presentPixmap(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int windowId = inputStream.readInt();
        int pixmapId = inputStream.readInt();
        int serial = inputStream.readInt();
        inputStream.skip(8);
        short xOff = inputStream.readShort();
        short yOff = inputStream.readShort();
        inputStream.skip(8);
        int idleFence = inputStream.readInt();
        inputStream.skip(client.getRemainingRequestLength());

        final Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        final Pixmap pixmap = client.xServer.pixmapManager.getPixmap(pixmapId);
        if (pixmap == null) throw new BadPixmap(pixmapId);

        Drawable content = window.getContent();
        int contentDepth = content.visual.depth;
        int pixmapDepth = pixmap.drawable.visual.depth;
        boolean depthCompat = (contentDepth == pixmapDepth) ||
            ((contentDepth == 24 || contentDepth == 32) && (pixmapDepth == 24 || pixmapDepth == 32));
        if (!depthCompat) throw new BadMatch();

        final com.winhub.hernandez.renderer.HostRenderer xr = client.xServer.getRenderer();
        final com.winhub.hernandez.renderer.vulkan.VulkanRenderer vr =
            (xr instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer)
                ? (com.winhub.hernandez.renderer.vulkan.VulkanRenderer) xr : null;

        final com.winhub.hernandez.renderer.egl.EglRenderer er =
            (xr instanceof com.winhub.hernandez.renderer.egl.EglRenderer)
                ? (com.winhub.hernandez.renderer.egl.EglRenderer) xr : null;

        final int targetFps = this.frameRateLimit;

        long ust = System.nanoTime() / 1000;
        long msc = ust / FAKE_INTERVAL;

        // AHB-backed pixmaps (native Vulkan / DXVK / vkd3d, DRI3 modifier 1255) carry their pixels
        // in an AHardwareBuffer, not in the CPU `data` buffer. Present them through the renderer's
        // AHB path instead of the generic copyArea (which would composite a blank buffer -> black):
        //   1. Vulkan native scanout + directScanout -> FLIP: swap the content texture to the
        //      pixmap's GPUImage and scan it out directly (zero copy).
        //   2. Vulkan (non-native) -> COPY via onUpdateWindowContentDirect (nativeUpdateWindowContentAHB).
        //   3. SHM pixmaps -> copyArea (the GPUImage is now CPU-locked + EGLImage-backed,
        //      so its real pixels are available to the CPU copy / GL texture).
        synchronized (content.renderLock) {
            boolean isNative = vr != null && vr.isNativeMode();

            if (isNative && pixmap.drawable.isDirectScanout()) {
                content.setTexture(pixmap.drawable.getTexture());
                content.setDirectScanout(true);
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.FLIP, ust, msc);
                if (window.attributes.isMapped()) vr.onUpdateWindowContent(window);
                emitIdleNotify(window, pixmap, serial, idleFence, targetFps, vr);
            } else if (vr != null && window.attributes.isMapped()
                    && pixmap.drawable.getTexture() instanceof GPUImage
                    && ((GPUImage) pixmap.drawable.getTexture()).getHardwareBufferPtr() != 0) {
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                vr.onUpdateWindowContentDirect(window, pixmap.drawable, xOff, yOff);
                emitIdleNotify(window, pixmap, serial, idleFence, targetFps, vr);
            } else if (er != null && window.attributes.isMapped() && xOff == 0 && yOff == 0
                    && er.canPresentDirect(window, pixmap.drawable)) {
                // EGL: sample the pixmap's buffer directly instead of copying it into the window content.
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                er.onUpdateWindowContentDirect(window, pixmap.drawable, xOff, yOff);
                emitIdleNotify(window, pixmap, serial, idleFence, targetFps, vr);
            } else {
                content.copyArea((short)0, (short)0, xOff, yOff, pixmap.drawable.width, pixmap.drawable.height, pixmap.drawable);
                sendCompleteNotify(window, serial, Kind.PIXMAP, Mode.COPY, ust, msc);
                emitIdleNotify(window, pixmap, serial, idleFence, targetFps, vr);
            }
        }

        // Notify the frame-counting observer (ReShade preview pulse) — a real present just happened.
        final PresentListener pl = presentListener;
        if (pl != null) pl.onPresent();
    }

    private void selectInput(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int eventId = inputStream.readInt();
        int windowId = inputStream.readInt();
        Bitmask mask = new Bitmask(inputStream.readInt());

        Window window = client.xServer.windowManager.getWindow(windowId);
        if (window == null) throw new BadWindow(windowId);

        synchronized (events) {
            Event event = events.get(eventId);
            if (event != null) {
                if (event.window != window || event.client != client) throw new BadMatch();

                if (!mask.isEmpty()) {
                    event.mask = mask;
                }
                else events.remove(eventId);
            }
            else {
                event = new Event();
                event.id = eventId;
                event.window = window;
                event.client = client;
                event.mask = mask;
                events.put(eventId, event);
            }
        }
    }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        int opcode = client.getRequestData();
        if (syncExtension == null) syncExtension = client.xServer.getExtension(SyncExtension.MAJOR_OPCODE);

        switch (opcode) {
            case ClientOpcodes.QUERY_VERSION :
                queryVersion(client, inputStream, outputStream);
                break;
            case ClientOpcodes.PRESENT_PIXMAP:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.PIXMAP_MANAGER)) {
                    presentPixmap(client, inputStream, outputStream);
                }
                break;
            case ClientOpcodes.SELECT_INPUT:
                try (XLock lock = client.xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    selectInput(client, inputStream, outputStream);
                }
                break;
            default:
                throw new BadImplementation();
        }
    }
}
