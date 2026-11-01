@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.winhub.hernandez.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.communityconfigs.CommunityConfigApply
import com.winhub.hernandez.store.DownloadManagerActivity
import com.winhub.hernandez.ui.AccountAvatar
import com.winhub.hernandez.ui.AccountUiBus
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.ui.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Games wall — a landscape-locked, 10-foot "Big Picture" library. This is the presentation the app now
// uses for the main library route (Screen.Games); it swaps the phone-grid ShortcutsScreen for a couch
// launcher that mirrors the approved mockup: a rail header (brand + search + status), a left nav rail,
// a centre-highlighted 2:3 cover wall (LazyRow), and a footer launch bar. Data + launch reuse the
// existing plumbing — ContainerManager.loadShortcuts(), loadCover() (local → SteamGridDB → monogram
// fallback), and launchShortcut() — so nothing about how games are stored or launched changes.
//
// Accent follows the user's chosen app theme (MaterialTheme.colorScheme.primary), read inside each
// composable — the wall never pins its own accent. Fully controller-first with a single root focus
// target (the same index-based, no-per-item-FocusRequester model BigPictureScreen uses to avoid
// phantom focus rings).

private val STAGE_BG = Color(0xFF0C0C0E)
private val TXT = Color(0xFFF3F3F5)
private val SUB = Color(0xFFA9A9AD)
private val DIM = Color(0xFF7C7C81)
private val PANEL = Color(0x0DFFFFFF)
private val BRD = Color(0x14FFFFFF)

private enum class WallZone { HEADER, RAIL, WALL, FOOTER }
private enum class WallSheet { TOOLS, POWER, OPTIONS }

// Rail rows, top→bottom, matching the mock: a dim Back, then the four nav items (Library is the
// current screen so it always wears the solid orange "selected" pill).
private const val RAIL_BACK = 0
private const val RAIL_LIBRARY = 1
private const val RAIL_COMMUNITY = 2
private const val RAIL_TOOLS = 3
private const val RAIL_POWER = 4
private const val RAIL_LAST = RAIL_POWER

@Composable
fun GamesWallScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val manager = remember { ContainerManager(context) }
    var shortcuts by remember { mutableStateOf<List<Shortcut>>(manager.loadShortcuts()) }

    // Community configs reuse the phone browser + account dialogs (both internal); applied to the
    // currently selected game via the shared ShortcutsViewModel apply methods.
    val vm: ShortcutsViewModel = viewModel()
    var showCommunity by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }

    // Optional community account — the brand in the header shows the user's avatar + username when signed
    // in (tap → the same MyAccountDialog the phone library uses), and the app logo + "WinHub" when
    // logged out. AccountUiBus mirrors AccountManager as Compose state and MyAccountDialog refreshes it on
    // every login/logout/avatar change, so the header swaps live. Refresh once on entry to pick up an
    // existing session.
    LaunchedEffect(Unit) { AccountUiBus.refresh(context) }
    val account = AccountUiBus.account
    var applyResult by remember { mutableStateOf<CommunityConfigApply.ConfigApplyResult?>(null) }

    // Decoded covers keyed by shortcut name; a plain in-flight set stops duplicate fetches. A name that
    // resolved to nothing is recorded in `noCover` so the tile shows the monogram (not a perpetual
    // loading state).
    val coverCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val inFlight = remember { HashSet<String>() }
    val noCover = remember { mutableStateMapOf<String, Boolean>() }

    var query by remember { mutableStateOf("") }
    val visible = remember(shortcuts, query) {
        val q = query.trim()
        if (q.isEmpty()) shortcuts else shortcuts.filter { it.name.contains(q, ignoreCase = true) }
    }
    var selectedIndex by remember { mutableStateOf(0) }
    if (selectedIndex > visible.lastIndex) selectedIndex = visible.lastIndex.coerceAtLeast(0)
    val selected = visible.getOrNull(selectedIndex)

    var zone by remember { mutableStateOf(WallZone.WALL) }
    var railIndex by remember { mutableStateOf(RAIL_LIBRARY) }
    var footerIndex by remember { mutableStateOf(0) } // 0 = Launch, 1 = Options
    var activeSheet by remember { mutableStateOf<WallSheet?>(null) }
    var editShortcut by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Single root focus target — every visible "focus" is our own zone/index state drawn as borders.
    val rootFocus = remember { FocusRequester() }
    val grabFocus: () -> Unit = { runCatching { rootFocus.requestFocus() } }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }

    // Lazily resolve a cover: custom path → SteamGridDB (persisted), exactly like BigPictureScreen.
    val ensureCover: (Shortcut) -> Unit = { s ->
        if (!coverCache.containsKey(s.name) && noCover[s.name] != true && inFlight.add(s.name)) {
            scope.launch(Dispatchers.IO) {
                val img = loadCover(s)
                withContext(Dispatchers.Main) {
                    if (img != null) coverCache[s.name] = img else noCover[s.name] = true
                    inFlight.remove(s.name)
                }
            }
        }
    }

    // Landscape-lock for the duration of this screen; restore the user's App-orientation pref on exit.
    DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose { AppOrientation.apply(activity) }
    }

    // Refresh on resume (new/edited games, changed covers) and re-grab focus after returning from a game.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shortcuts = manager.loadShortcuts()
                grabFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { grabFocus() }
    LaunchedEffect(activeSheet, editShortcut, showCommunity, showAccount, applyResult) {
        if (activeSheet == null && !editShortcut && !showCommunity && !showAccount && applyResult == null) grabFocus()
    }
    LaunchedEffect(selectedIndex, visible) { selected?.let { ensureCover(it) } }

    // Keep the selected tile roughly centred (same math BigPictureScreen uses).
    LaunchedEffect(selectedIndex, visible.size) {
        if (visible.isNotEmpty()) {
            val viewport = listState.layoutInfo.viewportSize.width
            val itemPx = with(density) { 150.dp.roundToPx() }
            val offset = if (viewport > 0) -(viewport / 2 - itemPx / 2) else 0
            runCatching { listState.animateScrollToItem(selectedIndex.coerceIn(0, visible.lastIndex), offset) }
        }
    }

    val onLaunch: () -> Unit = { selected?.let { launchShortcut(activity, it) } }

    val applyPick: (CommunityPick) -> Unit = { pick ->
        val target = selected
        if (target != null) {
            val onDone: (CommunityConfigApply.ConfigApplyResult) -> Unit = { res ->
                applyResult = res
                shortcuts = manager.loadShortcuts()
            }
            when (pick) {
                is CommunityPick.File -> vm.applyCommunityConfigFile(target, pick.ref, onDone)
                is CommunityPick.Device -> vm.applyCommunityConfig(target, pick.game, pick.device, onDone)
            }
        }
    }

    val activateRail: (Int) -> Unit = { idx ->
        when (idx) {
            RAIL_BACK -> if (!navController.popBackStack()) activity.finish()
            RAIL_LIBRARY -> { /* already here */ }
            RAIL_COMMUNITY -> showCommunity = true
            RAIL_TOOLS -> activeSheet = WallSheet.TOOLS
            RAIL_POWER -> activeSheet = WallSheet.POWER
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(STAGE_BG)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Overlays (community browser / account / apply result) own their own windows and D-pad;
                // only field the safety-net Back here.
                if (showCommunity || showAccount || applyResult != null) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.ButtonB, Key.Back -> {
                            when {
                                applyResult != null -> applyResult = null
                                showAccount -> showAccount = false
                                else -> showCommunity = false
                            }
                            true
                        }
                        else -> false
                    }
                }
                // A sheet up: only handle the close gesture; the sheet owns everything else.
                if (activeSheet != null || editShortcut) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.ButtonB, Key.Back -> { activeSheet = null; editShortcut = false; true }
                        else -> false
                    }
                }
                // While the search field holds focus let the IME have the keys; B pulls focus back out.
                if (searchFocused) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.ButtonB, Key.Back -> { keyboard?.hide(); grabFocus(); true }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        when (zone) {
                            WallZone.WALL -> if (selectedIndex > 0) selectedIndex--
                            WallZone.FOOTER -> footerIndex = 0
                            WallZone.RAIL -> { zone = WallZone.WALL }
                            WallZone.HEADER -> {}
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        when (zone) {
                            WallZone.WALL -> if (selectedIndex < visible.lastIndex) selectedIndex++
                            WallZone.FOOTER -> footerIndex = 1
                            WallZone.RAIL -> zone = WallZone.WALL
                            WallZone.HEADER -> {}
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        when (zone) {
                            WallZone.WALL -> { railIndex = RAIL_LIBRARY; zone = WallZone.RAIL }
                            WallZone.FOOTER -> zone = WallZone.WALL
                            WallZone.RAIL -> if (railIndex > RAIL_BACK) railIndex-- else zone = WallZone.HEADER
                            WallZone.HEADER -> {}
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        when (zone) {
                            WallZone.HEADER -> { railIndex = RAIL_LIBRARY; zone = WallZone.RAIL }
                            WallZone.RAIL -> if (railIndex < RAIL_LAST) railIndex++ else zone = WallZone.WALL
                            WallZone.WALL -> { footerIndex = 0; zone = WallZone.FOOTER }
                            WallZone.FOOTER -> {}
                        }
                        true
                    }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                        when (zone) {
                            WallZone.WALL -> onLaunch()
                            WallZone.FOOTER -> if (footerIndex == 0) onLaunch() else if (selected != null) activeSheet = WallSheet.OPTIONS
                            WallZone.RAIL -> activateRail(railIndex)
                            WallZone.HEADER -> { searchFocus.requestFocus(); keyboard?.show() }
                        }
                        true
                    }
                    Key.ButtonY -> { if (selected != null) activeSheet = WallSheet.OPTIONS; true }
                    Key.ButtonB, Key.Back -> { if (!navController.popBackStack()) activity.finish(); true }
                    else -> false
                }
            },
    ) {
        // Soft radial "stage" wash over the flat background (approximates the mock's twin radials).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF464A56).copy(alpha = 0.35f), Color.Transparent),
                        radius = 1400f,
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            WallHeader(
                query = query,
                onQuery = { query = it },
                searchFocused = searchFocused,
                headerFocused = zone == WallZone.HEADER,
                searchFocusRequester = searchFocus,
                onSearchFocusChanged = { searchFocused = it },
                onSearchBack = { keyboard?.hide(); grabFocus() },
                avatarUrl = account?.displayAvatarUrl,
                username = account?.username,
                onBrandClick = { showAccount = true },
            )

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                WallRail(
                    focusedIndex = if (zone == WallZone.RAIL) railIndex else -1,
                    onClick = { idx -> zone = WallZone.RAIL; railIndex = idx; activateRail(idx) },
                )

                // Games wall.
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        "LIBRARY · GAMES WALL",
                        color = DIM,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        modifier = Modifier.padding(start = 40.dp, top = 22.dp),
                    )
                    if (visible.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                if (query.isBlank()) "No games yet" else "No games match \"$query\"",
                                color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            val tileH = maxHeight.times(0.62f).coerceIn(140.dp, 230.dp)
                            val tileW = tileH * 2f / 3f
                            Box(contentAlignment = Alignment.Center) {
                                LazyRow(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                                    contentPadding = PaddingValues(horizontal = 44.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    itemsIndexed(visible) { index, s ->
                                        val isSel = index == selectedIndex
                                        LaunchedEffect(s.name) { ensureCover(s) }
                                        GameTile(
                                            name = s.name,
                                            cover = coverCache[s.name],
                                            selected = isSel,
                                            baseWidth = tileW,
                                            baseHeight = tileH,
                                            onClick = { zone = WallZone.WALL; selectedIndex = index },
                                        )
                                    }
                                }
                                // Edge scroll-mask: fade the wall's left/right into the background.
                                EdgeFade(Alignment.CenterStart)
                                EdgeFade(Alignment.CenterEnd)
                            }
                        }
                        ScrollHint()
                    }
                }
            }

            WallFooter(
                context = context,
                selected = selected,
                launchFocused = zone == WallZone.FOOTER && footerIndex == 0,
                optionsFocused = zone == WallZone.FOOTER && footerIndex == 1,
                onLaunch = { zone = WallZone.FOOTER; footerIndex = 0; onLaunch() },
                onOptions = { zone = WallZone.FOOTER; footerIndex = 1; if (selected != null) activeSheet = WallSheet.OPTIONS },
            )
        }
    }

    // ── Sheets ─────────────────────────────────────────────────────────────
    when (activeSheet) {
        WallSheet.TOOLS -> WallToolsSheet(
            onDismiss = { activeSheet = null },
            onNavigate = { route -> activeSheet = null; navController.navigate(route) },
            onDownloads = { activeSheet = null; context.startActivity(Intent(context, DownloadManagerActivity::class.java)) },
        )
        WallSheet.POWER -> WallPowerSheet(
            onDismiss = { activeSheet = null },
            onSettings = { activeSheet = null; navController.navigate(Screen.Settings.route) },
            onQuit = { activity.finishAffinity() },
        )
        WallSheet.OPTIONS -> selected?.let { s ->
            GameOptionsWallSheet(
                shortcut = s,
                onDismiss = { activeSheet = null },
                onEditShortcut = { activeSheet = null; editShortcut = true },
                onContainerSettings = { activeSheet = null; navController.navigate("container_detail?id=${s.container.id}") },
                onCommunityConfigs = { activeSheet = null; showCommunity = true },
                onRemoveCover = {
                    s.removeCustomCoverArt()
                    coverCache.remove(s.name); noCover.remove(s.name); inFlight.remove(s.name)
                    activeSheet = null
                },
            )
        } ?: run { activeSheet = null }
        null -> {}
    }

    if (editShortcut && selected != null) {
        ShortcutSettingsDialogScreen(
            shortcut = selected,
            onDismiss = { editShortcut = false; shortcuts = manager.loadShortcuts() },
        )
    }

    if (showCommunity) {
        CommunityCatalogBrowser(
            vm = vm,
            onDismiss = { showCommunity = false },
            onPick = { pick -> showCommunity = false; applyPick(pick) },
            onMyAccount = { showAccount = true },
        )
    }
    if (showAccount) {
        MyAccountDialog(vm = vm, onDismiss = { showAccount = false; AccountUiBus.refresh(context) }, onOpenMyUploads = { showAccount = false }, onLoggedIn = { AccountUiBus.refresh(context) })
    }
    applyResult?.let { res ->
        AlertDialog(
            onDismissRequest = { applyResult = null },
            title = { Text(if (res.ok) "Config applied" else "Couldn't apply") },
            text = { Text(res.message, color = TXT.copy(alpha = 0.85f)) },
            confirmButton = { TextButton(onClick = { applyResult = null }) { Text("Done") } },
        )
    }
}

// ── Rail header ───────────────────────────────────────────────────────────

@Composable
private fun WallHeader(
    query: String,
    onQuery: (String) -> Unit,
    searchFocused: Boolean,
    headerFocused: Boolean,
    searchFocusRequester: FocusRequester,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSearchBack: () -> Unit,
    avatarUrl: String?,
    username: String?,
    onBrandClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Brush.verticalGradient(listOf(Color(0x0AFFFFFF), Color.Transparent)))
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Brand doubles as the account entry point: the user's avatar + username when signed in, the app
        // logo (bolt) + "WinHub" when logged out. Tapping either opens the My-account sheet (login /
        // create / logout / profile).
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBrandClick)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AccountAvatar(avatarUrl = avatarUrl, size = 30.dp)
            } else {
                Box(
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                username?.takeIf { it.isNotBlank() } ?: "WinHub",
                color = TXT,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(26.dp))
        // Search pill.
        Row(
            modifier = Modifier
                .width(360.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PANEL)
                .border(
                    width = if (headerFocused || searchFocused) 2.dp else 1.dp,
                    color = if (headerFocused || searchFocused) accent else BRD,
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable { searchFocusRequester.requestFocus() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = DIM, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search…", color = DIM, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = TXT, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back)) {
                                onSearchBack(); true
                            } else false
                        },
                )
            }
        }

        Spacer(Modifier.weight(1f))
        // Clock + wifi + battery (clock is live; wifi/battery are decorative status glyphs).
        val clock = remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
        LaunchedEffect(Unit) {
            while (true) {
                clock.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                kotlinx.coroutines.delay(15_000)
            }
        }
        Text(clock.value, color = SUB, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Icon(Icons.Filled.Wifi, contentDescription = null, tint = SUB, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = SUB, modifier = Modifier.size(20.dp))
    }
}

// ── Left nav rail ─────────────────────────────────────────────────────────

@Composable
private fun WallRail(focusedIndex: Int, onClick: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.width(210.dp).fillMaxHeight()) {
        // Thin accent stripe (app theme accent) on the rail's left edge.
        Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent.copy(alpha = 0.9f)))
        Column(modifier = Modifier.fillMaxHeight().padding(start = 14.dp, end = 12.dp, top = 22.dp)) {
            RailItem(Icons.Filled.ChevronLeft, "back", isBack = true, selected = false, focused = focusedIndex == RAIL_BACK) { onClick(RAIL_BACK) }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 8.dp).background(BRD))
            Spacer(Modifier.height(10.dp))
            RailItem(Icons.Filled.VideoLibrary, "Library", isBack = false, selected = true, focused = focusedIndex == RAIL_LIBRARY) { onClick(RAIL_LIBRARY) }
            Spacer(Modifier.height(6.dp))
            RailItem(Icons.Filled.Public, "Community catalog", isBack = false, selected = false, focused = focusedIndex == RAIL_COMMUNITY) { onClick(RAIL_COMMUNITY) }
            Spacer(Modifier.height(6.dp))
            RailItem(Icons.Filled.Build, "Tools", isBack = false, selected = false, focused = focusedIndex == RAIL_TOOLS) { onClick(RAIL_TOOLS) }
            Spacer(Modifier.height(6.dp))
            RailItem(Icons.Filled.PowerSettingsNew, "Power", isBack = false, selected = false, focused = focusedIndex == RAIL_POWER) { onClick(RAIL_POWER) }
        }
    }
}

@Composable
private fun RailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isBack: Boolean,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = when {
        selected -> Color.White
        isBack -> DIM
        else -> SUB
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Cover tile ────────────────────────────────────────────────────────────

@Composable
private fun GameTile(
    name: String,
    cover: ImageBitmap?,
    selected: Boolean,
    baseWidth: androidx.compose.ui.unit.Dp,
    baseHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(if (selected) 1.14f else 1f, label = "wall-tile-scale")
    val alpha by animateFloatAsState(if (selected) 1f else 0.82f, label = "wall-tile-alpha")
    Box(
        modifier = Modifier
            .width(baseWidth)
            .height(baseHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(RoundedCornerShape(14.dp))
            .then(if (selected) Modifier.border(4.dp, accent, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        // Monogram fallback always underneath; real art crossfades in when resolved.
        MonogramTile(name)
        if (cover != null) {
            Crossfade(targetState = cover, label = "wall-cover") { c ->
                Image(
                    bitmap = c,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                )
            }
        }
    }
}

@Composable
private fun MonogramTile(name: String) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF3A3C44), Color(0xFF1C1D22)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White.copy(alpha = 0.85f), fontSize = 60.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdgeFade(align: Alignment) {
    val brush = if (align == Alignment.CenterStart)
        Brush.horizontalGradient(listOf(STAGE_BG, Color.Transparent))
    else
        Brush.horizontalGradient(listOf(Color.Transparent, STAGE_BG))
    Box(modifier = Modifier.align(align).fillMaxHeight().width(48.dp).background(brush))
}

@Composable
private fun ScrollHint() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = DIM, modifier = Modifier.size(18.dp))
        Text("scroll", color = DIM, fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DIM, modifier = Modifier.size(18.dp))
    }
}

// ── Footer launch bar ─────────────────────────────────────────────────────

@Composable
private fun WallFooter(
    context: Context,
    selected: Shortcut?,
    launchFocused: Boolean,
    optionsFocused: Boolean,
    onLaunch: () -> Unit,
    onOptions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x0AFFFFFF))))
            .padding(horizontal = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selected-game summary: name · playtime only (no completion % / play count — the mock's third
        // stat has no backing field).
        val accent = MaterialTheme.colorScheme.primary
        val playtime = remember(selected?.name) { wallPlaytime(context, selected?.name) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(selected?.name ?: "—", color = TXT, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected != null) {
                Spacer(Modifier.width(14.dp))
                Text("·", color = DIM, fontSize = 18.sp)
                Spacer(Modifier.width(14.dp))
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = SUB, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(playtime, color = SUB, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Button(
            onClick = onLaunch,
            enabled = selected != null,
            modifier = Modifier.height(52.dp).then(if (launchFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("LAUNCH", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(14.dp))
        OutlinedButton(
            onClick = onOptions,
            enabled = selected != null,
            modifier = Modifier.height(52.dp).then(if (optionsFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = TXT, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Options", color = TXT, fontWeight = FontWeight.Bold)
        }
        // Controller button hints.
        Spacer(Modifier.width(20.dp))
        GlyphHint("A", Color(0xFF7AC74F), Color(0xFF111111), "Launch")
        Spacer(Modifier.width(14.dp))
        GlyphHint("B", Color(0xFFE2564D), Color.White, "Back")
    }
}

@Composable
private fun GlyphHint(glyph: String, bg: Color, fg: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(bg), contentAlignment = Alignment.Center) {
            Text(glyph, color = fg, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = SUB, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Sheets ────────────────────────────────────────────────────────────────

private data class WallSheetRow(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val onClick: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallSheetScaffold(title: String, rows: List<WallSheetRow>, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainer,
        shape = sheetShape,
    ) {
        var focusIndex by remember { mutableStateOf(0) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        // The sheet is a plain Column, so on a short landscape screen the lower rows fell below the fold
        // with no way to reach them (no touch scroll, and D-pad focus walked off-screen). verticalScroll
        // restores touch scrolling; the BringIntoViewRequester keeps the D-pad-focused row on-screen.
        val scrollState = rememberScrollState()
        val bringers = remember(rows.size) { List(rows.size) { BringIntoViewRequester() } }
        LaunchedEffect(focusIndex) { runCatching { bringers.getOrNull(focusIndex)?.bringIntoView() } }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // App-menu outline — matches Modifier.outlinedMenuCard() / OutlinedAlertDialog (1dp outline).
                .border(1.dp, cs.outline, sheetShape)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp)
                .focusRequester(fr)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionUp -> { if (focusIndex > 0) focusIndex--; true }
                        Key.DirectionDown -> { if (focusIndex < rows.lastIndex) focusIndex++; true }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> { rows.getOrNull(focusIndex)?.onClick?.invoke(); true }
                        Key.ButtonB, Key.Back -> { onDismiss(); true }
                        else -> false
                    }
                },
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            HorizontalDivider(color = cs.outline, thickness = 1.dp)
            rows.forEachIndexed { i, r ->
                // Thin grey line between options, matching the app's menus.
                if (i > 0) HorizontalDivider(color = cs.outline, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringers[i])
                        .then(if (i == focusIndex) Modifier.background(cs.primary.copy(alpha = 0.18f)) else Modifier)
                        .clickable(onClick = r.onClick)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(r.icon, contentDescription = null, tint = cs.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(r.label, fontSize = 16.sp, color = cs.onSurface)
                }
            }
        }
    }
}

// Tools is also the escape hatch to the rest of the app: the games-wall replaces the drawer-bearing
// library, so the destinations the drawer used to reach (Containers, Contents, Saves, Settings…) live
// here to keep them reachable in couch mode.
@Composable
private fun WallToolsSheet(onDismiss: () -> Unit, onNavigate: (String) -> Unit, onDownloads: () -> Unit) {
    WallSheetScaffold(
        title = "Tools",
        rows = listOf(
            WallSheetRow(Icons.Filled.FolderOpen, "File Manager") { onNavigate(Screen.FileManager.route) },
            WallSheetRow(Icons.Filled.Layers, "Containers") { onNavigate(Screen.Containers.route) },
            WallSheetRow(Icons.Filled.Inventory2, "Contents") { onNavigate(Screen.Contents.route) },
            WallSheetRow(Icons.Filled.Save, "Saves") { onNavigate(Screen.Saves.route) },
            WallSheetRow(Icons.Filled.SportsEsports, "Input Controls") { onNavigate(Screen.InputControls.route) },
            WallSheetRow(Icons.Filled.Layers, "Manage Wrappers") { onNavigate(Screen.Wrappers.route) },
            WallSheetRow(Icons.Filled.Download, "Downloads") { onDownloads() },
            WallSheetRow(Icons.Filled.Settings, "Settings") { onNavigate(Screen.Settings.route) },
        ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun WallPowerSheet(onDismiss: () -> Unit, onSettings: () -> Unit, onQuit: () -> Unit) {
    WallSheetScaffold(
        title = "Power",
        rows = listOf(
            WallSheetRow(Icons.Filled.Settings, "App settings", onSettings),
            WallSheetRow(Icons.Filled.PowerSettingsNew, "Quit", onQuit),
        ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun GameOptionsWallSheet(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    onEditShortcut: () -> Unit,
    onContainerSettings: () -> Unit,
    onCommunityConfigs: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    val rows = buildList {
        add(WallSheetRow(Icons.Filled.Edit, "Edit shortcut", onEditShortcut))
        add(WallSheetRow(Icons.Filled.Tune, "Container settings", onContainerSettings))
        add(WallSheetRow(Icons.Filled.Public, "Community configs", onCommunityConfigs))
        if (!shortcut.customCoverArtPath.isNullOrEmpty()) {
            add(WallSheetRow(Icons.Filled.Image, "Remove cover art", onRemoveCover))
        }
    }
    WallSheetScaffold(title = shortcut.name, rows = rows, onDismiss = onDismiss)
}

// ── Helpers ───────────────────────────────────────────────────────────────

// Short "Xh Ym" playtime from the same prefs XServerDisplayActivity writes.
private fun wallPlaytime(context: Context, name: String?): String {
    if (name == null) return "0m"
    val prefs = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
    val totalMs = prefs.getLong("${name}_playtime", 0L)
    val totalMin = totalMs / 60000L
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
