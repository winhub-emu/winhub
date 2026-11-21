package com.winhub.hernandez.store.steamscript

import android.content.Context
import android.content.Intent
import android.util.Log
import com.winhub.hernandez.XServerDisplayActivity
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.store.EaSupport
import com.winhub.hernandez.store.UbisoftInstallerDownload
import com.winhub.hernandez.store.UbisoftSupport
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.core.WineRegistryEditor
import java.io.File

/**
 * Runs a Steam game's `installScript.vdf` recipe inside a Wine container — the install-time step the
 * real Steam client performs and Winlator historically skipped. This is what makes EA-on-Steam titles
 * (Need for Speed Payback, any EA game) auto-install the bundled EA App (EA Desktop) + registry keys +
 * offline entitlement, so DRM/steam_api titles actually RUN after download.
 *
 * Three independent stages, each guarded and each usable on its own:
 *  1. **Registry** — writes via [WineRegistryEditor] into the container's `system.reg`/`user.reg`
 *     (WOW64 views + HKCU/HKLM handled by [InstallScriptTokens]).
 *  2. **Copy Files** — host-side copy of e.g. the Origin LocalContent `.dat` entitlement into
 *     `%PROGRAMDATA%` (same pattern as [com.winhub.hernandez.store.AmazonSdkManager.deploySdkToPrefix]).
 *  3. **Run Process** — runs the bundled installer (EAappInstaller.exe) once, in-prefix, via the
 *     [RunProcessStage] seam (default: a live XServer installer session).
 *
 * The Run-Process stage is deliberately behind an interface so a future "pre-baked container with EA
 * Desktop already installed" path can replace ONLY that stage while Registry + Copy Files keep working
 * unchanged.
 *
 * Lifecycle: there is no container at Steam download-complete, so this runs when a container is bound —
 * primarily [com.winhub.hernandez.store.StarLaunchBridge.writeShortcutAsync] (shortcut creation), with a
 * robustness pass in [XServerDisplayActivity] right before a `steamAppId`-tagged game first launches.
 * Guarded once per (appId, container).
 */
object InstallScriptExecutor {
    private const val TAG = "InstallScript"
    private const val PREFS = "installscript_state"

    /** Swappable Run-Process backend (the fallback seam). Default runs the installer live. */
    @Volatile
    @JvmStatic
    var runProcessStage: RunProcessStage = LiveInstallerRunProcessStage

    /** Outcome of an [execute] call. */
    sealed class Result {
        object NoScript : Result()          // no install script for this game (and no bundled Ubisoft installer)
        object AlreadyDone : Result()       // guard already satisfied for this container
        object LocalApplied : Result()      // registry/copy applied; no run-process needed/launched
        object RunLaunched : Result()
        /**
         * The Ubisoft launcher installer the depot would run is a legacy one (a 2019 client that cannot start here), so
         * no session was launched. The launch gate ([UbisoftSupport.steamSetupFor]) is what offers Ubisoft's own current
         * installer instead; this only tells a script-driven run why it did nothing.
         */
        object LegacyUbisoftInstaller : Result()
        /** A prerequisite (wine-mono) install session was launched first; the Run-Process step is re-driven by [resumePending]. */
        object PrerequisiteLaunched : Result()       // a Run-Process session was launched (app will restart)
        data class Error(val message: String) : Result()
    }

    // ---- Public entry points ---------------------------------------------------------------------

    /**
     * Primary hook (shortcut creation). Best-effort; never throws. Applies the script's local stages
     * and, if present and not yet run, launches the bundled installer for [container]. [exePath] is the
     * game's Android exe path (used to locate the depot's install dir); [steamAppId] must be > 0.
     */
    @JvmStatic
    fun runForShortcut(context: Context, container: Container, steamAppId: Int, exePath: String) =
        runForShortcut(context, container, steamAppId, exePath, false)

    /**
     * As above; [retryRunProcess] = true is the explicit user-driven path (the Games tab's "Set up EA
     * Desktop" dialog, shown only when the bundled client is NOT installed): it clears this
     * (container, appId)'s optimistic "installer launched" mark first, so a setup session that ran but
     * failed (killed, installer error) can be run again instead of being treated as done forever.
     * Returns true when a setup session was actually launched.
     */
    @JvmStatic
    fun runForShortcut(context: Context, container: Container, steamAppId: Int, exePath: String, retryRunProcess: Boolean): Boolean {
        if (steamAppId <= 0) return false
        try {
            val installDir = locateInstallDir(File(exePath)) ?: return false
            // (EA's clean-up is EA-only; a Ubisoft title decides on the launcher files in execute().)
            if (retryRunProcess && !UbisoftSupport.detect(installDir) && !clientInstalled(context, container, installDir)) {
                unmark(container, "runproc", steamAppId)
                // A previous attempt may have died mid-install (wizard abort, killed session): burn then
                // "resumes" the half-registered bundle and fails. Start the retry from a clean slate.
                val cleaned = EaSupport.cleanupFailedInstall(container)
                Log.i(TAG, "retry requested and client not installed — cleared runproc guard for appId $steamAppId (container ${container.id}); leftover state cleaned=$cleaned")
            }
            val r = execute(context, container, steamAppId, installDir, allowRunProcess = true)
            when (r) {
                is Result.Error -> Log.w(TAG, "installScript for appId $steamAppId: ${r.message}")
                else -> Log.d(TAG, "installScript for appId $steamAppId -> ${r::class.simpleName}")
            }
            return r is Result.RunLaunched
        } catch (e: Exception) {
            Log.w(TAG, "runForShortcut failed for appId $steamAppId", e)
            return false
        }
    }

    /**
     * Robustness hook (pre-first-launch). Applies ONLY the local stages (Registry + Copy Files) so a
     * game's keys/entitlement are present before it boots, even for shortcuts created before this
     * feature existed. The Run-Process stage is intentionally not driven from inside a launching game
     * session; the primary hook owns it. Best-effort; never throws.
     */
    @JvmStatic
    fun applyLocalStagesForLaunch(context: Context, container: Container, steamAppId: Int, exePath: String) {
        if (steamAppId <= 0) return
        try {
            val installDir = locateInstallDir(File(exePath)) ?: return
            // ALWAYS re-apply here (idempotent). This hook runs after the prefix is prepared and before
            // Wine starts — the only moment a registry write is guaranteed to survive: writes made
            // while a session is running are overwritten when wineserver saves its own copy at exit,
            // and writes made before a brand-new prefix's first boot are lost to prefix creation
            // (device-proven 2026-09-06: keys written at 00:51:44 during a session, system.reg rewritten
            // 00:51:56, EA Desktop then reported the game as not installed).
            execute(context, container, steamAppId, installDir, allowRunProcess = false, forceLocal = true)
        } catch (e: Exception) {
            Log.w(TAG, "applyLocalStagesForLaunch failed for appId $steamAppId", e)
        }
    }

    /**
     * Full executor. Locates + parses the script, applies stages honouring per-(appId, container)
     * guards. When [allowRunProcess] is false, only Registry + Copy Files run.
     */
    fun execute(
        context: Context, container: Container, appId: Int, installDir: File, allowRunProcess: Boolean,
        forceLocal: Boolean = false,
    ): Result {
        val scriptFile = locateScript(installDir)
        // A Ubisoft depot that bundles its launcher installer still gets it run when it has no script at all. Without a
        // script only that run could do anything, so the launch-time pass (no Run Process) stops here, on the root listing.
        if (scriptFile == null && (!allowRunProcess || UbisoftSupport.bundledInstaller(installDir) == null)) return Result.NoScript
        val model = try {
            InstallScriptModel.parse(scriptFile?.readText() ?: "")
        } catch (e: Exception) {
            return Result.Error("parse failed: ${e.message}")
        }
        val imageFsRoot = File(context.filesDir, "imagefs")
        val tokens = InstallScriptTokens(container, installDir, imageFsRoot)

        // Local stages (idempotent). Registry only lands when the prefix has booted at least once,
        // else wineboot could clobber a hand-written hive; the launch-time hook re-applies then.
        var localComplete = true
        if (forceLocal || !localDone(container, appId)) {
            val registryApplied = applyRegistry(container, model, tokens)
            applyCopyFiles(model, tokens)
            localComplete = registryApplied || !model.hasRegistry
            if (localComplete) markLocalDone(container, appId)
        }

        if (!allowRunProcess) return Result.LocalApplied

        // The depot's bundled launcher installer is added unless the script already runs one (the gate answers BUNDLED
        // from it). Looked for only here: the launch-time pass above never runs a process, so it never searches the depot.
        val scriptProcesses = UbisoftSupport.withBundledInstaller(model.runProcesses, installDir)
        // The Run-Process stage only runs the first entry, so Ubisoft's launcher installer has to be that one.
        val ubisoftRun = scriptProcesses.any { UbisoftSupport.isInstaller(it.process) }
        val runProcesses = if (ubisoftRun) UbisoftSupport.installerFirst(scriptProcesses) else scriptProcesses

        // Ubisoft's launcher installer is judged by the launcher files in the prefix, never by the "already ran" mark or
        // the script's registry guard: a session that was killed mid-install leaves both behind with nothing installed.
        val needRun = if (ubisoftRun) !UbisoftSupport.launcherInstalled(container)
            else runProcesses.isNotEmpty() && !runProcDone(container, appId) && !runGuardSatisfied(model, tokens)
        if (needRun && ubisoftRun && !UbisoftSupport.isModernInstaller(runProcesses.first().process)) {
            // Only the current Ubisoft Connect installer is run from a depot (UplayInstaller.exe and the like install a
            // 2019 client that cannot start here): the Games tab offers Ubisoft's own download for these.
            Log.w(TAG, "appId $appId: the depot's Ubisoft installer '${runProcesses.first().process}' is a legacy one - not run")
            return Result.LegacyUbisoftInstaller
        }
        if (needRun) {
            // Prerequisite: EA's bundled installer (EAappInstaller.exe -> EA Desktop MSI) runs MANAGED
            // .NET custom actions, which need wine-mono in the prefix — without it the MSI dies with
            // 0x8007065b (device-proven, Aug 2026). Install the mono component first (its own
            // auto-closing session; the app restarts), remember this script, and let [resumePending]
            // re-drive the Run-Process step once mono is in.
            // wine-mono is staged (downloaded into the prefix) here and installed silently by the SAME
            // setup session that runs the bundled installer — one session, no app restart in between,
            // no second dialog (device test #2 showed the two-session chain confusing: a silent MSI
            // means a black desktop with nothing telling the user it is working).
            val firstRun = runProcesses.firstOrNull { it.process.isNotBlank() }
            val preMsis = ArrayList<String>()   // Windows paths, installed silently before the bundled installer
            if (firstRun != null && EaSupport.runProcessNeedsMono(firstRun.process)) {
                val staging = "C:\\windows\\temp\\bannerlator_components\\"
                if (!EaSupport.hasMono(container)) {
                    val msi = EaSupport.stageMonoMsi(context, container)
                        ?: return Result.Error("wine-mono is required but could not be downloaded")
                    preMsis += staging + msi.name
                    Log.i(TAG, "Run-Process needs wine-mono (container ${container.id}) — staged ${msi.name}")
                }
                if (!EaSupport.hasGecko(container)) {
                    // EA's MSI custom actions (64-bit msiexec) load mshtml → real Gecko for both arches,
                    // else the install dies 0x8007065b / stalls on Wine's Gecko download dialog.
                    val msis = EaSupport.stageGeckoMsis(context, container)
                    if (msis.isEmpty()) return Result.Error("Wine Gecko is required but could not be downloaded")
                    for (m in msis) preMsis += staging + m.name
                    Log.i(TAG, "Run-Process needs Wine Gecko (container ${container.id}) — staged ${msis.map { it.name }}")
                }
            }
            clearPending(context)
            // Mark optimistically before we hand off — the session restarts the app, mirroring
            // ComponentExecInstaller's fire-and-forget cursor advance.
            markRunProcDone(container, appId)
            val launched = runProcessStage.run(context, container, runProcesses, tokens, preMsis)
            return if (launched) Result.RunLaunched else Result.LocalApplied
        }
        return if (localComplete) Result.LocalApplied else Result.AlreadyDone
    }

    // ---- Stage 1: Registry -----------------------------------------------------------------------

    /** Applies all registry writes; returns true if the prefix was ready and writes were attempted. */
    fun applyRegistry(container: Container, model: InstallScriptModel, tokens: InstallScriptTokens): Boolean {
        if (!model.hasRegistry) return true
        val systemReg = File(container.rootDir, ".wine/system.reg")
        if (!systemReg.exists()) {
            // Prefix not generated yet — defer to the launch-time hook so wineboot doesn't overwrite us.
            Log.d(TAG, "Registry deferred: prefix not booted (container ${container.id})")
            return false
        }
        // Group by hive file so each hive is opened/rewritten once (close() clones + renames).
        val byFile = HashMap<File, MutableList<Pair<InstallScriptTokens.RegTarget, InstallScriptModel.RegistryWrite>>>()
        for (w in model.registryWrites) {
            val targets = tokens.registryTargets(w.hiveRoot, w.keyPath)
            if (targets.isEmpty()) {
                Log.w(TAG, "Unmapped hive root '${w.hiveRoot}' — skipping ${w.keyPath}\\${w.name}")
                continue
            }
            for (target in targets) byFile.getOrPut(target.hiveFile) { ArrayList() }.add(target to w)
        }
        for ((hiveFile, writes) in byFile) {
            try {
                WineRegistryEditor(hiveFile).use { reg ->
                    reg.setCreateKeyIfNotExist(true)
                    for ((target, w) in writes) writeOne(reg, target.key, w, tokens)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Registry write to ${hiveFile.name} failed", e)
            }
        }
        return true
    }

    private fun writeOne(
        reg: WineRegistryEditor, key: String, w: InstallScriptModel.RegistryWrite, tokens: InstallScriptTokens,
    ) {
        when (w.type) {
            "string", "expand_string", "multi_string" -> {
                // expand_string/multi_string are written as REG_SZ (WineRegistryEditor has no native
                // writer for them); Wine still expands %VARS% at read time. See TODO in the summary.
                reg.setStringValue(key, w.name, tokens.substituteWindowsForRegistry(w.value))
            }
            "dword" -> reg.setDwordValue(key, w.name, parseInt(tokens.substituteWindowsForRegistry(w.value)))
            "qword" -> reg.setStringValue(key, w.name, tokens.substituteWindowsForRegistry(w.value)) // no qword writer
            "binary" -> reg.setHexValue(key, w.name, tokens.substituteWindowsForRegistry(w.value).replace(" ", ""))
            else -> reg.setStringValue(key, w.name, tokens.substituteWindowsForRegistry(w.value))
        }
    }

    private fun parseInt(s: String): Int =
        try { Integer.decode(s.trim()) } catch (e: NumberFormatException) { s.trim().toLongOrNull(16)?.toInt() ?: 0 }

    // ---- Stage 2: Copy Files ---------------------------------------------------------------------

    /** Copies each `Copy Files` entry host-side (source under %INSTALLDIR%, dest under %PROGRAMDATA%). */
    fun applyCopyFiles(model: InstallScriptModel, tokens: InstallScriptTokens) {
        for (cf in model.copyFiles) {
            val src = tokens.resolveHostPath(cf.src)
            val dst = tokens.resolveHostPath(cf.dst)
            if (!src.exists()) {
                Log.w(TAG, "Copy Files: source missing ${src.absolutePath}")
                continue
            }
            // Idempotent: skip when an identical-size copy is already in place.
            if (dst.isFile && dst.length() == src.length()) continue
            dst.parentFile?.mkdirs()
            if (FileUtils.copy(src, dst)) Log.d(TAG, "Copied ${src.name} -> ${dst.absolutePath}")
            else Log.w(TAG, "Copy failed ${src.absolutePath} -> ${dst.absolutePath}")
        }
    }

    // ---- Stage 3: Run Process guard --------------------------------------------------------------

    /** True when the script's own HasRunKey is already present with the expected value (faithful guard). */
    private fun runGuardSatisfied(model: InstallScriptModel, tokens: InstallScriptTokens): Boolean {
        for (rp in model.runProcesses) {
            val keyPath = rp.hasRunKey ?: continue
            val (target, name) = tokens.resolveRunGuard(keyPath) ?: continue
            if (!target.hiveFile.exists()) continue
            val current = try {
                WineRegistryEditor(target.hiveFile).use { it.getStringValue(target.key, name) }
            } catch (e: Exception) { null }
            val expected = rp.hasRunValue
            if (current != null && (expected == null || current.equals(expected, true))) {
                Log.d(TAG, "Run-Process guard satisfied by '$keyPath'")
                return true
            }
        }
        return false
    }

    // ---- Script / install-dir location -----------------------------------------------------------

    /** The depot's install directory (`steam_games/<name>`) that [exeOrDir] lives under, or null. */
    fun locateInstallDir(exeOrDir: File): File? {
        val parts = exeOrDir.absolutePath.split('/')
        val idx = parts.indexOf("steam_games")
        if (idx < 0 || idx + 1 >= parts.size) return null
        return File(parts.subList(0, idx + 2).joinToString("/"))
    }

    /**
     * Finds the game's main installScript.vdf under [installDir] (excludes `*_installScript.vdf`). Ubisoft depots
     * (e.g. Assassin's Creed IV Black Flag) name it after the app, `<appId>_install.vdf`; that name is only trusted for a
     * depot whose files say Ubisoft, and the depot is only looked at when such a file is there.
     */
    fun locateScript(installDir: File): File? {
        val script = locateScriptByName(installDir) ?: return null
        if (INSTALL_VDF_BY_APP.matches(script.name) && !UbisoftSupport.hasLauncherFiles(installDir)) return null
        return script
    }

    /**
     * The script [locateScript] would pick, judged by file name alone: it never looks at the depot's other files.
     * [rootNames] = [installDir]'s listing when the caller already has it. Names are tested before any file is stat'ed.
     */
    internal fun locateScriptByName(installDir: File, rootNames: Array<String>? = null): File? {
        val names = rootNames ?: installDir.list() ?: return null
        fun pick(test: (String) -> Boolean): File? =
            names.firstOrNull { test(it) && File(installDir, it).isFile }?.let { File(installDir, it) }
        return pick { it.equals("installScript.vdf", true) }
            // Shallow fallback: a *installscript.vdf that isn't an uninstall companion.
            ?: pick { it.endsWith("installscript.vdf", true) && !it.contains("_installscript", true) }
            ?: pick { INSTALL_VDF_BY_APP.matches(it) }
    }

    private val INSTALL_VDF_BY_APP = Regex("""\d+_install\.vdf""", RegexOption.IGNORE_CASE)

    // ---- Pending resume (prerequisite chain) -----------------------------------------------------

    private const val PENDING_KEY = "pending_after_prereq"   // "<containerId>|<appId>|<installDir>"

    private fun savePending(c: Context, containerId: Int, appId: Int, installDir: File) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PENDING_KEY, "$containerId|$appId|${installDir.absolutePath}").commit()

    private fun clearPending(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(PENDING_KEY).commit()

    /** True when a Run-Process step is waiting on a prerequisite install (see [resumePending]). */
    @JvmStatic
    fun hasPending(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(PENDING_KEY)

    /**
     * Re-drives a script whose Run-Process step was deferred behind a prerequisite (wine-mono). Call
     * once the component installer reports its plan finished (ComponentInstallResume → Done). Returns
     * the executor result, or null when nothing was pending. Best-effort; never throws.
     */
    @JvmStatic
    fun resumePending(context: Context): Result? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PENDING_KEY, null) ?: return null
        return try {
            val parts = raw.split('|', limit = 3)
            val containerId = parts[0].toInt(); val appId = parts[1].toInt(); val installDir = File(parts[2])
            val container = ContainerManager(context).getContainerById(containerId)
            if (container == null || !installDir.isDirectory) { clearPending(context); return Result.Error("pending target gone") }
            val r = execute(context, container, appId, installDir, allowRunProcess = true)
            if (r !is Result.PrerequisiteLaunched) clearPending(context)
            Log.i(TAG, "resumePending appId=$appId container=$containerId -> ${r::class.simpleName}")
            r
        } catch (e: Exception) {
            clearPending(context)
            Log.w(TAG, "resumePending failed", e)
            Result.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * True when the script's own Run-Process guard (e.g. EA Desktop's `InstallSuccessful`) is already
     * satisfied in [container] — i.e. the bundled client is installed and a launch can proceed without
     * an install session. False when the script has no Run-Process step (nothing to install).
     */
    @JvmStatic
    fun clientInstalled(context: Context, container: Container, installDir: File): Boolean {
        val scriptFile = locateScript(installDir) ?: return true
        val model = try { InstallScriptModel.parse(scriptFile.readText()) } catch (e: Exception) { return true }
        if (!model.hasRunProcess) return true
        val tokens = InstallScriptTokens(container, installDir, File(context.filesDir, "imagefs"))
        return runGuardSatisfied(model, tokens)
    }

    /** True when [installDir] ships a main installScript with a Run-Process step (a bundled client installer). */
    @JvmStatic
    fun hasRunProcessScript(installDir: File): Boolean {
        val scriptFile = locateScript(installDir) ?: return false
        return try { InstallScriptModel.parse(scriptFile.readText()).hasRunProcess } catch (e: Exception) { false }
    }

    // ---- Per-(appId, container) guards -----------------------------------------------------------
    // Marker FILES inside the prefix (drive_c/bl_installscript/state/<appId>.<kind>), not app prefs:
    // a deleted-and-recreated container reuses its id, and a prefs guard keyed on the id then
    // silently skipped Registry/Copy Files for the new prefix (device test 2026-09-06: EA Desktop
    // reported "GameNotInstalled" because the EA Games keys + Origin licence were never written).

    private fun marker(container: Container, kind: String, appId: Int): File =
        File(container.rootDir, ".wine/drive_c/bl_installscript/state/$appId.$kind")

    private fun localDone(container: Container, appId: Int) = marker(container, "local", appId).exists()
    private fun markLocalDone(container: Container, appId: Int) = mark(container, "local", appId)
    private fun runProcDone(container: Container, appId: Int) = marker(container, "runproc", appId).exists()
    private fun markRunProcDone(container: Container, appId: Int) = mark(container, "runproc", appId)

    private fun mark(container: Container, kind: String, appId: Int) {
        try {
            val f = marker(container, kind, appId); f.parentFile?.mkdirs()
            f.writeText(java.util.Date().toString())
        } catch (e: Exception) { Log.w(TAG, "could not write $kind marker", e) }
    }

    private fun unmark(container: Container, kind: String, appId: Int) {
        try { marker(container, kind, appId).delete() } catch (e: Exception) { Log.w(TAG, "could not clear $kind marker", e) }
    }

    // ---- Run-Process seam ------------------------------------------------------------------------

    /**
     * Pluggable backend for the script's `Run Process` step. The default [LiveInstallerRunProcessStage]
     * runs the bundled installer in a live container session. A future pre-baked-container backend can
     * implement this to no-op (EA Desktop already present) without touching Registry/Copy Files.
     */
    interface RunProcessStage {
        /** Runs [processes] for [container]. Returns true if a session was launched (app will restart). */
        fun run(
            context: Context, container: Container,
            processes: List<InstallScriptModel.RunProcess>, tokens: InstallScriptTokens,
            preMsis: List<String> = emptyList(),
        ): Boolean
    }

    /**
     * Runs the first `Run Process` entry as a live installer session, reusing the same auto-close
     * mechanism as [com.winhub.hernandez.components.ComponentExecInstaller]: a transient `.desktop` with
     * `Exec=wine <exe>` + `[Extra Data]` execArgs/envVars, launched into [XServerDisplayActivity] with
     * `component_installer_exe` so the session ends when the installer exits.
     *
     * IMPORTANT: the command line is passed **verbatim** (leading `KEY=VALUE` tokens become env vars,
     * the rest args) — silent/quiet flags are NOT stripped (unlike ComponentExecInstaller), because the
     * EA installer is meant to run unattended (`EAX_LAUNCH_CLIENT=0 IGNORE_INSTALLED=1`).
     */
    object LiveInstallerRunProcessStage : RunProcessStage {
        override fun run(
            context: Context, container: Container,
            processes: List<InstallScriptModel.RunProcess>, tokens: InstallScriptTokens,
            preMsis: List<String>,
        ): Boolean {
            val rp = processes.firstOrNull { it.process.isNotBlank() } ?: return false
            // Run the installer through an ASCII alias of the game folder. EA's burn bootstrapper died
            // with ERROR_PATH_NOT_FOUND (exit 3) one second into "cache package" when its own source
            // path was Z:\steam_games\Need for Speed™ Most Wanted\… (device test #4); the install that
            // worked ran from a folder renamed to plain ASCII. Same trick the SteamLite launcher uses
            // for steamapps\common: a symlink C:\bl_installscript\<appId> -> the real install dir.
            val winProcess = aliasedProcessPath(context, container, tokens, rp.process)
            // The command line is passed VERBATIM as arguments. EA's burn bootstrapper takes
            // EAX_LAUNCH_CLIENT=0 IGNORE_INSTALLED=1 as command-line properties (that is how the
            // device-proven manual run passed them); an earlier revision split leading NAME=VALUE
            // tokens off as environment variables and the installer exited without installing.
            // Passed VERBATIM (Steam's own args, e.g. EAX_LAUNCH_CLIENT=0 IGNORE_INSTALLED=1). The EA
            // installer keeps its wizard: the user drives it (product decision 2026-09-06). Its
            // Wine-side GDI+ abort is fixed in the layer (gdiplus region.c assert → clamp), not here.
            val command = tokens.substituteWindows(rp.command).trim()

            // One setup session driven by a batch file: (optional) silent wine-mono MSI, then the
            // bundled installer with `start /wait`, then an exit-code marker. Same shape as the
            // hand-run C:\ea_setup\run.bat that installed EA Desktop on device (2026-09-05). The
            // installer exe path is passed as %1 (a Unicode-safe command-line argument) so a
            // non-ASCII game folder never has to survive the batch file's ANSI codepage.
            val safe = safeFileName(rp.name)
            val driveC = File(container.rootDir, ".wine/drive_c")
            val bat = File(driveC, "bl_installscript_$safe.bat")
            val exitMarker = "C:\\bl_installscript_$safe.exit"
            val lines = ArrayList<String>()
            lines += "@echo off"
            lines += "title WinHub setup - ${rp.name}"
            lines += "if exist $exitMarker del $exitMarker"
            lines += "echo WinHub: running the Steam install script step \"${rp.name}\"."
            lines += "echo This window closes by itself when everything has finished. Please wait."
            val total = preMsis.size + 1
            preMsis.forEachIndexed { i, msi ->
                val name = msi.substringAfterLast('\\')
                lines += "echo."
                lines += "echo [${i + 1}/$total] Installing $name (runtime the installer needs) - about 1-2 minutes..."
                lines += "msiexec /i \"$msi\" /qn"
                lines += "echo       $name exit code %ERRORLEVEL%"
            }
            lines += "echo."
            lines += "echo [$total/$total] Running the bundled installer - follow its prompts; this can take 2-4 minutes..."

            lines += "start /wait \"\" %1 $command"
            lines += "echo %ERRORLEVEL% > $exitMarker"
            // The bundled installer is usually a stub: `start /wait` returns as soon as it has spawned
            // its real (clean-room / elevated) worker, and the session's process watch then saw
            // nothing it recognised and closed the container 10 s into the install (device test #9).
            // Keep this batch — and therefore the session — alive until the install is really done:
            // the script's own HasRun registry value appears, or no installer-like process has been
            // seen for ~30 s, capped at 15 minutes.
            val exeBase = winProcess.substringAfterLast('\\').substringBeforeLast('.')
            // findstr: ONLY the first bare quoted string is a pattern (the rest are FILE names) — every
            // pattern must be its own /c:"…" (device test #10: the loop never matched, gave up after
            // 30 s and the session closed mid-install).
            val procPattern = listOf(exeBase, "EAappOfflineInstaller", "EAappInstaller", "msiexec", "UbisoftConnectInstaller")
                .distinct().joinToString(" ") { "/c:\"$it\"" }
            val guard = rp.hasRunKey?.trim()?.replace('/', '\\')
            val guardKey = guard?.substringBeforeLast('\\', "")?.takeIf { it.isNotEmpty() }
            val guardName = guard?.substringAfterLast('\\')
            lines += "echo."
            lines += "echo Waiting for the installer to finish - follow its prompts if it shows any..."
            lines += "set BL_N=0"
            lines += "set BL_MISS=0"
            lines += ":bl_wait"
            if (guardKey != null && !guardName.isNullOrEmpty()) {
                lines += "reg query \"$guardKey\" /v \"$guardName\" >nul 2>&1"
                lines += "if not errorlevel 1 goto bl_done"
            }
            lines += "tasklist 2>nul | findstr /i $procPattern >nul 2>&1"
            lines += "if errorlevel 1 (set /a BL_MISS+=1) else (set BL_MISS=0)"
            lines += "if %BL_MISS% GEQ 12 goto bl_done"
            lines += "set /a BL_N+=1"
            lines += "if %BL_N% GEQ 180 goto bl_done"
            lines += "timeout /t 5 /nobreak >nul 2>&1"
            lines += "goto bl_wait"
            lines += ":bl_done"
            // Ubisoft's own installer, downloaded by the app (never a game's bundled one): delete it now that the wait
            // above is over, success or not. This line is only reached after `start /wait` returned AND no installer
            // process was seen for 60 s (or the guard / the 15 minute cap ended the wait), and the session closes right
            // after this batch, so the delete cannot cut the install short.
            lines += UbisoftInstallerDownload.cleanupBatchLines(winProcess)
            lines += "echo Installer finished. Closing this window..."
            bat.writeText(lines.joinToString("\r\n") + "\r\n", Charsets.US_ASCII.takeIf { lines.all { l -> l.all { c -> c.code < 128 } } } ?: Charsets.UTF_8)

            val batWin = "C:\\" + bat.name
            val execTarget = WinePath.escapeForExec("C:\\windows\\system32\\cmd.exe")
            val execArgs = "/c $batWin \"$winProcess\""

            val desktopDir = File(context.filesDir, "desktops").apply { mkdirs() }
            val shortcut = File(desktopDir, "installscript_$safe.desktop").apply {
                writeText(buildString {
                    append("[Desktop Entry]\n")
                    append("Name=").append(rp.name).append("\n")
                    append("Exec=wine ").append(execTarget).append("\n")
                    append("Type=Application\n")
                    append("StartupWMClass=explorer\n")
                    append("\ncontainer_id:").append(container.id).append("\n")
                    append("\n[Extra Data]\n")
                    append("execArgs=").append(execArgs).append("\n")
                })
            }

            val intent = Intent(context, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", container.id)
            intent.putExtra("shortcut_path", shortcut.absolutePath)
            intent.putExtra("shortcut_name", shortcut.nameWithoutExtension)
            // The session auto-closes once the batch's cmd.exe is gone (msiexec / the installer are
            // matched too by the installer watch, so the hand-offs inside the batch never trip it).
            intent.putExtra("component_installer_exe", "cmd.exe")
            intent.putExtra("component_installer_label", (if (preMsis.isNotEmpty()) "runtimes + " else "") + rp.name)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched setup session for '${rp.name}' (batch ${bat.name}, preMsis=${preMsis.size})")
            return true
        }
    }

    /** A name from an install script as one safe file name: only letters, digits, `.`, `_` and `-` are kept. */
    internal fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "installscript" }

    /** True when [file]'s canonical path is below the folder [root] (`..` and symlinks resolved). */
    internal fun isUnder(root: File, file: File): Boolean =
        file.canonicalPath.startsWith(root.canonicalPath.trimEnd(File.separatorChar) + File.separator)

    /**
     * Windows path of a Run-Process exe, relocated to a REAL directory under `C:\\bl_installscript\\<ascii>`
     * when the exe lives under %INSTALLDIR%: the exe's directory (regular files only, e.g.
     * `__Installer\\Origin\\redist\\internal\\{EAappInstaller.exe, Autorun}`) is copied there, preserving the
     * relative path. Device evidence (2026-09-05): EA's burn bootstrapper dies caching its own payload
     * from a non-ASCII source path (exit 3), and its elevated worker never connects when the source is a
     * symlinked directory; the only run that installed EA Desktop ran from a real ASCII folder. Falls
     * back to the plain token-substituted path when the process is elsewhere or the copy fails.
     */
    private fun aliasedProcessPath(context: Context, container: Container, tokens: InstallScriptTokens, process: String): String {
        val plain = tokens.substituteWindows(process)
        val marker = "%INSTALLDIR%"
        val idx = process.indexOf(marker, ignoreCase = true)
        if (idx < 0) return plain
        val rel = process.substring(idx + marker.length).trimStart('\\', '/').replace('\\', '/')
        val srcExe = File(tokens.installDir, rel)
        if (!srcExe.isFile) return plain
        val alias = tokens.installDir.name.filter { it.code in 0x20..0x7E && it !in "<>:\"/\\|?*" }.trim()
            .ifEmpty { "game" }.replace(' ', '_')
        val dstExe = File(File(container.rootDir, ".wine/drive_c/bl_installscript/$alias"), rel)
        try {
            // A script path with ".." must not carry the copy out of the alias folder.
            if (!isUnder(File(container.rootDir, ".wine/drive_c/bl_installscript"), dstExe)) return plain
            val srcDir = srcExe.parentFile!!; val dstDir = dstExe.parentFile!!
            dstDir.mkdirs()
            // Ubisoft's installer sits in the game root next to the multi-GB archives: copy just the installer.
            val onlyInstaller = UbisoftSupport.isInstaller(process)
            for (f in srcDir.listFiles() ?: emptyArray()) {
                if (!f.isFile || (onlyInstaller && f != srcExe)) continue
                val d = File(dstDir, f.name)
                if (d.isFile && d.length() == f.length()) continue
                if (!com.winhub.hernandez.core.FileUtils.copy(f, d)) { Log.w(TAG, "alias copy failed for ${f.name}"); return plain }
            }
            if (!dstExe.isFile) return plain
        } catch (e: Exception) {
            Log.w(TAG, "alias copy failed, using the real path", e); return plain
        }
        return "C:\\bl_installscript\\$alias\\" + rel.replace('/', '\\')
    }

    /**
     * Fallback seam target: a container pre-baked with EA Desktop already installed. Registry + Copy
     * Files still run (they're per-game); the Run-Process step becomes a no-op. Not wired by default —
     * a future setup can `InstallScriptExecutor.runProcessStage = PrebakedContainerRunProcessStage`.
     */
    object PrebakedContainerRunProcessStage : RunProcessStage {
        override fun run(
            context: Context, container: Container,
            processes: List<InstallScriptModel.RunProcess>, tokens: InstallScriptTokens,
            preMsis: List<String>,
        ): Boolean {
            Log.d(TAG, "Run-Process skipped (pre-baked container backend)")
            return false
        }
    }
}
