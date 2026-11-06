package com.winhub.hernandez.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerIdentityTest {

    @Test fun allSdlMappingsCoverEveryMappedWineIdentity() {
        val lines = ControllerIdentity.allSdlMappings().split('\n')
        val mapped = ControllerIdentity.wineChoices().map { it.value }
            .filter { it != ControllerIdentity.GENERIC && it != ControllerIdentity.DINPUT_PAD }
        assertEquals(mapped.size, lines.size)
        for (value in mapped) {
            val id = ControllerIdentity.find(value)!!
            val vid = String.format("%02x%02x", id.vendor and 0xff, id.vendor shr 8)
            val pid = String.format("%02x%02x", id.product and 0xff, id.product shr 8)
            val guid = "03000000${vid}0000${pid}000010010000"
            assertTrue("no line for $value", lines.any { it.startsWith("$guid,*,") })
            assertTrue(ControllerIdentity.hasSdlMapping(value))
        }
        // Never for the generic pad (SDL maps it itself), the DirectInput pad or the Linux-only one.
        assertFalse(ControllerIdentity.hasSdlMapping(ControllerIdentity.GENERIC))
        assertFalse(ControllerIdentity.hasSdlMapping(ControllerIdentity.DINPUT_PAD))
        assertFalse(ControllerIdentity.hasSdlMapping(ControllerIdentity.STEAM_VIRTUAL))
        assertTrue(lines.any { it.startsWith("030000005e0400008e02000010010000,*,a:b0,b:b1,") })
        lines.forEach { assertTrue(it, it.endsWith("lefttrigger:a5,righttrigger:a4,")) }
    }

    @Test fun launchEnvForEveryWineLaunch() {
        val generic = ControllerIdentity.launchEnv(ControllerIdentity.GENERIC)
        assertNull(generic[ControllerIdentity.ENV])
        assertEquals("0", generic["SDL_JOYSTICK_HIDAPI_PS4"])
        assertEquals("0", generic["SDL_JOYSTICK_HIDAPI_STEAMDECK"])
        assertNull(generic["SDL_JOYSTICK_HIDAPI"])
        assertEquals(ControllerIdentity.allSdlMappings(), generic["SDL_GAMECONTROLLERCONFIG"])

        val xbox = ControllerIdentity.launchEnv(ControllerIdentity.XBOX_360)
        assertEquals("xbox360", xbox[ControllerIdentity.ENV])
        assertNull(xbox["SDL_JOYSTICK_HIDAPI"])

        val ds4 = ControllerIdentity.launchEnv(ControllerIdentity.DS4)
        assertEquals("0", ds4["SDL_JOYSTICK_HIDAPI"])
        assertEquals("0x054C/0x09CC", ds4["PROTON_DISABLE_HIDRAW"])
    }

    @Test fun userValuesWin() {
        assertEquals("1", ControllerIdentity.mergeEnv("SDL_JOYSTICK_HIDAPI_PS4", "0", "1"))
        val merged = ControllerIdentity.mergeEnv("SDL_GAMECONTROLLERCONFIG", "ours", "theirs")
        assertTrue(merged.indexOf("ours") < merged.indexOf("theirs")) // SDL: the later line wins
        assertEquals("1", ControllerIdentity.mergeEnv("PROTON_DISABLE_HIDRAW", "0x054C/0x09CC", "1"))
    }

    @Test fun genericKeepsTodaysEightRegistryNames() {
        assertEquals(
            listOf("Generic HID Gamepad 0", "ric HID Gamepad 0", "Generic HID Gamepad 1", "ric HID Gamepad 1",
                "Generic HID Gamepad 2", "ric HID Gamepad 2", "Generic HID Gamepad 3", "ric HID Gamepad 3"),
            ControllerIdentity.dinputNames(ControllerIdentity.GENERIC))
    }

    @Test fun slotIdentity() {
        assertEquals(0x1236, ControllerIdentity.slotVendor(ControllerIdentity.GENERIC, 2))
        assertEquals(0x567A, ControllerIdentity.slotProduct(ControllerIdentity.GENERIC, 2))
        assertEquals("Generic HID Gamepad 2", ControllerIdentity.slotName(ControllerIdentity.GENERIC, 2))
        assertEquals(0x045E, ControllerIdentity.slotVendor(ControllerIdentity.XBOX_ONE, 3))
        assertEquals("Xbox One S Controller (3)", ControllerIdentity.slotName(ControllerIdentity.XBOX_ONE, 3))
    }
}
