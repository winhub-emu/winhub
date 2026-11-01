package com.winhub.hernandez.store.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.outlinedMenuCard

/**
 * The Steam game-detail LAYOUT, lifted out of `SteamGameDetailActivity` as a reusable scaffold so
 * the GOG and Epic detail pages (and the store-only catalog page) read exactly like it:
 *
 *   header (back · store badge · actions)
 *   hero band with the fade into the page background
 *   game name
 *   ONE primary action button (accent, or a read-only progress fill while downloading) + ⚙ gear
 *   optional info line under the button ("…% · speed · ETA")
 *   pill tab strip (Details · DLC · Cloud saves …)
 *   the selected tab's body
 *
 * Steam itself is deliberately NOT routed through this file (no Steam regression risk); this is a
 * faithful re-cut of its composition over theme tokens. The tab strip and gear follow the approved
 * mockup 1:1 — same 9dp pills, same 46dp button height, same outlined menu card.
 */

/** One entry in the ⚙ gear dropdown. */
data class StoreGearItem(
    val emoji: String,
    val label: String,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** What the big primary button shows right now. */
sealed interface StorePrimaryAction {
    /** An actionable accent button (Install / Launch / Get for free …). */
    data class Button(val label: String, val onClick: () -> Unit, val enabled: Boolean = true) : StorePrimaryAction

    /**
     * Read-only download state: the SINGLE progress indicator. [fraction] is the solid front fill
     * (bytes on disk); [backFraction], when larger, is the lighter fill behind it (bytes fetched).
     * Pause / cancel live in the gear.
     */
    data class Progress(val label: String, val fraction: Float, val backFraction: Float = 0f) : StorePrimaryAction
}

@Composable
fun StoreDetailScaffold(
    onBack: () -> Unit,
    title: String,
    hero: @Composable BoxScope.() -> Unit,
    primary: StorePrimaryAction,
    gear: List<StoreGearItem>,
    tabs: List<String>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    storeBadge: @Composable () -> Unit = {},
    headerActions: @Composable RowScope.() -> Unit = { DownloadsButton() },
    /** Small line(s) between the name and the button — install status, exe name. */
    subtitle: (@Composable ColumnScope.() -> Unit)? = null,
    /** Line under the primary button (download rate / ETA). */
    infoLine: String? = null,
    tabBadges: Map<Int, String> = emptyMap(),
    /** Extra floating content (dialogs, result bar) drawn over the page. */
    overlay: @Composable BoxScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            StoreDetailHeader(onBack = onBack, storeBadge = storeBadge, actions = headerActions)

            StoreHero(imageContent = hero)

            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) subtitle()
            }

            StorePrimaryActionRow(primary = primary, gear = gear)

            if (!infoLine.isNullOrBlank()) {
                Text(
                    text = infoLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            if (tabs.size > 1) {
                StoreDetailTabs(
                    labels = tabs,
                    selected = selectedTab,
                    badges = tabBadges,
                    onSelect = onSelectTab,
                )
            }

            body()
        }
        overlay()
    }
}

/**
 * The primary button + gear row. The button is a 46dp accent block (or the two-layer progress fill
 * while downloading); the gear is a 46dp outlined square opening the app's outlined dropdown card.
 */
@Composable
fun StorePrimaryActionRow(
    primary: StorePrimaryAction,
    gear: List<StoreGearItem>,
    modifier: Modifier = Modifier,
) {
    var gearExpanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (primary) {
            is StorePrimaryAction.Progress -> {
                val front = primary.fraction.coerceIn(0f, 1f)
                val back = primary.backFraction.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(shape)
                        .background(cs.surfaceContainerHigh)
                        .border(1.dp, cs.outline, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (back > front) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .fillMaxWidth(back)
                                .background(cs.primary.copy(alpha = 0.4f)),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .fillMaxWidth(front)
                            .background(cs.primary),
                    )
                    Text(
                        text = primary.label,
                        color = cs.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            is StorePrimaryAction.Button -> Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(shape)
                    .background(if (primary.enabled) cs.primary else cs.primary.copy(alpha = 0.4f))
                    .clickable(enabled = primary.enabled) { primary.onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = primary.label,
                    color = cs.onPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        if (gear.isNotEmpty()) {
            Box {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(shape)
                        .background(cs.surface)
                        .border(1.dp, cs.outline, shape)
                        .clickable { gearExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", fontSize = 18.sp, color = cs.primary)
                }
                DropdownMenu(
                    expanded = gearExpanded,
                    onDismissRequest = { gearExpanded = false },
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    gear.forEachIndexed { i, item ->
                        if (i > 0) MenuItemDivider()
                        val base = if (item.danger) cs.error else cs.onSurface
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = item.label,
                                    color = if (item.enabled) base else base.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            leadingIcon = { Text(item.emoji, fontSize = 14.sp) },
                            enabled = item.enabled,
                            onClick = { gearExpanded = false; item.onClick() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The detail page's pill tab strip — horizontally scrollable, styled like the action buttons. A
 * tab may carry a small count badge (Steam uses it for "done/total" achievements).
 */
@Composable
fun StoreDetailTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    badges: Map<Int, String> = emptyMap(),
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSel = index == selected
            val badge = badges[index]
            val shape = RoundedCornerShape(9.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(shape)
                    .background(if (isSel) cs.primary.copy(alpha = 0.14f) else cs.surface)
                    .border(1.dp, if (isSel) cs.primary.copy(alpha = 0.45f) else cs.outline.copy(alpha = 0.5f), shape)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) cs.primary else cs.onSurfaceVariant,
                    maxLines = 1,
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) cs.primary.copy(alpha = 0.18f) else cs.surfaceContainerLowest)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
