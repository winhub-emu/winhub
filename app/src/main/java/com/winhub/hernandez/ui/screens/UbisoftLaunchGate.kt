package com.winhub.hernandez.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.UbisoftInstallerDownload
import com.winhub.hernandez.store.UbisoftPendingLaunch
import com.winhub.hernandez.store.UbisoftSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "UbisoftLaunchGate"

/** How long the Ubisoft check ([UbisoftLaunchGate.launchWithUbisoftGate]) may take before the game just launches. */
private const val UBISOFT_GATE_WAIT_MS = 4_000L

/** The download dialog's progress is redrawn at most this often (about four times a second). */
private const val UBISOFT_PROGRESS_STEP_MS = 250L

/**
 * The Ubisoft Connect setup question, shared by every screen that starts a game (Games list, Deck home, Games wall,
 * Big Picture): one check, one dialog, then the screen's own launch runs. See [UbisoftSupport] for what counts as a
 * Ubisoft title and what the setup does.
 *
 * Hold one with [rememberUbisoftLaunchGate], launch through [launchWithUbisoftGate] and draw [UbisoftSetupDialog] in the
 * same screen so the dialog has somewhere to appear.
 */
@Stable
class UbisoftLaunchGate internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    // A Ubisoft title whose container has no Ubisoft Connect yet.
    internal var setupFor by mutableStateOf<Shortcut?>(null)
    internal var setupBusy by mutableStateOf(false)
    // The "download" variant of the dialog (a game that bundles no installer or only a legacy one): Ubisoft's own
    // installer is fetched first. Progress in bytes (total 0 = connecting), a Cancel in flight, the last failure to
    // show, and the handle that aborts the transfer. The decision says why so the dialog can word it.
    internal var setupDownload by mutableStateOf(false)
    internal var setupDecision by mutableStateOf<UbisoftSupport.EpicDecision?>(null)
    internal var downloadDone by mutableLongStateOf(0L)
    internal var downloadTotal by mutableLongStateOf(0L)
    internal var setupCancelling by mutableStateOf(false)
    internal var setupError by mutableStateOf<String?>(null)
    private var downloadHandle: UbisoftInstallerDownload.Handle? = null
    // What "Launch anyway" runs and the screen's "I am done with this game" call, while the dialog is open.
    private var proceed: (() -> Unit)? = null
    private var onClosed: (() -> Unit)? = null
    // A gate check is deciding (a second tap on the same screen must not start another one).
    private var gateBusy = false

    private fun offerSetup(
        shortcut: Shortcut, download: Boolean, decision: UbisoftSupport.EpicDecision,
        proceed: () -> Unit, onClosed: () -> Unit,
    ) {
        this.proceed = proceed
        this.onClosed = onClosed
        setupDownload = download
        setupDecision = decision
        setupError = null
        setupCancelling = false
        downloadDone = 0L
        downloadTotal = 0L
        setupFor = shortcut
    }

    /**
     * The Ubisoft Connect setup check: only a Ubisoft candidate ([UbisoftSupport.gateKind], string tests) is looked at,
     * off the main thread and for at most a few seconds; every other game just gets [proceed] straight away.
     * FAIL-OPEN: an exception, a timeout or an unanswerable check all mean [proceed]. The dialog's "Launch anyway" and a
     * finished setup's automatic launch run the screen's launch, never this check again. [onDone] is called once when the
     * gate is finished with the game, whichever way (launched, set up started, cancelled).
     */
    fun launchWithUbisoftGate(shortcut: Shortcut, onDone: () -> Unit = {}, proceed: () -> Unit) {
        val kind = try {
            UbisoftPendingLaunch.clear(context)
            UbisoftSupport.gateKind(
                androidEntry = false,
                epicLike = UbisoftSupport.isEpicShortcut(shortcut),
                stamp = UbisoftSupport.epicStamp(shortcut),
                stampRechecked = UbisoftSupport.epicStampRechecked(shortcut),
                steamOrigin = isSteamOriginShortcut(shortcut),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "gate kind failed for ${shortcut.name}", t)
            UbisoftSupport.GateKind.NONE
        }
        if (kind == UbisoftSupport.GateKind.NONE) { proceed(); onDone(); return }
        if (gateBusy || setupFor != null) return
        gateBusy = true
        scope.launch {
            try {
                val decision = gateDecision(shortcut, kind)
                if (decision.setup == UbisoftSupport.EpicSetup.NONE) { proceed(); onDone() }
                else offerSetup(
                    shortcut, download = decision.setup == UbisoftSupport.EpicSetup.DOWNLOAD, decision = decision,
                    proceed = proceed, onClosed = onDone,
                )
            } finally {
                gateBusy = false
            }
        }
    }

    /** [UbisoftSupport.epicSetupFor] / [UbisoftSupport.steamSetupFor] off the main thread; no answer within [UBISOFT_GATE_WAIT_MS] (or a failure) = nothing to set up. */
    private suspend fun gateDecision(shortcut: Shortcut, kind: UbisoftSupport.GateKind): UbisoftSupport.EpicDecision {
        val work = scope.async(Dispatchers.IO) {
            runCatching {
                if (kind == UbisoftSupport.GateKind.EPIC) UbisoftSupport.epicSetupFor(context, shortcut)
                else UbisoftSupport.steamSetupFor(shortcut)
            }.getOrDefault(UbisoftSupport.EpicDecision.NOT_APPLICABLE)
        }
        return withTimeoutOrNull(UBISOFT_GATE_WAIT_MS) { work.await() } ?: UbisoftSupport.EpicDecision.NOT_APPLICABLE
    }

    /** Closes the setup dialog and tells the screen that opened it (once). */
    internal fun closeSetup() {
        val closed = onClosed
        setupFor = null
        proceed = null
        onClosed = null
        closed?.invoke()
    }

    /** The dialog's "Launch anyway": the opening screen's own launch, without the setup check. */
    internal fun launchAnyway() {
        val go = proceed
        val closed = onClosed
        setupFor = null
        proceed = null
        onClosed = null
        go?.invoke()
        closed?.invoke()
    }

    /**
     * The setup session for [s] was launched: remember the game so the app, once the session has ended and it is back,
     * starts it by itself ([resumePendingLaunch]). Not called when the session did not start or the download was cancelled.
     */
    internal fun setupStarted(s: Shortcut) {
        UbisoftPendingLaunch.record(context, s.file.path)
    }

    /**
     * The dialog's "Download and set up": fetches Ubisoft's installer (off the main thread) and hands it to the setup
     * stage. A failure stays on the dialog so the user can retry or launch anyway; [cancelDownload] aborts.
     */
    internal fun runDownloadSetup(s: Shortcut) {
        val handle = UbisoftInstallerDownload.Handle()
        downloadHandle = handle
        setupBusy = true
        setupCancelling = false
        setupError = null
        downloadDone = 0L
        downloadTotal = 0L
        scope.launch {
            val problem = withContext(Dispatchers.IO) {
                var lastWriteMs = 0L
                try {
                    val failure = UbisoftSupport.startSetupWithDownload(context, s.container, handle) { done, total ->
                        // The state is read by the dialog on the main thread: a few updates a second are plenty.
                        val now = System.currentTimeMillis()
                        if (now - lastWriteMs >= UBISOFT_PROGRESS_STEP_MS || done == total) {
                            lastWriteMs = now
                            downloadDone = done
                            downloadTotal = total
                        }
                        // The screen went away mid-download: stop the transfer instead of finishing it unseen.
                        if (!isActive) handle.cancel()
                    }
                    // A null answer that was not a Cancel means the setup session was launched: the game starts when it is
                    // done. Recorded here, in the same block, so leaving the screen right after the launch cannot skip it.
                    if (failure == null && !handle.cancelled) setupStarted(s)
                    failure
                } catch (t: Throwable) {
                    Log.w(TAG, "Ubisoft download setup failed", t)
                    context.getString(R.string.ubisoft_setup_failed)
                }
            }
            downloadHandle = null
            setupBusy = false
            setupCancelling = false
            if (problem != null && !handle.cancelled) setupError = problem
            else closeSetup()
        }
    }

    /** The dialog's "Set up" for a game that bundles the installer: starts the setup session. */
    internal fun runBundledSetup(s: Shortcut) {
        setupBusy = true
        scope.launch {
            val problem = withContext(Dispatchers.IO) {
                try { UbisoftSupport.startSetup(context, s) }
                catch (t: Throwable) {
                    Log.w(TAG, "Ubisoft setup failed", t)
                    context.getString(R.string.ubisoft_setup_failed)
                }
            }
            // Launched: the game starts by itself once the setup session is over and the app is back.
            if (problem != null) Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            else setupStarted(s)
            setupBusy = false
            closeSetup()
        }
    }

    /** Cancel on the download dialog: aborts the transfer (the partial file is deleted); the dialog closes once it has stopped. */
    internal fun cancelDownload() {
        setupCancelling = true
        downloadHandle?.cancel()
    }

    /**
     * The Ubisoft Connect setup this app started from the dialog is over (its session ended and the app restarted, which
     * is how this runs): start the game it was for through [launch]. The marker is read once and deleted first, so nothing
     * starts twice. Only when Connect is in the prefix now does the game start; otherwise nothing starts and the user is
     * told. A marker from this same process (a session that may still be running) or for a game not in [shortcuts] is
     * left alone. Returns true when it took the marker.
     */
    fun resumePendingLaunch(shortcuts: List<Shortcut>, launch: (Shortcut) -> Unit): Boolean {
        if (shortcuts.isEmpty()) return false
        val pending = UbisoftPendingLaunch.peek(context) ?: return false
        if (UbisoftPendingLaunch.recordedInThisProcess()) return false
        val s = shortcuts.firstOrNull { it.file.path == pending.shortcutPath } ?: return false
        UbisoftPendingLaunch.clear(context)
        val ageMs = System.currentTimeMillis() - pending.startedAt
        scope.launch {
            val installed = withContext(Dispatchers.IO) {
                runCatching { UbisoftSupport.launcherInstalled(s.container) }.getOrDefault(false)
            }
            when (UbisoftPendingLaunch.verdict(false, ageMs, installed)) {
                UbisoftPendingLaunch.Verdict.LAUNCH -> launch(s)
                UbisoftPendingLaunch.Verdict.NOT_FINISHED ->
                    Toast.makeText(context, context.getString(R.string.ubisoft_setup_unfinished, s.name ?: ""), Toast.LENGTH_LONG).show()
                UbisoftPendingLaunch.Verdict.WAIT, UbisoftPendingLaunch.Verdict.EXPIRED -> Unit
            }
        }
        return true
    }
}

@Composable
fun rememberUbisoftLaunchGate(): UbisoftLaunchGate {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context) { UbisoftLaunchGate(context, scope) }
}

/** The Ubisoft Connect setup dialog. Draw it where the launching screen draws its own dialogs. */
@Composable
fun UbisoftSetupDialog(gate: UbisoftLaunchGate) {
    val s = gate.setupFor ?: return
    val download = gate.setupDownload
    val busy = gate.setupBusy
    AlertDialog(
        onDismissRequest = { if (!busy) gate.closeSetup() },
        title = { Text(stringResource(R.string.ubisoft_setup_title)) },
        text = {
            Column {
                if (download) Text(when (gate.setupDecision?.reason) {
                    UbisoftSupport.EpicSetupReason.LEGACY_BUNDLED ->
                        stringResource(R.string.ubisoft_download_legacy_message, s.name ?: "", UbisoftInstallerDownload.APPROX_MB)
                    else -> stringResource(R.string.ubisoft_download_message, s.name ?: "", UbisoftInstallerDownload.APPROX_MB)
                })
                else Text(stringResource(R.string.ubisoft_setup_message, s.name ?: ""))
                if (download && busy) {
                    val done = gate.downloadDone
                    val total = gate.downloadTotal
                    Spacer(Modifier.height(12.dp))
                    if (total > 0L) {
                        LinearProgressIndicator(
                            progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (total > 0L) stringResource(
                            R.string.ubisoft_download_progress,
                            (done / (1024 * 1024)).toInt(), (total / (1024 * 1024)).toInt(), (done * 100 / total).toInt(),
                        ) else stringResource(R.string.ubisoft_download_connecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                gate.setupError?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (download) gate.runDownloadSetup(s) else gate.runBundledSetup(s)
            }) {
                Text(stringResource(when {
                    !busy -> if (download) R.string.ubisoft_download_confirm else R.string.ubisoft_setup_confirm
                    download -> R.string.ubisoft_download_downloading
                    else -> R.string.ubisoft_setup_starting
                }))
            }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = { gate.launchAnyway() }) {
                    Text(stringResource(R.string.ubisoft_setup_launch_anyway))
                }
                // While the installer downloads, Cancel aborts the transfer; the dialog closes once it has stopped.
                TextButton(enabled = !busy || (download && !gate.setupCancelling), onClick = {
                    if (busy) gate.cancelDownload() else gate.closeSetup()
                }) {
                    Text(stringResource(if (gate.setupCancelling) R.string.ubisoft_download_cancelling else android.R.string.cancel))
                }
            }
        },
    )
}
