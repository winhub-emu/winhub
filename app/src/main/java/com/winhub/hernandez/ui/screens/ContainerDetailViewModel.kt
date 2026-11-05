package com.winhub.hernandez.ui.screens

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.winhub.hernandez.R
import com.winhub.hernandez.box64.Box64Preset
import com.winhub.hernandez.box64.Box64PresetManager
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.WrapperManager
import com.winhub.hernandez.core.AppUtils
import com.winhub.hernandez.core.DefaultVersion
import com.winhub.hernandez.core.EnvVars
import com.winhub.hernandez.core.GPUInformation
import com.winhub.hernandez.core.NewContainerDefaults
import com.winhub.hernandez.core.PreloaderState
import com.winhub.hernandez.core.StorageRoots
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.SyncCaps
import com.winhub.hernandez.core.SyncMode
import com.winhub.hernandez.core.SyncSupport
import com.winhub.hernandez.core.WineInfo
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.core.WineRegistryEditor
import com.winhub.hernandez.core.WineUtils
import com.winhub.hernandez.core.WineThemeManager
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
import com.winhub.hernandez.fexcore.FEXCoreManager
import com.winhub.hernandez.fexcore.FEXCorePreset
import com.winhub.hernandez.fexcore.FEXCorePresetManager
import com.winhub.hernandez.midi.MidiManager
import com.winhub.hernandez.winhandler.WinHandler
import com.winhub.hernandez.xserver.XKeycode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class DriveEntry(val uid: Long = System.nanoTime(), var letter: String, var path: String)
data class WinComponentEntry(val key: String, var selectedIndex: Int, val label: String)

class ContainerDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context = app.applicationContext
    private lateinit var manager: ContainerManager
    private lateinit var contentsManager: ContentsManager

    var container: Container? = null; private set
    var isEditMode by mutableStateOf(false); private set
    var isSaving by mutableStateOf(false); private set
    private var initialized = false
    // Deck Mode hosts this editor inside the shell, so its activity-scoped ViewModel is reused for
    // every container id. Remember which id the form was loaded from: opening "+", "Defaults" or a
    // different container must reload, otherwise the previous container/defaults state leaks in
    // (Defaults silently creates a container, "+" edits the last-opened one).
    private var initializedFor: Int? = null

    // "New Container Defaults" mode: the SAME container form, but ✓ saves the field state as the
    // user's new-container defaults profile (NewContainerDefaults) instead of creating a container.
    // Entered via the EDIT_DEFAULTS_ID sentinel (see init); container stays null (it's a template).
    var defaultsMode by mutableStateOf(false); private set

    // Which architecture's defaults profile the form is editing WHILE in defaultsMode. Defaults are
    // stored per-arch (box64/wowbox64/FEXCore/emulator are arch-coupled), so in defaults mode an
    // ARCHITECTURE selector replaces the Wine Version dropdown and drives the form's arch via
    // applyArch(). arm64ec is the modern default. Meaningless outside defaultsMode (create/edit derive
    // the arch from the real/selected wine version instead). See setDefaultsArch().
    var defaultsArch by mutableStateOf(NewContainerDefaults.ARCH_ARM64EC); private set

    companion object {
        // Sentinel containerId that opens the editor in "New Container Defaults" mode. Negative like
        // the create sentinel (-1) so getContainerById is never consulted; distinct so init can tell
        // "edit the defaults" from "create a new container".
        const val EDIT_DEFAULTS_ID = -2

        /**
         * The create payload for flows that build a container WITHOUT opening this editor — the Deck
         * Add-Games "Cronos" path. It runs on a THROWAWAY form (never the shared editor instance, so
         * an editor open elsewhere can't be disturbed) seeded exactly like a manual New Container:
         * init(-1) applies the saved "New Container Defaults" profile for the default wine's arch, or
         * the built-in Container.DEFAULT_* when no profile exists; [wineVersion] then goes through
         * onWineVersionChanged — the same call the editor makes when that wine is picked in its
         * dropdown — so the arch-dependent slice (emulator, box64/wowbox64 + FEXCore versions and
         * presets, audio coercion) is re-seeded for THAT wine's arch. The result is the identical
         * buildCreateData JSON the editor's FAB sends.
         *
         * This is what a bare `Container(0, mgr).getData()` cannot do: JSONObject.put(name, null)
         * REMOVES the key, so a template's null cpuList/cpuListWoW64/emulator/box64Version/
         * fexcoreVersion never reach the config file, and the container then dies at launch
         * (GuestProgramLauncherComponent.box64Version/fexcoreVersion/emulator are all dereferenced
         * unguarded) — the "invalid settings" a hand-made container never has.
         *
         * Returns null when no wine entry resolves: the caller must abort (the editor's own
         * blank-wine gate, without its Toast).
         */
        fun buildFreshCreateData(
            context: Context,
            name: String,
            wineVersion: String,
            runAsAdmin: Boolean,
        ): JSONObject? {
            val vm = runCatching {
                ContainerDetailViewModel(context.applicationContext as Application).also { it.init(-1) }
            }.getOrNull() ?: return null
            vm.containerName = name
            if (wineVersion.isNotBlank()) vm.onWineVersionChanged(wineVersion)
            if (vm.selectedWineVersion.isBlank()) return null
            vm.runAsAdmin = runAsAdmin
            return runCatching {
                vm.buildCreateData(
                    vm.graphicsDriverConfig,
                    vm.dxWrapperConfig,
                    vm.fpsCounterConfig,
                    vm.envVarsStr,
                    vm.cpuList,
                    vm.cpuListWoW64,
                    // Same conversion the editor's ColorPickerView does for its colorAsString, so the
                    // seeded desktop background colour reaches desktopTheme byte-identically.
                    String.format(Locale.ENGLISH, "#%06X", 0x00ffffff and vm.desktopBgColorInt),
                )
            }.getOrNull()
        }
    }

    // ── Top-level fields ──────────────────────────────────────────────────────
    var containerName by mutableStateOf("")

    var screenSizeEntries by mutableStateOf(emptyList<String>()); private set
    var selectedScreenSize by mutableStateOf(Container.DEFAULT_SCREEN_SIZE)
    var customWidth  by mutableStateOf("")
    var customHeight by mutableStateOf("")

    var wineVersionEntries by mutableStateOf(emptyList<String>()); private set
    var selectedWineVersion by mutableStateOf("")

    // Sync (extra "syncMode": esync | ntsync | wineserver; core.SyncSupport). syncMode is the pick the
    // editor shows; syncCaps what the selected layer can run (null while the off-main ntdll.so probe
    // runs); syncNotice the "switched back" line after a layer change made the pick unavailable.
    // syncFollowsLayer: nothing was chosen yet (new container, or an old one whose env never said),
    // so the pick tracks the layer default quietly instead of claiming a choice.
    var syncMode by mutableStateOf(SyncMode.ESYNC); private set
    var syncCaps by mutableStateOf<SyncCaps?>(null); private set
    var syncNotice by mutableStateOf<String?>(null); private set
    private var syncFollowsLayer = true
    private var syncProbeJob: Job? = null

    fun onSyncModePicked(mode: String) {
        if (syncCaps?.isAvailable(mode) == false || SyncMode.normalize(mode) == null) return
        syncMode = mode
        syncFollowsLayer = false
        syncNotice = null
    }

    // Re-probe the selected layer (cached per layer dir) and re-check the pick against it. The cached
    // answer, if any, applies at once so the greying never lags a layer switch.
    private fun refreshSyncCaps(layer: String) {
        syncProbeJob?.cancel()
        syncNotice = null
        if (defaultsMode || layer.isEmpty()) { syncCaps = null; return }
        val known = SyncSupport.peek(layer)
        if (known != null) applySyncCaps(layer, known) else syncCaps = null
        syncProbeJob = viewModelScope.launch(Dispatchers.Main) {
            val caps = withContext(Dispatchers.IO) {
                SyncSupport.capsFor(context, if (::contentsManager.isInitialized) contentsManager else null, layer)
            }
            if (selectedWineVersion == layer) applySyncCaps(layer, caps)
        }
    }

    private fun applySyncCaps(layer: String, caps: SyncCaps) {
        syncCaps = caps
        when {
            syncFollowsLayer -> syncMode = caps.defaultMode
            !caps.isAvailable(syncMode) -> {
                syncNotice = com.winhub.hernandez.ui.components.syncSwitchedBackNotice(syncMode, layer, caps)
                syncMode = caps.defaultMode
            }
        }
    }

    // Whether the given wine/Proton layer has xrandr compiled in — i.e. can actually deliver the
    // in-game refresh unlock. The editor uses this to warn under the "In-game refresh rate" control
    // when the selected Proton can't. Cached per layer id in WineRandrSupport (cheap to recall).
    //
    // MUST be safe during early composition and in the create-new-container flow: contentsManager is a
    // lateinit set in init(), which runs AFTER the first compose pass, and a brand-new container has no
    // chosen layer yet. When we can't probe (uninitialized manager or empty version), return the benign
    // "capable" default so the incompatible-layer hint simply doesn't show and nothing throws.
    fun isWineXrandrCapable(wineVersion: String): Boolean {
        if (!::contentsManager.isInitialized || wineVersion.isEmpty()) return true
        return com.winhub.hernandez.core.WineRandrSupport.isXrandrCapable(context, contentsManager, wineVersion)
    }

    // Persist the guest-side refresh setting, but ONLY when it's a deliberate non-default (or the
    // container already had it set). Leaving the pure default (unlock + no cap = Unlimited) unwritten
    // keeps an untouched container "default", so the launch-time compatible-layer Toast never nags a
    // user who never opted in. Both extras are written together so the dropdown round-trips.
    private fun applyRefreshSettings(c: com.winhub.hernandez.container.Container) {
        val isDefault = unlockGameRefreshRate && maxGameRefreshRate == 0
        if (refreshWasExplicit || !isDefault) {
            c.setUnlockGameRefreshRate(unlockGameRefreshRate)
            c.setMaxGameRefreshRate(maxGameRefreshRate)
        }
    }
    var wineVersionEnabled by mutableStateOf(true); private set
    var isArm64EC by mutableStateOf(false); private set

    var graphicsDriverEntries by mutableStateOf(emptyList<String>()); private set
    var selectedGraphicsDriver by mutableStateOf(Container.DEFAULT_GRAPHICS_DRIVER)
    // graphicsDriverConfig stored via dummy View tag in Screen composable
    var graphicsDriverConfig by mutableStateOf(Container.DEFAULT_GRAPHICSDRIVERCONFIG)

    // Advanced Vulkan present options — backed by the container's dedicated renderer* fields
    // (NOT graphicsDriverConfig, whose KeyValueSet/semicolon mismatch made these never apply).
    var rendererNative      by mutableStateOf(false)
    var rendererPresentMode by mutableStateOf("fifo")
    var rendererDriverId    by mutableStateOf("system")
    var rendererFilterMode  by mutableStateOf(0)
    var rendererSwapRB      by mutableStateOf(false)
    var surfaceDirectPerformanceMode       by mutableStateOf(true)
    var surfaceDirectPresentAtRefreshRate  by mutableStateOf(true)
    var surfaceDirectBackPressure          by mutableStateOf(false)
    var surfaceDirectPrecisePresentation   by mutableStateOf(false)

    // Display backend: always X11.
    var displayBackend by mutableStateOf(Container.DISPLAY_BACKEND_X11)
    // Unreal Engine HDR (extra "unrealHdr": off | dx12 | dx11, absent = off).
    // See core.UnrealHdr.
    var unrealHdr by mutableStateOf(com.winhub.hernandez.core.UnrealHdr.OFF)
    // Render scale (supersampling) — stored via the "renderScale" extra (no DB field). "1.0" = Off.
    var renderScale         by mutableStateOf("1.0")
    var autoCloseOnExit     by mutableStateOf(true)

    var dxWrapperEntries by mutableStateOf(emptyList<String>()); private set
    var selectedDXWrapper by mutableStateOf(Container.DEFAULT_DXWRAPPER)
    var dxWrapperConfig by mutableStateOf(Container.DEFAULT_DXWRAPPERCONFIG)

    var audioDriverEntries by mutableStateOf(emptyList<String>()); private set
    var selectedAudioDriver by mutableStateOf(Container.DEFAULT_AUDIO_DRIVER)

    var emulatorEntries by mutableStateOf(emptyList<String>()); private set
    var selectedEmulator by mutableStateOf(Container.DEFAULT_EMULATOR)
    var emulatorEnabled by mutableStateOf(false); private set

    var midiEntries by mutableStateOf(emptyList<String>()); private set
    var selectedMidiIndex by mutableStateOf(0)

    var showFPS by mutableStateOf(false)
    var fpsCounterConfig by mutableStateOf(Container.DEFAULT_FPS_COUNTER_CONFIG)
    // Fullscreen aspect-ratio mode (#71): Container.FULLSCREEN_OFF/FIT/STRETCH.
    var fullscreenMode by mutableStateOf(Container.FULLSCREEN_OFF)
    // Screen alignment (#413): Container.ALIGN_CENTER/TOP/BOTTOM — vertical placement of the letterbox bar.
    var screenAlignment by mutableStateOf(Container.ALIGN_CENTER)

    // Frame-gen engine (per-container): "off" | "bionic" | "lsfg" (mutually exclusive).
    // multiplier & flow scale are tuned live from the in-game side menu (bionic-fg).
    var frameGenEngine by mutableStateOf("off")
    // bionic-fg interpolation model (per-container, 0-3). 0 = the long-standing default chain;
    // 1-3 are newer engines that are not device-proven yet. Only meaningful when engine=="bionic".
    var frameGenModel by mutableStateOf(0)
    // lsfg-vk performance_mode (per-container): lower interpolation quality for higher FPS. Also
    // live-toggleable from the in-game FG menu. Only meaningful when frameGenEngine == "lsfg".
    // Default ON for new/unset containers (see loadContainerData) — initial value mirrors that.
    var lsfgPerformanceMode by mutableStateOf(true)
    // lsfg-vk auto-enable at launch (per-container): start frame gen live at the saved multiplier
    // from launch instead of off. Only meaningful when frameGenEngine == "lsfg". Default ON (matches
    // GameNative; see loadContainerData) — initial value mirrors that.
    var lsfgAutoEnable by mutableStateOf(true)
    // LSFG Native experimental knobs (only shown while FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED). The capture
    // selection is a UI label: "panel", "game", "custom" or one of screenSizeEntries; it is turned
    // into the stored "panel" / "game" / bare-height value by resolvedFgCaptureResolution(). Only
    // the height is ever used (the width follows the panel), so Custom is a single height field
    // and the stored form is the same one the in-game drawer chips write.
    var fgCaptureSelection by mutableStateOf(Container.FG_CAPTURE_PANEL)
    var fgCaptureCustomHeight by mutableStateOf("")
    var lsfgVk11Compat by mutableStateOf(false)

    fun resolvedFgCaptureResolution(): String = when {
        fgCaptureSelection == Container.FG_CAPTURE_PANEL -> Container.FG_CAPTURE_PANEL
        fgCaptureSelection == Container.FG_CAPTURE_GAME  -> Container.FG_CAPTURE_GAME
        fgCaptureSelection.equals("custom", ignoreCase = true) -> {
            val h = fgCaptureCustomHeight.trim().toIntOrNull()
            if (h != null && h > 0) h.toString() else Container.FG_CAPTURE_PANEL
        }
        else -> {   // "1280x720 (16:9)" -> 720
            val h = Container.fgCaptureHeightFor(StringUtils.parseIdentifier(fgCaptureSelection))
            if (h > 0) h.toString() else Container.FG_CAPTURE_PANEL
        }
    }

    /** A stored value ("720", legacy "1280x720", "panel", "game") back to the picker. */
    private fun seedFgCaptureSelection(stored: String?) {
        val v = stored ?: Container.FG_CAPTURE_PANEL
        fgCaptureCustomHeight = ""
        fgCaptureSelection = when {
            v == Container.FG_CAPTURE_PANEL || v.isEmpty() -> Container.FG_CAPTURE_PANEL
            v == Container.FG_CAPTURE_GAME -> Container.FG_CAPTURE_GAME
            else -> {
                val h = Container.fgCaptureHeightFor(v)
                if (h <= 0) Container.FG_CAPTURE_PANEL else {
                    // The list is matched by height alone: that is all the stored value means.
                    val match = screenSizeEntries.firstOrNull {
                        !it.equals("custom", ignoreCase = true)
                            && Container.fgCaptureHeightFor(StringUtils.parseIdentifier(it)) == h
                    }
                    if (match != null) match else {
                        fgCaptureCustomHeight = h.toString()
                        screenSizeEntries.firstOrNull { it.equals("custom", ignoreCase = true) } ?: "Custom"
                    }
                }
            }
        }
    }
    // NOTE: the power-user performance toggles are intentionally NOT edited here. Their model is
    // global-default (App Settings > Performance, com.winhub.hernandez.perf.PerformanceSettings) +
    // optional per-game override (ShortcutsScreen / in-game drawer) — no container level.
    // FPS limiter on/off (loads the layer); the cap value is set live in-game.
    var fpsLimiterEnabled by mutableStateOf(false)
    // VRR: match the display panel refresh rate to the game's FPS. Default ON (safe — no-op unless
    // the FPS limiter is actually capping).
    var matchRefreshRate by mutableStateOf(true)
    // Manual refresh-rate lock (Hz) used when Auto (matchRefreshRate) is OFF. 0 = none/native.
    var manualRefreshRate by mutableStateOf(0)
    // Ceiling (Hz) on the rates advertised to the GAME via RandR, which is what fills its own
    // in-game refresh dropdown. 0 = no cap. Separate axis from the two above (host panel rate).
    var maxGameRefreshRate by mutableStateOf(0)
    // Guest-side refresh: unlockGameRefreshRate (off = Locked 60) + maxGameRefreshRate (cap; 0 =
    // Unlimited) together back the single "In-game refresh rate" dropdown. Default = unlock + no cap
    // (Unlimited). refreshWasExplicit tracks whether the loaded container had actually set the extra, so
    // an untouched default is NOT persisted (keeps it "default" → no compatible-layer nag at launch).
    var unlockGameRefreshRate by mutableStateOf(true)
    private var refreshWasExplicit = false

    // ReShade multi-effect LOADOUT (Tier 1), per-container default. The per-game shortcut can override.
    // ReshadeLoadoutState holds the ordered effects, per-effect enabled + params, and the solo/stack
    // mode; it serializes to reshadeLoadout + reshadeMode + nested reshadeParams (with legacy migration).
    val reshadeLoadout = ReshadeLoadoutState()
    var reshadeEffects by mutableStateOf<List<com.winhub.hernandez.reshade.ReshadeManager.ReshadeEffect>>(emptyList()); private set

    /** Re-scan the drop-in folder (e.g. after a catalog download) and reconcile the loadout: seed
     *  newly-reflected params, drop effects whose folder vanished, keep current selections/values. */
    fun rescanReshadeEffects() {
        reshadeEffects = com.winhub.hernandez.reshade.ReshadeManager.scanEffects(context)
        reshadeLoadout.reconcile(reshadeEffects)
    }

    // ── Renderer ──────────────────────────────────────────────────────────────
    var rendererEntries by mutableStateOf(emptyList<String>()); private set
    var selectedRenderer by mutableStateOf("Vulkan")
    // False when the device has no hardware Vulkan: the renderer and Frame Generation rows are
    // then hidden, since they can only run on the Vulkan compositor.
    var vulkanSupported by mutableStateOf(true); private set

    var lcAll by mutableStateOf("")
    var lcAllEntries by mutableStateOf(emptyList<String>()); private set

    var enableXInput by mutableStateOf(true)
    var enableDInput by mutableStateOf(false)
    var exclusiveXInput by mutableStateOf(true)

    // Controller vibration (PC-accurate dual-motor rumble), per-container. Mode: 0=Off 1=Controller
    // 2=Device(phone) 3=Both (Container.VIBRATION_MODE_*). Intensity 0..100 scales amplitude. Both
    // are also live-tunable from the in-game drawer — this is just the launch-time default.
    var vibrationMode by mutableStateOf(Container.VIBRATION_MODE_DEFAULT)
    var vibrationIntensity by mutableStateOf(Container.VIBRATION_INTENSITY_DEFAULT)

    // Manual controller->player-slot pins for this container (Player Slots section). Opaque JSON string
    // (descriptor -> slot), the exact schema the in-game Players tab + launch pre-assignment use — the
    // editor UI mutates it only through WinHandler.parse/buildSlotOverridesJson. "{}" = all auto.
    var controllerSlotOverridesJson by mutableStateOf("{}")

    // On-screen-controls vs physical-pad priority for this container (KEEP/YIELD/SHARE). Default KEEP so
    // existing containers are unchanged; a new container is seeded from the app-drawer global at creation.
    var onScreenControllerMode by mutableStateOf(Container.ON_SCREEN_MODE_DEFAULT)

    // Auto-hide on-screen controls when a controller takes the on-screen slot (#333). Container-level
    // default FALSE (existing containers untouched); a new container is seeded from the app-drawer global
    // (default ON) at creation, same discipline as onScreenControllerMode.
    var autoHideControlsOnPad by mutableStateOf(Container.AUTO_HIDE_CONTROLS_ON_PAD_DEFAULT)

    // Gyro (motion aim), per-container. Target: 0=Right stick 1=Left stick 2=Mouse; activator is the
    // button that gates the tilt (4 = always on), with the activation mode deciding whether that
    // button is held or tapped to latch (0=Hold 1=Toggle). Enabled/target/activator/mode/sensitivity/
    // invert are also per-game (shortcut editor) and live-tunable in-game — this is the default a
    // shortcut inherits. Deadzone/smoothing are container-only (hand tremor / latency vs jitter).
    var gyroEnabled by mutableStateOf(Container.GYRO_ENABLED_DEFAULT)
    var gyroTarget by mutableStateOf(Container.GYRO_TARGET_DEFAULT)
    var gyroActivator by mutableStateOf(Container.GYRO_ACTIVATOR_DEFAULT)
    var gyroActivationMode by mutableStateOf(Container.GYRO_ACTIVATION_MODE_DEFAULT)
    // 0=Rate (tilt speed drives the stick) 1=Orientation / "tilt to aim" (the angle held does).
    var gyroMode by mutableStateOf(Container.GYRO_MODE_DEFAULT)
    var gyroSensitivity by mutableStateOf(Container.GYRO_SENSITIVITY_DEFAULT)
    var gyroDeadzone by mutableStateOf(Container.GYRO_DEADZONE_DEFAULT)
    var gyroSmoothing by mutableStateOf(Container.GYRO_SMOOTHING_DEFAULT)
    var gyroInvertX by mutableStateOf(Container.GYRO_INVERT_X_DEFAULT)
    var gyroInvertY by mutableStateOf(Container.GYRO_INVERT_Y_DEFAULT)

    // "Run as administrator" (default ON): ON -> EnableLUA=0 (UAC off / full admin),
    // OFF -> EnableLUA=1. Stored in the container's .wine/system.reg (source of truth); on
    // create it's threaded through the createContainerAsync data flag, on edit it's read/written
    // here directly (mirrors saveMouseWarp, but against system.reg).
    var runAsAdmin by mutableStateOf(true)

    // ── Box64 ─────────────────────────────────────────────────────────────────
    var box64VersionEntries by mutableStateOf(emptyList<String>()); private set
    var selectedBox64Version by mutableStateOf("")
    var box64PresetEntries by mutableStateOf(emptyList<String>()); private set
    var selectedBox64PresetIndex by mutableStateOf(0)
    private var box64PresetIds = emptyList<String>()

    // ── FEXCore (arm64ec only) ────────────────────────────────────────────────
    var fexCoreVersionEntries by mutableStateOf(emptyList<String>()); private set
    var selectedFEXCoreVersion by mutableStateOf(DefaultVersion.FEXCORE)
    var fexCorePresetEntries by mutableStateOf(emptyList<String>()); private set
    var selectedFEXCorePresetIndex by mutableStateOf(0)
    private var fexCorePresetIds = emptyList<String>()

    // ── Startup selection ─────────────────────────────────────────────────────
    var startupSelectionEntries by mutableStateOf(emptyList<String>()); private set
    var selectedStartupSelection by mutableStateOf(Container.STARTUP_SELECTION_ESSENTIAL.toInt())
    // Custom-startup per-service enabled set (raw service names). Only consulted when the selection
    // is Custom (index 3); the toggle list reassigns this set on each flip so recomposition fires.
    var startupServicesEnabled by mutableStateOf(emptySet<String>())

    // ── Wine Config tab ───────────────────────────────────────────────────────
    var desktopThemeIndex by mutableStateOf(0)   // 0=LIGHT, 1=DARK
    var desktopBgTypeIndex by mutableStateOf(0)  // 0=IMAGE, 1=COLOR
    var desktopBgColorInt by mutableStateOf(Color.parseColor("#0277bd"))
    // 0=GLOBAL (shared across all containers), 1=CONTAINER (this container only)
    var desktopWallpaperScopeIndex by mutableStateOf(WineThemeManager.BackgroundScope.GLOBAL.ordinal)
    var mouseWarpEntries by mutableStateOf(emptyList<String>()); private set
    var selectedMouseWarpIndex by mutableStateOf(0)

    // ── Win Components tab ────────────────────────────────────────────────────
    val winComponents = mutableStateListOf<WinComponentEntry>()

    // ── Env Vars tab (managed via AndroidView) ────────────────────────────────
    var envVarsStr by mutableStateOf(Container.DEFAULT_ENV_VARS)

    // ── Drives tab ────────────────────────────────────────────────────────────
    val drives = mutableStateListOf<DriveEntry>()
    // D..Z, the letters a drive may actually use. Counting MAX_DRIVE_LETTERS (26) steps up from
    // 'D' ran three past 'Z' and offered "[:", "\:" and "]:" at the bottom of the dropdown — all
    // selectable, and saved as if they were drive letters.
    val driveLetterOptions: List<String> by lazy {
        ('D'..'Z').map { "$it:" }
    }

    // ── Advanced tab ─────────────────────────────────────────────────────────
    var cpuList by mutableStateOf(Container.getFallbackCPUList())
    var cpuListWoW64 by mutableStateOf(Container.getFallbackCPUListWoW64())

    // ── XR tab ────────────────────────────────────────────────────────────────
    var primaryControllerEntries by mutableStateOf(emptyList<String>()); private set
    var selectedPrimaryController by mutableStateOf(1)
    val xrMappingIndices = mutableStateListOf<Int>() // 10 items: spinner positions (ordinals)
    var xrKeycodeNames by mutableStateOf(emptyList<String>()); private set

    private val xrDefaults = listOf(
        XKeycode.KEY_A.ordinal,
        XKeycode.KEY_B.ordinal,
        XKeycode.KEY_X.ordinal,
        XKeycode.KEY_Y.ordinal,
        XKeycode.KEY_SPACE.ordinal,
        XKeycode.KEY_ENTER.ordinal,
        XKeycode.KEY_UP.ordinal,
        XKeycode.KEY_DOWN.ordinal,
        XKeycode.KEY_LEFT.ordinal,
        XKeycode.KEY_RIGHT.ordinal
    )

    val xrMappingLabels = listOf(
        "Button A", "Button B", "Button X", "Button Y",
        "Button Grip", "Button Trigger",
        "Thumbstick Up", "Thumbstick Down", "Thumbstick Left", "Thumbstick Right"
    )

    // ── Tab selection ─────────────────────────────────────────────────────────
    var selectedTab by mutableStateOf(0)

    // ─────────────────────────────────────────────────────────────────────────
    fun init(containerId: Int) {
        val firstInit = !initialized
        initialized = true
        initializedFor = containerId

        if (firstInit) {
            manager = ContainerManager(context)
            contentsManager = ContentsManager(context)
            contentsManager.syncContents()
        }

        // Reload on EVERY entry - never return early just because this instance last edited the
        // same id. Two things made that wrong: a container created/imported/deleted since the
        // manager was built did not resolve (a real id looked NEW, so Save wrote a duplicate and
        // the original kept its old frame-gen engine), and a reopen after saving elsewhere (the
        // in-game drawer, a shortcut editor, or the launch path's own container object) showed
        // the stale cached value - the editor said LSFG was off right after it had been saved.
        manager.reloadContainers()
        container = if (containerId > 0) manager.getContainerById(containerId) else null
        isEditMode = container != null
        defaultsMode = containerId == EDIT_DEFAULTS_ID
        isSaving = false

        loadStaticResources()
        loadContainerData()
    }

    private fun loadStaticResources() {
        val res = context.resources
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        screenSizeEntries = res.getStringArray(R.array.screen_size_entries).toList()

        // Wine versions (base + downloaded profiles)
        val wineList = res.getStringArray(R.array.wine_entries).toMutableList()
        for (p in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE))
            wineList.add(ContentsManager.getEntryName(p))
        for (p in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON))
            wineList.add(ContentsManager.getEntryName(p))
        wineVersionEntries = wineList

        // Bundled entries + user-imported wrappers (issue #132 Step 2). Built via the SHARED
        // WrapperManager.driverEntries helper so this list and the ShortcutsScreen one can never
        // drift (the dynamic-dropdown drift is the feature's top-ranked risk).
        graphicsDriverEntries = WrapperManager.driverEntries(
            context, res.getStringArray(R.array.graphics_driver_entries)
        )
        dxWrapperEntries  = res.getStringArray(R.array.dxwrapper_entries).toList()
        // (refreshGraphicsDriverEntries below re-reads the wrapper part after an import/delete.)
        audioDriverEntries = res.getStringArray(R.array.audio_driver_entries).toList()
        emulatorEntries   = res.getStringArray(R.array.emulator_entries).toList()
        vulkanSupported = GPUInformation.isVulkanSupported(context)
        rendererEntries = if (vulkanSupported) listOf("Vulkan", "EGL", "SurfaceDirect") else listOf("EGL", "SurfaceDirect")
        lcAllEntries      = res.getStringArray(R.array.some_lc_all).toList()
        startupSelectionEntries = res.getStringArray(R.array.startup_selection_entries).toList()
        mouseWarpEntries  = listOf(
            context.getString(R.string.disable),
            context.getString(R.string.enable),
            context.getString(R.string.force)
        )
        primaryControllerEntries = res.getStringArray(R.array.xr_controllers).toList()
        xrKeycodeNames = XKeycode.values().map { it.name }

        // Box64 presets
        val b64Presets = Box64PresetManager.getPresets("box64", context)
        box64PresetEntries = b64Presets.map { it.name }
        box64PresetIds     = b64Presets.map { it.id }

        // FEXCore presets
        val fexPresets = FEXCorePresetManager.getPresets(context)
        fexCorePresetEntries = fexPresets.map { it.name }
        fexCorePresetIds     = fexPresets.map { it.id }

        // MIDI
        val midiList = mutableListOf("-- ${context.getString(R.string.disabled)} --")
        midiList.add(MidiManager.DEFAULT_SF2_FILE)
        try {
            val sfDir = File(context.filesDir, MidiManager.SF_DIR)
            sfDir.listFiles()?.forEach { midiList.add(it.name) }
        } catch (_: Exception) {}
        midiEntries = midiList
    }

    /** Re-read the Graphics Driver dropdown entries. Call after a wrapper import/delete in the
     *  Wrapper Manager so a freshly-imported wrapper appears WITHOUT reopening the editor (#132). */
    fun refreshGraphicsDriverEntries() {
        graphicsDriverEntries = WrapperManager.driverEntries(
            context, context.resources.getStringArray(R.array.graphics_driver_entries)
        )
    }

    /**
     * A transient, in-memory template container built from the saved "New Container Defaults" profile,
     * or null when no profile is set (or we're editing a real container). It is NEVER persisted — it
     * exists only so loadContainerData() can read the user's saved preferences back through the exact
     * same Container getters a real container uses (round-tripped via loadData, extras included).
     */
    private fun buildTemplateContainer(arch: String): Container? {
        if (container != null) return null
        val json = NewContainerDefaults.load(context, arch) ?: return null
        return runCatching { Container(0, manager).apply { loadData(JSONObject(json)) } }.getOrNull()
    }

    private fun loadContainerData() {
        val c = container
        // Seed source for user-PREFERENCE fields: the real container in edit mode, else the saved
        // defaults template (create/defaults mode with a profile set), else null → built-in
        // Container.DEFAULT_*. When no profile exists `template == null` so `seed == c == null` and
        // this is byte-identical to today's hardcoded-defaults create flow. Identity/per-container/
        // device fields (name, drives, registry-backed mouseWarp/runAsAdmin) stay on the REAL `c`.
        // Resolve the architecture to seed for BEFORE building the template so create-mode pulls the
        // ARCH-MATCHED profile (box64/wowbox64/emulator/FEXCore are arch-coupled). Defaults mode uses
        // the explicit selector; a real new container derives it from its default wine version (the
        // same isArm64EC refreshWineDependent would compute below); edit mode's template is unused.
        val seedArch: String = when {
            defaultsMode -> defaultsArch
            c == null -> if (WineInfo.fromIdentifier(
                    context, contentsManager, wineVersionEntries.firstOrNull() ?: ""
                ).isArm64EC()) NewContainerDefaults.ARCH_ARM64EC else NewContainerDefaults.ARCH_X86_64
            else -> NewContainerDefaults.ARCH_X86_64
        }
        val template = buildTemplateContainer(seedArch)
        val seed = c ?: template

        containerName = if (c != null) c.name else "${context.getString(R.string.container)}-${manager.getNextContainerId()}"
        wineVersionEnabled = !isEditMode

        // Screen size: the real container / saved defaults profile wins; otherwise fit this device's
        // panel shape (1280x720 on 16:9 and wider, 1280x800 on 16:10, 1280x960 on 4:3).
        val ssValue = seed?.screenSize ?: Container.defaultScreenSizeFor(context)
        val ssFound = screenSizeEntries.indexOfFirst {
            StringUtils.parseIdentifier(it).equals(ssValue, ignoreCase = true)
        }
        if (ssFound >= 0) {
            selectedScreenSize = ssValue
        } else {
            selectedScreenSize = "custom"
            val parts = ssValue.split("x")
            customWidth  = parts.getOrElse(0) { "" }
            customHeight = parts.getOrElse(1) { "" }
        }

        // Wine version — NEVER templated (profiles omit it): a new container always picks its wine
        // fresh from the REAL container / first entry, so a saved profile can't force one. In defaults
        // mode the arch comes from the explicit selector (applyArch), not from any wine version.
        selectedWineVersion = c?.wineVersion ?: wineVersionEntries.firstOrNull() ?: ""
        if (defaultsMode) applyArch(defaultsArch == NewContainerDefaults.ARCH_ARM64EC)
        else refreshWineDependent(selectedWineVersion)

        // Arch-DEPENDENT fields (emulator, box64/wowbox64 version + preset, FEXCore version + preset)
        // — seeded from the arch-matched profile via the SAME helper a create-mode wine-version arch
        // flip uses, so initial-load and arch-change can't drift. Runs AFTER applyArch()/
        // refreshWineDependent() has populated box64VersionEntries for the arch.
        seedArchDependentDefaults(seedArch)

        // Graphics driver (load as display name for dropdown)
        selectedGraphicsDriver   = identifierToDisplay(seed?.graphicsDriver ?: defaultGraphicsDriverForNewContainer(), graphicsDriverEntries)
        graphicsDriverConfig     = seed?.graphicsDriverConfig ?: Container.DEFAULT_GRAPHICSDRIVERCONFIG
        rendererNative           = seed?.isRendererNative() ?: false
        rendererPresentMode      = seed?.getRendererPresentMode() ?: "fifo"
        rendererDriverId         = seed?.getRendererDriverId() ?: "system"
        rendererFilterMode       = seed?.getRendererFilterMode() ?: 0
        rendererSwapRB           = seed?.getRendererSwapRB() ?: false
        surfaceDirectPerformanceMode      = seed?.isSurfaceDirectPerformanceMode() ?: true
        surfaceDirectPresentAtRefreshRate = seed?.isSurfaceDirectPresentAtRefreshRate() ?: true
        surfaceDirectBackPressure         = seed?.isSurfaceDirectBackPressure() ?: false
        surfaceDirectPrecisePresentation  = seed?.isSurfaceDirectPrecisePresentation() ?: false
        displayBackend           = Container.DISPLAY_BACKEND_X11
        unrealHdr                = seed?.getUnrealHdr() ?: com.winhub.hernandez.core.UnrealHdr.OFF
        renderScale              = seed?.getExtra("renderScale", "1.0") ?: "1.0"
        autoCloseOnExit          = (seed?.getExtra("autoCloseOnExit", "1") ?: "1") == "1"
        selectedDXWrapper        = identifierToDisplay(seed?.getDXWrapper() ?: Container.DEFAULT_DXWRAPPER, dxWrapperEntries)
        dxWrapperConfig          = seed?.getDXWrapperConfig() ?: Container.DEFAULT_DXWRAPPERCONFIG

        // Audio driver (load as display name). Emulator is arch-dependent → seedArchDependentDefaults.
        selectedAudioDriver = identifierToDisplay(seed?.audioDriver ?: Container.DEFAULT_AUDIO_DRIVER, audioDriverEntries)
        // A container saved with DirectAudio but on (or later moved to) an unsupported layer self-heals
        // to the default here, so the greyed dropdown never shows an unselectable value as "selected".
        coerceAudioDriverForWine()

        // MIDI
        val midiVal = seed?.getMIDISoundFont() ?: ""
        selectedMidiIndex = if (midiVal.isEmpty()) 0
                            else midiEntries.indexOf(midiVal).takeIf { it >= 0 } ?: 0

        showFPS           = seed?.isShowFPS == true
        fpsCounterConfig  = seed?.getFPSCounterConfig() ?: Container.DEFAULT_FPS_COUNTER_CONFIG
        fullscreenMode      = seed?.getFullscreenMode() ?: Container.FULLSCREEN_OFF
        screenAlignment     = seed?.getScreenAlignment() ?: Container.ALIGN_CENTER

        frameGenEngine     = seed?.frameGenEngine ?: "off"
        frameGenModel      = seed?.frameGenModel ?: 0
        lsfgPerformanceMode = seed?.isLsfgPerformanceMode != false   // default ON for new/unset containers
        lsfgAutoEnable      = seed?.isLsfgAutoEnable != false   // default ON for new/unset containers (GameNative parity)
        seedFgCaptureSelection(seed?.fgCaptureResolution)
        lsfgVk11Compat      = seed?.isLsfgVk11Compat == true
        fpsLimiterEnabled  = seed?.isFpsLimiterEnabled == true
        matchRefreshRate   = seed?.isMatchRefreshRate != false   // default ON for new/unset containers
        manualRefreshRate  = seed?.manualRefreshRate ?: 0
        maxGameRefreshRate = seed?.maxGameRefreshRate ?: 0
        unlockGameRefreshRate = seed?.isUnlockGameRefreshRate != false  // default ON for new/unset containers
        refreshWasExplicit = seed?.hasExtra("unlockGameRefreshRate") == true

        // ReShade: scan the drop-in folder, then load the loadout (migrating a legacy single effect).
        reshadeEffects = com.winhub.hernandez.reshade.ReshadeManager.scanEffects(context)
        reshadeLoadout.init(
            reshadeEffects,
            seed?.getReshadeLoadout(), seed?.getReshadeMode(), seed?.getReshadeParams(), seed?.getReshadeEffect()
        )

        // Renderer: Vulkan or EGL. Legacy ids (opengl/surfaceflinger) degrade to the first entry.
        selectedRenderer = identifierToDisplay(seed?.renderer ?: "vulkan", rendererEntries)

        val locale = java.util.Locale.getDefault()
        lcAll = seed?.getLC_ALL() ?: "${locale.language}_${locale.country}.UTF-8"

        // Input type
        val inputType: Int = seed?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE.toInt()
        enableXInput   = (inputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0
        enableDInput   = (inputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0
        exclusiveXInput = seed?.isExclusiveXInput ?: true
        if (!exclusiveXInput) {
            enableXInput = true; enableDInput = true
        }
        vibrationMode      = seed?.getVibrationMode() ?: Container.VIBRATION_MODE_DEFAULT
        vibrationIntensity = seed?.getVibrationIntensity() ?: Container.VIBRATION_INTENSITY_DEFAULT
        controllerSlotOverridesJson = seed?.getControllerSlotOverrides() ?: "{}"
        onScreenControllerMode = seed?.getOnScreenControllerMode() ?: Container.ON_SCREEN_MODE_DEFAULT
        autoHideControlsOnPad = seed?.isAutoHideControlsOnPad() ?: Container.AUTO_HIDE_CONTROLS_ON_PAD_DEFAULT

        gyroEnabled     = seed?.isGyroEnabled() ?: Container.GYRO_ENABLED_DEFAULT
        gyroTarget      = seed?.getGyroTarget() ?: Container.GYRO_TARGET_DEFAULT
        gyroActivator   = seed?.getGyroActivator() ?: Container.GYRO_ACTIVATOR_DEFAULT
        gyroActivationMode = seed?.getGyroActivationMode() ?: Container.GYRO_ACTIVATION_MODE_DEFAULT
        gyroMode        = seed?.getGyroMode() ?: Container.GYRO_MODE_DEFAULT
        gyroSensitivity = seed?.getGyroSensitivity() ?: Container.GYRO_SENSITIVITY_DEFAULT
        gyroDeadzone    = seed?.getGyroDeadzone() ?: Container.GYRO_DEADZONE_DEFAULT
        gyroSmoothing   = seed?.getGyroSmoothing() ?: Container.GYRO_SMOOTHING_DEFAULT
        gyroInvertX     = seed?.isGyroInvertX() ?: Container.GYRO_INVERT_X_DEFAULT
        gyroInvertY     = seed?.isGyroInvertY() ?: Container.GYRO_INVERT_Y_DEFAULT

        // (Box64/FEXCore version + preset are arch-dependent → seeded by seedArchDependentDefaults.)

        // Startup selection
        selectedStartupSelection = (seed?.startupSelection ?: Container.STARTUP_SELECTION_ESSENTIAL).toInt()
        startupServicesEnabled = WineUtils.parseStartupServicesCsv(seed?.startupServices ?: "").toSet()

        // CPU lists
        cpuList      = seed?.getCPUList(true) ?: Container.getFallbackCPUList()
        cpuListWoW64 = seed?.getCPUListWoW64(true) ?: Container.getFallbackCPUListWoW64()

        // Wine Config (desktop theme)
        val themeStr = seed?.desktopTheme ?: WineThemeManager.DEFAULT_DESKTOP_THEME
        val themeInfo = WineThemeManager.ThemeInfo(themeStr)
        desktopThemeIndex   = themeInfo.theme.ordinal
        desktopBgTypeIndex  = themeInfo.backgroundType.ordinal
        desktopBgColorInt   = themeInfo.backgroundColor
        desktopWallpaperScopeIndex = themeInfo.wallpaperScope.ordinal

        // Mouse warp (from registry, only in edit mode)
        if (c != null) {
            val userRegFile = File(c.rootDir, ".wine/user.reg")
            try {
                WineRegistryEditor(userRegFile).use { reg ->
                    val mw = reg.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable")
                    selectedMouseWarpIndex = when (mw.lowercase(Locale.ENGLISH)) {
                        "enable" -> 1
                        "force"  -> 2
                        else     -> 0
                    }
                }
            } catch (_: Exception) {}
        }

        // Run as administrator (from registry, only in edit mode). EnableLUA=0 -> admin (toggle ON),
        // anything else -> UAC on (toggle OFF). Default ON when the value is missing/unreadable.
        if (c != null) {
            val systemRegFile = File(c.rootDir, ".wine/system.reg")
            try {
                WineRegistryEditor(systemRegFile).use { reg ->
                    val enableLUA = reg.getDwordValue(
                        "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", "EnableLUA", 0
                    )
                    runAsAdmin = enableLUA == 0
                }
            } catch (_: Exception) {}
        } else if (template != null) {
            // The template has no on-disk registry; the defaults profile stashes the toggle as a
            // dedicated extra (see saveDefaults) so a new container inherits the user's choice.
            runAsAdmin = template.getExtra("runAsAdminDefault", "1") == "1"
        }

        // Win Components
        loadWinComponents(seed?.winComponents ?: Container.DEFAULT_WINCOMPONENTS)

        // Env vars. Outside defaults mode the sync variables move to the Sync selector: its stored
        // extra wins, else what the env string said (WINEESYNC=0 → wineserver, WINENTSYNC=1 → ntsync,
        // WINEESYNC=1 → esync), else the layer default; then they leave the env string.
        val rawEnv = seed?.envVars ?: Container.DEFAULT_ENV_VARS
        if (defaultsMode) {
            envVarsStr = rawEnv
        } else {
            val stored = SyncSupport.storedMode(seed?.getExtra(SyncMode.EXTRA, ""), rawEnv)
            syncFollowsLayer = stored == null
            syncMode = stored ?: SyncMode.ESYNC
            envVarsStr = SyncSupport.stripSyncVars(rawEnv)
            refreshSyncCaps(selectedWineVersion)
        }

        // Drives
        drives.clear()
        for (entry in Container.drivesIterator(c?.drives ?: defaultDrivesForNewContainer())) {
            drives.add(DriveEntry(letter = entry[0], path = entry[1]))
        }

        // XR
        selectedPrimaryController = seed?.primaryController ?: 1
        val xcodes = XKeycode.values()
        val xrMappings = Container.XrControllerMapping.values()
        xrMappingIndices.clear()
        for ((i, mapping) in xrMappings.withIndex()) {
            val defaultOrdinal = xrDefaults.getOrElse(i) { 0 }
            val idx = if (seed != null) {
                val byteId = seed.getControllerMapping(mapping)
                xcodes.indexOfFirst { it.id == byteId }.takeIf { it >= 0 } ?: defaultOrdinal
            } else {
                defaultOrdinal
            }
            xrMappingIndices.add(idx)
        }
    }

    private fun refreshWineDependent(wineVersion: String) {
        val wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion)
        applyArch(wineInfo.isArm64EC())
    }

    /**
     * Seed the ARCH-DEPENDENT fields — emulator, box64/wowbox64 version + preset, FEXCore version +
     * preset — from the [arch]-matched defaults profile (the transient template for that arch, or the
     * real container in edit mode, else the built-in arch defaults). MUST run AFTER applyArch()/
     * refreshWineDependent() has populated box64VersionEntries for [arch].
     *
     * This is the SINGLE code path shared by the initial load and a create-mode wine-version arch flip
     * (onWineVersionChanged), so the two can never drift. Arch-AGNOSTIC fields (screen size, renderer,
     * dxwrapper, env vars, …) are deliberately NOT touched here — a wine change must not clobber edits
     * the user already made to them.
     */
    private fun seedArchDependentDefaults(arch: String) {
        // Same seed semantics as loadContainerData: real container in edit mode, else the arch-matched
        // template (null when no profile → built-in arch defaults below).
        val archSeed = container ?: buildTemplateContainer(arch)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        // Emulator (arm64ec only; identifierToDisplay degrades an unknown id to entry 0).
        selectedEmulator = identifierToDisplay(archSeed?.emulator ?: Container.DEFAULT_EMULATOR, emulatorEntries)

        // Box64/WOWBox64 version — honor the profile's saved value only when it's valid for THIS arch's
        // list; otherwise leave applyArch()'s firstOrNull() default in place.
        archSeed?.box64Version
            ?.takeIf { it.isNotEmpty() && box64VersionEntries.contains(it) }
            ?.let { selectedBox64Version = it }

        // Box64 preset (preset list itself is arch-agnostic; only the saved selection differs).
        val b64Preset = archSeed?.box64Preset ?: prefs.getString("box64_preset", Box64Preset.COMPATIBILITY) ?: Box64Preset.COMPATIBILITY
        selectedBox64PresetIndex = box64PresetIds.indexOf(b64Preset).takeIf { it >= 0 } ?: 0

        // FEXCore version (list is arch-agnostic, but the profile's saved value is arch-specific).
        loadFEXCoreVersions()
        selectedFEXCoreVersion = archSeed?.getFEXCoreVersion() ?: DefaultVersion.FEXCORE

        // FEXCore preset.
        val fexPreset = archSeed?.getFEXCorePreset() ?: prefs.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE) ?: FEXCorePreset.INTERMEDIATE
        selectedFEXCorePresetIndex = fexCorePresetIds.indexOf(fexPreset).takeIf { it >= 0 } ?: 0
    }

    // The arch-dependent slice of the form: emulator gate + the box64/wowbox64 version list & its
    // reset. Split out of refreshWineDependent so defaults mode can drive the arch straight from its
    // ARCHITECTURE selector (applyArch(defaultsArch == ARM64EC)) with no wine version involved, while
    // create/edit still derive it from the wine version via refreshWineDependent.
    private fun applyArch(arm64ec: Boolean) {
        isArm64EC    = arm64ec
        emulatorEnabled = isArm64EC

        // Box64 versions
        val b64Array = if (isArm64EC)
            context.resources.getStringArray(R.array.wowbox64_version_entries).toMutableList()
        else
            context.resources.getStringArray(R.array.box64_version_entries).toMutableList()

        val b64Type = if (isArm64EC) ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                      else           ContentProfile.ContentType.CONTENT_TYPE_BOX64
        for (p in contentsManager.getProfiles(b64Type)) {
            val name = ContentsManager.getEntryName(p)
            val dash = name.indexOf('-')
            b64Array.add(name.substring(dash + 1))
        }
        box64VersionEntries = b64Array
        selectedBox64Version = box64VersionEntries.firstOrNull() ?: ""
    }

    private fun loadFEXCoreVersions() {
        val list = context.resources.getStringArray(R.array.fexcore_version_entries).toMutableList()
        for (p in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)) {
            val name = ContentsManager.getEntryName(p)
            val dash = name.indexOf('-')
            list.add(name.substring(dash + 1))
        }
        fexCoreVersionEntries = list
    }

    private fun loadWinComponents(wincomponents: String) {
        winComponents.clear()
        val res = context.resources
        for (parts in com.winhub.hernandez.core.KeyValueSet(wincomponents)) {
            val key   = parts[0]
            val idx   = parts[1].toIntOrNull() ?: 0
            val resId = res.getIdentifier(key, "string", context.packageName)
            val label = if (resId != 0) res.getString(resId) else key
            winComponents.add(WinComponentEntry(key, idx, label))
        }
    }

    /** Finds the display name in [entries] whose identifier matches [id]. Falls back to first entry. */
    private fun identifierToDisplay(id: String, entries: List<String>): String =
        entries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: entries.firstOrNull()
            ?: id

    /**
     * DirectAudio only loads on the arm64ec Proton builds in DirectAudioSupport.SUPPORTED_BUILD_TOKENS
     * (7 as of driver v1.3.2); on any other layer it does
     * nothing / breaks audio. So it must never survive as the chosen driver on an unsupported layer:
     * if the currently-selected driver is DirectAudio but [selectedWineVersion] isn't one of those
     * builds, fall back to the app default (PulseAudio). Called on load, on a Wine-version change, and
     * again at save — the UI grey-out stops a fresh pick, this stops an already-set one from persisting.
     */
    private fun coerceAudioDriverForWine() {
        if (StringUtils.parseIdentifier(selectedAudioDriver) == "directaudio" &&
            !com.winhub.hernandez.core.DirectAudioSupport.isSupported(selectedWineVersion)) {
            selectedAudioDriver = identifierToDisplay(Container.DEFAULT_AUDIO_DRIVER, audioDriverEntries)
        }
    }

    fun onWineVersionChanged(version: String) {
        val wasArm64 = isArm64EC
        selectedWineVersion = version
        coerceAudioDriverForWine()      // a switch to an unsupported layer drops a stale DirectAudio pick
        refreshWineDependent(version)   // updates isArm64EC + swaps the box64/wowbox64 list
        refreshSyncCaps(version)        // re-grey the Sync options; an unavailable pick falls back

        // CREATE mode only: a wine change can FLIP the architecture. applyArch() swapped the box64 list
        // and reset its selection but did NOT re-seed the arch-dependent fields, so without this they'd
        // keep the OLD arch's profile values (box64 reset to default, FEXCore stale). Re-seed them from
        // the NEW arch's profile. Edit mode: never (real container). Defaults mode: arch is driven by
        // the selector via setDefaultsArch/loadContainerData, not here. Arch-agnostic fields untouched.
        if (container == null && !defaultsMode && wasArm64 != isArm64EC) {
            val arch = if (isArm64EC) NewContainerDefaults.ARCH_ARM64EC else NewContainerDefaults.ARCH_X86_64
            seedArchDependentDefaults(arch)
        }
    }

    /**
     * Defaults mode: switch which architecture's profile the form is editing. Reloads the WHOLE form
     * from THAT arch's saved profile (or its built-in defaults if unset) — loadContainerData rebuilds
     * the template from NewContainerDefaults.load(context, defaultsArch) and applies applyArch() — so
     * flipping the selector shows exactly the other arch's saved defaults.
     */
    fun selectDefaultsArch(arch: String) {
        if (arch == defaultsArch) return
        defaultsArch = arch
        loadContainerData()
    }

    fun refreshWineVersions() {
        contentsManager.syncContents()
        val res = context.resources
        val wineList = res.getStringArray(R.array.wine_entries).toMutableList()
        for (p in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE))
            wineList.add(ContentsManager.getEntryName(p))
        for (p in contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON))
            wineList.add(ContentsManager.getEntryName(p))
        wineVersionEntries = wineList
    }

    fun refreshBox64Versions() {
        contentsManager.syncContents()
        refreshWineDependent(selectedWineVersion)
    }

    fun refreshFEXCoreVersions() {
        contentsManager.syncContents()
        loadFEXCoreVersions()
    }

    fun onExclusiveXInputChanged(checked: Boolean) {
        exclusiveXInput = checked
        if (!checked) {
            enableXInput = true; enableDInput = true
        } else {
            if (enableXInput && enableDInput) enableDInput = false
        }
    }

    // ── Confirm ───────────────────────────────────────────────────────────────
    fun confirm(
        resolvedGraphicsDriverConfig: String,
        resolvedDXWrapperConfig: String,
        resolvedFPSCounterConfig: String,
        resolvedEnvVars: String,
        resolvedCPUList: String,
        resolvedCPUListWoW64: String,
        resolvedColorAsString: String,
        onDone: () -> Unit
    ) {
        // Defaults mode: the ✓ saves the field state as the user's new-container defaults profile
        // instead of creating a container (no Wine gate — a template can be saved before any Wine is
        // installed; the gate below still protects real create/edit).
        if (defaultsMode) {
            saveDefaults(
                resolvedGraphicsDriverConfig,
                resolvedDXWrapperConfig,
                resolvedFPSCounterConfig,
                resolvedEnvVars,
                resolvedCPUList,
                resolvedCPUListWoW64,
                resolvedColorAsString,
                onDone,
            )
            return
        }
        // No Wine/Proton ships in the APK any more, so on a fresh install this list is empty until
        // the user installs one from the in-app catalog. Saving anyway would write wineVersion="",
        // which WineInfo.fromIdentifier can only resolve to a non-existent fallback path — i.e. a
        // container that looks fine and dies at launch. Refuse with a message instead.
        if (selectedWineVersion.isBlank()) {
            AppUtils.showToast(context, R.string.no_wine_version_installed)
            onDone()
            return
        }
        isSaving = true
        PreloaderState.show(context.getString(R.string.creating_container))
        val cleanup = {
            PreloaderState.hide()
            isSaving = false
            onDone()
        }
        viewModelScope.launch(Dispatchers.Main) {
            doConfirm(
                resolvedGraphicsDriverConfig,
                resolvedDXWrapperConfig,
                resolvedFPSCounterConfig,
                resolvedEnvVars,
                resolvedCPUList,
                resolvedCPUListWoW64,
                resolvedColorAsString,
                onComplete = cleanup
            )
        }
    }

    private fun doConfirm(
        gdConfig: String,
        dxConfig: String,
        fpsConfig: String,
        envVarsIn: String,
        cpuListIn: String,
        cpuListWoW64In: String,
        colorAsString: String,
        onComplete: () -> Unit
    ) {
        val c = container
        if (c != null) {
            // Edit mode — the form is written straight onto the real container.
            applyFormTo(c, gdConfig, dxConfig, fpsConfig, envVarsIn, cpuListIn, cpuListWoW64In,
                colorAsString, seedControllerGlobals = false)
            val saved = c.saveData()
            saveMouseWarp(c)
            saveRunAsAdmin(c)
            // saveData() used to swallow a failed disk write, so a container whose config could not
            // be written looked like it saved and then silently reverted on reopen. Surface it.
            AppUtils.showToast(context, if (saved) R.string.container_saved else R.string.container_save_failed)
            onComplete()
        }
        else {
            // Create mode — the SAME writer fills a throwaway container, whose serialized form is
            // the payload createContainerAsync consumes (see buildCreateData). Nothing is re-applied
            // afterwards, so nothing can be forgotten afterwards either.
            val data = buildCreateData(gdConfig, dxConfig, fpsConfig, envVarsIn, cpuListIn,
                cpuListWoW64In, colorAsString)
            // createContainerAsync posts callback to main thread when done
            manager.createContainerAsync(data, contentsManager) { created ->
                container = created
                // Registry-backed settings need the prefix to exist, so they land after extraction.
                // (Run as administrator is stamped by ContainerManager from the runAsAdmin flag.)
                if (created != null) saveMouseWarp(created)
                onComplete()
            }
        }
    }

    /**
     * Write EVERY field this editor can set onto [c]. The ONE place the form becomes container
     * config: the edit path calls it on the real container, the create path on the throwaway
     * container it serializes into createContainerAsync's payload, and the "New Container Defaults"
     * path on the profile template. A field added here therefore sticks in all three.
     *
     * It exists because those three used to be hand-maintained lists that drifted: create put a
     * subset in its JSON, re-applied a second subset through setters once the container existed, and
     * silently dropped whatever was in neither.
     *
     * NOT here: mouse warp and "Run as administrator". Both live in the prefix's registry rather than
     * the config, so they need a container that exists on disk — saveMouseWarp/saveRunAsAdmin on
     * edit, and on create the registry stamp ContainerManager applies from the runAsAdmin data flag.
     *
     * [seedControllerGlobals] is create-only: a BRAND-NEW container inherits the app-drawer
     * controller globals for each of those three fields the user left at its default (existing
     * containers are never retroactively changed, and an explicit edit here always wins).
     */
    private fun applyFormTo(
        c: Container,
        gdConfig: String,
        dxConfig: String,
        fpsConfig: String,
        envVarsIn: String,
        cpuListIn: String,
        cpuListWoW64In: String,
        colorAsString: String,
        seedControllerGlobals: Boolean,
    ) {
        // Belt-and-suspenders: never write Audio=directaudio for a layer that can't load it. The UI
        // grey-out already blocks a fresh pick, but a container loaded already-set (or edited without
        // touching the audio row) reaches here — drop it back to the default first.
        coerceAudioDriverForWine()

        // Finalize graphics driver config (ensure version is set)
        var finalGDConfig = gdConfig
        try {
            val cfg = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(gdConfig)
            if (cfg["version"].isNullOrEmpty()) {
                cfg["version"] = if (GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context))
                    DefaultVersion.WRAPPER_ADRENO else DefaultVersion.WRAPPER
                finalGDConfig = GraphicsDriverConfigDialog.toGraphicsDriverConfig(cfg)
            }
        } catch (_: Exception) {}

        var inputType = 0
        if (enableXInput) inputType = inputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        if (enableDInput) inputType = inputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()

        c.name               = containerName
        c.screenSize         = buildScreenSize()
        // Sync: the selector's pick is stored and the env string no longer carries sync variables
        // (defaults mode has no layer to judge against, so it leaves both as they were).
        if (defaultsMode) {
            c.envVars        = envVarsIn
        } else {
            c.envVars        = SyncSupport.stripSyncVars(envVarsIn)
            c.putExtra(SyncMode.EXTRA, syncCaps?.resolve(syncMode) ?: syncMode)
        }
        c.setCPUList(cpuListIn)
        c.setCPUListWoW64(cpuListWoW64In)
        c.graphicsDriver     = StringUtils.parseIdentifier(selectedGraphicsDriver)
        c.graphicsDriverConfig = finalGDConfig
        c.setDXWrapper(StringUtils.parseIdentifier(selectedDXWrapper))
        c.setDXWrapperConfig(dxConfig)
        c.audioDriver        = StringUtils.parseIdentifier(selectedAudioDriver)
        c.emulator           = StringUtils.parseIdentifier(selectedEmulator)
        c.winComponents      = winComponents.joinToString(",") { "${it.key}=${it.selectedIndex}" }
        c.drives             = buildDrivesString()
        c.setShowFPS(showFPS)
        c.setFPSCounterConfig(fpsConfig)
        c.setFullscreenMode(fullscreenMode)
        c.setScreenAlignment(screenAlignment)
        c.setFrameGenEngine(frameGenEngine)
        c.setFrameGenModel(frameGenModel)
        c.setLsfgPerformanceMode(lsfgPerformanceMode)
        c.setLsfgAutoEnable(lsfgAutoEnable)
        if (com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED) {
            c.setFgCaptureResolution(resolvedFgCaptureResolution())
            c.setLsfgVk11Compat(lsfgVk11Compat)
        }
        c.setFpsLimiterEnabled(fpsLimiterEnabled)
        c.setMatchRefreshRate(matchRefreshRate)
        c.setManualRefreshRate(manualRefreshRate)
        applyRefreshSettings(c)
        c.setReshadeLoadout(reshadeLoadout.loadoutJsonOrNull())
        c.setReshadeMode(reshadeLoadout.mode)
        c.setReshadeParams(reshadeLoadout.paramsJsonOrNull())
        c.setReshadeEffect(reshadeLoadout.firstEffectName())
        c.setExclusiveXInput(exclusiveXInput)
        c.setVibrationMode(vibrationMode)
        c.setVibrationIntensity(vibrationIntensity)
        // Player Slots + On-screen mode + auto-hide: seeded from the app-drawer global ONLY for a
        // brand-new container, and only where the field is still the default the user didn't touch.
        c.setControllerSlotOverrides(
            if (seedControllerGlobals &&
                (controllerSlotOverridesJson.isBlank() || controllerSlotOverridesJson == "{}"))
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSlotOverridesJson(context)
            else controllerSlotOverridesJson
        )
        c.setOnScreenControllerMode(
            if (seedControllerGlobals && onScreenControllerMode == Container.ON_SCREEN_MODE_DEFAULT)
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.getOnScreenMode(context)
            else onScreenControllerMode
        )
        c.setAutoHideControlsOnPad(
            if (seedControllerGlobals && !autoHideControlsOnPad)
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.getAutoHideControlsOnPad(context)
            else autoHideControlsOnPad
        )
        c.setGyroEnabled(gyroEnabled)
        c.setGyroTarget(gyroTarget)
        c.setGyroActivator(gyroActivator)
        c.setGyroActivationMode(gyroActivationMode)
        c.setGyroMode(gyroMode)
        c.setGyroSensitivity(gyroSensitivity)
        c.setGyroDeadzone(gyroDeadzone)
        c.setGyroSmoothing(gyroSmoothing)
        c.setGyroInvertX(gyroInvertX)
        c.setGyroInvertY(gyroInvertY)
        c.setRenderer(StringUtils.parseIdentifier(selectedRenderer))
        c.setRendererNative(rendererNative)
        c.setRendererPresentMode(rendererPresentMode)
        c.setRendererDriverId(rendererDriverId)
        c.setRendererFilterMode(rendererFilterMode)
        c.setRendererSwapRB(rendererSwapRB)
        c.setSurfaceDirectPerformanceMode(surfaceDirectPerformanceMode)
        c.setSurfaceDirectPresentAtRefreshRate(surfaceDirectPresentAtRefreshRate)
        c.setSurfaceDirectBackPressure(surfaceDirectBackPressure)
        c.setSurfaceDirectPrecisePresentation(surfaceDirectPrecisePresentation)
        c.setDisplayBackend(Container.DISPLAY_BACKEND_X11)
        c.setUnrealHdr(unrealHdr)                   // off clears the extra
        c.putExtra("renderScale", if (renderScale == "1.0") null else renderScale)
        c.putExtra("autoCloseOnExit", if (autoCloseOnExit) null else "0")  // default ON
        c.setInputType(inputType)
        c.setStartupSelection(selectedStartupSelection.toByte())
        // Persist the Custom enabled set regardless of the active selection, so toggling to another
        // preset and back restores the picks. Launch only reads it when the selection is Custom.
        c.setStartupServices(startupServicesEnabled.joinToString(","))
        c.setBox64Version(selectedBox64Version)
        c.setBox64Preset(box64PresetIds.getOrElse(selectedBox64PresetIndex) { Box64Preset.COMPATIBILITY })
        c.setFEXCoreVersion(selectedFEXCoreVersion)
        c.setFEXCorePreset(fexCorePresetIds.getOrElse(selectedFEXCorePresetIndex) { FEXCorePreset.INTERMEDIATE })
        c.desktopTheme       = buildDesktopThemeStr(colorAsString)
        c.setMidiSoundFont(if (selectedMidiIndex == 0) "" else midiEntries.getOrElse(selectedMidiIndex) { "" })
        c.setLC_ALL(lcAll)
        c.setPrimaryController(selectedPrimaryController)
        c.setControllerMapping(buildControllerMapping())
    }

    // The create-mode container-config `data` JSON — built by writing the WHOLE form onto a
    // throwaway container (never touches disk) and serializing it with the SAME getData() a real
    // save uses. Container.loadData round-trips getData() in full, extraData included — the same
    // property duplicateContainer relies on — so every field the editor can set reaches the new
    // container, instead of the hand-picked subset this used to list.
    //
    // Two keys getData() cannot carry are added by hand:
    //   • wineVersion — getData() omits it for the bundled main wine, and createContainer REQUIRES
    //     the key (data.getString), so it is always written here.
    //   • runAsAdmin  — a registry stamp rather than a config field; ContainerManager reads the flag
    //     off the payload and writes EnableLUA into the freshly-extracted prefix.
    private fun buildCreateData(
        gdConfig: String, dxConfig: String, fpsConfig: String, envVarsIn: String,
        cpuListIn: String, cpuListWoW64In: String, colorAsString: String,
    ): JSONObject {
        val template = Container(0, manager)
        applyFormTo(template, gdConfig, dxConfig, fpsConfig, envVarsIn, cpuListIn, cpuListWoW64In,
            colorAsString, seedControllerGlobals = true)
        return template.getData().apply {
            put("wineVersion", selectedWineVersion)
            put("runAsAdmin", runAsAdmin)
        }
    }

    // Defaults mode ✓: write the form onto a throwaway template through the SAME applyFormTo a real
    // save uses, then persist its serialized form (minus the per-container name/drives and the
    // never-templated wineVersion) as the user's new-container defaults profile. Never creates a
    // container, and never seeds the controller globals — those belong to a real new container.
    private fun saveDefaults(
        gdConfig: String, dxConfig: String, fpsConfig: String, envVarsIn: String,
        cpuListIn: String, cpuListWoW64In: String, colorAsString: String, onDone: () -> Unit,
    ) {
        try {
            val template = Container(0, manager)
            applyFormTo(template, gdConfig, dxConfig, fpsConfig, envVarsIn, cpuListIn,
                cpuListWoW64In, colorAsString, seedControllerGlobals = false)
            // runAsAdmin is a registry stamp for real containers (not a config field), so getData()
            // won't carry it. Stash it as a dedicated profile-only extra so loadContainerData can seed
            // a new container's toggle from the saved default (see the template branch there).
            template.putExtra("runAsAdminDefault", if (runAsAdmin) "1" else "0")

            val profile = template.getData()
            // name + drives are per-container and wineVersion is never templated (a new container
            // picks its wine fresh); id is meaningless for a template. Strip all four. The profile is
            // saved under the arch currently being edited (defaultsArch).
            profile.remove("id")
            profile.remove("name")
            profile.remove("drives")
            profile.remove("wineVersion")
            NewContainerDefaults.save(context, defaultsArch, profile.toString())
            AppUtils.showToast(context, R.string.new_container_defaults_saved)
        } catch (e: Exception) {
            AppUtils.showToast(context, R.string.new_container_defaults_save_failed)
        }
        onDone()
    }

    /** Reset mode ✓: forget the CURRENT arch's profile and reload the form to its built-in defaults. */
    fun resetDefaults() {
        NewContainerDefaults.clear(context, defaultsArch)
        loadContainerData()   // template is now null → every field falls back to Container.DEFAULT_*
        AppUtils.showToast(context, R.string.reset_to_app_defaults)
    }

    private fun buildScreenSize(): String {
        if (selectedScreenSize.equals("custom", ignoreCase = true)) {
            val w = customWidth.trim()
            val h = customHeight.trim()
            if (w.matches(Regex("[0-9]+")) && h.matches(Regex("[0-9]+"))) {
                val wi = w.toInt(); val hi = h.toInt()
                if (wi % 2 == 0 && hi % 2 == 0) return "${wi}x${hi}"
            }
            return Container.defaultScreenSizeFor(context)
        }
        return StringUtils.parseIdentifier(selectedScreenSize)
    }

    /**
     * The presumptive container id used for the per-container wallpaper path. In edit mode this
     * is the real container id; in create mode the container doesn't exist yet, so we use the id
     * the manager will hand out next (same value used for the default container name at :277).
     */
    private fun effectiveContainerId(): Int = container?.id ?: manager.getNextContainerId()

    /** Global vs per-container wallpaper file for the given scope. */
    fun wallpaperFileFor(scope: WineThemeManager.BackgroundScope): File =
        if (scope == WineThemeManager.BackgroundScope.CONTAINER)
            WineThemeManager.getUserWallpaperFile(context, effectiveContainerId())
        else
            WineThemeManager.getUserWallpaperFile(context)

    private fun buildDesktopThemeStr(colorAsString: String): String {
        val theme   = WineThemeManager.Theme.values()[desktopThemeIndex]
        val bgType  = WineThemeManager.BackgroundType.values()[desktopBgTypeIndex]
        var str = "${theme},${bgType},$colorAsString"
        if (bgType == WineThemeManager.BackgroundType.IMAGE) {
            // Format: theme,bgType,color,SCOPE,mtime — SCOPE makes the launch path pick the right
            // file; mtime is a cache-bust so overwriting the chosen wallpaper regenerates the BMP.
            val scope = WineThemeManager.BackgroundScope.values()[desktopWallpaperScopeIndex]
            val wallpaper = wallpaperFileFor(scope)
            str += ",$scope," + if (wallpaper.isFile) wallpaper.lastModified() else "0"
        }
        return str
    }

    private fun buildDrivesString(): String =
        drives.filter { it.path.isNotBlank() }.joinToString("") { "${it.letter}:${it.path}" }

    private fun buildControllerMapping(): String {
        val xcodes = XKeycode.values()
        val bytes = ByteArray(xrMappingIndices.size) { i ->
            xcodes.getOrElse(xrMappingIndices.getOrElse(i) { 0 }) { xcodes[0] }.id
        }
        return String(bytes)
    }

    private fun saveMouseWarp(c: Container) {
        val userRegFile = File(c.rootDir, ".wine/user.reg")
        if (!userRegFile.exists()) return
        try {
            WineRegistryEditor(userRegFile).use { reg ->
                val value = when (selectedMouseWarpIndex) {
                    1    -> "enable"
                    2    -> "force"
                    else -> "disable"
                }
                reg.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", value)
            }
        } catch (_: Exception) {}
    }

    // Run as administrator: ON -> EnableLUA=0 (UAC off / full admin), OFF -> EnableLUA=1. Written
    // to the container's system.reg (source of truth). Mirrors saveMouseWarp; only used on edit —
    // the create path stamps EnableLUA via the createContainerAsync "runAsAdmin" data flag.
    private fun saveRunAsAdmin(c: Container) {
        val systemRegFile = File(c.rootDir, ".wine/system.reg")
        if (!systemRegFile.exists()) return
        try {
            WineRegistryEditor(systemRegFile).use { reg ->
                reg.setCreateKeyIfNotExist(true)
                reg.setDwordValue(
                    "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System",
                    "EnableLUA", if (runAsAdmin) 0 else 1
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Drive letters assigned to more than one drive. Two drives sharing a letter collide in the
     * container, so the editor flags them and saving is blocked until they are resolved.
     */
    val duplicateDriveLetters: Set<String>
        get() = drives.groupingBy { it.letter }.eachCount().filterValues { it > 1 }.keys

    /**
     * Default graphics driver for a NEW container: the GameNative wrapper on non-Adreno GPUs.
     *
     * `Container.DEFAULT_GRAPHICS_DRIVER` is plain "wrapper", which targets Adreno/Turnip. On Mali,
     * Xclipse and PowerVR that hands a brand-new container a driver never built for it;
     * wrapper-gamenative is the one that covers those parts. Ported from GameNative PR #1736.
     *
     * Falls back to the plain default when wrapper-gamenative is not among the installed driver
     * entries — [identifierToDisplay] silently degrades an unknown id to entry 0, which on a Mali
     * device would land somewhere arbitrary rather than on "wrapper".
     *
     * Same reasoning as [defaultDrivesForNewContainer] for computing it here: the static constant
     * has no Context and initialises a field on EVERY Container loaded from disk, where a saved
     * graphicsDriver overwrites it anyway. This runs only when the editor opens with no container.
     */
    private fun defaultGraphicsDriverForNewContainer(): String {
        if (GPUInformation.isAdrenoGPU(context)) return Container.DEFAULT_GRAPHICS_DRIVER
        val installed = graphicsDriverEntries.any {
            StringUtils.parseIdentifier(it) == Container.GRAPHICS_DRIVER_GAMENATIVE
        }
        return if (installed) Container.GRAPHICS_DRIVER_GAMENATIVE else Container.DEFAULT_GRAPHICS_DRIVER
    }

    /**
     * Default drives for a NEW container: one per storage volume, instead of internal-only.
     *
     * `Container.DEFAULT_DRIVES` covers internal storage and Downloads, so internal games all sit
     * inside F: and share it. Nothing covered an SD card or a USB stick, so every game on one had
     * to claim its own letter and ~24 of them exhausted the alphabet. Giving each mounted volume a
     * drive up front means games on removable storage reuse one the same way internal games do.
     *
     * Deliberately computed here rather than in `Container.DEFAULT_DRIVES`: that is a static
     * constant with no Context, and it initialises a field on EVERY Container object — including
     * each one loaded from disk, where the value is immediately overwritten by saved JSON. This
     * runs once, only when the editor opens with no container (i.e. creating one).
     */
    private fun defaultDrivesForNewContainer(): String {
        // Internal and Downloads come from Environment, not StorageRoots: they are fixed, and
        // StorageRoots deliberately degrades an unreadable volume to the deepest directory it can
        // list, which would silently point E: at an app-specific subfolder.
        val internal = Environment.getExternalStorageDirectory().absolutePath
        val downloads = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

        val sb = StringBuilder()
        sb.append("D:").append(downloads)
        sb.append("E:").append(internal)

        // F: onward for removable volumes — an SD card and a USB stick can be mounted at once.
        // Only genuine volume ROOTS are pre-declared; a degraded StorageRoots entry pointing part
        // way down the volume would be worse than nothing, and Part B covers that case on import
        // anyway. Z: is reserved (container filesystem root), so stop before it.
        val removableRoots = runCatching { StorageRoots.list(context) }.getOrNull().orEmpty()
            .filter { it.removable }
            .map { it.dir.absolutePath }
            .filter { WinePath.storageVolumeRootOf(it) == it }
            .distinct()
        var letter = 'F'
        for (path in removableRoots) {
            if (letter >= 'Z') break
            sb.append(letter).append(':').append(path)
            letter += 1
        }
        return sb.toString()
    }

    fun addDrive() {
        if (drives.size >= driveLetterOptions.size) return
        // Take the first UNUSED letter. Indexing by drives.size hands out a letter that an existing
        // drive already holds whenever the assigned letters are not the first N in order.
        val used = drives.mapTo(HashSet()) { it.letter }
        val letter = driveLetterOptions
            .map { it.trimEnd(':') }
            .firstOrNull { it !in used }
            ?: return
        drives.add(DriveEntry(letter = letter, path = ""))
    }

    fun removeDrive(uid: Long) {
        drives.removeAll { it.uid == uid }
    }

    fun updateDriveLetter(uid: Long, letter: String) {
        drives.indexOfFirst { it.uid == uid }.takeIf { it >= 0 }?.let { i ->
            drives[i] = drives[i].copy(letter = letter)
        }
    }

    fun updateDrivePath(uid: Long, path: String) {
        drives.indexOfFirst { it.uid == uid }.takeIf { it >= 0 }?.let { i ->
            drives[i] = drives[i].copy(path = path)
        }
    }

    // ── Preset ids, for the editor row ──────────────────────────────────────────────────────
    // The dropdowns work in display NAMES, but everything about editing a preset - which one to
    // open, where its values are stored, whether it carries a local copy - is keyed by ID. These
    // expose the selection as an id without making the id lists writable from outside.

    /** Id of the Box64 preset currently selected, or "" when the list has not loaded. */
    val selectedBox64PresetId: String
        get() = box64PresetIds.getOrElse(selectedBox64PresetIndex) { "" }

    /** Id of the FEXCore preset currently selected, or "" when the list has not loaded. */
    val selectedFEXCorePresetId: String
        get() = fexCorePresetIds.getOrElse(selectedFEXCorePresetIndex) { "" }

    fun selectBox64PresetById(id: String) {
        box64PresetIds.indexOf(id).takeIf { it >= 0 }?.let { selectedBox64PresetIndex = it }
    }

    fun selectFEXCorePresetById(id: String) {
        fexCorePresetIds.indexOf(id).takeIf { it >= 0 }?.let { selectedFEXCorePresetIndex = it }
    }

    /**
     * Re-read both preset lists after one is added, duplicated, removed or imported, keeping the
     * current selection on the same preset where it still exists (the index would otherwise point
     * at a different preset, or off the end).
     */
    fun reloadPresetLists(context: Context) {
        val keepB64 = selectedBox64PresetId
        val keepFex = selectedFEXCorePresetId

        val b64 = Box64PresetManager.getPresets("box64", context)
        box64PresetEntries = b64.map { it.name }
        box64PresetIds = b64.map { it.id }
        selectedBox64PresetIndex = box64PresetIds.indexOf(keepB64).takeIf { it >= 0 } ?: 0

        val fex = FEXCorePresetManager.getPresets(context)
        fexCorePresetEntries = fex.map { it.name }
        fexCorePresetIds = fex.map { it.id }
        selectedFEXCorePresetIndex = fexCorePresetIds.indexOf(keepFex).takeIf { it >= 0 } ?: 0
    }

}
