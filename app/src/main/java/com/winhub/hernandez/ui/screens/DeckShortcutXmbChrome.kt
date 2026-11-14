package com.winhub.hernandez.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.deck.XmbGlassBackdrop
import com.winhub.hernandez.ui.deck.XmbGlassTheme
import com.winhub.hernandez.ui.deck.XmbPageArtTheme

/**
 * The look of a game's settings page opened from the XMB Home: the glass on the game's own blurred art, under the page. The page's
 * panels turn to glass ([DeckGroup]) and its secondary text is lifted for the lighter ground. Fills its parent.
 */
@Composable
internal fun XmbSettingsPageHost(shortcut: Shortcut, content: @Composable () -> Unit) {
    XmbGlassTheme(enabled = true) {
        XmbPageArtTheme(enabled = true) {
            Box(Modifier.fillMaxSize()) {
                XmbGlassBackdrop(Modifier.matchParentSize())
                XmbGamePageArt(shortcut, Modifier.matchParentSize())
                content()
            }
        }
    }
}

/** A Deck page on the glass (the Glass look): the glass behind it and the glass theme in it. Fills its parent. */
@Composable
internal fun XmbGlassPage(content: @Composable () -> Unit) {
    XmbGlassTheme(enabled = true) {
        Box(Modifier.fillMaxSize()) {
            XmbGlassBackdrop(Modifier.matchParentSize())
            content()
        }
    }
}

/**
 * The page's rail: a fixed block (back arrow, kicker and the game's name, which wraps to two lines at most) over the small tabs.
 * A tab is one 40 dp line (icon and name) with an accent bar and tint while it is the shown one; A / tap shows it.
 */
@Composable
internal fun XmbSettingsRail(
    kicker: String,
    title: String,
    labels: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
        ) {
            val backShape = RoundedCornerShape(14.dp)
            val backInteraction = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .deckRing(backInteraction, backShape)
                    .clip(backShape)
                    .clickable(interactionSource = backInteraction, indication = null, onClick = onBack),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = cs.onSurface, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kicker.uppercase(),
                    color = cs.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = title,
                    color = cs.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            labels.forEachIndexed { i, label ->
                val isSel = i == selected
                val shape = RoundedCornerShape(12.dp)
                val interaction = remember { MutableInteractionSource() }
                val bg by animateColorAsState(
                    targetValue = if (isSel) cs.primary.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "xmbSettingsTabBg",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .deckRing(interaction, shape)
                        .clip(shape)
                        .background(bg)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(i) }
                        .drawBehind {
                            if (isSel) {
                                val w = 4.dp.toPx()
                                drawRoundRect(
                                    color = cs.primary,
                                    topLeft = Offset(0f, size.height * 0.22f),
                                    size = Size(w, size.height * 0.56f),
                                    cornerRadius = CornerRadius(w / 2, w / 2),
                                )
                            }
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Icon(
                        icons[i],
                        contentDescription = null,
                        tint = if (isSel) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        color = if (isSel) cs.onSurface else cs.onSurfaceVariant,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
