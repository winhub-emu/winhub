package com.winhub.hernandez.ui.deck

import com.winhub.hernandez.container.Shortcut

// How the library is ordered. The Deck filter dialog's sort chips and the XMB All Games page share this, so the two can't
// drift; the comparisons work on plain keys, so they don't need a Shortcut.

internal enum class LibrarySort(val label: String) {
    NAME("Name A–Z"),
    NAME_DESC("Name Z–A"),
    RECENT("Last played"),
    PLAYTIME("Most played"),
    CONTAINER("Container"),
    STORE("Store"),
}

/** What a sort compares for one game. Read once per game, so ordering a big library does no lookups inside the comparator. */
internal class GameSortKey(
    name: String,
    /** The "recent" order's value: the later of the last launch and the time the game was added. */
    val recent: Long,
    val playMs: Long,
    container: String,
    val store: String,
    /** Listed before everything else in every order (the Windows Steam client). */
    val pinned: Boolean = false,
) {
    val nameKey: String = name.lowercase()
    val containerKey: String = container.lowercase()
}

// The Stores tab's order, then the entries no store owns. The labels are the ones storeLabelOf returns.
private val STORE_ORDER = listOf("Windows Steam", "Steam", "Epic Games", "GOG", "Amazon Games", "Xbox", "Android", "Linux", "Added by me")

/** Where a [storeLabelOf] label sorts; a label nobody knows goes last. */
internal fun storeRank(label: String): Int = STORE_ORDER.indexOf(label).let { if (it < 0) STORE_ORDER.size else it }

/** [list] ordered by [sort]; every order falls back to the name, so equal keys keep a stable, readable order. */
internal fun <T> sortGames(list: List<T>, sort: LibrarySort, key: (T) -> GameSortKey): List<T> {
    val keyed = list.map { it to key(it) }
    val order: Comparator<Pair<T, GameSortKey>> = when (sort) {
        LibrarySort.NAME -> compareBy<Pair<T, GameSortKey>> { it.second.nameKey }
        LibrarySort.NAME_DESC -> compareByDescending<Pair<T, GameSortKey>> { it.second.nameKey }
        LibrarySort.RECENT -> compareByDescending<Pair<T, GameSortKey>> { it.second.recent }.thenBy { it.second.nameKey }
        LibrarySort.PLAYTIME -> compareByDescending<Pair<T, GameSortKey>> { it.second.playMs }.thenBy { it.second.nameKey }
        LibrarySort.CONTAINER -> compareBy<Pair<T, GameSortKey>> { it.second.containerKey }.thenBy { it.second.nameKey }
        LibrarySort.STORE -> compareBy<Pair<T, GameSortKey>> { storeRank(it.second.store) }.thenBy { it.second.nameKey }
    }
    val pinnedFirst = compareByDescending<Pair<T, GameSortKey>> { it.second.pinned }
    return keyed.sortedWith(pinnedFirst.then(order)).map { it.first }
}

/** A shortcut's sort key, from the play stats read for the library ([readPlayStats]). */
internal fun gameSortKeyOf(s: Shortcut, stats: Map<String, PlayStats>): GameSortKey {
    val st = stats[s.file.path]
    return GameSortKey(
        name = s.name,
        recent = st?.recentKey ?: recentKeyOf(s),
        playMs = st?.ms ?: 0L,
        container = s.container?.name.orEmpty(),
        store = storeLabelOf(s),
    )
}
