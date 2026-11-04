package com.winhub.hernandez.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class GameModsTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    private fun dll(name: String, content: String): Pair<File, String> {
        val f = File(tmp.root, name).apply { writeText(content) }
        return f to sha(f)
    }

    /** A minimal PE: one section holding the import descriptors and their DLL names. */
    private fun pe(machine: Int, pe64: Boolean, vararg dlls: String): File {
        val b = ByteArray(0x1000)
        fun put16(o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte() }
        fun put32(o: Int, v: Int) { put16(o, v); put16(o + 2, v shr 16) }
        b[0] = 'M'.code.toByte(); b[1] = 'Z'.code.toByte(); b[0x3c] = 0x80.toByte()
        val e = 0x80
        b[e] = 'P'.code.toByte(); b[e + 1] = 'E'.code.toByte()
        put16(e + 4, machine); put16(e + 6, 1)
        val optSize = if (pe64) 0xF0 else 0xE0
        put16(e + 20, optSize)
        val opt = e + 24
        put16(opt, if (pe64) 0x20b else 0x10b)
        put32(opt + (if (pe64) 112 else 96) + 8, 0x2000)
        val sec = opt + optSize
        put32(sec + 8, 0x800); put32(sec + 12, 0x2000); put32(sec + 16, 0x800); put32(sec + 20, 0x400)
        dlls.forEachIndexed { i, name ->
            put32(0x400 + i * 20 + 12, 0x2000 + 0x100 + i * 0x20)
            name.toByteArray().copyInto(b, 0x500 + i * 0x20)
        }
        return File(tmp.root, "game-$machine-${dlls.size}.exe").apply { writeBytes(b) }
    }

    @Test fun specialKNameFromChoiceImportsAndMachine() {
        val sk = GameMods.SPECIAL_K
        assertEquals("dinput8.dll", GameMods.targetDll(GameMods.REFRAMEWORK, "d3d9.dll", setOf("d3d9.dll"), GameMods.MACHINE_X64))
        assertEquals("dxgi.dll", GameMods.targetDll(sk, "", setOf("kernel32.dll"), GameMods.MACHINE_X64))
        assertEquals("d3d9.dll", GameMods.targetDll(sk, "", setOf("kernel32.dll"), GameMods.MACHINE_X86))
        assertEquals("d3d9.dll", GameMods.targetDll(sk, "", setOf("d3d9.dll"), GameMods.MACHINE_X64))
        assertEquals("dxgi.dll", GameMods.targetDll(sk, null, setOf("d3d9.dll", "dxgi.dll"), GameMods.MACHINE_X86))
        assertEquals("d3d8.dll", GameMods.targetDll(sk, "", setOf("d3d8.dll"), GameMods.MACHINE_X86))
        assertEquals("opengl32.dll", GameMods.targetDll(sk, "", setOf("opengl32.dll"), GameMods.MACHINE_X64))
        assertEquals("d3d11.dll", GameMods.targetDll(sk, "D3D11.dll", setOf("d3d9.dll"), GameMods.MACHINE_X86))
        assertEquals("dxgi.dll", GameMods.targetDll(sk, "bogus.dll", emptySet(), GameMods.MACHINE_X64))
    }

    @Test fun overridesAddTheDllUnlessTheUserNamesIt() {
        assertEquals("dxgi=n,b", GameMods.overrides("", "dxgi.dll"))
        assertEquals("d3d9=n,b", GameMods.overrides(null, "D3D9.dll"))
        assertEquals("dinput8=n,b;dxgi=n,b", GameMods.overrides("dinput8=n,b", "dxgi.dll"))
        assertEquals("dxgi=b", GameMods.overrides("dxgi=b", "dxgi.dll"))
        assertEquals("version,dinput8.dll=n,b", GameMods.overrides("version,dinput8.dll=n,b", "dinput8.dll"))
    }

    @Test fun whichModsAGameCanTake() {
        assertTrue(GameMods.supports(GameMods.REFRAMEWORK, "E:\\games\\RE2\\re2.exe", GameMods.MACHINE_X64))
        assertFalse(GameMods.supports(GameMods.REFRAMEWORK, "E:\\games\\RE2\\re2.exe", GameMods.MACHINE_X86))
        assertFalse(GameMods.supports(GameMods.REFRAMEWORK, "E:\\games\\W3\\witcher3.exe", GameMods.MACHINE_X64))
        assertTrue(GameMods.supports(GameMods.SPECIAL_K, "E:\\games\\W3\\witcher3.exe", GameMods.MACHINE_X64))
        assertTrue(GameMods.supports(GameMods.SPECIAL_K, "E:\\games\\P\\prototypef.exe", GameMods.MACHINE_X86))
        assertFalse(GameMods.supports(GameMods.SPECIAL_K, "E:\\games\\x.exe", 0))
        assertTrue(GameMods.asset(GameMods.SPECIAL_K, GameMods.MACHINE_X86)!!.url.endsWith("/SpecialK32.dll"))
        assertEquals(GameMods.SPECIAL_K, GameMods.byId("specialk"))
        assertEquals("arm64ec-r2", GameMods.asset(GameMods.SPECIAL_K, GameMods.MACHINE_X64)!!.tag())
        assertEquals("arm64ec-r2", GameMods.asset(GameMods.REFRAMEWORK, GameMods.MACHINE_X64)!!.tag())
    }

    @Test fun exeMachineAndImports() {
        val x64 = pe(GameMods.MACHINE_X64, true, "KERNEL32.dll", "d3d11.dll")
        val x86 = pe(GameMods.MACHINE_X86, false, "d3d9.dll", "USER32.dll")
        assertEquals(GameMods.MACHINE_X64, GameMods.peMachine(x64))
        assertEquals(GameMods.MACHINE_X86, GameMods.peMachine(x86))
        assertEquals(setOf("kernel32.dll", "d3d11.dll"), GameMods.peImports(x64))
        assertEquals(setOf("d3d9.dll", "user32.dll"), GameMods.peImports(x86))
        assertEquals(0, GameMods.peMachine(File(tmp.root, "text.exe").apply { writeText("not a PE") }))
        assertEquals(emptySet<String>(), GameMods.peImports(File(tmp.root, "missing.exe")))
    }

    @Test fun placeUpdateRenameAndRemove() {
        val dir = tmp.newFolder("game")
        val (v1, s1) = dll("v1.dll", "Special K one")
        val (v2, s2) = dll("v2.dll", "Special K two")
        val sk = GameMods.SPECIAL_K
        val r = GameMods.install(dir, sk, "dxgi.dll", v1, s1)
        assertTrue(r.ours); assertTrue(r.present)
        assertEquals("dxgi.dll placed", r.note)
        assertEquals("$s1 dxgi.dll", File(dir, sk.marker).readText())
        assertTrue(GameMods.inPlace(dir, "dxgi.dll", s1))
        assertEquals("dxgi.dll already in place", GameMods.install(dir, sk, "dxgi.dll", null, s1).note)
        assertEquals("dxgi.dll updated", GameMods.install(dir, sk, "dxgi.dll", v2, s2).note)
        assertEquals("d3d9.dll placed", GameMods.install(dir, sk, "d3d9.dll", v2, s2).note)
        assertFalse(File(dir, "dxgi.dll").exists())
        assertEquals("removed our d3d9.dll", GameMods.remove(dir, sk))
        assertNull(GameMods.remove(dir, sk))
    }

    @Test fun theGamesOwnFileIsNeverTouched() {
        val dir = tmp.newFolder("game")
        File(dir, "dxgi.dll").writeText("reshade")
        val (v1, s1) = dll("v1.dll", "Special K one")
        val r = GameMods.install(dir, GameMods.SPECIAL_K, "dxgi.dll", v1, s1)
        assertFalse(r.ours); assertTrue(r.present)
        assertEquals("reshade", File(dir, "dxgi.dll").readText())
        assertNull(GameMods.remove(dir, GameMods.SPECIAL_K))
        val empty = tmp.newFolder("empty")
        val none = GameMods.install(empty, GameMods.SPECIAL_K, "dxgi.dll", null, s1)
        assertFalse(none.ours); assertFalse(none.present)
    }

    @Test fun reframeworkKeepsItsOneFieldMarkerNextToSpecialK() {
        val dir = tmp.newFolder("game")
        val (v1, s1) = dll("v1.dll", "REFramework")
        val (v2, s2) = dll("v2.dll", "Special K")
        GameMods.install(dir, GameMods.REFRAMEWORK, "dinput8.dll", v1, s1)
        assertEquals(s1, File(dir, ReFramework.MARKER).readText())
        GameMods.install(dir, GameMods.SPECIAL_K, "dxgi.dll", v2, s2)
        assertEquals("removed our dxgi.dll", GameMods.remove(dir, GameMods.SPECIAL_K))
        assertTrue(File(dir, "dinput8.dll").exists())
        assertEquals("removed our dinput8.dll", GameMods.remove(dir, GameMods.REFRAMEWORK))
    }
}
