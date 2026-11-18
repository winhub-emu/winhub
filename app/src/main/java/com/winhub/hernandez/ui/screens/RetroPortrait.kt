package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.winhub.hernandez.retro.RetroIcons
import com.winhub.hernandez.retro.RetroLaunchResult
import com.winhub.hernandez.retro.RetroLibrary
import com.winhub.hernandez.retro.RetroMeta
import com.winhub.hernandez.retro.RetroScan
import com.winhub.hernandez.retro.RetroSystem
import com.winhub.hernandez.ui.deck.lastPlayedLabel
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The retro library in portrait: the PC library's portrait Home (PortraitHome) and game page (PortraitGamePage) and the same game menu
// sheet, fed with the games of the consoles picked in Settings. Tap a cover for its page, hold it for the menu.

/** The first chip of the shelf row: switches between the PC library and the retro one. */
@Composable
internal fun RetroPortraitSwitchChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(shape)
            .border(1.5.dp, Deck.primary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = Deck.primary, modifier = Modifier.size(20.dp))
        Text(label, color = Deck.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, maxLines = 1)
    }
}

private fun retroPlay(context: Context, sys: RetroSystem, game: RetroGame) {
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

@Composable
internal fun RetroPortraitScreen(onSwitchToPc: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { RetroCatalog.get(context) }

    var tick by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<RetroState>(RetroState.Loading) }
    var enabled by remember { mutableStateOf(RetroLibrary.enabledSystems(context)) }
    // The filter (a console id, "" for All) and the game whose page is open survive a rotation.
    var filterId by rememberSaveable { mutableStateOf("") }
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<RetroGame?>(null) }
    var emulatorFor by remember { mutableStateOf<RetroGame?>(null) }
    var removeFor by remember { mutableStateOf<RetroGame?>(null) }
    var detailsFor by remember { mutableStateOf<RetroGame?>(null) }
    var consolesDialog by remember { mutableStateOf(false) }
    var coverVersion by remember { mutableIntStateOf(0) }
    var emuTick by remember { mutableIntStateOf(0) }
    var scrapeTick by remember { mutableIntStateOf(0) }
    var refreshPath by remember { mutableStateOf<String?>(null) }
    var metaTick by remember { mutableIntStateOf(0) }

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
    val played = remember(scan) { RetroLibrary.lastPlayed(context) }
    val metaMap = remember(scan, metaTick) { RetroLibrary.allMeta(context) }
    val consoles = remember(scan, enabled) {
        if (scan == null) emptyList() else catalog.systems
            .filter { it.id in enabled && scan.bySystem[it.id].orEmpty().isNotEmpty() }
            .sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { it.name }))
    }
    val allGames = remember(scan, enabled, played, metaMap) {
        if (scan == null) emptyList() else scan.bySystem
            .filterKeys { it in enabled }.values.flatten()
            .map { g -> metaMap[g.path]?.name?.takeIf { it.isNotBlank() }?.let { g.copy(title = it) } ?: g }
            .sortedWith(compareByDescending<RetroGame> { played[it.path] ?: 0L }.thenBy { it.title.lowercase() })
    }
    // A filter whose console has no games any more is All again.
    val filter = consoles.firstOrNull { it.id == filterId }
    val games = remember(allGames, filter) { if (filter == null) allGames else allGames.filter { it.systemId == filter.id } }
    val opened = openPath?.let { p -> allGames.firstOrNull { it.path == p } }

    // The game pages are full pages: the shell drops its waves behind them, as it does for the PC library's page.
    LaunchedEffect(opened != null) { PortraitPageState.open = opened != null }
    DisposableEffect(Unit) { onDispose { PortraitPageState.open = false } }

    // The pictures of the games on screen are fetched by themselves, the one played last (the banner) first.
    LaunchedEffect(filter?.id, games, scrapeTick) {
        if (games.isEmpty()) return@LaunchedEffect
        val banner = games.firstOrNull { played.containsKey(it.path) }
        val refreshed = games.firstOrNull { it.path == refreshPath }
        val ordered = (listOfNotNull(refreshed, banner) + games).distinct()
        withContext(Dispatchers.IO) {
            RetroArt.download(
                context, ordered, root,
                stopped = { !isActive },
                onProgress = { _, _ -> },
                onSaved = { coverVersion++ },
                wide = setOfNotNull(banner?.path, refreshPath),
            )
        }
    }
    // The open page's game gets its background at once, wherever it is in the list.
    LaunchedEffect(opened?.path) {
        val g = opened ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            RetroArt.download(context, listOf(g), root, stopped = { !isActive }, onProgress = { _, _ -> }, onSaved = { coverVersion++ })
        }
    }

    val removeGame = { game: RetroGame ->
        val current = ready
        if (current != null) {
            RetroLibrary.excludePath(context, game.path)
            val next = RetroScan(current.scan.bySystem.mapValues { (_, list) -> list.filter { it.path != game.path } }.filterValues { it.isNotEmpty() })
            RetroSession.scan = next
            state = RetroState.Ready(current.path, next)
            openPath = null
            Toast.makeText(context, R.string.retro_removed, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        if (opened != null) {
            openPath = null
        } else {
            onSwitchToPc()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            RetroState.Loading -> CircularProgressIndicator(color = Deck.primary, modifier = Modifier.align(Alignment.Center))
            RetroState.NoRoot -> RetroPortraitEmpty(
                stringResource(R.string.retro_empty_no_folder_title), stringResource(R.string.retro_empty_no_folder_body),
                stringResource(R.string.retro_choose_folder), chooseFolder, onSwitchToPc,
            )
            is RetroState.Missing -> RetroPortraitEmpty(
                stringResource(R.string.retro_empty_missing_title), stringResource(R.string.retro_empty_missing_body, s.path),
                stringResource(R.string.retro_choose_folder), chooseFolder, onSwitchToPc,
            )
            is RetroState.Ready -> if (enabled.isEmpty()) {
                RetroPortraitEmpty(
                    stringResource(R.string.retro_empty_no_consoles_title), stringResource(R.string.retro_empty_no_consoles_body),
                    stringResource(R.string.retro_pick_title), { consolesDialog = true }, onSwitchToPc,
                )
            } else {
                RetroPortraitGrid(
                    games = games,
                    consoles = consoles,
                    filterId = filter?.id.orEmpty(),
                    played = played,
                    root = root,
                    version = coverVersion,
                    catalog = catalog,
                    emptyText = stringResource(R.string.retro_empty_none_body, s.path),
                    onFilter = { filterId = it },
                    onSwitchToPc = onSwitchToPc,
                    onOpen = { openPath = it.path },
                    onMenu = { menuFor = it },
                    onConsoles = { consolesDialog = true },
                )
            }
        }

        opened?.let { game ->
            val sys = catalog.byId[game.systemId]
            if (sys != null) {
                val emulatorName = remember(sys.id, emuTick) { RetroLibrary.resolveEmulator(context, sys)?.name }
                RetroPortraitGamePage(
                    game = game,
                    system = sys,
                    emulatorName = emulatorName,
                    meta = metaMap[game.path],
                    lastPlayed = played[game.path],
                    root = root,
                    version = coverVersion,
                    onBack = { openPath = null },
                    onPlay = { retroPlay(context, sys, game) },
                    onDetails = { detailsFor = game },
                    onMenu = { menuFor = game },
                )
            }
        }
    }

    // The game menu: the PC library's sheet, with the retro library's rows.
    menuFor?.let { game ->
        val sys = catalog.byId[game.systemId]
        if (sys != null) {
            val emulatorName = RetroLibrary.resolveEmulator(context, sys)?.name
            // This game's own options; the library's (consoles, ROM folder, removed games, rescan) are in Settings > Retro library.
            val items = buildList {
                add(DeckConsoleItem(context.getString(R.string.retro_opt_play), Icons.Filled.PlayArrow) { retroPlay(context, sys, game) })
                add(DeckConsoleItem(context.getString(R.string.retro_opt_details), Icons.Filled.Edit) { detailsFor = game })
                add(DeckConsoleItem(context.getString(R.string.retro_opt_emulator), Icons.Filled.SportsEsports) { emulatorFor = game })
                add(DeckConsoleItem(context.getString(R.string.retro_opt_covers), Icons.Filled.PhotoLibrary) {
                    scope.launch(Dispatchers.IO) {
                        RetroArt.forgetMisses(context, game)
                        refreshPath = game.path
                        scrapeTick++
                    }
                })
                add(DeckConsoleItem(context.getString(R.string.retro_opt_remove), Icons.Filled.Delete, danger = true) { removeFor = game })
            }
            PortraitGameMenuSheet(
                title = game.title,
                subtitle = if (emulatorName != null) "${sys.name} · $emulatorName" else sys.name,
                cover = rememberCover(sys, game, root, coverVersion),
                items = items,
                onDismiss = { menuFor = null },
            )
        }
    }
    // The emulator to start the game's console in.
    emulatorFor?.let { game ->
        val sys = catalog.byId[game.systemId]
        if (sys != null) {
            val installed = RetroLibrary.emulatorsFor(context, sys).filter { RetroLibrary.isInstalled(context, it.packageName) }
            val items = if (installed.isEmpty()) {
                listOf(DeckConsoleItem(context.getString(R.string.retro_emulator_none), Icons.Filled.SportsEsports) { })
            } else {
                listOf(
                    DeckConsoleItem(context.getString(R.string.retro_emulator_auto), Icons.Filled.SportsEsports) {
                        RetroLibrary.setEmulatorChoice(context, sys.id, null); emuTick++
                    },
                ) + installed.map { e ->
                    DeckConsoleItem(e.name, Icons.Filled.SportsEsports) { RetroLibrary.setEmulatorChoice(context, sys.id, e.packageName); emuTick++ }
                }
            }
            PortraitGameMenuSheet(
                title = sys.name,
                subtitle = context.getString(R.string.retro_opt_emulator),
                cover = null,
                items = items,
                onDismiss = { emulatorFor = null },
            )
        }
    }
    removeFor?.let { game ->
        DeckWarningDialog(
            title = stringResource(R.string.retro_opt_remove),
            message = stringResource(R.string.retro_remove_yes_sub, game.title),
            confirmLabel = stringResource(R.string.retro_remove_yes),
            onConfirm = { removeFor = null; removeGame(game) },
            onDismiss = { removeFor = null },
        )
    }
    detailsFor?.let { game ->
        RetroGameDetailsDialog(game, metaMap[game.path] ?: RetroMeta()) { saved ->
            if (saved != null) {
                RetroLibrary.setMeta(context, game.path, saved)
                metaTick++
            }
            detailsFor = null
        }
    }
    if (consolesDialog) RetroConsolesDialog { consolesDialog = false; enabled = RetroLibrary.enabledSystems(context) }
}

/** What shows when there is no grid yet (no ROM folder, no consoles picked): the switch back, and the way forward. */
@Composable
private fun RetroPortraitEmpty(title: String, body: String, action: String, onAction: () -> Unit, onSwitchToPc: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        RetroPortraitSwitchChip(stringResource(R.string.retro_switch_to_pc).uppercase(), onSwitchToPc)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RetroEmpty(title, body, action, Modifier, onAction)
        }
    }
}

/** The portrait Home's grid: header, shelf chips (the switch, All, one per console), the banner, the Library label and the covers. */
@Composable
private fun RetroPortraitGrid(
    games: List<RetroGame>,
    consoles: List<RetroSystem>,
    filterId: String,
    played: Map<String, Long>,
    root: String?,
    version: Int,
    catalog: RetroCatalog,
    emptyText: String,
    onFilter: (String) -> Unit,
    onSwitchToPc: () -> Unit,
    onOpen: (RetroGame) -> Unit,
    onMenu: (RetroGame) -> Unit,
    onConsoles: () -> Unit,
) {
    val context = LocalContext.current
    val continueGame = games.firstOrNull { played.containsKey(it.path) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { PortraitHeader() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                RetroPortraitSwitchChip(stringResource(R.string.retro_switch_to_pc).uppercase(), onSwitchToPc)
                RetroPortraitChip(stringResource(R.string.retro_filter_all), filterId.isEmpty()) { onFilter("") }
                consoles.forEach { c -> RetroPortraitChip(c.name, filterId == c.id) { onFilter(c.id) } }
            }
        }
        if (continueGame != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RetroContinueBanner(
                    continueGame, catalog.byId[continueGame.systemId], played[continueGame.path], root, version,
                    onOpen = { onOpen(continueGame) },
                    onPlay = { catalog.byId[continueGame.systemId]?.let { retroPlay(context, it, continueGame) } },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (games.isEmpty()) stringResource(R.string.retro_portrait_library)
                else stringResource(R.string.retro_portrait_library_count, games.size),
                color = Deck.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .aspectRatio(0.66f)
                    .glassCard(RoundedCornerShape(14.dp))
                    .clickable { onConsoles() },
            ) {
                Icon(Icons.Filled.GridView, contentDescription = stringResource(R.string.retro_opt_consoles), tint = Deck.primary, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.retro_opt_consoles), color = Deck.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    emptyText,
                    color = Deck.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        items(count = games.size, key = { games[it].path }) { i ->
            val g = games[i]
            RetroPortraitCover(g, catalog.byId[g.systemId], root, version, onClick = { onOpen(g) }, onLongClick = { onMenu(g) })
        }
    }
}

/** A shelf chip, as the PC library's. */
@Composable
private fun RetroPortraitChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(48.dp)
            .then(
                if (on) Modifier
                    .clip(shape)
                    .background(Deck.primary.copy(alpha = 0.18f))
                    .border(1.5.dp, Deck.primary, shape)
                else Modifier.glassCard(shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            label,
            color = if (on) Deck.primary else Deck.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** A cover of the grid, as the PC library's: the picture (or the title on a colour), a ring, the console's mark. Hold for the menu. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RetroPortraitCover(game: RetroGame, system: RetroSystem?, root: String?, version: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val hue = remember(game.title) { hueOf(game.title) * 360f }
    val cover = rememberCover(system, game, root, version)
    val bitmap = remember(cover) { cover?.asImageBitmap() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .aspectRatio(0.66f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.25f))))
            .border(1.5.dp, deckAccentBrush(), shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = game.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                game.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
        if (system != null) RetroConsoleBadge(system, Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

/** The console's mark on a cover, where the PC library has the store's logo. */
@Composable
private fun RetroConsoleBadge(system: RetroSystem, modifier: Modifier) {
    val context = LocalContext.current
    val icon = remember(system.id) { RetroIcons.of(context, system) }
    Box(modifier.size(24.dp).clip(CircleShape).background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        if (icon != 0) RetroIcon(icon, Modifier.size(16.dp))
        else Text(system.badge.take(3), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The game's wide picture (its banner, else its background), decoded off the main thread; null while it loads and when there is none. */
@Composable
private fun rememberWideArt(game: RetroGame?, version: Int): Bitmap? {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, game?.path, version) {
        value = if (game == null) null else withContext(Dispatchers.IO) { RetroPictures.wide(context, game) }
    }
    return bmp
}

/** "Continue playing": the PC library's banner, with the game played last. Tap = its page, Play = go. */
@Composable
private fun RetroContinueBanner(
    game: RetroGame,
    system: RetroSystem?,
    lastPlayed: Long?,
    root: String?,
    version: Int,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val wide = rememberWideArt(game, version)
    val cover = rememberCover(system, game, root, version)
    val art = remember(wide, cover) { (wide ?: cover)?.asImageBitmap() }
    val whenPlayed = lastPlayed?.let { lastPlayedLabel(context, it) } ?: stringResource(R.string.retro_info_never)
    val shape = RoundedCornerShape(24.dp)
    // A slow float, drawn in the layer so it never recomposes or re-lays-out the banner.
    val bob by rememberInfiniteTransition(label = "bannerBob").animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bannerBobY",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .graphicsLayer { translationY = bob.dp.toPx() }
            .glassCard(shape)
            .clickable { onOpen() },
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Deck.primary.copy(alpha = 0.35f), Color.Transparent))))
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
        ) {
            Text("CONTINUE PLAYING", color = Deck.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            Text(
                game.title,
                color = Deck.onBackground,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Deck.primary)
                        .clickable { onPlay() }
                        .padding(horizontal = 22.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Deck.onPrimary)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.retro_opt_play), color = Deck.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    (system?.name ?: "") + " · " + whenPlayed,
                    color = Deck.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The portrait game page, as the PC library's: the picture as a banner fading into the page with a back button on it, then the console,
 * the title, pills, stats, Play / Details / Options, and the description.
 */
@Composable
private fun RetroPortraitGamePage(
    game: RetroGame,
    system: RetroSystem,
    emulatorName: String?,
    meta: RetroMeta?,
    lastPlayed: Long?,
    root: String?,
    version: Int,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val hero = rememberHero(game, version)
    val wide = rememberWideArt(game, version)
    val cover = rememberCover(system, game, root, version)
    val art = remember(hero, wide, cover) { (hero ?: wide ?: cover)?.asImageBitmap() }
    val whenPlayed = lastPlayed?.let { lastPlayedLabel(context, it) } ?: stringResource(R.string.retro_info_never)

    // Controller-first: A starts the game the moment the page opens.
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(game.path) { runCatching { playFocus.requestFocus() } }

    val hue = remember(game.title) { hueOf(game.title) * 360f }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // Fade only the bottom edge into the page.
                    drawRect(
                        brush = Brush.verticalGradient(0.7f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.25f)))),
        ) {
            if (art != null) {
                Image(bitmap = art, contentDescription = game.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            val backSource = remember { MutableInteractionSource() }
            val backFocused by backSource.collectIsFocusedAsState()
            val backShape = RoundedCornerShape(16.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .size(52.dp)
                    .clip(backShape)
                    .background(Color(0x99000000))
                    .border(if (backFocused) 2.dp else 1.dp, if (backFocused) Deck.primary else Color(0x55FFFFFF), backShape)
                    .clickable(interactionSource = backSource, indication = null, onClick = onBack),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.retro_back), tint = Color.White)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Deck.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    system.name.uppercase(),
                    color = Deck.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                game.title,
                color = Deck.onBackground,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                emulatorName?.let { PortraitPill(it, Icons.Filled.SportsEsports) }
                listOf(meta?.genre, meta?.developer, meta?.year, meta?.players)
                    .filter { !it.isNullOrBlank() }
                    .forEach { PortraitPill(it.orEmpty(), Icons.Filled.GridView) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PortraitStat(stringResource(R.string.retro_portrait_last_played), whenPlayed, Modifier.weight(1f))
                PortraitStat(stringResource(R.string.retro_portrait_console), system.name, Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                PortraitPageButton(stringResource(R.string.retro_opt_play), Icons.Filled.PlayArrow, primary = true, onClick = onPlay, modifier = Modifier.weight(1f).focusRequester(playFocus))
                PortraitPageButton(stringResource(R.string.retro_portrait_details), Icons.Filled.Edit, primary = false, onClick = onDetails, modifier = Modifier.weight(1f))
                PortraitPageButton(stringResource(R.string.retro_hint_options), Icons.Filled.MoreVert, primary = false, onClick = onMenu, modifier = Modifier.weight(1f))
            }
            meta?.description?.takeIf { it.isNotBlank() }?.let { text ->
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Deck.surface)
                        .border(1.dp, Deck.line, shape)
                        .padding(16.dp),
                ) {
                    Text(stringResource(R.string.retro_portrait_about), color = Deck.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(text, color = Deck.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
