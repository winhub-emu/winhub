package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.reshade.ReshadeLoadout
import com.winhub.hernandez.reshade.ReshadeManager
import org.json.JSONObject

/**
 * Compose-observable state holder for a pre-launch ReShade LOADOUT (Tier 1), shared by the container
 * editor (ContainerDetailViewModel) and the per-game shortcut editor (ShortcutsScreen). Holds the
 * ordered effect names, per-effect enabled flags, the solo/stack mode, and per-effect uniform values
 * (keyed "<effect>::<uniform>" — the ReshadeManager.seedValues key scheme with an effect prefix).
 * Serializes to the reshadeLoadout array + nested reshadeParams object; migrates legacy single-effect
 * saves transparently via ReshadeLoadout.
 */
class ReshadeLoadoutState {
    var mode by mutableStateOf(ReshadeLoadout.MODE_SOLO)
        private set
    val order = mutableStateListOf<String>()                        // effect names, chain order
    private val enabledMap = mutableStateMapOf<String, Boolean>()
    private val paramValues = mutableStateMapOf<String, Float>()    // "<effect>::<uniformKey>"

    fun isEnabled(name: String): Boolean = enabledMap[name] ?: true

    fun setEnabled(name: String, on: Boolean) {
        if (mode == ReshadeLoadout.MODE_SOLO && on) order.forEach { enabledMap[it] = (it == name) }
        else enabledMap[name] = on
    }

    fun changeMode(m: String) {
        mode = ReshadeLoadout.normalizeMode(m)
        if (mode == ReshadeLoadout.MODE_SOLO) {
            var seen = false
            order.forEach { n ->
                val on = enabledMap[n] ?: false
                if (on && !seen) seen = true else enabledMap[n] = false
            }
        }
    }

    fun contains(name: String): Boolean = order.contains(name)

    // Add an effect (seeding its default params). Enabling it respects solo exclusivity.
    fun add(effect: ReshadeManager.ReshadeEffect, saved: JSONObject?) {
        if (order.contains(effect.name)) return
        order.add(effect.name)
        seed(effect, saved)
        setEnabled(effect.name, true)
    }

    fun remove(name: String) {
        order.remove(name)
        enabledMap.remove(name)
        val prefix = "$name::"
        paramValues.keys.filter { it.startsWith(prefix) }.toList().forEach { paramValues.remove(it) }
        // Solo: if we removed the active effect, promote the first remaining one.
        if (mode == ReshadeLoadout.MODE_SOLO && order.isNotEmpty() && order.none { isEnabled(it) })
            setEnabled(order.first(), true)
    }

    private fun seed(effect: ReshadeManager.ReshadeEffect, saved: JSONObject?) {
        val tmp = HashMap<String, Float>()
        for (p in effect.params) ReshadeManager.seedValues(p, saved, tmp)
        for ((k, v) in tmp) paramValues["${effect.name}::$k"] = v
    }

    fun paramValue(effect: String, key: String, fallback: Float): Float = paramValues["$effect::$key"] ?: fallback
    fun setParam(effect: String, key: String, value: Float) { paramValues["$effect::$key"] = value }

    fun loadoutJsonOrNull(): String? {
        if (order.isEmpty()) return null
        return ReshadeLoadout.serialize(order.map { ReshadeLoadout.Entry(it, isEnabled(it)) })
    }

    fun paramsJsonOrNull(): String? {
        if (order.isEmpty()) return null
        val root = JSONObject()
        for (name in order) {
            val eff = JSONObject()
            val prefix = "$name::"
            for ((k, v) in paramValues) if (k.startsWith(prefix)) eff.put(k.removePrefix(prefix), v.toDouble())
            if (eff.length() > 0) root.put(name, eff)
        }
        return if (root.length() == 0) null else root.toString()
    }

    fun firstEffectName(): String = order.firstOrNull() ?: "None"

    // (Re)load from stored strings + the scanned effects (used for param reflection). Effects no
    // longer present in the drop-in folder are dropped. Migrates a legacy single effect + flat params.
    fun init(
        effects: List<ReshadeManager.ReshadeEffect>,
        loadoutJson: String?,
        modeStr: String?,
        paramsJson: String?,
        legacyEffect: String?,
    ) {
        order.clear(); enabledMap.clear(); paramValues.clear()
        mode = ReshadeLoadout.normalizeMode(modeStr)
        val nested = !loadoutJson.isNullOrEmpty()
        for (e in ReshadeLoadout.parse(loadoutJson, legacyEffect)) {
            val effect = effects.firstOrNull { it.name.equals(e.name, true) } ?: continue
            order.add(effect.name)
            enabledMap[effect.name] = e.enabled
            seed(effect, ReshadeLoadout.paramsForEffect(paramsJson, effect.name, nested, legacyEffect))
        }
        changeMode(mode) // enforce the solo invariant
    }

    // Re-seed after a rescan (post-download), preserving current values where possible. Keeps the
    // selection; only drops effects whose folder vanished.
    fun reconcile(effects: List<ReshadeManager.ReshadeEffect>) {
        val present = effects.associateBy { it.name }
        order.filter { it !in present }.toList().forEach { remove(it) }
        for (name in order) {
            val effect = present[name] ?: continue
            // Seed any params that aren't already set (newly reflected), keep existing values.
            val tmp = HashMap<String, Float>()
            for (p in effect.params) ReshadeManager.seedValues(p, null, tmp)
            for ((k, v) in tmp) {
                val full = "$name::$k"
                if (!paramValues.containsKey(full)) paramValues[full] = v
            }
        }
    }
}

/**
 * Pre-launch multi-effect loadout editor: the catalog picker (multi-select + download-on-demand), a
 * Solo/Stack mode switch, a "longer launch compile" hint when the loadout is large, and a collapsible
 * per-effect block of typed controls. Shared by the container + shortcut editors.
 */
@Composable
fun ReshadeLoadoutEditor(
    state: ReshadeLoadoutState,
    effects: List<ReshadeManager.ReshadeEffect>,
    supported: Boolean,
    onCatalogChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        ReshadeLoadoutPicker(
            selectedNames = state.order,
            supported = supported,
            onToggle = { name, add ->
                if (add) effects.firstOrNull { it.name == name }?.let { state.add(it, null) }
                else state.remove(name)
            },
            onClear = { state.order.toList().forEach { state.remove(it) } },
            onCatalogChanged = onCatalogChanged,
        )

        if (state.order.isEmpty()) return@Column

        Spacer(Modifier.height(6.dp))
        ReshadeModeRow(state.mode) { state.changeMode(it) }
        if (state.order.size > 6) {
            Text(
                "${state.order.size} effects — longer launch compile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        state.order.forEach { name ->
            val effect = effects.firstOrNull { it.name == name }
            ReshadeEffectEditorRow(state, name, effect?.params ?: emptyList(), state.mode)
        }
    }
}

// Solo/Stack pill switch.
@Composable
private fun ReshadeModeRow(mode: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ReshadeLoadout.MODE_SOLO to "Solo", ReshadeLoadout.MODE_STACK to "Stack").forEach { (value, label) ->
                val selected = mode == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) cs.primary.copy(alpha = 0.18f) else cs.surface)
                        .border(1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { onChange(value) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, color = if (selected) cs.primary else cs.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        Text(
            if (mode == ReshadeLoadout.MODE_SOLO) "One effect active at a time (A/B compare)."
            else "Layer any subset of effects.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
        )
    }
}

// One effect's row in the loadout: an activation Switch + name (collapsible header), and — when
// expanded — its typed controls (Switch/dropdown/RGB-slider/slider) + per-effect Reset.
@Composable
private fun ReshadeEffectEditorRow(
    state: ReshadeLoadoutState,
    name: String,
    params: List<ReshadeManager.ReshadeParam>,
    mode: String,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(name) { mutableStateOf(false) }
    val enabled = state.isEnabled(name)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Switch(checked = enabled, onCheckedChange = { state.setEnabled(name, it) })
        Spacer(Modifier.width(8.dp))
        Text(
            name, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = cs.onSurface,
            modifier = Modifier.weight(1f).clickable { expanded = !expanded },
        )
        if (mode == ReshadeLoadout.MODE_SOLO && enabled) {
            Text("active", style = MaterialTheme.typography.labelSmall, color = cs.primary,
                modifier = Modifier.padding(end = 6.dp))
        }
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null, tint = cs.onSurfaceVariant,
            modifier = Modifier.clickable { expanded = !expanded },
        )
    }
    if (expanded) {
        if (params.isEmpty()) {
            Text("No tunable parameters.", style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    // Reset this effect's params to the .fx defaults.
                    params.forEach { p ->
                        val tmp = HashMap<String, Float>()
                        ReshadeManager.seedValues(p, null, tmp)
                        tmp.forEach { (k, v) -> state.setParam(name, k, v) }
                    }
                }) { Text("Reset", color = cs.primary) }
            }
            params.forEach { p -> ReshadeParamControl(state, name, p) }
        }
    }
}

// A single reflected uniform's control (pre-launch flavour: RGB sliders for COLOR, as before).
@Composable
private fun ReshadeParamControl(state: ReshadeLoadoutState, effect: String, p: ReshadeManager.ReshadeParam) {
    val value = state.paramValue(effect, p.name, p.defaultValue)
    when (p.type) {
        ReshadeManager.ParamType.BOOL -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                Switch(checked = value >= 0.5f, onCheckedChange = { state.setParam(effect, p.name, if (it) 1f else 0f) })
                Spacer(Modifier.width(8.dp))
                Text(p.label, style = MaterialTheme.typography.bodySmall)
            }
        }
        ReshadeManager.ParamType.COMBO -> {
            val options = p.options ?: emptyList()
            LabeledDropdown(
                label = p.label,
                options = options,
                selectedOption = options.getOrElse(value.toInt()) { options.firstOrNull() ?: "" },
                onSelect = { sel -> state.setParam(effect, p.name, options.indexOf(sel).coerceAtLeast(0).toFloat()) },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        ReshadeManager.ParamType.COLOR -> {
            Text(p.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
            val comp = listOf("R", "G", "B", "A")
            for (c in 0 until p.components) {
                val k = "${p.name}_$c"
                val cv = state.paramValue(effect, k, p.componentDefaults?.getOrNull(c) ?: 0f)
                Text("${comp.getOrElse(c) { "$c" }}: ${"%.2f".format(cv)}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
                Slider(
                    value = cv.coerceIn(0f, 1f),
                    onValueChange = { state.setParam(effect, k, it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        else -> {
            val display = if (p.type == ReshadeManager.ParamType.INT) value.toInt().toString() else "%.2f".format(value)
            Text("${p.label}: $display", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
            Slider(
                value = value.coerceIn(p.min, p.max),
                onValueChange = { state.setParam(effect, p.name, it) },
                valueRange = p.min..p.max,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
