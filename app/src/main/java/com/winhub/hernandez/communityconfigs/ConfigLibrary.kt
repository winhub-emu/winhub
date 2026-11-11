package com.winhub.hernandez.communityconfigs

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The curated config library published with the Social Hub site (`configs/` in winhub-emu/social-hub).
 * Reads a compact games index first, so the app lists games and only downloads the one config the
 * user picks. Served from GitHub Pages: cached, no API rate limit, no sign-in.
 *
 * Calls block; use from a background context ([load] and [fetchConfig] switch to IO themselves).
 */
object ConfigLibrary {

    private const val TAG = "ConfigLibrary"
    private const val BASE = "https://winhub-emu.github.io/social-hub/configs/"
    private const val CACHE_FILE = "config_library_index.json"

    /** The index changes rarely; a stale copy is also used when the site can't be reached. */
    private const val FRESH_MS = 6L * 60 * 60 * 1000

    data class Entry(
        val file: String,
        val device: String,
        val soc: String,
        val driver: String,
        val dxvk: String,
        val vkd3d: String,
        val fex: String,
        val proton: String,
        val resolution: String,
    )

    data class Game(val id: String, val name: String, val configs: List<Entry>)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** All games, from the cache when it is fresh, else the network, else a stale cache. */
    suspend fun load(context: Context, force: Boolean = false): Result<List<Game>> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, CACHE_FILE)
        val fresh = cache.isFile && System.currentTimeMillis() - cache.lastModified() < FRESH_MS
        if (fresh && !force) readCache(cache)?.let { return@withContext Result.success(it) }

        val body = get(BASE + "index.json")
        val games = body?.let(::parse)
        if (body != null && games != null) {
            try {
                cache.writeText(body)
            } catch (e: IOException) {
                Log.w(TAG, "couldn't cache the index", e)
            }
            return@withContext Result.success(games)
        }
        readCache(cache)?.let { return@withContext Result.success(it) }
        Result.failure(IOException("Couldn't reach the config library."))
    }

    /** The raw config JSON for one entry, or null when offline or the file is gone. */
    suspend fun fetchConfig(gameId: String, file: String): String? = withContext(Dispatchers.IO) {
        get(BASE + Uri.encode(gameId) + "/" + Uri.encode(file))
    }

    private fun readCache(file: File): List<Game>? {
        if (!file.isFile) return null
        return try {
            parse(file.readText())
        } catch (e: IOException) {
            Log.w(TAG, "couldn't read the cached index", e)
            null
        }
    }

    private fun parse(text: String): List<Game>? = try {
        val arr = JSONArray(text)
        val out = ArrayList<Game>(arr.length())
        for (i in 0 until arr.length()) {
            val g = arr.optJSONObject(i) ?: continue
            val id = g.optString("id")
            val list = g.optJSONArray("configs") ?: continue
            if (id.isEmpty() || list.length() == 0) continue
            val entries = ArrayList<Entry>(list.length())
            for (j in 0 until list.length()) {
                val c = list.optJSONObject(j) ?: continue
                val file = c.optString("f")
                if (file.isEmpty()) continue
                entries += Entry(
                    file = file,
                    device = c.optString("dev"),
                    soc = c.optString("soc"),
                    driver = c.optString("drv"),
                    dxvk = c.optString("dxvk"),
                    vkd3d = c.optString("vkd3d"),
                    fex = c.optString("fex"),
                    proton = c.optString("proton"),
                    resolution = c.optString("res"),
                )
            }
            if (entries.isNotEmpty()) out += Game(id, g.optString("name").ifEmpty { id }, entries)
        }
        out
    } catch (e: JSONException) {
        Log.w(TAG, "index is not valid JSON", e)
        null
    }

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url).header("User-Agent", "WinHub-ConfigLibrary").build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else {
                Log.w(TAG, "GET $url -> ${r.code}")
                null
            }
        }
    } catch (e: IOException) {
        Log.w(TAG, "GET $url failed", e)
        null
    }
}
