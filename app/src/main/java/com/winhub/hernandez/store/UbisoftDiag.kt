package com.winhub.hernandez.store

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.ExitReasonReporter
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.xenvironment.ImageFs
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The Ubisoft launch report: when the user switches it on (Log Manager "Ubisoft diagnostics report", or the container
 * extra `ubisoftDiagReport=1`, see [reportRequested]; off by default), every launch of a Ubisoft game (an Epic game stamped
 * Ubisoft, or a Steam game that armed the Ubisoft launcher chain) writes its own diagnostics into the game's log folder,
 * beside `wine_debug.log`, so a Ubisoft Connect problem can be diagnosed from one zip of that folder instead of files
 * collected by hand. With the switch off none of this runs (the tiny `launch_decisions.txt` note is separate):
 *
 *  - `ubisoft_diag.txt`: the index and the readable report (header, shortcut, decisions, install-record outcome,
 *    registry sections, Ubisoft file areas and what changed in them, Connect's logs and settings, launch settings).
 *  - `ubisoft_diag_files/`: everything bigger, each file named in the index: the Ubisoft sections of the registry
 *    hives, the shortcut, the install state files and Connect's small text files; full listings of the prefix before
 *    and after; the app's logcat; a process sampler; network probes; system and memory facts.
 *
 * Two phases. PRE runs on the launch worker thread just before Wine starts: only the small readable sections run
 * inline, the heavy listings, probes and the sampler run on their own background threads. POST runs when the session
 * ends, after the Wine tree has been stopped (so wineserver has saved its hives), on a bounded background thread. If
 * POST never runs, the PRE part is already on disk. Every section is its own try/catch; nothing here can fail a launch.
 * Non-Ubisoft launches never reach this object.
 *
 * Privacy: values of password / token / secret / exchange / auth / cookie / session / ticket names and of fields that
 * name a person or an account, e-mail addresses, GUIDs and the Epic launch arguments that carry an account are replaced
 * everywhere ([UbisoftDiagScan]); only allow-listed text files are copied, every other file is listed with a hash only.
 * The folder is still the user's to review before sharing: file names in the listings are not masked.
 */
object UbisoftDiag {
    private const val TAG = "UbisoftDiag"

    /** The index and report, in the game's log folder (listed in `LogRotation.isOurRunLog`). */
    const val OUTPUT_NAME = "ubisoft_diag.txt"

    /** Folder beside [OUTPUT_NAME] with the bigger files. Only the latest run is kept. */
    const val FILES_DIR = "ubisoft_diag_files"

    /** Shared-preference key of the "Ubisoft deep trace" switch (Log Manager), default off. */
    const val PREF_DEEP_TRACE = "ubisoft_deep_trace"

    /** The same switch as a container extra (`ubisoftDeepTrace=1`). A shortcut extra is ignored: a shortcut can come from the community. */
    const val EXTRA_DEEP_TRACE = "ubisoftDeepTrace"

    /** Shared-preference key of the "Ubisoft diagnostics report" switch (Log Manager), default off. */
    const val PREF_REPORT = "ubisoft_diag_report"

    /** The same switch as a container extra (`ubisoftDiagReport=1`). A shortcut extra is ignored: a shortcut can come from the community. */
    const val EXTRA_REPORT = "ubisoftDiagReport"

    /** Wine debug channels the deep trace adds for a Ubisoft launch: registry accesses and file accesses. */
    val DEEP_TRACE_CHANNELS = listOf("reg", "file")

    private const val MB = 1024L * 1024L
    private const val TOTAL_CAP = 20L * MB
    private const val MAIN_CAP = 2_500_000L
    private const val PRE_TIME_MS = 30_000L
    private const val POST_RESERVE_MS = 4_000L

    /** How long the network probes may take in all, and how long a POST started by a destroyed activity may run. */
    private const val PROBES_TIME_MS = 25_000L
    private const val LATE_POST_MS = 30_000L

    /** Text budgets: the registry sections in the report, the Connect log text in the report (per file and in all, files shown), and the shortcut text. */
    private const val REG_SNAPSHOT_CHARS = 400_000
    private const val TEXT_FILE_TAIL = 200_000
    private const val TEXT_TOTAL_CHARS = 1_000_000
    private const val TEXT_FILES_SHOWN = 40
    private const val SHORTCUT_BYTES = 64 * 1024

    /** Longest value of an intent extra shown in the report. */
    private const val EXTRA_VALUE_CHARS = 200

    /** Logcat tags of this feature set, used for the filtered logcat file. */
    private val LOG_TAGS = listOf(
        "UbisoftSupport", "UbisoftInstallRegistry", "UbisoftInstaller", "UbisoftDiag", "WineFontFallback", "BH_REALSTEAM",
        "XServerDisplayActivity", "BH_EPIC_LAUNCH", "BH_EPIC_GAMEFIX", "BH_EPIC_CLOUD", "EaSupport", "InstallScript", "ShortcutsScreen",
    )

    // ---- Public API -----------------------------------------------------------------------------

    /** Everything [begin] needs, collected by the activity. */
    class Launch(
        val context: Context,
        val container: Container,
        val shortcut: Shortcut?,
        /** `epic` or `steam`. */
        val route: String,
        /** Steam: the Ubisoft chain was put in the agent env for this launch. */
        val steamChainSet: Boolean,
        val steamAppId: Int,
        /** Steam: the install folder the launch resolved; null for Epic (worked out here). */
        val installDirHint: String?,
        /** The game's log folder (`LogLocation.resolveGameLogDir`). */
        val logDir: File,
        val intent: Intent?,
        /** The final `NAME=value` env handed to Wine. */
        val envLines: List<String>,
        val commandLine: String,
        /** `key: value` lines: Wine, FEX, DXVK and the other launch settings. */
        val settingsLines: List<String>,
        /** What the deep trace switch did for this launch. */
        val deepTraceNote: String,
    )

    /**
     * `WINEDEBUG` with the deep-trace channels added: null when it cannot take them (not a `+channel` list, e.g.
     * `-all`), the unchanged text when they are all there already. Pure.
     */
    @JvmStatic
    fun withDeepTrace(winedebug: String?): String? {
        val wd = winedebug?.trim() ?: return null
        if (!wd.startsWith("+")) return null
        val have = wd.split(',').map { it.trim().trimStart('+', '-') }.toSet()
        var out = wd
        for (ch in DEEP_TRACE_CHANNELS) if (ch !in have) out += ",+$ch"
        return out
    }

    /**
     * A switch from its two sources: the Log Manager preference and the container extra ("1" after trimming = on). A
     * shortcut extra is never read: imported community shortcuts must not be able to turn the report or the trace on. Pure.
     */
    @JvmStatic
    fun switchOn(pref: Boolean, containerExtra: String?): Boolean = pref || containerExtra?.trim() == "1"

    /** True when the user switched the Ubisoft deep trace on (Log Manager, or the container extra). [shortcut] is ignored on purpose. */
    @JvmStatic
    fun deepTraceRequested(prefs: SharedPreferences, shortcut: Shortcut?, container: Container?): Boolean =
        switchOn(prefs.getBoolean(PREF_DEEP_TRACE, false), container?.getExtra(EXTRA_DEEP_TRACE))

    /**
     * True when the user switched the full Ubisoft diagnostics report on (Log Manager, or the container extra).
     * [shortcut] is ignored on purpose. Off by default; while it is off [begin], the sampler, the probes and the exit
     * wait never run.
     */
    @JvmStatic
    fun reportRequested(prefs: SharedPreferences, shortcut: Shortcut?, container: Container?): Boolean =
        switchOn(prefs.getBoolean(PREF_REPORT, false), container?.getExtra(EXTRA_REPORT))

    /** The line the decisions note carries about the report: whether it runs for this launch and where it is switched on. Pure. */
    @JvmStatic
    fun reportNote(on: Boolean): String =
        if (on) "Ubisoft diagnostics report: ON (ubisoft_diag.txt and ubisoft_diag_files/ in this folder)\n"
        else "Ubisoft diagnostics report: OFF (switch it on in the Log Manager, or with the container extra $EXTRA_REPORT=1)\n"

    /**
     * PRE: starts the report for this launch. Call on the launch worker thread after the registry steps and before Wine
     * starts. The inline part is a few small reads; the heavy work runs on background threads. Never throws.
     */
    @JvmStatic
    fun begin(launch: Launch) {
        try {
            val t0 = SystemClock.elapsedRealtime()
            val old = session
            if (old != null) old.samplerStop = true
            val s = Session(launch)
            session = s
            s.status["PRE (inline)"] = "running"
            s.status["PRE (background)"] = "queued"
            s.status["Network probes"] = "queued"
            s.status["Sampler"] = "not started"
            s.status["POST"] = "NOT RUN YET (the game is still running, or the app was killed before the session ended)"
            launch.logDir.mkdirs()
            retireOldFolder(s)
            step(s, "PRE", "HEADER") { s.header = headerText(s) }
            step(s, "PRE", "SHORTCUT") { s.addMain("PRE", "SHORTCUT", shortcutText(s)) }
            step(s, "PRE", "DECISIONS") { s.addMain("PRE", "DECISIONS", decisionsText(s)) }
            step(s, "PRE", "REGISTRATION") { s.addMain("PRE", "REGISTRATION (install record)", registrationText(s)) }
            step(s, "PRE", "REGISTRY SNAPSHOT") { s.addMain("PRE", "REGISTRY SNAPSHOT (after our write, before Wine starts)", registryText(s)) }
            step(s, "PRE", "LAUNCH") { s.addMain("PRE", "LAUNCH (settings, command line, environment)", launchText(s)) }
            s.status["PRE (inline)"] = "done in ${SystemClock.elapsedRealtime() - t0} ms"
            s.flushIndex()
            startSampler(s)
            s.preThread = daemon("ubisoft-diag-pre") { preBackground(s) }
            daemon("ubisoft-diag-net") { networkProbes(s) }
            Log.i(TAG, "report started: ${s.outFile.path}")
        } catch (t: Throwable) {
            Log.w(TAG, "begin failed", t)
        }
    }

    /** Stops the process sampler (no waiting). Safe to call from any thread, any number of times. */
    @JvmStatic
    fun stopSampler() {
        val s = session ?: return
        s.samplerStop = true
        s.sampler?.interrupt()
    }

    /**
     * POST: finishes the report and waits for it at most [timeoutMs]. Call from the exit worker thread once the Wine
     * tree has been stopped and flushed. A late POST keeps the sections it finished; the PRE part is on disk anyway.
     */
    @JvmStatic
    fun finishBounded(timeoutMs: Long) {
        val s = session ?: return
        try {
            if (!s.postStarted.compareAndSet(false, true)) return
            s.postDeadline = System.currentTimeMillis() + timeoutMs - POST_RESERVE_MS
            val t = daemon("ubisoft-diag-post") { post(s, "the session ended and the Wine tree was stopped") }
            t.join(timeoutMs)
            if (t.isAlive) {
                s.abort = true
                s.status["POST"] = "CUT by the time limit ($timeoutMs ms); the sections below it finished before that"
                s.flushIndex()
                Log.w(TAG, "POST still running after $timeoutMs ms; leaving it")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "finish failed", t)
        }
    }

    /**
     * The activity is going away. When the normal exit routine did not run POST (the session was torn down some other
     * way), runs it in the background without waiting; the Wine tree may still have been alive, which POST records.
     */
    @JvmStatic
    fun onActivityDestroyed() {
        val s = session ?: return
        stopSampler()
        try {
            if (!s.postStarted.compareAndSet(false, true)) return
            s.postDeadline = System.currentTimeMillis() + LATE_POST_MS
            daemon("ubisoft-diag-post") { post(s, "the activity was destroyed before the exit routine ran (Wine may still have been running)") }
        } catch (t: Throwable) {
            Log.w(TAG, "late finish failed", t)
        }
    }

    // ---- State ----------------------------------------------------------------------------------

    @Volatile
    private var session: Session? = null

    private enum class Bucket(val limit: Long) {
        LISTINGS(2_500_000L), REGISTRY(5_800_000L), COPIES(3_500_000L), LOGCAT(4_500_000L),
        LOGCAT_FILTERED(500_000L), SAMPLER(1_000_000L), MISC(1_000_000L),
    }

    /** The STATUS lines of the report, set from several threads. */
    private class StatusMap {
        private val map = LinkedHashMap<String, String>()
        operator fun set(key: String, value: String) { synchronized(map) { map[key] = value } }
        operator fun get(key: String): String? = synchronized(map) { map[key] }
        fun lines(): List<String> = synchronized(map) { map.map { it.key + ": " + it.value } }
    }

    private class Session(val launch: Launch) {
        val startedMs = System.currentTimeMillis()
        val dir = File(launch.logDir, FILES_DIR)
        val outFile = File(launch.logDir, OUTPUT_NAME)
        val lock = Any()
        private val main = StringBuilder()
        private var mainBytes = 0L
        private var mainFull = false
        private val used = LongArray(Bucket.entries.size)
        private var total = 0L
        val files = LinkedHashMap<String, String>()
        val status = StatusMap()
        val postStarted = AtomicBoolean(false)

        @Volatile var preThread: Thread? = null
        @Volatile var abort = false
        @Volatile var postDeadline = Long.MAX_VALUE
        @Volatile var sampler: Thread? = null
        @Volatile var samplerStop = false
        @Volatile var samplerSamples = 0
        @Volatile var header = ""

        /** The game's install folder and how it was found, worked out once. */
        val install: Pair<File?, String> by lazy { resolveInstall(launch) }

        val prefix: File get() = File(launch.container.rootDir, ".wine")
        val driveC: File get() = File(prefix, "drive_c")

        fun room(b: Bucket): Long = synchronized(lock) { minOf(b.limit - used[b.ordinal], TOTAL_CAP - total).coerceAtLeast(0L) }

        fun charge(b: Bucket, n: Long) = synchronized(lock) { used[b.ordinal] += n; total += n }

        fun usedIn(b: Bucket): Long = synchronized(lock) { used[b.ordinal] }

        fun timeLeftMs(): Long = postDeadline - System.currentTimeMillis()

        /** Adds a readable section to the report (cut when the report is full). */
        fun addMain(phase: String, title: String, body: String) {
            val block = "\n===== [$phase] $title =====\n$body\n"
            val bytes = block.toByteArray(Charsets.UTF_8).size.toLong()
            synchronized(lock) {
                if (mainFull) return
                if (mainBytes + bytes > MAIN_CAP || total + bytes > TOTAL_CAP) {
                    val room = minOf(MAIN_CAP - mainBytes, TOTAL_CAP - total).coerceAtLeast(0L)
                    val (cut, _) = UbisoftDiagScan.capUtf8(block, room.toInt())
                    main.append(cut).append("\n[report full: this section and everything after it was cut]\n")
                    mainBytes = MAIN_CAP
                    mainFull = true
                    return
                }
                main.append(block)
                mainBytes += bytes
                total += bytes
            }
        }

        fun register(rel: String, desc: String, bytes: Long, cut: Boolean) {
            synchronized(lock) { files[rel] = "$rel  ($bytes bytes)  $desc" + if (cut) "  [TRUNCATED: size limit]" else "" }
        }

        /** Writes [text] as [rel] below the side folder within [b]'s budget. */
        fun writeSide(rel: String, desc: String, text: String, b: Bucket): Boolean {
            val room = room(b)
            if (room <= 0L) { register(rel, desc, 0, true); return false }
            val (body, cut) = UbisoftDiagScan.capUtf8(text, minOf(room, Int.MAX_VALUE.toLong()).toInt())
            val f = File(dir, rel)
            f.parentFile?.mkdirs()
            val bytes = (if (cut) body + "\n[truncated: size limit]\n" else body).toByteArray(Charsets.UTF_8)
            f.writeBytes(bytes)
            charge(b, bytes.size.toLong())
            register(rel, desc, bytes.size.toLong(), cut)
            return true
        }

        /** Rewrites the index + report file (temp file then rename, so a cut never leaves half a file). */
        fun flushIndex() {
            val text = synchronized(lock) {
                val sb = StringBuilder()
                sb.append("=== WinHub Ubisoft diagnostics (").append(OUTPUT_NAME).append(") ===\n")
                sb.append("written: ").append(stamp(System.currentTimeMillis())).append("   launch started: ").append(stamp(startedMs)).append('\n')
                sb.append("Zip the whole log folder (this file and ").append(FILES_DIR).append("/) to send it. Only the latest run's side files are kept.\n")
                sb.append("Listings *.gz are plain text, gzip-compressed (zcat / any unzip tool). Secrets, e-mail addresses and GUIDs are replaced by ").append(UbisoftDiagScan.REDACTED)
            .append(", but file names in the listings are not masked: look through the folder before you share it.\n")
                sb.append("\n--- STATUS ---\n")
                for (line in status.lines()) sb.append(line).append('\n')
                sb.append("size used: ").append(total).append(" of ").append(TOTAL_CAP).append(" bytes\n")
                sb.append("\n--- FILES in ").append(FILES_DIR).append("/ ---\n")
                if (files.isEmpty()) sb.append("(none yet)\n")
                for (v in files.values) sb.append(v).append('\n')
                if (header.isNotEmpty()) sb.append("\n===== HEADER =====\n").append(header).append('\n')
                sb.append(main)
                sb.toString()
            }
            try {
                outFile.parentFile?.mkdirs()
                val tmp = File(outFile.parentFile, "$OUTPUT_NAME.tmp")
                tmp.writeText(text, Charsets.UTF_8)
                if (!tmp.renameTo(outFile)) { outFile.writeText(text, Charsets.UTF_8); tmp.delete() }
            } catch (t: Throwable) {
                Log.w(TAG, "could not write ${outFile.path}", t)
            }
        }
    }

    private fun stamp(ms: Long): String = UbisoftDiagScan.fmtTime(ms) + " UTC"

    private fun daemon(name: String, body: () -> Unit): Thread {
        val t = Thread({
            try { body() } catch (t: Throwable) { Log.w(TAG, "$name failed", t) }
        }, name)
        t.isDaemon = true
        t.start()
        return t
    }

    /** Runs one report section: its failure is written into the report and the next section goes on. */
    private fun step(s: Session, phase: String, name: String, block: () -> Unit) {
        if (s.abort) return
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "section $name failed", t)
            s.addMain(phase, "$name -- FAILED", t.toString() + "\n" + t.stackTrace.take(6).joinToString("\n") { "  at $it" })
        }
    }

    /** The previous run's side folder is renamed away at once and deleted by the background thread. */
    private fun retireOldFolder(s: Session) {
        if (s.dir.exists()) {
            val gone = File(s.launch.logDir, "$FILES_DIR.deleting-${System.currentTimeMillis()}")
            if (!s.dir.renameTo(gone)) deleteTree(s.dir)
        }
    }

    private fun deleteLeftovers(s: Session) {
        s.launch.logDir.listFiles { f -> f.isDirectory && f.name.startsWith("$FILES_DIR.deleting-") }?.forEach { deleteTree(it) }
    }

    private fun deleteTree(f: File) {
        try {
            if (f.isDirectory && !Files.isSymbolicLink(f.toPath())) f.listFiles()?.forEach { deleteTree(it) }
            f.delete()
        } catch (_: Throwable) {
        }
    }

    // ---- Install folder -------------------------------------------------------------------------

    /** The game's folder and the way it was found. Read only: nothing is stamped or saved. */
    private fun resolveInstall(l: Launch): Pair<File?, String> {
        l.installDirHint?.let { return File(it) to "resolved by the Steam launch (library install folder)" }
        val sc = l.shortcut ?: return null to "no shortcut"
        val why = StringBuilder()
        val appName = sc.getExtra("epicAppName", "")
        if (appName.isEmpty()) {
            why.append("route 1 (store's saved folder): shortcut has no epicAppName; ")
        } else {
            val saved = EpicCloudSavePaths.installDir(l.context, appName)
            why.append("route 1 (store's saved folder for epicAppName='").append(appName).append("'): ")
                .append(saved?.path ?: "none saved").append(if (saved != null) " isDirectory=${saved.isDirectory}" else "").append("; ")
            if (saved != null && saved.isDirectory) return saved to why.append("=> used route 1").toString()
        }
        val exe = runCatching { WinePath.resolveAndroidPath(l.container, sc.path) }.getOrNull()
        why.append("route 2 (below epic_games on the exe path): exe=").append(exe?.absolutePath ?: "unmapped").append("; ")
        val below = exe?.let { UbisoftSupport.gameFolderUnder(it.absolutePath, UbisoftSupport.EPIC_ROOT) }
        if (below != null && below.isDirectory) return below to why.append("=> used route 2").toString()
        return null to why.append("=> no folder found").toString()
    }

    // ---- PRE sections ---------------------------------------------------------------------------

    private fun headerText(s: Session): String {
        val l = s.launch
        val ctx = l.context
        val pkg = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (t: Throwable) { null }
        return buildString {
            append("timestamp: ").append(stamp(System.currentTimeMillis())).append(" (epoch ").append(System.currentTimeMillis()).append(")\n")
            append("phase: PRE written first; POST appended when the session ends (see STATUS)\n")
            append("app: ").append(com.winhub.hernandez.BuildConfig.VERSION_NAME).append(" (versionCode ").append(com.winhub.hernandez.BuildConfig.VERSION_CODE).append(")\n")
            append("build: applicationId=").append(com.winhub.hernandez.BuildConfig.APPLICATION_ID).append(" flavor=").append(com.winhub.hernandez.BuildConfig.FLAVOR)
                .append(" type=").append(com.winhub.hernandez.BuildConfig.BUILD_TYPE).append(" debug=").append(com.winhub.hernandez.BuildConfig.DEBUG).append('\n')
            append("installed: first=").append(pkg?.firstInstallTime?.let { stamp(it) } ?: "?").append(" lastUpdate=").append(pkg?.lastUpdateTime?.let { stamp(it) } ?: "?").append('\n')
            append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" (").append(Build.DEVICE).append(", ").append(Build.HARDWARE).append(")\n")
            append("android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(") patch=").append(Build.VERSION.SECURITY_PATCH).append('\n')
            append("fingerprint: ").append(Build.FINGERPRINT).append('\n')
            append("abis: ").append(Build.SUPPORTED_ABIS.joinToString(",")).append('\n')
            append("container: id=").append(l.container.id).append(" name=").append(l.container.name).append(" root=").append(l.container.rootDir.path).append('\n')
            append("route: ").append(l.route).append("   shortcut: ").append(l.shortcut?.name ?: "<none>").append('\n')
            append("log folder: ").append(l.logDir.path)
        }
    }

    private fun shortcutText(s: Session): String {
        val l = s.launch
        val sc = l.shortcut ?: return "no shortcut for this launch"
        val sb = StringBuilder()
        sb.append("shortcut file: ").append(sc.file.path).append(" exists=").append(sc.file.isFile).append(" size=").append(sc.file.length())
            .append(" modified=").append(stamp(sc.file.lastModified())).append('\n')
        sb.append("name: ").append(sc.name).append('\n')
        sb.append("exe (Windows path in the shortcut): ").append(sc.path).append('\n')
        val host = runCatching { WinePath.resolveAndroidPath(l.container, sc.path) }.getOrNull()
        sb.append("exe (resolved host path): ").append(host?.absolutePath ?: "<unmapped>").append(" exists=").append(host?.isFile ?: false).append('\n')
        sb.append("--- full text of the .desktop file (secret-looking values replaced) ---\n")
        val text = try { sc.file.inputStream().use { UbisoftDiagScan.readUpTo(it, SHORTCUT_BYTES) } } catch (t: Throwable) { "(unreadable: $t)" }
        sb.append(UbisoftDiagScan.redactText(text))
        return sb.toString()
    }

    private fun decisionsText(s: Session): String {
        val l = s.launch
        val sc = l.shortcut
        val sb = StringBuilder()
        sb.append("route (what made this a Ubisoft launch): ").append(l.route).append('\n')
        if (sc != null) {
            sb.append("isEpicShortcut: ").append(UbisoftSupport.isEpicShortcut(sc)).append("   storeSource='").append(sc.getExtra("storeSource", "")).append("'\n")
            sb.append("ubisoft stamp: raw='").append(sc.getExtra(UbisoftSupport.EXTRA_UBISOFT, "")).append("' parsed=").append(UbisoftSupport.epicStamp(sc)).append('\n')
            sb.append("epicAppName='").append(sc.getExtra("epicAppName", "")).append("'  steamAppId(extra)='").append(sc.getExtra("steamAppId", ""))
                .append("'  launchMode='").append(sc.getExtra("launchMode", "")).append("'  container_id=").append(sc.container.id).append('\n')
        }
        if (l.route == "steam") {
            sb.append("Steam: WN_STEAM_LAUNCH_CHAIN armed in the agent env: ").append(l.steamChainSet)
                .append(" (value ").append(if (l.steamChainSet) UbisoftSupport.CHAIN_VALUE else "not set").append(")  steam app id (launch plan): ").append(l.steamAppId).append('\n')
        }
        val (dir, how) = s.install
        sb.append("install folder: ").append(dir?.path ?: "<none>").append(" exists=").append(dir?.isDirectory ?: false).append('\n')
        sb.append("  found by: ").append(how).append('\n')
        sb.append("launcherInstalled(container): ").append(UbisoftSupport.launcherInstalled(l.container))
            .append("   launcher files in Program Files (x86)/Ubisoft/Ubisoft Game Launcher: ")
            .append(UbisoftSupport.launcherFileStatus(l.container).joinToString(", ") { it.first + "=" + (if (it.second) "present" else "missing") }).append('\n')
        sb.append("connectBuild (version.txt, read at the launch): ").append(UbisoftSupport.connectBuildText(UbisoftSupport.connectBuild(l.container))).append('\n')
        sb.append("(hasLauncherFiles, bundledInstaller and the setup answer are in DECISIONS (file based) below, worked out in the background)\n")
        sb.append("launch entry point: ").append(intentText(l.intent))
        return UbisoftDiagScan.redactText(sb.toString())
    }

    @Suppress("DEPRECATION")
    private fun intentText(i: Intent?): String {
        if (i == null) return "no intent"
        val sb = StringBuilder()
        sb.append("action=").append(i.action).append(" data=").append(i.dataString?.let { UbisoftDiagScan.redactLine(it) }).append(" type=").append(i.type)
            .append(" component=").append(i.component?.flattenToShortString()).append(" package=").append(i.`package`)
            .append(" flags=0x").append(Integer.toHexString(i.flags)).append(" categories=").append(i.categories).append('\n')
        try {
            val ex = i.extras
            if (ex == null) sb.append("  extras: none") else {
                for (k in ex.keySet().sorted()) {
                    val v = try { ex.get(k) } catch (t: Throwable) { "(unreadable)" }
                    val shown = if (UbisoftDiagScan.isSecretName(k)) UbisoftDiagScan.REDACTED else UbisoftDiagScan.redactLine(v.toString()).take(EXTRA_VALUE_CHARS)
                    sb.append("  extra ").append(k).append(" (").append(v?.javaClass?.simpleName).append(") = ").append(shown).append('\n')
                }
            }
        } catch (t: Throwable) {
            sb.append("  extras unreadable: ").append(t)
        }
        return sb.toString().trimEnd()
    }

    private fun registrationText(s: Session): String {
        val l = s.launch
        val sb = StringBuilder()
        val last = UbisoftInstallRegistry.last()
        sb.append("outcome of this launch's registration: ").append(last?.toString() ?: "none recorded (UbisoftSupport.registerEpicInstall did not run for this launch)").append('\n')
        if (l.route == "steam") sb.append("(Steam route: the app writes no install record, only Epic installs are registered)\n")
        val dir = s.install.first ?: return sb.append("uplay_install.state: no install folder").toString()
        val stateFile = File(dir, UbisoftInstallRegistry.STATE_FILE)
        sb.append("state file: ").append(stateFile.path).append(" exists=").append(stateFile.isFile)
        if (!stateFile.isFile) return sb.toString()
        sb.append(" size=").append(stateFile.length()).append(" modified=").append(stamp(stateFile.lastModified()))
            .append(" sha256=").append(UbisoftDiagScan.sha256(stateFile, 16 * MB)).append('\n')
        val bytes = try { stateFile.readBytes() } catch (t: Throwable) { null }
        val st = UbisoftInstallState.read(stateFile)
        if (st == null) return sb.append("parsed: NOT a usable state file (see NO_STATE_FILE)").toString()
        sb.append("parsed: productId=").append(st.productId).append(" name='").append(st.name).append("' displayName='").append(st.displayName)
            .append("' language=").append(st.language).append(" hashField1=").append(bytes?.let { UbisoftInstallState.installHash(it) } ?: "?")
            .append(" registryEntries=").append(st.registry.size).append('\n')
        for (e in st.registry.take(24)) sb.append("  registry entry: ").append(e.path).append(" [").append(e.language).append("] = ").append(e.value).append('\n')
        val zDir = UbisoftInstallRegistry.zPathOf(dir, ImageFs.find(l.context).rootDir)
        sb.append("Z: path of the folder: ").append(zDir ?: "<outside the imagefs root>").append('\n')
        if (zDir != null) {
            val writes = UbisoftInstallRegistry.installWrites(st, zDir)
            sb.append("values the app writes (").append(writes.size).append("):\n")
            for (w in writes) sb.append("  HKLM\\").append(w.key).append('\\').append(w.name).append(" = ").append(w.data).append(" (").append(w.type).append(")\n")
        }
        return sb.toString().trimEnd()
    }

    /** The keys the app's own install-record write creates for this game (only the key names matter here, so the folder is a placeholder). */
    private fun expectedKeys(s: Session): List<String> {
        val dir = s.install.first ?: return emptyList()
        val st = UbisoftInstallState.read(File(dir, UbisoftInstallRegistry.STATE_FILE)) ?: return emptyList()
        return UbisoftInstallRegistry.installWrites(st, "Z:\\").map { it.key }.distinct()
    }

    /** Ubisoft / Uplay sections of the prefix's two hives, secret values replaced, and whether the expected keys are there. */
    private fun registryText(s: Session): String {
        val sb = StringBuilder()
        val expected = expectedKeys(s)
        for (name in listOf("system.reg", "user.reg")) {
            val f = File(s.prefix, name)
            sb.append("--- ").append(name).append(": ").append(f.path)
            if (!f.isFile) { sb.append("  MISSING FILE\n"); continue }
            sb.append("  size=").append(f.length()).append(" modified=").append(stamp(f.lastModified())).append('\n')
            val r = f.bufferedReader(Charsets.UTF_8).use { UbisoftDiagScan.registrySections(it, REG_SNAPSHOT_CHARS) }
            sb.append(r.keys.size).append(" Ubisoft/Uplay section(s)").append(if (r.truncated) " [TRUNCATED]" else "").append('\n')
            if (name == "system.reg") for (k in expected) sb.append(if (UbisoftDiagScan.hasKey(r.keys, k)) "  PRESENT: " else "  MISSING: ").append(k).append('\n')
            sb.append(if (r.text.isEmpty()) "(no Ubisoft or Uplay key in this hive)\n" else r.text)
        }
        return sb.toString().trimEnd()
    }

    private fun launchText(s: Session): String {
        val l = s.launch
        val sb = StringBuilder()
        sb.append("deep trace: ").append(l.deepTraceNote).append('\n')
        sb.append("--- settings ---\n")
        for (line in l.settingsLines) sb.append(UbisoftDiagScan.redactLine(line)).append('\n')
        sb.append("--- guest command (getWineStartCommand wrapped by the launcher) ---\n").append(UbisoftDiagScan.redactLine(l.commandLine)).append('\n')
        sb.append("--- environment handed to Wine (").append(l.envLines.size).append(" variables; secret-named values replaced) ---\n")
        for (line in l.envLines.sorted()) {
            val name = line.substringBefore('=')
            sb.append(if (UbisoftDiagScan.isSecretName(name)) "$name=${UbisoftDiagScan.REDACTED}" else UbisoftDiagScan.redactLine(line)).append('\n')
        }
        return sb.toString().trimEnd()
    }

    // ---- PRE background -------------------------------------------------------------------------

    private fun preBackground(s: Session) {
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        s.status["PRE (background)"] = "running"
        val t0 = SystemClock.elapsedRealtime()
        deleteLeftovers(s)
        step(s, "PRE", "DECISIONS (file based)") { s.addMain("PRE", "DECISIONS (file based)", fileDecisionsText(s)) }
        step(s, "PRE", "SYSTEM") { s.writeSide("system_pre.txt", "system, memory, storage, network, clock before the launch", systemText(s), Bucket.MISC) }
        step(s, "PRE", "PROCESSES") { s.writeSide("processes_pre.txt", "processes visible to the app before the launch", processText(), Bucket.MISC) }
        step(s, "PRE", "CONTAINER CONFIG") { s.writeSide("container_config.json", "the container's full settings (.container, secret values replaced)", containerConfigText(s), Bucket.MISC) }
        s.flushIndex()
        val deadline = System.currentTimeMillis() + PRE_TIME_MS
        step(s, "PRE", "FILES (Ubisoft areas)") { filesAreas(s, "pre", deadline) }
        step(s, "PRE", "FILES (game folder top 3 levels)") { filesGame(s, "pre", deadline) }
        step(s, "PRE", "FILES (drive_c)") { filesDriveC(s, "pre", deadline) }
        s.status["PRE (background)"] = "done in ${SystemClock.elapsedRealtime() - t0} ms"
        s.flushIndex()
    }

    private fun fileDecisionsText(s: Session): String {
        val l = s.launch
        val sb = StringBuilder()
        val dir = s.install.first
        sb.append("hasLauncherFiles(install folder): ").append(UbisoftSupport.hasLauncherFiles(dir)).append('\n')
        val bundled = UbisoftSupport.bundledInstaller(dir)
        sb.append("bundledInstaller: ").append(UbisoftSupport.bundledInstallerText(bundled?.path, bundled?.length() ?: 0L)).append('\n')
        sb.append("connectBuild (version.txt): ").append(UbisoftSupport.connectBuildText(UbisoftSupport.connectBuild(l.container))).append('\n')
        if (l.route == "steam") sb.append("UbisoftSupport.detect(install folder): ").append(UbisoftSupport.detect(dir)).append('\n')
        val sc = l.shortcut
        if (sc != null && l.route == "epic") {
            // The same answer as epicSetupFor, worked out without its side effects (it deletes a downloaded installer).
            val setup = UbisoftSupport.epicDecisionNow(l.container, UbisoftSupport.epicStamp(sc), dir)
            sb.append("epicSetupFor (recomputed read-only, as it would be now): ").append(setup).append('\n')
        }
        if (l.route == "steam") {
            sb.append("steamSetupFor (recomputed read-only, as it would be now): ").append(UbisoftSupport.steamDecisionNow(l.container, dir)).append('\n')
        }
        return sb.toString().trimEnd()
    }

    private fun containerConfigText(s: Session): String {
        val json = s.launch.container.data.toString(2)
        return UbisoftDiagScan.redactText(json)
    }

    // ---- Listings -------------------------------------------------------------------------------

    private val GAME_FILE_NAME = Regex("^(uplay.*|upc.*|.*ubisoft.*)$", RegexOption.IGNORE_CASE)

    /** The areas of the prefix Ubisoft Connect uses, paths relative to `drive_c`. */
    private fun areaRoots(s: Session): List<String> {
        val user = "users/${ImageFs.USER}"
        val out = LinkedHashSet<String>()
        out += "Program Files (x86)/Ubisoft"
        out += "Program Files/Ubisoft"
        out += "ProgramData/Ubisoft"
        out += "$user/AppData/Local/Ubisoft Game Launcher"
        for (parent in listOf("$user/AppData/Local", "$user/AppData/Roaming", "$user/Documents", "$user/My Documents")) {
            File(s.driveC, parent).listFiles()?.sortedBy { it.name }?.forEach { f ->
                if (f.isDirectory && f.name.startsWith("Ubisoft", ignoreCase = true)) out += "$parent/${f.name}"
            }
        }
        return out.toList()
    }

    /** An open listing file: gzip text, one [UbisoftDiagScan.formatEntry] line per entry, `#` lines for headers. */
    private class ListingOut(val file: File) : java.io.Closeable {
        private val w: BufferedWriter
        var lines = 0

        init {
            file.parentFile?.mkdirs()
            w = BufferedWriter(OutputStreamWriter(GZIPOutputStream(FileOutputStream(file), 64 * 1024), Charsets.UTF_8), 64 * 1024)
        }

        fun header(text: String) { w.write("# "); w.write(text); w.write("\n") }
        fun entry(path: String, e: UbisoftDiagScan.Entry) { w.write(UbisoftDiagScan.formatEntry(path, e)); w.write("\n"); lines++ }
        override fun close() { w.close() }
    }

    /** The earlier listing [rel] read back (entry lines only), or empty when it is not there. */
    private fun readListing(s: Session, rel: String): Map<String, UbisoftDiagScan.Entry> {
        val f = File(s.dir, rel)
        val out = HashMap<String, UbisoftDiagScan.Entry>()
        if (!f.isFile) return out
        try {
            BufferedReader(InputStreamReader(GZIPInputStream(FileInputStream(f)), Charsets.UTF_8)).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    UbisoftDiagScan.parseEntryLine(line)?.let { out[it.first] = it.second }
                }
            }
        } catch (e: java.io.IOException) {
            // A listing cut short (the app was killed while writing it): compare with what was read.
        }
        return out
    }

    /**
     * Writes one listing and, in POST, compares it with the PRE one. [walkAll] feeds every entry to the callback and
     * returns the lines describing the walk plus whether it finished. Returns the comparison text (null in PRE).
     */
    private fun listing(
        s: Session, phase: String, name: String, desc: String,
        diffLines: Int,
        walkAll: (ListingOut, UbisoftDiagScan.Comparison?) -> Pair<List<String>, Boolean>,
    ): Pair<List<String>, String?> {
        val rel = "files_${phase}_$name.txt.gz"
        val cmp = if (phase == "post") UbisoftDiagScan.Comparison(readListing(s, "files_pre_$name.txt.gz"), diffLines) else null
        val f = File(s.dir, rel)
        var lines: List<String> = emptyList()
        var complete = true
        if (s.room(Bucket.LISTINGS) <= 0L) {
            s.register(rel, desc, 0, true)
            return listOf("not written: listing size budget used up") to null
        }
        ListingOut(f).use { out ->
            out.header("$desc ($phase) started ${stamp(System.currentTimeMillis())}")
            val r = walkAll(out, cmp)
            lines = r.first
            complete = r.second
            out.header("finished ${stamp(System.currentTimeMillis())} complete=$complete")
        }
        s.charge(Bucket.LISTINGS, f.length())
        s.register(rel, desc, f.length(), false)
        cmp?.finish(complete)
        return lines to cmp?.render(name, complete)
    }

    private fun filesAreas(s: Session, phase: String, deadline: Long) {
        val roots = areaRoots(s)
        val (lines, diff) = listing(s, phase, "ubisoft", "Ubisoft areas of the prefix: ${roots.joinToString(" | ")} + the game folder Ubisoft files (GAME/...)", 800) { out, cmp ->
            val summary = ArrayList<String>()
            var complete = true
            for (rel in roots) {
                val root = realDirUnderDriveC(s, rel)
                if (root == null) { summary += "$rel: absent (or a link, not followed)"; continue }
                out.header("area $rel")
                UbisoftDiagScan.entryOf(root)?.let { out.entry(rel, it); cmp?.onNow(rel, it) }
                val r = UbisoftDiagScan.walk(root, 8, 4000, deadline) { p, e -> out.entry("$rel/$p", e); cmp?.onNow("$rel/$p", e) }
                summary += "$rel: " + UbisoftDiagScan.describeWalk(r)
                if (r.timedOut) complete = false
            }
            val game = s.install.first
            if (game != null && game.isDirectory) {
                out.header("game folder ${game.path}")
                gameUbisoftEntries(game) { p, e -> out.entry("GAME/$p", e); cmp?.onNow("GAME/$p", e) }
                summary += "GAME (${game.path}): listed"
            } else {
                summary += "GAME: no install folder"
            }
            summary to complete
        }
        val title = if (phase == "pre") "FILES [PRE] Ubisoft areas (full listing in files_pre_ubisoft.txt.gz)" else "FILES [POST] Ubisoft areas and what changed since PRE"
        s.addMain(phase.uppercase(), title, lines.joinToString("\n") + gameHashesText(s) + (if (diff != null) "\n" + diff else ""))
        s.flushIndex()
    }

    /** The game folder's Ubisoft-related top-level files and, one level deep, its `Uplay` and `Support` folders. */
    private fun gameUbisoftEntries(game: File, visit: (String, UbisoftDiagScan.Entry) -> Unit) {
        val kids = game.listFiles()?.sortedBy { it.name } ?: return
        for (f in kids) {
            if (!GAME_FILE_NAME.matches(f.name) && !f.name.equals("Uplay", true) && !f.name.equals("Support", true)) continue
            val e = UbisoftDiagScan.entryOf(f) ?: continue
            visit(f.name, e)
            if (e.kind == 'd' && (f.name.equals("Uplay", true) || f.name.equals("Support", true))) {
                f.listFiles()?.sortedBy { it.name }?.forEach { c -> UbisoftDiagScan.entryOf(c)?.let { visit(f.name + "/" + c.name, it) } }
            }
        }
    }

    private fun gameHashesText(s: Session): String {
        val game = s.install.first ?: return ""
        val sb = StringBuilder("\ngame folder Ubisoft files (${game.path}):\n")
        var any = false
        val kids = game.listFiles()?.sortedBy { it.name } ?: emptyList()
        for (f in kids) {
            if (!f.isFile || !GAME_FILE_NAME.matches(f.name)) continue
            any = true
            val hashed = f.name.equals(UbisoftInstallRegistry.STATE_FILE, true) || f.name.endsWith(".manifest", true)
            sb.append("  ").append(f.name).append("  size=").append(f.length()).append("  modified=").append(stamp(f.lastModified()))
            if (hashed) sb.append("  sha256=").append(UbisoftDiagScan.sha256(f, 64 * MB))
            sb.append('\n')
        }
        if (!any) sb.append("  (no uplay*, upc* or *ubisoft* file in the top level)\n")
        for (sub in listOf("Uplay", "Support")) {
            val d = File(game, sub)
            sb.append("  ").append(sub).append("/: ").append(if (d.isDirectory) "${d.list()?.size ?: 0} entries" else "absent").append('\n')
        }
        return sb.toString().trimEnd()
    }

    private fun filesGame(s: Session, phase: String, deadline: Long) {
        val game = s.install.first
        if (game == null || !game.isDirectory) { s.addMain(phase.uppercase(), "FILES [${phase.uppercase()}] game folder top 3 levels", "no install folder"); return }
        val (lines, diff) = listing(s, phase, "game_top3", "game install folder ${game.path}, top 3 levels", 1500) { out, cmp ->
            val r = UbisoftDiagScan.walk(game, 3, 20_000, deadline) { p, e -> out.entry(p, e); cmp?.onNow(p, e) }
            listOf(UbisoftDiagScan.describeWalk(r)) to !r.timedOut
        }
        if (diff != null) s.writeSide("files_diff_game_top3.txt", "game folder top 3 levels: POST minus PRE", diff, Bucket.LISTINGS)
        s.addMain(phase.uppercase(), "FILES [${phase.uppercase()}] game folder top 3 levels", lines.joinToString("\n") + (if (diff != null) "\n" + diff.lineSequence().firstOrNull() else ""))
        s.flushIndex()
    }

    private fun filesDriveC(s: Session, phase: String, deadline: Long) {
        val (lines, diff) = listing(s, phase, "drive_c", "the whole prefix drive_c (depth 12, up to 100000 entries)", 4000) { out, cmp ->
            val root = realDirUnderDriveC(s, "")
            if (root == null) {
                listOf("drive_c is absent or a link: not listed") to true
            } else {
                val r = UbisoftDiagScan.walk(root, 12, 100_000, deadline) { p, e -> out.entry(p, e); cmp?.onNow(p, e) }
                listOf(UbisoftDiagScan.describeWalk(r)) to !r.timedOut
            }
        }
        if (diff != null) s.writeSide("files_diff_drive_c.txt", "drive_c: POST minus PRE (added / changed / removed)", diff, Bucket.LISTINGS)
        s.addMain(phase.uppercase(), "FILES [${phase.uppercase()}] drive_c", lines.joinToString("\n") + (if (diff != null) "\n" + diff.lineSequence().firstOrNull() + "\n(full diff in files_diff_drive_c.txt)" else ""))
        s.flushIndex()
    }

    // ---- Network --------------------------------------------------------------------------------

    private fun networkProbes(s: Session) {
        s.status["Network probes"] = "running"
        try {
            val t0 = SystemClock.elapsedRealtime()
            val hosts = UbisoftDiagNet.HOSTS + UbisoftDiagNet.CONTROL_HOST
            val lines = UbisoftDiagNet.probeAll(hosts, PROBES_TIME_MS)
            val text = "probed at ${stamp(System.currentTimeMillis())}: DNS + TCP 443 + TLS handshake per host, no data sent after the handshake.\n" +
                "control host (not Ubisoft): ${UbisoftDiagNet.CONTROL_HOST}\n" + lines.joinToString("\n")
            s.writeSide("network_probes.txt", "reachability of the Ubisoft hosts (DNS, TCP 443, TLS, certificate)", text, Bucket.MISC)
            val ok = lines.count { it.trim().startsWith("tls: OK") }
            s.addMain("PRE", "NETWORK PROBES", "$ok of ${hosts.size} hosts completed a TLS handshake (also in network_probes.txt)\n" + lines.joinToString("\n"))
            s.status["Network probes"] = "done in ${SystemClock.elapsedRealtime() - t0} ms"
        } catch (t: Throwable) {
            s.status["Network probes"] = "failed: $t"
        }
        s.flushIndex()
    }

    // ---- System facts ---------------------------------------------------------------------------

    private fun systemText(s: Session): String {
        val ctx = s.launch.context
        val sb = StringBuilder()
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        sb.append("--- clock ---\n")
        sb.append("now: ").append(stamp(now)).append("  (epoch ").append(now).append(")\n")
        sb.append("timezone: ").append(tz.id).append(" offset=").append(tz.getOffset(now) / 60000).append(" min\n")
        val lastUpdate = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime } catch (t: Throwable) { 0L }
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val sane = year in 2024..2070 && now >= lastUpdate - 86_400_000L
        sb.append("clock looks sane: ").append(sane).append(" (year ").append(year).append(", app installed/updated ").append(if (lastUpdate > 0) stamp(lastUpdate) else "?")
            .append("; a clock before the install time or years off breaks Ubisoft logins)\n")
        for (k in listOf("auto_time", "auto_time_zone")) {
            val v = try { android.provider.Settings.Global.getInt(ctx.contentResolver, k, -1) } catch (t: Throwable) { -2 }
            sb.append(k).append(": ").append(v).append(" (1 = automatic)\n")
        }
        sb.append("uptime: ").append(SystemClock.elapsedRealtime() / 1000).append(" s\n")
        sb.append("\n--- device ---\n")
        sb.append("model: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" board=").append(Build.BOARD).append(" hardware=").append(Build.HARDWARE).append('\n')
        if (Build.VERSION.SDK_INT >= 31) sb.append("soc: ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL).append('\n')
        sb.append("cpu cores: ").append(Runtime.getRuntime().availableProcessors()).append('\n')
        sb.append(cpuSummary()).append('\n')
        sb.append("\n--- memory ---\n")
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            sb.append("ActivityManager: avail=").append(mi.availMem / MB).append(" MB total=").append(mi.totalMem / MB).append(" MB lowMemory=").append(mi.lowMemory)
                .append(" threshold=").append(mi.threshold / MB).append(" MB\n")
        } catch (t: Throwable) {
            sb.append("ActivityManager: ").append(t).append('\n')
        }
        try {
            val dm = Debug.MemoryInfo()
            Debug.getMemoryInfo(dm)
            sb.append("app process: pss=").append(dm.totalPss).append(" kB privateDirty=").append(dm.totalPrivateDirty).append(" kB sharedDirty=").append(dm.totalSharedDirty).append(" kB\n")
            sb.append("java heap: used=").append((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB).append(" MB max=").append(Runtime.getRuntime().maxMemory() / MB).append(" MB\n")
        } catch (t: Throwable) {
            sb.append("process memory: ").append(t).append('\n')
        }
        sb.append("/proc/meminfo:\n").append(readSmall("/proc/meminfo", 4096)?.lineSequence()?.take(16)?.joinToString("\n") { "  $it" } ?: "  unreadable").append('\n')
        sb.append("\n--- storage ---\n")
        for ((label, f) in listOf("log folder" to s.launch.logDir, "container" to s.launch.container.rootDir, "app files" to ctx.filesDir)) {
            sb.append(label).append(": ").append(f.path).append(" free=").append(freeMb(f)).append('\n')
        }
        sb.append("\n--- network ---\n").append(networkText(ctx)).append('\n')
        return sb.toString()
    }

    private fun freeMb(f: File): String = try {
        val st = android.os.StatFs(f.path)
        "${st.availableBytes / MB} MB of ${st.totalBytes / MB} MB"
    } catch (t: Throwable) {
        "?"
    }

    private fun cpuSummary(): String {
        val text = readSmall("/proc/cpuinfo", 64 * 1024) ?: return "cpuinfo: unreadable"
        val processors = text.lineSequence().count { it.startsWith("processor") }
        fun values(key: String) = text.lineSequence().filter { it.startsWith(key) }.map { it.substringAfter(':').trim() }.toSet().take(6)
        return "cpuinfo: processors=$processors hardware=${values("Hardware")} cpu part=${values("CPU part")} implementer=${values("CPU implementer")} " +
            "features=${text.lineSequence().firstOrNull { it.startsWith("Features") }?.substringAfter(':')?.trim()?.take(200)}"
    }

    private fun networkText(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "no ConnectivityManager"
            val net = cm.activeNetwork ?: return "no active network (offline)"
            val caps = cm.getNetworkCapabilities(net) ?: return "active network without capabilities"
            val transports = listOf(
                NetworkCapabilities.TRANSPORT_WIFI to "wifi", NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
                NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet", NetworkCapabilities.TRANSPORT_VPN to "vpn",
                NetworkCapabilities.TRANSPORT_BLUETOOTH to "bluetooth",
            ).filter { caps.hasTransport(it.first) }.joinToString(",") { it.second }
            fun cap(c: Int, name: String) = name + "=" + caps.hasCapability(c)
            val lp = cm.getLinkProperties(net)
            "connected: true transports=$transports ${cap(NetworkCapabilities.NET_CAPABILITY_INTERNET, "internet")} " +
                "${cap(NetworkCapabilities.NET_CAPABILITY_VALIDATED, "validated")} ${cap(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, "notMetered")} " +
                "${cap(NetworkCapabilities.NET_CAPABILITY_NOT_VPN, "notVpn")} ${cap(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL, "captivePortal")}\n" +
                "bandwidth: down=${caps.linkDownstreamBandwidthKbps} kbps up=${caps.linkUpstreamBandwidthKbps} kbps\n" +
                "dns servers: ${lp?.dnsServers?.joinToString { it.hostAddress ?: "?" }}" +
                (if (Build.VERSION.SDK_INT >= 28) " privateDns=${lp?.isPrivateDnsActive}" else "")
        } catch (t: Throwable) {
            "network state unreadable: $t"
        }
    }

    // ---- Processes ------------------------------------------------------------------------------

    private fun readSmall(path: String, max: Int): String? =
        try { FileInputStream(path).use { UbisoftDiagScan.readUpTo(it, max) } } catch (e: Exception) { null }

    private class Proc(val stat: UbisoftDiagScan.ProcStat, val cmd: String)

    /** The processes `/proc` shows the app (its own user's, on current Android). */
    private fun scanProcs(): List<Proc> {
        val names = File("/proc").list() ?: return emptyList()
        val out = ArrayList<Proc>()
        for (n in names) {
            if (n.isEmpty() || !n[0].isDigit()) continue
            val pid = n.toIntOrNull() ?: continue
            val st = readSmall("/proc/$pid/stat", 1024)?.let { UbisoftDiagScan.parseProcStat(it.trim()) } ?: continue
            val cmd = readSmall("/proc/$pid/cmdline", 512)?.replace('\u0000', ' ')?.trim() ?: ""
            out += Proc(st, cmd)
        }
        return out.sortedBy { it.stat.pid }
    }

    private fun processText(): String {
        val procs = scanProcs()
        return "processes visible to the app: ${procs.size} (Android only shows an app its own user's processes)\n" +
            procs.joinToString("\n") { UbisoftDiagScan.formatProc(it.stat, it.cmd) }
    }

    // ---- Sampler --------------------------------------------------------------------------------

    private fun focusOf(s: Session): Regex {
        val exe = s.launch.shortcut?.path?.substringAfterLast('\\')?.substringAfterLast('/')?.substringBeforeLast('.')?.lowercase().orEmpty()
        val words = mutableListOf("ubisoft", "uplay", "upc", "wineserver")
        if (exe.length >= 3) words += Regex.escape(exe)
        return Regex(words.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    private fun startSampler(s: Session) {
        val t = Thread({ samplerLoop(s) }, "ubisoft-diag-sampler")
        t.isDaemon = true
        t.priority = Thread.MIN_PRIORITY
        s.sampler = t
        s.status["Sampler"] = "running"
        t.start()
    }

    private fun samplerLoop(s: Session) {
        val rel = "sampler.txt"
        val f = File(s.dir, rel)
        var written = 0L
        var note = ""
        try {
            f.parentFile?.mkdirs()
            val focus = focusOf(s)
            BufferedWriter(OutputStreamWriter(FileOutputStream(f), Charsets.UTF_8)).use { w ->
                val head = "# every 5 s for the first 5 minutes, then every 30 s. t = seconds since the launch started.\n" +
                    "# focus processes (ubisoft/uplay/upc/wineserver/the game exe) also get status, wchan and a thread-state count.\n"
                w.write(head)
                written += head.length
                val t0 = SystemClock.elapsedRealtime()
                var n = 0
                var lastKey = ""
                while (!s.samplerStop) {
                    if (written >= Bucket.SAMPLER.limit || s.room(Bucket.SAMPLER) <= 0L) { note = "stopped: sampler size limit"; break }
                    val elapsed = SystemClock.elapsedRealtime() - t0
                    val text = sampleText(elapsed, n, focus) { key -> val same = key == lastKey; lastKey = key; same }
                    w.write(text)
                    w.flush()
                    written += text.length
                    n++
                    s.samplerSamples = n
                    val period = if (elapsed < 300_000L) 5_000L else 30_000L
                    var slept = 0L
                    while (slept < period && !s.samplerStop) { Thread.sleep(250); slept += 250 }
                }
                if (note.isEmpty()) note = "stopped with the session"
                w.write("# $note after ${s.samplerSamples} samples\n")
            }
        } catch (e: InterruptedException) {
            note = if (s.samplerStop) "stopped with the session" else "interrupted"
        } catch (t: Throwable) {
            note = "failed: $t"
        } finally {
            s.charge(Bucket.SAMPLER, written)
            s.register(rel, "process sampler: process table, thread counts, status/wchan/stat of the game and Ubisoft processes", f.length(), false)
            s.status["Sampler"] = "$note (${s.samplerSamples} samples)"
        }
    }

    /** One sample. [sameAsLast] gets the process-set key and says whether the table is unchanged since the last sample. */
    private fun sampleText(elapsedMs: Long, n: Int, focus: Regex, sameAsLast: (String) -> Boolean): String {
        val procs = scanProcs()
        val sb = StringBuilder()
        sb.append("=== t+").append(elapsedMs / 1000).append("s  ").append(stamp(System.currentTimeMillis())).append("  processes=").append(procs.size).append('\n')
        val key = procs.joinToString(",") { "${it.stat.pid}:${it.stat.state}:${it.stat.threads}" }
        val unchanged = sameAsLast(key)
        if (unchanged && n % 6 != 0) {
            sb.append("  process table unchanged (pids, states, thread counts)\n")
        } else {
            for (p in procs) sb.append("  ").append(UbisoftDiagScan.formatProc(p.stat, if (focus.containsMatchIn(p.cmd + " " + p.stat.comm)) p.cmd else "")).append('\n')
        }
        for (p in procs) {
            if (!focus.containsMatchIn(p.cmd + " " + p.stat.comm)) continue
            val pid = p.stat.pid
            sb.append("  [").append(pid).append(' ').append(p.stat.comm).append("] stat: ").append(readSmall("/proc/$pid/stat", 1024)?.trim()).append('\n')
            sb.append("     status: ").append(readSmall("/proc/$pid/status", 4096)?.let { UbisoftDiagScan.statusSummary(it) }).append('\n')
            sb.append("     wchan: ").append(readSmall("/proc/$pid/wchan", 128)?.trim()).append("   threads by state: ").append(threadStates(pid)).append('\n')
        }
        return sb.toString()
    }

    /** `R=1 S=20 D=2 (D: tid:wchan ...)` for a process's threads, at most 300 read. */
    private fun threadStates(pid: Int): String {
        val tids = File("/proc/$pid/task").list() ?: return "unreadable"
        val counts = java.util.TreeMap<Char, Int>()
        val blocked = ArrayList<String>()
        for (tid in tids.take(300)) {
            val st = readSmall("/proc/$pid/task/$tid/stat", 512)?.let { UbisoftDiagScan.parseProcStat(it.trim()) } ?: continue
            counts[st.state] = (counts[st.state] ?: 0) + 1
            if (st.state == 'D' && blocked.size < 6) blocked += tid + ":" + (readSmall("/proc/$pid/task/$tid/wchan", 128)?.trim() ?: "?")
        }
        return counts.entries.joinToString(" ") { "${it.key}=${it.value}" } + (if (tids.size > 300) " (first 300 of ${tids.size})" else "") +
            (if (blocked.isNotEmpty()) " (D: ${blocked.joinToString(" ")})" else "")
    }

    // ---- POST -----------------------------------------------------------------------------------

    private fun post(s: Session, why: String) {
        val t0 = SystemClock.elapsedRealtime()
        s.status["POST"] = "running (started because $why)"
        s.flushIndex()
        try { s.sampler?.join(3000) } catch (_: InterruptedException) {}
        // The PRE listings must be complete before they are compared with the POST ones.
        try { s.preThread?.join(20_000) } catch (_: InterruptedException) {}
        if (s.preThread?.isAlive == true) s.addMain("POST", "NOTE", "the PRE background work was still running after 20 s; the before/after comparisons below may be incomplete")
        val deadline = { s.postDeadline }
        step(s, "POST", "PROCESSES") { s.writeSide("processes_post.txt", "processes visible to the app at the end of the session (a survivor here means Wine did not stop)", processText(), Bucket.MISC) }
        step(s, "POST", "LOGCAT") { logcat(s) }
        step(s, "POST", "EXIT INFO") { exitInfo(s) }
        step(s, "POST", "REGISTRY SNAPSHOT") { s.addMain("POST", "REGISTRY SNAPSHOT (after the session)", registryText(s)); s.flushIndex() }
        step(s, "POST", "REGISTRY COPIES") { registryCopies(s) }
        step(s, "POST", "FILES (Ubisoft areas)") { filesAreas(s, "post", deadline()) }
        step(s, "POST", "TEXT FILES + COPIES") { textFilesAndCopies(s, deadline()) }
        step(s, "POST", "FILES (game folder top 3 levels)") { filesGame(s, "post", deadline()) }
        step(s, "POST", "FILES (drive_c)") { filesDriveC(s, "post", deadline()) }
        step(s, "POST", "SYSTEM") { s.writeSide("system_post.txt", "system, memory, storage, network, clock after the session", systemText(s), Bucket.MISC) }
        step(s, "POST", "SAMPLER") {
            s.addMain("POST", "SAMPLER", "${s.samplerSamples} samples, status: ${s.status["Sampler"]} (sampler.txt)")
        }
        s.status["POST"] = (if (s.abort) "CUT by the time limit after " else "done in ") + "${SystemClock.elapsedRealtime() - t0} ms (started because $why)"
        s.flushIndex()
        Log.i(TAG, "report finished: ${s.outFile.path}")
    }

    /**
     * The Ubisoft / Uplay sections of one hive as a side file (secret-named values replaced): the rest of the hive is not
     * copied on purpose, it holds the user name and whatever else the games and installers wrote.
     */
    private fun copyRegistrySections(src: File, dstRel: String, s: Session): String {
        if (!src.isFile) return "absent"
        val room = s.room(Bucket.REGISTRY)
        if (room <= 0L) return "skipped: size budget used up"
        val r = BufferedReader(InputStreamReader(FileInputStream(src), Charsets.ISO_8859_1)).use { UbisoftDiagScan.registrySections(it, minOf(room, Int.MAX_VALUE.toLong()).toInt()) }
        val dst = File(s.dir, dstRel)
        dst.parentFile?.mkdirs()
        val head = "# ${src.name}: only the Ubisoft / Uplay sections (${r.keys.size}) of the prefix's hive; secret-named values replaced; the rest of the hive is deliberately not copied\n"
        dst.writeText(head + r.text + (if (r.truncated) "\n[truncated: size limit]\n" else "\n"), Charsets.ISO_8859_1)
        s.charge(Bucket.REGISTRY, dst.length())
        s.register(dstRel, "Ubisoft / Uplay sections of the prefix's ${src.name} after the session", dst.length(), r.truncated)
        return "${r.keys.size} section(s), ${dst.length()} bytes" + (if (r.truncated) " (TRUNCATED)" else "")
    }

    private fun registryCopies(s: Session) {
        val sb = StringBuilder()
        for (name in listOf("system.reg", "user.reg")) {
            val f = File(s.prefix, name)
            sb.append(name).append(": ").append(if (f.isFile) "${f.length()} bytes, modified ${stamp(f.lastModified())} -> " else "").append(
                copyRegistrySections(f, "copies/registry/$name", s),
            ).append('\n')
        }
        s.addMain("POST", "REGISTRY COPIES", sb.toString().trimEnd())
        s.flushIndex()
    }

    private class Candidate(val rel: String, val file: File, val size: Long, val mtime: Long, val action: UbisoftDiagScan.CopyAction)

    /** [rel] below `drive_c` when it is a real folder (not a link) that still resolves below it; null otherwise, so a planted link cannot lead a walk or a copy out of the prefix. */
    private fun realDirUnderDriveC(s: Session, rel: String): File? {
        val f = File(s.driveC, rel)
        return try {
            if (f.isDirectory && !Files.isSymbolicLink(f.toPath()) && f.canonicalFile.toPath().startsWith(s.driveC.canonicalFile.toPath())) f else null
        } catch (e: java.io.IOException) {
            null
        }
    }

    /** Connect's logs and settings as readable text in the report, and redacted copies of its small text files in the side folder. */
    private fun textFilesAndCopies(s: Session, deadline: Long) {
        val roots = listOf(
            "Program Files (x86)/Ubisoft/Ubisoft Game Launcher",
            "users/${ImageFs.USER}/AppData/Local/Ubisoft Game Launcher",
            "ProgramData/Ubisoft",
        )
        val cands = ArrayList<Candidate>()
        val notes = ArrayList<String>()
        for (rel in roots) {
            val root = realDirUnderDriveC(s, rel)
            if (root == null) { notes += "$rel: absent (or a link, not followed)"; continue }
            val r = UbisoftDiagScan.walk(root, 14, 60_000, deadline) { p, e ->
                if (e.kind == 'f') {
                    val full = "$rel/$p"
                    cands += Candidate(full, File(root, p), e.size, e.mtime, UbisoftDiagScan.copyAction(full, e.size))
                }
            }
            notes += "$rel: " + UbisoftDiagScan.describeWalk(r)
        }

        // 1. Readable text in the report: settings.yaml first, then the newest *.log / *.txt (a tail of each, a budget in all).
        // A file whose name looks like a credential or identity store is not shown either, only hashed in the copies index.
        val texts = cands.filter { c ->
            val n = c.file.name.lowercase()
            c.action != UbisoftDiagScan.CopyAction.HASH_SENSITIVE && (n == "settings.yaml" || n.endsWith(".log") || n.endsWith(".txt"))
        }.sortedWith(compareBy({ if (it.file.name.equals("settings.yaml", true)) 0 else 1 }, { -it.mtime }))
        val sbText = StringBuilder()
        var budget = TEXT_TOTAL_CHARS
        var shown = 0
        for (c in texts) {
            if (budget <= 0 || shown >= TEXT_FILES_SHOWN) break
            val tail = UbisoftDiagScan.readTail(c.file, minOf(TEXT_FILE_TAIL, budget)) ?: continue
            sbText.append("\n--- ").append(c.rel).append("  size=").append(tail.size).append(" modified=").append(stamp(c.mtime))
                .append(if (tail.cut) "  [truncated: last ${tail.text.length} chars shown]" else "").append(" ---\n")
            if (tail.binary) {
                sbText.append("(binary content, not shown)\n")
            } else {
                sbText.append(UbisoftDiagScan.redactFile(c.file.name, tail.text)).append('\n')
                budget -= tail.text.length
            }
            shown++
        }
        val logs = texts.count { it.file.name.lowercase() != "settings.yaml" }
        sbText.insert(0, if (logs == 0) "no log files found under: ${roots.joinToString(", ")}\n" else "$logs log/text file(s) found; newest shown below\n")
        sbText.insert(0, "searched: ${notes.joinToString(" | ")}\n")
        s.addMain("POST", "TEXT FILES (Ubisoft Connect logs and settings)", sbText.toString().trimEnd())
        s.flushIndex()

        // 2. Copies: only allow-listed text files, redacted, smallest first; every other file is listed with its hash only.
        val index = StringBuilder("action        size        modified             path  [sha256 when not copied]\n")
        var hashBudget = 400L * MB
        val copyOrder = cands.sortedWith(compareBy({ if (it.action == UbisoftDiagScan.CopyAction.TEXT_REDACTED) 0 else 1 }, { it.size }))
        var copied = 0
        for (c in copyOrder) {
            if (s.abort || System.currentTimeMillis() > deadline) { index.append("-- stopped: time limit --\n"); break }
            var result: String
            if (c.action == UbisoftDiagScan.CopyAction.TEXT_REDACTED) {
                val room = s.room(Bucket.COPIES)
                if (room < c.size + 64) {
                    result = "SKIPPED(budget)"
                } else {
                    try {
                        val cap = if (c.file.name.equals("settings.yaml", true)) UbisoftDiagScan.YAML_READ_BYTES else UbisoftDiagScan.MAX_COPY_BYTES.toInt()
                        val raw = c.file.inputStream().use { UbisoftDiagScan.readUpTo(it, cap) }
                        // A file with a text name but binary content is not copied.
                        if (UbisoftDiagScan.looksBinary(raw.toByteArray(Charsets.UTF_8))) {
                            result = "HASH-ONLY(binary content)"
                        } else {
                            val text = UbisoftDiagScan.redactFile(c.file.name, raw)
                            val dst = File(s.dir, "copies/prefix/drive_c/${c.rel}")
                            dst.parentFile?.mkdirs()
                            dst.writeText(text, Charsets.UTF_8)
                            s.charge(Bucket.COPIES, dst.length())
                            copied++
                            result = "COPIED(redacted)"
                        }
                    } catch (t: Throwable) {
                        result = "COPY-FAILED(${t.javaClass.simpleName})"
                    }
                }
            } else {
                result = when (c.action) {
                    UbisoftDiagScan.CopyAction.HASH_SENSITIVE -> "HASH-ONLY(name looks like a credential or identity store)"
                    UbisoftDiagScan.CopyAction.HASH_LARGE -> "HASH-ONLY(2 MB or more)"
                    else -> "HASH-ONLY(not on the text allow-list)"
                }
            }
            if (result.startsWith("HASH-ONLY")) {
                if (c.size <= hashBudget) {
                    hashBudget -= c.size
                    result += "  sha256=" + UbisoftDiagScan.sha256(c.file, c.size + 1)
                } else {
                    result += "  sha256=(skipped: hash budget used up)"
                }
            }
            index.append(result.substringBefore("  sha256=").padEnd(14)).append(' ').append(c.size.toString().padStart(10)).append("  ")
                .append(UbisoftDiagScan.fmtTime(c.mtime)).append("  ").append(c.rel)
            if (result.contains("  sha256=")) index.append("  sha256=").append(result.substringAfter("  sha256="))
            index.append('\n')
        }
        s.writeSide("copies_index.txt", "what happened to every file under Connect's folders (copied / hash only)", index.toString(), Bucket.LISTINGS)
        s.addMain("POST", "COPIES", "$copied of ${cands.size} files copied into ubisoft_diag_files/copies/prefix/drive_c/ (index in copies_index.txt)")
        step(s, "POST", "SHORTCUT AND GAME STATE COPIES") { simpleCopies(s) }
        s.flushIndex()
    }

    private fun simpleCopies(s: Session) {
        val sb = StringBuilder()
        val sc = s.launch.shortcut
        if (sc != null) {
            val text = try { UbisoftDiagScan.redactText(sc.file.inputStream().use { UbisoftDiagScan.readUpTo(it, SHORTCUT_BYTES) }) } catch (t: Throwable) { null }
            if (text != null) { s.writeSide("copies/shortcut/${sc.file.name}", "the shortcut .desktop (secret values replaced)", text, Bucket.COPIES); sb.append("shortcut copied\n") }
        }
        val game = s.install.first
        if (game != null) {
            // The two install-record files are copied as they are: the game's own, with product ids and paths, no account data.
            game.listFiles()?.filter { UbisoftDiagScan.entryOf(it)?.kind == 'f' && (it.name.equals(UbisoftInstallRegistry.STATE_FILE, true) || it.name.equals("uplay_install.manifest", true)) }?.forEach { f ->
                val room = s.room(Bucket.COPIES)
                if (f.length() < room) {
                    f.copyTo(File(s.dir, "copies/game/${f.name}").also { it.parentFile?.mkdirs() }, overwrite = true)
                    s.charge(Bucket.COPIES, f.length())
                    s.register("copies/game/${f.name}", "the game's ${f.name} (raw)", f.length(), false)
                    sb.append(f.name).append(" copied\n")
                } else {
                    sb.append(f.name).append(" skipped: size budget\n")
                }
            }
        }
        s.addMain("POST", "SHORTCUT AND GAME STATE COPIES", sb.toString().trimEnd().ifEmpty { "nothing to copy" })
    }

    // ---- Logcat and exit info -------------------------------------------------------------------

    private fun logcat(s: Session) {
        val pid = android.os.Process.myPid()
        val proc = ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid").redirectErrorStream(true).start()
        val watchdog = daemon("ubisoft-diag-logcat-watchdog") {
            try { Thread.sleep(20_000); proc.destroy() } catch (_: InterruptedException) {}
        }
        val fullRel = "logcat_full.txt"
        val fullFile = File(s.dir, fullRel)
        fullFile.parentFile?.mkdirs()
        val ring = ArrayDeque<String>()
        var lines = 0
        var fullBytes = 0L
        var fullCut = false
        BufferedWriter(OutputStreamWriter(FileOutputStream(fullFile), Charsets.UTF_8)).use { w ->
            BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).use { r ->
                while (true) {
                    val raw = r.readLine() ?: break
                    lines++
                    val line = UbisoftDiagScan.redactLine(raw)
                    if (!fullCut) {
                        if (fullBytes + line.length + 1 > s.room(Bucket.LOGCAT)) { fullCut = true; w.write("[truncated: size limit; the end of the buffer is only in logcat_filtered.txt]\n") }
                        else { w.write(line); w.write("\n"); fullBytes += line.length + 1 }
                    }
                    if (UbisoftDiagScan.logcatLineWanted(line, LOG_TAGS)) {
                        ring.addLast(line)
                        if (ring.size > 1500) ring.removeFirst()
                    }
                }
            }
        }
        watchdog.interrupt()
        proc.destroy()
        s.charge(Bucket.LOGCAT, fullFile.length())
        s.register(fullRel, "the app's own complete logcat ($lines lines read, logcat -d -v time --pid=$pid)", fullFile.length(), fullCut)
        val filtered = ring.joinToString("\n")
        s.writeSide("logcat_filtered.txt", "logcat lines of this feature set's tags (${LOG_TAGS.joinToString(",")}) and any mention of Ubisoft/Uplay/upc, last 1500", filtered, Bucket.LOGCAT_FILTERED)
        s.addMain("POST", "APP LOGCAT", if (lines == 0) "logcat returned no lines for pid $pid (denied, or the buffer was empty)" else "$lines lines read; ${ring.size} matched the filter (logcat_filtered.txt); everything in logcat_full.txt")
        s.flushIndex()
    }

    private fun exitInfo(s: Session) {
        val sb = StringBuilder()
        sb.append("--- ApplicationExitInfo (why earlier processes of this app ended: native crash, low-memory kill, ...) ---\n")
        sb.append(if (ExitReasonReporter.isSupported()) ExitReasonReporter.capture(s.launch.context) else "needs Android 11 or newer").append('\n')
        sb.append("\n--- dumpsys meminfo ${android.os.Process.myPid()} (may be refused for an app) ---\n")
        sb.append(runCommand(listOf("dumpsys", "meminfo", android.os.Process.myPid().toString()), 8_000L, 64 * 1024)).append('\n')
        s.writeSide("exit_info_post.txt", "ApplicationExitInfo records and dumpsys meminfo of the app", UbisoftDiagScan.redactText(sb.toString()), Bucket.MISC)
    }

    private fun runCommand(cmd: List<String>, timeoutMs: Long, maxBytes: Int): String {
        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val wd = daemon("ubisoft-diag-cmd-watchdog") { try { Thread.sleep(timeoutMs); p.destroy() } catch (_: InterruptedException) {} }
            val text = p.inputStream.use { UbisoftDiagScan.readUpTo(it, maxBytes) }
            wd.interrupt()
            p.destroy()
            text.ifBlank { "(no output)" }
        } catch (t: Throwable) {
            "could not run ${cmd.joinToString(" ")}: $t"
        }
    }
}
