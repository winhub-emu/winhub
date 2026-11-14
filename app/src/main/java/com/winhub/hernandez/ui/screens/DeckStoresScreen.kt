package com.winhub.hernandez.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.winhub.hernandez.store.GogGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.AmazonLoginActivity
import com.winhub.hernandez.store.CatalogItem
import com.winhub.hernandez.store.DeckRise
import com.winhub.hernandez.store.EpicStoreCatalog
import com.winhub.hernandez.store.GogStoreCatalog
import com.winhub.hernandez.store.SteamStoreCatalog
import com.winhub.hernandez.store.StoreCatalogDetailActivity
import com.winhub.hernandez.store.deckTileFocus
import com.winhub.hernandez.store.AmazonUserData
import com.winhub.hernandez.store.AmazonGame
import com.winhub.hernandez.store.AmazonGameDetailActivity
import com.winhub.hernandez.store.AmazonLibraryRepo
import com.winhub.hernandez.store.EpicGame
import com.winhub.hernandez.store.EpicGameDetailActivity
import com.winhub.hernandez.store.EpicLibraryRepo
import com.winhub.hernandez.store.GogGameDetailActivity
import com.winhub.hernandez.store.GogLibraryRepo
import com.winhub.hernandez.store.SteamGame
import com.winhub.hernandez.store.SteamGameDetailActivity
import com.winhub.hernandez.store.SteamRepository
import com.winhub.hernandez.store.DeckStoreTheme
import com.winhub.hernandez.store.SteamFriendsStore
import com.winhub.hernandez.store.SteamFriendsTab
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.SteamProfileTab
import com.winhub.hernandez.store.StoreMatureSetting
import com.winhub.hernandez.store.StoreDownloadTier
import com.winhub.hernandez.store.rememberSteamLibrary
import com.winhub.hernandez.store.rememberStorefrontLayout
import com.winhub.hernandez.store.download.DownloadRegistry
import android.widget.Toast
import com.winhub.hernandez.store.EpicLoginActivity
import com.winhub.hernandez.store.EpicUserData
import com.winhub.hernandez.store.GogLoginActivity
import com.winhub.hernandez.store.GogUserData
import com.winhub.hernandez.store.SteamLoginActivity
import com.winhub.hernandez.store.SteamSessionManager
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.ui.deck.StoreStyle
import com.winhub.hernandez.ui.theme.AppThemeState

/**
 * Deck Mode's Stores section — a redesign of the store experience in DroidDeck's language, backed
 * by WinHub's REAL accounts (no mock data):
 *
 *  • The store row is a Deck stretchy tab strip — one tab per store, brand dot lit only while that
 *    store's account is actually signed in — with the shared Downloads tile in the title row.
 *  • Not signed in → DroidDeck's `SignInCard` hero: the store's tilted brand wall behind gradient
 *    scrims and a radial glow, its name at 42sp, and a large **Sign in** that opens WinHub's real
 *    login Activity for that store.
 *  • Signed in → storefront, one unified Library grid across every signed-in store (games only),
 *    Settings, and for Steam the Friends/Profile panes.
 *
 * Sign-in is read live from each store's own session (`GogUserData`/`EpicUserData`/`AmazonUserData`
 * cached profiles and `SteamSessionManager`), so it stays truthful after a login round-trip.
 * Brand colours are DroidDeck's `sourceColours()` values.
 */
private data class DeckStoreTile(
    val screen: Screen,
    val chip: String,
    val fill: Color,
    val ink: Color,
    val dot: Color,
)

/** DroidDeck `sourceColours()` mapped onto WinHub's store screens (order = `Screen.storeItems`). */
private val deckStores = listOf(
    DeckStoreTile(Screen.Gog, "GOG", Color(0xFF3A1A3C), Color(0xFFE6A3EA), Color(0xFFA855F7)),
    DeckStoreTile(Screen.Epic, "Epic", Color(0xFF2A2A2A), Color(0xFFDDDDDD), Color(0xFFFFFFFF)),
    DeckStoreTile(Screen.Amazon, "Amazon", Color(0xFF3A2A0A), Color(0xFFFFC266), Color(0xFFFF9900)),
    DeckStoreTile(Screen.Xbox, "Xbox", Color(0xFF0E2A0E), Color(0xFF8EDB8E), Color(0xFF107C10)),
    DeckStoreTile(Screen.Steam, "Steam", Color(0xFF16293D), Color(0xFF7CC4FF), Color(0xFF1A9FFF)),
)

/** Each store's logo and the brand colours it sits on in the picker tiles. */
internal fun deckStoreLogo(screen: Screen): Int = when (screen) {
    Screen.Steam -> R.drawable.store_logo_steam
    Screen.Epic -> R.drawable.store_logo_epic
    Screen.Gog -> R.drawable.store_logo_gog
    Screen.Xbox -> R.drawable.store_logo_xbox
    else -> R.drawable.store_logo_amazon
}

internal fun deckStoreBrand(screen: Screen): List<Color> = when (screen) {
    Screen.Steam -> listOf(Color(0xFF1B2838), Color(0xFF171A21))
    Screen.Epic -> listOf(Color(0xFF202020), Color(0xFF121212))
    Screen.Gog -> listOf(Color(0xFF5C2D91), Color(0xFF3B1C60))
    Screen.Xbox -> listOf(Color(0xFF107C10), Color(0xFF0B5A0B))
    else -> listOf(Color(0xFF232F3E), Color(0xFF131A22))
}

/** The full-screen store Activity of [screen] (what the XMB store style opens); null for a screen that is not a store. */
internal fun storeActivityClass(screen: Screen): Class<*>? = when (screen) {
    Screen.Steam -> com.winhub.hernandez.store.SteamMainActivity::class.java
    Screen.Epic -> com.winhub.hernandez.store.EpicMainActivity::class.java
    Screen.Gog -> com.winhub.hernandez.store.GogMainActivity::class.java
    Screen.Amazon -> com.winhub.hernandez.store.AmazonMainActivity::class.java
    Screen.Xbox -> com.winhub.hernandez.store.xbox.XboxMainActivity::class.java
    else -> null
}

/** Opens [screen]'s store Activity (the XMB store style); false when there is none. */
internal fun launchStoreActivity(context: Context, screen: Screen): Boolean {
    val cls = storeActivityClass(screen) ?: return false
    context.startActivity(Intent(context, cls))
    return true
}

/** WinHub's real per-store login Activities (what each store's Sign in opens). */
private fun launchDeckLogin(context: Context, screen: Screen) {
    val cls = when (screen) {
        Screen.Gog -> GogLoginActivity::class.java
        Screen.Epic -> EpicLoginActivity::class.java
        Screen.Amazon -> AmazonLoginActivity::class.java
        // Xbox's own store screen carries the Microsoft sign-in gate (and the missing-backend notice).
        Screen.Xbox -> com.winhub.hernandez.store.xbox.XboxMainActivity::class.java
        Screen.Steam -> SteamLoginActivity::class.java
        else -> return
    }
    val intent = Intent(context, cls)
    // Steam's login finishes back into the Deck pane when it was opened from here. Without this
    // it launches the classic store-first shell and the first login drops the user into the old
    // library instead of the Deck one.
    if (screen == Screen.Steam) intent.putExtra(SteamLoginActivity.EXTRA_FROM_DECK, true)
    context.startActivity(intent)
}

/** Live sign-in, straight off each store's own session store. */
internal fun deckSignedIn(context: Context, screen: Screen): Boolean = when (screen) {
    // The saved credentials, not the profile cache: that cache is only filled later by the Profile tab,
    // so right after a login the store still read "signed out".
    Screen.Gog -> !context.getSharedPreferences("bh_gog_prefs", 0).getString("access_token", null).isNullOrEmpty()
    Screen.Epic -> com.winhub.hernandez.store.EpicCredentialStore.load(context) != null
    Screen.Amazon -> com.winhub.hernandez.store.AmazonCredentialStore.isLoggedIn(context)
    Screen.Xbox -> com.winhub.hernandez.store.xbox.XboxAccountStore.signedInAs(context) != null
    Screen.Steam -> SteamSessionManager.isLoggedOn() || SteamPrefs.isLoggedIn
    else -> false
}

/** One owned game: cover art, and how to open its real per-store detail screen. */
private data class DeckOwnedGame(
    val title: String,
    val art: String?,
    val open: (Context) -> Unit,
    val gog: GogGame? = null,
    val epic: EpicGame? = null,
    val amazon: AmazonGame? = null,
    val isXbox: Boolean = false,
    val xbox: com.winhub.hernandez.store.xbox.XboxGame? = null,
    val steamAppId: Int = 0,
)

/**
 * The store's REAL cached owned-games library, read off each store's own repo. Steam's rows come off
 * `SteamRepository`'s SQLite cache (its header art needs no hash). Wrapped in `runCatching` so a
 * store that has never synced simply draws nothing rather than taking the screen down.
 */
private fun deckOwnedGames(context: Context, screen: Screen): List<DeckOwnedGame> = runCatching {
    when (screen) {
        Screen.Gog -> GogLibraryRepo.cached(context).map { g ->
            val c = GogLibraryRepo.toCatalogItem(g)
            DeckOwnedGame(
                title = g.title,
                art = c.tallImageUrl ?: c.imageUrl,
                gog = g,
                open = { ctx ->
                    ctx.startActivity(Intent(ctx, GogGameDetailActivity::class.java).apply {
                        putExtra("game_id", g.gameId)
                        putExtra("title", g.title)
                        putExtra("image_url", g.imageUrl)
                        putExtra("description", g.description)
                        putExtra("developer", g.developer)
                        putExtra("category", g.category)
                        putExtra("generation", g.generation)
                    })
                },
            )
        }
        Screen.Epic -> EpicLibraryRepo.cached(context).map { g ->
            DeckOwnedGame(
                title = g.title,
                art = g.artCover.ifBlank { null } ?: g.artSquare.ifBlank { null },
                epic = g,
                open = { ctx ->
                    ctx.startActivity(Intent(ctx, EpicGameDetailActivity::class.java).apply {
                        putExtra("app_name", g.appName)
                        putExtra("title", g.title)
                        putExtra("description", g.description)
                        putExtra("developer", g.developer)
                        putExtra("art_cover", g.artCover)
                        putExtra("namespace", g.namespace)
                        putExtra("catalog_item_id", g.catalogItemId)
                    })
                },
            )
        }
        Screen.Amazon -> AmazonLibraryRepo.cached(context).map { g ->
            DeckOwnedGame(
                title = g.title,
                art = g.artUrl.ifBlank { null } ?: g.heroUrl.ifBlank { null },
                amazon = g,
                open = { ctx ->
                    ctx.startActivity(Intent(ctx, AmazonGameDetailActivity::class.java).apply {
                        putExtra("product_id", g.productId)
                        putExtra("entitlement_id", g.entitlementId)
                        putExtra("title", g.title)
                        putExtra("developer", g.developer)
                        putExtra("publisher", g.publisher)
                        putExtra("art_url", g.artUrl)
                        putExtra("product_sku", g.productSku)
                    })
                },
            )
        }
        Screen.Xbox -> {
            val account = com.winhub.hernandez.store.xbox.XboxAccountStore.signedInAs(context)
            val games = if (account == null) emptyList()
            else com.winhub.hernandez.store.xbox.XboxLibraryCache.read(context, account, com.winhub.hernandez.store.xbox.Xodus.market())?.games.orEmpty()
            games.map { g ->
                DeckOwnedGame(
                    title = g.title,
                    art = g.posterUrl ?: g.heroUrl,
                    isXbox = true,
                    xbox = g,
                    open = { ctx -> ctx.startActivity(com.winhub.hernandez.store.xbox.XboxMainActivity.detailIntent(ctx, g)) },
                )
            }
        }
        Screen.Steam -> SteamRepository.getInstance().getCachedGameRows()
            .map { SteamGame.fromGameRow(it) }
            .map { g ->
                DeckOwnedGame(
                    title = g.name,
                    art = g.headerUrl,
                    steamAppId = g.appId,
                    open = { ctx ->
                        ctx.startActivity(
                            Intent(ctx, SteamGameDetailActivity::class.java)
                                .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, g.appId),
                        )
                    },
                )
            }
        else -> emptyList()
    }
}.getOrDefault(emptyList())

/** The selected store's own cached library, sorted by title — never mixed with other stores. */
private fun deckLibraryGames(context: Context, screen: Screen): List<DeckOwnedGame> = runCatching {
    deckOwnedGames(context, screen).sortedBy { it.title.lowercase() }
}.getOrDefault(emptyList())

/** One store's library: its own full-height cover grid, reloaded when the store tab changes. */
@Composable
private fun DeckLibraryPane(screen: Screen, context: Context, onGameClick: (DeckOwnedGame) -> Unit) {
    var games by remember(screen) { mutableStateOf<List<DeckOwnedGame>?>(null) }
    LaunchedEffect(screen) {
        games = withContext(Dispatchers.IO) { deckLibraryGames(context, screen) }
    }
    val list = games
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "LIBRARY",
                color = Deck.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            )
            if (list != null && list.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${list.size} game${if (list.size == 1) "" else "s"}",
                    color = Deck.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            list == null -> DeckLibrarySkeleton()
            list.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                DeckEmptyState(
                    title = "No games yet",
                    body = "Sign in to this store and sync to fill its library.",
                )
            }
            else -> DeckDragFrame(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(list, key = { index, game -> "$index-${game.title}" }) { index, game ->
                        DeckRise(index = index % 10) {
                            Box(Modifier.deckDragItem("$index-${game.title}")) {
                                DeckGameCard(game) { onGameClick(game) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A library block: the cover fills the tile, a gradient fade grounds the title at its foot. */
@Composable
private fun DeckGameCard(game: DeckOwnedGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val badge = when {
        game.gog != null -> deckStores[0].dot
        game.epic != null -> deckStores[1].dot
        game.amazon != null -> deckStores[2].dot
        game.isXbox -> deckStores.first { it.screen == Screen.Xbox }.dot
        game.steamAppId != 0 -> deckStores.first { it.screen == Screen.Steam }.dot
        else -> null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .deckFocusScale(interaction)
            .clip(shape)
            .background(Deck.surface)
            .deckOuterFocusStroke(interaction, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        val art = game.art
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Deck.background.copy(alpha = 0.92f),
                    ),
                ),
        )
        Text(
            text = game.title,
            color = Deck.onBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(badge)
                    .border(1.5.dp, Deck.background.copy(alpha = 0.8f), CircleShape),
            )
        }
    }
}

/** A row of Steam's featured titles under the store picker (nothing shows until the catalog answers). */
@Composable
private fun DeckFeaturedOnSteam(context: Context, onCard: (DeckBrowseItem) -> Unit) {
    var items by remember { mutableStateOf<List<DeckBrowseItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            val data = deckFeatured(context, Screen.Steam)
            (data.sections.firstOrNull { it.title == "Specials" }?.items ?: data.sections.firstOrNull()?.items).orEmpty().take(8)
        }
    }
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Featured on Steam",
            color = Deck.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        DeckDragFrame(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items, key = { i, it -> "feat-$i-${it.title}" }) { index, item ->
                    Box(Modifier.deckDragItem("feat-$index-${item.title}")) {
                        DeckBrowseCard(item) { onCard(item) }
                    }
                }
            }
        }
    }
}

/** The portrait Stores page's "Featured on Steam" row; a tap opens that title's store page. */
@Composable
internal fun PortraitFeaturedOnSteam() {
    val context = LocalContext.current
    DeckFeaturedOnSteam(context) { item -> item.open(context) }
}

/** A browseable storefront entry: cover art, title, price and how to open its detail page. */
private data class DeckBrowseItem(
    val title: String,
    val art: String?,
    val price: String,
    val catalog: CatalogItem? = null,
    val appId: Int = 0,
    val xbox: com.winhub.hernandez.store.xbox.XboxGame? = null,
    val open: (Context) -> Unit,
)

/** A labeled rail of storefront items, DroidDeck style. */
private data class DeckBrowseSection(val title: String, val items: List<DeckBrowseItem>)

/** A store's featured catalog: a hero pick plus labeled rails. */
private data class DeckStoreData(val hero: DeckBrowseItem?, val sections: List<DeckBrowseSection>)

private fun deckCatalogPrice(i: CatalogItem): String = when {
    i.isFree -> "Free"
    i.hasPrice && i.finalPrice.isNotBlank() -> i.finalPrice
    else -> ""
}

private fun deckCatalogBrowse(i: CatalogItem): DeckBrowseItem =
    DeckBrowseItem(
        title = i.title,
        art = i.tallImageUrl ?: i.imageUrl,
        price = deckCatalogPrice(i),
        open = { ctx -> ctx.startActivity(StoreCatalogDetailActivity.intent(ctx, i)) },
        catalog = i,
    )

private fun deckSteamBrowse(i: SteamStoreCatalog.StoreItem): DeckBrowseItem =
    DeckBrowseItem(
        title = i.name,
        art = i.artUrl,
        price = when {
            i.isFree -> "Free"
            i.hasPrice -> SteamStoreCatalog.formatPrice(i.finalCents, i.currency)
            else -> ""
        },
        appId = i.appId,
    ) { ctx ->
        ctx.startActivity(
            Intent(ctx, SteamGameDetailActivity::class.java)
                .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, i.appId),
        )
    }

private fun <T> List<T>.rail(title: String, toItem: (T) -> DeckBrowseItem): DeckBrowseSection? =
    if (isEmpty()) null else DeckBrowseSection(title, map(toItem))

/** The store's REAL featured catalog, fetched off the main thread. Amazon ships no browse catalog. */
private suspend fun deckFeatured(context: Context, screen: Screen): DeckStoreData = when (screen) {
    Screen.Gog -> runCatching { GogStoreCatalog.featured(context) }.getOrNull().let { f ->
        if (f == null) DeckStoreData(null, emptyList())
        else DeckStoreData(
            hero = f.hero?.let { deckCatalogBrowse(it) },
            sections = listOfNotNull(
                f.trending.rail("Trending") { deckCatalogBrowse(it) },
                f.newReleases.rail("New Releases") { deckCatalogBrowse(it) },
                f.deals.rail("Deals") { deckCatalogBrowse(it) },
                f.free.rail("Free") { deckCatalogBrowse(it) },
            ),
        )
    }
    Screen.Epic -> runCatching { EpicStoreCatalog.featured(context) }.getOrNull().let { f ->
        if (f == null) DeckStoreData(null, emptyList())
        else DeckStoreData(
            hero = f.hero?.let { deckCatalogBrowse(it) },
            sections = listOfNotNull(
                f.freeNow.rail("Free Now") { deckCatalogBrowse(it) },
                f.onSale.rail("On Sale") { deckCatalogBrowse(it) },
                f.newReleases.rail("New Releases") { deckCatalogBrowse(it) },
                f.freeToPlay.rail("Free to Play") { deckCatalogBrowse(it) },
            ),
        )
    }
    Screen.Steam -> runCatching { SteamStoreCatalog.featured(context) }.getOrNull().let { f ->
        if (f == null) DeckStoreData(null, emptyList())
        else DeckStoreData(
            hero = f.hero?.let { deckSteamBrowse(it) },
            sections = listOfNotNull(
                f.newReleases.rail("New Releases") { deckSteamBrowse(it) },
                f.topFree.rail("Top Free") { deckSteamBrowse(it) },
                f.specials.rail("Specials") { deckSteamBrowse(it) },
            ),
        )
    }
    Screen.Xbox -> runCatching {
        val account = com.winhub.hernandez.store.xbox.XboxAccountStore.signedInAs(context)
        val owned = if (account == null) emptyList()
        else com.winhub.hernandez.store.xbox.XboxLibraryCache.read(context, account, com.winhub.hernandez.store.xbox.Xodus.market())?.games.orEmpty()
        val trending = com.winhub.hernandez.store.xbox.XboxCatalog.featured(owned.associate { it.id to it.access })
        fun xboxBrowse(g: com.winhub.hernandez.store.xbox.XboxGame) = DeckBrowseItem(
            title = g.title,
            art = g.posterUrl ?: g.heroUrl,
            price = when { g.gamePass -> "Game Pass"; g.owned -> "Owned"; else -> "" },
            xbox = g,
            open = { ctx -> ctx.startActivity(com.winhub.hernandez.store.xbox.XboxMainActivity.detailIntent(ctx, g)) },
        )
        DeckStoreData(
            hero = trending.firstOrNull()?.let { xboxBrowse(it) },
            sections = listOfNotNull(
                trending.drop(1).rail("Trending on PC") { xboxBrowse(it) },
                owned.take(20).rail("In your library") { xboxBrowse(it) },
            ),
        )
    }.getOrNull() ?: DeckStoreData(null, emptyList())
    else -> DeckStoreData(null, emptyList())
}

/** The signed-in store's storefront: a hero pick plus labeled rails of real featured titles. */
@Composable
private fun DeckStoreFront(
    tile: DeckStoreTile,
    context: Context,
    onCard: (DeckBrowseItem) -> Unit,
    showMature: Boolean,
) {
    var data by remember(tile.screen) { mutableStateOf<DeckStoreData?>(null) }
    LaunchedEffect(tile.screen) {
        data = withContext(Dispatchers.IO) { deckFeatured(context, tile.screen) }
    }
    val raw = data
    if (raw == null) {
        DeckStoreSkeleton()
        return
    }
    // Mature titles stay out of the hero and rails unless the store setting shows them; Steam
    // items carry no catalog, so they always pass (`catalog?.mature != true`).
    val store = if (showMature) raw else DeckStoreData(
        hero = raw.hero?.takeIf { it.catalog?.mature != true },
        sections = raw.sections.mapNotNull { section ->
            val items = section.items.filter { it.catalog?.mature != true }
            if (items.isEmpty()) null else DeckBrowseSection(section.title, items)
        },
    )
    if (store.hero == null && store.sections.isEmpty()) {
        DeckEmptyState(
            title = "Nothing featured yet",
            body = "Open ${tile.screen.label} once to refresh its storefront.",
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        val heroes = (listOfNotNull(store.hero) + store.sections.firstOrNull()?.items.orEmpty().take(4))
            .distinctBy { it.title }
        if (heroes.isNotEmpty()) DeckHeroCarousel(heroes, onCard)
        store.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Deck.primary))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = section.title,
                        color = Deck.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                DeckDragFrame(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(section.items, key = { i, it -> "$i-${it.title}" }) { index, item ->
                            DeckRise(index = index % 8) {
                                Box(Modifier.deckDragItem("${section.title}-$index-${item.title}")) {
                                    DeckBrowseCard(item) { onCard(item) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The featured carousel: the hero pick plus the first shelf's lead titles, cross-fading every few
 * seconds (paused while the D-pad is on it) with dot indicators and a "View" pill.
 */
@Composable
private fun DeckHeroCarousel(items: List<DeckBrowseItem>, onCard: (DeckBrowseItem) -> Unit) {
    var index by remember(items) { mutableIntStateOf(0) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(items, focused) {
        if (items.size > 1 && !focused) {
            while (true) {
                delay(6000)
                index = (index + 1) % items.size
            }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(shape)
            .background(Deck.surface)
            .deckOuterFocusStroke(interaction, shape)
            .clickable(interactionSource = interaction, indication = null) {
                onCard(items[index.coerceIn(0, items.lastIndex)])
            },
    ) {
        Crossfade(
            targetState = index.coerceIn(0, items.lastIndex),
            animationSpec = tween(450),
            modifier = Modifier.matchParentSize(),
            label = "heroFade",
        ) { i ->
            val item = items[i]
            Box(Modifier.fillMaxSize()) {
                val art = item.art
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Deck.background.copy(alpha = 0.94f)),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp, end = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        color = Deck.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.price.isNotEmpty()) {
                        Text(
                            text = item.price,
                            color = if (item.price == "Free") Deck.primary else Deck.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Deck.primary)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text("View", color = Deck.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            if (items.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items.indices.forEach { d ->
                        Box(
                            Modifier
                                .size(if (d == index) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (d == index) Deck.primary else Deck.onSurfaceVariant.copy(alpha = 0.5f)),
                        )
                    }
                }
            }
        }
    }
}

/** A storefront rail card: cover art over graphite, title and price beneath. */
@Composable
private fun DeckBrowseCard(item: DeckBrowseItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(150.dp)
            .deckFocusScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(shape)
                .background(Deck.surface)
                .border(1.dp, Deck.line, shape),
        ) {
            val art = item.art
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = item.title,
            color = Deck.onBackground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.price.isNotEmpty()) {
            Text(
                text = item.price,
                color = if (item.price == "Free") Deck.primary else Deck.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun DeckStoresScreen(
    /** Full XMB: the store card that opened this page; it starts inside that store. */
    initialStore: Screen? = null,
    /** Full XMB: leaves the page back to Home (B from the top level of a store). */
    onExit: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val showStores by AppThemeState.showStores.collectAsState()
    var selected by remember { mutableStateOf<Screen>(initialStore ?: Screen.Gog) }
    var gogInstall by remember { mutableStateOf<GogGame?>(null) }
    var epicInstall by remember { mutableStateOf<EpicGame?>(null) }
    var amazonInstall by remember { mutableStateOf<AmazonGame?>(null) }
    var xboxInstall by remember { mutableStateOf<com.winhub.hernandez.store.xbox.XboxGame?>(null) }
    var steamInstall by remember { mutableStateOf<DeckSteamTarget?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var showMature by remember { mutableStateOf(StoreMatureSetting.get(context)) }
    // Returning from a store's login Activity does not recompose by itself. Bump a tick on every
    // resume so the sign-in gate (and the lit brand dots) re-read the session that just changed.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The login Activity can hand back before its session is written, so re-read a few times after
    // every resume instead of trusting the single read at that instant.
    var pollTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeTick) {
        repeat(20) {
            delay(1500)
            pollTick++
        }
    }
    val signedInByStore = remember(resumeTick, pollTick) {
        deckStores.associate { it.screen to deckSignedIn(context, it.screen) }
    }
    val dlEntries by DownloadRegistry.entries.collectAsState()
    val activeDownloads = dlEntries.count { it.isActive }
    LaunchedEffect(Unit) { DownloadRegistry.init(context) }
    val active = deckStores.firstOrNull { it.screen == selected } ?: deckStores.first()
    val scope = rememberCoroutineScope()

    // Two levels: the store picker (tiles), and inside a store its page tabs down the left edge.
    // L2/R2 switch the store on the picker and the page tab inside a store; B leaves a store.
    var inStore by remember { mutableStateOf(initialStore != null) }
    var storeTab by remember(active.screen) { mutableIntStateOf(0) }
    val storeTabCount = if (active.screen == Screen.Steam) 5 else 3
    val tabFocus = remember { FocusRequester() }
    // The shell owns L2/R2 (it works without Compose focus): the host steps the store on the picker and
    // the page tab inside a signed-in store. Pages and index are mirrored both ways below.
    val deckTabs = LocalDeckTabs.current
    val storeSignedIn = signedInByStore[active.screen] == true
    val stepPages = if (!inStore) deckStores.size else if (storeSignedIn) storeTabCount else 1
    val stepIndex = if (!inStore) deckStores.indexOfFirst { it.screen == selected }.coerceAtLeast(0) else storeTab
    LaunchedEffect(deckTabs, stepPages, stepIndex) {
        deckTabs.bind(stepPages)
        deckTabs.select(stepIndex)
    }
    LaunchedEffect(deckTabs.index) {
        val i = deckTabs.index
        if (!inStore) {
            deckStores.getOrNull(i)?.let { selected = it.screen }
        } else if (storeSignedIn && i in 0 until storeTabCount) {
            storeTab = i
        }
    }
    val panelOpen = showDownloads || gogInstall != null || epicInstall != null ||
        amazonInstall != null || xboxInstall != null || steamInstall != null
    val canLeaveStore = inStore && !panelOpen
    val backLayer = LocalDeckBack.current
    val exitNow by rememberUpdatedState(onExit)
    // B closes an open panel first, then leaves the store (to Home in Full XMB, otherwise to the picker).
    DisposableEffect(panelOpen, canLeaveStore, onExit != null) {
        backLayer.onBack = when {
            panelOpen -> {
                {
                    showMature = StoreMatureSetting.get(context)
                    showDownloads = false
                    gogInstall = null
                    epicInstall = null
                    amazonInstall = null
                    xboxInstall = null
                    steamInstall = null
                    true
                }
            }
            canLeaveStore || exitNow != null -> {
                {
                    val exit = exitNow
                    if (exit != null) exit() else inStore = false
                    true
                }
            }
            else -> null
        }
        onDispose { backLayer.onBack = null }
    }

    // Library (owned) card tap -> in-Deck install when the store's payload is attached.
    val openOwned: (DeckOwnedGame) -> Unit = { g ->
        when {
            g.gog != null -> gogInstall = g.gog
            g.epic != null -> epicInstall = g.epic
            g.amazon != null -> amazonInstall = g.amazon
            g.xbox != null -> xboxInstall = g.xbox
            g.steamAppId != 0 -> steamInstall = DeckSteamTarget(g.steamAppId, g.title, g.art)
            else -> g.open(context)
        }
    }

    // Store-tab browse card tap -> in-Deck install (GOG resolves its gen flag; Epic matches the
    // owned library for appName/namespace; Steam goes straight from appId). Unowned fallbacks
    // keep the legacy detail page.
    val onBrowseTap: (DeckBrowseItem) -> Unit = { item ->
        val cat = item.catalog
        when {
            active.screen == Screen.Gog && cat != null -> scope.launch {
                val game = withContext(Dispatchers.IO) {
                    deckGogBuild(
                        context, cat.id, cat.title, cat.imageUrl,
                        cat.description, cat.developer, cat.tags,
                    )
                }
                gogInstall = game
            }
            active.screen == Screen.Epic && cat != null -> scope.launch {
                val owned = withContext(Dispatchers.IO) {
                    EpicLibraryRepo.cached(context).firstOrNull { g ->
                        (g.namespace == cat.extra["namespace"] && g.catalogItemId == cat.id) ||
                            g.title.equals(cat.title, ignoreCase = true)
                    }
                }
                if (owned != null) epicInstall = owned else item.open(context)
            }
            active.screen == Screen.Xbox && item.xbox != null -> xboxInstall = item.xbox
            active.screen == Screen.Steam && item.appId != 0 ->
                steamInstall = DeckSteamTarget(item.appId, item.title, item.art)
            else -> item.open(context)
        }
    }

    when {
        showDownloads -> DeckDownloadsPanel {
            // Pull the panel's Store-settings change back so the storefront refilters at once.
            showMature = StoreMatureSetting.get(context)
            showDownloads = false
        }
        gogInstall != null -> {
            val g = gogInstall!!
            DeckGogInstall(
                game = g,
                onBack = { gogInstall = null },
                onOpenStore = {
                    context.startActivity(Intent(context, GogGameDetailActivity::class.java).apply {
                        putExtra("game_id", g.gameId)
                        putExtra("title", g.title)
                        putExtra("image_url", g.imageUrl)
                        putExtra("description", g.description)
                        putExtra("developer", g.developer)
                        putExtra("category", g.category)
                        putExtra("generation", g.generation)
                    })
                },
            )
        }
        epicInstall != null -> {
            val g = epicInstall!!
            DeckEpicInstall(
                game = g,
                onBack = { epicInstall = null },
                onOpenStore = {
                    context.startActivity(Intent(context, EpicGameDetailActivity::class.java).apply {
                        putExtra("app_name", g.appName)
                        putExtra("title", g.title)
                        putExtra("description", g.description)
                        putExtra("developer", g.developer)
                        putExtra("art_cover", g.artCover)
                        putExtra("namespace", g.namespace)
                        putExtra("catalog_item_id", g.catalogItemId)
                    })
                },
            )
        }
        xboxInstall != null -> {
            val g = xboxInstall!!
            DeckXboxInstall(
                game = g,
                onBack = { xboxInstall = null },
                onOpenStore = { context.startActivity(com.winhub.hernandez.store.xbox.XboxMainActivity.detailIntent(context, g)) },
            )
        }
        amazonInstall != null -> {
            val g = amazonInstall!!
            DeckAmazonInstall(
                game = g,
                onBack = { amazonInstall = null },
                onOpenStore = {
                    context.startActivity(Intent(context, AmazonGameDetailActivity::class.java).apply {
                        putExtra("product_id", g.productId)
                        putExtra("entitlement_id", g.entitlementId)
                        putExtra("title", g.title)
                        putExtra("developer", g.developer)
                        putExtra("publisher", g.publisher)
                        putExtra("art_url", g.artUrl)
                        putExtra("product_sku", g.productSku)
                    })
                },
            )
        }
        steamInstall != null -> {
            val t = steamInstall!!
            DeckSteamInstall(
                target = t,
                onBack = { steamInstall = null },
                onOpenStore = {
                    context.startActivity(
                        Intent(context, SteamGameDetailActivity::class.java)
                            .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, t.appId),
                    )
                },
            )
        }
        else -> {
    // NOT globally scrollable: the tab panes below (Store settings, Steam's Profile) carry their
    // OWN verticalScroll, and a scrollable measured with infinite height throws
    // "Vertically scrollable component was measured with an infinity maximum height constraints".
    // Chrome (title, chips, tab strip) stays fixed; the pane gets the bounded remainder.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(deckPageBackground())
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (e.key == Key.ButtonX) {
                    showDownloads = true
                    return@onPreviewKeyEvent true
                }
                false
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stores",
                    color = Deck.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            // Shared download queue: compact graphite tile with a live count.
            val downloadInteraction = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Deck.surfaceVariant)
                    .deckFocusBorder(downloadInteraction, RoundedCornerShape(13.dp))
                    .clickable(
                        interactionSource = downloadInteraction,
                        indication = null,
                    ) { showDownloads = true }
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Downloads",
                    tint = if (activeDownloads > 0) Deck.primary else Deck.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                if (activeDownloads > 0) {
                    Text(
                        text = activeDownloads.toString(),
                        color = Deck.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))

        if (!showStores) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                DeckPaneScroll {
                    DeckGroup("Stores are turned off") {
                        DeckRow(
                            label = "Enable Stores",
                            hint = "Settings › Side Menu › Stores",
                            control = { DeckToggle(false) { } },
                        )
                    }
                }
            }
        } else {
            if (!inStore) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    DeckStorePicker(
                        names = deckStores.map { it.chip },
                        accents = deckStores.map { it.dot },
                        logos = deckStores.map { deckStoreLogo(it.screen) },
                        brands = deckStores.map { deckStoreBrand(it.screen) },
                        signedIn = deckStores.map { signedInByStore[it.screen] == true },
                        selected = deckStores.indexOfFirst { it.screen == selected }.coerceAtLeast(0),
                        onSelect = { selected = deckStores[it].screen },
                        onOpen = {
                            selected = deckStores[it].screen
                            // The XMB store style is a full-screen Activity of its own; the Deck style stays in this page.
                            if (!(StoreStyle.isXmb(context) && launchStoreActivity(context, deckStores[it].screen))) inStore = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DeckFeaturedOnSteam(context, onBrowseTap)
                }
            } else {
                val signed = signedInByStore[active.screen] == true
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .width(190.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        DeckStoreHeader(
                            name = active.screen.label,
                            fill = active.fill,
                            ink = active.ink,
                            accent = active.dot,
                            signedIn = signed,
                            logo = deckStoreLogo(active.screen),
                            brand = deckStoreBrand(active.screen),
                        )
                        if (signed) {
                            // Store/Library/Settings, plus Friends/Profile on Steam alone.
                            val labels = if (active.screen == Screen.Steam) {
                                listOf("Store", "Library", "Settings", "Friends", "Profile")
                            } else {
                                listOf("Store", "Library", "Settings")
                            }
                            DeckStoreSideTabs(
                                labels = labels,
                                selected = storeTab,
                                onSelect = { storeTab = it },
                                selectedFocus = tabFocus,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (signed) {
                            when (storeTab) {
                                0 -> DeckPaneScroll {
                                    DeckStoreFront(active, context, onBrowseTap, showMature)
                                }
                                1 -> DeckLibraryPane(active.screen, context, openOwned)
                                2 -> DeckStoreSettingsPane(
                                    showMature = showMature,
                                    onShowMatureChange = { v ->
                                        showMature = v
                                        StoreMatureSetting.set(context, v)
                                    },
                                    onOpenDownloads = { showDownloads = true },
                                    activeDownloads = activeDownloads,
                                )
                                3 -> DeckSteamFriendsPane()
                                else -> DeckSteamProfilePane(onOpenFriends = { storeTab = 3 })
                            }
                        } else {
                            DeckPaneScroll { DeckSignInCard(active, context) }
                        }
                    }
                }
            }
            // Skipped on short landscape screens, where the page needs the room more.
            if (LocalConfiguration.current.screenHeightDp >= 400) DeckHintBar(
                hints = if (!inStore) {
                    listOf("A" to "Open", "\u25C0 \u25B6" to "Choose", "X" to "Downloads", "L2/R2" to "Switch store") +
                        if (onExit != null) listOf("B" to "Home") else emptyList()
                } else {
                    listOf("A" to "Select", "X" to "Downloads", "L2/R2" to "Switch tab", "B" to if (onExit != null) "Home" else "Stores")
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    }
    }
}

/**
 * One Store tab pane: a bounded, self-scrolling host. The Store chrome (title, chips, tab strip) is
 * fixed, so the pane sits in a `weight(1f)` Box with FINITE height — which is exactly what the
 * panes carrying their own `verticalScroll` (Store settings, Steam's Profile) need. A scrollable
 * measured inside an infinite-height constraint throws
 * "Vertically scrollable component was measured with an infinity maximum height constraints".
 */
@Composable
private fun DeckPaneScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        content = content,
    )
}

/**
 * DroidDeck's `SignInCard`: the store's tilted brand wall fading behind gradient scrims and a radial
 * glow, its name huge at the foot, and Sign in large beneath — here opening WinHub's real login.
 */
@Composable
private fun DeckSignInCard(tile: DeckStoreTile, context: Context) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(shape)
            .background(Deck.background)
            .border(1.dp, Deck.line, shape),
    ) {
        DeckTiltedWall(tile)
        // The words get the dark: side-lit from the right, weighted at the foot.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Deck.background.copy(alpha = 0.97f),
                    0.45f to Deck.background.copy(alpha = 0.90f),
                    0.78f to Deck.background.copy(alpha = 0.35f),
                    1f to Deck.background.copy(alpha = 0.15f),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Deck.background.copy(alpha = 0.92f)),
            ),
        )
        // The store's own glow, low and to the left, under the words.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(tile.dot.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.16f, size.height * 0.90f),
                        radius = size.height * 0.75f,
                    ),
                )
            },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 40.dp, bottom = 36.dp, end = 20.dp),
        ) {
            Text(
                text = tile.screen.label,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Black,
                color = Deck.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            DeckButton("Sign in", primary = true) { launchDeckLogin(context, tile.screen) }
        }
    }
}

/** The sign-in hero's background: the store's brand wall, tilted like DroidDeck's capsule wall. */
@Composable
private fun DeckTiltedWall(tile: DeckStoreTile) {
    val capsule = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { rotationZ = -13f; scaleX = 1.5f; scaleY = 1.5f },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(5) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    repeat(6) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(78.dp)
                                .clip(capsule)
                                .background(tile.fill)
                                .border(1.dp, tile.dot.copy(alpha = 0.22f), capsule),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Settings tab: the shared store settings (mature-content filter + download-speed tier — the
 * same ones the Downloads pane carries, so both entry points work) plus a jump into the queue.
 * [showMature] is hoisted by [DeckStoresScreen] so the storefront refilters the instant it flips.
 */
@Composable
private fun DeckStoreSettingsPane(
    showMature: Boolean,
    onShowMatureChange: (Boolean) -> Unit,
    onOpenDownloads: () -> Unit,
    activeDownloads: Int,
) {
    val context = LocalContext.current
    var dlTier by remember { mutableStateOf(StoreDownloadTier.get(context)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DeckGroup("Store settings") {
            DeckRow(
                label = "Show mature content",
                hint = "Storefront 17+/18+ titles; owned games always show",
                control = { DeckToggle(showMature, onShowMatureChange) },
            )
            DeckChoiceRow(
                label = "Download speed",
                hint = "Ceiling the next store download may ramp to",
                options = StoreDownloadTier.ALL.map { t -> t.toString() to StoreDownloadTier.label(t) },
                selected = dlTier.toString(),
                onPick = { v ->
                    v.toIntOrNull()?.let { t ->
                        dlTier = t
                        StoreDownloadTier.set(context, t)
                    }
                },
            )
        }
        DeckGroup("Queue") {
            DeckRow(
                label = "Downloads",
                hint = if (activeDownloads > 0) "$activeDownloads active" else "Nothing downloading",
                control = { DeckButton("Open", primary = false) { onOpenDownloads() } },
            )
        }
    }
}

/**
 * Steam's Friends tab embedded in the Deck tab strip: the SAME composable the store front runs,
 * wrapped in [DeckStoreTheme] so it draws in graphite, with the session inits the store Activity
 * would otherwise have done. Messages surface as a Toast (the Deck shell has no snackbar host).
 */
@Composable
internal fun DeckSteamFriendsPane() {
    val context = LocalContext.current
    // SteamMainActivity runs these in onCreate — composition-time, before ANY child reads a
    // pref or the engine, never deferred to an effect.
    remember(context) {
        SteamPrefs.init(context)
        SteamRepository.getInstance().initialize(context)
        SteamFriendsStore.init(context)
        true
    }
    val layout = rememberStorefrontLayout()
    DeckStoreTheme {
        SteamFriendsTab(
            wide = layout.wide,
            onMessage = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
        )
    }
}

/**
 * Steam's Profile tab in the Deck tab strip — self-loading like the store front's, so the library
 * count comes from the sanctioned [rememberSteamLibrary] host and Open Friends flips the tab strip.
 */
@Composable
private fun DeckSteamProfilePane(onOpenFriends: () -> Unit) {
    val context = LocalContext.current
    // Same composition-time session init as the Friends pane — see there for why it must not
    // wait for an effect: everything the profile tab touches expects SteamMainActivity's onCreate
    // sequence to have already run.
    remember(context) {
        SteamPrefs.init(context)
        SteamRepository.getInstance().initialize(context)
        SteamFriendsStore.init(context)
        true
    }
    val library = rememberSteamLibrary()
    val layout = rememberStorefrontLayout()
    DeckStoreTheme {
        SteamProfileTab(
            wide = layout.wide,
            libraryCount = library.games.size,
            onOpenFriends = onOpenFriends,
            onOpenApp = { appId ->
                context.startActivity(
                    Intent(context, SteamGameDetailActivity::class.java)
                        .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, appId),
                )
            },
            onMessage = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
        )
    }
}
