package com.winhub.hernandez.store

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SportsEsports
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.winhub.hernandez.ui.theme.StoreDiscountBg
import com.winhub.hernandez.ui.theme.StoreDiscountInk
import com.winhub.hernandez.ui.theme.StoreFreeGreen

/**
 * Shared chrome for the Steam storefront's four tabs: the portrait↔landscape decision, the D-pad
 * focus ring, and the capsule card / price row / action button the Store and Library both draw.
 *
 * **Colour rule:** every colour here comes from [MaterialTheme.colorScheme] or `LocalAccentDim`.
 * The only exceptions are the three store-semantic tokens in `ui/theme/Color.kt`
 * ([StoreDiscountInk] / [StoreDiscountBg] / [StoreFreeGreen]) — a discount badge that recolours
 * with the user's accent stops reading as "on sale". Nothing in this package hardcodes a hex.
 */

// ── Layout ────────────────────────────────────────────────────────────────────────────────────

/**
 * How the storefront should lay itself out right now.
 *
 * [wide] is the single switch every tab keys off: false → the portrait single-column stack under a
 * top [androidx.compose.material3.TabRow]; true → a left [androidx.compose.material3.NavigationRail]
 * with a two-column / master-detail body. Each tab then splits into a `…Portrait` / `…Landscape`
 * composable, matching the idiom `SteamFriendProfileScreen` already uses.
 *
 * These devices are gaming handhelds — landscape is arguably the PRIMARY orientation — so the
 * landscape branch is a designed layout that earns the width (more grid columns, more rail cards,
 * master-detail Friends), not a reflow of the portrait one.
 */
data class SteamStorefrontLayout(
    val wide: Boolean,
    /** Material's width bucket, kept for anything that wants Expanded-only affordances. */
    val widthClass: WindowWidthSizeClass,
)

/**
 * Resolve the layout from Material's real width breakpoints rather than a guessed dp number.
 *
 * [WindowSizeClass.calculateFromSize] is fed the live [LocalConfiguration] size, so this recomposes
 * on every rotation / multi-window resize with no Activity plumbing. `wide` requires BOTH landscape
 * and at least Medium width (≥600dp) — the same bar `SteamFriendsRoot` and `FriendProfileScreen`
 * already use for their two-pane layouts, now expressed as the size class instead of a literal.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberStorefrontLayout(): SteamStorefrontLayout {
    val cfg = LocalConfiguration.current
    val sizeClass = WindowSizeClass.calculateFromSize(
        DpSize(cfg.screenWidthDp.dp, cfg.screenHeightDp.dp),
    )
    val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    val wide = landscape && sizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    // Keyed on the resolved values, so this fires on an actual switch — never per recomposition.
    LaunchedEffect(wide, sizeClass.widthSizeClass) {
        StorefrontLog.i(
            StorefrontLog.HOST,
            "layout: ${if (wide) "LANDSCAPE/rail" else "PORTRAIT/tabs"} " +
                "(${cfg.screenWidthDp}x${cfg.screenHeightDp}dp, width=${sizeClass.widthSizeClass})",
        )
    }
    return SteamStorefrontLayout(wide = wide, widthClass = sizeClass.widthSizeClass)
}

// ── D-pad / gamepad focus ─────────────────────────────────────────────────────────────────────

/**
 * A visible focus state for gamepad + D-pad users. Compose's default touch indication draws
 * nothing on focus, so a controller-only user would otherwise have no idea where they are on a
 * handheld with no touch in play.
 *
 * Draws a 2dp accent ring while this element (or anything inside it) holds focus. Put it BEFORE
 * `.clickable {}` in the chain so the ring wraps the whole target; on a Material `Card(onClick=)`
 * or `Button` just pass it in the `modifier` — the internal clickable is a descendant and
 * `hasFocus` picks it up.
 */
@Composable
fun Modifier.steamFocusRing(shape: Shape = RoundedCornerShape(10.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(
            width = if (focused) 2.dp else 0.dp,
            color = if (focused) accent else Color.Transparent,
            shape = shape,
        )
}

// ── Section chrome ────────────────────────────────────────────────────────────────────────────

/** A rail/section header: bold title on the left, muted sub-label pushed to the right. */
@Composable
fun StoreSectionHeader(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A selectable filter chip — the storefront's ONE chip style.
 *
 * Started life as the Friends tab's All / Online / In-Game chips and moved here when the Library
 * grew its own All / Games / Demos row, so both read and focus identically rather than drifting
 * into two lookalike implementations.
 *
 * Selected = filled accent with on-accent ink; unselected = raised container with an outline. Both
 * states carry [steamFocusRing], so a D-pad user can see where they are.
 */
@Composable
fun StoreFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .steamFocusRing(shape)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 5.dp),
    )
}

/** Centered empty/error block with an optional retry — the storefront's graceful-degrade surface. */
@Composable
fun StoreNotice(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.steamFocusRing(RoundedCornerShape(20.dp)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { Text(actionLabel) }
        }
    }
}

// ── Capsule art ───────────────────────────────────────────────────────────────────────────────

/**
 * Ordered capsule-art candidates for [appId], best first.
 *
 * ## Why a chain and not one URL
 * [SteamStoreSearch.headerUrl] builds `cdn.akamai.steamstatic.com/steam/apps/<id>/header.jpg` —
 * Steam's LEGACY host. It resolves for older titles and 404s for recent ones (device log: ~20
 * misses, every one a 3-5M appId, i.e. a 2024+ release). That helper is SHARED with
 * `GameFolderScanner` and `ShortcutsScreen`, so its semantics are deliberately left alone; the
 * storefront layers a chain on top instead.
 *
 * Order:
 *  1. [apiUrl] — the image URL Steam's own `featuredcategories` / search response handed us. Always
 *     correct when present, because Steam built it.
 *  2. `shared.cloudflare.steamstatic.com` — the modern host, same path shape [SteamGame.headerUrl]
 *     already uses successfully for owned games.
 *  3. `shared.fastly.steamstatic.com` — the other modern edge, for when Cloudflare misses.
 *  4. The legacy akamai URL, last, so anything only present on the old host still resolves.
 *
 * Anything that needs a NETWORK ROUND-TRIP to learn its URL cannot live in this list — it would
 * have to block to build it. Those run as ordered async phases after every entry here has failed;
 * see [CAPSULE_RESOLVERS].
 *
 * ## PICS art (now live)
 * [SteamLibraryArt.header] is the app's PUBLISHED capsule from PICS appinfo — the same list Steam's
 * own client reads, which is why it resolves for legacy titles whose `header.jpg` doesn't exist at
 * the constructed path and for newer titles on content-hashed asset paths. It costs nothing (the
 * engine already downloaded it) and is inserted right after an explicit [apiUrl].
 */
internal fun capsuleCandidates(appId: Int, apiUrl: String? = null): List<String> = buildList {
    apiUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
    // Steam's OWN published asset list, straight from PICS appinfo via the engine's library
    // snapshot. No network call, no rate limit, no region dependency — so for an owned game this
    // outranks every constructed guess below and is why the Library needs no fallback at all.
    // Null for anything not owned, in which case the chain is unchanged.
    SteamLibraryArt.header(appId)?.let { add(it) }
    if (appId > 0) {
        add("https://shared.cloudflare.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg")
        add("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg")
        add(SteamStoreSearch.headerUrl(appId))
    }
}.distinct()

/**
 * One async capsule-art source: a name for the log and a suspending lookup returning a URL or null.
 */
internal class CapsuleResolver(
    val source: String,
    val resolve: suspend (android.content.Context, Int) -> String?,
)

/**
 * Async art sources, tried IN ORDER after every static [capsuleCandidates] entry has failed.
 *
 * Order is the whole point and is deliberately Steam-first:
 *  1. `appdetails` — Steam's OWN canonical `header_image` for the app. Authoritative, and the only
 *     real answer for Library items, which never receive an `apiUrl` and so have nothing but
 *     constructed guesses. Heavily rate-limited, hence batched + paced in [SteamStoreCatalog].
 *  2. SteamGridDB — community art. Correct-ish and often the only thing left, but never allowed to
 *     pre-empt Steam's own answer.
 *
 * Adding a rung means adding an entry here; nothing else changes.
 */
internal val CAPSULE_RESOLVERS: List<CapsuleResolver> = listOf(
    CapsuleResolver("appdetails") { ctx, appId -> SteamStoreCatalog.appDetailsHeader(ctx, appId) },
    CapsuleResolver("SteamGridDB") { ctx, appId -> SteamStoreCatalog.sgdbCapsule(ctx, appId) },
)

/**
 * The 92:43 store capsule for [appId]. Walks [capsuleCandidates] on each load failure and settles on
 * the themed placeholder (icon + title) only once every candidate is exhausted — so a delisted or
 * art-less app still reads, exactly as the prototype's `.ph` fallback does.
 *
 * [apiUrl] is the image URL Steam's own API returned for this item, when the caller has one.
 */
@Composable
fun StoreCapsule(
    appId: Int,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    apiUrl: String? = null,
    /**
     * Aspect ratio to force, or null to fill whatever [modifier] gives instead. Cards want the
     * 92:43 capsule shape; the game-detail hero is a fixed-height band and sizes itself, so it
     * passes null rather than fighting the ratio.
     */
    aspectRatio: Float? = 92f / 43f,
) {
    val ctx = LocalContext.current
    val candidates = remember(appId, apiUrl) { capsuleCandidates(appId, apiUrl) }
    // Phase 1 cursor. == candidates.size means every static candidate failed.
    var attempt by remember(candidates) { mutableStateOf(0) }
    // Phase 2 cursor: index into CAPSULE_RESOLVERS. == size means they all came up empty.
    var resolverIndex by remember(appId) { mutableStateOf(0) }
    // The URL the current resolver produced, if any.
    var resolvedUrl by remember(appId) { mutableStateOf<String?>(null) }

    val staticsExhausted = attempt >= candidates.size

    // Runs ONLY after the static chain fails, one resolver at a time, in order. Every lookup is
    // cached (hits AND misses) in SteamStoreCatalog, so a card scrolling back into view costs a map
    // lookup rather than a request — which is what keeps a rail of art-less cards from stampeding
    // the rate-limited appdetails endpoint.
    LaunchedEffect(appId, staticsExhausted, resolverIndex) {
        if (!staticsExhausted || resolvedUrl != null) return@LaunchedEffect
        if (resolverIndex >= CAPSULE_RESOLVERS.size) {
            StorefrontLog.artOutcome(
                appId,
                source = null,
                msg = "app $appId: NO capsule art — ${candidates.size} Steam CDN candidate(s) plus " +
                    CAPSULE_RESOLVERS.joinToString("/") { it.source } +
                    " all came up empty; showing the placeholder",
            )
            return@LaunchedEffect
        }
        val resolver = CAPSULE_RESOLVERS[resolverIndex]
        val found = runCatching { resolver.resolve(ctx, appId) }.getOrNull()
        if (found != null) {
            resolvedUrl = found
            StorefrontLog.artOutcome(
                appId,
                source = resolver.source,
                msg = "app $appId: no Steam CDN capsule — RESCUED by ${resolver.source}",
            )
        } else {
            // Advance; the effect re-runs on the new index and tries the next source.
            StorefrontLog.artCandidateFailed(
                "app $appId: ${resolver.source} had no capsule art — trying the next source",
            )
            resolverIndex += 1
        }
    }

    Box(
        modifier = modifier
            .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // Placeholder underneath: shows through until (and if) a capsule loads, and is what remains
        // when every source has failed. Nothing below ever replaces it with a spinner or a broken
        // image — each stage either renders art or falls through to this.
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        val resolved = resolvedUrl
        when {
            // Phase 1 — Steam's own CDNs, in order.
            !staticsExhausted -> {
                val url = candidates[attempt]
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        val next = attempt + 1
                        if (next < candidates.size) {
                            // Expected and uninteresting: a miss on one host with others to try.
                            StorefrontLog.artCandidateFailed(
                                "app $appId: capsule candidate ${attempt + 1}/${candidates.size} missed ($url)",
                            )
                        } else {
                            StorefrontLog.artCandidateFailed(
                                "app $appId: all ${candidates.size} Steam CDN candidate(s) failed — " +
                                    "starting the async resolvers",
                            )
                        }
                        attempt = next
                    },
                )
            }

            // Phase 2 — whichever resolver answered.
            resolved != null -> AsyncImage(
                model = resolved,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // Even a resolved URL can fail to decode; drop it and let the NEXT resolver try,
                // rather than leaving a broken image.
                onError = {
                    StorefrontLog.artCandidateFailed(
                        "app $appId: art from ${CAPSULE_RESOLVERS.getOrNull(resolverIndex)?.source} " +
                            "failed to load — trying the next source",
                    )
                    resolvedUrl = null
                    resolverIndex += 1
                },
            )

            // Phase 3 — nothing left; the placeholder above stands on its own.
            else -> Unit
        }
    }
}

// ── Price ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The price row: "-50%  $̶2̶4̶.̶9̶9̶  $12.49", or "Free to Play", or nothing when the endpoint gave us
 * no price at all. The discount chip and the free label are the ONLY non-scheme colours here.
 */
@Composable
fun StorePriceRow(item: SteamStoreCatalog.StoreItem, modifier: Modifier = Modifier) {
    when {
        item.isFree -> Text(
            text = "Free to Play",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = StoreFreeGreen,
            modifier = modifier,
        )

        !item.hasPrice -> Unit // price unknown → draw nothing rather than a fake "$0.00"

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
                    text = SteamStoreCatalog.formatPrice(item.originalCents, item.currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough,
                    maxLines = 1,
                )
            }
            Text(
                text = SteamStoreCatalog.formatPrice(item.finalCents, item.currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

// ── Action button ─────────────────────────────────────────────────────────────────────────────

/** What the store/library action button on a title should currently offer. */
sealed interface StoreAction {
    /** Owned → open the detail screen where the real download lives. */
    data object Download : StoreAction

    /** Free and not owned → RequestFreeLicense via [SteamFreeLicense]. */
    data object AddToLibrary : StoreAction

    /** A free-license request is in flight. */
    data object Working : StoreAction

    /** Paid and not owned. Non-actionable by design — the app cannot buy anything. */
    data object NotOwned : StoreAction
}

/**
 * The one action button both the Store cards and the Library cards draw, so a title looks the same
 * wherever it appears.
 *
 * - [StoreAction.AddToLibrary] → filled accent (the primary call to action).
 * - [StoreAction.Download] → the raised container tier, so an owned title reads as "already yours".
 * - [StoreAction.NotOwned] → outlined and disabled: honest about the fact that the app cannot
 *   purchase. It stays FOCUSABLE-but-disabled rather than being omitted, so D-pad traversal down a
 *   rail doesn't skip randomly.
 */
@Composable
fun StoreActionButton(
    action: StoreAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(7.dp)
    val pad = if (compact) PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    else PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    val style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium

    when (action) {
        StoreAction.AddToLibrary -> Button(
            onClick = onClick,
            modifier = modifier.steamFocusRing(shape),
            shape = shape,
            contentPadding = pad,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add to Library", style = style, maxLines = 1)
        }

        StoreAction.Working -> Button(
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
            Text("Adding…", style = style, maxLines = 1)
        }

        StoreAction.Download -> Button(
            onClick = onClick,
            modifier = modifier.steamFocusRing(shape),
            shape = shape,
            contentPadding = pad,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Download", style = style, maxLines = 1)
        }

        StoreAction.NotOwned -> OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = modifier,
            shape = shape,
            contentPadding = pad,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text("Not owned", style = style, maxLines = 1)
        }
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────────────────────

/** Fixed card width for the horizontal rails. Landscape simply fits more of them. */
val StoreCardWidth = 176.dp

/**
 * One rail card: capsule over title, price row, action button. The whole card is the primary focus
 * target (opens the detail screen); the action button is a second target directly below it, so a
 * D-pad reads RIGHT = next game, DOWN = this game's action.
 */
@Composable
fun StoreRailCard(
    item: SteamStoreCatalog.StoreItem,
    action: StoreAction,
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
        StoreCapsule(
            appId = item.appId,
            title = item.name,
            modifier = Modifier.fillMaxWidth(),
            apiUrl = item.artUrl,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StorePriceRow(item)
            StoreActionButton(
                action = action,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
        }
    }
}

/**
 * One search-result row: thumb + title + tags + price on the left, action on the right — the
 * prototype's `.row`. Used for the vertical result list that replaces the rails while searching.
 */
@Composable
fun StoreResultRow(
    item: SteamStoreCatalog.StoreItem,
    action: StoreAction,
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
        StoreCapsule(
            appId = item.appId,
            title = item.name,
            modifier = Modifier.width(104.dp),
            shape = RoundedCornerShape(6.dp),
            apiUrl = item.artUrl,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.name,
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
            StorePriceRow(item)
        }
        StoreActionButton(
            action = action,
            onClick = onAction,
            modifier = Modifier.width(112.dp),
            compact = true,
        )
    }
}

/**
 * The featured hero: full-bleed capsule with a bottom scrim carrying eyebrow / title / price.
 * The scrim is drawn over photographic art, so it stays a black gradient in every theme — the same
 * call the library grid tile already makes for its name scrim.
 */
@Composable
fun StoreHeroCard(
    item: SteamStoreCatalog.StoreItem,
    action: StoreAction,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
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
        StoreCapsule(
            appId = item.appId,
            title = item.name,
            modifier = Modifier.fillMaxWidth(),
            apiUrl = item.artUrl,
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
                text = "FEATURED & RECOMMENDED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                // On the black scrim, not on a themed surface — but still the live accent, so a
                // green-accent user gets a green eyebrow.
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                // Fixed white: this sits on the art scrim, like the grid-tile name.
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StorePriceRow(item, modifier = Modifier.weight(1f, fill = false))
                StoreActionButton(action = action, onClick = onAction, compact = true)
            }
        }
    }
}

/**
 * Transient themed message bar for the storefront (added to library / couldn't add / queued).
 *
 * Delegates to the bar the Steam screens already use: on this ROM (app targets SDK 28) a system
 * `Toast` renders as an empty black box, so every transient message in this package must go
 * through an in-Compose surface. Draw it LAST in the host so it floats over the tab content.
 */
@Composable
fun StoreMessageBar(message: String, onTimeout: () -> Unit) = UninstallResultBar(message, onTimeout)
