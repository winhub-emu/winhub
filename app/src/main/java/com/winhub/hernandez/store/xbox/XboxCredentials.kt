package com.winhub.hernandez.store.xbox

import android.content.Context
import android.net.LocalServerSocket
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The credential channel for one Xodus child (ported from DroidDeck, GPL-3.0). The backend has no Secret
 * Service on Android, so DroidDeck's patched backend asks the app over an abstract Unix socket named by
 * DROIDDECK_XBOX_CREDENTIAL_SOCKET. Every connection is checked: same UID, the peer is the child process
 * this channel was made for (or one of its threads), and that process maps the exact backend binary we
 * started (inode + SHA-256). Values live in XboxAccountStore, Keystore-sealed.
 *
 * Bannerlator runs the backend directly (bionic, no proot), so the peer is the child itself; there is no
 * PRoot tracer to check, unlike DroidDeck.
 */
internal class XboxCredentials(private val app: Context, expectedGeneration: String? = null) : Closeable {
    val name = "bannerlator-xbox-${UUID.randomUUID()}"
    private val generation = lifecycle(app, expectedGeneration) { it }
    private val server = LocalServerSocket(name)
    @Volatile private var closed = false
    private val bound = CountDownLatch(1)
    private var backend: Process? = null
    private var binaryHash = byteArrayOf()
    private lateinit var binary: File
    private val peers = ConcurrentHashMap<Int, String>()

    fun bind(process: Process, file: File, digest: ByteArray) {
        backend = process
        binary = file.canonicalFile
        binaryHash = digest
        bound.countDown()
    }

    private fun rejected(reason: String): Boolean {
        XboxDiag.log(app, "credential peer rejected: $reason")
        return false
    }

    private fun authorized(pid: Int): Boolean {
        if (!bound.await(5, TimeUnit.SECONDS) || closed) return rejected("unbound")
        val process = backend ?: return rejected("missing-child")
        if (!process.isAlive) return rejected("exited-child")
        val root = Xodus.pidOf(process)
        if (root <= 0 || pid <= 0) return rejected("missing-process-id")
        val identity = identity(pid, root) ?: return rejected("foreign-backend")
        if (peers[pid] == identity) return true
        if (!java.security.MessageDigest.isEqual(binaryHash, Xodus.sha256(binary))) return rejected("different-executable")
        peers[pid] = identity
        return true
    }

    private fun identity(pid: Int, root: Int): String? {
        if (root <= 0 || pid <= 0) return null
        val tgid = File("/proc/$pid/status").readLines().first { it.startsWith("Tgid:") }.substringAfter(':').trim().toInt()
        if (tgid != root) return null
        val executable = android.system.Os.stat(binary.path)
        if (!mapsBackend(File("/proc/$pid/maps").readText(), binary.path, executable.st_ino)) return null
        return File("/proc/$pid/stat").readText().substringAfterLast(") ").split(' ')[19] + ":${executable.st_dev}:${executable.st_ino}"
    }

    fun signalBackend(signal: Int) {
        val process = backend ?: return
        if (!process.isAlive) return
        val pid = Xodus.pidOf(process)
        if (pid > 0) runCatching { android.system.Os.kill(pid, signal) }
    }

    init {
        Thread({
            while (!closed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    it.soTimeout = 10_000
                    val reply = runCatching {
                        val peer = it.peerCredentials
                        check(peer.uid == android.os.Process.myUid() && authorized(peer.pid))
                        val request = JSONObject(readLine(it.inputStream.buffered(), 2_097_152))
                        storage(app) {
                            check(!closed && epoch(app) == generation && authorized(peer.pid))
                            answer(request)
                        }
                    }.getOrElse { e ->
                        XboxDiag.log(app, "credential request rejected: ${e.javaClass.simpleName}")
                        JSONObject().put("ok", false)
                    }
                    runCatching { it.outputStream.write((reply.toString() + "\n").toByteArray()) }
                }
            }
        }, "xbox-credentials").start()
    }

    private fun answer(request: JSONObject): JSONObject {
        val key = request.getString("key")
        require(key.isNotEmpty() && key.length <= 512)
        val record = XboxAccountStore.read(app)
        check(!XboxAccountStore.unavailable)
        val json = record ?: JSONObject()
        val slots = json.optJSONObject("slots") ?: JSONObject().also { json.put("slots", it) }
        val result = JSONObject().put("ok", true)
        when (request.getString("op")) {
            "get" -> result.put("value", slots.opt(key) ?: JSONObject.NULL)
            "set" -> {
                val bytes = request.getJSONArray("value")
                require(bytes.length() <= 262_144)
                for (i in 0 until bytes.length()) require(bytes.getInt(i) in 0..255)
                slots.put(key, bytes)
                XboxAccountStore.write(app, json)
            }
            "remove" -> { slots.remove(key); XboxAccountStore.write(app, json) }
            else -> error("Invalid credential operation")
        }
        return result
    }

    override fun close() { closed = true; bound.countDown(); runCatching { server.close() } }

    companion object {
        private val lock = Any()

        internal fun mapsBackend(maps: String, path: String, inode: Long): Boolean = maps.lineSequence().any { line ->
            val fields = line.trim().split(Regex("\\s+"), limit = 6)
            fields.size == 6 && 'x' in fields[1] && fields[4].toLongOrNull() == inode &&
                runCatching { File(fields[5]).canonicalPath == path }.getOrDefault(false)
        }

        private fun epoch(app: Context): String =
            runCatching { File(XboxAccountStore.dir(app), "generation").readText() }.getOrDefault("")

        /** Runs [block] with the current account generation; fails if it changed from [expected]. */
        fun <T> lifecycle(app: Context, expected: String? = null, block: (String) -> T): T = storage(app) {
            val current = epoch(app)
            check(expected == null || expected == current) { "Xbox account changed during the request" }
            block(current)
        }

        /** Sign-out: a new generation (in-flight children can no longer write) and the record removed. */
        fun invalidate(app: Context, revoked: () -> Unit = {}) = storage(app) {
            File(XboxAccountStore.dir(app), "generation").writeText(UUID.randomUUID().toString())
            XboxAccountStore.clear(app)
            revoked()
        }

        fun <T> storage(app: Context, block: () -> T): T = synchronized(lock) {
            java.io.RandomAccessFile(File(XboxAccountStore.dir(app), "account.lock"), "rw").use { file ->
                file.channel.lock().use { block() }
            }
        }

        fun readLine(input: InputStream, limit: Int): String {
            val out = java.io.ByteArrayOutputStream()
            while (out.size() <= limit) {
                val byte = input.read()
                if (byte == 10) return out.toString("UTF-8")
                if (byte < 0) throw java.io.EOFException("Xbox backend disconnected")
                out.write(byte)
            }
            error("Xbox backend response exceeded its limit")
        }
    }
}
