package com.winhub.hernandez.ui.screens

import android.util.Log
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.communityconfigs.GameDoctor
import com.winhub.hernandez.core.ComponentUpdates
import com.winhub.hernandez.core.UpdateManager
import com.winhub.hernandez.store.download.DownloadScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "DeckUpdates"
private val PERCENT = Regex("(\\d+)%")

private enum class UpdatePhase { OFFER, WORKING, INSTALLED, SWITCHING, DONE, FAILED }

/**
 * Once per app run the Deck looks for newer GE-Proton, GPU driver and FEXCore builds and offers each in
 * a popup, one after the other (landscape and portrait share the same compact card). Download installs
 * the build; then, only if the user asks, the containers on an older one are moved over, with an Undo.
 * Follows the same "notify about updates" switch as the app updater.
 */
@Composable
internal fun DeckUpdatesHost() {
    val context = LocalContext.current
    var notices by remember { mutableStateOf<List<GameDoctor.Notice>>(emptyList()) }
    var noticeIndex by remember { mutableStateOf(0) }
    var offers by remember { mutableStateOf<List<ComponentUpdates.Offer>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        // Let the launch screen and the first focus settle before anything pops up.
        delay(2500)
        notices = withContext(Dispatchers.IO) { GameDoctor.pendingNotices(context) }
        if (!UpdateManager.isNotifyEnabled(context)) return@LaunchedEffect
        delay(1500)
        offers = withContext(Dispatchers.IO) { ComponentUpdates.checkAll(context) }
    }
    if (DeckConsole.boot || DeckConsole.screensaver) return
    val notice = notices.getOrNull(noticeIndex)
    val offer = offers.getOrNull(index)
    when {
        // What the doctor changed on its own comes first; updates wait until it has been seen.
        notice != null -> key(noticeIndex) { DeckDoctorNoticeDialog(notice, onClose = { noticeIndex++ }) }
        offer != null -> key(index) { DeckComponentUpdateDialog(offer, onClose = { index++ }) }
    }
}

@Composable
private fun DeckDoctorNoticeDialog(notice: GameDoctor.Notice, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var undone by remember { mutableStateOf<Boolean?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(undone) {
        delay(200)
        runCatching { firstFocus.requestFocus() }
    }
    fun close() {
        GameDoctor.dismissNotice(context, notice)
        onClose()
    }
    DeckConfigDialog(title = "Game Doctor", onDismiss = { close() }, compact = true) {
        Text(notice.game, color = DeckPalette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            when (undone) {
                true -> "Put back to ${presetName(notice.from)}."
                false -> "Couldn't put it back. You can change the FEX preset in the game's settings."
                null -> "It crashed soon after launch, so its CPU translation preset went from " +
                    "${presetName(notice.from)} to ${presetName(notice.to)}. Launch it again to try."
            },
            color = if (undone == false) DeckPalette.error else DeckPalette.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        DeckButton("OK", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) { close() }
        if (undone == null) {
            Spacer(Modifier.height(8.dp))
            DeckButton("Undo", primary = false, modifier = Modifier.fillMaxWidth()) {
                scope.launch { undone = withContext(Dispatchers.IO) { GameDoctor.undoNotice(context, notice) } }
            }
        }
    }
}

private fun presetName(id: String): String = id.replace('_', ' ').lowercase(Locale.ENGLISH).replaceFirstChar { it.uppercase() }

@Composable
private fun DeckComponentUpdateDialog(offer: ComponentUpdates.Offer, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(UpdatePhase.OFFER) }
    var status by remember { mutableStateOf("Starting") }
    var fraction by remember { mutableStateOf(0f) }
    var installedId by remember { mutableStateOf<String?>(null) }
    var switchable by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val firstFocus = remember { FocusRequester() }
    // The dialog moves focus to its close button on open; take it back for the primary action.
    LaunchedEffect(phase, switchable, undo) {
        delay(200)
        runCatching { firstFocus.requestFocus() }
    }

    // On the process-lifetime scope so a download in progress is not lost when the Deck screen is left.
    fun startInstall() {
        phase = UpdatePhase.WORKING
        job = DownloadScope.io.launch {
            val id = try {
                offer.install { message ->
                    status = message
                    PERCENT.find(message)?.groupValues?.get(1)?.toIntOrNull()?.let { fraction = it / 100f }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${offer.kind} update install failed", e)
                null
            }
            if (id == null) {
                phase = UpdatePhase.FAILED
            } else {
                installedId = id
                switchable = withContext(Dispatchers.IO) { offer.candidates() }
                phase = UpdatePhase.INSTALLED
            }
        }
    }

    DeckConfigDialog(
        title = when (phase) {
            UpdatePhase.OFFER -> "${offer.kind} update available"
            else -> "${offer.kind} update"
        },
        onDismiss = {
            when (phase) {
                UpdatePhase.SWITCHING -> {}
                UpdatePhase.WORKING -> { job?.cancel(); onClose() }
                else -> onClose()
            }
        },
        compact = true,
    ) {
        Text(offer.label, color = DeckPalette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        if (phase == UpdatePhase.WORKING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(DeckPalette.line2),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(DeckPalette.primary),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        val failed = phase == UpdatePhase.FAILED
        Text(
            when (phase) {
                UpdatePhase.OFFER ->
                    "You have ${offer.installedLabel}." + (offer.sizeBytes?.let { " Download ${fmtMb(it)}." } ?: "") +
                        " " + offerHint(offer.kind)
                UpdatePhase.WORKING -> status
                UpdatePhase.INSTALLED ->
                    if (switchable > 0) {
                        "Installed. Switch $switchable container${if (switchable == 1) "" else "s"} to it now? " +
                            "Containers keep their other settings, and you can undo this right after."
                    } else "Installed. Pick it in a container's settings."
                UpdatePhase.SWITCHING -> "Switching containers…"
                UpdatePhase.DONE -> note.orEmpty()
                UpdatePhase.FAILED -> "The download or install failed. Check your connection and try again later from Contents."
            },
            color = if (failed) DeckPalette.error else DeckPalette.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))

        when (phase) {
            UpdatePhase.OFFER -> {
                DeckButton("Download", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) { startInstall() }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    DeckButton("Later", primary = false, modifier = Modifier.weight(1f), onClick = onClose)
                    DeckButton("Skip version", primary = false, modifier = Modifier.weight(1f)) {
                        offer.skip()
                        onClose()
                    }
                }
            }
            UpdatePhase.WORKING ->
                DeckButton("Cancel", primary = false, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) {
                    job?.cancel()
                    onClose()
                }
            UpdatePhase.INSTALLED ->
                if (switchable > 0) {
                    DeckButton("Switch containers", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus)) {
                        val id = installedId ?: return@DeckButton
                        phase = UpdatePhase.SWITCHING
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { offer.switchAll(id) }
                            undo = result.undo
                            note = switchSummary(result)
                            phase = UpdatePhase.DONE
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DeckButton("Not now", primary = false, modifier = Modifier.fillMaxWidth(), onClick = onClose)
                } else {
                    DeckButton("Done", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus), onClick = onClose)
                }
            UpdatePhase.SWITCHING -> {}
            UpdatePhase.DONE -> {
                DeckButton("Done", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus), onClick = onClose)
                val revert = undo
                if (revert != null) {
                    Spacer(Modifier.height(8.dp))
                    DeckButton("Undo", primary = false, modifier = Modifier.fillMaxWidth()) {
                        undo = null
                        scope.launch {
                            withContext(Dispatchers.IO) { revert() }
                            note = "Put back the way it was."
                        }
                    }
                }
            }
            UpdatePhase.FAILED ->
                DeckButton("Close", primary = true, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus), onClick = onClose)
        }
    }
}

private fun offerHint(kind: String): String = when (kind) {
    "Proton" -> "It is added to your layers. Your containers keep the layer they use until you switch them."
    "Driver" -> "It is added to your drivers. Containers on an older build can be switched once it is installed."
    else -> "It is added to your FEXCore versions. Containers on an older build can be switched once it is installed."
}

private fun switchSummary(r: ComponentUpdates.SwitchResult): String {
    val moved = if (r.moved == 0) "No container was switched." else
        "Switched ${r.moved} container${if (r.moved == 1) "" else "s"}."
    return if (r.failed.isEmpty()) moved else moved + " " + r.failed.joinToString(" ")
}

private fun fmtMb(bytes: Long): String = String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024.0 * 1024))
