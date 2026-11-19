package com.winhub.hernandez.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp

private val Ink = Color.White
private val Blue = Color(0xFF1E9BFF)

// The animated mark's keyframes (cronosdeck-icon-animated.svg), on its 240-unit canvas: a separate arc
// closes the ring, the ring turns once, the arc flies off, the dot pulses and two ripples leave it.
private val RingEase = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val PieceIn = CubicBezierEasing(0.2f, 0.8f, 0.25f, 1.12f)
private val PieceOut = CubicBezierEasing(0.6f, 0f, 0.9f, 0.5f)
private val DotIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val DotPop = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1.4f)
private val DotSettle = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val RippleEase = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f)

private const val CANVAS = 240f
private const val RING_R = 81f
private const val RING_STROKE = 38f
private const val DOT_R = 42f
private const val RING_GAP_DEG = 38.05f    // the open "C": its ends sit this far above and below 3 o'clock
private const val PIECE_HALF_DEG = 33f     // the loose arc that closes it
private const val LOOP_MS = 3000
// A frame with the arc gone and the dot at rest: the plain logo, used when the animation is off.
private const val STILL_T = 0.9f

private fun span(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The CronosDeck mark, animated on a 3 s loop. [still] (a failure, or the system's "animations off")
 * draws the plain open ring with its dot instead, so nothing keeps moving on a screen that is waiting on the user.
 */
@Composable
internal fun CronosDeckLogo(modifier: Modifier = Modifier, still: Boolean = false) {
    val context = LocalContext.current
    val animationsOff = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
    val frozen = still || animationsOff
    val loop = rememberInfiniteTransition(label = "cronosLogo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)),
        label = "cronosLogoT",
    )
    Canvas(modifier.semantics { contentDescription = "CronosDeck" }) {
        // The phase is read here, in the draw pass, so the loop never recomposes anything.
        drawMark(if (frozen) STILL_T else loop.value, size.minDimension / CANVAS)
    }
}

private fun DrawScope.drawMark(t: Float, u: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    ripple(t, 0.25f, 0.50f, 0.8f, 3f, u, c)
    ripple(t, 0.31f, 0.56f, 0.5f, 2f, u, c)

    val ringDeg = if (t < 0.25f) 0f else if (t < 0.58f) 360f * RingEase.transform(span(t, 0.25f, 0.58f)) else 360f
    val corner = Offset(c.x - RING_R * u, c.y - RING_R * u)
    val box = Size(2 * RING_R * u, 2 * RING_R * u)
    val stroke = Stroke(RING_STROKE * u)
    withTransform({ rotate(ringDeg, c) }) {
        drawArc(Ink, RING_GAP_DEG, 360f - 2 * RING_GAP_DEG, false, corner, box, style = stroke)

        var rot = 0f
        var grow = 1f
        var alpha = 0f
        when {
            t < 0.05f -> Unit
            t < 0.25f -> {
                val e = PieceIn.transform(span(t, 0.05f, 0.25f))
                rot = lerp(-200f, 0f, e); grow = lerp(1.8f, 1f, e); alpha = e.coerceIn(0f, 1f)
            }
            t < 0.58f -> alpha = 1f
            t < 0.78f -> {
                val e = PieceOut.transform(span(t, 0.58f, 0.78f))
                rot = lerp(0f, 200f, e); grow = lerp(1f, 1.8f, e); alpha = 1f - e
            }
        }
        if (alpha > 0f) {
            withTransform({ rotate(rot, c); scale(grow, grow, c) }) {
                drawArc(Ink, -PIECE_HALF_DEG, 2 * PIECE_HALF_DEG, false, corner, box, alpha = alpha, style = stroke)
            }
        }
    }

    val dot = when {
        t < 0.24f -> 1f
        t < 0.265f -> lerp(1f, 0.82f, DotIn.transform(span(t, 0.24f, 0.265f)))
        t < 0.34f -> lerp(0.82f, 1.18f, DotPop.transform(span(t, 0.265f, 0.34f)))
        t < 0.42f -> lerp(1.18f, 1f, DotSettle.transform(span(t, 0.34f, 0.42f)))
        else -> 1f
    }
    drawCircle(Blue, radius = DOT_R * u * dot, center = c)
}

/** A ring that leaves the dot: grows to 2.8x while fading out (its stroke grows with it, as in the SVG). */
private fun DrawScope.ripple(t: Float, from: Float, to: Float, peak: Float, width: Float, u: Float, c: Offset) {
    if (t < from || t >= to) return
    val e = RippleEase.transform(span(t, from, to))
    val grow = lerp(1f, 2.8f, e)
    drawCircle(Blue.copy(alpha = peak * (1f - e)), radius = DOT_R * u * grow, center = c, style = Stroke(width * u * grow))
}
