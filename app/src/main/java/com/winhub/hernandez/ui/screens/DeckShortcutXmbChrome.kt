package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.deck.DeckInk
import com.winhub.hernandez.ui.deck.XmbGlassBackdrop
import com.winhub.hernandez.ui.deck.XmbGlassTheme
import com.winhub.hernandez.ui.deck.XmbPageArtTheme
import com.winhub.hernandez.ui.deck.deckFocusRingInk

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
 * The page's rail: a fixed block (back arrow, kicker and the game's name, which wraps to two lines at most) over the small tabs,
 * which are the app's one tab rail ([DeckEditorCategoryRail]): Up / Down shows each tab, A / tap too.
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
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
        ) {
            val backShape = RoundedCornerShape(14.dp)
            DeckInk { item, ink ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .deckFocusRingInk(backShape, item, scaleTo = 1f)
                        .clip(backShape)
                        .clickable(onClick = onBack),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ink.onSurface, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = kicker.uppercase(),
                    color = cs.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    letterSpacing = 1.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = title,
                    color = cs.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DeckEditorCategoryRail(
            labels = labels,
            icons = icons,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 16.dp),
        )
    }
}
