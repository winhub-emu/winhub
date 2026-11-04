package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.ui.AccountAvatar
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.store.rememberShownAvatar
import kotlinx.coroutines.delay
import java.util.Date

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
    onPlay: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    val context = LocalContext.current
    // Jump-back-in banner: the game played last, if it is on the current shelf.
    val lastPlayed = context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE).getString("last_played", null)
    val continueIndex = if (lastPlayed == null) -1 else entries.indexOfFirst { it.game?.shortcutPath == lastPlayed }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            PortraitHeader()
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
                            .then(
                                if (on) Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Deck.primary.copy(alpha = 0.18f))
                                    .border(1.5.dp, Deck.primary, RoundedCornerShape(24.dp))
                                else Modifier.glassCard(RoundedCornerShape(24.dp))
                            )
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
        if (continueIndex >= 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PortraitContinueBanner(entries[continueIndex], onOpen = { onSelect(continueIndex) }, onPlay = { onPlay(continueIndex) })
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
                    .glassCard(RoundedCornerShape(14.dp))
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

/** "Continue playing" banner (bannercode's hero, compact): art fading down into the title, tap = game page, Play = go. */
@Composable
private fun PortraitContinueBanner(entry: HubEntry, onOpen: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(entry.bitmap) { entry.bitmap?.asImageBitmap() }
    val hours = remember(entry.title) { hubPlaytimeLabel(context, entry.title) }
    val shape = RoundedCornerShape(24.dp)
    // A slow float, drawn in the layer so it never recomposes or re-lays-out the banner.
    val bob by rememberInfiniteTransition(label = "bannerBob").animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bannerBobY",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .graphicsLayer { translationY = bob.dp.toPx() }
            .glassCard(shape)
            .clickable { onOpen() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Deck.primary.copy(alpha = 0.35f), Color.Transparent))),
        )
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
        ) {
            Text("CONTINUE PLAYING", color = Deck.primary, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            Text(
                entry.title,
                color = Deck.onBackground,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Deck.primary)
                        .clickable { onPlay() }
                        .padding(horizontal = 22.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Deck.onPrimary)
                    Spacer(Modifier.size(6.dp))
                    Text("Play", color = Deck.onPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    hubOriginLabel(entry) + " · " + hours,
                    color = Deck.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
            .border(if (isSelected) 2.5.dp else 1.5.dp, deckAccentBrush(), shape)
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

/** WinHub wordmark on the left; on the right the clock and the Social Hub account's avatar. */
@Composable
private fun PortraitHeader() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        SocialHubStore.init(context)
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(Date(now)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("WinHub", color = Deck.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(time, color = Deck.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        val gh by SocialHubStore.account.collectAsState()
        AccountAvatar(
            avatarUrl = gh?.let { rememberShownAvatar(it.login, it.avatarUrl) },
            size = 36.dp,
            fallbackTint = Deck.onSurfaceVariant,
        )
    }
}
