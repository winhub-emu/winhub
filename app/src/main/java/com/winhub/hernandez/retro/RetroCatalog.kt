package com.winhub.hernandez.retro

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "RetroCatalog"

/** How a ROM is handed to one standalone emulator. */
internal data class RetroLaunch(
    val activity: String,
    val action: String,
    /** The ROM path goes in this String extra; null means it travels as the intent's data URI. */
    val romExtraKey: String?,
    val mimeType: String?,
    /** The data URI is a FileProvider one with a temporary read grant instead of the raw file URI. */
    val contentUri: Boolean,
)

internal data class RetroEmulator(val packageName: String, val name: String, val launch: RetroLaunch?)

internal data class RetroSystem(
    val id: String,
    val name: String,
    val extensions: Set<String>,
    val folderNames: List<String>,
    val retroArchCore: String?,
    /** Emulator packages in the order the first installed one wins. */
    val emulators: List<String>,
    val defaultEmulator: String?,
    val label: String?,
    val year: Int,
    val brand: String?,
    val kind: String?,
    val thumbnails: String?,
    val esdeDirs: List<String>,
) {
    /** The short mark on a system's tile: its own label, else its initials. */
    val badge: String get() = label ?: name.split(' ', '/', '-').filter { it.isNotBlank() }.take(3).joinToString("") { it.take(1).uppercase() }
}

/** Every system and emulator the retro library knows, read once from the bundled catalog. */
internal class RetroCatalog(val systems: List<RetroSystem>, val emulators: List<RetroEmulator>) {
    val byId: Map<String, RetroSystem> = systems.associateBy { it.id }
    val emulatorByPackage: Map<String, RetroEmulator> = emulators.associateBy { it.packageName }

    // The first system to claim a folder name keeps it, so a later one can't re-home its games.
    private val byFolder: Map<String, RetroSystem> = buildMap {
        systems.forEach { s -> s.folderNames.forEach { putIfAbsent(it.lowercase(), s) } }
    }

    // Loose files outside any system folder are claimed by extension, except the ones that are mostly not games.
    private val byExtension: Map<String, RetroSystem> = buildMap {
        systems.forEach { s -> s.extensions.forEach { putIfAbsent(it, s) } }
    }

    /** The system [file] belongs to: its own folder (or the nearest parent) names it, else an unambiguous extension does. */
    fun detect(file: File): RetroSystem? {
        val ext = ".${file.extension.lowercase()}"
        var dir: File? = file.parentFile
        var folderMatch: RetroSystem? = null
        while (dir != null && folderMatch == null) {
            folderMatch = byFolder[dir.name.lowercase()]
            dir = dir.parentFile
        }
        if (folderMatch != null) {
            // A stray data file inside a system folder has an extension the system doesn't use. Archives are always taken
            // there: the folder names the system and the cores read zipped ROMs.
            val valid = ext in ARCHIVE_EXTENSIONS || ext in folderMatch.extensions
            return folderMatch.takeIf { valid }
        }
        if (ext in AMBIGUOUS_EXTENSIONS) return null
        return byExtension[ext]
    }

    companion object {
        // Extensions shared with saves, disc tracks, artwork and system files: never trusted outside a system folder.
        private val AMBIGUOUS_EXTENSIONS = setOf(".bin", ".iso", ".img", ".chd", ".cso", ".cue", ".zip", ".7z", ".m3u", ".png")
        private val ARCHIVE_EXTENSIONS = setOf(".zip", ".7z")

        // The PC and Android entries of the bundled list belong to the other library.
        private val NOT_RETRO = setOf("steam", "android")

        @Volatile private var cached: RetroCatalog? = null

        fun get(context: Context): RetroCatalog =
            cached ?: synchronized(this) { cached ?: load(context.applicationContext).also { cached = it } }

        private fun load(context: Context): RetroCatalog {
            val root = try {
                context.assets.open("retro/systems.json").use { JSONObject(it.bufferedReader().readText()) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not read the bundled system list", e)
                return RetroCatalog(emptyList(), emptyList())
            }
            val systems = ArrayList<RetroSystem>()
            root.optJSONArray("platforms")?.forEachObject { p ->
                val id = p.optString("id")
                if (id.isEmpty() || id in NOT_RETRO) return@forEachObject
                systems += RetroSystem(
                    id = id,
                    name = p.optString("displayName", id),
                    extensions = p.stringList("extensions").map { it.lowercase() }.toSet(),
                    folderNames = p.stringList("folderNames"),
                    retroArchCore = p.optString("retroArchCore").ifEmpty { null },
                    emulators = p.stringList("emulators"),
                    defaultEmulator = p.optString("defaultEmulator").ifEmpty { null },
                    label = p.optString("label").ifEmpty { null },
                    year = p.optInt("releaseYear", 0),
                    brand = p.optString("brand").ifEmpty { null },
                    kind = p.optString("kind").ifEmpty { null },
                    thumbnails = p.optString("libretroThumbnails").ifEmpty { null },
                    esdeDirs = p.stringList("esdeDirs"),
                )
            }
            val emulators = ArrayList<RetroEmulator>()
            root.optJSONArray("emulators")?.forEachObject { e ->
                val pkg = e.optString("packageName")
                if (pkg.isEmpty()) return@forEachObject
                val launch = e.optJSONObject("launch")?.let { l ->
                    val activity = l.optString("activity")
                    if (activity.isEmpty()) null else RetroLaunch(
                        activity = activity,
                        action = if (l.optString("action") == "MAIN") "android.intent.action.MAIN" else "android.intent.action.VIEW",
                        romExtraKey = l.optString("romExtraKey").ifEmpty { null },
                        mimeType = l.optString("mimeType").ifEmpty { null },
                        contentUri = l.optString("romUri") == "CONTENT",
                    )
                }
                emulators += RetroEmulator(pkg, e.optString("displayName", pkg), launch)
            }
            return RetroCatalog(systems, emulators)
        }
    }
}

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(block)
}

private fun JSONObject.stringList(key: String): List<String> {
    val a = optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotEmpty() } }
}
