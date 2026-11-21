package com.winhub.hernandez.widget.fusionhud

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * The WinHub mark for the HUD: the W, the H's grey bars and the three dots, at rest (the loading screen's
 * animation, WinHubLogoAnimation, is the same artwork). [draw] colours the W with [inkColor] and the dots
 * with [dotColor]. The art is cropped to its ink, so the mark sits flush against whatever is beside it.
 */
internal object WinHubHudMark {
    // The artwork's own coordinates, cropped to the W's left edge and the H's right stem, shadows included.
    private const val CROP_X = 84f
    private const val CROP_Y = 154f
    private const val CROP_W = 262f
    private const val CROP_H = 138f
    private const val DOT_R = 11.7f

    private val wPath = Path().apply {
        moveTo(107.75f, 179.55f)
        lineTo(144.0f, 264.75f)
        lineTo(181.8f, 180.0f)
        lineTo(218.5f, 265.3f)
        lineTo(256.25f, 180.55f)
    }
    private val wShadowGrow = floatArrayOf(18f, 12f, 6f)
    private val hShadowGrow = floatArrayOf(3.6f, 2.4f, 1.2f)
    // x, y, width, height, corner radius
    private val stemLeft = floatArrayOf(243.2f, 172f, 26f, 109f, 13f)
    private val stemRight = floatArrayOf(311.5f, 165f, 26f, 114f, 13f)
    private val bar = floatArrayOf(272f, 218f, 34f, 20.5f, 10.25f)
    private val dots = arrayOf(floatArrayOf(256.19f, 266.28f), floatArrayOf(324.03f, 179.44f), floatArrayOf(324.11f, 264.11f))
    private val greys = intArrayOf(0xFFD2D2D2.toInt(), 0xFFBFBFBF.toInt(), 0xFFD2D2D2.toInt())

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun widthFor(height: Float): Float = height * CROP_W / CROP_H

    fun draw(canvas: Canvas, left: Float, top: Float, height: Float, inkColor: Int, dotColor: Int) {
        val s = height / CROP_H
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(s, s)
        canvas.translate(-CROP_X, -CROP_Y)

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.argb(36, 0, 0, 0)
        for (grow in wShadowGrow) {
            paint.strokeWidth = 25.4f + grow
            canvas.drawPath(wPath, paint)
        }

        paint.style = Paint.Style.FILL
        for (b in arrayOf(stemLeft, stemRight, bar)) {
            paint.color = Color.argb(46, 0, 0, 0)
            for (grow in hShadowGrow) {
                canvas.drawRoundRect(b[0] - grow, b[1] - grow, b[0] + b[2] + grow, b[1] + b[3] + grow, b[4] + grow, b[4] + grow, paint)
            }
            paint.shader = LinearGradient(b[0], 0f, b[0] + b[2], 0f, greys, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(b[0], b[1], b[0] + b[2], b[1] + b[3], b[4], b[4], paint)
            paint.shader = null
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 25.4f
        paint.color = inkColor
        canvas.drawPath(wPath, paint)

        paint.style = Paint.Style.FILL
        paint.color = dotColor
        for (d in dots) canvas.drawCircle(d[0], d[1], DOT_R, paint)
        canvas.restore()
    }
}
