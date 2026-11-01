package com.winhub.hernandez.store.xbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last synced Xbox library, per account and market (ported from DroidDeck, GPL-3.0), so the Library
 * tab shows at once and refreshes quietly when older than 15 minutes.
 */
internal object XboxLibraryCache {
    data class Snapshot(val games: List<XboxGame>, val savedAt: Long) {
        fun needsRefresh(force: Boolean, now: Long = System.currentTimeMillis()): Boolean =
            force || now - savedAt !in 0 until 15 * 60 * 1000L
    }

    private fun prefs(app: Context) = app.getSharedPreferences("xbox", Context.MODE_PRIVATE)

    fun read(app: Context, account: String, market: String): Snapshot? =
        decode(prefs(app).getString("library", null), account, market)

    fun write(app: Context, account: String, market: String, games: List<XboxGame>) {
        check(prefs(app).edit().putString("library", encode(account, market, games)).commit())
    }

    fun clear(app: Context) { prefs(app).edit().remove("library").commit() }

    internal fun encode(account: String, market: String, games: List<XboxGame>, now: Long = System.currentTimeMillis()): String =
        JSONObject().put("schema", 2).put("account", account).put("market", market).put("savedAt", now)
            .put("games", JSONArray().apply {
                for (game in games) put(JSONObject().put("id", game.id).put("title", game.title)
                    .put("image", game.heroUrl).put("poster", game.posterUrl).put("description", game.description)
                    .put("developer", game.developer).put("publisher", game.publisher).put("size", game.sizeBytes)
                    .put("access", game.access).put("pc", game.pc).put("cloud", game.cloud).put("cloudId", game.cloudId))
            }).toString()

    internal fun decode(text: String?, account: String, market: String, now: Long = System.currentTimeMillis()): Snapshot? = runCatching {
        require(text != null && text.length <= 8_000_000)
        val json = JSONObject(text)
        require(json.getInt("schema") == 2 && json.getString("account") == account && json.getString("market") == market)
        val savedAt = json.getLong("savedAt")
        require(savedAt > 0 && savedAt <= now + 60_000)
        val rows = json.getJSONArray("games")
        require(rows.length() <= 10_000)
        val games = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val id = row.getString("id")
            val title = row.getString("title")
            val access = row.getString("access")
            require(id.matches(Regex("[A-Z0-9]{12}")) && title.isNotBlank() && access in setOf("owned", "gamepass"))
            XboxGame(id, title, row.optString("image").takeIf { it.isNotEmpty() }, row.optString("poster").takeIf { it.isNotEmpty() },
                row.optString("publisher"), row.optString("developer"), row.optString("description"), row.optLong("size"), market, access,
                row.getBoolean("pc"), row.getBoolean("cloud"), row.optString("cloudId").takeIf { it.matches(Regex("[A-Z0-9]{12}")) })
        }
        require(games.map { it.id }.distinct().size == games.size)
        Snapshot(games, savedAt)
    }.getOrNull()
}
