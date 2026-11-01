package com.winhub.hernandez.store

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.ui.theme.StoreDiscountBg
import com.winhub.hernandez.ui.theme.StoreDiscountInk
import com.winhub.hernandez.ui.theme.StoreFreeGreen

/**
 * Store-AGNOSTIC storefront pieces: the catalog item model plus the card / row / hero / price-row /
 * action-button set, shared by the GOG and Epic storefronts.
 *
 * The Steam storefront keeps its own `StoreRailCard` & co. in [SteamStorefrontUi] because they are
 * bound to `SteamStoreCatalog.StoreItem` and the PICS art chain. These are the same designs re-cut
 * over [CatalogItem], which carries pre-resolved image URLs and PRE-FORMATTED price strings — every
 * store formats money differently (GOG hands us "$1.49", Epic hands us cents + a locale string), so
 * the UI never does currency maths.
 *
 * Colour rule is unchanged: everything reads [MaterialTheme.colorScheme] except the three
 * store-semantic tokens ([StoreDiscountInk] / [StoreDiscountBg] / [StoreFreeGreen]).
 */

// ── Model ─────────────────────────────────────────────────────────────────────────────────────

/** One catalog entry as the storefront cards draw it, whichever store produced it. */
data class CatalogItem(
    val store: Store,
    /** Store-native id: GOG product id, Epic offer id. */
    val id: String,
    val title: String,
    /** Wide (landscape) art for rails and heroes. */
    val imageUrl: String?,
    /** Tall (2:3) box art when the store publishes one; falls back to [imageUrl]. */
    val tallImageUrl: String? = null,
    /** Comma-joined genre / tag line under the title in result rows. */
    val tags: String = "",
    val isFree: Boolean = false,
    /** False when the endpoint gave no price at all — the price row then draws nothing. */
    val hasPrice: Boolean = false,
    /** Pre-formatted by the store ("$9.99"). */
    val finalPrice: String = "",
    val originalPrice: String = "",
    val discountPercent: Int = 0,
    /** The web product page — where "Get for free" / "View on <store>" land. */
    val storeUrl: String = "",
    val developer: String = "",
    val releaseDate: String = "",
    val description: String = "",
    /** Per-store identifiers the detail page needs (Epic namespace / slug, GOG slug …). */
    val extra: Map<String, String> = emptyMap(),
    /** Rated or tagged for adults by the store's own data; hidden from the storefront unless Show mature content is on. */
    val mature: Boolean = false,
) {
    val isDiscounted: Boolean get() = discountPercent > 0 && originalPrice.isNotBlank()
}

/** Tags that mark a title adult (store tags/slug, whatever the catalog calls it). */
private val MATURE_TAGS = setOf(
    "mature", "nsfw", "adult", "adult only", "adults only",
    "nudity", "sexual content", "sexual-content",
)

/** Whether a store's own tags mark a title as adult. */
internal fun matureByTags(tags: Collection<String>): Boolean =
    tags.any { it.trim().lowercase() in MATURE_TAGS }

/** Whether a store's own age ratings mark a title as adult: any rating system's 17+. */
internal fun matureByAge(ages: Collection<Int>): Boolean = ages.any { it >= 17 }

/** What the action button on a catalog card should offer right now. */
sealed interface CatalogAction {
    /** Owned → open the store's own detail page (Download / Play live there). */
    data class Open(val installed: Boolean) : CatalogAction

    /** Free and not owned → claim it on the store's website (in-app WebView). */
    data object ClaimFree : CatalogAction

    /** Paid and not owned → view the store page. The app cannot buy anything. */
    data object ViewOnStore : CatalogAction

    /** Something is in flight for this title. */
    data object Working : CatalogAction
}

// ── Art ───────────────────────────────────────────────────────────────────────────────────────

/**
 * Cover art with an ordered candidate chain and a themed placeholder underneath. Same contract as
 * Steam's `StoreCapsule`: never a spinner, never a broken image — each miss falls through to the
 * next URL and finally to the icon + title placeholder.
 */
@Composable
fun CatalogArt(
    candidates: List<String>,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    aspectRatio: Float? = 92f / 43f,
) {
    val urls = remember(candidates) { candidates.filter { it.isNotBlank() }.distinct() }
    var attempt by remember(urls) { mutableStateOf(0) }
    Box(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (attempt < urls.size) {
            AsyncImage(
                model = urls[attempt],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { attempt += 1 },
            )
        }
    }
}

/** Wide art first, tall art as the fallback — for rails, rows and heroes. */
fun CatalogItem.wideArt(): List<String> = listOfNotNull(imageUrl, tallImageUrl)

/** Tall art first, wide as the fallback — for 2:3 library tiles. */
fun CatalogItem.tallArt(): List<String> = listOfNotNull(tallImageUrl, imageUrl)

// ── Price ─────────────────────────────────────────────────────────────────────────────────────

/** "-50%  ~~$24.99~~  $12.49", or "Free", or nothing when the store gave no price. */
@Composable
fun CatalogPriceRow(item: CatalogItem, modifier: Modifier = Modifier) {
    when {
        item.isFree -> Text(
            text = "Free",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = StoreFreeGreen,
            modifier = modifier,
        )

        !item.hasPrice -> Unit

        else -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (item.isDiscounted) {
                Text(
                    text = "-${item.discountPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = StoreDiscountInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StoreDiscountBg)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Text(
                    text = item.originalPrice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                )
            }
            Text(
                text = item.finalPrice,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

// ── Action button ─────────────────────────────────────────────────────────────────────────────

/**
 * The one action button every catalog card and library tile draws.
 *
 * - [CatalogAction.ClaimFree] → filled accent (the primary call to action).
 * - [CatalogAction.Open] → the raised container tier ("already yours"); Play when installed.
 * - [CatalogAction.ViewOnStore] → outlined but ENABLED: unlike Steam, GOG and Epic purchases go
 *   through the web page, so the honest thing is to open it rather than dead-end on "Not owned".
 */
@Composable
fun CatalogActionButton(
    action: CatalogAction,
    storeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(7.dp)
    val pad = if (compact) PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    else PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    val style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    when (action) {
        CatalogAction.ClaimFree -> Button(
            onClick = onClick,
            modifier = modifier.steamFocusRing(shape),
            shape = shape,
            contentPadding = pad,
        ) {
            Icon(Icons.Filled.Redeem, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Get for free", style = style, maxLines = 1)
        }

        CatalogAction.Working -> Button(
            onClick = {},
            enabled = false,
            modifier = modifier,
            shape = shape,
            contentPadding = pad,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(6.dp))
            Text("Working…", style = style, maxLines = 1)
        }

        is CatalogAction.Open -> Button(
            onClick = onClick,
            modifier = modifier.steamFocusRing(shape),
            shape = shape,
            contentPadding = pad,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                if (action.installed) Icons.Filled.PlayArrow else Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(if (action.installed) "Play" else "Download", style = style, maxLines = 1)
        }

        CatalogAction.ViewOnStore -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.steamFocusRing(shape),
            shape = shape,
            contentPadding = pad,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("View on $storeLabel", style = style, maxLines = 1)
        }
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────────────────────

/** One rail card: wide art over title, price row, action. Same geometry as Steam's rail card. */
@Composable
fun CatalogRailCard(
    item: CatalogItem,
    action: CatalogAction,
    storeLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .width(StoreCardWidth)
            .steamFocusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onOpen),
    ) {
        CatalogArt(
            candidates = item.wideArt(),
            title = item.title,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            CatalogPriceRow(item)
            CatalogActionButton(
                action = action,
                storeLabel = storeLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
        }
    }
}

/** One search-result row: thumb + title + tags + price on the left, action on the right. */
@Composable
fun CatalogResultRow(
    item: CatalogItem,
    action: CatalogAction,
    storeLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .steamFocusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onOpen)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        CatalogArt(
            candidates = item.wideArt(),
            title = item.title,
            modifier = Modifier.width(104.dp),
            shape = RoundedCornerShape(6.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.tags.isNotBlank()) {
                Text(
                    text = item.tags,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CatalogPriceRow(item)
        }
        CatalogActionButton(
            action = action,
            storeLabel = storeLabel,
            onClick = onAction,
            modifier = Modifier.width(118.dp),
            compact = true,
        )
    }
}

/** The featured hero: full-bleed wide art with a bottom scrim carrying eyebrow / title / price. */
@Composable
fun CatalogHeroCard(
    item: CatalogItem,
    action: CatalogAction,
    storeLabel: String,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String = "FEATURED & RECOMMENDED",
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .steamFocusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onOpen),
    ) {
        CatalogArt(
            candidates = item.wideArt(),
            title = item.title,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                    ),
                )
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CatalogPriceRow(item, modifier = Modifier.weight(1f, fill = false))
                CatalogActionButton(action = action, storeLabel = storeLabel, onClick = onAction, compact = true)
            }
        }
    }
}

/**
 * A 2:3 library tile: tall box art over the title and a Play / Download button. Used by the GOG and
 * Epic Library tabs, whose stores publish portrait covers (SteamGridDB grids / DieselGameBoxTall).
 */
@Composable
fun CatalogLibraryTile(
    item: CatalogItem,
    installed: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Small text pill over the art's top-left corner ("Gen 2", "EOS"); null = none. */
    badgeText: String? = null,
    /** Shown in place of the Play / Download button while the game downloads ("Downloading 34%"); null = the button. */
    progressLabel: String? = null,
    /** The tile is just the 2:3 art: title and status sit on a fade at its bottom, no button (tap opens the game). */
    artOnly: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    if (artOnly) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .steamFocusRing(shape)
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(onClick = onOpen),
        ) {
            CatalogArt(candidates = item.tallArt(), title = item.title, modifier = Modifier.fillMaxWidth(), aspectRatio = 2f / 3f)
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badgeText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 7.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = progressLabel ?: if (installed) "Installed" else null
                if (status != null) Text(status, style = MaterialTheme.typography.labelSmall,
                    color = if (progressLabel != null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .steamFocusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onOpen),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CatalogArt(
                candidates = item.tallArt(),
                title = item.title,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = 2f / 3f,
            )
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progressLabel != null) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            } else CatalogActionButton(
                action = CatalogAction.Open(installed),
                storeLabel = "",
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
        }
    }
}

// ── Rails / lists ─────────────────────────────────────────────────────────────────────────────

/** One horizontal rail (header + LazyRow of cards) as LazyListScope items. Empty list = no rail. */
fun LazyListScope.catalogRail(
    title: String,
    sub: String,
    games: List<CatalogItem>,
    storeLabel: String,
    actionFor: (CatalogItem) -> CatalogAction,
    onOpen: (CatalogItem) -> Unit,
    onAction: (CatalogItem) -> Unit,
) {
    if (games.isEmpty()) return
    item(key = "hdr_$title") {
        Spacer(Modifier.height(8.dp))
        StoreSectionHeader(title, sub)
    }
    item(key = "rail_$title") {
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            // Keyed by store + id, and the list is de-duplicated upstream — a duplicate key inside a
            // LazyRow is a hard crash (the Steam storefront shipped exactly that once).
            items(games.distinctBy { it.id }, key = { "${it.store}_${it.id}" }) { g ->
                CatalogRailCard(
                    item = g,
                    action = actionFor(g),
                    storeLabel = storeLabel,
                    onOpen = { onOpen(g) },
                    onAction = { onAction(g) },
                )
            }
        }
    }
}

/** The vertical result list that replaces the rails while a query is active. */
@Composable
fun CatalogResults(
    results: List<CatalogItem>,
    searching: Boolean,
    query: String,
    wide: Boolean,
    storeLabel: String,
    emptyBody: String,
    actionFor: (CatalogItem) -> CatalogAction,
    onOpen: (CatalogItem) -> Unit,
    onAction: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(results) { results.distinctBy { it.id } }
    Column(modifier = modifier.fillMaxSize()) {
        StoreSectionHeader(
            title = if (searching && rows.isEmpty()) "Searching…"
            else "${rows.size} result${if (rows.size == 1) "" else "s"}",
            sub = "“$query”",
        )
        when {
            rows.isEmpty() && searching -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            rows.isEmpty() -> StoreNotice(title = "No matches", body = emptyBody)

            wide -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(rows.chunked(2), key = { it.first().id }) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        pair.forEach { g ->
                            CatalogResultRow(
                                item = g,
                                action = actionFor(g),
                                storeLabel = storeLabel,
                                onOpen = { onOpen(g) },
                                onAction = { onAction(g) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(rows, key = { it.id }) { g ->
                    CatalogResultRow(
                        item = g,
                        action = actionFor(g),
                        storeLabel = storeLabel,
                        onOpen = { onOpen(g) },
                        onAction = { onAction(g) },
                    )
                }
            }
        }
    }
}

/** The storefront search field — identical to Steam's, with a per-store placeholder. */
@Composable
fun CatalogSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                placeholder,
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
