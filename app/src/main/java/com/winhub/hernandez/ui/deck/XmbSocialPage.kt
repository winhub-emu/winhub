package com.winhub.hernandez.ui.deck

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.winhub.hernandez.ui.screens.xmbClick

/** The card under the cursor is drawn this much larger, as on the Stores tab. */
private const val XMB_SOCIAL_FOCUS_SCALE = 1.04f

private class XmbSocialCard(val title: String, val sub: String, val icon: ImageVector, val colors: List<Color>)

private val XMB_SOCIAL_CARDS = listOf(
    XmbSocialCard("Feed", "What the community is playing", Icons.Filled.DynamicFeed, listOf(Color(0xFF134E4A), Color(0xFF0A2B29))),
    XmbSocialCard("Configs", "Shared settings for your games", Icons.Filled.Tune, listOf(Color(0xFF1E3A5F), Color(0xFF0F1F36))),
    XmbSocialCard("Profile", "Your page and your uploads", Icons.Filled.Person, listOf(Color(0xFF4A2A5C), Color(0xFF26142F))),
)

/**
 * The Social tab of the XMB Home's tabs page: the Social Hub's three parts as cards, in the look of the Stores tab. Left / Right move
 * between them, A (or a tap) opens the Social Hub on that card's tab, Up goes to the pill bar.
 */
@Composable
internal fun XmbSocialPage(focus: XmbTabsPageFocus, onOpen: (Int) -> Unit) {
    var sel by remember { mutableIntStateOf(0) }
    XmbTabsPageKeys(focus) { e ->
        when (e.key) {
            Key.DirectionLeft -> { sel = (sel - 1).coerceAtLeast(0); true }
            Key.DirectionRight -> { sel = (sel + 1).coerceAtMost(XMB_SOCIAL_CARDS.lastIndex); true }
            Key.DirectionUp -> { focus.toBar(); true }
            Key.DirectionDown -> true
            // A held key must not open the hub twice.
            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (e.nativeKeyEvent.repeatCount == 0) onOpen(sel)
                true
            }
            else -> false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(focus.contentPadding), contentAlignment = Alignment.Center) {
        val side = minOf(maxHeight.value * 0.8f, (maxWidth.value - 2 * 16f) / XMB_SOCIAL_CARDS.size).coerceAtLeast(96f)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            XMB_SOCIAL_CARDS.forEachIndexed { i, card ->
                XmbSocialCardView(card, side, focus.contentFocused && i == sel) { sel = i; onOpen(i) }
            }
        }
    }
}

@Composable
private fun XmbSocialCardView(card: XmbSocialCard, side: Float, focused: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val src = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) XMB_SOCIAL_FOCUS_SCALE else 1f, tween(140), label = "xmbSocialCard")
    Box(
        Modifier
            .size(side.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.verticalGradient(card.colors))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) XMB_FOCUS_OUTLINE else Color.White.copy(alpha = 0.10f), shape)
            .xmbClick(src, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
            Icon(card.icon, contentDescription = card.title, tint = Color.White, modifier = Modifier.size((side * 0.34f).dp))
            Spacer(Modifier.height((side * 0.06f).dp))
            Text(
                card.title, color = Color.White, fontSize = (side * 0.115f).coerceIn(11f, 20f).sp, fontWeight = FontWeight.Bold,
                maxLines = 1, textAlign = TextAlign.Center,
            )
            Text(
                card.sub, color = Color.White.copy(alpha = 0.72f), fontSize = (side * 0.085f).coerceIn(9f, 14f).sp,
                maxLines = 2, textAlign = TextAlign.Center,
            )
        }
    }
}
