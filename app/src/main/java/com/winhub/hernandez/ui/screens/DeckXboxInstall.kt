package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.formatDownloadSize
import com.winhub.hernandez.store.download.formatDownloadSpeed
import com.winhub.hernandez.store.download.formatEta
import com.winhub.hernandez.store.xbox.XboxDownloads
import com.winhub.hernandez.store.xbox.XboxGame
import com.winhub.hernandez.store.xbox.XboxInstallState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-Deck Xbox install. Drives the real [XboxDownloads] engine (resolve the PC package, then the
 * Xodus streaming download) and follows its progress in the shared [DownloadRegistry], so install
 * never leaves the Deck shell. Games with several PC packages ask which one in a Deck dialog.
 */
@Composable
internal fun DeckXboxInstall(game: XboxGame, onBack: () -> Unit, onOpenStore: (() -> Unit)? = null) {
    val appCtx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val entries by DownloadRegistry.entries.collectAsState()
    val entry = entries.firstOrNull { it.key == "${Store.XBOX}:${game.id}" }
    var installed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var choices by remember { mutableStateOf<List<XboxDownloads.PackageChoice>?>(null) }
    val downloading = entry != null && (entry.state == DownloadState.DOWNLOADING ||
        entry.state == DownloadState.PAUSED || entry.state == DownloadState.QUEUED)

    // Re-read the install record whenever a download starts, ends or fails.
    LaunchedEffect(entry?.state) {
        installed = withContext(Dispatchers.IO) { XboxInstallState.isInstalled(appCtx, game.id) }
        if (entry?.state == DownloadState.FAILED) errorText = entry?.error ?: "Xbox download failed."
    }

    val status = when {
        busy -> "Checking the PC package..."
        downloading && entry != null -> listOf(
            if (entry.installTotal > 0) "${entry.pct}%  ·  ${formatDownloadSize((entry.installTotal - entry.installDone).coerceAtLeast(0))} to go"
            else "Downloading... ${entry.pct}%",
            formatDownloadSpeed(entry.speedBps), formatEta(entry.etaSeconds),
        ).filter { it.isNotEmpty() }.joinToString("  ·  ")
        else -> ""
    }

    choices?.let { list ->
        DeckConfigDialog(title = "Choose the PC package", subtitle = game.title, onDismiss = { choices = null }) {
            list.forEach { c ->
                DeckButton(c.label, primary = false, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    choices = null
                    XboxDownloads.start(appCtx, game, c.contentId)
                }
            }
            DeckButton("Cancel", primary = false, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { choices = null }
        }
    }

    DeckInstallShell(
        title = game.title,
        art = game.heroUrl ?: game.posterUrl,
        dev = game.developer,
        onBack = onBack,
        progress = if (downloading) entry?.pct ?: 0 else if (busy) 0 else -1,
        status = status,
        finished = installed && !downloading,
        error = errorText,
        started = busy || downloading,
        storeLabel = "Xbox",
        onOpenStore = onOpenStore,
        publisher = game.publisher,
        description = game.description,
        canInstall = game.pc,
        installed = installed,
        addGame = DeckAddRequest(
            title = game.title,
            store = "Xbox",
            coverUrl = game.posterUrl ?: game.heroUrl,
            extras = mapOf("storeSource" to "xbox", "xboxProductId" to game.id),
            resolveExe = {
                withContext(Dispatchers.IO) {
                    XboxInstallState.get(appCtx, game.id)?.let { File(it.folder, it.exe).path }
                }
            },
        ),
        onStart = {
            errorText = null
            busy = true
            scope.launch {
                val resolved = withContext(Dispatchers.IO) { runCatching { XboxDownloads.resolve(appCtx, game) } }
                busy = false
                resolved.onFailure { errorText = it.message ?: "The Xbox package could not be resolved." }
                    .onSuccess { list ->
                        if (list.size == 1) XboxDownloads.start(appCtx, game, list.single().contentId)
                        else choices = list
                    }
            }
        },
        onCancel = { XboxDownloads.cancel(game.id) },
    )
}
