package com.winhub.hernandez.ui.screens.adrenodownload

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.winhub.hernandez.ui.components.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.contents.AdrenotoolsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdrenoDriverDownloadSheet(
    onDismiss: () -> Unit,
    onDriverInstalled: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cs = MaterialTheme.colorScheme
    // The Manage-sources button lives in the sheet header, so its open-state is hoisted here and handed
    // to the pane (which owns the dialog itself). See AdrenoDriverListPane.
    var showManage by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        contentColor = cs.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Download GPU drivers",
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showManage = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Manage sources", tint = cs.onSurfaceVariant)
                }
            }

            // Chip row + driver list + download/install/manage dialogs are shared with the WIN Components
            // landscape pane (single source of truth). cardStyle = false keeps the flat bottom-sheet rows.
            AdrenoDriverListPane(
                showManage = showManage,
                onManageDismiss = { showManage = false },
                onDriverInstalled = onDriverInstalled,
                cardStyle = false,
            )
        }
    }
}

/**
 * The reusable body of the GPU-driver downloader: the source chip row, the remote-driver list, and the
 * download/install + manage-sources dialogs. Extracted from [AdrenoDriverDownloadSheet] so the WIN
 * Components landscape rail can render the exact same driver UI in its right pane (see ContentDownloadSheet)
 * — one source of truth, one install path ([AdrenotoolsManager.installDriver]). Host inside a [ColumnScope]
 * (the list weight-fills the remaining height).
 *
 * [cardStyle] = false keeps the flat bottom-sheet rows used by the standalone sheet (unchanged). true
 * renders outlined-card rows matching the WIN Components pane, including a best-effort "Installed" badge
 * (matched by normalized driver name against the installed adrenotools drivers).
 *
 * The Manage-sources dialog is owned here but its open-state is hoisted to the caller (the caller places
 * the trigger button in its own header): pass [showManage] and [onManageDismiss].
 */
@Composable
fun ColumnScope.AdrenoDriverListPane(
    showManage: Boolean,
    onManageDismiss: () -> Unit,
    onDriverInstalled: (String) -> Unit,
    cardStyle: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    val repo = remember { RemoteDriverRepository(context) }
    // Recomputed whenever the manage dialog changes a source (add/remove/toggle), via manageRefreshKey.
    var manageRefreshKey by remember { mutableStateOf(0) }
    val sources = remember(manageRefreshKey) { DriverSources.allSources(context) }

    var selectedSourceIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<RemoteDriverEntry>>(emptyList()) }

    var pendingEntry by remember { mutableStateOf<RemoteDriverEntry?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadStage by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    // Installed adrenotools drivers (folder name == meta.json "name"), normalized, for the card-style
    // Installed badge. enumarateInstalledDrivers() is a cheap filesDir listing; re-read after each install.
    var installRefreshKey by remember { mutableStateOf(0) }
    val installedNorm = remember(installRefreshKey) {
        runCatching { AdrenotoolsManager(context).enumarateInstalledDrivers() }
            .getOrDefault(arrayListOf())
            .map { normDriver(it) }
            .toSet()
    }

    // Keep the selected chip in range if a source is disabled/removed out from under it.
    LaunchedEffect(sources.size) {
        if (selectedSourceIndex > sources.lastIndex) selectedSourceIndex = 0
    }

    LaunchedEffect(selectedSourceIndex, refreshKey, manageRefreshKey) {
        val source = sources.getOrNull(selectedSourceIndex)
        if (source == null) {
            entries = emptyList(); errorMessage = null; loading = false
            return@LaunchedEffect
        }
        loading = true
        errorMessage = null
        entries = emptyList()
        repo.fetchEntries(source).fold(
            onSuccess = { entries = it },
            onFailure = { errorMessage = it.message ?: it::class.java.simpleName },
        )
        loading = false
    }

    // Horizontally scrollable source chip row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sources.forEachIndexed { index, source ->
            SourceChip(
                text = source.name,
                selected = index == selectedSourceIndex,
                onClick = { selectedSourceIndex = index },
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Divider(color = cs.outline.copy(alpha = 0.4f))

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
            loading -> CenteredLoading()
            errorMessage != null -> CenteredError(
                message = errorMessage ?: "",
                onRetry = { refreshKey++ },
            )
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No drivers found.",
                    color = cs.onSurfaceVariant,
                )
            }
            // Card style spaces + insets the rows (matching the WIN Components pane); flat style keeps the
            // original edge-to-edge rows with a divider under each.
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = if (cardStyle) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else PaddingValues(0.dp),
                verticalArrangement = if (cardStyle) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(0.dp),
            ) {
                items(entries, key = { it.source + "_" + it.downloadUrl }) { entry ->
                    EntryRow(
                        entry = entry,
                        installed = cardStyle && normDriver(entry.displayName) in installedNorm,
                        cardStyle = cardStyle,
                        onClick = { pendingEntry = entry },
                    )
                    if (!cardStyle) Divider(color = cs.outline.copy(alpha = 0.25f))
                }
            }
        }
    }

    if (showManage) {
        ManageSourcesDialog(
            context = context,
            onDismiss = onManageDismiss,
            onChanged = { manageRefreshKey++ },
        )
    }

    pendingEntry?.let { entry ->
        if (!downloading) {
            OutlinedAlertDialog(
                onDismissRequest = { pendingEntry = null },
                title = { Text("Download driver?") },
                text = {
                    Column {
                        Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Source: ${entry.source}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "The driver will be downloaded and installed automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        downloading = true
                        downloadProgress = 0
                        downloadStage = "Downloading"
                        scope.launch {
                            repo.downloadEntry(entry) { pct -> downloadProgress = pct }
                                .fold(
                                    onSuccess = { file ->
                                        downloadStage = "Installing"
                                        val activity = context as? Activity ?: context
                                        val manager = AdrenotoolsManager(activity)
                                        val driverId = manager.installDriver(Uri.fromFile(file))
                                        file.delete()
                                        downloading = false
                                        pendingEntry = null
                                        if (driverId.isNotEmpty()) {
                                            installRefreshKey++ // re-enumerate so the Installed badge picks it up
                                            Toast.makeText(context, "Installed: $driverId", Toast.LENGTH_SHORT).show()
                                            onDriverInstalled(driverId)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Install failed — invalid driver package",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    },
                                    onFailure = { t ->
                                        downloading = false
                                        pendingEntry = null
                                        Toast.makeText(
                                            context,
                                            "Download failed: ${t.message ?: "unknown error"}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    },
                                )
                        }
                    }) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingEntry = null }) { Text("Cancel") }
                },
            )
        } else {
            OutlinedAlertDialog(
                onDismissRequest = { /* block dismiss while busy */ },
                title = { Text(downloadStage) },
                text = {
                    Column {
                        Text(
                            entry.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (downloadStage == "Downloading") {
                            LinearProgressIndicator(
                                progress = downloadProgress / 100f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "$downloadProgress%",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {},
            )
        }
    }
}

@Composable
private fun SourceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primary else cs.surfaceVariant
    val fg = if (selected) cs.onPrimary else cs.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EntryRow(
    entry: RemoteDriverEntry,
    installed: Boolean,
    cardStyle: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    if (!cardStyle) {
        // Flat bottom-sheet row (standalone sheet) — unchanged.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Memory,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = "Download",
                tint = cs.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        return
    }

    // Card row matching the WIN Components DownloadContentItem idiom (surfaceContainer fill, 1dp outline,
    // rounded 10dp). An already-installed driver shows a checkmark + "Installed" and isn't tappable.
    val installedBlue = Color(0xFF4FC3F7) // matches DownloadContentItem's installed/in-use blue
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (installed) Modifier else Modifier.clickable(onClick = onClick)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(1.dp, cs.outline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (installed) {
                    Text("Installed", style = MaterialTheme.typography.labelSmall, color = installedBlue)
                }
            }
            if (installed) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Installed", tint = installedBlue,
                    modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Download", tint = cs.primary,
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Normalized driver identity (lowercase, alphanumerics only) for best-effort installed-matching. */
private fun normDriver(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

@Composable
private fun CenteredLoading() {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(
                "Loading drivers…",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CenteredError(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = cs.error,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Could not load drivers",
                color = cs.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

// Manage driver sources (issue #160): toggle built-ins, delete custom sources, and add a new one
// (a JSON url, or a GitHub owner/repo). Mutations persist via [DriverSourceStore] and bump [onChanged]
// so the sheet's chip row recomputes. Styling matches the sheet (M3 + the local SourceChip toggle).
@Composable
private fun ManageSourcesDialog(
    context: Context,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val store = remember { DriverSourceStore(context) }
    // Re-read from the store after each mutation by bumping this.
    var version by remember { mutableStateOf(0) }
    val disabled = remember(version) { store.disabledBuiltIns() }
    val custom = remember(version) { store.customSources() }

    var newName by remember { mutableStateOf("") }
    var isGithub by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }
    var newOwner by remember { mutableStateOf("") }
    var newRepo by remember { mutableStateOf("") }

    fun mutate(block: () -> Unit) { block(); version++; onChanged() }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage driver sources") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Built-in sources", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                DriverSources.BUILT_IN.forEach { src ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(src.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                        Switch(
                            checked = src.name !in disabled,
                            onCheckedChange = { on -> mutate { store.setBuiltInEnabled(src.name, on) } },
                        )
                    }
                }

                if (custom.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Divider(color = cs.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text("Custom sources", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    custom.forEach { src ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                                Text(feedSummary(src), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            IconButton(onClick = { mutate { store.removeCustom(src.name) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = cs.error)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = cs.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                Text("Add source", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceChip("JSON URL", selected = !isGithub, onClick = { isGithub = false })
                    SourceChip("GitHub repo", selected = isGithub, onClick = { isGithub = true })
                }
                Spacer(Modifier.height(8.dp))
                if (!isGithub) {
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("JSON URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = newOwner,
                        onValueChange = { newOwner = it },
                        label = { Text("Owner (or github.com/owner/repo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newRepo,
                        onValueChange = { newRepo = it },
                        label = { Text("Repo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val name = newName.trim()
                    when {
                        name.isEmpty() ->
                            Toast.makeText(context, "Enter a name", Toast.LENGTH_SHORT).show()
                        !isGithub && newUrl.isBlank() ->
                            Toast.makeText(context, "Enter a JSON URL", Toast.LENGTH_SHORT).show()
                        !isGithub -> {
                            mutate { store.addCustom(name, DriverFeed.Json(newUrl.trim())) }
                            newName = ""; newUrl = ""; newOwner = ""; newRepo = ""
                        }
                        else -> {
                            val gh = DriverSourceStore.parseGithubRepo(newOwner, newRepo)
                            if (gh == null) {
                                Toast.makeText(context, "Enter owner and repo", Toast.LENGTH_SHORT).show()
                            } else {
                                mutate { store.addCustom(name, DriverFeed.GithubReleases(gh.first, gh.second)) }
                                newName = ""; newUrl = ""; newOwner = ""; newRepo = ""
                            }
                        }
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add source")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

private fun feedSummary(src: RemoteDriverSource): String =
    when (val f = src.feeds.firstOrNull()) {
        is DriverFeed.Json -> f.url
        is DriverFeed.GithubReleases -> "github.com/${f.owner}/${f.repo}"
        null -> ""
    }
