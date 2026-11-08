package com.winhub.hernandez.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Shared visual pieces for the Deck Stores tab: store rail, header, pill tabs, skeletons, empty
// states, the controller hint bar and the focus-scale used by the poster cards.

/** Cards grow a touch while the D-pad is on them. */
@Composable
internal fun Modifier.deckFocusScale(interaction: InteractionSource, scale: Float = 1.06f): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val s by animateFloatAsState(if (focused) scale else 1f, tween(160, easing = DeckEase), label = "focusScale")
    return graphicsLayer { scaleX = s; scaleY = s }
}

/** A looping shimmer placeholder shown while a pane loads. */
@Composable
internal fun DeckShimmer(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmerPhase",
    )
    val sheen = Deck.onBackground.copy(alpha = 0.07f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Deck.surface)
            .drawBehind {
                val start = (phase * 2f - 1f) * size.width
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, sheen, Color.Transparent),
                        startX = start,
                        endX = start + size.width,
                    ),
                )
            },
    )
}

/** Skeleton for the Library grid. */
@Composable
internal fun DeckLibrarySkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(5) {
            DeckShimmer(
                Modifier.weight(1f).height(160.dp),
                RoundedCornerShape(10.dp),
            )
        }
    }
}

/** Skeleton for the storefront: a hero block and a row of cards. */
@Composable
internal fun DeckStoreSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DeckShimmer(Modifier.fillMaxWidth().height(200.dp), RoundedCornerShape(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(5) {
                DeckShimmer(Modifier.weight(1f).height(150.dp), RoundedCornerShape(10.dp))
            }
        }
    }
}

/** Centered icon + title + body for empty or failed panes. */
@Composable
internal fun DeckEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Deck.surfaceVariant),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = Deck.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Text(title, color = Deck.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(body, color = Deck.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Rounded brand tile: the store's colour with its initial. */
@Composable
internal fun DeckStoreLogo(initial: String, fill: Color, ink: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(fill),
    ) {
        Text(initial, color = ink, fontSize = (size.value * 0.46f).sp, fontWeight = FontWeight.Black)
    }
}

/**
 * The store picker: one big brand-coloured tile per store (Steam blue, Epic white, GOG purple,
 * Amazon orange) with its name and sign-in state. The selected tile is taller and ringed; landing
 * on a tile selects it, A opens it.
 */
@Composable
internal fun DeckStorePicker(
    names: List<String>,
    accents: List<Color>,
    logos: List<Int>,
    brands: List<List<Color>>,
    signedIn: List<Boolean>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember(names.size) { List(names.size) { FocusRequester() } }
    // Focus follows the selection, so L2/R2 and returning from a store land on the right tile.
    LaunchedEffect(selected) {
        delay(50)
        runCatching { requesters[selected].requestFocus() }
    }
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { i, name ->
            val isSel = i == selected
            // The logos sit on the store's brand colours, so the text is always light.
            val ink = Color.White
            val height by animateDpAsState(if (isSel) 200.dp else 148.dp, tween(220, easing = DeckEase), label = "storeTileH")
            val brand = brands[i]
            val shape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(shape)
                    .border(3.dp, if (isSel) Deck.onBackground else Color.Transparent, shape)
                    .focusRequester(requesters[i])
                    .onFocusChanged { if (it.isFocused && !isSel) onSelect(i) }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(i) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .alpha(if (isSel) 1f else 0.82f)
                        .background(Brush.verticalGradient(brand)),
                ) {
                    val logo = rememberDeckLogo(logos[i])
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(0.86f),
                            )
                        } else {
                            Text(name.take(1).uppercase(), color = ink, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(name, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ink.copy(alpha = if (signedIn[i]) 1f else 0.4f)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (signedIn[i]) "Logged in" else "Not logged in",
                            color = ink.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Compact store identity (logo, name, sign-in state) at the top of a store's side tabs. */
@Composable
internal fun DeckStoreHeader(
    name: String,
    fill: Color,
    ink: Color,
    accent: Color,
    signedIn: Boolean,
    modifier: Modifier = Modifier,
    logo: Int? = null,
    brand: List<Color>? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.24f), Color.Transparent)))
            .border(1.dp, Deck.line, shape)
            .padding(10.dp),
    ) {
        val logoBmp = logo?.let { rememberDeckLogo(it) }
        if (logoBmp != null && brand != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(brand)),
            ) {
                Image(
                    bitmap = logoBmp,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(0.72f),
                )
            }
        } else {
            DeckStoreLogo(name.take(1).uppercase(), fill, ink, 36.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(name, color = Deck.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (signedIn) accent else Deck.onSurfaceVariant.copy(alpha = 0.5f)))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (signedIn) "Logged in" else "Not logged in",
                    color = Deck.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A store's page tabs down the left edge, each with an icon. Landing on one selects it; the screen
 * steps them with L2/R2. [selectedFocus] marks the selected tab and takes focus on first show.
 */
@Composable
internal fun DeckStoreSideTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { selectedFocus.requestFocus() }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selected
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val bg by animateColorAsState(
                if (isSel) Deck.primary.copy(alpha = 0.16f) else Color.Transparent,
                tween(200, easing = DeckEase),
                label = "sideTabBg",
            )
            val shape = RoundedCornerShape(12.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(shape)
                    .background(bg)
                    .border(
                        // Selection alone draws the ring: a focus left on the old tab after L2/R2 or a
                        // touch must not leave a second ring behind.
                        width = if (isSel) 2.dp else 1.dp,
                        color = if (isSel) Deck.primary else Deck.line,
                        shape = shape,
                    )
                    .then(if (isSel) Modifier.focusRequester(selectedFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused && !isSel) onSelect(i) }
                    .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    deckStoreTabIcon(label),
                    contentDescription = null,
                    tint = if (isSel) Deck.primary else Deck.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    color = if (isSel) Deck.primary else Deck.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun deckStoreTabIcon(label: String): ImageVector = when (label) {
    "Store" -> Icons.Filled.Storefront
    "Library" -> Icons.Filled.VideogameAsset
    "Settings" -> Icons.Filled.Settings
    "Friends" -> Icons.Filled.People
    else -> Icons.Filled.Person
}

/** Translucent footer listing the controller buttons that work right now. */
@Composable
internal fun DeckHintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Deck.surface.copy(alpha = 0.75f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        hints.forEach { (button, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Deck.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(button, color = Deck.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(6.dp))
                Text(action, color = Deck.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
