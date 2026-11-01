package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.winhub.hernandez.components.ComponentExecInstaller
import com.winhub.hernandez.components.ComponentInstallReturn
import com.winhub.hernandez.ui.ComponentReturnBus
import com.winhub.hernandez.ui.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown on app startup when an installer-based component install is mid-flight (its previous
 * installer session ran and the app restarted). Lets the user finish — which applies the remaining
 * registry/override steps and launches the next installer if the component has one — or discard it.
 */
@Composable
fun ComponentInstallResume(onNavigateToGames: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var pending by remember { mutableStateOf(ComponentExecInstaller.pendingComponentName(context)) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }

    // Consume the session-return target set at the source (RecommendedComponentsSection / ComponentsSheet)
    // before the install session restarted the app: land back on Games (Tier 1) and, when a shortcut is
    // known, re-open its settings on the recommended-components tab (Tier 2). Best-effort — a missing or
    // partial target just falls back to Games / a no-op. [installedName] non-null → show a success toast.
    fun consumeReturn(installedName: String?) {
        val target = runCatching { ComponentInstallReturn.get(context) }.getOrNull()
        ComponentInstallReturn.clear(context)
        installedName?.let {
            runCatching { Toast.makeText(context, "Installed $it.", Toast.LENGTH_SHORT).show() }
        }
        // Tier 2: ask the Shortcuts screen to open this shortcut's settings once we arrive.
        target?.shortcutBase?.let { base ->
            ComponentReturnBus.openShortcutSettings =
                ComponentReturnBus.ShortcutTarget(target.containerId, base)
        }
        // Tier 1: always route to Games so the user is never stranded on the resume dialog / default screen.
        runCatching { onNavigateToGames() }
    }

    message?.let { m ->
        OutlinedAlertDialog(
            onDismissRequest = { message = null },
            containerColor = cs.surfaceContainerHigh,
            text = { Text(m, color = cs.onSurface) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    val name = pending ?: return

    OutlinedAlertDialog(
        onDismissRequest = { /* keep until the user chooses */ },
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Finish installing $name", color = cs.onSurface) },
        text = {
            Column {
                Text(
                    if (busy) "Working on $name…"
                    else "The $name installer ran. Finish setting it up in the container? " +
                        "If it needs another installer, the container will open again.",
                    color = cs.onSurface,
                )
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        // intentional: status color (install-in-progress cyan, distinct from action accent)
                        color = Color(0xFF4FC3F7), trackColor = cs.surfaceContainerHighest,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val res = withContext(Dispatchers.IO) {
                        ComponentExecInstaller.resume(context) { f -> activity?.runOnUiThread { progress = f } }
                    }
                    busy = false
                    pending = null
                    when (res) {
                        // Another installer session is coming — DON'T return yet; the return target
                        // stays persisted for the next resume after that session restarts the app.
                        is ComponentExecInstaller.Result.Launched -> { /* heading into the next session */ }
                        is ComponentExecInstaller.Result.Done -> {
                            consumeReturn(name)
                            // EA setup chain: the mono prerequisite just finished → run the deferred
                            // installScript Run-Process step (EA Desktop installer) in its own session.
                            withContext(Dispatchers.IO) {
                                try { com.winhub.hernandez.store.steamscript.InstallScriptExecutor.resumePending(context) }
                                catch (t: Throwable) { android.util.Log.w("ComponentInstallResume", "installScript resume failed", t) }
                            }
                        }
                        is ComponentExecInstaller.Result.Error -> message = "Couldn't finish $name: ${res.message}"
                        null -> {}
                    }
                }
            }) { Text("Finish") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                ComponentExecInstaller.clearPlan(context)
                pending = null
                // Even on discard, return to Games so the user isn't stranded on the default screen.
                // No success toast (nothing was installed); Tier 2 still re-opens the shortcut so they
                // can retry from its recommendations.
                consumeReturn(null)
            }) { Text("Discard", color = Color(0xFFE57373)) } // intentional: destructive-action red
        },
    )
}
