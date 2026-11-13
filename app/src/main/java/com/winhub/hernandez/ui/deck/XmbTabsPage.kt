package com.winhub.hernandez.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.LocalXmbHintInset
import com.winhub.hernandez.ui.screens.XMB_PAGE_TABS
import com.winhub.hernandez.ui.screens.XmbDownloadRing
import com.winhub.hernandez.ui.screens.XmbSteamTab
import com.winhub.hernandez.ui.screens.xmbClick
import com.winhub.hernandez.ui.screens.xmbTabStep

// The XMB Home's tabs page, in the look of the Steam Deck's What's New / Friends / Recommended pages. Down from the games row pushes the
// Home up and out and brings the page up from the bottom: a pill bar of tabs (Stores, Downloads, Friends) under the
// shell's floating header, the tab's page under it, and the shell's Back / Search hints (and Start Add Game on the Stores tab) over it.
// B, or Up from the pill bar, or a swipe down slides it back down onto the Home, whose focused game has not moved. The rules (the
// order of the tabs, the Stores grid) are in screens/XmbTabsLogic.kt; what a page gets from the host is XmbTabsPageFocus.kt.

/** How long the Home and the page take to slide past each other, in ms. */
private const val XMB_TABS_SLIDE_MS = 420

/** A downward drag of this many dp on the page closes it. */
private const val XMB_TABS_SWIPE_CLOSE_DP = 64f

/** The tabs' text on the pill bar: light, a step under the selected tab's white. */
private val XMB_TABS_INK = Color.White.copy(alpha = 0.78f)

/** The selected tab's pill: dark, see-through. */
private val XMB_TABS_PILL = Color.Black.copy(alpha = 0.5f)

/** The green of the Friends tab's count badge (the same green as the A hint). */
private val XMB_TABS_BADGE = Color(0xFF3DDC84)

/**
 * The room the shell's floating header takes at the top of the Home (dp): its top padding and its height, as DeckFloatingBar lays
 * them out (keep in step with it). The camera hole's top edge comes on top of that.
 */
private fun xmbFloatingBarBand(ss: ScreenScale, portrait: Boolean): Dp =
    ss.dp(if (portrait) 2f else 6f) + ss.dp(40f * (if (portrait) 0.8f else 1f))

private fun XmbSteamTab.labelRes(): Int = when (this) {
    XmbSteamTab.STORES -> R.string.xmb_tab_stores
    XmbSteamTab.DOWNLOADS -> R.string.xmb_tab_downloads
    XmbSteamTab.FRIENDS -> R.string.xmb_tab_friends
    XmbSteamTab.SOCIAL -> R.string.xmb_tab_social
}

/**
 * The Home with the tabs page over it. [open] slides the Home ([home], kept composed, so its focused game stays) up and out and the page
 * in from the bottom, and false slides them back; [tab] is the shown tab's index in [XMB_PAGE_TABS] (any value: it is kept on the list)
 * and [onTab] / [onClose] are asked to change it / to close the page. The Stores tab shows [signedIn] and [games] and opens a store with
 * [onStore]; Start (or the hint) runs [onAddGame]. [badges] are the counts the pill bar shows on a tab (none for a missing or zero one); [downloadRing] is the Downloads tab's progress ring (none for null).
 * While the page is up the triggers L2 / R2 step the tab from anywhere (through the shell's DeckActions.subTab, so pads whose triggers
 * are analog work too).
 */
@Composable
internal fun XmbTabsPageHost(
    open: Boolean,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    signedIn: Map<DeckStore, Boolean>,
    games: List<Shortcut>,
    onStore: (DeckStore) -> Unit,
    onAddGame: () -> Unit,
    onSocial: (Int) -> Unit,
    badges: Map<XmbSteamTab, Int> = emptyMap(),
    downloadRing: XmbDownloadRing? = null,
    home: @Composable () -> Unit,
) {
    val progress = animateFloatAsState(if (open) 1f else 0f, tween(XMB_TABS_SLIDE_MS, easing = FastOutSlowInEasing), label = "xmbTabsPage")
    val openNow by rememberUpdatedState(open)
    // The page is composed while it is up or still sliding away; derived, so the animation does not recompose this per frame.
    val shown by remember { derivedStateOf { openNow || progress.value > 0.001f } }
    Box(Modifier.fillMaxSize()) {
        // Read in the graphics layers, so the slide re-draws without recomposing.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -progress.value * size.height }) { home() }
        if (shown) {
            // Under the page and over the Home: while the page rises or sinks, a strip of the Home still shows above it, and a tap on that
            // must not reach the games (a cover would open its options under the page).
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
            XmbTabsPageBody(open, progress, tab, onTab, onClose, signedIn, games, onStore, onAddGame, onSocial, badges, downloadRing)
        }
    }
}

@Composable
private fun XmbTabsPageBody(
    open: Boolean,
    progress: State<Float>,
    tab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    signedIn: Map<DeckStore, Boolean>,
    games: List<Shortcut>,
    onStore: (DeckStore) -> Unit,
    onAddGame: () -> Unit,
    onSocial: (Int) -> Unit,
    badges: Map<XmbSteamTab, Int>,
    downloadRing: XmbDownloadRing?,
) {
    val tabs = XMB_PAGE_TABS
    val sel = tab.coerceIn(0, tabs.lastIndex)
    val current = tabs[sel]
    val ss = rememberScreenScale()
    val deckActions = LocalDeckActions.current
    val focus = remember { XmbTabsPageFocus() }
    val selNow by rememberUpdatedState(sel)
    val onTabNow by rememberUpdatedState(onTab)
    val onCloseNow by rememberUpdatedState(onClose)
    val onAddNow by rememberUpdatedState(onAddGame)
    val onStores = current == XmbSteamTab.STORES

    // The phone layout has no floating header nor hints (the shell keeps its bars there), so it needs no room for them, and shows the
    // Add Game hint itself.
    val compact = isDeckCompact(LocalConfiguration.current)
    val dir = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    val top = maxOf(
        LocalTopBarOverlayInset.current,
        if (compact) 0.dp else cutout.calculateTopPadding() + xmbFloatingBarBand(ss, portrait = !deckRails()),
    )
    // The same gutter on both sides (the wider of the two camera-hole insets), so the pill bar and the pages are centred on the screen.
    val cutoutSide = if (compact) 0.dp else maxOf(cutout.calculateStartPadding(dir), cutout.calculateEndPadding(dir))
    val gutterStart = ss.dp(16f) + cutoutSide
    val gutterEnd = ss.dp(16f) + cutoutSide
    val pad = PaddingValues(start = gutterStart, top = ss.dp(8f), end = gutterEnd, bottom = LocalXmbHintInset.current + ss.dp(8f))
    // Handed to the pages after this composes (not written during it); the page is still sliding in on its first frame.
    SideEffect { if (focus.contentPadding != pad) focus.contentPadding = pad }

    // L2 / R2 step the tab, wrapping: through the shell, which also turns an analog trigger into the button.
    DisposableEffect(open, deckActions) {
        if (!open) return@DisposableEffect onDispose {}
        val step: (Int) -> Unit = { d -> onTabNow(xmbTabStep(selNow, d, tabs.size, wrap = true)) }
        deckActions.subTab.value = step
        onDispose { if (deckActions.subTab.value === step) deckActions.subTab.value = null }
    }
    // The Stores tab's "Start Add Game" hint in the shell's row of floating hints.
    DisposableEffect(open && onStores && !compact, deckActions) {
        if (!(open && onStores && !compact)) return@DisposableEffect onDispose {}
        val run: () -> Unit = { onAddNow() }
        deckActions.xmbAddGameHint.value = run
        onDispose { if (deckActions.xmbAddGameHint.value === run) deckActions.xmbAddGameHint.value = null }
    }

    // The keys come to one focus target: taken when the page opens and again when the app comes back (a store's own screen).
    val requester = remember { FocusRequester() }
    LaunchedEffect(open) {
        if (open) {
            // It opens with the cursor on the pill bar (also when it is opened again while still sliding away).
            focus.contentFocused = false
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, open) {
        if (!open) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) runCatching { requester.requestFocus() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = open) { onCloseNow() }

    // A swipe down closes it, from anywhere on the page that no list under the finger takes the drag for.
    val closePx = with(LocalDensity.current) { XMB_TABS_SWIPE_CLOSE_DP.dp.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    var dragDone by remember { mutableStateOf(false) }
    val drag = rememberDraggableState { d ->
        if (dragDone || !open) return@rememberDraggableState
        dragged += d
        if (dragged >= closePx) { dragDone = true; onCloseNow() }
    }

    val onKeyEvent: (KeyEvent) -> Boolean = keys@{ e ->
        if (!open || e.type != KeyEventType.KeyDown) return@keys false
        val first = e.nativeKeyEvent.repeatCount == 0
        // Start adds a game while the Stores tab shows, wherever the cursor is.
        if (e.key == Key.ButtonStart && onStores) {
            if (first) onAddNow()
            return@keys true
        }
        if (focus.contentFocused) {
            if (focus.onKey?.invoke(e) == true) return@keys true
            return@keys when (e.key) {
                Key.DirectionUp -> { focus.toBar(); true }
                Key.ButtonB, Key.Escape -> { if (first) onCloseNow(); true }
                Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> true
                else -> false
            }
        }
        when (e.key) {
            Key.DirectionLeft -> { onTabNow(xmbTabStep(selNow, -1, tabs.size, wrap = false)); true }
            Key.DirectionRight -> { onTabNow(xmbTabStep(selNow, 1, tabs.size, wrap = false)); true }
            // Into the page on a fresh press only: a held Down that opened the page would otherwise carry on into it with its repeats.
            Key.DirectionDown, Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { if (first) focus.contentFocused = true; true }
            Key.DirectionUp, Key.ButtonB, Key.Escape -> { if (first) onCloseNow(); true }
            else -> false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // From the bottom: 1 = in place, 0 = a whole screen down.
            .graphicsLayer { translationY = (1f - progress.value) * size.height }
            .draggable(drag, Orientation.Vertical, onDragStarted = { dragged = 0f; dragDone = false })
            .onPreviewKeyEvent(onKeyEvent)
            .focusRequester(requester)
            .focusable(),
    ) {
        XmbGlassBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(top = top)) {
            XmbTabsBar(
                tabs = tabs,
                sel = sel,
                barFocused = !focus.contentFocused,
                badges = badges,
                downloadRing = downloadRing,
                ss = ss,
                modifier = Modifier.padding(start = gutterStart, end = gutterEnd),
                onSelect = { i -> onTabNow(i) },
                onStep = { d -> onTabNow(xmbTabStep(selNow, d, tabs.size, wrap = true)) },
            )
            // The page's own box takes everything under the bar, down to the screen's edge; the host adds no padding (see XmbTabsPageFocus).
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = sel,
                    transitionSpec = {
                        val way = if (targetState >= initialState) 1 else -1
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { w -> way * w / 5 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(260)) { w -> -way * w / 5 })
                    },
                    label = "xmbTabsContent",
                    modifier = Modifier.fillMaxSize(),
                ) { i ->
                    when (tabs[i.coerceIn(0, tabs.lastIndex)]) {
                        XmbSteamTab.STORES -> XmbStoresPage(focus, signedIn, games, onStore)
                        XmbSteamTab.DOWNLOADS -> XmbDownloadsPage(focus)
                        XmbSteamTab.FRIENDS -> XmbFriendsPage(focus)
                        XmbSteamTab.SOCIAL -> XmbSocialPage(focus, onSocial)
                    }
                }
                // No floating hints on a phone: the same hint, in the page's corner.
                if (compact && onStores) {
                    DeckFloatingHint(
                        "≡", GlyphKind.Y, stringResource(R.string.xmb_hint_add_game), { onAddNow() },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = gutterEnd, bottom = ss.dp(8f)).defaultMinSize(minHeight = 48.dp),
                    )
                }
            }
        }
        // Sliding away: the page takes no taps (a tap on a store tile or a friend while it sinks would act on a page that is leaving).
        if (!open) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}

/**
 * The pill bar: L2, the tabs, R2, centred (a row wider than the screen scrolls, and keeps the selected tab in view). The selected tab is
 * a dark see-through pill with white bold text, ringed in the thin light outline while the bar holds the cursor; the others are bold
 * light text. A tab's [badges] count (Friends) and the [downloadRing] (Downloads) show as a small overlay badge right of its name (see [XmbTabPill]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun XmbTabsBar(
    tabs: List<XmbSteamTab>,
    sel: Int,
    barFocused: Boolean,
    badges: Map<XmbSteamTab, Int>,
    downloadRing: XmbDownloadRing?,
    ss: ScreenScale,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
    onStep: (Int) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ss.dp(6f), Alignment.CenterHorizontally),
    ) {
        XmbBarBumper("L2") { onStep(-1) }
        Row(
            Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).padding(horizontal = xmbBadgeEndRoom(ss)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ss.dp(4f)),
        ) {
            tabs.forEachIndexed { i, t ->
                val on = i == sel
                val src = remember { MutableInteractionSource() }
                val bring = remember { BringIntoViewRequester() }
                LaunchedEffect(on) { if (on) runCatching { bring.bringIntoView() } }
                val count = badges[t] ?: 0
                Box(
                    Modifier
                        .fillMaxHeight()
                        .defaultMinSize(minWidth = 48.dp)
                        .bringIntoViewRequester(bring)
                        .xmbClick(src) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    // The badge is an overlay and the highlight is drawn behind: the tab is the size of its label with or without it.
                    XmbTabPill(
                        tab = t, count = count, ring = downloadRing, ss = ss, padEnd = ss.dp(14f),
                        fill = if (on) XMB_TABS_PILL else Color.Transparent,
                        outline = if (on && barFocused) XMB_FOCUS_OUTLINE else null,
                        outlineWidth = 1.5.dp,
                        accent = primary,
                    ) {
                        Text(
                            stringResource(t.labelRes()).uppercase(),
                            color = if (on) Color.White else XMB_TABS_INK,
                            fontSize = ss.sp(11f),
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = ss.dp(14f), vertical = ss.dp(7f)),
                        )
                    }
                }
            }
        }
        XmbBarBumper("R2") { onStep(1) }
    }
}

/** The height of a tab's badge (the ring's size, the count pill's height): under the label's line, so it can never make a tab taller. */
private fun xmbBadgeH(tab: XmbSteamTab?, ss: ScreenScale): Dp = ss.dp(if (tab == XmbSteamTab.DOWNLOADS) 17f else 16f)

/** A tab's badge's base size (dp on a 412 dp phone): the ring's, or the count pill's height. */
private fun xmbBadgeBase(tab: XmbSteamTab?): Float = if (tab == XmbSteamTab.DOWNLOADS) 17f else 16f

/** The width of a tab's badge: the ring's size, or the count pill's (one digit as wide as it is high, a little more for each more). */
private fun xmbBadgeW(tab: XmbSteamTab?, count: Int, ss: ScreenScale): Dp {
    val digits = if (tab == XmbSteamTab.FRIENDS) count.toString().length.coerceAtLeast(1) else 1
    return ss.dp(xmbBadgeBase(tab) + 6f * (digits - 1))
}

/**
 * How far the selected tab's highlight reaches past the tab's own right edge to house a badge of width [w]: the badge starts a gap
 * after the label (which ends [padEnd] in from the edge) and the highlight keeps a margin past it; nothing when the tab's own padding
 * already has the room.
 */
private fun xmbBadgeReach(w: Dp, padEnd: Dp, ss: ScreenScale): Dp = maxOf(ss.dp(4f) + w + ss.dp(4f) - padEnd, 0.dp)

/**
 * The room a row of tabs keeps clear at each of its ends (the same at both, so the row stays centred), for the badge and the highlight
 * of the last tab to reach into: a scrolling row clips at its ends.
 */
internal fun xmbBadgeEndRoom(ss: ScreenScale): Dp = ss.dp(30f)

/**
 * The selected tab's highlight, drawn behind the tab without a say in its size: a see-through pill over the tab's own bounds, with the
 * thin [outline] (of [outlineWidth]) inside it when there is one, and [reach] wider to the right (the room for the badge). Nothing for a
 * [fill] and an [outline] that are both clear.
 */
private fun Modifier.xmbTabHighlight(fill: Color, outline: Color?, outlineWidth: Dp, reach: () -> Dp): Modifier = drawBehind {
    val w = this.size.width + reach().toPx()
    val h = this.size.height
    if (fill.alpha > 0f) drawRoundRect(fill, size = Size(w, h), cornerRadius = CornerRadius(h / 2f, h / 2f))
    if (outline != null) {
        val sw = outlineWidth.toPx()
        drawRoundRect(
            outline, topLeft = Offset(sw / 2f, sw / 2f), size = Size(w - sw, h - sw),
            cornerRadius = CornerRadius(h / 2f - sw / 2f, h / 2f - sw / 2f), style = Stroke(sw),
        )
    }
}

/** What a tab's badge last showed, kept so it can fade out in place after the ring or the count has gone. */
private class XmbBadgeHold {
    var ring: XmbDownloadRing? = null
    var count = 0
}

/**
 * A tab of the pill bar or of the Home's peek: its [label] (the text with its padding, [padEnd] of it at the right), the selected tab's
 * highlight ([fill], [outline]) behind it, and the tab's badge, Friends' online [count] in Steam's green or Downloads' [ring] in
 * [accent]. The badge is an overlay: it sits just right of the label, in the gap before the next tab, and the highlight is drawn a
 * little wider to cover it, but the tab's own size is that of its label whether the badge shows or not, so nothing moves (and the
 * highlight is as tall as ever). The badge fades in and out where it is.
 */
@Composable
internal fun XmbTabPill(
    tab: XmbSteamTab?,
    count: Int,
    ring: XmbDownloadRing?,
    ss: ScreenScale,
    padEnd: Dp,
    fill: Color,
    outline: Color?,
    outlineWidth: Dp,
    accent: Color,
    label: @Composable () -> Unit,
) {
    val hold = remember { XmbBadgeHold() }
    if (ring != null) hold.ring = ring
    if (count > 0) hold.count = count
    val wanted = when (tab) {
        XmbSteamTab.DOWNLOADS -> ring != null
        XmbSteamTab.FRIENDS -> count > 0
        else -> false
    }
    val shown = animateFloatAsState(if (wanted) 1f else 0f, tween(180), label = "xmbTabBadge")
    val bw = xmbBadgeW(tab, hold.count, ss)
    val bh = xmbBadgeH(tab, ss)
    val reach = xmbBadgeReach(bw, padEnd, ss)
    Box(Modifier.xmbTabHighlight(fill, outline, outlineWidth) { reach * shown.value }) {
        label()
        // Its right edge is the label's end plus the gap plus its width, measured from the tab's right edge (the label ends padEnd in).
        val at = Modifier
            .align(Alignment.CenterEnd)
            .offset(x = ss.dp(4f) + bw - padEnd)
            .graphicsLayer { alpha = shown.value }
        val held = hold.ring
        if (tab == XmbSteamTab.DOWNLOADS && held != null) {
            XmbRingGlyph(held, ss, accent, bh, at)
        } else if (tab == XmbSteamTab.FRIENDS && hold.count > 0) {
            Box(
                at.size(width = bw, height = bh).clip(CircleShape).background(XMB_TABS_BADGE),
                contentAlignment = Alignment.Center,
            ) {
                // No font padding and a line as tall as the digits, centred: the number sits in the middle of the pill, never cut at its foot.
                Text(
                    hold.count.toString(), color = Color.Black, fontSize = ss.sp(10f), fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    style = TextStyle(
                        lineHeight = ss.sp(10f),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    ),
                )
            }
        }
    }
}

/**
 * The Downloads tab's ring, [size] square: a hollow circle with a faint track, whose stroke fills clockwise from the top in [accent] as
 * far as [ring]'s progress, with a down arrow in it while a download runs and a pause symbol while the ones waiting do.
 */
@Composable
private fun XmbRingGlyph(ring: XmbDownloadRing, ss: ScreenScale, accent: Color, size: Dp, modifier: Modifier) {
    val sweep by animateFloatAsState(ring.progress, tween(300), label = "xmbRingSweep")
    val running = ring.running
    Canvas(modifier.size(size)) {
        val stroke = ss.dp(2f).toPx()
        val dia = this.size.minDimension - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val arc = Size(dia, dia)
        drawArc(accent.copy(alpha = 0.28f), 0f, 360f, false, topLeft = topLeft, size = arc, style = Stroke(stroke))
        if (sweep > 0.001f) {
            drawArc(accent, -90f, 360f * sweep, false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        // The symbol, strokes with round ends: its half-size is a fifth of the ring's inside.
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val h = dia * 0.21f
        val w = ss.dp(1.5f).toPx()
        if (running) {
            drawLine(accent, Offset(cx, cy - h), Offset(cx, cy + h), w, StrokeCap.Round)
            drawLine(accent, Offset(cx - h, cy), Offset(cx, cy + h), w, StrokeCap.Round)
            drawLine(accent, Offset(cx + h, cy), Offset(cx, cy + h), w, StrokeCap.Round)
        } else {
            drawLine(accent, Offset(cx - h * 0.55f, cy - h), Offset(cx - h * 0.55f, cy + h), w * 1.15f, StrokeCap.Round)
            drawLine(accent, Offset(cx + h * 0.55f, cy - h), Offset(cx + h * 0.55f, cy + h), w * 1.15f, StrokeCap.Round)
        }
    }
}

/** An L2 / R2 glyph on the pill bar, the same keycap as the header's L1 / R1: tapping it steps the tab as the trigger does. */
@Composable
private fun XmbBarBumper(text: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = 40.dp)
            .clip(deckShape(10.dp))
            .xmbClick(src, onClick)
            .padding(horizontal = 4.dp)
            .graphicsLayer { alpha = 0.6f },
    ) {
        DeckGlyph(text, GlyphKind.BUMPER)
    }
}
