package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val shelfLabels = listOf("All", "Recent", "Favourites", "Store games")

/**
 * Portrait Home: shelf chips, a hero card for the selected game (Play + menu), then a 3-column
 * cover grid that starts with an Add-game tile. Everything is tap-first with 48dp+ targets.
 */
@Composable
internal fun PortraitHome(
    entries: List<HubEntry>,
    loaded: Boolean,
    selected: Int,
    favs: Set<String>,
    shelf: Int,
    onShelf: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    val current = entries.getOrNull(selected)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("WinHub", color = Deck.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                shelfLabels.forEachIndexed { i, label ->
                    val on = i == shelf
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (on) Deck.primary.copy(alpha = 0.18f) else Deck.surface)
                            .border(1.5.dp, if (on) Deck.primary else Deck.line, RoundedCornerShape(24.dp))
                            .clickable { onShelf(i) }
                            .padding(horizontal = 18.dp),
                    ) {
                        Text(
                            label,
                            color = if (on) Deck.primary else Deck.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
        if (current != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PortraitHero(current, current.game?.shortcutPath in favs, onPlay, onMenu)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                if (entries.isEmpty()) "Library" else "Library · ${entries.size}",
                color = Deck.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .aspectRatio(0.66f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Deck.surface)
                    .border(1.5.dp, Deck.line, RoundedCornerShape(14.dp))
                    .clickable { onAdd() },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add game", tint = Deck.primary, modifier = Modifier.size(32.dp))
                Text("Add game", color = Deck.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (loaded && entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (shelf == 0) "No games yet. Tap Add game to build your library."
                    else "Nothing on this shelf yet. Open a game's menu to favourite it.",
                    color = Deck.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        items(count = entries.size) { i ->
            PortraitCover(
                entry = entries[i],
                isSelected = i == selected,
                favourite = entries[i].game?.shortcutPath in favs,
                onClick = { onSelect(i) },
            )
        }
    }
}

@Composable
private fun PortraitHero(entry: HubEntry, favourite: Boolean, onPlay: () -> Unit, onMenu: () -> Unit) {
    val hue = remember(entry.title) { hueOf(entry.title) * 360f }
    val bitmap = remember(entry.bitmap) { entry.bitmap?.asImageBitmap() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.25f)))),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(entry.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (entry.game?.store ?: "Custom") + if (favourite) " · Favourite" else "",
                    color = Color(0xCCFFFFFF),
                    fontSize = 14.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Deck.primary)
                        .clickable { onPlay() },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Deck.onPrimary)
                    Spacer(Modifier.size(6.dp))
                    Text("Play", color = Deck.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x66000000))
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(14.dp))
                        .clickable { onMenu() },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Game menu", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PortraitCover(entry: HubEntry, isSelected: Boolean, favourite: Boolean, onClick: () -> Unit) {
    val hue = remember(entry.title) { hueOf(entry.title) * 360f }
    val bitmap = remember(entry.bitmap) { entry.bitmap?.asImageBitmap() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .aspectRatio(0.66f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.25f))))
            .then(if (isSelected) Modifier.border(2.dp, Deck.primary, shape) else Modifier)
            .clickable { onClick() },
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = entry.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                entry.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
        if (favourite) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Favourite",
                tint = Color(0xFFFFC94D),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
            )
        }
    }
}
