package com.winhub.hernandez.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.inputcontrols.StickShaper
import com.winhub.hernandez.container.GameDetails
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.WrapperManager
import com.winhub.hernandez.core.ControllerIdentity
import com.winhub.hernandez.core.DInputVibration
import com.winhub.hernandez.core.DInputVisibility
import com.winhub.hernandez.core.DirectAudioSupport
import com.winhub.hernandez.core.HermesXInput
import com.winhub.hernandez.core.StickPacing
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.WineInfo
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.display.WaylandHdr
import com.winhub.hernandez.midi.MidiManager
import com.winhub.hernandez.store.SteamStoreSearch
import com.winhub.hernandez.ui.components.AUDIO_PRESETS
import com.winhub.hernandez.ui.components.PRESET_CUSTOM
import com.winhub.hernandez.ui.components.audioConfigFromEnv
import com.winhub.hernandez.ui.components.audioConfigToEnv
import com.winhub.hernandez.ui.components.latencyIsDirectBuffer
import com.winhub.hernandez.ui.components.latencyUsedBy
import com.winhub.hernandez.winhandler.WinHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Phase 1 of the nested XMB: a game's Settings (the section list, General and Controller), Game
// Details, Properties, Clone to container and Remove. Every row reads the shortcut's CURRENT value
// with the same fallbacks as ShortcutSettingsDialogScreen's init (extra → container → default) and
// writes on change with the same encoding as its save(); "" / null clears an extra so the game
// re-inherits the container. Phase 2/3 menus (XmbSettingsPhase2*.kt / XmbGameTools*.kt) plug in here.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Things only the Games screen can do (its ViewModel / legacy dialogs). */
internal class XmbGameHost(
    val remove: (Shortcut) -> Boolean,
    val containers: () -> List<Container>,
    val openWrapperManager: () -> Unit,
)

/** One shortcut's settings, read/written the way the pop-up editor does. */
internal class XmbPrefs(val context: Context, val shortcut: Shortcut) {
    val res = context.resources!!
    val c: Container get() = shortcut.container
    fun ex(key: String, def: String): String = shortcut.getExtra(key, def) ?: def
    fun put(key: String, value: String?) { shortcut.putExtra(key, value); shortcut.saveData() }
    fun arr(id: Int): List<String> = res.getStringArray(id).toList()
    fun str(id: Int): String = context.getString(id)
    /** Index-matched label for a stored id (StringUtils.parseIdentifier on the display entry). */
    fun labelFor(entries: List<String>, id: String): String =
        entries.firstOrNull { StringUtils.parseIdentifier(it) == id } ?: entries.firstOrNull() ?: id

    // async facts the editor loads in the background
    var arm64ec by mutableStateOf<Boolean?>(null)
    var midiList by mutableStateOf<List<String>>(emptyList())

    /** Why the device's screen can't show HDR10 (the HDR row is greyed with it), null when it can. Read once. */
    val hdrUnavailableReason: String? by lazy { WaylandHdr.unavailableReason(context) }
}

private fun XmbPrefs.loadAsync(xmb: XmbScope) {
    xmb.scope.launch {
        val (a, midi) = withContext(Dispatchers.IO) {
            val arm = runCatching {
                val cm = ContentsManager(context); cm.syncContents()
                WineInfo.fromIdentifier(context, cm, c.wineVersion).isArm64EC()
            }.getOrDefault(false)
            val m = mutableListOf("-- ${context.getString(R.string.disabled)} --", MidiManager.DEFAULT_SF2_FILE)
            File(context.filesDir, MidiManager.SF_DIR).listFiles()?.forEach { m.add(it.name) }
            arm to m
        }
        arm64ec = a; midiList = midi
    }
}

private fun XmbScope.set(p: XmbPrefs, key: String, value: String?) { p.put(key, value); saved(); refresh() }

// ── Settings: the section list (L1/R1 cycles the open section) ─────────────────────────────────────

// The sections follow the pop-up editor's tabs: General, Graphics, Win Components, Env Vars, Advanced, Controller (+ TV while a
// screen is plugged in or the game launches on one). General, Graphics and TV share one row builder and split it by key.
private val GFX_KEYS = setOf(
    "hGfx", "gfxDriver", "gfxConfig", "wrappers",
    "dxWrapper", "dxConfig", "renderer", "vkNative", "vkColors", "vkPresent", "vkDriver", "renderScale",
    "sdPerf", "sdRefresh", "sdBackPressure", "sdPrecise", "hGl", "fastOpenGL", "glFpsHud", "glVsyncOff",
)
private val TV_KEYS = setOf("hTv", "tvDisplay", "tvLaunch", "tvModeId", "tvMatchRes", "tvHdr", "tvNoteUnplug", "tvNoteTouch")

internal fun xmbSettingsMenu(xmb: XmbScope, shortcut: Shortcut, host: XmbGameHost): XmbMenu {
    val p = XmbPrefs(xmb.context, shortcut).also { it.loadAsync(xmb) }
    val tvOn = runCatching {
        com.winhub.hernandez.display.ExternalDisplay.find(xmb.context) != null ||
            com.winhub.hernandez.display.ExternalDisplay.launchOnTv(shortcut)
    }.getOrDefault(false)
    val titles = listOf("General", "Graphics", "Win Components", "Env Vars", "Advanced", "Controller", "Mods") + if (tvOn) listOf("TV") else emptyList()
    fun section(n: Int): XmbMenu = when (n) {
        0 -> xmbGeneralMenu(xmb, p, host)
        1 -> xmbGraphicsMenu(xmb, p, host)
        2 -> xmbWinComponentsMenu(xmb, shortcut)
        3 -> xmbEnvVarsMenu(xmb, shortcut)
        4 -> xmbAdvancedMenu(xmb, shortcut)
        5 -> xmbControllerMenu(xmb, p)
        6 -> xmbModsMenu(xmb, shortcut)
        else -> xmbTvMenu(xmb, p, host)
    }.also { it.siblings = XmbSiblings(n, titles.size, ::section) { i -> "sec$i" } }
    val icons = listOf(Icons.Filled.Settings, Icons.Filled.DesktopWindows, Icons.Filled.Widgets, Icons.Filled.Extension, Icons.Filled.Tune, Icons.Filled.Gamepad, Icons.Filled.Apps, Icons.Filled.Tv)
    val subs = listOf(
        "Game, display, frame generation, audio", "Driver, DX wrapper, renderer", "DirectX, VC++ runtimes…", "Environment variables",
        "Box64 / FEXCore, CPU cores, ReShade…", "Input, player slots, motion aim", "Game mods, programs to start", "External display",
    )
    return XmbMenu("Settings", Icons.Filled.Settings) {
        titles.mapIndexed { n, t -> XmbRow.Link("sec$n", t, icons[n], subtitle = subs[n]) { section(n) } }
    }
}

// ── General ─────────────────────────────────────────────────────────────────────────────────────────

internal fun xmbGeneralMenu(xmb: XmbScope, p: XmbPrefs, host: XmbGameHost): XmbMenu =
    XmbMenu("General", Icons.Filled.Settings) { generalRows(this, p, host).filter { it.key !in GFX_KEYS && it.key !in TV_KEYS } }

internal fun xmbGraphicsMenu(xmb: XmbScope, p: XmbPrefs, host: XmbGameHost): XmbMenu =
    XmbMenu("Graphics", Icons.Filled.DesktopWindows) {
        val g = generalRows(this, p, host).filter { it.key in GFX_KEYS }
        // The editor lists the render scale last in Graphics; it is built with Display, so move it.
        g.filter { it.key == "hGfx" } + g.filter { it.key != "hGfx" && it.key != "renderScale" } + g.filter { it.key == "renderScale" }
    }

internal fun xmbTvMenu(xmb: XmbScope, p: XmbPrefs, host: XmbGameHost): XmbMenu =
    XmbMenu("TV", Icons.Filled.Tv) { generalRows(this, p, host).filter { it.key in TV_KEYS } }

// Supported bundled driver ids for the Wayland "Compositor driver" row. The probe behind it is
// native + serialized, and XMB rebuilds its rows on every set/refresh, so it runs once per process:
// the first build launches it and refreshes when done; imported ids are re-read on every build.
private var xmbBundledDriverVersions: List<String>? = null
private var xmbBundledDriverVersionsLoading = false
// Same once-per-process pattern for the variant "Auto" resolves to (native renderer probe) — the
// "Auto (by GPU: …)" label of the Wayland game driver row; WaylandGameDriver caches the answer.
private var xmbWaylandAutoLoading = false

private fun generalRows(xmb: XmbScope, p: XmbPrefs, host: XmbGameHost): List<XmbRow> {
    val s = p.shortcut
    val c = p.c
    val rows = mutableListOf<XmbRow>()
    rows += XmbRow.Header("hGame", "Game")
    rows += XmbRow.Text("name", p.str(R.string.name), Icons.Filled.ShortText, s.name, subtitle = "Renames the shortcut") { v ->
        val newBase = v.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        if (newBase.isNotEmpty() && newBase != s.name) renameAndReturn(xmb, s, newBase)
    }
    rows += XmbRow.Text("execArgs", "Exec arguments", Icons.Filled.ShortText, p.ex("execArgs", ""), subtitle = "Extra command-line arguments") { v ->
        xmb.set(p, "execArgs", v.ifEmpty { null })
    }
    val onC = runCatching { WinePath.isOnRemovableStorage(c, s.path) }.getOrDefault(false)
    rows += XmbRow.Link("storage", "Storage", Icons.Filled.Folder,
        value = if (onC) "SD card / USB storage" else "Current drive", subtitle = "Move to Drive C") { xmbCopyToDriveCMenu(xmb, s) }
    rows += XmbRow.Link("exe", "Executable", Icons.Filled.SwapHoriz,
        value = File(s.path ?: "").name.ifEmpty { "Unknown" }, subtitle = "Pick another .exe") { xmbChangeExeMenu(xmb, s) }
    rows += XmbRow.Link("icon", "Icon", Icons.Filled.Image, subtitle = "Pick an image for this game") { iconPickerMenu(xmb, s) }

    if (s.getExtra("storeSource") == "epic") {
        val eos = p.ex("epicEos", "1") != "0"
        rows += XmbRow.Header("hEpic", "Epic")
        rows += XmbRow.Toggle("epicEos", "EOS sign-in", Icons.Filled.Layers, eos, subtitle = "Sign EOS games in with your Epic account") { xmb.set(p, "epicEos", if (it) "1" else "0") }
        rows += XmbRow.Toggle("epicOvt", "Force Denuvo ownership token", Icons.Filled.Layers, p.ex("epicOvtForce", "0") == "1",
            subtitle = "For Denuvo games we don't auto-detect", disabledReason = if (eos) null else "Needs EOS sign-in") { xmb.set(p, "epicOvtForce", if (it) "1" else "0") }
        rows += XmbRow.Toggle("epicOffline", "Launch offline", Icons.Filled.Layers, p.ex("epicOffline", "0") == "1",
            subtitle = "Skip Epic sign-in for this game", disabledReason = if (eos) null else "Needs EOS sign-in") { xmb.set(p, "epicOffline", if (it) "1" else "0") }
        if (com.winhub.hernandez.FeatureFlags.EPIC_OVERLAY_ENABLED) {
            rows += XmbRow.Toggle("epicOverlay", "Epic friends overlay", Icons.Filled.Layers, p.ex("epicOverlay", "0") == "1",
                subtitle = "Experimental — Shift+F3 in game", disabledReason = if (eos) null else "Needs EOS sign-in") { xmb.set(p, "epicOverlay", if (it) "1" else "0") }
        }
    }

    // Display
    rows += XmbRow.Header("hDisplay", "Display")
    // Display backend: the picker and its Wayland explainer are deliberately NOT offered here —
    // hidden by request. The container's stored backend and any per-game override are left exactly
    // as they are; only the row is gone. The EFFECTIVE backend still gates everything below
    // (Renderer, compositor driver, render scale, …), so it is still resolved here.
    val waylandCapable = com.winhub.hernandez.core.WineWaylandSupport.isWaylandCapable(p.context, c.wineVersion)
    val containerWaylandDefault = c.isWaylandBackend && waylandCapable
    val dbOverride = p.ex("displayBackend", "")
    val waylandGame = if (dbOverride.isEmpty()) containerWaylandDefault
                      else dbOverride == Container.DISPLAY_BACKEND_WAYLAND && waylandCapable
    val sizes = p.arr(R.array.screen_size_entries)
    val rawSize = p.ex("screenSize", c.getScreenSize())
    val sizeLabel = sizes.firstOrNull { StringUtils.parseIdentifier(it).equals(rawSize, ignoreCase = true) } ?: "Custom"
    rows += XmbRow.Choice("screenSize", p.str(R.string.screen_size), Icons.Filled.DesktopWindows, sizes, sizeLabel) { v ->
        if (v == "Custom") {
            val w = if (rawSize.contains("x")) rawSize.substringBefore("x") else "800"
            val h = if (rawSize.contains("x")) rawSize.substringAfter("x") else "600"
            xmb.set(p, "screenSize", "${w}x$h")
        } else xmb.set(p, "screenSize", StringUtils.parseIdentifier(v))
    }
    if (sizeLabel == "Custom") {
        val w = if (rawSize.contains("x")) rawSize.substringBefore("x") else "800"
        val h = if (rawSize.contains("x")) rawSize.substringAfter("x") else "600"
        fun setWH(nw: String, nh: String) {
            val ok = nw.matches(Regex("[0-9]+")) && nh.matches(Regex("[0-9]+")) && nw.toInt() % 2 == 0 && nh.toInt() % 2 == 0
            if (!ok) xmb.toast("Width and height must be even numbers")
            xmb.set(p, "screenSize", if (ok) "${nw}x$nh" else Container.DEFAULT_SCREEN_SIZE)
        }
        rows += XmbRow.Text("customW", "Width", Icons.Filled.AspectRatio, w, subtitle = "Even numbers only", numeric = true) { setWH(it, h) }
        rows += XmbRow.Text("customH", "Height", Icons.Filled.AspectRatio, h, subtitle = "Even numbers only", numeric = true) { setWH(w, it) }
    }
    val saValues = listOf("", "0", "1", "2")
    val saLabels = listOf(p.str(R.string.use_container_default), p.str(R.string.screen_alignment_center), p.str(R.string.screen_alignment_top), p.str(R.string.screen_alignment_bottom))
    val sa = p.ex("screenAlignment", "")
    // Alignment and fullscreen mode apply on X11 and Wayland alike (the compositor fits the desktop
    // with the same modes), so neither row is gated on the backend.
    rows += XmbRow.Choice("screenAlignment", "Screen alignment", Icons.Filled.DesktopWindows, saLabels, saLabels[saValues.indexOf(sa).coerceAtLeast(0)]) { v ->
        xmb.set(p, "screenAlignment", saValues[saLabels.indexOf(v)].ifEmpty { null })
    }
    val fsLabels = listOf(p.str(R.string.fullscreen_mode_default), p.str(R.string.fullscreen_mode_off), p.str(R.string.fullscreen_mode_fit),
        p.str(R.string.fullscreen_mode_stretch), p.str(R.string.fullscreen_mode_fill), p.str(R.string.fullscreen_mode_integer))
    val fsRaw = s.getExtra("fullscreenMode") ?: ""
    val fsOverride = when {
        fsRaw.isNotEmpty() -> fsRaw.toIntOrNull() ?: -1
        p.ex("fullscreenStretched", "") == "1" -> Container.FULLSCREEN_STRETCH
        else -> -1
    }
    val fsIdx = if (fsOverride < 0) 0 else (fsOverride + 1).coerceIn(1, fsLabels.size - 1)
    rows += XmbRow.Choice("fullscreen", "Fullscreen mode", Icons.Filled.DesktopWindows, fsLabels, fsLabels[fsIdx]) { v ->
        val idx = fsLabels.indexOf(v)
        s.putExtra("fullscreenStretched", null)
        xmb.set(p, "fullscreenMode", if (idx <= 0) null else (idx - 1).toString())
    }
    val rsValues = listOf("1.0", "1.25", "1.5", "2.0")
    val rsLabels = listOf("Off", "1.25x", "1.5x", "2x")
    val rs = p.ex("renderScale", c.getExtra("renderScale", "1.0") ?: "1.0")
    // Greyed on Wayland (the downscale lives in the X11 Vulkan renderer only) and then DISPLAYS
    // "Not used on Wayland"; the stored value is left untouched.
    val rsShown = if (waylandGame) "Not used on Wayland" else rsLabels[rsValues.indexOf(rs).coerceAtLeast(0)]
    rows += XmbRow.Choice("renderScale", "Render scale", Icons.Filled.AspectRatio, if (waylandGame) listOf(rsShown) else rsLabels, rsShown, subtitle = "Supersampling",
        disabledReason = if (waylandGame) "Not used on Wayland: the compositor has no supersampling downscale" else null) { v ->
        val nv = rsValues[rsLabels.indexOf(v)]
        xmb.set(p, "renderScale", if (nv == "1.0") null else nv)
    }
    val rates = xmbPanelRates(p.context).filter { it > 60 }
    if (xmbPanelRates(p.context).isNotEmpty()) {
        val rrValues = listOf("", "locked", "0") + rates.map { it.toString() }
        val rrLabels = listOf(p.str(R.string.use_container_default), p.str(R.string.in_game_refresh_locked), p.str(R.string.max_game_refresh_rate_unlimited)) + rates.map { "$it Hz" }
        val unlock = p.ex("unlockGameRefreshRate", "")
        val maxR = p.ex("maxGameRefreshRate", "")
        val cur = when {
            unlock.isEmpty() && maxR.isEmpty() -> ""
            unlock == "0" -> "locked"
            else -> maxR.ifEmpty { "0" }
        }
        rows += XmbRow.Choice("refresh", "In-game refresh rate", Icons.Filled.Speed, rrLabels, rrLabels[rrValues.indexOf(cur).coerceAtLeast(0)]) { v ->
            when (val nv = rrValues[rrLabels.indexOf(v)]) {
                "" -> { s.putExtra("unlockGameRefreshRate", null); xmb.set(p, "maxGameRefreshRate", null) }
                "locked" -> { s.putExtra("unlockGameRefreshRate", "0"); xmb.set(p, "maxGameRefreshRate", "0") }
                else -> { s.putExtra("unlockGameRefreshRate", "1"); xmb.set(p, "maxGameRefreshRate", nv) }
            }
        }
    }

    // TV — the same rows as the pop-up editor's TV tab (display.ExternalDisplay), on the same
    // condition: an external display is plugged in, or this game is already set to launch on one so a
    // leftover setting is never hidden. Per game only, no container default. HDR is reported, not
    // asked: starting the session on the TV is what opens the compositor's HDR gate there.
    // XMB has no display listener of its own — its rows rebuild on refresh(), so a cable plugged in
    // while this column is open shows up on the next change rather than instantly.
    val tvDisplay = com.winhub.hernandez.display.ExternalDisplay.find(p.context)
    if (tvDisplay != null || com.winhub.hernandez.display.ExternalDisplay.launchOnTv(s)) {
        rows += XmbRow.Header("hTv", "TV")
        rows += XmbRow.Info("tvDisplay",
            if (tvDisplay != null) com.winhub.hernandez.display.ExternalDisplay.title(tvDisplay) else "No external display",
            Icons.Filled.Tv,
            value = if (tvDisplay != null) com.winhub.hernandez.display.ExternalDisplay.modeLabel(
                com.winhub.hernandez.display.ExternalDisplay.activeMode(tvDisplay)) else "",
            subtitle = if (tvDisplay != null) com.winhub.hernandez.display.ExternalDisplay.summary(tvDisplay)
                       else "Nothing is plugged in. This game is still set to launch on a TV.")
        rows += XmbRow.Toggle("tvLaunch", "Launch this game on the TV", Icons.Filled.Tv,
            com.winhub.hernandez.display.ExternalDisplay.launchOnTv(s),
            subtitle = com.winhub.hernandez.display.ExternalDisplay.HELP_LAUNCH) {
            xmb.set(p, com.winhub.hernandez.display.ExternalDisplay.EXTRA_LAUNCH, if (it) "1" else "0")
        }
        val tvModes = com.winhub.hernandez.display.ExternalDisplay.selectableModes(tvDisplay)
        val tvModeId = com.winhub.hernandez.display.ExternalDisplay.modeId(s)
        if (tvModes.size > 1) {
            val tvValues = listOf(0) + tvModes.map { it.modeId }
            val tvLabels = listOf("Default (leave the TV as it is)") +
                tvModes.map { com.winhub.hernandez.display.ExternalDisplay.modeLabel(it) }
            rows += XmbRow.Choice("tvModeId", "Output mode", Icons.Filled.DesktopWindows, tvLabels,
                tvLabels[tvValues.indexOf(tvModeId).coerceAtLeast(0)],
                subtitle = "What the TV is told to run at") { v ->
                xmb.set(p, com.winhub.hernandez.display.ExternalDisplay.EXTRA_MODE_ID,
                    tvValues[tvLabels.indexOf(v)].toString())
            }
        } else {
            rows += XmbRow.Info("tvModeId", "Output mode", Icons.Filled.DesktopWindows,
                value = com.winhub.hernandez.display.ExternalDisplay.modeLabel(tvModes.firstOrNull()).ifEmpty { "—" },
                subtitle = if (tvDisplay != null) "This screen advertises one output mode"
                           else "Plug a screen in to choose an output mode")
        }
        rows += XmbRow.Toggle("tvMatchRes", "Match the TV's resolution", Icons.Filled.AspectRatio,
            com.winhub.hernandez.display.ExternalDisplay.matchResolution(s),
            subtitle = com.winhub.hernandez.display.ExternalDisplay.HELP_MATCH_RES) {
            xmb.set(p, com.winhub.hernandez.display.ExternalDisplay.EXTRA_MATCH_RES, if (it) "1" else "0")
        }
        val tvHdrReason = if (tvDisplay == null) null
                          else com.winhub.hernandez.display.ExternalDisplay.hdrUnavailableReason(tvDisplay)
        rows += XmbRow.Info("tvHdr", "Use HDR on the TV", Icons.Filled.HdrOn,
            value = if (tvDisplay != null && tvHdrReason == null) "On" else "Off",
            subtitle = tvHdrReason ?: (
                if (tvDisplay != null) "Automatic. This screen accepts HDR10, so HDR output switches on for this game."
                else "Plug the screen in to see what it can do."))
        rows += XmbRow.Info("tvNoteUnplug", "If the cable comes out", Icons.Filled.Info,
            subtitle = com.winhub.hernandez.display.ExternalDisplay.NOTE_UNPLUG)
        rows += XmbRow.Info("tvNoteTouch", "Touch on the TV", Icons.Filled.TouchApp,
            subtitle = com.winhub.hernandez.display.ExternalDisplay.NOTE_TOUCH)
    }

    // Graphics
    rows += XmbRow.Header("hGfx", "Graphics")
    val gfxEntries = WrapperManager.driverEntries(p.context, p.res.getStringArray(R.array.graphics_driver_entries))
    val gfxId = p.ex("graphicsDriver", c.graphicsDriver)
    // Under Wayland the wrapper flavour is irrelevant: the compositor loads the installed Turnip named
    // by the "version" key of graphicsDriverConfig (XServerDisplayActivity's Wayland resolve), and the
    // game renders on the Proton's bundled Wayland Turnip. So the flavour picker is swapped for a
    // "Compositor driver" choice over the installed Turnip ids that writes ONLY the version key back
    // (withGraphicsDriverVersion); the config link stays live for the same key.
    if (waylandGame) {
        val gdc = p.ex("graphicsDriverConfig", c.getGraphicsDriverConfig())
        val compositorVersion = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog.getVersion(gdc) ?: ""
        // Same source as the config dialog's "Graphics Driver Version" (minus "System").
        if (xmbBundledDriverVersions == null && !xmbBundledDriverVersionsLoading) {
            xmbBundledDriverVersionsLoading = true
            xmb.scope.launch {
                xmbBundledDriverVersions = supportedBundledDriverVersions(p.context)
                xmbBundledDriverVersionsLoading = false
                xmb.refresh()
            }
        }
        val turnips = ((xmbBundledDriverVersions ?: emptyList()) + importedDriverVersions(p.context)).distinct()
        val turnipsLoaded = xmbBundledDriverVersions != null
        // One driver pick on Wayland: with the game driver on "adapter" it drives the game too.
        val wgdEffective = p.ex("waylandGameDriver", "").ifEmpty { c.waylandGameDriver }
        val gfxSubtitle = when {
            com.winhub.hernandez.core.WaylandAdapter.isProprietaryBlob(p.context, compositorVersion) ->
                "Qualcomm's own driver cannot put Wayland frames on screen: the screen uses a bundled Turnip automatically."
            com.winhub.hernandez.core.WaylandGameDriver.adapterActive(p.context, wgdEffective, compositorVersion) ->
                "Used for the game (through the Wayland adapter) and for putting its frames on screen."
            else -> "Used by the Wayland compositor to put frames on screen; the game renders on the Wayland game driver below."
        }
        rows += XmbRow.Choice("gfxDriver", p.str(R.string.graphics_driver), Icons.Filled.Memory, turnips,
            compositorDriverLabel(compositorVersion, turnips, turnipsLoaded),
            subtitle = gfxSubtitle) { v ->
            xmb.set(p, "graphicsDriverConfig", withGraphicsDriverVersion(gdc, v))
        }
        // "System"/empty falls back to the system libvulkan, which can't import the game's dmabufs
        // (black screen) — mirrors XServerDisplayActivity's Wayland driver resolve; so does an id
        // that is no longer installed. Warn only.
        if (compositorDriverUnusable(compositorVersion, turnips, turnipsLoaded)) {
            val isSystem = compositorVersion.isEmpty() || compositorVersion == "System"
            rows += XmbRow.Info("gfxSystemWarn",
                if (isSystem) "Graphics driver is \"System\"" else "Graphics driver is not available", Icons.Filled.Info,
                subtitle = """Wayland needs a Turnip driver here. "System" or a missing driver cannot import the game's frames and shows a black screen.""")
        }
        // Wayland game driver (per-game override of the container's waylandGameDriver; "" = container
        // default): Auto / the bundled Turnip variants / imported Linux ICDs — same options and labels
        // as the pop-up editors (WaylandGameDriver). A stored imported:<id> whose import is gone stays
        // listed (labelled missing); launch uses Auto for it.
        val wgdAuto = com.winhub.hernandez.core.WaylandGameDriver.autoVariantIfKnown()
        if (wgdAuto == null && !xmbWaylandAutoLoading) {
            xmbWaylandAutoLoading = true
            xmb.scope.launch {
                waylandAutoVariant(p.context)
                xmbWaylandAutoLoading = false
                xmb.refresh()
            }
        }
        // A stored auto shows as the adapter it resolves to (WaylandGameDriver.editorChoice), so the list
        // never carries two entries with the adapter's label.
        val wgdOverride = p.ex("waylandGameDriver", "").let {
            if (it.isEmpty()) "" else com.winhub.hernandez.core.WaylandGameDriver.editorChoice(p.context, it)
        }
        val wgdInstalled = com.winhub.hernandez.core.WaylandGameDriver.optionValues(p.context)
        val wgdValues = listOf("") + (if (wgdOverride.isEmpty() || wgdOverride in wgdInstalled) wgdInstalled else wgdInstalled + wgdOverride)
        val wgdLabels = wgdValues.map {
            if (it.isEmpty()) "Container default (" + com.winhub.hernandez.core.WaylandGameDriver.optionLabel(p.context, c.waylandGameDriver, wgdAuto) + ")"
            else com.winhub.hernandez.core.WaylandGameDriver.optionLabel(p.context, it, wgdAuto)
        }
        rows += XmbRow.Choice("waylandGameDriver", "Wayland game driver (advanced)", Icons.Filled.Memory, wgdLabels,
            wgdLabels[wgdValues.indexOf(wgdOverride).coerceAtLeast(0)],
            subtitle = com.winhub.hernandez.core.WaylandGameDriver.HELP_TEXT) { v ->
            xmb.set(p, "waylandGameDriver", wgdValues[wgdLabels.indexOf(v)].ifEmpty { null })
        }
        // The pop-up editors' gear next to the Wayland game driver: GPU name spoof, memory cap, present
        // mode, UBWC hint, in the same graphicsDriverConfig keys as X11's driver configuration.
        val spoof = com.winhub.hernandez.core.GpuSpoof.gpuNameOf(gdc)
        rows += XmbRow.Link("waylandDriverCfg", "Wayland driver settings", Icons.Filled.Tune,
            value = spoof.takeIf { com.winhub.hernandez.core.GpuSpoof.isSpoofing(it) },
            subtitle = "GPU name spoof, memory cap, present mode…") { xmbWaylandDriverConfigMenu(xmb, s) }
        // HDR output (per-game override of the container's waylandHdr; "" = container default, "1" on,
        // "0" off) — same options as the pop-up editor (WaylandHdr). Greyed with the reason on a screen
        // that doesn't report HDR10, still showing what is stored.
        val hdrValues = listOf("", "1", "0")
        val hdrLabels = listOf("Container default (" + (if (c.isWaylandHdr()) "On" else "Off") + ")", "On", "Off")
        rows += XmbRow.Choice(WaylandHdr.EXTRA, WaylandHdr.TITLE, Icons.Filled.HdrOn, hdrLabels,
            hdrLabels[hdrValues.indexOf(WaylandHdr.shortcutChoice(s)).coerceAtLeast(0)],
            subtitle = WaylandHdr.HELP_SHORT, disabledReason = p.hdrUnavailableReason) { v ->
            xmb.set(p, WaylandHdr.EXTRA, hdrValues[hdrLabels.indexOf(v)].ifEmpty { null })
        }
    } else {
        rows += XmbRow.Choice("gfxDriver", p.str(R.string.graphics_driver), Icons.Filled.Memory, gfxEntries, p.labelFor(gfxEntries, gfxId)) { v ->
            xmb.set(p, "graphicsDriver", StringUtils.parseIdentifier(v))
        }
    }
    // Driver configuration is X11 tuning; on Wayland its only live field (the Turnip version) is
    // covered by the Compositor driver row above, so the link is left out of the Wayland layout.
    if (!waylandGame) rows += XmbRow.Link("gfxConfig", "Driver configuration", Icons.Filled.Tune,
        subtitle = "Vulkan version, BCn, present modes…") { xmbDriverConfigMenu(xmb, s) }
    // Wrappers are X11 game-driver shims; nothing on the Wayland path uses them.
    if (!waylandGame) rows += XmbRow.External("wrappers", "Manage wrappers", Icons.Filled.Cloud, subtitle = "Import or remove wrapper drivers") { host.openWrapperManager() }
    val dxEntries = p.arr(R.array.dxwrapper_entries)
    val dxId = p.ex("dxwrapper", c.getDXWrapper())
    rows += XmbRow.Choice("dxWrapper", "DX wrapper", Icons.Filled.Layers, dxEntries, p.labelFor(dxEntries, dxId)) { v ->
        val nid = StringUtils.parseIdentifier(v)
        // Leaving VEGAS strips its config-file key, as the editor does.
        if (dxId.contains("vegas") && !nid.contains("vegas")) {
            val cfg = p.ex("dxwrapperConfig", c.getDXWrapperConfig())
            s.putExtra("dxwrapperConfig", cfg.split(",").filterNot { it.startsWith("dxvkConfigFile=") }.joinToString(","))
        }
        xmb.set(p, "dxwrapper", nid)
    }
    rows += XmbRow.Link("dxConfig", "DX wrapper configuration", Icons.Filled.Tune,
        subtitle = "Versions, frame rate, filtering…") { xmbDxConfigMenu(xmb, s) }

    val rendId = p.ex("renderer", c.renderer).lowercase()
    val rend = when (rendId) {
        "egl" -> "EGL"
        "surfacedirect" -> "SurfaceDirect"
        else -> "Vulkan"
    }
    // Display only while disabled on Wayland: the compositor is always Vulkan; `rend` (the stored
    // X11 choice) is untouched and still drives the SurfaceDirect sub-rows below.
    val rendShown = if (waylandGame) "Vulkan (Wayland compositor)" else rend
    rows += XmbRow.Choice("renderer", "Renderer", Icons.Filled.DesktopWindows, if (waylandGame) listOf(rendShown) else listOf("Vulkan", "EGL", "SurfaceDirect"), rendShown,
        disabledReason = if (waylandGame) "Wayland draws through its own compositor" else null) { v ->
        xmb.set(p, "renderer", v.lowercase())
    }
    if (!waylandGame && rend == "Vulkan") {
        val native = p.ex("native", if (c.isRendererNative()) "true" else "false") == "true"
        rows += XmbRow.Toggle("vkNative", "Native renderer", Icons.Filled.DesktopWindows, native) { xmb.set(p, "native", if (it) "true" else "false") }
        val swap = p.ex("swapRB", if (c.getRendererSwapRB()) "true" else "false") == "true"
        rows += XmbRow.Choice("vkColors", "Colors", Icons.Filled.Image, listOf("BGRA", "RGBA"), if (swap) "RGBA" else "BGRA") { v ->
            xmb.set(p, "swapRB", if (v == "RGBA") "true" else "false")
        }
        val pmValues = listOf("fifo", "mailbox", "immediate")
        val pmLabels = listOf(p.str(R.string.renderer_present_mode_fifo), p.str(R.string.renderer_present_mode_mailbox), p.str(R.string.renderer_present_mode_immediate))
        val pm = p.ex("presentMode", c.getRendererPresentMode())
        rows += XmbRow.Choice("vkPresent", "Present mode", Icons.Filled.Speed, pmLabels, pmLabels[pmValues.indexOf(pm).coerceAtLeast(0)],
            disabledReason = if (native) "Set by the native renderer" else null) { v -> xmb.set(p, "presentMode", pmValues[pmLabels.indexOf(v)]) }
        val drivers = (listOf("system") + runCatching { com.winhub.hernandez.contents.AdrenotoolsManager(p.context).enumarateInstalledDrivers().toList() }.getOrDefault(emptyList())).distinct()
        val rd = p.ex("rendererDriverId", c.getRendererDriverId())
        rows += XmbRow.Choice("vkDriver", "Renderer driver", Icons.Filled.Memory, drivers, if (rd in drivers) rd else "system",
            subtitle = "The driver the compositor runs on") { v -> xmb.set(p, "rendererDriverId", v) }
    }
    // Renderer sub-options are X11-only; hidden on Wayland like the container/shortcut editors do
    // (their stored values are untouched and come back with the X11 backend).
    if (!waylandGame && rend == "SurfaceDirect") {
        fun sdFlag(key: String, def: Boolean) = p.ex(key, if (def) "true" else "false") == "true"
        rows += XmbRow.Toggle("sdPerf", "Performance mode", Icons.Filled.Speed, sdFlag("surfaceDirectPerformanceMode", c.isSurfaceDirectPerformanceMode())) {
            xmb.set(p, "surfaceDirectPerformanceMode", if (it) "true" else "false")
        }
        rows += XmbRow.Toggle("sdRefresh", "Present at refresh rate", Icons.Filled.Speed, sdFlag("surfaceDirectPresentAtRefreshRate", c.isSurfaceDirectPresentAtRefreshRate())) {
            xmb.set(p, "surfaceDirectPresentAtRefreshRate", if (it) "true" else "false")
        }
        rows += XmbRow.Toggle("sdBackPressure", "Submit every buffer", Icons.Filled.Speed, sdFlag("surfaceDirectBackPressure", c.isSurfaceDirectBackPressure())) {
            xmb.set(p, "surfaceDirectBackPressure", if (it) "true" else "false")
        }
        rows += XmbRow.Toggle("sdPrecise", "Precise presentation", Icons.Filled.Speed, sdFlag("surfaceDirectPrecisePresentation", c.isSurfaceDirectPrecisePresentation())) {
            xmb.set(p, "surfaceDirectPrecisePresentation", if (it) "true" else "false")
        }
    }
    // OpenGL (per game): each one is Auto (follow the container) / On / Off. Fast is locked to Auto,
    // with the reason shown, when the game's driver or this build can't run it.
    run {
        val fpsSw = com.winhub.hernandez.core.FastOpenGL.FPS_HUD
        val vsyncSw = com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF
        val driverVersion = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
            .getVersion(p.ex("graphicsDriverConfig", c.getGraphicsDriverConfig())) ?: ""
        val fastLocked = if (waylandGame) com.winhub.hernandez.core.FastOpenGL.WAYLAND_ALWAYS_ON
            else com.winhub.hernandez.core.FastOpenGL.unavailableReason(
                com.winhub.hernandez.core.FastOpenGL.driverUsable(p.context, driverVersion),
                com.winhub.hernandez.core.X11Egl.isBundled(p.context))
        val values = com.winhub.hernandez.ui.components.OPENGL_OVERRIDES
        fun glRow(key: String, title: String, icon: ImageVector, extra: String, autoOn: Boolean, hint: String, locked: String? = null) {
            val labels = values.map { if (it.isEmpty()) "Auto (" + (if (autoOn) "On" else "Off") + ")" else if (it == "1") "On" else "Off" }
            val cur = if (locked != null) "" else com.winhub.hernandez.core.FastOpenGL.normalize(p.ex(extra, "")) ?: ""
            rows += XmbRow.Choice(key, title, icon, labels, labels[values.indexOf(cur).coerceAtLeast(0)],
                subtitle = hint,
                disabledReason = com.winhub.hernandez.ui.components.openGLFastNote(locked)?.replaceFirstChar { it.uppercase() }) { v ->
                xmb.set(p, extra, values[labels.indexOf(v)].ifEmpty { null })
            }
        }
        rows += XmbRow.Header("hGl", "OpenGL")
        glRow("fastOpenGL", com.winhub.hernandez.core.FastOpenGL.TITLE, com.winhub.hernandez.ui.components.OPENGL_FAST_ICON,
            com.winhub.hernandez.core.FastOpenGL.EXTRA, c.isFastOpenGL(), com.winhub.hernandez.core.FastOpenGL.HINT, fastLocked)
        glRow("glFpsHud", fpsSw.title, com.winhub.hernandez.ui.components.OPENGL_FPS_ICON, fpsSw.extra,
            fpsSw.containerOn(c.getExtra(fpsSw.extra)), fpsSw.hint)
        glRow("glVsyncOff", vsyncSw.title, com.winhub.hernandez.ui.components.OPENGL_VSYNC_ICON, vsyncSw.extra,
            vsyncSw.containerOn(c.getExtra(vsyncSw.extra)), vsyncSw.hint)
    }
    val fgEngines = listOf("off", "lsfg-native")
    val fgLabels = listOf(p.str(R.string.frame_generation_off), p.str(R.string.frame_generation_lsfg_native))
    val fg = p.ex("frameGenEngine", c.frameGenEngine).let { if (it == "bionic" || it == "lsfg") "lsfg-native" else it }
    val lsfgDll = File(p.context.filesDir, "lsfg-vk/Lossless.dll").isFile
    // On Wayland the X11 renderer gate does not apply: FG runs inside the compositor (always Vulkan)
    // and the in-game drawer arms the engine picked here.
    val fgShown = fgLabels[fgEngines.indexOf(fg).coerceAtLeast(0)]
    rows += XmbRow.Header("hFg", "Frame generation")
    rows += XmbRow.Choice("frameGen", "Frame generation", Icons.Filled.Speed, fgLabels, fgShown,
        subtitle = if (!lsfgDll) "Import a Lossless.dll in Settings to enable LSFG" else null,
        disabledReason = when {
            !waylandGame && rend != "Vulkan" -> "Frame generation requires the Vulkan renderer"
            else -> null
        },
        disabledOptions = if (lsfgDll) emptySet() else setOf(fgLabels[1])) { v ->
            val picked = fgEngines[fgLabels.indexOf(v)]
            // Same override-when-different rule as the pop-up editor: only a real deviation from
            // the container is an override; matching it clears the extra instead of cementing it.
            // "Off" means inherit too, so it clears rather than pinning the game off.
            if (picked != "off" && picked != c.frameGenEngine) {
                xmb.set(p, "frameGenEngine", picked)
            } else {
                xmb.set(p, "frameGenEngine", null)
            }
            xmb.set(p, "frameGenEngineExplicit", null)
        }
    rows += XmbRow.Toggle("fpsLimiter", "FPS limiter", Icons.Filled.Speed,
        p.ex("fpsLimiterEnabled", if (c.isFpsLimiterEnabled) "1" else "0") == "1") { xmb.set(p, "fpsLimiterEnabled", if (it) "1" else "0") }
    rows += XmbRow.Link("perf", "Performance", Icons.Filled.Speed, subtitle = "Sustained performance, priority, big cores") { xmbPerformanceMenu(xmb, s) }

    // Audio & system
    rows += XmbRow.Header("hAudio", "Audio & system")
    val audioEntries = p.arr(R.array.audio_driver_entries)
    val daSupported = DirectAudioSupport.isSupported(c.wineVersion)
    var audioId = p.ex("audioDriver", c.audioDriver)
    if (audioId == "directaudio" && !daSupported) audioId = Container.DEFAULT_AUDIO_DRIVER
    val daEntry = audioEntries.firstOrNull { StringUtils.parseIdentifier(it) == "directaudio" }
    rows += XmbRow.Choice("audio", p.str(R.string.audio_driver), Icons.Filled.VolumeUp, audioEntries, p.labelFor(audioEntries, audioId),
        subtitle = if (!daSupported && daEntry != null) "DirectAudio requires Proton ${DirectAudioSupport.SUPPORTED_LABEL}" else null,
        disabledOptions = if (!daSupported && daEntry != null) setOf(daEntry) else emptySet(),
        confirm = { v -> if (StringUtils.parseIdentifier(v) == "directaudio") XmbConfirm("DirectAudio", "DirectAudio is experimental. Use it for this game?", "Use DirectAudio") else null }) { v ->
        xmb.set(p, "audioDriver", StringUtils.parseIdentifier(v))
    }
    if (audioId == "pulseaudio" || audioId == "alsa" || audioId == "directaudio") {
        rows += XmbRow.Link("audioSettings", "Audio settings", Icons.Filled.Tune, subtitle = "Latency presets and fine-tuning") { audioMenu(xmb, p, audioId) }
    }
    val micActive = audioId == "directaudio" && daSupported
    val env = p.ex("envVars", "")
    rows += XmbRow.Toggle("mic", "Microphone", Icons.Filled.Mic, micActive && DirectAudioSupport.isMicEnabledInEnv(env),
        subtitle = "Let games use the mic (DirectAudio captures input)",
        disabledReason = if (micActive) null else "Available on the DirectAudio driver") { want ->
        if (want && ContextCompat.checkSelfPermission(p.context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(p.context, "Allow the microphone permission for WinHub first (Android settings).", Toast.LENGTH_LONG).show()
        } else {
            xmb.set(p, "envVars", DirectAudioSupport.withMicEnabled(p.ex("envVars", ""), want).ifEmpty { null })
        }
    }
    val emuEntries = p.arr(R.array.emulator_entries)
    val emuId = p.ex("emulator", c.emulator)
    val arm = p.arm64ec
    rows += XmbRow.Choice("emulator", "Emulator", Icons.Filled.Memory,
        emuEntries.map { if (arm == true && StringUtils.parseIdentifier(it) == "box64") "WOWBox64" else it }.let { it },
        p.labelFor(emuEntries, emuId).let { if (arm == true && StringUtils.parseIdentifier(it) == "box64") "WOWBox64" else it },
        disabledReason = when (arm) { null -> "Checking the Wine build…"; false -> "Only arm64ec containers can switch"; else -> null }) { v ->
        val idx = emuEntries.indexOfFirst { it == v || (v == "WOWBox64" && StringUtils.parseIdentifier(it) == "box64") }
        if (idx >= 0) xmb.set(p, "emulator", StringUtils.parseIdentifier(emuEntries[idx]))
    }
    val midi = p.midiList
    if (midi.isNotEmpty()) {
        val curMidi = p.ex("midiSoundFont", c.getMIDISoundFont())
        rows += XmbRow.Choice("midi", "MIDI sound font", Icons.Filled.VolumeUp, midi, if (curMidi.isEmpty()) midi.first() else (midi.firstOrNull { it == curMidi } ?: midi.first())) { v ->
            xmb.set(p, "midiSoundFont", if (v == midi.first()) null else v)
        }
    }
    rows += XmbRow.Text("lcAll", "LC_ALL", Icons.Filled.ShortText, p.ex("lc_all", c.getLC_ALL()), subtitle = "Locale the game runs with") { xmb.set(p, "lc_all", it) }
    rows += XmbRow.Toggle("autoClose", "Close when game exits", Icons.Filled.Close,
        p.ex("autoCloseOnExit", c.getExtra("autoCloseOnExit", "1") ?: "1") == "1") { xmb.set(p, "autoCloseOnExit", if (it) "1" else "0") }
    return rows
}

/** Panel refresh rates, same source as the editor (drives whether the refresh row exists). */
private fun xmbPanelRates(context: Context): List<Int> = runCatching {
    val display = if (android.os.Build.VERSION.SDK_INT >= 30) context.display
        else (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
    com.winhub.hernandez.widget.XServerView.getSupportedRefreshRates(display).toList()
}.getOrDefault(emptyList())

/** Rename = the Game Details path (moves .desktop/.lnk + icon + cover). The shortcut object is stale
 *  afterwards, so go back to the games bar and reload the list. */
private fun renameAndReturn(xmb: XmbScope, s: Shortcut, newBase: String) {
    val container = s.container
    val oldBase = s.name
    xmb.scope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { ExeShortcutImporter.renameShortcutFiles(container, oldBase, newBase) }.getOrDefault(false) }
        xmb.toast(if (ok) "Renamed to “$newBase”" else "Couldn't rename — a game with that name may already exist")
        if (ok) { xmb.shortcutRenamed(container, oldBase, newBase); xmb.popToRoot(); xmb.reloadGames() }
    }
}

private fun iconPickerMenu(xmb: XmbScope, s: Shortcut): XmbMenu {
    val start = File(android.os.Environment.getExternalStorageDirectory(), "Pictures").takeIf { it.isDirectory }
        ?: android.os.Environment.getExternalStorageDirectory()
    val roots = listOf(
        "Pictures" to File(android.os.Environment.getExternalStorageDirectory(), "Pictures"),
        "Download" to File(android.os.Environment.getExternalStorageDirectory(), "Download"),
        "Internal storage" to android.os.Environment.getExternalStorageDirectory(),
    ).filter { it.second.isDirectory }
    val imageExt = setOf("png", "jpg", "jpeg", "webp", "bmp")
    return xmbFileBrowserMenu(xmb, "Select icon image", start, roots, { it.extension.lowercase() in imageExt }) { f ->
        xmb.scope.launch {
            val ok = withContext(Dispatchers.IO) { xmbApplyIconFile(s, f) }
            xmb.toast(if (ok) "Icon updated." else "Couldn't read that image.")
            if (ok) { xmb.pop(); xmb.reloadGames() }
        }
    }
}

/** Decodes an image file and writes it as the game's icon PNG, the way the editor's icon picker does. */
internal fun xmbApplyIconFile(s: Shortcut, f: File): Boolean = runCatching {
    val bitmap = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: return false
    s.iconFile?.let { out ->
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    s.icon = bitmap
    true
}.getOrDefault(false)

// ── Controller ──────────────────────────────────────────────────────────────────────────────────────

internal fun xmbControllerMenu(xmb: XmbScope, p: XmbPrefs): XmbMenu =
    XmbMenu("Controller", Icons.Filled.Gamepad) { controllerRows(this, p) }

private fun controllerRows(xmb: XmbScope, p: XmbPrefs): List<XmbRow> {
    val s = p.shortcut
    val c = p.c
    val rows = mutableListOf<XmbRow>()
    // "DirectInput games can see the controller" replaced Enable XInput / Enable DInput / Exclusive Input
    // (core.DInputVisibility); the game's own value is stored as the same inputType / exclusiveXInput pair.
    rows += XmbRow.Header("hInput", "Input")
    val visLabels = listOf("Use container default (${if (DInputVisibility.forContainer(c)) "On" else "Off"})", "On", "Off")
    val visCur = if (!DInputVisibility.gameOwns(s)) 0 else if (DInputVisibility.forGame(c, s)) 1 else 2
    rows += XmbRow.Choice("dinputVisible", p.str(R.string.dinput_visible), Icons.Filled.Gamepad, visLabels, visLabels[visCur]) { v ->
        val i = visLabels.indexOf(v).coerceAtLeast(0)
        if (i == 0) {
            s.putExtra(DInputVisibility.EXTRA_EXCLUSIVE, null)
            s.putExtra(DInputVisibility.EXTRA_INPUT_TYPE, null)
        } else {
            s.putExtra(DInputVisibility.EXTRA_EXCLUSIVE, "1")
            s.putExtra(DInputVisibility.EXTRA_INPUT_TYPE, DInputVisibility.inputTypeFor(i == 1).toString())
        }
        xmb.saved(); s.saveData(); xmb.refresh()
    }
    rows += XmbRow.Toggle("disableX", "Disable XInput", Icons.Filled.Gamepad, p.ex("disableXinput", "0") == "1") { xmb.set(p, "disableXinput", if (it) "1" else null) }
    // Direct XInput, vibration, controller type and stick updates: the container's until this game picks its own.
    fun inherit(what: String) = "Use container default ($what)"
    fun boolRow(id: String, title: String, extra: String, containerOn: Boolean) {
        val labels = listOf(inherit(if (containerOn) "On" else "Off"), "On", "Off")
        val values = listOf("", "1", "0")
        rows += XmbRow.Choice(id, title, Icons.Filled.Gamepad, labels, labels[values.indexOf(p.ex(extra, "")).coerceAtLeast(0)]) { v ->
            xmb.set(p, extra, values[labels.indexOf(v).coerceAtLeast(0)].ifEmpty { null })
        }
    }
    boolRow("hermesXInput", p.str(R.string.hermes_xinput), HermesXInput.EXTRA_KEY, c.isHermesXInput)
    boolRow("hermesSwitching", p.str(R.string.hermes_xinput_switching), HermesXInput.EXTRA_SWITCHING, c.isHermesXInputSwitching)
    boolRow("dinputVibration", p.str(R.string.dinput_vibration), DInputVibration.EXTRA, DInputVibration.forContainer(c))
    val idChoices = ControllerIdentity.wineChoices()
    val idLabels = listOf(inherit(ControllerIdentity.label(c.controllerIdentity))) + idChoices.map { it.label }
    val idCur = p.ex(ControllerIdentity.EXTRA, "")
    rows += XmbRow.Choice("controllerIdentity", p.str(R.string.controller_identity_label), Icons.Filled.Gamepad, idLabels,
        idLabels[(idChoices.indexOfFirst { it.value == idCur } + 1).coerceAtLeast(0)]) { v ->
        val i = idLabels.indexOf(v) - 1
        xmb.set(p, ControllerIdentity.EXTRA, if (i < 0) null else idChoices[i].value)
    }
    fun pacingLabel(pc: StickPacing) = when (pc.mode) {
        StickPacing.MODE_AUTO -> "Auto, up to ${pc.hz} per second"
        StickPacing.MODE_FIXED -> "Fixed, ${pc.hz} per second"
        else -> "Unpaced"
    }
    val pacingValues = mutableListOf("auto:60", "auto:90", "auto:120", "fixed:125", "fixed:250", "fixed:500", "off")
    val pacingCur = p.ex(StickPacing.EXTRA, "")
    if (pacingCur.isNotEmpty() && pacingCur !in pacingValues && StickPacing.parse(pacingCur) != null) pacingValues.add(pacingCur)
    val pacingLabels = listOf(inherit(pacingLabel(StickPacing.forContainer(c)))) + pacingValues.map { pacingLabel(StickPacing.parse(it)!!) }
    rows += XmbRow.Choice("stickPacing", p.str(R.string.stick_pacing_label), Icons.Filled.Gamepad, pacingLabels,
        pacingLabels[(pacingValues.indexOf(pacingCur) + 1).coerceAtLeast(0)]) { v ->
        val i = pacingLabels.indexOf(v) - 1
        xmb.set(p, StickPacing.EXTRA, if (i < 0) null else pacingValues[i])
    }
    rows += XmbRow.Toggle("simTouch", "Touchscreen mode", Icons.Filled.TouchApp, p.ex("simTouchScreen", "0") == "1") { xmb.set(p, "simTouchScreen", if (it) "1" else "0") }
    val nc = p.arr(R.array.num_controllers_entries)
    val ncIdx = ((p.ex("numControllers", "1").toIntOrNull() ?: 1).coerceIn(1, nc.size)) - 1
    rows += XmbRow.Choice("numControllers", "Number of controllers", Icons.Filled.Gamepad, nc, nc.getOrElse(ncIdx) { nc.first() }) { v ->
        xmb.set(p, "numControllers", (nc.indexOf(v) + 1).coerceAtLeast(1).toString())
    }
    val ahValues = listOf("", "1", "0")
    val ahLabels = listOf("Use container default", "On", "Off")
    val ah = p.ex("autoHideControlsOnPad", "")
    rows += XmbRow.Choice("autoHide", "Hide on-screen controls with a pad", Icons.Filled.TouchApp, ahLabels, ahLabels[ahValues.indexOf(ah).coerceAtLeast(0)],
        subtitle = "When a controller connects") { v -> xmb.set(p, "autoHideControlsOnPad", ahValues[ahLabels.indexOf(v)].ifEmpty { null }) }
    rows += XmbRow.Link("playerSlots", "Player slots", Icons.Filled.Gamepad,
        value = if ((s.getExtra("controllerSlotOverrides") ?: "").isEmpty()) "Container default" else "Custom",
        subtitle = "Pin controllers to players") { xmbPlayerSlotsMenu(xmb, s) }

    // Motion aim (all eight extras are independent overrides; no inherit state, as in the editor)
    rows += XmbRow.Header("hGyro", "Motion aim")
    val gyroOn = p.ex("gyroEnabled", if (c.isGyroEnabled) "1" else "0") == "1"
    rows += XmbRow.Toggle("gyro", "Enable motion aim", Icons.Filled.ScreenRotation, gyroOn) { xmb.set(p, "gyroEnabled", if (it) "1" else "0") }
    if (gyroOn) {
        val modeL = listOf(p.str(R.string.gyro_mode_rate), p.str(R.string.gyro_mode_orientation))
        val targetL = listOf(p.str(R.string.gyro_target_right_stick), p.str(R.string.gyro_target_left_stick), p.str(R.string.gyro_target_mouse))
        val actL = listOf(p.str(R.string.gyro_activator_l1), p.str(R.string.gyro_activator_l2), p.str(R.string.gyro_activator_r1), p.str(R.string.gyro_activator_r3), p.str(R.string.gyro_activator_always))
        val actModeL = listOf(p.str(R.string.gyro_activation_hold), p.str(R.string.gyro_activation_toggle))
        fun gi(key: String, cv: Int, def: Int) = p.ex(key, cv.toString()).toIntOrNull() ?: def
        val mode = gi("gyroMode", c.gyroMode, Container.GYRO_MODE_DEFAULT)
        val target = gi("gyroTarget", c.gyroTarget, Container.GYRO_TARGET_DEFAULT)
        val act = gi("gyroActivator", c.gyroActivator, Container.GYRO_ACTIVATOR_DEFAULT)
        val actMode = gi("gyroActivationMode", c.gyroActivationMode, Container.GYRO_ACTIVATION_MODE_DEFAULT)
        rows += XmbRow.Choice("gyroMode", "Motion mode", Icons.Filled.ScreenRotation, modeL, modeL.getOrElse(mode) { modeL[0] }) { v ->
            val nm = modeL.indexOf(v).coerceAtLeast(0)
            if (nm == Container.GYRO_MODE_ORIENTATION && target == Container.GYRO_TARGET_MOUSE) s.putExtra("gyroTarget", Container.GYRO_TARGET_RIGHT_STICK.toString())
            xmb.set(p, "gyroMode", nm.toString())
        }
        rows += XmbRow.Choice("gyroTarget", "Motion target", Icons.Filled.ScreenRotation, targetL, targetL.getOrElse(target) { targetL[0] }) { v ->
            val nt = targetL.indexOf(v).coerceAtLeast(0)
            if (nt == Container.GYRO_TARGET_MOUSE) s.putExtra("gyroMode", Container.GYRO_MODE_RATE.toString())
            xmb.set(p, "gyroTarget", nt.toString())
        }
        rows += XmbRow.Choice("gyroActivator", "Activator button", Icons.Filled.Gamepad, actL, actL.getOrElse(act) { actL[0] }) { v ->
            xmb.set(p, "gyroActivator", actL.indexOf(v).coerceAtLeast(0).toString())
        }
        if (act != Container.GYRO_ACTIVATOR_ALWAYS) {
            rows += XmbRow.Choice("gyroActMode", "Activation", Icons.Filled.Gamepad, actModeL, actModeL.getOrElse(actMode) { actModeL[0] }) { v ->
                xmb.set(p, "gyroActivationMode", actModeL.indexOf(v).coerceAtLeast(0).toString())
            }
        }
        val sens = p.ex("gyroSensitivity", c.gyroSensitivity.toString()).toFloatOrNull() ?: Container.GYRO_SENSITIVITY_DEFAULT
        rows += XmbRow.Slider("gyroSens", "Sensitivity", Icons.Filled.Tune, sens, 0.1f, 10f, 0.5f) { xmb.set(p, "gyroSensitivity", it.toString()) }
        rows += XmbRow.Toggle("gyroInvX", p.str(R.string.gyro_invert_x), Icons.Filled.ScreenRotation,
            p.ex("gyroInvertX", if (c.isGyroInvertX) "1" else "0") == "1") { xmb.set(p, "gyroInvertX", if (it) "1" else "0") }
        rows += XmbRow.Toggle("gyroInvY", p.str(R.string.gyro_invert_y), Icons.Filled.ScreenRotation,
            p.ex("gyroInvertY", if (c.isGyroInvertY) "1" else "0") == "1") { xmb.set(p, "gyroInvertY", if (it) "1" else "0") }
    }

    // Stick deadzone / sensitivity: the same five extras as the editor's "Override container sticks".
    // Each row writes its own key (the rest keep inheriting the container); the last row drops them all.
    rows += XmbRow.Header("hSticks", p.str(R.string.stick_tuning_title))
    val stickKeys = listOf(
        StickShaper.KEY_LEFT_DEADZONE, StickShaper.KEY_LEFT_SENSITIVITY, StickShaper.KEY_RIGHT_DEADZONE,
        StickShaper.KEY_RIGHT_SENSITIVITY, StickShaper.KEY_SMOOTH_EDGE,
    )
    rows += XmbRow.Slider("stickLDz", p.str(R.string.stick_left_deadzone), Icons.Filled.Tune,
        StickShaper.parseDeadzone(p.ex(StickShaper.KEY_LEFT_DEADZONE, ""), c.stickLeftDeadzone),
        StickShaper.DEADZONE_MIN, StickShaper.DEADZONE_MAX, 0.01f, format = { stickDeadzoneText(it) },
        subtitle = p.str(R.string.stick_tuning_hint)) { xmb.set(p, StickShaper.KEY_LEFT_DEADZONE, stickExtraText(it)) }
    rows += XmbRow.Slider("stickLSens", p.str(R.string.stick_left_sensitivity), Icons.Filled.Tune,
        StickShaper.parseSensitivity(p.ex(StickShaper.KEY_LEFT_SENSITIVITY, ""), c.stickLeftSensitivity),
        StickShaper.SENSITIVITY_MIN, StickShaper.SENSITIVITY_MAX, 0.05f, format = { stickSensitivityText(it) }) {
        xmb.set(p, StickShaper.KEY_LEFT_SENSITIVITY, stickExtraText(it))
    }
    rows += XmbRow.Slider("stickRDz", p.str(R.string.stick_right_deadzone), Icons.Filled.Tune,
        StickShaper.parseDeadzone(p.ex(StickShaper.KEY_RIGHT_DEADZONE, ""), c.stickRightDeadzone),
        StickShaper.DEADZONE_MIN, StickShaper.DEADZONE_MAX, 0.01f, format = { stickDeadzoneText(it) }) {
        xmb.set(p, StickShaper.KEY_RIGHT_DEADZONE, stickExtraText(it))
    }
    rows += XmbRow.Slider("stickRSens", p.str(R.string.stick_right_sensitivity), Icons.Filled.Tune,
        StickShaper.parseSensitivity(p.ex(StickShaper.KEY_RIGHT_SENSITIVITY, ""), c.stickRightSensitivity),
        StickShaper.SENSITIVITY_MIN, StickShaper.SENSITIVITY_MAX, 0.05f, format = { stickSensitivityText(it) }) {
        xmb.set(p, StickShaper.KEY_RIGHT_SENSITIVITY, stickExtraText(it))
    }
    rows += XmbRow.Toggle("stickSmooth", p.str(R.string.stick_smooth_edge), Icons.Filled.Tune,
        StickShaper.parseSmoothEdge(p.ex(StickShaper.KEY_SMOOTH_EDGE, ""), c.isStickSmoothEdge),
        subtitle = p.str(R.string.stick_smooth_edge_hint)) { xmb.set(p, StickShaper.KEY_SMOOTH_EDGE, if (it) "1" else "0") }
    if (stickKeys.any { p.shortcut.hasExtra(it) }) {
        rows += XmbRow.Action("stickInherit", p.str(R.string.stick_tuning_inherit), Icons.Filled.Tune,
            subtitle = p.str(R.string.stick_tuning_inherit_sub)) {
            stickKeys.forEach { p.put(it, null) }
            xmb.saved()
            xmb.refresh()
        }
    }
    return rows
}

// ── Audio settings (the editor's per-game AudioSettingsDialog, as XMB rows; stored in envVars) ──────

private fun audioMenu(xmb: XmbScope, p: XmbPrefs, driverId: String): XmbMenu = XmbMenu("Audio settings", Icons.Filled.VolumeUp) {
    val env = p.ex("envVars", "")
    val cfg = audioConfigFromEnv(env, driverId)
    val direct = latencyIsDirectBuffer(driverId)
    val used = latencyUsedBy(driverId)
    val custom = cfg.preset == PRESET_CUSTOM
    fun write(nc: com.winhub.hernandez.ui.components.AudioConfig) =
        xmb.set(p, "envVars", audioConfigToEnv(p.ex("envVars", ""), nc, driverId).ifEmpty { null })
    val presetNames = AUDIO_PRESETS.map { it.name }
    val cur = AUDIO_PRESETS.firstOrNull { it.id == cfg.preset } ?: AUDIO_PRESETS.first()
    val rows = mutableListOf<XmbRow>()
    rows += XmbRow.Choice("preset", "Preset", Icons.Filled.Tune, presetNames, cur.name, subtitle = cur.desc) { v ->
        val pr = AUDIO_PRESETS.first { it.name == v }
        write(if (direct) pr.cfg.copy(latencyMsec = cfg.latencyMsec) else pr.cfg)
    }
    val lockReason = if (custom) null else "Pick the Custom preset to change this"
    val perfOpts = if (driverId == "directaudio") listOf("None" to 0, "Low latency" to 1) else listOf("None" to 0, "Low latency" to 1, "Power saving" to 2)
    rows += XmbRow.Choice("perf", "Output mode", Icons.Filled.Speed, perfOpts.map { it.first },
        perfOpts.firstOrNull { it.second == cfg.perfMode }?.first ?: perfOpts.first().first, subtitle = "AAudio performance mode", disabledReason = lockReason) { v ->
        write(cfg.copy(perfMode = perfOpts.first { it.first == v }.second))
    }
    rows += XmbRow.Toggle("adaptive", "Adaptive buffer", Icons.Filled.Tune, cfg.adaptive, subtitle = "Auto-grow only if it hears crackle", disabledReason = lockReason) { write(cfg.copy(adaptive = it)) }
    rows += XmbRow.Slider("latency", if (direct) "Audio buffer" else "Guest buffer", Icons.Filled.Timer, cfg.latencyMsec.toFloat(),
        if (direct) 4f else 20f, if (direct) 120f else 200f, if (direct) 4f else 10f, format = { "${it.toInt()} ms" },
        subtitle = if (!used) "Not used by ALSA — set the buffer rows below" else null,
        disabledReason = lockReason ?: if (!used) "Not used by ALSA" else null) { write(cfg.copy(latencyMsec = it.toInt())) }
    rows += XmbRow.Slider("bf", "Initial sink buffer", Icons.Filled.Tune, cfg.bufferFrames.toFloat(), 0f, 8192f, 256f,
        format = { if (it <= 0f) "auto" else "${it.toInt()} frames" }, disabledReason = lockReason) { write(cfg.copy(bufferFrames = it.toInt())) }
    rows += XmbRow.Slider("mbf", "Max sink buffer", Icons.Filled.Tune, cfg.maxBufferFrames.toFloat(), 0f, 16384f, 256f,
        format = { if (it <= 0f) "device" else "${it.toInt()} frames" }, disabledReason = lockReason) { write(cfg.copy(maxBufferFrames = it.toInt())) }
    rows
}

// ── Game Details ────────────────────────────────────────────────────────────────────────────────────

internal fun xmbGameDetailsMenu(xmb: XmbScope, s: Shortcut): XmbMenu {
    var results by mutableStateOf<List<SteamStoreSearch.SteamSuggestion>>(emptyList())
    var searching by mutableStateOf(false)
    fun details(): GameDetails = GameDetails.from(s)
    fun write(d: GameDetails) { runCatching { d.writeTo(s) }; xmb.saved(); xmb.refresh() }
    // Opens on the name, not on "Unlink from Steam" (a stray A must not unlink).
    return XmbMenu("Game Details", Icons.Filled.Edit, initialKey = "name") {
        val d = details()
        val rows = mutableListOf<XmbRow>()
        d.steamAppId?.takeIf { it > 0 }?.let { id ->
            rows += XmbRow.Info("app", "Steam App ID", Icons.Filled.Star, id.toString())
            rows += XmbRow.Action("unlink", "Unlink from Steam", Icons.Filled.LinkOff) { write(d.copy(steamAppId = null)) }
        }
        rows += XmbRow.Text("name", "Game name", Icons.Filled.ShortText, s.name) { v ->
            val nb = v.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
            if (nb.isNotEmpty() && nb != s.name) renameAndReturn(xmb, s, nb)
        }
        rows += XmbRow.Link("search", "Search Steam", Icons.Filled.Search, subtitle = "Pick a result to fill every field") {
            results = emptyList(); searching = true
            val q = s.name
            xmb.scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { SteamStoreSearch.searchByName(q) }.getOrDefault(emptyList()) }
                results = r; searching = false
            }
            XmbMenu("Search Steam", Icons.Filled.Search) {
                when {
                    searching -> listOf(XmbRow.Info("busy", "Searching Steam…", Icons.Filled.Search))
                    results.isEmpty() -> listOf(XmbRow.Info("none", "No results found for “$q”", Icons.Filled.Search))
                    else -> results.mapIndexed { i, r ->
                        XmbRow.Action("r$i", r.name, Icons.Filled.Search, subtitle = "App ID: ${r.appId}") {
                            xmb.scope.launch {
                                xmb.toast("Filling from Steam…")
                                val info = withContext(Dispatchers.IO) { runCatching { SteamStoreSearch.fetchDetails(r.appId) }.getOrNull() }
                                val base = details()
                                val nd = if (info != null) GameDetails(
                                    steamAppId = r.appId, genres = info.genres, description = info.shortDescription,
                                    releaseYear = info.releaseYear, metacritic = info.metacritic,
                                ) else base.copy(steamAppId = r.appId)
                                withContext(Dispatchers.IO) { runCatching { nd.writeTo(s) }; runCatching { applySteamCover(s.container, s.name, r.appId) } }
                                xmb.saved(); xmb.pop(); xmb.reloadGames()
                                if (info != null && info.name.isNotBlank() && info.name != s.name) {
                                    val nb = info.name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
                                    if (nb.isNotEmpty()) renameAndReturn(xmb, s, nb)
                                }
                            }
                        }
                    }
                }
            }
        }
        rows += XmbRow.Text("genres", "Genres", Icons.Filled.Label, d.genres.joinToString(", "), subtitle = "Comma-separated", placeholder = "e.g. Action, RPG, Strategy") { v ->
            write(d.copy(genres = v.split(",").map { it.trim() }.filter { it.isNotBlank() }))
        }
        rows += XmbRow.Text("desc", "Description", Icons.Filled.Description, d.description ?: "", subtitle = "Shown on the launch screen") { v ->
            write(d.copy(description = v.trim().takeIf { it.isNotBlank() }))
        }
        rows += XmbRow.Text("year", "Release year", Icons.Filled.Event, d.releaseYear ?: "", numeric = true, placeholder = "e.g. 2023") { v ->
            write(d.copy(releaseYear = v.trim().take(4).takeIf { it.isNotBlank() }))
        }
        rows += XmbRow.Text("mc", "Metacritic", Icons.Filled.Star, d.metacritic?.toString() ?: "", subtitle = "1–100, leave blank to hide", numeric = true) { v ->
            write(d.copy(metacritic = v.trim().toIntOrNull()?.takeIf { it in 1..100 }))
        }
        rows
    }
}

// ── Properties ──────────────────────────────────────────────────────────────────────────────────────

// Opens on the play count, not on "Reset properties".
internal fun xmbPropertiesMenu(xmb: XmbScope, s: Shortcut): XmbMenu = XmbMenu("Properties", Icons.Filled.Info, initialKey = "plays") {
    val prefs = xmb.context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
    val tKey = "${s.name}_playtime"
    val cKey = "${s.name}_play_count"
    val totalMs = prefs.getLong(tKey, 0L)
    val sec = (totalMs / 1000) % 60
    val min = (totalMs / 60000) % 60
    val hr = (totalMs / 3600000) % 24
    val days = totalMs / 86400000
    listOf(
        XmbRow.Info("plays", "Number of times played", Icons.Filled.Star, prefs.getInt(cKey, 0).toString()),
        XmbRow.Info("time", "Playtime", Icons.Filled.Timer, String.format("%dd %02dh %02dm %02ds", days, hr, min, sec)),
        XmbRow.Action("reset", "Reset properties", Icons.Filled.Refresh, danger = true) {
            xmb.confirm(XmbConfirm("Reset properties", "Clear times played and playtime for “${s.name}”?", "Reset", danger = true)) {
                prefs.edit().remove(tKey).remove(cKey).apply()
                xmb.saved(); xmb.refresh()
            }
        },
    )
}

// ── Clone / Remove ──────────────────────────────────────────────────────────────────────────────────

internal fun xmbCloneMenu(xmb: XmbScope, s: Shortcut, host: XmbGameHost): XmbMenu = XmbMenu("Select container", Icons.Filled.ContentCopy) {
    host.containers().mapIndexed { i, c ->
        XmbRow.Action("c${c.id}_$i", c.name, Icons.Filled.Folder, subtitle = if (c.id == s.container.id) "Current container" else null) {
            val ok = runCatching { s.cloneToContainer(c) }.getOrDefault(false)
            xmb.toast(if (ok) "Shortcut cloned." else "Failed to clone shortcut.")
            xmb.popToRoot()
            if (ok) xmb.reloadGames()
        }
    }
}

internal fun xmbRemoveMenu(xmb: XmbScope, s: Shortcut, host: XmbGameHost): XmbMenu =
    XmbMenu("Remove shortcut?", Icons.Filled.Delete, initialKey = "cancel") {
        listOf(
            XmbRow.Info("msg", "Remove “${s.name}”?", Icons.Filled.Info),
            XmbRow.Action("remove", "Remove", Icons.Filled.Delete, danger = true) {
                val ok = host.remove(s)
                xmb.toast(if (ok) "Shortcut removed." else "Failed to remove shortcut.")
                xmb.popToRoot()
                xmb.reloadGames()
            },
            XmbRow.Action("cancel", "Cancel", Icons.Filled.Close) { xmb.pop() },
        )
    }
