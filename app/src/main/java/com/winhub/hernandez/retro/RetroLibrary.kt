package com.winhub.hernandez.retro

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.winhub.hernandez.store.StarLaunchBridge
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

private const val TAG = "RetroLibrary"

internal data class RetroGame(val title: String, val path: String, val systemId: String) {
    /** The file's own name without its extension: what the art servers and the media folders are keyed by. */
    val baseName: String get() = File(path).nameWithoutExtension
}

/** What the user wrote about a game: shown on the retro library instead of what the scan made of the file name. Blank fields mean "nothing". */
internal data class RetroMeta(
    val name: String = "",
    val description: String = "",
    val developer: String = "",
    val publisher: String = "",
    val genre: String = "",
    val year: String = "",
    val players: String = "",
) {
    /** Genre, developer, publisher, year and players that are filled in, joined for one line. */
    val line: String
        get() = listOf(genre, developer, publisher, year, players).filter { it.isNotBlank() }.joinToString(" \u00b7 ")
}

/** What a scan of the ROM folder found, grouped by system. */
internal class RetroScan(val bySystem: Map<String, List<RetroGame>>) {
    val total: Int get() = bySystem.values.sumOf { it.size }
}

internal sealed interface RetroLaunchResult {
    data object Started : RetroLaunchResult
    data object NoEmulator : RetroLaunchResult
    data class Failed(val reason: String) : RetroLaunchResult
}

/** The retro library's saved choices (ROM folder, emulator per system), the ROM scan and the launch of a game. */
internal object RetroLibrary {
    private const val PREFS = "retro_prefs"
    private const val KEY_ROOT = "rom_root"
    private const val KEY_EMU = "emu_"
    private const val KEY_EXCLUDED = "excluded"
    private const val KEY_ENABLED = "enabled_systems"
    private const val KEY_PLAYED = "lp:"

    val RETROARCH_PACKAGES = listOf("com.retroarch.aarch64", "org.libretro.retroarch")

    // Emulator data and OS folders that hold no ROMs; skipping them keeps saves and dumps out of the library.
    private val SKIP_FOLDERS = setOf(
        "savedata", "save", "saves", "savestates", "states", "savefiles",
        "sdmc", "nand", "shaders", "cache", "log", "logs", "dump", "dumps",
        "screenshots", "cheats", "textures", "texture_cache", "system",
        "memcards", "memory cards", "bios", "firmware", "firmwares", "tmp", "temp", "config", "configs",
        "os0", "vs0", "ur0", "tm0", "ud0", "pd0", "sa0", "gro0", "grw0",
        "license", "appmeta", "ppsspp_state", "private", "media", "downloaded_media", "gamelists",
    )
    private val SKIP_EXTENSIONS = setOf(
        ".txt", ".xml", ".nfo", ".jpg", ".jpeg", ".png", ".mp4", ".rar", ".sav", ".srm", ".state", ".dat", ".db", ".json", ".cfg", ".ini",
    )
    private val ROOT_NAMES = listOf("ROMs", "Roms", "roms", "ROMS", "Games", "games", "RetroArch/roms", "retroarch/roms", "Emulation/roms")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun romRoot(context: Context): String? = prefs(context).getString(KEY_ROOT, null)?.takeIf { it.isNotBlank() }

    fun setRomRoot(context: Context, path: String) {
        prefs(context).edit().putString(KEY_ROOT, path).apply()
    }

    fun emulatorChoice(context: Context, systemId: String): String? = prefs(context).getString(KEY_EMU + systemId, null)

    fun setEmulatorChoice(context: Context, systemId: String, packageName: String?) {
        prefs(context).edit().apply { if (packageName == null) remove(KEY_EMU + systemId) else putString(KEY_EMU + systemId, packageName) }.apply()
    }

    /** The consoles the user picked (Settings, first-run setup): only their games are shown. Empty until picked. */
    fun enabledSystems(context: Context): Set<String> = prefs(context).getStringSet(KEY_ENABLED, emptySet()).orEmpty().toSet()

    fun setEnabledSystems(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ENABLED, ids).apply()
    }

    fun markPlayed(context: Context, path: String) {
        prefs(context).edit().putLong(KEY_PLAYED + path, System.currentTimeMillis()).apply()
    }

    /** When each game was last started, by ROM path. */
    fun lastPlayed(context: Context): Map<String, Long> =
        prefs(context).all.mapNotNull { (k, v) -> if (k.startsWith(KEY_PLAYED) && v is Long) k.removePrefix(KEY_PLAYED) to v else null }.toMap()

    private fun metaPrefs(context: Context) = context.getSharedPreferences("retro_meta", Context.MODE_PRIVATE)

    /** The details the user edited, by ROM path. */
    fun allMeta(context: Context): Map<String, RetroMeta> =
        metaPrefs(context).all.mapNotNull { (path, json) -> (json as? String)?.let(::parseMeta)?.let { path to it } }.toMap()

    /** The details the user edited for one game, or null. */
    fun meta(context: Context, path: String): RetroMeta? = metaPrefs(context).getString(path, null)?.let(::parseMeta)

    /** Saves [meta] for [path]; an empty one forgets the edits. */
    fun setMeta(context: Context, path: String, meta: RetroMeta) {
        val edit = metaPrefs(context).edit()
        if (meta == RetroMeta()) {
            edit.remove(path)
        } else {
            val json = JSONObject()
                .put("name", meta.name).put("description", meta.description).put("developer", meta.developer)
                .put("publisher", meta.publisher).put("genre", meta.genre).put("year", meta.year).put("players", meta.players)
            edit.putString(path, json.toString())
        }
        edit.apply()
    }

    private fun parseMeta(json: String): RetroMeta? = try {
        val o = JSONObject(json)
        RetroMeta(
            o.optString("name"), o.optString("description"), o.optString("developer"), o.optString("publisher"),
            o.optString("genre"), o.optString("year"), o.optString("players"),
        )
    } catch (e: JSONException) {
        Log.w(TAG, "Unreadable game details", e)
        null
    }

    /** ROM paths the user removed from the library: a scan never brings them back (the files themselves are untouched). */
    fun excludedPaths(context: Context): Set<String> = prefs(context).getStringSet(KEY_EXCLUDED, emptySet()).orEmpty().toSet()

    fun excludePath(context: Context, path: String) {
        prefs(context).edit().putStringSet(KEY_EXCLUDED, excludedPaths(context) + path).apply()
    }

    fun clearExcluded(context: Context) {
        prefs(context).edit().remove(KEY_EXCLUDED).apply()
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** The emulators that can run [system], best first. A system with none of its own is left to RetroArch. */
    fun emulatorsFor(context: Context, system: RetroSystem): List<RetroEmulator> {
        val catalog = RetroCatalog.get(context)
        val packages = system.emulators.ifEmpty { RETROARCH_PACKAGES }
        return packages.map { catalog.emulatorByPackage[it] ?: RetroEmulator(it, it, null) }
    }

    /** The emulator a game of [system] starts in: the saved choice while it is installed, else the first installed one. */
    fun resolveEmulator(context: Context, system: RetroSystem): RetroEmulator? {
        val all = emulatorsFor(context, system)
        val chosen = emulatorChoice(context, system.id)?.let { c -> all.firstOrNull { it.packageName == c } }
        if (chosen != null && isInstalled(context, chosen.packageName)) return chosen
        return all.firstOrNull { isInstalled(context, it.packageName) }
    }

    // ── ROM folder ─────────────────────────────────────────────────────────────────────────────

    /** Internal storage plus any SD card or USB volume, as the folders to look for a ROM folder in. */
    private fun storageRoots(context: Context): List<File> {
        val roots = LinkedHashSet<File>()
        roots += Environment.getExternalStorageDirectory()
        context.getExternalFilesDirs(null).orEmpty().forEach { dir ->
            val path = dir?.absolutePath ?: return@forEach
            val cut = path.indexOf("/Android/")
            if (cut > 0) roots += File(path.substring(0, cut))
        }
        return roots.toList()
    }

    /** The usual ROM folder holding the most recognisable games, or null when nothing convincing is there. Run off the main thread. */
    fun detectRoot(context: Context): String? {
        val catalog = RetroCatalog.get(context)
        val candidates = storageRoots(context)
            .flatMap { root -> ROOT_NAMES.map { File(root, it) } }
            .filter { it.isDirectory }
            // FAT / exFAT cards are case-insensitive, so "ROMs" and "roms" can be one folder.
            .distinctBy { it.canonicalPath.lowercase() }
        return candidates
            .map { it to countGames(catalog, it, DETECT_CAP) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first?.absolutePath
    }

    private const val DETECT_CAP = 300

    private fun countGames(catalog: RetroCatalog, dir: File, cap: Int): Int {
        var n = 0
        for (f in walk(dir).maxDepth(3)) {
            if (isRomFile(f) && catalog.detect(f) != null && ++n >= cap) break
        }
        return n
    }

    private fun walk(dir: File) = dir.walkTopDown().onEnter { !it.name.startsWith(".") && it.name.lowercase() !in SKIP_FOLDERS || it == dir }

    private fun isRomFile(f: File) = f.isFile && !f.name.startsWith(".") && ".${f.extension.lowercase()}" !in SKIP_EXTENSIONS

    // ── Scan ───────────────────────────────────────────────────────────────────────────────────

    /** Every game under [rootPath], grouped by system, or null when the folder is not there (an unmounted card must not look empty). Run off the main thread. */
    fun scan(context: Context, rootPath: String): RetroScan? {
        val root = File(rootPath)
        if (!root.isDirectory) return null
        val catalog = RetroCatalog.get(context)
        val excluded = excludedPaths(context)
        val files = walk(root).filter { isRomFile(it) && it.absolutePath !in excluded }.toList()

        // A cue sheet or playlist stands for the tracks and discs it lists, which are not games of their own.
        val referenced = HashSet<String>()
        files.forEach { f ->
            when (f.extension.lowercase()) {
                "cue" -> cueTargets(f).forEach { referenced += normalized(it) }
                "m3u" -> m3uTargets(f).forEach { referenced += normalized(it) }
            }
        }

        val arcade by lazy { ArcadeNames.table(context) }
        val grouped = HashMap<String, MutableList<RetroGame>>()
        for (f in files) {
            if (normalized(f) in referenced) continue
            val system = catalog.detect(f) ?: continue
            val title = (if (system.id in ARCADE_SYSTEMS) arcade[f.nameWithoutExtension.lowercase()] else null) ?: cleanTitle(f)
            grouped.getOrPut(system.id) { ArrayList() } += RetroGame(title, f.absolutePath, system.id)
        }
        return RetroScan(grouped.mapValues { (_, games) -> games.sortedBy { it.title.lowercase() } })
    }

    private val ARCADE_SYSTEMS = setOf("mame", "fbneo", "neogeo", "cps1", "cps2", "cps3")

    private fun cleanTitle(f: File): String =
        f.nameWithoutExtension.replace(Regex("\\(.*?\\)"), "").replace(Regex("\\[.*?]"), "").trim().ifEmpty { f.nameWithoutExtension }

    private fun normalized(f: File): String = try {
        f.canonicalPath
    } catch (e: IOException) {
        f.absolutePath
    }

    private fun cueTargets(cue: File): List<File> {
        val dir = cue.parentFile ?: return emptyList()
        return try {
            cue.readLines().mapNotNull { line ->
                val t = line.trim()
                if (!t.startsWith("FILE", ignoreCase = true)) return@mapNotNull null
                val open = t.indexOf('"')
                val name = if (open >= 0) {
                    val close = t.indexOf('"', open + 1)
                    if (close > open) t.substring(open + 1, close) else t.substring(open + 1)
                } else {
                    t.split(Regex("\\s+")).let { p -> if (p.size >= 3) p.subList(1, p.size - 1).joinToString(" ") else p.getOrNull(1).orEmpty() }
                }
                name.takeIf { it.isNotEmpty() }?.let { File(dir, it) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read cue sheet ${cue.path}", e)
            emptyList()
        }
    }

    private fun m3uTargets(m3u: File): List<File> {
        val dir = m3u.parentFile ?: return emptyList()
        return try {
            m3u.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { File(dir, it) }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read playlist ${m3u.path}", e)
            emptyList()
        }
    }

    // ── Launch ─────────────────────────────────────────────────────────────────────────────────

    fun launch(context: Context, system: RetroSystem, game: RetroGame): RetroLaunchResult {
        val emulator = resolveEmulator(context, system) ?: return RetroLaunchResult.NoEmulator
        if (!File(game.path).isFile) return RetroLaunchResult.Failed("The ROM file is gone")
        val result = if (emulator.packageName in RETROARCH_PACKAGES) {
            launchRetroArch(context, emulator.packageName, system, game)
        } else {
            launchStandalone(context, emulator, game)
        }
        return result.fold(
            onSuccess = { markPlayed(context, game.path); RetroLaunchResult.Started },
            onFailure = { e ->
                Log.w(TAG, "Launch of ${game.path} in ${emulator.packageName} failed", e)
                RetroLaunchResult.Failed(e.message ?: "Could not start ${emulator.name}")
            },
        )
    }

    private fun launchRetroArch(context: Context, pkg: String, system: RetroSystem, game: RetroGame): Result<Unit> {
        // RetroArch's content activity boots a game directly; its default launch intent would only open the menu. The
        // Android build of a core carries an "_android" suffix.
        val corePath = system.retroArchCore?.let { name ->
            val androidName = if (name.endsWith("_android.so")) name else name.removeSuffix(".so") + "_android.so"
            "/data/user/0/$pkg/cores/$androidName"
        }
        val intent = Intent().apply {
            setClassName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", game.path)
            corePath?.let { putExtra("LIBRETRO", it) }
            putExtra("CONFIGFILE", "${Environment.getExternalStorageDirectory().absolutePath}/Android/data/$pkg/files/retroarch.cfg")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Better the menu than nothing if the content activity can't start.
        return start(context, intent).recoverCatching { first ->
            val menu = context.packageManager.getLaunchIntentForPackage(pkg) ?: throw first
            menu.putExtra("ROM", game.path)
            corePath?.let { menu.putExtra("LIBRETRO", it) }
            menu.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(menu)
        }
    }

    private fun launchStandalone(context: Context, emulator: RetroEmulator, game: RetroGame): Result<Unit> {
        val pkg = emulator.packageName
        val file = File(game.path)
        val spec = emulator.launch
        val openApp = { cause: Throwable ->
            val app = context.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: throw cause
            context.startActivity(app)
        }
        if (spec != null) {
            // Built inside the runCatching so a ROM the FileProvider can't describe falls back like a failed launch.
            return runCatching {
                val intent = Intent(spec.action).apply {
                    setClassName(pkg, spec.activity)
                    if (spec.romExtraKey != null) putExtra(spec.romExtraKey, game.path)
                    else setDataAndType(romUri(context, file, spec.contentUri), spec.mimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    if (data?.scheme == "content") addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
                .recoverCatching { context.startActivity(viewIntent(context, pkg, file, spec.contentUri)) }
                .recoverCatching(openApp)
        }
        // An emulator without a verified recipe: a package-targeted VIEW, else its own game list.
        return start(context, viewIntent(context, pkg, file, false)).recoverCatching(openApp)
    }

    private fun viewIntent(context: Context, pkg: String, file: File, content: Boolean): Intent =
        Intent(Intent.ACTION_VIEW, romUri(context, file, content)).apply {
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (data?.scheme == "content") addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** A FileProvider URI lets an emulator read the ROM across app sandboxes; a card the provider doesn't cover gets the file URI. */
    private fun romUri(context: Context, file: File, content: Boolean): Uri {
        if (!content) return Uri.fromFile(file)
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.tileprovider", file)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No content URI for ${file.path}, using the file URI", e)
            Uri.fromFile(file)
        }
    }

    private fun start(context: Context, intent: Intent): Result<Unit> = runCatching { context.startActivity(intent) }
}

/** Arcade ROMs are named after their MAME / FinalBurn romset id ("afighter"); this maps the id to the game's title. */
private object ArcadeNames {
    @Volatile private var names: Map<String, String>? = null

    fun table(context: Context): Map<String, String> =
        names ?: synchronized(this) { names ?: load(context).also { names = it } }

    private fun load(context: Context): Map<String, String> {
        val map = HashMap<String, String>(20_000)
        try {
            context.assets.open("retro/arcade_names.tsv.gz").use { raw ->
                GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0 && tab < line.length - 1) map[line.substring(0, tab)] = line.substring(tab + 1)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the arcade title table", e)
        }
        return map
    }
}

/**
 * Pictures of a game, kept on the device: its grid (the cover) and its hero (the background), both from SteamGridDB, with the free
 * libretro thumbnail server as a second source for the grid. A cover already on the device (beside the ROM, or in an ES-DE style
 * media folder) is used as it is.
 */
internal object RetroArt {
    private val IMAGE_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")
    private val MEDIA_DIRS = listOf("covers", "box2dfront", "boxart", "images", "box")

    private fun safeName(name: String) = name.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")

    private fun artDir(context: Context, game: RetroGame) = File(context.filesDir, "retro_art/${game.systemId}")

    private fun cacheFile(context: Context, game: RetroGame) = File(artDir(context, game), "${safeName(game.baseName)}.png")

    private fun heroFile(context: Context, game: RetroGame) = File(artDir(context, game), "${safeName(game.baseName)}.bg.jpg")

    private fun wideFile(context: Context, game: RetroGame) = File(artDir(context, game), "${safeName(game.baseName)}.wide.jpg")

    // A zero-length file saying "asked, and there is nothing": "none" for the grid, "nohero" for the hero, "nowide" for the banner.
    private fun marker(context: Context, game: RetroGame, kind: String) = File(artDir(context, game), "${safeName(game.baseName)}.$kind")

    /** The cover file for [game]: one fetched earlier, a picture named like the ROM beside it, or one in a media folder. Run off the main thread. */
    fun find(context: Context, system: RetroSystem, game: RetroGame, root: String?): File? {
        val cached = cacheFile(context, game)
        if (cached.isFile && cached.length() > 0) return cached
        val rom = File(game.path)
        val dir = rom.parentFile ?: return null
        val dirs = ArrayList<File>()
        dirs += dir
        MEDIA_DIRS.forEach { dirs += File(dir, "media/$it") }
        if (root != null) {
            (system.esdeDirs + system.id).distinct().forEach { d -> dirs += File(root, "downloaded_media/$d/covers") }
        }
        for (d in dirs) for (ext in IMAGE_EXTENSIONS) {
            val f = File(d, "${game.baseName}.$ext")
            if (f.isFile) return f
        }
        return null
    }

    /** The banner (landscape grid) of [game] fetched earlier, or null. Run off the main thread. */
    fun findWide(context: Context, game: RetroGame): File? = wideFile(context, game).takeIf { it.isFile && it.length() > 0 }

    /** The background picture of [game] fetched earlier, or null. Run off the main thread. */
    fun findHero(context: Context, game: RetroGame): File? = heroFile(context, game).takeIf { it.isFile && it.length() > 0 }

    /**
     * Fetches what [games] still lack, in order: the game is looked up on SteamGridDB by its title, then its grid and its hero are saved
     * (the grid from the libretro thumbnail server when SteamGridDB has none). Returns how many grids were saved. [onProgress] gets
     * (done, total); a [stopped] that turns true ends the run; [onSaved] runs after each picture. A game with nothing to find is
     * remembered so it isn't asked for again, and a network failure is not remembered. Run off the main thread.
     */
    fun download(
        context: Context,
        games: List<RetroGame>,
        root: String?,
        stopped: () -> Boolean,
        onProgress: (Int, Int) -> Unit,
        onSaved: () -> Unit = {},
        wide: Set<String> = emptySet(),
    ): Int {
        val catalog = RetroCatalog.get(context)
        var saved = 0
        games.forEachIndexed { i, game ->
            if (stopped()) return saved
            onProgress(i, games.size)
            val system = catalog.byId[game.systemId] ?: return@forEachIndexed
            val needGrid = find(context, system, game, root) == null && !marker(context, game, "none").exists()
            val needHero = findHero(context, game) == null && !marker(context, game, "nohero").exists()
            // The banner is only for the games that can be the first card: those in [wide].
            val needWide = game.path in wide && findWide(context, game) == null && !marker(context, game, "nowide").exists()
            if (!needGrid && !needHero && !needWide) return@forEachIndexed
            val id = searchGameId(context, game.title)
            if (id < 0) return@forEachIndexed

            if (needGrid) {
                var grid = Fetch.MISSING
                if (id > 0) firstImageUrl(context, "grids/game/$id?dimensions=600x900,342x482,660x930&types=static&nsfw=false&mimes=image/jpeg,image/png&limit=1")
                    ?.let { grid = fetch(it, cacheFile(context, game)) }
                val folder = system.thumbnails
                if (grid == Fetch.MISSING && folder != null) {
                    val url = "https://thumbnails.libretro.com/${Uri.encode(folder)}/Named_Boxarts/${Uri.encode(safeName(game.baseName))}.png"
                    grid = fetch(url, cacheFile(context, game))
                }
                when (grid) {
                    Fetch.SAVED -> { saved++; onSaved() }
                    Fetch.MISSING -> touch(marker(context, game, "none"))
                    Fetch.FAILED -> Unit
                }
            }
            if (needHero) {
                var hero = Fetch.MISSING
                if (id > 0) firstImageUrl(context, "heroes/game/$id?types=static&nsfw=false&mimes=image/jpeg,image/png&limit=1")
                    ?.let { hero = fetch(it, heroFile(context, game)) }
                when (hero) {
                    Fetch.SAVED -> onSaved()
                    Fetch.MISSING -> touch(marker(context, game, "nohero"))
                    Fetch.FAILED -> Unit
                }
            }
            if (needWide) {
                var banner = Fetch.MISSING
                if (id > 0) firstImageUrl(context, "grids/game/$id?dimensions=460x215,920x430&types=static&nsfw=false&mimes=image/jpeg,image/png&limit=1")
                    ?.let { banner = fetch(it, wideFile(context, game)) }
                when (banner) {
                    Fetch.SAVED -> onSaved()
                    Fetch.MISSING -> touch(marker(context, game, "nowide"))
                    Fetch.FAILED -> Unit
                }
            }
        }
        onProgress(games.size, games.size)
        return saved
    }

    /** Forgets that [game] had nothing to find, so the next [download] asks for its pictures again. */
    fun forgetMisses(context: Context, game: RetroGame) {
        listOf("none", "nohero", "nowide").forEach { marker(context, game, it).delete() }
    }

    /** Forgets which games had nothing to find, so the next [download] asks for them again. */
    fun clearMisses(context: Context) {
        File(context.filesDir, "retro_art").listFiles()?.forEach { dir ->
            dir.listFiles { f -> f.name.endsWith(".none") || f.name.endsWith(".nohero") || f.name.endsWith(".nowide") }?.forEach { it.delete() }
        }
    }

    private fun touch(f: File) {
        try {
            f.parentFile?.mkdirs()
            f.createNewFile()
        } catch (e: IOException) {
            Log.w(TAG, "Could not write ${f.path}", e)
        }
    }

    /** SteamGridDB's id for the game that best matches [title]: above 0 when found, 0 when there is none, -1 when the lookup failed. */
    private fun searchGameId(context: Context, title: String): Int {
        val body = StarLaunchBridge.sgdbGet(context, "search/autocomplete/" + Uri.encode(title)) ?: return -1
        return try {
            JSONObject(body).optJSONArray("data")?.optJSONObject(0)?.optInt("id", 0) ?: 0
        } catch (e: JSONException) {
            Log.w(TAG, "Unreadable SteamGridDB search answer", e)
            -1
        }
    }

    private fun firstImageUrl(context: Context, path: String): String? {
        val body = StarLaunchBridge.sgdbGet(context, path) ?: return null
        return try {
            JSONObject(body).optJSONArray("data")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }
        } catch (e: JSONException) {
            Log.w(TAG, "Unreadable SteamGridDB image answer", e)
            null
        }
    }

    private enum class Fetch { SAVED, MISSING, FAILED }

    private fun fetch(url: String, target: File): Fetch {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            Log.w(TAG, "Bad picture address: $url", e)
            return Fetch.FAILED
        }
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        return try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    target.parentFile?.mkdirs()
                    val tmp = File(target.path + ".part")
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    if (tmp.renameTo(target)) Fetch.SAVED else { tmp.delete(); Fetch.FAILED }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> Fetch.MISSING
                else -> Fetch.FAILED
            }
        } catch (e: IOException) {
            Log.w(TAG, "Picture fetch failed: $url", e)
            Fetch.FAILED
        } finally {
            conn.disconnect()
        }
    }
}
