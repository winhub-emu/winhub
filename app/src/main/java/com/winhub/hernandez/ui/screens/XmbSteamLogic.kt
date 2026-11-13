package com.winhub.hernandez.ui.screens

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// The geometry and lists of the XMB Home's Steam Deck library look (XmbSteamHome.kt), kept free of Compose and Android so it can be
// unit-tested.

/** The first game's card has the shape of Steam's wide capsule (460x215), at the same height as the other cards. */
internal const val XMB_STEAM_WIDE_RATIO = 460f / 215f

/** The card under the cursor is drawn this many times its size: a subtle lift, not a zoom. */
internal const val XMB_STEAM_FOCUS_SCALE = 1.06f

/** While a game's options are open its card is drawn this many times its size (a lifted, larger card, from its bottom left corner). */
internal const val XMB_STEAM_OPTS_SCALE = 1.14f

/** The Home backdrop's bottom gradient starts this far (dp, on a 412 dp phone) above the top edge of the tabs' band. */
internal const val XMB_STEAM_FADE_LEAD = 40f

/** The margin (dp, on a 412 dp phone) at the row's left end, where the first game sits, and on the other side of the screen at its right end. */
internal const val XMB_STEAM_EDGE = 56f

/** The text tabs are this tall (dp): the touch target. */
internal const val XMB_STEAM_TABS_H = 48f

/** The title and the subtitle under it, together (dp, on a 412 dp phone): the title and the subtitle right under it, boxes hugging their glyphs. */
internal const val XMB_STEAM_TITLE_H = 27f

/** The covers end this far (dp, on a 412 dp phone) above the title's box; the title is nudged up over it ([XMB_STEAM_TITLE_NUDGE]). */
internal const val XMB_STEAM_TITLE_GAP = 0f

/** The title's box sits this far (dp, on a 412 dp phone) above where the covers end, and its letters are drawn the same distance lower inside it: the subtitle stays put. */
internal const val XMB_STEAM_TITLE_NUDGE = 3f

/** The title and the subtitle start this far (dp, on a 412 dp phone) to the right of the focused card's left edge. */
internal const val XMB_STEAM_TITLE_INSET = 12f

/** The options column may run down to this far (dp, on a 412 dp phone) above the screen's bottom edge: the hints are on the right, not under it. */
internal const val XMB_STEAM_COL_BOTTOM = 12f

/** The title block ends this far (dp, on a 412 dp phone) above the tabs' text. */
internal const val XMB_STEAM_TABS_GAP = 4f

/** The tabs' text with its padding is about this tall (dp, on a 412 dp phone). */
internal const val XMB_STEAM_TAB_TEXT_H = 26f

/**
 * The room (dp, on a 412 dp phone) between the tabs' centre and the bottom edge the covers are sized to when working out where their
 * top edge is (the title block, its gaps and half the tabs' text). The covers themselves reach lower, down to the tighter title (see
 * XmbSteamLayout.cardBottom), so this only decides where their tops sit.
 */
private const val XMB_STEAM_COVER_ROOM = 61f

/** The tabs sit with their centre this share of the screen's height down (a narrow screen lifts them above the hints). */
private const val XMB_STEAM_TABS_AT = 0.92f

/** How far (dp, on a 412 dp phone) the covers lift while a game's options are open, at most. */
private const val XMB_STEAM_LIFT = 120f

/** The text tabs under the focused game's title: the peek of the tabs page, which lists all four (see [XMB_PAGE_TABS]). */
internal enum class XmbSteamTab { STORES, DOWNLOADS, FRIENDS, SOCIAL }

/** The pictures the first game's wide card tries: its landscape art, then its background art, each once; the caller falls back to the cover. */
internal fun <T> xmbSteamWideOrder(wide: List<T>, background: List<T>): List<T> = (wide + background).distinct()

/**
 * Whether the first game's wide card shows its cover (cropped to the card) instead of the placeholder while no landscape art is on it:
 * only once every landscape source has failed ([allFailed]) and the game's art lookup has finished ([resolved], as one may still find
 * more), and for good once that has been decided ([latched]), so the card does not swing between a placeholder and a cropped portrait.
 */
internal fun xmbWideCoverShows(allFailed: Boolean, resolved: Boolean, latched: Boolean): Boolean = latched || (allFailed && resolved)

/** How many dp the Home backdrop is blurred with [lift] (0 at rest, 1 with a game's options open) of the way to [max]. */
internal fun xmbSteamBackdropBlur(lift: Float, max: Float): Float = max * lift.coerceIn(0f, 1f)

/** The black dim over the Home backdrop's art with [lift] of the way to the options: 35% beside a blur, 55% where there is none (below API 31). */
internal fun xmbSteamBackdropDim(lift: Float, blurs: Boolean): Float = (if (blurs) 0.35f else 0.55f) * lift.coerceIn(0f, 1f)

/** The share of its size the card [index] is drawn at with the cursor at [pos]: [focus] on the cursor, 1 away from it, gliding in between. */
internal fun xmbSteamScale(index: Int, pos: Float, focus: Float = XMB_STEAM_FOCUS_SCALE): Float =
    1f + (focus - 1f) * (1f - abs(index - pos)).coerceAtLeast(0f)

/**
 * The slots to compose with the cursor at [pos]: [count] cards in all, on a screen [viewW] wide with cards at least [pitch] apart. A
 * window around the cursor wide enough for the row to run from edge to edge on either side of it (the row is centred on the cursor
 * or pinned to an end), so a library of hundreds only ever composes a couple of dozen covers.
 */
internal fun xmbSteamWindow(pos: Float, count: Int, viewW: Float, pitch: Float): IntRange {
    val reach = ceil(viewW / max(1f, pitch)).toInt() + 2
    val base = floor(pos).toInt()
    return (base - reach).coerceAtLeast(0)..(base + reach).coerceAtMost(count - 1)
}

/**
 * The row of cards: [count] of them, the first [firstW] wide and the others [otherW], [gap] apart, on a screen [viewW] wide with an
 * [edge] margin. Widths are those of an unfocused card; the one under the cursor is drawn [focus] times larger, from its left edge.
 */
internal class XmbSteamRow(
    val count: Int,
    val firstW: Float,
    val otherW: Float,
    val gap: Float,
    val viewW: Float,
    val edge: Float,
    val focus: Float = XMB_STEAM_FOCUS_SCALE,
) {
    private fun fullW(i: Int): Float = if (i == 0) firstW else otherW

    /** Card [i]'s left edge along the strip (0 = the first card's) with the cursor at [pos]; every card takes its drawn width. */
    fun left(i: Int, pos: Float): Float {
        if (i <= 0) return 0f
        // Only the (at most two) cards the cursor is on or between are drawn larger than their base size.
        var x = firstW + gap + (i - 1) * (otherW + gap)
        val lo = floor(pos).toInt()
        for (j in lo..lo + 1) if (j in 0 until i) x += fullW(j) * (xmbSteamScale(j, pos, focus) - 1f)
        return x
    }

    /**
     * Card [index]'s left edge while the options of the card [anchor] are open and [l] (0..1) of the way there: the anchor card sits at
     * the [edge] margin drawn [optsScale] times larger and the cards after it follow in a row to its right, the earlier ones run off to
     * the left. 0 is [x] (the row as it rests). [anchor] stays the same through the way back, so a cursor that has moved on does not
     * make the row jump.
     */
    fun xOpts(index: Int, pos: Float, anchor: Int, optsScale: Float, l: Float): Float {
        val rest = x(index, pos)
        if (l <= 0f || count <= 0) return rest
        val a = anchor.coerceIn(0, count - 1)
        var lifted = left(index, pos) + edge - left(a, pos)
        if (index > a) lifted += fullW(a) * (optsScale - xmbSteamScale(a, pos, focus)).coerceAtLeast(0f)
        return rest + (lifted - rest) * l.coerceAtMost(1f)
    }

    /** The strip's whole width with the cursor at [pos]. */
    fun total(pos: Float): Float = if (count <= 0) 0f else left(count, pos) - gap

    private fun centre(i: Int, pos: Float): Float = left(i, pos) + fullW(i) * xmbSteamScale(i, pos, focus) / 2f

    /**
     * Card [index]'s left edge on the screen with the cursor at [pos] (fractional while it glides): the cursor's card is centred, except
     * where that would show empty space: the first cards stay pinned to the left [edge] margin and the last ones to the right margin, and
     * a row that fits on the screen does not scroll at all (left aligned at the margin). That is clamp(centred, pinned right, pinned left).
     */
    fun x(index: Int, pos: Float): Float {
        if (count <= 0) return edge
        val p = pos.coerceIn(0f, (count - 1).toFloat())
        val lo = floor(p).toInt()
        val hi = min(lo + 1, count - 1)
        val focusCentre = centre(lo, pos) + (centre(hi, pos) - centre(lo, pos)) * (p - lo)
        val total = total(pos)
        val pinnedLeft = edge
        val pinnedRight = viewW - edge - total
        val shift = if (pinnedRight >= pinnedLeft) pinnedLeft else (viewW / 2f - focusCentre).coerceIn(pinnedRight, pinnedLeft)
        return left(index, pos) + shift
    }
}

/**
 * Where everything of the Steam look sits (dp, from the content box's top left): one row of covers resting low (their tops near half of the
 * screen and their bottoms just above the focused game's title), the title with the subtitle tight
 * under it and the centred text tabs near the bottom. [w] x [h] is the content box, [clipTop] the room the host's floating tab bar
 * takes at the top, [bottomClear] the room its hints take at the bottom, [k] the screen's scale ([ScreenScale.factor]) and [rowH] /
 * [extra] the options column's row height and the gap after its first row.
 */
internal class XmbSteamLayout(
    val w: Float,
    val h: Float,
    clipTop: Float,
    val bottomClear: Float,
    k: Float,
    rowH: Float,
    extra: Float,
) {
    /** The margin the row is pinned to at its ends, and the left margin of the lifted card (and its options column) while they are open. */
    val edge = (w * 0.055f).coerceIn(XMB_STEAM_EDGE * k, (XMB_STEAM_EDGE + 4f) * k)

    /** The tabs are centred (their text, in a band [XMB_STEAM_TABS_H] tall); a narrow screen, where they would meet the hints, lifts them above those. */
    val tabsCentre = if (w >= 640f) h * XMB_STEAM_TABS_AT else min(h * XMB_STEAM_TABS_AT, h - bottomClear - XMB_STEAM_TABS_H / 2f)
    val tabsTop = tabsCentre - XMB_STEAM_TABS_H / 2f

    // The covers are about a third of the screen tall with their bottoms at most that far below half of it, or higher on a short screen
    // where the title block and the tabs have to fit under them; that sets where their top edge is.
    private val wantH = min(h * 0.32f, w * 0.35f)
    private val coverBottom = min(tabsCentre - XMB_STEAM_COVER_ROOM * k, h * 0.50f + wantH)

    /** The top edge every cover shares (it does not move when the covers grow). */
    val cardTop = coverBottom - min(wantH, coverBottom - clipTop - 8f * k).coerceAtLeast(72f)

    // The title block ends 4 dp above the tabs' text and the covers 5 dp above the title: that is how low the covers can reach.
    private val cardBottomMax = tabsCentre - XMB_STEAM_TAB_TEXT_H * k / 2f - XMB_STEAM_TABS_GAP * k - XMB_STEAM_TITLE_H * k -
        XMB_STEAM_TITLE_GAP * k

    // The wide first card, drawn larger with the options open, must fit between the margins.
    private val widthCap = (w - 2f * edge) / (XMB_STEAM_WIDE_RATIO * XMB_STEAM_OPTS_SCALE)

    /** An unfocused portrait cover's height (from [cardTop] down to just above the title); the wide first card is as tall. */
    val fH = min(cardBottomMax - cardTop, widthCap).coerceAtLeast(72f)
    val fW = fH * 2f / 3f
    val wideW = fH * XMB_STEAM_WIDE_RATIO
    val gap = 6f * k

    /** The bottom edge every cover shares. */
    val cardBottom = cardTop + fH

    /** The title sits right under the covers; the subtitle follows it 2 dp lower (see [XMB_STEAM_TITLE_H]). */
    val titleY = cardBottom + XMB_STEAM_TITLE_GAP * k

    /** The title is as wide as the wide card (at most half the screen); it is kept on the screen when the row is pinned right. */
    val titleW = min(wideW, w * 0.5f)

    /** How far the covers lift while the options are open, so the column fits under the focused card without the larger card going under the tab bar. */
    val lift = min(XMB_STEAM_LIFT * k, cardTop - clipTop - 4f * k - fH * (XMB_STEAM_OPTS_SCALE - 1f)).coerceAtLeast(0f)

    /** The options column starts right under the lifted focused card. */
    val colTop = cardBottom - lift + 12f * k
    // Wide screens: the column runs down to the screen's foot (the hints are at the right). A narrow one keeps the room under the hints: its details pane sits under the column.
    val colBottom = if (w >= 640f) h - XMB_STEAM_COL_BOTTOM * k else h - bottomClear
    val nVis = ((colBottom - colTop - extra) / rowH).toInt().coerceIn(2, 8)

    /** The row for a library of [games] (all of them: there is no All Games card here). */
    fun row(games: Int): XmbSteamRow = XmbSteamRow(games, if (games > 0) wideW else fW, fW, gap, w, edge)

    /** The base width of the card in [slot]: only the first game's is wide. */
    fun cardW(slot: Int, games: Int): Float = if (slot == 0 && games > 0) wideW else fW

    /** The title's left edge: the focused card's, but never so far right that the title leaves the screen. */
    fun titleX(row: XmbSteamRow, target: Int, pos: Float): Float = min(row.x(target, pos), w - edge - titleW).coerceAtLeast(0f)
}

// ── Touch: swipes and taps on the Home ──

/** Which way a vertical swipe on the Home went once it travelled far enough (see [xmbVSwipe]). */
internal enum class XmbVSwipe { NONE, UP, DOWN }

/** The swipe's direction from its summed vertical travel [acc] (px, down is positive): none until it passed [thresholdPx] either way. */
internal fun xmbVSwipe(acc: Float, thresholdPx: Float): XmbVSwipe = when {
    acc <= -thresholdPx -> XmbVSwipe.UP
    acc >= thresholdPx -> XmbVSwipe.DOWN
    else -> XmbVSwipe.NONE
}

/** What a vertical swipe does on the Home. */
internal enum class XmbSwipeAction { NONE, OPEN_TABS, CLOSE_OPTIONS }

/**
 * An upward swipe opens the tabs page (when there are tabs to open and the options are not showing); a downward one closes the options
 * that are showing and does nothing otherwise (the tabs page closes with a downward swipe on its own page).
 */
internal fun xmbHomeSwipeAction(swipe: XmbVSwipe, optsOpen: Boolean, tabsAvailable: Boolean): XmbSwipeAction = when {
    swipe == XmbVSwipe.DOWN && optsOpen -> XmbSwipeAction.CLOSE_OPTIONS
    swipe == XmbVSwipe.UP && !optsOpen && tabsAvailable -> XmbSwipeAction.OPEN_TABS
    else -> XmbSwipeAction.NONE
}

/**
 * With the options open, a tap that no control took closes them only when it landed higher on the screen than the options column's top
 * edge ([optionsTop], in the same space as [tapY]); one at the column's height or lower is left alone (beside the column, the details
 * panel, the empty space under it: more likely a stray touch).
 */
internal fun xmbTapClosesOptions(tapY: Float, optionsTop: Float): Boolean = tapY < optionsTop

/** Two taps that open and close the options are at least this far (ms) apart; a quicker one is a double-tap bounce and is dropped. */
internal const val XMB_TAP_GAP_MS = 250L

/** False while [nowMs] is still within [gapMs] of the last toggling tap at [lastMs] (a clock that went backwards counts as open). */
internal fun xmbTapGateOpen(lastMs: Long, nowMs: Long, gapMs: Long = XMB_TAP_GAP_MS): Boolean = nowMs < lastMs || nowMs - lastMs >= gapMs

/** The least height (dp) of a row of the options column on the Home: a touch target. */
internal const val XMB_STEAM_MIN_ROW = 48f

// ── The details panel beside the options ──

// Line heights (dp, at the default font scale) and the gap above each block of the compact details panel (XmbSteamInfo.kt).
private const val XMB_INFO_HEADER_LINE = 19f
private const val XMB_INFO_DOTS_LINE = 14f
private const val XMB_INFO_FACTS_LINE = 17f
private const val XMB_INFO_DESC_LINE = 15f
private const val XMB_INFO_DOTS_GAP = 5f
private const val XMB_INFO_FACTS_GAP = 6f
private const val XMB_INFO_DESC_GAP = 5f
private const val XMB_INFO_WRAP_GAP = 4f

/** What the compact details panel shows: the header line (store tag and pills), the dots line, the facts row and [descLines] lines of description. */
internal class XmbInfoPlan(val header: Boolean, val dots: Boolean, val facts: Boolean, val descLines: Int)

private fun xmbInfoBlockH(lines: Int, lineH: Float, topGap: Float, k: Float): Float =
    topGap + lines * lineH * k + (lines - 1).coerceAtLeast(0) * XMB_INFO_WRAP_GAP

/**
 * Which blocks of the details panel fit in [availH] dp (at font scale [fontScale]), given how many lines each needs ([headerLines] for the
 * store tag and pills, [dotsLines] for the driver / VKD3D / backend dots, [factsLines] for playtime, genre, year and score; 0 = nothing
 * to show) and how many description lines are wanted at most. The facts row is the last to go and the description the first: blocks are
 * taken in the order facts, header, dots, description and the first that does not fit ends the list, so nothing is ever half shown.
 * The description takes as many of its lines as are left (none when not even one fits).
 */
internal fun xmbInfoPlan(availH: Float, fontScale: Float, headerLines: Int, dotsLines: Int, factsLines: Int, descWanted: Int): XmbInfoPlan {
    val k = fontScale.coerceIn(0.8f, 2f)
    var left = availH
    var ok = true
    var facts = false
    var header = false
    var dots = false
    var desc = 0
    if (factsLines > 0) {
        val cost = xmbInfoBlockH(factsLines, XMB_INFO_FACTS_LINE, XMB_INFO_FACTS_GAP, k)
        ok = cost <= left
        if (ok) { left -= cost; facts = true }
    }
    if (ok && headerLines > 0) {
        val cost = xmbInfoBlockH(headerLines, XMB_INFO_HEADER_LINE, 0f, k)
        ok = cost <= left
        if (ok) { left -= cost; header = true }
    }
    if (ok && dotsLines > 0) {
        val cost = xmbInfoBlockH(dotsLines, XMB_INFO_DOTS_LINE, XMB_INFO_DOTS_GAP, k)
        ok = cost <= left
        if (ok) { left -= cost; dots = true }
    }
    if (ok && descWanted > 0) {
        desc = floor((left - XMB_INFO_DESC_GAP) / (XMB_INFO_DESC_LINE * k)).toInt().coerceIn(0, descWanted)
    }
    return XmbInfoPlan(header, dots, facts, desc)
}

/** How many lines a wrapping row of items [widths] wide, [gap] apart, takes in [avail] (an item wider than that takes a line to itself); 0 for no items. */
internal fun xmbFlowLines(widths: List<Float>, gap: Float, avail: Float): Int {
    if (widths.isEmpty()) return 0
    var lines = 1
    var x = 0f
    for (w in widths) {
        if (x == 0f) { x = w; continue }
        val next = x + gap + w
        if (next > avail) { lines++; x = w } else x = next
    }
    return lines
}

/** A rough width (dp) of [chars] characters of text [sizeSp] sp tall at font scale [fontScale]: a little over half an em each, kept on the wide side. */
internal fun xmbTextWidth(chars: Int, sizeSp: Float, fontScale: Float): Float = chars * sizeSp * 0.6f * fontScale.coerceIn(0.8f, 2f)
