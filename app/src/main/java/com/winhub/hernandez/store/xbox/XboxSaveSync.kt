package com.winhub.hernandez.store.xbox

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Cloud saves on session exit. Container saves (XGameSave): the game's own runtime uploads each save in the
 * background as it is written and marks its index "dirty 0" once the cloud has it; quitting right after a save cut
 * that upload off, so the shutdown screen first waits, game still running, until this session's saves are uploaded
 * (bounded). A game that saves on close and exits at once leaves its last save dirty with nothing running to upload
 * it: then bxsaveup.exe (app/src/main/cpp/xbox-saveup) is started in the session through winhandler.exe and opens
 * the game's save provider, whose start-up sync uploads every dirty container. File saves (XGameSaveFiles): Xodus Nexus' xgameruntime starts a helper
 * (`rundll32.exe xgameruntime.dll,XodusSaveFilesMonitor`) that uploads the game's saves once the game process
 * has exited, through our bridge and account service. Bannerlator's teardown stops every Wine process at once,
 * which would kill that helper before it uploads; so for an Xbox session with cloud saves on, the shutdown
 * screen first ends only the game, then waits for the helper to finish (bounded), and only then tears down.
 */
object XboxSaveSync {
    private const val HELPER = "XodusSaveFilesMonitor"

    private fun commandLines(): List<Pair<Int, String>> =
        File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
            if (pid == android.os.Process.myPid()) return@mapNotNull null
            val cmd = runCatching { File(dir, "cmdline").readBytes() }.getOrNull() ?: return@mapNotNull null
            if (cmd.isEmpty()) null else pid to String(cmd).replace('\u0000', ' ')
        }

    /** Starts a Windows program in the running session (winhandler.exe's EXEC); false when it could not be sent. */
    fun interface SessionExec { fun run(windowsPath: String): Boolean }

    /** What the uploader needs: the session's prefix, the imagefs root (Z:), the game, and a way to start it. */
    class Uploader(val prefix: File, val imagefsRoot: File, val productId: String, val exec: SessionExec)

    /**
     * Container saves first: waits up to [containerTimeoutMs] while an index under [saveRoot] (XodusGameSave) changed
     * since [since] is still dirty. Then ends the game ([exeName]) and waits up to [timeoutMs] for the save helper to
     * finish its final upload.
     */
    @JvmStatic fun finish(app: Context, exeName: String, timeoutMs: Long, saveRoot: File?, since: Long, containerTimeoutMs: Long,
                          uploader: Uploader?) {
        if (saveRoot != null) waitForContainerUploads(app, exeName, saveRoot, since, containerTimeoutMs, uploader)
        val helpers = commandLines().filter { it.second.contains(HELPER) }.map { it.first }
        if (helpers.isEmpty()) {
            XboxDiag.log(app, "save sync on exit: no save helper running")
            return
        }
        val games = gameProcesses(exeName)
        games.forEach { pid -> runCatching { Os.kill(pid, OsConstants.SIGKILL) } }
        val start = System.currentTimeMillis()
        while (helpers.any { File("/proc/$it").exists() } && System.currentTimeMillis() - start < timeoutMs) Thread.sleep(250)
        val seconds = (System.currentTimeMillis() - start) / 1000.0
        if (helpers.any { File("/proc/$it").exists() })
            XboxDiag.log(app, "save sync on exit: helper still running after ${"%.1f".format(seconds)} s; local saves upload at the next launch")
        else XboxDiag.log(app, "save sync on exit: helper finished in ${"%.1f".format(seconds)} s (ended ${games.size} game process(es))")
    }

    /** Index files (<xuid>/<scid>/<container>/index) changed since [since] that still say "dirty 1". */
    private fun dirtyContainers(saveRoot: File, since: Long): List<File> =
        saveRoot.listFiles().orEmpty().flatMap { it.listFiles().orEmpty().toList() }
            .flatMap { it.listFiles().orEmpty().toList() }
            .map { File(it, "index") }
            .filter { it.isFile && it.lastModified() >= since && runCatching { it.readLines() }.getOrDefault(emptyList()).any { l -> l.trim() == "dirty 1" } }

    /**
     * The game's own processes: the program path (argv[0]) ends with [exeName]. Not a substring of the whole command
     * line, which also matches the Xodus backend's "run … --exe <exe>" (alive for the whole session) and winhandler.
     */
    private fun gameProcesses(exeName: String): List<Int> =
        File("/proc").listFiles().orEmpty().mapNotNull { dir ->
            val pid = dir.name.toIntOrNull() ?: return@mapNotNull null
            val cmd = runCatching { File(dir, "cmdline").readBytes() }.getOrNull() ?: return@mapNotNull null
            val argv0 = String(cmd, 0, cmd.indexOf(0.toByte()).let { if (it < 0) cmd.size else it })
            pid.takeIf { argv0.endsWith("\\$exeName", ignoreCase = true) || argv0.endsWith("/$exeName", ignoreCase = true) }
        }

    private fun gameRunning(exeName: String): Boolean = gameProcesses(exeName).isNotEmpty()

    /**
     * The upload runs inside the game, so this only helps while the game runs. A game that exits by itself right after
     * its last save (BALL x PIT saves on close, then exits ~1.5 s into the upload) leaves that save dirty; the runtime
     * uploads it when the game next starts.
     */
    private fun waitForContainerUploads(app: Context, exeName: String, saveRoot: File, since: Long, timeoutMs: Long,
                                        uploader: Uploader?) {
        val dirty = dirtyContainers(saveRoot, since)
        if (dirty.isEmpty()) return
        val start = System.currentTimeMillis()
        var left = dirty
        var running = gameRunning(exeName)
        while (left.isNotEmpty() && running && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(250)
            left = dirtyContainers(saveRoot, since)
            running = gameRunning(exeName)
        }
        if (left.isNotEmpty() && !running && uploader != null) {
            XboxDiag.log(app, "save sync on exit: the game closed before ${left.size} save(s) finished uploading; uploading them now")
            left = runUploader(app, uploader, saveRoot, since, left, timeoutMs - (System.currentTimeMillis() - start))
        }
        val seconds = "%.1f".format((System.currentTimeMillis() - start) / 1000.0)
        when {
            left.isEmpty() -> XboxDiag.log(app, "save sync on exit: ${dirty.size} save(s) uploaded in $seconds s")
            !running && uploader == null -> XboxDiag.log(app, "save sync on exit: the game closed before ${left.size} save(s) finished uploading; they upload at the next launch")
            else -> XboxDiag.log(app, "save sync on exit: ${left.size} save(s) not uploaded after $seconds s; they upload at the next launch")
        }
    }

    private const val UPLOADER = "bxsaveup.exe"

    /** Runs bxsaveup.exe for the SCIDs of [dirty] and waits (up to [budgetMs]) until they are clean; returns what is left. */
    private fun runUploader(app: Context, uploader: Uploader, saveRoot: File, since: Long, dirty: List<File>, budgetMs: Long): List<File> {
        val system32 = File(uploader.prefix, "drive_c/windows/system32")
        val temp = File(uploader.prefix, "drive_c/windows/temp").apply { mkdirs() }
        val exe = File(system32, UPLOADER)
        val input = File(temp, "bxsaveup.txt")
        val output = File(temp, "bxsaveup.log")
        try {
            val game = XboxInstallState.get(app, uploader.productId) ?: return dirty
            val config = File(game.folder).listFiles()?.firstOrNull { it.name.equals("MicrosoftGame.config", ignoreCase = true) }
                ?: return dirty.also { XboxDiag.log(app, "save uploader: the game has no MicrosoftGame.config") }
            val relative = runCatching { uploader.imagefsRoot.canonicalFile.toPath().relativize(config.canonicalFile.toPath()) }.getOrNull()
                ?: return dirty
            val scids = dirty.mapNotNull { it.parentFile?.parentFile?.name }.distinct()
            input.writeText("Z:\\" + relative.toString().replace('/', '\\') + "\n" + scids.joinToString("\n") + "\n")
            output.delete()
            app.assets.open("xbox/$UPLOADER").use { inp -> exe.outputStream().use { inp.copyTo(it) } }
            if (!uploader.exec.run("C:\\windows\\system32\\$UPLOADER"))
                return dirty.also { XboxDiag.log(app, "save uploader: could not be started") }
            val start = System.currentTimeMillis()
            var left = dirty
            while (System.currentTimeMillis() - start < budgetMs) {
                Thread.sleep(250)
                left = dirtyContainers(saveRoot, since)
                val result = runCatching { output.readText() }.getOrDefault("")
                if (left.isEmpty() || result.contains("done") || result.contains("error")) break
            }
            runCatching { output.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { XboxDiag.log(app, "save uploader: " + it.replace('\n', ';')) }
            return dirtyContainers(saveRoot, since)
        } catch (e: Exception) {
            XboxDiag.log(app, "save uploader failed: ${e.javaClass.simpleName}")
            return dirty
        } finally {
            exe.delete(); input.delete(); output.delete()
        }
    }
}
