package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.core.StartPrograms

/** One row of the Mods tab: a Windows path and the seconds to wait before starting it. */
internal class StartProgramItem(path: String, delay: String) {
    var path by mutableStateOf(path)
    var delay by mutableStateOf(delay)
}

/**
 * The Mods tab's list of programs to start with the game (core.StartPrograms), held by the Deck shortcut editor.
 */
internal class StartProgramsState(stored: String?) {
    val items = mutableStateListOf<StartProgramItem>().apply {
        StartPrograms.parse(stored).forEach { add(StartProgramItem(it.path, it.delaySec.toString())) }
    }

    fun add() {
        val delay = if (items.isEmpty()) StartPrograms.DEFAULT_DELAY_SEC else 5
        items.add(StartProgramItem("", delay.toString()))
    }

    fun remove(index: Int) {
        if (index in items.indices) items.removeAt(index)
    }

    fun move(index: Int, by: Int) {
        val to = index + by
        if (index !in items.indices || to !in items.indices) return
        val item = items[index]
        items[index] = items[to]
        items[to] = item
    }

    fun setDelay(item: StartProgramItem, text: String) {
        item.delay = text.filter { it.isDigit() }.take(3)
    }

    /** The extra value to save: blank paths dropped, an empty wait counted as 0 s. */
    fun encoded(): String? =
        StartPrograms.encode(items.map { StartPrograms.Entry(it.path, it.delay.toIntOrNull() ?: 0) })
}

private const val START_PROGRAMS_HINT =
    "These start with the game, top to bottom. The first waits from the game launching; each next one " +
        "waits after the one before it starts. A trainer usually needs about 10 s so the game is running."
private const val PATH_PLACEHOLDER = "F:\\Trainers\\Trainer.exe"

/** Deck editor: one group per program, controls in the Deck style so the pad drives them. */
@Composable
internal fun DeckStartProgramsTab(state: StartProgramsState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DeckGroup("Start with the game") {
            DeckRow(label = "Programs", hint = START_PROGRAMS_HINT) {
                DeckButton("Add program", primary = true) { state.add() }
            }
        }
        state.items.forEachIndexed { index, item ->
            DeckGroup("Program ${index + 1}") {
                DeckTextRow(
                    label = "Windows path",
                    hint = "e.g. $PATH_PLACEHOLDER",
                    value = item.path,
                    onValueChange = { item.path = it },
                    width = 320.dp,
                )
                DeckTextRow(
                    label = "Wait before starting (seconds)",
                    hint = if (index == 0) "From the game launching." else "From the previous program starting.",
                    value = item.delay,
                    onValueChange = { state.setDelay(item, it) },
                    width = 120.dp,
                )
                DeckRow(label = "Order") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (index > 0) DeckButton("Up", primary = false) { state.move(index, -1) }
                        if (index < state.items.lastIndex) DeckButton("Down", primary = false) { state.move(index, 1) }
                        DeckButton("Remove", primary = false) { state.remove(index) }
                    }
                }
            }
        }
    }
}
