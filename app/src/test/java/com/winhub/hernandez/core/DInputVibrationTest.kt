package com.winhub.hernandez.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DInputVibrationTest {

    @Test fun containerDefaultsOn() {
        assertTrue(DInputVibration.containerOn(null))
        assertTrue(DInputVibration.containerOn(""))
        assertTrue(DInputVibration.containerOn("1"))
        assertFalse(DInputVibration.containerOn("0"))
        assertFalse(DInputVibration.containerOn(" 0 "))
        assertTrue(DInputVibration.containerOn("junk"))
    }

    @Test fun containerStoresOnlyOff() {
        assertNull(DInputVibration.containerExtra(true))
        assertEquals("0", DInputVibration.containerExtra(false))
        assertTrue(DInputVibration.containerOn(DInputVibration.containerExtra(true)))
        assertFalse(DInputVibration.containerOn(DInputVibration.containerExtra(false)))
    }

    @Test fun gameOverridesContainer() {
        assertTrue(DInputVibration.requested(null, null))
        assertFalse(DInputVibration.requested("0", null))
        assertFalse(DInputVibration.requested("0", ""))
        assertTrue(DInputVibration.requested("0", "1"))
        assertFalse(DInputVibration.requested("1", "0"))
        assertFalse(DInputVibration.requested(null, "0"))
        // Anything but "1" / "0" on the game follows the container.
        assertFalse(DInputVibration.requested("0", "junk"))
        assertTrue(DInputVibration.requested(null, "junk"))
    }

    @Test fun envValueGameFirstThenContainer() {
        assertNull(DInputVibration.envValue(null, null))
        assertNull(DInputVibration.envValue("", "WINEDEBUG=-all"))
        assertEquals(false, DInputVibration.envValue("HERMES_DINPUT_FF=0", null))
        assertEquals(true, DInputVibration.envValue(null, "WINEDEBUG=-all HERMES_DINPUT_FF=1"))
        // The launch merges the game's env vars over the container's: the game's value is what the guest gets.
        assertEquals(false, DInputVibration.envValue("HERMES_DINPUT_FF=0", "HERMES_DINPUT_FF=1"))
        assertEquals(true, DInputVibration.envValue("HERMES_DINPUT_FF=1", "HERMES_DINPUT_FF=0"))
        // A value other than 1 / 0 decides nothing (the guest goes by the setting), even over the container's.
        assertNull(DInputVibration.envValue("HERMES_DINPUT_FF=yes", null))
        assertNull(DInputVibration.envValue("HERMES_DINPUT_FF=yes", "HERMES_DINPUT_FF=0"))
        // Another variable with the name as a prefix is not it.
        assertNull(DInputVibration.envValue("HERMES_DINPUT_FF_X=0", null))
    }

    @Test fun envWinsOverGameAndContainer() {
        assertTrue(DInputVibration.effective(null, null, null, null))
        assertFalse(DInputVibration.effective(null, null, "HERMES_DINPUT_FF=0", null))
        assertFalse(DInputVibration.effective("1", "1", null, "HERMES_DINPUT_FF=0"))
        assertTrue(DInputVibration.effective("0", "0", "HERMES_DINPUT_FF=1", null))
        assertTrue(DInputVibration.effective("0", "1", null, null))
        assertFalse(DInputVibration.effective("1", "0", null, null))
        assertFalse(DInputVibration.effective("0", "", "HERMES_DINPUT_FF=maybe", null))
    }

    @Test fun controlBlockMode() {
        assertEquals(DInputVibration.FF_MODE_ON, DInputVibration.ffMode(true))
        assertEquals(DInputVibration.FF_MODE_OFF, DInputVibration.ffMode(false))
        assertEquals(1, DInputVibration.FF_MODE_ON)
        assertEquals(2, DInputVibration.FF_MODE_OFF)
        assertEquals(0, DInputVibration.FF_MODE_UNSET)
    }

    @Test fun normalize() {
        assertEquals("1", DInputVibration.normalize(" 1"))
        assertEquals("0", DInputVibration.normalize("0"))
        assertNull(DInputVibration.normalize("true"))
        assertNull(DInputVibration.normalize(null))
    }
}
