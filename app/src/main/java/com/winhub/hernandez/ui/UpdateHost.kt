package com.winhub.hernandez.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.UpdateManager
import com.winhub.hernandez.core.UpdateManager.DownloadState
import java.util.Locale

private val UpdateGreen = Color(0xFF4CAF50)

private fun fmtSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 1L shl 30 -> String.format(Locale.ENGLISH, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.ENGLISH, "%.0f KB", bytes / 1024.0)
}

/**
 * Host this once in the app shell. It renders the update card whenever an update download is
 * running (full APK or small update), the new APK is being rebuilt from a patch, or a download
 * failed, whichever button started it. B / Back cancels a running download or closes a failure.
 */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    val state by UpdateManager.download.collectAsState()
    when (val s = state) {
        is DownloadState.Downloading -> UpdateProgressCard(
            title = "Updating to V ${s.info.versionName}",
            subtitle = if (s.small) "Downloading a small update…" else "Downloading the update…",
            bytes = s.bytes, total = s.total, bytesPerSec = s.bytesPerSec,
        )
        is DownloadState.Rebuilding -> UpdateProgressCard(
            title = "Updating to V ${s.info.versionName}",
            subtitle = "Building the update from the installed app…",
            bytes = s.bytes, total = s.total, bytesPerSec = 0L,
        )
        is DownloadState.Failed -> UpdateFailedCard(
            s,
            onRetry = {
                UpdateManager.clearDownloadState()
                (context as? Activity)?.let { UpdateManager.downloadAndInstall(it, s.info) {} }
            },
            onReleasePage = {
                UpdateManager.clearDownloadState()
                (context as? Activity)?.let { UpdateManager.openReleasesPage(it) }
            },
            onClose = { UpdateManager.clearDownloadState() },
        )
        else -> {}
    }
}

@Composable
private fun UpdateCardSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun UpdateProgressCard(title: String, subtitle: String, bytes: Long, total: Long, bytesPerSec: Long) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    Dialog(
        onDismissRequest = { UpdateManager.cancelDownload() },
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        UpdateCardSurface {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            val known = total > 0 && bytes > 0
            if (known) {
                LinearProgressIndicator(
                    progress = { (bytes.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = UpdateGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = UpdateGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row {
                val left = if (total > 0) " of ${fmtSize(total)}" else ""
                val speed = if (bytesPerSec > 0) " · ${fmtSize(bytesPerSec)}/s" else ""
                Text(
                    fmtSize(bytes).ifEmpty { "0 MB" } + left + speed,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (total > 0 && bytesPerSec > 0) {
                    Text(
                        "about ${StringUtils.humanDuration((total - bytes) * 1000 / bytesPerSec)} left",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { UpdateManager.cancelDownload() },
                    modifier = Modifier.heightIn(min = 48.dp).focusRequester(cancelFocus),
                ) { Text("Cancel (B)", fontSize = 14.sp) }
            }
        }
    }
}

@Composable
private fun UpdateFailedCard(
    s: DownloadState.Failed,
    onRetry: () -> Unit,
    onReleasePage: () -> Unit,
    onClose: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnClickOutside = false)) {
        UpdateCardSurface {
            Text("Update failed", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(s.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close (B)", fontSize = 14.sp) }
                TextButton(onClick = onReleasePage, modifier = Modifier.heightIn(min = 48.dp)) { Text("Release page", fontSize = 14.sp) }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = UpdateGreen, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp).focusRequester(retryFocus),
                ) { Text("Retry", fontSize = 14.sp) }
            }
        }
    }
}
