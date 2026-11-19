package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.preference.PreferenceManager
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.core.AutoDefaults
import com.winhub.hernandez.core.StringUtils
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

        val drivers by produceState(initialValue = emptyList<Pair<String, String>>(), context) {
            value = withContext(Dispatchers.IO) { installedDriverChoices(context) }
        }
        val currentDriver = GraphicsDriverConfigDialog.getVersion(viewModel.graphicsDriverConfig) ?: ""
        DeckChoiceRow(
            label = "Graphics Driver",
            hint = "The Turnip build the game renders on",
            options = withCurrent(currentDriver, drivers),
            selected = currentDriver,
            onPick = { id ->
                if (viewModel.isWaylandBackend) viewModel.onCompositorDriverPicked(id)
                else viewModel.graphicsDriverConfig = withGraphicsDriverVersion(viewModel.graphicsDriverConfig, id)
            },
        )

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
    DeckGroup("View") { DeckViewModeRow() }
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

    fun write(key: String, value: String) {
        p.put(key, value)
        xmb.saved()
        xmb.refresh()
    }

    return XmbMenu("Settings", Icons.Filled.Settings) {
        val rows = mutableListOf<XmbRow>()
        rows += XmbRow.Header("hEssentials", "Essentials")
        rows += XmbRow.Info("sProton", "Proton", Icons.Filled.Tune, p.c.wineVersion, subtitle = "Set per container")

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
