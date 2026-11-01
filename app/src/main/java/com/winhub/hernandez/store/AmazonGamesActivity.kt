package com.winhub.hernandez.store

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import coil.request.ImageRequest
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreDownloadHooks
import com.winhub.hernandez.ui.theme.WinlatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AmazonGamesActivity : ComponentActivity() {

    private var prefs: SharedPreferences? = null

    // UI state
    private var allGames by mutableStateOf<List<AmazonGame>>(emptyList())
    private var searchQuery by mutableStateOf("")
    private var viewMode by mutableStateOf("list")
    private var statusText by mutableStateOf("Loading Amazon library\u2026")
    private var gamesVisible by mutableStateOf(false)
    private var expandedProductId by mutableStateOf<String?>(null)
    // Themed auto-dismiss bar — system Toasts render as an unreadable black box on this ROM
    // (targetSDK 28); reuse Steam's UninstallResultBar for readable uninstall feedback.
    private var resultBarMsg by mutableStateOf<String?>(null)
    private var refreshEnabled by mutableStateOf(true)

    // Download progress per game
    private val downloadStates = mutableStateListOf<Pair<String, DownloadState>>()

    // Dialog states
    private var installConfirmGame by mutableStateOf<AmazonGame?>(null)
    private var detailDialogGame by mutableStateOf<AmazonGame?>(null)
    private var exePickerData by mutableStateOf<ExePickerData?>(null)

    // Cancel tokens
    private val cancelTokens = mutableMapOf<String, () -> Unit>()

    private val gameDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == AmazonGameDetailActivity.RESULT_REFRESH) {
            refreshFromCache()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, 0)
        viewMode = prefs!!.getString(VIEW_MODE_KEY, "grid") ?: "grid"

        // Cross-store Download Manager (Phase A): init the registry + seed the installed
        // Amazon library here too, so an INSTALLED library is present even if the user opens
        // the Amazon list without ever touching Steam. Idempotent.
        DownloadRegistry.init(this)
        AmazonLibrarySync.seed(this)

        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                AmazonGamesScreen(
                    games = allGames,
                    searchQuery = searchQuery,
                    viewMode = viewMode,
                    statusText = statusText,
                    gamesVisible = gamesVisible,
                    refreshEnabled = refreshEnabled,
                    downloadStates = downloadStates,
                    expandedProductId = expandedProductId,
                    onToggleExpansion = { game ->
                        expandedProductId = if (expandedProductId == game.productId) null
                        else game.productId
                    },
                    onSearchChange = { q ->
                        searchQuery = q
                        applyFilter()
                    },
                    onViewModeToggle = { toggleViewMode() },
                    onRefresh = { startSync(true) },
                    onBack = { finish() },
                    onCardClick = { game -> onCardClick(game) },
                    onArrowClick = { expandedProductId = null },
                    onInstallOrLaunch = { game -> onInstallOrLaunch(game) },
                    onLaunchAdd = { game -> launchAdd(game) },
                    onCancel = { game -> cancelDownload(game) },
                    onLongPress = { game -> openDetailScreen(game) },
                    onOpenDetail = { game -> openDetailScreen(game) },
                )

                installConfirmGame?.let { game ->
                    InstallConfirmDialog(
                        game = game,
                        freeBytes = getFreeBytes(),
                        onDismiss = { installConfirmGame = null },
                        onConfirm = {
                            installConfirmGame = null
                            startGameDownload(game)
                        },
                    )
                }

                detailDialogGame?.let { game ->
                    GameDetailDialog(
                        game = game,
                        prefs = prefs!!,
                        onDismiss = {
                            detailDialogGame = null
                            refreshFromCache()
                        },
                        onSetExe = { openExePicker(game) },
                        onUninstall = { uninstallGame(game) },
                    )
                }

                exePickerData?.let { data ->
                    ExePickerDialog(
                        candidates = data.candidates,
                        onDismiss = { exePickerData = null },
                        onSelected = { selected ->
                            exePickerData = null
                            data.onSelected(selected)
                        },
                    )
                }
                resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
            
                }}
        }

        val cached = loadCachedGames()
        if (cached != null && cached.isNotEmpty()) {
            showGames(cached)
            val cn = cached.size
            setSync("$cn ${if (cn == 1) "game" else "games"} — cached  •  tap ↺ to refresh", false)
        }
        startSync(cached == null || cached.isEmpty())
    }

    override fun onResume() {
        super.onResume()
        refreshFromCache()
    }

    // ── View mode toggle ───────────────────────────────────────────────────

    private fun toggleViewMode() {
        viewMode = when (viewMode) {
            "list" -> "grid"
            "grid" -> "poster"
            else -> "list"
        }
        prefs!!.edit().putString(VIEW_MODE_KEY, viewMode).apply()
        expandedProductId = null
    }

    // ── Sync ─────────────────────────────────────────────────────────────

    private fun startSync(showProgress: Boolean) {
        refreshEnabled = false
        if (showProgress) setSync("Loading Amazon library\u2026", false)
        lifecycleScope.launch(Dispatchers.IO) {
            syncLibrary(showProgress)
        }
    }

    private suspend fun syncLibrary(showProgress: Boolean) {
        try {
            if (showProgress) setSync("Checking credentials\u2026", false)
            val creds = AmazonCredentialStore.load(this@AmazonGamesActivity)
            if (creds == null || creds.accessToken == null) {
                setSync("Not logged in", true)
                enableRefresh()
                withContext(Dispatchers.Main) {
                    resultBarMsg = "Please log in to Amazon Games first"
                    finish()
                }
                return
            }

            if (showProgress) setSync("Refreshing token\u2026", false)
            val token = AmazonCredentialStore.getValidAccessToken(this@AmazonGamesActivity)
            if (token == null) {
                setSync("Token refresh failed", true)
                enableRefresh()
                return
            }

            if (showProgress) setSync("Fetching game list\u2026", false)
            val allEntitlements = AmazonApiClient.getEntitlements(token, creds.deviceSerial)

            if (allEntitlements == null || allEntitlements.isEmpty()) {
                setSync("No games found in Amazon library", true)
                enableRefresh()
                return
            }

            val games = mutableListOf<AmazonGame>()
            val amazonDlcMap = mutableMapOf<String, JSONArray>()
            for (g in allEntitlements) {
                if (g.isDLC && g.parentProductId.isNotEmpty()) {
                    val arr = amazonDlcMap.getOrPut(g.parentProductId) { JSONArray() }
                    try {
                        val dlcObj = JSONObject()
                        dlcObj.put("eid", g.entitlementId)
                        dlcObj.put("pid", g.productId)
                        dlcObj.put("title", g.title)
                        arr.put(dlcObj)
                    } catch (_: Exception) {}
                } else {
                    games.add(g)
                }
            }
            val dlcEd = prefs!!.edit()
            for ((key, arr) in amazonDlcMap) {
                dlcEd.putString("amazon_dlcs_$key", arr.toString())
            }
            dlcEd.apply()

            val finalGames = if (games.isEmpty()) allEntitlements.toMutableList() else games

            finalGames.sortWith { a, b -> a.title.compareTo(b.title, ignoreCase = true) }

            val cached = loadCachedGames()
            if (cached != null) {
                for (fresh in finalGames) {
                    for (old in cached) {
                        if (old.productId == fresh.productId) {
                            fresh.isInstalled = old.isInstalled
                            fresh.installPath = old.installPath
                            fresh.versionId = old.versionId
                            fresh.downloadSize = old.downloadSize
                            fresh.installSize = old.installSize
                            break
                        }
                    }
                }
            }

            checkForUpdates(token, finalGames)
            saveCachedGames(finalGames)

            withContext(Dispatchers.Main) {
                showGames(finalGames)
                val fn = finalGames.size
                setSync("$fn ${if (fn == 1) "game" else "games"} — tap a card to install", false)
                enableRefresh()
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncLibrary error", e)
            setSync("Error: ${e.message}", true)
            enableRefresh()
        }
    }

    private fun checkForUpdates(token: String, games: MutableList<AmazonGame>) {
        for (game in games) {
            val installedExe = prefs!!.getString("amazon_exe_${game.productId}", null)
            if (installedExe == null || game.productId.isEmpty()) continue
            try {
                val liveVersion = AmazonApiClient.getLiveVersionId(token, game.productId)
                if (liveVersion != null && liveVersion.isNotEmpty() && liveVersion != game.versionId) {
                    Log.d(TAG, "Update available: ${game.title} (${game.versionId} → $liveVersion)")
                    game.versionId = "${liveVersion}_UPDATE_AVAILABLE"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed for: ${game.title}", e)
            }
        }
    }

    private fun showGames(games: List<AmazonGame>) {
        allGames = games
        gamesVisible = true
    }

    private fun refreshFromCache() {
        val cached = loadCachedGames()
        if (cached != null && cached.isNotEmpty()) {
            allGames = cached
        }
    }

    // ── Card actions ──────────────────────────────────────────────────────

    private fun onCardClick(game: AmazonGame) {
        if (expandedProductId == game.productId) {
            openDetailScreen(game)
        } else {
            expandedProductId = game.productId
        }
    }

    private fun onInstallOrLaunch(game: AmazonGame) {
        val exe = prefs!!.getString("amazon_exe_${game.productId}", null)
        if (exe != null) {
            StarLaunchBridge.addToLauncher(this, game.title, exe, game.artUrl, "amazon", true)
            return
        }
        installConfirmGame = game
    }

    private fun launchAdd(game: AmazonGame) {
        val exe = prefs!!.getString("amazon_exe_${game.productId}", null)
        if (exe != null) {
            StarLaunchBridge.addToLauncher(this, game.title, exe, game.artUrl, "amazon", true)
        }
    }

    private fun cancelDownload(game: AmazonGame) {
        cancelTokens[game.productId]?.invoke()
        cancelTokens.remove(game.productId)
    }

    private fun openDetailScreen(game: AmazonGame) {
        val intent = Intent(this, AmazonGameDetailActivity::class.java).apply {
            putExtra("product_id", game.productId)
            putExtra("entitlement_id", game.entitlementId)
            putExtra("title", game.title)
            putExtra("developer", game.developer)
            putExtra("publisher", game.publisher)
            putExtra("art_url", game.artUrl)
            putExtra("product_sku", game.productSku)
        }
        gameDetailLauncher.launch(intent)
    }

    // ── Apply filter ──────────────────────────────────────────────────────

    private fun applyFilter() {
        val result = if (searchQuery.isBlank()) allGames
        else allGames.filter { it.title.contains(searchQuery, ignoreCase = true) }
        expandedProductId = null
        val visibleGames = result

        if (visibleGames.isEmpty()) {
            val q = searchQuery.trim()
            setSync(
                if (q.isEmpty()) "Your Amazon library is empty"
                else "No results for \"$q\"",
                false,
            )
        }
    }

    // ── Download ──────────────────────────────────────────────────────────

    private fun startGameDownload(game: AmazonGame) {
        val cancelled = AtomicBoolean(false)
        cancelTokens[game.productId] = { cancelled.set(true) }

        val idx = downloadStates.indexOfFirst { it.first == game.productId }
        if (idx >= 0) {
            downloadStates[idx] = game.productId to DownloadState(
                progress = 0, statusText = "0%",
                isVisible = true, isCancelling = false,
            )
        } else {
            downloadStates.add(game.productId to DownloadState(
                progress = 0, statusText = "0%",
                isVisible = true, isCancelling = false,
            ))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val token = AmazonCredentialStore.getValidAccessToken(this@AmazonGamesActivity)
            if (token == null) {
                withContext(Dispatchers.Main) {
                    resultBarMsg = "Login required"
                }
                removeDownloadState(game.productId)
                return@launch
            }

            var sanitized = game.title.replace("[^a-zA-Z0-9 \\-_]".toRegex(), "").trim()
            if (sanitized.isEmpty()) sanitized = "game_${game.productId.hashCode()}"
            val installDir = File(File(filesDir, "imagefs/Amazon"), sanitized)

            prefs!!.edit().putString(
                "amazon_dir_${game.productId}",
                installDir.absolutePath,
            ).apply()

            // Publish this list-flow download into the cross-store Download Manager registry \u2014
            // same shim + same key as the detail screen, so a live DOWNLOADING row shows here too
            // and doesn't double-list against the later seeded Library row. One honest install bar.
            StoreDownloadHooks.registerDownload(
                store = Store.AMAZON,
                id = game.productId,
                name = game.title,
                cover = game.artUrl,
                supportsPause = false,
                installTotal = prefs!!.getLong("amazon_size_${game.productId}", 0L),
                cancel = { cancelled.set(true) },
            )

            val ok = AmazonDownloadManager.install(
                this@AmazonGamesActivity, game, token, installDir,
                { dl, total, file ->
                    if (cancelled.get()) return@install
                    val pct = if (total > 0) (dl * 100L / total).toInt() else 0
                    val name = if (!file.isNullOrEmpty()) file else "Downloading\u2026"
                    StoreDownloadHooks.tick(
                        store = Store.AMAZON,
                        id = game.productId,
                        pct = pct,
                        installDone = dl,
                        installTotal = total,
                    )
                    updateDownloadState(game.productId, pct, name)
                },
                { cancelled.get() },
            )

            if (cancelled.get()) {
                StoreDownloadHooks.markCancelled(Store.AMAZON, game.productId)
                removeDownloadState(game.productId)
                return@launch
            }
            if (!ok) {
                withContext(Dispatchers.Main) {
                    resultBarMsg = "Error: Download failed"
                }
                StoreDownloadHooks.markFailed(Store.AMAZON, game.productId, "Download failed")
                removeDownloadState(game.productId)
                return@launch
            }

            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(installDir, exeFiles)

            if (exeFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    resultBarMsg = "Error: No executable found after install"
                }
                StoreDownloadHooks.markFailed(Store.AMAZON, game.productId, "No executable found")
                removeDownloadState(game.productId)
                return@launch
            }

            val lowerTitle = game.title.lowercase()
            exeFiles.sortWith { a, b ->
                AmazonLaunchHelper.scoreExe(b, lowerTitle) -
                    AmazonLaunchHelper.scoreExe(a, lowerTitle)
            }

            // Completion NEVER shows a picker: auto-record the best-scored exe (list already
            // sorted best-first) and finalize, regardless of exe count. This finalizes the install
            // even when this screen isn't foreground (a queued dialog on a stopped Activity used to
            // wedge the DL-manager card at 100%). Exe choice, if there are several, happens at
            // Launch instead (detail page's onLaunchClicked / a Launch-time picker).
            val path = exeFiles[0].absolutePath
            prefs!!.edit().putString("amazon_exe_${game.productId}", path).apply()
            onDownloadComplete(game.productId, path)
        }
    }

    private fun onDownloadComplete(productId: String, exePath: String) {
        cancelTokens.remove(productId)
        val idx = downloadStates.indexOfFirst { it.first == productId }
        if (idx >= 0) {
            downloadStates[idx] = productId to DownloadState(
                progress = 100, statusText = "Installed",
                isVisible = false, isComplete = true,
            )
        }
        prefs!!.edit().putString("amazon_exe_$productId", exePath).apply()
        // Terminal success → INSTALLED (persisted to the durable library).
        prefs!!.getString("amazon_dir_$productId", null)?.let { dir ->
            StoreDownloadHooks.markInstalled(
                store = Store.AMAZON,
                id = productId,
                installPath = dir,
                bytes = prefs!!.getLong("amazon_size_$productId", 0L),
            )
        }
        refreshFromCache()
    }

    private fun updateDownloadState(productId: String, progress: Int, statusText: String) {
        val idx = downloadStates.indexOfFirst { it.first == productId }
        if (idx >= 0) {
            downloadStates[idx] = productId to downloadStates[idx].second.copy(
                progress = progress, statusText = statusText,
            )
        }
    }

    private fun removeDownloadState(productId: String) {
        cancelTokens.remove(productId)
        downloadStates.removeAll { it.first == productId }
    }

    // ── Uninstall ─────────────────────────────────────────────────────────

    private fun uninstallGame(game: AmazonGame) {
        val installedDir = prefs!!.getString("amazon_dir_${game.productId}", null)
        if (installedDir == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            deleteDir(File(installedDir))
            // Purge the FULL native install record via the canonical helper, and clear the DL
            // manager's registry/library row — so uninstalling from the list keeps the detail
            // page and the cross-store Download Manager in sync (previously it left the DL card
            // and stale manifest/size prefs behind).
            AmazonInstallState.purge(applicationContext, game.productId)
            StoreDownloadHooks.markUninstalled(Store.AMAZON, game.productId)
            withContext(Dispatchers.Main) {
                refreshFromCache()
                resultBarMsg = "${game.title} uninstalled"
            }
        }
    }

    // ── Exe picker ────────────────────────────────────────────────────────

    private fun openExePicker(game: AmazonGame) {
        val installedDir = prefs!!.getString("amazon_dir_${game.productId}", null)
        if (installedDir == null) {
            resultBarMsg = "Install directory not found"
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val dir = File(installedDir)
            if (!dir.isDirectory) {
                withContext(Dispatchers.Main) {
                    resultBarMsg = "Install directory not found"
                }
                return@launch
            }
            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(dir, exeFiles)
            if (exeFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    resultBarMsg = "No .exe files found"
                }
                return@launch
            }
            val candidates = exeFiles.map { it.absolutePath }
            withContext(Dispatchers.Main) {
                exePickerData = ExePickerData(
                    candidates = candidates,
                    onSelected = { selected ->
                        if (!selected.isNullOrEmpty()) {
                            prefs!!.edit()
                                .putString("amazon_exe_${game.productId}", selected)
                                .apply()
                            refreshFromCache()
                            resultBarMsg = "Exe set: ${File(selected).name}"
                        }
                    },
                )
            }
        }
    }

    // ── Free space check ──────────────────────────────────────────────────

    private fun getFreeBytes(): Long {
        return try {
            val base = File(File(filesDir, "Amazon"), "_check")
            val parent = base.parentFile
            parent?.mkdirs()
            val sf = android.os.StatFs(
                (parent ?: cacheDir).absolutePath,
            )
            sf.availableBlocksLong * sf.blockSizeLong
        } catch (_: Exception) {
            -1L
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────

    private fun saveCachedGames(games: List<AmazonGame>) {
        try {
            val arr = JSONArray()
            for (g in games) {
                val j = JSONObject()
                j.put("productId", g.productId)
                j.put("entitlementId", g.entitlementId)
                j.put("title", g.title)
                j.put("artUrl", g.artUrl)
                j.put("heroUrl", g.heroUrl)
                j.put("developer", g.developer)
                j.put("publisher", g.publisher)
                j.put("productSku", g.productSku)
                j.put("isInstalled", g.isInstalled)
                j.put("installPath", g.installPath)
                j.put("versionId", g.versionId)
                j.put("downloadSize", g.downloadSize)
                j.put("installSize", g.installSize)
                AmazonLibrarySync.putMedia(j, g)
                arr.put(j)
            }
            prefs!!.edit().putString(CACHE_KEY, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveCachedGames failed", e)
        }
    }

    private fun loadCachedGames(): List<AmazonGame>? {
        try {
            val json = prefs!!.getString(CACHE_KEY, null) ?: return null
            val arr = JSONArray(json)
            val games = mutableListOf<AmazonGame>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val g = AmazonGame()
                g.productId = j.optString("productId", "")
                g.entitlementId = j.optString("entitlementId", "")
                g.title = j.optString("title", "")
                g.artUrl = j.optString("artUrl", "")
                g.heroUrl = j.optString("heroUrl", "")
                g.developer = j.optString("developer", "")
                g.publisher = j.optString("publisher", "")
                g.productSku = j.optString("productSku", "")
                g.isInstalled = j.optBoolean("isInstalled", false)
                g.installPath = j.optString("installPath", "")
                g.versionId = j.optString("versionId", "")
                g.downloadSize = j.optLong("downloadSize", 0L)
                g.installSize = j.optLong("installSize", 0L)
                AmazonLibrarySync.readMedia(j, g)
                games.add(g)
            }
            return games
        } catch (e: Exception) {
            Log.e(TAG, "loadCachedGames failed", e)
            return null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun setSync(msg: String, isError: Boolean) {
        statusText = msg
    }

    private fun enableRefresh() {
        refreshEnabled = true
    }

    companion object {
        private const val TAG = "BH_AMAZON"
        private const val PREFS_NAME = "bh_amazon_prefs"
        private const val CACHE_KEY = "amazon_library_cache"
        private const val VIEW_MODE_KEY = "amazon_view_mode"

        fun viewModeIcon(mode: String): String = when (mode) {
            "grid" -> "\u25A6"
            "poster" -> "\u2630"
            else -> "\u229E"
        }

        fun formatBytes(bytes: Long): String = when {
            bytes < 0 -> "Unknown"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            bytes < 1024L * 1024L * 1024L ->
                "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else ->
                "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }

        fun deleteDir(dir: File) {
            if (dir == null || !dir.exists()) return
            val children = dir.listFiles()
            if (children != null) for (c in children) deleteDir(c)
            dir.delete()
        }
    }
}

private data class DownloadState(
    val progress: Int = 0,
    val statusText: String = "",
    val isVisible: Boolean = false,
    val isCancelling: Boolean = false,
    val isComplete: Boolean = false,
)

private data class ExePickerData(
    val candidates: List<String>,
    val onSelected: (String) -> Unit,
)

// ─── Composable Screen ─────────────────────────────────────────────────────

@Composable
private fun AmazonGamesScreen(
    games: List<AmazonGame>,
    searchQuery: String,
    viewMode: String,
    statusText: String,
    gamesVisible: Boolean,
    refreshEnabled: Boolean,
    downloadStates: List<Pair<String, DownloadState>>,
    expandedProductId: String?,
    onSearchChange: (String) -> Unit,
    onViewModeToggle: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExpansion: (AmazonGame) -> Unit,
    onBack: () -> Unit,
    onCardClick: (AmazonGame) -> Unit,
    onArrowClick: () -> Unit,
    onInstallOrLaunch: (AmazonGame) -> Unit,
    onLaunchAdd: (AmazonGame) -> Unit,
    onCancel: (AmazonGame) -> Unit,
    onLongPress: (AmazonGame) -> Unit,
    onOpenDetail: (AmazonGame) -> Unit,
) {
    val filteredGames = remember(games, searchQuery) {
        if (searchQuery.isBlank()) games
        else games.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) { Text("\u2190", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }

            Text(
                text = "Amazon Games",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )

            Button(
                onClick = onViewModeToggle,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) {
                Text(
                    text = AmazonGamesActivity.viewModeIcon(viewMode),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.width(6.dp))

            Button(
                onClick = onRefresh,
                enabled = refreshEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (refreshEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            ) { Text("\u21BA", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }

            // \u2B07 cross-store Download Manager (global active-count badge), trailing the
            // header actions like Steam's list header.
            DownloadsButton()
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search games\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(0.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // Status bar
        val statusColor = when {
            statusText.startsWith("Error") || statusText.startsWith("Not logged in")
                || statusText.startsWith("Token refresh") || statusText.startsWith("No games") ->
                MaterialTheme.colorScheme.error
            statusText.contains("game") && (statusText.contains("tap") || statusText.contains("cached")) ->
                Color(0xFF81C784) // semantic "library ready" green
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = statusText,
            fontSize = 13.sp,
            color = statusColor,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // Game list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (gamesVisible) {
                when (viewMode) {
                    "list" -> GameListView(
                        games = filteredGames,
                        downloadStates = downloadStates,
                        expandedProductId = expandedProductId,
                        onCardClick = onCardClick,
                        onArrowClick = onArrowClick,
                        onInstallOrLaunch = onInstallOrLaunch,
                        onLaunchAdd = onLaunchAdd,
                        onCancel = onCancel,
                        onOpenDetail = onOpenDetail,
                    )
                    "grid" -> GameGridView(
                        games = filteredGames,
                        tileHeightDp = 105,
                        columns = 5,
                        downloadStates = downloadStates,
                        expandedProductId = expandedProductId,
                        onToggleExpansion = onToggleExpansion,
                        onInstallOrLaunch = onInstallOrLaunch,
                        onLaunchAdd = onLaunchAdd,
                        onCancel = onCancel,
                        onLongPress = onOpenDetail,
                    )
                    "poster" -> GameGridView(
                        games = filteredGames,
                        tileHeightDp = 176,
                        columns = 5,
                        downloadStates = downloadStates,
                        expandedProductId = expandedProductId,
                        onToggleExpansion = onToggleExpansion,
                        onInstallOrLaunch = onInstallOrLaunch,
                        onLaunchAdd = onLaunchAdd,
                        onCancel = onCancel,
                        onLongPress = onOpenDetail,
                    )
                }
            }

            if (!gamesVisible) {
                Text(
                    text = if (statusText.contains("Error") || statusText.contains("No games"))
                        statusText else "Loading\u2026",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
    }
}

// ─── List View ──────────────────────────────────────────────────────────────

@Composable
private fun GameListView(
    games: List<AmazonGame>,
    downloadStates: List<Pair<String, DownloadState>>,
    expandedProductId: String?,
    onCardClick: (AmazonGame) -> Unit,
    onArrowClick: () -> Unit,
    onInstallOrLaunch: (AmazonGame) -> Unit,
    onLaunchAdd: (AmazonGame) -> Unit,
    onCancel: (AmazonGame) -> Unit,
    onOpenDetail: (AmazonGame) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(games, key = { it.productId }) { game ->
            GameCard(
                game = game,
                isExpanded = expandedProductId == game.productId,
                downloadState = downloadStates.find { it.first == game.productId }?.second,
                onCardClick = { onCardClick(game) },
                onArrowClick = onArrowClick,
                onInstallOrLaunch = { onInstallOrLaunch(game) },
                onLaunchAdd = { onLaunchAdd(game) },
                onCancel = { onCancel(game) },
                onOpenDetail = { onOpenDetail(game) },
            )
        }
    }
}

@Composable
private fun GameCard(
    game: AmazonGame,
    isExpanded: Boolean,
    downloadState: DownloadState?,
    onCardClick: () -> Unit,
    onArrowClick: () -> Unit,
    onInstallOrLaunch: () -> Unit,
    onLaunchAdd: () -> Unit,
    onCancel: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bh_amazon_prefs", 0) }
    val isInstalled = prefs.getString("amazon_exe_${game.productId}", null) != null
    val updateAvailable = isInstalled &&
        game.versionId != null && game.versionId.endsWith("_UPDATE_AVAILABLE")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onCardClick)
            .padding(10.dp),
    ) {
        // Top row (always visible)
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover art
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(game.artUrl.ifEmpty { game.heroUrl })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 60.dp, height = 60.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = game.title,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isInstalled) {
                        Text(
                            text = " \u2713",
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50), // semantic installed-green
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (game.developer.isNotEmpty() || game.publisher.isNotEmpty()) {
                    val sub = when {
                        game.developer.isEmpty() -> game.publisher
                        game.publisher.isEmpty() -> game.developer
                        else -> "${game.developer}  \u00B7  ${game.publisher}"
                    }
                    Text(
                        text = sub,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = if (isExpanded) "\u25B2" else "\u25BC",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onArrowClick).padding(8.dp, 0.dp, 0.dp, 0.dp),
            )
        }

        // Expanded section
        if (isExpanded) {
            Spacer(Modifier.height(6.dp))

            if (game.developer.isNotEmpty() || game.publisher.isNotEmpty()) {
                val meta = when {
                    game.developer.isEmpty() -> game.publisher
                    game.publisher.isEmpty() -> game.developer
                    else -> "${game.developer} \u00B7 ${game.publisher}"
                }
                Text(
                    text = meta,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            val checkText = if (updateAvailable) "\u2713 Installed \u2014 Update Available"
            else "\u2713 Installed"
            // semantic: amber = update-available, installed-green = up to date
            val checkColor = if (updateAvailable) Color(0xFFFFAA00) else Color(0xFF4CAF50)
            if (isInstalled) {
                Text(
                    text = checkText,
                    fontSize = 10.sp,
                    color = checkColor,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Progress
            val ds = downloadState
            if (ds != null && ds.isVisible) {
                LinearProgressIndicator(
                    progress = { ds.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (!ds.isComplete) {
                    Text(
                        text = "${ds.progress}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (ds.isCancelling) {
                    Text(
                        text = "Cancelling\u2026",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    Text(
                        text = ds.statusText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val isCancelBtn = ds != null && ds.isVisible
            Button(
                onClick = {
                    if (ds != null && ds.isVisible) {
                        onCancel()
                    } else if (isInstalled) {
                        onLaunchAdd()
                    } else {
                        onInstallOrLaunch()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCancelBtn) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = when {
                        ds != null && ds.isVisible -> "Cancel"
                        isInstalled -> "Add to Launcher"
                        else -> "Install"
                    },
                    color = if (isCancelBtn) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// ─── Grid / Poster View ─────────────────────────────────────────────────────

@Composable
private fun GameGridView(
    games: List<AmazonGame>,
    tileHeightDp: Int,
    columns: Int,
    downloadStates: List<Pair<String, DownloadState>>,
    expandedProductId: String?,
    onToggleExpansion: (AmazonGame) -> Unit,
    onInstallOrLaunch: (AmazonGame) -> Unit,
    onLaunchAdd: (AmazonGame) -> Unit,
    onCancel: (AmazonGame) -> Unit,
    onLongPress: (AmazonGame) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(games, key = { it.productId }) { game ->
            GameGridTile(
                game = game,
                tileHeightDp = tileHeightDp,
                downloadState = downloadStates.find { it.first == game.productId }?.second,
                isExpanded = game.productId == expandedProductId,
                onToggleExpansion = { onToggleExpansion(game) },
                onInstallOrLaunch = { onInstallOrLaunch(game) },
                onLaunchAdd = { onLaunchAdd(game) },
                onCancel = { onCancel(game) },
                onLongPress = { onLongPress(game) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameGridTile(
    game: AmazonGame,
    tileHeightDp: Int,
    downloadState: DownloadState?,
    isExpanded: Boolean = false,
    onToggleExpansion: () -> Unit = {},
    onInstallOrLaunch: () -> Unit,
    onLaunchAdd: () -> Unit,
    onCancel: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bh_amazon_prefs", 0) }
    val isInstalled = prefs.getString("amazon_exe_${game.productId}", null) != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onToggleExpansion,
                onLongClick = onLongPress,
            ),
    ) {
        // Art with title overlay
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(game.artUrl.ifEmpty { game.heroUrl })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileHeightDp.dp),
                contentScale = ContentScale.Crop,
            )

            // Title bar at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x44000000),
                                Color(0xEE000000),
                            ),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = game.title,
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isInstalled) {
                    Text(
                        text = " \u2713",
                        fontSize = 11.sp,
                        color = Color(0xFF66BB6A), // semantic installed-green
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Action row (shown on tap)
        if (isExpanded) {
            val isCancelBtn = downloadState != null && downloadState.isVisible
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                val ds = downloadState
                if (ds != null && ds.isVisible) {
                    LinearProgressIndicator(
                        progress = { ds.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                Button(
                    onClick = {
                        if (ds != null && ds.isVisible) onCancel()
                        else if (isInstalled) onLaunchAdd()
                        else onInstallOrLaunch()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCancelBtn) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = when {
                            ds != null && ds.isVisible -> "Cancel"
                            isInstalled -> "Add to Launcher"
                            else -> "Install"
                        },
                        color = if (isCancelBtn) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// ─── Install Confirm Dialog ─────────────────────────────────────────────────

@Composable
private fun InstallConfirmDialog(
    game: AmazonGame,
    freeBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var sizeLabel by remember { mutableStateOf("Game size:  Fetching\u2026") }
    val context = LocalContext.current

    LaunchedEffect(game.productId) {
        if (game.installSize > 0) {
            sizeLabel = "Game size:  ${AmazonGamesActivity.formatBytes(game.installSize)}"
        } else {
            withContext(Dispatchers.IO) {
                var size = 0L
                try {
                    val token = AmazonCredentialStore.getValidAccessToken(context)
                    if (token != null) {
                        val spec = AmazonApiClient.getGameDownload(token, game.entitlementId)
                        if (spec != null && spec.downloadUrl.isNotEmpty()) {
                            val manifestUrl = AmazonApiClient.appendPath(spec.downloadUrl, "manifest.proto")
                            val manifestBytes = AmazonApiClient.getBytes(manifestUrl, token)
                            if (manifestBytes != null) {
                                val manifest = AmazonManifest.parse(manifestBytes)
                                size = manifest.totalInstallSize
                                game.installSize = size
                            }
                        }
                    }
                } catch (_: Exception) {}
                val finalSize = size
                withContext(Dispatchers.Main) {
                    sizeLabel = "Game size:  ${if (finalSize > 0) AmazonGamesActivity.formatBytes(finalSize) else "Unknown"}"
                }
            }
        }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install ${game.title}?") },
        text = {
            Column {
                Text(
                    text = sizeLabel,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Available storage:  ${AmazonGamesActivity.formatBytes(freeBytes)}",
                    fontSize = 14.sp,
                    color = Color(0xFF88CC88), // semantic storage-ok green
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Install") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─── Game Detail Dialog ─────────────────────────────────────────────────────

@Composable
private fun GameDetailDialog(
    game: AmazonGame,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onSetExe: () -> Unit,
    onUninstall: () -> Unit,
) {
    val installedExe = prefs.getString("amazon_exe_${game.productId}", null)
    val installedDir = prefs.getString("amazon_dir_${game.productId}", null)

    val msg = buildString {
        if (game.developer.isNotEmpty()) appendLine("Developer: ${game.developer}")
        if (game.publisher.isNotEmpty()) appendLine("Publisher: ${game.publisher}")
        append("ID: ${game.shortId()}")
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(game.title) },
        text = {
            Column {
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (installedExe != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "\n.exe: ${File(installedExe).name}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onSetExe,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) { Text("Set .exe\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (installedDir != null) {
                TextButton(onClick = onUninstall) { Text("Uninstall") }
            }
        },
    )
}

// ─── Exe Picker Dialog ──────────────────────────────────────────────────────

@Composable
private fun ExePickerDialog(
    candidates: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select game executable") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
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
