package com.winhub.hernandez.store

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.LocalXmbHintInset
import com.winhub.hernandez.ui.deck.DeckFloatingHint
import com.winhub.hernandez.ui.deck.GlyphKind
import com.winhub.hernandez.ui.deck.LocalXmbKeyboard
import com.winhub.hernandez.ui.deck.ScreenScale
import com.winhub.hernandez.ui.deck.XmbEditTapCatcher
import com.winhub.hernandez.ui.deck.XmbGlassBackdrop
import com.winhub.hernandez.ui.deck.XmbGroupItem
import com.winhub.hernandez.ui.deck.XmbHighlightHost
import com.winhub.hernandez.ui.deck.XmbHighlightItem
import com.winhub.hernandez.ui.deck.XmbInk
import com.winhub.hernandez.ui.deck.XmbKeyboardHost
import com.winhub.hernandez.ui.deck.XmbRailState
import com.winhub.hernandez.ui.deck.XmbScreenWindow
import com.winhub.hernandez.ui.deck.XmbTriggerGuard
import com.winhub.hernandez.ui.deck.XmbDarkScheme
import com.winhub.hernandez.ui.deck.XmbGlassTheme
import com.winhub.hernandez.ui.deck.deckFocusRingInk
import com.winhub.hernandez.ui.deck.rememberScreenScale
import com.winhub.hernandez.ui.deck.rememberXmbEdit
import com.winhub.hernandez.ui.deck.rememberXmbFocusGroup
import com.winhub.hernandez.ui.deck.rememberXmbHighlightItem
import com.winhub.hernandez.ui.deck.rememberXmbHintDensity
import com.winhub.hernandez.ui.deck.rememberXmbHome
import com.winhub.hernandez.ui.deck.xmbCardFill
import com.winhub.hernandez.ui.deck.xmbCardLine
import com.winhub.hernandez.ui.deck.xmbChipOverArt
import com.winhub.hernandez.ui.deck.xmbEditField
import com.winhub.hernandez.ui.deck.xmbEditStop
import com.winhub.hernandez.ui.deck.xmbFrame
import com.winhub.hernandez.ui.deck.xmbGlassCard
import com.winhub.hernandez.ui.deck.xmbHighlight
import com.winhub.hernandez.ui.deck.xmbHighlightHostOrNull
import com.winhub.hernandez.ui.deck.xmbPageFrost
import com.winhub.hernandez.ui.deck.xmbRailItem
import com.winhub.hernandez.ui.theme.LocalAccentDim
import kotlinx.coroutines.launch

// The store screens in the Deck interface's XMB-home mode: edge to edge in an even frame, a slim rail, a compact search
// field that types only on A, and portrait covers that pop when focused. Everything here is used only while
// [LocalStoreXmb] is true; the other interfaces keep the storefront exactly as it was.

/** True inside a store screen running in XMB-home mode (set by [StoreXmbScreen]). */
internal val LocalStoreXmb = staticCompositionLocalOf { false }

/**
 * Wraps a store screen. In XMB-home mode the window goes edge to edge with no system bars, the background runs to the
 * screen edges and the content sits in the even camera frame, clear of the keyboard; otherwise [content] is untouched.
 */
@Composable
internal fun StoreXmbScreen(content: @Composable () -> Unit) {
    val xmb = rememberXmbHome()
    XmbScreenWindow(xmb)
    XmbDarkScheme(xmb) {
    // XMB home: the XMB keyboard replaces Android's for every text field on the screen. The Back hint is inside it, so it can
    // step aside while the keyboard panel is up.
    val hosted: @Composable () -> Unit = { XmbKeyboardHost(xmb) { XmbBackHintLayer(xmb, content = content) } }
    // XMB home: the screen sits on the charcoal glass and the theme's own background is clear, so the glass shows through every
    // rail, tab row and page inside it, and their cards are glass over it. The highlight host wraps all of it: the one gliding
    // outline of the window draws over every page, rail and card (dialogs and sheets bring their own host).
    XmbGlassTheme(xmb) {
        CompositionLocalProvider(LocalStoreXmb provides xmb) {
            if (!xmb) {
                hosted()
            } else {
                XmbHighlightHost(enabled = true, modifier = Modifier.fillMaxSize()) {
                    XmbGlassBackdrop(Modifier.matchParentSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.ime)
                            .padding(horizontal = xmbFrame()),
                    ) { hosted() }
                }
            }
        }
    }
    }
}

/**
 * XMB home: the content of a store screen with a muted "B Back" hint floating at the bottom right, the look of the shell's
 * Search hint. A tap runs the same back action as the B button (the activity's back dispatcher: a section's own back handler
 * first, then the screen closes). It steps aside while the XMB keyboard panel is up, and it provides [LocalXmbHintInset] so
 * anything floating at the bottom can lift clear of it. The hint is drawn at Home's size and place (the Deck density, 16 dp
 * from the camera cut-out's end edge). Not [enabled]: [content] unchanged.
 */
@Composable
internal fun XmbBackHintLayer(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val ss = rememberScreenScale()
    val keyboard = LocalXmbKeyboard.current
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // The hint is drawn at the Deck style's density and at Home's place (the shell's hint row), so it looks the same on every
    // page. The inset the page lifts its floating buttons by is in this screen's own dp: convert it.
    val outer = LocalDensity.current
    val hintDensity = rememberXmbHintDensity()
    val k = hintDensity.density / outer.density
    val cutoutBottom = WindowInsets.displayCutout.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalXmbHintInset provides (ss.dp(12f) + XMB_BACK_HINT_HEIGHT) * k + cutoutBottom) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (keyboard?.panelOpen != true) {
                CompositionLocalProvider(LocalDensity provides hintDensity) {
                    val dir = LocalLayoutDirection.current
                    val cutout = WindowInsets.displayCutout.asPaddingValues()
                    DeckFloatingHint(
                        glyph = "B",
                        kind = GlyphKind.Y,
                        label = stringResource(R.string.xmb_store_back),
                        onClick = { dispatcher?.onBackPressed() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = ss.dp(16f) + cutout.calculateEndPadding(dir),
                                bottom = ss.dp(12f) + cutout.calculateBottomPadding(),
                            ),
                    )
                }
            }
        }
    }
}

/** The room the Back hint takes above its bottom margin (its glyph row plus the padding round it). */
private val XMB_BACK_HINT_HEIGHT = 34.dp

/**
 * The app theme around a store screen that keeps its own layout in every interface (the full library lists, the Steam
 * sign-in): [WinlatorTheme] plus the XMB keyboard, which is only in effect in XMB-home mode.
 */
@Composable
internal fun StoreKeyboardTheme(content: @Composable () -> Unit) {
    val xmb = rememberXmbHome()
    com.winhub.hernandez.ui.theme.WinlatorTheme {
        XmbDarkScheme(xmb) {
            XmbGlassTheme(xmb) {
                if (!xmb) {
                    XmbKeyboardHost(content = content)
                } else {
                    // These screens paint no backdrop of their own, and their `background` fills are clear on the glass.
                    XmbHighlightHost(enabled = true, modifier = Modifier.fillMaxSize()) {
                        XmbGlassBackdrop(Modifier.matchParentSize())
                        XmbKeyboardHost { XmbBackHintLayer(true, content = content) }
                    }
                }
            }
        }
    }
}

/**
 * The app theme around a store screen that is not a storefront (a game's details page): [WinlatorTheme] plus
 * [StoreXmbScreen], so in XMB-home mode the page is full screen like the rest of the store; elsewhere it is just the theme.
 */
@Composable
internal fun StoreXmbTheme(content: @Composable () -> Unit) {
    com.winhub.hernandez.ui.theme.WinlatorTheme { StoreXmbScreen(content) }
}

/**
 * L2 / R2 for the store screens in XMB home: they step through the rail's tabs from anywhere on the screen. Each store
 * activity feeds its key and motion events here; [StoreTabCycle] sets [onCycle] only while its screen is resumed in
 * XMB home, so in the other interfaces (and on a screen in the background) nothing is consumed.
 */
internal object StoreXmbInput {
    @Volatile
    var onCycle: ((Int) -> Unit)? = null

    private var l2Down = false
    private var r2Down = false
    private var lastPrev = 0L
    private var lastNext = 0L

    /** From an activity's dispatchKeyEvent: true when it took the key (L2 / R2, down and up alike). */
    fun onKey(ev: KeyEvent): Boolean {
        if (onCycle == null) return false
        val delta = when (ev.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> -1
            KeyEvent.KEYCODE_BUTTON_R2 -> 1
            else -> return false
        }
        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) fire(delta)
        return true
    }

    /**
     * From an activity's dispatchGenericMotionEvent. Pads that report the triggers only as axes (the built-in
     * Xbox-layout pad on handhelds) press past 0.6 and release below 0.3, as in the Deck shell. It never consumes
     * the event, so the sticks and the hat keep reaching the screen.
     */
    fun onMotion(ev: MotionEvent) {
        if (onCycle == null || ev.action != MotionEvent.ACTION_MOVE) return
        if ((ev.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return
        val l = maxOf(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE))
        val r = maxOf(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS))
        if (XmbTriggerGuard.holding(l, r)) return
        val lNow = if (l2Down) l > 0.3f else l > 0.6f
        val rNow = if (r2Down) r > 0.3f else r > 0.6f
        if (lNow && !l2Down) fire(-1)
        if (rNow && !r2Down) fire(1)
        l2Down = lNow
        r2Down = rNow
    }

    // A pad that sends both the button and the axis for one press steps only once.
    private fun fire(delta: Int) {
        val now = SystemClock.uptimeMillis()
        val last = if (delta < 0) lastPrev else lastNext
        if (now - last < STEP_DEBOUNCE_MS) return
        if (delta < 0) lastPrev = now else lastNext = now
        onCycle?.invoke(delta)
    }

    private const val STEP_DEBOUNCE_MS = 200L
}

/**
 * Hands L2 / R2 to this store screen while it is resumed and [enabled] (XMB home): [onCycle] gets -1 / +1 and switches
 * the tab the way tapping it does.
 */
@Composable
internal fun StoreTabCycle(enabled: Boolean, onCycle: (Int) -> Unit) {
    val current by rememberUpdatedState(onCycle)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose {}
        val handler: (Int) -> Unit = { current(it) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> StoreXmbInput.onCycle = handler
                Lifecycle.Event.ON_PAUSE -> if (StoreXmbInput.onCycle === handler) StoreXmbInput.onCycle = null
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (StoreXmbInput.onCycle === handler) StoreXmbInput.onCycle = null
        }
    }
}

/**
 * XMB home: the rail's end actions (refresh, downloads, the ⋮ overflow), in rail order, so L2 / R2 can step on past the
 * tabs onto them. Each action joins with [xmbRailStop]; the host provides one of these around its rail.
 */
@Stable
internal class XmbRailStops {
    val requesters = mutableStateListOf<FocusRequester>()
    var focused by mutableStateOf<FocusRequester?>(null)

    /** Index of the action holding focus, or -1. */
    fun focusedIndex(): Int = focused?.let { requesters.indexOf(it) } ?: -1
}

internal val LocalXmbRailStops = staticCompositionLocalOf<XmbRailStops?> { null }

/**
 * Makes this rail action an L2 / R2 stop when its host provides [LocalXmbRailStops] (XMB home); elsewhere it changes
 * nothing. [ring] adds the focus ring for an action that has none of its own.
 */
@Composable
internal fun Modifier.xmbRailStop(ring: Boolean = false): Modifier {
    val stops = LocalXmbRailStops.current ?: return this
    val requester = remember { FocusRequester() }
    DisposableEffect(stops, requester) {
        stops.requesters.add(requester)
        onDispose {
            stops.requesters.remove(requester)
            if (stops.focused === requester) stops.focused = null
        }
    }
    return this
        .then(if (ring) Modifier.steamFocusRing(CircleShape) else Modifier)
        .onFocusChanged {
            if (it.hasFocus) stops.focused = requester else if (stops.focused === requester) stops.focused = null
        }
        .focusRequester(requester)
}

/**
 * One L2 / R2 step through a store rail: the [tabCount] tabs, then its [stops]. Landing on a tab selects it (as tapping
 * it does, via [selectTab]); landing on an action gives it focus without running it, so A runs it. Wraps both ways.
 */
internal fun xmbRailCycle(delta: Int, tabCount: Int, selected: Int, stops: XmbRailStops, selectTab: (Int) -> Unit) {
    val total = tabCount + stops.requesters.size
    if (total == 0) return
    val onAction = stops.focusedIndex()
    val current = if (onAction >= 0) tabCount + onAction else selected
    val next = (current + delta + total) % total
    if (next < tabCount) selectTab(next) else runCatching { stops.requesters[next - tabCount].requestFocus() }
}

/**
 * L2 / R2 for a store host in XMB home ([enabled]): the triggers step through its [tabCount] tabs (from [selected]) and
 * then its rail's end actions, wrapping; the returned stops are for the host to provide around its rail. A tab is
 * chosen through [onSelect], then focus moves into [contentFocus], since whatever held it went away with the old tab.
 */
@Composable
internal fun rememberXmbRailCycle(
    enabled: Boolean,
    tabCount: Int,
    selected: Int,
    contentFocus: FocusRequester,
    onSelect: (Int) -> Unit,
): XmbRailStops {
    val scope = rememberCoroutineScope()
    val stops = remember { XmbRailStops() }
    StoreTabCycle(enabled) { delta ->
        xmbRailCycle(delta, tabCount, selected, stops) { index ->
            onSelect(index)
            scope.launch {
                repeat(2) { withFrameNanos { } }
                runCatching { contentFocus.requestFocus() }
            }
        }
    }
    return stops
}

/** The slim rail's width: room for the icons, and the current tab's short label under its icon. */
internal fun ScreenScale.xmbRailWidth(): Dp = w(0.055f, min = 52.dp, max = 76.dp)

/** Width of one cover in the store rails and grids. */
internal fun ScreenScale.xmbCoverWidth(): Dp = w(0.12f, min = 96.dp, max = 170.dp)

/** The catalog stores' library grid column: the XMB cover in XMB home, else their own 132 / 118 dp. */
@Composable
internal fun storeGridCell(wide: Boolean): Dp =
    if (LocalStoreXmb.current) rememberScreenScale().xmbCoverWidth() else if (wide) 132.dp else 118.dp

/** Steam's 600x900 library cover: the published one when the snapshot has it, else the CDN path. */
internal fun steamTallArt(appId: Int): String =
    SteamLibraryArt.libraryCapsule(appId)
        ?: "https://shared.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900.jpg"

/**
 * One rail entry, packed tight: just the icon, and on the tab you're on its one-line label (the count, if any, on a
 * second line) under it. The tabs sit edge to edge, each at least 40 dp tall so it stays easy to tap. Its selected look
 * (accent fill, accent icon and label) follows [selected] only, never focus: when the outline moves on into the page the
 * tab stays as it is. [rail] is the rail's [XmbRailState]: Up / Down onto the tab selects it, and Left back from the page
 * lands on the selected one (null: neither).
 */
@Composable
internal fun XmbRailItem(label: String, icon: ImageVector, selected: Boolean, count: Int?, onClick: () -> Unit, rail: XmbRailState? = null) {
    val s = rememberScreenScale()
    val shape = RoundedCornerShape(s.dp(10f))
    val hl = rememberXmbHighlightItem()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .xmbRailItem(rail, selected, onClick)
            .xmbRing(shape, hl)
            .clip(shape)
            .background(if (selected) LocalAccentDim.current else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(vertical = s.dp(2f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The colours are read inside XmbInk: while the tab holds the highlight its muted text and icon turn accent.
        XmbInk(hl) {
            val cs = MaterialTheme.colorScheme
            val ink = if (selected) cs.primary else cs.onSurfaceVariant
            Icon(icon, contentDescription = label, tint = ink, modifier = Modifier.size(s.dp(18f)))
            if (selected) {
                Spacer(Modifier.height(s.dp(1f)))
                // Each line gets a line box no taller than its text, and the count is pulled up a touch, so it sits right
                // under the label instead of a line of empty space below it.
                Text(
                    label, color = ink, fontSize = s.sp(10f), lineHeight = s.sp(10f), fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (count != null) {
                    Text(
                        "$count", color = cs.onSurfaceVariant, fontSize = s.sp(9f), lineHeight = s.sp(9f), maxLines = 1,
                        modifier = Modifier.offset(y = -s.dp(2f)),
                    )
                }
            }
        }
    }
}

/**
 * [steamFocusRing] that also hands the highlight [item] to the caller, so its text and icons can turn accent while it is hot
 * (wrap them in [XmbInk] with the same item). Under a highlight host (the XMB interface) it registers [item] as a stop of the
 * one gliding outline; everywhere else it is exactly [steamFocusRing].
 */
@Composable
internal fun Modifier.xmbRing(shape: Shape, item: XmbHighlightItem): Modifier =
    if (xmbHighlightHostOrNull() != null) this.xmbHighlight(shape, item, wholeSubtree = true) else this.steamFocusRing(shape)

/** How long (ms) the header ignores scrolling after it hides or shows. */
private const val HEADER_SETTLE_MS = 450L

/**
 * The header above a store's list or grid (search field, filter chips, sort) in the XMB home: it slides away while you
 * scroll down and comes back as soon as you scroll up, or the list is back at its top, so moving up with a controller
 * always reaches it. Each scrolling list below reports itself through [Track]. Off the XMB home nothing ever collapses.
 */
@Stable
internal class XmbHeaderState {
    var shown by mutableStateOf(true)
    var lastToggle = 0L
}

@Composable
internal fun rememberXmbHeader(): XmbHeaderState = remember { XmbHeaderState() }

/** The collapsing header of the store tab being drawn, so lists nested in its body can report their scrolling to it. */
internal val LocalXmbHeader = staticCompositionLocalOf<XmbHeaderState?> { null }

/** A list state that reports its scrolling to the surrounding tab's collapsing header, if it has one. */
@Composable
internal fun rememberTrackedListState(): LazyListState {
    val state = rememberLazyListState()
    LocalXmbHeader.current?.Track(state)
    return state
}

/** Watches one list's position (first visible item and its offset) and hides or shows the header with its direction. */
@Composable
internal fun XmbHeaderState.Track(firstIndex: () -> Int, firstOffset: () -> Int) {
    if (!LocalStoreXmb.current) return
    LaunchedEffect(this) {
        var lastI = firstIndex()
        var lastO = firstOffset()
        snapshotFlow { firstIndex() to firstOffset() }.collect { (i, o) ->
            val down = i > lastI || (i == lastI && o > lastO)
            val up = i < lastI || (i == lastI && o < lastO)
            lastI = i
            lastO = o
            // Hiding or showing resizes the list, which can nudge its position for a moment: ignore that.
            val now = System.currentTimeMillis()
            if (now - lastToggle < HEADER_SETTLE_MS) return@collect
            val next = when {
                i == 0 && o == 0 -> true
                down && i > 0 -> false
                up -> true
                else -> shown
            }
            if (next != shown) {
                shown = next
                lastToggle = now
            }
        }
    }
}

@Composable
internal fun XmbHeaderState.Track(list: LazyListState) = Track({ list.firstVisibleItemIndex }, { list.firstVisibleItemScrollOffset })

@Composable
internal fun XmbHeaderState.Track(grid: LazyGridState) = Track({ grid.firstVisibleItemIndex }, { grid.firstVisibleItemScrollOffset })

/** A list state made where the list is composed (so it starts over with it, as an untracked list's does) that reports to this header. */
@Composable
internal fun XmbHeaderState.trackedListState(): LazyListState = rememberLazyListState().also { Track(it) }

/** [trackedListState] for a grid. */
@Composable
internal fun XmbHeaderState.trackedGridState(): LazyGridState = rememberLazyGridState().also { Track(it) }

/** The header's content: collapsible in the XMB home, plain (laid out as before) everywhere else. */
@Composable
internal fun XmbHeaderState.Header(content: @Composable ColumnScope.() -> Unit) {
    if (!LocalStoreXmb.current) {
        Column(content = content)
    } else {
        AnimatedVisibility(
            visible = shown,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) { Column(content = content) }
    }
}

/**
 * A portrait (2:3) cover with the name small at its bottom, the way the Xbox library draws its games. Focused, it takes the
 * highlight outline ([deckFocusRingInk]) and shows [focusDetail] (a rating) under the name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun XmbStoreCover(
    art: List<String>,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** A small pill over the art's top-left corner ("Gen 2", "-50%"); null = none. */
    badge: String? = null,
    /** A line under the name ("Installed", "Downloading 34%"); null = none. */
    status: String? = null,
    statusAccent: Boolean = false,
    /** Shown under the name only while the cover is focused; null = nothing. */
    focusDetail: String? = null,
    /** Extra lines under the name (a compact price row). */
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val s = rememberScreenScale()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(s.dp(10f))
    var focused by remember { mutableStateOf(false) }
    val frostLine = xmbPageFrost()?.line
    // The name sits in white on the art, so there is no ink to turn: the cover only registers with the gliding outline.
    val hl = rememberXmbHighlightItem()
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .deckFocusRingInk(shape, hl, scaleTo = 1.06f)
            .clip(shape)
            .border(1.dp, frostLine ?: cs.outline, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        CatalogArt(candidates = art, title = title, modifier = Modifier.fillMaxWidth(), aspectRatio = 2f / 3f)
        if (!badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(s.dp(5f))
                    .clip(RoundedCornerShape(5.dp))
                    .background(xmbChipOverArt().copy(alpha = 0.9f))
                    .padding(horizontal = s.dp(5f), vertical = 1.dp),
            ) {
                Text(badge, fontSize = s.sp(9.5f), fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .padding(start = s.dp(7f), end = s.dp(7f), top = s.dp(18f), bottom = s.dp(6f)),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // Tight line height: a two-line name reads as one name, not two paragraphs.
            Text(
                title, fontSize = s.sp(11f), lineHeight = s.sp(12.5f), fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (status != null) {
                Text(
                    status, fontSize = s.sp(9.5f), maxLines = 1,
                    color = if (statusAccent) cs.primary else Color.White.copy(alpha = 0.8f),
                )
            }
            footer?.invoke(this)
            if (focused && focusDetail != null) {
                // A long rating ("Overwhelmingly Positive · 97%") wraps to a second line rather than being cut off.
                Text(
                    focusDetail, fontSize = s.sp(9.5f), lineHeight = s.sp(10.8f), color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A game as a wide row: its portrait cover beside the eyebrow, name and [footer] (price, rating). [compact] is the
 * smaller row for lists ("Top Free Games"); otherwise it is the featured hero.
 */
@Composable
internal fun XmbFeatureCard(
    art: List<String>,
    title: String,
    eyebrow: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val s = rememberScreenScale()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(s.dp(12f))
    // On the glass pages the card is a panel that pops (frosted glass, sheen, hairline, soft shadow) with a thin border;
    // elsewhere the opaque card, as before.
    val frost = xmbPageFrost()
    val hl = rememberXmbHighlightItem()
    Row(
        modifier = modifier
            // Room for the shadow: neighbouring cards in a list are only a few dp apart.
            .then(if (frost != null) Modifier.padding(vertical = 5.dp) else Modifier)
            .fillMaxWidth()
            .deckFocusRingInk(shape, hl, scaleTo = 1.03f)
            .xmbGlassCard(shape, frost?.card ?: cs.surfaceContainer)
            .border(1.dp, frost?.line ?: cs.outline, shape)
            .clickable(onClick = onClick)
            .padding(s.dp(8f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(12f)),
    ) {
        CatalogArt(
            candidates = art, title = title, aspectRatio = 2f / 3f,
            modifier = Modifier.width(if (compact) s.w(0.05f, min = 44.dp, max = 80.dp) else s.w(0.09f, min = 76.dp, max = 140.dp)),
            shape = RoundedCornerShape(s.dp(8f)),
        )
        // The text is read inside XmbInk: while the card holds the highlight it turns accent.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.dp(if (compact) 3f else 5f))) {
            XmbInk(hl) {
                val ink = MaterialTheme.colorScheme
                if (eyebrow.isNotBlank()) {
                    Text(eyebrow, fontSize = s.sp(10f), fontWeight = FontWeight.Bold, color = ink.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    title, fontSize = s.sp(if (compact) 12.5f else 16f), lineHeight = s.sp(if (compact) 14f else 18f),
                    fontWeight = if (compact) FontWeight.Bold else FontWeight.ExtraBold,
                    color = ink.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                footer()
            }
        }
    }
}

/**
 * XMB home: a glass panel that is ONE stop of the D-pad (the highlight glides from panel to panel). A enters the
 * [xmbInner] buttons inside [content], B comes back to the panel; a tap anywhere on it runs [onOpen]. [modifier] places the
 * panel: leave 12 dp or more round it for its shadow.
 */
@Composable
internal fun XmbPanelCard(modifier: Modifier, onOpen: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val cs = MaterialTheme.colorScheme
    XmbGroupItem(rememberXmbFocusGroup(), shape, modifier) {
        // The panel itself is not a focus target (the group item is).
        Box(
            Modifier
                .fillMaxWidth()
                .xmbGlassCard(shape, xmbCardFill(cs.surfaceVariant))
                .border(1.dp, xmbCardLine(cs.outline), shape)
                .focusProperties { canFocus = false }
                .clickable(onClick = onOpen),
        ) { content() }
    }
}

/**
 * The compact search field. D-pad focus only highlights it; A / Enter / a tap starts typing (keyboard up), and the
 * keyboard's Search, Back or B ends typing and leaves focus on the field, so passing over it never opens the keyboard.
 */
@Composable
internal fun XmbSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = rememberScreenScale()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(s.dp(9f))
    // Tap-to-edit behaviour is shared with every other search bar (ui/deck/DeckXmbKit.kt).
    val edit = rememberXmbEdit()
    val frost = xmbPageFrost()
    // Under a highlight host the gliding outline marks the bar (and stays on it while typing), so its own accent border is dropped.
    val glides = xmbHighlightHostOrNull() != null
    Row(
        modifier = modifier
            .widthIn(max = s.w(0.45f, min = 240.dp, max = 520.dp))
            .height(s.h(0.09f, min = 40.dp, max = 52.dp))
            .then(if (glides) Modifier.xmbHighlight(shape, wholeSubtree = true) else Modifier)
            .xmbEditStop(edit)
            .clip(shape)
            .background(frost?.raised ?: cs.surfaceContainer)
            .border(
                if (edit.active && !glides) 2.dp else 1.dp,
                if (edit.active && !glides) cs.primary else (frost?.line ?: cs.outline),
                shape,
            )
            .clickable { edit.start() }
            .padding(horizontal = s.dp(10f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(s.dp(18f)))
        Spacer(Modifier.width(s.dp(8f)))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, fontSize = s.sp(12.5f), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = cs.onSurface, fontSize = s.sp(12.5f)),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(); edit.stop() }),
                modifier = Modifier.fillMaxWidth().xmbEditField(edit),
            )
            XmbEditTapCatcher(edit)
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, contentDescription = "Clear search", tint = cs.onSurfaceVariant,
                modifier = Modifier
                    .size(s.dp(26f))
                    .clip(RoundedCornerShape(50))
                    .focusProperties { canFocus = false }
                    .clickable(onClick = onClear)
                    .padding(s.dp(4f)),
            )
        }
    }
}
