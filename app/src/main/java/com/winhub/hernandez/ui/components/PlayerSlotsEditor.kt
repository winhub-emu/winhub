@file:OptIn(ExperimentalMaterial3Api::class)

package com.winhub.hernandez.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.screens.DeckChoiceRow
import com.winhub.hernandez.ui.screens.DeckConfigNote
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.inputcontrols.ExternalController
import com.winhub.hernandez.winhandler.WinHandler

// ───── Out-of-game Player Slots editor — shared by the Container editor and the per-game Shortcut editor ─────
// Mirrors the in-game "Players" sub-tab (XServerDrawer.PlayerSlotRowItem) but writes a SAVED preference
// applied on the NEXT launch instead of reassigning a live session. The persisted value is the exact same
// controllerSlotOverrides JSON the in-game tab and launch pre-assignment use — parsed/serialized only via
// WinHandler.parseSlotOverridesJson / buildSlotOverridesJson, never hand-rolled here — so a pin set here is
// honored at launch by XServerDisplayActivity (resolvedControllerSlotOverridesJson -> setManualSlotOverrides
// -> preAssignConnectedControllers).

// Slot sentinel values: mirror WinHandler. Auto = absent from the map (FCFS at launch).
private const val SLOT_AUTO = -1
private val SLOT_IGNORE = WinHandler.SLOT_IGNORE // -2

/** One editable row: a physical controller (key == device.getDescriptor() == ExternalController.getId(),
 *  the SAME key launch pre-assign reads), or the on-screen pad (key == WinHandler.OSC_DESCRIPTOR). */
data class PlayerSlotEditorRow(
    val descriptor: String,
    val displayName: String,
    val isOnScreen: Boolean,
    val connected: Boolean, // false = a saved pin whose device is not currently plugged in
    val override: Int,      // SLOT_AUTO / 0..3 / SLOT_IGNORE
)

/** Build the row list from a saved-overrides JSON: the on-screen pad first (always, pinnable to any slot
 *  incl Player 1), then every currently-connected game controller (enumerated exactly like the app-drawer
 *  InputControlsScreen via ExternalController.getControllers()), then any saved-but-disconnected pins so a
 *  configured pad that's simply switched off is never dropped. Keys are the launch-time descriptor keys. */
fun buildPlayerSlotEditorRows(savedJson: String): List<PlayerSlotEditorRow> {
    val overrides = WinHandler.parseSlotOverridesJson(savedJson)
    val rows = ArrayList<PlayerSlotEditorRow>()
    val seen = HashSet<String>()

    val oscOverride = overrides[WinHandler.OSC_DESCRIPTOR] ?: SLOT_AUTO
    rows.add(PlayerSlotEditorRow(WinHandler.OSC_DESCRIPTOR, "On-screen controls", true, true, oscOverride))
    seen.add(WinHandler.OSC_DESCRIPTOR)

    for (controller in ExternalController.getControllers()) {
        val key = controller.id ?: continue
        if (!seen.add(key)) continue
        rows.add(PlayerSlotEditorRow(key, controller.name ?: key, false, true, overrides[key] ?: SLOT_AUTO))
    }

    for ((key, ov) in overrides) {
        if (!seen.add(key)) continue
        rows.add(PlayerSlotEditorRow(key, key, false, false, ov))
    }
    return rows
}

/** The shared Player-Slots list. Reads/writes the canonical controllerSlotOverrides JSON: each edit
 *  rebuilds the descriptor->slot map (Auto removes the key) and hands the re-serialized JSON back through
 *  onOverridesChange. The caller owns persistence (Container.setControllerSlotOverrides / Shortcut extra). */
@Composable
fun PlayerSlotsEditor(
    savedOverridesJson: String,
    onOverridesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    // Deck rows (label + hint + value chip) for callers that sit inside a DeckGroup; false keeps the
    // Material look the container and shortcut editors use.
    deck: Boolean = false,
) {
    // Keyed on the saved JSON so an external change (or the caller re-seeding) re-enumerates devices.
    val rows = remember(savedOverridesJson) { buildPlayerSlotEditorRows(savedOverridesJson) }

    if (deck) {
        Column(modifier.fillMaxWidth()) {
            DeckConfigNote(
                "Pin a controller (or the on-screen pad) to a player slot so the launch order is consistent. " +
                    "Applied on launch; the in-game side menu still lets you change it live."
            )
            if (rows.isEmpty()) {
                DeckConfigNote("No controllers detected. Connect a controller to assign it, or set a pin here to apply when it's plugged in.")
            }
            rows.forEach { row ->
                DeckPlayerSlotRow(row) { newOverride ->
                    val map = WinHandler.parseSlotOverridesJson(savedOverridesJson).toMutableMap()
                    if (newOverride == SLOT_AUTO) map.remove(row.descriptor)
                    else map[row.descriptor] = newOverride
                    onOverridesChange(WinHandler.buildSlotOverridesJson(map))
                }
            }
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "Pin a controller (or the on-screen pad) to a player slot so the launch order is consistent. " +
                "Applied on launch — the in-game side menu still lets you change it live.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (rows.isEmpty()) {
            Text(
                "No controllers detected. Connect a controller to assign it, or set a pin here to apply when it's plugged in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rows.forEach { row ->
                PlayerSlotEditorRowItem(row) { newOverride ->
                    val map = WinHandler.parseSlotOverridesJson(savedOverridesJson).toMutableMap()
                    if (newOverride == SLOT_AUTO) map.remove(row.descriptor)
                    else map[row.descriptor] = newOverride
                    onOverridesChange(WinHandler.buildSlotOverridesJson(map))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// One device row: name + status subtitle + an Auto / Player 1-4 / Ignore dropdown. Visually mirrors the
// in-game PlayerSlotRowItem. @OptIn is inherited from the file-level opt-in (ExposedDropdownMenuBox).
@Composable
private fun PlayerSlotEditorRowItem(row: PlayerSlotEditorRow, onChange: (Int) -> Unit) {
    val options = remember {
        buildList {
            add("Auto" to SLOT_AUTO)
            for (i in 0 until 4) add("Player ${i + 1}" to i)
            add("Ignore" to SLOT_IGNORE)
        }
    }
    val selectedLabel = options.firstOrNull { it.second == row.override }?.first ?: "Auto"

    val statusText = slotStatusText(row)

    var expanded by remember(row.descriptor, row.override) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            row.displayName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {}, readOnly = true,
                label = { Text("Slot", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                // #333 polish: match the shared outlined-menu-card look (outline + thin divider between
                // options) used by LabeledDropdown, so these slot pickers read identically.
                modifier = Modifier.outlinedMenuCard()
            ) {
                options.forEachIndexed { index, option ->
                    if (index > 0) MenuItemDivider()
                    val (label, value) = option
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        expanded = false
                        if (value != row.override) onChange(value)
                    })
                }
            }
        }
    }
}

private fun slotStatusText(row: PlayerSlotEditorRow): String {
    val status = when {
        row.override == SLOT_IGNORE -> "Ignored"
        row.override >= 0 -> "Pinned to Player ${row.override + 1}"
        else -> "Auto (assigned by launch order)"
    }
    val suffix = when {
        row.isOnScreen -> " · on-screen controls"
        !row.connected -> " · not connected (saved pin)"
        else -> ""
    }
    return status + suffix
}

// Deck counterpart of PlayerSlotEditorRowItem: same options and callback, Deck choice row.
@Composable
private fun DeckPlayerSlotRow(row: PlayerSlotEditorRow, onChange: (Int) -> Unit) {
    val options = remember {
        buildList {
            add(SLOT_AUTO.toString() to "Auto")
            for (i in 0 until 4) add(i.toString() to "Player ${i + 1}")
            add(SLOT_IGNORE.toString() to "Ignore")
        }
    }
    DeckChoiceRow(
        label = row.displayName,
        hint = slotStatusText(row),
        options = options,
        selected = row.override.toString(),
        onPick = { picked ->
            val value = picked.toInt()
            if (value != row.override) onChange(value)
        },
    )
}
