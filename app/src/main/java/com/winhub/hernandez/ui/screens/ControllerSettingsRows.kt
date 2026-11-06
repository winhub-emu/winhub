package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.core.ControllerIdentity
import com.winhub.hernandez.core.StickPacing
import com.winhub.hernandez.ui.components.IconButton
import com.winhub.hernandez.ui.components.OutlinedButton
import com.winhub.hernandez.ui.components.Switch

/**
 * The controller-input settings of the container editor: Direct XInput (HERMES), in-game switching,
 * controller type, DirectInput vibration and stick updates. Stateless; the caller owns the values.
 */
@Composable
internal fun ControllerSettingsRows(
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
    onHelp: (Int) -> Unit,
) {
    @Composable
    fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, helpRes: Int, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f))
            IconButton(onClick = { onHelp(helpRes) }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
        }
    }

    SwitchRow(stringResource(R.string.hermes_xinput), hermesXInput, true, R.string.help_hermes_xinput, onHermesXInput)
    SwitchRow(stringResource(R.string.hermes_xinput_switching), switching, hermesXInput,
        R.string.help_hermes_xinput_switching, onSwitching)
    SwitchRow(stringResource(R.string.dinput_vibration), dinputVibration, true,
        R.string.help_dinput_vibration, onDinputVibration)

    Spacer(Modifier.height(8.dp))
    val choices = remember { ControllerIdentity.wineChoices() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LabeledDropdown(
            label = stringResource(R.string.controller_identity_label),
            options = choices.map { it.label },
            selectedOption = ControllerIdentity.label(identity),
            onSelect = { picked -> choices.firstOrNull { it.label == picked }?.let { onIdentity(it.value) } },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onHelp(R.string.help_controller_identity) }) {
            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
        }
    }

    Spacer(Modifier.height(8.dp))
    val current = StickPacing.parse(pacing) ?: StickPacing.DEFAULT
    val modeLabels = listOf("Auto", "Fixed", "Unpaced")
    Row(verticalAlignment = Alignment.CenterVertically) {
        LabeledDropdown(
            label = stringResource(R.string.stick_pacing_label),
            options = modeLabels,
            selectedOption = when (current.mode) {
                StickPacing.MODE_AUTO -> "Auto"
                StickPacing.MODE_FIXED -> "Fixed"
                else -> "Unpaced"
            },
            onSelect = { picked ->
                onPacing(
                    when (picked) {
                        "Auto" -> StickPacing(StickPacing.MODE_AUTO, StickPacing.AUTO_DEFAULT)
                        "Fixed" -> StickPacing(StickPacing.MODE_FIXED, StickPacing.FIXED_DEFAULT)
                        else -> StickPacing(StickPacing.MODE_OFF, 0)
                    }.format()
                )
            },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onHelp(R.string.help_stick_pacing) }) {
            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
        }
    }
    if (current.mode != StickPacing.MODE_OFF) {
        // − / + rather than a slider: a scroll of this page that began on a slider changed the rate.
        val down = current.step(-1)
        val up = current.step(1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPacing(down.format()) }, enabled = down !== current) { Text("−") }
            Text(current.rateLabel(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onPacing(up.format()) }, enabled = up !== current) { Text("+") }
        }
    }
}
