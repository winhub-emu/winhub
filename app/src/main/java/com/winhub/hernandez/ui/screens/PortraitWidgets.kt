package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/** Equal-width 48dp touch chips for switching between a page's few categories. */
@Composable
internal fun DeckPortraitChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(24.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(if (on) Deck.primary.copy(alpha = 0.18f) else Deck.surface)
                    .border(1.5.dp, if (on) Deck.primary else Deck.line, shape)
                    .clickable { onSelect(i) },
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
