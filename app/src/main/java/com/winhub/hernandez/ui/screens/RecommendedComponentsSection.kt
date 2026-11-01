@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.components.Component
import com.winhub.hernandez.components.ComponentCatalog
import com.winhub.hernandez.components.ComponentExecInstaller
import com.winhub.hernandez.components.ComponentInstallReturn
import com.winhub.hernandez.components.ComponentInstaller
import com.winhub.hernandez.components.DependencyDetector
import com.winhub.hernandez.components.DependencyDetector.Recommendation
import com.winhub.hernandez.components.PrefixInstalledDetector
import com.winhub.hernandez.container.Container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Recommended components" chip row — Pillar 2 / Phase 2.2. Detects the redistributables a game
 * bundles ([DependencyDetector]) and offers each as a one-tap install into the shortcut's container
 * prefix, reusing the EXACT install routing + installed-state tracking of [ComponentsSheet]
 * (SharedPreferences `"component_installs"`, keyed `"c<id>"`).
 *
 * Surfaced in two places: the "Confirm game" import dialog (pass [exeFile], the game's .exe) and the
 * per-game shortcut settings' Win Components tab (pass [gameDir]). Detection runs off the main thread;
 * the whole section renders nothing until at least one catalog-backed recommendation is ready, and
 * hides itself on any detection/catalog failure (best-effort — never crashes the host dialog).
 *
 * Install gating: [ComponentInstaller]/[ComponentExecInstaller] both require the container's `.wine`
 * prefix to already exist. At IMPORT time it usually doesn't, so a tap first checks for the prefix and,
 * if missing, shows "launch the game once" instead of failing. Installs are prefix-level (shared across
 * every shortcut on that container), not per-shortcut — the copy says so.
 */
@Composable
fun RecommendedComponentsSection(
    container: Container,
    exeFile: File? = null,
    gameDir: File? = null,
    // The originating shortcut's base name, when this section is shown for a specific game (the
    // "Confirm game" import dialog or a shortcut's Win Components tab). Persisted as a Tier-2 return
    // target so an installer-based component that restarts the app can bring the user back to THIS
    // shortcut's recommendations. null (container editor) → Tier-1 (Games) return only.
    shortcutBaseName: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    // Detection + catalog results (both off-main-thread); recs is filtered to components the catalog
    // actually carries, so every chip is installable in principle.
    var recs by remember { mutableStateOf<List<Recommendation>>(emptyList()) }
    var catalog by remember { mutableStateOf<Map<String, Component>>(emptyMap()) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Detection + catalog-load run off-main; `loading` gates a spinner row so the section isn't a blank
    // gap the user reads as "no recommendations" and skips past.
    var loading by remember { mutableStateOf(true) }
    var installing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmExec by remember { mutableStateOf<Component?>(null) }

    // Installed-state persistence — identical scheme to ComponentsSheet so a component installed there
    // (or on a prior visit) shows as checked here, and vice-versa.
    val installsPrefs = remember { context.getSharedPreferences("component_installs", Context.MODE_PRIVATE) }
    val installKey = "c${container.id}"
    fun markInstalled(name: String) {
        installed = installed + name
        installsPrefs.edit().putStringSet(installKey, installed).apply()
    }

    // Detect (filesystem I/O) + load the catalog off the main thread when the dialog opens / target
    // changes. Keyed on the exe/gameDir path so re-opening on a different game re-detects.
    LaunchedEffect(container.id, exeFile?.path, gameDir?.path) {
        loading = true
        val found = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    exeFile != null -> DependencyDetector.detectForExe(exeFile)
                    gameDir != null -> DependencyDetector.detect(gameDir)
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
        }
        if (found.isEmpty()) { recs = emptyList(); loading = false; return@LaunchedEffect }
        val cat = withContext(Dispatchers.IO) {
            runCatching { ComponentCatalog.load() }.getOrDefault(emptyList())
        }.associateBy { it.name }
        catalog = cat
        val recorded = installsPrefs.getStringSet(installKey, emptySet())?.toSet() ?: emptySet()
        val detected = withContext(Dispatchers.IO) { PrefixInstalledDetector.detect(container) }
        installed = recorded + detected
        // Keep only recommendations whose component exists in the catalog (else we can't install it).
        recs = found.filter { cat.containsKey(it.componentName) }
        loading = false
    }

    // Run an installer-based component (vcredist/.NET): opens a container session; the app restarts
    // when it ends. Mirrors ComponentsSheet.runExecInstall.
    fun runExecInstall(c: Component) {
        installing = c.name
        // Record a session-return target BEFORE launching: an install_exe/msi component opens a
        // container session that restarts the app, so this must be persisted first to survive it.
        // Cleared again below if no session actually launched (inline set_windows/uninstall, or error).
        ComponentInstallReturn.set(context, container.id, shortcutBaseName)
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                ComponentExecInstaller.startInstall(context, container, c) { /* progress not surfaced on chips */ }
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

    // A chip tap: gate on the prefix, then route to the same installer ComponentsSheet would.
    fun onChipTap(c: Component) {
        if (installing != null) return
        // The prefix must exist first — at import time it usually doesn't. Gate here rather than let
        // the installer return a failure, so the message is actionable.
        if (!File(container.rootDir, ".wine").isDirectory) {
            message = "Launch the game once first, then install its components from the game's settings."
            return
        }
        // Prefer the file-drop `_dll` variant when the catalog carries one (e.g. vcredist2010_dll): it
        // copies DLLs straight into the prefix, so there's no container session — no black screen, no
        // app restart (the Confirm dialog stays open), and it works on Proton 11. Falls back to the base
        // component when no `_dll` variant exists. We still record installed-state under the BASE name
        // (below), so the chip + `component_installs` prefs + PrefixInstalledDetector stay consistent.
        val target = catalog["${c.name}_dll"] ?: c
        // Same reason logic ComponentsSheet's row uses (exec-driver vs file-drop installer).
        val reason = if (ComponentExecInstaller.handlesComponent(target)) ComponentExecInstaller.execBlockedReason(target)
                     else ComponentInstaller.blockedReason(target)
        if (reason != null) { message = reason; return }
        when {
            // Has an installer step → confirm (the container will open), then run a session. Only the
            // base component reaches here; `_dll` variants are file-drop and take the else branch.
            ComponentExecInstaller.isExecComponent(target) -> confirmExec = target
            // Local-only but not pure file-drop (set_windows/uninstall) → run inline; no session.
            ComponentExecInstaller.handlesComponent(target) -> runExecInstall(target)
            else -> {
                installing = c.name // spinner/installed-state keyed on the BASE name the chip renders
                scope.launch {
                    val err = withContext(Dispatchers.IO) {
                        ComponentInstaller.install(context, container, target) { /* progress not surfaced on chips */ }
                    }
                    installing = null
                    if (err == null) markInstalled(c.name)
                    else message = "Couldn't install ${c.name}: $err"
                }
            }
        }
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
            title = { Text("Install ${c.name}", color = cs.onSurface) },
            text = {
                Text(
                    "This installs ${c.name} into this game's container (shared by every shortcut on it). " +
                        "The container opens to a Windows desktop and runs the installer — click through the " +
                        "installer window, then close the container to finish. You'll return here afterward.",
                    color = cs.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = { val comp = c; confirmExec = null; runExecInstall(comp) }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirmExec = null }) { Text("Cancel") } },
        )
    }

    // Still detecting / loading the catalog → a small spinner row, so an in-progress scan doesn't read
    // as "nothing recommended". Once loading finishes with no recs, the section hides entirely.
    if (loading) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Looking for recommended components…",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        return
    }

    // Nothing detected (or catalog unavailable) → render nothing at all.
    if (recs.isEmpty()) return

    // Split by how each was found: BUNDLED = redist installers the game ships (meant to install);
    // SHIPPED = loose runtime DLLs that usually already work. When both are present, label the two
    // groups distinctly so the user knows which are actually worth installing; with a single kind,
    // show just that one group (no empty header).
    val bundled = recs.filter { it.kind == DependencyDetector.Kind.BUNDLED }
    val shipped = recs.filter { it.kind == DependencyDetector.Kind.SHIPPED }

    Column(modifier = modifier.fillMaxWidth()) {
        if (bundled.isNotEmpty() && shipped.isNotEmpty()) {
            RecommendedChipGroup(
                title = "Recommended",
                subtitle = "Redistributables this game bundles.",
                recs = bundled, catalog = catalog, installed = installed, installing = installing,
                cs = cs, onChipTap = ::onChipTap,
            )
            Spacer(Modifier.height(12.dp))
            RecommendedChipGroup(
                title = "Optional",
                subtitle = "Ships with the game — install only if it misbehaves.",
                recs = shipped, catalog = catalog, installed = installed, installing = installing,
                cs = cs, onChipTap = ::onChipTap,
            )
        } else if (shipped.isNotEmpty()) {
            RecommendedChipGroup(
                title = "Optional components",
                subtitle = "Ships with the game — install only if it misbehaves.",
                recs = shipped, catalog = catalog, installed = installed, installing = installing,
                cs = cs, onChipTap = ::onChipTap,
            )
        } else {
            RecommendedChipGroup(
                title = "Recommended components",
                subtitle = "Redistributables this game bundles — tap to install into its container.",
                recs = bundled, catalog = catalog, installed = installed, installing = installing,
                cs = cs, onChipTap = ::onChipTap,
            )
        }
    }
}

/** One labeled group of recommendation chips (header + subtitle + wrapping chip row). Extracted so the
 *  BUNDLED / SHIPPED split renders the same chip logic twice without duplication. */
@Composable
private fun RecommendedChipGroup(
    title: String,
    subtitle: String,
    recs: List<Recommendation>,
    catalog: Map<String, Component>,
    installed: Set<String>,
    installing: String?,
    cs: ColorScheme,
    onChipTap: (Component) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recs.forEach { rec ->
                val c = catalog[rec.componentName] ?: return@forEach
                val isInstalled = c.name in installed
                val isInstalling = installing == c.name
                FilterChip(
                    selected = isInstalled,
                    enabled = installing == null || isInstalling,
                    onClick = { onChipTap(c) },
                    label = { Text(rec.label) },
                    leadingIcon = {
                        when {
                            isInstalling -> CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
                            )
                            isInstalled -> Icon(
                                Icons.Filled.CheckCircle, contentDescription = "Installed",
                                modifier = Modifier.size(18.dp),
                            )
                            else -> Icon(
                                Icons.Filled.Download, contentDescription = "Install",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}
