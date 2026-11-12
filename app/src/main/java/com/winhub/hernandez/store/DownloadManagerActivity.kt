package com.winhub.hernandez.store

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winhub.hernandez.store.compose.AddResultDialog
import com.winhub.hernandez.store.compose.AddShortcutResult
import com.winhub.hernandez.store.compose.AddToShortcutsRequest
import com.winhub.hernandez.store.compose.DeckAddToShortcuts
import com.winhub.hernandez.store.compose.openShortcutsScreen
import com.winhub.hernandez.store.download.DownloadEntry
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreStyle
import com.winhub.hernandez.store.download.formatDownloadSize
import com.winhub.hernandez.store.download.formatDownloadSpeed
import com.winhub.hernandez.store.download.formatEta
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.ui.LocalXmbHintInset
import com.winhub.hernandez.ui.deck.LocalXmbGroup
import com.winhub.hernandez.ui.deck.ScreenScale
import com.winhub.hernandez.ui.deck.XmbHighlightHost
import com.winhub.hernandez.ui.deck.XmbScreenWindow
import com.winhub.hernandez.ui.deck.deckFocusRing
import com.winhub.hernandez.ui.deck.isXmbHome
import com.winhub.hernandez.ui.deck.rememberScreenScale
import com.winhub.hernandez.ui.deck.xmbFrame
import com.winhub.hernandez.ui.deck.XmbGlassTheme
import com.winhub.hernandez.ui.deck.xmbGlassBackground
import com.winhub.hernandez.ui.deck.xmbInner
import com.winhub.hernandez.ui.theme.LocalAccentDim
import com.winhub.hernandez.ui.theme.WinlatorTheme
import java.io.File

/**
 * Phase 3 — the cross-store Download Manager screen. A thin Compose consumer of the
 * store-agnostic [DownloadRegistry]: it collects [DownloadRegistry.entries] and renders
 * each row with the same card idiom as the Steam library list. All store wiring lives in
 * the producers (Phase 2); this Activity only reads the registry and calls back the
 * per-entry `pause`/`cancel` handles the producer attached.
 *
 * Launch / uninstall reuse the exact Steam flow the detail/library screens use
 * (AmazonLaunchHelper exe-scan → container picker → StarLaunchBridge shortcut write,
 * Goldberg-aware via [GoldbergPatcher.resolveLaunchExe]); Steam-specific DB ops are gated
 * on `entry.store == STEAM`. Lives in the `store` package (not `store.download`) so it can
 * reach those package-private Java launch helpers, exactly like the other Steam Activities.
 * Epic/GOG/Amazon land in a later phase.
 */
class DownloadManagerActivity : ComponentActivity() {

    // Add-to-shortcuts flow state (mirrors SteamGamesActivity / SteamGameDetailActivity).
    private var showExePicker by mutableStateOf<ExePickerDataDm?>(null)
    private var addToShortcuts by mutableStateOf<AddToShortcutsRequest?>(null)
    private var addResult by mutableStateOf<AddShortcutResult?>(null)
    // Non-null while an uninstall is deleting files → shows the blocking progress spinner.
    private var uninstallingName by mutableStateOf<String?>(null)
    // Non-null briefly after an uninstall → themed auto-dismiss confirmation bar (not a Toast).
    private var uninstallResult by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Idempotent: seeds the durable library so the screen has rows even if opened
        // before any producer touched the registry this session.
        SteamPrefs.init(this)
        SteamRepository.getInstance().initialize(this)
        DownloadRegistry.init(this)
        // Seed non-Steam libraries too, so opening the Manager directly (without visiting the
        // Amazon store first) still self-heals orphaned installs and surfaces update-available.
        // Idempotent; never throws into startup.
        AmazonLibrarySync.seed(this)
        GogLibrarySync.seed(this)
        EpicLibrarySync.seed(this)
        com.winhub.hernandez.store.xbox.XboxDownloads.seed(this)
        // XMB store style: this screen follows the app's orientation setting like the store screens do.
        val xmbHome = isXmbHome(this)
        if (xmbHome) com.winhub.hernandez.core.AppOrientation.apply(this)

        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                // XMB store style: the page sits on the charcoal glass and its cards are glass over it (a no-op otherwise).
                XmbGlassTheme(xmbHome) {
                    DownloadManagerScreen(
                        xmbHome = xmbHome,
                        onBack = { finish() },
                        onClear = { clearTerminalAndLibrary() },
                        onEntryClick = { openDetail(it) },
                        onLaunch = { launch(it) },
                        onUninstall = { uninstall(it) },
                        onCancel = { cancelEntry(it) },
                        onPauseResume = { it.pause?.invoke() },
                        onDismiss = { DownloadRegistry.remove(it.key) },
                        // Queue reorder (Steam-only; a no-op for any id that isn't a live queued Steam appId).
                        onMoveUp = { it.id.toIntOrNull()?.let { id -> DownloadQueue.moveUp(id) } },
                        onMoveToTop = { it.id.toIntOrNull()?.let { id -> DownloadQueue.moveToTop(id) } },
                    )
                }

                showExePicker?.let { data ->
                    ExePickerDialogDm(
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
                uninstallResult?.let { UninstallResultBar(it) { uninstallResult = null } }

                addToShortcuts?.let { req ->
                    DeckAddToShortcuts(req, null) { addToShortcuts = null }
                }

                addResult?.let { result ->
                    AddResultDialog(
                        result = result,
                        onOpenShortcuts = {
                            addResult = null
                            openShortcutsScreen(this@DownloadManagerActivity)
                        },
                        onDismiss = { addResult = null },
                    )
                }
            
                }}
        }
    }

    /** Clear ✓: drop terminal FAILED/CANCELLED rows and wipe the installed library,
     *  leaving active downloads untouched (BannerHub "clear finished" semantics). */
    private fun clearTerminalAndLibrary() {
        DownloadRegistry.entries.value.forEach { e ->
            if (e.state == DownloadState.FAILED || e.state == DownloadState.CANCELLED) {
                DownloadRegistry.remove(e.key)
            }
        }
        DownloadRegistry.clearLibrary()
    }

    /** Tap a card → open that game's detail in its store (works for downloading + installed). */
    private fun openDetail(entry: DownloadEntry) {
        when (entry.store) {
            Store.STEAM -> startActivity(
                Intent(this, SteamGameDetailActivity::class.java)
                    .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, entry.id.toIntOrNull() ?: 0),
            )
            Store.AMAZON -> {
                // The card only carries id(=productId)/name/cover; hydrate the rest of the detail
                // extras from amazon_library_cache. Fall back to the entry's own fields so the page
                // still opens (with a functional install/launch) even if the cache is unavailable.
                val d = AmazonLibrarySync.cachedDetail(this, entry.id)
                val title = d?.title?.takeIf { it.isNotEmpty() } ?: entry.name
                val art = d?.artUrl?.takeIf { it.isNotEmpty() } ?: entry.cover ?: ""
                startActivity(
                    Intent(this, AmazonGameDetailActivity::class.java).apply {
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
            Store.XBOX -> {
                // Installed record first (title, art, market), then the entry's own fields.
                val g = com.winhub.hernandez.store.xbox.XboxInstallState.get(this, entry.id)
                startActivity(com.winhub.hernandez.store.xbox.XboxMainActivity.detailIntent(this,
                    com.winhub.hernandez.store.xbox.XboxGame(entry.id, g?.title ?: entry.name, g?.heroUrl, g?.posterUrl ?: entry.cover,
                        market = g?.market ?: com.winhub.hernandez.store.xbox.Xodus.market(), access = g?.access ?: "")))
            }
            Store.GOG -> {
                // Hydrate the detail extras from gog_library_cache; fall back to the entry's own
                // fields so the page still opens even if the cache is unavailable.
                val d = GogLibrarySync.cachedDetail(this, entry.id)
                val title = d?.title?.takeIf { it.isNotEmpty() } ?: entry.name
                val image = d?.imageUrl?.takeIf { it.isNotEmpty() } ?: entry.cover ?: ""
                startActivity(
                    Intent(this, GogGameDetailActivity::class.java).apply {
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
            Store.EPIC -> {
                // Hydrate the detail extras from epic_cache; fall back to the entry's own fields so
                // the page still opens even if the cache is unavailable.
                val d = EpicLibrarySync.cachedDetail(this, entry.id)
                val title = d?.title?.takeIf { it.isNotEmpty() } ?: entry.name
                val art = d?.artCover?.takeIf { it.isNotEmpty() } ?: entry.cover ?: ""
                startActivity(
                    Intent(this, EpicGameDetailActivity::class.java).apply {
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
    private fun launch(entry: DownloadEntry) {
        val dir = entry.installPath
        if (dir.isNullOrEmpty()) {
            uninstallResult = "Install directory not set"
            return
        }
        if (entry.store == Store.XBOX) {
            // Xbox: the launch exe comes from MicrosoftGame.config and the shortcut carries the product id.
            val g = com.winhub.hernandez.store.xbox.XboxInstallState.get(this, entry.id)
            if (g == null) { uninstallResult = "This Xbox game is not installed"; return }
            StarLaunchBridge.addToLauncher(this, g.title, File(g.folder, g.exe).path, g.posterUrl ?: g.heroUrl, "xbox:${g.id}", false)
            return
        }
        val appId = entry.id.toIntOrNull() ?: 0
        val installDir = File(dir)
        Thread {
            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(installDir, exeFiles)
            if (exeFiles.isEmpty()) {
                runOnUiThread {
                    uninstallResult = "No .exe found in install directory"
                }
                return@Thread
            }
            val lowerTitle = entry.name.lowercase()
            exeFiles.sortWith { a, b ->
                AmazonLaunchHelper.scoreExe(b, lowerTitle) - AmazonLaunchHelper.scoreExe(a, lowerTitle)
            }
            val coverUrl = "https://shared.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900.jpg"

            if (exeFiles.size == 1) {
                runOnUiThread { startAddToShortcuts(appId, entry.name, exeFiles[0].absolutePath, coverUrl) }
                return@Thread
            }
            val candidates = exeFiles.map { it.absolutePath }
            runOnUiThread { showExePicker = ExePickerDataDm(appId, entry.name, candidates, coverUrl) }
        }.start()
    }

    /** Load containers, then show the M3 picker. Goldberg Cold-Client rewrites the exe. */
    private fun startAddToShortcuts(appId: Int, gameName: String, exePath: String, coverUrl: String?) {
        val launchExe = GoldbergPatcher.resolveLaunchExe(this, appId, exePath)
        StarLaunchBridge.loadContainers(this) { containers ->
            addToShortcuts = AddToShortcutsRequest(gameName, launchExe, coverUrl, containers)
        }
    }

    /** Uninstall: mark uninstalled in the store DB and delete the install dir off the UI
     *  thread; once the delete is VERIFIED complete, drop the durable-library row (which also
     *  removes it from the live registry), dismiss the spinner, and toast the real outcome. */
    private fun uninstall(entry: DownloadEntry) {
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
            uninstallResult = if (ok) "${entry.name} uninstalled" else "Couldn't fully remove ${entry.name}"
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
            Store.AMAZON -> AmazonInstallState.purge(this, entry.id)
            Store.GOG -> GogInstallState.purge(this, entry.id)
            Store.EPIC -> EpicInstallState.purge(this, entry.id)
            Store.XBOX -> com.winhub.hernandez.store.xbox.XboxInstallState.remove(this, entry.id)
            Store.STEAM -> Unit   // handled via SteamRepository.markUninstalled in `mark`
        }
    }

    /**
     * Cancel a download from the Download Manager. For Epic this raises the same keep/delete dialog
     * as the detail page + library list (synced via [EpicCancelPolicy]); the running download
     * coroutine acts on the choice. Other stores cancel directly (their keep/delete semantics are
     * unchanged).
     */
    private fun cancelEntry(entry: DownloadEntry) {
        if (entry.store == Store.EPIC) {
            AlertDialog.Builder(this)
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
}

private data class ExePickerDataDm(
    val appId: Int,
    val gameName: String,
    val candidates: List<String>,
    val coverUrl: String,
)

// --- Composable screen ---

/**
 * Three sections, top to bottom: the ACTIVE download (downloading/paused), the managed QUEUE (waiting
 * downloads, in FIFO/position order), then the installed Library (+ any failed/cancelled rows). Headers
 * only appear when a later group follows, so a pure library list stays header-less.
 */
private class DmSections(entries: List<DownloadEntry>) {
    val downloading = entries.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED }
    // Registry's default sort is by name; a FIFO must order by queue position instead.
    val queued = entries.filter { it.state == DownloadState.QUEUED }.sortedBy { it.queuePosition }
    val rest = entries.filterNot { it.isActive }
    val hasHeaders = downloading.isNotEmpty() || queued.isNotEmpty()
}

/**
 * Card sizes: the fixed ones of the classic list, or (Deck XMB home) shares of the screen within limits, with the
 * Deck focus ring. Text follows the same screen factor through the density the XMB layout provides.
 */
private class DmLook(
    val coverW: Dp,
    val coverH: Dp,
    val cardPadH: Dp,
    val cardPadV: Dp,
    val inner: Dp,
    val gap: Dp,
    val buttonH: Dp,
    val buttonPadH: Dp,
    val focus: Boolean,
)

private val CLASSIC_LOOK = DmLook(60.dp, 80.dp, 16.dp, 6.dp, 12.dp, 12.dp, 32.dp, 12.dp, focus = false)

private fun xmbLook(s: ScreenScale): DmLook {
    val coverH = s.h(0.22f, min = 96.dp, max = 180.dp)
    return DmLook(
        coverW = coverH * 0.75f,
        coverH = coverH,
        // At least 12 dp / 10 dp round a card: its glass shadow reaches that far and the grid would clip it.
        cardPadH = maxOf(s.dp(8f), 12.dp),
        cardPadV = maxOf(s.dp(6f), 10.dp),
        inner = s.dp(12f),
        gap = s.dp(12f),
        // Never under a comfortable touch target, however small the screen.
        buttonH = maxOf(s.dp(34f), 36.dp),
        buttonPadH = s.dp(12f),
        focus = true,
    )
}

/** The Deck focus ring with a slight pop, only in the XMB-home look. Put it before the clickable. */
@Composable
private fun Modifier.dmFocus(look: DmLook, shape: Shape): Modifier =
    if (look.focus) deckFocusRing(shape, scaleTo = 1.03f) else this

/**
 * A card button's size (and, in the XMB-home look, its focus ring). In the XMB look a card is one D-pad stop and its buttons
 * take focus only after A ([xmbInner]; [first] is the one A lands on); elsewhere [xmbInner] changes nothing.
 */
@Composable
private fun Modifier.dmButton(look: DmLook, first: Boolean = false): Modifier =
    dmFocus(look, RoundedCornerShape(8.dp)).xmbInner(first = first).height(look.buttonH)

/** XMB cards only (inside a group item): the controller's way to open the game's page, as a tap on the card does. */
@Composable
private fun DetailsButton(entry: DownloadEntry, onOpen: (DownloadEntry) -> Unit, look: DmLook) {
    if (LocalXmbGroup.current == null) return
    OutlinedButton(
        onClick = { onOpen(entry) },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.dmButton(look, first = true),
        contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
    ) { Text(stringResource(R.string.xmb_store_details), style = MaterialTheme.typography.labelSmall) }
}

/** XMB home: a card of the Downloads page, one D-pad stop (the highlight glides card to card, also across the two columns). */
@Composable
private fun XmbDmCard(look: DmLook, onOpen: () -> Unit, content: @Composable () -> Unit) {
    XmbPanelCard(Modifier.fillMaxWidth().padding(horizontal = look.cardPadH, vertical = look.cardPadV), onOpen, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadManagerScreen(
    xmbHome: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onEntryClick: (DownloadEntry) -> Unit,
    onLaunch: (DownloadEntry) -> Unit,
    onUninstall: (DownloadEntry) -> Unit,
    onCancel: (DownloadEntry) -> Unit,
    onPauseResume: (DownloadEntry) -> Unit,
    onDismiss: (DownloadEntry) -> Unit,
    onMoveUp: (DownloadEntry) -> Unit,
    onMoveToTop: (DownloadEntry) -> Unit,
) {
    val entries by DownloadRegistry.entries.collectAsStateWithLifecycle()

    if (xmbHome) {
        // Deck XMB home: edge to edge under the camera with no system bars, the page in the Deck's even frame, two
        // cards per row in landscape, every size a share of the screen.
        XmbScreenWindow(true)
        val scale = rememberScreenScale()
        val look = remember(scale) { xmbLook(scale) }
        val frame = xmbFrame()
        val density = LocalDensity.current
        // The highlight host and the Back hint sit outside the density change below: the hint scales its own text, and the
        // gliding outline is drawn over the whole page (dialogs bring their own host).
        XmbHighlightHost(enabled = true, modifier = Modifier.fillMaxSize()) {
            XmbBackHintLayer(true) {
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale.factor)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .xmbGlassBackground()
                            .padding(start = frame, end = frame, top = scale.dp(6f)),
                    ) {
                        XmbDownloadsBar(onBack, onClear, look)
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (entries.isEmpty()) {
                                Text(
                                    text = "No downloads or installed games yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                )
                            } else {
                                val sections = DmSections(entries)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(if (scale.widthDp > scale.heightDp) 2 else 1),
                                    modifier = Modifier.fillMaxSize(),
                                    // The last card scrolls clear of the floating Back hint.
                                    contentPadding = PaddingValues(bottom = look.cardPadV * 2 + LocalXmbHintInset.current),
                                ) {
                                    if (sections.downloading.isNotEmpty()) {
                                        item(key = "hdr_downloading", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Downloading", look) }
                                        items(sections.downloading, key = { it.key }) { entry ->
                                            DownloadCard(entry, onEntryClick, onLaunch, onUninstall, onCancel, onPauseResume, onDismiss, look)
                                        }
                                    }
                                    if (sections.queued.isNotEmpty()) {
                                        item(key = "hdr_queued", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Queued", look) }
                                        itemsIndexed(sections.queued, key = { _, e -> e.key }) { idx, entry ->
                                            QueuedCard(entry, idx + 1, idx > 0, onEntryClick, onCancel, onMoveUp, onMoveToTop, look)
                                        }
                                    }
                                    if (sections.rest.isNotEmpty()) {
                                        if (sections.hasHeaders) {
                                            item(key = "hdr_library", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Library", look) }
                                        }
                                        items(sections.rest, key = { it.key }) { entry ->
                                            DownloadCard(entry, onEntryClick, onLaunch, onUninstall, onCancel, onPauseResume, onDismiss, look)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Downloads & Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onClear) { Text("Clear ✓") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = "No downloads or installed games yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                // Three sections, top to bottom: the ACTIVE download (downloading/paused), the
                // managed QUEUE (waiting downloads, in FIFO/position order), then the installed
                // Library (+ any failed/cancelled rows). Headers only appear when a later group
                // follows, so a pure library list stays header-less.
                val downloading = entries.filter {
                    it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED
                }
                // Registry's default sort is by name; a FIFO must order by queue position instead.
                val queued = entries.filter { it.state == DownloadState.QUEUED }
                    .sortedBy { it.queuePosition }
                val rest = entries.filterNot { it.isActive }
                val hasHeaders = downloading.isNotEmpty() || queued.isNotEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    if (downloading.isNotEmpty()) {
                        item(key = "hdr_downloading") { SectionHeader("Downloading") }
                        items(downloading, key = { it.key }) { entry ->
                            DownloadCard(entry, onEntryClick, onLaunch, onUninstall, onCancel, onPauseResume, onDismiss)
                        }
                    }
                    if (queued.isNotEmpty()) {
                        item(key = "hdr_queued") { SectionHeader("Queued") }
                        itemsIndexed(queued, key = { _, e -> e.key }) { idx, entry ->
                            QueuedCard(
                                entry = entry,
                                position = idx + 1,
                                canMoveUp = idx > 0,
                                onClick = onEntryClick,
                                onCancel = onCancel,
                                onMoveUp = onMoveUp,
                                onMoveToTop = onMoveToTop,
                            )
                        }
                    }
                    if (rest.isNotEmpty()) {
                        if (hasHeaders) item(key = "hdr_library") { SectionHeader("Library") }
                        items(rest, key = { it.key }) { entry ->
                            DownloadCard(entry, onEntryClick, onLaunch, onUninstall, onCancel, onPauseResume, onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, look: DmLook = CLASSIC_LOOK) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = look.cardPadH, end = look.cardPadH, top = 8.dp, bottom = 2.dp),
    )
}

/** The XMB-home header: back, title and Clear on one slim line over the page background, no app-bar block. */
@Composable
private fun XmbDownloadsBar(onBack: () -> Unit, onClear: () -> Unit, look: DmLook) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = look.cardPadH),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.dmFocus(look, CircleShape)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(look.gap / 2))
        Text(
            text = "Downloads & Library",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear, modifier = Modifier.dmFocus(look, RoundedCornerShape(8.dp))) { Text("Clear ✓") }
    }
}

@Composable
private fun DownloadCard(
    entry: DownloadEntry,
    onClick: (DownloadEntry) -> Unit,
    onLaunch: (DownloadEntry) -> Unit,
    onUninstall: (DownloadEntry) -> Unit,
    onCancel: (DownloadEntry) -> Unit,
    onPauseResume: (DownloadEntry) -> Unit,
    onDismiss: (DownloadEntry) -> Unit,
    look: DmLook = CLASSIC_LOOK,
) {
    // XMB home: a glass panel that is one stop of the D-pad, its buttons reached with A.
    if (look.focus) {
        XmbDmCard(look, onOpen = { onClick(entry) }) {
            DownloadCardBody(entry, onClick, onLaunch, onUninstall, onCancel, onPauseResume, onDismiss, look)
        }
        return
    }
    // Same floating card as SteamGamesActivity.GameListItem: rounded surfaceVariant panel,
    // outline border, side margins, tall poster + info column.
    Card(
        onClick = { onClick(entry) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = look.cardPadH, vertical = look.cardPadV)
            .dmFocus(look, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        DownloadCardBody(entry, onClick, onLaunch, onUninstall, onCancel, onPauseResume, onDismiss, look)
    }
}

/** The content of a [DownloadCard]: cover, name, and the state's text and buttons. */
@Composable
private fun DownloadCardBody(
    entry: DownloadEntry,
    onClick: (DownloadEntry) -> Unit,
    onLaunch: (DownloadEntry) -> Unit,
    onUninstall: (DownloadEntry) -> Unit,
    onCancel: (DownloadEntry) -> Unit,
    onPauseResume: (DownloadEntry) -> Unit,
    onDismiss: (DownloadEntry) -> Unit,
    look: DmLook,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(look.inner),
    ) {
        DownloadCoverArt(
            entry = entry,
            modifier = Modifier
                .size(width = look.coverW, height = look.coverH)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(look.gap))

        Column(modifier = Modifier.weight(1f)) {
            StoreBadge(entry.store)
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.name.ifEmpty { "${entry.store} ${entry.id}" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))

            when (entry.state) {
                DownloadState.DOWNLOADING, DownloadState.PAUSED, DownloadState.QUEUED ->
                    ActiveContent(entry, onClick, onCancel, onPauseResume, look)

                DownloadState.INSTALLED -> {
                    // Amber "Update available" mirrors the store list's own language
                    // (AmazonGamesActivity "✓ Installed — Update Available"); up-to-date
                    // stays installed-green. Store-agnostic: any store that sets
                    // entry.updateAvailable gets the same marker.
                    if (entry.updateAvailable) {
                        Text(
                            text = "● Installed — Update available",
                            style = MaterialTheme.typography.bodySmall,
                            color = UPDATE_AMBER,
                        )
                    } else {
                        Text(
                            text = "● Installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = INSTALLED_GREEN,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailsButton(entry, onClick, look)
                        Button(
                            onClick = { onLaunch(entry) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.dmButton(look),
                            contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
                        ) { Text("Launch", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = { onUninstall(entry) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.dmButton(look),
                            contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
                        ) { Text("Uninstall", style = MaterialTheme.typography.labelSmall) }
                    }
                }

                DownloadState.FAILED -> {
                    Text(
                        text = entry.error?.let { "Failed: $it" } ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailsButton(entry, onClick, look)
                        OutlinedButton(
                            onClick = { onDismiss(entry) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.dmButton(look),
                            contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
                        ) { Text("Dismiss", style = MaterialTheme.typography.labelSmall) }
                    }
                }

                DownloadState.CANCELLED -> {
                    Text(
                        text = "Cancelled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailsButton(entry, onClick, look)
                        OutlinedButton(
                            onClick = { onDismiss(entry) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.dmButton(look),
                            contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
                        ) { Text("Dismiss", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

/** DOWNLOADING/PAUSED/QUEUED body: the two-bar byte model + status text + action buttons. */
@Composable
private fun ActiveContent(
    entry: DownloadEntry,
    onCancel: (DownloadEntry) -> Unit,
    onPauseResume: (DownloadEntry) -> Unit,
) {
    // Byte-driven fraction only when the store actually feeds byte progress (installDone > 0);
    // pct-only stores (GOG reports pct with no byte pairs) fall back to entry.pct so the bar
    // isn't stuck at 0 when a size happens to be known (installTotal > 0 but installDone == 0).
    val hasBytes = entry.installTotal > 0 && entry.installDone > 0
    val installFrac = if (hasBytes)
        (entry.installDone.toFloat() / entry.installTotal) else (entry.pct / 100f)
    val downloadFrac = if (entry.downloadTotal > 0)
        (entry.downloadDone.toFloat() / entry.downloadTotal) else installFrac
    val pct = (installFrac.coerceIn(0f, 1f) * 100).toInt()

    // Overlapping dual bar: the network (download) fill uses the themeable dim-accent underneath
    // so it reads as a DISTINCT second tone (not just a faded primary — that blended into one bar
    // when install% and download% tracked closely); the solid primary on-disk install fill sits on
    // top. Both colors follow the app theme via WinlatorTheme.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(downloadFrac.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(4.dp))
                .background(LocalAccentDim.current),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(installFrac.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
    Spacer(Modifier.height(4.dp))
    // Byte pair only for byte-feeding stores; pct-only stores (GOG) show just the percentage
    // instead of a misleading "(0 KB / 0 KB)".
    val sizePart = if (hasBytes) "  (${fmtSizeDm(entry.installDone)} / ${fmtSizeDm(entry.installTotal)})" else ""
    // Speed + ETA only while actively downloading (unknown when paused/queued).
    val speedEta = buildString {
        val s = formatDownloadSpeed(entry.speedBps); if (s.isNotEmpty()) append("  ·  $s")
        val e = formatEta(entry.etaSeconds);          if (e.isNotEmpty()) append("  ·  $e")
    }
    Text(
        text = when (entry.state) {
            DownloadState.PAUSED -> "Paused — $pct%$sizePart"
            DownloadState.QUEUED -> "Queued…"
            else -> "Downloading… $pct%$sizePart$speedEta"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onCancel(entry) },
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        ) { Text("✕ Cancel", style = MaterialTheme.typography.labelSmall) }

        if (entry.supportsPause) {
            Button(
                onClick = { onPauseResume(entry) },
                shape = RoundedCornerShape(8.dp),
                colors = if (entry.state == DownloadState.PAUSED) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (entry.state == DownloadState.PAUSED) "Resume" else "Pause",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * A waiting (QUEUED) download's card — same floating-card idiom as [DownloadCard], but its body shows
 * the queue position and offers Move-up / Start-next (reorder) + Cancel instead of a progress bar. A
 * queued item holds no worker thread and reports no bytes yet, so there is nothing to render as a bar.
 */
@Composable
private fun QueuedCard(
    entry: DownloadEntry,
    position: Int,
    canMoveUp: Boolean,
    onClick: (DownloadEntry) -> Unit,
    onCancel: (DownloadEntry) -> Unit,
    onMoveUp: (DownloadEntry) -> Unit,
    onMoveToTop: (DownloadEntry) -> Unit,
    look: DmLook = CLASSIC_LOOK,
) {
    // XMB home: a glass panel that is one stop of the D-pad, its buttons reached with A.
    if (look.focus) {
        XmbDmCard(look, onOpen = { onClick(entry) }) {
            QueuedCardBody(entry, position, canMoveUp, onClick, onCancel, onMoveUp, onMoveToTop, look)
        }
        return
    }
    Card(
        onClick = { onClick(entry) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = look.cardPadH, vertical = look.cardPadV)
            .dmFocus(look, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        QueuedCardBody(entry, position, canMoveUp, onClick, onCancel, onMoveUp, onMoveToTop, look)
    }
}

/** The content of a [QueuedCard]: cover, name, queue position and the reorder / cancel buttons. */
@Composable
private fun QueuedCardBody(
    entry: DownloadEntry,
    position: Int,
    canMoveUp: Boolean,
    onOpen: (DownloadEntry) -> Unit,
    onCancel: (DownloadEntry) -> Unit,
    onMoveUp: (DownloadEntry) -> Unit,
    onMoveToTop: (DownloadEntry) -> Unit,
    look: DmLook,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(look.inner),
    ) {
        DownloadCoverArt(
            entry = entry,
            modifier = Modifier
                .size(width = look.coverW, height = look.coverH)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(look.gap))

        Column(modifier = Modifier.weight(1f)) {
            StoreBadge(entry.store)
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.name.ifEmpty { "${entry.store} ${entry.id}" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Queued · #$position",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailsButton(entry, onOpen, look)
                if (canMoveUp) {
                    OutlinedButton(
                        onClick = { onMoveToTop(entry) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.dmButton(look),
                        contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
                    ) { Text("Start next", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { onMoveUp(entry) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.dmButton(look),
                        contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
                    ) { Text("↑ Up", style = MaterialTheme.typography.labelSmall) }
                }
                OutlinedButton(
                    onClick = { onCancel(entry) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.dmButton(look),
                    contentPadding = PaddingValues(horizontal = look.buttonPadH, vertical = 2.dp),
                ) { Text("✕ Cancel", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

/**
 * Store-aware cover loader for a Manager card.
 *
 * Steam art is derived from the appId (its `cover` holds the appId string), so Steam rows
 * reuse [GameCoverArt]. Every other store (Amazon now; GOG / Epic later) stores a real image
 * URL in `cover`, which [GameCoverArt] can't render — it only knows Steam's appId→URL scheme,
 * so an Amazon row was showing a blank/× box. For those we load the URL directly via Coil,
 * falling back to a themed placeholder box when the URL is genuinely absent.
 */
@Composable
private fun DownloadCoverArt(entry: DownloadEntry, modifier: Modifier) {
    if (entry.store == Store.STEAM) {
        GameCoverArt(appId = entry.cover?.toIntOrNull() ?: entry.id.toIntOrNull() ?: 0, modifier = modifier)
        return
    }
    val coverUrl = entry.cover
    if (coverUrl.isNullOrEmpty()) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val ctx = LocalContext.current
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(coverUrl).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/** Small store-tinted chip so a mixed-store list reads at a glance. */
@Composable
private fun StoreBadge(store: Store) {
    val accent = StoreStyle.accent(store)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = StoreStyle.label(store),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

@Composable
private fun ExePickerDialogDm(
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

private val INSTALLED_GREEN = Color(0xFF4CAF50)
// Matches the Amazon store list's amber update-available color (AmazonGamesActivity GameCard).
private val UPDATE_AMBER = Color(0xFFFFAA00)

// One shared byte formatter across the download stack (card text, detail label, shade line).
private fun fmtSizeDm(bytes: Long): String = formatDownloadSize(bytes)
