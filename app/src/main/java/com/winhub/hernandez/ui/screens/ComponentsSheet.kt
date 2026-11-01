package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import com.winhub.hernandez.ui.components.TextButton
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.components.Component
import com.winhub.hernandez.components.ComponentCatalog
import com.winhub.hernandez.components.ComponentExecInstaller
import com.winhub.hernandez.components.ComponentInstallReturn
import com.winhub.hernandez.components.ComponentInstaller
import com.winhub.hernandez.components.PrefixInstalledDetector
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.theme.Divider as DividerColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Browse + install Wine dependency components (mono, gecko, dotnet, vcredist, d3dx, …) into a
 * container's prefix. Reads the live components.json catalog from winlator-contents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentsSheet(container: Container, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var components by remember { mutableStateOf<List<Component>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmExec by remember { mutableStateOf<Component?>(null) }

    // "Installed" must survive closing/reopening the sheet, so persist it per container
    // (the in-memory set alone reset every time the sheet recomposed).
    val installsPrefs = remember { context.getSharedPreferences("component_installs", Context.MODE_PRIVATE) }
    val installKey = "c${container.id}"
    fun markInstalled(name: String) {
        installed = installed + name
        installsPrefs.edit().putStringSet(installKey, installed).apply()
    }

    // Run an installer-based component: download its installer, then launch the container session.
    fun runExecInstall(c: Component) {
        installing = c.name; progress = 0f
        // Container editor has no originating shortcut → container-only return target (Tier 1: land
        // back on Games instead of stranding the user after the install session restarts the app).
        // Cleared below if no session actually launched (inline set_windows/uninstall, or error).
        ComponentInstallReturn.set(context, container.id, null)
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                ComponentExecInstaller.startInstall(context, container, c) { f ->
                    activity?.runOnUiThread { progress = f }
                }
            }
            installing = null
            when (res) {
                is ComponentExecInstaller.Result.Launched -> { /* session launched; return target consumed after restart */ }
                is ComponentExecInstaller.Result.Done -> { markInstalled(c.name); ComponentInstallReturn.clear(context) }
                is ComponentExecInstaller.Result.Error -> {
                    message = "Couldn't install ${c.name}: ${res.message}"
                    ComponentInstallReturn.clear(context)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val recorded = installsPrefs.getStringSet(installKey, emptySet())?.toSet() ?: emptySet()
        // Backfill: components installed before we recorded installs (or by hand) that leave a
        // non-baseline prefix footprint (OpenAL/mono/PhysX/GFWL) — so they show as installed too.
        val detected = withContext(Dispatchers.IO) { PrefixInstalledDetector.detect(container) }
        installed = recorded + detected
        val list = withContext(Dispatchers.IO) { ComponentCatalog.load() }
        components = list
        loadError = list.isEmpty()
        loading = false
    }

    message?.let { m ->
        OutlinedAlertDialog(
            onDismissRequest = { message = null },
            containerColor = cs.surfaceContainerHigh,
            text = { Text(m, color = cs.onSurface) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }

    confirmExec?.let { c ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmExec = null },
            containerColor = cs.surfaceContainerHigh,
            title = { Text("Run ${c.name} installer", color = cs.onSurface) },
            text = {
                Text(
                    "This opens the container to a Windows desktop and runs the ${c.name} installer. " +
                        "Click through the installer window that appears, then close the container when it's " +
                        "done — you'll be brought back to finish setup.",
                    color = cs.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = { val comp = c; confirmExec = null; runExecInstall(comp) }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirmExec = null }) { Text("Cancel") } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = cs.surface, contentColor = cs.onSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(bottom = 12.dp)) {
            Text("Components", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp))
            Text("Install Wine dependencies into this container",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search components") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Divider(color = DividerColor)

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = cs.primary)
                }
                loadError -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    Text("Couldn't load the component catalog.", color = cs.onSurfaceVariant)
                }
                else -> {
                    // Name → component, for routing a base install to its file-drop `_dll` variant below.
                    // Built from the full list (the `_dll` variants are hidden from `shown`, not dropped).
                    val byName = remember(components) { components.associateBy { it.name } }
                    // Installed components float to the top (checked), so what the container already
                    // has is obvious and users don't reinstall. Stable within each group; re-sorts
                    // live when something finishes installing (installed is a key). The file-drop `_dll`
                    // variants are install-implementation (reached via routing in onInstall), so they're
                    // filtered out here — otherwise vcredist2010 AND vcredist2010_dll show as duplicates.
                    val shown = remember(components, query, installed) {
                        components.filter {
                            !it.name.endsWith("_dll") &&
                                (query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true))
                        }.sortedByDescending { it.name in installed }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.name }) { c ->
                                ComponentRow(
                                    c = c,
                                    isInstalled = c.name in installed,
                                    isInstalling = installing == c.name,
                                    progress = if (installing == c.name) progress else null,
                                    enabled = installing == null,
                                    onInstall = {
                                        // Prefer the file-drop `_dll` variant when the catalog carries one:
                                        // it copies DLLs straight into the prefix (no container session ⇒
                                        // no black screen / app restart, works on Proton 11). The base
                                        // component is what's rendered; installed-state is still recorded
                                        // under the BASE name so the row + prefs stay consistent.
                                        val target = byName["${c.name}_dll"] ?: c
                                        when {
                                            // Has an installer step → confirm, then run a container session.
                                            // Only the base reaches here; `_dll` variants are file-drop.
                                            ComponentExecInstaller.isExecComponent(target) -> confirmExec = target
                                            // Local-only but not pure file-drop (set_windows/uninstall) → run
                                            // inline via the exec driver; no session, no confirm needed.
                                            ComponentExecInstaller.handlesComponent(target) -> runExecInstall(target)
                                            else -> {
                                                installing = c.name; progress = 0f
                                                scope.launch {
                                                    val err = withContext(Dispatchers.IO) {
                                                        ComponentInstaller.install(context, container, target) { f ->
                                                            activity?.runOnUiThread { progress = f }
                                                        }
                                                    }
                                                    installing = null
                                                    if (err == null) markInstalled(c.name)
                                                    else message = "Couldn't install ${c.name}: $err"
                                                }
                                            }
                                        }
                                    },
                                )
                                Divider(color = DividerColor.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close", color = cs.primary) }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    c: Component,
    isInstalled: Boolean,
    isInstalling: Boolean,
    progress: Float?,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val installedBlue = Color(0xFF4FC3F7) // intentional: status color (installed indicator, distinct from action accent)
    // Components needing the exec engine (installer steps, or set_windows/uninstall) are gated by it;
    // the rest by the file-drop installer.
    val reason = if (ComponentExecInstaller.handlesComponent(c)) ComponentExecInstaller.execBlockedReason(c)
                 else ComponentInstaller.blockedReason(c)
    val installable = reason == null
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                val sub = when {
                    isInstalled -> "Installed"
                    !installable -> reason ?: ""
                    c.description.isNotEmpty() -> c.description
                    else -> c.provider
                }
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall,
                        color = if (isInstalled) installedBlue else cs.onSurfaceVariant)
                }
            }
            when {
                isInstalling -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                isInstalled -> Icon(Icons.Filled.CheckCircle, contentDescription = "Installed",
                    tint = installedBlue, modifier = Modifier.size(22.dp))
                installable -> IconButton(onClick = onInstall, enabled = enabled) {
                    Icon(Icons.Filled.Download, contentDescription = "Install", tint = cs.primary)
                }
                else -> Box(
                    Modifier.background(cs.surfaceContainerHigh, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("N/A", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
            }
        }
        if (isInstalling) {
            Spacer(Modifier.height(6.dp))
            val frac = (progress ?: 0f).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth().height(4.dp),
                color = cs.primary, trackColor = cs.surfaceContainerHighest)
        }
    }
}
