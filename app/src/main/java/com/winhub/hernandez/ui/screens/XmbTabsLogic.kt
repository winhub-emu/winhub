package com.winhub.hernandez.ui.screens

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The pure rules of the XMB Home's tabs page (ui/deck/XmbTabsPage.kt, XmbStoresPage.kt), kept free of Compose and Android so they can be
// unit-tested: the order of the tabs and stepping along it, and the Stores page's grid of square cards (3 on the top row, the rest
// centred beneath) with the D-pad's moves between them.

/** The tabs of the tabs page, in the order of its pill bar. Friends is always there, signed in to Steam or not (its page asks to sign in). */
internal val XMB_PAGE_TABS: List<XmbSteamTab> = XmbSteamTab.entries

/**
 * The tab after [index] moved [delta] places along [count] tabs: wrapping round the ends when [wrap] (the triggers L2 / R2), else
 * stopping at them (Left / Right on the bar). An index off the list counts as the nearest end.
 */
internal fun xmbTabStep(index: Int, delta: Int, count: Int, wrap: Boolean): Int {
    if (count <= 0) return 0
    val i = index.coerceIn(0, count - 1)
    return if (wrap) ((i + delta) % count + count) % count else (i + delta).coerceIn(0, count - 1)
}

/** How many stores sit on the Stores page's top row. */
internal const val XMB_STORES_PER_ROW = 3

/** The gap (dp) between the Stores page's cards. */
internal const val XMB_STORES_GAP = 12f

/** [xmbStoresMove]'s answer for Up from the top row: the focus goes to the pill bar. */
internal const val XMB_STORES_TO_BAR = -1

/** The sizes of the rows of [count] cards, [perRow] to a row and the rest on the last: 5 cards in rows of 3 are `[3, 2]`. */
internal fun xmbStoreRows(count: Int, perRow: Int = XMB_STORES_PER_ROW): List<Int> {
    if (count <= 0 || perRow <= 0) return emptyList()
    val rows = ArrayList<Int>()
    var left = count
    while (left > 0) {
        rows.add(min(perRow, left))
        left -= perRow
    }
    return rows
}

/**
 * The Stores page's cards: [rows] (see [xmbStoreRows]) of identical squares [side] wide, [gap] apart, each row centred in a box [w] x [h]
 * (dp) and the rows together centred in its height. The cards are as large as fit: the widest row must fit the width and all the rows
 * the height.
 */
internal class XmbStoresGrid(val rows: List<Int>, val side: Float, val gap: Float, val w: Float, val h: Float) {
    val count: Int get() = rows.sum()

    /** The row card [i] is on. */
    fun rowOf(i: Int): Int {
        var left = i
        for ((r, n) in rows.withIndex()) {
            if (left < n) return r
            left -= n
        }
        return rows.lastIndex
    }

    /** The column of card [i] in its row. */
    fun colOf(i: Int): Int = i - rows.take(rowOf(i)).sum()

    /** Card [i]'s left edge. */
    fun x(i: Int): Float {
        val n = rows[rowOf(i)]
        return (w - (n * side + (n - 1) * gap)) / 2f + colOf(i) * (side + gap)
    }

    /** Card [i]'s top edge. */
    fun y(i: Int): Float = (h - (rows.size * side + (rows.size - 1) * gap)) / 2f + rowOf(i) * (side + gap)
}

/** The grid of [count] cards in a box [w] x [h] (dp). */
internal fun xmbStoresGrid(count: Int, w: Float, h: Float, gap: Float = XMB_STORES_GAP): XmbStoresGrid {
    val rows = xmbStoreRows(count)
    if (rows.isEmpty()) return XmbStoresGrid(rows, 0f, gap, w, h)
    val cols = rows.max()
    val side = min((w - (cols - 1) * gap) / cols, (h - (rows.size - 1) * gap) / rows.size)
    return XmbStoresGrid(rows, max(0f, side), gap, w, h)
}

/**
 * The card the D-pad reaches from [sel] with [dx] / [dy] (each -1, 0 or 1) in cards of [rows]: Left / Right stop at the row's ends,
 * Up / Down land on the nearest card of the next row by its centre (the shorter row is centred, so the first of two equally near
 * wins), Down from the last row stays where it is and Up from the first is [XMB_STORES_TO_BAR].
 */
internal fun xmbStoresMove(rows: List<Int>, sel: Int, dx: Int, dy: Int): Int {
    if (rows.isEmpty()) return XMB_STORES_TO_BAR
    val total = rows.sum()
    val s = sel.coerceIn(0, total - 1)
    var r = 0
    var c = s
    while (c >= rows[r]) { c -= rows[r]; r++ }
    if (dx != 0) return s + (c + dx).coerceIn(0, rows[r] - 1) - c
    if (dy == 0) return s
    val to = r + dy
    if (to < 0) return XMB_STORES_TO_BAR
    if (to > rows.lastIndex) return s
    // Each card's centre, in cards, from the middle of its row.
    val centre = c - (rows[r] - 1) / 2f
    var best = 0
    for (k in 1 until rows[to]) {
        if (abs(k - (rows[to] - 1) / 2f - centre) < abs(best - (rows[to] - 1) / 2f - centre)) best = k
    }
    return rows.take(to).sum() + best
}
