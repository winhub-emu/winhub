package com.winhub.hernandez.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [UbisoftInstallState]: the real `uplay_install.state` of Assassin's Creed Syndicate (Epic build, copied from a
 * device, `src/test/resources/ubisoft`) and hand-built protobuf for the cases a game folder can throw at the reader.
 */
class UbisoftInstallStateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun real(): ByteArray =
        javaClass.getResourceAsStream("/ubisoft/uplay_install.state")!!.use { it.readBytes() }

    // ---- Tiny protobuf writer -------------------------------------------------------------------

    private fun cat(vararg parts: ByteArray): ByteArray = parts.fold(ByteArray(0)) { a, b -> a + b }

    private fun varint(value: Long): ByteArray {
        val out = ArrayList<Byte>()
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }

    private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
    private fun num(field: Int, v: Long) = cat(tag(field, 0), varint(v))
    private fun str(field: Int, s: String) = bytes(field, s.toByteArray(Charsets.UTF_8))
    private fun bytes(field: Int, b: ByteArray) = cat(tag(field, 2), varint(b.size.toLong()), b)
    private fun entry(path: String, lang: String, value: String) = bytes(15, cat(str(1, path), str(2, lang), str(3, value)))

    // ---- The real file --------------------------------------------------------------------------

    @Test
    fun theRealFileGivesTheProductIdAndTheNames() {
        val s = UbisoftInstallState.parse(real())!!
        assertEquals(1875, s.productId)
        assertEquals("Assassin's Creed Syndicate", s.name)
        assertEquals("Assassin's Creed® Syndicate", s.displayName)
        assertEquals("en-US", s.language)
    }

    @Test
    fun theRealFileGivesOneRegistryValuePerLanguage() {
        val s = UbisoftInstallState.parse(real())!!
        assertEquals(15, s.registry.size)
        val en = s.registry.first { it.language == "en-US" }
        assertEquals("HKEY_LOCAL_MACHINE\\SOFTWARE\\Ubisoft\\Assassin's Creed Syndicate\\Language", en.path)
        assertEquals("English(US)", en.value)
        assertEquals("French(France)", s.registry.first { it.language == "fr-FR" }.value)
    }

    @Test
    fun readTakesAFileAndGivesNullForAMissingOne() {
        val f = tmp.newFile("uplay_install.state").apply { writeBytes(real()) }
        assertEquals(1875, UbisoftInstallState.read(f)!!.productId)
        assertNull(UbisoftInstallState.read(File(tmp.root, "none.state")))
        assertNull(UbisoftInstallState.read(tmp.root))
    }

    @Test
    fun aFileOverTheSizeLimitIsNotRead() {
        val f = tmp.newFile("big.state")
        f.writeBytes(cat(num(28, 1875), ByteArray(UbisoftInstallState.MAX_BYTES)))
        assertNull(UbisoftInstallState.read(f))
    }

    // ---- Damaged and odd files ------------------------------------------------------------------

    @Test
    fun anEmptyFileIsNotAStateFile() {
        assertNull(UbisoftInstallState.parse(ByteArray(0)))
    }

    @Test
    fun aTruncatedFileIsNotUsed() {
        val data = real()
        for (cut in listOf(1, 2, 10, 100, 1000, data.size - 1)) {
            assertNull("cut at $cut", UbisoftInstallState.parse(data.copyOf(cut)))
        }
    }

    @Test
    fun aProductIdOfZeroOrNoneIsNotUsed() {
        assertNull(UbisoftInstallState.parse(cat(str(3, "en-US"), num(28, 0))))
        assertNull(UbisoftInstallState.parse(cat(str(3, "en-US"))))
        assertNull(UbisoftInstallState.parse(num(28, 0x1_0000_0000L)))
        assertNull(UbisoftInstallState.parse(num(28, -1L)))
    }

    @Test
    fun aLengthPastTheEndStopsTheReader() {
        // field 13, length 2^31 - 1, three bytes of payload
        val huge = cat(num(28, 1875), tag(13, 2), varint(0x7FFFFFFFL), byteArrayOf(1, 2, 3))
        assertNull(UbisoftInstallState.parse(huge))
        val negative = cat(num(28, 1875), tag(13, 2), varint(-1L))
        assertNull(UbisoftInstallState.parse(negative))
    }

    @Test
    fun garbageIsNotUsed() {
        assertNull(UbisoftInstallState.parse(ByteArray(64) { 0xFF.toByte() }))
        assertNull(UbisoftInstallState.parse("not a protobuf file at all".toByteArray()))
        // a group wire type and an unknown one
        assertNull(UbisoftInstallState.parse(cat(num(28, 1875), tag(5, 3))))
        assertNull(UbisoftInstallState.parse(cat(num(28, 1875), tag(5, 7))))
        // field number 0
        assertNull(UbisoftInstallState.parse(cat(num(28, 1875), byteArrayOf(0x00, 0x01))))
    }

    @Test
    fun unknownButWellFormedFieldsAreSkipped() {
        val fixed64 = cat(tag(40, 1), ByteArray(8))
        val fixed32 = cat(tag(41, 5), ByteArray(4))
        val s = UbisoftInstallState.parse(cat(fixed64, num(99, 7), fixed32, str(98, "x"), num(28, 42)))!!
        assertEquals(42, s.productId)
        assertEquals("", s.name)
        assertEquals("en-US", s.language)
        assertEquals(emptyList<UbisoftInstallState.RegistryEntry>(), s.registry)
        // a fixed-size field cut short
        assertNull(UbisoftInstallState.parse(cat(num(28, 42), tag(40, 1), ByteArray(3))))
    }

    // ---- Field fallbacks ------------------------------------------------------------------------

    @Test
    fun theNameFallsBackToTheDisplayNameAndBack() {
        val onlyDisplay = UbisoftInstallState.parse(cat(num(28, 7), str(22, "Game® One")))!!
        assertEquals("Game® One", onlyDisplay.name)
        assertEquals("Game® One", onlyDisplay.displayName)
        val onlyName = UbisoftInstallState.parse(cat(num(28, 7), bytes(13, str(1, "Game One"))))!!
        assertEquals("Game One", onlyName.name)
        assertEquals("Game One", onlyName.displayName)
    }

    @Test
    fun aPlainStringInTheNameFieldReadsAsTheName() {
        val s = UbisoftInstallState.parse(cat(num(28, 7), str(13, "Plain Name")))!!
        assertEquals("Plain Name", s.name)
    }

    @Test
    fun theLanguageFallsBackToFieldTwentySixThenEnUs() {
        assertEquals("fr-FR", UbisoftInstallState.parse(cat(num(28, 7), str(3, "fr-FR"), str(26, "de-DE")))!!.language)
        assertEquals("de-DE", UbisoftInstallState.parse(cat(num(28, 7), str(26, "de-DE")))!!.language)
        assertEquals("en-US", UbisoftInstallState.parse(cat(num(28, 7), str(3, "")))!!.language)
    }

    @Test
    fun aRegistryEntryWithoutAPathIsSkippedAndTheRestKept() {
        val s = UbisoftInstallState.parse(
            cat(
                num(28, 7),
                bytes(15, cat(str(2, "en-US"), str(3, "English(US)"))),
                entry("HKEY_LOCAL_MACHINE\\SOFTWARE\\Ubisoft\\G\\Language", "en-US", "English(US)"),
                bytes(15, byteArrayOf(0x0A, 0x7F)),
            ),
        )!!
        assertEquals(1, s.registry.size)
        assertNotNull(s.registry.single().path)
    }

    @Test
    fun aStringWithAControlCharacterIsNotTaken() {
        val s = UbisoftInstallState.parse(cat(num(28, 7), str(22, "Bad\nName"), str(3, "en\r-US")))!!
        assertEquals("", s.name)
        assertEquals("en-US", s.language)
    }

    @Test
    fun theInstallHashIsFieldOneAsTextOrHex() {
        assertEquals("abc", UbisoftInstallState.installHash(byteArrayOf(0x0A, 3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())))
        assertEquals("01ff", UbisoftInstallState.installHash(byteArrayOf(0x0A, 2, 0x01, 0xFF.toByte())))
        assertNull(UbisoftInstallState.installHash(byteArrayOf(0x10, 1)))
    }
}
