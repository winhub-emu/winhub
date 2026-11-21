package com.winhub.hernandez.store

import android.util.Log
import com.winhub.hernandez.container.Container
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Ubisoft's own Ubisoft Connect installer, fetched once for a game that needs Connect but bundles no installer
 * (Epic builds of Assassin's Creed Syndicate and friends). Store-neutral: it knows nothing about Epic or Steam, so
 * any launch gate can use it ([UbisoftSupport.startSetupWithDownload] runs it).
 *
 * The file goes to the container's own `C:` drive (`C:\bl_installscript\ubisoft_installer\`): the setup stage can run
 * a `C:` path as it is, with no copy of the 251 MB. It is written as `.part` and only renamed once it checks out, so
 * a file under the final name is always a whole one and is reused instead of downloaded again. [deleteDownloaded]
 * removes it. The setup session's own batch file deletes it as soon as the installer has exited, whatever the outcome
 * ([cleanupBatchLines]); [UbisoftSupport.epicSetupFor] and [UbisoftSupport.steamSetupFor] also call it for a container
 * that has Connect (one `exists()` check when there is nothing to remove).
 *
 * There is no published hash, so integrity rests on HTTPS to Ubisoft's CDN plus these checks: HTTP 200, a Content-Length
 * between [MIN_PLAUSIBLE_BYTES] and [MAX_BYTES], the final size equal to it, and an `MZ` (Windows executable) header.
 * Only [INSTALLER_URL] is ever requested; a redirect is followed only to another https URL on the same host and port
 * ([redirectTarget]). One download runs at a time in the process, and none runs longer than [TOTAL_DEADLINE_MS].
 *
 * Blocking: call from a worker thread / `Dispatchers.IO` only. [Handle.cancel] aborts from any thread.
 */
object UbisoftInstallerDownload {
    private const val TAG = "UbisoftInstaller"

    const val INSTALLER_URL = "https://static3.cdn.ubi.com/orbit/launcher_installer/UbisoftConnectInstaller.exe"

    /** What the dialog says it weighs (Content-Length 262918936 bytes, 251 MiB). */
    const val APPROX_MB = 251

    const val FILE_NAME = "UbisoftConnectInstaller.exe"

    private const val DIR_REL = ".wine/drive_c/bl_installscript/ubisoft_installer"
    private const val WIN_DIR = "C:\\bl_installscript\\ubisoft_installer\\"

    /** A file smaller than this under the final name, or announced to be, is not the installer (the real one is 251 MB). */
    private const val MIN_PLAUSIBLE_BYTES = 50L * 1024 * 1024

    /** A download announced to be larger than this is refused before a byte is written. */
    private const val MAX_BYTES = 1024L * 1024 * 1024

    /** The whole transfer, connecting included, may take this long (the per-read timeout alone lets a trickle run for ever). */
    private const val TOTAL_DEADLINE_MS = 25L * 60 * 1000

    /** One download at a time: two would write the same `.part` file. */
    private val downloadLock = java.util.concurrent.locks.ReentrantLock()

    /** Free space wanted beyond the file itself, for the installer to unpack into. */
    private const val SPACE_MARGIN_MB = 100
    private const val SPACE_MARGIN_BYTES = SPACE_MARGIN_MB * 1024L * 1024

    /** What the "not enough space" message says is needed: the file plus [SPACE_MARGIN_MB]. */
    const val NEEDED_FREE_MB = APPROX_MB + SPACE_MARGIN_MB

    private const val REPORT_STEP_BYTES = 256L * 1024
    private const val TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 2

    enum class Reason { NETWORK, HTTP, SIZE, INVALID, NO_SPACE, IO }

    sealed class Outcome {
        /** [file] is whole and checked (just downloaded, or kept from an earlier attempt). */
        class Done(val file: File) : Outcome()

        /** The user cancelled; the partial file is gone. */
        object Cancelled : Outcome()

        /** [detail] is the HTTP status for [Reason.HTTP], a short technical note otherwise. */
        class Failed(val reason: Reason, val detail: String) : Outcome()
    }

    /** Cancels a running [ensure] from any thread (the blocked read is cut by closing the connection). */
    class Handle {
        @Volatile
        var cancelled: Boolean = false
            private set

        @Volatile
        internal var connection: HttpURLConnection? = null

        fun cancel() {
            cancelled = true
            val c = connection ?: return
            // Closing a socket is network work: keep it off the caller's (UI) thread.
            Thread { runCatching { c.disconnect() } }.start()
        }
    }

    // ---- Where it lives -------------------------------------------------------------------------

    /** The folder the installer is kept in, inside [container]'s prefix. */
    @JvmStatic
    fun dirOf(container: Container): File = File(container.rootDir, DIR_REL)

    /** The downloaded installer's final path (it may not exist). */
    @JvmStatic
    fun fileOf(container: Container): File = File(dirOf(container), FILE_NAME)

    /** [file] (one in [dirOf]) as a Wine path, `C:\bl_installscript\ubisoft_installer\<name>`. */
    @JvmStatic
    fun windowsPathOf(file: File): String = WIN_DIR + file.name

    /** True when [windowsPath] is the downloaded installer (`C:\bl_installscript\ubisoft_installer\UbisoftConnectInstaller.exe`) and nothing else, never a game's own bundled one. Pure. */
    @JvmStatic
    fun isDownloadedPath(windowsPath: String): Boolean {
        val p = windowsPath.trim().trim('"').trim()
        return p.startsWith(WIN_DIR, ignoreCase = true) && p.substring(WIN_DIR.length).equals(FILE_NAME, ignoreCase = true)
    }

    /**
     * Batch lines that remove the downloaded installer and its folder, for the end of the setup session's batch file
     * (after the installer has exited, see `LiveInstallerRunProcessStage`). Empty for any [windowsPath] that is not the
     * downloaded installer, so a game's own installer is never touched. Pure.
     */
    @JvmStatic
    fun cleanupBatchLines(windowsPath: String): List<String> {
        if (!isDownloadedPath(windowsPath)) return emptyList()
        val dir = WIN_DIR.trimEnd('\\')
        return listOf(
            "echo Removing the downloaded installer...",
            "del /f /q \"$dir\\*\" >nul 2>&1",
            "rmdir \"$dir\" >nul 2>&1",
        )
    }

    /** Deletes the downloaded installer and any partial file (a stat when there is nothing). Never throws. */
    @JvmStatic
    fun deleteDownloaded(container: Container) {
        val dir = dirOf(container)
        if (!dir.exists()) return
        try {
            if (dir.deleteRecursively()) Log.i(TAG, "removed the downloaded Ubisoft Connect installer")
        } catch (e: Exception) {
            Log.w(TAG, "could not remove the downloaded installer", e)
        }
    }

    // ---- Pure checks ----------------------------------------------------------------------------

    /** True when [head] starts with `MZ`, the DOS/PE signature every Windows executable has. */
    @JvmStatic
    fun hasMzHeader(head: ByteArray): Boolean =
        head.size >= 2 && head[0] == 'M'.code.toByte() && head[1] == 'Z'.code.toByte()

    /** True when the bytes received equal the Content-Length the server announced (an unknown length never matches). */
    @JvmStatic
    fun sizeMatches(expected: Long, actual: Long): Boolean = expected > 0 && expected == actual

    /** True when [usableBytes] of free space holds a download of [total] bytes plus room to unpack it. */
    @JvmStatic
    fun hasEnoughSpace(usableBytes: Long, total: Long): Boolean = usableBytes >= total + SPACE_MARGIN_BYTES

    /** True for a file that can be reused instead of downloaded again: big enough and an executable. */
    @JvmStatic
    fun isPlausibleInstaller(length: Long, head: ByteArray): Boolean = length >= MIN_PLAUSIBLE_BYTES && hasMzHeader(head)

    /** True when the announced Content-Length [total] is one the installer can have: neither tiny nor huge. */
    internal fun isPlausibleLength(total: Long): Boolean = total in MIN_PLAUSIBLE_BYTES..MAX_BYTES

    /** True when a transfer that started [elapsedMs] ago has used up its time. */
    internal fun pastDeadline(elapsedMs: Long): Boolean = elapsedMs > TOTAL_DEADLINE_MS

    /** True when [e] says the disk is full. */
    @JvmStatic
    fun isNoSpace(e: IOException): Boolean {
        val m = e.message ?: return false
        return m.contains("ENOSPC", ignoreCase = true) || m.contains("No space left", ignoreCase = true)
    }

    /**
     * Where a redirect from [current] may go: [location] resolved against it, only when it is https on the same host and
     * port and carries no credentials, otherwise null (the download is refused rather than sent to another server).
     */
    @JvmStatic
    fun redirectTarget(current: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val base = try { URI(current) } catch (e: Exception) { return null }
        val next = try { base.resolve(location.trim()) } catch (e: Exception) { return null }
        val host = next.host ?: return null
        if (!"https".equals(next.scheme, ignoreCase = true) || !host.equals(base.host, ignoreCase = true)) return null
        if (next.port != base.port || next.userInfo != null) return null
        return next.toString()
    }

    // ---- Download -------------------------------------------------------------------------------

    /**
     * The installer for [container]: the one already downloaded when it is whole, otherwise a fresh download.
     * [onProgress] gets (bytes so far, total) on this thread, a few times a second at most. Never throws.
     */
    @JvmStatic
    fun ensure(container: Container, handle: Handle, onProgress: (Long, Long) -> Unit): Outcome {
        val dest = fileOf(container)
        if (dest.isFile && isPlausibleInstaller(dest.length(), readHead(dest))) return Outcome.Done(dest)
        // A second caller waits here and, once the first has finished, finds the whole file instead of starting over.
        downloadLock.lock()
        try {
            if (handle.cancelled) return Outcome.Cancelled
            if (dest.isFile && isPlausibleInstaller(dest.length(), readHead(dest))) return Outcome.Done(dest)
            return try {
                download(dest, handle, onProgress)
            } catch (t: Throwable) {
                Log.w(TAG, "download failed", t)
                Outcome.Failed(Reason.IO, t.message ?: t.javaClass.simpleName)
            }
        } finally {
            downloadLock.unlock()
        }
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private fun download(dest: File, handle: Handle, onProgress: (Long, Long) -> Unit): Outcome {
        val dir = dest.parentFile ?: return Outcome.Failed(Reason.IO, "no folder")
        if (!dir.isDirectory && !dir.mkdirs()) return Outcome.Failed(Reason.IO, "cannot create ${dir.path}")
        val part = File(dir, dest.name + ".part")
        part.delete()
        dest.delete()
        var finished = false
        val startedAt = System.nanoTime()
        try {
            val conn = connect(handle)
            val total = conn.contentLengthLong
            if (total <= 0) return Outcome.Failed(Reason.SIZE, "no Content-Length")
            if (!isPlausibleLength(total)) return Outcome.Failed(Reason.SIZE, "Content-Length $total is not an installer's size")
            // 0 means the file system would not say; a full disk still stops the write below with ENOSPC.
            val free = dir.usableSpace
            if (free > 0 && !hasEnoughSpace(free, total)) return Outcome.Failed(Reason.NO_SPACE, "$free free, $total needed")
            val input = conn.inputStream
            val out = try { FileOutputStream(part) } catch (e: IOException) { return failure(handle, if (isNoSpace(e)) Reason.NO_SPACE else Reason.IO, e) }
            var written = 0L
            var stop: Outcome? = null
            try {
                val buf = ByteArray(64 * 1024)
                var lastReport = 0L
                while (stop == null) {
                    val n = try { input.read(buf) } catch (e: IOException) { stop = failure(handle, Reason.NETWORK, e); break }
                    if (n < 0) break
                    if (handle.cancelled) { stop = Outcome.Cancelled; break }
                    if (pastDeadline((System.nanoTime() - startedAt) / 1_000_000L)) { stop = Outcome.Failed(Reason.NETWORK, "taking too long"); break }
                    try { out.write(buf, 0, n) } catch (e: IOException) { stop = failure(handle, if (isNoSpace(e)) Reason.NO_SPACE else Reason.IO, e); break }
                    written += n
                    if (written - lastReport >= REPORT_STEP_BYTES || written == total) { lastReport = written; onProgress(written, total) }
                }
                out.flush()
            } finally {
                runCatching { out.close() }
            }
            stop?.let { return it }
            if (handle.cancelled) return Outcome.Cancelled
            if (!sizeMatches(total, written)) return Outcome.Failed(Reason.SIZE, "$written of $total bytes")
            if (!hasMzHeader(readHead(part))) return Outcome.Failed(Reason.INVALID, "not an executable")
            if (!part.renameTo(dest)) return Outcome.Failed(Reason.IO, "cannot rename the download")
            finished = true
            Log.i(TAG, "downloaded the Ubisoft Connect installer ($written bytes)")
            return Outcome.Done(dest)
        } catch (e: HttpStatusException) {
            return Outcome.Failed(Reason.HTTP, e.code.toString())
        } catch (e: IOException) {
            return failure(handle, if (isNoSpace(e)) Reason.NO_SPACE else Reason.NETWORK, e)
        } finally {
            val c = handle.connection
            handle.connection = null
            if (c != null) runCatching { c.disconnect() }
            if (!finished) part.delete()
        }
    }

    /** Opens [INSTALLER_URL] (following same-host https redirects) and returns the connection once it answers 200. */
    private fun connect(handle: Handle): HttpURLConnection {
        var url = INSTALLER_URL
        for (hop in 0..MAX_REDIRECTS) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", StoreNet.BROWSER_UA)
                // No transparent gzip: the size check compares bytes on the wire with Content-Length.
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Accept", "*/*")
            }
            handle.connection = conn
            if (handle.cancelled) { runCatching { conn.disconnect() }; throw IOException("cancelled") }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) return conn
            val next = if (code in 300..399 && hop < MAX_REDIRECTS) redirectTarget(url, conn.getHeaderField("Location")) else null
            runCatching { conn.disconnect() }
            if (next == null) throw HttpStatusException(code)
            url = next
        }
        throw HttpStatusException(0)
    }

    private fun failure(handle: Handle, reason: Reason, e: IOException): Outcome {
        if (handle.cancelled) return Outcome.Cancelled
        Log.w(TAG, "download stopped ($reason)", e)
        return Outcome.Failed(reason, e.message ?: "")
    }

    private fun readHead(file: File): ByteArray = try {
        RandomAccessFile(file, "r").use { f ->
            val head = ByteArray(2)
            val n = f.read(head)
            if (n == 2) head else ByteArray(0)
        }
    } catch (e: IOException) {
        ByteArray(0)
    }
}
