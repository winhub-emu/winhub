package com.winhub.hernandez.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import com.winhub.hernandez.BuildConfig
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.core.StringUtils
import java.util.Locale

/**
 * Deck Mode's container editor — the classic editor's SAME settings, regrouped into modern
 * top tabs (General / Graphics / Performance / CPU / Input / Environment / Wine). Tab content
 * slides rapidly bottom-to-top on every switch (DroidDeck-style). Controller-first: focus lands
 * on the first tab pill (never the name text field, which swallowed D-pad input), so the whole
 * screen is D-pad navigable — tabs, rows, Cancel / Save.
 *
 * It binds to the SAME [ContainerDetailViewModel] the classic editor uses — so all load/save
 * and preset logic is shared verbatim. The seven `resolved*` params that the
 * classic screen pulls from AndroidView-backed refs (CPUListView, ColorPickerView, KeyValueSet tags)
 * are synthesized straight from the view-model's own state, which is exactly the fallback the
 * classic screen already uses when those refs are absent (`?: viewModel.cpuList`, `?: "#0277bd"`).
 */
@Composable
internal fun DeckContainerDetailScreen(
    containerId: Int,
    onNavigateBack: () -> Unit,
    // ONE view-model per edited id. The screen is activity-scoped and the Deck keeps the
    // outgoing editor composed for a frame during AnimatedContent transitions; with a single
    // shared instance that outgoing effect could re-run init() for the PREVIOUS id (Defaults,
    // New, another container) and clobber the form being edited - so Save wrote the defaults
    // profile / another container while the real one kept its old frame-gen engine. Keying by
    // id isolates each editor; init() then only ever re-loads on a real id change.
    viewModel: ContainerDetailViewModel = viewModel(key = "deckContainerDetail/$containerId"),
) {
    LaunchedEffect(containerId) { viewModel.init(containerId) }

    // The card that opened this screen disappears, taking its focus node with it. Park focus on the
    // Back button so the D-pad keeps a visible target from the first frame.
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }

    val title = when {
        viewModel.defaultsMode -> "New Container Defaults"
        containerId <= 0 -> "New Container"
        else -> viewModel.containerName.ifBlank { "Container" }
    }
    val saveLabel = when {
        viewModel.isSaving -> "Saving…"
        viewModel.isEditMode -> "Save Changes"
        else -> "Create Container"
    }

    val tabTitles = remember {
        listOf("General", "Graphics", "Performance", "CPU", "Input", "Environment", "Wine")
    }
    val tabs = LocalDeckTabs.current
    val tab = tabs.index
    LaunchedEffect(tabs) { tabs.bind(tabTitles.size) }

    // Which inline config section is expanded (null = all collapsed). One at a time, so a row's
    // editor pushes the rows below it down without stacking several open at once.
    var openSection by remember { mutableStateOf<String?>(null) }
    // VEGAS stock-config sheet (Deck-native; VEGAS wrappers only).
    var showStockConfigSheet by remember { mutableStateOf(false) }
    // Deck-native component download sheets for DXVK / VKD3D / D7VK.
    var showDxvkDownloadSheet by remember { mutableStateOf(false) }
    var showVkd3dDownloadSheet by remember { mutableStateOf(false) }
    var showD7vkDownloadSheet by remember { mutableStateOf(false) }
    var dxvkRefreshKey by remember { mutableStateOf(0) }
    val isVegasWrapper = StringUtils.parseIdentifier(viewModel.selectedDXWrapper).contains("vegas")
    // Renderer panel writes straight through on every change (the panel has no Apply gate), so
    // "Save Changes" is enough. Shared by the live-commit and Done paths.
    val applyRendererConfig: (String) -> Unit = { newConfig ->
        val m = parseVulkanConfig(newConfig)
        viewModel.rendererNative = m["native"] == "true"
        viewModel.rendererPresentMode = m["presentMode"] ?: "fifo"
        viewModel.rendererDriverId = m["driverId"] ?: "system"
        viewModel.rendererFilterMode = m["filterMode"]?.toIntOrNull() ?: 0
        viewModel.rendererSwapRB = m["swapRB"] == "true"
    }

    // Category-navigation layout — a setting (Settings → Appearance → Container Editor Layout).
    // "rail" = animated left rail (default), "pages" = full-page list.
    val context = LocalContext.current
    val portraitUi = LocalDeckPortrait.current
    val layoutPref = remember {
        if (portraitUi) "pages" else PreferenceManager.getDefaultSharedPreferences(context)
            .getString("container_editor_layout", "rail")?.takeIf { it != "tabs" } ?: "rail"
    }
    // "popup" = config editors open in a DeckConfigDialog; "dropdown" = classic inline
    // DeckExpandableRow. Read once per open (the Settings screen writes it before we get here).
    val configEditorStyle = remember {
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString("config_editor_style", "popup") ?: "popup"
    }
    // "pages" layout: whether a category page is open (false = the category list page).
    var pagesCategoryOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    // "pages" layout: Back first closes the open category, then leaves the editor.
                    if (layoutPref == "pages" && pagesCategoryOpen) pagesCategoryOpen = false
                    else onNavigateBack()
                },
                modifier = Modifier.focusRequester(backFocus),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Deck.onBackground)
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Deck.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = when {
                        viewModel.defaultsMode -> "Saved as the profile every NEW container starts from"
                        viewModel.isEditMode -> "Editing an existing container"
                        else -> "Configure a fresh container, then create it"
                    },
                    color = Deck.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            Text(
                text = when {
                    viewModel.defaultsMode -> "DEFAULTS"
                    viewModel.isEditMode -> "EDIT"
                    else -> "NEW"
                },
                color = Deck.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }

        // ── Navigation chrome: top tabs / side rail / full-page category list ──
        if (layoutPref == "tabs") {
            // L1/R1 step them; the accent pill stretches onto the new one.
            DeckStretchyTabs(
                labels = tabTitles,
                selected = tab,
                onSelect = { tabs.select(it) },
                focusable = false,
                scroll = true,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        if (layoutPref == "pages" && !pagesCategoryOpen) {
            // Full-page category list; picking one opens that category (Back returns here).
            DeckDragFrame(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tabTitles.forEachIndexed { i, label ->
                        DeckEditorCategoryRow(
                            label = label,
                            selected = i == tab,
                            onClick = { tabs.select(i); pagesCategoryOpen = true },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        } else {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (layoutPref == "rail") {
                DeckEditorCategoryRail(
                    labels = tabTitles,
                    selected = tab,
                    onSelect = { tabs.select(it) },
                    padFocus = false,
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                )
            }

        // After a tab switch the old page (and the focus on it) is gone: once its exit finishes, put
        // the pad on the new page's first control instead of leaving it to the first random press.
        val tabContentFocus = remember { FocusRequester() }
        var firstTabShown by remember { mutableStateOf(true) }
        LaunchedEffect(tab, pagesCategoryOpen) {
            if (firstTabShown) { firstTabShown = false; return@LaunchedEffect }
            kotlinx.coroutines.delay(220)
            runCatching { tabContentFocus.requestFocus() }
        }
        // ── Tab content — L1/R1 drag the page sideways, DroidDeck's long glide ──
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
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(tabContentFocus)
                .focusGroup(),
            label = "deckContainerTab",
        ) { currentTab ->
            DeckDragFrame(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                when (currentTab) {
                    0 -> {
                        DeckGroup("Container") {
                            DeckTextRow(
                                label = "Container Name",
                                hint = "Shown in the container list",
                                value = viewModel.containerName,
                                onValueChange = { viewModel.containerName = it },
                                width = 360.dp,
                            )
                            DeckChoiceRow(
                                label = "Screen Size",
                                options = viewModel.screenSizeEntries.map { it to it },
                                selected = viewModel.screenSizeEntries.firstOrNull {
                                    it.equals(viewModel.selectedScreenSize, ignoreCase = true)
                                } ?: viewModel.selectedScreenSize,
                                onPick = { viewModel.selectedScreenSize = it },
                            )
                            if (viewModel.selectedScreenSize.equals("custom", ignoreCase = true)) {
                                DeckTextRow(
                                    label = "Custom Width",
                                    hint = "Even pixel width, e.g. 1280",
                                    value = viewModel.customWidth,
                                    onValueChange = { viewModel.customWidth = it },
                                    width = 360.dp,
                                )
                                DeckTextRow(
                                    label = "Custom Height",
                                    hint = "Even pixel height, e.g. 720",
                                    value = viewModel.customHeight,
                                    onValueChange = { viewModel.customHeight = it },
                                    width = 360.dp,
                                )
                            }
                        }
                        DeckGroup("Behaviour") {
                            DeckRow(
                                label = "Run as Administrator",
                                hint = "Disables UAC (EnableLUA=0) inside the Wine prefix",
                                control = { DeckToggle(viewModel.runAsAdmin) { viewModel.runAsAdmin = it } },
                            )
                            DeckRow(
                                label = "Auto Close on Exit",
                                control = { DeckToggle(viewModel.autoCloseOnExit) { viewModel.autoCloseOnExit = it } },
                            )
                        }
                    }

                    1 -> {
                        DeckGroup("Graphics Driver") {
                        DeckChoiceRow(
                            label = "Graphics Driver",
                            hint = "Turnip / system Vulkan driver for Direct3D",
                            options = viewModel.graphicsDriverEntries.map { it to it },
                            selected = viewModel.selectedGraphicsDriver,
                            onPick = { viewModel.selectedGraphicsDriver = it },
                        )
                        DeckConfigSection(
                            label = "Driver Config",
                            hint = "Turnip / BCn / wrapper tuning",
                            title = "Driver Config",
                            sectionKey = "gfxDriver",
                            style = configEditorStyle,
                            openSection = openSection,
                            onOpen = { openSection = it },
                        ) {
                            DeckGraphicsDriverConfigEditor(
                                graphicsDriver = StringUtils.parseIdentifier(viewModel.selectedGraphicsDriver),
                                initialConfig = viewModel.graphicsDriverConfig,
                                    onConfirm = { viewModel.graphicsDriverConfig = it; openSection = null },
                                    onChange = { viewModel.graphicsDriverConfig = it },
                                )
                            }
                        }
                        DeckGroup("Direct3D") {
                        DeckChoiceRow(
                            label = "DX Wrapper",
                            options = viewModel.dxWrapperEntries.map { it to it },
                            selected = viewModel.selectedDXWrapper,
                            onPick = { viewModel.selectedDXWrapper = it },
                        )
                        DeckConfigSection(
                            label = "Wrapper Config",
                            hint = "DXVK / VKD3D / wined3d tuning",
                            title = "Wrapper Config",
                            sectionKey = "dxWrapper",
                            style = configEditorStyle,
                            openSection = openSection,
                            onOpen = { openSection = it },
                        ) {
                            if (StringUtils.parseIdentifier(viewModel.selectedDXWrapper)
                                    .let { it.contains("dxvk") || it.contains("vegas") }
                            ) {
                                DeckDxvkConfigEditor(
                                    isArm64EC = viewModel.isArm64EC,
                                    isVegas = StringUtils.parseIdentifier(viewModel.selectedDXWrapper).contains("vegas"),
                                    containerRootDir = viewModel.container?.rootDir,
                                    relaxDxvkFilter = StringUtils.parseIdentifier(viewModel.selectedGraphicsDriver) == "wrapper-compat-bcn",
                                    initialConfig = viewModel.dxWrapperConfig,
                                    onConfirm = { viewModel.dxWrapperConfig = it; openSection = null },
                                    onChange = { viewModel.dxWrapperConfig = it },
                                    onOpenConfigDownload = if (isVegasWrapper) { { showStockConfigSheet = true } } else null,
                                    refreshKey = dxvkRefreshKey,
                                    onDownloadDxvk = if (!isVegasWrapper) { { showDxvkDownloadSheet = true } } else null,
                                    onDownloadVkd3d = { showVkd3dDownloadSheet = true },
                                    onDownloadD7vk = { showD7vkDownloadSheet = true },
                                )
                            } else {
                                DeckWineD3DConfigEditor(
                                    initialConfig = viewModel.dxWrapperConfig,
                                    onConfirm = { viewModel.dxWrapperConfig = it; openSection = null },
                                    onChange = { viewModel.dxWrapperConfig = it },
                                )
                            }
                        }
                        }
                        DeckGroup("Renderer") {
                        DeckChoiceRow(
                            label = "Renderer",
                            options = viewModel.rendererEntries.map { it to it },
                            selected = viewModel.selectedRenderer,
                            onPick = { viewModel.selectedRenderer = it },
                        )
                        DeckConfigSection(
                            label = "Renderer Config",
                            hint = "Native render / present mode / colours",
                            title = "Renderer Config",
                            sectionKey = "renderer",
                            style = configEditorStyle,
                            openSection = openSection,
                            onOpen = { openSection = it },
                        ) {
                            DeckVulkanConfigEditor(
                                initialConfig = "native=${viewModel.rendererNative}" +
                                    ";presentMode=${viewModel.rendererPresentMode}" +
                                    ";driverId=${viewModel.rendererDriverId}" +
                                    ";filterMode=${viewModel.rendererFilterMode}" +
                                    ";swapRB=${viewModel.rendererSwapRB}",
                                frameGenSelected = viewModel.frameGenEngine != "off",
                                onConfirm = { newConfig ->
                                    applyRendererConfig(newConfig)
                                    openSection = null
                                },
                                onChange = applyRendererConfig,
                            )
                        }
                        }
                        DeckGroup("Display") {
                        DeckChoiceRow(
                            label = "Fullscreen",
                            options = listOf(
                                "Windowed" to Container.FULLSCREEN_OFF.toString(),
                                "Fit (letterboxed)" to Container.FULLSCREEN_FIT.toString(),
                                "Stretch" to Container.FULLSCREEN_STRETCH.toString(),
                            ),
                            selected = viewModel.fullscreenMode.toString(),
                            onPick = { viewModel.fullscreenMode = it.toInt() },
                        )
                        DeckConfigSection(
                            label = "Show FPS",
                            hint = "On-screen frame counter",
                            title = "FPS Counter",
                            sectionKey = "fps",
                            style = configEditorStyle,
                            openSection = openSection,
                            onOpen = { openSection = it },
                            control = { DeckToggle(viewModel.showFPS) { viewModel.showFPS = it } },
                        ) {
                            DeckFpsConfigEditor(
                                initialConfig = viewModel.fpsCounterConfig,
                                onConfirm = { viewModel.fpsCounterConfig = it; openSection = null },
                                onChange = { viewModel.fpsCounterConfig = it },
                            )
                        }
                        }
                    }

                    2 -> {
                        if (viewModel.vulkanSupported) {
                        DeckGroup("Frame Generation") {
                        DeckChoiceRow(
                            label = "Frame Generation",
                            hint = if (viewModel.frameGenEngine != "off" &&
                                viewModel.selectedRenderer != "Vulkan")
                                "LSFG Native needs Vulkan — the Renderer switches on pick"
                            else "LSFG Native frame interpolation",
                            options = listOf(
                                "off" to "Off",
                                "lsfg-native" to "LSFG",
                            ),
                            selected = viewModel.frameGenEngine,
                            onPick = {
                                viewModel.frameGenEngine = it
                                if (BuildConfig.DEBUG)
                                    android.util.Log.d("FrameGen", "container editor picked [" + it + "] -> form frameGenEngine=[" + viewModel.frameGenEngine + "]")
                                if (it != "off" && viewModel.selectedRenderer != "Vulkan")
                                    viewModel.selectedRenderer = "Vulkan"
                            },
                        )
                        DeckRow(
                            label = "LSFG Performance Mode",
                            control = { DeckToggle(viewModel.lsfgPerformanceMode) { viewModel.lsfgPerformanceMode = it } },
                        )
                        DeckRow(
                            label = "LSFG Auto Enable",
                            hint = "Start frame generation live at launch",
                            control = { DeckToggle(viewModel.lsfgAutoEnable) { viewModel.lsfgAutoEnable = it } },
                        )
                        }
                        }
                        DeckGroup("Framerate") {
                        DeckRow(
                            label = "FPS Limiter",
                            hint = "Loads the limiter layer; the cap is tuned in-game",
                            control = { DeckToggle(viewModel.fpsLimiterEnabled) { viewModel.fpsLimiterEnabled = it } },
                        )
                        DeckRow(
                            label = "Match Refresh Rate",
                            hint = "Sync the panel refresh to the game's FPS",
                            control = { DeckToggle(viewModel.matchRefreshRate) { viewModel.matchRefreshRate = it } },
                        )
                        }
                    }

                    3 -> {
                        if (viewModel.emulatorEnabled) {
                            DeckGroup("Emulator") {
                                DeckChoiceRow(
                                    label = "Emulator",
                                    options = viewModel.emulatorEntries.map { it to it },
                                    selected = viewModel.selectedEmulator,
                                    onPick = { viewModel.selectedEmulator = it },
                                )
                            }
                        }
                        DeckGroup("Box64") {
                        DeckChoiceRow(
                            label = "Box64 Version",
                            options = viewModel.box64VersionEntries.map { it to it },
                            selected = viewModel.selectedBox64Version,
                            onPick = { viewModel.selectedBox64Version = it },
                        )
                        DeckChoiceRow(
                            label = "Box64 Preset",
                            options = viewModel.box64PresetEntries.mapIndexed { i, e -> i.toString() to e },
                            selected = viewModel.selectedBox64PresetIndex.toString(),
                            onPick = { viewModel.selectedBox64PresetIndex = it.toInt() },
                        )
                        }
                        if (viewModel.isArm64EC) {
                            DeckGroup("FEXCore") {
                            DeckChoiceRow(
                                label = "FEXCore Version",
                                options = viewModel.fexCoreVersionEntries.map { it to it },
                                selected = viewModel.selectedFEXCoreVersion,
                                onPick = { viewModel.selectedFEXCoreVersion = it },
                            )
                            DeckChoiceRow(
                                label = "FEXCore Preset",
                                options = viewModel.fexCorePresetEntries.mapIndexed { i, e -> i.toString() to e },
                                selected = viewModel.selectedFEXCorePresetIndex.toString(),
                                onPick = { viewModel.selectedFEXCorePresetIndex = it.toInt() },
                            )
                            }
                        }
                    }

                    4 -> {
                        DeckGroup("Input") {
                        DeckRow(
                            label = "Exclusive Input",
                            hint = "One controller API at a time (XInput or DInput). Off lets Wine expose both.",
                            control = {
                                DeckToggle(viewModel.exclusiveXInput) {
                                    viewModel.onExclusiveXInputChanged(it)
                                }
                            },
                        )
                        DeckRow(
                            label = "XInput",
                            control = { DeckToggle(viewModel.enableXInput) { viewModel.enableXInput = it } },
                        )
                        DeckRow(
                            label = "DInput",
                            control = { DeckToggle(viewModel.enableDInput) { viewModel.enableDInput = it } },
                        )
                        }
                        DeckGroup("Feedback") {
                        DeckChoiceRow(
                            label = "Controller Vibration",
                            options = listOf(
                                "Off" to Container.VIBRATION_MODE_OFF.toString(),
                                "Controller" to Container.VIBRATION_MODE_CONTROLLER.toString(),
                                "Device" to Container.VIBRATION_MODE_DEVICE.toString(),
                                "Both" to Container.VIBRATION_MODE_BOTH.toString(),
                            ),
                            selected = viewModel.vibrationMode.toString(),
                            onPick = { viewModel.vibrationMode = it.toInt() },
                        )
                        DeckSliderRow(
                            label = "Vibration Intensity",
                            value = viewModel.vibrationIntensity.toFloat(),
                            range = 0f..100f,
                            format = { "${it.toInt()}%" },
                            onChange = { viewModel.vibrationIntensity = it.toInt() },
                        )
                        DeckRow(
                            label = "Gyroscope Aim",
                            hint = "Motion-controlled aiming default for this container",
                            control = { DeckToggle(viewModel.gyroEnabled) { viewModel.gyroEnabled = it } },
                        )
                        }
                    }

                    5 -> {
                        DeckGroup("Environment") {
                        DeckConfigSection(
                            label = "Env Vars",
                            hint = "KEY=value pairs",
                            title = "Env Vars",
                            sectionKey = "envVars",
                            style = configEditorStyle,
                            openSection = openSection,
                            onOpen = { openSection = it },
                        ) {
                            DeckEnvVarsEditor(
                                value = viewModel.envVarsStr,
                                onValueChange = { viewModel.envVarsStr = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        }
                        DeckGroup("Startup") {
                        DeckChoiceRow(
                            label = "Startup Selection",
                            options = viewModel.startupSelectionEntries.mapIndexed { i, e -> i.toString() to e },
                            selected = viewModel.selectedStartupSelection.toString(),
                            onPick = { viewModel.selectedStartupSelection = it.toInt() },
                        )
                        }
                    }

                    else -> {
                        DeckGroup("Wine") {
                            // Wine version is only settable when CREATE-ing: an existing container's
                            // applyFormTo never writes wineVersion (the classic editor locks the row
                            // for the same reason), so leaving it editable here made a wine change
                            // look accepted and then silently revert on reopen. Show it only when it
                            // can actually be saved, and route the pick through onWineVersionChanged
                            // so the arch-dependent defaults (emulator / box64 / FEXCore) re-seed.
                            if (viewModel.wineVersionEnabled) {
                                DeckChoiceRow(
                                    label = "Wine Version",
                                    hint = "The Wine / Proton layer this container runs on",
                                    options = viewModel.wineVersionEntries.map { it to it },
                                    selected = viewModel.selectedWineVersion,
                                    onPick = { viewModel.onWineVersionChanged(it) },
                                )
                            }
                            DeckChoiceRow(
                                label = "Audio Driver",
                                options = viewModel.audioDriverEntries.map { it to it },
                                selected = viewModel.selectedAudioDriver,
                                onPick = { viewModel.selectedAudioDriver = it },
                            )
                            DeckChoiceRow(
                                label = "Language",
                                options = viewModel.lcAllEntries.map { it to it },
                                selected = viewModel.lcAll,
                                onPick = { viewModel.lcAll = it },
                            )
                        }
                        DeckGroup("Desktop") {
                            DeckChoiceRow(
                                label = "Theme",
                                options = listOf("0" to "Light", "1" to "Dark"),
                                selected = viewModel.desktopThemeIndex.toString(),
                                onPick = { viewModel.desktopThemeIndex = it.toInt() },
                            )
                            DeckChoiceRow(
                                label = "Mouse Warp",
                                options = viewModel.mouseWarpEntries.mapIndexed { i, e -> i.toString() to e },
                                selected = viewModel.selectedMouseWarpIndex.toString(),
                                onPick = { viewModel.selectedMouseWarpIndex = it.toInt() },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
            }
        }
        }
        }

        // ── Config panels (the classic editor's per-row settings dialogs) ──
        // Config sections render inline (DeckExpandableRow) in their own tabs above — nothing to
        // overlay here anymore. `openSection` just drives which one is expanded.

        // ── Save bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckButton("Cancel", primary = false) { onNavigateBack() }
            Spacer(Modifier.weight(1f))
            DeckButton(saveLabel, primary = true) {
                if (!viewModel.isSaving) viewModel.confirm(
                    resolvedGraphicsDriverConfig = viewModel.graphicsDriverConfig,
                    resolvedDXWrapperConfig      = viewModel.dxWrapperConfig,
                    resolvedFPSCounterConfig     = viewModel.fpsCounterConfig,
                    resolvedEnvVars              = viewModel.envVarsStr,
                    resolvedCPUList              = viewModel.cpuList,
                    resolvedCPUListWoW64         = viewModel.cpuListWoW64,
                    resolvedColorAsString        = deckColorAsString(viewModel.desktopBgColorInt),
                    onDone                       = onNavigateBack,
                )
            }
        }
    }

    if (showStockConfigSheet) {
        DeckStockConfigSheet(onDismiss = { showStockConfigSheet = false; dxvkRefreshKey++ }, onContentChanged = { dxvkRefreshKey++ })
    }

    if (showDxvkDownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_DXVK),
            onDismiss = { showDxvkDownloadSheet = false },
            onContentChanged = { dxvkRefreshKey++ },
        )
    }

    if (showVkd3dDownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_VKD3D),
            onDismiss = { showVkd3dDownloadSheet = false },
            onContentChanged = { dxvkRefreshKey++ },
        )
    }

    if (showD7vkDownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_D7VK),
            onDismiss = { showD7vkDownloadSheet = false },
            onContentChanged = { dxvkRefreshKey++ },
        )
    }
}

/** androidx Color int (ARGB) -> the "#RRGGBB" string the classic ColorPickerView exposed. */
private fun deckColorAsString(argb: Int): String =
    String.format(Locale.US, "#%06X", 0xFFFFFF and argb)

/**
 * One config editor row. "dropdown" keeps the classic inline [DeckExpandableRow] (the editor
 * expands under the row); "popup" shows a plain [DeckRow] with a config chevron that opens the
 * editor in a [DeckConfigDialog] — the same popup the shortcut editor uses. The editor content is
 * identical in both modes, so every field/behaviour stays in one place.
 */
@Composable
private fun DeckConfigSection(
    label: String,
    hint: String,
    title: String,
    sectionKey: String,
    style: String,
    openSection: String?,
    onOpen: (String?) -> Unit,
    control: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (style == "dropdown") {
        DeckExpandableRow(
            label = label,
            hint = hint,
            expanded = openSection == sectionKey,
            onToggle = { onOpen(if (openSection == sectionKey) null else sectionKey) },
            control = control,
            content = content,
        )
    } else {
        DeckRow(
            label = label,
            hint = hint,
            configPanel = true,
            onConfigClick = { onOpen(sectionKey) },
            control = control,
        )
        if (openSection == sectionKey) {
            DeckConfigDialog(title = title, onDismiss = { onOpen(null) }, content = content)
        }
    }
}

/**
 * "rail" layout: the category list down the left, with the animated accent bar + tinted background
 * that stretches onto the selected item (the same DroidDeck feel as the top tab pill).
 */
@Composable
internal fun DeckEditorCategoryRail(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dots: List<Color>? = null,
    // Landing on a row selects it (D-pad browsing), and the selected row can be refocused by L2/R2.
    selectOnFocus: Boolean = false,
    selectedFocus: FocusRequester? = null,
    // False for sub-tab rails: the pad switches them with L2/R2 only, so D-pad focus never lands on them (touch still taps).
    padFocus: Boolean = true,
) {
    Column(
        // Scrollable: with many categories the tail ran off the bottom of the rail, so those
        // options were visible-ish but unreachable (no way to scroll, and D-pad focus could not
        // bring them on screen). Compose auto-scrolls to the focused row once it can scroll.
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selected
            val bg by animateColorAsState(
                targetValue = if (isSel) Deck.secondary.copy(alpha = 0.18f) else Color.Transparent,
                animationSpec = tween(220, easing = DeckEase),
                label = "railItemBg",
            )
            val accent by animateDpAsState(
                targetValue = if (isSel) 4.dp else 0.dp,
                animationSpec = tween(240, easing = DeckEase),
                label = "railItemAccent",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .focusProperties { canFocus = padFocus }
                    .then(if (isSel && selectedFocus != null) Modifier.focusRequester(selectedFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused && selectOnFocus && !isSel) onSelect(i) }
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(accent)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Deck.secondary),
                )
                Spacer(Modifier.width(10.dp))
                dots?.getOrNull(i)?.let { dot ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    color = if (isSel) Deck.onBackground else Deck.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * "pages" layout: one full-width card per category on its own page; tapping opens that category.
 */
@Composable
internal fun DeckEditorCategoryRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Deck.secondary.copy(alpha = 0.18f) else Deck.surface,
        animationSpec = tween(220, easing = DeckEase),
        label = "pageItemBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) Deck.onBackground else Deck.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text("\u203A", color = Deck.onSurfaceVariant, fontSize = 18.sp)
    }
}

