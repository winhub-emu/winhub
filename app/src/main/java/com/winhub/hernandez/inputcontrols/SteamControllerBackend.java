package com.winhub.hernandez.inputcontrols;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import org.libsdl.app.HIDDeviceManager;
import org.libsdl.app.SDL;

import java.util.Arrays;

/**
 * Optional Steam Controller support (Input Controls → Device → Steam Controller, off by default).
 *
 * The Steam Controller (the 2026 model, and the 2015 one over Bluetooth LE) speaks a Valve-specific
 * protocol: out of the box it acts as a keyboard + mouse ("lizard mode") and Android never reports a
 * gamepad, so the normal InputDevice path can't use it. SDL3's HIDAPI Steam drivers speak that
 * protocol: they open the pad over Bluetooth LE (a bonded "Steam Ctrl…" device) or USB (cable, or the
 * 2026 model's wireless puck), keep lizard mode off and parse the reports.
 *
 * SDL runs on a poll thread. Each pad reaches the main thread as an {@link ExternalController} with a
 * synthetic deviceId ({@link #DEVICE_ID_BASE} - SDL instance id), which WinHandler seats in the normal
 * XInput slot machinery like any hot-plugged pad (see WinHandler's "Steam Controller (SDL) pads").
 */
public final class SteamControllerBackend {
    private static final String TAG = "SteamControllerBackend";

    public static final int VALVE_VENDOR_ID = 0x28DE;
    /** Synthetic deviceIds are DEVICE_ID_BASE - SDL instance id: never an Android id (>= 0), never
     *  OSC's -1 and never ExternalController's -1 "not resolved yet". */
    public static final int DEVICE_ID_BASE = -1000;

    private static final long POLL_INTERVAL_MS = 4; // the 2026 pad reports at ~250 Hz
    // Trackpad mouse: pixels moved by one full swipe across a pad.
    private static final float TRACKPAD_PIXELS_PER_PAD = 900f;
    private static final float TRIGGER_FULL = 0.98f; // L2/R2 "pressed" like the Android path's == 1.0

    // Array layouts and button bits — keep in sync with steam_controller_bridge.cpp.
    private static final int MAX_PADS = 4;
    private static final int I_ID = 0, I_BUTTONS = 1, I_STRIDE = 2;
    private static final int F_LX = 0, F_LY = 1, F_RX = 2, F_RY = 3, F_LT = 4, F_RT = 5;
    private static final int F_RPAD_DOWN = 6, F_LPAD_DOWN = 9, F_STRIDE = 12; // each pad: down, x, y
    private static final int B_A = 0, B_B = 1, B_X = 2, B_Y = 3, B_LB = 4, B_RB = 5, B_BACK = 6,
            B_START = 7, B_LSTICK = 8, B_RSTICK = 9, B_GUIDE = 10, B_DPAD_UP = 11, B_DPAD_DOWN = 12,
            B_DPAD_LEFT = 13, B_DPAD_RIGHT = 14, B_QAM = 15, B_R4 = 16, B_L4 = 17, B_R5 = 18, B_L5 = 19,
            B_RPAD_CLICK = 20, B_LPAD_CLICK = 21;

    /** Which trackpad(s) move the mouse. A mouse pad's click is a left click; with BOTH, the left pad
     *  clicks right so both buttons are there. */
    public static final int TRACKPAD_MOUSE_OFF = 0, TRACKPAD_MOUSE_RIGHT = 1, TRACKPAD_MOUSE_LEFT = 2,
            TRACKPAD_MOUSE_BOTH = 3;

    /** Mappable extra buttons in settings order: L4 (upper left), L5 (lower left), R4 (upper right),
     *  R5 (lower right), then the "…" Quick Access button between the trackpads. None has an Xbox
     *  equivalent, so they're mapped here rather than through the profile bindings. */
    public static final int PADDLE_COUNT = 5;
    private static final int[] PADDLE_BITS = { B_L4, B_L5, B_R4, B_R5, B_QAM };

    // Face/shoulder/menu/stick-click bits -> the Android keycodes the profile bindings are keyed on.
    // D-pad, sticks and triggers reach the bindings through the pad state instead (hat / axis codes).
    private static final int[][] BUTTON_KEYCODES = {
            { B_A, android.view.KeyEvent.KEYCODE_BUTTON_A },
            { B_B, android.view.KeyEvent.KEYCODE_BUTTON_B },
            { B_X, android.view.KeyEvent.KEYCODE_BUTTON_X },
            { B_Y, android.view.KeyEvent.KEYCODE_BUTTON_Y },
            { B_LB, android.view.KeyEvent.KEYCODE_BUTTON_L1 },
            { B_RB, android.view.KeyEvent.KEYCODE_BUTTON_R1 },
            { B_BACK, android.view.KeyEvent.KEYCODE_BUTTON_SELECT },
            { B_START, android.view.KeyEvent.KEYCODE_BUTTON_START },
            { B_LSTICK, android.view.KeyEvent.KEYCODE_BUTTON_THUMBL },
            { B_RSTICK, android.view.KeyEvent.KEYCODE_BUTTON_THUMBR },
            { B_GUIDE, android.view.KeyEvent.KEYCODE_BUTTON_MODE },
    };

    /** Main-thread callbacks. */
    public interface Listener {
        void onSteamPadConnected(ExternalController pad);

        void onSteamPadDisconnected(ExternalController pad);

        /** pad.state changed (extra buttons mapped to gamepad buttons already merged in). guideDown is
         *  the Steam button, also carried in pad.state as IDX_BUTTON_MODE; quickAccessDown is the physical "…"
         *  button (for the controller test). pressedKeyCodes are the held buttons as Android keycodes,
         *  for the profile bindings. */
        void onSteamPadState(ExternalController pad, boolean guideDown, boolean quickAccessDown, int[] pressedKeyCodes);

        /** A back button mapped to a keyboard key or mouse button went down / up. */
        void onSteamPadBinding(Binding binding, boolean down);

        /** A mouse trackpad moved while touched (only when a trackpad mouse mode is on). */
        void onSteamPadMouseMove(int dx, int dy);

        /** A mouse trackpad clicked / released. secondary = the right mouse button (the left pad when
         *  both pads are mice); otherwise the left mouse button. */
        void onSteamPadMouseButton(boolean secondary, boolean down);
    }

    private static boolean librariesLoaded;
    private static boolean jniReady; // SDL.setupJNI() re-creates SDL's mutexes, so once per process
    // SDL is one per process: the game session and the settings test dialog can't both own it.
    private static SteamControllerBackend running_;

    private final Activity activity;
    private final Listener listener;
    private final int trackpadMode;
    private final Binding[] paddleBindings = new Binding[PADDLE_COUNT];
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private HIDDeviceManager hidManager;
    private Thread pollThread;
    private volatile boolean running;

    // Poll thread -> main thread hand-off: the poll thread publishes its newest frame and posts the
    // apply runnable only when none is queued, so a busy main thread coalesces frames. The main thread
    // never calls into SDL (the poll thread holds the bridge lock during SDL updates).
    private final Object frameLock = new Object();
    private int[] frameInts;
    private float[] frameFloats;
    private String[] frameNames;
    private String[] framePaths;
    private int frameCount;
    private boolean applyQueued;
    private final Runnable applyFrame = this::applyFrame;

    // Main-thread state.
    private final SparseArray<Pad> pads = new SparseArray<>();

    private static final class Pad {
        final ExternalController controller;
        int buttons = -1; // force the first frame through
        final float[] axes = new float[6];
        final Trackpad right = new Trackpad();
        final Trackpad left = new Trackpad();
        final boolean[] paddleDown = new boolean[PADDLE_COUNT];

        Pad(ExternalController controller) {
            this.controller = controller;
        }
    }

    private static final class Trackpad {
        boolean down, clickDown;
        float x, y, accX, accY;
    }

    /** trackpadMode: TRACKPAD_MOUSE_*. paddles: what each extra button does (L4, L5, R4, R5, "…"; null / NONE = nothing). Gamepad targets are
     *  merged into the pad state; keyboard / mouse targets are reported through onSteamPadBinding. */
    public SteamControllerBackend(Activity activity, int trackpadMode, Binding[] paddles, Listener listener) {
        this.activity = activity;
        this.trackpadMode = (trackpadMode < TRACKPAD_MOUSE_OFF || trackpadMode > TRACKPAD_MOUSE_BOTH)
                ? TRACKPAD_MOUSE_RIGHT : trackpadMode;
        this.listener = listener;
        for (int i = 0; i < PADDLE_COUNT; i++) {
            Binding b = paddles != null && i < paddles.length ? paddles[i] : null;
            paddleBindings[i] = (b == null || b == Binding.SHOW_ANDROID_KEYBOARD) ? Binding.NONE : b;
        }
    }

    /** True when SDL may use Bluetooth: BLUETOOTH_CONNECT on Android 12+, BLUETOOTH below. */
    public static boolean hasBluetoothPermission(Context context) {
        String permission = Build.VERSION.SDK_INT >= 31
                ? Manifest.permission.BLUETOOTH_CONNECT : Manifest.permission.BLUETOOTH;
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Main thread. Returns false (and leaves the normal input path untouched) if SDL can't load. */
    public boolean start() {
        if (running)
            return true;
        if (running_ != null && running_ != this) {
            Log.i(TAG, "Taking SDL over from the previous owner");
            running_.stop();
        }
        if (!loadLibraries())
            return false;
        try {
            if (!jniReady) {
                SDL.setupJNI();
                jniReady = true;
            }
            SDL.initialize();
            SDL.setContext(activity);
            hidManager = HIDDeviceManager.acquire(activity);
        } catch (Throwable t) {
            Log.e(TAG, "SDL Java setup failed; Steam Controller support stays off", t);
            return false;
        }
        // Without the permission SDL would request it itself from the poll thread (SDLActivity.
        // requestPermission). Never prompt in-game: run USB-only instead, the setting asks for it.
        final boolean bluetooth = hasBluetoothPermission(activity);
        running = true;
        running_ = this;
        pollThread = new Thread(() -> pollLoop(bluetooth), "SteamCtrlPoll");
        pollThread.start();
        Log.i(TAG, "Started (bluetooth " + bluetooth + ", trackpad mouse mode " + trackpadMode + ")");
        return true;
    }

    /** Main thread, at session teardown. Silent: no disconnect callbacks, the session is ending. */
    public void stop() {
        if (pollThread == null)
            return;
        running = false;
        try {
            pollThread.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (pollThread.isAlive())
            Log.w(TAG, "Poll thread still shutting SDL down after 1.5 s");
        pollThread = null;
        if (running_ == this)
            running_ = null;
        mainHandler.removeCallbacks(applyFrame);
        pads.clear();
        if (hidManager != null) {
            HIDDeviceManager.release(hidManager);
            hidManager = null;
        }
        SDL.setContext(null);
        Log.i(TAG, "Stopped");
    }

    /** Any thread (the vibration listener). low/high are raw 0..65535 XInput motor values. */
    public void rumble(int deviceId, int low, int high, int durationMs) {
        if (running)
            nativeRumble(DEVICE_ID_BASE - deviceId, low, high, durationMs);
    }

    private static synchronized boolean loadLibraries() {
        if (librariesLoaded)
            return true;
        try {
            System.loadLibrary("SDL3"); // explicitly first: SDL's JNI_OnLoad registers its natives
            System.loadLibrary("steamctrl");
            librariesLoaded = true;
        } catch (Throwable t) {
            Log.e(TAG, "Could not load SDL3 / steamctrl; Steam Controller support stays off", t);
        }
        return librariesLoaded;
    }

    // ---- poll thread ----

    private void pollLoop(boolean bluetooth) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        if (!nativeInit(bluetooth)) {
            Log.w(TAG, "SDL init failed; Steam Controller support stays off this session");
            running = false;
            return;
        }
        int[] ints = new int[MAX_PADS * I_STRIDE];
        float[] floats = new float[MAX_PADS * F_STRIDE];
        String[] names = new String[MAX_PADS];
        String[] paths = new String[MAX_PADS];
        int[] lastInts = new int[ints.length];
        float[] lastFloats = new float[floats.length];
        int lastCount = 0;
        SparseArray<String[]> identities = new SparseArray<>(); // SDL id -> {name, path}
        while (running) {
            int count = nativePoll(ints, floats);
            if (count < 0)
                break;
            // Idle pads produce identical frames; only hand real changes to the main thread.
            if (count != lastCount || !Arrays.equals(ints, lastInts) || !Arrays.equals(floats, lastFloats)) {
                for (int p = 0; p < count; p++) {
                    int id = ints[p * I_STRIDE + I_ID];
                    String[] identity = identities.get(id);
                    if (identity == null) {
                        identity = new String[] { nativeGetName(id), nativeGetPath(id) };
                        identities.put(id, identity);
                    }
                    names[p] = identity[0];
                    paths[p] = identity[1];
                }
                publish(ints, floats, names, paths, count);
                System.arraycopy(ints, 0, lastInts, 0, ints.length);
                System.arraycopy(floats, 0, lastFloats, 0, floats.length);
                lastCount = count;
            }
            SystemClock.sleep(POLL_INTERVAL_MS);
        }
        nativeShutdown();
    }

    private void publish(int[] ints, float[] floats, String[] names, String[] paths, int count) {
        synchronized (frameLock) {
            frameInts = ints.clone();
            frameFloats = floats.clone();
            frameNames = names.clone();
            framePaths = paths.clone();
            frameCount = count;
            if (applyQueued)
                return;
            applyQueued = true;
        }
        mainHandler.post(applyFrame);
    }

    // ---- main thread ----

    private void applyFrame() {
        int[] ints;
        float[] floats;
        String[] names;
        String[] paths;
        int count;
        synchronized (frameLock) {
            ints = frameInts;
            floats = frameFloats;
            names = frameNames;
            paths = framePaths;
            count = frameCount;
            applyQueued = false;
        }
        if (!running || ints == null)
            return;

        for (int i = pads.size() - 1; i >= 0; i--) {
            int id = pads.keyAt(i);
            boolean present = false;
            for (int p = 0; p < count; p++) {
                if (ints[p * I_STRIDE + I_ID] == id) { present = true; break; }
            }
            if (!present) {
                Pad pad = pads.valueAt(i);
                pads.removeAt(i);
                releaseHeld(pad);
                Log.i(TAG, "Disconnected: " + pad.controller.getName() + " (" + pad.controller.getId() + ")");
                listener.onSteamPadDisconnected(pad.controller);
            }
        }

        for (int p = 0; p < count; p++) {
            int id = ints[p * I_STRIDE + I_ID];
            Pad pad = pads.get(id);
            if (pad == null) {
                pad = new Pad(createController(id, names[p], paths[p]));
                pads.put(id, pad);
                Log.i(TAG, "Connected: " + pad.controller.getName() + " (" + pad.controller.getId()
                        + ", deviceId " + pad.controller.getDeviceId() + ")");
                listener.onSteamPadConnected(pad.controller);
            }
            applyPad(pad, ints[p * I_STRIDE + I_BUTTONS], floats, p * F_STRIDE);
        }
    }

    private static ExternalController createController(int sdlId, String name, String path) {
        ExternalController controller = new ExternalController();
        if (name == null || name.isEmpty())
            name = "Steam Controller";
        controller.setName(name);
        // Stable per controller (BLE path = "SteamController.<MAC>"), so Players-tab pins persist.
        controller.setId("sdl:" + (path != null && !path.isEmpty() ? path : name + "#" + sdlId));
        controller.setDeviceId(DEVICE_ID_BASE - sdlId);
        return controller;
    }

    private void applyPad(Pad pad, int buttons, float[] floats, int base) {
        boolean changed = buttons != pad.buttons;
        for (int a = 0; a < pad.axes.length; a++) {
            if (pad.axes[a] != floats[base + a]) {
                pad.axes[a] = floats[base + a];
                changed = true;
            }
        }
        if (changed) {
            pad.buttons = buttons;
            // Back buttons: gamepad targets act as that button (so profile bindings see them too);
            // keyboard / mouse targets fire on their own edges.
            int effective = buttons;
            float lt = floats[base + F_LT];
            float rt = floats[base + F_RT];
            for (int i = 0; i < PADDLE_COUNT; i++) {
                boolean down = bit(buttons, PADDLE_BITS[i]);
                Binding target = paddleBindings[i];
                if (target == Binding.NONE) {
                    pad.paddleDown[i] = down;
                    continue;
                }
                if (target.isGamepad()) {
                    if (down) {
                        if (target == Binding.GAMEPAD_BUTTON_L2) lt = 1f;
                        else if (target == Binding.GAMEPAD_BUTTON_R2) rt = 1f;
                        else effective |= gamepadTargetBits(target);
                    }
                } else if (down != pad.paddleDown[i]) {
                    listener.onSteamPadBinding(target, down);
                }
                pad.paddleDown[i] = down;
            }

            GamepadState s = pad.controller.state;
            pad.controller.applyRawSticks(floats[base + F_LX], floats[base + F_LY],
                    floats[base + F_RX], floats[base + F_RY]);
            s.triggerL = lt;
            s.triggerR = rt;
            s.setPressed(ExternalController.IDX_BUTTON_A, bit(effective, B_A));
            s.setPressed(ExternalController.IDX_BUTTON_B, bit(effective, B_B));
            s.setPressed(ExternalController.IDX_BUTTON_X, bit(effective, B_X));
            s.setPressed(ExternalController.IDX_BUTTON_Y, bit(effective, B_Y));
            s.setPressed(ExternalController.IDX_BUTTON_L1, bit(effective, B_LB));
            s.setPressed(ExternalController.IDX_BUTTON_R1, bit(effective, B_RB));
            s.setPressed(ExternalController.IDX_BUTTON_SELECT, bit(effective, B_BACK));
            s.setPressed(ExternalController.IDX_BUTTON_START, bit(effective, B_START));
            s.setPressed(ExternalController.IDX_BUTTON_L3, bit(effective, B_LSTICK));
            s.setPressed(ExternalController.IDX_BUTTON_R3, bit(effective, B_RSTICK));
            s.setPressed(ExternalController.IDX_BUTTON_L2, s.triggerL >= TRIGGER_FULL);
            s.setPressed(ExternalController.IDX_BUTTON_R2, s.triggerR >= TRIGGER_FULL);
            s.setPressed(ExternalController.IDX_BUTTON_MODE, bit(effective, B_GUIDE));
            s.dpad[0] = bit(effective, B_DPAD_UP);
            s.dpad[1] = bit(effective, B_DPAD_RIGHT);
            s.dpad[2] = bit(effective, B_DPAD_DOWN);
            s.dpad[3] = bit(effective, B_DPAD_LEFT);
            listener.onSteamPadState(pad.controller, bit(effective, B_GUIDE), bit(buttons, B_QAM),
                    pressedKeyCodes(effective));
        }
        if (trackpadMode == TRACKPAD_MOUSE_RIGHT || trackpadMode == TRACKPAD_MOUSE_BOTH)
            applyTrackpad(pad.right, floats, base + F_RPAD_DOWN, bit(buttons, B_RPAD_CLICK), false);
        if (trackpadMode == TRACKPAD_MOUSE_LEFT || trackpadMode == TRACKPAD_MOUSE_BOTH)
            applyTrackpad(pad.left, floats, base + F_LPAD_DOWN, bit(buttons, B_LPAD_CLICK),
                    trackpadMode == TRACKPAD_MOUSE_BOTH);
    }

    /** Button bits a back-button gamepad target presses (L2/R2 are handled as full trigger pulls). */
    private static int gamepadTargetBits(Binding target) {
        switch (target) {
            case GAMEPAD_BUTTON_A: return 1 << B_A;
            case GAMEPAD_BUTTON_B: return 1 << B_B;
            case GAMEPAD_BUTTON_X: return 1 << B_X;
            case GAMEPAD_BUTTON_Y: return 1 << B_Y;
            case GAMEPAD_BUTTON_L1: return 1 << B_LB;
            case GAMEPAD_BUTTON_R1: return 1 << B_RB;
            case GAMEPAD_BUTTON_SELECT: return 1 << B_BACK;
            case GAMEPAD_BUTTON_START: return 1 << B_START;
            case GAMEPAD_BUTTON_L3: return 1 << B_LSTICK;
            case GAMEPAD_BUTTON_R3: return 1 << B_RSTICK;
            case GAMEPAD_DPAD_UP: return 1 << B_DPAD_UP;
            case GAMEPAD_DPAD_DOWN: return 1 << B_DPAD_DOWN;
            case GAMEPAD_DPAD_LEFT: return 1 << B_DPAD_LEFT;
            case GAMEPAD_DPAD_RIGHT: return 1 << B_DPAD_RIGHT;
            default: return 0; // stick directions aren't offered for back buttons
        }
    }

    private static int[] pressedKeyCodes(int buttons) {
        int n = 0;
        for (int[] m : BUTTON_KEYCODES) if (bit(buttons, m[0])) n++;
        int[] out = new int[n];
        n = 0;
        for (int[] m : BUTTON_KEYCODES) if (bit(buttons, m[0])) out[n++] = m[1];
        return out;
    }

    /** One mouse trackpad: finger movement moves the mouse, clicking it presses a mouse button.
     *  off = the pad's {down, x, y} slot in the frame. */
    private void applyTrackpad(Trackpad t, float[] floats, int off, boolean click, boolean secondary) {
        boolean down = floats[off] > 0.5f;
        float x = floats[off + 1];
        float y = floats[off + 2];
        if (down && t.down) {
            t.accX += (x - t.x) * TRACKPAD_PIXELS_PER_PAD;
            t.accY += (y - t.y) * TRACKPAD_PIXELS_PER_PAD;
            int dx = (int) t.accX;
            int dy = (int) t.accY;
            if (dx != 0 || dy != 0) {
                t.accX -= dx;
                t.accY -= dy;
                listener.onSteamPadMouseMove(dx, dy);
            }
        } else {
            t.accX = 0;
            t.accY = 0;
        }
        t.down = down;
        t.x = x;
        t.y = y;

        if (click != t.clickDown) {
            t.clickDown = click;
            listener.onSteamPadMouseButton(secondary, click);
        }
    }

    /** A pad went away mid-press: release its trackpad click and any key / mouse back button. */
    private void releaseHeld(Pad pad) {
        if (pad.right.clickDown) {
            pad.right.clickDown = false;
            listener.onSteamPadMouseButton(false, false);
        }
        if (pad.left.clickDown) {
            pad.left.clickDown = false;
            listener.onSteamPadMouseButton(trackpadMode == TRACKPAD_MOUSE_BOTH, false);
        }
        for (int i = 0; i < PADDLE_COUNT; i++) {
            Binding target = paddleBindings[i];
            if (pad.paddleDown[i] && target != Binding.NONE && !target.isGamepad())
                listener.onSteamPadBinding(target, false);
            pad.paddleDown[i] = false;
        }
    }

    private static boolean bit(int buttons, int b) {
        return (buttons & (1 << b)) != 0;
    }

    private static native boolean nativeInit(boolean bluetooth);

    private static native int nativePoll(int[] ints, float[] floats);

    private static native String nativeGetName(int id);

    private static native String nativeGetPath(int id);

    private static native void nativeRumble(int id, int low, int high, int durationMs);

    private static native void nativeShutdown();
}
