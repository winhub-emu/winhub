package com.winhub.hernandez.ui.screens.adrenodownload

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences-backed persistence for the driver source list (issue #160):
 *  - a set of DISABLED built-in source names (built-ins the user hid), and
 *  - a list of user-added CUSTOM sources (name + one feed: a JSON url or a GitHub owner/repo).
 *
 * Everything is stored as JSON strings so the shapes can grow without a migration.
 */
class DriverSourceStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Built-in enable/disable ──────────────────────────────────────────────
    fun disabledBuiltIns(): Set<String> {
        val raw = prefs.getString(KEY_DISABLED, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    fun setBuiltInEnabled(name: String, enabled: Boolean) {
        val cur = disabledBuiltIns().toMutableSet()
        if (enabled) cur.remove(name) else cur.add(name)
        prefs.edit().putString(KEY_DISABLED, JSONArray(cur.toList()).toString()).apply()
    }

    // ── Custom sources ───────────────────────────────────────────────────────
    fun customSources(): List<RemoteDriverSource> {
        val arr = rawCustomArray()
        val out = ArrayList<RemoteDriverSource>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name", "").trim()
            if (name.isEmpty()) continue
            val feed = when (o.optString("type", "json")) {
                "github" -> {
                    val owner = o.optString("owner", "").trim()
                    val repo = o.optString("repo", "").trim()
                    if (owner.isEmpty() || repo.isEmpty()) continue
                    DriverFeed.GithubReleases(owner, repo)
                }
                else -> {
                    val url = o.optString("url", "").trim()
                    if (url.isEmpty()) continue
                    DriverFeed.Json(url)
                }
            }
            out.add(RemoteDriverSource(name, listOf(feed), builtIn = false))
        }
        return out
    }

    /** Adds a custom source. No-ops on a blank name or a name already used (built-in or custom). */
    fun addCustom(name: String, feed: DriverFeed) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val taken = (customSources().map { it.name } + (DriverSources.BUILT_IN + DriverSources.MALI_BUILT_IN).map { it.name })
            .map { it.lowercase() }
        if (trimmed.lowercase() in taken) return

        val o = JSONObject().put("name", trimmed)
        when (feed) {
            is DriverFeed.Json -> o.put("type", "json").put("url", feed.url.trim())
            is DriverFeed.GithubReleases -> o.put("type", "github")
                .put("owner", feed.owner.trim())
                .put("repo", feed.repo.trim())
        }
        val arr = rawCustomArray().put(o)
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }

    fun removeCustom(name: String) {
        val arr = rawCustomArray()
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("name", "").equals(name, ignoreCase = true)) continue
            kept.put(o)
        }
        prefs.edit().putString(KEY_CUSTOM, kept.toString()).apply()
    }

    private fun rawCustomArray(): JSONArray {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (_: Throwable) { JSONArray() }
    }

    companion object {
        private const val PREFS = "adreno_driver_sources"
        private const val KEY_DISABLED = "disabled_builtins"
        private const val KEY_CUSTOM = "custom_sources"

        /**
         * Parses a GitHub `owner`/`repo` pair from two fields, tolerating a full URL (or `owner/repo`)
         * pasted into the owner field. Returns null when it can't find both parts.
         */
        fun parseGithubRepo(ownerInput: String, repoInput: String): Pair<String, String>? {
            val o = ownerInput.trim()
            val r = repoInput.trim()
            val combined = if (o.contains("/")) o else "$o/$r"
            val cleaned = combined
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .removePrefix("github.com/")
                .removeSuffix(".git")
                .trim('/')
            val parts = cleaned.split("/").filter { it.isNotBlank() }
            return if (parts.size >= 2) parts[0] to parts[1] else null
        }
    }
}
