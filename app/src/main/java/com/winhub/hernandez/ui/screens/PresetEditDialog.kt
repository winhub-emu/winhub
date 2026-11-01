package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import com.winhub.hernandez.box64.Box64Preset
import com.winhub.hernandez.box64.Box64PresetManager
import com.winhub.hernandez.core.EnvVars
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.fexcore.FEXCorePreset
import com.winhub.hernandez.fexcore.FEXCorePresetManager
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.PresetOverrides
import com.winhub.hernandez.core.PresetScope
import org.json.JSONArray
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// PresetEditDialog — the Compose replacement for Box64EditPresetDialog and
// FEXCoreEditPresetDialog (both plain android.app.Dialog subclasses inflating
// XML). Those took their colours from the AppCompat theme, whose accent is a
// hardcoded #0055FF, so they stayed blue no matter which app theme was active.
// This draws from MaterialTheme instead and therefore follows the theme.
//
// Behaviour is deliberately identical to the dialogs it replaces: the variable
// list and its widget kinds come from <prefix>_env_vars.json, current values
// come from the preset manager (falling back to each variable's defaultValue),
// and saving routes through the same editPreset(). Built-in presets stay
// read-only — editPreset only rewrites entries in the custom-preset list, so a
// built-in id is a no-op there; we disable the inputs and hide Save to make
// that visible rather than silent.
// ─────────────────────────────────────────────────────────────────────────────

internal enum class PresetKind(
    /** Asset/pref prefix: box64_env_vars.json, fexcore_env_var_help__*, box64_preset_overrides. */
    val prefix: String,
    /**
     * Prefix on the VARIABLE names, which is not the same as [prefix] for FEXCore: the resource
     * keys are fexcore_env_var_help__* but the variables are FEX_*. Deriving this from [prefix]
     * looked for FEXCORE_ and matched nothing, so every FEXCore help lookup came back empty.
     */
    val varPrefix: String,
    val titleRes: Int,
) {
    BOX64("box64", "BOX64_", R.string.box64_preset),
    FEXCORE("fexcore", "FEX_", R.string.fexcore_preset);
}

/** What the editor is open on: a brand-new preset, or an existing one by id. */
internal sealed class PresetEditTarget(val id: String?) {
    data object New : PresetEditTarget(null)
    class Existing(id: String) : PresetEditTarget(id)
}

/** One row of <prefix>_env_vars.json. */
private data class VarSpec(
    val name: String,
    val values: List<String>,
    val kind: Kind,
    val defaultValue: String,
) {
    enum class Kind { TOGGLE, DROPDOWN, TEXT }
}

private fun loadSpecs(context: Context, prefix: String): List<VarSpec> = try {
    val data = JSONArray(FileUtils.readString(context, prefix + "_env_vars.json"))
    (0 until data.length()).map { i ->
        val item = data.getJSONObject(i)
        val values = item.optJSONArray("values")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        // The JSON is inconsistent about the casing of these two flags (both
        // "toggleSwitch" and "toggleswitch" appear, and some values are the
        // strings "true"/"false" rather than booleans), so accept either.
        val toggle = item.optBoolean("toggleSwitch", false) ||
            item.optBoolean("toggleswitch", false) ||
            item.optString("toggleSwitch") == "true" ||
            item.optString("toggleswitch") == "true"
        val text = item.optBoolean("editText", false) || item.optString("editText") == "true"
        VarSpec(
            name = item.getString("name"),
            values = values,
            kind = when {
                toggle -> VarSpec.Kind.TOGGLE
                text -> VarSpec.Kind.TEXT
                else -> VarSpec.Kind.DROPDOWN
            },
            defaultValue = item.optString("defaultValue", values.firstOrNull() ?: ""),
        )
    }
} catch (_: Throwable) {
    emptyList()
}

/** Per-variable help string, e.g. box64_env_var_help__dynarec_bigblock, fexcore_env_var_help__tsoenabled. */
private fun varHelp(context: Context, kind: PresetKind, name: String): String? {
    val suffix = name.replace(kind.varPrefix, "").lowercase(Locale.ENGLISH)
    return StringUtils.getString(context, kind.prefix + "_env_var_help__" + suffix)
}

/** Per-preset help string, e.g. box64_preset_help__extreme_2 / fexcore_preset_help__extreme_tso. */
private fun presetHelp(context: Context, prefix: String, id: String?): String? {
    if (id == null) return null
    val key = id.substringBefore('-').lowercase(Locale.ENGLISH)
    return StringUtils.getString(context, prefix + "_preset_help__" + key)
}

@Composable
internal fun PresetEditDialog(
    kind: PresetKind,
    presetId: String?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    /**
     * Who this edit belongs to. [PresetScope.GLOBAL] (the default, and what App Settings passes)
     * keeps the original behaviour exactly: the shared preset is edited and Reset restores the
     * values this build ships. The other two scopes store the values on the container or the
     * shortcut instead, leaving the shared preset alone — see [PresetOverrides].
     */
    scope: PresetScope = PresetScope.GLOBAL,
    container: Container? = null,
    shortcut: Shortcut? = null,
    /** A brand-new preset was created, so the caller can select it. Its id is passed. */
    onCreated: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val prefix = kind.prefix
    val isFex = kind == PresetKind.FEXCORE
    val scoped = scope != PresetScope.GLOBAL

    val existingName = remember(presetId) {
        when {
            presetId == null -> null
            kind == PresetKind.BOX64 -> Box64PresetManager.getPreset(prefix, context, presetId)?.name
            else -> FEXCorePresetManager.getPreset(context, presetId)?.name
        }
    }
    val isCustom = remember(presetId) {
        presetId != null && presetId.startsWith(
            if (kind == PresetKind.BOX64) Box64Preset.CUSTOM else FEXCorePreset.CUSTOM
        )
    }
    // Built-in presets are editable; their values are stored as an override so Reset can restore
    // the shipped ones. Only the NAME is locked, since it comes from a string resource.
    val isBuiltIn = presetId != null && !isCustom
    var modified by remember(presetId, scope) {
        mutableStateOf(
            presetId != null &&
                PresetOverrides.isCustomised(context, isFex, presetId, scope, container, shortcut)
        )
    }

    val specs = remember(prefix) { loadSpecs(context, prefix) }

    // What this scope INHERITS if its own copy is dropped — the reference Reset restores to and Save
    // compares against. Globally that is the values this build ships (the original behaviour); for a
    // container it is the shared preset, and for a game its container. A custom preset has no
    // inherited form at global scope, hence the null.
    val shipped = remember(presetId, scope) {
        if (presetId == null || (!scoped && presetId.startsWith(
                if (kind == PresetKind.BOX64) Box64Preset.CUSTOM else FEXCorePreset.CUSTOM
            ))
        ) null
        else PresetOverrides.inheritedBy(context, isFex, presetId, scope, container, shortcut)
    }
    // The values in effect here right now: this scope's own copy if it has one, else what it inherits.
    val current = remember(presetId, scope) {
        when {
            presetId == null -> null
            scoped -> PresetOverrides.localOf(context, isFex, presetId, scope, container, shortcut)
                ?: PresetOverrides.inheritedBy(context, isFex, presetId, scope, container, shortcut)
            kind == PresetKind.BOX64 -> Box64PresetManager.getEnvVars(prefix, context, presetId)
            else -> FEXCorePresetManager.getEnvVars(context, presetId)
        }
    }

    // One entry per variable, seeded from the preset (or its default) — this is the
    // edit buffer, so Cancel simply drops it and nothing is written.
    val values = remember(presetId, specs) {
        mutableStateMapOf<String, String>().apply {
            specs.forEach { spec ->
                put(spec.name, current?.takeIf { it.has(spec.name) }?.get(spec.name) ?: spec.defaultValue)
            }
        }
    }

    var name by remember(presetId) {
        mutableStateOf(
            existingName ?: (context.getString(R.string.preset) + "-" + when (kind) {
                PresetKind.BOX64 -> Box64PresetManager.getNextPresetId(context, prefix)
                PresetKind.FEXCORE -> FEXCorePresetManager.getNextPresetId(context)
            })
        )
    }

    var helpText by remember { mutableStateOf<String?>(null) }
    helpText?.let { HelpTextDialog(it) { helpText = null } }

    val presetHelpText = remember(presetId) { presetHelp(context, prefix, presetId) }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(context.getString(kind.titleRes), fontWeight = FontWeight.SemiBold)
                    if (presetHelpText != null) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { helpText = presetHelpText },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.HelpOutline,
                                contentDescription = "About this preset",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                // Say plainly WHERE this edit will land. The whole point of scoping is that editing
                // from a game changes that game alone, and the user can only rely on that if the
                // editor says so at the moment of editing.
                val scopeNote = when {
                    scope == PresetScope.SHORTCUT && modified ->
                        "Customised for this game. Reset makes it follow its container again."
                    scope == PresetScope.SHORTCUT ->
                        "Saved for this game only — no other game or container changes."
                    scope == PresetScope.CONTAINER && modified ->
                        "Customised for this container. Reset makes it follow the shared preset again."
                    scope == PresetScope.CONTAINER ->
                        "Saved for this container only — the shared preset is left alone."
                    isBuiltIn && modified -> "Built-in preset — edited. Reset restores the original."
                    isBuiltIn -> "Built-in preset — edits are saved separately and can be reset."
                    else -> null
                }
                scopeNote?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        color = if (modified) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    // Built-in names come from string resources, so only custom presets rename.
                    enabled = !isBuiltIn,
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Environment variables",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp),
                ) {
                    items(specs, key = { it.name }) { spec ->
                        // Resolve the help text up front: a variable with none hides its "?"
                        // rather than showing a button that does nothing when tapped.
                        val help = remember(spec.name) { varHelp(context, kind, spec.name) }
                        VarRow(
                            spec = spec,
                            value = values[spec.name] ?: spec.defaultValue,
                            enabled = true,
                            onValue = { values[spec.name] = it },
                            onHelp = help?.let { h -> { helpText = h } },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = name.trim().replace(Regex("[,|]+"), "")
                if (clean.isEmpty()) return@TextButton
                val envVars = EnvVars()
                specs.forEach { envVars.put(it.name, values[it.name] ?: it.defaultValue) }

                // Saving a built-in whose values match what this build ships must CLEAR the
                // override, not write one that happens to be identical — otherwise Reset then Save
                // left the preset permanently flagged as edited.
                //
                // The comparison has to be against what the editor would SHOW for the shipped
                // preset, not against the shipped map directly: a preset only sets the variables it
                // cares about (Extreme (TSO)-wn sets 11 of 17) while Save always writes all of them,
                // so the two maps never match on size alone.
                // Scoped: a container/game only needs its own copy when the values differ from what
                // it inherits — otherwise clear it and go back to following the level above. Global
                // keeps its original meaning: matching the shipped values clears the override.
                val unchanged = (scoped || isBuiltIn) && specs.all { spec ->
                    val inheritedSeed = shipped?.takeIf { it.has(spec.name) }?.get(spec.name)
                        ?: spec.defaultValue
                    (values[spec.name] ?: spec.defaultValue) == inheritedSeed
                }
                when {
                    scoped && presetId != null -> {
                        if (unchanged) PresetOverrides.clearLocal(isFex, scope, container, shortcut)
                        else PresetOverrides.writeLocal(
                            isFex, presetId, scope, container, shortcut, envVars
                        )
                    }
                    unchanged -> when (kind) {
                        PresetKind.BOX64 -> Box64PresetManager.resetPreset(prefix, context, presetId)
                        PresetKind.FEXCORE -> FEXCorePresetManager.resetPreset(context, presetId)
                    }
                    kind == PresetKind.BOX64 ->
                        Box64PresetManager.editPreset(prefix, context, presetId, clean, envVars)
                    else -> FEXCorePresetManager.editPreset(context, presetId, clean, envVars)
                }
                // A brand-new preset always lands in the shared list (there is nothing to scope it
                // to yet); tell the caller so it can select what was just created.
                if (presetId == null) {
                    val newId = if (kind == PresetKind.BOX64)
                        Box64PresetManager.getPresets(prefix, context).lastOrNull()?.id
                    else FEXCorePresetManager.getPresets(context).lastOrNull()?.id
                    newId?.let(onCreated)
                }
                onSaved()
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Reset only makes sense for a built-in that has actually been edited — a custom
                // preset has no shipped original to go back to.
                // Scoped: Reset means "stop keeping my own copy and follow the level above", which
                // applies to a custom preset just as much as a built-in — so the isBuiltIn gate is
                // global-only.
                if ((scoped || isBuiltIn) && modified) {
                    TextButton(onClick = {
                        if (scoped) PresetOverrides.clearLocal(isFex, scope, container, shortcut)
                        else when (kind) {
                            PresetKind.BOX64 -> Box64PresetManager.resetPreset(prefix, context, presetId)
                            PresetKind.FEXCORE -> FEXCorePresetManager.resetPreset(context, presetId)
                        }
                        // Re-seed the editor from the shipped values so the change is visible
                        // immediately rather than only after reopening.
                        specs.forEach { spec ->
                            values[spec.name] =
                                shipped?.takeIf { it.has(spec.name) }?.get(spec.name) ?: spec.defaultValue
                        }
                        modified = false
                        onSaved()
                    }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun VarRow(
    spec: VarSpec,
    value: String,
    enabled: Boolean,
    onValue: (String) -> Unit,
    onHelp: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            spec.name,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onHelp != null) {
            IconButton(onClick = onHelp, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.HelpOutline,
                    contentDescription = "About " + spec.name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Spacer(Modifier.width(4.dp))
        when (spec.kind) {
            VarSpec.Kind.TOGGLE -> Switch(
                checked = value == "1",
                onCheckedChange = { onValue(if (it) "1" else "0") },
                enabled = enabled,
            )

            VarSpec.Kind.TEXT -> OutlinedTextField(
                value = value,
                onValueChange = onValue,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(96.dp),
            )

            VarSpec.Kind.DROPDOWN -> {
                var open by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .width(96.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { open = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            value,
                            fontSize = 12.sp,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        // Same outlined menu card the rest of the app uses, so the value picker
                        // is outlined like every other popup.
                        modifier = Modifier.outlinedMenuCard(),
                    ) {
                        spec.values.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v, fontSize = 12.sp) },
                                onClick = { onValue(v); open = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
