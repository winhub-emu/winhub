package com.winhub.hernandez.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import com.winhub.hernandez.retro.RetroArt
import com.winhub.hernandez.retro.RetroCatalog
import com.winhub.hernandez.retro.RetroGame
import com.winhub.hernandez.retro.RetroLaunchResult
import com.winhub.hernandez.retro.RetroLibrary
import com.winhub.hernandez.retro.RetroScan
import com.winhub.hernandez.retro.RetroSystem
import com.winhub.hernandez.store.FolderPickerActivity
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.deck.DeckFloatingHint
import com.winhub.hernandez.ui.deck.DeckGlyph
import com.winhub.hernandez.ui.deck.GlyphKind
import com.winhub.hernandez.ui.deck.rememberScreenScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The retro library: every console the ROM folder has games for, then that console's games, in the look of the Home's All Games page.
// A game starts in the console's installed emulator (see RetroLibrary). Select on a pad, or the chip at the top left, switches back to
// the PC library.

private val PAGE_TEXT = Color(0xFFEDEDED)
private val PAGE_MUTED = Color(0xFFB4B4B4)

private sealed interface RetroState {
    data object Loading : RetroState
    data object NoRoot : RetroState
    data class Missing(val path: String) : RetroState
    data class Ready(val path: String, val scan: RetroScan) : RetroState
}

// The last scan, kept for the process so switching back and forth between the libraries doesn't walk the ROM folder every time.
private object RetroSession {
    @Volatile var scan: RetroScan? = null
    @Volatile var root: String? = null
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
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val ss = rememberScreenScale()
    val catalog = remember { RetroCatalog.get(context) }
    val hasPad = rememberGamepadConnected()

    var tick by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<RetroState>(RetroState.Loading) }
    // The cursor is kept by key (a system id, or a game's path), so it follows its item through a rescan.
    var openSystemId by rememberSaveable { mutableStateOf<String?>(null) }
    var selKey by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<List<RetroMenuItem>?>(null) }
    var menuSel by remember { mutableIntStateOf(0) }
    var coverProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Bumped when new covers have been saved, so the cells look again for their picture.
    var coverVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        state = RetroState.Loading
        state = withContext(Dispatchers.IO) { loadState(context) }
    }
    val rescan = { RetroSession.scan = null; tick++ }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra("path")?.takeIf { it.isNotBlank() }?.let {
            RetroLibrary.setRomRoot(context, it)
            rescan()
        }
    }
    val chooseFolder = { folderPicker.launch(Intent(context, FolderPickerActivity::class.java)) }

    val ready = state as? RetroState.Ready
    val scan = ready?.scan
    val systems = remember(scan) {
        if (scan == null) emptyList() else catalog.systems
            .filter { scan.bySystem[it.id].orEmpty().isNotEmpty() }
            .sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { it.name }))
    }
    val openSystem = systems.firstOrNull { it.id == openSystemId }
    val games = remember(scan, openSystem) { openSystem?.let { scan?.bySystem?.get(it.id) }.orEmpty() }
    val count = if (openSystem != null) games.size else systems.size
    val sel = remember(selKey, openSystem, systems, games) {
        (if (openSystem != null) games.indexOfFirst { it.path == selKey } else systems.indexOfFirst { it.id == selKey }).coerceAtLeast(0)
    }
    val moveSel = { i: Int ->
        if (openSystem != null) games.getOrNull(i)?.let { selKey = it.path } else systems.getOrNull(i)?.let { selKey = it.id }
    }
    val root = ready?.path

    val openSystemAt = { s: RetroSystem ->
        openSystemId = s.id
        selKey = scan?.bySystem?.get(s.id)?.firstOrNull()?.path
    }
    val closeSystem = {
        selKey = openSystemId
        openSystemId = null
    }
    val play = { system: RetroSystem, game: RetroGame ->
        when (val r = RetroLibrary.launch(context, system, game)) {
            RetroLaunchResult.Started -> Unit
            RetroLaunchResult.NoEmulator ->
                Toast.makeText(context, context.getString(R.string.retro_no_emulator, system.name), Toast.LENGTH_LONG).show()
            is RetroLaunchResult.Failed -> {
                android.util.Log.w("RetroLibrary", "Launch failed: ${r.reason}")
                Toast.makeText(context, context.getString(R.string.retro_launch_failed, system.name), Toast.LENGTH_LONG).show()
            }
        }
    }

    val startCovers = { system: RetroSystem, list: List<RetroGame> ->
        if (coverProgress == null) {
            if (system.thumbnails == null) {
                Toast.makeText(context, R.string.retro_covers_no_source, Toast.LENGTH_SHORT).show()
            } else scope.launch(Dispatchers.IO) {
                coverProgress = 0 to list.size
                val saved = RetroArt.download(context, system, list, root, stopped = { !isActive }, onProgress = { d, t -> coverProgress = d to t })
                coverProgress = null
                if (saved > 0) coverVersion++
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.resources.getQuantityString(R.plurals.retro_covers_saved, saved, saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val emulatorMenu = { system: RetroSystem ->
        val all = RetroLibrary.emulatorsFor(context, system)
        val installed = all.filter { RetroLibrary.isInstalled(context, it.packageName) }
        val auto = RetroMenuItem(stringResource0(context, R.string.retro_emulator_auto), stringResource0(context, R.string.retro_emulator_auto_sub)) {
            RetroLibrary.setEmulatorChoice(context, system.id, null); menu = null
        }
        val picks = installed.map { e ->
            RetroMenuItem(e.name, null) { RetroLibrary.setEmulatorChoice(context, system.id, e.packageName); menu = null }
        }
        if (picks.isEmpty()) {
            listOf(RetroMenuItem(stringResource0(context, R.string.retro_emulator_none), null) { menu = null })
        } else listOf(auto) + picks
    }
    val optionsMenu = {
        val items = ArrayList<RetroMenuItem>()
        if (openSystem != null) {
            val current = RetroLibrary.resolveEmulator(context, openSystem)?.name ?: stringResource0(context, R.string.retro_emulator_none_short)
            items += RetroMenuItem(stringResource0(context, R.string.retro_opt_emulator), current) {
                menu = emulatorMenu(openSystem); menuSel = 0
            }
            if (openSystem.thumbnails != null) {
                items += RetroMenuItem(stringResource0(context, R.string.retro_opt_covers), stringResource0(context, R.string.retro_opt_covers_sub)) {
                    menu = null; startCovers(openSystem, games)
                }
            }
        }
        items += RetroMenuItem(stringResource0(context, R.string.retro_opt_folder), root ?: RetroLibrary.romRoot(context)) {
            menu = null; chooseFolder()
        }
        items += RetroMenuItem(stringResource0(context, R.string.retro_opt_rescan), null) { menu = null; rescan() }
        menu = items
        menuSel = 0
    }

    // B goes up one level: the options, then the console's games, then back to the PC library.
    val goBack: () -> Unit = {
        when {
            menu != null -> menu = null
            openSystem != null -> closeSystem()
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

    val topInset = LocalTopBarOverlayInset.current
    val padH = ss.dp(16f)
    val gap = ss.dp(12f)
    val header = remember(topInset, ss.factor) { xmbAllHeader(topInset.value, ss.factor) }
    val gridState = rememberLazyGridState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = maxWidth - padH * 2
        val cols = if (openSystem != null) xmbGridColumns(avail.value, gap.value, ss.dp(80f).value)
        else xmbGridColumns(avail.value, gap.value, ss.dp(150f).value, wanted = 4, minCols = 2)
        val bottomMarginPx = with(density) { 8.dp.toPx() }
        val topMarginPx = with(density) { header.band.dp.toPx() }
        LaunchedEffect(sel, openSystem?.id, count) {
            withFrameNanos { }
            if (count > 0) gridState.reveal(sel + 1, topMarginPx, bottomMarginPx)
        }

        val selectedGame = if (openSystem != null) games.getOrNull(sel) else null
        val backdrop = rememberCover(openSystem, selectedGame, root, coverVersion)

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
                    val page = cols * 3
                    when (e.key) {
                        Key.DirectionLeft -> { moveSel(xmbGridMove(sel, XmbMove.LEFT, cols, count, page)); true }
                        Key.DirectionRight -> { moveSel(xmbGridMove(sel, XmbMove.RIGHT, cols, count, page)); true }
                        Key.DirectionUp -> { moveSel(xmbGridMove(sel, XmbMove.UP, cols, count, page)); true }
                        Key.DirectionDown -> { moveSel(xmbGridMove(sel, XmbMove.DOWN, cols, count, page)); true }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (openSystem != null) games.getOrNull(sel)?.let { play(openSystem, it) }
                            else systems.getOrNull(sel)?.let(openSystemAt)
                            true
                        }
                        Key.ButtonX -> { if (ready != null) optionsMenu(); true }
                        Key.ButtonSelect -> { onSwitchToPc(); true }
                        Key.ButtonB, Key.Escape -> { goBack(); true }
                        else -> false
                    }
                }
                // Nothing under the page may react to a touch that lands on it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            XmbHomeBackdrop(backdrop)

            when (val s = state) {
                RetroState.Loading -> CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center))
                RetroState.NoRoot -> RetroEmpty(
                    stringResource(R.string.retro_empty_no_folder_title), stringResource(R.string.retro_empty_no_folder_body), Modifier.align(Alignment.Center), chooseFolder,
                )
                is RetroState.Missing -> RetroEmpty(
                    stringResource(R.string.retro_empty_missing_title), stringResource(R.string.retro_empty_missing_body, s.path), Modifier.align(Alignment.Center), chooseFolder,
                )
                is RetroState.Ready -> if (count == 0) {
                    RetroEmpty(
                        stringResource(R.string.retro_empty_none_title), stringResource(R.string.retro_empty_none_body, s.path), Modifier.align(Alignment.Center), chooseFolder,
                    )
                } else {
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
                        if (openSystem != null) {
                            items(games.size, key = { games[it].path }) { i ->
                                val g = games[i]
                                GameCell(openSystem, g, i == sel, accent, root, coverVersion) { selKey = g.path; play(openSystem, g) }
                            }
                        } else {
                            items(systems.size, key = { systems[it].id }) { i ->
                                val sys = systems[i]
                                SystemTile(sys, s.scan.bySystem[sys.id].orEmpty().size, i == sel, accent) { selKey = sys.id; openSystemAt(sys) }
                            }
                        }
                    }
                }
            }

            // The header: the switch chip, then (inside a console) the back arrow, the title and the count; the pad hints on the right.
            val hintTap = Modifier.heightIn(min = 40.dp).widthIn(min = 40.dp)
            val backSrc = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = (padH - 12.dp).coerceAtLeast(0.dp), end = (padH - ss.dp(8f)).coerceAtLeast(0.dp), top = header.top.dp)
                    .height(header.height.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RetroSwitchChip(stringResource(R.string.retro_switch_to_pc).uppercase(), hasPad, onSwitchToPc, Modifier.padding(start = 8.dp))
                if (openSystem != null) {
                    Box(
                        Modifier.padding(start = ss.dp(8f)).size(48.dp).clip(CircleShape).xmbClick(backSrc) { closeSystem() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.retro_back),
                            tint = Color(0xFFDDDDDD), modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Row(Modifier.weight(1f).padding(start = ss.dp(8f)), verticalAlignment = Alignment.Bottom) {
                    Text(
                        openSystem?.name ?: stringResource(R.string.retro_title), color = Color.White, fontSize = ss.sp(16f),
                        fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    val progress = coverProgress
                    val subtitle = when {
                        progress != null -> stringResource(R.string.retro_covers_progress, progress.first, progress.second)
                        openSystem != null -> gamesLabel(games.size)
                        scan != null -> stringResource(R.string.retro_systems_count, systems.size, scan.total)
                        else -> null
                    }
                    if (subtitle != null) Text(
                        subtitle, color = PAGE_MUTED, fontSize = ss.sp(14f), maxLines = 1, modifier = Modifier.padding(start = ss.dp(8f), bottom = 1.dp),
                    )
                }
                if (ready != null) DeckFloatingHint(
                    "X".takeIf { hasPad }, GlyphKind.X, stringResource(R.string.retro_hint_options), { optionsMenu() }, hintTap,
                    icon = Icons.Filled.MoreHoriz, description = stringResource(R.string.retro_hint_options),
                )
                DeckFloatingHint(
                    "B".takeIf { hasPad }, GlyphKind.B, stringResource(R.string.retro_hint_back), { goBack() }, hintTap,
                    icon = Icons.AutoMirrored.Filled.ArrowBack, description = stringResource(R.string.retro_hint_back),
                )
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
                        .padding(top = (header.top + header.height).dp, end = padH)
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
        }
    }
}

@Composable
private fun gamesLabel(n: Int): String = LocalContext.current.resources.getQuantityString(R.plurals.retro_games_count, n, n)

// Reads a string resource inside plain lambdas, which are not composable.
private fun stringResource0(context: Context, id: Int): String = context.getString(id)

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

@Composable
private fun SystemTile(system: RetroSystem, count: Int, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .then(if (selected) Modifier.shadow(12.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.38f), Color(0xFF101114))))
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape))
            .xmbClick(src, onClick)
            .padding(12.dp),
    ) {
        Text(system.badge, color = Color.White.copy(alpha = 0.92f), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                system.name, color = if (selected) Color.White else PAGE_TEXT, fontSize = 14.sp, lineHeight = 17.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(gamesLabel(count), color = PAGE_MUTED, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun GameCell(
    system: RetroSystem,
    game: RetroGame,
    selected: Boolean,
    accent: Color,
    root: String?,
    coverVersion: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val src = remember { MutableInteractionSource() }
    val cover = rememberCover(system, game, root, coverVersion)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (selected) Modifier.shadow(12.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
                .clip(shape)
                .background(Color(0xFF111111))
                .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
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
        Text(
            game.title, color = if (selected) Color.White else PAGE_TEXT, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
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
