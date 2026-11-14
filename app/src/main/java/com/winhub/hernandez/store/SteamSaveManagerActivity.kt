package com.winhub.hernandez.store

import com.winhub.hernandez.ui.screens.deckCard
import com.winhub.hernandez.ui.screens.deckCardFillOr
import com.winhub.hernandez.ui.screens.deckGlassActive
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import com.winhub.hernandez.ui.screens.DeckEditorCategoryRail
import com.winhub.hernandez.ui.screens.DeckEditorCategoryRow
import com.winhub.hernandez.ui.screens.DeckPalette
import com.winhub.hernandez.ui.screens.DeckDragFrame
import com.winhub.hernandez.ui.screens.deckDragItem
import com.winhub.hernandez.ui.screens.deckPageBackground
import com.winhub.hernandez.ui.screens.DeckRow
import com.winhub.hernandez.ui.screens.DeckStretchyTabs
import com.winhub.hernandez.ui.screens.LocalDeckTabs
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.core.CustomSaveVault
import com.winhub.hernandez.core.GameSaveBackup
import com.winhub.hernandez.store.compose.ContainerPickerDialog
import com.winhub.hernandez.ui.components.CollapsibleRail
import com.winhub.hernandez.ui.components.RailItem
import com.winhub.hernandez.ui.components.RailSection
import com.winhub.hernandez.ui.components.rememberRailState
import com.winhub.hernandez.util.InAppFilePicker
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.winhub.hernandez.ui.components.EmuAccountConflictDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import com.winhub.hernandez.ui.theme.WinlatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File

/**
 * Central Steam Save Manager — one screen listing every game that has cloud saves or a local
 * Library folder, with an instant per-game sync status (rendered from the persisted sidecar,
 * NO network on open) and per-row quick Download/Upload. Pull-to-refresh does the live cloud
 * diff ([SaveSyncStore.refreshFromCloud]) per visible game off the main thread so "cloud ahead"
 * can surface.
 *
 * Three entry points reach the same [SaveManagerScreen] composable: the Steam store-home toolbar
 * (no focus) and the Games-tab per-item ⋮ menu on Steam-origin shortcuts (both via this Activity,
 * the latter passing [EXTRA_FOCUS_APP_ID] so the list opens scrolled to and highlighting that
 * game), plus the side-nav drawer's Library section (rendered directly by the NavHost, no focus).
 * Tapping a row opens that game's detail Cloud Saves section.
 */
class SteamSaveManagerActivity : ComponentActivity() {

    companion object {
        /** appId to scroll to / highlight when opened from a per-game menu (0 = no focus). */
        const val EXTRA_FOCUS_APP_ID = "focus_app_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour the user's App-orientation preference (Appearance -> AUTO / PORTRAIT / LANDSCAPE).
        // The whole Steam section previously ignored it: these activities pinned themselves in the
        // manifest and never asked. Applied before any content so the first frame is already in the
        // requested orientation. The game's XServerDisplayActivity is deliberately NOT touched.
        com.winhub.hernandez.core.AppOrientation.apply(this)
        val focusAppId = intent.getIntExtra(EXTRA_FOCUS_APP_ID, 0)
        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                SaveManagerScreen(
                    focusAppId = focusAppId,
                    onBack = { finish() },
                )
            
                }}
        }
    }
}

/**
 * Shared content of the Save Manager. Rendered both by [SteamSaveManagerActivity] (with an
 * [onBack] that finishes the Activity) and directly by the app NavHost for the drawer's
 * Library → Save Manager entry (no [onBack] → no header back button, since the drawer owns
 * navigation). Row taps open the per-game detail via [LocalContext], which is the hosting
 * Activity in either case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SaveManagerScreen(
    focusAppId: Int = 0,
    onBack: (() -> Unit)? = null,
    /** Hosted inside Deck Mode: no header/rail, the sections become a Deck pill strip. */
    deckMode: Boolean = false,
) {
    val context = LocalContext.current
    // Signed-in gate (a saved Steam account). Read once; SteamPrefs.init is idempotent + cheap.
    val signedIn = remember { SteamPrefs.init(context); SteamPrefs.isLoggedIn }
    // Lazy-connect: the store home starts SteamForegroundService (which opens the CM connection), but
    // reaching this screen via the drawer/⋮ doesn't — so ensure it here when signed in. Idempotent
    // (start/connect guard against double-connect); no-op for custom-only users so they never spin up
    // a Steam service/notification. Fires for BOTH the NavHost drawer destination and the Activity.
    LaunchedEffect(Unit) {
        if (signedIn) SteamForegroundService.start(context)
    }
    // Live Steam connection status for the header badge. Seed from the repository's current status,
    // then track the same "SteamStatus:<NAME>" events the detail page listens to — but via a
    // DisposableEffect (this composable is also the drawer NavHost destination, with no Activity to
    // implement SteamEventListener). Reflects the connection coming up from the auto-connect above.
    var steamStatus by remember { mutableStateOf(SteamRepository.getInstance().status) }
    DisposableEffect(Unit) {
        val repo = SteamRepository.getInstance()
        val listener = SteamRepository.SteamEventListener { event ->
            if (event.startsWith("SteamStatus:")) {
                val name = event.substringAfter("SteamStatus:")
                steamStatus = try { SteamRepository.SteamStatus.valueOf(name) } catch (e: Exception) { steamStatus }
            }
        }
        repo.addListener(listener)
        steamStatus = repo.status  // re-seed in case it changed between remember and register
        onDispose { repo.removeListener(listener) }
    }
    // Reuse the existing detail nav; its Cloud Saves section is the per-game control surface.
    val onOpenGame: (Int) -> Unit = { appId ->
        context.startActivity(
            Intent(context, SteamGameDetailActivity::class.java)
                .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, appId),
        )
    }
    // Epic/GOG rows open their own store detail page. Rebuild the same extras the store's
    // openDetailScreen uses from the cached library metadata (cachedDetail — cheap prefs+JSON read);
    // a cache miss (store never opened) still opens the page keyed by appName/gameId, just without the
    // hydrated title/art (the detail Activities default every missing extra) — never crashes.
    val onOpenEpic: (String) -> Unit = { appName ->
        val d = EpicLibrarySync.cachedDetail(context, appName)
        context.startActivity(
            Intent(context, EpicGameDetailActivity::class.java).apply {
                putExtra("app_name", appName)
                putExtra("title", d?.title ?: "")
                putExtra("description", d?.description ?: "")
                putExtra("developer", d?.developer ?: "")
                putExtra("art_cover", d?.artCover ?: "")
                putExtra("namespace", d?.namespace ?: "")
                putExtra("catalog_item_id", d?.catalogItemId ?: "")
            },
        )
    }
    val onOpenGog: (String) -> Unit = { gameId ->
        val d = GogLibrarySync.cachedDetail(context, gameId)
        context.startActivity(
            Intent(context, GogGameDetailActivity::class.java).apply {
                putExtra("game_id", gameId)
                putExtra("title", d?.title ?: "")
                putExtra("image_url", d?.imageUrl ?: "")
                putExtra("description", d?.description ?: "")
                putExtra("developer", d?.developer ?: "")
                putExtra("category", d?.category ?: "")
                putExtra("generation", d?.generation ?: 0)
            },
        )
    }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val pullState = rememberPullToRefreshState()

    var statuses by remember { mutableStateOf<List<SaveStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // 0 = Steam (cloud+local, appId-keyed), 1 = Custom (local vault, non-Steam imports), 2 = Settings,
    // 3 = Epic (Epic cloud saves via EpicCloudSaveManager + the EpicCloudSavePaths resolver),
    // 4 = GOG (GOG cloud saves via GogCloudSaveManager + the GogCloudSavePaths auto resolver;
    //     manual Browse pick still wins as an override).
    // rememberSaveable so the active section survives process death (the manifest now also carries
    // orientation in configChanges, so rotation no longer recreates the Activity and drops it).
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // In Deck mode the sections follow the Settings page's side rail / category pages.
    val deckPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val portraitDeck = com.winhub.hernandez.ui.screens.LocalDeckPortrait.current
    val sectionLayout = if (deckMode && !portraitDeck) {
        deckPrefs.getString("settings_layout", "rail")?.takeIf { it != "tabs" } ?: "rail"
    } else {
        "tabs"
    }
    var pagesCategoryOpen by rememberSaveable { mutableStateOf(false) }
    val deckTabLabels = remember { listOf("Steam", "Custom", "Epic", "GOG", "Settings") }
    // appIds with an in-flight quick action (Download/Upload) — disables that row's buttons.
    var busyAppIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // Live per-row progress line for a running (or just-finished) quick action, keyed by appId.
    // While busy it shows what the move is doing (from Callback.onStatus); on done/error it briefly
    // holds the summary/error, then the entry is removed and the row reverts to its last-synced line.
    val rowProgress = remember { mutableStateMapOf<Int, RowProgress>() }
    // Readable status feedback for the cloud/backup tabs. System Toasts render as an unreadable black
    // box on this ROM (targetSDK 28), so route messages through the shared outlined UninstallResultBar
    // (same pattern as the store screens). Hoisted here so the bar floats over whichever tab is shown.
    var resultBarMsg by remember { mutableStateOf<String?>(null) }

    // Instant load (sidecar + on-disk scan, no network) — off the main thread all the same.
    suspend fun reload() {
        val fresh = withContext(Dispatchers.IO) {
            // Ensure the Steam stack is ready (appContext + SteamDatabase) BEFORE computing statuses.
            // The drawer path only starts the async foreground service, so getGame()/resolveContainer
            // could otherwise hit an uninitialised DB → blank installDir → a set-up game mis-reported
            // as NOT_SET_UP. initialize() is idempotent and does NOT connect, so it's safe here on IO;
            // getInstance() is a non-null eager singleton. Guarded so a failure can't break the load.
            try { SteamRepository.getInstance().initialize(context.applicationContext) } catch (_: Throwable) {}
            SaveSyncStore.listStatuses(context)
        }
        statuses = fresh
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    // Pull-to-refresh: live cloud diff per visible game (blocking network → IO), then re-list so
    // the needs-attention-first sort re-settles with any freshly-surfaced "cloud ahead".
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            withContext(Dispatchers.IO) {
                for (s in statuses) {
                    try {
                        SaveSyncStore.refreshFromCloud(context, s.appId)
                    } catch (_: Throwable) { /* keep going; one bad game shouldn't stall the sweep */ }
                }
            }
            reload()
            pullState.endRefresh()
        }
    }

    // Focus: once the list is populated, scroll the requested game into view (it also renders a
    // highlighted border via row-level appId match below).
    LaunchedEffect(loading, focusAppId) {
        if (!loading && focusAppId != 0) {
            val idx = statuses.indexOfFirst { it.appId == focusAppId }
            if (idx >= 0) gridState.animateScrollToItem(idx)
        }
    }

    // One end-to-end combo for a single game, awaited to completion: syncFrom = Download+Apply
    // (cloud → into game), else = Collect+Upload (game → to cloud). Suspends until the manager reports
    // onDone/onError, driving the SAME per-row state a quick button tap does — busy spinner, live
    // progress line, and a per-row status refresh when it settles — so a bulk sync lights up the
    // individual rows exactly like tapping each one would. Returns true on success, false on error;
    // guards a double-run on the same appId. Must be called from the composition (main) scope. Reused
    // by the per-row buttons ([runQuickMove]) and the banner's "Sync Now" ([runSyncAll]).
    suspend fun syncOne(appId: Int, syncFrom: Boolean): Boolean {
        if (appId in busyAppIds) return false
        busyAppIds = busyAppIds + appId
        rowProgress[appId] = RowProgress(if (syncFrom) "Preparing sync from Cloud…" else "Preparing sync to Cloud…")
        // Use the same installDir source as SaveSyncStore + the detail page (getGame), so the combo
        // resolves the identical container. An empty dir → the manager's not-set-up guard fires.
        val installDir = withContext(Dispatchers.IO) {
            SteamRepository.getInstance().database.getGame(appId)?.installDir ?: ""
        }
        // Bridge the manager's callback API to a suspend point. onStatus may land on a worker thread, so
        // marshal that UI write onto the composition scope; onDone/onError resolve the await exactly once.
        val (ok, msg) = suspendCancellableCoroutine<Pair<Boolean, String>> { cont ->
            val cb = object : SteamCloudSaveManager.Callback {
                override fun onStatus(message: String) {
                    scope.launch { rowProgress[appId] = RowProgress(message) }
                }
                override fun onDone(summary: String) { if (cont.isActive) cont.resume(true to summary) }
                override fun onError(message: String) { if (cont.isActive) cont.resume(false to message) }
            }
            if (syncFrom) SteamCloudSaveManager.syncFromCloud(context, appId, installDir, cb)
            else SteamCloudSaveManager.syncToCloud(context, appId, installDir, cb)
        }
        // Settle the row: drop busy, show the summary/error, and (on success) refresh just this row's
        // status (records + local staleness, no network) so its pill + last-synced line are current.
        busyAppIds = busyAppIds - appId
        rowProgress[appId] = if (ok) RowProgress(msg) else RowProgress("Error: $msg", isError = true)
        if (ok) {
            val updated = withContext(Dispatchers.IO) { SaveSyncStore.statusOf(context, appId) }
            statuses = statuses.map { if (it.appId == appId) updated else it }
        }
        // Let the summary linger, then clear the row's progress line — in the background so a bulk sync
        // moves straight on to the next game instead of blocking on every row's linger.
        scope.launch {
            kotlinx.coroutines.delay(PROGRESS_LINGER_MS)
            rowProgress.remove(appId)
        }
        return ok
    }

    // Per-row quick button: fire-and-forget a single combo on the composition scope (buttons for
    // NOT_SET_UP rows are disabled up front; the manager also guards not-set-up itself).
    fun runQuickMove(appId: Int, syncFrom: Boolean) {
        scope.launch { syncOne(appId, syncFrom) }
    }

    // Deck tab strip ⇄ shell shoulders: L1/R1 on the Deck are bound to the tab host.
    val deckTabs = listOf(0, 1, 3, 4, 2)
    if (deckMode) {
        val deckHost = LocalDeckTabs.current
        DisposableEffect(Unit) {
            deckHost.bind(deckTabs.size)
            deckHost.select(deckTabs.indexOf(selectedTab).coerceAtLeast(0))
            onDispose {
                deckHost.bind(0)
                deckHost.onBack = null
            }
        }
        LaunchedEffect(deckHost.index) {
            selectedTab = deckTabs[deckHost.index.coerceIn(0, deckTabs.lastIndex)]
        }
        LaunchedEffect(selectedTab) {
            val i = deckTabs.indexOf(selectedTab).coerceAtLeast(0)
            if (deckHost.index != i) deckHost.select(i)
        }
        // B on an open category page returns to the page list before leaving the section.
        val pageBack = sectionLayout == "pages" && pagesCategoryOpen
        LaunchedEffect(pageBack) {
            deckHost.onBack = if (pageBack) {
                { pagesCategoryOpen = false; true }
            } else {
                null
            }
        }
    }

    // Steam-list needs-sync count. Drives BOTH the Steam tab's rail badge and the content-pane
    // warning strip below. (Custom rows load inside their own tab, so this is Steam scope only.)
    val needSync = statuses.count { it.state.needsAttention() }

    // ── Banner "Sync Now" — one-touch sync of every Steam game that needs attention ──────────────
    // Runs the needs-attention games SEQUENTIALLY (one at a time, so it doesn't hammer the network and
    // each row's progress stays legible), each in its CORRECT direction, reusing the same per-game path
    // as the row buttons ([syncOne]). Best-effort: one game's failure never aborts the rest.
    var syncAllRunning by remember { mutableStateOf(false) }
    // (current 1-based index, total) while a bulk sync runs — drives the button's "Syncing 3/7…" label.
    var syncAllProgress by remember { mutableStateOf(0 to 0) }

    fun runSyncAll() {
        if (syncAllRunning) return
        // Snapshot the games to sync + their direction NOW (statuses mutate as rows settle). Direction:
        // cloud newer → download; local newer / local-only / never-synced → upload. NOT_SET_UP (and any
        // non-attention state) is skipped — there's no container to sync, so it stays flagged. Skip any
        // row already mid-sync from a per-row tap so it isn't double-run / miscounted.
        val jobs = statuses.mapNotNull { s ->
            if (s.appId in busyAppIds) return@mapNotNull null
            when (s.state) {
                SaveState.CLOUD_AHEAD -> s.appId to true                                          // cloud → download
                SaveState.LOCAL_AHEAD, SaveState.LOCAL_ONLY, SaveState.NEVER_SYNCED -> s.appId to false  // local → upload
                else -> null                                                                       // NOT_SET_UP / settled → skip
            }
        }
        if (jobs.isEmpty()) {
            // Everything flagged is un-syncable (e.g. all NOT_SET_UP) — say so rather than no-op silently.
            resultBarMsg = "Nothing to sync yet — add these games to a container first."
            return
        }
        syncAllRunning = true
        syncAllProgress = 0 to jobs.size
        scope.launch {
            var okCount = 0
            var failCount = 0
            for ((i, job) in jobs.withIndex()) {
                syncAllProgress = (i + 1) to jobs.size
                val ok = try { syncOne(job.first, job.second) } catch (_: Throwable) { false }
                if (ok) okCount++ else failCount++
            }
            // Re-list so the needs-attention count drops + the needs-attention-first sort re-settles
            // (per-row refresh already flipped each synced row's pill).
            reload()
            syncAllRunning = false
            resultBarMsg = if (failCount == 0) "Synced $okCount game${plural(okCount)}."
                           else "Synced $okCount, $failCount failed."
        }
    }

    // Root Box so the status bar can float as an overlay over whichever tab is showing (matches how
    // the store screens place UninstallResultBar at their outermost container).
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().deckPageBackground(MaterialTheme.colorScheme.background)) {
        // Header bar — mirrors the Steam Library header idiom (back + title). Deck Mode already
        // sits inside the shell's rail, so it skips this header and uses the pill strip below.
        if (!deckMode) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the Activity entry points pass an onBack; the drawer destination relies on the
            // NavHost/drawer for navigation, so it renders without a header back button.
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Text(
                text = "Save Manager",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) 4.dp else 12.dp),
            )
            // Live Steam connection badge (signed-in only) — same pill the store/detail screens show;
            // tap to reconnect when offline. Custom-only users never see a Steam badge.
            if (signedIn) {
                SteamStatusPill(
                    status = steamStatus,
                    onReconnect = { SteamRepository.getInstance().reconnectNow() },
                )
                Spacer(Modifier.width(8.dp))
            }
        }

        // ── Shared collapsible left rail (mockup "Option 2") + content ──────────────────────────
        // Steam / Custom / Settings move off the old top TabRow into the rail; the needs-sync count
        // surfaces as a badge on the Steam tab + a warning strip over the content (no rail footer).
        // Landscape: expanded by default + per-screen memory; portrait: always collapsed icon-only
        // (the active section name is surfaced over the content).
        val railState = rememberRailState("savemanager")
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cols = if (isLandscape) 2 else 1
        val activeSection = when (selectedTab) {
            0 -> "Steam"
            1 -> "Custom"
            3 -> "Epic"
            4 -> "GOG"
            else -> "Settings"
        }

        if (deckMode) com.winhub.hernandez.ui.screens.PortraitScreenTitle("Saves")
        if (deckMode && sectionLayout == "tabs") {
            // Deck sections, same language as the Stores strip: landing on a pill selects its tab.
            if (com.winhub.hernandez.ui.screens.LocalDeckPortrait.current) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    com.winhub.hernandez.ui.screens.DeckPortraitChips(
                        deckTabLabels, deckTabs.indexOf(selectedTab).coerceAtLeast(0),
                    ) { selectedTab = deckTabs[it] }
                }
            } else {
            DeckStretchyTabs(
                labels = deckTabLabels,
                dots = listOf(
                    Color(0xFF1A9FFF),
                    DeckPalette.onSurfaceVariant,
                    Color.White,
                    Color(0xFFA855F7),
                    DeckPalette.onSurfaceVariant,
                ),
                badges = listOf(needSync, 0, 0, 0, 0),
                selected = deckTabs.indexOf(selectedTab).coerceAtLeast(0),
                onSelect = { selectedTab = deckTabs[it] },
                focusable = true,
                selectOnFocus = true,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (deckMode && sectionLayout == "pages" && !pagesCategoryOpen) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                deckTabLabels.forEachIndexed { i, label ->
                    DeckEditorCategoryRow(
                        label = label,
                        selected = deckTabs[i] == selectedTab,
                        onClick = {
                            selectedTab = deckTabs[i]
                            pagesCategoryOpen = true
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (!deckMode) CollapsibleRail(
                state = railState,
                title = "Save Manager",
                sections = listOf(
                    RailSection(
                        header = null,
                        items = listOf(
                            RailItem("Steam", Icons.Filled.VideogameAsset, selectedTab == 0, badge = needSync, onClick = { selectedTab = 0 }),
                            RailItem("Custom", Icons.Filled.Folder, selectedTab == 1) { selectedTab = 1 },
                            RailItem("Epic", Icons.Filled.Cloud, selectedTab == 3) { selectedTab = 3 },
                            RailItem("GOG", Icons.Filled.Storefront, selectedTab == 4) { selectedTab = 4 },
                            RailItem("Settings", Icons.Filled.Settings, selectedTab == 2) { selectedTab = 2 },
                        ),
                    ),
                ),
            ) else if (deckMode && sectionLayout == "rail") {
                DeckEditorCategoryRail(
                    labels = deckTabLabels,
                    selected = deckTabs.indexOf(selectedTab).coerceAtLeast(0),
                    onSelect = { selectedTab = deckTabs[it] },
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight()
                        .padding(start = 16.dp),
                )
            }

            // ── Content: full height beside the rail ─────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (deckMode && sectionLayout == "pages" && pagesCategoryOpen) {
                    DeckRow(
                        label = "← All categories",
                        hint = "Back to the category list",
                        onConfigClick = { pagesCategoryOpen = false },
                        control = {},
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // Collapsed rail hides the section labels, so surface the active one over the content
                // (matches ContainerDetailScreen) — the user never loses their place.
                if (!deckMode && railState.collapsed) {
                    Text(
                        activeSection,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                    )
                }

                // Content-pane warning strip — how many games still need syncing (Steam-list scope).
                // Shown only when there's something to sync, and only on the Steam tab (its scope);
                // hidden at 0 and on the Custom/Epic/Settings tabs.
                if (needSync > 0 && selectedTab == 0) {
                    // Don't offer a one-touch sync when it can't work: needs a signed-in account and a
                    // live-enough connection (per-game failures are still handled best-effort at run time).
                    val canSyncAll = signedIn &&
                        steamStatus != SteamRepository.SteamStatus.OFFLINE &&
                        steamStatus != SteamRepository.SteamStatus.SIGNED_OUT
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    ) {
                        Text(
                            text = "⚠️ $needSync game${plural(needSync)} need syncing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        // One tap syncs every needs-attention game (right direction each). Disabled while
                        // a bulk sync runs (then shows "Syncing i/N…") or when offline / signed out.
                        TextButton(
                            onClick = { runSyncAll() },
                            enabled = canSyncAll && !syncAllRunning,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            if (syncAllRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = if (syncAllRunning) "Syncing ${syncAllProgress.first}/${syncAllProgress.second}…" else "Sync Now",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (canSyncAll) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                when (selectedTab) {
                  0 -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .nestedScroll(pullState.nestedScrollConnection),
                    ) {
                    when {
                        loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        statuses.isEmpty() -> {
                            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Nothing to sync yet.\nDownload a game's cloud save from its detail page to start.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> {
                            // Multi-column in landscape (2-up), single column in portrait.
                            DeckDragFrame(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(cols),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(statuses, key = { it.appId }) { s ->
                                    SaveStatusRow(
                                        status = s,
                                        highlighted = s.appId == focusAppId,
                                        busy = s.appId in busyAppIds,
                                        progress = rowProgress[s.appId],
                                        onOpen = { onOpenGame(s.appId) },
                                        onSyncFrom = { runQuickMove(s.appId, syncFrom = true) },
                                        onSyncTo = { runQuickMove(s.appId, syncFrom = false) },
                                    )
                                }
                            }
                            }
                        }
                    }

                    // material3 1.2.0's PullToRefreshContainer draws its indicator even at rest; only
                    // show it while actively pulling or refreshing (matches FileManagerScreen).
                    if (pullState.verticalOffset > 0.5f || pullState.isRefreshing) {
                        PullToRefreshContainer(
                            state = pullState,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    }
                  }
                  1 -> {
                    CustomSaveTab(modifier = Modifier.weight(1f), columns = cols, onMessage = { resultBarMsg = it })
                  }
                  3 -> {
                    EpicSaveTab(modifier = Modifier.weight(1f), columns = cols, onMessage = { resultBarMsg = it }, onOpen = onOpenEpic)
                  }
                  4 -> {
                    GogSaveTab(modifier = Modifier.weight(1f), columns = cols, onMessage = { resultBarMsg = it }, onOpen = onOpenGog)
                  }
                  else -> {
                    SaveManagerSettingsSection(modifier = Modifier.weight(1f))
                  }
                }
            }
        }
        }
    }
        // Themed, auto-dismiss status feedback routed here from the cloud/backup tabs (replaces the
        // unreadable system Toast). Floats over the active tab via the root Box.
        resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
    }

}

// ─────────────────────────────────────────────────────────────────────────────
// Settings section — the two auto-back-up-on-exit toggles (both default ON, preserving current
// behavior). Now an inline rail section (was a cog dialog). State seeds from the shared
// "save_manager_prefs". Turning a toggle OFF is gated behind a warning confirm (write only on
// Continue; Cancel leaves it ON); turning ON writes through then shows a brief info dialog.
@Composable
private fun SaveManagerSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("save_manager_prefs", Context.MODE_PRIVATE) }
    var steamOn by remember { mutableStateOf(prefs.getBoolean("auto_collect_steam_on_exit", true)) }
    var steamUpOn by remember { mutableStateOf(prefs.getBoolean("auto_upload_steam_on_exit", true)) }
    var steamDlOn by remember { mutableStateOf(prefs.getBoolean("auto_download_steam_on_launch", true)) }
    var gogOn by remember { mutableStateOf(prefs.getBoolean("auto_upload_gog_on_exit", true)) }
    var gogDlOn by remember { mutableStateOf(prefs.getBoolean("auto_download_gog_on_launch", true)) }
    var customOn by remember { mutableStateOf(prefs.getBoolean("auto_backup_custom_on_exit", true)) }
    // Achievement sync-back is owned by SteamPrefs (NOT save_manager_prefs) and is default OFF — it
    // writes unlocks to the user's REAL Steam account, so it's gated behind an ON warning below.
    var achvSyncOn by remember { mutableStateOf(SteamPrefs.isAchievementSyncBackEnabled(context)) }
    // A pending toggle interaction rendered over the section (null = none).
    var pendingToggle by remember { mutableStateOf<TogglePrompt?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SettingsToggleRow(
            title = "Steam games: auto-collect on exit",
            subtitle = "Snapshot Steam-library saves to your local Library when a game exits.",
            checked = steamOn,
            // Don't flip/write here — route through the confirm (OFF) / info (ON) prompt.
            onCheckedChange = { pendingToggle = TogglePrompt(ToggleKind.STEAM, it) },
        )
        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            title = "Steam games: auto-upload to cloud on exit",
            subtitle = "Push Steam-library saves to Steam Cloud when a game exits (only after you accept the third-party cloud disclaimer; never deletes anything from the cloud).",
            checked = steamUpOn,
            onCheckedChange = { pendingToggle = TogglePrompt(ToggleKind.STEAM_UP, it) },
        )
        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            title = "Steam games: auto-download from cloud on launch",
            subtitle = "Pull the latest Steam Cloud saves into the game before it starts (newest-wins — never overwrites a newer local save).",
            checked = steamDlOn,
            onCheckedChange = { pendingToggle = TogglePrompt(ToggleKind.STEAM_DL, it) },
        )
        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            title = "GOG games: auto-upload to cloud on exit",
            subtitle = "Push GOG-library saves to GOG cloud when a game exits (newest-wins — never overwrites a newer cloud save).",
            checked = gogOn,
            onCheckedChange = { pendingToggle = TogglePrompt(ToggleKind.GOG, it) },
        )
        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            title = "GOG games: auto-download from cloud on launch",
            subtitle = "Pull the latest GOG cloud saves into the game before it starts (newest-wins — never overwrites a newer local save).",
            checked = gogDlOn,
            onCheckedChange = { pendingToggle = TogglePrompt(ToggleKind.GOG_DL, it) },
        )
        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            title = "Custom games: auto-back up on exit",
            subtitle = "Snapshot custom-import saves to the local vault when a game exits.",
            checked = customOn,
            onCheckedChange = { pendingToggle = TogglePrompt(ToggleKind.CUSTOM, it) },
        )
        Spacer(Modifier.height(16.dp))
        SettingsToggleRow(
            title = "Steam achievements: sync unlocks to your Steam profile",
            subtitle = "When ON, achievements you unlock in-game are written back to your REAL Steam account. Off by default — unlocks otherwise stay local to this device.",
            checked = achvSyncOn,
            onCheckedChange = { pendingToggle = TogglePrompt(ToggleKind.ACHV_SYNC, it) },
        )
    }

    pendingToggle?.let { prompt ->
            // Achievement sync-back lives in SteamPrefs (not save_manager_prefs) and has INVERTED
            // polarity: default OFF, and turning it ON writes to the user's real Steam account — so ON
            // is the guarded step here (OFF is a plain write + brief info). Handled up front so the
            // generic save-toggle flow below stays purely about the save_manager_prefs booleans.
            if (prompt.kind == ToggleKind.ACHV_SYNC) {
                if (prompt.newValue) {
                    OutlinedAlertDialog(
                        onDismissRequest = { pendingToggle = null },
                        title = { Text("Sync achievements to Steam?") },
                        text = {
                            Text(
                                "Turning this ON writes achievements you unlock in-game back to your REAL " +
                                    "Steam account and profile. This is a third-party sync — it can't be undone " +
                                    "on Steam and may not match what official Steam would record. Leave it off " +
                                    "to keep unlocks local to this device. Turn on anyway?",
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                SteamPrefs.setAchievementSyncBackEnabled(context, true)
                                achvSyncOn = true
                                pendingToggle = null
                            }) { Text("Turn on") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingToggle = null }) { Text("Cancel") }
                        },
                    )
                } else {
                    LaunchedEffect(prompt) {
                        SteamPrefs.setAchievementSyncBackEnabled(context, false)
                        achvSyncOn = false
                    }
                    OutlinedAlertDialog(
                        onDismissRequest = { pendingToggle = null },
                        title = { Text("Achievement sync off") },
                        text = {
                            Text(
                                "In-game achievement unlocks will stay local to this device and won't be " +
                                    "written to your Steam profile.",
                            )
                        },
                        confirmButton = { TextButton(onClick = { pendingToggle = null }) { Text("OK") } },
                    )
                }
                return@let
            }

            val prefKey = when (prompt.kind) {
                ToggleKind.STEAM -> "auto_collect_steam_on_exit"
                ToggleKind.STEAM_UP -> "auto_upload_steam_on_exit"
                ToggleKind.STEAM_DL -> "auto_download_steam_on_launch"
                ToggleKind.GOG -> "auto_upload_gog_on_exit"
                ToggleKind.GOG_DL -> "auto_download_gog_on_launch"
                ToggleKind.CUSTOM -> "auto_backup_custom_on_exit"
                ToggleKind.ACHV_SYNC -> "" // unreachable — handled above
            }
            // Commit a new value to both the pref and the controlling switch state.
            val commit = { value: Boolean ->
                if (prefKey.isNotEmpty()) prefs.edit().putBoolean(prefKey, value).apply()
                when (prompt.kind) {
                    ToggleKind.STEAM -> steamOn = value
                    ToggleKind.STEAM_UP -> steamUpOn = value
                    ToggleKind.STEAM_DL -> steamDlOn = value
                    ToggleKind.GOG -> gogOn = value
                    ToggleKind.GOG_DL -> gogDlOn = value
                    ToggleKind.CUSTOM -> customOn = value
                    ToggleKind.ACHV_SYNC -> {} // unreachable — handled above
                }
            }

            if (!prompt.newValue) {
                // OFF → warning confirm. Write + flip only on Continue; Cancel leaves the switch ON.
                OutlinedAlertDialog(
                    onDismissRequest = { pendingToggle = null },
                    title = { Text("Turn off auto-backup?") },
                    text = {
                        Text(
                            when (prompt.kind) {
                                ToggleKind.STEAM ->
                                    "Automatic save backup on exit will be OFF for your Steam library games. " +
                                        "Their saves won't be captured when a game closes — you'll need to back " +
                                        "them up yourself via a container's backup option or the Save Manager. Continue?"
                                ToggleKind.STEAM_UP ->
                                    "Automatic cloud upload on exit will be OFF for your Steam library games. " +
                                        "Their saves won't be pushed to Steam Cloud when a game closes — you'll need to " +
                                        "upload them yourself from the game's detail page Cloud Saves section. Continue?"
                                ToggleKind.STEAM_DL ->
                                    "Automatic cloud download on launch will be OFF for your Steam library games. " +
                                        "The latest cloud saves won't be pulled in before a game starts — you'll need to " +
                                        "download them yourself from the game's detail page Cloud Saves section. Continue?"
                                ToggleKind.GOG ->
                                    "Automatic cloud upload on exit will be OFF for your GOG library games. " +
                                        "Their saves won't be pushed to GOG cloud when a game closes — you'll need to " +
                                        "upload them yourself from the GOG Save Manager tab or the game's detail page. Continue?"
                                ToggleKind.GOG_DL ->
                                    "Automatic cloud download on launch will be OFF for your GOG library games. " +
                                        "The latest cloud saves won't be pulled in before a game starts — you'll need to " +
                                        "download them yourself from the GOG Save Manager tab or the game's detail page. Continue?"
                                ToggleKind.CUSTOM ->
                                    "Automatic save backup on exit will be OFF for your custom-imported games. " +
                                        "Their saves won't be captured when a game closes — you'll need to back them " +
                                        "up yourself via a container's backup option or the game's ⋮ menu → " +
                                        "'Back up saves'. Continue?"
                                ToggleKind.ACHV_SYNC -> "" // unreachable — handled above
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { commit(false); pendingToggle = null }) { Text("Continue") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingToggle = null }) { Text("Cancel") }
                    },
                )
            } else {
                // ON → write through, then a brief single-OK info dialog.
                LaunchedEffect(prompt) { commit(true) }
                OutlinedAlertDialog(
                    onDismissRequest = { pendingToggle = null },
                    title = { Text("Auto-backup on") },
                    text = {
                        Text(
                            when (prompt.kind) {
                                ToggleKind.STEAM ->
                                    "Automatic save backup on exit is ON for your Steam library games."
                                ToggleKind.STEAM_UP ->
                                    "Automatic cloud upload on exit is ON for your Steam library games."
                                ToggleKind.STEAM_DL ->
                                    "Automatic cloud download on launch is ON for your Steam library games."
                                ToggleKind.GOG ->
                                    "Automatic cloud upload on exit is ON for your GOG library games."
                                ToggleKind.GOG_DL ->
                                    "Automatic cloud download on launch is ON for your GOG library games."
                                ToggleKind.CUSTOM ->
                                    "Automatic save backup on exit is ON for your custom-imported games."
                                ToggleKind.ACHV_SYNC -> "" // unreachable — handled above
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { pendingToggle = null }) { Text("OK") }
                    },
                )
            }
        }
}

/** Which auto-backup toggle a pending confirm/info prompt belongs to. */
private enum class ToggleKind { STEAM, STEAM_UP, STEAM_DL, GOG, GOG_DL, CUSTOM, ACHV_SYNC }

/** A pending toggle interaction: which toggle, and the value the user is trying to set it to. */
private data class TogglePrompt(val kind: ToggleKind, val newValue: Boolean)

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom tab — non-Steam imported games with their LOCAL vault status + Back up / Restore. No cloud.
@Composable
private fun CustomSaveTab(modifier: Modifier = Modifier, columns: Int = 1, onMessage: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<CustomSaveVault.CustomGameStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // Rows with an in-flight backup/restore (keyed by the shortcut's file path).
    var busyKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Open dialogs: the backup layout picker, and the restore target-container picker.
    var backupFor by remember { mutableStateOf<CustomSaveVault.CustomGameStatus?>(null) }
    var restoreFor by remember { mutableStateOf<CustomSaveVault.CustomGameStatus?>(null) }
    // Restore-source chooser (latest vault snapshot vs. a browsed file), and a file-restore in
    // progress: the game + the picked save-zip Uri, awaiting a target container.
    var restoreChooser by remember { mutableStateOf<CustomSaveVault.CustomGameStatus?>(null) }
    var fileRestoreFor by remember { mutableStateOf<CustomSaveVault.CustomGameStatus?>(null) }
    var pickedSaveUri by remember { mutableStateOf<Uri?>(null) }
    // Emulator account ids a restore held back because the container already runs a different one.
    var emuConflicts by remember { mutableStateOf<List<GameSaveBackup.EmuIdConflict>>(emptyList()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedUri(result.data) else null
        if (uri != null) pickedSaveUri = uri else fileRestoreFor = null
    }

    suspend fun reload() {
        val fresh = withContext(Dispatchers.IO) { CustomSaveVault.listStatuses(context) }
        rows = fresh
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    fun runBackup(s: CustomSaveVault.CustomGameStatus, layout: GameSaveBackup.BackupLayout) {
        val key = s.shortcut.file.path
        busyKeys = busyKeys + key
        onMessage("Backing up saves for \"${s.name}\"…")
        CustomSaveVault.manualBackup(context, s.shortcut.container, s.shortcut, layout) { r ->
            if (r.wholeContainer && r.ok) {
                onMessage("No per-game saves detected — backed up the whole container.")
            }
            onMessage(
                if (r.ok) "Backed up ${r.fileCount} files → ${r.path?.substringAfterLast('/')}"
                else "Backup failed: ${r.error ?: "unknown error"}",
            )
            busyKeys = busyKeys - key
            scope.launch { reload() }
        }
    }

    fun runRestore(s: CustomSaveVault.CustomGameStatus, target: Container) {
        val key = s.shortcut.file.path
        busyKeys = busyKeys + key
        onMessage("Restoring saves into \"${target.name}\"…")
        CustomSaveVault.restoreLatest(context, s.shortcut, target) { r ->
            onMessage(
                if (r.ok) "Restored ${r.filesWritten} files to \"${target.name}\""
                else "Restore failed: ${r.error ?: "unknown error"}",
            )
            emuConflicts = r.emuConflicts
            busyKeys = busyKeys - key
            scope.launch { reload() }
        }
    }

    // Restore a browsed save zip (GameHub or Bannerlator) into the chosen container.
    fun runFileRestore(s: CustomSaveVault.CustomGameStatus, uri: Uri, target: Container) {
        val key = s.shortcut.file.path
        busyKeys = busyKeys + key
        onMessage("Restoring saves into \"${target.name}\"…")
        GameSaveBackup.restore(context, uri, target) { r ->
            onMessage(
                if (r.ok) "Restored ${r.filesWritten} files to \"${target.name}\""
                else "Restore failed: ${r.error ?: "unknown error"}",
            )
            emuConflicts = r.emuConflicts
            busyKeys = busyKeys - key
            scope.launch { reload() }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No custom games found.\nImport a game (exe/folder), then back its saves up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Multi-column in landscape (2-up), single column in portrait — matches the Steam grid.
            else -> DeckDragFrame(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { it.shortcut.file.path }) { s ->
                        CustomSaveRow(
                            status = s,
                            busy = s.shortcut.file.path in busyKeys,
                            onBackup = { backupFor = s },
                            onRestore = { restoreChooser = s },
                        )
                    }
                }
            }
        }
    }

    EmuAccountConflictDialog(conflicts = emuConflicts) { applied, _ ->
        emuConflicts = emptyList()
        if (applied > 0) onMessage("Emulator account switched to the backup's — relaunch the game")
    }

    // Backup layout picker (Winlator / GameHub) — mirrors the shortcut ⋮ menu's choice.
    backupFor?.let { s ->
        OutlinedAlertDialog(
            onDismissRequest = { backupFor = null },
            title = { Text("Backup format") },
            text = {
                Column {
                    Text(
                        "Which tool is this backup for?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { backupFor = null; runBackup(s, GameSaveBackup.BackupLayout.WINLATOR) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text("Winlator-native .zip", color = MaterialTheme.colorScheme.primary)
                        Text("Sibling Winlator / WinNative builds", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { backupFor = null; runBackup(s, GameSaveBackup.BackupLayout.GAMEHUB) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text("GameHub-compatible .zip", color = MaterialTheme.colorScheme.primary)
                        Text("GameHub, Proton-based tools (default)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { backupFor = null }) { Text("Cancel") } },
        )
    }

    // Restore target picker — choose which container to restore the latest vault snapshot into.
    restoreFor?.let { s ->
        val containers by produceState<List<Container>>(emptyList(), s) {
            value = withContext(Dispatchers.IO) {
                try { ContainerManager(context).getContainers() } catch (_: Throwable) { emptyList() }
            }
        }
        ContainerPickerDialog(
            gameName = s.name,
            containers = containers,
            onDismiss = { restoreFor = null },
            onSelected = { chosen ->
                restoreFor = null
                runRestore(s, chosen)
            },
        )
    }

    // Restore-source chooser: latest vault snapshot (if any) or a save file the user browses to.
    restoreChooser?.let { s ->
        OutlinedAlertDialog(
            onDismissRequest = { restoreChooser = null },
            title = { Text("Restore saves") },
            text = {
                Column {
                    if (s.hasBackup) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { restoreChooser = null; restoreFor = s }
                                .padding(vertical = 8.dp),
                        ) {
                            Text("Restore latest backup", color = MaterialTheme.colorScheme.primary)
                            Text("Backed up ${relTime(s.lastBackupMillis)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                restoreChooser = null
                                fileRestoreFor = s
                                filePicker.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.SAVE, "Select a save .zip"))
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Text("Restore from a file…", color = MaterialTheme.colorScheme.primary)
                        Text("Browse for a GameHub or WinHub save .zip", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { restoreChooser = null }) { Text("Cancel") } },
        )
    }

    // File restore: once a save zip is picked, choose the container to restore it into, then restore.
    val fr = fileRestoreFor
    val fu = pickedSaveUri
    if (fr != null && fu != null) {
        val containers by produceState<List<Container>>(emptyList(), fr) {
            value = withContext(Dispatchers.IO) {
                try { ContainerManager(context).getContainers() } catch (_: Throwable) { emptyList() }
            }
        }
        ContainerPickerDialog(
            gameName = fr.name,
            containers = containers,
            onDismiss = { fileRestoreFor = null; pickedSaveUri = null },
            onSelected = { chosen ->
                fileRestoreFor = null; pickedSaveUri = null
                runFileRestore(fr, fu, chosen)
            },
        )
    }
}

@Composable
private fun CustomSaveRow(
    status: CustomSaveVault.CustomGameStatus,
    busy: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val dragKey = remember { Any() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .deckCard(RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
            .deckDragItem(dragKey, selfOnly = true)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // Thumbnail = the shortcut's own art (cover → icon); custom games have no Steam appId cover.
        val bmp = status.shortcut.coverArt ?: status.shortcut.icon
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(deckCardFillOr(MaterialTheme.colorScheme.surface)),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.VideogameAsset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.containerLabel?.let { "Container: $it" } ?: "No container",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (status.hasBackup) "Backed up ${relTime(status.lastBackupMillis)}" else "No backup yet",
                style = MaterialTheme.typography.bodySmall,
                color = if (status.hasBackup) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        } else {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onBackup) { Text("Back up") }
                // Restore is always available: latest vault snapshot if present, else browse for a
                // GameHub/Bannerlator save file (the chooser decides).
                TextButton(onClick = onRestore) { Text("Restore") }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Epic tab — installed Epic games with cloud-save sync via EpicCloudSaveManager + the
// EpicCloudSavePaths resolver (auto save-folder resolution; no manual folder pick). Manual Up / Down
// only for P1 — conflict resolution + auto-triggers are P2.
@Composable
private fun EpicSaveTab(modifier: Modifier = Modifier, columns: Int = 1, onMessage: (String) -> Unit = {}, onOpen: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<EpicCloudSavePaths.EpicSaveStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // Rows with an in-flight up/down (keyed by appName).
    var busyKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun reload() {
        val fresh = withContext(Dispatchers.IO) { EpicCloudSavePaths.listStatuses(context) }
        rows = fresh
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    // Resolve the container + auto save dir (off-main), then drive the transport. Distinguishes
    // "not set up in a container" from "no cloud-save folder" so the toast is actionable.
    fun runSync(s: EpicCloudSavePaths.EpicSaveStatus, up: Boolean) {
        val key = s.appName
        if (key in busyKeys) return
        busyKeys = busyKeys + key
        onMessage(if (up) "Uploading \"${s.name}\" to Epic Cloud…" else "Downloading \"${s.name}\" from Epic Cloud…")
        scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                val installDir = EpicCloudSavePaths.installDir(context, s.appName)?.absolutePath
                val container = EpicCloudSavePaths.resolveContainer(context, s.appName, installDir)
                container to container?.let { EpicCloudSavePaths.resolveSaveDirectory(context, s.appName, it) }
            }
            val (container, dir) = resolved
            if (container == null) {
                onMessage("Add \"${s.name}\" to a container first to sync.")
                busyKeys = busyKeys - key
                return@launch
            }
            if (dir == null) {
                onMessage("Couldn't resolve a cloud-save folder for \"${s.name}\". Open the Epic store once to refresh its info.")
                busyKeys = busyKeys - key
                return@launch
            }
            val cb = object : EpicCloudSaveManager.Callback {
                override fun onStatus(message: String) { /* per-file progress omitted in P1 */ }
                override fun onDone(summary: String) {
                    scope.launch {
                        onMessage(summary)
                        busyKeys = busyKeys - key
                        reload()
                    }
                }
                override fun onError(message: String) {
                    scope.launch {
                        onMessage("Error: $message")
                        busyKeys = busyKeys - key
                    }
                }
            }
            if (up) EpicCloudSaveManager.uploadSaves(context, s.appName, dir, cb)
            else EpicCloudSaveManager.downloadSaves(context, s.appName, dir, cb)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No installed Epic games found.\nInstall a game from the Epic store, then sync its cloud saves here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> DeckDragFrame(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { it.appName }) { s ->
                        EpicSaveRow(
                            status = s,
                            busy = s.appName in busyKeys,
                            onOpen = { onOpen(s.appName) },
                            onUpload = { runSync(s, up = true) },
                            onDownload = { runSync(s, up = false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpicSaveRow(
    status: EpicCloudSavePaths.EpicSaveStatus,
    busy: Boolean,
    onOpen: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
) {
    val setUp = status.containerLabel != null
    val actionsEnabled = !busy && setUp && status.cloudSaveEnabled
    val dragKey = remember { Any() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .deckCard(RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
            // Tapping the card opens the Epic store detail page. The Up/Down IconButtons keep their own
            // click handlers so they stay independently tappable (not swallowed by the card click).
            .deckDragItem(dragKey, selfOnly = true)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // Real cover from the Epic library cache (artCover → artSquare); the neutral game-asset icon
        // stays behind it as the placeholder/fallback so a missing or failed cover still renders.
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(deckCardFillOr(MaterialTheme.colorScheme.surface)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VideogameAsset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            if (!status.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = status.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.containerLabel?.let { "Container: $it" } ?: "Not set up in a container",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val statusLine = when {
                !setUp -> "Add this game to a container first to sync."
                status.cloudSaveEnabled && status.lastSyncMillis > 0L -> "Synced ${relTime(status.lastSyncMillis)}"
                status.cloudSaveEnabled -> "Cloud saves ready — not synced yet"
                // Metadata fetched, but Epic ships no CloudSaveFolder for this title → genuinely unsupported.
                status.metadataChecked -> "No cloud-save support for this title"
                else -> "Open the Epic store to load this game's cloud-save info."
            }
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (actionsEnabled && status.lastSyncMillis == 0L) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val noCloudSupport = !status.cloudSaveEnabled && status.metadataChecked
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        } else if (noCloudSupport) {
            // No sync buttons for a title Epic doesn't cloud-sync — a single dimmed "cloud off" badge
            // makes the unsupported state unmistakable (vs a not-yet-refreshed game, which keeps its
            // buttons so a refresh can light them up).
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = "No cloud-save support",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp),
            )
        } else {
            val disabledTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onDownload, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = "Download from Epic Cloud",
                        tint = if (actionsEnabled) MaterialTheme.colorScheme.primary else disabledTint,
                    )
                }
                IconButton(onClick = onUpload, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = "Upload to Epic Cloud",
                        tint = if (actionsEnabled) MaterialTheme.colorScheme.primary else disabledTint,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GOG tab — installed GOG games with cloud-save sync via GogCloudSaveManager + the GogCloudSavePaths
// AUTO resolver (gap #2, P1). The save folder is auto-resolved inside the game's Wine container prefix
// from GOG's cloud-storage location template (remote-config, keyed by clientId) — the same pattern
// Epic uses. A manual Browse pick (`gog_save_dir_<gameId>` in bh_gog_prefs) still wins as an override.
// Up/Down light up when EITHER a manual folder is set OR the game is auto-resolvable (container + a
// known clientId). No conflict logic (P2/P3).

/** One row of the GOG save tab: an installed GOG game + its (optional) manual cloud-save folder. */
private data class GogSaveStatus(
    val gameId: String,
    val name: String,
    /** Absolute path of the user-picked save folder (`gog_save_dir_<gameId>`), or null if unset. */
    val saveDir: String?,
    /** Launch container's label, or null when the game isn't attached to a container yet. */
    val containerLabel: String?,
    /**
     * True when we can attempt an auto-resolve at sync time: a launch container is present AND a
     * clientId is known (so [GogCloudSavePaths] can fetch/expand the cloud-storage location). The
     * exact folder is resolved off-main lazily (may hit the network once).
     */
    val autoResolvable: Boolean,
    /** Normalized cover URL from the GOG library cache ([GogLibrarySync.cachedDetail]), or null. */
    val coverUrl: String?,
)

/**
 * Enumerate installed GOG games straight from `bh_gog_prefs` (UI-only; no new manager method). Walks
 * the `gog_dir_<gameId>` keys and keeps only rows that are really installed ON DISK — resolved
 * install dir present AND the recorded launch exe still exists — mirroring [GogLibrarySync.seed]'s
 * disk-truth check so uninstalled/half-recorded games don't linger. Title comes from the games-screen
 * cache ([GogLibrarySync.cachedDetail], gameId fallback); the save folder from `gog_save_dir_<gameId>`.
 * Sorted needs-a-folder-first, then by name. BLOCKING — call off the main thread.
 */
private fun loadGogSaveStatuses(context: Context): List<GogSaveStatus> {
    val prefs = context.getSharedPreferences("bh_gog_prefs", Context.MODE_PRIVATE)
    val out = ArrayList<GogSaveStatus>()
    for ((k, v) in prefs.all) {
        if (!k.startsWith("gog_dir_")) continue
        val gameId = k.substring("gog_dir_".length)
        if (gameId.isEmpty()) continue
        val dirName = (v as? String) ?: continue
        if (dirName.isEmpty()) continue
        val installPath = GogInstallPath.getInstallDir(context, dirName)
        val exe = prefs.getString("gog_exe_$gameId", null)
        if (!installPath.exists() || exe == null || !File(exe).exists()) continue
        // One cache lookup feeds both the title and the row's cover art (imageUrl already normalized).
        val detail = GogLibrarySync.cachedDetail(context, gameId)
        val name = detail?.title?.takeIf { it.isNotEmpty() } ?: gameId
        val coverUrl = detail?.imageUrl?.takeIf { it.isNotEmpty() }
        val saveDir = prefs.getString("gog_save_dir_$gameId", null)?.takeIf { it.isNotEmpty() }
        // Cheap (no network): launch container + clientId presence decide whether auto-resolve is
        // possible. The actual folder is resolved lazily off-main (GogCloudSavePaths.resolve).
        val container = try {
            GogCloudSavePaths.resolveContainer(context, gameId, installPath.absolutePath)
        } catch (e: Exception) { null }
        val autoResolvable = container != null && GogCloudSavePaths.clientId(context, gameId) != null
        out.add(GogSaveStatus(
            gameId, name, saveDir,
            containerLabel = container?.let { GogCloudSavePaths.containerLabel(it) },
            autoResolvable = autoResolvable,
            coverUrl = coverUrl,
        ))
    }
    // Needs-attention first (can't sync at all: no manual folder AND not auto-resolvable), then by name.
    out.sortWith(compareBy({ it.saveDir != null || it.autoResolvable }, { it.name.lowercase() }))
    return out
}

@Composable
private fun GogSaveTab(modifier: Modifier = Modifier, columns: Int = 1, onMessage: (String) -> Unit = {}, onOpen: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf<List<GogSaveStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // Rows with an in-flight up/down (keyed by gameId).
    var busyKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Auto-resolved folder tail per gameId ("Auto: …/<tail>"), filled lazily off-main.
    var autoTails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    suspend fun reload() {
        val fresh = withContext(Dispatchers.IO) { loadGogSaveStatuses(context) }
        rows = fresh
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    // Lazily resolve the auto folder for rows without a manual pick so the row can show "Auto: …/tail"
    // (mirrors the detail page). One resolve per eligible row, off-main; caches after the first fetch.
    LaunchedEffect(rows) {
        for (s in rows) {
            if (s.saveDir != null || !s.autoResolvable || autoTails.containsKey(s.gameId)) continue
            val dir = withContext(Dispatchers.IO) { GogCloudSavePaths.resolve(context, s.gameId).second }
            if (dir != null) autoTails = autoTails + (s.gameId to dir.name)
        }
    }

    // Drive the transport against the game's save folder: a manual pick (Browse) wins; otherwise
    // auto-resolve (container match + cloud-storage location expand) off-main, like the Epic tab.
    fun runSync(s: GogSaveStatus, up: Boolean) {
        val key = s.gameId
        if (key in busyKeys) return
        busyKeys = busyKeys + key
        onMessage(if (up) "Uploading \"${s.name}\" to GOG Cloud…" else "Downloading \"${s.name}\" from GOG Cloud…")
        val cb = object : GogCloudSaveManager.Callback {
            override fun onStatus(message: String) { /* per-file progress omitted (mirrors Epic) */ }
            override fun onDone(summary: String) {
                scope.launch {
                    onMessage(summary)
                    busyKeys = busyKeys - key
                    reload()
                }
            }
            override fun onError(message: String) {
                scope.launch {
                    onMessage("Error: $message")
                    busyKeys = busyKeys - key
                }
            }
        }
        scope.launch {
            val dir = withContext(Dispatchers.IO) {
                // Manual pick wins as an override; else auto-resolve.
                s.saveDir?.let { File(it) } ?: GogCloudSavePaths.resolve(context, s.gameId).second
            }
            if (dir == null) {
                onMessage("Couldn't resolve a save folder for \"${s.name}\". Add it to a container, or set one on its GOG detail page.")
                busyKeys = busyKeys - key
                return@launch
            }
            if (up) GogCloudSaveManager.uploadSaves(context, s.gameId, dir, cb)
            else GogCloudSaveManager.downloadSaves(context, s.gameId, dir, cb)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No installed GOG games found.\nInstall a game from the GOG store, then sync its cloud saves here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> DeckDragFrame(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(rows, key = { it.gameId }) { s ->
                        GogSaveRow(
                            status = s,
                            autoTail = autoTails[s.gameId],
                            busy = s.gameId in busyKeys,
                            onOpen = { onOpen(s.gameId) },
                            onUpload = { runSync(s, up = true) },
                            onDownload = { runSync(s, up = false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GogSaveRow(
    status: GogSaveStatus,
    autoTail: String?,
    busy: Boolean,
    onOpen: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
) {
    val hasManualFolder = status.saveDir != null
    // Up/Down light up when EITHER a manual folder is set OR the game can auto-resolve.
    val actionsEnabled = !busy && (hasManualFolder || status.autoResolvable)
    val dragKey = remember { Any() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .deckCard(RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.outline)
            // Tapping the card opens the GOG store detail page. The Up/Down IconButtons keep their own
            // click handlers so they stay independently tappable (not swallowed by the card click).
            .deckDragItem(dragKey, selfOnly = true)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // Real cover from the GOG library cache; the neutral game-asset icon stays behind it as the
        // placeholder/fallback so a missing or failed cover still renders (same idiom as the Epic tab).
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(deckCardFillOr(MaterialTheme.colorScheme.surface)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VideogameAsset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            if (!status.coverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = status.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Line 2: the folder source. Manual pick → "Folder: …"; else auto → "Auto: …/tail" once
            // resolved (falls back to the container label while the async resolve is in flight).
            val folderLine = when {
                hasManualFolder -> "Folder: ${status.saveDir!!.substringAfterLast('/')}"
                autoTail != null -> "Auto: …/$autoTail"
                status.autoResolvable -> status.containerLabel?.let { "Container: $it" } ?: "Auto-resolve on sync"
                status.containerLabel != null -> "Container: ${status.containerLabel}"
                else -> "No save folder set"
            }
            Text(
                text = folderLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Line 3: sync state / actionable hint.
            val canSync = hasManualFolder || status.autoResolvable
            val statusLine = when {
                hasManualFolder -> "Manual sync — GOG Cloud"
                status.autoResolvable -> "Auto save-folder — GOG Cloud"
                status.containerLabel == null -> "Add this game to a container first to sync."
                else -> "No cloud-save location — set a folder on its GOG detail page."
            }
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (canSync) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        } else {
            // GOG surfaces "unsupported" only at transport time (CLOUD_SAVES_NOT_SUPPORTED), so unlike
            // Epic there's no pre-checked cloud-off state — always show the buttons, disabled until a
            // save folder is set (manual) or the game is auto-resolvable.
            val disabledTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onDownload, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = "Download from GOG Cloud",
                        tint = if (actionsEnabled) MaterialTheme.colorScheme.primary else disabledTint,
                    )
                }
                IconButton(onClick = onUpload, enabled = actionsEnabled) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = "Upload to GOG Cloud",
                        tint = if (actionsEnabled) MaterialTheme.colorScheme.primary else disabledTint,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveStatusRow(
    status: SaveStatus,
    highlighted: Boolean,
    busy: Boolean,
    progress: RowProgress?,
    onOpen: () -> Unit,
    onSyncFrom: () -> Unit,
    onSyncTo: () -> Unit,
) {
    // Both combos require a container; NOT_SET_UP rows can't sync — disable the buttons and hint.
    val notSetUp = status.state == SaveState.NOT_SET_UP
    val actionsEnabled = !busy && !notSetUp
    // Sync-from-Cloud is pointless with nothing in the cloud (e.g. a Not-backed-up game) — gate it so
    // the meaningful action (Sync to Cloud = back it up) stands out.
    val syncFromEnabled = actionsEnabled && status.cloudFileCount > 0
    val dragKey = remember { Any() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .deckCard(RoundedCornerShape(12.dp), MaterialTheme.colorScheme.surfaceVariant, null)
            .border(
                width = if (highlighted) 2.dp else if (deckGlassActive()) 0.dp else 1.dp,
                color = if (highlighted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .deckDragItem(dragKey, selfOnly = true)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        val (pillColor, pillLabel) = pillFor(status.state)

        // Real Steam poster (library_600x900 → header.jpg), cached per appId; shows its own
        // spinner while loading and "×" if the art is missing.
        GameCoverArt(
            appId = status.appId,
            modifier = Modifier
                .size(width = 44.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.gameName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = status.containerLabel?.let { "Container: $it" } ?: "Not set up in a container",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // While a move runs (or just finished) the progress line takes the open space where the
            // last-synced line normally sits; a small spinner shows only while it's still running.
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = progress.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (progress.isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (notSetUp) {
                // Can't sync without a container — tell the user how to enable it (tap-through opens
                // the detail page where they can set it up).
                Text(
                    text = "Add this game to a container first to sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                val times = syncedTimesLine(status)
                if (times.isNotEmpty()) {
                    Text(
                        text = times,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Status pill.
            Spacer(Modifier.height(6.dp))
            StatusPill(color = pillColor, label = pillLabel)
        }

        // Per-row quick actions: the two end-to-end combos — Sync from Cloud (⬇) / Sync to Cloud (⬆).
        // Disabled while a move runs (live progress is in the row body) and for NOT_SET_UP rows.
        val disabledTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSyncFrom, enabled = syncFromEnabled) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Sync from Cloud",
                    tint = if (syncFromEnabled) MaterialTheme.colorScheme.primary else disabledTint,
                )
            }
            IconButton(onClick = onSyncTo, enabled = actionsEnabled) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = "Sync to Cloud",
                    tint = if (actionsEnabled) MaterialTheme.colorScheme.primary else disabledTint,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(color: Color, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** A row's live quick-action progress line: what the move is doing, and whether it's an error. */
private data class RowProgress(val message: String, val isError: Boolean = false)

/** How long a finished move's summary / error lingers in the row before it reverts to normal. */
private const val PROGRESS_LINGER_MS = 2500L

// Green = settled, amber/orange = local drift, blue = cloud drift, grey = nothing to do / unset.
private fun pillFor(state: SaveState): Pair<Color, String> = when (state) {
    SaveState.IN_SYNC        -> Color(0xFF3BA55D) to "In sync"
    SaveState.LOCAL_ONLY     -> Color(0xFFE0662E) to "Not backed up"
    SaveState.LOCAL_AHEAD    -> Color(0xFFE0A82E) to "Local ahead"
    SaveState.CLOUD_AHEAD    -> Color(0xFF4B9CE0) to "Cloud ahead"
    SaveState.NEVER_SYNCED   -> Color(0xFFE07B2E) to "Never synced"
    SaveState.NO_CLOUD_SAVES -> Color(0xFF9AA0A6) to "No cloud saves"
    SaveState.NOT_SET_UP     -> Color(0xFF9AA0A6) to "Not set up"
    SaveState.UNKNOWN        -> Color(0xFF9AA0A6) to "Unknown"
}

private fun SaveState.needsAttention(): Boolean = when (this) {
    SaveState.NOT_SET_UP, SaveState.CLOUD_AHEAD, SaveState.LOCAL_ONLY, SaveState.LOCAL_AHEAD, SaveState.NEVER_SYNCED -> true
    else -> false
}

private fun syncedTimesLine(status: SaveStatus): String {
    val parts = ArrayList<String>(2)
    if (status.lastDownloadAt > 0L) parts.add("Downloaded ${relTime(status.lastDownloadAt)}")
    if (status.lastUploadAt > 0L) parts.add("Uploaded ${relTime(status.lastUploadAt)}")
    return parts.joinToString(" · ")
}

private fun relTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()

private fun plural(n: Int) = if (n == 1) "" else "s"
