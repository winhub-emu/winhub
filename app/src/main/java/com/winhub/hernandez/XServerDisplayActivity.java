package com.winhub.hernandez;

import static com.winhub.hernandez.core.AppUtils.showToast;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import androidx.compose.ui.platform.ComposeView;
import com.winhub.hernandez.ui.XServerDrawerKt;
import com.winhub.hernandez.ui.XServerDrawerState;
import com.winhub.hernandez.ui.RuntimeBackend;
import com.winhub.hernandez.ui.FexMode;
import com.winhub.hernandez.ui.FexProbe;
import com.winhub.hernandez.ui.XServerDialogHostKt;
import com.winhub.hernandez.ui.XServerDialogState;
import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.ContainerManager;
import com.winhub.hernandez.container.ContainerOverlay;
import com.winhub.hernandez.container.ContainerOverlayMigrator;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.core.CustomSaveVault;
import com.winhub.hernandez.store.AchievementWatcher;
import com.winhub.hernandez.store.SteamLiteAchievementWatcher;
import com.winhub.hernandez.store.EpicOverlayManager;
import com.winhub.hernandez.store.GogCloudSaveManager;
import com.winhub.hernandez.store.GogCloudSavePaths;
import com.winhub.hernandez.store.GoldbergMode;
import com.winhub.hernandez.store.GoldbergPatcher;
import com.winhub.hernandez.store.SteamAchievementStore;
import com.winhub.hernandez.store.SteamPrefs;
import com.winhub.hernandez.store.SteamCloudSaveManager;
import com.winhub.hernandez.store.SteamDatabase;
import com.winhub.hernandez.store.SteamRepository;
import com.winhub.hernandez.store.RealSteamLauncher;
import com.winhub.hernandez.store.SteamLiteComponent;
import com.winhub.hernandez.contentdialog.ContentDialog;
import com.winhub.hernandez.contentdialog.DXVKConfigDialog;
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog;
import com.winhub.hernandez.contentdialog.WineD3DConfigDialog;
import com.winhub.hernandez.contents.ContentProfile;
import com.winhub.hernandez.contents.ContentsManager;
import com.winhub.hernandez.contents.AdrenotoolsManager;
import com.winhub.hernandez.contents.WrapperManager;
import com.winhub.hernandez.core.AppUtils;
import com.winhub.hernandez.core.DefaultVersion;
import com.winhub.hernandez.core.EnvVars;
import com.winhub.hernandez.core.FileUtils;
import com.winhub.hernandez.core.GPUInformation;
import com.winhub.hernandez.core.GyroCalibrator;
import com.winhub.hernandez.core.KeyValueSet;
import com.winhub.hernandez.core.OnExtractFileListener;
import com.winhub.hernandez.core.PreloaderDialog;
import com.winhub.hernandez.core.ProcessHelper;
import com.winhub.hernandez.core.StringUtils;
import com.winhub.hernandez.core.TarCompressorUtils;
import com.winhub.hernandez.core.DirectAudioSupport;
import com.winhub.hernandez.core.WineInfo;
import com.winhub.hernandez.core.WineRegistryEditor;
import com.winhub.hernandez.core.WineRequestHandler;
import com.winhub.hernandez.core.WineStartMenuCreator;
import com.winhub.hernandez.core.Callback;
import com.winhub.hernandez.core.WineThemeManager;
import com.winhub.hernandez.core.WineUtils;
import com.winhub.hernandez.inputcontrols.ControlsProfile;
import com.winhub.hernandez.inputcontrols.ExternalController;
import com.winhub.hernandez.inputcontrols.InputControlsManager;
import com.winhub.hernandez.inputcontrols.SteamControllerBackend;
import com.winhub.hernandez.inputcontrols.StickShaper;
import com.winhub.hernandez.inputcontrols.VisualStyle;
import com.winhub.hernandez.math.Mathf;
import com.winhub.hernandez.math.XForm;
import com.winhub.hernandez.midi.MidiHandler;
import com.winhub.hernandez.midi.MidiManager;
import com.winhub.hernandez.renderer.HostRenderer;
import com.winhub.hernandez.widget.EpicOverlayPill;
import com.winhub.hernandez.widget.FpsCounter;
import com.winhub.hernandez.widget.InputControlsView;
import com.winhub.hernandez.widget.LogView;
import com.winhub.hernandez.widget.TouchpadView;
import com.winhub.hernandez.widget.XServerView;
import com.winhub.hernandez.winhandler.MouseEventFlags;
import com.winhub.hernandez.winhandler.OnGetProcessInfoListener;
import com.winhub.hernandez.winhandler.ProcessInfo;
import com.winhub.hernandez.winhandler.WinHandler;
import com.winhub.hernandez.core.CPUStatus;
import com.winhub.hernandez.xserver.XLock;
import com.winhub.hernandez.xconnector.UnixSocketConfig;
import com.winhub.hernandez.xenvironment.ImageFs;
import com.winhub.hernandez.xenvironment.ImageFsInstaller;
import com.winhub.hernandez.xenvironment.XEnvironment;
import com.winhub.hernandez.xenvironment.components.ALSAServerComponent;
import com.winhub.hernandez.xenvironment.components.GuestProgramLauncherComponent;
import com.winhub.hernandez.xenvironment.components.PulseAudioComponent;
import com.winhub.hernandez.xenvironment.components.SysVSharedMemoryComponent;
import com.winhub.hernandez.xenvironment.components.XServerComponent;
import com.winhub.hernandez.xserver.Pointer;
import com.winhub.hernandez.xserver.Atom;
import com.winhub.hernandez.xserver.Property;
import com.winhub.hernandez.xserver.ScreenInfo;
import com.winhub.hernandez.xserver.extensions.RandrExtension;
import com.winhub.hernandez.xserver.Window;
import com.winhub.hernandez.xserver.WindowManager;
import com.winhub.hernandez.xserver.XKeycode;
import com.winhub.hernandez.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;

public class XServerDisplayActivity extends AppCompatActivity {
    public static String NOTIFICATION_CHANNEL_ID = "Winlator";
    public static int NOTIFICATION_ID = 9004;
    private XServerView xServerView;
    // Version-A spike: auto-swaps the game onto a connected external display (TV), handheld = controller.
    private com.winhub.hernandez.display.ExternalDisplayController externalDisplayController;
    // "Launch this game on the TV" (com.winhub.hernandez.display.ExternalDisplay — the per-game TV tab, NOT
    // the frozen controller above). The launcher started this activity with setLaunchDisplayId(id) and
    // passed the same id as EXTRA_DISPLAY_ID, so the session knows which screen it was ASKED for; -1 for
    // every ordinary handheld launch.
    //
    // The extra is the REQUEST, never the answer: Android can decline a launch display silently — no
    // exception for the launcher to catch, the activity is simply created on the handheld instead. So
    // nothing keys off tvLaunchDisplayId on its own. sessionDisplayId is where the window actually IS
    // (read from the activity's own display, re-read whenever the configuration or the display set
    // changes) and onTvLaunchDisplay() is the single question every TV decision asks. A declined launch
    // is an ordinary handheld session that happens to know which screen it was refused: no TV settings,
    // and no unplug watch, because it has no TV to lose.
    private int tvLaunchDisplayId = -1;
    private int sessionDisplayId = android.view.Display.DEFAULT_DISPLAY;
    private boolean tvDisconnectHandled = false;
    // Set in onCreate when the launch display was refused; the user is told once, from setupUI.
    private boolean tvLaunchDeclined = false;
    private boolean tvLaunchDeclinedNotified = false;
    // Set on a real background (onPause outside PiP) so onResume rebuilds the guest audio sink.
    private boolean wasBackgrounded = false;
    // Mid-game output-route watcher: plugging/unplugging wired (or USB/BT/HDMI) headphones during play
    // changes Android's default output, but the guest's PulseAudio AAudioSink keeps its already-open
    // stream on the old device (and on unplug the stream dies without reopening -> muted speaker). We
    // catch add/remove and fire the same resetGuestAudio() the HDMI/background path uses. Registered in
    // onResume / dropped in onPause. `primed` swallows the initial device list delivered at register.
    private android.media.AudioDeviceCallback audioRouteCallback;
    private boolean audioRouteCallbackPrimed = false;
    // In-app wireless-cast device discovery (Google Cast via mDNS) for the Cast dialog.
    private com.winhub.hernandez.cast.CastDiscovery castDiscovery;
    // Version B Part 2: captures + H.264-encodes the game for casting (Step 1 records to a file).
    private com.winhub.hernandez.cast.GameCaster gameCaster;
    // Part 2 Step 2a: Cast v2 session + local HTTP server that serve/cast the captured clip to the TV.
    private com.winhub.hernandez.cast.CastSession castSession;
    private com.winhub.hernandez.cast.HttpFileServer castHttp;
    private com.winhub.hernandez.cast.TsSegmenter castSegmenter;   // live HLS segmenter fed by the encoder
    private Runnable pendingCastStart; // (unused in live mode) cancelable delayed step
    private InputControlsView inputControlsView;

    // ---- Controller-status toast (P5b) — debounced hot-plug plumbing ----
    // Coalesce a burst of add/remove/change callbacks (fast replug, or a pad that fans out into
    // several sibling sub-devices) into ONE toast within this window.
    private static final long CONTROLLER_TOAST_DEBOUNCE_MS = 300;
    private final android.os.Handler controllerToastHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private String pendingToastReason = null;
    private String pendingToastDescriptor = null;
    private final Runnable fireControllerToast = () -> {
        showControllerStatusToast(pendingToastReason != null ? pendingToastReason : "connected", pendingToastDescriptor);
        pendingToastReason = null;
        pendingToastDescriptor = null;
        // #333: re-evaluate auto-hide on the same debounced tick, once slot assignment has settled.
        updateAutoHideForControllers();
        // #333: keep the in-game Players tab list current on hot-plug — it otherwise only rebuilds at
        // launch / manual slot change / Reset Input, so a controller connected or removed mid-session
        // wouldn't appear/disappear in the list until one of those fired. Via a method (not an inline
        // field ref) so the field initializer doesn't forward-reference winHandler.
        refreshInGamePlayerSlotList();
    };
    private TouchpadView touchpadView;

    // ---- Controller-test panel (Controls > Players popup) — input isolation ----
    // While the popup is open (controllerTestActive == true) a game controller's key/axis events are
    // forked into controllerTestController — a THROWAWAY snapshot used ONLY to drive the visualizer —
    // and swallowed at the dispatch chokepoints so they NEVER reach winHandler / the guest. The flag is
    // strictly gated: when it's false the input path is byte-for-byte unchanged. All access is on the
    // main (input-dispatch) thread. lastControllerTestAxisLogMs throttles the axis-fall-through
    // diagnostic (the folded-in spike) to once per second.
    private boolean controllerTestActive = false;
    private final ExternalController controllerTestController = new ExternalController();

    // Optional Steam Controller support (Input Controls → Device → Steam Controller). Null unless the
    // setting is on AND SDL came up; when null the input path below is unchanged. Main-thread only.
    private SteamControllerBackend steamControllerBackend;
    private boolean controllerTestGuideDown = false;
    private long lastControllerTestAxisLogMs = 0L;

    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private ContainerManager containerManager;
    protected Container container;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private ImageFs imageFs;
    private com.winhub.hernandez.widget.fusionhud.FusionHudView fusionHud = null; // the in-game performance HUD
    // Single authoritative FPS source: ticked once per present, read by every overlay so they all
    // show the identical number (there is one place per renderer to feed).
    private final FpsCounter fpsCounter = new FpsCounter();
    // Lazily built when the Task Manager first polls; snapshots CPU/GPU/RAM/battery for the header.
    private com.winhub.hernandez.widget.HudMetrics tmHudMetrics;
    // Async-arriving HUD labels are cached so a HUD built live (style swapped mid-game) is populated too.
    private String hudEngineShort = null;       // short API/dx name for the HUD engine tag
    private String hudGpuName = null;           // GPU model string from _MESA_DRV_GPU_NAME
    private volatile InGameControlsEditor inGameControlsEditor;
    private boolean inGameEditorPreviousShowTouchscreen;
    private boolean inGameEditorPreviousTimeoutEnabled;
    private ControlsProfile inGameEditorPreviousProfile;
    // #333 auto-hide OSC controls: the baseline visibility the USER chose (launch pref + live drawer
    // toggles). Auto-hide hides/restores relative to this so it never forces controls on when the user
    // wanted them off, and a manual re-show is remembered. controlsEditorOpen suspends auto-hide while
    // the in-game controls editor is up (it force-shows controls for editing).
    private boolean userWantsControlsShown;
    private boolean controlsEditorOpen;
    private Shortcut shortcut;
    // Xbox store game (shortcut storeSource=xbox): the licensed launch prepared before Wine starts
    // (XboxRuntime.prepare) — the materialised exe to run, its environment, and the lease/bridge to close.
    private com.winhub.hernandez.store.xbox.XboxRuntime.Launch xboxLaunch;
    // In-game Steam achievement watcher (Goldberg/GSE achievements.json → gold pills). Genuine-Steam
    // shortcuts only; armed on the launch worker thread in setupXEnvironment, stopped in onDestroy. Null
    // for non-Steam launches.
    private AchievementWatcher achievementWatcher;
    // The seed/watch hook (maybeSeedAndStartAchievementWatcher) is called once, from setupXEnvironment on
    // the launch WORKER thread (off-main so its fetch/read can't block the launch → the setupUI call site
    // was removed after it caused a first-launch black screen). This flag still guards the WATCHER so, if
    // the hook is ever re-entered, it re-runs the (idempotent) seed but never re-arms/double-arms the
    // FileObserver. Reset on teardown so a later launch in the same activity instance re-arms cleanly.
    private boolean achievementWatcherArmed = false;
    // In-game achievement pill producer for SteamLite / RealSteam mode: our headless Steam agent writes
    // each real-server unlock into C:\wn-achievement-events\ and this FileObserver watches them, popping
    // the SAME gold pill the Goldberg path does. Armed only when a RealSteam launch is actually staged
    // (realSteamPlan != null), alongside the Goldberg watcher; stopped in the same teardown. Null for
    // every other launch. Armed once (its own guard flag), like achievementWatcher above.
    private SteamLiteAchievementWatcher steamLiteAchievementWatcher;
    private boolean steamLiteAchievementWatcherArmed = false;
    // Real-Steam (VAC) launch plan (feature M3). Non-null ONLY when this is a genuine-Steam shortcut with
    // launchMode=RealSteam AND all prerequisites resolved (token/appId/install dir/SteamLite package):
    // built on the launch WORKER thread in setupXEnvironment (staging + env), then read by
    // getWineStartCommand() to rewrite the launch target to run our agent (steam.exe) with the per-game
    // spec. Null ⇒ every non-RealSteam (or failed-prep) launch is byte-for-byte unchanged.
    private RealSteamLauncher.Plan realSteamPlan;
    /** EA launcher-chain title (EA Desktop) — set by maybeStageRealSteam(); drives the FEX preset clamp + overlay hints. */
    private boolean realSteamEaChain = false;
    // True while THIS activity has the app's own Steam CM session suspended for the in-container
    // real-Steam session (set right after realSteamPlan is armed, cleared by releaseRealSteamSession).
    // Separate from realSteamPlan so the release is idempotent across exit() and onDestroy().
    private volatile boolean realSteamSessionHeld = false;
    private String graphicsDriver = Container.DEFAULT_GRAPHICS_DRIVER;
    // Which Vulkan driver the host compositor/present layer runs on ("system" = Android's own driver,
    // or an installed adrenotools Turnip). Separate from graphicsDriver (which the guest game renders
    // through). Default "system" — a Turnip compositor can black-screen on builds whose WSI doesn't
    // support the surface, so it's opt-in. Applied via VulkanRenderer.setDriverInfo before nativeInit.
    private String rendererDriverId = "system";
    private HashMap<String, String> graphicsDriverConfig;
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    // Best-effort RECORD_AUDIO request when a mic-enabled DirectAudio game launches (see onCreate).
    private static final int RECORD_AUDIO_REQUEST_CODE = 0xADD;
    private String emulator = Container.DEFAULT_EMULATOR;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private KeyValueSet dxwrapperConfig;
    private String startupSelection;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private boolean firstTimeBoot = false;
    private SharedPreferences preferences;
    private Callback<String> wineDebugLogCallback;
    private java.io.PrintWriter wineDebugWriter;
    private OnExtractFileListener onExtractFileListener;
    private WinHandler winHandler;
    private WineRequestHandler wineRequestHandler;
    private float globalCursorSpeed = 1.0f;
    private short taskAffinityMask = 0;
    private short taskAffinityMaskWoW64 = 0;
    // Prefer-big-cores live re-pin: guest pid -> its affinity mask BEFORE we changed it, so toggle OFF
    // restores each process exactly (revert philosophy). Populated on toggle ON, cleared on OFF.
    private final java.util.HashMap<Integer, Integer> bigCoreAffinitySnapshot = new java.util.HashMap<>();
    private int frameRatingWindowId = -1;
    // Wayland mode has no X window to bind the HUD to; the compositor's game window stands in.
    private static final int WAYLAND_HUD_WINDOW_ID = Integer.MAX_VALUE;
    // Master HUD on/off, parsed from the fps config's `hudEnabled` key (default on). When false, every
    // overlay style stays GONE even while a game window is bound to frameRatingWindowId — the drawer's
    // "Show HUD" master toggle drives this live via onFpsConfigApply.
    private boolean hudCounterEnabled = true;
    // Windows that have published a _MESA_DRV property (GPU/render windows). The perf HUD binds to one
    // of these (frameRatingWindowId); we keep the whole set so that when the bound window unmaps we can
    // re-bind to another still-live one instead of hiding the HUD permanently — games like Dirt 3 /
    // Dirt Showdown open an intro window then swap to the real render window.
    private final java.util.LinkedHashSet<Integer> mesaDrvWindowIds = new java.util.LinkedHashSet<>();
    private boolean cursorLock; // Flag to track if pointer capture was requested
    private final float[] xform = XForm.getInstance();
    private ContentsManager contentsManager;
    private MidiHandler midiHandler;
    private String midiSoundFont = "";
    private String lc_all = "";
    private String vkbasaltConfig = "";
    // Supersampling ("Render scale"): true when the launch resolution was multiplied above the
    // display res, so the Vulkan compositor should run a quality Lanczos downscale. Resolved in
    // onCreate (from the container/shortcut "renderScale" extra) and consumed in setupUI.
    private boolean hqDownscale = false;
    PreloaderDialog preloaderDialog = null;
    // ---- Launch progress overlay ----
    // Flipped true when the game first renders (the launch overlay is dismissed). Shared guard read
    // from the X11 window thread + the guest-termination thread, written on the UI thread.
    private volatile boolean winStarted = false;
    // Hold the launch screen this long past the first rendered game frame, so the boot steps are
    // actually seen instead of flashed away on a fast-booting game. The game renders behind it.
    private static final long LAUNCH_OVERLAY_GRACE_MS = 5000L;
    // "Not-frozen" reassurance timers over the unmeasurable guest-boot tail. Neither kills the launch.
    private static final long LAUNCH_SLOW_HINT_MS = 15_000L;
    private static final long LAUNCH_STILL_WORKING_MS = 90_000L;
    private final Handler launchTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable launchSlowHintRunnable = () -> preloaderDialog.hint(
            "Taking longer than usual — first launch can compile shaders. Not frozen, please wait.");
    private final Runnable launchStillWorkingRunnable = () -> preloaderDialog.hint(
            "Still working. If this seems stuck, check the log.");
    private Runnable configChangedCallback = null;
    private boolean isPaused = false;
    // ReShade "freeze-frame preview" (Live preview OFF): the guest is SIGSTOP'd while tuning and each
    // committed change briefly pulses to reveal it. reshadeLivePreview mirrors the persisted toggle;
    // reshadePreviewPaused marks the current freeze as preview-owned (a subset of isPaused, so tapping
    // the pause box or manually resuming clears it); reshadePulseInProgress serializes overlapping
    // pulses. isPaused stays the single source of truth for "frozen"; a pulse blips SIGCONT/SIGSTOP
    // underneath it without flipping the UI state.
    private boolean reshadeLivePreview = false;
    private boolean reshadePreviewPaused = false;
    private volatile boolean reshadePulseInProgress = false;
    private static final int RESHADE_PULSE_TARGET_PRESENTS = 2;   // real presents to reveal a change
    private static final long RESHADE_PULSE_FALLBACK_MS = 80L;    // re-freeze if the game isn't presenting
    private boolean isRelativeMouseMovement = false;
    private boolean isMouseDisabled = false;
    private boolean pointerCaptureRequested = false;

    // Inside the XServerDisplayActivity class
    private SensorManager sensorManager;
    // Gyro (motion aim) — rate samples go straight to WinHandler, which gates them on the
    // activator button and overlays them on the right stick. Inert when the device has no gyroscope.
    private Sensor gyroSensor;
    // Orientation ("tilt to aim") mode reads an absolute pose instead of a rate. GAME_ROTATION_VECTOR
    // rather than ROTATION_VECTOR on purpose: it fuses gyro + accelerometer only, so a speaker magnet
    // or a magnetic case can't drag the aim around. ROTATION_VECTOR is the fallback for the handful of
    // devices that only expose the magnetometer-fused one; null means orientation mode is unavailable.
    private Sensor gyroRotationSensor;
    private boolean gyroListenerRegistered = false;
    // Which sensor TYPE is currently registered. Without this a mid-session mode change would hit the
    // "already registered" early-out and silently keep feeding the wrong sensor to the wrong entry point.
    private int registeredGyroSensorType = -1;
    // Display rotation, cached. getDisplay().getRotation() is a binder call and the orientation remap
    // needs it on every sample (50-200 Hz while the game renders), so it is refreshed on the events
    // that can actually change it instead: onCreate, the config-changed path, and the display listener.
    private volatile int cachedDisplayRotation = Surface.ROTATION_0;
    // Scratch for the orientation math. All three SensorManager calls write into caller-supplied
    // arrays, so these live here and the sample path allocates nothing.
    private final float[] gyroRotationMatrix = new float[9];
    private final float[] gyroRemappedMatrix = new float[9];
    private final float[] gyroOrientationAngles = new float[3];
    // getRotationMatrixFromVector throws IllegalArgumentException on some Samsung builds when the
    // rotation vector carries more than 4 components, so anything longer is copied down into this.
    private final float[] gyroRotationVector = new float[4];
    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (winHandler == null) return;
            int type = event.sensor.getType();
            if (type == Sensor.TYPE_GYROSCOPE) {
                winHandler.updateGyroData(event.values[0], event.values[1]);
            }
            else if (type == Sensor.TYPE_GAME_ROTATION_VECTOR || type == Sensor.TYPE_ROTATION_VECTOR) {
                computeGyroOrientation(event.values);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    // Playtime stats tracking
    private long startTime;
    private SharedPreferences playtimePrefs;
    private String shortcutName;
    private Handler handler;
    private Runnable savePlaytimeRunnable;
    // Playtime is accumulated in memory and flushed periodically. One second was a disk write every
    // second for the whole session (SharedPreferences apply → constant I/O wakeups); 30s loses at
    // most half a minute on a hard crash and is invisible to the hub's playtime display.
    private static final long SAVE_INTERVAL_MS = 30000;

    // Version marker for the bundled graphics_driver/extra_libs.tzst payload (vkBasalt layer +
    // shared .so's). BUMP THIS whenever app/src/main/assets/graphics_driver/extra_libs.tzst is
    // repacked, so existing/old containers re-extract the updated .so on their next launch instead
    // of silently keeping the stale one. Read the persisted marker at
    // imageFs.getLibDir()/.extra_libs_version; a mismatch (or missing marker => -1) triggers a
    // re-extract. Value 2 = the 2.2.1 patched Tier-1 libvkbasalt.so (md5 3129127c…). Value 5 = leegao
    // compat_layer 22960fb3 (Mali YCbCr fallback, maintenance5, calibrated timestamps).
    private static final int EXTRA_LIBS_VERSION = 5;

    private Handler  timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;

    // Task Manager refresh runs on a plain main-thread Handler (render-independent), not a Compose
    // LaunchedEffect timer. The Compose effect clock stalls on the Vulkan host-render path, so the
    // old in-drawer delay() loop never fired there (Task Manager stayed empty). This mirrors the
    // java.util.Timer the pre-Compose TaskManagerDialog used and works on both renderers.
    private final Handler tmPollHandler = new Handler(Looper.getMainLooper());
    private final Runnable tmPollRunnable = new Runnable() {
        @Override
        public void run() {
            XServerDialogState ds = XServerDialogState.INSTANCE;
            if (winHandler != null) winHandler.listProcesses();
            updateTmCpuMemory(ds);
            tmPollHandler.postDelayed(this, 1000);
        }
    };

    // ---- Component installer auto-exit (Phase 3b) ----
    // When launched to run a component installer (.NET/vcredist), watch the guest process list and
    // auto-close the session once the installer (and any msiexec it spawns) has exited, so the user
    // doesn't have to manually leave the container after each installer.
    private String componentInstallerExe;
    private boolean installerProcSeen = false;
    private int installerGoneTicks = 0;
    private final java.util.ArrayList<String> installerTickNames = new java.util.ArrayList<>();
    private final Handler installerWatchHandler = new Handler(Looper.getMainLooper());
    private final OnGetProcessInfoListener installerProcListener = new OnGetProcessInfoListener() {
        @Override
        public void onGetProcessInfo(int index, int count, ProcessInfo info) {
            if (index == 0) installerTickNames.clear();
            if (info != null && info.name != null) installerTickNames.add(info.name.toLowerCase());
            if (count == 0 || index == count - 1) evaluateInstallerTick();
        }
    };
    private final Runnable installerWatchRunnable = new Runnable() {
        @Override
        public void run() {
            if (winHandler != null) {
                // Re-assert our listener (the Task Manager may have taken it) then request the list.
                winHandler.setOnGetProcessInfoListener(installerProcListener);
                winHandler.listProcesses();
            }
            installerWatchHandler.postDelayed(this, 2000);
        }
    };

    // ---- Auto-close session on game exit (per-container / per-shortcut) ----
    // For launches from a game shortcut, watch the guest process list and close the session once the
    // game's own executable has been seen and then disappears — so the user isn't left sitting on the
    // empty Wine desktop (black screen) after quitting the game. Mirrors the installer auto-exit above.
    // Gated to shortcut launches only and to the per-game/-container "autoCloseOnExit" setting.
    private boolean autoCloseOnExitEnabled = false;
    private String autoCloseExeName;          // lowercased basename of the launched game exe
    private boolean gameProcSeen = false;
    private int gameGoneTicks = 0;
    private final java.util.ArrayList<String> gameTickNames = new java.util.ArrayList<>();
    private final Handler gameExitWatchHandler = new Handler(Looper.getMainLooper());
    private final OnGetProcessInfoListener gameExitProcListener = new OnGetProcessInfoListener() {
        @Override
        public void onGetProcessInfo(int index, int count, ProcessInfo info) {
            if (index == 0) gameTickNames.clear();
            if (info != null && info.name != null) gameTickNames.add(info.name.toLowerCase());
            if (count == 0 || index == count - 1) evaluateGameExitTick();
        }
    };
    private final Runnable gameExitWatchRunnable = new Runnable() {
        @Override
        public void run() {
            if (winHandler != null) {
                // Re-assert our listener (Task Manager polling may have taken it) then request the list.
                winHandler.setOnGetProcessInfoListener(gameExitProcListener);
                winHandler.listProcesses();
            }
            gameExitWatchHandler.postDelayed(this, 2000);
        }
    };

    // Drift-detected CPU-affinity re-pin. A user-selected affinity (Task Manager Processor Affinity
    // dialog from the in-game side menu, Prefer Big Cores, or per-window task affinity) is a ONE-SHOT
    // SetProcessAffinityMask: it pins only the threads that exist at call time. Game engines spawn the
    // bulk of their job/worker threads once real gameplay starts (after menus/loading), and under
    // wow64/FEX those new Windows threads don't reliably inherit the process mask — they escape back
    // to all cores and Android's EAS scheduler parks them on the little (efficiency) cluster (hotice77
    // report: WD2 ~52% usage, 12fps, load on the wrong cluster). Nothing in this stack enforces
    // affinity persistently, and previously the ONLY re-pin lived inside the TM-OPEN refresh loop, so
    // closing the Task Manager stopped all enforcement.
    //
    // Rather than re-pin on a blind timer (which re-walks every thread each tick even when nothing
    // changed — a periodic cost that can surface as micro-stutter on heavy titles), we DRIFT-DETECT:
    // watch each pinned pid's Linux thread count (/proc/<pid>/task) and re-apply the mask ONLY when it
    // grows, i.e. exactly when new unpinned threads appeared. In steady-state gameplay (thread pool
    // stable) nothing fires, so there is no rhythmic re-pin and no stutter; at the load→gameplay
    // transition the new engine threads are caught within one interval and pinned once. wine maps
    // SetProcessAffinityMask onto the process's current Linux threads, so a single re-apply re-pins
    // them all. (Native DXVK/Turnip driver threads stay unreachable by any Windows affinity API — the
    // complete fix for those would be host-side per-tid sched_setaffinity, deliberately not done here.)
    private static final long AFFINITY_DRIFT_CHECK_INTERVAL_MS = 2000;
    private final Handler affinityReapplyHandler = new Handler(Looper.getMainLooper());
    // pid -> highest thread count seen at the last re-pin; a higher count means new threads escaped.
    private final java.util.HashMap<Integer, Integer> affinityThreadHighWater = new java.util.HashMap<>();
    // The winhandler affinity pid is a Wine "Windows" pid that doesn't exist under /proc (device-proven:
    // win pid 324 vs real Linux pid 5062), so the checker can't read thread info by it. Instead we track
    // the game's exe + chosen mask and resolve its real LINUX pid by exe name, then drift-detect + re-pin
    // HOST-SIDE (taskset), which also reaches the native FEX/driver threads the Windows path can't.
    private volatile String affinityTargetExe = null;
    private volatile int affinityTargetMask = 0;
    private int affinityLinuxPid = -1;
    private final Runnable affinityReapplyRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                final int mask = affinityTargetMask;
                final String exe = affinityTargetExe;
                if (mask != 0 && exe != null) {
                    // Resolve/refresh the game's LINUX pid (winhandler pids are Windows pids, unusable with
                    // /proc). Cheap in steady state — only re-scans /proc when the cached pid has died.
                    int lpid = affinityLinuxPid;
                    if (lpid <= 0 || ProcessHelper.getThreadCount(lpid) < 0) {
                        lpid = ProcessHelper.findLinuxPidByExe(exe);
                        if (lpid != affinityLinuxPid) affinityThreadHighWater.remove(affinityLinuxPid);
                        affinityLinuxPid = lpid;
                    }
                    if (lpid > 0) {
                        int threads = ProcessHelper.getThreadCount(lpid);
                        Integer prev = affinityThreadHighWater.get(lpid);
                        if (threads >= 0 && (prev == null || threads > prev)) {
                            // First sighting, or new threads spawned since the last pin -> re-pin HOST-SIDE so
                            // the fresh (all-core) threads get pulled onto the chosen cores. taskset -a hits
                            // every current thread, including the native FEX/driver threads the Windows API misses.
                            boolean ok = ProcessHelper.setLinuxAffinity(lpid, mask);
                            affinityThreadHighWater.put(lpid, threads);
                            if (ProcessHelper.PRINT_DEBUG) {
                                Log.d("AffinityDrift", "lpid=" + lpid + " exe=" + exe + " threads " + prev + "->"
                                        + threads + " host-repin mask=0x" + Integer.toHexString(mask) + " ok=" + ok
                                        + " achieved=0x" + Integer.toHexString(ProcessHelper.getProcessAffinityMask(lpid)));
                            }
                        } else if (threads >= 0 && ProcessHelper.PRINT_DEBUG) {
                            // Steady state (no new threads): verify the mask is actually being honored. If the
                            // real Cpus_allowed differs from what we asked, something below us — a vendor cpuset
                            // cgroup / scheduler (e.g. HyperOS/MIUI) — is overriding the pin. Surfacing it turns
                            // "affinity didn't help" into a concrete, testable cause.
                            int got = ProcessHelper.getProcessAffinityMask(lpid);
                            if (got != 0 && got != mask) {
                                Log.w("AffinityDrift", "lpid=" + lpid + " NOT-HONORED requested=0x"
                                        + Integer.toHexString(mask) + " achieved=0x" + Integer.toHexString(got)
                                        + ((got & ~mask) != 0
                                            ? " (ROM re-allowed excluded cores — likely cpuset/scheduler override)"
                                            : " (cpuset allows only a subset of the requested cores)"));
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w("XServerDisplayActivity", "Affinity drift-check tick failed", t);
            }
            affinityReapplyHandler.postDelayed(this, AFFINITY_DRIFT_CHECK_INTERVAL_MS);
        }
    };

    /**
     * Idempotently (re)start the periodic affinity drift check. Safe to call from every affinity
     * set-site — it clears any pending tick first, so repeated calls never stack. The runnable
     * self-gates on {@link WinHandler#hasManualAffinity()}, so starting it before any mask is set is
     * harmless; it is torn down in {@link #exit()}.
     */
    private void startAffinityReapply() {
        affinityReapplyHandler.removeCallbacks(affinityReapplyRunnable);
        affinityReapplyHandler.postDelayed(affinityReapplyRunnable, AFFINITY_DRIFT_CHECK_INTERVAL_MS);
    }

    // Live detection of which Direct3D API the running game actually uses, so the FPS-counter
    // overlay can show VKD3D for D3D12 titles instead of always printing the D3D9/10/11 wrapper
    // name (DXVK/VEGAS). Both wrappers are always present in the prefix, so the only reliable tell
    // is which d3d module the game has mapped in — we scan /proc/<pid>/maps (the app's own wine
    // processes are the only ones visible under our uid).
    private Thread dxApiThread;
    // Game pid resolved by findRunningGamePid, cached across polls so the dx-api-detect thread does
    // not re-walk /proc and re-read every cmdline every 2 s. Cleared when the pid dies or no longer
    // looks like the launched game.
    private String dxApiCachedPid;

    /**
     * Resolve the game's ACTUAL graphics API from the modules the guest wine processes have mapped,
     * so the perf HUD's "engine" label reflects what the game really renders with — not just the
     * configured DX wrapper. Both DX wrappers are always present in the prefix, so the only reliable
     * tell is which module the game loaded (scanned once per PID; the app's own wine processes are
     * the only ones visible under our uid).
     *
     * <p>Priority matters: D3D is checked FIRST, because a DXVK game ALSO maps vulkan-1.dll and a
     * WineD3D game ALSO maps opengl32.dll — the underlying API would otherwise mask the D3D layer
     * sitting on top of it. Only when NO d3d*.dll is mapped do we fall through to the native path.
     *
     * @param wrapper the configured D3D9/10/11 wrapper name (DXVK / VEGAS / WineD3D); used to tag the
     *                D3D9/10/11 result. D3D12 always runs on VKD3D regardless of this.
     * @return e.g. "D3D12 · VKD3D", "D3D11 · DXVK", "Vulkan", "Zink"/"OpenGL", or null if nothing
     *         graphics-related is mapped yet (caller keeps polling).
     */
    private String detectActiveDxApi(String wrapper) {
        java.io.File[] pids = new java.io.File("/proc").listFiles();
        if (pids == null) return null;
        boolean d3d12 = false, d3d11 = false, d3d10 = false, d3d9 = false;
        boolean vulkan = false, opengl = false;
        String dxPid = null;   // pid of the process holding the d3d12 modules (the game) — for log correlation
        for (java.io.File p : pids) {
            if (!p.isDirectory() || !android.text.TextUtils.isDigitsOnly(p.getName())) continue;
            boolean pHasD3d12 = false;
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.FileReader(new java.io.File(p, "maps")))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // Case-insensitive matching without allocating a lowercased copy of every maps line.
                    if (indexOfIgnoreCase(line, ".dll") < 0) continue;
                    // NOTE: do NOT break on the d3d12 hit — a dual-API build maps d3d12core.dll for a
                    // startup probe yet renders on d3d11 (both resident), so we must keep scanning this
                    // process to learn whether d3d11 is ALSO mapped (resolved below via the engine log).
                    if (indexOfIgnoreCase(line, "d3d12core.dll") >= 0 || indexOfIgnoreCase(line, "d3d12.dll") >= 0) { d3d12 = true; pHasD3d12 = true; }
                    else if (indexOfIgnoreCase(line, "d3d11.dll") >= 0) d3d11 = true;
                    else if (indexOfIgnoreCase(line, "d3d10.dll") >= 0) d3d10 = true;
                    else if (indexOfIgnoreCase(line, "d3d9.dll") >= 0)  d3d9  = true;
                    // Wine PE names: winevulkan.dll (the Wine Vulkan driver) and vulkan-1.dll (the
                    // loader apps link against); opengl32.dll is Wine's GL. DXVK/VKD3D also pull in
                    // vulkan-1.dll, hence the D3D-first ordering below.
                    else if (indexOfIgnoreCase(line, "winevulkan.dll") >= 0 || indexOfIgnoreCase(line, "vulkan-1.dll") >= 0) vulkan = true;
                    else if (indexOfIgnoreCase(line, "opengl32.dll") >= 0) opengl = true;
                    // Once BOTH top ranks are seen we have everything the ambiguous dual-API case needs;
                    // stop scanning this process early (perf parity with the old break-on-d3d12).
                    if (d3d12 && d3d11) break;
                }
            } catch (Exception ignore) {}
            // The first process carrying d3d12 IS the game — remember its pid so the engine-log
            // resolver can prove the log belongs to it (not a stale one from another game). Break
            // here keeps the old top-priority early-exit.
            if (pHasD3d12) { dxPid = p.getName(); break; }
        }
        final String SEP = " · ";                       // " · " (middle dot)
        // 1. D3D wins over the API it is layered on (rank 12 > 11 > 10 > 9).
        if (d3d12) {
            // Ambiguous dual-API build: a Unity title started with -force-d3d11 still LoadLibrary's
            // d3d12core.dll for a one-time D3D12 capability probe and then renders on d3d11. The probe
            // DLL stays resident, so module presence would report D3D12 forever. When BOTH are mapped,
            // ask the engine's own log which device it actually created (the only host-visible truth).
            if (d3d11) {
                String resolved = resolveDualApiFromEngineLog(wrapper, dxPid);
                if (resolved != null) return resolved;
            }
            return "D3D12" + SEP + "VKD3D";                  // D3D12 always runs on VKD3D
        }
        if (d3d11) return "D3D11" + SEP + wrapper;
        if (d3d10) return "D3D10" + SEP + wrapper;
        if (d3d9)  return "D3D9"  + SEP + wrapper;
        // 2. Native path — only reached when no D3D layer is mapped. Vulkan is checked BEFORE OpenGL
        //    on purpose: DLL-mapping can't reliably tell native-Vulkan from Zink-backed GL apart —
        //    opengl32.dll is loaded proactively (Wine desktop / app startup, not only when GL renders),
        //    and Zink itself loads vulkan-1.dll, so both APIs map BOTH DLLs. Vulkan-first keeps the
        //    common case correct (native Vulkan reads "Vulkan"); an OpenGL/Zink title then reads
        //    "Vulkan" too, which is underlying-accurate since Zink runs GL on Vulkan. (Tried opengl-first
        //    — it mislabeled the native Vulkan cube as "Zink" because opengl32 is always resident.)
        if (vulkan) return "Vulkan";
        if (opengl) return guestGlIsZink() ? "Zink" : "OpenGL";
        // 3. Nothing graphics-related mapped yet — keep polling (unchanged behaviour).
        return null;
    }

    /** Case-insensitive indexOf without allocating a lowercased copy of the haystack. */
    private static int indexOfIgnoreCase(String haystack, String needle) {
        final int max = haystack.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) return i;
        }
        return -1;
    }

    /**
     * P4 on WAYLAND: is the game rendering with native Vulkan or native OpenGL? Asked of the one
     * process that can answer — the game's own — and answered from what it has actually mapped.
     *
     * <p>{@link #detectActiveDxApi} deliberately refuses to separate the two on X11, where guest-side
     * Zink makes a GL title map vulkan-1.dll as well; it also ORs its flags over EVERY wine process,
     * so explorer.exe's modules count as the game's. Neither works here. On Wayland the GL stack sits
     * on the HOST side of winewayland.drv (wine's unix libEGL -> Zink -> Turnip), and — device-measured
     * on this layer — it is mapped into a process only when that process really takes the GL path:
     * <ul>
     *   <li><b>OpenGL</b>: the layer's own {@code lib/libEGL.so.1} / {@code libgallium-*.so} appear in
     *       the game's maps ({@code libwayland-egl.so} does not count: winewayland links it into every
     *       process). Measured against the counter-example:
     *       a D3D11-on-DXVK title (Titanfall 2, live Wayland session) maps NONE of them.</li>
     *   <li><b>Vulkan</b> (native, or DXVK/VKD3D on top): {@code winevulkan.so}, the unix half of
     *       winevulkan.dll, which only loads when the guest itself uses Vulkan.</li>
     * </ul>
     * Those are real ELF libraries out of the layer, so they are file-backed in
     * {@code /proc/<pid>/maps} even on arm64ec, where the PE-only DLLs are invisible to a module scan.
     * Costs one maps read per 2s poll (detectActiveDxApi reads every process's).
     *
     * <p>GL evidence is weighed FIRST because it is the specific signal: loading the GL stack means a
     * GL context was created, while winevulkan says nothing about what is layered on top of it.
     * {@code opengl32.so} is deliberately NOT evidence — the same Titanfall 2 session maps it while
     * rendering D3D11, so wine's GL DLL being resident proves nothing (the X11 resolver's note about
     * opengl32 being loaded proactively holds here too).
     *
     * <p>Returns null — not a guess — when the game pid isn't up yet or nothing is mapped; the caller
     * then leaves the neutral "Vulkan" (the compositor, true of every Wayland session) on the HUD. The
     * HUD must never name an API, or a wrapper, that nothing proves.
     */
    private String resolveWaylandNativeApi() {
        try {
            String pid = findRunningGamePid();
            if (pid == null) return null;
            boolean gl = false, vulkan = false;
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.FileReader(new java.io.File("/proc/" + pid + "/maps")))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.indexOf('/') < 0) continue;          // anonymous mapping — no module name
                    line = line.toLowerCase();
                    // The layer's Mesa EGL/Zink. "libegl.so.1" is Mesa's soname — Android's platform
                    // EGL is /system/lib64/libEGL.so (no ".1"), so this can't match the host's.
                    // libwayland-egl.so is NOT evidence either: winewayland links it for its own window
                    // surfaces, so every Wayland Wine process maps it - a Vulkan game (DOOM 2016's
                    // DOOMx64vk.exe: only libvulkan_wrapper/libvulkan_freedreno mapped) read "OpenGL".
                    if (line.indexOf("libegl.so.1") >= 0 || line.indexOf("libgallium") >= 0) { gl = true; break; }
                    if (line.indexOf("winevulkan.so") >= 0 || line.indexOf("winevulkan.dll") >= 0
                            || line.indexOf("vulkan-1.dll") >= 0) vulkan = true;
                }
            }
            if (gl) return "OpenGL";
            if (vulkan) return "Vulkan";
        } catch (Exception ignore) {}
        return null;
    }

    // Cache for resolveDualApiFromEngineLog: Player.log is near-static once the device is created,
    // so we only re-parse when its mtime/length changes — keeps the 2s poll cheap on a chatty log.
    private long lastEngineLogMtime = -1;
    private long lastEngineLogLen = -1;
    private String lastEngineLogApi = null;

    // ---- P3 (arm64ec-proof wrapper-log API resolver) state ---------------------------------------
    // Where DXVK/VKD3D write their startup logs THIS launch: the user's log dir when the "DXVK & VKD3D"
    // logging switch is ON, else a tiny PRIVATE hudapi dir we keep alive purely so the HUD always has
    // ground truth. Needed because on arm64ec Proton the DX wrappers (d3d11/d3d12/dxgi) are PE-only —
    // they never appear in /proc/<pid>/maps, so detectActiveDxApi is structurally blind to the DX API;
    // the wrapper logs are the only host-visible signal. Set in dxvkLogDir(); read by
    // resolveApiFromWrapperLogs(). May stay null (e.g. a WineD3D container), in which case P3 is inert.
    private File wrapperLogDir;
    // Wall-clock at launch (set in onCreate) — the freshness gate for P3.
    private long sessionStartMs;
    // Cache for resolveApiFromWrapperLogs, mirroring resolveDualApiFromEngineLog's (file,mtime,len)
    // shortcut so the 2s poll doesn't re-parse a static log. Only one game runs per activity, so a
    // cached winner can never point at a different title.
    private String lastWrapperLogPath = null;
    private long lastWrapperLogMtime = -1;
    private long lastWrapperLogLen = -1;
    private String lastWrapperLogApi = null;

    // Wine spins up a fixed set of system .exes alongside the game; never mistake one for the game exe.
    private static final java.util.Set<String> WINE_SYSTEM_EXES = new java.util.HashSet<>(java.util.Arrays.asList(
            "services.exe", "winedevice.exe", "plugplay.exe", "explorer.exe", "svchost.exe", "rpcss.exe",
            "wineboot.exe", "conhost.exe", "start.exe", "cmd.exe", "rundll32.exe", "tabtip.exe",
            "winedbg.exe", "wineconsole.exe", "regsvr32.exe", "msiexec.exe", "wscript.exe", "cscript.exe"));

    // Processes that render through the DX wrappers WITHOUT being the game: store clients, their overlay
    // proxies and launcher-chain helpers. EA Desktop's CEF/Qt front end and its in-game overlay proxy
    // (IGOProxy32/64) create real D3D12 swapchains on VKD3D, so their vkd3d-proton.log ranked above the
    // game's DXVK D3D11 log and every EA title read "D3D12 · VKD3D" (device-seen: Need for Speed Payback
    // and Most Wanted, both D3D11 on DXVK). Wrapper logs written by these exes never label the game.
    private static final java.util.Set<String> HELPER_RENDERER_EXES = new java.util.HashSet<>(java.util.Arrays.asList(
            "steam.exe", "steamwebhelper.exe", "gameoverlayui.exe",
            "eadesktop.exe", "eacefsubprocess.exe", "ealauncher.exe", "ealaunchhelper.exe", "easteamproxy.exe",
            "easteamlauncher.exe", "eaepiclauncher.exe", "link2ea.exe", "igoproxy32.exe", "igoproxy64.exe",
            "activationui.exe", "eabackgroundservice.exe", "ealocalhostsvc.exe", "eaanticheat.gameservicelauncher.exe",
            "origin.exe", "originwebhelperservice.exe", "originclientservice.exe",
            "upc.exe", "uplay.exe", "ubisoftconnect.exe", "ubisoftgamelauncher.exe", "uplaywebcore.exe",
            "epicgameslauncher.exe", "epicwebhelper.exe", "eossdk-win64-shipping.exe",
            "galaxyclient.exe", "galaxyclient helper.exe", "gog galaxy notifications renderer.exe"));

    /** Program name recorded in a vkd3d-proton.log header ({@code Program name: "<exe>"}), lowercased, or null. */
    private static String vkd3dLogProgramName(File log) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(log))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 60) {
                int i = line.indexOf("Program name: \"");
                if (i < 0) continue;
                int start = i + "Program name: \"".length();
                int end = line.indexOf('"', start);
                if (end > start) return line.substring(start, end).toLowerCase();
            }
        } catch (Exception ignore) {}
        return null;
    }

    /** Stem of a DXVK per-API log name ({@code <stem>_d3d11.log} -> {@code <stem>}), lowercased. */
    private static String dxvkLogStem(File log) {
        String n = log.getName().toLowerCase();
        int i = n.lastIndexOf("_d3d");
        return i > 0 ? n.substring(0, i) : n;
    }

    private static boolean isHelperRendererExe(String exeLower) {
        return exeLower != null && HELPER_RENDERER_EXES.contains(exeLower.endsWith(".exe") ? exeLower : exeLower + ".exe");
    }

    /**
     * Disambiguate a dual-API build (BOTH d3d11 and d3d12 mapped) by asking the game engine which
     * graphics device it ACTUALLY created — ground truth that module presence cannot provide.
     *
     * <p>Unity writes its active device to {@code Player.log} ("Forcing GfxDevice: Direct3D 11",
     * "Direct3D:\n    Version:  Direct3D 11.0 [level 11.1]"). A Unity title launched with
     * {@code -force-d3d11} still maps d3d12core.dll for a one-time D3D12 capability probe, so
     * /proc/maps shows both APIs; the log is the only host-visible signal for which one renders. We
     * scan for the "Direct3D &lt;N&gt;" token and keep the LAST match (a mid-run device switch still
     * resolves). Unity's capability-probe lines read "D3D12 Device Filter", NOT "Direct3D 12", so
     * they never false-positive here.
     *
     * @param pid pid of the running game process (holder of the d3d12 modules) — used to prove the
     *            chosen log belongs to THIS game, not a stale one left by a different title.
     * @return "D3D12 · VKD3D" / "D3D11 · &lt;wrapper&gt;" / etc., or null when no engine log that
     *         belongs to the running game is available (non-Unity title, logs off, or not written
     *         yet) so the caller keeps the module-presence result.
     */
    private String resolveDualApiFromEngineLog(String wrapper, String pid) {
        try {
            String gameDir = runningGameDirToken(pid);
            if (gameDir == null) return null;
            File localLow = new File(imageFs.home_path, ".wine/drive_c/users/xuser/AppData/LocalLow");
            File log = matchingPlayerLog(localLow, gameDir);
            if (log == null) return null;
            long mtime = log.lastModified(), len = log.length();
            if (mtime == lastEngineLogMtime && len == lastEngineLogLen) return lastEngineLogApi;
            String api = null;
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(log))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int i = line.indexOf("Direct3D 1");     // "Direct3D 12" / "11" / "10"
                    if (i >= 0 && i + 10 < line.length()) {
                        char c = line.charAt(i + 10);
                        if (c == '2') api = "D3D12";
                        else if (c == '1') api = "D3D11";
                        else if (c == '0') api = "D3D10";
                    } else if (line.indexOf("Direct3D 9") >= 0) {
                        api = "D3D9";
                    }
                }
            }
            final String SEP = " · ";
            String result = api == null ? null
                    : api.equals("D3D12") ? "D3D12" + SEP + "VKD3D" : api + SEP + wrapper;
            lastEngineLogMtime = mtime; lastEngineLogLen = len; lastEngineLogApi = result;
            return result;
        } catch (Exception ignore) { return null; }
    }

    /**
     * The running game's install directory, normalized (forward slashes, lowercase) — e.g.
     * {@code e:/winlator/games/mortal sin}. Read from {@code /proc/<pid>/cmdline}, whose argv[0] is
     * the game's Windows exe path (wine sets it). Returns null if unreadable, so the caller falls
     * back safely. This is the token we require inside a Player.log before trusting it.
     */
    private String runningGameDirToken(String pid) {
        if (pid == null) return null;
        try (java.io.FileReader fr = new java.io.FileReader(new java.io.File("/proc/" + pid + "/cmdline"))) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = fr.read()) != -1 && c != 0) sb.append((char) c);   // argv[0] only (stop at first NUL)
            String norm = sb.toString().trim().replace('\\', '/').toLowerCase();
            int slash = norm.lastIndexOf('/');
            return slash > 0 ? norm.substring(0, slash) : null;            // strip the exe filename
        } catch (Exception ignore) { return null; }
    }

    /**
     * The newest {@code AppData/LocalLow/<company>/<product>/Player.log} that PROVABLY belongs to the
     * running game — i.e. whose header records the same install directory ({@code gameDir}) via
     * Unity's "Mono path[0] = 'E:/.../<Game>/<Game>_Data/Managed'" line. A stale log from a different
     * game (or a non-Unity title with no log) won't match, so the resolver falls back rather than
     * mislabeling. This is the hardening: correlate by identity, not by recency.
     */
    private File matchingPlayerLog(File localLow, String gameDir) {
        if (localLow == null || gameDir == null || !localLow.isDirectory()) return null;
        File[] companies = localLow.listFiles();
        if (companies == null) return null;
        File best = null;
        long bestMtime = Long.MIN_VALUE;
        for (File company : companies) {
            if (!company.isDirectory()) continue;
            File[] products = company.listFiles();
            if (products == null) continue;
            for (File product : products) {
                if (!product.isDirectory()) continue;
                File log = new File(product, "Player.log");
                if (log.isFile() && log.lastModified() > bestMtime && logBelongsToGame(log, gameDir)) {
                    bestMtime = log.lastModified();
                    best = log;
                }
            }
        }
        return best;
    }

    /** Whether {@code log}'s header names {@code gameDir} (Unity records the install path near the top). */
    private boolean logBelongsToGame(File log, String gameDir) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(log))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 60) {   // Mono path / data path live near the top
                if (line.indexOf("Mono path[0]") >= 0 || line.indexOf("data path") >= 0) {
                    if (line.replace('\\', '/').toLowerCase().indexOf(gameDir) >= 0) return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    /**
     * P2: ask the running game's ENGINE log which graphics device it actually created, at TOP LEVEL —
     * not only inside {@link #detectActiveDxApi}'s dual-API branch, which never fires on arm64ec Proton
     * (the DX DLLs aren't file-backed in /proc/maps, so the "both d3d11 and d3d12 mapped" trigger is
     * unreachable). Finds the running game pid and delegates to the existing Unity {@code Player.log}
     * resolver, which self-gates to Unity titles (correlating by install-dir token) and returns null for
     * everything else. Runs BEFORE the wrapper-log resolver (P3) so a Unity {@code -force-d3d11} title
     * that ALSO leaves a vkd3d capability-probe log is still pinned to D3D11 by its own engine log.
     * Never throws; any miss returns null and falls through.
     */
    private String resolveApiFromEngineLogTopLevel(String wrapper) {
        try {
            String pid = findRunningGamePid();
            if (pid == null) return null;
            return resolveDualApiFromEngineLog(wrapper, pid);
        } catch (Exception ignore) { return null; }
    }

    /**
     * P3: the arm64ec-proof ground truth. On arm64ec Proton the DX wrappers (d3d11/d3d12/dxgi) are
     * PE-only — no unix {@code .so} half — so they NEVER show up in /proc/&lt;pid&gt;/maps and the module
     * scan can't see the DX API. But the wrappers still WRITE their startup logs, and we force those logs
     * to always exist this launch (see {@link #dxvkLogDir()} + {@code DXVKConfigDialog.setEnvVars}). Read
     * them from {@link #wrapperLogDir}, gated by IDENTITY (the log must belong to the running game's exe)
     * and FRESHNESS ({@code lastModified() >= sessionStartMs}), and report the real API. Never throws; any
     * miss returns null and falls through to {@link #detectActiveDxApi}.
     *
     * <p>vkd3d-proton.log has a FIXED name shared across games, so its identity is proven strictly by its
     * header ({@code Program name: "<exe>"}). DXVK writes per-API files named after the exe
     * ({@code <stem>_d3d11.log} etc.), so matching that filename to the running exe IS the identity check.
     */
    private String resolveApiFromWrapperLogs(String wrapper) {
        try {
            File dir = wrapperLogDir;
            if (dir == null || !dir.isDirectory()) return null;

            // Fast path: the previously-resolved log is unchanged (static startup log) — reuse it. Safe
            // because exactly one game runs per activity, so the cached winner can't be another title.
            if (lastWrapperLogPath != null) {
                File prev = new File(lastWrapperLogPath);
                if (prev.isFile() && prev.lastModified() == lastWrapperLogMtime
                        && prev.length() == lastWrapperLogLen) return lastWrapperLogApi;
            }

            // wrapperLogDir is THIS game's per-launch log folder (its LogLocation dir, or the private
            // hudapi dir), so a log written THIS session already belongs to the running game — NO exe-name
            // gate. That gate broke two-process titles: the launcher exe found running (e.g. PlayGTAV.exe)
            // is NOT the renderer the wrapper logs are named after (e.g. GTA5_Enhanced.exe). Identity =
            // per-game folder + freshness. DXVK per-API files are matched by suffix (any renderer stem).
            File[] files = dir.listFiles();
            if (files == null) return null;
            // Two tiers per API: logs written by a process that could be the game, and logs written by a
            // known helper (store client / overlay proxy / launcher chain — HELPER_RENDERER_EXES). A helper
            // log is used only when no game-tier log of that API exists, so EA Desktop's CEF D3D11 log or
            // its overlay's vkd3d log can't outrank the game's own wrapper log.
            File vkd3d = null, dxvk11 = null, dxvk10 = null, dxvk9 = null;
            File hDxvk11 = null, hDxvk10 = null, hDxvk9 = null;
            String wantedStem = shortcutExeBasename();          // "<exe>" without .exe, lowercase, or null
            if (wantedStem != null && wantedStem.endsWith(".exe")) wantedStem = wantedStem.substring(0, wantedStem.length() - 4);
            for (File f : files) {
                if (!f.isFile()) continue;
                String n = f.getName().toLowerCase();
                if (n.equals("vkd3d-proton.log")) { vkd3d = f; continue; }
                boolean helper = isHelperRendererExe(dxvkLogStem(f));
                if (n.endsWith("_d3d11.log"))      { if (helper) hDxvk11 = newerLog(hDxvk11, f); else dxvk11 = newerLog(dxvk11, f); }
                else if (n.endsWith("_d3d10.log")) { if (helper) hDxvk10 = newerLog(hDxvk10, f); else dxvk10 = newerLog(dxvk10, f); }
                else if (n.endsWith("_d3d9.log"))  { if (helper) hDxvk9  = newerLog(hDxvk9, f);  else dxvk9  = newerLog(dxvk9, f); }
            }
            if (dxvk11 == null) dxvk11 = hDxvk11;
            if (dxvk10 == null) dxvk10 = hDxvk10;
            if (dxvk9 == null)  dxvk9  = hDxvk9;

            final String SEP = " · ";
            // 1) D3D12 on VKD3D — highest rank, but ONLY when vkd3d actually RENDERED (a swapchain /
            //    command queue), not merely PROBED D3D12 support at startup. Dual-API titles (e.g. Deus
            //    Ex: Mankind Divided) run on D3D11 yet still create a throwaway D3D12 device to query
            //    support — that probe log has instance/device/pipeline-cache lines but no swapchain, so it
            //    must NOT win over the game's real D3D11 (its large DXVK _d3d11.log, matched below).
            //    Identity gates on top: the log names its program — a helper process (EA Desktop, its
            //    IGOProxy overlay, Steam's overlay …) never labels the game; and when the shortcut's own
            //    exe has a fresh DXVK log, a vkd3d log naming a DIFFERENT program loses to it.
            if (isFreshWrapperLog(vkd3d) && vkd3dLogShowsRendering(vkd3d)) {
                String prog = vkd3dLogProgramName(vkd3d);
                boolean helperVkd3d = isHelperRendererExe(prog);
                boolean shortcutHasDxvk = wantedStem != null && (
                        (isFreshWrapperLog(dxvk11) && wantedStem.equals(dxvkLogStem(dxvk11))) ||
                        (isFreshWrapperLog(dxvk10) && wantedStem.equals(dxvkLogStem(dxvk10))) ||
                        (isFreshWrapperLog(dxvk9)  && wantedStem.equals(dxvkLogStem(dxvk9))));
                boolean vkd3dIsShortcut = prog != null && wantedStem != null
                        && prog.equals(wantedStem + ".exe");
                if (!helperVkd3d && (vkd3dIsShortcut || !shortcutHasDxvk)) {
                    return cacheWrapperResult(vkd3d, "D3D12" + SEP + "VKD3D");
                }
            }
            // 2) DXVK per-API files (D3D11/10/9) — the file exists only when DXVK created that device.
            if (isFreshWrapperLog(dxvk11)) return cacheWrapperResult(dxvk11, "D3D11" + SEP + wrapper);
            if (isFreshWrapperLog(dxvk10)) return cacheWrapperResult(dxvk10, "D3D10" + SEP + wrapper);
            if (isFreshWrapperLog(dxvk9))  return cacheWrapperResult(dxvk9,  "D3D9"  + SEP + wrapper);
            // dxgi-only / nothing identifying => fall through.
            return null;
        } catch (Exception ignore) { return null; }
    }

    /** The newer of two candidate logs (either may be null) — used when several DXVK per-API files match. */
    private File newerLog(File a, File b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.lastModified() >= a.lastModified() ? b : a;
    }

    /** Whether {@code log}'s header (first ~60 lines) has a line containing {@code needle} (lowercased).
     *  Confirms a fresh vkd3d-proton.log is a REAL run (has a "Program name" line) rather than an empty/
     *  aborted stub — without requiring it to name any specific exe (two-process games log the renderer,
     *  not the launcher we find running). */
    private boolean logHasHeaderLine(File log, String needle) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(log))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 60) {
                if (line.toLowerCase().indexOf(needle) >= 0) return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    /** Whether a vkd3d-proton.log shows the game ACTUALLY rendered D3D12 — created a swapchain / command
     *  queue / submitted command lists — versus merely PROBING D3D12 support at startup (a throwaway
     *  device: instance + device-caps + pipeline-cache lines, but never a swapchain). Dual-API titles
     *  (Deus Ex: Mankind Divided, many engines) run on D3D11 yet emit such a probe log, so this gate is
     *  what stops them being mislabelled "D3D12 · VKD3D". Scans the whole (small, KB-sized) log. */
    private boolean vkd3dLogShowsRendering(File log) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(log))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 4000) {
                String l = line.toLowerCase();
                if (l.indexOf("swapchain") >= 0 || l.indexOf("command_queue") >= 0
                        || l.indexOf("executecommandlists") >= 0) return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    /** A wrapper log is trusted only when it exists, has real content, and was written THIS session. */
    private boolean isFreshWrapperLog(File f) {
        return f != null && f.isFile() && f.length() > 0 && f.lastModified() >= sessionStartMs;
    }

    /** Remember the winning log's (path,mtime,len) so the next poll can skip re-parsing a static log. */
    private String cacheWrapperResult(File f, String result) {
        lastWrapperLogPath = f.getPath();
        lastWrapperLogMtime = f.lastModified();
        lastWrapperLogLen = f.length();
        lastWrapperLogApi = result;
        return result;
    }

    /** Whether {@code log}'s header (first ~60 lines) has a line containing {@code needle} that also names
     *  {@code exeBase} — the identity gate for the fixed-name vkd3d-proton.log. Both compared lowercase. */
    private boolean logHeaderNamesExe(File log, String needle, String exeBase) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(log))) {
            String line;
            int n = 0;
            while ((line = r.readLine()) != null && n++ < 60) {
                String l = line.toLowerCase();
                if (l.indexOf(needle) >= 0 && l.indexOf(exeBase) >= 0) return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    /**
     * PID of the running GAME process — the wine process whose argv[0] is the game's own {@code .exe},
     * as opposed to a wine system service (services.exe, explorer.exe, winedevice.exe, …) or a
     * C:\windows\system32 helper. Prefers the process whose exe basename matches the launched shortcut;
     * otherwise returns the first plausible game exe found. Null when none is running yet (caller keeps
     * polling). Never throws.
     */
    private String findRunningGamePid() {
        String wanted = shortcutExeBasename();     // preferred match; may be null

        // Reuse the last resolved pid while it still names the game. This turns the steady-state poll
        // into ONE /proc/<pid>/cmdline read instead of a full /proc walk.
        if (dxApiCachedPid != null) {
            String cachedExe = readArgv0Exe(dxApiCachedPid);
            if (cachedExe != null) {
                String cachedBase = cachedExe.substring(cachedExe.lastIndexOf('/') + 1);
                boolean stillGame = wanted != null
                        ? wanted.equals(cachedBase)
                        : looksLikeGameExe(cachedExe, cachedBase);
                if (stillGame) return dxApiCachedPid;
            }
            dxApiCachedPid = null;
        }

        java.io.File[] pids = new java.io.File("/proc").listFiles();
        if (pids == null) return null;
        String fallbackPid = null;
        for (java.io.File p : pids) {
            if (!p.isDirectory() || !android.text.TextUtils.isDigitsOnly(p.getName())) continue;
            String exe = readArgv0Exe(p.getName());
            if (exe == null) continue;
            String base = exe.substring(exe.lastIndexOf('/') + 1);
            if (wanted != null && wanted.equals(base)) {                         // exact shortcut match wins
                dxApiCachedPid = p.getName();
                return dxApiCachedPid;
            }
            if (fallbackPid == null && looksLikeGameExe(exe, base)) fallbackPid = p.getName();
        }
        dxApiCachedPid = fallbackPid;
        return fallbackPid;
    }

    /** argv[0] of /proc/&lt;pid&gt; as a normalized (forward-slash, lowercase) Windows path ending in
     *  {@code .exe}, or null if the process has no cmdline or isn't a wine {@code .exe}. */
    private String readArgv0Exe(String pid) {
        try (java.io.FileReader fr = new java.io.FileReader(new java.io.File("/proc/" + pid + "/cmdline"))) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = fr.read()) != -1 && c != 0) sb.append((char) c);   // argv[0] only (stop at first NUL)
            String norm = sb.toString().trim().replace('\\', '/').toLowerCase();
            return norm.endsWith(".exe") ? norm : null;
        } catch (Exception ignore) { return null; }
    }

    /** Whether {@code normPath}/{@code base} looks like a GAME exe rather than a wine system exe: it must
     *  sit on a DOS drive path ({@code <letter>:/…}) and be neither under a C:\windows dir nor a known
     *  wine-service basename. */
    private boolean looksLikeGameExe(String normPath, String base) {
        if (normPath.indexOf(":/") != 1) return false;                 // "c:/…" — drive letter, then ":/"
        if (normPath.indexOf("/windows/") >= 0) return false;          // system32/syswow64/etc. helpers
        return !WINE_SYSTEM_EXES.contains(base);
    }

    /** Basename (lowercased, with extension) of the launched shortcut's exe, or null. A preference hint
     *  for {@link #findRunningGamePid}; null just means "no preferred match", never an error. */
    private String shortcutExeBasename() {
        try {
            if (shortcut == null || shortcut.path == null) return null;
            String norm = shortcut.path.trim().replace('\\', '/').toLowerCase();
            int e = norm.indexOf(".exe");                 // shortcut.path may carry launch args after the exe
            if (e < 0) return null;
            String full = norm.substring(0, e + 4);
            return full.substring(full.lastIndexOf('/') + 1);
        } catch (Exception ignore) { return null; }
    }

    /**
     * Whether guest OpenGL is served by Mesa's Zink (GL-on-Vulkan) gallium driver. This build routes
     * the guest GL stack through Zink unconditionally (see {@link #extractGraphicsDriverFiles}, which
     * sets GALLIUM_DRIVER=zink for the wrapper ICD), so we read the real env we hand the guest rather
     * than hardcode "Zink" — if that routing is ever gated, the HUD label follows automatically.
     */
    private boolean guestGlIsZink() {
        return "zink".equals(envVars.get("GALLIUM_DRIVER"))
            || "zink".equals(envVars.get("MESA_LOADER_DRIVER_OVERRIDE"));
    }

    // ---- App-declared active graphics API (general opt-in override for the perf HUD) --------------
    // A guest Windows app can publish its TRUE active graphics API by writing a tiny JSON status file
    // into the container's SHARED tmp — guest-writable AND host-readable because both sides resolve to
    // the same dir:
    //   host  read path : <filesDir>/imagefs/usr/tmp/hud_active_api.json  (imageFs.getTmpDir())
    //   guest write path: Z:\\usr\\tmp\\hud_active_api.json  (Z: is symlinked to the imagefs root — see
    //                     WineUtils.createDosdevicesSymlinks — so it is prefix-independent, i.e. it
    //                     does NOT depend on drive_c / %TEMP% / the wine prefix layout).
    //   schema          : {"label":"D3D9 · DXVK","path":"d3d9 → DXVK → Turnip","ts":<epoch_ms>}
    //   freshness gate  : now - ts < APP_DECLARED_API_TTL_MS, else fall back to detectActiveDxApi.
    // Why it exists: a compositor-style app (AIO Graphics Test v2) renders every backend offscreen and
    // presents through ONE D3D11 swapchain, so /proc/maps module scanning would forever read "D3D11"
    // even while a Vulkan backend is exercised. The freshness gate keeps NORMAL games unaffected — they
    // never write the file, so detection stays exactly as before.
    private static final String APP_DECLARED_API_FILE = "hud_active_api.json";
    private static final long APP_DECLARED_API_TTL_MS = 2000L;

    /**
     * The app-declared active graphics API label, or null when there is no FRESH declaration (file
     * missing, stale, empty, or unparseable) — in which case the caller keeps the existing present-
     * path/DLL detection unchanged. GENERAL: any guest app that writes {@link #APP_DECLARED_API_FILE}
     * lights this up; it is NOT hardcoded to any one app. All IO/parse failures fall back to null
     * (never throws), so a partial/half-written file simply defers to normal detection.
     */
    private String readAppDeclaredApi() {
        try {
            File f = new File(imageFs.getTmpDir(), APP_DECLARED_API_FILE);
            if (!f.isFile()) return null;
            String content = FileUtils.readString(f);
            if (content == null || content.isEmpty()) return null;          // empty/partial write
            JSONObject json = new JSONObject(content);                        // partial JSON -> throws -> null
            long ts = json.optLong("ts", 0L);
            if (System.currentTimeMillis() - ts >= APP_DECLARED_API_TTL_MS) return null;   // stale -> fall back
            String label = json.optString("label", "").trim();
            return label.isEmpty() ? null : label;
        } catch (Exception ignore) {
            return null;                                                      // missing/garbage -> normal detection
        }
    }

    // Window the FPS counter self-healed onto for the GL/Zink present topology (see
    // driveHudFrameTick). Written and read only from the present/tick threads and kept as a plain
    // volatile int — never a shared collection — so the tick path never mutates the WM-owned
    // frameRatingWindowId / mesaDrvWindowIds off-thread and can't race changeFrameRatingVisibility().
    // Reset to -1 when the HUD unbinds.
    private volatile int glZinkHealedWindowId = -1;

    // Controller input pacing (HermesWriter.onGameFrame) needs the game's frame rate whether or not
    // a HUD is shown, so it keeps its own copy of the HUD's window choice: the same rules as
    // changeFrameRatingVisibility (bind to a _MESA_DRV render window, upgrade off a fallback, rebind
    // to another one or the focused application window on unmap) and as driveHudFrameTick's GL/Zink
    // self-heal, applied to these fields instead of the HUD's. Written by the WM callbacks; the
    // present threads only read the volatile ints. padFeedMesaDrvIds is touched by the WM thread only.
    private volatile int padFeedWindowId = -1;
    private volatile int padFeedHealedWindowId = -1;
    private final java.util.LinkedHashSet<Integer> padFeedMesaDrvIds = new java.util.LinkedHashSet<>();
    private long padFeedBindTryNs;                  // present threads; a lost race only delays a bind
    private volatile boolean padFeedWaylandGame;    // Wayland: the compositor names a game surface
    // The guest-side win-fg layer (X11 when Win-FG Native cannot run, or for training capture)
    // presents its generated frames to the X server like real ones: while its multiplier is N >= 2,
    // only one present in N is a real game frame. Set from writeWinFgConfig, that layer's only
    // config writer. LSFG Native and Win-FG Native generate inside our own renderer/compositor and
    // never reach these present callbacks, so they need nothing here.
    private volatile int padFeedPresentsPerFrame = 1;
    private int padFeedPresentPhase;                // present threads; a lost race shifts the phase only

    /** WM thread: keep the pad frame feed bound to the game's presenting window (see the fields). */
    private void trackPadFeedWindow(Window window, Property property) {
        if (property != null) {
            if (!property.nameAsString().contains("_MESA_DRV")) return;
            padFeedMesaDrvIds.add(window.id);
            int bound = padFeedWindowId;
            boolean boundToFallback = bound != -1 && !padFeedMesaDrvIds.contains(bound);
            if (bound == -1 || boundToFallback) padFeedWindowId = window.id;
            return;
        }
        padFeedMesaDrvIds.remove(window.id);
        if (padFeedWindowId == -1 || window.id != padFeedWindowId) return;
        padFeedHealedWindowId = -1;
        Integer next = padFeedMesaDrvIds.isEmpty() ? null : padFeedMesaDrvIds.iterator().next();
        if (next == null) {
            try {
                Window focused = xServer.windowManager.getFocusedWindow();
                if (focused != null && focused.id != window.id && focused.isApplicationWindow()) next = focused.id;
            } catch (Exception ignore) {}
        }
        padFeedWindowId = next != null ? next : -1;
    }

    /**
     * Present threads: report one frame of the game's window to the controller transport. A few
     * volatile reads on the hot path; WM state is only consulted while unbound (at most twice a
     * second, the live-HUD rule: the focused application window) or for the GL/Zink self-heal.
     */
    private void feedPadFrame(int wid) {
        int bound = padFeedWindowId;
        if (bound == -1) {
            long now = System.nanoTime();
            if (now - padFeedBindTryNs < 500_000_000L) return;
            padFeedBindTryNs = now;
            try {
                Window focused = xServer.windowManager.getFocusedWindow();
                if (focused == null || focused.id != wid || !focused.isApplicationWindow()
                        || focused.isDesktopWindow()) return;
            } catch (Exception e) {
                return;
            }
            padFeedWindowId = bound = wid;
        }
        if (wid != bound && wid != padFeedHealedWindowId) {
            if (!guestGlIsZink()) return;
            try {
                Window focused = xServer.windowManager.getFocusedWindow();
                boolean gameFocused = focused != null && focused.isApplicationWindow() && !focused.isDesktopWindow();
                Window w = xServer.windowManager.getWindow(wid);
                if (!gameFocused || (w != null && w.isDesktopWindow())) return;
            } catch (Exception e) {
                return;
            }
            padFeedHealedWindowId = wid;
        }
        int perFrame = padFeedPresentsPerFrame;
        if (perFrame > 1 && (++padFeedPresentPhase % perFrame) != 0) return;   // a generated frame
        com.winhub.hernandez.inputcontrols.HermesWriter.onGameFrame();
    }

    /**
     * Drive every perf-HUD overlay for one presented frame on window {@code wid}. Single entry point
     * for all present/tick paths (SHM copyArea content-update, Vulkan AHB copy, Vulkan native
     * scanout) so FPS counting behaves identically however a game presents.
     *
     * <p>Normally only the window the HUD is bound to counts — {@code frameRatingWindowId}, seeded from
     * the guest {@code _MESA_DRV} property. But under Zink (GL-on-Vulkan) that {@code _MESA_DRV} window
     * is frequently NOT the window actually presenting frames (it rides a render surface distinct from
     * the composited application window), so the strict id match never fires and the HUD reads 0.0 fps
     * while the game renders fine. D3D/DXVK/Vulkan titles keep {@code _MESA_DRV} on their render window,
     * so they always take the strict path and this heuristic stays inert for them.
     *
     * <p>Self-heal: when the presenting window is the focused, viewable, top-level application window,
     * count it too and remember it in {@link #glZinkHealedWindowId} so later frames take the fast path
     * without re-touching WM state off-thread. We deliberately do NOT reassign {@code frameRatingWindowId}
     * or {@code mesaDrvWindowIds} here (those are owned by the WM thread) — the volatile int is enough.
     */
    private void driveHudFrameTick(int wid) {
        feedPadFrame(wid);   // controller pacing follows the game's frames, HUD or not
        if (frameRatingWindowId == -1 || !hudCounterEnabled) return;   // HUD inactive or toggled off -> never count
        if (wid != frameRatingWindowId && wid != glZinkHealedWindowId) {
            if (!guestGlIsZink()) return;                      // only the GL/Zink present topology
            // Device-observed (Stronghold Crusader / Zink): the window the game actually presents to is
            // a CHILD GL render surface with no WM class — NOT a top-level application window — while the
            // HUD's _MESA_DRV binding sits on the top-level game window. So we must NOT require the
            // presenter itself to be an application window (an earlier version did, and threw the real
            // presenter away -> 0 fps). Instead: count the presenter as long as a real game application
            // window is currently FOCUSED (we're in a game, not sitting at the desktop shell) and the
            // presenter isn't itself the desktop shell. Follow whatever window is presenting.
            Window focused = xServer.windowManager.getFocusedWindow();
            boolean gameFocused = focused != null && focused.isApplicationWindow() && !focused.isDesktopWindow();
            Window w = xServer.windowManager.getWindow(wid);
            boolean presenterOk = (w == null) || !w.isDesktopWindow();
            if (!gameFocused || !presenterOk) return;
            Log.d("XServerDisplayActivity", "GL/Zink HUD self-heal: counting FPS on presenting window " + wid
                    + " (focused game " + focused.id + ", HUD bound to " + frameRatingWindowId + ")");
            glZinkHealedWindowId = wid;
        }
        fpsCounter.tick();   // the Fusion HUD refreshes itself, so there is nothing else to push
    }

    /**
     * Build the in-game perf HUD for the resolved config's style, seeding orientation, the master toggle,
     * and the renderer label, then kick off live D3D-API detection. Idempotent: a no-op once any HUD view
     * exists. Called both at launch (behind {@code container.isShowFPS()}) and live from the in-game drawer
     * when the user turns "Show HUD" on with FPS previously off — one place so the two paths can't drift.
     */
    private void ensureHudBuilt() {
        if (fusionHud != null) return;   // already built

        String fpsConfigString = resolvedFPSCounterConfig();
        com.winhub.hernandez.core.KeyValueSet fpsConfig = new com.winhub.hernandez.core.KeyValueSet(fpsConfigString);
        // Master toggle: the HUD is still BUILT (so it can be revealed live), but stays GONE while off.
        hudCounterEnabled = fpsConfig.get("hudEnabled", "1").equals("1");

        // Host renderer: Vulkan, or EGL when the container/game picked it. On Wayland the container's
        // renderer setting picks an X11 present path this session never runs: the game's frames land
        // in the embedded compositor, which is always Vulkan.
        String resolvedHostRenderer = resolvedRenderer();
        String rendererMode = waylandMode ? "Vulkan"
            : "egl".equals(resolvedHostRenderer) ? "EGL"
            : "surfacedirect".equals(resolvedHostRenderer) ? "SurfaceDirect" : "Vulkan";
        // The configured wrapper NAMES what a D3D game would load here — it is not evidence that THIS
        // game loads one: a native OpenGL or Vulkan title never touches DXVK. It stays the tag for a
        // D3D API the resolvers actually prove (startDxApiDetection's fallback arg) and, on X11, the
        // launch-time seed it has always been. On Wayland the label instead starts at the one thing
        // true of every session — the compositor's Vulkan — and upgrades to "D3D9 · DXVK" / "OpenGL"
        // when an evidence resolver sees the real API (see startDxApiDetection).
        String dxName = dxwrapper.contains("dxvk") ? "DXVK" : dxwrapper.contains("vegas") ? "VEGAS" : "WineD3D";
        hudEngineShort = waylandMode ? rendererMode : dxName;

        // The Fusion HUD is the only HUD. A config saved with an older hudStyle (classic, GameHub or
        // GameNative) simply gets Fusion.
        buildFusionHud(fpsConfigString);

        // The label above is the configured D3D9/10/11 wrapper; probe what the game actually loads and
        // upgrade it to the real API — "D3D12 · VKD3D" for D3D12 titles, "D3D11 · DXVK" etc. for the
        // wrapped path, or "Vulkan"/"Zink"/"OpenGL" for native-API games.
        startDxApiDetection(rendererMode, dxName);
    }

    /**
     * When the HUD is built LIVE (user flipped "Show HUD" on mid-game after launching with FPS off), the
     * game's {@code _MESA_DRV} property already fired while no HUD existed, so changeFrameRatingVisibility
     * dropped it (it early-returns when every HUD view is null) and {@code frameRatingWindowId} is still -1
     * — driveHudFrameTick would never count and the HUD would sit at 0 fps. Bind to the currently focused
     * game window so counting starts immediately; the normal _MESA_DRV bind/upgrade path in
     * changeFrameRatingVisibility takes over on the next real render-window event. Mirrors the focused-
     * application-window logic driveHudFrameTick already uses. frameRatingWindowId is a plain cross-thread
     * field by existing design; we write it on the UI thread like the rest of onFpsConfigApply.
     */
    private void ensureHudBoundToGameWindow() {
        if (frameRatingWindowId != -1) return;   // already bound (e.g. HUD was built at launch)
        try {
            Window focused = xServer.windowManager.getFocusedWindow();
            if (focused != null && focused.isApplicationWindow() && !focused.isDesktopWindow()) {
                frameRatingWindowId = focused.id;
                Log.d("XServerDisplayActivity", "Live HUD build: binding FPS counter to focused game window " + focused.id);
            }
        } catch (Exception ignore) {}
    }

    /**
     * Continuously track the active graphics API and keep EVERY HUD's label live, like the other
     * metrics. Runs on a background thread at a ~2s cadence (off the render path, so it never stalls a
     * frame), backing off to 15 s once the result is stable. We only push to the HUDs when the API
     * actually CHANGES — the label is near-static — and
     * {@link #detectActiveDxApi} early-exits its /proc/maps scan on the top-priority hit, so the cost
     * is negligible even on weak devices.
     *
     * <p>Crucially it does NOT latch on the first result: a game that maps opengl32.dll early (UE probes
     * GL before loading d3d12core.dll) used to freeze on "Zink"; now the label upgrades to the real API
     * ("D3D12 · VKD3D") as soon as it loads. It never downgrades, because closed API DLLs stay resident
     * (the multi-API AIO test therefore shows the highest API loaded — a known limit of module scanning).
     *
     * <p>Once a D3D API is proven the thread stops: that result is terminal (it can never downgrade),
     * and keeping the /proc/maps walk alive just burns CPU for the rest of the session. A native
     * (Vulkan/OpenGL/Zink) label is still allowed to upgrade, but after five identical polls the
     * cadence drops from 2 s to 15 s.
     */
    private void startDxApiDetection(final String rendererMode, final String fallback) {
        stopDxApiDetection();
        dxApiThread = new Thread(() -> {
            String lastApi = null;
            int stablePolls = 0;
            while (!Thread.currentThread().isInterrupted()) {
                // HUD off -> don't do the /proc/maps API scan or push a label; idle-sleep and re-check.
                // Keeps the thread alive so the label resumes when "Show HUD" is turned back on, and
                // matches driveHudFrameTick's hudCounterEnabled gate so NOTHING HUD-related runs while off.
                if (!hudCounterEnabled) {
                    try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                    continue;
                }
                // App-declared override wins ONLY while fresh: a guest app can publish its true active
                // API into the shared tmp (see readAppDeclaredApi). Otherwise fall back to the
                // /proc/maps present-path detection, so normal games (which never write the file) are
                // unaffected. When the declaration goes stale the label reverts naturally on the next
                // poll, because the detected api then differs from lastApi and re-pushes.
                // Layered resolver, highest-confidence signal first (each returns null to fall through):
                //   P1 guest self-report (AIO Graphics Test) · P2 engine log (Unity Player.log) ·
                //   P3 wrapper logs (arm64ec-proof DXVK/VKD3D ground truth) · P4 /proc/maps module scan.
                String api = readAppDeclaredApi();                                  // P1
                if (api == null) api = resolveApiFromEngineLogTopLevel(fallback);   // P2
                if (api == null) api = resolveApiFromWrapperLogs(fallback);         // P3
                if (api == null && waylandMode) {
                    // P4 on Wayland: the game's OWN process says whether it is native Vulkan or
                    // native OpenGL. detectActiveDxApi's native branch can't answer it here — it ORs
                    // its flags over every wine process and reasons about the X11 topology — so only
                    // its D3D verdict (file-backed DX DLLs, i.e. a non-arm64ec layer) is still worth
                    // taking. No evidence => no api => the neutral compositor label stands.
                    api = resolveWaylandNativeApi();
                    if (api == null) {
                        String dx = detectActiveDxApi(fallback);
                        if (dx != null && dx.startsWith("D3D")) api = dx;
                    }
                } else if (api == null) api = detectActiveDxApi(fallback);          // P4
                if (api != null && api.equals(lastApi)) stablePolls++;
                else stablePolls = 0;
                if (api != null && !api.equals(lastApi)) {
                    lastApi = api;
                    final String apiFinal = api;
                    runOnUiThread(() -> {
                        hudEngineShort = apiFinal;
                        if (fusionHud != null) fusionHud.setEngineLabel(apiFinal);
                    });
                }
                // A proven D3D API is terminal — stop walking /proc for the rest of the session.
                if (api != null && api.startsWith("D3D")) return;
                long delayMs = (api != null && stablePolls >= 5) ? 15000L : 2000L;
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { return; }
            }
        }, "dx-api-detect");
        dxApiThread.start();
    }

    private void stopDxApiDetection() {
        if (dxApiThread != null) { dxApiThread.interrupt(); dxApiThread = null; }
    }

    private boolean isDarkMode;

    private GuestProgramLauncherComponent guestProgramLauncherComponent;
    // Experimental Wayland display path: when true, run the game through winewayland.drv into
    // our embedded compositor instead of the X11 server. All branches are guarded by this flag,
    // so the X11 path is unchanged when it's false. See WAYLAND_RUNTIME.md.
    private boolean waylandMode = false;
    // HUD metric sampling for wayland mode (see startWaylandCompositor): its own thread, never the
    // compositor's. update() self-throttles to 500 ms and posts the view refresh to the UI thread.
    private android.os.HandlerThread waylandHudThread;
    private volatile android.os.Handler waylandHudSampler;
    private final java.util.concurrent.atomic.AtomicBoolean waylandHudSampleQueued = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Runnable waylandHudSample = () -> {
        waylandHudSampleQueued.set(false);
        if (frameRatingWindowId == -1 || !hudCounterEnabled) return;
        if (frameRating != null) frameRating.update();
        if (frameRatingHorizontal != null) frameRatingHorizontal.update();
        if (perfHud != null) perfHud.update();
    };
    private android.view.SurfaceView waylandSurfaceView;
    private android.widget.ImageView waylandCursorView;
    // Wayland mode: Android clipboard <-> guest selection, and the soft keyboard's IME text.
    private com.winhub.hernandez.wayland.WaylandClipboardSync waylandClipboard;
    private com.winhub.hernandez.wayland.WaylandTextInput waylandTextInput;
    private float waylandCursorX = -1f, waylandCursorY = -1f; // touchpad cursor position (view px)
    // The Wayland pointer is an app-drawn overlay (the compositor never draws one), so nothing was
    // deciding when it should go away: any injected motion made it visible and only a pointer lock
    // ever hid it again. It now hides on its own when idle, and stays hidden while a controller is
    // the thing driving - physical pad or on-screen controls - because then the pointer is not what
    // the player is looking at.
    private static final long WAYLAND_CURSOR_IDLE_MS = 2500L;   // no pointer motion -> hide
    private static final long WAYLAND_CURSOR_PAD_MS  = 1200L;   // recent pad input -> keep hidden
    /** Session-scoped: on-screen controls live in InputControlsView, which has no activity handle. */
    public static volatile long waylandLastPadInputMs = 0L;
    private final android.os.Handler waylandCursorIdle =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable waylandCursorHideRunnable = () -> {
        if (waylandCursorView != null && waylandCursorView.getVisibility() != View.GONE)
            waylandCursorView.setVisibility(View.GONE);
    };

    // The guest's own pointer, from wl_pointer.set_cursor. Before this the app drew a fixed arrow and
    // had no idea what the game wanted - X11 always knew, because its X server owns the cursor.
    private final int[] waylandCursorBuf =
            new int[com.winhub.hernandez.wayland.WaylandCompositor.CURSOR_BUF_INTS];
    private int waylandCursorSerial = -1;        // last snapshot applied
    private boolean waylandGuestHidesCursor;     // the guest asked for NO pointer (mouse-look)
    private int waylandCursorHotX, waylandCursorHotY;

    /** Pull the guest's cursor if it changed. Cheap: usually just reads a serial. */
    private void waylandSyncGuestCursor() {
        if (waylandCursorView == null) return;
        int n = com.winhub.hernandez.wayland.WaylandCompositor.cursorSnapshot(waylandCursorBuf);
        if (n < 6) return;
        int serial = waylandCursorBuf[0];
        // Serial 0 = the guest has not called set_cursor yet. That is NOT "hide": it just means we
        // know nothing, so the idle/controller rules stay in charge and the fallback arrow is used.
        if (serial == 0 || serial == waylandCursorSerial) return;
        waylandCursorSerial = serial;
        waylandGuestHidesCursor = waylandCursorBuf[1] != 0;
        if (waylandGuestHidesCursor) return;
        int w = waylandCursorBuf[2], h = waylandCursorBuf[3];
        if (w <= 0 || h <= 0 || n < 6 + w * h) return;
        waylandCursorHotX = waylandCursorBuf[4];
        waylandCursorHotY = waylandCursorBuf[5];
        try {
            // wl_shm ARGB8888 is premultiplied; Android treats these ints as straight alpha. Hard-edged
            // cursors are unaffected; only soft shadows would differ slightly.
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                    waylandCursorBuf, 6, w, w, h, android.graphics.Bitmap.Config.ARGB_8888);
            waylandCursorView.setImageBitmap(bmp);
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "wayland: cursor bitmap failed", e);
        }
    }

    /** A controller just produced input: hide the pointer now and keep it hidden while it keeps coming. */
    public static void waylandNotePadInput() {
        waylandLastPadInputMs = android.os.SystemClock.uptimeMillis();
    }

    /**
     * Pointer motion happened. Show the overlay and re-arm the idle hide - unless a controller is
     * driving, in which case keep it hidden. Main thread only.
     */
    private void waylandCursorPoke() {
        if (waylandCursorView == null) return;
        waylandSyncGuestCursor();
        waylandCursorIdle.removeCallbacks(waylandCursorHideRunnable);
        boolean padDriving =
                android.os.SystemClock.uptimeMillis() - waylandLastPadInputMs < WAYLAND_CURSOR_PAD_MS;
        // The guest asking for no pointer is authoritative - it is what X11 always had and Wayland
        // never did. The idle/controller rules below are only a fallback for when it wants one.
        if (waylandGuestHidesCursor || padDriving || waylandPointerLocked) {
            if (waylandCursorView.getVisibility() != View.GONE)
                waylandCursorView.setVisibility(View.GONE);
            return;
        }
        if (waylandCursorView.getVisibility() != View.VISIBLE)
            waylandCursorView.setVisibility(View.VISIBLE);
        waylandCursorIdle.postDelayed(waylandCursorHideRunnable, WAYLAND_CURSOR_IDLE_MS);
    }
    private volatile boolean waylandPointerLocked; // a program holds a pointer lock in the compositor
    private EnvVars overrideEnvVars;

    private void createNotifcationChannel() {
        String name = "Winlator";
        String description = "Winlator XServer Messages";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The activity is sensorLandscape and a portrait-resolution container forces portrait, so the
        // rotation really does flip under us at runtime — re-read it for the orientation remap.
        refreshCachedDisplayRotation();
        // A move between displays arrives here now that colorMode|touchscreen|uiMode are in the
        // manifest's configChanges (before that it recreated the activity and killed the guest).
        checkSessionDisplay("configuration changed");
        if (configChangedCallback != null) {
            configChangedCallback.run();
            configChangedCallback = null;
        }
    }

    /**
     * The framework's own "this activity moved to another display" callback. It is a hidden Activity
     * method, so there is no {@code @Override} to hang here and no compile-time guarantee it is
     * called — the real work is in {@link #checkSessionDisplay(String)}, which
     * {@link #onConfigurationChanged} and the display listener also drive. This is only the earliest,
     * most direct notice of the move when the platform does dispatch it.
     */
    public void onMovedToDisplay(int displayId, Configuration config) {
        checkSessionDisplay("moved to display " + displayId);
    }

    /**
     * Is this session's window really on the external display it was launched on?
     *
     * <p>The one question every TV decision asks — the screen-size override, the output mode, the
     * unplug watch. Deliberately NOT "was a TV asked for": {@code ActivityOptions.setLaunchDisplayId}
     * is a request the system may decline without throwing, and a declined session runs on the handheld
     * while the intent extra still names the TV. Derived from {@link #sessionDisplayId} rather than
     * cached, so it cannot go stale when the window moves.
     */
    private boolean onTvLaunchDisplay() {
        return tvLaunchDisplayId >= 0 && sessionDisplayId == tvLaunchDisplayId;
    }

    /**
     * Re-read the display this session's window is on and, when it has left the screen it was launched
     * on, pause the game rather than let it run on (or die on) a screen that is gone.
     *
     * <p>Android moves the task back to the default display by itself when an external display is
     * unplugged — we deliberately do NOT move it, we only notice and pause. Only a session whose window
     * really WAS on the TV can lose one, so neither an ordinary handheld launch nor a declined TV launch
     * ever trips this.
     */
    private void checkSessionDisplay(String why) {
        int now;
        try {
            now = com.winhub.hernandez.display.ExternalDisplay.currentDisplayId(this);
        } catch (Throwable t) {
            return;
        }
        if (now == sessionDisplayId) return;
        int from = sessionDisplayId;
        // Asked BEFORE sessionDisplayId moves on: this is "the window was on the TV until a moment ago".
        boolean wasOnTvLaunchDisplay = onTvLaunchDisplay();
        sessionDisplayId = now;
        Log.i("XServerDisplayActivity", "TV: session moved from display " + from + " to " + now + " [" + why + "]");
        // The compositor's HDR gate is decided from the display the window is on, so the new screen's
        // capability has to be re-read (and pushed to the compositor) before anything else looks at it.
        reportHdrCapability("session moved to display " + now);
        if (wasOnTvLaunchDisplay) onTvDisconnected();
    }

    /**
     * The TV this session was launched on is gone (unplugged, or the system moved us off it). Pause the
     * game and say so; the user resumes from the drawer once they are looking at the handheld.
     *
     * <p>Deliberately posted rather than run inline: the move can be bracketed by a transient
     * pause/resume of the activity, and {@link #onResume} unconditionally clears a pause
     * ("if (isPaused) setPausedState(false)"), which would undo this the moment it landed. Settling
     * first, then pausing, makes the pause the last word. Runs once per session.
     */
    private void onTvDisconnected() {
        if (tvDisconnectHandled) return;
        tvDisconnectHandled = true;
        // Straight away, not on the delay below: the game is on its way back to the handheld and the
        // companion is a task on that same screen — left up, it would sit in front of the game it is
        // still telling the user to watch on the TV.
        com.winhub.hernandez.display.TvCompanionActivity.dismiss("the session left the external display");
        // Own handler: the field one is only built partway through onCreate, and a display can go away
        // before that.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            setPausedState(true);
            XServerDialogState.INSTANCE.showInfoToast(
                    "TV DISCONNECTED", "paused",
                    "The game is paused. Resume from the drawer.");
        }, 500);
    }

    /**
     * Publish the panel's real refresh rates through RandR so Wine can offer them to games.
     *
     * <p>Wine builds its display-mode list from what our X server reports; with no RandR at all it
     * used its "NoRes" fallback, which hardcodes a single 60 Hz mode — the reason every in-game
     * refresh dropdown was stuck at 60 on a high-refresh panel. Rates are de-duplicated after
     * rounding (panels commonly report 59.95/60.0 as separate modes, which would otherwise show up
     * as two identical "60 Hz" entries) and sorted highest-first.
     */
    private void advertisePanelRefreshRates() {
        RandrExtension randr = xServer.getExtension(RandrExtension.MAJOR_OPCODE);
        if (randr == null) return;

        android.view.Display display = getWindowManager().getDefaultDisplay();
        android.view.Display.Mode[] modes = display.getSupportedModes();
        if (modes == null || modes.length == 0) return;

        // 0 = no cap. A cap below every supported rate would leave an empty list, so the lowest
        // supported rate is always kept — a game with no modes at all is worse than one capped
        // slightly higher than asked.
        final int cap = resolvedMaxGameRefreshRate();

        java.util.TreeSet<Short> distinct = new java.util.TreeSet<>(java.util.Collections.reverseOrder());
        short lowest = Short.MAX_VALUE;
        for (android.view.Display.Mode mode : modes) {
            short hz = (short)Math.round(mode.getRefreshRate());
            if (hz <= 0) continue;
            if (hz < lowest) lowest = hz;
            if (cap <= 0 || hz <= cap) distinct.add(hz);
        }
        if (distinct.isEmpty() && lowest != Short.MAX_VALUE) distinct.add(lowest);
        if (distinct.isEmpty()) return;

        short[] rates = new short[distinct.size()];
        int i = 0;
        for (short hz : distinct) rates[i++] = hz;
        randr.setRefreshRates(rates);

        Log.d("XServerDisplayActivity", "RandR advertising refresh rates " + java.util.Arrays.toString(rates));
    }

    private float pickHighestRefreshRate() {
    	android.view.Display display = getWindowManager().getDefaultDisplay();
    	android.view.Display.Mode[] modes = display.getSupportedModes();
    	
    	float maxRefresh = 0f;
    	
    	for (android.view.Display.Mode mode : modes) {
			if (mode.getRefreshRate() > maxRefresh)
    	    	maxRefresh = mode.getRefreshRate();
    	}

    	Log.d("XServerDisplayActivity", "Picking refresh rate " + maxRefresh);

        return maxRefresh;
    }

    protected void showGuestKeyboard() {
        AppUtils.showKeyboard(this);
        // Wayland: a keyboard the user toggled is theirs; text input won't auto-hide it.
        if (waylandTextInput != null) waylandTextInput.onUserToggledKeyboard();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A cold start straight into the game (home-screen pinned shortcut, ACTION_VIEW, adb) never ran
        // MainActivity, so the theme/prefs singleton was uninitialised → default theme in the in-game UI.
        try { com.winhub.hernandez.ui.theme.AppThemeState.INSTANCE.init(this); } catch (Throwable ignored) {}
        // Stamp the launch time up front: the HUD wrapper-log resolver (P3) only trusts a DXVK/VKD3D
        // log written THIS session, so a previous game's stale log in a shared dir can never leak in.
        sessionStartMs = System.currentTimeMillis();
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);
               
        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = pickHighestRefreshRate();
        getWindow().setAttributes(params);
        
        setContentView(R.layout.xserver_display_activity);
        com.winhub.hernandez.ui.PreloaderOverlayHelper.attach(this);

        preloaderDialog = new PreloaderDialog(this);
        // Route the failure card's buttons back to this activity (cleared in onDestroy).
        com.winhub.hernandez.core.PreloaderState.setOnClose(() -> runOnUiThread(this::finish));
        com.winhub.hernandez.core.PreloaderState.setOnOpenLog(() -> runOnUiThread(this::openLogFolder));
        com.winhub.hernandez.core.PreloaderState.setOnCancel(() -> runOnUiThread(this::exit));
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        cursorLock = preferences.getBoolean("cursor_lock", false);

        // Check for Dark Mode
        isDarkMode = preferences.getBoolean("dark_mode", false);

        boolean isOpenWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean isShareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);



        // Check if xinputDisabled extra is passed
        boolean xinputDisabledFromShortcut = false;




        // Initialize SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            // Orientation mode's sensor, resolved once: game rotation vector first (no magnetometer),
            // plain rotation vector as the fallback, null when the device has neither.
            gyroRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            if (gyroRotationSensor == null)
                gyroRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        Log.i("XServerGyro", "Gyroscope sensor " + (gyroSensor != null ? "found: " + gyroSensor.getName() : "not available"));
        Log.i("XServerGyro", "Rotation-vector sensor " + (gyroRotationSensor != null ? "found: " + gyroRotationSensor.getName() : "not available"));
        // Seed the cached rotation the orientation remap reads (see refreshCachedDisplayRotation).
        refreshCachedDisplayRotation();
        // NOTE: the listener is NOT registered here — the container (and so the gyro config) isn't
        // known yet at this point. See the applyGyroTuning block below, which registers it once the
        // resolved config is in. onResume re-registers, so nothing is lost on the way back in.

        // Record the start time
        startTime = System.currentTimeMillis();

        // Initialize handler for periodic saving
        handler = new Handler(Looper.getMainLooper());
        savePlaytimeRunnable = new Runnable() {
            @Override
            public void run() {
                savePlaytimeData();
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        };
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);


        // Handler and Runnable to manage timeout for hiding controls

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", true);

        hideControlsRunnable = () -> {
            if (isTimeoutEnabled) {
                inputControlsView.releaseActiveControls();
                inputControlsView.setVisibility(View.GONE);
                Log.d("XServerDisplayActivity", "Touchscreen controls hidden after timeout.");
            }
        };


        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        drawerLayout = findViewById(R.id.DrawerLayout);
        // No gray slab behind the drawer: DrawerLayout dims the whole content area with its
        // default scrim (0x99000000) while a drawer is open, which reads as a huge gray
        // rectangle behind the panel. The drawer carries itself — the game stays visible.
        drawerLayout.setScrimColor(android.graphics.Color.TRANSPARENT);

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override public void onDrawerOpened(@NonNull View drawerView) {
                // Hide the on-handheld "playing on external display" badge while the menu is open so
                // it doesn't overlap the drawer content.
                XServerDialogState.INSTANCE.setMenuOpen(true);
                // Take view focus so the pad / keyboard drive the menu (see dispatchKeyEvent).
                drawerView.requestFocus();
                // Menu owns the controller while open — flush a neutral state
                // once so a held stick / pressed button / latched trigger from
                // the last frame doesn't stay applied in the guest.
                if (winHandler != null) {
                    winHandler.neutralizeControllers();
                }
            }
            @Override public void onDrawerClosed(@NonNull View drawerView) {
                XServerDialogState.INSTANCE.setMenuOpen(false);
                // A controller change waiting to reconnect goes now rather than after its batching delay.
                if (liveControllerSession != null) liveControllerSession.onMenuClosed();
                // If the user left Relative Mouse enabled, recapture.
                if (isRelativeMouseMovement && !pointerCaptureRequested) {
                    drawerLayout.postDelayed(() -> ensurePointerCapture("drawer-closed"), 2000);
                }
            }
        });
        
        drawerLayout.setOnApplyWindowInsetsListener((view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        // Wire Compose in-game drawer
        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false) || preferences.getBoolean("enable_box64_logs", false);
        boolean allowMagnifier = !XrActivity.isEnabled(this);
        XServerDrawerState state = XServerDrawerState.INSTANCE;
        state.reset();
        state.setShowLogs(enableLogs);
        state.setShowMagnifier(allowMagnifier);
        state.setIsPaused(isPaused);
        state.setIsRelativeMouseMovement(isRelativeMouseMovement);
        state.setIsMouseDisabled(isMouseDisabled);
        state.setMoveCursorToTouchpoint(preferences.getBoolean("move_cursor_to_touchpoint", false));
        state.onClose                  = () -> runOnUiThread(() -> drawerLayout.closeDrawers());
        state.onKeyboard               = this::showGuestKeyboard;
        state.onInputControls          = () -> showInputControlsDialog();
        state.onGraphicEngine          = () -> { XServerDrawerState.INSTANCE.selectTab(com.winhub.hernandez.ui.TabType.GRAPHICS); runOnUiThread(() -> drawerLayout.openDrawer(GravityCompat.END)); };
        state.onVibration              = () -> showVibrationDialog();
        state.onOverlayOpacityChange   = () -> {
            float v = XServerDrawerState.INSTANCE.getOverlayOpacityValue();
            if (inputControlsView != null) inputControlsView.setOverlayOpacity(v); // setter invalidates → live redraw
            preferences.edit().putFloat("overlay_opacity", v).apply();
        };
        // Swipeable OSC (Stage 2): the drawer's Swipe tab toggles → apply live to the overlay + persist.
        state.onSetSwipeButtons        = (v) -> {
            if (inputControlsView != null) inputControlsView.setSwipeButtonsEnabled(v);
            preferences.edit().putBoolean("touchscreen_swipe_buttons_enabled", v).apply();
        };
        state.onSetSwipeDpad           = (v) -> {
            if (inputControlsView != null) inputControlsView.setSwipeDpadEnabled(v);
            preferences.edit().putBoolean("touchscreen_swipe_dpad_enabled", v).apply();
        };
        state.onSetSwipeSticks         = (v) -> {
            if (inputControlsView != null) inputControlsView.setSwipeSticksEnabled(v);
            preferences.edit().putBoolean("touchscreen_swipe_sticks_enabled", v).apply();
        };
        state.onControlsColorChange    = () -> {
            // Per-profile on-screen controls accent. Write the two drawer values onto the ACTIVE
            // profile (the one bound to the running game), persist, and invalidate for a live redraw.
            // Toggling Follow-theme back ON also invalidates so the controls return to the theme accent.
            ControlsProfile profile = inputControlsView != null ? inputControlsView.getProfile() : null;
            if (profile != null) {
                profile.setCustomAccentEnabled(!XServerDrawerState.INSTANCE.getControlsFollowThemeValue());
                profile.setCustomAccentColor(XServerDrawerState.INSTANCE.getControlsAccentColorValue());
                profile.save();
            }
            if (inputControlsView != null) inputControlsView.invalidate();
        };
        state.onNativeRenderingToggle   = () -> {
            boolean next = !XServerDrawerState.INSTANCE.getNativeRenderingEnabled();
            XServerDrawerState.INSTANCE.setNativeRenderingEnabled(next);
            // Native (direct scanout) puts one opaque game SurfaceControl on top; secondary guest
            // windows composite UNDER it and go invisible. It's a single-fullscreen-window mode, so
            // warn (don't block — the count can be transiently >1 during splash/child popups) when
            // the user enables it with more than one mapped application window on screen.
            if (next && countMappedAppWindows() > 1)
                showToast(this, "Native Rendering is best with a single fullscreen window — extra windows may be hidden");
            // Actually drive the renderer (this was previously only flipping the UI flag, so the
            // toggle had no effect and no "Native Rendering+ Enabled" toast). Native (direct
            // scanout) only exists on the Vulkan renderer.
            HostRenderer r = xServerView.getRenderer();
            if (r instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer) {
                com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr =
                    (com.winhub.hernandez.renderer.vulkan.VulkanRenderer) r;
                vkr.setNativeMode(next);
                // Direction B: native (direct scanout) bypasses the compositor post pass, where ALL
                // the Vulkan presets live. Turning native ON resets every preset so the drawer is
                // truthful (no toggles left "on" doing nothing). Only sets renderer + StateFlows —
                // never invokes the preset apply callbacks, so there's no feedback loop.
                if (next) resetVulkanPresets(vkr);
            }
        };
        // bionic-fg live controls (frame gen multiplier/flow + fps limiter). Each in-menu slider
        // updates the drawer StateFlows then fires this; we rewrite conf.toml (hot-reloads in the
        // layer) and persist to the container. Only effective when the layer is loaded this session
        // (bionicFgActive). NOTE: initial drawer state is synced after `container` is loaded (below);
        // this callback is lazy so it safely captures the field.
        // Auto bg/fg pulse to reset win-fg cleanly on an FG toggle-on / model change.
        state.onFgResetPulse = () -> runOnUiThread(this::pulseFgReset);
        state.onBionicFgConfigChange = () -> {
            XServerDrawerState s = XServerDrawerState.INSTANCE;
            if (!s.getBionicFgActive().getValue()) return; // layer not loaded -> needs a relaunch
            boolean fgOn   = s.getFrameGenEnabled().getValue();
            int   mult     = fgOn ? s.getFrameGenMultiplier().getValue() : 0;
            float flow     = s.getFrameGenFlowScale().getValue();
            // Native frame gen that can't run here stays Off, with the reason. The drawer greys
            // the buttons out too; this covers a tap that landed before the notice did.
            String fgProblem = s.getFgUnavailableReason().getValue();
            if (mult >= 2 && !fgProblem.isEmpty() && nativeFrameGenEngine()) {
                s.setFrameGenMultiplier(0);
                Toast.makeText(this, fgProblem, Toast.LENGTH_LONG).show();
                return;
            }
            // Wayland: both native engines run inside the Wayland compositor (framegen_bridge.c),
            // so a level/flow/model change is one JNI call - no conf.toml, no layer, no present-mode
            // override (the compositor is always FIFO) and no presentation reset. Same persistence
            // and limiter re-evaluation as the X11 branches below.
            if (waylandMode) {
                int wModel = s.getFrameGenModel().getValue();
                int wPreset = s.getFrameGenPerfPreset().getValue();
                Log.i("XServerDisplayActivity", "wayland framegen toggle: engine=" + resolvedFrameGenEngine()
                    + " fgOn=" + fgOn + " mult=" + mult + " flow=" + flow + " model=" + wModel);
                applyWaylandFrameGen(mult, flow, wModel, wPreset);
                if (fgOn) container.setFrameGenMultiplier(mult);
                container.setFrameGenFlowScale(flow);
                if (!resolvedFrameGenEngine().equals("lsfg-native")) {
                    container.setFrameGenModel(wModel);
                    container.setFrameGenPerfPreset(wPreset);
                }
                container.saveData();
                // The limiter guard and the exact-fit slack still have to see the >=2 crossing.
                reapplyFpsLimit();
                return;
            }
            // Route the single in-game multiplier/flow control to whichever engine is running this
            // session (honors a per-game engine override, else the container's engine).
            if (resolvedFrameGenEngine().equals("lsfg-native")) {
                // Instrumented deliberately. The r3 run showed the drawer UI accepting a
                // multiplier change while the engine kept planning at the old one, and the
                // log could not say whether this handler ran, what it saw, or whether it
                // reached the renderer - so it now says all three.
                Log.i("XServerDisplayActivity", "lsfg-native toggle: fgOn=" + fgOn
                    + " mult=" + mult + " flow=" + flow
                    + " renderer=" + (vulkanRendererOrNull() != null ? "vulkan" : "NULL"));
                // LSFG Native: the generator is OUR compositor, so a level change is just a call.
                // None of the lsfg-vk workarounds apply and every one of them is deliberately
                // skipped here - no conf.toml rewrite, no vsync clock, no MAILBOX override and no
                // pause/teardown/Resume reset. There is no guest layer to re-sync with.
                applyLsfgNative(mult, flow);
                if (fgOn) container.setFrameGenMultiplier(mult);
                container.setFrameGenFlowScale(flow);
                if (com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED) {
                    // The experimental knob rides the same live path and persists with it.
                    container.setFgCaptureResolution(s.getFgCaptureResolution().getValue());
                }
                container.saveData();
                // The limiter guard still has to see the >=2 threshold crossing.
                reapplyFpsLimit();
                return;
            }
            if (resolvedFrameGenEngine().equals("lsfg")) {
                // lsfg-vk: rewrite its conf.toml — the fork layer watches the file mtime and reloads
                // live (swapchain recreate). Passthrough = multiplier 1 (layer treats <=1 as off).
                File dll = new File(getFilesDir(), "lsfg-vk/Lossless.dll");
                // Live performance_mode: seeded from the container at launch (below), toggled live from
                // the FG drawer. Rewriting conf.toml with it bumps the mtime so the layer re-reads.
                boolean perfMode = s.getLsfgPerformanceMode().getValue();
                // mult is already 0 when the in-game toggle is Off (or FG disabled). lsfg-vk treats
                // multiplier <= 1 as passthrough, so map anything below 2 to 1 — NOT max(2,mult),
                // which would force 2x on Off.
                writeLsfgConfig(mult >= 2 ? mult : 1, flow, dll.getAbsolutePath(), perfMode);
                // Keep the vsync clock running only while frame-gen is actually generating (mult>=2),
                // so the layer has a grid to phase-lock to; stop it in passthrough.
                if (mult >= 2) startVsyncClock(); else stopVsyncClock();
                if (fgOn) container.setFrameGenMultiplier(mult);
                container.setFrameGenFlowScale(flow);
                container.setLsfgPerformanceMode(perfMode);
                container.saveData();
                // lsfg multiplier may have crossed the >=2 threshold -> re-evaluate the limiter
                // guard (lsfgGovernsFps) so the cap steps aside / resumes without extra user action.
                reapplyFpsLimit();
                // ... and re-apply the host present mode: mailbox while multiplying (so the generated
                // frames aren't strangled by FIFO backpressure), back to the user's mode when off.
                applyEffectivePresentMode();
                // Frame-gen change (Off/On/2×/3×/4×) → full presentation reset. lsfg-vk restarts on the
                // conf.toml rewrite above, but a swapchain-only recreate leaves it over-queued → generated
                // frames present BLACK until a background/foreground cycle. Deterministically replicate
                // that cycle: pause the guest, tear the surface fully down, and prompt Resume. Fires once
                // per effective-level change (flow/perf-mode edits keep the level, so they don't reset).
                maybeTriggerFgReset(mult >= 2 ? mult : 0);
                return;
            }
            // FPS limiter is no longer part of frame gen — it's a standalone host pacer
            // (onFpsLimitChange). bionic-fg conf carries frame gen only; pass the limiter off.
            int fgModel = s.getFrameGenModel().getValue();
            int fgPreset = s.getFrameGenPerfPreset().getValue();
            if (winFgNativeSession) {
                // Win-FG Native: same shape as the LSFG Native branch above. The
                // generator is our compositor, so a level/model/preset change is a
                // call - no conf.toml, no layer reset, no presentation teardown.
                Log.i("XServerDisplayActivity", "win-fg native toggle: fgOn=" + fgOn
                    + " mult=" + mult + " flow=" + flow + " model=" + fgModel
                    + " preset=" + fgPreset
                    + " renderer=" + (vulkanRendererOrNull() != null ? "vulkan" : "NULL"));
                applyWinFgNative(mult, flow, fgModel, fgPreset);
                if (fgOn) container.setFrameGenMultiplier(mult);
                container.setFrameGenFlowScale(flow);
                container.setFrameGenModel(fgModel);
                container.setFrameGenPerfPreset(fgPreset);
                container.saveData();
                reapplyFpsLimit();
                return;
            }
            writeWinFgConfig(mult, flow, false, 0, fgModel, fgPreset);
            if (fgOn) container.setFrameGenMultiplier(mult);
            container.setFrameGenFlowScale(flow);
            container.setFrameGenModel(fgModel);
            container.setFrameGenPerfPreset(fgPreset);
            container.saveData();
            // Same present-mode override as lsfg: bionic-fg inserts extra presents too, so force
            // mailbox while multiplying (FIFO backpressure would strangle the generated frames).
            applyEffectivePresentMode();
            // win-fg: mirror lsfg's full presentation reset (pause guest + real surface teardown +
            // on-screen Resume prompt + VRR release/re-vote) on a frame-gen LEVEL (Off/On/2×/3×/4×),
            // interpolation MODEL, or PERFORMANCE-PRESET change — each restarts the layer's optical-
            // flow/present state and otherwise needs a manual bg/fg to settle. Perf-preset teardown
            // requires the win-fg .so's swapchain-recreate conf-reload fix (else the layer's own
            // perf_preset self-rebuild collides with the teardown's surface rebuild → Fold-8 freeze).
            // Flow Scale stays live (no reset). Replaces win-fg's old soft pulseFgReset.
            maybeTriggerWinFgReset(mult >= 2 ? mult : 0, fgModel, fgPreset);
            // win-fg gets the same base->shown readout. Its case is the mirror
            // image of LSFG Native's: its generated frames are produced INSIDE
            // the guest and arrive as ordinary deliveries, so our per-guest-frame
            // counter already counts them - what it cannot see is whether they
            // survive the trip to the panel. Comparing that count against the
            // measured present rate makes a coalesced, never-displayed frame
            // visible as a DROP instead of it silently inflating the number.
            if (mult >= 2) startLsfgStatsReadout(); else stopLsfgStatsReadout();
        };
        // Live Present Mode selector (Graphics tab). The user's pick is persisted (per-game shortcut
        // override if present, else the container) then applied live through the same choke point as the
        // FG mailbox override (which also echoes the effective mode back to the drawer). GUARD: while FG
        // is multiplying the mode is force-locked to Mailbox and the drawer BLOCKS the tap, so this must
        // never fire then — but no-op defensively if it somehow does, so a stray event can't overwrite
        // the user's saved FIFO/Immediate preference (it must survive to revert when FG turns off).
        state.onPresentModeChange = mode -> {
            if (frameGenGenerating()) return;   // locked to mailbox; UI blocks this — belt-and-braces
            if (shortcut != null) {
                shortcut.putExtra("presentMode", mode);
                shortcut.saveData();
            } else {
                container.setRendererPresentMode(mode);
                container.saveData();
            }
            applyEffectivePresentMode();   // applies live + echoes the effective mode back to the drawer
        };
        // Standalone FPS limiter: paces the X11 Present extension (delays IdleNotify) so the GAME
        // itself throttles — works live with any frame-gen engine or none, all host renderers, all
        // APIs. Output of the guest present is what's capped. Persists to the container.
        state.onFpsLimitChange = () -> {
            XServerDrawerState s = XServerDrawerState.INSTANCE;
            boolean limOn  = s.getFpsLimiterEnabled().getValue();
            int   limitVal = s.getFpsLimit().getValue();   // remembered slider value, kept across on/off
            applyFpsLimit(limOn && limitVal > 0 ? limitVal : 0);
            // Persist to the SAME owner the launch seed resolves from (resolvedFpsLimiter*). Writing the
            // in-game toggle only to the container while a shortcut-launched game re-reads its (stale)
            // shortcut extra next launch is why the limiter "resets every time you close the game"
            // (issue #46). Mirror the ReShade owner-discriminator fix: write-target == read-source.
            if (shortcut != null) {
                shortcut.putExtra("fpsLimiterEnabled", limOn ? "1" : "0");
                if (limitVal > 0) shortcut.putExtra("fpsLimiterValue", String.valueOf(limitVal));
                shortcut.saveData();
            } else {
                container.setFpsLimiterEnabled(limOn);
                if (limitVal > 0) container.setFpsLimiterValue(limitVal);
                container.saveData();
            }
        };
        // VRR / refresh-rate matching toggle. Persists to the container and re-votes the panel
        // refresh rate live (applyVrr). Independent of frame-gen; works on all 3 host renderers.
        state.onMatchRefreshChange = () -> {
            boolean on = XServerDrawerState.INSTANCE.getMatchRefreshRate().getValue();
            if (nativeFgLocksHeld) {
                // Frame gen is running, so this is the frame-gen Auto (applyNativeFgLocks). Off =
                // opt out for THIS GAME: remembered on the shortcut only, never the container, so
                // other games made from it keep getting Auto during frame gen. On = clear it.
                // Launched without a shortcut: the choice lasts for this session only.
                nativeFgAutoOn = on;
                XServerDrawerState.INSTANCE.setFgAutoTurnedOn(false);
                if (shortcut != null) {
                    if (on) shortcut.removeExtra(FG_AUTO_OPT_OUT);
                    else shortcut.putExtra(FG_AUTO_OPT_OUT, "1");
                    shortcut.saveData();
                }
                Log.i("XServerDisplayActivity", "native-fg auto " + (on ? "on" : "off")
                    + (shortcut != null ? " (remembered for this game)" : " (this session only, no shortcut)"));
                reapplyFpsLimit();
                return;
            }
            container.setMatchRefreshRate(on);
            container.saveData();
            // reapplyFpsLimit, not just reapplyVrr: under native frame gen the display
            // rate decides whether the cap is an exact fit, which sets the pacer slack.
            reapplyFpsLimit();
        };
        // Manual refresh-rate lock (Auto OFF). Persists the chosen rate and re-applies the panel vote
        // live (reapplyVrr reads the limiter state; applyVrr uses the manual rate when Auto is off).
        state.onManualRefreshChange = () -> {
            int rate = XServerDrawerState.INSTANCE.getManualRefreshRate().getValue();
            container.setManualRefreshRate(rate);
            container.saveData();
            reapplyFpsLimit();   // display rate feeds the native-FG pacer slack too
        };
        // Drawer HUD/FPS tab opened — refresh the live display-rate readout.
        state.onRefreshRatePoll = this::updateCurrentRefreshRate;

        // ── Power-user performance toggles (non-root + root). Apply the effect live, then persist:
        // with a shortcut the value is written as a per-game override ONLY when it DIFFERS from the
        // global default, else the override is removed so the game re-inherits (see persistPerfToggle). ──
        state.onSustainedPerfModeChange = () -> {
            boolean on = XServerDrawerState.INSTANCE.getSustainedPerfMode().getValue();
            applyPerfKeyLive("sustainedPerfMode", on);
            persistPerfToggle("sustainedPerfMode", on);
        };
        state.onPerfPriorityBoostChange = () -> {
            boolean on = XServerDrawerState.INSTANCE.getPerfPriorityBoost().getValue();
            applyPerfKeyLive("perfPriorityBoost", on);
            persistPerfToggle("perfPriorityBoost", on);
        };
        state.onPreferBigCoresChange = () -> {
            boolean on = XServerDrawerState.INSTANCE.getPreferBigCores().getValue();
            applyPerfKeyLive("preferBigCores", on);
            persistPerfToggle("preferBigCores", on);
        };
        state.onRootToggleChange = (key, on) -> {
            applyPerfKeyLive(key, on);
            persistPerfToggle(key, on);
        };
        state.onResetPerfKey = key -> resetPerfKey(key);
        state.onResetAllPerf = this::resetAllPerfOverrides;
        state.onFreeMemory = () -> com.winhub.hernandez.perf.PerfRootApplier.INSTANCE.freeMemoryNow();
        state.onDeepClean = () -> com.winhub.hernandez.perf.PerfRootApplier.INSTANCE.deepCleanMemory();
        state.onRootReadoutPoll = this::refreshRootReadouts;
        // Cycle OFF -> FIT -> STRETCH -> FILL -> INTEGER -> OFF (legacy path; kept for any
        // cycle-style trigger). The drawer selector uses onSetFullscreenMode below instead.
        state.onToggleFullscreen       = () ->
            applyFullscreenMode(Container.nextFullscreenMode(xServerView.getRenderer().getFullscreenMode()));
        // Drawer segmented selector (#71 Stage 2): set the picked mode directly, live, without
        // closing the drawer so the user can compare modes before dismissing it.
        state.onSetFullscreenMode      = this::applyFullscreenMode;
        // Drawer segmented selector (#413): set the picked alignment directly, live, without closing
        // the drawer — same pattern as onSetFullscreenMode.
        state.onSetScreenAlignment     = this::applyScreenAlignment;
        state.onPauseResume            = () -> setPausedState(!isPaused);
        state.onPipMode                = () -> enterPictureInPictureMode();
        state.onActiveWindows          = () -> showActiveWindowsDialog();
        state.onModMenu                = this::toggleModMenu;
        state.onTaskManager            = () -> {
            XServerDrawerState.INSTANCE.selectTab(com.winhub.hernandez.ui.TabType.TASK_MANAGER);
            XServerDialogState.INSTANCE.setTmProcesses(new ArrayList<>());
            startTmPolling();
        };
        state.onMagnifier              = () -> showMagnifierOverlay();
        state.onLogs                   = () -> XServerDialogState.INSTANCE.show(XServerDialogState.ActiveDialog.DEBUG);
        state.onExit                   = () -> exit();
        // Seed the drawer chip from the persisted preference so it reflects reality on open
        // (state.reset() above zeroes it, so this has to come after).
        state.setMoveCursorToTouchpoint(preferences.getBoolean("move_cursor_to_touchpoint", false));
        state.onMoveCursorToTouchpoint = () -> MoveCursorToTouchpoint();
        // Per-gesture config shown under the Cursor to Touch toggle. Seed from prefs; the push to the
        // touchpad happens in setupUI, which is where that view is actually built.
        state.setGestureDragSelect(preferences.getBoolean("gesture_drag_select", true));
        state.setGestureLongPressRightClick(preferences.getBoolean("gesture_long_press_rmb", true));
        state.setGestureLongPressMs(preferences.getInt("gesture_long_press_ms",
            TouchpadView.DEFAULT_LONG_PRESS_MILLISECONDS));
        state.onGestureConfigChange = this::applyGestureConfig;
        state.onRelativeMouseMovement  = () -> {
            isRelativeMouseMovement = !isRelativeMouseMovement;
            state.setIsRelativeMouseMovement(isRelativeMouseMovement);
            xServer.setRelativeMouseMovement(isRelativeMouseMovement);
            // Persist per game (issue #431): titles like ETS2 need Relative Mouse every launch, and this
            // toggle used to live only in memory so it reset to off each session while the neighbouring
            // Cursor to Touch survived. Same owner rule as Present Mode / the FPS limiter: write to the
            // shortcut when launched from one (that is what the launch seed reads back), else the container.
            if (shortcut != null) {
                shortcut.putExtra("relativeMouse", isRelativeMouseMovement ? "1" : "0");
                shortcut.saveData();
            } else {
                container.putExtra("relativeMouse", isRelativeMouseMovement ? "1" : "0");
                container.saveData();
            }
        };
        state.onDisableMouse           = () -> {
            isMouseDisabled = !isMouseDisabled;
            state.setIsMouseDisabled(isMouseDisabled);
            touchpadView.setMouseEnabled(!isMouseDisabled);
        };
        String fpsCfg = resolvedFPSCounterConfig();
        state.setFpsConfig(fpsCfg);
        state.onFpsConfigApply = (newConfig) -> {
            if (newConfig == null) return;
            state.setFpsConfig(newConfig);
            runOnUiThread(() -> {
                com.winhub.hernandez.core.KeyValueSet kv = new com.winhub.hernandez.core.KeyValueSet(newConfig);
                // Master toggle: re-read live so flipping "Show HUD" hides/shows the overlay without a
                // relaunch (this callback is the same path metric toggles ride).
                hudCounterEnabled = kv.get("hudEnabled", "1").equals("1");
                boolean haveHud = fusionHud != null;
                // Lazy build (container-wide scope): the user flipped "Show HUD" on but no HUD exists —
                // FPS was off for this launch so nothing was built at onCreate. Enable the container-wide
                // FPS gate (so ContainerDetailScreen's "Show FPS" reflects it next open) and build the HUD
                // now so it appears this instant, no relaunch, no trip to container settings. Fires only on
                // the OFF->build transition (no HUD yet); turning OFF never flips the gate back.
                if (hudCounterEnabled && !haveHud) {
                    if (container != null && !container.isShowFPS()) {
                        container.setShowFPS(true);
                        container.saveData();
                    }
                    ensureHudBuilt();               // builds the HUD (idempotent)
                    ensureHudBoundToGameWindow();   // bind to the presenting game window so FPS counts, not 0
                    // ensureHudBuilt() re-seeds hudCounterEnabled from the *persisted* (pre-toggle) config,
                    // which still reads hudEnabled=0 here (persist runs after this UI block). Re-assert the
                    // live ON state before the visibility push below.
                    hudCounterEnabled = true;
                }
                // Push the new config to the HUD. `shown` folds in the master toggle so flipping "Show HUD"
                // hides/reveals it live.
                boolean shown = frameRatingWindowId != -1 && hudCounterEnabled;
                if (fusionHud != null) { fusionHud.applyConfig(newConfig); fusionHud.setVisibility(shown ? View.VISIBLE : View.GONE); }
            });
            persistFPSCounterConfig(newConfig);
        };

        if (inputControlsView != null) inputControlsView.setVisualStyle(VisualStyle.GAMEHUB);

        ComposeView drawerComposeView = findViewById(R.id.XServerDrawerComposeView);
        XServerDrawerKt.setupComposeView(drawerComposeView);

        // Dialog host: a full-size ComposeView on top of the game surface for
        // in-game dialogs and floating overlays (magnifier, FSR panel).
        FrameLayout xServerDisplay = findViewById(R.id.FLXServerDisplay);
        ComposeView dialogHostView = new ComposeView(this);
        dialogHostView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        xServerDisplay.addView(dialogHostView);
        XServerDialogHostKt.setupDialogHost(dialogHostView);

        imageFs = ImageFs.find(this);

        // Stage the bundled components before the guest starts. MainActivity already does this on
        // app start, but this Activity is exported and home-screen game shortcuts launch it
        // directly — so a user who updates and then launches straight into a game would otherwise
        // run the previous frame-gen layer until they next opened the app. Synchronous on purpose:
        // once the stamps match this is a couple of stats, and when they don't the copy has to
        // happen before the guest dlopens the layer anyway.
        ImageFsInstaller.stageBundledComponents(this, imageFs);

        // Prepare dev/input directory - actual event files created after shortcut is loaded
        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {
            // js* as well as event*: a Linux session creates js0 for the Steam client, and a stale one
            // left behind would hand the next Wine session a phantom pad it never asked for.
            // Which nodes exist is the launcher's decision, so start every launch with none.
            for (int i = 0; i < 4; i++) {
                File eventFile = new File(devInputDir, "event" + i);
                if (eventFile.exists()) eventFile.delete();
                File jsFile = new File(devInputDir, "js" + i);
                if (jsFile.exists()) jsFile.delete();
            }
        }

        // Initialize the WinHandler
        winHandler = new WinHandler(this);
        winHandler.setFakeInputPath(devInputDir.getAbsolutePath());

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        containerManager = new ContainerManager(this);
        container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));

        componentInstallerExe = getIntent().getStringExtra("component_installer_exe");
        // Wayland and the Linux (gamescope) runtime are retired: every session is a Wine X11
        // session. The launch extras that used to switch them on are ignored.
        waylandMode = false;
        // "Launch this game on the TV": the launcher aimed this session at an external display and told
        // us which one. That extra is only what was ASKED for — the system can decline a launch display
        // without anything throwing — so read where the window really ended up and decide from that.
        // It is knowable here: an activity is attached to its display before onCreate runs, so
        // getDisplay() (API 30+, else the activity's WindowManager default display) already answers the
        // real screen, early enough for the screen-size decision further down.
        tvLaunchDisplayId = getIntent().getIntExtra(
                com.winhub.hernandez.display.ExternalDisplay.EXTRA_DISPLAY_ID, -1);
        sessionDisplayId = com.winhub.hernandez.display.ExternalDisplay.currentDisplayId(this);
        if (tvLaunchDisplayId >= 0) {
            if (onTvLaunchDisplay()) {
                Log.i("XServerDisplayActivity", "TV: session is on display " + sessionDisplayId + ", as asked");
                // The launcher put the handheld's companion screen up before starting us; tell it the
                // session really did land on the TV, so it stops waiting and knows where to send input
                // back to. This is the earliest honest answer — the window's display is known before
                // onCreate runs — and it is the session, not the launcher, that owns the screen's life.
                com.winhub.hernandez.display.TvCompanionActivity.onSessionOnTv(sessionDisplayId);
            } else {
                // Declined. This is NOT a TV session: no screen-size override, no output-mode request,
                // and the unplug/pause watch never arms — there is no TV to lose. The user is told once
                // from setupUI, where the toast host is up.
                tvLaunchDeclined = true;
                Log.w("XServerDisplayActivity", "TV: the system declined the launch on display "
                        + tvLaunchDisplayId + " — the game is on the handheld (display " + sessionDisplayId
                        + "), so this session is not a TV session");
                // The launcher put the handheld's companion screen up before starting us, and it is now
                // describing a TV this game is not on — with the game itself about to open behind it.
                com.winhub.hernandez.display.TvCompanionActivity.dismiss("the launch display was declined");
            }
        }
        // Watch the display set from here on, not from the end of setupUI: everything between the two is
        // the whole container setup (and on a portrait container setupUI waits for the orientation flip
        // as well), so a cable pulled in that window used to go unnoticed until the next resume or
        // configuration change. Idempotent — startHdrCapabilityReport() calls it again and it no-ops.
        registerDisplayWatch();

        // Log shortcut_path
        String shortcutPath = getIntent().getStringExtra("shortcut_path");
        Log.d("XServerDisplayActivity", "Shortcut Path: " + shortcutPath);


        // Determine container ID
        int containerId = getIntent().getIntExtra("container_id", 0);
        Log.d("XServerDisplayActivity", "Container ID from Intent: " + containerId);
        if (containerId == 0) {
            Log.d("XServerDisplayActivity", "Container ID is 0, attempting to parse from .desktop file");
            // Proceed with .desktop file parsing
        }


        // If container_id is 0, read from the .desktop file
        if (containerId == 0 && shortcutPath != null && !shortcutPath.isEmpty()) {
            File shortcutFile = new File(shortcutPath);
            containerId = parseContainerIdFromDesktopFile(shortcutFile);
            Log.d("XServerDisplayActivity", "Parsed Container ID from .desktop file: " + containerId);
        }

        // Initialize playtime tracking
        playtimePrefs = getSharedPreferences("playtime_stats", MODE_PRIVATE);
        shortcutName = getIntent().getStringExtra("shortcut_name");

        // Ensure shortcutPath is not null before proceeding
        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            if (shortcutName == null || shortcutName.isEmpty()) {
                shortcutName = parseShortcutNameFromDesktopFile(new File(shortcutPath));
                Log.d("XServerDisplayActivity", "Parsed Shortcut Name from .desktop file: " + shortcutName);
            }
        } else {
            Log.d("XServerDisplayActivity", "No shortcut path provided, skipping shortcut parsing.");
        }

        // Increment play count at the start of a session
        incrementPlayCount();

        // Log the final container_id
        Log.d("XServerDisplayActivity", "Final Container ID: " + containerId);

        // Retrieve the container and check if it's null
        container = containerManager.getContainerById(containerId);

        if (container == null) {
            Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);
            finish();  // Gracefully exit the activity to avoid crashing
            return;
        }

        // Initialise the Steam DB singleton in THIS process. A game launched directly (from a
        // library shortcut, not via the store) never ran SteamRepository.initialize(ctx), so the
        // repo's appContext is null and getDatabase() throws IllegalStateException ("SteamDatabase
        // not initialised"). That silently broke the achievement seed/schema/appId-resolve AND the
        // Steam cloud-save auto-triggers at launch/exit (all their DB reads threw and were caught).
        // getInstance(ctx) is idempotent + lightweight (SQLiteOpenHelper, no CM connection).
        try { SteamDatabase.getInstance(getApplicationContext()); }
        catch (Throwable t) { Log.w("XServerDisplayActivity", "SteamDatabase init failed", t); }

        // Construct the shortcut (if any) up front so per-game overrides (frame-gen engine, fps
        // limiter, renderer) can be resolved against it below; each falls back to the container value.
        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            shortcut = new Shortcut(container, new File(shortcutPath));
            XServerDrawerState.INSTANCE.setGameTitle(shortcut.name);
        }

        // Display backend: the game's override, else the container's. Resolved here so every launch
        // path (shortcut list, Games tab, Big Picture, pinned shortcuts, container Run) honours it,
        // not only the ones that pass wayland_mode.
        if (!waylandMode) {
            String backend = shortcut != null ? shortcut.getExtra("displayBackend", "") : "";
            if (backend.isEmpty()) backend = container.getDisplayBackend();
            waylandMode = Container.DISPLAY_BACKEND_WAYLAND.equals(backend);
        }

        // In-game Friends tab (drawer): read the friends/chat opt-in once for this launch and start
        // watching for a live source. The RealSteam hint keeps the app-session source from flashing up
        // before maybeStageRealSteam() arms the plan (which confirms or withdraws it); disarmed in onDestroy.
        try {
            com.winhub.hernandez.store.InGameFriendsSource.INSTANCE.arm(getApplicationContext(),
                    shortcut != null && "RealSteam".equals(shortcut.getExtra("launchMode")));
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "InGameFriendsSource arm failed", t);
        }

        // Sync the in-game frame-generation controls. bionicFgActive = is a frame-gen layer actually
        // loaded this session? Live FG tuning only works when it is; the drawer uses this to gate the
        // FG multiplier row. The engine honors per-game overrides (resolvedFrameGenEngine), else the
        // container's value. multiplier/flow are NOT per-game — live-tuned in-game, persisted on the
        // container. The FPS limiter is independent (host pacer) and is always available regardless.
        String fgEngine = resolvedFrameGenEngine();
        boolean fgEnabled = fgEngine.equals("bionic");
        // Both LSFG engines drive the same in-game multiplier row; only WHERE the
        // generator runs differs (in-container layer vs our own compositor).
        boolean lsfgOn = fgEngine.equals("lsfg") || fgEngine.equals("lsfg-native");
        boolean fpsLimOn = resolvedFpsLimiterEnabled();
        boolean bionicFgActive = fgEnabled || lsfgOn;
        XServerDrawerState.INSTANCE.setBionicFgActive(bionicFgActive);
        // Wayland: the compositor's frame-generation bridge (LSFG Native + Win-FG Native,
        // waylandcomp/src/framegen_bridge.c) is compiled in, so the drawer's FG rows are live there.
        XServerDrawerState.INSTANCE.setWaylandFrameGenAvailable(waylandMode);
        XServerDrawerState.INSTANCE.setFrameGenEnabled(fgEnabled || lsfgOn);
        // Frame gen normally starts OFF in-game (multiplier 0): the engine is loaded at launch
        // (below) so the player can opt in per session from the drawer. EXCEPTION: "LSFG Auto
        // Enable" seeds the saved multiplier so an opted-in container is live from frame one and
        // the drawer/badge show ON. Both LSFG engines honour it; native also needs the imported
        // DLL, or prepareLsfgNative publishes the reason instead of arming. The seeded multiplier
        // is what the drawer, HUD and present-mode/VRR logic read, so they all agree at launch.
        // A multiplier of 0 only ever means "off for this session" (the editor only offers 2-4).
        // With Auto Enable ON it contradicts the container's intent, so heal it to the default
        // instead of silently launching with frame gen off after an older in-game save.
        int savedFgMult = Math.max(2, container.getFrameGenMultiplier());
        boolean lsfgAutoArm = container.isLsfgAutoEnable()
                && (fgEngine.equals("lsfg")
                    || (fgEngine.equals("lsfg-native")
                        && com.winhub.hernandez.core.LsfgNative.isDllAvailable(this)));
        int lsfgSeedMult = lsfgAutoArm ? savedFgMult : 0;
        XServerDrawerState.INSTANCE.setFrameGenMultiplier(lsfgSeedMult);
        XServerDrawerState.INSTANCE.setFrameGenFlowScale(container.getFrameGenFlowScale());
        XServerDrawerState.INSTANCE.setFrameGenModel(resolvedFrameGenModel());
        XServerDrawerState.INSTANCE.setFrameGenPerfPreset(resolvedFrameGenPerfPreset());
        XServerDrawerState.INSTANCE.setFrameGenEngine(fgEngine);
        winFgNativeSession = computeWinFgNativeSession();
        XServerDrawerState.INSTANCE.setWinFgNative(winFgNativeSession);
        XServerDrawerState.INSTANCE.setLsfgPerformanceMode(container.isLsfgPerformanceMode());
        // LSFG Native experimental capture resolution (FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED;
        // with the flag off the seed is always panel, whatever the container stored).
        XServerDrawerState.INSTANCE.setFgCaptureResolution(
            com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED ? container.getFgCaptureResolution() : Container.FG_CAPTURE_PANEL);
        // The panel height in landscape, so the drawer's capture chips can leave out heights the
        // renderer would clamp anyway (it never runs the chain below a quarter of the panel or
        // above it).
        {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            XServerDrawerState.INSTANCE.setFgPanelHeight(Math.min(dm.widthPixels, dm.heightPixels));
        }
        XServerDrawerState.INSTANCE.setFpsLimiterEnabled(fpsLimOn);
        XServerDrawerState.INSTANCE.setFpsLimit(resolvedFpsLimiterValue());
        XServerDrawerState.INSTANCE.setMatchRefreshRate(resolvedMatchRefreshRate());
        XServerDrawerState.INSTANCE.setVrrSupported(
            com.winhub.hernandez.widget.XServerView.isDisplayVrrCapable(getWindowManager().getDefaultDisplay()));
        XServerDrawerState.INSTANCE.setSupportedRefreshRates(
            com.winhub.hernandez.widget.XServerView.getSupportedRefreshRates(getWindowManager().getDefaultDisplay()));
        XServerDrawerState.INSTANCE.setManualRefreshRate(resolvedManualRefreshRate());
        updateCurrentRefreshRate();

        // Power-user performance toggles (non-root). Seed the drawer, apply the ones that take effect
        // at launch. preferBig feeds the affinity computation just below; priority boost is applied
        // once the render threads exist (setupUI). All three are live-toggleable from the drawer.
        boolean sustainedPerf = resolvedSustainedPerfMode();
        boolean preferBig     = resolvedPreferBigCores();
        XServerDrawerState.INSTANCE.setSustainedPerfMode(sustainedPerf);
        XServerDrawerState.INSTANCE.setPerfPriorityBoost(resolvedPerfPriorityBoost());
        XServerDrawerState.INSTANCE.setPreferBigCores(preferBig);
        getWindow().setSustainedPerformanceMode(sustainedPerf);

        // PerfRootApplier-owned toggles: seed the drawer with the effective (override ?? global) values
        // and apply the effective state now. The five root-only ones no-op unless root is granted; the
        // GPU max-clock pin also applies without root on Adreno (KGSL turbo). Auto-revert on
        // exit/background/crash restores everything (PerfRevertRegistry).
        java.util.Map<String, Boolean> rootEffective = new java.util.HashMap<>();
        for (String rk : com.winhub.hernandez.perf.PerfRootApplier.INSTANCE.getROOT_KEYS()) {
            rootEffective.put(rk, resolvedRootBool(rk));
        }
        XServerDrawerState.INSTANCE.setRootToggles(rootEffective);
        // Auto deep-clean on launch (Tier 2, root-only, global default). deepCleanMemory() self-gates
        // on root and uses `am kill-all`, which never touches this game's foreground session.
        boolean autoDeepClean = com.winhub.hernandez.perf.PerformanceSettings.INSTANCE
            .rootDefaultValue(com.winhub.hernandez.perf.PerfRootApplier.KEY_AUTO_DEEP_CLEAN);
        com.winhub.hernandez.perf.PerfRootApplier.INSTANCE.applyEffective(rootEffective, autoDeepClean);

        // Unified per-game override tracking + two-way sync for ALL 9 keys: seed the overridden set
        // from the shortcut's extras (a key present = per-game override; absent = inherit + mirror the
        // App Settings global default live). Drives the override/global indicator + reset affordance.
        java.util.Set<String> overriddenKeys = new java.util.HashSet<>();
        if (shortcut != null) {
            for (String pk : com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.getALL_PERF_KEYS()) {
                if (shortcut.hasExtra(pk)) overriddenKeys.add(pk);
            }
        }
        XServerDrawerState.INSTANCE.startPerfSync(overriddenKeys);

        containerManager.activateContainer(container);

        // Controller discovery nodes: a slot's event<N> exists only while a controller holds that slot.
        // WinHandler's slot writer creates it when a pad or the on-screen pad takes the slot (pads that
        // are connected at launch are pre-assigned before Wine boots, the on-screen pad when setupUI
        // shows it) and removes it when the slot is released; the guest's evdev layer redirects the
        // inotify watch on /dev/input to this directory, so SDL (winebus' only bus here) sees nodes come
        // and go. All four used to be created here unconditionally, and slots 1-3, never written, were
        // listed to games as three phantom pads.

        taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));
        taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));

        if (shortcut != null) {
            taskAffinityMask = (short) ProcessHelper.getAffinityMask(shortcut.getExtra("cpuList", container.getCPUList(true)));
            taskAffinityMaskWoW64 = taskAffinityMask;
        }

        // "Prefer big cores" preset overrides the raw cpuList affinity with the top-frequency cluster.
        // Non-root: this only feeds the existing taskAffinityMask path. Empty result (undetectable
        // topology) leaves the computed affinity untouched.
        if (preferBig) {
            String bigList = detectBigCoreCpuListLogged();
            if (bigList != null && !bigList.isEmpty()) {
                taskAffinityMask = (short) ProcessHelper.getAffinityMask(bigList);
                taskAffinityMaskWoW64 = taskAffinityMask;
                Log.d("XServerDisplayActivity", "Prefer big cores: affinity -> " + bigList);
            }
        }

        // Determine the class name for the startup workarounds
        String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";
        Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);

        firstTimeBoot = container.getExtra("appVersion").isEmpty();

        String wineVersion = container.getWineVersion();
        wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);

        // Wayland gate (the ONE resolver every launch path funnels through — the intent flag, the
        // shortcut override and the container default were all folded into waylandMode above): the
        // selected layer must ship winewayland.so AND its bundled Wayland Turnip, else the compositor
        // start, the registry driver write and the winex11.drv hide below would all run against a
        // layer that can't drive them. Fall back to X11 and say so.
        if (waylandMode
                && !com.winhub.hernandez.core.WineWaylandSupport.isWaylandCapable(wineInfo)) {
            Log.w("XServerDisplayActivity", "wayland: layer " + wineVersion + " (" + wineInfo.path
                    + ") lacks winewayland.so and/or lib/libvulkan_freedreno_wayland.so; launching on X11");
            waylandMode = false;
            Toast.makeText(this, "Wayland needs the Wayland Proton layer (11.0-2.1 arm64ec); launching on X11.",
                    Toast.LENGTH_LONG).show();
        }

        imageFs.setWinePath(wineInfo.path);

        ProcessHelper.removeAllDebugCallbacks();
        XServerDialogState.INSTANCE.clearLog();
        if (enableLogs) {
            LogView.setFilename(getExecutable());
            ProcessHelper.addDebugCallback(line -> XServerDialogState.INSTANCE.appendLog(line));
        }

        graphicsDriver = container.getGraphicsDriver();
        rendererDriverId = container.getRendererDriverId();
        String graphicsDriverConfig = container.getGraphicsDriverConfig();
        audioDriver = container.getAudioDriver();
        emulator = container.getEmulator();
        midiSoundFont = container.getMIDISoundFont();
        dxwrapper = container.getDXWrapper();
        String fpsCounterConfig = container.getFPSCounterConfig();
        String dxwrapperConfig = container.getDXWrapperConfig();
        screenSize = container.getScreenSize();
        winHandler.setInputType((byte) container.getInputType());
        lc_all = container.getLC_ALL();

        // Log the entire intent to verify the extras
        Intent intent = getIntent();
        Log.d("XServerDisplayActivity", "Intent Extras: " + intent.getExtras());

        if (shortcut != null) {
            graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
            rendererDriverId = shortcut.getExtra("rendererDriverId", container.getRendererDriverId());
            graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
            audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
            emulator = shortcut.getExtra("emulator", container.getEmulator());
            dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
            dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
            screenSize = shortcut.getExtra("screenSize", container.getScreenSize());
            lc_all = shortcut.getExtra("lc_all", container.getLC_ALL());
            String inputType = shortcut.getExtra("inputType");
            if (isControllerPassthroughLaunch()) {
                // Controller passthrough (lever 3): force DInput-only so a classic game reads the pad
                // directly. Overrides the shortcut's own inputType only for this RealSteam launch.
                winHandler.setInputType(WinHandler.FLAG_INPUT_TYPE_DINPUT);
            } else if (!inputType.isEmpty()) {
                winHandler.setInputType(Byte.parseByte(inputType));
            }
            String xinputDisabledString = shortcut.getExtra("disableXinput", "false");
            xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);
            // Pass the value to WinHandler
            winHandler.setXInputDisabled(xinputDisabledFromShortcut);
            String sharpnessEffect = shortcut.getExtra("sharpnessEffect", "None");
            if (!sharpnessEffect.equals("None")) {
                double sharpnessLevel = Double.parseDouble(shortcut.getExtra("sharpnessLevel", "100"));
                double sharpnessDenoise = Double.parseDouble(shortcut.getExtra("sharpnessDenoise", "100"));
                vkbasaltConfig = "effects=" + sharpnessEffect.toLowerCase() + ";" + "casSharpness=" + sharpnessLevel / 100 + ";" + "dlsSharpness=" + sharpnessLevel / 100  + ";" + "dlsDenoise=" + sharpnessDenoise / 100 + ";" + "enableOnLaunch=True";
            }
            Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);
        }

        // DirectAudio's winedirectaudio.drv only loads on the arm64ec Proton builds listed in
        // DirectAudioSupport.SUPPORTED_BUILD_TOKENS (7 as of driver v1.3.2); on
        // any other layer it does nothing / breaks audio. The editors grey it out and coerce it on save,
        // but a container/shortcut written before this gate (or whose layer was swapped elsewhere) can
        // still arrive here as "directaudio" — the last place it could be applied to the guest registry.
        // Fall back to the default driver so an unsupported layer never gets Audio=directaudio.
        if ("directaudio".equals(audioDriver)
                && !DirectAudioSupport.isSupported(wineVersion)) {
            audioDriver = Container.DEFAULT_AUDIO_DRIVER;
        }

        // Microphone (opt-in per container/shortcut). The DirectAudio driver opens its OWN AAudio INPUT
        // stream when BANNER_AUDIO_DIRECT_MIC=1 is in the launch env; that needs RECORD_AUDIO. The editors
        // request it when the user turns the toggle on, but a shortcut granted-then-revoked in system
        // settings (or an imported config) can still arrive here with the flag set — so best-effort
        // re-request it now, on the main thread, before the guest loads. Never blocks the launch: if it's
        // denied, the driver simply gets no input (it owns capture and handles the missing-permission
        // case). The app never touches AudioRecord/AAudio/MediaRecorder itself.
        if ("directaudio".equals(audioDriver) && directMicRequestedInEnv()
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, RECORD_AUDIO_REQUEST_CODE);
        }

        // Gyro (motion aim) — resolve the whole config ONCE here and push it into WinHandler in a
        // single call. enabled/target/activator/sensitivity/invertX/invertY are per-game (the
        // shortcut extra wins, else the container value, else the GYRO_*_DEFAULT baked into the
        // getter); deadzone/smoothing are container-only, they describe the hand and the device
        // rather than the game. This must never move onto the sample path — Container.getExtra
        // parses JSON and allocates, and updateGyroData runs at the sensor rate while the game
        // renders. WinHandler keeps the resolved values in its volatile fields from here on.
        boolean gyroOn = container.isGyroEnabled();
        int gyroTarget = container.getGyroTarget();
        int gyroActivator = container.getGyroActivator();
        int gyroActivationMode = container.getGyroActivationMode();
        int gyroMode = container.getGyroMode();
        float gyroSensitivity = container.getGyroSensitivity();
        boolean gyroInvertX = container.isGyroInvertX();
        boolean gyroInvertY = container.isGyroInvertY();
        if (shortcut != null) {
            gyroOn = shortcut.getExtra("gyroEnabled", gyroOn ? "1" : "0").equals("1");
            gyroInvertX = shortcut.getExtra("gyroInvertX", gyroInvertX ? "1" : "0").equals("1");
            gyroInvertY = shortcut.getExtra("gyroInvertY", gyroInvertY ? "1" : "0").equals("1");
            try {
                gyroTarget = Integer.parseInt(shortcut.getExtra("gyroTarget", String.valueOf(gyroTarget)));
                gyroActivator = Integer.parseInt(shortcut.getExtra("gyroActivator", String.valueOf(gyroActivator)));
                gyroActivationMode = Integer.parseInt(shortcut.getExtra("gyroActivationMode", String.valueOf(gyroActivationMode)));
                gyroMode = Integer.parseInt(shortcut.getExtra("gyroMode", String.valueOf(gyroMode)));
            } catch (NumberFormatException e) {}
            try {
                gyroSensitivity = Float.parseFloat(shortcut.getExtra("gyroSensitivity", String.valueOf(gyroSensitivity)));
            } catch (NumberFormatException e) {}
        }
        // Orientation mode needs a rotation-vector sensor this device may not have. Fall back to rate
        // mode and say so once — but deliberately do NOT rewrite the stored setting: the container may
        // be restored from a backup onto a phone that does have the sensor, and silently downgrading
        // it here would lose the user's choice for good.
        if (gyroMode == WinHandler.GYRO_MODE_ORIENTATION && gyroRotationSensor == null) {
            Log.i("XServerGyro", "Gyro orientation mode requested but no rotation-vector sensor — running rate mode");
            gyroMode = WinHandler.GYRO_MODE_RATE;
        }
        // The mouse target can't be driven by an absolute tilt (see WinHandler.sanitizeGyroMode);
        // applyGyroTuning enforces that, so the mode WinHandler reports back may differ from this one.
        winHandler.applyGyroTuning(gyroOn, gyroTarget, gyroActivator, gyroActivationMode, gyroMode,
            gyroSensitivity, container.getGyroDeadzone(), container.getGyroSmoothing(), gyroInvertX, gyroInvertY);
        // Only now is it safe to let samples in (registerGyroSensor used to run in onCreate, long
        // before the container existed, so the first few hundred ms ran on the built-in defaults).
        registerGyroSensor();

        // Stick deadzone / sensitivity: the container value, with a shortcut's own extra winning key by
        // key. Resolved once here (Container.getExtra parses JSON) and published as one immutable
        // snapshot that every ExternalController reads; cleared again in onDestroy.
        StickShaper.Tuning stickTuning = container.getStickTuning();
        if (shortcut != null) {
            stickTuning = new StickShaper.Tuning(
                StickShaper.parseDeadzone(shortcut.getExtra(StickShaper.KEY_LEFT_DEADZONE), stickTuning.leftDeadzone),
                StickShaper.parseSensitivity(shortcut.getExtra(StickShaper.KEY_LEFT_SENSITIVITY), stickTuning.leftSensitivity),
                StickShaper.parseDeadzone(shortcut.getExtra(StickShaper.KEY_RIGHT_DEADZONE), stickTuning.rightDeadzone),
                StickShaper.parseSensitivity(shortcut.getExtra(StickShaper.KEY_RIGHT_SENSITIVITY), stickTuning.rightSensitivity),
                StickShaper.parseSmoothEdge(shortcut.getExtra(StickShaper.KEY_SMOOTH_EDGE), stickTuning.smoothEdge));
        }
        StickShaper.setActive(stickTuning);
        XServerDialogState.INSTANCE.setStickTuning(stickTuning);

        // VEGAS runs its own native DLLs from vegas-<ver>.tzst — no alias to DXVK.

        this.graphicsDriverConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
        this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);

        if (!wineInfo.isWin64()) {
            onExtractFileListener = (file, size) -> {
                String path = file.getPath();
                if (path.contains("system32/")) return null;
                return new File(path.replace("syswow64/", "system32/"));
            };
        }

        if (shortcut == null)
            preloaderDialog.show(container.getName(), null, null);
        else {
            preloaderDialog.show(shortcut.name, shortcut.icon, shortcut.getCoverArt(),
                com.winhub.hernandez.ui.screens.LaunchSpecBuilderKt.buildLaunchSpec(shortcut, this),
                com.winhub.hernandez.ui.screens.LaunchSpecBuilderKt.buildLaunchDetails(shortcut));
        }
        preloaderDialog.step(1, "Preparing container…");

        // TV render resolution (v2): when launching with an external display connected, honor the TV
        // render-resolution choice (2 = 1080p, 3 = 1440p) before ScreenInfo is built. "Match TV" (0) /
        // "Match handheld" (1) keep the container's screen size. Guarded to a TV being present at launch
        // so it never changes handheld-only sessions.
        try {
            int tvRes = Integer.parseInt(container.getExtra("tv.renderRes", "0"));
            if (tvRes == 2 || tvRes == 3) {
                android.hardware.display.DisplayManager tvDm =
                        (android.hardware.display.DisplayManager) getSystemService(DISPLAY_SERVICE);
                boolean tvPresent = tvDm != null && tvDm.getDisplays(
                        android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION).length > 0;
                if (tvPresent) screenSize = (tvRes == 2) ? "1920x1080" : "2560x1440";
            }
        } catch (Exception ignored) {}

        // "Match the TV's resolution" (per game, TV tab): render at the display's own resolution instead
        // of this game's screen size. Only when the window really IS on the TV — a declined launch is an
        // ordinary handheld session and must keep the game's own size, or it would render at the TV's
        // resolution on the phone. The display is read by the session's actual id for the same reason.
        // Only for THIS session, too: the shortcut's screenSize extra is never rewritten, so unplugging
        // the TV and launching again gives the game its own resolution back. The output mode the user
        // picked wins over the display's current one: that is the resolution the TV is being asked for.
        if (onTvLaunchDisplay() && shortcut != null
                && com.winhub.hernandez.display.ExternalDisplay.matchResolution(shortcut)) {
            try {
                android.view.Display tvDisplay =
                        com.winhub.hernandez.display.ExternalDisplay.byId(this, sessionDisplayId);
                String tvSize = com.winhub.hernandez.display.ExternalDisplay.resolutionOf(
                        com.winhub.hernandez.display.ExternalDisplay.effectiveMode(tvDisplay,
                                com.winhub.hernandez.display.ExternalDisplay.modeId(shortcut)));
                if (!tvSize.isEmpty()) {
                    Log.i("XServerDisplayActivity", "TV: matching the display's resolution — screen size "
                            + screenSize + " -> " + tvSize);
                    screenSize = tvSize;
                }
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "TV: could not read the display's resolution", e);
            }
        }

        // Supersampling ("Render scale"): multiply the game's render resolution so it renders above
        // display res, then let the Vulkan compositor Lanczos-downscale it (see setHqDownscale below).
        // Stored via the "renderScale" extra; the per-game shortcut overrides the container default.
        // Off / 1.0 = no change. This must run before ScreenInfo is built so Wine/the X server use it.
        float renderScale;
        try {
            String rsStr = (shortcut != null)
                ? shortcut.getExtra("renderScale", container.getExtra("renderScale", "1.0"))
                : container.getExtra("renderScale", "1.0");
            renderScale = Float.parseFloat(rsStr);
        } catch (NumberFormatException e) {
            renderScale = 1.0f;
        }
        if (renderScale > 1.0f) {
            String[] wh = screenSize.split("x");
            if (wh.length == 2) {
                try {
                    final int MAX_W = 7680, MAX_H = 4320; // same upper bound as the resolution picker
                    int baseW = Integer.parseInt(wh[0].trim());
                    int baseH = Integer.parseInt(wh[1].trim());
                    // Clamp the factor so neither dimension exceeds the max, preserving aspect ratio.
                    float factor = Math.min(renderScale, Math.min((float) MAX_W / baseW, (float) MAX_H / baseH));
                    int scaledW = Math.min(Math.round(baseW * factor), MAX_W);
                    int scaledH = Math.min(Math.round(baseH * factor), MAX_H);
                    // X servers / Wine desktops want even dimensions.
                    if ((scaledW & 1) == 1) scaledW--;
                    if ((scaledH & 1) == 1) scaledH--;
                    if (scaledW > baseW || scaledH > baseH) {
                        screenSize = scaledW + "x" + scaledH;
                        hqDownscale = true;
                    }
                } catch (NumberFormatException e) { /* keep the base screenSize */ }
            }
        }

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(new ScreenInfo(screenSize));
        xServer.setWinHandler(winHandler);
        advertisePanelRefreshRates();

        // Restore the saved Relative Mouse state for this game (issue #431). Read from the same owner
        // the drawer toggle writes to: shortcut extra first (per-game), container extra as the fallback.
        // The drawer state was seeded with the in-memory default before the shortcut existed, so echo
        // the restored value back to it here; pointer capture itself is (re)requested on window focus.
        {
            String savedRelMouse = container.getExtra("relativeMouse", "0");
            if (shortcut != null) savedRelMouse = shortcut.getExtra("relativeMouse", savedRelMouse);
            if ("1".equals(savedRelMouse)) {
                isRelativeMouseMovement = true;
                xServer.setRelativeMouseMovement(true);
                XServerDrawerState.INSTANCE.setIsRelativeMouseMovement(true);
            }
        }

        // Add the OnWindowModificationListener for dynamic workarounds
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted && window.isApplicationWindow()) {
                    winStarted = true;   // set first so this fires exactly once
                    xServerView.getRenderer().setCursorVisible(true);
                    cancelLaunchTimers();
                    // First real game frame: hold the launch screen a few more seconds (the game
                    // renders behind it) so the boot steps are actually seen, then close and pop the
                    // controller-status toast (game now visible, preloader gone; runs on the main thread
                    // so getPlayerSlotAssignments is safe).
                    if (componentInstallerExe != null && !componentInstallerExe.isEmpty()) {
                        // Installer / setup session: a window appeared, so the installer has a UI the
                        // user may need to click (device test #3: EA's installer waited for "Install"
                        // behind an opaque overlay). Close the overlay like a normal launch and keep the
                        // user informed with a reminder toast instead. A SILENT installer never maps a
                        // window, so it never reaches here and the overlay's status line stays up.
                        installerReminderToast();
                        new android.os.Handler(getMainLooper()).postDelayed(preloaderDialog::closeOnUiThread, 1500);
                    } else {
                        new android.os.Handler(getMainLooper()).postDelayed(() -> {
                            preloaderDialog.closeOnUiThread();
                            showControllerStatusToast("launch", null);
                        }, LAUNCH_OVERLAY_GRACE_MS);
                    }
                }
                    
                // SHM/copyArea present path — count the frame (self-heals onto the real presenting
                // window for GL/Zink titles; see driveHudFrameTick).
                driveHudFrameTick(window.id);
            }

            @Override
            public void onMapWindow(Window window) {
                // Log the class name of the mapped window
                Log.d("XServerDisplayActivity", "onMapWindow: Detected window className: " + window.getClassName());
                // A window mapped (before its content paints) — nudge the tail label so the user sees
                // progress past the guest-boot spinner. The real dismiss is still onUpdateWindowContent.
                if (!winStarted) preloaderDialog.enterGuest("Game window detected…");
                assignTaskAffinity(window);
            }

            @Override
            public void onModifyWindowProperty(Window window, Property property) {
                trackPadFeedWindow(window, property);
                changeFrameRatingVisibility(window, property);
                // The guest publishes its pid via _NET_WM_PID, which for many titles (e.g. DiRT 3)
                // arrives AFTER the window maps — so at onMapWindow assignTaskAffinity could only pin by
                // class name, which never registers a pid and left the drift checker dormant on the
                // container/shortcut CPU-list path. Now that the pid is known, re-run it so the mask is
                // (re-)applied by pid (the exe/mask the drift checker uses is captured either way).
                if (property != null && property.name == Atom.getId("_NET_WM_PID")) {
                    assignTaskAffinity(window);
                }
            }

            @Override
            public void onUnmapWindow(Window window) {
                trackPadFeedWindow(window, null);
                changeFrameRatingVisibility(window, null);
            }
        });

        if (!midiSoundFont.equals("")) {
            InputStream in = null;
            InputStream finalIn = in;
            MidiManager.OnMidiLoadedCallback callback = new MidiManager.OnMidiLoadedCallback() {
                @Override
                public void onSuccess(SF2Soundbank soundbank) {
                    midiHandler = new MidiHandler();
                    midiHandler.setSoundBank(soundbank);
                    midiHandler.start();
                }

                @Override
                public void onFailed(Exception e) {
                    try {
                        finalIn.close();
                    } catch (Exception e2) {}
                }
            };
            try {
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    in = getAssets().open(MidiManager.SF2_ASSETS_DIR + "/" + midiSoundFont);
                    MidiManager.load(in, callback);
                } else
                    MidiManager.load(new File(MidiManager.getSoundFontDir(this), midiSoundFont), callback);
            } catch (Exception e) {}
        }

        // Check if a profile is defined by the shortcut
        String controlsProfile = shortcut != null ? shortcut.getExtra("controlsProfile", "") : "";

        // Keep the running session alive while backgrounded: a REAL foreground service holds the
        // process at perceptible priority so Android's low-memory killer can't reap the guest and
        // force a shutdown on return. A plain notify() (what this used to be) does NOT protect the
        // process. See docs/session-foreground-service-plan.md.
        ContextCompat.startForegroundService(this,
                com.winhub.hernandez.core.GameSessionForegroundService.createIntent(this, shortcutName));

        Runnable runnable = () -> {
            setupUI();
            if (controlsProfile.isEmpty()) {
                // No profile defined, run the simulated dialog confirmation for input controls
                simulateConfirmInputControlsDialog();
            }
            Executors.newSingleThreadExecutor().execute(() -> {
                // Track which app-side stage is running so a failure surfaces on the right card.
                final String[] stage = { "Preparing Wine & graphics driver" };
                try {
                    // A previous session may have been killed without a clean exit (recents-swipe /
                    // background optimisation / force-stop) — none of those run onDestroy, so exit()
                    // (the only caller of terminateAllWineProcesses) never fired and the old
                    // wineserver + wine tree can still be alive as orphans. A new session then
                    // recreates the fake-input ring files those stale processes still hold mmap'd,
                    // and the stale reader faults with SIGBUS the moment the new session touches
                    // them → "The game exited before rendering / exit code 1" on the SECOND launch.
                    // Sweep before starting anything; this is a fresh launch, so every wine process
                    // found here is stale by construction. (A paused-session in-app resume never
                    // re-enters this runnable, so a live session can't be swept.)
                    sweepStaleWineProcesses();
                    // Every step of this stage prepares a WINE PREFIX: the display driver into its
                    // registry, its system files, install scripts, audio driver, refresh unlock, the
                    // Epic overlay.
                    preloaderDialog.step(2, "Preparing Wine & graphics driver…");
                    setWineDisplayDriver();   // BEFORE setupWineSystemFiles starts the first wineserver
                    // Controller input log (Log Manager, default off): this launch's recorder, before
                    // the controller setup below starts filling it.
                    com.winhub.hernandez.core.ControllerLog.begin(preferences);
                    setupWineSystemFiles();
                    // Steam install-recipe robustness pass: a steamAppId-tagged game's installScript.vdf
                    // Registry + Copy Files land in this prefix before the game boots (covers shortcuts made
                    // before the feature or re-bound to a new container). Local stages only — the Run
                    // Process step (EA Desktop installer) is driven from the Games tab's EA setup flow.
                    runSteamInstallScriptPreLaunch();
                    extractGraphicsDriverFiles();
                    changeWineAudioDriver();
                    applyGameRefreshRateUnlock();
                    provisionEpicOverlay();
                    stage[0] = "Preparing the Xbox game";
                    prepareXboxLaunch();
                    stage[0] = "Building environment";
                    setupXEnvironment();
                } catch (Exception e) {
                    Log.e("XServerDisplayActivity", "Launch setup failed at stage: " + stage[0], e);
                    final String stageName = stage[0];
                    final String detail = e.getMessage();
                    final String logDir = com.winhub.hernandez.core.LogLocation.resolveLogDir(this).getAbsolutePath();
                    final boolean loggingEnabled = isLaunchLoggingEnabled();
                    runOnUiThread(() -> {
                        cancelLaunchTimers();
                        preloaderDialog.fail(stageName, "Setup step failed", detail, logDir, loggingEnabled);
                    });
                }
            });
        };

        if (xServer.screenInfo.height > xServer.screenInfo.width) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            configChangedCallback = runnable;
        } else
              runnable.run();
    }

    // Method to parse container_id from .desktop file
    private int parseContainerIdFromDesktopFile(File desktopFile) {
        int containerId = 0;
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        containerId = Integer.parseInt(line.split(":")[1].trim());
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.e("XServerDisplayActivity", "Error parsing container_id from .desktop file", e);
            }
        }
        return containerId;
    }

    private boolean parseBoolean(String value) {
        // Return true for "true", "1", "yes" (case-insensitive)
        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }
        // Return false for any other value, including "false", "0", "no"
        return false;
    }

    // Inside XServerDisplayActivity class
    private void handleCapturedPointer(MotionEvent event) {
        boolean handled = false;

        int actionButton = event.getActionButton();
        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS:
                if (actionButton == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.LEFTDOWN, 0, 0, 0);
                    else
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                } else if (actionButton == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.RIGHTDOWN, 0, 0, 0);
                    else
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                } else if (actionButton == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MIDDLEDOWN, 0, 0, 0);
                    else
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_MIDDLE); // Add this line for middle mouse button press
                }
                handled = true;
                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                if (actionButton == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0);
                    else
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                } else if (actionButton == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0);
                    else
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                } else if (actionButton == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MIDDLEUP, 0, 0, 0);
                    else
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_MIDDLE); // Add this line for middle mouse button release
                }
                handled = true;
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                float[] transformedPoint = XForm.transformPoint(xform, event.getX(), event.getY());
                if (xServer.isRelativeMouseMovement())
                    xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, (int)transformedPoint[0], (int)transformedPoint[1], 0);
                else
                    xServer.injectPointerMoveDelta((int)transformedPoint[0], (int)transformedPoint[1]);
                handled = true;
                break;
            case MotionEvent.ACTION_SCROLL:
                float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scrollY <= -1.0f) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int)scrollY * 270);
                    else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN);
                    }
                } else if (scrollY >= 1.0f) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.WHEEL, 0, 0,(int)scrollY * 270);
                    else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP);
                    }
                }
                handled = true;
                break;
        }
    }

    private void ensurePointerCapture(String reason) {
        if ((!isRelativeMouseMovement && !cursorLock && !waylandPointerLocked) || touchpadView == null || inGameControlsEditor != null) return;

        final int[] tries = {0};
        Runnable attempt = new Runnable() {
            @Override public void run() {
                if (isFinishing() || isDestroyed()) return;
                if (inGameControlsEditor != null) return;
                if (!hasWindowFocus()) return;
                if (!touchpadView.isAttachedToWindow()) { touchpadView.postDelayed(this, 50); return; }
                if (tries[0]++ >= 40) return;

                // Make sure the view can take focus
                touchpadView.setFocusableInTouchMode(true);
                touchpadView.requestFocus();

                touchpadView.requestPointerCapture();
                touchpadView.setOnCapturedPointerListener((v, e) -> { handleCapturedPointer(e); return true; });
                pointerCaptureRequested = true;

            }
        };
        // Try quickly a few times to dodge transient focus transitions
        touchpadView.postDelayed(attempt, 50); // First attempt
    }

    @Override
    public void onResume() {
        super.onResume();
        sessionInForeground = true;
        com.winhub.hernandez.core.MusicPlayer.INSTANCE.setInGame(true);

        // A TV session that was paused but never stopped is in front again, so the teardown onPause
        // handed to onStop is moot — and there is nothing for the restore below to undo, because
        // nothing was ever suspended. It still runs, unchanged: every call in it is idempotent (SIGCONT
        // to a process that was never stopped, a GL thread told to resume when it never paused, a perf
        // profile re-applied), which is what keeps the two sides symmetric without a second flag.
        tvSuspendDeferred = false;

        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
            // Re-assert the Samsung Galaxy performance profile (released while backgrounded).
            com.winhub.hernandez.perf.galaxy.GalaxyPerfManager.resume();
        }
        // Android hides clipboard changes from background apps: re-read it now we're in front.
        if (waylandClipboard != null) waylandClipboard.refresh();
        startTime = System.currentTimeMillis();
        // Exactly one playtime ticker however we got here: the runnable re-posts itself, and a TV
        // session that was paused without ever being stopped never ran onPause's removeCallbacks, so
        // posting blind would leave a second chain running for the rest of the session.
        handler.removeCallbacks(savePlaytimeRunnable);
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);
        ProcessHelper.resumeAllWineProcesses();
        // Returning to the foreground unconditionally resumes the guest (above) — keep the paused
        // UI/state in sync so a stale pause box / Pause-button state can't linger over a running game.
        if (isPaused) setPausedState(false);
        // Re-assert the VRR vote — onStop() released it when backgrounded.
        reapplyVrr();
        // A real foreground IS the bg/fg reset an FG-reset overlay was waiting on, and it already
        // resumed the guest above — but the game SurfaceView is still torn down (its visibility
        // isn't restored automatically). Complete the reset so the surface comes back and the
        // overlay clears (no-op unless a reset was mid-flight).
        if (fgResetInProgress) resumeFromFgReset();
        // onPause() dropped the gyro listener so it can't drain the battery in the background.
        registerGyroSensor();
        // The user may have been away recalibrating (Input Controls -> Gyroscope), which writes the
        // bias to the global prefs. Re-read it once here, on the way back in, so a fresh calibration
        // takes effect without relaunching the game. Deliberately NOT on the sample path.
        reloadGyroBias();
        // Track the live panel rate again (the readout shows it while Auto is on).
        registerVrrDisplayListener();
        updateCurrentRefreshRate();
        // Re-check the external display in case a TV was (un)plugged while we were backgrounded.
        if (externalDisplayController != null) externalDisplayController.onResume();
        // Same for a session launched ON a TV: the cable can come out while the app is in the
        // background, and the display listener's notice arrives with nothing on screen to read it.
        checkSessionDisplay("resume");
        // Returning from the background can leave the guest's AAudio output route dead (the stream is
        // torn down while backgrounded) — on the TV OR the handheld. Rebuild the audio sink shortly
        // after resume so sound comes back. Only after a real background (not a PiP/dialog pause).
        if (wasBackgrounded) {
            wasBackgrounded = false;
            handler.postDelayed(this::resetGuestAudio, 600); // smooth: suspend/resume the CURRENT sink for a plain background/foreground; real route changes are recreated live by the always-registered watcher
        }
        applyHandheldDim(); // re-assert the handheld dim state after resume (brightness can reset)
        // Watch for headphone/USB/BT/HDMI plug changes during play so audio follows the new route.
        registerAudioRouteWatcher();
        // Re-arm controller-test isolation if the popup is still showing (onPause cleared the flag).
        if (XServerDialogState.INSTANCE.getActiveDialog().getValue()
                == XServerDialogState.ActiveDialog.CONTROLLER_TEST) {
            controllerTestActive = true;
        }
    }

    @Override
    public void onPause() {
        if (inGameControlsEditor != null) inGameControlsEditor.save();
        super.onPause();
        sessionInForeground = false;

        // A session playing on the TV is PAUSED the moment ANOTHER activity of this app comes to the
        // front on the handheld — the games list the user was left looking at, and now the companion
        // screen — while the game is still fully on screen over there. Device-proven on the Pocket FIT:
        // a touch alone leaves the guest running (S<s), but MainActivity on display 0 SIGSTOPs it (T<s)
        // at once, which is the user's "the game freezes but the audio keeps playing".
        //
        // So a pause on its own is NOT the signal to tear a session down: hand the whole teardown to
        // onStop, the callback that really does mean this session has left the screen. Nothing at all
        // happens here — not even releasing held inputs, because the pad is still playing the game over
        // on the TV. If a build genuinely stops us, everything below still runs, one callback later.
        // PiP is untouched (it has always frozen the guest) and a session on its way out tears down
        // here as before — there is no onStop worth waiting for.
        if (!isInPictureInPictureMode() && onTvLaunchDisplay() && !isFinishing() && !isDestroyed()) {
            tvSuspendDeferred = true;
            Log.i("XServerDisplayActivity", "TV: paused but still on display " + sessionDisplayId
                    + " — leaving the game running");
            return;
        }

        suspendSessionForBackground();
    }

    /**
     * Set when {@link #onPause} handed a TV session's teardown to {@link #onStop}. Cleared by whichever
     * callback arrives next — onStop does the work, onResume means the session never left the screen.
     */
    private boolean tvSuspendDeferred = false;

    /**
     * Freeze the guest and drop everything that must not keep running while this session is off screen:
     * exactly what {@link #onPause} used to do inline, unchanged, so the two callers cannot drift.
     *
     * <p>The restore side stays where it has always been, in {@link #onResume} (which always follows
     * onStart), and stays unconditional: every call in it is idempotent — SIGCONT to a process that was
     * never stopped, a surface resume on a thread that never paused, a perf profile re-applied —
     * and PiP relies on it running after a pause this method never saw.
     */
    private void suspendSessionForBackground() {
        // Nothing may stay held while the guest is frozen: a button still down when the game stops
        // answering comes back pressed. (Deliberately NOT done for a TV session that is only paused —
        // the game is still on screen and the controller is still playing it.)
        if (inputControlsView != null) inputControlsView.releaseAllInputs();
        if (touchpadView != null) touchpadView.releaseAllInputs();
        if (winHandler != null && inputControlsView != null) winHandler.releaseAllControllerInputs();

        // Never leave controller-test isolation latched across a background (the guest SIGSTOPs when
        // backgrounded). onResume re-arms it if the popup is still up.
        controllerTestActive = false;

        // Check if we are entering Picture-in-Picture mode
        if (!isInPictureInPictureMode()) {
            // Only pause environment and xServerView if not in PiP mode
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
                // Drop the Samsung Galaxy boost while backgrounded (the guest is frozen anyway).
                // Guarded by the same non-PiP check so PiP playback keeps its profile.
                com.winhub.hernandez.perf.galaxy.GalaxyPerfManager.pause();
            }
            // Backgrounding auto-pauses the guest; if the game is on the TV, show the pause pill there
            // so the external display reads as paused (not a frozen frame) while the user is away.
            if (externalDisplayController != null) externalDisplayController.setPaused(true);
            // Mark a real background so onResume rebuilds the audio sink (the AAudio route dies while
            // backgrounded, on TV or handheld). Distinct from PiP/dialog pauses, which don't set this —
            // and, since the TV path only gets here from onStop, from a focus flicker on the handheld.
            wasBackgrounded = true;
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
        ProcessHelper.pauseAllWineProcesses();
        unregisterGyroSensor();
    }

    // Re-reads the calibration bias from the global prefs and hands it to WinHandler. GyroCalibrator
    // still honours the device stamp, so a bias restored from another phone comes back 0.
    private void reloadGyroBias() {
        if (winHandler == null || gyroSensor == null) return;
        float[] bias = new float[2];
        GyroCalibrator.loadBias(this, bias);
        winHandler.setGyroBias(bias[0], bias[1]);
    }

    // Gyro listener register/unregister — both are idempotent and a no-op without a sensor.
    //
    // The mode branch lives HERE and nowhere else: rate mode registers the gyroscope and its samples
    // land in WinHandler.updateGyroData, orientation mode registers the rotation vector and its samples
    // land in updateGyroOrientation. Neither sample path knows the other exists.
    //
    // The "already registered" early-out has to compare the sensor TYPE, not just the flag: switching
    // mode from the in-game drawer calls straight back in here, and a plain flag check would return
    // with the previous sensor still registered — the new mode would then never receive a sample.
    private void registerGyroSensor() {
        if (sensorManager == null) return;
        boolean orientationMode = winHandler != null
            && winHandler.getGyroMode() == WinHandler.GYRO_MODE_ORIENTATION
            && gyroRotationSensor != null;
        Sensor sensor = orientationMode ? gyroRotationSensor : gyroSensor;
        if (sensor == null) return;
        if (gyroListenerRegistered && registeredGyroSensorType == sensor.getType()) return;
        // Unregister-then-register so a rapid run of mode taps can't leak a second listener. Both calls
        // are on the main thread, so no sample can slip in between them.
        if (gyroListenerRegistered) sensorManager.unregisterListener(gyroListener);
        sensorManager.registerListener(gyroListener, sensor, SensorManager.SENSOR_DELAY_GAME);
        gyroListenerRegistered = true;
        registeredGyroSensorType = sensor.getType();
        // Whichever direction we switched, the deflection and the captured centre belong to the old
        // sensor — drop both rather than carry them across.
        if (winHandler != null) winHandler.resetGyroRuntimeState();
    }

    // Rotation-vector sample -> yaw/pitch in radians, remapped so the axes follow the SCREEN rather
    // than the device. The remap is mandatory here (unlike rate mode, which reads raw device axes):
    // the activity is sensorLandscape, so ROTATION_90 and ROTATION_270 both happen, and a
    // portrait-resolution container forces portrait — all four cases are reachable.
    private void computeGyroOrientation(float[] rotationVector) {
        if (winHandler == null) return;
        // Some Samsung builds hand out 5+ components and getRotationMatrixFromVector then throws
        // IllegalArgumentException. Copy the first four into the preallocated scratch instead — a
        // try/catch on a 200 Hz path would be the wrong shape of fix.
        float[] vector = rotationVector;
        if (rotationVector.length > 4) {
            System.arraycopy(rotationVector, 0, gyroRotationVector, 0, 4);
            vector = gyroRotationVector;
        }
        SensorManager.getRotationMatrixFromVector(gyroRotationMatrix, vector);
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        switch (cachedDisplayRotation) {
            case Surface.ROTATION_90:
                axisX = SensorManager.AXIS_Y;
                axisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                axisX = SensorManager.AXIS_MINUS_X;
                axisY = SensorManager.AXIS_MINUS_Y;
                break;
            case Surface.ROTATION_270:
                axisX = SensorManager.AXIS_MINUS_Y;
                axisY = SensorManager.AXIS_X;
                break;
            default:
                break;
        }
        SensorManager.remapCoordinateSystem(gyroRotationMatrix, axisX, axisY, gyroRemappedMatrix);
        SensorManager.getOrientation(gyroRemappedMatrix, gyroOrientationAngles);
        // gyroOrientationAngles = [azimuth (yaw), pitch, roll]; roll is unused.
        winHandler.updateGyroOrientation(gyroOrientationAngles[0], gyroOrientationAngles[1]);
    }

    // Refreshed on rotation events only — never from the sample path, where it would be a binder call
    // per sample. Volatile because the sensor callback reads it.
    private void refreshCachedDisplayRotation() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.Display display = getDisplay();
                if (display != null) {
                    cachedDisplayRotation = display.getRotation();
                    return;
                }
            }
            cachedDisplayRotation = getWindowManager().getDefaultDisplay().getRotation();
        }
        catch (Exception e) {
            // Leave the previous value in place; a stale remap beats a crashed activity.
        }
    }

    // Write-back for the per-game gyro settings: the shortcut when the game was launched from one,
    // otherwise the container. Same key on both sides, so the launch resolver reads back exactly what
    // was written here (see the applyGyroTuning block in setupUI).
    private void persistGyroExtra(String key, String value) {
        if (shortcut != null) {
            shortcut.putExtra(key, value);
            shortcut.saveData();
        }
        else if (container != null) {
            container.putExtra(key, value);
            container.saveData();
        }
    }

    private void persistStickTuning(StickShaper.Tuning t) {
        if (shortcut != null) {
            shortcut.putExtra(StickShaper.KEY_LEFT_DEADZONE, String.valueOf(t.leftDeadzone));
            shortcut.putExtra(StickShaper.KEY_LEFT_SENSITIVITY, String.valueOf(t.leftSensitivity));
            shortcut.putExtra(StickShaper.KEY_RIGHT_DEADZONE, String.valueOf(t.rightDeadzone));
            shortcut.putExtra(StickShaper.KEY_RIGHT_SENSITIVITY, String.valueOf(t.rightSensitivity));
            shortcut.putExtra(StickShaper.KEY_SMOOTH_EDGE, t.smoothEdge ? "1" : "0");
            shortcut.saveData();
        }
        else if (container != null) {
            container.setStickLeftDeadzone(t.leftDeadzone);
            container.setStickLeftSensitivity(t.leftSensitivity);
            container.setStickRightDeadzone(t.rightDeadzone);
            container.setStickRightSensitivity(t.rightSensitivity);
            container.setStickSmoothEdge(t.smoothEdge);
            container.saveData();
        }
    }

    private void unregisterGyroSensor() {
        if (sensorManager == null || !gyroListenerRegistered) return;
        sensorManager.unregisterListener(gyroListener);
        gyroListenerRegistered = false;
        registeredGyroSensorType = -1;
        // Clear the runtime state on the way out: with the listener gone nothing arrives to un-latch a
        // toggled-on gyro, so the overlay would stay frozen into the last gamepad state the game saw.
        if (winHandler != null) winHandler.resetGyroRuntimeState();
    }


    // Writes the win-fg layer config (TOML) into the guest HOME so it is present before the first
    // swapchain present. The layer hot-reloads this file, so it doubles as the live-control path
    // (see in-game drawer). win-fg keys: enabled (0/1), multiplier (2-4), flowScale, model (3-4).
    // multiplier: 0 = frame gen off (Off in the menu / not yet enabled), else 2-4.
    // (win-fg is the clean-room replacement for the removed bionic-fg layer; the fpsLimiter args are
    //  retained for call-site compatibility — the host pacer owns limiting, not this layer.)
    private void writeWinFgConfig(int multiplier, float flowScale, boolean fpsLimiterEnabled, int fpsLimitValue, int model, int perfPreset) {
        try {
            File configDir = new File(imageFs.home_path, ".config/win-fg");
            configDir.mkdirs();
            File confFile = new File(configDir, "conf.toml");
            boolean on = multiplier >= 2;
            // The layer will now present multiplier frames per game frame: the pad frame feed
            // counts one of them (see padFeedPresentsPerFrame).
            padFeedPresentsPerFrame = on ? Math.max(2, Math.min(4, multiplier)) : 1;
            String toml = "# Written by Bannerlator (per-container frame generation)\n"
                    + "enabled = " + (on ? "1" : "0") + "\n"
                    + "multiplier = " + Math.max(2, Math.min(4, on ? multiplier : 2)) + "\n"
                    + "flowScale = " + String.format(java.util.Locale.US, "%.2f", flowScale) + "\n"
                    + "model = " + Math.max(3, Math.min(4, model)) + "\n"
                    // Performance preset (0 Quality / 1 Balanced (default) / 2 Performance). The layer
                    // hot-reloads + self-rebuilds on this key — live-tunable from the in-game FG drawer.
                    + "perf_preset = " + Math.max(0, Math.min(2, perfPreset)) + "\n";
            FileUtils.writeString(confFile, toml);
        }
        catch (Exception e) {
            Log.e("WinFG", "Failed to write win-fg conf.toml", e);
        }
    }

    // lsfg-vk (GameNative fork) conf.toml. The layer watches this file's mtime in its present hook
    // and forces a swapchain recreate when it changes, re-reading multiplier/flow — so rewriting it
    // from the in-game menu re-applies live. exe MUST equal the LSFG_PROCESS env value.
    void writeLsfgConfig(int multiplier, float flowScale, String dllPath, boolean performanceMode) {
        try {
            File configDir = new File(imageFs.home_path, ".config/lsfg-vk");
            configDir.mkdirs();
            File confFile = new File(configDir, "conf.toml");
            // Guest layer present mode: mailbox while generating, fifo in passthrough (GameNative parity).
            // The layer already paces itself against the vsync clock we publish (vsync.txt); mesa's FIFO
            // queue underneath the layer breaks the display cadence and backs frames up in the host
            // compositor (device-proven SmoMoState over-queue -> generated frames present black).
            boolean generating = multiplier >= 2;
            String toml = "# Written by Bannerlator (per-container lsfg-vk frame generation)\n"
                    + "version = 1\n\n"
                    + "[global]\n"
                    + "dll = \"" + dllPath + "\"\n"
                    + "no_fp16 = false\n\n"
                    + "[[game]]\n"
                    + "exe = \"bannerlator-lsfg\"\n"
                    + "multiplier = " + multiplier + "\n"
                    + "flow_scale = " + String.format(java.util.Locale.US, "%.2f", flowScale) + "\n"
                    + "performance_mode = " + performanceMode + "\n"
                    + "hdr_mode = false\n"
                    + "experimental_present_mode = " + (generating ? "\"mailbox\"" : "\"fifo\"") + "\n";
            FileUtils.writeString(confFile, toml);
        }
        catch (Exception e) {
            Log.e("lsfg-vk", "Failed to write lsfg-vk conf.toml", e);
        }
    }

    // === lsfg-vk vsync clock ===================================================================
    // The lsfg-vk fork layer phase-locks its frame pacing to a display vsync grid published here as
    // vsync.txt (sibling of conf.toml). Without it the layer free-runs and over-queues the host
    // compositor — device-proven root cause of the LSFG black-frame flicker: Qualcomm's composer
    // spams "SmoMoState::FrameIsLate: queued_frames >= 2" while frame-gen pushes ~124fps unpaced, so
    // dropped/stale generated frames reach the glass BLACK. GameNative ships the byte-identical .so
    // yet doesn't flicker because it publishes this clock; we did not. Rewritten once a second off the
    // UI thread. Choreographer frame timestamps are CLOCK_MONOTONIC — the clock the layer paces with.
    private android.os.Handler vsyncClockHandler;
    private java.util.concurrent.ExecutorService vsyncWriteExecutor;

    // ===================== LSFG Native (compositor-side) =====================
    // Runs the Lossless Scaling chain inside our own Vulkan compositor. Unlike
    // lsfg-vk nothing is injected into the container, so there is no conf.toml,
    // no ENABLE_LSFG, no vsync clock and no presentation reset on a multiplier
    // change - the generator shares the compositor's command stream.

    /** Renderer for this session, or null when it is not the Vulkan one. */
    private com.winhub.hernandez.renderer.vulkan.VulkanRenderer vulkanRendererOrNull() {
        HostRenderer r = (xServerView != null) ? xServerView.getRenderer() : null;
        return (r instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer)
            ? (com.winhub.hernandez.renderer.vulkan.VulkanRenderer) r : null;
    }

    /**
     * Build the SPIR-V cache from the user's Lossless.dll if needed, then arm
     * the renderer. Cache building is slow on a first run - the DXBC chain is
     * translated on device - so it happens off the main thread and arms only
     * once it has actually succeeded.
     */
    private void prepareLsfgNative() {
        if (!com.winhub.hernandez.core.LsfgNative.isDllAvailable(this)) {
            Log.w("XServerDisplayActivity",
                "LSFG Native selected but no Lossless.dll imported (Settings) - leaving frame gen off");
            lsfgNativeStatus = com.winhub.hernandez.core.LsfgNative.STATUS_NOT_INSTALLED;
            refreshNativeFgAvailability(true, 0);
            return;
        }
        // "LSFG Auto Enable" is the container's launch switch for BOTH LSFG engines: ON arms the
        // saved multiplier from frame one, OFF keeps the safe default (chain loaded, generating only
        // when the player turns it on in the drawer). This used to be hardcoded to 0, which is why
        // selecting LSFG + Auto Enable and pressing Save never applied at launch.
        final int launchMult = container.isLsfgAutoEnable()
            ? Math.max(2, container.getFrameGenMultiplier()) : 0;
        final float flow = container.getFrameGenFlowScale();

        new Thread(() -> {
            final int status = com.winhub.hernandez.core.LsfgNative.ensureCache(
                XServerDisplayActivity.this, /*preferFp16=*/false);
            runOnUiThread(() -> {
                lsfgNativeStatus = status;
                if (status != com.winhub.hernandez.core.LsfgNative.STATUS_OK) {
                    Log.e("XServerDisplayActivity", "LSFG Native unavailable: "
                        + com.winhub.hernandez.core.LsfgNative.explain(status));
                    refreshNativeFgAvailability(true, 0);
                    return;
                }
                // Launch prep runs before XServerView (and its Vulkan renderer) exists, so arming
                // now would be dropped. Wait for the renderer when we have a multiplier to arm.
                if (launchMult >= 2) armLsfgNativeWhenReady(flow, NATIVE_FG_ARM_TRIES);
                else applyLsfgNative(launchMult, flow);
                refreshNativeFgAvailability(true, NATIVE_FG_CHECK_TRIES);
            });
        }, "lsfg-native-cache").start();
    }

    /**
     * The experimental tuning that rides along with flow scale: the capture height
     * (0 = panel, -1 = the game's own height, which the renderer resolves from the X screen it
     * was created with - shortcut screen-size override and render scale included). It comes
     * from the drawer state, which is seeded from the container and only ever non-default
     * while FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED is on.
     */
    private void pushNativeFgTuning(com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr,
                                    float flowScale) {
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        int captureHeight = 0;
        if (com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED)
            captureHeight = Container.fgCaptureHeightFor(s.getFgCaptureResolution().getValue());
        vkr.setFrameGenTuning(flowScale, currentDisplayRefreshHz(), captureHeight);
    }

    /**
     * Arm the launch multiplier once the Vulkan renderer exists. The cache-build callback can run
     * before XServerView is created (the cache is cached on later launches, so it returns fast);
     * applyLsfgNative would log "no Vulkan renderer" and silently leave frame gen off. The
     * multiplier is read from the drawer at arm time: if the player turns FG off (or picks a
     * different level) while we wait, that live choice wins over the launch seed.
     */
    private void armLsfgNativeWhenReady(float flowScale, int triesLeft) {
        com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr = vulkanRendererOrNull();
        if (vkr != null) {
            int multiplier = XServerDrawerState.INSTANCE.getFrameGenMultiplier().getValue();
            if (multiplier >= 2) applyLsfgNative(multiplier, flowScale);
            return;
        }
        if (triesLeft > 0) {
            fgCheckHandler.postDelayed(
                () -> armLsfgNativeWhenReady(flowScale, triesLeft - 1), 250);
        } else {
            Log.w("XServerDisplayActivity",
                "LSFG Native auto-enable: no Vulkan renderer after waiting - not armed");
        }
    }

    /** Point the renderer at the cache and arm it at `multiplier` (0 = off). */
    private void applyLsfgNative(int multiplier, float flowScale) {
        if (waylandMode) {
            applyWaylandFrameGen(multiplier, flowScale, resolvedFrameGenModel(), resolvedFrameGenPerfPreset());
            return;
        }
        com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr = vulkanRendererOrNull();
        if (vkr == null) {
            Log.w("XServerDisplayActivity",
                "applyLsfgNative(" + multiplier + ") ignored - no Vulkan renderer");
            return;
        }
        Log.i("XServerDisplayActivity", "applyLsfgNative: multiplier=" + multiplier
            + " flow=" + flowScale + " refresh=" + currentDisplayRefreshHz()
            + " capture=" + XServerDrawerState.INSTANCE.getFgCaptureResolution().getValue());
        vkr.setLsfgCachePath(
            com.winhub.hernandez.core.LsfgNative.cacheFile(this).getAbsolutePath());
        pushNativeFgTuning(vkr, flowScale);
        vkr.setFrameGenArmed(multiplier >= 2, multiplier);
        // Present mode has to follow the armed state: fifo while multiplying,
        // back to the user's choice when off.
        applyEffectivePresentMode();
        applyNativeFgLocks(multiplier >= 2);
        if (multiplier >= 2) startLsfgStatsReadout(); else stopLsfgStatsReadout();
    }

    /**
     * Win-FG Native at launch: select the engine and arm it OFF. Nothing to
     * build or import - the chain is embedded - so this is immediate. Unlike
     * LSFG Native, win-fg has no auto-enable setting: it always starts off.
     */
    private void prepareWinFgNative() {
        applyWinFgNative(0, container.getFrameGenFlowScale(),
            resolvedFrameGenModel(), resolvedFrameGenPerfPreset());
        refreshNativeFgAvailability(true, NATIVE_FG_CHECK_TRIES);
    }

    // ── Native frame gen that cannot run: say so instead of failing silently ──
    // A community report (Adreno 710): LSFG Native did nothing because the Renderer
    // Driver was the stock "System" driver (Vulkan 1.1); it needs 1.3. The chain ran
    // in no frame, the drawer still offered 2x/3x/4x, and only logcat knew why.

    // LsfgNative.ensureCache result for this session; -1 = not checked yet.
    private int lsfgNativeStatus = -1;
    // One launch-time notice per session; the drawer keeps showing the reason.
    private boolean nativeFgProblemAnnounced = false;
    // The renderer and its swapchain come up after launch prep; retry this many
    // times, 1.5 s apart, before giving up on a verdict (the drawer then stays quiet).
    private static final int NATIVE_FG_CHECK_TRIES = 10;
    // 40 x 250 ms = 10 s for the Vulkan renderer to appear before an auto-arm is abandoned.
    private static final int NATIVE_FG_ARM_TRIES = 40;
    private final android.os.Handler fgCheckHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * Why the selected native frame-gen engine cannot run in this session, in plain
     * words with the fix. "" = nothing wrong; null = not known yet (the renderer or its
     * swapchain is still coming up).
     */
    private String nativeFgProblemReason() {
        final String engine = resolvedFrameGenEngine();
        final boolean lsfg = "lsfg-native".equals(engine);
        final boolean winfg = "bionic".equals(engine) && winFgNativeSession;
        if (!lsfg && !winfg) return "";
        final String name = lsfg ? "LSFG Native" : "Win-FG Native";
        if (lsfg && lsfgNativeStatus > com.winhub.hernandez.core.LsfgNative.STATUS_OK)
            return "LSFG Native can't start: "
                + com.winhub.hernandez.core.LsfgNative.explain(lsfgNativeStatus) + ".";
        if (waylandMode) {
            // The Wayland compositor is always Vulkan (Turnip via adrenotools); the verdict comes
            // from its frame-generation bridge once its device is up (-1 until then -> retry).
            switch (com.winhub.hernandez.wayland.WaylandCompositor.nativeFrameGenProblem()) {
                case com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_DRIVER: {
                    String caps = com.winhub.hernandez.wayland.WaylandCompositor.nativeFrameGenCapsReason();
                    boolean version = caps != null && caps.contains("Vulkan version below");
                    return name + " can't run on this Renderer Driver: "
                        + (version ? "it needs Vulkan 1.3, which this driver doesn't provide."
                                   : "this driver is missing a feature it needs.")
                        + " In this container's settings, set Renderer Driver to a Turnip driver,"
                        + " then relaunch the game.";
                }
                case com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_START_FAILED:
                    return name + " couldn't start in the Wayland compositor. In this container's"
                        + " settings, try a Turnip driver under Renderer Driver, then relaunch the game.";
                case com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_NONE:
                    return "";
                default:
                    return null;
            }
        }
        if (!"vulkan".equalsIgnoreCase(resolvedRenderer()))
            return getString(R.string.frame_generation_requires_vulkan)
                + " Change Renderer in this container's settings, then relaunch the game.";
        com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr = vulkanRendererOrNull();
        if (vkr == null) return null;
        switch (vkr.getFrameGenProblem()) {
            case com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_DRIVER: {
                String caps = vkr.getLsfgCapsReason();
                boolean version = caps != null && caps.contains("Vulkan version below");
                // The experimental compat switch is the other way out for LSFG Native on a
                // Vulkan 1.1/1.2 driver; name it only while it is off and actually offered.
                boolean offerCompat = lsfg && version
                    && com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED
                    && container != null && !container.isLsfgVk11Compat();
                return name + " can't run on this Renderer Driver: "
                    + (version ? "it needs Vulkan 1.3, which this driver doesn't provide."
                               : "this driver is missing a feature it needs.")
                    + " In this container's settings, set Renderer Driver to a Turnip driver,"
                    + (offerCompat
                        ? " or turn on \"" + getString(R.string.lsfg_vk11_compat) + "\","
                        : "")
                    + " then relaunch the game.";
            }
            case com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_START_FAILED:
                return name + " couldn't start on this Renderer Driver. In this container's"
                    + " settings, try a Turnip driver under Renderer Driver, then relaunch the game.";
            case com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_NONE:
                return "";
            default:
                return null;
        }
    }

    /** Work out whether native frame gen can run and publish it; retries while unknown. */
    private void refreshNativeFgAvailability(boolean announce, int triesLeft) {
        String reason = nativeFgProblemReason();
        if (reason == null) {
            if (triesLeft > 0)
                fgCheckHandler.postDelayed(() -> refreshNativeFgAvailability(announce, triesLeft - 1), 1500);
            return;
        }
        setNativeFgProblem(reason, announce);
    }

    private void setNativeFgProblem(String reason, boolean announce) {
        com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr = vulkanRendererOrNull();
        String detail = "";
        if (!reason.isEmpty() && waylandMode) {
            if (com.winhub.hernandez.wayland.WaylandCompositor.nativeFrameGenProblem()
                    == com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_DRIVER)
                detail = com.winhub.hernandez.wayland.WaylandCompositor.nativeFrameGenCapsReason();
        } else if (!reason.isEmpty() && vkr != null
                && vkr.getFrameGenProblem() == com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_DRIVER) {
            detail = vkr.getLsfgCapsReason();
        }
        if (detail == null) detail = "";
        XServerDrawerState.INSTANCE.setFgUnavailable(reason, detail);
        if (reason.isEmpty()) return;
        Log.w("XServerDisplayActivity", "native frame gen unavailable: " + reason
            + (detail.isEmpty() ? "" : " [" + detail + "]"));
        if (announce && !nativeFgProblemAnnounced) {
            nativeFgProblemAnnounced = true;
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        }
    }

    /** The armed engine stopped or never started generating: publish why and go back to Off,
     *  so the drawer and the HUD stop claiming frame gen is running. */
    private void onNativeFgFailed() {
        String reason = nativeFgProblemReason();
        if (reason == null || reason.isEmpty())
            reason = "Frame generation couldn't start on this device.";
        setNativeFgProblem(reason, true);
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        s.setFrameGenMultiplier(0);
        float flow = s.getFrameGenFlowScale().getValue();
        if ("lsfg-native".equals(resolvedFrameGenEngine())) applyLsfgNative(0, flow);
        else applyWinFgNative(0, flow, resolvedFrameGenModel(), resolvedFrameGenPerfPreset());
    }

    /** Win-FG Native is Performance-only; see applyWinFgNative. 2 = Performance. */
    private static final int WINFG_PERF_PRESET = 2;

    /** Select win-fg in the renderer, push its knobs, and arm it at `multiplier` (0 = off). */
    private void applyWinFgNative(int multiplier, float flowScale, int model, int perfPreset) {
        if (waylandMode) {
            applyWaylandFrameGen(multiplier, flowScale, model, perfPreset);
            return;
        }
        com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr = vulkanRendererOrNull();
        if (vkr == null) {
            Log.w("XServerDisplayActivity",
                "applyWinFgNative(" + multiplier + ") ignored - no Vulkan renderer");
            return;
        }
        Log.i("XServerDisplayActivity", "applyWinFgNative: multiplier=" + multiplier
            + " flow=" + flowScale + " model=" + model
            + " preset=" + WINFG_PERF_PRESET + " (pinned; requested " + perfPreset + ")"
            + " refresh=" + currentDisplayRefreshHz());
        vkr.setFrameGenEngine(com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_ENGINE_WINFG);
        // Win-FG Native runs on the Performance preset permanently (user decision,
        // 2026-09-09). The preset picks the finest pyramid level the flow search
        // reaches, and changing it live tears down and rebuilds the whole chain -
        // visible in a device log as three rebuilds in twenty seconds while the
        // chips were being tried. Pinned here rather than only hiding the UI, so a
        // container or shortcut still carrying an older value cannot reinstate it.
        vkr.setWinFgTuning(model, WINFG_PERF_PRESET);
        // Capture height 0: Win-FG shares the composite ring with LSFG Native but has no
        // capture-resolution control, so it always runs at panel resolution.
        vkr.setFrameGenTuning(flowScale, currentDisplayRefreshHz(), 0);
        vkr.setFrameGenArmed(multiplier >= 2, multiplier);
        // Identical follow-through to LSFG Native: fifo while multiplying, the
        // limiter/VRR locks, and the base->shown readout.
        applyEffectivePresentMode();
        applyNativeFgLocks(multiplier >= 2);
        if (multiplier >= 2) startLsfgStatsReadout(); else stopLsfgStatsReadout();
    }

    /**
     * Wayland: select the native engine in the compositor's frame-generation bridge
     * (waylandcomp/src/framegen_bridge.c), push its knobs and arm it at {@code multiplier}
     * (0 = off). Same engines, same tuning and the same follow-through as the X11 native path
     * (limiter/VRR locks, the base->shown readout); only the present-mode step is absent, the
     * Wayland compositor is FIFO by construction, which is what the multi-present pacing needs.
     * Every setter is a value store on the native side, so this is safe before the compositor
     * thread is up (launch) and from the drawer mid-game. bionic-fg (the guest-side win-fg
     * layer) is X11-only: on Wayland "bionic" always means Win-FG Native.
     */
    private void applyWaylandFrameGen(int multiplier, float flowScale, int model, int perfPreset) {
        final boolean lsfg = "lsfg-native".equals(resolvedFrameGenEngine());
        Log.i("XServerDisplayActivity", "applyWaylandFrameGen: engine=" + (lsfg ? "lsfg-native" : "winfg-native")
            + " multiplier=" + multiplier + " flow=" + flowScale
            + (lsfg ? "" : " model=" + model + " preset=" + WINFG_PERF_PRESET + " (pinned; requested " + perfPreset + ")")
            + " refresh=" + currentDisplayRefreshHz());
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetFrameGenEngine(lsfg
            ? com.winhub.hernandez.wayland.WaylandCompositor.FG_ENGINE_LSFG
            : com.winhub.hernandez.wayland.WaylandCompositor.FG_ENGINE_WINFG);
        if (lsfg) {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetLsfgCachePath(
                com.winhub.hernandez.core.LsfgNative.cacheFile(this).getAbsolutePath());
        } else {
            // Performance preset pinned exactly as applyWinFgNative does (user decision, 2026-09-09).
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetWinFgTuning(model, WINFG_PERF_PRESET);
        }
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetFrameGenTuning(flowScale, currentDisplayRefreshHz());
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetFrameGenArmed(multiplier >= 2, multiplier);
        applyNativeFgLocks(multiplier >= 2);
        if (multiplier >= 2) startLsfgStatsReadout(); else stopLsfgStatsReadout();
    }

    // Live readout for the FG drawer, polled while the native engine is armed.
    private android.os.Handler lsfgStatsHandler;
    private Runnable lsfgStatsTick;

    /**
     * Poll the renderer for what frame generation is actually achieving and
     * push it to the drawer. Worth showing because the multiplier is a ceiling
     * to earn, not a setting that is obeyed - the governor grants an extra
     * generated frame only when it measurably helps, so "4x selected" and
     * "2x running" is a normal, correct state rather than a bug.
     */
    private void startLsfgStatsReadout() {
        if (lsfgStatsHandler != null) return;
        lsfgStatsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        lsfgStatsTick = new Runnable() {
            @Override public void run() {
                com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr = vulkanRendererOrNull();
                // Armed but the engine can't generate here (driver lacks what it needs, or
                // it failed to start): stop pretending, say why, and go back to Off.
                if ((waylandMode || vkr != null) && nativeFgLocksHeld) {
                    int p = waylandMode
                        ? com.winhub.hernandez.wayland.WaylandCompositor.nativeFrameGenProblem()
                        : vkr.getFrameGenProblem();
                    if (p == com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_DRIVER
                            || p == com.winhub.hernandez.renderer.vulkan.VulkanRenderer.FG_PROBLEM_START_FAILED) {
                        onNativeFgFailed();
                        return;
                    }
                }
                // Wayland: the same six numbers, measured by the compositor's bridge (presents
                // counted per source frame, generated frames included).
                float[] st = waylandMode
                    ? com.winhub.hernandez.wayland.WaylandCompositor.nativeFrameGenStats()
                    : (vkr != null) ? vkr.getFrameGenStats() : null;
                if (st != null && st.length >= 5) {
                    // st[1] is what is actually planned for this frame. st[0] is
                    // the governor's accepted level, which is meaningless while
                    // the governor is off.
                    final int trusted = (int) st[1];
                    // st[2] is the LSFG engine's own source rate and is 0 for
                    // win-fg, which has no engine here. The HUDs do not need it
                    // - they compare against their own counter - so only the
                    // drawer line is suppressed in that case.
                    final float realFps = st[2], shownFps = st[3];
                    final boolean haveEngineRates = realFps > 0f;
                    String text;
                    if (shownFps <= 0f) {
                        text = "measuring…";
                    } else {
                        text = String.format(java.util.Locale.US,
                            "%.0f real → %.0f shown  (%dx", realFps, shownFps, trusted + 1);
                        if (st[4] >= 3f) text += ", throttling";
                        text += ")";
                        // What each generated frame costs on the GPU. This is
                        // the number that says whether a game has room for
                        // frame generation before generation silently stops.
                        if (st.length >= 6 && st[5] > 0f)
                            text += String.format(java.util.Locale.US, "  %.1f ms/frame GPU", st[5]);
                    }
                    if (haveEngineRates) XServerDrawerState.INSTANCE.setFrameGenReadout(text);
                    // Also feed the in-game HUD. Its own counter ticks once per
                    // GUEST frame and therefore cannot see anything added after
                    // the game - it read 30 while the panel was showing 118,
                    // which made a working feature look like a broken one. Only
                    // a system overlay could tell the truth until now.
                    if (fusionHud != null) fusionHud.setPresentedFps(shownFps);
                }
                if (lsfgStatsHandler != null) lsfgStatsHandler.postDelayed(this, 1000);
            }
        };
        lsfgStatsHandler.postDelayed(lsfgStatsTick, 1000);
    }

    private void stopLsfgStatsReadout() {
        // 0 puts the HUD back to plain guest-rate reporting.
        if (fusionHud != null) fusionHud.setPresentedFps(0f);
        if (lsfgStatsHandler != null && lsfgStatsTick != null)
            lsfgStatsHandler.removeCallbacks(lsfgStatsTick);
        lsfgStatsHandler = null;
        lsfgStatsTick = null;
        XServerDrawerState.INSTANCE.setFrameGenReadout("");
    }

    // The user's limiter choice from before native FG took it over, so it comes
    // back exactly as it was when it is turned off.
    private boolean nativeFgSavedLimiterOn = false;
    private int     nativeFgSavedLimit     = 0;
    private boolean nativeFgLocksHeld = false;

    /**
     * While LSFG Native or Win-FG Native generates: FPS limiter locked ON. An
     * uncapped guest times the multiplier overruns the panel and FIFO stalls the
     * compositor. If no cap was set, 30 is used: it is the case this was proven on.
     *
     * Auto refresh (VRR) used to be locked OFF here too. Now it is switched ON
     * for the frame-gen session instead, whatever the saved setting says, so
     * applyVrr fits the display to cap x multiplier (pickNativeFgRefresh). The
     * user can turn it off during frame gen; that opt-out is remembered on the
     * game's SHORTCUT only (FG_AUTO_OPT_OUT), never the container, so other games
     * made from the same container keep getting it. When frame gen stops, Auto
     * goes back to the saved setting.
     */
    private void applyNativeFgLocks(boolean lock) {
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        if (lock && !nativeFgLocksHeld) {
            nativeFgSavedLimiterOn    = s.getFpsLimiterEnabled().getValue();
            nativeFgSavedLimit        = s.getFpsLimit().getValue();
            nativeFgLocksHeld = true;

            boolean savedAuto = resolvedMatchRefreshRate();
            boolean canMatch = com.winhub.hernandez.widget.XServerView.isDisplayVrrCapable(
                getWindowManager().getDefaultDisplay());
            nativeFgAutoOn = canMatch && !fgAutoOptedOut();
            s.setMatchRefreshRate(nativeFgAutoOn);
            s.setFgAutoTurnedOn(nativeFgAutoOn && !savedAuto);
            s.setFgAutoPerGame(shortcut != null);

            int cap = nativeFgSavedLimit > 0 ? nativeFgSavedLimit : 30;
            s.setFpsLimiterEnabled(true);
            s.setFpsLimit(cap);
            s.setNativeFgLocks(true);
            Log.i("XServerDisplayActivity", "native-fg locks ON: limiter=" + cap
                + " (was limiter=" + (nativeFgSavedLimiterOn ? nativeFgSavedLimit : 0) + ")"
                + " auto=" + nativeFgAutoOn + " (saved=" + savedAuto + " optOut=" + fgAutoOptedOut()
                + " canMatch=" + canMatch + ")");
            applyFpsLimit(cap);
        } else if (!lock && nativeFgLocksHeld) {
            nativeFgLocksHeld = false;
            nativeFgAutoOn = false;
            s.setNativeFgLocks(false);
            s.setFgAutoTurnedOn(false);
            s.setMatchRefreshRate(resolvedMatchRefreshRate());
            s.setFpsLimiterEnabled(nativeFgSavedLimiterOn);
            s.setFpsLimit(nativeFgSavedLimit);
            Log.i("XServerDisplayActivity", "native-fg locks OFF: restored limiter="
                + (nativeFgSavedLimiterOn ? nativeFgSavedLimit : 0) + " auto=" + resolvedMatchRefreshRate());
            applyFpsLimit(nativeFgSavedLimiterOn && nativeFgSavedLimit > 0 ? nativeFgSavedLimit : 0);
        }
    }

    // Auto (match FPS) for the current native frame-gen session (see applyNativeFgLocks).
    private boolean nativeFgAutoOn = false;

    /** Shortcut extra: the user turned Auto off during frame gen for this game. */
    private static final String FG_AUTO_OPT_OUT = "fgAutoRefreshOptOut";

    private boolean fgAutoOptedOut() {
        return shortcut != null && "1".equals(shortcut.getExtra(FG_AUTO_OPT_OUT, "0"));
    }

    /** Auto (match FPS) as it applies right now: the frame-gen session's value while native
     *  frame gen holds its locks, the saved container/shortcut setting otherwise. */
    private boolean autoRefreshActive() {
        return nativeFgLocksHeld ? nativeFgAutoOn : resolvedMatchRefreshRate();
    }

    /** Extra room below an exact display fit - see pacedLimitWithSlack. */
    private static final float NATIVE_FG_FIT_SLACK = 0.003f;

    /** Display rates at the current resolution, exact and ascending. */
    private float[] displayRatesPrecise() {
        return com.winhub.hernandez.widget.XServerView.getSupportedRefreshRatesPrecise(
            getWindowManager().getDefaultDisplay());
    }

    /**
     * The display rate native frame gen should run at for `wanted` = cap x
     * multiplier, from the display's own list: exactly `wanted` if it has it,
     * else the closest rate ABOVE it (a rate below would overrun the panel).
     * 0 = nothing at or above: the display stays at its top rate and the
     * drawer's over-limit warning takes over.
     *
     * Never a multiple. LSFG Native and Win-FG Native present each real frame's
     * burst on back-to-back refreshes, so 30 x 2 on 120 Hz shows as 1,3 (quick
     * pair, long pause); only an exact fit is even.
     */
    private float pickNativeFgRefresh(int wanted) {
        if (wanted <= 0) return 0f;
        for (float r : displayRatesPrecise()) {
            if (Math.abs(r - wanted) < 0.5f || r > wanted) return r;
        }
        return 0f;
    }

    /** The rate the display will be asked to run at while native frame gen generates. */
    private float nativeFgDisplayRate(int cap, int mult) {
        float[] rates = displayRatesPrecise();
        float top = rates.length > 0 ? rates[rates.length - 1] : pickHighestRefreshRate();
        if (container != null && autoRefreshActive()) {
            float r = pickNativeFgRefresh(cap * mult);
            return r > 0f ? r : top;
        }
        int manual = resolvedManualRefreshRate();
        if (manual > 0) {
            for (float r : rates) if (Math.abs(r - manual) < 0.5f) return r;
            return manual;
        }
        return top;
    }

    /**
     * FPS to pace the game at. When cap x multiplier fills the display exactly,
     * run the game a hair under the cap (0.3%) so the display always has room.
     * Two things push an exact fit over: the game's timer and the display's
     * clock are different clocks (a 59.94 Hz panel is 0.1% short of 30 x 2), and
     * the limiter lets a late frame be followed by an early one. At an exact fit
     * either one queues a frame that never drains - a frame of lag, then a skip.
     * With the slack the worst case is one repeated refresh every few seconds.
     * The cap the user sees and saves stays a whole number.
     */
    private float pacedLimitWithSlack(int fps) {
        if (fps <= 0 || !(nativeFrameGenEngine() && frameGenMultipliesDisplay())) return fps;
        int mult = XServerDrawerState.INSTANCE.getFrameGenMultiplier().getValue();
        float display = nativeFgDisplayRate(fps, mult);
        if (display <= 0f || Math.abs(fps * mult - display) >= 0.5f) return fps;
        return display * (1f - NATIVE_FG_FIT_SLACK) / mult;
    }

    /** The panel's real refresh rate; the pacer never generates above it. */
    private float currentDisplayRefreshHz() {
        try {
            // The panel's HIGHEST mode, not getRefreshRate(). The latter reports
            // what the platform currently grants this window, and on device it
            // read 60 on a 144 Hz panel mid-game (a frame-rate override, not a
            // mode switch). The engine uses this as its headroom ceiling, so a
            // 60 there quietly caps 4x to 2x. The panel can always be driven at
            // its top mode under FIFO; that is the number the ceiling wants.
            float best = (float) pickHighestRefreshRate();
            android.view.Display d = getWindowManager().getDefaultDisplay();
            float hz = (d != null) ? d.getRefreshRate() : 0f;
            float out = Math.max(best, hz);
            return out > 1f ? out : 0f;
        } catch (Throwable t) {
            return 0f;
        }
    }

    private void startVsyncClock() {
        stopVsyncClock();
        if (imageFs == null) return;
        final File vsyncFile = new File(imageFs.home_path, ".config/lsfg-vk/vsync.txt");
        if (vsyncWriteExecutor == null) {
            vsyncWriteExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "lsfg-vsync");
                t.setDaemon(true);
                return t;
            });
        }
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        vsyncClockHandler = h;
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (vsyncClockHandler != h) return;
                android.view.Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
                    if (vsyncClockHandler != h) return;
                    float refreshRate = 60f;
                    try {
                        android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
                        if (wm != null && wm.getDefaultDisplay() != null) {
                            float rr = wm.getDefaultDisplay().getRefreshRate();
                            if (rr > 1f) refreshRate = rr;
                        }
                    } catch (Exception ignored) {}
                    final long periodNs = (long) (1_000_000_000.0 / refreshRate);
                    vsyncWriteExecutor.execute(() -> {
                        try {
                            File parent = vsyncFile.getParentFile();
                            if (parent != null) parent.mkdirs();
                            FileUtils.writeString(vsyncFile, "vsync_ns=" + frameTimeNanos + "\nperiod_ns=" + periodNs + "\n");
                        } catch (Exception ignored) {}
                    });
                });
                h.postDelayed(this, 1000);
            }
        };
        h.post(tick);
    }

    private void stopVsyncClock() {
        if (vsyncClockHandler != null) {
            vsyncClockHandler.removeCallbacksAndMessages(null);
            vsyncClockHandler = null;
        }
    }

    // === ReShade (vkBasalt) per-game effect config =============================================
    // Writes ONE merged vkBasalt.conf when a ReShade effect is selected, folding in the existing
    // CAS/DLS sharpness path so the two never fight over the env (the old code set an inline
    // VKBASALT_CONFIG for CAS; ReShade needs a config FILE for the source/include/texture paths and
    // uniform values — so when both are wanted, everything goes through the single file here).
    //
    // Layout: the chosen effect's whole subfolder is COPIED into the container's guest HOME
    // (.config/vkBasalt/effects/<name>/) so every path in the conf is HOST-ABSOLUTE inside rootDir
    // (this fork does NOT proot — proven by the spike), and any .fxh includes / textures travel with
    // it. effects = <reshade>:cas  (sharpen LAST, the usual order). Returns the host-absolute conf
    // path for VKBASALT_CONFIG_FILE, or null when no ReShade effect is selected (caller then keeps
    // the legacy inline CAS path untouched).
    // Launch-time write: honor the persisted master (enableOnLaunch) so an in-game ReShade OFF sticks.
    private String writeVkBasaltConfig() { return writeVkBasaltConfig(resolveReshade().masterEnabled, true); }
    private String writeVkBasaltConfig(boolean enableOnLaunch) { return writeVkBasaltConfig(enableOnLaunch, false); }

    // Tier 1 multi-effect loadout. Every loadout effect is COMPILED into the vkBasalt chain up front
    // (effects = e1:e2:..:cas); the per-effect `<ei>_enabled = 0|1` flag decides which of them present
    // (1 = active, 0 = bypassed) so the in-game drawer can flip them LIVE with no recompile. Master
    // enableOnLaunch (whole chain) is independent: our patched libvkbasalt watches this conf's mtime
    // and re-reads enableOnLaunch into presentEffect (passthrough vs effect) AND each `_enabled` flag
    // WITHOUT recompiling, so a drawer change here turns effects on/off live.
    //
    // restage: re-copy each effect's drop-in folder into the guest HOME (.config/vkBasalt/effects/<name>/)
    // so path edits take effect. True on launch; false for live in-game apply (folders don't change
    // mid-session, so we skip the IO and just rewrite the conf → mtime bump → layer reloads).
    private String writeVkBasaltConfig(boolean enableOnLaunch, boolean restage) {
        if (!reshadeSupported()) return null; // WineD3D/GL/GDI titles can't carry the layer

        ResolvedReshade rr = resolveReshade();
        if (rr.loadout.isEmpty()) return null; // no loadout / all "None" -> legacy inline-CAS path

        try {
            File configDir = new File(imageFs.home_path, ".config/vkBasalt");
            File effectsRoot = new File(configDir, "effects");

            StringBuilder sb = new StringBuilder();
            sb.append("# Written by Bannerlator (per-game ReShade loadout via vkBasalt)\n");

            StringBuilder chain = new StringBuilder();       // e1:e2:...:en (CAS appended after)
            StringBuilder effectLines = new StringBuilder();  // per-effect: <ei> = fx + uniforms + _enabled
            List<String> stagedDirs = new ArrayList<>();
            int idx = 0;

            for (com.winhub.hernandez.reshade.ReshadeLoadout.Entry entry : rr.loadout) {
                com.winhub.hernandez.reshade.ReshadeManager.ReshadeEffect effect =
                    com.winhub.hernandez.reshade.ReshadeManager.findEffect(this, entry.name);
                if (effect == null) {
                    // Skip-and-continue: one missing effect must not kill the rest of the chain.
                    Log.w("VkBasalt", "ReShade loadout effect not found, skipping: " + entry.name);
                    continue;
                }

                // The effect technique name vkBasalt keys on (stable, lower-case, syntax-safe).
                String effectKey = effect.name.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(java.util.Locale.US);
                if (effectKey.isEmpty()) effectKey = "reshade" + idx;

                File destDir = new File(effectsRoot, effect.name);
                if (restage || !destDir.isDirectory()) {
                    FileUtils.delete(destDir);
                    destDir.mkdirs();
                    if (!FileUtils.copy(effect.dir, destDir)) {
                        Log.e("VkBasalt", "Failed to stage ReShade effect folder: " + effect.dir);
                        continue;
                    }
                }
                String destDirPath = destDir.getAbsolutePath();           // host-absolute
                File fxDest = new File(destDir, effect.fxFile.getName());
                String fxPath = fxDest.getAbsolutePath();
                stagedDirs.add(destDirPath);

                if (chain.length() > 0) chain.append(":");
                chain.append(effectKey);

                // Per-effect source path.
                effectLines.append(effectKey).append(" = ").append(fxPath).append("\n");

                // Per-uniform overrides for THIS effect (nested {"<effect>":{...}}, or migrated flat
                // legacy), layered over the .fx defaults. seedValues resolves the value-map key scheme;
                // formatUniformLine writes the "<effectKey>_<uniform>[_c]" keys the layer reads.
                org.json.JSONObject paramJson = com.winhub.hernandez.reshade.ReshadeLoadout.paramsForEffect(
                        rr.paramsJson, effect.name, rr.nested, rr.legacyEffect);
                HashMap<String, Float> resolved = new HashMap<>();
                for (com.winhub.hernandez.reshade.ReshadeManager.ReshadeParam p : effect.params) {
                    com.winhub.hernandez.reshade.ReshadeManager.seedValues(p, paramJson, resolved);
                }
                for (com.winhub.hernandez.reshade.ReshadeManager.ReshadeParam p : effect.params) {
                    effectLines.append(formatUniformLine(effectKey, p, resolved));
                }

                // NEW per-effect enable flag the patch reads (1 = active, 0 = bypassed).
                effectLines.append(effectKey).append("_enabled = ").append(entry.enabled ? "1" : "0").append("\n");
                idx++;
            }

            if (chain.length() == 0) {
                Log.w("VkBasalt", "No ReShade loadout effects could be staged; skipping conf");
                return null;
            }

            // Sharpen LAST: the loadout chain first, then the existing CAS/DLS chain (if any).
            if (vkbasaltConfig != null && !vkbasaltConfig.isEmpty()) {
                appendSharpnessFromInline(sb, chain);
            }
            sb.append("effects = ").append(chain).append("\n");
            sb.append(effectLines);

            // Texture/include search paths. Co-located #includes already resolve relative to each
            // staged .fx (device-proven), so these are a fallback — colon-join every staged dir
            // (vkBasalt splits these list paths on ':', same as the effects list). Single-effect
            // loadouts collapse to exactly one path (identical to the pre-Tier-1 conf).
            String pathList = android.text.TextUtils.join(":", stagedDirs);
            sb.append("reshadeTexturePath = ").append(pathList).append("\n");
            sb.append("reshadeIncludePath = ").append(pathList).append("\n");

            sb.append("toggleKey = Home\n");
            sb.append("enableOnLaunch = ").append(enableOnLaunch ? "True" : "False").append("\n");

            File confFile = new File(configDir, "vkBasalt.conf");
            FileUtils.writeString(confFile, sb.toString());
            String confPath = confFile.getAbsolutePath();
            Log.d("VkBasalt", "Wrote ReShade loadout conf (" + chain + ") -> " + confPath);
            return confPath;
        } catch (Exception e) {
            Log.e("VkBasalt", "Failed to write ReShade vkBasalt.conf", e);
            return null;
        }
    }

    // Fold the legacy inline CAS/DLS string ("effects=cas;casSharpness=..;dlsSharpness=..;
    // dlsDenoise=..;enableOnLaunch=True") into the merged file: append the sharpen effect to the
    // chain and copy its sharpness keys as conf lines.
    private void appendSharpnessFromInline(StringBuilder sb, StringBuilder chain) {
        String sharpenEffect = null;
        for (String kv : vkbasaltConfig.split(";")) {
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            String k = kv.substring(0, eq).trim();
            String v = kv.substring(eq + 1).trim();
            if (k.equals("effects")) sharpenEffect = v;
            else if (k.equals("casSharpness") || k.equals("dlsSharpness") || k.equals("dlsDenoise"))
                sb.append(k).append(" = ").append(v).append("\n");
        }
        if (sharpenEffect != null && !sharpenEffect.isEmpty() && !sharpenEffect.equals("none"))
            chain.append(":").append(sharpenEffect);
    }

    // Single source of truth for how a reflected uniform value is written into vkBasalt.conf.
    // Our patched libvkbasalt (patches/vkbasalt-reshade-livereload.patch, ReshadeUniform::setFromConfig)
    // reads per-uniform overrides under "<effectKey>_<uniform>" for single-component uniforms and
    // "<effectKey>_<uniform>_<c>" for each component of a multi-component one (the SAME effectKey used
    // in `effects = <effectKey>`), pushing the value into the live UBO. `values` is the resolved
    // value-map from ReshadeManager.seedValues (keys "<uniform>" or, for COLOR, "<uniform>_<c>").
    //   BOOL  -> <effectKey>_<uniform> = 0|1          (read as getOption<bool>)
    //   COMBO -> <effectKey>_<uniform> = <index>      (read as getOption<int32_t>)
    //   INT   -> <effectKey>_<uniform> = <int>
    //   COLOR -> <effectKey>_<uniform>_0..N-1 = <f>   (read per-component as getOption<float>)
    //   FLOAT -> <effectKey>_<uniform> = <f>
    private String formatUniformLine(String effectKey, com.winhub.hernandez.reshade.ReshadeManager.ReshadeParam p,
                                     Map<String, Float> values) {
        String base = effectKey + "_" + p.name;
        switch (p.type) {
            case BOOL:
                return base + " = " + (getF(values, p.name, p.defaultValue) >= 0.5f ? "1" : "0") + "\n";
            case COMBO:
            case INT:
                return base + " = " + Math.round(getF(values, p.name, p.defaultValue)) + "\n";
            case COLOR: {
                StringBuilder sb = new StringBuilder();
                for (int c = 0; c < p.components; c++) {
                    String k = p.name + "_" + c;
                    float def = (p.componentDefaults != null && c < p.componentDefaults.length)
                            ? p.componentDefaults[c] : 0f;
                    sb.append(base).append("_").append(c).append(" = ")
                      .append(String.format(java.util.Locale.US, "%.4f", getF(values, k, def))).append("\n");
                }
                return sb.toString();
            }
            case FLOAT:
            default:
                return base + " = " + String.format(java.util.Locale.US, "%.4f", getF(values, p.name, p.defaultValue)) + "\n";
        }
    }

    private static float getF(Map<String, Float> values, String key, float fallback) {
        Float v = values != null ? values.get(key) : null;
        return v != null ? v : fallback;
    }

    // Seed the in-game ReShade drawer controls from the resolved launch config. Runs in setupUI
    // (after container/shortcut are assigned). enableOnLaunch=True, so the loadout is ON at launch;
    // each effect's own <ei>_enabled flag decides which of them present.
    private void seedReshadeDrawerState(XServerDialogState ds) {
        boolean supported = reshadeSupported();
        ds.setReshadeSupported(supported);

        ResolvedReshade rr = supported ? resolveReshade() : null;
        java.util.ArrayList<com.winhub.hernandez.ui.ReshadeLoadoutItem> items = new java.util.ArrayList<>();
        if (rr != null) {
            for (com.winhub.hernandez.reshade.ReshadeLoadout.Entry entry : rr.loadout) {
                com.winhub.hernandez.reshade.ReshadeManager.ReshadeEffect effect =
                    com.winhub.hernandez.reshade.ReshadeManager.findEffect(this, entry.name);
                if (effect == null) continue; // only tune effects actually present in the drop-in folder
                // Values: nested per-effect JSON (or migrated flat legacy) layered over the .fx defaults.
                org.json.JSONObject saved = com.winhub.hernandez.reshade.ReshadeLoadout.paramsForEffect(
                        rr.paramsJson, effect.name, rr.nested, rr.legacyEffect);
                HashMap<String, Float> values = new HashMap<>();
                for (com.winhub.hernandez.reshade.ReshadeManager.ReshadeParam p : effect.params) {
                    com.winhub.hernandez.reshade.ReshadeManager.seedValues(p, saved, values);
                }
                items.add(new com.winhub.hernandez.ui.ReshadeLoadoutItem(
                        effect.name, entry.enabled, effect.params, values));
            }
        }
        ds.setReshadeMode(rr != null ? rr.mode : com.winhub.hernandez.reshade.ReshadeLoadout.MODE_SOLO);
        ds.setReshadeLoadout(items);
        // Master (whole-chain) on/off: ON whenever there's something to show, unless the user
        // explicitly turned ReShade off in-game last session (persisted rr.masterEnabled == false).
        ds.setReshadeMasterEnabled(!items.isEmpty() && (rr == null || rr.masterEnabled));
    }

    // SINGLE pluggable seam for ReShade live-apply. Persists the full drawer loadout snapshot
    // (per-effect enabled + per-effect values + mode + master on/off) to the active store, then
    // rewrites the merged vkBasalt.conf. Our patched libvkbasalt watches the conf mtime and reloads
    // enableOnLaunch (whole-chain present) + each <ei>_enabled flag + the uniform values WITHOUT a
    // recompile, so the change takes effect live. restage=false: the effect folders are already
    // staged from launch, so we only rewrite the conf.
    private void applyReshadeLive(boolean masterEnabled, String mode,
                                  List<com.winhub.hernandez.ui.ReshadeLoadoutItem> items) {
        try {
            java.util.ArrayList<com.winhub.hernandez.reshade.ReshadeLoadout.Entry> entries = new java.util.ArrayList<>();
            org.json.JSONObject nestedParams = new org.json.JSONObject();
            if (items != null) {
                for (com.winhub.hernandez.ui.ReshadeLoadoutItem it : items) {
                    entries.add(new com.winhub.hernandez.reshade.ReshadeLoadout.Entry(it.getName(), it.getEnabled()));
                    Map<String, Float> vals = it.getValues();
                    if (vals != null && !vals.isEmpty()) {
                        org.json.JSONObject effJson = new org.json.JSONObject();
                        for (Map.Entry<String, Float> e : vals.entrySet())
                            effJson.put(e.getKey(), (double) e.getValue());
                        nestedParams.put(it.getName(), effJson);
                    }
                }
            }
            String loadoutJson = com.winhub.hernandez.reshade.ReshadeLoadout.serialize(entries);
            String paramsJson = nestedParams.length() == 0 ? null : nestedParams.toString();
            String modeStr = com.winhub.hernandez.reshade.ReshadeLoadout.normalizeMode(mode);
            // Keep the legacy single field roughly coherent for any old reader (new resolution
            // prefers reshadeLoadout when present).
            String firstEffect = entries.isEmpty() ? "None" : entries.get(0).name;

            // Persist to the SAME source resolveReshade() will read next launch (the authoritative
            // owner for this session): the shortcut only when it already owns reshade as a unit,
            // otherwise the container. Writing by the same shortcutOwnsReshade() discriminator keeps
            // write-target == read-source, so an in-game change (loadout/mode/params/enabled + the
            // master switch) is restored on relaunch instead of reverting to the pre-launch config.
            if (shortcutOwnsReshade()) {
                shortcut.putExtra("reshadeLoadout", entries.isEmpty() ? null : loadoutJson);
                shortcut.putExtra("reshadeMode", modeStr);
                shortcut.putExtra("reshadeParams", paramsJson);
                shortcut.putExtra("reshadeEffect", firstEffect);
                shortcut.putExtra("reshadeMasterEnabled", masterEnabled ? null : "0");
                shortcut.saveData();
            } else if (container != null) {
                container.setReshadeLoadout(entries.isEmpty() ? null : loadoutJson);
                container.setReshadeMode(modeStr);
                container.setReshadeParams(paramsJson);
                container.setReshadeEffect(firstEffect);
                container.setReshadeMasterEnabled(masterEnabled);
                container.saveData();
            }
        } catch (JSONException ignored) {}
        // masterEnabled -> enableOnLaunch: the drawer "ReShade" master switch off writes
        // enableOnLaunch=False (whole-chain passthrough); per-effect flags ride <ei>_enabled.
        writeVkBasaltConfig(masterEnabled);

        // The conf is now committed (mtime bumped -> the patched libvkbasalt hot-reloads it). In the
        // freeze-frame preview mode (Live preview OFF) this is where a committed change enters/pulses
        // the preview-pause so the new look is revealed on a frozen scene.
        handleReshadePreviewChange();
    }

    // ─────────────────────────── ReShade freeze-frame preview ───────────────────────────
    // Called after each COMMITTED ReShade change (effect toggle or slider release -> applyReshadeLive).
    // Live preview ON: no-op (the game keeps running, changes apply live). OFF: freeze the game on the
    // FIRST change (SIGSTOP + pause box), and PULSE on every subsequent change (brief SIGCONT so 1–2
    // frames render the change, then SIGSTOP again). Runs on the drawer's UI thread.
    private void handleReshadePreviewChange() {
        if (reshadeLivePreview) return;
        if (isPaused) {
            // Already frozen (this preview, or a manual Pause) -> reveal the change with a brief pulse.
            reshadePreviewPaused = true;
            pulseReshadePreview();
        } else {
            // First committed change while the game was running -> freeze. The change was applied to
            // the live conf while still running, so the frames just before the freeze already show it.
            reshadePreviewPaused = true;
            setPausedState(true);
        }
    }

    // One brief resume so the committed ReShade change actually renders, then re-freeze. Counts real
    // presented frames via the PresentExtension observer (N=RESHADE_PULSE_TARGET_PRESENTS); a time
    // fallback re-freezes if the game isn't presenting (no present callback fires). Serialized so
    // overlapping changes can't stack SIGCONT/SIGSTOP pairs. Never flips isPaused (the UI stays
    // "frozen" the whole time) — the pulse is purely at the process-signal level.
    private void pulseReshadePreview() {
        if (reshadePulseInProgress) return;   // debounce: a pulse is already running
        reshadePulseInProgress = true;

        final com.winhub.hernandez.xserver.extensions.PresentExtension pe = (xServer != null)
            ? xServer.getExtension(com.winhub.hernandez.xserver.extensions.PresentExtension.MAJOR_OPCODE)
            : null;

        final java.util.concurrent.atomic.AtomicBoolean finished =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable[] refreezeHolder = new Runnable[1];
        final Runnable refreeze = () -> {
            if (!finished.compareAndSet(false, true)) return;   // present-callback vs fallback: run once
            if (pe != null) pe.setPresentListener(null);
            handler.removeCallbacks(refreezeHolder[0]);
            // Re-freeze only if we're still meant to be paused (tap-to-resume may have won the race).
            if (isPaused) ProcessHelper.pauseAllWineProcesses();
            reshadePulseInProgress = false;
        };
        refreezeHolder[0] = refreeze;

        if (pe != null) {
            final java.util.concurrent.atomic.AtomicInteger presents =
                new java.util.concurrent.atomic.AtomicInteger(0);
            pe.setPresentListener(() -> {
                if (presents.incrementAndGet() >= RESHADE_PULSE_TARGET_PRESENTS)
                    runOnUiThread(refreeze);   // marshal back to the handler thread
            });
        }
        // Let the game run so it presents the new look, with a time-based safety net.
        ProcessHelper.resumeAllWineProcesses();
        handler.postDelayed(refreeze, RESHADE_PULSE_FALLBACK_MS);
    }

    // Single source of truth for the frozen/paused state. Suspends/resumes the guest, clears the
    // preview-ownership flag on any resume, and mirrors to BOTH Compose holders (the drawer Pause
    // button + the centered pause box). Everything that pauses/resumes (manual Pause, the ReShade
    // preview, the box tap, lifecycle) routes through here so the flag never disagrees with reality.
    private static String audioDriverLabel(String d) {
        if ("alsa".equals(d)) return "ALSA";
        if ("pulseaudio".equals(d)) return "PulseAudio";
        if ("directaudio".equals(d)) return "DirectAudio";
        return d == null ? "" : d;
    }

    // Engine tag for the per-scope env keys BANNER_AUDIO_<ENG>_* — must match AudioSettingsDialog.engTag.
    private static String audioEngTag(String d) {
        if ("alsa".equals(d)) return "ALSA";
        if ("directaudio".equals(d)) return "DIRECT";
        return "PULSE";
    }

    // ALSA and DirectAudio both drive AAudio directly and default to PERFORMANCE_MODE_NONE (proven
    // crackle-free); PulseAudio defaults to LOW_LATENCY/Auto.
    private static boolean audioNoneDefault(String d) {
        return "alsa".equals(d) || "directaudio".equals(d);
    }

    private static Integer envInt(EnvVars ev, String key) {
        if (ev == null || !ev.has(key)) return null;
        try { return Integer.parseInt(ev.get(key).trim()); } catch (Exception ex) { return null; }
    }

    private String audioPrefsName(String driverId) {
        if ("alsa".equals(driverId)) return "banner_audio_alsa";
        if ("directaudio".equals(driverId)) return "banner_audio_directaudio";
        return "banner_audio_pulseaudio";
    }

    /** DirectAudio's shipping buffer in ms (DA_DEFAULT_MS in winedirectaudio.drv) => ~33 ms total. */
    private static final int DIRECT_DEFAULT_MS = 12;

    /** The latency field means the guest winepulse buffer on Pulse (100 ms) but the DRIVER's own buffer
     *  on DirectAudio, where 100 ms would be eight times its default - so the default cannot be shared.
     *  ALSA ignores the field entirely. */
    private static int defaultLatencyMsec(String driverId) {
        return "directaudio".equals(driverId) ? DIRECT_DEFAULT_MS : 100;
    }

    // Reseed the launching engine's EPHEMERAL runtime prefs (banner_audio_<engine>) from the resolved
    // per-scope config: engine-scoped env keys BANNER_AUDIO_<ENG>_* (already merged shortcut-over-
    // container), else the engine default. A FULL write EVERY launch — the runtime file carries no
    // cross-launch/cross-game memory; persistence lives only in the per-scope env. Reads only THIS
    // engine's keys, so Pulse and ALSA never touch each other's config.
    private void seedAudioPrefsForLaunch(EnvVars ev, String driverId) {
        String kp = "BANNER_AUDIO_" + audioEngTag(driverId) + "_";
        int defPerf = audioNoneDefault(driverId) ? 0 : 1;         // ALSA/DirectAudio NONE (proven) vs Pulse Auto
        // ALSA keeps Stable (its floor is unmeasured, and it pays an extra server hop);
        // DirectAudio moves to Auto - Stable is 83 ms of total latency against 33 ms
        // measured holding through 7 minutes of gameplay on one underrun. See
        // AudioSettingsDialogKt.defaultPresetFor, which must agree with this.
        String defPreset = "alsa".equals(driverId) ? "stable" : "auto";
        boolean hasPreset   = ev != null && ev.has(kp + "PRESET");
        boolean hasAdaptive = ev != null && ev.has(kp + "ADAPTIVE");
        Integer perf = envInt(ev, kp + "PERF");
        Integer bf   = envInt(ev, kp + "BF");
        Integer mbf  = envInt(ev, kp + "MBF");
        Integer lat  = envInt(ev, kp + "LAT");
        getSharedPreferences(audioPrefsName(driverId), MODE_PRIVATE).edit()
                .putString("preset", hasPreset ? ev.get(kp + "PRESET") : defPreset)
                .putInt("perf_mode", perf != null ? perf : defPerf)
                .putBoolean("adaptive", hasAdaptive ? !"0".equals(ev.get(kp + "ADAPTIVE")) : true)
                .putInt("buffer_frames", bf != null ? bf : 0)
                .putInt("max_buffer_frames", mbf != null ? mbf : 0)
                .putInt("latency_msec", lat != null ? lat : defaultLatencyMsec(driverId))
                .apply();
    }

    // Persist an in-game audio change to the LAUNCHING SHORTCUT only (never the container, never another
    // game). Reads the just-updated runtime prefs for the active engine and writes them into the
    // shortcut's env under that engine's key prefix, replacing only this engine's keys (the other
    // engine's config + all non-audio env survive). Mirrors resetPerfKey's putExtra+saveData pattern.
    // The exact engine-scoped keys persistAudioToShortcut re-emits. Everything else carrying the same
    // prefix belongs to whoever typed it — the audio cog owns the keys it writes, not the whole
    // BANNER_AUDIO_<ENG>_ namespace. Blanket-dropping the prefix silently deleted hand-set driver knobs
    // (DirectAudio's _MS/_MAXMS/_DECAY and its watchdog/decay tuning) the first time the cog was applied:
    // they survived every launch, then vanished on the first in-game audio change. Keeping this list
    // narrow means a knob added to a driver later is preserved without touching this code.
    private static final String[] COG_OWNED_AUDIO_KEYS = { "PRESET", "PERF", "ADAPTIVE", "LAT", "BF", "MBF" };

    private static boolean isCogOwnedAudioKey(String tok, String kp) {
        if (!tok.startsWith(kp)) return false;
        int eq = tok.indexOf('=');
        String name = eq >= 0 ? tok.substring(kp.length(), eq) : tok.substring(kp.length());
        for (String k : COG_OWNED_AUDIO_KEYS) if (k.equals(name)) return true;
        return false;
    }

    private void persistAudioToShortcut(String driverId) {
        if (shortcut == null) return;   // no per-game store (e.g. direct/installer launch)
        try {
            android.content.SharedPreferences p = getSharedPreferences(audioPrefsName(driverId), MODE_PRIVATE);
            String kp = "BANNER_AUDIO_" + audioEngTag(driverId) + "_";
            int perf = p.getInt("perf_mode", audioNoneDefault(driverId) ? 0 : 1);
            boolean adaptive = p.getBoolean("adaptive", true);
            int bf = p.getInt("buffer_frames", 0), mbf = p.getInt("max_buffer_frames", 0);
            int lat = p.getInt("latency_msec", defaultLatencyMsec(driverId));
            String preset = p.getString("preset", audioNoneDefault(driverId) ? "stable" : "auto");
            StringBuilder sb = new StringBuilder();
            String existing = shortcut.getExtra("envVars");
            if (existing != null) for (String tok : existing.split(" ")) {
                if (tok.isEmpty() || isCogOwnedAudioKey(tok, kp)) continue;   // drop only what we rewrite below
                if (sb.length() > 0) sb.append(' ');
                sb.append(tok);
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append(kp).append("PRESET=").append(preset).append(' ')
              .append(kp).append("PERF=").append(perf).append(' ')
              .append(kp).append("ADAPTIVE=").append(adaptive ? 1 : 0).append(' ')
              .append(kp).append("LAT=").append(lat);
            if (bf > 0)  sb.append(' ').append(kp).append("BF=").append(bf);
            if (mbf > 0) sb.append(' ').append(kp).append("MBF=").append(mbf);
            shortcut.putExtra("envVars", sb.toString());
            shortcut.saveData();
        } catch (Throwable t) {
            android.util.Log.w("ALSAAudio", "persistAudioToShortcut failed", t);
        }
    }

    // Resolve the effective ALSA config from the ALSA engine's OWN prefs file (banner_audio_alsa — a
    // per-game cog was written here at launch if present, else it's the remembered/in-game config) and
    // push it to the native ALSA player. Defaults to NONE — the device-proven crackle-free mode — for a
    // fresh file. Never reads Pulse's file, so no cross-engine bleed. Safe before streams open (config is
    // process-global) and again on in-game apply, where the bumped generation reopens streams live.
    private void applyAlsaAudioConfig() {
        try {
            android.content.SharedPreferences p = getSharedPreferences("banner_audio_alsa", MODE_PRIVATE);
            String preset = p.getString("preset", "stable");
            int perf, adaptive, bf, mbf;
            if ("custom".equals(preset)) {
                perf = p.contains("perf_mode") ? p.getInt("perf_mode", 0) : 0; // ALSA proven default = NONE
                adaptive = p.getBoolean("adaptive", true) ? 1 : 0;
                bf  = p.getInt("buffer_frames", 0);
                mbf = p.getInt("max_buffer_frames", 0);
            } else {
                // Named presets come from alsaPresetConfig(), so the greyed fine-tune rows in the cog
                // show the values actually pushed to the native player. It gives the two SAFER rungs a
                // real buffer - without it Auto, Balanced and Stable differed only by performance mode
                // - while leaving Auto/Low on the native default (framesPerBurst * 2), which is both
                // device-adaptive and lower than any fixed number we could pick here.
                com.winhub.hernandez.ui.components.AudioConfig c =
                        com.winhub.hernandez.ui.components.AudioSettingsDialogKt.alsaPresetConfig(preset);
                perf = c.getPerfMode();
                adaptive = c.getAdaptive() ? 1 : 0;
                bf  = c.getBufferFrames();
                mbf = c.getMaxBufferFrames();
            }
            com.winhub.hernandez.alsaserver.ALSAClient.nativeSetAudioConfig(perf, adaptive, bf, mbf);
        } catch (Throwable t) {
            android.util.Log.w("ALSAAudio", "applyAlsaAudioConfig failed", t);
        }
    }

    // Resolve the DirectAudio cog preset to the engine-scoped env the unixlib reads at stream open
    // (BANNER_AUDIO_DIRECT_PERF/BF/ADAPTIVE/MBF). Unlike ALSA/Pulse — which read prefs into a native
    // player / PULSE_LATENCY_MSEC — the DirectAudio driver takes its config straight from the guest env,
    // so without this the cog never reaches it and it runs the driver's compiled defaults. Named presets
    // map to a CONCRETE buffer and ALWAYS force LOW_LATENCY (device-proven; NONE = normal-priority thread
    // the guest preempts under box64/FEX -> choppy). Custom honours the user's own knobs. Initial buffers
    // stay within the proven-safe LOW_LATENCY envelope (<=62.5 ms; the adaptive path grows on xruns). The
    // cog is authoritative for DirectAudio, so we OVERWRITE any keys the shared-preset env carried (its
    // perfMode/latency are tuned for ALSA/Pulse). Reads only DirectAudio's own prefs -> no cross-engine bleed.
    private void applyDirectAudioConfig(EnvVars envVars) {
        try {
            android.content.SharedPreferences p = getSharedPreferences("banner_audio_directaudio", MODE_PRIVATE);
            String preset = p.getString("preset", "stable");
            int perf, bf, mbf = 0, ms = 0; boolean adaptive;
            if ("custom".equals(preset)) {
                perf = p.getInt("perf_mode", 1);
                bf   = p.getInt("buffer_frames", 0);
                mbf  = p.getInt("max_buffer_frames", 0);
                ms   = p.getInt("latency_msec", DIRECT_DEFAULT_MS);
                adaptive = p.getBoolean("adaptive", true);
            } else {
                // Named presets come from directPresetConfig() rather than a switch of their own, so
                // the greyed fine-tune rows in the audio cog show these exact values instead of a
                // second copy that can drift from them. It forces LOW_LATENCY for every preset -
                // device-proven, NONE is a normal-priority thread the guest preempts under box64/FEX.
                com.winhub.hernandez.ui.components.AudioConfig c =
                        com.winhub.hernandez.ui.components.AudioSettingsDialogKt.directPresetConfig(preset);
                perf = c.getPerfMode();
                bf   = c.getBufferFrames();
                mbf  = c.getMaxBufferFrames();
                adaptive = c.getAdaptive();
            }
            // POWER_SAVING (2) churns on DirectAudio under box64/FEX (stream errors + reopens); the cog no
            // longer offers it, but coerce here too so a legacy pref or hand-set env can't select it.
            if (perf == 2) perf = 1;
            envVars.put("BANNER_AUDIO_DIRECT_PERF", String.valueOf(perf));
            envVars.put("BANNER_AUDIO_DIRECT_ADAPTIVE", adaptive ? "1" : "0");
            // Exactly ONE buffer key, never both. _MS overrides _BF inside the driver, so emitting the
            // pair would make the frame stepper silently lose to the millisecond slider. Frames win when
            // set - that stepper is the advanced control and 0 means "not set" - otherwise the latency
            // slider is what the user actually moved, and on DirectAudio it IS the buffer. Named presets
            // carry their own frame count and never reach here with ms.
            if (bf  > 0) envVars.put("BANNER_AUDIO_DIRECT_BF", String.valueOf(bf));
            else if (ms > 0) envVars.put("BANNER_AUDIO_DIRECT_MS", String.valueOf(ms));
            if (mbf > 0) envVars.put("BANNER_AUDIO_DIRECT_MBF", String.valueOf(mbf));
            // Live-config "mailbox": tell the driver where to watch for in-game changes, and clear any
            // stale file from a previous session so THIS launch starts from the env above, not an old
            // override. The driver reads config from the env at stream open; the mailbox lets an in-game
            // cog save reach the ALREADY-RUNNING driver (see writeDirectAudioRuntime / onReapplyAudio).
            java.io.File rt = new java.io.File(getFilesDir(), "banner_audio_directaudio.rt");
            rt.delete();
            envVars.put("BANNER_AUDIO_DIRECT_RUNTIME", rt.getAbsolutePath());
        } catch (Throwable t) {
            android.util.Log.w("DirectAudio", "applyDirectAudioConfig failed", t);
        }
    }

    // True when the resolved launch env (shortcut over container) carries BANNER_AUDIO_DIRECT_MIC=1 — the
    // opt-in mic flag the DirectAudio driver reads to open an AAudio INPUT stream. It's a presence flag
    // (the editors write "=1" or remove it), and a shortcut's putAll only overrides keys it actually
    // carries, so "present in either scope" is exactly the merged launch-env result. Read straight from
    // the container/shortcut sources here because the merged EnvVars isn't built until the background
    // launch thread, and this runs on the main thread (where a permission request is valid).
    private boolean directMicRequestedInEnv() {
        // container.getEnvVars() and shortcut.getExtra("envVars") are both the raw space-joined env
        // string, so the same presence check covers either scope.
        if (DirectAudioSupport.isMicEnabledInEnv(container.getEnvVars())) return true;
        if (shortcut != null && DirectAudioSupport.isMicEnabledInEnv(shortcut.getExtra("envVars"))) return true;
        return false;
    }

    // Map a Proton layer name to the bundled DirectAudio asset set, or null if the build is unsupported.
    // The driver is BUILD-SPECIFIC (a driver built against Wine major X only initializes on Wine X), but
    // device-proven interchangeable WITHIN the Wine-11 point-release family (11.0-1/-3/-5): the same
    // complete 3-file set drives any arm64ec 11.0-x layer. Wine 10 (10.0-4/10.0-34) has its own ABI, its own set.
    private static String directAudioAssetDir(String layerName) {
        if (layerName == null) return null;
        if (layerName.contains("11.0-")) return "wine11";   // any arm64ec Wine-11 point release
        if (layerName.contains("10.0-4")) return "wine10";  // the arm64ec Wine-10 build we ship for
        if (layerName.contains("10.0-34")) return "wine10"; // Wine-10 family (same major as 10.0-4) -> same set
        return null;                                         // not a supported build -> leave its own driver
    }

    // Deliver the bundled, BUILD-MATCHED DirectAudio driver into the shared Proton layer at launch, so a
    // user's dormant/old winedirectaudio.drv auto-upgrades to the APK's bundled driver before the guest
    // loads it (version = assets/directaudio/<build>/version.txt, v1.3.2 at time of writing).
    // The shared-layer copy is the one Wine actually loads, so overlaying it upgrades EVERY container on
    // that layer. Per-build dispatch (wine11 for any 11.0-x, wine10 for 10.0-4; arm64ec only), version-gated
    // by a per-layer marker, page-size aware (4KB -> sdk28, 16KB -> sdk35). Copies the COMPLETE 3-file set -
    // BOTH PE arches (aarch64-windows + i386-windows) plus the shared aarch64-unix unixlib - because which
    // PE actually loads is decided by the GUEST GAME's bitness (64-bit -> aarch64, 32-bit/wow64 -> i386),
    // not the Proton build; shipping both closes the latent 32-bit gap the old aarch64-only overlay left.
    // Idempotent + best-effort (any failure just leaves the existing driver); metadata only, no GPU probing.
    private void overlayDirectAudioDriver() {
        try {
            if (container == null) return;
            com.winhub.hernandez.contents.ContentProfile prof =
                    contentsManager.getProfileByEntryName(container.getWineVersion());
            if (prof == null) return;
            java.io.File layer = com.winhub.hernandez.contents.ContentsManager.getInstallDir(this, prof);
            java.io.File unixDir  = new java.io.File(layer, "lib/wine/aarch64-unix");     // shared unixlib
            java.io.File winDir   = new java.io.File(layer, "lib/wine/aarch64-windows");  // 64-bit (arm64ec) guest PE
            java.io.File win32Dir = new java.io.File(layer, "lib/wine/i386-windows");     // 32-bit (wow64) guest PE
            // ABI gate: these two dirs exist only on an arm64ec Proton layer, and the name carries the build.
            if (!unixDir.isDirectory() || !winDir.isDirectory()) return;
            String build = directAudioAssetDir(layer.getName());
            if (build == null) return;   // not one of the supported builds -> leave its own driver

            String base = "directaudio/" + build + "/";
            String want = FileUtils.readString(this, base + "version.txt");
            if (want == null) return;
            want = want.trim();
            // Per-layer marker records "<build> <version>"; re-overlay if either changes. The pre-existing
            // bare-version marker (old aarch64-only overlay, e.g. "1.3.1") can never match the newer
            // "<build> <version>" tag, so those layers get a one-time re-overlay that finally
            // delivers the i386 PE.
            java.io.File marker = new java.io.File(unixDir, ".directaudio_bundled");
            String tag = build + " " + want;
            String have = marker.isFile() ? FileUtils.readString(marker) : null;
            if (have != null && tag.equals(have.trim())) return;   // already current for this build

            String sdk = (android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) >= 16384)
                    ? "sdk35" : "sdk28";
            String src = base + sdk + "/";
            // Complete set: unixlib + both PE arches. The i386 PE goes to i386-windows/ if the layer has it.
            assetToFile(src + "winedirectaudio.so",          new java.io.File(unixDir,  "winedirectaudio.so"));
            assetToFile(src + "winedirectaudio-aarch64.drv", new java.io.File(winDir,   "winedirectaudio.drv"));
            if (win32Dir.isDirectory())
                assetToFile(src + "winedirectaudio-i386.drv", new java.io.File(win32Dir, "winedirectaudio.drv"));
            FileUtils.writeString(marker, tag);
            android.util.Log.i("DirectAudio", "overlaid bundled driver v" + want + " (" + build + "/" + sdk + ") -> " + layer.getName());
        } catch (Throwable t) {
            android.util.Log.w("DirectAudio", "driver overlay failed (keeping existing)", t);
        }
    }

    // Copy an APK asset to a file, then make it readable+executable (Wine dlopen's the .so). Overwrites.
    private void assetToFile(String assetPath, java.io.File dst) throws java.io.IOException {
        try (java.io.InputStream in = getAssets().open(assetPath);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        dst.setReadable(true, false);
        dst.setExecutable(true, false);
    }

    // Write the live-config "mailbox" the winedirectaudio.drv watcher polls, so an in-game cog SAVE
    // reaches the running driver (which otherwise only reads config at stream open). Same resolution as
    // applyDirectAudioConfig, emitted as the mailbox's ms/perf keys (it takes milliseconds, not frames;
    // a frame count converts at 48 kHz). PERF stays 0/1/2 - the driver maps it to the AAudio enum.
    private void writeDirectAudioRuntime() {
        try {
            android.content.SharedPreferences p = getSharedPreferences("banner_audio_directaudio", MODE_PRIVATE);
            String preset = p.getString("preset", "stable");
            int perf, bf, mbf = 0, ms = 0;
            if ("custom".equals(preset)) {
                perf = p.getInt("perf_mode", 1);
                bf   = p.getInt("buffer_frames", 0);
                mbf  = p.getInt("max_buffer_frames", 0);
                ms   = p.getInt("latency_msec", DIRECT_DEFAULT_MS);
            } else {
                com.winhub.hernandez.ui.components.AudioConfig c =
                        com.winhub.hernandez.ui.components.AudioSettingsDialogKt.directPresetConfig(preset);
                perf = c.getPerfMode();
                bf   = c.getBufferFrames();
                mbf  = c.getMaxBufferFrames();
            }
            if (perf == 2) perf = 1;   // no POWER_SAVING on DirectAudio (churns) - same as applyDirectAudioConfig
            int outMs = bf > 0 ? (bf * 1000 + 47999) / 48000 : ms;   // frames -> ms, round up
            StringBuilder sb = new StringBuilder();
            if (outMs > 0) sb.append("MS=").append(outMs).append('\n');
            if (mbf  > 0) sb.append("MAXMS=").append((mbf * 1000 + 47999) / 48000).append('\n');
            sb.append("PERF=").append(perf).append('\n');
            java.io.File f = new java.io.File(getFilesDir(), "banner_audio_directaudio.rt");
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
                os.write(sb.toString().getBytes());
            }
        } catch (Throwable t) {
            android.util.Log.w("DirectAudio", "writeDirectAudioRuntime failed", t);
        }
    }

    // Restart the guest audio server (PulseAudio + its AAudio sink). The AAudio output stream can be
    // torn down when the app is backgrounded or the HDMI audio route changes, and the sink does not
    // always re-establish it — leaving the game silent (notably after returning to a game on the TV).
    // Rebuilding the sink re-grabs the current default output route. Runs off the main thread (it does
    // file IO + spawns a process). Exposed to the TV tab's "Reset audio" button and the auto-reset.
    public void resetGuestAudio() {
        final XEnvironment env = environment;
        if (env == null) return;
        new Thread(() -> {
            try {
                PulseAudioComponent audio = env.getComponent(PulseAudioComponent.class);
                // Suspend/resume the sink (reopens the AAudio route) instead of restarting the daemon,
                // so the guest's audio connection survives. Falls back to a restart only if unreachable.
                if (audio != null) audio.resetAudioSink();
            } catch (Exception e) {
                android.util.Log.w("XServerDisplay", "guest audio reset failed", e);
            }
        }, "audio-reset").start();
    }

    // Recover audio after a MID-PLAY output-route change. A route change (headphone/USB/BT/HDMI plug or
    // unplug) DISCONNECTS the guest's AAudio stream, which can never be restarted — so the suspend/
    // resume in resetGuestAudio() is useless here. Instead we build a fresh sink on the new route and
    // move the guest's streams onto it (see PulseAudioComponent.recreateSinkForRouteChange). Runs off
    // the main thread (native PulseAudio client IO). Distinct from resetGuestAudio(), which stays the
    // right tool for background/foreground + the TV "Reset audio" button (stream idle, not disconnected).
    public void resetGuestAudioForRouteChange() {
        final XEnvironment env = environment;
        if (env == null) return;
        new Thread(() -> {
            try {
                PulseAudioComponent audio = env.getComponent(PulseAudioComponent.class);
                if (audio != null) audio.recreateSinkForRouteChange();
            } catch (Exception e) {
                android.util.Log.w("XServerDisplay", "guest audio route recovery failed", e);
            }
        }, "audio-route-recreate").start();
    }

    // True for the physical output routes that, when (un)plugged mid-game, require the AAudio sink to
    // reopen onto the new default (3.5mm, USB-C, Bluetooth, HDMI). Internal speaker/earpiece are the
    // fallback route resetGuestAudio() re-grabs, so a change involving one of these still needs a reset.
    private static boolean isRouteChangingOutput(android.media.AudioDeviceInfo d) {
        if (d == null || !d.isSink()) return false;
        switch (d.getType()) {
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
            case android.media.AudioDeviceInfo.TYPE_USB_DEVICE:
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case android.media.AudioDeviceInfo.TYPE_HDMI:
            case android.media.AudioDeviceInfo.TYPE_HDMI_ARC:
            case android.media.AudioDeviceInfo.TYPE_HDMI_EARC:
            case android.media.AudioDeviceInfo.TYPE_AUX_LINE:
                return true;
            default:
                return d.getType() == 26 /* TYPE_BLE_HEADSET, API 31 */
                    || d.getType() == 27 /* TYPE_BLE_SPEAKER, API 31 */;
        }
    }

    // Debounced recovery: a single plug event can surface as several add/remove callbacks in quick
    // succession, so coalesce them into one recovery ~350ms after the last one settles.
    private final Runnable audioRouteResetRunnable = this::resetGuestAudioForRouteChange;

    private void onAudioRouteChanged(android.media.AudioDeviceInfo[] devices) {
        boolean relevant = false;
        if (devices != null) {
            for (android.media.AudioDeviceInfo d : devices) {
                if (isRouteChangingOutput(d)) { relevant = true; break; }
            }
        }
        if (!relevant) return;
        handler.removeCallbacks(audioRouteResetRunnable);
        handler.postDelayed(audioRouteResetRunnable, 350);
    }

    /** Start watching for mid-game output-route changes (headphone plug/unplug etc.). Idempotent. */
    private void registerAudioRouteWatcher() {
        if (audioRouteCallback != null) return;
        try {
            final android.media.AudioManager am =
                (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            audioRouteCallbackPrimed = false;
            audioRouteCallback = new android.media.AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] addedDevices) {
                    // The first callback after register is the current device list — not a route change.
                    if (!audioRouteCallbackPrimed) { audioRouteCallbackPrimed = true; return; }
                    onAudioRouteChanged(addedDevices);
                }
                @Override
                public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] removedDevices) {
                    onAudioRouteChanged(removedDevices);
                }
            };
            am.registerAudioDeviceCallback(audioRouteCallback, handler);
        } catch (Exception e) {
            android.util.Log.w("XServerDisplay", "audio route watcher register failed", e);
            audioRouteCallback = null;
        }
    }

    /** Stop watching output-route changes and cancel any pending debounced reset. Idempotent. */
    private void unregisterAudioRouteWatcher() {
        handler.removeCallbacks(audioRouteResetRunnable);
        if (audioRouteCallback == null) return;
        try {
            final android.media.AudioManager am =
                (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) am.unregisterAudioDeviceCallback(audioRouteCallback);
        } catch (Exception e) {
            android.util.Log.w("XServerDisplay", "audio route watcher unregister failed", e);
        } finally {
            audioRouteCallback = null;
        }
    }

    /** Dim the handheld (host) window while the game is on the TV, if the user enabled it (battery/heat
     *  saver). Restores full brightness once the game is back on the phone or the toggle is off. */
    private void applyHandheldDim() {
        final boolean dim = XServerDrawerState.INSTANCE.getTvDimHandheld().getValue()
                && externalDisplayController != null && externalDisplayController.isGameOnExternal();
        runOnUiThread(() -> {
            try {
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = dim ? 0.02f
                        : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                getWindow().setAttributes(lp);
            } catch (Exception ignored) {}
        });
    }

    /** Best-effort media output routing while on TV (EXPERIMENTAL — the guest's PulseAudio AAudio sink
     *  may not follow, as we don't own its output track). 0 = follow system, 1 = TV/HDMI, 2 = handheld. */
    private void applyTvAudioRoute(int mode) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null || android.os.Build.VERSION.SDK_INT < 31) return;
            if (mode == 0) { am.clearCommunicationDevice(); resetGuestAudio(); return; }
            for (android.media.AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
                int t = d.getType();
                boolean match = (mode == 1)
                        ? (t == android.media.AudioDeviceInfo.TYPE_HDMI
                            || t == android.media.AudioDeviceInfo.TYPE_HDMI_ARC
                            || t == android.media.AudioDeviceInfo.TYPE_HDMI_EARC
                            || t == android.media.AudioDeviceInfo.TYPE_AUX_LINE)
                        : (t == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
                if (match) { am.setCommunicationDevice(d); break; }
            }
            // Nudge PulseAudio to reopen its AAudio stream onto the new route.
            resetGuestAudio();
        } catch (Exception e) {
            android.util.Log.w("XServerDisplay", "tv audio route failed", e);
        }
    }

    /** Step 2b: wait for the first HLS segment, host the live playlist, and cast it to the TV. */
    private void startLiveCast(com.winhub.hernandez.cast.CastDiscovery.Device device,
                              com.winhub.hernandez.cast.TsSegmenter seg) {
        new Thread(() -> {
            try {
                // Wait for the first segment (~2-4s of video) before pointing the TV at the playlist.
                long deadline = System.currentTimeMillis() + 15000;
                while (!seg.hasSegments() && System.currentTimeMillis() < deadline) Thread.sleep(200);
                if (!seg.hasSegments()) { castFail("No video yet — try again."); return; }
                String ip = com.winhub.hernandez.cast.HttpFileServer.localIpv4();
                if (ip == null) { castFail("Couldn't find this phone's Wi-Fi address."); return; }
                castHttp = new com.winhub.hernandez.cast.HttpFileServer(seg);
                int port = castHttp.start();
                // Cast the master (multivariant) playlist so the receiver sees the CODECS up front.
                String url = "http://" + ip + ":" + port + "/master.m3u8";
                android.util.Log.i("CastSession", "live url: " + url);
                runOnUiThread(() -> XServerDialogState.INSTANCE.setCastStatusDetail("Sending to the TV…"));
                castSession = new com.winhub.hernandez.cast.CastSession(device.host,
                        new com.winhub.hernandez.cast.CastSession.Callback() {
                    @Override public void onConnected() {
                        runOnUiThread(() -> XServerDialogState.INSTANCE.setCastStatusDetail("Loading on the TV…"));
                    }
                    @Override public void onLoaded() {
                        runOnUiThread(() -> {
                            XServerDialogState.INSTANCE.setCastStatus(XServerDialogState.CastStatus.CONNECTED);
                            XServerDialogState.INSTANCE.setCastStatusDetail("Live on your TV (a few seconds behind).");
                        });
                    }
                    @Override public void onError(String message) { castFail(message); }
                });
                castSession.connectAndLoad(url, "application/vnd.apple.mpegurl", "LIVE");
            } catch (Exception e) {
                castFail("Cast failed: " + e.getMessage());
            }
        }, "cast-start").start();
    }

    private void castFail(String message) {
        runOnUiThread(() -> {
            XServerDialogState.INSTANCE.setCastStatus(XServerDialogState.CastStatus.FAILED);
            XServerDialogState.INSTANCE.setCastStatusDetail(message);
        });
    }

    /** Tear down a cast: cancel a pending start, stop capture/session/server, return the game. */
    private void stopCast() {
        if (pendingCastStart != null) { handler.removeCallbacks(pendingCastStart); pendingCastStart = null; }
        try { if (gameCaster != null) gameCaster.stop(); } catch (Exception ignored) {}
        try { if (castSession != null) { castSession.close(); castSession = null; } } catch (Exception ignored) {}
        try { if (castHttp != null) { castHttp.stop(); castHttp = null; } } catch (Exception ignored) {}
        castSegmenter = null;
        if (externalDisplayController != null) externalDisplayController.resumeAfterCast();
        XServerDialogState.INSTANCE.setCastStatus(XServerDialogState.CastStatus.IDLE);
        XServerDialogState.INSTANCE.setCastTargetName("");
        XServerDialogState.INSTANCE.setCastStatusDetail("");
    }

    private void setPausedState(boolean paused) {
        isPaused = paused;
        if (paused) {
            ProcessHelper.pauseAllWineProcesses();
        } else {
            ProcessHelper.resumeAllWineProcesses();
            reshadePreviewPaused = false;
        }
        XServerDrawerState.INSTANCE.setIsPaused(paused);
        XServerDialogState.INSTANCE.setPaused(paused);
        // Mirror the paused state onto the TV (the pause pill shows on the external display too).
        if (externalDisplayController != null) externalDisplayController.setPaused(paused);
    }

    // Brief automatic "background + foreground" pulse used to reset frame generation on an FG
    // toggle-on / model change. A bare SIGSTOP/SIGCONT is not enough — win-fg comes up artifacty
    // because its optical flow starts on a moving frame pair. Replicating the FULL bg/fg cycle
    // (pause the X server + render view, freeze the guest, then resume all of it ~0.5s later) makes
    // the guest go fully still so win-fg restarts from a near-zero-motion pair. Does NOT flip
    // isPaused (no pause UI, no user tap needed); debounced; bails if a real pause is active.
    private boolean fgResetPulseInProgress = false;
    private void pulseFgReset() {
        if (fgResetPulseInProgress || isPaused || environment == null) return;
        fgResetPulseInProgress = true;
        // --- background half (mirrors the onPause path) ---
        environment.onPause();
        if (xServerView != null) xServerView.onPause();
        ProcessHelper.pauseAllWineProcesses();
        // --- resume half ~0.5s later (mirrors the onResume path) ---
        handler.postDelayed(() -> {
            if (!isPaused) {
                if (xServerView != null) xServerView.onResume();
                environment.onResume();
                ProcessHelper.resumeAllWineProcesses();
                reapplyVrr();
            }
            fgResetPulseInProgress = false;
        }, 500);
    }

    // === Frame-gen change → full presentation reset (LSFG black-frame flicker fix) ================
    // Device-proven: with lsfg-vk generating, a plain swapchain recreate on the SAME surface (which
    // the conf.toml rewrite already triggers) does NOT clear the black-frame flicker — only a real
    // background/foreground cycle does, because that fully tears down + rebuilds the Android window
    // surface AND pauses/resumes the guest, resetting the host compositor's over-queue. This
    // replicates that cycle deterministically on an in-game FG change and gates the resume behind a
    // user "Resume" tap so the surface is guaranteed rebuilt before input returns to the game.
    private boolean fgResetInProgress = false;   // a reset is showing / the surface is torn down
    private int lastCommittedFgLevel = -1;       // effective FG level last committed: 0 = off, else 2/3/4

    // Called from onBionicFgConfigChange (lsfg branch) after each committed change. Fires the reset
    // ONLY when the effective FG level actually changes (Off/On/2×/3×/4×) — flow-scale / performance-
    // mode / model edits keep the level, so they never reset. Tracks the latest level regardless so a
    // change that lands while a reset is already up is still remembered (the Resume rebuild picks up
    // the newest conf.toml the handler already wrote).
    private void maybeTriggerFgReset(int newLevel) {
        boolean changed = (newLevel != lastCommittedFgLevel);
        lastCommittedFgLevel = newLevel;
        if (!changed) return;
        triggerFgPresentationReset();
    }

    // win-fg equivalent of maybeTriggerFgReset. win-fg restarts its optical-flow / present state on a
    // frame-gen LEVEL (Off/On/2×/3×/4×), interpolation MODEL, or PERFORMANCE-PRESET change, so — like
    // lsfg on a level change — each fires the SAME deterministic pause + surface-teardown + Resume
    // reset (triggerFgPresentationReset) instead of the old soft bg/fg pulse. Perf-preset is keyed here
    // together with the win-fg .so fix that makes the swapchain recreate re-read the conf and clear the
    // mtime — without it, the layer's own perf_preset self-rebuild would ALSO fire on the fresh
    // swapchain and collide with the teardown (Fold-8 freeze). Flow-scale keeps all three keys so it
    // stays live. Tracks all three so a change that lands while a reset is up is remembered.
    private int lastCommittedWinFgLevel = -1;
    private int lastCommittedWinFgModel = -1;
    private int lastCommittedWinFgPreset = -1;
    private void maybeTriggerWinFgReset(int level, int model, int preset) {
        boolean changed = (level != lastCommittedWinFgLevel)
                       || (model != lastCommittedWinFgModel)
                       || (preset != lastCommittedWinFgPreset);
        lastCommittedWinFgLevel = level;
        lastCommittedWinFgModel = model;
        lastCommittedWinFgPreset = preset;
        if (!changed) return;
        triggerFgPresentationReset();
    }

    // Background half: freeze the guest (SIGSTOP via the same path onPause() / a manual Pause use),
    // release the renderer, then tear the Android render surface FULLY down (SurfaceView → GONE →
    // surfaceDestroyed → nativeDetachSurface), and raise the Resume overlay. Does NOT flip isPaused —
    // the FG-reset overlay is its own state so it never collides with the manual-pause / ReShade box.
    private void triggerFgPresentationReset() {
        if (fgResetInProgress || environment == null) return;
        // A manual Pause / ReShade freeze is already suspending the guest — mirroring bg/fg needs a
        // running-then-frozen guest, and stacking another cycle would fight the manual resume. Skip;
        // the next FG change after the user resumes will reset cleanly.
        if (isPaused) return;
        // No detachable SurfaceView (e.g. renderer not initialised yet) — nothing to cycle.
        if (xServerView == null || !xServerView.canRecreateSurface()) return;
        fgResetInProgress = true;
        environment.onPause();
        xServerView.onPause();
        ProcessHelper.pauseAllWineProcesses();
        // Mirror onStop()'s display-rate release: a real background DROPS the panel refresh-rate vote
        // entirely (setDisplayFrameRate 0f) and unhooks the display listener, so the panel re-negotiates
        // its refresh mode clean on the way back. The old reset skipped this — it only re-asserted the
        // vote on resume, never released it, so the panel stayed stuck in a stale VRR mode and the
        // vsync clock (which reads getRefreshRate()) fed the layer the wrong cadence → generated frames
        // paced against the wrong grid (device symptom: spurty FPS drops after an in-game multiplier
        // change that ONLY a genuine bg/fg cleared). Release it here, while the surface is still alive,
        // so the Resume half re-negotiates from scratch exactly like onStop→onResume.
        routeVrrVote(0f);
        unregisterVrrDisplayListener();
        xServerView.teardownSurface();
        XServerDialogState.INSTANCE.setFgResetPaused(true);
    }

    // Foreground half (Resume tap, or an incidental real foreground while a reset is up): dismiss the
    // overlay, rebuild a FRESH surface (SurfaceView → VISIBLE → surfaceCreated → nativeReattachSurface
    // + swapchain recreate), then resume the guest (SIGCONT) and re-assert present mode / VRR. Ordered
    // so input only returns to the game after the surface is coming back. Idempotent + main-thread.
    private void resumeFromFgReset() {
        if (!fgResetInProgress) return;
        fgResetInProgress = false;
        XServerDialogState.INSTANCE.setFgResetPaused(false);
        if (xServerView != null) {
            xServerView.rebuildSurface();
            xServerView.onResume();
        }
        if (environment != null) environment.onResume();
        ProcessHelper.resumeAllWineProcesses();
        applyEffectivePresentMode();
        reapplyVrr();
        // Mirror onResume(): re-hook the display listener and re-read the live panel rate so the vsync
        // pacing clock picks up the freshly re-negotiated cadence instead of a stale one. Idempotent —
        // when a REAL foreground drove this (onResume → resumeFromFgReset), onResume already did both;
        // registerVrrDisplayListener() no-ops if already hooked. Completes the full VRR release→re-
        // negotiate cycle a genuine bg/fg performs, which the old half-reset was missing.
        registerVrrDisplayListener();
        updateCurrentRefreshRate();
    }

    private void savePlaytimeData() {
        long endTime = System.currentTimeMillis();
        long playtime = endTime - startTime;

        // Ensure that playtime is not negative
        if (playtime < 0) {
            playtime = 0;
        }

        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playtimeKey = shortcutName + "_playtime";

        // Accumulate the playtime into totalPlaytime
        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0) + playtime;
        editor.putLong(playtimeKey, totalPlaytime);
        editor.apply();

        // Reset startTime to the current time for the next interval
        startTime = System.currentTimeMillis();
    }


    private void incrementPlayCount() {
        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playCountKey = shortcutName + "_play_count";
        int playCount = playtimePrefs.getInt(playCountKey, 0) + 1;
        editor.putInt(playCountKey, playCount);
        editor.apply();
    }

    // Re-entry guard: exit() has several callers (menu quit / onCancel, the game-exit watcher, the
    // installer auto-exit watcher, autoClose). Before the save phase ran on a worker thread they
    // serialized harmlessly on the main thread, but now a second exit() would spawn a SECOND save/upload
    // worker whose restart can kill the first upload mid-flight (observed: two concurrent GOG uploads on
    // one exit). Latch the first call; ignore the rest. exit() is always on the main thread.
    private volatile boolean exiting = false;

    /** Ends the Xbox lease (removes the launch view) and the bridge, then the account service, and puts the
     *  container's own system32 modules back. No-op when this is not an Xbox session. */
    private void closeXboxSession() {
        if (xboxLaunch == null) return;
        xboxLaunch.close();
        xboxLaunch = null;
        com.winhub.hernandez.store.xbox.XboxRuntime.INSTANCE.stop();
        com.winhub.hernandez.store.xbox.XboxRuntime.restoreSystem32(new File(imageFs.wineprefix, "drive_c/windows/system32"));
    }

    private void exit() {
        eaRefusalHandler.removeCallbacks(eaRefusalWatchRunnable);
        if (exiting) return;
        exiting = true;
        // Take the handheld's companion screen down at the START of the shutdown, not at onDestroy:
        // the save/upload phase below can run for many seconds, and the user should not be looking at
        // "playing on the TV" while the game is being shut down.
        com.winhub.hernandez.display.TvCompanionActivity.dismiss("the session is ending");
        // Wayland HDR output: the session's "HDR on screen: ..." line, written while the game is still
        // connected (the compositor writes it once; onDestroy's call is the fallback).
        if (waylandMode) {
            try { com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrSessionEnd(); } catch (Throwable ignored) {}
        }
        // A frozen (SIGSTOP'd) guest can't act on the SIGTERM below — resume before tearing down so
        // graceful termination isn't stuck waiting on a suspended process (any pending pulse aside).
        reshadePulseInProgress = false;
        ProcessHelper.resumeAllWineProcesses();
        // Epic EOS Phase 2: remove this game's short-lived Denuvo ownership token (.ovt)
        // from the wine prefix now that the session is ending. No-op for non-Epic games.
        // Xbox with cloud saves on: the save helper's final upload needs the game ended first and our bridge and
        // account service still up, so that session is closed after the sync, just before the teardown (below).
        final boolean xboxSaveSync = xboxLaunch != null && com.winhub.hernandez.store.xbox.XboxMainActivity.Companion.cloudSaves(this);
        try {
            if (shortcut != null) com.winhub.hernandez.store.EpicLaunchArgs.cleanupOwnershipToken(this, shortcut);
            if (!xboxSaveSync) closeXboxSession();
        } catch (Throwable ignored) {}
        installerWatchHandler.removeCallbacks(installerWatchRunnable);
        gameExitWatchHandler.removeCallbacks(gameExitWatchRunnable);
        affinityReapplyHandler.removeCallbacks(affinityReapplyRunnable);
        stopDxApiDetection();
        // Stop the session foreground service (also removes its ongoing notification).
        stopService(new Intent(this, com.winhub.hernandez.core.GameSessionForegroundService.class));
        preloaderDialog.showOnUiThread(R.string.shutdown);
        final Runnable teardown = new Runnable() {
            @Override
            public void run() {
                // The controllers and their slots for the controller log, before anything is released.
                if (winHandler != null) com.winhub.hernandez.core.ControllerLog.pads(winHandler.getPlayerSlotAssignments(), true);
                savePlaytimeData(); // Save on destroy
                handler.removeCallbacks(savePlaytimeRunnable);
                if (midiHandler != null) midiHandler.stop();
                // Unregister sensor listener to avoid memory leaks
                if (environment != null) environment.stopEnvironmentComponents();
                // Release the Steam Controller (SDL closes it, so it drops back to its own
                // keyboard/mouse mode) before WinHandler tears the slots down.
                stopSteamControllerSupport();
                if (winHandler != null) winHandler.stop();
                if (wineRequestHandler != null) wineRequestHandler.stop();
                /* Gracefully terminate all running wine processes */
                ProcessHelper.terminateAllWineProcesses();
                /* Wait until all processes have gracefully terminated, forcefully killing them only after a certain amount of time */
                long start = System.currentTimeMillis();
                while (!ProcessHelper.listRunningWineProcesses().isEmpty()) {
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed >= 1500) {
                        break;
                    }
                }
                // Best-effort save backup on exit, now that the game has terminated (and flushed).
                // Steam-library games → their per-appId local Library (Collect, no cloud); GOG-library
                // games → GOG cloud upload (Galaxy-parity); custom imports → the persistent local vault.
                // Runs BEFORE restartApplication() because that kills the process (exit(0)), which would
                // otherwise abort the copy. Each branch is bounded + fully guarded, gated by its Save
                // Manager toggle (shared prefs, all default true).
                //
                // CRITICAL: this phase can be SLOW — the GOG cloud upload is a NETWORK op (bounded to
                // ~15s). It used to run inline on THIS main-thread runnable, which froze the "Shutting
                // down…" overlay's progress animation for the whole upload → users thought the app hung
                // and swiped it off recents, killing the upload mid-flight. So the save phase now runs on
                // a WORKER thread: the main thread returns, the Compose preloader keeps animating, we
                // surface a live "Backing up… / Uploading…" hint, and only once the phase finishes (or
                // its own internal bounds elapse) do we post the overlay close + process restart back to
                // the main thread. (Teardown above stays on-main; it's short.)
                preloaderDialog.hint(getString(R.string.saving_on_exit));
                new Thread(() -> {
                    // Controller input log: first, because it is quick and the network steps below may be
                    // cut short by a user who swipes the app away. No-op unless this launch began one.
                    if (com.winhub.hernandez.core.ControllerLog.isActive()) {
                        try {
                            ProcessHelper.removeDebugCallback(com.winhub.hernandez.core.ControllerLog.GUEST_LINES);
                            Context logCtx = getApplicationContext();
                            String logGame = currentLogGameName();
                            com.winhub.hernandez.core.ControllerLog.finish(logCtx,
                                    com.winhub.hernandez.core.LogLocation.resolveGameLogDir(logCtx, logGame), logGame);
                        } catch (Throwable t) {
                            Log.w("XServerDisplayActivity", "controller log on exit errored", t);
                        }
                    }
                    try {
                        // Real-Steam launch: the guest (and the agent's own Steam_LogOff) is gone, so the
                        // account is free — bring the app's own CM session back FIRST and give it a bounded
                        // window to log on, so the Steam Cloud upload + achievement sync-back below run on a
                        // live session. Worker thread, so the wait never touches the UI. No-op otherwise.
                        if (realSteamPlan != null) releaseRealSteamSession("game exit", 6000L);
                        clearOfflineSteamPresence("game exit");
                        SharedPreferences savePrefs = getSharedPreferences("save_manager_prefs", MODE_PRIVATE);
                        if (isGenuineSteamShortcut()) {
                            if (savePrefs.getBoolean("auto_collect_steam_on_exit", true)) autoCollectSteamSavesBlocking();
                            // Additionally push to Steam Cloud (opt-in) — ONLY once the user has accepted
                            // the third-party cloud disclaimer (steam_prefs). Absent flag → skip; we never
                            // auto-upload to a real Steam Cloud without consent. The local Collect above
                            // stays unconditional (independent of this cloud toggle).
                            boolean cloudDisclaimerOk = getSharedPreferences("steam_prefs", MODE_PRIVATE)
                                    .getBoolean("cloud_saves_disclaimer_accepted", false);
                            if (cloudDisclaimerOk && savePrefs.getBoolean("auto_upload_steam_on_exit", true))
                                autoUploadSteamSavesBlocking();
                        } else {
                            // GOG-library games (untagged, installed under gog_games/) push their saves to
                            // GOG cloud — the Galaxy-parity auto-trigger. Additive: they ALSO keep the local
                            // vault snapshot below, so a game with no cloud support (or a stalled upload)
                            // still gets its usual offline backup.
                            if (isGogShortcut() && savePrefs.getBoolean("auto_upload_gog_on_exit", true))
                                autoUploadGogSavesBlocking();
                            if (savePrefs.getBoolean("auto_backup_custom_on_exit", true)) autoSnapshotCustomSavesBlocking();
                        }
                        // Flush any queued achievement unlocks back to the user's Steam profile. Safe
                        // no-op unless the default-OFF "sync unlocks" toggle is on (guarded inside).
                        try {
                            SteamAchievementStore.flushPendingSyncBack(getApplicationContext());
                        } catch (Throwable t) {
                            Log.w("BH_STEAM_ACHV", "flushPendingSyncBack on exit errored", t);
                        }
                        // SteamLite (Steam client) logs — fold the genuine client's own logs from this
                        // container's prefix into a single shareable steamlite.txt beside wine_debug.log,
                        // with a summary + auto-scanned diagnostics on top. We run it HERE, on the exit
                        // worker thread, precisely because the wine tree (and the Steam client) have just
                        // been terminated + flushed above — so the logs on disk are complete. Gated on the
                        // default-OFF toggle AND on realSteamPlan != null: only a launch that actually went
                        // through SteamLite this session collects, so a normal launch of the same container
                        // never picks up a STALE Steam-logs dir left in the prefix by an earlier run. The
                        // collector itself also no-ops when the marker/logs aren't present. Fully guarded —
                        // logging must never break a game exiting.
                        try {
                            if (realSteamPlan != null
                                    && preferences.getBoolean("enable_steamlite_logs", false)) {
                                Context appCtx = getApplicationContext();
                                String gameName = currentLogGameName();
                                File driveC = new File(container.getRootDir(), ".wine/drive_c");
                                File perGameLogDir =
                                        com.winhub.hernandez.core.LogLocation.resolveGameLogDir(appCtx, gameName);
                                com.winhub.hernandez.core.SteamLiteLogCollector.Info info =
                                        new com.winhub.hernandez.core.SteamLiteLogCollector.Info(
                                                String.valueOf(container.id),
                                                container.getWineVersion(),
                                                emulator,
                                                wineInfo != null && wineInfo.isArm64EC(),
                                                dxwrapper,
                                                dxwrapperConfig != null ? dxwrapperConfig.get("version") : null,
                                                dxwrapperConfig != null ? dxwrapperConfig.get("vkd3dVersion") : null);
                                com.winhub.hernandez.core.SteamLiteLogCollector.collect(
                                        appCtx, driveC, perGameLogDir, gameName, realSteamPlan.appId, info,
                                        steamAgentChannel != null ? steamAgentChannel.eventLines() : null);
                            }
                        } catch (Throwable t) {
                            Log.w("SteamLiteLogs", "collect on exit errored", t);
                        }
                    } catch (Throwable t) {
                        Log.w("XServerDisplayActivity", "exit save-backup phase errored", t);
                    } finally {
                        // Always finalize — close the overlay + restart the process — on the main thread,
                        // whatever happened above. Guarded so a torn-down activity can't crash the restart.
                        runOnUiThread(() -> {
                            try { if (preloaderDialog != null) preloaderDialog.close(); } catch (Throwable ignored) {}
                            // A normal quit used to always restart the whole process, which replayed
                            // the cold-start splash before the deck came back. If this session was
                            // launched on top of the deck and nothing is waiting to resume, just
                            // finish back to it. A pending relaunch (fail-card Retry / Goldberg) or a
                            // component install that must resume still needs the clean restart.
                            boolean needsRestart = false;
                            try {
                                needsRestart = com.winhub.hernandez.store.SteamSessionManager.INSTANCE
                                        .hasPendingRelaunch(getApplicationContext())
                                        || com.winhub.hernandez.components.ComponentInstallReturn.INSTANCE
                                        .get(getApplicationContext()) != null;
                            } catch (Throwable ignored) {}
                            if (!needsRestart && !isTaskRoot()) {
                                finish();
                            } else {
                                AppUtils.restartApplication(getApplicationContext());
                            }
                        });
                    }
                }, "BH-ExitSaveBackup").start();
            }
        };
        if (xboxSaveSync) {
            final long teardownAt = android.os.SystemClock.uptimeMillis() + 1000;
            final String exeName = xboxLaunch.getExe().getName();
            final long savesSince = xboxLaunch.getPreparedAt();
            final File saveRoot = new File(imageFs.wineprefix, "drive_c/users/xuser/AppData/Local/XodusGameSave");
            final com.winhub.hernandez.store.xbox.XboxSaveSync.Uploader uploader = winHandler == null ? null
                    : new com.winhub.hernandez.store.xbox.XboxSaveSync.Uploader(new File(imageFs.wineprefix), imageFs.getRootDir(),
                            xboxLaunch.getProductId(), path -> winHandler.execRaw(path, ""));
            preloaderDialog.hint(getString(R.string.xbox_saving_on_exit));
            new Thread(() -> {
                try {
                    com.winhub.hernandez.store.xbox.XboxSaveSync.finish(getApplicationContext(), exeName, 25000L, saveRoot, savesSince, 30000L, uploader);
                } catch (Throwable t) {
                    Log.w("XServerDisplayActivity", "Xbox save sync on exit failed", t);
                } finally {
                    try { closeXboxSession(); } catch (Throwable ignored) {}
                    handler.postAtTime(teardown, teardownAt);
                }
            }, "xbox-save-sync").start();
        } else {
            handler.postDelayed(teardown, 1000);
        }
    }

    /**
     * Terminate any wine processes orphaned by a previous session that was killed without a clean
     * exit (recents-swipe, background optimisation, force-stop) — none of those run {@link #exit()},
     * the only other caller of the wine teardown, so the stale wineserver + wine tree can survive as
     * orphans. Only called on the fresh-launch path, where every wine process is stale by
     * construction; a paused-session in-app resume never re-enters the launch runnable, so a live
     * session can never be swept.
     */
    private void sweepStaleWineProcesses() {
        Log.d("XServerDisplayActivity", "Sweeping stale wine processes from previous session");
        ProcessHelper.terminateAllWineProcessesAndWait(1500, true);
    }

    /**
     * Whether this shortcut is a genuine Steam-library game: tagged {@code storeSource=steam}, or its
     * exec path lives under the {@code steam_games} install root (covers pre-tagging shortcuts). Steam
     * games back up to their per-appId Library; everything else (custom exe/folder imports) backs up
     * to the local vault. Mirrors ShortcutsScreen's Steam-origin gate.
     */
    private boolean isGenuineSteamShortcut() {
        if (shortcut == null) return false;
        if ("steam".equals(shortcut.getExtra("storeSource"))) return true;
        String p = shortcut.path;
        return p != null && p.toLowerCase().contains("steam_games");
    }

    /**
     * Whether this launch is a RealSteam (SteamLite) launch with the per-game "Controller passthrough"
     * toggle ON — the device-proven fix for classic DInput games (Half-Life 2, CS:S) that read the pad
     * directly and get nothing while the genuine client holds it as Steam Input. Gates ALL passthrough
     * behaviour: the prefix levers (in {@link RealSteamLauncher#prepare}) and the DInput input-type
     * override below. False (byte-unchanged) for Goldberg / Raw / normal launches and for a toggle-OFF
     * RealSteam launch.
     */
    private boolean isControllerPassthroughLaunch() {
        return shortcut != null
                && "RealSteam".equals(shortcut.getExtra("launchMode"))
                && "1".equals(shortcut.getExtra("controllerPassthrough"))
                && isGenuineSteamShortcut();
    }

    /**
     * Whether this shortcut is a GOG-library game: GOG shortcuts are UNTAGGED (StarLaunchBridge only
     * stamps steam/epic), so the only signal is the exec path living under the {@code gog_games} install
     * root ({@link GogInstallPath}). Used to fire GOG cloud auto-upload on exit.
     */
    private boolean isGogShortcut() {
        if (shortcut == null) return false;
        String p = shortcut.path;
        return p != null && p.toLowerCase().contains("gog_games");
    }

    /**
     * On game exit, snapshot this game's saves from its container into the local save Library
     * (Container -> Library) when the shortcut is a Steam-library game (tagged with a positive
     * {@code steamAppId}). Collect only — never uploads to Steam Cloud (explicit requirement).
     *
     * Best-effort and silent: no UI/Toast, all errors swallowed (logged to "BH_SAVE_SYNC"). Uses the
     * application context so the copy isn't tied to this dying activity. The DB lookup + collect run
     * on a worker thread (Room can't be queried on the main thread); we bound-wait on a latch so the
     * copy finishes before the process exits, while a stuck collect can never freeze game-exit.
     */
    private void autoCollectSteamSavesBlocking() {
        try {
            final Shortcut sc = shortcut;
            if (sc == null) return;

            // A steamAppId alone is NOT proof of a Steam-library game: custom exe/folder imports can
            // carry one purely to link cover art / metadata. Auto-collect (keyed by appId) only for
            // GENUINE Steam-library games — tagged storeSource=steam, OR whose exec lives under the
            // Steam install root (steam_games/, where the in-app store installs; imports don't). This
            // prevents a cover-linked custom import from being mis-filed under a Steam appId's Library.
            String storeSource = sc.getExtra("storeSource", "");
            String path = sc.path != null ? sc.path.toLowerCase() : "";
            boolean genuineSteam = "steam".equals(storeSource) || path.contains("steam_games");
            if (!genuineSteam) return;

            // Tagged appId is the fast path; 0/missing (a PRE-TAGGING shortcut like an older Half-Life 2
            // whose .desktop carries no steamAppId) is NOT a bail — we derive the appId from the exec
            // path's steam_games/<folder> via the installed-games DB below, on the worker thread.
            int tagged;
            try {
                tagged = Integer.parseInt(sc.getExtra("steamAppId", "0").trim());
            } catch (Exception e) {
                tagged = 0;
            }
            final int fTaggedAppId = tagged;
            final String execPath = sc.path;                    // original casing, for the folder parse
            final String folder = steamGamesFolderOf(execPath); // e.g. "Half-Life 2", or null

            final Context appCtx = getApplicationContext();
            final CountDownLatch latch = new CountDownLatch(1);

            new Thread(() -> {
                try {
                    int appId = fTaggedAppId;
                    String installDir = "";

                    // Fast path: tagged appId → resolve installDir via getGame (Room, off-main here).
                    if (appId > 0) {
                        SteamDatabase.GameRow row = SteamRepository.getInstance().getDatabase().getGame(appId);
                        installDir = (row != null && row.installDir != null) ? row.installDir : "";
                    }

                    // Pre-tagging shortcut (no/<=0 steamAppId, or appId with no install row): derive
                    // appId+installDir by matching the exec path's steam_games/<folder> against the
                    // installed-games DB. getDatabase() lazy-inits from SteamRepository's appContext;
                    // a "not initialised" throw is caught by the surrounding try/catch below.
                    if ((appId <= 0 || installDir.isEmpty()) && folder != null) {
                        List<SteamDatabase.GameRow> installed =
                                SteamRepository.getInstance().getDatabase().getInstalledGames();
                        if (installed != null) {
                            for (SteamDatabase.GameRow r : installed) {
                                if (installDirMatchesFolder(r.installDir, folder)) {
                                    appId = r.appId;
                                    installDir = (r.installDir != null) ? r.installDir : "";
                                    break;
                                }
                            }
                        }
                    }

                    if (appId <= 0 || installDir.isEmpty()) {
                        Log.w("BH_SAVE_SYNC", "auto-collect: could not resolve appId for " + execPath);
                        latch.countDown();
                        return;
                    }

                    final int fAppId = appId;
                    SteamCloudSaveManager.INSTANCE.collectFromContainer(appCtx, fAppId, installDir,
                            new SteamCloudSaveManager.Callback() {
                                @Override public void onStatus(String message) {}
                                @Override public void onDone(String summary) {
                                    Log.i("BH_SAVE_SYNC", "auto-collect on exit (appId " + fAppId + "): " + summary);
                                    latch.countDown();
                                }
                                @Override public void onError(String message) {
                                    Log.w("BH_SAVE_SYNC", "auto-collect on exit failed (appId " + fAppId + "): " + message);
                                    latch.countDown();
                                }
                            });
                } catch (Throwable t) {
                    Log.w("BH_SAVE_SYNC", "auto-collect on exit errored", t);
                    latch.countDown();
                }
            }, "BH-SaveAutoCollect").start();

            // Bounded so a stalled collect (local file copy — normally sub-second) never hangs exit.
            latch.await(8, TimeUnit.SECONDS);
        } catch (Throwable t) {
            Log.w("BH_SAVE_SYNC", "auto-collect on exit wrapper errored", t);
        }
    }

    /**
     * The game folder from a Steam shortcut's exec path — the segment right after {@code steam_games}.
     * Handles both separators and the {@code Z:\steam_games\<Folder>\...exe} form. Returns null when
     * the path has no {@code steam_games/} segment. e.g. {@code Z:\steam_games\Half-Life 2\hl2.exe} →
     * "Half-Life 2".
     */
    private static String steamGamesFolderOf(String rawPath) {
        if (rawPath == null) return null;
        String norm = rawPath.replace('\\', '/');
        int idx = norm.toLowerCase().indexOf("steam_games/");
        if (idx < 0) return null;
        String after = norm.substring(idx + "steam_games/".length());
        int slash = after.indexOf('/');
        String folder = (slash >= 0 ? after.substring(0, slash) : after).trim();
        return folder.isEmpty() ? null : folder;
    }

    /**
     * Whether an installed-game {@code installDir} corresponds to the exec path's steam_games folder.
     * Matches on the adjacent path segments {@code steam_games/<folder>} (case-insensitive, slash-
     * normalized) so "Half-Life" can't false-match "Half-Life 2".
     */
    private static boolean installDirMatchesFolder(String installDir, String folder) {
        if (installDir == null || folder == null) return false;
        String norm = installDir.replace('\\', '/');
        while (norm.endsWith("/")) norm = norm.substring(0, norm.length() - 1);
        String[] segs = norm.split("/");
        for (int i = 0; i + 1 < segs.length; i++) {
            if (segs[i].equalsIgnoreCase("steam_games") && segs[i + 1].equalsIgnoreCase(folder)) return true;
        }
        return false;
    }

    /** Resolved [appId, installDir] for a genuine Steam-library shortcut. */
    private static final class SteamAppRef {
        final int appId;
        final String installDir;
        SteamAppRef(int appId, String installDir) { this.appId = appId; this.installDir = installDir; }
    }

    /**
     * Resolve the running shortcut's Steam appId + install dir, REUSING the exact derivation
     * {@link #autoCollectSteamSavesBlocking()} uses: genuine-Steam gate (storeSource=steam OR exec under
     * steam_games/), then the tagged {@code steamAppId} fast-path, else match the exec path's
     * {@code steam_games/<folder>} against the installed-games DB. Returns null when not a genuine Steam
     * game or the appId/installDir can't be resolved. MUST be called off the main thread (queries Room).
     */
    private SteamAppRef resolveSteamAppRef() {
        final Shortcut sc = shortcut;
        if (sc == null) return null;
        int tagged;
        try {
            tagged = Integer.parseInt(sc.getExtra("steamAppId", "0").trim());
        } catch (Exception e) {
            tagged = 0;
        }
        return resolveSteamAppRefFrom(sc.path, sc.getExtra("storeSource", ""), tagged);
    }

    /**
     * Real-Steam (VAC) launch (feature M3): if this is a genuine-Steam shortcut explicitly set to
     * {@code launchMode=RealSteam}, stage the SteamLite client + our clean-room agent into this
     * container's prefix, register the game under {@code steamapps\common\<canonical>} (so genuine Steam's
     * {@code LaunchApp} is SECURE — {@code -steam}, VAC-capable), write the per-game spec, and store the
     * resulting {@link RealSteamLauncher.Plan} in {@link #realSteamPlan}. Delegates all the file work +
     * env assembly to {@link RealSteamLauncher#prepare}; this method only resolves the identity and the
     * container's {@code drive_c}, then hands off.
     *
     * <p>MUST run on the launch worker thread BEFORE {@link #getWineStartCommand()} (which reads
     * {@code realSteamPlan} to rewrite the launch target) — {@code setupXEnvironment} calls it exactly
     * there. Fully guarded: any missing prerequisite (not RealSteam / not genuine-Steam / no appId / no
     * token / install dir absent / SteamLite not downloaded) or failure leaves {@code realSteamPlan} null,
     * so {@code getWineStartCommand()} keeps the NORMAL launch and a non-RealSteam launch is byte-for-byte
     * unchanged. Never throws; never logs the refresh token (it lives only inside the plan's env map).
     */
    /**
     * Applies a Steam game's installScript.vdf local stages (Registry + Copy Files) into this
     * container just before first launch, guarded once per (appId, container). No-op for non-Steam
     * shortcuts. Runs on the setup worker thread (prefix already prepared by setupWineSystemFiles).
     */
    private void runSteamInstallScriptPreLaunch() {
        if (shortcut == null || container == null) return;
        int appId;
        try {
            appId = Integer.parseInt(shortcut.getExtra("steamAppId", "0").trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (appId <= 0) return;
        try {
            File exe = com.winhub.hernandez.core.WinePath.INSTANCE.resolveAndroidPath(container, shortcut.path);
            if (exe == null) return;
            com.winhub.hernandez.store.steamscript.InstallScriptExecutor.applyLocalStagesForLaunch(
                    this, container, appId, exe.getAbsolutePath());
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "installScript pre-launch pass failed", t);
        }
    }

    private void maybeStageRealSteam() {
        try {
            if (shortcut == null || container == null) return;
            if (!"RealSteam".equals(shortcut.getExtra("launchMode"))) return;
            if (!isGenuineSteamShortcut()) return;

            // C:\ (the guest's drive_c) = the launching container's own <rootDir>/.wine/drive_c — exactly
            // where WINEPREFIX/drive_c resolves via the xuser symlink (cf. Container.getDesktopDir() and
            // copyDllsToPrefix, which both key off the container root). Derived from the container's own
            // root dir — never a hardcoded /data/data path.
            File realSteamDriveC = new File(container.getRootDir(), ".wine/drive_c");

            SteamAppRef ref = resolveSteamAppRef();   // Room lookup — off-main OK on this worker thread
            if (ref == null) {
                Log.w("BH_REALSTEAM", "RealSteam requested but appId/installDir unresolved — "
                        + "falling back to normal launch");
                return;
            }
            SteamDatabase.GameRow row = SteamRepository.getInstance().getDatabase().getGame(ref.appId);
            String displayName = (row != null && row.name != null) ? row.name : shortcut.name;
            // EA support: an EA-published title launches through EA Desktop's chain — tell the agent
            // (WN_STEAM_LAUNCH_CHAIN) and clamp the FEX preset later (Activation64 anti-tamper needs SMC
            // checks ON). Detection is on-disk (Link2EA/EASteamProxy/__Installer/Origin/Core/Activation*).
            com.winhub.hernandez.store.EaSupport.Profile eaProfile =
                    com.winhub.hernandez.store.EaSupport.detect(new File(ref.installDir));
            realSteamEaChain = eaProfile != null && eaProfile.getEaChain();
            if (realSteamEaChain) Log.i("BH_REALSTEAM", "EA launcher-chain title detected (appId=" + ref.appId + ")");

            // Live agent↔app channel: bind the loopback listener BEFORE the guest boots so the plan
            // env can carry its port (BL_AGENT_PORT). Best-effort — 0 = no channel, launch unchanged.
            int agentPort = com.winhub.hernandez.store.SteamSessionManager.INSTANCE.openAgentChannel(agentChannelListener);
            steamAgentChannel = com.winhub.hernandez.store.SteamSessionManager.INSTANCE.agentChannel();

            realSteamPlan = RealSteamLauncher.prepare(
                    this,
                    realSteamDriveC,
                    SteamLiteComponent.INSTANCE.installDir(this),
                    shortcut,
                    ref.appId,
                    displayName,
                    ref.installDir,
                    isControllerPassthroughLaunch(),
                    agentPort,
                    // The DB's install_dir is the HOST path after download (not PICS installdir); prepare()
                    // ignores path-like values and derives the ASCII folder name from the display name.
                    row != null ? row.installDir : null,
                    realSteamEaChain);

            if (realSteamPlan != null) {
                Log.i("BH_REALSTEAM", "RealSteam launch armed (appId=" + realSteamPlan.appId
                        + ", steamapps\\common\\" + realSteamPlan.canonicalName
                        + (agentPort > 0 ? ", agent channel port " + agentPort : ", no agent channel") + ")");
                // EA titles: prepare() has just linked the depot into steamapps\common\<ascii name>.
                // The installScript Registry stage ran earlier (onCreate worker), when that link did not
                // exist yet on a fresh container, so the EA Games "Install Dir" value still points at the
                // raw Z:\steam_games\<name> depot — and EA Desktop launches the game from that value.
                // A non-ASCII depot name (e.g. the ™ in "Need for Speed™ Payback") makes Frostbite quit
                // before it ever creates a D3D device. Re-apply the stage now that the link exists; it is
                // idempotent by design (it re-applies Registry + Copy on every launch anyway).
                if (realSteamEaChain) runSteamInstallScriptPreLaunch();
                // EA titles: watch EA Desktop's own log for a refused licence so the user gets a card
                // with EA's reason instead of a black screen (see eaRefusalWatchRunnable).
                if (realSteamEaChain) {
                    eaLogOffset = -1; eaAbortSeenAt = 0; eaAbortReason = null; eaGameSpawned = false;
                    eaRefusalHandler.removeCallbacks(eaRefusalWatchRunnable);
                    eaRefusalHandler.postDelayed(eaRefusalWatchRunnable, EA_REFUSAL_POLL_MS);
                }
                // The app's own CM session is suspended later, in suspendAppSteamSessionForRealSteam()
                // — AFTER the pre-launch Steam Cloud pull and achievement seed, which still ride it.
                armAgentWatchdog();
            } else {
                Log.w("BH_REALSTEAM", "RealSteam prep incomplete — falling back to normal launch");
                closeAgentChannel();
            }
        } catch (Throwable t) {
            // Never let a RealSteam setup failure abort the launch — fall through to the normal path.
            realSteamPlan = null;
            closeAgentChannel();
            Log.w("BH_REALSTEAM", "RealSteam setup errored — falling back to normal launch", t);
        }
    }

    // ── Live SteamLite agent channel (Phase 1-C) ─────────────────────────────────────────────────
    // The in-container agent streams its login / launch / game events over loopback (see
    // SteamAgentChannel + agent-src/AGENT_CHANNEL.md). They drive the launch overlay's reassurance
    // line with the REAL state ("Signing in to Steam…", "Steam accepted the launch…", "Game running
    // (secure)") and, on a failed sign-in / insecure fallback / no sign-in within a bound, replace the
    // black screen with a failure card offering Retry / Launch with Goldberg. Everything here is
    // best-effort: an agent without the feature never connects and the launch behaves as before.
    private static final String AGENT_TAG = "BH_STEAM_AGENT";
    /** No logged_in / login_failed / game_spawned within this long of arming → "sign-in did not complete". */
    // ── "EA said no" watch ─────────────────────────────────────────────────────────────────────────
    // EA titles launch through EA Desktop, and when EA refuses to license the game EA Desktop aborts
    // the launch itself: the game exe never starts, the SteamLite agent keeps holding the session for
    // it (up to 900 s) and the user sits on a black screen. EA Desktop writes the verdict to its own
    // log inside the prefix within seconds ("Failed to license game. Aborting game launch." plus a
    // game.license.erro telemetry line whose OOALaunchFailure names the reason, device-seen:
    // "Concurrency guard limit"). Watch that log during the chain hold and turn the verdict into the
    // launch failure card with EA's reason, instead of the black screen.
    private final android.os.Handler eaRefusalHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile boolean eaRefusalShown = false;
    private volatile boolean eaGameSpawned = false;
    private long eaLogOffset = -1;
    private long eaAbortSeenAt = 0;
    private String eaAbortReason = null;
    private static final long EA_REFUSAL_POLL_MS = 2000L;
    private static final long EA_REFUSAL_REASON_GRACE_MS = 5000L;
    // NOTE: winStarted is NOT a stop condition here. On an EA launch the first application window is
    // EA Desktop's own ("Preparing game…"), which flips winStarted and dismisses the launch screen long
    // before EA has decided anything; the refusal comes after that. Only the game actually starting
    // (agent game_spawned), a shown card, or exit() ends the watch. Device-seen: r1 stopped at
    // winStarted and the refusal went unreported.
    private final Runnable eaRefusalWatchRunnable = new Runnable() {
        @Override public void run() {
            if (exiting || eaGameSpawned || eaRefusalShown) return;
            try { pollEaDesktopLogForRefusal(); } catch (Throwable ignored) {}
            if (!exiting && !eaGameSpawned && !eaRefusalShown)
                eaRefusalHandler.postDelayed(this, EA_REFUSAL_POLL_MS);
        }
    };

    private File eaDesktopLogFile() {
        if (container == null) return null;
        return new File(container.getRootDir(), ".wine/drive_c/ProgramData/EA Desktop/Logs/EADesktop.log");
    }

    /** Reads only what EA Desktop appended since the watch started; older verdicts never count. */
    private void pollEaDesktopLogForRefusal() throws java.io.IOException {
        File log = eaDesktopLogFile();
        if (log == null || !log.isFile()) return;
        long len = log.length();
        if (eaLogOffset < 0) { eaLogOffset = len; return; }          // baseline = size at watch start
        if (len < eaLogOffset) eaLogOffset = 0;                       // rotated / truncated
        if (len > eaLogOffset) {
            byte[] buf = new byte[(int) Math.min(len - eaLogOffset, 4 * 1024 * 1024)];
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(log, "r")) {
                raf.seek(eaLogOffset);
                int n = raf.read(buf);
                eaLogOffset += Math.max(n, 0);
                String chunk = new String(buf, 0, Math.max(n, 0), java.nio.charset.StandardCharsets.UTF_8);
                if (eaAbortSeenAt == 0 && (chunk.contains("Aborting game launch") || chunk.contains("Failed to request new license"))) {
                    eaAbortSeenAt = android.os.SystemClock.uptimeMillis();
                    Log.w("BH_REALSTEAM", "EA Desktop aborted the launch (licence refused)");
                }
                int i = chunk.indexOf("\"OOALaunchFailure\":\"");
                if (i >= 0) {
                    int start = i + "\"OOALaunchFailure\":\"".length();
                    int end = chunk.indexOf('"', start);
                    if (end > start) eaAbortReason = chunk.substring(start, end);
                }
            }
        }
        // Show once the abort is seen and either the reason arrived or the grace period passed.
        if (eaAbortSeenAt != 0 && (eaAbortReason != null
                || android.os.SystemClock.uptimeMillis() - eaAbortSeenAt > EA_REFUSAL_REASON_GRACE_MS)) {
            showEaRefusal(eaAbortReason);
        }
    }

    /** Plain-words version of EA's OOALaunchFailure string (EA's own wording is kept in the detail). */
    private static String eaRefusalInPlainWords(String reason) {
        if (reason == null) return "EA Desktop refused to license the game and did not start it.";
        String r = reason.toLowerCase();
        if (r.contains("concurrency") || r.contains("limit"))
            return "EA's activation limit for this game was hit: too many activations in a short time, or too many "
                    + "computers holding it (every WinHub container counts as a separate PC to EA).";
        if (r.contains("entitle") || r.contains("not owned") || r.contains("ownership"))
            return "EA reports this account does not own the game.";
        if (r.contains("offline") || r.contains("network") || r.contains("connect"))
            return "EA Desktop could not reach EA's licence servers.";
        return "EA Desktop refused to license the game and did not start it.";
    }

    private void showEaRefusal(String reason) {
        if (eaRefusalShown) return;
        eaRefusalShown = true;
        Log.w("BH_REALSTEAM", "EA refused the licence (" + reason + ") — showing the EA card");
        eaRefusalHandler.removeCallbacks(eaRefusalWatchRunnable);
        final String logDir = com.winhub.hernandez.core.LogLocation.resolveLogDir(this).getAbsolutePath();
        final boolean logging = isLaunchLoggingEnabled();
        final Shortcut sc = shortcut;
        final Container c = container;
        final String game = sc != null ? sc.name : "the game";
        final String detail = eaRefusalInPlainWords(reason)
                + (reason != null ? " EA's reason: \"" + reason + "\"." : "")
                + " Try again later; if it keeps happening, use \"Deauthorize computers\" in your EA account's "
                + "security settings.";
        // The agent is still holding the Steam session for a game that will not come — release it so
        // the guest tears down cleanly behind the card (Close finishes the session either way).
        try { if (steamAgentChannel != null) steamAgentChannel.requestLogoff(); } catch (Throwable ignored) {}
        runOnUiThread(() -> {
            if (exiting || preloaderDialog == null) return;
            cancelLaunchTimers();
            java.util.List<com.winhub.hernandez.core.FailureAction> actions = new ArrayList<>();
            if (sc != null && c != null) {
                actions.add(new com.winhub.hernandez.core.FailureAction("Retry", true, () -> {
                    com.winhub.hernandez.store.SteamSessionManager.INSTANCE.setPendingRelaunch(
                            getApplicationContext(), sc.file.getPath(), c.id,
                            com.winhub.hernandez.store.SteamSessionManager.RelaunchMode.STEAMLITE);
                    exit();
                }));
            }
            preloaderDialog.fail("EA Desktop", "EA Desktop couldn't start " + game, detail, logDir, logging, actions);
        });
    }

    private static final long AGENT_LOGIN_TIMEOUT_MS = 75_000L;
    private com.winhub.hernandez.store.SteamAgentChannel steamAgentChannel = null;
    private volatile boolean agentConnected = false;
    private volatile boolean agentLoginResolved = false;
    /** WN_STEAM_VAC policy the agent echoed on insecure_fallback (absent/older agent = VAC). Picks the
     *  game_spawned wording: a non-VAC title's direct start is not an "insecure" outcome. */
    private volatile boolean agentFallbackVac = true;
    private final android.os.Handler agentWatchdog = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable agentWatchdogRunnable = () -> {
        if (agentLoginResolved || winStarted || exiting) return;
        if (!agentConnected) {
            Log.i(AGENT_TAG, "watchdog: agent never connected (no channel support?) — leaving the launch alone");
            return;
        }
        Log.w(AGENT_TAG, "watchdog: no sign-in result from the agent within " + (AGENT_LOGIN_TIMEOUT_MS / 1000) + "s");
        showAgentFailure("Steam sign-in",
                "Steam sign-in did not complete",
                "The Steam client inside the container reported no sign-in result within "
                        + (AGENT_LOGIN_TIMEOUT_MS / 1000) + "s.");
    };

    private final com.winhub.hernandez.store.SteamAgentChannel.Listener agentChannelListener =
            new com.winhub.hernandez.store.SteamAgentChannel.Listener() {
        @Override public void onAgentConnected() {
            agentConnected = true;
            preloaderHint("Steam client started — signing in…");
        }
        @Override public void onAgentDisconnected() {
            // game_exited / shutdown already said why; the Friends tab swaps to its "relay stopped" line.
            try { com.winhub.hernandez.store.InGameFriendsSource.INSTANCE.onRelayDropped(); } catch (Throwable ignored) {}
        }
        @Override public void onAgentEvent(String ev, org.json.JSONObject obj) {
            switch (ev) {
                case "started":
                    preloaderHint("Signing in to Steam…");
                    break;
                case "logged_in":
                    agentLoginResolved = true;
                    preloaderHint("Signed in to Steam — launching the game…");
                    break;
                case "login_failed": {
                    agentLoginResolved = true;
                    int er = obj.optInt("eresult", 0);
                    String reason = obj.optString("reason", "");
                    showAgentFailure("Steam sign-in",
                            "Steam rejected the sign-in" + (reason.isEmpty() ? "" : " (" + reason + ")"),
                            (er != 0 ? "EResult " + er + " — " : "")
                                    + "The saved Steam sign-in did not work inside the container. "
                                    + "Retry, sign in again from the Steam tab, or launch offline with Goldberg.");
                    break;
                }
                case "appinfo": {
                    String st = obj.optString("state", "");
                    if ("not_installed".equals(st) || "timeout".equals(st))
                        preloaderHint("Steam doesn't see the game as installed — launching anyway…");
                    break;
                }
                case "launch_accepted":
                    preloaderHint(realSteamEaChain
                            ? "Steam accepted the launch — starting EA Desktop (sign in to EA if it asks)…"
                            : "Steam accepted the launch — starting the game…");
                    break;
                case "launch_refused":
                    preloaderHint("Steam refused the launch (" + obj.optString("reason", "") + ") — trying a direct start…");
                    break;
                case "insecure_fallback": {
                    agentLoginResolved = true;
                    // Agent p3b tags the fallback with the WN_STEAM_VAC policy the app sent: vac=false means
                    // the title never needed a Steam-owned launch (no VAC marker in its app-info / user said
                    // so), so the direct start is the normal outcome — one reassurance line, no failure
                    // card. Absent field (older agent) = treat as VAC.
                    boolean vac = obj.optBoolean("vac", true);
                    agentFallbackVac = vac;
                    if (!vac) {
                        preloaderHint("Steam couldn't start the game itself — started it directly (fine for this title)");
                    } else if (!winStarted) {
                        showAgentFailure("Steam launch",
                                "Steam did not start the game — insecure fallback",
                                "Steam's LaunchApp refused (" + obj.optString("reason", "") + "). The game can still run "
                                        + "WITHOUT Steam protection: VAC servers will reject it and live-service titles may "
                                        + "report a wrong build. Retry, launch offline with Goldberg, or keep going insecure.",
                                true);
                    } else {
                        preloaderHint("Running WITHOUT Steam protection (insecure fallback)");
                    }
                    break;
                }
                case "friends_relay":
                    // Agent p3b: the in-game friends/chat relay verdict, ~5 s after spawn. Drives the
                    // drawer's Friends tab (live = tab appears; off after live = "relay stopped" line).
                    Log.i(AGENT_TAG, "friends_relay: " + obj);
                    try {
                        com.winhub.hernandez.store.InGameFriendsSource.INSTANCE.onRelayVerdict(
                                "live".equals(obj.optString("state", "")));
                    } catch (Throwable ignored) {}
                    break;
                case "direct_exe":
                    agentLoginResolved = true;
                    preloaderHint("Starting the selected exe directly (no Steam launch)…");
                    break;
                case "game_spawned": {
                    agentLoginResolved = true;
                    eaGameSpawned = true;   // EA Desktop started the game — no refusal possible now
                    boolean secure = obj.optBoolean("secure", false);
                    // An insecure spawn after a vac=false fallback (agentFallbackVac, from the
                    // insecure_fallback event) is the normal outcome for a title without VAC — say
                    // so, instead of the warning that fits a VAC title's fallback.
                    preloaderHint(secure ? "Game running (secure Steam launch)"
                            : agentFallbackVac ? "Game running (insecure — no VAC)"
                            : "Game running (started directly — this title doesn't need a secure launch)");
                    break;
                }
                case "session_lost":
                    preloaderHint("Steam session lost — online features may drop");
                    break;
                case "game_exited":
                case "shutdown":
                    Log.i(AGENT_TAG, ev + ": " + obj);
                    break;
                default:
                    break;
            }
        }
    };

    private void preloaderHint(String text) {
        try { if (preloaderDialog != null) preloaderDialog.hint(text); } catch (Throwable ignored) {}
    }

    private void armAgentWatchdog() {
        agentWatchdog.removeCallbacks(agentWatchdogRunnable);
        agentWatchdog.postDelayed(agentWatchdogRunnable, AGENT_LOGIN_TIMEOUT_MS);
    }

    private void closeAgentChannel() {
        agentWatchdog.removeCallbacks(agentWatchdogRunnable);
        try { com.winhub.hernandez.store.SteamSessionManager.INSTANCE.closeAgentChannel(); } catch (Throwable ignored) {}
    }

    private void showAgentFailure(String stage, String what, String detail) {
        showAgentFailure(stage, what, detail, false);
    }

    /**
     * Failure card for an agent-reported problem, with "Retry" (re-runs the SteamLite pre-flight +
     * launch from the library) and "Launch with Goldberg" (offline) — both record a pending relaunch
     * the library screen consumes after this session's normal exit path restarts the app — plus an
     * optional "Keep going" that just returns to the launch spinner.
     */
    private void showAgentFailure(String stage, String what, String detail, boolean offerContinue) {
        final String logDir = com.winhub.hernandez.core.LogLocation.resolveLogDir(this).getAbsolutePath();
        final boolean logging = isLaunchLoggingEnabled();
        final Shortcut sc = shortcut;
        final Container c = container;
        runOnUiThread(() -> {
            if (exiting || preloaderDialog == null) return;
            cancelLaunchTimers();
            java.util.List<com.winhub.hernandez.core.FailureAction> actions = new ArrayList<>();
            if (offerContinue) {
                actions.add(new com.winhub.hernandez.core.FailureAction("Keep going", false,
                        () -> { try { preloaderDialog.enterGuest("Waiting for the game to render…"); } catch (Throwable ignored) {} }));
            }
            if (sc != null && c != null) {
                actions.add(new com.winhub.hernandez.core.FailureAction("Launch with Goldberg", false, () -> {
                    com.winhub.hernandez.store.SteamSessionManager.INSTANCE.setPendingRelaunch(
                            getApplicationContext(), sc.file.getPath(), c.id,
                            com.winhub.hernandez.store.SteamSessionManager.RelaunchMode.GOLDBERG);
                    exit();
                }));
                actions.add(new com.winhub.hernandez.core.FailureAction("Retry", true, () -> {
                    com.winhub.hernandez.store.SteamSessionManager.INSTANCE.setPendingRelaunch(
                            getApplicationContext(), sc.file.getPath(), c.id,
                            com.winhub.hernandez.store.SteamSessionManager.RelaunchMode.STEAMLITE);
                    exit();
                }));
            }
            preloaderDialog.fail(stage, what, detail, logDir, logging, actions);
        });
    }

    /**
     * Take the app's own Steam CM session down for a real-Steam launch so the in-guest agent is the
     * account's only session. No-op unless {@link #maybeStageRealSteam()} armed a plan. Must run on the
     * launch worker AFTER every pre-launch consumer of the app session (Steam Cloud pull, achievement
     * seed) and BEFORE the guest starts. Never throws.
     */
    private void suspendAppSteamSessionForRealSteam() {
        if (realSteamPlan == null || realSteamSessionHeld) return;
        try {
            SteamRepository.getInstance().suspendForRealSteam();
            realSteamSessionHeld = true;
        } catch (Throwable t) {
            Log.w("BH_REALSTEAM", "could not suspend the app's Steam session — the game may hit "
                    + "a session conflict", t);
        }
    }

    /** True while this launch announced "in game" for a Goldberg/Raw Steam-origin game (see below). */
    private volatile boolean offlinePresenceAnnounced = false;

    /**
     * Steam-parity presence for launches that do NOT run the genuine client: a Steam-origin shortcut
     * in Goldberg or Raw mode reports {@code CMsgClientGamesPlayed} through the app's own CM session
     * (friends see "playing <game>", Steam accrues playtime). Strictly gated: never when a RealSteam
     * plan is armed (the in-guest client reports itself and the app session is paused), never for
     * non-Steam shortcuts, never while the app session is suspended, and off when the user turned the
     * "Show me as in-game for offline launches" setting off. Runs on the launch worker (Room lookup).
     * Never throws; a failure only means no presence.
     */
    private void announceOfflineSteamPresence() {
        try {
            if (realSteamPlan != null || realSteamSessionHeld) return;
            if (shortcut == null || !isGenuineSteamShortcut()) return;
            if (!SteamPrefs.INSTANCE.isOfflinePresenceEnabled(getApplicationContext())) return;
            SteamRepository repo = SteamRepository.getInstance();
            if (repo.isSuspendedForRealSteam()) return;
            SteamAppRef ref = resolveSteamAppRef();
            if (ref == null || ref.appId <= 0) {
                Log.i("BL_STEAM_PRESENCE", "offline presence: appId unresolved — skip");
                return;
            }
            boolean sent = repo.setInGamePresence(ref.appId);
            offlinePresenceAnnounced = true;   // cleared on exit even if the send waits for a reconnect
            Log.i("BL_STEAM_PRESENCE", "offline presence: in game appId=" + ref.appId + " (sent=" + sent
                    + ", mode=" + shortcut.getExtra("launchMode", "") + ")");
        } catch (Throwable t) {
            Log.w("BL_STEAM_PRESENCE", "offline presence announce failed", t);
        }
    }

    /** Inverse of {@link #announceOfflineSteamPresence()}; idempotent, never throws. */
    private void clearOfflineSteamPresence(String why) {
        if (!offlinePresenceAnnounced) return;
        offlinePresenceAnnounced = false;
        try {
            SteamRepository.getInstance().clearInGamePresence();
            Log.i("BL_STEAM_PRESENCE", "offline presence cleared (" + why + ")");
        } catch (Throwable t) {
            Log.w("BL_STEAM_PRESENCE", "offline presence clear failed (" + why + ")", t);
        }
    }

    /**
     * Give the account back to the app's own Steam CM session after a real-Steam launch: the inverse
     * of {@link #suspendAppSteamSessionForRealSteam()}. Idempotent (guarded by
     * {@link #realSteamSessionHeld}) and a no-op for every non-RealSteam launch, so it is safe to call
     * from both the normal exit worker (after the guest — and the agent's own Steam_LogOff — is gone)
     * and onDestroy (abnormal teardown). {@code awaitLoggedInMs > 0} blocks the CALLER for the fresh
     * logon and must only be used off the UI thread. Never throws.
     */
    private void releaseRealSteamSession(String why, long awaitLoggedInMs) {
        // The agent channel dies with the guest; drop the listener + port (event lines stay readable
        // on steamAgentChannel for the log collector).
        closeAgentChannel();
        if (!realSteamSessionHeld) return;
        realSteamSessionHeld = false;
        try {
            boolean online = SteamRepository.getInstance().resumeAfterRealSteam(awaitLoggedInMs);
            Log.i("BH_REALSTEAM", "app Steam session released (" + why + ", loggedIn=" + online + ")");
        } catch (Throwable t) {
            Log.w("BH_REALSTEAM", "resume of the app's Steam session errored (" + why + ")", t);
        }
    }

    /**
     * Resolve a Steam appId + install dir from raw identity signals (exec path / storeSource / tagged
     * appId) rather than requiring the live {@link #shortcut}. Extracted from {@link #resolveSteamAppRef()}
     * (which now just feeds it the shortcut's signals — behaviour is byte-identical for that caller) so the
     * achievement hook can also resolve when {@code shortcut} is null, off a fallback identity rebuilt from
     * the launch intent / container. Genuine-Steam gate (storeSource=steam OR exec under steam_games/),
     * then the tagged appId fast-path, else match the exec path's {@code steam_games/<folder>} against the
     * installed-games DB. Returns null when not genuine-Steam or the appId/installDir can't be resolved.
     * MUST be called off the main thread (queries Room).
     */
    private SteamAppRef resolveSteamAppRefFrom(String execPath, String storeSource, int taggedAppId) {
        String pathLower = execPath != null ? execPath.toLowerCase() : "";
        boolean genuineSteam = "steam".equals(storeSource) || pathLower.contains("steam_games");
        if (!genuineSteam) {
            logResolveSteamAppRefFailure("not genuine-Steam", execPath, storeSource, taggedAppId);
            return null;
        }

        int appId = taggedAppId;
        String installDir = "";
        try {
            SteamDatabase db = SteamRepository.getInstance().getDatabase();

            // (1) Tagged appId fast-path — the shortcut carried a real steamAppId.
            if (appId > 0) {
                SteamDatabase.GameRow row = db.getGame(appId);
                installDir = (row != null && row.installDir != null) ? row.installDir : "";
            }

            // Installed set — fetched once (lazily), reused by the DB-match fallbacks below.
            List<SteamDatabase.GameRow> installed = null;

            // (2) Segment match: exec path's steam_games/<folder> vs an installed row's steam_games/<folder>
            //     (case-insensitive, slash-normalized so "Half-Life" can't false-match "Half-Life 2").
            String folder = steamGamesFolderOf(execPath);
            if ((appId <= 0 || installDir.isEmpty()) && folder != null) {
                installed = db.getInstalledGames();
                if (installed != null) {
                    for (SteamDatabase.GameRow r : installed) {
                        if (installDirMatchesFolder(r.installDir, folder)) {
                            appId = r.appId;
                            installDir = (r.installDir != null) ? r.installDir : "";
                            break;
                        }
                    }
                }
            }

            // (3) NEW lenient fallback for legacy / drive-mapped exec paths that don't literally carry a
            //     steam_games/<folder> segment (untagged legacy shortcuts, backslash Z:\ paths, etc.): for
            //     each installed row, match if the normalized+lowercased exec path CONTAINS that row's
            //     normalized+lowercased install_dir, OR contains "/<install_dir basename>/" (the game folder).
            if (appId <= 0 || installDir.isEmpty()) {
                if (installed == null) installed = db.getInstalledGames();
                String execNorm = pathNorm(execPath);
                if (installed != null && !execNorm.isEmpty()) {
                    for (SteamDatabase.GameRow r : installed) {
                        String instNorm = pathNorm(r.installDir);
                        while (instNorm.endsWith("/")) instNorm = instNorm.substring(0, instNorm.length() - 1);
                        if (instNorm.isEmpty()) continue;
                        String base = basenameNorm(instNorm);
                        boolean hit = execNorm.contains(instNorm)
                                || (!base.isEmpty() && execNorm.contains("/" + base + "/"));
                        if (hit) {
                            appId = r.appId;
                            installDir = (r.installDir != null) ? r.installDir : "";
                            break;
                        }
                    }
                }
            }

            // (4) NEW last-ditch: genuine-Steam exec path but still unresolved and EXACTLY ONE steam game is
            //     installed → it can only be that one.
            if (appId <= 0 || installDir.isEmpty()) {
                if (installed == null) installed = db.getInstalledGames();
                if (installed != null && installed.size() == 1) {
                    SteamDatabase.GameRow only = installed.get(0);
                    if (only.installDir != null && !only.installDir.isEmpty()) {
                        appId = only.appId;
                        installDir = only.installDir;
                    }
                }
            }
        } catch (Throwable t) {
            Log.w("BH_SAVE_SYNC", "resolveSteamAppRef errored", t);
        }
        if (appId <= 0 || installDir.isEmpty()) {
            logResolveSteamAppRefFailure("unresolved appId/installDir", execPath, storeSource, taggedAppId);
            return null;
        }
        return new SteamAppRef(appId, installDir);
    }

    /** Slash-normalized ("\\"->"/") + lowercased form of a path, or "" for null. */
    private static String pathNorm(String p) {
        return p == null ? "" : p.replace('\\', '/').toLowerCase();
    }

    /** Last non-empty path segment (basename) of an already slash-normalized path, or "". */
    private static String basenameNorm(String norm) {
        if (norm == null || norm.isEmpty()) return "";
        String s = norm;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    /**
     * B1 diagnostic — dump exactly why {@link #resolveSteamAppRefFrom} gave up so a device log stops the
     * guessing: the raw identity signals, the extracted steam_games/<folder>, and the whole installed-games
     * table (appId + install_dir). Fully guarded — never throws, never blocks the launch.
     */
    private void logResolveSteamAppRefFailure(String reason, String execPath, String storeSource, int taggedAppId) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("resolveSteamAppRef FAIL (").append(reason).append("): execPath=").append(execPath)
              .append(" storeSource=").append(storeSource)
              .append(" taggedAppId=").append(taggedAppId)
              .append(" steamGamesFolder=").append(steamGamesFolderOf(execPath));
            List<SteamDatabase.GameRow> installed =
                    SteamRepository.getInstance().getDatabase().getInstalledGames();
            sb.append(" installedCount=").append(installed != null ? installed.size() : 0);
            if (installed != null) {
                for (SteamDatabase.GameRow r : installed) {
                    sb.append(" [appId=").append(r.appId)
                      .append(" installDir=").append(r.installDir).append("]");
                }
            }
            Log.i("BH_STEAM_ACHV", sb.toString());
        } catch (Throwable t) {
            Log.w("BH_STEAM_ACHV", "resolveSteamAppRef failure-diagnostic errored", t);
        }
    }

    /** Raw Steam-identity signals (exec path / storeSource / tagged appId) — from the live shortcut, else
     *  rebuilt from the launch intent's {@code shortcut_path} .desktop (+ container). Fields may be empty. */
    private static final class SteamIdentity {
        final String execPath;
        final String storeSource;
        final int taggedAppId;
        SteamIdentity(String execPath, String storeSource, int taggedAppId) {
            this.execPath = execPath;
            this.storeSource = storeSource != null ? storeSource : "";
            this.taggedAppId = taggedAppId;
        }
        /** Mirrors {@link #isGenuineSteamShortcut()} but off raw signals (survives a null shortcut). */
        boolean isGenuineSteam() {
            if ("steam".equals(storeSource)) return true;
            return execPath != null && execPath.toLowerCase().contains("steam_games");
        }
    }

    /**
     * Build the running game's Steam identity, preferring the live {@link #shortcut}. If the shortcut is
     * null on this launch path, rebuild it from the launch intent's {@code shortcut_path} .desktop — parsed
     * the same way {@code onCreate} builds {@code shortcut} — so a genuine-Steam game stays identifiable for
     * seed/schema/watch even when the Shortcut object never got constructed. Never returns null (empty
     * fields when nothing identifies the game); fully guarded so a parse failure can't crash the launch.
     */
    private SteamIdentity resolveSteamIdentity() {
        final Shortcut sc = shortcut;
        if (sc != null) {
            int tagged;
            try {
                tagged = Integer.parseInt(sc.getExtra("steamAppId", "0").trim());
            } catch (Exception e) {
                tagged = 0;
            }
            return new SteamIdentity(sc.path, sc.getExtra("storeSource", ""), tagged);
        }
        // Fallback: the Shortcut object is null but the launch intent may still name the .desktop, whose
        // Exec line (steam_games/<folder>) + Extra Data (steamAppId/storeSource) identify the game. Parsing
        // it also lets the DB match in resolveSteamAppRefFrom stand in for the "container's game exec path".
        try {
            Intent li = getIntent();
            String shortcutPath = (li != null) ? li.getStringExtra("shortcut_path") : null;
            if (shortcutPath != null && !shortcutPath.isEmpty() && container != null) {
                java.io.File f = new java.io.File(shortcutPath);
                if (f.isFile()) {
                    Shortcut probe = new Shortcut(container, f);
                    int tagged;
                    try {
                        tagged = Integer.parseInt(probe.getExtra("steamAppId", "0").trim());
                    } catch (Exception e) {
                        tagged = 0;
                    }
                    Log.i("BH_STEAM_ACHV", "seed/watch: rebuilt identity from intent shortcut_path (exec="
                            + probe.path + ", storeSource=" + probe.getExtra("storeSource", "")
                            + ", steamAppId=" + tagged + ")");
                    return new SteamIdentity(probe.path, probe.getExtra("storeSource", ""), tagged);
                }
            }
        } catch (Throwable t) {
            Log.w("BH_STEAM_ACHV", "seed/watch: intent-shortcut identity fallback failed", t);
        }
        return new SteamIdentity(null, "", 0);
    }

    /**
     * On game exit, push a genuine Steam-library game's saves UP to Steam Cloud (local → cloud) — the
     * Steam-parity mirror of {@link #autoUploadGogSavesBlocking()}. Additive to the unconditional local
     * Collect above: this ONLY runs when the Save Manager toggle {@code auto_upload_steam_on_exit} is on
     * AND the user has accepted the third-party cloud disclaimer (checked by the caller).
     *
     * Best-effort + fully guarded (logs to "BH_SAVE_SYNC"). The blocking cloud sync runs on its own
     * worker thread and we bound-wait on a latch so a stalled network can never freeze game-exit, while
     * the copy still finishes before {@link AppUtils#restartApplication}'s exit(0) aborts it. Safe by
     * construction: {@link SteamCloudSaveManager#syncToCloudBlocking} only ADDS/REPLACES cloud files.
     */
    private void autoUploadSteamSavesBlocking() {
        try {
            final Context appCtx = getApplicationContext();
            // We're on the exit worker thread ("BH-ExitSaveBackup") → Room query is safely off-main.
            final SteamAppRef ref = resolveSteamAppRef();
            if (ref == null) {
                Log.i("BH_SAVE_SYNC", "auto-upload Steam: could not resolve appId — skip");
                return;
            }
            try { if (preloaderDialog != null) preloaderDialog.hint(getString(R.string.saving_on_exit)); } catch (Throwable ignored) {}

            final CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    // .INSTANCE. mirrors the existing SteamCloudSaveManager.INSTANCE.collectFromContainer
                    // call in this file (a @JvmStatic method is still callable this way, so this is
                    // robust whether or not the frozen method is annotated static).
                    String summary = SteamCloudSaveManager.INSTANCE.syncToCloudBlocking(appCtx, ref.appId, ref.installDir);
                    Log.i("BH_SAVE_SYNC", "auto-upload Steam on exit (appId " + ref.appId + "): " + summary);
                    try { if (preloaderDialog != null) preloaderDialog.hint(summary); } catch (Throwable ignored) {}
                } catch (Throwable t) {
                    Log.w("BH_SAVE_SYNC", "auto-upload Steam on exit failed (appId " + ref.appId + ")", t);
                } finally {
                    latch.countDown();
                }
            }, "BH-SteamCloudAutoUpload").start();

            // Network op — bounded so a stalled upload never hangs game-exit (Steam saves are small).
            if (!latch.await(20, TimeUnit.SECONDS))
                Log.w("BH_SAVE_SYNC", "auto-upload Steam on exit timed out (20s) — proceeding with exit");
        } catch (Throwable t) {
            Log.w("BH_SAVE_SYNC", "auto-upload Steam on exit wrapper errored", t);
        }
    }

    /**
     * Before the guest boots, pull a genuine Steam-library game's saves DOWN from Steam Cloud (cloud →
     * local, newest-wins) — the Steam-parity mirror of {@link #autoDownloadGogSavesBlocking()}. Called
     * from {@link #setupXEnvironment()} on the launch WORKER thread just before the guest starts, so
     * blocking here is safe and naturally GATES the launch until the pull completes or its bound elapses.
     *
     * Best-effort + fully guarded: no-op for non-Steam games / when the toggle is off / when the appId
     * can't be resolved. {@link SteamCloudSaveManager#syncFromCloudNewestWins} never overwrites a newer
     * local save, so an offline save since the last upload is preserved.
     */
    private void autoDownloadSteamSavesBlocking() {
        try {
            final Shortcut sc = shortcut;
            if (sc == null) return;
            if (!isGenuineSteamShortcut()) return;

            // The SteamLite pre-flight (SteamSessionManager, run in the launch popup BEFORE this
            // activity opened) already did this pull — never repeat it over the game art.
            if (getIntent().getBooleanExtra(com.winhub.hernandez.store.SteamSessionManager.EXTRA_PREFLIGHT_DONE, false)) {
                Log.i("BH_SAVE_SYNC", "auto-download Steam: done by the launch pre-flight — skip");
                return;
            }

            SharedPreferences savePrefs = getSharedPreferences("save_manager_prefs", MODE_PRIVATE);
            if (!savePrefs.getBoolean("auto_download_steam_on_launch", true)) return;

            final Context appCtx = getApplicationContext();
            // We're on the launch worker thread → Room query is safely off-main.
            final SteamAppRef ref = resolveSteamAppRef();
            if (ref == null) {
                Log.i("BH_SAVE_SYNC", "auto-download Steam: could not resolve appId for " + sc.path + " — skip");
                return;
            }

            preloaderDialog.hint(getString(R.string.downloading_on_launch));
            final CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    String summary = SteamCloudSaveManager.INSTANCE.syncFromCloudNewestWins(appCtx, ref.appId, ref.installDir);
                    Log.i("BH_SAVE_SYNC", "auto-download Steam on launch (appId " + ref.appId + "): " + summary);
                    try { if (preloaderDialog != null) preloaderDialog.hint(summary); } catch (Throwable ignored) {}
                } catch (Throwable t) {
                    Log.w("BH_SAVE_SYNC", "auto-download Steam on launch failed (appId " + ref.appId + ")", t);
                } finally {
                    latch.countDown();
                }
            }, "BH-SteamCloudAutoDownload").start();

            if (!latch.await(20, TimeUnit.SECONDS))
                Log.w("BH_SAVE_SYNC", "auto-download Steam on launch timed out (20s) — launching with local saves");
        } catch (Throwable t) {
            Log.w("BH_SAVE_SYNC", "auto-download Steam on launch wrapper errored", t);
        }
    }

    /**
     * For a genuine Steam-library game running under Goldberg: (1) SEED this container's Goldberg/GSE
     * {@code achievements.json} with the user's REAL earned achievements, then (2) arm
     * {@link AchievementWatcher} on it. Guarded like {@link #autoCollectSteamSavesBlocking()} and
     * best-effort — a seed/arm failure must never block or crash the launch. No-op for non-Steam
     * shortcuts.
     *
     * ORDERING (critical) — this runs SYNCHRONOUSLY on the launch worker thread, immediately BEFORE
     * {@code environment.startEnvironmentComponents()} boots the guest, so:
     *   seed (write real earned) → watcher.start() (snapshot now INCLUDES the seeded/owned set, so
     *   owned achievements can't false-toast) → guest boots (gbe_fork reads the seeded file at startup
     *   → the game's own achievement screen reflects what the user really owns).
     * Blocking here is fine (worker thread); seeding is a small local read/merge/write (the real state
     * is normally already cached by the detail page).
     */
    private void maybeSeedAndStartAchievementWatcher() {
        try {
            // Establish the game's Steam identity from the live shortcut, else fall back to the launch
            // intent / container (the shortcut can be null on some launch paths — e.g. a launch whose
            // Shortcut object never got constructed — yet the game is still identifiable). Never null;
            // its fields are empty when unknown, and isGenuineSteam()/appId then resolve to skip.
            final SteamIdentity id = resolveSteamIdentity();
            final boolean genuine = id.isGenuineSteam();
            // Entry breadcrumb so a silent no-op is always diagnosable (this was previously an unlogged
            // guard return). shortcutGate = the raw shortcut-only signal; genuine = the effective gate
            // (identity-based, so it can still be true via the intent/container fallback when shortcut==null).
            Log.i("BH_STEAM_ACHV", "seed/watch: entry shortcut=" + (shortcut != null)
                    + " container=" + (container != null) + " genuine=" + genuine
                    + " (shortcutGate=" + isGenuineSteamShortcut() + ")");

            if (container == null) {
                Log.i("BH_STEAM_ACHV", "seed/watch: container null — skip");
                return;
            }
            if (!genuine) {
                Log.i("BH_STEAM_ACHV", "seed/watch: not a genuine-Steam shortcut — skip");
                return;
            }
            final java.io.File containerRoot = container.getRootDir();
            final Context appCtx = getApplicationContext();
            // appId resolution touches Room — we're already on the launch worker thread, so blocking OK.
            // Resolve from the identity (shortcut OR intent/container fallback) rather than the shortcut only.
            SteamAppRef ref = resolveSteamAppRefFrom(id.execPath, id.storeSource, id.taggedAppId);
            if (ref == null) {
                Log.i("BH_STEAM_ACHV", "achievement seed/watch: could not resolve appId — skip");
                return;
            }
            final int appId = ref.appId;

            // SteamLite / RealSteam mode (genuine Steam via our headless agent): the agent writes each
            // real-server unlock into C:\wn-achievement-events\ — watch that folder and pop the SAME gold
            // pill the Goldberg path pops. Gated inside on the RealSteam plan being armed
            // (realSteamPlan != null, set by maybeStageRealSteam() earlier in setupXEnvironment) — the only
            // launch where the agent actually runs and emits those files. Independent of the Goldberg
            // seed/watch below (which no-ops for a Goldberg-OFF RealSteam launch).
            maybeStartSteamLiteAchievementWatcher(appCtx, appId, containerRoot);

            // (1) Seed — only for Goldberg-on games (an OFF game runs genuine Steam, no GSE store to
            // seed). Uses the SAME GSE path the watcher watches (shared helper). Best-effort/no-op on
            // no-real-state; never wipes local unlocks.
            try {
                SteamPrefs.INSTANCE.init(appCtx); // idempotent + cheap; XServer path may not have inited it
                if (SteamPrefs.INSTANCE.getGoldbergMode(appId) != GoldbergMode.OFF) {
                    java.io.File gseFile = AchievementWatcher.gseAchievementsFile(containerRoot, appId);
                    SteamAchievementStore.seedGse(appCtx, appId, gseFile);

                    // Also ensure the gbe_fork achievement SCHEMA (definitions) exists beside each
                    // swapped steam_api dll. A fresh Goldberg apply writes this in GoldbergPatcher's
                    // sharedPrep, but an install patched BEFORE this fix never got it — writing it here
                    // makes those existing installs (e.g. HL2) work on next launch WITHOUT a manual
                    // re-apply. DEFS only (real earned state is the seedGse call above); fully guarded
                    // and a no-op when no defs are cached, so it can never break the launch. Runs here,
                    // before the guest boots, so gbe_fork reads it at startup.
                    try {
                        java.io.File installRoot = new java.io.File(ref.installDir);
                        if (installRoot.isDirectory()) {
                            for (GoldbergPatcher.PatchTarget target : GoldbergPatcher.analyze(installRoot)) {
                                java.io.File settingsDir = new java.io.File(target.getDir(), "steam_settings");
                                settingsDir.mkdirs();
                                SteamAchievementStore.writeGbeAchievementSchema(appCtx, appId, settingsDir);
                            }
                        }
                    } catch (Throwable t) {
                        Log.w("BH_STEAM_ACHV", "achievement schema ensure failed (appId=" + appId + ")", t);
                    }
                } else {
                    Log.i("BH_STEAM_ACHV", "achievement seed: Goldberg OFF for appId=" + appId + " — no seed");
                }
            } catch (Throwable t) {
                Log.w("BH_STEAM_ACHV", "GSE seed failed (appId=" + appId + ")", t);
            }

            // (2) Arm the watcher — its snapshot now includes the seeded state. start()/stop() are
            // synchronized on the instance; the post-start destroy re-check avoids leaking the watcher
            // if the activity tore down while we were seeding/arming. This hook runs from BOTH setupUI
            // and setupXEnvironment, so guard against a double-arm: the seed above is idempotent and is
            // refreshed on every call, but the watcher (FileObserver + scheduler) is armed only once.
            if (achievementWatcherArmed) {
                Log.i("BH_STEAM_ACHV", "seed/watch: watcher already armed (appId=" + appId
                        + ") — seed refreshed, not re-arming");
                return;
            }
            if (achievementWatcher == null) achievementWatcher = new AchievementWatcher();
            final AchievementWatcher watcher = achievementWatcher;
            if (isFinishing() || isDestroyed()) { watcher.stop(); return; }
            watcher.start(appCtx, appId, containerRoot);
            if (isFinishing() || isDestroyed()) { watcher.stop(); return; }
            achievementWatcherArmed = true;
        } catch (Throwable t) {
            Log.w("BH_STEAM_ACHV", "maybeSeedAndStartAchievementWatcher errored", t);
        }
    }

    /**
     * For a genuine-Steam game launched in SteamLite / RealSteam mode (our headless Steam agent drives a
     * real Steam client, so unlocks hit the REAL server — there is no local achievements.json to diff),
     * arm {@link SteamLiteAchievementWatcher} on the agent's event folder ({@code C:\wn-achievement-events\})
     * so a real-server unlock still pops the in-game gold pill.
     *
     * Gated on {@link #realSteamPlan} being non-null — the definitive "the agent is running and will emit
     * event files" signal (set by {@link #maybeStageRealSteam()} earlier in {@code setupXEnvironment}); a
     * no-op for every other launch, so an ordinary or Goldberg launch is unaffected. Best-effort + fully
     * guarded — a failure here must never block/crash the launch — and idempotent: re-entry never
     * double-arms the FileObserver. Mirrors {@link #maybeSeedAndStartAchievementWatcher()}'s envelope.
     */
    private void maybeStartSteamLiteAchievementWatcher(Context appCtx, int appId, java.io.File containerRoot) {
        try {
            if (realSteamPlan == null) return; // not a RealSteam launch → the agent emits no event files
            if (appCtx == null || containerRoot == null || appId <= 0) return;
            if (steamLiteAchievementWatcherArmed) {
                Log.i("BH_STEAM_ACHV", "steamlite watch: already armed (appId=" + appId + ") — skip");
                return;
            }
            if (steamLiteAchievementWatcher == null)
                steamLiteAchievementWatcher = new SteamLiteAchievementWatcher();
            final SteamLiteAchievementWatcher watcher = steamLiteAchievementWatcher;
            if (isFinishing() || isDestroyed()) { watcher.stop(); return; }
            watcher.start(appCtx, appId, containerRoot);
            if (isFinishing() || isDestroyed()) { watcher.stop(); return; }
            steamLiteAchievementWatcherArmed = true;
            Log.i("BH_STEAM_ACHV", "steamlite watch: armed (appId=" + appId + ")");
        } catch (Throwable t) {
            Log.w("BH_STEAM_ACHV", "maybeStartSteamLiteAchievementWatcher errored", t);
        }
    }

    /**
     * On game exit, snapshot a CUSTOM (non-Steam) game's saves into the persistent local vault
     * (<externalStorage>/Bannerlator/GameSaveVault/<key>.zip, overwriting the latest), so they
     * survive the shortcut/game being removed. Local only — no cloud.
     *
     * Same robustness envelope as the Steam collect: application context (not this dying activity),
     * the zip runs on its own worker thread, and we bound-wait on a latch so it finishes BEFORE
     * restartApplication()'s exit(0) aborts the process. Silent + best-effort; logs to "BH_SAVE_SYNC";
     * skips gracefully when there's no shortcut/container or no saves are discovered.
     */
    private void autoSnapshotCustomSavesBlocking() {
        try {
            final Shortcut sc = shortcut;
            final Container ctn = container;
            if (sc == null || ctn == null) return;

            final Context appCtx = getApplicationContext();
            final CountDownLatch latch = new CountDownLatch(1);

            new Thread(() -> {
                try {
                    CustomSaveVault.VaultResult r = CustomSaveVault.INSTANCE.snapshot(appCtx, ctn, sc);
                    if (r != null && r.getOk()) {
                        Log.i("BH_SAVE_SYNC", "auto-vault on exit (" + sc.name + "): " + r.getFileCount() + " files");
                    } else {
                        Log.i("BH_SAVE_SYNC", "auto-vault on exit (" + sc.name + "): "
                                + (r != null ? r.getError() : "null result"));
                    }
                } catch (Throwable t) {
                    Log.w("BH_SAVE_SYNC", "auto-vault on exit errored (" + sc.name + ")", t);
                } finally {
                    latch.countDown();
                }
            }, "BH-SaveAutoVault").start();

            // Bounded so a stalled snapshot (local file copy — normally sub-second) never hangs exit.
            latch.await(8, TimeUnit.SECONDS);
        } catch (Throwable t) {
            Log.w("BH_SAVE_SYNC", "auto-vault on exit wrapper errored", t);
        }
    }

    /**
     * On game exit, push a GOG-library game's saves to GOG cloud (local → cloud), the Galaxy-parity
     * auto-trigger for gap #2-P2. Mirrors {@link #autoCollectSteamSavesBlocking()}'s robustness
     * envelope: application context (not this dying activity), all work off a worker thread, and a
     * bound-wait on a latch so the upload finishes BEFORE {@link AppUtils#restartApplication}'s exit(0)
     * aborts it — while a stalled network can never freeze game-exit.
     *
     * Fully guarded + silent (logs to "BH_SAVE_SYNC"). Skips cleanly (no upload) when the game can't be
     * reverse-mapped to a gameId, has no resolvable save dir (missing container / no GOG cloud support),
     * or the save dir doesn't exist yet (unplayed → nothing to upload). Safe by construction: the
     * transport's newest-wins logic ({@link GogCloudSaveManager#uploadSaves}) never overwrites a newer
     * cloud save, so an automatic push can't clobber progress made on another device.
     */
    private void autoUploadGogSavesBlocking() {
        try {
            final Shortcut sc = shortcut;
            final Container ctn = container;
            if (sc == null || sc.path == null || ctn == null) return;

            final Context appCtx = getApplicationContext();
            // Reverse-map the running shortcut's gog_games/<dir> exec path to its GOG gameId (offline).
            final String gameId = GogCloudSavePaths.INSTANCE.gameIdForExecPath(appCtx, sc.path);
            if (gameId == null) {
                Log.i("BH_SAVE_SYNC", "auto-upload GOG: no gameId for " + sc.path + " — skip");
                return;
            }

            final CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    // Resolve the concrete save dir inside the RUNNING container's prefix. Off-main:
                    // hits the network on a cloud-location cache-miss (remote-config fetch).
                    File dir = GogCloudSavePaths.INSTANCE.resolveSaveDirectory(appCtx, gameId, ctn);
                    if (dir == null) {
                        Log.i("BH_SAVE_SYNC", "auto-upload GOG (" + gameId + "): no resolvable save dir — skip");
                        latch.countDown();
                        return;
                    }
                    if (!dir.isDirectory()) {
                        Log.i("BH_SAVE_SYNC", "auto-upload GOG (" + gameId + "): save dir absent (no saves yet) — skip");
                        latch.countDown();
                        return;
                    }
                    // uploadSaves runs on its OWN worker thread; the latch is released by its callback.
                    GogCloudSaveManager.uploadSaves(appCtx, gameId, dir, new GogCloudSaveManager.Callback() {
                        @Override public void onStatus(String message) {
                            // Surface live upload progress on the "Shutting down…" overlay so the user
                            // sees it's actively working (not frozen) and doesn't swipe it away mid-upload.
                            try { if (preloaderDialog != null) preloaderDialog.hint(message); } catch (Throwable ignored) {}
                        }
                        @Override public void onDone(String summary) {
                            Log.i("BH_SAVE_SYNC", "auto-upload GOG on exit (" + gameId + "): " + summary);
                            try { if (preloaderDialog != null) preloaderDialog.hint(summary); } catch (Throwable ignored) {}
                            latch.countDown();
                        }
                        @Override public void onError(String message) {
                            Log.w("BH_SAVE_SYNC", "auto-upload GOG on exit failed (" + gameId + "): " + message);
                            latch.countDown();
                        }
                    });
                } catch (Throwable t) {
                    Log.w("BH_SAVE_SYNC", "auto-upload GOG on exit errored", t);
                    latch.countDown();
                }
            }, "BH-GogCloudAutoUpload").start();

            // Network op, so more headroom than the local copies (8s) — but still bounded so a stalled
            // upload never hangs game-exit. GOG saves are small (config + slot files), so this is ample.
            if (!latch.await(20, TimeUnit.SECONDS))
                Log.w("BH_SAVE_SYNC", "auto-upload GOG on exit timed out (20s) — proceeding with exit");
        } catch (Throwable t) {
            Log.w("BH_SAVE_SYNC", "auto-upload GOG on exit wrapper errored", t);
        }
    }

    /**
     * Before the guest boots, pull a GOG-library game's saves DOWN from GOG cloud (cloud → local) so
     * the game starts with the latest progress — the Galaxy-parity download-on-launch trigger, paired
     * with {@link #autoUploadGogSavesBlocking()}. Called from {@link #setupXEnvironment()} on the launch
     * WORKER thread immediately before {@code startEnvironmentComponents()}, so blocking here is safe
     * (the UI thread is free, the launch preloader keeps animating) and naturally GATES the guest start
     * until the pull completes or its bound elapses.
     *
     * Best-effort + fully guarded: no-op for non-GOG games / when the toggle is off / when the game
     * can't be reverse-mapped or has no resolvable save dir (missing container / no cloud support).
     * Offline or a slow network just times out (bounded) and the game launches with its local saves —
     * a pre-launch download must NEVER block or fail a launch. Safe by construction: the transport's
     * newest-wins ({@link GogCloudSaveManager#downloadSaves}) SKIPS any file whose local copy is
     * newer-or-equal, so a save made offline since the last upload is never overwritten by an older
     * cloud copy.
     */
    private void autoDownloadGogSavesBlocking() {
        try {
            final Shortcut sc = shortcut;
            final Container ctn = container;
            if (sc == null || sc.path == null || ctn == null) return;
            if (!isGogShortcut()) return;

            SharedPreferences savePrefs = getSharedPreferences("save_manager_prefs", MODE_PRIVATE);
            if (!savePrefs.getBoolean("auto_download_gog_on_launch", true)) return;

            final Context appCtx = getApplicationContext();
            final String gameId = GogCloudSavePaths.INSTANCE.gameIdForExecPath(appCtx, sc.path);
            if (gameId == null) {
                Log.i("BH_SAVE_SYNC", "auto-download GOG: no gameId for " + sc.path + " — skip");
                return;
            }

            // Resolve the save dir inside the RUNNING container's prefix. We're already on the launch
            // worker thread, so this (network on a cloud-location cache-miss) is safely off-main.
            File dir = GogCloudSavePaths.INSTANCE.resolveSaveDirectory(appCtx, gameId, ctn);
            if (dir == null) {
                Log.i("BH_SAVE_SYNC", "auto-download GOG (" + gameId + "): no resolvable save dir — skip");
                return;
            }

            preloaderDialog.hint(getString(R.string.downloading_on_launch));
            final CountDownLatch latch = new CountDownLatch(1);
            // downloadSaves runs on its OWN worker thread; the latch is released by its callback.
            GogCloudSaveManager.downloadSaves(appCtx, gameId, dir, new GogCloudSaveManager.Callback() {
                @Override public void onStatus(String message) {
                    // Show live download progress on the launch overlay's reassurance line.
                    try { if (preloaderDialog != null) preloaderDialog.hint(message); } catch (Throwable ignored) {}
                }
                @Override public void onDone(String summary) {
                    Log.i("BH_SAVE_SYNC", "auto-download GOG on launch (" + gameId + "): " + summary);
                    // Show the outcome (e.g. "Already up to date (13 files)" / "Downloaded N") so a skip
                    // reads as up-to-date, not a silent "was it downloading?".
                    try { if (preloaderDialog != null) preloaderDialog.hint(summary); } catch (Throwable ignored) {}
                    latch.countDown();
                }
                @Override public void onError(String message) {
                    Log.w("BH_SAVE_SYNC", "auto-download GOG on launch failed (" + gameId + "): " + message);
                    latch.countDown();
                }
            });
            // Bounded so a stalled/offline network never delays the launch indefinitely; on timeout we
            // launch with whatever local saves exist (newest-wins already protected them).
            if (!latch.await(20, TimeUnit.SECONDS))
                Log.w("BH_SAVE_SYNC", "auto-download GOG on launch timed out (20s) — launching with local saves");
        } catch (Throwable t) {
            Log.w("BH_SAVE_SYNC", "auto-download GOG on launch wrapper errored", t);
        }
    }

    // Whether Wine/box64 logging is on — the single source of truth for the failure card's guidance
    // and for whether we open wine_debug.log at all (see setupXEnvironment).
    private boolean isLaunchLoggingEnabled() {
        return preferences.getBoolean("enable_wine_debug", false)
                || preferences.getBoolean("enable_box64_logs", false);
    }

    // Where DXVK/VKD3D write their logs this launch. When the Log Manager's "DXVK & VKD3D" switch is ON
    // that's the user's (per-game) log dir — visible, co-located with wine_debug.log, unchanged. When it
    // is OFF we no longer silence the wrappers: we point them at a tiny PRIVATE hudapi dir at a minimal
    // level (see DXVKConfigDialog.setEnvVars) so the in-game HUD API resolver (P3) always has ground
    // truth — critical on arm64ec, where the DX DLLs are invisible to /proc/maps. Either way the chosen
    // dir is cached in wrapperLogDir for P3 to read. The user-facing log toggle still governs VISIBLE logs.
    private File dxvkLogDir() {
        boolean dxvkLogs = preferences.getBoolean("enable_dxvk_logs", true);
        File dir = dxvkLogs
                ? com.winhub.hernandez.core.LogLocation.resolveGameLogDir(this, currentLogGameName())
                : hudApiLogDir();
        wrapperLogDir = dir;
        return dir;
    }

    // Tiny PRIVATE wrapper-log dir the HUD API resolver (P3) always has, even with the user's "DXVK &
    // VKD3D" logging switch OFF. Lives under the container's shared tmp (host-readable at
    // imageFs.getTmpDir()). setupXEnvironment clears that whole tmp per launch (so this is emptied for
    // free); we re-create it AFTER that clear (see setupXEnvironment) so the guest can write into it.
    // Startup-level logs only — no per-frame spam. Null on failure => P3 falls through to /proc/maps.
    private File hudApiLogDir() {
        try {
            File dir = new File(imageFs.getTmpDir(), "hudapi");
            if (!dir.exists()) dir.mkdirs();
            return dir.isDirectory() && dir.canWrite() ? dir : null;
        } catch (Exception ignore) { return null; }
    }

    // Arm/cancel the two "not-frozen" reassurance timers.
    private void startLaunchTimers() {
        cancelLaunchTimers();
        launchTimerHandler.postDelayed(launchSlowHintRunnable, LAUNCH_SLOW_HINT_MS);
        launchTimerHandler.postDelayed(launchStillWorkingRunnable, LAUNCH_STILL_WORKING_MS);
    }

    private void cancelLaunchTimers() {
        launchTimerHandler.removeCallbacks(launchSlowHintRunnable);
        launchTimerHandler.removeCallbacks(launchStillWorkingRunnable);
    }

    // Best-effort "open the log folder" for the failure card. Folder-opening is unevenly supported
    // across file managers, so fall back to showing the path if no handler is available.
    private void openLogFolder() {
        File dir = com.winhub.hernandez.core.LogLocation.resolveLogDir(this);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(dir.getAbsolutePath()), "resource/folder");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(intent, "Open log folder"));
        } catch (Exception e) {
            Toast.makeText(this, "Log folder: " + dir.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        sessionInForeground = false;
        // The last word on the handheld's companion screen, whatever took this session down (Exit, the
        // game's own watcher, a recents swipe, the system). Every other dismissal is about telling the
        // user something sooner; this one is the guarantee that nothing is left on the handheld
        // describing a session that no longer exists. No-op when it is already gone.
        com.winhub.hernandez.display.TvCompanionActivity.dismiss("the session is gone");
        if (inGameControlsEditor != null) {
            inGameControlsEditor.dispose();
            inGameControlsEditor = null;
        }
        modKeyHandler.removeCallbacksAndMessages(null);
        // Not on a config-change recreate: the new instance has already (or is about to) publish its own.
        if (isFinishing()) StickShaper.reset();
        com.winhub.hernandez.core.MusicPlayer.INSTANCE.setInGame(false);
        if (startProgramsExecutor != null) {
            startProgramsExecutor.shutdownNow();
            startProgramsExecutor = null;
        }
        waylandVsyncRunning = false;
        if (waylandHudThread != null) { waylandHudThread.quitSafely(); waylandHudThread = null; waylandHudSampler = null; }
        if (waylandClipboard != null) { waylandClipboard.stop(); waylandClipboard = null; }
        if (waylandTextInput != null) { waylandTextInput.stop(); waylandTextInput = null; }
        super.onDestroy();
        // The EGL render thread outlives the surface; stop it with the session.
        if (xServerView != null && xServerView.getRenderer() instanceof com.winhub.hernandez.renderer.egl.EglRenderer)
            xServerView.getRenderer().forceCleanup();
        // Power-user perf: stop the thermal watchdog and revert any privileged sysfs writes on game
        // exit (no-op unless a root toggle wrote something this session).
        com.winhub.hernandez.perf.TempWatchdog.INSTANCE.stop();
        com.winhub.hernandez.perf.PerfRevertRegistry.INSTANCE.revertAll();
        // Game xinput DLLs moved aside for Direct XInput this session go back under their own names
        // (core.GameXInputDlls; the next app start or launch catches a session that never got here).
        try {
            for (String line : com.winhub.hernandez.core.GameXInputDlls.restoreAll(gameXInputManifest()))
                Log.i("XServerDisplayActivity", "Game xinput DLL: " + line);
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "game xinput DLL restore", t);
        }
        // Release the no-root Samsung Galaxy performance boost on game exit (no-op off Samsung).
        com.winhub.hernandez.perf.galaxy.GalaxyPerfManager.stop();
        // Clear the cross-vendor "game is running" signal.
        com.winhub.hernandez.perf.GameModeSignal.exitGameplay(this);
        unregisterGyroSensor();
        unregisterAudioRouteWatcher();
        stopDxApiDetection();
        cancelLaunchTimers();
        // Abnormal teardown (no exit() worker ran): never leave the app's Steam session suspended.
        // Non-blocking — the reconnect is posted to the CM pump. No-op unless a real-Steam launch
        // suspended it this session.
        releaseRealSteamSession("activity destroyed", 0L);
        clearOfflineSteamPresence("activity destroyed");
        // Hide the drawer's Friends tab source + leave any in-game chat thread (the full Friends
        // screen's own open/close is untouched).
        try { com.winhub.hernandez.store.InGameFriendsSource.INSTANCE.disarm(); } catch (Throwable ignored) {}
        // The HDR capability report watches the same DisplayManager; drop its listener too.
        stopHdrCapabilityReport();
        // Version-A spike: unregister the display listener, dismiss the Presentation, and pull the
        // game back to the phone so nothing leaks a window on the external display.
        if (externalDisplayController != null) {
            externalDisplayController.stop();
            externalDisplayController = null;
        }
        if (castDiscovery != null) {
            castDiscovery.stop();
            castDiscovery = null;
        }
        if (gameCaster != null) {
            try { gameCaster.stop(); } catch (Exception ignored) {}
            gameCaster = null;
        }
        try { if (castSession != null) { castSession.close(); castSession = null; } } catch (Exception ignored) {}
        try { if (castHttp != null) { castHttp.stop(); castHttp = null; } } catch (Exception ignored) {}
        stopSteamControllerSupport();
        // Controller-status toast: drop the listener + any pending debounced toast so a late callback
        // can't run against a tearing-down activity.
        if (winHandler != null) winHandler.setControllerAssignmentListener(null);
        controllerToastHandler.removeCallbacks(fireControllerToast);
        // Stop the in-game achievement watcher (FileObserver + its scheduler) so a late file event
        // can't run against a tearing-down activity.
        if (achievementWatcher != null) {
            try { achievementWatcher.stop(); } catch (Throwable ignored) {}
            achievementWatcher = null;
        }
        achievementWatcherArmed = false;
        // Stop the SteamLite event-folder watcher (FileObserver + its executor) for the same reason.
        if (steamLiteAchievementWatcher != null) {
            try { steamLiteAchievementWatcher.stop(); } catch (Throwable ignored) {}
            steamLiteAchievementWatcher = null;
        }
        steamLiteAchievementWatcherArmed = false;
        // Drop the failure-card callbacks so this activity isn't retained via the static holder.
        com.winhub.hernandez.core.PreloaderState.setOnClose(null);
        com.winhub.hernandez.core.PreloaderState.setOnOpenLog(null);
        com.winhub.hernandez.core.PreloaderState.setOnCancel(null);
        // A failure card that outlives this activity (Close -> finish, or a recents swipe) would keep
        // showing on MainActivity's copy of the overlay with no owner to act on Close. Clear it.
        com.winhub.hernandez.core.PreloaderState.hideIfFailed();
        if (wineDebugLogCallback != null) {
            ProcessHelper.removeDebugCallback(wineDebugLogCallback);
            wineDebugLogCallback = null;
        }
        if (wineDebugWriter != null) {
            wineDebugWriter.close();
            wineDebugWriter = null;
        }
        // Stop publishing the lsfg-vk vsync clock on game exit.
        stopVsyncClock();
        // ... and stop polling the native LSFG readout.
        stopLsfgStatsReadout();
        if (vsyncWriteExecutor != null) {
            vsyncWriteExecutor.shutdownNow();
            vsyncWriteExecutor = null;
        }
        // Clear the FG-reset overlay state (XServerDialogState is a singleton — a torn-down reset must
        // not survive into the next game's launch).
        fgResetInProgress = false;
        XServerDialogState.INSTANCE.setFgResetPaused(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // The teardown onPause handed over for a session playing on the TV. onStop is the callback that
        // means "no longer visible to the user", so reaching it says the session really did leave the
        // screen — a home press, another app taking the TV, the system stopping us — and the guest must
        // freeze after all. The log line is deliberate: if a game on the TV still freezes when the
        // handheld is touched, this line in logcat is the proof that this build stops the session
        // instead of only pausing it, which no code in here can tell apart any earlier.
        if (tvSuspendDeferred) {
            tvSuspendDeferred = false;
            Log.i("XServerDisplayActivity", "TV: stopped while on display " + sessionDisplayId
                    + " — freezing the game after all");
            suspendSessionForBackground();
        }
        // Belt-and-suspenders: also drop controller-test isolation on stop (see onPause).
        controllerTestActive = false;
        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
        // Release the panel refresh-rate vote while backgrounded so we don't pin the display rate
        // for whatever is composited on top. onResume() re-asserts it.
        routeVrrVote(0f);
        unregisterVrrDisplayListener();
    }

    private void releasePointerCaptureIfNeeded(String reason) {
        if (pointerCaptureRequested && touchpadView != null) {
            touchpadView.releasePointerCapture();
            touchpadView.setOnCapturedPointerListener(null);
            pointerCaptureRequested = false;
            Log.d("PointerCapture", "Released: " + reason);
        }
    }

    @Override
    public void onBackPressed() {
        if (inGameControlsEditor != null) {
            if (inGameControlsEditor.handleBack()) return;
            closeInGameControlsEditor();
            return;
        }
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.openDrawer(GravityCompat.END);
            }
            else drawerLayout.closeDrawers();
        }
    }

    private void openXServerDrawer() {
        if (environment != null) {
            releasePointerCaptureIfNeeded("open-drawer/shortcut");
            if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.openDrawer(GravityCompat.END);
            } else {
                drawerLayout.closeDrawers();
            }
        }
    }


    
    private void showVibrationDialog() {
        if (winHandler == null) return;
        int max = winHandler.getMaxControllers();
        java.util.List<android.util.Pair<String, Boolean>> slots = new java.util.ArrayList<>();
        for (int i = 0; i < max; i++) {
            slots.add(new android.util.Pair<>(
                getString(R.string.vibration_slot, i + 1),
                winHandler.isVibrationEnabledForSlot(i)));
        }
        // Convert android.util.Pair to kotlin.Pair for XServerDialogState
        java.util.List<kotlin.Pair<String, Boolean>> kSlots = new java.util.ArrayList<>();
        for (android.util.Pair<String, Boolean> p : slots) {
            kSlots.add(new kotlin.Pair<>(p.first, p.second));
        }
        XServerDialogState ds = XServerDialogState.INSTANCE;
        ds.setVibrationSlots(kSlots);
        ds.onVibrationSlotChanged = (slot, enabled) -> winHandler.setVibrationEnabledForSlot(slot, enabled);
        ds.setVibrationMasterEnabled(winHandler.isVibrationMasterEnabled());
        ds.onVibrationMasterChanged = (enabled) -> winHandler.setVibrationMasterEnabled(enabled);
        ds.show(XServerDialogState.ActiveDialog.VIBRATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && waylandClipboard != null) waylandClipboard.refresh();

        if (hasFocus && (cursorLock || isRelativeMouseMovement || waylandPointerLocked) && inGameControlsEditor == null) {
            touchpadView.requestPointerCapture();
            pointerCaptureRequested = true;
            touchpadView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                @Override
                public boolean onCapturedPointer(View view, MotionEvent event) {
                    handleCapturedPointer(event);
                    return true;
                }
            });
        }
        else if (!hasFocus) {
            touchpadView.releasePointerCapture();
            touchpadView.setOnCapturedPointerListener(null);
            pointerCaptureRequested = false;
        }
    }

    // private void extractInputDLLs() {
    //     String inputAsset = "input_dlls.tzst";
    //     File wineFolder = new File(imageFs.getWinePath() + "/lib/wine/");
    //     boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, inputAsset, wineFolder);
    //     if (!success)
    //         Log.d("XServerDisplayActivity", "Failed to extract input dlls");
    // }

    // Bump when the bundled container_pattern_common.tzst CONTENT changes but the
    // versionCode does not (it is frozen at 67 between stable releases). This forces
    // an existing container to re-run applyGeneralPatches on its next launch so newly
    // bundled pattern files land in already-created prefixes. The re-extract is an
    // additive tar overlay (no wipe, no .reg in the pattern), so existing game data and
    // registry customisations are preserved. Empty on legacy containers -> trips once.
    // History: "1" = add Pale Moon browser (2026-08-05).
    //          "2" = Banner File Manager 1.2.0 — fast large folders + version metadata (2026-08-05).
    //          "3" = AIO Graphics Test 2.0.0 added alongside v1 (2026-08-05).
    //          "4" = AIO Graphics Test 2.0.0 fixed exe — default now opens the v2 shell (supersedes the "3" test bake).
    //          "5" = AIO Graphics Test 2.0.1 (+ OpenGL start-menu entry) (2026-08-06).
    //          "6" = drop Pale Moon DESKTOP shortcut (kept in Start Menu only); the repacked
    //                pattern no longer ships it, and existing containers get it deleted on next
    //                launch via removePaleMoonDesktopShortcut() (2026-08-06).
    //          "7" = adaptive PulseAudio module: repacked pulseaudio.tzst carries the new
    //                module-aaudio-sink (performance_mode/adaptive/buffer modargs). MUST bump so
    //                existing containers re-extract it — vc is frozen, so without this the old module
    //                lingers and the new default.pa args are rejected → silence (2026-08-10).
    private static final String PATTERN_CONTENT_VERSION = "7";

    private void setupWineSystemFiles() {
        String appVersion = String.valueOf(AppUtils.getVersionCode(this));
        String imgVersion = String.valueOf(imageFs.getVersion());
        boolean containerDataChanged = false;

        ContainerOverlayMigrator.migrateIfNeeded(this, contentsManager, container);

        if (!container.getExtra("appVersion").equals(appVersion)
                || !container.getExtra("imgVersion").equals(imgVersion)
                || !container.getExtra("patternVersion").equals(PATTERN_CONTENT_VERSION)) {
            applyGeneralPatches(container);
            container.putExtra("appVersion", appVersion);
            container.putExtra("imgVersion", imgVersion);
            container.putExtra("patternVersion", PATTERN_CONTENT_VERSION);
            containerDataChanged = true;
        }

        String dxwrapper = this.dxwrapper;

        if (dxwrapper.contains("dxvk")) {
            String dxvkWrapper = "dxvk-" + dxwrapperConfig.get("version");
            String vkd3dWrapper = "vkd3d-" + dxwrapperConfig.get("vkd3dVersion");
            String ddrawrapper = dxwrapperConfig.get("ddrawrapper");
            dxwrapper = dxvkWrapper + ";" + vkd3dWrapper + ";" + ddrawrapper + d7vkMarker(ddrawrapper);
        }
        else if (dxwrapper.contains("vegas")) {
            String vegasVersion = dxwrapperConfig.get("version");
            if (vegasVersion == null || vegasVersion.isEmpty())
                vegasVersion = DefaultVersion.getVegasDefault();
            String ddrawrapper = dxwrapperConfig.get("ddrawrapper");
            String vkd3dVersion = dxwrapperConfig.get("vkd3dVersion");
            String vkd3dPart = (vkd3dVersion != null && !vkd3dVersion.isEmpty() && !vkd3dVersion.equals("none") && !vkd3dVersion.equals("None"))
                ? "vkd3d-" + vkd3dVersion : "";
            dxwrapper = "vegas-" + vegasVersion + ";" + vkd3dPart + ";" + ddrawrapper + d7vkMarker(ddrawrapper);
        }

        if (!dxwrapper.equals(container.getExtra("dxwrapper"))) {
            if (extractDXWrapperFiles(dxwrapper)) {
                container.putExtra("dxwrapper", dxwrapper);
                containerDataChanged = true;
            }
        }

        // Direct XInput (HERMES): the HERMES-XInput dlls into this prefix (system32 + syswow64), or
        // the prefix's own xinput dlls back when the setting is off (core.HermesXInput). Every launch,
        // idempotent, finishes or undoes an interrupted one; setupXEnvironment adds the
        // WINEDLLOVERRIDES entry and writes the session log line.
        // With "Allow in-game switching", our DLLs go in either way plus Wine's own xinput1_4 beside them
        // (the forward target), so the XInput source can change mid-game (Controls › Pad).
        // A game xinput DLL an earlier session moved aside and never put back (it ended uncleanly) goes
        // back first (core.GameXInputDlls); applyHermesXInputEnv decides this launch's.
        for (String line : com.winhub.hernandez.core.GameXInputDlls.restoreAll(gameXInputManifest()))
            Log.i("XServerDisplayActivity", "Game xinput DLL: " + line);
        String hxinputWineRoot = null;
        try { hxinputWineRoot = imageFs.getWinePath(); } catch (Throwable ignored) {}
        hxinputSync = com.winhub.hernandez.core.HermesXInput.syncForLaunch(this, new File(imageFs.getRootDir(), ImageFs.WINEPREFIX),
                container, shortcut, wineInfo != null && wineInfo.isArm64EC(),
                hxinputWineRoot != null ? new File(hxinputWineRoot) : null);
        String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();
        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        WineThemeManager.ThemeInfo themeInfo = new WineThemeManager.ThemeInfo(desktopTheme);
        boolean themeChanged = !(desktopTheme+","+xServer.screenInfo).equals(container.getExtra("desktopTheme"));
        // Also regenerate when the source wallpaper is newer than this container's cached bmp, so a
        // GLOBAL wallpaper changed while editing another container still propagates here on launch.
        if (themeChanged || WineThemeManager.wallpaperNeedsRegen(this, themeInfo, container.id)) {
            WineThemeManager.apply(this, themeInfo, xServer.screenInfo, container.id);
            container.putExtra("desktopTheme", desktopTheme+","+xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container);

        // Ship the current in-container file manager (wfm.exe) from the APK into this container's
        // drive_c\windows on every launch, so an app update delivers a new wfm.exe to EVERY existing
        // container without an imagefs reinstall or a new container. Version-gated + best-effort.
        stageBundledFileManager();

        // DirectInput visibility ("DirectInput games can see the controller", core.DInputVisibility,
        // which also reads the old Enable DInput / Exclusive Input values). The entries it writes are
        // matched by the pad's name, which follows the Controller identity
        // (core.ControllerIdentity.dinputNames); the Classic DirectInput pad also needs winebus to
        // present the pads unmapped, i.e. not as XInput gamepads.
        String padIdentity = com.winhub.hernandez.core.ControllerIdentity.effective(container, shortcut);
        boolean dinputVisible = com.winhub.hernandez.core.DInputVisibility.forGame(container, shortcut);
        String dinputWhy = "settings";
        if (isControllerPassthroughLaunch()) {
            // Controller passthrough (lever 3): the classic game reads the pad through DirectInput, so it
            // must see it for this RealSteam launch, whatever the container/shortcut value.
            dinputVisible = true;
            dinputWhy = "controller passthrough";
        } else if (com.winhub.hernandez.core.ControllerIdentity.DINPUT_PAD.equals(padIdentity)) {
            // The Classic DirectInput pad exists for DirectInput games; hidden from them it is no pad at all.
            if (!dinputVisible) dinputWhy = "forced on by the Classic DirectInput pad identity";
            dinputVisible = true;
        }
        Log.i("XServerDisplayActivity", "DirectInput games can see the controller: "
                + (dinputVisible ? "on" : "off") + " (" + dinputWhy + ")");
        com.winhub.hernandez.core.ControllerLog.setup("Controller type", com.winhub.hernandez.core.ControllerIdentity.describe(padIdentity));
        com.winhub.hernandez.core.ControllerLog.setup("DirectInput games can see the controller", (dinputVisible ? "on" : "off") + " (" + dinputWhy + ")");
        com.winhub.hernandez.core.ControllerLog.setup("winebus", (com.winhub.hernandez.core.ControllerIdentity.mapControllers(padIdentity)
                ? "Map Controllers default (pads mapped as gamepads)" : "Map Controllers 0 (raw joystick, Classic DirectInput pad)")
                + ", DisableHidraw 1");
        // What the in-game controller settings (Controls › Pad) start from.
        launchPadIdentity = padIdentity;
        launchDInputVisible = dinputVisible;
        launchDInputForcedWhy = dinputWhy.equals("settings") ? null
                : (dinputWhy.startsWith("forced on by ") ? dinputWhy.substring("forced on by ".length()) : dinputWhy);
        boolean dinputForced = !dinputWhy.equals("settings");
        WineUtils.setJoystickRegistryKeys(container, dinputVisible, dinputForced, padIdentity);
        com.winhub.hernandez.core.ControllerLog.setup("DirectInput registry",
                com.winhub.hernandez.core.JoystickRegistry.describe(dinputVisible, dinputForced));
        WineUtils.setWinebusOptions(container, com.winhub.hernandez.core.ControllerIdentity.mapControllers(padIdentity));

        String startupServices;
        if (shortcut != null) {
            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()));
            startupServices = shortcut.getExtra("startupServices", container.getStartupServices());
        }
        else {
            startupSelection = String.valueOf(container.getStartupSelection());
            startupServices = container.getStartupServices();
        }

        // Epic Friends Overlay (Phase 3) needs full Wine services alive: the EOS SDK spins the overlay
        // up through services.exe (RpcSs + BITS). An AGGRESSIVE startup kills services.exe, so the
        // overlay can't render. When the per-shortcut overlay toggle is ON, bump an aggressive selection
        // to NORMAL for THIS launch (in-memory; it flows through changeServicesStatus below). Only the
        // aggressive case is touched — ESSENTIAL/NORMAL already keep services running.
        if (isEpicOverlayEnabledForLaunch()) {
            try {
                if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_AGGRESSIVE) {
                    Log.i("XServerDisplayActivity", "Epic overlay ON: overriding AGGRESSIVE startup -> NORMAL "
                            + "so Wine services (RpcSs/BITS) survive for the EOS overlay");
                    startupSelection = String.valueOf(Container.STARTUP_SELECTION_NORMAL);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Cache signature: for the three presets it's just the selection (unchanged behaviour — the
        // cached "startupSelection" extra keeps holding "0"/"1"/"2"). For Custom the signature also
        // folds in the enabled-CSV, so two DIFFERENT custom sets (both selection "3") produce
        // different signatures and a changed set actually re-applies instead of being skipped.
        String startupSignature = startupSelection;
        try {
            if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_CUSTOM)
                startupSignature = startupSelection + "|" + startupServices;
        }
        catch (NumberFormatException e) {}

        if (!startupSignature.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, startupSelection, startupServices);
            container.putExtra("startupSelection", startupSignature);
            containerDataChanged = true;
        }
        if (containerDataChanged) container.saveData();
    }

    // Version of the bundled wfm.exe carried in app/src/main/assets/wfm.exe. Bump this whenever the
    // asset is replaced so a fresh APK restages the file into every container on next launch.
    private static final String BUNDLED_WFM_VERSION = "1.2.1";

    // Stage the APK-bundled wfm.exe (the in-container file manager) into this container's
    // drive_c\windows, overwriting whatever the imagefs shipped. Previously wfm.exe lived only in the
    // imagefs, so updating it needed an imagefs reinstall; carrying it in the APK and copying it here
    // means an app update reaches every EXISTING container on its next launch — no reinstall, no new
    // container. A tiny ".wfm_version" marker beside the exe records the staged version; we only rewrite
    // when it's missing or doesn't match, so the steady-state launch does no work. Best-effort: any
    // failure is logged and the launch continues on whatever wfm.exe the container already had. Runs on
    // the background launch worker (setupWineSystemFiles), so the copy is off the UI thread.
    private void stageBundledFileManager() {
        try {
            // The launching container's own drive_c\windows — /home/xuser resolves here through the
            // xuser symlink at launch, so this is exactly the C:\windows the guest reads wfm.exe from.
            File windowsDir = new File(container.getRootDir(), ".wine/drive_c/windows");
            if (!windowsDir.isDirectory() && !windowsDir.mkdirs()) {
                Log.w("XServerDisplayActivity", "stageBundledFileManager: windows dir unavailable: " + windowsDir);
                return;
            }
            File wfmFile = new File(windowsDir, "wfm.exe");
            File marker = new File(windowsDir, ".wfm_version");

            boolean upToDate = wfmFile.isFile() && marker.isFile()
                    && BUNDLED_WFM_VERSION.equals(FileUtils.readString(marker).trim());
            if (upToDate) return;

            // Copy to a temp sibling then rename, so a crash mid-copy can't leave a truncated wfm.exe
            // that the guest would then try to run.
            File tmp = new File(windowsDir, "wfm.exe.tmp");
            try (java.io.InputStream in = getAssets().open("wfm.exe");
                 java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            if (!tmp.isFile() || tmp.length() == 0) {
                Log.w("XServerDisplayActivity", "stageBundledFileManager: staged wfm.exe empty, skipping");
                tmp.delete();
                return;
            }
            // renameTo won't overwrite on some Android fs layers — clear the old exe first.
            if (wfmFile.exists()) wfmFile.delete();
            if (!tmp.renameTo(wfmFile)) {
                Log.w("XServerDisplayActivity", "stageBundledFileManager: rename into place failed");
                tmp.delete();
                return;
            }
            wfmFile.setReadable(true, false);
            wfmFile.setExecutable(true, false);
            FileUtils.writeString(marker, BUNDLED_WFM_VERSION);
            Log.i("XServerDisplayActivity", "Staged bundled wfm.exe " + BUNDLED_WFM_VERSION + " -> " + wfmFile);
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "stageBundledFileManager failed (continuing launch)", e);
        }
    }

    /**
     * Resolve the "Sync" setting for this launch and write its env (core.SyncSupport): the shortcut's
     * own choice, else the container's, else the layer default; a choice the layer can't run (ntsync
     * off a layer without it, esync on a layer without it) falls back to the layer default. Runs on
     * the launch worker thread (the layer probe reads ntdll.so once, then it is cached).
     */
    private void applySyncModeEnv() {
        if (container == null) return;
        String requested = com.winhub.hernandez.core.SyncSupport.requestedMode(
                container.getExtra(com.winhub.hernandez.core.SyncMode.EXTRA),
                container.getEnvVars(),
                shortcut != null ? shortcut.getExtra(com.winhub.hernandez.core.SyncMode.EXTRA) : null,
                shortcut != null ? shortcut.getExtra("envVars") : null);
        com.winhub.hernandez.core.SyncCaps caps = com.winhub.hernandez.core.SyncSupport.capsForLayerPath(
                wineInfo != null ? wineInfo.path : null);
        String mode = caps.resolve(requested);
        com.winhub.hernandez.core.SyncSupport.applyToEnv(envVars, mode);
        Log.i("XServerDisplayActivity", "sync: requested=" + (requested != null ? requested : "(layer default)")
                + " -> " + mode + " [layer esync=" + caps.getEsync() + " ntsync=" + caps.getNtsync() + "]"
                + " WINEESYNC=" + envVars.get("WINEESYNC")
                + (envVars.has("WINENTSYNC") ? " WINENTSYNC=" + envVars.get("WINENTSYNC") : ""));
    }

    /**
     * Resolve "Fast OpenGL" for this launch (core.FastOpenGL.decideX11) and, when on, export X11Egl's
     * env for the route: WINHUB_X11_EGL=0 (off), WINHUB_X11_GLX=1 (GLX route) and WINHUB_X11_EGL=1
     * (EGL route) in the environment win outright; else the shortcut's override, else the container's
     * choice (default on), on only when the driver is a Turnip and the APK carries the GL set. The
     * layer picks the route: EGL when its aarch64-unix win32u knows WINE_USE_EGL, else GLX, which also
     * turns on the X server's server-side GLX + DRI3/Present 1.2 for this session. Runs on the launch
     * worker before XServerComponent opens the socket, so no client ever sees the extension list
     * change; EGL route or off leaves the X server as it always was. Then the two plain switches beside
     * it (Show OpenGL FPS, OpenGL vsync off). One log line for all three.
     */
    private void applyFastOpenGLEnv(boolean wayland) {
        if (container == null) return;
        com.winhub.hernandez.core.FastOpenGL.X11Decision decision;
        if (wayland) {
            decision = null;   // always on with Wayland: winewayland takes EGL on its own, nothing to export
        } else {
            String game = shortcut != null
                    ? com.winhub.hernandez.core.FastOpenGL.normalize(shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA))
                    : null;
            boolean requested = game != null ? game.equals("1") : container.isFastOpenGL();
            String driverId = graphicsDriverConfig != null ? graphicsDriverConfig.get("version") : null;
            decision = com.winhub.hernandez.core.FastOpenGL.decideX11(
                    requested, game != null ? "game" : "container",
                    com.winhub.hernandez.core.X11Egl.envOverride(envVars),
                    com.winhub.hernandez.core.X11Egl.glxForced(envVars),
                    com.winhub.hernandez.core.FastOpenGL.unavailableReason(this, driverId),
                    com.winhub.hernandez.core.FastOpenGL.layerHasEgl(wineInfo != null ? wineInfo.path : null));
        }
        String detail = null;
        String glxLine = null;
        if (decision != null && decision.getOn()) {
            boolean egl = decision.route == com.winhub.hernandez.core.FastOpenGL.Route.EGL;
            detail = com.winhub.hernandez.core.X11Egl.apply(this, envVars, imageFs.getRootDir().getPath() + "/usr/lib", egl);
            if (detail == null) {
                decision = new com.winhub.hernandez.core.FastOpenGL.X11Decision(null,
                        "unsupported: the bundled GL libraries could not be installed");
            } else if (!egl) {
                xServer.enableServerGlx();
                glxLine = "x11 glx: server GLX + DRI3/Present 1.2 on (" + decision.why + ")";
            }
        }
        String line = (decision != null ? decision.toString() : "fast opengl: on (wayland)")
                + " | fps hud: " + applyGlLaunchSwitch(com.winhub.hernandez.core.FastOpenGL.FPS_HUD)
                + " | vsync off: " + applyGlLaunchSwitch(com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF);
        Log.i("XServerDisplayActivity", line + (detail != null ? " - " + detail : ""));
        if (glxLine != null) Log.i("XServerDisplayActivity", glxLine);
        if (wineDebugWriter != null) {
            wineDebugWriter.println(line);
            if (detail != null) wineDebugWriter.println(detail);
            if (glxLine != null) wineDebugWriter.println(glxLine);
        }
    }

    /**
     * Resolve one plain OpenGL switch (shortcut override, else container) and export its variable when
     * on, unless the environment variables already set it. Returns its part of the log line.
     */
    private String applyGlLaunchSwitch(com.winhub.hernandez.core.GlLaunchSwitch sw) {
        String game = shortcut != null ? com.winhub.hernandez.core.FastOpenGL.normalize(shortcut.getExtra(sw.extra)) : null;
        boolean on = sw.requested(container.getExtra(sw.extra), shortcut != null ? shortcut.getExtra(sw.extra) : null);
        String why = game != null ? "game" : "container";
        if (!on) return "off (" + why + ")";
        if (envVars.has(sw.envName))
            return "on (" + why + ", " + sw.envName + "=" + envVars.get(sw.envName) + " is yours, kept)";
        envVars.put(sw.envName, sw.envValue);
        return "on (" + why + ", " + sw.envName + "=" + sw.envValue + ")";
    }

    // Bring up the embedded Wayland compositor into a full-screen SurfaceView. The socket is created
    // under the imagefs /tmp (XDG_RUNTIME_DIR = rootDir/tmp) so the guest — which sees the imagefs as
    // its root — finds it at /tmp/wayland-0 (matching the guest env in GuestProgramLauncherComponent).
    /** The Wayland zero-copy layer mode (ZERO_COPY_SPIKE.md) for this launch: BANNER_WAYLAND_ZERO_COPY=1
     *  (or true) in the container's or the shortcut's environment variables, else the Wayland adapter
     *  settings (profile / Advanced switch - core.WaylandAdapterSettings). One switch for both halves: the
     *  compositor (nativeSetZeroCopy) and the guest's Wayland Turnip (BANNER_WSI_AHB=1, gralloc swapchain
     *  images). */
    private boolean isWaylandZeroCopyRequested() {
        if (container == null) return false;
        com.winhub.hernandez.core.WaylandAdapterSettings.Effective e = waylandAdapterEffective();
        return e != null && Boolean.TRUE.equals(e.zeroCopy);
    }

    /** The resolved Wayland adapter settings of this launch (shortcut keys > container keys > profile, with
     *  the user's env vars over all of them), computed once: the compositor setters (startWaylandCompositor),
     *  the drawer's zero-copy switch and the guest env (setupXEnvironment) all read the same answer. The
     *  Auto profile reads the cached probe report of the graphics driver this session uses. */
    private com.winhub.hernandez.core.WaylandAdapterSettings.Effective waylandAdapterEffective;

    private com.winhub.hernandez.core.WaylandAdapterSettings.Effective waylandAdapterEffective() {
        if (waylandAdapterEffective != null) return waylandAdapterEffective;
        // Before the container is resolved there is nothing to read: answer the defaults, uncached.
        if (container == null) return com.winhub.hernandez.core.WaylandAdapterSettings.resolve(null, null, null, null);
        try {
            String containerCfg = container.getGraphicsDriverConfig();
            String shortcutCfg = shortcut != null ? shortcut.getExtra("graphicsDriverConfig", "") : null;
            if (shortcutCfg != null && shortcutCfg.isEmpty()) shortcutCfg = null;
            String driverId = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
                    .getVersion(shortcutCfg != null ? shortcutCfg : containerCfg);
            com.winhub.hernandez.core.WaylandDriverProbe.Report probe =
                    com.winhub.hernandez.core.WaylandDriverProbe.cached(this, driverId);
            waylandAdapterEffective = com.winhub.hernandez.core.WaylandAdapterSettings.resolve(
                    containerCfg, shortcutCfg, effectiveUserEnv(), probe);
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "wayland: adapter settings resolve failed; using defaults", e);
            waylandAdapterEffective = com.winhub.hernandez.core.WaylandAdapterSettings.resolve(null, null, null, null);
        }
        return waylandAdapterEffective;
    }

    // Whether a game session is on screen: core.WaylandDriverProbe refuses to run beside one (a second
    // Vulkan device on the same driver while a game renders is not worth the risk for a settings dialog).
    private static volatile boolean sessionInForeground;

    public static boolean isSessionInForeground() {
        return sessionInForeground;
    }

    /** The effective env (container first, shortcut second, so the shortcut wins), or null. */
    private EnvVars effectiveUserEnv() {
        if (container == null) return null;
        String raw = container.getEnvVars();
        if (shortcut != null) {
            String sv = shortcut.getExtra("envVars", "");
            if (sv != null && !sv.isEmpty()) raw = (raw == null ? "" : raw + " ") + sv;
        }
        return raw != null && !raw.isEmpty() ? new EnvVars(raw) : null;
    }

    /** The Wayland compositor's HDR10 output for this launch (waylandcomp/src/banner_color.h,
     *  display.WaylandHdr). The "HDR output" setting decides — the game shortcut's own choice, else the
     *  container's, the same owner the editors write — and BANNER_WAYLAND_HDR in the container's or
     *  shortcut's environment variables overrides it: 1/true/on = on, 0/false/off = off, force = on
     *  whatever the display says (testing the negotiation on an SDR panel). "On" still only turns
     *  anything on where the game's display lists HDR10 (startWaylandCompositor). */
    private int resolvedWaylandHdrMode() {
        EnvVars env = effectiveUserEnv();
        if (env != null && env.has("BANNER_WAYLAND_HDR")) {
            String v = env.get("BANNER_WAYLAND_HDR").trim();
            if (v.equalsIgnoreCase("force")) return com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_FORCE;
            if (v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on"))
                return com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_ON;
            return com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_OFF;
        }
        return com.winhub.hernandez.display.WaylandHdr.effective(shortcut, container)
                ? com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_ON
                : com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_OFF;
    }

    /** What decided resolvedWaylandHdrMode(), for the session log. */
    private String waylandHdrSource() {
        EnvVars env = effectiveUserEnv();
        if (env != null && env.has("BANNER_WAYLAND_HDR")) {
            if (shortcut != null) {
                String sv = shortcut.getExtra("envVars", "");
                if (sv != null && !sv.isEmpty() && new EnvVars(sv).has("BANNER_WAYLAND_HDR")) return "shortcut env var";
            }
            return "container env var";
        }
        return !com.winhub.hernandez.display.WaylandHdr.shortcutChoice(shortcut).isEmpty()
                ? "the game's HDR output setting" : "the container's HDR output setting";
    }

    /** Set in startWaylandCompositor: HDR output is really on for this session (the switch resolved on,
     *  AND the game's display lists HDR10, or =force). Read by setupXEnvironment (worker thread, later). */
    private volatile boolean waylandHdrActive = false;
    /** The display reading startWaylandCompositor made (the brightness hand-off exports it). */
    private volatile com.winhub.hernandez.display.DisplayHdrInfo waylandHdrDisplay;

    /** DXVK_HDR=1 in the effective env: DXVK then reports an HDR display through DXGI. */
    private boolean isDxvkHdrEnvOn() {
        EnvVars env = effectiveUserEnv();
        String v = env != null ? env.get("DXVK_HDR") : null;
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true"));
    }

    private com.winhub.hernandez.core.HermesXInput.Result hxinputSync;
    /** XInput DLL names this game ships that Wine's builtin serves this launch (=b), for the toast and Pad. */
    private final java.util.List<String> hxinputGameBuiltin = new ArrayList<>();
    private com.winhub.hernandez.core.HermesXInput.LaunchControl hermesLaunchControl;
    // In-game controller settings (Controls › Pad) of a Wine session, and what the launch set them to.
    private LiveControllerSession liveControllerSession;
    /** Where in-game rumble and Controls › Pad changes are saved (made in setupUI). */
    private ControllerSaveTarget controllerSaveTarget;
    private String launchPadIdentity = com.winhub.hernandez.core.ControllerIdentity.GENERIC;
    private boolean launchDInputVisible;
    private String launchDInputForcedWhy;

    /** Direct XInput (HERMES, core.HermesXInput; launch worker thread, after the user's env vars are
     *  merged): when setupWineSystemFiles put the dlls in the prefix, WINEDLLOVERRIDES gains
     *  xinput1_1,xinput1_2,xinput1_3,xinput1_4,xinput9_1_0,xinputuap=n,b (a user's own entry for any of
     *  them is kept). One "xinput" line in the session log; off logs only when this launch put the
     *  prefix's own dlls back. */
    private void applyHermesXInputEnv(EnvVars envVars) {
        try {
            com.winhub.hernandez.core.HermesXInput.Result sync = hxinputSync;
            if (sync == null) return;
            if (!sync.on) {
                if (sync.detail != null) logSessionLine("xinput", "Direct XInput (HERMES) off: " + sync.detail);
                com.winhub.hernandez.core.ControllerLog.setup("Direct XInput", "off" + (sync.detail != null ? ": " + sync.detail : ""));
                return;
            }
            StringBuilder line = new StringBuilder("Direct XInput (HERMES) on: ")
                    .append(sync.detail != null ? sync.detail : "dlls not installed");
            if (sync.installed) {
                String before = envVars.has("WINEDLLOVERRIDES") ? envVars.get("WINEDLLOVERRIDES") : "";
                // The game's own xinput DLLs (core.GameXInputDlls): Wine looks in the game's folder before
                // system32, so a copy there would load instead of ours (Microsoft's needs a driver Wine
                // lacks: no pads). A name the user's overrides set stays theirs; with "Use Direct XInput
                // over the game's own XInput DLL" the copy is moved aside for the session; otherwise that
                // name gets Wine's builtin (=b), as before Direct XInput.
                java.util.Map<String, String> userNamed = com.winhub.hernandez.core.GameXInputDlls.named(before);
                java.util.Map<String, File> shipped = com.winhub.hernandez.core.GameXInputDlls.findShipped(
                        gameExeFolders(), com.winhub.hernandez.core.HermesXInput.bundledShas(this));
                boolean overGame = shortcut != null
                        && "1".equals(shortcut.getExtra(com.winhub.hernandez.core.GameXInputDlls.EXTRA_OVER_GAME));
                java.util.Set<String> builtin = new java.util.LinkedHashSet<>();
                java.util.List<String> notes = new ArrayList<>();
                for (java.util.Map.Entry<String, File> e : shipped.entrySet()) {
                    String dll = e.getKey();
                    if (userNamed.containsKey(dll)) {
                        String order = userNamed.get(dll);
                        notes.add("the game ships " + e.getValue().getName() + "; yours kept (" + dll + "=" + order + ")"
                                + (com.winhub.hernandez.core.GameXInputDlls.nativeFirst(order)
                                        ? ", so the game's own copy loads (Prefer game-folder DLLs)" : ""));
                        continue;
                    }
                    if (overGame && com.winhub.hernandez.core.GameXInputDlls.moveAside(gameXInputManifest(), e.getValue(), notes))
                        continue;
                    builtin.add(dll);
                    notes.add("the game ships " + e.getValue().getName() + ": Wine's builtin " + dll + " for it (=b)");
                }
                com.winhub.hernandez.core.GameXInputDlls.Overrides o =
                        com.winhub.hernandez.core.GameXInputDlls.overrides(before, builtin);
                if (!o.value.equals(before)) envVars.put("WINEDLLOVERRIDES", o.value);
                synchronized (hxinputGameBuiltin) {
                    hxinputGameBuiltin.clear();
                    hxinputGameBuiltin.addAll(o.builtin);
                }
                line.append("; WINEDLLOVERRIDES=").append(o.value);
                if (!o.kept.isEmpty()) line.append(" (yours kept for ").append(String.join(",", o.kept)).append(")");
                for (String n : notes) line.append("; ").append(n);
            } else {
                line.append("; no override, Wine's builtin xinput stays in use");
            }
            logSessionLine("xinput", line.toString());
            com.winhub.hernandez.core.ControllerLog.setup("Direct XInput", line.toString());
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "Direct XInput (HERMES) environment failed", t);
        }
    }

    /** Set in startWaylandCompositor: the HDR opt-in turned zero-copy presentation on for this session
     *  (the guest half, BANNER_WSI_AHB=1, is exported in setupXEnvironment, which runs after it). */
    private volatile boolean waylandHdrZeroCopyForced = false;
    /** The HDR opt-in mode this session started with (the ratio sampler runs only when it is on). */
    private int waylandHdrMode = com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_OFF;

    /** The Wayland session's HDR environment (launch worker thread, after the user's env vars are
     *  merged, so an explicit value of theirs always wins):
     *  - HDR output on for this session -> DXVK_HDR=1, so DXVK tells the game its display is HDR;
     *  - every Wayland session whose display lists HDR10 -> BANNER_WAYLAND_HDR_MAX_NITS /
     *    _MAX_AVG_NITS / _MIN_NITS (decimal nits from Display.getHdrCapabilities(); a value that is
     *    unknown is left out, and so is a max or max-average of 0). The Wayland layer from versionCode
     *    10 describes the monitor to Windows with them (EDID HDR metadata), so DXGI reports this
     *    screen's real peak instead of DXVK's 1499-nit stand-in. Never on a display without HDR10: its
     *    EDID would then claim PQ support the screen does not have. Android reports no LIVE brightness
     *    in nits; the live HDR/SDR ratio is what the compositor logs instead. */
    private void applyWaylandHdrEnv(EnvVars envVars) {
        try {
            StringBuilder said = new StringBuilder();
            if (waylandHdrActive) {
                if (envVars.has("DXVK_HDR")) {
                    said.append("DXVK_HDR=").append(envVars.get("DXVK_HDR")).append(" (yours, kept)");
                } else {
                    envVars.put("DXVK_HDR", "1");
                    said.append("DXVK_HDR=1");
                }
            }
            com.winhub.hernandez.display.DisplayHdrInfo d = waylandHdrDisplay;
            if (d == null) d = com.winhub.hernandez.display.DisplayHdrInfo.read(hdrTargetDisplay());
            String[][] nits = {
                    {"BANNER_WAYLAND_HDR_MAX_NITS", d.maxLuminance > 0f ? com.winhub.hernandez.display.WaylandHdr.nits(d.maxLuminance) : null},
                    {"BANNER_WAYLAND_HDR_MAX_AVG_NITS", d.maxAverageLuminance > 0f ? com.winhub.hernandez.display.WaylandHdr.nits(d.maxAverageLuminance) : null},
                    {"BANNER_WAYLAND_HDR_MIN_NITS", d.minLuminance >= 0f ? com.winhub.hernandez.display.WaylandHdr.nits(d.minLuminance) : null}};
            if (d.supportsHdr10 && d.maxLuminance > 0f) { // HDR10 displays only; no peak = nothing worth describing
                for (String[] kv : nits) {
                    if (kv[1] == null) continue;
                    if (said.length() > 0) said.append(' ');
                    if (envVars.has(kv[0])) {
                        said.append(kv[0]).append('=').append(envVars.get(kv[0])).append(" (yours, kept)");
                    } else {
                        envVars.put(kv[0], kv[1]);
                        said.append(kv[0]).append('=').append(kv[1]);
                    }
                }
            }
            if (said.length() > 0) {
                String line = "session environment: " + said + " - from \"" + d.displayName + "\" (Android reports no live "
                        + "brightness in nits; the HDR/SDR ratio lines are the live reading)";
                Log.i("XServerDisplayActivity", "wayland HDR " + line);
                com.winhub.hernandez.wayland.WaylandCompositor.nativeLogColor(line);
            }
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "wayland: HDR environment failed", t);
        }
    }

    /** One line for this session's log under {@code area}: the Wayland session log (Download/Wayland-logs)
     *  on Wayland, the Wine debug log on X11 when the Log Manager has it open; logcat always. */
    private void logSessionLine(String area, String line) {
        Log.i("XServerDisplayActivity", area + ": " + line);
        if (waylandMode) {
            try { com.winhub.hernandez.wayland.WaylandCompositor.nativeLog(area, line); } catch (Throwable ignored) {}
        } else if (wineDebugWriter != null) {
            wineDebugWriter.println("Bannerlator " + area + ": " + line);
        }
    }

    /** Controller input log (core.ControllerLog), when the Log Manager switch is on: the rest of the launch's
     *  controller setup, the evdev layer's diagnostics (FAKE_EVDEV_LOG=1, written at attach / probe / loss)
     *  and the guest's output, of which only the controller lines are kept. Launch worker thread, after
     *  applyHermesXInputEnv; before the guest starts, so the capture sees its first line. */
    private void applyControllerLogEnv(EnvVars envVars) {
        if (!com.winhub.hernandez.core.ControllerLog.isActive()) return;
        try {
            com.winhub.hernandez.core.HermesXInput.Result sync = hxinputSync;
            boolean direct = sync != null && sync.installed && sync.directOn;
            boolean dinputVibration = com.winhub.hernandez.core.DInputVibration.effective(container, shortcut);
            com.winhub.hernandez.core.ControllerLog.launchChoices(direct, !direct, launchDInputVisible, dinputVibration);
            com.winhub.hernandez.core.ControllerLog.setup("XInput source", (direct ? "Direct (HERMES)" : "Wine's XInput")
                    + (sync != null && sync.installed && sync.switching && sync.forwardAvailable
                            ? ", in-game switching available" : ", no in-game switching"));
            com.winhub.hernandez.core.ControllerLog.setup("DirectInput vibration", (dinputVibration ? "on" : "off")
                    + (com.winhub.hernandez.core.DInputVibration.envOverride(container, shortcut) != null
                            ? " (" + com.winhub.hernandez.core.DInputVibration.ENV + " in the env vars decides)" : ""));
            com.winhub.hernandez.core.ControllerLog.setup("Stick updates",
                    com.winhub.hernandez.core.StickPacing.forGame(container, shortcut).format()
                            + (com.winhub.hernandez.core.StickPacing.envOverrides(container, shortcut)
                                    ? " (HERMES_AXIS_* in the env vars decide)" : ""));
            com.winhub.hernandez.core.ControllerLog.setup("Wine layer", container.getWineVersion()
                    + (wineInfo != null && wineInfo.isArm64EC() ? " (arm64ec)" : ""));
            if (!envVars.has("FAKE_EVDEV_LOG")) envVars.put("FAKE_EVDEV_LOG", "1");
            ProcessHelper.addDebugCallback(com.winhub.hernandez.core.ControllerLog.GUEST_LINES);
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "controller log setup failed", t);
        }
    }

    /** Where GameXInputDlls records the game xinput DLLs it moved aside (app files, survives the session). */
    private File gameXInputManifest() {
        return new File(getFilesDir(), com.winhub.hernandez.core.GameXInputDlls.MANIFEST_NAME);
    }

    // ── Game mods (core.GameMods: REFramework, Special K) ────────────────────────────────────────────────

    private final Handler modKeyHandler = new Handler(Looper.getMainLooper());
    private static final String START_PROGRAM_ID = "startprog:";

    /** Game mods (launch worker thread, after the user's env vars are merged; Wine sessions): every mod
     *  switched on for this game (game settings > Mods) and available for its EXE is downloaded from its
     *  release when this device does not have it yet, placed next to the EXE, and gets its
     *  WINEDLLOVERRIDES entry (a user's own entry for that DLL is kept); REFramework's menu is set to start
     *  closed. A mod switched off has only the file we placed removed. One "mods" line per mod in the
     *  session log; the drawer's Mods tab gets one card per mod this EXE can take (on, off or not loaded),
     *  then one per program the game starts. */
    private void applyGameModsEnv(EnvVars envVars) {
        java.util.List<com.winhub.hernandez.ui.DrawerMod> cards = new ArrayList<>();
        try {
            if (shortcut == null) return;
            java.util.List<File> dirs = gameExeFolders();
            File exe = gameExeFile();
            int machine = com.winhub.hernandez.core.GameMods.peMachine(exe);
            java.util.Set<String> imports = null;
            String bits = machine == com.winhub.hernandez.core.GameMods.MACHINE_X86 ? "32-bit" : "64-bit";
            for (com.winhub.hernandez.core.GameMods.Mod mod : com.winhub.hernandez.core.GameMods.ALL) {
                boolean on = "1".equals(shortcut.getExtra(mod.extra));
                boolean available = com.winhub.hernandez.core.GameMods.supports(mod, shortcut.path, machine);
                if (available && !on) cards.add(new com.winhub.hernandez.ui.DrawerMod(mod.id, mod.name, false, false, "", "", bits));
                if (!on || !available) {
                    for (File dir : dirs) {
                        String note = com.winhub.hernandez.core.GameMods.remove(dir, mod);
                        if (note != null) logSessionLine("mods", mod.name + " off: " + note);
                    }
                    if (on) logSessionLine("mods", mod.name + " on, but not available for this EXE"
                            + (machine == 0 ? " (EXE not readable)" : "") + ": not loaded");
                    continue;
                }
                if (dirs.isEmpty()) {
                    logSessionLine("mods", mod.name + " on, but the game's folder could not be found: not loaded");
                    cards.add(new com.winhub.hernandez.ui.DrawerMod(mod.id, mod.name, true, false, "", "", bits));
                    continue;
                }
                File dir = dirs.get(0);
                com.winhub.hernandez.core.GameMods.Asset asset = com.winhub.hernandez.core.GameMods.asset(mod, machine);
                if (imports == null) imports = com.winhub.hernandez.core.GameMods.peImports(exe);
                String dll = com.winhub.hernandez.core.GameMods.targetDll(mod,
                        shortcut.getExtra(com.winhub.hernandez.core.GameMods.EXTRA_SPECIALK_DLL), imports, machine);
                StringBuilder line = new StringBuilder(mod.name).append(" on (").append(bits).append("): ");
                File source = null;
                if (!com.winhub.hernandez.core.GameMods.inPlace(dir, dll, asset.sha256)) {
                    try {
                        boolean had = com.winhub.hernandez.core.GameModCache.cached(getFilesDir(), asset) != null;
                        source = com.winhub.hernandez.core.GameModCache.get(getFilesDir(), asset, null);
                        if (!had) line.append("downloaded; ");
                    } catch (Throwable t) {
                        line.append("download failed (").append(t.getMessage()).append("); ");
                    }
                }
                com.winhub.hernandez.core.GameMods.Result r =
                        com.winhub.hernandez.core.GameMods.install(dir, mod, dll, source, asset.sha256);
                line.append(r.note);
                if (r.present) {
                    String before = envVars.has("WINEDLLOVERRIDES") ? envVars.get("WINEDLLOVERRIDES") : "";
                    String after = com.winhub.hernandez.core.GameMods.overrides(before, dll);
                    if (!after.equals(before)) envVars.put("WINEDLLOVERRIDES", after);
                    line.append("; WINEDLLOVERRIDES=").append(after);
                    if (after.equals(before)) line.append(" (yours kept)");
                }
                if (mod == com.winhub.hernandez.core.GameMods.REFRAMEWORK && r.ours) {
                    try {
                        if (com.winhub.hernandez.core.ReFramework.applyMenuStartsClosed(dir)) line.append("; menu starts closed");
                    } catch (Throwable t) {
                        line.append("; menu state not set (").append(t.getMessage()).append(")");
                    }
                }
                logSessionLine("mods", line.toString());
                cards.add(new com.winhub.hernandez.ui.DrawerMod(mod.id, mod.name, true, r.present,
                        r.ours ? asset.tag() : "", r.present ? dll : "", bits));
            }
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "game mods setup failed", t);
            logSessionLine("mods", "setup failed: " + t.getMessage());
        } finally {
            if (shortcut != null) {
                List<com.winhub.hernandez.core.StartPrograms.Entry> programs =
                        com.winhub.hernandez.core.StartPrograms.parse(shortcut.getExtra(com.winhub.hernandez.core.StartPrograms.EXTRA, ""));
                for (int i = 0; i < programs.size(); i++) {
                    String path = programs.get(i).path;
                    cards.add(new com.winhub.hernandez.ui.DrawerMod(START_PROGRAM_ID + i,
                            "Start program " + (i + 1), true, true, "", path.substring(path.lastIndexOf('\\') + 1), "", "Start"));
                }
            }
            final java.util.List<com.winhub.hernandez.ui.DrawerMod> list = cards;
            runOnUiThread(() -> XServerDrawerState.INSTANCE.setMods(list));
        }
    }

    /** Drawer > Mods: for a mod, shows or hides its in-game menu by sending its menu key after the drawer
     *  has closed, as a press and a release 150 ms later (our REFramework build reads the key's state once
     *  per frame when the game blocks key messages, so the press has to outlast a frame). REFramework:
     *  Insert; Special K: Ctrl + Shift + Backspace. For a program card, starts that program again. */
    private void toggleModMenu(String id) {
        if (id.startsWith(START_PROGRAM_ID)) {
            try {
                int index = Integer.parseInt(id.substring(START_PROGRAM_ID.length()));
                List<com.winhub.hernandez.core.StartPrograms.Entry> programs = shortcut == null ? null
                        : com.winhub.hernandez.core.StartPrograms.parse(shortcut.getExtra(com.winhub.hernandez.core.StartPrograms.EXTRA, ""));
                if (programs != null && index >= 0 && index < programs.size() && startProgramsExecutor != null) {
                    final com.winhub.hernandez.core.StartPrograms.Entry program = programs.get(index);
                    startProgramsExecutor.execute(() -> startProgram(program));
                }
            } catch (NumberFormatException e) {
                Log.w("XServerDisplayActivity", "bad program card id: " + id, e);
            }
            return;
        }
        final XKeycode[] keys = com.winhub.hernandez.core.GameMods.SPECIAL_K.id.equals(id)
                ? new XKeycode[]{XKeycode.KEY_CTRL_L, XKeycode.KEY_SHIFT_L, XKeycode.KEY_BKSP}
                : new XKeycode[]{XKeycode.KEY_INSERT};
        modKeyHandler.postDelayed(() -> {
            if (xServer == null) return;
            try {
                for (XKeycode k : keys) xServer.injectKeyPress(k);
            } catch (Throwable t) {
                Log.w("XServerDisplayActivity", "mod menu key", t);
                return;
            }
            modKeyHandler.postDelayed(() -> {
                try {
                    if (xServer != null) for (int i = keys.length - 1; i >= 0; i--) xServer.injectKeyRelease(keys[i]);
                } catch (Throwable t) {
                    Log.w("XServerDisplayActivity", "mod menu key release", t);
                }
            }, 150);
        }, 200);
    }

    /** The game EXE on the Android side, or null for a desktop launch, a .lnk or an unmapped drive. */
    private File gameExeFile() {
        try {
            if (shortcut == null || container == null || shortcut.path == null || shortcut.path.endsWith(".lnk")) return null;
            File f = com.winhub.hernandez.core.WinePath.INSTANCE.resolveAndroidPath(container, shortcut.path);
            return f != null && f.isFile() ? f : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The game EXE's folder on the Android side (also its working directory), or none for a desktop
     *  launch, a .lnk or an unmapped drive. */
    private java.util.List<File> gameExeFolders() {
        java.util.List<File> dirs = new ArrayList<>();
        try {
            if (shortcut == null || container == null || shortcut.path == null || shortcut.path.endsWith(".lnk")) return dirs;
            File dir = com.winhub.hernandez.core.WinePath.INSTANCE.resolveAndroidPath(container, FileUtils.getDirname(shortcut.path));
            if (dir != null && dir.isDirectory()) dirs.add(dir);
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "game folder for the mods", t);
        }
        return dirs;
    }

    // ── Game settings > Mods > Start programs (trainers, Cheat Engine, ...) ──────────────────────────────

    private java.util.concurrent.ScheduledExecutorService startProgramsExecutor;

    /** Wine sessions: starts the programs this game lists in its Mods tab, in that order. The first waits
     *  from winhandler.exe being up (it starts the game), each next one from the start of the one before.
     *  Off the main thread, as starting one writes a batch file. */
    private void scheduleStartPrograms() {
        if (shortcut == null) return;
        final List<com.winhub.hernandez.core.StartPrograms.Entry> programs =
                com.winhub.hernandez.core.StartPrograms.parse(shortcut.getExtra(com.winhub.hernandez.core.StartPrograms.EXTRA, ""));
        if (programs.isEmpty()) return;
        startProgramsExecutor = Executors.newSingleThreadScheduledExecutor();
        long at = 0;
        for (com.winhub.hernandez.core.StartPrograms.Entry program : programs) {
            at += program.delaySec * 1000L;
            startProgramsExecutor.schedule(() -> startProgram(program), at, TimeUnit.MILLISECONDS);
        }
    }

    private void startProgram(com.winhub.hernandez.core.StartPrograms.Entry program) {
        try {
            if (winHandler == null) return;
            boolean ok = winHandler.execInFolder(program.path, "");
            logSessionLine("mods", "Start program: " + program.path
                    + (ok ? "" : " (not started: the batch file could not be written)"));
        } catch (RuntimeException e) {
            Log.w("XServerDisplayActivity", "start program failed: " + program.path, e);
            logSessionLine("mods", "Start program failed: " + program.path + " (" + e.getMessage() + ")");
        }
    }

    /** The prefix's C: drive (the container's .wine/drive_c), or null without a container. */
    public File getWineDriveC() {
        if (container != null) return new File(container.getRootDir(), ".wine/drive_c");
        return imageFs != null && imageFs.wineprefix != null ? new File(imageFs.wineprefix, "drive_c") : null;
    }

    /** One line for this session's log under {@code area}: the Wine debug log when the Log Manager
     *  has it open; logcat always. */
    private void logSessionLine(String area, String line) {
        Log.i("XServerDisplayActivity", area + ": " + line);
        if (wineDebugWriter != null) {
            wineDebugWriter.println("Bannerlator " + area + ": " + line);
        }
    }

    /** The dxvk.conf this session generates, in the container's own directory. Rewritten every launch,
     *  and pointed at with DXVK_CONFIG_FILE — an absolute Android path, which is what DXVK opens (the
     *  same kind of path WINEPREFIX and DXVK_STATE_CACHE_PATH already carry). */
    private static final String DXVK_GENERATED_CONF = "dxvk-generated.conf";

    /** Wayland: the driver config's GPU name spoof and memory cap, delivered to DXVK as a generated
     *  dxvk.conf (DXVK_CONFIG_FILE) plus the ids in DXVK_CONFIG — core.GpuSpoof says why it takes both
     *  routes and why the name can only ride the file. The X11 wrapper that reads WRAPPER_* isn't on
     *  this path. Launch worker thread, after the user's env vars are merged, so a key they set in
     *  DXVK_CONFIG or a config file of their own wins. WRAPPER_DEVICE_NAME / _ID / WRAPPER_VENDOR_ID
     *  stay exported (from the exact list entry) for the Wayland Turnip to read later. One "gpu" line
     *  in the session log, saying what each route actually carried; none when neither is set. */
    private void applyWaylandGpuSpoofEnv(EnvVars envVars) {
        try {
            String gpuName = requestedWaylandGpuSpoof();   // null unless this session asks for a spoof
            int memMb = 0;
            try { memMb = Integer.parseInt(graphicsDriverConfig.get("maxDeviceMemory")); } catch (Exception ignored) {}
            boolean spoofing = gpuName != null;
            if (!spoofing && memMb <= 0) return;
            com.winhub.hernandez.core.GpuSpoof.Card card = spoofing ? com.winhub.hernandez.core.GpuSpoof.find(this, gpuName) : null;
            StringBuilder said = new StringBuilder();
            if (spoofing) {
                said.append("spoof: \"").append(gpuName).append('"');
                EnvVars user = effectiveUserEnv();
                if (user == null || !user.has("WRAPPER_DEVICE_NAME")) envVars.put("WRAPPER_DEVICE_NAME", gpuName);
                if (card == null) {
                    said.append(" is not in the GPU list, so there are no ids to spoof");
                } else {
                    said.append(" (vendor ").append(card.vendorId >= 0 ? com.winhub.hernandez.core.GpuSpoof.pciHex(card.vendorId) : "?")
                        .append(" device ").append(card.deviceId >= 0 ? com.winhub.hernandez.core.GpuSpoof.pciHex(card.deviceId) : "?").append(')');
                    if (card.deviceId >= 0 && (user == null || !user.has("WRAPPER_DEVICE_ID")))
                        envVars.put("WRAPPER_DEVICE_ID", String.valueOf(card.deviceId));
                    if (card.vendorId >= 0 && (user == null || !user.has("WRAPPER_VENDOR_ID")))
                        envVars.put("WRAPPER_VENDOR_ID", String.valueOf(card.vendorId));
                }
            }
            boolean dxvk = dxwrapper != null && (dxwrapper.contains("dxvk") || dxwrapper.contains("vegas"));
            if (!dxvk) {
                if (said.length() > 0) said.append(' ');
                said.append("not applied: the DX wrapper isn't DXVK (WineD3D has its own GPU name setting)");
                logSessionLine("gpu", said.toString());
                return;
            }
            java.util.LinkedHashMap<String, String> ours = com.winhub.hernandez.core.GpuSpoof.dxvkOptions(card, memMb);
            if (!ours.isEmpty()) {
                String existing = envVars.has("DXVK_CONFIG") ? envVars.get("DXVK_CONFIG") : "";
                String userPath = envVars.has("DXVK_CONFIG_FILE") ? envVars.get("DXVK_CONFIG_FILE") : null;
                String userFile = com.winhub.hernandez.core.GpuSpoof.readConfigFile(userPath);
                com.winhub.hernandez.core.GpuSpoof.Merge m =
                        com.winhub.hernandez.core.GpuSpoof.mergeDxvkConfig(existing, userFile, ours);

                // Route 1 — the generated config file. The only route that can carry a GPU name: a
                // quoted value is what DXVK's file parser is written for, while the same name sent
                // through the environment never reached the game (core.GpuSpoof). A config file of
                // the user's own is folded in underneath ours, not replaced; when it can't be read from
                // the Android side we leave DXVK_CONFIG_FILE pointing at it and go with the environment
                // alone, since hijacking it would take their whole config away. This also takes over
                // from a dxvk.conf sitting in the game's folder, which DXVK reads only while
                // DXVK_CONFIG_FILE is unset — the trade for a spoof that actually arrives.
                String wrote = null, noFile = null;
                if (com.winhub.hernandez.core.GpuSpoof.isConfigFilePath(userPath) && userFile == null) {
                    noFile = "your config file " + userPath + " can't be read from here, so it stays in charge";
                } else if (container == null) {
                    noFile = "no container directory to write one in";
                } else {
                    File conf = new File(container.getRootDir(), DXVK_GENERATED_CONF);
                    String path = conf.getPath();
                    if (!com.winhub.hernandez.core.GpuSpoof.envSafe(path))
                        noFile = path + " can't go through the environment";
                    else if (!FileUtils.writeString(conf,
                            com.winhub.hernandez.core.GpuSpoof.configFileText(m.options, userPath, userFile)))
                        noFile = "couldn't write " + path;
                    else { envVars.put("DXVK_CONFIG_FILE", path); wrote = path; }
                }

                // Route 2 — the ids and the memory cap, whitespace-free so they survive the trip.
                if (!m.value.isEmpty()) envVars.put("DXVK_CONFIG", m.value);

                // Only a name that really went out may reach the app's own readouts: the HUD and the
                // Task Manager must never name a GPU the game was not told about. A name of the user's
                // own is in m.kept instead of m.options, and theirs is the one the game will report.
                if (wrote != null && m.options.containsKey(com.winhub.hernandez.core.GpuSpoof.KEY_DXGI_DEVICE_DESC)) {
                    deliveredGpuSpoofName = gpuName;
                    // The Task Manager's CONTAINER block was built in setupUI, long before this ran —
                    // rebuild it so the GPU-name row appears (the HDR row refreshes the same way).
                    runOnUiThread(() -> XServerDialogState.INSTANCE.setTmContainerInfo(buildTmContainerInfo()));
                }

                // What each route actually carried. The line used to claim a DXVK_CONFIG delivery for
                // the name that the game never saw, which is how the spoof stayed broken so long.
                StringBuilder how = new StringBuilder();
                if (wrote != null) how.append(wrote).append(" (")
                        .append(String.join(", ", m.options.keySet())).append(')');
                if (!m.added.isEmpty()) how.append(how.length() > 0 ? " + " : "")
                        .append("DXVK_CONFIG (").append(String.join(", ", m.added)).append(')');
                said.append(how.length() > 0 ? " via " : " NOT delivered: nothing could be written").append(how);
                if (noFile != null) said.append("; no generated config file: ").append(noFile);
                if (memMb > 0) said.append(", memory cap ").append(memMb).append(" MB (dxgi.maxDeviceMemory)");
                if (!m.kept.isEmpty()) said.append("; yours kept: ").append(String.join(", ", m.kept));
                // DXVK only started reading DXVK_CONFIG in 2.5 (2.4.1 ignores it outright, device-proven),
                // so on an older one the file is the whole delivery and the environment is dead weight.
                String dxvkVersion = dxwrapperConfig != null ? dxwrapperConfig.get("version") : null;
                if (dxvkVersion != null && !dxvkVersion.isEmpty() && compareVersion(dxvkVersion, "2.5") < 0)
                    said.append("; DXVK ").append(dxvkVersion).append(" ignores DXVK_CONFIG (2.5 and up read it)");
            }
            logSessionLine("gpu", said.toString());
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "wayland: GPU name spoof failed", t);
        }
    }

    /** The GPU name the Wayland driver settings ask this session to report, or null when it asks for
     *  none — what the launch path above tries to deliver, read the same way it reads every other
     *  graphicsDriverConfig key (the shortcut's override is already folded into that field). */
    private String requestedWaylandGpuSpoof() {
        if (!waylandMode) return null;
        String gpuName = graphicsDriverConfig != null ? graphicsDriverConfig.get("gpuName") : null;
        return com.winhub.hernandez.core.GpuSpoof.isSpoofing(gpuName) ? gpuName : null;
    }

    /** The GPU name this session actually got out to the game, or null when the game sees the real
     *  adapter — set by applyWaylandGpuSpoofEnv only once the name is written where DXVK will read it,
     *  so the app's readouts follow the delivery instead of the setting. This exists at all because
     *  Wayland hands the spoof to DXVK and nothing renames the Vulkan device: every query the app makes
     *  still answers with the real chip. On X11 the wrapper's ICD does the renaming, so the readouts
     *  are handed the spoofed name already and this stays null. Launch worker writes, UI thread reads. */
    private volatile String deliveredGpuSpoofName = null;

    /** The HUD's display-server label: "X11" / "Wayland" (the HDR state has a line of its own). */
    private String hudDisplayServerLabel() {
        return waylandMode ? "Wayland" : "X11";
    }

    /** The Fusion HUD's HDR line, directly under latency · display server - only in sessions whose HDR
     *  gate is open (everywhere else FusionHdr.NONE: no line, the HUD exactly as before):
     *  "HDR" while HDR frames are really on screen with HDR headroom; "HDR (no headroom)" while they are
     *  on screen but the display has given them none for 5 s+ (brightness at maximum, or a screen
     *  recording: Android turns HDR headroom off while the screen is recorded); "HDR off" while the
     *  drawer's HDR output switch is off (tone-mapped to SDR); "HDR tone-mapped" while the switch is on
     *  but the frames are tone-mapped anyway (frame generation on a screen with no HDR10 swapchain);
     *  "HDR not on this screen" when the screen the game is on NOW has no HDR10 (the TV was unplugged
     *  mid-game); "HDR ready" otherwise (no HDR frames on screen right now). What is on screen wins:
     *  frames that stay HDR with the switch off read "HDR". */
    private volatile int hudHdrState = 0;          // WaylandCompositor.nativeHdrState()
    private volatile boolean hudHdrToneMapped = false; // WaylandCompositor.nativeHdrToneMappedOnScreen()
    private volatile boolean hudHdrGateOpen = false; // the compositor opened HDR for this session
    /** The drawer's HDR output switch (per session, starts on; only offered while the HDR gate is open). */
    private volatile boolean waylandHdrOutputOn = true;
    private int hudHdrCode() {
        if (!waylandMode || !hudHdrGateOpen) return com.winhub.hernandez.widget.fusionhud.FusionHdr.NONE;
        if (hudHdrState == 1) return com.winhub.hernandez.widget.fusionhud.FusionHdr.ON;
        if (hudHdrState == 2) return com.winhub.hernandez.widget.fusionhud.FusionHdr.NO_HEADROOM;
        if (!waylandHdrOutputOn) return com.winhub.hernandez.widget.fusionhud.FusionHdr.OFF;
        if (hudHdrToneMapped) return com.winhub.hernandez.widget.fusionhud.FusionHdr.TONEMAPPED;
        // No HDR frames on screen right now — and "ready" is only honest on a screen that could show
        // them. The colour-manager offer to the game is fixed for the life of the session (it cannot be
        // withdrawn from a running client), so the gate deliberately stays open after the TV is pulled;
        // the SCREEN underneath is what changed, and on a panel with no HDR10 SurfaceFlinger tone-maps
        // whatever we tag. The session log already says exactly that — this stops the HUD contradicting it.
        return sessionDisplaySupportsHdr10() ? com.winhub.hernandez.widget.fusionhud.FusionHdr.READY
                                             : com.winhub.hernandez.widget.fusionhud.FusionHdr.NOT_ON_THIS_SCREEN;
    }

    /** Does the display this session is on NOW report HDR10? Reads the capability
     *  {@link #reportHdrCapability} keeps for the current display (re-read on every display move, so it
     *  cannot go stale), and answers optimistically before the first read: an unknown display must not
     *  contradict a gate the compositor really did open. */
    private boolean sessionDisplaySupportsHdr10() {
        com.winhub.hernandez.display.DisplayHdrInfo info = hdrInfo;
        return info == null || info.supportsHdr10;
    }

    /** Tell the compositor what the game's display reports (the HDR gate's input; logged on change). */
    private void pushWaylandHdrDisplay(com.winhub.hernandez.display.DisplayHdrInfo d) {
        if (!waylandMode || d == null) return;
        try {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHdrDisplay(d.displayId, d.displayName, d.formats,
                    d.supportsHdr10, d.maxLuminance, d.maxAverageLuminance, d.minLuminance,
                    d.hdrSdrRatioAvailable, d.hdrSdrRatio, android.os.Build.VERSION.SDK_INT);
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHdrHighestRatio(d.highestHdrSdrRatio);
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "wayland: HDR display push failed", t);
        }
    }

    /** The in-game drawer's Wayland rows (Graphics tab): seed the Zero-copy toggle from the effective
     *  env and wire its live switch + writer + the frame-count poll. Runs from setupUI, after the
     *  container and shortcut are resolved and after the drawer's reset() in onCreate. */
    private void setupWaylandDrawerGlue() {
        XServerDrawerState state = XServerDrawerState.INSTANCE;
        state.setWaylandZeroCopyRequested(isWaylandZeroCopyRequested());
        // Writes BANNER_WAYLAND_ZERO_COPY into the SHORTCUT's env vars on a shortcut launch, else the
        // container's; every other variable is kept. The two env strings are concatenated at launch
        // (container first, shortcut second, so the shortcut wins), hence for a shortcut: ON puts =1;
        // OFF removes the var, or writes =0 when the container's own env still carries =1 — removing
        // it from the shortcut alone would leave the container's value in force.
        state.onWaylandZeroCopyToggle = on -> {
            // Live first, so the switch is felt before any disk write: the compositor flips its state
            // on its own thread and tells every bound game (banner_ahb_v1.mode) to rebuild its
            // swapchain on -- or off -- gralloc buffers. The env write below is only the DEFAULT for
            // the next launch; nothing in this session waits for it.
            try {
                com.winhub.hernandez.wayland.WaylandCompositor.nativeSetZeroCopy(on);
                state.setWaylandZeroCopyActive(on);
            } catch (Throwable t) {
                Log.e("XServerDisplayActivity", "wayland: live zero-copy switch failed", t);
            }
            try {
                if (shortcut != null) {
                    EnvVars env = new EnvVars(shortcut.getExtra("envVars", ""));
                    if (on) env.put("BANNER_WAYLAND_ZERO_COPY", "1");
                    else if (isZeroCopyEnvOn(container.getEnvVars())) env.put("BANNER_WAYLAND_ZERO_COPY", "0");
                    else env.remove("BANNER_WAYLAND_ZERO_COPY");
                    String out = env.toString();
                    shortcut.putExtra("envVars", out.isEmpty() ? null : out);
                    shortcut.saveData();
                } else {
                    EnvVars env = new EnvVars(container.getEnvVars());
                    if (on) env.put("BANNER_WAYLAND_ZERO_COPY", "1");
                    else env.remove("BANNER_WAYLAND_ZERO_COPY");
                    container.setEnvVars(env.toString());
                    container.saveData();
                }
                Log.i("XServerDisplayActivity", "wayland: zero-copy presentation " + (on ? "on" : "off")
                        + " applied live and saved to " + (shortcut != null ? "shortcut" : "container")
                        + " env as the next launch's default");
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "wayland: zero-copy env write failed", e);
            }
        };
        // OpenGL safe mode. GALLIUM_THREAD is read by Mesa when the GL driver comes up inside the
        // guest, so unlike zero-copy there is nothing to flip live: the switch is the DEFAULT for the
        // next launch of this game, and the row says so. Written to the SAME owner
        // resolvedWaylandGlSafeMode() reads from (shortcut on a shortcut launch, else the container),
        // so the toggle can never be inert.
        state.setWaylandGlSafeMode(resolvedWaylandGlSafeMode());
        state.onWaylandGlSafeModeToggle = on -> {
            try {
                if (shortcut != null) {
                    shortcut.putExtra("waylandGlSafeMode", on ? "1" : "0");
                    shortcut.saveData();
                } else {
                    container.setWaylandGlSafeMode(on);
                    container.saveData();
                }
                Log.i("XServerDisplayActivity", "wayland: OpenGL safe mode " + (on ? "on" : "off")
                        + " saved to " + (shortcut != null ? "shortcut" : "container")
                        + " - applies at the next launch (GALLIUM_THREAD is read when Mesa starts)");
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "wayland: OpenGL safe mode write failed", e);
            }
        };

        // HDR output (HDR sessions only: the row is shown once the sampler below sees the gate open).
        // A live, per-session switch - nothing is saved: the editors' "HDR output" setting stays the
        // next launch's choice (DXVK_HDR and the colour-manager offer are decided at launch).
        waylandHdrOutputOn = true;
        state.setWaylandHdrOutput(true);
        state.onWaylandHdrOutputToggle = on -> {
            waylandHdrOutputOn = on;
            try {
                com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHdrOutput(on);
            } catch (Throwable t) {
                Log.e("XServerDisplayActivity", "wayland: live HDR output switch failed", t);
            }
            Log.i("XServerDisplayActivity", "wayland: HDR output " + (on ? "on" : "off (tone-mapped to SDR)")
                    + " for this session");
            if (fusionHud != null) fusionHud.setHdrState(hudHdrCode());
        };

        // Last-10-s zero-copy frame count, straight from the compositor's stats window, plus whether
        // a zero-copy frame reached the display layer just now. The 10 s counter cannot show a switch
        // that happened two seconds ago; the age can, so the row says "switching..." only for as long
        // as it really is.
        state.onWaylandZeroCopyPoll = () -> {
            state.setWaylandZeroCopyFrames(com.winhub.hernandez.wayland.WaylandCompositor.nativeZeroCopyFrames());
            int age = com.winhub.hernandez.wayland.WaylandCompositor.nativeZeroCopyLastFrameAgeMs();
            state.setWaylandZeroCopyLive(age >= 0 && age < 1500);
        };
    }

    // ───── HDR capability reporting (both backends; reporting only, nothing turns HDR on) ─────
    // We have exactly one data point on HDR hardware (this device: none) and no idea what testers'
    // phones report, so every session records the real platform answer. It is deliberately NOT a
    // toggle: the compositor emits no colour metadata at all, so a switch would promise output we do
    // not produce.
    //
    // Capability belongs to the DISPLAY, not the device - it comes from that connector's EDID - and
    // the game can move onto an external screen at runtime (ExternalDisplayController + Presentation).
    // So it is read live for the display the game is on, and re-read whenever the display set changes.
    private com.winhub.hernandez.display.DisplayHdrInfo hdrInfo;
    private android.hardware.display.DisplayManager hdrDisplayManager;
    private final android.hardware.display.DisplayManager.DisplayListener hdrDisplayListener =
            new android.hardware.display.DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId)   { reportHdrCapability("display added"); }
        @Override public void onDisplayRemoved(int displayId) {
            // The removal is the FIRST notice that a TV went away — the window's own display id still
            // reads as the dead one until the system finishes handing the task back, so this cannot
            // wait for onConfigurationChanged. A session whose window is ON that display pauses now; the
            // capability re-read below then runs against whatever we ended up on. onTvLaunchDisplay()
            // and not the requested id: a session the system refused to put on the TV is already running
            // on the handheld, and pulling that TV's cable must not pause it.
            if (displayId == tvLaunchDisplayId && onTvLaunchDisplay()) {
                Log.i("XServerDisplayActivity", "TV: launch display " + displayId + " removed");
                onTvDisconnected();
            }
            reportHdrCapability("display removed");
            checkSessionDisplay("display removed");
        }
        @Override public void onDisplayChanged(int displayId) { reportHdrCapability("display changed"); }
    };

    /**
     * Register the one display watch this session has (HDR capability + the TV unplug path above).
     * Split out of {@link #startHdrCapabilityReport()} so onCreate can arm it before the container
     * setup begins: setupUI, where the report itself starts, can be a long way off — and on a portrait
     * container it does not run until the orientation flips — which used to leave a cable pulled during
     * setup unnoticed until the next resume or configuration change.
     *
     * <p>Idempotent: the manager field doubles as the "already registered" flag, so the onCreate call
     * and {@link #startHdrCapabilityReport()}'s cannot register twice. Unregistered where it always
     * was, in {@link #stopHdrCapabilityReport()} (onDestroy runs it even for a launch that bails early).
     */
    private void registerDisplayWatch() {
        if (hdrDisplayManager != null) return;
        try {
            hdrDisplayManager = (android.hardware.display.DisplayManager)
                    getSystemService(android.content.Context.DISPLAY_SERVICE);
            if (hdrDisplayManager != null)
                hdrDisplayManager.registerDisplayListener(hdrDisplayListener,
                        new android.os.Handler(getMainLooper()));
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "HDR: display listener unavailable", t);
        }
    }

    private void startHdrCapabilityReport() {
        registerDisplayWatch();
        reportHdrCapability("session start");
        if (waylandMode && waylandHdrMode != com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_OFF)
            startHdrRatioSampler();
    }

    private void stopHdrCapabilityReport() {
        stopHdrRatioSampler();
        if (waylandMode) {
            // The compositor's "HDR on screen: ..." summary (once; a no-op when HDR was never asked for).
            try { com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrSessionEnd(); } catch (Throwable ignored) {}
        }
        if (hdrDisplayManager == null) return;
        try { hdrDisplayManager.unregisterDisplayListener(hdrDisplayListener); } catch (Throwable ignored) {}
        hdrDisplayManager = null;
    }

    // ───── HDR evidence on Wayland: the display's live HDR/SDR ratio (API 34+) ─────
    // The one platform reading that says an HDR layer is really being SHOWN as HDR: 1.0 while only SDR
    // is on screen, above 1.0 once the display grants the picture HDR headroom. The compositor tags the
    // game's frames and counts them; this feeds it what the display did with them, so the session log
    // (and its "HDR on screen: ..." line) can tell "tagged" from "shown". Runs only while the HDR switch
    // is on, stops by itself when the compositor reports the gate closed, and never throws.
    private final android.os.Handler hdrRatioHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable hdrRatioSampler;
    private java.util.function.Consumer<android.view.Display> hdrRatioListener;
    private android.view.Display hdrRatioDisplay;

    private void startHdrRatioSampler() {
        if (hdrRatioSampler != null) return;
        hdrRatioSampler = new Runnable() {
            @Override public void run() {
                if (hdrRatioSampler != this) return;
                int gate;
                try { gate = com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrGateState(); }
                catch (Throwable t) { gate = 0; }
                if (gate == 0) {  // closed: nothing to prove this session, and no drawer switch / HUD line
                    XServerDrawerState.INSTANCE.setWaylandHdrAvailable(false);
                    if (hudHdrGateOpen) {
                        hudHdrGateOpen = false;
                        if (fusionHud != null) fusionHud.setHdrState(hudHdrCode());
                    }
                    stopHdrRatioSampler();
                    return;
                }
                if (gate == 1) {
                    XServerDrawerState drawer = XServerDrawerState.INSTANCE;
                    drawer.setWaylandHdrAvailable(true);
                    boolean hudChanged = !hudHdrGateOpen;
                    hudHdrGateOpen = true;
                    android.view.Display d = hdrTargetDisplay();
                    armHdrRatioListener(d);
                    armHdrEvidence();
                    // Thermal headroom: at most every 10 s (Android returns NaN when asked more often than
                    // once a second), then the whole evidence set to the compositor (it logs changes only).
                    if (hdrEvidenceTick++ % 10 == 0) readHdrThermalHeadroom();
                    pushHdrEvidence();
                    applyScreenHdrHeadroom();
                    float ratio = com.winhub.hernandez.display.DisplayHdrInfo.liveHdrSdrRatio(d);
                    try { com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrSdrRatioSample(ratio, false); }
                    catch (Throwable ignored) {}
                    // The HUD's HDR line (hudHdrCode) and the drawer row: "HDR" while HDR frames are
                    // really on screen (the compositor's verdict: frames tagged BT2020_PQ in the last
                    // 1.5 s and, where Android reports it, an HDR/SDR ratio above 1), "HDR (no headroom)"
                    // after 5 s of ratio 1.00 with HDR frames on screen.
                    int state;
                    boolean toneMapped;
                    try { state = com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrState(); }
                    catch (Throwable t) { state = 0; }
                    try { toneMapped = com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrToneMappedOnScreen(); }
                    catch (Throwable t) { toneMapped = false; }
                    drawer.setWaylandHdrOnScreen(state == 1);
                    drawer.setWaylandHdrNoHeadroom(state == 2);
                    drawer.setWaylandHdrToneMapped(toneMapped);
                    if (state != hudHdrState) { hudHdrState = state; hudChanged = true; }
                    if (toneMapped != hudHdrToneMapped) { hudHdrToneMapped = toneMapped; hudChanged = true; }
                    if (hudChanged && fusionHud != null) fusionHud.setHdrState(hudHdrCode());
                }
                hdrRatioHandler.postDelayed(this, 1000);
            }
        };
        hdrRatioHandler.postDelayed(hdrRatioSampler, 1000);
    }

    /** Register the display's own ratio listener (changes arrive at once, not a second later); moves with
     *  the game to another display. Silently nothing where the display has no ratio (API < 34 / SDR).
     *  Reached by reflection: Display.registerHdrSdrRatioListener is not in the compile SDK's stubs,
     *  and where it is missing the one-second sampler above is all there is (nothing is lost but speed). */
    private void armHdrRatioListener(android.view.Display d) {
        if (android.os.Build.VERSION.SDK_INT < 34 || d == null || d == hdrRatioDisplay) return;
        disarmHdrRatioListener();
        hdrRatioDisplay = d; // whatever happens below, do not retry every second
        try {
            if (!d.isHdrSdrRatioAvailable()) return;
            java.util.function.Consumer<android.view.Display> l = disp -> {
                float r = com.winhub.hernandez.display.DisplayHdrInfo.liveHdrSdrRatio(disp);
                try { com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrSdrRatioSample(r, true); }
                catch (Throwable ignored) {}
            };
            java.util.concurrent.Executor ex = hdrRatioHandler::post;
            android.view.Display.class.getMethod("registerHdrSdrRatioListener",
                    java.util.concurrent.Executor.class, java.util.function.Consumer.class).invoke(d, ex, l);
            hdrRatioListener = l;
        } catch (Throwable t) {
            hdrRatioListener = null;
            Log.w("XServerDisplayActivity", "HDR: ratio listener unavailable (sampling once a second instead)", t);
        }
    }

    private void disarmHdrRatioListener() {
        if (hdrRatioDisplay != null && hdrRatioListener != null) {
            try {
                android.view.Display.class.getMethod("unregisterHdrSdrRatioListener", java.util.function.Consumer.class)
                        .invoke(hdrRatioDisplay, hdrRatioListener);
            } catch (Throwable ignored) {}
        }
        hdrRatioListener = null;
        hdrRatioDisplay = null;
    }

    private void stopHdrRatioSampler() {
        if (hdrRatioSampler != null) hdrRatioHandler.removeCallbacks(hdrRatioSampler);
        hdrRatioSampler = null;
        disarmHdrRatioListener();
        disarmHdrEvidence();
    }

    // ───── HDR evidence beside the headroom (HDR sessions only; non-root APIs, no permission) ─────
    // The Fold lost HDR headroom with HDR frames on screen in three ways: a screen recording (Android turns
    // headroom off for it), heat under load, and possibly the brightness slider at maximum. So the session
    // log carries what the device says about heat and brightness - PowerManager thermal status (+ a
    // listener) and thermal headroom, the brightness setting and its mode (+ an observer). A screenshot or
    // a screen recording is not detected in this build (that needs extra permissions); the log names it as
    // a possible cause instead. Nothing here prompts.
    private boolean hdrEvidenceArmed;
    private Object hdrThermalListener;          // PowerManager.OnThermalStatusChangedListener (API 29)
    private android.database.ContentObserver hdrBrightnessObserver;
    private float hdrThermalHeadroom = Float.NaN;
    private int hdrEvidenceTick;

    private void armHdrEvidence() {
        if (hdrEvidenceArmed) return;
        hdrEvidenceArmed = true;
        java.util.concurrent.Executor ex = hdrRatioHandler::post;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
                if (pm != null) {
                    android.os.PowerManager.OnThermalStatusChangedListener l = status -> pushHdrEvidence();
                    pm.addThermalStatusListener(ex, l);
                    hdrThermalListener = l;
                }
            } catch (Throwable t) {
                Log.w("XServerDisplayActivity", "HDR evidence: no thermal status listener", t);
            }
        }
        try {
            hdrBrightnessObserver = new android.database.ContentObserver(hdrRatioHandler) {
                @Override public void onChange(boolean selfChange) { pushHdrEvidence(); }
            };
            android.content.ContentResolver cr = getContentResolver();
            cr.registerContentObserver(android.provider.Settings.System.getUriFor(
                    android.provider.Settings.System.SCREEN_BRIGHTNESS), false, hdrBrightnessObserver);
            cr.registerContentObserver(android.provider.Settings.System.getUriFor(
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE), false, hdrBrightnessObserver);
        } catch (Throwable t) {
            hdrBrightnessObserver = null;
            Log.w("XServerDisplayActivity", "HDR evidence: no brightness observer", t);
        }
        readHdrThermalHeadroom();
        pushHdrEvidence();
    }

    // ───── The screen surface's HDR headroom request (frames through the HDR10 swapchain) ─────
    // The game's display layer asks for headroom itself (sc_layer.c, ASurfaceTransaction_setDesiredHdrHeadroom).
    // Frame generation presents HDR through the compositor's own swapchain on this SurfaceView instead, so the
    // request goes on the SurfaceView (API 35): content peak / SDR white, cleared when those frames stop. Some
    // phones only boost HDR when a surface asks.
    private float hdrScreenHeadroomApplied = -1f; // -1 = never set
    private boolean hdrScreenHeadroomMissingSaid;

    private void applyScreenHdrHeadroom() {
        float want;
        try { want = com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrScreenHeadroom(); }
        catch (Throwable t) { return; }
        if (hdrScreenHeadroomApplied < 0f && want <= 0f) return;            // never asked: leave it be
        if (Math.abs(want - hdrScreenHeadroomApplied) < 0.005f) return;
        android.view.SurfaceView sv = waylandSurfaceView;
        if (sv == null) return;
        String how = null;
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            try {
                android.view.SurfaceView.class.getMethod("setDesiredHdrHeadroom", float.class).invoke(sv, want);
                how = "SurfaceView.setDesiredHdrHeadroom";
            } catch (Throwable t) {
                try {
                    android.view.SurfaceControl sc = sv.getSurfaceControl();
                    android.view.SurfaceControl.Transaction tx = new android.view.SurfaceControl.Transaction();
                    android.view.SurfaceControl.Transaction.class.getMethod("setDesiredHdrHeadroom",
                            android.view.SurfaceControl.class, float.class).invoke(tx, sc, want);
                    tx.apply();
                    how = "SurfaceControl.Transaction.setDesiredHdrHeadroom";
                } catch (Throwable t2) {
                    how = null;
                }
            }
        }
        hdrScreenHeadroomApplied = want;
        try {
            if (how == null) {
                if (!hdrScreenHeadroomMissingSaid && want > 0f) {
                    hdrScreenHeadroomMissingSaid = true;
                    com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrNoteHeadroomRequest(-1f);
                    com.winhub.hernandez.wayland.WaylandCompositor.nativeLogColor("HDR headroom request on the screen surface "
                            + "not available (Android < 15): frames through the HDR10 swapchain rely on Android's default");
                }
                return;
            }
            com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrNoteHeadroomRequest(want);
            if (want > 0f) {
                String why = com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrScreenHeadroomWhy();
                com.winhub.hernandez.wayland.WaylandCompositor.nativeLogColor(String.format(java.util.Locale.US,
                        "requested HDR headroom %.1fx on the screen surface (HDR10 swapchain for frame generation; %s) via %s",
                        want, why, how));
            } else {
                com.winhub.hernandez.wayland.WaylandCompositor.nativeLogColor("HDR headroom request on the screen surface "
                        + "cleared (no preference): no HDR frames go through the swapchain any more");
            }
        } catch (Throwable ignored) {}
    }

    private void readHdrThermalHeadroom() {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
            if (pm != null) hdrThermalHeadroom = pm.getThermalHeadroom(10);
        } catch (Throwable t) {
            hdrThermalHeadroom = Float.NaN;
        }
    }

    /** Thermal status + headroom and the brightness setting to the compositor (it logs changes only). */
    private void pushHdrEvidence() {
        int thermal = -1, brightness = -1, mode = -1;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
                if (pm != null) thermal = pm.getCurrentThermalStatus();
            } catch (Throwable ignored) {}
        }
        try {
            brightness = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS, -1);
            mode = android.provider.Settings.System.getInt(getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
        } catch (Throwable ignored) {}
        float headroom = hdrThermalHeadroom;
        if (Float.isNaN(headroom) || Float.isInfinite(headroom)) headroom = -1f;
        try { com.winhub.hernandez.wayland.WaylandCompositor.nativeHdrEnvSample(thermal, headroom, brightness, mode); }
        catch (Throwable ignored) {}
    }

    private void disarmHdrEvidence() {
        if (!hdrEvidenceArmed) return;
        hdrEvidenceArmed = false;
        if (android.os.Build.VERSION.SDK_INT >= 29 && hdrThermalListener != null) {
            try {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(android.content.Context.POWER_SERVICE);
                if (pm != null) pm.removeThermalStatusListener(
                        (android.os.PowerManager.OnThermalStatusChangedListener) hdrThermalListener);
            } catch (Throwable ignored) {}
        }
        hdrThermalListener = null;
        if (hdrBrightnessObserver != null) {
            try { getContentResolver().unregisterContentObserver(hdrBrightnessObserver); } catch (Throwable ignored) {}
            hdrBrightnessObserver = null;
        }
    }

    /** The display the game is on: the TV when it has been moved there, else this activity's. */
    private android.view.Display hdrTargetDisplay() {
        try {
            if (externalDisplayController != null) {
                android.view.Display d = externalDisplayController.getExternalGameDisplay();
                if (d != null) return d;
            }
        } catch (Throwable ignored) {}
        try { return getWindowManager().getDefaultDisplay(); } catch (Throwable ignored) { return null; }
    }

    /** Read the capability now and record it: one "display" line in the Wayland session log (only
     *  when the answer actually changed, so a chatty DisplayManager cannot flood it) and the value
     *  the Task Manager's CONTAINER block shows. Never throws. */
    private void reportHdrCapability(String why) {
        try {
            com.winhub.hernandez.display.DisplayHdrInfo now =
                    com.winhub.hernandez.display.DisplayHdrInfo.read(hdrTargetDisplay());
            boolean first = hdrInfo == null;
            if (!first && now.sameAs(hdrInfo)) return;
            hdrInfo = now;
            String line = now.logLine() + " [" + why + "]";
            Log.i("XServerDisplayActivity", "HDR: " + line);
            if (waylandMode) {
                try { com.winhub.hernandez.wayland.WaylandCompositor.nativeLogDisplay(line); }
                catch (Throwable t) { Log.w("XServerDisplayActivity", "HDR: session-log write failed", t); }
                if (!first) pushWaylandHdrDisplay(now); // the HDR output hears about a new display too
                // The readouts follow the SCREEN, not the gate: once the game moves to a display with no
                // HDR10 (the cable came out) the HUD must stop saying "HDR ready" and the drawer must
                // stop presenting the session as HDR-capable. Nothing else refreshes them — the
                // once-a-second sampler only pushes when the compositor's own verdict changes, and a
                // display move changes neither of its values.
                XServerDrawerState.INSTANCE.setWaylandHdrScreenCapable(now.supportsHdr10);
                if (fusionHud != null) fusionHud.setHdrState(hudHdrCode());
            }
            // The Task Manager header is built once at launch; refresh it so a screen plugged in
            // mid-game updates the row instead of showing the handheld's answer for ever.
            if (!first) runOnUiThread(() ->
                    XServerDialogState.INSTANCE.setTmContainerInfo(buildTmContainerInfo()));
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "HDR: capability read failed", t);
        }
    }

    /** Short HDR value for the Task Manager's CONTAINER block ("none - panel 500 nits"). */
    private String hdrRowValue() {
        com.winhub.hernandez.display.DisplayHdrInfo info = hdrInfo;
        if (info == null) info = com.winhub.hernandez.display.DisplayHdrInfo.read(hdrTargetDisplay());
        return info.shortSummary();
    }

    private static boolean isZeroCopyEnvOn(String raw) {
        if (raw == null || raw.isEmpty()) return false;
        String zc = new EnvVars(raw).get("BANNER_WAYLAND_ZERO_COPY");
        return zc != null && (zc.equals("1") || zc.equalsIgnoreCase("true"));
    }

    /** "compositor: <adrenotools driver> · game: <Wayland game driver>" for the Task Manager's
     *  CONTAINER block on Wayland (the X11 graphicsDriver id is idle in that session). */
    private String waylandDriverSummary() {
        String comp = "System";
        try {
            String gdc = (shortcut != null)
                    ? shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig())
                    : container.getGraphicsDriverConfig();
            String driverId = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog.getVersion(gdc);
            if (driverId != null && !driverId.isEmpty() && !driverId.equals("System")) {
                AdrenotoolsManager atm = new AdrenotoolsManager(this);
                String name = atm.getDriverName(driverId);
                String ver = atm.getDriverVersion(driverId);
                comp = (name == null || name.isEmpty() ? driverId : name) + (ver == null || ver.isEmpty() ? "" : " " + ver);
                if (com.winhub.hernandez.core.WaylandAdapter.isProprietaryBlob(this, driverId))
                    comp += " (screen on a bundled Turnip: the blob can't import frames)";
            }
        } catch (Exception e) {
            Log.w("XServerDisplayActivity", "wayland: compositor driver name unavailable", e);
        }
        String game;
        try {
            String choice = com.winhub.hernandez.core.WaylandGameDriver.effectiveChoice(container, shortcut);
            // Auto resolves to the adapter whenever it can (WaylandGameDriver.resolve), so both read alike.
            if (com.winhub.hernandez.core.WaylandGameDriver.isAdapterChoice(choice)
                    && com.winhub.hernandez.core.WaylandAdapter.isBundled(this)) {
                String gdc = (shortcut != null)
                        ? shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig())
                        : container.getGraphicsDriverConfig();
                String driverId = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog.getVersion(gdc);
                String why = com.winhub.hernandez.core.WaylandAdapter.unusableReason(this, driverId);
                if (why == null) game = "adapter on " + comp;
                else {
                    String v = com.winhub.hernandez.core.WaylandGameDriver.autoVariantIfKnown();
                    game = (v == null ? "Auto (by GPU)" : com.winhub.hernandez.core.WaylandGameDriver.variantShortName(v) + " (auto)")
                            + " - adapter off: " + why;
                }
            } else if (com.winhub.hernandez.core.WaylandGameDriver.isImported(choice)) {
                com.winhub.hernandez.core.WaylandGameDriver.Resolution r =
                        com.winhub.hernandez.core.WaylandGameDriver.resolve(this, choice);
                game = r.icdPath != null
                        ? new com.winhub.hernandez.contents.WaylandGameDriverManager(this)
                                .getDriverName(com.winhub.hernandez.core.WaylandGameDriver.importedId(r.choice)) + " (imported)"
                        : com.winhub.hernandez.core.WaylandGameDriver.variantShortName(r.variant);
            } else if (Container.WAYLAND_GAME_DRIVER_AUTO.equals(choice)) {
                // Auto's answer is cached by the GPU probe the launch env export runs; before that
                // (or if it never ran) say Auto rather than probing the GPU on the UI thread here.
                String v = com.winhub.hernandez.core.WaylandGameDriver.autoVariantIfKnown();
                game = v == null ? "Auto (by GPU)"
                        : com.winhub.hernandez.core.WaylandGameDriver.variantShortName(v) + " (auto)";
            } else {
                game = com.winhub.hernandez.core.WaylandGameDriver.variantShortName(
                        com.winhub.hernandez.core.WaylandGameDriver.resolve(this, choice).variant);
            }
        } catch (Exception e) {
            game = "—";
        }
        return "compositor: " + comp + " · game: " + game;
    }

    private void startWaylandCompositor(FrameLayout rootView) {
        // Wayland has no XServer onUpdateWindowContent hook to dismiss the launch overlay, so
        // dismiss on the compositor's FIRST presented client frame instead (mirrors the X11 grace
        // delay so the boot steps are briefly visible). Fires on the compositor thread -> marshal
        // to UI. Guard with winStarted so it runs exactly once.
        com.winhub.hernandez.wayland.WaylandCompositor.setFirstFrameListener(() -> runOnUiThread(() -> {
            if (winStarted) return;
            winStarted = true;
            cancelLaunchTimers();
            long grace = LAUNCH_OVERLAY_GRACE_MS;
            new android.os.Handler(getMainLooper()).postDelayed(
                    preloaderDialog::closeOnUiThread, grace);
        }));
        // Performance HUD: X11 shows it when a window gets _MESA_DRV and counts X presents. Here the
        // compositor reports the window presenting GPU frames, then each of its frames.
        com.winhub.hernandez.wayland.WaylandCompositor.setGameListener(new com.winhub.hernandez.wayland.WaylandCompositor.GameListener() {
            @Override public void onGameSurface(String window, String gpuName) {
                if (window == null) {
                    frameRatingWindowId = -1;
                    fpsCounter.reset();
                    runOnUiThread(() -> {
                        if (frameRating != null) { frameRating.setVisibility(View.GONE); frameRating.reset(); }
                        if (frameRatingHorizontal != null) { frameRatingHorizontal.setVisibility(View.GONE); frameRatingHorizontal.reset(); }
                        if (perfHud != null) perfHud.setVisibility(View.GONE);
                        if (gameNativeHud != null) gameNativeHud.setVisibility(View.GONE);
                        if (fusionHud != null) fusionHud.setVisibility(View.GONE);
                    });
                    return;
                }
                Log.d("XServerDisplayActivity", "wayland: HUD follows " + window);
                frameRatingWindowId = WAYLAND_HUD_WINDOW_ID;
                if (gpuName != null && !gpuName.isEmpty())
                    hudGpuName = com.winhub.hernandez.core.GPUInformation.extractModelName(gpuName);
                runOnUiThread(() -> {
                    if (hudGpuName != null) {
                        if (frameRating != null) frameRating.setGpuName(hudGpuName);
                        if (perfHud != null) perfHud.setGpuModel(hudGpuName);
                        if (gameNativeHud != null) gameNativeHud.setGpuModel(hudGpuName);
                        if (fusionHud != null) fusionHud.setGpuModel(hudGpuName);
                    }
                    // The name above is the adapter the COMPOSITOR is really on. When the session got a
                    // spoof out to the game, the Fusion HUD names that instead — the X11 HUD shows the
                    // spoof already, because there the wrapper renames the Vulkan device itself.
                    if (fusionHud != null) fusionHud.setGpuSpoofName(deliveredGpuSpoofName);
                    // Respect the master toggle, like the _MESA_DRV binding does.
                    if (!hudCounterEnabled) return;
                    if (perfHud != null) perfHud.setVisibility(View.VISIBLE);
                    if (gameNativeHud != null) gameNativeHud.setVisibility(View.VISIBLE);
                    if (fusionHud != null) fusionHud.setVisibility(View.VISIBLE);
                    if (fpsHudHorizontal) {
                        if (frameRatingHorizontal != null) frameRatingHorizontal.setVisibility(View.VISIBLE);
                    } else {
                        if (frameRating != null) frameRating.setVisibility(View.VISIBLE);
                    }
                });
            }
            @Override public void onGameFrame() {
                // Compositor (Wayland dispatch) thread: every client is stalled while this runs, so it
                // only counts the frame. The HUD's own refresh (FrameRating/PerfHudView.update: sysfs
                // temperature/GPU-load reads and BatteryManager binder calls, every 500 ms) runs on the
                // sampler thread; one job is queued at a time, so a fast game can't pile them up.
                if (frameRatingWindowId == -1 || !hudCounterEnabled) return;
                fpsCounter.tick();
                android.os.Handler h = waylandHudSampler;
                if (h != null && waylandHudSampleQueued.compareAndSet(false, true)) h.post(waylandHudSample);
            }
            @Override public void onGameProgram(int pid, String program) {
                // X11 arms the launch-time CPU affinity from window events (onMapWindow / _NET_WM_PID);
                // a Wayland session has none, so the game's first presented frame arms it instead.
                runOnUiThread(() -> assignWaylandTaskAffinity(pid, program));
            }
        });
        if (waylandHudThread == null) {
            waylandHudThread = new android.os.HandlerThread("wayland-hud-sampler");
            waylandHudThread.start();
            waylandHudSampler = new android.os.Handler(waylandHudThread.getLooper());
        }

        // Relative-mode mouse input (Relative Mouse chip, captured mouse, stick-as-mouse) goes to the
        // compositor as deltas instead of the guest-side mouse_event: a program's pointer lock gets it
        // as relative motion; unlocked, the compositor moves its pointer by the delta.
        if (winHandler != null) winHandler.setWaylandMouseRouting(true);
        // Pointer lock (zwp_pointer_constraints_v1): while a program holds one the input path
        // delivers deltas exactly like Relative Mouse (and captures a physical mouse); when it
        // ends, the X pointer (the absolute input's source) is re-synced to where the compositor's
        // pointer ended up (a SetCursorPos warp, typically), so absolute input resumes from there.
        // The compositor focused a window by itself (new window / the focused one closed): have
        // winhandler.exe bring it to the front inside Wine, as DesktopHelper does on every X11 map,
        // and once more a second later (idempotent) unless a newer window asked meanwhile.
        com.winhub.hernandez.wayland.WaylandCompositor.setBringToFrontListener((exe, hwnd) -> {
            final int seq = ++waylandBringToFrontSeq;
            Log.i("XServerDisplayActivity", "wayland: bring to front: " + exe + " hwnd 0x" + Long.toHexString(hwnd));
            WinHandler wh = winHandler;
            if (wh == null) { Log.w("XServerDisplayActivity", "wayland: bring to front: no WinHandler"); return; }
            wh.bringToFront(exe, hwnd);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                WinHandler w = winHandler;
                if (w != null && seq == waylandBringToFrontSeq) w.bringToFront(exe, hwnd);
            }, 1000L);
        });
        // A lock that ends within WAYLAND_WARP_MAX_MS is winewayland's SetCursorPos (lock, position
        // hint, commit, unlock): the game moved the pointer, nothing was locked for the player. Those
        // only re-sync the pointer; a lock still held after that is a real one (mouse-look with a
        // hidden cursor) and switches the input path to deltas. Before, every warp also asked for
        // pointer capture, released it and showed the arrow again.
        com.winhub.hernandez.wayland.WaylandCompositor.setPointerLockListener((locked, x, y) ->
                runOnUiThread(() -> onWaylandPointerLock(locked, x, y)));
        // On-screen controls, a mouse and keys mapped to controller buttons all inject into the X
        // server, which has no client in wayland mode: hand that input to the compositor too.
        if (xServer != null) xServer.setInputSink(new com.winhub.hernandez.xserver.XServer.InputSink() {
            @Override public void onPointerMove(int x, int y) {
                com.winhub.hernandez.wayland.WaylandCompositor.nativeSendSceneInput(2, x, y);
                runOnUiThread(() -> {
                    if (waylandSurfaceView == null || waylandCursorView == null) return;
                    int vw = waylandSurfaceView.getWidth(), vh = waylandSurfaceView.getHeight();
                    if (vw <= 0 || vh <= 0) return;
                    float[] pos = waylandSceneToView(x, y, vw, vh);
                    waylandCursorX = pos[0];
                    waylandCursorY = pos[1];
                    waylandCursorView.setX(waylandCursorX - waylandCursorHotX);
                    waylandCursorView.setY(waylandCursorY - waylandCursorHotY);
                    waylandCursorPoke();
                });
            }
            @Override public void onPointerButton(com.winhub.hernandez.xserver.Pointer.Button button, boolean pressed) {
                switch (button) {
                    case BUTTON_LEFT: com.winhub.hernandez.wayland.WaylandCompositor.nativeSendSceneInput(3, 0x110, pressed ? 1 : 0); break;
                    case BUTTON_RIGHT: com.winhub.hernandez.wayland.WaylandCompositor.nativeSendSceneInput(3, 0x111, pressed ? 1 : 0); break;
                    case BUTTON_MIDDLE: com.winhub.hernandez.wayland.WaylandCompositor.nativeSendSceneInput(3, 0x112, pressed ? 1 : 0); break;
                    case BUTTON_SCROLL_UP: if (pressed) com.winhub.hernandez.wayland.WaylandCompositor.nativeSendSceneInput(4, -1, 0); break;
                    case BUTTON_SCROLL_DOWN: if (pressed) com.winhub.hernandez.wayland.WaylandCompositor.nativeSendSceneInput(4, 1, 0); break;
                    default: break;
                }
            }
            @Override public void onKey(int evdev, boolean pressed) {
                if (evdev > 0) com.winhub.hernandez.wayland.WaylandCompositor.nativeSendKey(evdev, pressed ? 1 : 0);
            }
        });
        // Advertise the panel's real refresh rate (X11 offers it through RandR; games pick their
        // saved 144 Hz mode from Wine's mode list, which Wine derives from the Wayland output).
        try {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetOutputRefreshRate(currentDisplayRefreshHz());
            // And the container's screen size: on X11 the X server's screen is the container size, so
            // Wine lists display modes up to it; a 1920x1080 output listed modes above the desktop.
            if (xServer != null)
                com.winhub.hernandez.wayland.WaylandCompositor.nativeSetOutputSize(
                        xServer.screenInfo.width, xServer.screenInfo.height);
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "wayland: refresh rate unavailable", t);
        }
        // The SurfaceView can host the soft keyboard's InputConnection (text input for the guest).
        com.winhub.hernandez.wayland.WaylandTextInput.SurfaceInputView waylandInputView =
                new com.winhub.hernandez.wayland.WaylandTextInput.SurfaceInputView(this);
        waylandSurfaceView = waylandInputView;
        waylandSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Touch -> wl_pointer. Map view pixels to the compositor's 1920x1080 output space (we blit
        // the guest surface fullscreen, so that IS the guest coordinate space). action 0=down/1=move/2=up.
        // Touchpad-style cursor: a visible on-screen pointer that moves RELATIVE to finger drag
        // (from wherever it is, not jumping to the touch point), with tap = left-click. The cursor is
        // an Android overlay view (waylandCursorView); we keep it in sync with the wl_pointer.motion
        // we send, so the guest's pointer and the visible arrow always match.
        waylandCursorView = new android.widget.ImageView(this);
        waylandCursorView.setImageBitmap(makeArrowCursorBitmap());
        waylandCursorView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        waylandCursorView.setVisibility(View.GONE);
        final float[] last = {0f, 0f};
        final float[] moved = {0f};
        final float SENS = 1.4f;
        waylandSurfaceView.setOnTouchListener((v, ev) -> {
            int vw = v.getWidth(), vh = v.getHeight();
            if (vw <= 0 || vh <= 0) return true;
            // Touchscreen mode (the same "touchscreen_toggle" X11 uses): every finger goes to the
            // guest as a real wl_touch sequence with its own id, so a game gets multi-touch instead
            // of one synthesised mouse. The touchpad cursor is not used in this mode.
            if (waylandTouchscreenMode()) {
                int act = ev.getActionMasked();
                if (waylandCursorView != null && waylandCursorView.getVisibility() != View.GONE)
                    waylandCursorView.setVisibility(View.GONE);
                switch (act) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        // A first finger starts a new gesture, and Android has ended every earlier
                        // one: whatever the compositor still holds (an up lost to the drawer opening
                        // mid-touch) goes first, or every later touch lands on a stale sequence.
                        com.winhub.hernandez.wayland.WaylandCompositor.sendTouch(
                                com.winhub.hernandez.wayland.WaylandCompositor.TOUCH_CANCEL, 0, 0, 0);
                        // fall through
                    case android.view.MotionEvent.ACTION_POINTER_DOWN: {
                        int i = ev.getActionIndex();
                        waylandSendFinger(com.winhub.hernandez.wayland.WaylandCompositor.TOUCH_DOWN,
                                ev.getPointerId(i), ev.getX(i), ev.getY(i), vw, vh);
                        break;
                    }
                    case android.view.MotionEvent.ACTION_MOVE:
                        for (int i = 0; i < ev.getPointerCount(); i++)
                            waylandSendFinger(com.winhub.hernandez.wayland.WaylandCompositor.TOUCH_MOVE,
                                    ev.getPointerId(i), ev.getX(i), ev.getY(i), vw, vh);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_POINTER_UP: {
                        int i = ev.getActionIndex();
                        waylandSendFinger(com.winhub.hernandez.wayland.WaylandCompositor.TOUCH_UP,
                                ev.getPointerId(i), ev.getX(i), ev.getY(i), vw, vh);
                        break;
                    }
                    case android.view.MotionEvent.ACTION_CANCEL:
                        com.winhub.hernandez.wayland.WaylandCompositor.sendTouch(
                                com.winhub.hernandez.wayland.WaylandCompositor.TOUCH_CANCEL, 0, 0, 0);
                        break;
                }
                return true;
            }
            if (waylandCursorX < 0) { waylandCursorX = vw / 2f; waylandCursorY = vh / 2f; }
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    last[0] = ev.getX(); last[1] = ev.getY(); moved[0] = 0f;
                    break;
                case android.view.MotionEvent.ACTION_MOVE: {
                    float dx = (ev.getX() - last[0]) * SENS, dy = (ev.getY() - last[1]) * SENS;
                    last[0] = ev.getX(); last[1] = ev.getY();
                    moved[0] += Math.abs(dx) + Math.abs(dy);
                    waylandCursorX = Math.max(0f, Math.min(vw, waylandCursorX + dx));
                    waylandCursorY = Math.max(0f, Math.min(vh, waylandCursorY + dy));
                    updateWaylandCursor(vw, vh, 1); // motion
                    break;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (moved[0] < 14f) { // a tap (not a drag) -> left click at the cursor
                        updateWaylandCursor(vw, vh, 0); // button press
                        updateWaylandCursor(vw, vh, 2); // button release
                    }
                    break;
            }
            return true;
        });
        // Dedicated runtime dir for the wayland socket. NOT imagefs/tmp — setupXEnvironment does
        // FileUtils.clear(imagefs/tmp), which races the compositor's async socket creation and
        // deletes wayland-0. filesDir/.wayland-rt is app-private, never cleared, and reachable by
        // the guest (full /data paths, no chroot). GuestProgramLauncherComponent uses the same path.
        File waylandRtDir = new File(getFilesDir(), ".wayland-rt");
        waylandRtDir.mkdirs();
        // Extract the xkb keymap so the compositor can send it to wl_keyboard clients (guest needs
        // it to interpret our evdev key codes). Same dir as the socket = the compositor's XDG_RUNTIME_DIR.
        try { FileUtils.copy(this, "wayland/keymap.xkb", new File(waylandRtDir, "keymap.xkb")); }
        catch (Exception e) { Log.e("XServerDisplayActivity", "wayland: keymap extract failed", e); }
        final String xdgRuntimeDir = waylandRtDir.getPath();
        // Resolve the Turnip driver (adrenotools) so the compositor can import dmabufs — honoring a
        // per-game shortcut override exactly like the guest does (the guest's ADRENOTOOLS_DRIVER_PATH
        // is set from the same resolved value). Using container.getGraphicsDriverConfig() unconditionally
        // grabbed the wrong driver on a shortcut launch (empty libraryName -> adrenotools load fails ->
        // system libvulkan -> no dmabuf exts -> vkCreateDevice fails -> black screen).
        String driverPath = null, libraryName = null;
        try {
            String gdc = (shortcut != null)
                    ? shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig())
                    : container.getGraphicsDriverConfig();
            String driverId = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog.getVersion(gdc);
            if (com.winhub.hernandez.core.WaylandAdapter.isProprietaryBlob(this, driverId)) {
                // The Qualcomm blob (v819) has no dma-buf import either: same black screen as System
                // (device-seen 2026-09-25). The pick still stands for the game's side; the compositor
                // takes the bundled Turnip below instead.
                Log.w("XServerDisplayActivity", "wayland: " + driverId + " is the proprietary Qualcomm driver, "
                        + "which cannot import the game's frames; the compositor uses a bundled Turnip");
            } else if (driverId != null && !driverId.isEmpty() && !driverId.equals("System")) {
                com.winhub.hernandez.contents.AdrenotoolsManager atm =
                        new com.winhub.hernandez.contents.AdrenotoolsManager(this);
                driverPath = atm.getDriverPath(driverId);
                libraryName = atm.getLibraryName(driverId);
            }
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "wayland: driver resolve failed", e);
        }
        // No usable driver here and the compositor cannot import the session's frames at all: the
        // system Vulkan has no dma-buf extensions, vkCreateDevice fails and the user gets a black
        // screen with nothing said. It is the state a container carries when nobody ever picked a
        // driver, or when the one it names was removed. Rather than start that session, take the
        // bundled Turnip this GPU supports (NOT the first entry
        // of the picker's list: that is v819, the proprietary blob, which has no dma-buf
        // extensions either and gave a fresh install the very same black screen, device-seen
        // 2026-09-22) so a clean install boots. (The problem is WinNative's c01a89f0; it
        // downloads a driver, we already ship several.)
        if (libraryName == null || libraryName.isEmpty()) {
            try {
                com.winhub.hernandez.contents.AdrenotoolsManager atm =
                        new com.winhub.hernandez.contents.AdrenotoolsManager(this);
                String candidate = null;
                for (String id : getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries)) {
                    if (id == null || id.isEmpty() || id.equals("System")) continue;
                    if (!com.winhub.hernandez.core.GPUInformation.isDriverSupported(id, this)) continue;
                    if (id.toLowerCase().contains("turnip")) { candidate = id; break; }
                    if (candidate == null) candidate = id;
                }
                String lib = candidate != null ? atm.getLibraryName(candidate) : null;
                if (lib != null && !lib.isEmpty()) {
                    driverPath = atm.getDriverPath(candidate);
                    libraryName = lib;
                    Log.w("XServerDisplayActivity", "wayland: no usable driver was set; falling back to bundled "
                            + candidate + " so the session is not black");
                }
            } catch (Exception e) {
                Log.e("XServerDisplayActivity", "wayland: bundled driver fallback failed", e);
            }
        }
        final String fDriverPath = driverPath, fLibraryName = libraryName;
        final String nativeLibDir = getApplicationInfo().nativeLibraryDir;
        // Experimental layer mode (ZERO_COPY_SPIKE.md): BANNER_WAYLAND_ZERO_COPY=1 in the container's
        // (or the shortcut's) environment variables presents a fullscreen game on its own Android
        // layer. Read here, before the compositor starts; the guest half of the same switch
        // (BANNER_WSI_AHB=1) is exported in setupXEnvironment.
        try {
            String raw = container.getEnvVars();
            if (shortcut != null) {
                String sv = shortcut.getExtra("envVars", "");
                if (sv != null && !sv.isEmpty()) raw = (raw == null ? "" : raw + " ") + sv;
            }
            EnvVars env = raw != null && !raw.isEmpty() ? new EnvVars(raw) : null;
            boolean zeroCopy = isWaylandZeroCopyRequested();
            // HDR10 output (opt-in: the game's / container's HDR output setting, BANNER_WAYLAND_HDR overrides
            // it): the compositor decides the gate when it starts, from this request plus the display the
            // game is on. The best path is the game's own 10-bit frames straight on its display layer, so
            // when HDR can be on, zero-copy presentation is turned on for this session too (whatever needs
            // the compositor - effects, windows, frame generation - gets the composed HDR picture). Without
            // the setting, or on a display without HDR10, nothing here changes anything.
            waylandHdrMode = resolvedWaylandHdrMode();
            com.winhub.hernandez.display.DisplayHdrInfo hdrDisp =
                    com.winhub.hernandez.display.DisplayHdrInfo.read(hdrTargetDisplay());
            boolean hdrPossible = waylandHdrMode == com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_FORCE
                    || (waylandHdrMode == com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_ON && hdrDisp.supportsHdr10);
            waylandHdrActive = hdrPossible;
            waylandHdrDisplay = hdrDisp;
            // SDR content composed into an HDR picture (a window over the game, the desktop around a
            // windowed game) is placed at this many nits; BT.2408's 203 unless the user says otherwise.
            if (hdrPossible && env != null && env.has("BANNER_WAYLAND_HDR_SDR_NITS")) {
                try {
                    com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHdrSdrWhite(
                            Float.parseFloat(env.get("BANNER_WAYLAND_HDR_SDR_NITS").trim()));
                } catch (NumberFormatException ignored) {}
            }
            waylandHdrZeroCopyForced = hdrPossible && !zeroCopy;
            if (waylandHdrZeroCopyForced) {
                zeroCopy = true;
                XServerDrawerState.INSTANCE.setWaylandZeroCopyRequested(true);
                Log.i("XServerDisplayActivity", "wayland: HDR output requested on an HDR10 display - zero-copy on for this session");
            }
            pushWaylandHdrDisplay(hdrDisp);
            // DXVK_HDR=1 reaches the game when the user set it, or when HDR is on and the user did not
            // set it at all (setupXEnvironment exports it then; an explicit DXVK_HDR=0 stays 0).
            boolean dxvkHdrInSession = isDxvkHdrEnvOn() || (hdrPossible && (env == null || !env.has("DXVK_HDR")));
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHdrRequest(waylandHdrMode, waylandHdrSource(),
                    dxvkHdrInSession, waylandHdrZeroCopyForced);
            if (waylandHdrMode != com.winhub.hernandez.wayland.WaylandCompositor.HDR_MODE_OFF)
                Log.i("XServerDisplayActivity", "wayland: BANNER_WAYLAND_HDR mode " + waylandHdrMode + " on \""
                        + hdrDisp.displayName + "\" (HDR types " + hdrDisp.formats + ", HDR10 " + hdrDisp.supportsHdr10 + ")");
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetZeroCopy(zeroCopy);
            XServerDrawerState.INSTANCE.setWaylandZeroCopyActive(zeroCopy);
            if (zeroCopy) Log.i("XServerDisplayActivity", "wayland: zero-copy layer mode requested");
            // The Wayland adapter settings (core.WaylandAdapterSettings): the three switches below and the
            // zero-copy flag above come from ONE resolution - an env var the user typed (BANNER_WAYLAND_UBWC=0
            // and friends, as before) > the shortcut's keys > the container's keys > the profile. The guest
            // half of the same answer is written in setupXEnvironment. Logged once per launch here.
            com.winhub.hernandez.core.WaylandAdapterSettings.Effective adapterCfg = waylandAdapterEffective();
            Log.i("XServerDisplayActivity", "wayland: adapter settings " + adapterCfg.describe()
                    + " -> " + adapterCfg.summary(null));
            // Compressed (UBWC) game buffers, default on; off forces the linear-only advertisement.
            boolean ubwc = !Boolean.FALSE.equals(adapterCfg.ubwc);
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetUbwc(ubwc);
            if (!ubwc) Log.i("XServerDisplayActivity", "wayland: compressed (UBWC) game buffers disabled");
            // The compositor's asynchronous copy path (frames in flight; the compositor thread no longer
            // waits for its own screen blit), default on; off restores the synchronous path.
            boolean asyncCopy = !Boolean.FALSE.equals(adapterCfg.asyncCopy);
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetAsyncCopy(asyncCopy);
            if (!asyncCopy) Log.i("XServerDisplayActivity", "wayland: asynchronous copy path disabled");
            // Zero-copy acquire fences the game's driver attaches itself (banner_ahb_v1 version 3), default on;
            // off makes the compositor ignore them.
            boolean clientFence = !Boolean.FALSE.equals(adapterCfg.clientFence);
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetZeroCopyClientFence(clientFence);
            if (!clientFence) Log.i("XServerDisplayActivity", "wayland: zero-copy client render fences ignored");
            // Windows the compositor focuses by itself are made Wine's foreground window: by default
            // through winhandler.exe (the X11 path's DesktopHelper does the same on every map), "click"
            // = one synthetic click instead, 0/false/off = keyboard focus only.
            String aa = env != null ? env.get("BANNER_WAYLAND_AUTO_ACTIVATE") : null;
            int autoActivate = com.winhub.hernandez.wayland.WaylandCompositor.AUTO_ACTIVATE_BRING_TO_FRONT;
            if (aa != null && (aa.equals("0") || aa.equalsIgnoreCase("false") || aa.equalsIgnoreCase("off")))
                autoActivate = com.winhub.hernandez.wayland.WaylandCompositor.AUTO_ACTIVATE_OFF;
            else if (aa != null && aa.equalsIgnoreCase("click"))
                autoActivate = com.winhub.hernandez.wayland.WaylandCompositor.AUTO_ACTIVATE_CLICK;
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetAutoActivate(autoActivate);
            if (aa != null)
                Log.i("XServerDisplayActivity", "wayland: BANNER_WAYLAND_AUTO_ACTIVATE=" + aa + " -> mode " + autoActivate);
            // Debug: BANNER_WAYLAND_NO_RENDER_NODE=1 makes the compositor name no DRM device in its
            // dma-buf feedback (main device 0:0), which is what a phone that exposes no /dev/dri
            // node to apps sends. Reproduces those phones' OpenGL path on a device that has one.
            String nrn = env != null ? env.get("BANNER_WAYLAND_NO_RENDER_NODE") : null;
            boolean noRenderNode = nrn != null && (nrn.equals("1") || nrn.equalsIgnoreCase("true") || nrn.equalsIgnoreCase("on"));
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetNoRenderNode(noRenderNode);
            if (noRenderNode) Log.i("XServerDisplayActivity", "wayland: BANNER_WAYLAND_NO_RENDER_NODE - advertising no DRM device");
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "wayland: zero-copy flag read failed", e);
        }
        waylandSurfaceView.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
            boolean started = false;
            @Override public void surfaceCreated(android.view.SurfaceHolder h) {
                if (!started) {
                    started = true;
                    setWaylandSessionLogDir();
                    com.winhub.hernandez.wayland.WaylandCompositor.nativeStartWithSurface(
                            h.getSurface(), xdgRuntimeDir, fDriverPath, fLibraryName, nativeLibDir);
                    startWaylandVsync();
                } else {
                    com.winhub.hernandez.wayland.WaylandCompositor.nativeSetSurface(h.getSurface());
                }
                // A fresh surface comes up with no frame-rate vote: re-assert the one VRR last
                // routed, the way XServerView.reassertFrameRate does for the X11 backend.
                applySurfaceFrameRate(waylandSurfaceView, vrrVote);
            }
            @Override public void surfaceChanged(android.view.SurfaceHolder h, int f, int w, int ht) {}
            @Override public void surfaceDestroyed(android.view.SurfaceHolder h) {
                com.winhub.hernandez.wayland.WaylandCompositor.nativeSetSurface(null);
            }
        });
        rootView.addView(waylandSurfaceView);
        rootView.addView(waylandCursorView); // the overlay pointer, on top of the compositor surface
        // Clipboard both ways and the soft keyboard's text; the compositor queues anything sent
        // before its thread is up.
        waylandClipboard = new com.winhub.hernandez.wayland.WaylandClipboardSync(this);
        waylandClipboard.start();
        waylandTextInput = new com.winhub.hernandez.wayland.WaylandTextInput(this, waylandInputView);
        waylandTextInput.start();
    }

    /** Scene (virtual desktop) pixel -> view pixel through the fullscreen mode + alignment, with the
     *  same ViewTransformation the touch map and the compositor use, so the overlay arrow sits on the
     *  desktop pixel the compositor draws there (letterbox bars, FILL crop, TOP/BOTTOM half). */
    // Bumped per compositor bring-to-front request, so a delayed repeat never re-raises an older window.
    private volatile int waylandBringToFrontSeq = 0;

    private static final long WAYLAND_WARP_MAX_MS = 120L;
    private boolean waylandLockPending;              // a lock that may still turn out to be a warp
    private final Runnable waylandLockApply = () -> {
        if (!waylandLockPending) return;
        waylandLockPending = false;
        applyWaylandPointerLock(true, 0, 0);
    };

    /** The compositor's lock state changed. Main thread. */
    private void onWaylandPointerLock(boolean locked, int x, int y) {
        if (xServer == null) return;
        if (locked) {
            waylandLockPending = true;
            waylandCursorIdle.removeCallbacks(waylandLockApply);
            waylandCursorIdle.postDelayed(waylandLockApply, WAYLAND_WARP_MAX_MS);
            return;
        }
        if (waylandLockPending) {
            waylandLockPending = false;
            waylandCursorIdle.removeCallbacks(waylandLockApply);
            xServer.injectPointerMove(x, y);
            waylandMoveCursorView(x, y);
            return;
        }
        applyWaylandPointerLock(false, x, y);
    }

    private void applyWaylandPointerLock(boolean locked, int x, int y) {
        waylandPointerLocked = locked;
        xServer.setExternalRelativeMode(locked);
        if (locked) {
            if (waylandCursorView != null) waylandCursorView.setVisibility(View.GONE);
            ensurePointerCapture("wayland-pointer-lock");
        } else {
            xServer.injectPointerMove(x, y);
            if (!isRelativeMouseMovement && !cursorLock && touchpadView != null && pointerCaptureRequested) {
                touchpadView.releasePointerCapture();
                touchpadView.setOnCapturedPointerListener(null);
                pointerCaptureRequested = false;
            }
        }
    }

    /** The overlay arrow to scene x,y without showing it (the game moved the pointer, not the player). */
    private void waylandMoveCursorView(int x, int y) {
        if (waylandSurfaceView == null || waylandCursorView == null) return;
        int vw = waylandSurfaceView.getWidth(), vh = waylandSurfaceView.getHeight();
        if (vw <= 0 || vh <= 0) return;
        float[] pos = waylandSceneToView(x, y, vw, vh);
        waylandCursorX = pos[0];
        waylandCursorY = pos[1];
        waylandCursorView.setX(waylandCursorX - waylandCursorHotX);
        waylandCursorView.setY(waylandCursorY - waylandCursorHotY);
    }

    /**
     * The Wayland session log goes into this game's log folder, wayland/ (one file per run, the same
     * keep-last as previous/). Written whatever the Wine debug switch says, as before; if the folder
     * can't be made the compositor keeps Download/Wayland-logs.
     */
    private void setWaylandSessionLogDir() {
        try {
            java.io.File dir = com.winhub.hernandez.core.LogLocation.resolveWaylandLogDir(this, currentLogGameName());
            if (dir != null) com.winhub.hernandez.core.LogRotation.pruneWaylandLogs(
                    dir, com.winhub.hernandez.core.LogLocation.keepLastRuns(this));
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetSessionLogDir(dir != null ? dir.getAbsolutePath() : null);
            Log.i("XServerDisplayActivity", "wayland: session log folder " + (dir != null ? dir : "Download/Wayland-logs"));
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "wayland: session log folder not set", t);
        }
    }

    private float[] waylandSceneToView(int x, int y, int vw, int vh) {
        int sw = xServer.screenInfo.width, sh = xServer.screenInfo.height;
        HostRenderer r = xServer.getRenderer();
        int mode = r != null ? r.getFullscreenMode() : Container.FULLSCREEN_FIT;
        int align = r != null ? r.getScreenAlignment() : Container.ALIGN_CENTER;
        com.winhub.hernandez.renderer.ViewTransformation vt = new com.winhub.hernandez.renderer.ViewTransformation();
        vt.update(vw, vh, sw, sh, mode, align);
        if (mode != Container.FULLSCREEN_STRETCH)
            return new float[]{vt.viewOffsetX + x * vt.aspect, vt.viewOffsetY + y * vt.aspect};
        return new float[]{vt.regionOffsetX + (float) x * vt.regionWidth / sw,
                           vt.regionOffsetY + (float) y * vt.regionHeight / sh};
    }

    /** Move the overlay pointer to the current touchpad position and send the guest a wl_pointer
     *  event mapped to the 1920x1080 output space. action: 0=press, 1=motion, 2=release. */
    // The compositor draws once per screen refresh: feed it the Choreographer's vsync ticks for as
    // long as this activity lives (the tick is a cheap JNI call; the compositor ignores it while
    // it has nothing new to draw or no window).
    private boolean waylandVsyncRunning = false;
    private final android.view.Choreographer.FrameCallback waylandVsyncCallback = new android.view.Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!waylandVsyncRunning) return;
            com.winhub.hernandez.wayland.WaylandCompositor.nativeVsync(frameTimeNanos);
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void startWaylandVsync() {
        if (waylandVsyncRunning) return;
        waylandVsyncRunning = true;
        android.view.Choreographer.getInstance().postFrameCallback(waylandVsyncCallback);
    }

    /** Touchscreen mode: fingers go to the guest as wl_touch. Shared with X11's setting. */
    private boolean waylandTouchscreenMode() {
        SharedPreferences sp = preferences != null ? preferences
                : PreferenceManager.getDefaultSharedPreferences(this);
        return sp != null && sp.getBoolean("touchscreen_toggle", false);
    }

    /** One finger to the compositor, view pixels -> output space (the same mapping the pointer uses). */
    private void waylandSendFinger(int action, int id, float x, float y, int vw, int vh) {
        int ox = (int) (Math.max(0f, Math.min(vw, x)) / vw * 1920f);
        int oy = (int) (Math.max(0f, Math.min(vh, y)) / vh * 1080f);
        com.winhub.hernandez.wayland.WaylandCompositor.sendTouch(action, id, ox, oy);
    }

    private void updateWaylandCursor(int vw, int vh, int action) {
        if (waylandCursorView != null) {
            waylandCursorView.setX(waylandCursorX - waylandCursorHotX);
            waylandCursorView.setY(waylandCursorY - waylandCursorHotY);
            waylandCursorPoke();
        }
        int ox = (int) (waylandCursorX / vw * 1920f);
        int oy = (int) (waylandCursorY / vh * 1080f);
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSendPointer(action, ox, oy);
    }

    /** A small classic arrow cursor bitmap (white fill, dark outline) drawn in code. */
    private android.graphics.Bitmap makeArrowCursorBitmap() {
        int w = 22, h = 34;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(1, 1); p.lineTo(1, 25); p.lineTo(7, 19); p.lineTo(11, 28);
        p.lineTo(15, 26); p.lineTo(11, 17); p.lineTo(19, 17); p.close();
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setColor(0xFFFFFFFF);
        cv.drawPath(p, paint);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFF202020);
        cv.drawPath(p, paint);
        return bmp;
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        // Set environment variables
        envVars.put("LC_ALL", lc_all);
        envVars.put("WINEPREFIX", imageFs.wineprefix);

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels", SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);
        // With debug off, say "-all" and mean it. The old value was "+err,+warn,+fixme,-all", whose
        // comment claimed errors still surfaced — they did not. Wine parses WINEDEBUG left to right
        // and an unprefixed "-all" clears every class on every channel, so the trailing entry
        // overrode the three before it; those three were also written where Wine expects a CHANNEL
        // name, not the err/warn/fixme classes they were meant to name. So this is what the old
        // string already resolved to, now stated honestly — and with logging off nothing reads
        // Wine's output anyway (see the guarded block below), so it just stops Wine formatting
        // messages that are thrown away.
        //
        // A WINEDEBUG set by the user on the container or shortcut still wins: those env vars are
        // merged further down and overwrite this one.
        String wineDebugValue;
        if (enableWineDebug && !wineDebugChannels.isEmpty()) {
            wineDebugValue = "+" + wineDebugChannels.replace(",", ",+");
        } else {
            wineDebugValue = "-all";
        }
        envVars.put("WINEDEBUG", wineDebugValue);

        // ── Wine debug file log (OPT-IN) ───────────────────────────────────────
        // Writes all Wine stdout/stderr to a readable file, in the user-chosen log folder
        // (Settings › Log Manager, issue #70). Defaults to
        // /sdcard/Android/data/com.winhub.hernandez/files/wine_debug.log; falls back there if the
        // chosen dir is missing/unwritable.
        try {
            // Only when the user actually asked for logs. While no debug callback is registered,
            // ProcessHelper points a process's stdout/stderr at /dev/null and spawns no reader
            // threads at all (ProcessHelper.execGuestProgram) — so registering one unconditionally,
            // as this block used to, quietly defeated every switch in the Log Manager AND made
            // every launch pay for a reader thread pair, a redaction pass and a disk write per line
            // of output, for every user, forever. Turning the switches off has to mean off.
            //
            // Setting WINEDEBUG cannot substitute for this gate: what lands here is the output of
            // everything we spawn — Box64/FEXCore, wineserver, the preloader, DXVK — not just Wine.
            //
            // Gate on the same predicate the launch-failure card uses (Wine debug OR Box64/FEXCore),
            // because both of those write to this one stream: either switch on means we must capture
            // it, both off means nothing below runs — no folder, no rotation, no file, no callback.
            //
            // Per-game folder (Settings › Log Manager). The name comes from the shortcut so the
            // folder is recognisable; a container launched with no shortcut uses the container name.
            // Rotate BEFORE opening the writer: the previous run's files move into previous/<stamp>/
            // and the oldest beyond the keep-count are pruned, so this run starts on a clean slate
            // without destroying the log of a crash the user may still want.
            File logDir = isLaunchLoggingEnabled()
                    ? com.winhub.hernandez.core.LogLocation.resolveGameLogDir(this, currentLogGameName())
                    : null;
            if (logDir != null) {
                logDir.mkdirs();
                // Only rotate inside a folder WE created for this game. With per-game folders off,
                // resolveGameLogDir returns the flat log root — which on the default location holds
                // other subsystems' debug files, and on a custom location is a folder the user
                // chose. Archiving and pruning in there would delete files that aren't ours.
                //
                // Test the folder we ACTUALLY got, not the preference. resolveGameLogDir falls back
                // to that same flat root whenever the per-game folder can't be created or isn't
                // writable, and it does so silently — with the preference still reading "on". Asking
                // the preference therefore answers a different question than the one that matters.
                File flatLogRoot = com.winhub.hernandez.core.LogLocation.resolveLogDir(this);
                if (!logDir.equals(flatLogRoot)) {
                    com.winhub.hernandez.core.LogRotation.rotate(
                            logDir, com.winhub.hernandez.core.LogLocation.keepLastRuns(this));
                }
                File logFile = new File(logDir, "wine_debug.log");
                wineDebugWriter = new java.io.PrintWriter(
                        new java.io.BufferedWriter(new java.io.FileWriter(logFile, false)), true);
                // Header: print context that helps diagnose the crash
                wineDebugWriter.println("=== Wine Debug Log ===");
                wineDebugWriter.println("WINEDEBUG: " + wineDebugValue);
                wineDebugWriter.println("WINEPREFIX: " + imageFs.wineprefix);
                wineDebugWriter.println("Container ID: " + (container != null ? container.id : "null"));
                if (shortcut != null) {
                    wineDebugWriter.println("Shortcut file: " + shortcut.file.getPath());
                    wineDebugWriter.println("Shortcut path (resolved): " + shortcut.path);
                } else {
                    wineDebugWriter.println("Shortcut: null (launching Wine File Manager)");
                }
                // DX wrapper diagnostic
                wineDebugWriter.println("--- DX Wrapper State ---");
                wineDebugWriter.println("dxwrapper type: " + this.dxwrapper);
                wineDebugWriter.println("dxwrapperConfig (raw): " + (container != null ? container.getDXWrapperConfig() : "null"));
                wineDebugWriter.println("vkd3dVersion (parsed): " + dxwrapperConfig.get("vkd3dVersion"));
                wineDebugWriter.println("dxvk version (parsed): " + dxwrapperConfig.get("version"));
                wineDebugWriter.println("ddrawrapper (parsed): " + dxwrapperConfig.get("ddrawrapper"));
                String cachedDxwrapper = (container != null ? container.getExtra("dxwrapper") : "none");
                wineDebugWriter.println("cached dxwrapper extra: " + cachedDxwrapper);
                if (this.dxwrapper.contains("dxvk")) {
                    String expectedDxvkWrapper = "dxvk-" + dxwrapperConfig.get("version");
                    String expectedVkd3dWrapper = "vkd3d-" + dxwrapperConfig.get("vkd3dVersion");
                    String expectedDdra = dxwrapperConfig.get("ddrawrapper");
                    String expectedFull = expectedDxvkWrapper + ";" + expectedVkd3dWrapper + ";" + expectedDdra + d7vkMarker(expectedDdra);
                    wineDebugWriter.println("expected full string: " + expectedFull);
                    wineDebugWriter.println("extraction will run: " + (!expectedFull.equals(cachedDxwrapper)));
                }
                wineDebugWriter.println("--- End DX Wrapper State ---");
                wineDebugWriter.println("=== Wine output below ===");
                // Redact HERE, not when a report is built. This file lands somewhere the user can
                // reach and share directly (issue #70's whole point), so it has to be safe on disk
                // and not merely safe when it happens to leave via the Report button. The redactor
                // short-circuits on a single cheap scan for lines no pattern could match, which is
                // almost all Wine output, so this stays affordable on a per-line callback.
                wineDebugLogCallback = line -> {
                    if (wineDebugWriter != null) {
                        wineDebugWriter.println(com.winhub.hernandez.core.LogcatCapture.redact(line));
                    }
                };
                ProcessHelper.addDebugCallback(wineDebugLogCallback);
                Log.d("WineDebug", "Wine debug log → " + logFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e("WineDebug", "Failed to open wine debug log file", e);
        }

        // Clear any temporary directory
        String rootPath = imageFs.getRootDir().getPath();
        FileUtils.clear(imageFs.getTmpDir());

        // The tmp clear above just wiped the private hudapi wrapper-log dir (a subdir of tmp) if the
        // "DXVK & VKD3D" switch is off. Re-create it now, before the guest starts, so DXVK/VKD3D can
        // write their startup logs there for the HUD API resolver (P3). No-op when logging is on
        // (wrapperLogDir then points at the user's log dir, outside tmp) or on a WineD3D container (null).
        if (wrapperLogDir != null) wrapperLogDir.mkdirs();

        guestProgramLauncherComponent = new GuestProgramLauncherComponent(
                contentsManager,
                contentsManager.getProfileByEntryName(container.getWineVersion()),
                shortcut
        );

        // Additional container checks and environment configuration
        if (container != null) {
            if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_AGGRESSIVE) {
                // winHandler.killProcess("services.exe"); 
            }
            guestProgramLauncherComponent.setContainer(this.container);
            guestProgramLauncherComponent.setWineInfo(this.wineInfo);
            guestProgramLauncherComponent.setWaylandMode(waylandMode);

            // Real-Steam (VAC) launch (feature M3) — STAGE + build the plan BEFORE getWineStartCommand()
            // below reads realSteamPlan to rewrite the launch target. ONLY for a genuine-Steam shortcut
            // explicitly set to launchMode=RealSteam: stage the SteamLite client + our agent into this
            // container's prefix, register the game under steamapps\common\<canonical> (secure LaunchApp),
            // and write the per-game spec. We're on the launch WORKER thread, so the file staging + Room
            // lookup (resolveSteamAppRef) can block safely. The env it produces is merged AFTER the
            // container/shortcut env below (so RealSteam wins); the plan is read by getWineStartCommand().
            // On any missing prerequisite (no token / appId / install dir / SteamLite not downloaded)
            // prepare() returns null and realSteamPlan stays null → the NORMAL launch is byte-for-byte
            // unchanged. The refresh token lives ONLY inside the returned env map; nothing here logs it.
            maybeStageRealSteam();
            // Tell the drawer's Friends tab which source to wait for: the agent relay when the plan is
            // armed, else the app's own session (a fallback to the normal launch never pauses it).
            try { com.winhub.hernandez.store.InGameFriendsSource.INSTANCE.setRealSteamLaunch(realSteamPlan != null); }
            catch (Throwable ignored) {}

            String guestExecutable = "wine explorer /desktop=shell," + xServer.screenInfo + " " + getWineStartCommand();

            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());

            // Frame-gen engine: lsfg-vk or bionic-fg (mutually exclusive), or off. Honors per-game
            // overrides (else the container's value), matching the drawer sync above.
            // NOTE: the FPS limiter is no longer wired here — it's a standalone host-side pacer
            // applied to the renderer (see renderer.setFpsLimit below), independent of frame gen.
            //
            // Runtime safety gate: FG's mailbox/present-mode delivery only exists on the Vulkan host
            // renderer, so loading the FG layer on any other renderer is inert/broken. The editors
            // now forbid FG unless the renderer is Vulkan; honor the same rule here so a stale
            // "FG on + non-Vulkan renderer" config simply doesn't run FG (no broken layer).
            // Wayland: the compositor is Vulkan by construction, and the two native engines run
            // inside it (framegen_bridge.c); prepareLsfgNative/prepareWinFgNative route there.
            boolean fgRendererVulkan = "vulkan".equalsIgnoreCase(resolvedRenderer()) || waylandMode;
            if (fgRendererVulkan) {
            if (resolvedFrameGenEngine().equals("lsfg-native")) {
                // LSFG Native runs the Lossless Scaling chain inside OUR compositor, on the Android
                // side of the Wine boundary. Nothing is injected into the container: no layer, no
                // conf.toml, no ENABLE_LSFG, and no vsync clock file for a guest layer to phase-lock
                // to - the generator shares our command stream, so it needs none of them.
                //
                // All that happens at launch is building the SPIR-V cache from the user's own
                // Lossless.dll (slow on a first run: the DXBC chain is translated on device, which
                // is why it is off the main thread) and arming the renderer.
                prepareLsfgNative();
            } else if (resolvedFrameGenEngine().equals("lsfg")) {
                // lsfg-vk engine (mutually exclusive with bionic-fg). Opt-in via ENABLE_LSFG so the
                // staged layer stays inert elsewhere. Driven by conf.toml (NOT the LSFG_LEGACY env):
                // the GameNative-fork layer watches the conf.toml mtime in its present hook and forces
                // a swapchain recreate on change, so rewriting the file re-applies multiplier/flow LIVE
                // in-game. It HARD-EXITS if it can't read the Lossless.dll, so only enable when the
                // user-imported copy exists. LSFG_PROCESS must match the conf.toml [[game]].exe (under
                // Wine /proc/self/exe is the loader, so the real exe name is unusable).
                File losslessDll = new File(getFilesDir(), "lsfg-vk/Lossless.dll");
                if (losslessDll.isFile()) {
                    // Start in passthrough (multiplier 1 = frame gen off). ENABLE_LSFG still loads the
                    // layer, so the FG drawer can enable it live in-session (the conf.toml mtime watch
                    // re-applies the user's multiplier without a relaunch). Container value untouched.
                    // EXCEPTION: a container that opted into auto-enable starts LIVE at its saved
                    // multiplier from frame one (GameNative-style), matching the drawer seed above.
                    int lsfgSavedMult = container.getFrameGenMultiplier();
                    int lsfgLaunchMult = (container.isLsfgAutoEnable() && lsfgSavedMult >= 2) ? lsfgSavedMult : 1;
                    writeLsfgConfig(lsfgLaunchMult, container.getFrameGenFlowScale(), losslessDll.getAbsolutePath(), container.isLsfgPerformanceMode());
                    File lsfgConf = new File(imageFs.home_path, ".config/lsfg-vk/conf.toml");
                    envVars.put("ENABLE_LSFG", "1");
                    envVars.put("LSFG_CONFIG", lsfgConf.getAbsolutePath());
                    envVars.put("LSFG_PROCESS", "bannerlator-lsfg");
                    // Publish the display vsync clock so the layer phase-locks its pacing instead of
                    // free-running and over-queuing the host compositor (the black-frame flicker root
                    // cause). Only when this launch actually generates from frame one: a passthrough
                    // launch has no layer pacing to feed, and the toggle path starts the clock the
                    // instant FG is enabled in-game. onDestroy stops it.
                    if (lsfgLaunchMult >= 2) startVsyncClock();
                    // Baseline the FG-reset tracker to the launch level so the first in-game change
                    // fires the presentation reset. Auto-enable containers already generate from frame
                    // one (lsfgLaunchMult >= 2), so a passthrough launch is level 0.
                    lastCommittedFgLevel = (lsfgLaunchMult >= 2) ? lsfgLaunchMult : 0;
                } else {
                    Log.w("XServerDisplayActivity", "lsfg-vk selected but no Lossless.dll imported (Settings) — leaving frame gen off");
                }
            } else {
                // bionic-fg layer: load it only when frame generation is the selected engine. The
                // FPS limiter is handled separately (host pacer), so it no longer forces this layer
                // to load. multiplier=0 -> frame gen starts Off in-game (layer loaded, enable live).
                boolean fgOn = resolvedFrameGenEngine().equals("bionic");
                // Win-FG Native: the win-fg chain runs inside OUR compositor, exactly like
                // LSFG Native, and needs nothing from the user - the shaders are ours and
                // embedded. Otherwise the in-container win-fg layer is used instead.
                winFgNativeSession = computeWinFgNativeSession();
                if (fgOn && winFgNativeSession) {
                    prepareWinFgNative();
                } else if (fgOn) {
                    envVars.put("WIN_FG_ENABLE", "1");
                    writeWinFgConfig(
                            0,
                            container.getFrameGenFlowScale(),
                            false,
                            0,
                            resolvedFrameGenModel(),
                            resolvedFrameGenPerfPreset());
                    // Baseline the win-fg reset trackers to the launch state (frame gen starts Off,
                    // multiplier 0) so the first in-game level/model/preset change fires the reset.
                    lastCommittedWinFgLevel = 0;
                    lastCommittedWinFgModel = resolvedFrameGenModel();
                    lastCommittedWinFgPreset = resolvedFrameGenPerfPreset();
                }
            }
            } else if (!"off".equals(resolvedFrameGenEngine())) {
                // Stale config: FG selected on a non-Vulkan renderer — skip the layer (see note above).
                Log.w("XServerDisplayActivity", "Frame gen (" + resolvedFrameGenEngine()
                        + ") requested but host renderer is " + resolvedRenderer()
                        + " — skipping FG layer (Vulkan required).");
            }

            if (shortcut != null) envVars.putAll(shortcut.getExtra("envVars"));

            applyHermesXInputEnv(envVars);
            applyControllerLogEnv(envVars);
            applyGameModsEnv(envVars);
            // Wayland adapter settings, guest half (core.WaylandAdapterSettings.applyToGuestEnv): the present
            // mode, async copy, zero-copy, client fences, UBWC, DRM modifiers and KGSL timing shim the
            // profile / Advanced switches decided, as the env vars the adapter, Mesa and the compositor
            // read - written AFTER both user env merges and skipping every var the user typed, so an
            // explicit env var always wins. MESA_VK_WSI_PRESENT_MODE from the stored X11 presentMode key
            // (extractGraphicsDriverFiles) is replaced only when a Wayland present mode was decided.
            if (waylandMode) {
                com.winhub.hernandez.core.WaylandAdapterSettings.Effective adapterCfg = waylandAdapterEffective();
                if (adapterCfg != null) com.winhub.hernandez.core.WaylandAdapterSettings.applyToGuestEnv(envVars, adapterCfg);
            }

            // Wayland zero-copy layer mode: the same BANNER_WAYLAND_ZERO_COPY=1 that puts the game on
            // its own Android layer (startWaylandCompositor) also tells our Wayland Turnip's WSI to
            // allocate the game's swapchain images as gralloc buffers and hand them to the compositor
            // (banner_ahb_v1), so the layer shows the game's own buffer without a copy.
            // The HDR opt-in (startWaylandCompositor, which runs first) can turn zero-copy on too.
            if (waylandMode && (isWaylandZeroCopyRequested() || waylandHdrZeroCopyForced)) envVars.put("BANNER_WSI_AHB", "1");
            if (waylandMode) applyWaylandHdrEnv(envVars);
            // On Wayland, the GPU name spoof: after both user env merges and the HDR env above.
            if (waylandMode) applyWaylandGpuSpoofEnv(envVars);

            // OpenGL safe mode (Wayland only, default ON): GALLIUM_THREAD=0 removes Mesa's
            // u_threaded_context helper thread. That thread is a plain pthread with no Wine TEB, so a
            // fault on it makes Wine's SIGSEGV handler fault again and the kernel kills the process
            // with NO tombstone, NO Wine exception and NO log line - the game just disappears
            // (proved on Wizardry: 2 swaps and gone, vs 2862 swaps with the thread off).
            // It only reaches Mesa's gallium drivers, i.e. the OpenGL/Zink path; a DXVK/VKD3D game
            // goes straight to Turnip's Vulkan driver and never loads one, so this is inert for it.
            // Both user env strings (container, then shortcut) are already merged above, so an
            // explicit GALLIUM_THREAD the user typed themselves still wins.
            if (waylandMode) {
                if (!resolvedWaylandGlSafeMode()) {
                    Log.i("XServerDisplayActivity", "wayland: OpenGL safe mode is OFF for this launch"
                            + " - Mesa's threaded context stays on");
                } else if (envVars.has("GALLIUM_THREAD")) {
                    Log.i("XServerDisplayActivity", "wayland: OpenGL safe mode on, but GALLIUM_THREAD="
                            + envVars.get("GALLIUM_THREAD") + " is already set in the environment variables"
                            + " - leaving the user's value alone");
                } else {
                    envVars.put("GALLIUM_THREAD", "0");
                    Log.i("XServerDisplayActivity", "wayland: OpenGL safe mode on - exporting GALLIUM_THREAD=0");
                }
            }

            // Keep the lsfg-vk Vulkan layer INERT unless lsfg-vk is actually the engine.
            // Placed AFTER both user env merges (container above, shortcut just here) so
            // nothing the user carries over can re-enable it. The layer's manifest honours
            // disable_environment over enable_environment, so this wins even when a stale
            // ENABLE_LSFG=1 is still sitting in a container that used lsfg-vk before.
            //
            // Device report that prompted this: r8 tester on Adreno 830, LSFG Native
            // selected, NFS Heat asserting in winevulkan's vkCreateSwapchainKHR thunk on
            // launch. LSFG Native writes nothing into the guest, but a leftover ENABLE_LSFG
            // loads the lsfg-vk layer into the game with no conf.toml, no LSFG_CONFIG and no
            // DLL path - and that layer hard-fails swapchain creation when it cannot find
            // its config. That is precisely the assertion in the screenshot.
            if (!"lsfg".equals(resolvedFrameGenEngine())) {
                envVars.put("DISABLE_LSFG", "1");
                envVars.remove("ENABLE_LSFG");
            }

            // Epic per-game launch fixes (Feature #7). Runs on the background launch setup thread.
            //  • applyEnv: merge required WINEDLLOVERRIDES (Kingdom Hearts III) into the launch env,
            //    after the shortcut env so a user override still wins.
            //  • applyPreLaunch: write the Bethesda "Installed Path" registry value + wipe the
            //    Hogwarts Legacy ProgramData cache folder before the guest process starts.
            // Both no-op for non-Epic shortcuts and Epic games without a registered fix.
            if (shortcut != null) {
                com.winhub.hernandez.store.EpicGameFixes.applyEnv(this, shortcut, envVars);
                com.winhub.hernandez.store.EpicGameFixes.applyPreLaunch(this, shortcut);
            }

            // Xbox store game: the bridge port/key, service socket, save-sync mode and PlayFab token for the
            // game's Xbox runtime modules, and those modules forced native (they sit next to the exe).
            if (xboxLaunch != null) {
                for (java.util.Map.Entry<String, String> e : xboxLaunch.getEnv().entrySet()) envVars.put(e.getKey(), e.getValue());
                String overrides = envVars.has("WINEDLLOVERRIDES") ? envVars.get("WINEDLLOVERRIDES") : "";
                String xbox = xboxLaunch.getOverrides();
                envVars.put("WINEDLLOVERRIDES", overrides.isEmpty() ? xbox : xbox + ";" + overrides);
            }

            // Real-Steam (VAC) launch (feature M3) — ENV injection. The staging + plan was built above by
            // maybeStageRealSteam() (before getWineStartCommand()). Merge the plan's WN_STEAM_* +
            // PROTON_DISABLE_LSTEAMCLIENT env HERE — after the container (5288) and shortcut/Epic env merges
            // — so the RealSteam env always wins. No-op (realSteamPlan == null) for every non-RealSteam or
            // failed-prep launch. The refresh token lives ONLY inside plan.env; nothing here logs it.
            if (realSteamPlan != null) {
                for (Map.Entry<String, String> e : realSteamPlan.env.entrySet())
                    envVars.put(e.getKey(), e.getValue());
                // Crash symbolization aid: with the Log Manager's Wine-debug toggle ON, a RealSteam launch
                // also enables the "seh" channel (exception code/address + the handler/unwind frames)
                // and "loaddll" (one line per module load = the base addresses that turn a faulting
                // address into module+offset) so a game that dies at startup under the genuine client
                // (intermittent c0000005 at kernel32+0x62600 right after LaunchApp) leaves the detail
                // in wine_debug.log; SteamLiteLogCollector quotes those lines under its CRASH entry.
                // Only when the user already opted into Wine debug output (logging cost is theirs
                // already) and only when the channels are a "+..." list (a user WINEDEBUG that named
                // seh, or "-all", is left alone). Non-RealSteam launches are untouched.
                try {
                    if (preferences.getBoolean("enable_wine_debug", false)) {
                        String wd = envVars.has("WINEDEBUG") ? envVars.get("WINEDEBUG") : "";
                        if (wd != null && wd.startsWith("+")) {
                            String add = "";
                            if (!wd.contains("seh")) add += ",+seh";
                            if (!wd.contains("loaddll")) add += ",+loaddll";
                            if (!add.isEmpty()) envVars.put("WINEDEBUG", wd + add);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // Sync (esync / ntsync / wineserver) is no longer defaulted here: applySyncModeEnv() below
            // writes it after every env source (overrideEnvVars included) is merged.

            ArrayList<String> bindingPaths = new ArrayList<>();
            for (String[] drive : container.drivesIterator()) {
                bindingPaths.add(drive[1]);
            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            String box64Preset = shortcut != null
                    ? shortcut.getExtra("box64Preset", container.getBox64Preset())
                    : container.getBox64Preset();
            guestProgramLauncherComponent.setBox64Preset(box64Preset);
            // A preset edited from Edit Container or from this game's own settings keeps its values
            // there rather than in the shared preset, so resolve game -> container -> shared. Null
            // (nothing customised) leaves the launcher on its original preset-manager lookup.
            guestProgramLauncherComponent.setBox64PresetVars(
                    com.winhub.hernandez.core.PresetOverrides.localEffective(
                            this, false, box64Preset, container, shortcut));

            String fexPreset = shortcut != null
                    ? shortcut.getExtra("fexcorePreset", container.getFEXCorePreset())
                    : container.getFEXCorePreset();
            // EA titles: FEX_SMCCHECKS=none (Extreme presets) crashes EA's Activation64 anti-tamper
            // (c0000005 in the game exe, device-proven) → clamp to the Performance twin for this launch.
            if (realSteamEaChain && realSteamPlan != null) {
                String clamped = com.winhub.hernandez.store.EaSupport.clampPresetForEa(fexPreset);
                if (clamped != null && !clamped.equals(fexPreset)) {
                    Log.i("BH_REALSTEAM", "EA launch: FEX preset " + fexPreset + " -> " + clamped + " (SMC checks must stay on)");
                    fexPreset = clamped;
                }
            }
            guestProgramLauncherComponent.setFEXCorePreset(fexPreset);
            // Same three-tier resolve for FEXCore. Deliberately AFTER the EA clamp above, so a
            // clamped launch looks up the values of the preset it was clamped TO — otherwise an
            // Extreme customisation would be re-applied on top of the Performance twin and put
            // FEX_SMCCHECKS=none straight back, which is exactly what the clamp exists to prevent.
            guestProgramLauncherComponent.setFEXCorePresetVars(
                    com.winhub.hernandez.core.PresetOverrides.localEffective(
                            this, true, fexPreset, container, shortcut));
        }

        // Merge overrideEnvVars if present
        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars.clear(); // Clear overrideEnvVars as per smali logic
        }

        // Sync selector: the ONE writer of WINEESYNC / WINENTSYNC (and the remover of WINEFSYNC). After
        // every env merge so a stale value in the container/shortcut env string can't fight it.
        applySyncModeEnv();

        // Fast OpenGL (the bundled Mesa through Wine's EGL backend or winex11's GLX on the X server's
        // server-side GLX, so Zink presents through a Vulkan swapchain instead of XMesa's finish +
        // readback + XPutImage per frame; core.FastOpenGL / X11Egl), plus "Show OpenGL FPS" and
        // "OpenGL vsync off". After every env merge so a value the user typed wins, and before the X
        // server's socket opens (the GLX route turns server GLX on).
        applyFastOpenGLEnv(waylandMode);

        // Create our overall XEnvironment with various components
        preloaderDialog.step(3, "Building environment…");
        environment = new XEnvironment(this, imageFs);
        environment.addComponent(
                new SysVSharedMemoryComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)
                )
        );
        // In wayland mode the embedded compositor is the display server, so don't run the X server.
        if (!waylandMode) {
            environment.addComponent(
                    new XServerComponent(
                            xServer,
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)
                    )
            );
        }

        // Audio driver logic. Reseed the launching engine's EPHEMERAL runtime prefs from the resolved
        // per-scope config (engine-scoped BANNER_AUDIO_<ENG>_* env, shortcut-over-container, else engine
        // default). Full write every launch = no cross-launch/cross-game memory in the runtime file;
        // persistence lives only in each scope's env. Reads only this engine's keys → no cross-engine
        // bleed. In-game saves persist back to THIS shortcut's env (persistAudioToShortcut).
        seedAudioPrefsForLaunch(envVars, audioDriver);
        if (audioDriver.equals("alsa")) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath + UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", "true");
            applyAlsaAudioConfig();   // push perf/adaptive/buffer to the native ALSA player before streams open
            environment.addComponent(
                    new ALSAServerComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH)
                    )
            );
        } else if (audioDriver.equals("pulseaudio")) {
            envVars.put("PULSE_SERVER", rootPath + UnixSocketConfig.PULSE_SERVER_PATH);
            // Guest-side audio buffer (winepulse). Paired with the sink-side adaptive buffer, this is
            // the other half of the crackle/latency tradeoff. Default comes from the Pulse engine's own
            // prefs ("banner_audio_pulseaudio", default 100ms); a container/shortcut PULSE_LATENCY_MSEC
            // still wins (env is already merged above, so only set it when the user hasn't).
            if (!envVars.has("PULSE_LATENCY_MSEC")) {
                int lat = getSharedPreferences("banner_audio_pulseaudio", MODE_PRIVATE).getInt("latency_msec", 100);
                if (lat > 0) envVars.put("PULSE_LATENCY_MSEC", String.valueOf(lat));
            }
            environment.addComponent(
                    new PulseAudioComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.PULSE_SERVER_PATH)
                    )
            );
        } else if (audioDriver.equals("directaudio")) {
            // Native AAudio via winedirectaudio.drv: no host audio server and no ALSA/Pulse socket — the
            // guest reaches AAudio directly and the unixlib reads BANNER_AUDIO_DIRECT_* from the env at
            // stream open. Resolve the cog preset to concrete env here (LOW_LATENCY + a real per-preset
            // buffer) so the menu actually controls the driver. The Wine "Audio" registry driver is set
            // by changeWineAudioDriver(); route changes are handled inside the driver.
            overlayDirectAudioDriver();   // ensure a supported layer (any 11.0-x / 10.0-4) has the bundled driver before the guest loads it
            applyDirectAudioConfig(envVars);
        }

        // Turnip TU_DEBUG composition (per-container + per-game). Runs AFTER every env source is
        // merged (container DEFAULT_ENV_VARS, shortcut envVars, overrideEnvVars) so it unions with —
        // never clobbers — a manually-set TU_DEBUG. Additive by contract: emits NOTHING (leaves the
        // environment byte-for-byte unchanged) when the resolved contribution is empty, i.e. the
        // default Auto/no-tokens config on a non-710/720/722 GPU.
        applyTurnipTuDebug();

        // Pass final envVars to the launcher
        guestProgramLauncherComponent.setEnvVars(envVars);
        // Direct XInput's mode/flags for HERMES's control block (the launcher writes it with the slots'
        // identity before the guest starts). No "DirectInput only" hiding from XInput in this build.
        com.winhub.hernandez.core.HermesXInput.LaunchControl hxControl =
                com.winhub.hernandez.core.HermesXInput.launchControlMode(hxinputSync, false);
        guestProgramLauncherComponent.setHermesLaunchControl(hxControl.xinputMode, hxControl.xinputFlags);
        hermesLaunchControl = hxControl;
        final boolean launchLoggingEnabled = isLaunchLoggingEnabled();
        guestProgramLauncherComponent.setTerminationCallback((status) -> {
            // The guest process died. If it never rendered a window, this is a launch failure — show
            // the failure card and let the user read it (Close finishes). If it had already rendered,
            // this is a normal exit / in-game crash: keep the existing exit-on-termination behaviour.
            if (eaRefusalShown) {
                // EA Desktop refused the licence and the card already names EA's reason; the guest
                // tearing down after the agent's logoff is expected — keep that card.
                return;
            }
            if (!winStarted) {
                final String logDir = com.winhub.hernandez.core.LogLocation.resolveLogDir(this).getAbsolutePath();
                runOnUiThread(() -> {
                    cancelLaunchTimers();
                    preloaderDialog.fail(
                            "Launching Windows",
                            "The game exited before rendering",
                            "exit code " + status,
                            logDir,
                            launchLoggingEnabled);
                });
            } else {
                exit();
            }
        });

        // Add the launcher to our environment
        environment.addComponent(guestProgramLauncherComponent);

        // Initialize fake input for controller emulation - MUST be before Wine starts! Deleting old ones should also be done here ofc.
        // Initialize fake input for controller emulation - MUST be before Wine starts!
        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {
             // Cleanup moved to onCreate
        }

        // Manual per-device slot overrides (in-game Players sub-tab), per-container — pushed BEFORE
        // pre-assignment so launch-time slotting honors the user's pins/ignores first (a pinned pad
        // claims its exact player slot; an ignored device never takes one). Resolve shortcut-override
        // -else-container, the same owner discipline as the vibration/gyro tuning.
        winHandler.setManualSlotOverrides(parseSlotOverrides(resolvedControllerSlotOverridesJson()));

        // Pre-assign any controllers that are already connected, BEFORE Wine boots. This opens
        // their slot rings up front so the guest sees them from its first device enumeration
        // instead of only after the first input event. The fake-input path was set in onCreate
        // (setFakeInputPath), and the launcher component builds FAKE_EVDEV_MEMFD_PATHS during
        // startEnvironmentComponents below, so this must sit here — after the path is known and
        // before the guest starts reading the rings.
        winHandler.preAssignConnectedControllers();
        // Then the on-screen pad, so a game that only looks for controllers at startup finds it too.
        seatOnScreenPadBeforeBoot();
        // What the guest will list at boot: one node per slot a controller holds, nothing else.
        Log.i("XServerDisplayActivity", "fake evdev nodes at boot: "
                + java.util.Arrays.toString(devInputDir.list()) + ", slots held: " + winHandler.getUsedSlotsSnapshot());

        // Pre-launch: pull the freshest GOG cloud saves INTO the prefix before the guest boots, so the
        // game reads current progress (Galaxy-parity download-on-launch). We're on the launch worker
        // thread here (not main), so this can block briefly — which naturally GATES the guest start
        // below until the pull finishes. Bounded + best-effort: offline / slow / no-cloud-support just
        // logs and launches with local saves; newest-wins ensures a newer LOCAL save is never
        // overwritten by an older cloud copy (e.g. a save made offline since the last upload). No-op
        // for non-GOG games and when the download toggle is off.
        autoDownloadGogSavesBlocking();
        // Steam-parity: pull the freshest Steam Cloud saves into the prefix before boot (newest-wins).
        // Same launch-worker-thread gating + best-effort envelope as the GOG pull above; no-op for
        // non-Steam games and when the download toggle is off.
        autoDownloadSteamSavesBlocking();

        // In-game achievements (genuine-Steam + Goldberg-on games): seed the Goldberg/GSE
        // achievements.json with the user's REAL earned achievements, THEN arm the watcher so its
        // snapshot includes the seeded (owned) set — BOTH must happen before the guest boots below, so
        // gbe_fork reads the seeded file at startup (its screen shows what the user owns) and owned
        // achievements don't false-toast. We're on the launch worker thread → the small local
        // read/merge/write can block here. Best-effort; never blocks/crashes the launch. No-op for
        // non-Steam / Goldberg-OFF shortcuts.
        maybeSeedAndStartAchievementWatcher();

        // Single session per account: the RealSteam agent logs THIS account into genuine Steam seconds
        // after wine starts, so the app's own CM session must be down by then — but only NOW, after the
        // cloud pull + achievement seed above have used it (suspending earlier made the pre-launch
        // cloud download fail with AsyncJobFailedException). Released on game exit / activity destroy.
        suspendAppSteamSessionForRealSteam();

        // Offline (Goldberg / Raw) launch of a Steam game: the app's own session reports "in game" so
        // friends see it and playtime counts (Steam parity). No-op for RealSteam (the genuine client
        // reports itself), non-Steam shortcuts, or with the setting off. Cleared on exit / destroy.
        announceOfflineSteamPresence();

        // Start all environment components (XServer, Audio, Wine, etc.)
        preloaderDialog.step(4, "Launching Windows…");
        environment.startEnvironmentComponents();
        runOnUiThread(this::startLiveControllerSession);

        // Guest is now booting — the tail is unmeasurable, so switch to the indeterminate spinner and
        // arm the not-frozen reassurance timers (cancelled on first render or termination).
        String preloaderGameName = (shortcut != null) ? shortcut.name : container.getName();
        preloaderDialog.enterGuest("Waiting for " + preloaderGameName + " to render…");
        runOnUiThread(this::startLaunchTimers);

        // Wayland: the launch overlay must ALWAYS clear so the guest is visible — the compositor
        // present path (first-frame hook) may not fire for a shm-only desktop, and the guest must
        // never be hidden behind a stuck spinner. Force-close it 2s after guest boot, unconditionally.
        if (waylandMode) {
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                if (!winStarted) { winStarted = true; cancelLaunchTimers(); }
                preloaderDialog.closeOnUiThread();
            }, 2000L);
        }

        // Start the WinHandler (writes events to the file)
        winHandler.start();
        scheduleStartPrograms();
        // Steam Controller support (no-op unless the setting is on) — after WinHandler so pads that
        // are already paired seat straight into its slots.
        runOnUiThread(this::startSteamControllerSupport);

        // If this session was launched to run a component installer, watch for it to finish and
        // auto-close the container (see componentInstallerExe / installerWatchRunnable).
        if (componentInstallerExe != null && !componentInstallerExe.isEmpty()) startInstallerWatch();
        // Otherwise, for a game-shortcut launch, optionally auto-close the session when the game exits
        // so the user isn't left on the empty Wine desktop. Skipped for plain container/file-manager
        // launches (shortcut == null) and during installer runs.
        else if (shortcut != null && resolvedAutoCloseOnExit()) {
            autoCloseOnExitEnabled = true;
            startGameExitWatch();
        }

        if (wineRequestHandler != null) wineRequestHandler.start();

        // Reset dxwrapper config
        dxwrapperConfig = null;

    }

    private void createWrapperScript(String path, String content) {
        File scriptFile = new File(path);
        FileUtils.writeString(scriptFile, content);
        scriptFile.setExecutable(true);
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        // Seeded here (after the container + backend are resolved, and after the drawer's reset()
        // in onCreate) so the drawer can grey Wayland-unsupported controls such as Relative Mouse.
        XServerDrawerState.INSTANCE.setIsWaylandMode(waylandMode);
        if (waylandMode) setupWaylandDrawerGlue();
        xServerView = new XServerView(this, xServer);
        // Vulkan unless the container/game picked EGL; any legacy/unknown id degrades to Vulkan, and
        // initRenderer itself falls back to Vulkan if the EGL renderer cannot start on this device.
        xServerView.initRenderer(resolvedRenderer(), resolvedSurfaceDirectOptions());
        final HostRenderer renderer = xServerView.getRenderer();
        // Gate the drawer's live Present Mode selector: it only exists on the Vulkan host renderer.
        XServerDrawerState.INSTANCE.setRendererIsVulkan(
            renderer instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer);
        renderer.setCursorVisible(false);
        if (renderer instanceof com.winhub.hernandez.renderer.egl.EglRenderer) {
            // Frames that skip the CPU copy path still have to tick the perf HUD.
            ((com.winhub.hernandez.renderer.egl.EglRenderer) renderer).setHudFrameTick(this::driveHudFrameTick);
            if (container != null) renderer.setFilterMode(container.getRendererFilterMode());
        }

        // Power-user perf (non-root): arm the thermal watchdog for this session, and if the priority
        // boost is on, raise the guest CPU-worker subtree once it exists (short delay so the guest is
        // up and getPid() is populated).
        com.winhub.hernandez.perf.TempWatchdog.INSTANCE.start(this);
        if (XServerDrawerState.INSTANCE.getPerfPriorityBoost().getValue()) {
            handler.postDelayed(
                () -> com.winhub.hernandez.perf.PerfPriority.INSTANCE.boost(GuestProgramLauncherComponent.getPid()), 5000);
        }

        // No-root Samsung Galaxy Performance SDK: load and apply this container's saved power
        // profile for the session (dormant off Samsung / without the bundled SDK jar).
        if (container != null) {
            com.winhub.hernandez.perf.galaxy.GalaxyPerfManager.start(container.getRootDir());
        }

        // Cross-vendor "a game is running" signal -> lets each OEM's own game-mode booster engage
        // (OnePlus/OPPO/Red Magic/Xiaomi/Pixel/...). No-op below Android 13. No jar, no root.
        com.winhub.hernandez.perf.GameModeSignal.enterGameplay(this);

        // Standalone FPS limiter (guest-side, via the X11 Present extension): apply the resolved
        // per-game/container value up front, independent of the frame-gen engine. The in-game toggle
        // (onFpsLimitChange) updates it live afterwards.
        applyFpsLimit(resolvedFpsLimiterEnabled() ? resolvedFpsLimiterValue() : 0);

        // Apply the container's advanced Vulkan present settings (native rendering, present mode,
        // filter, swap R/B). Source of truth = the container's dedicated renderer* fields. The old
        // code parsed these out of graphicsDriverConfig via KeyValueSet, but that splits on COMMA
        // while graphicsDriverConfig is SEMICOLON-separated -> every value silently fell to default,
        // so these options never applied. These are container-level (not per-game shortcut extras).
        if (renderer instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer) {
            com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkRenderer =
                (com.winhub.hernandez.renderer.vulkan.VulkanRenderer) renderer;
            // Experimental (FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED): let the LSFG probe accept
            // a Vulkan 1.1/1.2 compositor driver via extensions. Must precede nativeInit like the
            // driver info below. Only for a session that will run LSFG Native (per-game engine
            // override included): the switch changes device creation, so a container whose
            // engine is Off or Win-FG must get the same device as before.
            vkRenderer.setLsfgVk11Compat(
                com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED
                    && container.isLsfgVk11Compat()
                    && "lsfg-native".equals(resolvedFrameGenEngine()));
            // Compositor (present-layer) Vulkan driver. "system"/empty => leave driverPath null so
            // nativeInit falls back to the system libvulkan (the safe default). An installed Turnip =>
            // point the compositor at it. MUST run before the async surface nativeInit.
            if (rendererDriverId != null && !rendererDriverId.isEmpty()
                    && !rendererDriverId.equalsIgnoreCase("system")) {
                try {
                    AdrenotoolsManager adm = new AdrenotoolsManager(this);
                    if (adm.enumarateInstalledDrivers().contains(rendererDriverId)) {
                        String rp = adm.getDriverPath(rendererDriverId);
                        String rl = adm.getLibraryName(rendererDriverId);
                        if (rl != null && !rl.isEmpty()) {
                            vkRenderer.setDriverInfo(rp, rl, getApplicationInfo().nativeLibraryDir);
                            Log.d("RendererDriver", "compositor driver = " + rendererDriverId + " (" + rl + ")");
                        }
                    }
                } catch (Exception e) {
                    Log.w("RendererDriver", "failed to apply renderer driver '" + rendererDriverId + "', staying on system", e);
                }
            }
            // Present mode (with the mailbox-while-FG override) AND the drawer's live selector seed both
            // flow through the single choke point applyEffectivePresentMode() — see that method.
            applyEffectivePresentMode();
            // Scaling mode owns the base sampler filter on Vulkan (modes 1/2 call setFilterMode
            // internally), so drive the launch through setUpscaler instead of a separate
            // setFilterMode call — keeping the in-game "Scaling mode" picker the single source of
            // truth for scaling/filtering. Default the scaling mode to Linear (1) — the safe,
            // artifact-free choice for a global default — and only seed Nearest (2) when the
            // container's filter mode is explicitly Nearest; mirror it into the drawer.
            // (filterMode: 0=default -> Linear, 1=linear -> Linear, 2=nearest -> Nearest.)
            // Per-game scaling mode wins if the user picked one in-game last session; else the
            // container base filter (Linear/Nearest). Restores SGSR/FSR/etc. across relaunch.
            int initialUpscaler = resolveScalingMode();
            vkRenderer.setUpscaler(initialUpscaler);
            XServerDialogState.INSTANCE.setUpscalerMode(initialUpscaler);
            // Supersampling: when the launch resolution was scaled above display res (see onCreate),
            // run the compositor's quality Lanczos downscale. No-op when render scale is Off.
            vkRenderer.setHqDownscale(hqDownscale);
            // Composable CAS / fake-HDR + real upscaler sharpness — remembered PER GAME (shortcut
            // override, else container) so an in-game change survives relaunch (#382). Defaults match
            // the legacy seed (sharpness 75 == 0.25 RCAS stops, CAS off @60, HDR off), so first launch
            // with no saved value is byte-identical. Seed the renderer AND mirror into the drawer state.
            int vkUpscaleSharpness = resolveExtraInt("upscaleSharpness", 75);
            boolean vkCasEnabled = resolveExtraBool("casEnabled", false);
            int vkCasSharpness = resolveExtraInt("casSharpness", 60);
            boolean vkHdrEnabled = resolveExtraBool("hdrEnabled", false);
            vkRenderer.setUpscaleSharpness(vkUpscaleSharpness);
            vkRenderer.setCas(vkCasEnabled, vkCasSharpness);
            vkRenderer.setHdr(vkHdrEnabled);
            XServerDialogState.INSTANCE.setUpscaleSharpness(vkUpscaleSharpness);
            XServerDialogState.INSTANCE.setCasEnabled(vkCasEnabled);
            XServerDialogState.INSTANCE.setCasSharpness(vkCasSharpness);
            XServerDialogState.INSTANCE.setHdrVkEnabled(vkHdrEnabled);
            // Phase 2 screen effects — drawer-only / session-live, default
            // off / neutral grade. Seed the renderer and mirror into the drawer state.
            vkRenderer.setScreenEffects(0f, 0f, 1.0f, 100f, false, false, false, false);
            XServerDialogState.INSTANCE.setVkBrightness(0f);
            XServerDialogState.INSTANCE.setVkContrast(0f);
            XServerDialogState.INSTANCE.setVkGamma(1.0f);
            XServerDialogState.INSTANCE.setVkSaturation(100f);
            XServerDialogState.INSTANCE.setVkFxaa(false);
            XServerDialogState.INSTANCE.setVkToon(false);
            XServerDialogState.INSTANCE.setVkCrt(false);
            XServerDialogState.INSTANCE.setVkNtsc(false);
            vkRenderer.setSwapRB(resolvedRendererSwapRB());
            // Must run before the surface is created so onSurfaceCreated sets up the scanout path.
            // A restored preset scaling mode (>=3, e.g. FSR) lives in the compositor pass that native
            // direct-scanout bypasses, so it wins over the container's native flag on relaunch —
            // mirroring the in-game mutual exclusion (picking a preset turns Native Rendering off).
            // "Colors: RGBA" (R/B swap — buffers are BGRA by default) can't be done on a composited AHB
            // in native mode (setColorTransform is blocked on Android 12+), so a container that needs the
            // swap runs through the normal compositor instead — where nativeSetSwapRB does it in-shader.
            // BGRA (no swap, the native DXVK buffer order) stays native.
            boolean nativeOn = resolvedRendererNative() && initialUpscaler < 3 && !resolvedRendererSwapRB();
            vkRenderer.setInitialNativeMode(nativeOn);
            XServerDrawerState.INSTANCE.setNativeRenderingEnabled(nativeOn); // keep the toggle in sync
            // Native is the supported path on Vulkan — EXCEPT when Colors=RGBA (R/B swap), which native
            // can't do (setColorTransform blocked on 12+). Such a container runs on the compositor, so
            // hide the in-game Native toggle too; otherwise forcing it on would re-break the colors.
            XServerDrawerState.INSTANCE.setNativeRenderingSupported(!resolvedRendererSwapRB());
            // Tick the perf HUD per present (the Vulkan AHB path bypasses copyArea, which normally
            // drives it). driveHudFrameTick gates on the FPS window (self-healing onto the real
            // presenting window for GL/Zink) so we only count game frames.
            vkRenderer.setHudFrameTick(this::driveHudFrameTick);
        }

        if (shortcut != null) {
            renderer.setUnviewableWMClasses("explorer.exe");
            // Wayland: the compositor skips explorer's windows the same way.
            if (waylandMode) com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHideShell(true);
        }

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        // Wayland mode: overlay the embedded compositor's SurfaceView on top of the (idle) X view,
        // and start the compositor rendering into it. winewayland.drv connects to its socket.
        if (waylandMode) startWaylandCompositor(rootView);
        // "Launch this game on the TV": re-read the display the window is on now that it is attached,
        // then ask the TV for the output mode the user picked — but only when the window really is on
        // the TV. The re-read goes through checkSessionDisplay so a cable pulled between onCreate and
        // here (the whole container setup) takes the normal unplug path instead of being mistaken for a
        // refusal; it is a no-op when nothing moved.
        checkSessionDisplay("UI ready");
        if (onTvLaunchDisplay()) {
            int tvModeId = com.winhub.hernandez.display.ExternalDisplay.modeId(shortcut);
            if (tvModeId > 0) {
                // The window's preferred mode is what asks the display to switch; 0 leaves the TV on
                // whatever it is already doing, which is why "Default" stores 0.
                try {
                    android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.preferredDisplayModeId = tvModeId;
                    getWindow().setAttributes(lp);
                } catch (Throwable t) {
                    Log.w("XServerDisplayActivity", "TV: output mode " + tvModeId + " refused", t);
                }
            }
        } else if (tvLaunchDeclined && !tvLaunchDeclinedNotified) {
            // The game was aimed at the TV and came up on the handheld. Silent refusal otherwise: the
            // launcher's catch never fires for it, so this is the only place the user hears about it.
            // Once per session, and safe this early — the toast is state the host shows when it composes.
            tvLaunchDeclinedNotified = true;
            try {
                XServerDialogState.INSTANCE.showInfoToast(
                        "TV LAUNCH REFUSED", "handheld",
                        "The system wouldn't open this game on the TV. It's running on the handheld screen.");
            } catch (Throwable ignored) {}
        }
        startHdrCapabilityReport();

        // Version A: watch for an external (TV) display and reparent the game onto it, using the
        // handheld as the controller. The listener updates the in-game TV tab + raises Compose toasts.
        // Gated behind FeatureFlags.TV_OUTPUT_ENABLED — while off, none of this is constructed or
        // started, so a TV/DeX display never triggers an auto-swap and the in-game TV tab stays hidden
        // (tvConnected / castSupported are left false). All external call sites null-guard the
        // controller / caster, so leaving them null is safe. See issue #339.
        if (com.winhub.hernandez.FeatureFlags.TV_OUTPUT_ENABLED) {
        com.winhub.hernandez.display.ExternalDisplayController.Listener tvListener =
                new com.winhub.hernandez.display.ExternalDisplayController.Listener() {
                    @Override public void onTvConnectedChanged(boolean connected, String displayName) {
                        XServerDrawerState.INSTANCE.setTvConnected(connected);
                        XServerDrawerState.INSTANCE.setTvDisplayName(displayName != null ? displayName : "");
                        // Populate the TV tab's display-mode picker + HDR readout from the display.
                        if (connected && externalDisplayController != null) {
                            java.util.List<XServerDrawerState.TvDisplayMode> ms = new java.util.ArrayList<>();
                            for (android.view.Display.Mode m : externalDisplayController.getSupportedModes()) {
                                String label = m.getPhysicalWidth() + "×" + m.getPhysicalHeight()
                                        + " @ " + Math.round(m.getRefreshRate()) + "Hz";
                                ms.add(new XServerDrawerState.TvDisplayMode(m.getModeId(), label));
                            }
                            XServerDrawerState.INSTANCE.setTvModes(ms);
                            XServerDrawerState.INSTANCE.setTvCurrentModeId(externalDisplayController.getActiveModeId());
                            XServerDrawerState.INSTANCE.setTvHdr(externalDisplayController.getHdrSummary());
                        } else {
                            XServerDrawerState.INSTANCE.setTvModes(java.util.Collections.emptyList());
                            XServerDrawerState.INSTANCE.setTvHdr("");
                        }
                        // When auto-switch is off we only notify and wait for the user to open the TV tab.
                        if (connected && externalDisplayController != null && !externalDisplayController.isAutoSwap()) {
                            XServerDialogState.INSTANCE.showInfoToast(
                                    "EXTERNAL DISPLAY DETECTED", "TV",
                                    "Open the TV tab in the menu to switch displays");
                        }
                    }
                    @Override public void onGameOnExternalChanged(boolean onExternal) {
                        XServerDrawerState.INSTANCE.setTvGameOnExternal(onExternal);
                        // Show the on-handheld "playing on external display" indicator (the phone would
                        // otherwise be a black screen once the game surface moves to the TV).
                        XServerDialogState.INSTANCE.setPlayingOnExternal(onExternal);
                        applyHandheldDim(); // dim the phone when the game is on the TV (restore when back)
                        if (onExternal) {
                            XServerDialogState.INSTANCE.showInfoToast(
                                    "GAME MOVED TO TV", "now", "Use the handheld as the controller");
                        } else {
                            XServerDialogState.INSTANCE.showInfoToast(
                                    "GAME ON HANDHELD", "now", "Returned to the phone screen");
                        }
                    }
                };
        externalDisplayController = new com.winhub.hernandez.display.ExternalDisplayController(this, xServerView, rootView, tvListener);

        // Seed the master "Play on TV" / "Auto-switch" state from the container BEFORE start(): start()
        // runs the first update() and would auto-swap immediately, so the persisted off-switch has to be
        // applied first (issue #339 — the toggle previously reset to ON every launch).
        try {
            boolean tvEnabled = !"0".equals(container.getExtra("tv.enabled", "1"));
            boolean tvAutoSwap = !"0".equals(container.getExtra("tv.autoSwap", "1"));
            externalDisplayController.setEnabled(tvEnabled);
            externalDisplayController.setAutoSwap(tvAutoSwap);
            XServerDrawerState.INSTANCE.setTvPlayOnTv(tvEnabled);
            XServerDrawerState.INSTANCE.setTvAutoSwap(tvAutoSwap);
        } catch (Exception ignored) {}

        externalDisplayController.start();

        // Wire the in-game TV tab controls to the controller. Both master switches persist per-container.
        XServerDrawerState.INSTANCE.onTvPlayOnTvChange = (b) -> {
            externalDisplayController.setEnabled(b);
            container.putExtra("tv.enabled", b ? "1" : "0");
            container.saveData();
        };
        XServerDrawerState.INSTANCE.onTvAutoSwapChange = (b) -> {
            externalDisplayController.setAutoSwap(b);
            container.putExtra("tv.autoSwap", b ? "1" : "0");
            container.saveData();
        };
        XServerDrawerState.INSTANCE.onMoveToTv = () -> externalDisplayController.requestMoveToExternal();
        XServerDrawerState.INSTANCE.onBringBackFromTv = () -> externalDisplayController.bringBackToHandheld();
        XServerDrawerState.INSTANCE.onTvModeChange = (id) -> externalDisplayController.setPreferredModeId(id);

        // TV Options v2: seed from the container (TV settings are display-scoped, stored as tv.* extras).
        try {
            int tvOs = Integer.parseInt(container.getExtra("tv.overscan", "0"));
            XServerDrawerState.INSTANCE.setTvOverscan(tvOs);
            externalDisplayController.setOverscanPercent(tvOs);
            XServerDrawerState.INSTANCE.setTvDimHandheld(!container.getExtra("tv.dim", "1").equals("0"));
            XServerDrawerState.INSTANCE.setTvAudioOut(Integer.parseInt(container.getExtra("tv.audioOut", "0")));
            XServerDrawerState.INSTANCE.setTvRenderRes(Integer.parseInt(container.getExtra("tv.renderRes", "0")));
        } catch (Exception ignored) {}

        XServerDrawerState.INSTANCE.onTvOverscanChange = (p) -> {
            externalDisplayController.setOverscanPercent(p);
            container.putExtra("tv.overscan", String.valueOf(p));
            container.saveData();
        };
        XServerDrawerState.INSTANCE.onTvDimHandheldChange = (b) -> {
            container.putExtra("tv.dim", b ? "1" : "0");
            container.saveData();
            applyHandheldDim();
        };
        XServerDrawerState.INSTANCE.onTvAudioOutChange = (i) -> {
            container.putExtra("tv.audioOut", String.valueOf(i));
            container.saveData();
            applyTvAudioRoute(i);
        };
        XServerDrawerState.INSTANCE.onTvRenderResChange = (i) -> {
            // Stored only — the render resolution is fixed at X-server bring-up, so it applies next launch.
            container.putExtra("tv.renderRes", String.valueOf(i));
            container.saveData();
        };

        // Wireless cast: in-app device picker. We discover Google Cast devices ourselves (mDNS) and show
        // them in our own dialog (Refresh + per-device Connecting/Connected status + Disconnect), instead
        // of bouncing to the Android cast screen. NOTE: this build ships the PICKER — the live game
        // streaming pipeline is the next increment, so "connect" verifies the device is reachable.
        XServerDrawerState.INSTANCE.setCastSupported(true);
        castDiscovery = new com.winhub.hernandez.cast.CastDiscovery(this, devices -> runOnUiThread(() -> {
            XServerDialogState.INSTANCE.setCastDevices(devices);
            XServerDialogState.INSTANCE.setCastScanning(false);
        }));
        XServerDrawerState.INSTANCE.onOpenCastPicker = () -> {
            // Keep the connected state if a cast is active — only clear when idle (not casting).
            if (XServerDialogState.INSTANCE.getCastStatus().getValue() == XServerDialogState.CastStatus.IDLE) {
                XServerDialogState.INSTANCE.setCastTargetName("");
                XServerDialogState.INSTANCE.setCastStatusDetail("");
            }
            XServerDialogState.INSTANCE.setCastScanning(true);
            castDiscovery.refresh();
            XServerDialogState.INSTANCE.show(XServerDialogState.ActiveDialog.CAST);
        };
        XServerDialogState.INSTANCE.onCastRefresh = () -> {
            XServerDialogState.INSTANCE.setCastScanning(true);
            castDiscovery.refresh();
        };
        // Part 2 (Step 1): the game→VirtualDisplay→H.264 capture engine. Reports state to the dialog.
        gameCaster = new com.winhub.hernandez.cast.GameCaster(this, xServerView, rootView, (castState, detail) -> {
            switch (castState) {
                case "STREAMING":
                    XServerDialogState.INSTANCE.setCastStatus(XServerDialogState.CastStatus.CONNECTED);
                    XServerDialogState.INSTANCE.setCastStatusDetail("Capturing the game (test recording). " +
                            "TV playback is the next update — tap Disconnect to stop & save.");
                    break;
                case "FAILED":
                    XServerDialogState.INSTANCE.setCastStatus(XServerDialogState.CastStatus.FAILED);
                    XServerDialogState.INSTANCE.setCastStatusDetail(detail);
                    if (externalDisplayController != null) externalDisplayController.resumeAfterCast();
                    break;
                default:
                    XServerDialogState.INSTANCE.setCastStatus(XServerDialogState.CastStatus.IDLE);
                    XServerDialogState.INSTANCE.setCastStatusDetail(detail);
            }
        });
        XServerDialogState.INSTANCE.onCastConnect = (device) -> {
            XServerDialogState.INSTANCE.setCastTargetName(device.name);
            XServerDialogState.INSTANCE.setCastStatus(XServerDialogState.CastStatus.CONNECTING);
            XServerDialogState.INSTANCE.setCastStatusDetail("Starting the live stream…");
            // Step 2b: encode the game live into HLS segments, host them, and cast the live playlist.
            if (externalDisplayController != null) externalDisplayController.pauseForCast();
            castSegmenter = new com.winhub.hernandez.cast.TsSegmenter();
            boolean ok = gameCaster.startStream(1280, 720, 6_000_000, castSegmenter);
            if (!ok) {
                if (externalDisplayController != null) externalDisplayController.resumeAfterCast();
                return;
            }
            startLiveCast(device, castSegmenter);
        };
        XServerDialogState.INSTANCE.onCastDisconnect = () -> stopCast();
        } // end FeatureFlags.TV_OUTPUT_ENABLED

        // Audio-tab callbacks — independent of the TV feature, so wired unconditionally. Routed to the
        // engine that actually launched: PulseAudio recreates its sink; ALSA re-pushes its native config
        // (which bumps the generation so live streams reopen). The drawer shows this label at the top.
        XServerDrawerState.INSTANCE.audioDriverLabel = audioDriverLabel(audioDriver);
        XServerDrawerState.INSTANCE.audioDriverId = audioDriver;   // "alsa"/"pulseaudio" → per-engine prefs file
        // Reset audio = live route recovery only (no persist). Reapply = user saved in-game → apply live
        // AND persist to THIS shortcut's env (per-game; never the container/other games).
        XServerDrawerState.INSTANCE.onResetAudio = () -> {
            if ("alsa".equals(audioDriver)) applyAlsaAudioConfig();
            else if ("pulseaudio".equals(audioDriver)) resetGuestAudioForRouteChange();
            // directaudio: route recovery is handled inside winedirectaudio.drv (AAudio disconnect ->
            // reopen); no host-side action.
        };
        XServerDrawerState.INSTANCE.onReapplyAudio = () -> {
            if ("alsa".equals(audioDriver)) applyAlsaAudioConfig();
            else if ("pulseaudio".equals(audioDriver)) resetGuestAudioForRouteChange();
            // directaudio applies LIVE by writing its mailbox file, which the running winedirectaudio.drv
            // watcher picks up and reopens the stream from (no relaunch); it also persists below.
            else if ("directaudio".equals(audioDriver)) writeDirectAudioRuntime();
            persistAudioToShortcut(audioDriver);
        };

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        touchpadView = new TouchpadView(this, xServer, timeoutHandler, hideControlsRunnable);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMouseEnabled(!isMouseDisabled);
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.END)) drawerLayout.openDrawer(GravityCompat.END);
        });
        // The preference persists across launches but was never restored onto the view, so
        // Cursor to Touch silently reverted to off every session until it was toggled again.
        touchpadView.setMoveCursorToTouchpoint(preferences.getBoolean("move_cursor_to_touchpoint", false));
        applyGestureConfig(); // wiring ran before this view existed; push the seeded set now
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this, timeoutHandler, hideControlsRunnable);
        inputControlsView.setShowKeyboardCallback(this::showGuestKeyboard);
        float savedOverlayOpacity = preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY);
        inputControlsView.setOverlayOpacity(savedOverlayOpacity);
        XServerDrawerState.INSTANCE.setOverlayOpacity(savedOverlayOpacity); // seed the Controls-tab slider

        // Swipeable OSC (Stage 2): resolve per-category swipe gates (Buttons default ON; D-pad and Sticks
        // default OFF), apply to the live overlay, and seed the drawer's Swipe tab so its chips match.
        boolean swipeButtons = preferences.getBoolean("touchscreen_swipe_buttons_enabled", true);
        boolean swipeDpad = preferences.getBoolean("touchscreen_swipe_dpad_enabled", false);
        boolean swipeSticks = preferences.getBoolean("touchscreen_swipe_sticks_enabled", false);
        inputControlsView.setSwipeButtonsEnabled(swipeButtons);
        inputControlsView.setSwipeDpadEnabled(swipeDpad);
        inputControlsView.setSwipeSticksEnabled(swipeSticks);
        XServerDrawerState.INSTANCE.setSwipeButtons(swipeButtons);
        XServerDrawerState.INSTANCE.setSwipeDpad(swipeDpad);
        XServerDrawerState.INSTANCE.setSwipeSticks(swipeSticks);
        inputControlsView.setTouchpadView(touchpadView);
        inputControlsView.setXServer(xServer);
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);

        inputControlsView.setVisualStyle(VisualStyle.GAMEHUB);

        // Wayland mode: touchpadView and inputControlsView stay above the compositor surface, exactly
        // like on X11. Their input goes to the X server, whose input sink forwards it to the compositor,
        // so touch gets the full X11 gesture set (tap, hold-drag, two-finger right click, scroll) and
        // the on-screen controls and HUD stay visible (a SurfaceView brought to the front punches
        // through the views below it). Only the overlay pointer goes on top.
        if (waylandMode && waylandCursorView != null) waylandCursorView.bringToFront();

        startTouchscreenTimeout();

        // Inside onCreate(), after initializing controls
        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        if (isTimeoutEnabled) {
            startTouchscreenTimeout();
        }

        // Reseed the in-game drawer's HUD state from the now-loaded container config.
        // The early seed in setupUI runs before `container` is assigned, so it defaults
        // to classic; without this the drawer shows classic toggles even when the
        // container (and the live overlay below) are configured for the GameHub HUD.
        if (container != null) XServerDrawerState.INSTANCE.setFpsConfig(resolvedFPSCounterConfig());

        // Container-gated launch build. The live in-game "Show HUD" toggle can also drive this later
        // (see onFpsConfigApply -> ensureHudBuilt), so the build body lives in one idempotent method.
        if (container != null && container.isShowFPS()) {
            ensureHudBuilt();
        }

        // Resolve the fullscreen aspect-ratio mode (#71): a per-game shortcut override wins, else the
        // container's setting, with backward-compat for the legacy per-game "fullscreenStretched".
        int fullscreenMode = Container.FULLSCREEN_OFF;
        String scMode = shortcut != null ? shortcut.getExtra("fullscreenMode") : "";
        String scStretched = shortcut != null ? shortcut.getExtra("fullscreenStretched") : "";
        if (shortcut != null && scMode != null && !scMode.isEmpty()) {
            try { fullscreenMode = Integer.parseInt(scMode); } catch (NumberFormatException ignored) {}
        } else if (shortcut != null && scStretched != null && !scStretched.isEmpty()) {
            fullscreenMode = scStretched.equals("1") ? Container.FULLSCREEN_STRETCH : Container.FULLSCREEN_OFF;
        } else if (container != null) {
            fullscreenMode = container.getFullscreenMode();
        }

        // Apply to the renderer. FIT and STRETCH are both fullscreen-immersive (bars already hidden
        // for the whole session via AppUtils.hideSystemUI); OFF is the default windowed letterbox.
        renderer.setFullscreenMode(fullscreenMode);
        XServerDrawerState.INSTANCE.setFullscreenMode(fullscreenMode);

        // Resolve the screen alignment (#413) the same way as the mode: per-game shortcut override wins,
        // else the container setting; absent -> ALIGN_CENTER (== today's centered letterbox).
        int screenAlignment = Container.ALIGN_CENTER;
        String scAlign = shortcut != null ? shortcut.getExtra("screenAlignment") : "";
        if (shortcut != null && scAlign != null && !scAlign.isEmpty()) {
            try { screenAlignment = Integer.parseInt(scAlign); } catch (NumberFormatException ignored) {}
        } else if (container != null) {
            screenAlignment = container.getScreenAlignment();
        }
        renderer.setScreenAlignment(screenAlignment);
        XServerDrawerState.INSTANCE.setScreenAlignment(screenAlignment);
        inputControlsView.setScreenAlignment(screenAlignment); // #413: size the OSC overlay to its half (TOP/BOTTOM)
        // Wayland: the compositor fits the desktop with the same mode + alignment (the X renderer above
        // is idle there, but the touch map still reads the mode from it, so both stay in step).
        if (waylandMode) com.winhub.hernandez.wayland.WaylandCompositor.nativeSetScaleMode(fullscreenMode, screenAlignment);
        // touchpadView.toggleFullscreen() just re-runs updateXform (it does NOT change the mode), so it
        // also picks up a non-center alignment. CENTER keeps the original OFF-only condition unchanged.
        if (fullscreenMode != Container.FULLSCREEN_OFF || screenAlignment != Container.ALIGN_CENTER) touchpadView.toggleFullscreen();

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null) showInputControls(profile);
            }

            // A Linux session is controller-first: the Steam client IS the shell.
            // Big Picture has no keyboard/mouse affordance worth falling back to.
            // So when no touch profile has been picked, seed the bundled "Virtual Gamepad" layout.
            // It binds GAMEPAD_*, which is what reaches the fake-evdev rings the session reads (FAKE_EVDEV_* above).
            // The client then sees a controller rather than synthesised key presses.
            // #338's rule still applies: a physical pad that is already connected owns the slot, so don't add a phantom one.
            // Same switch as the session's controller wiring: seeding the overlay is part of that
            // feature, and a baseline that still seeds it is not a baseline. It is read again here
            // because this runs in a different part of the launch.
            boolean controllersEnabled = !new File(
                    android.os.Environment.getExternalStorageDirectory(),
                    "Download/bannerlator-no-fake-input").exists();
            String controllerProfile = shortcut.getExtra("controllerProfile");
            if (!controllerProfile.isEmpty()) {
                ControlsProfile physical = inputControlsManager.getProfile(Integer.parseInt(controllerProfile));
                if (physical != null) setPhysicalProfile(physical);
            }

            String simTouchScreen = shortcut.getExtra("simTouchScreen");
            touchpadView.setSimTouchScreen(simTouchScreen.equals("1"));
        }

        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);

        // Initialize inline tab states (Graphics, Controls, HUD)
        initInlineTabStates(renderer);

        // Epic Friends Overlay pill — added last so it sits above the HUD/controls (only for an Epic
        // shortcut with the overlay toggle on).
        attachEpicOverlayPill();

        // NOTE: the in-game achievement seed/watch hook is deliberately NOT called here. It used to be
        // (redundant with the setupXEnvironment call), but on the MAIN thread its fetch()/CM work could
        // block launch → first-launch black screen / 0 FPS (second launch worked because the cache was
        // warm). The single call now lives on the launch WORKER thread in setupXEnvironment(), just before
        // the guest boots — off-main, and still ordered before gbe_fork's first read of achievements.json.
    }

    // Apply a fullscreen aspect-ratio mode (#71) live and remember it PER GAME: the per-game shortcut
    // override if launched from one, else the container. Shared by the drawer's segmented selector
    // (direct pick, drawer stays open) and the legacy cycle trigger.
    private void applyFullscreenMode(int mode) {
        HostRenderer r = xServerView.getRenderer();
        r.setFullscreenMode(mode);
        touchpadView.toggleFullscreen();          // recompute touch->guest map for the new mode
        if (waylandMode) com.winhub.hernandez.wayland.WaylandCompositor.nativeSetScaleMode(mode, r.getScreenAlignment());
        XServerDrawerState.INSTANCE.setFullscreenMode(mode);
        if (shortcut != null) {
            shortcut.putExtra("fullscreenMode", String.valueOf(mode));
            shortcut.putExtra("fullscreenStretched", null); // clear legacy so it can't override
            shortcut.saveData();
        } else if (container != null) {
            container.setFullscreenMode(mode);
            container.saveData();
        }
    }

    // Apply a screen alignment (#413) live and remember it PER GAME (the per-game shortcut override if
    // launched from one, else the container). Mirrors applyFullscreenMode. Only moves the letterbox bar
    // position — CENTER reproduces today's output.
    private void applyScreenAlignment(int alignment) {
        HostRenderer r = xServerView.getRenderer();
        r.setScreenAlignment(alignment);
        touchpadView.toggleFullscreen();          // recompute touch->guest map for the new alignment
        if (waylandMode) com.winhub.hernandez.wayland.WaylandCompositor.nativeSetScaleMode(r.getFullscreenMode(), alignment);
        XServerDrawerState.INSTANCE.setScreenAlignment(alignment);
        // #413: live-resize the OSC overlay to own its half (TOP/BOTTOM), or full screen (CENTER restores).
        if (inputControlsView != null) inputControlsView.setScreenAlignment(alignment);
        if (shortcut != null) {
            shortcut.putExtra("screenAlignment", String.valueOf(alignment));
            shortcut.saveData();
        } else if (container != null) {
            container.setScreenAlignment(alignment);
            container.saveData();
        }
    }

    // Scaling/upscaler mode (0-8: None/Linear/Nearest/SGSR/FSR/FSR-Fit/Sharpen/NIS/SGSR HQ) persistence.
    // In-game picks are remembered PER GAME (shortcut override, else container) so the drawer's
    // "Scaling mode" picker is sticky across relaunch — matching the fullscreen-mode behavior.
    private void persistScalingMode(int mode) {
        if (shortcut != null) {
            shortcut.putExtra("scalingMode", String.valueOf(mode));
            shortcut.saveData();
        } else if (container != null) {
            container.putExtra("scalingMode", String.valueOf(mode));
            container.saveData();
        }
    }

    // Resolve the launch scaling mode: per-game shortcut override wins; else the persisted container
    // value; else fall back to the container base sampler filter (0/2 -> Linear/Nearest).
    private int resolveScalingMode() {
        String sm = shortcut != null ? shortcut.getExtra("scalingMode") : null;
        if ((sm == null || sm.isEmpty()) && container != null) sm = container.getExtra("scalingMode");
        if (sm != null && !sm.isEmpty()) {
            try {
                int m = Integer.parseInt(sm);
                if (m >= 0 && m <= 8) return m;
            } catch (NumberFormatException ignored) {}
        }
        return container != null && container.getRendererFilterMode() == 2 ? 2 : 1;
    }

    // Texture sharpness "Auto" (dxwrapperConfig lodBias=auto): the mip LOD bias that matches the scaling
    // mode this game starts with. Only the spatial upscalers count (3 SGSR, 4 FSR, 5 FSR-Fit, 7 NIS,
    // 8 SGSR HQ); Sharpen/Linear/Nearest/None give 0. DXVK reads it once at device creation, so a
    // scaling mode changed later in the drawer applies from the next launch.
    private float autoTextureLodBias() {
        int mode = resolveScalingMode();
        boolean spatial = mode == 3 || mode == 4 || mode == 5 || mode == 7 || mode == 8;
        if (!spatial || xServer == null) return 0f;
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        return DXVKConfigDialog.autoLodBias(xServer.screenInfo.width, xServer.screenInfo.height,
                dm.widthPixels, dm.heightPixels);
    }

    // --- Generic drawer graphics quick-settings persistence (per game) -------------------------
    // The upscale/CAS/HDR sharpness sliders + SGSR/deband toggles mirror a live renderer config, so
    // an in-game change must stick per game the same way the scaling-mode picker does (#scaling-persist).
    // These follow persistScalingMode/resolveScalingMode exactly: write to the shortcut if launched from
    // one, else the container, then saveData(); read the shortcut override first, else the container,
    // else the supplied default. Booleans persist as "1"/"0" and parse either "1" or "true" on read.
    private void persistExtraInt(String key, int value) {
        if (shortcut != null) {
            shortcut.putExtra(key, String.valueOf(value));
            shortcut.saveData();
        } else if (container != null) {
            container.putExtra(key, String.valueOf(value));
            container.saveData();
        }
    }

    private void persistExtraBool(String key, boolean value) {
        if (shortcut != null) {
            shortcut.putExtra(key, value ? "1" : "0");
            shortcut.saveData();
        } else if (container != null) {
            container.putExtra(key, value ? "1" : "0");
            container.saveData();
        }
    }

    private int resolveExtraInt(String key, int def) {
        String v = shortcut != null ? shortcut.getExtra(key) : null;
        if ((v == null || v.isEmpty()) && container != null) v = container.getExtra(key);
        if (v != null && !v.isEmpty()) {
            try { return Integer.parseInt(v); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private boolean resolveExtraBool(String key, boolean def) {
        String v = shortcut != null ? shortcut.getExtra(key) : null;
        if ((v == null || v.isEmpty()) && container != null) v = container.getExtra(key);
        if (v != null && !v.isEmpty()) return v.equals("1") || v.equalsIgnoreCase("true");
        return def;
    }

    // --- FPS / perf HUD position persistence (per game) ----------------------------------------
    // Each overlay remembers its own dragged spot across relaunch. The classic vertical/horizontal
    // orientations and the GameHub HUD use distinct keys, so flipping orientation or switching HUD
    // style keeps each overlay in its own place. Written to the shortcut if launched from one, else
    // the container.
    private void persistHudPosition(String key, float x, float y) {
        String vx = String.valueOf(Math.round(x)), vy = String.valueOf(Math.round(y));
        if (shortcut != null) {
            shortcut.putExtra(key + "X", vx);
            shortcut.putExtra(key + "Y", vy);
            shortcut.saveData();
        } else if (container != null) {
            container.putExtra(key + "X", vx);
            container.putExtra(key + "Y", vy);
            container.saveData();
        }
    }

    private String getHudExtra(String key) {
        if (shortcut != null) {
            String v = shortcut.getExtra(key);
            if (v != null && !v.isEmpty()) return v;
        }
        return container != null ? container.getExtra(key) : null;
    }

    // Restore a saved HUD position once the view is actually laid out (getX/setX need its measured
    // size + post-layout left). The overlays are created GONE and revealed when the game window maps,
    // so a one-shot layout listener is used instead of post(). Clamps into the root so a spot saved on
    // a different screen size can't strand the overlay off-screen.
    private void restoreHudPosition(final View view, String key) {
        String sx = getHudExtra(key + "X"), sy = getHudExtra(key + "Y");
        if (sx == null || sx.isEmpty() || sy == null || sy.isEmpty()) return;
        final float savedX, savedY;
        try { savedX = Integer.parseInt(sx); savedY = Integer.parseInt(sy); }
        catch (NumberFormatException e) { return; }
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                                 int ol, int ot, int or, int ob) {
                if (v.getWidth() == 0 || v.getHeight() == 0) return; // not laid out yet
                v.removeOnLayoutChangeListener(this);
                View root = (View) v.getParent();
                float maxX = root != null ? Math.max(0, root.getWidth()  - v.getWidth())  : savedX;
                float maxY = root != null ? Math.max(0, root.getHeight() - v.getHeight()) : savedY;
                v.setX(Math.max(0, Math.min(savedX, maxX)));
                v.setY(Math.max(0, Math.min(savedY, maxY)));
            }
        });
    }

    // Vulkan preset <-> Native Rendering mutual exclusion (the two presets cannot coexist: native
    // direct scanout bypasses the compositor post pass where all presets live).

    /** Direction A: a preset was enabled, so turn Native Rendering off. Guarded — no-op (and no
     *  repeated toast) when native is already off, since screen-effect sliders fire continuously. */
    private void disableNativeRenderingForPreset() {
        if (!XServerDrawerState.INSTANCE.getNativeRenderingEnabled()) return;
        HostRenderer r = xServerView.getRenderer();
        if (r instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer)
            ((com.winhub.hernandez.renderer.vulkan.VulkanRenderer) r).setNativeMode(false);
        XServerDrawerState.INSTANCE.setNativeRenderingEnabled(false); // flips the toggle UI off
        showToast(this, "Native Rendering off — needed for post-processing");
    }

    /** Count mapped, real-sized top-level application windows. Native Rendering (direct scanout)
     *  is a single-fullscreen-window mode — see the onNativeRenderingToggle warning. */
    private int countMappedAppWindows() {
        if (xServer == null || xServer.windowManager == null) return 0;
        int n = 0;
        for (com.winhub.hernandez.xserver.Window w : xServer.windowManager.rootWindow.getChildren())
            if (w.isApplicationWindow()) n++;
        return n;
    }

    /** Direction B: Native Rendering was enabled, so reset every Vulkan preset to neutral so the
     *  drawer is truthful. Only touches renderer setters + StateFlows — never the apply callbacks,
     *  so this cannot re-enter disableNativeRenderingForPreset(). */
    private void resetVulkanPresets(com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr) {
        XServerDialogState ds = XServerDialogState.INSTANCE;
        vkr.setUpscaler(0);                          ds.setUpscalerMode(0);
        vkr.setCas(false, ds.getCasSharpness().getValue()); ds.setCasEnabled(false);
        vkr.setHdr(false);                           ds.setHdrVkEnabled(false);
        vkr.setScreenEffects(0f, 0f, 1.0f, 100f, false, false, false, false);
        ds.setVkBrightness(0f); ds.setVkContrast(0f); ds.setVkGamma(1.0f); ds.setVkSaturation(100f);
        ds.setVkFxaa(false); ds.setVkToon(false); ds.setVkCrt(false); ds.setVkNtsc(false);
    }

    // Read-only runtime-backend HUD chip (Graphics tab header). arch + translator are known
    // immediately from the resolved launch config; the FEX unixlib mode is filled in a few seconds
    // later once /proc/<pid>/maps has the .so mapped. Purely diagnostic — touches nothing.
    private void seedRuntimeBackend() {
        boolean arm64ec = wineInfo != null && wineInfo.isArm64EC();
        String arch = arm64ec ? "arm64ec" : "x86-64";
        String emu = emulator == null ? "" : emulator.toLowerCase();
        String translator;
        if (!arm64ec) translator = "Box64";                 // x86-64 always runs under box64
        else if (emu.contains("wowbox64")) translator = "wowbox64";
        else translator = "FEXCore";

        // Seed arch+translator now (fexMode NA); the poll below resolves the unixlib segment.
        XServerDrawerState.INSTANCE.setRuntimeBackend(new RuntimeBackend(arch, translator, FexMode.NA));

        // Box64/x86-64 never uses the FEX unixlib, so there's no maps token to probe.
        if (!arm64ec) return;

        new Thread(() -> {
            FexMode mode = FexMode.NA;
            // The unixlib .so maps ~2-3s after the guest is up; poll with a short backoff.
            for (int i = 0; i < 15 && mode == FexMode.NA; i++) {
                try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
                mode = FexProbe.detect(GuestProgramLauncherComponent.getPid());
            }
            XServerDrawerState.INSTANCE.setRuntimeBackend(new RuntimeBackend(arch, translator, mode));
        }, "fexmode-probe").start();
    }

    /** Wayland: seed the compositor's screen-effect chain from the per-game values the Vulkan path
     *  remembers (#382 keys, same defaults) and wire the drawer's Vulkan post-chain callbacks to
     *  {@link com.winhub.hernandez.wayland.WaylandCompositor}. Modes/ranges are 1:1 with the X11 Vulkan
     *  renderer; Native Rendering does not exist here, so nothing toggles it. */
    private void initWaylandEffects(XServerDialogState ds) {
        int initialUpscaler = resolveScalingMode();
        int upscaleSharpness = resolveExtraInt("upscaleSharpness", 75);
        boolean casEnabled = resolveExtraBool("casEnabled", false);
        int casSharpness = resolveExtraInt("casSharpness", 60);
        boolean hdrEnabled = resolveExtraBool("hdrEnabled", false);
        boolean debandEnabled = resolveExtraBool("debandEnabled", false);
        int debandStrength = resolveExtraInt("debandStrength", 100);
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetUpscaler(initialUpscaler);
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetUpscaleSharpness(upscaleSharpness);
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetCas(casEnabled, casSharpness);
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHdr(hdrEnabled);
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetDeband(debandEnabled, debandStrength);
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetScreenEffects(0f, 0f, 1.0f, 100f, false, false, false, false);
        ds.setUpscalerMode(initialUpscaler);
        ds.setUpscaleSharpness(upscaleSharpness);
        ds.setCasEnabled(casEnabled);
        ds.setCasSharpness(casSharpness);
        ds.setHdrVkEnabled(hdrEnabled);
        ds.setDebandEnabled(debandEnabled);
        ds.setDebandStrength(debandStrength);
        ds.setVkBrightness(0f); ds.setVkContrast(0f); ds.setVkGamma(1.0f); ds.setVkSaturation(100f);
        ds.setVkFxaa(false); ds.setVkToon(false); ds.setVkCrt(false); ds.setVkNtsc(false);
        updateWaylandLookName(ds);

        ds.onUpscalerApply = (mode) -> {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetUpscaler(mode);
            persistScalingMode(mode);
            updateWaylandLookName(ds);
        };
        ds.onUpscaleSharpnessApply = (sharpness) -> {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetUpscaleSharpness(sharpness);
            persistExtraInt("upscaleSharpness", sharpness);
        };
        ds.onCasApply = (enabled, sharpness) -> {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetCas(enabled, sharpness);
            persistExtraBool("casEnabled", enabled);
            persistExtraInt("casSharpness", sharpness);
            updateWaylandLookName(ds);
        };
        ds.onHdrApply = (enabled) -> {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetHdr(enabled);
            persistExtraBool("hdrEnabled", enabled);
        };
        ds.onDebandApply = (enabled, strength) -> {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetDeband(enabled, strength);
            persistExtraBool("debandEnabled", enabled);
            persistExtraInt("debandStrength", strength);
            updateWaylandLookName(ds);
        };
        ds.onVulkanScreenEffectsApply = (brightness, contrast, gamma, saturation, fxaa, toon, crt, ntsc) -> {
            com.winhub.hernandez.wayland.WaylandCompositor.nativeSetScreenEffects(brightness, contrast, gamma, saturation, fxaa, toon, crt, ntsc);
            updateWaylandLookName(ds);
        };
        // The compositor's effect chain is compiled in: un-grey the effect rows on Wayland.
        XServerDrawerState.INSTANCE.setWaylandEffectsAvailable(true);
    }

    /** Which Look the drawer's live Vulkan-block values are (null = Custom), named in the compositor's
     *  `effects` log line. A Look is applied through three callbacks in a row; the last one sees the
     *  whole preset and the compositor coalesces the change into one line. */
    private void updateWaylandLookName(XServerDialogState ds) {
        int cas = ds.getCasEnabled().getValue() ? ds.getCasSharpness().getValue() : 0;
        Integer idx = com.winhub.hernandez.ui.ScreenEffectLooks.INSTANCE.indexOfMatch(
                ds.getVkBrightness().getValue(), ds.getVkContrast().getValue(), ds.getVkGamma().getValue(),
                ds.getVkSaturation().getValue(), cas, ds.getVkFxaa().getValue(), ds.getVkCrt().getValue(),
                ds.getVkToon().getValue(), ds.getVkNtsc().getValue(), ds.getDebandEnabled().getValue(),
                ds.getUpscalerMode().getValue());
        com.winhub.hernandez.wayland.WaylandCompositor.nativeSetLookName(
                idx == null ? null : com.winhub.hernandez.ui.ScreenEffectLooks.INSTANCE.getLOOKS().get(idx).getName());
    }

    private void initInlineTabStates(HostRenderer renderer) {
        seedRuntimeBackend();

        XServerDialogState ds = XServerDialogState.INSTANCE;

        // Scaling mode (spatial upscaler) is a Vulkan-only control. Flag it for the drawer gate and
        // wire the apply callback here so it works on the Vulkan renderer. setUpscaler covers
        // modes 0..5 and drives the base sampler filter for modes 1/2 (single source of truth).
        // Wayland: the X renderer is idle; the embedded compositor runs the same Vulkan post chain
        // (waylandcomp/src/effects_chain.c), so the drawer's Vulkan block drives it instead.
        boolean vulkanActive = !waylandMode && renderer instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer;
        ds.setVulkanSupported(vulkanActive || waylandMode);
        if (waylandMode) {
            initWaylandEffects(ds);
        } else if (vulkanActive) {
            com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkr =
                (com.winhub.hernandez.renderer.vulkan.VulkanRenderer) renderer;
            // Direction A: enabling any preset that lives in the compositor post pass turns Native
            // Rendering OFF (it bypasses that pass). disableNativeRenderingForPreset() is guarded so
            // it's a no-op (and no repeated toast) when native is already off — important because
            // onVulkanScreenEffectsApply fires continuously during slider drags.
            ds.onUpscalerApply = (mode) -> {
                if (mode >= 3) disableNativeRenderingForPreset(); // 3=SGSR 4=FSR 5=FSR-Fit 6=Sharpen 7=NIS 8=SGSR HQ
                vkr.setUpscaler(mode);
                persistScalingMode(mode);   // remember the pick per game (#scaling-persist)
            };
            ds.onCasApply = (enabled, sharpness) -> {
                if (enabled) disableNativeRenderingForPreset();
                vkr.setCas(enabled, sharpness);
                persistExtraBool("casEnabled", enabled);   // remember per game (#382)
                persistExtraInt("casSharpness", sharpness);
            };
            ds.onHdrApply = (enabled) -> {
                if (enabled) disableNativeRenderingForPreset();
                vkr.setHdr(enabled);
                persistExtraBool("hdrEnabled", enabled);    // remember per game (#382)
            };
            // Terminal debanding (TPDF dither) — runs in the compositor post pass, so enabling
            // it (like CAS/HDR) turns Native Rendering off. Remembered per game (#382); defaults
            // off @100 so first launch is unchanged. Seed the renderer too so a saved value applies.
            boolean vkDebandEnabled = resolveExtraBool("debandEnabled", false);
            int vkDebandStrength = resolveExtraInt("debandStrength", 100);
            ds.setDebandEnabled(vkDebandEnabled);
            ds.setDebandStrength(vkDebandStrength);
            if (vkDebandEnabled) vkr.setDeband(true, vkDebandStrength);
            ds.onDebandApply = (enabled, strength) -> {
                if (enabled) disableNativeRenderingForPreset();
                vkr.setDeband(enabled, strength);
                persistExtraBool("debandEnabled", enabled); // remember per game (#382)
                persistExtraInt("debandStrength", strength);
            };
            ds.onUpscaleSharpnessApply = (sharpness) -> {
                vkr.setUpscaleSharpness(sharpness);
                persistExtraInt("upscaleSharpness", sharpness); // remember per game (#382)
            };
            ds.onVulkanScreenEffectsApply = (brightness, contrast, gamma, saturation, fxaa, toon, crt, ntsc) -> {
                // color grade neutral = brightness 0 / contrast 0 / gamma 1.0 / saturation 100
                if (fxaa || toon || crt || ntsc || brightness != 0f || contrast != 0f || gamma != 1.0f
                        || saturation != 100f)
                    disableNativeRenderingForPreset();
                vkr.setScreenEffects(brightness, contrast, gamma, saturation, fxaa, toon, crt, ntsc);
            };
        } else {
            ds.onUpscalerApply = null;
            ds.onCasApply = null;
            ds.onHdrApply = null;
            ds.onDebandApply = null;
            ds.onUpscaleSharpnessApply = null;
            ds.onVulkanScreenEffectsApply = null;
        }

        // ReShade (vkBasalt) drawer state — renderer-agnostic (the layer hooks the guest Vulkan
        // swapchain, not our host renderer), gated only on DXVK/VKD3D. Seeded HERE, in setupUI,
        // because `container`/`shortcut` are assigned by now (seeding in onCreate would capture a
        // null effect). Live-apply rides the single onReshadeApply -> applyReshadeLive seam.
        seedReshadeDrawerState(ds);
        ds.onReshadeApply = (masterEnabled, mode, items) -> applyReshadeLive(masterEnabled, mode, items);

        // "Live preview" toggle — persisted global flag (default OFF = freeze-frame + pulse preview).
        reshadeLivePreview = preferences.getBoolean("reshade_live_preview", false);
        ds.setReshadeLivePreview(reshadeLivePreview);
        ds.onReshadeLivePreviewChange = (enabled) -> {
            reshadeLivePreview = enabled;
            preferences.edit().putBoolean("reshade_live_preview", enabled).apply();
            // Turning Live preview ON while a preview freeze is active: let the game run again.
            if (enabled && reshadePreviewPaused) runOnUiThread(() -> setPausedState(false));
        };
        // Tapping the centered pause box = full resume (covers preview pause AND manual pause).
        ds.onRequestResume = () -> runOnUiThread(() -> setPausedState(false));
        // Tapping the frame-gen-reset "Resume" = rebuild the surface + SIGCONT the guest so FG
        // restarts into a clean, non-over-queued state (the LSFG black-frame flicker fix).
        ds.onFgResetResume = () -> runOnUiThread(this::resumeFromFgReset);

        // Input Controls state (renderer-independent: controller profiles + vibration work on
        // both host renderers, so this runs unconditionally.)
        ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        ArrayList<String> profileNames = new ArrayList<>();
        int selectedPosition = 0;
        for (int i = 0; i < profiles.size(); i++) {
            ControlsProfile profile = profiles.get(i);
            if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                selectedPosition = i + 1;
            profileNames.add(profile.getName());
        }
        ds.setInputProfiles(profileNames);
        ds.setSelectedProfileIdx(selectedPosition);
        ds.setShowTouchscreen(inputControlsView.isShowTouchscreenControls());
        ds.setTimeoutEnabled(preferences.getBoolean("touchscreen_timeout_enabled", false));
        ds.setHapticsEnabled(preferences.getBoolean("touchscreen_haptics_enabled", false));
        // Seed the Controls-tab accent toggle/picker from the ACTIVE profile (the one bound to the
        // running game via showInputControls, set before this runs).
        seedControlsColorState();

        ds.onInputControlsConfirm = (profileIndex, showTouchscreen, timeout, haptics) -> {
            ds.setSelectedProfileIdx(profileIndex);
            inputControlsView.setShowTouchscreenControls(showTouchscreen);
            userWantsControlsShown = showTouchscreen;   // #333: remember the user's manual choice
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("touchscreen_timeout_enabled", timeout);
            editor.putBoolean("touchscreen_haptics_enabled", haptics);
            // #338: remember an explicit "-- Disabled --" choice (profileIndex 0) so the #333 smart
            // default never re-seeds the phantom pad on later launches; picking a real profile clears it.
            editor.putBoolean("smart_default_touch_optout", profileIndex == 0);
            editor.apply();
            if (timeout) startTouchscreenTimeout();
            else touchpadView.setOnTouchListener(null);
            ArrayList<ControlsProfile> currentProfiles = inputControlsManager.getProfiles(true);
            if (profileIndex > 0 && profileIndex - 1 < currentProfiles.size()) showInputControls(currentProfiles.get(profileIndex - 1));
            else hideInputControls();
            // The active profile may have changed — re-seed the accent toggle/picker so the Controls
            // tab reflects the newly-selected profile's saved accent.
            seedControlsColorState();
        };

        ds.onInputControlsSettings = profileIndex -> {
            openInGameControlsEditorFromDialog(profileIndex);
        };

        // Vibration state
        if (winHandler != null) {
            int max = winHandler.getMaxControllers();
            java.util.List<kotlin.Pair<String, Boolean>> kSlots = new java.util.ArrayList<>();
            for (int i = 0; i < max; i++) {
                kSlots.add(new kotlin.Pair<>(
                    getString(com.winhub.hernandez.R.string.vibration_slot, i + 1),
                    winHandler.isVibrationEnabledForSlot(i)));
            }
            ds.setVibrationSlots(kSlots);
            ds.onVibrationSlotChanged = (slot, enabled) -> winHandler.setVibrationEnabledForSlot(slot, enabled);
            ds.setVibrationMasterEnabled(winHandler.isVibrationMasterEnabled());
            ds.onVibrationMasterChanged = (enabled) -> winHandler.setVibrationMasterEnabled(enabled);

            // Per-container rumble mode/intensity: Container is the source of truth (persists across
            // sessions/editor); this is called AFTER `container` is assigned (setupUI, not onCreate),
            // so it's safe to read here — mirrors the ReShade/lsfg seed-after-container pattern.
            // Resolve per-game override (shortcut extra, e.g. an imported community config) else the
            // container value; write live edits back to the same owner (shortcut only when it already
            // owns the extra, else container — so per-container behavior is untouched otherwise).
            int vibMode = resolvedVibrationMode();
            int vibIntensity = resolvedVibrationIntensity();
            winHandler.setVibrationTuning(vibMode, vibIntensity);

            // On-screen-controls priority (KEEP/YIELD/SHARE), same seed-after-container discipline as the
            // vibration tuning above: resolve per-game override else the container value and push it in.
            // #333: when auto-hide is on, unpinned pads take over the on-screen pad's slot (YIELD
            // semantics) so a connecting controller becomes the touch player and the overlay can hide.
            // This is also how "auto-hide wins over SHARE/KEEP" is realized. Pinned pads are still
            // honored (handleOnScreenModeForNewPad skips explicit pins).
            winHandler.setOnScreenControllerMode(resolvedAutoHideControlsOnPad()
                    ? Container.ON_SCREEN_MODE_YIELD : resolvedOnScreenControllerMode());
            ds.setVibrationMode(vibMode);
            ds.setVibrationIntensity(vibIntensity);
            ds.onVibrationModeChanged = (mode) -> {
                winHandler.setVibrationTuning(mode, winHandler.getVibrationIntensity());
                if (shortcut != null && shortcut.hasExtra("vibrationMode")) {
                    shortcut.putExtra("vibrationMode", String.valueOf(mode));
                    shortcut.saveData();
                } else if (container != null) {
                    container.setVibrationMode(mode);
                    container.saveData();
                }
            };
            ds.onVibrationIntensityChanged = (pct) -> {
                winHandler.setVibrationTuning(winHandler.getVibrationMode(), pct);
                if (shortcut != null && shortcut.hasExtra("vibrationIntensity")) {
                    shortcut.putExtra("vibrationIntensity", String.valueOf(pct));
                    shortcut.saveData();
                } else if (container != null) {
                    container.setVibrationIntensity(pct);
                    container.saveData();
                }
            };

            // Player Slots (manual per-device slot assignment) — Controls > Players sub-tab. Seeds the
            // drawer list from WinHandler's live device/slot state, then wires the refresh + change
            // callbacks. A change is applied LIVE (WinHandler.setDeviceSlotAssignment: physical devices
            // are torn down + re-seated so the guest sees the move; OSC softReleases and only its slot
            // index changes) and the FULL override map is persisted per-container (shortcut-override
            // -else-container, same owner as the vibration/gyro writes above), so it survives relaunch.
            final Runnable refreshPlayerSlots = () -> ds.setPlayerSlots(buildPlayerSlotRows());
            refreshPlayerSlots.run();
            ds.onPlayerSlotsRefresh = refreshPlayerSlots;
            ds.onPlayerSlotChanged = (descriptor, desiredSlot) -> {
                winHandler.setDeviceSlotAssignment(descriptor, desiredSlot);
                persistControllerSlotOverridesJson(buildSlotOverridesJson());
                refreshPlayerSlots.run();
                // Manual reassignment → status toast (may be a "PLAYER n · SHARED" state).
                showControllerStatusToast("reassign", null);
                // #333: a manual slot change alters who owns the on-screen slot (e.g. pinning a pad to
                // Player 2 = 2-player → restore the overlay; pinning to Player 1 = takeover → hide), so
                // re-evaluate auto-hide live. Also makes Ignore↔Auto a clean connect/disconnect sim.
                updateAutoHideForControllers();
            };
            ds.onResetInput = () -> {
                winHandler.resetInputPipeline();
                // Physical lane: the rebuild cleared held/latched pad state — re-apply the active
                // physical profile (or passthrough) so remapping resumes without a fresh event.
                inputControlsView.reapplyPhysicalProfile();
                refreshPlayerSlots.run();
                showControllerStatusToast("reset", null);
                // #333: pipeline reset re-seats slots → re-evaluate auto-hide against the fresh state.
                updateAutoHideForControllers();
            };

            // Physical lane (Players > Bind): the in-game Bind picker activates a profile for the
            // PHYSICAL pad's bindings (id), or -1 for native passthrough — independent of the OSC lane.
            // A FRESH manager resolves the id off disk so a profile the binder just created (in its own
            // manager) and templates both resolve here.
            ds.onPhysicalProfileChanged = (profileId) -> {
                ControlsProfile physical = profileId >= 0
                        ? new InputControlsManager(this).getProfile(profileId) : null;
                setPhysicalProfile(physical);
            };
            // Live profile lists: after the binder creates/renames a profile, reload ours and re-push the
            // Touch dropdown so new/renamed profiles appear in both pickers with no relaunch.
            ds.onProfilesChanged = () -> {
                inputControlsManager.loadProfiles(true);
                pushInputProfileNames();
            };

            // Controller-test popup: arm/disarm the input-isolation fork with the popup's visibility.
            // While active, dispatch{Generic,Key}Event fork game-controller events into the throwaway
            // visualizer snapshot instead of the guest. On close, drop any residual state and release
            // all controller inputs so nothing is left half-pressed.
            ds.onControllerTestActive = (active) -> {
                controllerTestActive = active;
                if (active) {
                    controllerTestController.state.reset();
                    controllerTestController.remappedState.reset();
                    controllerTestGuideDown = false;
                    // Default the in-game visual binder to the profile the game is running.
                    com.winhub.hernandez.inputcontrols.ControlsProfile activeProfile =
                            inputControlsView != null ? inputControlsView.getProfile() : null;
                    XServerDialogState.INSTANCE.activeProfileId = activeProfile != null ? activeProfile.id : -1;
                    refreshPlayerSlots.run();
                } else {
                    if (winHandler != null) winHandler.releaseAllControllerInputs();
                    XServerDialogState.INSTANCE.setControllerTestSnapshot(null);
                }
            };
            // "Identify" — buzz the pad owning the given 0-based slot (bypasses the vibration gate).
            ds.onControllerIdentify = (slot) -> {
                if (winHandler != null) winHandler.testRumble(slot);
            };

            // Hot-plug (add/remove/progressive-change) → status toast. WinHandler fires a plain callback
            // on the main looper; we DEBOUNCE a burst (a fast unplug/replug, or a pad that fans out into
            // several sibling sub-devices) into a single toast, keeping only the latest reason/descriptor.
            winHandler.setControllerAssignmentListener((reason, descriptor) -> {
                pendingToastReason = reason;
                pendingToastDescriptor = descriptor;
                controllerToastHandler.removeCallbacks(fireControllerToast);
                controllerToastHandler.postDelayed(fireControllerToast, CONTROLLER_TOAST_DEBOUNCE_MS);
            });

            // Gyro (motion aim) state — WinHandler holds the live values (already resolved at launch
            // from the shortcut/container chain), so this seeds the drawer straight off it. Each
            // change is applied live AND written back to the SAME owner the launch seed reads from:
            // the shortcut when the game was launched from one, else the container. Writing only to
            // the container is what made the FPS limiter "reset every time you close the game"
            // (issue #46) — the gyro is per-game too, so it would hit the identical bug.
            // Deadzone/smoothing are container-only, matching the launch resolution above.
            ds.setGyroSupported(gyroSensor != null);
            // Separate flag: plenty of devices have a gyroscope but no rotation-vector sensor, and the
            // Orientation chip is rendered disabled-with-a-reason rather than hidden on those.
            ds.setGyroOrientationSupported(gyroRotationSensor != null);
            ds.setGyroEnabled(winHandler.isGyroEnabled());
            ds.setGyroTarget(winHandler.getGyroTarget());
            ds.setGyroSensitivity(winHandler.getGyroSensitivity());
            ds.setGyroDeadzone(winHandler.getGyroDeadzone());
            ds.setGyroSmoothing(winHandler.getGyroSmoothing());
            ds.setGyroInvertX(winHandler.isGyroInvertX());
            ds.setGyroInvertY(winHandler.isGyroInvertY());
            ds.setGyroActivator(winHandler.getGyroActivator());
            ds.setGyroActivationMode(winHandler.getGyroActivationMode());
            // Read back off WinHandler, not off the local variable: the launch resolver may have been
            // overruled (mouse target, or no rotation-vector sensor), and the drawer must show what is
            // actually running.
            ds.setGyroMode(winHandler.getGyroMode());
            ds.onGyroEnabledChanged = (enabled) -> {
                winHandler.setGyroEnabled(enabled);
                persistGyroExtra("gyroEnabled", enabled ? "1" : "0");
            };
            ds.onGyroTargetChanged = (target) -> {
                winHandler.setGyroTarget(target);
                persistGyroExtra("gyroTarget", String.valueOf(winHandler.getGyroTarget()));
                // Selecting the mouse target knocks orientation mode back to rate, which means a
                // different sensor — re-register and re-seed the chip so the drawer stays honest.
                registerGyroSensor();
                ds.setGyroMode(winHandler.getGyroMode());
            };
            ds.onGyroModeChanged = (mode) -> {
                winHandler.setGyroMode(mode);
                persistGyroExtra("gyroMode", String.valueOf(winHandler.getGyroMode()));
                // Live mode switch: the OTHER sensor has to be registered before the next sample, or
                // the newly selected entry point never sees one.
                registerGyroSensor();
            };
            // Manual recenter (orientation mode). Not bound to a gamepad button on purpose — the
            // activator already spends one — and it is the ONLY way to recentre under the "Always"
            // activator, which never has a rising edge to auto-recentre on.
            ds.onGyroRecenterRequested = () -> winHandler.recenterGyroOrientation();
            ds.onGyroActivatorChanged = (index) -> {
                winHandler.setGyroActivator(index);
                persistGyroExtra("gyroActivator", String.valueOf(winHandler.getGyroActivator()));
            };
            ds.onGyroActivationModeChanged = (mode) -> {
                winHandler.setGyroActivationMode(mode);
                persistGyroExtra("gyroActivationMode", String.valueOf(winHandler.getGyroActivationMode()));
            };
            ds.onGyroSensitivityChanged = (value) -> {
                winHandler.setGyroSensitivity(value);
                persistGyroExtra("gyroSensitivity", String.valueOf(winHandler.getGyroSensitivity()));
            };
            ds.onGyroInvertXChanged = (invert) -> {
                winHandler.setGyroInvertX(invert);
                persistGyroExtra("gyroInvertX", invert ? "1" : "0");
            };
            ds.onGyroInvertYChanged = (invert) -> {
                winHandler.setGyroInvertY(invert);
                persistGyroExtra("gyroInvertY", invert ? "1" : "0");
            };
            // Container-only pair: not per-game, so these always land on the container.
            ds.onGyroDeadzoneChanged = (value) -> {
                winHandler.setGyroDeadzone(value);
                if (container != null) {
                    container.setGyroDeadzone(winHandler.getGyroDeadzone());
                    container.saveData();
                }
            };
            ds.onGyroSmoothingChanged = (value) -> {
                winHandler.setGyroSmoothing(value);
                if (container != null) {
                    container.setGyroSmoothing(winHandler.getGyroSmoothing());
                    container.saveData();
                }
            };

            // Stick deadzone / sensitivity: StickShaper is process-wide, so publishing the snapshot is
            // the live apply; it is then written back to the same owner as the gyro (shortcut, else
            // container).
            ds.setStickTuning(StickShaper.getActive());
            ds.onStickTuningChanged = (tuning) -> {
                StickShaper.setActive(tuning);
                persistStickTuning(tuning);
            };
        }

        // Task Manager actions (End Process / Bring to Front / New Task / Set Affinity) are
        // renderer-independent (UDP to winhandler.exe + host X-server focus). They MUST be wired
        // before any renderer-specific early return — otherwise the ds.onTm* callbacks stay null
        // and the drawer's `onTm...?.invoke()` is a silent no-op, so End Process / Bring to Front
        // "do nothing" (the process list still populates because startTmPolling() registers its own
        // listener). This was the root cause of the Vulkan Task Manager bug.
        setupTmCallbacks();

        // NOTE: setupTmCallbacks() is intentionally called earlier so the Task Manager actions
        // work on every renderer.
    }



    private final ActivityResultLauncher<String> inGameIconPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && inGameControlsEditor != null) {
                    inGameControlsEditor.addCustomIcon(uri);
                }
            });

    private String parseShortcutNameFromDesktopFile(File desktopFile) {
        String shortcutName = "";
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Name=")) {
                        shortcutName = line.split("=")[1].trim();
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("XServerDisplayActivity", "Error reading shortcut name from .desktop file", e);
            }
        }
        return shortcutName;
    }

    private void showInputControlsDialog() {
        ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        ArrayList<String> profileNames = new ArrayList<>();
        int selectedPosition = 0;
        for (int i = 0; i < profiles.size(); i++) {
            ControlsProfile profile = profiles.get(i);
            if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                selectedPosition = i + 1;
            profileNames.add(profile.getName());
        }

        XServerDialogState ds = XServerDialogState.INSTANCE;
        ds.setInputProfiles(profileNames);
        ds.setSelectedProfileIdx(selectedPosition);
        ds.setShowTouchscreen(inputControlsView.isShowTouchscreenControls());
        ds.setTimeoutEnabled(preferences.getBoolean("touchscreen_timeout_enabled", false));
        ds.setHapticsEnabled(preferences.getBoolean("touchscreen_haptics_enabled", false));
        // Seed the Controls-tab accent toggle/picker from the ACTIVE profile (the one bound to the
        // running game via showInputControls, set before this runs).
        seedControlsColorState();

        ds.onInputControlsConfirm = (profileIndex, showTouchscreen, timeout, haptics) -> {
            ds.setSelectedProfileIdx(profileIndex);
            inputControlsView.setShowTouchscreenControls(showTouchscreen);
            userWantsControlsShown = showTouchscreen;   // #333: remember the user's manual choice
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("touchscreen_timeout_enabled", timeout);
            editor.putBoolean("touchscreen_haptics_enabled", haptics);
            // #338: remember an explicit "-- Disabled --" choice (profileIndex 0) so the #333 smart
            // default never re-seeds the phantom pad on later launches; picking a real profile clears it.
            editor.putBoolean("smart_default_touch_optout", profileIndex == 0);
            editor.apply();
            if (timeout) startTouchscreenTimeout();
            else touchpadView.setOnTouchListener(null);
            ArrayList<ControlsProfile> currentProfiles = inputControlsManager.getProfiles(true);
            if (profileIndex > 0 && profileIndex - 1 < currentProfiles.size()) showInputControls(currentProfiles.get(profileIndex - 1));
            else hideInputControls();
            // The active profile may have changed — re-seed the accent toggle/picker so the Controls
            // tab reflects the newly-selected profile's saved accent.
            seedControlsColorState();
        };

        ds.onInputControlsSettings = profileIndex -> {
            openInGameControlsEditorFromDialog(profileIndex);
        };
        ds.show(XServerDialogState.ActiveDialog.INPUT_CONTROLS);
    }

    private void openInGameControlsEditorFromDialog(int selectedIndex) {
        if (inGameControlsEditor != null || inputControlsView == null) return;
        ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        if (selectedIndex <= 0 || selectedIndex - 1 >= profiles.size()) {
            AppUtils.showToast(this, R.string.no_profile_selected);
            return;
        }

        ControlsProfile profile = profiles.get(selectedIndex - 1);
        inGameEditorPreviousShowTouchscreen = inputControlsView.isShowTouchscreenControls();
        inGameEditorPreviousTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        inGameEditorPreviousProfile = inputControlsView.getProfile();
        XServerDialogState.INSTANCE.dismiss();
        drawerLayout.closeDrawers();
        releasePointerCaptureIfNeeded("in-game-controls-editor");
        inputControlsView.releaseAllInputs();
        if (touchpadView != null) touchpadView.releaseAllInputs();
        if (winHandler != null) winHandler.releaseAllControllerInputs();
        timeoutHandler.removeCallbacks(hideControlsRunnable);
        if (touchpadView != null) touchpadView.setOnTouchListener(null);

        showInputControls(profile);
        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setEditorBackgroundVisible(false);
        inputControlsView.setEditMode(true);
        controlsEditorOpen = true;   // #333: suspend auto-hide while editing controls

        FrameLayout container = findViewById(R.id.FLXServerDisplay);
        inGameControlsEditor = new InGameControlsEditor(
                this,
                container,
                inputControlsView,
                profile,
                this::closeInGameControlsEditor,
                () -> inGameIconPickerLauncher.launch("image/*"));
    }

    private void closeInGameControlsEditor() {
        if (inGameControlsEditor == null) return;
        inGameControlsEditor.dispose();
        inputControlsView.setEditorBackgroundVisible(true);
        inputControlsView.setEditMode(false);
        controlsEditorOpen = false;   // #333: resume auto-hide after editing
        updateAutoHideForControllers();
        if (inGameEditorPreviousProfile != null) showInputControls(inGameEditorPreviousProfile);
        else hideInputControls();
        inGameEditorPreviousProfile = null;
        inputControlsView.setShowTouchscreenControls(inGameEditorPreviousShowTouchscreen);
        inputControlsView.requestFocus();
        inputControlsView.invalidate();
        seedControlsColorState();
        if (inGameEditorPreviousShowTouchscreen && inGameEditorPreviousTimeoutEnabled) {
            startTouchscreenTimeout();
        } else {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            if (touchpadView != null) touchpadView.setOnTouchListener(null);
        }
        inGameControlsEditor = null;
        if (isRelativeMouseMovement || cursorLock) {
            inputControlsView.postDelayed(() -> ensurePointerCapture("in-game-controls-editor-closed"), 250);
        }
    }

    private void simulateConfirmInputControlsDialog() {
        // Simulate setting the relative mouse movement and touchscreen controls from preferences

        boolean isShowTouchscreenControls = preferences.getBoolean("show_touchscreen_controls_enabled", false); // default is false (hidden)
        inputControlsView.setShowTouchscreenControls(isShowTouchscreenControls);
        userWantsControlsShown = isShowTouchscreenControls;   // #333: baseline for auto-hide restore

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        boolean isHapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", false);

        // Apply these settings as if the user confirmed the dialog
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
        editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
        editor.apply();

        // If no profile is selected, hide the controls
        int selectedProfileIndex = preferences.getInt("selected_profile_index", -1); // Default to -1 for no profile

        if (selectedProfileIndex >= 0 && selectedProfileIndex < inputControlsManager.getProfiles().size()) {
            // A profile is selected, show the controls
            ControlsProfile profile = inputControlsManager.getProfiles().get(selectedProfileIndex);
            showInputControls(profile);
        } else {
            // #333 smart default layout: a fresh user who hasn't picked a profile gets the bundled
            // "Virtual Gamepad" touch layout so there's a working touch controller out of the box —
            // but ONLY when auto-hide is on (new/opted-in containers), so existing setups with no
            // profile are unchanged. The overlay then auto-hides once a controller takes over.
            //
            // #338: two suppressions so an explicit "Disabled" is honored and the phantom pad never
            // fights a real one. (a) If the user has explicitly confirmed "-- Disabled --" in the
            // controls dialog we persist an opt-out and never re-seed. (b) If a physical controller is
            // already connected at launch, the out-of-box need ("a working touch controller") is
            // already met — seeding a phantom pad here is unwanted AND grabs a player slot, which then
            // defeats auto-hide's own same-slot rule (real pad on P1, phantom pad bumped to P2 reads as
            // a second player, so the overlay is kept). Not seeding it removes the whole chain.
            boolean smartDefaultOptOut = preferences.getBoolean("smart_default_touch_optout", false);
            ControlsProfile defaultVg = (resolvedAutoHideControlsOnPad()
                    && !smartDefaultOptOut
                    && !hasConnectedGameController())
                    ? findVirtualGamepadProfile() : null;
            if (defaultVg != null) {
                inputControlsView.setShowTouchscreenControls(true);
                userWantsControlsShown = true;
                showInputControls(defaultVg);
            } else {
                // No profile selected, ensure the controls are hidden
                hideInputControls();
            }
        }

        // Timeout logic should only apply if the controls are visible
        if (isTimeoutEnabled && inputControlsView.getVisibility() == View.VISIBLE) {
            startTouchscreenTimeout(); // Start timeout if enabled and controls are visible
        } else {
            touchpadView.setOnTouchListener(null); // Disable the timeout listener if not needed
        }

        Log.d("XServerDisplayActivity", "Input controls simulated confirmation executed.");

        // #333: apply auto-hide at launch — a pad connected before/at launch should already have the
        // overlay hidden, and the pad seated on the on-screen slot (YIELD pushed above).
        updateAutoHideForControllers();
    }

    private void startTouchscreenTimeout() {
        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);

        if (isTimeoutEnabled) {
            // Show controls initially and set up touch event listeners
            inputControlsView.setVisibility(View.VISIBLE);
            Log.d("XServerDisplayActivity", "Timeout is enabled, setting up timeout logic.");

            // Attach the OnTouchListener to reset the timeout on touch events
            touchpadView.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    // Reset the timeout on any touch event
                    //Log.d("XServerDisplayActivity", "Touch detected, resetting timeout.");

                    // Keep the controls visible
                    inputControlsView.setVisibility(View.VISIBLE);

                    // Remove any pending hide callbacks and reset the timeout
                    timeoutHandler.removeCallbacks(hideControlsRunnable);
                    timeoutHandler.postDelayed(hideControlsRunnable, 5000); // Reset timeout
                }

                return false; // Allow the touch event to propagate
            });

            // Reset the timeout when the controls are initially displayed
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            timeoutHandler.postDelayed(hideControlsRunnable, 5000); // Hide after 5 seconds of inactivity
        } else {
            // If timeout is disabled, keep the controls always visible
            Log.d("XServerDisplayActivity", "Timeout is disabled, controls will stay visible.");

            inputControlsView.setVisibility(View.VISIBLE); // Ensure controls are visible
            timeoutHandler.removeCallbacks(hideControlsRunnable); // Remove any existing hide callbacks
            touchpadView.setOnTouchListener(null); // Remove the touch listener
        }
    }

    // Push the active profile's per-profile controls accent (follow-theme + custom color) into the
    // in-game drawer state so the Controls-tab toggle/picker reflect it. Defaults (follow theme,
    // app blue) when no profile is active. The drawer's onControlsColorChange writes back.
    private void seedControlsColorState() {
        ControlsProfile profile = inputControlsView != null ? inputControlsView.getProfile() : null;
        if (profile != null) {
            XServerDrawerState.INSTANCE.setControlsFollowTheme(!profile.isCustomAccentEnabled());
            XServerDrawerState.INSTANCE.setControlsAccentColor(profile.getCustomAccentColor());
        }
        else {
            XServerDrawerState.INSTANCE.setControlsFollowTheme(true);
            XServerDrawerState.INSTANCE.setControlsAccentColor(
                    com.winhub.hernandez.ui.theme.AppThemeState.getCurrentAccentArgb());
        }
    }

    // Physical lane (Players > Bind): activate `profile` on the PHYSICAL pad without drawing the OSC,
    // persist it per-game as the "controllerProfile" extra (removed = passthrough), and mirror it to the
    // dialog so the Bind picker reflects the active selection. null = revert to native Xbox passthrough.
    private void setPhysicalProfile(ControlsProfile profile) {
        inputControlsView.setPhysicalProfile(profile);
        // #345-proven: a pad's D-pad is an AXIS_HAT_* MOTION event (analog sticks are motion too), and the
        // framework focus-routes joystick motion — it only reaches InputControlsView.onGenericMotionEvent
        // when that view is VISIBLE + FOCUSED. Buttons are KEY events dispatched directly, so they work
        // without this (which is why A remapped but the D-pad didn't). Give the view focus for the physical
        // lane WITHOUT drawing the OSC: showTouchscreenControls is left untouched (the Touch lane owns it),
        // and onDraw stays gated on the OSC profile + showTouchscreenControls, so a physical-only view is
        // visible+focused for motion delivery yet paints nothing and claims no slot.
        if (profile != null) {
            inputControlsView.setVisibility(View.VISIBLE);
            inputControlsView.requestFocus();
        } else if (inputControlsView.getProfile() == null) {
            // No physical AND no OSC profile — nothing needs the view; let it go so touches pass through.
            inputControlsView.setVisibility(View.GONE);
        }
        if (shortcut != null) {
            shortcut.putExtra("controllerProfile", profile != null ? String.valueOf(profile.id) : null);
            shortcut.saveData();
        }
        XServerDialogState.INSTANCE.physicalProfileId = profile != null ? profile.id : -1;
    }

    // Re-push the Touch (on-screen) profile dropdown from the current profiles, preserving the OSC
    // selection. Called after an in-game create/rename so the list stays live (templates excluded).
    private void pushInputProfileNames() {
        ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        ArrayList<String> profileNames = new ArrayList<>();
        int selectedPosition = 0;
        for (int i = 0; i < profiles.size(); i++) {
            ControlsProfile profile = profiles.get(i);
            if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                selectedPosition = i + 1;
            profileNames.add(profile.getName());
        }
        XServerDialogState.INSTANCE.setInputProfiles(profileNames);
        XServerDialogState.INSTANCE.setSelectedProfileIdx(selectedPosition);
    }

    private void showInputControls(ControlsProfile profile) {
        inputControlsView.setVisibility(View.VISIBLE);
        inputControlsView.requestFocus();
        inputControlsView.setProfile(profile);

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);

        inputControlsView.invalidate();
        winHandler.sendGamepadState();
    }

    private void hideInputControls() {
        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        inputControlsView.invalidate();
        winHandler.sendGamepadState();
    }

    // Reads the persisted extra_libs.tzst payload version. Missing or unparseable => -1, which
    // forces a re-extract (existing installs updating into this build have no marker yet).
    private int readExtraLibsVersion(File versionFile) {
        if (!versionFile.exists()) return -1;
        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            String line = reader.readLine();
            if (line == null) return -1;
            return Integer.parseInt(line.trim());
        } catch (Exception e) {
            Log.d("XServerDisplayActivity", "extra_libs version marker unreadable/unparseable, treating as -1: " + e.getMessage());
            return -1;
        }
    }

    // Persists the extra_libs.tzst payload version marker after a successful re-extract so the
    // trigger converges (only re-extracts once per app-upgrade).
    private void writeExtraLibsVersion(File versionFile, int version) {
        try (FileOutputStream out = new FileOutputStream(versionFile)) {
            out.write(Integer.toString(version).getBytes());
        } catch (Exception e) {
            Log.d("XServerDisplayActivity", "Failed to write extra_libs version marker: " + e.getMessage());
        }
    }

    // Wrapper Version Manager (Step 1, issue #132): extract a bundled graphics_driver asset, but
    // prefer a user-installed override at filesDir/graphics_driver/<assetFileName> when present.
    // Byte-for-byte identical to the old bundled-asset extract when no override exists.
    private void extractGraphicsAsset(String assetFileName, File rootDir) {
        File override = new File(getFilesDir(), "graphics_driver/" + assetFileName);
        if (override.isFile()) {
            Log.d("GraphicsDriverExtraction", "using user override for " + assetFileName + " (" + override.getAbsolutePath() + ")");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, override, rootDir);
        } else {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/" + assetFileName, rootDir);
        }
    }

    private void extractGraphicsDriverFiles() {
    // 1. Retrieve the selected driver name from the config
    String selectedDriver = graphicsDriverConfig.get("graphicsDriver");
    if (selectedDriver == null) selectedDriver = graphicsDriverConfig.get("graphics_driver");
    
    String adrenoToolsDriverId = graphicsDriverConfig.get("version");

    // turnip-26.1.0 (the direct Vulkan ICD) was removed along with its bundled asset. A container or
    // shortcut saved on it would otherwise be handed to adrenotools with an id that has no installed
    // driver. Self-heal on the first launch of an affected container instead of sweeping every
    // container at startup: fall back to System AND persist it, so the editor stops showing a driver
    // that no longer exists. Shortcut override wins over the container, same as everywhere else.
    if (DefaultVersion.REMOVED_TURNIP_ICD.equals(adrenoToolsDriverId)) {
        Log.w("GraphicsDriverExtraction", "Driver '" + DefaultVersion.REMOVED_TURNIP_ICD
                + "' was removed — falling back to " + DefaultVersion.WRAPPER + " and persisting");
        adrenoToolsDriverId = DefaultVersion.WRAPPER;
        graphicsDriverConfig.put("version", DefaultVersion.WRAPPER);
        try {
            String rewritten = GraphicsDriverConfigDialog.toGraphicsDriverConfig(graphicsDriverConfig);
            if (shortcut != null && !shortcut.getExtra("graphicsDriverConfig", "").isEmpty()) {
                shortcut.putExtra("graphicsDriverConfig", rewritten);
                shortcut.saveData();
            } else if (container != null) {
                container.setGraphicsDriverConfig(rewritten);
                container.saveData();
            }
        } catch (Exception e) {
            // Never block the launch on the persist half — the in-memory fallback above is enough.
            Log.w("GraphicsDriverExtraction", "could not persist turnip-26.1.0 migration", e);
        }
    }

    Log.d("GraphicsDriverExtraction", "Selected Driver from Config: " + selectedDriver);
    Log.d("GraphicsDriverExtraction", "Adrenotools DriverID: " + adrenoToolsDriverId);

    File rootDir = imageFs.getRootDir();

    // Wrapper Version Manager Step 2 (issue #132): an EXACT-name override at
    // filesDir/graphics_driver/<graphicsDriver>.tzst wins over the bundled chain. This single check
    // resolves (a) the default "wrapper" slot override (which the startsWith chain below never
    // handled), (b) any bundled-slot override whose graphicsDriver == its file base name, and
    // (c) free-form IMPORTED wrappers (identifier == graphicsDriver). Bundled drivers with NO
    // matching file — including bcn/compat (graphicsDriver "wrapper-bcn_layer", whose base archive
    // is leegao_bcn.tzst / wrapper-leegao.tzst, not "wrapper-bcn_layer.tzst") — fall through
    // unchanged to the per-branch chain below.
    File userWrapper = new File(getFilesDir(), "graphics_driver/" + graphicsDriver + ".tzst");
    if (userWrapper.isFile()) {
        Log.d("GraphicsDriverExtraction", "Extracting user wrapper (override/import): " + userWrapper.getAbsolutePath());
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, userWrapper, rootDir);
    }
    // Perform wrapper extraction based on selected version
    else if (graphicsDriver.startsWith("wrapper-original")) {
        Log.d("GraphicsDriverExtraction", "Extracting: graphics_driver/wrapper-original.tzst");
        extractGraphicsAsset("wrapper-original.tzst", rootDir);
    }
    else if (graphicsDriver.startsWith("wrapper-leegao")) {
        Log.d("GraphicsDriverExtraction", "Extracting: graphics_driver/wrapper-leegao.tzst");
        extractGraphicsAsset("wrapper-leegao.tzst", rootDir);
    }
    else if (graphicsDriver.startsWith("wrapper-legacy")) {
        Log.d("GraphicsDriverExtraction", "Extracting: graphics_driver/wrapper-legacy.tzst");
        extractGraphicsAsset("wrapper-legacy.tzst", rootDir);
    }
    else if (graphicsDriver.startsWith("wrapper-gamenative")) {
        Log.d("GraphicsDriverExtraction", "Extracting: graphics_driver/wrapper-gamenative.tzst");
        extractGraphicsAsset("wrapper-gamenative.tzst", rootDir);
    }
    else if (graphicsDriver.startsWith("wrapper-bcn_layer")) {
        // Wrapper + bcn_layer == the wrapper-leegao ICD as its base, PLUS leegao's bcn_layer
        // implicit Vulkan layer (its .so + manifest ship in extra_libs.tzst and are picked up
        // via the already-set VK_LAYER_PATH). Extract the SAME base wrapper as Wrapper-leegao;
        // the BCn env block below activates the layer.
        Log.d("GraphicsDriverExtraction", "Extracting: graphics_driver/wrapper-leegao.tzst (base for wrapper-bcn_layer)");
        extractGraphicsAsset("wrapper-leegao.tzst", rootDir);
    }
    else if (graphicsDriver.startsWith("wrapper-compat-bcn")) {
        // Wrapper + compat + bcn == the wrapper-leegao ICD base, PLUS leegao's bcn_layer AND
        // compat_layer implicit Vulkan layers (their .so + manifest ship in extra_libs.tzst and are
        // picked up via the already-set VK_LAYER_PATH). Extract the SAME base wrapper as Wrapper-leegao;
        // the BCn env block below activates bcn_layer and the compat env block activates compat_layer.
        //
        // Per-game engine swap (config key compatUseGamenative=1): the leegao ICD reports Vulkan 1.1 on
        // old Mali blobs and lacks the promoted 1.3 entrypoints, so DXVK/VKD3D reject the adapter (no
        // DX12). When the tester opts in, swap the ICD base to the GameNative wrapper (Mesa-vk-runtime
        // ICD) which reports 1.3, emulates the entrypoints and has its own integrated BCn. In that mode
        // the leegao bcn_layer / compat_layer are left DORMANT (their enable-envs are NOT set below), so
        // BCn is handled by the GameNative wrapper itself (WRAPPER_EMULATE_BCN).
        if ("1".equals(graphicsDriverConfig.get("compatUseGamenative"))) {
            Log.d("GraphicsDriverExtraction", "Extracting: graphics_driver/wrapper-gamenative.tzst (GameNative engine base for wrapper-compat-bcn; compatUseGamenative=1)");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/wrapper-gamenative.tzst", rootDir);
        } else {
            Log.d("GraphicsDriverExtraction", "Extracting: graphics_driver/wrapper-leegao.tzst (leegao base for wrapper-compat-bcn; compatUseGamenative off)");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/wrapper-leegao.tzst", rootDir);
        }
    }

    // Original logic for DXWrapper and environment variables
    if (dxwrapper.contains("dxvk")) {
        DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars, dxvkLogDir(), autoTextureLodBias());
        String version = dxwrapperConfig.get("version");
        if (version != null && version.equals("1.11.1-sarek")) {
            Log.d("GraphicsDriverExtraction", "Disabling Wrapper PATCH_OPCONSTCOMP SPIR-V pass");
            envVars.put("WRAPPER_NO_PATCH_OPCONSTCOMP", "1");
        }
    }
    else if (dxwrapper.contains("vegas")) {
        DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars, dxvkLogDir(), autoTextureLodBias());
    }
    else {
        WineD3DConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
    }

    boolean useDRI3 = preferences.getBoolean("use_dri3", true);
    if (!useDRI3) {
        envVars.put("MESA_VK_WSI_DEBUG", "sw");
    }

    envVars.put("VK_ICD_FILENAMES", imageFs.getShareDir() + "/vulkan/icd.d/wrapper_icd.aarch64.json");
    envVars.put("GALLIUM_DRIVER", "zink");

    // 2. SHARED LIBS EXTRACTION
    // First boot extracts everything. After that, extra_libs.tzst carries the vkBasalt layer
    // (libvkbasalt.so + the implicit_layer.d manifest) that powers the CAS/DLS sharpness AND the
    // ReShade feature. Three triggers re-extract extra_libs.tzst so pre-existing containers heal:
    //   (a) firstTimeBoot                — brand-new container (also gets layers.tzst).
    //   (b) the layer .so is absent      — container predates the bundle entirely.
    //   (c) the installed payload is OUTDATED — the app was updated and ships a newer
    //       extra_libs.tzst (EXTRA_LIBS_VERSION bumped) than what this shared imagefs holds, so
    //       existing containers would otherwise keep the stale .so and the new features no-op.
    // Version is persisted in a marker file colocated with the extracted imagefs state
    // (imageFs.getLibDir()/.extra_libs_version) so a reinstall-imagefs resets it consistently.
    // Extraction stays a pure additive per-entry overwrite (extra_libs.tzst contains ONLY
    // usr/lib/*.so + usr/share/vulkan/* — no home/drive_c/user data); no delete/clean step.
    // Cheap & idempotent: one int read + compare on launch, extraction only on mismatch.
    File vkBasaltSo = new File(imageFs.getLibDir(), "libvkbasalt.so");
    File extraLibsVersionFile = new File(imageFs.getLibDir(), ".extra_libs_version");
    int installedExtraLibsVer = readExtraLibsVersion(extraLibsVersionFile);
    if (firstTimeBoot) {
        Log.d("XServerDisplayActivity", "First time container boot, re-extracting layers and extra_libs");
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "layers" + ".tzst", rootDir);
        extractGraphicsAsset("extra_libs.tzst", rootDir);
        writeExtraLibsVersion(extraLibsVersionFile, EXTRA_LIBS_VERSION);
    }
    else if (!vkBasaltSo.exists() || installedExtraLibsVer != EXTRA_LIBS_VERSION) {
        if (!vkBasaltSo.exists())
            Log.d("XServerDisplayActivity", "vkBasalt layer absent (pre-existing container) — re-extracting extra_libs");
        else
            Log.d("XServerDisplayActivity", "extra_libs outdated (installed=" + installedExtraLibsVer + " bundled=" + EXTRA_LIBS_VERSION + ") — re-extracting extra_libs");
        extractGraphicsAsset("extra_libs.tzst", rootDir);
        writeExtraLibsVersion(extraLibsVersionFile, EXTRA_LIBS_VERSION);
    }

    // Wrapper Version Manager (#132): the leegao BCn layer (libbcn_layer.so + manifest) normally
    // ships inside extra_libs.tzst. A user-installed "BCn layer" override (leegao_bcn.tzst) overlays
    // a newer copy on top — extract it AFTER extra_libs so it wins, and every launch when present
    // (small file) so it applies regardless of the extra_libs version gate above.
    File bcnLayerOverride = new File(getFilesDir(), "graphics_driver/leegao_bcn.tzst");
    if (bcnLayerOverride.isFile()) {
        Log.d("GraphicsDriverExtraction", "applying user BCn layer override (leegao_bcn.tzst)");
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, bcnLayerOverride, rootDir);
    }

    // 3. Driver integration — adrenotools for every selectable driver (turnip-sdk36, v819,
    // custom-installed). The old direct-Vulkan-ICD mode (turnip-26.1.0) has been removed along
    // with its bundled asset; a container still saved on it is migrated to System just above.
    if (adrenoToolsDriverId != null && !adrenoToolsDriverId.equals("System")) {
        AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
        adrenotoolsManager.setDriverById(envVars, imageFs, adrenoToolsDriverId);
    }

    // Wayland GAME driver (winewayland sets VK_ICD_FILENAMES itself). Resolve the container's
    // waylandGameDriver extra (shortcut override first) into the Proton's BANNER_WAYLAND_VK_VARIANT /
    // BANNER_WAYLAND_VK_ICD contract - adapter hands over the bundled Wayland adapter, which loads the
    // adrenotools driver exported just above (so that one pick feeds compositor AND game; System / a
    // Qualcomm blob → Auto, logged), Auto maps the device GPU to a bundled variant, imported: hands over
    // an imported Linux ICD (missing import → Auto, logged). No-op on X11; waylandMode is final by here.
    com.winhub.hernandez.core.WaylandGameDriver.applyToLaunchEnv(this, envVars, container, shortcut, waylandMode,
            adrenoToolsDriverId);
    // The Task Manager's CONTAINER block was built before this ran (setupUI); now Auto's variant is known.
    if (waylandMode) XServerDialogState.INSTANCE.setTmContainerInfo(buildTmContainerInfo());

    // --- Environment Variable Setup ---
    // 2.8.1 structure restored: WRAPPER_VK_VERSION = chosenMinor + probePatch,
    // unconditionally. The clamp introduced between 2.9 and 2.9.1 (WinNative PR
    // #669) compared the user's chosen minor against the app-process probe's —
    // but on a wrapper graphics driver the probe measures the SYSTEM ICD, not
    // the Turnip the guest actually wraps. On low-tier Adreno (e.g. 610) the
    // system blob reports Vulkan 1.0/1.1, so min(chosen, probe) collapsed
    // WRAPPER_VK_VERSION below every DXVK's floor ("Device does not support
    // Vulkan 1.3" on DXVK 2.x; "Skipping Vulkan 1.0 adapter" on Sarek) even
    // though the guest-side wrapper enumerated the inner Turnip fine. The
    // probe supplies ONLY the patch digit here, as in 2.8.1.
    // The safe split fallback is retained so a non-dotted probe result
    // ("Unknown") degrades to patch "0" instead of crashing the launch.
    String vulkanVersion = graphicsDriverConfig.get("vulkanVersion");
    if (vulkanVersion == null) vulkanVersion = "1.3";
    String driverVkVersion = GPUInformation.getVulkanVersion(adrenoToolsDriverId, this);
    String[] driverVkParts = (driverVkVersion != null && driverVkVersion.split("\\.").length >= 3)
        ? driverVkVersion.split("\\.")
        : new String[] { "1", "3", "0" };
    String vulkanVersionPatch = driverVkParts[2];
    vulkanVersion = vulkanVersion + "." + vulkanVersionPatch;
    Log.i("XServerVulkan", "WRAPPER_VK_VERSION=" + vulkanVersion
        + " driverId=" + adrenoToolsDriverId + " graphicsDriver=" + graphicsDriver
        + " driverVkVersion=" + driverVkVersion);
    envVars.put("WRAPPER_VK_VERSION", vulkanVersion);
    if (wineDebugWriter != null) {
        wineDebugWriter.println("XServerVulkan: WRAPPER_VK_VERSION=" + vulkanVersion
            + " driverVkVersion=" + driverVkVersion + " driverId=" + adrenoToolsDriverId);
    }

    String blacklistedExtensions = graphicsDriverConfig.get("blacklistedExtensions");
    envVars.put("WRAPPER_EXTENSION_BLACKLIST", blacklistedExtensions != null ? blacklistedExtensions : "");

    String gpuName = graphicsDriverConfig.get("gpuName");
    String dxvkVersion = dxwrapperConfig.get("version");
    if (gpuName != null && !gpuName.equals("Device") && dxvkVersion != null && !dxvkVersion.equals("1.11.1-sarek")) {
        envVars.put("WRAPPER_DEVICE_NAME", gpuName);
        envVars.put("WRAPPER_DEVICE_ID", WineD3DConfigDialog.getDeviceIdFromGPUName(this, gpuName));
        envVars.put("WRAPPER_VENDOR_ID", WineD3DConfigDialog.getVendorIdFromGPUName(this, gpuName));
    }

    String maxDeviceMemory = graphicsDriverConfig.get("maxDeviceMemory");
    if (maxDeviceMemory != null && Integer.parseInt(maxDeviceMemory) > 0)
        envVars.put("WRAPPER_VMEM_MAX_SIZE", maxDeviceMemory);
    
    String presentMode = graphicsDriverConfig.get("presentMode");
    if (presentMode != null) {
        if (presentMode.contains("immediate")) {
            envVars.put("WRAPPER_MAX_IMAGE_COUNT", "1");
        }
        envVars.put("MESA_VK_WSI_PRESENT_MODE", presentMode);
    }

    String resourceType = graphicsDriverConfig.get("resourceType");
    if (resourceType != null) envVars.put("WRAPPER_RESOURCE_TYPE", resourceType);

    String syncFrame = graphicsDriverConfig.get("syncFrame");
    if (syncFrame != null && syncFrame.equals("1"))
        envVars.put("MESA_VK_WSI_DEBUG", "forcesync");

    String disablePresentWait = graphicsDriverConfig.get("disablePresentWait");
    if (disablePresentWait != null) envVars.put("WRAPPER_DISABLE_PRESENT_WAIT", disablePresentWait);

    // Mali only: hidden extensions + PANVK_* driver variables. After the two settings above so the
    // extension hide builds on the user's blacklist and present-wait choice (returns at once elsewhere).
    String maliApplied = com.winhub.hernandez.core.MaliPanvk.applyToLaunchEnv(envVars, graphicsDriverConfig);
    if (maliApplied != null && wineDebugWriter != null) wineDebugWriter.println("XServerVulkan: " + maliApplied);

    // Wrapper + bcn_layer: leegao's bcn_layer implicit Vulkan layer OWNS the shared
    // ENABLE_BCN_COMPUTE / BCN_COMPUTE_AUTO vars when this driver is selected. Its env block
    // (below) takes precedence and the legacy wrapper bcnEmulation heuristic is SUPPRESSED so
    // the two paths can't emit contradictory values. The layer is gated by a hardcoded vendor
    // check (below): activated on non-Qualcomm GPUs (Mali/Xclipse/PowerVR) which lack native BCn,
    // and skipped on Adreno/Qualcomm which has native BCn.
    // "Wrapper + compat + bcn" is a bcn-family driver: it reuses the entire bcn_layer / vendor-gate
    // infrastructure below (same transcode half as "Wrapper + bcn_layer") and additionally activates
    // leegao's compat_layer for DX12 feature emulation on Valhall Mali (see isCompatDriver block).
    boolean isBcnLayerDriver = graphicsDriver != null
            && (graphicsDriver.startsWith("wrapper-bcn_layer") || graphicsDriver.startsWith("wrapper-compat-bcn"));
    boolean isCompatDriver = graphicsDriver != null && graphicsDriver.startsWith("wrapper-compat-bcn");
    // Per-game engine swap: run "Wrapper + compat + bcn" on the GameNative wrapper ICD (extracted
    // above) instead of the leegao ICD, to unlock DX12. When set we take the GameNative activation
    // path below and DO NOT activate the leegao bcn_layer / compat_layer implicit layers.
    boolean useGamenativeEngine = isCompatDriver && "1".equals(graphicsDriverConfig.get("compatUseGamenative"));

    // #132 Smart Wrapper Manager: an IMPORTED wrapper whose archive carries libbcn_layer.so also
    // drives the implicit bcn_layer, so it must run the same activation env (below) — otherwise the
    // BCn Layer Settings the dialog now SHOWS for that import would do nothing. Gate on the import's
    // DETECTED caps (WrapperManager.capsFor), and only for imports: a bundled driver's behavior is
    // decided by the name check above and is left untouched (e.g. wrapper-gamenative keeps its
    // WRAPPER_EMULATE_BCN path even though its caps include a BCn layer). The compat_layer path is
    // NOT activated here — that lives on feat/mali-ultimate-driver, not this branch.
    if (!isBcnLayerDriver && graphicsDriver != null) {
        WrapperManager wm = new WrapperManager(this);
        if (wm.isImported(graphicsDriver) && wm.capsFor(graphicsDriver).hasBcnLayer)
            isBcnLayerDriver = true;
    }

    if (!isBcnLayerDriver) {
        String bcnEmulation = graphicsDriverConfig.get("bcnEmulation");
        String bcnEmulationType = graphicsDriverConfig.get("bcnEmulationType");

        if (bcnEmulation != null) {
            // Adreno/Qualcomm (vendor 0x5143) has NATIVE BCn. The integrated-wrapper BCn
            // emulation (WRAPPER_EMULATE_BCN) is honored by the BCn-aware Wrapper-leegao/
            // Wrapper-gamenative builds shipped since 2.5 — on Adreno it is pure per-texture
            // overhead and can abort BC-heavy DX11 titles (e.g. Skyrim AE) on load. Force it
            // OFF on Qualcomm, mirroring the ENABLE_BCN_COMPUTE guards below and the bcn_layer
            // vendor gate. Only 0x5143 is skipped; Mali/Xclipse/PowerVR behaviour is unchanged.
            boolean isQualcomm = GPUInformation.getVendorID(null, null) == 0x5143;
            // BCn double-decode fix: "Type = compute" activates leegao's STANDALONE libbcn_layer.so
            // (via ENABLE_BCN_COMPUTE) — a full BCn decoder on its own. The wrapper ICD ALSO has its
            // own integrated BCn (WRAPPER_EMULATE_BCN). Before, auto/full set BOTH, so on a non-Qualcomm
            // GPU with the default auto+compute the container ran TWO BCn decoders at once (redundant
            // GPU/memory work + slower startup, and they don't coordinate). Make them mutually
            // exclusive: when the compute layer is active, force WRAPPER_EMULATE_BCN=0 so the standalone
            // layer is the ONLY decoder. "software" type keeps the wrapper's own emulation (layer off).
            // (Mirrors the "never both" rule already applied on the global ASTC/ETC2 toggle path.)
            boolean computeLayer = "compute".equals(bcnEmulationType) && !isQualcomm;
            switch (bcnEmulation) {
                case "auto" -> {
                    if (computeLayer) {
                        envVars.put("ENABLE_BCN_COMPUTE", "1");
                        envVars.put("BCN_COMPUTE_AUTO", "1");
                        envVars.put("WRAPPER_EMULATE_BCN", "0"); // layer decodes BCn; don't double up
                    } else {
                        envVars.put("WRAPPER_EMULATE_BCN", isQualcomm ? "0" : "3");
                    }
                }
                case "full" -> {
                    if (computeLayer) {
                        envVars.put("ENABLE_BCN_COMPUTE", "1");
                        envVars.put("BCN_COMPUTE_AUTO", "0");
                        envVars.put("WRAPPER_EMULATE_BCN", "0"); // layer decodes BCn; don't double up
                    } else {
                        envVars.put("WRAPPER_EMULATE_BCN", isQualcomm ? "0" : "2");
                    }
                }
                case "none" -> envVars.put("WRAPPER_EMULATE_BCN", "0");
                default -> envVars.put("WRAPPER_EMULATE_BCN", isQualcomm ? "0" : "1");
            }

            // BCn -> ASTC transcode target for the compute layer on the DEFAULT driver. The compute
            // path activates the implicit leegao bcn_layer (ENABLE_BCN_COMPUTE) but never exposed its
            // ASTC target here — only the explicit Wrapper + bcn_layer driver did — so Mali users had
            // to hand-add BCN_TRANSCODE_TO_ASTC. Honor the same bcnTranscodeAstc toggle now. Opt-in
            // (emit only when enabled, like WRAPPER_BCN_ASTC below) and only on the compute path, so
            // OFF leaves the previous behavior byte-identical.
            if (computeLayer && "1".equals(graphicsDriverConfig.get("bcnTranscodeAstc")))
                envVars.put("BCN_TRANSCODE_TO_ASTC", "1");
        }

        String bcnEmulationCache = graphicsDriverConfig.get("bcnEmulationCache");
        if (bcnEmulationCache != null) envVars.put("WRAPPER_USE_BCN_CACHE", bcnEmulationCache);

        // WRAPPER_BCN_ASTC — integrated-wrapper ASTC transcode target (Wrapper-gamenative build
        // honors this; older/non-BCn wrappers ignore it). Only emit when the user opts in.
        String bcnEmulationAstc = graphicsDriverConfig.get("bcnEmulationAstc");
        if ("1".equals(bcnEmulationAstc)) envVars.put("WRAPPER_BCN_ASTC", "1");
    }
    else {
        // === bcn_layer activation ===
        // Vendor gate (hardcoded, no user toggle): non-Qualcomm GPUs (Mali/Xclipse/PowerVR/...)
        // are the target case, so the layer is activated on them. Adreno/Qualcomm (0x5143) has
        // NATIVE BCn — transcode would be wasted overhead — so it is skipped there, exactly like
        // the legacy wrapper BCn block's own != 0x5143 guard.
        boolean activateBcnLayer = GPUInformation.getVendorID(null, null) != 0x5143;

        if (activateBcnLayer) {
        if (useGamenativeEngine) {
        // === GameNative engine (DX12) activation — Wrapper + compat + bcn, compatUseGamenative=1 ===
        // The extraction step already swapped the ICD base to wrapper-gamenative.tzst. Here we drive
        // that wrapper's env-var interface INSTEAD of the leegao bcn_layer/compat_layer: we deliberately
        // do NOT set ENABLE_BCN_COMPUTE / BCN_* / ENABLE_DXVK_MALI_COMPAT_LAYER, so the leegao implicit
        // layers (still shipping in extra_libs.tzst) stay dormant. BCn is handled by the GameNative
        // wrapper itself via WRAPPER_EMULATE_BCN.
        //
        // Still gated by the Valhall Mali (r32p1+) model allowlist — same floor as leegao's compat_layer.
        // The vendor gate (!= 0x5143, above) already excluded Adreno. On a borderline non-Valhall Mali (or
        // other non-Qualcomm GPU) fall back with a warning and emit NO DX12 override envs.
        String gnRenderer = GPUInformation.getRenderer(null, null);
        if (GPUInformation.isCompatLayerSupportedGpu(gnRenderer)) {
            // WRAPPER_VK_VERSION (=1.3.<patch>) is already set above and the GameNative wrapper reads it.
            // WRAPPER_SAFE_CREATE_DEVICE=1 — tolerate vkCreateDevice with features the old Mali blob under
            //   the wrapper doesn't natively expose (the wrapper emulates/masks them) so DXVK/VKD3D proceed.
            envVars.put("WRAPPER_SAFE_CREATE_DEVICE", "1");
            // WRAPPER_DRIVER_ID — parsed by atoi() in the shipped libvulkan_wrapper.so (getenv ->
            //   cbz-if-null -> atoi -> store int; verified by disassembly at .text 0xf400c). It is the
            //   NUMERIC VkDriverId enum value, NOT the name string. 24 = VK_DRIVER_ID_ARM_PROPRIETARY, so
            //   the wrapper advertises an ARM proprietary driverID to the D3D layer.
            envVars.put("WRAPPER_DRIVER_ID", "24");
            // WRAPPER_EMULATE_BCN=3 — GameNative's own auto BCn transcode (non-Qualcomm auto), replacing
            //   the leegao bcn_layer for texture decode. Values: 3=auto 2=full 1=default 0=off.
            envVars.put("WRAPPER_EMULATE_BCN", "3");
            // WRAPPER_DIAG=1 — the wrapper prints what it actually advertised (version/driverID/apiVersion)
            //   to the Wine debug log. Essential for our no-Mali-device iteration: it confirms the 1.3 +
            //   ARM_PROPRIETARY override took. Format: "[WRAPPER_DIAG] driver=%s (driverID=%u) ...".
            envVars.put("WRAPPER_DIAG", "1");
            Log.d("GraphicsDriverExtraction", "Wrapper + compat + bcn: GameNative engine (DX12) active on '"
                    + gnRenderer + "' — WRAPPER_SAFE_CREATE_DEVICE=1 WRAPPER_DRIVER_ID=24(ARM_PROPRIETARY)"
                    + " WRAPPER_EMULATE_BCN=3 WRAPPER_DIAG=1 (leegao bcn_layer/compat_layer left dormant)");
        } else {
            showToast(this, "Wrapper + compat + bcn: GameNative DX12 engine needs a Valhall Mali (r32p1+)"
                    + " GPU — it is disabled on this device (" + GPUInformation.extractModelName(gnRenderer)
                    + "). No DX12 overrides applied.");
            Log.w("GraphicsDriverExtraction", "GameNative DX12 engine disabled: GPU '" + gnRenderer
                    + "' is not on the Valhall (r32p1+) allowlist — no DX12 envs emitted");
        }
        } else {
        // ENABLE_BCN_COMPUTE is both the master switch and the loader enable-gate — always 1.
        envVars.put("ENABLE_BCN_COMPUTE", "1");

        // "Force decode on all GPUs" ON  -> BCN_COMPUTE_AUTO=0 (force, the Mali fix, default)
        //                            OFF -> BCN_COMPUTE_AUTO=1 (layer auto-detects)
        String bcnLayerAuto = graphicsDriverConfig.get("bcnLayerAuto");
        // config stores the toggle state: "1" == force-decode enabled. Default = force decode.
        boolean forceDecode = (bcnLayerAuto == null) || bcnLayerAuto.equals("1");
        envVars.put("BCN_COMPUTE_AUTO", forceDecode ? "0" : "1");

        // Two independent transcode targets (checkboxes 1:1 with env vars).
        String etc2 = graphicsDriverConfig.get("bcnTranscodeEtc2");
        envVars.put("BCN_TRANSCODE_TO_ETC2", "1".equals(etc2) ? "1" : "0");
        String astc = graphicsDriverConfig.get("bcnTranscodeAstc");
        envVars.put("BCN_TRANSCODE_TO_ASTC", "1".equals(astc) ? "1" : "0");

        // Storage image (1, default) vs staging buffer (0).
        String imageView = graphicsDriverConfig.get("bcnImageView");
        envVars.put("BCN_COMPUTE_IMAGE_VIEW", "0".equals(imageView) ? "0" : "1");

        // Optional debug log. The shader-v3 layer's logger writes to STDERR (not a file), which
        // Winlator captures via the Wine debug log (Settings > Logs > Enable Wine Debug). The old
        // BCN_LF/BCN_LL (file logging) and BCN_MAX_TEXTURE_SIZE were removed upstream in shader-v3.
        // shader-v3 only actually emits its transfer log when BOTH the log level AND the transfer
        // profiler are enabled (@kylinzang, #70). BCN_LAYER_LOG_LEVEL alone is silent — the profiler
        // is what drives the per-transfer log lines — so set the pair together.
        String debugLog = graphicsDriverConfig.get("bcnDebugLog");
        if ("1".equals(debugLog)) {
            envVars.put("BCN_LAYER_LOG_LEVEL", "info,error");
            envVars.put("BCN_PROFILE_TRANSFERS", "1");
        }

        // === compat_layer activation (Wrapper + compat + bcn only) ===
        // leegao's compat_layer emulates DX12/VKD3D feature levels down to D3D 12.0, but needs a
        // Valhall Mali (r32p1+). The != 0x5143 vendor gate above is necessary but NOT sufficient — a
        // Bifrost G52/G76 or sub-r32p1 part passes it yet fails compat's floor. Runtime driver-version
        // isn't probeable, so gate on the GPU MODEL allowlist (GPUInformation.isCompatLayerSupportedGpu).
        // On a supported GPU: enable the layer (+ optional sparse-binding). On a borderline non-Valhall
        // Mali (or any other non-Qualcomm GPU): leave the compat enable-var OFF — the bcn transcode half
        // above still runs exactly like "Wrapper + bcn_layer" — and warn the tester.
        if (isCompatDriver) {
            String renderer = GPUInformation.getRenderer(null, null);
            if (GPUInformation.isCompatLayerSupportedGpu(renderer)) {
                envVars.put("ENABLE_DXVK_MALI_COMPAT_LAYER", "1");
                // Auto-detect handles push/null descriptors; only sparse binding is a user opt-in.
                if ("1".equals(graphicsDriverConfig.get("bcnCompatSparse")))
                    envVars.put("COMPAT_EMULATE_SPARSE_BINDING", "1");
            }
            else {
                showToast(this, "Wrapper + compat + bcn: the DX12 compat layer needs a Valhall Mali (r32p1+)"
                        + " GPU — it is disabled on this device (" + GPUInformation.extractModelName(renderer)
                        + "). BCn texture transcode is still active.");
                Log.w("GraphicsDriverExtraction", "compat_layer disabled: GPU '" + renderer
                        + "' is not on the Valhall (r32p1+) allowlist");
            }
        }
        } // useGamenativeEngine ? GameNative DX12 path : leegao bcn_layer/compat_layer path
        } // activateBcnLayer
    }

    String fdDevFeatures = graphicsDriverConfig.get("fdDevFeatures");
    if (fdDevFeatures != null && fdDevFeatures.equals("1"))
        envVars.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");

    // ReShade (vkBasalt) — when a per-game/container effect is selected, write ONE merged conf
    // file that also folds in the CAS/DLS sharpness chain, and point the layer at it via the config
    // FILE env. When no ReShade effect is selected, fall back to the legacy inline CAS path
    // UNCHANGED (the existing sharpness feature keeps working exactly as before).
    String reshadeConf = writeVkBasaltConfig();
    if (reshadeConf != null) {
        envVars.put("ENABLE_VKBASALT", "1");
        envVars.put("VKBASALT_CONFIG_FILE", reshadeConf);
    }
    else if (vkbasaltConfig != null && !vkbasaltConfig.isEmpty()) {
        envVars.put("ENABLE_VKBASALT", "1");
        envVars.put("VKBASALT_CONFIG", vkbasaltConfig);
    }

    // #132 Smart Wrapper Manager, Layer 1: GENERIC emission for IMPORTED wrappers. For each env-var
    // NAME auto-detected from this wrapper's binaries (cached in its .meta), emit KEY=value from the
    // per-game config — EXCEPT keys a curated control already drives (HANDLED_ENV_KEYS) and any key the
    // block above already set (has() guard: belt-and-suspenders against double-emit / clobbering curated
    // env). Toggle "0" and empty values are off/default and skipped, so we only emit what the user
    // enabled or filled in. This is what activates an imported compat/DX12 or BCn wrapper generically
    // (e.g. ENABLE_DXVK_MALI_COMPAT_LAYER=1 + COMPAT_*) via the already-set VK_LAYER_PATH — no hardcoded
    // per-name logic. Bundled wrappers are untouched (isImported gate).
    if (graphicsDriver != null) {
        WrapperManager wmGeneric = new WrapperManager(this);
        if (wmGeneric.isImported(graphicsDriver)) {
            java.util.Set<String> hiddenKeys = wmGeneric.hiddenKeys(graphicsDriver);
            for (String key : wmGeneric.detectedEnvKeys(graphicsDriver)) {
                if (WrapperManager.HANDLED_ENV_KEYS.contains(key)) continue;
                if (WrapperManager.isDebugEnvKey(key)) continue;   // debug/diag plumbing -> never emit
                if (WrapperManager.isDriverInternalEnvKey(key)) continue; // Mesa/adrenotools driver internals
                if (hiddenKeys.contains(key)) continue;            // user hid it via Edit settings
                if (envVars.has(key)) continue; // never overwrite curated env
                String value = graphicsDriverConfig.get(key);
                if (value == null) continue;
                value = value.trim();
                if (value.isEmpty() || value.equals("0")) continue; // off / default -> don't emit
                envVars.put(key, value);
            }
        }
    }

    // "BCn layer: Fast" (Mali only): after every block above has set the BCn env, swap the bundled
    // leegao layer for Fcharan's build when it is about to run. No-op unless picked on a Mali GPU.
    String bcnFastApplied = com.winhub.hernandez.core.BcnFastLayer.applyToLaunchEnv(this, rootDir, envVars,
            graphicsDriverConfig);
    if (bcnFastApplied != null && wineDebugWriter != null) wineDebugWriter.println("XServerVulkan: " + bcnFastApplied);
}
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (inGameControlsEditor != null) {
            super.dispatchGenericMotionEvent(event);
            return true;
        }
        // A physical pad moving/pressing: the player is not using the pointer, so let the Wayland
        // overlay cursor hide (see waylandCursorPoke).
        if (waylandMode) {
            int src = event.getSource();
            if ((src & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK
                    || (src & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD) {
                waylandNotePadInput();
            }
        }
        if (isSteamControllerShadowEvent(event.getDevice())) return true;
        // The open menu gets the sticks / hat too (the framework turns the hat into D-pad keys).
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            return super.dispatchGenericMotionEvent(event);
        }
        // Controller-test isolation: while the Players popup is open, a game-controller AXIS event
        // drives ONLY the throwaway visualizer snapshot and is swallowed here — it never reaches
        // winHandler / touchpadView / the guest. Strictly gated on controllerTestActive so the normal
        // path below is byte-for-byte unchanged when the popup is closed.
        if (controllerTestActive && isControllerTestMotionEvent(event)) {
            controllerTestFeedMotionEvent(event);
            return true;
        }
        boolean handledByWinHandler = false;
        boolean handledByTouchpadView = false;

        // Let winHandler process the event if available
        if (winHandler != null) {
            handledByWinHandler = winHandler.onGenericMotionEvent(event);
            if (handledByWinHandler) {
                //Log.d("XServerDisplayActivity", "Event handled by winHandler");
            }
        }

        // Let touchpadView process the event if available
        if (touchpadView != null) {
            handledByTouchpadView = touchpadView.onExternalMouseEvent(event);
            if (handledByTouchpadView) {
                //Log.d("XServerDisplayActivity", "Event handled by touchpadView");
            }
        }

        // Pass the event to the super method to ensure system-level handling
        boolean handledBySuper = super.dispatchGenericMotionEvent(event);
        if (!handledBySuper) {
            //Log.d("XServerDisplayActivity", "Event not handled by super");
        }

        // Combine the results: any handler consuming the event indicates it was handled
        return handledByWinHandler || handledByTouchpadView || handledBySuper;
    }


    private static final int RECAPTURE_DELAY_MS = 10000; // 10 seconds

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (inGameControlsEditor != null) {
            super.dispatchKeyEvent(event);
            return true;
        }
        // A physical pad moving/pressing: the player is not using the pointer, so let the Wayland
        // overlay cursor hide (see waylandCursorPoke).
        if (waylandMode) {
            int src = event.getSource();
            if ((src & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK
                    || (src & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD) {
                waylandNotePadInput();
            }
        }
        if (isSteamControllerShadowEvent(event.getDevice())) return true;

        // While the menu is open it owns the keyboard and pad: A becomes the confirm key the Compose
        // controls understand, everything else goes to the focused control (B / Back close it).
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            KeyEvent menuEvent = event;
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
                menuEvent = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(),
                        KeyEvent.KEYCODE_DPAD_CENTER, event.getRepeatCount(), event.getMetaState(),
                        event.getDeviceId(), event.getScanCode(), event.getFlags(), event.getSource());
            }
            return super.dispatchKeyEvent(menuEvent);
        }

        // Controller-test isolation: while the Players popup is open, a game-controller BUTTON event
        // drives ONLY the throwaway visualizer snapshot and is swallowed here (before the drawer-open
        // hotkey handling below), so testing a pad can never drive the game. Non-controller keys (back,
        // volume, etc.) fall through untouched. Strictly gated on controllerTestActive.
        if (controllerTestActive && ExternalController.isGameController(event.getDevice())) {
            controllerTestFeedKeyEvent(event);
            return true;
        }

        // Wayland mode: route keyboard keys to wl_keyboard (the guest) instead of the X server.
        // Game controller buttons stay on the normal path below (WinHandler -> XInput, drawer
        // hotkeys), exactly like X11; only the sticks arrive as motion events, so sending the
        // buttons to wl_keyboard left pads with working sticks and dead A/B/X/Y.
        // Leave system keys (back/volume/home) to Android so the device still behaves normally.
        if (waylandMode && !ExternalController.isGameController(event.getDevice())) {
            int kc = event.getKeyCode();
            boolean systemKey = kc == KeyEvent.KEYCODE_BACK || kc == KeyEvent.KEYCODE_HOME
                    || kc == KeyEvent.KEYCODE_VOLUME_UP || kc == KeyEvent.KEYCODE_VOLUME_DOWN
                    || kc == KeyEvent.KEYCODE_VOLUME_MUTE || kc == KeyEvent.KEYCODE_BUTTON_MODE;
            if (!systemKey) {
                int evdev = androidKeyToEvdev(kc);
                // What the key stands for, and the key it would sit on without Shift - non-zero
                // only for a character Shift puts there, which is every capital and the symbol row.
                int ch = event.getUnicodeChar();
                int plain = ch > 0 ? unshiftedChar(ch) : 0;
                // A soft keyboard's symbol keys are in neither the table above nor a scan code, so
                // they reached the session as nothing at all and an EA sign-in took an address
                // without its @. Work back from the character instead: which key carries it.
                if (evdev <= 0 && ch > 0) {
                    int code = androidKeyToEvdev(keycodeForChar(plain != 0 ? plain : ch));
                    if (code > 0) evdev = code;
                }
                // And the modifier has to be made here rather than passed on. A soft keyboard
                // reports Shift in the event's meta state and sends no Shift key of its own, so a
                // capital arrives as a key this side already knows - which is why the fallback
                // above never saw it, and why every capital came out lowercase. A hardware
                // keyboard does send its own Shift, and giving it a second one would release the
                // modifier while the key is still physically held.
                int shiftEvdev = (evdev > 0 && plain != 0 && event.getDeviceId() <= 0) ? 42 : 0;
                if (evdev <= 0 && event.getScanCode() > 0) evdev = event.getScanCode();
                if (evdev > 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (shiftEvdev != 0)
                            com.winhub.hernandez.wayland.WaylandCompositor.nativeSendKey(shiftEvdev, 1);
                        com.winhub.hernandez.wayland.WaylandCompositor.nativeSendKey(evdev, 1);
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        com.winhub.hernandez.wayland.WaylandCompositor.nativeSendKey(evdev, 0);
                        if (shiftEvdev != 0)
                            com.winhub.hernandez.wayland.WaylandCompositor.nativeSendKey(shiftEvdev, 0);
                    }
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }

        // The Home / Steam / Select buttons are kept away from Android's own handling, but they
        // still have to arrive somewhere: in a Linux session the Steam button is how the client
        // opens its in-game menu, and it only gets there as part of the pad state the client
        // reads. So each is offered to the profile bindings, then to the pad, then to the
        // keyboard, instead of the result being computed and dropped.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int homeKc = event.getKeyCode();
            if (homeKc == KeyEvent.KEYCODE_BUTTON_MODE || homeKc == KeyEvent.KEYCODE_HOME
                    || homeKc == KeyEvent.KEYCODE_BUTTON_SELECT) {
                boolean handled = inputControlsView != null && inputControlsView.onKeyEvent(event);
                if (!handled && winHandler != null) handled = winHandler.onKeyEvent(event);
                if (!handled && xServer != null) xServer.keyboard.onKeyEvent(event);
                return true;
            }
        }

        // Fallback to existing input handling
        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event) && xServer.keyboard.onKeyEvent(event)) ||
                (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
    }

    /** Map an Android KeyEvent keyCode to a Linux evdev keycode (for wl_keyboard in wayland mode).
     *  Returns -1 if unmapped (caller falls back to KeyEvent.getScanCode() for HW keyboards). */
    /**
     * The character a key carries when Shift is not held, for a character that needs it - so '@'
     * gives '2' and 'A' gives 'a'. Returns 0 when the character is typed without Shift, which is
     * also the answer for anything this layout does not place on a key.
     */
    private static int unshiftedChar(int ch) {
        if (ch >= 'A' && ch <= 'Z') return Character.toLowerCase(ch);
        int at = SHIFTED_CHARS.indexOf(ch);
        return at >= 0 ? PLAIN_CHARS.charAt(at) : 0;
    }

    // Index-aligned: the symbol, and the key it shares with Shift held. US layout, which is what
    // the session's keymap is.
    private static final String SHIFTED_CHARS = "!@#$%^&*()_+{}|:\"<>?~";
    private static final String PLAIN_CHARS   = "1234567890-=[]\\;',./`";

    /** The Android key code that carries an unshifted character, or 0 when nothing does. */
    private static int keycodeForChar(int ch) {
        if (ch >= 'a' && ch <= 'z') return KeyEvent.KEYCODE_A + (ch - 'a');
        if (ch >= '0' && ch <= '9') return KeyEvent.KEYCODE_0 + (ch - '0');
        switch (ch) {
            case '-':  return KeyEvent.KEYCODE_MINUS;
            case '=':  return KeyEvent.KEYCODE_EQUALS;
            case '[':  return KeyEvent.KEYCODE_LEFT_BRACKET;
            case ']':  return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case '\\': return KeyEvent.KEYCODE_BACKSLASH;
            case ';':  return KeyEvent.KEYCODE_SEMICOLON;
            case '\'': return KeyEvent.KEYCODE_APOSTROPHE;
            case ',':  return KeyEvent.KEYCODE_COMMA;
            case '.':  return KeyEvent.KEYCODE_PERIOD;
            case '/':  return KeyEvent.KEYCODE_SLASH;
            case '`':  return KeyEvent.KEYCODE_GRAVE;
            case ' ':  return KeyEvent.KEYCODE_SPACE;
            default:   return 0;
        }
    }

    private static int androidKeyToEvdev(int kc) {
        switch (kc) {
            // Letters (evdev order is NOT alphabetical)
            case KeyEvent.KEYCODE_A: return 30; case KeyEvent.KEYCODE_B: return 48;
            case KeyEvent.KEYCODE_C: return 46; case KeyEvent.KEYCODE_D: return 32;
            case KeyEvent.KEYCODE_E: return 18; case KeyEvent.KEYCODE_F: return 33;
            case KeyEvent.KEYCODE_G: return 34; case KeyEvent.KEYCODE_H: return 35;
            case KeyEvent.KEYCODE_I: return 23; case KeyEvent.KEYCODE_J: return 36;
            case KeyEvent.KEYCODE_K: return 37; case KeyEvent.KEYCODE_L: return 38;
            case KeyEvent.KEYCODE_M: return 50; case KeyEvent.KEYCODE_N: return 49;
            case KeyEvent.KEYCODE_O: return 24; case KeyEvent.KEYCODE_P: return 25;
            case KeyEvent.KEYCODE_Q: return 16; case KeyEvent.KEYCODE_R: return 19;
            case KeyEvent.KEYCODE_S: return 31; case KeyEvent.KEYCODE_T: return 20;
            case KeyEvent.KEYCODE_U: return 22; case KeyEvent.KEYCODE_V: return 47;
            case KeyEvent.KEYCODE_W: return 17; case KeyEvent.KEYCODE_X: return 45;
            case KeyEvent.KEYCODE_Y: return 21; case KeyEvent.KEYCODE_Z: return 44;
            // Digit row
            case KeyEvent.KEYCODE_1: return 2;  case KeyEvent.KEYCODE_2: return 3;
            case KeyEvent.KEYCODE_3: return 4;  case KeyEvent.KEYCODE_4: return 5;
            case KeyEvent.KEYCODE_5: return 6;  case KeyEvent.KEYCODE_6: return 7;
            case KeyEvent.KEYCODE_7: return 8;  case KeyEvent.KEYCODE_8: return 9;
            case KeyEvent.KEYCODE_9: return 10; case KeyEvent.KEYCODE_0: return 11;
            // Whitespace / edit
            case KeyEvent.KEYCODE_ENTER: return 28; case KeyEvent.KEYCODE_NUMPAD_ENTER: return 28;
            case KeyEvent.KEYCODE_SPACE: return 57; case KeyEvent.KEYCODE_TAB: return 15;
            case KeyEvent.KEYCODE_DEL: return 14; /* backspace */
            case KeyEvent.KEYCODE_FORWARD_DEL: return 111; case KeyEvent.KEYCODE_ESCAPE: return 1;
            // Modifiers
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 42; case KeyEvent.KEYCODE_SHIFT_RIGHT: return 54;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 29; case KeyEvent.KEYCODE_CTRL_RIGHT: return 97;
            case KeyEvent.KEYCODE_ALT_LEFT: return 56; case KeyEvent.KEYCODE_ALT_RIGHT: return 100;
            case KeyEvent.KEYCODE_CAPS_LOCK: return 58;
            // Arrows / nav
            case KeyEvent.KEYCODE_DPAD_UP: return 103; case KeyEvent.KEYCODE_DPAD_DOWN: return 108;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 105; case KeyEvent.KEYCODE_DPAD_RIGHT: return 106;
            case KeyEvent.KEYCODE_MOVE_HOME: return 102; case KeyEvent.KEYCODE_MOVE_END: return 107;
            case KeyEvent.KEYCODE_PAGE_UP: return 104; case KeyEvent.KEYCODE_PAGE_DOWN: return 109;
            case KeyEvent.KEYCODE_INSERT: return 110;
            // Punctuation
            case KeyEvent.KEYCODE_GRAVE: return 41; case KeyEvent.KEYCODE_MINUS: return 12;
            case KeyEvent.KEYCODE_EQUALS: return 13; case KeyEvent.KEYCODE_LEFT_BRACKET: return 26;
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return 27; case KeyEvent.KEYCODE_BACKSLASH: return 43;
            case KeyEvent.KEYCODE_SEMICOLON: return 39; case KeyEvent.KEYCODE_APOSTROPHE: return 40;
            case KeyEvent.KEYCODE_SLASH: return 53; case KeyEvent.KEYCODE_COMMA: return 51;
            case KeyEvent.KEYCODE_PERIOD: return 52;
            // Function row
            case KeyEvent.KEYCODE_F1: return 59; case KeyEvent.KEYCODE_F2: return 60;
            case KeyEvent.KEYCODE_F3: return 61; case KeyEvent.KEYCODE_F4: return 62;
            case KeyEvent.KEYCODE_F5: return 63; case KeyEvent.KEYCODE_F6: return 64;
            case KeyEvent.KEYCODE_F7: return 65; case KeyEvent.KEYCODE_F8: return 66;
            case KeyEvent.KEYCODE_F9: return 67; case KeyEvent.KEYCODE_F10: return 68;
            case KeyEvent.KEYCODE_F11: return 87; case KeyEvent.KEYCODE_F12: return 88;
            default: return -1;
        }
    }

    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    // ---- Steam Controller support (SDL3 HIDAPI, see SteamControllerBackend) ----

    /** Starts SDL for this session when Input Controls → Device → Steam Controller is on. Off (the
     *  default) = never loaded. SDL pads join WinHandler's slots like hot-plugged pads; their state
     *  follows the same routing as a physical pad (visualizer only while the Players test is open,
     *  nothing while the in-game controls editor is open). Main thread. */
    private void startSteamControllerSupport() {
        if (steamControllerBackend != null || winHandler == null || isFinishing()) return;
        if (!com.winhub.hernandez.ui.components.GlobalControllerPrefs.isSteamControllerEnabled(this)) return;
        int trackpadMode = com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSteamTrackpadMouseMode(this);
        com.winhub.hernandez.inputcontrols.Binding[] paddles =
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSteamPaddleBindings(this);
        SteamControllerBackend backend = new SteamControllerBackend(this, trackpadMode, paddles, new SteamControllerBackend.Listener() {
            @Override
            public void onSteamPadConnected(ExternalController pad) {
                if (winHandler != null) winHandler.onSdlPadConnected(pad);
            }

            @Override
            public void onSteamPadDisconnected(ExternalController pad) {
                if (inputControlsView != null) inputControlsView.onSteamPadDisconnected(pad);
                if (winHandler != null) winHandler.onSdlPadDisconnected(pad);
            }

            @Override
            public void onSteamPadState(ExternalController pad, boolean guideDown, boolean quickAccessDown, int[] pressedKeyCodes) {
                if (inGameControlsEditor != null) return;
                if (controllerTestActive) {
                    controllerTestController.state.copy(pad.state);
                    controllerTestGuideDown = guideDown;
                    controllerTestPublishSteamPad(pad, quickAccessDown);
                    return;
                }
                // The profile's Default / Any Controller bindings, like an unconfigured Android pad;
                // raw state when there are none.
                if (inputControlsView != null && inputControlsView.onSteamPadState(pad, pressedKeyCodes)) return;
                if (winHandler != null) winHandler.sendGamepadState(pad);
            }

            @Override
            public void onSteamPadBinding(com.winhub.hernandez.inputcontrols.Binding binding, boolean down) {
                // Back button mapped to a key / mouse button. Always deliver a release so nothing sticks.
                if (down && (inGameControlsEditor != null || controllerTestActive)) return;
                if (inputControlsView != null) inputControlsView.handleInputEvent(binding, down);
            }

            @Override
            public void onSteamPadMouseMove(int dx, int dy) {
                if (inGameControlsEditor != null || controllerTestActive) return;
                if (winHandler != null) winHandler.steamPadMouseMove(dx, dy);
            }

            @Override
            public void onSteamPadMouseButton(boolean secondary, boolean down) {
                // Always deliver a release so a click held while a panel opens can't stick.
                if (down && (inGameControlsEditor != null || controllerTestActive)) return;
                if (winHandler != null) winHandler.steamPadMouseButton(secondary, down);
            }
        });
        if (!backend.start()) return;
        steamControllerBackend = backend;
        winHandler.setSteamControllerBackend(backend);
    }

    private void stopSteamControllerSupport() {
        if (steamControllerBackend == null) return;
        if (winHandler != null) winHandler.setSteamControllerBackend(null);
        steamControllerBackend.stop();
        steamControllerBackend = null;
    }

    /** While SDL owns a Steam Controller, whatever Android still reports for it (its keyboard/mouse
     *  "lizard mode", or a HID gamepad collection) is the same physical pad: swallow it so it can't
     *  type, click or take a second player slot. Valve devices pass through untouched otherwise. */
    private boolean isSteamControllerShadowEvent(android.view.InputDevice device) {
        return steamControllerBackend != null && winHandler != null && winHandler.hasSdlPads()
                && device != null && device.getVendorId() == SteamControllerBackend.VALVE_VENDOR_ID;
    }

    /** controllerTestPublishSnapshot for a Steam Controller read through SDL (no InputDevice). */
    private void controllerTestPublishSteamPad(ExternalController pad, boolean quickAccess) {
        com.winhub.hernandez.inputcontrols.GamepadState st = controllerTestController.state;
        XServerDialogState.INSTANCE.setControllerTestSnapshot(new com.winhub.hernandez.ui.controllertest.ControllerTestSnapshot(
                st.buttons & 0xFFFF,
                st.dpad[0], st.dpad[1], st.dpad[2], st.dpad[3],
                st.thumbLX, st.thumbLY, st.thumbRX, st.thumbRY,
                st.triggerL, st.triggerR,
                controllerTestGuideDown,
                pad.getDeviceId(),
                pad.getName() != null ? pad.getName() : "Steam Controller",
                com.winhub.hernandez.ui.controllertest.PadArt.STEAM.ordinal(),
                -1,
                true,
                quickAccess));
    }

    // ---- Controller-test panel input fork (gated on controllerTestActive; see field docs) ----

    /** True for a motion event that belongs to a game controller (joystick/gamepad source + a real
     *  controller device). Only these are forked to the visualizer and swallowed while test mode is on;
     *  mouse / other sources are left to the normal path. */
    private boolean isControllerTestMotionEvent(MotionEvent event) {
        int src = event.getSource();
        boolean joystickish = (src & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK
                || (src & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD;
        return joystickish && ExternalController.isGameController(event.getDevice());
    }

    /** Fork a controller AXIS event into the throwaway snapshot (never the guest). Also emits the
     *  folded-in axis-fall-through DIAGNOSTIC (throttled to 1/sec): if lines under the "ControllerTest"
     *  logcat tag appear while the popup is open, joystick motion IS reaching this Activity through the
     *  non-focusable Dialog on the tester's device (the top-risk unknown). Buttons visualize regardless
     *  (KEY dispatch is reliable); sticks/triggers degrade gracefully if axis fall-through fails here. */
    private void controllerTestFeedMotionEvent(MotionEvent event) {
        if (ExternalController.isJoystickDevice(event)) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastControllerTestAxisLogMs > 1000L) {
                lastControllerTestAxisLogMs = now;
                Log.d("ControllerTest", "axis dispatch reached src=" + event.getSource()
                        + " dev=" + event.getDeviceId()
                        + " LX=" + event.getAxisValue(MotionEvent.AXIS_X)
                        + " LY=" + event.getAxisValue(MotionEvent.AXIS_Y)
                        + " RX=" + event.getAxisValue(MotionEvent.AXIS_Z)
                        + " RY=" + event.getAxisValue(MotionEvent.AXIS_RZ));
            }
        }
        controllerTestController.updateStateFromMotionEvent(event);
        controllerTestPublishSnapshot(event.getDevice());
    }

    /** Fork a controller BUTTON event into the throwaway snapshot (never the guest). */
    private void controllerTestFeedKeyEvent(KeyEvent event) {
        if (event.getRepeatCount() == 0) {
            controllerTestController.updateStateFromKeyEvent(event);
        }
        // Guide/Home is NOT part of GamepadState's button bitfield — track it separately for the picture.
        int kc = event.getKeyCode();
        if (kc == KeyEvent.KEYCODE_BUTTON_MODE || kc == KeyEvent.KEYCODE_HOME) {
            controllerTestGuideDown = event.getAction() == KeyEvent.ACTION_DOWN;
        }
        controllerTestPublishSnapshot(event.getDevice());
    }

    /** Push the throwaway controller state to the Compose visualizer. Reads ONLY the snapshot copy —
     *  never winHandler, so the guest is untouched. Main-thread only. */
    private void controllerTestPublishSnapshot(android.view.InputDevice device) {
        com.winhub.hernandez.inputcontrols.GamepadState st = controllerTestController.state;
        int battery = -1;
        if (device != null && android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.hardware.BatteryState bs = device.getBatteryState();
                if (bs != null && bs.isPresent()) {
                    float cap = bs.getCapacity();
                    if (cap >= 0f) battery = Math.round(cap * 100f);
                }
            } catch (Throwable ignored) { }
        }
        boolean hasVibrator = false;
        if (device != null) {
            android.os.Vibrator vib = device.getVibrator();
            hasVibrator = vib != null && vib.hasVibrator();
        }
        XServerDialogState.INSTANCE.setControllerTestSnapshot(new com.winhub.hernandez.ui.controllertest.ControllerTestSnapshot(
                st.buttons & 0xFFFF,
                st.dpad[0], st.dpad[1], st.dpad[2], st.dpad[3],
                st.thumbLX, st.thumbLY, st.thumbRX, st.thumbRY,
                st.triggerL, st.triggerR,
                controllerTestGuideDown,
                device != null ? device.getId() : -1,
                device != null && device.getName() != null ? device.getName() : "",
                com.winhub.hernandez.ui.controllertest.ControllerTestVisualizerKt.classifyPadArt(device).ordinal(),
                battery,
                hasVibrator));
    }

    /** Snapshot the current input-device/slot state as UI rows. Main-thread only (reads
     *  WinHandler.getPlayerSlotAssignments). Shared by the Players sub-tab refresh and the toast. */
    private java.util.List<XServerDialogState.PlayerSlotRow> buildPlayerSlotRows() {
        java.util.List<com.winhub.hernandez.winhandler.WinHandler.PlayerSlotInfo> infos =
                winHandler.getPlayerSlotAssignments();
        java.util.List<XServerDialogState.PlayerSlotRow> uiRows = new java.util.ArrayList<>();
        for (com.winhub.hernandez.winhandler.WinHandler.PlayerSlotInfo info : infos) {
            uiRows.add(new XServerDialogState.PlayerSlotRow(
                    info.displayName, info.descriptor, info.currentSlot,
                    info.override, info.isOnScreen, info.isGameController,
                    winHandler.slotHasVibrator(info.currentSlot)));
        }
        return uiRows;
    }

    /** Build + show the in-game controller-status toast for an event. Main-thread only. reason ∈
     *  {"launch","connected","disconnected","reassign","reset"}; changedDescriptor marks a hot-plugged
     *  row NEW (may be null). */
    private void showControllerStatusToast(String reason, String changedDescriptor) {
        if (winHandler == null) return;
        XServerDialogState.INSTANCE.showControllerToastFor(reason, buildPlayerSlotRows(), changedDescriptor);
    }

    protected boolean isInGameControlsEditorOpen() {
        return inGameControlsEditor != null;
    }

    private static final String TAG = "DXWrapperExtraction";

    // Fold the chosen d7vk version into the dxwrapper signature string (compared against the container's
    // stored value) so switching d7vk versions — which leaves the ddrawrapper token unchanged as "d7vk" —
    // still re-triggers extraction. Appended as a trailing ";d7vk=<ver>" field the split-based apply
    // code (which only reads indices 0..2) safely ignores. Empty for any non-d7vk wrapper.
    private String d7vkMarker(String ddrawrapper) {
        if (!"d7vk".equals(ddrawrapper) || dxwrapperConfig == null) return "";
        String ver = dxwrapperConfig.get("d7vkVersion");
        return ";d7vk=" + (ver == null ? "" : ver);
    }

    // Materialize the selected d7vk into the prefix: a downloaded CONTENT_TYPE_D7VK profile (its .wcp
    // drops syswow64/ddraw.dll) when the user picked one, otherwise the bundled offline d7vk.tzst asset.
    // Callers have already restored the builtin ddraw + copied it aside as the ddraw_.dll proxy target.
    private void applyD7vk(File windowsDir) {
        String d7vkVersion = (dxwrapperConfig != null) ? dxwrapperConfig.get("d7vkVersion") : null;
        ContentProfile d7vkProfile = findD7vkProfile(d7vkVersion);
        if (d7vkProfile != null) {
            Log.d(TAG, "Applying user-defined d7vk content profile: " + d7vkVersion);
            contentsManager.applyContent(d7vkProfile);
        } else {
            Log.d(TAG, "Extracting bundled d7vk .tzst archive");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/d7vk.tzst", windowsDir, onExtractFileListener);
        }
    }

    // Resolve a stored d7vkVersion ("verName-verCode") to an installed profile; null = use the bundled
    // asset (empty/absent value, the "Bundled (default)" sentinel, or the profile isn't installed).
    private ContentProfile findD7vkProfile(String version) {
        if (version == null || version.isEmpty()
                || version.equals(DXVKConfigDialog.D7VK_BUNDLED) || contentsManager == null) return null;
        List<ContentProfile> profiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_D7VK);
        if (profiles == null) return null;
        for (ContentProfile p : profiles) {
            if ((p.verName + "-" + p.verCode).equals(version) && ContentsManager.getInstallDir(this, p).exists())
                return p;
        }
        return null;
    }

    private boolean extractDXWrapperFiles(String dxwrapper) {
        final String[] dlls = {"d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll", "d3d8.dll", "d3d9.dll", "dxgi.dll", "ddraw.dll", "d3dimm.dll"};

        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");

        if (dxwrapper.contains("dxvk")) {
            Log.d(TAG, "Extracting DXVK wrapper files, version: " + dxwrapper);

            String dxvkWrapper = dxwrapper.split(";")[0];
            String vkd3dWrapper = dxwrapper.split(";")[1];
            String ddrawrapper = dxwrapper.split(";")[2];
            
            ContentProfile dxvkProfile = contentsManager.getProfileByEntryName(dxvkWrapper);
            if (dxvkProfile != null) {
                Log.d(TAG, "Applying user-defined DXVK content profile: " + dxvkWrapper);
                contentsManager.applyContent(dxvkProfile);
            } else {
                Log.d(TAG, "Extracting fallback DXVK .tzst archive: " + dxvkWrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + dxvkWrapper + ".tzst", windowsDir, onExtractFileListener);

                if (compareVersion(dxvkWrapper, "2.4") < 0) {
                    Log.d(TAG, "Extracting d8vk as part of DXVK version " + dxvkWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d8vk-" + DefaultVersion.D8VK + ".tzst", windowsDir, onExtractFileListener);
                }
            }

            boolean vkd3dOk;
            if (vkd3dWrapper.contains("None")) {
                Log.d(TAG, "No VKD3D has been selected, restoring original d3d12");
                restoreOriginalDllFiles(new String[]{"d3d12.dll", "d3d12core.dll"});
                vkd3dOk = true;
            } else {
                ContentProfile vkd3dProfile = contentsManager.getProfileByEntryName(vkd3dWrapper);
                if (vkd3dProfile != null) {
                    Log.d(TAG, "Applying user-defined VKD3D content profile: " + vkd3dWrapper);
                    contentsManager.applyContent(vkd3dProfile);
                    vkd3dOk = true;
                } else {
                    Log.d(TAG, "Extracting fallback VKD3D .tzst archive: " + vkd3dWrapper);
                    vkd3dOk = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + vkd3dWrapper + ".tzst", windowsDir, onExtractFileListener);
                    if (!vkd3dOk) Log.e(TAG, "VKD3D extraction failed: " + vkd3dWrapper);
                }
            }
            if (!vkd3dOk) return false;

            Log.d(TAG, "Extracting nglide wrapper");
TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/nglide.tzst", windowsDir, onExtractFileListener);

if (ddrawrapper.contains("None")) {
    Log.d(TAG, "No DDRaw wrapper has been selected, restoring original ddraw files");
    restoreOriginalDllFiles(new String[]{ "ddraw.dll", "d3dimm.dll" });
}
else {
    if (ddrawrapper.equals("cnc-ddraw")) {
        envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\windows\\syswow64\\ddraw.ini");
    }
    // Fixed: Ensure no hidden characters (\u200b) exist before 'else if'
    else if (ddrawrapper.equals("dgvoodoo")) {
        Log.d(TAG, "Applying dgvoodoo ddrawrapper");
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/dgvoodoo.tzst", windowsDir, onExtractFileListener);
    }
    else if (ddrawrapper.equals("d7vk")) {
        Log.d(TAG, "Applying d7vk ddrawrapper");
        // d7vk's ddraw.dll proxies unimplemented (2D/GDI) DirectDraw calls to a
        // renamed builtin ddraw_.dll (see "GetProxiedDDrawModule: Loaded ddraw_.dll").
        // Restore the genuine wine builtin, copy it aside as the proxy target, then
        // drop d7vk's native ddraw.dll over the top (d3dimm stays builtin).
        File d7vkSyswow64 = new File(windowsDir, "syswow64");
        new File(d7vkSyswow64, "ddraw_.dll").delete();
        restoreOriginalDllFiles("ddraw.dll", "d3dimm.dll");
        File d7vkBuiltinDdraw = new File(d7vkSyswow64, "ddraw.dll");
        if (d7vkBuiltinDdraw.exists()) FileUtils.copy(d7vkBuiltinDdraw, new File(d7vkSyswow64, "ddraw_.dll"));
        applyD7vk(windowsDir);
    }

    Log.d(TAG, "Extracting ddrawrapper " + ddrawrapper);
    // Only extract if it wasn't already handled specifically above
    if (!ddrawrapper.equals("dgvoodoo") && !ddrawrapper.equals("d7vk")) {
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/" + ddrawrapper + ".tzst", windowsDir, onExtractFileListener);
    }
}

Log.d(TAG, "Finished extraction of DXVK wrapper files, version: " + dxwrapper);
return true;
} else if (dxwrapper.contains("vegas")) {
    Log.d(TAG, "Extracting VEGAS wrapper files: " + dxwrapper);

    String[] parts = dxwrapper.split(";");
    String vegasWrapper = parts[0];
    String vkd3dWrapper = parts.length > 1 ? parts[1] : "";
    String ddrawrapper = parts.length > 2 ? parts[2] : "";

    // Extract vegas DLL archive
    // vegas WCPs use CONTENT_TYPE_VEGAS, verName like "vegas-2.7.3"
    // getProfileByEntryName("vegas-2.7.3") can't resolve because the installed
    // profile has verName="vegas-2.7.3" and verCode≥1, so we search manually.
    ContentProfile vegasProfile = contentsManager.getProfileByEntryName(vegasWrapper);
    if (vegasProfile == null) {
        String needVersion = vegasWrapper.substring("vegas-".length());
        Log.d(TAG, "Searching VEGAS profiles for version: " + needVersion);
        for (ContentProfile p : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VEGAS)) {
            String pVer = (p.verName != null && p.verName.startsWith("vegas-"))
                    ? p.verName.substring("vegas-".length()) : p.verName;
            if (needVersion.equals(pVer)) {
                vegasProfile = p;
                Log.d(TAG, "Found matching VEGAS content profile: " + ContentsManager.getEntryName(p));
                break;
            }
        }
    }
    if (vegasProfile != null) {
        Log.d(TAG, "Applying user-defined VEGAS content profile: " + vegasWrapper);
        contentsManager.applyContent(vegasProfile);
    } else {
        Log.d(TAG, "Extracting fallback VEGAS .tzst archive: " + vegasWrapper);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + vegasWrapper + ".tzst", windowsDir, onExtractFileListener);
    }

    // Extract VKD3D if part of vegas+vkd3d combo
    boolean hasVkd3d = vkd3dWrapper != null && !vkd3dWrapper.isEmpty() && !vkd3dWrapper.contains("None");
    if (hasVkd3d) {
        Log.d(TAG, "Extracting VKD3D wrapper files for VEGAS combo: " + vkd3dWrapper);
        ContentProfile vkd3dProfile = contentsManager.getProfileByEntryName(vkd3dWrapper);
        if (vkd3dProfile != null) {
            Log.d(TAG, "Applying user-defined VKD3D content profile: " + vkd3dWrapper);
            contentsManager.applyContent(vkd3dProfile);
        } else {
            Log.d(TAG, "Extracting VKD3D .tzst archive: " + vkd3dWrapper);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + vkd3dWrapper + ".tzst", windowsDir, onExtractFileListener);
        }
    } else {
        // Restore original d3d12 (vanilla vegas does not include VKD3D)
        restoreOriginalDllFiles(new String[]{"d3d12.dll", "d3d12core.dll"});
    }

    // Extract nglide
    Log.d(TAG, "Extracting nglide wrapper");
    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/nglide.tzst", windowsDir, onExtractFileListener);

    // Handle ddrawrapper
    if (ddrawrapper.contains("None")) {
        Log.d(TAG, "No DDraw wrapper selected, restoring original ddraw files");
        restoreOriginalDllFiles(new String[]{ "ddraw.dll", "d3dimm.dll" });
    }
    else {
        if (ddrawrapper.equals("cnc-ddraw")) {
            envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\windows\\syswow64\\ddraw.ini");
        }
        else if (ddrawrapper.equals("dgvoodoo")) {
            Log.d(TAG, "Applying dgvoodoo ddrawrapper");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/dgvoodoo.tzst", windowsDir, onExtractFileListener);
        }
        else if (ddrawrapper.equals("d7vk")) {
            Log.d(TAG, "Applying d7vk ddrawrapper");
            // See site above: d7vk proxies to a renamed builtin ddraw_.dll.
            File d7vkSyswow64 = new File(windowsDir, "syswow64");
            new File(d7vkSyswow64, "ddraw_.dll").delete();
            restoreOriginalDllFiles("ddraw.dll", "d3dimm.dll");
            File d7vkBuiltinDdraw = new File(d7vkSyswow64, "ddraw.dll");
            if (d7vkBuiltinDdraw.exists()) FileUtils.copy(d7vkBuiltinDdraw, new File(d7vkSyswow64, "ddraw_.dll"));
            applyD7vk(windowsDir);
        }

        Log.d(TAG, "Extracting ddrawrapper " + ddrawrapper);
        if (!ddrawrapper.equals("dgvoodoo") && !ddrawrapper.equals("d7vk")) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/" + ddrawrapper + ".tzst", windowsDir, onExtractFileListener);
        }
    }

    Log.d(TAG, "Finished extraction of VEGAS wrapper files: " + dxwrapper);
    return true;
} else if (dxwrapper.contains("wined3d")) {
    Log.d(TAG, "Restoring original DLL files for wined3d.");
    restoreOriginalDllFiles(dlls);
        }
        return true;
    }

    private static int compareVersion(String varA, String varB) {
        int[] a = parseSemverLoose(varA);
        int[] b = parseSemverLoose(varB);

        if (a[0] != b[0]) return a[0] - b[0];
        if (a[1] != b[1]) return a[1] - b[1];
        return a[2] - b[2];
    }

    private static final Pattern SEMVER_LOOSE =
            Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static int[] parseSemverLoose(String s) {
        if (s == null) return new int[]{0, 0, 0};

        Matcher m = SEMVER_LOOSE.matcher(s);

        String g1 = null, g2 = null, g3 = null;
        while (m.find()) {
            g1 = m.group(1);
            g2 = m.group(2);
            g3 = m.group(3);
        }

        if (g1 == null || g2 == null) {
            return new int[]{0, 0, 0};
        }

        int major = safeParseInt(g1);
        int minor = safeParseInt(g2);
        int patch = safeParseInt(g3);
        return new int[]{major, minor, patch};
    }

    private static int safeParseInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
    
    private void extractWinComponentFiles() {
        Log.d("XServerDisplayActivity", "Extracting WinComponents");
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX+"/drive_c/windows");
        File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/system.reg");

        try {
            JSONObject wincomponentsJSONObject = new JSONObject(FileUtils.readString(this, "wincomponents/wincomponents.json"));
            ArrayList<String> dlls = new ArrayList<>();
            String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();

            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                if (wincomponent[1].equals(oldWinComponentsIter.next()[1]) && !firstTimeBoot) continue;
                String identifier = wincomponent[0];
                boolean useNative = wincomponent[1].equals("1");

                if (useNative) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/"+identifier+".tzst", windowsDir, onExtractFileListener);
                }
                else {
                    JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);
                    for (int i = 0; i < dlnames.length(); i++) {
                        String dlname = dlnames.getString(i);
                        dlls.add(!dlname.endsWith(".exe") ? dlname+".dll" : dlname);
                    }
                }
                Log.d("XServerDisplayActivity", "Setting wincomponent " + identifier + " to " + String.valueOf(useNative));
                WineUtils.overrideWinComponentDlls(this, container, identifier, useNative);
                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative, this);
            }

            if (!dlls.isEmpty()) restoreOriginalDllFiles(dlls.toArray(new String[0]));
        }
        catch (JSONException e) {}
    }

    private void restoreOriginalDllFiles(final String... dlls) {
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX+"/drive_c/windows");
        File system32dlls = null;
        File syswow64dlls = null;

        if (wineInfo.isArm64EC())
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/aarch64-windows");
        else
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/x86_64-windows");

        syswow64dlls = new File(imageFs.getWinePath() + "/lib/wine/i386-windows");


        for (String dll : dlls) {
            File srcFile = new File(system32dlls, dll);
            File dstFile = new File(windowsDir, "system32/" + dll);
            FileUtils.copy(srcFile, dstFile);
            srcFile = new File(syswow64dlls, dll);
            dstFile = new File(windowsDir, "syswow64/" + dll);
            FileUtils.copy(srcFile, dstFile);
        }
   }

    private String getWineStartCommand() {
        // Initialize overrideEnvVars if not already done
        EnvVars envVars = getOverrideEnvVars();

        // Define default arguments
        String args = "";

        // Real-Steam (VAC) launch (feature M3): when setupXEnvironment armed a RealSteam plan, run our
        // agent as the Steam client's steam.exe with the per-game spec as its argument, instead of the
        // game exe directly — the agent logs into genuine Steam then LaunchApp's the game securely (-steam).
        // Peer to the Epic arg-append block below; still wrapped in winhandler.exe like every other launch.
        // realSteamPlan is null for every non-RealSteam (or failed-prep) launch, so this branch is inert
        // there and the normal command path below is untouched.
        if (realSteamPlan != null) {
            String steamArgs = "/dir " + StringUtils.escapeDOSPath(realSteamPlan.steamExeDirWin)
                    + " \"" + realSteamPlan.steamExeName + "\" " + realSteamPlan.specArgWin;
            return "winhandler.exe " + steamArgs;
        }

        if (shortcut != null) {
            String execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " " + execArgs : "";

            if (xboxLaunch != null) {
                // Xbox: run the licensed launch view's executable (inside imagefs, so on Z:), not the
                // encrypted package file the shortcut points at.
                String rel;
                try {
                    rel = imageFs.getRootDir().getCanonicalFile().toPath().relativize(xboxLaunch.getExe().getCanonicalFile().toPath()).toString();
                } catch (java.io.IOException e) {
                    rel = imageFs.getRootDir().toPath().relativize(xboxLaunch.getExe().toPath()).toString();
                }
                String win = "Z:\\" + rel.replace('/', '\\');
                Log.i("BannerXbox", "start command exe: " + win + " (exists=" + xboxLaunch.getExe().isFile() + ")");
                args += "/dir " + StringUtils.escapeDOSPath(FileUtils.getDirname(win)) + " \"" + FileUtils.getName(win) + "\"" + execArgs;
            } else if (shortcut.path.endsWith(".lnk")) {
                args += "\"" + shortcut.path + "\"" + execArgs;
            } else {
                String exeDir = FileUtils.getDirname(shortcut.path);
                String filename = FileUtils.getName(shortcut.path);

                int dotIndex = filename.lastIndexOf(".");
                int spaceIndex = (dotIndex != -1) ? filename.indexOf(" ", dotIndex) : -1;

                if (spaceIndex != -1) {
                    execArgs = filename.substring(spaceIndex + 1) + execArgs;
                    filename = filename.substring(0, spaceIndex);
                }

                args += "/dir " + StringUtils.escapeDOSPath(exeDir) + " \"" + filename + "\"" + execArgs;
            }
        } else {
            // Append EXTRA_EXEC_ARGS from overrideEnvVars if it exists
            if (envVars.has("EXTRA_EXEC_ARGS")) {
                args += " " + envVars.get("EXTRA_EXEC_ARGS");
                envVars.remove("EXTRA_EXEC_ARGS"); // Remove the key after use
            } else {
                args += "\"wfm.exe\"";
            }
        }
        // Epic Online Services (EOS) Phase 1: for Epic-origin shortcuts with EOS auth
        // enabled, append the real-Epic launch args (-EpicPortal + fresh exchange code).
        // This runs on the background launch worker, so the synchronous exchange-code
        // fetch is ANR-safe. buildArgString silent-no-ops (returns "") on any failure.
        if (shortcut != null
                && "epic".equals(shortcut.getExtra("storeSource"))
                && !"0".equals(shortcut.getExtra("epicEos"))) {
            String epicArgs = com.winhub.hernandez.store.EpicLaunchArgs.buildArgString(this, shortcut);
            if (epicArgs != null && !epicArgs.isEmpty()) args += " " + epicArgs;
        }

        // Construct the final command
        String command = "winhandler.exe " + args;

        return command;
    }

    private String getExecutable() {
        String filename = "";
        if (shortcut != null) {
            filename = FileUtils.getName(shortcut.path);
        }
        else
            filename = "wfm.exe";
        return filename;
    }

    // Per-game overrides for renderer / frame-gen engine / fps limiter: use the shortcut's value if it
    // has one, otherwise follow the container. Read-only — never written back to the container, so a
    // per-game choice can't leak into the container's saved settings (the in-game toggle calls saveData).
    // Turnip TU_DEBUG assembly. Two contributors, both resolved through the already per-game-merged
    // graphicsDriverConfig map (shortcut override won over container at parse time, line ~1521):
    //   • Task #1 — "turnipGmem" tri-state: auto | on | off (default auto). Auto adds the `gmem`
    //     token ONLY on Adreno 710/720/722 (the parts that need it on a STOCK driver); On always
    //     adds it; Off never does (the escape hatch — Off means "don't add gmem", it does NOT force
    //     sysmem).
    //   • Task #2 — "turnipTokens": opt-in advanced tokens from a fixed allowlist (forcecb, nocb,
    //     deck_emu, sysmem). deck_emu only means anything on a Banners-Turnip driver; harmless else.
    // The two are comma-joined + de-duplicated, then UNIONED into any TU_DEBUG already in envVars
    // (container DEFAULT_ENV_VARS ships "noconform,sysmem"; a shortcut or manual env may override).
    // When the contribution set is empty we return WITHOUT touching envVars, so a default config on a
    // non-target GPU leaves the environment exactly as it was assembled. Read-only w.r.t. persistence
    // — same discipline as the resolved* helpers; never writes anything back.
    private void applyTurnipTuDebug() {
        if (graphicsDriverConfig == null) return;

        java.util.LinkedHashSet<String> add = new java.util.LinkedHashSet<>();

        // Task #1 — GMEM tri-state.
        String gmemMode = graphicsDriverConfig.get("turnipGmem");
        if (gmemMode == null || gmemMode.isEmpty()) gmemMode = "auto";
        boolean addGmem = "on".equals(gmemMode)
                || ("auto".equals(gmemMode) && GPUInformation.isAutoGmemGpu(this));
        if (addGmem) add.add("gmem");

        // Task #2 — advanced opt-in tokens (allowlist only; gmem/sysmem-vs-gmem collision avoided by
        // NOT listing gmem here — task #1 owns it).
        String tokens = graphicsDriverConfig.get("turnipTokens");
        if (tokens != null && !tokens.isEmpty()) {
            for (String t : tokens.split(",")) {
                t = t.trim();
                switch (t) {
                    case "forcecb": case "nocb": case "deck_emu": case "sysmem":
                        add.add(t);
                        break;
                    default:
                        break; // ignore anything not on the allowlist
                }
            }
        }

        if (add.isEmpty()) return; // default case — emit an unchanged environment

        // Union with any TU_DEBUG already present, preserving existing tokens and order.
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        String existing = envVars.get("TU_DEBUG");
        if (existing != null && !existing.isEmpty()) {
            for (String t : existing.split(",")) {
                t = t.trim();
                if (!t.isEmpty()) merged.add(t);
            }
        }
        merged.addAll(add);
        // GMEM must win over sysmem: Turnip's `sysmem` flag forces the direct/bypass path and defeats
        // `gmem`, so a default container (DEFAULT_ENV_VARS ships "noconform,sysmem") would silently
        // no-op the feature. When we're contributing gmem, strip sysmem from the FINAL set — from both
        // the pre-existing tokens AND any task-2 sysmem pick — so gmem takes effect (matches
        // GameNative #1656's sysmem→gmem replacement). Force Off / Auto-on-non-target never reach here
        // (empty `add` early-returns above), so a user who wants sysmem simply leaves GMEM off.
        if (add.contains("gmem")) merged.remove("sysmem");
        envVars.put("TU_DEBUG", String.join(",", merged));
        Log.d("XServerDisplayActivity", "Composed TU_DEBUG=" + envVars.get("TU_DEBUG")
                + " (gmemMode=" + gmemMode + ")");
    }

    // Host renderer for this session: the shortcut's pick wins, then the container's. EGL and SurfaceDirect are
    // opt-in; any other id (including the removed legacy ones) runs on Vulkan.
    private String resolvedRenderer() {
        if (container == null) return "vulkan";
        String id = shortcut != null ? shortcut.getExtra("renderer", container.getRenderer()) : container.getRenderer();
        if ("egl".equalsIgnoreCase(id)) return "egl";
        if ("surfacedirect".equalsIgnoreCase(id)) return "surfacedirect";
        return "vulkan";
    }

    // SurfaceDirect tuning: a shortcut extra wins, the container is the fallback (read-only, never written back).
    private com.winhub.hernandez.renderer.egl.SurfaceDirectRenderer.Options resolvedSurfaceDirectOptions() {
        com.winhub.hernandez.renderer.egl.SurfaceDirectRenderer.Options options =
            new com.winhub.hernandez.renderer.egl.SurfaceDirectRenderer.Options();
        if (container == null) return options;
        options.performanceMode = resolvedSurfaceDirectFlag("surfaceDirectPerformanceMode", container.isSurfaceDirectPerformanceMode());
        options.presentAtRefreshRate = resolvedSurfaceDirectFlag("surfaceDirectPresentAtRefreshRate", container.isSurfaceDirectPresentAtRefreshRate());
        options.backPressure = resolvedSurfaceDirectFlag("surfaceDirectBackPressure", container.isSurfaceDirectBackPressure());
        options.precisePresentation = resolvedSurfaceDirectFlag("surfaceDirectPrecisePresentation", container.isSurfaceDirectPrecisePresentation());
        return options;
    }

    private boolean resolvedSurfaceDirectFlag(String key, boolean containerValue) {
        if (shortcut == null) return containerValue;
        return shortcut.getExtra(key, containerValue ? "true" : "false").equals("true");
    }

    // Per-game overrides for the Vulkan-settings block (native / Colors=swapRB / present mode).
    // Shortcut extra wins, container is the fallback, read-only (never written back). Lets a
    // shortcut set e.g. Colors=RGBA without touching the container or its other games.
    private boolean resolvedRendererNative() {
        if (container == null) return false;
        return shortcut != null
                ? shortcut.getExtra("native", container.isRendererNative() ? "true" : "false").equals("true")
                : container.isRendererNative();
    }
    private boolean resolvedRendererSwapRB() {
        if (container == null) return false;
        return shortcut != null
                ? shortcut.getExtra("swapRB", container.getRendererSwapRB() ? "true" : "false").equals("true")
                : container.getRendererSwapRB();
    }
    private String resolvedRendererPresentMode() {
        if (container == null) return "fifo";
        return shortcut != null
                ? shortcut.getExtra("presentMode", container.getRendererPresentMode())
                : container.getRendererPresentMode();
    }

    private String resolvedFrameGenEngine() {
        final String containerEngine = container.getFrameGenEngine();
        if (shortcut != null) {
            final String e = shortcut.getExtra("frameGenEngine", "");
            // A per-game "off" is not a choice of engine: it means "inherit the container", just
            // like an absent extra. Only an enabled per-game engine overrides the container, so a
            // stale Off on the shortcut can no longer hide a container that has frame gen on -
            // which is what made the drawer say "enable it in the container" while it already was.
            if (!e.isEmpty() && !"off".equals(e))
                return "lsfg".equals(e) ? "lsfg-native" : e;
        }
        // lsfg-vk retired 2026-09-05 (parked on feat/lsfg-vk-plumbing): a legacy
        // per-game override still saying "lsfg" runs LSFG Native.
        return "lsfg".equals(containerEngine) ? "lsfg-native" : containerEngine;
    }

    // True for the session when the "bionic" (win-fg) engine runs inside our compositor
    // rather than as a layer inside the guest. Decided from launch config only, so the
    // drawer seed and the launch-env builder compute the same answer.
    private boolean winFgNativeSession = false;

    private boolean computeWinFgNativeSession() {
        if (!"bionic".equals(resolvedFrameGenEngine())) return false;
        // Wayland: the compositor is always Vulkan, and the guest-side win-fg layer is X11-only
        // (its frames are born inside the guest and have nothing to attach to in the Wayland
        // compositor), so "bionic" is Win-FG Native there.
        if (waylandMode) return true;
        return "vulkan".equalsIgnoreCase(resolvedRenderer());
    }

    // Either engine that generates in OUR compositor: LSFG Native, or Win-FG Native.
    // Everything that is true of one is true of the other - fifo while multiplying,
    // limiter/VRR locks, HUD base->shown - so the callers below key on this, not on
    // the engine name.
    private boolean nativeFrameGenEngine() {
        final String engine = resolvedFrameGenEngine();
        return "lsfg-native".equals(engine) || ("bionic".equals(engine) && winFgNativeSession);
    }

    // Any frame-gen engine (lsfg-vk OR bionic-fg) actively multiplying (mult >= 2) inserts extra
    // presents at the guest swapchain. The host compositor MUST be mailbox for those to reach the
    // screen: FIFO vsync-blocks the host present, which backpressures the guest present and strangles
    // the generated frames (device-verified — fifo makes FG "fps drops", mailbox makes it base×N).
    // Off / passthrough (mult < 2) keeps the user's configured mode (fifo = power-efficient, no waste).
    private boolean frameGenGenerating() {
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        return !"off".equals(resolvedFrameGenEngine())
            && s.getFrameGenEnabled().getValue()
            && s.getFrameGenMultiplier().getValue() >= 2;
    }

    // Host present mode — the user's chosen mode is always honored (mailbox lock removed:
    // frame gen no longer forces mailbox, so FIFO/etc. can be used with FG on).
    private String effectivePresentMode() {
        // LSFG Native REQUIRES fifo while it is multiplying, and this is the
        // opposite of what lsfg-vk wants. The native path queues the real frame
        // and its generated frames together in one submit and relies on fifo to
        // scan them out on consecutive vblanks. Under mailbox the presentation
        // engine keeps only the NEWEST queued image per vblank, so the whole
        // batch collapses to the real frame and every generated frame is
        // discarded at the very last step - the exact failure the guest-side
        // engines suffer, just moved. lsfg-vk needs mailbox for the opposite
        // reason: its extra presents come from inside the guest and fifo
        // back-pressure strangles them.
        // Keyed on the renderer's REAL armed state, not the drawer's multiplier
        // StateFlow. That flow defaults to 2 before the launch seed writes 0, which
        // is how the r9 log shows FIFO being forced 0.6 s after mailbox was applied
        // and before anything was armed - and then never released on disarm.
        com.winhub.hernandez.renderer.vulkan.VulkanRenderer vkrPm = vulkanRendererOrNull();
        if (nativeFrameGenEngine() && vkrPm != null && vkrPm.isFrameGenArmed()) {
            return "fifo";
        }
        return resolvedRendererPresentMode();
    }

    // (Re)apply the host present mode to the live Vulkan renderer — called at launch and whenever
    // frame gen toggles / the multiplier changes. Mailbox lock removed: the user's chosen present
    // mode is honored regardless of FG, and the drawer selector stays unlocked so it can be changed
    // live with FG on. No-op on non-Vulkan renderers / before setup.
    // Last mode actually pushed to the renderer + drawer. Without this, applying
    // the same mode again still writes the drawer StateFlow, the drawer
    // recomposes, its frame-gen controls re-fire their apply callback, and that
    // calls back into here - a feedback loop that spun applyLsfgNative four
    // times in four milliseconds on device and took the game down with it.
    private String lastAppliedPresentMode = null;

    private void applyEffectivePresentMode() {
        if (xServerView == null) return;
        HostRenderer r = xServerView.getRenderer();
        if (r instanceof com.winhub.hernandez.renderer.vulkan.VulkanRenderer) {
            String pm = effectivePresentMode();
            if (pm != null && pm.equals(lastAppliedPresentMode)) return;
            lastAppliedPresentMode = pm;
            int pmInt = "immediate".equals(pm) ? 0 : "mailbox".equals(pm) ? 1 : 2; // VkPresentModeKHR
            ((com.winhub.hernandez.renderer.vulkan.VulkanRenderer) r).setVkPresentMode(pmInt);
            // Mirror the mode into the drawer selector; never lock it (mailbox lock removed).
            XServerDrawerState.INSTANCE.setPresentMode(pm);
            XServerDrawerState.INSTANCE.setPresentModeLocked(false);
        }
    }

    // ── Power-user performance toggles (non-root). LOCKED two-level resolution chain:
    //     per-game shortcut override (only when the shortcut has that extra key set)  ->  global default.
    // There is NO container level. The global default is the single shared store both perf surfaces
    // bind to (App Settings' Performance menu writes it; the in-game drawer reads the effective value).
    private boolean resolvedPerfBool(String key, boolean globalDefault) {
        if (shortcut != null && shortcut.hasExtra(key)) return shortcut.getExtra(key, "0").equals("1");
        return globalDefault;
    }
    private boolean resolvedSustainedPerfMode() {
        return resolvedPerfBool("sustainedPerfMode",
                com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.getSustainedPerfMode().getValue());
    }
    private boolean resolvedPerfPriorityBoost() {
        return resolvedPerfBool("perfPriorityBoost",
                com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.getPerfPriorityBoost().getValue());
    }
    private boolean resolvedPreferBigCores() {
        return resolvedPerfBool("preferBigCores",
                com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.getPreferBigCores().getValue());
    }

    // Persist a live in-game flip under the rule "App Settings = default; a per-game toggle is saved
    // and honored only when it's DIFFERENT". With a shortcut: if the new value EQUALS the global
    // default we REMOVE the extra (the game re-inherits, hasExtra=false); if it DIFFERS we write the
    // per-game override. Without a shortcut (container-direct launch, no per-game store) the flip edits
    // the GLOBAL default — the only durable store. Applies to all 9 keys (non-root three + root six).
    private void persistPerfToggle(String key, boolean on) {
        if (shortcut != null) {
            boolean global = com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.globalDefault(key);
            if (on == global) {
                shortcut.removeExtra(key);
                XServerDrawerState.INSTANCE.markInherited(key); // resume mirroring the global default
            } else {
                shortcut.putExtra(key, on ? "1" : "0");
                XServerDrawerState.INSTANCE.markOverridden(key);
            }
            shortcut.saveData();
        } else {
            com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.setGlobalDefault(key, on);
        }
    }

    /**
     * Name of the folder this launch's logs go in. The shortcut name is what the user recognises in
     * the library, so it wins; a container booted straight to the desktop has no shortcut and uses
     * the container's own name ("Container-2"), which is still what they see in the Containers list.
     * Sanitising happens in {@link com.winhub.hernandez.core.LogLocation#sanitizeFolderName}.
     */
    private String currentLogGameName() {
        if (shortcut != null && shortcut.name != null && !shortcut.name.trim().isEmpty())
            return shortcut.name;
        if (container != null && container.getName() != null && !container.getName().trim().isEmpty())
            return container.getName();
        return com.winhub.hernandez.core.LogLocation.APP_FOLDER;
    }

    // The LIVE effect of a perf key (independent of persistence), so both a toggle flip and a
    // reset-to-global re-apply it the same way.
    private void applyPerfKeyLive(String key, boolean on) {
        switch (key) {
            case "sustainedPerfMode":
                runOnUiThread(() -> getWindow().setSustainedPerformanceMode(on));
                break;
            case "perfPriorityBoost":
                // Boost the GUEST CPU-worker subtree (box64/wine) + our audio/worker threads, never
                // downgrading an already-hot thread. Snapshotted so OFF restores exact nice values.
                if (on) com.winhub.hernandez.perf.PerfPriority.INSTANCE.boost(GuestProgramLauncherComponent.getPid());
                else    com.winhub.hernandez.perf.PerfPriority.INSTANCE.restore();
                break;
            case "preferBigCores":
                // Recompute the affinity mask the guest launcher reads (processes spawned after the
                // flip + next launch)...
                if (on) {
                    String bigList = detectBigCoreCpuListLogged();
                    if (bigList != null && !bigList.isEmpty()) {
                        taskAffinityMask = (short) ProcessHelper.getAffinityMask(bigList);
                        taskAffinityMaskWoW64 = taskAffinityMask;
                    }
                } else {
                    String cpuList = shortcut != null
                            ? shortcut.getExtra("cpuList", container.getCPUList(true))
                            : container.getCPUList(true);
                    taskAffinityMask = (short) ProcessHelper.getAffinityMask(cpuList);
                    taskAffinityMaskWoW64 = shortcut != null
                            ? taskAffinityMask
                            : (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));
                }
                // ...AND re-pin the ALREADY-RUNNING guest tree so the current game moves now.
                reapplyBigCoresToRunningGuest(on);
                // Keep the game pinned against later-spawned threads while Prefer Big Cores is ON: point the
                // drift checker at the game exe + big mask so it resolves the real Linux pid and re-pins
                // host-side on thread growth. OFF -> clear the target so the checker stops maintaining it.
                if (on) {
                    String exe = gameExeBasename();
                    if (exe != null && Integer.bitCount(taskAffinityMask & 0xff) < Runtime.getRuntime().availableProcessors()) {
                        if (!exe.equals(affinityTargetExe)) affinityLinuxPid = -1;
                        affinityTargetExe = exe;
                        affinityTargetMask = taskAffinityMask & 0xff;
                        startAffinityReapply();
                    }
                } else {
                    affinityTargetMask = 0;
                }
                break;
            default: // root six
                com.winhub.hernandez.perf.PerfRootApplier.INSTANCE.apply(key, on);
                break;
        }
    }

    // Reset ONE perf key: drop the per-game override so it re-inherits the global default, update the
    // drawer display, and re-apply the (now global) value live.
    private void resetPerfKey(String key) {
        if (shortcut == null) return; // no per-game store -> global already IS the value
        if (shortcut.hasExtra(key)) {
            shortcut.removeExtra(key);
            shortcut.saveData();
        }
        XServerDrawerState.INSTANCE.markInherited(key);
        applyPerfKeyLive(key, com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.globalDefault(key));
    }

    // Reset ALL 9 perf keys so the game fully re-inherits the global defaults.
    private void resetAllPerfOverrides() {
        for (String key : com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.getALL_PERF_KEYS()) resetPerfKey(key);
    }

    // Root-tier effective value: per-game shortcut override (when the shortcut has the key) else the
    // global default from PerformanceSettings. Same two-level chain as the non-root three.
    private boolean resolvedRootBool(String key) {
        return resolvedPerfBool(key, com.winhub.hernandez.perf.PerformanceSettings.INSTANCE.rootDefaultValue(key));
    }

    // Live readouts for the in-game Root Performance section (governor / GPU MHz / SoC temp / fan RPM).
    // Cheap sysfs / HudMetrics reads, refreshed on a ~1.5s poll while that section is open.
    private com.winhub.hernandez.widget.HudMetrics rootHudMetrics;
    private void refreshRootReadouts() {
        try {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            // Governor (first readable core).
            String gov = null;
            for (com.winhub.hernandez.perf.PerfNodeResolver.CpuCoreNodes c :
                    com.winhub.hernandez.perf.PerfNodeResolver.INSTANCE.cpuCores()) {
                if (c.getGovernor() != null) { gov = readSysfsLine(c.getGovernor()); break; }
            }
            m.put("governor", gov != null ? gov : "—");
            // SoC temp = hottest of CPU/GPU.
            if (rootHudMetrics == null) rootHudMetrics = new com.winhub.hernandez.widget.HudMetrics(this);
            Integer cpuT = rootHudMetrics.getCpuTempC();
            Integer gpuT = rootHudMetrics.getGpuTempC();
            Integer soc = (cpuT != null && gpuT != null) ? Math.max(cpuT, gpuT) : (cpuT != null ? cpuT : gpuT);
            // Raw numbers only — the dashboard gauges format their own units, so appending them here
            // would double up ("47°C °C").
            m.put("socTemp", soc != null ? String.valueOf(soc) : "—");
            // GPU current clock (MHz). Prefer HudMetrics' accessor (multi-path, works where the bare
            // kgsl gpuclk node is SELinux-blocked, e.g. SD8Gen3); fall back to the raw sysfs read.
            Integer gpuClk = rootHudMetrics.getGpuClockMhz();
            String gpuMhz = gpuClk != null ? String.valueOf(gpuClk) : readGpuMhz();
            m.put("gpuMhz", gpuMhz != null ? gpuMhz : "—");
            // Fan RPM (hwmon fanN_input), if any.
            String fan = readFanRpm();
            m.put("fanRpm", fan != null ? fan : "n/a");
            XServerDrawerState.INSTANCE.setRootReadouts(m);
        } catch (Exception ignored) {}
    }

    private static String readSysfsLine(String path) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String line = r.readLine();
            return line != null ? line.trim() : null;
        } catch (Exception e) { return null; }
    }

    private String readGpuMhz() {
        String[] cands = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",             // Adreno, Hz
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",   // Adreno devfreq, Hz
        };
        for (String c : cands) {
            String v = readSysfsLine(c);
            if (v != null) {
                try { long hz = Long.parseLong(v.trim()); if (hz > 1_000_000L) return String.valueOf(hz / 1_000_000L); }
                catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String readFanRpm() {
        java.io.File[] chips = new java.io.File("/sys/class/hwmon").listFiles();
        if (chips != null) {
            for (java.io.File chip : chips) {
                java.io.File[] inputs = chip.listFiles((d, n) -> n.matches("fan\\d+_input"));
                if (inputs != null) for (java.io.File f : inputs) {
                    String v = readSysfsLine(f.getPath());
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    // HUD config (fpsCounterConfig KeyValueSet) resolution. Container-scoped by default, but a shortcut
    // may OWN it — e.g. a community config imported onto the shortcut writes the whole blob as an extra.
    // When the shortcut carries the extra we honor it at launch AND route live in-game drawer edits back
    // to the SAME owner (persistFPSCounterConfig), so an imported HUD theme applies and tweaks to it
    // don't drift onto the container. The master FPS on/off (container.isShowFPS) stays container-scoped
    // and still gates whether any HUD is built — importing a config themes the HUD, it does not force it on.
    private boolean shortcutOwnsFpsConfig() {
        return shortcut != null && shortcut.hasExtra("fpsCounterConfig");
    }

    private String resolvedFPSCounterConfig() {
        if (shortcutOwnsFpsConfig()) return shortcut.getExtra("fpsCounterConfig");
        return container != null ? container.getFPSCounterConfig() : Container.DEFAULT_FPS_COUNTER_CONFIG;
    }

    // Write a HUD config change to whichever owner the launch resolver reads from: the shortcut when it
    // owns the blob (import), else the container (unchanged legacy behavior). Keeps read/write symmetric.
    private void persistFPSCounterConfig(String cfg) {
        if (shortcutOwnsFpsConfig()) {
            shortcut.putExtra("fpsCounterConfig", cfg);
            shortcut.saveData();
        } else if (container != null) {
            container.setFPSCounterConfig(cfg);
            container.saveData();
        }
    }

    // Vibration mode/intensity resolution — same discipline as resolvedFrameGenModel: per-game override
    // (shortcut extra) else the container value. The in-game drawer edits route back to the shortcut only
    // when it already owns the extra (import), else the container, so existing per-container behavior is
    // untouched for games never carrying an imported vibration override.
    private int resolvedVibrationMode() {
        int fallback = container != null ? container.getVibrationMode() : Container.VIBRATION_MODE_DEFAULT;
        if (shortcut == null) return fallback;
        try {
            return Integer.parseInt(shortcut.getExtra("vibrationMode", String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int resolvedOnScreenControllerMode() {
        int fallback = container != null ? container.getOnScreenControllerMode() : Container.ON_SCREEN_MODE_DEFAULT;
        if (shortcut == null) return fallback;
        try {
            return Integer.parseInt(shortcut.getExtra("onScreenControllerMode", String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // #333 smart default: the bundled "Virtual Gamepad" touch layout (controls-3.icp), used as the
    // default overlay for a fresh user who hasn't picked a profile. Matched by name; null if absent.
    private ControlsProfile findVirtualGamepadProfile() {
        for (ControlsProfile p : inputControlsManager.getProfiles()) {
            if (p != null && "Virtual Gamepad".equalsIgnoreCase(p.getName())) return p;
        }
        return null;
    }

    // #338: is a physical game controller connected right now? Used to suppress the #333 smart-default
    // touch overlay at launch — if a real pad is already present there's no out-of-box need for a
    // phantom one, and seeding it would grab a player slot that defeats auto-hide. Reads the same live
    // slot data as updateAutoHideForControllers(); on-screen ("virtual") pads are excluded.
    /**
     * Removes the fake evdev nodes of slots no device holds, for a Linux session.
     * Slot 0 is always kept, because the on-screen pad lands there when no physical one is connected.
     */
    private void trimUnusedFakeInputNodes(File fakeInputDir) {
        java.util.Set<Integer> held = new java.util.HashSet<>();
        held.add(0);
        if (winHandler != null) {
            for (WinHandler.PlayerSlotInfo s : winHandler.getPlayerSlotAssignments()) {
                if (s.currentSlot >= 0) held.add(s.currentSlot);
            }
        }
        File[] nodes = fakeInputDir.listFiles();
        if (nodes == null) return;
        StringBuilder removed = new StringBuilder();
        for (File node : nodes) {
            String name = node.getName();
            if (!name.startsWith("event")) continue;
            int slot;
            try {
                slot = Integer.parseInt(name.substring("event".length()));
            } catch (NumberFormatException e) {
                continue;
            }
            if (held.contains(slot)) continue;
            if (node.delete()) removed.append(' ').append(name);
        }
        Log.i("XServerDisplayActivity", "fake evdev: slots held " + held
                + (removed.length() > 0 ? ", removed" + removed : ", nothing removed"));
    }

    private boolean hasConnectedGameController() {
        if (winHandler == null) return false;
        for (WinHandler.PlayerSlotInfo s : winHandler.getPlayerSlotAssignments()) {
            if (s.isGameController && !s.isOnScreen) return true;
        }
        return false;
    }

    // #333: auto-hide on-screen controls when a controller takes the on-screen slot. Resolved
    // shortcut-override-else-container, same discipline as resolvedOnScreenControllerMode above.
    private boolean resolvedAutoHideControlsOnPad() {
        boolean fallback = container != null ? container.isAutoHideControlsOnPad()
                : Container.AUTO_HIDE_CONTROLS_ON_PAD_DEFAULT;
        if (shortcut == null) return fallback;
        String extra = shortcut.getExtra("autoHideControlsOnPad");
        if (extra == null || extra.isEmpty()) return fallback;
        return extra.equals("1");
    }

    /**
     * #333 slot-aware auto-hide. When enabled for this game/container, hide the on-screen touch controls
     * once a physical controller takes over the on-screen pad's player slot, and restore them (to the
     * user's chosen baseline) when none does. Locked disambiguation rule: only a controller that is
     * UNPINNED (solo takeover) or PINNED to the on-screen slot triggers the hide; a controller pinned to
     * a DIFFERENT player is treated as a separate player and leaves the overlay up. No-op while the
     * controls editor is open, and never forces controls on that the user chose to keep off. Main-thread
     * only (called from the debounced assignment listener, at launch, and on editor close).
     */
    // #333: rebuild the in-game Players tab list from live slot state. In a method (not the
    // fireControllerToast field initializer) to avoid an illegal forward reference to winHandler.
    private void refreshInGamePlayerSlotList() {
        if (winHandler != null) XServerDialogState.INSTANCE.setPlayerSlots(buildPlayerSlotRows());
    }

    /** Launch worker thread, before Wine boots: seats the on-screen pad on the main thread (its profile
     *  and slot state live there) and waits for it, at most 2 s. */
    private void seatOnScreenPadBeforeBoot() {
        final CountDownLatch seated = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                inputControlsView.loadProfileElementsIfReady();
                winHandler.seatOnScreenPadAtBoot();
            } finally {
                seated.countDown();
            }
        });
        try {
            if (!seated.await(2, TimeUnit.SECONDS))
                Log.w("XServerDisplayActivity", "on-screen pad not seated before boot (main thread busy)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Controls › Pad for a Wine session: the controller settings that change while the game runs
     * (LiveControllerSession), started once the launch has written their starting values.
     */
    private void startLiveControllerSession() {
        if (winHandler == null || container == null || liveControllerSession != null) return;
        com.winhub.hernandez.core.HermesXInput.Result sync = hxinputSync;
        boolean direct = sync != null ? sync.directOn
                : com.winhub.hernandez.core.HermesXInput.effective(container, shortcut);
        boolean switchable = sync != null && sync.installed && sync.switching && sync.forwardAvailable;
        int mode = hermesLaunchControl != null ? hermesLaunchControl.xinputMode : 0;
        int flags = hermesLaunchControl != null ? hermesLaunchControl.xinputFlags : 0;
        if (controllerSaveTarget == null) {
            controllerSaveTarget = new ControllerSaveTarget(preferences, container, shortcut);
            controllerSaveTarget.attach();
        }
        winHandler.setReconnectGapMs(preferences.getInt("live_reconnect_gap_ms", (int) com.winhub.hernandez.winhandler.SlotReconnector.GAP_MS_DEFAULT));
        liveControllerSession = new LiveControllerSession(container, shortcut, winHandler, new LiveControllerSession.Host() {
            @Override public void onReconnecting() {
                XServerDialogState.INSTANCE.showInfoToast("CONTROLLER RECONNECTING…", "just now", null);
            }

            @Override public void onReconnected(boolean ok, String identityLabel) {
                refreshInGamePlayerSlotList();
                updateAutoHideForControllers();
                if (ok) {
                    XServerDialogState.INSTANCE.showControllerToastFor("reconnected", buildPlayerSlotRows(), null);
                } else {
                    XServerDialogState.INSTANCE.showInfoToast("CONTROLLER NOT BACK YET", "",
                            "If the game lost the controller: Controls › Players › Reset Input, or restart the game.");
                }
            }

            @Override public void onXInputApplied(boolean direct, boolean ok) {
                if (ok) {
                    XServerDialogState.INSTANCE.showInfoToast("XINPUT SOURCE", "applied in game",
                            direct ? "Direct XInput (WinHub)" : "Wine's XInput");
                } else {
                    XServerDialogState.INSTANCE.showInfoToast("XINPUT SOURCE", "",
                            "Wine's XInput isn't available in this game; it stays on Direct XInput.");
                }
            }
        }, controllerSaveTarget, launchPadIdentity, launchDInputVisible, launchDInputForcedWhy, direct, switchable, mode, flags);
        liveControllerSession.attach();
        com.winhub.hernandez.core.ControllerLog.pads(winHandler.getPlayerSlotAssignments(), false);
        noteGameShipsXInput();
    }

    /** The game ships its own xinput DLL and Wine's builtin serves it (applyHermesXInputEnv): a note on
     *  Controls › Pad, and once per game a toast saying how to use Direct XInput there. */
    private void noteGameShipsXInput() {
        String names;
        synchronized (hxinputGameBuiltin) {
            if (hxinputGameBuiltin.isEmpty()) return;
            names = String.join(", ", hxinputGameBuiltin);
        }
        if (liveControllerSession != null) liveControllerSession.noteGameShipsXInput(names);
        String key = "hxinput_game_dll_toast:" + (shortcut != null ? shortcut.file.getAbsolutePath() : "") + ":" + names;
        if (preferences.getBoolean(key, false)) return;
        preferences.edit().putBoolean(key, true).apply();
        XServerDialogState.INSTANCE.showInfoToast("DIRECT XINPUT", "",
                "This game ships its own XInput DLL (" + names + "), so Wine's XInput is used for it instead of Direct XInput.");
    }

    private void updateAutoHideForControllers() {
        if (controlsEditorOpen) return;
        if (winHandler == null || inputControlsView == null) return;
        if (!resolvedAutoHideControlsOnPad()) return;

        java.util.List<WinHandler.PlayerSlotInfo> slots = winHandler.getPlayerSlotAssignments();
        // The on-screen pad's "home" slot: its explicit pin if any, else Player 1 (slot 0).
        int oscHomeSlot = 0;
        for (WinHandler.PlayerSlotInfo s : slots) {
            if (s.isOnScreen) { if (s.override >= 0) oscHomeSlot = s.override; break; }
        }
        // Does a physical controller actually OCCUPY the on-screen slot? Key off the resolved current
        // slot, not the pin: a solo pad yields onto the on-screen slot (hide); a pad on a DIFFERENT
        // player (pinned to P2, or bumped to P2 because the on-screen pad is pinned to P1) is a separate
        // player and leaves the overlay up; an Ignored/unassigned pad (currentSlot -1) never triggers.
        boolean padTakingOver = false;
        for (WinHandler.PlayerSlotInfo s : slots) {
            if (!s.isGameController) continue;
            if (s.currentSlot >= 0 && s.currentSlot == oscHomeSlot) { padTakingOver = true; break; }
        }

        if (padTakingOver) {
            if (inputControlsView.isShowTouchscreenControls()) {
                timeoutHandler.removeCallbacks(hideControlsRunnable);
                inputControlsView.setShowTouchscreenControls(false);
                inputControlsView.setVisibility(View.GONE);
                Log.d("XServerDisplayActivity", "#333 auto-hide: controller took the on-screen slot -> hiding touch controls");
            }
        } else if (userWantsControlsShown && !inputControlsView.isShowTouchscreenControls()) {
            // No controller owns the on-screen slot: restore to the user's baseline (never force on).
            inputControlsView.setShowTouchscreenControls(true);
            inputControlsView.setVisibility(View.VISIBLE);
            if (preferences.getBoolean("touchscreen_timeout_enabled", false)) startTouchscreenTimeout();
            Log.d("XServerDisplayActivity", "#333 auto-hide: no controller on the on-screen slot -> restoring touch controls");
        }
    }

    private int resolvedVibrationIntensity() {
        int fallback = container != null ? container.getVibrationIntensity() : Container.VIBRATION_INTENSITY_DEFAULT;
        if (shortcut == null) return fallback;
        try {
            return Integer.parseInt(shortcut.getExtra("vibrationIntensity", String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Manual controller-slot overrides (Players sub-tab): the same per-game-override-else-container
    // discipline as the vibration resolvers above. The value is an opaque JSON object string
    // (descriptor -> desired slot); WinHandler parses/serializes it via parseSlotOverrides/
    // buildSlotOverridesJson.
    private String resolvedControllerSlotOverridesJson() {
        String fallback = container != null ? container.getControllerSlotOverrides() : "{}";
        if (shortcut == null) return fallback;
        return shortcut.getExtra("controllerSlotOverrides", fallback);
    }

    private void persistControllerSlotOverridesJson(String json) {
        if (shortcut != null && shortcut.hasExtra("controllerSlotOverrides")) {
            shortcut.putExtra("controllerSlotOverrides", json);
            shortcut.saveData();
        } else if (container != null) {
            container.setControllerSlotOverrides(json);
            container.saveData();
        }
    }

    // Delegate to the shared schema helpers on WinHandler so the in-game Players tab, launch
    // pre-assign, and the out-of-game container/shortcut editors all read/write ONE JSON format.
    private java.util.Map<String, Integer> parseSlotOverrides(String json) {
        return com.winhub.hernandez.winhandler.WinHandler.parseSlotOverridesJson(json);
    }

    private String buildSlotOverridesJson() {
        return com.winhub.hernandez.winhandler.WinHandler.buildSlotOverridesJson(winHandler.getManualSlotOverrides());
    }

    // bionic-fg interpolation model for this launch: per-game override else the container value.
    // Same read-only resolver discipline as resolvedFrameGenEngine — never writes back.
    private int resolvedFrameGenModel() {
        int fallback = container.getFrameGenModel();
        if (shortcut == null) return fallback;
        try {
            int m = Integer.parseInt(shortcut.getExtra("frameGenModel", String.valueOf(fallback)));
            return (m < 0 || m > 4) ? fallback : m;
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    // win-fg performance preset resolution — same read-only resolver discipline as
    // resolvedFrameGenModel: per-game shortcut override wins, else the container's value.
    private int resolvedFrameGenPerfPreset() {
        int fallback = container.getFrameGenPerfPreset();
        if (shortcut == null) return fallback;
        try {
            int p = Integer.parseInt(shortcut.getExtra("frameGenPerfPreset", String.valueOf(fallback)));
            return (p < 0 || p > 2) ? fallback : p;
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Resolved ReShade config for this launch: the loadout (ordered effects + per-effect enabled),
    // the solo/stack mode, and the raw per-effect params JSON (nested, or migrated flat legacy).
    private static class ResolvedReshade {
        java.util.List<com.winhub.hernandez.reshade.ReshadeLoadout.Entry> loadout;
        String mode;
        String paramsJson;   // nested {"<effect>":{uniform:value}} when nested==true, else flat legacy
        boolean nested;      // whether a reshadeLoadout array was the source (params are nested)
        String legacyEffect; // for flat-params migration
        boolean masterEnabled = true; // whole-chain enableOnLaunch, persisted from the drawer master switch
    }

    // A shortcut OWNS the reshade config as a unit once it carries any reshade extra (the new loadout
    // array or the legacy single effect); until then the container's config is authoritative. This is
    // the SINGLE discriminator used by BOTH resolveReshade() (which source to read) and the live-apply
    // persist (which source to write) so a write always lands where the next launch will read it.
    private boolean shortcutOwnsReshade() {
        return shortcut != null
                && (shortcut.getExtra("reshadeLoadout", null) != null
                    || shortcut.getExtra("reshadeEffect", null) != null);
    }

    // ReShade selection resolution. The shortcut OWNS the whole reshade config (loadout + mode +
    // params) when it sets any reshade extra (reshadeLoadout or the legacy reshadeEffect); otherwise
    // the container's is used. Resolving as a unit (rather than per-key) keeps the loadout + its
    // params coherent and migrates legacy single-effect saves transparently (ReshadeLoadout.parse).
    private ResolvedReshade resolveReshade() {
        ResolvedReshade r = new ResolvedReshade();
        if (container == null) {
            r.loadout = new java.util.ArrayList<>();
            r.mode = com.winhub.hernandez.reshade.ReshadeLoadout.MODE_SOLO;
            r.paramsJson = null;
            r.nested = false;
            r.legacyEffect = "None";
            return r;
        }
        String loadoutJson, mode, paramsJson, legacyEffect;
        boolean shortcutOwns = shortcutOwnsReshade();
        if (shortcutOwns) {
            loadoutJson  = shortcut.getExtra("reshadeLoadout", null);
            mode         = shortcut.getExtra("reshadeMode", "solo");
            paramsJson   = shortcut.getExtra("reshadeParams", null);
            legacyEffect = shortcut.getExtra("reshadeEffect", "None");
            r.masterEnabled = !shortcut.getExtra("reshadeMasterEnabled", "1").equals("0");
        } else {
            loadoutJson  = container.getReshadeLoadout();
            mode         = container.getReshadeMode();
            paramsJson   = container.getReshadeParams();
            legacyEffect = container.getReshadeEffect();
            r.masterEnabled = container.getReshadeMasterEnabled();
        }
        r.nested = loadoutJson != null && !loadoutJson.isEmpty();
        r.loadout = com.winhub.hernandez.reshade.ReshadeLoadout.parse(loadoutJson, legacyEffect);
        r.mode = com.winhub.hernandez.reshade.ReshadeLoadout.normalizeMode(mode);
        r.paramsJson = paramsJson;
        r.legacyEffect = legacyEffect;
        // Solo safety: never light up two effects at once in solo mode.
        com.winhub.hernandez.reshade.ReshadeLoadout.enforceSolo(r.loadout, r.mode);
        return r;
    }

    // ReShade only rides the guest-side Vulkan swapchain (DXVK/VKD3D via Turnip), so it's a no-op on
    // WineD3D/GL/GDI titles. Renderer-agnostic (works under any host renderer). Drives the editor
    // hint + the in-game drawer grey-out (mirrors how SGSR/HDR are gated).
    private boolean reshadeSupported() {
        return dxwrapper != null && (dxwrapper.contains("dxvk") || dxwrapper.contains("vegas"));
    }

    private boolean resolvedFpsLimiterEnabled() {
        if (shortcut != null) {
            return shortcut.getExtra("fpsLimiterEnabled", container.isFpsLimiterEnabled() ? "1" : "0").equals("1");
        }
        return container.isFpsLimiterEnabled();
    }

    // Per-game override for the limiter cap value (shortcut wins over the container default), so the
    // value seed reads from the SAME owner onFpsLimitChange writes to. Mirrors resolvedFpsLimiterEnabled().
    private int resolvedFpsLimiterValue() {
        if (shortcut != null) {
            try {
                return Integer.parseInt(shortcut.getExtra("fpsLimiterValue",
                    String.valueOf(container.getFpsLimiterValue())));
            } catch (NumberFormatException e) {
                return container.getFpsLimiterValue();
            }
        }
        return container.getFpsLimiterValue();
    }

    // Per-game override for VRR / refresh-rate matching (shortcut wins over the container default).
    // Mirrors resolvedFpsLimiterEnabled(). Null-safe for early calls before the container is loaded.
    private boolean resolvedMatchRefreshRate() {
        if (container == null) return false;
        if (shortcut != null) {
            return shortcut.getExtra("matchRefreshRate", container.isMatchRefreshRate() ? "1" : "0").equals("1");
        }
        return container.isMatchRefreshRate();
    }

    // Per-game override for OpenGL safe mode (shortcut wins over the container default), Wayland only.
    // Default ON (Container.isWaylandGlSafeMode). Read and WRITTEN through the same owner: when a
    // shortcut is launched the value lives on the shortcut, otherwise on the container -- so the
    // in-game toggle is never inert. (resolvedMatchRefreshRate() has exactly that bug: it prefers a
    // shortcut extra while the drawer writes the container, so a shortcut carrying its own value
    // swallows the toggle. Do not copy that shape.)
    private boolean resolvedWaylandGlSafeMode() {
        if (container == null) return true;
        if (shortcut != null) {
            return shortcut.getExtra("waylandGlSafeMode",
                container.isWaylandGlSafeMode() ? "1" : "0").equals("1");
        }
        return container.isWaylandGlSafeMode();
    }

    // Per-game override for the manual refresh-rate lock (shortcut wins over the container default).
    // Mirrors resolvedMatchRefreshRate(). 0 = no manual lock. Null-safe for early calls.
    // Per-game override for the guest-side refresh ceiling (shortcut wins over the container
    // default). Mirrors resolvedManualRefreshRate(). 0 = no cap. Null-safe for early calls.
    private int resolvedMaxGameRefreshRate() {
        if (container == null) return 0;
        if (shortcut != null) {
            try {
                return Integer.parseInt(shortcut.getExtra("maxGameRefreshRate",
                    String.valueOf(container.getMaxGameRefreshRate())));
            } catch (NumberFormatException e) {
                return container.getMaxGameRefreshRate();
            }
        }
        return container.getMaxGameRefreshRate();
    }

    // Per-game override for the in-game refresh unlock (shortcut wins over the container default).
    // The shortcut extra is tri-state: "" = inherit the container, "1" = on, "0" = off.
    private boolean resolvedUnlockGameRefreshRate() {
        if (container == null) return true;
        if (shortcut != null) {
            String extra = shortcut.getExtra("unlockGameRefreshRate", "");
            if (extra.equals("1")) return true;
            if (extra.equals("0")) return false;
        }
        return container.isUnlockGameRefreshRate();
    }

    private int resolvedManualRefreshRate() {
        if (container == null) return 0;
        if (shortcut != null) {
            try {
                return Integer.parseInt(shortcut.getExtra("manualRefreshRate",
                    String.valueOf(container.getManualRefreshRate())));
            } catch (NumberFormatException e) {
                return container.getManualRefreshRate();
            }
        }
        return container.getManualRefreshRate();
    }

    // lsfg-vk does its OWN frame pacing when it is multiplying (multiplier >= 2). Layering the
    // standalone IdleNotify limiter on top double-paces the present stream: our pacer throttles
    // lsfg's already-multiplied output, clamping the panel to the limiter value (killing the FG
    // smoothness gain and wasting GPU on interpolated frames that then get blocked). So the limiter
    // steps aside and lets lsfg govern whenever lsfg is active at mult >= 2.
    //
    // >>> IF USERS REPORT "THE FPS LIMITER DOESN'T WORK / NO CAP" ON lsfg-vk, THIS GUARD IS WHY:
    //     the limiter is intentionally disabled while lsfg-vk multiplies (mult >= 2). It is NOT
    //     disabled for bionic-fg, for Off, or for lsfg at 1x (passthrough) -- those still cap.
    //
    // Behavior ported from GameNative (its "limiterControlledByLsfg"):
    // https://github.com/utkarshdalal/GameNative. See README Credits.
    private boolean lsfgGovernsFps() {
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        // ONLY lsfg-vk. It lives inside the guest and paces the guest itself, so
        // our limiter has to step aside or the two fight.
        //
        // LSFG Native deliberately does NOT qualify. It never touches the guest;
        // it generates frames in our compositor on top of whatever the guest
        // produces. Letting the limiter step aside there would silently drop the
        // user's FPS cap, run the guest uncapped, and hand the frame-gen chain a
        // hotter, busier GPU to compete with - which is exactly what makes the
        // governor reject its probes. The right shape for the native engine is:
        // cap the REAL frames, and generate in between them.
        return "lsfg".equals(resolvedFrameGenEngine())
            && s.getFrameGenEnabled().getValue()
            && s.getFrameGenMultiplier().getValue() >= 2;
    }

    // True when what reaches the PANEL is a multiple of the guest's rate, for
    // either LSFG engine. VRR must vote the displayed cadence, not the cap -
    // separate question from who paces the guest.
    private boolean frameGenMultipliesDisplay() {
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        final String engine = resolvedFrameGenEngine();
        return ("lsfg".equals(engine) || nativeFrameGenEngine())
            && s.getFrameGenEnabled().getValue()
            && s.getFrameGenMultiplier().getValue() >= 2;
    }

    // Re-evaluate and re-apply the FPS cap from the remembered limiter state. Called when the
    // frame-gen config changes live (e.g. switching lsfg multiplier) so the lsfgGovernsFps() guard
    // takes effect immediately without the user touching the limiter toggle.
    private void reapplyFpsLimit() {
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        boolean limOn  = s.getFpsLimiterEnabled().getValue();
        int   limitVal = s.getFpsLimit().getValue();
        applyFpsLimit(limOn && limitVal > 0 ? limitVal : 0);
    }

    // Apply the FPS cap (0 = off). The real limiter is the X11 Present extension, which throttles
    // the guest by pacing its IdleNotify (so the game itself slows -> in-game HUD reflects it, GPU
    // drops). Also feeds the renderer's SurfaceControl frame-rate hint (active in Vulkan native mode).
    private void applyFpsLimit(int fps) {
        // Capture the un-guarded cap (the limiter value, 0 = uncapped) BEFORE the lsfg guard zeroes
        // the local `fps`. VRR votes the DISPLAYED rate, which in the lsfg-governs case is cap x mult
        // even though the present pacer steps aside (fps -> 0). This is the only place the two diverge.
        int vrrCap = fps;
        // Step aside while lsfg-vk is multiplying -- it paces itself (see lsfgGovernsFps()).
        if (lsfgGovernsFps()) fps = 0;
        com.winhub.hernandez.xserver.extensions.PresentExtension pe =
                xServer.getExtension(com.winhub.hernandez.xserver.extensions.PresentExtension.MAJOR_OPCODE);
        float paced = pacedLimitWithSlack(fps);
        if (paced != fps) Log.i("XServerDisplayActivity", "fps limit " + fps + " paced at " + paced
            + " (exact display fit under native frame gen; slack " + NATIVE_FG_FIT_SLACK + ")");
        if (pe != null) pe.setFrameRateLimit(paced);
        if (xServerView != null) {
            HostRenderer r = xServerView.getRenderer();
            if (r != null) r.setFpsLimit(fps);
        }
        // Wayland: the compositor paces buffer returns instead of the Present extension.
        if (waylandMode) com.winhub.hernandez.wayland.WaylandCompositor.nativeSetFpsLimit(Math.round(paced));
        // VRR / refresh-rate matching: vote the panel cadence to match the displayed FPS.
        applyVrr(vrrCap);
    }

    // Surface.FRAME_RATE_COMPATIBILITY_DEFAULT (== 0). Referenced as a literal so the call site is not
    // an API-30 field access; the real API call is guarded inside XServerView.setDisplayFrameRate.
    private static final int VRR_FRAME_RATE_COMPATIBILITY = 0;

    // Vote a panel refresh rate that matches the DISPLAYED frame rate (VRR / refresh-rate matching).
    // Complementary to the FPS limiter: the limiter caps the producer/render rate, this matches the
    // display/panel rate so the panel cadence follows render cadence (smoother + power savings).
    //   Auto ON, cap == 0 (limiter off)    -> vote 0f (clear; panel runs free)
    //   Auto ON, normal / bionic-fg        -> vote cap
    //   Auto ON, lsfg multiplying (>= 2)   -> vote cap x mult (the displayed rate)
    //   Auto OFF, manual rate > 0          -> vote that rate (lock, independent of the FPS cap)
    //   Auto OFF, manual rate == 0         -> vote 0f (no lock; panel runs free)
    private void applyVrr(int cap) {
        // The policy below is backend-agnostic; only routeVrrVote() knows where the vote goes.
        // X11 needs the X view (it owns the presenting surface); Wayland presents through the
        // embedded compositor's SurfaceView instead, so it must not bail out here.
        if (xServerView == null && !waylandMode) return;
        float vrrRate = 0.0f;
        // LSFG Native / Win-FG Native generating with Auto on: fit the display to
        // cap x multiplier, picked from the display's own rates (exact, else the
        // closest above; see pickNativeFgRefresh). The two reasons this used to be
        // left out no longer hold as they did: the fixed-multiplier pacer now
        // presents exactly the requested multiplier, and the vote only changes
        // when the cap, multiplier or frame gen changes - one mode switch per user
        // action, never a chase of the live frame rate. Nothing at or above the
        // wanted rate -> no vote, the panel stays at its max, and the drawer warns.
        final boolean nativeFgGenerating = nativeFrameGenEngine() && frameGenMultipliesDisplay();
        // Under native frame gen this is the session's Auto (on unless this game opted out),
        // otherwise the saved setting - see applyNativeFgLocks.
        final boolean autoOn = autoRefreshActive();
        if (container != null && autoOn && nativeFgGenerating) {
            int mult = XServerDrawerState.INSTANCE.getFrameGenMultiplier().getValue();
            vrrRate = cap > 0 ? pickNativeFgRefresh(cap * mult) : 0.0f;
            Log.i("XServerDisplayActivity", "native-fg vrr: " + cap + " x " + mult + " = " + (cap * mult)
                + " -> display " + (vrrRate > 0f ? vrrRate + " Hz" : "top rate (nothing at or above)"));
        } else if (container != null && autoOn) {
            // Auto (match FPS): vote the panel cadence to follow the displayed FPS while capping.
            if (cap > 0) {
                if (frameGenMultipliesDisplay()) {
                    int mult = XServerDrawerState.INSTANCE.getFrameGenMultiplier().getValue();
                    vrrRate = (float) cap * (mult >= 2 ? mult : 1);
                } else {
                    vrrRate = (float) cap;
                }
            }
        } else if (container != null) {
            // Manual: lock the panel to the chosen rate, independent of the FPS cap. 0 = no lock.
            int manual = resolvedManualRefreshRate();
            if (manual > 0) vrrRate = (float) manual;
        }
        routeVrrVote(vrrRate);
        // onCreate pins the window's preferredRefreshRate to the panel max (for smooth UI). That
        // window-level request out-votes the VRR surface vote, so the panel never leaves max. When VRR is
        // matching a capped rate, lower the window preference to that rate too; otherwise restore the max.
        applyWindowPreferredRefreshRate(vrrRate);
    }

    // The last rate applyVrr routed, so a surface that is (re)created later can be given the same
    // vote instead of coming up unvoted.
    private float vrrVote = 0.0f;

    // Send the panel refresh-rate vote to whichever surface the ACTIVE backend actually presents on.
    // SurfaceFlinger only counts a frame-rate vote from a layer that is producing frames, so this has
    // to follow the frames:
    //   X11     -> the XServerView's surface (it owns the renderer, and re-asserts the vote itself
    //              across surface recreation - XServerView.reassertFrameRate).
    //   Wayland -> the embedded compositor's SurfaceView. The X view is still constructed and added
    //              in a Wayland session, but it is idle (no X server component runs, nothing draws
    //              into it), so the vote used to land on a layer that never presents and SurfaceFlinger
    //              dropped it - refresh-rate matching was silently inert on every Wayland session.
    //              Under zero-copy the game's frames go straight onto their own ASurfaceControl layer
    //              and bypass this surface, so the same rate is voted on the layer as well.
    // Never both: two layers of the same app voting different rates is exactly what makes the
    // aggregate unpredictable.
    private void routeVrrVote(float vrrRate) {
        vrrVote = vrrRate;
        if (waylandMode) {
            applySurfaceFrameRate(waylandSurfaceView, vrrRate);
            try {
                com.winhub.hernandez.wayland.WaylandCompositor.nativeSetLayerFrameRate(vrrRate);
            } catch (Throwable t) {
                Log.w("XServerDisplayActivity", "wayland: layer frame-rate vote unavailable", t);
            }
        } else if (xServerView != null) {
            xServerView.setDisplayFrameRate(vrrRate, VRR_FRAME_RATE_COMPATIBILITY);
        }
    }

    // Surface.setFrameRate on a SurfaceView's surface, with the same API guards and change strategy
    // XServerView.applyFrameRateToSurface uses: the 3-arg overload with CHANGE_FRAME_RATE_ALWAYS from
    // API 31 (the 2-arg default is seamless-only, which a peak-refresh panel simply ignores), the
    // 2-arg one on API 30, nothing below that. Safe before the surface exists - surfaceCreated
    // re-asserts the remembered vote.
    private void applySurfaceFrameRate(android.view.SurfaceView v, float fps) {
        if (Build.VERSION.SDK_INT < 30 || v == null) return;
        android.view.SurfaceHolder h = v.getHolder();
        android.view.Surface s = h != null ? h.getSurface() : null;
        if (s == null || !s.isValid()) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                s.setFrameRate(fps, VRR_FRAME_RATE_COMPATIBILITY,
                        android.view.Surface.CHANGE_FRAME_RATE_ALWAYS);
            } else {
                s.setFrameRate(fps, VRR_FRAME_RATE_COMPATIBILITY);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Surface released, or the rate/compat was rejected - the vote is best-effort.
        }
    }

    // Keep the window's preferred refresh rate in step with VRR so it doesn't fight the surface vote.
    // vrrRate > 0 -> prefer that exact rate (the panel switches to the matching mode); 0 -> restore max.
    private void applyWindowPreferredRefreshRate(float vrrRate) {
        runOnUiThread(() -> {
            android.view.WindowManager.LayoutParams p = getWindow().getAttributes();
            float desired = vrrRate > 0f ? vrrRate : pickHighestRefreshRate();
            // The drawer's frame-gen over-limit warning compares cap x multiplier against this.
            XServerDrawerState.INSTANCE.setDisplayTargetHz(Math.round(desired));
            if (p.preferredRefreshRate != desired) {
                p.preferredRefreshRate = desired;
                getWindow().setAttributes(p);
            }
        });
    }

    // Re-apply the VRR vote from the current remembered limiter state (used on resume and when the
    // match-refresh toggle changes live, without re-poking the present pacer / renderer).
    private void reapplyVrr() {
        XServerDrawerState s = XServerDrawerState.INSTANCE;
        boolean limOn  = s.getFpsLimiterEnabled().getValue();
        int   limitVal = s.getFpsLimit().getValue();
        applyVrr(limOn && limitVal > 0 ? limitVal : 0);
    }

    // Push the live (actual) display refresh rate into the drawer so the readout can show what the
    // panel is really running at while Auto (match FPS) is on and the manual slider is greyed.
    private void updateCurrentRefreshRate() {
        int rate = com.winhub.hernandez.widget.XServerView.getCurrentRefreshRate(getWindowManager().getDefaultDisplay());
        XServerDrawerState.INSTANCE.setCurrentRefreshRate(rate);
    }

    // Listen for panel mode switches so the readout tracks the real rate live (e.g. when VRR drops
    // the panel 144->60 after a vote). Registered while resumed, released on stop.
    private android.hardware.display.DisplayManager.DisplayListener vrrDisplayListener;

    private void registerVrrDisplayListener() {
        if (vrrDisplayListener != null) return;
        android.hardware.display.DisplayManager dm =
            (android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;
        vrrDisplayListener = new android.hardware.display.DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int displayId) {}
            @Override public void onDisplayRemoved(int displayId) {}
            @Override public void onDisplayChanged(int displayId) {
                updateCurrentRefreshRate();
                // Rotation rides on the same callback, and this fires for rotations that never reach
                // onConfigurationChanged (e.g. a 180 flip between the two landscape orientations).
                refreshCachedDisplayRotation();
            }
        };
        dm.registerDisplayListener(vrrDisplayListener, handler);
    }

    private void unregisterVrrDisplayListener() {
        if (vrrDisplayListener == null) return;
        android.hardware.display.DisplayManager dm =
            (android.hardware.display.DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm != null) dm.unregisterDisplayListener(vrrDisplayListener);
        vrrDisplayListener = null;
    }


    public XServer getXServer() {
        return xServer;
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public EnvVars getOverrideEnvVars() {
        if (overrideEnvVars == null) {
            overrideEnvVars = new EnvVars();
        }
        return overrideEnvVars;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = imageFs.getRootDir();
            File userRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals("alsa")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                }
                else if (audioDriver.equals("pulseaudio")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
                else if (audioDriver.equals("directaudio")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "directaudio");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    // Turn Wine's win32u display-mode EMULATION off (or back on) in the ACTIVE prefix so games see the
    // discrete refresh rates our RandR extension advertises instead of the emulated {60, current}.
    // Under HKCU [Software\Wine\X11 Driver], "EmulateModelist"/"EmulateModeset"="Y" DISABLE emulation
    // (sysparams.c:6201 emulate_modelist = !IS_OPTION_TRUE) — device-proven (Dirt 3 cycled 60→120).
    // Written on every launch (idempotent) so it survives a prefix regen (applyGeneralPatches) and
    // retrofits already-created containers; imageFs.getRootDir()+WINEPREFIX resolves through the
    // xuser symlink to the launching container's own .wine. When the toggle is OFF the values are
    // removed, reverting to Wine's default (emulation on). Runs AFTER setupWineSystemFiles so any
    // prefix regen this launch is already done.
    //
    // GATED ON LAYER CAPABILITY: disabling emulation only helps on a Proton/Wine layer whose winex11
    // was COMPILED WITH xrandr (the Refreshed Proton 10.0-4 / 11.0-1 builds). On an old layer it gives
    // no refresh benefit AND shrinks the resolution list (NoRes single mode), a mild regression — so
    // when the selected layer isn't xrandr-capable we DON'T write the keys (and remove any left by a
    // previous run), then Toast the user to install a compatible layer.
    private void applyGameRefreshRateUnlock() {
        final String x11DriverKey = "Software\\Wine\\X11 Driver";
        boolean unlock = resolvedUnlockGameRefreshRate();
        boolean capable = isSelectedLayerXrandrCapable();
        boolean writeKeys = unlock && capable;

        File rootDir = imageFs.getRootDir();
        File userRegFile = new File(rootDir, ImageFs.WINEPREFIX+"/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            if (writeKeys) {
                registryEditor.setStringValue(x11DriverKey, "EmulateModelist", "Y");
                registryEditor.setStringValue(x11DriverKey, "EmulateModeset", "Y");
            }
            else {
                // Toggle off OR incapable layer: strip the keys so no stale resolution regression lingers.
                registryEditor.removeValue(x11DriverKey, "EmulateModelist");
                registryEditor.removeValue(x11DriverKey, "EmulateModeset");
            }
        }
        boolean explicit = isRefreshUnlockExplicit();
        Log.d("XServerDisplayActivity", "In-game refresh unlock: setting=" + (unlock ? "unlock" : "locked")
                + (explicit ? " (explicit)" : " (default)") + " layerXrandrCapable=" + capable
                + " -> keys " + (writeKeys ? "WRITTEN" : "removed"));

        // The user EXPLICITLY chose a non-Locked rate but the selected layer can't deliver it — tell
        // them why. Suppressed for the untouched default (extra absent) so we never nag users who never
        // opted in; the capability guard above already prevents the functional regression regardless.
        if (unlock && !capable && explicit) {
            runOnUiThread(() -> Toast.makeText(this,
                    R.string.refresh_unlock_needs_compatible_layer, Toast.LENGTH_LONG).show());
        }
    }

    // Whether the guest-side refresh setting was explicitly chosen by the user (extra present) vs. left
    // at the untouched default (extra absent). A per-game override that inherits (no shortcut extra)
    // defers to the container's explicitness. Keyed on the unlockGameRefreshRate extra, which the merged
    // "In-game refresh rate" dropdown always writes alongside the cap when a concrete option is picked.
    private boolean isRefreshUnlockExplicit() {
        if (container == null) return false;
        if (shortcut != null && shortcut.hasExtra("unlockGameRefreshRate")) return true;
        return container.hasExtra("unlockGameRefreshRate");
    }

    // True when the SELECTED wine/Proton layer's unix winex11 driver was compiled with xrandr. The scan
    // + per-layer cache lives in WineRandrSupport (shared with the container editor's warning hint).
    private boolean isSelectedLayerXrandrCapable() {
        return com.winhub.hernandez.core.WineRandrSupport.isXrandrCapable(wineInfo);
    }

    // ── Epic Friends Overlay (Phase 3) ────────────────────────────────────────────────────────
    // Provision-only: we drop Epic's REAL overlay component into the prefix and write ONE HKCU
    // pointer; the game's own bundled EOS SDK loads it and owns the hotkey (Shift+F3). We render
    // nothing. Gated per-shortcut by storeSource=epic + epicOverlay=1.

    /**
     * True when the launching shortcut is an Epic game with the Friends-Overlay toggle on AND the
     * feature is enabled at build time. This is the single authoritative predicate: it gates the
     * pill attach, the AGGRESSIVE-startup override, and (via the strip branch below) provisioning.
     * While {@link com.winhub.hernandez.FeatureFlags#EPIC_OVERLAY_ENABLED} is false this always returns
     * false, so every Epic launch takes the strip-and-do-nothing path — inert and self-cleaning.
     */
    private boolean isEpicOverlayEnabledForLaunch() {
        return com.winhub.hernandez.FeatureFlags.EPIC_OVERLAY_ENABLED
                && shortcut != null
                && "epic".equals(shortcut.getExtra("storeSource"))
                && "1".equals(shortcut.getExtra("epicOverlay"));
    }

    // Install the overlay component (idempotent CDN download) and write the OverlayPath pointer when
    // the toggle is on; strip the pointer when it's off so a disabled overlay leaves no stale key.
    // Runs on the background launch worker (sync file/reg/network I/O is ANR-safe there). Never throws.
    /**
     * Xbox store game (shortcut storeSource=xbox, xboxProductId=…): before Wine starts, sign the launch with
     * the account service and materialise the licensed launch view inside imagefs (XboxRuntime.prepare).
     * Runs on the launch worker; a failure stops the launch with the reason on the loading card.
     */
    private void prepareXboxLaunch() throws Exception {
        File system32 = new File(imageFs.wineprefix, "drive_c/windows/system32");
        if (shortcut == null || !"xbox".equals(shortcut.getExtra("storeSource"))) {
            // An Xbox session that did not end cleanly may have left its modules in this container.
            com.winhub.hernandez.store.xbox.XboxRuntime.restoreSystem32(system32);
            return;
        }
        String id = shortcut.getExtra("xboxProductId");
        if (id == null || id.isEmpty()) throw new IllegalStateException("This Xbox shortcut has no product id; add the game again from the Xbox store.");
        // Windows Runtime classes only the 32-bit registry view has (PropertySet: Xbox sign-in), added to the 64-bit view.
        com.winhub.hernandez.store.xbox.XboxWinRt.ensure(getApplicationContext(), new File(imageFs.wineprefix));
        // What the game's MicrosoftGame.config declares (VC14), installed offline when this container lacks it.
        com.winhub.hernandez.store.xbox.XboxDependencies.ensure(getApplicationContext(), container, id, wineInfo != null && wineInfo.isArm64EC(), label -> {
            preloaderDialog.step(2, label);
            return kotlin.Unit.INSTANCE;
        });
        preloaderDialog.step(2, "Checking the Xbox license…");
        xboxLaunch = com.winhub.hernandez.store.xbox.XboxRuntime.INSTANCE.prepare(getApplicationContext(), id,
                java.util.UUID.randomUUID().toString(), imageFs.getRootDir(),
                system32,
                com.winhub.hernandez.store.xbox.XboxMainActivity.Companion.cloudSaves(this),
                !"0".equals(shortcut.getExtra(com.winhub.hernandez.store.xbox.XboxRuntime.NEXUS_RUNTIME_EXTRA)));
    }

    private void provisionEpicOverlay() {
        if (shortcut == null || !"epic".equals(shortcut.getExtra("storeSource"))) return;
        File prefixDir = new File(ImageFs.find(this).wineprefix);
        try {
            if (!isEpicOverlayEnabledForLaunch()) {
                EpicOverlayManager.stripRegistry(prefixDir);
                return;
            }
            boolean ok = EpicOverlayManager.ensureOverlayInstalled(this, prefixDir);
            if (ok) {
                EpicOverlayManager.writeRegistry(prefixDir);
                // DXVK guarantee note: the EOS overlay renders through the guest's D3D/DXVK path; a
                // software (no3d) wrapper yields a grey overlay. We don't force-rewrite the user's
                // wrapper (that could break the game), but warn when it isn't a DXVK-based one.
                if (this.dxwrapper == null || !this.dxwrapper.contains("dxvk")) {
                    Log.w("XServerDisplayActivity", "Epic overlay ON but dxwrapper is not DXVK-based ("
                            + this.dxwrapper + ") — overlay may render grey; DXVK is recommended");
                }
                Log.i("XServerDisplayActivity", "Epic overlay provisioned for launch");
            } else {
                Log.w("XServerDisplayActivity", "Epic overlay provisioning failed; leaving registry untouched");
            }
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "provisionEpicOverlay failed", t);
        }
    }

    // Synthesise the EOS overlay hotkey (Shift+F3) into the guest — four ordered X calls, mirroring a
    // real keyboard chord. UI-thread only (that's where the OSC injects too). The overlay is ALSO
    // summonable by a physical Shift+F3 independently — this is just a touch-friendly synthesiser.
    private void injectEpicOverlayHotkey() {
        if (xServer == null) return;
        if (XKeycode.KEY_SHIFT_L.id == 0 || XKeycode.KEY_F3.id == 0) return; // guard KEY_NONE/id 0
        try {
            xServer.injectKeyPress(XKeycode.KEY_SHIFT_L);
            xServer.injectKeyPress(XKeycode.KEY_F3);
            xServer.injectKeyRelease(XKeycode.KEY_F3);
            xServer.injectKeyRelease(XKeycode.KEY_SHIFT_L);
        } catch (Throwable t) {
            Log.w("XServerDisplayActivity", "injectEpicOverlayHotkey failed", t);
        }
    }

    // Add the draggable edge-snap Epic pill over the game, only for an Epic shortcut with the overlay
    // toggle on. The pill just synthesises Shift+F3 — it is never a prerequisite for the overlay
    // rendering (a hardware keyboard works regardless). Position is persisted per game.
    private void attachEpicOverlayPill() {
        if (!isEpicOverlayEnabledForLaunch()) return;
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        if (rootView == null) return;
        float density = getResources().getDisplayMetrics().density;
        EpicOverlayPill pill = new EpicOverlayPill(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP | android.view.Gravity.START
        );
        lp.leftMargin = Math.round(8 * density);
        lp.topMargin  = Math.round(120 * density);
        pill.setLayoutParams(lp);
        pill.setOnTapListener(this::injectEpicOverlayHotkey);
        pill.setOnMovedListener((x, y) -> persistHudPosition("epicPillPos", x, y));
        restoreHudPosition(pill, "epicPillPos");
        rootView.addView(pill);
        pill.bringToFront();
    }

    // Force the Wine graphics driver via the prefix registry. Wayland selects winewayland.drv
    // (into our compositor); otherwise we only restore x11 if a prior wayland launch had set it,
    // so normal X11 prefixes are left untouched.
    private void setWineDisplayDriver() {
        File userRegFile = new File(imageFs.getRootDir(), ImageFs.WINEPREFIX + "/user.reg");
        try (WineRegistryEditor reg = new WineRegistryEditor(userRegFile)) {
            if (waylandMode) {
                reg.setStringValue("Software\\Wine\\Drivers", "Graphics", "wayland");
                // Every process of the session is born on the "shell" desktop - explorer's own threads
                // included. Without this explorer starts on "Default" and SetThreadDesktop moves only
                // its main thread to "shell"; the winewayland event thread, clipboard and systray
                // threads stay behind on "Default". The server's desktop-close heuristic
                // (remove_desktop_user: users == top-window owner's running_threads) then holds by
                // coincidence while a game's startup threads come and go - and this Proton base
                // defaults the close timeout to ZERO (server/winstation.c close_timeout_val; upstream
                // Wine waits 1 s), so explorer got WM_CLOSE and the desktop vanished ~0.25 s after it
                // was created (Half-Life 2: 32-bit under FEX, thread churn at startup; the 64-bit AIO
                // test attached before any churn and survived). With all of explorer's threads on
                // "shell" the equality means exactly "no other process is attached" - winhandler.exe
                // and the game hold the desktop for the whole session. The size entry is what
                // explorer's get_default_desktop_size("shell") reads, so an explorer win32u spawns for
                // this desktop (stale-prefix wineboot dialog) creates it at the container size too.
                // Wine reads Software\Wine\Explorer\Desktop in win32u's winstation_init for every
                // process that gets no explicit desktop.
                reg.setStringValue("Software\\Wine\\Explorer", "Desktop", "shell");
                reg.setStringValue("Software\\Wine\\Explorer\\Desktops", "shell", String.valueOf(xServer.screenInfo));
            } else {
                String cur = reg.getStringValue("Software\\Wine\\Drivers", "Graphics", "");
                if ("wayland".equals(cur))
                    reg.setStringValue("Software\\Wine\\Drivers", "Graphics", "x11");
                // Self-healing like Graphics: an X11 launch drops the Wayland desktop seeding so the
                // X11 path stays exactly as it was (explorer /desktop=shell,WxH on the command line).
                if ("shell".equals(reg.getStringValue("Software\\Wine\\Explorer", "Desktop", ""))) {
                    reg.removeValue("Software\\Wine\\Explorer", "Desktop");
                    reg.removeValue("Software\\Wine\\Explorer\\Desktops", "shell");
                }
            }
        }
        // Winlator patches winex11.drv so its init succeeds even with no X server, so it always
        // wins Wine's driver selection. To force winewayland, hide winex11.drv in wayland mode so
        // explorer's LoadLibrary fails and it falls through to wayland. Self-healing: any X11-mode
        // launch restores it, so a crash mid-wayland can never permanently break the X11 path.
        try {
            com.winhub.hernandez.contents.ContentProfile profile =
                    contentsManager.getProfileByEntryName(container.getWineVersion());
            if (profile != null) {
                File libDir = new File(ContentsManager.getInstallDir(this, profile), profile.wineLibPath);
                for (String arch : new String[]{"aarch64-windows", "i386-windows"}) {
                    File drv = new File(libDir, "wine/" + arch + "/winex11.drv");
                    File bak = new File(libDir, "wine/" + arch + "/winex11.drv.bak");
                    if (waylandMode) {
                        if (drv.exists() && !bak.exists()) drv.renameTo(bak);
                    } else {
                        if (bak.exists() && !drv.exists()) bak.renameTo(drv);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "wayland: winex11 hide/restore failed", e);
        }
    }

    private void applyGeneralPatches(Container container) {
        File rootDir = imageFs.getRootDir();
        if (!container.isOverlay()) TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "container_pattern_common.tzst", rootDir);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst", new File(getFilesDir(), "pulseaudio"));
        removePaleMoonDesktopShortcut(container);
        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("graphicsDriver", null);
        container.putExtra("desktopTheme", null);
    }

    // Pale Moon ships only as a START MENU launcher now — never as a desktop shortcut. The repacked
    // container_pattern_common.tzst no longer contains the Desktop "Pale Moon.lnk", but the overlay
    // extract above is purely additive (it can't remove a file an EXISTING container already got from
    // an older pattern), so delete the Desktop shortcut here. This runs once per container when the
    // PATTERN_CONTENT_VERSION gate trips, cleaning already-created containers too. getStartMenuDir()
    // is deliberately left untouched so Pale Moon stays launchable from the Windows Start Menu. The
    // sibling ".desktop" is the file ContainerManager.loadShortcuts() lazily generates from the .lnk,
    // so removing both also clears the stray Pale Moon entry from the Games tab. (2026-08-06)
    private void removePaleMoonDesktopShortcut(Container container) {
        File desktopDir = container.getDesktopDir();
        File lnk = new File(desktopDir, "Pale Moon.lnk");
        File desktop = new File(desktopDir, "Pale Moon.desktop");
        if (lnk.isFile()) lnk.delete();
        if (desktop.isFile()) desktop.delete();
    }

    /**
     * The running game's exe basename (e.g. {@code dirt3_game.exe}) that the drift checker resolves to a
     * real Linux pid. Prefers the value already captured from the mapped window's class name; falls back to
     * the shortcut's Exec line. Lower-cased for matching. Null when neither is available.
     */
    private String gameExeBasename() {
        if (affinityTargetExe != null) return affinityTargetExe;
        try {
            if (shortcut != null) {
                String e = shortcut.getExecutable();
                if (e != null && !e.isEmpty()) return e.trim().toLowerCase();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void assignTaskAffinity(Window window) {
        if (taskAffinityMask == 0 || taskAffinityMaskWoW64 == 0) return;
        int processId = window.getProcessId();
        String className = window.getClassName();
        int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

        // Apply immediately via winhandler so the cores take effect right now (by pid when known, else by
        // class name — _NET_WM_PID often arrives after the window maps).
        if (processId > 0) winHandler.setProcessAffinity(processId, processAffinity);
        else if (!className.isEmpty()) winHandler.setProcessAffinity(className, processAffinity);

        // Arm the drift checker — but only for a genuine restriction (fewer cores than available); no point
        // maintaining an "all cores" default. The checker resolves the game's real LINUX pid by exe name and
        // re-pins HOST-SIDE, so it no longer depends on the winhandler Windows-pid (which isn't a /proc pid).
        if (!className.isEmpty()) {
            boolean restrict = Integer.bitCount(processAffinity & 0xff) < Runtime.getRuntime().availableProcessors();
            if (restrict) {
                String exe = className.toLowerCase();
                int slash = Math.max(exe.lastIndexOf('/'), exe.lastIndexOf('\\'));
                if (slash >= 0) exe = exe.substring(slash + 1);
                if (!exe.equals(affinityTargetExe)) affinityLinuxPid = -1; // new target -> re-resolve
                affinityTargetExe = exe;
                affinityTargetMask = processAffinity & 0xff;
                startAffinityReapply();
            }
        }
    }

    /**
     * Wayland counterpart of {@link #assignTaskAffinity(Window)}. A Wayland session has no X window
     * events, so before this the launch-time mask (container/shortcut CPU list, or Prefer Big Cores)
     * was never applied there — only the in-game toggle and the Task Manager armed anything. The
     * compositor reports the program behind the first game window that presents GPU frames: its Linux
     * pid (the Wayland client's credentials, i.e. the real /proc pid, no exe scan needed) and its
     * executable name. The mask goes Windows-side by name, as X11's class-name path does, and the
     * host-side drift checker is armed on the pid. The compositor cannot tell a WoW64 program apart,
     * so the 64-bit mask applies. UI thread.
     */
    private void assignWaylandTaskAffinity(int pid, String program) {
        if (taskAffinityMask == 0) return;
        final int processAffinity = taskAffinityMask;
        String exe = program != null ? program.trim().toLowerCase(java.util.Locale.ROOT) : "";
        int slash = Math.max(exe.lastIndexOf('/'), exe.lastIndexOf('\\'));
        if (slash >= 0) exe = exe.substring(slash + 1);
        if (!exe.isEmpty() && winHandler != null) winHandler.setProcessAffinity(exe, processAffinity);
        boolean restrict = Integer.bitCount(processAffinity & 0xff) < Runtime.getRuntime().availableProcessors();
        String msg = "launch CPU affinity for " + (exe.isEmpty() ? "an unnamed program" : exe) + " (pid " + pid
                + "): mask 0x" + Integer.toHexString(processAffinity & 0xffff)
                + (restrict ? ", kept on those cores by the drift checker" : " (every core: nothing to enforce)");
        Log.i("XServerDisplayActivity", "wayland: " + msg);
        try { com.winhub.hernandez.wayland.WaylandCompositor.nativeLogPerf(msg); } catch (Throwable ignored) {}
        if (!restrict) return;
        if (!exe.isEmpty()) {
            if (!exe.equals(affinityTargetExe)) affinityLinuxPid = -1; // new target -> re-resolve
            affinityTargetExe = exe;
            if (pid > 0) affinityLinuxPid = pid;
            affinityTargetMask = processAffinity & 0xff;
            startAffinityReapply();
        } else if (pid > 0) {
            // No name to re-resolve by if it restarts: pin it once, host-side.
            ProcessHelper.setLinuxAffinity(pid, processAffinity & 0xff);
        }
    }

    /**
     * Prefer Big Cores' core list ({@link com.winhub.hernandez.perf.CpuTopology}), and — once per session —
     * which cores it picked and why, to logcat and, on Wayland, the session log.
     */
    private boolean bigCoresLogged = false;
    private String detectBigCoreCpuListLogged() {
        String list = com.winhub.hernandez.perf.CpuTopology.INSTANCE.detectBigCoreCpuList();
        if (!bigCoresLogged) {
            bigCoresLogged = true;
            String msg = "prefer big cores: " + com.winhub.hernandez.perf.CpuTopology.INSTANCE.describeBigCores();
            Log.i("XServerDisplayActivity", msg);
            if (waylandMode) {
                try { com.winhub.hernandez.wayland.WaylandCompositor.nativeLogPerf(msg); } catch (Throwable ignored) {}
            }
        }
        return list;
    }

    /**
     * Re-pin the ALREADY-RUNNING guest process tree when Prefer Big Cores is toggled mid-game (the old
     * behavior only changed the mask for newly-spawned processes, leaving the current game on 0-7).
     * Enumerates every guest process via the WinHandler process list and sets each one's affinity —
     * wine maps SetProcessAffinityMask to sched_setaffinity on the process's Linux threads, so the
     * game's Cpus_allowed_list shrinks to the big cluster. ON snapshots each process's prior mask;
     * OFF restores it verbatim (revert philosophy). Best-effort: no-op if WinHandler isn't ready.
     */
    private void reapplyBigCoresToRunningGuest(boolean on) {
        if (winHandler == null) return;
        if (!on && bigCoreAffinitySnapshot.isEmpty()) return; // nothing we changed -> nothing to revert
        final int bigMask;
        if (on) {
            String bigList = detectBigCoreCpuListLogged();
            if (bigList == null || bigList.isEmpty()) return; // topology unknown -> nothing to pin to
            bigMask = ProcessHelper.getAffinityMask(bigList);
        } else {
            bigMask = 0;
        }
        // Fallback mask for OFF when a process has no snapshot (e.g. spawned after ON): the resolved
        // container/shortcut cpuList, else all cores.
        String cpuList = shortcut != null ? shortcut.getExtra("cpuList", container.getCPUList(true))
                                          : container.getCPUList(true);
        final int fallbackMask = ProcessHelper.getAffinityMask(
                (cpuList != null && !cpuList.isEmpty()) ? cpuList : Container.getFallbackCPUList());

        final OnGetProcessInfoListener prev = winHandler.getOnGetProcessInfoListener();
        final java.util.ArrayList<ProcessInfo> collected = new java.util.ArrayList<>();
        winHandler.setOnGetProcessInfoListener((index, count, info) -> {
            if (index == 0) collected.clear();
            if (info != null && info.pid > 0) collected.add(info);
            if (count == 0 || index == count - 1) {
                for (ProcessInfo pi : collected) {
                    if (on) {
                        if (!bigCoreAffinitySnapshot.containsKey(pi.pid))
                            bigCoreAffinitySnapshot.put(pi.pid, pi.affinityMask); // prior mask, once
                        winHandler.setProcessAffinity(pi.pid, bigMask);
                    } else {
                        Integer orig = bigCoreAffinitySnapshot.get(pi.pid);
                        winHandler.setProcessAffinity(pi.pid, orig != null ? orig : fallbackMask);
                    }
                }
                if (!on) bigCoreAffinitySnapshot.clear();
                Log.d("XServerDisplayActivity", "Prefer big cores live re-pin (" + (on ? "ON" : "OFF")
                        + "): " + collected.size() + " guest process(es)");
                winHandler.setOnGetProcessInfoListener(prev); // hand the listener back
            }
        });
        winHandler.listProcesses();
    }

    /** Build the Fusion HUD and add it. Safe to call live (UI thread). */
    private void buildFusionHud(String fpsConfigString) {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        fusionHud = new com.winhub.hernandez.widget.fusionhud.FusionHudView(this);
        fusionHud.setFpsCounter(fpsCounter);
        // Fusion sits in the top-right corner until dragged, anchored on its right edge so a tap to a
        // higher level grows it leftward into the screen.
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP | android.view.Gravity.END
        );
        plp.topMargin = 10;
        plp.rightMargin = 10;
        fusionHud.setLayoutParams(plp);
        fusionHud.applyConfig(fpsConfigString);
        if (hudEngineShort != null) fusionHud.setEngineLabel(hudEngineShort);
        if (hudGpuName != null) fusionHud.setGpuModel(hudGpuName);
        // A HUD built (or rebuilt, on a style switch) mid-session picks up the GPU-name spoof this
        // session delivered, the same way it picks up the display server; null on X11, on an unspoofed
        // session and on one whose spoof never got out, so the GPU row is what it always was.
        fusionHud.setGpuSpoofName(deliveredGpuSpoofName);
        fusionHud.setDisplayServer(hudDisplayServerLabel());
        // SurfaceDirect only: present latency / skipped frames for the level 4 PRES row. The renderer is
        // looked up on every read, so a renderer that is swapped later is followed.
        fusionHud.setPresentStatsProvider(() -> {
            com.winhub.hernandez.renderer.HostRenderer r = xServerView != null ? xServerView.getRenderer() : null;
            return r instanceof com.winhub.hernandez.renderer.egl.EglRenderer
                    ? ((com.winhub.hernandez.renderer.egl.EglRenderer) r).readPresentStats() : null;
        });
        fusionHud.setHdrState(hudHdrCode());
        // Level 4 stack-layer versions: Proton/Wine, the graphics-driver wrapper package, and DX wrapper.
        if (wineInfo != null) fusionHud.setWineVersion(wineInfo.toString());
        fusionHud.setGraphicsWrapper(friendlyGraphicsWrapper());
        if (dxwrapperConfig != null) fusionHud.setDxWrapper(dxwrapperConfig.get("version"), dxwrapperConfig.get("vkd3dVersion"));
        // Fusion: a tap steps the detail level (persist to hudSize), long-press toggles the lock.
        fusionHud.setOnSizeCycledListener((token) -> persistHudConfigKey("hudSize", token));
        fusionHud.setOnLockChangedListener((locked) -> persistHudConfigKey("hudLocked", locked ? "1" : "0"));
        fusionHud.setOnMovedListener((x, y) -> persistHudPosition("hudPosFusion", x, y));
        restoreHudPosition(fusionHud, "hudPosFusion");
        // A dragged HUD keeps its right edge when it changes size, so a wider size could push it past
        // the left edge. Pull it back on screen whenever its size changes (setX/setY don't relayout).
        fusionHud.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            View parent = (View) v.getParent();
            if (parent == null || v.getWidth() == 0 || v.getHeight() == 0) return;
            float x = Math.max(0, Math.min(v.getX(), Math.max(0, parent.getWidth() - v.getWidth())));
            float y = Math.max(0, Math.min(v.getY(), Math.max(0, parent.getHeight() - v.getHeight())));
            if (x != v.getX()) v.setX(x);
            if (y != v.getY()) v.setY(y);
        });
        fusionHud.setVisibility(frameRatingWindowId != -1 && hudCounterEnabled ? View.VISIBLE : View.GONE);
        rootView.addView(fusionHud);
    }

    /**
     * Short friendly name (+ bundled build date) for the selected graphics-driver WRAPPER package, for
     * the Fusion Mega "Wrapper" row — the same package the graphics-driver picker names
     * (GameNative / bcn_layer / comp / …). Name from {@code graphicsDriver}; version best-effort from
     * the bundled-wrapper catalog ({@link com.winhub.hernandez.contents.WrapperManager#listSlots}).
     */
    private String friendlyGraphicsWrapper() {
        String gd = graphicsDriver == null ? "" : graphicsDriver;
        if (gd.isEmpty()) return null;
        String name;
        String slotFile = null;
        if (gd.startsWith("wrapper-gamenative"))      { name = "GameNative"; slotFile = "wrapper-gamenative.tzst"; }
        else if (gd.startsWith("wrapper-compat-bcn")) { name = "comp"; }
        else if (gd.startsWith("wrapper-bcn_layer")
                 || gd.startsWith("leegao_bcn"))       { name = "bcn_layer"; slotFile = "leegao_bcn.tzst"; }
        else if (gd.startsWith("wrapper-leegao"))      { name = "leegao"; slotFile = "wrapper-leegao.tzst"; }
        else if (gd.startsWith("wrapper-legacy"))      { name = "Bionic"; slotFile = "wrapper-legacy.tzst"; }
        else if (gd.startsWith("wrapper-original"))    { name = "Original"; slotFile = "wrapper-original.tzst"; }
        else if (gd.startsWith("turnip"))             { name = "Turnip"; }
        else if (gd.startsWith("wrapper"))            { name = "Wrapper"; slotFile = "wrapper.tzst"; }
        else                                          { name = gd; }
        if (slotFile != null) {
            try {
                for (com.winhub.hernandez.contents.WrapperManager.WrapperSlot slot :
                        new com.winhub.hernandez.contents.WrapperManager(this).listSlots()) {
                    if (slotFile.equals(slot.fileName) && slot.version != null) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{6,8})").matcher(slot.version);
                        if (m.find()) name = name + " " + m.group(1);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return name;
    }

    /**
     * Merge a single HUD config key into the container/shortcut's saved FPS config and persist it, so
     * an in-place change (Fusion tap→level, long-press→lock) survives relaunch and the menus reflect it.
     */
    private void persistHudConfigKey(String key, String value) {
        String cfgStr = resolvedFPSCounterConfig();
        com.winhub.hernandez.core.KeyValueSet cfg = new com.winhub.hernandez.core.KeyValueSet(cfgStr);
        cfg.put(key, value);
        String updated = cfg.toString();
        persistFPSCounterConfig(updated);
        // Keep the in-game drawer's HUD pane in sync with the live change.
        com.winhub.hernandez.ui.XServerDrawerState.INSTANCE.setFpsConfig(updated);
    }

    private void changeFrameRatingVisibility(Window window, Property property) {
        if (fusionHud == null) return;

        if (property != null) {
            boolean isMesaDrv = property.nameAsString().contains("_MESA_DRV");
            if (isMesaDrv) mesaDrvWindowIds.add(window.id);
            // Bind when unbound, OR UPGRADE off a non-_MESA_DRV *fallback* window (the focused-window
            // fallback in the unmap branch — e.g. the static AIO menu the HUD parked on after a cube
            // closed) to this real render window. Without the upgrade the binding stays pinned to the
            // fallback and the FPS counter keeps ticking a window that isn't presenting, so every cube
            // after the first reads 0. Don't steal from another real render window (real games keep
            // their single window). NB mesaDrvWindowIds already includes window.id, so the contains()
            // check tests the OLD bound id.
            boolean boundToFallback = frameRatingWindowId != -1 && !mesaDrvWindowIds.contains(frameRatingWindowId);
            if (isMesaDrv && (frameRatingWindowId == -1 || boundToFallback)) {
                frameRatingWindowId = window.id;
                Log.d("XServerDisplayActivity", "Showing hud for Window " + window.getName());

                runOnUiThread(() -> {
                    // Respect the master toggle: a binding window must not reveal the HUD while "Show
                    // HUD" is off. When it's turned back on, onFpsConfigApply re-asserts visibility.
                    if (!hudCounterEnabled) return;
                    if (fusionHud != null) fusionHud.setVisibility(View.VISIBLE);
                });
            }
            if (property.nameAsString().contains("_MESA_DRV_GPU_NAME")) {
                // Reduce the raw renderer string (e.g. "zink Vulkan 1.4(Wrapper(Adreno (TM) 750)
                // (MESA_TURNIP))") to just the chip ("Adreno 750") for the HUD GPU-model row.
                hudGpuName = com.winhub.hernandez.core.GPUInformation.extractModelName(property.toString());
                runOnUiThread(() -> {
                    if (fusionHud != null) fusionHud.setGpuModel(hudGpuName);
                });
            }
        }
        else {
            mesaDrvWindowIds.remove(window.id);
            // Only react when the HUD's OWN bound window unmapped — an unrelated window unmapping must
            // not hide the HUD. And because games (Dirt 3 / Dirt Showdown) open an intro window then
            // swap to the real render window, re-bind to another still-mapped _MESA_DRV window before
            // giving up — otherwise the HUD vanishes permanently when the intro window closes.
            if (frameRatingWindowId != -1 && window.id == frameRatingWindowId) {
                // The bound render window went away — any GL/Zink self-heal target is now stale, so
                // clear it and let driveHudFrameTick re-evaluate against the new binding.
                glZinkHealedWindowId = -1;
                Integer next = mesaDrvWindowIds.isEmpty() ? null : mesaDrvWindowIds.iterator().next();
                // GLX/OpenGL fallback: _MESA_DRV rides the Vulkan surface, so a GL/Zink title that
                // recreates its render window (e.g. the AIO test's OpenGL cube) leaves NO _MESA_DRV
                // window to rebind to — the HUD would vanish even though a live game window is focused
                // right there. This is why it works for every other API (D3D*/Vulkan keep _MESA_DRV) but
                // breaks only on OpenGL. Follow the focused application window instead of giving up.
                if (next == null) {
                    try {
                        Window focused = xServer.windowManager.getFocusedWindow();
                        if (focused != null && focused.id != window.id && focused.isApplicationWindow())
                            next = focused.id;
                    } catch (Exception ignore) {}
                }
                if (next != null) {
                    frameRatingWindowId = next;   // keep the HUD visible, now tracking the new window
                    Log.d("XServerDisplayActivity", "Re-binding hud to Window id " + next);
                } else {
                    frameRatingWindowId = -1;
                    Log.d("XServerDisplayActivity", "Hiding hud for Window " + window.getName());
                    fpsCounter.reset();
                    runOnUiThread(() -> {
                        if (fusionHud != null) fusionHud.setVisibility(View.GONE);
                    });
                }
            }
        }
    }


    private void MoveCursorToTouchpoint() {
        // Toggle the preference value
        boolean currentValue = preferences.getBoolean("move_cursor_to_touchpoint", false);
        boolean newValue = !currentValue;
        
        preferences.edit().putBoolean("move_cursor_to_touchpoint", newValue).apply();
        XServerDrawerState.INSTANCE.setMoveCursorToTouchpoint(newValue);

        // Update the touchpadView state
        if (touchpadView != null) {
            touchpadView.setMoveCursorToTouchpoint(newValue);
        }

        // Push back into the drawer so the chip renders its on/off state (matches the
        // Relative Mouse / Disable Mouse toggles in setupUI).
        XServerDrawerState.INSTANCE.setMoveCursorToTouchpoint(newValue);
    } // Closes MoveCursorToTouchpoint

    /** Persist the drawer's gesture settings and apply them to the live touchpad. */
    private void applyGestureConfig() {
        XServerDrawerState state = XServerDrawerState.INSTANCE;
        boolean dragSelect = state.getGestureDragSelectValue();
        boolean longPress = state.getGestureLongPressRightClickValue();
        int longPressMs = state.getGestureLongPressMsValue();

        preferences.edit()
            .putBoolean("gesture_drag_select", dragSelect)
            .putBoolean("gesture_long_press_rmb", longPress)
            .putInt("gesture_long_press_ms", longPressMs)
            .apply();

        if (touchpadView != null)
            touchpadView.setGestureConfig(dragSelect, longPress, longPressMs);
    }

    private void showActiveWindowsDialog() {
        ArrayList<com.winhub.hernandez.xserver.Window> activeWindows = new ArrayList<>();
        ArrayList<android.graphics.Bitmap> activeIcons = new ArrayList<>();
        try {
            try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {
                findAppWindowsForCompose(xServer.windowManager.rootWindow, activeWindows);
                for (com.winhub.hernandez.xserver.Window w : activeWindows) {
                    activeIcons.add(xServer.pixmapManager.getWindowIcon(w));
                }
            }
        } catch (Exception e) {
            Log.e("XServerDisplayActivity", "Error reading windows", e);
        }

        ArrayList<XServerDialogState.ActiveWindow> windowInfoList = new ArrayList<>();
        for (int i = 0; i < activeWindows.size(); i++) {
            com.winhub.hernandez.xserver.Window w = activeWindows.get(i);
            String title = w.getName();
            String cls   = w.getClassName() != null ? w.getClassName() : "";
            if (title == null || title.isEmpty()) title = cls;
            if (title.isEmpty()) title = "Unnamed Window";
            windowInfoList.add(new XServerDialogState.ActiveWindow(
                title, cls, activeIcons.get(i), null, w.getHandle()));
        }

        XServerDialogState ds = XServerDialogState.INSTANCE;
        ds.setAwWindows(windowInfoList);
        ds.onWindowClick = (cls, handle) -> {
            WinHandler wh = getWinHandler();
            if (wh != null) wh.bringToFront(cls, handle);
        };
        ds.show(XServerDialogState.ActiveDialog.ACTIVE_WINDOWS);
    }

    private void findAppWindowsForCompose(com.winhub.hernandez.xserver.Window parent,
                                          ArrayList<com.winhub.hernandez.xserver.Window> result) {
        if (parent == null) return;
        for (com.winhub.hernandez.xserver.Window child : parent.getChildren()) {
            if (child.attributes.isMapped()) {
                String className = child.getClassName();
                boolean isSystem = false;
                if (className != null) {
                    String cls = className.toLowerCase();
                    if (cls.contains("progman") || cls.contains("shell_traywnd") || cls.equals("explorer.exe"))
                        isSystem = true;
                }
                String title  = child.getName();
                boolean hasTitle = title != null && !title.isEmpty();
                boolean hasClass = className != null && !className.isEmpty();
                if (!isSystem && (hasTitle || hasClass)) {
                    if (child.getWidth() < xServer.screenInfo.width || child.getHeight() < xServer.screenInfo.height
                            || child.getParent() != xServer.windowManager.rootWindow
                            || (title != null && !title.isEmpty()
                                && !title.equalsIgnoreCase("Default - Wine desktop"))) {
                        result.add(child);
                        continue;
                    }
                }
            }
            findAppWindowsForCompose(child, result);
        }
    }

    private void showMagnifierOverlay() {
        // Drive the magnifier through the HostRenderer interface (declares get/setMagnifierZoom,
        // implemented by Vulkan). The old code cast to the removed GL renderer and no-op'd
        // for any other renderer, so on the default Vulkan renderer the overlay opened stuck at 100%
        // and the +/- buttons did nothing (issue #22). VulkanRenderer.setMagnifierZoom applies
        // the zoom live via updateTransform().
        HostRenderer r = xServerView != null ? xServerView.getRenderer() : null;
        XServerDialogState ds = XServerDialogState.INSTANCE;

        ds.setMagnifierZoom(r != null ? r.getMagnifierZoom() : 1.0f);
        ds.onMagnifierZoom = delta -> {
            if (r == null) return;
            float z = Mathf.clamp(r.getMagnifierZoom() + delta, 1.0f, 3.0f);
            r.setMagnifierZoom(z);
            ds.setMagnifierZoom(z);
        };
        ds.onMagnifierHide = () -> ds.setMagnifierVisible(false);
        ds.setMagnifierVisible(true);
    }

    private void startTmPolling() {
        registerTmProcessInfoListener();
        tmPollHandler.removeCallbacks(tmPollRunnable);
        tmPollHandler.post(tmPollRunnable);
    }

    // ---- Component installer auto-exit (Phase 3b) ----

    private void startInstallerWatch() {
        installerProcSeen = false;
        installerGoneTicks = 0;
        installerWatchHandler.removeCallbacks(installerWatchRunnable);
        // Give Wine a head start to boot and actually launch the installer before we begin watching,
        // so we don't conclude "finished" before it has even appeared.
        installerWatchHandler.postDelayed(installerWatchRunnable, 8000);
    }

    private boolean looksLikeInstallerProc(String name) {
        if (name == null) return false;
        String target = componentInstallerExe != null ? componentInstallerExe.toLowerCase() : "";
        // The bootstrapper may relaunch itself from %temp% under its original name and spawn msiexec,
        // so match the staged name plus the usual installer/runtime process names.
        return name.equals(target)
                || name.contains("msiexec")
                || name.contains("redist")
                || name.contains("vcredist")
                || name.contains("dotnet")
                || name.contains("ndp")
                || name.contains("setup")
                || name.contains("install");
    }

    private int installerTicksTotal = 0;

    /** Short status for installer sessions (label from the intent, else the watched exe name). */
    private String installerLabel() {
        String label = getIntent().getStringExtra("component_installer_label");
        return (label != null && !label.isEmpty()) ? label : componentInstallerExe;
    }

    private void installerReminderToast() {
        try {
            runOnUiThread(() -> android.widget.Toast.makeText(this,
                    "Installing " + installerLabel() + "… follow the installer's prompts if it shows any. "
                            + "This session closes by itself when it is done.",
                    android.widget.Toast.LENGTH_LONG).show());
        } catch (Throwable ignored) {}
    }

    private void evaluateInstallerTick() {
        if (componentInstallerExe == null) return;
        // Silent installer (no window ever mapped -> overlay still up): keep its status line current.
        if (!winStarted) preloaderHint("Installing " + installerLabel()
                + "… this can take a few minutes. Leave it running — this screen closes by itself when it is done.");
        // Visible installer: repeat the reminder roughly every 45 s so a long install never looks stuck.
        if (winStarted && (++installerTicksTotal % 45) == 0) installerReminderToast();
        boolean present = false;
        for (String n : installerTickNames) {
            if (looksLikeInstallerProc(n)) { present = true; break; }
        }
        if (present) {
            installerProcSeen = true;
            installerGoneTicks = 0;
        } else if (installerProcSeen) {
            installerGoneTicks++;
            // Require a few consecutive empty ticks so a brief gap (bootstrapper exits, then msiexec
            // spawns) doesn't trip an early exit.
            if (installerGoneTicks >= 3) {
                installerWatchHandler.removeCallbacks(installerWatchRunnable);
                componentInstallerExe = null;
                if (winHandler != null) winHandler.setOnGetProcessInfoListener(null);
                runOnUiThread(XServerDisplayActivity.this::exit);
            }
        }
    }

    private void stopTmPolling() {
        tmPollHandler.removeCallbacks(tmPollRunnable);
        if (winHandler != null) winHandler.setOnGetProcessInfoListener(null);
        XServerDialogState.INSTANCE.setTmProcesses(new ArrayList<>());
    }

    /** Per-game/-container "close session when the game exits", defaulting to ON. */
    private boolean resolvedAutoCloseOnExit() {
        if (container == null) return false;
        String def = container.getExtra("autoCloseOnExit", "1");
        String v = shortcut != null ? shortcut.getExtra("autoCloseOnExit", def) : def;
        return v.equals("1");
    }

    private void startGameExitWatch() {
        autoCloseExeName = getExecutable().toLowerCase();
        gameProcSeen = false;
        gameGoneTicks = 0;
        gameExitWatchHandler.removeCallbacks(gameExitWatchRunnable);
        // Give Wine time to boot and the game to actually appear before we start watching, so a slow
        // first launch isn't mistaken for "already exited".
        gameExitWatchHandler.postDelayed(gameExitWatchRunnable, 12000);
    }

    private void evaluateGameExitTick() {
        if (!autoCloseOnExitEnabled || autoCloseExeName == null) return;
        boolean present = false;
        for (String n : gameTickNames) {
            if (n.equals(autoCloseExeName)) { present = true; break; }
        }
        if (present) {
            gameProcSeen = true;
            gameGoneTicks = 0;
        } else if (gameProcSeen) {
            // EA launcher-chain titles hand the game exe off more than once (stub exe → EA Desktop
            // relaunch, and again after a first-ever activation), with gaps longer than three ticks.
            // The SteamLite agent (steam.exe) holds the Steam session through those gaps and bounds
            // them itself (900 s for the first hand-off, 60 s for later ones), so while the agent is
            // alive the game is not over; when the agent tears down, its exit ends the session through
            // the guest termination callback. Closing here mid-hand-off killed the relaunch.
            if (realSteamEaChain && gameTickNames.contains("steam.exe")) {
                gameGoneTicks = 0;
                return;
            }
            gameGoneTicks++;
            // Require a few consecutive empty ticks so a brief gap (e.g. a loader that relaunches the
            // same exe) doesn't trigger an early close.
            if (gameGoneTicks >= 3) {
                gameExitWatchHandler.removeCallbacks(gameExitWatchRunnable);
                autoCloseOnExitEnabled = false;
                if (winHandler != null) winHandler.setOnGetProcessInfoListener(null);
                runOnUiThread(XServerDisplayActivity.this::exit);
            }
        }
    }

    private void setupTmCallbacks() {
        XServerDialogState ds = XServerDialogState.INSTANCE;

        ds.onTmRefresh = () -> {
            if (winHandler != null) winHandler.listProcesses();
            updateTmCpuMemory(ds);
        };

        ds.onTmDismissed = () -> stopTmPolling();

        // Show the Compose New Task dialog instead of the native ContentDialog.prompt — the native
        // prompt is invisible over the Vulkan fullscreen SurfaceView (same class of bug that
        // killed the old Task Manager confirm). The submit path is unchanged: OK runs winHandler.exec.
        ds.onTmNewTask = () -> ds.show(XServerDialogState.ActiveDialog.NEW_TASK);
        ds.onTmNewTaskSubmit = command -> { if (winHandler != null) winHandler.exec(command); };

        // Bring to Front: drive it host-side (renderer-agnostic). Look up the real X window for the
        // target Windows pid, set X-server input focus on it, and send bringToFront with the REAL
        // window handle (not 0). On native-rendering Vulkan a UDP-only restack may have no
        // visible effect, so we also raise + redraw via the host window manager (mirrors
        // DesktopHelper.setFocusedWindow / GameNative). Falls back to a plain by-name UDP send if
        // we can't resolve the window.
        ds.onTmBringToFront = (name, pid) -> {
            if (winHandler == null) return;
            Window target = null;
            try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                target = xServer.windowManager.findWindowWithProcessId(pid);
            } catch (Exception ignored) {}
            if (target != null) {
                final Window window = target;
                try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    Window parent = window.getParent();
                    boolean parentIsRoot = parent != null && parent == xServer.windowManager.rootWindow;
                    xServer.windowManager.setFocus(window,
                        parentIsRoot ? WindowManager.FocusRevertTo.POINTER_ROOT : WindowManager.FocusRevertTo.PARENT);
                } catch (Exception ignored) {}
                winHandler.bringToFront(window.getClassName(), window.getHandle());
                // Force a recomposite so the restack is visible on the native Vulkan path too.
                try { xServerView.requestRender(); } catch (Exception ignored) {}
            } else {
                // Couldn't resolve the window host-side; fall back to a plain by-name UDP raise.
                winHandler.bringToFront(name);
            }
        };

        // End Process runs the same renderer-agnostic winhandler command the rest of the app uses.
        // It used to be wrapped in a native ContentDialog.confirm, but that dialog does not display
        // over the Vulkan fullscreen SurfaceView, so End Process silently did nothing there (the
        // confirm never appeared). Run the command directly so it works on every renderer. (The deeper
        // cause of the Vulkan breakage was this whole method bailing before the callbacks were
        // wired — now fixed by calling setupTmCallbacks() earlier.)
        ds.onTmKillProcess = name -> {
            if (winHandler != null) winHandler.killProcess(name);
        };

        ds.onTmSetAffinity = (pid, mask) -> {
            if (winHandler != null) {
                winHandler.setProcessAffinity(pid, mask);
                // Point the drift checker at the game exe + this mask so it survives past the TM close: it
                // resolves the real Linux pid and re-pins host-side on thread growth. A restriction arms it;
                // "all cores" clears the target. (Targets the game exe — the common case of pinning the game.)
                String exe = gameExeBasename();
                if (exe != null && Integer.bitCount(mask & 0xff) < Runtime.getRuntime().availableProcessors()) {
                    if (!exe.equals(affinityTargetExe)) affinityLinuxPid = -1;
                    affinityTargetExe = exe;
                    affinityTargetMask = mask & 0xff;
                    startAffinityReapply();
                } else if (Integer.bitCount(mask & 0xff) >= Runtime.getRuntime().availableProcessors()) {
                    affinityTargetMask = 0;
                }
            }
        };

        ds.onTmQueryAffinity = pid -> {
            if (winHandler == null) return -1;
            Integer m = winHandler.getManualAffinity(pid);
            return m != null ? m : -1;
        };

        registerTmProcessInfoListener();

        ds.setTmContainerInfo(buildTmContainerInfo());
        updateTmCpuMemory(ds);
    }

    // The active container's config for the Task Manager header. Set once (it doesn't change while
    // the game runs). Uses the same resolved getters the launch path uses so it reflects per-game
    // shortcut overrides, not just the raw container.
    private XServerDialogState.TmContainerInfo buildTmContainerInfo() {
        try {
            String wine = wineInfo != null ? wineInfo.toString() : "—";
            String res = container != null ? container.getScreenSize() : "—";
            int cores = Runtime.getRuntime().availableProcessors();
            String soc = "";
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    && android.os.Build.SOC_MODEL != null && !android.os.Build.SOC_MODEL.isEmpty()) {
                soc = " · " + android.os.Build.SOC_MODEL;
            }
            String device = android.os.Build.MODEL + soc + " · " + cores + " cores · Android "
                + android.os.Build.VERSION.RELEASE;
            // The trailing flag is passed explicitly: a Kotlin default value is not visible from
            // Java, so the old nine-argument call stopped compiling when the field was added.
            return new XServerDialogState.TmContainerInfo(
                wine, dxwrapper, resolvedRenderer(),
                waylandMode ? waylandDriverSummary() : graphicsDriver, res, device,
                waylandMode ? "Wayland" : "X11", hdrRowValue(), deliveredGpuSpoofName);
        } catch (Exception e) {
            return null;
        }
    }

    private void registerTmProcessInfoListener() {
        XServerDialogState ds = XServerDialogState.INSTANCE;
        if (winHandler != null) {
            winHandler.setOnGetProcessInfoListener(new OnGetProcessInfoListener() {
                private final ArrayList<XServerDialogState.TmProcess> buffer = new ArrayList<>();
                private final java.util.HashSet<Integer> livePids = new java.util.HashSet<>();

                @Override
                public void onGetProcessInfo(int index, int numProcesses, ProcessInfo info) {
                    android.graphics.Bitmap icon = null;
                    try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                        com.winhub.hernandez.xserver.Window w = xServer.windowManager.findWindowWithProcessId(info.pid);
                        if (w != null) icon = xServer.pixmapManager.getWindowIcon(w);
                    } catch (Exception ignored) {}

                    final android.graphics.Bitmap finalIcon = icon;
                    runOnUiThread(() -> {
                        if (index == 0) { buffer.clear(); livePids.clear(); }
                        livePids.add(info.pid);
                        // Show the mask the USER applied, not the guest's stale GetProcessAffinityMask
                        // readback (which keeps reporting the full mask under wow64/FEX). Falls back to
                        // the guest value when the user never set an affinity for this pid.
                        Integer override = winHandler != null ? winHandler.getManualAffinity(info.pid) : null;
                        int displayMask = override != null ? override : info.affinityMask;
                        buffer.add(new XServerDialogState.TmProcess(
                            index, info.pid, info.name,
                            info.getFormattedMemoryUsage(), info.wow64Process, displayMask, finalIcon));
                        if (numProcesses == 0 || index == numProcesses - 1) {
                            ds.setTmProcesses(new ArrayList<>(buffer));
                            ds.setTmCount(numProcesses);
                            if (winHandler != null) {
                                // Enumerations overlap and share buffer/livePids (a concurrent one's
                                // index-0 reset wipes livePids mid-cycle), so only prune on a clean,
                                // complete pass -- every pid seen exactly once. Pruning off a truncated
                                // livePids would wrongly drop a still-valid override the instant it's set.
                                if (numProcesses > 0 && livePids.size() == numProcesses) {
                                    winHandler.retainManualAffinities(livePids);
                                }
                                // Re-pin survivors so threads spawned since the last set (which escape
                                // back to all cores) get bound. Idempotent and race-free.
                                winHandler.reapplyManualAffinities();
                            }
                        }
                    });
                }
            });
        }
    }

    private void updateTmCpuMemory(XServerDialogState ds) {
        try {
            short[] clocks = CPUStatus.getCurrentClockSpeeds();
            int total = 0; short maxClock = 0;
            ArrayList<String> cores = new ArrayList<>();
            for (int i = 0; i < clocks.length; i++) {
                short max = CPUStatus.getMaxClockSpeed(i);
                cores.add(clocks[i] + "/" + max + " MHz");
                total += clocks[i];
                if (max > maxClock) maxClock = max;
            }
            int avg = clocks.length > 0 ? total / clocks.length : 0;
            int pct = maxClock > 0 ? (int)(((float) avg / maxClock) * 100) : 0;
            ds.setTmCpuCores(cores);
            ds.setTmCpuTitle("CPU (" + pct + "%)");

            android.app.ActivityManager am =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long used = mi.totalMem - mi.availMem;
            int memPct = (int)(((double) used / mi.totalMem) * 100);
            ds.setTmMemTitle("Memory (" + memPct + "%)");
            ds.setTmMemInfo(StringUtils.formatBytes(used, false) + " / " +
                StringUtils.formatBytes(mi.totalMem));
        } catch (Exception ignored) {}

        // Enriched header — one HudMetrics snapshot + live FPS, pushed to the TM header grid.
        try {
            if (tmHudMetrics == null) tmHudMetrics = new com.winhub.hernandez.widget.HudMetrics(this);
            com.winhub.hernandez.widget.HudMetrics.Snapshot s = tmHudMetrics.snapshot();
            java.util.ArrayList<Integer> perCore = new java.util.ArrayList<>();
            for (int mhz : s.perCoreClockMhz) perCore.add(mhz);
            String swap = (s.swapUsedText() != null && s.swapTotalText() != null)
                ? (s.swapUsedText() + "/" + s.swapTotalText()) : null;
            ds.setTmHeader(new XServerDialogState.TmHeaderStats(
                s.cpuPercent, s.cpuTempC,
                s.gpuPercent, s.gpuTempC, s.gpuClockMhz,
                Math.round(fpsCounter.getCurrentFPS()), fpsCounter.getMinFPS(),
                s.ramUsedText(), s.ramTotalText(),
                swap,
                s.battery.percent, s.battery.watts, s.battery.tempC, s.battery.charging,
                perCore));
        } catch (Exception ignored) {}
    }


} // Closes the XServerDisplayActivity class



















































