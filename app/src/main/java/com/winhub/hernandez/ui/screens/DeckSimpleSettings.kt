package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.preference.PreferenceManager
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.core.AutoDefaults
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.WaylandAdapterSettings
import com.winhub.hernandez.display.WaylandHdr
import com.winhub.hernandez.contentdialog.DXVKConfigDialog
import com.winhub.hernandez.fexcore.FEXCorePresetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings view: Simple shows the four things most games need (Proton, DXVK, GPU driver, FEXCore
 * preset); Advanced is every setting, as it always was. One switch for the whole app, kept across runs.
 * Nothing is reset by switching: settings the Simple view hides keep their values.
 */
internal object DeckSettingsMode {
    private const val KEY = "deck_settings_advanced"
    private var state: MutableState<Boolean>? = null

    private fun state(context: Context): MutableState<Boolean> = state ?: mutableStateOf(
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext).getBoolean(KEY, false),
    ).also { state = it }

    fun advanced(context: Context): Boolean = state(context).value

    fun set(context: Context, advanced: Boolean) {
        state(context).value = advanced
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putBoolean(KEY, advanced).apply()
    }
}

/** The Simple / Advanced switch as a Deck row. */
@Composable
internal fun DeckViewModeRow() {
    val context = LocalContext.current
    DeckChoiceRow(
        label = "Settings view",
        hint = "Simple: Proton, DXVK, driver and FEXCore preset. Advanced: every setting.",
        options = listOf("simple" to "Simple", "advanced" to "Advanced"),
        selected = if (DeckSettingsMode.advanced(context)) "advanced" else "simple",
        onPick = { DeckSettingsMode.set(context, it == "advanced") },
    )
}

/** Simple | Advanced as two pills for a screen header; the one in use is filled. */
@Composable
internal fun DeckViewSwitch() {
    val context = LocalContext.current
    val advanced = DeckSettingsMode.advanced(context)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DeckButton("Simple", primary = !advanced) { DeckSettingsMode.set(context, false) }
        DeckButton("Advanced", primary = advanced) { DeckSettingsMode.set(context, true) }
    }
}

/** Installed adrenotools drivers as (id, label); a name two drivers share gets its id appended. Blocking (disk). */
private fun installedDriverChoices(context: Context): List<Pair<String, String>> {
    val manager = AdrenotoolsManager(context.applicationContext)
    val named = manager.enumarateInstalledDrivers().map { id -> id to manager.getDriverName(id).ifBlank { id } }
    val count = named.groupingBy { it.second }.eachCount()
    return named.map { (id, name) -> id to if ((count[name] ?: 0) > 1) "$name ($id)" else name }
}

/** The driver list with the value the config holds right now always present, so opening it never loses it. */
private fun withCurrent(current: String, choices: List<Pair<String, String>>): List<Pair<String, String>> =
    if (choices.any { it.first == current }) choices
    else listOf(current to current.ifEmpty { "Default" }) + choices

/** The Simple page of the container editor: the four settings, then the switch back to Advanced. */
@Composable
internal fun DeckSimpleContainerPage(viewModel: ContainerDetailViewModel) {
    val context = LocalContext.current
    DeckGroup("Essentials") {
        if (viewModel.wineVersionEnabled) {
            DeckChoiceRow(
                label = "Proton Version",
                hint = "The Proton layer this container runs on",
                options = viewModel.wineVersionEntries.map { it to it },
                selected = viewModel.selectedWineVersion,
                onPick = { viewModel.onWineVersionChanged(it) },
            )
        } else {
            DeckRow(label = "Proton Version", hint = "Chosen when the container was made") {
                Text(
                    viewModel.selectedWineVersion,
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        DeckChoiceRow(
            label = "Display Backend",
            hint = "Wayland routes the game through the embedded compositor",
            options = listOf(
                Container.DISPLAY_BACKEND_WAYLAND to "Wayland",
                Container.DISPLAY_BACKEND_X11 to "X11",
            ),
            selected = viewModel.displayBackend,
            onPick = { viewModel.onDisplayBackendChanged(it) },
        )
        if (viewModel.isWaylandStored && !viewModel.isWaylandBackend) {
            DeckRow(
                label = "Wayland is not available",
                hint = (viewModel.waylandLayerProblem() ?: "This Proton layer cannot run Wayland.") + " It will be saved as X11.",
                control = {},
            )
        }

        val wrapper = StringUtils.parseIdentifier(viewModel.selectedDXWrapper)
        if (wrapper.contains("dxvk") && !wrapper.contains("vegas")) {
            val choices = remember(viewModel.isArm64EC, viewModel.wineVersionEntries) { viewModel.dxvkVersionChoices() }
            val current = viewModel.dxvkVersion()
            DeckChoiceRow(
                label = "DXVK Version",
                hint = "Direct3D 9, 10 and 11 on Vulkan",
                options = (if (current.isNotEmpty() && current !in choices) listOf(current) + choices else choices).map { it to it },
                selected = current,
                onPick = { viewModel.setDxvkVersion(it) },
            )
        }

        // On Wayland the picker offers only drivers the compositor can use (a wrong pick is a black screen).
        val wayland = viewModel.isWaylandBackend
        val drivers by produceState(initialValue = emptyList<Pair<String, String>>(), context, wayland) {
            value = if (wayland) {
                val ids = compositorDriverChoices(context)
                withContext(Dispatchers.IO) {
                    val manager = AdrenotoolsManager(context.applicationContext)
                    ids.map { id -> id to manager.getDriverName(id).ifBlank { id } }
                }
            } else {
                withContext(Dispatchers.IO) { installedDriverChoices(context) }
            }
        }
        val currentDriver = GraphicsDriverConfigDialog.getVersion(viewModel.graphicsDriverConfig) ?: ""
        DeckChoiceRow(
            label = "Graphics Driver",
            hint = if (wayland && compositorDriverUnusable(currentDriver, drivers.map { it.first }, drivers.isNotEmpty()))
                "Wayland needs a Turnip driver here; \"System\" or a missing driver shows a black screen"
            else if (wayland) "The Turnip build the Wayland compositor and the game run on"
            else "The Turnip build the game renders on",
            options = withCurrent(currentDriver, drivers),
            selected = currentDriver,
            onPick = { id ->
                if (viewModel.isWaylandBackend) viewModel.onCompositorDriverPicked(id)
                else viewModel.graphicsDriverConfig = withGraphicsDriverVersion(viewModel.graphicsDriverConfig, id)
            },
        )

        if (wayland) {
            val profile = WaylandAdapterSettings.normalizeProfile(
                GraphicsDriverConfigDialog.parseGraphicsDriverConfig(viewModel.graphicsDriverConfig)[WaylandAdapterSettings.KEY_PROFILE],
            )
            DeckChoiceRow(
                label = "Wayland Profile",
                hint = WaylandAdapterSettings.profileHelp(profile),
                options = WaylandAdapterSettings.PROFILES.map { it to WaylandAdapterSettings.profileLabel(it) },
                selected = profile,
                onPick = {
                    viewModel.graphicsDriverConfig = withGraphicsDriverKeys(
                        viewModel.graphicsDriverConfig, mapOf(WaylandAdapterSettings.KEY_PROFILE to it),
                    )
                },
            )
            val hdrProblem = remember { WaylandHdr.unavailableReason(context) }
            if (hdrProblem == null) {
                DeckRow(
                    label = WaylandHdr.TITLE,
                    hint = "HDR10 output on a screen that supports it",
                    control = { DeckToggle(viewModel.waylandHdr) { viewModel.waylandHdr = it } },
                )
            }
        }

        if (viewModel.isArm64EC) {
            DeckChoiceRow(
                label = "FEXCore Preset",
                hint = "Speed against compatibility of the CPU translation",
                options = viewModel.fexCorePresetEntries.mapIndexed { i, e -> i.toString() to e },
                selected = viewModel.selectedFEXCorePresetIndex.toString(),
                onPick = { viewModel.selectedFEXCorePresetIndex = it.toInt() },
            )
        } else {
            DeckChoiceRow(
                label = "Box64 Preset",
                hint = "Speed against compatibility of the CPU translation",
                options = viewModel.box64PresetEntries.mapIndexed { i, e -> i.toString() to e },
                selected = viewModel.selectedBox64PresetIndex.toString(),
                onPick = { viewModel.selectedBox64PresetIndex = it.toInt() },
            )
        }
    }
}

/**
 * The per-game Simple menu (XMB): the same settings for one game. A game has no layer of its own (that
 * belongs to its container), so Proton is shown read-only. The shortcut's own extras are written, the same
 * keys the full editors write.
 */
internal fun xmbSimpleMenu(xmb: XmbScope, p: XmbPrefs): XmbMenu {
    var drivers: List<Pair<String, String>>? = null
    var dxvk: List<String>? = null
    xmb.scope.launch {
        val loaded = withContext(Dispatchers.IO) {
            val contents = ContentsManager(xmb.context).apply { syncContents() }
            val arm = runCatching { com.winhub.hernandez.core.WineInfo.fromIdentifier(xmb.context, contents, p.c.wineVersion).isArm64EC() }
                .getOrDefault(false)
            installedDriverChoices(xmb.context) to DXVKConfigDialog.loadDxvkVersionList(xmb.context, contents, arm)
        }
        drivers = loaded.first
        dxvk = loaded.second
        xmb.refresh()
    }

    fun write(key: String, value: String?) {
        p.put(key, value)
        xmb.saved()
        xmb.refresh()
    }

    return XmbMenu("Settings", Icons.Filled.Settings) {
        val rows = mutableListOf<XmbRow>()
        rows += XmbRow.Header("hEssentials", "Essentials")
        rows += XmbRow.Info("sProton", "Proton", Icons.Filled.Tune, p.c.wineVersion, subtitle = "Set per container")

        // Wayland: where the game runs, and (when it does) how its frames are paced.
        val waylandCapable = com.winhub.hernandez.core.WineWaylandSupport.isWaylandCapable(xmb.context, p.c.wineVersion)
        val dbOverride = p.ex("displayBackend", "")
        val containerWayland = p.c.isWaylandBackend && waylandCapable
        val dbValues = listOf("", Container.DISPLAY_BACKEND_X11, Container.DISPLAY_BACKEND_WAYLAND)
        val dbLabels = listOf("Container default (" + (if (containerWayland) "Wayland" else "X11") + ")", "X11", "Wayland")
        rows += XmbRow.Choice("sBackend", "Display backend", Icons.Filled.DesktopWindows, dbLabels,
            dbLabels[dbValues.indexOf(dbOverride).coerceAtLeast(0)],
            subtitle = if (waylandCapable) "Wayland routes the game through the embedded compositor"
                else "Wayland needs a layer with winewayland; this container's layer has none",
            disabledOptions = if (waylandCapable) emptySet() else setOf(dbLabels[2])) { v ->
            write("displayBackend", dbValues[dbLabels.indexOf(v)].ifEmpty { null })
        }
        val waylandGame = if (dbOverride.isEmpty()) containerWayland else dbOverride == Container.DISPLAY_BACKEND_WAYLAND && waylandCapable
        if (waylandGame) {
            val own = p.ex("graphicsDriverConfig", "")
            val stored = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(own)[WaylandAdapterSettings.KEY_PROFILE].orEmpty()
            val profileValues = listOf("") + WaylandAdapterSettings.PROFILES
            val profileLabels = listOf("Container default") + WaylandAdapterSettings.PROFILES.map { WaylandAdapterSettings.profileLabel(it) }
            rows += XmbRow.Choice("sWlProfile", "Wayland profile", Icons.Filled.Tune, profileLabels,
                profileLabels[profileValues.indexOf(stored).coerceAtLeast(0)],
                subtitle = "Smooth evens out frame pacing, Fast lowers latency, Compatibility is the safest") { v ->
                val value = profileValues[profileLabels.indexOf(v)]
                // The shortcut's own config starts from the container's, with the adapter keys cleared so only this one is set.
                val base = own.ifEmpty { withGraphicsDriverKeys(p.c.graphicsDriverConfig, WaylandAdapterSettings.KEYS.associateWith { "" }) }
                write("graphicsDriverConfig", withGraphicsDriverKeys(base, mapOf(WaylandAdapterSettings.KEY_PROFILE to value)))
            }
        }

        val dxCfg = p.ex("dxwrapperConfig", "").ifEmpty { p.c.getDXWrapperConfig() }
        val dxCurrent = AutoDefaults.dxvkVersionOf(dxCfg)
        val dxList = dxvk
        val wrapper = StringUtils.parseIdentifier(p.ex("dxwrapper", p.c.getDXWrapper()))
        if (dxList != null && wrapper.contains("dxvk") && !wrapper.contains("vegas")) {
            val shown = if (dxCurrent.isNotEmpty() && dxCurrent !in dxList) listOf(dxCurrent) + dxList else dxList
            rows += XmbRow.Choice("sDxvk", "DXVK version", Icons.Filled.DesktopWindows, shown, dxCurrent,
                subtitle = "Direct3D 9, 10 and 11 on Vulkan") { v ->
                write("dxwrapperConfig", AutoDefaults.withDxvkVersion(dxCfg, v))
            }
        }

        val gdCfg = p.ex("graphicsDriverConfig", "").ifEmpty { p.c.graphicsDriverConfig }
        val gdCurrent = GraphicsDriverConfigDialog.getVersion(gdCfg) ?: ""
        val driverList = drivers
        if (driverList != null) {
            val shown = withCurrent(gdCurrent, driverList)
            rows += XmbRow.Choice("sDriver", "Graphics driver", Icons.Filled.DesktopWindows, shown.map { it.second },
                shown.firstOrNull { it.first == gdCurrent }?.second ?: gdCurrent, subtitle = "The Turnip build the game renders on") { label ->
                shown.firstOrNull { it.second == label }?.let { write("graphicsDriverConfig", withGraphicsDriverVersion(gdCfg, it.first)) }
            }
        }

        if (p.arm64ec == true) {
            val presets = FEXCorePresetManager.getPresets(xmb.context)
            val id = p.ex("fexcorePreset", p.c.getFEXCorePreset())
            rows += XmbRow.Choice("sFex", "FEXCore preset", Icons.Filled.Memory, presets.map { it.name },
                presets.firstOrNull { it.id == id }?.name ?: id, subtitle = "Speed against compatibility of the CPU translation") { name ->
                presets.firstOrNull { it.name == name }?.let { write("fexcorePreset", it.id) }
            }
        }

        rows += XmbRow.Header("hView", "View")
        rows += XmbRow.Choice("sView", "Settings view", Icons.Filled.Settings, listOf("Simple", "Advanced"), "Simple",
            subtitle = "Advanced shows every setting. Reopen Settings after switching.") { v ->
            DeckSettingsMode.set(xmb.context, v == "Advanced")
            xmb.toast(if (v == "Advanced") "Advanced view. Reopen Settings." else "Simple view")
        }
        rows
    }
}
