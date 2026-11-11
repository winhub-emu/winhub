package com.winhub.hernandez.ui

import android.graphics.Bitmap
import com.winhub.hernandez.inputcontrols.StickShaper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object XServerDialogState {

    enum class ActiveDialog {
        NONE, VIBRATION, DEBUG, INPUT_CONTROLS, ACTIVE_WINDOWS, NEW_TASK, CAST,
        CONTROLLER_TEST
    }

    // -------------------------------------------------------------------------
    // Wireless cast — in-app device picker (Google Cast devices via mDNS).
    // -------------------------------------------------------------------------
    enum class CastStatus { IDLE, CONNECTING, CONNECTED, FAILED }

    private val _castDevices = MutableStateFlow<List<com.winhub.hernandez.cast.CastDiscovery.Device>>(emptyList())
    val castDevices: StateFlow<List<com.winhub.hernandez.cast.CastDiscovery.Device>> = _castDevices
    fun setCastDevices(v: List<com.winhub.hernandez.cast.CastDiscovery.Device>) { _castDevices.value = v }

    private val _castScanning = MutableStateFlow(false)
    val castScanning: StateFlow<Boolean> = _castScanning
    fun setCastScanning(v: Boolean) { _castScanning.value = v }

    private val _castStatus = MutableStateFlow(CastStatus.IDLE)
    val castStatus: StateFlow<CastStatus> = _castStatus
    fun setCastStatus(v: CastStatus) { _castStatus.value = v }

    // Name of the device currently connecting/connected, and a human-readable detail line.
    private val _castTargetName = MutableStateFlow("")
    val castTargetName: StateFlow<String> = _castTargetName
    fun setCastTargetName(v: String) { _castTargetName.value = v }

    private val _castStatusDetail = MutableStateFlow("")
    val castStatusDetail: StateFlow<String> = _castStatusDetail
    fun setCastStatusDetail(v: String) { _castStatusDetail.value = v }

    @JvmField var onCastRefresh: Runnable? = null
    @JvmField var onCastConnect: java.util.function.Consumer<com.winhub.hernandez.cast.CastDiscovery.Device>? = null
    @JvmField var onCastDisconnect: Runnable? = null

    // -------------------------------------------------------------------------
    // Active dialog
    // -------------------------------------------------------------------------
    private val _activeDialog = MutableStateFlow(ActiveDialog.NONE)
    val activeDialog: StateFlow<ActiveDialog> = _activeDialog

    fun show(d: ActiveDialog) { _activeDialog.value = d }
    fun dismiss() { _activeDialog.value = ActiveDialog.NONE }

    // -------------------------------------------------------------------------
    // Magnifier overlay
    // -------------------------------------------------------------------------
    private val _magnifierVisible = MutableStateFlow(false)
    val magnifierVisible: StateFlow<Boolean> = _magnifierVisible

    private val _magnifierZoom = MutableStateFlow(1.0f)
    val magnifierZoom: StateFlow<Float> = _magnifierZoom

    fun setMagnifierVisible(v: Boolean) { _magnifierVisible.value = v }
    fun setMagnifierZoom(v: Float)      { _magnifierZoom.value = v }

    fun interface FloatCallback { fun invoke(delta: Float) }
    @JvmField var onMagnifierZoom: FloatCallback? = null
    @JvmField var onMagnifierHide: Runnable? = null

    // -------------------------------------------------------------------------
    // Scaling mode (Vulkan spatial upscaler)
    // -------------------------------------------------------------------------
    // 0=none 1=linear 2=nearest 3=sgsr 4=fsr(fill) 5=fsr_fit(letterbox). Drawer-only /
    // session-live (not persisted to the Container). Modes 1/2 also drive the base sampler
    // filter natively, so this is the single source of truth for Vulkan scaling/filtering.
    private val _upscalerMode = MutableStateFlow(0)
    val upscalerMode: StateFlow<Int> = _upscalerMode
    fun setUpscalerMode(v: Int) { _upscalerMode.value = v }

    // The spatial upscaler lives on the Vulkan renderer only; false grays the picker.
    private val _vulkanSupported = MutableStateFlow(false)
    val vulkanSupported: StateFlow<Boolean> = _vulkanSupported
    fun setVulkanSupported(v: Boolean) { _vulkanSupported.value = v }

    fun interface UpscalerApplyCallback { fun invoke(mode: Int) }
    @JvmField var onUpscalerApply: UpscalerApplyCallback? = null

    // -------------------------------------------------------------------------
    // Vulkan composable post effects (CAS sharpen + fake-HDR) + real upscaler
    // sharpness. Drawer-only / session-live, same lifecycle as the scaling mode.
    // -------------------------------------------------------------------------
    private val _casEnabled = MutableStateFlow(false)
    val casEnabled: StateFlow<Boolean> = _casEnabled
    fun setCasEnabled(v: Boolean) { _casEnabled.value = v }

    private val _casSharpness = MutableStateFlow(60)
    val casSharpness: StateFlow<Int> = _casSharpness
    fun setCasSharpness(v: Int) { _casSharpness.value = v }

    private val _hdrVkEnabled = MutableStateFlow(false)
    val hdrVkEnabled: StateFlow<Boolean> = _hdrVkEnabled
    fun setHdrVkEnabled(v: Boolean) { _hdrVkEnabled.value = v }

    // 0..100; 75 == the legacy 0.25 RCAS stops. Drives RCAS + SGSR EdgeSharpness.
    private val _upscaleSharpness = MutableStateFlow(75)
    val upscaleSharpness: StateFlow<Int> = _upscaleSharpness
    fun setUpscaleSharpness(v: Int) { _upscaleSharpness.value = v }

    fun interface CasApplyCallback { fun invoke(enabled: Boolean, sharpness: Int) }
    @JvmField var onCasApply: CasApplyCallback? = null

    fun interface HdrApplyCallback { fun invoke(enabled: Boolean) }
    @JvmField var onHdrApply: HdrApplyCallback? = null

    fun interface UpscaleSharpnessCallback { fun invoke(sharpness: Int) }
    @JvmField var onUpscaleSharpnessApply: UpscaleSharpnessCallback? = null

    // Terminal debanding / dither. strength 0..200 -> strength/100 LSBs (100 = 1 LSB,
    // the default). Drawer-only / session-live, same lifecycle as CAS/HDR.
    private val _debandEnabled = MutableStateFlow(false)
    val debandEnabled: StateFlow<Boolean> = _debandEnabled
    fun setDebandEnabled(v: Boolean) { _debandEnabled.value = v }

    private val _debandStrength = MutableStateFlow(100)
    val debandStrength: StateFlow<Int> = _debandStrength
    fun setDebandStrength(v: Int) { _debandStrength.value = v }

    fun interface DebandApplyCallback { fun invoke(enabled: Boolean, strength: Int) }
    @JvmField var onDebandApply: DebandApplyCallback? = null

    // -------------------------------------------------------------------------
    // Vulkan Phase 2 screen effects: Color grade
    // (brightness/contrast/gamma sliders) + FXAA/Toon/CRT/NTSC toggles. Drawer-only
    // / session-live, same lifecycle as the scaling mode + CAS/HDR.
    // -------------------------------------------------------------------------
    private val _vkBrightness = MutableStateFlow(0f)       // -100..100, 0 = neutral
    val vkBrightness: StateFlow<Float> = _vkBrightness
    fun setVkBrightness(v: Float) { _vkBrightness.value = v }

    private val _vkContrast = MutableStateFlow(0f)         // -100..100, 0 = neutral
    val vkContrast: StateFlow<Float> = _vkContrast
    fun setVkContrast(v: Float) { _vkContrast.value = v }

    private val _vkGamma = MutableStateFlow(1.0f)          // 0.5..3.0, 1.0 = neutral
    val vkGamma: StateFlow<Float> = _vkGamma
    fun setVkGamma(v: Float) { _vkGamma.value = v }

    // Saturation is expressed in percent (0..200) so it reads like the other grade
    // sliders; 100 = neutral. The renderer divides by 100 for the shader's [0,2] mix.
    private val _vkSaturation = MutableStateFlow(100f)     // 0..200, 100 = neutral
    val vkSaturation: StateFlow<Float> = _vkSaturation
    fun setVkSaturation(v: Float) { _vkSaturation.value = v }

    private val _vkFxaa = MutableStateFlow(false)
    val vkFxaa: StateFlow<Boolean> = _vkFxaa
    fun setVkFxaa(v: Boolean) { _vkFxaa.value = v }

    private val _vkToon = MutableStateFlow(false)
    val vkToon: StateFlow<Boolean> = _vkToon
    fun setVkToon(v: Boolean) { _vkToon.value = v }

    private val _vkCrt = MutableStateFlow(false)
    val vkCrt: StateFlow<Boolean> = _vkCrt
    fun setVkCrt(v: Boolean) { _vkCrt.value = v }

    private val _vkNtsc = MutableStateFlow(false)
    val vkNtsc: StateFlow<Boolean> = _vkNtsc
    fun setVkNtsc(v: Boolean) { _vkNtsc.value = v }

    // Single applier for the Vulkan screen effects. `saturation` is
    // the 0..200 percent slider (100 = neutral), same units as vkSaturation above.
    fun interface VulkanScreenEffectsCallback {
        fun invoke(brightness: Float, contrast: Float, gamma: Float, saturation: Float,
                   fxaa: Boolean, toon: Boolean, crt: Boolean, ntsc: Boolean)
    }
    @JvmField var onVulkanScreenEffectsApply: VulkanScreenEffectsCallback? = null

    // -------------------------------------------------------------------------
    // ReShade (vkBasalt) — in-game tab. Tier 1: a multi-effect LOADOUT the user switches between
    // LIVE. All loadout effects are compiled into the chain up front; per-effect flags decide which
    // present. A solo/stack MODE governs how many can be active at once (solo = radio/one-at-a-time
    // for A/B; stack = checkboxes/layered). The effects are chosen pre-launch (shortcut/container
    // editor); this tab toggles + tunes them. Supported only on DXVK/VKD3D (Vulkan) games — the tab
    // greys/hides otherwise (mirrors how SGSR/HDR are gated). Every change rides a SINGLE pluggable
    // seam (onReshadeApply -> applyReshadeLive: conf rewrite the patched libvkbasalt mtime-watch
    // reloads live, and persists to Container/shortcut).
    // -------------------------------------------------------------------------
    private val _reshadeSupported = MutableStateFlow(false)
    val reshadeSupported: StateFlow<Boolean> = _reshadeSupported
    fun setReshadeSupported(v: Boolean) { _reshadeSupported.value = v }

    // Master (whole-chain) on/off — enableOnLaunch. Independent of the per-effect flags.
    private val _reshadeMasterEnabled = MutableStateFlow(false)
    val reshadeMasterEnabled: StateFlow<Boolean> = _reshadeMasterEnabled
    fun setReshadeMasterEnabled(v: Boolean) { _reshadeMasterEnabled.value = v }

    // "solo" (one active) | "stack" (any subset). Governs the activation controls (radio vs checkbox).
    private val _reshadeMode = MutableStateFlow(com.winhub.hernandez.reshade.ReshadeLoadout.MODE_SOLO)
    val reshadeMode: StateFlow<String> = _reshadeMode
    fun setReshadeMode(v: String) { _reshadeMode.value = com.winhub.hernandez.reshade.ReshadeLoadout.normalizeMode(v) }

    // The loadout: ordered effects, each with enabled + reflected params + current values. Set once
    // at launch (seeded after the container is assigned); the drawer edits copies + reports snapshots.
    private val _reshadeLoadout = MutableStateFlow<List<ReshadeLoadoutItem>>(emptyList())
    val reshadeLoadout: StateFlow<List<ReshadeLoadoutItem>> = _reshadeLoadout
    fun setReshadeLoadout(v: List<ReshadeLoadoutItem>) { _reshadeLoadout.value = v }

    // Full drawer snapshot: master on/off + mode + the per-effect (enabled + values) loadout. The
    // activity rewrites the conf (mtime bump → live) and persists in applyReshadeLive.
    fun interface ReshadeApplyCallback {
        fun invoke(masterEnabled: Boolean, mode: String, items: List<ReshadeLoadoutItem>)
    }
    @JvmField var onReshadeApply: ReshadeApplyCallback? = null

    // "Live preview" — persisted global toggle at the top of the ReShade tab. ON = ReShade changes
    // apply live while the game keeps running (today's behaviour). OFF (default) = "freeze-frame +
    // pulse" preview: the first committed change SIGSTOPs the game; each subsequent change briefly
    // resumes for 1–2 presents to reveal the new look, then re-freezes. The activity owns the flag
    // (seeded from SharedPreferences in setupUI); this flow mirrors it for the drawer toggle.
    private val _reshadeLivePreview = MutableStateFlow(false)
    val reshadeLivePreview: StateFlow<Boolean> = _reshadeLivePreview
    fun setReshadeLivePreview(v: Boolean) { _reshadeLivePreview.value = v }

    fun interface BoolCallback { fun invoke(value: Boolean) }
    @JvmField var onReshadeLivePreviewChange: BoolCallback? = null

    // -------------------------------------------------------------------------
    // Paused / frozen state — single mirror of the activity's `isPaused`. Drives the centered
    // "Paused — tap to resume" box in the dialog host (covers BOTH the ReShade freeze-frame preview
    // and a normal manual Pause). The activity is the single source of truth; it writes this flow
    // whenever the guest is suspended/resumed. Tapping the box fires onRequestResume (full resume).
    // -------------------------------------------------------------------------
    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused
    fun setPaused(v: Boolean) { _paused.value = v }

    // True while the game is shown on an external display (TV). Drives the on-handheld "playing on
    // external display" indicator so the phone isn't just a black screen once the game surface moves.
    private val _playingOnExternal = MutableStateFlow(false)
    val playingOnExternal: StateFlow<Boolean> = _playingOnExternal
    fun setPlayingOnExternal(v: Boolean) { _playingOnExternal.value = v }

    // True while the in-game side menu (drawer) is open — used to hide the external-display badge so
    // it doesn't overlap the menu.
    private val _menuOpen = MutableStateFlow(false)
    val menuOpen: StateFlow<Boolean> = _menuOpen
    fun setMenuOpen(v: Boolean) { _menuOpen.value = v }
    @JvmField var onRequestResume: Runnable? = null

    // "Frame generation changed — Resume" overlay. Shown while the guest is paused AND the game
    // surface is fully torn down after an in-game frame-gen change (Off/On/2×/3×/4×). A plain
    // swapchain recreate on the same surface does NOT clear the LSFG black-frame flicker — only a
    // background/foreground-style surface teardown does — so the reset is made explicit with a
    // Resume prompt: the guest stays frozen and the surface stays down until the user taps Resume,
    // which fires onFgResetResume (rebuild the surface + SIGCONT the guest). Distinct from `paused`
    // above so it never collides with the ReShade freeze-preview / manual-pause box.
    private val _fgResetPaused = MutableStateFlow(false)
    val fgResetPaused: StateFlow<Boolean> = _fgResetPaused
    fun setFgResetPaused(v: Boolean) { _fgResetPaused.value = v }
    @JvmField var onFgResetResume: Runnable? = null

    // -------------------------------------------------------------------------
    // Vibration dialog
    // -------------------------------------------------------------------------
    private val _vibrationSlots = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val vibrationSlots: StateFlow<List<Pair<String, Boolean>>> = _vibrationSlots

    fun setVibrationSlots(slots: List<Pair<String, Boolean>>) { _vibrationSlots.value = slots }

    /** Reflect a per-slot toggle in the UI list IMMEDIATELY. The row's ToggleRow is controlled purely by
     *  this list, so without this a toggle would snap back visually (the WinHandler persist via
     *  onVibrationSlotChanged happened, but the list stayed stale). Call this alongside the callback. */
    fun updateVibrationSlot(index: Int, enabled: Boolean) {
        val cur = _vibrationSlots.value
        if (index in cur.indices) {
            _vibrationSlots.value = cur.toMutableList().also { it[index] = it[index].first to enabled }
        }
    }

    fun interface VibrationSlotCallback { fun invoke(slot: Int, enabled: Boolean) }
    @JvmField var onVibrationSlotChanged: VibrationSlotCallback? = null

    // Master controller-vibration switch (off = all rumble suppressed; disables the per-slot rows).
    private val _vibrationMasterEnabled = MutableStateFlow(true)
    val vibrationMasterEnabled: StateFlow<Boolean> = _vibrationMasterEnabled
    fun setVibrationMasterEnabled(enabled: Boolean) { _vibrationMasterEnabled.value = enabled }

    fun interface VibrationMasterCallback { fun invoke(enabled: Boolean) }
    @JvmField var onVibrationMasterChanged: VibrationMasterCallback? = null

    // Per-container rumble MODE (0=Off 1=Controller 2=Device 3=Both) + INTENSITY (0..100). Seeded
    // from Container in setupUI (after the container is assigned — seeding earlier would read a
    // still-null container) and pushed live to WinHandler + persisted to Container on every change,
    // same round-trip as the lsfg "Performance mode" toggle.
    private val _vibrationMode = MutableStateFlow(1) // Container.VIBRATION_MODE_DEFAULT (Controller)
    val vibrationMode: StateFlow<Int> = _vibrationMode
    fun setVibrationMode(v: Int) { _vibrationMode.value = v }

    private val _vibrationIntensity = MutableStateFlow(100)
    val vibrationIntensity: StateFlow<Int> = _vibrationIntensity
    fun setVibrationIntensity(v: Int) { _vibrationIntensity.value = v }

    fun interface VibrationModeCallback { fun invoke(mode: Int) }
    @JvmField var onVibrationModeChanged: VibrationModeCallback? = null

    fun interface VibrationIntensityCallback { fun invoke(intensity: Int) }
    @JvmField var onVibrationIntensityChanged: VibrationIntensityCallback? = null

    // ── Controls › Pad: live controller settings (LiveControllerSession owns the logic) ──
    /** What the Pad sub-tab shows. The activity republishes it after every change and reconnect. */
    data class PadState(
        /** Wine session, not gamescope / the Linux runtime: the sub-tab exists at all. */
        val supported: Boolean = false,
        /** ControllerIdentity value the setting holds now. */
        val identity: String = "generic",
        /** Non-null: why the identity can't change here (e.g. FAKE_EVDEV_IDENTITY in the env vars). */
        val identityLockedReason: String? = null,
        /** Direct XInput is installed for this session (the XInput source row exists). */
        val xinputAvailable: Boolean = false,
        /** XInput source wanted: true = Direct (HERMES), false = Wine's own. */
        val xinputDirect: Boolean = true,
        /** The source can switch in-game ("Allow in-game switching" was on at launch). */
        val xinputSwitchable: Boolean = false,
        val dinputVisible: Boolean = false,
        /** Non-null: why DirectInput visibility is fixed for this launch (passthrough, DirectInput pad). */
        val dinputLockedReason: String? = null,
        /** DirectInput vibration (core.DInputVibration): DirectInput games' force feedback reaches the pad. */
        val dinputVibration: Boolean = true,
        /** Non-null: why it can't change here (HERMES_DINPUT_FF in the env vars). */
        val dinputVibrationLockedReason: String? = null,
        /** StickPacing format: auto:N / fixed:N / off. */
        val pacing: String = "auto:60",
        /** Non-null: why the pacing can't change here (HERMES_AXIS_* env vars). */
        val pacingLockedReason: String? = null,
        /** The stick-frame rate the game gets right now (0 = unknown). */
        val nowHz: Int = 0,
        /** Changes saved but waiting for the controller to reconnect. */
        val pendingReconnect: Boolean = false,
        val reconnecting: Boolean = false,
        /** Settings saved that take effect only on the next launch (shown as a note). */
        val nextLaunchOnly: List<String> = emptyList(),
    )

    private val _pad = MutableStateFlow(PadState())
    val pad: StateFlow<PadState> = _pad
    fun setPad(state: PadState) { _pad.value = state }

    fun interface PadStringCallback { fun invoke(value: String) }
    fun interface PadBoolCallback { fun invoke(value: Boolean) }
    @JvmField var onPadIdentity: PadStringCallback? = null
    @JvmField var onPadXInputDirect: PadBoolCallback? = null
    @JvmField var onPadDInputVisible: PadBoolCallback? = null
    @JvmField var onPadDInputVibration: PadBoolCallback? = null
    @JvmField var onPadPacing: PadStringCallback? = null
    @JvmField var onPadReconnectNow: Runnable? = null
    @JvmField var onPadNextLaunch: Runnable? = null
    /** The sub-tab is showing: refresh "Now: N Hz" (called about once a second). */
    @JvmField var onPadRefresh: Runnable? = null

    // ── Where Controls › Pad and the rumble rows save (ControllerSaveTarget in the activity) ──
    data class ControllerSaveScope(
        /** The game the session runs; null for a desktop / container launch (container only). */
        val gameName: String? = null,
        val containerName: String = "",
        /** Changes go to this game; false = to the container, for all of its games. */
        val toGame: Boolean = false,
        /** Where the last change went, e.g. "Saved for NINJA GAIDEN only."; null before any change. */
        val lastSaved: String? = null,
    )

    private val _controllerSaveScope = MutableStateFlow(ControllerSaveScope())
    val controllerSaveScope: StateFlow<ControllerSaveScope> = _controllerSaveScope
    fun setControllerSaveScope(scope: ControllerSaveScope) { _controllerSaveScope.value = scope }

    /** The footer's "Save for" choice: true = this game, false = all games in the container. */
    @JvmField var onControllerSaveScope: PadBoolCallback? = null

    // -------------------------------------------------------------------------
    // Player Slots (manual per-device slot assignment) — Controls > Players sub-tab. One row per
    // detected input device (plus the on-screen pad). `override` is the user's choice: SLOT_AUTO
    // (-1) leaves auto-assignment alone, SLOT_IGNORE (-2) drops the device, 0..3 pins it to that
    // XInput player slot. `currentSlot` is the slot the device actually holds right now (-1 =
    // unassigned) and is shown as a subtitle. The list is re-read from WinHandler via
    // onPlayerSlotsRefresh (devices hot-plug), and each edit fires onPlayerSlotChanged, which the
    // activity applies live (WinHandler.setDeviceSlotAssignment) + persists per-container.
    // -------------------------------------------------------------------------
    const val SLOT_AUTO = -1
    const val SLOT_IGNORE = -2 // mirrors WinHandler.SLOT_IGNORE

    data class PlayerSlotRow(
        val displayName: String,
        val descriptor: String,
        val currentSlot: Int,
        val override: Int,
        val isOnScreen: Boolean,
        val isGameController: Boolean,
        // Whether the device currently owning this row's slot exposes a rumble motor — gates the
        // controller-test "Identify" buzz button. Defaults false so it never claims rumble it can't do.
        val hasVibrator: Boolean = false,
    )

    private val _playerSlots = MutableStateFlow<List<PlayerSlotRow>>(emptyList())
    val playerSlots: StateFlow<List<PlayerSlotRow>> = _playerSlots
    fun setPlayerSlots(rows: List<PlayerSlotRow>) { _playerSlots.value = rows }

    fun interface PlayerSlotCallback { fun invoke(descriptor: String, desiredSlot: Int) }
    /** Fired when the user changes a device's slot selection. desiredSlot = 0..3 pin, SLOT_IGNORE, or
     *  SLOT_AUTO. The activity applies it live and re-seeds the list afterwards. */
    @JvmField var onPlayerSlotChanged: PlayerSlotCallback? = null
    /** Re-reads the device list from WinHandler (call when the sub-tab opens / after a change). */
    @JvmField var onPlayerSlotsRefresh: Runnable? = null
    /** Manual "Reset Input" recovery — rebuilds the fake-input transport in place (no relaunch). */
    @JvmField var onResetInput: Runnable? = null

    // -------------------------------------------------------------------------
    // Controller Test panel (Controls > Players popup). While the popup is open the activity forks a
    // THROWAWAY copy of the physical controller state — used ONLY to drive the visualizer — and pushes
    // it here every input event; the game's real input path is left byte-for-byte untouched (the
    // isolation is gated on onControllerTestActive at the activity's dispatch chokepoints). The
    // emulated target is always an Xbox 360 pad; `type` only picks which picture to draw.
    // -------------------------------------------------------------------------
    // The snapshot type is the shared/neutral one (com.winhub.hernandez.ui.controllertest) so the in-game
    // dialog and the at-rest Settings screen produce the SAME data for the SAME visualizer panel.
    private val _controllerTestSnapshot =
        MutableStateFlow<com.winhub.hernandez.ui.controllertest.ControllerTestSnapshot?>(null)
    val controllerTestSnapshot: StateFlow<com.winhub.hernandez.ui.controllertest.ControllerTestSnapshot?> =
        _controllerTestSnapshot
    fun setControllerTestSnapshot(v: com.winhub.hernandez.ui.controllertest.ControllerTestSnapshot?) {
        _controllerTestSnapshot.value = v
    }
    fun clearControllerTestSnapshot() { _controllerTestSnapshot.value = null }

    fun interface BooleanCallback { fun invoke(active: Boolean) }
    /** Fired true when the controller-test popup becomes visible and false when it goes away (close,
     *  or lifecycle onPause/onStop). The activity flips its input-isolation flag on this. */
    @JvmField var onControllerTestActive: BooleanCallback? = null

    fun interface IntCallback { fun invoke(value: Int) }
    /** "Identify" — buzz the pad currently owning the given 0-based slot (WinHandler.testRumble). */
    @JvmField var onControllerIdentify: IntCallback? = null

    /** Id of the profile active in the running game (inputControlsView.getProfile()), so the in-game
     *  visual binder defaults to editing it. -1 when none. Set by the activity in setupUI. */
    @JvmField var activeProfileId: Int = -1

    /** Id of the profile currently ACTIVE on the PHYSICAL pad (Players > Bind lane), independent of the
     *  OSC lane. -1 = native Xbox passthrough. Seeded by the activity from the "controllerProfile"
     *  shortcut extra and kept in sync as the Bind picker activates profiles, so the picker reflects it. */
    @JvmField var physicalProfileId: Int = -1

    /** Fired by the in-game Bind picker to ACTIVATE a profile on the physical pad (id), or -1 to revert
     *  to native passthrough. The activity resolves the id and calls InputControlsView.setPhysicalProfile. */
    @JvmField var onPhysicalProfileChanged: IntCallback? = null

    /** Fired after the in-game binder creates/renames a profile so the activity re-reads profiles and
     *  re-pushes the Touch dropdown — keeps both profile pickers live without a relaunch. */
    @JvmField var onProfilesChanged: Runnable? = null

    // -------------------------------------------------------------------------
    // Controller-status TOAST (P5b) — a small app-themed card that fades into the TOP-RIGHT of the
    // in-game screen (below the Fusion HUD), lists each detected input device (type icon + name +
    // player badge), holds a few seconds, then fades out. Fired on: launch, controller hot-plug
    // (add/remove), manual slot reassignment, and Reset Input. Purely informational / non-interactive
    // (see ControllerToastOverlay). The activity reads getPlayerSlotAssignments (main-thread only) and
    // calls showControllerToastFor with the SAME PlayerSlotRow list the Players sub-tab uses, so the
    // toast and the editor never disagree.
    // -------------------------------------------------------------------------
    enum class ToastIconType { CONTROLLER, TOUCH, MOUSE, KEYBOARD }
    enum class ToastBadgeType { PLAYER, SHARE, IGNORED, DETECTED }

    data class ControllerToastRow(
        val name: String,
        val kind: String,
        val icon: ToastIconType,
        val badge: ToastBadgeType,
        val slot: Int,        // 0-based player slot for PLAYER/SHARE badges; -1 otherwise
        val isNew: Boolean,   // true for the device that just hot-plugged (drives the "NEW" tag)
    )

    data class ControllerToastData(
        val title: String,
        val subLabel: String,
        val rows: List<ControllerToastRow>,
        // Incremented on every event; the overlay keys its fade-in/hold/fade-out animation on this so a
        // new event fired while the toast is showing restarts the cycle with fresh content.
        val token: Long,
        // Optional full-width message line (used by info toasts that have no device rows, e.g. the
        // external-display / TV notification). Null for the controller toast.
        val message: String? = null,
    )

    private val _controllerToast = MutableStateFlow<ControllerToastData?>(null)
    val controllerToast: StateFlow<ControllerToastData?> = _controllerToast
    private var _toastToken = 0L

    /** Called by the overlay once the fade-out finishes (auto-dismiss). */
    fun clearControllerToast() { _controllerToast.value = null }

    /**
     * Show a simple informational toast that reuses the controller-toast card (title + right-aligned
     * subLabel + one full-width message line, no device rows). Used for external-display / TV
     * notifications so they match the existing input notifications. Main-thread only.
     */
    fun showInfoToast(title: String, subLabel: String, message: String?) {
        _toastToken += 1
        _controllerToast.value = ControllerToastData(title, subLabel, emptyList(), _toastToken, message)
    }

    /**
     * Build + show the controller-status toast from the current player-slot rows. Main-thread only
     * (the caller must have just read WinHandler.getPlayerSlotAssignments on the main thread).
     *
     * @param reason one of "launch", "connected", "disconnected", "reassign", "reset" — drives the
     *   header title + sub-label.
     * @param slots  the SAME PlayerSlotRow list the Players sub-tab renders (OSC first, then devices).
     * @param changedDescriptor descriptor of a just-hot-plugged device (marks its row NEW); may be null.
     */
    fun showControllerToastFor(reason: String, slots: List<PlayerSlotRow>, changedDescriptor: String?) {
        // OSC only appears as a row when it is actually seated as a player (has a slot); physical
        // controllers + any slot-holder always appear (getPlayerSlotAssignments already filtered those).
        val visible = slots.filter { !it.isOnScreen || it.currentSlot >= 0 }

        // Shared-slot detection: two visible rows holding the same real slot.
        val slotCounts = HashMap<Int, Int>()
        visible.forEach { if (it.currentSlot >= 0) slotCounts[it.currentSlot] = (slotCounts[it.currentSlot] ?: 0) + 1 }

        val rows = visible.map { r ->
            val lname = r.displayName.lowercase()
            val icon = when {
                r.isOnScreen -> ToastIconType.TOUCH
                r.isGameController -> ToastIconType.CONTROLLER
                lname.contains("mouse") -> ToastIconType.MOUSE
                lname.contains("keyboard") || lname.contains("keypad") -> ToastIconType.KEYBOARD
                else -> ToastIconType.CONTROLLER
            }
            val badge = when {
                r.override == SLOT_IGNORE -> ToastBadgeType.IGNORED
                r.currentSlot >= 0 && (slotCounts[r.currentSlot] ?: 0) > 1 -> ToastBadgeType.SHARE
                r.currentSlot >= 0 -> ToastBadgeType.PLAYER
                else -> ToastBadgeType.DETECTED
            }
            val kind = when {
                r.isOnScreen -> "Virtual gamepad"
                badge == ToastBadgeType.IGNORED -> "Controller · filtered"
                icon == ToastIconType.MOUSE -> "Pointer"
                icon == ToastIconType.KEYBOARD -> "Keys"
                else -> "Controller"
            }
            ControllerToastRow(
                name = r.displayName, kind = kind, icon = icon, badge = badge, slot = r.currentSlot,
                isNew = changedDescriptor != null && r.descriptor == changedDescriptor,
            )
        }
        if (rows.isEmpty()) return

        val hasShare = rows.any { it.badge == ToastBadgeType.SHARE }
        val hasMouseKb = rows.any { it.icon == ToastIconType.MOUSE || it.icon == ToastIconType.KEYBOARD }
        val sharedSlot = rows.firstOrNull { it.badge == ToastBadgeType.SHARE }?.slot ?: 0

        val (title, sub) = when (reason) {
            "connected"    -> "CONTROLLER CONNECTED" to "just now"
            "disconnected" -> "CONTROLLER DISCONNECTED" to "just now"
            "reset"        -> "INPUT RESET" to "just now"
            "reassign"     ->
                if (hasShare) "PLAYER ${sharedSlot + 1} · SHARED" to "updated"
                else "INPUT UPDATED" to "updated"
            else /* launch */ -> when {
                hasMouseKb     -> "INPUT DETECTED"
                rows.size == 1 -> "CONTROLLER DETECTED"
                else           -> "CONTROLLERS DETECTED"
            } to "on launch"
        }

        _toastToken += 1
        _controllerToast.value = ControllerToastData(title, sub, rows, _toastToken)
    }

    // -------------------------------------------------------------------------
    // Achievement PILL stack — a channel SEPARATE from the single controller-toast slot above. Each
    // Steam achievement unlocked in-game (surfaced by AchievementWatcher from the Goldberg GSE
    // achievements.json) appends a gold pill; AchievementPillStackOverlay renders the whole LIST
    // stacked down the top-right and removes each by id once its own ~4.5s lifecycle finishes, so a
    // burst STACKS instead of replacing. Distinct from _controllerToast so the two never collide.
    // -------------------------------------------------------------------------
    data class AchievementPill(
        val id: Long,
        val name: String,
        val description: String?,
        val iconPath: String?, // local file path to the (color) achievement icon; null → placeholder
    )

    private val _achievementPills = MutableStateFlow<List<AchievementPill>>(emptyList())
    val achievementPills: StateFlow<List<AchievementPill>> = _achievementPills
    private var _achievementPillId = 0L

    /**
     * Append an achievement pill to the stack. Fired by AchievementWatcher for each newly-earned
     * achievement. Thread-safe (StateFlow) — callable off the FileObserver thread; from Java as
     * XServerDialogState.INSTANCE.showAchievementToast(...).
     */
    @JvmStatic
    fun showAchievementToast(name: String, description: String?, iconPath: String?) {
        _achievementPillId += 1
        _achievementPills.value = _achievementPills.value +
            AchievementPill(_achievementPillId, name, description, iconPath)
    }

    /** Remove a pill once its lifecycle finishes (called by the overlay on fade-out). */
    fun removeAchievementPill(id: Long) {
        _achievementPills.value = _achievementPills.value.filterNot { it.id == id }
    }

    // -------------------------------------------------------------------------
    // Gyro (motion aim) — Controls tab. WinHandler owns the values and persists them to
    // SharedPreferences, same round-trip as the vibration master switch: the activity seeds these
    // flows in setupUI and each drawer change fires the matching callback straight back into
    // WinHandler (live, no restart). Per-container/per-game persistence is a later phase.
    // -------------------------------------------------------------------------
    // False on devices with no gyroscope — hides the whole section rather than offering dead controls.
    private val _gyroSupported = MutableStateFlow(false)
    val gyroSupported: StateFlow<Boolean> = _gyroSupported
    fun setGyroSupported(v: Boolean) { _gyroSupported.value = v }

    // Separate from gyroSupported: a device can have a gyroscope but no rotation-vector sensor, in
    // which case rate mode works and only the Orientation chip is dead. False renders that chip
    // disabled with a reason rather than hiding it.
    private val _gyroOrientationSupported = MutableStateFlow(false)
    val gyroOrientationSupported: StateFlow<Boolean> = _gyroOrientationSupported
    fun setGyroOrientationSupported(v: Boolean) { _gyroOrientationSupported.value = v }

    private val _gyroEnabled = MutableStateFlow(true)
    val gyroEnabled: StateFlow<Boolean> = _gyroEnabled
    fun setGyroEnabled(v: Boolean) { _gyroEnabled.value = v }

    // 0 = Rate (tilt SPEED drives the stick, which recentres when you stop), 1 = Orientation /
    // "tilt to aim" (the stick follows the ANGLE held, so a held tilt sustains). WinHandler.GYRO_MODE_*.
    private val _gyroMode = MutableStateFlow(0)
    val gyroMode: StateFlow<Int> = _gyroMode
    fun setGyroMode(v: Int) { _gyroMode.value = v }

    // 0=Right stick 1=Left stick 2=Mouse (WinHandler.GYRO_TARGET_*).
    private val _gyroTarget = MutableStateFlow(0)
    val gyroTarget: StateFlow<Int> = _gyroTarget
    fun setGyroTarget(v: Int) { _gyroTarget.value = v }

    private val _gyroSensitivity = MutableStateFlow(2.0f)
    val gyroSensitivity: StateFlow<Float> = _gyroSensitivity
    fun setGyroSensitivity(v: Float) { _gyroSensitivity.value = v }

    private val _gyroDeadzone = MutableStateFlow(0.05f)
    val gyroDeadzone: StateFlow<Float> = _gyroDeadzone
    fun setGyroDeadzone(v: Float) { _gyroDeadzone.value = v }

    private val _gyroSmoothing = MutableStateFlow(0.5f)
    val gyroSmoothing: StateFlow<Float> = _gyroSmoothing
    fun setGyroSmoothing(v: Float) { _gyroSmoothing.value = v }

    private val _gyroInvertX = MutableStateFlow(false)
    val gyroInvertX: StateFlow<Boolean> = _gyroInvertX
    fun setGyroInvertX(v: Boolean) { _gyroInvertX.value = v }

    private val _gyroInvertY = MutableStateFlow(false)
    val gyroInvertY: StateFlow<Boolean> = _gyroInvertY
    fun setGyroInvertY(v: Boolean) { _gyroInvertY.value = v }

    // 0=L1 1=L2 2=R1 3=R3 4=Always on (WinHandler.GYRO_ACTIVATOR_*). Hold-to-activate throughout;
    // a hold-vs-toggle mode is a later phase.
    private val _gyroActivator = MutableStateFlow(0)
    val gyroActivator: StateFlow<Int> = _gyroActivator
    fun setGyroActivator(v: Int) { _gyroActivator.value = v }

    // 0 = Hold (gyro runs while the activator is down), 1 = Toggle (a tap latches it on until the
    // next tap). Ignored when the activator is "Always" — there's no button to latch.
    private val _gyroActivationMode = MutableStateFlow(0)
    val gyroActivationMode: StateFlow<Int> = _gyroActivationMode
    fun setGyroActivationMode(v: Int) { _gyroActivationMode.value = v }

    // Physical stick deadzone / sensitivity (see inputcontrols.StickShaper). One immutable snapshot so
    // the drawer always shows a consistent set; changes apply live and are persisted by the activity.
    private val _stickTuning = MutableStateFlow(StickShaper.DEFAULT)
    val stickTuning: StateFlow<StickShaper.Tuning> = _stickTuning
    fun setStickTuning(v: StickShaper.Tuning) { _stickTuning.value = v }

    fun interface StickTuningCallback { fun invoke(tuning: StickShaper.Tuning) }
    @JvmField var onStickTuningChanged: StickTuningCallback? = null

    fun interface GyroBoolCallback { fun invoke(value: Boolean) }
    fun interface GyroIntCallback { fun invoke(value: Int) }
    fun interface GyroFloatCallback { fun invoke(value: Float) }
    fun interface GyroActionCallback { fun invoke() }

    @JvmField var onGyroEnabledChanged: GyroBoolCallback? = null
    @JvmField var onGyroTargetChanged: GyroIntCallback? = null
    @JvmField var onGyroSensitivityChanged: GyroFloatCallback? = null
    @JvmField var onGyroDeadzoneChanged: GyroFloatCallback? = null
    @JvmField var onGyroSmoothingChanged: GyroFloatCallback? = null
    @JvmField var onGyroInvertXChanged: GyroBoolCallback? = null
    @JvmField var onGyroInvertYChanged: GyroBoolCallback? = null
    @JvmField var onGyroActivatorChanged: GyroIntCallback? = null
    @JvmField var onGyroActivationModeChanged: GyroIntCallback? = null
    @JvmField var onGyroModeChanged: GyroIntCallback? = null
    // Fired by the drawer's Recenter row: the next orientation sample recaptures the pose as centre.
    @JvmField var onGyroRecenterRequested: GyroActionCallback? = null

    // -------------------------------------------------------------------------
    // Debug / Logs dialog
    // -------------------------------------------------------------------------
    private val _logLines  = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines

    private val _logPaused = MutableStateFlow(false)
    val logPaused: StateFlow<Boolean> = _logPaused

    fun appendLog(line: String) {
        if (!_logPaused.value) {
            val current = _logLines.value
            // Cap at 3000 lines to avoid unbounded growth
            _logLines.value = if (current.size >= 3000) current.drop(1) + line else current + line
        }
    }
    fun clearLog()              { _logLines.value = emptyList() }
    fun setLogPaused(v: Boolean) { _logPaused.value = v }

    // -------------------------------------------------------------------------
    // Input Controls dialog
    // -------------------------------------------------------------------------
    private val _inputProfiles      = MutableStateFlow<List<String>>(emptyList())
    val inputProfiles: StateFlow<List<String>> = _inputProfiles

    private val _selectedProfileIdx = MutableStateFlow(0)  // 0 = Disabled
    val selectedProfileIdx: StateFlow<Int> = _selectedProfileIdx

    private val _showTouchscreen    = MutableStateFlow(false)
    val showTouchscreen: StateFlow<Boolean> = _showTouchscreen

    private val _timeoutEnabled     = MutableStateFlow(false)
    val timeoutEnabled: StateFlow<Boolean> = _timeoutEnabled

    private val _hapticsEnabled     = MutableStateFlow(false)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled

    fun setInputProfiles(profiles: List<String>) { _inputProfiles.value = profiles }
    fun setSelectedProfileIdx(v: Int)  { _selectedProfileIdx.value = v }
    fun setShowTouchscreen(v: Boolean) { _showTouchscreen.value = v }
    fun setTimeoutEnabled(v: Boolean)  { _timeoutEnabled.value = v }
    fun setHapticsEnabled(v: Boolean)  { _hapticsEnabled.value = v }

    fun interface InputConfirmCallback {
        fun invoke(profileIndex: Int, showTouchscreen: Boolean, timeout: Boolean, haptics: Boolean)
    }
    fun interface InputSettingsCallback {
        fun invoke(profileIndex: Int)
    }
    @JvmField var onInputControlsConfirm: InputConfirmCallback? = null
    @JvmField var onInputControlsSettings: InputSettingsCallback? = null

    // -------------------------------------------------------------------------
    // Active Windows dialog
    // -------------------------------------------------------------------------
    data class ActiveWindow(
        val title: String,
        val className: String,
        val icon: Bitmap?,
        val screenshot: Bitmap?,
        val handle: Long
    )

    private val _awWindows = MutableStateFlow<List<ActiveWindow>>(emptyList())
    val awWindows: StateFlow<List<ActiveWindow>> = _awWindows

    fun setAwWindows(windows: List<ActiveWindow>) { _awWindows.value = windows }
    fun updateAwScreenshot(index: Int, bitmap: Bitmap) {
        val list = _awWindows.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(screenshot = bitmap)
            _awWindows.value = list
        }
    }

    fun interface WindowClickCallback { fun invoke(className: String, handle: Long) }
    @JvmField var onWindowClick: WindowClickCallback? = null

    // -------------------------------------------------------------------------
    // Task Manager dialog
    // -------------------------------------------------------------------------
    data class TmProcess(
        val index: Int,
        val pid: Int,
        val name: String,
        val formattedMemory: String,
        val wow64: Boolean,
        // Current CPU-affinity bitmask (bit i = CPU i) reported by the guest's GetProcessAffinityMask.
        // Feeds the Processor Affinity picker so it opens pre-ticked to the process's live cores.
        val affinityMask: Int,
        val icon: Bitmap?
    )

    private val _tmProcesses = MutableStateFlow<List<TmProcess>>(emptyList())
    val tmProcesses: StateFlow<List<TmProcess>> = _tmProcesses

    private val _tmCpuCores  = MutableStateFlow<List<String>>(emptyList())
    val tmCpuCores: StateFlow<List<String>> = _tmCpuCores

    private val _tmCpuTitle  = MutableStateFlow("CPU")
    val tmCpuTitle: StateFlow<String> = _tmCpuTitle

    private val _tmMemTitle  = MutableStateFlow("Memory")
    val tmMemTitle: StateFlow<String> = _tmMemTitle

    private val _tmMemInfo   = MutableStateFlow("")
    val tmMemInfo: StateFlow<String> = _tmMemInfo

    private val _tmCount     = MutableStateFlow(0)
    val tmCount: StateFlow<Int> = _tmCount

    fun setTmProcesses(list: List<TmProcess>) { _tmProcesses.value = list }
    fun setTmCpuCores(cores: List<String>)    { _tmCpuCores.value = cores }
    fun setTmCpuTitle(s: String)              { _tmCpuTitle.value = s }
    fun setTmMemTitle(s: String)              { _tmMemTitle.value = s }
    fun setTmMemInfo(s: String)               { _tmMemInfo.value = s }
    fun setTmCount(n: Int)                    { _tmCount.value = n }

    // Enriched Task Manager header: live perf stats (polled ~1s) + the running container's config
    // (set once — it doesn't change mid-session). Nullable Int = metric unreadable (show "—").
    data class TmHeaderStats(
        val cpuPct: Int?, val cpuTempC: Int?,
        val gpuPct: Int?, val gpuTempC: Int?, val gpuClockMhz: Int?,
        val fps: Int, val fpsMin: Int,
        val ramUsed: String, val ramTotal: String,
        val swap: String?,
        val batteryPct: Int?, val batteryWatts: Float, val batteryTempC: Int?, val charging: Boolean,
        val perCoreMhz: List<Int>,
    )
    // displayBackend: "X11" or "Wayland". On Wayland the activity fills graphicsDriver with the
    // "compositor: … · game: …" pair (the X11 renderer + its driver are idle in that session).
    // hdr: what the display the game is on reports right now ("none - panel 500 nits",
    // "HDR10, HLG - 1000 nits"). Reporting only: nothing in the stack emits HDR, and the value is
    // re-sent when a screen is plugged in, because capability is per-display.
    // gpuSpoof: the GPU name a Wayland session actually got out to the game in place of the real
    // adapter, null when the game sees the real one (a spoof that could not be delivered included).
    // Always null on X11 — there the driver wrapper renames the Vulkan device, so every readout is
    // handed the spoofed name already and has nothing to substitute. Its row exists only while it is set.
    data class TmContainerInfo(
        val wine: String, val dxWrapper: String, val renderer: String,
        val graphicsDriver: String, val resolution: String, val device: String,
        val displayBackend: String, val hdr: String, val gpuSpoof: String?,
    )

    private val _tmHeader = MutableStateFlow<TmHeaderStats?>(null)
    val tmHeader: StateFlow<TmHeaderStats?> = _tmHeader
    private val _tmContainerInfo = MutableStateFlow<TmContainerInfo?>(null)
    val tmContainerInfo: StateFlow<TmContainerInfo?> = _tmContainerInfo

    fun setTmHeader(v: TmHeaderStats?)          { _tmHeader.value = v }
    fun setTmContainerInfo(v: TmContainerInfo?) { _tmContainerInfo.value = v }

    @JvmField var onTmRefresh: Runnable? = null
    @JvmField var onTmDismissed: Runnable? = null
    // Opens the New Task prompt. Wired to show the Compose NEW_TASK dialog (it used to fire a native
    // ContentDialog.prompt, which is invisible over the Vulkan/ASR fullscreen SurfaceView).
    @JvmField var onTmNewTask: Runnable? = null
    // Runs the command entered in the New Task dialog (same path the native prompt's OK ran:
    // winHandler.exec(command)).
    @JvmField var onTmNewTaskSubmit: TmStringCallback? = null

    fun interface TmStringCallback { fun invoke(name: String) }
    fun interface TmFrontCallback { fun invoke(name: String, pid: Int) }
    // Bring to Front needs the pid so the host can resolve the real X window + handle.
    @JvmField var onTmBringToFront: TmFrontCallback? = null
    @JvmField var onTmKillProcess: TmStringCallback? = null

    fun interface TmAffinityCallback { fun invoke(pid: Int, mask: Int) }
    @JvmField var onTmSetAffinity: TmAffinityCallback? = null

    // Live lookup of the mask the user last applied to a pid (or -1 if none). Lets the affinity
    // dialog show the user's real choice the instant it opens, without waiting on the async
    // process-list rebuild to carry the override into TmProcess.affinityMask.
    fun interface TmAffinityQuery { fun invoke(pid: Int): Int }
    @JvmField var onTmQueryAffinity: TmAffinityQuery? = null

    fun interface FpsConfigCallback { fun invoke(config: String) }

    // -------------------------------------------------------------------------
    // Reset — call when activity is destroyed or restarted
    // -------------------------------------------------------------------------
    fun reset() {
        _activeDialog.value    = ActiveDialog.NONE
        _magnifierVisible.value = false
        _magnifierZoom.value   = 1.0f
        _upscalerMode.value    = 0
        _vulkanSupported.value = false
        _casEnabled.value      = false
        _casSharpness.value    = 60
        _hdrVkEnabled.value    = false
        _upscaleSharpness.value = 75
        _debandEnabled.value   = false
        _debandStrength.value  = 100
        _vkBrightness.value    = 0f
        _vkContrast.value      = 0f
        _vkGamma.value         = 1.0f
        _vkSaturation.value    = 100f
        _vkFxaa.value          = false
        _vkToon.value          = false
        _vkCrt.value           = false
        _vkNtsc.value          = false
        _reshadeSupported.value = false
        _reshadeMasterEnabled.value = false
        _reshadeMode.value     = com.winhub.hernandez.reshade.ReshadeLoadout.MODE_SOLO
        _reshadeLoadout.value  = emptyList()
        _reshadeLivePreview.value = false
        _paused.value          = false
        _controllerToast.value = null
        _achievementPills.value = emptyList()
        _vibrationSlots.value  = emptyList()
        _vibrationMode.value   = 1
        _vibrationIntensity.value = 100
        _playerSlots.value     = emptyList()
        _gyroSupported.value   = false
        _gyroOrientationSupported.value = false
        _gyroEnabled.value     = true
        _gyroMode.value        = 0
        _gyroTarget.value      = 0
        _gyroSensitivity.value = 2.0f
        _gyroDeadzone.value    = 0.05f
        _gyroSmoothing.value   = 0.5f
        _gyroInvertX.value     = false
        _gyroInvertY.value     = false
        _gyroActivator.value   = 0
        _gyroActivationMode.value = 0
        _stickTuning.value     = StickShaper.DEFAULT
        _logLines.value        = emptyList()
        _logPaused.value       = false
        _inputProfiles.value   = emptyList()
        _selectedProfileIdx.value = 0
        _showTouchscreen.value = false
        _timeoutEnabled.value  = false
        _hapticsEnabled.value  = false
        _awWindows.value       = emptyList()
        _tmProcesses.value     = emptyList()
        _tmCpuCores.value      = emptyList()
        _tmCpuTitle.value      = "CPU"
        _tmMemTitle.value      = "Memory"
        _tmMemInfo.value       = ""
        _tmCount.value         = 0
        onMagnifierZoom = null; onMagnifierHide = null
        onUpscalerApply = null
        onCasApply = null; onHdrApply = null; onUpscaleSharpnessApply = null
        onDebandApply = null
        onVulkanScreenEffectsApply = null
        onReshadeApply = null
        onReshadeLivePreviewChange = null
        onRequestResume = null
        onVibrationSlotChanged = null
        onVibrationModeChanged = null; onVibrationIntensityChanged = null
        onPadIdentity = null; onPadXInputDirect = null; onPadDInputVisible = null; onPadDInputVibration = null; onPadPacing = null
        onPadReconnectNow = null; onPadNextLaunch = null; onPadRefresh = null
        onControllerSaveScope = null
        onPlayerSlotChanged = null; onPlayerSlotsRefresh = null; onResetInput = null
        onGyroEnabledChanged = null; onGyroTargetChanged = null
        onGyroSensitivityChanged = null; onGyroDeadzoneChanged = null; onGyroSmoothingChanged = null
        onGyroInvertXChanged = null; onGyroInvertYChanged = null; onGyroActivatorChanged = null
        onGyroActivationModeChanged = null; onGyroModeChanged = null
        onGyroRecenterRequested = null
        onStickTuningChanged = null
        onInputControlsConfirm = null; onInputControlsSettings = null
        onWindowClick = null
        physicalProfileId = -1; onPhysicalProfileChanged = null; onProfilesChanged = null
        onTmRefresh = null; onTmDismissed = null; onTmNewTask = null; onTmNewTaskSubmit = null
        onTmBringToFront = null; onTmKillProcess = null; onTmSetAffinity = null; onTmQueryAffinity = null
    }
}
