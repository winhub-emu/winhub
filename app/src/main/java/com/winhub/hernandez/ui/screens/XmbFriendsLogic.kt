package com.winhub.hernandez.ui.screens

import com.winhub.hernandez.store.SteamFriendsStore

// The pure rules of the XMB Home's Friends tab (ui/deck/XmbFriendsPage.kt), kept free of Compose and Android so they can be unit-tested: how the
// roster is grouped into sections and flattened into the page's rows, how the rows fall into lines (headings across the full width, the
// friends and the requests as cards two to a line), where the D-pad's cursor moves in them, and which actions a friend offers.

/** The sections of the roster, top to bottom. "Unknown" only appears while a SteamLite game holds the session (see SteamFriendsStore). */
internal val XMB_FRIENDS_SECTIONS: List<SteamFriendsStore.Presence> = listOf(
    SteamFriendsStore.Presence.IN_GAME,
    SteamFriendsStore.Presence.ONLINE,
    SteamFriendsStore.Presence.AWAY,
    SteamFriendsStore.Presence.OFFLINE,
    SteamFriendsStore.Presence.UNKNOWN,
)

/** The toolbar's buttons, left to right: Add friend, Settings. */
internal const val XMB_FRIENDS_TOOLBAR_BUTTONS = 2

/** One row of the page's list. [key] is stable across updates (the cursor follows it); [selectable] is whether the cursor can rest on it. */
internal sealed interface XmbFriendsRow {
    val key: String
    val selectable: Boolean

    /** The top row: the counts and the Add friend / Settings buttons. */
    object Toolbar : XmbFriendsRow {
        override val key = "toolbar"
        override val selectable = true
    }

    /** The heading over the friend requests. */
    data class RequestsHeader(val count: Int) : XmbFriendsRow {
        override val key = "requests"
        override val selectable = false
    }

    /** Someone asking to be our friend: Accept / Decline. */
    data class Incoming(val friend: SteamFriendsStore.SteamFriend) : XmbFriendsRow {
        override val key = "in:${friend.steamId}"
        override val selectable = true
    }

    /** A request we sent: Cancel. */
    data class Outgoing(val friend: SteamFriendsStore.SteamFriend) : XmbFriendsRow {
        override val key = "out:${friend.steamId}"
        override val selectable = true
    }

    /** A section's heading (In game, Online, ...), with how many friends it holds; the cursor folds it away. */
    data class Header(val presence: SteamFriendsStore.Presence, val count: Int, val collapsed: Boolean) : XmbFriendsRow {
        override val key = "hdr:${presence.name}"
        override val selectable = true
    }

    data class Friend(val friend: SteamFriendsStore.SteamFriend) : XmbFriendsRow {
        override val key = "f:${friend.steamId}"
        override val selectable = true
    }
}

private fun byName(friends: List<SteamFriendsStore.SteamFriend>): List<SteamFriendsStore.SteamFriend> =
    friends.sortedBy { it.displayName.lowercase() }

/**
 * The page's rows: the toolbar, then (when there are any) the friend requests under their heading, then a heading and the friends of each
 * non-empty section in [XMB_FRIENDS_SECTIONS] order, by name. A section in [collapsed] keeps its heading (with its count) but lists no friends.
 */
internal fun xmbFriendsRows(
    friends: List<SteamFriendsStore.SteamFriend>,
    incoming: List<SteamFriendsStore.SteamFriend>,
    outgoing: List<SteamFriendsStore.SteamFriend>,
    collapsed: Set<SteamFriendsStore.Presence>,
): List<XmbFriendsRow> {
    val rows = ArrayList<XmbFriendsRow>()
    rows.add(XmbFriendsRow.Toolbar)
    if (incoming.isNotEmpty() || outgoing.isNotEmpty()) {
        rows.add(XmbFriendsRow.RequestsHeader(incoming.size + outgoing.size))
        byName(incoming).forEach { rows.add(XmbFriendsRow.Incoming(it)) }
        byName(outgoing).forEach { rows.add(XmbFriendsRow.Outgoing(it)) }
    }
    val groups = friends.groupBy { it.presence }
    for (presence in XMB_FRIENDS_SECTIONS) {
        val group = groups[presence] ?: continue
        val folded = presence in collapsed
        rows.add(XmbFriendsRow.Header(presence, group.size, folded))
        if (!folded) byName(group).forEach { rows.add(XmbFriendsRow.Friend(it)) }
    }
    return rows
}

/** True when the list shows nothing but its toolbar: no friend and no request. */
internal fun xmbFriendsEmpty(rows: List<XmbFriendsRow>): Boolean = rows.all { it === XmbFriendsRow.Toolbar }

/**
 * The index the cursor is on: where [key] is now, else (the row went: a friend removed, a request answered) the nearest selectable row at or
 * after [lastIndex], else the nearest before it, else 0.
 */
internal fun xmbFriendsResolve(rows: List<XmbFriendsRow>, key: String, lastIndex: Int): Int {
    if (rows.isEmpty()) return 0
    val at = rows.indexOfFirst { it.key == key }
    if (at >= 0) return at
    val from = lastIndex.coerceIn(0, rows.lastIndex)
    for (i in from..rows.lastIndex) if (rows[i].selectable) return i
    for (i in from downTo 0) if (rows[i].selectable) return i
    return 0
}

/**
 * The cursor's state on a row that the page keeps beside the row's key: [sub] the button of the row it is on, [openKey] the row whose
 * buttons hold the cursor (null: none) and [confirmRemove] Remove waiting for its second press.
 */
internal data class XmbFriendsCursor(val key: String, val sub: Int, val openKey: String?, val confirmRemove: Boolean)

/**
 * [cursor] made to agree with [rows] after they changed under it. [index] is where the cursor is now ([xmbFriendsResolve]'s answer): when the
 * row there is not the one [XmbFriendsCursor.key] names (that friend went: removed, a request answered) the cursor takes that row's key and
 * starts afresh on it (first button, nothing open, no Remove waiting); else it would keep the old button number (the toolbar's Settings, say)
 * on a row with others. On the same row the button is only clamped into the row's buttons (a friend leaving their game has one action
 * fewer) and a Remove that was waiting is dropped when the button moved.
 */
internal fun xmbFriendsNormalise(rows: List<XmbFriendsRow>, cursor: XmbFriendsCursor, index: Int): XmbFriendsCursor {
    val row = rows.getOrNull(index) ?: return cursor
    if (row.key != cursor.key) return XmbFriendsCursor(row.key, 0, null, false)
    val open = cursor.openKey == row.key
    val sub = cursor.sub.coerceIn(0, xmbFriendsButtonCount(row, open) - 1)
    return XmbFriendsCursor(row.key, sub, if (open) row.key else null, cursor.confirmRemove && sub == cursor.sub)
}

/** True for the rows drawn as a card, two to a line: a friend and the two kinds of request. The toolbar and the headings span the full width. */
internal fun xmbFriendsIsCard(row: XmbFriendsRow): Boolean =
    row is XmbFriendsRow.Friend || row is XmbFriendsRow.Incoming || row is XmbFriendsRow.Outgoing

/**
 * The lines of [rows] with the cards [columns] to a line: the toolbar and each heading alone on a line, the cards between two of them
 * (the requests, or one section's friends) filling lines, the last of a run maybe shorter. Each line holds indexes into [rows].
 */
internal fun xmbFriendsLines(rows: List<XmbFriendsRow>, columns: Int): List<List<Int>> =
    xmbTwoColLines(rows.map { !xmbFriendsIsCard(it) }, columns)

/**
 * Where Up ([down] false) or Down from row [from] goes: the nearest line above or below that holds a selectable row (the requests' heading
 * only labels, so it is skipped), on the column [wantCol] or the last card of a shorter line. Up from the first line is
 * [XMB_TWO_COL_TO_BAR]; Down from the last stays.
 */
internal fun xmbFriendsVertical(rows: List<XmbFriendsRow>, lines: List<List<Int>>, from: Int, down: Boolean, wantCol: Int): Int =
    xmbTwoColVertical(lines, from, down, wantCol) { i -> rows.getOrNull(i)?.selectable == true }

/**
 * True when Left / Right on [row] move between its buttons rather than between the columns: always on the toolbar (Add friend, Settings),
 * and on a friend's or a request's card once A has stepped into its [open] buttons.
 */
internal fun xmbFriendsSideIsButtons(row: XmbFriendsRow, open: Boolean): Boolean = when (row) {
    is XmbFriendsRow.Toolbar -> true
    is XmbFriendsRow.Friend, is XmbFriendsRow.Incoming -> open
    is XmbFriendsRow.Outgoing, is XmbFriendsRow.Header, is XmbFriendsRow.RequestsHeader -> false
}

/** The buttons of an open friend's action row, left to right. */
internal enum class XmbFriendAction { MESSAGE, GAME, PROFILE, REMOVE }

/** The actions [friend] offers: Message, the game's page while in a game, the profile and Remove (which asks twice). */
internal fun xmbFriendActions(friend: SteamFriendsStore.SteamFriend): List<XmbFriendAction> {
    val inGame = friend.presence == SteamFriendsStore.Presence.IN_GAME && friend.gameAppId != 0
    return if (inGame) {
        listOf(XmbFriendAction.MESSAGE, XmbFriendAction.GAME, XmbFriendAction.PROFILE, XmbFriendAction.REMOVE)
    } else {
        listOf(XmbFriendAction.MESSAGE, XmbFriendAction.PROFILE, XmbFriendAction.REMOVE)
    }
}

/**
 * How many buttons the row under the cursor has side by side (the Left / Right range while they hold it): 1 for a row without inner
 * buttons. [open]: A has stepped into the row's buttons (a friend's actions, an incoming request's Accept / Decline).
 */
internal fun xmbFriendsButtonCount(row: XmbFriendsRow, open: Boolean): Int = when (row) {
    is XmbFriendsRow.Toolbar -> XMB_FRIENDS_TOOLBAR_BUTTONS
    is XmbFriendsRow.Incoming -> if (open) 2 else 1
    is XmbFriendsRow.Outgoing -> 1
    is XmbFriendsRow.Friend -> if (open) xmbFriendActions(row.friend).size else 1
    is XmbFriendsRow.Header, is XmbFriendsRow.RequestsHeader -> 1
}
