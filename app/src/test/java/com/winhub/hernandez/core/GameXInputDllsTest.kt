package com.winhub.hernandez.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GameXInputDllsTest {

    @get:Rule val tmp = TemporaryFolder()

    private val all = "xinput1_1,xinput1_2,xinput1_3,xinput1_4,xinput9_1_0,xinputuap"

    @Test fun nothingShippedIsAllOurs() {
        val o = GameXInputDlls.overrides("", emptySet())
        assertEquals("$all=n,b", o.value)
        assertTrue(o.builtin.isEmpty())
    }

    @Test fun shippedNameGetsWineBuiltin() {
        val o = GameXInputDlls.overrides("", setOf("xinput1_3"))
        assertEquals("xinput1_1,xinput1_2,xinput1_4,xinput9_1_0,xinputuap=n,b;xinput1_3=b", o.value)
        assertEquals(listOf("xinput1_3"), o.builtin)
    }

    @Test fun usersPreferGameFolderEntryIsKept() {
        // "Prefer game-folder DLLs" on for xinput1_3: the game's copy loads, the rest stay ours.
        val o = GameXInputDlls.overrides("version=n,b;xinput1_3=n,b", setOf("xinput1_3"))
        assertEquals("version=n,b;xinput1_3=n,b;xinput1_1,xinput1_2,xinput1_4,xinput9_1_0,xinputuap=n,b", o.value)
        assertEquals(listOf("xinput1_3"), o.kept)
        assertTrue(o.builtin.isEmpty())
        assertTrue(GameXInputDlls.nativeFirst(GameXInputDlls.named("version=n,b;xinput1_3=n,b")["xinput1_3"]))
    }

    @Test fun groupedAndDllSuffixedUserEntriesCount() {
        val named = GameXInputDlls.named("XInput1_4.dll,dinput8=b;xinputuap")
        assertEquals("b", named["xinput1_4"])
        assertEquals("", named["xinputuap"])
        assertFalse(GameXInputDlls.nativeFirst("b,n"))
        assertTrue(GameXInputDlls.nativeFirst("native,builtin"))
    }

    @Test fun findsGameCopiesInAnyCaseButNotOurs() {
        val game = tmp.newFolder("game")
        File(game, "XInput1_3.DLL").writeText("microsoft")
        File(game, "xinput1_4.dll").writeText("ours")
        File(game, "readme.txt").writeText("x")
        val ourSha = GameXInputDlls.sha256(File(game, "xinput1_4.dll"))
        val found = GameXInputDlls.findShipped(listOf(game, File(game, "missing")), setOf(ourSha))
        assertEquals(setOf("xinput1_3"), found.keys)
        assertEquals("XInput1_3.DLL", found["xinput1_3"]!!.name)
    }

    @Test fun moveAsideAndRestore() {
        val game = tmp.newFolder("game")
        val manifest = File(tmp.root, "state/moved.properties")
        val dll = File(game, "xinput1_3.dll").apply { writeText("microsoft") }
        val log = mutableListOf<String>()
        assertTrue(GameXInputDlls.moveAside(manifest, dll, log))
        assertFalse(dll.exists())
        assertTrue(File(game, "xinput1_3.dll.hermes-disabled").isFile)
        assertTrue(manifest.isFile)
        GameXInputDlls.restoreAll(manifest)
        assertEquals("microsoft", dll.readText())
        assertFalse(File(game, "xinput1_3.dll.hermes-disabled").exists())
        assertFalse(manifest.exists())
        // Nothing recorded: nothing to do.
        assertTrue(GameXInputDlls.restoreAll(manifest).isEmpty())
    }

    @Test fun anExistingDisabledCopyIsNeverOverwritten() {
        val game = tmp.newFolder("game")
        val manifest = File(tmp.root, "moved.properties")
        val dll = File(game, "xinput1_3.dll").apply { writeText("new") }
        val old = File(game, "xinput1_3.dll.hermes-disabled").apply { writeText("old") }
        assertFalse(GameXInputDlls.moveAside(manifest, dll, mutableListOf()))
        assertEquals("new", dll.readText())
        assertEquals("old", old.readText())
        assertFalse(manifest.exists())
    }

    @Test fun restoreLeavesBothWhenTheOriginalCameBack() {
        val game = tmp.newFolder("game")
        val manifest = File(tmp.root, "moved.properties")
        val dll = File(game, "xinput1_3.dll").apply { writeText("v1") }
        assertTrue(GameXInputDlls.moveAside(manifest, dll, mutableListOf()))
        dll.writeText("v2") // the game was repaired mid-session
        GameXInputDlls.restoreAll(manifest)
        assertEquals("v2", dll.readText())
        assertEquals("v1", File(game, "xinput1_3.dll.hermes-disabled").readText())
        assertFalse(manifest.exists())
    }
}
