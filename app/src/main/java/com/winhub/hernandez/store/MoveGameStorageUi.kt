package com.winhub.hernandez.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog

/**
 * Confirm sheet for a storage move — says exactly where the game is going, what it costs, and what
 * the user is trading away, then hands off to [MoveGameStorage.execute].
 *
 * The two warnings are the ones that actually bite:
 *  • **Speed** — the card is FUSE-backed, so a game that streams assets can stall on it. This is the
 *    same trade the "Install to SD card" download toggle warns about, and the reason "Copy game to
 *    Drive C" exists. Only shown for the internal → card direction.
 *  • **EA titles** — an EA/Denuvo game can treat a new install path as a new machine and demand a
 *    re-activation, which spends a limited activation allowance. Shown whenever [EaSupport] fingerprints
 *    the folder, in either direction.
 */
@Composable
internal fun MoveStorageDialog(
    plan: MoveGameStorage.Plan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val toSd = plan.to.place == MoveGameStorage.Place.SD
    val fromLabel = if (plan.from == MoveGameStorage.Place.INTERNAL) "Internal storage" else "SD card"
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (toSd) "Move to SD card" else "Move to internal storage") },
        text = {
            Column {
                Text(
                    text = "${plan.gameName}\n$fromLabel  →  ${plan.to.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Size: ${SteamSdInstall.fmtBytes(plan.sizeBytes)}   ·   " +
                        "Free there: ${SteamSdInstall.fmtBytes(plan.to.freeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!plan.fitsOnTarget) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "⛔ There isn't enough free space for this game.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // What the user is really asking: "will my game still work afterwards?" Say it up
                // front rather than making them find out.
                Text(
                    text = "Your saves, cloud saves and achievements are kept — they don't live in " +
                        "the game folder. The game's shortcut" +
                        (if (plan.shortcuts.size == 1) " is" else "s (${plan.shortcuts.size}) are") +
                        " updated to the new location, so launching works exactly as before.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (plan.shortcuts.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "This game isn't in a container yet, so there's no shortcut to update.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (toSd) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "⚠️ The SD card is slower than internal storage. Some games stall while " +
                            "loading from a card — if that happens, move it back, or use Copy game to " +
                            "Drive C from the shortcut editor.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (plan.isEaTitle) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "⚠️ This is an EA game. EA's activation can treat a new install location " +
                            "as a new computer and ask you to sign in and activate again, which uses up " +
                            "one of your limited activations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "The original copy is only deleted after the new one is checked, so a " +
                        "failed move leaves the game exactly where it is.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = plan.fitsOnTarget) {
                Text(if (toSd) "Move to SD card" else "Move to internal")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Blocking progress for a running move. Not dismissable — the only way out is Cancel, and that is
 * only offered while the copy is still running: once the shortcuts and the database pointer are
 * being rewritten the move is committing, and stopping there is what would leave them disagreeing.
 */
@Composable
internal fun MoveProgressDialog(
    gameName: String,
    progress: MoveGameStorage.Progress,
    onCancel: () -> Unit,
) {
    val copying = progress.phase == MoveGameStorage.Phase.COPYING
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "Moving $gameName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(14.dp))
                if (copying) {
                    LinearProgressIndicator(
                        progress = { progress.pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (progress.phase) {
                        MoveGameStorage.Phase.COPYING ->
                            "${progress.pct}%  ·  ${SteamSdInstall.fmtBytes(progress.copiedBytes)} of " +
                                SteamSdInstall.fmtBytes(progress.totalBytes)
                        MoveGameStorage.Phase.VERIFYING -> "Checking the copy…"
                        MoveGameStorage.Phase.REPOINTING -> "Updating shortcuts…"
                        MoveGameStorage.Phase.CLEANING -> "Freeing the old copy…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (copying && progress.currentFile.isNotBlank()) {
                    Text(
                        text = progress.currentFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel, enabled = copying) { Text("Cancel") }
                }
            }
        }
    }
}
