package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Deck-native confirm/warning popup used wherever the Deck UI would otherwise fall back to the classic
 * [the renderer warning dialog]. Generic so other Deck flows can reuse it.
 */
@Composable
internal fun DeckWarningDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DeckConfigDialog(title = title, onDismiss = onDismiss, compact = true) {
        DeckConfigNote(message)
        DeckButtonRow(onDismiss = onDismiss, confirmLabel = confirmLabel, onConfirm = onConfirm)
    }
}

/** Common Cancel / Confirm footer shared by the Deck popups. */
@Composable
internal fun DeckButtonRow(onDismiss: () -> Unit, confirmLabel: String, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DeckButton("Cancel", primary = false, onClick = onDismiss)
        DeckButton(confirmLabel, primary = true, onClick = onConfirm)
    }
}
