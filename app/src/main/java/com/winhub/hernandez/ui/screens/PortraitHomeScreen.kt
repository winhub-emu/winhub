package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.AccountAvatar
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.store.rememberShownAvatar
import kotlinx.coroutines.delay
import java.util.Date

private val shelfLabels = listOf("All", "Recent", "Favourites", "Store games")

/**
 * Portrait Home: a greeting, a hero card for the game to pick up, shelves of covers, then every
 * game in a 3-column grid that starts with an Add tile. Tap-first, 48dp+ targets.
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
    onPlay: (Int) -> Unit,
    onAdd: () -> Unit,
    // Holding a cover opens that game's menu.
    onMenu: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    val context = LocalContext.current
    // Jump-back-in hero: the game played last, else the first one on this shelf.
    val lastPlayed = context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE).getString("last_played", null)
    val heroIndex = if (entries.isEmpty()) -1 else entries.indexOfFirst { it.game?.shortcutPath == lastPlayed }.coerceAtLeast(0)
    val recent = remember(entries) { entries.indices.take(8) }
    val favourites = remember(entries, favs) { entries.indices.filter { entries[it].game?.shortcutPath in favs } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PortraitHeader()
        }
        if (heroIndex >= 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PortraitContinueBanner(
                    entries[heroIndex],
                    onOpen = { onSelect(heroIndex) },
                    onPlay = { onPlay(heroIndex) },
                    onMenu = { onMenu(heroIndex) },
                )
            }
        }
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PortraitShelf("Recently played", recent, entries, favs, onSelect, onMenu)
            }
        }
        if (favourites.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PortraitShelf("Favourites", favourites, entries, favs, onSelect, onMenu)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("All games", color = Deck.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                shelfLabels.forEachIndexed { i, label ->
                    val on = i == shelf
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (on) Deck.primary.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { onShelf(i) }
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            label,
                            color = if (on) Deck.primary else Deck.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        if (loaded && entries.isEmpty() && shelf == 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(120.dp).clip(CircleShape).background(Deck.primary.copy(alpha = 0.16f)),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(84.dp).clip(CircleShape).background(Deck.surface),
                        ) {
                            Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = Deck.primary, modifier = Modifier.size(44.dp))
                        }
                    }
                    Text("Your shelf is empty", color = Deck.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Add a game from your files, or pick one from a store.",
                        color = Deck.onSurfaceVariant,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Deck.primary)
                            .clickable { onAdd() }
                            .padding(horizontal = 22.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Deck.onPrimary)
                        Spacer(Modifier.size(6.dp))
                        Text("Add a game", color = Deck.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (loaded && entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nothing on this shelf yet. Open a game's menu to favourite it.",
                    color = Deck.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        if (!(loaded && entries.isEmpty() && shelf == 0)) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .aspectRatio(0.75f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Deck.surface)
                        .clickable { onAdd() },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a game", tint = Deck.primary, modifier = Modifier.size(30.dp))
                    Text("Add a game", color = Deck.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        items(count = entries.size) { i ->
            PortraitCover(
                entry = entries[i],
                isSelected = false,
                favourite = entries[i].game?.shortcutPath in favs,
                onClick = { onSelect(i) },
                onLongClick = { onMenu(i) },
                radius = 18.dp,
            )
        }
    }
}

/** A titled sideways row of covers with the game name under each. */
@Composable
private fun PortraitShelf(
    title: String,
    indices: List<Int>,
    entries: List<HubEntry>,
    favs: Set<String>,
    onSelect: (Int) -> Unit,
    onMenu: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Deck.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(indices) { i ->
                Column(modifier = Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortraitCover(
                        entry = entries[i],
                        isSelected = false,
                        favourite = entries[i].game?.shortcutPath in favs,
                        onClick = { onSelect(i) },
                        onLongClick = { onMenu(i) },
                        radius = 20.dp,
                    )
                    Text(
                        entries[i].title,
                        color = Deck.onBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The hero: art fading into the title, origin and playtime, then Play, Details and a menu button. */
@Composable
private fun PortraitContinueBanner(entry: HubEntry, onOpen: () -> Unit, onPlay: () -> Unit, onMenu: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(entry.bitmap) { entry.bitmap?.asImageBitmap() }
    val hours = remember(entry.title) { hubPlaytimeLabel(context, entry.title) }
    val hue = remember(entry.title) { hueOf(entry.title) * 360f }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.62f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.26f))))
            .clickable { onOpen() },
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color(0xD10E0A14)),
            ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
        ) {
            Text("Pick up where you left off", color = Color(0xFFFFD9BD), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                entry.title,
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hubOriginLabel(entry) + " · " + hours,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Deck.primary)
                        .clickable { onPlay() }
                        .padding(horizontal = 22.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Deck.onPrimary)
                    Spacer(Modifier.size(6.dp))
                    Text("Play", color = Deck.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Deck.surfaceVariant)
                        .clickable { onOpen() }
                        .padding(horizontal = 20.dp),
                ) {
                    Text("Details", color = Deck.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onMenu() },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PortraitCover(
    entry: HubEntry,
    isSelected: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    radius: androidx.compose.ui.unit.Dp = 18.dp,
) {
    val haptics = LocalHapticFeedback.current
    val hue = remember(entry.title) { hueOf(entry.title) * 360f }
    val bitmap = remember(entry.bitmap) { entry.bitmap?.asImageBitmap() }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.58f, 0.62f), Color.hsv((hue + 40f) % 360f, 0.55f, 0.28f))))
            .then(if (isSelected) Modifier.border(2.dp, Deck.primary, shape) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = entry.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                entry.title.take(1).uppercase(),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 6.dp),
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
        if (entry.game != null) {
            PortraitSourceBadge(entry.game.store, Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
}

/** Store logo for a hub game's source; the My Files folder for custom (hand-added) games. */
@Composable
private fun PortraitSourceBadge(store: String?, modifier: Modifier) {
    val logo = when (store?.lowercase()) {
        "steam" -> R.drawable.store_logo_steam
        "epic" -> R.drawable.store_logo_epic
        "gog" -> R.drawable.store_logo_gog
        "amazon" -> R.drawable.store_logo_amazon
        "xbox" -> R.drawable.store_logo_xbox
        else -> null
    }
    Box(
        modifier = modifier.size(24.dp).clip(CircleShape).background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = store,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(Icons.Filled.FolderOpen, contentDescription = "Custom game", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

/** A greeting on the left; on the right the Social Hub account's avatar. */
@Composable
private fun PortraitHeader() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SocialHubStore.init(context) }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(greeting, color = Deck.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Ready when you are", color = Deck.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        val gh by SocialHubStore.account.collectAsState()
        AccountAvatar(
            avatarUrl = gh?.let { rememberShownAvatar(it.login, it.avatarUrl) },
            size = 44.dp,
            fallbackTint = Deck.onSurfaceVariant,
        )
    }
}
