package com.winhub.hernandez.store

import androidx.compose.runtime.CompositionLocalProvider
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
 * The Epic **Store** tab — the Steam Store tab re-cut over Epic's store GraphQL.
 *
 * Rails: Free This Week (the giveaway feed), On Sale, New Releases, Free To Play. Ownership is
 * matched on the offer's catalog item ids against the library cache (with the namespace as a
 * fallback). Claims and purchases go through the store page in the in-app WebView — Epic's free
 * claim is a web checkout with a captcha, so there is no honest API path.
 */
@Composable
fun EpicStoreTab(
    wide: Boolean,
    ownedItemIds: Set<String>,
    ownedNamespaces: Set<String>,
    installedNamespaces: Set<String>,
    onOpenOwned: (CatalogItem) -> Unit,
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

    var featured by remember { mutableStateOf<EpicStoreCatalog.Featured?>(EpicStoreCatalog.cachedFeatured(ctx)) }
    var loading by remember { mutableStateOf(featured == null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        if (featured == null) loading = true
        val fresh = runCatching { EpicStoreCatalog.featured(ctx, force = reloadKey > 0) }.getOrNull()
        if (fresh != null) featured = fresh
        loading = false
        if (featured == null) onMessage("Epic's store didn't answer")
    }

    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        results = runCatching { EpicStoreCatalog.search(ctx, term) }.getOrDefault(emptyList())
        searching = false
    }

    fun isOwned(item: CatalogItem): Boolean {
        val items = item.extra["items"]?.split(',').orEmpty()
        if (items.any { it in ownedItemIds }) return true
        val ns = item.extra["namespace"].orEmpty()
        return ns.isNotBlank() && ns in ownedNamespaces
    }

    fun actionFor(item: CatalogItem): CatalogAction = when {
        isOwned(item) -> CatalogAction.Open(item.extra["namespace"].orEmpty() in installedNamespaces)
        item.isFree -> CatalogAction.ClaimFree
        else -> CatalogAction.ViewOnStore
    }

    fun open(item: CatalogItem) {
        if (isOwned(item)) onOpenOwned(item) else onOpenCatalog(item)
    }

    fun act(item: CatalogItem) {
        when (actionFor(item)) {
            is CatalogAction.Open -> onOpenOwned(item)
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
                    placeholder = "Search the Epic Games Store…",
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
                    storeLabel = "Epic",
                    emptyBody = "Nothing on the Epic Games Store matched that. Try a shorter or different title.",
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
                        body = "Epic's storefront didn't answer. Search still works, and everything you " +
                            "already own is in the Library tab.",
                        actionLabel = "Retry",
                        onAction = { reloadKey++ },
                    )
                }

                else -> {
                    val data = featured!!
                    if (wide) EpicStoreLandscape(data, ::actionFor, ::open, ::act, Modifier.weight(1f))
                    else EpicStorePortrait(data, ::actionFor, ::open, ::act, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EpicStorePortrait(
    data: EpicStoreCatalog.Featured,
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
                    storeLabel = "Epic",
                    onOpen = { onOpen(hero) },
                    onAction = { onAction(hero) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    eyebrow = if (hero.discountPercent == 100) "FREE THIS WEEK" else "FEATURED",
                )
            }
        }
        catalogRail("Free This Week", "Claim before it's gone", data.freeNow, "Epic", actionFor, onOpen, onAction)
        catalogRail("On Sale", "Current discounts", data.onSale, "Epic", actionFor, onOpen, onAction)
        catalogRail("New Releases", "Just landed", data.newReleases, "Epic", actionFor, onOpen, onAction)
        catalogRail("Free To Play", "Always free", data.freeToPlay, "Epic", actionFor, onOpen, onAction)
    }
}

@Composable
private fun EpicStoreLandscape(
    data: EpicStoreCatalog.Featured,
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
                        storeLabel = "Epic",
                        onOpen = { onOpen(hero) },
                        onAction = { onAction(hero) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        eyebrow = if (hero.discountPercent == 100) "FREE THIS WEEK" else "FEATURED",
                    )
                }
            }
            val actOn = (data.freeNow + data.freeToPlay).distinctBy { it.id }
            if (actOn.isNotEmpty()) {
                item(key = "free_hdr") { StoreSectionHeader("Free Games", "Claim on the Epic Games Store") }
                items(actOn, key = { "free_${it.id}" }) { g ->
                    CatalogResultRow(
                        item = g,
                        action = actionFor(g),
                        storeLabel = "Epic",
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
            catalogRail("On Sale", "Current discounts", data.onSale, "Epic", actionFor, onOpen, onAction)
            catalogRail("New Releases", "Just landed", data.newReleases, "Epic", actionFor, onOpen, onAction)
            if (data.onSale.isEmpty() && data.newReleases.isEmpty()) {
                item(key = "browse_empty") {
                    StoreNotice(title = "Nothing to browse right now", body = "Epic's store feeds came back empty.")
                }
            }
        }
    }
}
