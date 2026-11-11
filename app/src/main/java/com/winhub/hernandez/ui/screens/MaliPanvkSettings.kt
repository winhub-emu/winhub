package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.core.MaliPanvk

/**
 * The Wayland driver gear's "Mali (PanVK)" section, seeded from one graphicsDriverConfig. The mode
 * switch is stored only once the user touches it (absent = follow the driver's name, see MaliPanvk),
 * and with it bcnEmulation (the same key as the X11 driver configuration's BCn emulation). The three
 * adapter sub-switches (on by default) sit under Advanced while the mode is on. Empty /
 * Auto PANVK_* fields are stored empty and not exported; out-of-range input is stored empty too.
 */
@Stable
internal class MaliPanvkSettingsState(context: Context, cfg: Map<String, String>) {
    private val storedBcn = cfg["bcnEmulation"]?.ifEmpty { null } ?: "auto"
    private val derivedOn = MaliPanvk.looksLikePanvk(context, cfg["version"])
    /** "1" / "0" once chosen; null = follow the driver's name. */
    var modeOverride by mutableStateOf(cfg[MaliPanvk.KEY_MODE]?.ifEmpty { null })
    val modeOn: Boolean get() = modeOverride?.let { it == "1" } ?: derivedOn
    var bcnEmulation by mutableStateOf(if (cfg[MaliPanvk.KEY_MODE].isNullOrEmpty() && derivedOn) "none" else storedBcn)
    var heapMb by mutableStateOf(cfg[MaliPanvk.KEY_HEAP] ?: "")
    var tilerHeapMb by mutableStateOf(cfg[MaliPanvk.KEY_TILER_HEAP] ?: "")
    var polyHeapMb by mutableStateOf(cfg[MaliPanvk.KEY_POLY_HEAP] ?: "")
    var atomStride by mutableStateOf(cfg[MaliPanvk.KEY_ATOM_STRIDE]?.takeIf { it in MaliPanvk.ATOM_STRIDES } ?: "")
    var trace by mutableStateOf(cfg[MaliPanvk.KEY_TRACE] == "1")
    var hideExts by mutableStateOf(MaliPanvk.subOn(cfg, MaliPanvk.KEY_HIDE_EXTS))
    var noSubmitWaits by mutableStateOf(MaliPanvk.subOn(cfg, MaliPanvk.KEY_NO_SUBMIT_WAITS))
    var noAcquireSignal by mutableStateOf(MaliPanvk.subOn(cfg, MaliPanvk.KEY_NO_ACQUIRE_SIGNAL))

    fun setMode(on: Boolean) {
        modeOverride = if (on) "1" else "0"
        bcnEmulation = if (on) "none" else storedBcn
    }

    fun pickBcn(value: String) {
        if (modeOverride == null) modeOverride = "1" // pin the mode so launch keeps this choice
        bcnEmulation = value
    }

    /** The keys OK writes back (withGraphicsDriverKeys). */
    fun keys(): Map<String, String> = linkedMapOf<String, String>().apply {
        modeOverride?.let {
            put(MaliPanvk.KEY_MODE, it)
            put("bcnEmulation", bcnEmulation)
        }
        put(MaliPanvk.KEY_HIDE_EXTS, if (hideExts) "1" else "0")
        put(MaliPanvk.KEY_NO_SUBMIT_WAITS, if (noSubmitWaits) "1" else "0")
        put(MaliPanvk.KEY_NO_ACQUIRE_SIGNAL, if (noAcquireSignal) "1" else "0")
        put(MaliPanvk.KEY_HEAP, MaliPanvk.validMb(heapMb, MaliPanvk.HEAP_MIN, MaliPanvk.HEAP_MAX) ?: "")
        put(MaliPanvk.KEY_TILER_HEAP, MaliPanvk.validMb(tilerHeapMb, MaliPanvk.TILER_HEAP_MIN, MaliPanvk.TILER_HEAP_MAX) ?: "")
        put(MaliPanvk.KEY_POLY_HEAP, MaliPanvk.validMb(polyHeapMb, MaliPanvk.POLY_HEAP_MIN, MaliPanvk.POLY_HEAP_MAX) ?: "")
        put(MaliPanvk.KEY_ATOM_STRIDE, atomStride)
        put(MaliPanvk.KEY_TRACE, if (trace) "1" else "")
    }
}

@Composable
internal fun MaliPanvkSection(state: MaliPanvkSettingsState, onHelp: (Int) -> Unit) {
    val context = LocalContext.current
    val bcnEntries = remember { context.resources.getStringArray(R.array.bcn_emulation_entries).toList() }
    var advancedOpen by remember { mutableStateOf(false) }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.mali_panvk_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onHelp(R.string.help_mali_panvk) }) {
            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = state.modeOn, onCheckedChange = { state.setMode(it) })
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.mali_panvk_mode), modifier = Modifier.weight(1f))
    }
    Text(
        stringResource(R.string.mali_panvk_mode_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state.modeOn) {
        Spacer(Modifier.height(8.dp))
        LabeledDropdown(
            stringResource(R.string.graphics_driver_bcn_emulation), bcnEntries, state.bcnEmulation,
            { state.pickBcn(it) }, modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.mali_panvk_bcn_warning),
            style = MaterialTheme.typography.bodySmall,
            color = if (state.bcnEmulation == "none") MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
        )
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = { advancedOpen = !advancedOpen }) {
        Icon(if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.mali_panvk_advanced), modifier = Modifier.weight(1f))
    }
    if (advancedOpen) {
        if (state.modeOn) {
            PanvkSwitch(stringResource(R.string.mali_panvk_hide_exts), state.hideExts) { state.hideExts = it }
            PanvkSwitch(stringResource(R.string.mali_panvk_no_submit_waits), state.noSubmitWaits) { state.noSubmitWaits = it }
            PanvkSwitch(stringResource(R.string.mali_panvk_no_acquire_signal), state.noAcquireSignal && state.noSubmitWaits,
                enabled = state.noSubmitWaits) { state.noAcquireSignal = it }
            Text(
                stringResource(R.string.mali_panvk_sync_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PanvkMbField(stringResource(R.string.mali_panvk_heap), stringResource(R.string.mali_panvk_heap_hint),
            state.heapMb, MaliPanvk.HEAP_MIN, MaliPanvk.HEAP_MAX) { state.heapMb = it }
        PanvkMbField(stringResource(R.string.mali_panvk_tiler_heap), stringResource(R.string.mali_panvk_tiler_heap_hint),
            state.tilerHeapMb, MaliPanvk.TILER_HEAP_MIN, MaliPanvk.TILER_HEAP_MAX) { state.tilerHeapMb = it }
        PanvkMbField(stringResource(R.string.mali_panvk_poly_heap), stringResource(R.string.mali_panvk_poly_heap_hint),
            state.polyHeapMb, MaliPanvk.POLY_HEAP_MIN, MaliPanvk.POLY_HEAP_MAX) { state.polyHeapMb = it }
        Spacer(Modifier.height(8.dp))
        val autoLabel = stringResource(R.string.mali_panvk_default)
        val strideLabels = listOf(autoLabel) + MaliPanvk.ATOM_STRIDES
        LabeledDropdown(
            stringResource(R.string.mali_panvk_atom_stride), strideLabels,
            state.atomStride.ifEmpty { autoLabel },
            { state.atomStride = if (it == autoLabel) "" else it }, modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.mali_panvk_atom_stride_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.trace, onCheckedChange = { state.trace = it })
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mali_panvk_trace), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PanvkSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(
            label, modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A PANVK_*_MB field: empty = Auto / Default (not exported); out of range is flagged and not stored. */
@Composable
private fun PanvkMbField(label: String, hint: String, value: String, min: Int, max: Int, onChange: (String) -> Unit) {
    val invalid = value.isNotEmpty() && MaliPanvk.validMb(value, min, max) == null
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(5)) },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.mali_panvk_default)) },
        singleLine = true,
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text(if (invalid) stringResource(R.string.mali_panvk_out_of_range) else hint) },
        modifier = Modifier.fillMaxWidth()
    )
}
