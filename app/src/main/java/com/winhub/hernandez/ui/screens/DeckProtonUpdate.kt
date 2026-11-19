package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.core.ProtonUpdateChecker
import com.winhub.hernandez.core.UpdateManager
import com.winhub.hernandez.store.download.ContentDownloadPhase
import com.winhub.hernandez.store.download.ContentDownloadRegistry
import com.winhub.hernandez.store.download.startContentDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Once per app run the Deck looks for a newer GE-Proton arm64ec in the Official catalog and, when
 * there is one, offers it in a popup (landscape and portrait share the same compact card). Download
 * installs the layer through the normal contents pipeline and swaps the card for its progress; no
 * container is changed. Follows the same "notify about updates" switch as the app updater.
 */
@Composable
internal fun DeckProtonUpdateHost() {
    val context = LocalContext.current
    var offer by remember { mutableStateOf<ProtonUpdateChecker.Available?>(null) }
    var closed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!UpdateManager.isNotifyEnabled(context)) return@LaunchedEffect
        // Let the launch screen and the first focus settle before anything pops up.
        delay(4000)
        offer = withContext(Dispatchers.IO) { ProtonUpdateChecker.check(context) }
    }
    val available = offer
    if (available != null && !closed && !DeckConsole.boot && !DeckConsole.screensaver) {
        DeckProtonUpdateDialog(available, onClose = { closed = true })
    }
}

@Composable
private fun DeckProtonUpdateDialog(available: ProtonUpdateChecker.Available, onClose: () -> Unit) {
    val context = LocalContext.current
    val registry by ContentDownloadRegistry.states.collectAsState()
    val downloadKey = remember(available) { ContentsManager.getEntryName(available.profile) }
    var started by remember { mutableStateOf(false) }
    val state = if (started) registry[downloadKey] else null
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var switchable by remember { mutableStateOf(0) }
    var switching by remember { mutableStateOf(false) }
    var switchNote by remember { mutableStateOf<String?>(null) }
    val installed = state?.phase == ContentDownloadPhase.DONE
    LaunchedEffect(installed) {
        if (installed) switchable = withContext(Dispatchers.IO) { ProtonUpdateChecker.switchCandidates(context, available.entryName).size }
    }
    fun closeDone() {
        ContentDownloadRegistry.remove(downloadKey)
        onClose()
    }
    // The dialog moves focus to its close button on open; take it back for the primary action.
    LaunchedEffect(started, state?.terminal, switchable, switching, switchNote) {
        delay(200)
        runCatching { firstFocus.requestFocus() }
    }

    if (state == null) {
        DeckConfigDialog(
            title = "Proton update available",
            onDismiss = onClose,
            compact = true,
        ) {
            Text(available.label, color = DeckPalette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "You have ${available.installedLabel}." + (available.sizeBytes?.let { " Download ${fmtMb(it)}." } ?: ""),
                color = DeckPalette.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "It is added to your layers. Once installed you can switch your containers to it, or leave them as they are.",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
            DeckButton(
                "Download",
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus),
            ) {
                startContentDownload(context.applicationContext, available.profile)
                started = true
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DeckButton("Later", primary = false, modifier = Modifier.weight(1f), onClick = onClose)
                DeckButton("Skip version", primary = false, modifier = Modifier.weight(1f)) {
                    ProtonUpdateChecker.skip(context, available)
                    onClose()
                }
            }
        }
        return
    }

    DeckConfigDialog(
        title = if (state.terminal) "Proton update" else "Installing Proton update",
        onDismiss = {
            when {
                switching -> {}
                state.terminal -> closeDone()
                else -> ContentDownloadRegistry.requestCancel(downloadKey)
            }
        },
        compact = true,
    ) {
        Text(available.label, color = DeckPalette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (!state.terminal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(DeckPalette.line2),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(DeckPalette.primary),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        val failed = state.phase == ContentDownloadPhase.ERROR && !state.cancelled
        val done = state.phase == ContentDownloadPhase.DONE
        val offerSwitch = done && switchable > 0 && switchNote == null
        Text(
            when {
                switching -> "Switching containers…"
                switchNote != null -> switchNote.orEmpty()
                offerSwitch -> "Installed. Switch $switchable container${if (switchable == 1) "" else "s"} to it now? " +
                    "Each one is backed up first, and Revert layer in its menu puts it back."
                done -> "Installed. Pick it in a container's Compatibility layer."
                else -> when (state.phase) {
                    ContentDownloadPhase.DOWNLOADING -> "Downloading ${(state.fraction * 100).toInt()}%"
                    ContentDownloadPhase.INSTALLING -> "Installing"
                    else -> if (state.cancelled) "Cancelled" else state.error ?: "Failed"
                }
            },
            color = if (failed) DeckPalette.error else DeckPalette.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        when {
            switching -> {}
            offerSwitch -> {
                DeckButton("Switch containers", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) {
                    switching = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { ProtonUpdateChecker.switchAll(context, available.entryName) }
                        switchNote = switchSummary(result)
                        switching = false
                    }
                }
                Spacer(Modifier.height(8.dp))
                DeckButton("Not now", primary = false, modifier = Modifier.fillMaxWidth()) { closeDone() }
            }
            state.terminal -> DeckButton("Done", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) { closeDone() }
            else -> DeckButton("Cancel", primary = false, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) {
                ContentDownloadRegistry.requestCancel(downloadKey)
            }
        }
    }
}

private fun switchSummary(r: ProtonUpdateChecker.SwitchResult): String {
    val moved = if (r.moved == 0) "No container was switched." else
        "Switched ${r.moved} container${if (r.moved == 1) "" else "s"}."
    return if (r.failed.isEmpty()) moved else moved + " " + r.failed.joinToString(" ")
}

private fun fmtMb(bytes: Long): String = String.format(Locale.ENGLISH, "%.0f MB", bytes / (1024.0 * 1024))
