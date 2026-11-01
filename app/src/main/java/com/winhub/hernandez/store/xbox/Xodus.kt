package com.winhub.hernandez.store.xbox

import android.content.Context
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The Xbox backend: Xodus (xodus-gaming/xodus, GPL-3.0) with Xodus Nexus' and DroidDeck's patches
 * (tools/xbox), built for Android and shipped as libxodus_cli.so / libxodus_service.so in jniLibs, so it
 * runs as a plain child process of the app: no Linux runtime, no proot. Ported from DroidDeck's Xodus.kt.
 */
internal object Xodus {
    fun market(): String = java.util.Locale.getDefault().country.uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US"

    fun binary(app: Context, service: Boolean): File =
        File(app.applicationInfo.nativeLibraryDir, if (service) "libxodus_service.so" else "libxodus_cli.so")

    fun isAvailable(app: Context): Boolean = binary(app, false).isFile && binary(app, true).isFile

    /** The backend's own home: config, caches and its data, all inside the app's private storage. */
    fun home(app: Context): File = File(app.filesDir, "xbox/home").apply { mkdirs() }

    class Child(val process: Process, private val credentials: XboxCredentials, private val runtimeDirectory: File? = null,
                private val launchDirectory: File? = null) : Closeable {
        /** The backend's last known error message (see [KNOWN_ERRORS]), for the user. */
        @Volatile var lastError: String? = null

        @Synchronized fun send(frame: JSONObject) {
            process.outputStream.write((frame.toString() + "\n").toByteArray())
            process.outputStream.flush()
        }
        override fun close() {
            try {
                credentials.close()
                runCatching { process.outputStream.close() }
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    credentials.signalBackend(android.system.OsConstants.SIGINT)
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        credentials.signalBackend(android.system.OsConstants.SIGKILL)
                        if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
                    }
                }
            } finally {
                runtimeDirectory?.let { directory ->
                    File(directory, "xodus.sock").delete()
                    directory.delete()
                }
                launchDirectory?.let(XboxLaunchViews::remove)
            }
        }
    }

    fun start(app: Context, args: List<String>, service: Boolean = false, runtimeDirectory: File? = null,
              launchDirectory: File? = null, credentialGeneration: String? = null): Child {
        val binary = binary(app, service)
        check(binary.isFile) { "The Xbox backend is missing from this build." }
        val credentials = XboxCredentials(app, credentialGeneration)
        try {
            val home = home(app)
            val runtime = (runtimeDirectory ?: File(app.filesDir, "xbox/runtime")).apply { mkdirs() }
            val tmp = (launchDirectory ?: File(app.filesDir, "xbox/tmp")).apply { mkdirs() }
            val builder = ProcessBuilder(listOf(binary.path) + args).directory(home)
            builder.environment().apply {
                remove("LD_PRELOAD"); remove("LD_AUDIT"); remove("LD_LIBRARY_PATH")
                put("HOME", home.path)
                put("XDG_CONFIG_HOME", File(home, ".config").path)
                put("XDG_DATA_HOME", File(home, ".local/share").path)
                put("XDG_STATE_HOME", File(home, ".local/state").path)
                put("XDG_CACHE_HOME", File(home, ".cache").path)
                put("XDG_RUNTIME_DIR", runtime.path)
                put("TMPDIR", tmp.path)
                // OpenSSL (native-tls) and the rustls verifier read Android's CA store (hashed PEM files);
                // Android 14+ keeps the updatable one in the Conscrypt APEX.
                put("SSL_CERT_DIR", listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")
                    .firstOrNull { File(it).isDirectory } ?: "/system/etc/security/cacerts")
                put("XODUS_LOG", "off"); put("RUST_LOG", "off")
                put("DROIDDECK_XBOX_CREDENTIAL_SOCKET", credentials.name)
            }
            val digest = sha256(binary)
            val process = builder.start()
            credentials.bind(process, binary, digest)
            XboxDiag.log(app, "backend started: ${if (service) "service" else "cli ${args.firstOrNull { !it.startsWith("-") }.orEmpty()}"} pid=${pidOf(process)}")
            return Child(process, credentials, runtimeDirectory, launchDirectory)
        } catch (e: Exception) { credentials.close(); throw e }
    }

    fun run(app: Context, args: List<String>, timeout: Long = 90, credentialGeneration: String? = null): JSONObject {
        start(app, args, credentialGeneration = credentialGeneration).use { child ->
            val output = java.io.ByteArrayOutputStream()
            var readError: Throwable? = null
            val reader = Thread({
                try {
                    val buffer = ByteArray(8192)
                    child.process.inputStream.use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 8_000_000)
                            output.write(buffer, 0, count)
                        }
                    }
                } catch (t: Throwable) { readError = t; child.process.destroy() }
            }, "xbox-result").apply { start() }
            val diagnostic = diagnostics(app, child)
            check(child.process.waitFor(timeout, TimeUnit.SECONDS)) { "Xbox request timed out. Try again." }
            reader.join(3000); diagnostic.join(3000)
            check(readError == null && !reader.isAlive) { "Xbox returned an invalid response." }
            val code = child.process.exitValue()
            XboxDiag.log(app, "backend ${args.firstOrNull { !it.startsWith("-") }.orEmpty()} exited $code")
            check(code == 0) { child.lastError ?: "Xbox request failed. Check your account and network connection." }
            return JSONObject(output.toString("UTF-8"))
        }
    }

    /** Xodus' fixed error texts (crates/xodus-cli: package.rs, package_plan.rs, license.rs, streaming.rs); none carry tokens. */
    private val KNOWN_ERRORS = Regex("(Package was not found, is it owned by the user\\?|No \\.msixvc file found|" +
        "Package selection requires a Store product ID|Invalid STS token|Uns?pp?orted (user )?token|Failed to get exchange MS token|" +
        "Microsoft returned an unsupported content-licensing user ticket\\.|Store product identity mismatch|Too many Store packages|" +
        "Store bundle (contains too many products|exceeds resolver limits)|Circular or excessively deep Store bundle|" +
        "Selected package is not a unique supported member of this Store offer|Invalid Store product or market|" +
        "Store metadata is too large|unexpected number of content keys [0-9]{1,4}|not enough free disk space on [^:]{1,200}: need [0-9]+ bytes, have [0-9]+ bytes.*|" +
        "Cannot start this download: .{1,120}|Unable to prepare download checkpoints: .{1,120}|Cannot read (cached )?package-file metadata: .{1,120})")

    /** Copies the backend's allow-listed diagnostic lines (no tokens, no names) into the Xbox log. */
    fun diagnostics(app: Context, child: Child): Thread = Thread({
        runCatching {
            var afterPanic = false
            child.process.errorStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                if (line.matches(Regex("NEXUS_CREDENTIAL_ERROR_V1 [a-z-]{1,48}|NEXUS_LIBRARY_DIAGNOSTIC_V1 [a-z-]{1,48} [0-9]{1,3} [0-9]{1,6}|NEXUS_SESSION_DIAGNOSTIC_V1 [a-z-]{1,64}|NEXUS_PACKAGE_FILES_V1 [0-9]{1,5} [a-z0-9,]{1,200}|Account library unavailable \\([a-z-]{1,64}\\); credentials and installed games are unchanged\\.|Account library timed out; installed games are unchanged\\.")))
                    XboxDiag.log(app, "backend: $line")
                else if (line.length <= 300 && line.matches(KNOWN_ERRORS)) {
                    child.lastError = line
                    XboxDiag.log(app, "backend error: $line")
                } else if (line.length in 1..200 && line.all { it in ' '..'~' } && (line.startsWith("error") || line.startsWith("Error") || line.contains("panicked"))) {
                    val panic = line.contains("panicked")
                    // A panic's location (file:line) and message are the only way to tell why the backend died; long token-like runs are redacted.
                    XboxDiag.log(app, "backend error class: " + if (panic) line.replace(Regex("[A-Za-z0-9_.~+/=-]{40,}"), "<redacted>") else line.substringBefore(':').take(80))
                    afterPanic = panic
                } else if (afterPanic) {
                    afterPanic = false
                    if (line.length in 1..300 && line.all { it in ' '..'~' })
                        XboxDiag.log(app, "backend panic: " + line.replace(Regex("[A-Za-z0-9_.~+/=-]{40,}"), "<redacted>"))
                }
            } }
        }
    }, "xbox-diagnostics").apply { start() }

    /** The OS pid of a child (java.lang.Process.pid() is not in this compileSdk; read the field). */
    fun pidOf(process: Process): Int = runCatching {
        val field = process.javaClass.getDeclaredField("pid"); field.isAccessible = true; field.getInt(process)
    }.getOrDefault(-1)

    fun sha256(file: File): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest()
    }
}
