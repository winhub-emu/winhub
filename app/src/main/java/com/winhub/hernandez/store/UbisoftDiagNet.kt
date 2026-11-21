package com.winhub.hernandez.store

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Reachability probes for the Ubisoft diagnostics report ([UbisoftDiag]): for each host a DNS lookup, a TCP connect
 * to port 443 and a TLS handshake, each timed. Nothing is ever sent after the handshake (no request, no
 * credentials): the socket is closed as soon as the certificate has been read. Run on a background thread.
 */
object UbisoftDiagNet {
    /** The Ubisoft hosts seen in the Wine logs of Connect and the games. Named in the `ubisoft_diag_report_info` text of the Log Manager: keep both in step. */
    val HOSTS = listOf(
        "public-ubiservices.ubi.com",
        "public-ws-ubiservices.ubi.com",
        "dmx.upc.ubisoft.com",
        "ubiservices.cdn.ubi.com",
        "static3.cdn.ubi.com",
        "uplaypc-s-ubisoft.cdn.ubi.com",
        "ubisoft-avatars.akamaized.net",
    )

    /** A host that is not Ubisoft's, to tell "Ubisoft is unreachable" from "no network". */
    const val CONTROL_HOST = "www.cloudflare.com"

    private const val STEP_TIMEOUT_MS = 4000

    /** How much of an exception message goes into the report, and how many addresses of a host are listed / tried. */
    private const val ERROR_CHARS = 160
    private const val ADDRS_LISTED = 6
    private const val ADDRS_TRIED = 3
    private const val MAX_PARALLEL = 8

    /** Runs [block] on a daemon thread and waits at most [ms]; null when it did not finish. A throw comes back as a failed [Result]. */
    private fun <T> withTimeout(ms: Long, block: () -> T): Result<T>? {
        var result: Result<T>? = null
        val t = Thread({ result = runCatching(block) }, "ubisoft-diag-probe")
        t.isDaemon = true
        t.start()
        t.join(ms)
        return result
    }

    private fun short(e: Throwable): String = e.javaClass.simpleName + (e.message?.let { ": " + it.take(ERROR_CHARS) } ?: "")

    /** One host's result as report lines (never throws). */
    @JvmStatic
    fun probe(host: String): List<String> {
        val out = ArrayList<String>()
        val started = System.nanoTime()
        fun ms(from: Long) = (System.nanoTime() - from) / 1_000_000

        // DNS
        val dnsStart = System.nanoTime()
        val dns = withTimeout(STEP_TIMEOUT_MS.toLong()) { InetAddress.getAllByName(host).toList() }
        val addrs = dns?.getOrNull()
        out += "dns: " + when {
            dns == null -> "TIMEOUT after ${ms(dnsStart)} ms"
            addrs == null -> "FAILED ${short(dns.exceptionOrNull()!!)} (${ms(dnsStart)} ms)"
            else -> "OK ${ms(dnsStart)} ms -> " + addrs.take(ADDRS_LISTED).joinToString(", ") { it.hostAddress ?: "?" }
        }
        if (addrs.isNullOrEmpty()) return render(host, out, ms(started))

        // TCP then TLS (no payload): the first address that connects is used.
        var socket: Socket? = null
        val tcpStart = System.nanoTime()
        var tcpNote = ""
        for (a in addrs.take(ADDRS_TRIED)) {
            val s = Socket()
            try {
                s.connect(InetSocketAddress(a, 443), STEP_TIMEOUT_MS)
                socket = s
                tcpNote = "OK ${ms(tcpStart)} ms to ${a.hostAddress}:443"
                break
            } catch (e: Exception) {
                tcpNote = "FAILED ${short(e)} (${ms(tcpStart)} ms, ${a.hostAddress}:443)"
                try { s.close() } catch (_: Exception) {}
            }
        }
        out += "tcp: $tcpNote"
        val connected = socket ?: return render(host, out, ms(started))

        val tlsStart = System.nanoTime()
        try {
            connected.soTimeout = STEP_TIMEOUT_MS
            val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(connected, host, 443, true) as SSLSocket
            ssl.soTimeout = STEP_TIMEOUT_MS
            ssl.startHandshake()
            val session = ssl.session
            val cert = session.peerCertificates.firstOrNull() as? X509Certificate
            val verified = try { HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session) } catch (e: Exception) { false }
            out += "tls: OK ${ms(tlsStart)} ms ${session.protocol} ${session.cipherSuite} hostnameVerified=$verified"
            if (cert != null) {
                out += "cert: subject=\"${cert.subjectX500Principal.name}\" issuer=\"${cert.issuerX500Principal.name}\" " +
                    "notBefore=${cert.notBefore} notAfter=${cert.notAfter}"
            }
            try { ssl.close() } catch (_: Exception) {}
        } catch (e: Exception) {
            out += "tls: FAILED ${short(e)} (${ms(tlsStart)} ms)"
        } finally {
            try { connected.close() } catch (_: Exception) {}
        }
        return render(host, out, ms(started))
    }

    private fun render(host: String, lines: List<String>, totalMs: Long): List<String> =
        listOf("host $host") + lines.map { "  $it" } + "  total $totalMs ms"

    /** [probe] for every host of [hosts] in parallel; waits at most [totalMs] for all of them. Result in the order of [hosts]. */
    @JvmStatic
    fun probeAll(hosts: List<String>, totalMs: Long): List<String> {
        val pool = Executors.newFixedThreadPool(minOf(hosts.size, MAX_PARALLEL).coerceAtLeast(1)) { r ->
            Thread(r, "ubisoft-diag-net").also { it.isDaemon = true }
        }
        val out = ArrayList<String>()
        try {
            val futures = hosts.map { h -> pool.submit(Callable { probe(h) }) }
            val deadline = System.currentTimeMillis() + totalMs
            for ((i, f) in futures.withIndex()) {
                val left = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
                try {
                    out += f.get(left, TimeUnit.MILLISECONDS)
                } catch (e: Exception) {
                    out += "host ${hosts[i]}"
                    out += "  not finished: ${short(e)}"
                }
            }
        } finally {
            pool.shutdownNow()
        }
        return out
    }
}
