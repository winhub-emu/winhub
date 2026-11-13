package com.winhub.hernandez.ui.screens

import com.winhub.hernandez.store.download.DownloadEntry
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.formatDownloadSize
import com.winhub.hernandez.store.download.formatDownloadSpeed
import com.winhub.hernandez.store.download.formatEta

// The pure rules of the XMB Home's Downloads page (ui/deck/XmbDownloadsPage.kt), kept free of Compose and Android so they can be
// unit-tested: how the registry's entries group into sections, where the D-pad's cursor goes, which buttons a card has, and how
// a download's progress reads.

/** The page's sections, top to bottom. */
internal enum class XmbDlSection {
    /** Downloading now, or paused part-way. */
    ACTIVE,

    /** Waiting for the one download slot. */
    QUEUED,

    /** Failed or cancelled: kept for the session so it can be opened again or dismissed. */
    STOPPED,

    /** The installed games, the ones with an update first. */
    INSTALLED,
}

/** One section of the page: its [entries], in the order the page shows them. */
internal class XmbDlGroup(val section: XmbDlSection, val entries: List<DownloadEntry>)

private val NAME_ORDER = compareBy<DownloadEntry>({ it.name.lowercase() }, { it.key })

/**
 * [entries] (any order) as the page's sections, the empty ones left out. The running download comes before the paused ones, the queue
 * is in its own order (position 1 starts next), failures come before cancellations and the games with an update before the rest; the
 * ties go by name.
 */
internal fun xmbDlGroups(entries: List<DownloadEntry>): List<XmbDlGroup> {
    val active = entries
        .filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED }
        .sortedWith(compareBy<DownloadEntry> { if (it.state == DownloadState.DOWNLOADING) 0 else 1 }.then(NAME_ORDER))
    val queued = entries
        .filter { it.state == DownloadState.QUEUED }
        .sortedWith(compareBy<DownloadEntry> { it.queuePosition }.then(NAME_ORDER))
    val stopped = entries
        .filter { it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED }
        .sortedWith(compareBy<DownloadEntry> { if (it.state == DownloadState.FAILED) 0 else 1 }.then(NAME_ORDER))
    val installed = entries
        .filter { it.state == DownloadState.INSTALLED }
        .sortedWith(compareBy<DownloadEntry> { if (it.updateAvailable) 0 else 1 }.then(NAME_ORDER))
    return listOf(
        XmbDlGroup(XmbDlSection.ACTIVE, active),
        XmbDlGroup(XmbDlSection.QUEUED, queued),
        XmbDlGroup(XmbDlSection.STOPPED, stopped),
        XmbDlGroup(XmbDlSection.INSTALLED, installed),
    ).filter { it.entries.isNotEmpty() }
}

/** The cards of [groups], top to bottom: what the D-pad's cursor steps along. */
internal fun xmbDlCards(groups: List<XmbDlGroup>): List<DownloadEntry> = groups.flatMap { it.entries }

/**
 * Where a card sits in the page's list, which has a heading item before each section's cards and then one item per line of cards: [header]
 * is the index of its section's heading, [item] the index of the line it is on, and [first] says that line is the first of its section
 * (so the heading is to be shown with it).
 */
internal class XmbDlSlot(val header: Int, val item: Int, val first: Boolean)

/** The slot of the card with index [card] along [xmbDlCards], the cards [columns] to a line; null for an index off the list. */
internal fun xmbDlSlot(groups: List<XmbDlGroup>, card: Int, columns: Int): XmbDlSlot? {
    if (card < 0) return null
    val cols = columns.coerceAtLeast(1)
    var index = 0
    var left = card
    for (g in groups) {
        val header = index
        index++
        if (left < g.entries.size) return XmbDlSlot(header, index + left / cols, first = left < cols)
        index += (g.entries.size + cols - 1) / cols
        left -= g.entries.size
    }
    return null
}

/**
 * The lines of cards of the page, top to bottom: each section's cards [columns] to a line (the last line of a section may be shorter),
 * each line the indexes along [xmbDlCards] of its cards. The headings between the sections are not on a line: the cursor skips them.
 */
internal fun xmbDlLines(groups: List<XmbDlGroup>, columns: Int): List<List<Int>> {
    val cols = columns.coerceAtLeast(1)
    val lines = ArrayList<List<Int>>()
    var next = 0
    for (g in groups) {
        val ids = (next until next + g.entries.size).toList()
        for (chunk in ids.chunked(cols)) lines.add(chunk)
        next += g.entries.size
    }
    return lines
}

/**
 * How long a button that asks for a second press to confirm (a download's Cancel, a game's Uninstall, a friend's Remove) waits for it before
 * it gives up, in ms.
 */
internal const val XMB_ARMED_MS = 4000L

/**
 * [inner], the button of the card under the cursor that holds it (-1: the card itself), made to fit a card that now has [actionCount] buttons:
 * a card that changed state under the cursor (downloading, then failed) has fewer. -1 stays -1; a card with no buttons drops it.
 */
internal fun xmbDlInnerClamp(inner: Int, actionCount: Int): Int =
    if (inner < 0 || actionCount <= 0) -1 else inner.coerceAtMost(actionCount - 1)

/**
 * Where the cursor is on the new list of card [keys]: on the card it was on ([key]) wherever that has moved to (a download finishing
 * or the queue being reordered must not take the cursor off it), else on the nearest index to the old one ([index]) when that card has
 * gone. -1 for no cards.
 */
internal fun xmbDlCursor(keys: List<String>, key: String?, index: Int): Int {
    if (keys.isEmpty()) return -1
    val at = if (key != null) keys.indexOf(key) else -1
    return if (at >= 0) at else index.coerceIn(0, keys.lastIndex)
}

/** What a card's buttons do. */
internal enum class XmbDlAction { PAUSE, RESUME, CANCEL, DETAILS, START_NEXT, LAUNCH, UNINSTALL, DISMISS }

/**
 * The buttons of [entry]'s card, left to right (the first is where A lands). Pause is only there for a store that can pause, "start next"
 * only for a queued download that is not already next ([canMoveUp]).
 */
internal fun xmbDlActions(entry: DownloadEntry, canMoveUp: Boolean): List<XmbDlAction> = when (entry.state) {
    DownloadState.DOWNLOADING ->
        listOfNotNull(if (entry.supportsPause) XmbDlAction.PAUSE else null, XmbDlAction.CANCEL, XmbDlAction.DETAILS)
    DownloadState.PAUSED ->
        listOfNotNull(if (entry.supportsPause) XmbDlAction.RESUME else null, XmbDlAction.CANCEL, XmbDlAction.DETAILS)
    DownloadState.QUEUED ->
        listOfNotNull(if (canMoveUp) XmbDlAction.START_NEXT else null, XmbDlAction.CANCEL, XmbDlAction.DETAILS)
    DownloadState.INSTALLED -> listOf(XmbDlAction.LAUNCH, XmbDlAction.DETAILS, XmbDlAction.UNINSTALL)
    DownloadState.FAILED, DownloadState.CANCELLED -> listOf(XmbDlAction.DETAILS, XmbDlAction.DISMISS)
}

private val QUICK_ACTIONS = setOf(XmbDlAction.PAUSE, XmbDlAction.RESUME, XmbDlAction.START_NEXT, XmbDlAction.LAUNCH, XmbDlAction.DISMISS)

/**
 * The one press on a card without stepping into its buttons (X): the first of [xmbDlActions] that is not destructive nor just a
 * look (pause or resume, start next, launch, dismiss). Null when the card has none.
 */
internal fun xmbDlQuickAction(entry: DownloadEntry, canMoveUp: Boolean): XmbDlAction? =
    xmbDlActions(entry, canMoveUp).firstOrNull { it in QUICK_ACTIONS }

/**
 * True when [action] on [entry] wants a second press to confirm: removing a game's files, or giving up a download (which the Epic
 * store asks about in a dialog of its own, so not twice).
 */
internal fun xmbDlNeedsConfirm(entry: DownloadEntry, action: XmbDlAction): Boolean = when (action) {
    XmbDlAction.UNINSTALL -> true
    XmbDlAction.CANCEL -> entry.store != Store.EPIC
    else -> false
}

/** A running download's progress: [fraction] of the bar (0..1) and its [percent] as shown, [hasBytes] when the sizes below are real. */
internal class XmbDlProgress(val fraction: Float, val percent: Int, val hasBytes: Boolean)

/**
 * [e]'s progress. Bytes drive it only when the store feeds byte progress (some bytes done): a store that reports a percentage alone
 * (GOG) uses it, so the bar is not stuck at 0 when the total happens to be known.
 */
internal fun xmbDlProgress(e: DownloadEntry): XmbDlProgress {
    val hasBytes = e.installTotal > 0 && e.installDone > 0
    val fraction = (if (hasBytes) e.installDone.toFloat() / e.installTotal else e.pct / 100f).coerceIn(0f, 1f)
    return XmbDlProgress(fraction, (fraction * 100).toInt(), hasBytes)
}

/**
 * What the Downloads tab's ring shows: how far the download it follows has got ([progress], 0..1) and whether it is running now
 * ([running]: a down arrow in the ring) or nothing runs and it waits (a pause symbol).
 */
internal data class XmbDownloadRing(val progress: Float, val running: Boolean)

/**
 * The ring of the Downloads tab for [entries] (any order), or null when nothing is running, paused or queued. It follows the download at
 * the top of the page's list: the running one (the first, if several run), else the first of the paused ones, else the first queued.
 */
internal fun xmbRingOf(entries: List<DownloadEntry>): XmbDownloadRing? {
    val groups = xmbDlGroups(entries)
    val active = groups.firstOrNull { it.section == XmbDlSection.ACTIVE }?.entries.orEmpty()
    val running = active.firstOrNull { it.state == DownloadState.DOWNLOADING }
    if (running != null) return XmbDownloadRing(xmbDlProgress(running).fraction, running = true)
    val waiting = active.firstOrNull() ?: groups.firstOrNull { it.section == XmbDlSection.QUEUED }?.entries?.firstOrNull() ?: return null
    return XmbDownloadRing(xmbDlProgress(waiting).fraction, running = false)
}

/** "1.2 GB / 3.4 GB" for a store that feeds bytes, else "" (a percentage-only store has no sizes to show). */
internal fun xmbDlSizeText(e: DownloadEntry): String =
    if (xmbDlProgress(e).hasBytes) "${formatDownloadSize(e.installDone)} / ${formatDownloadSize(e.installTotal)}" else ""

/** "12.4 MB/s · ~8 min left" while downloading, with whichever of the two is known; "" when neither. */
internal fun xmbDlSpeedText(e: DownloadEntry): String =
    listOf(formatDownloadSpeed(e.speedBps), formatEta(e.etaSeconds)).filter { it.isNotEmpty() }.joinToString(" · ")
