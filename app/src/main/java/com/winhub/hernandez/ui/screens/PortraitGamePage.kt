package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.GameFacts
import com.winhub.hernandez.store.SteamStoreSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Portrait game page (ported from bannercode's DeckGamePage banner layout): the art as a banner
 * fading into the page with a back button on it, then origin, title, container chip, stats,
 * Play / Game settings / Options, and the store blurb. Wired to [HubEntry] instead of Shortcut.
 */
@Composable
internal fun PortraitGamePage(
    entry: HubEntry,
    favourite: Boolean,
    containerName: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val origin = hubOriginLabel(entry)
    val hours = remember(entry.title) { hubPlaytimeLabel(context, entry.title) }
    val scores = rememberDeckScores(entry.title)
    var description by remember(entry.title) { mutableStateOf<String?>(null) }
    var bannerId by remember(entry.title) { mutableStateOf(entry.game?.appId?.takeIf { it > 0 }) }

    LaunchedEffect(entry.title) {
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                val appId = bannerId ?: SteamStoreSearch.searchByName(entry.title).firstOrNull()?.appId
                appId to appId?.let { SteamStoreSearch.fetchDetails(it)?.shortDescription }
            }.getOrNull()
        }
        bannerId = resolved?.first ?: bannerId
        description = resolved?.second
    }
    val art: Any? = bannerId?.let { "https://shared.steamstatic.com/store_item_assets/steam/apps/$it/header.jpg" } ?: entry.bitmap

    // Controller-first: A starts the game the moment the page opens.
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(entry.title) { runCatching { playFocus.requestFocus() } }

    val hue = remember(entry.title) { hueOf(entry.title) * 360f }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // Fade only the bottom edge into the page.
                    drawRect(
                        brush = Brush.verticalGradient(0.7f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.55f), Color.hsv((hue + 40f) % 360f, 0.6f, 0.25f)))),
        ) {
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val backSource = remember { MutableInteractionSource() }
            val backFocused by backSource.collectIsFocusedAsState()
            val backShape = RoundedCornerShape(16.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .size(52.dp)
                    .clip(backShape)
                    .background(Color(0x99000000))
                    .border(if (backFocused) 2.dp else 1.dp, if (backFocused) Deck.primary else Color(0x55FFFFFF), backShape)
                    .clickable(interactionSource = backSource, indication = null, onClick = onBack),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = Deck.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    origin.uppercase(),
                    color = Deck.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                entry.title,
                color = Deck.onBackground,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                containerName?.let { PortraitPill(it, Icons.Filled.Inventory2) }
                if (favourite) PortraitPill("Favourite", Icons.Filled.Star)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PortraitStat("Hours played", hours, Modifier.weight(1f))
                PortraitStat("Source", origin, Modifier.weight(1f))
            }
            DeckScoresRow(scores)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                PortraitPageButton("Play", Icons.Filled.PlayArrow, primary = true, onClick = onPlay, modifier = Modifier.weight(1f).focusRequester(playFocus))
                PortraitPageButton("Settings", Icons.Filled.Tune, primary = false, onClick = onSettings, modifier = Modifier.weight(1f))
                PortraitPageButton("Options", Icons.Filled.MoreVert, primary = false, onClick = onMenu, modifier = Modifier.weight(1f))
            }
            description?.takeIf { it.isNotBlank() }?.let { raw ->
                val shape = RoundedCornerShape(16.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Deck.surface)
                        .border(1.dp, Deck.line, shape)
                        .padding(16.dp),
                ) {
                    Text("ABOUT THIS GAME", color = Deck.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(GameFacts.cleanText(raw), color = Deck.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PortraitPill(text: String, icon: ImageVector) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(Deck.surface)
            .border(1.dp, Deck.line, shape)
            .padding(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Deck.onBackground, modifier = Modifier.size(18.dp))
        Text(text, color = Deck.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun PortraitStat(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Deck.surfaceVariant)
            .border(1.dp, Deck.line, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), color = Deck.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
        Text(value, color = Deck.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 56dp touch/controller button; a thicker primary border shows focus. */
@Composable
private fun PortraitPageButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(if (primary) Deck.primary else Deck.surface)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Color.White else if (primary) Deck.primary else Deck.line,
                shape,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        val tint = if (primary) Deck.onPrimary else Deck.onBackground
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
