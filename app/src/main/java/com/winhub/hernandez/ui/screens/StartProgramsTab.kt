package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as keyed
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.core.StartPrograms

/** One program of the Mods tab: a Windows path and the seconds to wait before starting it. */
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

    /** Takes the program at [from] out and puts it back at [to]; its wait goes with it. */
    fun moveTo(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        items.add(to, items.removeAt(from))
    }

    /** The extra value to save: blank paths dropped, an empty wait counted as 0 s. */
    fun encoded(): String? =
        StartPrograms.encode(items.map { StartPrograms.Entry(it.path, it.delay.toIntOrNull() ?: 0) })
}

private const val START_PROGRAMS_HINT =
    "These start with the game, top to bottom. The first waits from the game launching; each next one " +
        "waits after the one before it starts. A trainer usually needs about 10 s so the game is running."
private const val PATH_LABEL = "Windows path, e.g. F:\\Trainers\\Trainer.exe"

private val DELAY_CHOICES = listOf(0, 1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300)

internal fun delayLabel(seconds: Int): String = if (seconds == 0) "Right away" else "$seconds s"

/** The wait choices, plus the saved value when it is not one of them. */
internal fun delayOptions(current: String): List<Pair<String, String>> =
    (DELAY_CHOICES + listOfNotNull(current.toIntOrNull())).distinct().sorted()
        .map { it.toString() to delayLabel(it) }

internal fun programName(path: String): String = path.trim().substringAfterLast('\\').ifEmpty { "New program" }

/** Deck editor: add programs, set each one's path and wait, and open the whole list to reorder it. */
@Composable
internal fun DeckStartProgramsTab(state: StartProgramsState) {
    var arranging by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DeckGroup("Start with the game") {
            DeckConfigNote(START_PROGRAMS_HINT)
            DeckRow(
                label = "Programs",
                hint = if (state.items.isEmpty()) "None yet." else "${state.items.size} to start",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.items.isNotEmpty()) DeckButton("Arrange", primary = false) { arranging = true }
                    DeckButton("Add", primary = true) { state.add() }
                }
            }
        }
        state.items.forEachIndexed { index, item ->
            keyed(item) {
                DeckGroup("Program ${index + 1}") {
                    DeckConfigTextField(
                        label = PATH_LABEL,
                        value = item.path,
                        onValueChange = { item.path = it },
                    )
                    DeckChoiceRow(
                        label = "Wait before starting",
                        hint = if (index == 0) "After the game launches." else "After the previous program starts.",
                        options = delayOptions(item.delay),
                        selected = item.delay.toIntOrNull()?.toString() ?: "0",
                        onPick = { item.delay = it },
                    )
                }
            }
        }
    }
    if (arranging) StartProgramsOrderDialog(state) { arranging = false }
}

/**
 * Every program in one list. Pick one up (tap, or A), then tap where it goes or press Up / Down; pick it
 * again to drop it. Remove takes the picked one out of the list.
 */
@Composable
private fun StartProgramsOrderDialog(state: StartProgramsState, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf<Int?>(null) }
    // The last program was removed: nothing left to arrange.
    LaunchedEffect(state.items.size) { if (state.items.isEmpty()) onDismiss() }
    DeckConfigDialog(title = "Program order", onDismiss = onDismiss) {
        DeckConfigNote(
            if (picked == null) "Tap a program to pick it up."
            else "Tap where it should go, or press Up / Down. Tap it again to drop it.",
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { e ->
                    val from = picked
                    if (from == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val to = when (e.key) {
                        Key.DirectionUp -> from - 1
                        Key.DirectionDown -> from + 1
                        else -> return@onPreviewKeyEvent false
                    }
                    if (to in state.items.indices) {
                        state.moveTo(from, to)
                        picked = to
                    }
                    true
                },
        ) {
            state.items.forEachIndexed { index, item ->
                keyed(item) {
                    OrderRow(index, item, isPicked = picked == index) {
                        val from = picked
                        picked = when {
                            from == null -> index
                            from == index -> null
                            else -> { state.moveTo(from, index); null }
                        }
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            val from = picked
            if (from != null) DeckButton("Remove", primary = false) { state.remove(from); picked = null }
            DeckButton("Done", primary = true, onClick = onDismiss)
        }
    }
}

@Composable
private fun OrderRow(index: Int, item: StartProgramItem, isPicked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (isPicked) DeckPalette.primary.copy(alpha = 0.18f) else DeckPalette.surfaceVariant)
            .border(1.5.dp, if (isPicked) DeckPalette.primary else DeckPalette.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("${index + 1}", color = DeckPalette.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                programName(item.path),
                color = DeckPalette.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.path.trim().ifEmpty { "No path yet" },
                color = DeckPalette.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(delayLabel(item.delay.toIntOrNull() ?: 0), color = DeckPalette.onSurfaceVariant, fontSize = 12.sp)
    }
}
