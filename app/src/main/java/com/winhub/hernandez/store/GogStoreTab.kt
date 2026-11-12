package com.winhub.hernandez.store

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The GOG **Store** tab — the section's new front door, the Steam Store tab re-cut over GOG's
 * public catalog.
 *
 * Portrait: search, featured hero, then Trending / Free games / New releases / Deals rails.
 * Landscape: two columns — hero + the free-games list on the left (the titles you can actually act
 * on), the browse rails on the right. Typing replaces either layout with a result list.
 *
 * Owned titles open the app's own GOG detail page; everything else opens the store-only detail
 * page ([StoreCatalogDetailActivity]). "Get for free" / "View on GOG.com" open the product page in
 * the in-app WebView — GOG exposes no add-to-library API to third parties, so the claim is a web
 * checkout, exactly as Heroic does it.
 */
@Composable
fun GogStoreTab(
    wide: Boolean,
    ownedIds: Set<String>,
    installedIds: Set<String>,
    onOpenOwned: (String) -> Unit,
    onOpenCatalog: (CatalogItem) -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    var featured by remember { mutableStateOf<GogStoreCatalog.Featured?>(GogStoreCatalog.cachedFeatured(ctx)) }
    var loading by remember { mutableStateOf(featured == null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        if (featured == null) loading = true
        val fresh = runCatching { GogStoreCatalog.featured(ctx, force = reloadKey > 0) }.getOrNull()
        if (fresh != null) featured = fresh
        loading = false
        if (featured == null) onMessage("GOG's catalog didn't answer")
    }

    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        results = runCatching { GogStoreCatalog.search(ctx, term) }.getOrDefault(emptyList())
        searching = false
    }

    fun actionFor(item: CatalogItem): CatalogAction = when {
        item.id in ownedIds -> CatalogAction.Open(item.id in installedIds)
        item.isFree -> CatalogAction.ClaimFree
        else -> CatalogAction.ViewOnStore
    }

    fun open(item: CatalogItem) {
        if (item.id in ownedIds) onOpenOwned(item.id) else onOpenCatalog(item)
    }

    fun act(item: CatalogItem) {
        when (actionFor(item)) {
            is CatalogAction.Open -> onOpenOwned(item.id)
            CatalogAction.ClaimFree, CatalogAction.ViewOnStore -> {
                if (item.storeUrl.isBlank()) onMessage("No store page for ${item.title}")
                else onOpenWeb(item.storeUrl, item.title)
            }
            CatalogAction.Working -> Unit
        }
    }

    val header = rememberXmbHeader()
    CompositionLocalProvider(LocalXmbHeader provides header) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            header.Header {
                CatalogSearchField(
                    query = query,
                    placeholder = "Search the GOG catalog…",
                    onQueryChange = { query = it },
                    onClear = { query = ""; focusManager.clearFocus() },
                    onSearch = { focusManager.clearFocus() },
                )
            }

            when {
                query.trim().length >= 2 -> CatalogResults(
                    results = results,
                    searching = searching,
                    query = query.trim(),
                    wide = wide,
                    storeLabel = "GOG",
                    emptyBody = "Nothing on GOG matched that. Try a shorter or different title.",
                    actionFor = ::actionFor,
                    onOpen = ::open,
                    onAction = ::act,
                    modifier = Modifier.weight(1f),
                )

                loading && featured == null -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

                featured == null || featured?.isEmpty == true -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    StoreNotice(
                        title = "Store browsing is unavailable",
                        body = "GOG's catalog didn't answer. Search still works, and everything you " +
                            "already own is in the Library tab.",
                        actionLabel = "Retry",
                        onAction = { reloadKey++ },
                    )
                }

                else -> {
                    val data = featured!!
                    if (wide) GogStoreLandscape(data, ::actionFor, ::open, ::act, Modifier.weight(1f))
                    else GogStorePortrait(data, ::actionFor, ::open, ::act, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GogStorePortrait(
    data: GogStoreCatalog.Featured,
    actionFor: (CatalogItem) -> CatalogAction,
    onOpen: (CatalogItem) -> Unit,
    onAction: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().focusGroup(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        data.hero?.let { hero ->
            item(key = "hero") {
                CatalogHeroCard(
                    item = hero,
                    action = actionFor(hero),
                    storeLabel = "GOG",
                    onOpen = { onOpen(hero) },
                    onAction = { onAction(hero) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    eyebrow = "TRENDING ON GOG",
                )
            }
        }
        catalogRail("Trending", "Popular right now", data.trending, "GOG", actionFor, onOpen, onAction)
        catalogRail("Free Games", "Claim on GOG.com", data.free, "GOG", actionFor, onOpen, onAction)
        catalogRail("New Releases", "Just landed", data.newReleases, "GOG", actionFor, onOpen, onAction)
        catalogRail("Deals", "Biggest discounts", data.deals, "GOG", actionFor, onOpen, onAction)
    }
}

@Composable
private fun GogStoreLandscape(
    data: GogStoreCatalog.Featured,
    actionFor: (CatalogItem) -> CatalogAction,
    onOpen: (CatalogItem) -> Unit,
    onAction: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(0.42f).fillMaxHeight().focusGroup(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            data.hero?.let { hero ->
                item(key = "hero") {
                    CatalogHeroCard(
                        item = hero,
                        action = actionFor(hero),
                        storeLabel = "GOG",
                        onOpen = { onOpen(hero) },
                        onAction = { onAction(hero) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        eyebrow = "TRENDING ON GOG",
                    )
                }
            }
            if (data.free.isNotEmpty()) {
                item(key = "free_hdr") { StoreSectionHeader("Free Games", "Claim on GOG.com") }
                items(data.free.distinctBy { it.id }, key = { "free_${it.id}" }) { g ->
                    CatalogResultRow(
                        item = g,
                        action = actionFor(g),
                        storeLabel = "GOG",
                        onOpen = { onOpen(g) },
                        onAction = { onAction(g) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(0.58f).fillMaxHeight().focusGroup(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            catalogRail("Trending", "Popular right now", data.trending, "GOG", actionFor, onOpen, onAction)
            catalogRail("New Releases", "Just landed", data.newReleases, "GOG", actionFor, onOpen, onAction)
            catalogRail("Deals", "Biggest discounts", data.deals, "GOG", actionFor, onOpen, onAction)
            if (data.trending.isEmpty() && data.newReleases.isEmpty() && data.deals.isEmpty()) {
                item(key = "browse_empty") {
                    StoreNotice(title = "Nothing to browse right now", body = "GOG's catalog feeds came back empty.")
                }
            }
        }
    }
}
