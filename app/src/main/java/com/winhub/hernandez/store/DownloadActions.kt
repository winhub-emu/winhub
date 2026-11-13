package com.winhub.hernandez.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.store.compose.AddToShortcutsRequest
import com.winhub.hernandez.store.compose.DeckAddToShortcuts
import com.winhub.hernandez.store.download.DownloadEntry
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import java.io.File

/**
 * What a card of the cross-store Download Manager does, shared by [DownloadManagerActivity] and the XMB Home's Downloads page
 * (ui/deck/XmbDownloadsPage.kt): open a game's page in its store, launch (scan for exes, pick one, pick a container, write the
 * shortcut), uninstall, cancel. It holds the state of the dialogs those flows raise; call [Dialogs] once, next to the list, to
 * draw them. Lives in the `store` package (not `store.download`) so it can reach the package-private Java launch helpers, exactly
 * like the Steam Activities.
 *
 * [inlineAddResult]: a shortcut written without trouble is reported in the themed bar ([notice]) rather than in the "Added to
 * Shortcuts" dialog with its jump to the Shortcuts screen (the XMB Home shows the new game in its own row).
 */
internal class DownloadActions(private val activity: Activity) {
    // Add-to-shortcuts flow state (mirrors SteamGamesActivity / SteamGameDetailActivity).
    private var showExePicker by mutableStateOf<ExePickerDataXa?>(null)
    private var addToShortcuts by mutableStateOf<AddToShortcutsRequest?>(null)
    // Non-null while an uninstall is deleting files → shows the blocking progress spinner.
    private var uninstallingName by mutableStateOf<String?>(null)
    // Non-null briefly after an uninstall (or a launch problem) → themed auto-dismiss confirmation bar (not a Toast).
    var notice by mutableStateOf<String?>(null)
        private set

    /** Draws the dialogs and the bar of the flows below. [onOpenShortcuts]: the "Open Shortcuts" button of the success dialog. */
    @Composable
    fun Dialogs() {
        showExePicker?.let { data ->
            ExePickerDialogXa(
                gameName = data.gameName,
                candidates = data.candidates,
                onDismiss = { showExePicker = null },
                onSelected = { chosen ->
                    showExePicker = null
                    startAddToShortcuts(data.appId, data.gameName, chosen, data.coverUrl)
                },
            )
        }

        uninstallingName?.let { UninstallProgressDialog(it) }
        notice?.let { UninstallResultBar(it) { notice = null } }

        addToShortcuts?.let { req ->
            DeckAddToShortcuts(req, null) { addToShortcuts = null }
        }
    }

    /** Tap a card → open that game's detail in its store (works for downloading + installed). */
    fun openDetail(entry: DownloadEntry) {
        when (entry.store) {
            Store.STEAM -> activity.startActivity(
                Intent(activity, SteamGameDetailActivity::class.java)
                    .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, entry.id.toIntOrNull() ?: 0),
            )
            Store.AMAZON -> {
                // The card only carries id(=productId)/name/cover; hydrate the rest of the detail
                // extras from amazon_library_cache. Fall back to the entry's own fields so the page
                // still opens (with a functional install/launch) even if the cache is unavailable.
                val d = AmazonLibrarySync.cachedDetail(activity, entry.id)
                val title = d?.title?.takeIf { it.isNotEmpty() } ?: entry.name
                val art = d?.artUrl?.takeIf { it.isNotEmpty() } ?: entry.cover ?: ""
                activity.startActivity(
                    Intent(activity, AmazonGameDetailActivity::class.java).apply {
                        putExtra("product_id", entry.id)
                        putExtra("entitlement_id", d?.entitlementId ?: "")
                        putExtra("title", title)
                        putExtra("developer", d?.developer ?: "")
                        putExtra("publisher", d?.publisher ?: "")
                        putExtra("art_url", art)
                        putExtra("product_sku", d?.productSku ?: "")
                    },
                )
            }
            Store.GOG -> {
                // Hydrate the detail extras from gog_library_cache; fall back to the entry's own
                // fields so the page still opens even if the cache is unavailable.
                val d = GogLibrarySync.cachedDetail(activity, entry.id)
                val title = d?.title?.takeIf { it.isNotEmpty() } ?: entry.name
                val image = d?.imageUrl?.takeIf { it.isNotEmpty() } ?: entry.cover ?: ""
                activity.startActivity(
                    Intent(activity, GogGameDetailActivity::class.java).apply {
                        putExtra("game_id", entry.id)
                        putExtra("title", title)
                        putExtra("image_url", image)
                        putExtra("description", d?.description ?: "")
                        putExtra("developer", d?.developer ?: "")
                        putExtra("category", d?.category ?: "")
                        putExtra("generation", d?.generation ?: 0)
                    },
                )
            }
            Store.XBOX -> {
                // Installed record first (title, art, market), then the entry's own fields.
                val g = com.winhub.hernandez.store.xbox.XboxInstallState.get(activity, entry.id)
                activity.startActivity(com.winhub.hernandez.store.xbox.XboxMainActivity.detailIntent(activity,
                    com.winhub.hernandez.store.xbox.XboxGame(entry.id, g?.title ?: entry.name, g?.heroUrl, g?.posterUrl ?: entry.cover,
                        market = g?.market ?: com.winhub.hernandez.store.xbox.Xodus.market(), access = g?.access ?: "")))
            }
            Store.EPIC -> {
                // Hydrate the detail extras from epic_cache; fall back to the entry's own fields so
                // the page still opens even if the cache is unavailable.
                val d = EpicLibrarySync.cachedDetail(activity, entry.id)
                val title = d?.title?.takeIf { it.isNotEmpty() } ?: entry.name
                val art = d?.artCover?.takeIf { it.isNotEmpty() } ?: entry.cover ?: ""
                activity.startActivity(
                    Intent(activity, EpicGameDetailActivity::class.java).apply {
                        putExtra("app_name", entry.id)
                        putExtra("title", title)
                        putExtra("description", d?.description ?: "")
                        putExtra("developer", d?.developer ?: "")
                        putExtra("art_cover", art)
                        putExtra("namespace", d?.namespace ?: "")
                        putExtra("catalog_item_id", d?.catalogItemId ?: "")
                    },
                )
            }
        }
    }

    /** Same launch flow the Steam screens use: scan for exes, pick one, add a shortcut. */
    fun launch(entry: DownloadEntry) {
        val dir = entry.installPath
        if (dir.isNullOrEmpty()) {
            notice = "Install directory not set"
            return
        }
        if (entry.store == Store.XBOX) {
            // Xbox: the launch exe comes from MicrosoftGame.config and the shortcut carries the product id.
            val g = com.winhub.hernandez.store.xbox.XboxInstallState.get(activity, entry.id)
            if (g == null) { notice = "This Xbox game is not installed"; return }
            StarLaunchBridge.addToLauncher(activity, g.title, File(g.folder, g.exe).path, g.posterUrl ?: g.heroUrl, "xbox:${g.id}", false)
            return
        }
        // Only a Steam entry's id is a Steam app ID; GOG ids are numeric too and must not be stamped as Steam.
        val appId = if (entry.store == Store.STEAM) entry.id.toIntOrNull() ?: 0 else 0
        val installDir = File(dir)
        Thread {
            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(installDir, exeFiles)
            if (exeFiles.isEmpty()) {
                activity.runOnUiThread {
                    notice = "No .exe found in install directory"
                }
                return@Thread
            }
            val lowerTitle = entry.name.lowercase()
            exeFiles.sortWith { a, b ->
                AmazonLaunchHelper.scoreExe(b, lowerTitle) - AmazonLaunchHelper.scoreExe(a, lowerTitle)
            }
            val coverUrl = "https://shared.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900.jpg"

            if (exeFiles.size == 1) {
                activity.runOnUiThread { startAddToShortcuts(appId, entry.name, exeFiles[0].absolutePath, coverUrl) }
                return@Thread
            }
            val candidates = exeFiles.map { it.absolutePath }
            activity.runOnUiThread { showExePicker = ExePickerDataXa(appId, entry.name, candidates, coverUrl) }
        }.start()
    }

    /** Load containers, then show the M3 picker. Goldberg Cold-Client rewrites the exe. */
    private fun startAddToShortcuts(appId: Int, gameName: String, exePath: String, coverUrl: String?) {
        val launchExe = GoldbergPatcher.resolveLaunchExe(activity, appId, exePath)
        StarLaunchBridge.loadContainers(activity) { containers ->
            addToShortcuts = AddToShortcutsRequest(gameName, launchExe, coverUrl, containers)
        }
    }

    /** Uninstall: mark uninstalled in the store DB and delete the install dir off the UI
     *  thread; once the delete is VERIFIED complete, drop the durable-library row (which also
     *  removes it from the live registry), dismiss the spinner, and toast the real outcome. */
    fun uninstall(entry: DownloadEntry) {
        uninstallingName = entry.name
        StoreUninstaller.run(
            installDir = entry.installPath,
            mark = {
                if (entry.store == Store.STEAM) {
                    entry.id.toIntOrNull()?.let { SteamRepository.getInstance().database.markUninstalled(it) }
                }
            },
        ) { ok ->
            // Clear the durable-library row (also drops the live registry entry) AND the store's
            // OWN native install record — otherwise the originating store's list/detail keeps
            // reading "installed" from a record the Manager never touched (device-observed bug).
            DownloadRegistry.removeLibraryEntry(entry.key)
            purgeNativeInstall(entry)
            uninstallingName = null
            notice = if (ok) "${entry.name} uninstalled" else "Couldn't fully remove ${entry.name}"
        }
    }

    /**
     * Per-store native install-record purge — the generalized seam so a cross-store uninstall
     * clears each store's OWN "installed" bookkeeping, not just Steam's DB. Steam is already
     * handled by the `mark` callback above (DB `markUninstalled`); this covers the storefronts
     * whose install-state lives outside a DB.
     */
    private fun purgeNativeInstall(entry: DownloadEntry) {
        when (entry.store) {
            Store.AMAZON -> AmazonInstallState.purge(activity, entry.id)
            Store.GOG -> GogInstallState.purge(activity, entry.id)
            Store.EPIC -> EpicInstallState.purge(activity, entry.id)
            Store.XBOX -> com.winhub.hernandez.store.xbox.XboxInstallState.remove(activity, entry.id)
            Store.STEAM -> Unit   // handled via SteamRepository.markUninstalled in `mark`
        }
    }

    /**
     * Cancel a download from the Download Manager. For Epic this raises the same keep/delete dialog
     * as the detail page + library list (synced via [EpicCancelPolicy]); the running download
     * coroutine acts on the choice. Other stores cancel directly (their keep/delete semantics are
     * unchanged).
     */
    fun cancel(entry: DownloadEntry) {
        if (entry.store == Store.EPIC) {
            AlertDialog.Builder(activity)
                .setTitle("Cancel download?")
                .setMessage("Keep the partial download so you can resume later, or delete all " +
                        "downloaded files for this game?")
                .setPositiveButton("Keep files") { _, _ ->
                    EpicCancelPolicy.setDeleteOnCancel(entry.id, false)
                    entry.cancel?.invoke()
                }
                .setNegativeButton("Delete files") { _, _ ->
                    EpicCancelPolicy.setDeleteOnCancel(entry.id, true)
                    entry.cancel?.invoke()
                }
                .setNeutralButton("Keep downloading", null)
                .show()
        } else {
            entry.cancel?.invoke()
        }
    }

    /** Pause a running download, or resume a paused one. */
    fun pauseResume(entry: DownloadEntry) {
        entry.pause?.invoke()   // the store's own handle toggles
    }

    /** Drop a failed / cancelled row. */
    fun dismiss(entry: DownloadEntry) {
        DownloadRegistry.remove(entry.key)
    }

    /** Queue reorder (Steam-only; a no-op for any id that isn't a live queued Steam appId). */
    fun moveUp(entry: DownloadEntry) {
        if (entry.store != Store.STEAM) return   // GOG ids are numeric too
        entry.id.toIntOrNull()?.let { id -> DownloadQueue.moveUp(id) }
    }

    fun moveToTop(entry: DownloadEntry) {
        if (entry.store != Store.STEAM) return
        entry.id.toIntOrNull()?.let { id -> DownloadQueue.moveToTop(id) }
    }

    companion object {
        /**
         * Seeds the durable library so a screen has rows even if opened before any producer touched the registry this session, and
         * the non-Steam libraries too (they self-heal orphaned installs and surface update-available). Idempotent; never throws
         * into startup.
         */
        fun prepare(context: Context) {
            SteamPrefs.init(context)
            SteamRepository.getInstance().initialize(context)
            DownloadRegistry.init(context)
            AmazonLibrarySync.seed(context)
            GogLibrarySync.seed(context)
            EpicLibrarySync.seed(context)
            com.winhub.hernandez.store.xbox.XboxDownloads.seed(context)
        }
    }
}

private data class ExePickerDataXa(
    val appId: Int,
    val gameName: String,
    val candidates: List<String>,
    val coverUrl: String,
)

@Composable
private fun ExePickerDialogXa(
    gameName: String,
    candidates: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select executable for \"$gameName\"") },
        text = {
            // Cap at ~half the screen height so a long bin/*.exe list still scrolls to the
            // real game exe (same treatment as the Steam screens' exe pickers).
            val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
            Column(
                modifier = Modifier
                    .heightIn(max = maxListHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                candidates.forEach { path ->
                    val f = File(path)
                    val parent = f.parentFile
                    val label = if (parent != null) "${parent.name}/${f.name}" else f.name
                    TextButton(
                        onClick = { onSelected(path) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label, modifier = Modifier.weight(1f)) }
                }
            }
        },
        confirmButton = {},
    )
}
