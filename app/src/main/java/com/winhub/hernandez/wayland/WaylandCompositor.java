package com.winhub.hernandez.wayland;

import android.view.Surface;

/**
 * Embedded Wayland compositor (experimental parallel display runtime).
 *
 * Brings up a libwayland-server compositor in-process so games launched through
 * Wine's winewayland.drv can present into it (companion to the winewayland Proton
 * build). Committed frames (dmabufs from winewayland's Vulkan WSI) are composited
 * onto the given Surface via the native Vulkan present backend. Not on the default
 * X11 path.
 */
public final class WaylandCompositor {
    static {
        System.loadLibrary("bannerwayland");
    }

    private WaylandCompositor() {}

    private static volatile Runnable firstFrameListener;

    /** Register a callback fired once, when the compositor presents the first client
     *  frame to the output Surface. Used to dismiss the launch overlay in wayland mode
     *  (there is no XServer window-content hook). Runs on the compositor thread — the
     *  listener must marshal to the UI thread itself. */
    public static void setFirstFrameListener(Runnable r) { firstFrameListener = r; }

    /** Invoked from native (banner_on_first_frame) on the first present. */
    @SuppressWarnings("unused")
    static void onFirstFramePresented() {
        Runnable r = firstFrameListener;
        if (r != null) r.run();
    }

    /** The in-game performance HUD's feed in wayland mode (X11 binds the HUD to the window
     *  carrying _MESA_DRV and counts X presents; there is no X server here). */
    public interface GameListener {
        /** A window started presenting GPU frames ({@code window} describes it; {@code gpuName} is the
         *  compositor's GPU), or {@code window == null} when that window closed. Compositor thread. */
        void onGameSurface(String window, String gpuName);
        /** One GPU frame from that window. Compositor thread — keep it cheap. */
        void onGameFrame();
        /** The program behind that window, right after {@link #onGameSurface}: its Linux pid (the
         *  Wayland client's credentials) and executable name ({@code ""} when unknown). The app arms its
         *  launch-time CPU affinity on it — on X11 that comes from window events, which a Wayland session
         *  has none of. Compositor thread — marshal to the UI thread. */
        default void onGameProgram(int pid, String program) {}
    }

    private static volatile GameListener gameListener;

    public static void setGameListener(GameListener l) { gameListener = l; }

    /** Invoked from native (banner_on_game_surface). */
    @SuppressWarnings("unused")
    static void onGameSurface(String window, String gpuName) {
        GameListener l = gameListener;
        if (l != null) l.onGameSurface(window, gpuName);
    }

    private static volatile long lastFrameNanos;
    private static volatile long frameIntervalNanos;

    /**
     * How long frames of the HUD's window are taking to arrive right now, in ms, or -1 before the first.
     * It is the smoothed interval, or the time since the last frame when that is longer (a session that has stalled).
     * In a Linux session that window is gamescope's, so this follows the game's own pacing rather than the display's.
     * (From Droid-Deck/DroidDeck #70.)
     */
    public static long recentFrameIntervalMs() {
        long last = lastFrameNanos;
        if (last == 0) return -1;
        return Math.max(frameIntervalNanos, System.nanoTime() - last) / 1_000_000;
    }

    /** Invoked from native (banner_on_game_frame) for every frame of the HUD's window. */
    @SuppressWarnings("unused")
    static void onGameFrame() {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous != 0) {
            long interval = now - previous;
            long smoothed = frameIntervalNanos;
            frameIntervalNanos = smoothed == 0 ? interval : (smoothed * 3 + interval) / 4;
        }
        GameListener l = gameListener;
        if (l != null) l.onGameFrame();
    }

    /** Invoked from native (banner_on_game_program) once per game window that starts presenting. */
    @SuppressWarnings("unused")
    static void onGameProgram(int pid, String program) {
        GameListener l = gameListener;
        if (l != null) l.onGameProgram(pid, program != null ? program : "");
    }

    /** Pointer lock (zwp_pointer_constraints_v1) state, for the app's input path. */
    public interface PointerLockListener {
        /** A program locked the pointer ({@code locked}): the app must feed the compositor deltas
         *  (scene input type 6) instead of absolute positions. When the lock ends, {@code x,y} is
         *  where the pointer now is (scene = virtual-desktop coordinates) for the app to re-sync
         *  its own pointer to. Compositor thread. */
        void onPointerLock(boolean locked, int x, int y);
    }

    private static volatile PointerLockListener pointerLockListener;

    public static void setPointerLockListener(PointerLockListener l) { pointerLockListener = l; }

    /** Invoked from native (banner_on_pointer_lock). */
    @SuppressWarnings("unused")
    static void onPointerLock(boolean locked, int x, int y) {
        PointerLockListener l = pointerLockListener;
        if (l != null) l.onPointerLock(locked, x, y);
    }

    /** Clipboard text a program in the guest copied (wl_data_device / zwlr_data_control). */
    public interface ClipboardListener {
        /** {@code text} is what the guest put on its clipboard. Compositor thread. */
        void onGuestClipboardText(String text);
    }

    private static volatile ClipboardListener clipboardListener;

    public static void setClipboardListener(ClipboardListener l) { clipboardListener = l; }

    /** Invoked from native (banner_on_clipboard_text) with UTF-8 bytes. */
    @SuppressWarnings("unused")
    static void onClipboardText(byte[] utf8) {
        ClipboardListener l = clipboardListener;
        if (l != null && utf8 != null) l.onGuestClipboardText(new String(utf8, java.nio.charset.StandardCharsets.UTF_8));
    }

    /** A program pastes the Android text announced with {@link #setClipboardPending()}. */
    public interface ClipboardRequestListener {
        /** Read Android's clipboard now and answer with {@link #setClipboardText}. Compositor thread. */
        void onClipboardRequest();
    }

    private static volatile ClipboardRequestListener clipboardRequestListener;

    public static void setClipboardRequestListener(ClipboardRequestListener l) { clipboardRequestListener = l; }

    /** Invoked from native (banner_on_clipboard_request). */
    @SuppressWarnings("unused")
    static void onClipboardRequest() {
        ClipboardRequestListener l = clipboardRequestListener;
        if (l != null) l.onClipboardRequest();
        else nativeSetClipboardText(new byte[0]);
    }

    /** Text input (zwp_text_input_v3) state: a program accepts IME text, or stopped. */
    public interface TextInputListener {
        /** {@code enabled}: {@code program} (its exe name) accepts IME text; {@code x,y,w,h} is its caret
         *  rectangle in scene (virtual desktop) pixels, all 0 until the program positions one. Compositor
         *  thread. */
        void onTextInput(boolean enabled, String program, int x, int y, int w, int h);
    }

    private static volatile TextInputListener textInputListener;

    public static void setTextInputListener(TextInputListener l) { textInputListener = l; }

    /** Invoked from native (banner_on_text_input). */
    @SuppressWarnings("unused")
    static void onTextInput(boolean enabled, String program, int x, int y, int w, int h) {
        TextInputListener l = textInputListener;
        if (l != null) l.onTextInput(enabled, program, x, y, w, h);
    }

    private static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Android's clipboard text becomes the guest's selection ({@code null}/empty clears it). Any thread. */
    public static void setClipboardText(String text) { nativeSetClipboardText(utf8(text)); }

    /** Android's clipboard has text: offer it to the guest without reading it (reading shows Android's
     *  "pasted from your clipboard" notice); a paste then calls {@link ClipboardRequestListener}. Any thread. */
    public static void setClipboardPending() { nativeSetClipboardPending(); }

    /** Commit soft-keyboard text to the program accepting text input. Any thread. */
    public static void textInputCommit(String text) { if (text != null && !text.isEmpty()) nativeTextInputCommit(utf8(text)); }

    /** Composing (pre-edit) text with the caret at character index {@code cursor} (-1 = end); empty clears. */
    public static void textInputPreedit(String text, int cursor) { nativeTextInputPreedit(utf8(text), cursor, cursor); }

    /** The IME deleted {@code before} characters before and {@code after} after the caret. */
    public static void textInputDelete(int before, int after) { nativeTextInputDelete(before, after); }

    private static native void nativeSetClipboardText(byte[] utf8);
    private static native void nativeSetClipboardPending();
    private static native void nativeTextInputCommit(byte[] utf8);
    private static native void nativeTextInputPreedit(byte[] utf8, int cursorBegin, int cursorEnd);
    private static native void nativeTextInputDelete(int before, int after);

    /** Start the compositor headless (no output window) — bring-up tests only. */
    public static native void nativeStart(String xdgRuntimeDir);

    /** Folder for the next session's log (the game's log folder's wayland/); call before nativeStart*.
     *  Null/empty or unwritable = Download/Wayland-logs. */
    public static native void nativeSetSessionLogDir(String dir);

    /** Start the compositor rendering to {@code surface}. XDG_RUNTIME_DIR = an
     *  app-writable dir for the wayland socket (e.g. context.getFilesDir()).
     *  driverPath/libraryName/nativeLibDir select the Turnip driver via adrenotools
     *  (all null -> system libvulkan, which can't do dmabuf import). */
    public static native void nativeStartWithSurface(Surface surface, String xdgRuntimeDir,
                                                     String driverPath, String libraryName,
                                                     String nativeLibDir);

    /** Replace/clear the output window when the SurfaceView is (re)created/destroyed. */
    public static native void nativeSetSurface(Surface surface);

    /** Inject a pointer event into the compositor (from the SurfaceView touch listener).
     *  action: 0=down, 1=move, 2=up. x/y in compositor output space (0..1919, 0..1079). */
    public static native void nativeSendPointer(int action, int x, int y);

    /** Inject a key event. evdev = Linux input keycode (KEY_A=30…); state: 1=down, 0=up. */
    public static native void nativeSendKey(int evdev, int state);

    /** One screen refresh (Choreographer frame callback). The compositor draws the newest state
     *  once per tick, so games run unthrottled and the screen shows their latest frame. */
    public static native void nativeVsync(long frameTimeNanos);

    /** Shortcut launches: don't draw explorer's windows (desktop, taskbar, Start menu), matching the
     *  X11 renderer's unviewable "explorer.exe". Set before the compositor starts. */
    public static native void nativeSetHideShell(boolean hide);

    /** The panel's refresh rate in Hz, advertised on the Wayland output so Wine's display modes
     *  carry the real rate (games that insist on their saved 144 Hz mode find it, as on X11).
     *  Set before the compositor starts. */
    public static native void nativeSetOutputRefreshRate(float hz);

    /** Re-arm the first-frame callback: the Linux session restarts the Steam client once on a
     *  first run and the loading screen goes back up for it; the callback dismisses it again. */
    public static native void nativeResetFirstFrame();

    /** Show a single fullscreen window on its own Android layer (SurfaceControl) instead of blitting
     *  it into the compositor's swapchain. BANNER_WAYLAND_ZERO_COPY=1 in the container's environment
     *  variables is the launch default; see waylandcomp/ZERO_COPY_SPIKE.md.
     *
     *  Live and thread-safe: before the compositor starts this is the initial state, and afterwards
     *  the call is marshalled onto the compositor thread, which flips the state, tells every bound
     *  game over banner_ahb_v1.mode to rebuild its swapchain for it, and redraws. The switch is one
     *  frame's worth of latency; the picture never goes black, because the old swapchain's buffers
     *  keep being shown until the game has replaced them. */
    public static native void nativeSetZeroCopy(boolean on);

    /** Zero-copy frames the compositor presented in its LAST completed 10 s stats window (the
     *  "| N zero-copy frames" figure of its session-log stats line); 0 while zero-copy is off or
     *  before the first window closes. Read-only, any thread — the in-game drawer polls it. */
    public static native int nativeZeroCopyFrames();

    /** Milliseconds since the compositor last put a game frame on the display layer without a copy;
     *  -1 if it never has this session. Unlike nativeZeroCopyFrames this updates on every such frame
     *  rather than once per 10 s window, so the drawer can tell "switching..." from "running" within
     *  a frame or two of the live toggle. Read-only, any thread. */
    public static native int nativeZeroCopyLastFrameAgeMs();

    /** Compressed (UBWC) game buffers: the compositor advertises DRM_FORMAT_MOD_QCOM_COMPRESSED next to
     *  LINEAR on zwp_linux_dmabuf_v1 for every format its driver can import that way, so the game's
     *  Turnip allocates compressed swapchain images instead of resolving every frame to a linear copy.
     *  Default on; BANNER_WAYLAND_UBWC=0 in the container's environment variables turns it off (A/B).
     *  Set before the compositor starts. */
    public static native void nativeSetUbwc(boolean on);

    /** The compositor's asynchronous copy path: a presented frame stays in flight on the GPU and the
     *  compositor thread no longer waits for its own screen blit (which queued behind the game's work
     *  and paced every game to that wait). Default on; BANNER_WAYLAND_ASYNC_COPY=0 in the container's
     *  environment variables restores the synchronous path (A/B). Set before the compositor starts. */
    public static native void nativeSetAsyncCopy(boolean on);

    /** Zero-copy acquire fences the game's driver attaches itself (banner_ahb_v1 version 3): on kernels without
     *  the dma-buf sync_file ioctls the compositor cannot export a fence from the game's buffer and the display
     *  could show a frame still being drawn; a version 3 driver sends its render-complete fence instead. Default
     *  on; BANNER_WAYLAND_ZC_CLIENT_FENCE=0 in the container's environment variables makes the compositor ignore
     *  those fences (A/B; behaves like a version 2 driver). Any time. */
    public static native void nativeSetZeroCopyClientFence(boolean on);

    /** Driver capability probe (native driver_probe.c). Creates its own Vulkan instance and device on the driver
     *  at {@code libPath} (a Turnip .so, loaded through adrenotools exactly as the compositor loads its own;
     *  null = the system libvulkan), measures what the Wayland path depends on - sync_fd fences/semaphores, DRM
     *  modifiers, global queue priority, the KGSL zero-timeout fence bug, the dma-buf sync_file ioctls, AHB export -
     *  and destroys everything before returning. Safe before, beside or without a running compositor; takes up to
     *  about 2 s, so call it off the UI thread. Returns one JSON object (schema in driver_probe.h); on any failure
     *  {@code ok} is false and {@code error} says why, with whatever was measured before it. */
    public static native String nativeProbeDriver(String libPath);

    /** How a program window the compositor focuses by itself (a new window, or the one left when the
     *  focused window closes) is made Wine's foreground window, so a game that pauses unfocused starts
     *  without a tap: {@link #AUTO_ACTIVATE_BRING_TO_FRONT} (default; {@link BringToFrontListener} →
     *  winhandler.exe), {@link #AUTO_ACTIVATE_CLICK} (one synthetic click at its top-left corner) or
     *  {@link #AUTO_ACTIVATE_OFF}. From BANNER_WAYLAND_AUTO_ACTIVATE (0 / click / default). Set before
     *  the compositor starts. */
    public static native void nativeSetAutoActivate(int mode);

    public static final int AUTO_ACTIVATE_OFF = 0;
    public static final int AUTO_ACTIVATE_BRING_TO_FRONT = 1;
    public static final int AUTO_ACTIVATE_CLICK = 2;

    /** The compositor focused a program window by itself and wants it foreground inside Wine. */
    public interface BringToFrontListener {
        /** {@code exe}: the program's exe name, lower case; {@code hwnd}: its Win32 handle (0 =
         *  unknown). Compositor thread — only queue work here. */
        void onBringToFront(String exe, long hwnd);
    }

    private static volatile BringToFrontListener bringToFrontListener;

    public static void setBringToFrontListener(BringToFrontListener l) { bringToFrontListener = l; }

    /** Invoked from native (banner_on_bring_to_front). */
    @SuppressWarnings("unused")
    static void onBringToFront(String exe, long hwnd) {
        BringToFrontListener l = bringToFrontListener;
        if (l != null) l.onBringToFront(exe != null ? exe : "", hwnd);
    }

    /** Debug: advertise no DRM device (main device 0:0) in the dma-buf feedback, as a phone that
     *  exposes no /dev/dri node to apps does. BANNER_WAYLAND_NO_RENDER_NODE=1 in the container's or
     *  shortcut's environment variables. Set before the compositor starts. */
    public static native void nativeSetNoRenderNode(boolean on);

    /** VRR / refresh-rate matching: mirror the panel refresh-rate vote the activity puts on the
     *  compositor's SurfaceView onto the game's own SurfaceControl layer. Under zero-copy the game's
     *  frames go straight onto that layer and never reach the app's surface, so the surface vote alone
     *  does not tell SurfaceFlinger the game's cadence. Same rate and compatibility as
     *  {@code Surface.setFrameRate}; {@code 0} clears the vote. Callable any time from any thread; the
     *  compositor applies it on its next layer transaction and writes one `layer` line per change to
     *  the session log. A no-op on Android versions without {@code ASurfaceTransaction_setFrameRate}. */
    public static native void nativeSetLayerFrameRate(float hz);

    /** Write one line into the compositor's session log (the game's log folder, wayland/) under the "display"
     *  area, from Java. Used for facts the platform knows and the native side does not — the panel's
     *  HDR capability, which lives behind android.view.Display. Safe before the compositor thread is
     *  up (the line then only reaches logcat) and safe after it has gone. */
    public static native void nativeLogDisplay(String message);

    /** The same under the "perf" area (e.g. the CPU cores Prefer Big Cores picked). Same safety. */
    public static native void nativeLogPerf(String message);

    /** The container's screen size, advertised as the Wayland output's mode so Wine's display-mode
     *  list stops at the desktop size, as the X server's does on X11. Set before the compositor starts. */
    public static native void nativeSetOutputSize(int width, int height);

    // ── HDR10 output, round 1 (waylandcomp/src/banner_color.h, wl_color_mgmt.c) ─────────────────
    // Opt-in: BANNER_WAYLAND_HDR=1 in the container's or shortcut's env vars. The compositor offers
    // games HDR10 (wp_color_manager_v1 + 10-bit buffers, frames tagged BT2020_PQ on the game's own
    // display layer) only when the game's display lists HDR10 as well; otherwise nothing changes and
    // the session log says why. Not the drawer's "HDR" effect, which is an SDR bloom/contrast filter.

    /** HDR_MODE_OFF / HDR_MODE_ON (BANNER_WAYLAND_HDR=1) / HDR_MODE_FORCE (=force: skip the display
     *  check, for testing the negotiation on an SDR panel). */
    public static final int HDR_MODE_OFF = 0, HDR_MODE_ON = 1, HDR_MODE_FORCE = 2;

    /** The opt-in: mode, where it came from ("container env" / "shortcut env"), whether DXVK_HDR=1 is
     *  in the game's environment, and whether the app turned zero-copy on for this session because HDR
     *  needs it. Set before the compositor starts. */
    public static native void nativeSetHdrRequest(int mode, String source, boolean dxvkHdr, boolean zeroCopyForced);

    /** The game's display as {@code android.view.Display} reports it. Before the compositor starts (it
     *  feeds the gate) and again whenever it changes (logged; the gate is decided once per session). */
    public static native void nativeSetHdrDisplay(int displayId, String name, String formats, boolean hdr10,
                                                  float maxLuminance, float maxAverageLuminance, float minLuminance,
                                                  boolean hdrSdrRatioAvailable, float hdrSdrRatio, int apiLevel);

    /** One {@code Display.getHdrSdrRatio()} reading ({@code < 0} = not available); {@code listener} = it
     *  came from the display's ratio listener rather than the periodic sampler. Any thread. */
    public static native void nativeHdrSdrRatioSample(float ratio, boolean listener);

    /** Milliseconds since an HDR frame last reached a display layer; -1 = none this session. Any thread. */
    public static native int nativeHdrLastFrameAgeMs();

    /** -1 = the compositor has not decided the HDR gate yet, 0 = closed, 1 = open. Any thread. */
    public static native int nativeHdrGateState();

    /** The session is ending: the compositor writes its "HDR on screen: …" summary line. Once. */
    public static native void nativeHdrSessionEnd();

    /** True while HDR frames are really on screen: frames tagged BT2020_PQ reached the display in the
     *  last 1.5 s and, where the display reports an HDR/SDR ratio, it is above 1 (the HUD's badge). */
    public static native boolean nativeHdrOnScreen();

    /** One "color" line in the session log, from Java (the HDR environment, the DXVK warning). */
    public static native void nativeLogColor(String message);

    /** One line in the session log under {@code area} (the log's 9-character column: "gpu", "nvapi"),
     *  from Java: launch facts the app decides (the GPU name spoof, the Unreal Engine HDR mode). Like
     *  {@link #nativeLogDisplay}, safe before the session file exists (the line is written when it
     *  opens) and after it has gone. */
    public static native void nativeLog(String area, String message);

    /** Nits at which SDR content is placed when the compositor composes an HDR picture (a window over
     *  an HDR game, the desktop around a windowed one). Default 203 (BT.2408). Before the start. */
    public static native void nativeSetHdrSdrWhite(float nits);

    /** The in-game drawer's HDR output switch (only meaningful while the HDR gate is open; per session,
     *  starts on). On = the game's HDR frames go to the display as HDR; off = the same frames are shown
     *  tone-mapped to SDR. Live, any thread (queued to the compositor, which logs the flip); the game is
     *  told nothing - DXVK_HDR and the colour-manager offer were decided at launch. */
    public static native void nativeSetHdrOutput(boolean on);

    /** The HDR output switch's current state (any thread). */
    public static native boolean nativeHdrOutput();

    /** True while an HDR game's frames are being shown tone-mapped to SDR (the last one under 1.5 s ago). */
    public static native boolean nativeHdrToneMappedOnScreen();

    /** 0 = no HDR frames on screen (or not confirmed yet), 1 = HDR frames on screen with HDR headroom,
     *  2 = HDR frames on screen but the display has given them no headroom (HDR/SDR ratio 1.00) for 5 s
     *  or more - the brightness slider at maximum, or a screen recording (Android turns HDR headroom off
     *  while the screen is recorded). The HUD badge and the drawer's HDR row. */
    public static native int nativeHdrState();

    /** Device evidence beside the HDR/SDR headroom, HDR sessions only (any thread): PowerManager thermal
     *  status (0..6, -1 unknown) and getThermalHeadroom(10) (NaN / negative = not available), and
     *  Settings.System SCREEN_BRIGHTNESS (0..255, -1 unknown) + SCREEN_BRIGHTNESS_MODE (1 auto, 0 manual,
     *  -1 unknown). The compositor logs changes of status and brightness and puts the values on every
     *  no-headroom line, the 10 s HDR line and the verdict. */
    public static native void nativeHdrEnvSample(int thermalStatus, float thermalHeadroom, int brightness,
                                                 int brightnessMode);

    /** Display.getHighestHdrSdrRatio() (Android 16+), <= 0 = not reported: the display's own ceiling, capped
     *  into the headroom request and logged in the verdict. Any thread. */
    public static native void nativeSetHdrHighestRatio(float ratio);

    /** The HDR headroom the SCREEN surface should ask for while HDR frames go through the HDR10 swapchain
     *  (frame generation), 0 = none; the app applies it (SurfaceView.setDesiredHdrHeadroom, API 35). */
    public static native float nativeHdrScreenHeadroom();

    /** Why that value ("content peak 1207 nits (max CLL) / SDR 203 ..."), for the log line. */
    public static native String nativeHdrScreenHeadroomWhy();

    /** What the app asked for on the screen surface: > 0 the ratio, 0 cleared, -1 not possible (< Android 15). */
    public static native void nativeHdrNoteHeadroomRequest(float ratio);

    /** Fullscreen mode ({@code Container.FULLSCREEN_OFF/FIT/STRETCH/FILL/INTEGER}) and screen alignment
     *  ({@code Container.ALIGN_CENTER/TOP/BOTTOM}): how the compositor fits the desktop onto the screen,
     *  with the same arithmetic as {@code ViewTransformation} (which maps touch input), so the picture and
     *  the pointer agree. OFF and FIT both letterbox. Callable any time; the next frame uses it. */
    public static native void nativeSetScaleMode(int fullscreenMode, int screenAlignment);

    // ---- Screen effects: the X11 Vulkan renderer's post chain, run by the compositor between the
    // composited scene and the output blit (waylandcomp/src/effects_chain.c). Same modes, ranges and
    // pass order as VulkanRenderer's setters, so a saved preset looks the same on both backends.
    // Callable any time from any thread; the compositor applies them on its next frame and writes
    // one `effects` line to the session log per change. While any effect is on, a zero-copy
    // (layer-mode) game is presented through the compositor pass instead.

    /** Scaling mode: 0=None 1=Linear 2=Nearest 3=SGSR 4=FSR 5=FSR Fit 6=Sharpen 7=NIS 8=SGSR HQ.
     *  Resizes the scene to its mapped output size (there is no render scale on Wayland). */
    public static native void nativeSetUpscaler(int mode);

    /** The upscaler's own sharpness slider 0..100 (RCAS lobe scale, SGSR edge 0.5..4.5, NIS). */
    public static native void nativeSetUpscaleSharpness(int sharpness);

    /** AMD CAS sharpen toggle + level 0..100 (0 = the pass is off). */
    public static native void nativeSetCas(boolean enabled, int sharpness);

    /** Fake-HDR toggle. */
    public static native void nativeSetHdr(boolean enabled);

    /** Terminal debanding toggle + strength 0..200 (100 = 1 LSB). */
    public static native void nativeSetDeband(boolean enabled, int strength);

    /** Colour grade in the drawer's slider units (brightness/contrast -100..100, gamma 0.5..3.0,
     *  saturation 0..200 percent; 0/0/1/100 = neutral, pass off) plus the FXAA / Toon / CRT / NTSC
     *  toggles — the same signature as {@code VulkanRenderer.setScreenEffects}. */
    public static native void nativeSetScreenEffects(float brightness, float contrast, float gamma, float saturation,
                                                     boolean fxaa, boolean toon, boolean crt, boolean ntsc);

    /** The Look the controls currently match ({@code null} = Custom); only named in the log line. */
    public static native void nativeSetLookName(String name);

    /** The in-game FPS limiter: frames per second, 0 = unlimited. Paces when replaced buffers go
     *  back to the game, like the X11 IdleNotify pacer, so the game itself slows to the cap. */
    public static native void nativeSetFpsLimit(int fps);

    /** Inject the app's X-server input in scene (virtual desktop) pixels. type 2 = move to a,b;
     *  3 = evdev button a (BTN_LEFT=0x110…) pressed (b=1) or released (b=0); 4 = a wheel steps,
     *  negative = up. */
    public static native void nativeSendSceneInput(int type, int a, int b);

    // ── Frame generation (waylandcomp/src/framegen_bridge.c) ─────────────────────────────────
    // LSFG Native and Win-FG Native run inside the compositor's Turnip device, the same engines
    // the X11 VulkanRenderer hosts. Every setter only stores a value; the compositor thread applies
    // it on its next frame, so they are safe from any thread and before the compositor starts.
    // bionic-fg (the guest-side win-fg layer) is X11-only and has no Wayland counterpart.
    public static final int FG_ENGINE_LSFG = 0, FG_ENGINE_WINFG = 1;

    /** Which native engine generates: {@link #FG_ENGINE_LSFG} or {@link #FG_ENGINE_WINFG}. */
    public static native void nativeSetFrameGenEngine(int kind);

    /** Arm (multiplier 2..4: one real frame plus multiplier-1 interpolated ones per game frame) or
     *  disarm. Generated frames are presented ahead of the real frame on consecutive vblanks. */
    public static native void nativeSetFrameGenArmed(boolean armed, int multiplier);

    /** LSFG Native: the SPIR-V cache built from the user's Lossless.dll ({@code LsfgNative.cacheFile}). */
    public static native void nativeSetLsfgCachePath(String path);

    /** Flow scale (0.25-1.0) and the panel's real refresh rate (the pacer never generates above it). */
    public static native void nativeSetFrameGenTuning(float flowScale, float refreshHz);

    /** Win-FG Native only: interpolation model (3/4) and performance preset (0..2). */
    public static native void nativeSetWinFgTuning(int model, int perfPreset);

    /** Same codes as {@code VulkanRenderer.getFrameGenProblem()}: -1 not known yet (the compositor's
     *  device is not up), 0 fine, 1 the driver lacks what the selected engine needs
     *  ({@link #nativeFrameGenCapsReason()} says what), 2 the engine failed to start. */
    public static native int nativeFrameGenProblem();
    public static native String nativeFrameGenCapsReason();

    /** Same shape as {@code VulkanRenderer.getFrameGenStats()}: {generations trusted, generations
     *  planned, real fps, presented fps (generated frames included), thermal (-1 = none),
     *  GPU ms per generated frame (-1 = unknown)}. */
    public static native float[] nativeFrameGenStats();

    /** Relative pointer motion by dx,dy scene pixels (the Relative Mouse / captured-mouse path):
     *  while a program holds a pointer lock this is what it receives as relative_motion; otherwise
     *  the compositor moves its pointer by the delta. */
    /**
     * The guest's pointer image, straight from wl_pointer.set_cursor.
     * out = [serial, hidden, w, h, hotspotX, hotspotY, ARGB8888 pixels...]; returns the number of
     * ints written, or 0 if {@code out} is too small. Poll [0] (the serial) and only rebuild when it
     * changes; [1] = 1 means the guest asked for NO pointer, which is what a mouse-look game does.
     */
    public static int cursorSnapshot(int[] out) { return nativeCursorSnapshot(out); }

    private static native int nativeCursorSnapshot(int[] out);

    /** Enough for the header plus a 128x128 cursor, the largest the compositor snapshots. */
    public static final int CURSOR_BUF_INTS = 6 + 128 * 128;

    /** Touch actions for {@link #sendTouch}. */
    public static final int TOUCH_DOWN = 0, TOUCH_MOVE = 1, TOUCH_UP = 2, TOUCH_CANCEL = 3;

    /**
     * One finger, as real wl_touch rather than a synthesised mouse: the guest sees fingers with
     * their own ids, so multi-touch works. x/y are output-space (0..1919, 0..1079); a finger stays
     * with the surface it went down on until it lifts, as the protocol requires.
     */
    public static void sendTouch(int action, int id, int x, int y) {
        nativeSendTouch(action, id, x, y);
    }

    private static native void nativeSendTouch(int action, int id, int x, int y);

    public static void sendPointerDelta(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        nativeSendSceneInput(6, dx * 256, dy * 256);
    }
}
