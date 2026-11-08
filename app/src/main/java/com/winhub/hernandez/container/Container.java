package com.winhub.hernandez.container;

import android.os.Environment;
import android.util.Log;

import com.winhub.hernandez.BuildConfig;
import com.winhub.hernandez.box64.Box64Preset;
import com.winhub.hernandez.contentdialog.DXVKConfigDialog;
import com.winhub.hernandez.contentdialog.WineD3DConfigDialog;
import com.winhub.hernandez.core.ControllerIdentity;
import com.winhub.hernandez.core.DefaultVersion;
import com.winhub.hernandez.core.EnvVars;
import com.winhub.hernandez.core.FileUtils;
import com.winhub.hernandez.core.KeyValueSet;
import com.winhub.hernandez.core.WineInfo;
import com.winhub.hernandez.core.WineThemeManager;
import com.winhub.hernandez.fexcore.FEXCorePreset;
import com.winhub.hernandez.winhandler.WinHandler;
import com.winhub.hernandez.xenvironment.ImageFs;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;
import android.opengl.GLES10;

public class Container {
    public enum XrControllerMapping {
        BUTTON_A, BUTTON_B, BUTTON_X, BUTTON_Y, BUTTON_GRIP, BUTTON_TRIGGER,
        THUMBSTICK_UP, THUMBSTICK_DOWN, THUMBSTICK_LEFT, THUMBSTICK_RIGHT
    }
    // DXVK_HUD is left listed but EMPTY by default: the variable stays visible in the container's
    // env-var editor (so its HUD-element chips are one tap away) while nothing is shown until a user
    // opts in. EnvVars keeps "DXVK_HUD=" (index of '=' > 0) with an empty value, and DXVK renders no
    // overlay for an empty element list — so a fresh container starts with the HUD off.
    public static final String DEFAULT_ENV_VARS = "WRAPPER_MAX_IMAGE_COUNT=0 ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=512MB mesa_glthread=true WINEESYNC=1 TU_DEBUG=noconform,sysmem DXVK_HUD=";
    public static final String DEFAULT_SCREEN_SIZE = "1280x720";

    /**
     * Screen size for a NEW container on this device, chosen to fill the panel's shape instead of
     * always letterboxing to 16:9. Panels at 16:9 or wider (phones, most handhelds) keep
     * {@link #DEFAULT_SCREEN_SIZE}: a wider guest desktop only costs pixels and most games are 16:9.
     * 16:10 and 3:2 panels get 1280x800; 4:3 and squarer ones (Retroid Pocket Nova/Classic/Mini,
     * foldable inner screens) get 1280x960. Existing containers are never touched.
     */
    public static String defaultScreenSizeFor(android.content.Context context) {
        android.hardware.display.DisplayManager displayManager = (android.hardware.display.DisplayManager)
                context.getSystemService(android.content.Context.DISPLAY_SERVICE);
        android.view.Display display = displayManager != null
                ? displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY) : null;
        if (display == null) return DEFAULT_SCREEN_SIZE;
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        display.getRealMetrics(metrics);
        return defaultScreenSizeForPanel(metrics.widthPixels, metrics.heightPixels);
    }

    public static String defaultScreenSizeForPanel(int width, int height) {
        if (width <= 0 || height <= 0) return DEFAULT_SCREEN_SIZE;
        float ratio = (float) Math.max(width, height) / Math.min(width, height);
        if (ratio < 1.467f) return "1280x960";  // 4:3 and squarer (cut halfway between 4:3 and 16:10)
        if (ratio < 1.689f) return "1280x800";  // 16:10 and 3:2 (cut halfway between 16:10 and 16:9)
        return DEFAULT_SCREEN_SIZE;             // 16:9 and wider
    }
    public static final String DEFAULT_GRAPHICS_DRIVER = "wrapper";
    /**
     * The graphics wrapper built for non-Adreno parts (Mali, Xclipse, PowerVR) — the WrapperManager
     * slot identifier, not an adrenotools driver id. New containers on those GPUs default to this
     * instead of {@link #DEFAULT_GRAPHICS_DRIVER}, which targets Adreno/Turnip.
     */
    public static final String GRAPHICS_DRIVER_GAMENATIVE = "wrapper-gamenative";
    public static final String DEFAULT_AUDIO_DRIVER = "pulseaudio";
    public static final String DEFAULT_EMULATOR = "FEXCore";
    public static final String DEFAULT_DXWRAPPER = "dxvk+vkd3d";
    public static final String DEFAULT_DXWRAPPERCONFIG = "version=" + DefaultVersion.getVegasDefault() + ",framerate=0,async=0,asyncCache=0" + ",vkd3dVersion=2.8" + ",vkd3dLevel=12_1" + ",ddrawrapper=" + Container.DEFAULT_DDRAWRAPPER + ",csmt=3" + ",gpuName=NVIDIA GeForce GTX 480" + ",videoMemorySize=2048" + ",strict_shader_math=1" + ",OffscreenRenderingMode=fbo" + ",renderer=gl";
    public static final String DEFAULT_GRAPHICSDRIVERCONFIG =
            "vulkanVersion=1.3" + ";version=" + ";blacklistedExtensions=" + ";maxDeviceMemory=0" + ";presentMode=mailbox" + ";syncFrame=0" + ";disablePresentWait=0" + ";resourceType=auto" + ";bcnEmulation=auto" + ";bcnEmulationType=compute" + ";bcnEmulationCache=0" + ";gpuName=Device" + ";fdDevFeatures=0";
    public static final String DEFAULT_DDRAWRAPPER = "none";
    /**
     * Canonical default HUD scale (percent). Referenced by every overlay view and both HUD config
     * surfaces when the {@code hudScale} key is absent, so a config missing that key resolves to the
     * SAME size everywhere (previously classic launched at 100 while the drawer emitted 92 → the
     * overlay jumped size on the first metric toggle).
     */
    public static final int DEFAULT_HUD_SCALE = 100;
    /** Size a brand-new container's HUD starts at (Fusion level 2 at 75%). */
    public static final int NEW_CONTAINER_HUD_SCALE = 75;
    /**
     * The HUD a new container gets: Fusion level 2 (FPS and graph) at 75%, unlocked, with every Fusion metric on. Each
     * key is written explicitly (both spellings where the editors emit two) so the editors show the
     * same switches the overlay draws, instead of falling back to their own absent-key defaults.
     */
    public static final String DEFAULT_FPS_COUNTER_CONFIG = "hudStyle=fusion,hudEnabled=1,hudMode=horizontal,hudSize=2,hudScale=" + NEW_CONTAINER_HUD_SCALE + ",hudLocked=0"
            + ",showFPS=1,showFPSGraph=1,showCPUUsage=1,showCPULoad=1,showGPULoad=1,showRAM=1,showVram=1,showPower=1,showBattery=1"
            + ",showTemp=1,showBatteryTemp=1,showGpuTemp=1,showEngine=1,showRenderer=1,showGpuModel=1,showLow001=1,fpsDecimal=1,showClock=1"
            + ",showPerCore=1,showSwap=1,showNet=1,showResolution=1,showProton=1,showWrapper=1,showDxVer=1,showSession=1";
    public static final String DEFAULT_WINCOMPONENTS = "direct3d=1,directsound=0,directmusic=0,directshow=0,directplay=0,xaudio=0,vcrun2010=1";
    public static final String FALLBACK_WINCOMPONENTS = "direct3d=1,directsound=1,directmusic=1,directshow=1,directplay=1,xaudio=1,vcrun2010=1";
    public static final String DEFAULT_DRIVES = "F:"+Environment.getExternalStorageDirectory().getAbsolutePath()+"D:"+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    public static final byte STARTUP_SELECTION_NORMAL = 0;
    public static final byte STARTUP_SELECTION_ESSENTIAL = 1;
    public static final byte STARTUP_SELECTION_AGGRESSIVE = 2;
    // Custom: per-service on/off. The enabled set is stored in startupServices (CSV of ENABLED
    // service raw names); everything not listed is disabled. Custom starts all-off (empty CSV).
    public static final byte STARTUP_SELECTION_CUSTOM = 3;
    public static final byte MAX_DRIVE_LETTERS = 26;
    public final int id;
    private String name;
    private String screenSize = DEFAULT_SCREEN_SIZE;
    private String envVars = DEFAULT_ENV_VARS;
    private String graphicsDriver = DEFAULT_GRAPHICS_DRIVER;
    private String graphicsDriverConfig = DEFAULT_GRAPHICSDRIVERCONFIG;
    private String dxwrapper = DEFAULT_DXWRAPPER;
    private String dxwrapperConfig = "";
    private String fpsCounterConfig = DEFAULT_FPS_COUNTER_CONFIG;
    private String wincomponents = DEFAULT_WINCOMPONENTS;
    private String audioDriver = DEFAULT_AUDIO_DRIVER;
    private String drives = DEFAULT_DRIVES;
    private String wineVersion = WineInfo.MAIN_WINE_VERSION.identifier();
    private String basePrefix = "";
    private boolean showFPS;
    private boolean rendererNative = false;
    private String rendererPresentMode = "fifo";
    private String rendererDriverId = "system";
    private int rendererFilterMode = 0;
    private boolean rendererSwapRB = false;
    // SurfaceDirect (SurfaceFlinger presentation) options; the defaults match the renderer's own.
    private boolean surfaceDirectPerformanceMode = true;
    private boolean surfaceDirectPresentAtRefreshRate = true;
    private boolean surfaceDirectBackPressure = false;
    private boolean surfaceDirectPrecisePresentation = false;
    // Fullscreen aspect-ratio mode (issue #71). Replaces the old fullscreenStretched boolean.
    public static final int FULLSCREEN_OFF = 0;      // windowed, letterboxed (preserve aspect)
    public static final int FULLSCREEN_FIT = 1;      // fullscreen-immersive, letterboxed (preserve aspect)
    public static final int FULLSCREEN_STRETCH = 2;  // fullscreen-immersive, fills surface (distorts aspect)
    public static final int FULLSCREEN_FILL = 3;     // fullscreen-immersive, crop-to-fill (preserve aspect, no bars)
    public static final int FULLSCREEN_INTEGER = 4;  // fullscreen-immersive, largest whole-number scale (pixel-perfect, centered)
    private int fullscreenMode = FULLSCREEN_OFF;
    // Screen alignment (issue #413): vertical placement of the letterbox rect on square-ish/foldable
    // displays. CENTER is the historical behavior (equal bars top+bottom); TOP/BOTTOM pool the empty
    // space on the opposite edge for touch controls. Aspect is preserved — only the bar moves.
    public static final int ALIGN_CENTER = 0;
    public static final int ALIGN_TOP = 1;
    public static final int ALIGN_BOTTOM = 2;
    private int screenAlignment = ALIGN_CENTER;
    private byte startupSelection = STARTUP_SELECTION_ESSENTIAL;
    // CSV of ENABLED service raw names when startupSelection == CUSTOM. "" (default) = none enabled
    // (Custom starts every service off). Ignored by the other three presets. Per-game shortcuts
    // inherit this via the getExtra("startupServices", container default) fallback at launch.
    private String startupServices = "";
    private String cpuList;
    private String cpuListWoW64;
    private String desktopTheme = WineThemeManager.DEFAULT_DESKTOP_THEME;
    private String fexcoreVersion;
    private String fexcorePreset = FEXCorePreset.INTERMEDIATE;
    private String box64Preset = Box64Preset.COMPATIBILITY;
    /**
     * This container's OWN copy of a preset's values, as {@code presetId|VARS}, or null while it is
     * still following the shared preset. Written when the preset is edited from Edit Container, so
     * the change stays in this container instead of moving every container using that preset. Games
     * in this container inherit it unless they carry their own copy. See
     * {@link com.winhub.hernandez.core.PresetOverrides}, which owns the format and the resolution order.
     */
    public String fexcorePresetVars = null;
    public String box64PresetVars = null;
    private File rootDir;
    private JSONObject extraData;
    private String midiSoundFont = "";
    private int inputType = WinHandler.DEFAULT_INPUT_TYPE;
    private String lc_all = "";
    private int primaryController = 1;
    private String controllerMapping = new String(new char[XrControllerMapping.values().length]);
    private String box64Version;
    private String emulator;
    private String renderer = "vulkan";
    private boolean exclusiveXInput = true;
    // "Direct XInput (HERMES)": XInput games read the pad straight from the HERMES slot files
    // (core/HermesXInput). Default off; always persisted so an explicit choice survives a reload.
    private boolean hermesXInput = false;
    // "Allow in-game switching" of Direct XInput: the HERMES-XInput DLLs go in for both states and Wine's
    // own xinput is installed beside them as the forward target, so the mode can flip mid-game
    // (core/HermesXInput). Default on; always persisted, so an explicit off stays off.
    private boolean hermesXInputSwitching = true;
    private ContainerManager containerManager;



    
    public static boolean isMaliGPU() {
        try {
            String renderer = GLES10.glGetString(GLES10.GL_RENDERER);
            return renderer != null && renderer.contains("Mali");
        } catch (Exception e) {
            return false;
        }
    }

    public Container(int id) {
        this.id = id;
        this.name = "Container-"+id;
    }

    public Container(int id, ContainerManager containerManager) {
        this.id = id;
        this.name = "Container-"+id;
        this.containerManager = containerManager;
    }

    public ContainerManager getManager() {
        return containerManager;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(String screenSize) {
        this.screenSize = screenSize;
    }

    public String getEnvVars() {
        return envVars;
    }

    public void setEnvVars(String envVars) {
        this.envVars = envVars != null ? envVars : "";
    }

    public String getGraphicsDriver() {
        return graphicsDriver;
    }

    public void setGraphicsDriver(String graphicsDriver) {
        this.graphicsDriver = graphicsDriver;
    }

    public String getGraphicsDriverConfig() { return this.graphicsDriverConfig; }

    public void setGraphicsDriverConfig(String graphicsDriverConfig) { this.graphicsDriverConfig = graphicsDriverConfig; }

    public String getDXWrapper() {
        return dxwrapper;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public String getDXWrapperConfig() {
        return dxwrapperConfig;
    }

    public void setDXWrapperConfig(String dxwrapperConfig) {
        this.dxwrapperConfig = dxwrapperConfig != null ? dxwrapperConfig : "";
    }

    public String getAudioDriver() {
        return audioDriver;
    }

    public void setAudioDriver(String audioDriver) {
        this.audioDriver = audioDriver;
    }

    public String getWinComponents() {
        return wincomponents;
    }

    public void setWinComponents(String wincomponents) {
        this.wincomponents = wincomponents;
    }

    public String getDrives() {
        return drives;
    }

    public void setDrives(String drives) {
        this.drives = drives;
    }

    public String getLC_ALL() {
        return lc_all;
    }

    public void setLC_ALL(String lc_all) {
        this.lc_all = lc_all;
    }

    public int getPrimaryController() {
        return primaryController;
    }

    public void setPrimaryController(int primaryController) {
        this.primaryController = primaryController;
    }

    public byte getControllerMapping(XrControllerMapping input) {
        return (byte) controllerMapping.charAt(input.ordinal());
    }

    public void setControllerMapping(String controllerMapping) {
        this.controllerMapping = controllerMapping;
    }

    public int getFullscreenMode() { return fullscreenMode; }

    public void setFullscreenMode(int mode) { this.fullscreenMode = mode; }

    public int getScreenAlignment() { return screenAlignment; }

    public void setScreenAlignment(int a) { this.screenAlignment = a; }

    // Legacy compat: derived helper so any lingering callers still compile/behave.
    public boolean isFullscreenStretched() { return fullscreenMode == FULLSCREEN_STRETCH; }

    public void setFullscreenStretched(boolean stretched) {
        this.fullscreenMode = stretched ? FULLSCREEN_STRETCH : FULLSCREEN_OFF;
    }

    // In-game live cycle used by the drawer toggle: OFF -> FIT -> STRETCH -> FILL -> INTEGER -> OFF.
    public static int nextFullscreenMode(int mode) {
        switch (mode) {
            case FULLSCREEN_OFF:     return FULLSCREEN_FIT;
            case FULLSCREEN_FIT:     return FULLSCREEN_STRETCH;
            case FULLSCREEN_STRETCH: return FULLSCREEN_FILL;
            case FULLSCREEN_FILL:    return FULLSCREEN_INTEGER;
            case FULLSCREEN_INTEGER: return FULLSCREEN_OFF;
            default:                 return FULLSCREEN_OFF;
        }
    }

    public boolean isShowFPS() {
        return showFPS;
    }

    public void setShowFPS(boolean showFPS) {
        this.showFPS = showFPS;
    }

    public String getFPSCounterConfig() {
        return fpsCounterConfig;
    }

    public void setFPSCounterConfig(String fpsCounterConfig) {
        this.fpsCounterConfig = fpsCounterConfig;
    }

    public byte getStartupSelection() {
        return startupSelection;
    }

    public void setStartupSelection(byte startupSelection) {
        this.startupSelection = startupSelection;
    }

    public String getStartupServices() {
        return startupServices;
    }

    public void setStartupServices(String startupServices) {
        this.startupServices = startupServices != null ? startupServices : "";
    }

    public String getCPUList() {
        return getCPUList(false);
    }

    public String getCPUList(boolean allowFallback) {
        return cpuList != null ? cpuList : (allowFallback ? getFallbackCPUList() : null);
    }

    public void setCPUList(String cpuList) {
        this.cpuList = cpuList != null && !cpuList.isEmpty() ? cpuList : null;
    }

    public String getCPUListWoW64() {
        return getCPUListWoW64(false);
    }

    public String getCPUListWoW64(boolean allowFallback) {
        return cpuListWoW64 != null ? cpuListWoW64 : (allowFallback ? getFallbackCPUListWoW64() : null);
    }

    public void setCPUListWoW64(String cpuListWoW64) {
        this.cpuListWoW64 = cpuListWoW64 != null && !cpuListWoW64.isEmpty() ? cpuListWoW64 : null;
    }

    public void setFEXCoreVersion(String version) {
        this.fexcoreVersion = version;
    }

    public String getFEXCoreVersion() {
        return this.fexcoreVersion;
    }

    public void setFEXCorePreset(String preset) {
        this.fexcorePreset = preset;
    }

    public String getFEXCorePreset() {
        return fexcorePreset;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public String getBox64Version() { return box64Version; }

    public void setBox64Version(String version) { this.box64Version = version; }

    public void setEmulator(String emulator) {
        this.emulator = emulator;
    }

    public String getEmulator() {
        return this.emulator;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public void setExtraData(JSONObject extraData) {
        this.extraData = extraData;
    }

    // Controller identity (core.ControllerIdentity): what the virtual pads say they are to a Wine game
    // (FAKE_EVDEV_IDENTITY at launch). Absent = Generic, the identity every container had before;
    // Generic is stored as absent so an untouched container's extraData doesn't change.
    public String getControllerIdentity() {
        String v = ControllerIdentity.normalize(getExtra(ControllerIdentity.EXTRA, ""));
        return v != null ? v : ControllerIdentity.GENERIC;
    }

    public void setControllerIdentity(String identity) {
        String v = ControllerIdentity.normalize(identity);
        putExtra(ControllerIdentity.EXTRA,
                v == null || v.equals(ControllerIdentity.GENERIC) ? null : v);
    }

    public String getExtra(String name) {
        return getExtra(name, "");
    }

    public String getExtra(String name, String fallback) {
        try {
            return extraData != null && extraData.has(name) ? extraData.getString(name) : fallback;
        }
        catch (JSONException e) {
            return fallback;
        }
    }

    // Whether an extra was explicitly written (vs. absent and only serving a default). Used to tell an
    // untouched default apart from a deliberate user choice — e.g. so the refresh-unlock "needs a
    // compatible layer" Toast only nags users who actually opted in, not every default container.
    public boolean hasExtra(String name) {
        return extraData != null && extraData.has(name);
    }

    // Marks a container created by the Deck "Cronos" add-game path (one game, its own container).
    public static final String EXTRA_CRONOS = "cronos";

    public boolean isCronos() {
        return "1".equals(getExtra(EXTRA_CRONOS, ""));
    }

    public void putExtra(String name, Object value) {
        if (extraData == null) extraData = new JSONObject();
        try {
            if (value != null) {
                extraData.put(name, value);
            }
            else extraData.remove(name);
        }
        catch (JSONException e) {}
    }

    // --- Fast OpenGL (per-container), stored in extraData. ---
    // OpenGL through Wine's EGL backend (or winex11's GLX) + Zink instead of XMesa's readback per
    // frame (core.FastOpenGL, core.X11Egl). Default ON; the launch still checks the layer and driver
    // can do it. A shortcut may override per game with the same-named extra.
    public boolean isFastOpenGL() {
        return com.winhub.hernandez.core.FastOpenGL.containerOn(getExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA));
    }

    public void setFastOpenGL(boolean enabled) {
        putExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA, enabled ? "1" : "0");
    }

    // --- Show OpenGL FPS / OpenGL vsync off (per-container), stored in extraData. ---
    // GALLIUM_HUD=fps and vblank_mode=0 at launch (core.FastOpenGL.FPS_HUD / VSYNC_OFF). Default OFF.
    // A shortcut may override per game with the same-named extra.
    public boolean isGlFpsHud() {
        return com.winhub.hernandez.core.FastOpenGL.FPS_HUD.containerOn(getExtra(com.winhub.hernandez.core.FastOpenGL.FPS_HUD.extra));
    }

    public void setGlFpsHud(boolean enabled) {
        putExtra(com.winhub.hernandez.core.FastOpenGL.FPS_HUD.extra, enabled ? "1" : "0");
    }

    public boolean isGlVsyncOff() {
        return com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF.containerOn(getExtra(com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF.extra));
    }

    public void setGlVsyncOff(boolean enabled) {
        putExtra(com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF.extra, enabled ? "1" : "0");
    }

    // --- Display backend (per-container), stored in extraData ---
    // "x11" (default) = the Java X server + libwinlator compositor (winex11.drv).
    public static final String DISPLAY_BACKEND_X11 = "x11";

    public String getDisplayBackend() {
        return getExtra("displayBackend", DISPLAY_BACKEND_X11);
    }

    public void setDisplayBackend(String value) {
        putExtra("displayBackend", DISPLAY_BACKEND_X11);
    }

    // --- bionic-fg frame generation (per-container), stored in extraData ---
    // The on/off flag is set in the container settings; multiplier & flow scale
    // are tuned live from the in-game side menu (both hot-reload via conf.toml).
    public static final int FRAMEGEN_DEFAULT_MULTIPLIER = 2;
    public static final float FRAMEGEN_DEFAULT_FLOW_SCALE = 0.6f;
    // lsfg-vk runs best at a higher flow scale than bionic-fg (GameNative's proven default). Only the
    // UNSET default differs per engine (see getFrameGenFlowScale) — an explicit user value wins either way.
    public static final float LSFG_DEFAULT_FLOW_SCALE = 0.80f;
    public static final int FRAMEGEN_DEFAULT_MODEL = 3;   // win-fg model 3 = Optical flow (~2ms; device-proven best base-FPS retention)
    // win-fg performance preset (conf.toml `perf_preset`): 0 = Quality, 1 = Balanced (default), 2 = Performance.
    // The layer hot-reloads + self-rebuilds when this changes, so it's live-tunable from the in-game FG drawer.
    public static final int FRAMEGEN_PERF_PRESET_QUALITY = 0;
    public static final int FRAMEGEN_PERF_PRESET_BALANCED = 1;
    public static final int FRAMEGEN_PERF_PRESET_PERFORMANCE = 2;
    public static final int FRAMEGEN_DEFAULT_PERF_PRESET = FRAMEGEN_PERF_PRESET_BALANCED;

    public boolean isFrameGenEnabled() {
        return getExtra("frameGenEnabled", "0").equals("1");
    }

    public void setFrameGenEnabled(boolean enabled) {
        putExtra("frameGenEnabled", enabled ? "1" : "0");
    }

    // Frame-gen engine selection: "off" | "lsfg-native". LSFG Native is the only engine this
    // build ships. Legacy values ("bionic", the retired in-container "lsfg") resolve to it so an
    // old container keeps generating frames instead of silently turning off.
    public String getFrameGenEngine() {
        String e = getExtra("frameGenEngine", "");
        String resolved;
        if (e.isEmpty()) resolved = isFrameGenEnabled() ? "lsfg-native" : "off";
        else if (e.equals("bionic") || e.equals("lsfg") || e.equals("lsfg-native")) resolved = "lsfg-native";
        else resolved = "off";
        if (BuildConfig.DEBUG) Log.d("FrameGen", "getFrameGenEngine: rawExtra=\"" + e + "\" -> " + resolved);
        return resolved;
    }

    public void setFrameGenEngine(String engine) {
        boolean lsfg = "lsfg-native".equals(engine) || "lsfg".equals(engine) || "bionic".equals(engine);
        putExtra("frameGenEngine", lsfg ? "lsfg-native" : "off");
        // The legacy bionic flag no longer selects an engine; keep it off so no stale path arms it.
        setFrameGenEnabled(false);
        if (BuildConfig.DEBUG) Log.d("FrameGen", "setFrameGenEngine: got=\"" + engine + "\" -> stored \"" + getExtra("frameGenEngine", "") + "\"");
    }

    public boolean isLsfgEngine() {
        return getFrameGenEngine().equals("lsfg-native");
    }

    // Allowed values: 0 (Off, set live from the in-game menu) or 2-4. Anything else -> default.
    public int getFrameGenMultiplier() {
        try {
            int m = Integer.parseInt(getExtra("frameGenMultiplier", String.valueOf(FRAMEGEN_DEFAULT_MULTIPLIER)));
            if (m == 0) return 0;
            return (m < 2 || m > 4) ? FRAMEGEN_DEFAULT_MULTIPLIER : m;
        }
        catch (NumberFormatException e) {
            return FRAMEGEN_DEFAULT_MULTIPLIER;
        }
    }

    public void setFrameGenMultiplier(int multiplier) {
        putExtra("frameGenMultiplier", String.valueOf(multiplier));
    }

    public float getFrameGenFlowScale() {
        // Engine-dependent UNSET default: lsfg-vk defaults to 0.80, bionic-fg stays at 0.60. An explicit
        // frameGenFlowScale extra (whatever the engine) is always honored — only the fallback differs.
        float dflt = isLsfgEngine() ? LSFG_DEFAULT_FLOW_SCALE : FRAMEGEN_DEFAULT_FLOW_SCALE;
        if (!hasExtra("frameGenFlowScale")) return dflt;
        try {
            float f = Float.parseFloat(getExtra("frameGenFlowScale"));
            // Mirror the layer's clamp range (layer.cpp parseConfigFile).
            return (f < 0.2f || f > 1.0f) ? dflt : f;
        }
        catch (NumberFormatException e) {
            return dflt;
        }
    }

    public void setFrameGenFlowScale(float flowScale) {
        putExtra("frameGenFlowScale", String.valueOf(flowScale));
    }

    // bionic-fg interpolation model (conf.toml `model`, layer clamp 0-4):
    //   0 = hand-written optical-flow chain (the long-standing default)
    //   1 = GameScopeVK's traced dispatch graph
    //   2 = same graph fed libGameScopeV2's shader variants
    //   3 = FidelityFX Optical Flow
    //   4 = FidelityFX Optical Flow v2 — 3's front-end with a per-block search, a wider
    //       match window, sub-pixel refinement and a true bidirectional solve whose
    //       forward/backward disagreement gates the flow at occlusion edges. 3 is kept
    //       unchanged alongside it so the two can be compared live in the same scene.
    // win-fg models: 3 = Optical flow (default; ~2ms, best base-FPS retention on GPU-bound titles,
    // device-proven 2026-08-25), 4 = Bidirectional (~10ms, heavier). An explicit per-container/shortcut
    // pick still wins; the layer clamps to [3,4].
    public int getFrameGenModel() {
        try {
            int m = Integer.parseInt(getExtra("frameGenModel", String.valueOf(FRAMEGEN_DEFAULT_MODEL)));
            return (m < 0 || m > 4) ? FRAMEGEN_DEFAULT_MODEL : m;
        }
        catch (NumberFormatException e) {
            return FRAMEGEN_DEFAULT_MODEL;
        }
    }

    public void setFrameGenModel(int model) {
        putExtra("frameGenModel", String.valueOf(model));
    }

    // win-fg performance preset (conf.toml perf_preset, layer clamp 0-2): 0 = Quality, 1 = Balanced
    // (default), 2 = Performance. Default Balanced keeps existing behavior unchanged. Live-tunable from
    // the in-game FG drawer (the layer hot-reloads + self-rebuilds — no bg/fg pulse needed).
    public int getFrameGenPerfPreset() {
        try {
            int p = Integer.parseInt(getExtra("frameGenPerfPreset", String.valueOf(FRAMEGEN_DEFAULT_PERF_PRESET)));
            return (p < 0 || p > 2) ? FRAMEGEN_DEFAULT_PERF_PRESET : p;
        }
        catch (NumberFormatException e) {
            return FRAMEGEN_DEFAULT_PERF_PRESET;
        }
    }

    public void setFrameGenPerfPreset(int preset) {
        putExtra("frameGenPerfPreset", String.valueOf(preset));
    }

    // lsfg-vk "performance mode" (conf.toml performance_mode): trades interpolation quality for FPS.
    // Per-container, default ON (matches GameNative — the lighter LSFG_3_1P model is cheaper on Adreno).
    // Also live-toggleable from the in-game FG menu.
    public boolean isLsfgPerformanceMode() {
        return getExtra("lsfgPerformanceMode", "1").equals("1");
    }

    public void setLsfgPerformanceMode(boolean performanceMode) {
        putExtra("lsfgPerformanceMode", performanceMode ? "1" : "0");
    }

    // lsfg-vk "auto-enable at launch": when ON, the container starts frame generation LIVE at its saved
    // multiplier from the first frame (GameNative-style) instead of the safe global default (layer loaded
    // but frame gen OFF, opt-in per session from the FG drawer). Per-container, default ON to match
    // GameNative — uncheck it to restore the start-off behavior for a given lsfg container. Note this
    // only affects lsfg containers; bionic-fg still always starts off in-game.
    public boolean isLsfgAutoEnable() {
        return getExtra("lsfgAutoEnable", "1").equals("1");
    }

    public void setLsfgAutoEnable(boolean autoEnable) {
        putExtra("lsfgAutoEnable", autoEnable ? "1" : "0");
    }

    // --- LSFG Native experimental knobs (default to today's behaviour; only surfaced and
    // honoured while FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED is on) ---

    // Capture resolution the frame-gen chain runs at. Stored as "panel" (default, today's
    // behaviour), "game" (the height the game really renders at this session: per-game screen
    // size override and render scale included, resolved by the renderer) or a bare pixel height
    // ("720"). Both the container editor and the in-game drawer write that one form; the legacy
    // "WxH" form is still read. Only the HEIGHT matters: the width follows the phone screen's
    // aspect so nothing is stretched. See fgCaptureHeightFor.
    public static final String FG_CAPTURE_PANEL = "panel";
    public static final String FG_CAPTURE_GAME  = "game";
    /** fgCaptureHeightFor's answer for "game": the renderer substitutes the X screen's height. */
    public static final int FG_CAPTURE_HEIGHT_GAME = -1;

    public String getFgCaptureResolution() {
        String v = getExtra("fgCaptureResolution", FG_CAPTURE_PANEL);
        return v == null || v.isEmpty() ? FG_CAPTURE_PANEL : v;
    }

    public void setFgCaptureResolution(String value) {
        putExtra("fgCaptureResolution", value == null || value.isEmpty() ? FG_CAPTURE_PANEL : value);
    }

    /**
     * Resolve a capture-resolution value to what the renderer takes: 0 = panel resolution,
     * {@link #FG_CAPTURE_HEIGHT_GAME} = the game's own height, otherwise a pixel height.
     * Accepts "720", "1280x720" and "1280x720 (16:9)".
     */
    public static int fgCaptureHeightFor(String value) {
        if (value == null || value.isEmpty() || FG_CAPTURE_PANEL.equals(value)) return 0;
        if (FG_CAPTURE_GAME.equals(value)) return FG_CAPTURE_HEIGHT_GAME;
        try {
            String s = value.trim();
            int x = s.indexOf('x');
            if (x >= 0) s = s.substring(x + 1);
            int sp = s.indexOf(' ');
            if (sp >= 0) s = s.substring(0, sp);
            return Math.max(0, Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Vulkan 1.1 compat: let LSFG Native run on a compositor driver that only reports Vulkan 1.1
    // (e.g. the stock Adreno driver) when it offers the extensions the chain needs. Read at launch.
    public boolean isLsfgVk11Compat() {
        return getExtra("lsfgVk11Compat", "0").equals("1");
    }

    public void setLsfgVk11Compat(boolean on) {
        putExtra("lsfgVk11Compat", on ? "1" : "0");
    }

    // NOTE: the power-user performance toggles (sustainedPerfMode / perfPriorityBoost / preferBigCores)
    // are deliberately NOT container-level. The locked resolution model is two levels only —
    // per-game shortcut override -> global default (com.winhub.hernandez.perf.PerformanceSettings).
    // Shortcuts still store their own override under these extraData keys; the container is not a base.

    // Controller vibration (PC-accurate dual-motor rumble), per-container. Mode gates WHERE rumble
    // goes: 0=Off 1=Controller(default, matches the pre-existing hardcoded behavior) 2=Device(phone)
    // 3=Both. Intensity (0..100) scales amplitude on top of the master/per-slot toggles in WinHandler.
    // Both are also live-tunable from the in-game drawer (WinHandler.setVibrationTuning), which is why
    // these getters clamp/validate exactly like isLsfgPerformanceMode/getFrameGenMultiplier above.
    public static final int VIBRATION_MODE_OFF = 0;
    public static final int VIBRATION_MODE_CONTROLLER = 1;
    public static final int VIBRATION_MODE_DEVICE = 2;
    public static final int VIBRATION_MODE_BOTH = 3;
    public static final int VIBRATION_MODE_DEFAULT = VIBRATION_MODE_CONTROLLER;
    public static final int VIBRATION_INTENSITY_DEFAULT = 100;

    public int getVibrationMode() {
        try {
            int m = Integer.parseInt(getExtra("vibrationMode", String.valueOf(VIBRATION_MODE_DEFAULT)));
            return (m < VIBRATION_MODE_OFF || m > VIBRATION_MODE_BOTH) ? VIBRATION_MODE_DEFAULT : m;
        }
        catch (NumberFormatException e) {
            return VIBRATION_MODE_DEFAULT;
        }
    }

    public void setVibrationMode(int mode) {
        putExtra("vibrationMode", String.valueOf(mode));
    }

    public int getVibrationIntensity() {
        try {
            int v = Integer.parseInt(getExtra("vibrationIntensity", String.valueOf(VIBRATION_INTENSITY_DEFAULT)));
            return (v < 0 || v > 100) ? VIBRATION_INTENSITY_DEFAULT : v;
        }
        catch (NumberFormatException e) {
            return VIBRATION_INTENSITY_DEFAULT;
        }
    }

    public void setVibrationIntensity(int intensity) {
        putExtra("vibrationIntensity", String.valueOf(intensity));
    }

    // Manual controller slot overrides (in-game "Players" sub-tab), per-container. Stored as a raw
    // JSON object string mapping a stable device descriptor -> desired slot: 0..3 pins the device to
    // that XInput player slot, WinHandler.SLOT_IGNORE (-2) means "never take a slot", and a missing
    // key = auto (FCFS). The on-screen pad uses the WinHandler.OSC_DESCRIPTOR sentinel as its key.
    // Kept as an opaque string here (same discipline as getFPSCounterConfig): XServerDisplayActivity
    // parses it into a Map for WinHandler.setManualSlotOverrides and writes edits back through here.
    public String getControllerSlotOverrides() {
        return getExtra("controllerSlotOverrides", "{}");
    }

    public void setControllerSlotOverrides(String json) {
        putExtra("controllerSlotOverrides", json == null || json.isEmpty() ? "{}" : json);
    }

    // On-screen-controls vs physical-pad priority, per-container. Mirrors the WinHandler.ON_SCREEN_MODE_*
    // constants (duplicated here for the same reason VIBRATION_MODE_* is — the editor VM shouldn't import
    // winhandler). KEEP (default) = the historical behavior: the on-screen pad keeps whatever slot it
    // holds, so a pad hot-plugged mid-game lands on the next free player. YIELD = a pad connecting while
    // the on-screen pad holds Player 1 promotes to Player 1 (on-screen steps up to the next free slot).
    // SHARE = that pad co-occupies the on-screen pad's slot (both drive that player, merged). Default is
    // KEEP so existing containers behave exactly as before. Resolved (shortcut-override-else-container)
    // and pushed to WinHandler at launch (XServerDisplayActivity.setupUI).
    public static final int ON_SCREEN_MODE_KEEP = 0;
    public static final int ON_SCREEN_MODE_YIELD = 1;
    public static final int ON_SCREEN_MODE_SHARE = 2;
    public static final int ON_SCREEN_MODE_DEFAULT = ON_SCREEN_MODE_KEEP;

    public int getOnScreenControllerMode() {
        try {
            int m = Integer.parseInt(getExtra("onScreenControllerMode", String.valueOf(ON_SCREEN_MODE_DEFAULT)));
            return (m < ON_SCREEN_MODE_KEEP || m > ON_SCREEN_MODE_SHARE) ? ON_SCREEN_MODE_DEFAULT : m;
        }
        catch (NumberFormatException e) {
            return ON_SCREEN_MODE_DEFAULT;
        }
    }

    public void setOnScreenControllerMode(int mode) {
        putExtra("onScreenControllerMode", String.valueOf(mode));
    }

    // Auto-hide the on-screen touch controls when a physical controller takes over the on-screen pad's
    // player slot (issue #333). Per-container, resolved shortcut-override-else-container and applied live
    // at launch + on hot-plug. The container-level fallback is FALSE so existing containers are unchanged;
    // a NEW container is seeded from the app-drawer global (GlobalControllerPrefs, default ON) at creation,
    // exactly like onScreenControllerMode above. Stored as "1"/"0" in extraData (matches the shortcut
    // "exclusiveXInput"/"inputType" extra convention that XServerDisplayActivity reads with equals("1")).
    public static final boolean AUTO_HIDE_CONTROLS_ON_PAD_DEFAULT = false;

    public boolean isAutoHideControlsOnPad() {
        return getExtra("autoHideControlsOnPad", AUTO_HIDE_CONTROLS_ON_PAD_DEFAULT ? "1" : "0").equals("1");
    }

    public void setAutoHideControlsOnPad(boolean enabled) {
        putExtra("autoHideControlsOnPad", enabled ? "1" : "0");
    }

    // Gyro (motion aim), per-container. Mirrors the WinHandler.GYRO_* constants so the editor VM and
    // the shortcut screen can talk about targets/activators without importing winhandler (same reason
    // the VIBRATION_MODE_* values are duplicated above). Enabled/target/sensitivity/activator/invert
    // are ALSO per-game (the shortcut extra of the same name wins — see XServerDisplayActivity);
    // deadzone/smoothing are container-only, they describe the hand/device, not the game.
    // NOTE: the calibration bias is deliberately NOT here — it's a physical property of this phone's
    // IMU and stays a global pref, so a container copy or an imported config can't carry someone
    // else's sensor zero. The clamps below match the WinHandler setters exactly, so a hand-edited
    // container JSON can't push e.g. smoothing >= 1.0 and make the low-pass diverge.
    public static final int GYRO_TARGET_RIGHT_STICK = 0;
    public static final int GYRO_TARGET_LEFT_STICK = 1;
    public static final int GYRO_TARGET_MOUSE = 2;
    public static final int GYRO_ACTIVATOR_L1 = 0;
    public static final int GYRO_ACTIVATOR_L2 = 1;
    public static final int GYRO_ACTIVATOR_R1 = 2;
    public static final int GYRO_ACTIVATOR_R3 = 3;
    public static final int GYRO_ACTIVATOR_ALWAYS = 4;
    public static final int GYRO_ACTIVATION_HOLD = 0;
    public static final int GYRO_ACTIVATION_TOGGLE = 1;
    // How the tilt is read. RATE = the shipped behaviour (angular velocity -> stick deflection, the
    // stick recentres the moment you stop moving). ORIENTATION = "tilt to aim": the stick follows the
    // ANGLE you're holding the device at, so a held tilt keeps the stick deflected.
    public static final int GYRO_MODE_RATE = 0;
    public static final int GYRO_MODE_ORIENTATION = 1;

    // OFF for new containers: motion aim is a deliberate choice, not something a fresh
    // container should start doing on its own. Existing containers are unaffected — the
    // one-time gyro pref migration wrote an explicit gyroEnabled on every container, so this
    // default is only consulted when the extra is genuinely absent (i.e. a new container).
    public static final boolean GYRO_ENABLED_DEFAULT = false;
    public static final int GYRO_TARGET_DEFAULT = GYRO_TARGET_RIGHT_STICK;
    public static final float GYRO_DEADZONE_DEFAULT = 0.05f;
    public static final float GYRO_SENSITIVITY_DEFAULT = 2.0f;
    public static final float GYRO_SMOOTHING_DEFAULT = 0.5f;
    public static final int GYRO_ACTIVATOR_DEFAULT = GYRO_ACTIVATOR_L1;
    // HOLD is the default on purpose: it's what the gyro has always done, so an existing container
    // that has never seen this key behaves exactly as before.
    public static final int GYRO_ACTIVATION_MODE_DEFAULT = GYRO_ACTIVATION_HOLD;
    // RATE for the same reason HOLD is the activation default: it's what the gyro has always done, so
    // a container that has never seen this key behaves exactly as it did before tilt-to-aim existed.
    public static final int GYRO_MODE_DEFAULT = GYRO_MODE_RATE;
    public static final boolean GYRO_INVERT_X_DEFAULT = false;
    public static final boolean GYRO_INVERT_Y_DEFAULT = false;

    public boolean isGyroEnabled() {
        return getExtra("gyroEnabled", GYRO_ENABLED_DEFAULT ? "1" : "0").equals("1");
    }

    public void setGyroEnabled(boolean enabled) {
        putExtra("gyroEnabled", enabled ? "1" : "0");
    }

    public int getGyroMode() {
        try {
            int m = Integer.parseInt(getExtra("gyroMode", String.valueOf(GYRO_MODE_DEFAULT)));
            return (m < GYRO_MODE_RATE || m > GYRO_MODE_ORIENTATION) ? GYRO_MODE_DEFAULT : m;
        }
        catch (NumberFormatException e) {
            return GYRO_MODE_DEFAULT;
        }
    }

    public void setGyroMode(int mode) {
        putExtra("gyroMode", String.valueOf(mode));
    }

    public int getGyroTarget() {
        try {
            int t = Integer.parseInt(getExtra("gyroTarget", String.valueOf(GYRO_TARGET_DEFAULT)));
            return (t < GYRO_TARGET_RIGHT_STICK || t > GYRO_TARGET_MOUSE) ? GYRO_TARGET_DEFAULT : t;
        }
        catch (NumberFormatException e) {
            return GYRO_TARGET_DEFAULT;
        }
    }

    public void setGyroTarget(int target) {
        putExtra("gyroTarget", String.valueOf(target));
    }

    public int getGyroActivator() {
        try {
            int a = Integer.parseInt(getExtra("gyroActivator", String.valueOf(GYRO_ACTIVATOR_DEFAULT)));
            return (a < GYRO_ACTIVATOR_L1 || a > GYRO_ACTIVATOR_ALWAYS) ? GYRO_ACTIVATOR_DEFAULT : a;
        }
        catch (NumberFormatException e) {
            return GYRO_ACTIVATOR_DEFAULT;
        }
    }

    public void setGyroActivator(int activator) {
        putExtra("gyroActivator", String.valueOf(activator));
    }

    public int getGyroActivationMode() {
        try {
            int m = Integer.parseInt(getExtra("gyroActivationMode", String.valueOf(GYRO_ACTIVATION_MODE_DEFAULT)));
            return (m < GYRO_ACTIVATION_HOLD || m > GYRO_ACTIVATION_TOGGLE) ? GYRO_ACTIVATION_MODE_DEFAULT : m;
        }
        catch (NumberFormatException e) {
            return GYRO_ACTIVATION_MODE_DEFAULT;
        }
    }

    public void setGyroActivationMode(int activationMode) {
        putExtra("gyroActivationMode", String.valueOf(activationMode));
    }

    public float getGyroSensitivity() {
        try {
            float v = Float.parseFloat(getExtra("gyroSensitivity", String.valueOf(GYRO_SENSITIVITY_DEFAULT)));
            return Math.min(10.0f, Math.max(0.1f, v));
        }
        catch (NumberFormatException e) {
            return GYRO_SENSITIVITY_DEFAULT;
        }
    }

    public void setGyroSensitivity(float sensitivity) {
        putExtra("gyroSensitivity", String.valueOf(Math.min(10.0f, Math.max(0.1f, sensitivity))));
    }

    public float getGyroDeadzone() {
        try {
            float v = Float.parseFloat(getExtra("gyroDeadzone", String.valueOf(GYRO_DEADZONE_DEFAULT)));
            return Math.min(0.5f, Math.max(0.0f, v));
        }
        catch (NumberFormatException e) {
            return GYRO_DEADZONE_DEFAULT;
        }
    }

    public void setGyroDeadzone(float deadzone) {
        putExtra("gyroDeadzone", String.valueOf(Math.min(0.5f, Math.max(0.0f, deadzone))));
    }

    public float getGyroSmoothing() {
        try {
            float v = Float.parseFloat(getExtra("gyroSmoothing", String.valueOf(GYRO_SMOOTHING_DEFAULT)));
            return Math.min(0.95f, Math.max(0.0f, v));
        }
        catch (NumberFormatException e) {
            return GYRO_SMOOTHING_DEFAULT;
        }
    }

    public void setGyroSmoothing(float smoothing) {
        putExtra("gyroSmoothing", String.valueOf(Math.min(0.95f, Math.max(0.0f, smoothing))));
    }

    public boolean isGyroInvertX() {
        return getExtra("gyroInvertX", GYRO_INVERT_X_DEFAULT ? "1" : "0").equals("1");
    }

    public void setGyroInvertX(boolean invert) {
        putExtra("gyroInvertX", invert ? "1" : "0");
    }

    public boolean isGyroInvertY() {
        return getExtra("gyroInvertY", GYRO_INVERT_Y_DEFAULT ? "1" : "0").equals("1");
    }

    public void setGyroInvertY(boolean invert) {
        putExtra("gyroInvertY", invert ? "1" : "0");
    }

    // FPS limiter (implemented by the bionic-fg layer: paces the real/base frames, so with
    // frame gen on the on-screen rate is limit × multiplier). Tuned live from the in-game menu.
    public static final int FPS_LIMITER_DEFAULT = 60;

    public boolean isFpsLimiterEnabled() {
        return getExtra("fpsLimiterEnabled", "0").equals("1");
    }

    public void setFpsLimiterEnabled(boolean enabled) {
        putExtra("fpsLimiterEnabled", enabled ? "1" : "0");
    }

    public int getFpsLimiterValue() {
        try {
            int v = Integer.parseInt(getExtra("fpsLimiterValue", String.valueOf(FPS_LIMITER_DEFAULT)));
            return (v < 10 || v > 200) ? FPS_LIMITER_DEFAULT : v;
        }
        catch (NumberFormatException e) {
            return FPS_LIMITER_DEFAULT;
        }
    }

    public void setFpsLimiterValue(int value) {
        putExtra("fpsLimiterValue", String.valueOf(value));
    }

    // Match the Android display refresh rate to the game's FPS (votes Surface.setFrameRate). Default
    // ON: it's safe because it only ever votes a rate while the FPS limiter is actually capping;
    // otherwise it votes 0 (no-op / panel runs free). Complementary to the limiter (producer rate).
    public boolean isMatchRefreshRate() {
        return getExtra("matchRefreshRate", "1").equals("1");
    }

    public void setMatchRefreshRate(boolean enabled) {
        putExtra("matchRefreshRate", enabled ? "1" : "0");
    }

    // Manual refresh-rate lock (Hz). Used when "Auto (match FPS)" is OFF: the panel is pinned to this
    // rate regardless of the FPS cap. 0 = no manual lock (panel runs free / native).
    public int getManualRefreshRate() {
        try { return Integer.parseInt(getExtra("manualRefreshRate", "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    public void setManualRefreshRate(int rate) {
        putExtra("manualRefreshRate", String.valueOf(rate));
    }

    // Ceiling (Hz) on the refresh rates our RandR extension advertises to Wine, which is what
    // populates a game's own in-game refresh/display dropdown. 0 = no cap (offer every rate the
    // panel supports). NOTE this is the GUEST-side list and is a different axis from
    // matchRefreshRate/manualRefreshRate above, which drive the HOST Android surface: this one
    // bounds what the game is allowed to ask for, those decide what the panel actually runs at.
    public int getMaxGameRefreshRate() {
        try { return Integer.parseInt(getExtra("maxGameRefreshRate", "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    public void setMaxGameRefreshRate(int rate) {
        putExtra("maxGameRefreshRate", String.valueOf(rate));
    }

    // Unlock the game's own in-game refresh dropdown by turning OFF Wine's win32u display-mode
    // emulation (which otherwise discards the rates our RandR extension advertises and hardcodes the
    // guest to {60, current}). Applied as the two "X11 Driver" registry values EmulateModelist /
    // EmulateModeset = "Y" (INVERTED semantics: "Y" disables emulation). Default ON — the whole
    // refresh feature is opt-in-by-hardware; the toggle lets a user turn it off if a game misbehaves
    // with the reduced (container-res-capped) resolution ladder emulation-off produces.
    public boolean isUnlockGameRefreshRate() {
        return getExtra("unlockGameRefreshRate", "1").equals("1");
    }

    public void setUnlockGameRefreshRate(boolean unlock) {
        putExtra("unlockGameRefreshRate", unlock ? "1" : "0");
    }

    // --- ReShade effect (vkBasalt drop-in), per-container default; the per-game shortcut can
    // override both keys via its extras (resolvedReshade* in XServerDisplayActivity). "None" / empty
    // = no effect. reshadeParams is a JSON object of {uniformName: value} overriding the .fx defaults.
    public String getReshadeEffect() {
        return getExtra("reshadeEffect", "None");
    }

    public void setReshadeEffect(String effect) {
        putExtra("reshadeEffect", (effect == null || effect.isEmpty()) ? "None" : effect);
    }

    public String getReshadeParams() {
        return getExtra("reshadeParams", "");
    }

    public void setReshadeParams(String json) {
        putExtra("reshadeParams", (json == null || json.isEmpty()) ? null : json);
    }

    // --- ReShade multi-effect loadout (Tier 1). reshadeLoadout is a JSON array of
    // {"name":..,"enabled":..} in chain order; reshadeMode is "solo" (one active) or "stack" (any
    // subset). The nested reshadeParams ({"<effect>":{uniform:value}}) hold per-effect overrides.
    // When reshadeLoadout is absent the legacy single reshadeEffect + flat reshadeParams are migrated
    // transparently (see ReshadeLoadout.parse / .paramsForEffect). "" = no loadout set.
    public String getReshadeLoadout() {
        return getExtra("reshadeLoadout", "");
    }

    public void setReshadeLoadout(String json) {
        putExtra("reshadeLoadout", (json == null || json.isEmpty()) ? null : json);
    }

    public String getReshadeMode() {
        return getExtra("reshadeMode", "solo");
    }

    public void setReshadeMode(String mode) {
        putExtra("reshadeMode", (mode == null || mode.isEmpty()) ? null : mode);
    }

    // Master (whole-chain) on/off — mirrors the in-game ReShade drawer switch (enableOnLaunch). ON is
    // the default whenever a loadout is present; only an explicit in-game OFF is stored ("0") so the
    // absence of the key keeps the historical "on when there's something to show" behaviour.
    public boolean getReshadeMasterEnabled() {
        return !getExtra("reshadeMasterEnabled", "1").equals("0");
    }

    public void setReshadeMasterEnabled(boolean enabled) {
        putExtra("reshadeMasterEnabled", enabled ? null : "0");
    }

    public String getWineVersion() {
        return wineVersion;
    }

    public void setWineVersion(String wineVersion) {
        this.wineVersion = wineVersion;
    }

    public String getBasePrefix() {
        return basePrefix != null ? basePrefix : "";
    }

    public void setBasePrefix(String basePrefix) {
        this.basePrefix = basePrefix != null ? basePrefix : "";
    }

    public boolean isOverlay() {
        return ContainerOverlay.isEligible(this) && !getBasePrefix().isEmpty();
    }

    public File getConfigFile() {
        return new File(rootDir, ".container");
    }

    public File getDesktopDir() {
        return new File(rootDir, ".wine/drive_c/users/"+ImageFs.USER+"/Desktop/");
    }

    public File getStartMenuDir() {
        return new File(rootDir, ".wine/drive_c/ProgramData/Microsoft/Windows/Start Menu/");
    }

    public File getIconsDir(int size) {
        return new File(rootDir, ".local/share/icons/hicolor/"+size+"x"+size+"/apps/");
    }

    public String getDesktopTheme() {
        return desktopTheme;
    }

    public void setDesktopTheme(String desktopTheme) {
        this.desktopTheme = desktopTheme;
    }

    public String getMIDISoundFont() {
        return midiSoundFont;
    }

    public void setMidiSoundFont(String fileName) {
        midiSoundFont = fileName;
    }

    public int getInputType() {
        return inputType;
    }

    public void setInputType(int inputType) {
        this.inputType = inputType;
    }

    public boolean isHermesXInput() {
        return hermesXInput;
    }

    public void setHermesXInput(boolean hermesXInput) {
        this.hermesXInput = hermesXInput;
    }

    public boolean isHermesXInputSwitching() {
        return hermesXInputSwitching;
    }

    public void setHermesXInputSwitching(boolean hermesXInputSwitching) {
        this.hermesXInputSwitching = hermesXInputSwitching;
    }

    public boolean isExclusiveXInput() {
        return exclusiveXInput;
    }

    public void setExclusiveXInput(boolean exclusiveXInput) {
        this.exclusiveXInput = exclusiveXInput;
    }



    public String getRenderer() { return renderer; }
    public void setRenderer(String renderer) { this.renderer = renderer; }

    public boolean isRendererNative() { return rendererNative; }
    public void setRendererNative(boolean v) { this.rendererNative = v; }
    public String getRendererPresentMode() { return rendererPresentMode; }
    public void setRendererPresentMode(String v) { this.rendererPresentMode = v != null ? v : "fifo"; }
    public String getRendererDriverId() { return rendererDriverId; }
    public void setRendererDriverId(String v) { this.rendererDriverId = v != null ? v : ""; }
    public int getRendererFilterMode() { return rendererFilterMode; }
    public void setRendererFilterMode(int v) { this.rendererFilterMode = v; }
    public boolean getRendererSwapRB() { return rendererSwapRB; }
    public void setRendererSwapRB(boolean v) { this.rendererSwapRB = v; }
    public boolean isSurfaceDirectPerformanceMode() { return surfaceDirectPerformanceMode; }
    public void setSurfaceDirectPerformanceMode(boolean v) { this.surfaceDirectPerformanceMode = v; }
    public boolean isSurfaceDirectPresentAtRefreshRate() { return surfaceDirectPresentAtRefreshRate; }
    public void setSurfaceDirectPresentAtRefreshRate(boolean v) { this.surfaceDirectPresentAtRefreshRate = v; }
    public boolean isSurfaceDirectBackPressure() { return surfaceDirectBackPressure; }
    public void setSurfaceDirectBackPressure(boolean v) { this.surfaceDirectBackPressure = v; }
    public boolean isSurfaceDirectPrecisePresentation() { return surfaceDirectPrecisePresentation; }
    public void setSurfaceDirectPrecisePresentation(boolean v) { this.surfaceDirectPrecisePresentation = v; }

    public static String getDefaultVulkanConfig() {
        return "native=false;presentMode=fifo;driverId=system;filterMode=0;swapRB=false";
    }

    public Iterable<String[]> drivesIterator() {
        return drivesIterator(drives);
    }

    public static Iterable<String[]> drivesIterator(final String drives) {
        final int[] index = {drives.indexOf(":")};
        final String[] item = new String[2];
        return () -> new Iterator<String[]>() {
            @Override
            public boolean hasNext() {
                return index[0] != -1;
            }

            @Override
            public String[] next() {
                item[0] = String.valueOf(drives.charAt(index[0]-1));
                int nextIndex = drives.indexOf(":", index[0]+1);
                item[1] = drives.substring(index[0]+1, nextIndex != -1 ? nextIndex-1 : drives.length());
                index[0] = nextIndex;
                return item;
            }
        };
    }

    public boolean saveData() {
        try {
            File file = getConfigFile();
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            boolean ok = FileUtils.writeString(file, getData().toString());
            if (!ok) Log.e("Container", "saveData: could not write " + file.getAbsolutePath());
            return ok;
        }
        catch (JSONException e) {
            Log.e("Container", "saveData: could not serialise container config", e);
            return false;
        }
    }

    // The full config JSON exactly as saveData() persists it (extraData included). Split out of
    // saveData() so callers that need the serialized form WITHOUT writing to disk — e.g. the
    // "New Container Defaults" profile, which templates a transient container and strips the
    // per-container name/drives — can reuse the identical field set instead of drifting a copy.
    public JSONObject getData() throws JSONException {
        JSONObject data = new JSONObject();
        {
            data.put("id", id);
            data.put("name", name);
            data.put("screenSize", screenSize);
            data.put("envVars", envVars);
            data.put("cpuList", cpuList);
            data.put("cpuListWoW64", cpuListWoW64);
            data.put("graphicsDriver", graphicsDriver);
            data.put("graphicsDriverConfig", graphicsDriverConfig);
            data.put("emulator", emulator);
            data.put("dxwrapper", dxwrapper);
            if (!dxwrapperConfig.isEmpty()) data.put("dxwrapperConfig", dxwrapperConfig);
            data.put("audioDriver", audioDriver);
            data.put("wincomponents", wincomponents);
            data.put("drives", drives);
            data.put("showFPS", showFPS);
            data.put("fpsCounterConfig", fpsCounterConfig);
            data.put("fullscreenMode", fullscreenMode);
            data.put("screenAlignment", screenAlignment);
            data.put("inputType", inputType);
            data.put("startupSelection", startupSelection);
            data.put("startupServices", startupServices);
            data.put("box64Version", box64Version);
            data.put("fexcorePreset", fexcorePreset);
            data.put("fexcoreVersion", fexcoreVersion);
            data.put("box64Preset", box64Preset);
            // Only written once the container actually carries its own values, so an untouched
            // container's config file is byte-identical to before this existed.
            if (fexcorePresetVars != null) data.put("fexcorePresetVars", fexcorePresetVars);
            if (box64PresetVars != null) data.put("box64PresetVars", box64PresetVars);
            data.put("desktopTheme", desktopTheme);
            if (extraData != null) data.put("extraData", extraData);
            data.put("midiSoundFont", midiSoundFont);
            data.put("lc_all", lc_all);
            data.put("primaryController", primaryController);
            data.put("controllerMapping", controllerMapping);
            data.put("exclusiveXInput", exclusiveXInput);
            data.put("hermesXInput", hermesXInput);
            data.put("hermesXInputSwitching", hermesXInputSwitching);
            data.put("renderer", renderer);
            data.put("rendererNative", rendererNative);
            data.put("rendererPresentMode", rendererPresentMode);
            if (!rendererDriverId.isEmpty()) data.put("rendererDriverId", rendererDriverId);
            if (rendererFilterMode != 0) data.put("rendererFilterMode", rendererFilterMode);
            if (rendererSwapRB) data.put("rendererSwapRB", true);
            // Only written when changed, so existing containers keep an identical file.
            if (!surfaceDirectPerformanceMode) data.put("surfaceDirectPerformanceMode", false);
            if (!surfaceDirectPresentAtRefreshRate) data.put("surfaceDirectPresentAtRefreshRate", false);
            if (surfaceDirectBackPressure) data.put("surfaceDirectBackPressure", true);
            if (surfaceDirectPrecisePresentation) data.put("surfaceDirectPrecisePresentation", true);
            if (!WineInfo.isMainWineVersion(wineVersion)) data.put("wineVersion", wineVersion);
            if (!getBasePrefix().isEmpty()) data.put("basePrefix", basePrefix);
        }
        return data;
    }


    public void loadData(JSONObject data) throws JSONException {
        wineVersion = WineInfo.MAIN_WINE_VERSION.identifier();
        dxwrapperConfig = "";
        checkObsoleteOrMissingProperties(data);

        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
            String key = it.next();
            switch (key) {
                case "name" :
                    setName(data.getString(key));
                    break;
                case "screenSize" :
                    setScreenSize(data.getString(key));
                    break;
                case "envVars" :
                    setEnvVars(data.getString(key));
                    break;
                case "cpuList" :
                    setCPUList(data.getString(key));
                    break;
                case "cpuListWoW64" :
                    setCPUListWoW64(data.getString(key));
                    break;
                case "graphicsDriver" :
                    setGraphicsDriver(data.getString(key));
                    break;
                case "graphicsDriverConfig" :
                    setGraphicsDriverConfig(data.getString(key));
                    break;
                case "emulator":
                    setEmulator(data.getString(key));
                    break;
                case "wincomponents" :
                    setWinComponents(data.getString(key));
                    break;
                case "dxwrapper" :
                    setDXWrapper(data.getString(key));
                    break;
                case "dxwrapperConfig" :
                    setDXWrapperConfig(data.getString(key));
                    break;
                case "drives" :
                    setDrives(data.getString(key));
                    break;
                case "showFPS" :
                    setShowFPS(data.getBoolean(key));
                    break;
                case "fpsCounterConfig" :
                    setFPSCounterConfig(data.getString(key));
                    break;
                case "fullscreenMode" :
                    setFullscreenMode(data.getInt(key));
                    break;
                case "screenAlignment" :
                    // Absent key -> stays default ALIGN_CENTER, so existing containers are unaffected.
                    setScreenAlignment(data.getInt(key));
                    break;
                case "fullscreenStretched" :
                    // Backward-compat migration: only honour the legacy boolean when the new int
                    // key is absent (true -> STRETCH, false -> OFF). Saves back as fullscreenMode.
                    if (!data.has("fullscreenMode")) setFullscreenStretched(data.getBoolean(key));
                    break;
                case "inputType" :
                    setInputType(data.getInt(key));
                    break;
                case "startupSelection" :
                    setStartupSelection((byte)data.getInt(key));
                    break;
                case "startupServices" :
                    setStartupServices(data.getString(key));
                    break;
                case "extraData" : {
                    JSONObject extraData = data.getJSONObject(key);
                    checkObsoleteOrMissingProperties(extraData);
                    setExtraData(extraData);
                    break;
                }
                case "wineVersion" :
                    setWineVersion(data.getString(key));
                    break;
                case "basePrefix" :
                    setBasePrefix(data.optString(key, ""));
                    break;
                case "box64Version":
                    setBox64Version(data.getString(key));
                    break;
                case "fexcoreVersion":
                    setFEXCoreVersion(data.getString(key));
                    break;
                case "fexcorePreset":
                    setFEXCorePreset(data.getString(key));
                    break;
                case "box64Preset" :
                    setBox64Preset(data.getString(key));
                    break;
                case "fexcorePresetVars" :
                    fexcorePresetVars = data.getString(key);
                    break;
                case "box64PresetVars" :
                    box64PresetVars = data.getString(key);
                    break;
                case "audioDriver" :
                    setAudioDriver(data.getString(key));
                    break;
                case "desktopTheme" :
                    setDesktopTheme(data.getString(key));
                    break;
                case "midiSoundFont" :
                    setMidiSoundFont(data.getString(key));
                    break;
                case "lc_all" :
                    setLC_ALL(data.getString(key));
                    break;
                case "primaryController" :
                    setPrimaryController(data.getInt(key));
                    break;
                case "controllerMapping" :
                    controllerMapping = data.getString(key);
                    break;
                case "exclusiveXInput" :
                    setExclusiveXInput(data.getBoolean(key));
                    break;
                case "hermesXInput" :
                    setHermesXInput(data.optBoolean(key, false));
                    break;
                case "hermesXInputSwitching" :
                    setHermesXInputSwitching(data.optBoolean(key, true));
                    break;
                case "renderer" :
                    setRenderer(data.getString(key));
                    break;
                case "rendererNative" :
                    rendererNative = data.getBoolean(key);
                    break;
                case "rendererPresentMode" :
                    rendererPresentMode = data.getString(key);
                    break;
                case "rendererDriverId":
                    rendererDriverId = data.getString(key);
                    break;
                case "rendererFilterMode" :
                    rendererFilterMode = data.getInt(key);
                    break;
                case "rendererSwapRB":
                    rendererSwapRB = data.getBoolean(key);
                    break;
                case "surfaceDirectPerformanceMode":
                    surfaceDirectPerformanceMode = data.getBoolean(key);
                    break;
                case "surfaceDirectPresentAtRefreshRate":
                    surfaceDirectPresentAtRefreshRate = data.getBoolean(key);
                    break;
                case "surfaceDirectBackPressure":
                    surfaceDirectBackPressure = data.getBoolean(key);
                    break;
                case "surfaceDirectPrecisePresentation":
                    surfaceDirectPrecisePresentation = data.getBoolean(key);
                    break;
            }
        }

        // Configs written outside the editor drop these keys (JSONObject.put(name, null) removes
        // them) and the launch path dereferences them unguarded — heal with the editor defaults.
        if (emulator == null || emulator.isEmpty()) emulator = DEFAULT_EMULATOR;
        if (box64Version == null || box64Version.isEmpty()) box64Version = DefaultVersion.BOX64;
        if (fexcoreVersion == null || fexcoreVersion.isEmpty()) fexcoreVersion = DefaultVersion.FEXCORE;
    }

    public static void checkObsoleteOrMissingProperties(JSONObject data) {
        try {
            if (data.has("dxcomponents")) {
                data.put("wincomponents", data.getString("dxcomponents"));
                data.remove("dxcomponents");
            }

            if (data.has("dxwrapper")) {
                String dxwrapper = data.getString("dxwrapper");
                if (dxwrapper.equals("original-wined3d")) {
                    data.put("dxwrapper", DEFAULT_DXWRAPPER);
                }
                else if (dxwrapper.startsWith("d8vk-") || dxwrapper.startsWith("dxvk-")) {
                    data.put("dxwrapper", dxwrapper);
                }

            }

            if (data.has("graphicsDriver")) {
                String graphicsDriver = data.getString("graphicsDriver");
                if (graphicsDriver.equals("turnip-zink") || graphicsDriver.equals("turnip")) {
                    data.put("graphicsDriver", "wrapper");
                }
                else if (graphicsDriver.equals("llvmpipe")) {
                    data.put("graphicsDriver", "virgl");
                }
            }

            if (data.has("envVars") && data.has("extraData")) {
                JSONObject extraData = data.getJSONObject("extraData");
                // Back-fill env vars added since app version 16, but ONLY onto a container that
                // really carries an old stamp. An ABSENT appVersion means "never booted", not
                // "written by app version 0": it is what a container the editor just wrote looks
                // like, and what the New Container Defaults profile always looks like. Treating
                // that as legacy re-added every DEFAULT_ENV_VARS entry the user had deliberately
                // deleted, silently undoing their edit. (Parsing defensively also keeps a junk
                // stamp from throwing NumberFormatException straight out of loadData, which no
                // caller catches.)
                String stamp = extraData.optString("appVersion", "");
                int appVersion = Integer.MAX_VALUE;
                if (!stamp.isEmpty()) {
                    try { appVersion = Integer.parseInt(stamp); }
                    catch (NumberFormatException e) { appVersion = 0; }
                }
                if (appVersion < 16) {
                    EnvVars defaultEnvVars = new EnvVars(DEFAULT_ENV_VARS);
                    EnvVars envVars = new EnvVars(data.getString("envVars"));
                    for (String name : defaultEnvVars) if (!envVars.has(name)) envVars.put(name, defaultEnvVars.get(name));
                    data.put("envVars", envVars.toString());
                }
            }

            KeyValueSet wincomponents1 = new KeyValueSet(DEFAULT_WINCOMPONENTS);
            KeyValueSet wincomponents2 = new KeyValueSet(data.getString("wincomponents"));
            String result = "";

            for (String[] wincomponent1 : wincomponents1) {
                String value = wincomponent1[1];

                for (String[] wincomponent2 : wincomponents2) {
                    if (wincomponent1[0].equals(wincomponent2[0])) {
                        value = wincomponent2[1];
                        break;
                    }
                }

                result += (!result.isEmpty() ? "," : "")+wincomponent1[0]+"="+value;
            }

            data.put("wincomponents", result);
        }
        catch (JSONException e) {}
    }

    public static String getFallbackCPUList() {
        String cpuList = "";
        int numProcessors = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < numProcessors; i++) cpuList += (!cpuList.isEmpty() ? "," : "")+i;
        return cpuList;
    }

    public static String getFallbackCPUListWoW64() {
        String cpuList = "";
        int numProcessors = Runtime.getRuntime().availableProcessors();
        for (int i = numProcessors / 2; i < numProcessors; i++) cpuList += (!cpuList.isEmpty() ? "," : "")+i;
        return cpuList;
    }

    // Check if a specific environment variable exists
    public boolean hasEnvVar(String keyValue) {
        if (envVars == null || envVars.isEmpty()) return false;
        String[] vars = envVars.split(",");
        for (String var : vars) {
            if (var.trim().equalsIgnoreCase(keyValue.trim())) {
                return true; // Found the variable
            }
        }
        return false;
    }

}









