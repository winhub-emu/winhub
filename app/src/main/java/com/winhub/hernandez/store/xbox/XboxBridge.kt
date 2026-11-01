package com.winhub.hernandez.store.xbox

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Collections

/**
 * The game's way to the Xbox account service (port of DroidDeck's droiddeck-xbox bridge, GPL-3.0). The
 * Windows Xbox runtime modules inside Wine (xgameruntime.dll) connect to 127.0.0.1:[port], send the
 * 64-character [key], and are then joined to the service's Unix socket. Anything that doesn't present the
 * key first is dropped. The game gets XODUS_BRIDGE_PORT / XODUS_BRIDGE_KEY; it never sees the credential
 * channel.
 */
internal class XboxBridge(private val upstream: String) : Closeable {
    val key: String = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    private val clients: MutableSet<Closeable> = Collections.synchronizedSet(HashSet())
    @Volatile private var stopped = false

    init {
        Thread({
            while (!stopped) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (clients.size >= 64) { runCatching { client.close() }; continue }
                clients.add(client)
                Thread({ connect(client) }, "xbox-bridge-client").apply { isDaemon = true; start() }
            }
        }, "xbox-bridge").apply { isDaemon = true; start() }
    }

    private fun connect(client: Socket) {
        var remote: LocalSocket? = null
        try {
            client.soTimeout = 10_000
            val expected = key.toByteArray(Charsets.US_ASCII)
            val supplied = ByteArray(expected.size)
            var read = 0
            val input = client.getInputStream()
            while (read < supplied.size) {
                val n = input.read(supplied, read, supplied.size - read)
                if (n < 0) return
                read += n
            }
            if (!java.security.MessageDigest.isEqual(supplied, expected)) return
            remote = LocalSocket().apply { connect(LocalSocketAddress(upstream, LocalSocketAddress.Namespace.FILESYSTEM)) }
            clients.add(remote)
            client.soTimeout = 0
            val r = remote
            val writer = Thread({ forward(input, r.outputStream) { runCatching { r.shutdownOutput() } } }, "xbox-bridge-up")
                .apply { isDaemon = true; start() }
            forward(r.inputStream, client.getOutputStream()) { runCatching { client.shutdownOutput() } }
            writer.join(2000)
        } catch (_: Exception) {
        } finally {
            remote?.let { clients.remove(it); runCatching { it.close() } }
            clients.remove(client); runCatching { client.close() }
        }
    }

    private fun forward(source: InputStream, target: OutputStream, done: () -> Unit) {
        try {
            val buffer = ByteArray(65536)
            while (!stopped) {
                val n = source.read(buffer)
                if (n < 0) break
                target.write(buffer, 0, n); target.flush()
            }
        } catch (_: Exception) {
        } finally { done() }
    }

    override fun close() {
        stopped = true
        runCatching { server.close() }
        synchronized(clients) { clients.forEach { runCatching { it.close() } }; clients.clear() }
    }
}
