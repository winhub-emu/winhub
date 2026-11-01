package com.winhub.hernandez.ui.screens.contents

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InstallDesktop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winhub.hernandez.R
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.store.download.ContentDownloadPhase
import com.winhub.hernandez.store.download.ContentDownloadRegistry
import com.winhub.hernandez.store.download.ContentDownloadState
import com.winhub.hernandez.store.download.InstallProgressDialog
import com.winhub.hernandez.ui.screens.adrenodownload.DriverFeed
import com.winhub.hernandez.ui.screens.adrenodownload.DriverSourceStore
import com.winhub.hernandez.ui.screens.DeckDragFrame
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.OfficialSourceColor
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import com.winhub.hernandez.ui.screens.SourceTagBadge
import com.winhub.hernandez.ui.screens.deckDragSelf
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val InstalledGreen = Color(0xFF37C26B)
private val SavedBlue = Color(0xFF3D9BFF)

private enum class HubTab(val label: String, val railLabel: String) {
    DOWNLOAD("Download Components", "Download"),
    COMPONENT("Components", "Components"),
    INSTALLED("Installed", "Installed"),
    MY_FILES("My Files", "My Files"),
}

private fun tabIcon(t: HubTab): ImageVector = when (t) {
    HubTab.DOWNLOAD -> Icons.Filled.Download
    HubTab.COMPONENT -> Icons.Filled.Extension
    HubTab.MY_FILES -> Icons.Filled.Folder
    HubTab.INSTALLED -> Icons.Filled.CheckCircle
}

@Composable
fun ContentsHubScreen(vm: ContentsHubViewModel = viewModel()) {
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableStateOf(HubTab.DOWNLOAD) }

    // Same wide/narrow signal the Download tab uses for master–detail governs the tab chrome:
    // narrow → top TabRow; wide → a left vertical NavigationRail, content filling the rest
    // (so landscape reads rail · repo-list · detail).
    // One animated focus ring for every button below (PrimaryButton / save), as in Deck mode.
    DeckDragFrame(modifier = Modifier.fillMaxSize(), color = cs.primary) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(cs.background)) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(containerColor = cs.background) {
                    // LINUX is hidden from the chrome (kept in the enum so the `when` stays exhaustive).
                    HubTab.values().forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(tabIcon(t), contentDescription = null) },
                            label = { Text(t.railLabel, fontWeight = FontWeight.Bold, maxLines = 1) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = cs.primary,
                                selectedTextColor = cs.primary,
                                indicatorColor = cs.primary.copy(alpha = 0.16f),
                                unselectedIconColor = cs.onSurfaceVariant,
                                unselectedTextColor = cs.onSurfaceVariant,
                            ),
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { HubTabContent(vm, tab, wide = true) }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = tab.ordinal,
                    containerColor = cs.background,
                    contentColor = cs.primary,
                ) {
                    HubTab.values().forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t },
                            text = { Text(t.label, fontWeight = FontWeight.Bold) },
                            selectedContentColor = cs.primary,
                            unselectedContentColor = cs.onSurfaceVariant,
                        )
                    }
                }
                // Weighted so the tab content gets the REMAINING height after the TabRow and its own
                // internal lists scroll within it (never clipped, incl. large interface scale).
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { HubTabContent(vm, tab, wide = false) }
            }
        }
    }
    }

    // ── Shared install-progress popup ──────────────────────────────────────────────
    // The SAME composable + SAME cancel path the container-create sheet uses. It picks up any active
    // Contents install straight off the process-lifetime registry (keys are "contents::…" — see
    // ContentsInstaller.keyFor), so a download OR a from-file install both surface a live popup —
    // crucially the file-install path, which otherwise had no on-screen feedback at all.
    //
    // The popup holds its OWN snapshot rather than reading the registry live: while the registry entry
    // exists we keep [shown] synced to it (live progress + the terminal flip); once the launcher's
    // finally removes the entry (~2s after done) we KEEP the last snapshot so the finished popup — real
    // name, type chip, description, "Version … • build N" — stays up until the user taps Done/Close.
    val registry by ContentDownloadRegistry.states.collectAsState()
    var shownKey by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<ContentDownloadState?>(null) }
    LaunchedEffect(registry) {
        val k = shownKey
        if (k == null) {
            // Attach to the next active Contents install once nothing is being shown.
            registry.entries.firstOrNull { it.key.startsWith("contents::") && !it.value.terminal }?.let {
                shownKey = it.key
                shown = it.value
            }
        } else {
            // Sync while the entry lives; after removal keep the last snapshot (don't null it out).
            registry[k]?.let { shown = it }
        }
    }
    shown?.let { st ->
        InstallProgressDialog(
            state = st,
            onCancel = { ContentDownloadRegistry.requestCancel(st.key) },
            onDismiss = {
                // Explicit close: drop the registry entry (frees the host to attach to the next
                // install) and clear the held snapshot.
                ContentDownloadRegistry.remove(st.key)
                shown = null
                shownKey = null
            },
        )
    }
}

@Composable
private fun HubTabContent(vm: ContentsHubViewModel, tab: HubTab, wide: Boolean) {
    when (tab) {
        // A single wide/narrow decision, taken once from the full screen width, drives BOTH the tab
        // chrome (rail vs top tabs) and the Download master–detail — so it's never rail + single-pane.
        HubTab.DOWNLOAD -> DownloadTab(vm, wide)
        HubTab.COMPONENT -> ComponentTab(vm)
        HubTab.MY_FILES -> MyFilesTab(vm)
        HubTab.INSTALLED -> InstalledTab(vm)
    }
}

// ── Component tab (category cards → that family's items across every source) ──────────
private data class HubCategory(val type: String, val title: String, val blurb: String, val icon: ImageVector)

// Fixed component families the catalog ships (mirrors ContentsTypes.ALL). Descriptors are accurate
// one-liners, so the tab doubles as a quick glossary for newcomers.
private val hubCategories = listOf(
    HubCategory("DXVK", "DXVK", "Direct3D 9 / 10 / 11 \u2192 Vulkan", Icons.Filled.ViewInAr),
    HubCategory("D7VK", "D7VK", "Direct3D 7 \u2192 Vulkan", Icons.Filled.ViewInAr),
    HubCategory("VKD3D", "VKD3D", "Direct3D 12 \u2192 Vulkan (VKD3D-Proton)", Icons.Filled.ViewInAr),
    HubCategory(ContentsTypes.GPU_DRIVERS, "GPU Drivers", "Turnip / Adreno Vulkan drivers", Icons.Filled.Memory),
    HubCategory("Box64", "Box64", "x86_64 userland emulation on ARM64", Icons.Filled.Extension),
    HubCategory("WOWBox64", "WOWBox64", "x86 (32-bit) emulation on ARM64", Icons.Filled.Extension),
    HubCategory("FEXCore", "FEXCore", "x86 / x86_64 backend (ARM64EC)", Icons.Filled.Extension),
    HubCategory("Wine", "Wine", "Windows compatibility layer (runtime)", Icons.Filled.Dns),
    HubCategory("Proton", "Proton", "Valve's Wine fork tuned for gaming", Icons.Filled.Dns),
)

@Composable
private fun ComponentTab(vm: ContentsHubViewModel) {
    val cs = MaterialTheme.colorScheme
    val categoryType by vm.categoryType.collectAsState()
    val items by vm.categoryItems.collectAsState()
    val loading by vm.categoryLoading.collectAsState()

    if (categoryType == null) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Components", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "Browse every component family across all repositories",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(hubCategories, key = { i, c -> "$i-${c.type}" }) { _, cat ->
                    CategoryCard(cat) { vm.selectCategory(cat.type) }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    } else {
        val title = hubCategories.firstOrNull { it.type == categoryType }?.title ?: categoryType ?: "Components"
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { vm.selectCategory(null) }) {
                    Icon(Icons.Filled.ArrowBack, "Back to categories", tint = cs.onSurface)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = cs.primary)
                }
            } else {
                Text(
                    "${items.size} release${if (items.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (items.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No releases found for $title.", color = cs.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        itemsIndexed(items, key = { i, it -> "$i-${it.sourceName}-${it.downloadUrl}" }) { _, item ->
                            ComponentRow(vm, item, showSource = true)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: HubCategory, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cs.outline, RoundedCornerShape(16.dp))
            .background(cs.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(cat.icon, null, tint = cs.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(cat.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(cat.blurb, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

// ── Download tab (master–detail) ───────────────────────────────────────────────
@Composable
private fun DownloadTab(vm: ContentsHubViewModel, wide: Boolean) {
    val sources by vm.sources.collectAsState()
    val selected by vm.selected.collectAsState()
    val query by vm.query.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<HubSource?>(null) }

    // Device/gesture back returns to the source list (both narrow single-pane and wide) instead of
    // leaving the Contents screen; only intercepts while a repo detail is open.
    BackHandler(enabled = selected != null) { vm.selectSource(null) }

    // [wide] is decided once from the FULL screen width (passed down), so a wide screen always gets
    // rail + master–detail and never rail + single-pane.
    if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                // Left column always shows the search field + repo list (results go right).
                SourceListPane(vm, sources, query, selected, showInlineResults = false,
                    onAdd = { showAdd = true }, onImport = { showImport = true },
                    onSettings = { showSettings = true }, onMenu = { menuFor = it })
            }
            VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outline)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // Right pane priority: active search → results (full width); else selected repo; else empty.
                when {
                    query.isNotBlank() -> SearchResultsPane(vm, Modifier.fillMaxSize().padding(top = 12.dp, start = 14.dp, end = 14.dp))
                    selected != null -> RepoDetail(vm, showBack = false)
                    else -> EmptyDetailPlaceholder()
                }
            }
        }
    } else {
        if (selected == null) {
            SourceListPane(vm, sources, query, selected, showInlineResults = true,
                onAdd = { showAdd = true }, onImport = { showImport = true },
                onSettings = { showSettings = true }, onMenu = { menuFor = it })
        } else {
            RepoDetail(vm, showBack = true)
        }
    }

    if (showAdd) AddRepoDialog(vm, onDismiss = { showAdd = false })
    if (showImport) ImportExportDialog(vm, onDismiss = { showImport = false })
    if (showSettings) SettingsDialog(vm,
        onDismiss = { showSettings = false },
        onLocation = { showSettings = false; showLocation = true })
    if (showLocation) LocationDialog(vm, onDismiss = { showLocation = false })
    menuFor?.let { RepoMenuDialog(vm, it, onDismiss = { menuFor = null }) }
}

@Composable
private fun SourceListPane(
    vm: ContentsHubViewModel,
    sources: List<HubSource>,
    query: String,
    selected: HubSource?,
    // Narrow: results replace the list inline when a query is active. Wide: false — the left column
    // always shows the search field + repo list, and results render in the right detail pane instead.
    showInlineResults: Boolean,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onMenu: (HubSource) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search all repositories…") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = cs.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))

        if (query.isBlank() || !showInlineResults) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Select Online Repository", style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.reloadSources(); vm.refreshSelected() }) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = cs.primary)
                }
                IconButton(onClick = onImport) { Icon(Icons.Filled.FileDownload, "Import list", tint = cs.primary) }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = cs.primary) }
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add repository", tint = cs.primary) }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(sources, key = { i, s -> "$i-${s.name}-${s.driverOnly}" }) { _, source ->
                    RepoCard(source, isSelected = selected == source,
                        onClick = { vm.selectSource(source) }, onMenu = { onMenu(source) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else {
            SearchResultsPane(vm, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** The cross-source search results block: "N results" header + full-width ComponentRow list. Rendered
 *  inline in the single column when narrow, and in the right detail pane when wide. */
@Composable
private fun SearchResultsPane(vm: ContentsHubViewModel, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val query by vm.query.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()

    Column(modifier = modifier) {
        if (searching) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = cs.primary) }
        } else {
            Text("${searchResults.size} result${if (searchResults.size != 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            Spacer(Modifier.height(8.dp))
            if (searchResults.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No components match “$query”.", color = cs.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(searchResults, key = { i, it -> "$i-${it.sourceName}-${it.type}-${it.downloadUrl}" }) { _, item ->
                        ComponentRow(vm, item, showSource = true)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCard(
    source: HubSource,
    isSelected: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val driverOnly = source.driverOnly
    val pills = source.typePills
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isSelected) cs.primary else cs.outline, RoundedCornerShape(16.dp))
            .background(if (isSelected) cs.primary.copy(alpha = 0.12f) else cs.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(if (driverOnly) Icons.Filled.ViewInAr else Icons.Filled.Folder, null,
            tint = cs.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Name takes the slack and ellipsises; the Official tag is fixed-width so it never wraps
            // the row, narrow or wide.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false))
                if (source.isOfficial) {
                    Spacer(Modifier.width(8.dp))
                    SourceTagBadge("Official", OfficialSourceColor)
                }
            }
            Text(source.displayFormat, style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pills.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    pills.take(4).forEach { TypePill(it) }
                    if (pills.size > 4) TypePill("+${pills.size - 4}")
                }
            }
        }
        IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, "Options", tint = cs.onSurfaceVariant) }
        Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun TypePill(label: String) {
    val cs = MaterialTheme.colorScheme
    val driver = ContentsTypes.isDriver(label)
    Text(
        text = label.uppercase(),
        fontSize = 10.sp, fontWeight = FontWeight.Bold,
        color = if (driver) cs.primary else cs.onSurfaceVariant,
        modifier = Modifier
            .background(if (driver) cs.primary.copy(alpha = 0.14f) else cs.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyDetailPlaceholder() {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Apps, null, tint = cs.outline, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Pick a repository", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("Choose a source from the list to browse and download its components.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun RepoDetail(vm: ContentsHubViewModel, showBack: Boolean) {
    val cs = MaterialTheme.colorScheme
    val source by vm.selected.collectAsState()
    val types by vm.detailTypes.collectAsState()
    val items by vm.detailItems.collectAsState()
    val loading by vm.detailLoading.collectAsState()
    val src = source ?: return
    var filter by remember(src) { mutableStateOf("All") }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (showBack) {
                IconButton(onClick = { vm.selectSource(null) }) { Icon(Icons.Filled.ArrowBack, "Back", tint = cs.onSurface) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(src.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false))
                    if (src.isOfficial) {
                        Spacer(Modifier.width(8.dp))
                        SourceTagBadge("Official", OfficialSourceColor)
                    }
                }
                Text("${src.displayFormat} · ${items.size} ${if (src.driverOnly) "drivers" else "components"}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = { vm.refreshSelected() }) { Icon(Icons.Filled.Refresh, "Refresh", tint = cs.primary) }
        }

        // Keep-raw is controlled once from Contents settings (single source of truth); no per-repo bar.
        Spacer(Modifier.height(12.dp))
        // Filter chips
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("All", filter == "All") { filter = "All" }
            types.forEach { t -> FilterPill(t, filter == t) { filter = t } }
        }

        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = cs.primary) }
            items.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No components available.", color = cs.onSurfaceVariant)
            }
            else -> {
                val shown = if (filter == "All") items else items.filter { it.type == filter }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(shown, key = { i, it -> "$i-${it.type}-${it.downloadUrl}" }) { _, item ->
                        ComponentRow(vm, item, showSource = false)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (active) Color.Black else cs.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .border(1.dp, if (active) cs.primary else cs.outline, RoundedCornerShape(20.dp))
            .background(if (active) cs.primary else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
    )
}

// ── Component / driver row ─────────────────────────────────────────────────────
@Composable
private fun ComponentRow(vm: ContentsHubViewModel, item: ContentsHubViewModel.CatalogItem, showSource: Boolean) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val installedKeys by vm.installedKeys.collectAsState()
    val savedKeys by vm.savedKeys.collectAsState()
    val registry by ContentDownloadRegistry.states.collectAsState()

    val baseDisplay by vm.baseDisplay.collectAsState()

    // recompute cheap booleans against the observed sets
    val installed = installedKeys.let { vm.isInstalled(item) }
    val saved = savedKeys.let { vm.isSaved(item) }
    val state: ContentDownloadState? = registry[ContentsInstaller.keyFor(item.type, item.sourceName, item.versionName)]
    val busy = state != null && !state.terminal

    Column(modifier = Modifier.fillMaxWidth()
        .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
        .background(cs.surface, RoundedCornerShape(14.dp))
        .padding(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(38.dp)
                .background(if (item.isDriver) cs.primary.copy(alpha = 0.14f) else cs.onSurface.copy(alpha = 0.05f),
                    RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                Icon(typeIcon(item.type), null,
                    tint = if (item.isDriver) cs.primary else cs.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (showSource) Text(item.sourceName, style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold)
                // PACK_JSON / release-tag sources set name == version (both the tag), so a naive
                // "name version" doubles the title. The filename row below already shows the version.
                val title = remember(item.displayName, item.versionName) {
                    val n = item.displayName.trim(); val v = item.versionName.trim()
                    if (v.isEmpty() || n == v || n.contains(v, ignoreCase = true)) n else "$n $v"
                }
                Text(title, style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaBit(Icons.Filled.Inventory2, item.fileName)
                    item.sizeBytes?.let { MetaBit(Icons.Filled.SdStorage, RemoteSourceRepository.formatFileSize(it)) }
                    item.publishedAt?.let { MetaBit(Icons.Filled.Event, it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(item.type, cs.onSurface.copy(alpha = 0.08f), cs.onSurfaceVariant, typeIcon(item.type))
                    if (installed) StatusBadge("Installed", InstalledGreen.copy(alpha = 0.16f), InstalledGreen, Icons.Filled.CheckCircle)
                    if (saved) StatusBadge("Saved", SavedBlue.copy(alpha = 0.16f), SavedBlue, Icons.Filled.Save)
                }
            }
        }

        if (state != null) {
            Spacer(Modifier.height(10.dp))
            // A save-only run shares the key with an install; only the wording differs.
            val label = when (state.phase) {
                ContentDownloadPhase.DOWNLOADING -> "Downloading ${(state.fraction * 100).toInt()}%"
                ContentDownloadPhase.INSTALLING -> if (state.saveOnly) "Saving to My Files" else "Installing"
                ContentDownloadPhase.DONE -> if (state.saveOnly) "Saved" else "Installed"
                ContentDownloadPhase.ERROR -> state.error ?: "Failed"
            }
            if (!state.terminal) {
                LinearProgressIndicator(progress = { state.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(), color = cs.primary)
                Spacer(Modifier.height(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = if (state.phase == ContentDownloadPhase.ERROR) cs.error else cs.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))
        // Install button takes the slack; the save-only action is a fixed-width square matched to the
        // button's height (IntrinsicSize.Min), so the pair never wraps in portrait or landscape.
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (installed) {
                PrimaryButton("Installed", Icons.Filled.CheckCircle, enabled = false,
                    container = InstalledGreen.copy(alpha = 0.16f), content = InstalledGreen, modifier = Modifier.weight(1f)) {}
            } else {
                PrimaryButton(if (busy) "Working…" else "Download & Install", Icons.Filled.Download,
                    enabled = !busy, container = cs.primary, content = Color.Black, modifier = Modifier.weight(1f)) {
                    ContentsInstaller.install(
                        context.applicationContext, item.type, item.sourceName, item.versionName,
                        item.downloadUrl, vm.keepRaw.value,
                    ) { vm.refreshStatus(); vm.refreshFolders() }
                }
            }
            // "Save archive only": raw archive → My Files (Contents save location), nothing installed.
            // Already saved → dimmed, tap explains, long-press re-downloads. Available on every row
            // (installed ones too — the archive is still worth keeping) across all repos and drivers.
            SaveArchiveAction(
                saved = saved, enabled = !busy,
                onClick = {
                    if (saved) {
                        Toast.makeText(context, "Already saved to $baseDisplay${item.type}/. Long-press to re-download.", Toast.LENGTH_SHORT).show()
                    } else vm.saveArchive(item)
                },
                onLongClick = { vm.saveArchive(item, force = true) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaveArchiveAction(saved: Boolean, enabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .width(46.dp)
            .deckDragSelf()
            .border(1.dp, if (saved) SavedBlue.copy(alpha = 0.5f) else cs.outline, RoundedCornerShape(11.dp))
            .background(if (saved) SavedBlue.copy(alpha = 0.10f) else cs.onSurface.copy(alpha = 0.04f), RoundedCornerShape(11.dp))
            .then(if (enabled) Modifier.combinedClickable(
                onClickLabel = "Save archive only",
                onLongClickLabel = "Re-download archive",
                onClick = onClick, onLongClick = onLongClick,
            ) else Modifier)
            .alpha(if (!enabled) 0.4f else if (saved) 0.6f else 1f),
    ) {
        Icon(Icons.Filled.Save, "Save archive only", tint = if (saved) SavedBlue else cs.onSurface, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MetaBit(icon: ImageVector, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusBadge(text: String, bg: Color, fg: Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(bg, RoundedCornerShape(20.dp)).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrimaryButton(
    text: String, icon: ImageVector, enabled: Boolean, container: Color, content: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    // Respect the caller's container alpha (faint tints must stay translucent); dim the whole
    // component when disabled rather than forcing the fill opaque.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .deckDragSelf()
            .background(container, RoundedCornerShape(11.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 10.dp)) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = content, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

// ── My Files tab ───────────────────────────────────────────────────────────────
@Composable
private fun MyFilesTab(vm: ContentsHubViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val folders by vm.savedFolders.collectAsState()
    val expanded = remember { mutableStateOf(setOf<String>()) }

    val installPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedUri(result.data)?.let { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                val type = if (name.endsWith(".wcp", true) || name.endsWith(".tzst", true)) "" else ContentsTypes.GPU_DRIVERS
                ContentsInstaller.installFromFile(context.applicationContext, type, name, uri) { vm.refreshStatus() }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        // Info bar
        Row(modifier = Modifier.fillMaxWidth()
            .background(cs.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp)) {
            Icon(Icons.Filled.Inventory2, null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Text("Raw .wcp / .zip / .tzst archives you chose to keep, filed by component type. Re-install offline, share, or delete.",
                style = MaterialTheme.typography.bodySmall, color = cs.primary)
        }
        Spacer(Modifier.height(12.dp))
        // Save location lives once in Contents settings (the cog) — no duplicate bar here.
        PrimaryButton("Install content from file…", Icons.Filled.FolderOpen, enabled = true,
            container = cs.onSurface.copy(alpha = 0.06f), content = cs.onSurface, modifier = Modifier.fillMaxWidth()) {
            installPicker.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.WCP, "Select content pack"))
        }
        Spacer(Modifier.height(14.dp))

        if (folders.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No saved archives yet.\nUse the disk icon on a component to save its archive here, or enable “Keep raw archive” before installing.",
                    color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(folders.keys.toList(), key = { i, t -> "$i-$t" }) { _, type ->
                    FolderCard(vm, type, folders[type].orEmpty(),
                        open = type in expanded.value,
                        onToggle = {
                            expanded.value = if (type in expanded.value) expanded.value - type else expanded.value + type
                        })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    vm: ContentsHubViewModel, type: String, files: List<ComponentLibrary.SavedFile>,
    open: Boolean, onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()
        .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
        .background(cs.surface, RoundedCornerShape(14.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(15.dp)) {
            Icon(if (ContentsTypes.isDriver(type)) Icons.Filled.ViewInAr else Icons.Filled.Folder, null,
                tint = cs.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(13.dp))
            Text(type, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${files.size}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.background(cs.onSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ExpandMore, null, tint = cs.onSurfaceVariant)
        }
        if (open) {
            Divider(color = cs.outline)
            files.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Icon(if (f.name.endsWith(".zip", true)) Icons.Filled.FolderZip else Icons.Filled.Inventory2,
                        null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.bodySmall, color = cs.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        // Only the library can tell a save-only archive from one kept on install.
                        Text(
                            if (f.savedOnly) "${RemoteSourceRepository.formatFileSize(f.sizeBytes)} · saved only, not installed"
                            else RemoteSourceRepository.formatFileSize(f.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (f.savedOnly) SavedBlue else cs.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = {
                        // Reinstall = the normal offline install; on success the file stops being "saved only".
                        ContentsInstaller.installFromFile(context.applicationContext, f.type, f.name, f.uri) { ok ->
                            if (ok) vm.markInstalledFromSaved(f) else vm.refreshStatus()
                        }
                    }) { Icon(Icons.Filled.InstallDesktop, "Install", tint = cs.primary) }
                    IconButton(onClick = { shareArchive(context, f) }) { Icon(Icons.Filled.Share, "Share", tint = cs.onSurfaceVariant) }
                    IconButton(onClick = { vm.deleteSaved(f) }) { Icon(Icons.Filled.DeleteOutline, "Delete", tint = cs.onSurfaceVariant) }
                }
            }
        }
    }
}

private fun shareArchive(context: Context, f: ComponentLibrary.SavedFile) {
    runCatching {
        val shareUri = if (f.uri.scheme == "content") f.uri else {
            val file = java.io.File(f.uri.path!!)
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.tileprovider", file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${f.name}"))
    }
}

// ── Installed tab (parity with AdrenoToolsScreen) ────────────────────────────────
@Composable
private fun InstalledTab(vm: ContentsHubViewModel) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val manager = remember { AdrenotoolsManager(context) }
    var refreshKey by remember { mutableStateOf(0) }

    // Metadata-only listing (name/version) — never probes a driver (a6xx-safe).
    val drivers = remember(refreshKey) { manager.enumarateInstalledDrivers().toList() }
    val scope = rememberCoroutineScope()
    val components = remember(refreshKey) {
        val cm = ContentsManager(context)
        cm.syncContents()
        ContentProfile.ContentType.values().mapNotNull { type ->
            val installed = (cm.getProfiles(type) ?: emptyList()).filter { it.remoteUrl == null }
            if (installed.isEmpty()) null else type to installed
        }
    }

    var confirmRemoveDriver by remember { mutableStateOf<String?>(null) }
    var confirmRemoveProfile by remember { mutableStateOf<ContentProfile?>(null) }
    val expanded = remember { mutableStateOf(setOf<String>()) }

    // Install-from-file — mirrors the My Files tab: the in-app file manager (no system SAF), route by
    // extension (.wcp/.tzst → component pipeline, anything else e.g. a driver .zip → GPU driver).
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedUri(result.data)?.let { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                val type = if (name.endsWith(".wcp", true) || name.endsWith(".tzst", true)) "" else ContentsTypes.GPU_DRIVERS
                ContentsInstaller.installFromFile(context.applicationContext, type, name, uri) { refreshKey++; vm.refreshStatus() }
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        InstalledSectionHeader("GPU Drivers", Icons.Filled.ViewInAr)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Install content from file…", Icons.Filled.FolderOpen, enabled = true,
            container = cs.onSurface.copy(alpha = 0.06f), content = cs.onSurface, modifier = Modifier.fillMaxWidth()) {
            filePicker.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.WCP, "Select content pack"))
        }
        Spacer(Modifier.height(12.dp))
        if (drivers.isEmpty()) {
            InstalledEmpty("No GPU drivers installed.")
        } else {
            drivers.forEach { id ->
                InstalledRow(icon = Icons.Filled.ViewInAr, driver = true,
                    title = manager.getDriverName(id), subtitle = manager.getDriverVersion(id),
                    onRemove = { confirmRemoveDriver = id })
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        InstalledSectionHeader("Components", Icons.Filled.Extension)
        Spacer(Modifier.height(10.dp))
        if (components.isEmpty()) {
            InstalledEmpty("No components installed.")
        } else {
            components.forEach { (type, profiles) ->
                val label = type.toString()
                InstalledComponentFolder(
                    type = label, profiles = profiles,
                    open = label in expanded.value,
                    onToggle = { expanded.value = if (label in expanded.value) expanded.value - label else expanded.value + label },
                    onRemove = { confirmRemoveProfile = it })
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    // Confirm: remove driver (same manager.removeDriver path as AdrenoToolsScreen).
    confirmRemoveDriver?.let { id ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmRemoveDriver = null },
            containerColor = cs.surfaceContainerHigh,
            title = { Text("Remove driver?", color = cs.onSurface) },
            text = { Text("Remove \"${manager.getDriverName(id)}\"?", color = cs.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    manager.removeDriver(id); confirmRemoveDriver = null; refreshKey++; vm.refreshStatus()
                }) { Text("Remove", color = cs.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveDriver = null }) { Text("Cancel", color = cs.primary) } },
        )
    }


    // Confirm: remove component.
    confirmRemoveProfile?.let { profile ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmRemoveProfile = null },
            containerColor = cs.surfaceContainerHigh,
            title = { Text("Remove component?", color = cs.onSurface) },
            text = { Text("Remove \"${profile.verName}\"?", color = cs.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    val cm = ContentsManager(context); cm.removeContent(profile); cm.syncContents()
                    confirmRemoveProfile = null; refreshKey++; vm.refreshStatus()
                }) { Text("Remove", color = cs.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveProfile = null }) { Text("Cancel", color = cs.primary) } },
        )
    }
}

@Composable
private fun InstalledSectionHeader(title: String, icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = cs.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InstalledEmpty(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun InstalledRow(icon: ImageVector, driver: Boolean, title: String, subtitle: String, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
            .background(cs.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
        Box(modifier = Modifier.size(38.dp)
            .background(if (driver) cs.primary.copy(alpha = 0.14f) else cs.onSurface.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (driver) cs.primary else cs.onSurface, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.DeleteOutline, "Remove", tint = cs.onSurfaceVariant) }
    }
}

@Composable
private fun InstalledComponentFolder(
    type: String, profiles: List<ContentProfile>, open: Boolean, onToggle: () -> Unit,
    onRemove: (ContentProfile) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()
        .border(1.dp, cs.outline, RoundedCornerShape(14.dp))
        .background(cs.surface, RoundedCornerShape(14.dp))) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(15.dp)) {
            Icon(typeIcon(type), null, tint = cs.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(13.dp))
            Text(type, style = MaterialTheme.typography.titleSmall, color = cs.onSurface,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${profiles.size}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.background(cs.onSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 2.dp))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ExpandMore, null, tint = cs.onSurfaceVariant)
        }
        if (open) {
            Divider(color = cs.outline)
            profiles.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.verName, style = MaterialTheme.typography.bodySmall, color = cs.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("Code ${p.verCode}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(p) }) { Icon(Icons.Filled.DeleteOutline, "Remove", tint = cs.onSurfaceVariant) }
                }
            }
        }
    }
}

// ── Type icon ──────────────────────────────────────────────────────────────────
private fun typeIcon(type: String): ImageVector = when {
    ContentsTypes.isDriver(type) -> Icons.Filled.ViewInAr
    type.equals("DXVK", true) -> Icons.Filled.GridView
    type.equals("VKD3D", true) -> Icons.Filled.Widgets
    type.equals("Box64", true) -> Icons.Filled.Memory
    type.equals("WOWBox64", true) -> Icons.Filled.DeveloperBoard
    type.equals("FEXCore", true) -> Icons.Filled.Dns
    type.equals("Wine", true) -> Icons.Filled.LocalBar
    type.equals("Proton", true) -> Icons.Filled.Science
    else -> Icons.Filled.Extension
}

// ── Dialogs ────────────────────────────────────────────────────────────────────
@Composable
private fun AddRepoDialog(vm: ContentsHubViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isDriver by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(RemoteSourceRepository.SourceFormat.WCP_JSON) }
    var formatMenu by remember { mutableStateOf(false) }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Add repository", color = cs.onSurface) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Paste any component or GPU-driver source — it joins the list like a built-in.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text(if (isDriver) "GitHub repo (owner/repo) or JSON URL" else "URL (repo, releases API, or JSON)") },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                // GPU-driver sources persist through the shared adrenotools store (also visible in
                // the AdrenoTools screen); component sources use the Contents source store.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("GPU driver source", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                        Text("Shared with the AdrenoTools screen", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(checked = isDriver, onCheckedChange = { isDriver = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = cs.primary))
                }
                if (!isDriver) {
                    Spacer(Modifier.height(8.dp))
                    Text("Source format", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Box {
                        TextButton(onClick = { formatMenu = true }) { Text(format.name.replace('_', ' '), color = cs.primary) }
                        androidx.compose.material3.DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false },
                            modifier = Modifier.outlinedMenuCard()) {
                            RemoteSourceRepository.SourceFormat.values().forEach { fmt ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(fmt.name.replace('_', ' ')) },
                                    onClick = { format = fmt; formatMenu = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim(); val u = url.trim()
                if (n.isNotBlank() && u.isNotBlank()) {
                    if (isDriver) {
                        val feed = driverFeedFor(u)
                        vm.addDriverSource(n, feed)
                    } else {
                        vm.addComponentSource(RemoteSourceRepository.RemoteSource(
                            name = n, url = u, format = format, supportedTypes = emptyList(), isCustom = true))
                    }
                    onDismiss()
                }
            }) { Text("Add source", color = cs.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = cs.primary) } },
    )
}

/** JSON feeds (.json / raw github) stay JSON; anything that parses as owner/repo becomes a releases feed. */
private fun driverFeedFor(url: String): DriverFeed {
    val looksJson = url.endsWith(".json", true) || url.contains("raw.githubusercontent.com", true)
    if (!looksJson) {
        DriverSourceStore.parseGithubRepo(url, "")?.let { (owner, repo) ->
            return DriverFeed.GithubReleases(owner, repo)
        }
    }
    return DriverFeed.Json(url)
}

@Composable
private fun RepoMenuDialog(
    vm: ContentsHubViewModel, source: HubSource, onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainerHigh,
        title = { Text(source.name, color = cs.onSurface) },
        text = {
            Column {
                MenuRow(Icons.Filled.Apps, if (source.driverOnly) "Browse drivers" else "Browse components") { onDismiss(); vm.selectSource(source) }
                MenuItemDivider()
                MenuRow(Icons.Filled.OpenInNew, "Open source page") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.browseUrl(source)))) }
                    onDismiss()
                }
                MenuItemDivider()
                MenuRow(Icons.Filled.Delete, if (source.removeIsHide) "Hide default repository" else "Remove repository",
                    tint = cs.error) { vm.removeSource(source); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = cs.primary) } },
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ImportExportDialog(vm: ContentsHubViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@let
                    vm.importRepoListJson(json, merge = true)
                }
                onDismiss()
            }
        }
    }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Import / export sources", color = cs.onSurface) },
        text = {
            Column {
                MenuRow(Icons.Filled.FileDownload, "Import list from file…") {
                    importer.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                    })
                }
                MenuItemDivider()
                MenuRow(Icons.Filled.FileUpload, "Export current list") {
                    runCatching {
                        val json = vm.exportRepoListJson()
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"; putExtra(Intent.EXTRA_TEXT, json)
                        }
                        context.startActivity(Intent.createChooser(share, "Export sources"))
                    }
                    onDismiss()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = cs.primary) } },
    )
}

@Composable
private fun SettingsDialog(vm: ContentsHubViewModel, onDismiss: () -> Unit, onLocation: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val keepRaw by vm.keepRaw.collectAsState()
    val showOfficial by vm.showOfficial.collectAsState()
    val baseDisplay by vm.baseDisplay.collectAsState()
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Contents settings", color = cs.onSurface) },
        text = {
            // Scrolls so the taller body still fits a landscape phone (dialog height is capped there).
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep raw archive by default", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                        Text("Save every install's download to My Files", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(checked = keepRaw, onCheckedChange = { vm.setKeepRaw(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = cs.primary))
                }
                MenuItemDivider()
                MenuRow(Icons.Filled.FolderSpecial, "Save location") { onLocation() }
                Text(baseDisplay, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp))
                Text("Save-only archives (the disk icon) are filed by type in this folder too.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, top = 2.dp))
                MenuItemDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Official catalog in repositories", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                        Text("Off hides it here; container and shortcut sheets keep it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(checked = showOfficial, onCheckedChange = { vm.setShowOfficial(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = cs.primary))
                }
                MenuItemDivider()
                MenuRow(Icons.Filled.Restore, "Restore default repositories") { vm.restoreDefaultSources(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = cs.primary) } },
    )
}

@Composable
private fun LocationDialog(vm: ContentsHubViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    // In-app file manager in directory-pick mode (issue #70) — returns an absolute path, stored the
    // same way as the other two options (plain File base). No SAF, no persistable-permission dance:
    // the app holds all-files access, so direct path writes work.
    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            InAppFilePicker.pickedPath(result.data)?.let { path ->
                vm.library.setFileBase(path)
                vm.refreshBase(); vm.refreshFolders(); onDismiss()
            }
        }
    }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surfaceContainerHigh,
        title = { Text("Component save location", color = cs.onSurface) },
        text = {
            Column {
                Text("Raw archives are filed under <folder>/components/<type>/ — next to WinHub's logs and saves.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                MenuRow(Icons.Filled.Folder, "Downloads › bannerlator (default)") {
                    vm.library.setDefaultBase(); vm.refreshBase(); vm.refreshFolders(); onDismiss()
                }
                MenuItemDivider()
                MenuRow(Icons.Filled.FolderSpecial, "App-private storage") {
                    vm.library.setFileBase(vm.library.appPrivateBasePath()); vm.refreshBase(); vm.refreshFolders(); onDismiss()
                }
                MenuItemDivider()
                MenuRow(Icons.Filled.FolderOpen, "Choose another folder…") {
                    dirPicker.launch(
                        InAppFilePicker.buildDirIntent(
                            context,
                            title = "Select save folder",
                            initialDir = vm.library.currentFileBasePath(),
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = cs.primary) } },
    )
}
