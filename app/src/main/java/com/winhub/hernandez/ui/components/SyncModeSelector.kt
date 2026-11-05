package com.winhub.hernandez.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.core.SyncCaps
import com.winhub.hernandez.core.SyncMode

/**
 * The "Sync" row shared by the container editor and the game shortcut editor: a label, a segmented
 * pill group (esync | ntsync | fsync | wineserver, exactly one selected) and one helper line.
 * Unavailable pills are dimmed, struck through and not clickable. [caps] null = the layer is still
 * being probed: only wineserver and the current pick stay live until it lands.
 *
 * Touch only; a D-pad host wraps it and drives [onPick] itself ([focused] draws its highlight).
 * [onHelp] non-null → the app's usual "?" beside the label; the host opens R.string.help_sync_mode
 * in its HelpDialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncModeSelector(
    selected: String,
    caps: SyncCaps?,
    helper: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    // Non-null (game editor, while this game overrides the container) → a "Use container's" action.
    onUseContainer: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) cs.primary else cs.primary.copy(alpha = 0.33f),
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 10.dp),
            ) {
                Text("Sync", fontSize = 14.sp, color = cs.onSurfaceVariant)
                if (onHelp != null) {
                    IconButton(onClick = onHelp, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Help, contentDescription = "What is Sync?", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(vertical = 4.dp)
                    .background(cs.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
                    .padding(3.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (mode in SyncMode.ALL) {
                    val on = mode == selected
                    val available = on || if (caps == null) mode == SyncMode.WINESERVER || mode == SyncMode.ESYNC
                                          else caps.isAvailable(mode)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 44.dp)
                            .background(if (on) cs.primary else Color.Transparent, RoundedCornerShape(50))
                            .selectable(
                                selected = on,
                                enabled = available,
                                role = Role.RadioButton,
                                onClick = { if (!on) onPick(mode) },
                            )
                            .padding(horizontal = 9.dp),
                    ) {
                        Text(
                            mode,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                on -> cs.onPrimary
                                available -> cs.onSurface
                                else -> cs.onSurface.copy(alpha = 0.38f)
                            },
                            textDecoration = if (available) null else TextDecoration.LineThrough,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                helper,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onUseContainer != null) {
                TextButton(onClick = onUseContainer, modifier = Modifier.heightIn(min = 32.dp)) {
                    Text("Use container's", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** One-liner for the picked mode. */
fun syncModeBlurb(mode: String, caps: SyncCaps?): String = when (mode) {
    SyncMode.NTSYNC -> "ntsync — closest to Windows. New: try it per game."
    SyncMode.WINESERVER -> "wineserver — slowest, most compatible. For games that misbehave."
    else -> if (caps == null || caps.defaultMode == SyncMode.ESYNC) "esync — layer default. Fast and well tested."
            else "esync — fast and well tested."
}

/** Why each greyed pill is greyed, for [layer] (the layer's display name). */
fun syncGreyedReasons(caps: SyncCaps?, layer: String): List<String> {
    val out = mutableListOf("Android blocks fsync")
    if (caps != null && !caps.ntsync) out += "ntsync isn't in $layer"
    if (caps != null && !caps.esync) out += "esync isn't in $layer"
    return out
}

/** The container editor's helper line: the pick's one-liner plus what is greyed and why. */
fun containerSyncHelper(mode: String, caps: SyncCaps?, layer: String): String =
    if (caps == null) "Checking what $layer supports…"
    else syncModeBlurb(mode, caps) + "  ·  Greyed: " + syncGreyedReasons(caps, layer).joinToString("; ") + "."

/** Shown when a layer change (or load) finds the stored pick unavailable and switches to the default. */
fun syncSwitchedBackNotice(from: String, layer: String, caps: SyncCaps): String {
    val def = caps.defaultMode
    return "$from isn't in $layer — switched back to $def (layer default)."
}
