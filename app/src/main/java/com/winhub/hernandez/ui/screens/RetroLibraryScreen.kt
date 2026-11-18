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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.LocalXmbHintInset
import com.winhub.hernandez.ui.deck.DeckGlyph
import com.winhub.hernandez.ui.deck.GlyphKind
import com.winhub.hernandez.ui.deck.ScreenScale
import com.winhub.hernandez.ui.deck.XMB_FOCUS_OUTLINE
import com.winhub.hernandez.ui.deck.rememberScreenScale
import com.winhub.hernandez.ui.deck.rememberXmbLogo
import com.winhub.hernandez.ui.deck.lastPlayedLabel
import com.winhub.hernandez.ui.deck.xmbInkColor
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max

// The retro library, laid out like the PC Home: every game of the consoles picked in Settings in one row of covers (the one under the
// cursor larger) over the game's own background, its title and console under it, and the consoles as the filters in the strip below:
// "All", then one per console. Up / Down (L2 / R2) change filter. Covers and backgrounds come from SteamGridDB as the games are looked
// at. A game starts in its console's installed emulator (see RetroLibrary). Select on a pad, or the chip at the top left, switches back
// to the PC library.

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
    /** The filter last on: a console id, "" for All. */
    @Volatile var lastShelf: String = ""
    val lastGame = HashMap<String, String>()
}

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

/** [focus] is the Home's own requester: the shell hands the keys to it when the pad comes down from the top bar. */
@Composable
internal fun RetroLibraryScreen(onSwitchToPc: () -> Unit, focus: FocusRequester) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
    // The options are the PC Home's: A lifts the card to the left and opens a column of options under it (an XMB dropdown). [actIdx] is
    // the chosen row, [subActions] a nested column (the emulators, the removal confirm) and [parentIdx] the row it was opened from.
    var optsOpen by remember { mutableStateOf(false) }
    var liftIdx by remember { mutableIntStateOf(0) }
    var actIdx by remember { mutableIntStateOf(0) }
    var parentIdx by remember { mutableIntStateOf(0) }
    var subActions by remember { mutableStateOf<List<XmbAction>?>(null) }
    val liftAnim = remember { Animatable(0f) }
    LaunchedEffect(optsOpen) { liftAnim.animateTo(if (optsOpen) 1f else 0f, tween(300)) }
    val lift = { liftAnim.value }
    var consolesDialog by remember { mutableStateOf(false) }
    var detailsFor by remember { mutableStateOf<RetroGame?>(null) }
    var metaTick by remember { mutableIntStateOf(0) }
    var enabled by remember { mutableStateOf(RetroLibrary.enabledSystems(context)) }
    var coverProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Bumped when a picture has been saved (so the cards look again for theirs), when the emulator choice changed and when the
    // pictures are asked for again.
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
    val played = remember(scan) { RetroLibrary.lastPlayed(context) }
    // What the user edited about the games (name, description, metadata); read again when they save.
    val metaMap = remember(scan, metaTick) { RetroLibrary.allMeta(context) }
    // The consoles picked in Settings that have games, oldest first; the library's filters.
    val consoles = remember(scan, enabled) {
        if (scan == null) emptyList() else catalog.systems
            .filter { it.id in enabled && scan.bySystem[it.id].orEmpty().isNotEmpty() }
            .sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { it.name }))
    }
    // All the games of those consoles, the ones played last first.
    val allGames = remember(scan, enabled, played, metaMap) {
        if (scan == null) emptyList() else scan.bySystem
            .filterKeys { it in enabled }.values.flatten()
            .map { g -> metaMap[g.path]?.name?.takeIf { it.isNotBlank() }?.let { g.copy(title = it) } ?: g }
            .sortedWith(compareByDescending<RetroGame> { played[it.path] ?: 0L }.thenBy { it.title.lowercase() })
    }

    // The filter is the shell's sub-tab (L2 / R2 step it): 0 is All, then one per console. It is put back as it was when this screen
    // goes, so the PC shelf is where it was.
    DisposableEffect(deckTabs) {
        val saved = deckTabs.index
        onDispose {
            deckTabs.bind(max(deckTabs.count, saved + 1))
            deckTabs.select(saved, quiet = true)
        }
    }
    var restored by remember { mutableStateOf(false) }
    val shelfKey = consoles.joinToString(",") { it.id }
    LaunchedEffect(shelfKey, allGames.isEmpty()) {
        if (allGames.isEmpty()) {
            deckTabs.bind(0)
        } else {
            deckTabs.bind(1 + consoles.size)
            val at = consoles.indexOfFirst { it.id == RetroSession.lastShelf } + 1
            deckTabs.select(at, quiet = true)
            restored = true
        }
    }
    val shelf = deckTabs.index.coerceIn(0, consoles.size)
    val filter = consoles.getOrNull(shelf - 1)
    LaunchedEffect(filter?.id, restored) { if (restored) RetroSession.lastShelf = filter?.id.orEmpty() }

    val games = remember(allGames, filter) { if (filter == null) allGames else allGames.filter { it.systemId == filter.id } }
    val shelfName = filter?.id.orEmpty()
    val start = remember(shelfName, games) { games.indexOfFirst { it.path == RetroSession.lastGame[shelfName] }.coerceAtLeast(0) }
    var target by remember(shelfName, games) { mutableIntStateOf(start) }
    val pos = remember(shelfName, games) { androidx.compose.runtime.mutableFloatStateOf(start.toFloat()) }
    LaunchedEffect(target, shelfName, games) {
        animate(pos.floatValue, target.toFloat(), animationSpec = tween(220)) { v, _ -> pos.floatValue = v }
        games.getOrNull(target)?.let { RetroSession.lastGame[shelfName] = it.path }
    }
    val focusedGame = games.getOrNull(target)
    val focusedSystem = focusedGame?.let { catalog.byId[it.systemId] }
    val emulatorName = remember(focusedSystem?.id, state, emuTick) { focusedSystem?.let { RetroLibrary.resolveEmulator(context, it)?.name } }

    // The pictures of the games on screen are fetched as they are looked at (from the cursor on first), so the library fills in by itself;
    // a game with nothing to find is remembered and not asked again.
    LaunchedEffect(shelfName, games, scrapeTick) {
        if (games.isEmpty()) return@LaunchedEffect
        val from = target.coerceIn(0, games.lastIndex)
        val ordered = games.drop(from) + games.take(from)
        withContext(Dispatchers.IO) {
            coverProgress = 0 to ordered.size
            RetroArt.download(
                context, ordered, root,
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

    val emulatorActions = { sys: RetroSystem ->
        val installed = RetroLibrary.emulatorsFor(context, sys).filter { RetroLibrary.isInstalled(context, it.packageName) }
        val chosen = RetroLibrary.emulatorChoice(context, sys.id)
        if (installed.isEmpty()) {
            listOf(XmbAction(context.getString(R.string.retro_emulator_none), Icons.Filled.SportsEsports) { subActions = null; actIdx = parentIdx })
        } else {
            listOf(
                XmbAction(context.getString(R.string.retro_emulator_auto), Icons.Filled.SportsEsports, subtitle = context.getString(R.string.retro_emulator_auto_sub)) {
                    RetroLibrary.setEmulatorChoice(context, sys.id, null); emuTick++; optsOpen = false; subActions = null
                },
            ) + installed.map { e ->
                XmbAction(
                    e.name, Icons.Filled.SportsEsports,
                    subtitle = if (e.packageName == chosen) context.getString(R.string.retro_emulator_in_use) else e.packageName,
                ) {
                    RetroLibrary.setEmulatorChoice(context, sys.id, e.packageName); emuTick++; optsOpen = false; subActions = null
                }
            }
        }
    }
    val removeGame = { sys: RetroSystem, game: RetroGame ->
        val current = ready
        if (current != null) {
            RetroLibrary.excludePath(context, game.path)
            val next = RetroScan(current.scan.bySystem.mapValues { (_, list) -> list.filter { it.path != game.path } }.filterValues { it.isNotEmpty() })
            // The cursor lands on the neighbour that takes its place.
            (games.getOrNull(target + 1) ?: games.getOrNull(target - 1))?.let { RetroSession.lastGame[sys.id] = it.path; RetroSession.lastGame[shelfName] = it.path }
            RetroSession.scan = next
            state = RetroState.Ready(current.path, next)
            Toast.makeText(context, R.string.retro_removed, Toast.LENGTH_SHORT).show()
        }
    }
    val openOpts = {
        if (focusedSystem != null && focusedGame != null) {
            liftIdx = target
            actIdx = 0
            subActions = null
            optsOpen = true
            DeckSound.play(DeckSound.SELECT)
        }
    }
    val closeOpts = { optsOpen = false; subActions = null }
    // Built only while the options are open: Play first, then the emulator to launch from, then the library's own.
    val mainActions: List<XmbAction> = if (!optsOpen || focusedSystem == null || focusedGame == null) emptyList() else {
        val items = ArrayList<XmbAction>()
        items += XmbAction(context.getString(R.string.retro_opt_play), Icons.Filled.PlayArrow) { closeOpts(); play(focusedSystem, focusedGame) }
        items += XmbAction(context.getString(R.string.retro_opt_details), Icons.Filled.Edit, subtitle = context.getString(R.string.retro_opt_details_sub)) {
            closeOpts(); detailsFor = focusedGame
        }
        items += XmbAction(context.getString(R.string.retro_opt_emulator), Icons.Filled.SportsEsports, subtitle = emulatorName ?: context.getString(R.string.retro_emulator_none_short)) {
            parentIdx = actIdx; subActions = emulatorActions(focusedSystem); actIdx = 0
        }
        items += XmbAction(context.getString(R.string.retro_opt_covers), Icons.Filled.PhotoLibrary, subtitle = context.getString(R.string.retro_opt_covers_sub)) {
            closeOpts()
            scope.launch(Dispatchers.IO) { RetroArt.clearMisses(context); scrapeTick++ }
        }
        items += XmbAction(context.getString(R.string.retro_opt_remove), Icons.Filled.Delete, subtitle = focusedGame.title, danger = true) {
            // Cancel is the first row, so a double press never removes by accident.
            parentIdx = actIdx
            subActions = listOf(
                XmbAction(context.getString(R.string.retro_remove_cancel), Icons.Filled.Close) { subActions = null; actIdx = parentIdx },
                XmbAction(
                    context.getString(R.string.retro_remove_yes), Icons.Filled.Delete,
                    subtitle = context.getString(R.string.retro_remove_yes_sub, focusedGame.title), danger = true,
                ) { closeOpts(); removeGame(focusedSystem, focusedGame) },
            )
            actIdx = 0
        }
        items += XmbAction(context.getString(R.string.retro_opt_consoles), Icons.Filled.GridView, subtitle = context.getString(R.string.retro_opt_consoles_sub)) {
            closeOpts(); consolesDialog = true
        }
        val hidden = RetroLibrary.excludedPaths(context).size
        if (hidden > 0) {
            items += XmbAction(context.getString(R.string.retro_opt_restore), Icons.Filled.Visibility, subtitle = context.getString(R.string.retro_opt_restore_sub, hidden)) {
                RetroLibrary.clearExcluded(context); closeOpts(); rescan()
            }
        }
        items += XmbAction(context.getString(R.string.retro_opt_folder), Icons.Filled.FolderOpen, subtitle = root ?: RetroLibrary.romRoot(context)) {
            closeOpts(); chooseFolder()
        }
        items += XmbAction(context.getString(R.string.retro_opt_rescan), Icons.Filled.Refresh) { closeOpts(); rescan() }
        items
    }
    val actions = subActions ?: mainActions
    // The options belong to one game: another console, another game list or a rescan closes them.
    LaunchedEffect(shelfName, games) { optsOpen = false; subActions = null }

    // The cursor along the row and down the column, with the Deck's move sound when it really moved.
    val stepGame = { delta: Int ->
        val next = (target + delta).coerceIn(0, games.lastIndex.coerceAtLeast(0))
        if (next != target) {
            target = next
            DeckSound.play(DeckSound.MOVE)
        }
    }
    val stepAction = { delta: Int ->
        val next = (actIdx + delta).coerceIn(0, actions.lastIndex.coerceAtLeast(0))
        if (next != actIdx) {
            actIdx = next
            DeckSound.play(DeckSound.MOVE)
        }
    }

    // B goes up one level: the emulator list, then the options, then back to the PC library.
    val goBack: () -> Unit = {
        if (subActions != null) {
            subActions = null
            actIdx = parentIdx
        } else if (optsOpen) {
            optsOpen = false
        } else {
            onSwitchToPc()
        }
    }
    val latestBack by rememberUpdatedState(goBack)
    val backLayer = LocalDeckBack.current
    DisposableEffect(backLayer) {
        backLayer.onBack = { latestBack(); true }
        onDispose { backLayer.onBack = null }
    }
    BackHandler { latestBack() }

    // The keys are the Home's: taken when the screen opens, when the games are there, when the dialog closes and when the app comes
    // back to the front (after an emulator or the folder picker).
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    LaunchedEffect(ready != null, consolesDialog, detailsFor) { if (!consolesDialog && detailsFor == null) { withFrameNanos { }; runCatching { focus.requestFocus() } } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) runCatching { focus.requestFocus() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val screenH = maxHeight.value
        val h = screenH - topInset.value
        val sl = remember(w, h, hintInset, ss) {
            XmbSteamLayout(w, h, ss.dp(XMB_PEEK_CLIP).value, max(hintInset.value, 16f * ss.factor), ss.factor, 48f, 6f)
        }
        val backdropCover = rememberCover(focusedSystem, focusedGame, root, coverVersion)
        val backdropHero = rememberHero(focusedGame, coverVersion)

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (optsOpen) {
                        return@onPreviewKeyEvent when (e.key) {
                            Key.DirectionUp -> { stepAction(-1); true }
                            Key.DirectionDown -> { stepAction(1); true }
                            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { DeckSound.play(DeckSound.SELECT); actions.getOrNull(actIdx)?.onClick?.invoke(); true }
                            Key.ButtonB, Key.Escape, Key.ButtonX -> { goBack(); true }
                            // The cursor moving on closes the options, as on the PC Home.
                            Key.DirectionLeft -> { closeOpts(); stepGame(-1); true }
                            Key.DirectionRight -> { closeOpts(); stepGame(1); true }
                            Key.ButtonSelect -> { onSwitchToPc(); true }
                            else -> false
                        }
                    }
                    when (e.key) {
                        Key.DirectionLeft -> { stepGame(-1); true }
                        Key.DirectionRight -> { stepGame(1); true }
                        Key.DirectionUp -> { deckTabs.step(-1); true }
                        Key.DirectionDown -> { deckTabs.step(1); true }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonX -> { openOpts(); true }
                        Key.ButtonY -> { if (ready != null) { DeckSound.play(DeckSound.SELECT); consolesDialog = true }; true }
                        Key.ButtonSelect -> { onSwitchToPc(); true }
                        Key.ButtonB, Key.Escape -> { goBack(); true }
                        else -> false
                    }
                }
                // Nothing under the screen may react to a touch that lands on it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // The PC Home's backdrop: the game's own background over its blurred cover, with the navy fade toward the strip.
            RetroBackdrop(backdropCover, backdropHero, (topInset.value + sl.tabsTop - XMB_STEAM_FADE_LEAD * ss.factor) / screenH.coerceAtLeast(1f))

            Box(Modifier.fillMaxSize().padding(top = topInset)) {
                when (val s = state) {
                    RetroState.Loading -> CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center))
                    RetroState.NoRoot -> RetroEmpty(
                        stringResource(R.string.retro_empty_no_folder_title), stringResource(R.string.retro_empty_no_folder_body),
                        stringResource(R.string.retro_choose_folder), Modifier.align(Alignment.Center), chooseFolder,
                    )
                    is RetroState.Missing -> RetroEmpty(
                        stringResource(R.string.retro_empty_missing_title), stringResource(R.string.retro_empty_missing_body, s.path),
                        stringResource(R.string.retro_choose_folder), Modifier.align(Alignment.Center), chooseFolder,
                    )
                    is RetroState.Ready -> if (enabled.isEmpty()) {
                        RetroEmpty(
                            stringResource(R.string.retro_empty_no_consoles_title), stringResource(R.string.retro_empty_no_consoles_body),
                            stringResource(R.string.retro_pick_title), Modifier.align(Alignment.Center), { consolesDialog = true },
                        )
                    } else if (allGames.isEmpty()) {
                        RetroEmpty(
                            stringResource(R.string.retro_empty_none_title), stringResource(R.string.retro_empty_none_body, s.path),
                            stringResource(R.string.retro_choose_folder), Modifier.align(Alignment.Center), chooseFolder,
                        )
                    } else {
                        RetroHomeRow(
                            games, catalog, root, coverVersion, emulatorName, target, pos, sl, accent, ss, liftIdx, lift,
                            onCard = { i ->
                                if (optsOpen) {
                                    // A tap on the lifted card closes the options; on another card it moves the cursor there.
                                    closeOpts()
                                    DeckSound.play(DeckSound.BACK)
                                    if (i != liftIdx) target = i
                                } else if (i == target) {
                                    openOpts()
                                } else {
                                    target = i
                                    DeckSound.play(DeckSound.MOVE)
                                }
                            },
                            onStep = { delta -> stepGame(delta) },
                        )
                        RetroFilterStrip(
                            listOf(stringResource(R.string.retro_filter_all)) + consoles.map { it.name },
                            listOf(0) + consoles.map { RetroIcons.of(context, it) },
                            shelf, sl, ss, accent,
                            Modifier.align(Alignment.TopCenter).offset(y = sl.tabsTop.dp).graphicsLayer { alpha = 1f - lift() },
                        ) { deckTabs.select(it) }
                        // The options: the column under the lifted card and the game's details beside it.
                        RetroOptions(actions, actIdx, sl, accent, lift, optsOpen, onStep = { delta -> stepAction(delta) }) { i ->
                            DeckSound.play(DeckSound.SELECT)
                            actIdx = i
                            actions.getOrNull(i)?.onClick?.invoke()
                        }
                        if (focusedGame != null) RetroInfo(focusedGame, focusedSystem, emulatorName, played[focusedGame.path], metaMap[focusedGame.path], sl, ss, lift)
                    }
                }
            }

            // Top left the switch; top right how far the pictures are.
            val top = topInset + (if (fullXmb) 38.dp else 8.dp)
            RetroSwitchChip(
                stringResource(R.string.retro_switch_to_pc).uppercase(), hasPad, onSwitchToPc,
                Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = top),
            )
            coverProgress?.let { (done, total) ->
                Text(
                    stringResource(R.string.retro_covers_progress, done, total), color = PAGE_MUTED, fontSize = ss.sp(14f),
                    maxLines = 1, modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = top + 12.dp),
                )
            }
        }
    }

    if (consolesDialog) RetroConsolesDialog { consolesDialog = false; enabled = RetroLibrary.enabledSystems(context) }
    detailsFor?.let { game ->
        RetroGameDetailsDialog(game, metaMap[game.path] ?: RetroMeta()) { saved ->
            if (saved != null) {
                RetroLibrary.setMeta(context, game.path, saved)
                metaTick++
            }
            detailsFor = null
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

// ── The backdrop and the row (the PC Home's: one row of covers over the game's background, the focused card larger, its title under it) ──

@Composable
private fun RetroBackdrop(cover: Bitmap?, hero: Bitmap?, fadeFrom: Float) {
    Box(Modifier.fillMaxSize()) {
        XmbHomeBackdrop(cover)
        Crossfade(targetState = hero, animationSpec = tween(600), label = "retroHero") { h ->
            if (h != null) {
                Image(
                    bitmap = remember(h) { h.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(xmbSteamBottomFade(fadeFrom.coerceIn(0.25f, 0.97f))))
    }
}

@Composable
private fun RetroHomeRow(
    games: List<RetroGame>,
    catalog: RetroCatalog,
    root: String?,
    coverVersion: Int,
    emulatorName: String?,
    target: Int,
    pos: MutableFloatState,
    sl: XmbSteamLayout,
    accent: Color,
    ss: ScreenScale,
    liftIdx: Int,
    lift: () -> Float,
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
                RetroCover(catalog.byId[games[i].systemId], games[i], i, i == target, root, coverVersion, pos, sl, row, accent, liftIdx, lift) { onCard(i) }
            }
        }
        val focused = games.getOrNull(target)
        RetroTitle(focused, focused?.let { catalog.byId[it.systemId] }, emulatorName, sl, row, target, pos, accent, ss, lift)
    }
}

@Composable
private fun RetroCover(
    system: RetroSystem?,
    game: RetroGame,
    index: Int,
    focused: Boolean,
    root: String?,
    coverVersion: Int,
    pos: MutableFloatState,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    accent: Color,
    liftIdx: Int,
    lift: () -> Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(XMB_STEAM_CORNER)
    val lifted = index == liftIdx
    val src = remember { MutableInteractionSource() }
    val cover = rememberCover(system, game, root, coverVersion)
    Box(
        Modifier
            .zIndex(if (focused || lifted) 1f else 0f)
            .size(sl.fW.dp, sl.fH.dp)
            .graphicsLayer {
                val p = pos.floatValue
                val l = lift()
                val sc0 = xmbSteamScale(index, p)
                // With the options open the lifted card goes to the left margin, larger, and the cards after it follow in a row to its right.
                val sc = if (lifted) sc0 + (XMB_STEAM_OPTS_SCALE - sc0) * l else sc0
                translationX = row.xOpts(index, p, liftIdx, XMB_STEAM_OPTS_SCALE, l).dp.toPx()
                translationY = (sl.cardTop - sl.lift * l).dp.toPx()
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
        // The cards that are not the lifted one are dimmed while the options are open (an opaque overlay, as on the PC Home).
        if (!lifted) Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f * lift() }.background(Color.Black))
    }
}

// Under the focused card, left aligned with it (it follows the card as the row glides): the game's name, bold, and below it the console and
// the emulator in tiny light capitals behind a small play arrow.
@Composable
private fun RetroTitle(
    game: RetroGame?,
    system: RetroSystem?,
    emulatorName: String?,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    target: Int,
    pos: MutableFloatState,
    accent: Color,
    ss: ScreenScale,
    lift: () -> Float,
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
                alpha = 1f - lift()
            },
    ) { g ->
        if (g != null) {
            val consoleName = system?.name.orEmpty()
            val meta = if (emulatorName != null && consoleName.isNotEmpty()) "$consoleName · $emulatorName" else consoleName
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

// The options column under the lifted card, as on the PC Home: the chosen row sits in the first slot, the next ones follow below and the
// rows you passed slide up and fade. [live] = the options are open (the rows take taps only then); [onPick] gets the tapped row.
@Composable
private fun RetroOptions(
    actions: List<XmbAction>,
    actIdx: Int,
    sl: XmbSteamLayout,
    accent: Color,
    lift: () -> Float,
    live: Boolean,
    onStep: (Int) -> Unit,
    onPick: (Int) -> Unit,
) {
    val rowH = 48f
    val extra = 6f
    val iconBox = 34f
    val labelW = 176f
    Box(
        Modifier
            .offset(sl.edge.dp, sl.colTop.dp)
            .width((iconBox + 12f + labelW).dp)
            .height((sl.nVis * rowH + extra).dp)
            .graphicsLayer { alpha = lift() }
            // A swipe up or down the column moves the chosen row, a row at a time.
            .pointerInput(live, actions.size) {
                if (!live) return@pointerInput
                var acc = 0f
                val stepPx = rowH.dp.toPx()
                detectVerticalDragGestures(onDragStart = { acc = 0f }) { _, dy ->
                    acc += dy
                    while (acc <= -stepPx) { onStep(1); acc += stepPx }
                    while (acc >= stepPx) { onStep(-1); acc -= stepPx }
                }
            },
    ) {
        actions.forEachIndexed { i, a ->
            key("$i:${a.label}") {
                val d = i - actIdx
                val vis = d in 0 until sl.nVis
                val y by animateFloatAsState(d * rowH + if (d > 0) extra else 0f, spring(dampingRatio = 0.9f, stiffness = 500f), label = "retroRowY")
                val rowAlpha by animateFloatAsState(if (vis) 1f else 0f, tween(220), label = "retroRowA")
                val src = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowH.dp)
                        .graphicsLayer { translationY = y.dp.toPx(); alpha = rowAlpha }
                        .then(if (vis && live) Modifier.xmbClick(src) { onPick(i) } else Modifier),
                ) { XmbActionRowContent(a, d == 0, accent, iconBox, Modifier) }
            }
        }
    }
}

// The game's details beside the options column.
@Composable
private fun RetroInfo(
    game: RetroGame,
    system: RetroSystem?,
    emulatorName: String?,
    lastPlayed: Long?,
    meta: RetroMeta?,
    sl: XmbSteamLayout,
    ss: ScreenScale,
    lift: () -> Float,
) {
    val context = LocalContext.current
    val x = sl.edge + 34f + 12f + 176f + 12f
    val width = sl.w - x - 80f
    if (width < 120f) return
    val played = lastPlayed?.let { lastPlayedLabel(context, it) } ?: stringResource(R.string.retro_info_never)
    Column(
        Modifier.offset(x.dp, sl.colTop.dp).width(width.dp).graphicsLayer { alpha = lift() },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(game.title, color = Color.White, fontSize = ss.sp(16f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val console = system?.name.orEmpty()
        Text(
            if (emulatorName != null) "$console · $emulatorName" else console,
            color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        meta?.line?.takeIf { it.isNotEmpty() }?.let {
            Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val description = meta?.description.orEmpty()
        if (description.isNotEmpty()) {
            Text(description, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        } else {
            Text(java.io.File(game.path).name, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.retro_info_played, played), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1)
    }
}

// The filters, in the place and shape of the PC Home's text tabs: "All" and the consoles, the current one and two either side, the current
// one in a thin white outline with its illustration. A tap picks one.
@Composable
private fun RetroFilterStrip(
    labels: List<String>,
    icons: List<Int>,
    selected: Int,
    sl: XmbSteamLayout,
    ss: ScreenScale,
    accent: Color,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    val first = (selected - 2).coerceAtMost(labels.size - 5).coerceAtLeast(0)
    val last = (first + 4).coerceAtMost(labels.lastIndex)
    Row(modifier.height(XMB_STEAM_TABS_H.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in first..last) {
            val on = i == selected
            val shape = RoundedCornerShape(14.dp)
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
                val icon = icons.getOrElse(i) { 0 }
                if (on && icon != 0) RetroIcon(icon, Modifier.size(22.dp))
                Text(
                    labels[i].uppercase(),
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

/** A console illustration: a vector drawable as it is, a PNG decoded off the main thread at a sensible size. */
@Composable
internal fun RetroIcon(@DrawableRes res: Int, modifier: Modifier) {
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
private fun RetroEmpty(title: String, body: String, action: String, modifier: Modifier, onAction: () -> Unit) {
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
                .xmbClick(src, onAction)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Text(action, color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A game's cover, decoded off the main thread; null while it loads and when the game has none. */
@Composable
private fun rememberCover(system: RetroSystem?, game: RetroGame?, root: String?, version: Int): Bitmap? {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, game?.path, version) {
        value = if (system == null || game == null) null else withContext(Dispatchers.IO) { RetroPictures.cover(context, system, game, root) }
    }
    return bmp
}

/** A game's background picture, decoded off the main thread; null while it loads and when the game has none. */
@Composable
private fun rememberHero(game: RetroGame?, version: Int): Bitmap? {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, game?.path, version) {
        value = if (game == null) null else withContext(Dispatchers.IO) { RetroPictures.hero(context, game) }
    }
    return bmp
}

private object RetroPictures {
    private const val CACHE_BYTES = 32 * 1024 * 1024
    private const val COVER_WIDTH = 360
    private const val HERO_WIDTH = 1280

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cover(context: Context, system: RetroSystem, game: RetroGame, root: String?): Bitmap? =
        RetroArt.find(context, system, game, root)?.let { decode(it, COVER_WIDTH) }

    fun hero(context: Context, game: RetroGame): Bitmap? = RetroArt.findHero(context, game)?.let { decode(it, HERO_WIDTH) }

    private fun decode(file: java.io.File, width: Int): Bitmap? {
        val key = "${file.path}:${file.lastModified()}"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bmp)
        return bmp
    }
}
