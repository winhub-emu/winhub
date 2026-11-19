package com.winhub.hernandez.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp

// The WinHub mark's loop (wh-animated.svg), in that file's own coordinates: the three dots pulse in turn,
// the H's outline draws itself in white and retracts, then all three dots pulse once more and ripple out.
private const val VIEW_X = 45.5f
private const val VIEW_Y = 122.5f
private const val VIEW_W = 340f
private const val VIEW_H = 200f
private const val LOOP_MS = 3000
// A frame where every outline and ripple is gone and the dots are at rest: the plain mark, for when the animation is off.
private const val STILL_T = 0.95f

private val Blue = Color(0xFF1EA1FF)
private val Ink = Color.White
private const val DOT_R = 11.7f

private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val OutlineEase = CubicBezierEasing(0.6f, 0f, 0.3f, 1f)
private val RippleEase = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f)

// Keyframes as flat (time, value) pairs, so sampling a frame allocates nothing.
private val DotA = floatArrayOf(0f, 1f, .08f, 1f, .11f, 1.25f, .15f, 1f, .48f, 1f, .51f, 1.3f, .56f, 1f, 1f, 1f)
private val DotB = floatArrayOf(0f, 1f, .10f, 1f, .13f, 1.25f, .17f, 1f, .48f, 1f, .51f, 1.3f, .56f, 1f, 1f, 1f)
private val DotC = floatArrayOf(0f, 1f, .12f, 1f, .15f, 1.25f, .19f, 1f, .48f, 1f, .51f, 1.3f, .56f, 1f, 1f, 1f)
// Stroke-dash offsets of the three outline strokes (1.02 = nothing drawn, 0 = fully drawn).
private val OutlineLeft = floatArrayOf(.12f, 1.02f, .24f, 0f, .78f, 0f, .88f, 1.02f)
private val OutlineBar = floatArrayOf(.24f, 1.02f, .34f, 0f, .70f, 0f, .78f, 1.02f)
private val OutlineRight = floatArrayOf(.34f, 1.02f, .48f, 0f, .60f, 0f, .70f, 1.02f)

private val DotLeft = Offset(256.19f, 266.28f)
private val DotTop = Offset(324.03f, 179.44f)
private val DotRight = Offset(324.11f, 264.11f)

private val WPath = Path().apply {
    moveTo(107.75f, 179.55f)
    lineTo(144.0f, 264.75f)
    lineTo(181.8f, 180.0f)
    lineTo(218.5f, 265.3f)
    lineTo(256.25f, 180.55f)
}
private val WShadowGrow = floatArrayOf(18f, 12f, 6f)
private val HShadowGrow = floatArrayOf(3.6f, 2.4f, 1.2f)
private val Dots = arrayOf(DotLeft, DotTop, DotRight)
private val WStroke = Stroke(25.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val HStemLeft = Offset(243.2f, 172f) to Size(26f, 109f)
private val HStemRight = Offset(311.5f, 165f) to Size(26f, 114f)
private val HBar = Offset(272f, 218f) to Size(34f, 20.5f)
private fun hGrey(x: Float, w: Float) = Brush.horizontalGradient(
    listOf(Color(0xFFD2D2D2), Color(0xFFBFBFBF), Color(0xFFD2D2D2)), startX = x, endX = x + w,
)
private val HStemLeftBrush = hGrey(HStemLeft.first.x, HStemLeft.second.width)
private val HStemRightBrush = hGrey(HStemRight.first.x, HStemRight.second.width)
private val HBarBrush = hGrey(HBar.first.x, HBar.second.width)

/** The value of a flat (time, value) keyframe list at [t]; the same [ease] shapes every segment, as the SVG's animation does. */
private fun keyed(t: Float, ease: Easing, k: FloatArray): Float {
    if (t <= k[0]) return k[1]
    var i = 2
    while (i < k.size) {
        if (t < k[i]) {
            val f = (t - k[i - 2]) / (k[i] - k[i - 2])
            return lerp(k[i - 1], k[i + 1], ease.transform(f))
        }
        i += 2
    }
    return k[k.size - 1]
}

/**
 * The WinHub mark, animated on a 3 s loop. Give it a width; the height follows the artwork's 340:200 shape.
 * [still] (a failure, or the system's "animations off") draws the plain mark instead, so nothing keeps
 * moving on a screen that is waiting on the user.
 */
@Composable
internal fun WinHubLogoAnimation(modifier: Modifier = Modifier, still: Boolean = false) {
    val context = LocalContext.current
    val animationsOff = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
    val frozen = still || animationsOff
    val loop = rememberInfiniteTransition(label = "whLogo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)),
        label = "whLogoT",
    )
    Canvas(modifier.aspectRatio(VIEW_W / VIEW_H).semantics { contentDescription = "WinHub" }) {
        // The phase is read here, in the draw pass, so the loop never recomposes anything.
        val t = if (frozen) STILL_T else loop.value
        // Draw in the SVG's own coordinates: shift its viewBox to the origin, then scale to the canvas.
        withTransform({
            scale(size.width / VIEW_W, size.width / VIEW_W, Offset.Zero)
            translate(-VIEW_X, -VIEW_Y)
        }) { drawMark(t) }
    }
}

private fun DrawScope.drawMark(t: Float) {
    // W's shadow, then the H's grey bars with theirs, then the outlines that light the H up.
    for (grow in WShadowGrow) {
        drawPath(WPath, Color.Black.copy(alpha = 0.14f), style = Stroke(25.4f + grow, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    hBar(HStemLeft, HStemLeftBrush, 13f)
    hBar(HStemRight, HStemRightBrush, 13f)
    hBar(HBar, HBarBrush, 10.25f)

    outline(t, .12f, .881f, OutlineLeft, Offset(256.2f, 268f), Offset(256.2f, 185f), 26f)
    outline(t, .24f, .781f, OutlineBar, Offset(282.25f, 228.25f), Offset(295.75f, 228.25f), 20.5f)
    outline(t, .34f, .701f, OutlineRight, Offset(324.5f, 266f), Offset(324.5f, 178f), 26f)

    drawPath(WPath, Ink, style = WStroke)

    // Ripples leave all three dots together, as the second pulse peaks.
    if (t in 0.48f..0.72f) {
        val e = RippleEase.transform((t - 0.48f) / 0.24f)
        val grow = lerp(1f, 3f, e)
        val ring = Blue.copy(alpha = 0.8f * (1f - e))
        for (c in Dots) {
            drawCircle(ring, radius = DOT_R * grow, center = c, style = Stroke(1.5f * grow))
        }
    }
    drawCircle(Blue, radius = DOT_R * keyed(t, EaseInOut, DotA), center = DotLeft)
    drawCircle(Blue, radius = DOT_R * keyed(t, EaseInOut, DotB), center = DotTop)
    drawCircle(Blue, radius = DOT_R * keyed(t, EaseInOut, DotC), center = DotRight)
}

private fun DrawScope.hBar(bar: Pair<Offset, Size>, fill: Brush, radius: Float) {
    val (at, box) = bar
    for (grow in HShadowGrow) {
        drawRoundRect(
            Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(at.x - grow, at.y - grow),
            size = Size(box.width + 2 * grow, box.height + 2 * grow),
            cornerRadius = CornerRadius(radius + grow),
        )
    }
    drawRoundRect(fill, topLeft = at, size = box, cornerRadius = CornerRadius(radius))
}

/** One outline stroke: draws from [from] toward [to], holds, then retracts toward [from]; invisible outside [start]..[end]. */
private fun DrawScope.outline(t: Float, start: Float, end: Float, offsets: FloatArray, from: Offset, to: Offset, width: Float) {
    if (t < start || t >= end) return
    val shown = (1.02f - keyed(t, OutlineEase, offsets)).coerceIn(0f, 1f)
    if (shown <= 0.001f) return
    drawLine(Ink, from, Offset(lerp(from.x, to.x, shown), lerp(from.y, to.y, shown)), strokeWidth = width, cap = StrokeCap.Round)
}
