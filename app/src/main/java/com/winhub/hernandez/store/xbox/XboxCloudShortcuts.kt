package com.winhub.hernandez.store.xbox

import android.app.Activity
import android.content.Context
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.store.StarLaunchBridge
import org.json.JSONObject
import java.io.File

/**
 * Xbox games in the Games tab before they are installed (Xbox shortcuts only). Such an entry is an ordinary
 * Xbox shortcut marked [NOT_INSTALLED], parked in the first container until the user downloads the game: it
 * streams at once, and Download asks for a container, installs, then moves the shortcut there and points it at
 * the game's .exe ([onInstalled]). Settings and long-press options that need the installed copy stay greyed out.
 */
object XboxCloudShortcuts {
    const val NOT_INSTALLED = "xboxNotInstalled"
    private const val LINKS = "xbox_shortcut_links"

    fun isNotInstalled(shortcut: Shortcut): Boolean =
        shortcut.getExtra("storeSource") == "xbox" && shortcut.getExtra(NOT_INSTALLED) == "1"

    /** Adds [game] to Games without installing it; [done] gets a message for the user. */
    fun addNotInstalled(activity: Activity, game: XboxGame, done: (Boolean, String) -> Unit) {
        val holding = ContainerManager(activity).containers.firstOrNull()
            ?: return done(false, "Create a container first; downloaded games are installed into one.")
        // Never run: launches of a not-installed entry stream or download instead.
        val placeholder = File(activity.filesDir, "imagefs/xbox-cloud/${game.id}.exe").path
        StarLaunchBridge.writeShortcutAsync(activity, holding, game.title, placeholder, game.posterUrl ?: game.heroUrl,
            0, null, "xbox:${game.id}", false) { success, message ->
            if (!success) { done(false, message); return@writeShortcutAsync }
            val file = File(holding.desktopDir, safeName(game.title) + ".desktop")
            runCatching { Shortcut(holding, file).apply { putExtra(NOT_INSTALLED, "1"); saveData() } }
                .onSuccess { done(true, "${game.title} added to Games. Stream it now, or download it from its launch options.") }
                .onFailure { done(false, "The Games entry could not be saved.") }
        }
    }

    /** The user chose [containerId] for a not-installed shortcut's download; [onInstalled] finishes it. */
    fun rememberDownload(context: Context, productId: String, shortcutPath: String, fromContainer: Int, containerId: Int) {
        prefs(context).edit().putString(productId.uppercase(), JSONObject().put("shortcut", shortcutPath)
            .put("from", fromContainer).put("to", containerId).toString()).apply()
    }

    /** The cached library's copy of a product (title, art, PC package, cloud), or null before a library sync. */
    fun cachedGame(context: Context, productId: String): XboxGame? = runCatching {
        val app = context.applicationContext
        val account = XboxAccountStore.signedInAs(app) ?: return null
        XboxLibraryCache.read(app, account, Xodus.market())?.games?.firstOrNull { it.id == productId.uppercase() }
    }.getOrNull()

    /** Launch method Download on a not-installed shortcut: the game's store page, which asks for a container and installs. */
    fun openDownload(activity: Activity, shortcut: Shortcut) {
        val id = shortcut.getExtra("xboxProductId")
        Thread {
            val game = cachedGame(activity, id)
            activity.runOnUiThread {
                if (game == null) {
                    com.winhub.hernandez.core.AppUtils.showToast(activity, "Open the Xbox store once to sync your library, then try again.")
                    return@runOnUiThread
                }
                activity.startActivity(XboxMainActivity.detailIntent(activity, game)
                    .putExtra(EXTRA_SHORTCUT, shortcut.file.path).putExtra(EXTRA_SHORTCUT_CONTAINER, shortcut.container.id))
            }
        }.start()
    }

    const val EXTRA_SHORTCUT = "games_entry_shortcut"
    const val EXTRA_SHORTCUT_CONTAINER = "games_entry_container"

    /** Called when an Xbox game finished installing: completes the not-installed shortcut waiting for it, if any. */
    fun onInstalled(context: Context, productId: String, exePath: String) {
        val app = context.applicationContext
        val key = productId.uppercase()
        val link = prefs(app).getString(key, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        prefs(app).edit().remove(key).apply()
        runCatching {
            val manager = ContainerManager(app)
            val from = manager.getContainerById(link.getInt("from")) ?: error("holding container gone")
            val to = manager.getContainerById(link.getInt("to")) ?: error("chosen container gone")
            var file = File(link.getString("shortcut"))
            check(file.isFile) { "shortcut removed" }
            if (from.id != to.id) {
                check(Shortcut(from, file).cloneToContainer(to)) { "move failed" }
                file.delete()
                file = File(to.desktopDir, file.name)
            }
            // Point Exec at the installed .exe, the same mapping the store's Add to Games uses.
            val imageFsRoot = File(app.filesDir, "imagefs").absolutePath
            val exec = if (exePath.startsWith(imageFsRoot)) {
                "Z:\\\\\\\\" + exePath.substring(imageFsRoot.length).trimStart('/').replace("/", "\\\\\\\\")
            } else WinePath.escapeForExec(WinePath.resolveWindowsPath(to, exePath))
            FileUtils.writeString(file, FileUtils.readLines(file).joinToString("\n") { if (it.startsWith("Exec=")) "Exec=wine $exec" else it } + "\n")
            Shortcut(to, file).apply { putExtra(NOT_INSTALLED, null); saveData() }
            XboxDiag.log(app, "games entry $key installed into container ${to.id}")
        }.onFailure { XboxDiag.log(app, "games entry $key not completed: ${it.message}") }
    }

    private fun safeName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "game" }

    private fun prefs(context: Context) = context.getSharedPreferences(LINKS, Context.MODE_PRIVATE)
}
