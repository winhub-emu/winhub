package com.winhub.hernandez.inputcontrols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

class StickShaperTest {
    private val out = FloatArray(2)

    private fun shape(x: Float, y: Float, dz: Float, sens: Float, smooth: Boolean): FloatArray {
        StickShaper.shape(x, y, dz, sens, smooth, out)
        return floatArrayOf(out[0], out[1])
    }

    private fun legacy(v: Float): Float = if (Math.abs(v) >= ControlElement.STICK_DEAD_ZONE) v else 0f

    @Test
    fun classicAtDefaults_isTheOldFixedGate() {
        var i = -100
        while (i <= 100) {
            var j = -100
            while (j <= 100) {
                val x = i / 100f
                val y = j / 100f
                val r = shape(x, y, StickShaper.DEADZONE_DEFAULT, StickShaper.SENSITIVITY_DEFAULT, false)
                assertEquals("x=$x", legacy(x), r[0], 0f)
                assertEquals("y=$y", legacy(y), r[1], 0f)
                j++
            }
            i++
        }
    }

    @Test
    fun zeroStaysZeroAndFullStaysOne_forEverySensitivityAndStyle() {
        var s = StickShaper.SENSITIVITY_MIN
        while (s <= StickShaper.SENSITIVITY_MAX + 1e-4f) {
            for (smooth in listOf(false, true)) {
                for (dz in listOf(0f, 0.15f, 0.5f)) {
                    val zero = shape(0f, 0f, dz, s, smooth)
                    assertEquals(0f, zero[0], 0f)
                    assertEquals(0f, zero[1], 0f)
                    val full = shape(1f, 0f, dz, s, smooth)
                    assertEquals("dz=$dz s=$s smooth=$smooth", 1f, full[0], 1e-4f)
                    assertEquals(0f, full[1], 1e-4f)
                    val negative = shape(0f, -1f, dz, s, smooth)
                    assertEquals(-1f, negative[1], 1e-4f)
                }
            }
            s += 0.05f
        }
    }

    @Test
    fun classic_cutsBelowDeadzoneAndKeepsSign() {
        val below = shape(0.09f, -0.09f, 0.10f, 1f, false)
        assertEquals(0f, below[0], 0f)
        assertEquals(0f, below[1], 0f)
        val above = shape(-0.5f, 0.5f, 0.10f, 1f, false)
        assertEquals(-0.5f, above[0], 0f)
        assertEquals(0.5f, above[1], 0f)
    }

    @Test
    fun smooth_startsAtZeroJustPastTheEdge() {
        val dz = 0.15f
        val inside = shape(dz - 0.001f, 0f, dz, 1f, true)
        assertEquals(0f, inside[0], 0f)
        val outside = shape(dz + 0.001f, 0f, dz, 1f, true)
        assertTrue(outside[0] in 0f..0.01f)
    }

    @Test
    fun smooth_isRadialAndKeepsDirection() {
        val x = 0.30f
        val y = 0.20f
        val r = shape(x, y, 0.15f, 1.3f, true)
        assertEquals(atan2(y, x), atan2(r[1], r[0]), 1e-5f)
        assertTrue(hypot(r[0], r[1]) in 0f..1f)
    }

    @Test
    fun smooth_clampsSquareGateDiagonalToOne() {
        val r = shape(1f, 1f, 0.15f, 1f, true)
        assertEquals(1f, hypot(r[0], r[1]), 1e-4f)
    }

    @Test
    fun outputNeverDecreasesAsThePushGrows() {
        for (smooth in listOf(false, true)) {
            for (s in listOf(0.5f, 1f, 2f)) {
                var previous = 0f
                var p = 0f
                while (p <= 1.0001f) {
                    val r = shape(p, 0f, 0.2f, s, smooth)
                    assertTrue("smooth=$smooth s=$s p=$p", r[0] >= previous - 1e-6f)
                    previous = r[0]
                    p += 0.005f
                }
            }
        }
    }

    @Test
    fun higherSensitivityReactsMoreToTheSamePush() {
        for (smooth in listOf(false, true)) {
            val fine = shape(0.5f, 0f, 0.15f, 0.5f, smooth)[0]
            val normal = shape(0.5f, 0f, 0.15f, 1f, smooth)[0]
            val quick = shape(0.5f, 0f, 0.15f, 2f, smooth)[0]
            assertTrue(fine < normal)
            assertTrue(normal < quick)
        }
    }

    @Test
    fun nonFiniteInputGivesZero() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (smooth in listOf(false, true)) {
                val a = shape(bad, 0.5f, 0.15f, 1f, smooth)
                assertEquals(0f, a[0], 0f)
                assertEquals(0f, a[1], 0f)
                val b = shape(0.5f, bad, 0.15f, 1f, smooth)
                assertEquals(0f, b[0], 0f)
                assertEquals(0f, b[1], 0f)
            }
        }
    }

    @Test
    fun outOfRangeSettingsAreClamped() {
        assertEquals(StickShaper.DEADZONE_MAX, StickShaper.clampDeadzone(9f), 0f)
        assertEquals(StickShaper.DEADZONE_MIN, StickShaper.clampDeadzone(-1f), 0f)
        assertEquals(StickShaper.DEADZONE_DEFAULT, StickShaper.clampDeadzone(Float.NaN), 0f)
        assertEquals(StickShaper.SENSITIVITY_MAX, StickShaper.clampSensitivity(99f), 0f)
        assertEquals(StickShaper.SENSITIVITY_MIN, StickShaper.clampSensitivity(0f), 0f)
        assertEquals(StickShaper.SENSITIVITY_DEFAULT, StickShaper.clampSensitivity(Float.POSITIVE_INFINITY), 0f)

        val t = StickShaper.Tuning(5f, 0f, -5f, 50f, true)
        assertEquals(StickShaper.DEADZONE_MAX, t.leftDeadzone, 0f)
        assertEquals(StickShaper.SENSITIVITY_MIN, t.leftSensitivity, 0f)
        assertEquals(StickShaper.DEADZONE_MIN, t.rightDeadzone, 0f)
        assertEquals(StickShaper.SENSITIVITY_MAX, t.rightSensitivity, 0f)
    }

    @Test
    fun parsingFallsBackOnMissingOrBadText() {
        assertEquals(0.3f, StickShaper.parseDeadzone(null, 0.3f), 0f)
        assertEquals(0.3f, StickShaper.parseDeadzone("", 0.3f), 0f)
        assertEquals(0.3f, StickShaper.parseDeadzone("abc", 0.3f), 0f)
        assertEquals(0.5f, StickShaper.parseDeadzone("9999", 0.3f), 0f)
        assertEquals(0.25f, StickShaper.parseDeadzone("0.25", 0.3f), 0f)
        assertEquals(1.5f, StickShaper.parseSensitivity("1.5", 1f), 0f)
        assertEquals(1f, StickShaper.parseSensitivity("x", 1f), 0f)
        assertTrue(StickShaper.parseSmoothEdge("1", false))
        assertFalse(StickShaper.parseSmoothEdge("0", true))
        assertTrue(StickShaper.parseSmoothEdge("", true))
        assertFalse(StickShaper.parseSmoothEdge(null, false))
    }

    @Test
    fun activeTuning_defaultsAndResets() {
        StickShaper.reset()
        assertEquals(StickShaper.DEFAULT, StickShaper.getActive())
        val custom = StickShaper.Tuning(0.05f, 1.2f, 0.25f, 0.8f, true)
        StickShaper.setActive(custom)
        assertEquals(custom, StickShaper.getActive())
        StickShaper.setActive(null)
        assertEquals(StickShaper.DEFAULT, StickShaper.getActive())
    }
}
