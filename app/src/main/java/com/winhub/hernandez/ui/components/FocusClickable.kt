package com.winhub.hernandez.ui.components

import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role
import com.winhub.hernandez.ui.screens.deckDragSelf
import androidx.compose.foundation.clickable as foundationClickable

/*
 * Drop-in for foundation's clickable: a row/tile made clickable also joins the surrounding
 * DeckDragFrame, so the smooth drag ring glides onto it with the gamepad. The overload that takes
 * an explicit interactionSource + indication is left untouched - those call sites draw their own
 * focus treatment. Outside a frame (or inside a Dialog window) deckDragSelf is a no-op and the
 * global focus outline still applies.
 */
fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val base = if (enabled) Modifier.deckDragSelf() else Modifier
    this.then(base).foundationClickable(enabled, onClickLabel, role, onClick)
}

fun Modifier.clickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = foundationClickable(interactionSource, indication, enabled, onClickLabel, role, onClick)
