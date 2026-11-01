package com.winhub.hernandez.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.winhub.hernandez.UnpackArchiveActivity
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.unpack.UnpackManager
import com.winhub.hernandez.core.unpack.UnpackPhase
import com.winhub.hernandez.core.unpack.UnpackService

/**
 * App-wide minimized progress pill for a running unpack. Hosted in the root scaffold so it floats
 * over every screen, and observes the SAME [UnpackManager] StateFlow the foreground service updates
 * and the system notification renders — the three views (full [com.winhub.hernandez.ui.screens.UnpackArchiveScreen],
 * this pill, the notification) never diverge.
 *
 * Tap → reopen the full progress view; the pill's ⋯ Cancel kills the job. Renders nothing (and takes
 * no space) when no extraction is active, so it is safe to drop unconditionally into a layout.
 */
@Composable
fun UnpackProgressPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by UnpackManager.state.collectAsState()
    if (!state.isRunning) return

    val listing = state.phase == UnpackPhase.LISTING
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { context.startActivity(UnpackArchiveActivity.intent(context, state.archivePath)) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Unarchive, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.archiveName.ifBlank { "Unpacking" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (listing) "Reading archive…"
                        else buildString {
                            append("${state.percent}%")
                            if (state.speedBps > 0) append("  •  ${StringUtils.formatBytes(state.speedBps)}/s")
                            if (state.etaSeconds >= 0) append("  •  ETA ${StringUtils.humanDuration(state.etaSeconds * 1000)}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { UnpackService.cancel(context) }) {
                    Icon(Icons.Filled.Close, "Cancel unpack", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (listing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }
            }
        }
    }
}
