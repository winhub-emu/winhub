package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.winhub.hernandez.R
import com.winhub.hernandez.retro.RetroArt
import com.winhub.hernandez.retro.RetroCatalog
import com.winhub.hernandez.retro.RetroGame
import com.winhub.hernandez.retro.RetroIcons
import com.winhub.hernandez.retro.RetroLaunchResult
import com.winhub.hernandez.retro.RetroLibrary
import com.winhub.hernandez.retro.RetroScan
import com.winhub.hernandez.retro.RetroSystem
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.LocalXmbHintInset
import com.winhub.hernandez.ui.deck.DeckFloatingHint
import com.winhub.hernandez.ui.deck.DeckGlyph
import com.winhub.hernandez.ui.deck.GlyphKind
import com.winhub.hernandez.ui.deck.ScreenScale
import com.winhub.hernandez.ui.deck.XMB_FOCUS_OUTLINE
import com.winhub.hernandez.ui.deck.rememberScreenScale
import com.winhub.hernandez.ui.deck.rememberXmbLogo
import com.winhub.hernandez.ui.deck.xmbInkColor
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max

// The retro library, laid out like the PC Home: one row of covers (the current console's games) over the focused game's blurred cover,
// its title and console under it, and the consoles as the strip below. Up / Down (L2 / R2) change console, Y lists them all. A game starts in
// the console's installed emulator (see RetroLibrary). Select on a pad, or the chip at the top left, switches back to the PC library.

private val PAGE_TEXT = Color(0xFFEDEDED)
private val PAGE_MUTED = Color(0xFFB4B4B4)

private sealed interface RetroState {
    data object Loading : RetroState
    data object NoRoot : RetroState
    data class Missing(val path: String) : RetroState
    data class Ready(val path: String, val scan: RetroScan) : RetroState
}

// What outlives the screen for the process: the last scan, so switching between the libraries doesn't walk the ROM folder every time,
// and where the cursor was.
private object RetroSession {
    @Volatile var scan: RetroScan? = null
    @Volatile var root: String? = null
    @Volatile var lastSystemId: String? = null
    val lastGame = HashMap<String, String>()
}

private class RetroMenuItem(val label: String, val sub: String?, val onPick: () -> Unit)

/** The switch between the two libraries: a pill that says where it goes, with the pad's Select glyph when a pad is attached. */
@Composable
internal fun RetroSwitchChip(label: String, hasPad: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ss = rememberScreenScale()
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), shape)
            .xmbClick(src, onClick)
            .padding(horizontal = ss.dp(14f), vertical = ss.dp(6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ss.dp(8f)),
    ) {
        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = PAGE_TEXT, modifier = Modifier.size(ss.dp(20f)))
        Text(label, color = PAGE_TEXT, fontSize = ss.sp(14f), fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, maxLines = 1)
        if (hasPad) DeckGlyph("SELECT", GlyphKind.PLAIN)
    }
}

@Composable
internal fun RetroLibraryScreen(onSwitchToPc: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val ss = rememberScreenScale()
    val catalog = remember { RetroCatalog.get(context) }
    val hasPad = rememberGamepadConnected()
    val deckTabs = LocalDeckTabs.current
    val fullXmb = rememberFullXmb()
    val topInset = LocalTopBarOverlayInset.current
    val hintInset = LocalXmbHintInset.current

    var tick by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<RetroState>(RetroState.Loading) }
    var menu by remember { mutableStateOf<List<RetroMenuItem>?>(null) }
    var menuSel by remember { mutableIntStateOf(0) }
    var pageOpen by rememberSaveable { mutableStateOf(false) }
    var coverProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Bumped when a cover has been saved (so the cards look again for their picture), when the emulator choice changed and when the
    // covers are asked for again.
    var coverVersion by remember { mutableIntStateOf(0) }
    var emuTick by remember { mutableIntStateOf(0) }
    var scrapeTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        state = RetroState.Loading
        state = withContext(Dispatchers.IO) { loadState(context) }
    }
    val rescan = { RetroSession.scan = null; tick++ }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedPath(result.data)?.takeIf { it.isNotBlank() }?.let {
                RetroLibrary.setRomRoot(context, it)
                rescan()
            }
        }
    }
    val chooseFolder = {
        folderPicker.launch(
            InAppFilePicker.buildDirIntent(context, context.getString(R.string.retro_choose_folder), RetroLibrary.romRoot(context)),
        )
    }

    val ready = state as? RetroState.Ready
    val scan = ready?.scan
    val root = ready?.path
    val systems = remember(scan) {
        if (scan == null) emptyList() else catalog.systems
            .filter { scan.bySystem[it.id].orEmpty().isNotEmpty() }
            .sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { it.name }))
    }

    // The console is the shell's sub-tab (L2 / R2 step it), put back as it was when this screen goes, so the PC shelf is where it was.
    DisposableEffect(deckTabs) {
        val saved = deckTabs.index
        onDispose {
            deckTabs.bind(max(deckTabs.count, saved + 1))
            deckTabs.select(saved, quiet = true)
        }
    }
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(systems) {
        if (systems.isEmpty()) {
            deckTabs.bind(0)
        } else {
            deckTabs.bind(systems.size)
            val at = systems.indexOfFirst { it.id == RetroSession.lastSystemId }
            deckTabs.select(if (at >= 0) at else 0, quiet = true)
            restored = true
        }
    }
    val systemIdx = deckTabs.index.coerceIn(0, (systems.size - 1).coerceAtLeast(0))
    val system = systems.getOrNull(systemIdx)
    LaunchedEffect(system?.id, restored) { if (restored) RetroSession.lastSystemId = system?.id }

    val games = remember(scan, system) { system?.let { scan?.bySystem?.get(it.id) }.orEmpty() }
    val start = remember(system?.id, games) { games.indexOfFirst { it.path == RetroSession.lastGame[system?.id] }.coerceAtLeast(0) }
    var target by remember(system?.id, games) { mutableIntStateOf(start) }
    val pos = remember(system?.id, games) { mutableFloatStateOf(start.toFloat()) }
    LaunchedEffect(target, system?.id, games) {
        animate(pos.floatValue, target.toFloat(), animationSpec = tween(220)) { v, _ -> pos.floatValue = v }
        val sys = system
        val game = games.getOrNull(target)
        if (sys != null && game != null) RetroSession.lastGame[sys.id] = game.path
    }
    val focusedGame = games.getOrNull(target)
    val emulatorName = remember(system?.id, state, emuTick) { system?.let { RetroLibrary.resolveEmulator(context, it)?.name } }

    // The covers of the console on screen are fetched as it is looked at (the games from the cursor on first), so the library fills in
    // by itself; a game the server has no picture for is remembered and not asked again.
    LaunchedEffect(system?.id, games, scrapeTick) {
        val sys = system ?: return@LaunchedEffect
        if (sys.thumbnails == null || games.isEmpty()) return@LaunchedEffect
        val from = target.coerceIn(0, games.lastIndex)
        val ordered = games.drop(from) + games.take(from)
        withContext(Dispatchers.IO) {
            coverProgress = 0 to ordered.size
            RetroArt.download(
                context, sys, ordered, root,
                stopped = { !isActive },
                onProgress = { d, t -> coverProgress = d to t },
                onSaved = { coverVersion++ },
            )
            coverProgress = null
        }
    }

    val play = { sys: RetroSystem, game: RetroGame ->
        when (val r = RetroLibrary.launch(context, sys, game)) {
            RetroLaunchResult.Started -> Unit
            RetroLaunchResult.NoEmulator ->
                Toast.makeText(context, context.getString(R.string.retro_no_emulator, sys.name), Toast.LENGTH_LONG).show()
            is RetroLaunchResult.Failed -> {
                android.util.Log.w("RetroLibrary", "Launch failed: ${r.reason}")
                Toast.makeText(context, context.getString(R.string.retro_launch_failed, sys.name), Toast.LENGTH_LONG).show()
            }
        }
    }

    val emulatorMenu = { sys: RetroSystem ->
        val installed = RetroLibrary.emulatorsFor(context, sys).filter { RetroLibrary.isInstalled(context, it.packageName) }
        if (installed.isEmpty()) {
            listOf(RetroMenuItem(context.getString(R.string.retro_emulator_none), null) { menu = null })
        } else {
            listOf(
                RetroMenuItem(context.getString(R.string.retro_emulator_auto), context.getString(R.string.retro_emulator_auto_sub)) {
                    RetroLibrary.setEmulatorChoice(context, sys.id, null); emuTick++; menu = null
                },
            ) + installed.map { e ->
                RetroMenuItem(e.name, null) { RetroLibrary.setEmulatorChoice(context, sys.id, e.packageName); emuTick++; menu = null }
            }
        }
    }
    val removeGame = { sys: RetroSystem, game: RetroGame ->
        val current = ready
        if (current != null) {
            RetroLibrary.excludePath(context, game.path)
            // The cursor lands on the neighbour that takes its place.
            (games.getOrNull(target + 1) ?: games.getOrNull(target - 1))?.let { RetroSession.lastGame[sys.id] = it.path }
            val next = RetroScan(current.scan.bySystem.mapValues { (_, list) -> list.filter { it.path != game.path } }.filterValues { it.isNotEmpty() })
            RetroSession.scan = next
            state = RetroState.Ready(current.path, next)
            Toast.makeText(context, R.string.retro_removed, Toast.LENGTH_SHORT).show()
        }
    }
    val optionsMenu = {
        val items = ArrayList<RetroMenuItem>()
        if (system != null) {
            items += RetroMenuItem(context.getString(R.string.retro_opt_emulator), emulatorName ?: context.getString(R.string.retro_emulator_none_short)) {
                menu = emulatorMenu(system); menuSel = 0
            }
            if (system.thumbnails != null) {
                items += RetroMenuItem(context.getString(R.string.retro_opt_covers), context.getString(R.string.retro_opt_covers_sub)) {
                    menu = null
                    scope.launch(Dispatchers.IO) { RetroArt.clearMisses(context, system.id); scrapeTick++ }
                }
            }
        }
        if (system != null && focusedGame != null) {
            items += RetroMenuItem(context.getString(R.string.retro_opt_remove), focusedGame.title) {
                // Cancel is the one the cursor starts on, so a double press never removes by accident.
                menu = listOf(
                    RetroMenuItem(context.getString(R.string.retro_remove_yes), context.getString(R.string.retro_remove_yes_sub, focusedGame.title)) {
                        menu = null; removeGame(system, focusedGame)
                    },
                    RetroMenuItem(context.getString(R.string.retro_remove_cancel), null) { menu = null },
                )
                menuSel = 1
            }
        }
        val hidden = RetroLibrary.excludedPaths(context).size
        if (hidden > 0) {
            items += RetroMenuItem(context.getString(R.string.retro_opt_restore), context.getString(R.string.retro_opt_restore_sub, hidden)) {
                RetroLibrary.clearExcluded(context); menu = null; rescan()
            }
        }
        items += RetroMenuItem(context.getString(R.string.retro_opt_folder), root ?: RetroLibrary.romRoot(context)) { menu = null; chooseFolder() }
        items += RetroMenuItem(context.getString(R.string.retro_opt_rescan), null) { menu = null; rescan() }
        menu = items
        menuSel = 0
    }

    // B goes up one level: the options, then the consoles page, then back to the PC library.
    val goBack: () -> Unit = {
        when {
            menu != null -> menu = null
            pageOpen -> pageOpen = false
            else -> onSwitchToPc()
        }
    }
    val latestBack by rememberUpdatedState(goBack)
    val backLayer = LocalDeckBack.current
    DisposableEffect(backLayer) {
        backLayer.onBack = { latestBack(); true }
        onDispose { backLayer.onBack = null }
    }
    BackHandler { latestBack() }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focusRequester.requestFocus() } }
    // Back from the consoles page the keys return to the row.
    LaunchedEffect(pageOpen) { if (!pageOpen) { withFrameNanos { }; runCatching { focusRequester.requestFocus() } } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val screenH = maxHeight.value
        val h = screenH - topInset.value
        val sl = remember(w, h, hintInset, ss) {
            XmbSteamLayout(w, h, ss.dp(XMB_PEEK_CLIP).value, max(hintInset.value, 16f * ss.factor), ss.factor, 48f, 0f)
        }
        val backdropCover = rememberCover(system, focusedGame, root, coverVersion)

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || pageOpen) return@onPreviewKeyEvent false
                    val items = menu
                    if (items != null) {
                        return@onPreviewKeyEvent when (e.key) {
                            Key.DirectionUp -> { menuSel = (menuSel - 1).coerceAtLeast(0); true }
                            Key.DirectionDown -> { menuSel = (menuSel + 1).coerceAtMost(items.lastIndex); true }
                            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { items.getOrNull(menuSel)?.onPick?.invoke(); true }
                            Key.ButtonB, Key.Escape, Key.ButtonX -> { menu = null; true }
                            else -> false
                        }
                    }
                    when (e.key) {
                        Key.DirectionLeft -> { target = (target - 1).coerceAtLeast(0); true }
                        Key.DirectionRight -> { target = (target + 1).coerceAtMost(games.lastIndex).coerceAtLeast(0); true }
                        Key.DirectionUp -> { deckTabs.step(-1); true }
                        Key.DirectionDown -> { deckTabs.step(1); true }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (system != null) focusedGame?.let { play(system, it) }
                            true
                        }
                        Key.ButtonY -> { if (systems.isNotEmpty()) pageOpen = true; true }
                        Key.ButtonX -> { if (ready != null) optionsMenu(); true }
                        Key.ButtonSelect -> { onSwitchToPc(); true }
                        Key.ButtonB, Key.Escape -> { goBack(); true }
                        else -> false
                    }
                }
                // Nothing under the screen may react to a touch that lands on it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // The same backdrop as the PC Home: the focused cover, blurred, with the navy fade toward the strip.
            XmbSteamBackdrop(null, backdropCover, (topInset.value + sl.tabsTop - XMB_STEAM_FADE_LEAD * ss.factor) / screenH.coerceAtLeast(1f))

            Box(Modifier.fillMaxSize().padding(top = topInset)) {
                when (val s = state) {
                    RetroState.Loading -> CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center))
                    RetroState.NoRoot -> RetroEmpty(
                        stringResource(R.string.retro_empty_no_folder_title), stringResource(R.string.retro_empty_no_folder_body), Modifier.align(Alignment.Center), chooseFolder,
                    )
                    is RetroState.Missing -> RetroEmpty(
                        stringResource(R.string.retro_empty_missing_title), stringResource(R.string.retro_empty_missing_body, s.path), Modifier.align(Alignment.Center), chooseFolder,
                    )
                    is RetroState.Ready -> if (system == null) {
                        RetroEmpty(
                            stringResource(R.string.retro_empty_none_title), stringResource(R.string.retro_empty_none_body, s.path), Modifier.align(Alignment.Center), chooseFolder,
                        )
                    } else {
                        RetroHomeRow(
                            system, games, root, coverVersion, emulatorName, target, pos, sl, accent, ss,
                            onCard = { i -> if (i == target) play(system, games[i]) else target = i },
                            onStep = { delta -> target = (target + delta).coerceIn(0, games.lastIndex) },
                        )
                        RetroConsoleStrip(
                            systems, systemIdx, sl, ss, accent, Modifier.align(Alignment.TopCenter).offset(y = sl.tabsTop.dp),
                        ) { deckTabs.select(it) }
                    }
                }
            }

            // Top left the switch; top right the hints for the consoles page and the options, and the cover progress.
            val top = topInset + (if (fullXmb) 38.dp else 8.dp)
            RetroSwitchChip(
                stringResource(R.string.retro_switch_to_pc).uppercase(), hasPad, onSwitchToPc,
                Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = top),
            )
            if (ready != null && system != null) {
                val hintTap = Modifier.heightIn(min = 40.dp).widthIn(min = 40.dp)
                Row(
                    Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = top),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coverProgress?.let { (done, total) ->
                        Text(
                            stringResource(R.string.retro_covers_progress, done, total), color = PAGE_MUTED, fontSize = ss.sp(14f),
                            maxLines = 1, modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    DeckFloatingHint(
                        "Y".takeIf { hasPad }, GlyphKind.Y, stringResource(R.string.retro_hint_consoles), { pageOpen = true }, hintTap,
                        icon = Icons.Filled.GridView, description = stringResource(R.string.retro_hint_consoles),
                    )
                    DeckFloatingHint(
                        "X".takeIf { hasPad }, GlyphKind.X, stringResource(R.string.retro_hint_options), { optionsMenu() }, hintTap,
                        icon = Icons.Filled.MoreHoriz, description = stringResource(R.string.retro_hint_options),
                    )
                }
            }

            val items = menu
            if (items != null) {
                // A tap off the menu closes it.
                val off = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxSize().xmbClick(off) { menu = null })
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = top + 52.dp, end = 16.dp)
                        .widthIn(min = 240.dp, max = 360.dp)
                        .clip(shape)
                        .background(Color(0xEE16171B))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                        .padding(6.dp),
                ) {
                    items.forEachIndexed { i, item ->
                        val src = remember { MutableInteractionSource() }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (i == menuSel) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                .xmbClick(src) { menuSel = i; item.onPick() }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(item.label, color = PAGE_TEXT, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            if (item.sub != null) Text(item.sub, color = PAGE_MUTED, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (pageOpen && systems.isNotEmpty()) {
                RetroConsolesPage(
                    systems = systems,
                    counts = { s -> scan?.bySystem?.get(s.id).orEmpty().size },
                    current = systemIdx,
                    onPick = { i -> deckTabs.select(i); pageOpen = false },
                    onClose = { pageOpen = false },
                )
            }
        }
    }
}

/** Finds the ROM folder (the saved one, else the best usual place) and scans it. Run off the main thread. */
private fun loadState(context: Context): RetroState {
    var root = RetroLibrary.romRoot(context)
    if (root == null) {
        root = RetroLibrary.detectRoot(context)
        if (root != null) RetroLibrary.setRomRoot(context, root)
    }
    if (root == null) return RetroState.NoRoot
    val cached = RetroSession.scan
    if (cached != null && RetroSession.root == root) return RetroState.Ready(root, cached)
    val scan = RetroLibrary.scan(context, root) ?: return RetroState.Missing(root)
    RetroSession.root = root
    RetroSession.scan = scan
    return RetroState.Ready(root, scan)
}

// ── The row (the PC Home's: one row of covers over the backdrop, the focused card larger, its title under it) ──

@Composable
private fun RetroHomeRow(
    system: RetroSystem,
    games: List<RetroGame>,
    root: String?,
    coverVersion: Int,
    emulatorName: String?,
    target: Int,
    pos: MutableFloatState,
    sl: XmbSteamLayout,
    accent: Color,
    ss: ScreenScale,
    onCard: (Int) -> Unit,
    onStep: (Int) -> Unit,
) {
    val row = remember(sl, games.size) { XmbSteamRow(games.size, sl.fW, sl.fW, sl.gap, sl.w, sl.edge) }
    // Only the covers near the cursor are composed; each one's place is read from `pos` in its graphics layer.
    val base by remember { derivedStateOf { floor(pos.floatValue).toInt() } }
    Box(
        Modifier
            .fillMaxSize()
            // A swipe along the row moves the cursor, a card at a time.
            .pointerInput(games.size) {
                var acc = 0f
                val stepPx = 48.dp.toPx()
                detectHorizontalDragGestures(onDragStart = { acc = 0f }) { _, dx ->
                    acc += dx
                    while (acc <= -stepPx) { onStep(1); acc += stepPx }
                    while (acc >= stepPx) { onStep(-1); acc -= stepPx }
                }
            },
    ) {
        for (i in xmbSteamWindow(base.toFloat(), games.size, sl.w, sl.fW + sl.gap)) {
            key(games[i].path) {
                RetroCover(system, games[i], i, i == target, root, coverVersion, pos, sl, row, accent) { onCard(i) }
            }
        }
        RetroTitle(system, games.getOrNull(target), emulatorName, sl, row, target, pos, accent, ss)
    }
}

@Composable
private fun RetroCover(
    system: RetroSystem,
    game: RetroGame,
    index: Int,
    focused: Boolean,
    root: String?,
    coverVersion: Int,
    pos: MutableFloatState,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(XMB_STEAM_CORNER)
    val src = remember { MutableInteractionSource() }
    val cover = rememberCover(system, game, root, coverVersion)
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .size(sl.fW.dp, sl.fH.dp)
            .graphicsLayer {
                val p = pos.floatValue
                val sc = xmbSteamScale(index, p)
                translationX = row.x(index, p).dp.toPx()
                translationY = sl.cardTop.dp.toPx()
                scaleX = sc
                scaleY = sc
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .clip(shape)
            .background(Color(0xFF111111))
            .then(if (focused) Modifier.border(1.5.dp, XMB_FOCUS_OUTLINE, shape) else Modifier)
            .xmbClick(src, onClick),
    ) {
        if (cover != null) {
            Image(
                bitmap = remember(cover) { cover.asImageBitmap() },
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            XmbMonogram(game.title, accent)
        }
    }
}

// Under the focused card, left aligned with it (it follows the card as the row glides): the game's name, bold, and below it the console and
// the emulator in tiny light capitals behind a small play arrow.
@Composable
private fun RetroTitle(
    system: RetroSystem,
    game: RetroGame?,
    emulatorName: String?,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    target: Int,
    pos: MutableFloatState,
    accent: Color,
    ss: ScreenScale,
) {
    val ink = remember(accent) { xmbInkColor(accent, Color.White, XMB_STEAM_NAVY) }
    AnimatedContent(
        targetState = game,
        contentKey = { it?.path },
        transitionSpec = { xmbTitleTransition() },
        label = "retroTitle",
        modifier = Modifier
            .offset(0.dp, sl.titleY.dp - ss.dp(XMB_STEAM_TITLE_NUDGE))
            .width(sl.titleW.dp)
            .graphicsLayer {
                translationX = sl.titleX(row, target, pos.floatValue).dp.toPx() + ss.dp(XMB_STEAM_TITLE_INSET).toPx()
            },
    ) { g ->
        if (g != null) {
            val meta = if (emulatorName != null) "${system.name} · $emulatorName" else system.name
            Column {
                Text(
                    g.title, color = Color.White, fontSize = ss.sp(16f), fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(y = ss.dp(XMB_STEAM_TITLE_NUDGE)),
                    style = XMB_STEAM_TEXT.copy(lineHeight = ss.sp(15f), lineHeightStyle = XMB_STEAM_TOP),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        meta.uppercase(), color = XMB_STEAM_MUTED, fontSize = ss.sp(10f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        style = XMB_STEAM_TEXT.copy(lineHeight = ss.sp(10f), lineHeightStyle = XMB_STEAM_TIGHT),
                    )
                }
            }
        }
    }
}

// The consoles, in the place and shape of the PC Home's text tabs: the current one and two either side, the current one in a thin white
// outline with its illustration. A tap picks one; Y lists them all.
@Composable
private fun RetroConsoleStrip(
    systems: List<RetroSystem>,
    selected: Int,
    sl: XmbSteamLayout,
    ss: ScreenScale,
    accent: Color,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    val first = (selected - 2).coerceAtMost(systems.size - 5).coerceAtLeast(0)
    val last = (first + 4).coerceAtMost(systems.lastIndex)
    Row(modifier.height(XMB_STEAM_TABS_H.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in first..last) {
            val on = i == selected
            val shape = RoundedCornerShape(14.dp)
            val icon = RetroIcons.of(LocalContext.current, systems[i])
            Row(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(if (on) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Color.White, shape) else Modifier)
                    .xmbClick(remember { MutableInteractionSource() }) { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (on && icon != 0) RetroIcon(icon, Modifier.size(22.dp))
                Text(
                    systems[i].name.uppercase(),
                    color = if (on) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = ss.sp(10f),
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    style = XMB_STEAM_TEXT,
                )
            }
        }
    }
}

// ── The consoles page: every console with games, as tiles ──

@Composable
private fun RetroConsolesPage(
    systems: List<RetroSystem>,
    counts: (RetroSystem) -> Int,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val ss = rememberScreenScale()
    val topInset = LocalTopBarOverlayInset.current
    var sel by remember { mutableIntStateOf(current) }
    val padH = ss.dp(16f)
    val gap = ss.dp(12f)
    val header = remember(topInset, ss.factor) { xmbAllHeader(topInset.value, ss.factor) }
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focusRequester.requestFocus() } }
    val backSrc = remember { MutableInteractionSource() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = maxWidth - padH * 2
        val cols = xmbGridColumns(avail.value, gap.value, ss.dp(150f).value, wanted = 4, minCols = 2)
        val bottomMarginPx = with(density) { 8.dp.toPx() }
        val topMarginPx = with(density) { header.band.dp.toPx() }
        LaunchedEffect(sel) {
            withFrameNanos { }
            gridState.reveal(sel + 1, topMarginPx, bottomMarginPx)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF2060B16))
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val page = cols * 3
                    when (e.key) {
                        Key.DirectionLeft -> { sel = xmbGridMove(sel, XmbMove.LEFT, cols, systems.size, page); true }
                        Key.DirectionRight -> { sel = xmbGridMove(sel, XmbMove.RIGHT, cols, systems.size, page); true }
                        Key.DirectionUp -> { sel = xmbGridMove(sel, XmbMove.UP, cols, systems.size, page); true }
                        Key.DirectionDown -> { sel = xmbGridMove(sel, XmbMove.DOWN, cols, systems.size, page); true }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onPick(sel); true }
                        Key.ButtonY, Key.ButtonB, Key.Escape -> { onClose(); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            val fadeStartPx = with(density) { header.fadeStart.dp.toPx() }
            val fadeEndPx = with(density) { header.band.dp.toPx() }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(start = padH, end = padH, top = 0.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = fadeStartPx, endY = fadeEndPx),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                item(key = "retro-header", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.fillMaxWidth().height((header.band.dp - gap).coerceAtLeast(0.dp)))
                }
                items(systems.size, key = { systems[it].id }) { i ->
                    val s = systems[i]
                    ConsoleTile(s, counts(s), i == sel, i == current, accent) { sel = i; onPick(i) }
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = (padH - 12.dp).coerceAtLeast(0.dp), end = padH, top = header.top.dp)
                    .height(header.height.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).xmbClick(backSrc) { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retro_back),
                        tint = Color(0xFFDDDDDD), modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    stringResource(R.string.retro_consoles_title), color = Color.White, fontSize = ss.sp(16f), fontWeight = FontWeight.Light,
                    maxLines = 1, modifier = Modifier.padding(start = ss.dp(4f)),
                )
                Text(
                    stringResource(R.string.retro_consoles_count, systems.size), color = PAGE_MUTED, fontSize = ss.sp(14f),
                    maxLines = 1, modifier = Modifier.padding(start = ss.dp(8f)),
                )
            }
        }
    }
}

@Composable
private fun ConsoleTile(system: RetroSystem, count: Int, selected: Boolean, current: Boolean, accent: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val src = remember { MutableInteractionSource() }
    val icon = remember(system.id) { RetroIcons.of(context, system) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .then(if (selected) Modifier.shadow(12.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), Color(0xFF101114))))
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier.border(1.dp, Color.White.copy(alpha = if (current) 0.4f else 0.08f), shape))
            .xmbClick(src, onClick)
            .padding(12.dp),
    ) {
        if (icon != 0) {
            RetroIcon(icon, Modifier.align(Alignment.TopCenter).fillMaxWidth(0.62f).aspectRatio(1.6f))
        } else {
            Text(system.badge, color = Color.White.copy(alpha = 0.92f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.align(Alignment.TopStart))
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                system.name, color = if (selected) Color.White else PAGE_TEXT, fontSize = 14.sp, lineHeight = 17.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(gamesLabel(count), color = PAGE_MUTED, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/** A console illustration: a vector drawable as it is, a PNG decoded off the main thread at a sensible size. */
@Composable
private fun RetroIcon(@DrawableRes res: Int, modifier: Modifier) {
    val resources = LocalContext.current.resources
    val isVector = remember(res) {
        val value = TypedValue()
        resources.getValue(res, value, true)
        value.string?.endsWith(".xml") == true
    }
    if (isVector) {
        Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        val bmp = rememberXmbLogo(res)
        if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}

@Composable
private fun gamesLabel(n: Int): String = LocalContext.current.resources.getQuantityString(R.plurals.retro_games_count, n, n)

@Composable
private fun RetroEmpty(title: String, body: String, modifier: Modifier, onChoose: () -> Unit) {
    val ss = rememberScreenScale()
    val src = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier.widthIn(max = 520.dp).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = ss.sp(20f), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(body, color = PAGE_MUTED, fontSize = ss.sp(14f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
                .xmbClick(src, onChoose)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.retro_choose_folder), color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A game's cover, decoded off the main thread; null while it loads and when the game has none. */
@Composable
private fun rememberCover(system: RetroSystem?, game: RetroGame?, root: String?, version: Int): Bitmap? {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, game?.path, version) {
        value = if (system == null || game == null) null else withContext(Dispatchers.IO) { RetroCovers.load(context, system, game, root) }
    }
    return bmp
}

private object RetroCovers {
    private const val CACHE_BYTES = 24 * 1024 * 1024
    private const val TARGET_WIDTH = 360

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, system: RetroSystem, game: RetroGame, root: String?): Bitmap? {
        val file = RetroArt.find(context, system, game, root) ?: return null
        val key = "${file.path}:${file.lastModified()}"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bmp)
        return bmp
    }
}
