package com.winhub.hernandez.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TWO_PI = (2.0 * PI).toFloat()

/** The tabs that get the liquid backdrop in portrait (not editors; see DeckModeScreen). */
internal val liquidSections = setOf(
    "home", "containers", "stores", "file_manager", "input_controls", "contents", "saves", "social_hub", "settings",
)

/**
 * A tab's own page background. In portrait the shell draws the backdrop (liquid or plain), so the page
 * stays transparent there and the waves show through; elsewhere it paints [color].
 */
@Composable
internal fun Modifier.deckPageBackground(color: Color = DeckPalette.background): Modifier =
    if (LocalDeckPortrait.current) this else this.background(color)

/**
 * True while portrait Home has a game page open over the grid. The page paints its own background, so
 * the shell stops drawing the liquid backdrop (it would otherwise show under the bottom bar).
 */
internal object PortraitPageState {
    var open by mutableStateOf(false)

    /** True while the inline per-game settings editor is up: it is a full page, so no waves behind it. */
    var editor by mutableStateOf(false)
}

/**
 * Portrait backdrop: three layers of waves along the bottom and, when [glows] is on (Home), three
 * theme-coloured glows drifting slowly behind them. Pure drawing from one looping phase (read in the
 * draw phase, so it redraws without recomposing); the colours come from the active theme.
 */
@Composable
internal fun Modifier.liquidBackdrop(glows: Boolean = true): Modifier {
    val transition = rememberInfiniteTransition(label = "liquid")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "liquidPhase",
    )
    val primary = Deck.primary
    val secondary = Deck.secondary
    val tertiary = DeckPalette.drawerTertiary
    val wave = remember { Path() }
    return this.drawBehind {
        val t = phase * TWO_PI
        val w = size.width
        val h = size.height
        if (glows) {
            drawGlow(primary, w * (0.15f + 0.30f * sin(t)), h * (0.10f + 0.12f * cos(t)), w * 0.8f, 0.42f)
            drawGlow(secondary, w * (0.90f - 0.30f * cos(t)), h * (0.50f + 0.12f * sin(t)), w * 0.75f, 0.28f)
            drawGlow(tertiary, w * (0.25f + 0.25f * cos(t)), h * (0.92f - 0.08f * sin(t)), w * 0.8f, 0.24f)
        }
        drawWave(wave, primary.copy(alpha = 0.14f), h * 0.88f, h * 0.012f, 1.2f, t)
        drawWave(wave, secondary.copy(alpha = 0.12f), h * 0.91f, h * 0.014f, 1.7f, -2f * t)
        drawWave(wave, tertiary.copy(alpha = 0.10f), h * 0.94f, h * 0.016f, 2.3f, 3f * t)
    }
}

private fun DrawScope.drawGlow(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
    val c = Offset(cx, cy)
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), c, radius), radius, c)
}

private fun DrawScope.drawWave(path: Path, color: Color, base: Float, amp: Float, cycles: Float, shift: Float) {
    val step = 12f
    path.reset()
    path.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + step) {
        path.lineTo(x, base + amp * sin(TWO_PI * cycles * (x / size.width) + shift))
        x += step
    }
    path.lineTo(x, size.height)
    path.close()
    drawPath(path, color)
}

/**
 * Frosted-glass surface: a faint translucent fill with a light edge, tinted from the theme's text
 * colour so it reads on dark and light themes alike. No backdrop blur, so it costs nothing extra.
 */
internal fun Modifier.glassCard(shape: Shape): Modifier {
    val ink = Deck.onBackground
    return this
        .clip(shape)
        .background(Brush.linearGradient(listOf(ink.copy(alpha = 0.14f), ink.copy(alpha = 0.05f))))
        .border(1.dp, Brush.linearGradient(listOf(ink.copy(alpha = 0.40f), ink.copy(alpha = 0.08f))), shape)
}
