package com.winhub.hernandez.store

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.ui.deck.rememberScreenScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The **Store** tab — the storefront's new front door, replacing the old library-first landing.
 *
 * Portrait: search field, featured hero, then three horizontal rails (What's New / Top Free Games /
 * Latest Deals). Landscape: a designed two-column split ([StoreLandscape]) — hero + the free-games
 * column on the left (the things you can actually act on), the two browse rails on the right, each
 * pane scrolling independently. Typing replaces either layout with a vertical result list.
 *
 * **Degrades, never fails.** [SteamStoreCatalog] talks to undocumented Steam endpoints, so an empty
 * or failed fetch shows an inline retry with a pointer at the Library tab — never a crash, never a
 * blank screen. The tab is fully usable with no network at all.
 */
@Composable
fun SteamStoreTab(
    wide: Boolean,
    ownedAppIds: Set<Int>,
    onOpenApp: (Int) -> Unit,
    onMessage: (String) -> Unit,
    onLibraryChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SteamStoreCatalog.StoreItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    var featured by remember { mutableStateOf<SteamStoreCatalog.Featured?>(null) }
    var loadingFeatured by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    // appIds with a free-license request in flight — drives the per-card "Adding…" state.
    var pending by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(reloadKey) {
        loadingFeatured = true
        featured = runCatching { SteamStoreCatalog.featured(ctx, force = reloadKey > 0) }
            .onFailure { StorefrontLog.w(StorefrontLog.STORE, "featured() threw — store rails unavailable", it) }
            .getOrNull()
        loadingFeatured = false
        val f = featured
        if (f == null || f.isEmpty) {
            StorefrontLog.w(
                StorefrontLog.STORE,
                "Store rails EMPTY (attempt ${reloadKey + 1}) — showing the inline retry; " +
                    "Library tab still has ${ownedAppIds.size} owned app(s)",
            )
        }
    }

    // Debounced search: 350ms after the last keystroke, so a fast typist makes one request, not ten.
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < 2) {
            results = emptyList(); searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(350)
        results = runCatching { SteamStoreCatalog.search(ctx, term) }
            .onFailure { StorefrontLog.w(StorefrontLog.STORE, "search(\"$term\") threw", it) }
            .getOrDefault(emptyList())
        searching = false
        if (results.isEmpty()) StorefrontLog.i(StorefrontLog.STORE, "search(\"$term\"): 0 results shown")
    }

    /** Owned → Download, in-flight → Working, free → Add, otherwise → the honest "Not owned". */
    fun actionFor(item: SteamStoreCatalog.StoreItem): StoreAction = when {
        item.appId in ownedAppIds -> StoreAction.Download
        item.appId in pending -> StoreAction.Working
        item.isFree -> StoreAction.AddToLibrary
        else -> StoreAction.NotOwned
    }

    fun onAction(item: SteamStoreCatalog.StoreItem) {
        when (actionFor(item)) {
            StoreAction.Download -> {
                StorefrontLog.i(StorefrontLog.STORE, "open detail for owned app ${item.appId} (${item.name})")
                onOpenApp(item.appId)
            }
            StoreAction.AddToLibrary -> {
                StorefrontLog.i(StorefrontLog.STORE, "Add to Library requested for app ${item.appId} (${item.name})")
                pending = pending + item.appId
                scope.launch {
                    val result = SteamFreeLicense.request(item.appId)
                    pending = pending - item.appId
                    when (result) {
                        is SteamFreeLicense.Result.Granted -> {
                            onLibraryChanged()
                            onMessage(
                                if (result.libraryUpdated) "${item.name} added to your library"
                                // A grant whose license push missed the engine's window: still a
                                // success, the PICS crawl just hasn't caught up yet.
                                else "${item.name} added — your library is still catching up",
                            )
                        }
                        is SteamFreeLicense.Result.AlreadyOwned -> {
                            onLibraryChanged()
                            onMessage("${item.name} is already in your library")
                        }
                        is SteamFreeLicense.Result.Failed -> {
                            StorefrontLog.w(
                                StorefrontLog.STORE,
                                "Add to Library FAILED for app ${item.appId} (${item.name}): ${result.message}",
                            )
                            onMessage(result.message)
                        }
                    }
                }
            }
            // Working / NotOwned are non-actionable by design.
            else -> Unit
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
                StoreSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = ""; focusManager.clearFocus() },
                    onSearch = { focusManager.clearFocus() },
                )
            }

            when {
                query.trim().length >= 2 -> StoreResults(
                    results = results,
                    searching = searching,
                    query = query.trim(),
                    wide = wide,
                    actionFor = ::actionFor,
                    onOpen = onOpenApp,
                    onAction = ::onAction,
                    modifier = Modifier.weight(1f),
                )

                loadingFeatured && featured == null -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

                featured == null || featured?.isEmpty == true -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    StoreNotice(
                        title = "Store browsing is unavailable",
                        body = "Steam's storefront feed didn't answer. Search still works, and " +
                            "everything you already own is in the Library tab.",
                        actionLabel = "Retry",
                        onAction = { reloadKey++ },
                    )
                }

                else -> {
                    val data = featured!!
                    if (wide) {
                        StoreLandscape(data, ::actionFor, onOpenApp, ::onAction, Modifier.weight(1f))
                    } else {
                        StorePortrait(data, ::actionFor, onOpenApp, ::onAction, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Layouts ───────────────────────────────────────────────────────────────────────────────────

/** Portrait: one scroll column — hero, then the three rails top to bottom. */
@Composable
private fun StorePortrait(
    data: SteamStoreCatalog.Featured,
    actionFor: (SteamStoreCatalog.StoreItem) -> StoreAction,
    onOpen: (Int) -> Unit,
    onAction: (SteamStoreCatalog.StoreItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().focusGroup(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        data.hero?.let { hero ->
            item(key = "hero") {
                StoreHeroCard(
                    item = hero,
                    action = actionFor(hero),
                    onOpen = { onOpen(hero.appId) },
                    onAction = { onAction(hero) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
        railItems("What's New", "New & trending", data.newReleases, actionFor, onOpen, onAction)
        railItems("Top Free Games", "Add instantly", data.topFree, actionFor, onOpen, onAction)
        railItems("Latest Deals", "Current specials", data.specials, actionFor, onOpen, onAction)
    }
}

/**
 * Landscape: a real two-column layout, following the `ProfilePortrait`/`ProfileLandscape` idiom
 * already in `SteamFriendProfileScreen`.
 *
 * LEFT (42%) is the *act on it* column — the hero plus Top Free Games as a vertical list, because
 * those are the only titles the app can actually add. RIGHT (58%) is the *browse* column — What's
 * New and Latest Deals as horizontal rails, which at this width show roughly twice the cards
 * portrait does. Both panes scroll independently and are separate focus groups.
 */
@Composable
private fun StoreLandscape(
    data: SteamStoreCatalog.Featured,
    actionFor: (SteamStoreCatalog.StoreItem) -> StoreAction,
    onOpen: (Int) -> Unit,
    onAction: (SteamStoreCatalog.StoreItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .focusGroup(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            data.hero?.let { hero ->
                item(key = "hero") {
                    StoreHeroCard(
                        item = hero,
                        action = actionFor(hero),
                        onOpen = { onOpen(hero.appId) },
                        onAction = { onAction(hero) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
            if (data.topFree.isNotEmpty()) {
                item(key = "free_hdr") { StoreSectionHeader("Top Free Games", "Add instantly") }
                items(data.topFree, key = { "free_${it.appId}" }) { g ->
                    StoreResultRow(
                        item = g,
                        action = actionFor(g),
                        onOpen = { onOpen(g.appId) },
                        onAction = { onAction(g) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .focusGroup(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            railItems("What's New", "New & trending", data.newReleases, actionFor, onOpen, onAction)
            railItems("Latest Deals", "Current specials", data.specials, actionFor, onOpen, onAction)
            if (data.newReleases.isEmpty() && data.specials.isEmpty()) {
                item(key = "browse_empty") {
                    StoreNotice(
                        title = "Nothing to browse right now",
                        body = "Steam's new-releases and specials feeds came back empty.",
                    )
                }
            }
        }
    }
}

/** One horizontal rail (header + LazyRow of cards) as LazyListScope items. Empty list = no rail. */
private fun androidx.compose.foundation.lazy.LazyListScope.railItems(
    title: String,
    sub: String,
    games: List<SteamStoreCatalog.StoreItem>,
    actionFor: (SteamStoreCatalog.StoreItem) -> StoreAction,
    onOpen: (Int) -> Unit,
    onAction: (SteamStoreCatalog.StoreItem) -> Unit,
) {
    if (games.isEmpty()) return
    item(key = "hdr_$title") {
        Spacer(Modifier.height(8.dp))
        StoreSectionHeader(title, sub)
    }
    item(key = "rail_$title") {
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            // XMB home: room above and below for a focused cover's pop.
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = if (LocalStoreXmb.current) 8.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(games, key = { it.appId }) { g ->
                StoreRailCard(
                    item = g,
                    action = actionFor(g),
                    onOpen = { onOpen(g.appId) },
                    onAction = { onAction(g) },
                )
            }
        }
    }
}

// ── Search ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun StoreSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    if (LocalStoreXmb.current) {
        XmbSearchField(
            query, "Search the Steam catalog…", onQueryChange, onClear, onSearch,
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        )
        return
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                "Search the Steam catalog…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.steamFocusRing(RoundedCornerShape(20.dp))) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(9.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/**
 * The vertical result list that replaces the rails while a query is active. In landscape it becomes
 * two columns of rows, so the wide screen shows twice as many hits without shrinking anything.
 */
@Composable
private fun StoreResults(
    results: List<SteamStoreCatalog.StoreItem>,
    searching: Boolean,
    query: String,
    wide: Boolean,
    actionFor: (SteamStoreCatalog.StoreItem) -> StoreAction,
    onOpen: (Int) -> Unit,
    onAction: (SteamStoreCatalog.StoreItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        StoreSectionHeader(
            title = if (searching && results.isEmpty()) "Searching…"
            else "${results.size} result${if (results.size == 1) "" else "s"}",
            sub = "“$query”",
        )
        when {
            results.isEmpty() && searching -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            results.isEmpty() -> StoreNotice(
                title = "No matches",
                body = "Nothing in Steam's catalog matched that. Try a shorter or different title.",
            )

            // XMB home: the results as a grid of portrait covers.
            LocalStoreXmb.current -> LazyVerticalGrid(
                columns = GridCells.Adaptive(rememberScreenScale().xmbCoverWidth()),
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                gridItems(results, key = { it.appId }) { g ->
                    XmbStoreCover(
                        art = listOfNotNull(steamTallArt(g.appId), g.artUrl), title = g.name, onClick = { onOpen(g.appId) },
                        footer = { StorePriceRow(g) },
                    )
                }
            }

            // Two columns in landscape: the rows are ~340dp of useful content, so a wide screen
            // fits two side by side instead of stretching one across the whole panel.
            wide -> LazyColumn(
                state = rememberTrackedListState(),
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(results.chunked(2), key = { it.first().appId }) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        pair.forEach { g ->
                            StoreResultRow(
                                item = g,
                                action = actionFor(g),
                                onOpen = { onOpen(g.appId) },
                                onAction = { onAction(g) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keep the last odd row's card at column width instead of full width.
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(results, key = { it.appId }) { g ->
                    StoreResultRow(
                        item = g,
                        action = actionFor(g),
                        onOpen = { onOpen(g.appId) },
                        onAction = { onAction(g) },
                    )
                }
            }
        }
    }
}
