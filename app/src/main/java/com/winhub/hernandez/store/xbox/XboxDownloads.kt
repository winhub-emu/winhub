package com.winhub.hernandez.store.xbox

import android.content.Context
import com.winhub.hernandez.store.download.DownloadEntry
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadScope
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreDownloadHooks
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Xbox installs (adapted from DroidDeck's XboxInstaller, GPL-3.0): `xodus-cli resolve-package` picks the PC
 * package, `xodus-cli streaming` downloads it with fresh licensing and reports NEXUS_PROGRESS lines, which
 * feed the shared Download Manager through [StoreDownloadHooks]. Games go to imagefs/xbox_games/<productId>,
 * which every container sees as Z:\xbox_games\<productId>. A game becomes installed only when the backend
 * reports completion and its MicrosoftGame.config names a PC executable.
 */
object XboxDownloads {
    private class Job(val cancelled: AtomicBoolean = AtomicBoolean(false)) {
        @Volatile var child: Xodus.Child? = null
        @Volatile var deleteOnCancel = false
    }

    private val jobs = ConcurrentHashMap<String, Job>()
    private val seeded = AtomicBoolean(false)

    fun root(app: Context): File = File(app.filesDir, "imagefs/xbox_games").apply { mkdirs() }
    fun folder(app: Context, id: String): File = File(root(app), id)
    fun isRunning(id: String) = jobs.containsKey(id)

    /** One PC package the offer contains (DLC and other kinds are left out). */
    data class PackageChoice(val contentId: String, val label: String, val json: JSONObject)

    /** The PC game packages of an offer. One means install straight away; several need the user to choose. */
    fun resolve(app: Context, game: XboxGame): List<PackageChoice> {
        val plan = Xodus.run(app, listOf("resolve-package", game.id, "--json", "--market", game.market))
        require(plan.getInt("schema") == 1 && plan.getString("rootProductId") == game.id)
        val choices = XboxCatalog.objects(plan.getJSONArray("choices"))
            .filter { it.optBoolean("supported") && it.optString("kind") == "Game" }
            .map { c -> PackageChoice(c.getString("contentId"),
                listOf(c.optString("title"), c.optString("packageName"), c.optString("version")).filter { it.isNotBlank() && it != "null" }
                    .joinToString(" · ").ifBlank { c.getString("contentId") }, c) }
        check(choices.isNotEmpty()) { "This offer has no supported PC package." }
        return choices
    }

    /** Starts (or resumes, with the same folder) a download on the shared download scope. */
    fun start(app: Context, game: XboxGame, contentId: String) {
        require(contentId.matches(Regex("[a-fA-F0-9-]{36}"))) { "Select a PC game package before installing." }
        if (jobs.containsKey(game.id)) return
        val job = Job()
        jobs[game.id] = job
        val folder = folder(app, game.id)
        val fresh = !XboxInstallState.isInstalled(app, game.id)
        job.deleteOnCancel = fresh
        StoreDownloadHooks.registerDownload(Store.XBOX, game.id, game.title, cover = game.posterUrl ?: game.heroUrl,
            supportsPause = false, installTotal = game.sizeBytes, cancel = { cancel(game.id) })
        XboxDiag.log(app, "download start: ${game.id} fresh=$fresh")
        DownloadScope.io.launch {
            try {
                check(!XboxRuntime.isActive(game.id)) { "Close this Xbox game before updating it." }
                check(XboxAccountStore.signedInAs(app) != null) { "Sign in to Xbox before installing." }
                folder.mkdirs()
                var complete = false
                var sequence = -1L
                // Ceiling of the backend's adaptive request window, from the shared store speed tier (6/16/32/96).
                val window = com.winhub.hernandez.store.StoreDownloadTier.config(app).maxNetworkWindow
                XboxDiag.log(app, "download window ceiling $window")
                Xodus.start(app, listOf("streaming", game.id, folder.path, "--market", game.market,
                    "--progress-json", "--control-stdin", "--selected-content-id", contentId, "--parallel", window.toString())).use { process ->
                    job.child = process
                    val diagnostics = Xodus.diagnostics(app, process)
                    if (job.cancelled.get()) process.process.destroy()
                    process.process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        if (!line.startsWith("NEXUS_PROGRESS ")) return@forEach
                        val progress = JSONObject(line.removePrefix("NEXUS_PROGRESS "))
                        if (progress.optInt("schema") != 1 || progress.optLong("sequence") <= sequence) return@forEach
                        sequence = progress.getLong("sequence")
                        when (progress.optString("phase")) {
                            "complete" -> complete = true
                            "downloading", "download", "transfer" -> {
                                // After a resume the backend counts only what is still to fetch: completedBytes of totalBytes remaining.
                                val done = progress.optLong("completedBytes"); val total = progress.optLong("totalBytes").takeIf { it > 0 } ?: game.sizeBytes
                                val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
                                val rate = progress.optLong("bytesPerSecond").coerceAtLeast(0L)
                                val eta = if (rate > 0 && total > done) (total - done) / rate else -1L
                                StoreDownloadHooks.tick(Store.XBOX, game.id, pct, installDone = done, installTotal = total,
                                    speedBps = rate, etaSeconds = eta)
                            }
                            else -> Unit
                        }
                    } }
                    check(process.process.waitFor(15, TimeUnit.SECONDS)) { "Xbox installer did not exit." }
                    diagnostics.join(2000)
                    if (job.cancelled.get()) return@use
                    val code = process.process.exitValue()
                    XboxDiag.log(app, "download ${game.id} exited $code complete=$complete")
                    check(code == 0 && complete) { process.lastError?.let(::explain) ?: "Xbox download did not complete. Try again to resume with fresh licenses." }
                }
                if (job.cancelled.get()) {
                    if (job.deleteOnCancel) folder.deleteRecursively()
                    StoreDownloadHooks.markCancelled(Store.XBOX, game.id)
                    return@launch
                }
                val exe = XboxManifest.executable(folder)
                val info = XboxManifest.read(folder)
                XboxInstallState.put(app, XboxInstallState.Installed(game.id, game.title, folder.path, exe, info.version, contentId,
                    game.market, game.access, game.posterUrl, game.heroUrl, System.currentTimeMillis()))
                StoreDownloadHooks.markInstalled(Store.XBOX, game.id, folder.path, game.sizeBytes)
                // A Games entry added before the download now launches the installed game.
                XboxCloudShortcuts.onInstalled(app, game.id, java.io.File(folder, exe).path)
                XboxDiag.log(app, "installed ${game.id} exe=${exe.substringAfterLast('/')} version=${info.version}")
            } catch (e: Exception) {
                XboxDiag.log(app, "download ${game.id} failed: ${e.javaClass.simpleName}")
                if (job.cancelled.get()) {
                    if (job.deleteOnCancel) folder.deleteRecursively()
                    StoreDownloadHooks.markCancelled(Store.XBOX, game.id)
                } else StoreDownloadHooks.markFailed(Store.XBOX, game.id, e.message ?: "Xbox download failed.")
            } finally {
                job.child = null
                jobs.remove(game.id)
            }
        }
    }

    private fun explain(error: String): String = when {
        error.startsWith("Package was not found") -> "Microsoft found no PC package of this game for your account."
        error.startsWith("not enough free disk space") -> "Not enough free space for this game."
        else -> "Xbox: $error"
    }

    /** Stops a download: asks the backend to pause (it keeps verified checkpoints), then ends it. */
    fun cancel(id: String) {
        val job = jobs[id] ?: return
        job.cancelled.set(true)
        val process = job.child ?: return
        Thread({
            runCatching { process.send(JSONObject().put("schema", 1).put("command", "pause")) }
            Thread.sleep(1500)
            process.close()
        }, "xbox-download-stop").start()
    }

    /** Removes an installed game's files and records. */
    fun uninstall(app: Context, id: String) {
        check(!XboxRuntime.isActive(id)) { "Close this Xbox game first." }
        val record = XboxInstallState.get(app, id)
        val dir = record?.let { File(it.folder) } ?: folder(app, id)
        if (dir.canonicalPath.startsWith(root(app).canonicalPath + File.separator)) dir.deleteRecursively()
        XboxInstallState.remove(app, id)
        StoreDownloadHooks.markUninstalled(Store.XBOX, id)
        XboxDiag.log(app, "uninstalled $id")
    }

    /** Puts installed Xbox games into the Download Manager's library once per process. */
    fun seed(app: Context) {
        if (!seeded.compareAndSet(false, true)) return
        runCatching {
            for (g in XboxInstallState.all(app)) {
                if (!File(g.folder, g.exe).isFile) continue
                if (DownloadRegistry.get("${Store.XBOX}:${g.id}") != null) continue
                DownloadRegistry.upsert(DownloadEntry(store = Store.XBOX, id = g.id, name = g.title, cover = g.posterUrl ?: g.heroUrl,
                    state = DownloadState.INSTALLED, pct = 100, installPath = g.folder))
            }
        }
    }
}
