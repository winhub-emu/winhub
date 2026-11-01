package com.winhub.hernandez.store.download

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * ONE shared install-progress popup used by BOTH the container-create component sheet
 * ([com.winhub.hernandez.ui.screens.ContentDownloadSheet]) and the Contents hub
 * ([com.winhub.hernandez.ui.screens.contents.ContentsHubScreen]) — so a download/install looks and
 * behaves identically wherever it's kicked off. It renders straight off a [ContentDownloadState]
 * snapshot from [ContentDownloadRegistry], so it reflects live phase+percent (and survives
 * backgrounding), and offers a best-effort Cancel while the job is still running.
 *
 * - [onCancel] fires while non-terminal → route it to [ContentDownloadRegistry.requestCancel].
 * - [onDismiss] fires on the terminal Done/Close button (and outside-tap once terminal).
 *
 * Driver installs carry no `profile.json` (no incremental progress): the popup just shows the
 * "GPU Drivers" type + filename and an indeterminate bar until it flips to Installed.
 */
@Composable
fun InstallProgressDialog(
    state: ContentDownloadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val terminal = state.terminal
    Dialog(
        onDismissRequest = { if (terminal) onDismiss() },
        // usePlatformDefaultWidth=false lets the card use the wider padding below so long .wcp names
        // (e.g. proton-10.0-2-arm64ec-controllerfix-unixlib.wcp) get the room to wrap tidily.
        properties = DialogProperties(
            dismissOnBackPress = terminal,
            dismissOnClickOutside = terminal,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
            border = BorderStroke(1.dp, cs.outline),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Memory, contentDescription = null, tint = cs.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    // Title = verName ?: title; capped at 2 lines so a long name never orphans a char.
                    Text(state.verName ?: state.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }

                // Component-type chip.
                state.type?.takeIf { it.isNotBlank() }?.let { t ->
                    Spacer(Modifier.height(12.dp))
                    TypeChip(t)
                }

                // Description (when the archive's profile.json carried one).
                if (!state.desc.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.desc, color = cs.onSurface, style = MaterialTheme.typography.bodySmall)
                }

                // Version name + version code, e.g. "Version 2.4.1 • build 63".
                versionLine(state)?.let { v ->
                    Spacer(Modifier.height(8.dp))
                    Text(v, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))

                // Phase label + live bar.
                val frac = state.fraction.coerceIn(0f, 1f)
                val label = when {
                    state.cancelled -> "Cancelled"
                    state.phase == ContentDownloadPhase.ERROR -> state.error ?: if (state.saveOnly) "Save failed." else "Install failed."
                    state.phase == ContentDownloadPhase.DONE -> if (state.saveOnly) "Saved to My Files" else "Installed"
                    state.phase == ContentDownloadPhase.DOWNLOADING -> "Downloading ${(frac * 100).toInt()}%"
                    state.saveOnly -> "Saving to My Files"
                    else -> "Installing ${(frac * 100).toInt()}%"
                }
                // Cancelled reads as a neutral outcome (not the alarming error red); a finished install
                // reads in the theme accent (matched — no green).
                val labelColor = when {
                    state.phase == ContentDownloadPhase.ERROR && !state.cancelled -> cs.error
                    state.phase == ContentDownloadPhase.DONE -> cs.primary
                    else -> cs.onSurfaceVariant
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = labelColor)

                if (!terminal || state.phase == ContentDownloadPhase.DONE) {
                    Spacer(Modifier.height(4.dp))
                    // Two-pass overlay bar, all in the theme accent (no green). First pass = DOWNLOAD in a
                    // DARKER accent that fills the track; second pass = INSTALL in a LIGHTER accent sweeping
                    // left→right over it. A finished install ends as a full lighter-accent bar (matched to
                    // the theme). A local file (hasDownload=false) has no download pass — only the lighter
                    // install bar shows, so it reads as a single bar.
                    val darkPass = lerp(cs.primary, Color.Black, 0.40f) // download — first pass
                    val litePass = lerp(cs.primary, Color.White, 0.12f) // install  — second pass
                    val dlFrac = when {
                        !state.hasDownload -> 0f
                        state.phase == ContentDownloadPhase.DOWNLOADING -> frac
                        else -> 1f
                    }
                    val instFrac = if (state.phase == ContentDownloadPhase.DOWNLOADING) 0f else frac
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(cs.surfaceContainerHighest),
                    ) {
                        if (dlFrac > 0f) {
                            Box(
                                Modifier.fillMaxWidth(dlFrac).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)).background(darkPass),
                            )
                        }
                        if (instFrac > 0f) {
                            Box(
                                Modifier.fillMaxWidth(instFrac).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp)).background(litePass),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (!terminal) {
                        TextButton(onClick = onCancel) { Text("Cancel", color = cs.primary) }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(if (state.phase == ContentDownloadPhase.DONE) "Done" else "Close", color = cs.primary)
                        }
                    }
                }
            }
        }
    }
}

/** "Version <name> • build <code>" — omits either half when it isn't known (0/blank code = unknown). */
private fun versionLine(state: ContentDownloadState): String? {
    val name = state.verName?.takeIf { it.isNotBlank() }
    val code = state.verCode?.takeIf { it.isNotBlank() && it != "0" }
    return when {
        name != null && code != null -> "Version $name • build $code"
        name != null -> "Version $name"
        code != null -> "build $code"
        else -> null
    }
}

@Composable
private fun TypeChip(type: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        type,
        style = MaterialTheme.typography.labelSmall,
        color = cs.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cs.primary.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
