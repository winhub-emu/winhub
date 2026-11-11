@file:OptIn(ExperimentalMaterial3Api::class)
package com.winhub.hernandez.ui.screens

import androidx.compose.runtime.mutableIntStateOf
import com.winhub.hernandez.core.PresetScope
import com.winhub.hernandez.core.PresetOverrides
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import com.winhub.hernandez.inputcontrols.StickShaper
import com.winhub.hernandez.ui.components.TextButton
import com.winhub.hernandez.ui.components.OutlinedButton
import com.winhub.hernandez.ui.components.Button
import com.winhub.hernandez.ui.components.Switch
import com.winhub.hernandez.ui.components.Checkbox
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.res.Configuration
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winhub.hernandez.MainActivity
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.EmulatorLabels
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.contentdialog.DXVKConfigDialog
import com.winhub.hernandez.contentdialog.WineD3DConfigDialog
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.WrapperManager
import com.winhub.hernandez.contents.WrapperSettingsDictionary
import com.winhub.hernandez.core.AppUtils
import com.winhub.hernandez.core.DefaultVersion
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.GPUInformation
import com.winhub.hernandez.core.ImageUtils
import com.winhub.hernandez.util.InAppFilePicker
import java.io.File
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.ui.components.AudioSettingsDialog
import com.winhub.hernandez.ui.components.audioConfigFromEnv
import com.winhub.hernandez.ui.components.audioConfigToEnv
import com.winhub.hernandez.core.WineThemeManager
import com.winhub.hernandez.core.WineUtils
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.items
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.util.concurrent.Executors
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.widget.fusionhud.parseHudOutline
import com.winhub.hernandez.widget.exportHudDiagnostics
import com.winhub.hernandez.widget.ColorPickerView
import com.winhub.hernandez.widget.CPUListView
import com.winhub.hernandez.ui.components.CollapsibleRail
import com.winhub.hernandez.ui.components.ContainerGlossarySheet
import com.winhub.hernandez.ui.components.EnvVarsEditor
import com.winhub.hernandez.ui.components.PlayerSlotsEditor
import com.winhub.hernandez.ui.components.RailItem
import com.winhub.hernandez.ui.components.RailLink
import com.winhub.hernandez.ui.components.RailSection
import com.winhub.hernandez.ui.components.rememberRailState
import com.winhub.hernandez.contentdialog.VegasKeyCatalog
import com.winhub.hernandez.contentdialog.VegasKeyKnowledge
import com.winhub.hernandez.contentdialog.VegasTierPresets
import com.winhub.hernandez.container.VegasLiveCheck
import com.winhub.hernandez.core.HttpUtils
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Block
import androidx.compose.foundation.layout.ExperimentalLayoutApi

// Serializes all native adrenotools probing (isDriverSupported + enumerateExtensions) off the
// main thread. Serial = no concurrent AdrenoTools hooks (old SIGSEGV); off-main = no ANR.
internal val graphicsProbeMutex = Mutex()

// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ContainerDetailScreen(
    containerId: Int,
    onNavigateBack: () -> Unit,
    // One instance per id: this screen is also composed inline (ShortcutsXmbView), where the
    // activity owner is shared across ids and a stale init() would edit the wrong container.
    viewModel: ContainerDetailViewModel = viewModel(key = "containerDetail/$containerId")
) {
    val context = LocalContext.current

    LaunchedEffect(containerId) { viewModel.init(containerId) }

    var showGraphicsDriverConfig by remember { mutableStateOf(false) }
    var showDxvkConfig           by remember { mutableStateOf(false) }
    var showWineD3DConfig        by remember { mutableStateOf(false) }
    var showFpsConfig            by remember { mutableStateOf(false) }
    var showWineDownloadSheet    by remember { mutableStateOf(false) }
    var showBox64DownloadSheet   by remember { mutableStateOf(false) }
    var showFexCoreDownloadSheet by remember { mutableStateOf(false) }
    var showDxvkDownloadSheet    by remember { mutableStateOf(false) }
    // null = hidden; "" = glossary open unfiltered (the button); "term" = open at a field's term.
    var glossaryQuery            by remember { mutableStateOf<String?>(null) }
    var showVegasDownloadSheet   by remember { mutableStateOf(false) }
    var showStockConfigSheet     by remember { mutableStateOf(false) }
    var showVkd3dDownloadSheet   by remember { mutableStateOf(false) }
    var showD7vkDownloadSheet    by remember { mutableStateOf(false) }
    var showVulkanConfig          by remember { mutableStateOf(false) }
    // Bumped after a DXVK/VKD3D/Vegas download so the open DxvkConfigDialog re-reads its version lists.
    var dxvkRefreshKey           by remember { mutableStateOf(0) }

    // AndroidView references for custom views
    val cpuListViewRef      = remember { mutableStateOf<CPUListView?>(null)      }
    val cpuListWoW64Ref     = remember { mutableStateOf<CPUListView?>(null)      }
    val colorPickerViewRef  = remember { mutableStateOf<ColorPickerView?>(null)  }

    // DRIVES is per-container (letters map to real paths), so it's dropped in "New Container Defaults"
    // mode. Content is dispatched by TITLE below (not raw index) so removing a tab never misaligns.
    val tabTitles = if (viewModel.defaultsMode)
        listOf("GENERAL", "GRAPHICS", "ENVIROMENT", "WIN COMPONENTS", "ADVANCED")
    else
        listOf("GENERAL", "GRAPHICS", "ENVIROMENT", "DRIVES", "WIN COMPONENTS", "ADVANCED")

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val duplicates = viewModel.duplicateDriveLetters
                    if (duplicates.isNotEmpty()) {
                        // Saving would write two drives onto one letter; send the user to the tab.
                        viewModel.selectedTab = tabTitles.indexOf("DRIVES")
                        Toast.makeText(
                            context,
                            "Two drives share " + duplicates.sorted().joinToString(", ") { "$it:" } +
                                " — give each drive its own letter",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else if (!viewModel.isSaving) viewModel.confirm(
                        resolvedGraphicsDriverConfig = viewModel.graphicsDriverConfig,
                        resolvedDXWrapperConfig      = viewModel.dxWrapperConfig,
                        resolvedFPSCounterConfig     = viewModel.fpsCounterConfig,
                        resolvedEnvVars      = viewModel.envVarsStr,
                        resolvedCPUList      = cpuListViewRef.value?.checkedCPUListAsString ?: viewModel.cpuList,
                        resolvedCPUListWoW64 = cpuListWoW64Ref.value?.checkedCPUListAsString ?: viewModel.cpuListWoW64,
                        resolvedColorAsString = colorPickerViewRef.value?.colorAsString ?: "#0277bd",
                        onDone = onNavigateBack
                    )
                },
                containerColor = if (viewModel.isSaving)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Check, contentDescription = "Confirm")
            }
        }
    ) { padding ->
        val contentScroll = rememberScrollState()
        // Switching tabs drops focus with the old content; put the pad on the new tab's first control
        // (top-left in focus order) so it never has to be coaxed back by pressing random directions.
        val tabContentFocus = remember { FocusRequester() }
        val keyboardCtl = LocalSoftwareKeyboardController.current
        var firstTabShown by remember { mutableStateOf(true) }
        LaunchedEffect(viewModel.selectedTab) {
            if (firstTabShown) { firstTabShown = false; return@LaunchedEffect }
            contentScroll.scrollTo(0)
            delay(80)
            // The first control is often the name field; keep the keyboard down until the user opts in.
            if (runCatching { tabContentFocus.requestFocus() }.isSuccess) keyboardCtl?.hide()
        }

        // ── Shared collapsible left rail (landscape: expanded by default + per-screen persistence A;
        //    portrait: always collapsed, no toggle). See ui/components/CollapsibleRail. ─────────────
        val railState = rememberRailState("containers")
        val railCollapsed = railState.collapsed

        val activeTab = tabTitles.getOrNull(viewModel.selectedTab) ?: "GENERAL"
        val screenTitle = when {
            viewModel.defaultsMode -> "New Container Defaults"
            containerId <= 0 -> "New Container"
            else -> "Edit Container"
        }
        val railLinks = buildList {
            if (viewModel.defaultsMode) {
                add(RailLink(stringResource(R.string.reset_to_app_defaults), Icons.Filled.Restore) { viewModel.resetDefaults() })
            }
        }

        val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

        // The single scrolling content region — identical in portrait and landscape, so both layouts
        // reuse it. A bottom buffer (FAB + nav-bar inset + margin) is the ONLY reserved space, so
        // content reaches near the bottom instead of stopping in a dead zone.
        val mainContent: @Composable () -> Unit = {
            val bottomBuffer = 72.dp +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(tabContentFocus)
                    .focusGroup()
                    .verticalScroll(contentScroll)
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp)
            ) {
                Column {
                    // Dispatch by tab TITLE (not index): DRIVES is absent in defaults mode, so a
                    // raw index would misalign the remaining tabs.
                    when (activeTab) {
                        "GENERAL", "GRAPHICS" -> Column {
                            TopLevelFields(
                                tab = activeTab,
                                viewModel = viewModel,
                                onShowGfxConfig = { showGraphicsDriverConfig = true },
                                onShowDxvkConfig = { showDxvkConfig = true },
                                onShowWineD3DConfig = { showWineD3DConfig = true },
                                onShowFpsConfig = { showFpsConfig = true },
                                onShowWineDownloadSheet = { showWineDownloadSheet = true },
                                onShowVulkanConfig = { showVulkanConfig = true },
                            )
                            if (activeTab == "GENERAL") WineConfigTab(viewModel, colorPickerViewRef)
                        }
                        "ENVIROMENT" -> EnvVarsTab(viewModel)
                        "DRIVES" -> DrivesTab(viewModel)
                        "WIN COMPONENTS" -> WinComponentsTab(viewModel)
                        "ADVANCED" -> Column {
                            AdvancedTab(
                                viewModel,
                                cpuListViewRef,
                                cpuListWoW64Ref,
                                onShowBox64DownloadSheet = { showBox64DownloadSheet = true },
                                onShowFexCoreDownloadSheet = { showFexCoreDownloadSheet = true },
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            XRTab(viewModel)
                        }
                    }
                    // Clears the save FAB + nav-bar inset so the last setting can scroll above them,
                    // leaving only a small buffer rather than a large empty band.
                    Spacer(modifier = Modifier.height(bottomBuffer))
                }
            }
        }

        if (isPortrait) {
            // PORTRAIT (the 3 container screens): tabs run across the TOP as a horizontal icon bar,
            // with the help/reset links in a slim row above them; content gets the full width. The
            // collapsible left rail is landscape-only. Same icons/labels as the rail — repositioned.
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ContainerTopTabs(
                    tabs = tabTitles,
                    selected = viewModel.selectedTab,
                    links = railLinks,
                    onSelect = { viewModel.selectedTab = it },
                )
                mainContent()
            }
        } else {
            // LANDSCAPE: unchanged — the shared collapsible left rail beside full-height content.
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                CollapsibleRail(
                    state = railState,
                    title = screenTitle,
                    headerIcon = R.drawable.icon_menu_container,
                    links = railLinks,
                    sections = listOf(
                        RailSection(
                            header = null,
                            items = tabTitles.mapIndexed { index, tab ->
                                RailItem(tab, tabIcon(tab), index == viewModel.selectedTab) { viewModel.selectedTab = index }
                            },
                        )
                    ),
                )

                // ── Content: full height beside the rail ───────────────────────────────────────
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Collapsed rail hides the tab labels, so surface the active tab name over the
                    // content (mockup: "ctxhdr") — the user never loses their place.
                    if (railCollapsed) {
                        Text(
                            activeTab,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                        )
                    }
                    mainContent()
                }
            }
        }
    }

    glossaryQuery?.let { q ->
        ContainerGlossarySheet(initialQuery = q, onDismiss = { glossaryQuery = null })
    }

    if (showGraphicsDriverConfig) {
        GraphicsDriverConfigDialog(
            graphicsDriver = StringUtils.parseIdentifier(viewModel.selectedGraphicsDriver),
            initialConfig = viewModel.graphicsDriverConfig,
            onConfirm = { newConfig -> viewModel.graphicsDriverConfig = newConfig; showGraphicsDriverConfig = false },
            onDismiss = { showGraphicsDriverConfig = false }
        )
    }
    val isVegasWrapper = StringUtils.parseIdentifier(viewModel.selectedDXWrapper ?: "").contains("vegas")
    // Mali compat/bcn testers need DXVK 1.x reachable even with VKD3D selected, to try the
    // 1.10.3 adapter-accept workaround (#137). Relax the #113 DXVK-2.x-only filter ONLY for the
    // "Wrapper + compat + bcn" driver; every other driver keeps the guard unchanged.
    val relaxDxvkFilter = StringUtils.parseIdentifier(viewModel.selectedGraphicsDriver) == "wrapper-compat-bcn"
    if (showDxvkConfig) {
        DxvkConfigDialog(
            isArm64EC = viewModel.isArm64EC,
            isVegas = isVegasWrapper,
                containerRootDir = viewModel.container?.rootDir,
            relaxDxvkFilter = relaxDxvkFilter,
            refreshKey = dxvkRefreshKey,
            initialConfig = viewModel.dxWrapperConfig,
            onLivePointerChanged = { p ->
                    // persist the live pointer into envVars
                },
            onConfirm = { newConfig -> viewModel.dxWrapperConfig = newConfig; showDxvkConfig = false },
            onDismiss = { showDxvkConfig = false },
            // Close the config dialog first — the download sheet is a ModalBottomSheet (activity
            // window) and would otherwise render BEHIND this AlertDialog. It reopens on sheet dismiss.
            onDownloadDxvk = { showDxvkConfig = false; if (isVegasWrapper) showVegasDownloadSheet = true else showDxvkDownloadSheet = true },
            onDownloadVkd3d = { showDxvkConfig = false; showVkd3dDownloadSheet = true },
            onOpenConfigDownload = { showDxvkConfig = false; showStockConfigSheet = true },
            onDownloadD7vk = { showDxvkConfig = false; showD7vkDownloadSheet = true }
        )
    }
    if (showWineD3DConfig) {
        WineD3DConfigDialog(
            initialConfig = viewModel.dxWrapperConfig,
            onConfirm = { newConfig -> viewModel.dxWrapperConfig = newConfig; showWineD3DConfig = false },
            onDismiss = { showWineD3DConfig = false }
        )
    }
    if (showFpsConfig) {
        FpsCounterConfigDialog(
            initialConfig = viewModel.fpsCounterConfig,
            onConfirm = { newConfig -> viewModel.fpsCounterConfig = newConfig; showFpsConfig = false },
            onDismiss = { showFpsConfig = false }
        )
    }

    if (showVulkanConfig) {
        VulkanSettingsDialog(
            initialConfig = "native=${viewModel.rendererNative}" +
                ";presentMode=${viewModel.rendererPresentMode}" +
                ";driverId=${viewModel.rendererDriverId}" +
                ";filterMode=${viewModel.rendererFilterMode}" +
                ";swapRB=${viewModel.rendererSwapRB}",
            onConfirm = { newConfig ->
                val m = parseVulkanConfig(newConfig)
                viewModel.rendererNative      = m["native"] == "true"
                viewModel.rendererPresentMode = m["presentMode"] ?: "fifo"
                viewModel.rendererDriverId    = m["driverId"] ?: "system"
                viewModel.rendererFilterMode  = m["filterMode"]?.toIntOrNull() ?: 0
                viewModel.rendererSwapRB      = m["swapRB"] == "true"
                showVulkanConfig = false
            },
            onDismiss = { showVulkanConfig = false },
            frameGenSelected = viewModel.frameGenEngine != "off"
        )
    }

    // ── Content download sheets ────────────────────────────────────────────
    if (showWineDownloadSheet) {
        ContentDownloadSheet(
            contentTypes = listOf(
                com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_WINE,
                com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_PROTON,
            ),
            onDismiss = { showWineDownloadSheet = false },
            onContentChanged = { viewModel.refreshWineVersions() },
            inUseKey = viewModel.selectedWineVersion,
        )
    }
    if (showBox64DownloadSheet) {
        ContentDownloadSheet(
            contentType = if (viewModel.isArm64EC)
                com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
            else
                com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            onDismiss = { showBox64DownloadSheet = false },
            onContentChanged = { viewModel.refreshBox64Versions() },
            inUseKey = viewModel.selectedBox64Version,
        )
    }
    if (showFexCoreDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            onDismiss = { showFexCoreDownloadSheet = false },
            onContentChanged = { viewModel.refreshFEXCoreVersions() },
            inUseKey = viewModel.selectedFEXCoreVersion,
        )
    }
    if (showDxvkDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_DXVK,
            onDismiss = { showDxvkDownloadSheet = false; showDxvkConfig = true },
            onContentChanged = { dxvkRefreshKey++ }
        )
    }
    if (showVkd3dDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
            onDismiss = { showVkd3dDownloadSheet = false; showDxvkConfig = true },
            onContentChanged = { dxvkRefreshKey++ }
        )
    }
    if (showD7vkDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_D7VK,
            onDismiss = { showD7vkDownloadSheet = false; showDxvkConfig = true },
            onContentChanged = { dxvkRefreshKey++ }
        )
    }
    if (showVegasDownloadSheet) {
        VegasDownloadSheet(
            onDismiss = { showVegasDownloadSheet = false; showDxvkConfig = true },
            onContentChanged = { dxvkRefreshKey++ }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Parse the semicolon-separated Vulkan settings string ("native=..;presentMode=..;..") into a map.
internal fun parseVulkanConfig(s: String): Map<String, String> =
    s.split(";").mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
    }.toMap()

/** The Material icon for each container-settings tab (mirrors the mockup's glyphs). */
private fun tabIcon(title: String): ImageVector = when (title) {
    "GENERAL" -> Icons.Filled.Settings
    "ENVIROMENT" -> Icons.Filled.Extension
    "GRAPHICS" -> Icons.Filled.Memory
    "DRIVES" -> Icons.Filled.Storage
    "WIN COMPONENTS" -> Icons.Filled.Widgets
    "ADVANCED" -> Icons.Filled.Tune
    else -> Icons.Filled.Settings
}

/** Abbreviates the two long tab titles so the portrait top bar stays tidy (mirrors the rail's
 *  collapsed labels). */
private fun topTabLabel(title: String): String = when (title) {
    "WIN COMPONENTS" -> "WIN COMP"
    else -> title
}

/**
 * Portrait-only: the container-editor tabs as a horizontal TEXT strip across the top (landscape keeps
 * the collapsible left rail instead). No icons — just the uppercase titles with the accent underline
 * under the active one (by request, so the strip reads as a single minimal row). The help /
 * reset-to-defaults buttons are appended INLINE at the end of the same row (a faint divider hints
 * they're actions, not tabs). Every cell gets equal weight, so the tabs + buttons evenly fill the
 * full width. Used by all 3 container screens (New / Edit = tabs + help; Defaults = tabs + help +
 * reset). No separate button row above the tabs, so the top stays tight under the header.
 */
@Composable
private fun ContainerTopTabs(
    tabs: List<String>,
    selected: Int,
    links: List<RailLink>,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val active = index == selected
                TopCell(
                    label = topTabLabel(tab),
                    tint = if (active) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    highlight = active,
                    indicatorOn = active,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (links.isNotEmpty()) {
                // Faint separator: everything past here is an action button, not a tab.
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                )
                links.forEach { link ->
                    TopCell(
                        label = topActionLabel(link.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        highlight = false,
                        indicatorOn = false,
                        onClick = link.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

/** One cell of the portrait top bar — a tab or an action button. TEXT ONLY (no icons, by request):
 *  the uppercase title sits on one line with the accent underline under it. The underline collapses
 *  to zero width when off, so every cell keeps the same height. */
@Composable
private fun TopCell(
    label: String,
    tint: Color,
    highlight: Boolean,
    indicatorOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 9.dp, horizontal = 2.dp),
    ) {
        Text(
            label.uppercase(),
            color = tint,
            // Longer titles drop a size so the full word stays on one line instead of ellipsizing.
            fontSize = if (label.length > 9) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (label.length > 9) 0.sp else 0.4.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(if (indicatorOn) 16.dp else 0.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (indicatorOn) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/** Short label for the top-bar action buttons — only Help + Restore ever appear there. */
private fun topActionLabel(icon: ImageVector): String =
    if (icon == Icons.Filled.Restore) "RESET" else "HELP"

@Composable
internal fun VulkanSettingsDialog(
    initialConfig: String,
    onConfirm: (newConfig: String) -> Unit,
    onDismiss: () -> Unit,
    // The FG engine dropdown lives on the main screen, not in this dialog — pass whether one is
    // selected so we can caption the Present Mode field about the temporary auto-switch to Mailbox.
    frameGenSelected: Boolean = false
) {
    // The config string is SEMICOLON-separated (see the confirm button below), so parse it that way.
    // (The old KeyValueSet path split on commas and silently returned every default.)
    val cfg = remember { parseVulkanConfig(initialConfig) }
    var nativeRender by remember { mutableStateOf(cfg["native"] == "true") }
    var presentMode by remember { mutableStateOf(cfg["presentMode"] ?: "fifo") }
    var driverId by remember { mutableStateOf(cfg["driverId"] ?: "system") }
    // Read-only now: editing moved to the in-game drawer "Scaling mode". Kept so the
    // persisted value round-trips through this dialog and still seeds the launch default.
    val filterMode = remember { cfg["filterMode"]?.toIntOrNull() ?: 0 }
    var swapRB by remember { mutableStateOf(cfg["swapRB"] == "true") }

    // Per-field "?" help — this dialog is its own composable, so it carries its own helpRes.
    // HelpDialog renders as a Dialog on top of this AlertDialog (fine — same pattern as elsewhere).
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vulkan_settings)) },
        text = {
            // Cap the scrollable region so tall content scrolls inside the dialog instead of
            // pushing the OK/Cancel buttons off-screen (Material3 AlertDialog doesn't bound its
            // text slot height on its own).
            val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.renderer_native), Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_renderer_native }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                    Switch(checked = nativeRender, onCheckedChange = { nativeRender = it })
                }

                val presentModes = listOf("fifo", "mailbox", "immediate")
                val presentModeLabels = listOf(
                    stringResource(R.string.renderer_present_mode_fifo),
                    stringResource(R.string.renderer_present_mode_mailbox),
                    stringResource(R.string.renderer_present_mode_immediate)
                )
                val selectedPresentIdx = presentModes.indexOf(presentMode).coerceAtLeast(0)
                // Present mode is ignored under Native Rendering (direct scanout bypasses the
                // swapchain), so grey it out while native is on.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.renderer_present_mode),
                        options = presentModeLabels,
                        selectedOption = presentModeLabels[selectedPresentIdx],
                        onSelect = { presentMode = presentModes[presentModeLabels.indexOf(it)] },
                        enabled = !nativeRender,
                        modifier = (if (nativeRender) Modifier.alpha(0.5f) else Modifier).weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.renderer_present_mode_help_content }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                // FG temporarily forces Mailbox; caption the field so FIFO-while-FG-selected isn't confusing.
                if (frameGenSelected) {
                    Text(
                        stringResource(R.string.renderer_present_mode_fg_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Renderer (compositor) driver: which Vulkan driver the present layer itself runs on —
                // "System" (Android's own driver, the safe default) or an installed Turnip. This is the
                // compositor, NOT where your game renders (that's the top-level Graphics Driver). Applied
                // at launch by XServerDisplayActivity (VulkanRenderer.setDriverInfo before nativeInit).
                // Options = System + installed
                // adrenotools drivers; default stays System because a Turnip compositor can black-screen
                // on builds whose WSI doesn't support the surface.
                val vkCtx = androidx.compose.ui.platform.LocalContext.current
                val rendererDriverOptions = remember {
                    val installed = try {
                        com.winhub.hernandez.contents.AdrenotoolsManager(vkCtx).enumarateInstalledDrivers()
                    } catch (e: Exception) { arrayListOf<String>() }
                    (listOf("system") + installed).distinct()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.renderer_driver_id),
                        options = rendererDriverOptions,
                        selectedOption = if (rendererDriverOptions.contains(driverId)) driverId else "system",
                        onSelect = { driverId = it },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_renderer_driver }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }

                // Filter mode (Nearest/Linear) is no longer edited here: the in-game
                // drawer's "Scaling mode" picker is the single source of truth for
                // Vulkan scaling/filtering (modes 1/2 drive the base sampler natively).
                // The persisted `filterMode` value is preserved untouched below and
                // still seeds the drawer's initial scaling mode at launch
                // (XServerDisplayActivity: getRendererFilterMode -> initialUpscaler).

                // Colors = the game buffer's channel order. Device-confirmed: DXVK's scanout buffer is
                // BGRA_8888 (AHardwareBuffer format 5), so BGRA (default) presents as-is; RGBA swaps R/B
                // for the rare title whose buffer is actually RGBA-ordered (red/blue reversed on default).
                // Backed by the same `swapRB` boolean (BGRA=false, RGBA=true); RGBA (swap) routes the
                // container through the compositor (native scanout can't swap channels).
                val colorOrders = listOf("BGRA", "RGBA")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.renderer_colors),
                        options = colorOrders,
                        selectedOption = if (swapRB) "RGBA" else "BGRA",
                        onSelect = { swapRB = (it == "RGBA") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_renderer_colors }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val config = "native=$nativeRender;presentMode=$presentMode;driverId=$driverId;filterMode=$filterMode;swapRB=$swapRB"
                onConfirm(config)
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopLevelFields(
    tab: String,
    viewModel: ContainerDetailViewModel,
    onShowGfxConfig: () -> Unit,
    onShowDxvkConfig: () -> Unit,
    onShowWineD3DConfig: () -> Unit,
    onShowFpsConfig: () -> Unit,
    onShowVulkanConfig: () -> Unit,
    onShowWineDownloadSheet: () -> Unit,
) {
    val context = LocalContext.current
    // Per-field "?" help — a centered, scrollable Compose dialog (HelpDialog), replacing the old
    // top-left PopupWindow. null = no dialog; otherwise the string res of the field's help text.
    var helpRes by remember { mutableStateOf<Int?>(null) }
    // Same pattern as helpRes, but for help text held as a raw string (HelpTextDialog).
    var helpText by remember { mutableStateOf<String?>(null) }
    var showAudioSettings by remember { mutableStateOf(false) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }
    helpText?.let { HelpTextDialog(it) { helpText = null } }
    // Declared at function level: the wrapper manager dialog renders on the GRAPHICS tab, the
    // launcher for it lives there too.
    var showWrapperManager by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {

        // ── GRAPHICS tab (always 2nd): wrapper manager, DX Wrapper, Renderer, render scale ─────
        if (tab == "GRAPHICS") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { showWrapperManager = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.wrapper_manager_open))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (tab == "GENERAL") {
        // Name — per-container identity, hidden in "New Container Defaults" mode (not templatable).
        if (!viewModel.defaultsMode) {
            OutlinedTextField(
                value = viewModel.containerName,
                onValueChange = { viewModel.containerName = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }

        // Screen Size
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledDropdown(
                label = stringResource(R.string.screen_size),
                options = viewModel.screenSizeEntries,
                selectedOption = viewModel.selectedScreenSize,
                onSelect = { viewModel.selectedScreenSize = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { helpRes = R.string.help_screen_size }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
        }
        if (viewModel.selectedScreenSize.equals("custom", ignoreCase = true)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.customWidth,
                    onValueChange = { viewModel.customWidth = it },
                    label = { Text(stringResource(R.string.width)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.customHeight,
                    onValueChange = { viewModel.customHeight = it },
                    label = { Text(stringResource(R.string.height)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Wine Version (create/edit) OR Architecture selector (defaults mode). Defaults are stored
        // per-arch (box64/wowbox64/emulator/FEXCore are arch-coupled) and wine is NEVER templated, so
        // in defaults mode the arch selector replaces the wine version dropdown + its download gear and
        // drives the arch-dependent fields via setDefaultsArch (which reloads that arch's profile).
        if (viewModel.defaultsMode) {
            val archValues = listOf(
                com.winhub.hernandez.core.NewContainerDefaults.ARCH_X86_64,
                com.winhub.hernandez.core.NewContainerDefaults.ARCH_ARM64EC,
            )
            val archLabels = listOf("x86-64", "arm64ec")
            val archIdx = archValues.indexOf(viewModel.defaultsArch).coerceAtLeast(0)
            LabeledDropdown(
                label = "Architecture",
                options = archLabels,
                selectedOption = archLabels[archIdx],
                onSelect = { viewModel.selectDefaultsArch(archValues[archLabels.indexOf(it)]) }
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledDropdown(
                    label = stringResource(R.string.wine_version),
                    options = viewModel.wineVersionEntries,
                    selectedOption = viewModel.selectedWineVersion,
                    enabled = viewModel.wineVersionEnabled,
                    onSelect = { viewModel.onWineVersionChanged(it) },
                    modifier = Modifier.weight(1f)
                )
                ContentInstallGear(onDownloadFile = onShowWineDownloadSheet)
                IconButton(onClick = { helpRes = R.string.help_wine_version }) {
                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                }
            }
            // Sync (esync / ntsync / fsync / wineserver) belongs to the layer, so it sits right under
            // it. The view-model re-probes the layer on every change and falls a now-unavailable pick
            // back to the layer default (syncNotice says so). Not in defaults mode: no layer to judge.
            Spacer(Modifier.height(8.dp))
            com.winhub.hernandez.ui.components.SyncModeSelector(
                selected = viewModel.syncMode,
                caps = viewModel.syncCaps,
                helper = viewModel.syncNotice ?: com.winhub.hernandez.ui.components.containerSyncHelper(
                    viewModel.syncMode, viewModel.syncCaps, viewModel.selectedWineVersion),
                onPick = { viewModel.onSyncModePicked(it) },
                onHelp = { helpRes = R.string.help_sync_mode },
            )
        }
        Spacer(Modifier.height(8.dp))
        }
        if (tab == "GRAPHICS") {
        if (showWrapperManager) WrapperManagerDialog(onDismiss = {
            showWrapperManager = false
            viewModel.refreshGraphicsDriverEntries() // pick up a just-imported/deleted wrapper
        })
        Spacer(Modifier.height(8.dp))
        // Graphics Driver + config button.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledDropdown(
                label = stringResource(R.string.graphics_driver),
                options = viewModel.graphicsDriverEntries,
                selectedOption = viewModel.selectedGraphicsDriver,
                onSelect = { viewModel.selectedGraphicsDriver = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { helpRes = R.string.help_graphics_driver }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onShowGfxConfig) {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
        }

        // DX Wrapper + config button
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                LabeledDropdown(
                    label = stringResource(R.string.dxwrapper),
                    options = viewModel.dxWrapperEntries,
                    selectedOption = viewModel.selectedDXWrapper,
                    onSelect = { newWrapper ->
                        val wasVegas = StringUtils.parseIdentifier(viewModel.selectedDXWrapper ?: "").contains("vegas")
                        val isVegas = StringUtils.parseIdentifier(newWrapper).contains("vegas")
                        viewModel.selectedDXWrapper = newWrapper
                        // Strip dxvkConfigFile when leaving VEGAS — prevents stale
                        // VEGAS config path from leaking into plain DXVK+VKD3D.
                        if (wasVegas && !isVegas) {
                            val raw = viewModel.dxWrapperConfig
                            val cfg = DXVKConfigDialog.parseConfig(raw)
                            val path = cfg.get("dxvkConfigFile")
                            if (path.isNotEmpty()) {
                                val stripped = raw.split(",").filter { !it.startsWith("dxvkConfigFile=") }.joinToString(",")
                                viewModel.dxWrapperConfig = stripped
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { helpRes = R.string.dxwrapper_help_content }) {
                    Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = {
                val wrapper = StringUtils.parseIdentifier(viewModel.selectedDXWrapper ?: "")
                if (wrapper.contains("dxvk") || wrapper.contains("vegas")) onShowDxvkConfig() else onShowWineD3DConfig()
            }) {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Renderer — the X11-side compositor stage. Vulkan is the only host renderer.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledDropdown(
                label = stringResource(R.string.renderer),
                options = viewModel.rendererEntries,
                selectedOption = viewModel.selectedRenderer,
                onSelect = { viewModel.selectedRenderer = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { helpRes = R.string.help_renderer }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
            if (viewModel.selectedRenderer == "Vulkan") {
                IconButton(onClick = onShowVulkanConfig) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Render scale (supersampling) — pre-launch override stored via the "renderScale" extra.
        // The game renders at this multiple of the display res; the Vulkan compositor then does a
        // quality downscale. "1.0" = Off.
        run {
            val renderScaleValues = listOf("1.0", "1.25", "1.5", "2.0")
            val renderScaleLabels = listOf("Off", "1.25x", "1.5x", "2x")
            val rsIdx = renderScaleValues.indexOf(viewModel.renderScale).coerceAtLeast(0)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledDropdown(
                    label = "Render scale (supersampling)",
                    options = renderScaleLabels,
                    selectedOption = renderScaleLabels[rsIdx],
                    onSelect = { viewModel.renderScale = renderScaleValues[renderScaleLabels.indexOf(it)] },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    helpText = "How much the game renders above the display resolution; the compositor scales it back down (supersampling). \"Off\" renders at the native display resolution."
                }) {
                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                }
            }
        }

        // SurfaceDirect hands each window's buffer to SurfaceFlinger; these tune how it submits them.
        if (viewModel.selectedRenderer == "SurfaceDirect") {
            Spacer(Modifier.height(8.dp))
            HudToggleRow("SurfaceDirect: performance mode", viewModel.surfaceDirectPerformanceMode) { viewModel.surfaceDirectPerformanceMode = it }
            HudToggleRow("SurfaceDirect: present at refresh rate", viewModel.surfaceDirectPresentAtRefreshRate) { viewModel.surfaceDirectPresentAtRefreshRate = it }
            HudToggleRow("SurfaceDirect: submit every buffer", viewModel.surfaceDirectBackPressure) { viewModel.surfaceDirectBackPressure = it }
            HudToggleRow("SurfaceDirect: precise presentation", viewModel.surfaceDirectPrecisePresentation) { viewModel.surfaceDirectPrecisePresentation = it }
        }
        // OpenGL card (core.FastOpenGL): Fast / Show FPS / Vsync off as three compact switch rows under
        // one "?". Fast greys with one short reason when the driver isn't a Turnip or the APK has no
        // X11 GL set. Show FPS / Vsync off reach Mesa on GLX and EGL alike, so they are never greyed.
        run {
            Spacer(Modifier.height(8.dp))
            val fastGlDriverVersion = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
                .getVersion(viewModel.graphicsDriverConfig) ?: ""
            val fastGlDriverOk = remember(fastGlDriverVersion) {
                com.winhub.hernandez.core.FastOpenGL.driverUsable(context, fastGlDriverVersion)
            }
            val fastGlBundled = remember { com.winhub.hernandez.core.X11Egl.isBundled(context) }
            val fastGlReason = com.winhub.hernandez.core.FastOpenGL.unavailableReason(fastGlDriverOk, fastGlBundled)
            com.winhub.hernandez.ui.components.OpenGLCard(onHelp = { helpRes = R.string.help_opengl_settings }) {
                com.winhub.hernandez.ui.components.OpenGLCardRow(
                    com.winhub.hernandez.ui.components.OPENGL_FAST_ICON, "Fast",
                    com.winhub.hernandez.ui.components.openGLFastNote(fastGlReason)
                ) {
                    Switch(
                        enabled = fastGlReason == null,
                        checked = viewModel.fastOpenGL && fastGlReason == null,
                        onCheckedChange = { viewModel.fastOpenGL = it }
                    )
                }
                com.winhub.hernandez.ui.components.OpenGLCardRow(
                    com.winhub.hernandez.ui.components.OPENGL_FPS_ICON, "Show FPS", null
                ) {
                    Switch(checked = viewModel.glFpsHud, onCheckedChange = { viewModel.glFpsHud = it })
                }
                com.winhub.hernandez.ui.components.OpenGLCardRow(
                    com.winhub.hernandez.ui.components.OPENGL_VSYNC_ICON, "Vsync off", null
                ) {
                    Switch(checked = viewModel.glVsyncOff, onCheckedChange = { viewModel.glVsyncOff = it })
                }
            }
        }
        }  // end GRAPHICS block
        if (tab == "GENERAL") {
        Spacer(Modifier.height(8.dp))

        // Auto-close the session when the launched game exits (default ON). Avoids being left on the
        // empty Wine desktop (black screen) after quitting. Applies to game-shortcut launches.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = viewModel.autoCloseOnExit,
                onCheckedChange = { viewModel.autoCloseOnExit = it }
            )
            Spacer(Modifier.width(8.dp))
            Text("Close when game exits")
        }
        Spacer(Modifier.height(8.dp))

        // Audio Driver
        // DirectAudio only loads on the four arm64ec Proton builds its .drv is built for; off those
        // layers it does nothing / breaks audio. Grey the option out (keyed on the selected layer so it
        // re-evaluates when the Wine version changes) and never let it be picked there. The ViewModel
        // also coerces it back to the default on save / layer-change, so the two can't drift.
        val directAudioSupported = remember(viewModel.selectedWineVersion) {
            com.winhub.hernandez.core.DirectAudioSupport.isSupported(viewModel.selectedWineVersion)
        }
        val directAudioEntry = remember(viewModel.audioDriverEntries) {
            viewModel.audioDriverEntries.firstOrNull { StringUtils.parseIdentifier(it) == "directaudio" }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledDropdown(
                label = stringResource(R.string.audio_driver),
                options = viewModel.audioDriverEntries,
                selectedOption = viewModel.selectedAudioDriver,
                disabledOptions = if (!directAudioSupported && directAudioEntry != null) setOf(directAudioEntry) else emptySet(),
                onSelect = {
                    viewModel.selectedAudioDriver = it
                    // DirectAudio is experimental — warn on select (reuses the HelpDialog surface).
                    if (StringUtils.parseIdentifier(it) == "directaudio") helpRes = R.string.directaudio_experimental_warning
                },
                modifier = Modifier.weight(1f)
            )
            // Cog → adaptive audio presets & fine-tuning. Both engines honor the same presets/knobs
            // (PulseAudio sink + ALSA player), so it's shown for either driver.
            val audioId = StringUtils.parseIdentifier(viewModel.selectedAudioDriver)
            if (audioId == "pulseaudio" || audioId == "alsa" || audioId == "directaudio") {
                IconButton(onClick = { showAudioSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Audio settings", modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { helpRes = R.string.help_audio_driver }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
        }
        if (!directAudioSupported && directAudioEntry != null) {
            Text(
                "DirectAudio requires Proton ${com.winhub.hernandez.core.DirectAudioSupport.SUPPORTED_LABEL}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
        if (showAudioSettings) {
            AudioSettingsDialog(
                initial = audioConfigFromEnv(viewModel.envVarsStr, StringUtils.parseIdentifier(viewModel.selectedAudioDriver)),
                scopeLabel = "this container",
                latencyLive = true,
                driverLabel = when (StringUtils.parseIdentifier(viewModel.selectedAudioDriver)) {
                    "alsa" -> "ALSA"; "pulseaudio" -> "PulseAudio"; "directaudio" -> "DirectAudio"
                    else -> StringUtils.parseIdentifier(viewModel.selectedAudioDriver)
                },
                driverId = StringUtils.parseIdentifier(viewModel.selectedAudioDriver),
                onDismiss = { showAudioSettings = false },
                onSave = { cfg ->
                    viewModel.envVarsStr = audioConfigToEnv(viewModel.envVarsStr, cfg, StringUtils.parseIdentifier(viewModel.selectedAudioDriver))
                    showAudioSettings = false
                }
            )
        }
        Spacer(Modifier.height(8.dp))

        // Microphone (DirectAudio only). Opt-in per container, default OFF — a knowingly-granted
        // permission. ON seeds BANNER_AUDIO_DIRECT_MIC=1 into this scope's env; the DirectAudio driver
        // opens the AAudio INPUT stream itself when the flag is present (the app never records). Greyed
        // off DirectAudio, since only that driver consumes the flag today. Keyed on the live env
        // (viewModel.envVarsStr) so it can't capture a stale value and drift.
        run {
            val micCtx = LocalContext.current
            val micDriverActive = StringUtils.parseIdentifier(viewModel.selectedAudioDriver) == "directaudio" && directAudioSupported
            val micOn = com.winhub.hernandez.core.DirectAudioSupport.isMicEnabledInEnv(viewModel.envVarsStr)
            val micPermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    viewModel.envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(viewModel.envVarsStr, true)
                } else {
                    viewModel.envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(viewModel.envVarsStr, false)
                    Toast.makeText(micCtx, "Microphone permission denied — mic stays off.", Toast.LENGTH_SHORT).show()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    enabled = micDriverActive,
                    checked = micOn && micDriverActive,
                    onCheckedChange = { want ->
                        if (want) {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(micCtx, android.Manifest.permission.RECORD_AUDIO)
                                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                viewModel.envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(viewModel.envVarsStr, true)
                            } else {
                                micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            viewModel.envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(viewModel.envVarsStr, false)
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Microphone",
                        color = if (micDriverActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (micDriverActive) "Let games use the mic (DirectAudio captures input)"
                        else "Available on the DirectAudio driver",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Emulator (arm64ec only) — the non-FEX backend here is wowbox64.dll, not box64, so the
        // entry is relabelled for display; selectedEmulator keeps the canonical "Box64" value that
        // StringUtils.parseIdentifier() persists as "box64". See EmulatorLabels.
        if (viewModel.isArm64EC) {
            LabeledDropdown(
                label = "Emulator",
                options = EmulatorLabels.options(viewModel.emulatorEntries, true),
                selectedOption = EmulatorLabels.display(viewModel.selectedEmulator, true),
                enabled = viewModel.emulatorEnabled,
                onSelect = {
                    viewModel.selectedEmulator =
                        EmulatorLabels.fromDisplay(it, viewModel.emulatorEntries, true)
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        // MIDI Sound Font
        LabeledDropdown(
            label = stringResource(R.string.midi_sound_font),
            options = viewModel.midiEntries,
            selectedOption = viewModel.midiEntries.getOrElse(viewModel.selectedMidiIndex) { "" },
            onSelect = { opt -> viewModel.selectedMidiIndex = viewModel.midiEntries.indexOf(opt).coerceAtLeast(0) }
        )
        Spacer(Modifier.height(8.dp))

        // Show FPS + config — the HUD ticks on the X11 session.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = viewModel.showFPS,
                onCheckedChange = { viewModel.showFPS = it }
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.show_fps), modifier = Modifier.weight(1f))
            IconButton(onClick = onShowFpsConfig) {
                Icon(Icons.Default.Settings, contentDescription = null)
            }
        }

        // Fullscreen aspect-ratio mode (#71): Off (windowed letterbox) / Fit (letterbox) /
        // Stretch (fill) / Fill (crop) / Integer (pixel-perfect). Option index maps 1:1 to
        // Container.FULLSCREEN_OFF/FIT/STRETCH/FILL/INTEGER.
        val fullscreenModeLabels = listOf(
            stringResource(R.string.fullscreen_mode_off),
            stringResource(R.string.fullscreen_mode_fit),
            stringResource(R.string.fullscreen_mode_stretch),
            stringResource(R.string.fullscreen_mode_fill),
            stringResource(R.string.fullscreen_mode_integer)
        )
        // Applies to the X11 session
        val fsSelIdx = viewModel.fullscreenMode.coerceIn(0, fullscreenModeLabels.size - 1)
        LabeledDropdown(
            label = stringResource(R.string.fullscreen_mode),
            options = fullscreenModeLabels,
            selectedOption = fullscreenModeLabels[fsSelIdx],
            onSelect = { viewModel.fullscreenMode = fullscreenModeLabels.indexOf(it).coerceAtLeast(0) }
        )
        Spacer(Modifier.height(8.dp))

        // Screen alignment (#413): Center / Top / Bottom on square-ish foldables. TOP/BOTTOM confine the
        // game to half the panel (a handheld split) and give the other half to the on-screen controls.
        // Applies in EVERY fullscreen mode now — Fit/Fill/Stretch/Integer all scale within the game's half
        // — so it's always available. Option index maps 1:1 to Container.ALIGN_*.
        val screenAlignmentLabels = listOf(
            stringResource(R.string.screen_alignment_center),
            stringResource(R.string.screen_alignment_top),
            stringResource(R.string.screen_alignment_bottom)
        )
        val alignSelIdx = viewModel.screenAlignment.coerceIn(0, screenAlignmentLabels.size - 1)
        LabeledDropdown(
            label = stringResource(R.string.screen_alignment),
            options = screenAlignmentLabels,
            selectedOption = screenAlignmentLabels[alignSelIdx],
            onSelect = { viewModel.screenAlignment = screenAlignmentLabels.indexOf(it).coerceAtLeast(0) }
        )
        Spacer(Modifier.height(8.dp))

        // Frame Generation engine: Off / LSFG Native. LSFG is grayed out until a Lossless.dll is
        // imported (Settings). This is the ONLY per-container FG control; the multiplier & flow
        // scale are tuned live from the in-game side menu.
        val fgEngines = listOf("off", "lsfg-native")
        val fgEngineLabels = listOf(
            stringResource(R.string.frame_generation_off),
            stringResource(R.string.frame_generation_lsfg_native)
        )
        val lsfgDllAvailable = remember { java.io.File(context.filesDir, "lsfg-vk/Lossless.dll").isFile }
        val fgDisabledOpts = buildSet {
            // LSFG Native needs an imported Lossless.dll. Whether the DEVICE can run the chain
            // (Vulkan 1.3 + the three shader features + a storage-capable swapchain format) is
            // only knowable once a renderer is up, so it is reported in-game rather than guessed here.
            if (!lsfgDllAvailable) add(fgEngineLabels[1])
        }
        val fgSelectedEngine =
            if (viewModel.frameGenEngine == "bionic" || viewModel.frameGenEngine == "lsfg") "lsfg-native"
            else viewModel.frameGenEngine
        val fgSelIdx = fgEngines.indexOf(fgSelectedEngine).coerceAtLeast(0)
        // FG's present-mode/mailbox delivery only exists on the Vulkan host renderer, so gate the
        // whole dropdown on it (combined with the lsfg-DLL option gate).
        val fgVulkan = viewModel.vulkanSupported && viewModel.selectedRenderer == "Vulkan"
        val fgShown = fgEngineLabels[fgSelIdx]
        // Per-engine paragraphs used to print inline under this row (and under the model picker);
        // they now join the generic help behind the row's "?".
        val fgEngineHint = when (fgSelectedEngine) {
            "lsfg-native" -> stringResource(R.string.frame_generation_lsfg_native_hint)
            else -> null
        }
        val fgHelp = listOfNotNull(
            stringResource(R.string.help_frame_generation), fgEngineHint
        ).joinToString("\n\n")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledDropdown(
                label = stringResource(R.string.frame_generation),
                options = fgEngineLabels,
                selectedOption = fgShown,
                onSelect = { viewModel.frameGenEngine = fgEngines[fgEngineLabels.indexOf(it)] },
                enabled = fgVulkan,
                disabledOptions = fgDisabledOpts,
                modifier = (if (!fgVulkan) Modifier.alpha(0.5f) else Modifier).weight(1f)
            )
            IconButton(onClick = { helpText = fgHelp }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
        }
        if (!fgVulkan) {
            Text(
                text = stringResource(R.string.frame_generation_requires_vulkan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 2.dp, bottom = 4.dp)
            )
        }
        if (!lsfgDllAvailable) {
            Text(
                text = stringResource(R.string.frame_generation_lsfg_needs_dll),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 2.dp, bottom = 4.dp)
            )
        }
        if (viewModel.frameGenEngine == "lsfg-native") {
            // Experimental LSFG Native capture resolution, shown while
            // FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED is on. (The Vulkan 1.1 compat switch
            // lives below, outside this engine check: it also serves per-game overrides.)
            if (com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.fg_experimental_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                // Capture resolution: Panel / Game / the Screen Size list (+ a Custom height).
                val capPanel = stringResource(R.string.fg_capture_panel)
                val capGame  = stringResource(R.string.fg_capture_game)
                val capOptions = listOf(capPanel, capGame) + viewModel.screenSizeEntries
                val capSelected = when (viewModel.fgCaptureSelection) {
                    Container.FG_CAPTURE_PANEL -> capPanel
                    Container.FG_CAPTURE_GAME  -> capGame
                    else -> viewModel.fgCaptureSelection
                }
                val capHint = stringResource(R.string.fg_capture_resolution_hint)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.fg_capture_resolution),
                        options = capOptions,
                        selectedOption = capSelected,
                        onSelect = {
                            viewModel.fgCaptureSelection = when (it) {
                                capPanel -> Container.FG_CAPTURE_PANEL
                                capGame  -> Container.FG_CAPTURE_GAME
                                else     -> it
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpText = capHint }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                if (viewModel.fgCaptureSelection.equals("custom", ignoreCase = true)) {
                    // Only the height is used (the width follows the panel's aspect).
                    OutlinedTextField(
                        value = viewModel.fgCaptureCustomHeight,
                        onValueChange = { viewModel.fgCaptureCustomHeight = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.height)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        // Vulkan 1.1 compat (experimental, FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED). Shown
        // whatever this container's engine is: a per-game shortcut can override the engine to
        // LSFG Native, and the launch-time notice points here. Applied at device creation, only
        // in sessions that run LSFG Native, so no drawer control.
        if (com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED) {
            if (viewModel.frameGenEngine != "lsfg-native") {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.fg_experimental_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // X11: this compat mode is handed to the X11 Vulkan renderer (setLsfgVk11Compat).
            val lsfgVk11Hint = stringResource(R.string.lsfg_vk11_compat_hint)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.lsfgVk11Compat,
                    onCheckedChange = { viewModel.lsfgVk11Compat = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lsfg_vk11_compat), modifier = Modifier.weight(1f))
                IconButton(onClick = { helpText = lsfgVk11Hint }) {
                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                }
            }
        }
        if (viewModel.frameGenEngine == "lsfg") {
            Text(
                text = stringResource(R.string.frame_generation_lsfg_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 2.dp, bottom = 4.dp)
            )
            // lsfg-vk performance_mode: lower quality for higher FPS. Also live-toggleable in-game.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.lsfgPerformanceMode,
                    onCheckedChange = { viewModel.lsfgPerformanceMode = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fg_performance_mode), modifier = Modifier.weight(1f))
            }
            Text(
                text = stringResource(R.string.fg_performance_mode_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 2.dp, bottom = 4.dp)
            )
            // lsfg-vk auto-enable at launch: start frame gen live at the saved multiplier from launch.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.lsfgAutoEnable,
                    onCheckedChange = { viewModel.lsfgAutoEnable = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lsfg_auto_enable), modifier = Modifier.weight(1f))
            }
            Text(
                text = stringResource(R.string.lsfg_auto_enable_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 2.dp, bottom = 4.dp)
            )
        }

        // FPS Limiter (bionic-fg). This switch just loads the layer; the cap value is set live
        // from the in-game FPS menu. (Frame Generation also loads the layer, so this is only
        // needed if you want a cap without frame gen.)
        val fpsHelp = if (viewModel.fpsLimiterEnabled)
            stringResource(R.string.help_fps_limiter) + "\n\n" + stringResource(R.string.fps_limiter_ingame_hint)
        else stringResource(R.string.help_fps_limiter)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = viewModel.fpsLimiterEnabled,
                onCheckedChange = { viewModel.fpsLimiterEnabled = it }
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.fps_limiter), modifier = Modifier.weight(1f))
            IconButton(onClick = { helpText = fpsHelp }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
        }

        // ReShade multi-effect loadout (vkBasalt drop-in), per-container default. The per-game shortcut
        // editor has the same picker and overrides this. Only applies to DXVK/VKD3D (Vulkan) games.
        val reshadeWrapper = StringUtils.parseIdentifier(viewModel.selectedDXWrapper ?: "")
        val reshadeSupported = reshadeWrapper.contains("dxvk") || reshadeWrapper.contains("vegas")
        ReshadeLoadoutEditor(
            state = viewModel.reshadeLoadout,
            effects = viewModel.reshadeEffects,
            supported = reshadeSupported,
            onCatalogChanged = { viewModel.rescanReshadeEffects() },
        )

        // LC_ALL
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = viewModel.lcAll,
                onValueChange = { viewModel.lcAll = it },
                label = { Text("LC_ALL") },
                modifier = Modifier.weight(1f)
            )
            var showLcMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showLcMenu = true }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
            }
            DropdownMenu(expanded = showLcMenu, onDismissRequest = { showLcMenu = false }) {
                viewModel.lcAllEntries.forEach { lc ->
                    DropdownMenuItem(
                        text = { Text("$lc.UTF-8") },
                        onClick = { viewModel.lcAll = "$lc.UTF-8"; showLcMenu = false }
                    )
                }
            }
        }
        } // end GENERAL (tail) block
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WineConfigTab(
    viewModel: ContainerDetailViewModel,
    colorPickerViewRef: MutableState<ColorPickerView?>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Desktop section
        SectionBox(title = "Desktop") {
            LabeledDropdown(
                label = stringResource(R.string.theme),
                options = listOf("Light", "Dark"),
                selectedOption = listOf("Light", "Dark").getOrElse(viewModel.desktopThemeIndex) { "Light" },
                onSelect = { opt -> viewModel.desktopThemeIndex = listOf("Light", "Dark").indexOf(opt).coerceAtLeast(0) }
            )
            Spacer(Modifier.height(8.dp))
            LabeledDropdown(
                label = stringResource(R.string.background),
                options = listOf("Image", "Solid Color"),
                selectedOption = listOf("Image", "Solid Color").getOrElse(viewModel.desktopBgTypeIndex) { "Image" },
                onSelect = { opt -> viewModel.desktopBgTypeIndex = listOf("Image", "Solid Color").indexOf(opt).coerceAtLeast(0) }
            )
            // Color picker (visible when Solid Color selected)
            if (viewModel.desktopBgTypeIndex == WineThemeManager.BackgroundType.COLOR.ordinal) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Background Color", modifier = Modifier.weight(1f))
                    AndroidView(
                        factory = { ctx ->
                            ColorPickerView(ctx).also { cpv ->
                                cpv.setColor(viewModel.desktopBgColorInt)
                                colorPickerViewRef.value = cpv
                            }
                        },
                        update = { cpv -> cpv.setColor(viewModel.desktopBgColorInt) },
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            // Wallpaper picker (visible when Image selected). The picked image is written to a
            // GLOBAL user-wallpaper.png (shared by all containers) or a per-container
            // user-wallpaper-<id>.png depending on the scope selector. On Save
            // buildDesktopThemeStr() emits the scope + the chosen file's mtime, so switching scope
            // or overwriting the file regenerates this container's Wine wallpaper.
            if (viewModel.desktopBgTypeIndex == WineThemeManager.BackgroundType.IMAGE.ordinal) {
                val context = LocalContext.current
                val ioScope = rememberCoroutineScope()
                val scopeOptions = listOf("All containers", "This container")
                val currentScope = WineThemeManager.BackgroundScope.values()
                    .getOrElse(viewModel.desktopWallpaperScopeIndex) { WineThemeManager.BackgroundScope.GLOBAL }
                // File depends on the selected scope; getNextContainerId() is O(1) so recomputing
                // per recomposition is cheap.
                val wallpaperFile = viewModel.wallpaperFileFor(currentScope)
                // Both the file path AND the mtime can change (scope switch / new pick), so drive
                // preview reload off (scopeIndex, stamp). Stamp is bumped on the main thread after
                // a successful save.
                var wallpaperStamp by remember { mutableStateOf(0L) }
                var preview by remember { mutableStateOf<ImageBitmap?>(null) }

                LaunchedEffect(viewModel.desktopWallpaperScopeIndex, wallpaperStamp) {
                    preview = if (wallpaperFile.isFile) {
                        withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(wallpaperFile.path)?.asImageBitmap()
                        }
                    } else null
                }

                fun applyWallpaperFromUri(uri: Uri) {
                    ioScope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val bitmap = ImageUtils.getBitmapFromUri(context, uri, 1280)
                                ?: return@withContext false
                            wallpaperFile.parentFile?.mkdirs()
                            ImageUtils.save(bitmap, wallpaperFile, Bitmap.CompressFormat.PNG, 100)
                        }
                        if (ok) wallpaperStamp = wallpaperFile.lastModified()
                    }
                }

                // System SAF picker (secondary).
                val pickWallpaperLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri -> uri?.let { applyWallpaperFromUri(it) } }

                // Built-in in-app image picker (primary).
                val pickWallpaperInAppLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        InAppFilePicker.pickedUri(result.data)?.let { applyWallpaperFromUri(it) }
                    }
                }
                var showWallpaperMenu by remember { mutableStateOf(false) }

                Spacer(Modifier.height(8.dp))
                LabeledDropdown(
                    label = "Apply wallpaper to",
                    options = scopeOptions,
                    selectedOption = scopeOptions.getOrElse(viewModel.desktopWallpaperScopeIndex) { scopeOptions[0] },
                    onSelect = { opt -> viewModel.desktopWallpaperScopeIndex = scopeOptions.indexOf(opt).coerceAtLeast(0) }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wallpaper Image", modifier = Modifier.weight(1f))
                    preview?.let { img ->
                        Image(
                            bitmap = img,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Box {
                        OutlinedButton(onClick = { showWallpaperMenu = true }) {
                            Text(if (preview != null) "Change" else "Browse")
                        }
                        DropdownMenu(expanded = showWallpaperMenu, onDismissRequest = { showWallpaperMenu = false }) {
                            DropdownMenuItem(text = { Text("Browse files") }, onClick = {
                                showWallpaperMenu = false
                                pickWallpaperInAppLauncher.launch(
                                    InAppFilePicker.buildIntent(context, InAppFilePicker.IMAGES, "Select wallpaper")
                                )
                            })
                            DropdownMenuItem(text = { Text("Pick via system…") }, onClick = {
                                showWallpaperMenu = false
                                pickWallpaperLauncher.launch("image/*")
                            })
                        }
                    }
                }
            }
        }

        // DirectInput section
        SectionBox(title = "DirectInput") {
            // Mouse warp (DirectInput's SetCursorPos re-centring).
            LabeledDropdown(
                label = stringResource(R.string.mouse_warp_override),
                options = viewModel.mouseWarpEntries,
                selectedOption = viewModel.mouseWarpEntries.getOrElse(viewModel.selectedMouseWarpIndex) { "" },
                onSelect = { opt -> viewModel.selectedMouseWarpIndex = viewModel.mouseWarpEntries.indexOf(opt).coerceAtLeast(0) }
            )
        }

        // System section — "Run as administrator" (default ON) toggles UAC in the prefix. Backed by
        // EnableLUA in .wine/system.reg (source of truth): the VM reads it on load and writes it on
        // save/create (mirrors the DirectInput mouse-warp registry idiom above).
        SectionBox(title = "System") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Run as administrator")
                    Text(
                        "Disables UAC so everything runs elevated (helps installers/tools that require admin)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = viewModel.runAsAdmin,
                    onCheckedChange = { viewModel.runAsAdmin = it }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WinComponentsTab(viewModel: ContainerDetailViewModel) {
    val directxItems by remember {
        derivedStateOf { viewModel.winComponents.filter { it.key.startsWith("direct") } }
    }
    val generalItems by remember {
        derivedStateOf { viewModel.winComponents.filterNot { it.key.startsWith("direct") } }
    }
    var showComponentsSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Components installer (only for an existing container with a Wine prefix).
        if (viewModel.container != null) {
            SectionBox(title = "Components") {
                Text(
                    "Install Wine dependencies — mono, gecko, .NET, VC++ runtimes, DirectX libraries, fonts and more.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showComponentsSheet = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Browse & install components")
                }
            }
        }
        if (directxItems.isNotEmpty()) {
            SectionBox(title = "DirectX") {
                directxItems.forEach { comp ->
                    WinComponentRow(comp) { idx ->
                        val i = viewModel.winComponents.indexOfFirst { it.key == comp.key }
                        if (i >= 0) viewModel.winComponents[i] = viewModel.winComponents[i].copy(selectedIndex = idx)
                    }
                }
            }
        }
        if (generalItems.isNotEmpty()) {
            SectionBox(title = "General") {
                generalItems.forEach { comp ->
                    WinComponentRow(comp) { idx ->
                        val i = viewModel.winComponents.indexOfFirst { it.key == comp.key }
                        if (i >= 0) viewModel.winComponents[i] = viewModel.winComponents[i].copy(selectedIndex = idx)
                    }
                }
            }
        }
    }

    if (showComponentsSheet) {
        viewModel.container?.let { container ->
            ComponentsSheet(container = container, onDismiss = { showComponentsSheet = false })
        }
    }
}

@Composable
private fun WinComponentRow(comp: WinComponentEntry, onSelect: (Int) -> Unit) {
    val options = listOf("Builtin (Wine)", "Native (Windows)")
    LabeledDropdown(
        label = comp.label,
        options = options,
        selectedOption = options.getOrElse(comp.selectedIndex) { options[0] },
        onSelect = { opt -> onSelect(options.indexOf(opt).coerceAtLeast(0)) }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EnvVarsTab(viewModel: ContainerDetailViewModel) {
    // The editor writes straight through to the ViewModel on every edit, so a tab switch
    // can't drop in-progress edits and nothing has to be flushed back on dispose.
    // gameDir is null here: a container has no single game folder to scan.
    EnvVarsEditor(
        value = viewModel.envVarsStr,
        onValueChange = { viewModel.envVarsStr = it },
        modifier = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DrivesTab(viewModel: ContainerDetailViewModel) {
    val context = LocalContext.current
    var pendingDriveUid by remember { mutableStateOf<Long?>(null) }

    // Uses the built-in picker rather than SAF: it returns a real absolute path, so a folder on the
    // SD card yields /storage/<uuid>/... The SAF mapping produced /mnt/media_rw/<uuid>/..., the raw
    // vold mount, which neither the app nor the container can read.
    val dirPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val path = InAppFilePicker.pickedPath(result.data)
            val uid = pendingDriveUid
            if (path != null && uid != null) viewModel.updateDrivePath(uid, path)
        }
        pendingDriveUid = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (viewModel.drives.isEmpty()) {
            Text(
                stringResource(R.string.no_items_to_display),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val duplicateLetters = viewModel.duplicateDriveLetters
        viewModel.drives.forEach { drive ->
            DriveRow(
                drive = drive,
                letterOptions = viewModel.driveLetterOptions,
                isDuplicate = drive.letter in duplicateLetters,
                onLetterChange = { viewModel.updateDriveLetter(drive.uid, it) },
                onPathChange   = { viewModel.updateDrivePath(drive.uid, it)   },
                onBrowse = {
                    pendingDriveUid = drive.uid
                    dirPickerLauncher.launch(
                        InAppFilePicker.buildDirIntent(
                            context,
                            title = "Select folder for drive ${drive.letter}:",
                            initialDir = drive.path.takeIf { it.isNotBlank() && File(it).isDirectory },
                        )
                    )
                },
                onRemove = { viewModel.removeDrive(drive.uid) }
            )
        }
        if (duplicateLetters.isNotEmpty()) {
            Text(
                "Each drive needs its own letter. Duplicated: " +
                    duplicateLetters.sorted().joinToString(", ") { "$it:" },
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
        Button(
            onClick = { viewModel.addDrive() },
            enabled = viewModel.drives.size < Container.MAX_DRIVE_LETTERS,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.add) + " " + stringResource(R.string.drives))
        }
    }
}

@Composable
private fun DriveRow(
    drive: DriveEntry,
    letterOptions: List<String>,
    isDuplicate: Boolean,
    onLetterChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactDropdown(
            options = letterOptions,
            selectedOption = "${drive.letter}:",
            onSelect = { onLetterChange(it.trimEnd(':')) },
            modifier = Modifier
                .width(64.dp)
                .then(
                    if (isDuplicate) Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        RoundedCornerShape(4.dp),
                    ) else Modifier
                )
        )
        OutlinedTextField(
            value = drive.path,
            onValueChange = onPathChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("Path") }
        )
        IconButton(onClick = onBrowse) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = null)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AdvancedTab(
    viewModel: ContainerDetailViewModel,
    cpuListViewRef: MutableState<CPUListView?>,
    cpuListWoW64Ref: MutableState<CPUListView?>,
    onShowBox64DownloadSheet: () -> Unit = {},
    onShowFexCoreDownloadSheet: () -> Unit = {},
) {
    val context = LocalContext.current
    // Per-field "?" help — centered scrollable Compose dialog (same as the General tab).
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }

    // Bumped whenever a preset's values or the preset list change, so the "customised" badges
    // re-evaluate. The badge state lives on the Container object rather than in Compose state, so
    // there is nothing for Compose to observe on its own.
    var presetRevision by remember { mutableIntStateOf(0) }
    val box64PresetCustomised = remember(
        presetRevision, viewModel.selectedBox64PresetIndex, viewModel.box64PresetEntries
    ) {
        PresetOverrides.isCustomised(
            context, false, viewModel.selectedBox64PresetId,
            PresetScope.CONTAINER, viewModel.container, null
        )
    }
    val fexPresetCustomised = remember(
        presetRevision, viewModel.selectedFEXCorePresetIndex, viewModel.fexCorePresetEntries
    ) {
        PresetOverrides.isCustomised(
            context, true, viewModel.selectedFEXCorePresetId,
            PresetScope.CONTAINER, viewModel.container, null
        )
    }
    // Flush legacy CPUListView selections back to the ViewModel before the tab
    // leaves composition, so a tab switch doesn't drop in-progress edits.
    DisposableEffect(Unit) {
        onDispose {
            cpuListViewRef.value?.let { viewModel.cpuList = it.checkedCPUListAsString }
            cpuListWoW64Ref.value?.let { viewModel.cpuListWoW64 = it.checkedCPUListAsString }
            cpuListViewRef.value = null
            cpuListWoW64Ref.value = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Box64 / WOWBox64 section — arm64ec wrappers use WOWBox64, everything else Box64.
        val emulatorLabel = if (viewModel.isArm64EC) "WOWBox64" else "Box64"
        SectionBox(title = emulatorLabel) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledDropdown(
                    label = "$emulatorLabel Version",
                    options = viewModel.box64VersionEntries,
                    selectedOption = viewModel.selectedBox64Version,
                    onSelect = { viewModel.selectedBox64Version = it },
                    modifier = Modifier.weight(1f)
                )
                ContentInstallGear(onDownloadFile = onShowBox64DownloadSheet)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledDropdown(
                    label = "$emulatorLabel Preset",
                    options = viewModel.box64PresetEntries,
                    selectedOption = viewModel.box64PresetEntries.getOrElse(viewModel.selectedBox64PresetIndex) { "" },
                    onSelect = { opt -> viewModel.selectedBox64PresetIndex = viewModel.box64PresetEntries.indexOf(opt).coerceAtLeast(0) },
                    modifier = Modifier.weight(1f)
                )
                if (box64PresetCustomised) PresetCustomBadge()
            }
            PresetEditorRow(
                kind = PresetKind.BOX64,
                selectedPresetId = viewModel.selectedBox64PresetId,
                scope = PresetScope.CONTAINER,
                container = viewModel.container,
                shortcut = null,
                onSelect = { viewModel.selectBox64PresetById(it) },
                onListChanged = { viewModel.reloadPresetLists(context); presetRevision++ },
                onValuesChanged = { presetRevision++ },
            )
        }

        // FEXCore section (arm64ec only)
        if (viewModel.isArm64EC) {
            SectionBox(title = "FEXCore") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabeledDropdown(
                        label = stringResource(R.string.fexcore_version),
                        options = viewModel.fexCoreVersionEntries,
                        selectedOption = viewModel.selectedFEXCoreVersion,
                        onSelect = { viewModel.selectedFEXCoreVersion = it },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_fexcore_version }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                    ContentInstallGear(onDownloadFile = onShowFexCoreDownloadSheet)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.fexcore_preset),
                        options = viewModel.fexCorePresetEntries,
                        selectedOption = viewModel.fexCorePresetEntries.getOrElse(viewModel.selectedFEXCorePresetIndex) { "" },
                        onSelect = { opt -> viewModel.selectedFEXCorePresetIndex = viewModel.fexCorePresetEntries.indexOf(opt).coerceAtLeast(0) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_fexcore_preset }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                    if (fexPresetCustomised) PresetCustomBadge()
                }
                // Edits made here belong to THIS container: they are stored on it and its games
                // follow them, while the shared preset and every other container stay as they were.
                PresetEditorRow(
                    kind = PresetKind.FEXCORE,
                    selectedPresetId = viewModel.selectedFEXCorePresetId,
                    scope = PresetScope.CONTAINER,
                    container = viewModel.container,
                    shortcut = null,
                    onSelect = { viewModel.selectFEXCorePresetById(it) },
                    onListChanged = { viewModel.reloadPresetLists(context); presetRevision++ },
                    onValuesChanged = { presetRevision++ },
                )
            }
        }

        // Game Controller section
        SectionBox(title = stringResource(R.string.game_controller)) {
            // "DirectInput games can see the controller" replaced Enable XInput / Enable DInput /
            // Exclusive Input (core.DInputVisibility maps the old values).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.dinputVisible,
                    onCheckedChange = { viewModel.dinputVisible = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dinput_visible), modifier = Modifier.weight(1f))
                IconButton(onClick = { helpRes = R.string.help_dinput_visible }) {
                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            ControllerSettingsRows(
                hermesXInput = viewModel.hermesXInput,
                onHermesXInput = { viewModel.hermesXInput = it },
                switching = viewModel.hermesXInputSwitching,
                onSwitching = { viewModel.hermesXInputSwitching = it },
                identity = viewModel.controllerIdentity,
                onIdentity = { viewModel.controllerIdentity = it },
                dinputVibration = viewModel.dinputVibration,
                onDinputVibration = { viewModel.dinputVibration = it },
                pacing = viewModel.stickPacing,
                onPacing = { viewModel.stickPacing = it },
                onHelp = { helpRes = it },
            )
        }

        // Vibration section — per-container rumble target + intensity default. Also live-tunable
        // in-game from the drawer (this is just the value a new session launches with).
        SectionBox(title = stringResource(R.string.vibration)) {
            val vibrationModeLabels = listOf(
                stringResource(R.string.vibration_mode_off),
                stringResource(R.string.vibration_mode_controller),
                stringResource(R.string.vibration_mode_device),
                stringResource(R.string.vibration_mode_both),
            )
            LabeledDropdown(
                label = stringResource(R.string.vibration_mode_label),
                options = vibrationModeLabels,
                selectedOption = vibrationModeLabels.getOrElse(viewModel.vibrationMode) {
                    vibrationModeLabels[Container.VIBRATION_MODE_DEFAULT]
                },
                onSelect = { opt -> viewModel.vibrationMode = vibrationModeLabels.indexOf(opt).coerceAtLeast(0) }
            )
            if (viewModel.vibrationMode != Container.VIBRATION_MODE_OFF) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${stringResource(R.string.vibration_intensity_label)}: ${viewModel.vibrationIntensity}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = viewModel.vibrationIntensity.toFloat(),
                    onValueChange = { viewModel.vibrationIntensity = it.toInt() },
                    valueRange = 0f..100f, steps = 99
                )
            }
        }

        // Player Slots section — manual controller->player-slot pins that the launch pre-assignment
        // applies. Same JSON schema and editor the in-game "Players" drawer tab uses, but here it's a
        // saved default (applied on next launch), not a live reassignment.
        SectionBox(title = "Player Slots") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Pin controllers to players; assign two to one player to share control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { helpRes = R.string.help_player_slots }) {
                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                }
            }
            // On-screen priority: what happens to the on-screen pad when a physical pad connects mid-game.
            val onScreenModeLabels = listOf("Keep on-screen player", "Yield Player 1 to pad", "Share the player")
            LabeledDropdown(
                label = "On-screen priority",
                options = onScreenModeLabels,
                selectedOption = onScreenModeLabels.getOrElse(viewModel.onScreenControllerMode) { onScreenModeLabels[0] },
                onSelect = { viewModel.onScreenControllerMode = onScreenModeLabels.indexOf(it).coerceAtLeast(0) },
            )
            Spacer(Modifier.height(8.dp))
            // #333: auto-hide the on-screen touch controls when a physical controller takes over the
            // on-screen pad's player slot; they reappear when it leaves. Slot-aware — a controller pinned
            // to a DIFFERENT player (2-player setup) leaves the overlay up. Overrides Share while active.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.autoHideControlsOnPad,
                    onCheckedChange = { viewModel.autoHideControlsOnPad = it }
                )
                Spacer(Modifier.width(8.dp))
                Text("Hide on-screen controls when a controller connects")
            }
            Spacer(Modifier.height(8.dp))
            PlayerSlotsEditor(
                savedOverridesJson = viewModel.controllerSlotOverridesJson,
                onOverridesChange = { viewModel.controllerSlotOverridesJson = it },
            )
        }

        // Gyro (motion aim) section — the default a session (and a new shortcut) launches with.
        // Enabled/target/activator/sensitivity/invert can be overridden per game in the shortcut
        // editor; deadzone/smoothing stay container-wide because they track the hand and the device,
        // not the game. All of them are also live-tunable from the in-game drawer.
        SectionBox(title = stringResource(R.string.gyro)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.gyroEnabled,
                    onCheckedChange = { viewModel.gyroEnabled = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.gyro_enabled), modifier = Modifier.weight(1f))
            }
            if (viewModel.gyroEnabled) {
                // Rate = tilt SPEED drives the target and it recentres when you stop; Tilt to Aim =
                // the ANGLE held drives it, so a held tilt sustains. Mutually exclusive with the Mouse
                // target: the mouse path emits relative deltas, so a held tilt would be a constant
                // delta and the pointer would run to a screen edge and stay there. Each selection
                // therefore knocks the other back to a working value (WinHandler enforces the same
                // rule at launch, so a hand-edited container JSON can't reach the broken pair either).
                val gyroModeLabels = listOf(
                    stringResource(R.string.gyro_mode_rate),
                    stringResource(R.string.gyro_mode_orientation),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.gyro_mode_label),
                        options = gyroModeLabels,
                        selectedOption = gyroModeLabels.getOrElse(viewModel.gyroMode) {
                            gyroModeLabels[Container.GYRO_MODE_DEFAULT]
                        },
                        onSelect = { opt ->
                            val mode = gyroModeLabels.indexOf(opt).coerceAtLeast(0)
                            viewModel.gyroMode = mode
                            if (mode == Container.GYRO_MODE_ORIENTATION && viewModel.gyroTarget == Container.GYRO_TARGET_MOUSE)
                                viewModel.gyroTarget = Container.GYRO_TARGET_DEFAULT
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_gyro_mode }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val gyroTargetLabels = listOf(
                    stringResource(R.string.gyro_target_right_stick),
                    stringResource(R.string.gyro_target_left_stick),
                    stringResource(R.string.gyro_target_mouse),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.gyro_target_label),
                        options = gyroTargetLabels,
                        selectedOption = gyroTargetLabels.getOrElse(viewModel.gyroTarget) {
                            gyroTargetLabels[Container.GYRO_TARGET_DEFAULT]
                        },
                        onSelect = { opt ->
                            val target = gyroTargetLabels.indexOf(opt).coerceAtLeast(0)
                            viewModel.gyroTarget = target
                            if (target == Container.GYRO_TARGET_MOUSE)
                                viewModel.gyroMode = Container.GYRO_MODE_RATE
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_gyro_target }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                val gyroActivatorLabels = listOf(
                    stringResource(R.string.gyro_activator_l1),
                    stringResource(R.string.gyro_activator_l2),
                    stringResource(R.string.gyro_activator_r1),
                    stringResource(R.string.gyro_activator_r3),
                    stringResource(R.string.gyro_activator_always),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.gyro_activator_label),
                        options = gyroActivatorLabels,
                        selectedOption = gyroActivatorLabels.getOrElse(viewModel.gyroActivator) {
                            gyroActivatorLabels[Container.GYRO_ACTIVATOR_DEFAULT]
                        },
                        onSelect = { opt -> viewModel.gyroActivator = gyroActivatorLabels.indexOf(opt).coerceAtLeast(0) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_gyro_activator }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                // Hold vs Toggle for that button. Pointless with "Always On" (no button to latch), so
                // the picker is hidden rather than greyed here — an editor row has no live state to
                // explain, unlike the in-game drawer where the row stays visible but disabled.
                if (viewModel.gyroActivator != Container.GYRO_ACTIVATOR_ALWAYS) {
                    val gyroActivationModeLabels = listOf(
                        stringResource(R.string.gyro_activation_hold),
                        stringResource(R.string.gyro_activation_toggle),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabeledDropdown(
                            label = stringResource(R.string.gyro_activation_mode_label),
                            options = gyroActivationModeLabels,
                            selectedOption = gyroActivationModeLabels.getOrElse(viewModel.gyroActivationMode) {
                                gyroActivationModeLabels[Container.GYRO_ACTIVATION_MODE_DEFAULT]
                            },
                            onSelect = { opt -> viewModel.gyroActivationMode = gyroActivationModeLabels.indexOf(opt).coerceAtLeast(0) },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { helpRes = R.string.help_gyro_activation_mode }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stringResource(R.string.gyro_sensitivity_label)}: ${"%.1f".format(viewModel.gyroSensitivity)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_gyro_sensitivity }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Slider(
                    value = viewModel.gyroSensitivity,
                    onValueChange = { viewModel.gyroSensitivity = it },
                    valueRange = 0.1f..10f
                )
                Text(
                    "${stringResource(R.string.gyro_deadzone_label)}: ${"%.2f".format(viewModel.gyroDeadzone)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = viewModel.gyroDeadzone,
                    onValueChange = { viewModel.gyroDeadzone = it },
                    valueRange = 0f..0.5f
                )
                Text(
                    "${stringResource(R.string.gyro_smoothing_label)}: ${"%.2f".format(viewModel.gyroSmoothing)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = viewModel.gyroSmoothing,
                    onValueChange = { viewModel.gyroSmoothing = it },
                    valueRange = 0f..0.95f
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = viewModel.gyroInvertX,
                        onCheckedChange = { viewModel.gyroInvertX = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gyro_invert_x), modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = viewModel.gyroInvertY,
                        onCheckedChange = { viewModel.gyroInvertY = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gyro_invert_y), modifier = Modifier.weight(1f))
                }
            }
        }

        // Physical stick tuning — what a session launches with unless the shortcut overrides it.
        SectionBox(title = stringResource(R.string.stick_tuning_title)) {
            Text(
                stringResource(R.string.stick_tuning_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${stringResource(R.string.stick_left_deadzone)}: ${stickDeadzoneText(viewModel.stickLeftDeadzone)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = viewModel.stickLeftDeadzone,
                onValueChange = { viewModel.stickLeftDeadzone = it },
                valueRange = STICK_DEADZONE_RANGE,
                steps = STICK_DEADZONE_STEPS
            )
            Text(
                "${stringResource(R.string.stick_left_sensitivity)}: ${stickSensitivityText(viewModel.stickLeftSensitivity)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = viewModel.stickLeftSensitivity,
                onValueChange = { viewModel.stickLeftSensitivity = it },
                valueRange = STICK_SENSITIVITY_RANGE,
                steps = STICK_SENSITIVITY_STEPS
            )
            Text(
                "${stringResource(R.string.stick_right_deadzone)}: ${stickDeadzoneText(viewModel.stickRightDeadzone)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = viewModel.stickRightDeadzone,
                onValueChange = { viewModel.stickRightDeadzone = it },
                valueRange = STICK_DEADZONE_RANGE,
                steps = STICK_DEADZONE_STEPS
            )
            Text(
                "${stringResource(R.string.stick_right_sensitivity)}: ${stickSensitivityText(viewModel.stickRightSensitivity)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = viewModel.stickRightSensitivity,
                onValueChange = { viewModel.stickRightSensitivity = it },
                valueRange = STICK_SENSITIVITY_RANGE,
                steps = STICK_SENSITIVITY_STEPS
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = viewModel.stickSmoothEdge,
                    onCheckedChange = { viewModel.stickSmoothEdge = it }
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.stick_smooth_edge), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.stick_smooth_edge_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = {
                viewModel.stickLeftDeadzone = StickShaper.DEADZONE_DEFAULT
                viewModel.stickLeftSensitivity = StickShaper.SENSITIVITY_DEFAULT
                viewModel.stickRightDeadzone = StickShaper.DEADZONE_DEFAULT
                viewModel.stickRightSensitivity = StickShaper.SENSITIVITY_DEFAULT
                viewModel.stickSmoothEdge = StickShaper.SMOOTH_EDGE_DEFAULT
            }) {
                Text(stringResource(R.string.stick_tuning_reset))
            }
        }

        // Startup Selection
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledDropdown(
                label = stringResource(R.string.startup_selection),
                options = viewModel.startupSelectionEntries,
                selectedOption = viewModel.startupSelectionEntries.getOrElse(viewModel.selectedStartupSelection) { "" },
                onSelect = { opt -> viewModel.selectedStartupSelection = viewModel.startupSelectionEntries.indexOf(opt).coerceAtLeast(0) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { helpRes = R.string.help_startup_selection }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
        }

        // Custom per-service toggles — only shown when "Custom" (index 3) is selected.
        if (viewModel.selectedStartupSelection == Container.STARTUP_SELECTION_CUSTOM.toInt()) {
            StartupServicesToggleList(
                enabled = viewModel.startupServicesEnabled,
                onToggle = { raw, on ->
                    viewModel.startupServicesEnabled =
                        if (on) viewModel.startupServicesEnabled + raw
                        else viewModel.startupServicesEnabled - raw
                }
            )
        }

        // Processor Affinity
        SectionBox(title = stringResource(R.string.processor_affinity)) {
            Text(
                stringResource(R.string.processor_affinity),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            AndroidView(
                factory = { ctx ->
                    CPUListView(ContextThemeWrapper(ctx, R.style.AppTheme_Dark)).also { cpv ->
                        cpv.setCheckedCPUList(viewModel.cpuList)
                        cpuListViewRef.value = cpv
                    }
                },
                update = {},
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            )
            if (viewModel.isArm64EC) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.processor_affinity_32_bit_apps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                AndroidView(
                    factory = { ctx ->
                        CPUListView(ContextThemeWrapper(ctx, R.style.AppTheme_Dark)).also { cpv ->
                            cpv.setCheckedCPUList(viewModel.cpuListWoW64)
                            cpuListWoW64Ref.value = cpv
                        }
                    },
                    update = {},
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun XRTab(viewModel: ContainerDetailViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Primary controller
        LabeledDropdown(
            label = stringResource(R.string.primary_controller),
            options = viewModel.primaryControllerEntries,
            selectedOption = viewModel.primaryControllerEntries.getOrElse(viewModel.selectedPrimaryController) { "" },
            onSelect = { opt -> viewModel.selectedPrimaryController = viewModel.primaryControllerEntries.indexOf(opt).coerceAtLeast(0) }
        )

        // Controller button mappings
        SectionBox(title = "Controller Mapping") {
            viewModel.xrMappingLabels.forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(label, modifier = Modifier.weight(1f))
                    CompactDropdown(
                        options = viewModel.xrKeycodeNames,
                        selectedOption = viewModel.xrKeycodeNames.getOrElse(viewModel.xrMappingIndices.getOrElse(i) { 0 }) { "" },
                        onSelect = { opt ->
                            val idx = viewModel.xrKeycodeNames.indexOf(opt).coerceAtLeast(0)
                            if (i < viewModel.xrMappingIndices.size) viewModel.xrMappingIndices[i] = idx
                        },
                        modifier = Modifier.width(160.dp)
                    )
                }
                if (i < viewModel.xrMappingLabels.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────────────────────

// Per-service on/off list for the "Custom" startup selection. Shared by the container editor and the
// shortcut Advanced tab (both are in this package), so the service list, labels and ordering come
// from the single WineUtils source of truth and can't drift between the two screens.
@Composable
internal fun StartupServicesToggleList(
    enabled: Set<String>,
    onToggle: (rawName: String, on: Boolean) -> Unit
) {
    SectionBox(title = "Custom Services") {
        Text(
            "Custom starts with every service off — turn on only what you need. " +
                "Disabling Wine Bus/HID can break controllers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        WineUtils.STARTUP_SERVICES.forEachIndexed { i, entry ->
            val raw = WineUtils.startupServiceRawName(entry)
            val label = WineUtils.STARTUP_SERVICE_LABELS.getOrElse(i) { raw }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("$label ($raw)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled.contains(raw), onCheckedChange = { onToggle(raw, it) })
            }
            if (i < WineUtils.STARTUP_SERVICES.lastIndex) Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun SectionBox(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), content = content)
        }
    }
}

@Composable
internal fun LabeledDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
    disabledOptions: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    // ── Controller / D-pad support (all defaulted, so every existing touch caller is unaffected) ──
    // [focused] draws the focus border on the anchor when this dropdown is the highlighted control.
    // [expandedOverride] (when non-null) lets a parent CONTROL the open state instead of the internal
    // one — the shortcut editor's root D-pad handler opens/closes exactly one dropdown at a time this
    // way. [onExpandedChange] fires on every open/close request (touch tap, item pick, outside dismiss)
    // so the parent's open-tracker stays in sync. [highlightedIndex] tints the option the D-pad cursor
    // is on. With all four at their defaults the box behaves exactly as before (own state, no highlight).
    focused: Boolean = false,
    expandedOverride: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    highlightedIndex: Int = -1,
) {
    var internalExpanded by remember { mutableStateOf(false) }
    val expanded = expandedOverride ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = { want ->
        if (enabled) {
            onExpandedChange?.invoke(want)
            if (expandedOverride == null) internalExpanded = want
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { setExpanded(it) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .then(
                    if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                    else Modifier
                )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            options.forEachIndexed { idx, opt ->
                if (idx > 0) MenuItemDivider()
                val optEnabled = opt !in disabledOptions
                DropdownMenuItem(
                    text = { Text(opt) },
                    enabled = optEnabled,
                    onClick = { if (optEnabled) { onSelect(opt); setExpanded(false) } },
                    modifier = if (idx == highlightedIndex)
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    else Modifier,
                )
            }
        }
    }
}

@Composable
private fun CompactDropdown(
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedCard(
            modifier = Modifier
                .menuAnchor()
                .height(56.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = selectedOption,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            options.forEachIndexed { idx, opt ->
                if (idx > 0) MenuItemDivider()
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
internal fun GraphicsDriverConfigDialog(
    graphicsDriver: String,
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val cfg = remember(initialConfig) {
        initialConfig.split(";").associate { elem ->
            val parts = elem.split("=")
            parts[0] to if (parts.size > 1) parts[1] else ""
        }
    }

    var version          by remember { mutableStateOf(cfg["version"] ?: "") }
    var vulkanVersion    by remember { mutableStateOf(cfg["vulkanVersion"] ?: "1.4") }
    var gpuName          by remember { mutableStateOf(cfg["gpuName"] ?: "Device") }
    var presentMode      by remember { mutableStateOf(cfg["presentMode"] ?: "mailbox") }
    var resourceType     by remember { mutableStateOf(cfg["resourceType"] ?: "auto") }
    var bcnEmulation     by remember { mutableStateOf(cfg["bcnEmulation"] ?: "auto") }
    var bcnEmulationType by remember { mutableStateOf(cfg["bcnEmulationType"] ?: "software") }
    var bcnEmulationCache by remember { mutableStateOf(cfg["bcnEmulationCache"] ?: "0") }
    // WRAPPER_BCN_ASTC — integrated-wrapper (Wrapper-gamenative/leegao) ASTC transcode path.
    // Off by default; only honored by the BCn-integrated wrapper ICD, ignored by others.
    var bcnEmulationAstc by remember { mutableStateOf(cfg["bcnEmulationAstc"] == "1") }
    var syncFrame        by remember { mutableStateOf(cfg["syncFrame"] == "1") }
    var disablePresentWait by remember { mutableStateOf(cfg["disablePresentWait"] == "1") }
    var fdDevFeatures    by remember { mutableStateOf(cfg["fdDevFeatures"] == "1") }

    // --- Turnip GMEM tri-state (task #1) + advanced TU_DEBUG tokens (task #2) ---
    // turnipGmem: "auto" (default) | "on" | "off". Auto adds the `gmem` token only on Adreno
    // 710/720/722 at launch (resolved in XServerDisplayActivity). turnip* token flags map to the
    // opt-in advanced TU_DEBUG tokens, stored comma-joined under "turnipTokens".
    var turnipGmem       by remember { mutableStateOf(cfg["turnipGmem"] ?: "auto") }
    val initialTurnipTokens = remember(initialConfig) {
        (cfg["turnipTokens"] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    var turnipForceCb    by remember { mutableStateOf("forcecb" in initialTurnipTokens) }
    var turnipNoCb       by remember { mutableStateOf("nocb" in initialTurnipTokens) }
    var turnipSysmem     by remember { mutableStateOf("sysmem" in initialTurnipTokens) }
    var turnipDeckEmu    by remember { mutableStateOf("deck_emu" in initialTurnipTokens) }
    var turnipSectionExpanded by remember { mutableStateOf(false) }

    // --- #132 Smart Wrapper Manager: capability + GPU gating (replaces the old exact-name gates) ---
    // Gate the BCn options by WHAT THE WRAPPER CONTAINS, not by its identifier string. capsFor()
    // returns a bundled driver's known caps, or — for an imported wrapper — the caps detected from
    // its .tzst at import time (libvulkan_wrapper.so / libbcn_layer.so / libdxvk_mali_compat_layer.so,
    // cached in the .meta). This reproduces every bundled driver's current gating exactly, while
    // letting an imported wrapper that actually carries a BCn layer (e.g. "112") show the same
    // options. hasCompatLayer drives the DX12/compat UI (sparse binding / "Use GameNative engine")
    // brought in from the Mali branch below.
    val caps = remember(graphicsDriver) { WrapperManager(context).capsFor(graphicsDriver) }
    val isImported = remember(graphicsDriver) { WrapperManager(context).isImported(graphicsDriver) }
    // Integrated-BCn ICD = a wrapper ICD that honors WRAPPER_BCN_ASTC. Bundled: only wrapper-gamenative
    // (hasIcd + hasBcnLayer). For IMPORTS we can't yet tell integrated-BCn (env baked into the ICD, no
    // separate .so) from a plain ICD without an env-scan of the binary (Step-3 Layer 1, deferred), so
    // any imported ICD shows the toggle — a non-gamenative ICD simply ignores WRAPPER_BCN_ASTC (inert,
    // harmless). This keeps an imported GameNative wrapper from losing its ASTC toggle.
    val isIntegratedBcn = (caps.hasIcd && caps.hasBcnLayer) || (isImported && caps.hasIcd)
    // Standalone BCn Layer Settings (the implicit bcn_layer overlay's env block). Bundled: only
    // wrapper-bcn_layer (hasBcnLayer, no own ICD). For imports: any archive carrying a BCn layer.
    // The bundled integrated-BCn ICD (gamenative, hasIcd + hasBcnLayer but NOT an import) is excluded
    // — it drives BCn through WRAPPER_EMULATE_BCN, not the implicit-layer env — so its dialog stays
    // byte-for-byte as today.
    val showBcnLayerSettings = caps.hasBcnLayer && (isImported || !caps.hasIcd)
    // GPU awareness: BCn transcode/emulation is inert on Qualcomm/Adreno (native BCn). Mirror XSDA's
    // activateBcnLayer = getVendorID != 0x5143 gate so we MARK (never silently hide) those options.
    val isQualcomm = remember { GPUInformation.getVendorID(null, null) == 0x5143 }
    val gpuModel = remember { GPUInformation.extractModelName(GPUInformation.getRenderer(null, context)) ?: "" }
    // --- Mali branch: name-based gates for the new "Wrapper + compat + bcn" (DX12) driver. Kept
    // alongside the capability gates above — downstream UI uses BOTH (caps gates for the generic BCn
    // panel; these for the compat-driver-specific DX12/GameNative-engine controls). ---
    // BCn Layer (leegao bcn_layer) settings; meaningful for both the bcn_layer driver and the
    // "Wrapper + compat + bcn" driver, which reuses the same BCn transcode panel.
    val isBcnLayer = graphicsDriver == "wrapper-bcn_layer" || graphicsDriver == "wrapper-compat-bcn"
    // compat_layer (DX12 feature emulation) is exclusive to "Wrapper + compat + bcn".
    val isCompatDriver = graphicsDriver == "wrapper-compat-bcn"
    // The integrated-BCn wrapper (Wrapper-gamenative) is the only wrapper ICD that actually honors
    // WRAPPER_BCN_ASTC (see XServerDisplayActivity BCn env block). The older wrappers
    // (original/leegao/legacy) ignore it, and Wrapper + bcn_layer has its own ASTC control
    // (bcnTranscodeAstc), so the general "BCn -> ASTC transcode" toggle belongs to gamenative only.
    val isGamenative = graphicsDriver == "wrapper-gamenative"
    var bcnSectionExpanded by remember { mutableStateOf(false) }
    // Force decode on all GPUs -> BCN_COMPUTE_AUTO=0. Default ON (the Mali force-decode fix).
    var bcnLayerAuto      by remember { mutableStateOf(cfg["bcnLayerAuto"]?.let { it == "1" } ?: true) }
    var bcnTranscodeEtc2  by remember { mutableStateOf(cfg["bcnTranscodeEtc2"] == "1") }
    var bcnTranscodeAstc  by remember { mutableStateOf(cfg["bcnTranscodeAstc"] == "1") }
    // Storage image path -> BCN_COMPUTE_IMAGE_VIEW=1. Default ON.
    var bcnImageView      by remember { mutableStateOf(cfg["bcnImageView"]?.let { it == "1" } ?: true) }
    var bcnDebugLog       by remember { mutableStateOf(cfg["bcnDebugLog"] == "1") }
    // "BCn layer: Fast" (BcnFastLayer): Fcharan's faster build of the layer, Mali only. Seeded from the
    // stored key on every GPU so OK keeps it; the row itself is shown only on a Mali GPU.
    val isMaliGpu = remember { com.winhub.hernandez.core.MaliPanvk.isMaliGpu() }
    var bcnLayerFast      by remember { mutableStateOf(com.winhub.hernandez.core.BcnFastLayer.isPicked(cfg)) }
    // PanVK driver variables (MaliPanvk), Mali only. Seeded from the stored keys on every GPU so OK keeps
    // them; blank = unset = not exported.
    var panvkHeap         by remember { mutableStateOf(cfg[com.winhub.hernandez.core.MaliPanvk.KEY_HEAP] ?: "") }
    var panvkTilerHeap    by remember { mutableStateOf(cfg[com.winhub.hernandez.core.MaliPanvk.KEY_TILER_HEAP] ?: "") }
    var panvkPolyHeap     by remember { mutableStateOf(cfg[com.winhub.hernandez.core.MaliPanvk.KEY_POLY_HEAP] ?: "") }
    var panvkAtomStride   by remember { mutableStateOf(cfg[com.winhub.hernandez.core.MaliPanvk.KEY_ATOM_STRIDE] ?: "") }
    var panvkTrace        by remember { mutableStateOf(cfg[com.winhub.hernandez.core.MaliPanvk.KEY_TRACE] == "1") }
    var maliHideExts      by remember { mutableStateOf(cfg[com.winhub.hernandez.core.MaliPanvk.KEY_HIDE_EXTS] == "1") }
    // compat_layer: emulate D3D12 tiled/sparse resources (COMPAT_EMULATE_SPARSE_BINDING). Opt-in,
    // only honored by the "Wrapper + compat + bcn" driver on a Valhall Mali. Default OFF.
    var bcnCompatSparse   by remember { mutableStateOf(cfg["bcnCompatSparse"] == "1") }
    // compat engine selection: OFF (default) = leegao bcn_layer + compat_layer (BCn textures only,
    // no DX12). ON = swap the ICD base to the GameNative wrapper (wrapper-gamenative.tzst), which
    // reports Vulkan 1.3 + emulates the promoted entrypoints so DXVK/VKD3D accept the adapter (DX12),
    // and uses its own integrated BCn. Only for "Wrapper + compat + bcn"; a Valhall Mali (r32p1+) is
    // required at activation time (XServerDisplayActivity gates it and falls back with a warning).
    var compatUseGamenative by remember { mutableStateOf(cfg["compatUseGamenative"] == "1") }

    // --- #132 Smart Wrapper Manager, Layer 1: auto-detected settings for IMPORTED wrappers only ---
    // The env-var NAMES were scanned out of the wrapper's binaries at import and cached in its .meta.
    // We only READ the .meta here (off-main via IO, keyed on the config so it can't drift), then render
    // one control per detected key that ISN'T already exposed by a curated control above. Values live in
    // graphicsDriverConfig under the RAW ENV KEY so they round-trip and XSDA emits them generically.
    var detectedKeys by remember(graphicsDriver) { mutableStateOf<List<String>>(emptyList()) }
    val detectedValues = remember(graphicsDriver) { mutableStateMapOf<String, String>() }
    LaunchedEffect(graphicsDriver, isImported) {
        if (!isImported) { detectedKeys = emptyList(); return@LaunchedEffect }
        val wm = WrapperManager(context)
        // A detected key is a settable SETTING only if it isn't already exposed by a curated control
        // (HANDLED_ENV_KEYS), isn't debug/diagnostics plumbing (isDebugEnvKey), and hasn't been hidden
        // for this wrapper via "Edit settings" (hiddenKeys). Same predicate as XSDA emission + the
        // Edit-settings dialog.
        val hidden = withContext(Dispatchers.IO) { wm.hiddenKeys(graphicsDriver) }
        val keys = withContext(Dispatchers.IO) { wm.detectedEnvKeys(graphicsDriver) }
            .filter {
                it !in WrapperManager.HANDLED_ENV_KEYS && !WrapperManager.isDebugEnvKey(it) &&
                    !WrapperManager.isDriverInternalEnvKey(it) && it !in hidden
            }
        keys.forEach { k ->
            val def = WrapperSettingsDictionary.defFor(k)
            // Seed from the stored config; a toggle normalises to "1"/"0", others keep the raw string.
            detectedValues[k] = when (def.type) {
                WrapperSettingsDictionary.Type.TOGGLE -> if (cfg[k] == "1") "1" else "0"
                else -> cfg[k] ?: ""
            }
        }
        detectedKeys = keys
    }

    val deviceMemoryEntries = remember { context.resources.getStringArray(R.array.device_memory_entries).toList() }
    var selectedMemoryEntry by remember {
        val storedNum = cfg["maxDeviceMemory"] ?: "0"
        mutableStateOf(deviceMemoryEntries.firstOrNull { StringUtils.parseNumber(it) == storedNum } ?: deviceMemoryEntries.first())
    }

    var driverVersions      by remember { mutableStateOf(listOf<String>()) }
    var gpuNames            by remember { mutableStateOf(listOf("Device")) }
    var allExtensions       by remember { mutableStateOf(listOf<String>()) }
    val initialBlacklist = remember(initialConfig) {
        (cfg["blacklistedExtensions"] ?: "").split(",").filter { it.isNotEmpty() }.toSet()
    }
    var blacklisted   by remember { mutableStateOf(initialBlacklist) }
    var showAllDrivers by remember { mutableStateOf(false) }
    var showExtPicker by remember { mutableStateOf(false) }
    // True when the picked custom driver couldn't load on this GPU and the native probe fell
    // back to the system ICD (instead of crashing). Drives the inline note under the dropdown.
    var driverFellBack by remember { mutableStateOf(false) }
    // True when the selected version is an installed custom (Qualcomm proprietary) Adreno
    // driver, whose extensions we intentionally don't probe here — the UI shows an explanatory
    // note instead of a misleading "0/0 extensions".
    var isCustomDriver by remember { mutableStateOf(false) }

    LaunchedEffect(showAllDrivers) {
        val atVersions = withContext(Dispatchers.IO) {
            AdrenotoolsManager(context).enumarateInstalledDrivers()
        }
        val gpuList = withContext(Dispatchers.IO) {
            val list = mutableListOf("Device")
            try {
                val json = FileUtils.readString(context, "gpu_cards.json")
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i).getString("name"))
            } catch (_: Exception) {}
            list
        }
        // isDriverSupported() is a native JNI call. It used to run on the main thread to keep
        // AdrenoTools hook calls serial (concurrency caused SIGSEGV); running it there blocked
        // the UI and caused ANRs. Run it off-main but serialized via graphicsProbeMutex instead.
        val wrapperVersions = context.resources
            .getStringArray(R.array.wrapper_graphics_driver_version_entries)
            .let { arr ->
                if (showAllDrivers) arr.toList()
                else withContext(Dispatchers.IO) {
                    graphicsProbeMutex.withLock { arr.filter { GPUInformation.isDriverSupported(it, context) } }
                }
            }

        driverVersions = wrapperVersions + atVersions
        gpuNames = gpuList
        if (version.isEmpty() || (wrapperVersions + atVersions).none { it.equals(version, ignoreCase = true) }) {
            version = wrapperVersions.firstOrNull { it.equals(DefaultVersion.WRAPPER_ADRENO, ignoreCase = true) }
                ?: wrapperVersions.firstOrNull { it.equals(DefaultVersion.WRAPPER, ignoreCase = true) }
                ?: wrapperVersions.firstOrNull() ?: version
        }
    }

    LaunchedEffect(version) {
        if (version.isEmpty()) {
            allExtensions = emptyList()
            driverFellBack = false
            isCustomDriver = false
            return@LaunchedEffect
        }
        // Proprietary Qualcomm (Adreno) blobs must NEVER be probed in-process: on some a6xx
        // devices the in-app instance creation aborts inside the vendor app-profile/log path
        // with a -fstack-protector stack smash (SIGABRT) — uncatchable by the SEGV/BUS guard —
        // or corrupts the linker heap on dlopen (hotice77's Redmi Note 11, Aug 2026). Mesa
        // wrappers (Turnip/freedreno, panfrost, ...) export libvulkan_*.so and ARE safe to
        // probe, so they still list their real extensions. Anything that isn't a libvulkan_*
        // Mesa wrapper (a vulkan.ad*.so Qualcomm blob, or an unreadable meta) is skipped here
        // and shows the "applied in-game" note; the driver still loads at game launch.
        val probeUnsafe = withContext(Dispatchers.IO) {
            graphicsProbeMutex.withLock {
                val mgr = AdrenotoolsManager(context)
                val installed = mgr.enumarateInstalledDrivers()
                if (installed.none { it.equals(version, ignoreCase = true) }) {
                    false // wrapper/bundled entry (not a custom import) -> safe to probe
                } else {
                    !mgr.getLibraryName(version).startsWith("libvulkan", ignoreCase = true)
                }
            }
        }
        if (probeUnsafe) {
            allExtensions = emptyList()
            driverFellBack = false
            isCustomDriver = true
            if (version != cfg["version"]) blacklisted = emptySet()
            return@LaunchedEffect
        }
        // Soft-probe the (Mesa/wrapper) driver. Serialized + off-main via the mutex so it can
        // never wedge the UI thread (ANR) or run concurrently with the isDriverSupported filter.
        val exts = withContext(Dispatchers.IO) {
            graphicsProbeMutex.withLock {
                GPUInformation.enumerateExtensions(version, context)?.toList() ?: emptyList()
            }
        }
        allExtensions = exts
        driverFellBack = GPUInformation.driverLoadedFellBack()
        isCustomDriver = exts.isEmpty()
        if (version != cfg["version"]) blacklisted = emptySet()
    }

    if (showExtPicker) {
        ExtensionPickerDialog(
            extensions = allExtensions,
            blacklisted = blacklisted,
            onDismiss = { showExtPicker = false },
            onConfirm = { newBlacklist -> blacklisted = newBlacklist; showExtPicker = false }
        )
    }

    val vulkanVersions      = remember { context.resources.getStringArray(R.array.vulkan_version_entries).toList() }
    val presentModeEntries  = remember { context.resources.getStringArray(R.array.present_mode_entries).toList() }
    val resourceTypeEntries = remember { context.resources.getStringArray(R.array.resource_type_entries).toList() }
    val bcnEmulationEntries = remember { context.resources.getStringArray(R.array.bcn_emulation_entries).toList() }
    val bcnTypeEntries      = remember { context.resources.getStringArray(R.array.bcn_emulation_type_entries).toList() }
    val bcnCacheEntries     = remember { context.resources.getStringArray(R.array.bcn_emulation_cache_entries).toList() }

    // Per-option "?" help — this dialog is its own composable, so it carries its own helpRes.
    // HelpDialog renders as a Dialog on top of this AlertDialog (same pattern as elsewhere).
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.graphics_driver_configuration)) },
        text = {
            // Cap the scrollable region so tall content scrolls inside the dialog instead of
            // pushing the OK/Cancel buttons off-screen (Material3 AlertDialog doesn't bound its
            // text slot height on its own).
            val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_vulkan_version), vulkanVersions, vulkanVersion, { vulkanVersion = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_vulkan_version }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_version), driverVersions, version, { version = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_graphics_driver_version }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showAllDrivers, onCheckedChange = { showAllDrivers = it })
                    Text(stringResource(R.string.graphics_driver_show_incompatible), modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_show_incompatible_drivers }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (isCustomDriver) {
                    Text(
                        text = "Custom Qualcomm (Adreno) driver — its extensions load when a game starts, so none are listed here. That's expected, not an error: the driver is applied in-game, where your HUD will show it's active.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                    )
                } else if (allExtensions.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showExtPicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            val enabled = allExtensions.size - blacklisted.size
                            Text(stringResource(R.string.graphics_driver_available_extensions) + " ($enabled/${allExtensions.size})")
                        }
                        IconButton(onClick = { helpRes = R.string.help_available_extensions }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.gpu_name), gpuNames, gpuName, { gpuName = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_gpu_name }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_max_device_memory), deviceMemoryEntries, selectedMemoryEntry, { selectedMemoryEntry = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_max_device_memory }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_present_modes), presentModeEntries, presentMode, { presentMode = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_wrapper_present_modes }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_resource_type), resourceTypeEntries, resourceType, { resourceType = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_resource_type }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // #132 GPU-context note: BCn transcode/emulation (and the compat layers) only do
                // anything on Mali/non-Qualcomm GPUs — Adreno has native BCn. Surface the real chip so
                // Adreno users understand why these options are inert for them (a real point of confusion).
                Text(
                    (if (gpuModel.isNotEmpty()) "GPU: $gpuModel — " else "") +
                        "BCn/compat layers apply to Mali and other non-Qualcomm GPUs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_bcn_emulation), bcnEmulationEntries, bcnEmulation, { bcnEmulation = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_bcn_emulation }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_bcn_emulation_type), bcnTypeEntries, bcnEmulationType, { bcnEmulationType = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_bcn_emulation_type }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(stringResource(R.string.graphics_driver_bcn_emulation_cache), bcnCacheEntries, bcnEmulationCache, { bcnEmulationCache = it }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_bcn_emulation_cache }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // ASTC transcode is offered by any integrated-BCn wrapper ICD (bundled Wrapper-
                // gamenative, or an imported wrapper whose archive carries a BCn layer). The standalone
                // bcn_layer driver has its own ASTC control in its section below; the older wrappers
                // ignore WRAPPER_BCN_ASTC entirely — so gate on the detected capability, not the name.
                if (isIntegratedBcn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bcnEmulationAstc, onCheckedChange = { bcnEmulationAstc = it })
                        Text(stringResource(R.string.graphics_driver_bcn_emulation_astc), modifier = Modifier.weight(1f))
                        IconButton(onClick = { helpRes = R.string.help_bcn_transcode_astc }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (isQualcomm) {
                        Text(
                            "No effect on Adreno (native BCn)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Compute-layer BCn -> ASTC target on the DEFAULT driver. When BCn type = compute, the
                // implicit leegao bcn_layer is the active decoder (ENABLE_BCN_COMPUTE) and honors
                // BCN_TRANSCODE_TO_ASTC — previously only exposed on the explicit Wrapper + bcn_layer
                // driver, so Mali users had to hand-add the env var. Reuses the same bcnTranscodeAstc
                // state (already persisted); default off.
                if (!isBcnLayer && bcnEmulationType == "compute" &&
                    (bcnEmulation == "auto" || bcnEmulation == "full")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bcnTranscodeAstc, onCheckedChange = { bcnTranscodeAstc = it })
                        Text(stringResource(R.string.bcn_layer_transcode_astc), modifier = Modifier.weight(1f))
                        IconButton(onClick = { helpRes = R.string.help_bcn_transcode_astc }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (isQualcomm) {
                        Text(
                            "No effect on Adreno (native BCn)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Mali only: pick Fcharan's faster BCn layer build wherever the layer runs (the
                // compute type on this driver, or a driver that carries the layer).
                if (isMaliGpu && (showBcnLayerSettings || (!isBcnLayer && bcnEmulationType == "compute" &&
                        (bcnEmulation == "auto" || bcnEmulation == "full")))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bcnLayerFast, onCheckedChange = { bcnLayerFast = it })
                        Text(stringResource(R.string.bcn_layer_fast), modifier = Modifier.weight(1f))
                        IconButton(onClick = { helpRes = R.string.help_bcn_layer_fast }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (isMaliGpu) {
                    Text("Mali (PanVK) driver", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = maliHideExts, onCheckedChange = { maliHideExts = it })
                        Text("Hide present-wait / dynamic-rendering extensions", modifier = Modifier.weight(1f))
                    }
                    Text(
                        "Hides present_id, present_wait and dynamic_rendering from the game and turns on Disable present wait. Try it if a game freezes or shows a black screen on PanVK.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    @Composable
                    fun MbField(label: String, value: String, min: Int, max: Int, hint: String, onChange: (String) -> Unit) {
                        val bad = value.isNotBlank() && com.winhub.hernandez.core.MaliPanvk.validMb(value, min, max) == null
                        OutlinedTextField(
                            value = value,
                            onValueChange = { onChange(it.filter { ch -> ch.isDigit() }.take(5)) },
                            label = { Text(label) },
                            supportingText = { Text(if (bad) "Out of range ($min to $max): not used." else hint) },
                            isError = bad,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MbField("PANVK_HEAP_MB", panvkHeap, com.winhub.hernandez.core.MaliPanvk.HEAP_MIN,
                        com.winhub.hernandez.core.MaliPanvk.HEAP_MAX, "Default: a quarter of RAM, 1024 to 3072 MB.") { panvkHeap = it }
                    MbField("PANVK_TILER_HEAP_MB", panvkTilerHeap, com.winhub.hernandez.core.MaliPanvk.TILER_HEAP_MIN,
                        com.winhub.hernandez.core.MaliPanvk.TILER_HEAP_MAX, "Default: 512 MB.") { panvkTilerHeap = it }
                    MbField("PANVK_POLY_HEAP_MB", panvkPolyHeap, com.winhub.hernandez.core.MaliPanvk.POLY_HEAP_MIN,
                        com.winhub.hernandez.core.MaliPanvk.POLY_HEAP_MAX, "Default: 16 MB.") { panvkPolyHeap = it }
                    LabeledDropdown(
                        "PANVK_ATOM_STRIDE",
                        listOf("Auto") + com.winhub.hernandez.core.MaliPanvk.ATOM_STRIDES,
                        panvkAtomStride.ifEmpty { "Auto" },
                        { panvkAtomStride = if (it == "Auto") "" else it }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = panvkTrace, onCheckedChange = { panvkTrace = it })
                        Text("PANVK_TRACE (driver tracing)", modifier = Modifier.weight(1f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = syncFrame, onCheckedChange = { syncFrame = it })
                    Text(stringResource(R.string.graphics_driver_sync_frame), modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_sync_every_frame }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = disablePresentWait, onCheckedChange = { disablePresentWait = it })
                    Text(stringResource(R.string.graphics_driver_disable_present_wait), modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_disable_present_wait }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fdDevFeatures, onCheckedChange = { fdDevFeatures = it })
                    Text("OneUI / HyperOS Fix", modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_oneui_hyperos_fix }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }

                // --- Turnip GMEM (task #1) — always visible: this is the escape hatch for 710/720/722
                // users on a STOCK driver. Tri-state maps to whether `gmem` joins TU_DEBUG at launch. ---
                Spacer(Modifier.height(12.dp))
                Text(
                    "Turnip GMEM",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                val gmemLabels = listOf("Auto (Adreno 710/720/722)", "Force On", "Force Off")
                val gmemValues = listOf("auto", "on", "off")
                val gmemSel = gmemLabels[gmemValues.indexOf(turnipGmem).coerceAtLeast(0)]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown("GMEM (tiled rendering)", gmemLabels, gmemSel, { picked ->
                        turnipGmem = gmemValues[gmemLabels.indexOf(picked).coerceAtLeast(0)]
                    }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { helpRes = R.string.help_turnip_gmem }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    "Auto forces GMEM tiled rendering (TU_DEBUG=gmem) only on Adreno 710/720/722; " +
                        "Force On applies it on any GPU; Force Off never applies it. Leave on Auto unless a " +
                        "game misbehaves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // --- Advanced TU_DEBUG tokens (task #2) — collapsed expert section, off by default. ---
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { turnipSectionExpanded = !turnipSectionExpanded }
                ) {
                    Text(
                        (if (turnipSectionExpanded) "▾  " else "▸  ") + "Advanced Turnip (TU_DEBUG)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (turnipSectionExpanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Expert-only Turnip debug tokens. Off by default — enable only if you know what " +
                            "they do. These are unioned with the GMEM setting above into TU_DEBUG.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = turnipForceCb, onCheckedChange = { turnipForceCb = it })
                        Text("forcecb — force concurrent binning", modifier = Modifier.weight(1f))
                        IconButton(onClick = { helpRes = R.string.help_turnip_concurrent_binning }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = turnipNoCb, onCheckedChange = { turnipNoCb = it })
                        Text("nocb — disable concurrent binning", modifier = Modifier.weight(1f))
                        IconButton(onClick = { helpRes = R.string.help_turnip_concurrent_binning }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    // sysmem is the direct opposite of gmem and Turnip lets it defeat gmem, so when
                    // GMEM = Force On the two would contradict. Grey out the pick (gmem always wins the
                    // launch-time merge anyway) so the UI can't express the contradiction.
                    val sysmemBlockedByGmem = turnipGmem == "on"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = turnipSysmem && !sysmemBlockedByGmem,
                            onCheckedChange = { turnipSysmem = it },
                            enabled = !sysmemBlockedByGmem
                        )
                        Text(
                            "sysmem — force sysmem (bypass GMEM)",
                            modifier = Modifier.weight(1f),
                            color = if (sysmemBlockedByGmem) MaterialTheme.colorScheme.onSurfaceVariant
                                    else Color.Unspecified
                        )
                        IconButton(onClick = { helpRes = R.string.help_turnip_sysmem }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (sysmemBlockedByGmem) {
                        Text(
                            "Disabled — Turnip GMEM = Force On overrides sysmem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = turnipDeckEmu, onCheckedChange = { turnipDeckEmu = it })
                        Text("deck_emu — advertise as SteamDeck", modifier = Modifier.weight(1f))
                        IconButton(onClick = { helpRes = R.string.help_turnip_deck_emu }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "deck_emu requires a Banners-Turnip driver (ignored on stock Turnip).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // BCn Layer Settings — shown when the wrapper carries a BCn layer as an implicit
                // overlay: bundled wrapper-bcn_layer, or an imported wrapper whose .tzst contains
                // libbcn_layer.so (e.g. "112"). Detected via caps, not the driver name.
                if (showBcnLayerSettings) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bcnSectionExpanded = !bcnSectionExpanded }
                    ) {
                        Text(
                            (if (bcnSectionExpanded) "▾  " else "▸  ") + stringResource(R.string.bcn_layer_section),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (bcnSectionExpanded) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.bcn_layer_section_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isQualcomm) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No effect on Adreno (native BCn) — these apply to Mali/non-Qualcomm GPUs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = bcnLayerAuto, onCheckedChange = { bcnLayerAuto = it })
                            Text(stringResource(R.string.bcn_layer_force_decode))
                        }
                        Text(
                            stringResource(R.string.bcn_layer_force_decode_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = bcnTranscodeEtc2, onCheckedChange = { bcnTranscodeEtc2 = it })
                            Text(stringResource(R.string.bcn_layer_transcode_etc2))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = bcnTranscodeAstc, onCheckedChange = { bcnTranscodeAstc = it })
                            Text(stringResource(R.string.bcn_layer_transcode_astc))
                        }
                        Text(
                            stringResource(R.string.bcn_layer_transcode_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = bcnImageView, onCheckedChange = { bcnImageView = it })
                            Text(stringResource(R.string.bcn_layer_image_view))
                        }
                        Text(
                            stringResource(R.string.bcn_layer_image_view_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = bcnDebugLog, onCheckedChange = { bcnDebugLog = it })
                            Text(stringResource(R.string.bcn_layer_debug_log))
                        }
                        Text(
                            stringResource(R.string.bcn_layer_debug_log_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // compat_layer DX12 sparse-binding emulation — only for Wrapper + compat + bcn.
                        if (isCompatDriver) {
                            // Engine selector: swap the ICD base from leegao to the GameNative wrapper
                            // for real DX12 support. Primary switch, shown above the sparse opt-in.
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = compatUseGamenative, onCheckedChange = { compatUseGamenative = it })
                                Text(stringResource(R.string.compat_use_gamenative))
                            }
                            Text(
                                stringResource(R.string.compat_use_gamenative_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = bcnCompatSparse, onCheckedChange = { bcnCompatSparse = it })
                                Text(stringResource(R.string.bcn_compat_sparse))
                            }
                            Text(
                                stringResource(R.string.bcn_compat_sparse_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // --- #132 Smart Wrapper Manager, Layer 1: auto-detected settings (imports only) ---
                // One control per env-var name scanned from THIS wrapper's binaries, minus the keys a
                // curated control above already exposes (HANDLED_ENV_KEYS). Values are stored under the
                // raw env key and passed to the wrapper verbatim at launch. Bundled wrappers: not shown.
                if (isImported) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Detected settings (advanced) — from scanning this wrapper",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Values are passed to the wrapper as-is; unknown ones are safe to leave blank.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    if (detectedKeys.isEmpty()) {
                        Text(
                            "No extra settings detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        detectedKeys.forEach { key ->
                            val def = WrapperSettingsDictionary.defFor(key)
                            val current = detectedValues[key] ?: ""
                            when (def.type) {
                                WrapperSettingsDictionary.Type.TOGGLE -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = current == "1",
                                            onCheckedChange = { detectedValues[key] = if (it) "1" else "0" }
                                        )
                                        Text(def.label)
                                    }
                                }
                                WrapperSettingsDictionary.Type.DROPDOWN -> {
                                    val selected = current.ifEmpty { def.default }.ifEmpty { def.choices.firstOrNull() ?: "" }
                                    LabeledDropdown(def.label, def.choices, selected, { detectedValues[key] = it })
                                }
                                WrapperSettingsDictionary.Type.SLIDER -> {
                                    val fv = current.toFloatOrNull() ?: def.default.toFloatOrNull() ?: def.min
                                    Text("${def.label}: ${fv.toInt()}", style = MaterialTheme.typography.bodySmall)
                                    Slider(
                                        value = fv,
                                        onValueChange = { detectedValues[key] = it.toInt().toString() },
                                        valueRange = def.min..(if (def.max > def.min) def.max else def.min + 1f),
                                        steps = if (def.step > 0f && def.max > def.min)
                                            (((def.max - def.min) / def.step).toInt() - 1).coerceAtLeast(0) else 0
                                    )
                                }
                                WrapperSettingsDictionary.Type.TEXT -> {
                                    OutlinedTextField(
                                        value = current,
                                        onValueChange = { detectedValues[key] = it },
                                        singleLine = true,
                                        label = { Text(def.label) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (def.hint.isNotBlank()) {
                                Text(
                                    def.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val config = "vulkanVersion=$vulkanVersion;" +
                    "version=$version;" +
                    "blacklistedExtensions=${blacklisted.joinToString(",")};" +
                    "maxDeviceMemory=${StringUtils.parseNumber(selectedMemoryEntry)};" +
                    "presentMode=$presentMode;" +
                    "syncFrame=${if (syncFrame) "1" else "0"};" +
                    "disablePresentWait=${if (disablePresentWait) "1" else "0"};" +
                    "resourceType=$resourceType;" +
                    "bcnEmulation=$bcnEmulation;" +
                    "bcnEmulationType=$bcnEmulationType;" +
                    "bcnEmulationCache=$bcnEmulationCache;" +
                    "bcnEmulationAstc=${if (bcnEmulationAstc) "1" else "0"};" +
                    "bcnLayerAuto=${if (bcnLayerAuto) "1" else "0"};" +
                    "bcnTranscodeEtc2=${if (bcnTranscodeEtc2) "1" else "0"};" +
                    "bcnTranscodeAstc=${if (bcnTranscodeAstc) "1" else "0"};" +
                    "bcnImageView=${if (bcnImageView) "1" else "0"};" +
                    "bcnDebugLog=${if (bcnDebugLog) "1" else "0"};" +
                    "bcnCompatSparse=${if (bcnCompatSparse) "1" else "0"};" +
                    "compatUseGamenative=${if (compatUseGamenative) "1" else "0"};" +
                    (if (bcnLayerFast) "${com.winhub.hernandez.core.BcnFastLayer.KEY}=${com.winhub.hernandez.core.BcnFastLayer.FAST};" else "") +
                    // Stored only when set (Mali), so other GPUs' configs stay as they were.
                    listOf(
                        com.winhub.hernandez.core.MaliPanvk.KEY_HEAP to panvkHeap.trim(),
                        com.winhub.hernandez.core.MaliPanvk.KEY_TILER_HEAP to panvkTilerHeap.trim(),
                        com.winhub.hernandez.core.MaliPanvk.KEY_POLY_HEAP to panvkPolyHeap.trim(),
                        com.winhub.hernandez.core.MaliPanvk.KEY_ATOM_STRIDE to panvkAtomStride,
                        com.winhub.hernandez.core.MaliPanvk.KEY_TRACE to (if (panvkTrace) "1" else ""),
                        com.winhub.hernandez.core.MaliPanvk.KEY_HIDE_EXTS to (if (maliHideExts) "1" else ""),
                    ).filter { it.second.isNotEmpty() }.joinToString("") { "${it.first}=${it.second};" } +
                    "gpuName=$gpuName" +
                    ";fdDevFeatures=${if (fdDevFeatures) "1" else "0"}" +
                    ";turnipGmem=$turnipGmem" +
                    ";turnipTokens=" + buildList {
                        if (turnipForceCb) add("forcecb")
                        if (turnipNoCb) add("nocb")
                        // Don't persist sysmem when GMEM = Force On overrides it (matches the greyed-out
                        // checkbox); the launch-time merge would strip it anyway.
                        if (turnipSysmem && turnipGmem != "on") add("sysmem")
                        if (turnipDeckEmu) add("deck_emu")
                    }.joinToString(",")
                // #132 Layer 1: append auto-detected wrapper settings under their RAW ENV KEY. Sanitise
                // values so they can't break the ";"/"=" k=v format the config round-trips through.
                val detectedPart = detectedKeys.joinToString("") { key ->
                    val v = (detectedValues[key] ?: "").replace(";", "").replace("=", "")
                    ";$key=$v"
                }
                onConfirm(config + detectedPart)
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
internal fun ExtensionPickerDialog(
    extensions: List<String>,
    blacklisted: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var state by remember(extensions, blacklisted) {
        mutableStateOf(extensions.associateWith { !blacklisted.contains(it) })
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.graphics_driver_available_extensions)) },
        text = {
            if (extensions.isEmpty()) {
                Text("No extensions available for this driver.")
            } else {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(extensions) { ext ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = state[ext] == true,
                                onCheckedChange = { checked ->
                                    state = state.toMutableMap().also { it[ext] = checked }
                                }
                            )
                            Text(ext, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newBlacklist = extensions.filter { state[it] != true }.toSet()
                onConfirm(newBlacklist)
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────

// ─── VEGAS per-key editor helpers ────────────────────────────────────────────────
@Composable
private fun SectionLabel(
    text: String,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.08.em,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}


// ─── VEGAS-expanded DxvkConfigDialog (replaces upstream's simpler version) ────
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DxvkConfigDialog(
    isArm64EC: Boolean,
    isVegas: Boolean = false,
    containerRootDir: java.io.File? = null,
    refreshKey: Int = 0,
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    onDownloadDxvk: () -> Unit = {},
    onDownloadVkd3d: () -> Unit = {},
    onDownloadD7vk: () -> Unit = {},
    relaxDxvkFilter: Boolean = false,
    // Fires after every successful live-file write with the file the game must read.
    // The host persists dxvkConfigFile immediately — waiting for OK left toggles
    // invisible to launched games when the sheet was dismissed without OK (the
    // pointer kept aiming at the old path, e.g. a legacy /sdcard/dxvk.conf).
    onLivePointerChanged: (String) -> Unit = {},
    onOpenConfigDownload: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember(initialConfig) { DXVKConfigDialog.parseConfig(initialConfig) }
    val activity = context.findActivity() ?: return
    var isProcessing by remember { mutableStateOf(false) }

    // Virtual "Browse…" entry in the custom-source dropdown; launches the file picker.
    val BROWSE_CONFIG_MARKER = "Browse for file…"

    val allDxvkVersions = remember { mutableStateOf(listOf<String>()) }
    val vkd3dVersions   = remember { mutableStateOf(listOf<String>()) }
    val configSourceEntries = remember { mutableStateOf(listOf<String>()) }
    // Seeded with the bundled sentinel so the D7VK version dropdown always offers "Bundled (default)"
    // even before the async catalog load lands (or when there are no downloaded d7vk profiles).
    val d7vkVersions    = remember { mutableStateOf(listOf(DXVKConfigDialog.D7VK_BUNDLED)) }

    // ---- VEGAS config source: stock/custom two-source model (Tier-2B) ----
    // Declared before LaunchedEffect: the init block below references these on first load.
    val stockSources = remember { mutableStateOf(listOf<DXVKConfigDialog.StockSource>()) }
    // Installed VEGAS builds (verNames) — drives the inline ⬇ on the stock-config row:
    // versions whose parked .conf is missing can be fetched straight from the releases
    // feed without re-downloading the whole build.
    val installedVegasVersions = remember { mutableStateOf(listOf<String>()) }
    val customEntries = remember { mutableStateOf(listOf<String>()) }
    var useDefaults by remember { mutableStateOf(true) }
    var selectedStock by remember { mutableStateOf<String?>(null) }   // stock verName
    var selectedCustom by remember { mutableStateOf<String?>(null) }  // custom file path
    var stockEdited by remember { mutableStateOf(false) }
    var toggleVersion by remember { mutableStateOf(0) }               // bump after a write -> re-snapshot
    // Capture-once backups: the auto slot is the FILE <name>.bak beside the live file.
    // It is created on the FIRST edit after a fresh selection/restore and then left
    // alone — across edits AND sessions (no in-memory set to reset). Restore consumes
    // it, so the next edit recaptures. Manual "Backup now" copies use .bak-manual-N
    // and are never touched automatically.
    // §6c value editor: the row whose value picker is open + the freeform draft.
    var valuePickerRow by remember { mutableStateOf<VegasKeyKnowledge.EditRow?>(null) }
    var customValueDraft by remember { mutableStateOf("") }
    var pendingDeleteKey by remember { mutableStateOf<String?>(null) }
    // (+) add-key editor: freeform key/value appended to the live file (stock OR custom).
    var showAddKey by remember { mutableStateOf(false) }
    var addKeyDraft by remember { mutableStateOf("") }
    var addValueDraft by remember { mutableStateOf("") }
    // §tier: staged FAQ tier selection (null = auto). Applied through vegas.forceTier.
    var tierChoice by remember { mutableStateOf<Int?>(null) }
    // §tier: device detection, read once per dialog open (native probe is cheap, still cached).
    val tierCtx = LocalContext.current
    val gpuModel = remember { VegasTierPresets.readGpuModel(tierCtx) }
    val detectedTier = remember { VegasTierPresets.classifyModel(gpuModel) }
    var showBackups by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<java.io.File?>(null) }
    // §7 release notes: live fetch (isygold/vegas-releases) cached per version per session,
    // bundled fallback (VegasTierPresets.BUNDLED_NOTES), hidden when neither.
    var notesCache by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    var notesLoading by remember { mutableStateOf(false) }
    var notesSource by remember { mutableStateOf<String?>(null) }  // "live" | "bundled" | "none"
    var showNotes by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val cm = ContentsManager(context)
            cm.syncContents()
            val versions = if (isVegas)
                DXVKConfigDialog.loadVegasVersionList(context, cm)
            else
                DXVKConfigDialog.loadDxvkVersionList(context, cm, isArm64EC)
            val vkd3d = DXVKConfigDialog.loadVkd3dVersionList(context, cm)
            val d7vk = DXVKConfigDialog.loadD7vkVersionList(context, cm)
            val cfgsrc = DXVKConfigDialog.loadVegasConfigSourceList(context)
            val stock = if (isVegas) DXVKConfigDialog.loadVegasStockSources(context, cm) else listOf()
            val installed = if (isVegas)
                cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VEGAS)
                    ?.mapNotNull { it.verName }?.distinct().orEmpty()
                else listOf()
            withContext(Dispatchers.Main) {
                allDxvkVersions.value = versions
                vkd3dVersions.value = vkd3d
                d7vkVersions.value = d7vk
                configSourceEntries.value = cfgsrc
                stockSources.value = stock
                installedVegasVersions.value = installed
                                // Option B init: the stored dxvkConfigFile IS the live file. Restore by matching:
                // parked stock file -> stock dropdown; sidecar of a known stock -> that stock
                // baseline (the pointer moved when the first edit happened); anything else
                // (custom path, incl. a legacy vegas/active.conf) -> custom dropdown, edited
                // in place. No active.conf copy is ever created or implied.
                val stored = config.get("dxvkConfigFile")
                val stockMatch = stock.firstOrNull { it.file.absolutePath == stored }
                val sDir = containerRootDir?.let { java.io.File(java.io.File(it, "vegas"), "configs") }
                val sidecarMatch = if (stored.isNotEmpty() && sDir != null && sDir.isDirectory) {
                    stock.firstOrNull { it.tag != null && java.io.File(sDir, it.tag + ".user.conf").absolutePath == stored }
                } else null
                val customBase = cfgsrc.filter { it != "None" }
                customEntries.value = if (stored.isNotEmpty() && stored !in customBase) customBase + stored else customBase
                when {
                    stored.isEmpty() -> {
                        useDefaults = true
                    }
                    stockMatch != null -> {
                        selectedStock = stockMatch.displayLabel()
                        selectedCustom = null
                        useDefaults = false
                    }
                    sidecarMatch != null -> {
                        selectedStock = sidecarMatch.displayLabel()
                        selectedCustom = null
                        useDefaults = false
                    }
                    else -> {
                        selectedCustom = stored
                        selectedStock = null
                        useDefaults = false
                    }
                }
            }
        }
    }

    var selectedVkd3d by remember { mutableStateOf(config.get("vkd3dVersion").ifEmpty { "None" }) }

    // VKD3D-Proton needs DXVK 2.x's DXGI; DXVK 1.x can't back it, so the DX12 test fails to start.
    // Filter the DXVK list to 2.x+ (keeping unparseable names, e.g. VEGAS) when VKD3D is enabled —
    // matches the shortcut-level dialog, which already enforces this. Fixes #113.
    // Exception: the Mali "Wrapper + compat + bcn" driver (relaxDxvkFilter) shows all DXVK versions
    // so testers can try the DXVK 1.10.3 adapter-accept workaround with VKD3D on (#137).
    val filteredDxvk = remember(selectedVkd3d, allDxvkVersions.value, relaxDxvkFilter) {
        if (selectedVkd3d != "None" && !relaxDxvkFilter) {
            allDxvkVersions.value.filter { v ->
                val major = DXVKConfigDialog.tryGetMajor(v)
                major == null || major >= 2
            }
        } else allDxvkVersions.value
    }

    var selectedDxvk by remember(allDxvkVersions.value) {
        val stored = config.get("version")
        mutableStateOf(allDxvkVersions.value.firstOrNull { it == stored } ?: allDxvkVersions.value.firstOrNull() ?: stored)
    }

    // Re-sync installed versions every time this dialog is composed (it only exists while
    // open): deletions made in the contents hub previously left ghosts in the uni-select.
    LaunchedEffect(Unit) {
        if (!isVegas) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val cm = ContentsManager(context)
            cm.syncContents()
            val versions = DXVKConfigDialog.loadVegasVersionList(context, cm)
            val stock = DXVKConfigDialog.loadVegasStockSources(context, cm)
            val installed = cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VEGAS)
                ?.mapNotNull { it.verName }?.distinct().orEmpty()
            withContext(Dispatchers.Main) {
                allDxvkVersions.value = versions
                stockSources.value = stock
                installedVegasVersions.value = installed
                if (selectedDxvk !in versions) selectedDxvk = versions.firstOrNull() ?: selectedDxvk
            }
        }
    }

    val dxvkType = remember(selectedDxvk) { DXVKConfigDialog.getDXVKType(selectedDxvk) }

    val framerateEntries  = remember { context.resources.getStringArray(R.array.dxvk_framerate_entries).toList() }
    val featureLevelEntries = remember { DXVKConfigDialog.VKD3D_FEATURE_LEVEL.toList() }
    val ddraEntries       = remember { context.resources.getStringArray(R.array.ddrawrapper_entries).toList() }
    val videoMemEntries   = remember { context.resources.getStringArray(R.array.dxvk_max_device_memory_entries).toList() }

    var selectedFramerate by remember {
        val stored = config.get("framerate")
        mutableStateOf(framerateEntries.firstOrNull { StringUtils.parseNumber(it) == stored } ?: framerateEntries.first())
    }
    var selectedFeatureLevel by remember { mutableStateOf(featureLevelEntries.firstOrNull { it == config.get("vkd3dLevel") } ?: featureLevelEntries.first()) }
    var selectedDdra         by remember { mutableStateOf(ddraEntries.firstOrNull { StringUtils.parseIdentifier(it) == config.get("ddrawrapper") } ?: ddraEntries.first()) }
    // D7VK version (only meaningful when DDraw Wrapper == D7VK). Empty/unknown -> the bundled asset.
    var selectedD7vk         by remember(d7vkVersions.value) {
        val stored = config.get("d7vkVersion")
        mutableStateOf(d7vkVersions.value.firstOrNull { it == stored } ?: DXVKConfigDialog.D7VK_BUNDLED)
    }
    var asyncEnabled         by remember { mutableStateOf(config.get("async") == "1") }
    var asyncCacheEnabled    by remember { mutableStateOf(config.get("asyncCache") == "1") }
    // Texture filtering: labels index-aligned with DXVKConfigDialog.ANISOTROPY_VALUES / LOD_BIAS_VALUES.
    val anisotropyLabels = remember { listOf("Game default", "2x", "4x", "8x", "16x") }
    val lodBiasLabels = remember {
        listOf("Game default", "Auto (match scaling mode)", "Sharper (-0.25)", "Sharper (-0.5)",
               "Sharper (-0.75)", "Sharpest (-1.0)")
    }
    var selectedAnisotropy by remember {
        val i = DXVKConfigDialog.ANISOTROPY_VALUES.indexOf(config.get("anisotropy"))
        mutableStateOf(anisotropyLabels[if (i >= 0) i else 0])
    }
    var selectedLodBias by remember {
        val i = DXVKConfigDialog.LOD_BIAS_VALUES.indexOf(config.get("lodBias"))
        mutableStateOf(lodBiasLabels[if (i >= 0) i else 0])
    }
    // "?" help for the two texture-filtering rows (opens above this sheet).
    var textureHelpRes by remember { mutableStateOf<Int?>(null) }
    textureHelpRes?.let { HelpDialog(it) { textureHelpRes = null } }

    // VEGAS knowledge layer: bundled asset or null (null -> unclassified fallback).
    val vegasKnowledge = remember {
        val k = DXVKConfigDialog.loadVegasKeyKnowledge(context)
        // Autonomy: re-apply feed-discovered version tail persisted from prior sessions.
        val saved = context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE)
            .getString("released_tail", null)?.split("|")?.filter { it.isNotBlank() }
        if (!saved.isNullOrEmpty()) k.mergeReleasedTail(saved)
        k
    }
    // VEGAS key catalog (classifier ground truth, §6b): null -> classifier off, rows unverified.
    // Heal with live tail — tags seen via GitHub feed that are newer than bundled catalog (heals "catalog behind").
    val vegasCatalog = remember {
        val cat = DXVKConfigDialog.loadVegasKeyCatalog(context)
        if (cat != null) {
            val tail = context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE)
                .getString("catalog_tail", null)?.split("|")?.filter { it.isNotBlank() }
            if (!tail.isNullOrEmpty()) cat.mergeTailTags(tail)
        }
        cat
    }
    val activeStockTag = remember(selectedStock, stockSources.value) {
        stockSources.value.firstOrNull { it.verName == selectedStock || it.displayLabel() == selectedStock }?.tag
    }
    // Coverage rule (STOCK rows only): installed tag missing from catalog (or no tag
    // recorded) -> "catalog behind build". Tail-healed tags count as covered.
    val catalogBehind = remember(vegasCatalog, selectedStock, activeStockTag) {
        vegasCatalog != null && selectedStock != null && (activeStockTag == null || !vegasCatalog.isCoveredOrTail(activeStockTag))
    }
    var showCatalogDialog by remember { mutableStateOf(false) }
    var showKeyDocSheet by remember { mutableStateOf(false) }
    // Fork-Feature filter persists across dialog opens (user request: toggle → OK → reopen
    // must stay filtered). Written through on every flip — cheap pref, no OK gate needed.
    val forkFilterPrefs = remember { context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE) }
    var forkFilter by remember { mutableStateOf(forkFilterPrefs.getBoolean("forkFilter", false)) }
    // §6a.6 schema-aware editor: wrong-family key awaiting the block-with-explanation dialog.
    var pendingSchemaBlock by remember { mutableStateOf<String?>(null) }
    // §6b.1 user-initiated "Check for new builds" (report only — observation, never mutation).
    var liveReport by remember { mutableStateOf<VegasLiveCheck.Report?>(null) }
    var liveChecking by remember { mutableStateOf(false) }

        // ---- Option B: "the selected path IS the live file" ----
    // Stock baseline: the parked stock file is read-only until the FIRST edit; that first
    // write creates the user's own sidecar <container>/vegas/configs/<tag>.user.conf which
    // becomes the live file (and the saved dxvkConfigFile pointer moves with it). Custom
    // selections are the live file from the start, edited in place. No active.conf.
    val stockPathForSelected = remember(selectedStock, stockSources.value) {
        selectedStock?.let { s ->
            stockSources.value.firstOrNull { it.verName == s || it.displayLabel() == s }?.file?.absolutePath
        }
    }
    // Sidecar lives inside the container so it survives WCP updates of the parked file.
    // Fix blue-button-not-staying: tag may be null when .provenance.json is missing (older installs).
    // Fall back to the stock verName (stripped) so the sidecar is always resolvable.
    val sidecarFallback = remember(selectedStock, stockSources.value) {
        stockSources.value.firstOrNull { it.verName == selectedStock || it.displayLabel() == selectedStock }
            ?.verName?.removePrefix("vegas-")?.substringBefore(" ·")
            ?: selectedStock?.removePrefix("vegas-")?.substringBefore(" ·")
    }
    val sidecarBase = remember(activeStockTag, sidecarFallback) {
        (activeStockTag?.takeIf { it.isNotBlank() } ?: sidecarFallback)?.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
    val sidecarPath = remember(containerRootDir, sidecarBase) {
        if (sidecarBase == null) null
        else {
            val dir = if (containerRootDir != null)
                java.io.File(java.io.File(containerRootDir, "vegas"), "configs")
            else
                java.io.File(context.filesDir, "vegas-defaults/configs")
            java.io.File(dir, sidecarBase + ".user.conf").absolutePath
        }
    }
    // Probe both tag-based and verName-based sidecar names so an older tag-sidecar is still found
    val sidecarExists = remember(toggleVersion, sidecarPath, containerRootDir, sidecarFallback, activeStockTag) {
        if (sidecarPath != null && java.io.File(sidecarPath).isFile) true
        else if (containerRootDir != null && sidecarFallback != null) {
            // legacy fallback: check the alternative naming
            val altBase = sidecarFallback.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (altBase != sidecarBase) java.io.File(java.io.File(java.io.File(containerRootDir, "vegas"), "configs"), altBase + ".user.conf").isFile
            else false
        } else false
    }
    // Resolve the actual existing sidecar file (prefer tag-based, fallback to verName-based)
    val resolvedSidecarPath = remember(sidecarPath, containerRootDir, sidecarFallback, toggleVersion) {
        if (sidecarPath != null && java.io.File(sidecarPath).isFile) sidecarPath
        else if (containerRootDir != null && sidecarFallback != null) {
            val alt = java.io.File(java.io.File(java.io.File(containerRootDir, "vegas"), "configs"), sidecarFallback.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".user.conf").absolutePath
            if (java.io.File(alt).isFile) alt else sidecarPath
        } else sidecarPath
    }
    val liveFile = remember(useDefaults, selectedStock, selectedCustom, stockPathForSelected, resolvedSidecarPath, sidecarExists) {
        when {
            useDefaults -> null
            selectedStock != null && sidecarExists && resolvedSidecarPath != null -> java.io.File(resolvedSidecarPath!!)
            selectedStock != null -> stockPathForSelected?.let { java.io.File(it) }
            selectedCustom != null -> java.io.File(selectedCustom!!)
            else -> null
        }
    }
    // "" = USE DEFAULTS (no file).
    val livePath = liveFile?.absolutePath ?: ""
    // Option B backups: .bak-* copies beside the CURRENT live file, newest first.
    val backupsList = remember(liveFile, toggleVersion) {
        val dir = liveFile?.parentFile
        val name = liveFile?.name
        if (dir == null || name == null || !dir.isDirectory) emptyList()
        else dir.listFiles { f -> f.isFile && f.name.startsWith(name + ".bak") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    // Config-file snapshot, read ONCE per source pick or per write (never re-read on the fly):
    // empty text = USE DEFAULTS; missing = live file not found on disk.
    val configSourceText = remember(useDefaults, liveFile, toggleVersion) {
        if (useDefaults || liveFile == null) ""
        else if (liveFile.isFile) runCatching { liveFile.readText() }.getOrDefault("")
        else ""
    }
    val configSourceMissing = remember(liveFile, useDefaults) {
        !useDefaults && liveFile != null && !liveFile.isFile
    }
    val configRows = remember(vegasKnowledge, configSourceText, selectedDxvk) {
        if (vegasKnowledge != null) vegasKnowledge.editRows(configSourceText, selectedDxvk)
        else VegasKeyKnowledge.editRowsUnclassified(configSourceText)
    }

    // §6c value editor ground truth: distinct enabled values per key across ALL installed
    // stock baseline files — the value-picker option pool. Boolean keys (only 0/1 values)
    // stay switch-driven. Nothing when no stock package is installed.
    val stockBaselineKeyValues = remember(stockSources.value, vegasKnowledge, selectedDxvk) {
        val m = mutableMapOf<String, MutableSet<String>>()
        for (src in stockSources.value) {
            val f = src.file
            if (!f.isFile) continue
            val rows = (vegasKnowledge?.editRows(runCatching { f.readText() }.getOrDefault(""), src.verName)
                ?: VegasKeyKnowledge.editRowsUnclassified(runCatching { f.readText() }.getOrDefault("")))
            for (r in rows) if (r.enabled && r.value.isNotEmpty()) m.getOrPut(r.key) { linkedSetOf() }.add(r.value)
        }
        m.mapValues { it.value.toList() }
    }
    // The SELECTED baseline's rows: value-picker reset target + pending-row source.
    val baselineRowsForSelected = remember(activeStockTag, vegasKnowledge, stockSources.value, selectedDxvk) {
        if (selectedStock == null) emptyList()
        else {
            val f = stockSources.value.firstOrNull { it.tag == activeStockTag }?.file
            if (f == null || !f.isFile) emptyList()
            else (vegasKnowledge?.editRows(runCatching { f.readText() }.getOrDefault(""), selectedDxvk)
                ?: VegasKeyKnowledge.editRowsUnclassified(runCatching { f.readText() }.getOrDefault("")))
        }
    }
    // Pending rows (stock editor only): baseline keys ABSENT from the active config —
    // switch OFF, "added on save" when enabled. Custom files are user-owned: never listed.
    val pendingRows = remember(baselineRowsForSelected, configRows) {
        val activeKeys = configRows.map { it.key }.toSet()
        baselineRowsForSelected.filter { it.key !in activeKeys }
    }
    // Keys whose active row differs from the selected baseline (value or comment state) —
    // "edited" mark + per-key reset in the value picker.
    val changedKeys = remember(configRows, baselineRowsForSelected, selectedStock) {
        if (selectedStock == null) emptySet()
        else {
            val base = baselineRowsForSelected.associateBy { it.key }
            configRows.filter { r ->
                val b = base[r.key]
                b != null && (b.value != r.value || b.enabled != r.enabled)
            }.map { it.key }.toSet()
        }
    }

    fun isBooleanKey(key: String): Boolean =
        stockBaselineKeyValues[key]?.isNotEmpty() == true && stockBaselineKeyValues[key]!!.all { it == "0" || it == "1" }

    // §tier: the vegas.forceTier value currently in the active config (3 = high, 2 = mid,
    // 1 = entry, 0 = auto, null = unset). Recomputed after every write (toggleVersion).
    val activeForceTier = remember(configRows, toggleVersion) {
        configRows.firstOrNull { it.key == "vegas.forceTier" }?.value?.toIntOrNull()
    }

    fun schemaName(s: VegasKeyCatalog.Schema?): String = when (s) {
        VegasKeyCatalog.Schema.SAREK -> "Sarek (dxvk.vegas.*)"
        VegasKeyCatalog.Schema.STAR -> "Star Engine (vegas.*)"
        null -> "unknown"
    }

    // Restore a .bak archive as the LIVE file: capture-once semantics — the backup is
    // CONSUMED (written into the live file, then deleted if it is the auto slot), so
    // nothing accumulates and the next edit recaptures fresh. Manual .bak-manual-N
    // copies survive restores: they are deliberate snapshots, not rolling state.
    fun restoreBackup(backup: java.io.File) {
        val target = liveFile ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (!target.isFile) return@withContext false
                val content = runCatching { backup.readText() }.getOrNull() ?: return@withContext false
                if (!runCatching { target.writeText(content) }.isSuccess) return@withContext false
                if (backup.name == target.name + ".bak") backup.delete()
                true
            }
            if (ok) { stockEdited = true; toggleVersion++ }
            else Toast.makeText(activity, "Failed to restore backup", Toast.LENGTH_SHORT).show()
        }
    }

    // Manual safety net for capture-once backups: an explicit user-taken snapshot.
    // Named <name>.bak-manual (-2, -3…) so it never collides with the auto slot,
    // shows up in the same Restore list, and survives restores and edits alike.
    fun manualBackup() {
        val target = liveFile ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (!target.isFile) return@withContext false
                var bak = java.io.File(target.absolutePath + ".bak-manual")
                var n = 2
                while (bak.isFile) { bak = java.io.File(target.absolutePath + ".bak-manual-" + n); n++ }
                runCatching { java.nio.file.Files.copy(target.toPath(), bak.toPath()) }.isSuccess
            }
            if (ok) toggleVersion++
            else Toast.makeText(activity, "Failed to create backup", Toast.LENGTH_SHORT).show()
        }
    }

    // §7 release notes: report-only fetch from the vegas-releases feed (the same source
    // the live-check uses), matched by the SELECTED version's tag, cached per session.
    // Falls back to the bundled per-build notes; hidden entirely when neither exists.
    fun openReleaseNotes() {
        val version = selectedDxvk.removePrefix("vegas-")
        val cached = notesCache
        if (cached != null && cached.first == version) {
            notesSource = "live"
            showNotes = true
            return
        }
        val bundled = VegasTierPresets.BUNDLED_NOTES[version]
        notesSource = if (bundled != null) "bundled" else "none"
        showNotes = true
        if (notesLoading) return
        notesLoading = true
        HttpUtils.download("https://api.github.com/repos/isygold/vegas-releases/releases") { body ->
            scope.launch {
                val parsed = runCatching {
                    val arr = JSONArray(body)
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val tag = o.optString("tag_name", "").removePrefix("vegas-")
                        val b = o.optString("body", "")
                        tag to b
                    }.firstOrNull { it.first == version }?.second
                        ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(8)
                }.getOrNull()
                if (parsed != null && parsed.isNotEmpty()) {
                    notesCache = version to parsed
                    notesSource = "live"
                }
                notesLoading = false
            }
        }
    }

    // §6b.1 user-initiated "Check for new builds" — report + heal catalog tail (so next open isn't "unverified").
    fun runLiveCheck() {
        if (liveChecking) return
        liveChecking = true
        HttpUtils.download("https://api.github.com/repos/isygold/vegas-releases/releases") { body ->
            try {
                scope.launch {
                    val catalogNewest = vegasCatalog?.newestTag()
                    val newestAt = catalogNewest?.let { vegasCatalog?.publishedAtOf(it) }
                    val report = VegasLiveCheck.check(body, activeStockTag, catalogNewest, newestAt)
                    liveReport = report
                    // heal: any newer tags seen live become covered
                    if (report.feedOk && report.newerTags.isNotEmpty()) {
                        val prefs = context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE)
                        val cur = prefs.getString("catalog_tail", "")?.split('|')?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
                        var added = false
                        for (t in report.newerTags) if (t.isNotBlank() && cur.add(t)) added = true
                        // also ensure installed tag itself is considered seen (covers hash-renamed installs)
                        if (report.installedTag != null && report.installedTag.isNotBlank() && cur.add(report.installedTag!!)) added = true
                        // cap growth so the persisted tail stays bounded
                        VegasKeyCatalog.capToMax(cur)
                        if (added) {
                            prefs.edit().putString("catalog_tail", cur.joinToString("|")).apply()
                            vegasCatalog?.mergeTailTags(cur.toList())
                        }
                    }
                    liveChecking = false
                }
            } catch (_: IllegalStateException) {
                // coroutine scope already disposed (user navigated away) — ignore
            }
        }
    }


    val pickCustomConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val path = InAppFilePicker.pickedPath(result.data)
            if (path != null) {
                selectedCustom = path
                selectedStock = null
                useDefaults = false
                if (path !in customEntries.value) customEntries.value = customEntries.value + path
            }
        }
    }

    // Comment/uncomment the exact config line. Option B: the selected path IS the live
    // file. Stock baselines stay pristine until the FIRST edit — that write creates the
    // user's own sidecar copy (<container>/vegas/configs/<tag>.user.conf) which becomes
    // the live file. Custom files are written in place from the start. No active.conf,
    // no import, no seed/switch decision rows. §6a.6: wrong-schema keys are BLOCKED
    // with an explanation before anything else (stock rows only — custom is user-owned).
    fun commitConfigWrite(isStockPath: Boolean, transform: (String) -> String?) {
        val target = liveFile ?: return
        // Use the resolved sidecar path (existing file) for final pointer; fall back to intended new sidecar
        val intendedSidecar = sidecarPath
        val effectiveSidecar = resolvedSidecarPath ?: intendedSidecar
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (!target.isFile) return@withContext false
                val text = runCatching { target.readText() }.getOrNull() ?: return@withContext false
                val next = transform(text) ?: return@withContext false
                // Capture-once backup: create the auto slot ONLY when it does not exist
                // yet (first edit after a fresh selection/restore). Never .bak-2, -3…
                // from repeat edits or new sessions — the slot persists until consumed
                // by a restore. Manual "Backup now" copies use .bak-manual-N instead.
                val autoBak = java.io.File(target.absolutePath + ".bak")
                if (!autoBak.isFile) {
                    runCatching { java.nio.file.Files.copy(target.toPath(), autoBak.toPath()) }
                }
                // Ensure the target's parent directory exists (fallback sidecar or custom path).
                if (!target.parentFile.isDirectory) target.parentFile.mkdirs()
                // Stock + pristine baseline: materialize the sidecar with the edited content.
                // From then on the sidecar IS the live file (pointer saved on OK).
                if (isStockPath && !sidecarExists && effectiveSidecar != null) {
                    val s = java.io.File(effectiveSidecar!!)
                    if (!s.parentFile.exists() && !s.parentFile.mkdirs()) return@withContext false
                    if (!s.parentFile.isDirectory) return@withContext false
                    runCatching { s.writeText(next) }.isSuccess
                } else {
                    runCatching { target.writeText(next) }.isSuccess
                }
            }
            if (ok) {
                if (isStockPath) stockEdited = true
                toggleVersion++
                // The game reads dxvkConfigFile at launch — point it at the file we just
                // wrote NOW, not on OK. Dismissing the sheet must never orphan toggles.
                val finalPath = if (isStockPath && !sidecarExists && effectiveSidecar != null) effectiveSidecar!!
                                else target.absolutePath
                onLivePointerChanged(finalPath)
            }
            else Toast.makeText(activity, "Failed to update config file", Toast.LENGTH_SHORT).show()
        }
    }

    // Comment/uncomment the exact config line (Option B direct-write; §6a.6 wrong-schema
    // block). Pending (absent) keys: enabling appends the line with the row's stock
    // default; disabling an absent key is a structural no-op.
    fun applyToggle(key: String, value: String, enable: Boolean) {
        if (useDefaults || liveFile == null) return
        val isStockPath = selectedStock != null
        // §6a.6 schema-aware editor: block BEFORE the write — a wrong-family key can never
        // be meaningfully applied to this build's schema. Stock rows only.
        if (isStockPath && activeStockTag != null && vegasCatalog != null && vegasCatalog.isWrongFamily(key, activeStockTag)) {
            pendingSchemaBlock = key
            return
        }
        commitConfigWrite(isStockPath) { text ->
            VegasKeyKnowledge.toggleLine(text, key, enable)
                ?: if (enable) VegasKeyKnowledge.setLine(text, key, value) else text
        }
    }

    // Value edit (non-boolean keys via the value picker): same pipeline; setLine preserves
    // comment state and appends an enabled line for absent (pending) keys.
    fun applyValue(key: String, value: String) {
        if (useDefaults || liveFile == null) return
        val isStockPath = selectedStock != null
        if (isStockPath && activeStockTag != null && vegasCatalog != null && vegasCatalog.isWrongFamily(key, activeStockTag)) {
            pendingSchemaBlock = key
            return
        }
        commitConfigWrite(isStockPath) { text ->
            VegasKeyKnowledge.setLine(text, key, value)
        }
    }

    // (+) Add a brand-new key=value line to the live file (stock sidecar OR custom —
    // both are plain live files under Option B). setLine appends an enabled line at
    // the end when the key is absent; an existing key gets its value updated in
    // place instead of duplicating. Same guards as applyValue.
    fun applyAddKey(key: String, value: String) {
        if (useDefaults || liveFile == null) return
        val isStockPath = selectedStock != null
        if (isStockPath && activeStockTag != null && vegasCatalog != null && vegasCatalog.isWrongFamily(key, activeStockTag)) {
            pendingSchemaBlock = key
            return
        }
        commitConfigWrite(isStockPath) { text ->
            VegasKeyKnowledge.setLine(text, key, value)
        }
    }

    // Per-key delete: drops the key's line entirely from the live file (stock sidecar
    // OR custom — both plain live files under Option B). Same guards as applyValue so a
    // gated (wrong-schema) key can't be silently dropped without the user seeing why.
    fun applyDeleteKey(key: String) {
        if (useDefaults || liveFile == null) return
        val isStockPath = selectedStock != null
        if (isStockPath && activeStockTag != null && vegasCatalog != null && vegasCatalog.isWrongFamily(key, activeStockTag)) {
            pendingSchemaBlock = key
            return
        }
        commitConfigWrite(isStockPath) { text ->
            VegasKeyKnowledge.removeLine(text, key)
        }
    }

    val pickVegasLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = InAppFilePicker.pickedUri(result.data)
            if (uri != null) {
                isProcessing = true
                installContentFromUri(activity, uri) { success ->
                    if (success) {
                        Toast.makeText(activity, "VEGAS version installed", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val cm = ContentsManager(context)
                                cm.syncContents()
                                val newVersions = if (isVegas)
                                    DXVKConfigDialog.loadVegasVersionList(context, cm)
                                else
                                    DXVKConfigDialog.loadDxvkVersionList(context, cm, isArm64EC)
                                withContext(Dispatchers.Main) {
                                    allDxvkVersions.value = newVersions
                                }
                            }
                        }
                    }
                    isProcessing = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isVegas) "VEGAS ${stringResource(R.string.configuration)}" else "DXVK ${stringResource(R.string.configuration)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                // §VEGAS version management (VEGAS mode): top section, inline action cluster
                // at the right edge — download gear, delete, install-from-file. Non-VEGAS
                // keeps the original DXVK-first order below.
                // §loglevel: VEGAS ignores the config-file log-level key; it must be an env var.
                Text(
                    stringResource(R.string.vegas_config_loglevel_envvar_note),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.merge(TextStyle(fontWeight = FontWeight.Bold))
                )
                Spacer(Modifier.height(8.dp))
                if (isVegas) {
                    SectionLabel("VEGAS VERSION")
                    if (filteredDxvk.isEmpty()) {
                        Text(
                            "no VEGAS build installed — download one via the sheet",
                            color = MaterialTheme.colorScheme.outline,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabeledDropdown(
                            "", filteredDxvk, selectedDxvk, { selectedDxvk = it },
                            modifier = Modifier.weight(1f)
                        )
                        ContentInstallGear(onDownloadFile = onDownloadDxvk)
                        IconButton(
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val cm = ContentsManager(context)
                                            cm.syncContents()
                                            // Match the exact "vegas-<selected>" name OR any hash-suffixed
                                            // variant ("vegas-2.4.1-cf04e7f" vs "-3137660" asset renames) —
                                            // exact-equality silently no-oped for renamed installs.
                                            val expectedName = "vegas-$selectedDxvk"
                                            val profile = cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VEGAS)
                                                .firstOrNull {
                                                    it.verName == expectedName ||
                                                        it.verName.removePrefix("vegas-") == selectedDxvk ||
                                                        it.verName == selectedDxvk
                                                }
                                            if (profile != null) {
                                                cm.removeContent(profile)
                                                cm.syncContents()
                                                val newVersions = DXVKConfigDialog.loadVegasVersionList(context, cm)
                                                withContext(Dispatchers.Main) {
                                                    allDxvkVersions.value = newVersions
                                                    if (selectedDxvk !in newVersions) {
                                                        selectedDxvk = newVersions.firstOrNull() ?: selectedDxvk
                                                    }
                                                    Toast.makeText(activity, "VEGAS version deleted", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(activity, "No installed VEGAS version to delete", Toast.LENGTH_SHORT).show()
        }
    }
}
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(activity, "ERROR: Failed to delete — ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) { isProcessing = false }
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(
                            onClick = { pickVegasLauncher.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.WCP, "Select VEGAS package")) },
                            enabled = !isProcessing,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Install from file", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (isProcessing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    // §7 release-notes chip: visible when notes exist for the selected version
                    // (live-cached or bundled); ● live / ◐ bundled marker.
                    val verKey = selectedDxvk.removePrefix("vegas-")
                    if (notesCache?.first == verKey || VegasTierPresets.BUNDLED_NOTES[verKey] != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { openReleaseNotes() }) {
                                Text("What's new in $selectedDxvk", style = MaterialTheme.typography.bodySmall)
                            }
                            val live = notesCache?.first == verKey
                            Text(
                                if (live) "● live" else "◐ bundled",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabeledDropdown(
                        stringResource(R.string.vkd3d_version), vkd3dVersions.value, selectedVkd3d, { selectedVkd3d = it },
                        modifier = Modifier.weight(1f)
                    )
                    ContentInstallGear(onDownloadFile = onDownloadVkd3d)
                }
                Spacer(Modifier.height(8.dp))
                if (!isVegas) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabeledDropdown(
                            stringResource(R.string.dxvk_version),
                            filteredDxvk, selectedDxvk, { selectedDxvk = it },
                            modifier = Modifier.weight(1f)
                        )
                        ContentInstallGear(onDownloadFile = onDownloadDxvk)
                    }
                    if (isProcessing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (dxvkType != DXVKConfigDialog.DXVK_TYPE_NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = asyncEnabled, onCheckedChange = { asyncEnabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Async")
                    }
                }
                if (dxvkType == DXVKConfigDialog.DXVK_TYPE_GPLASYNC) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = asyncCacheEnabled, onCheckedChange = { asyncCacheEnabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Async Cache")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LabeledDropdown(stringResource(R.string.frame_rate), framerateEntries, selectedFramerate, { selectedFramerate = it })
                Spacer(Modifier.height(8.dp))
                SectionLabel("TEXTURE FILTERING")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown("Anisotropic filtering", anisotropyLabels, selectedAnisotropy, { selectedAnisotropy = it },
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { textureHelpRes = R.string.help_anisotropic_filtering }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown("Texture sharpness", lodBiasLabels, selectedLodBias, { selectedLodBias = it },
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { textureHelpRes = R.string.help_texture_sharpness }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    "DirectX 9-11 games only. Applies the next time the game starts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                SectionLabel("API FEATURE LEVEL")
                LabeledDropdown("", featureLevelEntries, selectedFeatureLevel, { selectedFeatureLevel = it })
                Spacer(Modifier.height(8.dp))
                LabeledDropdown("DDraw Wrapper", ddraEntries, selectedDdra, { selectedDdra = it })
                // D7VK is a catalog-backed component: when it's the chosen DDraw wrapper, offer a
                // version dropdown ("Bundled (default)" + any downloaded profiles) and a cloud button
                // to fetch more — mirroring the DXVK/VKD3D version UI above.
                if (StringUtils.parseIdentifier(selectedDdra) == "d7vk") {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabeledDropdown(
                            "D7VK Version", d7vkVersions.value, selectedD7vk, { selectedD7vk = it },
                            modifier = Modifier.weight(1f)
                        )
                        ContentInstallGear(onDownloadFile = onDownloadD7vk)
                    }
                }
                // §tier: FAQ performance tiers (docs/vegas_faq.html #11) — GPU detection,
                // staged selection → preview → apply as vegas.forceTier through the normal
                // config write pipeline. Rendered above the Config section; writing to a
                // file-based source only (defaults has no file, so Apply is gated).
                if (isVegas) {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("PERFORMANCE TIER")
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            // effectiveTier = staged choice if present, else persisted value (0/null → Auto). Fixes
                            // "blue stays on Auto after reopen" — chips now reflect what is actually applied.
                            val staged = tierChoice
                            val effectiveTier: Int? = staged ?: activeForceTier?.let { if (it == 0) null else it }
                            val tierForPreview = staged
                            Text(
                                when {
                                    gpuModel == null -> "GPU model unreadable — tier is manual"
                                    detectedTier != null -> "$gpuModel · auto Tier $detectedTier"
                                    else -> "GPU: $gpuModel — no tier suggestion"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = effectiveTier == null,
                                    onClick = { tierChoice = null },
                                    label = { Text("Auto${if (detectedTier != null) " · T$detectedTier" else ""}") }
                                )
                                VegasTierPresets.TIERS.forEach { t ->
                                    FilterChip(
                                        selected = effectiveTier == t.number,
                                        onClick = { tierChoice = t.number },
                                        label = { Text(t.label) }
                                    )
                                }
                            }
                            val p = tierForPreview?.let { VegasTierPresets.PARAMS[it] }
                            if (tierForPreview != null && p != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Draw threshold ${p.drawThreshold} (D3D9 ${p.drawThresholdD3D9}) · HAAE pacing ${p.haaePacing}ms · governor cap ${p.governorCap} · shader zero-init ${p.shaderZeroInit} · frame-gen ${p.frameGen}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "will write: vegas.forceTier = $tierForPreview",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(modifier = Modifier.padding(top = 4.dp)) {
                                    TextButton(
                                        enabled = !useDefaults,
                                        onClick = { applyValue("vegas.forceTier", tierForPreview.toString()); tierChoice = null }
                                    ) { Text("Apply tier", style = MaterialTheme.typography.bodySmall) }
                                    if (useDefaults) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "pick a config source first — defaults has no file to write to",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            } else {
                                val applied = activeForceTier
                                if (applied != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        if (applied == 0) "Applied — auto (vegas.forceTier = 0)"
                                        else "Applied — vegas.forceTier = $applied",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(modifier = Modifier.padding(top = 4.dp)) {
                                        TextButton(
                                            enabled = !useDefaults,
                                            onClick = { applyValue("vegas.forceTier", "0"); tierChoice = null }
                                        ) { Text("Reset to auto", style = MaterialTheme.typography.bodySmall) }
                                        if (useDefaults) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "pick a config source first",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Mali hint: non-Adreno device — the experimental Mali "Wrapper + compat +
                    // bcn" driver (with the relaxed DXVK list) lives in the driver settings.
                    if (gpuModel != null && !gpuModel.contains("adreno", ignoreCase = true)) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Experimental — Mali driver", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Pair the Mali 'Wrapper + compat + bcn' driver with the relaxed DXVK list (all DXVK versions) in the container's driver settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (isVegas) {
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("CONFIG")
                    // Guidance for the three config audiences (new / customizer / file-savvy).
                    Text(
                        "New to configs? Grab a Stock config via ⬇, then pick it below. " +
                        "Want it your way? Edit anything in a stock config — changes auto-save as your own copy, the original stays untouched. " +
                        "Prefer managing files yourself? \"Custom config file\" points at any .conf (the built-in way of doing DXVK_CONFIG_FILE). " +
                        "Heads-up: builds SILENTLY IGNORE keys they don't recognise — if an added key does nothing, it's either not supported by that build or has a typo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    // ---- config source: two-source model (stock/custom), one ACTIVE ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = useDefaults, onCheckedChange = { useDefaults = it }, modifier = Modifier.height(32.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Use defaults (no config file)", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!useDefaults) {
                        // Option B: the selected path IS the live file — no adoption banner,
                        // no seed/switch decisions. Legacy containers' parked stock files are
                        // simply live files (their content shows and applies).
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            LabeledDropdown(
                                "Stock config (per version)",
                                stockSources.value.map { it.displayLabel() },
                                selectedStock ?: "",
                                { s -> selectedStock = s; selectedCustom = null; useDefaults = false },
                                modifier = Modifier.weight(1f)
                            )
                            // Inline ⬇ hands off to the host-level config-download sheet
                            // (same layering as the build sheets: dialog hides, sheet shows).
                            if (isVegas) {
                                IconButton(onClick = onOpenConfigDownload) {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = "Download stock configs",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (stockSources.value.isEmpty()) {
                            Text(
                                "no installed VEGAS package ships a config — install one via the sheet",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // Option B stock edit info: pristine baselines stay read-only; the
                        // FIRST edit creates the user's own sidecar which takes over as the
                        // live file (and the saved pointer).
                        if (selectedStock != null && selectedCustom == null && stockPathForSelected != null && !configSourceMissing) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (sidecarExists)
                                    "Your edits live in your own copy — the stock version stays pristine."
                                else
                                    "Read-only until the first edit — changes create your own copy.",
                                color = if (sidecarExists) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LabeledDropdown(
                            "Custom config file",
                            customEntries.value + BROWSE_CONFIG_MARKER,
                            selectedCustom ?: "",
                            { c ->
                                if (c == BROWSE_CONFIG_MARKER) {
                                    pickCustomConfigLauncher.launch(
                                        InAppFilePicker.buildIntent(context, emptyArray(), "Select config file")
                                    )
                                } else {
                                    selectedCustom = c; selectedStock = null; useDefaults = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (livePath.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "📍 $livePath",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (configSourceMissing) {
                            Spacer(Modifier.height(4.dp))
                            Text("not found: $livePath", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            // #5 ghost → one-tap remove from dropdown
                            if (selectedCustom != null) {
                                TextButton(onClick = {
                                    customEntries.value = customEntries.value.filter { it != selectedCustom }
                                    selectedCustom = null
                                    if (selectedStock == null) useDefaults = true
                                }) { Text("Remove from list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        // Option B backups: .bak-* beside the CURRENT live file. Always visible
                        // once a live file exists — an empty list reads as "no backups yet" —
                        // so the restore entry point can never be hidden by state (user's #5).
                        if (liveFile != null && !configSourceMissing) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (backupsList.isEmpty())
                                        "No backups yet — the first edit keeps a copy of the current file."
                                    else
                                        "Backups: ${backupsList.size} — restore a previously saved state",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                // Manual safety net for capture-once backups: an explicit
                                // snapshot taken by the user, never overwritten automatically.
                                if (liveFile != null && liveFile.isFile) {
                                    TextButton(onClick = { manualBackup() }) {
                                        Text("Backup now", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (backupsList.isNotEmpty()) {
                                    TextButton(onClick = { showBackups = true }) { Text("Restore…", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        if (!configSourceMissing && configSourceText.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = forkFilter, onCheckedChange = { forkFilter = it; forkFilterPrefs.edit().putBoolean("forkFilter", it).apply() }, modifier = Modifier.height(32.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fork-feature filter", style = MaterialTheme.typography.bodySmall)
                            }
                            if (forkFilter) {
                                // Combined semantics: fork-only view AND unavailable
                                // (LATE/REMOVED) keys hidden — count what actually shows.
                                val shownCount = configRows.count { row ->
                                    val k = vegasKnowledge
                                    k?.isForkKey(row.key) == true && !k.isGated(row.key, selectedDxvk)
                                }
                                Text(
                                    "Fork features only: $shownCount of ${configRows.size} keys shown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = { showAddKey = true; addKeyDraft = ""; addValueDraft = "" }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add key", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            // #7 & #10 helpers: pre-filter + typo suggestion (conflict-free, no LazyColumn nesting)
                            val visibleRows = remember(configRows, forkFilter, selectedDxvk, vegasKnowledge) {
                                configRows.filter { r ->
                                    if (forkFilter && vegasKnowledge?.isForkKey(r.key) != true) false
                                    else if (forkFilter && vegasKnowledge != null && vegasKnowledge.isGated(r.key, selectedDxvk)) false
                                    else true
                                }
                            }
                            fun levenshtein(a: String, b: String): Int {
                                val dp = Array(a.length + 1) { IntArray(b.length + 1) }
                                for (i in 0..a.length) dp[i][0] = i
                                for (j in 0..b.length) dp[0][j] = j
                                for (i in 1..a.length) for (j in 1..b.length) {
                                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                                    dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                                }
                                return dp[a.length][b.length]
                            }
                            fun typoFor(key: String): String? {
                                if (vegasCatalog == null) return null
                                val bucket = vegasCatalog.classify(key, activeStockTag)
                                if (bucket != VegasKeyCatalog.Bucket.NOWHERE) return null
                                var best: String? = null
                                var bestDist = 3
                                for (cand in vegasCatalog.allKeys()) {
                                    val d = levenshtein(key, cand)
                                    if (d < bestDist) { bestDist = d; best = cand }
                                }
                                return if (bestDist in 1..2) best else null
                            }
                            // Gated keys are now *shown* as ineffective — no auto-off (per your "stop").
                            // They appear grey `needs 2.8.0+ · ineffective` with ○──, you decide to Add/Remove.
                            // No LaunchedEffect auto-mutation, Custom and Stock both stay as you left them.
                            // Brighten primary 40% toward white so changed-value text is readable on
                            // dark surfaces (AMOLED pure-black gives ~4.6:1 for #0055FF at bodySmall).
                            val changedTextColor = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.40f)
                            visibleRows.forEach { row ->
                                val gated = vegasKnowledge != null && vegasKnowledge.isGated(row.key, selectedDxvk)
                                val baseBadge = vegasKnowledge?.badgeFor(row.key, selectedDxvk) ?: "unclassified"
                                // Bucket vocabulary describes VEGAS stock configs; a custom (user-owned) file is
                                // by definition not a VEGAS build — suffixes would read as warnings
                                // about something the user did deliberately. Stock-only.
                                // #1 fix: shorten chain so badge doesn't overflow on small screens
                                val bucketPart = if (vegasCatalog != null && selectedStock != null)
                                    when (vegasCatalog.classify(row.key, activeStockTag)) {
                                        VegasKeyCatalog.Bucket.IN_BUILD -> ""
                                        VegasKeyCatalog.Bucket.OTHER_BUILD -> if (catalogBehind) "" else " · other"
                                        VegasKeyCatalog.Bucket.UPSTREAM -> " · upstream"
                                        VegasKeyCatalog.Bucket.NOWHERE -> " · new"
                                    }
                                else ""
                                // cap length so 90-key screens don't wrap badly; UNKNOWN already user-friendly
                                val badgeRaw = baseBadge + bucketPart + if (catalogBehind) " · unverified" else ""
                                val badge = if (badgeRaw.length > 48) badgeRaw.take(45) + "…" else badgeRaw
                                val changed = selectedStock != null && row.key in changedKeys
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Switch(
                                        checked = row.enabled,
                                        onCheckedChange = { applyToggle(row.key, row.value, it) },
                                        modifier = Modifier.height(32.dp).width(48.dp)
                                    )
                                    Text(
                                        row.key,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (gated) MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                                 else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isBooleanKey(row.key)) {
                                        // Boolean keys stay switch-driven; the value is the comment state.
                                        Text(
                                            row.value,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (changed) changedTextColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        // Non-boolean keys open the value picker (stock vocabulary + custom).
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .heightIn(min = 40.dp)
                                                .clickable(enabled = !gated) {
                                                    valuePickerRow = row
                                                    customValueDraft = row.value
                                                }
                                        ) {
                                            Text(
                                                row.value,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (changed) changedTextColor
                                                        else if (gated) MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            if (!gated) Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (!gated) IconButton(
                                        onClick = { pendingDeleteKey = row.key },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete key ${row.key}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                // #10 typo inline suggestion (only for NOWHERE bucket, distance ≤2)
                                typoFor(row.key)?.let { sug ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 48.dp)) {
                                        Text("Typo? Did you mean $sug", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                        TextButton(onClick = {
                                            // Fix: write correct key with same value, then delete typo key
                                            applyValue(sug, row.value)
                                            // delete old typo after a short delay so sidecar write settles
                                            scope.launch { kotlinx.coroutines.delay(300); applyDeleteKey(row.key) }
                                        }) { Text("Fix", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                                Text(badge, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            // §6c pending rows (stock editor only): baseline keys ABSENT from the
                            // active config — #4 fix: Add button not inverted Switch OFF.
                            if (selectedStock != null && pendingRows.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("New in this baseline — tap Add to insert", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                pendingRows.forEach { row ->
                                    val gated = vegasKnowledge != null && vegasKnowledge.isGated(row.key, selectedDxvk)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(
                                            enabled = !gated,
                                            onClick = { applyToggle(row.key, row.value, true) },
                                            modifier = Modifier.height(32.dp)
                                        ) { Text("Add", style = MaterialTheme.typography.bodySmall) }
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            row.key,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (gated) MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                                     else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isBooleanKey(row.key)) {
                                            Text(row.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .heightIn(min = 40.dp)
                                                    .clickable(enabled = !gated) {
                                                        valuePickerRow = row
                                                        customValueDraft = row.value
                                                    }
                                            ) {
                                                Text(row.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                                if (!gated) Icon(
                                                    Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text("missing — will be added as ${row.key} = ${row.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    // Knowledge footer: provenance + state of the data layer
                    Spacer(Modifier.height(6.dp))
                    val footerText = if (vegasKnowledge != null)
                        "knowledge: fork ${vegasKnowledge.forkBuild()} · ${vegasKnowledge.generated()}"
                    else
                        "knowledge data unavailable — showing keys unclassified"
                    Text(footerText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val catalogFooter = when {
                        vegasCatalog == null -> "catalog unavailable — classifier off, rows marked unverified"
                        selectedCustom != null -> "catalog: newest ${vegasCatalog.newestTag()} · ${vegasCatalog.generatedAt()} — classifier applies to stock configs"
                        activeStockTag == null -> "catalog: newest ${vegasCatalog.newestTag()} · ${vegasCatalog.generatedAt()} — no stock source selected"
                        catalogBehind -> "catalog behind build — key classes unverified (newest known: ${vegasCatalog.newestTag()})"
                        else -> "catalog: covered · newest ${vegasCatalog.newestTag()} · ${vegasCatalog.generatedAt()}"
                    }
                    Text(catalogFooter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showKeyDocSheet = true }) {
                            Icon(Icons.Filled.Book, contentDescription = "Config key reference", tint = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { showCatalogDialog = true }) { Text("Check catalog", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (showCatalogDialog) {
                        AlertDialog(
                            onDismissRequest = { showCatalogDialog = false },
                            title = { Text("VEGAS key catalog") },
                            text = {
                                Column {
                                    Text("generated ${vegasCatalog?.generatedAt() ?: "n/a"} · upstream ${vegasCatalog?.upstreamSource() ?: "n/a"} (${vegasCatalog?.upstreamFetchedAt() ?: "n/a"})")
                                    Spacer(Modifier.height(4.dp))
                                    vegasCatalog?.knownTags()?.forEach { t ->
                                        val st = vegasCatalog.stateOf(t)
                                        Text("$t — ${st?.name?.lowercase()?.replace('_', '-') ?: "?"}")
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("Updated at build time (assistant-side maintenance).\nCheck for new builds")
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(onClick = { runLiveCheck() }, enabled = !liveChecking) {
                                        Text(if (liveChecking) "Checking…" else "Check for new builds", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showCatalogDialog = false }) { Text("OK") } }
                        )
                    }
                    if (showKeyDocSheet) {
                        VGlossarySheet(
                            onDismiss = { showKeyDocSheet = false },
                            vegasCatalog = vegasCatalog,
                            installedTag = activeStockTag,
                        )
                    }
                    // §6b.1 report dialog: observation only, zero writes. Shown regardless of
                    // the catalog dialog's own visibility so a report survives its dismissal.
                    liveReport?.let { r ->
                        AlertDialog(
                            onDismissRequest = { liveReport = null },
                            title = { Text("VEGAS new-build check") },
                            text = {
                                Column {
                                    if (!r.feedOk) {
                                        Text("Could not reach the release feed (network or API failure).")
                                        Spacer(Modifier.height(4.dp))
                                        Text("The bundled catalog is unchanged; keys for unknown builds stay 'unverified'.")
                                    } else {
                                        Text("Catalog newest: ${r.catalogNewestTag ?: "?"} (${r.catalogNewestAt.ifEmpty { "?" }}).")
                                        Spacer(Modifier.height(4.dp))
                                        if (r.newerCount == 0) {
                                            Text("No newer releases found.")
                                        } else {
                                            Text("${r.newerCount} newer release(s) found" +
                                                    (if (r.newBuildCount > 0) " — $r.newBuildCount stable" else " (all prerelease)") + ":")
                                            r.newerTags.forEach { t -> Text("· $t", style = MaterialTheme.typography.bodySmall) }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            if (r.installedFoundLive) "Your installed build is still listed upstream."
                                            else "Your installed build is not in the current release list."
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text("This check only reports — no writes, no catalog change. Download and classification still use the existing flows; the catalog asset updates at build time.")
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { liveReport = null }) { Text("OK") } }
                        )
                    }
                    // Only meaningful while a config file is actually in play — hidden under
                    // "Use defaults" (no file to be edited).
                    if (stockEdited && !useDefaults) {
                        Text(
                            if (sidecarExists) "Edited · yours now — saved to your own copy"
                            else "Edited · saved to the live config file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // §7 release-notes dialog: live or bundled notes for the selected version. Observation
                    // only — the fetch never writes anything.
                    if (showNotes) {
                        val verKey = selectedDxvk.removePrefix("vegas-")
                        val notes = notesCache?.takeIf { it.first == verKey }?.second
                            ?: VegasTierPresets.BUNDLED_NOTES[verKey]
                        AlertDialog(
                            onDismissRequest = { showNotes = false },
                            title = { Text("What's new — $verKey") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    if (notes.isNullOrEmpty()) {
                                        Text(if (notesLoading) "Fetching notes…" else "No release notes for this version.")
                                    } else {
                                        notes.forEach { n -> Text("· $n", style = MaterialTheme.typography.bodySmall) }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (notesSource == "live") "Fetched from the vegas-releases feed."
                                        else if (notesSource == "bundled") "Bundled with the app (offline)."
                                        else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            confirmButton = { TextButton(onClick = { showNotes = false }) { Text("OK") } }
                        )
                    }
                    // Option B backup picker: newest-first list of .bak archives beside the live
                    // file; tapping one opens the danger-confirm (the restore itself backs
                    // up the current state first).
                    if (showBackups) {
                        AlertDialog(
                            onDismissRequest = { showBackups = false },
                            title = { Text("Restore a backup") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    if (backupsList.isEmpty()) {
                                        Text("No backups yet — they appear after the first edit to this config file.")
                                    }
                                    backupsList.forEach { b ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            TextButton(
                                                onClick = { restoreTarget = b; showBackups = false },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    "${b.name} · ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(b.lastModified())}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            IconButton(onClick = {
                                                if (b.delete()) toggleVersion++
                                            }) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Delete backup",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showBackups = false }) { Text("Close") } }
                        )
                    }
                    restoreTarget?.let { backup ->
                        AlertDialog(
                            onDismissRequest = { restoreTarget = null },
                            title = { Text("Restore this backup?") },
                            text = {
                                Text("The live config file will be replaced by '${backup.name}'. The current state is backed up first — nothing is lost.")
                            },
                            confirmButton = {
                                TextButton(onClick = { restoreBackup(backup); restoreTarget = null }) { Text("Restore") }
                            },
                            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text(stringResource(android.R.string.cancel)) } }
                        )
                    }
                    // §6c value picker: tap a non-boolean key's value to pick from the stock vocabulary
                    // (current file value first), the selected baseline's default (reset), or a
                    // custom string. Value writes go through the same pipeline as toggles.
                    valuePickerRow?.let { row ->
                        val baseline = baselineRowsForSelected.firstOrNull { it.key == row.key }
                        AlertDialog(
                            onDismissRequest = { valuePickerRow = null },
                            title = { Text(row.key) },
                            confirmButton = {
                                TextButton(onClick = { if (customValueDraft.isNotBlank()) applyValue(row.key, customValueDraft.trim()); valuePickerRow = null }) { Text("Done") }
                            },
                            text = {
                                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                    Text(
                                        "Values used in stock configs — pick one, reset to stock, or type your own.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val opts = linkedSetOf<String>()
                                    if (row.value.isNotEmpty()) opts.add(row.value)
                                    stockBaselineKeyValues[row.key].orEmpty().forEach { opts.add(it) }
                                    opts.forEach { v ->
                                        TextButton(
                                            onClick = { applyValue(row.key, v); customValueDraft = v },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text(v, style = MaterialTheme.typography.bodySmall) }
                                    }
                                    if (baseline != null && (baseline.value != row.value || baseline.enabled != row.enabled)) {
                                        TextButton(
                                            onClick = { applyValue(row.key, baseline.value); customValueDraft = baseline.value },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Reset to stock (${baseline.value})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = customValueDraft,
                                        onValueChange = { customValueDraft = it },
                                        label = { Text("Custom value") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(modifier = Modifier.padding(top = 6.dp)) {
                                        TextButton(
                                            enabled = customValueDraft.isNotBlank(),
                                            onClick = { applyValue(row.key, customValueDraft.trim()) }
                                        ) { Text("Apply") }
                                    }
                                }
                            },
                            dismissButton = { TextButton(onClick = { valuePickerRow = null }) { Text(stringResource(android.R.string.cancel)) } }
                        )
                    }
                    // glass delete confirmation: translucent card over a light scrim (config dialog shows through)
                    pendingDeleteKey?.let { delKey ->
                        Dialog(onDismissRequest = { pendingDeleteKey = null }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                            ) {
                                Card(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(24.dp)
                                        .fillMaxWidth(0.92f),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            "Remove key?",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Are you sure you want '$delKey' removed? You can get it back via Backup → Restore or Add key.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { pendingDeleteKey = null }) {
                                                Text("Cancel")
                                            }
                                            Button(
                                                onClick = {
                                                    applyDeleteKey(delKey)
                                                    pendingDeleteKey = null
                                                    Toast.makeText(activity, "Removed $delKey — Restore to undo", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // §6a.6 schema block: the key belongs to the OTHER line's schema. Nothing
                    // is written — no decision row, no backup, just the explanation.
                    pendingSchemaBlock?.let { key ->
                        val keyFam = vegasCatalog?.familyOf(key)
                        val instFam = activeStockTag?.let { vegasCatalog?.schemaFamilyOf(it) }
                        val prefix = when (keyFam) {
                            VegasKeyCatalog.Schema.SAREK -> "dxvk.vegas."
                            VegasKeyCatalog.Schema.STAR -> if (key == "dxvk.enableStarProfile") key else "vegas."
                            null -> "?"
                        }
                        AlertDialog(
                            onDismissRequest = { pendingSchemaBlock = null },
                            title = { Text("Key not applicable to this build's schema") },
                            text = {
                                Text(
                                    "$key belongs to the ${schemaName(keyFam)} schema ($prefix…).\n\n" +
                                    "This build (${activeStockTag ?: "unknown"}) uses the ${schemaName(instFam)} schema — " +
                                    "the option cannot be applied and would be ignored."
                                )
                            },
                            confirmButton = { TextButton(onClick = { pendingSchemaBlock = null }) { Text("Got it") } }
                        )
                    }
                    // (+) add-key dialog: freeform key/value appended to the live file.
                    // Works for stock (writes the sidecar) and custom alike; an existing
                    // key updates in place instead of duplicating (setLine semantics).
                    if (showAddKey) {
                        val keyValid = VegasKeyKnowledge.isValidConfigKey(addKeyDraft.trim())
                        AlertDialog(
                            onDismissRequest = { showAddKey = false },
                            title = { Text("Add config entry") },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = addKeyDraft,
                                        onValueChange = { addKeyDraft = it },
                                        label = { Text("Key (e.g. dxvk.maxFrameLatency)") },
                                        singleLine = true,
                                        isError = addKeyDraft.isNotBlank() && !keyValid,
                                        supportingText = {
                                            if (addKeyDraft.isNotBlank() && !keyValid) {
                                                Text("Not a valid config key — use a dotted name or ENV_STYLE caps")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = addValueDraft,
                                        onValueChange = { addValueDraft = it },
                                        label = { Text("Value") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = addKeyDraft.isNotBlank() && addValueDraft.isNotBlank() && keyValid,
                                    onClick = {
                                        applyAddKey(addKeyDraft.trim(), addValueDraft.trim())
                                        showAddKey = false
                                    }
                                ) { Text("Add") }
                            },
                            dismissButton = { TextButton(onClick = { showAddKey = false }) { Text(stringResource(android.R.string.cancel)) } }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cfg = DXVKConfigDialog.parseConfig(initialConfig)
                cfg.put("version", selectedDxvk)
                cfg.put("framerate", StringUtils.parseNumber(selectedFramerate))
                cfg.put("async", if (asyncEnabled && dxvkType != DXVKConfigDialog.DXVK_TYPE_NONE) "1" else "0")
                cfg.put("asyncCache", if (asyncCacheEnabled && dxvkType == DXVKConfigDialog.DXVK_TYPE_GPLASYNC) "1" else "0")
                cfg.put("anisotropy", DXVKConfigDialog.ANISOTROPY_VALUES[anisotropyLabels.indexOf(selectedAnisotropy).coerceAtLeast(0)])
                cfg.put("lodBias", DXVKConfigDialog.LOD_BIAS_VALUES[lodBiasLabels.indexOf(selectedLodBias).coerceAtLeast(0)])
                cfg.put("vkd3dVersion", selectedVkd3d)
                cfg.put("vkd3dLevel", selectedFeatureLevel)
                cfg.put("ddrawrapper", StringUtils.parseIdentifier(selectedDdra))
                cfg.put("d7vkVersion", selectedD7vk)
                cfg.put("dxvkConfigFile", livePath)
                onConfirm(cfg.toString())
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}


// ─── Upstream dialogs that follow DxvkConfigDialog ──────────────────────────────
@Composable
internal fun WineD3DConfigDialog(
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = remember(initialConfig) { WineD3DConfigDialog.parseConfig(initialConfig) }

    val csmtOptions   = remember { listOf("Enabled", "Disabled") }
    val ssmOptions    = remember { listOf("Enabled", "Disabled") }
    val ormOptions    = remember { listOf("fbo", "backbuffer") }
    val rendOptions   = remember { listOf("gl", "vulkan", "gdi") }
    val ddraEntries   = remember { context.resources.getStringArray(R.array.ddrawrapper_entries).toList() }
    val videoMemEntries = remember { context.resources.getStringArray(R.array.video_memory_size_entries).toList() }
    var gpuNames      by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val names = WineD3DConfigDialog.loadGpuNames(context)
            withContext(Dispatchers.Main) { gpuNames = names }
        }
    }

    var csmt      by remember { mutableStateOf(if (config.get("csmt") == "3") "Enabled" else "Disabled") }
    var gpuName   by remember { mutableStateOf(config.get("gpuName")) }
    var ddra      by remember { mutableStateOf(ddraEntries.firstOrNull { StringUtils.parseIdentifier(it) == config.get("ddrawrapper") } ?: ddraEntries.first()) }
    var videoMem  by remember {
        val stored = config.get("videoMemorySize")
        mutableStateOf(videoMemEntries.firstOrNull { StringUtils.parseNumber(it) == stored } ?: videoMemEntries.first())
    }
    var ssm       by remember { mutableStateOf(if (config.get("strict_shader_math") == "1") "Enabled" else "Disabled") }
    var orm       by remember { mutableStateOf(config.get("OffscreenRenderingMode").ifEmpty { "fbo" }) }
    var renderer  by remember { mutableStateOf(config.get("renderer").ifEmpty { "gl" }) }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WineD3D ${stringResource(R.string.configuration)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                LabeledDropdown("CSMT", csmtOptions, csmt, { csmt = it })
                Spacer(Modifier.height(8.dp))
                LabeledDropdown(stringResource(R.string.gpu_name), gpuNames, gpuName, { gpuName = it })
                Spacer(Modifier.height(8.dp))
                LabeledDropdown("DDraw Wrapper", ddraEntries, ddra, { ddra = it })
                Spacer(Modifier.height(8.dp))
                LabeledDropdown(stringResource(R.string.graphics_driver_max_device_memory), videoMemEntries, videoMem, { videoMem = it })
                Spacer(Modifier.height(8.dp))
                LabeledDropdown("Strict Shader Math", ssmOptions, ssm, { ssm = it })
                Spacer(Modifier.height(8.dp))
                LabeledDropdown("Offscreen Rendering Mode", ormOptions, orm, { orm = it })
                Spacer(Modifier.height(8.dp))
                LabeledDropdown("Renderer", rendOptions, renderer, { renderer = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cfg = WineD3DConfigDialog.parseConfig(initialConfig)
                cfg.put("csmt", if (csmt == "Enabled") "3" else "0")
                cfg.put("strict_shader_math", if (ssm == "Enabled") "1" else "0")
                cfg.put("OffscreenRenderingMode", orm)
                cfg.put("gpuName", gpuName)
                cfg.put("ddrawrapper", StringUtils.parseIdentifier(ddra))
                cfg.put("videoMemorySize", StringUtils.parseNumber(videoMem))
                cfg.put("renderer", renderer)
                onConfirm(cfg.toString())
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun FpsCounterConfigDialog(
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    fun parseConfig(s: String): Map<String, String> {
        if (s.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        s.split(",").forEach { part ->
            val eq = part.indexOf('=')
            if (eq >= 0) map[part.substring(0, eq)] = part.substring(eq + 1)
        }
        return map
    }

    val cfg = remember(initialConfig) { parseConfig(initialConfig) }
    val diagContext = LocalContext.current
    fun bool(k: String, fallbackKey: String, d: String) =
        cfg.getOrDefault(k, cfg.getOrDefault(fallbackKey, d)) == "1"

    // Master HUD on/off — mirrors the in-game drawer's "Show HUD" toggle so the two surfaces keep an
    // identical key set. When off, the overlay stays hidden even while a game window is bound.
    var hudEnabled by remember { mutableStateOf(bool("hudEnabled", "hudEnabled", "1")) }
    // Orientation (vertical/horizontal) is toggled live by tapping the HUD in-game; preserve it.
    val hudMode = remember { cfg.getOrDefault("hudMode", "vertical") }
    // Fusion is the only HUD; a config saved with another hudStyle is rewritten as fusion on save.
    val hudStyle = "fusion"
    val fusionLevels = com.winhub.hernandez.widget.fusionhud.FusionSize.values().toList()
    // Detail level 1..4; also stepped by tapping the HUD in-game. Older size names map across.
    var fusionSize by remember {
        mutableStateOf(com.winhub.hernandez.widget.fusionhud.FusionSize.from(cfg.getOrDefault("hudSize", "3")).token)
    }
    // The chips this level actually renders (single source of truth in FusionSize).
    val fusionChips = com.winhub.hernandez.widget.fusionhud.FusionSize.from(fusionSize).supportedChips()

    // Metric toggles.
    var showFPS      by remember { mutableStateOf(bool("showFPS", "showFPS", "1")) }
    var showGraph    by remember { mutableStateOf(bool("showFPSGraph", "showFPSGraph", "0")) }
    var showCPU      by remember { mutableStateOf(bool("showCPUUsage", "showCPULoad", "1")) }
    var showGPU      by remember { mutableStateOf(bool("showGPULoad", "showGPULoad", "1")) }
    var showRAM      by remember { mutableStateOf(bool("showRAM", "showRAM", "1")) }
    var showPower    by remember { mutableStateOf(bool("showPower", "showPower", "1")) }
    var showTemp     by remember { mutableStateOf(bool("showTemp", "showBatteryTemp", "1")) }
    var showEngine   by remember { mutableStateOf(bool("showEngine", "showRenderer", "1")) }
    var showGpuModel by remember { mutableStateOf(bool("showGpuModel", "showGpuModel", "1")) }
    // Extra metrics (absent = off is the intended default).
    var showGpuTemp  by remember { mutableStateOf(bool("showGpuTemp", "showGpuTemp", "0")) }
    var showBattery  by remember { mutableStateOf(bool("showBattery", "showBattery", "0")) }
    var showClock    by remember { mutableStateOf(bool("showClock", "showClock", "1")) }
    // Fusion extra metrics + global lock (defaults match Container.DEFAULT_FPS_COUNTER_CONFIG).
    var showVram     by remember { mutableStateOf(bool("showVram", "showVram", "1")) }
    var showLow001   by remember { mutableStateOf(bool("showLow001", "showLow001", "1")) }
    var fpsDecimal   by remember { mutableStateOf(bool("fpsDecimal", "fpsDecimal", "1")) }
    var hudLocked    by remember { mutableStateOf(bool("hudLocked", "hudLocked", "0")) }
    // Fusion Mega-only metrics (defaults match Container.DEFAULT_FPS_COUNTER_CONFIG).
    var showPerCore  by remember { mutableStateOf(bool("showPerCore", "showPerCore", "1")) }
    var showSwap     by remember { mutableStateOf(bool("showSwap", "showSwap", "1")) }
    var showNet      by remember { mutableStateOf(bool("showNet", "showNet", "1")) }
    var showResolution by remember { mutableStateOf(bool("showResolution", "showResolution", "1")) }
    var showProton   by remember { mutableStateOf(bool("showProton", "showProton", "1")) }
    var showWrapper  by remember { mutableStateOf(bool("showWrapper", "showWrapper", "1")) }
    var showDxVer    by remember { mutableStateOf(bool("showDxVer", "showDxVer", "1")) }
    var showSession  by remember { mutableStateOf(bool("showSession", "showSession", "1")) }
    // Temperature display — same keys as the in-game drawer pane, so the two stay interchangeable.
    var tempUnitF  by remember { mutableStateOf(cfg.getOrDefault("tempUnit", "c").equals("f", true)) }
    var tempBands  by remember { mutableStateOf(cfg.getOrDefault("tempBands", "1") != "0") }
    var tempAuto   by remember { mutableStateOf(cfg.getOrDefault("tempAuto", "1") != "0") }
    var tempRedCpu by remember { mutableStateOf(cfg.getOrDefault("tempRedCpu", "90").toIntOrNull() ?: 90) }
    var tempRedGpu by remember { mutableStateOf(cfg.getOrDefault("tempRedGpu", "90").toIntOrNull() ?: 90) }
    var tempRedBat by remember { mutableStateOf(cfg.getOrDefault("tempRedBat", "48").toIntOrNull() ?: 48) }

    var hudScale by remember { mutableStateOf(cfg.getOrDefault("hudScale", Container.DEFAULT_HUD_SCALE.toString()).toIntOrNull() ?: Container.DEFAULT_HUD_SCALE) }
    var hudOpacity by remember { mutableStateOf(cfg.getOrDefault("hudOpacity", "62").toIntOrNull() ?: 62) }

    val colors = listOf("soft", "mid", "vivid")
    var color by remember { mutableStateOf(cfg.getOrDefault("hudColor", "mid")) }
    // hudOutline is a 0..100 intensity (legacy off/soft/strong strings map via parseHudOutline).
    var outlineValue by remember { mutableStateOf(parseHudOutline(cfg.getOrDefault("hudOutline", "40"))) }
    var outlineAccent by remember { mutableStateOf(cfg.getOrDefault("hudOutlineAccent", "0") == "1") }

    fun i(v: Boolean) = if (v) "1" else "0"
    fun buildConfig(): String = listOf(
        "hudStyle=$hudStyle",
        "hudEnabled=${i(hudEnabled)}",
        "hudSize=$fusionSize",
        "hudLocked=${i(hudLocked)}",
        "showVram=${i(showVram)}",
        "showLow001=${i(showLow001)}",
        "fpsDecimal=${i(fpsDecimal)}",
        "showPerCore=${i(showPerCore)}",
        "showSwap=${i(showSwap)}",
        "showNet=${i(showNet)}",
        "showResolution=${i(showResolution)}",
        "showProton=${i(showProton)}",
        "showWrapper=${i(showWrapper)}",
        "showDxVer=${i(showDxVer)}",
        "showSession=${i(showSession)}",
        "hudMode=$hudMode",
        "showFPS=${i(showFPS)}",
        "showFPSGraph=${i(showGraph)}",
        "showCPUUsage=${i(showCPU)}",
        "showCPULoad=${i(showCPU)}",
        "showGPULoad=${i(showGPU)}",
        "showRAM=${i(showRAM)}",
        "showPower=${i(showPower)}",
        "showTemp=${i(showTemp)}",
        "showBatteryTemp=${i(showTemp)}",
        "showEngine=${i(showEngine)}",
        "showRenderer=${i(showEngine)}",
        "showGpuModel=${i(showGpuModel)}",
        "showGpuTemp=${i(showGpuTemp)}",
        "showBattery=${i(showBattery)}",
        "showClock=${i(showClock)}",
        "tempUnit=${if (tempUnitF) "f" else "c"}",
        "tempBands=${i(tempBands)}",
        "tempAuto=${i(tempAuto)}",
        "tempRedCpu=$tempRedCpu",
        "tempRedGpu=$tempRedGpu",
        "tempRedBat=$tempRedBat",
        "hudColor=$color",
        "hudOutline=$outlineValue",
        "hudOutlineAccent=${if (outlineAccent) 1 else 0}",
        "hudScale=$hudScale",
        "hudOpacity=$hudOpacity"
    ).joinToString(",")

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FPS Counter Settings") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7f).dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Master toggle (parity with the in-game drawer): hides the whole overlay when off.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = hudEnabled,
                        onCheckedChange = { hudEnabled = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Show HUD")
                }
                Spacer(Modifier.height(12.dp))
                HudThreeStop(
                    "Detail level",
                    fusionLevels.map { it.label },
                    fusionLevels.indexOfFirst { it.token == fusionSize }.coerceAtLeast(0)
                ) { fusionSize = fusionLevels[it].token }
                Text(
                    "1 FPS, 2 adds the frametime graph, 3 adds GPU, CPU and memory, 4 shows everything.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tip: tap the HUD in-game to step through the levels.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))

                // Compact multi-select metric chips (filled = on) in a wrap layout,
                // so ~13 metrics fit in a few rows instead of stacked Switch rows.
                Text("Metrics", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                // Build the chips the SELECTED LEVEL draws (single source of truth in
                // FusionSize.supportedChips()), then chunk into an aligned 3-wide grid so hidden chips
                // never leave holes. Hiding a chip is UI-only; buildConfig() still emits every key.
                fun show(label: String): Boolean = label in fusionChips
                val metricChips = buildList<Triple<String, Boolean, () -> Unit>> {
                    if (show("FPS")) add(Triple("FPS", showFPS) { showFPS = !showFPS })
                    if (show("FPS graph")) add(Triple("FPS graph", showGraph) { showGraph = !showGraph })
                    if (show("CPU")) add(Triple("CPU", showCPU) { showCPU = !showCPU })
                    if (show("GPU")) add(Triple("GPU", showGPU) { showGPU = !showGPU })
                    if (show("VRAM")) add(Triple("VRAM", showVram) { showVram = !showVram })
                    if (show("RAM")) add(Triple("RAM", showRAM) { showRAM = !showRAM })
                    if (show("Power")) add(Triple("Power", showPower) { showPower = !showPower })
                    if (show("Temp")) add(Triple("Temp", showTemp) { showTemp = !showTemp })
                    if (show("GPU temp")) add(Triple("GPU temp", showGpuTemp) { showGpuTemp = !showGpuTemp })
                    if (show("Battery")) add(Triple("Battery", showBattery) { showBattery = !showBattery })
                    if (show("0.01% low")) add(Triple("0.01% low", showLow001) { showLow001 = !showLow001 })
                    if (show("FPS .1")) add(Triple("FPS .1", fpsDecimal) { fpsDecimal = !fpsDecimal })
                    if (show("Per-core")) add(Triple("Per-core", showPerCore) { showPerCore = !showPerCore })
                    if (show("Swap")) add(Triple("Swap", showSwap) { showSwap = !showSwap })
                    if (show("Network")) add(Triple("Network", showNet) { showNet = !showNet })
                    if (show("Resolution")) add(Triple("Resolution", showResolution) { showResolution = !showResolution })
                    if (show("Proton")) add(Triple("Proton", showProton) { showProton = !showProton })
                    if (show("Wrapper")) add(Triple("Wrapper", showWrapper) { showWrapper = !showWrapper })
                    if (show("DX ver")) add(Triple("DX ver", showDxVer) { showDxVer = !showDxVer })
                    if (show("Session")) add(Triple("Session", showSession) { showSession = !showSession })
                    if (show("Clock")) add(Triple("Clock", showClock) { showClock = !showClock })
                    if (show("Engine")) add(Triple("Engine", showEngine) { showEngine = !showEngine })
                    if (show("GPU model")) add(Triple("GPU model", showGpuModel) { showGpuModel = !showGpuModel })
                    // Global appearance control, shown at every level.
                    add(Triple("Lock in place", hudLocked) { hudLocked = !hudLocked })
                }
                ModeChipGrid(metricChips, perRow = 3)

                // ── Temperature display ── only meaningful when a temperature is on screen.
                if (showTemp || showGpuTemp || showBattery) {
                    Spacer(Modifier.height(12.dp))
                    HudThreeStop("Temp unit", listOf("\u00B0C", "\u00B0F"), if (tempUnitF) 1 else 0) {
                        tempUnitF = it == 1
                    }
                    Spacer(Modifier.height(4.dp))
                    // Danger bands as one 3-way rather than two toggles: "bands off but auto on"
                    // isn't a distinct state worth exposing.
                    val bandMode = if (!tempBands) 0 else if (tempAuto) 1 else 2
                    HudThreeStop("Danger colors", listOf("Off", "Auto", "Manual"), bandMode) {
                        tempBands = it != 0
                        tempAuto = it != 2
                    }
                    Text(
                        when (bandMode) {
                            0 -> "Temperatures use their normal color."
                            1 -> "Thresholds read from your device's own thermal trip points, falling back to safe defaults."
                            else -> "Set the red point per sensor; amber sits just below it."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (bandMode == 2) {
                        // Red point only; amber is derived. Always \u00B0C — thresholds never convert.
                        Spacer(Modifier.height(4.dp))
                        Text("CPU red at: $tempRedCpu\u00B0C", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = tempRedCpu.toFloat(),
                            onValueChange = { tempRedCpu = it.toInt() },
                            valueRange = 50f..110f, steps = 59
                        )
                        if (showGpuTemp) {
                            Text("GPU red at: $tempRedGpu\u00B0C", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = tempRedGpu.toFloat(),
                                onValueChange = { tempRedGpu = it.toInt() },
                                valueRange = 50f..110f, steps = 59
                            )
                        }
                        Text("Battery red at: $tempRedBat\u00B0C", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = tempRedBat.toFloat(),
                            onValueChange = { tempRedBat = it.toInt() },
                            valueRange = 35f..60f, steps = 24
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("HUD Scale: $hudScale%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = hudScale.toFloat(),
                    onValueChange = { hudScale = it.toInt().coerceAtLeast(50) },
                    valueRange = 50f..150f, steps = 99
                )

                Spacer(Modifier.height(4.dp))
                Text("HUD Opacity: $hudOpacity%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = hudOpacity.toFloat(),
                    onValueChange = { hudOpacity = it.toInt() },
                    valueRange = 0f..100f, steps = 99
                )
                Spacer(Modifier.height(8.dp))
                HudThreeStop("HUD color", listOf("Soft", "Mid", "Vivid"), colors.indexOf(color)) { color = colors[it] }
                Spacer(Modifier.height(8.dp))
                Text("HUD outline: $outlineValue", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = outlineValue.toFloat(),
                    onValueChange = { outlineValue = it.toInt() },
                    valueRange = 0f..100f, steps = 99
                )
                HudThreeStop("Outline color", listOf("Gray", "Accent"), if (outlineAccent) 1 else 0) { outlineAccent = it == 1 }

                // General HUD action (not gated to a style): dump every sensor path + value to a
                // shareable text file so a device owner can report which nodes their SoC exposes.
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { exportHudDiagnostics(diagContext) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export HUD diagnostics")
                }
                Text(
                    "Saves a sensor report (CPU/GPU/temp/VRAM…) straight to your Downloads folder.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(buildConfig()) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

// Multi-select metric grid styled exactly like the in-game drawer's FullscreenModeButtons: each item
// toggles independently, but shares the box style (accent fill + bold black text ON; black bg +
// dimmed-accent 1dp border + accent medium text OFF) and the aligned equal-width grid (weight(1f),
// short rows padded with Spacer so widths stay equal). Callers build the VISIBLE list first, then
// this chunks per row so per-style gating never leaves holes. There's no LocalAccentDim in this
// screen, so we derive the dim border from a 40%-alpha primary — reads the same as accentDim.
@Composable
private fun ModeChipGrid(items: List<Triple<String, Boolean, () -> Unit>>, perRow: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDim = accent.copy(alpha = 0.4f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { (label, isOn, onTap) ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOn) accent else Color.Black)
                            .border(
                                width = 1.dp,
                                color = if (isOn) accent else accentDim,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onTap() }
                            .padding(vertical = 9.dp)
                    ) {
                        Text(
                            label,
                            color = if (isOn) Color.Black else accent,
                            fontSize = 12.sp,
                            fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HudThreeStop(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row {
        options.forEachIndexed { idx, opt ->
            FilterChip(
                selected = selected == idx,
                onClick = { onSelect(idx) },
                label = { Text(opt) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline install helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun installContentFromUri(activity: Activity, uri: Uri, onResult: (Boolean) -> Unit) {
    val cm = ContentsManager(activity)
    Executors.newSingleThreadExecutor().execute {
        try {
            cm.extraContentFile(uri, object : ContentsManager.OnInstallFinishedCallback {
                var phase = 0
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    val message = when (reason) {
                        ContentsManager.InstallFailedReason.ERROR_NOSPACE -> "Not enough storage space"
                        ContentsManager.InstallFailedReason.ERROR_BADTAR -> "Corrupted archive file"
                        ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> "No valid profile found in package"
                        ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> "Invalid profile in package"
                        ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> "Missing required files in package"
                        ContentsManager.InstallFailedReason.ERROR_EXIST -> "This version is already installed"
                        ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> "Untrusted profile, installation blocked"
                        ContentsManager.InstallFailedReason.ERROR_UNKNOWN -> "Unknown installation error"
                    }
                    activity.runOnUiThread {
                        Toast.makeText(activity, "ERROR: $message", Toast.LENGTH_LONG).show()
                        onResult(false)
                    }
                }
                override fun onSucceed(profile: ContentProfile) {
                    try {
                        if (phase == 0) {
                            phase = 1
                            cm.finishInstallContent(profile, this)
                        } else {
                            cm.syncContents()
                            activity.runOnUiThread { onResult(true) }
                        }
                    } catch (e: Exception) {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "ERROR: Installation error — ${e.message}", Toast.LENGTH_LONG).show()
                            onResult(false)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            activity.runOnUiThread {
                Toast.makeText(activity, "ERROR: Installation error — ${e.message}", Toast.LENGTH_LONG).show()
                onResult(false)
            }
        }
    }
}

@Composable
private fun ContentInstallGear(
    onDownloadFile: () -> Unit,
) {
    // Cloud opens the download menu directly. Browse/download + "install from file" both live in the sheet.
    IconButton(onClick = onDownloadFile, modifier = Modifier.size(40.dp)) {
        Icon(
            Icons.Default.CloudDownload,
            contentDescription = "Download / install",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}



// ─── VEGAS HUD toggle row ───────────────────────────────────────────────────

@Composable
private fun HudToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}


// ─── VEGAS stock config download sheet ──────────────────────────────────────────
@Composable
internal fun StockConfigDownloadSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf(listOf<VegasStockConfigFetcher.ReleaseConf>()) }
    var parkedTag by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        rows = withContext(Dispatchers.IO) { VegasStockConfigFetcher.listReleaseConfigs() }
        loading = false
        // Autonomy: persist any newly seen release versions so the classifier's
        // known list grows by itself — no bundled-asset regeneration ever needed.
        val prefs = context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE)
        val existing = prefs.getString("released_tail", "")?.split('|')
            ?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        var added = false
        for (rel in rows) {
            val prefix = rel.tag.removePrefix("v").substringBefore('-')
            for (c in rel.verNames + prefix) {
                if (c.isNotBlank() && existing.add(c)) added = true
            }
        }
        if (added) prefs.edit().putString("released_tail", existing.joinToString("|")).apply()
        // Heal catalog tail too — tags seen live become "covered" so "unverified" doesn't stick forever
        val catExisting = prefs.getString("catalog_tail", "")?.split('|')
            ?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
        var catAdded = false
        for (rel in rows) if (rel.tag.isNotBlank() && catExisting.add(rel.tag)) catAdded = true
        VegasKeyCatalog.capToMax(catExisting)
        if (catAdded) prefs.edit().putString("catalog_tail", catExisting.joinToString("|")).apply()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Stock configs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a version to fetch its config. Builds without a shipped config show none.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        if (loading) {
            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        } else if (rows.isEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "Couldn't reach the releases feed — check connection and retry. (GitHub limit 60/h)",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { retryKey++ }) { Text("Retry") }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(rows.size) { idx ->
                    val rel = rows[idx]
                    val hasConf = rel.confUrl != null
                    val busy = parkedTag == rel.tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasConf && !busy) {
                                parkedTag = rel.tag
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) { VegasStockConfigFetcher.park(context, rel) }
                                    parkedTag = null
                                    when (res) {
                                        is VegasStockConfigFetcher.ParkResult.Ok -> {
                                            activity?.let {
                                                Toast.makeText(it,
                                                    "Parked as ${res.parkedAs}.conf — now select \"${res.parkedAs}\" under Stock config",
                                                    Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        is VegasStockConfigFetcher.ParkResult.Fail ->
                                            activity?.let { Toast.makeText(it, "Failed: ${res.reason}", Toast.LENGTH_LONG).show() }
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            if (hasConf) Icons.Filled.Download else Icons.Filled.Block,
                            contentDescription = null,
                            tint = if (hasConf) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rel.tag.ifEmpty { "(untagged)" }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append(rel.confName ?: "no config asset")
                                    if (rel.date.isNotEmpty()) append("  ·  ${rel.date}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

// ─── VEGAS key definitions glossary ─────────────────────────────────────────────
private val KEY_DEFINITIONS: Map<String, String> = mapOf(
    // --- environment / DXVK core ---
    "DXVK_CONFIG_FILE" to "Native DXVK config-file path. Set automatically when a custom .conf is selected.",
    "DXVK_FILTER_DEVICE_NAME" to "Substring filter to force or avoid a GPU by name.",
    "DXVK_LOG_LEVEL" to "Sets DXVK log verbosity (none/info/warn/error).",
    "GPU" to "Legacy environment hint; verify before use.",
    // --- dxvk.* core ---
    "dxvk.enableAsync" to "Enables async shader compilation (legacy; superseded by GPL).",
    "dxvk.gplAsyncCache" to "Caches pipeline state for fast GPU link (GPL async).",
    "dxvk.enableStarProfile" to "Enables the Star Engine tuning profile.",
    "dxvk.hud" to "Configures the DXVK on-screen HUD (e.g. 'devinfo,fps').",
    "dxvk.numCompilerThreads" to "Number of threads used for shader compilation.",
    "dxvk.tearFree" to "Enables tear-free presentation.",
    "dxvk.latencySleep" to "Inserts a sleep to reduce input latency (experimental).",
    "dxvk.maxFrameLatency" to "Caps the number of frames queued for presentation.",
    "dxvk.enableDebugUtils" to "Enables Vulkan debug-utils instrumentation.",
    "dxvk.numAsyncThreads" to "Legacy async compile thread count.",
    "dxvk.shrinkNvidiaHvvHeap" to "Reduces NV host-visible heap usage.",
    "dxvk.useRawSsbo" to "Uses raw SSBO bindings (compat workaround).",
    // --- dxvk.vegas.* (Sarek) / vegas.* (Star) — best-effort ---
    "dxvk.vegas.enable" to "Master switch for VEGAS / DXVK-VEGAS extensions.", // ⚠ best-effort
    "dxvk.vegas.gpuMask" to "Restricts rendering to a subset of GPUs (mask).", // ⚠ best-effort
    "dxvk.vegas.tbdr" to "Enables TBDR (tile-based) optimizations for Adreno.", // ⚠ best-effort
    "dxvk.vegas.threshold" to "Sets internal VEGAS quality / skip thresholds.", // ⚠ best-effort
    "dxvk.vegas.vramSwap" to "Controls VEGAS VRAM swap-to-storage behavior.", // ⚠ best-effort
    "vegas.enableUpscaler" to "Enables the VEGAS upscaler.",
    "vegas.forceTier" to "Selects a fixed VEGAS performance tier.", // ⚠ best-effort
    "vegas.profileDraws" to "Enables VEGAS draw-call profiling.", // ⚠ best-effort
    "vegas.telemetry" to "Enables VEGAS telemetry collection.", // ⚠ best-effort
    "VEGAS_GAME_CONFIG" to "Selects a per-game VEGAS config profile.", // ⚠ best-effort
    "VEGAS_THRESHOLDS" to "VEGAS internal tuning thresholds.", // ⚠ best-effort
    // --- d3d11.* ---
    "d3d11.samplerAnisotropy" to "Caps the max anisotropy for samplers.",
    "d3d11.maxFeatureLevel" to "Caps the reported D3D feature level.",
    "d3d11.maxTessFactor" to "Caps the tessellation factor.",
    "d3d11.ignoreGraphicsBarriers" to "Relaxes barrier insertion (compat).",
    "d3d11.invariantPosition" to "Forces invariant position math (strict).",
    "d3d11.enableDepthPrePass" to "Enables a depth pre-pass.",
    "d3d11.disableMsaa" to "Disables MSAA.",
    "d3d11.maxImplicitDiscardSize" to "Threshold for implicit resource discards.",
    "d3d11.relaxedBarriers" to "Relaxes resource barriers (compat).",
    "d3d11.cachedDynamicResources" to "Caches dynamic resource uploads.",
    "d3d11.constantBufferRangeCheck" to "Validates CB range access.",
    "d3d11.dcSingleUseMode" to "Uses single-use deferred contexts.",
    "d3d11.maxDynamicImageBufferSize" to "Caps dynamic image buffer size.",
    "d3d11.zeroWorkgroupMemory" to "Zeroes workgroup memory (compat).",
    // --- d3d9.* ---
    "d3d9.samplerAnisotropy" to "Caps anisotropy (D3D9).",
    "d3d9.maxFrameRate" to "Caps presentation rate (D3D9).",
    "d3d9.maxFrameLatency" to "Caps queued frames (D3D9).",
    "d3d9.disableMsaa" to "Disables MSAA (D3D9).",
    "d3d9.tearFree" to "Tear-free presentation (D3D9).",
    "d3d9.invariantPosition" to "Invariant position (D3D9).",
    "d3d9.forceAspectRatio" to "Forces a fixed aspect ratio.",
    "d3d9.shaderModel" to "Caps the D3D9 shader model.",
    "d3d9.enableDepthPrePass" to "Depth pre-pass (D3D9).",
    "d3d9.enableDialogMode" to "Dialog-mode tweaks.",
    "d3d9.evictManagedOnUnlock" to "Evicts managed textures on unlock.",
    "d3d9.deferSurfaceCreation" to "Defers swapchain surface creation.",
    "d3d9.customDeviceId" to "Spoofs the GPU device id.",
    "d3d9.customVendorId" to "Spoof the GPU vendor id.",
    "d3d9.customDeviceDesc" to "Spoofs the GPU description string.",
    "d3d9.supportD32" to "Enables D3D9 D32 depth format support.",
    "d3d9.supportDFFormats" to "Enables D3D9 float formats support.",
    "d3d9.supportVCache" to "Enables D3D9 vertex-cache support.",
    "d3d9.supportX4R4G4B4" to "Enables D3D9 X4R4G4B4 format support.",
    "d3d9.seamlessCubes" to "Fixes cube-map seam sampling.",
    "d3d9.strictConstantCopies" to "Stricter constant-buffer copies (compat).",
    "d3d9.strictPow" to "Stricter pow() math (compat).",
    "d3d9.floatEmulation" to "Controls float emulation mode.",
    "d3d9.lenientClear" to "Lenient clear behavior.",
    "d3d9.longMad" to "Uses long MAD (compat).",
    "d3d9.memoryTrackTest" to "Memory-tracking test hook.",
    "d3d9.noExplicitFrontBuffer" to "Avoids an explicit front buffer.",
    "d3d9.numBackBuffers" to "Sets back-buffer count.",
    "d3d9.presentInterval" to "Sets presentation interval.",
    "d3d9.allowDiscard" to "Allows discard usage flags.",
    "d3d9.allowDoNotWait" to "Allows do-not-wait usage flags.",
    "d3d9.alphaTestWiggleRoom" to "Alpha-test tolerance (compat).",
    "d3d9.cachedDynamicBuffers" to "Caches dynamic vertex/index buffers.",
    "d3d9.forceSwapchainMSAA" to "Forces MSAA on the swapchain.",
    "d3d9.maxAvailableMemory" to "Caps reported available memory.",
    "d3d9.enumerateByDisplays" to "Enumerates adapters by display.",
    "d3d9.dpiAware" to "Marks the app DPI-aware.",
    // --- d3d8.* ---
    "d3d8.batching" to "D3D8-era batching tweak (compat).", // ⚠ best-effort
    "d3d8.drefScaling" to "D3D8 depth-reference scaling (compat).", // ⚠ best-effort
    "d3d8.forceLegacyDiscard" to "Forces legacy discard behavior.", // ⚠ best-effort
    "d3d8.forceVsDecl" to "Forces a specific vertex declaration.", // ⚠ best-effort
    "d3d8.placeP8InScratch" to "Places P8 textures in scratch (compat).", // ⚠ best-effort
    "d3d8.shadowPerspectiveDivide" to "Shadow perspective-divide fix (compat).", // ⚠ best-effort
    // --- dxgi.* ---
    "dxgi.maxFrameRate" to "Caps presentation frame rate.",
    "dxgi.maxFrameLatency" to "Caps queued frames.",
    "dxgi.maxDeviceMemory" to "Caps reported GPU memory.",
    "dxgi.maxSharedMemory" to "Caps reported shared memory.",
    "dxgi.syncInterval" to "Sets vsync interval (0 = disabled).",
    "dxgi.tearFree" to "Tear-free presentation.",
    "dxgi.numBackBuffers" to "Sets back-buffer count.",
    "dxgi.customDeviceId" to "Spoofs the GPU device id.",
    "dxgi.customVendorId" to "Spoof the GPU vendor id.",
    "dxgi.customDeviceDesc" to "Spoofs the GPU description string.",
    "dxgi.deferSurfaceCreation" to "Defers surface creation.",
    "dxgi.emulateUMA" to "Emulates a UMA memory model.",
    "dxgi.enableDummyCompositionSwapchain" to "Creates a dummy composition swapchain.",
    "dxgi.hideAmdGpu" to "Hides the AMD GPU from the app.",
    "dxgi.hideIntelGpu" to "Hides the Intel GPU from the app.",
    "dxgi.hideNvidiaGpu" to "Hides the Nvidia GPU from the app.",
    "dxgi.enableHDR" to "Enables HDR output when supported by the display.",
    "dxgi.enableUe4Workarounds" to "Enables workarounds for Unreal Engine 4 rendering quirks.",
    "dxgi.forceRefreshRate" to "Forces a specific display refresh rate (Hz). 0 = auto.",
    "dxgi.hideNvkGpu" to "Hides the NVK (open-source NVIDIA) GPU from the app.",
    // --- dxvk.* extended ---
    "dxvk.allowFse" to "Allows fullscreen exclusive mode transitions.",
    "dxvk.deviceFilter" to "Substring filter to restrict Vulkan device selection by name.",
    "dxvk.disableNvLowLatency2" to "Disables the VK_NV_low_latency2 extension.",
    "dxvk.enableDescriptorBuffer" to "Enables VK_EXT_descriptor_buffer for faster descriptor access.",
    "dxvk.enableDescriptorHeap" to "Enables pooled descriptor heap allocation.",
    "dxvk.enableGraphicsPipelineLibrary" to "Enables VK_EXT_graphics_pipeline_library for faster pipeline creation.",
    "dxvk.enableImplicitResolves" to "Enables implicit MSAA/resolve transitions.",
    "dxvk.enableMemoryDefrag" to "Enables automatic Vulkan memory defragmentation.",
    "dxvk.enableNvRawAccessChains" to "Enables VK_NV_raw_access_chains for raw buffer access.",
    "dxvk.enableUnifiedImageLayouts" to "Uses unified image layout tracking.",
    "dxvk.hideIntegratedGraphics" to "Hides integrated GPUs from the app (discrete only).",
    "dxvk.latencyTolerance" to "Latency tolerance in microseconds for sleep decisions.",
    "dxvk.lowerSinCos" to "Uses lower-precision sin/cos for performance.",
    "dxvk.maxFrameRate" to "Caps presentation frame rate at the DXVK layer.",
    "dxvk.maxMemoryBudget" to "Overrides the Vulkan memory budget in MB.",
    "dxvk.tilerMode" to "Controls TBDR tiler behavior (Adreno-specific).",
    "dxvk.trackPipelineLifetime" to "Tracks pipeline object lifecycle for cache management.",
    "dxvk.zeroMappedMemory" to "Zeroes memory on allocation (debug / leak detection).",
    // --- d3d11.* extended ---
    "d3d11.clampNegativeLodBias" to "Clamps negative LOD bias to 0.",
    "d3d11.disableDirectImageMapping" to "Disables direct image-to-host mapping.",
    "d3d11.enableContextLock" to "Serializes D3D11 device context access (compat).",
    "d3d11.exposeDriverCommandLists" to "Exposes driver-level command list support.",
    "d3d11.forceComputeLdsBarriers" to "Forces barriers between compute LDS accesses.",
    "d3d11.forceComputeUavBarriers" to "Forces barriers between compute UAV accesses.",
    "d3d11.forceSampleRateShading" to "Forces sample-rate shading for all materials.",
    "d3d11.relaxedGraphicsBarriers" to "Relaxes barriers on graphics pipeline resources.",
    "d3d11.reproducibleCommandStream" to "Produces deterministic command streams (debug).",
    "d3d11.samplerLodBias" to "Global LOD bias applied to all samplers.",
    // --- d3d8.* extended ---
    "d3d8.scaleDref" to "Scales depth-reference values for D3D8 compat.",
    // --- d3d9.* extended ---
    "d3d9.cachedWriteOnlyBuffers" to "Caches write-only buffer data in system memory.",
    "d3d9.clampNegativeLodBias" to "Clamps negative LOD bias to 0 (D3D9).",
    "d3d9.countLosableResources" to "Counts resources that can be evicted (memory tracking).",
    "d3d9.deviceLocalConstantBuffers" to "Places constant buffers in device-local memory.",
    "d3d9.deviceLossOnFocusLoss" to "Reports device loss when the app loses focus.",
    "d3d9.disableA8RT" to "Disables A8 render-target format support.",
    "d3d9.extraFrontbuffer" to "Allocates an extra front buffer for compat.",
    "d3d9.forceRefreshRate" to "Forces a specific display refresh rate for D3D9 (Hz).",
    "d3d9.forceSampleRateShading" to "Forces sample-rate shading for D3D9 materials.",
    "d3d9.forceSamplerTypeSpecConstants" to "Uses spec constants to control sampler types.",
    "d3d9.hideAmdGpu" to "Hides the AMD GPU from D3D9 enumeration.",
    "d3d9.hideIntelGpu" to "Hides the Intel GPU from D3D9 enumeration.",
    "d3d9.hideNvidiaGpu" to "Hides the NVIDIA GPU from D3D9 enumeration.",
    "d3d9.hideNvkGpu" to "Hides the NVK GPU from D3D9 enumeration.",
    "d3d9.ignoreDefaultBufferLockRange" to "Ignores the default lock range on buffers.",
    "d3d9.modeCountCompatibility" to "Reports a compatible display mode count.",
    "d3d9.reproducibleCommandStream" to "Produces deterministic D3D9 command streams (debug).",
    "d3d9.samplerLodBias" to "Global LOD bias applied to D3D9 samplers.",
    "d3d9.supportCubeDepthFormats" to "Enables depth format support on cube textures.",
    "d3d9.textureMemory" to "Caps total D3D9 texture memory pool in MB.",
    "d3d9.useD32forD24" to "Uses D32 format where D24 would be used.",
    "d3d9.useFP16" to "Uses half-precision float where full precision is not required.",
    // --- HUD labels (VEGAS governor internal — not user-configurable) ---
    "cap" to "HUD: Maximum draw-batch size before forced flush (governor internal).",
    "draw" to "HUD: Draw calls per millisecond — game rendering workload.",
    "flush" to "HUD: Number of GPU flushes per frame (auto-tuned by governor).",
    "key" to "HUD/Sarek: Internal key identifier (not user-configurable).",
    "thr" to "HUD: Draw-batch threshold — triggers flush when reached.",
    // --- vegas.* extended ---
    "vegas.enableHud" to "Enables the VEGAS governor HUD overlay.",
)

// ─── VEGAS glossary sheet ───────────────────────────────────────────────────────
@Composable
private fun VGlossarySheet(
    onDismiss: () -> Unit,
    vegasCatalog: VegasKeyCatalog?,
    installedTag: String?,
) {
    val bucketOrder = listOf(
        VegasKeyCatalog.Bucket.IN_BUILD,
        VegasKeyCatalog.Bucket.UPSTREAM,
        VegasKeyCatalog.Bucket.OTHER_BUILD,
        VegasKeyCatalog.Bucket.NOWHERE,
    )
    val bucketLabel: (VegasKeyCatalog.Bucket) -> String = { b ->
        when (b) {
            VegasKeyCatalog.Bucket.IN_BUILD -> "Documented by this build"
            VegasKeyCatalog.Bucket.UPSTREAM -> "Upstream only"
            VegasKeyCatalog.Bucket.OTHER_BUILD -> "Other build"
            VegasKeyCatalog.Bucket.NOWHERE -> "Not in catalog"
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
        ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.86f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("V-Glossary", style = MaterialTheme.typography.titleLarge)
                Text(
                    buildString {
                        append(if (installedTag != null) "Build: $installedTag" else "No build selected")
                        append("  ·  catalog ${vegasCatalog?.generatedAt() ?: "n/a"}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (vegasCatalog == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Catalog offline — provenance hidden, definitions still shown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(10.dp))
                val keys = if (vegasCatalog != null) vegasCatalog.allKeys() else KEY_DEFINITIONS.keys.toList()
                val grouped = keys.groupBy { key ->
                    if (vegasCatalog != null) vegasCatalog.classify(key, installedTag) else VegasKeyCatalog.Bucket.NOWHERE
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    bucketOrder.filter { grouped.containsKey(it) }.forEach { bucket ->
                        item {
                            Text(
                                bucketLabel(bucket),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(grouped.getValue(bucket).sorted()) { key ->
                            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                                Text(
                                    key,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val fam = vegasCatalog?.familyOf(key)?.name ?: "—"
                                    Text("[$fam]", style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        KEY_DEFINITIONS[key] ?: "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            }
        }
        }
    }
}
