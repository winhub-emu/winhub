package com.winhub.hernandez.store

import android.content.Intent
import android.os.Bundle
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreDownloadHooks
import com.winhub.hernandez.ui.theme.WinlatorTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.function.Consumer

class GogGamesActivity : ComponentActivity() {

    companion object {
        private const val TAG = "BH_GOG"
        private const val CACHE_KEY = "gog_library_cache"
        private const val VIEW_MODE_KEY = "view_mode"
        private const val REQ_GAME_DETAIL = 1001
        private const val SGDB_KEY = "cf89227f12c773bb1117b6b109ae1659"

        // gap #8 (Option A): incremental/throttle bookkeeping on the existing prefs cache.
        // `gog_library_synced_at` = millis of the last HEAVY (all-owned-ids) sync. A non-forced
        // open within THROTTLE_MS re-fetches only NEW ids (incremental); a forced refresh or a
        // stale (>15 min) open re-fetches every owned id. Vertical covers live in a per-id pref
        // so backfill stays idempotent (never re-hit gamesdb for a game we already covered).
        private const val LAST_SYNC_KEY = "gog_library_synced_at"
        private const val THROTTLE_MS = 15L * 60L * 1000L
        private const val VCOVER_PREFIX = "gog_vcover_"

        private fun httpGet(url: String, token: String?): String? {
            return try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                if (conn.responseCode != 200) { conn.disconnect(); return null }
                val sb = StringBuilder()
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { br ->
                    var line: String?
                    while (br.readLine().also { line = it } != null) sb.append(line)
                }
                conn.disconnect()
                sb.toString()
            } catch (_: Exception) { null }
        }

        private fun sgdbFetchCover(title: String): String {
            return try {
                val encoded = URLEncoder.encode(title, "UTF-8")
                val searchJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/search/autocomplete/$encoded", SGDB_KEY,
                ) ?: return ""
                val results = JSONObject(searchJson).optJSONArray("data")
                if (results == null || results.length() == 0) return ""
                val gameId = results.getJSONObject(0).getInt("id")

                val gridsJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/grids/game/$gameId?dimensions=600x900&mimes=image/jpeg,image/png&limit=1",
                    SGDB_KEY,
                ) ?: return ""
                val grids = JSONObject(gridsJson).optJSONArray("data")
                if (grids == null || grids.length() == 0) return ""
                grids.getJSONObject(0).optString("url", "")
            } catch (_: Exception) { "" }
        }

        /**
         * gap #8 behavior 4: fetch a real GOG 2:3 vertical cover from the UNAUTHENTICATED
         * gamesdb.gog.com service (no Bearer token — verified live). Two hops:
         *   1. external_releases/{gogProductId}  → internal gamesdb `game_id`
         *   2. games/{game_id}                   → `vertical_cover.url_format` template
         * The template's `{formatter}` (empty = full-res) and `{ext}` (webp) tokens are substituted.
         * Fail-soft: any miss / non-200 / missing field returns null so the caller falls back to the
         * existing SGDB→icon cover chain. gamesdb ids are internal & mutable, so we always map via the
         * stable GOG product id and never cache the intermediate game_id as a key.
         */
        private fun fetchVerticalCover(productId: String): String? {
            return try {
                val extJson = httpGet(
                    "https://gamesdb.gog.com/platforms/gog/external_releases/$productId", null,
                ) ?: return null
                val gameId = JSONObject(extJson).optString("game_id", "")
                if (gameId.isEmpty()) return null

                val gameJson = httpGet("https://gamesdb.gog.com/games/$gameId", null) ?: return null
                val vc = JSONObject(gameJson).optJSONObject("vertical_cover") ?: return null
                val fmt = vc.optString("url_format", "")
                if (fmt.isEmpty()) return null
                fmt.replace("{formatter}", "").replace("{ext}", "webp")
            } catch (_: Exception) { null }
        }
    }

    private lateinit var prefs: android.content.SharedPreferences

    private var allGames by mutableStateOf<List<GogGame>>(emptyList())
    private var viewMode by mutableStateOf("list")
    private var syncText by mutableStateOf("Loading GOG library\u2026")
    private var syncTextColor by mutableIntStateOf(0xFFCCCCCC.toInt())
    private var searchQuery by mutableStateOf("")
    private var scrollVisible by mutableStateOf(false)
    private var isSyncing by mutableStateOf(false)
    private var expandedGameId by mutableStateOf<String?>(null)
    private var showExePicker by mutableStateOf<ExePickerDataGog?>(null)
    private var showInstallDialog by mutableStateOf<InstallDialogData?>(null)
    private var showDetailDialog by mutableStateOf<DetailDialogData?>(null)

    // Themed auto-dismiss bar — system Toasts render as an unreadable black box on this ROM
    // (targetSDK 28); reuse the shared UninstallResultBar for readable feedback.
    private var resultBarMsg by mutableStateOf<String?>(null)
    private val downloadStates = mutableStateMapOf<String, GameDownloadState>()

    private val detailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == GogGameDetailActivity.RESULT_REFRESH) {
            applyFilter(searchQuery)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("bh_gog_prefs", 0)
        viewMode = prefs.getString(VIEW_MODE_KEY, "grid") ?: "grid"

        // Cross-store Download Manager (Phase B): init the registry + seed/self-heal the installed
        // GOG library here (idempotent), mirroring AmazonGamesActivity.
        DownloadRegistry.init(this)
        GogLibrarySync.seed(this)

        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                GogGamesScreen(
                    syncText = syncText,
                    syncTextColor = syncTextColor,
                    searchQuery = searchQuery,
                    scrollVisible = scrollVisible,
                    isSyncing = isSyncing,
                    viewMode = viewMode,
                    games = allGames,
                    expandedGameId = expandedGameId,
                    downloadStates = downloadStates,
                    onBack = { finish() },
                    onViewToggle = {
                        viewMode = when (viewMode) {
                            "list" -> "grid"
                            "grid" -> "poster"
                            else -> "list"
                        }
                        prefs.edit().putString(VIEW_MODE_KEY, viewMode).apply()
                        expandedGameId = null
                        applyFilter(searchQuery)
                    },
                    // Refresh button = FORCED full re-sync: bypasses the 15-min throttle and
                    // re-fetches every owned id (refreshes metadata/covers), not just new ids.
                    onRefresh = { startSync(showProgress = true, force = true) },
                    onSearchChange = { applyFilter(it) },
                    onGameClick = { game -> onGameClick(game) },
                    onGameLongClick = { game -> openDetailScreen(game) },
                    onExpandToggle = { gameId ->
                        expandedGameId = if (expandedGameId == gameId) null else gameId
                    },
                    onInstallClick = { game -> onInstallClick(game) },
                    onCancelClick = { game -> onCancelClick(game) },
                    onAddToLauncher = { game -> onAddToLauncher(game) },
                )

                showExePicker?.let { data ->
                    ExePickerDialogGog(
                        candidates = data.candidates,
                        onSelected = { path ->
                            data.onSelected(path)
                            showExePicker = null
                        },
                        onDismiss = { showExePicker = null },
                    )
                }

                showInstallDialog?.let { data ->
                    InstallConfirmDialog(
                        game = data.game,
                        freeBytes = data.freeBytes,
                        gameSize = data.gameSize,
                        onConfirm = {
                            showInstallDialog = null
                            data.onConfirm()
                        },
                        onDismiss = { showInstallDialog = null },
                    )
                }

                showDetailDialog?.let { data ->
                    DetailDialog(
                        game = data.game,
                        onDismiss = { showDetailDialog = null },
                        onSetExe = { data.onSetExe() },
                        onUninstall = { data.onUninstall() },
                        onCopyToDownloads = { data.onCopyToDownloads() },
                    )
                }
                resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
            
                }}
        }

        val cached = loadCachedGames()
        if (cached != null && cached.isNotEmpty()) {
            allGames = cached
            applyFilter("")
            scrollVisible = true
            val cn = cached.size
            setSync("$cn game${if (cn == 1) "" else "s"} \u2014 cached  \u2022  tap \u21ba to refresh", syncOk = true)
        }
        startSync(cached == null || cached.isEmpty())
    }

    override fun onResume() {
        super.onResume()
        applyFilter(searchQuery)
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    private fun startSync(showProgress: Boolean, force: Boolean = false) {
        if (isSyncing) return
        isSyncing = true
        if (showProgress) setSync("Loading GOG library\u2026")
        lifecycleScope.launch(Dispatchers.IO) {
            syncLibrary(showProgress, force)
            withContext(Dispatchers.Main) { isSyncing = false }
        }
    }

    private suspend fun syncLibrary(showProgress: Boolean, force: Boolean) {
        try {
            if (showProgress) withContext(Dispatchers.Main) { setSync("Checking token\u2026") }

            var token = prefs.getString("access_token", null)
            if (token == null) { withContext(Dispatchers.Main) { setSync("Not logged in") }; return }

            val loginTime = prefs.getInt("bh_gog_login_time", 0)
            val expiresIn = prefs.getInt("bh_gog_expires_in", 3600)
            val nowSec = System.currentTimeMillis() / 1000L
            if (loginTime == 0 || nowSec >= loginTime + expiresIn) {
                if (showProgress) withContext(Dispatchers.Main) { setSync("Refreshing token\u2026") }
                val newToken = GogTokenRefresh.refresh(this@GogGamesActivity)
                if (newToken == null) {
                    withContext(Dispatchers.Main) { setSync("Session expired \u2014 please sign in again") }
                    return
                }
                token = newToken
            }

            if (showProgress) withContext(Dispatchers.Main) { setSync("Fetching game list\u2026") }

            val gamesJson = httpGet("https://embed.gog.com/user/data/games", token)
            if (gamesJson == null) { withContext(Dispatchers.Main) { setSync("Failed to fetch library") }; return }

            val ids = mutableListOf<String>()
            try {
                val obj = JSONObject(gamesJson)
                val ownedArr = obj.optJSONArray("owned")
                if (ownedArr != null) {
                    for (i in 0 until ownedArr.length()) {
                        val id = ownedArr.getLong(i).toString()
                        if (id != "1801418160") ids.add(id)
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { setSync("Error parsing library") }; return
            }

            if (ids.isEmpty()) { withContext(Dispatchers.Main) { setSync("No games found in library") }; return }

            // \u2500\u2500 gap #8: incremental diff + 15-min throttle + prune \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
            // `ids` above is the COMPLETE owned-id set \u2014 the diff source.
            val ownedSet = ids.toHashSet()
            val cachedList = loadCachedGames() ?: emptyList()

            val lastSync = prefs.getLong(LAST_SYNC_KEY, 0L)
            val stale = System.currentTimeMillis() - lastSync >= THROTTLE_MS
            // HEAVY = re-fetch every owned id (refresh metadata/covers). Runs only when forced, on the
            // first sync (empty cache), or once the 15-min throttle lapses. Otherwise we run the cheap
            // owned-id diff and fetch ONLY new ids, so newly-purchased games still appear immediately.
            val heavy = force || cachedList.isEmpty() || stale
            val cachedIds = cachedList.mapTo(HashSet()) { it.gameId }
            val idsToFetch = if (heavy) ids else ids.filter { it !in cachedIds }

            // Seed the merged list from cached entries we are NOT re-fetching this pass: keeps them
            // with no network hit and resilient to transient errors, and prunes revoked games from
            // the LIST only (install-state prefs stay owned by GogInstallState \u2014 never purged here).
            // Re-fetched ids are intentionally NOT seeded \u2014 they come purely from fetch results so the
            // filter (and drop-on-failure) applies to them, clearing any stale/junk cache entry.
            val fetchSet = idsToFetch.toHashSet()
            val merged = LinkedHashMap<String, GogGame>()
            for (g in cachedList) if (g.gameId in ownedSet && g.gameId !in fetchSet) merged[g.gameId] = g

            if (idsToFetch.isEmpty()) {
                // Throttled with no new purchases: still commit the (possibly pruned) list so revoked
                // games drop out, but do NOT advance gog_library_synced_at (no heavy pass happened).
                saveCachedGames(merged.values.toList())
                withContext(Dispatchers.Main) {
                    val list = merged.values.toList()
                    if (list.isEmpty()) setSync("No compatible games found")
                    else {
                        allGames = list.sortedBy { it.title.lowercase() }
                        applyFilter(searchQuery)
                        scrollVisible = true
                        val fn = list.size
                        setSync("$fn game${if (fn == 1) "" else "s"} \u2014 up to date", syncOk = true)
                    }
                }
                return
            }

            if (showProgress) withContext(Dispatchers.Main) {
                setSync("Syncing ${idsToFetch.size} game${if (idsToFetch.size == 1) "" else "s"}\u2026")
            }

            val finalToken = token
            val pool = Executors.newFixedThreadPool(5)
            val futures = idsToFetch.map { id ->
                pool.submit(java.util.concurrent.Callable<GogGame?> { fetchGame(id, finalToken) })
            }
            pool.shutdown()

            for (f in futures) {
                val g = try { f.get() } catch (_: Exception) { null } ?: continue
                merged[g.gameId] = g   // merge new / refreshed entries over existing
            }

            saveDlcBuffer()
            val finalList = merged.values.toList()
            saveCachedGames(finalList)
            // Advance the throttle clock only after an actual heavy (all-owned) pass, so the 15-min
            // window is measured from the last full refresh \u2014 not from a cheap incremental new-id pass.
            if (heavy) prefs.edit().putLong(LAST_SYNC_KEY, System.currentTimeMillis()).apply()

            withContext(Dispatchers.Main) {
                if (finalList.isEmpty()) {
                    setSync("No compatible games found")
                } else {
                    allGames = finalList.sortedBy { it.title.lowercase() }
                    applyFilter(searchQuery)
                    scrollVisible = true
                    val fn = finalList.size
                    setSync("$fn game${if (fn == 1) "" else "s"} \u2014 tap a card to install", syncOk = true)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { setSync("Error: ${e.message}") }
        }
    }

    private fun fetchGame(id: String, token: String): GogGame? {
        try {
            val productJson = httpGet(
                "https://api.gog.com/products/$id?expand=downloads,description", token,
            )
            if (productJson == null) return null

            val prod = JSONObject(productJson)
            if (prod.optBoolean("is_secret", false)) return null
            val gameType = prod.optString("game_type", "")
            if ("dlc" == gameType) {
                storeDlcInBuffer(id, prod)
                return null
            }
            // gap #8 filter: drop non-game product types (movie / goodie / etc). Only `game` and
            // `pack` (bundles) are kept; an EMPTY game_type is kept too (some products omit it and
            // we must not over-filter an owned playable title).
            if (gameType.isNotEmpty() && gameType != "game" && gameType != "pack") return null

            val titleObj = prod.optJSONObject("title")
            var titleStr = titleObj?.optString("*")
            if (titleStr == null) titleStr = prod.optString("title")
            if (titleStr.isNullOrEmpty()) return null

            var imageUrl = sgdbFetchCover(titleStr)
            if (imageUrl.isEmpty()) {
                val images = prod.optJSONObject("images")
                imageUrl = images?.optString("icon", "") ?: ""
                if (imageUrl.isEmpty()) imageUrl = images?.optString("background", "") ?: ""
            }

            val descObj = prod.optJSONObject("description")
            val desc = descObj?.optString("lead", "") ?: ""

            val company = prod.optJSONObject("developers")
            val developer = company?.optString("name", "") ?: prod.optString("developer", "") ?: ""

            val genres = prod.optJSONArray("genres")
            val category = if (genres != null && genres.length() > 0) {
                genres.optJSONObject(0)?.optString("name", "") ?: ""
            } else ""

            var generation = 1
            var hasWindowsBuild = false
            try {
                // Build list lives on content-system.gog.com, NOT api.gog.com (which
                // returns the storefront HTML → parse fails → everything mislabelled gen1).
                // Requesting generation=2 still returns gen1-only builds, so the true
                // generation must be read from items[].generation (take the max), not
                // merely "items exist → gen2".
                val buildsJson = httpGet(
                    "https://content-system.gog.com/products/$id/os/windows/builds?generation=2", token,
                )
                if (buildsJson != null) {
                    val bObj = JSONObject(buildsJson)
                    val bitems = bObj.optJSONArray("items")
                    if (bitems != null && bitems.length() > 0) {
                        hasWindowsBuild = true
                        var maxGen = 0
                        for (bi in 0 until bitems.length()) {
                            val g = bitems.optJSONObject(bi)?.optInt("generation", 0) ?: 0
                            if (g > maxGen) maxGen = g
                        }
                        if (maxGen > 0) generation = maxGen
                    }
                }
            } catch (_: Exception) {}

            prefs.edit().putInt("gog_gen_$id", generation).apply()

            val releaseDate = prod.optString("release_date", "")
            if (releaseDate.isNotEmpty()) {
                prefs.edit().putString("gog_release_$id", releaseDate).apply()
            }
            val rating = prod.optInt("rating", -1)
            if (rating >= 0) {
                prefs.edit().putInt("gog_rating_$id", rating).apply()
            }

            if (prefs.getLong("gog_size_$id", -1) <= 0) {
                val size = GogDownloadManager.fetchInstallSizeBytes(id, token)
                if (size > 0) prefs.edit().putLong("gog_size_$id", size).apply()
            }

            // gap #8 filter — prime / size-0 / no-content guard. Drop only products this app can
            // NEVER install: no content-system Windows build AND no standalone Windows installer.
            // The installer check protects our standalone-installer classics (runInstaller) — a gen1
            // title with no build is still installable, so build-absence ALONE must not drop it.
            val downloads = prod.optJSONObject("downloads")
            var hasWindowsInstaller = false
            val installersArr = downloads?.optJSONArray("installers")
            if (installersArr != null) {
                for (di in 0 until installersArr.length()) {
                    if (installersArr.optJSONObject(di)?.optString("os", "") == "windows") {
                        hasWindowsInstaller = true; break
                    }
                }
            }
            if (!hasWindowsBuild && !hasWindowsInstaller) return null

            // gap #8 behavior 4 — vertical box-art backfill, lazy + idempotent. Only hit the unauth
            // gamesdb service when we don't already hold a cover for this id (stored per-id pref).
            // Once stored it's never re-fetched — even on a heavy all-owned re-sync — so the 2 extra
            // GETs are paid once per NEW/uncovered game. A gamesdb miss stores nothing and falls back
            // to the SGDB/icon `imageUrl` chain below (verticalCover stays null).
            var verticalCover: String? = prefs.getString("$VCOVER_PREFIX$id", null)
            if (verticalCover.isNullOrEmpty()) {
                verticalCover = fetchVerticalCover(id)
                if (!verticalCover.isNullOrEmpty()) {
                    prefs.edit().putString("$VCOVER_PREFIX$id", verticalCover).apply()
                }
            }

            return GogGame(id, titleStr, imageUrl, desc, developer, category, generation, verticalCover)
        } catch (e: Exception) {
            return null
        }
    }

    private val gogDlcBuffer = mutableMapOf<String, MutableList<Array<String>>>()

    private fun storeDlcInBuffer(dlcId: String, prod: JSONObject) {
        try {
            var dlcTitle = ""
            val titleObj = prod.optJSONObject("title")
            if (titleObj != null) dlcTitle = titleObj.optString("*", "")
            if (dlcTitle.isEmpty()) dlcTitle = prod.optString("title", "")
            if (dlcTitle.isEmpty()) dlcTitle = "Unknown DLC"

            var baseId = ""
            val reqGame = prod.optJSONObject("required_game")
            if (reqGame != null) baseId = reqGame.optString("id", "")
            if (baseId.isEmpty()) {
                val reqArr = prod.optJSONArray("requiredGames")
                if (reqArr != null && reqArr.length() > 0) baseId = reqArr.optString(0, "")
            }
            if (baseId.isEmpty()) return

            val list = gogDlcBuffer.getOrPut(baseId) { mutableListOf() }
            list.add(arrayOf(dlcId, dlcTitle))
        } catch (_: Exception) {}
    }

    private fun saveDlcBuffer() {
        for ((baseId, dlcs) in gogDlcBuffer) {
            try {
                val arr = JSONArray()
                for (dlc in dlcs) {
                    val obj = JSONObject()
                    obj.put("id", dlc[0])
                    obj.put("title", dlc[1])
                    arr.put(obj)
                }
                prefs.edit().putString("gog_dlcs_$baseId", arr.toString()).apply()
            } catch (_: Exception) {}
        }
        gogDlcBuffer.clear()
    }

    // ── Caching ───────────────────────────────────────────────────────────────

    private fun loadCachedGames(): List<GogGame>? {
        val json = prefs.getString(CACHE_KEY, null) ?: return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GogGame(
                    o.getString("gameId"),
                    o.getString("title"),
                    o.optString("imageUrl", ""),
                    o.optString("description", ""),
                    o.optString("developer", ""),
                    o.optString("category", ""),
                    o.optInt("generation", 1),
                    o.optString("verticalCover", "").ifEmpty { null }, // additive; absent → null
                )
            }
        } catch (_: Exception) { null }
    }

    private fun saveCachedGames(games: List<GogGame>) {
        try {
            val arr = JSONArray()
            for (g in games) {
                val o = JSONObject()
                o.put("gameId", g.gameId)
                o.put("title", g.title)
                o.put("imageUrl", g.imageUrl)
                o.put("description", g.description)
                o.put("developer", g.developer)
                o.put("category", g.category)
                o.put("generation", g.generation)
                // Additive key — readers that don't know it (cachedDetail/seed pre-#8) ignore it via
                // optString. A null cover simply omits the key (JSONObject.put drops null values).
                if (!g.verticalCover.isNullOrEmpty()) o.put("verticalCover", g.verticalCover)
                arr.put(o)
            }
            prefs.edit().putString(CACHE_KEY, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    // ── Display / Filter ──────────────────────────────────────────────────────

    private fun applyFilter(query: String) {
        searchQuery = query
        val filtered = if (query.isBlank()) allGames
        else allGames.filter { it.title.lowercase().contains(query.trim().lowercase()) }
        scrollVisible = filtered.isNotEmpty() || allGames.isNotEmpty()
    }

    private fun setSync(msg: String, syncOk: Boolean = false) {
        syncText = msg
        syncTextColor = when {
            msg.startsWith("Error") || msg.startsWith("Session expired") || msg.startsWith("Failed") || msg.startsWith("Not logged in") ->
                0xFFFF6B6B.toInt()
            syncOk || (msg.contains("game") && (msg.contains("tap") || msg.contains("cached"))) ->
                0xFF81C784.toInt()
            else -> 0xFFCCCCCC.toInt()
        }
    }

    // ── Game click handlers ──────────────────────────────────────────────────

    private fun onGameClick(game: GogGame) {
        if (viewMode == "list") {
            if (expandedGameId == game.gameId) {
                openDetailScreen(game)
            } else {
                expandedGameId = game.gameId
            }
        } else {
            expandedGameId = if (expandedGameId == game.gameId) null else game.gameId
        }
    }

    private fun onInstallClick(game: GogGame) {
        showInstallConfirm(game) {
            startDownload(game)
        }
    }

    private fun onCancelClick(game: GogGame) {
        val state = downloadStates[game.gameId]
        state?.cancelRunnable?.run()
    }

    private fun onAddToLauncher(game: GogGame) {
        val exePath = prefs.getString("gog_exe_${game.gameId}", null)
        if (exePath != null) GogLaunchHelper.addToLauncher(this, game.title, exePath, game.imageUrl)
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private fun startDownload(game: GogGame) {
        val dlState = GameDownloadState(
            buttonText = "Cancel",
            buttonColor = 0xFFCC3333.toInt(),
            progressVisible = true,
            progress = 0,
            status = "0%",
        )
        downloadStates[game.gameId] = dlState

        // Publish into the cross-store Download Manager (shade notification + background survival).
        // Single honest bar (pct only). Cancel routes to the Runnable stored in downloadStates
        // (read lazily so it resolves once startDownload has returned it below).
        StoreDownloadHooks.registerDownload(
            store = Store.GOG,
            id = game.gameId,
            name = game.title,
            cover = normalizeGogCover(game.imageUrl),
            supportsPause = false,
            installTotal = prefs.getLong("gog_size_${game.gameId}", 0L),
            cancel = { downloadStates[game.gameId]?.cancelRunnable?.run() },
        )

        // applicationContext: the GOG engine keeps the Context for its whole background run; the
        // list Activity must not be pinned/leaked. Registry hooks run on the engine callback thread.
        val cancelRunnable = GogDownloadManager.startDownload(applicationContext, game, object : GogDownloadManager.Callback {
            override fun onProgress(msg: String, pct: Int) {
                StoreDownloadHooks.tick(Store.GOG, game.gameId, pct)
                runOnUiThread {
                    val existing = downloadStates[game.gameId] ?: return@runOnUiThread
                    downloadStates[game.gameId] = existing.copy(
                        progress = pct,
                        status = msg,
                    )
                }
            }
            override fun onComplete(exePath: String) {
                // Finalize into the registry (engine already wrote gog_exe_/gog_dir_).
                val exe = prefs.getString("gog_exe_${game.gameId}", null)
                val dir = prefs.getString("gog_dir_${game.gameId}", null)
                if (exe != null && dir != null) {
                    val installAbs = GogInstallPath.getInstallDir(applicationContext, dir).absolutePath
                    StoreDownloadHooks.markInstalled(
                        store = Store.GOG,
                        id = game.gameId,
                        installPath = installAbs,
                        bytes = prefs.getLong("gog_size_${game.gameId}", 0L),
                    )
                } else {
                    StoreDownloadHooks.markFailed(Store.GOG, game.gameId, "No executable found")
                }
                runOnUiThread {
                    downloadStates[game.gameId] = GameDownloadState(
                        progress = 100,
                        status = "Installed",
                        isInstalled = true,
                        buttonText = "Add Game",
                        buttonColor = 0xFF0055FF.toInt(),
                        progressVisible = true,
                    )
                    applyFilter(searchQuery)
                }
            }
            override fun onError(msg: String) {
                StoreDownloadHooks.markFailed(Store.GOG, game.gameId, msg)
                runOnUiThread {
                    downloadStates.remove(game.gameId)
                    applyFilter(searchQuery)
                    resultBarMsg = "Error: $msg"
                }
            }
            override fun onCancelled() {
                StoreDownloadHooks.markCancelled(Store.GOG, game.gameId)
                runOnUiThread {
                    downloadStates.remove(game.gameId)
                    applyFilter(searchQuery)
                }
            }
            override fun onSelectExe(candidates: MutableList<String>?, onSelected: Consumer<String>?) {
                // No completion dialog (would wedge the DL card at 100% off-screen): auto-pick the
                // best (first) candidate. Exe choice stays available via the detail "Set .exe…".
                if (onSelected != null) onSelected.accept(candidates?.firstOrNull() ?: "")
            }
        })

        val existing = downloadStates[game.gameId] ?: return
        downloadStates[game.gameId] = existing.copy(cancelRunnable = cancelRunnable)
    }

    /** `//host/img` → `https://host/img`; blank → null. Matches the detail page's normalization. */
    private fun normalizeGogCover(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return if (url.startsWith("//")) "https:$url" else url
    }

    // ── Install confirmation dialog ──────────────────────────────────────────

    private fun showInstallConfirm(game: GogGame, onConfirm: () -> Unit) {
        var freeBytes = -1L
        try {
            val installBase = GogInstallPath.getInstallDir(this, "_check")
            val parent = installBase.parentFile
            parent?.mkdirs()
            val sf = StatFs(
                (parent ?: cacheDir).absolutePath,
            )
            freeBytes = sf.availableBlocksLong * sf.blockSizeLong
        } catch (_: Exception) {}

        showInstallDialog = InstallDialogData(game, freeBytes, onConfirm)

        lifecycleScope.launch(Dispatchers.IO) {
            val size = GogDownloadManager.fetchGameSize(this@GogGamesActivity, game)
            withContext(Dispatchers.Main) {
                showInstallDialog = showInstallDialog?.copy(gameSize = size)
            }
        }
    }

    // ── Detail screen ─────────────────────────────────────────────────────────

    private fun openDetailScreen(game: GogGame) {
        val intent = Intent(this, GogGameDetailActivity::class.java)
        intent.putExtra("game_id", game.gameId)
        intent.putExtra("title", game.title)
        intent.putExtra("image_url", game.imageUrl)
        intent.putExtra("description", game.description)
        intent.putExtra("developer", game.developer)
        intent.putExtra("category", game.category)
        intent.putExtra("generation", game.generation)
        detailLauncher.launch(intent)
    }

    // ── Detail dialog (list view) ────────────────────────────────────────────

    private fun showDetailDialog(game: GogGame) {
        val installedExe = prefs.getString("gog_exe_${game.gameId}", null)
        val dirName = prefs.getString("gog_dir_${game.gameId}", null)
        showDetailDialog = DetailDialogData(
            game = game,
            onSetExe = {
                if (dirName != null) {
                    val installPath = GogInstallPath.getInstallDir(this, dirName)
                    lifecycleScope.launch(Dispatchers.IO) {
                        val candidates = GogDownloadManager.collectExeCandidates(installPath)
                        if (candidates.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                resultBarMsg = "No .exe files found in install directory"
                            }
                            return@launch
                        }
                        withContext(Dispatchers.Main) {
                            showExePicker = ExePickerDataGog(candidates) { selected ->
                                if (selected.isNotEmpty()) {
                                    prefs.edit().putString("gog_exe_${game.gameId}", selected).apply()
                                    resultBarMsg = "Exe set to: ${java.io.File(selected).name}"
                                }
                            }
                        }
                    }
                }
            },
            onUninstall = {
                if (dirName != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val installPath = GogInstallPath.getInstallDir(this@GogGamesActivity, dirName)
                        deleteDir(installPath)
                        // Purge the FULL native install record + clear the DL manager's row, so the
                        // detail page and the cross-store Download Manager stay in sync. Mirrors Amazon.
                        GogInstallState.purge(applicationContext, game.gameId)
                        StoreDownloadHooks.markUninstalled(Store.GOG, game.gameId)
                        withContext(Dispatchers.Main) {
                            applyFilter(searchQuery)
                            resultBarMsg = "${game.title} uninstalled"
                        }
                    }
                }
            },
            onCopyToDownloads = {
                resultBarMsg = "Copying to Downloads\u2026"
                lifecycleScope.launch(Dispatchers.IO) {
                    val dest = GogDownloadManager.copyToDownloads(this@GogGamesActivity, game.gameId)
                    withContext(Dispatchers.Main) {
                        resultBarMsg = if (dest != null) "Copied to: $dest"
                        else "Copy failed \u2014 check storage permission"
                    }
                }
            },
        )
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun deleteDir(dir: java.io.File) {
        if (dir == null || !dir.exists()) return
        val children = dir.listFiles()
        if (children != null) for (c in children) deleteDir(c)
        dir.delete()
    }

}

// ── Data classes ────────────────────────────────────────────────────────────

private data class GameDownloadState(
    val progress: Int = 0,
    val status: String = "",
    val progressVisible: Boolean = false,
    val buttonText: String = "Install",
    val buttonColor: Int = 0xFF0055FF.toInt(),
    val isInstalled: Boolean = false,
    val cancelRunnable: Runnable? = null,
)

private data class ExePickerDataGog(
    val candidates: List<String>,
    val onSelected: (String) -> Unit,
)

private data class InstallDialogData(
    val game: GogGame,
    val freeBytes: Long,
    val onConfirm: () -> Unit,
    val gameSize: Long = -1,
)

private data class DetailDialogData(
    val game: GogGame,
    val onSetExe: () -> Unit,
    val onUninstall: () -> Unit,
    val onCopyToDownloads: () -> Unit,
)

// ── Helper composables ──────────────────────────────────────────────────────

@Composable
private fun GogGamesScreen(
    syncText: String,
    syncTextColor: Int,
    searchQuery: String,
    scrollVisible: Boolean,
    isSyncing: Boolean,
    viewMode: String,
    games: List<GogGame>,
    expandedGameId: String?,
    downloadStates: Map<String, GameDownloadState>,
    onBack: () -> Unit,
    onViewToggle: () -> Unit,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onGameClick: (GogGame) -> Unit,
    onGameLongClick: (GogGame) -> Unit,
    onExpandToggle: (String) -> Unit,
    onInstallClick: (GogGame) -> Unit,
    onCancelClick: (GogGame) -> Unit,
    onAddToLauncher: (GogGame) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
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
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text("\u2190", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }
            Spacer(Modifier.width(8.dp))
            Text(
                "GOG Library",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onViewToggle,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(
                    when (viewMode) {
                        "grid" -> "\u25A6"
                        "poster" -> "\u2630"
                        else -> "\u229E"
                    },
                    color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onRefresh,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) { Text("\u21ba", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }
            // \u2b07 cross-store Download Manager (global active-count badge).
            DownloadsButton()
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search games\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchChange(searchQuery) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        // Sync status
        Text(
            text = syncText,
            fontSize = 13.sp,
            color = Color(syncTextColor), // semantic status colour (error red / ready green / neutral) set in setSync()
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (scrollVisible && games.isNotEmpty()) {
                if (viewMode == "list") {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(games, key = { it.gameId }) { game ->
                            // No live download this session → fall back to disk-truth so an
                            // already-installed game shows Installed + "Add to Launcher" instead of
                            // "Install" (mirrors EpicGamesActivity's `?: GameDownloadState(...)`).
                            // buttonText MUST be set here: line ~1099 reads downloadState.buttonText
                            // directly, so a bare default would render "Install" despite isInstalled.
                            val context = LocalContext.current
                            val dlState = downloadStates[game.gameId]
                                ?: if (GogInstallState.isInstalled(context, game.gameId))
                                    GameDownloadState(isInstalled = true, buttonText = "Add to Launcher")
                                else null
                            val isExpanded = expandedGameId == game.gameId
                            GameListCard(
                                game = game,
                                isExpanded = isExpanded,
                                downloadState = dlState,
                                onClick = { onGameClick(game) },
                                onExpandToggle = { onExpandToggle(game.gameId) },
                                onInstallClick = { onInstallClick(game) },
                                onCancelClick = { onCancelClick(game) },
                                onAddToLauncher = { onAddToLauncher(game) },
                            )
                        }
                    }
                } else {
                    val artHeight = if (viewMode == "poster") 176 else 105
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(games, key = { it.gameId }) { game ->
                            // Disk-truth fallback (see the list branch above) so a prior-session
                            // install renders Installed + "Add to Launcher", not "Install".
                            val context = LocalContext.current
                            val dlState = downloadStates[game.gameId]
                                ?: if (GogInstallState.isInstalled(context, game.gameId))
                                    GameDownloadState(isInstalled = true, buttonText = "Add to Launcher")
                                else null
                            val isExpanded = expandedGameId == game.gameId
                            GameGridTile(
                                game = game,
                                artHeightDp = artHeight,
                                isExpanded = isExpanded,
                                downloadState = dlState,
                                onClick = { onGameClick(game) },
                                onLongClick = { onGameLongClick(game) },
                                onInstallClick = { onInstallClick(game) },
                                onCancelClick = { onCancelClick(game) },
                                onAddToLauncher = { onAddToLauncher(game) },
                            )
                        }
                    }
                }
            } else if (!scrollVisible && !isSyncing) {
                Text(
                    text = "Your GOG library is empty",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

@Composable
private fun GameListCard(
    game: GogGame,
    isExpanded: Boolean,
    downloadState: GameDownloadState?,
    onClick: () -> Unit,
    onExpandToggle: () -> Unit,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onAddToLauncher: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isExpanded) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // Collapsed header
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Cover
            Box(
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface),
            ) {
                // gap #8: prefer the real GOG 2:3 vertical cover when we have one; otherwise fall
                // back to the existing SGDB/icon cover (imageUrl). `//host` → `https://host`.
                val art = game.verticalCover?.takeIf { it.isNotEmpty() } ?: game.imageUrl
                if (art.isNotEmpty()) {
                    AsyncImage(
                        model = if (art.startsWith("//")) "https:$art" else art,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (game.generation > 0) {
                        GenBadge(game.generation)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = game.title,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (downloadState?.isInstalled == true) {
                        Text(" \u2713", fontSize = 14.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold) // semantic installed-green
                    }
                }
                if (game.developer.isNotEmpty() || game.category.isNotEmpty()) {
                    val sub = when {
                        game.developer.isEmpty() -> game.category
                        game.category.isEmpty() -> game.developer
                        else -> "${game.developer}  \u00B7  ${game.category}"
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
                modifier = Modifier.padding(start = 8.dp).clickable { onExpandToggle() },
            )
        }

        // Expandable section
        if (isExpanded) {
            ExpandedSection(
                game = game,
                downloadState = downloadState,
                onInstallClick = onInstallClick,
                onCancelClick = onCancelClick,
                onAddToLauncher = onAddToLauncher,
            )
        }
    }
}

@Composable
private fun ExpandedSection(
    game: GogGame,
    downloadState: GameDownloadState?,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onAddToLauncher: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        if (game.category.isNotEmpty() || game.developer.isNotEmpty()) {
            val meta = when {
                game.category.isEmpty() -> game.developer
                game.developer.isEmpty() -> game.category
                else -> "${game.category} \u00B7 ${game.developer}"
            }
            Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        val isInstalled = downloadState?.isInstalled == true

        if (isInstalled) {
            Text(
                "\u2713 Installed",
                fontSize = 10.sp,
                color = Color(0xFF4CAF50), // semantic installed-green
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (downloadState?.progressVisible == true) {
            LinearProgressIndicator(
                progress = { (downloadState.progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                "${downloadState.progress}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                downloadState.status,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        val btnText = downloadState?.buttonText
            ?: if (isInstalled) "Add to Launcher" else "Install"
        // Container/content derived from theme at call site (btnText carries the intent);
        // "Cancel" is the only destructive action \u2192 error, everything else \u2192 primary.
        val isCancelBtn = btnText == "Cancel"

        Button(
            onClick = {
                when (btnText) {
                    "Cancel" -> onCancelClick()
                    "Add Game", "Add to Launcher" -> onAddToLauncher()
                    else -> onInstallClick()
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
                btnText,
                color = if (isCancelBtn) MaterialTheme.colorScheme.onError
                else MaterialTheme.colorScheme.onPrimary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun GameGridTile(
    game: GogGame,
    artHeightDp: Int,
    isExpanded: Boolean,
    downloadState: GameDownloadState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInstallClick: () -> Unit,
    onCancelClick: () -> Unit,
    onAddToLauncher: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        // Art area
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artHeightDp.dp),
                contentAlignment = Alignment.Center,
            ) {
                // gap #8: prefer the real GOG 2:3 vertical cover when we have one; otherwise fall
                // back to the existing SGDB/icon cover (imageUrl). `//host` → `https://host`.
                val art = game.verticalCover?.takeIf { it.isNotEmpty() } ?: game.imageUrl
                if (art.isNotEmpty()) {
                    AsyncImage(
                        model = if (art.startsWith("//")) "https:$art" else art,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            // Gen badge
            if (game.generation > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                        .background(
                            // Gen badge = informational (not brand); themed accent, translucent over art.
                            // gen1/gen2 no longer differ by hue — the "Gen N" label carries the distinction.
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            RoundedCornerShape(3.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("Gen ${game.generation}", fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            // Title bar at bottom
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x44000000), Color(0xEE000000)),
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
                if (downloadState?.isInstalled == true) {
                    Text(" \u2713", fontSize = 11.sp, color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold) // semantic installed-green
                }
            }
        }

        // Action row (shown only when expanded)
        if (isExpanded) {
            val isInstalled = downloadState?.isInstalled == true
            val btnText = downloadState?.buttonText
                ?: if (isInstalled) "Add to Launcher" else "Install"
            // "Cancel" is the only destructive action \u2192 error; everything else \u2192 primary.
            val isCancelBtn = btnText == "Cancel"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                if (downloadState?.progressVisible == true) {
                    LinearProgressIndicator(
                        progress = { (downloadState.progress.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        when (btnText) {
                            "Cancel" -> onCancelClick()
                            "Add Game", "Add to Launcher" -> onAddToLauncher()
                            else -> onInstallClick()
                        }
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
                        btnText,
                        color = if (isCancelBtn) MaterialTheme.colorScheme.onError
                        else MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenBadge(generation: Int) {
    Text(
        text = "Gen $generation",
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(
                // informational gen badge (not brand); gen1/gen2 distinction carried by the label
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun ExePickerDialogGog(
    candidates: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
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
                    val f = java.io.File(path)
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

@Composable
private fun InstallConfirmDialog(
    game: GogGame,
    freeBytes: Long,
    gameSize: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Install ${game.title}?") },
        text = {
            Column {
                val sizeText = if (gameSize > 0) "Game size:  ${GogDownloadManager.formatBytes(gameSize)}"
                else "Game size:  Fetching\u2026"
                val notEnoughSpace = gameSize > 0 && freeBytes > 0 && gameSize > freeBytes
                val sizeColor = if (notEnoughSpace) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = sizeText,
                    fontSize = 14.sp,
                    color = sizeColor,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Available storage:  ${GogDownloadManager.formatBytes(freeBytes)}",
                    fontSize = 14.sp,
                    color = if (notEnoughSpace) MaterialTheme.colorScheme.error
                    else Color(0xFF88CC88), // semantic storage-ok green
                )
                if (notEnoughSpace) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "\u26A0 Not enough space",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            val canInstall = gameSize <= 0 || freeBytes <= 0 || gameSize <= freeBytes
            TextButton(
                onClick = onConfirm,
                enabled = canInstall,
            ) { Text("Install", color = if (canInstall) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}

@Composable
private fun DetailDialog(
    game: GogGame,
    onDismiss: () -> Unit,
    onSetExe: () -> Unit,
    onUninstall: () -> Unit,
    onCopyToDownloads: () -> Unit,
) {
    val msg = buildString {
        if (game.developer.isNotEmpty()) append("Developer: ${game.developer}\n")
        if (game.category.isNotEmpty()) append("Genre: ${game.category}\n")
        if (game.description.isNotEmpty()) append("\n${game.description}")
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(game.title) },
        text = {
            Column {
                Text(
                    text = msg.trim(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    )
}
