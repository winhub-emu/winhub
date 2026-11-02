package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** True while Deck is laid out for the portrait (touch-first) app orientation. */
internal val LocalDeckPortrait = compositionLocalOf { false }

/**
 * A left rail + content on landscape; in portrait the rail becomes a strip above the content.
 * [content] receives the modifier that gives it the remaining space in either direction.
 */
@Composable
internal fun DeckRailLayout(
    modifier: Modifier,
    rail: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (LocalDeckPortrait.current) {
        Column(modifier = modifier) {
            rail()
            content(Modifier.weight(1f).fillMaxWidth())
        }
    } else {
        Row(modifier = modifier) {
            rail()
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Darker -> lighter variant of the theme's primary: the stroke on selected items and the divider under tabs. */
@Composable
internal fun deckAccentBrush(alpha: Float = 1f): Brush {
    val base = Deck.primary
    return Brush.horizontalGradient(
        listOf(
            lerp(base, Color.Black, 0.45f).copy(alpha = alpha),
            lerp(base, Color.White, 0.45f).copy(alpha = alpha),
        ),
    )
}

/** Subtle gradient hairline between a tab bar and the screen below it. */
@Composable
internal fun PortraitTabDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.5.dp).background(deckAccentBrush(0.55f)))
}

/** Touch-only 48dp pill chips with the icon left of the label: equal width up to 3, sideways scroll beyond. */
@Composable
internal fun DeckPortraitChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val equal = labels.size <= 3
    val accent = deckAccentBrush()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().then(if (equal) Modifier else Modifier.horizontalScroll(rememberScrollState())),
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val shape = RoundedCornerShape(24.dp)
                val tint = if (on) Deck.primary else Deck.onSurfaceVariant
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .then(if (equal) Modifier.weight(1f) else Modifier)
                        .height(48.dp)
                        .clip(shape)
                        .background(if (on) Deck.primary.copy(alpha = 0.18f) else Deck.surface)
                        .then(if (on) Modifier.border(1.5.dp, accent, shape) else Modifier.border(1.dp, Deck.line, shape))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 16.dp),
                ) {
                    Icon(portraitTabIcon(label), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        color = tint,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PortraitTabDivider()
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * Portrait tab strip in bannercode's Deck style: one rounded glass card, scrolls sideways, the
 * selected tab is a filled primary pill with its icon + label. Scrolls the selection into view.
 */
@Composable
internal fun DeckPortraitTabStrip(
    labels: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(selected.coerceAtLeast(0)) }
    val cardShape = RoundedCornerShape(18.dp)
    val accent = deckAccentBrush()
    Column(modifier = modifier.fillMaxWidth()) {
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Deck.surface)
            .border(1.dp, Deck.line, cardShape),
    ) {
        itemsIndexed(labels) { i, label ->
            val on = i == selected
            val source = remember { MutableInteractionSource() }
            val shape = RoundedCornerShape(12.dp)
            val tint = if (on) Deck.onPrimary else Deck.onSurfaceVariant
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(shape)
                    .background(if (on) Deck.primary else Color.Transparent)
                    .then(if (on) Modifier.border(1.5.dp, accent, shape) else Modifier)
                    .clickable(interactionSource = source, indication = null) { if (!on) onSelect(i) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp),
            ) {
                icons.getOrNull(i)?.let { Icon(it, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
                Text(label, color = tint, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    PortraitTabDivider()
    }
}

/** Glyph for a portrait tab, by its label (same Material set the shortcut editor uses). */
private fun portraitTabIcon(label: String): ImageVector = when (label) {
    "Graphics" -> Icons.Filled.Memory
    "Performance" -> Icons.Filled.Speed
    "CPU", "System" -> Icons.Filled.DeveloperBoard
    "Input" -> Icons.Filled.SportsEsports
    "Environment" -> Icons.Filled.Extension
    "Wine" -> Icons.Filled.Widgets
    "Browse" -> Icons.Filled.Search
    "My Files" -> Icons.Filled.FolderOpen
    "Installed", "Containers" -> Icons.Filled.Inventory2
    "Interface" -> Icons.Filled.Apps
    "Appearance" -> Icons.Filled.Visibility
    "Cronos" -> Icons.Filled.Layers
    "Steam", "Epic", "GOG" -> Icons.Filled.Storefront
    "Custom", "Game Configurations" -> Icons.Filled.Tune
    "Feed" -> Icons.Filled.People
    "Profile" -> Icons.Filled.Person
    else -> Icons.Filled.Settings
}

/** [DeckPortraitTabStrip] with the icon picked from each label. */
@Composable
internal fun DeckPortraitTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    DeckPortraitTabStrip(labels, labels.map { portraitTabIcon(it) }, selected, onSelect, modifier)
}

/** Portrait-only preference: "strip" (icon tabs on top, default) or "pages" (full-page category list). */
internal const val PORTRAIT_TABS_PREF = "portrait_tabs_style"

@Composable
internal fun rememberPortraitStrip(): Boolean =
    PreferenceManager.getDefaultSharedPreferences(LocalContext.current).getString(PORTRAIT_TABS_PREF, "strip") != "pages"

/** Page title for portrait Deck screens that don't bring their own (same look as Contents' header). */
@Composable
internal fun PortraitScreenTitle(title: String, subtitle: String? = null, horizontal: androidx.compose.ui.unit.Dp = 16.dp) {
    Column(Modifier.fillMaxWidth().padding(start = horizontal, end = horizontal, top = 8.dp, bottom = 10.dp)) {
        Text(title, color = Deck.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Deck.onSurfaceVariant, fontSize = 13.sp)
    }
}
