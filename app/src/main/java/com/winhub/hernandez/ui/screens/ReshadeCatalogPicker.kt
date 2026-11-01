package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.reshade.ReshadeCatalog
import com.winhub.hernandez.reshade.ReshadeCatalogEntry
import com.winhub.hernandez.reshade.ReshadeDownloader
import com.winhub.hernandez.reshade.ReshadeManager
import com.winhub.hernandez.ui.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pre-launch ReShade LOADOUT picker (Tier 1) — used by the container editor + per-game shortcut
 * editor. Shows the current loadout summary + a "Browse / download" button that opens a catalog sheet
 * listing EVERY effect from reshade.json: installed effects carry a checkbox that toggles loadout
 * membership; not-yet-downloaded ones are greyed with a download affordance (tapping downloads, then
 * the row "fills in" and is auto-added to the loadout). Multi-select — the sheet stays open so several
 * effects can be added in one pass. [onCatalogChanged] lets the parent rescan the drop-in folder (so
 * params seed) after an install.
 */
@Composable
fun ReshadeLoadoutPicker(
    selectedNames: List<String>,
    supported: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    onCatalogChanged: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("ReShade loadout", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text(
                if (selectedNames.isEmpty()) "None"
                else "${selectedNames.size} effect${if (selectedNames.size == 1) "" else "s"}: ${selectedNames.joinToString(", ")}",
                style = MaterialTheme.typography.bodyLarge, color = cs.onSurface,
            )
        }
        OutlinedButton(onClick = { showSheet = true }) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Browse")
        }
    }
    if (!supported && selectedNames.isNotEmpty()) {
        Text(
            "ReShade only applies to DXVK/VKD3D (Vulkan) games; it has no effect with this DX wrapper.",
            style = MaterialTheme.typography.bodySmall, color = cs.error,
            modifier = Modifier.padding(top = 2.dp),
        )
    }

    if (showSheet) {
        ReshadeCatalogSheet(
            selectedNames = selectedNames.toSet(),
            onDismiss = { showSheet = false },
            onToggle = onToggle,
            onClear = onClear,
            onCatalogChanged = onCatalogChanged,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReshadeCatalogSheet(
    selectedNames: Set<String>,
    onDismiss: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    onCatalogChanged: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var catalog by remember { mutableStateOf<List<ReshadeCatalogEntry>>(emptyList()) }
    var installed by remember { mutableStateOf(ReshadeManager.scanEffectNames(context).toSet()) }
    var source by remember { mutableStateOf(ReshadeCatalog.Source.NONE) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var phaseLabel by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ReshadeCatalogEntry?>(null) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { ReshadeCatalog.loadCached(context) }
        catalog = result.entries
        source = result.source
        installed = withContext(Dispatchers.IO) { ReshadeManager.scanEffectNames(context).toSet() }
        loading = false
    }

    // No live connection: only NETWORK means the catalog is current and not-installed effects are
    // downloadable. CACHE/NONE = offline → greyed rows can't be fetched.
    val offline = source != ReshadeCatalog.Source.NETWORK

    // Selection is case-insensitive against the effect id.
    val selectedLc = remember(selectedNames) { selectedNames.map { it.lowercase() }.toSet() }
    fun isSelected(id: String) = id.lowercase() in selectedLc

    // Rows: catalog entries + any locally-installed effect not in the catalog (user-dropped), then
    // filtered by the search query (name/author/category) and split into the pinned "Installed"
    // group and the "Available" group, each sorted A→Z by display name.
    val groups = remember(catalog, installed, query) {
        val q = query.trim()
        val catIds = catalog.map { it.id }.toSet()
        val extras = installed.filter { it !in catIds }
            .map { ReshadeCatalogEntry(it, it, "", "Installed", "", "", "", 0L, "", 1) }
        val all = (catalog + extras).filter { e ->
            q.isEmpty() || e.name.contains(q, true) || e.author.contains(q, true) || e.category.contains(q, true)
        }
        val byName = compareBy<ReshadeCatalogEntry> { it.name.lowercase() }
        Pair(
            all.filter { it.id in installed }.sortedWith(byName),
            all.filter { it.id !in installed }.sortedWith(byName),
        )
    }
    val installedRows = groups.first
    val availableRows = groups.second

    fun startDownload(entry: ReshadeCatalogEntry) {
        downloadingId = entry.id
        phaseLabel = "Downloading"; progress = 0f; errorMsg = null
        scope.launch {
            val ok = ReshadeDownloader.install(context, entry) { phase, f ->
                activity?.runOnUiThread {
                    phaseLabel = if (phase == ReshadeDownloader.Phase.EXTRACT) "Installing" else "Downloading"
                    progress = f
                }
            }
            downloadingId = null
            if (ok) {
                installed = installed + entry.id
                onCatalogChanged()      // parent rescans → params seed
                onToggle(entry.id, true) // auto-add the freshly installed effect to the loadout
            } else errorMsg = "Failed to download ${entry.name}."
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        contentColor = cs.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ReShade Effects",
                    color = cs.onSurface, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (selectedNames.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear all", color = cs.primary) }
                }
            }
            if (offline && !loading) {
                Text(
                    if (source == ReshadeCatalog.Source.CACHE)
                        "Offline — showing a cached list. Connect to download new effects."
                    else "Offline — showing installed effects only. Connect to browse the catalog.",
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            // Search bar — filters the visible list (both groups) as the user types.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search effects…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear",
                            modifier = Modifier.size(20.dp).clickable { query = "" })
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Divider(color = cs.outlineVariant)

            errorMsg?.let {
                Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = cs.primary)
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (installedRows.isNotEmpty()) {
                            item("__installed_hdr__") { GroupHeader("Installed (${installedRows.size})") }
                            items(installedRows, key = { "i_${it.id}" }) { entry ->
                                CatalogRowItem(entry, installed, ::isSelected, downloadingId, phaseLabel, progress, offline, cs,
                                    onToggle, { startDownload(it) }, { pendingDelete = entry }) { errorMsg = it }
                                Divider(color = cs.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

                        if (availableRows.isNotEmpty()) {
                            item("__available_hdr__") { GroupHeader("Available (${availableRows.size})") }
                            items(availableRows, key = { "a_${it.id}" }) { entry ->
                                CatalogRowItem(entry, installed, ::isSelected, downloadingId, phaseLabel, progress, offline, cs,
                                    onToggle, { startDownload(it) }, { pendingDelete = entry }) { errorMsg = it }
                                Divider(color = cs.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

                        if (installedRows.isEmpty() && availableRows.isEmpty()) {
                            item("__empty__") {
                                Text(
                                    if (query.isNotBlank()) "No effects match \"$query\"."
                                    else "No effects available. Connect to the internet to browse the catalog.",
                                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Done", color = cs.primary) }
            }
        }
    }

    // Confirm-then-delete: removes the effect's folder from the ReShade dir, drops the row back to
    // "Available", and takes it out of the loadout. Re-downloadable afterwards.
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${entry.name}?") },
            text = { Text("This removes the downloaded effect from storage and takes it out of your loadout. You can re-download it any time.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = entry.id
                    pendingDelete = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { ReshadeManager.deleteEffect(context, id) }
                        if (ok) {
                            installed = installed - id      // row drops back to the "Available" group
                            onToggle(id, false)             // and out of the current loadout
                            onCatalogChanged()              // parent rescans the drop-in folder
                        } else errorMsg = "Couldn't delete ${entry.name}."
                    }
                }) { Text("Delete", color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = cs.primary) }
            },
        )
    }
}

// One catalog row + its tap behaviour: installed rows TOGGLE loadout membership; available rows
// download (if online) or hint (if offline). Extracted so both groups share it.
@Composable
private fun CatalogRowItem(
    entry: ReshadeCatalogEntry,
    installed: Set<String>,
    isSelected: (String) -> Boolean,
    downloadingId: String?,
    phaseLabel: String,
    progress: Float,
    offline: Boolean,
    cs: ColorScheme,
    onToggle: (String, Boolean) -> Unit,
    onDownload: (ReshadeCatalogEntry) -> Unit,
    onDelete: () -> Unit,
    onHint: (String) -> Unit,
) {
    val isInstalled = entry.id in installed
    val isBusy = downloadingId == entry.id
    val selected = isSelected(entry.id)
    ReshadeCatalogRow(
        entry = entry,
        isInstalled = isInstalled,
        isSelected = selected,
        isBusy = isBusy,
        offline = offline,
        phaseLabel = if (isBusy) phaseLabel else "",
        progress = if (isBusy) progress else null,
        // Delete only makes sense for a downloaded effect; hidden while a download is in flight.
        onDelete = if (isInstalled && !isBusy) onDelete else null,
        onClick = {
            when {
                isInstalled -> onToggle(entry.id, !selected)             // toggle membership
                downloadingId != null -> {}                              // one at a time
                offline -> onHint("Connect to the internet to download effects.")
                else -> onDownload(entry)
            }
        },
    )
}

@Composable
private fun GroupHeader(text: String) {
    val cs = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = cs.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ReshadeCatalogRow(
    entry: ReshadeCatalogEntry,
    isInstalled: Boolean,
    isSelected: Boolean,
    isBusy: Boolean,
    offline: Boolean,
    phaseLabel: String,
    progress: Float?,
    onDelete: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val installedBlue = Color(0xFF4FC3F7) // intentional: status color (in-loadout indicator, distinct from action accent)
    // Not-installed rows render greyed; tapping them downloads. Installed rows are full-opacity and
    // carry a checkbox reflecting loadout membership.
    val contentAlpha = if (isInstalled || isBusy) 1f else 0.5f
    Column(
        Modifier.fillMaxWidth().clickable(enabled = !isBusy, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name, style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface.copy(alpha = contentAlpha),
                )
                val sub = buildString {
                    if (entry.category.isNotBlank()) append(entry.category)
                    if (entry.author.isNotBlank()) { if (isNotEmpty()) append(" · "); append(entry.author) }
                    if (entry.license.isNotBlank()) { if (isNotEmpty()) append(" · "); append(entry.license) }
                }
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = contentAlpha))
                }
                if (entry.description.isNotBlank()) {
                    Text(entry.description, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = contentAlpha))
                }
                // Offline + not yet downloaded: tell the user a connection is needed.
                if (!isInstalled && offline && !isBusy) {
                    Text("Needs connection to download", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = contentAlpha))
                }
            }
            when {
                isBusy -> {}
                isInstalled -> Row(verticalAlignment = Alignment.CenterVertically) {
                    // Trash: its own tap target, so it never triggers the row's loadout toggle.
                    onDelete?.let { del ->
                        IconButton(onClick = del, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete download",
                                tint = cs.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        }
                    }
                    Icon(
                        if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null, tint = if (isSelected) installedBlue else cs.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                offline -> Icon(Icons.Filled.CloudOff, contentDescription = "Offline", tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
                else -> Icon(Icons.Filled.CloudDownload, contentDescription = "Download", tint = cs.primary, modifier = Modifier.size(22.dp))
            }
        }
        if (isBusy) {
            Spacer(Modifier.height(6.dp))
            val frac = progress?.coerceIn(0f, 1f) ?: 0f
            val barColor = if (phaseLabel == "Installing") Color(0xFF4CAF50) else cs.primary // intentional: status color (green = installing/extract phase)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(phaseLabel, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth().height(4.dp),
                color = barColor, trackColor = cs.surfaceContainerHighest)
        }
    }
}
