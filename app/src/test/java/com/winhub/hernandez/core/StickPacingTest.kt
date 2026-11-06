package com.winhub.hernandez.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickPacingTest {

    @Test fun parseAndFormat() {
        assertEquals("auto:60", StickPacing.parse("auto:60")!!.format())
        assertEquals("auto:60", StickPacing.parse("auto")!!.format())
        assertEquals("fixed:125", StickPacing.parse("fixed")!!.format())
        assertEquals("fixed:90", StickPacing.parse(" Fixed:90 ")!!.format())
        assertEquals("off", StickPacing.parse("off")!!.format())
        assertNull(StickPacing.parse(null))
        assertNull(StickPacing.parse(""))
        assertNull(StickPacing.parse("auto:x"))
        assertNull(StickPacing.parse("sometimes"))
    }

    @Test fun clamps() {
        assertEquals(240, StickPacing.parse("auto:999")!!.hz)
        assertEquals(20, StickPacing.parse("auto:1")!!.hz)
        assertEquals(1000, StickPacing.parse("fixed:5000")!!.hz)
        assertEquals(30, StickPacing.parse("fixed:2")!!.hz)
        assertEquals(StickPacing.DEFAULT, StickPacing.parse("auto:0"))
    }

    @Test fun stepsAutoInTens() {
        val p = StickPacing.parse("auto:60")!!
        assertEquals("auto:70", p.step(1).format())
        assertEquals("auto:50", p.step(-1).format())
        assertEquals("auto:70", StickPacing.parse("auto:61")!!.step(1).format())
        assertEquals("auto:60", StickPacing.parse("auto:61")!!.step(-1).format())
        assertEquals("auto:160", StickPacing.parse("auto:168")!!.step(-1).format())
        val low = StickPacing.parse("auto:20")!!
        assertEquals(low, low.step(-1))
        val high = StickPacing.parse("auto:240")!!
        assertEquals(high, high.step(1))
    }

    @Test fun stepsFixedAlongTheLadder() {
        assertEquals("fixed:150", StickPacing.parse("fixed:125")!!.step(1).format())
        assertEquals("fixed:100", StickPacing.parse("fixed:125")!!.step(-1).format())
        assertEquals("fixed:40", StickPacing.parse("fixed:33")!!.step(1).format())
        assertEquals("fixed:30", StickPacing.parse("fixed:33")!!.step(-1).format())
        val top = StickPacing.parse("fixed:1000")!!
        assertEquals(top, top.step(1))
        val off = StickPacing.parse("off")!!
        assertEquals(off, off.step(1))
        assertEquals("", off.rateLabel())
        assertEquals("Up to 60 per second", StickPacing.DEFAULT.rateLabel())
        assertEquals("125 per second", StickPacing.parse("fixed")!!.rateLabel())
    }
}
