package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.store.DeckRise
import com.winhub.hernandez.store.deckTileFocus
import com.winhub.hernandez.store.download.ContentDownloadPhase
import com.winhub.hernandez.store.download.ContentDownloadRegistry
import com.winhub.hernandez.store.download.ContentDownloadState
import com.winhub.hernandez.ui.screens.adrenodownload.DriverFeed
import com.winhub.hernandez.ui.screens.adrenodownload.DriverSourceStore
import com.winhub.hernandez.ui.screens.contents.ComponentLibrary
import com.winhub.hernandez.ui.screens.contents.ContentsHubViewModel
import com.winhub.hernandez.ui.screens.contents.ContentsInstaller
import com.winhub.hernandez.ui.screens.contents.ContentsTypes
import com.winhub.hernandez.ui.screens.contents.HubSource
import com.winhub.hernandez.ui.screens.contents.RemoteSourceRepository
import com.winhub.hernandez.util.InAppFilePicker

private val ContentsGreen = Color(0xFF37C26B)
private val ContentsBlue = Color(0xFF3D9BFF)

private enum class DeckContentsTab(val label: String) {
    BROWSE("Browse"),
    FILES("My Files"),
    INSTALLED("Installed"),
}

private data class DeckContentCategory(
    val type: String,
    val title: String,
    val blurb: String,
    val icon: ImageVector,
)

private val deckContentCategories = listOf(
    DeckContentCategory("DXVK", "DXVK", "Direct3D 9 / 10 / 11 to Vulkan", Icons.Filled.ViewInAr),
    DeckContentCategory("D7VK", "D7VK", "Direct3D 7 to Vulkan", Icons.Filled.ViewInAr),
    DeckContentCategory("VKD3D", "VKD3D", "Direct3D 12 to Vulkan (VKD3D-Proton)", Icons.Filled.Widgets),
    DeckContentCategory(ContentsTypes.GPU_DRIVERS, "GPU Drivers", "Turnip / Adreno Vulkan drivers", Icons.Filled.Memory),
    DeckContentCategory("Box64", "Box64", "x86_64 userland emulation on ARM64", Icons.Filled.Extension),
    DeckContentCategory("WOWBox64", "WOWBox64", "x86 (32-bit) emulation on ARM64", Icons.Filled.DeveloperBoard),
    DeckContentCategory("FEXCore", "FEXCore", "x86 / x86_64 backend (ARM64EC)", Icons.Filled.Dns),
    DeckContentCategory("Wine", "Wine", "Windows compatibility layer (runtime)", Icons.Filled.LocalBar),
    DeckContentCategory("Proton", "Proton", "Valve's Wine fork tuned for gaming", Icons.Filled.Science),
)

/** Deck Mode's Contents section, rebuilt from scratch in DroidDeck's own vocabulary. */
@Composable
internal fun DeckContentsScreen(vm: ContentsHubViewModel = viewModel()) {
    val tabs = LocalDeckTabs.current
    val tab = tabs.index
    LaunchedEffect(tabs) { tabs.bind(DeckContentsTab.values().size) }

    // "Apply Settings layout everywhere" makes Contents follow the Settings page's own choice
    // (top tabs / side rail / category pages) instead of always using the top strip.
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val universalLayout = prefs.getBoolean("deck_universal_layout", false)
    val portraitStrip = rememberPortraitStrip()
    val layout = if (LocalDeckPortrait.current) {
        if (portraitStrip) "portrait" else "pages"
    } else if (universalLayout) {
        prefs.getString("settings_layout", "rail") ?: "rail"
    } else {
        "tabs"
    }
    var pagesCategoryOpen by remember { mutableStateOf(false) }
    val tabLabels = DeckContentsTab.values().map { it.label }

    val installedKeys by vm.installedKeys.collectAsState()
    val folders by vm.savedFolders.collectAsState()
    val browseCategory by vm.categoryType.collectAsState()
    var browseRepo by remember { mutableStateOf<String?>(null) }
    val archived = remember(folders) { folders.values.sumOf { it.size } }

    // Picking another component family starts again at its repository list.
    LaunchedEffect(browseCategory) { browseRepo = null }

    // B inside a repository folds back to the repository list before it leaves the section.
    val detailOpen = browseCategory != null && browseRepo != null
    DisposableEffect(detailOpen, pagesCategoryOpen) {
        tabs.onBack = {
            if (pagesCategoryOpen) {
                pagesCategoryOpen = false
                true
            } else if (detailOpen) {
                browseRepo = null
                true
            } else false
        }
        onDispose { tabs.onBack = null }
    }
    BackHandler(enabled = detailOpen || pagesCategoryOpen) {
        if (pagesCategoryOpen) pagesCategoryOpen = false else browseRepo = null
    }

    Box(Modifier.fillMaxSize().deckPageBackground()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Contents",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckPalette.onBackground,
                    )
                    Text(
                        text = "${installedKeys.size} installed - $archived archived",
                        fontSize = 13.sp,
                        color = DeckPalette.onSurfaceVariant,
                    )
                }
                DeckIconAction(Icons.Filled.Refresh, "Refresh contents") {
                    vm.refreshStatus()
                    vm.refreshFolders()
                    vm.reloadSources()
                    vm.refreshSelected()
                }
            }
            if (layout == "tabs") {
                Spacer(Modifier.height(12.dp))
                DeckStretchyTabs(
                    labels = tabLabels,
                    selected = tab,
                    onSelect = { tabs.select(it) },
                    focusable = false,
                )
                Spacer(Modifier.height(12.dp))
            }
            if (layout == "portrait") {
                DeckPortraitChips(tabLabels, tab) { tabs.select(it) }
            }
            if (layout == "pages" && !pagesCategoryOpen) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tabLabels.forEachIndexed { i, label ->
                        DeckEditorCategoryRow(
                            label = label,
                            selected = i == tab,
                            onClick = {
                                tabs.select(i)
                                pagesCategoryOpen = true
                            },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (layout == "rail") {
                        DeckEditorCategoryRail(
                            labels = tabLabels,
                            selected = tab,
                            onSelect = { tabs.select(it) },
                            padFocus = false,
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .padding(end = 12.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (layout == "pages" && pagesCategoryOpen) {
                            DeckRow(
                                label = "← All categories",
                                hint = "Back to the category list",
                                onConfigClick = { pagesCategoryOpen = false },
                                control = {},
                            )
                            Spacer(Modifier.height(8.dp))
                        }
        // Switching tabs removes the focused control with the old page; once the swap settles, put the
        // pad on the new page's first control (top-left in focus order) instead of a lost focus.
        val tabContentFocus = remember { FocusRequester() }
        var firstTabShown by remember { mutableStateOf(true) }
        LaunchedEffect(tab, pagesCategoryOpen) {
            if (firstTabShown) { firstTabShown = false; return@LaunchedEffect }
            kotlinx.coroutines.delay(220)
            runCatching { tabContentFocus.requestFocus() }
        }
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val forward = targetState >= initialState
                                (fadeIn(tween(300, delayMillis = 60, easing = DeckEase)) +
                                    slideInHorizontally(tween(420, delayMillis = 60, easing = DeckEase)) {
                                        if (forward) it / 24 else -it / 24
                                    })
                                    .togetherWith(
                                        fadeOut(tween(170, easing = DeckEase)) +
                                            slideOutHorizontally(tween(170, easing = DeckEase)) {
                                                if (forward) -it / 40 else it / 40
                                            },
                                    )
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(tabContentFocus).focusGroup(),
                            label = "contentsTab",
                        ) { which ->
                            when (which) {
                                0 -> DeckContentsBrowseTab(vm, browseRepo) { browseRepo = it }
                                1 -> DeckContentsFilesTab(vm)
                                else -> DeckContentsInstalledTab(vm)
                            }
                        }
                    }
                }
            }
        }
        DeckContentsInstallOverlay()
    }
}

// ── Chrome ────────────────────────────────────────────────────────────────────

@Composable
private fun DeckIconAction(
    icon: ImageVector,
    label: String,
    tint: Color = DeckPalette.onSurfaceVariant,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .border(1.dp, DeckPalette.line, RoundedCornerShape(11.dp))
            .deckRing(interaction, RoundedCornerShape(11.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DeckAction(
    text: String,
    primary: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .deckRing(interaction, RoundedCornerShape(12.dp))
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) DeckPalette.primary else DeckPalette.surface)
            .border(
                1.dp,
                if (primary) DeckPalette.primary else DeckPalette.line,
                RoundedCornerShape(12.dp),
            )
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            color = if (primary) DeckPalette.onPrimary else DeckPalette.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeckDangerAction(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DeckPalette.error.copy(alpha = 0.12f))
            .border(1.dp, DeckPalette.error, RoundedCornerShape(12.dp))
            .deckRing(interaction, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(text, color = DeckPalette.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeckBadge(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun DeckSectionLabel(title: String, count: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = DeckPalette.onSurfaceVariant,
        )
        if (count != null) {
            Text(
                text = "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeckPalette.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
        Box(Modifier.weight(1f).height(1.dp).background(DeckPalette.line))
    }
}

@Composable
private fun DeckEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = DeckPalette.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

@Composable
private fun DeckLoading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
        Text("Loading...", fontSize = 13.sp, color = DeckPalette.onSurfaceVariant)
    }
}

// ── Browse ────────────────────────────────────────────────────────────────────

@Composable
private fun DeckContentsBrowseTab(
    vm: ContentsHubViewModel,
    repo: String?,
    onRepo: (String?) -> Unit,
) {
    val query by vm.query.collectAsState()
    val results by vm.searchResults.collectAsState()
    val searching by vm.searching.collectAsState()
    val category by vm.categoryType.collectAsState()
    val items by vm.categoryItems.collectAsState()
    val loading by vm.categoryLoading.collectAsState()
    val sources by vm.sources.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<HubSource?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { DeckSearchField(query) { vm.setQuery(it) } }
            Spacer(Modifier.width(8.dp))
            DeckIconAction(Icons.Filled.Add, "Add repository") { showAdd = true }
            Spacer(Modifier.width(8.dp))
            DeckIconAction(Icons.Filled.FileDownload, "Import or export sources") { showTransfer = true }
            Spacer(Modifier.width(8.dp))
            DeckIconAction(Icons.Filled.Settings, "Contents settings") { showSettings = true }
        }
        Spacer(Modifier.height(12.dp))
        when {
            query.isNotBlank() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (searching) "Searching..." else "${results.size} result${if (results.size == 1) "" else "s"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckPalette.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    DeckIconAction(Icons.Filled.Close, "Clear search") { vm.setQuery("") }
                }
                Spacer(Modifier.height(8.dp))
                if (searching) DeckLoading()
                else if (results.isEmpty()) DeckEmpty("No component matches \"$query\".")
                else DeckDragFrame(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            results,
                            key = { index, item -> "$index-${item.sourceName}-${item.type}-${item.downloadUrl}" },
                        ) { index, item ->
                            DeckRise(index = index % 10) {
                                DeckCatalogCard(vm, item, showSource = true)
                            }
                        }
                    }
                }
            }
            category != null -> {
                val title = deckContentCategories.firstOrNull { it.type == category }?.title
                    ?: category ?: "Components"
                val repoItems = if (repo == null) items else items.filter { it.sourceName == repo }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeckIconAction(
                        Icons.Filled.ArrowBack,
                        if (repo == null) "All component families" else "Back to repositories",
                    ) {
                        if (repo == null) vm.selectCategory(null) else onRepo(null)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (repo == null) title else "$title - $repo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeckPalette.onBackground,
                        )
                        Text(
                            text = if (repo == null) {
                                "Choose a repository"
                            } else {
                                "${repoItems.size} release${if (repoItems.size == 1) "" else "s"}"
                            },
                            fontSize = 12.sp,
                            color = DeckPalette.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> DeckLoading()
                    items.isEmpty() -> DeckEmpty("No releases found for $title.")
                    repo == null -> {
                        val names = items.map { it.sourceName }.toSet()
                        val repos = sources.filter { it.name in names }
                        if (repos.isEmpty()) DeckEmpty("No repository offers $title.")
                        else DeckDragFrame(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                itemsIndexed(
                                    repos,
                                    key = { index, source -> "$index-${source.name}-${source.driverOnly}" },
                                ) { index, source ->
                                    DeckRise(index = index % 10) {
                                        DeckSourceCard(
                                            source = source,
                                            onOpen = { onRepo(source.name) },
                                            onMenu = { menuFor = source },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> DeckDragFrame(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(
                                repoItems,
                                key = { index, item -> "$index-${item.sourceName}-${item.downloadUrl}" },
                            ) { index, item ->
                                DeckRise(index = index % 10) {
                                    DeckCatalogCard(vm, item, showSource = false)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                DeckDragFrame(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(170.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        gridItemsIndexed(
                            deckContentCategories,
                            key = { index, category -> "$index-${category.type}" },
                        ) { index, entry ->
                            DeckRise(index = index % 10) {
                                Box(Modifier.deckDragItem("category-$index-${entry.type}")) {
                                    DeckCategoryCard(entry) { vm.selectCategory(entry.type) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) DeckAddSourceDialog(vm, onDismiss = { showAdd = false })
    if (showTransfer) DeckTransferDialog(vm, onDismiss = { showTransfer = false })
    if (showSettings) DeckContentsSettingsDialog(vm, onDismiss = { showSettings = false })
    menuFor?.let { source ->
        DeckSourceMenuDialog(vm, source, onDismiss = { menuFor = null })
    }
}

@Composable
internal fun DeckSearchField(
    query: String,
    hint: String = "Search every repository...",
    // Last, so existing trailing-lambda callers keep working.
    onQuery: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val edit = rememberDeckEditState()
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            readOnly = !edit.editing,
            textStyle = TextStyle(color = DeckPalette.onBackground, fontSize = 14.sp),
            cursorBrush = SolidColor(DeckPalette.primary),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .focusProperties { canFocus = edit.editing }
                .focusRequester(edit.fieldFocus)
                .onFocusChanged { if (edit.editing && !it.isFocused) edit.editing = false }
                .onPreviewKeyEvent { event ->
                    if (edit.editing && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.ButtonB || event.key == Key.Back)
                    ) {
                        edit.returnFocus = true
                        edit.editing = false
                        true
                    } else false
                }
                .clip(shape)
                .background(DeckPalette.surface)
                .border(1.dp, if (focused) DeckPalette.primary else DeckPalette.line, shape),
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (focused) DeckPalette.primary else DeckPalette.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = hint,
                                fontSize = 14.sp,
                                color = DeckPalette.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { onQuery("") },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = DeckPalette.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            },
        )
        if (!edit.editing) DeckEditOverlay(edit, Modifier.matchParentSize(), ring = shape)
    }
}

@Composable
private fun DeckCategoryCard(category: DeckContentCategory, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DeckPalette.primary.copy(alpha = 0.14f)),
        ) {
            Icon(category.icon, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = category.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = category.blurb,
                fontSize = 12.sp,
                color = DeckPalette.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = DeckPalette.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DeckCatalogCard(
    vm: ContentsHubViewModel,
    item: ContentsHubViewModel.CatalogItem,
    showSource: Boolean,
) {
    val context = LocalContext.current
    val installedKeys by vm.installedKeys.collectAsState()
    val savedKeys by vm.savedKeys.collectAsState()
    val registry by ContentDownloadRegistry.states.collectAsState()
    val keepRaw by vm.keepRaw.collectAsState()

    val installed = installedKeys.let { vm.isInstalled(item) }
    val saved = savedKeys.let { vm.isSaved(item) }
    val state = registry[ContentsInstaller.keyFor(item.type, item.sourceName, item.versionName)]
    val busy = state != null && !state.terminal

    val title = remember(item.displayName, item.versionName) {
        val name = item.displayName.trim()
        val version = item.versionName.trim()
        if (version.isEmpty() || name == version || name.contains(version, ignoreCase = true)) name
        else "$name $version"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (item.isDriver) DeckPalette.primary.copy(alpha = 0.14f)
                        else DeckPalette.surfaceVariant,
                    ),
            ) {
                Icon(
                    deckContentTypeIcon(item.type),
                    contentDescription = null,
                    tint = if (item.isDriver) DeckPalette.primary else DeckPalette.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (showSource) {
                    Text(
                        text = item.sourceName.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = DeckPalette.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckPalette.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeckMeta(Icons.Filled.Inventory2, item.fileName)
                    item.sizeBytes?.let { DeckMeta(Icons.Filled.SdStorage, RemoteSourceRepository.formatFileSize(it)) }
                    item.publishedAt?.let { DeckMeta(Icons.Filled.Event, it) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckBadge(item.type, DeckPalette.onSurfaceVariant)
            if (installed) DeckBadge("Installed", ContentsGreen)
            if (saved) DeckBadge("Saved", ContentsBlue)
        }
        if (state != null) {
            Spacer(Modifier.height(10.dp))
            DeckDownloadStatus(state)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DeckAction(
                text = if (installed) "Installed" else if (busy) "Working..." else "Download & Install",
                primary = !installed,
                enabled = !installed && !busy,
                modifier = Modifier.weight(1f),
            ) {
                ContentsInstaller.install(
                    context.applicationContext,
                    item.type,
                    item.sourceName,
                    item.versionName,
                    item.downloadUrl,
                    keepRaw,
                ) {
                    vm.refreshStatus()
                    vm.refreshFolders()
                }
            }
            DeckAction(
                text = if (saved) "Saved" else "Save",
                primary = false,
                enabled = !busy && !saved,
                modifier = Modifier.weight(1f),
            ) { vm.saveArchive(item) }
        }
    }
}

@Composable
private fun DeckMeta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = DeckPalette.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = DeckPalette.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeckDownloadStatus(state: ContentDownloadState) {
    val label = when (state.phase) {
        ContentDownloadPhase.DOWNLOADING -> "Downloading ${(state.fraction * 100).toInt()}%"
        ContentDownloadPhase.INSTALLING -> if (state.saveOnly) "Saving to My Files" else "Installing"
        ContentDownloadPhase.DONE -> if (state.saveOnly) "Saved" else "Installed"
        ContentDownloadPhase.ERROR -> state.error ?: "Failed"
    }
    if (!state.terminal) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
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
        Spacer(Modifier.height(4.dp))
    }
    Text(
        text = label,
        fontSize = 11.sp,
        color = if (state.phase == ContentDownloadPhase.ERROR) DeckPalette.error
        else DeckPalette.onSurfaceVariant,
    )
}

// ── Sources ───────────────────────────────────────────────────────────────────

@Composable
private fun DeckSourceCard(
    source: HubSource,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, shape)
            .deckRing(interaction, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(DeckPalette.primary.copy(alpha = 0.12f)),
        ) {
            Icon(
                if (source.driverOnly) Icons.Filled.Memory else Icons.Filled.Folder,
                contentDescription = null,
                tint = DeckPalette.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckPalette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (source.isOfficial) {
                    Spacer(Modifier.width(8.dp))
                    DeckBadge("Official", DeckPalette.primary)
                }
            }
            Text(
                text = source.displayFormat,
                fontSize = 12.sp,
                color = DeckPalette.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.typePills.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    source.typePills.take(4).forEach { DeckBadge(it, DeckPalette.onSurfaceVariant) }
                    if (source.typePills.size > 4) {
                        DeckBadge("+${source.typePills.size - 4}", DeckPalette.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        DeckIconAction(Icons.Filled.MoreVert, "Repository options", onClick = onMenu)
    }
}

// ── My Files ──────────────────────────────────────────────────────────────────

@Composable
private fun DeckContentsFilesTab(vm: ContentsHubViewModel) {
    val context = LocalContext.current
    val folders by vm.savedFolders.collectAsState()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var pendingDelete by remember { mutableStateOf<ComponentLibrary.SavedFile?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedUri(result.data)?.let { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                val type = if (name.endsWith(".wcp", true) || name.endsWith(".tzst", true)) ""
                else ContentsTypes.GPU_DRIVERS
                ContentsInstaller.installFromFile(context.applicationContext, type, name, uri) {
                    vm.refreshStatus()
                    vm.refreshFolders()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "My Files",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckPalette.onBackground,
                )
                Text(
                    text = "Kept archives, filed by type. Install offline, share, or delete.",
                    fontSize = 12.sp,
                    color = DeckPalette.onSurfaceVariant,
                )
            }
            DeckAction("Install from file...", primary = false) {
                picker.launch(
                    InAppFilePicker.buildIntent(context, InAppFilePicker.WCP, "Select content pack"),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (folders.isEmpty()) {
            DeckEmpty("No saved archives yet. Use Save on a component, or turn on Keep raw archive.")
        } else {
            DeckDragFrame(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    folders.keys.toList(),
                    key = { index, type -> "$index-$type" },
                ) { index, type ->
                    DeckRise(index = index % 10) {
                        DeckSavedFolder(
                            type = type,
                            files = folders[type].orEmpty(),
                            open = type in expanded,
                            onToggle = {
                                expanded = if (type in expanded) expanded - type else expanded + type
                            },
                            onInstall = { file ->
                                ContentsInstaller.installFromFile(
                                    context.applicationContext,
                                    file.type,
                                    file.name,
                                    file.uri,
                                ) { ok ->
                                    if (ok) vm.markInstalledFromSaved(file) else vm.refreshStatus()
                                }
                            },
                            onShare = { file -> shareDeckArchive(context, file) },
                            onDelete = { file -> pendingDelete = file },
                        )
                    }
                }
            }
            }
        }
    }

    pendingDelete?.let { file ->
        DeckConfirm(
            title = "Delete archive?",
            message = "Remove \"${file.name}\" from My Files? Installed components stay installed.",
            confirmLabel = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                vm.deleteSaved(file)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun DeckSavedFolder(
    type: String,
    files: List<ComponentLibrary.SavedFile>,
    open: Boolean,
    onToggle: () -> Unit,
    onInstall: (ComponentLibrary.SavedFile) -> Unit,
    onShare: (ComponentLibrary.SavedFile) -> Unit,
    onDelete: (ComponentLibrary.SavedFile) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                    .deckRing(interaction, shape)
                    .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckPalette.primary.copy(alpha = 0.12f)),
            ) {
                Icon(
                    if (ContentsTypes.isDriver(type)) Icons.Filled.ViewInAr else Icons.Filled.Folder,
                    contentDescription = null,
                    tint = DeckPalette.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = type,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${files.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeckPalette.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (open) "v" else ">",
                fontSize = 12.sp,
                color = DeckPalette.onSurfaceVariant,
            )
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
            files.forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Icon(
                        if (file.name.endsWith(".zip", true)) Icons.Filled.FolderZip else Icons.Filled.Inventory2,
                        contentDescription = null,
                        tint = DeckPalette.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DeckPalette.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (file.savedOnly) {
                                "${RemoteSourceRepository.formatFileSize(file.sizeBytes)} - saved only, not installed"
                            } else RemoteSourceRepository.formatFileSize(file.sizeBytes),
                            fontSize = 11.sp,
                            color = if (file.savedOnly) ContentsBlue else DeckPalette.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DeckIconAction(Icons.Filled.InstallDesktop, "Install ${file.name}", DeckPalette.primary) {
                        onInstall(file)
                    }
                    Spacer(Modifier.width(6.dp))
                    DeckIconAction(Icons.Filled.Share, "Share ${file.name}") { onShare(file) }
                    Spacer(Modifier.width(6.dp))
                    DeckIconAction(Icons.Filled.DeleteOutline, "Delete ${file.name}", DeckPalette.error) {
                        onDelete(file)
                    }
                }
            }
        }
    }
}

private fun shareDeckArchive(context: Context, file: ComponentLibrary.SavedFile) {
    runCatching {
        val shareUri = if (file.uri.scheme == "content") file.uri else {
            val path = file.uri.path ?: return
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.tileprovider",
                java.io.File(path),
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }
}

// ── Installed ─────────────────────────────────────────────────────────────────

@Composable
private fun DeckContentsInstalledTab(vm: ContentsHubViewModel) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    var confirmDriver by remember { mutableStateOf<String?>(null) }
    var confirmProfile by remember { mutableStateOf<ContentProfile?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    val manager = remember { AdrenotoolsManager(context) }
    val drivers = remember(refreshKey) { manager.enumarateInstalledDrivers().toList() }
    val components = remember(refreshKey) {
        val contents = ContentsManager(context)
        contents.syncContents()
        ContentProfile.ContentType.values().mapNotNull { type ->
            val installed = (contents.getProfiles(type) ?: emptyList()).filter { it.remoteUrl == null }
            if (installed.isEmpty()) null else type to installed
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedUri(result.data)?.let { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                val type = if (name.endsWith(".wcp", true) || name.endsWith(".tzst", true)) ""
                else ContentsTypes.GPU_DRIVERS
                ContentsInstaller.installFromFile(context.applicationContext, type, name, uri) {
                    refreshKey++
                    vm.refreshStatus()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Installed",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckPalette.onBackground,
                )
                Text(
                    text = "${drivers.size} drivers - ${components.sumOf { it.second.size }} components",
                    fontSize = 12.sp,
                    color = DeckPalette.onSurfaceVariant,
                )
            }
            DeckAction("Install from file...", primary = false) {
                picker.launch(
                    InAppFilePicker.buildIntent(context, InAppFilePicker.WCP, "Select content pack"),
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                DeckSectionLabel("GPU Drivers", drivers.size)
            }
            if (drivers.isEmpty()) {
                item { DeckEmpty("No GPU drivers installed.") }
            } else {
                itemsIndexed(drivers, key = { index, id -> "$index-$id" }) { _, id ->
                    DeckInstalledRow(
                        icon = Icons.Filled.ViewInAr,
                        title = manager.getDriverName(id),
                        subtitle = manager.getDriverVersion(id),
                        onRemove = { confirmDriver = id },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                DeckSectionLabel("Components", components.sumOf { it.second.size })
            }
            if (components.isEmpty()) {
                item { DeckEmpty("No components installed.") }
            } else {
                itemsIndexed(
                    components,
                    key = { index, pair -> "$index-${pair.first}" },
                ) { _, pair ->
                    DeckInstalledComponents(
                        type = pair.first.toString(),
                        profiles = pair.second,
                        open = pair.first.toString() in expanded,
                        onToggle = {
                            val key = pair.first.toString()
                            expanded = if (key in expanded) expanded - key else expanded + key
                        },
                        onRemove = { confirmProfile = it },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { Spacer(Modifier.height(14.dp)) }
        }
    }

    confirmDriver?.let { id ->
        DeckConfirm(
            title = "Remove driver?",
            message = "Remove \"${manager.getDriverName(id)}\"? Games using it fall back to the next driver.",
            confirmLabel = "Remove",
            onDismiss = { confirmDriver = null },
            onConfirm = {
                manager.removeDriver(id)
                confirmDriver = null
                refreshKey++
                vm.refreshStatus()
            },
        )
    }
    confirmProfile?.let { profile ->
        DeckConfirm(
            title = "Remove component?",
            message = "Remove \"${profile.verName}\"? Containers using it will need another version.",
            confirmLabel = "Remove",
            onDismiss = { confirmProfile = null },
            onConfirm = {
                val contents = ContentsManager(context)
                contents.removeContent(profile)
                contents.syncContents()
                confirmProfile = null
                refreshKey++
                vm.refreshStatus()
            },
        )
    }
}

@Composable
private fun DeckInstalledRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DeckPalette.primary.copy(alpha = 0.12f)),
        ) {
            Icon(icon, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = DeckPalette.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DeckIconAction(Icons.Filled.DeleteOutline, "Remove $title", DeckPalette.error, onRemove)
    }
}

@Composable
private fun DeckInstalledComponents(
    type: String,
    profiles: List<ContentProfile>,
    open: Boolean,
    onToggle: () -> Unit,
    onRemove: (ContentProfile) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, RoundedCornerShape(14.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                    .deckRing(interaction, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
                .padding(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeckPalette.primary.copy(alpha = 0.12f)),
            ) {
                Icon(
                    deckContentTypeIcon(type),
                    contentDescription = null,
                    tint = DeckPalette.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = type,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profiles.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeckPalette.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (open) "v" else ">", fontSize = 12.sp, color = DeckPalette.onSurfaceVariant)
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
            profiles.forEach { profile ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = profile.verName ?: "Unknown version",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DeckPalette.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Code ${profile.verCode}",
                            fontSize = 11.sp,
                            color = DeckPalette.onSurfaceVariant,
                        )
                    }
                    DeckIconAction(
                        Icons.Filled.DeleteOutline,
                        "Remove ${profile.verName}",
                        DeckPalette.error,
                    ) { onRemove(profile) }
                }
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun DeckConfirm(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DeckConfigDialog(title = title, onDismiss = onDismiss, compact = true) {
        DeckConfigNote(message)
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            DeckAction("Cancel", primary = false) { onDismiss() }
            Spacer(Modifier.width(8.dp))
            DeckDangerAction(confirmLabel, onConfirm)
        }
    }
}

@Composable
private fun DeckAddSourceDialog(vm: ContentsHubViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isDriver by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(RemoteSourceRepository.SourceFormat.WCP_JSON) }

    DeckConfigDialog(title = "Add repository", onDismiss = onDismiss) {
        DeckConfigNote("Paste any component or GPU-driver source. It joins the list like a built-in.")
        DeckConfigTextField("Display name", name, { name = it })
        DeckConfigTextField(
            label = if (isDriver) "GitHub repo (owner/repo) or JSON URL" else "URL (repo, releases API, or JSON)",
            value = url,
            onValueChange = { url = it },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "GPU driver source",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeckPalette.onBackground,
                )
                Text(
                    "Shared with the AdrenoTools screen",
                    fontSize = 11.sp,
                    color = DeckPalette.onSurfaceVariant,
                )
            }
            DeckToggle(checked = isDriver, onChange = { isDriver = it })
        }
        if (!isDriver) {
            val chips = RemoteSourceRepository.SourceFormat.values().map { fmt ->
                val pick: () -> Unit = { format = fmt }
                Triple(fmt.name.replace('_', ' '), format == fmt, pick)
            }
            DeckConfigChipGrid("Source format", chips)
        }
        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            DeckAction("Cancel", primary = false) { onDismiss() }
            Spacer(Modifier.width(8.dp))
            DeckAction(
                text = "Add source",
                primary = true,
                enabled = name.isNotBlank() && url.isNotBlank(),
            ) {
                val sourceName = name.trim()
                val sourceUrl = url.trim()
                if (isDriver) {
                    vm.addDriverSource(sourceName, deckDriverFeedFor(sourceUrl))
                } else {
                    vm.addComponentSource(
                        RemoteSourceRepository.RemoteSource(
                            name = sourceName,
                            url = sourceUrl,
                            format = format,
                            supportedTypes = emptyList(),
                            isCustom = true,
                        ),
                    )
                }
                onDismiss()
            }
        }
    }
}

private fun deckDriverFeedFor(url: String): DriverFeed {
    val looksJson = url.endsWith(".json", true) || url.contains("raw.githubusercontent.com", true)
    if (!looksJson) {
        DriverSourceStore.parseGithubRepo(url, "")?.let { (owner, repo) ->
            return DriverFeed.GithubReleases(owner, repo)
        }
    }
    return DriverFeed.Json(url)
}

@Composable
private fun DeckSourceMenuDialog(
    vm: ContentsHubViewModel,
    source: HubSource,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DeckConfigDialog(title = source.name, onDismiss = onDismiss, compact = true) {
        DeckDialogAction(Icons.Filled.OpenInNew, "Open source page") {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.browseUrl(source))))
            }
            onDismiss()
        }
        DeckDialogDivider()
        DeckDialogAction(
            icon = Icons.Filled.Delete,
            label = if (source.removeIsHide) "Hide default repository" else "Remove repository",
            tint = DeckPalette.error,
        ) {
            vm.removeSource(source)
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            DeckAction("Close", primary = false) { onDismiss() }
        }
    }
}

@Composable
private fun DeckDialogAction(
    icon: ImageVector,
    label: String,
    tint: Color = DeckPalette.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (tint == DeckPalette.error) DeckPalette.error else DeckPalette.onBackground,
        )
    }
}

@Composable
private fun DeckDialogDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
}

@Composable
private fun DeckTransferDialog(vm: ContentsHubViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() } ?: return@let
                    vm.importRepoListJson(json, merge = true)
                }
                onDismiss()
            }
        }
    }
    DeckConfigDialog(title = "Import / export sources", onDismiss = onDismiss, compact = true) {
        DeckDialogAction(Icons.Filled.FileDownload, "Import list from file...") {
            importer.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                },
            )
        }
        DeckDialogDivider()
        DeckDialogAction(Icons.Filled.FileUpload, "Export current list") {
            runCatching {
                val json = vm.exportRepoListJson()
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_TEXT, json)
                }
                context.startActivity(Intent.createChooser(share, "Export sources"))
            }
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            DeckAction("Close", primary = false) { onDismiss() }
        }
    }
}

@Composable
private fun DeckContentsSettingsDialog(vm: ContentsHubViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val keepRaw by vm.keepRaw.collectAsState()
    val showOfficial by vm.showOfficial.collectAsState()
    val baseDisplay by vm.baseDisplay.collectAsState()

    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedPath(result.data)?.let { path ->
                vm.library.setFileBase(path)
                vm.refreshBase()
                vm.refreshFolders()
                onDismiss()
            }
        }
    }

    DeckConfigDialog(title = "Contents settings", onDismiss = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Keep raw archive by default",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeckPalette.onBackground,
                )
                Text(
                    "Save every install's download to My Files",
                    fontSize = 11.sp,
                    color = DeckPalette.onSurfaceVariant,
                )
            }
            DeckToggle(checked = keepRaw, onChange = { vm.setKeepRaw(it) })
        }
        DeckDialogDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Show Official catalog",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeckPalette.onBackground,
                )
                Text(
                    "Container sheets keep it either way",
                    fontSize = 11.sp,
                    color = DeckPalette.onSurfaceVariant,
                )
            }
            DeckToggle(checked = showOfficial, onChange = { vm.setShowOfficial(it) })
        }
        DeckDialogDivider()
        DeckDialogAction(Icons.Filled.FolderOpen, "Save location: $baseDisplay") {
            dirPicker.launch(
                InAppFilePicker.buildDirIntent(
                    context,
                    title = "Select save folder",
                    initialDir = vm.library.currentFileBasePath(),
                ),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
        ) {
            DeckAction("Default folder", primary = false) {
                vm.library.setDefaultBase()
                vm.refreshBase()
                vm.refreshFolders()
                onDismiss()
            }
            DeckAction("App-private", primary = false) {
                vm.library.setFileBase(vm.library.appPrivateBasePath())
                vm.refreshBase()
                vm.refreshFolders()
                onDismiss()
            }
        }
        DeckDialogDivider()
        DeckDialogAction(Icons.Filled.Restore, "Restore default repositories") {
            vm.restoreDefaultSources()
            onDismiss()
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            DeckAction("Close", primary = false) { onDismiss() }
        }
    }
}

// ── Install progress ─────────────────────────────────────────────────────────

@Composable
private fun DeckContentsInstallOverlay() {
    val registry by ContentDownloadRegistry.states.collectAsState()
    var shownKey by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<ContentDownloadState?>(null) }

    LaunchedEffect(registry) {
        val key = shownKey
        if (key == null) {
            registry.entries.firstOrNull { it.key.startsWith("contents::") && !it.value.terminal }?.let {
                shownKey = it.key
                shown = it.value
            }
        } else {
            registry[key]?.let { shown = it }
        }
    }

    shown?.let { state ->
        DeckInstallDialog(
            state = state,
            onCancel = { ContentDownloadRegistry.requestCancel(state.key) },
            onDismiss = {
                ContentDownloadRegistry.remove(state.key)
                shown = null
                shownKey = null
            },
        )
    }
}

@Composable
private fun DeckInstallDialog(
    state: ContentDownloadState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    DeckConfigDialog(
        title = when {
            state.saveOnly && state.terminal -> "Archive saved"
            state.saveOnly -> "Saving archive"
            state.terminal -> "Component ready"
            else -> "Installing component"
        },
        onDismiss = { if (state.terminal) onDismiss() else onCancel() },
        compact = true,
    ) {
        Text(
            text = state.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = DeckPalette.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        state.type?.let { type ->
            Spacer(Modifier.height(6.dp))
            DeckBadge(type, DeckPalette.onSurfaceVariant)
        }
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
        val label = when (state.phase) {
            ContentDownloadPhase.DOWNLOADING -> "Downloading ${(state.fraction * 100).toInt()}%"
            ContentDownloadPhase.INSTALLING -> if (state.saveOnly) "Saving to My Files" else "Installing"
            ContentDownloadPhase.DONE -> if (state.saveOnly) "Saved to My Files" else "Installed"
            ContentDownloadPhase.ERROR -> if (state.cancelled) "Cancelled" else state.error ?: "Failed"
        }
        DeckConfigNote(
            text = label,
            error = state.phase == ContentDownloadPhase.ERROR && !state.cancelled,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.terminal) {
                DeckAction("Done", primary = true) { onDismiss() }
            } else {
                DeckAction("Cancel", primary = false) { onCancel() }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun deckContentTypeIcon(type: String): ImageVector = when {
    ContentsTypes.isDriver(type) -> Icons.Filled.ViewInAr
    type.equals("DXVK", true) -> Icons.Filled.GridView
    type.equals("D7VK", true) -> Icons.Filled.ViewInAr
    type.equals("VKD3D", true) -> Icons.Filled.Widgets
    type.equals("Box64", true) -> Icons.Filled.Memory
    type.equals("WOWBox64", true) -> Icons.Filled.DeveloperBoard
    type.equals("FEXCore", true) -> Icons.Filled.Dns
    type.equals("Wine", true) -> Icons.Filled.LocalBar
    type.equals("Proton", true) -> Icons.Filled.Science
    else -> Icons.Filled.Extension
}
