package com.winhub.hernandez.ui.screens.adrenodownload

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RemoteDriverRepository(private val context: Context) {

    private val cacheDir: File by lazy {
        File(context.cacheDir, "adreno_driver_dl").apply { if (!exists()) mkdirs() }
    }

    /**
     * Fetches every feed of [source] IN ORDER and concatenates them (feed 1 on top), deduped by download
     * URL. A feed that fails (network / rate-limit / HTTP≠2xx) is SKIPPED, not fatal — so e.g. Maxes MTR's
     * curated json still lists even if the GitHub API is down. Fails only when EVERY feed failed.
     */
    suspend fun fetchEntries(source: RemoteDriverSource): Result<List<RemoteDriverEntry>> =
        withContext(Dispatchers.IO) {
            val merged = LinkedHashMap<String, RemoteDriverEntry>()
            var anySuccess = false
            var lastError: Throwable? = null
            for (feed in source.feeds) {
                try {
                    val feedEntries = when (feed) {
                        is DriverFeed.Json -> fetchJsonFeed(source.name, feed.url)
                        is DriverFeed.GithubReleases -> fetchGithubReleasesFeed(source.name, feed.owner, feed.repo, feed.assetPattern)
                    }
                    anySuccess = true
                    for (e in feedEntries) if (!merged.containsKey(e.downloadUrl)) merged[e.downloadUrl] = e
                } catch (t: Throwable) {
                    Log.w(TAG, "feed failed for ${source.name}", t)
                    lastError = t
                }
            }
            if (!anySuccess) Result.failure(lastError ?: RuntimeException("No feeds for ${source.name}"))
            else Result.success(merged.values.toList())
        }

    /** The existing json format: a JSON array of `{verName, remoteUrl}`, keeping only `.zip` urls. */
    private fun fetchJsonFeed(sourceName: String, url: String): List<RemoteDriverEntry> {
        val body = httpGetText(url)
        val arr = JSONArray(body)
        val out = ArrayList<RemoteDriverEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val remote = o.optString("remoteUrl", "")
            if (!remote.endsWith(".zip", ignoreCase = true)) continue
            val verName = o.optString("verName", "").ifBlank {
                remote.substringAfterLast('/').removeSuffix(".zip")
            }
            out.add(RemoteDriverEntry(source = sourceName, displayName = verName, downloadUrl = remote))
        }
        return out
    }

    /**
     * GitHub releases: each `.zip` asset across all releases (newest-first, as the API returns them),
     * only those matching [assetPattern] when one is given.
     */
    private fun fetchGithubReleasesFeed(
        sourceName: String,
        owner: String,
        repo: String,
        assetPattern: String? = null,
    ): List<RemoteDriverEntry> {
        val filter = assetPattern?.let { Regex(it) }
        val body = httpGetText(
            "https://api.github.com/repos/$owner/$repo/releases?per_page=100",
            accept = "application/vnd.github+json",
        )
        val releases = JSONArray(body)
        val out = ArrayList<RemoteDriverEntry>()
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name", "")
                if (!name.endsWith(".zip", ignoreCase = true)) continue
                if (filter != null && !filter.containsMatchIn(name)) continue
                val dl = a.optString("browser_download_url", "")
                if (dl.isEmpty()) continue
                out.add(
                    RemoteDriverEntry(
                        source = sourceName,
                        displayName = name.dropLast(4), // strip ".zip"
                        downloadUrl = dl,
                    )
                )
            }
        }
        return out
    }

    /** Downloads the .zip to internal cache. Returns the local file. progress(0..100). */
    suspend fun downloadEntry(
        entry: RemoteDriverEntry,
        progress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val safeName = entry.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".zip"
            val outFile = File(cacheDir, "${System.currentTimeMillis()}_$safeName")
            val conn = (URL(entry.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "star-android")
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) {
                    return@withContext Result.failure(RuntimeException("HTTP ${conn.responseCode}"))
                }
                val totalBytes = conn.contentLengthLong
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(64 * 1024)
                        var written = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            written += n
                            if (totalBytes > 0) {
                                val pct = ((written * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    progress(pct)
                                }
                            }
                        }
                    }
                }
                progress(100)
                Result.success(outFile)
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "downloadEntry failed for ${entry.displayName}", t)
            Result.failure(t)
        }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun httpGetText(url: String, accept: String? = null): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "star-android")
            if (accept != null) setRequestProperty("Accept", accept)
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "RemoteDriverRepo"
    }
}
