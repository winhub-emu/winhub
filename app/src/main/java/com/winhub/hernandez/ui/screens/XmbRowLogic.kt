package com.winhub.hernandez.ui.screens

// The index maths of the XMB games row and of the All Games page, kept free of Compose and Android so it can be unit-tested.

/** The Classic (and the Deck's full-screen) XMB's games row shows this many of the most recent games; the rest are on the All Games page. The Deck Home shows the whole library. */
internal const val XMB_ROW_LIMIT = 6

/** The All Games page's grid view shows this many games per row (fewer only when the screen is too narrow, see [xmbGridColumns]). */
internal const val XMB_GRID_COLS = 6

/** What the row remembers as the focused "path" while the All Games card is the one under the cursor (no real path looks like it). */
internal const val XMB_ALL_PATH = "xmb:all-games"

/** The first [limit] of an already ordered [list] when [enabled]; a search, a filter or a selection shows its whole list. */
internal fun <T> xmbRowOf(list: List<T>, limit: Int = XMB_ROW_LIMIT, enabled: Boolean = true): List<T> =
    if (enabled) list.take(limit) else list

/**
 * Where a remembered [path] sits in the row: [paths] are the games, which keep slots 0 until the last, and with [hasAll] the
 * All Games card takes the slot after the last game (so slot 0 is always the first game, or the card alone when there are no
 * games). -1 = the path isn't in the row.
 */
internal fun xmbSlotOf(paths: List<String>, path: String?, hasAll: Boolean): Int {
    if (path == null) return -1
    if (path == XMB_ALL_PATH) return if (hasAll) paths.size else -1
    return paths.indexOf(path)
}

/**
 * Columns of the All Games page's grid view: [wanted] when a cover can stay at least [minCell] wide, fewer on a narrow
 * (portrait) screen, never under [minCols]. [avail] and [gap] are the grid's width and the space between covers, in the same unit.
 */
internal fun xmbGridColumns(avail: Float, gap: Float, minCell: Float, wanted: Int = XMB_GRID_COLS, minCols: Int = 3): Int =
    ((avail + gap) / (minCell + gap)).toInt().coerceIn(minCols, wanted)

internal enum class XmbMove { LEFT, RIGHT, UP, DOWN, PAGE_UP, PAGE_DOWN }

/**
 * The cell the cursor lands on after [move] in a grid of [cols] columns holding [count] cells, row by row: Left/Right step
 * through the reading order, Up/Down a whole row (Down from a row above a shorter last row lands on its last cell).
 * [page] is how far a page jump goes.
 */
internal fun xmbGridMove(sel: Int, move: XmbMove, cols: Int, count: Int, page: Int): Int {
    if (count <= 0) return 0
    val last = count - 1
    val c = cols.coerceAtLeast(1)
    val s = sel.coerceIn(0, last)
    return when (move) {
        XmbMove.LEFT -> (s - 1).coerceAtLeast(0)
        XmbMove.RIGHT -> (s + 1).coerceAtMost(last)
        XmbMove.UP -> if (s - c >= 0) s - c else s
        XmbMove.DOWN -> when {
            s + c <= last -> s + c
            s / c < last / c -> last
            else -> s
        }
        XmbMove.PAGE_UP -> (s - page).coerceAtLeast(0)
        XmbMove.PAGE_DOWN -> (s + page).coerceAtMost(last)
    }
}

// The All Games card's mosaic (a folder-like 3 x 2 preview of the library).

/** The All Games card's mosaic shows this many covers, and a "+N more" tile as the sixth cell when the library has more. */
internal const val XMB_MOSAIC_COVERS = 5

/** The mosaic is a 3 rows x 2 columns grid: a library of up to this many games is shown cover by cover, without a "+N more" tile. */
internal const val XMB_MOSAIC_CELLS = 6

/** The mosaic's grid: rows x columns. */
internal const val XMB_MOSAIC_ROWS = 3
internal const val XMB_MOSAIC_COLS = 2

/** The covers a mosaic shows ([covers], in cell order) and the games left out of it ([more], 0 = no "+N more" tile). */
internal data class XmbMosaic<T>(val covers: List<T>, val more: Int)

/**
 * The games the mosaic and the wall behind it draw from, most recent first: the games [row] shows (the host's most recent ones), then
 * the rest of [all] in its own order. [key] tells two entries of the same game apart.
 */
internal fun <T> xmbRecentOrder(row: List<T>, all: List<T>, key: (T) -> String): List<T> = (row + all).distinctBy(key)

/**
 * What the mosaic shows for [ordered] games (most recent first): up to [XMB_MOSAIC_CELLS] games are shown cover by cover, more
 * than that [XMB_MOSAIC_COVERS] covers and a "+N more" tile counting the rest. Games with art come first (the rest only fill up
 * when fewer than that have any), each group keeping its order, so the pick never changes between recompositions.
 */
internal fun <T> xmbMosaicPick(ordered: List<T>, hasArt: (T) -> Boolean): XmbMosaic<T> {
    val total = ordered.size
    val (withArt, without) = ordered.partition(hasArt)
    val covers = (withArt + without).take(if (total <= XMB_MOSAIC_CELLS) total else XMB_MOSAIC_COVERS)
    return XmbMosaic(covers, if (total <= XMB_MOSAIC_CELLS) 0 else total - covers.size)
}

// The All Games page's header band: the row of back arrow, title, View and Sort at the top, with the covers scrolling under it.

/** The header row is never shorter than this (dp): the back arrow's tap area. */
internal const val XMB_ALL_HEADER_MIN_HEIGHT = 48f

/** The header row's height and its gap above it and below it, in dp as drawn on a 412 dp phone (they follow the screen scale). */
internal const val XMB_ALL_HEADER_HEIGHT = 40f
internal const val XMB_ALL_HEADER_TOP = 6f
internal const val XMB_ALL_HEADER_GAP = 6f

/**
 * Where the header sits and how much of the top it takes, all in dp from the page's top edge. [top] is the header row's top, [height]
 * its height and [band] the whole header zone: the first row of covers starts there, and what scrolls up into it fades out.
 */
internal data class XmbAllHeader(val top: Float, val height: Float, val band: Float) {
    /** Below this y the covers start to show; above it they are fully faded out. */
    val fadeStart: Float get() = top + height * 0.2f
}

/**
 * The header for a screen of scale [scale] ([ScreenScale.factor]) whose top [inset] (dp) is taken by the camera cut-out or by a
 * see-through top bar. The row is at least [XMB_ALL_HEADER_MIN_HEIGHT] tall.
 */
internal fun xmbAllHeader(inset: Float, scale: Float): XmbAllHeader {
    val top = inset.coerceAtLeast(0f) + XMB_ALL_HEADER_TOP * scale
    val height = maxOf(XMB_ALL_HEADER_MIN_HEIGHT, XMB_ALL_HEADER_HEIGHT * scale)
    return XmbAllHeader(top, height, top + height + XMB_ALL_HEADER_GAP * scale)
}

/**
 * The scroll [delta] (px, positive = scroll forward, so the content moves up) that brings a cell spanning [cellTop]..[cellBottom]
 * fully into the visible part of its viewport, [topMargin] px clear at the top (the header) and [bottomMargin] px at the bottom
 * (the floating hints), which is [viewportHeight] tall; 0 when it already is. A cell taller than the room shows its top.
 */
internal fun xmbRevealDelta(cellTop: Float, cellBottom: Float, viewportHeight: Float, topMargin: Float, bottomMargin: Float): Float {
    val bottom = viewportHeight - bottomMargin
    return when {
        cellTop < topMargin -> cellTop - topMargin
        cellBottom > bottom -> minOf(cellBottom - bottom, cellTop - topMargin)
        else -> 0f
    }
}
