package com.winhub.hernandez.store

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.store.compose.AddResultDialog
import com.winhub.hernandez.store.compose.AddShortcutResult
import com.winhub.hernandez.store.compose.AddToShortcutsRequest
import com.winhub.hernandez.store.compose.ContainerPickerDialog
import com.winhub.hernandez.store.compose.openShortcutsScreen
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog

/**
 * The **Library** tab: the owned-games grid, moved here out of [SteamGamesActivity] so the tab host
 * and the standalone activity render one implementation rather than two.
 *
 * [rememberSteamLibrary] owns the library state (rows, sync status, the repository event listener,
 * the stale-library auto-sync). The storefront host holds ONE of those and feeds both this tab and
 * the Store tab's "do I own this?" set, so a granted free license lights up in both places at once.
 *
 * Two view modes, both preserved from the original screen:
 *  - `grid` — the storefront's capsule card (art + name + Download/Play), 2 columns in portrait and
 *    as many as fit in landscape via [GridCells.Adaptive].
 *  - `list` — the detailed row (developer / genres / size / Metacritic / Launch + Uninstall).
 */

/**
 * The Library tab's type chips.
 *
 * [GAMES] is the DEFAULT and reproduces the behaviour the tab has always had, so an update never
 * silently drops demos into someone's library — seeing them is opt-in.
 *
 * [ALL] is deliberately Games ∪ Demos and NOT "every app type". The library rows also carry `dlc`,
 * `config`, `tool`, `music` and `video` entries, which are infrastructure the user never launches;
 * surfacing them would make the tab worse, not more complete.
 */
enum class LibraryTypeFilter(val label: String) {
    ALL("All"),
    GAMES("Games"),
    DEMOS("Demos"),
    /** Games ∪ Demos that are downloaded on this device — what the user can launch right now. */
    INSTALLED("Installed"),
    ;

    /**
     * Whether [game] belongs under this chip.
     *
     * The allowlist check is not incidental: a handful of appIds (Lossless Scaling and friends) are
     * not `type == "game"` but are deliberately surfaced anyway — see `SteamRepository`'s
     * LIBRARY_ALLOWLIST. They belong under Games, and therefore under All.
     */
    fun accepts(game: SteamGame): Boolean = when (this) {
        GAMES -> isGame(game)
        DEMOS -> game.type == TYPE_DEMO
        ALL -> isGame(game) || game.type == TYPE_DEMO
        INSTALLED -> (isGame(game) || game.type == TYPE_DEMO) && game.isInstalled
    }

    fun filter(games: List<SteamGame>): List<SteamGame> = games.filter { accepts(it) }

    /** Copy shown when this chip matches nothing. */
    fun emptyMessage(): String = when (this) {
        GAMES -> "No games in your library"
        DEMOS -> "No demos in your library"
        INSTALLED -> "Nothing installed yet"
        ALL -> "No games yet"
    }

    companion object {
        const val TYPE_GAME = "game"
        const val TYPE_DEMO = "demo"

        private fun isGame(game: SteamGame): Boolean =
            game.type == TYPE_GAME || SteamRepository.LIBRARY_ALLOWLIST.contains(game.appId)

        /** Parse a persisted name, falling back to the opt-in-safe default. */
        fun fromName(name: String?): LibraryTypeFilter =
            entries.firstOrNull { it.name == name } ?: GAMES
    }
}

// ── State ─────────────────────────────────────────────────────────────────────────────────────

/** Everything the Library tab and the Store tab's ownership check need, from one source of truth. */
class SteamLibraryState internal constructor(
    val games: List<SteamGame>,
    val isLoading: Boolean,
    val statusText: String,
    val steamStatus: SteamRepository.SteamStatus,
    /**
     * Fast membership test for the Store tab's Download / Add to Library / Not-owned decision.
     *
     * Built from the FULL [games] list and NEVER from the type-filtered display list. If this
     * followed the chip, selecting "Demos" would tell the Store tab the account owns nothing but
     * demos and every owned game in a rail would read "Not owned".
     *
     * Including demos here also fixes a pre-existing bug: an owned demo used to be filtered out of
     * the library entirely, so it showed as "Not owned" in Store rails.
     */
    val ownedAppIds: Set<Int>,
    val refresh: () -> Unit,
    val reload: () -> Unit,
    val reconnect: () -> Unit,
)

/**
 * Library state bound to the live [SteamRepository]: re-reads the cached rows on every
 * `LibrarySynced` / resume, tracks the connection pill's status, and kicks a sync when the cache is
 * empty or older than four hours. Lifted verbatim from [SteamGamesActivity]'s Activity-level state
 * so both hosts share one behaviour.
 */
@Composable
fun rememberSteamLibrary(): SteamLibraryState {
    var games by remember { mutableStateOf<List<SteamGame>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Loading library…") }
    var steamStatus by remember {
        mutableStateOf(
            runCatching { SteamRepository.getInstance().status }
                .getOrDefault(SteamRepository.SteamStatus.OFFLINE),
        )
    }

    val load: () -> Unit = load@{
        val repo = runCatching { SteamRepository.getInstance() }.getOrNull() ?: return@load
        val rows = runCatching { repo.getCachedGameRows() }.getOrDefault(emptyList())
        // The FULL launchable set — games (plus allowlisted appIds) AND demos. The type chip narrows
        // what is DISPLAYED; it must never narrow what is OWNED. Everything past this point,
        // ownedAppIds especially, is computed from this list.
        //
        // Demos have always arrived here with type == "demo" (the engine parses common.type from
        // appinfo); the old filter simply discarded them on this line.
        games = rows
            .map { SteamGame.fromGameRow(it) }
            .filter { LibraryTypeFilter.ALL.accepts(it) }
            .sortedBy { it.name.lowercase() }
        isLoading = false
        val demoCount = games.count { it.type == LibraryTypeFilter.TYPE_DEMO }
        if (games.isNotEmpty()) statusText = "${games.size - demoCount} games in library"
        StorefrontLog.i(
            StorefrontLog.LIBRARY,
            "loaded ${games.size} entry(s) from ${rows.size} cached row(s) — " +
                "${games.size - demoCount} game(s), $demoCount demo(s), " +
                "${games.count { it.isInstalled }} installed",
        )
    }

    val appCtx = LocalContext.current.applicationContext

    DisposableEffect(Unit) {
        val repo = runCatching { SteamRepository.getInstance() }.getOrNull()
        val listener = SteamRepository.SteamEventListener { event ->
            when {
                event.startsWith("LibraryProgress:") -> {
                    val parts = event.split(":")
                    val phase = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val count = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    val total = parts.getOrNull(3)?.toIntOrNull() ?: 0
                    statusText = when {
                        phase == 0 -> "Syncing packages ($count)…"
                        phase == 2 && total > 0 -> "Fetching app records ($count/$total)…"
                        else -> "Fetching $count app records…"
                    }
                }
                event.startsWith("LibrarySynced:") -> {
                    StorefrontLog.i(StorefrontLog.LIBRARY, "library sync COMPLETED — re-reading cached rows")
                    // The snapshot's published artwork lands with the sync. Parsing it is a JSON
                    // walk over every owned app, so it goes on a worker rather than the event thread.
                    Thread({ SteamLibraryArt.refreshFromSnapshot(appCtx) }, "SteamArtRefresh")
                        .apply { isDaemon = true }.start()
                    load()
                }
                event.startsWith("SteamStatus:") -> {
                    val name = event.substringAfter("SteamStatus:")
                    steamStatus = runCatching { SteamRepository.SteamStatus.valueOf(name) }
                        .getOrDefault(steamStatus)
                }
                event == "Disconnected" -> statusText = "Disconnected — reconnecting…"
                event == "Connected" -> {
                    val r = runCatching { SteamRepository.getInstance() }.getOrNull()
                    if (games.isEmpty() && r?.isLoggedIn == true) {
                        statusText = "Reconnected — syncing library…"
                        r.syncLibrary()
                    }
                }
            }
        }
        repo?.addListener(listener)
        onDispose { repo?.removeListener(listener) }
    }

    // Re-read on every resume — an install/uninstall done in the detail screen or the download
    // manager must be reflected when the user comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        // Restore the on-disk art mirror first so owned games render immediately (and offline),
        // then pull a fresh snapshot if the engine happens to be logged on.
        withContext(Dispatchers.IO) {
            SteamLibraryArt.init(appCtx)
            SteamLibraryArt.refreshFromSnapshot(appCtx)
        }
        load()
        val repo = runCatching { SteamRepository.getInstance() }.getOrNull() ?: return@LaunchedEffect
        if (!repo.isLoggedIn) return@LaunchedEffect
        val staleThresholdSec = 4 * 60 * 60L
        val elapsed = System.currentTimeMillis() / 1000L - repo.lastSyncTime
        if (games.isEmpty() || elapsed > staleThresholdSec) {
            statusText = if (games.isEmpty()) "Syncing library…" else "Refreshing library…"
            StorefrontLog.i(
                StorefrontLog.LIBRARY,
                "library sync KICKED (cached=${games.size}, ${elapsed}s since last sync)",
            )
            repo.syncLibrary()
        }
    }

    return SteamLibraryState(
        games = games,
        isLoading = isLoading,
        statusText = statusText,
        steamStatus = steamStatus,
        // From `games` (the full launchable set), not from any filtered view — see the field doc.
        ownedAppIds = remember(games) { games.mapTo(HashSet(games.size)) { it.appId } },
        refresh = {
            StorefrontLog.i(StorefrontLog.LIBRARY, "manual library refresh requested")
            runCatching { SteamRepository.getInstance().syncLibrary() }
                .onFailure { StorefrontLog.w(StorefrontLog.LIBRARY, "syncLibrary() failed to start", it) }
            Unit
        },
        reload = load,
        reconnect = { runCatching { SteamRepository.getInstance().reconnectNow() } },
    )
}

// ── Screen ────────────────────────────────────────────────────────────────────────────────────

/**
 * The Library tab body. [wide] only affects density here — the grid is [GridCells.Adaptive], so
 * landscape gains columns from the same code rather than needing a second layout, and the detailed
 * list mode simply gets longer rows. All the modal flows (exe picker → container picker → result,
 * uninstall confirm/progress) moved here with the grid.
 */
@Composable
fun SteamLibraryTab(
    state: SteamLibraryState,
    wide: Boolean,
    viewMode: String,
    typeFilter: LibraryTypeFilter,
    onTypeFilter: (LibraryTypeFilter) -> Unit,
    onOpenApp: (Int) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    // The chip narrows only what is drawn. state.ownedAppIds stays the full set — see its doc.
    val shown = remember(state.games, typeFilter) { typeFilter.filter(state.games) }
    val activity = ctx as? Activity

    var showExePicker by remember { mutableStateOf<SteamExePickerData?>(null) }
    var addToShortcuts by remember { mutableStateOf<AddToShortcutsRequest?>(null) }
    var addResult by remember { mutableStateOf<AddShortcutResult?>(null) }
    var uninstallingName by remember { mutableStateOf<String?>(null) }

    fun startAddToShortcuts(gameName: String, exePath: String, coverUrl: String?) {
        val a = activity ?: return
        StarLaunchBridge.loadContainers(a) { containers ->
            addToShortcuts = AddToShortcutsRequest(gameName, exePath, coverUrl, containers)
        }
    }

    /** Installed-game launch: find the best .exe, then hand off to the add-to-shortcuts flow. */
    fun launchInstalled(game: SteamGame) {
        if (game.installDir.isEmpty()) {
            StorefrontLog.w(StorefrontLog.LIBRARY, "launch app ${game.appId} (${game.name}): installDir is EMPTY")
            onMessage("Install directory not set")
            return
        }
        val installDir = java.io.File(game.installDir)
        Thread {
            val exeFiles = mutableListOf<java.io.File>()
            AmazonLaunchHelper.collectExe(installDir, exeFiles)
            // Prefer the app's PUBLISHED portrait cover; the constructed URL is the fallback for
            // anything the snapshot doesn't carry.
            val coverUrl = SteamLibraryArt.libraryCapsule(game.appId)
                ?: "https://shared.steamstatic.com/store_item_assets/steam/apps/${game.appId}/library_600x900.jpg"
            val lowerTitle = game.name.lowercase()
            exeFiles.sortWith { a, b ->
                AmazonLaunchHelper.scoreExe(b, lowerTitle) - AmazonLaunchHelper.scoreExe(a, lowerTitle)
            }
            activity?.runOnUiThread {
                when {
                    exeFiles.isEmpty() -> {
                        StorefrontLog.w(
                            StorefrontLog.LIBRARY,
                            "launch app ${game.appId} (${game.name}): NO .exe under ${game.installDir}",
                        )
                        onMessage("No .exe found in install directory")
                    }
                    exeFiles.size == 1 -> startAddToShortcuts(game.name, exeFiles[0].absolutePath, coverUrl)
                    else -> showExePicker =
                        SteamExePickerData(game.name, exeFiles.map { it.absolutePath }, coverUrl)
                }
            }
        }.start()
    }

    fun uninstall(game: SteamGame) {
        uninstallingName = game.name
        StoreUninstaller.run(
            installDir = game.installDir,
            mark = { runCatching { SteamRepository.getInstance().database.markUninstalled(game.appId) } },
        ) { ok ->
            uninstallingName = null
            if (ok) StorefrontLog.i(StorefrontLog.LIBRARY, "uninstalled app ${game.appId} (${game.name})")
            else StorefrontLog.w(StorefrontLog.LIBRARY, "uninstall INCOMPLETE for app ${game.appId} (${game.name}) — files remain at ${game.installDir}")
            onMessage(if (ok) "${game.name} uninstalled" else "Couldn't fully remove ${game.name}")
            state.reload()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Type chips. Same row in both orientations — they are one short line either way, and the
        // grid below is what reflows.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LibraryTypeFilter.entries.forEach { f ->
                StoreFilterChip(
                    label = f.label,
                    selected = typeFilter == f,
                    onClick = { onTypeFilter(f) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.games.isEmpty() && !state.isLoading -> StoreNotice(
                    title = "No games yet",
                    body = "Nothing in this account's Steam library has synced yet. Pull a fresh sync " +
                        "with Refresh, or add a free game from the Store tab.",
                    actionLabel = "Refresh",
                    onAction = state.refresh,
                    modifier = Modifier.align(Alignment.Center),
                )

                // The library HAS entries, this chip just matches none of them — say which, rather
                // than showing an empty grid that looks like a failure.
                shown.isEmpty() && !state.isLoading -> StoreNotice(
                    title = typeFilter.emptyMessage(),
                    body = when (typeFilter) {
                        LibraryTypeFilter.DEMOS ->
                            "Demos you have installed from the Steam store will appear here."
                        LibraryTypeFilter.INSTALLED ->
                            "Games and demos you download from your library will appear here."
                        else ->
                            "Nothing in your library matches this filter."
                    },
                    actionLabel = if (typeFilter != LibraryTypeFilter.ALL) "Show all" else null,
                    onAction = if (typeFilter != LibraryTypeFilter.ALL) {
                        { onTypeFilter(LibraryTypeFilter.ALL) }
                    } else {
                        null
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

                viewMode == "list" -> LazyColumn(
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(shown, key = { it.appId }) { game ->
                        LibraryDetailRow(
                            game = game,
                            onClick = { onOpenApp(game.appId) },
                            onLaunch = { launchInstalled(game) },
                            onUninstall = { uninstall(game) },
                        )
                    }
                }

                else -> LazyVerticalGrid(
                    // Adaptive rather than a fixed count: 2 columns on a portrait phone, 4-6 across a
                    // handheld in landscape, from one declaration.
                    columns = GridCells.Adaptive(minSize = if (wide) 190.dp else 165.dp),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    items(shown, key = { it.appId }) { game ->
                        LibraryCapsuleCard(
                            game = game,
                            onOpen = { onOpenApp(game.appId) },
                            onAction = {
                                if (game.isInstalled) launchInstalled(game) else onOpenApp(game.appId)
                            },
                        )
                    }
                }
            }

            if (state.isLoading && state.games.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            showExePicker?.let { data ->
                ExePickerDialog(
                    title = "Select executable for \"${data.gameName}\"",
                    candidates = data.candidates,
                    onDismiss = { showExePicker = null },
                    onSelected = { chosen ->
                        showExePicker = null
                        startAddToShortcuts(data.gameName, chosen, data.coverUrl)
                    },
                )
            }

            addToShortcuts?.let { req ->
                ContainerPickerDialog(
                    gameName = req.gameName,
                    containers = req.containers,
                    onDismiss = { addToShortcuts = null },
                    onSelected = { container ->
                        addToShortcuts = null
                        val a = activity ?: return@ContainerPickerDialog
                        StarLaunchBridge.writeShortcutAsync(
                            a, container, req.gameName, req.exePath, req.coverUrl,
                        ) { success, message ->
                            addResult = AddShortcutResult(req.gameName, success, message)
                        }
                    },
                )
            }

            addResult?.let { result ->
                AddResultDialog(
                    result = result,
                    onOpenShortcuts = {
                        addResult = null
                        activity?.let { openShortcutsScreen(it) }
                    },
                    onDismiss = { addResult = null },
                )
            }

            uninstallingName?.let { UninstallProgressDialog(it) }
        }
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The prototype's library card: capsule art, name, and a single action — Download (opens the detail
 * screen, where the real download UI lives) or Play for an already-installed game.
 */
@Composable
private fun LibraryCapsuleCard(
    game: SteamGame,
    onOpen: () -> Unit,
    onAction: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .steamFocusRing(shape)
            .deckTileFocus(interaction)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    ) {
        StoreCapsule(
            appId = game.appId,
            title = game.name.ifEmpty { "App ${game.appId}" },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = game.name.ifEmpty { "App ${game.appId}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (game.isInstalled) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().steamFocusRing(RoundedCornerShape(7.dp)),
                    shape = RoundedCornerShape(7.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) { Text("Play", style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            } else {
                StoreActionButton(
                    action = StoreAction.Download,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            }
        }
    }
}

/**
 * The detailed list row — the original [SteamGamesActivity] `GameListItem`, moved. Kept because it
 * is the only surface carrying developer / genres / size / Metacritic and the Uninstall action.
 */
@Composable
private fun LibraryDetailRow(
    game: SteamGame,
    onClick: () -> Unit,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .steamFocusRing(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            GameCoverArt(
                appId = game.appId,
                modifier = Modifier.size(width = 60.dp, height = 80.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name.ifEmpty { "App ${game.appId}" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.developer.isNotEmpty()) MutedLine(game.developer)
                if (game.genres.isNotEmpty()) MutedLine(game.genres)
                if (game.sizeBytes > 0) MutedLine(fmtLibrarySize(game.sizeBytes))
                if (game.metacriticScore > 0) {
                    Text(
                        text = "Metacritic: ${game.metacriticScore}",
                        style = MaterialTheme.typography.bodySmall,
                        // Semantic review-score colours, deliberately not themed (unchanged).
                        color = when {
                            game.metacriticScore >= 75 -> Color(0xFF4CAF50)
                            game.metacriticScore >= 50 -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        },
                    )
                }
                if (game.isInstalled) {
                    Text(
                        text = "● Installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50), // semantic installed-green (unchanged)
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onLaunch,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp).steamFocusRing(RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        ) { Text("Launch / Add", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = onUninstall,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.height(32.dp).steamFocusRing(RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        ) { Text("Uninstall", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MutedLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ── Dialogs / helpers moved from SteamGamesActivity ───────────────────────────────────────────

internal data class SteamExePickerData(
    val gameName: String,
    val candidates: List<String>,
    val coverUrl: String,
)

@Composable
private fun ExePickerDialog(
    title: String,
    candidates: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Long lists (HL2's dozens of bin/*.exe SDK tools) must scroll or the game exe below
            // the fold is unreachable. Cap at ~half the screen so it fits in landscape too.
            val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
            Column(
                modifier = Modifier
                    .heightIn(max = maxListHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                candidates.forEach { path ->
                    val f = java.io.File(path)
                    val parent = f.parentFile
                    val label = if (parent != null) "${parent.name}/${f.name}" else f.name
                    TextButton(
                        onClick = { onSelected(path) },
                        modifier = Modifier.fillMaxWidth().steamFocusRing(RoundedCornerShape(8.dp)),
                    ) { Text(label, modifier = Modifier.weight(1f)) }
                }
            }
        },
        confirmButton = {},
    )
}

internal fun fmtLibrarySize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.0f KB".format(bytes / 1024.0)
}

/** Sign-out confirmation, moved with the rest of the library chrome. */
@Composable
internal fun SteamSignOutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out of Steam?") },
        text = { Text("Your saved login will be removed. You will need to sign in again.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Sign Out") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Intent into the shared game-detail destination — the storefront's one "open a title" path. */
internal fun openSteamGameDetail(ctx: android.content.Context, appId: Int) {
    runCatching {
        ctx.startActivity(
            Intent(ctx, SteamGameDetailActivity::class.java)
                .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, appId),
        )
    }
}
