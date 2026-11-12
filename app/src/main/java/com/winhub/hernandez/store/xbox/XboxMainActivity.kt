package com.winhub.hernandez.store.xbox

import com.winhub.hernandez.store.xmbRailStop
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.store.CatalogAction
import com.winhub.hernandez.store.CatalogHeroCard
import com.winhub.hernandez.store.CatalogItem
import com.winhub.hernandez.store.CatalogLibraryTile
import com.winhub.hernandez.store.CatalogResults
import com.winhub.hernandez.store.StoreXmbTheme
import com.winhub.hernandez.store.storeGridCell
import com.winhub.hernandez.store.CatalogSearchField
import com.winhub.hernandez.store.StoreAvatar
import com.winhub.hernandez.store.Header
import com.winhub.hernandez.store.StoreFilterChip
import com.winhub.hernandez.store.trackedGridState
import com.winhub.hernandez.store.trackedListState
import com.winhub.hernandez.store.rememberXmbHeader
import com.winhub.hernandez.store.StoreNotice
import com.winhub.hernandez.store.StoreRailOverflow
import com.winhub.hernandez.store.StoreSectionHost
import com.winhub.hernandez.store.StoreSectionTab
import com.winhub.hernandez.store.catalogRail
import com.winhub.hernandez.store.download.DownloadRegistry
import androidx.compose.runtime.collectAsState
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreSection
import com.winhub.hernandez.store.steamFocusRing
import com.winhub.hernandez.store.DeckStoreTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Xbox / PC Game Pass section: the same Store / Library / Profile shell as GOG, Epic and Amazon
 * ([StoreSectionHost]). Sign-in goes through [XboxLoginActivity]; every title opens [XboxGameDetailActivity].
 * Games are PC (MSIXVC) packages downloaded with the user's own license and run in a Wine container.
 */
class XboxMainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "xbox_tab"
        const val PREF_CLOUD_SAVES = "xbox_cloud_saves"

        fun prefs(ctx: Context) = ctx.getSharedPreferences("xbox", Context.MODE_PRIVATE)
        fun cloudSaves(ctx: Context) = prefs(ctx).getBoolean(PREF_CLOUD_SAVES, false)

        fun detailIntent(ctx: Context, game: XboxGame): Intent = Intent(ctx, XboxGameDetailActivity::class.java)
            .putExtra("id", game.id).putExtra("title", game.title).putExtra("hero", game.heroUrl)
            .putExtra("poster", game.posterUrl).putExtra("publisher", game.publisher).putExtra("developer", game.developer)
            .putExtra("description", game.description).putExtra("size", game.sizeBytes).putExtra("market", game.market)
            .putExtra("access", game.access).putExtra("pc", game.pc).putExtra("cloud", game.cloud).putExtra("cloudId", game.cloudId)

        fun toCatalogItem(game: XboxGame, installed: Boolean = false) = CatalogItem(
            store = Store.XBOX, id = game.id, title = game.title, imageUrl = game.heroUrl, tallImageUrl = game.posterUrl,
            tags = listOfNotNull(
                when { game.gamePass -> if (game.pc) "PC Game Pass" else "Game Pass"; game.owned -> "Owned"; else -> null },
                when { game.cloud && !game.pc -> "Cloud only"; game.cloud -> "Cloud"; else -> null },
            ).joinToString(" · "),
            storeUrl = game.storeUrl, developer = game.developer, description = game.description,
            extra = mapOf("access" to game.access, "installed" to installed.toString()),
        )
    }

    private var account by mutableStateOf<String?>(null)
    private var shownName by mutableStateOf("")
    private var startTab by mutableStateOf(1)
    private var message by mutableStateOf<String?>(null)

    // XMB home: L2 / R2 (buttons or analog triggers) step through the rail's tabs; see StoreXmbInput.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        com.winhub.hernandez.store.StoreXmbInput.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        com.winhub.hernandez.store.StoreXmbInput.onMotion(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        DownloadRegistry.init(this)
        XboxDownloads.seed(this)
        startTab = intent?.getIntExtra(EXTRA_TAB, 1) ?: 1
        refreshAccount()
        setContent {
            com.winhub.hernandez.store.StoreXmbTheme {
                DeckStoreTheme {
                val signedIn = account
                if (signedIn != null) {
                    XboxStorefrontHost(
                        initialTab = startTab,
                        account = signedIn,
                        shownName = shownName.ifBlank { XboxAccountStore.mask(signedIn) },
                        externalMessage = message,
                        onExternalMessageShown = { message = null },
                        onSignOut = { signOut() },
                        onClose = { finish() },
                    )
                } else {
                    XboxLoginScreen(
                        backendReady = Xodus.isAvailable(this),
                        message = message,
                        onLoginClick = { startActivity(Intent(this, XboxLoginActivity::class.java)) },
                    )
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAccount()
    }

    private fun refreshAccount() {
        Thread {
            val app = applicationContext
            val name = XboxAccountStore.signedInAs(app)
            runOnUiThread { account = name; shownName = XboxAccountStore.displayName(app).orEmpty() }
            // Accounts signed in before the gamertag was kept get it now.
            if (name != null && XboxAccountStore.read(app)?.optString("gamertag").isNullOrBlank() && XboxAccountStore.refreshGamertag(app))
                runOnUiThread { shownName = XboxAccountStore.displayName(app).orEmpty() }
        }.start()
    }

    private fun signOut() {
        val app = applicationContext
        Thread {
            XboxCredentials.invalidate(app) {
                XboxLibraryCache.clear(app)
                XboxRuntime.stop()
                XboxCloudActivity.clearWebSignIn(app)
                XboxCloudSession.clear()
            }
            XboxDiag.log(app, "signed out")
            runOnUiThread { account = null; message = "Signed out of Xbox. Installed games stay installed." }
        }.start()
    }
}

private val XBOX_TABS = listOf(
    StoreSectionTab("store", "Store", Icons.Filled.Storefront),
    StoreSectionTab("library", "Library", Icons.Filled.VideoLibrary),
    StoreSectionTab("profile", "Profile", Icons.Filled.AccountCircle),
)

/** The library as the tabs see it: cached at once, refreshed from Microsoft in the background. */
class XboxLibraryState(
    val games: List<XboxGame>,
    val installedIds: Set<String>,
    val syncing: Boolean,
    val statusText: String,
    val access: Map<String, String>,
    val refresh: (force: Boolean) -> Unit,
)

@Composable
fun rememberXboxLibrary(account: String, onMessage: (String) -> Unit): XboxLibraryState {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf<List<XboxGame>>(emptyList()) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var syncing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun loadInstalled() { installed = XboxInstallState.all(app).filter { java.io.File(it.folder, it.exe).isFile }.map { it.id }.toSet() }

    fun sync(force: Boolean) {
        if (syncing) return
        syncing = true
        scope.launch {
            val market = Xodus.market()
            val cached = withContext(Dispatchers.IO) { XboxLibraryCache.read(app, account, market) }
            if (cached != null) games = cached.games
            if (cached != null && !cached.needsRefresh(force)) { syncing = false; status = ""; return@launch }
            status = "Syncing your Xbox library…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val response = Xodus.run(app, listOf("library", "--json", "--market", market))
                    val licenses = XboxCatalog.access(response, account, market, report = { XboxDiag.log(app, it) })
                    val products = XboxCatalog.rawProducts(licenses.keys.toList(), market)
                    // The cloud lists are public; without them the library still lists the PC games.
                    val cloud = runCatching { XboxCatalog.cloudCatalog(market) }
                        .onFailure { XboxDiag.log(app, "cloud catalog failed: ${it.javaClass.simpleName}") }.getOrNull()
                    val list = mergeEditions(products.mapNotNull { XboxCatalog.product(it, licenses, market, cloud) }).sortedBy { it.title.lowercase() }
                    XboxDiag.log(app, "library requested=${licenses.size} returned=${products.size} supported=${list.size} " +
                        "pc=${list.count { it.pc }} cloud=${list.count { it.cloud }} cloud-only=${list.count { !it.pc }} merged=${list.count { it.cloudId != null }} " +
                        "cloud-lists=${cloud?.gamePass?.size ?: -1}/${cloud?.all?.size ?: -1}")
                    runCatching { XboxLibraryCache.write(app, account, market, list) }
                    list
                }
            }
            result.onSuccess { list ->
                // Probe: can the store account sign in to Xbox Cloud Gaming by itself? Logs counts only.
                launch(Dispatchers.IO) {
                    runCatching {
                        val session = XboxCloudSession.get(app, account)
                        val titles = XboxCloudSession.titles(session)
                        XboxDiag.log(app, "cloud session ok offering=${session.offering} regions=${session.regions} transfer=${session.userToken != null} " +
                            "titles=${titles.size} in-library=${list.count { (it.cloudId ?: it.id) in titles.keys }}")
                    }.onFailure { XboxDiag.log(app, "cloud session failed: ${it.javaClass.simpleName}: ${it.message?.take(160)}") }
                }
            }
            result.onSuccess { games = it; status = "" }
                .onFailure { status = ""; onMessage(it.message ?: "The Xbox library could not be loaded.") }
            syncing = false
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) loadInstalled() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(account) { loadInstalled(); sync(false) }

    val access = remember(games) { games.associate { it.id to it.access } }
    return XboxLibraryState(games, installed, syncing, status, access, ::sync)
}

@Composable
private fun XboxStorefrontHost(
    initialTab: Int,
    account: String,
    shownName: String,
    externalMessage: String?,
    onExternalMessageShown: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, XBOX_TABS.size - 1)) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(externalMessage) { if (externalMessage != null) { message = externalMessage; onExternalMessageShown() } }
    val library = rememberXboxLibrary(account, onMessage = { message = it })
    fun open(game: XboxGame) = ctx.startActivity(XboxMainActivity.detailIntent(ctx, game))

    StoreSectionHost(
        storeName = "Xbox",
        tabs = XBOX_TABS,
        selected = tab,
        onSelect = { tab = it },
        onClose = onClose,
        counts = mapOf("library" to library.games.size),
        statusSlot = {
            Text(shownName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        },
        railStatusSlot = { StoreAvatar(emptyList(), shownName, 36.dp) },
        actions = {
            IconButton(onClick = { library.refresh(true) }, modifier = Modifier.steamFocusRing().xmbRailStop()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync library", tint = MaterialTheme.colorScheme.primary)
            }
            DownloadsButton(Modifier.xmbRailStop(ring = true))
            IconButton(onClick = { ctx.startActivity(XboxCloudActivity.intent(ctx)) }, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.Filled.Cloud, contentDescription = "Xbox Cloud Gaming", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onSignOut, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = MaterialTheme.colorScheme.primary)
            }
        },
        railActions = {
            IconButton(onClick = { library.refresh(true) }, modifier = Modifier.steamFocusRing().xmbRailStop()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync library", tint = MaterialTheme.colorScheme.primary)
            }
            DownloadsButton(Modifier.xmbRailStop(ring = true))
            StoreRailOverflow(items = listOf(Triple("Xbox Cloud Gaming", Icons.Filled.Cloud) { ctx.startActivity(XboxCloudActivity.intent(ctx)) }), onSignOut = onSignOut)
        },
        message = message,
        onMessageTimeout = { message = null },
    ) { wide, mod ->
        when (XBOX_TABS[tab].key) {
            "store" -> XboxStoreTab(library, wide, ::open, mod)
            "library" -> XboxLibraryTab(library, wide, ::open, mod)
            else -> XboxProfileTab(shownName, library, mod)
        }
    }
}

@Composable
private fun XboxLibraryTab(state: XboxLibraryState, wide: Boolean, onOpen: (XboxGame) -> Unit, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) }
    val shown = remember(state.games, state.installedIds, query, filter) {
        val q = query.trim().lowercase()
        state.games.filter { g ->
            when (filter) { 1 -> g.id in state.installedIds; 2 -> g.gamePass; 3 -> g.owned; 4 -> g.cloud; else -> true } &&
                (q.isEmpty() || g.title.lowercase().contains(q))
        }
    }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val header = rememberXmbHeader()
        header.Header {
            CatalogSearchField(query = query, placeholder = "Search your Xbox library…", onQueryChange = { query = it },
                onClear = { query = ""; focusManager.clearFocus() }, onSearch = { focusManager.clearFocus() })
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoreFilterChip("All", filter == 0) { filter = 0 }
                StoreFilterChip("Installed", filter == 1) { filter = 1 }
                StoreFilterChip("Game Pass", filter == 2) { filter = 2 }
                StoreFilterChip("Owned", filter == 3) { filter = 3 }
                StoreFilterChip("Cloud", filter == 4) { filter = 4 }
                Spacer(Modifier.weight(1f))
                Text(state.statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        val downloads by DownloadRegistry.entries.collectAsState()
        when {
            state.games.isEmpty() && state.syncing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StoreNotice(title = "Syncing your library", body = "Fetching the games your account owns or has through Game Pass…")
            }
            state.games.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StoreNotice(title = "No games found yet",
                    body = "Games with a PC version, or that stream with Xbox Cloud Gaming, are listed.",
                    actionLabel = "Sync now", onAction = { state.refresh(true) })
            }
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StoreNotice(title = "No matches", body = "Nothing in your library matches that filter.")
            }
            else -> LazyVerticalGrid(
                state = header.trackedGridState(),
                columns = GridCells.Adaptive(storeGridCell(wide)),
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { g ->
                    val installed = g.id in state.installedIds
                    val download = downloads.firstOrNull { it.store == com.winhub.hernandez.store.download.Store.XBOX && it.id == g.id }
                        ?.takeIf { it.state == com.winhub.hernandez.store.download.DownloadState.DOWNLOADING ||
                            it.state == com.winhub.hernandez.store.download.DownloadState.QUEUED ||
                            it.state == com.winhub.hernandez.store.download.DownloadState.PAUSED }
                    CatalogLibraryTile(item = XboxMainActivity.toCatalogItem(g, installed), installed = installed,
                        onOpen = { onOpen(g) }, badgeText = listOfNotNull(if (g.gamePass) "Game Pass" else null, if (g.cloud) "Cloud" else null).joinToString(" · ").ifEmpty { null },
                        progressLabel = download?.let { "Downloading ${it.pct}%" }, artOnly = true)
                }
            }
        }
    }
}

@Composable
private fun XboxStoreTab(state: XboxLibraryState, wide: Boolean, onOpen: (XboxGame) -> Unit, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var featured by remember { mutableStateOf<List<XboxGame>>(emptyList()) }
    var featuredError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<XboxGame>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(state.access) {
        val r = withContext(Dispatchers.IO) { runCatching { XboxCatalog.featured(state.access) } }
        r.onSuccess { featured = it; featuredError = null }.onFailure { featuredError = it.message }
    }
    fun action(item: CatalogItem): CatalogAction =
        if (item.extra["access"].isNullOrEmpty()) CatalogAction.ViewOnStore else CatalogAction.Open(item.extra["installed"] == "true")
    val byId = remember(featured, results) { (featured + results.orEmpty()).associateBy { it.id } }
    // A library copy knows whether the game streams; catalog results do not.
    fun open(item: CatalogItem) { (state.games.firstOrNull { it.id == item.id } ?: byId[item.id])?.let(onOpen) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val header = rememberXmbHeader()
        header.Header {
            CatalogSearchField(query = query, placeholder = "Search PC games on the Microsoft Store…",
                onQueryChange = { query = it; if (it.isBlank()) results = null },
                onClear = { query = ""; results = null; focusManager.clearFocus() },
                onSearch = {
                    focusManager.clearFocus()
                    val q = query.trim()
                    if (q.length >= 2) {
                        searching = true
                        scope.launch {
                            results = withContext(Dispatchers.IO) { runCatching { XboxCatalog.search(q, state.access) }.getOrDefault(emptyList()) }
                            searching = false
                        }
                    }
                })
        }
        val list = results
        if (list != null || searching) {
            CatalogResults(results = list.orEmpty().map { XboxMainActivity.toCatalogItem(it, it.id in state.installedIds) },
                searching = searching, query = query, wide = wide, storeLabel = "Xbox",
                emptyBody = "No PC games match. Console-only titles are not listed.",
                actionFor = ::action, onOpen = ::open, onAction = ::open)
        } else LazyColumn(Modifier.fillMaxSize(), state = header.trackedListState(), contentPadding = PaddingValues(bottom = 24.dp)) {
            val owned = state.games.filter { it.id !in state.installedIds }.take(20).map { XboxMainActivity.toCatalogItem(it) }
            val installed = state.games.filter { it.id in state.installedIds }.map { XboxMainActivity.toCatalogItem(it, true) }
            val trending = featured.map { XboxMainActivity.toCatalogItem(it, it.id in state.installedIds) }
            trending.firstOrNull()?.let { hero ->
                item(key = "hero") {
                    CatalogHeroCard(item = hero, action = action(hero), storeLabel = "Xbox", onOpen = { open(hero) },
                        onAction = { open(hero) }, modifier = Modifier.padding(14.dp), eyebrow = "TRENDING ON PC")
                }
            }
            if (installed.isNotEmpty()) catalogRail("Installed", "Ready to play", installed, "Xbox", ::action, { open(it) }, { open(it) })
            if (owned.isNotEmpty()) catalogRail("In your library", "Owned and PC Game Pass", owned, "Xbox", ::action, { open(it) }, { open(it) })
            if (trending.size > 1) catalogRail("Trending", "Popular PC games on the Microsoft Store", trending.drop(1), "Xbox", ::action, { open(it) }, { open(it) })
            featuredError?.let { err ->
                item(key = "err") { StoreNotice(title = "Microsoft Store unavailable", body = err, modifier = Modifier.padding(14.dp)) }
            }
        }
    }
}

@Composable
private fun XboxProfileTab(account: String, state: XboxLibraryState, modifier: Modifier) {
    val ctx = LocalContext.current
    var cloud by remember { mutableStateOf(XboxMainActivity.cloudSaves(ctx)) }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
        StoreSection(title = "Account") {
            Text(account, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("${state.games.size} PC games · ${state.games.count { it.gamePass }} through PC Game Pass · ${state.installedIds.size} installed",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StoreSection(title = "Cloud saves (experimental)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sync game saves with Xbox cloud", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = cloud, onCheckedChange = { cloud = it; XboxMainActivity.prefs(ctx).edit().putBoolean(XboxMainActivity.PREF_CLOUD_SAVES, it).apply() })
            }
            Text("Off by default. Saves then stay on this device. Turning it on can overwrite your account's cloud saves; " +
                "use a test account first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StoreSection(title = "Cloud gaming") {
            Text("Resolution, region, bitrate, picture effects, stats and sound for streamed games.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { ctx.startActivity(android.content.Intent(ctx, XboxCloudSettingsActivity::class.java)) },
                modifier = Modifier.steamFocusRing()) { Text("Cloud gaming settings") }
        }
        StoreSection(title = "About") {
            Text("PC games from the Microsoft Store and PC Game Pass, downloaded with your own license and run in a Wine " +
                "container. No console emulation; UWP/APPX apps are not supported. Microsoft may not welcome third-party " +
                "clients; use an account you can afford to lose.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Backend: Xodus (GPL-3.0) with Xodus Nexus (MIT) and DroidDeck's Android adaptation (GPL-3.0). " +
                "Cloud player: Greenlight's WebRTC client by UnknownSKL (MIT); stream settings and sharpening after " +
                "Better xCloud by redphx (MIT). " +
                "Diagnostics: Download/Bannerlator/xbox_diag.txt", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun XboxLoginScreen(backendReady: Boolean, message: String?, onLoginClick: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Xbox", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Sign in with your Microsoft account to download the PC games you own or have through PC Game Pass.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Experimental. Use an account you can afford to lose.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            message?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onLoginClick, enabled = backendReady, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp).steamFocusRing(RoundedCornerShape(8.dp))) { Text("Sign in with Microsoft") }
            if (!backendReady) {
                Spacer(Modifier.height(8.dp))
                Text("The Xbox backend is missing from this build.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
