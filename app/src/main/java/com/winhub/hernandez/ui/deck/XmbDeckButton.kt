package com.winhub.hernandez.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.ui.screens.Deck

/** The XMB friends dialogs' button: rounded, bold label, optional leading icon and trailing controller glyph; the pad focuses and presses it. */
@Composable
internal fun XmbDeckButton(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    big: Boolean = false,
    glyph: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val ink = if (primary) Deck.onPrimary else Deck.onBackground
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(shape)
            .background(if (primary) Deck.primary else Deck.surfaceVariant)
            .then(if (primary) Modifier else Modifier.border(1.dp, Deck.line, shape))
            .clickable(onClick = onClick)
            .heightIn(min = if (big) 48.dp else 40.dp)
            .padding(horizontal = if (big) 22.dp else 15.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(if (big) 22.dp else 18.dp))
        Text(
            text = label,
            color = ink,
            fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = if (big) 16.sp else 13.5.sp,
            maxLines = 1,
        )
        if (glyph != null) Box(
            Modifier.size(18.dp).clip(CircleShape).background(ink.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
