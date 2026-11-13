package com.winhub.hernandez.ui.screens

// The pure rules the XMB tabs page's two-column lists share (Downloads, Friends, and a game's configs on the Community page), kept free of
// Compose and Android so they can be unit-tested: how many columns fit, how a flat list of items (headings that span the full width, and
// cards that sit two to a line) falls into lines, and where the D-pad's cursor goes between them.
//
// A page keeps its cursor as an index into its own flat list of items. [xmbTwoColLines] turns that list into lines (each a list of item
// indexes: one for a full-width item, up to `columns` for a run of cards); the page draws one lazy item per line, and the moves below work
// on the lines. A page also keeps the column the cursor last chose (its "wanted" column), so a trip through a one-card line or a heading
// and back lands in the same column again.

/** The columns of a two-column page on a screen wide enough for them. */
internal const val XMB_TWO_COL_COLUMNS = 2

/** The content must be at least this wide (dp) for the cards to go two to a line; narrower, they stay one to a line. */
internal const val XMB_TWO_COL_MIN_DP = 720f

/** The gap (dp, before the screen's scale) between the two columns and between the lines. */
internal const val XMB_TWO_COL_GAP_DP = 12f

/** The widest the lists get (dp), however wide the screen: two cards of at least a sensible width, and not stretched past it. */
internal const val XMB_TWO_COL_MAX_WIDTH_DP = 1040f

/** [xmbTwoColVertical]'s answer for Up from the first selectable line: the cursor goes to the pill bar. */
internal const val XMB_TWO_COL_TO_BAR = -1

/** How many columns a list [widthDp] wide has: [XMB_TWO_COL_COLUMNS] when it is wide enough, else 1 (a phone held upright). */
internal fun xmbTwoColColumns(widthDp: Float): Int = if (widthDp >= XMB_TWO_COL_MIN_DP) XMB_TWO_COL_COLUMNS else 1

/**
 * The lines of a flat list whose item i spans the full width when `full[i]` is true (a heading, a toolbar) and is a card otherwise.
 * A full-width item is a line of its own; the cards between two of them (or the ends) fill lines of [columns], the last one maybe shorter.
 * Each line holds the indexes of its items.
 */
internal fun xmbTwoColLines(full: List<Boolean>, columns: Int): List<List<Int>> {
    val cols = columns.coerceAtLeast(1)
    val lines = ArrayList<List<Int>>()
    var run = ArrayList<Int>()
    for (i in full.indices) {
        if (full[i]) {
            if (run.isNotEmpty()) {
                lines.add(run)
                run = ArrayList()
            }
            lines.add(listOf(i))
        } else {
            run.add(i)
            if (run.size == cols) {
                lines.add(run)
                run = ArrayList()
            }
        }
    }
    if (run.isNotEmpty()) lines.add(run)
    return lines
}

/** The index in [lines] of the line holding item [item]; -1 when no line does. */
internal fun xmbTwoColLineOf(lines: List<List<Int>>, item: Int): Int = lines.indexOfFirst { item in it }

/** The column of item [item] in its line (0 for the left one); -1 when no line holds it. */
internal fun xmbTwoColCol(lines: List<List<Int>>, item: Int): Int {
    val at = xmbTwoColLineOf(lines, item)
    return if (at < 0) -1 else lines[at].indexOf(item)
}

/**
 * The item the cursor reaches from [from] with Up ([down] false) or Down: the first line above or below that has [selectable] items (a
 * heading that only labels is skipped), on the column [wantCol] or, when that line is shorter, on its last item. Down from the last
 * line stays on [from]; Up from the first line is [XMB_TWO_COL_TO_BAR]. An item that is on no line is treated as at the very top.
 */
internal fun xmbTwoColVertical(
    lines: List<List<Int>>,
    from: Int,
    down: Boolean,
    wantCol: Int,
    selectable: (Int) -> Boolean = { true },
): Int {
    val at = xmbTwoColLineOf(lines, from)
    val step = if (down) 1 else -1
    var j = (if (at < 0) -1 else at) + step
    while (j in lines.indices && !selectable(lines[j].first())) j += step
    if (j !in lines.indices) return if (down) from else XMB_TWO_COL_TO_BAR
    val line = lines[j]
    return line[wantCol.coerceIn(0, line.lastIndex)]
}

/** The item [delta] columns (-1 or 1) from [from] on its own line, stopping at the line's ends; [from] when it is on no line. */
internal fun xmbTwoColSide(lines: List<List<Int>>, from: Int, delta: Int): Int {
    val at = xmbTwoColLineOf(lines, from)
    if (at < 0) return from
    val line = lines[at]
    return line[(line.indexOf(from) + delta).coerceIn(0, line.lastIndex)]
}
