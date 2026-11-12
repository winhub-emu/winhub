package com.winhub.hernandez.store

import com.winhub.hernandez.ui.deck.XmbTapToEdit
import com.winhub.hernandez.ui.deck.xmbDoneActions
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadScope
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreDownloadHooks
import com.winhub.hernandez.ui.theme.WinlatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EpicGamesActivity : ComponentActivity() {
    data class GameDownloadState(
        val progress: Int = 0,
        val status: String = "",
        val isActive: Boolean = false,
        val showProgress: Boolean = false,
        val installed: Boolean = false,
    )

    companion object {
        private const val TAG = "BH_EPIC"
        private const val PREFS_NAME = "bh_epic_prefs"
        private const val CACHE_KEY = "epic_cache"
        private const val VIEW_MODE_KEY = "epic_view_mode"
        private const val REQ_GAME_DETAIL = 1001
    }

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var prefs: SharedPreferences? = null

    private var syncText by mutableStateOf("Loading Epic library\u2026")
    private var games by mutableStateOf<List<EpicGame>>(emptyList())
    private var allGames by mutableStateOf<List<EpicGame>>(emptyList())
    private var searchQuery by mutableStateOf("")
    private var viewMode by mutableStateOf("list")
    private var refreshEnabled by mutableStateOf(true)
    private var scrollVisible by mutableStateOf(false)

    // Themed auto-dismiss bar — system Toasts render as an unreadable black box on this ROM
    // (targetSDK 28); reuse the shared UninstallResultBar for readable feedback.
    private var resultBarMsg by mutableStateOf<String?>(null)
    private val downloadStates = mutableStateMapOf<String, GameDownloadState>()
    private val cancelRunnables = mutableMapOf<String, Runnable>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, 0)
        viewMode = prefs!!.getString(VIEW_MODE_KEY, "grid") ?: "grid"

        // Cross-store Download Manager (Phase C): init the registry + seed/self-heal the installed
        // Epic library here (idempotent), mirroring Amazon/GOG.
        DownloadRegistry.init(this)
        EpicLibrarySync.seed(this)

        setContent {
            StoreKeyboardTheme {
                DeckStoreTheme {
                EpicGamesScreen(
                    syncText = syncText,
                    searchQuery = searchQuery,
                    viewMode = viewMode,
                    games = games,
                    scrollVisible = scrollVisible,
                    refreshEnabled = refreshEnabled,
                    downloadStates = downloadStates,
                    onBack = { finish() },
                    onSearchChange = { q -> searchQuery = q; applyFilter(q) },
                    onViewToggle = {
                        viewMode = when (viewMode) {
                            "list" -> "grid"
                            "grid" -> "poster"
                            else -> "list"
                        }
                        prefs!!.edit().putString(VIEW_MODE_KEY, viewMode).apply()
                        applyFilter(searchQuery)
                    },
                    onRefresh = { startSync(true) },
                    onFreeGames = { startActivity(Intent(this@EpicGamesActivity, EpicFreeGamesActivity::class.java)) },
                    onGameClick = { game -> openDetailScreen(game) },
                    onInstallGame = { game -> showInstallConfirmDialog(game) },
                    onCancelDownload = { appName ->
                        // Same keep/delete dialog as the detail page + Download Manager (synced via
                        // EpicCancelPolicy) — the running download coroutine acts on the choice.
                        AlertDialog.Builder(this@EpicGamesActivity)
                            .setTitle("Cancel download?")
                            .setMessage("Keep the partial download so you can resume later, or delete " +
                                    "all downloaded files for this game?")
                            .setPositiveButton("Keep files") { _, _ ->
                                EpicCancelPolicy.setDeleteOnCancel(appName, false)
                                cancelRunnables[appName]?.run()
                                cancelRunnables.remove(appName)
                                downloadStates[appName] = GameDownloadState()
                            }
                            .setNegativeButton("Delete files") { _, _ ->
                                EpicCancelPolicy.setDeleteOnCancel(appName, true)
                                cancelRunnables[appName]?.run()
                                cancelRunnables.remove(appName)
                                downloadStates[appName] = GameDownloadState()
                            }
                            .setNeutralButton("Keep downloading", null)
                            .show()
                    },
                    onAddToLauncher = { game ->
                        val exe = prefs!!.getString("epic_exe_${game.appName}", null)
                        if (exe != null) {
                            StarLaunchBridge.addToLauncher(
                                this@EpicGamesActivity, game.title, exe,
                                if (game.artCover.isNotEmpty()) game.artCover else game.artSquare,
                                StarLaunchBridge.EpicMeta(game.appName, game.namespace, game.catalogItemId),
                            )
                        }
                    },
                )
                resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
            
                }}
        }

        val cached = loadCachedGames()
        if (cached != null && cached.isNotEmpty()) {
            games = cached
            val cn = cached.size
            syncText = "$cn ${if (cn == 1) "game" else "games"} \u2014 cached  \u2022  tap \u21ba to refresh"
            scrollVisible = true
        }
        startSync(cached == null || cached.isEmpty())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_GAME_DETAIL && resultCode == EpicGameDetailActivity.RESULT_REFRESH) {
            applyFilter(searchQuery)
        }
    }

    private fun startSync(showProgress: Boolean) {
        if (showProgress) syncText = "Loading Epic library\u2026"
        refreshEnabled = false
        lifecycleScope.launch(Dispatchers.IO) { syncLibrary(showProgress) }
    }

    private suspend fun syncLibrary(showProgress: Boolean) {
        try {
            if (showProgress) setSync("Checking credentials\u2026")
            val token = EpicCredentialStore.getValidAccessToken(this)
            if (token == null) {
                setSync("Not logged in")
                withContext(Dispatchers.Main) {
                    enableRefresh()
                    resultBarMsg = "Please log in to Epic Games first"
                    finish()
                }
                return
            }

            if (showProgress) setSync("Fetching game list\u2026")
            val rawGames = EpicApiClient.getLibraryItems(token)

            if (rawGames.isNullOrEmpty()) {
                setSync("No games found in Epic library")
                withContext(Dispatchers.Main) { enableRefresh() }
                return
            }

            if (showProgress) setSync("Loading game details\u2026")
            val total = rawGames.size
            var done = 0
            for (game in rawGames) {
                EpicApiClient.enrichFromCatalog(token, game)
                if (game.releaseDate.isNotEmpty()) {
                    prefs!!.edit().putString("epic_release_${game.appName}", game.releaseDate).apply()
                }
                done++
                if (done % 5 == 0) {
                    val d = done
                    setSync("Loading game details\u2026 ($d/$total)")
                }
            }

            val mainGames = mutableListOf<EpicGame>()
            val epicDlcMap = mutableMapOf<String, JSONArray>()
            for (g in rawGames) {
                if (!g.isDLC) {
                    mainGames.add(g)
                } else if (g.baseGameCatalogItemId.isNotEmpty()) {
                    var arr = epicDlcMap[g.baseGameCatalogItemId]
                    if (arr == null) { arr = JSONArray(); epicDlcMap[g.baseGameCatalogItemId] = arr }
                    try {
                        val dlcObj = JSONObject()
                        dlcObj.put("app", g.appName)
                        dlcObj.put("ns", g.namespace)
                        dlcObj.put("cat", g.catalogItemId)
                        dlcObj.put("title", g.title)
                        arr.put(dlcObj)
                    } catch (_: Exception) {}
                }
            }
            val dlcEd = prefs!!.edit()
            for ((key, value) in epicDlcMap) {
                dlcEd.putString("epic_dlcs_$key", value.toString())
            }
            dlcEd.apply()
            val displayGames = (if (mainGames.isEmpty()) rawGames else mainGames)
                .distinctBy { it.appName }
                .toMutableList()

            displayGames.sortWith { a, b -> a.title.compareTo(b.title, ignoreCase = true) }

            val cached = loadCachedGames()
            if (cached != null) {
                for (fresh in displayGames) {
                    for (old in cached) {
                        if (old.appName == fresh.appName) {
                            fresh.isInstalled = old.isInstalled
                            fresh.installPath = old.installPath
                            fresh.version = old.version
                            fresh.installSize = old.installSize
                            break
                        }
                    }
                }
            }

            saveCachedGames(displayGames)

            withContext(Dispatchers.Main) {
                allGames = displayGames
                applyFilter(searchQuery)
                val fn = displayGames.size
                syncText = "$fn ${if (fn == 1) "game" else "games"} \u2014 tap a card to install"
                enableRefresh()
                scrollVisible = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncLibrary error", e)
            setSync("Error: ${e.message}")
            withContext(Dispatchers.Main) { enableRefresh() }
        }
    }

    private fun applyFilter(query: String) {
        games = if (query.isBlank()) allGames
        else allGames.filter { it.title.contains(query, ignoreCase = true) }
    }

    private fun enableRefresh() {
        uiHandler.post { refreshEnabled = true }
    }

    private fun setSync(msg: String) {
        uiHandler.post { syncText = msg }
    }

    private fun openDetailScreen(game: EpicGame) {
        val intent = Intent(this, EpicGameDetailActivity::class.java).apply {
            putExtra("app_name", game.appName)
            putExtra("title", game.title)
            putExtra("description", game.description)
            putExtra("developer", game.developer)
            putExtra("art_cover", game.artCover)
            putExtra("namespace", game.namespace)
            putExtra("catalog_item_id", game.catalogItemId)
        }
        startActivityForResult(intent, REQ_GAME_DETAIL)
    }

    private fun showInstallConfirmDialog(game: EpicGame) {
        var freeBytes = -1L
        try {
            val base = File(File(filesDir, "imagefs/epic_games"), "_check")
            val parent = base.parentFile
            parent?.mkdirs()
            val sf = android.os.StatFs((parent ?: cacheDir).absolutePath)
            freeBytes = sf.availableBlocksLong * sf.blockSizeLong
        } catch (_: Exception) {}

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 16, 40, 16)

            addView(TextView(this@EpicGamesActivity).apply {
                id = android.R.id.text1
                text = "Download size:  Fetching\u2026"
                setTextColor(0xFFCCCCCC.toInt())
                textSize = 14f
            })

            addView(TextView(this@EpicGamesActivity).apply {
                text = "Available storage:  ${formatBytes(freeBytes)}"
                setTextColor(0xFF88CC88.toInt())
                textSize = 14f
            })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Install ${game.title}?")
            .setView(content)
            .setPositiveButton("Install", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.dismiss()
            startDownload(game)
        }

        if (game.installSize > 0) {
            (content.getChildAt(0) as TextView).text = "Download size:  ${formatBytes(game.installSize)}"
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                var size = 0L
                try {
                    val token = EpicCredentialStore.getValidAccessToken(this@EpicGamesActivity)
                    if (token != null) {
                        size = EpicApiClient.getInstallSize(token, game)
                        game.installSize = size
                    }
                } catch (_: Exception) {}
                val finalSize = size
                withContext(Dispatchers.Main) {
                    if (dialog.isShowing) {
                        (content.getChildAt(0) as TextView).text =
                            "Download size:  ${if (finalSize > 0) formatBytes(finalSize) else "Unknown"}"
                    }
                }
            }
        }
    }

    private fun startDownload(game: EpicGame) {
        val appName = game.appName
        downloadStates[appName] = GameDownloadState(isActive = true, showProgress = true)

        // CANCEL: install() polls this flag across manifest/verify/chunk/assemble, so cancel stops
        // the download promptly. Keep/delete of the partial is decided via EpicCancelPolicy below.
        EpicCancelPolicy.clear(appName)   // fresh download → keep-on-cancel until a dialog says otherwise
        val cancelled = AtomicBoolean(false)
        cancelRunnables[appName] = Runnable { cancelled.set(true) }

        // Publish into the cross-store Download Manager (shade notification + background survival).
        StoreDownloadHooks.registerDownload(
            store = Store.EPIC,
            id = appName,
            name = game.title,
            cover = game.artCover.ifEmpty { game.artSquare },
            supportsPause = false,
            installTotal = prefs!!.getLong("epic_size_$appName", 0L),
            cancel = { cancelled.set(true) },
        )

        // applicationContext + DownloadScope.io: install() blocks synchronously, so the download now
        // survives this list Activity being destroyed / the app backgrounded.
        val appCtx = applicationContext
        DownloadScope.io.launch {
            try {
                val token = EpicCredentialStore.getValidAccessToken(appCtx)
                if (token == null) {
                    StoreDownloadHooks.markFailed(Store.EPIC, appName, "Login required")
                    withContext(Dispatchers.Main) {
                        downloadStates[appName] = GameDownloadState()
                        resultBarMsg = "Login required"
                    }
                    return@launch
                }

                downloadStates[appName] = downloadStates[appName]?.copy(status = "Fetching manifest\u2026")
                    ?: GameDownloadState(isActive = true, status = "Fetching manifest\u2026", showProgress = true)

                val manifestJson = EpicApiClient.getManifestApiJson(
                    token, game.namespace, game.catalogItemId, game.appName,
                )
                if (manifestJson == null) {
                    StoreDownloadHooks.markFailed(Store.EPIC, appName, "Failed to fetch manifest")
                    withContext(Dispatchers.Main) {
                        downloadStates[appName] = GameDownloadState()
                        resultBarMsg = "Failed to fetch manifest. If this is Fortnite, it is not supported."
                    }
                    return@launch
                }

                var sanitized = game.title.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
                if (sanitized.isEmpty()) sanitized = "epic_${game.appName.hashCode()}"
                val installDir = File(File(filesDir, "imagefs/epic_games"), sanitized)
                prefs!!.edit().putString("epic_dir_${game.appName}", installDir.absolutePath).apply()

                // Feature #2 — download required(base) + the container language's files only.
                val installTags = EpicInstallTags.tagsForCurrentContainer(appCtx)

                val ok = EpicDownloadManager.install(
                    appCtx,
                    manifestJson,
                    token,
                    installDir.absolutePath,
                    installTags,
                    cancelled,
                ) { msg, pct ->
                    if (!cancelled.get()) {
                        StoreDownloadHooks.tick(Store.EPIC, appName, pct)
                        downloadStates[appName] = downloadStates[appName]?.copy(progress = pct, status = msg)
                            ?: GameDownloadState(isActive = true, progress = pct, status = msg, showProgress = true)
                    }
                }

                if (cancelled.get()) {
                    // Delete the partial if the user chose "Delete files" on any Cancel dialog.
                    if (EpicCancelPolicy.consumeDeleteOnCancel(appName)) {
                        try { EpicDownloadManager.deleteDir(installDir) } catch (_: Exception) {}
                        // Clear the install record so the list/detail recompute to "not installed".
                        EpicInstallState.purge(applicationContext, appName)
                    }
                    StoreDownloadHooks.markCancelled(Store.EPIC, appName)
                    withContext(Dispatchers.Main) {
                        cancelRunnables.remove(appName)
                        downloadStates[appName] = GameDownloadState()
                    }
                    return@launch
                }
                if (!ok) {
                    StoreDownloadHooks.markFailed(Store.EPIC, appName, "Download failed")
                    withContext(Dispatchers.Main) {
                        cancelRunnables.remove(appName)
                        downloadStates[appName] = GameDownloadState()
                        resultBarMsg = "Download failed"
                    }
                    return@launch
                }

                val exeFiles = mutableListOf<File>()
                AmazonLaunchHelper.collectExe(installDir, exeFiles)

                if (exeFiles.isEmpty()) {
                    StoreDownloadHooks.markFailed(Store.EPIC, appName, "No executable found")
                    withContext(Dispatchers.Main) {
                        cancelRunnables.remove(appName)
                        downloadStates[appName] = GameDownloadState()
                        resultBarMsg = "No executable found after install"
                    }
                    return@launch
                }

                val lowerTitle = game.title.lowercase()
                exeFiles.sortWith { a, b ->
                    AmazonLaunchHelper.scoreExe(b, lowerTitle) - AmazonLaunchHelper.scoreExe(a, lowerTitle)
                }

                // Completion NEVER shows a picker: auto-record the best-scored exe and finalize \u2014
                // mirrors the Amazon/GOG fix (a dialog on a stopped Activity wedged the card at 100%).
                val path = exeFiles[0].absolutePath
                prefs!!.edit().putString("epic_exe_${game.appName}", path).apply()
                // Detect EOS SDK presence so the library can show an "EOS" badge.
                EpicEosDetector.scanAsync(appCtx, game.appName, installDir, null)
                StoreDownloadHooks.markInstalled(
                    store = Store.EPIC,
                    id = appName,
                    installPath = installDir.absolutePath,
                    bytes = prefs!!.getLong("epic_size_$appName", 0L),
                )
                withContext(Dispatchers.Main) {
                    cancelRunnables.remove(appName)
                    downloadStates[appName] = GameDownloadState(progress = 100, installed = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startEpicDownload failed", e)
                if (!cancelled.get()) {
                    StoreDownloadHooks.markFailed(Store.EPIC, appName, e.message ?: "Unknown error")
                    withContext(Dispatchers.Main) {
                        cancelRunnables.remove(appName)
                        downloadStates[appName] = GameDownloadState()
                        resultBarMsg = e.message ?: "Unknown error"
                    }
                }
            }
        }
    }

    private fun saveCachedGames(games: List<EpicGame>) {
        try {
            val arr = JSONArray()
            for (g in games) {
                val j = JSONObject()
                j.put("appName", g.appName)
                j.put("namespace", g.namespace)
                j.put("catalogItemId", g.catalogItemId)
                j.put("title", g.title)
                j.put("artCover", g.artCover)
                j.put("artSquare", g.artSquare)
                j.put("developer", g.developer)
                j.put("description", g.description)
                j.put("version", g.version)
                j.put("isInstalled", g.isInstalled)
                j.put("installPath", g.installPath)
                j.put("installSize", g.installSize)
                j.put("canRunOffline", g.canRunOffline)
                j.put("cloudSaveEnabled", g.cloudSaveEnabled)
                j.put("cloudSaveFolder", g.cloudSaveFolder)
                arr.put(j)
            }
            val ed = prefs!!.edit().putString(CACHE_KEY, arr.toString())
            // Persist each game's CloudSaveFolder token string per-appName so the Save Manager's Epic
            // tab (EpicCloudSavePaths.resolveSaveDirectory) can resolve it without another catalog call.
            for (g in games) {
                if (g.appName.isEmpty()) continue
                // Mark that we've fetched this game's cloud-save metadata at least once — lets the Save
                // Manager tell "no cloud-save support" (checked, empty folder) apart from "not refreshed
                // yet" (never checked).
                ed.putBoolean("epic_cloud_checked_${g.appName}", true)
                if (g.cloudSaveFolder.isNotEmpty()) ed.putString("epic_save_folder_${g.appName}", g.cloudSaveFolder)
                else ed.remove("epic_save_folder_${g.appName}")
            }
            ed.apply()
        } catch (e: Exception) { Log.e(TAG, "saveCachedGames failed", e) }
    }

    private fun loadCachedGames(): List<EpicGame>? {
        return try {
            val json = prefs!!.getString(CACHE_KEY, null) ?: return null
            val arr = JSONArray(json)
            val list = mutableListOf<EpicGame>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val g = EpicGame()
                g.appName = j.optString("appName", "")
                g.namespace = j.optString("namespace", "")
                g.catalogItemId = j.optString("catalogItemId", "")
                g.title = j.optString("title", "")
                g.artCover = j.optString("artCover", "")
                g.artSquare = j.optString("artSquare", "")
                g.developer = j.optString("developer", "")
                g.description = j.optString("description", "")
                g.version = j.optString("version", "")
                g.isInstalled = j.optBoolean("isInstalled", false)
                g.installPath = j.optString("installPath", "")
                val cachedSize = j.optLong("installSize", 0L)
                g.installSize = if (cachedSize > 1_099_511_627_776L) 0L else cachedSize
                g.canRunOffline = j.optBoolean("canRunOffline", true)
                g.cloudSaveEnabled = j.optBoolean("cloudSaveEnabled", false)
                g.cloudSaveFolder = j.optString("cloudSaveFolder", "")
                list.add(g)
            }
            list.distinctBy { it.appName }
        } catch (e: Exception) { Log.e(TAG, "loadCachedGames failed", e); null }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "Unknown"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
private fun EpicGamesScreen(
    syncText: String,
    searchQuery: String,
    viewMode: String,
    games: List<EpicGame>,
    scrollVisible: Boolean,
    refreshEnabled: Boolean,
    downloadStates: Map<String, EpicGamesActivity.GameDownloadState>,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onViewToggle: () -> Unit,
    onRefresh: () -> Unit,
    onFreeGames: () -> Unit,
    onGameClick: (EpicGame) -> Unit,
    onInstallGame: (EpicGame) -> Unit,
    onCancelDownload: (String) -> Unit,
    onAddToLauncher: (EpicGame) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) { Text("\u2190", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }
            Text(
                text = "Epic Games",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Button(
                onClick = onViewToggle,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) { Text("\u25A6", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onRefresh,
                enabled = refreshEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) { Text("\u21ba", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onFreeGames,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp),
            ) { Text("FREE", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            // ⬇ cross-store Download Manager (global active-count badge).
            DownloadsButton()
        }

        XmbTapToEdit(Modifier.fillMaxWidth()) { fieldModifier, stopEditing ->
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search games\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                modifier = fieldModifier,
                keyboardActions = xmbDoneActions(stopEditing),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            )
        }

        Text(
            text = syncText,
            fontSize = 13.sp,
            color = when {
                syncText.startsWith("Error") || syncText.startsWith("Not logged in") || syncText.startsWith("No games") -> MaterialTheme.colorScheme.error
                syncText.contains("game") && (syncText.contains("tap") || syncText.contains("cached")) -> Color(0xFF81C784) // semantic "library ready" green
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        if (scrollVisible) {
            when (viewMode) {
                "list" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(games, key = { it.appName }) { game ->
                            // No live download this session → disk-truth (epic_exe_ present), the
                            // same record the detail page reads. game.isInstalled is never re-derived
                            // from prefs on a cold start, so trusting it alone left installed games
                            // showing "Install".
                            val ds = downloadStates[game.appName]
                                ?: EpicGamesActivity.GameDownloadState(
                                    installed = EpicInstallState.isInstalled(LocalContext.current, game.appName),
                                )
                            GameListCard(
                                game = game,
                                downloadState = ds,
                                onClick = { onGameClick(game) },
                                onInstall = { onInstallGame(game) },
                                onCancel = { onCancelDownload(game.appName) },
                                onAddToLauncher = { onAddToLauncher(game) },
                            )
                        }
                        if (games.isEmpty()) {
                            item { EmptyState(searchQuery) }
                        }
                    }
                }
                "grid" -> {
                    LazyVerticalGrid(
                        // 4 cols: the compact square tiles were cramped at 5 on a portrait phone.
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(games, key = { it.appName }) { game ->
                            // No live download this session → disk-truth (epic_exe_ present), the
                            // same record the detail page reads. game.isInstalled is never re-derived
                            // from prefs on a cold start, so trusting it alone left installed games
                            // showing "Install".
                            val ds = downloadStates[game.appName]
                                ?: EpicGamesActivity.GameDownloadState(
                                    installed = EpicInstallState.isInstalled(LocalContext.current, game.appName),
                                )
                            GameGridTile(
                                game = game,
                                downloadState = ds,
                                artHeightDp = 105,
                                onClick = { onGameClick(game) },
                                onInstall = { onInstallGame(game) },
                                onCancel = { onCancelDownload(game.appName) },
                                onAddToLauncher = { onAddToLauncher(game) },
                            )
                        }
                        if (games.isEmpty()) {
                            item { EmptyState(searchQuery) }
                        }
                    }
                }
                "poster" -> {
                    LazyVerticalGrid(
                        // 3 cols: the tall portrait cover-art tiles were far too narrow at 5.
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(games, key = { it.appName }) { game ->
                            // No live download this session → disk-truth (epic_exe_ present), the
                            // same record the detail page reads. game.isInstalled is never re-derived
                            // from prefs on a cold start, so trusting it alone left installed games
                            // showing "Install".
                            val ds = downloadStates[game.appName]
                                ?: EpicGamesActivity.GameDownloadState(
                                    installed = EpicInstallState.isInstalled(LocalContext.current, game.appName),
                                )
                            GameGridTile(
                                game = game,
                                downloadState = ds,
                                artHeightDp = 176,
                                onClick = { onGameClick(game) },
                                onInstall = { onInstallGame(game) },
                                onCancel = { onCancelDownload(game.appName) },
                                onAddToLauncher = { onAddToLauncher(game) },
                            )
                        }
                        if (games.isEmpty()) {
                            item { EmptyState(searchQuery) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    Text(
        text = if (query.isBlank()) "Your Epic library is empty"
        else "No results for \"$query\"",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
    )
}

/**
 * Small blue "EOS" pill shown on installed games detected as using Epic Online Services.
 * Identification only — see EpicLaunchArgs for the actual auth injection.
 */
@Composable
internal fun EosBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1A73E8))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("EOS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * Small dark-grey "EPIC" pill marking a game whose store source is the Epic Games Store.
 * Deliberately neutral/grey so it stays visually distinct from the blue EOS pill when the
 * two sit side by side (an Epic game that also uses EOS shows both).
 */
@Composable
internal fun EpicBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A2A))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("EPIC", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * EPIC + EOS pills clustered for the top-left corner of a game's cover art. Caller aligns/insets
 * this (Alignment.TopStart, ~4dp). The pill backgrounds carry their own contrast over artwork.
 */
@Composable
internal fun StoreBadgeOverlay(showEpic: Boolean, showEos: Boolean, modifier: Modifier = Modifier) {
    if (!showEpic && !showEos) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showEpic) EpicBadge()
        if (showEos) EosBadge()
    }
}

@Composable
private fun GameListCard(
    game: EpicGame,
    downloadState: EpicGamesActivity.GameDownloadState,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onAddToLauncher: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.clickable {
                    if (expanded) onClick()
                    else expanded = true
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                ) {
                    val imageUrl = if (game.artCover.isNotEmpty()) game.artCover else game.artSquare
                    if (imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = game.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (downloadState.installed) {
                            Text(" \u2713", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50)) // semantic installed-green
                        }
                        // Badges next to the name (thumbnail too small to overlay). Whole library is
                        // Epic \u2192 EPIC unconditional; EOS only when detected on an installed game.
                        Spacer(Modifier.width(6.dp))
                        StoreBadgeOverlay(
                            showEpic = true,
                            showEos = downloadState.installed &&
                                EpicEosDetector.isEosCached(LocalContext.current, game.appName),
                        )
                    }
                    if (game.developer.isNotEmpty()) {
                        Text(
                            text = game.developer,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (expanded) "\u25B2" else "\u25BC",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                if (game.developer.isNotEmpty()) {
                    Text(
                        text = game.developer,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (downloadState.installed) {
                    Text(
                        text = "\u2713 Installed",
                        fontSize = 10.sp,
                        color = Color(0xFF4CAF50), // semantic installed-green
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (downloadState.showProgress) {
                    LinearProgressIndicator(
                        progress = { downloadState.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    if (downloadState.status.isNotEmpty()) {
                        Text(
                            text = downloadState.status,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Button(
                    onClick = {
                        if (downloadState.isActive) onCancel()
                        else if (downloadState.installed) onAddToLauncher()
                        else onInstall()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (downloadState.isActive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        when {
                            downloadState.isActive -> "Cancel"
                            downloadState.installed -> "Add to Launcher"
                            else -> "Install"
                        },
                        color = if (downloadState.isActive) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameGridTile(
    game: EpicGame,
    downloadState: EpicGamesActivity.GameDownloadState,
    artHeightDp: Int,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onAddToLauncher: () -> Unit,
) {
    var showAction by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable {
                showAction = if (showAction) false else true
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artHeightDp.dp),
            ) {
                val imageUrl = if (game.artCover.isNotEmpty()) game.artCover else game.artSquare
                if (imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // Store badges overlaid top-left on the cover. EPIC unconditional (whole library
                // is Epic); EOS only when detected on an installed game.
                StoreBadgeOverlay(
                    showEpic = true,
                    showEos = downloadState.installed &&
                        EpicEosDetector.isEosCached(LocalContext.current, game.appName),
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0x44000000), Color(0xEE000000)),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = game.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (downloadState.installed) {
                    Text(" \u2713", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF66BB6A)) // semantic installed-green
                }
            }
        }

        if (showAction) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                if (downloadState.showProgress) {
                    LinearProgressIndicator(
                        progress = { downloadState.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        if (downloadState.isActive) onCancel()
                        else if (downloadState.installed) onAddToLauncher()
                        else onInstall()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (downloadState.isActive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth().height(32.dp).padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        when {
                            downloadState.isActive -> "Cancel"
                            downloadState.installed -> "Add to Launcher"
                            else -> "Install"
                        },
                        color = if (downloadState.isActive) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}
