package com.winhub.hernandez.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.InputDevice
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.inputcontrols.ExternalController
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.LocalXmbHintInset
import com.winhub.hernandez.ui.deck.LocalDeckActions
import com.winhub.hernandez.ui.deck.XmbStoreMark
import com.winhub.hernandez.ui.deck.rememberScreenScale
import com.winhub.hernandez.ui.deck.storeMarkOf
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.theme.DangerRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Games-tab XMB layout (PS3 cross media bar). Games run along a horizontal bar and the focused
 * one sits enlarged at the "cross"; the column under it holds that game's options, with Play first.
 * Every size is derived from the content box, so portrait and landscape are the same code; a
 * rotation only re-measures. Touch: swipe the bar (with fling), tap a cover to focus it, tap the
 * focused cover to launch, swipe or tap the column. Controller / D-pad: Left/Right games, Up/Down
 * options, A select, B back to Play, L1/R1 jump 5. Up on Play is left unhandled, so focus can
 * climb to the top bar. [centeredHome] with [steamHome] turns it into the Deck Home's Steam look (see there).
 */
@Composable
internal fun SteamXmbView(
    shortcuts: List<Shortcut>,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    onToggleSelect: (Shortcut) -> Unit,
    onExitSelection: () -> Unit,
    onPlay: (Shortcut) -> Unit,
    actionsFor: (Shortcut) -> List<XmbAction>,
    storeBadges: @Composable (Shortcut) -> Unit,
    sdBadge: @Composable (Shortcut) -> Unit,
    /** The game list changed (rename, clone, remove, new exe…): reload it. */
    onReloadGames: () -> Unit = {},
    /** True while a nested menu is open (the Games tab hides its + button). */
    onNestedChange: (Boolean) -> Unit = {},
    /** The game under the cursor changed (the Deck home tints its backdrop and aims its game menu with it). */
    onFocusedChange: (Shortcut) -> Unit = {},
    /**
     * Drawn inside the Deck shell: the shell keeps Android's bars as it set them, and L1/R1 at the top
     * level fall through to it (they move its tabs, as on every Deck page) instead of jumping five games.
     */
    inDeckShell: Boolean = false,
    /** The top-left controller hints (root and nested menus). Off when the host has its own. */
    showHints: Boolean = true,
    /** The top-right "n / N" counter and clock. Off when the host draws its own floating status there. */
    showClock: Boolean = true,
    /**
     * The Deck Home: the games rest low with no options showing (see [steamHome] for the look). A (or a tap on the focused cover) shows
     * the options; B, Up on Play or moving to another game hides them again.
     */
    centeredHome: Boolean = false,
    /**
     * [centeredHome] only: the Steam Deck library look (XmbSteamHome.kt). The focused game's
     * own art fills the backdrop, one row of covers sits low with the first game as a wide card, and [homeTabs] are the plain text tabs
     * under its title, the peek of the host's tabs page: Down from the covers runs the action of tab [homeTabSel] (the last one used, the
     * first until there is one), and a tap on a tab runs its own, which open the page there. The row holds the
     * whole [shortcuts] list (recent first) and has no All Games card, so it applies only while [allShortcuts] is null.
     */
    steamHome: Boolean = false,
    homeTabs: List<XmbAction> = emptyList(),
    /** The counts the peek shows after its tabs ([homeTabs] are the tabs of [XMB_PAGE_TABS], in its order; a missing or zero one shows nothing). */
    homeBadges: Map<XmbSteamTab, Int> = emptyMap(),
    /** The Downloads tab's progress ring on the peek (null: none). */
    homeDownloadRing: XmbDownloadRing? = null,
    /** The index in [homeTabs] of the tab the host's tabs page last showed (highlighted under the games), or -1 when it has not been used yet. */
    homeTabSel: Int = -1,
    /** Asked before the view takes D-pad focus (entry, resume): false leaves focus where it is. */
    takeFocus: () -> Boolean = { true },
    /** Changes when the host wants the view to take focus again (the Deck shell, when a page should take its first focus). */
    focusTick: Int = 0,
    /**
     * Every game the host has. Given, the row ends with an All Games card that opens the All Games page over this view, and
     * [shortcuts] is the part of the library the row shows (the host limits it to the most recent games). With no games the
     * card is the whole row.
     */
    allShortcuts: List<Shortcut>? = null,
    /**
     * [steamHome] in the Deck shell only: what Start (and a tap on the shell's "Start Add Game" hint) runs while the covers hold the
     * cursor. Null = Start is left alone and no hint shows (the host passes null while its tabs page is up, which has its own).
     */
    onAddGame: (() -> Unit)? = null,
) {
    if (shortcuts.isEmpty() && allShortcuts == null) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val n = shortcuts.size
    // The All Games card is the slot after the last game (slot n), so a game's slot is its index in `shortcuts` and the row's
    // index maths (target, pos, the swipes, the counters) runs on slots; slot 0 is the first game.
    val hasAll = allShortcuts != null
    val slots = n + if (hasAll) 1 else 0
    val allTotal = allShortcuts?.size ?: 0
    val allCount = remember(allTotal) { context.resources.getQuantityString(R.plurals.xmb_all_games_count, allTotal, allTotal) }
    val allLabel = stringResource(R.string.xmb_all_games_open)
    var allOpen by rememberSaveable { mutableStateOf(false) }
    // What the All Games card shows of the library (the mosaic on the card, the collage behind it while it is hovered): the
    // games the row shows come first (the most recent), then the rest, games with art ahead of those without. Built from the
    // covers already decoded for the row, and kept until the game list changes.
    val libraryOrder = remember(shortcuts, allShortcuts) { if (hasAll) xmbRecentOrder(shortcuts, allShortcuts.orEmpty()) { it.file.path } else emptyList() }
    val mosaic = remember(libraryOrder) { xmbMosaicPick(libraryOrder) { it.icon != null } }
    val collageArts = remember(libraryOrder) { xmbCollagePick(libraryOrder) { it.icon != null }.mapNotNull { it.icon } }

    // The focused game is remembered by path so it survives rotation, leaving the tab, and a
    // refresh or sort that reorders the list. The All Games card is remembered as XMB_ALL_PATH.
    var focusedPath by rememberSaveable { mutableStateOf<String?>(null) }
    fun slotOfPath(path: String?): Int = xmbSlotOf(shortcuts.map { it.file.path }, path, hasAll)
    // Opens on the first game (slot 0) unless the cursor was somewhere else.
    var target by remember { mutableIntStateOf(slotOfPath(focusedPath).takeIf { it >= 0 } ?: 0) }
    val pos = remember { mutableFloatStateOf(target.toFloat()) } // visual position, fractional mid-swipe
    var act by remember { mutableIntStateOf(0) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Centred home: whether the focused game's options are showing. They belong to one game, so moving closes them.
    var optsOpen by remember { mutableStateOf(false) }
    // Steam home: the card the options opened on. The row glides to its options layout around this card, and goes back from it even
    // when the cursor has moved on meanwhile (which is what closes the options), so it only changes when they open.
    var liftIdx by remember { mutableIntStateOf(target) }
    val centered = centeredHome && !optsOpen
    val steam = steamHome && centeredHome && allShortcuts == null

    LaunchedEffect(shortcuts, hasAll) {
        val i = slotOfPath(focusedPath)
        val idx = if (i >= 0) i else target.coerceIn(0, slots - 1)
        settleJob?.cancel()
        target = idx
        pos.floatValue = idx.toFloat()
        // The lifted card is the focused one while the options are open; the list changing order under them (a launch, a new game)
        // moves it, and the lift is an index, so it follows or the options would hang on another game's card.
        if (optsOpen) liftIdx = idx
    }
    // The game under the cursor, or null on the All Games card (the slot after the last game), which has no game behind it.
    val focused: Shortcut? = if (hasAll && target >= n) null else shortcuts[target.coerceIn(0, n - 1)]
    val onAll = focused == null
    val focusKey = focused?.file?.path ?: XMB_ALL_PATH
    LaunchedEffect(focusKey) {
        focusedPath = focusKey
        if (focused != null) onFocusedChange(focused)
    }

    // Moving to another game always lands back on Play, so A launches (like tapping a card).
    fun settleTo(i: Int) {
        val t = i.coerceIn(0, slots - 1)
        if (t != target) { target = t; act = 0; optsOpen = false }
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(pos.floatValue, t.toFloat(), animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)) { v, _ ->
                pos.floatValue = v
            }
        }
    }

    val playtime = remember(focused) { focused?.let { steamXmbPlaytime(context, it.name) } }
    val isSelected = focused != null && focused.file.path in selectedPaths
    // The All Games card offers just "Open".
    val actions = if (focused == null) listOf(XmbAction(allLabel, Icons.Filled.GridView, allCount) { allOpen = true }) else buildList<XmbAction> {
        add(
            if (selectionMode) XmbAction(if (isSelected) "Deselect" else "Select", Icons.Filled.Checklist) { onToggleSelect(focused) }
            else XmbAction("Play", Icons.Filled.PlayArrow, playtime?.let { "$it played" } ?: "Not played yet") { onPlay(focused) }
        )
        addAll(actionsFor(focused))
    }
    val actIdx = act.coerceIn(0, actions.lastIndex)

    // Nested XMB menus (Settings, Game Details, tools…): model in XmbModel.kt, drawing in XmbMenuHost.kt.
    val nav = remember { XmbNavState() }
    val nested = nav.depth > 0
    val reload by rememberUpdatedState(onReloadGames)
    val appCtx = context
    val coScope = scope
    val xmbScope = remember(nav) {
        object : XmbScope {
            override val context: Context get() = appCtx
            override val scope: CoroutineScope get() = coScope
            override fun push(menu: XmbMenu) = nav.push(menu)
            override fun pop() = nav.pop()
            override fun popToRoot() = nav.popTo(0)
            override fun refresh() = nav.bump()
            override fun toast(message: String) { Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show() }
            override fun saved() { nav.savedAt = System.currentTimeMillis() }
            override fun confirm(confirm: XmbConfirm, onOk: () -> Unit) = nav.push(xmbConfirmMenu(nav, confirm, onOk))
            override fun reloadGames() = reload()
        }
    }
    fun runAction(a: XmbAction) { val mk = a.menu; if (mk != null) nav.push(mk(xmbScope)) else a.onClick() }
    // The All Games page counts as nested for the host: the Games tab's floating + button would sit on the page's last row.
    LaunchedEffect(nested, allOpen) { onNestedChange(nested || allOpen) }
    DisposableEffect(Unit) { onDispose { onNestedChange(false) } }
    BackHandler(enabled = nested) { nav.pop() }

    // Steam home: the text tabs under the title are the host's tabs page peeking. The one it last showed is highlighted (none until it
    // has been used) and is the one Down opens it on; the page itself, and the keys while it is up, are the host's.
    val tabIdx = homeTabSel.coerceIn(0, (homeTabs.size - 1).coerceAtLeast(0))
    val tabsActive = steam && homeTabs.isNotEmpty() && homeTabSel >= 0 && !nested && !optsOpen
    // When the options were last opened or closed (uptime ms): a tap right after is a double-tap bounce, not a new request.
    var toggledAt by remember { mutableLongStateOf(0L) }
    fun closeOpts() { optsOpen = false; act = 0; toggledAt = SystemClock.uptimeMillis() }
    fun openOpts() { liftIdx = target; optsOpen = true; act = 0; toggledAt = SystemClock.uptimeMillis() }
    fun tapOpts(open: Boolean) {
        if (xmbTapGateOpen(toggledAt, SystemClock.uptimeMillis())) { if (open) openOpts() else closeOpts() }
    }
    BackHandler(enabled = centeredHome && optsOpen && !nested) { closeOpts() }
    // The All Games page is open: Back closes it onto its card. Registered after the others, so it is asked first (the page's own
    // BackHandler, which closes its sort menu before the page, is registered later still and takes the press before this one).
    BackHandler(enabled = allOpen) { allOpen = false }
    val deckActions = LocalDeckActions.current
    // Steam home: Start adds a game and the shell shows its "Start Add Game" hint, while the covers (not the options, nor a nested menu)
    // hold the cursor. The options' own column is the cursor's place then, and the hint would only be noise beside it.
    val addNow by rememberUpdatedState(onAddGame)
    val addHere = steam && inDeckShell && onAddGame != null && !optsOpen && !nested && !allOpen
    DisposableEffect(addHere, deckActions) {
        if (!addHere) return@DisposableEffect onDispose {}
        val run: () -> Unit = { addNow?.invoke() }
        deckActions.xmbAddGameHint.value = run
        onDispose { if (deckActions.xmbAddGameHint.value === run) deckActions.xmbAddGameHint.value = null }
    }
    // 1 = centred (no options), 0 = the options layout.
    val cT by animateFloatAsState(if (centered) 1f else 0f, tween(300), label = "xmbCentred")
    // True once the options have glided in most of the way: until then their rows take no touches. Derived, so the glide does not
    // recompose this view every frame.
    val optsLive by remember { derivedStateOf { cT < 0.5f } }

    // Take D-pad focus on entry and again whenever the app comes back (e.g. after a game exits).
    val focusRequester = remember { FocusRequester() }
    nav.refocus = { runCatching { focusRequester.requestFocus() } }
    val hostMayTakeFocus by rememberUpdatedState(takeFocus)
    // The All Games page holds the keys while it is up (it takes focus back itself when the app returns).
    val pageUp by rememberUpdatedState(allOpen)
    val mayTakeFocus = { hostMayTakeFocus() && !pageUp }
    LaunchedEffect(focusTick) { if (mayTakeFocus()) runCatching { focusRequester.requestFocus() } }
    // Closing the page hands the keys back to the row, with its card still under the cursor.
    var pageWasUp by remember { mutableStateOf(allOpen) }
    LaunchedEffect(allOpen) {
        if (pageWasUp && !allOpen) runCatching { focusRequester.requestFocus() }
        pageWasUp = allOpen
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && mayTakeFocus()) runCatching { focusRequester.requestFocus() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Landscape XMB is a couch front end: the Android status bar and nav buttons are hidden (an edge
    // swipe brings them back briefly). Shown again on rotating to portrait, switching view, or leaving the tab.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    DisposableEffect(landscape, lifecycleOwner, inDeckShell) {
        if (inDeckShell) return@DisposableEffect onDispose {}
        val window = context.findActivity()?.window
        val bars = if (landscape && window != null) WindowCompat.getInsetsController(window, view) else null
        val hideNav = {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        }
        hideNav()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hideNav() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Space under a see-through top bar: the backdrop fills it, the bar/cover layout starts below it.
    val topInset = LocalTopBarOverlayInset.current

    // The clock ticks, and the pad is watched, only while something shows them: a host with its own status line and no
    // hints would otherwise recompose this view every 15 s for nothing.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (showClock) LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000L) } }
    val clock = remember(now / 60_000L) {
        DateUtils.formatDateTime(
            context, now,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_ALL,
        ) + "  " + DateUtils.formatDateTime(context, now, DateUtils.FORMAT_SHOW_TIME)
    }
    val hasPad = showHints && rememberGamepadConnected()

    // Steam home: the room the host's floating tab bar takes at the top, which the lifted card keeps clear of, scaled with the screen
    // as that bar is.
    val ss = rememberScreenScale()
    val clipTop = ss.dp(XMB_PEEK_CLIP).value
    // Steam home: the room the shell's Back / Search hints take at the bottom, which the tabs sit above.
    val hintInset = LocalXmbHintInset.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val m = remember(maxWidth, maxHeight, topInset) { XmbMetrics(maxWidth.value, maxHeight.value - topInset.value) }
        // The Steam home's option rows are at least 48 dp tall, a touch target; the other looks keep their own row height.
        val rowH = if (steam) m.rowH.coerceAtLeast(XMB_STEAM_MIN_ROW) else m.rowH
        val sl = remember(m, clipTop, hintInset, ss, rowH) {
            XmbSteamLayout(m.w, m.h, clipTop, max(hintInset.value, 16f * ss.factor), ss.factor, rowH, m.extra)
        }
        // Where the options column and the info pane beside it start: under the focused card of the Steam home, which has glided to the
        // left margin by then (the column is left aligned with it), else at the usual place.
        val colX = if (steam) sl.edge else m.colX
        val colTop = if (steam) sl.colTop else m.colTop
        val nVis = if (steam) sl.nVis else m.nVis
        val labelW = if (steam && m.port) min(220f, m.w - colX - m.iconBox - 28f).coerceAtLeast(120f) else m.labelW
        val infoX = if (!steam) m.infoX else if (m.port) 16f else colX + m.iconBox + 12f + labelW + 12f
        val infoY = if (!steam) m.infoY else if (m.port) colTop + nVis * rowH + m.extra + 10f else colTop
        val infoW = if (!steam) m.infoW else if (m.port) m.w - 32f else m.w - infoX - 80f
        // The Steam home keeps its info pane above the bottom hints (B Back / Y Search at the bottom right): at least 64 dp clear there.
        val infoH = if (!steam) m.infoH else if (m.port) m.h - infoY - 84f else m.h - infoY - max(64f, sl.bottomClear)
        // The Classic XMB's description takes this many lines (the Steam home's panel fits its blocks to its room, see XmbSteamInfo).
        val infoLines = if (m.port) 6 else 3
        // The collage behind the hovered All Games card: a 5 x 3 grid of covers (repeated when the library is small), drawn once
        // off the main thread.
        val collageSpec = xmbCollageSpec(maxWidth.value / maxHeight.value.coerceAtLeast(1f), collageArts.size)
        val collageState = remember(collageArts, collageSpec) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(collageState, onAll) {
            if (onAll && collageSpec != null && collageState.value == null) {
                collageState.value = withContext(Dispatchers.Default) { xmbCollageBitmap(collageArts, collageSpec) }
            }
        }
        // One card's step on screen: the Steam home's covers are drawn at their own sizes.
        val pitchPx = with(density) { (if (steam) sl.fW + sl.gap else m.pitch).dp.toPx() }
        val rowPx = with(density) { rowH.dp.toPx() }

        var swipe by remember { mutableFloatStateOf(0f) }
        val swipeBackPx = with(density) { 60.dp.toPx() }
        val hDrag = rememberDraggableState { delta ->
            if (nav.depth > 0) { swipe += delta; return@rememberDraggableState }
            settleJob?.cancel()
            // One cover's step on screen, so the row follows the finger.
            val p = (pos.floatValue - delta / pitchPx).coerceIn(-0.45f, slots - 1 + 0.45f)
            pos.floatValue = p
            val r = p.roundToInt().coerceIn(0, slots - 1)
            if (r != target) { target = r; act = 0; optsOpen = false }
        }
        val swipePx = with(density) { XMB_SWIPE_DP.dp.toPx() }
        var vAcc by remember { mutableFloatStateOf(0f) }
        // The options column only scrolls with its own vertical drag, whichever way it goes: a swipe that starts on it never closes them.
        val vDrag = rememberDraggableState { d ->
            if (nav.depth > 0 || centered) return@rememberDraggableState
            vAcc += d
            val step = rowPx * 0.9f
            while (vAcc <= -step) { vAcc += step; act = (act + 1).coerceAtMost(actions.lastIndex) }
            while (vAcc >= step) { vAcc -= step; act = (act - 1).coerceAtLeast(0) }
        }

        // Centred home, touch: a short swipe UP from the games opens the tabs page (the page rises from the bottom as the finger goes
        // up); a short swipe DOWN with the options open goes back to the games (the covers glide back). A downward swipe on the games
        // does nothing. The options' own column scrolls with its vertical drag (vDrag), which takes a swipe that starts on it first, so
        // only a swipe that starts anywhere else (the covers, the backdrop, the details panel) gets here.
        var swipeAcc by remember { mutableFloatStateOf(0f) }
        var swipeDone by remember { mutableStateOf(false) }
        val swipeDrag = rememberDraggableState { d ->
            if (swipeDone || !centeredHome || nav.depth > 0) return@rememberDraggableState
            swipeAcc += d
            when (xmbHomeSwipeAction(xmbVSwipe(swipeAcc, swipePx), optsOpen, steam && homeTabs.isNotEmpty() && !selectionMode)) {
                XmbSwipeAction.CLOSE_OPTIONS -> { swipeDone = true; closeOpts() }
                XmbSwipeAction.OPEN_TABS -> { swipeDone = true; runAction(homeTabs[tabIdx]) }
                XmbSwipeAction.NONE -> {}
            }
        }
        // What the root's tap detector reads fresh each tap (it is keyed on nothing, so it must not capture these by value): where the
        // options column's top edge is on the screen (px; the column sits under the see-through top bar's inset) and whether the options
        // are showing over the games.
        val optsTopPx = with(density) { (topInset.value + colTop).dp.toPx() }
        val optsTopNow by rememberUpdatedState(optsTopPx)
        val optsOpenNow by rememberUpdatedState(steam && optsOpen && nav.depth == 0)

        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (nav.depth > 0) {
                        // Typing into a Text row: the field gets the keys; A = done, B = cancel.
                        if (nav.editingKey != null) {
                            return@onPreviewKeyEvent when (e.key) {
                                Key.ButtonA -> { nav.commitEdit(xmbScope); true }
                                Key.ButtonB -> { nav.pop(); true }
                                else -> false
                            }
                        }
                        val k = when (e.key) {
                            Key.DirectionUp -> XmbKey.Up
                            Key.DirectionDown -> XmbKey.Down
                            Key.DirectionLeft -> XmbKey.Left
                            Key.DirectionRight -> XmbKey.Right
                            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> XmbKey.A
                            Key.ButtonB, Key.Escape -> XmbKey.B
                            Key.ButtonL1 -> XmbKey.L1
                            Key.ButtonR1 -> XmbKey.R1
                            Key.ButtonX -> XmbKey.X
                            else -> null
                        } ?: return@onPreviewKeyEvent false
                        return@onPreviewKeyEvent nav.onKey(k, xmbScope)
                    }
                    // Steam home, the covers holding the cursor: Start adds a game (the shell's hint says so).
                    if (e.key == Key.ButtonStart && addHere) {
                        if (e.nativeKeyEvent.repeatCount == 0) addNow?.invoke()
                        return@onPreviewKeyEvent true
                    }
                    // Centred home, no options showing: A shows them, Up has nothing above the games (the Steam home swallows it), Down
                    // opens the Steam home's tabs page on its last used tab (a held Down opens it once: the repeats are swallowed, or the
                    // page would take them as its own).
                    if (centered) {
                        return@onPreviewKeyEvent when (e.key) {
                            Key.DirectionLeft -> { settleTo(target - 1); true }
                            Key.DirectionRight -> { settleTo(target + 1); true }
                            Key.ButtonL1 -> if (inDeckShell) false else { settleTo(target - 5); true }
                            Key.ButtonR1 -> if (inDeckShell) false else { settleTo(target + 5); true }
                            Key.DirectionDown -> {
                                if (steam && homeTabs.isNotEmpty() && !selectionMode && e.nativeKeyEvent.repeatCount == 0) runAction(homeTabs[tabIdx])
                                true
                            }
                            Key.DirectionUp -> steam
                            // The All Games card has no options to show: A opens its page.
                            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { if (onAll) allOpen = true else openOpts(); true }
                            Key.ButtonB -> if (selectionMode) { onExitSelection(); true } else false
                            else -> false
                        }
                    }
                    when (e.key) {
                        Key.DirectionLeft -> { settleTo(target - 1); true }
                        Key.DirectionRight -> { settleTo(target + 1); true }
                        Key.ButtonL1 -> if (inDeckShell) false else { settleTo(target - 5); true }
                        Key.ButtonR1 -> if (inDeckShell) false else { settleTo(target + 5); true }
                        Key.DirectionDown -> { act = (actIdx + 1).coerceAtMost(actions.lastIndex); true }
                        Key.DirectionUp -> when {
                            actIdx > 0 -> { act = actIdx - 1; true }
                            centeredHome -> { closeOpts(); true }
                            else -> false
                        }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { runAction(actions[actIdx]); true }
                        Key.ButtonB -> when {
                            centeredHome -> { closeOpts(); true }
                            actIdx > 0 -> { act = 0; true }
                            selectionMode -> { onExitSelection(); true }
                            else -> false
                        }
                        else -> false
                    }
                }
                .draggable(
                    hDrag, Orientation.Horizontal,
                    onDragStarted = { swipe = 0f },
                    onDragStopped = { velocity ->
                        if (nav.depth > 0) { if (swipe > swipeBackPx) nav.pop() }
                        else settleTo((pos.floatValue - velocity / pitchPx * 0.14f).roundToInt())
                    },
                )
                .draggable(swipeDrag, Orientation.Vertical, onDragStarted = { swipeAcc = 0f; swipeDone = false })
                // Options showing: a tap that nothing took (a control consumes its own) and that landed higher than the options column's top
                // edge closes them; one at the column's height or lower (beside it, the details panel, the space under it) does nothing.
                // Covers are controls, so a tap on one is handled by onCard (another cover focuses its game, the lifted card closes).
                // Read, never consumed: a tap that closes the options goes on to the host like any other.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val up = waitForUpOrCancellation()
                        if (up != null && optsOpenNow && xmbTapClosesOptions(down.position.y, optsTopNow)) tapOpts(false)
                    }
                },
        ) {
            // Background: black, the focused cover blurred at full brightness, one flat veil for the text. Nothing else (no accent
            // glow, no scrims, no preset colour), so the home looks the same under every preset. The All Games card has no art, so on
            // it only the black shows.
            // On the All Games card, the collage of the games inside it (a plain Bitmap, so it is blurred, dimmed and crossfaded
            // exactly like a cover). It is built the first time the card is hovered, then kept until the games change.
            val collage = collageState.value
            val picture: Any? = focused?.icon ?: (if (onAll) collage?.let { XmbCollagePicture(it) } else null)
            // The Steam home shows the focused game's own landscape art over that (the blurred cover stays under it until it loads).
            // Its bottom gradient starts XMB_STEAM_FADE_LEAD above the text tabs' top edge (the tabs sit in the content box, below the
            // top bar's inset), so it is already building behind the title; it is drawn under the content box, so the text and covers stay clear of it.
            // [cT] (1 centred, 0 with the options open, glided over 300 ms) blurs and dims the art while the options are up.
            if (steam) XmbSteamBackdrop(
                focused, picture,
                (topInset.value + sl.tabsTop - XMB_STEAM_FADE_LEAD * ss.factor) / screenHeight.value.coerceAtLeast(1f),
                lift = { 1f - cT },
            )
            else XmbHomeBackdrop(picture)
            // Faint shade behind a see-through top bar so its title and buttons stay readable.
            if (topInset > 0.dp) {
                Box(Modifier.fillMaxWidth().height(topInset + 24.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
            }

            // The focus target starts BELOW the see-through top bar: D-pad focus search skips a target that
            // overlaps the bar, so Down from the top-bar buttons could never come back to the XMB.
            Box(Modifier.fillMaxSize().padding(top = topInset).focusRequester(focusRequester).focusable()) {
                // Top line: controller hints (only with a pad attached) · position · clock. Larger in
                // landscape, where the game row leaves the space above it free.
                val hs = m.headerScale
                if (showHints) Row(
                    Modifier.align(Alignment.TopStart).padding(start = 16.dp * hs, top = 8.dp * hs),
                    horizontalArrangement = Arrangement.spacedBy(12.dp * hs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasPad && nested) {
                        val hi = nav.hint(xmbScope)
                        XmbButtonHint("A", Color(0xFF3DDC84), hi.a, hs)
                        XmbButtonHint("B", Color(0xFFFF5252), "Back", hs)
                        if (!m.port && m.w >= 600f) {
                            XmbKeyHint("▲ ▼", "Move", hs)
                            if (hi.change) XmbKeyHint("◀ ▶", "Change", hs)
                        }
                        if (hi.sections) XmbKeyHint("L1 R1", "Section", hs)
                        if (hi.reset) XmbButtonHint("X", Color(0xFF5B9DFF), "Reset", hs)
                    } else if (hasPad) {
                        XmbButtonHint("A", Color(0xFF3DDC84), if (onAll) allLabel else if (centered) "Options" else actions[actIdx].label, hs)
                        XmbButtonHint("B", Color(0xFFFF5252), "Back", hs)
                        if (!m.port && m.w >= 600f) {
                            XmbKeyHint("◀ ▶", "Games", hs); XmbKeyHint("▲ ▼", "Options", hs); XmbKeyHint("L1 R1", if (inDeckShell) "Tabs" else "Jump", hs)
                        }
                    }
                }
                if (showClock) Row(
                    Modifier.align(Alignment.TopEnd).padding(end = 16.dp * hs, top = 8.dp * hs),
                    horizontalArrangement = Arrangement.spacedBy(12.dp * hs),
                ) {
                    Text(if (onAll) "All / $allTotal" else "${target + 1} / $n", color = Color(0xFFAAAAAA), fontSize = 12.sp * hs)
                    Text(clock, color = Color(0xFFDDDDDD), fontSize = 12.sp * hs, fontWeight = FontWeight.Light)
                }

                val rootAlpha by animateFloatAsState(if (nested) 0f else 1f, tween(260), label = "xmbRootAlpha")
                // Everything that belongs to the options layout fades out while centred. A lambda, read in the graphics layers, so
                // the fade doesn't recompose this view every frame.
                val optsA = { 1f - cT }
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = rootAlpha
                    translationY = (1f - rootAlpha) * -12.dp.toPx()
                }) {
                if (steam) {
                    // The Steam look: one row of covers, the focused game's title and the text tabs (XmbSteamHome.kt). Its options are
                    // the column below, which sits under the lifted focused card.
                    XmbSteamHome(
                        shortcuts = shortcuts, pos = pos, target = target, liftIdx = liftIdx, sl = sl, accent = accent, ss = ss,
                        focused = focused, sdBadge = sdBadge,
                        lift = { 1f - cT }, tabs = homeTabs, tabSel = tabIdx, tabsActive = tabsActive, tabsEnabled = centered, badges = homeBadges, downloadRing = homeDownloadRing,
                        onCard = { i ->
                            if (nav.depth == 0) {
                                val s = shortcuts[i]
                                // A tap on another card focuses it (and, with the options open, closes them: the cursor moved). A tap on the
                                // focused one opens its options, or closes them when they are open (the lifted card); a bounce right after
                                // the options opened or closed is dropped.
                                when {
                                    i != target -> settleTo(i)
                                    selectionMode -> onToggleSelect(s)
                                    optsOpen -> tapOpts(false)
                                    else -> tapOpts(true)
                                }
                            }
                        },
                        // A tap opens the tabs page on that tab (its action sets the tab, so the peek highlights it).
                        onTab = { i -> if (nav.depth == 0) runAction(homeTabs[i]) },
                    )
                }
                if (!steam) {
                // The bar. Only covers near the cross are composed; each one's placement is read from
                // `pos` in its graphics layer, so a swipe re-draws without recomposing.
                val baseIdx by remember { derivedStateOf { floor(pos.floatValue).toInt() } }
                val ahead = ceil((m.w - m.cross) / m.pitch).toInt() + 1
                Box(Modifier.fillMaxSize()) {
                for (i in (baseIdx - 3).coerceAtLeast(0)..(baseIdx + ahead + 1).coerceAtMost(slots - 1)) {
                    if (i >= n) {
                        key(XMB_ALL_PATH) {
                            XmbAllGamesCover(
                                index = i, pos = pos, m = m, accent = accent, count = allCount, mosaic = mosaic,
                                focused = i == target,
                                // Like a game, a tap first brings it to the cross; on the focused one it opens the page.
                                onClick = { if (nav.depth == 0) { if (i != target) settleTo(i) else allOpen = true } },
                            )
                        }
                        continue
                    }
                    val s = shortcuts[i]
                    key(s.file.path) {
                        XmbCover(
                            shortcut = s, index = i, pos = pos, m = m, accent = accent,
                            focused = i == target, selectionMode = selectionMode, selected = s.file.path in selectedPaths,
                            onClick = {
                                if (nav.depth == 0) when {
                                    i != target -> settleTo(i)
                                    selectionMode -> onToggleSelect(s)
                                    centered -> { optsOpen = true; act = 0 }
                                    else -> onPlay(s)
                                }
                            },
                        )
                    }
                }
                }

                // Focused game's name + "container · resolution", right of its cover under the rest of the bar.
                // On the All Games card: its name and the number of games.
                AnimatedContent(
                    targetState = focused,
                    contentKey = { it?.file?.path ?: XMB_ALL_PATH },
                    transitionSpec = { xmbTitleTransition() },
                    label = "xmbTitle",
                    modifier = Modifier.offset(m.titleX.dp, m.titleY.dp).width(m.titleW.coerceAtLeast(80f).dp),
                ) { s ->
                    if (s == null) {
                        Column {
                            Text(
                                stringResource(R.string.xmb_all_games), color = Color.White, fontSize = m.titleSize.sp,
                                lineHeight = (m.titleSize * 1.15f).sp, maxLines = m.titleLines, overflow = TextOverflow.Ellipsis,
                            )
                            Text(allCount, color = Color(0xFFC4C4C4), fontSize = if (centeredHome) ss.sp(12f) else 12.sp, maxLines = 1)
                        }
                    } else {
                        // An Android game has no container/resolution line; its package name stands in.
                        val meta = remember(s) {
                            buildLaunchSpec(s, context).meta
                        }
                        Column {
                            Text(
                                s.name, color = Color.White, fontSize = m.titleSize.sp, lineHeight = (m.titleSize * 1.15f).sp,
                                maxLines = m.titleLines, overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (meta.isNotEmpty()) {
                                    Text(
                                        meta, color = Color(0xFFC4C4C4), fontSize = if (centeredHome) ss.sp(12f) else 12.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                sdBadge(s)
                            }
                        }
                    }
                }
                }

                // The cross: the chosen option always sits in the first slot under the bar. Options
                // you pass slide up behind it and fade; the next few stay visible below.
                Box(
                    Modifier
                        .offset(colX.dp, colTop.dp)
                        .width((m.iconBox + 12f + labelW).dp)
                        .height((nVis * rowH + m.extra).dp)
                        .graphicsLayer { alpha = optsA() }
                        // Only while the options show, and only then at all: a pointer modifier that is merely disabled still takes part in
                        // hit testing, so this (invisible) column, which sits over the lower part of the first card while the games rest,
                        // would swallow every tap that lands on it (the wide first card then opened its options from only part of its face).
                        .then(
                            if (!centered && !nested) Modifier.draggable(vDrag, Orientation.Vertical, onDragStarted = { vAcc = 0f })
                            else Modifier
                        ),
                ) {
                    actions.forEachIndexed { i, a ->
                        key(a.label) {
                            val d = i - actIdx
                            val vis = d in 0 until nVis
                            val y by animateFloatAsState(d * rowH + if (d > 0) m.extra else 0f, spring(dampingRatio = 0.9f, stiffness = 500f), label = "xmbRowY")
                            // The Steam home shows every row that fits at full strength (the unselected ones are dimmer by their text colour); the rest fade with distance.
                            val a2 by animateFloatAsState(if (!vis) 0f else if (d == 0 || steam) 1f else max(0.35f, 1f - d * 0.13f), tween(220), label = "xmbRowA")
                            val src = remember { MutableInteractionSource() }
                            XmbActionRow(
                                action = a, selected = d == 0, accent = accent, iconBox = m.iconBox,
                                // The Steam home's rows are touch targets the height of their step, over the column's whole width.
                                hitH = if (steam) rowH else 0f,
                                modifier = Modifier
                                    .graphicsLayer { translationY = y.dp.toPx(); alpha = a2 }
                                    // Not while the options are still gliding in (a quick second tap on the card must not land on an invisible row).
                                    .then(if (vis && !nested && !centered && optsLive) Modifier.xmbClick(src) { act = i; runAction(a) } else Modifier),
                            )
                        }
                    }
                }

                // Nothing about a game to show on the All Games card.
                if (infoW >= 120f && infoH >= (if (steam) 24f else 40f) && focused != null) {
                    Column(
                        Modifier.offset(infoX.dp, infoY.dp).width(infoW.dp).height(infoH.dp).graphicsLayer { alpha = optsA() }.clipToBounds(),
                    ) {
                        // The Steam home's panel is compact and fits what it shows into infoH (nothing is ever cut mid-row).
                        if (steam) XmbSteamInfo(focused, playtime, infoW, infoH, if (m.port) 5 else 3, storeBadges)
                        else XmbInfo(focused, playtime, infoLines, storeBadges)
                    }
                }
                }
                if (nested) {
                    XmbNestedLayer(nav, xmbScope, m.w, m.h, m.port, accent, focused?.name.orEmpty(), focused?.icon, actions.map { it.icon }, actIdx)
                }
            }
        }
        // The All Games page covers the row; closing it lands back here with the All Games card still under the cursor.
        if (allOpen && allShortcuts != null) {
            XmbAllGamesPage(allShortcuts, onPlay = onPlay, onClose = { allOpen = false })
        }
    }
}

/** All XMB geometry in dp, from the content box. Portrait = taller than wide. */
private class XmbMetrics(val w: Float, val h: Float) {
    val port = h > w * 1.05f
    val fH = if (port) min(w * 0.52f, h * 0.29f) else (h * 0.36f).coerceIn(96f, 280f)
    val fW = fH * 2f / 3f                  // 2:3, same as the grid covers
    val sH = fH * 0.62f
    val sW = sH * 2f / 3f
    val gap = (fH * 0.09f).roundToInt().toFloat()
    val pitch = sW + gap
    val grow = fW - sW
    // Landscape: the row of games sits three quarters up the screen (its middle a quarter down),
    // never so low that fewer than three option rows fit under the focused cover. Portrait keeps it
    // at the top.
    val top = if (port) 34f else (h * 0.25f - sH / 2f).coerceAtMost(h - fH - 12f - 3 * 40f - 6f - 12f).coerceAtLeast(34f)
    val cross = if (port) 16f + sW * 0.42f + gap else max(16f + pitch, w * 0.13f)
    val iconBox = 34f
    val headerScale = if (port) 1f else 1.4f   // the top line; landscape has room above the row
    val rowH = if (port) 46f else 40f
    val extra = 6f
    val colX = cross + fW / 2f - iconBox / 2f
    val colTop = top + fH + 12f
    val labelW = if (port) min(220f, w - colX - iconBox - 28f) else 176f
    val nVis = if (port) ((h * 0.38f) / rowH).toInt().coerceIn(3, 8) else max(2, ((h - colTop - 12f - extra) / rowH).toInt())
    val infoX = if (port) 16f else colX + iconBox + 12f + labelW + 12f
    val infoY = if (port) colTop + nVis * rowH + extra + 10f else colTop
    val infoW = if (port) w - 32f else w - infoX - 80f   // 80 keeps clear of the + button
    val infoH = if (port) h - infoY - 84f else h - infoY - 12f
    val titleX = cross + fW + 16f
    val titleY = top + sH + 8f
    val titleW = w - titleX - 16f
    val titleH = fH - sH - 8f
    val titleLines = if (titleH > 58f) 2 else 1
    val titleSize = (if (titleLines == 2) (titleH - 18f) / 2.4f else titleH - 22f).coerceIn(15f, 26f)
}

@Composable
private fun XmbCover(
    shortcut: Shortcut,
    index: Int,
    pos: MutableFloatState,
    m: XmbMetrics,
    accent: Color,
    focused: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val src = remember { MutableInteractionSource() }
    val mark = remember(shortcut) { storeMarkOf(shortcut) }
    Box(
        Modifier
            .xmbCoverSlot(index, pos, m, focused)
            .then(if (focused && !selected) Modifier.shadow(14.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(Color(0xFF111111))
            .then(
                when {
                    selected -> Modifier.border(3.dp, DangerRed, shape)
                    focused -> Modifier.border(2.dp, accent, shape)
                    else -> Modifier
                }
            )
            .xmbClick(src, onClick),
    ) {
        val bmp = shortcut.icon
        if (bmp != null) {
            Image(
                bitmap = remember(bmp) { bmp.asImageBitmap() },
                contentDescription = shortcut.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            XmbMonogram(shortcut.name, accent)
        }
        // Bottom-end, so it clears the selection mark at the top-end; it scales with the cover.
        XmbStoreMark(mark, Modifier.align(Alignment.BottomEnd).padding(5.dp), (m.fW * 0.25f).coerceIn(22.5f, 32.5f).dp)
        if (selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) DangerRed else Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, if (selected) DangerRed else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// A card's place in a row (a game's or the All Games card's): its size, position and fade from its distance to the cross, read in
// the graphics layer so a swipe re-draws without recomposing.
private fun Modifier.xmbCoverSlot(
    index: Int, pos: MutableFloatState, m: XmbMetrics, focused: Boolean,
): Modifier = this
    .zIndex(if (focused) 1f else 0f)
    .size(m.fW.dp, m.fH.dp)
    .graphicsLayer {
        // u = distance from the chosen card in covers (the cross); the focused one grows to full size and the
        // ones after it are pushed right by the extra width, so spacing stays even mid-swipe.
        val u = index - pos.floatValue
        val t = (1f - abs(u)).coerceAtLeast(0f)
        val sc = (m.sH + (m.fH - m.sH) * t) / m.fH
        val x = m.cross + u * m.pitch + m.grow * u.coerceIn(0f, 1f)
        translationX = x.dp.toPx()
        translationY = m.top.dp.toPx()
        scaleX = sc
        scaleY = sc
        transformOrigin = TransformOrigin(0f, 0f)
        alpha = ((if (u >= 0f) 0.82f else 0.82f + u * 0.32f) + 0.18f * t).coerceIn(0f, 1f)
    }

// The last card of the row: a folder-like 3 x 2 preview of the library (the grid glyph when there are no games) on a plain
// surface, 2:3 like a cover and ringed like the focused one. Its name and game count are the
// title beside the card (like every card's), so only the semantics carry them here. It opens the All Games page and has no game
// behind it, so there is no store mark and no selection mark. One flat opaque fill and nothing nested: a see-through fill would
// let the focus shadow show through as a dark inner box.
@Composable
private fun XmbAllGamesCover(
    index: Int,
    pos: MutableFloatState,
    m: XmbMetrics,
    accent: Color,
    count: String,
    mosaic: XmbMosaic<Shortcut>,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val src = remember { MutableInteractionSource() }
    val description = stringResource(R.string.xmb_all_games) + ", " + count
    Box(
        Modifier
            .xmbCoverSlot(index, pos, m, focused)
            .then(if (focused) Modifier.shadow(14.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(XMB_PLAIN_CARD))
            .border(if (focused) 2.dp else 1.dp, if (focused) accent else Color.White.copy(alpha = 0.14f), shape)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .xmbClick(src, onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (mosaic.covers.isEmpty()) {
            // No games yet: the grid glyph.
            Icon(Icons.Filled.GridView, contentDescription = null, tint = Color.White, modifier = Modifier.size((m.fW * 0.44f).dp))
        } else {
            // Like a folder: a 3 x 2 preview of the games filling the card. Everything is sized in dp inside the card, so it
            // scales with the card (the card itself is scaled by its graphics layer).
            XmbAllGamesMosaic(mosaic, accent, Modifier.fillMaxSize().padding(6.dp))
        }
    }
}

// The All Games card's folder-like preview: up to five covers and a "+N more" tile, in a grid of XMB_MOSAIC_ROWS rows x
// XMB_MOSAIC_COLS columns that fills the space it is given (the cells share it equally and crop the covers to fit, about square on
// the 2:3 card). Cells with no game stay faintly empty.
@Composable
private fun XmbAllGamesMosaic(mosaic: XmbMosaic<Shortcut>, accent: Color, modifier: Modifier) {
    val gap = 3.dp
    val tileShape = RoundedCornerShape(3.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in 0 until XMB_MOSAIC_ROWS) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (col in 0 until XMB_MOSAIC_COLS) {
                    val i = row * XMB_MOSAIC_COLS + col
                    val tile = Modifier.weight(1f).fillMaxHeight().clip(tileShape)
                    when {
                        i < mosaic.covers.size -> XmbMosaicCover(mosaic.covers[i], accent, tile)
                        i == mosaic.covers.size && mosaic.more > 0 -> Box(tile.background(Color(0xFF2E3138)), contentAlignment = Alignment.Center) {
                            // In a small cell "+N more" wraps at the space: "+N" over "more".
                            Text(
                                stringResource(R.string.xmb_all_games_more, mosaic.more), color = Color.White.copy(alpha = 0.92f),
                                fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2,
                            )
                        }
                        else -> Box(tile.background(Color.White.copy(alpha = 0.06f)))
                    }
                }
            }
        }
    }
}

// One game's tile in the mosaic: its cover (the bitmap the row's cover shows), or its initials on the monogram's gradient.
@Composable
private fun XmbMosaicCover(s: Shortcut, accent: Color, modifier: Modifier) {
    val bmp = s.icon
    if (bmp != null) {
        Image(
            bitmap = remember(bmp) { bmp.asImageBitmap() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF0A0A0A)))), contentAlignment = Alignment.Center) {
            Text(xmbInitials(s.name), color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

// Cover fallback: initials on an accent gradient (no art yet, or a custom game).
@Composable
internal fun XmbMonogram(name: String, accent: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF0A0A0A))))
            .padding(10.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(xmbInitials(name), color = Color.White.copy(alpha = 0.92f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(name, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * One row of the options column. With a [hitH] above zero the row is a box that tall and as wide as its column (the touch target; the
 * icon and the text stay at its top, where they sit without it) and [modifier] goes on that box.
 */
@Composable
private fun XmbActionRow(action: XmbAction, selected: Boolean, accent: Color, iconBox: Float, modifier: Modifier, hitH: Float = 0f) {
    if (hitH > 0f) {
        Box(modifier.fillMaxWidth().height(hitH.dp)) { XmbActionRowContent(action, selected, accent, iconBox, Modifier) }
    } else {
        XmbActionRowContent(action, selected, accent, iconBox, modifier)
    }
}

@Composable
private fun XmbActionRowContent(action: XmbAction, selected: Boolean, accent: Color, iconBox: Float, modifier: Modifier) {
    val ring = if (action.danger) DangerRed else accent
    val boxShape = RoundedCornerShape(10.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(iconBox.dp)
                .clip(boxShape)
                .background(if (selected) ring.copy(alpha = 0.25f) else Color.Transparent)
                .then(if (selected) Modifier.border(1.5.dp, ring, boxShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                action.icon, contentDescription = null, modifier = Modifier.size(19.dp),
                tint = when {
                    selected -> Color.White
                    action.danger -> Color(0xFFE8A197)
                    else -> Color(0xFFCFCFCF)
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (action.menu != null) "${action.label}  ›" else action.label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.58f),
                fontSize = if (selected) 16.sp else 13.5.sp,
                fontWeight = if (selected) FontWeight.Normal else FontWeight.Light,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = action.subtitle
            if (selected && sub != null) {
                Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Steam home: the room (dp, on a 412 dp phone) the host's floating tab bar takes at the top; the lifted card keeps clear of it. */
internal const val XMB_PEEK_CLIP = 50f

/** The surface of the All Games card. */
internal val XMB_PLAIN_CARD = listOf(Color(0xFF2A2D33), Color(0xFF16181C))

/** Centred home: how far (dp) a vertical swipe has to travel to open the tabs page (up) or go back from the options (down). Short, so a flick is enough. */
private const val XMB_SWIPE_DP = 28f

/** How a focused card's title swaps for the next one (the Classic row's and the Steam home's): the new one slides up in a fade, the old fades out quickly. */
internal fun xmbTitleTransition(): ContentTransform =
    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(90))

/** The home backdrop's blur: a medium one, so the art stays recognisable. */
private val XMB_BACKDROP_BLUR = 16.dp

/** The one flat black veil over the home backdrop's art, just enough for the text on top to stay readable. */
private const val XMB_BACKDROP_VEIL = 0.30f

/** Below API 31 the backdrop art is shrunk to 1/this of its size and scaled back up, which looks blurred. */
internal const val XMB_BACKDROP_SOFT_DIV = 16

/** The collage behind the hovered All Games card is blurred only very slightly (a tenth of [XMB_BACKDROP_BLUR]). */
private val XMB_COLLAGE_BLUR = 1.6.dp

/** Below API 31 the collage is not shrunk at all (it is already a small bitmap, scaled up with filtering), so it stays only slightly soft. */
internal const val XMB_COLLAGE_SOFT_DIV = 1

/** The size [xmbBackdropSoftBitmap] shrinks a [w] x [h] picture to (by [div]); never below 1 px. */
internal fun xmbBackdropSoftSize(w: Int, h: Int, div: Int = XMB_BACKDROP_SOFT_DIV): Pair<Int, Int> =
    max(1, w / div.coerceAtLeast(1)) to max(1, h / div.coerceAtLeast(1))

/** What the backdrop shows on the All Games card: the [bitmap] collage of its games (it gets its own, lighter blur). */
private data class XmbCollagePicture(val bitmap: Bitmap)

/**
 * The home backdrop, one implementation for the games row and the All Games page: black, then [picture] blurred at full
 * brightness and crossfaded when it changes, then one flat veil for the text. [picture] is a game's cover (a [Bitmap]),
 * the All Games collage, or null for the black base alone. Fills its parent.
 */
@Composable
internal fun XmbHomeBackdrop(picture: Any?) {
    // Modifier.blur does nothing below API 31, where the art is drawn pre-shrunk and scaled up instead.
    val nativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        Crossfade(targetState = picture, animationSpec = tween(600), label = "xmbBackdrop") { pic ->
            when (pic) {
                is Bitmap -> XmbBackdropBitmap(pic, nativeBlur, XMB_BACKDROP_BLUR, XMB_BACKDROP_SOFT_DIV)
                // The collage is only slightly blurred (each crossfade layer has its own blur).
                is XmbCollagePicture -> XmbBackdropBitmap(pic.bitmap, nativeBlur, XMB_COLLAGE_BLUR, XMB_COLLAGE_SOFT_DIV)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = XMB_BACKDROP_VEIL)))
    }
}

/**
 * A backdrop picture: [bmp] filling the screen, blurred by [radius] (Modifier.blur, API 31+) or, below that, shrunk by [softDiv]
 * and scaled back up (see [xmbBackdropSoftBitmap]).
 */
@Composable
private fun XmbBackdropBitmap(bmp: Bitmap, nativeBlur: Boolean, radius: Dp, softDiv: Int) {
    val art = remember(bmp, nativeBlur, softDiv) { if (nativeBlur) bmp else xmbBackdropSoftBitmap(bmp, softDiv) }
    if (art != null) Image(
        bitmap = remember(art) { art.asImageBitmap() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = if (nativeBlur) FilterQuality.Low else FilterQuality.Medium,
        modifier = Modifier.fillMaxSize().blur(radius),
    )
}

/**
 * A tiny copy of [src] for the backdrop below API 31, where Modifier.blur does nothing: drawn scaled up with filtering it
 * looks blurred. Halved step by step, as one big filtered jump skips pixels and aliases. Null if the bitmap can't be scaled
 * (the backdrop then shows only its black base). With a [div] of 1 the picture is used as it is.
 */
private fun xmbBackdropSoftBitmap(src: Bitmap, div: Int = XMB_BACKDROP_SOFT_DIV): Bitmap? = runCatching {
    if (div <= 1) return@runCatching src
    val (w, h) = xmbBackdropSoftSize(src.width, src.height, div)
    var cur = src
    while (cur.width / 2 > w && cur.height / 2 > h) cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
    Bitmap.createScaledBitmap(cur, w, h, true)
}.getOrNull()

/**
 * The backdrop behind the hovered All Games card: [arts] (decoded covers, most recent first) tiled row by row into the grid of
 * [spec], each centre-cropped to its cell, as one small bitmap. The backdrop then treats it as any other picture (the same veil
 * and crossfade, but a much lighter blur: XMB_COLLAGE_BLUR, XMB_COLLAGE_SOFT_DIV), so it costs one draw like a cover. Cells with no cover stay black; a
 * cover that can't be drawn is skipped. Null if the bitmap can't be made.
 */
private fun xmbCollageBitmap(arts: List<Bitmap>, spec: XmbCollageSpec): Bitmap? = runCatching {
    val out = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    for (i in 0 until min(spec.cols * spec.rows, arts.size)) {
        val art = arts[i]
        val cell = xmbCellRect(i, spec.cols, spec.rows, spec.width, spec.height)
        val crop = xmbCenterCrop(art.width, art.height, cell.width, cell.height)
        runCatching {
            canvas.drawBitmap(art, Rect(crop.left, crop.top, crop.right, crop.bottom), Rect(cell.left, cell.top, cell.right, cell.bottom), paint)
        }
    }
    out
}.getOrNull()

// Right / lower pane: store badges, the same spec chips as the list card, playtime and Game Details.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XmbInfo(s: Shortcut, playtime: String?, descLines: Int, storeBadges: @Composable (Shortcut) -> Unit) {
    val context = LocalContext.current
    val spec = remember(s) { buildLaunchSpec(s, context) }
    val details = remember(s) { buildLaunchDetails(s) }
    storeBadges(s)
    SpecChipRows(
        rendererLabel = spec.rendererLabel,
        dxvkVersion = spec.dxvkVersion,
        frameGenLabel = spec.frameGenLabel,
        driverLabel = spec.driverLabel,
        vkd3dVersion = spec.vkd3dVersion,
        backendLabel = spec.backendLabel,
        eosEnabled = spec.eosEnabled,
    )
    val factColor = Color(0xFFD0D0D0)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(playtime ?: "Not played yet", color = factColor, fontSize = 12.sp)
        }
        if (details.genres.isNotEmpty()) Text(details.genres.take(3).joinToString(" · "), color = factColor, fontSize = 12.sp)
        details.releaseYear?.takeIf { it.isNotBlank() }?.let { Text(it, color = factColor, fontSize = 12.sp) }
        details.metacritic?.let { score ->
            Text(
                "$score", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(metacriticColor(score)).padding(horizontal = 5.dp),
            )
        }
    }
    details.description?.takeIf { it.isNotBlank() }?.let {
        Text(
            it, color = Color(0xFFABABAB), fontSize = 12.sp, lineHeight = 17.sp,
            maxLines = descLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun XmbButtonHint(glyph: String, color: Color, label: String, s: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(17.dp * s).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.Black, fontSize = 10.sp * s, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(4.dp * s))
        Text(label, color = Color(0xFFB0B0B0), fontSize = 11.sp * s, maxLines = 1)
    }
}

@Composable
private fun XmbKeyHint(keys: String, label: String, s: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            keys, color = Color(0xFFDDDDDD), fontSize = 10.sp * s,
            modifier = Modifier.border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp * s)).padding(horizontal = 5.dp * s),
        )
        Spacer(Modifier.width(4.dp * s))
        Text(label, color = Color(0xFFB0B0B0), fontSize = 11.sp * s, maxLines = 1)
    }
}

// Controller hints only make sense with a pad attached; tracks hot-plug.
@Composable
internal fun rememberGamepadConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val check = {
            connected = InputDevice.getDeviceIds().any { ExternalController.isGameController(InputDevice.getDevice(it)) }
        }
        check()
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = check()
            override fun onInputDeviceRemoved(deviceId: Int) = check()
            override fun onInputDeviceChanged(deviceId: Int) = check()
        }
        im?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose { im?.unregisterInputDeviceListener(listener) }
    }
    return connected
}

// "Xh Ym" from the same prefs XServerDisplayActivity writes; null = never played.
private fun steamXmbPlaytime(context: Context, name: String): String? {
    val ms = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE).getLong("${name}_playtime", 0L)
    if (ms <= 0L) return null
    val mins = ms / 60_000L
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

internal fun xmbInitials(name: String): String {
    val words = name.replace(Regex("[^A-Za-z0-9 ]"), " ").split(' ').filter { it.isNotBlank() }
    val first = words.firstOrNull() ?: return name.trim().take(1).ifEmpty { "?" }
    if (first.length <= 4 && first == first.uppercase() && first.any { it.isLetter() }) return first
    return words.filterNot { it.lowercase() in setOf("the", "of", "and", "a") }
        .take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { first.take(1).uppercase() }
}

internal fun metacriticColor(score: Int): Color = when {
    score >= 75 -> Color(0xFF66CC33)
    score >= 50 -> Color(0xFFFFCC33)
    else -> Color(0xFFFF4444)
}
