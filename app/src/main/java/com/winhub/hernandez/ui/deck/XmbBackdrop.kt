package com.winhub.hernandez.ui.deck

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// The XMB interface's backgrounds.
//
// Home and the All Games page are not tinted by the preset at all: black under the focused game's blurred art (see
// ShortcutsXmbView). The theme's `background` there is the flat wash: the theme colour as an even, soft tint over the preset's
// own background, composited ahead of time into one opaque colour ([XmbWash.flat]) so it looks the same on every Android
// version and a part painting it twice doesn't get a second layer of tint.
//
// Every other XMB page (the Deck tabs and their sub-pages, the store screens, the downloads, the search and the dialogs) sits
// on neutral charcoal glass: a near-black base with one faint lighter sweep toward the top-left, and the cards on top as
// frosted panels (a slightly lighter translucent fill, a thin light border and a soft sheen at the top that fades out). The
// preset only picks the tone: a dark, muted shade of its own colour family (grey for the neutral presets); its accent colour
// stays on the focus rings, selection and toggles.

/**
 * Most of the accent that is mixed into the background; the wash backs off from this when text would lose contrast. At 0.20
 * every built-in preset keeps its secondary text above 4.5:1 with some room (0.22 is the first to tip Phosphor and Frost), so
 * they all get the same strength and only an unusual custom accent is held back.
 */
internal const val XMB_WASH_MAX_ALPHA = 0.20f

/** The faint white lift that makes the tint read as frosted glass rather than a coloured fill (dark themes only). */
internal const val XMB_WASH_VEIL_ALPHA = 0.04f

/** WCAG AA for body text; the secondary text colour is held to it too. */
internal const val XMB_GLASS_MIN_CONTRAST = 4.5

private fun channelToLinear(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
}

/** WCAG relative luminance of an sRGB colour given as 0..1 channels. */
internal fun xmbLuminance(r: Float, g: Float, b: Float): Double =
    0.2126 * channelToLinear(r) + 0.7152 * channelToLinear(g) + 0.0722 * channelToLinear(b)

/** WCAG contrast ratio (1..21) between two relative luminances, in either order. */
internal fun xmbContrast(l1: Double, l2: Double): Double {
    val hi = maxOf(l1, l2)
    val lo = minOf(l1, l2)
    return (hi + 0.05) / (lo + 0.05)
}

// A Color keeps 8 bits per channel, so the contrast is judged on the colour that is really drawn: a value that passes by a
// hair before the rounding could land just under the minimum after it.
private fun to8Bit(c: Float): Float = (c.coerceIn(0f, 1f) * 255f).roundToInt() / 255f

/**
 * The opaque colour (r, g, b as 0..1, rounded to the 8 bits a Color keeps) that [accent] at [alpha] makes over [base], then
 * the white veil of [veil] over that.
 */
internal fun xmbWashedRgb(base: Color, accent: Color, alpha: Float, veil: Float): FloatArray {
    val r = base.red * (1f - alpha) + accent.red * alpha
    val g = base.green * (1f - alpha) + accent.green * alpha
    val b = base.blue * (1f - alpha) + accent.blue * alpha
    return floatArrayOf(to8Bit(r * (1f - veil) + veil), to8Bit(g * (1f - veil) + veil), to8Bit(b * (1f - veil) + veil))
}

private fun isDarkBase(background: Color): Boolean = xmbLuminance(background.red, background.green, background.blue) < 0.18

/** The veil strength for a background: only dark themes get the white lift, a light one would just go grey. */
internal fun xmbWashVeil(background: Color): Float = if (isDarkBase(background)) XMB_WASH_VEIL_ALPHA else 0f

/**
 * How much of [accent] to mix into [background]: [XMB_WASH_MAX_ALPHA] unless that would drop the contrast of any of [texts]
 * on the washed colour below [XMB_GLASS_MIN_CONTRAST], then the largest value that keeps it (down to no tint at all). The
 * presets all pass at the maximum; this keeps a very light custom accent, or a light scheme, readable too.
 */
internal fun xmbWashAlpha(background: Color, accent: Color, texts: List<Color>): Float {
    val veil = xmbWashVeil(background)
    var steps = (XMB_WASH_MAX_ALPHA * 100f).roundToInt()
    while (steps > 0) {
        val a = steps / 100f
        val rgb = xmbWashedRgb(background, accent, a, veil)
        val lum = xmbLuminance(rgb[0], rgb[1], rgb[2])
        if (texts.all { xmbContrast(xmbLuminance(it.red, it.green, it.blue), lum) >= XMB_GLASS_MIN_CONTRAST }) return a
        steps--
    }
    return 0f
}

/** The wash for one colour scheme: how much accent and white veil went in, and the opaque colour they make over the plain background. */
internal class XmbWash(val accentAlpha: Float, val veilAlpha: Float, val flat: Color)

internal fun xmbWashFor(background: Color, accent: Color, texts: List<Color>): XmbWash {
    val alpha = xmbWashAlpha(background, accent, texts)
    val veilAlpha = xmbWashVeil(background)
    val rgb = xmbWashedRgb(background, accent, alpha, veilAlpha)
    return XmbWash(alpha, veilAlpha, Color(rgb[0].coerceIn(0f, 1f), rgb[1].coerceIn(0f, 1f), rgb[2].coerceIn(0f, 1f)))
}

// ---------------------------------------------------------------------------------------------------------------------
// The glass: the maths. Everything here is pure (no Compose runtime), so the contrast of the text over it is unit-tested.
// ---------------------------------------------------------------------------------------------------------------------

/**
 * The fills of the glass on the base. [card] is a card or panel, [raised] something on a card (a chip, a button, a field),
 * [bar] a top strip / rail / bottom bar (a veil of the base, so content scrolling under it stays out of its text), [line] the
 * thin border round them and [sheen] the light at the top of a panel (it fades to nothing by [SHEEN_REACH] of its height).
 */
internal class XmbFrost(val card: Color, val raised: Color, val bar: Color, val line: Color, val sheen: Color)

/**
 * One glass: the opaque [base], the lighter [sweep] it fades from at the top-left, [panel] (a card's opaque colour over the
 * base, for a panel that can't be see-through), the [frost] that sits on top of the base, and the secondary text colour the
 * pages use over it ([secondaryText], the theme's own, lifted only as far as it takes to stay readable on the lightest glass).
 */
internal class XmbGlass(val base: Color, val sweep: Color, val panel: Color, val frost: XmbFrost, val secondaryText: Color)

/**
 * The base's lightness runs from [GLASS_BASE_L_MIN] (a pure black preset) to [GLASS_BASE_L_MIN] + [GLASS_BASE_L_SPAN] (a preset
 * whose background is at [GLASS_BASE_L_REF] lightness or lighter), so presets keep their own depth and none goes above a dark grey.
 */
private const val GLASS_BASE_L_MIN = 0.055f
private const val GLASS_BASE_L_SPAN = 0.040f
private const val GLASS_BASE_L_REF = 0.10f

/**
 * The base takes its hue from the preset's surface colour and a fraction ([GLASS_TINT_SCALE]) of its saturation, held to
 * [GLASS_SAT_MIN]..[GLASS_SAT_MAX], so it is a muted shade of the preset's family. A surface under [GLASS_NEUTRAL_BELOW]
 * saturation (Charcoal, Classic Dark, AMOLED, Monochrome) gives plain grey.
 */
private const val GLASS_SAT_MIN = 0.08f
private const val GLASS_SAT_MAX = 0.18f
private const val GLASS_TINT_SCALE = 0.35f
private const val GLASS_NEUTRAL_BELOW = 0.06f

/** How much lighter the sweep is than the base at the top-left corner (lightness): a few percent, so it only just shows. */
private const val GLASS_SWEEP_LIFT = 0.035f

/** Where the sweep has faded back to the base, as a fraction of the diagonal from the top-left to the bottom-right corner. */
private const val GLASS_SWEEP_END = 0.55f

/**
 * Opacity of white over the base for a card / for something on a card, on a dark theme (a light one needs more white to read as
 * glass). A little stronger than a flat frost so the panels pop off the page (together with their shadow, see [xmbGlassCard]);
 * the secondary text is lifted by [xmbGlassSecondaryText] where the lighter chips need it.
 */
private const val FROST_CARD_ALPHA = 0.085f
private const val FROST_RAISED_ALPHA = 0.12f
private const val FROST_CARD_ALPHA_LIGHT = 0.45f
private const val FROST_RAISED_ALPHA_LIGHT = 0.60f

/** The thin border round glass: ~1 dp of white at this opacity on a dark theme. */
private const val FROST_LINE_ALPHA = 0.13f

/** The sheen's opacity at the top of a panel (white), and how far down it reaches before it is gone. */
private const val FROST_SHEEN_ALPHA = 0.05f
private const val FROST_SHEEN_ALPHA_LIGHT = 0.60f
internal const val SHEEN_REACH = 0.6f

/** How much of the base a bar keeps over the page behind it. */
private const val FROST_BAR_ALPHA = 0.72f

/** The contrast the glass is tuned to; the extra 0.1 over [XMB_GLASS_MIN_CONTRAST] covers the 8-bit rounding of what is drawn. */
internal const val XMB_GLASS_TARGET_CONTRAST = 4.6

private fun mixColor(a: Color, b: Color, t: Float): Color =
    Color(
        (a.red + (b.red - a.red) * t).coerceIn(0f, 1f),
        (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
        (a.blue + (b.blue - a.blue) * t).coerceIn(0f, 1f),
    )

/** [fill] (its alpha as the opacity) laid over the opaque [ground]. */
private fun overColor(ground: Color, fill: Color): Color = mixColor(ground, fill, fill.alpha)

/** An sRGB colour as hue (0..360), saturation and lightness (0..1). */
internal fun toHsl(c: Color): FloatArray {
    val mx = max(c.red, max(c.green, c.blue))
    val mn = min(c.red, min(c.green, c.blue))
    val l = (mx + mn) / 2f
    val d = mx - mn
    if (d < 1e-6f) return floatArrayOf(0f, 0f, l)
    val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        c.red -> (c.green - c.blue) / d + (if (c.green < c.blue) 6f else 0f)
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    }
    return floatArrayOf(h * 60f, s, l)
}

private fun hueChannel(p: Float, q: Float, tIn: Float): Float {
    var t = tIn
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 0.5f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}

internal fun fromHsl(hue: Float, s: Float, l: Float): Color {
    val h = (((hue % 360f) + 360f) % 360f) / 360f
    if (s < 1e-6f) return Color(l.coerceIn(0f, 1f), l.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    return Color(
        hueChannel(p, q, h + 1f / 3f).coerceIn(0f, 1f),
        hueChannel(p, q, h).coerceIn(0f, 1f),
        hueChannel(p, q, h - 1f / 3f).coerceIn(0f, 1f),
    )
}

/**
 * The dark, muted base of a dark preset ([lift] lighter in lightness for the sweep): hue and a little of the saturation from
 * [surface], depth from how dark the preset's [background] is. The accent plays no part, so a custom accent can't change it.
 */
internal fun xmbGlassBase(background: Color, surface: Color, lift: Float = 0f): Color {
    val bg = toHsl(background)
    val sf = toHsl(surface)
    val l = GLASS_BASE_L_MIN + GLASS_BASE_L_SPAN * (bg[2] / GLASS_BASE_L_REF).coerceIn(0f, 1f) + lift
    val s = if (sf[1] < GLASS_NEUTRAL_BELOW) 0f else (sf[1] * GLASS_TINT_SCALE).coerceIn(GLASS_SAT_MIN, GLASS_SAT_MAX)
    return fromHsl(sf[0], s, l)
}

/** The glass fills for a [dark] or light theme over [base]. */
internal fun xmbFrostFor(dark: Boolean, base: Color): XmbFrost =
    XmbFrost(
        card = Color.White.copy(alpha = if (dark) FROST_CARD_ALPHA else FROST_CARD_ALPHA_LIGHT),
        raised = Color.White.copy(alpha = if (dark) FROST_RAISED_ALPHA else FROST_RAISED_ALPHA_LIGHT),
        bar = if (dark) base.copy(alpha = FROST_BAR_ALPHA) else Color.White.copy(alpha = FROST_BAR_ALPHA),
        line = if (dark) Color.White.copy(alpha = FROST_LINE_ALPHA) else Color.Black.copy(alpha = 0.12f),
        sheen = Color.White.copy(alpha = if (dark) FROST_SHEEN_ALPHA else FROST_SHEEN_ALPHA_LIGHT),
    )

/**
 * Every opaque colour text can end up on: the page (the base, and the sweep at its lightest), a card on it with and without the
 * sheen at its top, and a chip on each of those. The glass stacks, and each layer lifts the ground a little more.
 */
internal fun xmbGlassGrounds(base: Color, sweep: Color, frost: XmbFrost): List<Color> {
    val grounds = ArrayList<Color>(10)
    for (page in listOf(base, sweep)) {
        val card = overColor(page, frost.card)
        val lit = overColor(card, frost.sheen)
        grounds.add(page)
        grounds.add(card)
        grounds.add(lit)
        grounds.add(overColor(card, frost.raised))
        grounds.add(overColor(lit, frost.raised))
    }
    return grounds
}

/**
 * The secondary text over the glass: the theme's own, moved towards its main text colour only as far as it takes to reach
 * [XMB_GLASS_TARGET_CONTRAST] on every ground (most presets need a small step; a theme whose secondary text is already clear
 * is left alone). If even the main colour can't do it, that is the theme's own doing and the main colour is used.
 */
internal fun xmbGlassSecondaryText(grounds: List<Color>, onBackground: Color, onSurfaceVariant: Color): Color {
    val lums = grounds.map { xmbLuminance(it.red, it.green, it.blue) }
    for (step in 0..20) {
        val c = mixColor(onSurfaceVariant, onBackground, step / 20f)
        val ink = xmbLuminance(c.red, c.green, c.blue)
        if (lums.all { xmbContrast(ink, it) >= XMB_GLASS_TARGET_CONTRAST }) return c
    }
    return onBackground
}

/**
 * The glass for one colour scheme. A light scheme (not reachable from the app today, which is dark only) gets the page colour
 * as its base with white glass on it.
 */
internal fun xmbGlassFor(background: Color, surface: Color, onBackground: Color, onSurfaceVariant: Color): XmbGlass {
    val dark = isDarkBase(background)
    val base = if (dark) xmbGlassBase(background, surface) else background
    val sweep = if (dark) xmbGlassBase(background, surface, GLASS_SWEEP_LIFT) else mixColor(background, Color.White, 0.6f)
    val frost = xmbFrostFor(dark, base)
    val secondary = xmbGlassSecondaryText(xmbGlassGrounds(base, sweep, frost), onBackground, onSurfaceVariant)
    return XmbGlass(base, sweep, overColor(base, frost.card), frost, secondary)
}

// ---------------------------------------------------------------------------------------------------------------------
// The glass: drawing and theming.
// ---------------------------------------------------------------------------------------------------------------------

/** The wash [XmbGlassTheme] settled on for this subtree, so a nested reader doesn't wash the already washed background again. */
private val LocalXmbWash = staticCompositionLocalOf<XmbWash?> { null }

/** The glass [XmbGlassTheme] settled on (set in every XMB subtree, glass pages or not), for the panels that draw their own. */
private val LocalXmbGlass = staticCompositionLocalOf<XmbGlass?> { null }

/** True where the page itself sits on the glass (every XMB page but Home), so its cards turn to glass. */
private val LocalXmbOnGlass = staticCompositionLocalOf { false }

/** The scheme [XmbGlassTheme] started from, before the glass changed some of it: [XmbFlatTheme] goes back to it. */
private val LocalXmbBaseScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** The wash for the current theme; recomputed only when the colour scheme changes. */
@Composable
internal fun rememberXmbWash(): XmbWash {
    val provided = LocalXmbWash.current
    val cs = MaterialTheme.colorScheme
    return provided ?: remember(cs.background, cs.primary, cs.onBackground, cs.onSurface, cs.onSurfaceVariant) {
        xmbWashFor(cs.background, cs.primary, listOf(cs.onBackground, cs.onSurface, cs.onSurfaceVariant))
    }
}

/** The glass for the current theme; recomputed only when the colour scheme changes. */
@Composable
internal fun rememberXmbGlass(): XmbGlass {
    val provided = LocalXmbGlass.current
    val cs = MaterialTheme.colorScheme
    return provided ?: remember(cs.background, cs.surface, cs.onBackground, cs.onSurfaceVariant) {
        xmbGlassFor(cs.background, cs.surface, cs.onBackground, cs.onSurfaceVariant)
    }
}

/**
 * Paints [glass] into whatever box this is applied to: one diagonal gradient from the sweep at the top-left corner to the base
 * (opaque stops, so it covers whatever is behind it). The brush is built once per size (the cache), nothing is allocated while
 * drawing, and nothing in it reads state, so a scroll behind or over it never redraws it.
 */
internal fun xmbGlassDraw(glass: XmbGlass): Modifier = Modifier.drawWithCache {
    val brush = Brush.linearGradient(
        0f to glass.sweep,
        GLASS_SWEEP_END to glass.base,
        1f to glass.base,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    onDrawBehind { drawRect(brush) }
}

/**
 * The glass behind this element, drawn straight into it (no layer of its own): for a screen whose root is a scrolling column
 * or grid, which must not become a layer. Needs the glass of the current theme, so use it under [XmbGlassTheme].
 */
@Composable
internal fun Modifier.xmbGlassBackground(): Modifier {
    val glass = rememberXmbGlass()
    return this.then(remember(glass) { xmbGlassDraw(glass) })
}

/**
 * The backdrop of an XMB page: the glass for the current theme, filling the box it is given. It is a layer of its own (drawn
 * once and then just composited), so a page scrolling on top of it never makes it draw again.
 */
@Composable
internal fun XmbGlassBackdrop(modifier: Modifier = Modifier) {
    val glass = rememberXmbGlass()
    val draw = remember(glass) { xmbGlassDraw(glass) }
    Box(modifier.graphicsLayer { }.then(draw))
}

/** The sheen of a panel: [sheen] at the top of the box fading to nothing by [SHEEN_REACH] of its height, drawn over its fill. */
internal fun xmbSheenDraw(sheen: Color): Modifier = Modifier.drawWithCache {
    val brush = Brush.verticalGradient(listOf(sheen, sheen.copy(alpha = 0f)), startY = 0f, endY = max(1f, size.height * SHEEN_REACH))
    onDrawBehind { drawRect(brush) }
}

/**
 * The sheen over a card's fill, so it reads as glass catching the light. Put it right after the card's `background` (and its
 * clip): it follows the clip's corners. Nothing off the glass pages, and nothing in a dialog, menu or sheet (see [xmbPageFrost]).
 */
@Composable
internal fun Modifier.xmbSheen(): Modifier {
    val frost = xmbPageFrost() ?: return this
    return this.then(remember(frost) { xmbSheenDraw(frost.sheen) })
}

// ---------------------------------------------------------------------------------------------------------------------
// Panels that pop: a soft drop shadow round a card and a light hairline along its top edge.
// ---------------------------------------------------------------------------------------------------------------------

/** The card's ambient shadow: black at this opacity, blurred over [XMB_CARD_SHADOW_BLUR_DP], dropped [XMB_CARD_SHADOW_DY_DP]. */
internal const val XMB_CARD_SHADOW_ALPHA = 0.45f
internal const val XMB_CARD_SHADOW_BLUR_DP = 16f
internal const val XMB_CARD_SHADOW_DY_DP = 4f

/** The tight contact shadow right under the card edge. */
internal const val XMB_CARD_CONTACT_ALPHA = 0.35f
internal const val XMB_CARD_CONTACT_BLUR_DP = 3f
internal const val XMB_CARD_CONTACT_DY_DP = 1f

/** The 1 dp edge light: white at the top of the card, fading to the bottom value along its height. */
internal const val XMB_HAIRLINE_TOP_ALPHA = 0.26f
internal const val XMB_HAIRLINE_BOTTOM_ALPHA = 0.08f

/** One soft rounded rect of the stacked shadow: [spreadDp] bigger than the card on every side, [dyDp] lower, black at [alpha]. */
internal class XmbShadowLayer(val spreadDp: Float, val dyDp: Float, val alpha: Float)

/**
 * The shadow as stacked rounded rects, widest first (stacked translucent layers stand in for a blur; no blur layer is needed,
 * so it is cheap and works on every API level). A card's shadow reaches [XMB_CARD_SHADOW_BLUR_DP] x 5/8 = 10 dp to the sides,
 * 6 dp above and 14 dp below it: a lazy list that holds cards needs at least that much spacing / edge padding (12 dp, 16 dp at the
 * bottom edge) or its viewport clips the shadow.
 */
internal fun xmbShadowLayers(): List<XmbShadowLayer> {
    val reach = XMB_CARD_SHADOW_BLUR_DP * 0.625f
    val ambient = XMB_CARD_SHADOW_ALPHA * 0.2f
    val contact = XMB_CARD_CONTACT_ALPHA * 0.5f
    return listOf(
        XmbShadowLayer(reach, XMB_CARD_SHADOW_DY_DP, ambient),
        XmbShadowLayer(reach * 0.6f, XMB_CARD_SHADOW_DY_DP, ambient),
        XmbShadowLayer(reach * 0.2f, XMB_CARD_SHADOW_DY_DP, ambient),
        XmbShadowLayer(XMB_CARD_CONTACT_BLUR_DP * 0.6f, XMB_CARD_CONTACT_DY_DP, contact),
        XmbShadowLayer(XMB_CARD_CONTACT_BLUR_DP * 0.2f, XMB_CARD_CONTACT_DY_DP, contact),
    )
}

internal val XMB_SHADOW_LAYERS = xmbShadowLayers()

internal fun CacheDrawScope.shapePath(shape: Shape): Path {
    val path = Path()
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }
    return path
}

/**
 * The shadow behind a card of [shape], drawn in the box of the element it is on (put it BEFORE the clip: the shadow reaches
 * outside). The card's own outline is cut out of every layer, so a see-through card is never darkened by the shadow behind it.
 * Cached per size: nothing is built while drawing.
 */
private fun xmbShadowDraw(shape: Shape): Modifier = Modifier.drawWithCache {
    val cutOut = shapePath(shape)
    val corner = xmbShapeCornerPx(shape, size.width, size.height, this)
    onDrawBehind {
        clipPath(cutOut, ClipOp.Difference) {
            for (layer in XMB_SHADOW_LAYERS) {
                val spread = layer.spreadDp.dp.toPx()
                drawRoundRect(
                    color = Color.Black.copy(alpha = layer.alpha),
                    topLeft = Offset(-spread, layer.dyDp.dp.toPx() - spread),
                    size = Size(size.width + spread * 2f, size.height + spread * 2f),
                    cornerRadius = CornerRadius(corner + spread),
                )
            }
        }
    }
}

/**
 * Over a card's fill: the [sheen] fading out down the card (null: none) and the hairline, a 1 dp line along the card's edge
 * that is brightest at the top. Drawn behind the card's content, inside its clip.
 */
private fun xmbGlassTopDraw(shape: Shape, sheen: Color?): Modifier = Modifier.drawWithCache {
    val edge = shapePath(shape)
    val sheenBrush = if (sheen == null) null else
        Brush.verticalGradient(listOf(sheen, sheen.copy(alpha = 0f)), startY = 0f, endY = max(1f, size.height * SHEEN_REACH))
    val hairBrush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = XMB_HAIRLINE_TOP_ALPHA), Color.White.copy(alpha = XMB_HAIRLINE_BOTTOM_ALPHA)),
        startY = 0f,
        endY = max(1f, size.height),
    )
    // Stroked 2 dp on the edge; the clip keeps the inner 1 dp.
    val stroke = Stroke(width = 2.dp.toPx())
    onDrawBehind {
        if (sheenBrush != null) drawRect(sheenBrush)
        drawPath(edge, hairBrush, style = stroke)
    }
}

/**
 * A card on the glass: `clip(shape)`, the [fill], the sheen, and what makes it pop: a soft shadow round it and a hairline of
 * light along its top edge. The card's border, if any, goes after it. Shapes without corners work too (the shadow just has
 * square corners).
 *
 * On a glass page the shadow is cut out of the card's own outline, so a translucent card shows the page, not a darkened
 * patch. In a dialog, menu or sheet (see [xmbPageFrost]) the panel stays opaque, with only the hairline and no shadow; off the
 * glass pages (Home, Classic, plain Deck) it is exactly `clip(shape).background(fill)`.
 *
 * The shadow is drawn outside the card: a scrolling list clips it at its viewport, so lists need 12 dp or more of spacing and
 * edge padding round their cards (see [xmbShadowLayers] for how far it reaches).
 */
@Composable
internal fun Modifier.xmbGlassCard(shape: Shape, fill: Color = deckCardFill()): Modifier {
    val frost = xmbPageFrost()
    if (frost != null) {
        val shadow = remember(shape) { xmbShadowDraw(shape) }
        val top = remember(shape, frost) { xmbGlassTopDraw(shape, frost.sheen) }
        return this.then(shadow).clip(shape).background(fill).then(top)
    }
    if (xmbOnGlass()) {
        val top = remember(shape) { xmbGlassTopDraw(shape, null) }
        return this.clip(shape).background(fill).then(top)
    }
    return this.clip(shape).background(fill)
}

/** The scheme colours that carry the glass look: the page background goes clear, and the two "raised" surfaces turn to frost. */
private fun ColorScheme.onGlass(glass: XmbGlass): ColorScheme =
    copy(
        background = Color.Transparent,
        surfaceVariant = glass.frost.raised,
        surfaceContainerHighest = glass.frost.raised,
        onSurfaceVariant = glass.secondaryText,
    )

/**
 * In XMB mode, a theme for the pages inside. With [glass] (every page but Home) the pages sit on [XmbGlassBackdrop]: their own
 * `colorScheme.background` fills go clear so the glass shows through, the two raised surfaces become glass and the secondary
 * text is lifted where it has to be to stay readable on it. Without it (Home) `background` is the flat wash.
 * Everything else in the scheme is untouched, so stock dialogs, menus and sheets keep their opaque surfaces. Off: [content] as is.
 */
@Composable
internal fun XmbGlassTheme(enabled: Boolean, glass: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val wash = rememberXmbWash()
    val spec = rememberXmbGlass()
    val base = MaterialTheme.colorScheme
    // Not remembered: MaterialTheme keeps one ColorScheme and updates it in place, so `base` is the same object after a theme
    // change; reading it here is what makes this recompose, and the copy is cheap.
    val themed = if (glass) base.onGlass(spec) else base.copy(background = wash.flat)
    CompositionLocalProvider(
        LocalXmbWash provides wash,
        LocalXmbGlass provides spec,
        LocalXmbOnGlass provides glass,
        LocalXmbBaseScheme provides (LocalXmbBaseScheme.current ?: base),
    ) {
        MaterialTheme(
            colorScheme = themed,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * For a part that has to look the same on every XMB page and over Home, and sits inside the glass pages' theme (the XMB
 * keyboard, drawn in a window of its own): the scheme the glass started from with the flat wash as its background, and none of
 * the glass. Not in XMB mode: [content] as is.
 */
@Composable
internal fun XmbFlatTheme(content: @Composable () -> Unit) {
    val base = LocalXmbBaseScheme.current
    val wash = LocalXmbWash.current
    if (base == null || wash == null) {
        content()
        return
    }
    CompositionLocalProvider(LocalXmbOnGlass provides false) {
        MaterialTheme(
            colorScheme = base.copy(background = wash.flat),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * The page background as a solid colour, for something drawn in it (a fade into the page, a cut-out). On the glass pages the
 * scheme's `background` is clear, which would draw nothing, so this is the glass's own base there; elsewhere the scheme's.
 */
@Composable
internal fun xmbSolidBackground(): Color {
    val background = MaterialTheme.colorScheme.background
    return if (background.alpha > 0f) background else LocalXmbGlass.current?.base ?: background
}

/** A card's fill: the glass on a glass page, else [fallback] (the card's own colour, untouched off the glass, in dialogs and over Home). */
@Composable
internal fun xmbCardFill(fallback: Color): Color = xmbPageFrost()?.card ?: fallback

/** The thin border round a card: the light glass line on a glass page, else [fallback]. */
@Composable
internal fun xmbCardLine(fallback: Color): Color = xmbPageFrost()?.line ?: fallback

/**
 * A solid chip fill for a label over artwork (a badge on a cover). `surfaceContainerHighest`, except on the glass
 * pages where that is glass: the artwork would show through it, so there it is the opaque `surfaceContainerHigh`.
 */
@Composable
internal fun xmbChipOverArt(): Color {
    val cs = MaterialTheme.colorScheme
    return if (xmbOnGlass()) cs.surfaceContainerHigh else cs.surfaceContainerHighest
}

/** True while the page sits on the glass, so a card there is glass. */
@Composable
internal fun xmbOnGlass(): Boolean = LocalXmbOnGlass.current && LocalXmbGlass.current != null

/**
 * The glass fills for a card on a glass page, or null: off the glass, and inside a dialog, menu or sheet (their window is the
 * page's composition but not its pixels, so a see-through card would show the dimmed page behind it instead of the glass).
 */
@Composable
internal fun xmbPageFrost(): XmbFrost? {
    if (!LocalXmbOnGlass.current) return null
    val glass = LocalXmbGlass.current ?: return null
    return if (inFloatingWindow()) null else glass.frost
}

private fun Context.hostActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return c as? Activity
}

/** Whether this composition is drawn in a window of its own (a dialog, a popup menu, a sheet) rather than in the activity's. */
@Composable
private fun inFloatingWindow(): Boolean {
    val view = LocalView.current
    return remember(view) {
        val decor = view.context.hostActivity()?.window?.decorView
        decor != null && view.rootView !== decor
    }
}

/**
 * The look of a panel in a window of its own (a dialog, the search overlay): clipped to [shape], the glass panel's opaque
 * colour, the sheen over it and the thin border round it. A panel can't be see-through (the dimmed page is behind it), so it
 * carries the colour a card would have over the base instead. Only on a glass page (or inside [XmbPanelTheme]), where the text
 * is the one the glass was tuned for; elsewhere [fill] and the Deck hairline.
 */
@Composable
internal fun Modifier.xmbPanel(shape: Shape, fill: Color): Modifier {
    val glass = if (LocalXmbOnGlass.current) LocalXmbGlass.current else null
    if (glass == null) return this.clip(shape).background(fill).border(1.dp, deckLine(), shape)
    // The sheen plus the hairline of light along the top edge (no outer shadow: the window has no page of its own to cast on).
    val top = remember(glass, shape) { xmbGlassTopDraw(shape, glass.frost.sheen) }
    return this.clip(shape).background(glass.panel).then(top).border(1.dp, glass.frost.line, shape)
}

// ---------------------------------------------------------------------------------------------------------------------
// Raised elements on a popup's own panel (the launch popup): opaque fill, the top-edge hairline and the soft shadow.
// ---------------------------------------------------------------------------------------------------------------------

/** White over the popup's panel for a raised row / tile / button, and for a small raised element (a "?" dot, a close button): the card and chip lifts of the glass. */
internal const val XMB_POP_LIFT = FROST_CARD_ALPHA
internal const val XMB_POP_LIFT_SMALL = FROST_RAISED_ALPHA

/**
 * The opaque fill of a raised element sitting on the opaque [surface] of a popup: [lift] of white over it, then (for a selected
 * one) [tint] at [tintAlpha] over that. The lift backs off, down to none, until every one of [inks] (the colours of the text and
 * icons drawn on it) keeps [XMB_GLASS_MIN_CONTRAST] on the result; if it already fails on the plain [surface] that is the theme's
 * own doing and the lift just goes to none. Never translucent.
 */
internal fun xmbPopFillOf(surface: Color, lift: Float, tint: Color?, tintAlpha: Float, inks: List<Color>): Color {
    val ground = Color(surface.red, surface.green, surface.blue)
    fun build(steps: Int): Color {
        var fill = overColor(ground, Color.White.copy(alpha = steps / 1000f))
        if (tint != null && tintAlpha > 0f) fill = overColor(fill, tint.copy(alpha = tintAlpha))
        return fill
    }
    fun readable(fill: Color): Boolean {
        val lum = xmbLuminance(fill.red, fill.green, fill.blue)
        return inks.all { xmbContrast(xmbLuminance(it.red, it.green, it.blue), lum) >= XMB_GLASS_MIN_CONTRAST }
    }
    var steps = (lift.coerceIn(0f, 1f) * 1000f).roundToInt()
    var fill = build(steps)
    while (steps > 0 && !readable(fill)) {
        steps--
        fill = build(steps)
    }
    return fill
}

/** True where a popup sits in the XMB interface (the glass is provided, on every XMB page and over Home): the raised look applies. */
@Composable
internal fun xmbPopActive(): Boolean = LocalXmbGlass.current != null

/**
 * The fill of a raised element on a popup's panel ([MaterialTheme]'s `surface`): a slightly lighter opaque tone ([small]: the
 * stronger lift of a chip), [tint] (the accent) at [tintAlpha] over it for a selected one. Read it OUTSIDE an [XmbInk] block (the
 * ink changes the text colours the lift is checked against). Not in XMB mode: the scheme's `surfaceContainerHigh`.
 */
@Composable
internal fun xmbPopFill(small: Boolean = false, tint: Color? = null, tintAlpha: Float = 0f): Color {
    val cs = MaterialTheme.colorScheme
    val glass = LocalXmbGlass.current
    return remember(glass, cs.surface, cs.onSurface, cs.onSurfaceVariant, cs.primary, small, tint, tintAlpha) {
        if (glass == null) {
            cs.surfaceContainerHigh
        } else {
            val inks = ArrayList<Color>(4)
            inks.add(cs.onSurface)
            inks.add(cs.onSurfaceVariant)
            inks.add(xmbInkColor(cs.primary, cs.onSurface, glass.panel))
            if (tint != null) inks.add(tint)
            xmbPopFillOf(cs.surface, if (small) XMB_POP_LIFT_SMALL else XMB_POP_LIFT, tint, tintAlpha, inks)
        }
    }
}

/**
 * A raised element on a popup's own opaque panel: `clip(shape)`, the opaque [fill] (see [xmbPopFill]; never translucent), the
 * 1 dp hairline of light along the top edge and, with [shadow], the soft shadow of [xmbGlassCard] so it lifts off the panel (the
 * shadow is cut out of the element's own outline, so it never darkens it). It is the dialog counterpart of [xmbGlassCard], which
 * keeps a dialog flat because a see-through card would show the dimmed page; an element on the popup's own panel has no such page
 * behind it. Not in XMB mode: exactly `clip(shape).background(fill)`.
 *
 * The shadow is drawn outside the element (before the clip) and reaches ~10 dp to the sides, 6 dp above and 14 dp below it (see
 * [xmbShadowLayers]): keep 8 dp or more between raised elements and 10 dp or more of padding to a scrolling column's edge.
 */
@Composable
internal fun Modifier.xmbPopCard(shape: Shape, fill: Color, shadow: Boolean = true): Modifier {
    if (LocalXmbGlass.current == null) return this.clip(shape).background(fill)
    val top = remember(shape) { xmbGlassTopDraw(shape, null) }
    if (!shadow) return this.clip(shape).background(fill).then(top)
    val drop = remember(shape) { xmbShadowDraw(shape) }
    return this.then(drop).clip(shape).background(fill).then(top)
}

/**
 * For a panel that draws its own glass ([xmbPanel]) outside the glass pages (the search over Home): the same scheme the glass
 * pages have, so its text is the one the glass was tuned for. Not in XMB mode: [content] as is.
 */
@Composable
internal fun XmbPanelTheme(content: @Composable () -> Unit) {
    val glass = LocalXmbGlass.current
    if (glass == null) {
        content()
        return
    }
    CompositionLocalProvider(LocalXmbOnGlass provides true) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.onGlass(glass),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
