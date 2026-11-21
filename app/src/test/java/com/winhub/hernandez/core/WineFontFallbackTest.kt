package com.winhub.hernandez.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The file-light half of [WineFontFallback]: when a `user.reg` already has a "Tahoma" font replacement, and when the prefix has the real font. */
class WineFontFallbackTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aPrefixWithTheRealFontIsLeftAloneWithoutReadingTheRegistry() {
        val prefix = tmp.newFolder("prefix")
        val text = "WINE REGISTRY Version 2\n"
        val userReg = File(prefix, "user.reg").apply { writeText(text) }
        assertFalse(WineFontFallback.hasTahomaFont(userReg))
        File(prefix, "drive_c/windows/Fonts").mkdirs()
        File(prefix, "drive_c/windows/Fonts/tahoma.ttf").writeText("x")
        assertTrue(WineFontFallback.hasTahomaFont(userReg))
        // With the font there, the registry is not even opened: false and the file as it was.
        assertFalse(WineFontFallback.ensureTahomaReplacement(userReg))
        assertEquals(text, userReg.readText())
    }

    private val replacementsKey = "[Software\\\\Wine\\\\Fonts\\\\Replacements] 1700000000\n#time=1dc0000000000000\n"

    @Test
    fun tahomaReplacementIsNeededWhenTheRegistryHasNone() {
        assertTrue(WineFontFallback.tahomaReplacementNeeded(""))
        assertTrue(WineFontFallback.tahomaReplacementNeeded("[Software\\\\Wine\\\\Drivers] 1\n\"Audio\"=\"pulse\"\n"))
        // Other replacements stay out of the way: only Tahoma counts.
        assertTrue(WineFontFallback.tahomaReplacementNeeded(replacementsKey + "\"Segoe UI\"=\"Arial\"\n"))
    }

    @Test
    fun tahomaReplacementIsNotNeededOnceOneExists() {
        assertFalse(WineFontFallback.tahomaReplacementNeeded(replacementsKey + "\"Tahoma\"=\"Arial\"\n"))
        // The user's own choice (any data, any case, CRLF) is kept.
        assertFalse(WineFontFallback.tahomaReplacementNeeded(replacementsKey.replace("\n", "\r\n") + "\"tahoma\"=\"DejaVu Sans\"\r\n"))
        assertFalse(WineFontFallback.tahomaReplacementNeeded(
            "[SOFTWARE\\\\Wine\\\\Fonts\\\\Replacements] 1\n\"Tahoma\"=hex(7):41,00,00,00,00,00\n"))
    }

    @Test
    fun tahomaValueInAnotherKeyDoesNotCount() {
        val text = "[Software\\\\Wine\\\\Fonts] 1\n\"Tahoma\"=\"x\"\n\n" + replacementsKey + "\"Segoe UI\"=\"Arial\"\n\n" +
            "[Software\\\\Wine\\\\Fonts\\\\Replacements\\\\Sub] 1\n\"Tahoma\"=\"x\"\n"
        assertTrue(WineFontFallback.tahomaReplacementNeeded(text))
    }

    @Test
    fun theReplacementsKeyInTheMiddleOfAFileIsFound() {
        val text = "WINE REGISTRY Version 2\n\n[Control Panel\\\\Desktop] 1\n\"A\"=\"b\"\n\n" +
            replacementsKey + "\"Tahoma\"=\"Arial\"\n\n[Software\\\\Wine\\\\X11 Driver] 1\n\"C\"=\"d\"\n"
        assertFalse(WineFontFallback.tahomaReplacementNeeded(text))
    }
}
