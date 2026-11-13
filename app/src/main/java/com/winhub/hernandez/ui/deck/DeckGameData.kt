package com.winhub.hernandez.ui.deck

import android.content.Context
import android.text.format.DateUtils
import com.winhub.hernandez.container.Shortcut

// What the Steam-style Home reads of a game for its All Games page and sorts: play stats and the store a game came from.

internal data class PlayStats(val ms: Long, val count: Int, val lastPlayed: Long, val addedAt: Long = 0L) {
    /** What the recent order sorts on, newest first: a new game counts as the latest played until it is really played. */
    val recentKey: Long get() = maxOf(lastPlayed, addedAt)
}

private const val EXTRA_ADDED_AT = "addedAt"

internal fun addedAtOf(s: Shortcut): Long = s.getExtra(EXTRA_ADDED_AT, "").toLongOrNull() ?: s.file.lastModified()

/** The hub remembers only the one game played last, so that game counts as the latest launch. */
internal fun lastPlayedOf(context: Context, s: Shortcut): Long {
    val last = context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE).getString("last_played", null)
    return if (last == s.file.path) System.currentTimeMillis() else 0L
}

internal fun recentKeyOf(s: Shortcut): Long = addedAtOf(s)

internal fun readPlayStats(context: Context, shortcuts: List<Shortcut>): Map<String, PlayStats> {
    val prefs = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
    return shortcuts.associate { s ->
        s.file.path to PlayStats(
            ms = prefs.getLong("${s.name}_playtime", 0L),
            count = prefs.getInt("${s.name}_play_count", 0),
            lastPlayed = lastPlayedOf(context, s),
            addedAt = addedAtOf(s),
        )
    }
}

internal fun playtimeLabel(ms: Long): String {
    val minutes = ms / 60_000L
    return when {
        ms <= 0L -> "Not played yet"
        minutes < 60L -> "${minutes.coerceAtLeast(1L)} min played"
        else -> "${"%.1f".format(ms / 3_600_000.0)} h played"
    }
}

/** "Today", "Yesterday", "3 days ago", "Sep 28"; null when the game has no recorded launch. */
internal fun lastPlayedLabel(context: Context, whenMs: Long): String? {
    if (whenMs <= 0L) return null
    val now = System.currentTimeMillis()
    return when {
        DateUtils.isToday(whenMs) -> "Today"
        DateUtils.isToday(whenMs + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        now - whenMs < 7 * DateUtils.DAY_IN_MILLIS -> "${(now - whenMs) / DateUtils.DAY_IN_MILLIS} days ago"
        else -> DateUtils.formatDateTime(context, whenMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}

/** The label of the store a game came from; "Added by me" for a game no store owns. */
internal fun storeLabelOf(s: Shortcut): String =
    DeckStore.entries.firstOrNull { it.owns(s) }?.label ?: "Added by me"
