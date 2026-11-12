package com.winhub.hernandez.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.LocalStoreXmb
import com.winhub.hernandez.ui.components.Button
import com.winhub.hernandez.ui.components.OutlinedButton
import com.winhub.hernandez.ui.deck.xmbCardFill
import com.winhub.hernandez.ui.deck.xmbCardLine
import com.winhub.hernandez.ui.deck.xmbGlassCard
import com.winhub.hernandez.ui.screens.Deck
import com.winhub.hernandez.ui.screens.DeckButton
import com.winhub.hernandez.ui.screens.DeckEmptyState
import com.winhub.hernandez.ui.screens.LocalDeckMode

// The Social Hub panes in the Deck store-page look (surface cards on a hairline, Deck buttons, the empty-state
// block). Outside Deck Mode (the classic app, the in-game drawer) each piece falls back to the Material look it had.

/** A row / card surface: Deck surface on a hairline in Deck Mode, the tinted Material card elsewhere. */
@Composable
internal fun Modifier.socialCard(highlighted: Boolean = false): Modifier {
    val deck = LocalDeckMode.current
    val cs = MaterialTheme.colorScheme
    // The XMB store style (its own Activity): glass cards over the charcoal glass, like the stores' cards.
    if (LocalStoreXmb.current) {
        val glassShape = RoundedCornerShape(12.dp)
        return this
            .xmbGlassCard(glassShape, xmbCardFill(cs.surfaceVariant))
            .border(
                if (highlighted) 2.dp else 1.dp,
                if (highlighted) cs.primary else xmbCardLine(cs.outline),
                glassShape,
            )
    }
    val shape = RoundedCornerShape(if (deck) 16.dp else 12.dp)
    val fill = if (deck) Deck.surface else cs.surfaceVariant.copy(alpha = 0.45f)
    val line = when {
        highlighted -> if (deck) Deck.primary else cs.primary
        deck -> Deck.line
        else -> cs.outline.copy(alpha = 0.45f)
    }
    return this.clip(shape).background(fill).border(if (highlighted) 2.dp else 1.dp, line, shape)
}

/** A button: [DeckButton] in Deck Mode, the Material one elsewhere. */
@Composable
internal fun SocialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    if (LocalDeckMode.current) {
        DeckButton(text, primary, modifier, enabled, icon, onClick)
        return
    }
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 14.sp)
    }
    val m = modifier.heightIn(min = 48.dp)
    if (primary) Button(onClick = onClick, enabled = enabled, modifier = m, content = content)
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = m, content = content)
}

/** Centered title + body for an empty or failed pane, with an optional action under it. */
@Composable
internal fun SocialEmpty(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (LocalDeckMode.current) {
            DeckEmptyState(title, body)
        } else {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

/** The small uppercase eyebrow the store panes put over their content ("LIBRARY  12 games"). */
@Composable
internal fun SocialSectionLabel(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            color = Deck.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
        )
        if (detail != null) {
            Spacer(Modifier.width(8.dp))
            Text(detail, color = Deck.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/** The sign-in hero: the Social name big at the foot of a dark card with a soft glow, [content] beneath it. */
@Composable
internal fun SocialDeckHero(title: String, body: String?, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Deck.background, Deck.surface)))
            .border(1.dp, Deck.line, shape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF2DD4BF).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.16f, size.height * 0.90f),
                        radius = size.height * 0.80f,
                    ),
                )
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 32.dp, end = 20.dp),
        ) {
            Text(title, color = Deck.onBackground, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, maxLines = 2)
            if (body != null) Text(body, color = Deck.onSurfaceVariant, fontSize = 14.sp, maxLines = 3)
            content()
        }
    }
}
