package com.winhub.hernandez.store.xbox

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Xbox game launches (adapted from DroidDeck's XboxRuntime + droiddeck-xbox, GPL-3.0). DroidDeck starts
 * the game through its Linux runtime; here the game runs in an ordinary Bannerlator Wine container and the
 * app does the host side:
 *  - the account service (xodus-service) behind a Unix socket, one per signed-in account;
 *  - a licensed launch view: `xodus-cli run <folder> droiddeck:prepare` materialises the encrypted images
 *    (exe and DLLs) in a private directory inside imagefs, so the Wine session can open them, and prints
 *    the executable to start; that child stays alive for the session and removes the view on exit;
 *  - a loopback bridge (XboxBridge) the game's Xbox runtime modules use to reach the service;
 *  - a lease per launch, renewed while the session runs, released when it ends.
 */
object XboxRuntime {
    /** What the Wine session needs to start an Xbox game, and the cleanup when it ends. */
    class Launch internal constructor(
        val productId: String,
        /** The materialised executable (a Unix path inside imagefs). */
        val exe: File,
        /** Environment for the game process (bridge port/key, service socket, save sync, PlayFab token). */
        val env: Map<String, String>,
        /** WINEDLLOVERRIDES entries forcing this launch's staged modules native. */
        val overrides: String,
        private val token: String,
        private val bridge: XboxBridge,
        private val viewRoot: File? = null,
        private val installRoot: File? = null,
        private val viewFiles: Set<String> = emptySet(),
    ) : Closeable {
        /** Wall-clock time the launch was prepared: saves changed after it belong to this session. */
        val preparedAt: Long = System.currentTimeMillis()
        @Volatile private var closed = false
        /** Non-blocking (teardown may call it on the main thread): the backend children get up to 5 s to exit. */
        override fun close() {
            if (closed) return
            closed = true
            Thread({
                // Before the lease ends (which removes the view): keep what the game wrote there.
                val view = viewRoot; val install = installRoot
                if (view != null && install != null) runCatching { XboxViewSaves.persist(view, install, viewFiles) }
                runCatching { bridge.close() }
                released(token)
                XboxDiag.log(null, "launch released: $productId")
            }, "xbox-launch-close").start()
        }
    }

    private val state = Any()
    private val startup = Any()
    private var child: Xodus.Child? = null
    private var owner: String? = null
    private var serviceSocket: File? = null
    private var generation = 0L
    private val views = XboxLaunchViews()
    private val launches = XboxLaunchLeases { SystemClock.uptimeMillis() }
    @Volatile private var session: String? = null

    fun isActive(id: String): Boolean = launches.active(id)
    private fun released(token: String) { runCatching { launches.release(token)?.close() } }

    /**
     * Prepares an installed game for a session. [imagefsRoot] is the session's imagefs root; the launch view
     * goes under its xbox-views (not tmp: the session clears imagefs/tmp after this runs). Blocks up to ~3 minutes (service start + licensed preparation).
     */
    fun prepare(app: Context, id: String, sessionId: String, imagefsRoot: File, system32: File, cloudSaves: Boolean,
                nexusRuntime: Boolean = true): Launch {
        val game = XboxInstallState.get(app, id) ?: error("This Xbox game is not installed.")
        check(File(game.folder, game.exe).isFile) { "This Xbox game's files are missing. Reinstall it." }
        session = sessionId
        val account = XboxAccountStore.signedInAs(app) ?: error("Sign in to Xbox before playing.")
        val (token, current, credentials) = XboxCredentials.lifecycle(app) { epoch ->
            synchronized(state) { Triple(launches.begin(id, account, sessionId), generation, epoch) }
        }
        XboxDiag.log(app, "launch prepare: $id")
        Thread({
            while (isActive(id)) {
                Thread.sleep(10_000)
                launches.renew(token, id, XboxAccountStore.signedInAs(app), session)
                launches.stale(XboxAccountStore.signedInAs(app), session, false).forEach { runCatching { it.close() } }
            }
        }, "xbox-launch-expiry").apply { isDaemon = true; start() }
        try {
            val socket = service(app, account, current, credentials)
            check(synchronized(state) { generation == current }) { "Xbox launch was cancelled" }
            val market = game.market.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: Xodus.market()
            val directory = views.create(imagefsRoot)
            val process = try {
                Xodus.start(app, listOf("run", game.folder, "droiddeck:prepare", "--exe", game.exe,
                    "--materialize-chain", "--market", market), launchDirectory = directory, credentialGeneration = credentials)
            } catch (e: Exception) { XboxLaunchViews.remove(directory); throw e }
            if (!launches.attach(token, process)) { process.close(); error("Xbox launch was cancelled") }
            Xodus.diagnostics(app, process)
            val result = FutureTask {
                val input = process.process.inputStream.buffered()
                var line: String
                do { line = XboxCredentials.readLine(input, 131_072) } while (!line.startsWith("DROIDDECK_LAUNCH_V1 "))
                JSONObject(line.removePrefix("DROIDDECK_LAUNCH_V1 "))
            }
            Thread(result, "xbox-launch-view").start()
            val view = try { result.get(150, TimeUnit.SECONDS) } catch (e: Exception) {
                error("The Xbox game could not be prepared (license or files). Check your sign-in and try again.")
            }
            check(launches.renew(token, id, account, sessionId) && synchronized(state) { generation == current }) { "Xbox launch was cancelled" }
            val exe = File(view.getString("exe"))
            check(exe.isFile) { "The Xbox launch view has no executable." }
            // Proton builds whose wintypes.dll predates Windows.Foundation.Collections.PropertySet (Proton 10) get
            // Xodus Nexus' wintypes for the session; Xbox sign-in needs that class.
            val compat = COMPAT_DLLS.filter { needsCompat(it, system32) }
            // An older Proton (it needs the wintypes module) keeps its own WinHTTP: Nexus' build relies on Wine 11's
            // ntdll and crashed opening Xbox Live connections on Proton 10 (Brotato, BALL x PIT); Wine's own works.
            val skip = if (compat.isEmpty()) emptyList() else listOf("winhttp.dll", "libnghttp2.dll")
            stageModules(app, system32, compat, skip)
            if (compat.isNotEmpty()) XboxWinRt.require(app, system32.parentFile!!.parentFile!!.parentFile!!, PROPERTY_SET, "wintypes.dll")
            val overrides = (DLLS + compat - skip.toSet()).filter { it != "libnghttp2.dll" }.joinToString(";") { it.removeSuffix(".dll") + "=n" }
            // The view's root mirrors the install folder; game.exe is relative to both.
            val viewRoot = File(exe.path.removeSuffix(game.exe).trimEnd('/')).takeIf { exe.path.endsWith(game.exe) }
            if (nexusRuntime) useStagedRuntime(app, directory, exe.parentFile, system32)
            else if (shipsOwnRuntime(app, id)) XboxDiag.log(app, "the game ships its own xgameruntime.dll: kept for this launch (shortcut setting off)")
            val localAppData = File(system32.parentFile?.parentFile, "users/xuser/AppData/Local")
            val viewFiles = viewRoot?.let { runCatching { XboxViewSaves.prepare(app, it, localAppData) }.getOrDefault(emptySet()) } ?: emptySet()
            val bridge = XboxBridge(socket.path)
            val env = LinkedHashMap<String, String>()
            env["XODUS_SOCKET"] = socket.path
            env["XODUS_BRIDGE_PORT"] = bridge.port.toString()
            env["XODUS_BRIDGE_KEY"] = bridge.key
            env["XODUS_LOG"] = "off"; env["RUST_LOG"] = "off"
            // Cloud saves are off unless turned on: the backend's downloads were seen to arrive corrupted, and
            // its uploads can overwrite a real account's cloud copies.
            env["XODUS_SAVE_SYNC"] = if (cloudSaves) "auto" else "off"
            view.optString("playfab").takeIf { it.isNotBlank() && it != "null" }?.let { env["XODUS_XSTS_PLAYFAB"] = it }
            // The Wine env is logged at launch (ProcessHelper); keep the bridge key and the Xbox token out of it.
            com.winhub.hernandez.store.SteamLogRedactor.registerSecret(bridge.key)
            env["XODUS_XSTS_PLAYFAB"]?.let { header ->
                com.winhub.hernandez.store.SteamLogRedactor.registerSecret(header)
                com.winhub.hernandez.store.SteamLogRedactor.registerSecret(header.substringAfterLast(';'))
            }
            XboxDiag.log(app, "launch prepared: $id view=${directory.name} bridge=127.0.0.1:${bridge.port}")
            return Launch(id, exe, env, overrides, token, bridge, viewRoot, File(game.folder), viewFiles)
        } catch (e: Exception) {
            XboxDiag.log(app, "launch prepare failed: ${e.javaClass.simpleName}")
            released(token); throw e
        }
    }

    private fun service(app: Context, account: String, current: Long, credentials: String): File = synchronized(startup) {
        val existing = synchronized(state) {
            check(generation == current) { "Xbox launch was cancelled" }
            serviceSocket?.takeIf { child?.process?.isAlive == true && owner == account }
        }
        val socket = existing ?: File(app.filesDir, "xbox/runtime/s-${java.util.UUID.randomUUID().toString().replace("-", "")}/xodus.sock")
        if (existing == null) {
            val old = synchronized(state) { child.also { child = null; owner = null; serviceSocket = null } }
            old?.close()
            socket.delete()
            val service = Xodus.start(app, emptyList(), service = true, runtimeDirectory = socket.parentFile!!.apply { mkdirs() },
                credentialGeneration = credentials)
            val accepted = synchronized(state) {
                (generation == current).also { if (it) { child = service; owner = account; serviceSocket = socket } }
            }
            if (!accepted) { service.close(); error("Xbox launch was cancelled") }
            try {
                Xodus.diagnostics(app, service)
                Thread({ runCatching { service.process.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (input.read(buffer) >= 0) { Thread.yield() }
                } } }, "xbox-service-output").apply { isDaemon = true; start() }
                val deadline = SystemClock.elapsedRealtime() + 90_000
                while (!socket.exists() && service.process.isAlive && synchronized(state) { generation == current } &&
                    SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
                check(socket.exists() && service.process.isAlive) {
                    "Xbox account service did not become ready. Check your sign-in and network connection."
                }
                XboxDiag.log(app, "account service ready")
            } catch (e: Exception) {
                synchronized(state) { if (child === service) { child = null; owner = null; serviceSocket = null } }
                service.close()
                throw e
            }
        }
        socket
    }

    /** Ends every launch and the account service (sign-out, session end). */
    fun stop() {
        val processes = synchronized(state) {
            generation++
            val all = launches.clear() + listOfNotNull(child)
            child = null; owner = null; serviceSocket = null
            all
        }
        session = null
        Thread({ processes.forEach { runCatching { it.close() } } }, "xbox-runtime-stop").start()
    }

    /**
     * The five Xodus Nexus Windows modules (shipped in assets/xbox/windows), unpacked once per build to
     * files/xbox/windows, for staging into a game's Wine prefix.
     */
    fun windowsModules(app: Context): File {
        val dir = File(app.filesDir, "xbox/windows")
        val version = runCatching { app.assets.open("xbox/version").use { String(it.readBytes()).trim() } }.getOrDefault("")
        val marker = File(dir, ".version")
        if (version.isNotEmpty() && runCatching { marker.readText() }.getOrNull() == version) return dir
        dir.mkdirs()
        for (name in DLLS + COMPAT_DLLS) {
            val target = File(dir, name)
            val tmp = File(dir, "$name.tmp")
            app.assets.open("xbox/windows/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target))
        }
        marker.writeText(version)
        return dir
    }

    /** The Xbox runtime modules; all but libnghttp2 are Wine builtins overridden to native. */
    val DLLS = listOf("xgameruntime.dll", "winhttp.dll", "windows.web.dll", "twinapi.appcore.dll", "libnghttp2.dll")
    /** Staged only for containers whose own copy lacks what Xbox games need (see [needsCompat]). */
    val COMPAT_DLLS = listOf("wintypes.dll")
    private const val PROPERTY_SET = "Windows.Foundation.Collections.PropertySet"

    /** True when the container's own [name] lacks the class the Xbox runtime needs from it. */
    private fun needsCompat(name: String, system32: File): Boolean = when (name) {
        // The container's own copy: the kept original if an earlier session staged ours and did not end cleanly.
        "wintypes.dll" -> runCatching { (File(system32, name + ORIGINAL).takeIf { it.isFile } ?: File(system32, name)).readBytes() }.getOrNull()
            ?.let { indexOf(it, PROPERTY_SET.toByteArray(Charsets.UTF_16LE)) < 0 } ?: true
        else -> false
    }

    private fun indexOf(data: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..data.size - needle.size) {
            for (j in needle.indices) if (data[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** Suffix of the container's own copy of a module we replace in system32, put back after the session. */
    private const val ORIGINAL = ".bannerlator-wine"

    /**
     * Installs the modules into the container's system32, as DroidDeck does: GDK loads xgameruntime.dll and WinHTTP
     * loads libnghttp2.dll with LOAD_LIBRARY_SEARCH_SYSTEM32, and WinRT activates windows.web.dll by its system32
     * path, so copies next to the exe are not found. The container's own winhttp / windows.web / twinapi.appcore
     * are kept as <name>.bannerlator-wine and put back by [restoreSystem32] when the session ends.
     */
    fun stageModules(app: Context, system32: File, compat: List<String> = emptyList(), skip: List<String> = emptyList()) {
        check(system32.isDirectory) { "This container's Wine prefix has no system32 folder." }
        val source = windowsModules(app)
        // Skipped modules: put the container's own copy back if an earlier session left ours there.
        for (name in skip) {
            val original = File(system32, name + ORIGINAL)
            if (original.isFile) { File(system32, name).delete(); original.renameTo(File(system32, name)) }
        }
        for (name in DLLS + compat - skip.toSet()) {
            val bytes = File(source, name).readBytes()
            val target = File(system32, name)
            if (java.nio.file.Files.isSymbolicLink(target.toPath())) target.delete()
            if (target.isFile && target.readBytes().contentEquals(bytes)) continue
            val original = File(system32, name + ORIGINAL)
            if (target.isFile && !original.exists()) check(target.renameTo(original)) { "Could not keep the container's $name." }
            val tmp = File(system32, "$name.xbox-tmp")
            tmp.writeBytes(bytes)
            check(tmp.renameTo(target)) { "Could not install $name into the container." }
        }
        XboxDiag.log(app, "Xbox modules staged in the container's system32" + (if (compat.isEmpty()) "" else " (+ ${compat.joinToString()} for this Proton)") +
            (if (skip.isEmpty()) "" else " (this Proton keeps its own ${skip.joinToString()})"))
    }

    /**
     * A game that ships Microsoft's xgameruntime.dll beside its exe (Cyberpunk 2077) loads it before the staged one in
     * system32 (the exe's folder is searched first); Microsoft's needs Windows' Gaming Services and the game stops at
     * start. The launch view's entry is pointed at the staged copy instead: the install is not touched, and the entry stays
     * readable for Nexus' chunk check (which reports a chunk unavailable when one of its files can't be opened).
     */
    /** The shortcut setting that turns [useStagedRuntime] off for a game ("1" or absent = on). */
    const val NEXUS_RUNTIME_EXTRA = "xboxNexusRuntime"

    /** Whether the installed game has its own xgameruntime.dll beside its exe (see [useStagedRuntime]). */
    @JvmStatic fun shipsOwnRuntime(app: Context, id: String): Boolean {
        val game = XboxInstallState.get(app, id) ?: return false
        val exeDir = File(game.folder, game.exe).parentFile ?: return false
        return exeDir.list()?.any { it.equals("xgameruntime.dll", ignoreCase = true) } == true
    }

    private fun useStagedRuntime(app: Context, view: File, exeDir: File?, system32: File) {
        if (exeDir == null || !exeDir.canonicalPath.startsWith(view.canonicalPath + File.separator)) return
        val bundled = exeDir.listFiles()?.firstOrNull { it.name.equals("xgameruntime.dll", ignoreCase = true) } ?: return
        val staged = File(system32, "xgameruntime.dll")
        if (!staged.isFile) return
        java.nio.file.Files.delete(bundled.toPath())
        java.nio.file.Files.createSymbolicLink(bundled.toPath(), staged.toPath())
        XboxDiag.log(app, "the game ships its own xgameruntime.dll: this launch uses Xodus Nexus' instead")
    }

    /** Puts back the container's own modules that [stageModules] replaced. Safe to call for any container. */
    @JvmStatic fun restoreSystem32(system32: File) {
        for (name in DLLS + COMPAT_DLLS) {
            val original = File(system32, name + ORIGINAL)
            if (!original.isFile) continue
            val target = File(system32, name)
            target.delete()
            original.renameTo(target)
        }
    }
}
