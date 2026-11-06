package com.winhub.hernandez.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickRegistryTest {

    @Test fun genericHiddenIsTodaysEntries() {
        val d = JoystickRegistry.desired(ControllerIdentity.GENERIC, false)
        assertEquals(8, d.set.size)
        assertTrue(d.set.values.all { it == "disabled" })
        assertFalse(d.delete.any { it.startsWith("Generic HID Gamepad") })
        assertTrue(d.delete.contains("Xbox 360 Controller (0)"))
        assertTrue(d.delete.contains("Controller (XBOX 360 For Windows)"))
    }

    @Test fun visibleWritesNoEntry() {
        // The old "Exclusive Input off": every known name deleted, none set, so Wine's DirectInput
        // opens the pad's own HID gamepad device ("override" would swap in its XInput HID device).
        val d = JoystickRegistry.desired(ControllerIdentity.XBOX_360, true)
        assertTrue(d.set.isEmpty())
        assertEquals(ControllerIdentity.allDinputNames().size, d.delete.size)
        assertTrue(d.delete.contains("Controller (XBOX 360 For Windows)"))
        assertTrue(d.delete.contains("Generic HID Gamepad 0"))
    }

    @Test fun forcedVisibleIsOverrideAndSharedNamesAreKept() {
        val d = JoystickRegistry.desired(ControllerIdentity.XBOX_360, true, true)
        assertEquals("override", d.set["Controller (XBOX 360 For Windows)"])
        assertFalse(d.delete.contains("Controller (XBOX 360 For Windows)"))
        assertTrue(d.delete.contains("Generic HID Gamepad 0"))
        // Every known name is either set or deleted, never both.
        val all = ControllerIdentity.allDinputNames()
        assertEquals(all.size, d.set.size + d.delete.size)
        d.delete.forEach { assertFalse(d.set.containsKey(it)) }
    }

    @Test fun regFileFormat() {
        val reg = JoystickRegistry.toRegFile(JoystickRegistry.desired(ControllerIdentity.GENERIC, true, true))
        assertTrue(reg.startsWith("REGEDIT4\r\n\r\n[HKEY_CURRENT_USER\\Software\\Wine\\DirectInput\\Joysticks]\r\n"))
        assertTrue(reg.contains("\"Xbox 360 Controller (0)\"=-\r\n"))
        assertTrue(reg.contains("\"Generic HID Gamepad 0\"=\"override\"\r\n"))
        // Deletions come before the values.
        assertTrue(reg.indexOf("=-") < reg.indexOf("=\"override\""))
        assertFalse(reg.replace("\r\n", "").contains("\n"))
        // Visible: deletions only, which regedit applies live as well.
        val none = JoystickRegistry.toRegFile(JoystickRegistry.desired(ControllerIdentity.GENERIC, true))
        assertTrue(none.contains("\"Generic HID Gamepad 0\"=-\r\n"))
        assertFalse(none.contains("=\""))
    }

    @Test fun escaping() {
        assertEquals("a\\\\b\\\"c", JoystickRegistry.escape("a\\b\"c"))
        assertEquals("Controller (XBOX 360 For Windows)", JoystickRegistry.escape("Controller (XBOX 360 For Windows)"))
    }
}
