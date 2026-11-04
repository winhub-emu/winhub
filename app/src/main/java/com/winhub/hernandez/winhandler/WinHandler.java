package com.winhub.hernandez.winhandler;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.preference.PreferenceManager;

import com.winhub.hernandez.XServerDisplayActivity;
import com.winhub.hernandez.core.GyroCalibrator;
import com.winhub.hernandez.core.StringUtils;
import com.winhub.hernandez.inputcontrols.ControlsProfile;
import com.winhub.hernandez.inputcontrols.ExternalController;
import com.winhub.hernandez.inputcontrols.FakeInputWriter;
import com.winhub.hernandez.inputcontrols.GamepadState;
import com.winhub.hernandez.inputcontrols.SteamControllerBackend;
import com.winhub.hernandez.widget.InputControlsView;
import com.winhub.hernandez.xserver.Pointer;
import com.winhub.hernandez.xserver.XServer;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WinHandler {
    private static final short SERVER_PORT = 7947;
    private static final short CLIENT_PORT = 7946;
    public static final byte FLAG_INPUT_TYPE_XINPUT = 0x04;
    public static final byte FLAG_INPUT_TYPE_DINPUT = 0x08;
    public static final byte DEFAULT_INPUT_TYPE = (byte)(FLAG_INPUT_TYPE_XINPUT | FLAG_INPUT_TYPE_DINPUT);
    private DatagramSocket socket;
    private final ByteBuffer sendData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer receiveData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final DatagramPacket sendPacket = new DatagramPacket(sendData.array(), 64);
    private final DatagramPacket receivePacket = new DatagramPacket(receiveData.array(), 64);
    private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
    private boolean initReceived = false;
    private boolean running = false;
    private OnGetProcessInfoListener onGetProcessInfoListener;
    // Task Manager "Processor Affinity": the last mask the user applied per (guest) pid. The guest's
    // GetProcessAffinityMask readback is unreliable under wow64/FEX (it keeps reporting the full mask
    // even after SetProcessAffinityMask has moved the live threads), so we remember what the user
    // actually chose and surface THAT in the dialog. Pruned to live pids each process-list refresh, so
    // a restarted process (new pid) correctly falls back to the guest value — matching Windows, where
    // affinity resets on restart.
    private final java.util.concurrent.ConcurrentHashMap<Integer, Integer> manualAffinity =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, ExternalController> controllers = new HashMap<>(); // map deviceId -> controller
                                                                                  // implementation
    private InetAddress localhost;
    private byte inputType = DEFAULT_INPUT_TYPE;
    private final XServerDisplayActivity activity;
    private final List<Integer> gamepadClients = new CopyOnWriteArrayList<>();
    private SharedPreferences preferences;

    // Multi-controller support
    private static final int MAX_CONTROLLERS = 4;
    private static final int OSC_DEVICE_ID = -1;
    private FakeInputWriter[] writers = new FakeInputWriter[MAX_CONTROLLERS];
    // ConcurrentHashMap: the vibration thread iterates this to resolve a slot's owning
    // device while the input/hotplug path (main thread) mutates it — avoid a CME.
    private Map<Integer, Integer> deviceToSlot = new ConcurrentHashMap<>();
    // Descriptor-keyed slot identity for PHYSICAL controllers. The Android deviceId is
    // transient (it changes on every reconnect and each sub-device of one physical pad
    // — gamepad + touchpad + motion — gets its own id), so slots are keyed on the stable
    // device descriptor instead. descriptorToSlot maps a physical controller to its slot;
    // deviceToDescriptor maps each live deviceId back to its descriptor so releaseSlot can
    // tell whether a sibling sub-device still holds the slot. OSC has no descriptor and is
    // never entered here — it keeps the deviceId(-1)→slot mapping in deviceToSlot only.
    private Map<String, Integer> descriptorToSlot = new HashMap<>();
    private Map<Integer, String> deviceToDescriptor = new HashMap<>();
    private Set<Integer> usedSlots = new HashSet<>();
    private String fakeInputBasePath;

    // Manual per-device slot overrides from the in-game "Players" sub-tab (persisted per-container by
    // XServerDisplayActivity in Container.controllerSlotOverrides). Keyed on the stable device
    // descriptor — same key as descriptorToSlot — with the OSC virtual pad using the OSC_DESCRIPTOR
    // sentinel below (it has no real descriptor). A value of 0..MAX_CONTROLLERS-1 PINS the device to
    // that XInput player slot; SLOT_IGNORE means "never take a slot"; a missing entry = auto (FCFS,
    // the historical behavior). Consulted by assignSlot on EVERY assignment path (startup pre-assign,
    // hot-plug, and the per-input-event send), so an override can't be undone by a later input event.
    // Main-thread only (drawer callbacks + InputDeviceListener + input events all land on the main
    // looper), so a plain HashMap is safe here — mirrors descriptorToSlot/deviceToDescriptor.
    public static final int SLOT_IGNORE = -2;
    public static final String OSC_DESCRIPTOR = "__osc__"; // stand-in descriptor for the on-screen pad
    private final Map<String, Integer> manualSlotOverrides = new HashMap<>();

    // ---- Steam Controller (SDL) pads ----
    // Pads read through SteamControllerBackend have no Android InputDevice. They go through the same
    // slot machinery as a physical pad, keyed on a synthetic deviceId (SteamControllerBackend.
    // DEVICE_ID_BASE - SDL instance id) and an "sdl:" descriptor, the way OSC uses OSC_DEVICE_ID /
    // OSC_DESCRIPTOR. Only populated while the Steam Controller setting is on. ConcurrentHashMap: the
    // vibration thread reads it in resolveSlotOwnerDeviceId / dispatchControllerVibration.
    private final Map<Integer, ExternalController> sdlPads = new ConcurrentHashMap<>();
    private volatile SteamControllerBackend sdlBackend;

    // ---- Shared / combined player slots (opt-in, manual-only) ----
    // A slot is SHARED when >=2 DISTINCT contributors drive it: two physical descriptors pinned to the
    // same player slot, or the on-screen pad co-occupying a physical pad's slot. This is only ever
    // produced by an EXPLICIT choice — same-slot pins in the Players UI / saved overrides, or the
    // per-container On-screen SHARE mode below. Plain auto-assignment never co-seats two devices on one
    // slot (assignFreeSlot/assignSpecificSlot skip used slots), so a single-device slot always takes the
    // untouched direct-write path and behaves EXACTLY as before. slotShared[] is a cache recomputed only
    // when slot occupancy changes (recomputeSharedSlots), so the per-frame write path is a plain array
    // read with no allocation whenever nothing is shared.
    private final boolean[] slotShared = new boolean[MAX_CONTROLLERS];
    // Per-shared-slot contributor snapshots, keyed by contributor descriptor (OSC_DESCRIPTOR for the
    // on-screen pad). Each write copies the contributor's latest state in, then the slot writes the
    // MERGE of all contributors — so one contributor's neutral frame (e.g. OSC's per-frame full,
    // mostly-neutral state) can never stomp another contributor's held input. Main-thread only.
    @SuppressWarnings("unchecked")
    private final Map<String, GamepadState>[] slotContributions = new HashMap[MAX_CONTROLLERS];
    // Pre-allocated merged-state scratch per slot, reused so a shared slot never allocates per frame.
    private final GamepadState[] slotMerged = new GamepadState[MAX_CONTROLLERS];

    // Per-container on-screen-controls-vs-physical-pad priority (see Container.getOnScreenControllerMode).
    // KEEP = the historical behavior (OSC keeps whatever slot it holds; a pad hot-plugged mid-game lands
    // on the next free slot). YIELD = a pad connecting while OSC holds Player 1 promotes to Player 1 and
    // OSC steps up to the next free slot. SHARE = that pad co-occupies OSC's slot (merged, per above).
    // Pushed once at launch (setupUI), the same seed-after-container discipline as the vibration tuning.
    public static final int ON_SCREEN_MODE_KEEP = 0;
    public static final int ON_SCREEN_MODE_YIELD = 1;
    public static final int ON_SCREEN_MODE_SHARE = 2;
    private volatile int onScreenControllerMode = ON_SCREEN_MODE_KEEP;
    // Session-only OSC slot preference set by a YIELD promotion (NOT persisted). assignSlot honors it for
    // OSC when OSC has no explicit pin, so a bumped OSC re-seats onto the slot it was moved to instead of
    // falling straight back to the lowest free slot (which is the one we just handed the physical pad).
    private int oscYieldSlot = -1;

    // Physical hot-plug debounce. Android churns remove/add events during Bluetooth flaps
    // and sub-device re-enumeration; a real unplug stays gone and is torn down after the
    // delay, while a quick reconnect re-seats onto the SAME slot (via descriptorToSlot)
    // before the writer is ever destroyed, so the guest never sees a disconnect. Runs on
    // the main looper — the InputDeviceListener is registered with a null Handler, so its
    // callbacks land on the main thread too.
    private static final long PHYSICAL_DISCONNECT_DEBOUNCE_MS = 400;
    private final Handler inputHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Runnable> pendingDeviceReleases = new HashMap<>();
    private LocalServerSocket vibrationServer;
    private volatile boolean vibrationRunning = false;
    private boolean[] vibrationEnabledSlots = new boolean[MAX_CONTROLLERS]; // per-slot vibration toggle
    private boolean vibrationMasterEnabled = true; // master switch — off = NO controller vibration at all

    // Per-container rumble tuning, pushed from XServerDisplayActivity.setupUI (once the Container is
    // resolved) and re-pushed live from the in-game drawer. Mirrors Container.getVibrationMode()/
    // getVibrationIntensity() — see those for the mode values (0=Off 1=Controller 2=Device 3=Both).
    public static final int VIBRATION_MODE_OFF = 0;
    public static final int VIBRATION_MODE_CONTROLLER = 1;
    public static final int VIBRATION_MODE_DEVICE = 2;
    public static final int VIBRATION_MODE_BOTH = 3;
    private volatile int vibrationMode = VIBRATION_MODE_CONTROLLER;
    private volatile int vibrationIntensity = 100;

    // Gyro (motion aim) — "hold the activator + tilt the device" -> right stick / left stick / mouse.
    // Rate-mode math (deadzone -> invert -> sensitivity -> low-pass -> clamp) follows the reference
    // gyro implementation in the WinNative tree (WinHandler.updateGyroData / getOutputGamepadState).
    // Sensitivity/deadzone/smoothing/invert/activator are live user settings (in-game drawer, Controls
    // tab); the defaults below reproduce the original hardcoded P1 behaviour exactly.
    public static final int GYRO_TARGET_RIGHT_STICK = 0;
    public static final int GYRO_TARGET_LEFT_STICK = 1;
    public static final int GYRO_TARGET_MOUSE = 2;
    // Activator = the button that gates the gyro while held. ALWAYS = no gate (no hold needed).
    public static final int GYRO_ACTIVATOR_L1 = 0;
    public static final int GYRO_ACTIVATOR_L2 = 1;
    public static final int GYRO_ACTIVATOR_R1 = 2;
    public static final int GYRO_ACTIVATOR_R3 = 3;
    public static final int GYRO_ACTIVATOR_ALWAYS = 4;
    // How the activator gates the gyro. HOLD = the original behaviour (gyro runs while the button is
    // down); TOGGLE = a tap latches the gyro on until the next tap. Meaningless with ALWAYS, which has
    // no button to latch, so the gate below ignores the mode in that case.
    public static final int GYRO_ACTIVATION_HOLD = 0;
    public static final int GYRO_ACTIVATION_TOGGLE = 1;
    // How the tilt is read. RATE = the original path (angular velocity -> deflection; the stick
    // recentres as soon as you stop moving). ORIENTATION = "tilt to aim": the stick follows the ANGLE
    // the device is held at relative to a captured zero, so a held tilt keeps the stick deflected.
    // The two modes never share a sample: the activity picks ONE sensor from this value and rate
    // samples land in updateGyroData while orientation samples land in updateGyroOrientation. Nothing
    // in updateGyroData branches on the mode, so rate mode is byte-identical to before this existed.
    public static final int GYRO_MODE_RATE = 0;
    public static final int GYRO_MODE_ORIENTATION = 1;

    public static final boolean GYRO_ENABLED_DEFAULT = true;
    public static final int GYRO_TARGET_DEFAULT = GYRO_TARGET_RIGHT_STICK;
    public static final float GYRO_DEADZONE_DEFAULT = 0.05f;    // rad/s; below this is hand tremor
    public static final float GYRO_SENSITIVITY_DEFAULT = 2.0f;  // rad/s -> stick deflection gain
    public static final float GYRO_SMOOTHING_DEFAULT = 0.5f;    // 0 = raw, ->1 = heavier low-pass
    public static final int GYRO_ACTIVATOR_DEFAULT = GYRO_ACTIVATOR_L1;
    public static final int GYRO_ACTIVATION_MODE_DEFAULT = GYRO_ACTIVATION_HOLD;
    public static final int GYRO_MODE_DEFAULT = GYRO_MODE_RATE;

    private static final float GYRO_AXIS_EPSILON = 0.001f; // don't re-inject for sub-noise changes
    // rad/s * sensitivity -> pixels per sensor sample in mouse mode. Picked so sensitivity 2.0 gives
    // roughly the same "feel" as the right-stick path at the SENSOR_DELAY_GAME (~50 Hz) sample rate.
    private static final float GYRO_MOUSE_SCALE = 12.0f;
    // rad of tilt * sensitivity -> stick deflection in orientation mode. At sensitivity 1.0 that's
    // full deflection at ~14 deg off centre, so the stock 2.0 lands at ~7 deg — a wrist's worth.
    private static final float GYRO_ORIENTATION_STICK_GAIN = 4.0f;
    // getOrientation()'s yaw is undefined as the remapped pitch approaches +/-90 deg (gimbal lock):
    // the azimuth flips wildly for a motionless device. Past this we hold the last deflection rather
    // than emit garbage. 1.3 rad ~= 74.5 deg, comfortably outside any normal hand-held pose.
    private static final float GYRO_ORIENTATION_GIMBAL_LIMIT = 1.3f;
    // Axis/handedness verification aid — off unless `setprop log.tag.BLGyroOrient DEBUG` is set, and
    // rate-limited so even then it can't flood a 200 Hz path. Ship-safe, so it never needs removing.
    private static final String GYRO_ORIENT_LOG_TAG = "BLGyroOrient";
    private static final long GYRO_ORIENT_LOG_INTERVAL_MS = 200; // ~5 Hz

    private volatile boolean gyroEnabled = GYRO_ENABLED_DEFAULT;
    private volatile int gyroTarget = GYRO_TARGET_DEFAULT;
    private volatile float gyroDeadzone = GYRO_DEADZONE_DEFAULT;
    private volatile float gyroSensitivity = GYRO_SENSITIVITY_DEFAULT;
    private volatile float gyroSmoothing = GYRO_SMOOTHING_DEFAULT;
    private volatile boolean gyroInvertX = false;
    private volatile boolean gyroInvertY = false;
    private volatile int gyroActivator = GYRO_ACTIVATOR_DEFAULT;
    private volatile int gyroActivationMode = GYRO_ACTIVATION_MODE_DEFAULT;
    private volatile int gyroMode = GYRO_MODE_DEFAULT;
    // False until the launch-time container/shortcut resolution lands (applyGyroTuning). The sample
    // path is a strict no-op while it's false, so the gyro never runs on values the user didn't pick.
    private volatile boolean gyroConfigApplied = false;
    // Zero-rate bias measured by the out-of-game calibration (Input Controls -> Gyroscope). 0 = the
    // uncalibrated default, which makes the subtraction in updateGyroData a strict no-op.
    private volatile float gyroBiasX = 0.0f;
    private volatile float gyroBiasY = 0.0f;

    private float smoothedGyroX = 0.0f;
    private float smoothedGyroY = 0.0f;
    private float currentGyroStickX = 0.0f;
    private float currentGyroStickY = 0.0f;
    // Toggle-mode latch plus the previous activator level it edge-detects against. Both are touched
    // ONLY from the sensor path (updateGyroActivation) and the reset/config helpers, all on the main
    // thread, so plain fields are enough — no volatile, no lock on a 50-200 Hz path.
    private boolean gyroToggleLatched = false;
    private boolean gyroActivatorWasPressed = false;
    // Sub-pixel carry for mouse mode, so slow tilts still move the pointer instead of rounding to 0.
    private float accumulatedGyroMouseX = 0.0f;
    private float accumulatedGyroMouseY = 0.0f;
    // Orientation-mode zero reference: the pose captured the moment the gyro became active, which the
    // offsets below are measured against. Touched only from the sensor path and the reset/config
    // helpers (main thread), same as the toggle latch above.
    private boolean gyroOrientationCalibrated = false;
    private float orientationYawZero = 0.0f;
    private float orientationPitchZero = 0.0f;
    private long lastGyroOrientLogMs = 0;
    // Last controller that pushed state; the gyro re-injects through it so a held tilt keeps panning.
    private ExternalController gyroTargetController;
    private boolean gyroApplyLogged = false;
    // Scratch state for the gyro overlay — never handed out, so no per-event allocation.
    private final GamepadState outputGamepadState = new GamepadState();

    // Mouse-mode injection scratch. The pending delta is accumulated under this lock and drained by a
    // SINGLE pre-allocated Runnable, so a sustained tilt never allocates per sample (this path runs
    // while the game renders) and bursts coalesce into one packet instead of flooding the send queue.
    private final Object gyroMouseLock = new Object();
    private int pendingGyroMouseDx = 0;
    private int pendingGyroMouseDy = 0;
    private boolean gyroMouseActionQueued = false;
    private final Runnable gyroMouseAction = () -> {
        final int dx, dy;
        synchronized (gyroMouseLock) {
            dx = pendingGyroMouseDx;
            dy = pendingGyroMouseDy;
            pendingGyroMouseDx = 0;
            pendingGyroMouseDy = 0;
            gyroMouseActionQueued = false;
        }
        if (dx == 0 && dy == 0)
            return;
        sendData.rewind();
        sendData.put(RequestCodes.MOUSE_EVENT);
        sendData.putInt(10);
        sendData.putInt(MouseEventFlags.MOVE);
        sendData.putShort((short) dx);
        sendData.putShort((short) dy);
        sendData.putShort((short) 0);
        sendData.put((byte) 1); // cursor pos feedback (MOVE)
        sendPacket(CLIENT_PORT);
    };

    private boolean xinputDisabled;
    private boolean xinputDisabledInitialized = false;

    private int fallbackSlot = -1;

    private final InputManager inputManager;
    private final InputManager.InputDeviceListener inputDeviceListener;

    // ---- Controller-status toast hook (P5b) ----
    // A plain, UI-framework-agnostic callback so the Activity/Compose layer can surface an in-game
    // "controllers detected/changed" toast WITHOUT this class ever touching Compose. Fired only for
    // real game-controller hot-plug events (add/remove/progressive-change); manual reassignment + Reset
    // Input are already driven from the Activity's own drawer callbacks, so those fire the toast there.
    // Always invoked on the main looper (the InputDeviceListener + the debounced release Runnable both
    // run there), so the Activity may read getPlayerSlotAssignments() straight from the callback.
    public interface ControllerAssignmentListener {
        /** @param reason "connected" | "disconnected" | "changed"; @param descriptor affected device
         *  descriptor (may be null, e.g. an already-detached device). */
        void onControllerAssignmentsChanged(String reason, String descriptor);
    }
    private ControllerAssignmentListener assignmentListener;
    public void setControllerAssignmentListener(ControllerAssignmentListener l) { this.assignmentListener = l; }
    private void notifyAssignmentsChanged(String reason, String descriptor) {
        ControllerAssignmentListener l = assignmentListener;
        if (l != null) l.onControllerAssignmentsChanged(reason, descriptor);
    }

    public WinHandler(XServerDisplayActivity activity) {
        this.activity = activity;
        this.inputManager = (InputManager) activity.getSystemService(Context.INPUT_SERVICE);
        this.inputDeviceListener = new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                // A pad appeared (or reconnected). Cancel any pending release for this id
                // first so a fast reconnect re-seats its slot instead of tearing it down,
                // then assign it a slot up front so the guest sees it without waiting for
                // the first input event.
                cancelPendingDeviceRelease(deviceId);
                // On-screen priority (per-container): YIELD/SHARE may re-seat OSC or co-seat the pad
                // first. When SHARE has already seated the pad, skip the normal auto-assign.
                if (!handleOnScreenModeForNewPad(deviceId))
                    assignConnectedDeviceIfPossible(deviceId, "hotplug");
                // Surface a "connected" event for the in-game status toast — game controllers only, so a
                // random device add never pops a toast.
                android.view.InputDevice added = android.view.InputDevice.getDevice(deviceId);
                if (ExternalController.isGameController(added))
                    notifyAssignmentsChanged("connected", added != null ? added.getDescriptor() : null);
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                // Debounced: a real unplug is released after PHYSICAL_DISCONNECT_DEBOUNCE_MS
                // (the release runnable also notifies InputControlsView — see
                // scheduleDeviceRelease); a flap that re-adds within the window is a no-op.
                scheduleDeviceRelease(deviceId);
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                // Progressive capability (GameNative concept): some controllers advertise
                // only a partial source set on add and expand to a full gamepad on the
                // first change event. Re-evaluate here so those pads still get a slot.
                // Thrash-guarded: cancelPendingDeviceRelease keeps a mid-debounce pad, and
                // assignConnectedDeviceIfPossible early-returns when the device is already
                // slotted or isn't a game controller, so repeated change events are cheap.
                cancelPendingDeviceRelease(deviceId);
                boolean assigned = assignConnectedDeviceIfPossible(deviceId, "changed");
                // A pad that only became a full game controller on this change event just took a slot —
                // treat it like a fresh connect for the status toast.
                if (assigned) {
                    android.view.InputDevice dev = android.view.InputDevice.getDevice(deviceId);
                    notifyAssignmentsChanged("connected", dev != null ? dev.getDescriptor() : null);
                }
            }
        };
        inputManager.registerInputDeviceListener(inputDeviceListener, null);

        preferences = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());

        // Load per-slot vibration preferences (default: enabled)
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            vibrationEnabledSlots[i] = preferences.getBoolean("vibration_slot_" + i, true);
        }
        // Master switch (default: enabled) — a single kill-switch for ALL controller vibration.
        vibrationMasterEnabled = preferences.getBoolean("vibration_master_enabled", true);

        // Gyro tuning is NOT read here any more — it lives on the container (and, for the game-facing
        // half, on the shortcut). XServerDisplayActivity resolves the chain once at launch and pushes
        // it in via applyGyroTuning; until then the pipeline stays inert (gyroConfigApplied == false),
        // so a sample arriving before the container is known can't act on stale defaults.

        // Calibration bias — read once here (never on the sample path). GyroCalibrator honours the
        // device stamp, so a bias restored from another phone by Android auto-backup comes back 0.
        float[] bias = new float[2];
        GyroCalibrator.loadBias(activity, bias, preferences);
        gyroBiasX = bias[0];
        gyroBiasY = bias[1];
    }

    private boolean sendPacket(int port) {
        try {
            int size = sendData.position();
            if (size == 0)
                return false;
            sendPacket.setAddress(localhost);
            sendPacket.setPort(port);
            socket.send(sendPacket);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void exec(String command) {
        command = command.trim();
        if (command.isEmpty())
            return;

        // The `split` function here should be sensitive to paths with spaces.
        // Instead of splitting, let's assume that command is directly provided in two
        // parts: filename and parameters.
        // Adjust command splitting based on whether it contains quotes.

        String filename;
        String parameters;

        if (command.contains("\"")) {
            // If the command is quoted, extract the quoted part as the filename
            int firstQuote = command.indexOf("\"");
            int lastQuote = command.lastIndexOf("\"");
            filename = command.substring(firstQuote + 1, lastQuote);
            if (lastQuote + 1 < command.length()) {
                parameters = command.substring(lastQuote + 1).trim();
            } else {
                parameters = "";
            }
        } else {
            // Standard split when no quotes
            String[] cmdList = command.split(" ", 2);
            filename = cmdList[0];
            if (cmdList.length > 1) {
                parameters = cmdList[1];
            } else {
                parameters = "";
            }
        }

        // winhandler.exe takes at most EXEC_RAW_MAX bytes per EXEC; a longer command (a program in a game
        // folder) would not fit the packet, so it goes through a batch file instead.
        if (filename.getBytes().length + parameters.getBytes().length > EXEC_RAW_MAX) {
            if (!execInFolder(filename, parameters))
                Log.w("WinHandler", "exec: command too long and the batch file could not be written: " + filename);
            return;
        }

        addAction(() -> {
            byte[] filenameBytes = filename.getBytes();
            byte[] parametersBytes = parameters.getBytes();

            sendData.rewind();
            sendData.put(RequestCodes.EXEC);
            sendData.putInt(filenameBytes.length + parametersBytes.length + 8);
            sendData.putInt(filenameBytes.length);
            sendData.putInt(parametersBytes.length);
            sendData.put(filenameBytes);
            sendData.put(parametersBytes);
            sendPacket(CLIENT_PORT);
        });
    }

    /** Largest filename + parameters winhandler.exe takes in one EXEC: its receive buffer is 64 bytes,
     *  minus the code byte and three ints. */
    public static final int EXEC_RAW_MAX = 51;

    /**
     * EXEC without {@link #exec}'s quote parsing: the filename and parameters go to ShellExecute as
     * given. False (nothing sent) when they don't fit winhandler.exe's buffer ({@link #EXEC_RAW_MAX}).
     * winhandler.exe sends nothing back; watch the process list to know when the program is done.
     */
    public boolean execRaw(String filename, String parameters) {
        final byte[] filenameBytes = filename.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        final byte[] parametersBytes = (parameters != null ? parameters : "").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        if (filenameBytes.length + parametersBytes.length > EXEC_RAW_MAX)
            return false;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.EXEC);
            sendData.putInt(filenameBytes.length + parametersBytes.length + 8);
            sendData.putInt(filenameBytes.length);
            sendData.putInt(parametersBytes.length);
            sendData.put(filenameBytes);
            sendData.put(parametersBytes);
            sendPacket(CLIENT_PORT);
        });
        return true;
    }

    private final java.util.concurrent.atomic.AtomicInteger batchSlot = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Starts a program from any path, in its own folder: winhandler.exe takes at most {@link #EXEC_RAW_MAX}
     * bytes per EXEC and starts programs in its own folder, so the command goes into a short batch file in
     * C:\windows (blrun0.bat .. blrun9.bat, in turn) that changes to the program's folder and starts it.
     * {@code filename} is a Windows path. False when the batch file could not be written or sent.
     */
    public boolean execInFolder(String filename, String parameters) {
        java.io.File driveC = activity.getWineDriveC();
        if (driveC == null || filename == null || filename.trim().isEmpty()) return false;
        filename = filename.trim();
        String name = "blrun" + Math.floorMod(batchSlot.getAndIncrement(), 10) + ".bat";
        int slash = filename.lastIndexOf('\\');
        StringBuilder bat = new StringBuilder("@echo off\r\n");
        if (slash > 0) bat.append("cd /d \"").append(filename, 0, slash).append("\"\r\n");
        bat.append("start \"\" \"").append(filename).append('"');
        if (parameters != null && !parameters.trim().isEmpty()) bat.append(' ').append(parameters.trim());
        bat.append("\r\n");
        java.nio.charset.Charset cs = java.nio.charset.Charset.isSupported("windows-1252")
                ? java.nio.charset.Charset.forName("windows-1252") : java.nio.charset.StandardCharsets.ISO_8859_1;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(driveC, "windows/" + name))) {
            out.write(bat.toString().getBytes(cs));
        } catch (java.io.IOException e) {
            Log.w("WinHandler", "execInFolder: " + name + " not written", e);
            return false;
        }
        return execRaw("C:\\windows\\" + name, "");
    }

    public void killProcess(final String processName) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KILL_PROCESS);
            byte[] bytes = processName.getBytes();
            sendData.putInt(bytes.length);
            sendData.put(bytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void listProcesses() {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.LIST_PROCESSES);
            sendData.putInt(0);

            boolean sentLP = sendPacket(CLIENT_PORT);
            Log.d("WinHandlerTM", "listProcesses sent=" + sentLP + " initReceived=" + initReceived + " listener=" + (onGetProcessInfoListener != null));
            if (!sentLP && onGetProcessInfoListener != null) {
                onGetProcessInfoListener.onGetProcessInfo(0, 0, null);
            }
        });
    }

    public void setProcessAffinity(final String processName, final int affinityMask) {
        addAction(() -> {
            byte[] bytes = processName.getBytes();
            sendData.rewind();
            sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            sendData.putInt(9 + bytes.length);
            sendData.putInt(0);
            sendData.putInt(affinityMask);
            sendData.put((byte) bytes.length);
            sendData.put(bytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void setProcessAffinity(final int pid, final int affinityMask) {
        if (pid != 0) manualAffinity.put(pid, affinityMask);
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            sendData.putInt(9);
            sendData.putInt(pid);
            sendData.putInt(affinityMask);
            sendData.put((byte) 0);
            sendPacket(CLIENT_PORT);
        });
    }

    // The mask the user last applied to this pid via the Task Manager, or null if none. The TM uses
    // this to pre-tick the Processor Affinity dialog with the user's real choice instead of the
    // guest's stale GetProcessAffinityMask readback.
    public Integer getManualAffinity(int pid) {
        return manualAffinity.get(pid);
    }

    // Drop remembered affinities for pids that are no longer running (called on each process-list
    // refresh) so a restarted process doesn't inherit a stale override.
    public void retainManualAffinities(java.util.Set<Integer> livePids) {
        if (livePids.isEmpty()) manualAffinity.clear();
        else manualAffinity.keySet().retainAll(livePids);
    }

    // Whether any pid currently has a user-applied affinity mask remembered. Lets the periodic
    // drift check skip its tick entirely when there's nothing to re-pin.
    public boolean hasManualAffinity() {
        return !manualAffinity.isEmpty();
    }

    // Snapshot of the pids that currently have a remembered affinity, safe to iterate while the
    // drift checker calls setProcessAffinity()/clearManualAffinity() (which mutate the live map).
    public java.util.Set<Integer> getManualAffinityPids() {
        return new java.util.HashSet<>(manualAffinity.keySet());
    }

    // Forget one pid's remembered affinity — used by the drift checker when a pid has exited, so we
    // stop re-pinning a dead (or recycled) pid without waiting for the Task Manager's prune pass.
    public void clearManualAffinity(int pid) {
        manualAffinity.remove(pid);
    }

    // Re-send every remembered affinity. The guest's process-affinity record doesn't stick under
    // wow64/FEX, so threads spawned after the original set escape back to all cores; re-applying
    // re-pins them (and, in the guest, every current thread of the process, since wine maps
    // SetProcessAffinityMask onto the process's live Linux threads). Called by the Task-Manager
    // process-refresh loop while it is open; when it is closed, XServerDisplayActivity's drift checker
    // re-pins per-pid on thread growth instead (see AFFINITY_DRIFT_CHECK_INTERVAL_MS).
    public void reapplyManualAffinities() {
        for (Map.Entry<Integer, Integer> e : manualAffinity.entrySet()) {
            setProcessAffinity((int) e.getKey(), (int) e.getValue());
        }
    }

    public void mouseEvent(int flags, int dx, int dy, int wheelDelta) {
        if (!initReceived)
            return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.MOUSE_EVENT);
            sendData.putInt(10);
            sendData.putInt(flags);
            sendData.putShort((short) dx);
            sendData.putShort((short) dy);
            sendData.putShort((short) wheelDelta);
            sendData.put((byte) ((flags & MouseEventFlags.MOVE) != 0 ? 1 : 0)); // cursor pos feedback
            sendPacket(CLIENT_PORT);
        });
    }

    public void keyboardEvent(byte vkey, int flags) {
        if (!initReceived)
            return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KEYBOARD_EVENT);
            sendData.put(vkey);
            sendData.putInt(flags);
            sendPacket(CLIENT_PORT);
        });
    }

    public void bringToFront(final String processName) {
        bringToFront(processName, 0);
    }

    public void bringToFront(final String processName, final long handle) {
        addAction(() -> {
            sendData.rewind();
            try {
                sendData.put(RequestCodes.BRING_TO_FRONT);
                byte[] bytes = processName.getBytes();
                sendData.putInt(bytes.length);
                // FIXME: Chinese and Japanese got from winhandler.exe are broken, and they
                // cause overflow.
                sendData.put(bytes);
                sendData.putLong(handle);
            } catch (java.nio.BufferOverflowException e) {
                e.printStackTrace();
                sendData.rewind();
            }
            sendPacket(CLIENT_PORT);
        });
    }

    private void addAction(Runnable action) {
        synchronized (actions) {
            actions.add(action);
            actions.notify();
        }
    }

    public OnGetProcessInfoListener getOnGetProcessInfoListener() {
        return onGetProcessInfoListener;
    }

    public void setOnGetProcessInfoListener(OnGetProcessInfoListener onGetProcessInfoListener) {
        synchronized (actions) {
            this.onGetProcessInfoListener = onGetProcessInfoListener;
        }
    }

    private void startSendThread() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (running) {
                synchronized (actions) {
                    while (initReceived && !actions.isEmpty())
                        actions.poll().run();
                    try {
                        actions.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        });
    }

    public void stop() {
        running = false;
        closeFakeInputWriter();

        if (socket != null) {
            socket.close();
            socket = null;
        }

        synchronized (actions) {
            actions.notify();
        }
    }

    public void startVibrationListener() {
        if (vibrationRunning)
            return;
        vibrationRunning = true;

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                vibrationServer = new LocalServerSocket("winlator_vibration");
                Log.d("WinHandler", "Vibration listener started on abstract socket: winlator_vibration");

                while (vibrationRunning) {
                    LocalSocket client = vibrationServer.accept();
                    try {
                        java.io.InputStream is = client.getInputStream();
                        byte[] buf = new byte[8];
                        int read = is.read(buf);
                        if (read == 8) {
                            int strong = (buf[0] & 0xFF) | ((buf[1] & 0xFF) << 8);
                            int weak = (buf[2] & 0xFF) | ((buf[3] & 0xFF) << 8);
                            int durationMs = (buf[4] & 0xFF) | ((buf[5] & 0xFF) << 8);
                            int slot = (buf[6] & 0xFF) | ((buf[7] & 0xFF) << 8);
                            triggerVibration(strong, weak, durationMs, slot);
                        }
                        client.close();
                    } catch (IOException e) {
                        Log.e("WinHandler", "Vibration client error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (vibrationRunning) {
                    Log.e("WinHandler", "Vibration listener error: " + e.getMessage());
                }
            }
        });
    }

    private void triggerVibration(int strong, int weak, int durationMs, int slot) {
        // Master kill-switch: off = no controller vibration at all, regardless of slot. This catches
        // rumble on ANY slot (incl. an out-of-range/unmapped slot the per-slot gate below would miss).
        if (!vibrationMasterEnabled)
            return;

        // Per-container mode: Off short-circuits everything below (per-slot gate, dispatch).
        int mode = vibrationMode;
        if (mode == VIBRATION_MODE_OFF)
            return;

        // Check if vibration is enabled for this slot
        if (slot >= 0 && slot < MAX_CONTROLLERS && !vibrationEnabledSlots[slot])
            return;

        int duration = Math.max(1, durationMs);
        boolean stopping = strong <= 0 && weak <= 0;

        // Controller (physical, or OSC/phone-fallback exactly as before) — Controller and Both modes.
        if (mode == VIBRATION_MODE_CONTROLLER || mode == VIBRATION_MODE_BOTH) {
            dispatchControllerVibration(strong, weak, duration, slot, stopping);
        }
        // Phone's own vibrator, independent of slot mapping — Device and Both modes.
        if (mode == VIBRATION_MODE_DEVICE || mode == VIBRATION_MODE_BOTH) {
            dispatchDeviceVibration(strong, weak, duration, stopping);
        }
    }

    /**
     * Resolves which deviceId should receive rumble for a slot. Prefers OSC (phone vibrator)
     * when it owns the slot, then a physical sub-device that actually exposes a vibrator, then
     * the first physical sub-device. deviceToSlot is a ConcurrentHashMap, so this iteration is
     * safe on the vibration thread. Returns null when nothing owns the slot.
     */
    private Integer resolveSlotOwnerDeviceId(int slot) {
        Integer firstPhysical = null;
        Integer withVibrator = null;
        for (Map.Entry<Integer, Integer> entry : deviceToSlot.entrySet()) {
            if (entry.getValue() != slot)
                continue;
            int devId = entry.getKey();
            if (devId == OSC_DEVICE_ID)
                return OSC_DEVICE_ID;
            if (firstPhysical == null)
                firstPhysical = devId;
            if (withVibrator == null && sdlPads.containsKey(devId)) {
                withVibrator = devId; // Steam Controller rumbles through SDL
            } else if (withVibrator == null) {
                android.view.InputDevice d = android.view.InputDevice.getDevice(devId);
                if (d != null) {
                    Vibrator v = d.getVibrator();
                    if (v != null && v.hasVibrator())
                        withVibrator = devId;
                }
            }
        }
        return withVibrator != null ? withVibrator : firstPhysical;
    }

    /**
     * "Identify this pad" buzz for the in-game Players controller-test panel. Fires a short, firm
     * rumble on the slot's owner device, going STRAIGHT to {@link #dispatchControllerVibration} so it
     * bypasses the per-container vibration mode/master gate — this is an explicit user action in the
     * test panel, not game-driven rumble, so it must work even when in-game vibration is set to Off.
     * Main-thread only.
     */
    public void testRumble(int slot) {
        if (slot < 0 || slot >= MAX_CONTROLLERS)
            return;
        // strong/weak are raw 0..65535 (same scale the guest sends); dispatchControllerVibration runs
        // them through rawToAmplitude/applyIntensity exactly like an in-game rumble.
        dispatchControllerVibration(48000, 32000, 420, slot, false);
    }

    /**
     * True when the slot's resolved owner device actually exposes a vibrator (physical pad motor, or
     * the phone vibrator when OSC owns the slot). Lets the test panel disable the Identify button for
     * pads with no rumble. Main-thread only.
     */
    public boolean slotHasVibrator(int slot) {
        if (slot < 0 || slot >= MAX_CONTROLLERS)
            return false;
        Integer deviceId = resolveSlotOwnerDeviceId(slot);
        if (deviceId == null)
            return false;
        if (deviceId == OSC_DEVICE_ID) {
            Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            return v != null && v.hasVibrator();
        }
        if (sdlPads.containsKey(deviceId))
            return true;
        android.view.InputDevice d = android.view.InputDevice.getDevice(deviceId);
        if (d == null)
            return false;
        Vibrator v = d.getVibrator();
        return v != null && v.hasVibrator();
    }

    /**
     * Controller-mode dispatch: resolves the same deviceId-owns-slot / OSC-or-no-vibrator phone
     * fallback that this method always used, then delivers via independent low/high motors
     * (VibratorManager, API 31+) when the target exposes ≥1 vibrator id, blending to one motor
     * otherwise. Below API 31 this always blends — identical to the pre-dual-motor behavior.
     */
    private void dispatchControllerVibration(int strong, int weak, int duration, int slot, boolean stopping) {
        // Which deviceId owns this slot. With descriptor keying one slot can now hold
        // several sub-device ids for a single physical pad, so resolve to the one that
        // actually has a vibrator (falling back to OSC / first physical) — for a plain
        // single-device pad this returns exactly the device it always did.
        Integer deviceId = resolveSlotOwnerDeviceId(slot);

        // Steam Controller (SDL): both motors map straight onto SDL's low/high rumble. The container
        // intensity scales the raw 0..65535 values the same way applyIntensity scales amplitudes.
        SteamControllerBackend backend = sdlBackend;
        if (deviceId != null && backend != null && sdlPads.containsKey(deviceId)) {
            int pct = Math.max(0, Math.min(100, vibrationIntensity));
            int low = stopping ? 0 : (strong * pct) / 100;
            int high = stopping ? 0 : (weak * pct) / 100;
            backend.rumble(deviceId, low, high, stopping ? 0 : duration);
            return;
        }

        android.view.InputDevice device = null;
        Vibrator fallbackVibrator = null;

        if (deviceId != null && deviceId == OSC_DEVICE_ID) {
            // OSC is mapped to this slot — use the phone vibrator
            fallbackVibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        } else if (deviceId != null) {
            // Physical controller
            android.view.InputDevice candidate = android.view.InputDevice.getDevice(deviceId);
            if (candidate != null) {
                Vibrator v = candidate.getVibrator();
                if (v != null && v.hasVibrator()) {
                    device = candidate;
                } else {
                    // Fallback to phone vibrator if OSC is off and no other controller has fallen back
                    if (!deviceToSlot.containsKey(OSC_DEVICE_ID) && (fallbackSlot == -1 || fallbackSlot == slot)) {
                        fallbackVibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
                        fallbackSlot = slot;
                    }
                }
            }
        }

        if (stopping) {
            if (device != null) stopVibrationTarget(device);
            if (fallbackVibrator != null) fallbackVibrator.cancel();
            return;
        }

        if (device != null) {
            if (!dispatchDualMotor(device, strong, weak, duration)) {
                Vibrator v = device.getVibrator();
                if (v != null && v.hasVibrator()) {
                    vibrateBlended(v, strong, weak, duration);
                }
            }
            return;
        }

        if (fallbackVibrator != null && fallbackVibrator.hasVibrator()) {
            vibrateBlended(fallbackVibrator, strong, weak, duration);
        }
    }

    /** Device-mode dispatch: always the phone's own vibrator, regardless of which slot/controller
     *  triggered the rumble. Single-motor blend — phones don't expose independent XInput motors. */
    private void dispatchDeviceVibration(int strong, int weak, int duration, boolean stopping) {
        Vibrator vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator())
            return;

        if (stopping) {
            vibrator.cancel();
        } else {
            vibrateBlended(vibrator, strong, weak, duration);
        }
    }

    /**
     * Dual-motor delivery for a physical controller on API 31+: strong (low-frequency) drives the
     * first vibrator id, weak (high-frequency) drives the second, dispatched together via
     * CombinedVibration so both motors start in the same frame. Falls back to a single blended
     * motor when the device exposes exactly one vibrator id. Returns false (caller should fall
     * back to {@link #vibrateBlended}) when below API 31, the device has no VibratorManager, or
     * both scaled amplitudes are zero.
     */
    private boolean dispatchDualMotor(android.view.InputDevice device, int strong, int weak, long duration) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S)
            return false;

        android.os.VibratorManager vm = device.getVibratorManager();
        if (vm == null)
            return false;

        int[] ids = vm.getVibratorIds();
        if (ids == null || ids.length == 0)
            return false;

        // Sort ascending for a deterministic "low motor = ids[0], high motor = ids[1]" assignment.
        int[] sortedIds = ids.clone();
        java.util.Arrays.sort(sortedIds);

        int strongAmp = rawToAmplitude(strong);
        int weakAmp = rawToAmplitude(weak);
        if (strongAmp == 0 && weakAmp == 0)
            return false;

        if (sortedIds.length == 1) {
            Vibrator only = vm.getVibrator(sortedIds[0]);
            if (only == null) return false;
            // strongAmp/weakAmp are already intensity-scaled (rawToAmplitude) — just take the max,
            // no further intensity scaling here.
            int blended = Math.min(255, Math.max(1, Math.max(strongAmp, weakAmp)));
            only.vibrate(VibrationEffect.createOneShot(duration, blended));
            return true;
        }

        android.os.CombinedVibration.ParallelCombination combo = android.os.CombinedVibration.startParallel();
        boolean any = false;
        if (strongAmp > 0) {
            combo.addVibrator(sortedIds[0], VibrationEffect.createOneShot(duration, strongAmp));
            any = true;
        }
        if (weakAmp > 0) {
            combo.addVibrator(sortedIds[1], VibrationEffect.createOneShot(duration, weakAmp));
            any = true;
        }
        if (!any) return false;

        vm.vibrate(combo.combine());
        return true;
    }

    /** Stops an in-flight dual-motor rumble on a physical controller (VibratorManager cancel on
     *  API 31+, single-Vibrator cancel otherwise). */
    private void stopVibrationTarget(android.view.InputDevice device) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.os.VibratorManager vm = device.getVibratorManager();
            if (vm != null) {
                vm.cancel();
                return;
            }
        }
        Vibrator v = device.getVibrator();
        if (v != null) v.cancel();
    }

    /** Single-motor blend of strong+weak using the pre-dual-motor formula (max of the two, scaled to
     *  0..255), then applies the per-container intensity. Used for the API<31 fallback and for any
     *  single-vibrator target (phone, or a controller with exactly one motor id). */
    private void vibrateBlended(Vibrator vibrator, int strong, int weak, long duration) {
        int intensity = Math.max(strong, weak);
        int amplitude = Math.min(255, Math.max(1, (int) ((intensity / 65535.0f) * 255)));
        int scaled = applyIntensity(amplitude);
        // Per-container intensity 0 (e.g. vibration set to 0 in the in-game side menu) drives scaled
        // to 0; VibrationEffect.createOneShot rejects amplitude 0 with IllegalArgumentException and
        // crashes the vibration-listener thread. Treat 0 as silence — matching the dual-motor path's
        // `amp > 0` guards and applyIntensity's documented "0 stays 0". Also skips the pre-O legacy
        // vibrate() so intensity 0 is truly silent, not a full-strength buzz.
        if (scaled <= 0) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, scaled));
        } else {
            vibrator.vibrate(duration);
        }
    }

    /** Raw XInput amplitude (0..65535) -> 0..255, then scaled by the per-container intensity
     *  (floored at 1 so a non-zero input never quantises away to silence, 0 stays 0). */
    private int rawToAmplitude(int raw) {
        if (raw <= 0) return 0;
        int amplitude = Math.min(255, Math.max(1, (int) ((raw / 65535.0f) * 255)));
        return applyIntensity(amplitude);
    }

    /** Applies the per-container intensity (0..100) to an already-0..255 amplitude. */
    private int applyIntensity(int amplitude255) {
        if (amplitude255 <= 0 || vibrationIntensity <= 0) return 0;
        int scaled = (amplitude255 * vibrationIntensity) / 100;
        return Math.min(255, Math.max(1, scaled));
    }

    public boolean isVibrationEnabledForSlot(int slot) {
        if (slot >= 0 && slot < MAX_CONTROLLERS)
            return vibrationEnabledSlots[slot];
        return false;
    }

    public void setVibrationEnabledForSlot(int slot, boolean enabled) {
        if (slot >= 0 && slot < MAX_CONTROLLERS) {
            vibrationEnabledSlots[slot] = enabled;
            preferences.edit().putBoolean("vibration_slot_" + slot, enabled).apply();
        }
    }

    /** Master controller-vibration switch (persisted globally). Off = ALL controller rumble suppressed. */
    public boolean isVibrationMasterEnabled() {
        return vibrationMasterEnabled;
    }

    public void setVibrationMasterEnabled(boolean enabled) {
        vibrationMasterEnabled = enabled;
        preferences.edit().putBoolean("vibration_master_enabled", enabled).apply();
    }

    /** Per-container rumble mode/intensity, pushed once at launch (setupUI, after the Container is
     *  resolved) and again live from the in-game drawer / container editor. NOT persisted here —
     *  Container.setVibrationMode/setVibrationIntensity is the source of truth; this is just the
     *  live cache triggerVibration reads on the dispatch thread. Invalid mode falls back to
     *  Controller (matches Container's own default) rather than silently doing nothing.
     */
    public void setVibrationTuning(int mode, int intensity) {
        vibrationMode = (mode < VIBRATION_MODE_OFF || mode > VIBRATION_MODE_BOTH) ? VIBRATION_MODE_CONTROLLER : mode;
        vibrationIntensity = Math.min(100, Math.max(0, intensity));
    }

    /** Per-container on-screen-controls priority mode (KEEP/YIELD/SHARE). Pushed at launch (setupUI,
     *  after the Container is resolved), the same seed-after-container discipline as setVibrationTuning.
     *  Invalid values fall back to KEEP so an existing container's behavior never changes by accident. */
    public void setOnScreenControllerMode(int mode) {
        onScreenControllerMode = (mode < ON_SCREEN_MODE_KEEP || mode > ON_SCREEN_MODE_SHARE)
                ? ON_SCREEN_MODE_KEEP : mode;
    }

    public int getOnScreenControllerMode() {
        return onScreenControllerMode;
    }

    public int getVibrationMode() {
        return vibrationMode;
    }

    public int getVibrationIntensity() {
        return vibrationIntensity;
    }

    public int getMaxControllers() {
        return MAX_CONTROLLERS;
    }

    private void handleRequest(byte requestCode, final int port) {
        switch (requestCode) {
            case RequestCodes.INIT: {
                initReceived = true;
                Log.d("WinHandlerTM", "INIT received from guest");

                preferences = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());

                if (!xinputDisabledInitialized) {
                    xinputDisabled = preferences.getBoolean("xinput_toggle", false);
                }
                synchronized (actions) {
                    actions.notify();
                }
                break;
            }

            case RequestCodes.GET_PROCESS: {
                if (onGetProcessInfoListener == null)
                    return;
                receiveData.position(receiveData.position() + 4);
                int numProcesses = receiveData.getShort();
                int index = receiveData.getShort();
                int pid = receiveData.getInt();
                long memoryUsage = receiveData.getLong();
                int affinityMask = receiveData.getInt();
                boolean wow64Process = receiveData.get() == 1;

                byte[] bytes = new byte[32];
                receiveData.get(bytes);
                String name = StringUtils.fromANSIString(bytes);

                Log.d("WinHandlerTM", "GET_PROCESS idx=" + index + "/" + numProcesses + " name=" + name);
                onGetProcessInfoListener.onGetProcessInfo(index, numProcesses,
                        new ProcessInfo(pid, name, memoryUsage, affinityMask, wow64Process));
                break;
            }
            case RequestCodes.GET_GAMEPAD: {
                break;
            }
            case RequestCodes.GET_GAMEPAD_STATE: {
                break;
            }
            case RequestCodes.RELEASE_GAMEPAD: {
                // currentController = null; // No longer needed
                // Maybe clear all controllers or reset mapping?
                // For now, doing nothing is safest as mapping is sticky.
            }
            case RequestCodes.CURSOR_POS_FEEDBACK: {
                short x = receiveData.getShort();
                short y = receiveData.getShort();
                XServer xServer = activity.getXServer();
                xServer.pointer.setX(x);
                xServer.pointer.setY(y);
                activity.getXServerView().requestRender();
                break;
            }
            default: {
                // Handle any other request codes if needed
                break;
            }
        }
    }

    public void start() {
        try {
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            try {
                localhost = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException ex) {
            }
        }

        running = true;
        startSendThread();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress((InetAddress) null, SERVER_PORT));

                while (running) {
                    socket.receive(receivePacket);

                    synchronized (actions) {
                        receiveData.rewind();
                        byte requestCode = receiveData.get();
                        Log.d("WinHandlerTM", "recv code=" + requestCode + " from port " + receivePacket.getPort());
                        handleRequest(requestCode, receivePacket.getPort());
                    }
                }
            } catch (IOException e) {
            }
        });
    }

    public void sendGamepadState() {
        final InputControlsView inputControlsView = activity.getInputControlsView();
        if (inputControlsView == null) return;
        final ControlsProfile profile = inputControlsView.getProfile();
        if (profile == null) {
            releaseSlot(OSC_DEVICE_ID);
            return;
        }

        final GamepadState gamepadState = profile.getGamepadState();
        final boolean useVirtualGamepad = profile.isVirtualGamepad()
                && inputControlsView.isShowTouchscreenControls();

        // Handle virtual gamepad (on-screen controls)
        if (useVirtualGamepad) {
            int slot = assignSlot(OSC_DEVICE_ID);
            writeSlotState(slot, OSC_DEVICE_ID, getOutputGamepadState(gamepadState));
        } else {
            releaseSlot(OSC_DEVICE_ID);
        }

    }

    // Menu owns the controller while the drawer is open; zero tracked pad state and
    // push it once through the ring so nothing (held stick, pressed button, latched
    // trigger) stays applied in the guest while the user isn't looking. Preserves
    // our slot pinning and per-slot rumble tuning (rumble state lives outside
    // GamepadState and is not touched here).
    //
    // Ported from WinNative-Emu/WinNative PR #603.
    public void neutralizeControllers() {
        for (ExternalController controller : this.controllers.values()) {
            if (controller == null) continue;
            clearGamepadState(controller.state);
            clearGamepadState(controller.remappedState);
            int slot = assignSlot(controller.getDeviceId());
            if (slot >= 0 && slot < MAX_CONTROLLERS && this.writers[slot] != null) {
                this.writers[slot].writeGamepadState(controller.state);
            }
        }
    }

    private static void clearGamepadState(GamepadState state) {
        state.thumbLX = 0;
        state.thumbLY = 0;
        state.thumbRX = 0;
        state.thumbRY = 0;
        state.triggerL = 0;
        state.triggerR = 0;
        state.buttons = 0;
        state.dpad[0] = state.dpad[1] = state.dpad[2] = state.dpad[3] = false;
    }

    // Re-push every live physical controller's current state through the slot pipeline, re-evaluating
    // the physical-profile remap gate. Used when the physical lane changes (InputControlsView
    // .setPhysicalProfile) so a switch to passthrough (raw resumes) or a bound profile (remap/silence)
    // applies at once instead of waiting for the next input event. Mirrors releaseAllControllerInputs.
    public void refreshControllerStates() {
        for (ExternalController controller : controllers.values()) {
            if (controller != null) sendGamepadState(controller);
        }
    }

    public void sendGamepadState(ExternalController controller) {
        if (controller == null)
            return;

        // Steam Controller (SDL): no profile lookup. The raw pad object sends its own state; the
        // bindings view InputControlsView runs for it (same synthetic deviceId, see onSteamPadState)
        // sends its remapped output.
        int deviceId = controller.getDeviceId();
        if (deviceId <= SteamControllerBackend.DEVICE_ID_BASE) {
            ExternalController sdlPad = sdlPads.get(deviceId);
            if (sdlPad == null && !deviceToSlot.containsKey(deviceId))
                return; // gone and already released — never claim a slot for it
            gyroTargetController = controller;
            int slot = assignSlot(deviceId);
            writeSlotState(slot, deviceId,
                    getOutputGamepadState(controller == sdlPad ? controller.state : controller.remappedState));
            return;
        }

        // Remember the live controller so the gyro can re-push through it between input events.
        gyroTargetController = controller;

        // Check if this controller has bindings in the current profile
        // If it does, we should NOT send the raw state here, because InputControlsView
        // will send the remapped state via the no-arg sendGamepadState().
        InputControlsView inputControlsView = activity.getInputControlsView();
        ControlsProfile profile = inputControlsView != null ? inputControlsView.getPhysicalProfile() : null;
        if (profile != null) {
            ExternalController profileController = profile.getController(controller.getDeviceId());
            if (profileController != null && profileController.getControllerBindingCount() > 0) {
                // If bindings are present, use the remappedState from the controller
                // This reverts the single-slot consolidation where the no-arg
                // sendGamepadState()
                // was solely responsible for sending remapped states.
                int slot = assignSlot(controller.getDeviceId());
                writeSlotState(slot, controller.getDeviceId(), getOutputGamepadState(controller.remappedState));
                return; // Suppress raw state sending if remapped state was sent
            }
        }

        int slot = assignSlot(controller.getDeviceId());
        writeSlotState(slot, controller.getDeviceId(), getOutputGamepadState(controller.state));
    }

    /**
     * Writes one contributor's state to a slot, applying the SHARED-slot merge when needed.
     *
     * Common case (slot has a single owner — every OSC-only or pad-only session, and every
     * auto-assigned multi-pad session): slotShared[slot] is false, so this is a plain
     * writers[slot].writeGamepadState(state) — byte-identical to the pre-sharing code path.
     *
     * Shared case (>=2 contributors on the slot — manual same-slot pins or On-screen SHARE mode):
     * snapshot this contributor's latest state under its descriptor key, then write the MERGE of
     * every contributor's latest snapshot. The merge ORs all buttons/dpad and takes the
     * largest-magnitude stick axis / largest trigger, so a neutral frame from one contributor
     * (notably OSC, which sends a full mostly-neutral state every frame) can NEVER overwrite
     * another contributor's held input.
     */
    private void writeSlotState(int slot, int deviceId, GamepadState state) {
        if (slot < 0 || slot >= MAX_CONTROLLERS || writers[slot] == null || state == null)
            return;

        if (!slotShared[slot]) {
            writers[slot].writeGamepadState(state);
            return;
        }

        String key = (deviceId == OSC_DEVICE_ID) ? OSC_DESCRIPTOR : deviceToDescriptor.get(deviceId);
        if (key == null)
            key = "dev:" + deviceId;

        Map<String, GamepadState> contrib = slotContributions[slot];
        if (contrib == null) {
            contrib = new HashMap<>();
            slotContributions[slot] = contrib;
        }
        GamepadState snap = contrib.get(key);
        if (snap == null) {
            snap = new GamepadState();
            contrib.put(key, snap);
        }
        snap.copy(state);

        GamepadState merged = slotMerged[slot];
        if (merged == null) {
            merged = new GamepadState();
            slotMerged[slot] = merged;
        }
        mergeContributions(contrib, merged);
        writers[slot].writeGamepadState(merged);
    }

    /** Combines every contributor's latest state into {@code out}: buttons and the four D-pad
     *  directions are OR'd (any device pressing wins); each stick axis takes the value with the
     *  largest magnitude (the actively-deflected one beats a centred one); each trigger takes the
     *  max. Neutral contributions therefore drop out of the result entirely. */
    private void mergeContributions(Map<String, GamepadState> contrib, GamepadState out) {
        out.reset();
        for (GamepadState s : contrib.values()) {
            if (s == null) continue;
            out.buttons |= s.buttons;
            for (int i = 0; i < out.dpad.length; i++)
                out.dpad[i] |= s.dpad[i];
            if (Math.abs(s.thumbLX) > Math.abs(out.thumbLX)) out.thumbLX = s.thumbLX;
            if (Math.abs(s.thumbLY) > Math.abs(out.thumbLY)) out.thumbLY = s.thumbLY;
            if (Math.abs(s.thumbRX) > Math.abs(out.thumbRX)) out.thumbRX = s.thumbRX;
            if (Math.abs(s.thumbRY) > Math.abs(out.thumbRY)) out.thumbRY = s.thumbRY;
            if (s.triggerL > out.triggerL) out.triggerL = s.triggerL;
            if (s.triggerR > out.triggerR) out.triggerR = s.triggerR;
        }
    }

    /** Recomputes slotShared[] from live occupancy: a slot is shared when >=2 distinct contributors
     *  hold it (the OSC virtual pad counted via deviceToSlot, physical pads via descriptorToSlot, which
     *  is descriptor-keyed so sibling sub-devices of ONE pad count once). Called only when slot
     *  occupancy changes — never on the per-frame input path. Clears the contribution/merge scratch for
     *  any slot that is no longer shared so a stale snapshot can't linger. */
    private void recomputeSharedSlots() {
        int[] counts = new int[MAX_CONTROLLERS];
        Integer oscSlot = deviceToSlot.get(OSC_DEVICE_ID);
        if (oscSlot != null && oscSlot >= 0 && oscSlot < MAX_CONTROLLERS)
            counts[oscSlot]++;
        for (Integer s : descriptorToSlot.values())
            if (s != null && s >= 0 && s < MAX_CONTROLLERS)
                counts[s]++;
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            boolean shared = counts[i] >= 2;
            slotShared[i] = shared;
            if (!shared && slotContributions[i] != null)
                slotContributions[i].clear();
        }
    }

    /** Wipes all shared-slot bookkeeping (flags + contributor/merge scratch). Used by the reset/close
     *  paths that also clear the slot maps. */
    private void clearSharedSlotState() {
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            slotShared[i] = false;
            if (slotContributions[i] != null) slotContributions[i].clear();
        }
    }

    /**
     * On-screen priority for a pad that just connected (onInputDeviceAdded), per the per-container
     * On-screen mode. KEEP is a no-op (returns false → the pad auto-assigns normally). YIELD, when OSC
     * currently holds Player 1, bumps OSC up to the next free slot so the incoming pad takes Player 1
     * via the normal FCFS assign (returns false so that assign still runs). SHARE co-occupies OSC's slot
     * with the pad (returns true — the pad is already seated via the merge, skip the normal assign).
     * Only steers the AUTO case: an explicit pin/ignore on the pad or on OSC is always respected.
     */
    private boolean handleOnScreenModeForNewPad(int deviceId) {
        int mode = onScreenControllerMode;
        if (mode == ON_SCREEN_MODE_KEEP)
            return false;
        if (deviceToSlot.containsKey(deviceId))
            return false;
        if (!sdlPads.containsKey(deviceId)) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
            if (!ExternalController.isGameController(device) || isShadowedBySdl(device))
                return false;
        }
        String descriptor = descriptorOf(deviceId);
        if (descriptor == null)
            return false;
        // Respect an explicit pin/ignore on the incoming pad — these modes only steer un-pinned pads.
        if (manualSlotOverrides.get(descriptor) != null)
            return false;
        // OSC must currently hold Player 1 (slot 0) for either mode to have anything to do.
        Integer oscSlot = deviceToSlot.get(OSC_DEVICE_ID);
        if (oscSlot == null || oscSlot != 0)
            return false;
        // Respect an explicit OSC pin — never move a user-pinned on-screen pad.
        if (manualSlotOverrides.get(OSC_DESCRIPTOR) != null)
            return false;

        if (mode == ON_SCREEN_MODE_SHARE) {
            // Co-occupy OSC's Player-1 slot: the per-slot merge lets the pad and OSC both drive it.
            if (getController(deviceId) == null)
                return false;
            int slot = joinSharedSlot(deviceId, descriptor, oscSlot);
            Log.d("WinHandler", "On-screen SHARE: pad " + deviceId + " joined OSC slot " + slot);
            return slot >= 0;
        }

        // YIELD: move OSC to the lowest free HIGHER slot so the pad can take Player 1 via FCFS.
        int free = -1;
        for (int s = 1; s < MAX_CONTROLLERS; s++) {
            if (!usedSlots.contains(s)) { free = s; break; }
        }
        if (free < 0)
            return false; // no room to move OSC — leave things as they are (safe)
        releaseSlot(OSC_DEVICE_ID);         // softRelease: frees slot 0, OSC ring stays alive
        oscYieldSlot = free;                // session-only OSC preference honored by assignSlot(OSC)
        int newOscSlot = assignSlot(OSC_DEVICE_ID);
        Log.d("WinHandler", "On-screen YIELD: OSC moved to slot " + newOscSlot
                + "; incoming pad " + deviceId + " will take Player 1");
        return false; // let assignConnectedDeviceIfPossible seat the pad on the now-free slot 0
    }

    /**
     * Feeds one gyroscope sample (rad/s about the device X and Y axes) into the configured target
     * (right stick, left stick or the mouse pointer). Called on the main thread from the activity's
     * SensorEventListener, so it shares a thread with the controller/OSC input path and needs no
     * extra synchronization.
     */
    public void updateGyroData(float rawGyroX, float rawGyroY) {
        if (!gyroConfigApplied || !gyroEnabled) {
            resetGyroRuntimeState();
            return;
        }

        // Calibration bias first, and deliberately so: BEFORE the deadzone (otherwise the deadzone has
        // to exceed the bias just to hold still, which is what makes fine aim mushy), BEFORE invert
        // (an inverted axis would subtract with the wrong sign), BEFORE sensitivity (the bias is in
        // rad/s, sensitivity is a unitless gain) and BEFORE the low-pass (which would otherwise
        // converge on bias*sensitivity and never let the stick recenter). Uncalibrated = 0 = no-op.
        rawGyroX -= gyroBiasX;
        rawGyroY -= gyroBiasY;

        // Toggle-mode edge, evaluated HERE and nowhere else. Two reasons it can't move:
        //  - not into sendGamepadState(): it has several call sites and the gyro re-enters it itself
        //    (pushGyroToActiveTarget), and the raw/remapped split means one physical press can arrive
        //    twice — the latch would flip twice and land back where it started.
        //  - not below the deadzone block: with the device held still both axes deadzone to zero and
        //    this method returns early further down, so a tap made while holding still would never be
        //    seen at all.
        // Caveat by design: the edge is sampled at the sensor rate (SENSOR_DELAY_GAME, ~50 Hz worst
        // case), so a sub-20ms tap can slip between samples. Real presses run 60-150ms, so this is
        // fine — don't "fix" it by moving the edge into the input event path.
        updateGyroActivation();

        if (Math.abs(rawGyroX) < gyroDeadzone)
            rawGyroX = 0.0f;
        if (Math.abs(rawGyroY) < gyroDeadzone)
            rawGyroY = 0.0f;

        if (gyroInvertX)
            rawGyroX = -rawGyroX;
        if (gyroInvertY)
            rawGyroY = -rawGyroY;

        if (gyroTarget == GYRO_TARGET_MOUSE) {
            updateGyroMouse(rawGyroX, rawGyroY);
            return;
        }

        rawGyroX *= gyroSensitivity;
        rawGyroY *= gyroSensitivity;

        final float smoothing = gyroSmoothing;
        smoothedGyroX = (smoothedGyroX * smoothing) + (rawGyroX * (1.0f - smoothing));
        smoothedGyroY = (smoothedGyroY * smoothing) + (rawGyroY * (1.0f - smoothing));

        float nextGyroStickX = clamp(smoothedGyroX, -1.0f, 1.0f);
        float nextGyroStickY = clamp(smoothedGyroY, -1.0f, 1.0f);
        // Snap the low-pass tail to exact zero so the stick fully recenters once motion stops.
        if (Math.abs(nextGyroStickX) < GYRO_AXIS_EPSILON) nextGyroStickX = 0.0f;
        if (Math.abs(nextGyroStickY) < GYRO_AXIS_EPSILON) nextGyroStickY = 0.0f;
        if (Math.abs(nextGyroStickX - currentGyroStickX) <= GYRO_AXIS_EPSILON
                && Math.abs(nextGyroStickY - currentGyroStickY) <= GYRO_AXIS_EPSILON) {
            return;
        }
        currentGyroStickX = nextGyroStickX;
        currentGyroStickY = nextGyroStickY;

        pushGyroToActiveTarget();
    }

    /**
     * Feeds one ORIENTATION sample (yaw and pitch in radians, already remapped for the display
     * rotation by the activity) into the configured stick. Deliberately a separate entry point rather
     * than a branch inside updateGyroData: rate mode is the shipped, device-proven path and stays
     * textually untouched, so selecting rate mode is a provable no-op. The activity registers exactly
     * one sensor for the selected mode, so only one of the two methods is ever called in a session.
     *
     * The pipeline mirrors rate mode — deadzone, invert, sensitivity, low-pass, clamp — but on the
     * ANGLE OFFSET from a captured zero instead of an angular rate, so a held tilt sustains the
     * deflection. Same thread and the same allocation-free rules as updateGyroData.
     */
    public void updateGyroOrientation(float yaw, float pitch) {
        if (!gyroConfigApplied || !gyroEnabled) {
            resetGyroRuntimeState();
            return;
        }

        // NOT subtracted here, on purpose: the calibration bias is a zero-RATE offset in rad/s and the
        // input on this path is an angle, so the subtraction would be dimensionally meaningless. The
        // zero-reference capture below already cancels any constant offset, which is what the bias
        // does for rate mode. The bias stays live for rate mode and its UI stays visible in both.

        // Same single call site as rate mode — this is the only per-sample hook in orientation mode,
        // so the toggle latch would never see an edge without it.
        updateGyroActivation();

        if (!isGyroActivatorHeld()) {
            // Inactive: drop the zero reference so the NEXT activation recaptures centre (this is the
            // auto-recenter for Hold and Toggle alike — Hold never reaches updateGyroActivation), then
            // clear the filter and the overlay so the stick doesn't stay deflected.
            gyroOrientationCalibrated = false;
            smoothedGyroX = 0.0f;
            smoothedGyroY = 0.0f;
            clearGyroStickOverlay();
            return;
        }

        // Gimbal guard: hold the previous deflection instead of emitting a garbage yaw. Placed before
        // the capture too, so a zero reference can't be taken from a pose where yaw is undefined.
        if (Math.abs(pitch) > GYRO_ORIENTATION_GIMBAL_LIMIT)
            return;

        if (!gyroOrientationCalibrated) {
            orientationYawZero = yaw;
            orientationPitchZero = pitch;
            gyroOrientationCalibrated = true;
        }

        float deltaX = wrapAngle(yaw - orientationYawZero);
        float deltaY = wrapAngle(pitch - orientationPitchZero);

        // Same setting as rate mode, reinterpreted: here it's a dead CONE around the captured centre
        // rather than a minimum rate, so the stock 0.05 reads as ~2.9 degrees of slop. One setting on
        // purpose — a second "orientation deadzone" would be one more knob describing the same hand.
        if (Math.abs(deltaX) < gyroDeadzone)
            deltaX = 0.0f;
        if (Math.abs(deltaY) < gyroDeadzone)
            deltaY = 0.0f;

        if (gyroInvertX)
            deltaX = -deltaX;
        if (gyroInvertY)
            deltaY = -deltaY;

        deltaX *= gyroSensitivity * GYRO_ORIENTATION_STICK_GAIN;
        deltaY *= gyroSensitivity * GYRO_ORIENTATION_STICK_GAIN;

        final float smoothing = gyroSmoothing;
        smoothedGyroX = (smoothedGyroX * smoothing) + (deltaX * (1.0f - smoothing));
        smoothedGyroY = (smoothedGyroY * smoothing) + (deltaY * (1.0f - smoothing));

        // The clamp is load-bearing here in a way it isn't in rate mode: tilt far enough and the raw
        // gain runs well past 1.0, and the stick has to saturate rather than wrap or overshoot.
        float nextGyroStickX = clamp(smoothedGyroX, -1.0f, 1.0f);
        float nextGyroStickY = clamp(smoothedGyroY, -1.0f, 1.0f);
        if (Math.abs(nextGyroStickX) < GYRO_AXIS_EPSILON) nextGyroStickX = 0.0f;
        if (Math.abs(nextGyroStickY) < GYRO_AXIS_EPSILON) nextGyroStickY = 0.0f;
        // Keep this early-out: a held tilt is near-constant, so without it every single sample would
        // re-push an identical gamepad state for as long as the user holds the device still.
        if (Math.abs(nextGyroStickX - currentGyroStickX) <= GYRO_AXIS_EPSILON
                && Math.abs(nextGyroStickY - currentGyroStickY) <= GYRO_AXIS_EPSILON) {
            return;
        }
        currentGyroStickX = nextGyroStickX;
        currentGyroStickY = nextGyroStickY;

        logGyroOrientation(nextGyroStickX, nextGyroStickY);
        pushGyroToActiveTarget();
    }

    /**
     * Wraps a radian difference back into [-PI, PI] so crossing the yaw discontinuity doesn't throw
     * the stick to the opposite extreme. Two branches rather than Math.IEEEremainder: the inputs are
     * two angles already in [-PI, PI], so the difference is in [-2PI, 2PI] and one correction is
     * always enough — and this costs a compare instead of a libm call on the sensor path.
     */
    private static float wrapAngle(float radians) {
        if (radians > (float) Math.PI) radians -= (float) (2.0 * Math.PI);
        else if (radians < -(float) Math.PI) radians += (float) (2.0 * Math.PI);
        return radians;
    }

    /**
     * Rate-limited debug of the computed deflection, used to verify the axis mapping and the invert
     * handedness on real hardware (`adb shell setprop log.tag.BLGyroOrient DEBUG`). isLoggable is a
     * cached property read, so with the tag off this is a compare and a return.
     */
    private void logGyroOrientation(float stickX, float stickY) {
        if (!Log.isLoggable(GYRO_ORIENT_LOG_TAG, Log.DEBUG))
            return;
        long now = SystemClock.uptimeMillis();
        if (now - lastGyroOrientLogMs < GYRO_ORIENT_LOG_INTERVAL_MS)
            return;
        lastGyroOrientLogMs = now;
        Log.d(GYRO_ORIENT_LOG_TAG, "deltaX=" + stickX + " deltaY=" + stickY + " target=" + gyroTarget);
    }

    /**
     * Manual recenter: the next orientation sample recaptures the current pose as centre. The drawer's
     * Recenter row is the only caller and it is NOT optional — with the "Always" activator there is no
     * rising edge for the whole session, so this is the only way to fix a drifted centre.
     */
    public void recenterGyroOrientation() {
        gyroOrientationCalibrated = false;
        smoothedGyroX = 0.0f;
        smoothedGyroY = 0.0f;
    }

    /**
     * Re-injects the current gamepad state for whichever target holds the activator, so a sustained
     * tilt keeps panning even while no controller/touch event is arriving. A no-op when the
     * activator isn't held, and FakeInputWriter still diffs the axes, so an unchanged state writes
     * nothing.
     */
    private void pushGyroToActiveTarget() {
        ExternalController controller = gyroTargetController;
        if (controller != null && (isGyroTargetActive(controller.state)
                || isGyroTargetActive(controller.remappedState))) {
            sendGamepadState(controller);
            return;
        }
        if (activity.getInputControlsView() == null)
            return;
        ControlsProfile profile = activity.getInputControlsView().getProfile();
        if (profile != null && isGyroTargetActive(profile.getGamepadState())) {
            sendGamepadState();
        }
    }

    /**
     * Same walk as pushGyroToActiveTarget, but WITHOUT the activation gate. Used the moment the toggle
     * un-latches: the button press that turned it off was injected while the latch was still on (the
     * edge is detected here on the sensor path, not in the event path), so the game is holding a state
     * that still carries the gyro deflection and the gated push would refuse to correct it.
     */
    private void pushGyroClearToLastTarget() {
        ExternalController controller = gyroTargetController;
        if (controller != null) {
            sendGamepadState(controller);
            return;
        }
        if (activity.getInputControlsView() == null)
            return;
        if (activity.getInputControlsView().getProfile() != null)
            sendGamepadState();
    }

    /**
     * Toggle-mode edge detect — called exactly once per gyro sample from updateGyroData. Reads the
     * activator off the same sources isGyroActivatorHeld() walks (the target controller's raw and
     * remapped state, then the on-screen-controls profile), so the latch responds to whichever one the
     * user is actually pressing. Hold mode and "Always" leave immediately: nothing to latch, and the
     * early-out keeps the hot path exactly as cheap as it was before toggle existed.
     */
    private void updateGyroActivation() {
        if (gyroActivationMode != GYRO_ACTIVATION_TOGGLE || gyroActivator == GYRO_ACTIVATOR_ALWAYS)
            return;

        // OR across every source, exactly as isGyroActivatorHeld does — a pad can be connected without
        // being the thing the user is pressing, so falling through to the on-screen controls matters.
        boolean pressed = false;
        ExternalController controller = gyroTargetController;
        if (controller != null && (isGyroActivatorPressed(controller.state)
                || isGyroActivatorPressed(controller.remappedState))) {
            pressed = true;
        }
        if (!pressed && activity.getInputControlsView() != null) {
            ControlsProfile profile = activity.getInputControlsView().getProfile();
            pressed = profile != null && isGyroActivatorPressed(profile.getGamepadState());
        }

        if (pressed && !gyroActivatorWasPressed) {
            gyroToggleLatched = !gyroToggleLatched;
            if (!gyroToggleLatched) {
                // Latched off: drop the deflection and the filter/carry state, then push once through
                // the ungated path so the stick actually recenters instead of waiting for whatever
                // controller event happens to come next.
                currentGyroStickX = 0.0f;
                currentGyroStickY = 0.0f;
                smoothedGyroX = 0.0f;
                smoothedGyroY = 0.0f;
                accumulatedGyroMouseX = 0.0f;
                accumulatedGyroMouseY = 0.0f;
                // Orientation mode: drop the zero reference too, so the next tap-on recaptures centre
                // from wherever the device is being held rather than resuming an hours-old pose.
                gyroOrientationCalibrated = false;
                pushGyroClearToLastTarget();
            }
        }
        gyroActivatorWasPressed = pressed;
    }

    /**
     * Rate-mode mouse path: the tilt drives the POINTER instead of a stick, which is the only gyro
     * target that does anything on a Wine desktop or in a mouse-look game. Deltas stay proportional
     * to the tilt rate (no fixed-speed drift) and the sub-pixel remainder is carried between samples
     * so a slow tilt still moves. Allocation-free — see gyroMouseAction.
     */
    private void updateGyroMouse(float rawGyroX, float rawGyroY) {
        // Mouse mode never touches the sticks; drop any overlay a previous stick session left behind.
        clearGyroStickOverlay();

        if (!isGyroActivatorHeld()) {
            smoothedGyroX = 0.0f;
            smoothedGyroY = 0.0f;
            accumulatedGyroMouseX = 0.0f;
            accumulatedGyroMouseY = 0.0f;
            return;
        }

        final float smoothing = gyroSmoothing;
        smoothedGyroX = (smoothedGyroX * smoothing) + (rawGyroX * (1.0f - smoothing));
        smoothedGyroY = (smoothedGyroY * smoothing) + (rawGyroY * (1.0f - smoothing));

        accumulatedGyroMouseX += smoothedGyroX * gyroSensitivity * GYRO_MOUSE_SCALE;
        accumulatedGyroMouseY += smoothedGyroY * gyroSensitivity * GYRO_MOUSE_SCALE;

        int dx = (int) accumulatedGyroMouseX;
        int dy = (int) accumulatedGyroMouseY;
        if (dx == 0 && dy == 0)
            return;
        accumulatedGyroMouseX -= dx;
        accumulatedGyroMouseY -= dy;

        // Same seam the on-screen-controls mouse timer uses: relative mode goes to winhandler.exe as
        // real relative motion (mouse-look games), otherwise we move the X pointer (desktop/windowed).
        XServer xServer = activity != null ? activity.getXServer() : null;
        if (xServer != null && !xServer.isRelativeMouseMovement()) {
            xServer.injectPointerMoveDelta(dx, dy);
            return;
        }
        queueGyroMouseEvent(dx, dy);
    }

    /** Coalescing, allocation-free enqueue of a relative mouse move onto the existing send thread. */
    private void queueGyroMouseEvent(int dx, int dy) {
        if (!initReceived)
            return;
        synchronized (gyroMouseLock) {
            pendingGyroMouseDx += dx;
            pendingGyroMouseDy += dy;
            if (gyroMouseActionQueued)
                return;
            gyroMouseActionQueued = true;
        }
        addAction(gyroMouseAction);
    }

    /** Zeroes the stick overlay and re-pushes once, so the stick can't stay stuck deflected. */
    private void clearGyroStickOverlay() {
        if (currentGyroStickX == 0.0f && currentGyroStickY == 0.0f)
            return;
        currentGyroStickX = 0.0f;
        currentGyroStickY = 0.0f;
        pushGyroToActiveTarget();
    }

    /**
     * Full runtime reset — used when the gyro is switched off, re-pointed at another target or
     * re-configured, when the target controller disappears, and when the activity drops the sensor
     * listener. Public because XServerDisplayActivity has to be able to clear a toggle latch on
     * pause: no samples arrive in the background, so a latched-on overlay would otherwise stay frozen
     * into the last written gamepad state.
     */
    public void resetGyroRuntimeState() {
        smoothedGyroX = 0.0f;
        smoothedGyroY = 0.0f;
        accumulatedGyroMouseX = 0.0f;
        accumulatedGyroMouseY = 0.0f;
        // Overlay first, THEN the latch: clearGyroStickOverlay re-pushes through the activation gate,
        // and dropping the latch ahead of it would gate that clean push away — freezing the last
        // deflected state into the game, which is the exact bug this method exists to prevent.
        clearGyroStickOverlay();
        gyroToggleLatched = false;
        gyroActivatorWasPressed = false;
        gyroOrientationCalibrated = false;
    }

    /**
     * The one activation gate: in toggle mode the latch IS the state, in hold mode it's the live
     * button. "Always" keeps ignoring the mode — there's no button to tap, so the gate always passes.
     */
    private boolean isGyroTargetActive(GamepadState state) {
        if (gyroActivationMode == GYRO_ACTIVATION_TOGGLE && gyroActivator != GYRO_ACTIVATOR_ALWAYS)
            return gyroToggleLatched;
        return isGyroActivatorPressed(state);
    }

    /** True when the activator is held on any live target (or the user chose "Always on"). */
    private boolean isGyroActivatorHeld() {
        if (gyroActivator == GYRO_ACTIVATOR_ALWAYS)
            return true;
        // Toggle mode: the latch is the whole answer, so short-circuit before walking the targets.
        // That's also how the mouse path inherits the latch without any plumbing of its own.
        if (gyroActivationMode == GYRO_ACTIVATION_TOGGLE)
            return gyroToggleLatched;
        ExternalController controller = gyroTargetController;
        if (controller != null && (isGyroActivatorPressed(controller.state)
                || isGyroActivatorPressed(controller.remappedState)))
            return true;
        if (activity.getInputControlsView() == null)
            return false;
        ControlsProfile profile = activity.getInputControlsView().getProfile();
        return profile != null && isGyroActivatorPressed(profile.getGamepadState());
    }

    // Activation gate: gyro only contributes while the chosen button is held on the state we're about
    // to inject. "Always on" needs no button at all, so it short-circuits before the state lookup.
    private boolean isGyroActivatorPressed(GamepadState state) {
        if (gyroActivator == GYRO_ACTIVATOR_ALWAYS)
            return true;
        if (state == null)
            return false;
        return state.isPressed(gyroActivatorButtonIndex());
    }

    private byte gyroActivatorButtonIndex() {
        switch (gyroActivator) {
            case GYRO_ACTIVATOR_L2: return ExternalController.IDX_BUTTON_L2;
            case GYRO_ACTIVATOR_R1: return ExternalController.IDX_BUTTON_R1;
            case GYRO_ACTIVATOR_R3: return ExternalController.IDX_BUTTON_R3;
            default:                return ExternalController.IDX_BUTTON_L1;
        }
    }

    /**
     * Overlays the gyro deflection on the configured stick. Returns baseState untouched whenever the
     * gyro isn't contributing, so the normal controller path stays byte-identical; when it is, the
     * deltas are added to (never replace) the physical stick and clamped back into range.
     */
    private GamepadState getOutputGamepadState(GamepadState baseState) {
        if (baseState == null)
            return baseState;
        if (currentGyroStickX == 0.0f && currentGyroStickY == 0.0f)
            return baseState;
        if (gyroTarget == GYRO_TARGET_MOUSE)
            return baseState;
        if (!isGyroTargetActive(baseState))
            return baseState;

        outputGamepadState.copy(baseState);
        if (gyroTarget == GYRO_TARGET_LEFT_STICK) {
            outputGamepadState.thumbLX = clamp(baseState.thumbLX + currentGyroStickX, -1.0f, 1.0f);
            outputGamepadState.thumbLY = clamp(baseState.thumbLY + currentGyroStickY, -1.0f, 1.0f);
        } else {
            outputGamepadState.thumbRX = clamp(baseState.thumbRX + currentGyroStickX, -1.0f, 1.0f);
            outputGamepadState.thumbRY = clamp(baseState.thumbRY + currentGyroStickY, -1.0f, 1.0f);
        }

        if (!gyroApplyLogged) {
            gyroApplyLogged = true;
            Log.i("WinHandlerGyro", "Gyro applied to stick (first sample): target=" + gyroTarget
                    + " x=" + currentGyroStickX + " y=" + currentGyroStickY);
        }
        return outputGamepadState;
    }

    // ---- Gyro settings (runtime mirror only — the container/shortcut owns persistence) ----
    //
    // These fields are written exactly twice per session: once by applyGyroTuning at launch, then by
    // the in-game drawer callbacks (which persist to the shortcut/container themselves). Nothing on
    // the sample path ever reads the container, so updateGyroData stays allocation-free.

    public boolean isGyroEnabled()      { return gyroEnabled; }
    public int getGyroTarget()          { return gyroTarget; }
    public float getGyroSensitivity()   { return gyroSensitivity; }
    public float getGyroDeadzone()      { return gyroDeadzone; }
    public float getGyroSmoothing()     { return gyroSmoothing; }
    public boolean isGyroInvertX()      { return gyroInvertX; }
    public boolean isGyroInvertY()      { return gyroInvertY; }
    public int getGyroActivator()       { return gyroActivator; }
    public int getGyroActivationMode()  { return gyroActivationMode; }
    public int getGyroMode()            { return gyroMode; }

    /**
     * Applies the whole resolved gyro configuration in one shot (launch time, from
     * XServerDisplayActivity once the container/shortcut chain is known) and arms the sample path.
     * Same clamps as the individual setters below, so a hand-edited container JSON can't get through.
     */
    public void applyGyroTuning(boolean enabled, int target, int activator, int activationMode,
                                int mode, float sensitivity, float deadzone, float smoothing,
                                boolean invertX, boolean invertY) {
        gyroEnabled = enabled;
        gyroTarget = (target < GYRO_TARGET_RIGHT_STICK || target > GYRO_TARGET_MOUSE)
                ? GYRO_TARGET_DEFAULT : target;
        gyroMode = sanitizeGyroMode(mode);
        gyroActivator = (activator < GYRO_ACTIVATOR_L1 || activator > GYRO_ACTIVATOR_ALWAYS)
                ? GYRO_ACTIVATOR_DEFAULT : activator;
        gyroActivationMode = (activationMode < GYRO_ACTIVATION_HOLD || activationMode > GYRO_ACTIVATION_TOGGLE)
                ? GYRO_ACTIVATION_MODE_DEFAULT : activationMode;
        gyroSensitivity = clamp(sensitivity, 0.1f, 10.0f);
        gyroDeadzone = clamp(deadzone, 0.0f, 0.5f);
        gyroSmoothing = clamp(smoothing, 0.0f, 0.95f);
        gyroInvertX = invertX;
        gyroInvertY = invertY;
        // Target may have changed under us — drop any stale deflection before arming.
        resetGyroRuntimeState();
        gyroConfigApplied = true;
    }

    public void setGyroEnabled(boolean enabled) {
        gyroEnabled = enabled;
        if (!enabled)
            resetGyroRuntimeState();
    }

    /** Switching target must drop the old overlay, or the previous stick stays deflected forever. */
    public void setGyroTarget(int target) {
        gyroTarget = (target < GYRO_TARGET_RIGHT_STICK || target > GYRO_TARGET_MOUSE)
                ? GYRO_TARGET_DEFAULT : target;
        // Re-run the mouse rule: picking the mouse target has to knock orientation mode back to rate.
        gyroMode = sanitizeGyroMode(gyroMode);
        resetGyroRuntimeState();
    }

    /** Switching read mode drops the zero reference and the overlay via the shared reset. */
    public void setGyroMode(int mode) {
        gyroMode = sanitizeGyroMode(mode);
        resetGyroRuntimeState();
    }

    /**
     * Range check plus the one rule the two settings share: orientation mode can NOT drive the mouse.
     * That path integrates into a sub-pixel accumulator and emits relative deltas, so a held tilt is a
     * constant delta and the pointer accelerates into a screen edge and pegs there. The UI disables
     * the combination, and this makes a hand-edited container JSON unable to reach it either.
     */
    private int sanitizeGyroMode(int mode) {
        if (mode < GYRO_MODE_RATE || mode > GYRO_MODE_ORIENTATION)
            return GYRO_MODE_DEFAULT;
        if (mode == GYRO_MODE_ORIENTATION && gyroTarget == GYRO_TARGET_MOUSE)
            return GYRO_MODE_RATE;
        return mode;
    }

    public void setGyroSensitivity(float sensitivity) {
        gyroSensitivity = clamp(sensitivity, 0.1f, 10.0f);
    }

    public void setGyroDeadzone(float deadzone) {
        gyroDeadzone = clamp(deadzone, 0.0f, 0.5f);
    }

    public void setGyroSmoothing(float smoothing) {
        gyroSmoothing = clamp(smoothing, 0.0f, 0.95f);
    }

    public void setGyroInvertX(boolean invert) {
        gyroInvertX = invert;
    }

    public void setGyroInvertY(boolean invert) {
        gyroInvertY = invert;
    }

    public void setGyroActivator(int activator) {
        gyroActivator = (activator < GYRO_ACTIVATOR_L1 || activator > GYRO_ACTIVATOR_ALWAYS)
                ? GYRO_ACTIVATOR_DEFAULT : activator;
        resetGyroRuntimeState();
    }

    /** Changing the mode has to drop the latch too, or a toggled-on gyro survives the switch to hold. */
    public void setGyroActivationMode(int activationMode) {
        gyroActivationMode = (activationMode < GYRO_ACTIVATION_HOLD || activationMode > GYRO_ACTIVATION_TOGGLE)
                ? GYRO_ACTIVATION_MODE_DEFAULT : activationMode;
        resetGyroRuntimeState();
    }

    /**
     * Pushes a freshly measured calibration bias in (Input Controls -> Gyroscope, then back to the
     * running session). Both fields are volatile and the sample path only reads them, so this stays a
     * plain assignment — the bias is never re-read from prefs on the sample path.
     */
    public void setGyroBias(float biasX, float biasY) {
        gyroBiasX = biasX;
        gyroBiasY = biasY;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Assigns a slot to a device. Slots are FCFS and sticky (a disconnect keeps the
     * reservation via descriptorToSlot until the debounce fires). Physical controllers
     * are keyed on their stable descriptor so a reconnect (new deviceId) and every
     * sub-device of one pad share a single slot; OSC keeps its simple deviceId(-1)→slot
     * path so the on-screen-controls transport is untouched.
     */
    private int assignSlot(int deviceId) {
        // Fast path: this exact deviceId already owns a slot.
        Integer existing = deviceToSlot.get(deviceId);
        if (existing != null)
            return existing;

        // OSC / virtual gamepad: no real descriptor, so it keys manual overrides on OSC_DESCRIPTOR.
        // Ignore = no slot; pin = claim the chosen slot (including slot 0 / Player 1) when free,
        // else fall back to FCFS so the on-screen pad still works. Default (no override) is the
        // historical simple FCFS behavior.
        if (deviceId == OSC_DEVICE_ID) {
            Integer oscOverride = manualSlotOverrides.get(OSC_DESCRIPTOR);
            if (oscOverride != null) {
                if (oscOverride == SLOT_IGNORE)
                    return -1;
                if (oscOverride >= 0 && oscOverride < MAX_CONTROLLERS) {
                    if (!usedSlots.contains(oscOverride))
                        return assignSpecificSlot(deviceId, null, oscOverride);
                    // Slot is taken but the user pinned MULTIPLE devices to it — co-occupy (shared merge).
                    if (isSlotManuallyShared(oscOverride))
                        return joinSharedSlot(deviceId, null, oscOverride);
                }
            }
            // Session-only YIELD preference: a pad promotion moved OSC up to this slot. Honor it before
            // FCFS so OSC doesn't fall straight back onto the slot 0 we just handed the physical pad.
            if (oscYieldSlot >= 0 && oscYieldSlot < MAX_CONTROLLERS && !usedSlots.contains(oscYieldSlot))
                return assignSpecificSlot(deviceId, null, oscYieldSlot);
            return assignFreeSlot(deviceId, null);
        }

        // Physical: resolve the descriptor so sibling sub-devices group under one slot.
        String descriptor = descriptorOf(deviceId);

        if (descriptor != null) {
            // Manual override wins over auto-assignment. Ignore = never take a slot (guards the
            // input-event path so an ignored pad can't re-grab a slot on its next event).
            Integer override = manualSlotOverrides.get(descriptor);
            if (override != null && override == SLOT_IGNORE)
                return -1;

            // Another deviceId from the same physical controller (a reconnect, or a sibling
            // sub-device) already holds a slot — reuse it instead of consuming a new one.
            Integer descriptorSlot = descriptorToSlot.get(descriptor);
            if (descriptorSlot != null) {
                deviceToSlot.put(deviceId, descriptorSlot);
                deviceToDescriptor.put(deviceId, descriptor);
                ensureWriterForSlot(descriptorSlot);
                Log.d("WinHandler", "Mapped device " + deviceId + " to existing slot "
                        + descriptorSlot + " (same physical controller: " + descriptor + ")");
                return descriptorSlot;
            }

            // Manual same-slot SHARING: the user pinned this descriptor to a slot that ANOTHER
            // descriptor (or OSC) is also pinned to. Co-occupy it instead of evicting/spilling — the
            // per-slot merge lets both devices drive the one XInput player. Manual-only: nothing
            // auto-assigns two descriptors to one slot, so this is reachable only via explicit pins.
            if (override != null && override >= 0 && override < MAX_CONTROLLERS
                    && usedSlots.contains(override) && isSlotManuallyShared(override)) {
                return joinSharedSlot(deviceId, descriptor, override);
            }

            // Pinned to a specific slot: claim it when free (else fall through to FCFS).
            if (override != null && override >= 0 && override < MAX_CONTROLLERS
                    && !usedSlots.contains(override)) {
                return assignSpecificSlot(deviceId, descriptor, override);
            }
        }

        return assignFreeSlot(deviceId, descriptor);
    }

    /** Claims a SPECIFIC slot for deviceId (manual pin path), recording the descriptor keys
     *  (physical only) and opening the slot's writer. Returns the slot, or -1 if it's out of range
     *  or already taken — callers fall back to assignFreeSlot in that case. */
    private int assignSpecificSlot(int deviceId, String descriptor, int slot) {
        if (slot < 0 || slot >= MAX_CONTROLLERS || usedSlots.contains(slot))
            return -1;
        usedSlots.add(slot);
        deviceToSlot.put(deviceId, slot);
        if (descriptor != null) {
            descriptorToSlot.put(descriptor, slot);
            deviceToDescriptor.put(deviceId, descriptor);
        }
        ensureWriterForSlot(slot);
        recomputeSharedSlots();
        Log.d("WinHandler", "Pinned device " + deviceId + " to slot " + slot
                + (descriptor != null ? " (descriptor: " + descriptor + ")" : " (OSC)"));
        return slot;
    }

    /** True when the user has manually pinned >=2 DISTINCT descriptors (OSC counted) to {@code slot}.
     *  This is the manual-only trigger for shared/combined control — auto-assignment never produces it. */
    private boolean isSlotManuallyShared(int slot) {
        int count = 0;
        for (Integer v : manualSlotOverrides.values())
            if (v != null && v == slot) count++;
        return count >= 2;
    }

    /** Co-occupies an already-used slot for {@code deviceId} (manual same-slot sharing, or On-screen
     *  SHARE mode) instead of evicting the current holder. Records the same descriptor keys as the
     *  other assign helpers, marks the slot shared, and leaves the existing writer in place (both
     *  contributors feed it through the per-slot merge). Returns the slot. */
    private int joinSharedSlot(int deviceId, String descriptor, int slot) {
        if (slot < 0 || slot >= MAX_CONTROLLERS)
            return -1;
        usedSlots.add(slot); // already present; idempotent
        deviceToSlot.put(deviceId, slot);
        if (descriptor != null) {
            descriptorToSlot.put(descriptor, slot);
            deviceToDescriptor.put(deviceId, descriptor);
        }
        ensureWriterForSlot(slot);
        recomputeSharedSlots();
        Log.d("WinHandler", "Device " + deviceId + " SHARING slot " + slot
                + (descriptor != null ? " (descriptor: " + descriptor + ")" : " (OSC)"));
        return slot;
    }

    /** Claims the lowest free slot for deviceId, recording the descriptor keys (physical
     *  only) and opening the slot's writer. Returns -1 when all slots are taken. */
    private int assignFreeSlot(int deviceId, String descriptor) {
        for (int slot = 0; slot < MAX_CONTROLLERS; slot++) {
            if (!usedSlots.contains(slot)) {
                usedSlots.add(slot);
                deviceToSlot.put(deviceId, slot);
                if (descriptor != null) {
                    descriptorToSlot.put(descriptor, slot);
                    deviceToDescriptor.put(deviceId, descriptor);
                }
                ensureWriterForSlot(slot);
                recomputeSharedSlots();
                Log.d("WinHandler", "Assigned device " + deviceId + " to slot " + slot
                        + (descriptor != null ? " (descriptor: " + descriptor + ")" : ""));
                return slot;
            }
        }
        Log.w("WinHandler", "No slots available for device " + deviceId);
        return -1;
    }

    /** Lazily creates and opens the fake-input writer backing a slot. Opening the writer
     *  activates the slot's mmap ring (P0), which is what the guest sees as a controller. */
    private void ensureWriterForSlot(int slot) {
        if (slot < 0 || slot >= MAX_CONTROLLERS || fakeInputBasePath == null)
            return;
        if (writers[slot] == null) {
            writers[slot] = new FakeInputWriter(fakeInputBasePath, slot);
            writers[slot].open();
        }
    }

    private void releaseSlot(int deviceId) {
        Integer slot = deviceToSlot.remove(deviceId);
        if (slot == null)
            return;

        // Sibling-sub-device reuse: one physical controller enumerates several deviceIds
        // (gamepad + touchpad + motion), all sharing one slot via its descriptor. Only tear
        // the slot down once the LAST of those sub-devices is gone, otherwise unplugging the
        // touchpad half would kill a still-connected pad.
        String descriptor = deviceToDescriptor.remove(deviceId);
        // Same-physical-controller sibling still on this slot? (gamepad + touchpad + motion each
        // enumerate a deviceId but share one descriptor / one slot.)
        boolean siblingHoldsSlot = false;
        if (descriptor != null) {
            for (Map.Entry<Integer, String> entry : deviceToDescriptor.entrySet()) {
                if (descriptor.equals(entry.getValue()) && deviceToSlot.containsKey(entry.getKey())) {
                    siblingHoldsSlot = true;
                    break;
                }
            }
            if (!siblingHoldsSlot)
                descriptorToSlot.remove(descriptor);
        }

        // Shared-slot co-owner (a DIFFERENT descriptor, or OSC) still on this slot? If so we must NOT
        // tear the ring down — only the LAST contributor to leave a shared slot destroys it (mirrors the
        // sibling-sub-device rule above, extended to distinct co-owners of a shared slot).
        boolean coOwnerHoldsSlot = false;
        for (Integer s : deviceToSlot.values()) {
            if (s != null && s.intValue() == slot) { coOwnerHoldsSlot = true; break; }
        }

        boolean slotStillInUse = siblingHoldsSlot || coOwnerHoldsSlot;

        // Drop the departing contributor's snapshot from the shared merge (unless a same-descriptor
        // sibling still feeds that same key) so its last-held inputs don't linger in the combined state.
        if (!siblingHoldsSlot) {
            String contribKey = (deviceId == OSC_DEVICE_ID) ? OSC_DESCRIPTOR : descriptor;
            if (contribKey != null && slotContributions[slot] != null)
                slotContributions[slot].remove(contribKey);
        }

        if (!slotStillInUse) {
            if (fallbackSlot == slot) fallbackSlot = -1;
            if (writers[slot] != null) {
                if (deviceId == OSC_DEVICE_ID) {
                    // OSC / virtual pad: keep the ring alive across on-screen-controls
                    // hide/show. softRelease resets the pad to neutral without bumping the
                    // ring generation, so toggling the overlay never flaps the guest's
                    // virtual controller — this is the P0 on-screen path, left intact.
                    writers[slot].softRelease();
                } else {
                    // Physical disconnect: destroy() so the ring generation bumps and the
                    // guest sees a REAL removal (the core hot-plug fix). A fresh writer is
                    // created on reconnect, which re-activates the ring at a new generation.
                    writers[slot].destroy();
                    writers[slot] = null;
                }
            }
            usedSlots.remove(slot);
            if (slotContributions[slot] != null) slotContributions[slot].clear();
            Log.d("WinHandler", "Device " + deviceId + " disconnected (or OSC disabled). Slot released: " + slot);
        } else {
            Log.d("WinHandler", "Device " + deviceId + " removed but slot " + slot
                    + " still used by a sibling sub-device or shared-slot co-owner.");
        }

        // Occupancy changed either way — refresh the shared cache. When a co-owner remains, immediately
        // re-write the merged state (now without the departed contributor) so its held inputs release
        // even before the remaining contributor's next frame.
        recomputeSharedSlots();
        if (slotStillInUse && slotShared[slot] && writers[slot] != null && slotContributions[slot] != null) {
            GamepadState merged = slotMerged[slot];
            if (merged == null) { merged = new GamepadState(); slotMerged[slot] = merged; }
            mergeContributions(slotContributions[slot], merged);
            writers[slot].writeGamepadState(merged);
        }

        if (gyroTargetController != null && gyroTargetController.getDeviceId() == deviceId) {
            gyroTargetController = null;
            // Worst case for toggle mode: latched on and then the pad is unplugged, leaving no
            // button anywhere to tap it back off. Drop the latch along with the device.
            resetGyroRuntimeState();
        }
        controllers.remove(deviceId);
    }

    // ---- Physical controller pre-assignment + hot-plug (ported from WinNative) ----

    /**
     * Assigns every already-connected gamepad to a slot BEFORE Wine boots, so controllers
     * present at launch are live from the guest's very first device enumeration instead of
     * only appearing after the first input event. Called from XServerDisplayActivity's
     * setupXEnvironment, after the fake-input path is set and before the environment
     * components (Wine) start. No-op until setFakeInputPath has run.
     */
    public int preAssignConnectedControllers() {
        if (fakeInputBasePath == null || fakeInputBasePath.isEmpty()) {
            Log.w("WinHandler", "Skipping pre-assignment: fake input path is not set yet.");
            return 0;
        }
        int assignedCount = 0;
        // Steam Controllers read through SDL have no InputDevice for the scan to find; append them so
        // their pins/ignores go through the same two passes (only non-empty mid-session, e.g. Reset).
        int[] ids = getConnectedGamepadDeviceIds();
        if (!sdlPads.isEmpty()) {
            java.util.List<Integer> sdlIds = new java.util.ArrayList<>(sdlPads.keySet());
            int base = ids.length;
            ids = Arrays.copyOf(ids, base + sdlIds.size());
            for (int i = 0; i < sdlIds.size(); i++)
                ids[base + i] = sdlIds.get(i);
        }
        // Pass 1 — honor manual pins FIRST, so a user-pinned controller claims its exact player slot
        // before FCFS can hand that slot to anyone else. Without the two-pass split, a non-pinned pad
        // that sorts earlier could take a slot another pad is pinned to (then the pin would spill to a
        // free slot instead of the one the user chose).
        for (int deviceId : ids) {
            if (usedSlots.size() >= MAX_CONTROLLERS)
                break;
            if (assignPinnedDeviceIfPossible(deviceId))
                assignedCount++;
        }
        // Pass 2 — auto-assign the remaining controllers into whatever slots are left (ignored
        // descriptors are skipped inside assignConnectedDeviceIfPossible / assignSlot).
        for (int deviceId : ids) {
            if (usedSlots.size() >= MAX_CONTROLLERS)
                break;
            if (assignConnectedDeviceIfPossible(deviceId, "startup-scan"))
                assignedCount++;
        }
        Log.d("WinHandler", "Pre-assigned " + assignedCount + " controller(s) before Wine startup.");
        return assignedCount;
    }

    /** Pass-1 helper for pre-assignment: assigns a device to its manually PINNED slot if it has one
     *  and isn't slotted yet. Returns true only when a new pin assignment happened. Ignored devices
     *  and un-pinned (auto) devices return false here — the latter are handled in pass 2. */
    private boolean assignPinnedDeviceIfPossible(int deviceId) {
        if (deviceToSlot.containsKey(deviceId))
            return false;
        if (!sdlPads.containsKey(deviceId)) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
            if (!ExternalController.isGameController(device) || isShadowedBySdl(device))
                return false;
        }
        String descriptor = descriptorOf(deviceId);
        if (descriptor == null)
            return false;
        Integer override = manualSlotOverrides.get(descriptor);
        if (override == null || override == SLOT_IGNORE || override < 0 || override >= MAX_CONTROLLERS)
            return false;
        // Make sure an ExternalController instance exists for this deviceId (input path relies on it).
        if (getController(deviceId) == null)
            return false;
        int slot = assignSlot(deviceId); // honors the pin (descriptor is in manualSlotOverrides)
        if (slot >= 0) {
            Log.d("WinHandler", "Pre-assigned PINNED device " + deviceId + " to slot " + slot + ".");
            return true;
        }
        return false;
    }

    // ---- Manual per-device slot overrides (in-game "Players" sub-tab) ----

    /** One row for the in-game Players sub-tab. Immutable snapshot — the UI re-reads via
     *  getPlayerSlotAssignments() rather than holding a live handle. */
    public static class PlayerSlotInfo {
        public final String displayName;
        public final String descriptor;       // OSC_DESCRIPTOR for the on-screen pad
        public final int currentSlot;         // 0..MAX_CONTROLLERS-1, or -1 if unassigned
        public final boolean isGameController; // false for OSC and for non-controllers still holding a slot
        public final boolean isOnScreen;       // true only for the OSC row
        public final int override;             // 0..MAX-1 pin, SLOT_IGNORE, or -1 (auto)

        public PlayerSlotInfo(String displayName, String descriptor, int currentSlot,
                              boolean isGameController, boolean isOnScreen, int override) {
            this.displayName = displayName;
            this.descriptor = descriptor;
            this.currentSlot = currentSlot;
            this.isGameController = isGameController;
            this.isOnScreen = isOnScreen;
            this.override = override;
        }
    }

    /** Replaces the manual override map wholesale. Called from XServerDisplayActivity.setupUI once the
     *  container is resolved and BEFORE preAssignConnectedControllers, so launch-time assignment honors
     *  the per-container pins/ignores. Values: 0..MAX-1 = pin, SLOT_IGNORE = ignore, absent = auto. */
    public void setManualSlotOverrides(Map<String, Integer> overrides) {
        manualSlotOverrides.clear();
        if (overrides != null)
            manualSlotOverrides.putAll(overrides);
        Log.d("WinHandler", "Manual slot overrides set: " + manualSlotOverrides);
    }

    /** Copy of the current override map, for persistence. Includes overrides for devices that are
     *  currently DISCONNECTED (loaded at launch, mutated per-key by setDeviceSlotAssignment) so
     *  serializing this back to the container never drops a configured-but-unplugged device. */
    public Map<String, Integer> getManualSlotOverrides() {
        return new HashMap<>(manualSlotOverrides);
    }

    // ---- Shared controller-slot-overrides JSON schema (SINGLE source of truth) ----
    // The persisted format is a flat JSON object: descriptor -> desired slot int, where the key is
    // device.getDescriptor() for a physical pad (== ExternalController.getId(), the SAME key the
    // launch pre-assign and the in-game Players tab look up) or OSC_DESCRIPTOR for the on-screen pad,
    // and the value is 0..MAX_CONTROLLERS-1 (a player-slot pin), SLOT_IGNORE (never take a slot), or
    // absent (auto/FCFS). Every read/write site MUST go through these two helpers so the in-game tab,
    // launch pre-assign, and the out-of-game container/shortcut editors all agree on one format.

    /** Parse a slot-overrides JSON object string into a descriptor->slot map. Empty/null/bad JSON
     *  yields an empty map (all-auto). */
    public static Map<String, Integer> parseSlotOverridesJson(String json) {
        Map<String, Integer> map = new HashMap<>();
        if (json == null || json.isEmpty()) return map;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                map.put(key, obj.getInt(key));
            }
        } catch (org.json.JSONException e) {
            Log.w("WinHandler", "Bad controllerSlotOverrides JSON: " + json, e);
        }
        return map;
    }

    /** Serialize a descriptor->slot map back to the canonical JSON object string. */
    public static String buildSlotOverridesJson(Map<String, Integer> overrides) {
        org.json.JSONObject obj = new org.json.JSONObject();
        if (overrides != null) {
            try {
                for (Map.Entry<String, Integer> e : overrides.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null)
                        obj.put(e.getKey(), (int) e.getValue());
                }
            } catch (org.json.JSONException e) {
                Log.w("WinHandler", "Failed to serialize controllerSlotOverrides", e);
            }
        }
        return obj.toString();
    }

    /** Snapshot of every device the Players sub-tab should list: the OSC virtual pad (always), every
     *  connected game controller, and any device currently holding a slot (so a mis-slotted device can
     *  be freed). Sibling sub-devices are de-duplicated by descriptor. Main-thread only. */
    public java.util.List<PlayerSlotInfo> getPlayerSlotAssignments() {
        java.util.List<PlayerSlotInfo> out = new java.util.ArrayList<>();

        Integer oscSlot = deviceToSlot.get(OSC_DEVICE_ID);
        Integer oscOverride = manualSlotOverrides.get(OSC_DESCRIPTOR);
        out.add(new PlayerSlotInfo("On-screen controls", OSC_DESCRIPTOR,
                oscSlot != null ? oscSlot : -1, false, true,
                oscOverride != null ? oscOverride : -1));

        java.util.LinkedHashMap<String, PlayerSlotInfo> byDescriptor = new java.util.LinkedHashMap<>();
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(id);
            if (device == null)
                continue;
            String descriptor = device.getDescriptor();
            if (descriptor == null || byDescriptor.containsKey(descriptor))
                continue;
            boolean isController = ExternalController.isGameController(device);
            boolean holdsSlot = descriptorToSlot.containsKey(descriptor);
            // Only list real controllers, plus anything currently occupying a slot (edge case: a
            // device that grabbed a slot before an override/filter change and should be freeable).
            if (!isController && !holdsSlot)
                continue;
            // A Valve device Android exposes while SDL owns the Steam Controller is the same pad —
            // it's listed once, as the SDL row below.
            if (isShadowedBySdl(device) && !holdsSlot)
                continue;
            Integer slot = descriptorToSlot.get(descriptor);
            Integer override = manualSlotOverrides.get(descriptor);
            byDescriptor.put(descriptor, new PlayerSlotInfo(device.getName(), descriptor,
                    slot != null ? slot : -1, isController, false,
                    override != null ? override : -1));
        }
        for (ExternalController pad : sdlPads.values()) {
            String descriptor = pad.getId();
            if (descriptor == null || byDescriptor.containsKey(descriptor))
                continue;
            Integer slot = descriptorToSlot.get(descriptor);
            Integer override = manualSlotOverrides.get(descriptor);
            byDescriptor.put(descriptor, new PlayerSlotInfo(pad.getName(), descriptor,
                    slot != null ? slot : -1, true, false,
                    override != null ? override : -1));
        }
        out.addAll(byDescriptor.values());
        return out;
    }

    /**
     * Live per-device slot reassignment from the in-game Players sub-tab. desiredSlot is 0..MAX-1 to
     * pin the device to that XInput player slot (Player 1 = slot 0), SLOT_IGNORE to drop it (free its
     * slot, never re-grab), or -1 to clear the override back to auto (FCFS). The override is recorded
     * first so every later assign path obeys it, then the change is applied immediately:
     *   • Physical devices are torn down (releaseSlot → destroy() → ring generation bump → the guest
     *     sees a real removal) and re-seated on the new slot — the P1 physical-reassignment semantic.
     *   • OSC keeps softRelease semantics: only its slot INDEX changes; its ring stays alive across
     *     the move so the on-screen overlay never flaps.
     * Pinning onto a slot another device holds evicts that other device (it re-auto-assigns to the
     * next free slot). Returns the resulting slot, or -1 for ignore / unassigned. Main-thread only.
     */
    public int setDeviceSlotAssignment(String descriptor, int desiredSlot) {
        if (descriptor == null)
            return -1;

        // Normalize + record the override up front (guards the input-event assign path too).
        int normalized;
        if (desiredSlot == SLOT_IGNORE) {
            manualSlotOverrides.put(descriptor, SLOT_IGNORE);
            normalized = SLOT_IGNORE;
        } else if (desiredSlot >= 0 && desiredSlot < MAX_CONTROLLERS) {
            manualSlotOverrides.put(descriptor, desiredSlot);
            normalized = desiredSlot;
        } else {
            manualSlotOverrides.remove(descriptor);
            normalized = -1; // auto
        }

        // ---- OSC virtual pad ----
        if (OSC_DESCRIPTOR.equals(descriptor)) {
            // An explicit OSC assignment overrides any session-only YIELD promotion preference.
            oscYieldSlot = -1;
            Integer cur = deviceToSlot.get(OSC_DEVICE_ID);
            if (normalized >= 0 && cur != null && cur == normalized)
                return normalized; // already there — no flap
            // Pinning OSC onto a slot a physical pad holds evicts that pad (it re-auto-assigns to the
            // next free slot), so OSC can take ANY slot including Player 1 (slot 0).
            if (normalized >= 0)
                evictSlotOccupants(normalized, OSC_DESCRIPTOR);
            // softRelease (inside releaseSlot for OSC) keeps the ring alive across the move; only the
            // slot INDEX changes. The re-seat below (or the next sendGamepadState frame) re-opens it.
            releaseSlot(OSC_DEVICE_ID);
            if (normalized >= 0)
                return assignSlot(OSC_DEVICE_ID);
            return -1;
        }

        // ---- Physical controller ----
        Integer curSlot = descriptorToSlot.get(descriptor);
        if (normalized >= 0 && curSlot != null && curSlot == normalized)
            return normalized; // already pinned there — avoid a needless destroy/recreate flap

        // Evict whoever currently holds the target slot (a different device), so the pin wins.
        if (normalized >= 0)
            evictSlotOccupants(normalized, descriptor);

        // Release this descriptor's current slot across all of its live/stale deviceIds so the last
        // release destroys the writer (guest sees the removal) before we re-seat it.
        java.util.List<Integer> liveIds = new java.util.ArrayList<>();
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice d = android.view.InputDevice.getDevice(id);
            if (d != null && descriptor.equals(d.getDescriptor()))
                liveIds.add(id);
        }
        for (Map.Entry<Integer, ExternalController> e : sdlPads.entrySet()) {
            if (descriptor.equals(e.getValue().getId()))
                liveIds.add(e.getKey());
        }
        java.util.List<Integer> toRelease = new java.util.ArrayList<>(liveIds);
        for (Map.Entry<Integer, String> e : deviceToDescriptor.entrySet()) {
            if (descriptor.equals(e.getValue()) && !toRelease.contains(e.getKey()))
                toRelease.add(e.getKey());
        }
        for (int id : toRelease)
            releaseSlot(id);

        if (normalized == SLOT_IGNORE)
            return -1;

        // Re-seat: the first live sub-device claims the slot (honoring the pin, or FCFS when auto);
        // siblings reuse it via the descriptor. If the device is currently disconnected the override
        // still persists and is applied on its next reconnect / next launch.
        for (int id : liveIds) {
            if (getController(id) == null)
                continue;
            int slot = assignSlot(id);
            if (slot >= 0)
                return slot;
        }
        return -1;
    }

    /** Frees targetSlot by releasing whatever device currently holds it, UNLESS that device is the one
     *  identified by exceptDescriptor (a sibling of the device we're about to pin there). A physical
     *  occupant is destroyed; the OSC occupant softReleases. The evicted device re-auto-assigns to the
     *  next free slot on its next input frame (or stays unassigned if none free). */
    private void evictSlotOccupants(int targetSlot, String exceptDescriptor) {
        java.util.List<Integer> occupants = new java.util.ArrayList<>();
        for (Map.Entry<Integer, Integer> e : deviceToSlot.entrySet()) {
            if (e.getValue() != null && e.getValue() == targetSlot)
                occupants.add(e.getKey());
        }
        for (int occ : occupants) {
            if (occ == OSC_DEVICE_ID) {
                // Only evict OSC when the incoming device isn't OSC itself.
                if (OSC_DESCRIPTOR.equals(exceptDescriptor)) continue;
                // Shared: OSC is ALSO manually pinned to this slot — it's an intended co-owner, keep it.
                Integer oscOv = manualSlotOverrides.get(OSC_DESCRIPTOR);
                if (oscOv != null && oscOv == targetSlot) continue;
                releaseSlot(OSC_DEVICE_ID);
                continue;
            }
            String occDescriptor = deviceToDescriptor.get(occ);
            if (occDescriptor != null && occDescriptor.equals(exceptDescriptor)) continue;
            // Shared: this occupant is ALSO manually pinned to the same slot — intended co-owner, keep it.
            if (occDescriptor != null) {
                Integer occOv = manualSlotOverrides.get(occDescriptor);
                if (occOv != null && occOv == targetSlot) continue;
            }
            releaseSlot(occ);
        }
    }

    /**
     * Manual recovery: rebuilds the fake-input transport IN PLACE without relaunching the game or
     * restarting the container. Belt-and-suspenders for any input-loss (e.g. on-screen input death);
     * the P0 ring should prevent it, but this hands the user an explicit "re-handshake" button.
     * Idempotent — safe to call at any time. Steps:
     *   1. Destroy every slot writer so each ring's generation bumps → the native reader re-reads a
     *      fresh keyframe (cancel pending debounced releases first so nothing fires mid-rebuild).
     *   2. Re-register the hot-plug listener (in case it was torn down).
     *   3. Re-run slot assignment via the SAME override-honoring two-pass path pre-assignment uses,
     *      so physical pads re-seat on their pinned/auto slots.
     *   4. Re-open the OSC ring so on-screen touch resumes writing (no-op if OSC is set to Ignore).
     * The manual-override map is deliberately preserved across the reset.
     */
    public void resetInputPipeline() {
        if (fakeInputBasePath == null || fakeInputBasePath.isEmpty()) {
            Log.w("WinHandler", "resetInputPipeline: fake input path not set; nothing to rebuild.");
            return;
        }
        Log.d("WinHandler", "resetInputPipeline: rebuilding input transport in place.");

        // 1) Tear down all rings + slot bookkeeping (overrides preserved so we re-seat the same pins).
        cancelAllPendingDeviceReleases();
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            if (writers[i] != null) {
                writers[i].destroy();
                writers[i] = null;
            }
        }
        deviceToSlot.clear();
        descriptorToSlot.clear();
        deviceToDescriptor.clear();
        usedSlots.clear();
        controllers.clear();
        fallbackSlot = -1;
        oscYieldSlot = -1;
        clearSharedSlotState();

        // 2) Make sure the hot-plug listener is still live.
        if (inputManager != null && inputDeviceListener != null) {
            try {
                inputManager.unregisterInputDeviceListener(inputDeviceListener);
            } catch (Exception ignored) {
            }
            inputManager.registerInputDeviceListener(inputDeviceListener, null);
        }

        // 3) Re-assign physical controllers (honors pins/ignores, same path as launch).
        preAssignConnectedControllers();

        // 4) Re-open the OSC ring so on-screen touch resumes. assignSlot(OSC) honors an OSC pin/ignore;
        //    when on-screen controls aren't active the next sendGamepadState frame releases it again
        //    (softRelease), so this is safe either way.
        int oscSlot = assignSlot(OSC_DEVICE_ID);
        Log.d("WinHandler", "resetInputPipeline: complete. OSC slot=" + oscSlot
                + ", used slots=" + usedSlots);
    }

    /** Connected deviceIds sorted by a stable physical identity, so slot order is
     *  deterministic across launches (player 1 stays player 1) rather than following
     *  Android's arbitrary deviceId ordering. */
    private int[] getConnectedGamepadDeviceIds() {
        int[] deviceIds = android.view.InputDevice.getDeviceIds();
        Integer[] sortedIds = new Integer[deviceIds.length];
        for (int i = 0; i < deviceIds.length; i++)
            sortedIds[i] = deviceIds[i];

        Arrays.sort(sortedIds, Comparator.comparing((Integer id) -> {
            android.view.InputDevice device = android.view.InputDevice.getDevice(id);
            if (device == null) return "";
            String physicalId = ExternalController.getPhysicalDeviceIdentifier(device);
            if (physicalId != null && !physicalId.isEmpty()) return physicalId;
            String descriptor = device.getDescriptor();
            return descriptor != null ? descriptor : "";
        }).thenComparingInt(Integer::intValue));

        int[] result = new int[sortedIds.length];
        for (int i = 0; i < sortedIds.length; i++)
            result[i] = sortedIds[i];
        return result;
    }

    /** Assigns one deviceId to a slot if it's a game controller that isn't already slotted.
     *  Shared by the startup scan and the add/change hot-plug hooks. Returns true only when
     *  a NEW physical assignment happened. */
    private boolean assignConnectedDeviceIfPossible(int deviceId, String source) {
        if (deviceToSlot.containsKey(deviceId))
            return false;

        if (usedSlots.size() >= MAX_CONTROLLERS) {
            // Still allow a reconnecting / sibling sub-device that will REUSE an existing
            // physical controller's slot rather than consume a new one; without this a
            // transient remove/add while all four slots are full would be dropped.
            String descriptor = descriptorOf(deviceId);
            if (descriptor == null || !descriptorToSlot.containsKey(descriptor)) {
                Log.d("WinHandler", "Ignoring device " + deviceId + " from " + source + ": slot limit reached.");
                return false;
            }
        }

        if (!sdlPads.containsKey(deviceId)) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
            if (!ExternalController.isGameController(device))
                return false;
            if (isShadowedBySdl(device)) {
                Log.d("WinHandler", "Skipping device " + deviceId + " from " + source
                        + ": Valve pad already read through SDL.");
                return false;
            }
        }

        // Manual "Ignore" override: user marked this physical controller as not-a-player in the
        // in-game Players sub-tab — never auto-assign it a slot.
        String descriptor = descriptorOf(deviceId);
        if (descriptor != null) {
            Integer override = manualSlotOverrides.get(descriptor);
            if (override != null && override == SLOT_IGNORE) {
                Log.d("WinHandler", "Skipping device " + deviceId + " from " + source
                        + ": ignored by manual slot override.");
                return false;
            }
        }

        ExternalController controller = getController(deviceId);
        if (controller == null)
            return false;

        int slot = assignSlot(deviceId);
        if (slot >= 0) {
            Log.d("WinHandler", "Auto-assigned device " + deviceId + " to slot " + slot + " via " + source + ".");
            return true;
        }
        return false;
    }

    /** Debounces a physical removal (see PHYSICAL_DISCONNECT_DEBOUNCE_MS). OSC is released
     *  immediately (no flap to guard against). The release runnable is also where the
     *  InputControlsView notification fires, so a flap that re-adds within the window leaves
     *  the on-screen controls state untouched too. */
    private void scheduleDeviceRelease(final int deviceId) {
        if (deviceId == OSC_DEVICE_ID) {
            releaseSlot(deviceId);
            return;
        }
        cancelPendingDeviceRelease(deviceId);
        Runnable release = () -> {
            pendingDeviceReleases.remove(deviceId);
            // Capture the descriptor BEFORE releaseSlot clears the mapping (the device itself is already
            // gone from the system, so InputDevice.getDevice() would return null here).
            String releasedDescriptor = deviceToDescriptor.get(deviceId);
            // Retained from our tree: tell the on-screen controls a physical pad left so it
            // resets that controller's mapping/overlay state (WinNative has no equivalent).
            InputControlsView inputControlsView = activity.getInputControlsView();
            if (inputControlsView != null) inputControlsView.onControllerDisconnected(deviceId);
            releaseSlot(deviceId);
            // Only toast a real tracked controller's departure (a device we'd actually slotted).
            if (releasedDescriptor != null)
                notifyAssignmentsChanged("disconnected", releasedDescriptor);
        };
        pendingDeviceReleases.put(deviceId, release);
        inputHandler.postDelayed(release, PHYSICAL_DISCONNECT_DEBOUNCE_MS);
        Log.d("WinHandler", "Device " + deviceId + " removed; scheduling slot release in "
                + PHYSICAL_DISCONNECT_DEBOUNCE_MS + "ms (debounce). Reconnect within the window keeps the slot.");
    }

    private void cancelPendingDeviceRelease(int deviceId) {
        Runnable pending = pendingDeviceReleases.remove(deviceId);
        if (pending != null)
            inputHandler.removeCallbacks(pending);
    }

    private void cancelAllPendingDeviceReleases() {
        for (Runnable pending : pendingDeviceReleases.values())
            inputHandler.removeCallbacks(pending);
        pendingDeviceReleases.clear();
    }

    public void setXInputDisabled(boolean disabled) {
        this.xinputDisabled = disabled;
        this.xinputDisabledInitialized = true;
        Log.d("WinHandler", "XInput Disabled set to: " + xinputDisabled);
    }

    /**
     * @param fakeInputPath Path to the fake-input directory (e.g.,
     *                      /home/xuser/fake-input)
     */
    public void setFakeInputPath(String fakeInputPath) {
        if (fakeInputPath != null && !fakeInputPath.isEmpty()) {
            this.fakeInputBasePath = fakeInputPath;
            Log.d("WinHandler", "FakeInputWriter base path set: " + fakeInputPath);
            startVibrationListener();
        }
    }

    public void closeFakeInputWriter() {
        cancelAllPendingDeviceReleases();
        if (inputManager != null && inputDeviceListener != null) {
            inputManager.unregisterInputDeviceListener(inputDeviceListener);
        }
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            if (writers[i] != null) {
                writers[i].destroy();
                writers[i] = null;
            }
        }
        deviceToSlot.clear();
        descriptorToSlot.clear();
        deviceToDescriptor.clear();
        usedSlots.clear();
        controllers.clear();
        sdlPads.clear();
        sdlBackend = null;
        manualSlotOverrides.clear();
        fallbackSlot = -1;
        oscYieldSlot = -1;
        clearSharedSlotState();

        vibrationRunning = false;
        if (vibrationServer != null) {
            try {
                vibrationServer.close();
            } catch (IOException e) {
            }
            vibrationServer = null;
        }
    }

    // ---- Steam Controller (SDL) pads — see the sdlPads field ----

    public void setSteamControllerBackend(SteamControllerBackend backend) {
        sdlBackend = backend;
    }

    /** True while at least one Steam Controller is being read through SDL. */
    public boolean hasSdlPads() {
        return !sdlPads.isEmpty();
    }

    /** A Steam Controller came up through SDL: seat it like a hot-plugged pad (On-screen priority
     *  mode, pins/ignores, status toast). Main-thread only. */
    public void onSdlPadConnected(ExternalController pad) {
        int deviceId = pad.getDeviceId();
        cancelPendingDeviceRelease(deviceId);
        sdlPads.put(deviceId, pad);
        releaseShadowedValveSlots();
        if (!handleOnScreenModeForNewPad(deviceId))
            assignConnectedDeviceIfPossible(deviceId, "sdl");
        notifyAssignmentsChanged("connected", pad.getId());
    }

    /** The SDL pad went away: neutralize it now and free its slot after the normal disconnect
     *  debounce. A quick Bluetooth reconnect comes back with a new deviceId but the same descriptor,
     *  so it re-seats on the slot still held under that descriptor. Main-thread only. */
    public void onSdlPadDisconnected(ExternalController pad) {
        int deviceId = pad.getDeviceId();
        if (sdlPads.remove(deviceId) == null)
            return;
        pad.state.reset();
        pad.remappedState.reset();
        if (deviceToSlot.containsKey(deviceId))
            sendGamepadState(pad);
        scheduleDeviceRelease(deviceId);
    }

    /** Steam Controller right trackpad as a mouse. Same seam as the gyro mouse: relative-mouse games
     *  get real relative motion through winhandler.exe, otherwise the X pointer moves. Main thread. */
    public void steamPadMouseMove(int dx, int dy) {
        XServer xServer = activity != null ? activity.getXServer() : null;
        if (xServer != null && !xServer.isRelativeMouseMovement()) {
            xServer.injectPointerMoveDelta(dx, dy);
            return;
        }
        queueGyroMouseEvent(dx, dy);
    }

    /** A mouse trackpad's click: the left mouse button, or the right one for the secondary pad (the
     *  left pad when both pads move the mouse). */
    public void steamPadMouseButton(boolean secondary, boolean down) {
        XServer xServer = activity != null ? activity.getXServer() : null;
        if (xServer == null)
            return;
        Pointer.Button button = secondary ? Pointer.Button.BUTTON_RIGHT : Pointer.Button.BUTTON_LEFT;
        if (xServer.isRelativeMouseMovement())
            mouseEvent(MouseEventFlags.getFlagFor(button, down), 0, 0, 0);
        else if (down)
            xServer.injectPointerButtonPress(button);
        else
            xServer.injectPointerButtonRelease(button);
    }

    /** Descriptor for any deviceId: the SDL pad's "sdl:" key, else the Android device descriptor. */
    private String descriptorOf(int deviceId) {
        ExternalController sdlPad = sdlPads.get(deviceId);
        if (sdlPad != null)
            return sdlPad.getId();
        android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
        return device != null ? device.getDescriptor() : null;
    }

    /** While SDL owns a Steam Controller, any Valve device Android also exposes (its lizard-mode
     *  keyboard/mouse, or a HID gamepad collection) is the same physical pad and must not take a
     *  second player slot. */
    private boolean isShadowedBySdl(android.view.InputDevice device) {
        return device != null && !sdlPads.isEmpty()
                && device.getVendorId() == SteamControllerBackend.VALVE_VENDOR_ID;
    }

    /** Frees slots a Valve Android device took before SDL claimed the controller. */
    private void releaseShadowedValveSlots() {
        for (Integer id : new java.util.ArrayList<>(deviceToSlot.keySet())) {
            if (id == null || id < 0 || sdlPads.containsKey(id))
                continue;
            if (isShadowedBySdl(android.view.InputDevice.getDevice(id))) {
                Log.d("WinHandler", "Releasing Android Valve device " + id + ": SDL owns the Steam Controller.");
                releaseSlot(id);
            }
        }
    }

    private ExternalController getController(int deviceId) {
        ExternalController sdlPad = sdlPads.get(deviceId);
        if (sdlPad != null)
            return sdlPad;
        if (!sdlPads.isEmpty() && isShadowedBySdl(android.view.InputDevice.getDevice(deviceId)))
            return null;
        if (controllers.containsKey(deviceId)) {
            return controllers.get(deviceId);
        }
        // Sibling sub-device reuse: if another live deviceId of the same physical controller
        // (same descriptor) already has an ExternalController, reuse that instance so both
        // sub-devices feed one shared gamepad state instead of two competing halves.
        android.view.InputDevice device = android.view.InputDevice.getDevice(deviceId);
        if (device != null) {
            String descriptor = device.getDescriptor();
            if (descriptor != null) {
                for (Map.Entry<Integer, ExternalController> entry : controllers.entrySet()) {
                    android.view.InputDevice existing = android.view.InputDevice.getDevice(entry.getKey());
                    if (existing != null && descriptor.equals(existing.getDescriptor())) {
                        controllers.put(deviceId, entry.getValue());
                        return entry.getValue();
                    }
                }
            }
        }
        ExternalController controller = ExternalController.getController(deviceId);
        if (controller != null) {
            controllers.put(deviceId, controller);
        }
        return controller;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        boolean handled = false;
        ExternalController controller = getController(event.getDeviceId());

        if (controller != null) {
            handled = controller.updateStateFromMotionEvent(event);
            if (handled)
                sendGamepadState(controller);
        }
        return handled;
    }

    public boolean onKeyEvent(KeyEvent event) {
        boolean handled = false;
        ExternalController controller = getController(event.getDeviceId());

        if (controller != null && event.getRepeatCount() == 0) {
            int action = event.getAction();

            if (action == KeyEvent.ACTION_DOWN) {
                handled = controller.updateStateFromKeyEvent(event);
            } else if (action == KeyEvent.ACTION_UP) {
                handled = controller.updateStateFromKeyEvent(event);
            }

            if (handled)
                sendGamepadState(controller);
        }
        return handled;
    }

    public void releaseAllControllerInputs() {
        for (ExternalController controller : controllers.values()) {
            controller.state.reset();
            controller.remappedState.reset();
            sendGamepadState(controller);
        }
        for (ExternalController controller : sdlPads.values()) {
            controller.state.reset();
            controller.remappedState.reset();
            sendGamepadState(controller);
        }
        InputControlsView inputControlsView = activity.getInputControlsView();
        ControlsProfile profile = inputControlsView != null ? inputControlsView.getPhysicalProfile() : null;
        if (profile == null) return;
        for (ExternalController controller : profile.getControllers()) {
            controller.state.reset();
            controller.remappedState.reset();
            sendGamepadState(controller);
        }
    }

    public byte getInputType() {
        return inputType;
    }

    public void setInputType(byte inputType) {
        this.inputType = inputType;
    }

    public void execWithDelay(String command, int delaySeconds) {
        if (command == null || command.trim().isEmpty() || delaySeconds < 0)
            return;

        // Use a scheduled executor for delay
        Executors.newSingleThreadScheduledExecutor().schedule(() -> exec(command), delaySeconds, TimeUnit.SECONDS);
    }

}
