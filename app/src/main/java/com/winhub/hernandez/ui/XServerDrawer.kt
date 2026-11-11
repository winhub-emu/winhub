package com.winhub.hernandez.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.winhub.hernandez.ui.components.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.winhub.hernandez.R
import com.winhub.hernandez.inputcontrols.StickShaper
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.core.ControllerIdentity
import com.winhub.hernandez.core.StickPacing
import com.winhub.hernandez.perf.PerfGpuTurbo
import com.winhub.hernandez.perf.PerfRevertRegistry
import com.winhub.hernandez.perf.PerfRootApplier
import com.winhub.hernandez.perf.RootManager
import com.winhub.hernandez.reshade.ReshadeLoadout
import com.winhub.hernandez.reshade.ReshadeManager
import com.winhub.hernandez.ui.components.ColorPicker
import com.winhub.hernandez.ui.screens.HelpDialog
import com.winhub.hernandez.ui.screens.DeckPalette
import com.winhub.hernandez.ui.screens.DeckSound
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.WatchdogSection
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import com.winhub.hernandez.ui.theme.LocalAccentDim
import com.winhub.hernandez.ui.theme.WinlatorTheme
import com.winhub.hernandez.widget.fusionhud.parseHudOutline
import com.winhub.hernandez.widget.exportHudDiagnostics

// Accent colors route to the live MaterialTheme.colorScheme (primary/surface) so the drawer
// follows the user's theme preset / custom accent. The dim accent (low-emphasis fills/borders/
// tracks) routes to LocalAccentDim.current — AMOLED maps that to the exact legacy #002277 so the
// default look stays identical, while other presets/custom accents recolor it.
// Phase 2: the neutral surface/text/divider constants are gone — call sites read
// MaterialTheme.colorScheme directly (surface / onSurface / onSurfaceVariant / outline) so the
// whole drawer (every tab) follows the theme. AMOLED's tokens match the legacy neutrals closely,
// so the default look stays near-identical.
// PureBlack is kept only for spots that need a true literal black that must NOT theme (the
// color-picker knob outline); panel/rail backgrounds route to colorScheme.surface instead.
private val PureBlack = Color(0xFF000000)
private val ToggleTrackOff = Color(0xFF333333)
private val ToggleThumbOff = Color(0xFF666666)

fun setupComposeView(view: ComposeView) {
    view.setContent {
        WinlatorTheme {
            XServerDrawer()
        }
    }
}

/** Deck palette forced for the in-game drawer: graphite surfaces, one signal accent. */
private val DeckDrawerScheme get() = darkColorScheme(
    primary = DeckPalette.primary,
    onPrimary = DeckPalette.onPrimary,
    primaryContainer = DeckPalette.drawerContainer,
    onPrimaryContainer = DeckPalette.drawerOnContainer,
    secondary = DeckPalette.onSurfaceVariant,
    onSecondary = DeckPalette.onPrimary,
    secondaryContainer = DeckPalette.surfaceVariant,
    onSecondaryContainer = DeckPalette.onBackground,
    tertiary = DeckPalette.drawerTertiary,
    onTertiary = DeckPalette.onPrimary,
    background = DeckPalette.background,
    onBackground = DeckPalette.onBackground,
    surface = DeckPalette.surface,
    onSurface = DeckPalette.onBackground,
    surfaceVariant = DeckPalette.surfaceVariant,
    onSurfaceVariant = DeckPalette.onSurfaceVariant,
    surfaceContainer = DeckPalette.surface,
    surfaceContainerLow = DeckPalette.surface,
    surfaceContainerLowest = DeckPalette.background,
    surfaceContainerHigh = DeckPalette.surfaceVariant,
    surfaceContainerHighest = DeckPalette.surfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = DeckPalette.onBackground,
    inverseOnSurface = DeckPalette.background,
    error = DeckPalette.error,
    onError = DeckPalette.onPrimary,
    errorContainer = Color(0xFF3A1416),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = DeckPalette.line2,
    outlineVariant = DeckPalette.line,
    scrim = Color.Black,
)

/** Deck's dim accent rides in LocalAccentDim, which most drawer controls use for fills/tracks. */
private val DeckDrawerAccentDim get() = DeckPalette.drawerDim

@Composable
fun XServerDrawer() {
    MaterialTheme(
        colorScheme = DeckDrawerScheme,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalAccentDim provides DeckDrawerAccentDim) {
            XServerDrawerContent()
        }
    }
}

@Composable
private fun XServerDrawerContent() {
    val state = XServerDrawerState
    val selectedTab by state.selectedTab.collectAsState()
    val isPaused by state.isPaused.collectAsState()
    val tvConnected by state.tvConnected.collectAsState()
    val castSupported by state.castSupported.collectAsState()
    val gameTitle by state.gameTitle.collectAsState()
    // Friends tab: present only while a live friends source exists for this launch (agent relay during
    // a SteamLite game, else the app's own session); unread dot = a message landed while the thread
    // wasn't open. See XServerFriendsTab.kt.
    val friendsSource by com.winhub.hernandez.store.InGameFriendsSource.state.collectAsState()
    val friendsUnread by com.winhub.hernandez.store.SteamFriendsStore.unread.collectAsState()
    // Re-check the friends source on every drawer open (its liveness isn't all flow-driven).
    val menuOpen by XServerDialogState.menuOpen.collectAsState()
    LaunchedEffect(menuOpen) { if (menuOpen) com.winhub.hernandez.store.InGameFriendsSource.poke() }
    val clock = rememberDrawerClock()
    val appContext = LocalContext.current
    LaunchedEffect(Unit) { DeckSound.init(appContext) }

    // The header line shows the focused tile / tab's name for a moment, like a console tooltip.
    var hint by remember { mutableStateOf<String?>(null) }
    fun hintFor(label: String): (Boolean) -> Unit = { focused -> if (focused) hint = label else if (hint == label) hint = null }
    var confirmExit by remember { mutableStateOf(false) }
    var exitTried by remember { mutableStateOf(false) }
    val resumeFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    // The controller starts on Resume whenever the menu opens; the exit confirmation starts on Cancel.
    LaunchedEffect(menuOpen) { if (menuOpen) runCatching { resumeFocus.requestFocus() } }
    LaunchedEffect(confirmExit) {
        if (confirmExit) runCatching { cancelFocus.requestFocus() }
        else if (exitTried) runCatching { exitFocus.requestFocus() }
    }

    val mods by state.mods.collectAsState()
    val hasMods = mods.isNotEmpty()
    LaunchedEffect(hasMods, selectedTab) {
        if (!hasMods && selectedTab == TabType.MODS) state.selectTab(TabType.QUICK)
    }
    val stripTabs = remember(friendsSource.tabVisible, tvConnected, castSupported, hasMods) {
        buildList {
            add(TabType.QUICK)
            add(TabType.GRAPHICS)
            add(TabType.HUD)
            add(TabType.RESHADE)
            add(TabType.CONTROLS)
            add(TabType.AUDIO)
            add(TabType.ADVANCED)
            if (hasMods) add(TabType.MODS)
            if (friendsSource.tabVisible) add(TabType.FRIENDS)
            if (com.winhub.hernandez.FeatureFlags.TV_OUTPUT_ENABLED && (tvConnected || castSupported)) add(TabType.TV)
            add(TabType.TASK_MANAGER)
        }
    }
    fun stepTab(delta: Int) {
        val i = stripTabs.indexOf(selectedTab).coerceAtLeast(0)
        handleTabClick(stripTabs[(i + delta + stripTabs.size) % stripTabs.size], state)
    }
    val unreadTotal = friendsUnread.values.sum()

    // ── Shell: a quick-menu panel docked to the right edge — glass over the live game, a header with
    // the game and the session actions, a tab rail, the open tab's page, and a button-hint bar. B and
    // the bumpers work from anywhere in it (A is turned into the confirm key by the activity).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonB -> { DeckSound.play(DeckSound.BACK); if (confirmExit) confirmExit = false else state.onClose?.run(); true }
                    Key.ButtonL1 -> { DeckSound.play(DeckSound.MOVE); stepTab(-1); true }
                    Key.ButtonR1 -> { DeckSound.play(DeckSound.MOVE); stepTab(1); true }
                    else -> false
                }
            },
    ) {
        // Touch sink, bottom-most child: eat every event on the empty panel and gutters so a tap
        // can never fall through to the game surface / virtual controls underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawerGlass(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        ) {
            // Header: game + status on the left, the session actions on the right.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = gameTitle.ifBlank { "In game" },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckPalette.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hint ?: if (isPaused) "Paused  ·  $clock" else clock,
                        fontSize = 13.sp,
                        color = if (hint != null) DeckPalette.primary else DeckPalette.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (confirmExit) {
                    Text("Exit game?", color = DeckPalette.onBackground, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                    DrawerPill("Cancel", danger = false, focusRequester = cancelFocus) { confirmExit = false }
                    Spacer(Modifier.width(6.dp))
                    DrawerPill("Exit", danger = true) { state.onExit?.run() }
                } else {
                    DrawerTile(R.drawable.icon_play, "Resume", focusRequester = resumeFocus, onFocus = hintFor("Resume")) {
                        if (isPaused) state.onPauseResume?.run()
                        state.onClose?.run()
                    }
                    if (!isPaused) {
                        Spacer(Modifier.width(6.dp))
                        DrawerTile(R.drawable.icon_pause, "Pause", onFocus = hintFor("Pause")) {
                            state.onPauseResume?.run(); state.onClose?.run()
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    DrawerTile(R.drawable.icon_keyboard, "Keyboard", onFocus = hintFor("Keyboard")) {
                        state.onClose?.run(); state.onKeyboard?.run()
                    }
                    Spacer(Modifier.width(6.dp))
                    DrawerTile(R.drawable.icon_task_manager, "Task manager", onFocus = hintFor("Task manager")) {
                        state.selectTab(TabType.TASK_MANAGER)
                        state.onTaskManager?.run()
                    }
                    Spacer(Modifier.width(6.dp))
                    DrawerTile(
                        R.drawable.icon_exit, "Exit game", tint = DeckPalette.error,
                        focusRequester = exitFocus, onFocus = hintFor("Exit game"),
                    ) { exitTried = true; confirmExit = true }
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DeckPalette.line),
            )
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Tab rail: icons only; the focused one names itself in the header.
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    stripTabs.forEach { tab ->
                        DrawerRailItem(
                            icon = drawerTabIcon(tab),
                            label = tabLabel(tab),
                            selected = tab == selectedTab,
                            unread = if (tab == TabType.FRIENDS) unreadTotal else 0,
                            onFocus = hintFor(tabLabel(tab)),
                        ) { handleTabClick(tab, state) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(DeckPalette.line),
                )
                Spacer(Modifier.width(10.dp))
                // The selected tab's page. Friends owns its own scrolling (LazyColumn roster/thread
                // with a pinned send box), so it opts out of the page's verticalScroll.
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        text = tabLabel(selectedTab),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckPalette.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(if (selectedTab == TabType.FRIENDS) Modifier else Modifier.verticalScroll(rememberScrollState()))
                            .padding(horizontal = 2.dp),
                    ) {
                        when (selectedTab) {
                            TabType.QUICK -> DrawerQuickPage { handleTabClick(it, state) }
                            TabType.GRAPHICS -> GraphicsContent(state)
                            TabType.HUD -> HudContent(state)
                            TabType.RESHADE -> ReshadeContent(state)
                            TabType.CONTROLS -> ControlsContent(state)
                            TabType.ADVANCED -> AdvancedContent(state)
                            TabType.TASK_MANAGER -> TmContent()
                            TabType.TV -> TvContent(state)
                            TabType.AUDIO -> AudioContent(state)
                            TabType.FRIENDS -> FriendsContent(state)
                            TabType.MODS -> ModsContent(state, mods)
                        }
                    }
                }
            }

            DrawerHintBar(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp))
        }
    }
}

private fun tabLabel(tab: TabType): String = when (tab) {
    TabType.QUICK -> "Quick"
    TabType.GRAPHICS -> "Graphics"
    TabType.HUD -> "FPS"
    TabType.RESHADE -> "Effects"
    TabType.CONTROLS -> "Controls"
    TabType.AUDIO -> "Audio"
    TabType.ADVANCED -> "Advanced"
    TabType.FRIENDS -> "Friends"
    TabType.TV -> "TV"
    TabType.MODS -> "Mods"
    TabType.TASK_MANAGER -> "Tasks"
}

private fun handleTabClick(tab: TabType, state: XServerDrawerState) {
    DeckSound.play(DeckSound.SELECT)
    if (tab == TabType.TASK_MANAGER && state.selectedTab.value != tab) state.onTaskManager?.run()
    state.selectTab(tab)
}

// ───── TV / External Display tab ─────
// Version A: game on the TV, handheld as the controller. This minimal panel exposes the display
// controls; picture/latency controls (aspect, overscan, latency mode, audio) land in a later pass.
// In-game Audio tab: adaptive presets + fine-tuning, applied LIVE via onReapplyAudio (sink recreate).
// Guest-buffer latency is fixed at connect, so that one knob is flagged "next launch" in the dialog.
@Composable
private fun AudioContent(state: XServerDrawerState) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    val driverId = state.audioDriverId
    var cfg by remember { mutableStateOf(com.winhub.hernandez.ui.components.loadAudioConfig(ctx, driverId)) }
    Text(
        "Audio",
        fontSize = 18.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(2.dp))
    val engine = state.audioDriverLabel
    Text(
        if (engine.isNotBlank()) "Engine: $engine  ·  preset: ${cfg.preset}" else "Current preset: ${cfg.preset}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp
    )
    Spacer(Modifier.height(12.dp))
    AccentButton("Presets & fine-tuning", Modifier.fillMaxWidth()) { show = true }
    Text(
        "Balance crackle vs delay. Applies live; guest buffer needs a relaunch.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
    Spacer(Modifier.height(12.dp))
    AccentButton("Reset audio", Modifier.fillMaxWidth()) { state.onResetAudio?.run() }
    Text(
        "Fixes lost sound after switching apps.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (show) {
        com.winhub.hernandez.ui.components.AudioSettingsDialog(
            initial = cfg,
            scopeLabel = "live · this session",
            latencyLive = false,
            driverLabel = engine,
            driverId = driverId,
            onDismiss = { show = false },
            onSave = { newCfg ->
                com.winhub.hernandez.ui.components.saveAudioConfig(ctx, driverId, newCfg)
                cfg = newCfg
                state.onReapplyAudio?.run()
                show = false
            }
        )
    }
}

@Composable
private fun TvContent(state: XServerDrawerState) {
    val tvConnected by state.tvConnected.collectAsState()
    val displayName by state.tvDisplayName.collectAsState()
    val playOnTv by state.tvPlayOnTv.collectAsState()
    val autoSwap by state.tvAutoSwap.collectAsState()
    val onExternal by state.tvGameOnExternal.collectAsState()
    val modes by state.tvModes.collectAsState()
    val currentModeId by state.tvCurrentModeId.collectAsState()
    val hdr by state.tvHdr.collectAsState()

    // ───────────── Wireless cast (screen mirroring — no app on the TV) ─────────────
    SectionHeader("Cast to a TV (wireless)")
    Text(
        text = "⚠ EXPERIMENTAL · VIDEO ONLY — wireless streaming is new: the picture runs a few seconds " +
            "behind, there's no TV sound yet (game audio stays on this device), and it may take a moment " +
            "to start or need a second try.",
        color = MaterialTheme.colorScheme.error,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    Text(
        text = "Stream the game's picture to a Google TV / Chromecast on your Wi-Fi — pick one in the app, " +
            "nothing to install on the TV. Tap the “?” inside for how it works and the trade-offs.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    AccentButton("Cast to a TV", Modifier.fillMaxWidth()) {
        state.onOpenCastPicker?.run()
    }
    Text(
        text = "For the lowest lag, a wired USB-C→HDMI cable is still best.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp)
    )

    // ───────────── Wired / external display (only when one is actually connected) ─────────────
    if (tvConnected) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
        SectionHeader("TV / External Display")

        Text(
            text = if (displayName.isNotBlank()) "Connected: $displayName" else "External display connected",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Output resolution + refresh rate of the TV (e.g. switch 4K@30 → 1080p@60 for smoother play).
        if (modes.isNotEmpty()) {
            val labels = modes.map { it.label }
            val selectedIdx = modes.indexOfFirst { it.id == currentModeId }.let { if (it >= 0) it else 0 }
            ReshadeDropdown("Display mode (resolution & refresh)", labels, selectedIdx) { i ->
                val id = modes[i].id
                state.setTvCurrentModeId(id)
                state.onTvModeChange?.accept(id)
            }
            Spacer(Modifier.height(6.dp))
        }

        if (hdr.isNotBlank()) {
            Text(
                text = "HDR supported by display: $hdr",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ToggleRow("Play on TV", playOnTv) {
            state.setTvPlayOnTv(it)
            state.onTvPlayOnTvChange?.accept(it)
        }
        ToggleRow("Auto-switch on connect", autoSwap, enabled = playOnTv) {
            state.setTvAutoSwap(it)
            state.onTvAutoSwapChange?.accept(it)
        }

        Spacer(Modifier.height(12.dp))

        if (onExternal) {
            AccentButton("Bring game back to handheld", Modifier.fillMaxWidth()) {
                state.onBringBackFromTv?.run()
            }
        } else {
            AccentButton("Move game to TV", Modifier.fillMaxWidth()) {
                state.onMoveToTv?.run()
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Rebuild the audio route — sound can drop after backgrounding or an HDMI route change.
    AccentButton("Reset audio", Modifier.fillMaxWidth()) {
        state.onResetAudio?.run()
    }
    Text(
        text = "Fixes lost sound after switching apps.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp)
    )


    val rendererIsVulkan by state.rendererIsVulkan.collectAsState()

    // ───────────── Picture ─────────────
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
    SectionHeader("Picture")

    // Aspect on TV — reuses the same fullscreen-mode cycle as the handheld (OFF / FIT / STRETCH).
    val fullscreenMode by state.fullscreenMode.collectAsState()
    Text("Aspect", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
    FullscreenModeButtons(fullscreenMode) { mode ->
        state.setFullscreenMode(mode)
        state.onSetFullscreenMode?.accept(mode)
    }

    // Overscan / safe area (v2) — pads the game inward on TVs that crop the edges.
    Spacer(Modifier.height(8.dp))
    val overscan by state.tvOverscan.collectAsState()
    var overscanVal by remember(overscan) { mutableIntStateOf(overscan) }
    IntSlider("Overscan / safe area", overscanVal, 0..8,
        onValueChange = { overscanVal = it },
        onValueChangeFinished = {
            state.setTvOverscan(overscanVal)
            state.onTvOverscanChange?.accept(overscanVal)
        },
        steps = 7, enabled = true)
    Text("Shrinks the picture inward if your TV cuts off the edges.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

    // ───────────── Latency & pacing ─────────────
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))

    // Latency mode (present mode). PresentModeSection self-gates to the Vulkan renderer.
    if (rendererIsVulkan) {
        PresentModeSection(state)
    } else {
        SectionHeader("Latency")
        Text("Latency mode (V-Sync / Low latency) is available on the Vulkan renderer.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp))
    }

    // Frame cap — drives the standalone host FPS limiter (shared with the HUD tab).
    Spacer(Modifier.height(6.dp))
    val fpsLimiterEnabled by state.fpsLimiterEnabled.collectAsState()
    val fpsLimit by state.fpsLimit.collectAsState()
    val capOptions = listOf("Off", "30 FPS", "60 FPS", "90 FPS", "120 FPS")
    val capValues = listOf(0, 30, 60, 90, 120)
    val capIdx = if (!fpsLimiterEnabled) 0 else capValues.indexOf(fpsLimit).let { if (it >= 0) it else 0 }
    ReshadeDropdown("Frame cap", capOptions, capIdx) { i ->
        val v = capValues[i]
        state.setFpsLimiterEnabled(v > 0)
        if (v > 0) state.setFpsLimit(v)
        state.onFpsLimitChange?.run()
    }

    // Frame generation — the full reused section (engine picker + multiplier + models).
    Spacer(Modifier.height(6.dp))
    FrameGenSection(state)

    // TV Game Mode tip (biggest wired-latency win is TV-side; we can only advise).
    Text("Tip: enable Game Mode on your TV for the lowest input lag.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp))

    // ───────────── Audio & power ─────────────
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
    SectionHeader("Audio & power")

    val audioOut by state.tvAudioOut.collectAsState()
    ReshadeDropdown("Audio output", listOf("Follow system", "TV / HDMI", "Handheld"), audioOut) { i ->
        state.setTvAudioOut(i)
        state.onTvAudioOutChange?.accept(i)
    }
    Text("Experimental — the guest audio route may not always follow.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

    val dimHandheld by state.tvDimHandheld.collectAsState()
    ToggleRow("Dim handheld while on TV", dimHandheld) {
        state.setTvDimHandheld(it)
        state.onTvDimHandheldChange?.accept(it)
    }
    Text("Saves battery and heat by dimming the phone screen.",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp))

    // ───────────── Advanced ─────────────
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
    SectionHeader("Advanced")

    val renderRes by state.tvRenderRes.collectAsState()
    ReshadeDropdown("TV render resolution", listOf("Match TV", "Match handheld", "1080p", "1440p"), renderRes) { i ->
        state.setTvRenderRes(i)
        state.onTvRenderResChange?.accept(i)
    }
    Text("Applies on the next game launch (the render resolution is fixed at startup).",
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp))

    // ───────────── Streaming (v3 — WiFi caster, not yet available) ─────────────
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 12.dp))
    val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Text("Streaming", style = MaterialTheme.typography.titleSmall.copy(
        fontSize = 15.sp, fontWeight = FontWeight.Bold), color = disabledColor)
    Text("Wireless streaming to a TV without a cable (bitrate, codec, transport) is coming in a " +
        "future update. Wired HDMI / DeX casting works today via the controls above.",
        color = disabledColor, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    listOf("Bitrate", "Codec (H.264 / HEVC)", "Transport (WebRTC / DLNA)", "Stream resolution & FPS").forEach {
        Text("• $it — requires WiFi streaming", color = disabledColor, fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp))
    }

    Spacer(Modifier.height(12.dp))
}

// ───── Section Header ─────

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(DeckPalette.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
            color = DeckPalette.primary,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(DeckPalette.line),
        )
    }
}

// ───── Modern Toggle Row ─────

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDim = LocalAccentDim.current
    val shape = RoundedCornerShape(12.dp)
    // The whole row is the one focus target (A / tap flips it); the switch itself is display-only.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.consoleFocus(shape) else Modifier)
            .clip(shape)
            .border(1.dp, DeckPalette.line, shape)
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier.alpha(0.4f))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accentDim,
                checkedBorderColor = accent,
                uncheckedThumbColor = ToggleThumbOff,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = DeckPalette.line2,
            )
        )
    }
}

// ───── Modern Slider Row ─────

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    format: (Float) -> String = { "%.0f".format(it) }
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .then(if (enabled) Modifier.consoleFocus(shape) else Modifier.alpha(0.4f))
            .clip(shape)
            .border(1.dp, DeckPalette.line, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = format(value),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished ?: {},
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = ToggleTrackOff,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ───── Modern Accent Button ─────

@Composable
private fun AccentButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accentDim = LocalAccentDim.current
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(42.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DeckPalette.primary,
            contentColor = DeckPalette.onPrimary
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

// ───── Graphics Tab ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphicsContent(state: XServerDrawerState) {
    val accent = MaterialTheme.colorScheme.primary

    // Title on the left, runtime-backend diagnostic chip pinned top-right.
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { SectionHeader("Graphics") }
        RuntimeBackendChip(state)
    }

    // Frame Generation pinned to the top of the Graphics tab.
    FrameGenSection(state)

    // Present Mode selector (Vulkan renderer only) — directly under Frame Generation because the two
    // interact: while FG multiplies, the host present mode is forced to Mailbox (reflected live here).
    PresentModeSection(state)

    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

    // Fullscreen aspect-ratio mode (#71 Stage 2): a segmented selector (Off/Fit/Stretch/Fill/Integer)
    // that sets the mode live WITHOUT closing the drawer, so the user can compare modes before
    // dismissing it — same box-chip idiom as the Scaling-mode row.
    val fullscreenMode by state.fullscreenMode.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.fullscreen_mode),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(R.drawable.icon_fullscreen),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
    }
    Spacer(Modifier.height(6.dp))
    FullscreenModeButtons(selected = fullscreenMode) { state.onSetFullscreenMode?.accept(it) }

    // Screen alignment (#413): Center/Top/Bottom, live like the mode above. Now applies in EVERY
    // fullscreen mode — TOP/BOTTOM confine the game (Fit/Fill/Stretch/Integer alike) to its half and
    // leave the other half for the controls, so the control is always live (no more STRETCH/FILL greying).
    val screenAlignment by state.screenAlignment.collectAsState()
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.screen_alignment),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(6.dp))
    ScreenAlignmentButtons(selected = screenAlignment, enabled = true) {
        state.onSetScreenAlignment?.accept(it)
    }

    Spacer(Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

    // Renderer-specific graphics controls. Each host renderer has its own set, so
    // show ONLY the set that applies to the active renderer instead of packing the
    // tab with disabled rows: Scaling mode on Vulkan (the only host renderer).
    val vulkanSupported  by XServerDialogState.vulkanSupported.collectAsState()  // Vulkan renderer

    val nativeRenderingEnabled by state.nativeRenderingEnabled.collectAsState()
    // Wayland: the whole effect/scaling chain below belongs to the X11 renderer's pass. It is live on
    // Wayland only once the compositor reports its own chain (waylandEffectsAvailable); until then
    // every control is greyed with the short reason, same callbacks and labels either way.
    val isWaylandSession by state.isWaylandMode.collectAsState()
    val waylandEffectsOk by state.waylandEffectsAvailable.collectAsState()
    val waylandFxBlocked = isWaylandSession && !waylandEffectsOk
    val vkEnabled = !waylandFxBlocked
    val vkHeaderColor = if (vkEnabled) accent else accent.copy(alpha = 0.4f)


    if (vulkanSupported) {
        // ---- Vulkan: Scaling mode (spatial upscaler) ----
        // Single source of truth for scaling/filtering on the Vulkan renderer (modes
        // 1/2 drive the base sampler filter natively). Keyed on the live value so the
        // picker reflects the seeded/launch config. Drawer-only / session-live.
        val initUpscalerMode by XServerDialogState.upscalerMode.collectAsState()
        var upscalerMode by remember(initUpscalerMode) { mutableIntStateOf(initUpscalerMode) }

        // ---- Composable post effects (layer on top of any scaling mode) ----
        val initCasEnabled   by XServerDialogState.casEnabled.collectAsState()
        val initCasSharpness by XServerDialogState.casSharpness.collectAsState()
        val initHdrVkEnabled by XServerDialogState.hdrVkEnabled.collectAsState()
        var casEnabled   by remember(initCasEnabled)   { mutableStateOf(initCasEnabled) }
        var casSharpness by remember(initCasSharpness) { mutableIntStateOf(initCasSharpness) }
        var hdrVkEnabled by remember(initHdrVkEnabled) { mutableStateOf(initHdrVkEnabled) }

        // ---- Screen Effects (ported to the Vulkan post
        //      chain). Color grade is always-applied via the sliders (neutral = no-op);
        //      FXAA/Toon/CRT/NTSC are toggles. Drawer-only / session-live. ----
        //      Declared up here, ahead of the first control, because the Looks row below
        //      drives the CAS / debanding / scaling controls emitted above its header.
        val initVkBrightness by XServerDialogState.vkBrightness.collectAsState()
        val initVkContrast   by XServerDialogState.vkContrast.collectAsState()
        val initVkGamma      by XServerDialogState.vkGamma.collectAsState()
        val initVkSaturation by XServerDialogState.vkSaturation.collectAsState()
        val initVkFxaa       by XServerDialogState.vkFxaa.collectAsState()
        val initVkToon       by XServerDialogState.vkToon.collectAsState()
        val initVkCrt        by XServerDialogState.vkCrt.collectAsState()
        val initVkNtsc       by XServerDialogState.vkNtsc.collectAsState()
        val initVkDeband     by XServerDialogState.debandEnabled.collectAsState()
        var vkBrightness by remember(initVkBrightness) { mutableFloatStateOf(initVkBrightness) }
        var vkContrast   by remember(initVkContrast)   { mutableFloatStateOf(initVkContrast) }
        var vkGamma      by remember(initVkGamma)      { mutableFloatStateOf(initVkGamma) }
        var vkSaturation by remember(initVkSaturation) { mutableFloatStateOf(initVkSaturation) }
        var vkFxaa       by remember(initVkFxaa)       { mutableStateOf(initVkFxaa) }
        var vkToon       by remember(initVkToon)       { mutableStateOf(initVkToon) }
        var vkCrt        by remember(initVkCrt)        { mutableStateOf(initVkCrt) }
        var vkNtsc       by remember(initVkNtsc)       { mutableStateOf(initVkNtsc) }

        fun applyVkSe() {
            XServerDialogState.onVulkanScreenEffectsApply?.invoke(
                vkBrightness, vkContrast, vkGamma, vkSaturation, vkFxaa, vkToon, vkCrt, vkNtsc)
        }

        // ---- Looks: one-tap presets, shared with the Vulkan post chain ----------------------
        // Writes the same local state and fires the same appliers the controls below use.
        var selectedLook by remember {
            mutableStateOf(
                ScreenEffectLooks.indexOfMatch(
                    initVkBrightness, initVkContrast, initVkGamma, initVkSaturation,
                    if (initCasEnabled) initCasSharpness else 0,
                    initVkFxaa, initVkCrt, initVkToon, initVkNtsc, initVkDeband, initUpscalerMode
                )
            )
        }

        fun applyVkLook(index: Int) {
            val look = ScreenEffectLooks.LOOKS[index]
            selectedLook = index
            // 1. Colour grade + the four shader toggles -> onVulkanScreenEffectsApply.
            vkBrightness = look.brightness
            vkContrast   = look.contrast
            vkGamma      = look.gamma
            vkSaturation = look.saturation.toFloat()
            vkFxaa = look.fxaa; vkCrt = look.crt; vkToon = look.toon; vkNtsc = look.ntsc
            XServerDialogState.setVkBrightness(vkBrightness)
            XServerDialogState.setVkContrast(vkContrast)
            XServerDialogState.setVkGamma(vkGamma)
            XServerDialogState.setVkSaturation(vkSaturation)
            XServerDialogState.setVkFxaa(vkFxaa)
            XServerDialogState.setVkCrt(vkCrt)
            XServerDialogState.setVkToon(vkToon)
            XServerDialogState.setVkNtsc(vkNtsc)
            applyVkSe()
            // 2. Sharpening -> onCasApply. cas == 0 means the CAS pass is OFF; a non-zero level
            //    also moves the slider (0 leaves the user's last level under the hidden slider).
            casEnabled = look.cas > 0
            if (look.cas > 0) casSharpness = look.cas
            XServerDialogState.setCasEnabled(casEnabled)
            XServerDialogState.setCasSharpness(casSharpness)
            XServerDialogState.onCasApply?.invoke(casEnabled, casSharpness)
            // 3. Terminal debanding -> onDebandApply (DebandControls re-seeds off the flow).
            XServerDialogState.setDebandEnabled(look.deband)
            XServerDialogState.onDebandApply?.invoke(look.deband, XServerDialogState.debandStrength.value)
            // 4. Scaling mode ONLY when the Look actually names one.
            look.scalingMode?.let { mode ->
                upscalerMode = mode
                XServerDialogState.setUpscalerMode(mode)
                XServerDialogState.onUpscalerApply?.invoke(mode)
            }
        }

        // A manual move of a control the active Look OWNS drops back to "Custom". Controls no
        // Look touches (HDR, the upscaler's own sharpness) deliberately do not.
        fun lookTouched() { selectedLook = null }
        fun lookTouchedScaling() {
            if (ScreenEffectLooks.LOOKS.getOrNull(selectedLook ?: -1)?.scalingMode != null) selectedLook = null
        }

        ScalingModeHeader("Scaling mode", vkHeaderColor)
        if (waylandFxBlocked) WaylandNotYetNote()
        Spacer(Modifier.height(2.dp))
        UpscalerModeButtons(upscalerMode, vkEnabled) {
            upscalerMode = it
            XServerDialogState.setUpscalerMode(it)
            lookTouchedScaling()
            XServerDialogState.onUpscalerApply?.invoke(it)
        }

        // "Sharpness" controls the REAL upscaler sharpness (RCAS stops / SGSR EdgeSharpness /
        // NIS sharpness) and only applies to the sharpening scaling modes (SGSR/FSR/FSR-Fit/Sharpen/NIS/SGSR HQ).
        if (upscalerMode in 3..8) {
            val initUpscaleSharpness by XServerDialogState.upscaleSharpness.collectAsState()
            var upscaleSharpness by remember(initUpscaleSharpness) { mutableIntStateOf(initUpscaleSharpness) }
            Spacer(Modifier.height(4.dp))
            IntSlider("Sharpness", upscaleSharpness, 0..100, { upscaleSharpness = it }, {
                XServerDialogState.onUpscaleSharpnessApply?.invoke(upscaleSharpness)
            }, enabled = vkEnabled)
        }

        Spacer(Modifier.height(4.dp))

        ToggleRow("CAS", casEnabled, vkEnabled) {
            casEnabled = it
            XServerDialogState.setCasEnabled(it)
            lookTouched()
            XServerDialogState.onCasApply?.invoke(casEnabled, casSharpness)
        }
        if (casEnabled) {
            Spacer(Modifier.height(4.dp))
            IntSlider("CAS Sharpness", casSharpness, 0..100, { casSharpness = it; lookTouched() }, {
                XServerDialogState.setCasSharpness(casSharpness)
                XServerDialogState.onCasApply?.invoke(casEnabled, casSharpness)
            }, enabled = vkEnabled)
        }
        ToggleRow("HDR", hdrVkEnabled, vkEnabled) {
            hdrVkEnabled = it
            XServerDialogState.onHdrApply?.invoke(hdrVkEnabled)
        }

        // Terminal debanding (TPDF dither) — kills 8-bit gradient banding. Drawer-only / session-live.
        DebandControls(vkEnabled, onUserChange = { lookTouched() })

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))

        Text("Screen Effects", color = vkHeaderColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))

        LooksRow(
            selected = selectedLook,
            enabled = vkEnabled,
            onPick = { applyVkLook(it) }
        )

        LabeledSlider("Brightness", vkBrightness, -100f..100f,
            { vkBrightness = it; lookTouched(); applyVkSe() },
            onValueChangeFinished = { XServerDialogState.setVkBrightness(vkBrightness) },
            enabled = vkEnabled)
        LabeledSlider("Contrast", vkContrast, -100f..100f,
            { vkContrast = it; lookTouched(); applyVkSe() },
            onValueChangeFinished = { XServerDialogState.setVkContrast(vkContrast) },
            enabled = vkEnabled)
        LabeledSlider("Gamma", vkGamma, 0.5f..3.0f,
            { vkGamma = it; lookTouched(); applyVkSe() },
            onValueChangeFinished = { XServerDialogState.setVkGamma(vkGamma) },
            enabled = vkEnabled, format = { "%.2f".format(it) })
        // Saturation: 0..200 percent, 100 = neutral (grey at 0, 2x at 200).
        LabeledSlider("Saturation", vkSaturation, 0f..200f,
            { vkSaturation = it; lookTouched(); applyVkSe() },
            onValueChangeFinished = { XServerDialogState.setVkSaturation(vkSaturation) },
            enabled = vkEnabled)

        // Four independent shader flags with identical wiring — one row of chips instead of four
        // switch rows. Same applyVkSe() round-trip as before.
        ToggleChipGrid(
            listOf(
                ToggleChipItem("FXAA", vkFxaa, vkEnabled) { vkFxaa = it; XServerDialogState.setVkFxaa(it); lookTouched(); applyVkSe() },
                ToggleChipItem("Toon", vkToon, vkEnabled) { vkToon = it; XServerDialogState.setVkToon(it); lookTouched(); applyVkSe() },
                ToggleChipItem("CRT", vkCrt, vkEnabled) { vkCrt = it; XServerDialogState.setVkCrt(it); lookTouched(); applyVkSe() },
                ToggleChipItem("NTSC", vkNtsc, vkEnabled) { vkNtsc = it; XServerDialogState.setVkNtsc(it); lookTouched(); applyVkSe() },
            ),
            perRow = 4
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))
    }

    if (!vulkanSupported) {
        Text(
            "No graphics enhancements are available without Vulkan support.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))
    }

    // Native Rendering is only offered on renderers that support direct scanout (Vulkan).
    val nativeRenderingSupported by state.nativeRenderingSupported.collectAsState()
    if (nativeRenderingSupported) {
        // Wayland: X11 direct scanout never runs (the X renderer is idle behind the compositor), so
        // the row is greyed and points at Zero-copy presentation, which is the Wayland equivalent.
        ToggleRow("Native Rendering", nativeRenderingEnabled, enabled = !isWaylandSession) {
            state.onNativeRenderingToggle?.run()
        }
        if (isWaylandSession) {
            HelperText("X11 direct scanout is not used on Wayland; Zero-copy presentation below is the Wayland equivalent.")
        }
    }

    if (isWaylandSession) WaylandHdrOutputRow(state)
    if (isWaylandSession) WaylandZeroCopyRow(state, waylandEffectsOk)
    if (isWaylandSession) WaylandGlSafeModeRow(state)
}

// ───── Wayland: HDR output (live, HDR sessions only) ─────
// Shown only while the compositor has HDR open for this session (the game's or container's "HDR output"
// setting was on at launch AND this screen reports HDR10) - never on X11, never on an SDR screen. A live
// switch: on = the game's HDR frames go to the display as real HDR; off = the SAME frames are shown as a
// tone-mapped SDR picture. Nothing is relaunched and the game is not told (DXVK_HDR and the offer to the
// game were decided at launch, so its own HDR setting is untouched). Per session: the next launch starts
// on again, and whether HDR is offered at all stays the editors' setting.
@Composable
private fun WaylandHdrOutputRow(state: XServerDrawerState) {
    val available by state.waylandHdrAvailable.collectAsState()
    if (!available) return
    val output by state.waylandHdrOutput.collectAsState()
    val onScreen by state.waylandHdrOnScreen.collectAsState()
    val noHeadroom by state.waylandHdrNoHeadroom.collectAsState()
    val toneMapped by state.waylandHdrToneMapped.collectAsState()
    // The gate is decided once at launch and cannot be withdrawn from a running game, so the row stays;
    // this is whether the screen the game is on NOW can actually show HDR10 (the TV can be unplugged
    // mid-session). Without it the status line below goes on claiming HDR on a panel that has none.
    val screenCapable by state.waylandHdrScreenCapable.collectAsState()
    var checked by remember(output) { mutableStateOf(output) }

    Spacer(Modifier.height(6.dp))
    ToggleRow("HDR output", checked) {
        checked = it
        state.setWaylandHdrOutput(it)
        state.onWaylandHdrOutputToggle?.accept(it)
    }
    HelperText("On: HDR games show real HDR on this screen. Off: the same picture tone-mapped to SDR. " +
        "Applies immediately, for this session only; the game's own HDR setting is left alone.")
    HelperText(
        when {
            checked && onScreen    -> "On: HDR frames on screen now."
            checked && noHeadroom  -> "On, but the screen gives HDR no headroom right now: brightness at maximum, " +
                                      "or the screen is being recorded (Android turns HDR headroom off while recording)."
            checked && toneMapped  -> "On, but shown tone-mapped: frame generation on a screen with no HDR swapchain."
            checked && !screenCapable -> "On, but this screen has no HDR10: Android tone-maps the picture for it. " +
                                      "The game keeps the HDR it was offered until it closes."
            checked                -> "On: no HDR frames right now (is HDR on in the game's settings?)."
            onScreen || noHeadroom -> "Off, but these frames cannot be tone-mapped here: they stay HDR."
            toneMapped             -> "Off: HDR frames shown tone-mapped to SDR."
            else                   -> "Off: no HDR frames right now."
        }
    )
}

// ───── Wayland: OpenGL safe mode ─────
// Native OpenGL games render through Mesa here (Zink on Turnip). Mesa runs OpenGL draw submission on
// a helper thread of its own, and a fault on that thread takes the whole game down WITHOUT a crash
// report: it is not a Wine thread, so Wine's crash handler faults again on it and the kernel kills
// the process outright - the game simply vanishes. Safe mode removes the helper thread. The cost is
// a little CPU-side throughput in OpenGL games; DXVK/VKD3D games never load that driver at all.
//
// GALLIUM_THREAD is read once, when the guest's GL driver starts, so this row is a saved preference
// for the next launch and NOT a live switch - the helper text says exactly that rather than
// pretending the flip did something to the running game.
@Composable
private fun WaylandGlSafeModeRow(state: XServerDrawerState) {
    val safeMode by state.waylandGlSafeMode.collectAsState()
    var checked by remember(safeMode) { mutableStateOf(safeMode) }

    Spacer(Modifier.height(6.dp))
    ToggleRow("OpenGL safe mode", checked) {
        checked = it
        state.setWaylandGlSafeMode(it)
        state.onWaylandGlSafeModeToggle?.accept(it)
    }
    HelperText(
        if (checked)
            "On: stops native OpenGL games disappearing with no error. Costs a little OpenGL speed; " +
            "DirectX games are unaffected. Saved for this game - takes effect the next time it starts."
        else
            "Off: OpenGL games keep Mesa's extra draw thread (slightly faster), but a fault on it can " +
            "close the game with no error message. Saved for this game - takes effect the next time it starts."
    )
}

// ───── Wayland: Zero-copy presentation ─────
// A live switch. Flipping it applies to the RUNNING compositor at once (it broadcasts
// banner_ahb_v1.mode, and every game rebuilds its swapchain on — or off — gralloc display buffers,
// one frame's worth of latency, no black frame in between) and is also written to the effective env
// (shortcut override else container) as the next launch's default.
//
// The status line follows the COMPOSITOR, not the toggle: a flip is only real once frames actually
// start — or stop — reaching the display layer, which is what waylandZeroCopyLive reports. The
// 10 s frame counter is far too slow to show that, so it is only used once the mode has settled.
@Composable
private fun WaylandZeroCopyRow(state: XServerDrawerState, effectsAvailable: Boolean) {
    val requested by state.waylandZeroCopyRequested.collectAsState()
    val frames by state.waylandZeroCopyFrames.collectAsState()
    val live by state.waylandZeroCopyLive.collectAsState()
    // Keyed on the seeded value so a reopen shows what is stored, not a stale capture.
    var checked by remember(requested) { mutableStateOf(requested) }

    Spacer(Modifier.height(6.dp))
    ToggleRow("Zero-copy presentation", checked) {
        checked = it
        state.setWaylandZeroCopyRequested(it)
        state.onWaylandZeroCopyToggle?.accept(it)
    }
    HelperText("Fullscreen games are shown on their own display layer with no compositor copy. Applies immediately, and is kept as this game's default.")

    // Polled whichever way the switch is set: the line has to be able to say "switching off" too.
    LaunchedEffect(Unit) {
        while (true) {
            state.onWaylandZeroCopyPoll?.run()
            delay(1000)
        }
    }
    // Screen effects no longer take the layer away: their result is drawn into the game's own
    // display layer, so the frames are not copy-free any more but the game stays hardware-composed.
    val effectsOn = effectsAvailable && waylandCompositorEffectsOn()
    HelperText(
        when {
            checked && effectsOn  -> "On: screen effects are drawn into the game's display layer, so it keeps hardware composition."
            checked && !live      -> "Switching on… the game is rebuilding its swapchain on display buffers."
            checked && frames > 0 -> "On: $frames zero-copy frames in the last 10 s"
            checked               -> "On: frames are going straight to the display layer."
            live                  -> "Switching off… the game's last display-layer frames are still on screen."
            else                  -> "Off: the compositor copies each frame into its own swapchain."
        }
    )
}

/** True when any Vulkan-block effect that runs in the compositor pass is currently on. */
@Composable
private fun waylandCompositorEffectsOn(): Boolean {
    val upscaler by XServerDialogState.upscalerMode.collectAsState()
    val cas by XServerDialogState.casEnabled.collectAsState()
    val hdr by XServerDialogState.hdrVkEnabled.collectAsState()
    val deband by XServerDialogState.debandEnabled.collectAsState()
    val brightness by XServerDialogState.vkBrightness.collectAsState()
    val contrast by XServerDialogState.vkContrast.collectAsState()
    val gamma by XServerDialogState.vkGamma.collectAsState()
    val saturation by XServerDialogState.vkSaturation.collectAsState()
    val fxaa by XServerDialogState.vkFxaa.collectAsState()
    val toon by XServerDialogState.vkToon.collectAsState()
    val crt by XServerDialogState.vkCrt.collectAsState()
    val ntsc by XServerDialogState.vkNtsc.collectAsState()
    return upscaler >= 3 || cas || hdr || deband || fxaa || toon || crt || ntsc ||
        brightness != 0f || contrast != 0f || gamma != 1f || saturation != 100f
}

/** Dim one-line explanation under a control (same style as the Controls tab's Wayland note). */
@Composable
private fun HelperText(text: String) {
    Text(
        text,
        color = LocalAccentDim.current,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
    )
}

/** The one reason line for a control the Wayland compositor does not drive in this build. */
@Composable
private fun WaylandNotYetNote() = HelperText("Not on Wayland in this build yet")

// ───── Runtime-backend diagnostic chip (Graphics tab header) ─────
// Read-only status: arch · translator, plus the FEX unixlib mode. unixlib (native .so loaded) =
// accent/green; DLL (self-contained FEX DLL path) = muted. Box64/x86-64 shows no unixlib segment.
// It is a status readout, never a "faster" flag. Hidden until the activity seeds arch+translator.
@Composable
private fun RuntimeBackendChip(state: XServerDrawerState) {
    val backend by state.runtimeBackend.collectAsState()
    if (!backend.isValid) return

    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(shape)
            .background(DeckPalette.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, DeckPalette.line, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // The eyebrow names the readout; the value stays in plain case so arch names read literally.
        Text(
            "RUNTIME",
            color = DeckPalette.onSurfaceVariant,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )
        Text(
            "${backend.arch} · ${backend.translator}",
            color = DeckPalette.onBackground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (backend.showsFexMode) {
            val (label, color) = when (backend.fexMode) {
                FexMode.UNIXLIB -> "unixlib" to DeckPalette.good // native .so loaded
                FexMode.DLL     -> "DLL"     to DeckPalette.onSurfaceVariant // self-contained DLL path
                FexMode.NA      -> "N/A"     to DeckPalette.onSurfaceVariant // maps not resolved yet
            }
            Text(
                label,
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

// ───── Frame Generation section (pinned to top of Graphics tab) ─────
// On/off is per-container; multiplier & flow scale are tuned live here and hot-reload
// via conf.toml. Multiplier is a segmented button row (Off / 2× / 3× / 4×); the Flow
// Scale slider collapses while Off and expands when a multiplier is selected.
@Composable
private fun FrameGenSection(state: XServerDrawerState) {
    val accent = MaterialTheme.colorScheme.primary
    val frameGenEnabled by state.frameGenEnabled.collectAsState()
    val initFgMult by state.frameGenMultiplier.collectAsState()
    val initFgFlow by state.frameGenFlowScale.collectAsState()
    val initFgModel by state.frameGenModel.collectAsState()
    val initFgPreset by state.frameGenPerfPreset.collectAsState()
    val engine by state.frameGenEngine.collectAsState()
    val layerActive by state.bionicFgActive.collectAsState()
    val initLsfgPerf by state.lsfgPerformanceMode.collectAsState()
    val winFgNative by state.winFgNative.collectAsState()
    // For the fit advice under the multiplier buttons.
    val nativeFgLocks by state.nativeFgLocks.collectAsState()
    val fpsCap by state.fpsLimit.collectAsState()
    val displayTargetHz by state.displayTargetHz.collectAsState()
    val supportedRates by state.supportedRefreshRates.collectAsState()
    val liveRate by state.currentRefreshRate.collectAsState()
    val matchRefresh by state.matchRefreshRate.collectAsState()
    val vrrOk by state.vrrSupported.collectAsState()
    // Set when LSFG Native / Win-FG Native can't run in this session (driver, DLL, renderer).
    val fgUnavailable by state.fgUnavailableReason.collectAsState()
    val fgUnavailableDetail by state.fgUnavailableDetail.collectAsState()
    // Wayland: frame generation is live only once the compositor reports its own chain
    // (waylandFrameGenAvailable); until then the multiplier row is greyed with the short reason.
    val isWaylandSession by state.isWaylandMode.collectAsState()
    val waylandFgOk by state.waylandFrameGenAvailable.collectAsState()
    val waylandFgBlocked = isWaylandSession && !waylandFgOk

    // Title on the left, engine badge on the right (green dot = engine actually running this
    // session). Replaces the old standalone "Frame Generation (AI)" header so the engine isn't
    // labeled twice. Badge shows bionic-fg / lsfg-vk depending on the container's selection.
    val engineLabel = when (engine) {
        "lsfg"        -> "lsfg-vk"
        "lsfg-native" -> "LSFG Native"
        "bionic"      -> if (winFgNative) "Win-FG Native" else "win-fg"
        else          -> "Off"
    }
    // Green dot = engine actually multiplying frames right now. Frame gen starts at multiplier 0
    // (Off) every launch even when the container has an engine selected, so gate on initFgMult too
    // — otherwise the dot would show green while FG is idle. Tracks live as the user toggles Off/2×/…
    val isRunning = layerActive && engine != "off" && initFgMult > 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Frame Generation", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            )
            Spacer(Modifier.width(5.dp))
            Text(
                engineLabel,
                color = if (isRunning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Native LSFG reports what it is actually achieving, because the requested
    // multiplier is a ceiling to earn rather than a setting that is obeyed: the
    // governor only keeps an extra generated frame when it measurably helps.
    val readout by state.frameGenReadout.collectAsState()
    if (readout.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            readout,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            fontSize = 10.sp
        )
    }
    Spacer(Modifier.height(8.dp))

    if (frameGenEnabled) {
        var fgMult by remember(initFgMult) { mutableIntStateOf(initFgMult) }
        var fgFlow by remember(initFgFlow) { mutableFloatStateOf(initFgFlow) }
        var fgModel by remember(initFgModel) { mutableIntStateOf(initFgModel) }
        var fgPreset by remember(initFgPreset) { mutableIntStateOf(initFgPreset) }
        fun applyFg() {
            state.setFrameGenMultiplier(fgMult)
            state.setFrameGenFlowScale(fgFlow)
            state.setFrameGenModel(fgModel)
            state.setFrameGenPerfPreset(fgPreset)
            state.onBionicFgConfigChange?.run()
        }

        if (waylandFgBlocked) WaylandNotYetNote()
        if (fgUnavailable.isNotEmpty()) {
            Text(
                "⚠ $fgUnavailable",
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            if (fgUnavailableDetail.isNotEmpty()) {
                Text(
                    "Driver check: $fgUnavailableDetail",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
        }
        FgMultiplierButtons(fgMult, engine, enabled = fgUnavailable.isEmpty() && !waylandFgBlocked) { newMult ->
            fgMult = newMult; applyFg()
            // Both engines now do the FULL surface-teardown reset with a Resume prompt, driven from
            // onBionicFgConfigChange in the activity (win-fg on an On/Off/multiplier/model/preset
            // change, lsfg on a level change). The old soft pulseFgReset for win-fg is retired, so
            // nothing extra fires here.
        }

        // Same fit advice as under Max FPS, shown where the multiplier is picked.
        // nativeFgLocks = LSFG Native or Win-FG Native is generating right now.
        // (The sub-controls below only exist for a multiplier the user can pick, so none of them
        // render while the Wayland gate holds.)
        if (nativeFgLocks && !waylandFgBlocked) {
            FgFitAdvice(
                cap = fpsCap, mult = fgMult,
                screen = rememberFgScreen(displayTargetHz, supportedRates, liveRate),
                supported = supportedRates, autoOn = matchRefresh && vrrOk,
                canChangeMult = engine == "lsfg-native"
            ) { fix ->
                state.setFpsLimit(fix)
                state.onFpsLimitChange?.run()
            }
        }

        // Interpolation model, win-fg only. The layer rebuilds its framegen context when the
        // model changes (same path as a multiplier change), so this switches live. Hidden while
        // frame gen is Off, where it would have nothing to act on.
        AnimatedVisibility(
            visible = engine == "bionic" && fgMult > 0 && !waylandFgBlocked,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Model",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
                FgModelButtons(fgModel) { newModel ->
                    fgModel = newModel; applyFg()
                    // Model switch → full presentation reset, fired from onBionicFgConfigChange.
                }
            }
        }

        // The Quality / Balanced / Performance preset row used to live here.
        // Removed at the user's decision (2026-09-09): Win-FG Native runs on
        // Performance permanently. Each preset change rebuilt the whole flow
        // pyramid live, and Performance is the setting the engine is device-
        // proven at. applyWinFgNative pins the value, so nothing a container or
        // shortcut carries from before can put it back.

        // Flow Scale only matters with frame gen actually on -> collapse it while Off.
        AnimatedVisibility(
            visible = fgMult > 0 && !waylandFgBlocked,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                LabeledSlider(
                    "Flow Scale", fgFlow, 0.2f..1.0f,
                    { fgFlow = it }, { applyFg() },
                    format = { "%.2f".format(it) }
                )
                Text(
                    "Higher flow scale = smoother motion estimate, more GPU cost.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }

        // LSFG Native experimental capture resolution (FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED). Live: the
        // activity's lsfg-native branch of onBionicFgConfigChange reads it from the state,
        // persists it and pushes it to the renderer with the multiplier/flow.
        if (engine == "lsfg-native" && com.winhub.hernandez.FeatureFlags.LSFG_NATIVE_EXPERIMENTS_ENABLED && !waylandFgBlocked) {
            val capture by state.fgCaptureResolution.collectAsState()
            val panelHeight by state.fgPanelHeight.collectAsState()
            Spacer(Modifier.height(10.dp))
            Text(
                "Experimental",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            Text(
                "Capture resolution",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            FgCaptureChips(capture, panelHeight) { newValue ->
                state.setFgCaptureResolution(newValue)
                applyFg()
            }
            Text(
                "Height the frame-gen chain runs at (width follows the screen). Lower = far " +
                    "cheaper on the GPU, softer picture. Panel = full resolution.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }

        // lsfg-vk only: performance_mode (bionic-fg has no such setting). Toggling rewrites conf.toml
        // via the same applyFg -> onBionicFgConfigChange path (mtime bump -> layer re-reads live) and
        // persists to the container there.
        if (engine == "lsfg" && !waylandFgBlocked) {
            var lsfgPerf by remember(initLsfgPerf) { mutableStateOf(initLsfgPerf) }
            Spacer(Modifier.height(8.dp))
            ToggleRow("Performance mode", lsfgPerf) {
                lsfgPerf = it
                state.setLsfgPerformanceMode(it)
                applyFg()
            }
            Text(
                "Lower quality for higher FPS — helps on low-end devices.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    } else if (waylandFgBlocked) {
        WaylandNotYetNote()
    } else {
        Text(
            "Enable Frame Generation in this container's settings to tune it here.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

// What the display is really doing, for the frame-gen advice. `asked` = the rate the activity asked
// for (Auto's pick, a manual lock, or the top rate). If the display sits below that for longer than
// a normal mode switch, the device is holding it there (battery saver, a vendor refresh tool, an
// Android 11 panel that only switches seamlessly) and the advice uses the real rate instead.
private data class FgScreen(val hz: Int, val asked: Int, val held: Boolean)

@Composable
private fun rememberFgScreen(target: Int, supported: List<Int>, current: Int): FgScreen {
    val asked = if (target > 0) target else supported.maxOrNull() ?: current
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(asked, current) {
        held = false
        if (asked > 0 && current in 1 until asked - 1) {
            delay(2500)   // a mode switch in flight settles well inside this
            held = true
        }
    }
    return FgScreen(if (held) current else asked, asked, held)
}

@Composable
private fun FgFixButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Frame-gen fit advice, shown under Max FPS and under the multiplier buttons while LSFG Native or
// Win-FG Native is generating. Over the display's rate: the over-limit warning. Under it with Auto
// (match FPS) on: the activity put the display on the closest rate ABOVE cap x multiplier
// (pickNativeFgRefresh), which still leaves repeat gaps, so offer a cap that fits a display rate
// exactly - the lower one first, since a game holds a lower cap more easily.
@Composable
private fun FgFitAdvice(
    cap: Int, mult: Int, screen: FgScreen, supported: List<Int>, autoOn: Boolean,
    canChangeMult: Boolean, onSetCap: (Int) -> Unit
) {
    if (cap <= 0 || mult < 2 || screen.hz <= 0) return
    val made = cap * mult
    if (made > screen.hz) {
        FgOverLimitWarning(cap, mult, screen, canChangeMult, onSetCap)
        return
    }
    if (!autoOn || screen.held || made == screen.hz) return
    // Exact fits this multiplier can reach on this display: (cap, rate), ascending.
    val fits = supported.filter { it % mult == 0 && it / mult >= 10 }.map { (it / mult) to it }
    val lower = fits.lastOrNull { it.first <= cap }
    val higher = fits.firstOrNull { it.first > cap }
    val pick = lower ?: higher ?: return
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Screen set to ${screen.hz} Hz, the closest speed above $cap × $mult = $made. " +
                "A few refreshes repeat a picture. For a perfect fit:",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FgFixButton("Set Max FPS to ${pick.first} → ${pick.second} Hz", accent) { onSetCap(pick.first) }
            if (lower != null && higher != null) {
                Text(
                    "or ${higher.first} → ${higher.second} Hz if the game holds it",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// Shown while LSFG Native or Win-FG Native is generating and the game's real frames (the FPS cap)
// times the multiplier is more than the display can show. Both present one frame per refresh under
// FIFO, so the surplus queues: the compositor falls behind the game and real frames arrive late or
// get dropped - stutter and input lag, not extra smoothness. canChangeMult = false for Win-FG
// Native, which is fixed at 2x.
@Composable
private fun FgOverLimitWarning(
    cap: Int, mult: Int, screen: FgScreen, canChangeMult: Boolean, onSetCap: (Int) -> Unit
) {
    val error = MaterialTheme.colorScheme.error
    val fixCap = maxOf(10, screen.hz / mult)
    val fitMult = if (canChangeMult) (mult - 1 downTo 2).firstOrNull { cap * it <= screen.hz } else null
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "⚠ $cap × $mult = ${cap * mult}: more than your ${screen.hz} Hz screen can show. " +
                "The extra frames pile up, so expect stutter and laggy controls.",
            color = error,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (screen.held) {
            Text(
                "Your device is keeping the screen at ${screen.hz} Hz (WinHub asked for ${screen.asked} Hz). " +
                    "Battery saver or your device's own refresh-rate setting may be holding it.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FgFixButton("Set Max FPS to $fixCap", error) { onSetCap(fixCap) }
            if (fitMult != null) {
                Text(
                    "or pick ${fitMult}×",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

// Off / 2× / 3× / 4× segmented row. mult values 0/2/3/4; selected = blue stroke + translucent fill.
@Composable
private fun FgModelButtons(selected: Int, onSelect: (Int) -> Unit) {
    // win-fg's two optical-flow models: 3 = single-direction flow, 4 = block-grid
    // bidirectional flow with occlusion gating (softer at occlusion edges). Legacy
    // stored values 0-2 map to the standard flow (model 3), matching the layer's clamp.
    val options = listOf(3 to "Optical flow", 4 to "Bidirectional")
    val sel = if (selected < 3) 3 else selected
    SegmentedFrame {
        options.forEach { (model, label) -> SegmentCell(label, sel == model) { onSelect(model) } }
    }
}


/**
 * Experimental capture-resolution picker: Panel, Game, then every distinct height from the
 * container Screen Size list (R.array.screen_size_entries), as scrollable chips. Stored value is
 * "panel" / "game" / the bare height ("720"), the same form the container editor writes.
 * Heights the renderer would clamp anyway (below a quarter of the panel, or at/above it) are left
 * out, so a highlighted chip is always the height really in effect.
 */
@Composable
private fun FgCaptureChips(selected: String, panelHeight: Int, onSelect: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val heights = remember(panelHeight) {
        val minH = maxOf(16, panelHeight / 4)
        context.resources.getStringArray(R.array.screen_size_entries)
            .filterNot { it.equals("custom", ignoreCase = true) }
            .map { Container.fgCaptureHeightFor(it.substringBefore(" ")) }
            .filter { it > 0 && (panelHeight <= 0 || (it >= minH && it < panelHeight)) }
            .distinct()
            .sorted()
    }
    val options = listOf(Container.FG_CAPTURE_PANEL to "Panel", Container.FG_CAPTURE_GAME to "Game") +
        heights.map { it.toString() to "${it}p" }
    val selectedHeight = Container.fgCaptureHeightFor(selected)
    SegmentedFrame(scroll = true) {
        options.forEach { (value, label) ->
            val isSel = when (value) {
                Container.FG_CAPTURE_PANEL, Container.FG_CAPTURE_GAME -> selected == value
                else -> selected != Container.FG_CAPTURE_PANEL && selected != Container.FG_CAPTURE_GAME
                    && selectedHeight == value.toInt()
            }
            SegmentCell(label, isSel, weighted = false) { onSelect(value) }
        }
    }
}

@Composable
private fun FgMultiplierButtons(selected: Int, engine: String, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    // win-fg is a simple Off / On toggle for now (On = 2×); selecting On reveals the
    // model + flow-scale controls (gated on multiplier > 0). lsfg-vk keeps 2×/3×/4×.
    // Disabled (the engine can't run here): all dimmed, nothing clickable.
    val options = if (engine == "bionic")
        listOf(0 to "Off", 2 to "On")
    else
        listOf(0 to "Off", 2 to "2×", 3 to "3×", 4 to "4×")
    SegmentedFrame {
        options.forEach { (mult, label) -> SegmentCell(label, selected == mult && enabled, enabled = enabled) { onSelect(mult) } }
    }
}


// ───── ReShade tab ─────
// First-class drawer tab (peer to Graphics/FPS). Header + the ReShade section body.
@Composable
private fun ReshadeContent(state: XServerDrawerState) {
    SectionHeader("ReShade")
    ReshadeSection()
}

// Tier 1 multi-effect LOADOUT — the effects picked pre-launch, switchable LIVE here. A master
// on/off (whole chain) + a Solo/Stack mode switch + one row per effect: an activation control (radio
// in solo, checkbox in stack) and an expander revealing that effect's typed controls (BOOL -> toggle,
// COMBO/RADIO/LIST -> dropdown, COLOR (floatN) -> HSV picker, FLOAT/INT -> slider) + a per-effect
// Reset. In solo mode, activating one deactivates the others. Shows a placeholder on non-DXVK/VKD3D
// games or with an empty loadout. Effect SELECTION is pre-launch (shortcut/container editor); this
// toggles + tunes the loaded set. Every change rides the single onReshadeApply seam (-> applyReshadeLive:
// conf rewrite the patched libvkbasalt mtime-watch picks up live, and persists to Container/shortcut).
@Composable
private fun ReshadeSection() {
    val accent = MaterialTheme.colorScheme.primary
    val supported by XServerDialogState.reshadeSupported.collectAsState()
    // Seed ONCE from the flows (the launch/last-applied state). Read via .value (not collectAsState)
    // so writing the snapshot back on each apply — for reopen consistency — doesn't re-key the live
    // edit state and collapse the open row. The section is recomposed fresh whenever the tab reopens.
    val seed = remember { XServerDialogState.reshadeLoadout.value }

    if (!supported || seed.isEmpty()) {
        Text(
            "No ReShade effects selected. Add one or more in this game's settings (or the container's) to switch and tune them here.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )
        return
    }

    var master by remember { mutableStateOf(XServerDialogState.reshadeMasterEnabled.value) }
    var mode by remember { mutableStateOf(XServerDialogState.reshadeMode.value) }
    val enabledState = remember { mutableStateMapOf<String, Boolean>().apply { seed.forEach { put(it.name, it.enabled) } } }
    // One SnapshotStateMap of live values per effect (keyed by effect name).
    val valueState = remember { seed.associate { it.name to mutableStateMapOf<String, Float>().apply { putAll(it.values) } } }
    // Bumped on any per-effect Reset so COLOR pickers (internal HSV state) re-seed from `values`.
    var resetNonce by remember { mutableIntStateOf(0) }
    val colorSeed = remember(resetNonce) { Any() }
    // Which effect row is expanded to reveal its params (default: the first).
    var expanded by remember { mutableStateOf(seed.firstOrNull()?.name) }

    fun snapshot(): List<ReshadeLoadoutItem> = seed.map { item ->
        item.copy(
            enabled = enabledState[item.name] ?: item.enabled,
            values = valueState[item.name]?.toMap() ?: item.values
        )
    }
    fun apply() {
        val snap = snapshot()
        XServerDialogState.setReshadeMasterEnabled(master)
        XServerDialogState.setReshadeMode(mode)
        XServerDialogState.setReshadeLoadout(snap)
        XServerDialogState.onReshadeApply?.invoke(master, mode, snap)
    }
    fun setEnabled(name: String, on: Boolean) {
        if (mode == ReshadeLoadout.MODE_SOLO && on) {
            // Solo: activating one deactivates the rest.
            enabledState.keys.toList().forEach { enabledState[it] = (it == name) }
        } else {
            enabledState[name] = on
        }
        apply()
    }
    fun setMode(newMode: String) {
        mode = newMode
        // Switching to solo: keep only the first enabled effect active.
        if (newMode == ReshadeLoadout.MODE_SOLO) {
            var seen = false
            seed.forEach { item ->
                val on = enabledState[item.name] ?: false
                if (on && !seen) seen = true else enabledState[item.name] = false
            }
        }
        apply()
    }

    // "Live preview" — persisted global toggle. ON = changes apply live while the game runs. OFF
    // (default) = freeze-frame + pulse preview (first change SIGSTOPs; each later change briefly
    // resumes 1–2 frames to reveal it, then re-freezes). The activity owns the flag + the freeze/
    // pulse; this just reports it. Independent of `master` so it can be set before enabling ReShade.
    var livePreview by remember { mutableStateOf(XServerDialogState.reshadeLivePreview.value) }

    ToggleRow("ReShade", master, true) { master = it; apply() }

    ToggleRow("Live preview", livePreview, true) {
        livePreview = it
        XServerDialogState.setReshadeLivePreview(it)
        XServerDialogState.onReshadeLivePreviewChange?.invoke(it)
    }
    Text(
        if (livePreview) "Changes apply live; the game keeps running."
        else "Game freezes while tuning; each change pulses briefly to preview, then re-freezes.",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
    )

    if (master) {
        ReshadeModeSelector(mode) { setMode(it) }
        seed.forEach { item ->
            val itemEnabled = enabledState[item.name] ?: item.enabled
            val isOpen = expanded == item.name
            // Activation control + label + expander.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (mode == ReshadeLoadout.MODE_SOLO) {
                    RadioButton(
                        selected = itemEnabled,
                        onClick = { setEnabled(item.name, true) },
                        colors = RadioButtonDefaults.colors(selectedColor = accent)
                    )
                } else {
                    Checkbox(
                        checked = itemEnabled,
                        onCheckedChange = { setEnabled(item.name, it) },
                        colors = CheckboxDefaults.colors(checkedColor = accent)
                    )
                }
                Text(
                    item.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = if (itemEnabled) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f).clickable { expanded = if (isOpen) null else item.name }
                )
                IconButton(onClick = { expanded = if (isOpen) null else item.name }) {
                    Icon(
                        if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isOpen) {
                val values = valueState[item.name] ?: mutableStateMapOf()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        values.clear()
                        item.params.forEach { p -> ReshadeManager.seedValues(p, null, values) }
                        resetNonce++
                        apply()
                    }, enabled = item.params.isNotEmpty()) {
                        Text("Reset", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (item.params.isEmpty()) {
                    Text(
                        "No tunable parameters.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                } else {
                    ReshadeEffectParams(item.params, values, colorSeed) { apply() }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// Solo/Stack mode switch — two pills. Solo = one effect active (A/B); Stack = layered subset.
@Composable
private fun ReshadeModeSelector(mode: String, onChange: (String) -> Unit) {
    SegmentedFrame(Modifier.padding(vertical = 6.dp)) {
        listOf(ReshadeLoadout.MODE_SOLO to "Solo", ReshadeLoadout.MODE_STACK to "Stack").forEach { (value, label) ->
            SegmentCell(label, mode == value) { onChange(value) }
        }
    }
    Text(
        if (mode == ReshadeLoadout.MODE_SOLO) "One effect at a time (A/B compare)."
        else "Layer any subset of effects.",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

// The typed controls for ONE effect (reused by each expanded loadout row). BOOL -> toggle,
// COMBO/RADIO/LIST -> dropdown, COLOR (floatN) -> HSV picker, FLOAT/INT (slider|drag) -> slider.
@Composable
private fun ReshadeEffectParams(
    params: List<ReshadeManager.ReshadeParam>,
    values: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Float>,
    colorSeed: Any,
    onApply: () -> Unit,
) {
    params.forEach { p ->
        when (p.type) {
            ReshadeManager.ParamType.BOOL -> {
                val v = values[p.name] ?: p.defaultValue
                Spacer(Modifier.height(4.dp))
                ToggleRow(p.label, v >= 0.5f, true) {
                    values[p.name] = if (it) 1f else 0f; onApply()
                }
            }
            ReshadeManager.ParamType.COMBO -> {
                val idx = (values[p.name] ?: p.defaultValue).roundToInt()
                ReshadeDropdown(p.label, p.options ?: emptyList(), idx) { sel ->
                    values[p.name] = sel.toFloat(); onApply()
                }
            }
            ReshadeManager.ParamType.COLOR -> {
                ReshadeColorControl(
                    label = p.label,
                    components = p.components,
                    seedKey = colorSeed,
                    component = { c -> values["${p.name}_$c"] ?: p.componentDefaults?.getOrNull(c) ?: 0f },
                    onChange = { comps ->
                        comps.forEachIndexed { c, value -> values["${p.name}_$c"] = value }
                        onApply()
                    }
                )
            }
            else -> {
                val v = (values[p.name] ?: p.defaultValue).coerceIn(p.min, p.max)
                Spacer(Modifier.height(4.dp))
                LabeledSlider(
                    p.label, v, p.min..p.max,
                    { values[p.name] = it },
                    { onApply() },
                    format = {
                        if (p.type == ReshadeManager.ParamType.INT) it.toInt().toString()
                        else "%.2f".format(it)
                    }
                )
            }
        }
    }
}

// COMBO/RADIO/LIST dropdown — shows the ui_items labels; reports the selected index.
@Composable
private fun ReshadeDropdown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, DeckPalette.line, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    options.getOrElse(selected) { options.firstOrNull() ?: "" },
                    color = accent, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.outlinedMenuCard()
            ) {
                options.forEachIndexed { i, opt ->
                    if (i > 0) MenuItemDivider()
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(i); expanded = false })
                }
            }
        }
    }
}

// COLOR (float3/float4) — full HSV color picker: preview swatch + hue/saturation/brightness (and
// alpha for float4) gradient sliders. Emits the RGB(A) components as 0..1 floats. Internal HSV state
// is keyed on [seedKey] so a Reset (or fresh seed) snaps the widget back to the resolved values.
@Composable
private fun ReshadeColorControl(
    label: String,
    components: Int,
    seedKey: Any,
    component: (Int) -> Float,
    onChange: (FloatArray) -> Unit
) {
    val initHsv = remember(seedKey) {
        val r = (component(0).coerceIn(0f, 1f) * 255f).roundToInt()
        val g = (component(1).coerceIn(0f, 1f) * 255f).roundToInt()
        val b = (component(2).coerceIn(0f, 1f) * 255f).roundToInt()
        FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.rgb(r, g, b), it) }
    }
    var hue by remember(seedKey) { mutableFloatStateOf(initHsv[0]) }
    var sat by remember(seedKey) { mutableFloatStateOf(initHsv[1]) }
    var valv by remember(seedKey) { mutableFloatStateOf(initHsv[2]) }
    var alpha by remember(seedKey) { mutableFloatStateOf(if (components >= 4) component(3).coerceIn(0f, 1f) else 1f) }

    fun rgbInt() = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, valv))
    fun emit() {
        val c = rgbInt()
        val out = FloatArray(components)
        if (components >= 1) out[0] = android.graphics.Color.red(c) / 255f
        if (components >= 2) out[1] = android.graphics.Color.green(c) / 255f
        if (components >= 3) out[2] = android.graphics.Color.blue(c) / 255f
        if (components >= 4) out[3] = alpha
        onChange(out)
    }

    // Collapsed by default — a deep shader (e.g. Technicolor) has several color params, and
    // expanding every Hue/Sat/Brightness set at once is a wall of rainbow sliders. The header row
    // (label + swatch + chevron) is tappable to reveal this param's sliders.
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(rgbInt()))
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(6.dp))
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (expanded) {
            GradientSlider(
                "Hue", hue, 0f..360f,
                Brush.horizontalGradient((0..12).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) }),
                { hue = it; emit() }
            )
            GradientSlider(
                "Saturation", sat, 0f..1f,
                Brush.horizontalGradient(listOf(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, valv))),
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, valv)))
                )),
                { sat = it; emit() }
            )
            GradientSlider(
                "Brightness", valv, 0f..1f,
                Brush.horizontalGradient(listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f))))),
                { valv = it; emit() }
            )
            if (components >= 4) {
                GradientSlider(
                    "Alpha", alpha, 0f..1f,
                    Brush.horizontalGradient(listOf(Color.Black, Color(rgbInt()))),
                    { alpha = it; emit() }
                )
            }
        }
    }
}

// Tappable/draggable gradient track slider (drawer dark idiom) used by the color picker.
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    track: Brush,
    onValueChange: (Float) -> Unit
) {
    var width by remember { mutableIntStateOf(0) }
    fun pick(x: Float) {
        if (width > 0) {
            val frac = (x / width).coerceIn(0f, 1f)
            onValueChange(range.start + frac * (range.endInclusive - range.start))
        }
    }
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(track)
                .border(1.dp, ToggleTrackOff, RoundedCornerShape(11.dp))
                .onSizeChanged { width = it.width }
                .pointerInput(range) { detectTapGestures { pick(it.x) } }
                .pointerInput(range) { detectHorizontalDragGestures { ch, _ -> pick(ch.position.x) } }
        ) {
            val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().padding(horizontal = 3.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth(frac)) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, PureBlack, CircleShape)
                    )
                }
            }
        }
    }
}

// Scaling-mode picker: 9 options (0=None 1=Linear 2=Nearest 3=SGSR 8=SGSR HQ 4=FSR
// 5=FSR Fit 6=Sharpen 7=NIS) laid out as rows of three segmented chips (same box-chip
// idiom as FgMultiplierButtons). SGSR HQ sits next to SGSR; its int is 8 because the
// mode ints are persisted per game and must never be renumbered.
// Terminal debanding controls (toggle + optional dither-strength slider), shared by the
// GL and Vulkan graphics blocks. Reads/writes the single _debandEnabled/_debandStrength
// state and fires onDebandApply; only one renderer block is shown per session, so the
// shared state never conflicts. strength 0..200 (CPU maps /100 to LSBs, default 100 = 1 LSB).
// onUserChange fires only for a HUMAN toggle/drag here, never for a value pushed in from
// outside — the Screen Effect Looks row uses it to fall back to "Custom". Both controls also
// write their value back into XServerDialogState, so the shared state stays the single source
// of truth and a Look applied elsewhere (which can only reach this composable through those
// flows) re-seeds these locals.
@Composable
private fun DebandControls(enabled: Boolean = true, onUserChange: () -> Unit = {}) {
    val initDebandEnabled  by XServerDialogState.debandEnabled.collectAsState()
    val initDebandStrength by XServerDialogState.debandStrength.collectAsState()
    var debandEnabled  by remember(initDebandEnabled)  { mutableStateOf(initDebandEnabled) }
    var debandStrength by remember(initDebandStrength) { mutableIntStateOf(initDebandStrength) }
    ToggleRow("Debanding", debandEnabled, enabled) {
        debandEnabled = it
        XServerDialogState.setDebandEnabled(it)
        onUserChange()
        XServerDialogState.onDebandApply?.invoke(debandEnabled, debandStrength)
    }
    if (debandEnabled) {
        Spacer(Modifier.height(4.dp))
        IntSlider("Dither strength", debandStrength, 0..200,
            onValueChange = { debandStrength = it },
            onValueChangeFinished = {
                XServerDialogState.setDebandStrength(debandStrength)
                XServerDialogState.onDebandApply?.invoke(debandEnabled, debandStrength)
            },
            enabled = enabled)
    }
}

// ───── Screen Effect Looks row ─────
// The one-tap preset chips that head the Screen Effects section on both renderer paths.
// Deliberately built out of the drawer's existing ToggleChipGrid so it reads as part of the
// section rather than a new widget language: translucent blue fill + blue stroke when selected,
// graphite tile + hairline border otherwise, equal widths, three per row so the longest label ("Adaptive Sharpen")
// wraps to two lines instead of overflowing the drawer's fixed 380dp shell.
// `selected` == null means the user has moved one of the controls a Look owns — a trailing
// "Custom" chip is appended and lights up. Tapping it does nothing (Custom is a state you
// arrive at, not one you pick); tapping any Look re-applies it and drops the chip.
@Composable
private fun LooksRow(selected: Int?, enabled: Boolean, onPick: (Int) -> Unit) {
    val looks = ScreenEffectLooks.LOOKS
    val chips = looks.mapIndexed { i, look ->
        ToggleChipItem(look.name, selected == i, enabled) { onPick(i) }
    } + if (selected == null) listOf(ToggleChipItem(ScreenEffectLooks.CUSTOM_LABEL, true, enabled) { }) else emptyList<ToggleChipItem>()

    Text(
        "Looks",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    ToggleChipGrid(chips, perRow = 3)
    val note = if (selected != null) {
        val look = looks[selected]
        if (look.desc.isEmpty()) look.name else "${look.name} — ${look.desc}"
    } else {
        "Adjusted — everything below is yours. Tap a look to go back."
    }
    Text(
        note,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.9f else 0.4f),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
    )
}

// Fullscreen aspect-ratio selector (#71 Stage 2): 5 mode chips laid out as rows (3 + 2), same
// box-chip idiom as UpscalerModeButtons. Selecting a mode applies it live and does NOT close the
// drawer, so the user can flip between modes and settle on one before dismissing.
@Composable
private fun FullscreenModeButtons(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        0 to stringResource(R.string.fullscreen_mode_off_short),
        1 to stringResource(R.string.fullscreen_mode_fit_short),
        2 to stringResource(R.string.fullscreen_mode_stretch_short),
        3 to stringResource(R.string.fullscreen_mode_fill_short),
        4 to stringResource(R.string.fullscreen_mode_integer_short)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            SegmentedFrame {
                row.forEach { (mode, label) -> SegmentCell(label, selected == mode) { onSelect(mode) } }
            }
        }
    }
}

// Screen alignment (#413): vertical placement of the letterbox bar (Center/Top/Bottom), same
// segmented-chip idiom as FullscreenModeButtons. [enabled]=false greys it out for STRETCH/FILL,
// where there is no bar to move. 0/1/2 map to Container.ALIGN_CENTER/TOP/BOTTOM.
@Composable
private fun ScreenAlignmentButtons(selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val options = listOf(
        0 to stringResource(R.string.screen_alignment_center_short),
        1 to stringResource(R.string.screen_alignment_top_short),
        2 to stringResource(R.string.screen_alignment_bottom_short)
    )
    SegmentedFrame {
        options.forEach { (align, label) -> SegmentCell(label, selected == align, enabled = enabled) { onSelect(align) } }
    }
}

// Scaling-mode section header with a small "?" that explains every mode (help_scaling_mode).
// The help stays available while the picker itself is greyed out.
@Composable
private fun ScalingModeHeader(title: String, color: Color, modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(title, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        IconButton(onClick = { showHelp = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Help, contentDescription = "What is this?", tint = color, modifier = Modifier.size(16.dp))
        }
    }
    if (showHelp) HelpDialog(R.string.help_scaling_mode) { showHelp = false }
}

@Composable
private fun UpscalerModeButtons(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val options = listOf(
        0 to "None", 1 to "Linear", 2 to "Nearest",
        3 to "SGSR", 8 to "SGSR HQ", 4 to "FSR",
        5 to "FSR (Fit)", 6 to "Sharpen", 7 to "NIS"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            SegmentedFrame {
                row.forEach { (mode, label) -> SegmentCell(label, selected == mode, enabled = enabled) { onSelect(mode) } }
            }
        }
    }
}

// Per-container rumble target picker (Off/Controller/Device/Both) — same segmented-chip style as
// UpscalerModeButtons above, just a fixed 4-wide row instead of chunked(3). "Device" = the phone's
// own vibrator (Container.VIBRATION_MODE_DEVICE); "Both" drives the physical controller AND the
// phone together (Container.VIBRATION_MODE_BOTH).
@Composable
private fun VibrationModeButtons(selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val options = listOf(0 to "Off", 1 to "Controller", 2 to "Device", 3 to "Both")
    SegmentedFrame {
        options.forEach { (mode, label) -> SegmentCell(label, selected == mode, enabled = enabled, fontSize = 12.sp) { onSelect(mode) } }
    }
}

// Multi-select cousin of FullscreenModeButtons: each item toggles independently, but shares the exact
// box style (translucent blue fill + blue stroke ON; graphite tile + hairline border OFF)
// and the aligned equal-width grid (weight(1f), short rows padded with Spacer so widths stay equal).
// Callers build the list of currently-VISIBLE chips FIRST, then this chunks per row — so per-style
// gating never leaves holes or misaligns the grid.
// enabled=false greys the WHOLE grid and swallows taps, the same way VibrationModeButtons does it —
// for rows that stay on screen because they still explain something, but can't be acted on yet.
// disabledIndices greys INDIVIDUAL chips (indices into the flat items list) for the case where one
// option is unreachable in the current configuration but the rest of the row is still live — greyed
// rather than dropped, so the row doesn't reflow and the option is visibly still a thing that exists.
@Composable
private fun ModeChipGrid(items: List<Triple<String, Boolean, () -> Unit>>, perRow: Int,
                         enabled: Boolean = true, disabledIndices: Set<Int> = emptySet()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.withIndex().chunked(perRow).forEach { row ->
            SegmentedFrame {
                row.forEach { (index, item) ->
                    val (label, isOn, onTap) = item
                    val chipEnabled = enabled && index !in disabledIndices
                    SegmentCell(label, isOn && chipEnabled, enabled = chipEnabled) { onTap() }
                }
            }
        }
    }
}

// One on/off chip in a ToggleChipGrid. Same tuple ToggleRow takes (label + checked + enabled +
// callback), just laid out as a chip instead of a full-width switch row.
private data class ToggleChipItem(
    val label: String,
    val checked: Boolean,
    val enabled: Boolean = true,
    val onToggle: (Boolean) -> Unit
)

// Compact stand-in for a run of ToggleRows: same chip language as ModeChipGrid above (blue stroke +
// blue text ON; graphite tile + hairline border OFF, equal widths, short
// rows padded with Spacer), but every chip toggles independently. Packing adjacent toggles 2–4 per
// row is where the vertical space comes back — a Switch row costs ~4x the height of a chip.
// Disabled chips keep ToggleRow's alpha-0.4 grey-out and swallow taps.
@Composable
private fun ToggleChipGrid(items: List<ToggleChipItem>, perRow: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(perRow).forEach { row ->
            // IntrinsicSize.Min + fillMaxHeight keeps a row level when one label wraps to two lines.
            SegmentedFrame(Modifier.height(IntrinsicSize.Min)) {
                row.forEach { item ->
                    SegmentCell(
                        item.label, item.checked && item.enabled,
                        modifier = Modifier.fillMaxHeight(), enabled = item.enabled,
                    ) { item.onToggle(!item.checked) }
                }
            }
        }
    }
}

// Manual refresh-rate slider — snaps to [Off] + each supported panel rate (which may be unevenly
// spaced, e.g. 60/90/120/144). Off (0) = no manual lock. The label tracks the snapped value live
// while dragging; the actual panel rate is applied on release so we don't flash through modes mid-drag.
// Greyed when disabled (Auto on or display not VRR-capable).
@Composable
private fun RefreshRateSlider(rates: List<Int>, selected: Int, enabled: Boolean, autoRate: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val stops = remember(rates) { listOf(0) + rates }
    var idx by remember(selected, stops) { mutableStateOf(stops.indexOf(selected).coerceAtLeast(0)) }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    // When the slider is disabled by Auto, the manual selection is meaningless — show the live actual
    // display rate instead, kept in normal blue so it reads as a real value, not a greyed leftover.
    val showAuto = !enabled && autoRate > 0
    val rightText = when {
        showAuto -> "$autoRate Hz"
        stops[idx] == 0 -> "Off"
        else -> "${stops[idx]} Hz"
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rate", style = MaterialTheme.typography.bodySmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else dim)
            Text(
                rightText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled || showAuto) accent else dim,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = idx.toFloat(),
            onValueChange = { idx = it.roundToInt().coerceIn(stops.indices) },
            onValueChangeFinished = { onSelect(stops[idx]) },
            valueRange = 0f..(stops.size - 1).toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        // Tick labels under each notch so the snap values are visible, not just anonymous notches.
        // Padded by ~the thumb radius so the end labels line up with the end notches.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stops.forEach { s ->
                Text(
                    if (s == 0) "Off" else "$s",
                    fontSize = 10.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else dim
                )
            }
        }
    }
}

@Composable
private fun IntSlider(label: String, value: Int, valueRange: IntRange, onValueChange: (Int) -> Unit, onValueChangeFinished: (() -> Unit)? = null, steps: Int = -1, enabled: Boolean = true) {
    val accent = MaterialTheme.colorScheme.primary
    // steps < 0 -> continuous (one stop per integer); steps >= 0 -> snap to that many
    // interior stops (e.g. steps = 3 over 0..100 yields the 5 positions {0,25,50,75,100}).
    val sliderSteps = if (steps >= 0) steps else (valueRange.last - valueRange.first - 1)
    Column(modifier = Modifier.padding(vertical = 4.dp).then(if (enabled) Modifier else Modifier.alpha(0.4f))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "$value", style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            onValueChangeFinished = { onValueChangeFinished?.invoke() },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = sliderSteps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// ───── HUD Tab ─────

@Composable
private fun HudContent(state: XServerDrawerState) {
    val accent = MaterialTheme.colorScheme.primary
    val fpsConfig by state.fpsConfig.collectAsState()

    // Re-read the live display refresh rate when this tab opens so the "Rate" readout is fresh on
    // open; the display listener keeps it current while the drawer stays open.
    LaunchedEffect(Unit) { state.onRefreshRatePoll?.run() }

    SectionHeader("HUD")

    // ── FPS Limiter state (caps the game's own frames; with LSFG Native / Win-FG Native the screen
    //    gets cap x multiplier, and it steps aside while lsfg-vk multiplies). Declared here; its UI
    //    lives in the Performance accordion section below. ──
    val fpsLimiterEnabled by state.fpsLimiterEnabled.collectAsState()
    val initFpsLimit by state.fpsLimit.collectAsState()
    var limiterOn by remember(fpsLimiterEnabled) { mutableStateOf(fpsLimiterEnabled) }
    var limitVal by remember(initFpsLimit) { mutableIntStateOf(initFpsLimit) }
    fun applyLimiter() {
        state.setFpsLimiterEnabled(limiterOn)
        state.setFpsLimit(limitVal)
        // Standalone limiter: applies live to the host renderer regardless of frame-gen engine.
        state.onFpsLimitChange?.run()
    }

    // ── Refresh rate state: Auto (match FPS / VRR) + manual snap to a supported panel rate.
    //    Declared here; its UI lives in the Performance accordion section below. ──
    val matchRefreshRate by state.matchRefreshRate.collectAsState()
    val vrrSupported by state.vrrSupported.collectAsState()
    val manualRefreshRate by state.manualRefreshRate.collectAsState()
    val supportedRefreshRates by state.supportedRefreshRates.collectAsState()
    val currentRefreshRate by state.currentRefreshRate.collectAsState()
    var matchRefreshOn by remember(matchRefreshRate) { mutableStateOf(matchRefreshRate) }

    fun parseConfig(s: String): Map<String, String> {
        if (s.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        s.split(",").forEach { part ->
            val eq = part.indexOf('=')
            if (eq >= 0) map[part.substring(0, eq)] = part.substring(eq + 1)
        }
        return map
    }

    val cfg = remember(fpsConfig) { parseConfig(fpsConfig) }
    // Read with fallback across classic + gamehub key names (mirrors the container dialog).
    fun b(k: String, fb: String, d: String) = (cfg[k] ?: cfg[fb] ?: d) == "1"

    // Every HUD control is keyed on `cfg` so the drawer always mirrors the
    // setup currently in use: when a container launches, the overlay honors the
    // saved config and these re-initialize from that same live config (just like
    // the FPS-limiter rows above). Un-keyed remembers would capture stale values
    // once and drift from what's actually on screen.
    // Orientation is flipped by tapping the HUD in-game; preserve it on write-back.
    val hudMode = remember(cfg) { cfg.getOrDefault("hudMode", "vertical") }

    // Master HUD on/off. When off, the activity keeps every overlay style GONE even while a game
    // window is bound (see XServerDisplayActivity.hudCounterEnabled). The HUD group below hides.
    var hudEnabled by remember(cfg) { mutableStateOf(b("hudEnabled", "hudEnabled", "1")) }

    // Fusion is the only HUD; a config saved with another hudStyle is rewritten as fusion on the next apply.
    val hudStyle = "fusion"
    // Detail level 1..4 (also stepped by tapping the HUD in-game). Older size names map across.
    val fusionLevels = com.winhub.hernandez.widget.fusionhud.FusionSize.values().toList()
    var fusionSize by remember(cfg) {
        mutableStateOf(com.winhub.hernandez.widget.fusionhud.FusionSize.from(cfg.getOrDefault("hudSize", "3")).token)
    }
    // Chips the selected level actually renders (single source of truth in FusionSize).
    val fusionChips = com.winhub.hernandez.widget.fusionhud.FusionSize.from(fusionSize).supportedChips()
    var showFPS by remember(cfg) { mutableStateOf(b("showFPS", "showFPS", "1")) }
    var showGraph by remember(cfg) { mutableStateOf(b("showFPSGraph", "showFPSGraph", "0")) }
    var showCPU by remember(cfg) { mutableStateOf(b("showCPUUsage", "showCPULoad", "1")) }
    var showGPU by remember(cfg) { mutableStateOf(b("showGPULoad", "showGPULoad", "1")) }
    var showRAM by remember(cfg) { mutableStateOf(b("showRAM", "showRAM", "1")) }
    var showPower by remember(cfg) { mutableStateOf(b("showPower", "showPower", "1")) }
    var showTemp by remember(cfg) { mutableStateOf(b("showTemp", "showBatteryTemp", "1")) }
    var showEngine by remember(cfg) { mutableStateOf(b("showEngine", "showRenderer", "1")) }
    var showGpuModel by remember(cfg) { mutableStateOf(b("showGpuModel", "showGpuModel", "1")) }
    // Extra metrics (absent = off is the intended default).
    var showGpuTemp by remember(cfg) { mutableStateOf(b("showGpuTemp", "showGpuTemp", "0")) }
    var showBattery by remember(cfg) { mutableStateOf(b("showBattery", "showBattery", "0")) }
    var showClock by remember(cfg) { mutableStateOf(b("showClock", "showClock", "1")) }
    // Extra metrics + global lock (defaults match Container.DEFAULT_FPS_COUNTER_CONFIG).
    var showVram by remember(cfg) { mutableStateOf(b("showVram", "showVram", "1")) }
    var showLow001 by remember(cfg) { mutableStateOf(b("showLow001", "showLow001", "1")) }
    var fpsDecimal by remember(cfg) { mutableStateOf(b("fpsDecimal", "fpsDecimal", "1")) }
    var hudLocked by remember(cfg) { mutableStateOf(b("hudLocked", "hudLocked", "0")) }
    // Level 4 metrics.
    var showPerCore by remember(cfg) { mutableStateOf(b("showPerCore", "showPerCore", "1")) }
    var showSwap by remember(cfg) { mutableStateOf(b("showSwap", "showSwap", "1")) }
    var showNet by remember(cfg) { mutableStateOf(b("showNet", "showNet", "1")) }
    var showResolution by remember(cfg) { mutableStateOf(b("showResolution", "showResolution", "1")) }
    var showProton by remember(cfg) { mutableStateOf(b("showProton", "showProton", "1")) }
    var showWrapper by remember(cfg) { mutableStateOf(b("showWrapper", "showWrapper", "1")) }
    var showDxVer by remember(cfg) { mutableStateOf(b("showDxVer", "showDxVer", "1")) }
    var showSession by remember(cfg) { mutableStateOf(b("showSession", "showSession", "1")) }
    // Temperature display: unit, plus danger bands as a single 3-way (Off / Auto / Manual) rather
    // than two toggles — "banding on but auto off" and "banding off but auto on" aren't distinct
    // states worth exposing. Auto reads the device's own thermal trip points.
    var tempUnitF by remember(cfg) { mutableStateOf(cfg.getOrDefault("tempUnit", "c").equals("f", true)) }
    var tempBands by remember(cfg) { mutableStateOf(cfg.getOrDefault("tempBands", "1") != "0") }
    var tempAuto by remember(cfg) { mutableStateOf(cfg.getOrDefault("tempAuto", "1") != "0") }
    var tempRedCpu by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("tempRedCpu", "90").toFloatOrNull() ?: 90f) }
    var tempRedGpu by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("tempRedGpu", "90").toFloatOrNull() ?: 90f) }
    var tempRedBat by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("tempRedBat", "48").toFloatOrNull() ?: 48f) }

    var scaleValue by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("hudScale", Container.DEFAULT_HUD_SCALE.toString()).toFloatOrNull() ?: Container.DEFAULT_HUD_SCALE.toFloat()) }
    var opacityValue by remember(cfg) { mutableFloatStateOf(cfg.getOrDefault("hudOpacity", "62").toFloatOrNull() ?: 62f) }

    val colors = listOf("soft", "mid", "vivid")
    var color by remember(cfg) { mutableStateOf(cfg.getOrDefault("hudColor", "mid")) }
    // hudOutline is a 0..100 intensity (legacy off/soft/strong strings map via parseHudOutline).
    var outlineValue by remember(cfg) { mutableFloatStateOf(parseHudOutline(cfg.getOrDefault("hudOutline", "40")).toFloat()) }
    var outlineAccent by remember(cfg) { mutableStateOf(cfg.getOrDefault("hudOutlineAccent", "0") == "1") }

    fun i(v: Boolean) = if (v) "1" else "0"
    // Identical key set to ContainerDetailScreen.FpsCounterConfigDialog.buildConfig(),
    // so the in-game drawer and the pre-launch dialog stay fully interchangeable.
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
        "tempRedCpu=${tempRedCpu.toInt()}",
        "tempRedGpu=${tempRedGpu.toInt()}",
        "tempRedBat=${tempRedBat.toInt()}",
        "hudColor=$color",
        "hudOutline=${outlineValue.toInt()}",
        "hudOutlineAccent=${if (outlineAccent) 1 else 0}",
        "hudScale=${scaleValue.toInt()}",
        "hudOpacity=${opacityValue.toInt()}",
    ).joinToString(",")

    fun apply() { state.onFpsConfigApply?.invoke(buildConfig()) }

    // ═══ Master toggle: hides every HUD group below when off (matches the approved prototype). ═══
    ToggleRow("Show HUD", hudEnabled) { hudEnabled = it; apply() }

    // ── Performance group: always shown. The limiter + refresh live here regardless of the HUD. ──
    HudGroupLabel("Performance")
    CollapsibleSection("Frame rate & refresh", lead = "always on", initiallyExpanded = true) {
        Text("FPS Limiter", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        val nativeFgLocks by state.nativeFgLocks.collectAsState()
        val fgEngine by state.frameGenEngine.collectAsState()
        val fgEnabled by state.frameGenEnabled.collectAsState()
        val fgMult by state.frameGenMultiplier.collectAsState()
        val displayTargetHz by state.displayTargetHz.collectAsState()
        // nativeFgLocks covers both compositor engines; name the one actually running.
        val nativeFgName = if (fgEngine == "lsfg-native") "LSFG Native" else "Win-FG Native"
        // Locked ON while native frame gen generates - see XServerDrawerState.nativeFgLocks.
        ToggleRow("Limit FPS", limiterOn, enabled = !nativeFgLocks) { limiterOn = it; applyLimiter() }
        if (nativeFgLocks) {
            Text(
                "Locked on while $nativeFgName is generating",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 10.sp
            )
        }
        if (limiterOn) {
            LabeledSlider(
                "Max FPS", limitVal.toFloat(), 10f..200f,
                { limitVal = it.roundToInt() }, { applyLimiter() },
                format = { "${it.roundToInt()}" }
            )
            // Quick presets: set the cap in one tap. Shares limitVal with the slider above, so the
            // slider thumb snaps to the picked value (and the matching chip highlights on any value).
            Spacer(Modifier.height(6.dp))
            ModeChipGrid(
                listOf(30, 60, 90, 120).map { preset ->
                    Triple("$preset", limitVal == preset) { limitVal = preset; applyLimiter() }
                },
                perRow = 4
            )
            // What the cap means depends on where the extra frames are made. LSFG Native and
            // Win-FG Native generate in our compositor ON TOP of the capped game, so the screen
            // gets cap x multiplier. lsfg-vk paces itself while multiplying, so the cap steps
            // aside (XServerDisplayActivity.lsfgGovernsFps).
            val lsfgVkMultiplying = fgEngine == "lsfg" && fgEnabled && fgMult >= 2
            Text(
                when {
                    nativeFgLocks ->
                        "Caps the game's real frames. $nativeFgName adds its own on top, so you'll see up to " +
                            "$limitVal × $fgMult = ${limitVal * fgMult}."
                    lsfgVkMultiplying ->
                        "Not applied while lsfg-vk is multiplying: it paces frames itself."
                    else ->
                        "Caps the game's frame rate. With LSFG Native or Win-FG Native on, you'll see Max FPS × the multiplier."
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
            if (nativeFgLocks) {
                // limitVal tracks the slider while dragging, so the advice updates live.
                FgFitAdvice(
                    cap = limitVal, mult = fgMult,
                    screen = rememberFgScreen(displayTargetHz, supportedRefreshRates, currentRefreshRate),
                    supported = supportedRefreshRates, autoOn = matchRefreshOn && vrrSupported,
                    canChangeMult = fgEngine == "lsfg-native"
                ) { fix -> limitVal = fix; applyLimiter() }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Refresh rate", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        // Auto (match FPS) == the existing VRR toggle. While native frame gen runs it is the
        // session's Auto: switched on at start unless this game opted out, and toggling it off
        // opts this game out (applyNativeFgLocks / onMatchRefreshChange in the activity).
        val nativeFgLocksVrr by state.nativeFgLocks.collectAsState()
        val fgAutoTurnedOn by state.fgAutoTurnedOn.collectAsState()
        val fgAutoPerGame by state.fgAutoPerGame.collectAsState()
        ToggleRow("Auto (match FPS)", matchRefreshOn && vrrSupported, enabled = vrrSupported) {
            matchRefreshOn = it
            state.setMatchRefreshRate(it)
            state.onMatchRefreshChange?.run()
        }
        // Manual rate slider: selectable only when Auto is OFF and the panel can switch rates.
        if (supportedRefreshRates.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            RefreshRateSlider(supportedRefreshRates, manualRefreshRate, vrrSupported && !matchRefreshOn, currentRefreshRate) { rate ->
                state.setManualRefreshRate(rate)
                state.onManualRefreshChange?.run()
            }
        }
        Text(
            when {
                !vrrSupported ->
                    "Unavailable — this display has a single refresh rate, so there's nothing to match."
                matchRefreshOn && nativeFgLocksVrr && fgAutoTurnedOn ->
                    "Auto was turned on for frame generation — the display follows Max FPS × multiplier. " +
                        (if (fgAutoPerGame) "Turn it off if you prefer; this game will remember."
                         else "Turn it off if you prefer (for this session).")
                matchRefreshOn && nativeFgLocksVrr ->
                    "Auto is on — with frame generation running, the display follows Max FPS × multiplier."
                nativeFgLocksVrr ->
                    (if (fgAutoPerGame) "Auto is off for this game while frame generation runs. "
                     else "Auto is off while frame generation runs. ") +
                        "Turn it on to fit the screen to Max FPS × multiplier." +
                        (if (manualRefreshRate > 0) " Display locked to ${manualRefreshRate} Hz." else "")
                matchRefreshOn ->
                    "Auto is on — the display follows your FPS."
                manualRefreshRate > 0 ->
                    "Display locked to ${manualRefreshRate} Hz."
                else ->
                    "Pick a rate to lock the display, or turn Auto on to follow your FPS."
            },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }

    // ── HUD group: hidden entirely when the master toggle is off. ──
    if (hudEnabled) {
        HudGroupLabel("HUD")

        CollapsibleSection("Detail level") {
            HudChipRow("Level", fusionLevels.map { it.label }, fusionLevels.indexOfFirst { it.token == fusionSize }.coerceAtLeast(0)) {
                fusionSize = fusionLevels[it].token; apply()
            }
            Text(
                "Four levels, like the Steam Deck overlay: 1 FPS, 2 adds the frametime graph, 3 adds GPU, CPU and memory, 4 shows everything. Tap the HUD in game to step through them.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
            )
        }

        // Build the chips the SELECTED LEVEL draws (FusionSize.supportedChips(), the same single source of
        // truth the view uses), then chunk into an aligned 3-wide grid so hidden chips never leave holes.
        // Hiding a chip is UI-only; buildConfig() still emits every key, so toggles keep their state.
        fun show(label: String): Boolean = label in fusionChips
        val metricChips = buildList<Triple<String, Boolean, () -> Unit>> {
            if (show("FPS")) add(Triple("FPS", showFPS) { showFPS = !showFPS; apply() })
            if (show("FPS graph")) add(Triple("FPS graph", showGraph) { showGraph = !showGraph; apply() })
            if (show("CPU")) add(Triple("CPU", showCPU) { showCPU = !showCPU; apply() })
            if (show("GPU")) add(Triple("GPU", showGPU) { showGPU = !showGPU; apply() })
            if (show("VRAM")) add(Triple("VRAM", showVram) { showVram = !showVram; apply() })
            if (show("RAM")) add(Triple("RAM", showRAM) { showRAM = !showRAM; apply() })
            if (show("Power")) add(Triple("Power", showPower) { showPower = !showPower; apply() })
            if (show("Temp")) add(Triple("Temp", showTemp) { showTemp = !showTemp; apply() })
            if (show("GPU temp")) add(Triple("GPU temp", showGpuTemp) { showGpuTemp = !showGpuTemp; apply() })
            if (show("Battery")) add(Triple("Battery", showBattery) { showBattery = !showBattery; apply() })
            if (show("0.01% low")) add(Triple("0.01% low", showLow001) { showLow001 = !showLow001; apply() })
            if (show("FPS .1")) add(Triple("FPS .1", fpsDecimal) { fpsDecimal = !fpsDecimal; apply() })
            if (show("Per-core")) add(Triple("Per-core", showPerCore) { showPerCore = !showPerCore; apply() })
            if (show("Swap")) add(Triple("Swap", showSwap) { showSwap = !showSwap; apply() })
            if (show("Network")) add(Triple("Network", showNet) { showNet = !showNet; apply() })
            if (show("Resolution")) add(Triple("Resolution", showResolution) { showResolution = !showResolution; apply() })
            if (show("Proton")) add(Triple("Proton", showProton) { showProton = !showProton; apply() })
            if (show("Wrapper")) add(Triple("Wrapper", showWrapper) { showWrapper = !showWrapper; apply() })
            if (show("DX ver")) add(Triple("DX ver", showDxVer) { showDxVer = !showDxVer; apply() })
            if (show("Session")) add(Triple("Session", showSession) { showSession = !showSession; apply() })
            if (show("Clock")) add(Triple("Clock", showClock) { showClock = !showClock; apply() })
            if (show("Engine")) add(Triple("Engine", showEngine) { showEngine = !showEngine; apply() })
            if (show("GPU model")) add(Triple("GPU model", showGpuModel) { showGpuModel = !showGpuModel; apply() })
            // Global appearance control, shown at every level.
            add(Triple("Lock in place", hudLocked) { hudLocked = !hudLocked; apply() })
        }

        CollapsibleSection("Metrics", lead = "${metricChips.count { it.second }} on") {
            ModeChipGrid(metricChips, perRow = 3)
        }

        CollapsibleSection("Appearance") {
            LabeledSlider("HUD Scale", scaleValue, 50f..150f, { scaleValue = it }, { apply() }, format = { "${it.toInt()}%" })
            LabeledSlider("HUD Opacity", opacityValue, 0f..100f, { opacityValue = it }, { apply() }, format = { "${it.toInt()}%" })
            HudChipRow("HUD color", listOf("Soft", "Mid", "Vivid"), colors.indexOf(color)) { color = colors[it]; apply() }
            LabeledSlider("HUD outline", outlineValue, 0f..100f, { outlineValue = it }, { apply() }, format = { "${it.toInt()}" })
            HudChipRow("Outline color", listOf("Gray", "Accent"), if (outlineAccent) 1 else 0) { outlineAccent = it == 1; apply() }
        }

        // ── Temperature display ── only worth a section when a temperature is actually on screen.
        if (showTemp || showGpuTemp || showBattery) {
            CollapsibleSection("Alerts & Temp") {
                HudChipRow("Temp unit", listOf("°C", "°F"), if (tempUnitF) 1 else 0) {
                    tempUnitF = it == 1; apply()
                }
                val bandMode = if (!tempBands) 0 else if (tempAuto) 1 else 2
                HudChipRow("Danger colors", listOf("Off", "Auto", "Manual"), bandMode) {
                    tempBands = it != 0
                    tempAuto = it != 2
                    apply()
                }
                Text(
                    when (bandMode) {
                        0 -> "Temperatures use their normal color."
                        1 -> "Thresholds read from your device's own thermal trip points, falling back to safe defaults."
                        else -> "Set the red point per sensor; amber sits just below it."
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                )
                if (bandMode == 2) {
                    // Only the red point is exposed; amber is derived. Nobody knows their preferred amber
                    // in the abstract, and three sliders beat six. Values are always °C.
                    LabeledSlider("CPU red at", tempRedCpu, 50f..110f, { tempRedCpu = it }, { apply() }, format = { "${it.toInt()}°C" })
                    if (showGpuTemp)
                        LabeledSlider("GPU red at", tempRedGpu, 50f..110f, { tempRedGpu = it }, { apply() }, format = { "${it.toInt()}°C" })
                    LabeledSlider("Battery red at", tempRedBat, 35f..60f, { tempRedBat = it }, { apply() }, format = { "${it.toInt()}°C" })
                }
            }
        }

        CollapsibleSection("Tools") {
            // General HUD action (every style): export a device sensor report silently to Downloads, so
            // an owner can report which sysfs nodes their SoC actually exposes for any metric showing "—".
            val diagContext = LocalContext.current
            OutlinedButton(onClick = { exportHudDiagnostics(diagContext) }, modifier = Modifier.fillMaxWidth()) {
                Text("Export HUD diagnostics")
            }
            Text(
                "Saves a sensor report (CPU/GPU/temp/VRAM…) straight to your Downloads folder.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// ───── Accordion group label (uppercase, letter-spaced, dim) ─────

@Composable
private fun HudGroupLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 2.dp)
    )
}

// ───── Collapsible accordion section: top divider + clickable header (title + optional pill lead +
//       rotating chevron) + AnimatedVisibility body. Expanded state is remembered per-title. ─────

@Composable
private fun CollapsibleSection(
    title: String,
    lead: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "hudSectionChevron")
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 14.dp, horizontal = 2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (lead != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 2.dp)
                ) {
                    Text(lead, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            Spacer(Modifier.weight(1f))
            // Text chevron rotated 0°→90° on expand (no icon dependency); ">" points right when closed.
            Text(
                "›",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.rotate(chevronRotation)
            )
        }
        AnimatedVisibility(expanded) {
            Column(modifier = Modifier.padding(bottom = 6.dp)) { content() }
        }
    }
}

// ───── Live Present Mode selector (Vulkan host renderer only) ─────
// Reflects the EFFECTIVE mode: while frame generation is multiplying the activity forces Mailbox
// (effectivePresentMode()), so the Mailbox chip lights up on its own. The chips stay fully interactive
// during FG, but FIFO/Immediate taps are BLOCKED (Mailbox is required for FG's extra presents) and flash
// a transient note for ~2s instead of switching — the user's saved preference is untouched, so the
// highlight snaps back when FG turns off.
@Composable
private fun PresentModeSection(state: XServerDrawerState) {
    val rendererIsVulkan by state.rendererIsVulkan.collectAsState()
    if (!rendererIsVulkan) return

    val presentMode by state.presentMode.collectAsState()
    val locked by state.presentModeLocked.collectAsState()

    val modes = listOf("fifo", "mailbox", "immediate")
    val labels = listOf(
        stringResource(R.string.renderer_present_mode_fifo),
        stringResource(R.string.renderer_present_mode_mailbox),
        stringResource(R.string.renderer_present_mode_immediate)
    )
    val selectedIdx = modes.indexOf(presentMode).coerceAtLeast(0)

    // Transient "blocked" note shown when the user taps FIFO/Immediate while FG forces Mailbox. Each
    // rejected tap bumps blockedFlash; the LaunchedEffect shows the note and auto-hides it after ~2s.
    var blockedFlash by remember { mutableStateOf(0) }
    var showBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(blockedFlash) {
        if (blockedFlash > 0) { showBlocked = true; delay(2000); showBlocked = false }
    }

    HudChipRow(
        label = stringResource(R.string.renderer_present_mode),
        options = labels,
        selected = selectedIdx,
        onSelect = { idx ->
            val mode = modes[idx]
            if (locked) {
                // FG is multiplying -> Mailbox is forced. Tapping Mailbox is a harmless no-op; FIFO /
                // Immediate are rejected WITHOUT touching the saved mode (so it reverts on FG off).
                if (mode != "mailbox") blockedFlash++
            } else {
                state.onPresentModeChange?.accept(mode)
            }
        }
    )
    AnimatedVisibility(visible = showBlocked, enter = fadeIn(), exit = fadeOut()) {
        Text(
            stringResource(R.string.present_mode_fg_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

// ───── 3-stop chip selector (skin / color / outline) ─────
@Composable
private fun HudChipRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        SegmentedFrame {
            options.forEachIndexed { idx, opt -> SegmentCell(opt, idx == selected) { onSelect(idx) } }
        }
    }
}

// ───── Controls Tab ─────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsContent(state: XServerDrawerState) {
    val accent = MaterialTheme.colorScheme.primary
    val profiles by XServerDialogState.inputProfiles.collectAsState()
    val initProfileIdx by XServerDialogState.selectedProfileIdx.collectAsState()
    val initTouchscreen by XServerDialogState.showTouchscreen.collectAsState()
    val initTimeout by XServerDialogState.timeoutEnabled.collectAsState()
    val initHaptics by XServerDialogState.hapticsEnabled.collectAsState()

    val moveCursorToTouch by state.moveCursorToTouchpoint.collectAsState()
    val isRelativeMouse by state.isRelativeMouseMovement.collectAsState()
    val isWaylandSession by state.isWaylandMode.collectAsState()
    val isMouseDisabled by state.isMouseDisabled.collectAsState()
    val initOverlayOpacity by state.overlayOpacity.collectAsState()
    val controlsFollowTheme by state.controlsFollowTheme.collectAsState()
    val initControlsAccent by state.controlsAccentColor.collectAsState()

    SectionHeader("Controls")

    // Four unrelated feature areas live under this tab, so they're segmented rather than stacked —
    // exactly one renders at a time. ModeChipGrid is already the drawer's segmented-control language
    // (equal-width accent-filled chips), so the bar reads as native here instead of a new widget.
    val subTab by state.controlsSubTab.collectAsState()
    // "Pad" (live controller settings) exists only where LiveControllerSession runs: a Wine session.
    val padSupported = XServerDialogState.pad.collectAsState().value.supported
    LaunchedEffect(subTab, padSupported) {
        if (subTab == 6 && !padSupported) state.setControlsSubTab(0)
    }
    ModeChipGrid(
        listOfNotNull(
            Triple("Touch", subTab == 0) { state.setControlsSubTab(0) },
            Triple("Mouse", subTab == 1) { state.setControlsSubTab(1) },
            Triple("Vibration", subTab == 2) { state.setControlsSubTab(2) },
            Triple("Gyro", subTab == 3) { state.setControlsSubTab(3) },
            Triple("Players", subTab == 4) { state.setControlsSubTab(4) },
            Triple(stringResource(R.string.swipe_tab), subTab == 5) { state.setControlsSubTab(5) },
            if (padSupported) Triple("Pad", subTab == 6) { state.setControlsSubTab(6) } else null,
            Triple(stringResource(R.string.stick_tuning_title), subTab == 7) { state.setControlsSubTab(7) },
        ),
        perRow = 3
    )
    Spacer(Modifier.height(8.dp))

    // Input Controls section — hoisted above the sub-tab switch so the in-flight profile/flag edits
    // survive a hop to another sub-tab and back.
    var selectedIdx by remember(initProfileIdx) { mutableIntStateOf(initProfileIdx) }
    var showTouchscreen by remember(initTouchscreen) { mutableStateOf(initTouchscreen) }
    var timeoutEnabled by remember(initTimeout) { mutableStateOf(initTimeout) }
    var hapticsEnabled by remember(initHaptics) { mutableStateOf(initHaptics) }
    val allItems = listOf("-- Disabled --") + profiles
    var dropdownExpanded by remember { mutableStateOf(false) }

    when (subTab) {
        // ── Touch ──
        0 -> {
            Text("Input Controls", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))

            ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = it }) {
                OutlinedTextField(
                    value = allItems.getOrElse(selectedIdx) { "-- Disabled --" },
                    onValueChange = {}, readOnly = true,
                    label = { Text("Profile", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true,
                )
                ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                    allItems.forEachIndexed { i, label ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            selectedIdx = i
                            dropdownExpanded = false
                            XServerDialogState.onInputControlsConfirm?.invoke(selectedIdx, showTouchscreen, timeoutEnabled, hapticsEnabled)
                        })
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Three plain on/off flags that all round-trip through the same onInputControlsConfirm call —
            // packed as chips rather than full-width switch rows to keep the sub-tab short.
            ToggleChipGrid(
                listOf(
                    ToggleChipItem("Touch Controls", showTouchscreen) {
                        showTouchscreen = it
                        XServerDialogState.onInputControlsConfirm?.invoke(selectedIdx, showTouchscreen, timeoutEnabled, hapticsEnabled)
                    },
                    ToggleChipItem("Timeout", timeoutEnabled) {
                        timeoutEnabled = it
                        XServerDialogState.onInputControlsConfirm?.invoke(selectedIdx, showTouchscreen, timeoutEnabled, hapticsEnabled)
                    },
                    ToggleChipItem("Haptics", hapticsEnabled) {
                        hapticsEnabled = it
                        XServerDialogState.onInputControlsConfirm?.invoke(selectedIdx, showTouchscreen, timeoutEnabled, hapticsEnabled)
                    },
                ),
                perRow = 3
            )

            // On-screen controls opacity — live, applied to the visible overlay as you drag.
            var overlayOpacity by remember(initOverlayOpacity) { mutableFloatStateOf(initOverlayOpacity) }
            LabeledSlider(
                label = "Overlay Opacity",
                value = overlayOpacity,
                valueRange = 0f..1f,
                onValueChange = {
                    overlayOpacity = it
                    state.setOverlayOpacity(it)
                    state.onOverlayOpacityChange?.run()
                },
                format = { "${(it * 100).toInt()}%" },
            )

            // On-screen controls accent — per-profile override. Follow the app theme (default) or pick a
            // custom accent for the active profile; idle controls stay white, pressed auto-brightens.
            Spacer(Modifier.height(4.dp))
            ToggleChipGrid(
                listOf(
                    ToggleChipItem("App Theme", controlsFollowTheme) {
                        state.setControlsFollowTheme(it)
                        state.onControlsColorChange?.run()
                    }
                ),
                perRow = 3
            )
            if (!controlsFollowTheme) {
                Spacer(Modifier.height(8.dp))
                Text("Controls Accent", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                ColorPicker(
                    initialColor = Color(initControlsAccent),
                    onColorChanged = {
                        state.setControlsAccentColor(it.toArgb())
                        state.onControlsColorChange?.run()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    XServerDialogState.onInputControlsConfirm?.invoke(selectedIdx, showTouchscreen, timeoutEnabled, hapticsEnabled)
                    XServerDialogState.onInputControlsSettings?.invoke(selectedIdx)
                },
                enabled = selectedIdx > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Profile Settings\u2026") }

            Spacer(Modifier.height(4.dp))

            AccentButton("Apply & Close") {
                XServerDialogState.onInputControlsConfirm?.invoke(selectedIdx, showTouchscreen, timeoutEnabled, hapticsEnabled)
                state.onClose?.run()
                Unit
            }
        }

        // ── Mouse ──
        1 -> {
            Text("Mouse & Cursor", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))

            // Each of these flips its flag host-side and stays open — like the fullscreen selector, the
            // drawer keeps rendering so the chip's new on/off state is visible where you tapped it.
            ToggleChipGrid(
                listOf(
                    ToggleChipItem("Cursor to Touch", moveCursorToTouch) {
                        state.onMoveCursorToTouchpoint?.run()
                    },
                    ToggleChipItem("Relative Mouse", isRelativeMouse) {
                        state.onRelativeMouseMovement?.run()
                    },
                    ToggleChipItem("Disable Mouse", isMouseDisabled) {
                        state.onDisableMouse?.run()
                    },
                ),
                perRow = 3
            )

            if (isWaylandSession) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wayland: mouse deltas go to the compositor; a game that locks the pointer receives them as relative motion",
                    color = LocalAccentDim.current,
                    fontSize = 11.sp,
                    lineHeight = 13.sp
                )
            }

            // Tied directly to the toggle: the gestures only exist in absolute-cursor mode, so the
            // pane appears as part of switching Cursor to Touch on and leaves with it. No cog — one
            // less tap, and turning the mode on now shows you exactly what you turned on.
            if (moveCursorToTouch) TouchGestureSettings(state)
        }

        // ── Vibration ──
        2 -> {
            Text("Vibration", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))

            // Master kill-switch — off suppresses ALL controller rumble regardless of slot (and hides the
            // rumble target, intensity, and per-slot rows below, which are moot while it's off). Persists
            // globally.
            val vibrationMasterOn by XServerDialogState.vibrationMasterEnabled.collectAsState()
            ToggleChipGrid(
                listOf(
                    ToggleChipItem("Enabled", vibrationMasterOn) {
                        XServerDialogState.setVibrationMasterEnabled(it)
                        XServerDialogState.onVibrationMasterChanged?.invoke(it)
                    }
                ),
                perRow = 3
            )
            if (vibrationMasterOn) {
                // Per-container rumble target + intensity (PC-accurate dual-motor rumble). Keyed on the
                // incoming config so re-opening the drawer doesn't drift from a stale capture — same pattern
                // as the lsfg "Performance mode" toggle elsewhere in this drawer.
                val initVibrationMode by XServerDialogState.vibrationMode.collectAsState()
                var vibrationMode by remember(initVibrationMode) { mutableIntStateOf(initVibrationMode) }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Rumble Target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                VibrationModeButtons(vibrationMode) {
                    vibrationMode = it
                    XServerDialogState.setVibrationMode(it)
                    XServerDialogState.onVibrationModeChanged?.invoke(it)
                }

                if (vibrationMode != 0) {
                    val initVibrationIntensity by XServerDialogState.vibrationIntensity.collectAsState()
                    var vibrationIntensity by remember(initVibrationIntensity) { mutableIntStateOf(initVibrationIntensity) }
                    IntSlider("Intensity", vibrationIntensity, 0..100,
                        onValueChange = { vibrationIntensity = it },
                        onValueChangeFinished = {
                            XServerDialogState.setVibrationIntensity(vibrationIntensity)
                            XServerDialogState.onVibrationIntensityChanged?.invoke(vibrationIntensity)
                        }
                    )
                }
            }
            val vibrationSlots by XServerDialogState.vibrationSlots.collectAsState()
            if (vibrationMasterOn && vibrationSlots.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ToggleChipGrid(
                    vibrationSlots.mapIndexed { index, slot ->
                        ToggleChipItem(slot.first, slot.second) {
                            XServerDialogState.updateVibrationSlot(index, it)          // reflect in the UI immediately
                            XServerDialogState.onVibrationSlotChanged?.invoke(index, it) // persist to WinHandler
                        }
                    },
                    perRow = 3
                )
            }
        }

        // ── Gyro ── its own branch, deliberately OUTSIDE the vibration block above: the gyro section
        // is unrelated to rumble and must render whether or not vibration is switched on.
        3 -> GyroSection()

        // ── Players ── manual per-device slot assignment (override when auto-assignment guesses wrong).
        4 -> PlayersSection()

        // ── Sticks ── physical stick deadzone / sensitivity, applied live.
        7 -> SticksSection()

        // ── Swipe ── live per-category on-screen-control swipe gates (Buttons/D-pad/Sticks).
        5 -> SwipeSection(state)

        // ── Pad ── live controller settings (identity, XInput source, DirectInput, stick updates).
        6 -> PadSection()
    }
}

// ───── Controls > Swipe — live per-category swipe toggles ─────
// Buttons/D-pad let a finger slide onto (and off) those controls without lifting; Sticks lets a free
// finger slide into a stick to grab it. Each chip reflects its StateFlow and round-trips through its
// callback (apply-live-to-InputControlsView + persist), mirroring the Touch sub-tab's flag chips.
@Composable
private fun SwipeSection(state: XServerDrawerState) {
    val accent = MaterialTheme.colorScheme.primary
    val swipeButtons by state.swipeButtons.collectAsState()
    val swipeDpad by state.swipeDpad.collectAsState()
    val swipeSticks by state.swipeSticks.collectAsState()

    Text(stringResource(R.string.swipe_tab), color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    Text(
        "Slide a finger onto a control to press it — chain buttons and roll the d-pad without lifting.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    ToggleChipGrid(
        listOf(
            ToggleChipItem(stringResource(R.string.swipe_buttons), swipeButtons) {
                state.setSwipeButtons(it)
                state.onSetSwipeButtons?.accept(it)
            },
            ToggleChipItem(stringResource(R.string.swipe_dpad), swipeDpad) {
                state.setSwipeDpad(it)
                state.onSetSwipeDpad?.accept(it)
            },
            ToggleChipItem(stringResource(R.string.swipe_sticks), swipeSticks) {
                state.setSwipeSticks(it)
                state.onSetSwipeSticks?.accept(it)
            },
        ),
        perRow = 3
    )
}

// How a change reaches the game: "Live" (at once), "Reconnects controller" (the pad leaves and comes
// back with the change) or "Restart required" (the next launch).
private enum class PadApply(val label: String) { LIVE("Live"), RECONNECT("Reconnects controller"), RESTART("Restart required") }

@Composable
private fun PadRowHeader(title: String, apply: PadApply, helpRes: Int) {
    var showHelp by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        Text(
            apply.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (apply == PadApply.RESTART) MaterialTheme.colorScheme.error else LocalAccentDim.current,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, if (apply == PadApply.RESTART) MaterialTheme.colorScheme.error else LocalAccentDim.current, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        IconButton(onClick = { showHelp = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Help, contentDescription = "What is this?",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
    if (showHelp) HelpDialog(helpRes) { showHelp = false }
}

@Composable
private fun PadNote(text: String) {
    Text(text, fontSize = 11.sp, lineHeight = 13.sp, color = LocalAccentDim.current)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PadSection() {
    val accent = MaterialTheme.colorScheme.primary
    val pad by XServerDialogState.pad.collectAsState()
    // "Now: N Hz" follows the game while the sub-tab is open.
    LaunchedEffect(Unit) {
        while (true) {
            XServerDialogState.onPadRefresh?.run()
            delay(1000)
        }
    }

    Text("Controller", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))

    // Controller type. The Classic DirectInput pad changes how winebus presents every pad, which it
    // reads once: switching to or from it applies on the next launch.
    val choices = remember { ControllerIdentity.wineChoices() }
    val identityApply = if (pad.nextLaunchOnly.contains("identity")) PadApply.RESTART else PadApply.RECONNECT
    PadRowHeader("Controller type", identityApply, R.string.help_controller_identity)
    var identityExpanded by remember { mutableStateOf(false) }
    val identityLocked = pad.identityLockedReason != null
    ExposedDropdownMenuBox(expanded = identityExpanded && !identityLocked,
        onExpandedChange = { if (!identityLocked) identityExpanded = it }) {
        OutlinedTextField(
            value = ControllerIdentity.label(pad.identity),
            onValueChange = {}, readOnly = true, enabled = !identityLocked,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = identityExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = identityExpanded && !identityLocked, onDismissRequest = { identityExpanded = false }) {
            choices.forEach { id ->
                DropdownMenuItem(text = { Text(id.label) }, onClick = {
                    identityExpanded = false
                    if (id.value != pad.identity) XServerDialogState.onPadIdentity?.invoke(id.value)
                })
            }
        }
    }
    pad.identityLockedReason?.let { PadNote(it) }
    if (pad.nextLaunchOnly.contains("identity")) PadNote("The Classic DirectInput pad switches in or out on the next launch.")

    // XInput source: Direct (HERMES) or Wine's own. Live only when the session was started with
    // in-game switching allowed (the forwarding copy is installed then).
    if (pad.xinputAvailable) {
        Spacer(Modifier.height(8.dp))
        PadRowHeader("XInput source", if (pad.xinputSwitchable) PadApply.RECONNECT else PadApply.RESTART, R.string.help_hermes_xinput)
        ModeChipGrid(
            listOf(
                Triple<String, Boolean, () -> Unit>("Direct", pad.xinputDirect) { if (!pad.xinputDirect) XServerDialogState.onPadXInputDirect?.invoke(true) },
                Triple<String, Boolean, () -> Unit>("Wine", !pad.xinputDirect) { if (pad.xinputDirect) XServerDialogState.onPadXInputDirect?.invoke(false) },
            ),
            perRow = 2
        )
        if (!pad.xinputSwitchable) PadNote("Restart to apply. Turn on \"Allow in-game switching\" to switch without restarting.")
    }

    // DirectInput visibility.
    Spacer(Modifier.height(8.dp))
    PadRowHeader(stringResource(R.string.dinput_visible), PadApply.RECONNECT, R.string.help_dinput_visible)
    ModeChipGrid(
        listOf(
            Triple<String, Boolean, () -> Unit>("Hidden", !pad.dinputVisible) { if (pad.dinputVisible) XServerDialogState.onPadDInputVisible?.invoke(false) },
            Triple<String, Boolean, () -> Unit>("Visible", pad.dinputVisible) { if (!pad.dinputVisible) XServerDialogState.onPadDInputVisible?.invoke(true) },
        ),
        perRow = 2,
        enabled = pad.dinputLockedReason == null
    )
    pad.dinputLockedReason?.let { PadNote(it) }

    // DirectInput vibration: HERMES takes it when the game opens the pad, so a change reconnects it.
    Spacer(Modifier.height(8.dp))
    PadRowHeader(stringResource(R.string.dinput_vibration), PadApply.RECONNECT, R.string.help_dinput_vibration)
    ModeChipGrid(
        listOf(
            Triple<String, Boolean, () -> Unit>("On", pad.dinputVibration) { if (!pad.dinputVibration) XServerDialogState.onPadDInputVibration?.invoke(true) },
            Triple<String, Boolean, () -> Unit>("Off", !pad.dinputVibration) { if (pad.dinputVibration) XServerDialogState.onPadDInputVibration?.invoke(false) },
        ),
        perRow = 2,
        enabled = pad.dinputVibrationLockedReason == null
    )
    pad.dinputVibrationLockedReason?.let { PadNote(it) }

    // Stick updates: HERMES's pacing, live.
    Spacer(Modifier.height(8.dp))
    PadRowHeader("Stick updates", PadApply.LIVE, R.string.help_stick_pacing)
    val pacing = StickPacing.parse(pad.pacing) ?: StickPacing.DEFAULT
    val pacingLocked = pad.pacingLockedReason != null
    ModeChipGrid(
        listOf(
            Triple<String, Boolean, () -> Unit>("Auto", pacing.mode == StickPacing.MODE_AUTO) {
                XServerDialogState.onPadPacing?.invoke(StickPacing(StickPacing.MODE_AUTO, StickPacing.AUTO_DEFAULT).format())
            },
            Triple<String, Boolean, () -> Unit>("Fixed", pacing.mode == StickPacing.MODE_FIXED) {
                XServerDialogState.onPadPacing?.invoke(StickPacing(StickPacing.MODE_FIXED, StickPacing.FIXED_DEFAULT).format())
            },
            Triple<String, Boolean, () -> Unit>("Unpaced", pacing.mode == StickPacing.MODE_OFF) {
                XServerDialogState.onPadPacing?.invoke(StickPacing(StickPacing.MODE_OFF, 0).format())
            },
        ),
        perRow = 3,
        enabled = !pacingLocked
    )
    if (pacing.mode != StickPacing.MODE_OFF) {
        // − / + (core.StickPacing.step) rather than a slider: a scroll of this menu that began on the
        // slider changed the rate.
        val down = pacing.step(-1)
        val up = pacing.step(1)
        Spacer(Modifier.height(6.dp))
        Text(pacing.rateLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        ModeChipGrid(
            listOf(
                Triple<String, Boolean, () -> Unit>("−", false) { XServerDialogState.onPadPacing?.invoke(down.format()) },
                Triple<String, Boolean, () -> Unit>("+", false) { XServerDialogState.onPadPacing?.invoke(up.format()) },
            ),
            perRow = 2,
            enabled = !pacingLocked,
            disabledIndices = setOfNotNull(0.takeIf { down == pacing }, 1.takeIf { up == pacing })
        )
    }
    PadNote(if (pad.nowHz > 0) "Now: ${pad.nowHz} Hz" else "Now: no game reading the pad yet")
    pad.pacingLockedReason?.let { PadNote(it) }

    // Pending bar: changes wait for the controller to reconnect (automatically shortly after the last
    // change, or when the menu closes), or keep the session as it is and apply on the next launch.
    if (pad.pendingReconnect || pad.reconnecting) {
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, accent, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                if (pad.reconnecting) "Reconnecting the controller…" else "The controller reconnects to apply this.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface
            )
            if (!pad.reconnecting) {
                Spacer(Modifier.height(6.dp))
                ModeChipGrid(
                    listOf(
                        Triple<String, Boolean, () -> Unit>("Reconnect now", true) { XServerDialogState.onPadReconnectNow?.run() },
                        Triple<String, Boolean, () -> Unit>("Next launch", false) { XServerDialogState.onPadNextLaunch?.run() },
                    ),
                    perRow = 2
                )
            }
        }
    }
    pad.nextLaunchOnly.filter { it != "identity" && it != "xinput" }.forEach { PadNote(it) }
    if (pad.nextLaunchOnly.contains("xinput") && pad.xinputSwitchable) PadNote("XInput source applies on the next launch.")

    Spacer(Modifier.height(8.dp))
    SaveScopeFooter()
}

// "Save for: This game / All games in container" under Controls › Pad and the rumble rows
// (ControllerSaveTarget in the activity; the choice is kept for the app). A desktop / container launch
// has no game, so its changes go to the container and only the note shows.
@Composable
private fun SaveScopeFooter() {
    val scope by XServerDialogState.controllerSaveScope.collectAsState()
    if (scope.gameName != null) {
        Text("Save for", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        ModeChipGrid(
            listOf(
                Triple<String, Boolean, () -> Unit>("This game", scope.toGame) { XServerDialogState.onControllerSaveScope?.invoke(true) },
                Triple<String, Boolean, () -> Unit>("All games in container", !scope.toGame) { XServerDialogState.onControllerSaveScope?.invoke(false) },
            ),
            perRow = 2
        )
        Spacer(Modifier.height(4.dp))
    }
    PadNote(scope.lastSaved ?: when {
        scope.gameName == null -> "Changes are saved for every game in ${scope.containerName}."
        scope.toGame -> "Changes are saved for ${scope.gameName} only."
        else -> "Changes are saved for every game in ${scope.containerName}."
    })
}

// ───── Controls > Players — manual per-device XInput slot assignment ─────
// One row per detected input device (plus the on-screen pad), each with a Player 1-4 / Ignore / Auto
// selector. Applied live (WinHandler.setDeviceSlotAssignment) and persisted per-container. Fixes the
// case where auto-assignment hands Player 1 to the wrong device (e.g. an aux media-button board that
// sorts first). The list is re-read from WinHandler each time the sub-tab opens, since devices
// hot-plug. Reuses SectionHeader + the drawer's ExposedDropdownMenu pattern for visual consistency.
@Composable
private fun PlayersSection() {
    val accent = MaterialTheme.colorScheme.primary
    val rows by XServerDialogState.playerSlots.collectAsState()

    // Devices hot-plug, so pull a fresh snapshot whenever this sub-tab is shown.
    LaunchedEffect(Unit) { XServerDialogState.onPlayerSlotsRefresh?.run() }

    Text("Player Slots", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    Text(
        "Assign each device to a player, or ignore it. Applied immediately and saved for this container.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    // Full landscape popup: the same slot controls in a left rail, plus a live "Controller Test"
    // picture that lights up as each button/stick/dpad/trigger is pressed (game input is isolated
    // while it's open). Opens the CONTROLLER_TEST dialog hosted by XServerDialogHost.
    Button(
        onClick = { XServerDialogState.show(XServerDialogState.ActiveDialog.CONTROLLER_TEST) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
    ) { Text("Physical Controller Test / Bind") }
    Spacer(Modifier.height(4.dp))
    Text(
        "Test each physical controller, remap its buttons, and confirm every input registers.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    if (rows.isEmpty()) {
        Text(
            "No input devices detected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            PlayerSlotRowItem(row)
        }
    }

    // Manual recovery: rebuild the fake-input transport in place (no relaunch). Belt-and-suspenders
    // for any input-loss — re-handshakes physical pads AND the on-screen controls. Refreshes the list
    // afterwards so the rebuilt slot assignments show.
    Spacer(Modifier.height(4.dp))
    OutlinedButton(
        onClick = {
            XServerDialogState.onResetInput?.run()
            XServerDialogState.onPlayerSlotsRefresh?.run()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
    ) { Text("Reset Input") }
    Text(
        "Re-handshake controllers & on-screen if input stops responding.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// One device row: name + "currently Player N / unassigned" subtitle + a slot selector dropdown.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSlotRowItem(row: XServerDialogState.PlayerSlotRow) {
    val accent = MaterialTheme.colorScheme.primary

    // Selector option order: Auto, Player 1-4, Ignore. Value encodes the override the callback wants:
    // SLOT_AUTO / 0..3 / SLOT_IGNORE — the exact contract of onPlayerSlotChanged.
    val options = remember {
        buildList {
            add("Auto" to XServerDialogState.SLOT_AUTO)
            for (i in 0 until 4) add("Player ${i + 1}" to i)
            add("Ignore" to XServerDialogState.SLOT_IGNORE)
        }
    }
    val selectedLabel = options.firstOrNull { it.second == row.override }?.first ?: "Auto"

    val subtitle = when {
        row.currentSlot >= 0 -> "Currently Player ${row.currentSlot + 1}"
        row.override == XServerDialogState.SLOT_IGNORE -> "Ignored"
        else -> "Unassigned"
    }

    var expanded by remember(row.descriptor, row.override) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            row.displayName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle + if (row.isOnScreen) " · on-screen controls" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {}, readOnly = true,
                label = { Text("Slot", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                // #333 polish: match the shared outlined-menu-card look (outline + thin divider between
                // options), same as the out-of-game Player Slots pickers.
                modifier = Modifier.outlinedMenuCard()
            ) {
                options.forEachIndexed { index, option ->
                    if (index > 0) MenuItemDivider()
                    val (label, value) = option
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        expanded = false
                        if (value != row.override) {
                            XServerDialogState.onPlayerSlotChanged?.invoke(row.descriptor, value)
                        }
                    })
                }
            }
        }
    }
}

// ───── Touch gesture settings — Controls > Mouse, shown while Cursor to Touch is on ─────
// These gestures only exist in absolute-cursor mode, so the pane lives and dies with that toggle
// rather than behind its own control. Each gesture is independently switchable because the right set
// is per-game: an RTS wants both, a mouse-look shooter wants neither stealing its drags. Changes
// apply to the live touchpad immediately and persist, so a game can be tuned without relaunching.
// There is no pinch-to-zoom — two-finger pan already emits the same wheel events.
@Composable
private fun TouchGestureSettings(state: XServerDrawerState) {
    val accent = MaterialTheme.colorScheme.primary
    val dragSelect by state.gestureDragSelect.collectAsState()
    val longPress by state.gestureLongPressRightClick.collectAsState()

    Spacer(Modifier.height(10.dp))
    Text("Touch Gestures", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(2.dp))
    Text(
        "Drag to box-select, hold for right click. Two-finger drag scrolls the wheel.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))

    ToggleChipGrid(
        listOf(
            ToggleChipItem("Box Select", dragSelect) {
                state.setGestureDragSelect(it); state.onGestureConfigChange?.run()
            },
            ToggleChipItem("Hold = Right", longPress) {
                state.setGestureLongPressRightClick(it); state.onGestureConfigChange?.run()
            },
        ),
        perRow = 2
    )

    // The slider only exists while its gesture does — a hold delay with holds switched off is the
    // kind of dead control the sub-tab split was meant to get rid of.
    if (longPress) {
        val initHoldMs by state.gestureLongPressMs.collectAsState()
        var holdMs by remember(initHoldMs) { mutableIntStateOf(initHoldMs) }
        IntSlider("Hold Delay", holdMs, 150..800,
            onValueChange = { holdMs = it },
            onValueChangeFinished = {
                state.setGestureLongPressMs(holdMs); state.onGestureConfigChange?.run()
            }
        )
    }
}

// ───── Stick deadzone / sensitivity — Controls tab, "Sticks" sub-tab ─────
// Sliders only commit on release (like the gyro sensitivity): each commit publishes one immutable
// StickShaper.Tuning, which the activity applies live and writes back to the shortcut / container.
@Composable
private fun SticksSection() {
    val accent = MaterialTheme.colorScheme.primary
    val tuning by XServerDialogState.stickTuning.collectAsState()
    var leftDz by remember(tuning) { mutableFloatStateOf(tuning.leftDeadzone) }
    var leftSens by remember(tuning) { mutableFloatStateOf(tuning.leftSensitivity) }
    var rightDz by remember(tuning) { mutableFloatStateOf(tuning.rightDeadzone) }
    var rightSens by remember(tuning) { mutableFloatStateOf(tuning.rightSensitivity) }

    // Rounded to the slider step so what gets saved is a tidy 1% / 0.05x value.
    fun commit(smoothEdge: Boolean) {
        val t = StickShaper.Tuning(
            Math.round(leftDz * 100f) / 100f, Math.round(leftSens * 100f) / 100f,
            Math.round(rightDz * 100f) / 100f, Math.round(rightSens * 100f) / 100f, smoothEdge)
        XServerDialogState.setStickTuning(t)
        XServerDialogState.onStickTuningChanged?.invoke(t)
    }

    Text(stringResource(R.string.stick_tuning_title), color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    GyroHint(stringResource(R.string.stick_tuning_hint))
    Spacer(Modifier.height(4.dp))

    LabeledSlider(
        label = stringResource(R.string.stick_left_deadzone),
        value = leftDz,
        valueRange = StickShaper.DEADZONE_MIN..StickShaper.DEADZONE_MAX,
        onValueChange = { leftDz = it },
        onValueChangeFinished = { commit(tuning.smoothEdge) },
        steps = 49,
        format = { "${Math.round(it * 100f)}%" }
    )
    LabeledSlider(
        label = stringResource(R.string.stick_left_sensitivity),
        value = leftSens,
        valueRange = StickShaper.SENSITIVITY_MIN..StickShaper.SENSITIVITY_MAX,
        onValueChange = { leftSens = it },
        onValueChangeFinished = { commit(tuning.smoothEdge) },
        steps = 29,
        format = { "%.2fx".format(it) }
    )
    LabeledSlider(
        label = stringResource(R.string.stick_right_deadzone),
        value = rightDz,
        valueRange = StickShaper.DEADZONE_MIN..StickShaper.DEADZONE_MAX,
        onValueChange = { rightDz = it },
        onValueChangeFinished = { commit(tuning.smoothEdge) },
        steps = 49,
        format = { "${Math.round(it * 100f)}%" }
    )
    LabeledSlider(
        label = stringResource(R.string.stick_right_sensitivity),
        value = rightSens,
        valueRange = StickShaper.SENSITIVITY_MIN..StickShaper.SENSITIVITY_MAX,
        onValueChange = { rightSens = it },
        onValueChangeFinished = { commit(tuning.smoothEdge) },
        steps = 29,
        format = { "%.2fx".format(it) }
    )

    ToggleChipGrid(
        listOf(
            ToggleChipItem(stringResource(R.string.stick_smooth_edge), tuning.smoothEdge) { commit(it) }
        ),
        perRow = 3
    )
    GyroHint(stringResource(R.string.stick_smooth_edge_hint))
    Spacer(Modifier.height(4.dp))
    AccentButton(stringResource(R.string.stick_tuning_reset)) {
        leftDz = StickShaper.DEADZONE_DEFAULT
        leftSens = StickShaper.SENSITIVITY_DEFAULT
        rightDz = StickShaper.DEADZONE_DEFAULT
        rightSens = StickShaper.SENSITIVITY_DEFAULT
        commit(StickShaper.SMOOTH_EDGE_DEFAULT)
    }
}

// ───── Gyro (motion aim) section — Controls tab, "Gyro" sub-tab ─────
// Progressive disclosure: the master chip is always visible; everything downstream only appears once
// the gyro is on, so the tab isn't a wall of dead controls. Order is by how often a control is
// touched — Enable, target, sensitivity, activation, then the set-once fine tuning. Hidden entirely
// on devices with no gyroscope.
@Composable
private fun GyroSection() {
    val accent = MaterialTheme.colorScheme.primary
    val gyroSupported by XServerDialogState.gyroSupported.collectAsState()
    if (!gyroSupported) return

    Text(stringResource(R.string.gyro_drawer_title), color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))

    val gyroEnabled by XServerDialogState.gyroEnabled.collectAsState()
    ToggleChipGrid(
        listOf(
            ToggleChipItem(stringResource(R.string.gyro_drawer_enabled), gyroEnabled) {
                XServerDialogState.setGyroEnabled(it)
                XServerDialogState.onGyroEnabledChanged?.invoke(it)
            }
        ),
        perRow = 3
    )
    if (!gyroEnabled) return

    // Where the tilt goes. Right/Left stick overlay the gamepad; Mouse drives the pointer instead,
    // which is the only target that does anything on a Wine desktop or in a mouse-look game.
    val initGyroTarget by XServerDialogState.gyroTarget.collectAsState()
    var gyroTarget by remember(initGyroTarget) { mutableIntStateOf(initGyroTarget) }

    // How the tilt is READ. Rate = the tilt speed drives the stick and it recentres when you stop;
    // Tilt to aim = the stick follows the angle you hold, so a held tilt keeps aiming. The two are
    // mutually exclusive with the Mouse target (a held tilt would be a constant pointer delta and the
    // pointer would run to a screen edge), so each greys the other's chip out with a reason.
    val orientationSupported by XServerDialogState.gyroOrientationSupported.collectAsState()
    val initGyroMode by XServerDialogState.gyroMode.collectAsState()
    var gyroMode by remember(initGyroMode) { mutableIntStateOf(initGyroMode) }
    val orientationBlockedByMouse = gyroTarget == 2
    val orientationSelectable = orientationSupported && !orientationBlockedByMouse
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.gyro_drawer_mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    ModeChipGrid(
        listOf(
            Triple(stringResource(R.string.gyro_drawer_mode_rate), gyroMode == 0) { setGyroModeLive(0) { gyroMode = it } },
            Triple(stringResource(R.string.gyro_drawer_mode_orientation), gyroMode == 1) { setGyroModeLive(1) { gyroMode = it } },
        ),
        perRow = 2,
        disabledIndices = if (orientationSelectable) emptySet() else setOf(1)
    )
    if (!orientationSupported) {
        GyroHint(stringResource(R.string.gyro_drawer_orientation_unsupported))
    }
    else if (orientationBlockedByMouse) {
        GyroHint(stringResource(R.string.gyro_drawer_orientation_mouse_hint))
    }
    else if (gyroMode == 1) {
        GyroHint(stringResource(R.string.gyro_drawer_orientation_hint))
        // Mandatory, not polish: with the "Always" activator there is never a rising edge, so this is
        // the only way to fix a centre that has drifted. Deliberately not bound to a gamepad button —
        // the activator already costs one.
        Spacer(Modifier.height(6.dp))
        AccentButton(stringResource(R.string.gyro_drawer_recenter)) {
            XServerDialogState.onGyroRecenterRequested?.invoke()
        }
        GyroHint(stringResource(R.string.gyro_drawer_recenter_hint))
    }

    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.gyro_drawer_apply_to), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    ModeChipGrid(
        listOf(
            Triple(stringResource(R.string.gyro_drawer_target_right_stick), gyroTarget == 0) { setGyroTargetLive(0) { gyroTarget = it } },
            Triple(stringResource(R.string.gyro_drawer_target_left_stick), gyroTarget == 1) { setGyroTargetLive(1) { gyroTarget = it } },
            Triple(stringResource(R.string.gyro_drawer_target_mouse), gyroTarget == 2) { setGyroTargetLive(2) { gyroTarget = it } },
        ),
        perRow = 3,
        disabledIndices = if (gyroMode == 1) setOf(2) else emptySet()
    )
    if (gyroMode == 1) {
        GyroHint(stringResource(R.string.gyro_drawer_mouse_unavailable_hint))
    }
    else if (gyroTarget == 2) {
        GyroHint(stringResource(R.string.gyro_drawer_mouse_hint))
    }

    // Sensitivity is the one knob people reach for constantly, so it sits right under the target.
    val initGyroSensitivity by XServerDialogState.gyroSensitivity.collectAsState()
    var gyroSensitivity by remember(initGyroSensitivity) { mutableFloatStateOf(initGyroSensitivity) }
    LabeledSlider(
        label = stringResource(R.string.gyro_sensitivity_label),
        value = gyroSensitivity,
        valueRange = 0.1f..10f,
        onValueChange = { gyroSensitivity = it },
        onValueChangeFinished = {
            XServerDialogState.setGyroSensitivity(gyroSensitivity)
            XServerDialogState.onGyroSensitivityChanged?.invoke(gyroSensitivity)
        },
        format = { "%.1f".format(it) }
    )

    // Which button gates the tilt. "Always on" removes the gate entirely — the only option that
    // works with no controller attached (e.g. gyro-as-mouse on the Wine desktop).
    val initGyroActivator by XServerDialogState.gyroActivator.collectAsState()
    var gyroActivator by remember(initGyroActivator) { mutableIntStateOf(initGyroActivator) }
    Spacer(Modifier.height(2.dp))
    Text(stringResource(R.string.gyro_drawer_activation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    ModeChipGrid(
        listOf(
            Triple(stringResource(R.string.gyro_activator_l1), gyroActivator == 0) { setGyroActivatorLive(0) { gyroActivator = it } },
            Triple(stringResource(R.string.gyro_activator_l2), gyroActivator == 1) { setGyroActivatorLive(1) { gyroActivator = it } },
            Triple(stringResource(R.string.gyro_activator_r1), gyroActivator == 2) { setGyroActivatorLive(2) { gyroActivator = it } },
            Triple(stringResource(R.string.gyro_activator_r3), gyroActivator == 3) { setGyroActivatorLive(3) { gyroActivator = it } },
            Triple(stringResource(R.string.gyro_drawer_activator_always), gyroActivator == 4) { setGyroActivatorLive(4) { gyroActivator = it } },
        ),
        perRow = 5
    )

    // Hold vs Toggle for that button. Greyed rather than hidden under "Always" — there's no button
    // to latch, but hiding the row would make it look like the setting vanished for good.
    val initGyroActivationMode by XServerDialogState.gyroActivationMode.collectAsState()
    var gyroActivationMode by remember(initGyroActivationMode) { mutableIntStateOf(initGyroActivationMode) }
    val activationModeEnabled = gyroActivator != 4
    Spacer(Modifier.height(4.dp))
    ModeChipGrid(
        listOf(
            Triple(stringResource(R.string.gyro_activation_hold), gyroActivationMode == 0) {
                setGyroActivationModeLive(0) { gyroActivationMode = it }
            },
            Triple(stringResource(R.string.gyro_activation_toggle), gyroActivationMode == 1) {
                setGyroActivationModeLive(1) { gyroActivationMode = it }
            },
        ),
        perRow = 2,
        enabled = activationModeEnabled
    )
    if (activationModeEnabled && gyroActivationMode == 1) {
        GyroHint(stringResource(R.string.gyro_drawer_toggle_hint))
    }

    // ---- Fine tuning: set once, then forgotten. Kept last so it never crowds the controls above. ----
    val initGyroDeadzone by XServerDialogState.gyroDeadzone.collectAsState()
    var gyroDeadzone by remember(initGyroDeadzone) { mutableFloatStateOf(initGyroDeadzone) }
    LabeledSlider(
        label = stringResource(R.string.gyro_deadzone_label),
        value = gyroDeadzone,
        valueRange = 0f..0.5f,
        onValueChange = { gyroDeadzone = it },
        onValueChangeFinished = {
            XServerDialogState.setGyroDeadzone(gyroDeadzone)
            XServerDialogState.onGyroDeadzoneChanged?.invoke(gyroDeadzone)
        },
        format = { "%.2f".format(it) }
    )

    val initGyroSmoothing by XServerDialogState.gyroSmoothing.collectAsState()
    var gyroSmoothing by remember(initGyroSmoothing) { mutableFloatStateOf(initGyroSmoothing) }
    LabeledSlider(
        label = stringResource(R.string.gyro_smoothing_label),
        value = gyroSmoothing,
        valueRange = 0f..0.95f,
        onValueChange = { gyroSmoothing = it },
        onValueChangeFinished = {
            XServerDialogState.setGyroSmoothing(gyroSmoothing)
            XServerDialogState.onGyroSmoothingChanged?.invoke(gyroSmoothing)
        },
        format = { "%.2f".format(it) }
    )

    val gyroInvertX by XServerDialogState.gyroInvertX.collectAsState()
    val gyroInvertY by XServerDialogState.gyroInvertY.collectAsState()
    ToggleChipGrid(
        listOf(
            ToggleChipItem(stringResource(R.string.gyro_invert_x), gyroInvertX) {
                XServerDialogState.setGyroInvertX(it)
                XServerDialogState.onGyroInvertXChanged?.invoke(it)
            },
            ToggleChipItem(stringResource(R.string.gyro_invert_y), gyroInvertY) {
                XServerDialogState.setGyroInvertY(it)
                XServerDialogState.onGyroInvertYChanged?.invoke(it)
            },
        ),
        perRow = 3
    )
}

// The one hint-text treatment used throughout the gyro section: dimmed, small, tucked under the row
// it explains. Also how a disabled chip states its reason, so "greyed out" is never unexplained.
@Composable
private fun GyroHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

// Target/activator changes have to hit the local picker state AND WinHandler (which resets the
// overlay so the old stick can't stay deflected) — kept out of the composable so the chip lambdas
// stay one-liners.
private fun setGyroTargetLive(target: Int, reflect: (Int) -> Unit) {
    reflect(target)
    XServerDialogState.setGyroTarget(target)
    XServerDialogState.onGyroTargetChanged?.invoke(target)
}

private fun setGyroActivatorLive(activator: Int, reflect: (Int) -> Unit) {
    reflect(activator)
    XServerDialogState.setGyroActivator(activator)
    XServerDialogState.onGyroActivatorChanged?.invoke(activator)
}

// Read-mode switch. The activity re-registers the sensor on the way through (rate and orientation
// read DIFFERENT sensors), so this is the one gyro setting whose callback has a side effect beyond
// WinHandler's own fields.
private fun setGyroModeLive(mode: Int, reflect: (Int) -> Unit) {
    reflect(mode)
    XServerDialogState.setGyroMode(mode)
    XServerDialogState.onGyroModeChanged?.invoke(mode)
}

// Same deal for the activation mode: WinHandler drops the toggle latch on the way through, so
// switching to Hold can't leave a latched-on gyro behind.
private fun setGyroActivationModeLive(mode: Int, reflect: (Int) -> Unit) {
    reflect(mode)
    XServerDialogState.setGyroActivationMode(mode)
    XServerDialogState.onGyroActivationModeChanged?.invoke(mode)
}

// ───── Advanced Tab ─────

@Composable
private fun AdvancedContent(state: XServerDrawerState) {
    SectionHeader("Advanced")

    AdvancedActionRow("Magnifier", R.drawable.icon_magnifier) {
        state.onClose?.run(); state.onMagnifier?.run()
    }
    AdvancedActionRow("Active Windows", R.drawable.icon_active_windows) {
        state.onClose?.run(); state.onActiveWindows?.run()
    }
    AdvancedActionRow("Debug Logs", R.drawable.icon_debug) {
        state.onClose?.run(); state.onLogs?.run()
    }
    AdvancedActionRow("Picture-in-Picture", R.drawable.ic_picture_in_picture_alt) {
        state.onClose?.run(); state.onPipMode?.run()
    }
    AdvancedActionRow("Show Keyboard", R.drawable.icon_keyboard) {
        state.onClose?.run(); state.onKeyboard?.run()
    }

    Spacer(Modifier.height(14.dp))

    // ── Performance ── one entry that opens the full dashboard dialog (Form C). This REPLACES the
    // old inline toggle stack; every control still binds to the SAME XServerDrawerState flows and
    // perf/* objects — now inside PerformanceDashboardDialog — so it is a pure drop-in swap.
    SectionHeader("Performance")
    var showPerfDialog by remember { mutableStateOf(false) }
    AdvancedActionRow(
        "Performance",
        R.drawable.ic_sidebar_performance,
        subtitle = "CPU / GPU clocks, thermal, memory — live gauges, root-aware.",
    ) { showPerfDialog = true }

    if (showPerfDialog) {
        PerformanceDashboardDialog(state) { showPerfDialog = false }
    }
}

// Subtle per-toggle indicator: "per-game override (Reset)" vs "using global default".
@Composable
private fun PerfOverrideLine(overridden: Boolean, onReset: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 1.dp, bottom = 6.dp)
    ) {
        Text(
            if (overridden) "● Per-game override" else "○ Using global default",
            fontSize = 10.sp,
            color = if (overridden) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (overridden) {
            Text("Reset to global", fontSize = 10.sp, color = accent,
                modifier = Modifier.clickable { onReset() })
        }
    }
}

/**
 * "Lock GPU to max clock" — the one PerfRootApplier-owned toggle that is NOT root-only, so it lives
 * with the non-root rows. Enabled when root is granted (sysfs pwrlevel pin) OR the device is Adreno
 * (non-root KGSL turbo). On a non-Adreno device with no root there is nothing to drive, so the row
 * greys out with a reason.
 */
@Composable
private fun GpuClockLockRow(state: XServerDrawerState, overridden: Set<String>) {
    val key = PerfRootApplier.KEY_GPU_CLOCK_LOCK
    val rootState by RootManager.state.collectAsState()
    val toggles by state.rootToggles.collectAsState()

    val granted = rootState == RootManager.RootState.GRANTED
    val enabled = granted || PerfGpuTurbo.isSupported

    ToggleRow("Lock GPU to max clock", toggles[key] ?: false, enabled = enabled) { on ->
        state.setRootToggle(key, on)
        state.onRootToggleChange?.accept(key, on)
    }
    if (!enabled) {
        Text(
            "🔒 Needs an Adreno GPU, or root on other GPUs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
        )
    } else {
        PerfOverrideLine(key in overridden) { state.onResetPerfKey?.accept(key) }
    }
}

// ── Root Performance sub-section (in-game). Mirrors App Settings' root tier; toggles are enabled
// only when root is GRANTED. Binds to the same shared stores (RootManager.state, PerfRevertRegistry
// .harnessProven, TempWatchdog.enabled) so App Settings <-> in-game stay in sync. ──
@Composable
private fun RootPerformanceSection(state: XServerDrawerState) {
    val rootState by RootManager.state.collectAsState()
    val harnessProven by PerfRevertRegistry.harnessProven.collectAsState()
    val toggles by state.rootToggles.collectAsState()
    val readouts by state.rootReadouts.collectAsState()
    val overridden by state.overriddenKeys.collectAsState()

    val granted = rootState == RootManager.RootState.GRANTED

    // Poll the live readouts while this section is on screen.
    LaunchedEffect(granted) {
        while (true) {
            state.onRootReadoutPoll?.run()
            delay(1500)
        }
    }

    SectionHeader("Root Performance")

    if (!granted) {
        Text(
            "🔒 " + when (rootState) {
                RootManager.RootState.UNAVAILABLE -> "No root manager detected."
                else -> "Grant root in App Settings → Performance to enable these."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    } else {
        // Live readouts.
        val gov = readouts["governor"] ?: "—"
        val gpu = readouts["gpuMhz"] ?: "—"
        val temp = readouts["socTemp"] ?: "—"
        val fan = readouts["fanRpm"] ?: "—"
        Text(
            "Gov $gov · GPU $gpu · SoC $temp · Fan $fan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }

    RootToggleRow(PerfRootApplier.KEY_CPU_GOVERNOR, "CPU governor → performance", state, toggles, granted, harnessProven, overridden)
    RootToggleRow(PerfRootApplier.KEY_CPU_FREQ_LOCK, "Lock CPU frequency to max", state, toggles, granted, harnessProven, overridden)
    RootToggleRow(PerfRootApplier.KEY_CORES_ONLINE, "Keep all cores online", state, toggles, granted, harnessProven, overridden)
    RootToggleRow(PerfRootApplier.KEY_THERMAL_DISABLE, "Disable thermal throttling", state, toggles, granted, harnessProven, overridden)
    RootToggleRow(PerfRootApplier.KEY_FAN_MAX, "Fan to maximum", state, toggles, granted, harnessProven, overridden)

    if (granted && !harnessProven) {
        Text(
            "Thermal / fan locked until safety-revert is verified on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (granted) {
        Spacer(Modifier.height(6.dp))
        // TIER 1 — light, honest label. Drops file caches; the system reclaims cache automatically.
        AdvancedActionRow(
            "Drop file caches", R.drawable.icon_task_manager,
            subtitle = "Frees cached files. Little visible RAM; the system reclaims cache automatically.",
        ) { state.onFreeMemory?.run() }
        // TIER 2 — the real RAM free (root-only). `am kill-all` never touches the running game/system.
        AdvancedActionRow(
            "Deep clean (free app memory)", R.drawable.icon_task_manager,
            subtitle = "Force-closes background apps to free real memory. Won't touch your game or system.",
        ) { state.onDeepClean?.run() }
    }

    // Temperature watchdog (device-wide; shared control block, identical + synced with App Settings).
    Spacer(Modifier.height(10.dp))
    WatchdogSection()
}

@Composable
private fun RootToggleRow(
    key: String,
    label: String,
    state: XServerDrawerState,
    toggles: Map<String, Boolean>,
    granted: Boolean,
    harnessProven: Boolean,
    overridden: Set<String>,
) {
    val gated = PerfRootApplier.isHarnessGated(key) && !harnessProven
    val enabled = granted && !gated
    ToggleRow(label, toggles[key] ?: false, enabled = enabled) { on ->
        state.setRootToggle(key, on)
        state.onRootToggleChange?.accept(key, on)
    }
    PerfOverrideLine(key in overridden) { state.onResetPerfKey?.accept(key) }
}

@Composable
private fun AdvancedActionRow(
    label: String,
    iconRes: Int,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, DeckPalette.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ───── Task Manager Tab ─────

@Composable
private fun TmContent() {
    val accent = MaterialTheme.colorScheme.primary
    val processes by XServerDialogState.tmProcesses.collectAsState()
    val cpuCores by XServerDialogState.tmCpuCores.collectAsState()
    val cpuTitle by XServerDialogState.tmCpuTitle.collectAsState()
    val memTitle by XServerDialogState.tmMemTitle.collectAsState()
    val memInfo by XServerDialogState.tmMemInfo.collectAsState()
    val count by XServerDialogState.tmCount.collectAsState()
    val header by XServerDialogState.tmHeader.collectAsState()
    val containerInfo by XServerDialogState.tmContainerInfo.collectAsState()

    // Polling is driven by a render-independent Handler timer in XServerDisplayActivity
    // (started via onTaskManager). A Compose LaunchedEffect delay() loop here stalls on the
    // Vulkan host-render path and left the Task Manager empty. onTmRefresh kicks an immediate
    // first refresh on entry; onTmDismissed stops the Activity timer on exit.
    LaunchedEffect(Unit) {
        XServerDialogState.onTmRefresh?.run()
    }

    DisposableEffect(Unit) {
        onDispose { XServerDialogState.onTmDismissed?.run() }
    }

    SectionHeader("Task Manager")

    // Enriched header: live perf stat grid + per-core clocks + collapsible container config.
    TmStatGrid(header)
    TmCoreStrip(header)
    TmContainerPanel(containerInfo)
    Spacer(Modifier.height(8.dp))

    Text(
        text = "Processes: $count",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )

    Spacer(Modifier.height(6.dp))

    if (processes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No processes", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    } else {
        // Each process is its own card (matching the app File Manager rows), not a flat
        // divider-separated list; cards self-space via their vertical padding.
        Column(modifier = Modifier.fillMaxWidth()) {
            processes.forEach { proc ->
                TmProcessRow(proc)
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = {
                XServerDialogState.onTmDismissed?.run()
                XServerDialogState.onTmNewTask?.run()
            }
        ) { Text("New Task\u2026", color = accent) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { XServerDialogState.onTmDismissed?.run() }) { Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TmProcessRow(proc: XServerDialogState.TmProcess) {
    val accent = MaterialTheme.colorScheme.primary
    var menuExpanded by remember { mutableStateOf(false) }
    var showAffinity by remember { mutableStateOf(false) }

    if (showAffinity) {
        ProcessorAffinityDialog(proc = proc, onDismiss = { showAffinity = false })
    }

    // Card per process, matching the app File Manager item style (rounded surfaceContainer
    // panel + outline border + vertical margin) instead of a flat divider-separated row.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (proc.icon != null) {
            Image(
                bitmap = proc.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.taskmgr_process),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = proc.name + if (proc.wow64) " *32" else "",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "PID ${proc.pid}  \u2022  ${proc.formattedMemory}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.outlinedMenuCard()
            ) {
                DropdownMenuItem(
                    text = { Text("Processor Affinity") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        showAffinity = true
                    },
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Bring to Front") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.FlipToFront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        XServerDialogState.onTmBringToFront?.invoke(proc.name, proc.pid)
                    },
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("End Process", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        XServerDialogState.onTmKillProcess?.invoke(proc.name)
                    },
                )
            }
        }
      }
    }
}

/**
 * Windows Task Manager-style "Set affinity" for one running process/service. A checkbox per logical
 * CPU (plus "<All Processors>"), pre-ticked to the process's current mask, pins that PID to the
 * chosen cores live via the already-wired [XServerDialogState.onTmSetAffinity]. Not persisted — it
 * resets if the process restarts, exactly like Windows.
 */
@Composable
private fun ProcessorAffinityDialog(
    proc: XServerDialogState.TmProcess,
    onDismiss: () -> Unit,
) {
    val coreCount = remember { Runtime.getRuntime().availableProcessors().coerceIn(1, 32) }
    val allMask = remember(coreCount) { if (coreCount >= 32) -1 else (1 shl coreCount) - 1 }
    // Open pre-ticked to the cores the user actually chose. Prefer the live override cache (what the
    // user last applied) over the guest's GetProcessAffinityMask readback, which is unreliable under
    // wow64/FEX. Fall back to the guest value, then to all.
    var mask by remember(proc.pid) {
        val stored = XServerDialogState.onTmQueryAffinity?.invoke(proc.pid) ?: -1
        val m = (if (stored > 0) stored else proc.affinityMask) and allMask
        mutableStateOf(if (m == 0) allMask else m)
    }
    val allChecked = (mask and allMask) == allMask

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Processor Affinity", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "Which processors are allowed to run \"${proc.name}\"?",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                // "<All Processors>" is a full-width master toggle. Each CPU is a compact bordered
                // tile (accent checkbox + "CPUn" label) matching the core picker in the container /
                // game settings (widget/CPUListView -> cpu_list_item). Cores come from the SAME
                // source the container picker uses (availableProcessors), so the set is identical
                // regardless of an arm64ec vs x86-64 container. Laid out 4-per-row so 8 cores fit in
                // two rows without scrolling; a heightIn + scroll fallback covers the 32-core cap.
                AffinityCheckRow(
                    label = "<All Processors>",
                    checked = allChecked,
                    bold = true,
                    onToggle = { mask = if (allChecked) 0 else allMask },
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                // All cores in one shaded panel with hairline dividers (matches the header strip).
                // Bounded + scrollable only as a fallback for very high core counts; 8 cores = 2 rows.
                Box(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    CorePanel(count = coreCount) { i ->
                        val on = (mask shr i) and 1 == 1
                        val cbAccent = MaterialTheme.colorScheme.primary
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { mask = (mask xor (1 shl i)) and allMask }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                        ) {
                            Checkbox(
                                checked = on,
                                onCheckedChange = { mask = (mask xor (1 shl i)) and allMask },
                                colors = CheckboxDefaults.colors(checkedColor = cbAccent),
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text("CPU$i", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = (mask and allMask) != 0,
                onClick = {
                    XServerDialogState.onTmSetAffinity?.invoke(proc.pid, mask and allMask)
                    onDismiss()
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AffinityCheckRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 1.dp, horizontal = 2.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(2.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───── Enriched Task Manager header ─────

/** One stat tile's content: tiny caption + a big value with a smaller muted suffix, tinted. */
private data class TmTile(val caption: String, val big: String, val small: String, val tint: Color)

/** Small uppercase section label with a hairline rule filling the remaining row width. Mirrors the
 *  mockup's `.seclabel::after`. [leading] slots an icon before the text (used by the CONTAINER header). */
@Composable
private fun TmSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        leading?.invoke()
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        )
    }
}

/** Dense 4-column stat-tile grid fed by the polled HudMetrics snapshot + FPS (mockup's PERFORMANCE
 *  block). CPU/GPU are accent-tinted, FPS reads "ok" green, battery reads "warn" amber; every
 *  nullable/Mali metric still renders gracefully as "—". */
@Composable
private fun TmStatGrid(h: XServerDialogState.TmHeaderStats?) {
    if (h == null) return
    val accent = MaterialTheme.colorScheme.primary
    val onSurf = MaterialTheme.colorScheme.onSurface
    val dark = isSystemInDarkTheme()
    val ok = if (dark) Color(0xFF6FDF9A) else Color(0xFF1F9D57)     // FPS — reads on both themes
    val warn = if (dark) Color(0xFFE2B06F) else Color(0xFFB5761A)   // battery

    // Leading numeric part of a "5.3GiB" style string, and a compact "GiB"->"G" unit form.
    fun numOf(s: String) = s.takeWhile { it.isDigit() || it == '.' }.ifEmpty { s }
    fun compact(s: String) = s.replace("iB", "")

    val tiles = buildList {
        add(TmTile("CPU", h.cpuPct?.let { "$it%" } ?: "—", h.cpuTempC?.let { " $it°" } ?: "", accent))
        add(TmTile("GPU", h.gpuPct?.let { "$it%" } ?: "—", h.gpuTempC?.let { " $it°" } ?: "", accent))
        add(TmTile("GPU CLK", h.gpuClockMhz?.let { "$it" } ?: "—", if (h.gpuClockMhz != null) "MHz" else "", onSurf))
        add(TmTile("FPS", "${h.fps}", if (h.fpsMin > 0) " ·${h.fpsMin}min" else "", ok))
        add(TmTile("RAM", numOf(h.ramUsed), "/" + compact(h.ramTotal), onSurf))
        if (h.swap != null) {
            add(TmTile("SWAP", numOf(h.swap.substringBefore("/")), "/" + compact(h.swap.substringAfter("/")), onSurf))
        } else {
            add(TmTile("SWAP", "—", "", onSurf))
        }
        add(TmTile("BAT", h.batteryPct?.let { "$it%" } ?: "—",
            " " + String.format("%.1f", h.batteryWatts) + "W" + (if (h.charging) " ⚡" else ""), warn))
        add(TmTile("BAT °C", h.batteryTempC?.let { "$it°" } ?: "—", "", onSurf))
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        TmSectionLabel("PERFORMANCE")
        Spacer(Modifier.height(4.dp))
        tiles.chunked(4).forEach { rowTiles ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.5.dp)) {
                rowTiles.forEachIndexed { i, t ->
                    StatTile(t, Modifier.weight(1f))
                    if (i < rowTiles.size - 1) Spacer(Modifier.width(5.dp))
                }
                // Pad a short final row so tiles keep the 4-column width.
                repeat(4 - rowTiles.size) {
                    Spacer(Modifier.width(5.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(tile: TmTile, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 7.dp, vertical = 6.dp),
    ) {
        Text(
            tile.caption,
            color = muted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tile.tint)) {
                    append(tile.big)
                }
                if (tile.small.isNotEmpty()) {
                    withStyle(SpanStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = muted)) {
                        append(tile.small)
                    }
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** Horizontal, scrollable per-core current-clock strip. Values are shown in FULL MHz (the raw
 *  perCoreMhz integer, NOT abbreviated to GHz) with a tiny "MHz" unit. */
@Composable
private fun TmCoreStrip(h: XServerDialogState.TmHeaderStats?) {
    val cores = h?.perCoreMhz ?: return
    if (cores.isEmpty()) return
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        CorePanel(count = cores.size) { i ->
            val mhz = cores[i]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Text("C$i", color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)) {
                            append(if (mhz > 0) "$mhz" else "—")
                        }
                        if (mhz > 0) {
                            withStyle(SpanStyle(fontSize = 8.sp, fontWeight = FontWeight.Medium, color = muted)) {
                                append(" MHz")
                            }
                        }
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

/** The cores as ONE shaded panel — the translucent accent wash used by the Vulkan/DXVK game-card
 *  component pills — with hairline dividers BETWEEN cells only (no per-cell borders, no outer-edge
 *  lines). 4-per-row; scales to any count. [cell] renders one core's content by 0-based index.
 *  Vertical hairlines use a Box rather than VerticalDivider (not on the classpath). */
@Composable
private fun CorePanel(
    count: Int,
    cols: Int = 4,
    cell: @Composable (Int) -> Unit,
) {
    if (count <= 0) return
    val accent = MaterialTheme.colorScheme.primary
    val div = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val rows = (count + cols - 1) / cols
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(12.dp)),
    ) {
        for (r in 0 until rows) {
            if (r > 0) HorizontalDivider(thickness = 1.dp, color = div)
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    if (c > 0) {
                        if (idx < count) {
                            Box(Modifier.width(1.dp).fillMaxHeight().background(div))
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                    }
                    if (idx < count) {
                        Box(Modifier.weight(1f)) { cell(idx) }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Collapsible panel showing the running container's config (Wine/DX/renderer/driver/res/device).
 *  DX wrapper + Renderer render in the accent colour; the raw resolved ids are prettified. */
@Composable
private fun TmContainerPanel(info: XServerDialogState.TmContainerInfo?) {
    if (info == null) return
    val accent = MaterialTheme.colorScheme.primary
    var expanded by remember { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        TmSectionLabel(
            "CONTAINER",
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded },
            leading = {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(3.dp))
            },
        )
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val wayland = info.displayBackend == "Wayland"
                ContainerInfoRow("Wine", info.wine)
                // "Display backend" row removed — hidden by request (the X11/Wayland choice is no
                // longer surfaced anywhere in the UI); `wayland` below still drives the Renderer line.
                ContainerInfoRow("DX wrapper", prettyDxWrapper(info.dxWrapper), accent)
                // Wayland: the game presents through the embedded compositor's Vulkan backend, and the
                // driver value is the "compositor: … · game: …" pair the activity resolved (wraps).
                ContainerInfoRow(
                    "Renderer",
                    when {
                        wayland -> "Vulkan (Wayland compositor)"
                        else -> prettyRenderer(info.renderer)
                    },
                    accent,
                )
                ContainerInfoRow("Graphics driver", info.graphicsDriver)
                // Wayland hands the GPU-name spoof to DXVK and leaves the Vulkan device alone, so this
                // is the only place that can say the game is being told about a GPU that isn't here.
                // Null (X11, or no spoof) = no row, the block exactly as it was.
                info.gpuSpoof?.let { ContainerInfoRow("GPU name", "$it (spoofed, not the real GPU)") }
                ContainerInfoRow("Resolution", info.resolution)
                // What the panel the game is on reports, read live. Reporting only - there is no HDR
                // output path, so there is deliberately no toggle beside it.
                ContainerInfoRow("HDR", info.hdr)
                ContainerInfoRow("Device", tidyDevice(info.device))
            }
        }
    }
}

@Composable
private fun ContainerInfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.42f))
        Text(
            value,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** Prettify a raw dxwrapper id (e.g. "dxvk+vkd3d") to its display form ("DXVK+VKD3D"). */
private fun prettyDxWrapper(raw: String): String {
    if (raw.isBlank() || raw == "—") return raw
    return raw.split("+").joinToString("+") { token ->
        when (token.trim().lowercase()) {
            "dxvk" -> "DXVK"
            "vkd3d" -> "VKD3D"
            "wined3d" -> "WineD3D"
            "vegas" -> "VEGAS"
            else -> token.trim().uppercase()
        }
    }
}

/** Prettify a raw renderer id (e.g. "vulkan") to its display form ("Vulkan"). */
private fun prettyRenderer(raw: String): String = when (raw.trim().lowercase()) {
    "" -> raw
    "vulkan" -> "Vulkan"
    "gl", "opengl", "gles" -> "OpenGL"
    "vortek" -> "Vortek"
    else -> raw.trim().replaceFirstChar { it.uppercase() }
}

/** Tidy the device line: "8 cores" -> "8c" so it fits the narrow value column without wrapping to a
 *  dangling separator. */
private fun tidyDevice(raw: String): String = raw.replace(" cores", "c")

// ───── Mods tab ─────

/** Mods tab: one card per mod this game's EXE can take (core.GameMods), then one per program the game
 *  starts. A loaded mod shows its release, the DLL it loads as and a button that shows or hides its
 *  in-game menu; an off or not-loaded one is dimmed. */
@Composable
private fun ModsContent(state: XServerDrawerState, mods: List<DrawerMod>) {
    SectionHeader("Mods")
    for (mod in mods) {
        ModCard(mod) {
            state.onClose?.run()
            state.onModMenu?.accept(mod.id)
        }
    }
}

@Composable
private fun ModCard(mod: DrawerMod, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (mod.loaded) Modifier else Modifier.alpha(0.45f)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DeckPalette.surface),
        border = BorderStroke(1.dp, DeckPalette.line),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mod.name,
                    color = DeckPalette.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val tag = when {
                    mod.loaded -> mod.version
                    !mod.on -> "Off"
                    else -> "Not loaded"
                }
                if (tag.isNotEmpty()) ModTag(tag)
            }
            if (mod.loaded) {
                Text(
                    if (mod.bits.isEmpty()) mod.file else "${mod.file} \u00b7 ${mod.bits}",
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(10.dp))
                AccentButton(mod.button, onClick = onClick)
            }
        }
    }
}

@Composable
private fun ModTag(text: String) {
    Text(
        text,
        color = DeckPalette.onBackground.copy(alpha = 0.85f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DeckPalette.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
