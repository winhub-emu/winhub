package com.winhub.hernandez.ui.deck

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.winhub.hernandez.ui.screens.GlassLook

// Small pieces of the XMB interface that the store screens (StoreXmb.kt) and the XMB kit lean on.

/** Settings > Store style: the Deck store pages (default) or the XMB ones. Read straight from the preferences, so a store activity can ask first thing. */
internal object StoreStyle {
    const val KEY = "store_style"
    const val DECK = "deck"
    const val XMB = "xmb"

    /** The XMB store pages: chosen, or implied by the Glass look (the stores are on the glass with it). */
    fun isXmb(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(KEY, DECK) == XMB || GlassLook.read(prefs) == GlassLook.GLASS
    }
}

/**
 * The XMB pages are light text on dark glass. A bright preset keeps its accent but sits on charcoal neutrals instead (its
 * light background would make a light glass under the light text); a dark preset is used as it is. Off: [content] as is.
 */
@Composable
internal fun XmbDarkScheme(enabled: Boolean, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (!enabled || xmbLuminance(cs.background.red, cs.background.green, cs.background.blue) < 0.18) {
        content()
        return
    }
    // A very dark accent would vanish on charcoal: lift it a little.
    val accent = if (xmbLuminance(cs.primary.red, cs.primary.green, cs.primary.blue) < 0.12) lerp(cs.primary, Color.White, 0.4f) else cs.primary
    val onAccent = if (xmbLuminance(accent.red, accent.green, accent.blue) > 0.4) Color(0xFF101114) else Color.White
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = cs.secondary,
            background = Color(0xFF16181C),
            onBackground = Color(0xFFE8EAED),
            surface = Color(0xFF1E2127),
            onSurface = Color(0xFFE8EAED),
            surfaceVariant = Color(0xFF2A2D33),
            onSurfaceVariant = Color(0xFFB4B8BF),
            outline = Color(0xFF3A3E45),
            error = Color(0xFFFF8A80),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

internal fun deckShape(radius: Dp): CornerBasedShape = RoundedCornerShape(radius)

/**
 * The density the Deck style draws at: the Deck screen's own when there is one, else the current density at [DECK_UI_SCALE].
 * The XMB "B Back" hint is drawn at this density on every page, so a store screen (which runs at the app's own, larger
 * density) shows it at the size and place Home does.
 */
@Composable
internal fun rememberXmbHintDensity(): Density {
    val outer = LocalDensity.current
    return LocalDeckDensity.current ?: Density(outer.density * DECK_UI_SCALE, outer.fontScale)
}

/** Card fill: the XMB glass page's own, else the theme surface at 90%. */
@Composable
internal fun deckCardFill(): Color = xmbPageFrost()?.card ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)

/**
 * A focusable element whose text and icons turn accent-coloured while the gliding outline is on it (XMB). [content] gets the
 * element's highlight [item] (hand it to [deckFocusRingInk]) and the colour scheme AS INKED: read colours from that `cs`, not
 * from one taken outside, or the ink never reaches them. Outside XMB it is just [content].
 */
@Composable
internal fun DeckInk(content: @Composable (item: XmbHighlightItem, cs: ColorScheme) -> Unit) {
    val item = rememberXmbHighlightItem()
    XmbInk(item) { content(item, MaterialTheme.colorScheme) }
}

/**
 * [deckFocusRing] for an element wrapped in [XmbInk]: under a highlight host it registers [item] as the stop (so the element's
 * text turns accent); without one it is the ordinary ring.
 */
@Composable
internal fun Modifier.deckFocusRingInk(
    shape: Shape,
    item: XmbHighlightItem,
    scaleTo: Float = 1.05f,
    onFocused: (() -> Unit)? = null,
): Modifier =
    if (xmbHighlightHostOrNull() != null) this.xmbHighlight(shape, item, onFocused = onFocused)
    else this.deckFocusRing(shape, scaleTo, onFocused)

/** A pad button's glyph: a round A / B / X / Y, a bumper, or plain text. */
@Composable
internal fun DeckGlyph(text: String, kind: GlyphKind, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val round = kind == GlyphKind.A || kind == GlyphKind.B || kind == GlyphKind.X || kind == GlyphKind.Y
    val shape = when {
        round -> CircleShape
        kind == GlyphKind.BUMPER -> RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 5.dp, bottomEnd = 5.dp)
        else -> deckShape(7.dp)
    }
    val (fill, ink) = when (kind) {
        GlyphKind.A -> cs.primary to cs.onPrimary
        GlyphKind.B -> cs.error to cs.onError
        GlyphKind.X, GlyphKind.Y -> cs.surfaceVariant to cs.onSurface
        GlyphKind.BUMPER -> cs.surfaceVariant.copy(alpha = 0.7f) to cs.onSurfaceVariant
        GlyphKind.PLAIN -> Color.Transparent to cs.onSurfaceVariant
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = if (kind == GlyphKind.BUMPER) 30.dp else 22.dp, minHeight = 22.dp)
            .background(fill, shape)
            .then(
                if (kind != GlyphKind.A && kind != GlyphKind.B) Modifier.border(1.5.dp, cs.onSurfaceVariant.copy(alpha = 0.6f), shape)
                else Modifier,
            )
            .padding(horizontal = 5.dp),
    ) {
        Text(
            text = text,
            color = ink,
            fontFamily = NunitoSansFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5.sp,
            maxLines = 1,
        )
    }
}

/**
 * A floating hint with no box around it: the button's [glyph] (none = not drawn, for a touch-only screen), an optional [icon]
 * and a [label], muted and tappable. The one look behind the XMB hints.
 */
@Composable
internal fun DeckFloatingHint(
    glyph: String?,
    kind: GlyphKind,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = label,
) {
    val ss = rememberScreenScale()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ss.dp(6f)),
        modifier = modifier
            .clip(deckShape(10.dp))
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = ss.dp(8f), vertical = ss.dp(4f))
            .alpha(0.75f),
    ) {
        if (glyph != null) DeckGlyph(glyph, kind)
        if (icon != null) Icon(icon, contentDescription = description, tint = Color(0xFFDDDDDD), modifier = Modifier.size(ss.dp(18f)))
        if (label != null) Text(label, color = Color(0xFFDDDDDD), fontSize = ss.sp(14f), fontWeight = FontWeight.Light, maxLines = 1)
    }
}
