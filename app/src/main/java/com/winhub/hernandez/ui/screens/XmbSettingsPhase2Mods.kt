package com.winhub.hernandez.ui.screens

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.mutableStateMapOf
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.GameModCache
import com.winhub.hernandez.core.GameMods
import com.winhub.hernandez.core.StartPrograms
import com.winhub.hernandez.core.WinePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "XmbMods"

private const val START_PROGRAMS_NOTE =
    "These start with the game, top to bottom. The first waits from the game launching; each next one " +
        "waits after the one before it starts."

/**
 * XMB twin of the shortcut editor's Mods tab: the mods this game's EXE can take (core.GameMods) with
 * Special K's DLL name, then the programs started with the game (core.StartPrograms). Every change is
 * written to the shortcut's extras straight away, with the same encoding the editor saves.
 */
internal fun xmbModsMenu(xmb: XmbScope, shortcut: Shortcut): XmbMenu {
    val exe = runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path) }.getOrNull()
    val machine = GameMods.peMachine(exe)
    val mods = GameMods.ALL.filter { GameMods.supports(it, shortcut.path, machine) }
    val imports = if (GameMods.SPECIAL_K in mods) GameMods.peImports(exe) else emptySet()
    // Per mod id: "" or a short download state, read by the rows so they redraw when it changes.
    val status = mutableStateMapOf<String, String>()

    fun download(mod: GameMods.Mod) {
        val asset = GameMods.asset(mod, machine) ?: return
        val filesDir = xmb.context.filesDir
        if (GameModCache.cached(filesDir, asset) != null) { status[mod.id] = ""; return }
        status[mod.id] = "downloading"
        xmb.scope.launch {
            status[mod.id] = withContext(Dispatchers.IO) {
                try {
                    GameModCache.get(filesDir, asset) { _ -> }
                    "ready"
                } catch (e: Exception) {
                    Log.w(TAG, "download of ${mod.name} failed", e)
                    "download failed"
                }
            }
        }
    }

    return XmbMenu("Mods", Icons.Filled.Apps) {
        val rows = mutableListOf<XmbRow>()
        rows += XmbRow.Header("hMods", "Game mods")
        if (mods.isEmpty()) {
            rows += XmbRow.Info("noMods", "No mods for this game", Icons.Filled.Info,
                subtitle = "REFramework is for RE Engine games and Special K for 32 and 64-bit Windows games. " +
                    "This game's EXE can't take either.")
        }
        for (mod in mods) {
            val on = shortcut.getExtra(mod.extra) == "1"
            val hint = when (status[mod.id].orEmpty()) {
                "" -> "Downloaded the first time it is switched on"
                "ready" -> "Downloaded and ready"
                "download failed" -> "Download failed. Check your connection and switch it off and on to retry"
                else -> "Downloading…"
            }
            rows += XmbRow.Toggle("mod:${mod.id}", mod.name, Icons.Filled.Extension, on, subtitle = hint) { checked ->
                p2Put(xmb, shortcut, mod.extra, if (checked) "1" else null)
                if (checked) download(mod)
            }
            if (mod === GameMods.SPECIAL_K && on) {
                val auto = GameMods.targetDll(mod, "", imports, machine) + " (auto)"
                val stored = shortcut.getExtra(GameMods.EXTRA_SPECIALK_DLL) ?: ""
                rows += XmbRow.Choice("skDll", "Special K DLL name", Icons.Filled.Extension,
                    listOf(auto) + GameMods.SPECIALK_DLLS, stored.ifEmpty { auto },
                    subtitle = "The name it loads as in the game folder") { v ->
                    p2Put(xmb, shortcut, GameMods.EXTRA_SPECIALK_DLL, if (v == auto) null else v)
                }
            }
        }

        rows += XmbRow.Header("hProg", "Start with the game")
        val list = StartPrograms.parse(shortcut.getExtra(StartPrograms.EXTRA, ""))
        rows += XmbRow.Link("addProg", "Add program", Icons.Filled.Add, subtitle = START_PROGRAMS_NOTE) {
            xmbAddProgramMenu(xmb, shortcut)
        }
        list.forEachIndexed { i, e ->
            rows += XmbRow.Link("prog:$i", programName(e.path), Icons.Filled.Apps,
                value = delayLabel(e.delaySec), subtitle = e.path) { xmbProgramMenu(xmb, shortcut, i) }
        }
        rows
    }
}

private fun saveProgramList(xmb: XmbScope, shortcut: Shortcut, list: List<StartPrograms.Entry>) =
    p2Put(xmb, shortcut, StartPrograms.EXTRA, StartPrograms.encode(list))

private fun xmbAddProgramMenu(xmb: XmbScope, shortcut: Shortcut): XmbMenu =
    XmbMenu("Add program", Icons.Filled.Add) {
        listOf(XmbRow.Text("path", "Windows path", Icons.Filled.Add, "",
            subtitle = "e.g. F:\\Trainers\\Trainer.exe", placeholder = "F:\\Trainers\\Trainer.exe") { raw ->
            val path = raw.trim()
            if (path.isEmpty()) {
                xmb.toast("Enter a Windows path")
            } else {
                val list = StartPrograms.parse(shortcut.getExtra(StartPrograms.EXTRA, ""))
                // Same defaults as the editor: the first waits for the game to be running, the rest follow it.
                val delay = if (list.isEmpty()) StartPrograms.DEFAULT_DELAY_SEC else 5
                saveProgramList(xmb, shortcut, list + StartPrograms.Entry(path, delay))
                xmb.pop()
            }
        })
    }

/** One program: its path, the wait before it starts, its place in the order, and Remove. */
private fun xmbProgramMenu(xmb: XmbScope, shortcut: Shortcut, index: Int): XmbMenu =
    XmbMenu("Program ${index + 1}", Icons.Filled.Apps) {
        val list = StartPrograms.parse(shortcut.getExtra(StartPrograms.EXTRA, ""))
        val cur = list.getOrNull(index)
        if (cur == null) emptyList() else programRows(xmb, shortcut, list, index, cur)
    }

private fun programRows(
    xmb: XmbScope, shortcut: Shortcut, list: List<StartPrograms.Entry>, index: Int, cur: StartPrograms.Entry,
): List<XmbRow> {
    fun replace(e: StartPrograms.Entry) =
        saveProgramList(xmb, shortcut, list.toMutableList().also { it[index] = e })
    // A move or removal changes what this index means, so those go back to the list.
    fun reorder(to: Int) {
        val moved = list.toMutableList()
        moved.add(to, moved.removeAt(index))
        saveProgramList(xmb, shortcut, moved)
        xmb.pop()
    }

    val rows = mutableListOf<XmbRow>()
    rows += XmbRow.Text("path", "Windows path", Icons.Filled.Apps, cur.path,
        subtitle = "e.g. F:\\Trainers\\Trainer.exe") { raw ->
        if (raw.isBlank()) xmb.toast("Enter a Windows path") else replace(StartPrograms.Entry(raw.trim(), cur.delaySec))
    }
    val delays = delayOptions(cur.delaySec.toString())
    rows += XmbRow.Choice("delay", "Wait before starting", Icons.Filled.Timer,
        delays.map { it.second }, delayLabel(cur.delaySec),
        subtitle = if (index == 0) "After the game launches" else "After the previous program starts") { v ->
        delays.firstOrNull { it.second == v }?.first?.toIntOrNull()
            ?.let { replace(StartPrograms.Entry(cur.path, it)) }
    }
    if (index > 0) rows += XmbRow.Action("up", "Move up", Icons.Filled.ArrowUpward) { reorder(index - 1) }
    if (index < list.size - 1) rows += XmbRow.Action("down", "Move down", Icons.Filled.ArrowDownward) { reorder(index + 1) }
    rows += XmbRow.Action("remove", "Remove", Icons.Filled.Delete, danger = true) {
        saveProgramList(xmb, shortcut, list.filterIndexed { i, _ -> i != index })
        xmb.pop()
    }
    return rows
}
