package com.winhub.hernandez.store.xbox

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Installed Xbox games: one record per product in files/xbox/installs.json, written only when a download
 * completed (an interrupted or cancelled download never becomes an installed game).
 */
object XboxInstallState {
    data class Installed(
        val id: String,
        val title: String,
        /** The game's install folder (the MSIXVC package root, with MicrosoftGame.config). */
        val folder: String,
        /** The launch executable, relative to [folder], from MicrosoftGame.config. */
        val exe: String,
        val version: String,
        val contentId: String,
        val market: String,
        val access: String,
        val posterUrl: String?,
        val heroUrl: String?,
        val installedAt: Long,
    ) {
        fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("folder", folder).put("exe", exe)
            .put("version", version).put("contentId", contentId).put("market", market).put("access", access)
            .put("poster", posterUrl).put("hero", heroUrl).put("installedAt", installedAt)

        companion object {
            fun fromJson(o: JSONObject) = Installed(o.getString("id"), o.getString("title"), o.getString("folder"), o.getString("exe"),
                o.optString("version"), o.optString("contentId"), o.optString("market", "US"), o.optString("access"),
                o.optString("poster").takeIf { it.isNotEmpty() }, o.optString("hero").takeIf { it.isNotEmpty() }, o.optLong("installedAt"))
        }
    }

    private val lock = Any()
    private fun file(app: Context) = File(app.filesDir, "xbox/installs.json").apply { parentFile?.mkdirs() }

    fun all(app: Context): List<Installed> = synchronized(lock) {
        val f = file(app)
        if (!f.isFile) return emptyList()
        runCatching {
            val json = JSONObject(f.readText())
            json.keys().asSequence().mapNotNull { runCatching { Installed.fromJson(json.getJSONObject(it)) }.getOrNull() }.toList()
        }.getOrDefault(emptyList())
    }

    fun get(app: Context, id: String): Installed? = all(app).firstOrNull { it.id == id }

    fun isInstalled(app: Context, id: String): Boolean = get(app, id)?.let { File(it.folder, it.exe).isFile } == true

    fun put(app: Context, installed: Installed) = synchronized(lock) {
        val f = file(app)
        val json = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        json.put(installed.id, installed.toJson())
        val tmp = File(f.path + ".tmp"); tmp.writeText(json.toString()); check(tmp.renameTo(f))
    }

    fun remove(app: Context, id: String) = synchronized(lock) {
        val f = file(app)
        val json = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        json.remove(id)
        val tmp = File(f.path + ".tmp"); tmp.writeText(json.toString()); check(tmp.renameTo(f))
    }
}
