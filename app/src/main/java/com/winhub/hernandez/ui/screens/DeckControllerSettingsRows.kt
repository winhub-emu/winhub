package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.core.ControllerIdentity
import com.winhub.hernandez.core.StickPacing

/**
 * Deck-style counterpart of [ControllerSettingsRows]: Direct XInput (HERMES), in-game switching,
 * controller type, DirectInput vibration and stick updates. Stateless; the caller owns the values.
 */
@Composable
internal fun DeckControllerSettingsRows(
    hermesXInput: Boolean,
    onHermesXInput: (Boolean) -> Unit,
    switching: Boolean,
    onSwitching: (Boolean) -> Unit,
    identity: String,
    onIdentity: (String) -> Unit,
    dinputVibration: Boolean,
    onDinputVibration: (Boolean) -> Unit,
    pacing: String,
    onPacing: (String) -> Unit,
) {
    DeckGroup("Controller") {
        DeckRow(
            label = stringResource(R.string.hermes_xinput),
            hint = stringResource(R.string.hermes_xinput_summary),
            control = { DeckToggle(hermesXInput, onHermesXInput) },
        )
        if (hermesXInput) {
            DeckRow(
                label = stringResource(R.string.hermes_xinput_switching),
                hint = stringResource(R.string.hermes_xinput_switching_summary),
                control = { DeckToggle(switching, onSwitching) },
            )
        }
        DeckRow(
            label = stringResource(R.string.dinput_vibration),
            hint = stringResource(R.string.dinput_vibration_summary),
            control = { DeckToggle(dinputVibration, onDinputVibration) },
        )
        val choices = remember { ControllerIdentity.wineChoices() }
        DeckChoiceRow(
            label = stringResource(R.string.controller_identity_label),
            options = choices.map { it.value to it.label },
            selected = ControllerIdentity.forWine(identity),
            onPick = onIdentity,
        )
        val current = StickPacing.parse(pacing) ?: StickPacing.DEFAULT
        DeckChoiceRow(
            label = stringResource(R.string.stick_pacing_label),
            options = listOf(
                StickPacing(StickPacing.MODE_AUTO, StickPacing.AUTO_DEFAULT).format() to "Auto",
                StickPacing(StickPacing.MODE_FIXED, StickPacing.FIXED_DEFAULT).format() to "Fixed",
                StickPacing(StickPacing.MODE_OFF, 0).format() to "Unpaced",
            ),
            // Rate changes keep the mode, so match on the mode alone.
            selected = when (current.mode) {
                StickPacing.MODE_AUTO -> StickPacing(StickPacing.MODE_AUTO, StickPacing.AUTO_DEFAULT).format()
                StickPacing.MODE_FIXED -> StickPacing(StickPacing.MODE_FIXED, StickPacing.FIXED_DEFAULT).format()
                else -> StickPacing(StickPacing.MODE_OFF, 0).format()
            },
            onPick = { picked ->
                val next = StickPacing.parse(picked)
                if (next != null && next.mode != current.mode) onPacing(picked)
            },
        )
        if (current.mode != StickPacing.MODE_OFF) {
            // − / + rather than a slider: a scroll of this page that began on a slider changed the rate.
            val down = current.step(-1)
            val up = current.step(1)
            DeckRow(
                label = current.rateLabel(),
                control = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckButton("−", primary = false, modifier = Modifier.width(56.dp)) {
                            if (down !== current) onPacing(down.format())
                        }
                        DeckButton("+", primary = false, modifier = Modifier.width(56.dp)) {
                            if (up !== current) onPacing(up.format())
                        }
                    }
                },
            )
        }
    }
}
