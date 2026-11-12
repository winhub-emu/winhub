package com.winhub.hernandez.store

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.ui.deck.rememberScreenScale
import com.winhub.hernandez.ui.deck.rememberXmbRailState
import com.winhub.hernandez.ui.deck.xmbRailGroup
import com.winhub.hernandez.ui.theme.LocalAccentDim

/**
 * The store-AGNOSTIC section shell: the Steam storefront's four-tab host
 * ([SteamStorefrontHost]) re-cut so GOG and Epic get the same chrome without touching the Steam
 * code path.
 *
 * - **Portrait / compact** → a title row (store name + status slot + action icons) over the Deck tab
 *   strip ([DeckTabBar]), content beneath.
 * - **Landscape at Medium width or wider** → a left [androidx.compose.material3.NavigationRail]
 *   of EXACT [RAIL_WIDTH] (see [SteamMainActivity]'s note on why a bounded Column and not a bare
 *   NavigationRail — a `fillMaxWidth()` descendant would otherwise inflate it to the whole screen),
 *   scrolling as a whole, with the secondary actions in its tail.
 * - The rail is one focus group whose D-pad RIGHT lands in the content pane.
 * - Hardware back returns to the first tab before it leaves the section.
 *
 * The host owns nothing store-specific: tabs, counts and every action come from the caller.
 */
data class StoreSectionTab(val key: String, val label: String, val icon: ImageVector)

@Composable
fun StoreSectionHost(
    storeName: String,
    tabs: List<StoreSectionTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    counts: Map<String, Int> = emptyMap(),
    /** Portrait: drawn after the store name in the title row (a signed-in chip, a status pill). */
    statusSlot: @Composable () -> Unit = {},
    /** Landscape: drawn at the top of the rail, above the tabs. */
    railStatusSlot: @Composable ColumnScope.() -> Unit = {},
    /** Portrait: the right-aligned action icons. */
    actions: @Composable RowScope.() -> Unit = {},
    /** Landscape: the rail tail (two or three icons plus an overflow). */
    railActions: @Composable ColumnScope.() -> Unit = {},
    /** Transient themed message, drawn LAST so it floats over the tab content. */
    message: String? = null,
    onMessageTimeout: () -> Unit = {},
    content: @Composable (wide: Boolean, modifier: Modifier) -> Unit,
) {
    val layout = rememberStorefrontLayout()
    val contentFocus = remember { FocusRequester() }
    // XMB home: StoreXmbScreen already lays the screen out edge to edge in its frame, with the bars hidden.
    val xmb = LocalStoreXmb.current

    BackHandler(enabled = true) {
        if (selected == 0) onClose() else onSelect(0)
    }

    // XMB home: L2 / R2 step through the rail's tabs and then its end actions (wrapping) from anywhere on the screen.
    val railStops = rememberXmbRailCycle(xmb && tabs.isNotEmpty(), tabs.size, selected, contentFocus, onSelect)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (xmb) Modifier else Modifier.systemBarsPadding().displayCutoutPadding()),
    ) {
        if (layout.wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalXmbRailStops provides if (xmb) railStops else null) {
                    SectionRail(
                        tabs = tabs,
                        selected = selected,
                        counts = counts,
                        onSelect = onSelect,
                        contentFocus = contentFocus,
                        statusSlot = railStatusSlot,
                        actions = railActions,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Requester BEFORE the focus group — the rail's `right = contentFocus`
                        // resolves against the group that follows it. Order is load-bearing.
                        .focusRequester(contentFocus)
                        .focusGroup(),
                ) { content(true, Modifier.fillMaxSize()) }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = storeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    statusSlot()
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        actions()
                    }
                }
                SectionTabRow(tabs = tabs, selected = selected, counts = counts, onSelect = onSelect)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .focusRequester(contentFocus)
                        .focusGroup(),
                ) { content(false, Modifier.fillMaxSize()) }
            }
        }

        message?.let { StoreMessageBar(it, onMessageTimeout) }
    }
}

/** Fixed rail width — wide enough for "Library (12)" at labelSmall. */
private val RAIL_WIDTH = 96.dp

@Composable
private fun SectionTabRow(
    tabs: List<StoreSectionTab>,
    selected: Int,
    counts: Map<String, Int>,
    onSelect: (Int) -> Unit,
) {
    // The same Deck tab strip Steam uses, so every store's Store / Library / Profile tabs look alike.
    DeckTabBar(
        tabs = tabs.map { DeckTabSpec(it.label, counts[it.key] ?: 0) },
        selected = selected.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)),
        onSelect = onSelect,
    )
}

@Composable
private fun SectionRail(
    tabs: List<StoreSectionTab>,
    selected: Int,
    counts: Map<String, Int>,
    onSelect: (Int) -> Unit,
    contentFocus: FocusRequester,
    statusSlot: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    // XMB home: a slim rail, just wide enough for an icon over a short label.
    val xmb = LocalStoreXmb.current
    val railWidth = if (xmb) rememberScreenScale().xmbRailWidth() else RAIL_WIDTH
    // XMB home: Up / Down on the rail selects the tab it lands on; Left back from the content lands on the selected one.
    val railState = rememberXmbRailState()
    Column(
        modifier = Modifier
            // EXACT width. Do not replace with widthIn/wrapContentWidth.
            .width(railWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .focusGroup()
            .focusProperties { right = contentFocus }
            .xmbRailGroup(railState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (xmb) 2.dp else 6.dp))
        statusSlot()
        Spacer(Modifier.height(if (xmb) 4.dp else 8.dp))

        tabs.forEachIndexed { index, t ->
            val n = counts[t.key]?.takeIf { it > 0 }
            if (xmb) {
                XmbRailItem(t.label, t.icon, selected = selected == index, count = n, onClick = { onSelect(index) }, rail = railState)
                return@forEachIndexed
            }
            NavigationRailItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(t.icon, contentDescription = t.label) },
                label = {
                    Text(
                        text = if (n != null) "${t.label} ($n)" else t.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                alwaysShowLabel = true,
                modifier = Modifier.steamFocusRing(),
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = LocalAccentDim.current,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.width(if (xmb) railWidth - 16.dp else RAIL_WIDTH - 24.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(4.dp))
        actions()
        Spacer(Modifier.height(8.dp))
    }
}
