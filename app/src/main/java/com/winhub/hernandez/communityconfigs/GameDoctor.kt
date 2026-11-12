package com.winhub.hernandez.communityconfigs

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.GPUInformation
import com.winhub.hernandez.core.LogInventory
import com.winhub.hernandez.core.LogRotation
import com.winhub.hernandez.core.WaylandAdapterSettings
import com.winhub.hernandez.core.WineWaylandSupport
import com.winhub.hernandez.ui.screens.withGraphicsDriverKeys
import com.winhub.hernandez.fexcore.FEXCorePreset
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * "Why doesn't my game run?" in one place. Reads the game's last run logs, checks its settings against
 * the device, looks up shared configs for it, and turns all of that into plain findings with one-tap
 * fixes. Nothing here writes to a shortcut by itself: a [Tweak] only runs when the user picks it, and
 * [snapshot] / [restore] let the UI undo everything the doctor changed.
 *
 * Blocking (file and system calls): run [diagnose] off the main thread.
 */
object GameDoctor {

    private const val TAG = "GameDoctor"

    /** A run's logs can be huge; the end of each file is where a crash shows up. */
    private const val LOG_TAIL_BYTES = 300_000
    private const val MAX_LOG_FILES = 8

    enum class Level { PROBLEM, WARNING, TIP }

    /** One reversible settings change. [change] edits the shortcut's extras; the caller saves it. */
    class Tweak(val key: String, val label: String, val detail: String, val change: (Shortcut) -> Unit)

    class Finding(val level: Level, val title: String, val detail: String, val tweaks: List<Tweak> = emptyList())

    /** A shared config for this game; [matchesDevice] = it was made on this chipset or GPU. */
    class Pick(val game: ConfigLibrary.Game, val entry: ConfigLibrary.Entry, val matchesDevice: Boolean)

    class Report(
        val hadLogs: Boolean,
        val findings: List<Finding>,
        val picks: List<Pick>,
        /** Safe things to try one at a time when nothing obvious is wrong (or the first fix didn't help). */
        val tweaks: List<Tweak>,
    )

    fun diagnose(context: Context, shortcut: Shortcut, library: List<ConfigLibrary.Game>?): Report {
        val ctx = context.applicationContext
        val tweaks = tweaksFor(ctx, shortcut)
        val logs = readLogs(ctx, shortcut)
        val findings = ArrayList<Finding>()

        if (logs != null) findings += scanLogs(logs, tweaks)
        findings += waylandFindings(ctx, shortcut, tweaks)
        findings += checkSettings(ctx, shortcut, tweaks)

        if (findings.none { it.level == Level.PROBLEM }) {
            findings += Finding(
                Level.TIP,
                if (logs == null) "No log for this game yet" else "Nothing obviously wrong in the last run",
                if (logs == null) "Play it once so there is something to read, then run the doctor again. Meanwhile you can try a shared config."
                else "If it still misbehaves, try a shared config for your chip or one of the changes below, one at a time.",
            )
        }
        return Report(logs != null, findings, picks(ctx, shortcut, library), tweaks.values.toList())
    }

    // ── Logs ──────────────────────────────────────────────────────────────────────────────────

    /** The tail of this game's newest log files joined together, or null when it has none. */
    private fun readLogs(context: Context, shortcut: Shortcut): String? {
        val entry = LogInventory.forGame(context, shortcut.name) ?: return null
        val files = entry.dir.listFiles { f ->
            f.isFile && (f.extension.equals("log", ignoreCase = true) || f.extension.equals("txt", ignoreCase = true))
        }?.sortedByDescending { it.lastModified() }?.take(MAX_LOG_FILES)
        if (files.isNullOrEmpty()) return null
        return files.joinToString("\n") { tail(it) }.ifBlank { null }
    }

    private fun tail(file: File): String = try {
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            val n = minOf(len, LOG_TAIL_BYTES.toLong()).toInt()
            raf.seek(len - n)
            val bytes = ByteArray(n)
            raf.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        }
    } catch (e: IOException) {
        Log.w(TAG, "couldn't read ${file.name}", e)
        ""
    }

    private val ci = RegexOption.IGNORE_CASE

    private val DEVICE_LOST = Regex("VK_ERROR_DEVICE_LOST|device lost", ci)
    private val OUT_OF_MEMORY = Regex("VK_ERROR_OUT_OF_(DEVICE|HOST)_MEMORY|std::bad_alloc|cannot allocate memory|out of memory", ci)
    private val NO_DEVICE = Regex("failed to create (a )?(d3d1[01]|d3d12|vulkan)? ?device|vkCreateDevice failed|VK_ERROR_INCOMPATIBLE_DRIVER|VK_ERROR_INITIALIZATION_FAILED|no suitable vulkan", ci)
    private val MISSING_DLL = Regex("Library\\s+([\\w.\\-+]+\\.dll)[^\\n]{0,200}not found", ci)
    private val CRASH = Regex("Unhandled exception: (page fault|unimplemented|illegal)|EXCEPTION_ACCESS_VIOLATION|c0000005", ci)
    private val ILLEGAL_INSN = Regex("SIGILL|illegal instruction", ci)
    private val ANTI_CHEAT = Regex("EasyAntiCheat|BattlEye|BEService|vgk\\.sys", ci)
    private val DENUVO = Regex("denuvo", ci)

    private fun scanLogs(logs: String, t: Map<String, Tweak>): List<Finding> {
        val out = ArrayList<Finding>()
        fun pick(vararg keys: String) = keys.mapNotNull { t[it] }

        if (DEVICE_LOST.containsMatchIn(logs)) out += Finding(
            Level.PROBLEM, "The GPU driver crashed",
            "The driver lost the GPU mid-game (\"device lost\"). It usually means this driver or DXVK build doesn't suit the game on your chip, or the GPU ran out of headroom.",
            pick("fg_off", "res"),
        )
        if (OUT_OF_MEMORY.containsMatchIn(logs)) out += Finding(
            Level.PROBLEM, "The game ran out of memory",
            "Graphics or system memory ran out. A lower resolution and no frame generation free up the most.",
            pick("fg_off", "res"),
        )
        if (NO_DEVICE.containsMatchIn(logs)) out += Finding(
            Level.PROBLEM, "The graphics device couldn't start",
            "The game asked for a graphics device the current driver stack can't provide. A shared config for your chip usually carries a driver and DXVK that work.",
            pick("vulkan"),
        )
        val dlls = MISSING_DLL.findAll(logs).map { it.groupValues[1].lowercase() }.distinct().take(3).toList()
        if (dlls.isNotEmpty()) out += Finding(
            Level.PROBLEM, "Missing Windows file: ${dlls.joinToString(", ")}",
            "The game needs ${if (dlls.size == 1) "this file" else "these files"} and Wine can't find ${if (dlls.size == 1) "it" else "them"}. " +
                "Install the matching Windows component (Visual C++, DirectX or .NET) in the game's Win Components settings, or place the DLL next to the game's exe.",
        )
        if (CRASH.containsMatchIn(logs)) out += Finding(
            Level.PROBLEM, "The game crashed",
            "Wine reported an access violation. That is most often the CPU translation layer or the Windows runtime. A safer FEX preset is the usual first fix.",
            pick("fex_stability", "fex_compat"),
        )
        if (ILLEGAL_INSN.containsMatchIn(logs)) out += Finding(
            Level.PROBLEM, "The CPU translator hit an unsupported instruction",
            "The game used a CPU instruction the translator can't run in its current mode. The Stability or Compatibility preset trades speed for support.",
            pick("fex_stability", "fex_compat"),
        )
        if (ANTI_CHEAT.containsMatchIn(logs)) out += Finding(
            Level.WARNING, "Anti-cheat detected",
            "This game ships kernel-level anti-cheat. Single-player may work, but online play won't.",
        )
        if (DENUVO.containsMatchIn(logs)) out += Finding(
            Level.WARNING, "Denuvo protection detected",
            "Denuvo titles run best with the Denuvo FEX preset.",
            pick("fex_denuvo"),
        )
        return out
    }

    // ── Wayland pacing ────────────────────────────────────────────────────────────────────────

    private val WL_PERF_RELEASE = Regex("release ([\\d.]+)/[\\d.]+ ms \\((\\d+),")
    private val WL_PERF_ZERO_COPY = Regex("zero-copy (\\d+),")

    /**
     * Wayland sessions keep their own log in the game's log folder ([LogRotation.WAYLAND_DIR]). With
     * zero-copy on, the display layer decides when frames appear, and a game that cannot keep up lets
     * frames queue there: each buffer then waits several frames before the display lets go of it, which
     * shows as uneven frame pacing. The Smooth adapter profile (vsync, no zero-copy) evens that out.
     */
    private fun waylandFindings(context: Context, shortcut: Shortcut, t: Map<String, Tweak>): List<Finding> {
        val tweak = t["wayland_smooth"] ?: return emptyList()
        val dir = LogInventory.forGame(context, shortcut.name)?.dir?.let { File(it, LogRotation.WAYLAND_DIR) } ?: return emptyList()
        val newest = dir.listFiles { f -> f.isFile && LogRotation.isOurWaylandLog(f.name) }?.maxByOrNull { it.name } ?: return emptyList()
        val holds = ArrayList<Double>()
        for (line in tail(newest).lineSequence()) {
            if (!line.contains("perf") || !line.contains("last 10 s")) continue
            val zeroCopy = WL_PERF_ZERO_COPY.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val release = WL_PERF_RELEASE.find(line) ?: continue
            val avg = release.groupValues[1].toDoubleOrNull() ?: continue
            val frames = release.groupValues[2].toIntOrNull() ?: continue
            if (zeroCopy >= 100 && frames >= 100) holds += avg
        }
        if (holds.size < 3) return emptyList()
        val median = holds.sorted()[holds.size / 2]
        if (median < 60.0) return emptyList()
        return listOf(Finding(
            Level.WARNING, "Frames queue up on the Wayland display",
            "In the last run each frame waited about ${median.toInt()} ms on the display layer before it was released " +
                "(a smooth run is closer to 30-35 ms). That shows up as uneven frame pacing. The Smooth adapter profile " +
                "paces frames through the compositor instead.",
            listOf(tweak),
        ))
    }

    // ── Settings vs this device ───────────────────────────────────────────────────────────────

    private fun checkSettings(context: Context, shortcut: Shortcut, t: Map<String, Tweak>): List<Finding> {
        val out = ArrayList<Finding>()
        val gb = totalRamGb(context)
        val width = Regex("(\\d+)x(\\d+)").find(effective(shortcut, "screenSize") { it.getScreenSize() })?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (gb in 1..6 && width >= 1600) out += Finding(
            Level.WARNING, "High resolution for ${gb} GB of RAM",
            "This phone has about $gb GB. Heavy games at ${width}p often run out of memory or stutter.",
            listOfNotNull(t["res"]),
        )
        val dxvkVersion = dxvkVersionOf(effective(shortcut, "dxwrapperConfig") { it.getDXWrapperConfig() })
        if (!GPUInformation.isAdrenoGPU(context) && (dxvkVersion.startsWith("2.") || dxvkVersion.startsWith("3."))) out += Finding(
            Level.WARNING, "Mali GPUs run best on DXVK 1.10.3",
            "You are on DXVK $dxvkVersion. Newer DXVK needs Vulkan features many Mali drivers lack.",
            listOfNotNull(t["dxvk_mali"]),
        )
        return out
    }

    private fun totalRamGb(context: Context): Int = try {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt()
    } catch (e: RuntimeException) {
        0
    }

    private fun dxvkVersionOf(dxwrapperConfig: String): String =
        Regex("(^|,)version=([^,]*)").find(dxwrapperConfig)?.groupValues?.get(2).orEmpty()

    /** The shortcut's own value, else the container's default for the same setting. */
    private fun effective(shortcut: Shortcut, key: String, containerDefault: (com.winhub.hernandez.container.Container) -> String?): String =
        shortcut.getExtra(key, "").ifBlank { shortcut.container?.let(containerDefault).orEmpty() }

    // ── Tweaks: small, individually reversible changes ───────────────────────────────────────

    private fun tweaksFor(context: Context, shortcut: Shortcut): LinkedHashMap<String, Tweak> {
        val t = LinkedHashMap<String, Tweak>()

        val fg = effective(shortcut, "frameGenEngine") { it.frameGenEngine }
        if (fg.isNotBlank() && fg != "off") t["fg_off"] = Tweak(
            "fg_off", "Turn frame generation off",
            "Frame generation spends extra GPU time and memory.",
        ) { it.putExtra("frameGenEngine", "off") }

        val size = effective(shortcut, "screenSize") { it.getScreenSize() }
        Regex("(\\d+)x(\\d+)").find(size)?.let { m ->
            val w = m.groupValues[1].toInt()
            val h = m.groupValues[2].toInt()
            if (w >= 960) {
                val nw = (w * 3 / 4) / 2 * 2
                val nh = (h * 3 / 4) / 2 * 2
                t["res"] = Tweak("res", "Lower the resolution to ${nw}x$nh", "A quarter fewer pixels to draw. Games still fill the screen.") {
                    it.putExtra("screenSize", "${nw}x$nh")
                }
            }
        }

        // Only for a game that actually runs on Wayland and is not already on the Smooth profile.
        if (WineWaylandSupport.runsOnWayland(context, shortcut)) {
            val own = shortcut.getExtra("graphicsDriverConfig", "")
            val containerCfg = shortcut.container?.getGraphicsDriverConfig().orEmpty()
            val profile = WaylandAdapterSettings.resolve(containerCfg, own.ifEmpty { null }, null, null).profile
            if (profile != WaylandAdapterSettings.PROFILE_SMOOTH) t["wayland_smooth"] = Tweak(
                "wayland_smooth", "Use the Smooth Wayland adapter profile",
                "Vsync pacing and background copy, no zero-copy. Evens out uneven frame pacing.",
            ) {
                val base = it.getExtra("graphicsDriverConfig", "").ifEmpty {
                    withGraphicsDriverKeys(
                        it.container?.getGraphicsDriverConfig().orEmpty(),
                        WaylandAdapterSettings.KEYS.associateWith { "" },
                    )
                }
                it.putExtra(
                    "graphicsDriverConfig",
                    withGraphicsDriverKeys(base, mapOf(WaylandAdapterSettings.KEY_PROFILE to WaylandAdapterSettings.PROFILE_SMOOTH)),
                )
            }
        }

        val renderer = effective(shortcut, "renderer") { it.renderer }.lowercase()
        if (renderer.isNotBlank() && renderer != "vulkan") t["vulkan"] = Tweak(
            "vulkan", "Use the Vulkan renderer", "The Vulkan path is the best supported one for most games.",
        ) { it.putExtra("renderer", "vulkan") }

        val emulator = effective(shortcut, "emulator") { it.getEmulator() }.lowercase()
        if (emulator.isBlank() || emulator.contains("fex")) {
            val preset = effective(shortcut, "fexcorePreset") { it.getFEXCorePreset() }
            if (preset != FEXCorePreset.STABILITY) t["fex_stability"] = Tweak(
                "fex_stability", "Use the Stability FEX preset", "Slower, but the safest CPU translation mode.",
            ) { it.putExtra("fexcorePreset", FEXCorePreset.STABILITY) }
            if (preset != FEXCorePreset.COMPATIBILITY) t["fex_compat"] = Tweak(
                "fex_compat", "Use the Compatibility FEX preset", "Favours getting the game to run over speed.",
            ) { it.putExtra("fexcorePreset", FEXCorePreset.COMPATIBILITY) }
            if (preset != FEXCorePreset.DENUVO) t["fex_denuvo"] = Tweak(
                "fex_denuvo", "Use the Denuvo FEX preset", "Tuned for Denuvo-protected games.",
            ) { it.putExtra("fexcorePreset", FEXCorePreset.DENUVO) }
        }

        val dxCfg = effective(shortcut, "dxwrapperConfig") { it.getDXWrapperConfig() }
        Regex("(^|,)async=([01])").find(dxCfg)?.let { m ->
            val on = m.groupValues[2] == "1"
            t["async"] = Tweak(
                "async",
                if (on) "Turn DXVK async off" else "Turn DXVK async on",
                if (on) "Fixes missing or flickering objects at the cost of shader stutter."
                else "Compiles shaders in the background for fewer hitches.",
            ) { it.putExtra("dxwrapperConfig", dxCfg.replace(Regex("(^|,)async=[01]"), "$1async=${if (on) 0 else 1}")) }
        }

        // Only offered when that DXVK is already installed: a version we can't launch is worse than the current one.
        if (!GPUInformation.isAdrenoGPU(context)) {
            val resolved = InstalledComponents.read(context).resolve("DXVK", "1.10.3", null)
            if (resolved is InstalledComponents.Resolution.Match) {
                val version = resolved.token ?: "1.10.3"
                val current = dxvkVersionOf(dxCfg)
                if (current != version && dxCfg.contains("version=")) t["dxvk_mali"] = Tweak(
                    "dxvk_mali", "Use DXVK $version", "The build Mali GPUs handle best.",
                ) { it.putExtra("dxwrapperConfig", dxCfg.replace(Regex("(^|,)version=[^,]*"), "$1version=$version")) }
            }
        }
        return t
    }

    // ── Shared configs ────────────────────────────────────────────────────────────────────────

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun picks(context: Context, shortcut: Shortcut, library: List<ConfigLibrary.Game>?): List<Pick> {
        if (library.isNullOrEmpty()) return emptyList()
        val wanted = norm(shortcut.name)
        if (wanted.length < 3) return emptyList()
        val game = library.firstOrNull { norm(it.name) == wanted }
            ?: library.firstOrNull {
                val n = norm(it.name)
                n.length >= 6 && wanted.length >= 6 && (n.contains(wanted) || wanted.contains(n))
            }
            ?: return emptyList()
        val soc = DeviceIdentity.soc()
        val gpu = DeviceIdentity.gpu(context)
        val ranked = game.configs.map { e ->
            Pick(game, e, GameMatcher.hardwareMatchesUser(soc, gpu, listOf(e.device, e.soc)))
        }.sortedByDescending { it.matchesDevice }
        val matching = ranked.filter { it.matchesDevice }.take(3)
        return matching.ifEmpty { ranked.take(2) }
    }

    // ── Undo ──────────────────────────────────────────────────────────────────────────────────

    private fun snapshotFile(context: Context, shortcut: Shortcut): File =
        File(File(context.filesDir, "doctor_undo"), shortcut.file.absolutePath.hashCode().toUInt().toString(16) + ".desktop")

    /** Copy the shortcut's file aside so [restore] can put it back. Call before the first change. */
    fun snapshot(context: Context, shortcut: Shortcut): Boolean = try {
        val target = snapshotFile(context.applicationContext, shortcut)
        target.parentFile?.mkdirs()
        shortcut.file.copyTo(target, overwrite = true)
        true
    } catch (e: IOException) {
        Log.w(TAG, "snapshot failed", e)
        false
    }

    /** Put the snapshot back. The in-memory [shortcut] is stale afterwards: reload the games list. */
    fun restore(context: Context, shortcut: Shortcut): Boolean = try {
        val source = snapshotFile(context.applicationContext, shortcut)
        if (!source.isFile) false else {
            source.copyTo(shortcut.file, overwrite = true)
            true
        }
    } catch (e: IOException) {
        Log.w(TAG, "restore failed", e)
        false
    }
}
