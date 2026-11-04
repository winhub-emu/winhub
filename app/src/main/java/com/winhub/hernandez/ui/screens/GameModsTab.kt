package com.winhub.hernandez.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.GameModCache
import com.winhub.hernandez.core.GameMods
import com.winhub.hernandez.core.WinePath
import java.io.File

/**
 * Game settings > Mods: which mods (core.GameMods) this game's EXE can take, each one's per-game switch
 * (off by default) and Special K's DLL name ("" = picked from the EXE). Switching a mod on downloads it
 * from its release if this device does not have it yet.
 */
internal class GameModsSetting(
    val mods: List<GameMods.Mod>, val machine: Int, val imports: Set<String>,
    initialOn: Map<String, Boolean>, initialSkDll: String,
) {
    val on = mutableStateMapOf<String, Boolean>().apply { putAll(initialOn) }
    var skDll by mutableStateOf(initialSkDll)
    /** Per mod id: "" or a short download state ("40 %", "ready", "download failed"). */
    val status = mutableStateMapOf<String, String>()
    private val main = Handler(Looper.getMainLooper())

    fun write(putExtra: (String, String?) -> Unit) {
        for (m in mods) putExtra(m.extra, if (on[m.id] == true) "1" else null)
        if (GameMods.SPECIAL_K in mods) putExtra(GameMods.EXTRA_SPECIALK_DLL, skDll.ifEmpty { null })
    }

    fun download(filesDir: File, mod: GameMods.Mod) {
        val asset = GameMods.asset(mod, machine) ?: return
        if (GameModCache.cached(filesDir, asset) != null) { status[mod.id] = ""; return }
        status[mod.id] = "0 %"
        Thread {
            val done = try {
                GameModCache.get(filesDir, asset) { f -> main.post { status[mod.id] = "${(f * 100).toInt().coerceIn(0, 100)} %" } }
                "ready"
            } catch (e: Exception) {
                "download failed"
            }
            main.post { status[mod.id] = done }
        }.apply { isDaemon = true }.start()
    }
}

@Composable
internal fun rememberGameModsSetting(shortcut: Shortcut): GameModsSetting = remember(shortcut) {
    val exe = runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path) }.getOrNull()
    val machine = GameMods.peMachine(exe)
    val mods = GameMods.ALL.filter { GameMods.supports(it, shortcut.path, machine) }
    val imports = if (GameMods.SPECIAL_K in mods) GameMods.peImports(exe) else emptySet()
    GameModsSetting(
        mods, machine, imports,
        GameMods.ALL.associate { it.id to (shortcut.getExtra(it.extra) == "1") },
        shortcut.getExtra(GameMods.EXTRA_SPECIALK_DLL) ?: "",
    )
}

/** Deck editor's Mods tab: the mods this game can take, then the programs it starts. */
@Composable
internal fun DeckModsTab(mods: GameModsSetting, programs: StartProgramsState) {
    val filesDir = LocalContext.current.filesDir
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DeckGroup("Game mods") {
            if (mods.mods.isEmpty()) {
                DeckRow(
                    label = "No mods for this game",
                    hint = "REFramework is for RE Engine games and Special K for 32 and 64-bit Windows games. " +
                        "This game's EXE can't take either.",
                ) {}
            }
            for (mod in mods.mods) {
                val status = mods.status[mod.id].orEmpty()
                DeckRow(
                    label = mod.name,
                    hint = when (status) {
                        "" -> "Downloaded the first time it is switched on."
                        "ready" -> "Downloaded and ready."
                        "download failed" -> "Download failed. Check your connection and switch it off and on to retry."
                        else -> "Downloading… $status"
                    },
                ) {
                    DeckToggle(checked = mods.on[mod.id] == true) { checked ->
                        mods.on[mod.id] = checked
                        if (checked) mods.download(filesDir, mod)
                    }
                }
                if (mod === GameMods.SPECIAL_K && mods.on[mod.id] == true) {
                    val auto = GameMods.targetDll(mod, "", mods.imports, mods.machine)
                    DeckChoiceRow(
                        label = "Special K DLL name",
                        hint = "The name it loads as in the game folder.",
                        options = listOf("" to "$auto (auto)") + GameMods.SPECIALK_DLLS.map { it to it },
                        selected = mods.skDll,
                        onPick = { mods.skDll = it },
                    )
                }
            }
        }
        DeckStartProgramsTab(programs)
    }
}
