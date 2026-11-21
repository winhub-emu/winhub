package com.winhub.hernandez.store

import android.content.Context
import android.util.Log
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.store.steamscript.InstallScriptExecutor
import com.winhub.hernandez.store.steamscript.InstallScriptModel
import com.winhub.hernandez.store.steamscript.InstallScriptTokens
import com.winhub.hernandez.xenvironment.ImageFs
import java.io.File

/**
 * Ubisoft-published Steam titles ("Ubisoft support", e.g. Assassin's Creed IV Black Flag): detection, the
 * one-time Ubisoft Connect install into the prefix, and the overlay setting that keeps those games alive.
 *
 * These games start Ubisoft Connect themselves (`uplay_r1_loader.dll` reads `HKLM\Software\Wow6432Node\Ubisoft\Launcher`
 * and runs the launcher found there) and stop with "Unable to find Ubisoft Game Launcher" when it is not in the
 * prefix. Steam installs it from the depot's install script (`<appId>_install.vdf` or `installScript.vdf`, a "Run
 * Process" step running the bundled `UbisoftConnectInstaller.exe /S`); the same step is run here for any Ubisoft title.
 *
 * Epic Games Store builds of Ubisoft titles (Assassin's Creed Syndicate) need the same launcher but have no Steam
 * script: their folder carries the loader plus `Uplay\UplayInstaller.exe`, and the shortcut's `ubisoft` stamp
 * says so (see the Epic section below). When such a folder bundles no installer at all, Ubisoft's own is downloaded
 * once, after the user agrees ([UbisoftInstallerDownload]).
 *
 * Cross-checked with DroidDeck (Droid-Deck/DroidDeck #1449 / #1463) and GameNative (#2286): both install the bundled
 * launcher through the Steam install script, and DroidDeck turns the launcher's overlay off because it hooks
 * `dxgi.dll` and crashes the game on an Arm64EC DXVK.
 */
object UbisoftSupport {
    private const val TAG = "UbisoftSupport"

    /** Where Ubisoft Connect installs itself inside a prefix (relative to the container root). */
    private const val LAUNCHER_DIR = ".wine/drive_c/Program Files (x86)/Ubisoft/Ubisoft Game Launcher"
    private val LAUNCHER_EXES = listOf("upc.exe", "UbisoftConnect.exe", "UbisoftGameLauncher.exe")

    /** The launcher's own settings file (relative to the container root). */
    private val SETTINGS_FILE = ".wine/drive_c/users/${ImageFs.USER}/AppData/Local/Ubisoft Game Launcher/settings.yaml"

    /** A real `settings.yaml` is a few KB; a larger one is not read or rewritten. */
    private const val MAX_SETTINGS_BYTES = 1024L * 1024

    /** An install script is a few KB; a larger file is not read. */
    private const val MAX_SCRIPT_BYTES = 512L * 1024

    /**
     * Files a Ubisoft game links against to find the launcher (root of the game folder), plus the install record
     * (`uplay_install.manifest` / `.state`) Ubisoft Connect leaves beside a game it installed, which Epic builds ship too.
     */
    private val LOADER_PREFIXES = listOf(
        "uplay_r1_loader", "upc_r1_loader", "uplay_r2_loader", "upc_r2_loader", "uplay_install",
    )

    private const val SETUP_NAME = "Ubisoft Connect PC Client"

    /**
     * Agent launcher-chain exe names (`WN_STEAM_LAUNCH_CHAIN`, SteamLite v6 / agent p5). Steam starts the game
     * as `<game>.exe -uplay_steam_mode`; that stub starts `UbisoftGameLauncher.exe`, exits about 11 seconds later,
     * and Ubisoft Connect (`upc.exe`) then starts the real game. Without the chain the agent reads the stub's
     * exit as "game exited" and ends the session before `upc.exe` ever starts (device log, AC4 Black Flag).
     * Only the two hand-off exes: `UplayService`/`UplayWebCore` stay resident and would hold the session.
     */
    const val CHAIN_VALUE = "UbisoftGameLauncher.exe;upc.exe"

    /**
     * True when a SteamLite launch plan's agent env ([env], `RealSteamLauncher.Plan.env`) carries the Ubisoft chain, i.e.
     * [RealSteamLauncher] armed it for this launch. A map lookup: the decision was already made when the plan was built.
     */
    @JvmStatic
    fun steamChainArmed(env: Map<String, String>?): Boolean = env?.get(EaSupport.CHAIN_ENV) == CHAIN_VALUE

    // ---- Detection ------------------------------------------------------------------------------

    /**
     * How well [fileName] looks like a Ubisoft launcher installer, lower is better, null when it is not one. One rule
     * for every question that asks (the depot search, the Steam script's Run Process steps), so they cannot disagree:
     * an `.exe` whose name, lower-cased with spaces, `_`, `-` and `.` taken out, contains `installer` and names the
     * launcher: `UbisoftConnectInstaller` (0), another `ubisoft...installer` (1), `uplay...installer` or
     * `upcinstaller...` (2). `setup` is not accepted: `DXSETUP.exe` style names are too common to run silently on
     * a guess, and `upc` alone would match `upcoming`. Uninstallers are not installers.
     * Matches `Ubisoft Connect Installer.exe`, `UbisoftGameLauncherInstaller.exe`, `Uplay_Installer.exe`,
     * `UplayInstaller_x64.exe`; not `UplayLaunch.exe`, `UplayService.exe`, `upc.exe`, `UbisoftGameLauncher.exe`, a DLL.
     */
    internal fun installerRank(fileName: String): Int? {
        val name = fileName.trim()
        if (!name.endsWith(".exe", ignoreCase = true)) return null
        val stem = name.dropLast(4).lowercase().filter { it != ' ' && it != '_' && it != '-' && it != '.' }
        if (!stem.contains("installer") || stem.contains("uninstall")) return null
        return when {
            stem.startsWith("ubisoftconnectinstaller") -> 0
            stem.contains("ubisoft") -> 1
            stem.contains("uplay") || stem.startsWith("upcinstaller") -> 2
            else -> null
        }
    }

    /** True when [fileName] (no folders) is a Ubisoft launcher installer, see [installerRank]. */
    internal fun isInstallerName(fileName: String): Boolean = installerRank(fileName) != null

    private fun processFileName(processPath: String): String =
        processPath.trim().trim('"').trim().substringAfterLast('\\').substringAfterLast('/')

    /** True for a "Run Process" exe that is Ubisoft's launcher installer (judged by the file name, not the folders). */
    fun isInstaller(processPath: String): Boolean = isInstallerName(processFileName(processPath))

    /** [processes] with Ubisoft's launcher installer first, best name first (order otherwise kept), so it is the step that gets run. */
    fun installerFirst(processes: List<InstallScriptModel.RunProcess>): List<InstallScriptModel.RunProcess> =
        processes.sortedBy { installerRank(processFileName(it.process)) ?: Int.MAX_VALUE }

    /**
     * The "Run Process" steps to run for a depot whose script is [scriptSteps]: the script's own steps when one of them
     * is a Ubisoft launcher installer, otherwise those plus the installer the depot bundles (none for a depot without
     * one, so an EA depot is unaffected). Pure given the depot's files.
     */
    fun withBundledInstaller(scriptSteps: List<InstallScriptModel.RunProcess>, installDir: File): List<InstallScriptModel.RunProcess> =
        if (scriptSteps.any { isInstaller(it.process) }) scriptSteps else scriptSteps + fallbackRunProcesses(installDir)

    /**
     * What a depot's files say about the launcher, found once and reused for a short while: one launch asks about the
     * same depot from several places (the Games tab, the install-script pass, the agent hand-off), mostly for games
     * that are not Ubisoft's. Each part is looked up on first use only.
     *
     * The installer search goes [DEEP_LEVELS] folders down only for a depot that already looks like Ubisoft's (a loader
     * or install record in its root, or an install script that names Ubisoft). Any other depot gets its root and one
     * folder below, so a game that has nothing to do with Ubisoft costs a few directory listings and nothing more.
     */
    private class DepotScan(val installDir: File, val atMs: Long) {
        val rootNames: Array<String> by lazy { installDir.list() ?: emptyArray<String>() }
        val hasLoader: Boolean by lazy { UbisoftSupport.rootHasLoader(installDir, rootNames) }
        val scriptMentionsUbisoft: Boolean by lazy {
            UbisoftSupport.scriptText(installDir, rootNames)?.let { UbisoftSupport.mentionsUbisoft(it) } == true
        }
        val installer: File? by lazy {
            UbisoftSupport.searchInstaller(
                installDir, if (hasLoader || scriptMentionsUbisoft) UbisoftSupport.DEEP_LEVELS else UbisoftSupport.SHALLOW_LEVELS, rootNames,
            )
        }
    }

    private const val SCAN_TTL_MS = 30_000L
    private const val SCAN_CACHE_MAX = 16
    private val scans = java.util.concurrent.ConcurrentHashMap<String, DepotScan>()

    private fun scanOf(installDir: File): DepotScan {
        val key = installDir.absolutePath
        val now = System.currentTimeMillis()
        scans[key]?.let { if (now - it.atMs in 0..SCAN_TTL_MS) return it }
        if (scans.size >= SCAN_CACHE_MAX) scans.clear()
        return DepotScan(installDir, now).also { scans[key] = it }
    }

    /** Drops every remembered depot scan (the files changed on disk, or a test reuses a folder). */
    internal fun forgetScans() = scans.clear()

    /** Folder levels below the root the installer search may go for a depot that looks like Ubisoft's. */
    internal const val DEEP_LEVELS = 3

    /** Folder levels below the root searched for any other depot (a layout like `Uplay\UplayInstaller.exe`). */
    internal const val SHALLOW_LEVELS = 1

    /** Safety net: folders listed by one installer search, whatever the depot looks like. */
    private const val SEARCH_MAX_DIRS = 300

    /**
     * True when a file in [root] has a launcher-loader name. The names come from one listing ([names]) and are checked
     * before anything is stat'ed, so a game without such a name costs no file stat at all.
     */
    internal fun rootHasLoader(root: File, names: Array<String>): Boolean =
        names.any { n -> LOADER_PREFIXES.any { n.startsWith(it, ignoreCase = true) } && File(root, n).isFile }

    /** True when [text] (an install script) names Ubisoft or Uplay. */
    private fun mentionsUbisoft(text: String): Boolean =
        text.contains("Ubisoft", ignoreCase = true) || text.contains("Uplay", ignoreCase = true)

    /**
     * The depot's install script text, null when there is none, it is bigger than [MAX_SCRIPT_BYTES] or it cannot be
     * read. Found by file name only ([InstallScriptExecutor.locateScriptByName]), so asking never walks the depot.
     */
    private fun scriptText(installDir: File, rootNames: Array<String>? = null): String? {
        val script = InstallScriptExecutor.locateScriptByName(installDir, rootNames) ?: return null
        if (script.length() > MAX_SCRIPT_BYTES) return null
        return try { script.readText() } catch (e: Exception) { null }
    }

    /**
     * The launcher installer found by walking folders from [root] level by level, down to [maxDepth] levels below it, so
     * the shallowest hit wins. On one level the best [installerRank] wins, then the lower-cased path (the listing order
     * of the file system never matters). Only entries whose name looks like an installer are stat'ed as files, and the
     * walk stops after [maxDirs] folders. [rootNames] = the root's listing when the caller already has it.
     */
    internal fun searchInstaller(root: File, maxDepth: Int, rootNames: Array<String>? = null, maxDirs: Int = SEARCH_MAX_DIRS): File? {
        var level = listOf(root)
        var listed = 0
        for (depth in 0..maxDepth) {
            var best: File? = null
            var bestRank = Int.MAX_VALUE
            val next = ArrayList<File>()
            for (dir in level) {
                if (listed >= maxDirs) return best
                listed++
                val names = (if (dir === root) rootNames else null) ?: dir.list() ?: continue
                for (name in names) {
                    val rank = installerRank(name)
                    val f = File(dir, name)
                    if (rank != null && f.isFile) {
                        if (best == null || rank < bestRank || (rank == bestRank && sortKey(f) < sortKey(best))) { best = f; bestRank = rank }
                    } else if (depth < maxDepth && f.isDirectory) next += f
                }
            }
            if (best != null) return best
            level = next
        }
        return null
    }

    /** Orders equal-rank installers by path, folder by folder (a separator sorts before any name character), case aside. */
    private fun sortKey(f: File): String = f.path.replace('/', '\u0000').lowercase() + "\u0001" + f.path

    /**
     * The launcher installer a depot bundles. Looked for in its root and one folder below it (`Uplay\`), and down to
     * three folders (`Support\Uplay\`) when the depot looks like Ubisoft's (see [DepotScan]). Null when the depot ships
     * none. The answer is reused for 30 seconds.
     */
    fun bundledInstaller(installDir: File?): File? {
        if (installDir == null || !installDir.isDirectory) return null
        return scanOf(installDir).installer?.takeIf { it.isFile }
    }

    /**
     * True when the depot's files alone say Ubisoft: a launcher loader in its root or a bundled launcher installer.
     * File-only on purpose ([InstallScriptExecutor.locateScript] uses it to accept `<appId>_install.vdf`).
     */
    @JvmStatic
    fun hasLauncherFiles(installDir: File?): Boolean {
        if (installDir == null || !installDir.isDirectory) return false
        val scan = scanOf(installDir)
        return scan.hasLoader || scan.installer?.isFile == true
    }

    /** True for a Ubisoft title, detected from the depot's files (and its install script), so no tag is needed. */
    fun detect(installDir: File?): Boolean {
        if (installDir == null || !installDir.isDirectory) return false
        if (hasLauncherFiles(installDir)) return true
        return scanOf(installDir).scriptMentionsUbisoft
    }

    /** The "Run Process" step to run when a depot bundles the installer but its script has none (or there is no script). */
    internal fun fallbackRunProcesses(installDir: File): List<InstallScriptModel.RunProcess> {
        val exe = bundledInstaller(installDir) ?: return emptyList()
        val rel = exe.relativeTo(installDir).path.replace('/', '\\')
        return listOf(InstallScriptModel.RunProcess(SETUP_NAME, "%INSTALLDIR%\\$rel", "/S", null, null, null))
    }

    // ---- The prefix -----------------------------------------------------------------------------

    /** True when Ubisoft Connect is installed in [container]'s prefix. */
    fun launcherInstalled(container: Container): Boolean {
        val dir = File(container.rootDir, LAUNCHER_DIR)
        return dir.isDirectory && LAUNCHER_EXES.any { File(dir, it).isFile }
    }

    /** Which of Connect's launcher exes ([launcherInstalled] wants one) are in [container]'s prefix, name to present. For the diagnostics report. */
    fun launcherFileStatus(container: Container): List<Pair<String, Boolean>> {
        val dir = File(container.rootDir, LAUNCHER_DIR)
        return LAUNCHER_EXES.map { it to File(dir, it).isFile }
    }

    /** `version.txt` in the launcher's folder: one line, the client's build number (relative to the container root). */
    private const val VERSION_FILE = "$LAUNCHER_DIR/version.txt"

    /** Builds are four to six digits; a file longer than this is not the build file. */
    private const val VERSION_FILE_MAX_BYTES = 64L

    /** The largest number taken for a build (they are four to six digits). */
    private const val MAX_BUILD = 999_999

    /**
     * The build number in a `version.txt` [text] (whitespace and a BOM around it ignored), or null when it is not a
     * plain sensible number. Pure. Informational only (the launch notes print it): no decision is made on the build.
     * For orientation: 13393 (v175, 2026) is what Ubisoft's current installer puts in; 7178 (2019) is what the legacy
     * `UplayInstaller.exe` of an Epic Syndicate folder installs, and that client fails to start here.
     */
    internal fun connectBuildFrom(text: String): Int? {
        val n = text.trim().trim('\uFEFF').trim().toIntOrNull() ?: return null
        return if (n in 1..MAX_BUILD) n else null
    }

    /**
     * The Ubisoft Connect build installed in [container]'s prefix, read from the launcher folder's `version.txt`; null
     * when the file is missing, unreadable, oversized or not a number (an unknown build). One tiny file read.
     * Informational only: nothing decides on it.
     */
    fun connectBuild(container: Container): Int? = connectBuildAt(container.rootDir)

    /** [connectBuild] for the container root folder [rootDir] (what a test can call without a container). */
    internal fun connectBuildAt(rootDir: File): Int? {
        val file = File(rootDir, VERSION_FILE)
        if (!file.isFile || file.length() > VERSION_FILE_MAX_BYTES) return null
        return try { connectBuildFrom(file.readText(Charsets.UTF_8)) } catch (e: Exception) { null }
    }

    /** [build] in words for the notes. Pure. */
    fun connectBuildText(build: Int?): String = build?.toString() ?: "unknown (no readable version.txt)"

    /**
     * True when [fileName] is an installer name that ships the current Ubisoft Connect: only `UbisoftConnectInstaller...`
     * ([installerRank] 0). The legacy names (`UplayInstaller.exe`, `Uplay_Installer.exe`, `upcinstaller...`, another
     * `ubisoft...installer`) are what old games bundle, and they install a 2019 client that fails here. Pure.
     */
    internal fun isModernInstallerName(fileName: String?): Boolean = fileName != null && installerRank(fileName) == 0

    /** True for a "Run Process" exe that is the current Ubisoft Connect installer (judged by the file name, see [isModernInstallerName]). */
    fun isModernInstaller(processPath: String): Boolean = isModernInstallerName(processFileName(processPath))

    /** The best of the installer file [names] ([installerRank], the first of equals), null for none. Pure. */
    internal fun bestInstallerName(names: List<String>): String? = names.minByOrNull { installerRank(it) ?: Int.MAX_VALUE }

    /** File names of the install script's "Run Process" steps that run a Ubisoft launcher installer; empty without a script. */
    private fun scriptInstallerNames(installDir: File): List<String> {
        val text = scriptText(installDir) ?: return emptyList()
        return try {
            InstallScriptModel.parse(text).runProcesses.map { processFileName(it.process) }.filter { isInstallerName(it) }
        } catch (e: Exception) { emptyList() }
    }

    /**
     * What the Steam [shortcut] needs before it launches (see [EpicDecision], shared with the Epic gate): nothing, the
     * one-time setup with the installer the depot ships ([EpicSetup.BUNDLED], only when that is the current
     * `UbisoftConnectInstaller...`), or the download of Ubisoft's own ([EpicSetup.DOWNLOAD]: the depot's installer is a
     * legacy one, or the depot has Ubisoft's launcher files and no installer). The prefix check is a few file stats; the
     * depot checks read the game's folders, so they come second and only for a container without Connect. A game that
     * is not Ubisoft's costs the root listing and one folder level below it. Call off the main thread.
     */
    fun steamSetupFor(shortcut: Shortcut): EpicDecision {
        if (launcherInstalled(shortcut.container)) {
            // Safety net: a download kept by a setup session that died before its batch deleted it (a stat when nothing is there).
            UbisoftInstallerDownload.deleteDownloaded(shortcut.container)
            return steamSetupDecision(true, false, null, false)
        }
        val installDir = EaSupport.installDirOf(shortcut) ?: return EpicDecision.NOT_APPLICABLE
        return steamDecisionFor(installDir)
    }

    /** Which setup check a launch has to pass first (see [gateKind]). */
    enum class GateKind {
        /** None: not a Ubisoft candidate (or an Android game, or a stamp that was already looked at again). */
        NONE,

        /** The Epic check ([epicSetupFor]). */
        EPIC,

        /** The Steam check ([steamSetupFor]). */
        STEAM,
    }

    /**
     * Whether a launch is a Ubisoft candidate and which check it gets, from facts that need no disk access. Pure. This is
     * the test the launch flows apply first ([ShortcutLauncher.requestLaunch] and the other launch entry points): an
     * Android game is never one; an Epic-like game ([epicLike]) is, except one stamped "not Ubisoft" ([stamp] false)
     * whose stamp was already looked at again in this app run ([stampRechecked]); any other Steam-origin game
     * ([steamOrigin]) gets the Steam check; everything else (GOG, Amazon, Xbox, custom, Linux...) none.
     */
    fun gateKind(androidEntry: Boolean, epicLike: Boolean, stamp: Boolean?, stampRechecked: Boolean, steamOrigin: Boolean): GateKind = when {
        androidEntry -> GateKind.NONE
        epicLike -> if (stamp == false && stampRechecked) GateKind.NONE else GateKind.EPIC
        steamOrigin -> GateKind.STEAM
        else -> GateKind.NONE
    }

    /** [steamSetupDecision] from the depot [installDir]'s files, for a container without Connect. */
    internal fun steamDecisionFor(installDir: File): EpicDecision {
        if (!detect(installDir)) return EpicDecision.NOT_APPLICABLE
        val name = bestInstallerName(scriptInstallerNames(installDir)) ?: bundledInstaller(installDir)?.name
        return steamSetupDecision(false, true, name, hasLauncherFiles(installDir))
    }

    /** [steamSetupFor] without the shortcut, read-only: [container]'s prefix and the depot [installDir] (the diagnostics' view). */
    fun steamDecisionNow(container: Container, installDir: File?): EpicDecision {
        if (launcherInstalled(container)) return steamSetupDecision(true, false, null, false)
        if (installDir == null || !installDir.isDirectory) return EpicDecision.NOT_APPLICABLE
        return steamDecisionFor(installDir)
    }

    /**
     * Starts the one-time Ubisoft Connect setup session for [shortcut] (the depot's bundled installer, run silently).
     * Returns null when the session was launched, or a message for the user when it could not start. Off the main thread.
     */
    fun startSetup(context: Context, shortcut: Shortcut): String? {
        if (isEpicShortcut(shortcut)) {
            val epicDir = epicInstallDirOf(context, shortcut)
                ?: return context.getString(R.string.ubisoft_setup_no_folder, shortcut.path)
            return startSetupForInstallDir(context, shortcut.container, epicDir)
        }
        val installDir = EaSupport.installDirOf(shortcut)
            ?: return context.getString(R.string.ubisoft_setup_no_folder, shortcut.path)
        // runForShortcut finds the depot from the exe's steam_games/steamapps segment, so prefer a path inside it.
        val resolved = runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path)?.absolutePath }.getOrNull()
        val exe = resolved?.takeIf { InstallScriptExecutor.locateInstallDir(File(it)) != null }
            ?: File(installDir, shortcut.path.replace('\\', '/').substringAfterLast('/')).absolutePath
        val appId = runCatching { EaSupport.resolveSteamAppId(shortcut, installDir) }.getOrDefault(0)
        if (appId <= 0) return context.getString(R.string.ubisoft_setup_no_app_id)
        val started = InstallScriptExecutor.runForShortcut(context, shortcut.container, appId, exe, true)
        return if (started) null else context.getString(R.string.ubisoft_setup_failed)
    }

    /**
     * Store-neutral one-time setup: runs the launcher installer [installDir] bundles (silently) in one setup session
     * for [container]. No Steam app id and no install script needed. Same contract as [startSetup]: null when the
     * session was launched, otherwise a message for the user. Off the main thread (it looks through the folder).
     */
    private fun startSetupForInstallDir(context: Context, container: Container, installDir: File): String? {
        val processes = fallbackRunProcesses(installDir)
        if (processes.isEmpty()) return context.getString(R.string.ubisoft_setup_not_started)
        // A legacy installer (UplayInstaller.exe...) installs a 2019 client that cannot start here: never run one.
        if (!isModernInstaller(processes.first().process)) return context.getString(R.string.ubisoft_setup_failed)
        val tokens = InstallScriptTokens(container, installDir, File(context.filesDir, "imagefs"))
        Log.i(TAG, "running the game's bundled Ubisoft Connect installer; Connect build before: ${connectBuild(container) ?: "none"}")
        val started = InstallScriptExecutor.runProcessStage.run(context, container, processes, tokens, emptyList())
        return if (started) null else context.getString(R.string.ubisoft_setup_failed)
    }

    /**
     * Setup for a game (Epic or Steam) that bundles no usable installer (none, or only a legacy one that is never run):
     * gets Ubisoft's official one ([UbisoftInstallerDownload], reused when an earlier attempt already fetched it) and
     * runs it silently in one setup session for [container]. The file lives in the container's own `C:` drive, so the
     * stage runs it by its plain `C:` path (no copy of 251 MB), and the session's batch file deletes it as soon as the
     * installer has exited, success or not. For a Steam game this one silent step stands in for the install script's
     * legacy Ubisoft step (the stage only ever runs the first step; the script's registry and file stages still run
     * at launch). [onProgress] gets (bytes so far, total) on this thread. Returns null when the session was launched or
     * [handle] cancelled the download (it deleted the partial file), otherwise a message for the user. Off the main thread.
     */
    fun startSetupWithDownload(
        context: Context, container: Container, handle: UbisoftInstallerDownload.Handle,
        onProgress: (Long, Long) -> Unit,
    ): String? {
        val exe = when (val r = UbisoftInstallerDownload.ensure(container, handle, onProgress)) {
            is UbisoftInstallerDownload.Outcome.Done -> r.file
            is UbisoftInstallerDownload.Outcome.Cancelled -> return null
            is UbisoftInstallerDownload.Outcome.Failed -> return downloadFailureMessage(context, r)
        }
        // Cancel pressed just as the file completed: keep it for the next try, start nothing.
        if (handle.cancelled) return null
        val process = UbisoftInstallerDownload.windowsPathOf(exe)
        val processes = listOf(InstallScriptModel.RunProcess(SETUP_NAME, process, "/S", null, null, null))
        // %INSTALLDIR% is not used by this step; the folder only has to exist for the tokens.
        val tokens = InstallScriptTokens(container, exe.parentFile ?: exe, File(context.filesDir, "imagefs"))
        // An update runs over the old client: the build before it is logged here, the one after shows in the launch decisions note.
        Log.i(TAG, "running the downloaded Ubisoft Connect installer; Connect build before: ${connectBuild(container) ?: "none"}")
        val started = InstallScriptExecutor.runProcessStage.run(context, container, processes, tokens, emptyList())
        return if (started) null else context.getString(R.string.ubisoft_setup_failed)
    }

    /** The user-facing line for a failed download. */
    private fun downloadFailureMessage(context: Context, failed: UbisoftInstallerDownload.Outcome.Failed): String = when (failed.reason) {
        UbisoftInstallerDownload.Reason.NETWORK -> context.getString(R.string.ubisoft_download_failed_network)
        UbisoftInstallerDownload.Reason.HTTP -> context.getString(R.string.ubisoft_download_failed_http, failed.detail)
        UbisoftInstallerDownload.Reason.SIZE -> context.getString(R.string.ubisoft_download_failed_size)
        UbisoftInstallerDownload.Reason.INVALID -> context.getString(R.string.ubisoft_download_failed_invalid)
        UbisoftInstallerDownload.Reason.NO_SPACE -> context.getString(R.string.ubisoft_download_failed_space, UbisoftInstallerDownload.NEEDED_FREE_MB)
        UbisoftInstallerDownload.Reason.IO -> context.getString(R.string.ubisoft_download_failed_io)
    }

    // ---- Epic Games Store titles ----------------------------------------------------------------

    /** Shortcut [Extra Data] stamp: "1" = the game's files say Ubisoft, "0" = looked and it is not, absent = not looked yet. */
    const val EXTRA_UBISOFT = "ubisoft"

    internal const val EPIC_ROOT = "epic_games"

    /** [EXTRA_UBISOFT] as true / false, or null while it has not been worked out. */
    internal fun stampValue(raw: String?): Boolean? = when (raw?.trim()) {
        "1" -> true
        "0" -> false
        else -> null
    }

    /**
     * [epicLikeReason] helper: the path sits in a store-installed Steam game's folder. `steam_games` anywhere counts; a
     * `steamapps/common/` segment pair counts only for an untagged or `steam`-tagged shortcut (a `custom` import of a game
     * that happens to sit in a Steam library folder is not claimed by it).
     */
    private fun isInSteamInstallFolder(path: String, storeSource: String): Boolean {
        if (path.contains("steam_games", ignoreCase = true)) return true
        if (storeSource.isNotEmpty() && storeSource != "steam") return false
        val segs = path.replace('\\', '/').split('/')
        return (0 until segs.size - 2).any { segs[it].equals("steamapps", true) && segs[it + 1].equals("common", true) }
    }

    /**
     * [isEpicLike] with the reason in words (for the launch decisions note and the install-record skip line): the
     * answer and why. Pure string checks, no disk access.
     */
    internal fun epicLikeReason(storeSource: String, path: String): Pair<Boolean, String> {
        val source = storeSource.trim().lowercase()
        if (source == "epic") return true to "storeSource=epic"
        if (source.isNotEmpty() && source != "custom") return false to "storeSource='$storeSource' is another store's tag"
        val shown = if (source.isEmpty()) "empty" else "custom"
        if (isInSteamInstallFolder(path, source)) return false to "storeSource is $shown and the exe path is in a Steam install folder"
        val folder = gameFolderUnder(path, EPIC_ROOT)
            ?: return false to "storeSource is $shown and the exe path is not under epic_games"
        return true to "storeSource is $shown and the exe path is under epic_games (game folder '${folder.name}')"
    }

    /**
     * True for a game that is an Epic install: the Epic store tagged it (`epic`), or it carries no store tag at all /
     * `custom` (an exe import, a folder scan, the Download Manager's launch button: none of them write Epic extras) and
     * its Windows exe [path] sits in a game folder below `epic_games`. Any other tag (steam, gog, amazon, xbox,
     * android...) is never Epic, whatever the path says. Pure string checks, no disk access.
     */
    internal fun isEpicLike(storeSource: String, path: String): Boolean = epicLikeReason(storeSource, path).first

    /**
     * True for an Epic game for the Ubisoft paths (setup offer, overlay, install record, launch report): [isEpicLike] on
     * the shortcut's `storeSource` and exe path. No disk access.
     */
    fun isEpicShortcut(shortcut: Shortcut): Boolean =
        isEpicLike(shortcut.getExtra("storeSource", ""), shortcut.path.orEmpty())

    /** The stamp on [shortcut] (see [EXTRA_UBISOFT]). No disk access. */
    fun epicStamp(shortcut: Shortcut): Boolean? = stampValue(shortcut.getExtra(EXTRA_UBISOFT, ""))

    /**
     * The game folder just below a [root] segment in [path] (`/.../epic_games/<game>/Bin/x.exe` gives
     * `/.../epic_games/<game>`), either separator, any case; null when [path] has no such segment.
     */
    fun gameFolderUnder(path: String, root: String): File? {
        val parts = path.replace('\\', '/').split('/')
        val at = parts.indexOfFirst { it.equals(root, ignoreCase = true) }
        if (at < 0 || at + 1 >= parts.size || parts[at + 1].isEmpty()) return null
        return File(parts.subList(0, at + 2).joinToString("/"))
    }

    /**
     * The install folder of an Epic shortcut's game: the one the store saved for its `epicAppName`, else the folder
     * below `epic_games` on the exe's Android path. Null when neither is there (e.g. the SD card is out).
     */
    private fun epicInstallDirOf(context: Context, shortcut: Shortcut): File? = epicInstallDirRoute(context, shortcut).first

    /**
     * [epicInstallDirOf] with the way it was found in words (for the launch decisions note). A shortcut without an
     * `epicAppName` (one the store did not write) goes straight to the exe path route.
     */
    private fun epicInstallDirRoute(context: Context, shortcut: Shortcut): Pair<File?, String> {
        val appName = shortcut.getExtra("epicAppName", "")
        if (appName.isNotEmpty()) {
            val saved = EpicCloudSavePaths.installDir(context, appName)?.takeIf { it.isDirectory }
            if (saved != null) return saved to "the folder the Epic store saved for epicAppName='$appName'"
        }
        val exe = runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path) }.getOrNull()
            ?: return null to "no saved folder (epicAppName='$appName') and the exe path '${shortcut.path}' does not map to a host path"
        val below = gameFolderUnder(exe.absolutePath, EPIC_ROOT)?.takeIf { it.isDirectory }
        return if (below != null) below to "the game folder below epic_games on the exe path ${exe.absolutePath}"
        else null to "no saved folder (epicAppName='$appName') and no existing game folder below epic_games on ${exe.absolutePath}"
    }

    /**
     * True when a stamp of [stamp] should be looked at again: only a "not Ubisoft" (`0`) that has not been
     * re-checked yet in this app run. A `1` stands, an unset stamp is worked out the normal way.
     */
    internal fun stampNeedsRecheck(stamp: Boolean?, alreadyChecked: Boolean): Boolean = stamp == false && !alreadyChecked

    private const val RECHECKED_MAX = 512

    /** Shortcut files whose `0` stamp was re-checked in this app run (bounded: past the cap it starts over). */
    private val rechecked: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** True once [shortcut]'s `0` stamp was re-checked in this app run (see [isEpicUbisoft]). No disk access. */
    fun epicStampRechecked(shortcut: Shortcut): Boolean = rechecked.contains(shortcut.file.path)

    /** Takes [shortcut]'s one re-check of this run; false when it was taken already. */
    private fun claimRecheck(shortcut: Shortcut): Boolean {
        if (rechecked.size >= RECHECKED_MAX) rechecked.clear()
        return rechecked.add(shortcut.file.path)
    }

    /**
     * The stamp to write for a game folder: "1" when its files say Ubisoft, "0" only when it holds files and none
     * of them do, null (leave the stamp unset, look again at launch) for an empty or unreadable folder.
     */
    internal fun stampFor(folderHasFiles: Boolean, ubisoft: Boolean): String? = when {
        ubisoft -> "1"
        folderHasFiles -> "0"
        else -> null
    }

    /** [stampFor] for the game folder [dir]; null when it is missing, unreadable or has no file in its root. */
    @JvmStatic
    fun stampForFolder(dir: File?): String? {
        if (dir == null || !dir.isDirectory) return null
        val ubisoft = hasLauncherFiles(dir)
        return stampFor(ubisoft || dir.listFiles()?.any { it.isFile } == true, ubisoft)
    }

    /**
     * Drops a stale `ubisoft=0` line from a copied shortcut's text so the copy works it out again (a `0` may have been
     * stamped while the game folder was empty or partial). A `ubisoft=1` and every other line stay as they are.
     */
    @JvmStatic
    fun dropStaleStamp(text: String): String {
        if (!text.contains("$EXTRA_UBISOFT=")) return text
        return text.split('\n').filterNot { line ->
            val t = line.trim()
            t.startsWith("$EXTRA_UBISOFT=") && stampValue(t.substring(EXTRA_UBISOFT.length + 1)) == false
        }.joinToString("\n")
    }

    /**
     * True when the Epic [shortcut]'s game needs Ubisoft Connect. A plain read of the [EXTRA_UBISOFT] stamp; a
     * shortcut without one (written before the stamp existed) gets it worked out from the game's files once and saved.
     * A `0` is looked at once more per app run (it may have been stamped on an empty or partial folder): when a loader
     * is now in the folder's root the stamp becomes `1`, otherwise the `0` stays and is not looked at again until the
     * app restarts. Only those looks touch the disk, so call this off the main thread.
     */
    internal fun isEpicUbisoft(context: Context, shortcut: Shortcut): Boolean {
        if (!isEpicShortcut(shortcut)) return false
        epicStamp(shortcut)?.let { stamp ->
            if (!stampNeedsRecheck(stamp, epicStampRechecked(shortcut)) || !claimRecheck(shortcut)) return stamp
            // Folder not found or still no loader in its root: the 0 stays. The root names are all the re-check reads.
            val dir = epicInstallDirOf(context, shortcut) ?: return false
            val names = dir.list() ?: return false
            if (!rootHasLoader(dir, names)) return false
            scans.remove(dir.absolutePath) // a scan remembered from the stale look would answer the same again
            stampEpic(shortcut, true)
            return true
        }
        // No folder to look in: say no now and look again next time instead of stamping a guess.
        val dir = epicInstallDirOf(context, shortcut) ?: return false
        val ubisoft = hasLauncherFiles(dir)
        stampEpic(shortcut, ubisoft)
        return ubisoft
    }

    private fun stampEpic(shortcut: Shortcut, ubisoft: Boolean) {
        try {
            // The stamp is bookkeeping, not an edit by the user: keep the file's modified time (the Games list sorts by it).
            val modified = shortcut.file.lastModified()
            shortcut.putExtra(EXTRA_UBISOFT, if (ubisoft) "1" else "0")
            shortcut.saveData()
            if (modified > 0L) shortcut.file.setLastModified(modified)
        } catch (e: Exception) {
            Log.w(TAG, "could not stamp the Ubisoft tag on ${shortcut.name}", e)
        }
    }

    /** What a Ubisoft launch has to do about Ubisoft Connect first (the Epic and the Steam gate share it). */
    enum class EpicSetup {
        /** Nothing: not a Ubisoft title, Connect is already in the prefix, or the game's folder is not there to look in. */
        NONE,

        /** Offer the one-time setup with the (current) installer the game's folder / depot bundles. */
        BUNDLED,

        /**
         * Offer to download Ubisoft's own current installer ([UbisoftInstallerDownload]): Connect is missing and the game
         * bundles no installer or only a legacy one (see [EpicSetupReason]).
         */
        DOWNLOAD,
    }

    /** Why an [EpicSetup] answer is what it is: the dialog words the download variants by it, the notes print it. */
    enum class EpicSetupReason {
        /** Not a Ubisoft title, or its folder is not there to look in: nothing to decide. */
        NOT_APPLICABLE,

        /** Connect is already in the prefix. */
        CURRENT,

        /** Connect is missing and the game bundles a current-name installer (`UbisoftConnectInstaller...`). */
        MODERN_BUNDLED,

        /** Connect is missing and the only installer the game has is a legacy one (`UplayInstaller.exe`...: a 2019 client that fails here). */
        LEGACY_BUNDLED,

        /** Connect is missing and the game has Ubisoft's launcher files but no installer. */
        NO_INSTALLER,
    }

    /** [setup] and why ([reason]). */
    data class EpicDecision(val setup: EpicSetup, val reason: EpicSetupReason) {
        override fun toString(): String = "$setup ($reason)"

        companion object {
            @JvmField
            val NOT_APPLICABLE = EpicDecision(EpicSetup.NONE, EpicSetupReason.NOT_APPLICABLE)
        }
    }

    /** The decision for a Ubisoft game whose container has no Connect, from the file name of the installer it has (null = none). */
    private fun missingConnectDecision(installerName: String?): EpicDecision = when {
        installerName == null -> EpicDecision(EpicSetup.DOWNLOAD, EpicSetupReason.NO_INSTALLER)
        isModernInstallerName(installerName) -> EpicDecision(EpicSetup.BUNDLED, EpicSetupReason.MODERN_BUNDLED)
        else -> EpicDecision(EpicSetup.DOWNLOAD, EpicSetupReason.LEGACY_BUNDLED)
    }

    /**
     * What an Epic Ubisoft game needs for Connect, from two facts. Pure.
     *  - Connect installed: [EpicSetup.NONE] (whatever its build).
     *  - Connect missing, a current-name bundled installer: [EpicSetup.BUNDLED]; a legacy-name one: [EpicSetup.DOWNLOAD]
     *    ([EpicSetupReason.LEGACY_BUNDLED]); none: [EpicSetup.DOWNLOAD] ([EpicSetupReason.NO_INSTALLER]).
     * [bundledName] is the file name of the installer the game's folder bundles, null when there is none.
     */
    internal fun epicSetupDecision(launcherInstalled: Boolean, bundledName: String?): EpicDecision =
        if (launcherInstalled) EpicDecision(EpicSetup.NONE, EpicSetupReason.CURRENT) else missingConnectDecision(bundledName)

    /**
     * What a Steam game needs for Connect, from four facts. Pure. Connect installed: [EpicSetup.NONE]. Not a Ubisoft
     * title ([ubisoft] false, the depot's files say so): [EpicSetup.NONE] ([EpicSetupReason.NOT_APPLICABLE]). Otherwise
     * as for Epic, with [installerName] = the file name of the installer the depot would run (the best-named Ubisoft
     * step of its install script, else the bundled installer; null = none): a current-name one is run as it always was
     * ([EpicSetup.BUNDLED]), a legacy-name one is never run ([EpicSetup.DOWNLOAD], [EpicSetupReason.LEGACY_BUNDLED]), and a
     * depot with Ubisoft's launcher files ([hasLauncherFiles]) but no installer at all gets [EpicSetup.DOWNLOAD]
     * ([EpicSetupReason.NO_INSTALLER]); without launcher files and an installer there is nothing to set up.
     */
    internal fun steamSetupDecision(launcherInstalled: Boolean, ubisoft: Boolean, installerName: String?, hasLauncherFiles: Boolean): EpicDecision = when {
        launcherInstalled -> EpicDecision(EpicSetup.NONE, EpicSetupReason.CURRENT)
        !ubisoft -> EpicDecision.NOT_APPLICABLE
        installerName == null && !hasLauncherFiles -> EpicDecision.NOT_APPLICABLE
        else -> missingConnectDecision(installerName)
    }

    /**
     * [epicSetupDecision] for an Epic Ubisoft game, read from the disk without side effects: [container]'s prefix
     * and, only while Connect is missing, the installer [installDir] bundles. [stamp] = the shortcut's Ubisoft stamp;
     * anything but `1` answers [EpicDecision.NOT_APPLICABLE], as does a missing folder while Connect is missing.
     * Shared by [epicSetupFor] and the diagnostics.
     */
    fun epicDecisionNow(container: Container, stamp: Boolean?, installDir: File?): EpicDecision {
        if (stamp != true) return EpicDecision.NOT_APPLICABLE
        if (launcherInstalled(container)) return epicSetupDecision(true, null)
        if (installDir == null) return EpicDecision.NOT_APPLICABLE
        return epicSetupDecision(false, bundledInstaller(installDir)?.name)
    }

    /**
     * What the Epic [shortcut] needs before it launches (see [EpicDecision]). A stamped "not Ubisoft" game answers
     * [EpicDecision.NOT_APPLICABLE] without touching the disk; otherwise a few file stats, plus the depot search for a
     * Ubisoft title whose container has no Connect. Call off the main thread.
     */
    fun epicSetupFor(context: Context, shortcut: Shortcut): EpicDecision {
        if (!isEpicUbisoft(context, shortcut)) return EpicDecision.NOT_APPLICABLE
        val container = shortcut.container
        val installed = launcherInstalled(container)
        val decision = epicDecisionNow(container, true, if (installed) null else epicInstallDirOf(context, shortcut))
        // Safety net only: the setup session's own batch deletes the downloaded installer as soon as it has run (see
        // UbisoftInstallerDownload.cleanupBatchLines). This catches a session that died before that; a stat when nothing is there.
        if (installed) UbisoftInstallerDownload.deleteDownloaded(container)
        return decision
    }

    /**
     * Launch-worker hook for an Epic game: turns the Ubisoft Connect overlay off when the game needs Connect (see
     * [disableOverlay]). A stamp read and nothing else for every other Epic game, a no-op for non-Epic shortcuts. Never throws.
     */
    @JvmStatic
    fun applyEpicPreLaunch(context: Context, container: Container, shortcut: Shortcut?) {
        if (shortcut == null) return
        try {
            if (isEpicUbisoft(context, shortcut)) disableOverlay(container)
        } catch (t: Throwable) {
            Log.w(TAG, "Epic Ubisoft pre-launch failed for ${shortcut.name}", t)
        }
    }

    /**
     * Launch-worker hook for an Epic game that needs Ubisoft Connect: records the game as installed in [container]'s
     * registry, which is what "Locate installed game" does by hand ([UbisoftInstallRegistry]); without it Connect
     * offers to download the game again. Call before any Wine process starts (it rewrites `system.reg`). A stamp
     * read and nothing else for every other Epic game, a no-op for non-Epic shortcuts. Never throws. Returns what
     * happened in words ([UbisoftInstallRegistry.Outcome], also kept for the launch report), never null.
     */
    @JvmStatic
    fun registerEpicInstall(context: Context, container: Container, shortcut: Shortcut?): UbisoftInstallRegistry.Outcome {
        if (shortcut == null) return UbisoftInstallRegistry.record(skippedNotUbisoft("no shortcut"))
        return try {
            if (!isEpicUbisoft(context, shortcut)) {
                val skipped = skippedNotUbisoft(skipReason(shortcut))
                // record() stays quiet about skips; say so for an Epic game, where a skip is worth knowing about.
                if (isEpicShortcut(shortcut)) Log.i(TAG, "Ubisoft install record skipped for ${shortcut.name}: ${skipped.detail}")
                return UbisoftInstallRegistry.record(skipped)
            }
            val dir = epicInstallDirOf(context, shortcut)
                ?: return UbisoftInstallRegistry.record(
                    UbisoftInstallRegistry.Outcome(
                        UbisoftInstallRegistry.Result.NO_FOLDER,
                        "no install folder for epicAppName='${shortcut.getExtra("epicAppName", "")}' and none below epic_games on '${shortcut.path}'",
                    ),
                )
            UbisoftInstallRegistry.registerDetailed(container, dir, ImageFs.find(context).rootDir)
        } catch (t: Throwable) {
            Log.w(TAG, "Epic Ubisoft install record failed for ${shortcut.name}", t)
            UbisoftInstallRegistry.record(UbisoftInstallRegistry.Outcome(UbisoftInstallRegistry.Result.ERROR, t.toString()))
        }
    }

    private fun skippedNotUbisoft(why: String) =
        UbisoftInstallRegistry.Outcome(UbisoftInstallRegistry.Result.SKIPPED_NOT_UBISOFT, why)

    /** Why [isEpicUbisoft] said no for [shortcut]: its `storeSource`, whether the exe is under `epic_games`, the stamp. No disk access. */
    private fun skipReason(shortcut: Shortcut): String {
        val source = shortcut.getExtra("storeSource", "")
        val path = shortcut.path.orEmpty()
        val (epicLike, why) = epicLikeReason(source, path)
        val underEpic = gameFolderUnder(path, EPIC_ROOT) != null
        val stamp = shortcut.getExtra(EXTRA_UBISOFT, "")
        return "storeSource='$source', exe under epic_games=$underEpic, ubisoft stamp='$stamp' => " +
            if (epicLike) "an Epic game, but not stamped Ubisoft (no loader or installer was found in its folder, or the folder was not found)"
            else "not treated as an Epic game: $why"
    }

    // ---- Launch report trigger ------------------------------------------------------------------

    /**
     * True when an Epic-like game gets the Ubisoft launch report: its stamp says Ubisoft, or this launch's install
     * record found something to record ([last] is anything but "not Ubisoft"), so a wrong stamp cannot hide the report.
     */
    internal fun epicRouteDecision(epicLike: Boolean, stamp: Boolean?, last: UbisoftInstallRegistry.Result?): Boolean =
        epicLike && (stamp == true || (last != null && last != UbisoftInstallRegistry.Result.SKIPPED_NOT_UBISOFT))

    /**
     * [epicRouteDecision] for [shortcut], from what this launch already worked out (the stamp, the install record's
     * outcome). No disk access: call it after [registerEpicInstall] has run.
     */
    @JvmStatic
    fun epicReportRoute(shortcut: Shortcut): Boolean =
        epicRouteDecision(isEpicShortcut(shortcut), epicStamp(shortcut), UbisoftInstallRegistry.last()?.result)

    // ---- Launch decisions note ------------------------------------------------------------------

    /** The note's file name in the game's log folder (listed in `LogRotation.isOurRunLog`). */
    const val DECISIONS_NAME = "launch_decisions.txt"

    /** File names that give a Ubisoft game away when they are part of its exe path: the loaders, the launcher and its installers. */
    private val PATH_MARKERS = LOADER_PREFIXES + listOf("ubisoftgamelauncher", "ubisoftconnect", "uplayinstaller", "uplaylaunch", "/upc.exe")

    /** True when [path] (either separator, any case) itself carries a Ubisoft marker file name. A string check, no disk access. */
    internal fun pathHasUbisoftMarker(path: String): Boolean {
        val p = path.replace('\\', '/').lowercase()
        return PATH_MARKERS.any { p.contains(it) }
    }

    /**
     * What makes a launch worth a decisions note, in words, or null when none of it applies (the usual launch, and an
     * Epic game stamped "not Ubisoft"). Booleans and a string in, so the common case is a few field reads.
     * [epicUbisoft] = an Epic-like shortcut whose stamp says Ubisoft.
     */
    internal fun decisionsTrigger(epicUbisoft: Boolean, steamChain: Boolean, pathMarker: Boolean, diagRoute: String?): String? {
        val parts = ArrayList<String>()
        if (epicUbisoft) parts += "Epic-like shortcut stamped Ubisoft"
        if (steamChain) parts += "Ubisoft launcher chain armed"
        if (pathMarker) parts += "Ubisoft file name in the exe path"
        if (diagRoute != null) parts += "Ubisoft launch report route '$diagRoute'"
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    /** Why a launch got no Ubisoft launch report, in one line. Pure. */
    internal fun noRouteReason(storeSource: String, path: String, stampRaw: String, steamChain: Boolean, registrationSkipped: Boolean): String {
        if (steamChain) return "the Ubisoft launcher chain is armed but the report was not started"
        val (epicLike, why) = epicLikeReason(storeSource, path)
        if (!epicLike) return "$why, and no Ubisoft launcher chain in a Steam launch plan"
        val stamp = stampValue(stampRaw)
        val stampText = when (stamp) {
            true -> "1"
            false -> "0 after recheck"
            null -> "unset"
        }
        return when {
            stamp == true -> "Epic-like and stamped Ubisoft but the report was not started"
            registrationSkipped -> "$why; ubisoft stamp=$stampText and the install record was SKIPPED_NOT_UBISOFT"
            else -> "$why; ubisoft stamp=$stampText"
        }
    }

    /** Everything the decisions note prints, already worked out (see [launchDecisionsText]). */
    internal class LaunchFacts(
        val timestamp: String, val trigger: String,
        val app: String, val flavor: String, val debug: Boolean,
        val shortcutFile: String, val name: String, val exe: String,
        val storeSource: String, val epicAppName: String, val epicSandboxId: String, val epicEos: String,
        val ubisoft: String, val launchMode: String, val steamAppId: String,
        val strictEpic: Boolean, val epicLike: Boolean, val epicLikeWhy: String,
        val installDir: String, val installRoute: String,
        val hasLauncherFiles: String, val stateFile: String,
        val launcherInstalled: String, val connectBuild: String, val bundledInstaller: String, val setupDecision: String,
        val registration: String,
        val route: String?, val routeWhy: String,
    )

    /** The bundled installer [path] (null = none) in words for the notes: size and whether its name is a current one ([isModernInstallerName]). Pure. */
    fun bundledInstallerText(path: String?, bytes: Long): String =
        if (path == null) "none" else "$path ($bytes bytes, current-client name: ${isModernInstallerName(File(path).name)})"

    /** The text of the decisions note for [f]: a few lines, plain, one fact per line. Pure. */
    internal fun launchDecisionsText(f: LaunchFacts): String = buildString {
        append("launch decisions (rewritten on every launch of a Ubisoft-stamped Epic, Ubisoft-marked or Ubisoft-chain shortcut)\n")
        append("timestamp: ").append(f.timestamp).append('\n')
        append("written because: ").append(f.trigger).append('\n')
        append("app: ").append(f.app).append(" flavor=").append(f.flavor).append(" debug=").append(f.debug).append('\n')
        append("shortcut file: ").append(f.shortcutFile).append('\n')
        append("name: ").append(f.name).append('\n')
        append("exe (Windows path in the shortcut): ").append(f.exe).append('\n')
        append("storeSource='").append(f.storeSource).append("'  epicAppName='").append(f.epicAppName).append("'  epicSandboxId='").append(f.epicSandboxId)
            .append("'  epicEos='").append(f.epicEos).append("'  ubisoft='").append(f.ubisoft).append("'  launchMode='").append(f.launchMode)
            .append("'  steamAppId='").append(f.steamAppId).append("'\n")
        append("storeSource is exactly epic: ").append(f.strictEpic).append('\n')
        append("Epic-like (storeSource epic, or empty/custom with the exe under epic_games): ").append(f.epicLike).append(" - ").append(f.epicLikeWhy).append('\n')
        append("install folder: ").append(f.installDir).append("  (found by: ").append(f.installRoute).append(")\n")
        append("hasLauncherFiles(install folder): ").append(f.hasLauncherFiles).append('\n')
        append("uplay_install.state: ").append(f.stateFile).append('\n')
        append("Ubisoft Connect in the prefix: ").append(f.launcherInstalled).append('\n')
        append("Ubisoft Connect build (version.txt): ").append(f.connectBuild).append('\n')
        append("bundled installer: ").append(f.bundledInstaller).append('\n')
        append("Ubisoft Connect setup decision (read-only, as the launch gate would answer now): ").append(f.setupDecision).append('\n')
        append("install record (UbisoftInstallRegistry.last): ").append(f.registration).append('\n')
        append("Ubisoft launch report route: ").append(f.route ?: "none").append(if (f.route == null) " - " + f.routeWhy else "").append('\n')
    }

    /**
     * Builds the decisions note for a launch of [shortcut], or null when the launch is none of the kinds that get one
     * (see [decisionsTrigger]: an Epic game stamped Ubisoft, an armed Ubisoft chain, a report route, a Ubisoft file name
     * in the exe path; the check is a few string tests and touches no file). [steamChain] = the Steam plan armed the
     * Ubisoft chain, [diagRoute] = the report route this launch chose (null = none). The launches that do get a note
     * look at a few files; the installer search goes deep only for a depot that looks like Ubisoft's (see [DepotScan]).
     * Call on the launch worker, after [registerEpicInstall] has run. Never throws.
     */
    @JvmStatic
    fun launchDecisionsFor(context: Context, shortcut: Shortcut?, steamChain: Boolean, diagRoute: String?): String? {
        return try {
            val source = shortcut?.getExtra("storeSource", "").orEmpty()
            val exe = shortcut?.path.orEmpty()
            val strict = source == "epic"
            val (epicLike, why) = epicLikeReason(source, exe)
            val stamp = if (shortcut == null) null else epicStamp(shortcut)
            val trigger = decisionsTrigger(epicLike && stamp == true, steamChain, pathHasUbisoftMarker(exe), diagRoute) ?: return null

            var found: Pair<File?, String> = null to "no shortcut"
            if (shortcut != null) {
                found = epicInstallDirRoute(context, shortcut)
                if (found.first == null && steamChain) {
                    val steamDir = runCatching { EaSupport.installDirOf(shortcut) }.getOrNull()
                    if (steamDir != null) found = steamDir to "the Steam install folder of the shortcut (${found.second})"
                }
            }
            val dir = found.first
            val stateFile = if (dir == null) "no install folder" else {
                val f = File(dir, UbisoftInstallRegistry.STATE_FILE)
                if (!f.isFile) "absent (${f.path})" else {
                    val st = UbisoftInstallState.read(f)
                    if (st == null) "present but not usable (${f.length()} bytes)" else "present, product id ${st.productId} (${st.name})"
                }
            }
            val launcher = if (shortcut == null) "unknown (no shortcut)" else {
                val files = launcherFileStatus(shortcut.container)
                "launcherInstalled=${launcherInstalled(shortcut.container)} (" +
                    files.joinToString(", ") { it.first + "=" + (if (it.second) "present" else "missing") } + ")"
            }
            val bundled = if (dir == null) null else bundledInstaller(dir)
            val setup = when {
                shortcut == null -> "n/a (no shortcut)"
                epicLike -> "Epic gate: " + epicDecisionNow(shortcut.container, stamp, dir)
                steamChain -> "Steam gate: " + steamDecisionNow(shortcut.container, dir)
                else -> "n/a (neither an Epic game nor a Ubisoft launcher chain launch)"
            }
            val last = UbisoftInstallRegistry.last()
            val facts = LaunchFacts(
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                trigger = trigger,
                app = "${com.winhub.hernandez.BuildConfig.VERSION_NAME} (versionCode ${com.winhub.hernandez.BuildConfig.VERSION_CODE})",
                flavor = com.winhub.hernandez.BuildConfig.FLAVOR,
                debug = com.winhub.hernandez.BuildConfig.DEBUG,
                shortcutFile = shortcut?.file?.path ?: "<no shortcut>",
                name = shortcut?.name ?: "<no shortcut>",
                exe = exe,
                storeSource = source,
                epicAppName = shortcut?.getExtra("epicAppName", "").orEmpty(),
                epicSandboxId = shortcut?.getExtra("epicSandboxId", "").orEmpty(),
                epicEos = shortcut?.getExtra("epicEos", "").orEmpty(),
                ubisoft = shortcut?.getExtra(EXTRA_UBISOFT, "").orEmpty(),
                launchMode = shortcut?.getExtra("launchMode", "").orEmpty(),
                steamAppId = shortcut?.getExtra("steamAppId", "").orEmpty(),
                strictEpic = strict,
                epicLike = epicLike,
                epicLikeWhy = why,
                installDir = dir?.path ?: "<none>",
                installRoute = found.second,
                hasLauncherFiles = if (dir == null) "n/a (no folder)" else hasLauncherFiles(dir).toString(),
                stateFile = stateFile,
                launcherInstalled = launcher,
                connectBuild = if (shortcut == null) "unknown (no shortcut)" else connectBuildText(connectBuild(shortcut.container)),
                bundledInstaller = if (dir == null) "n/a (no folder)" else bundledInstallerText(bundled?.path, bundled?.length() ?: 0L),
                setupDecision = setup,
                registration = last?.toString() ?: "none recorded (registerEpicInstall did not run for this launch)",
                route = diagRoute,
                routeWhy = noRouteReason(
                    source, exe, shortcut?.getExtra(EXTRA_UBISOFT, "").orEmpty(), steamChain,
                    last?.result == UbisoftInstallRegistry.Result.SKIPPED_NOT_UBISOFT,
                ),
            )
            launchDecisionsText(facts)
        } catch (t: Throwable) {
            Log.w(TAG, "could not build the launch decisions note", t)
            null
        }
    }

    /** Overwrites the note in [logDir] with [text]. Returns true when it was written. Never throws. */
    @JvmStatic
    fun writeLaunchDecisions(logDir: File, text: String): Boolean = try {
        logDir.mkdirs()
        File(logDir, DECISIONS_NAME).writeText(text, Charsets.UTF_8)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "could not write $DECISIONS_NAME", t)
        false
    }

    // ---- Overlay --------------------------------------------------------------------------------

    /**
     * Turns the Ubisoft Connect overlay off in the launcher's settings before a game starts. The overlay hooks
     * `dxgi.dll`, and on an Arm64EC DXVK that corrupts native code: the game dies about 12 seconds after its window
     * opens, or shows a black screen (DroidDeck #1463, Watch_Dogs 2). A no-op while the launcher is not installed.
     * Returns true when the file was changed. Never throws.
     */
    @JvmStatic
    fun disableOverlay(container: Container): Boolean {
        if (!File(container.rootDir, LAUNCHER_DIR).isDirectory) return false
        val file = File(container.rootDir, SETTINGS_FILE)
        return try {
            if (file.isFile && file.length() > MAX_SETTINGS_BYTES) {
                Log.w(TAG, "Ubisoft Connect settings.yaml is ${file.length()} bytes - left as is")
                return false
            }
            val current = if (file.isFile) file.readText(Charsets.UTF_8) else ""
            val updated = overlayOff(current)
            if (updated == current) return false
            val dir = file.parentFile ?: return false
            dir.mkdirs()
            val tmp = File(dir, ".settings-bl.tmp")
            tmp.writeText(updated, Charsets.UTF_8)
            if (!tmp.renameTo(file)) { file.writeText(updated, Charsets.UTF_8); tmp.delete() }
            Log.i(TAG, "Ubisoft Connect overlay turned off (container ${container.id})")
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not turn the Ubisoft Connect overlay off - left as is", e)
            false
        }
    }

    /**
     * `settings.yaml` text with `overlay: { enabled: false, warning_enabled: false }`. Line-based, not a YAML parser:
     * everything else in the file is kept as written, a missing section or key is added, and a file that already says
     * so comes back unchanged.
     */
    internal fun overlayOff(text: String): String {
        val sep = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = text.replace("\r\n", "\n").split('\n').toMutableList()
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)

        val head = Regex("""^overlay:\s*(#.*)?$""")
        val start = lines.indexOfFirst { head.matches(it) }
        if (start < 0) {
            lines += "overlay:"
            lines += "  enabled: false"
            lines += "  warning_enabled: false"
            return lines.joinToString(sep) + sep
        }
        // The section runs over indented and blank lines; new keys go after its last real line.
        var last = start
        var i = start + 1
        while (i < lines.size && (lines[i].isBlank() || lines[i].startsWith(" ") || lines[i].startsWith("\t"))) {
            if (lines[i].isNotBlank()) last = i
            i++
        }
        for (key in listOf("enabled", "warning_enabled")) {
            val keyLine = Regex("""^ {2}${key}:.*$""")
            val at = (start + 1..last).firstOrNull { keyLine.matches(lines[it]) }
            if (at != null) {
                if (lines[at].trimEnd() != "  $key: false") lines[at] = "  $key: false"
            } else {
                lines.add(last + 1, "  $key: false")
                last++
            }
        }
        return lines.joinToString(sep) + sep
    }
}
