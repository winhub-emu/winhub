package com.winhub.hernandez.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import com.winhub.hernandez.retro.RetroCatalog
import com.winhub.hernandez.retro.RetroIcons
import com.winhub.hernandez.retro.RetroLibrary
import com.winhub.hernandez.retro.RetroSystem
import com.winhub.hernandez.util.InAppFilePicker

// The consoles the retro library shows: picked here, in Settings and in the first-run setup. Plain rows (no lazy list), so it sits
// inside the scrolling body of a Deck dialog or of a wizard step; every row is a D-pad stop.

/** Every console as a row with a tick; [onChange] gets the new set of ticked console ids. */
@Composable
internal fun RetroConsolePicker(selected: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val systems = remember { RetroCatalog.get(context).systems.sortedWith(compareBy({ if (it.year == 0) Int.MAX_VALUE else it.year }, { it.name })) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeckButton(stringResource(R.string.retro_pick_all), primary = false) { onChange(systems.map { it.id }.toSet()) }
            DeckButton(stringResource(R.string.retro_pick_none), primary = false) { onChange(emptySet()) }
            Text(
                stringResource(R.string.retro_pick_count, selected.size), color = DeckPalette.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        systems.forEach { s ->
            val on = s.id in selected
            ConsoleRow(s, on) { onChange(if (on) selected - s.id else selected + s.id) }
        }
    }
}

@Composable
private fun ConsoleRow(system: RetroSystem, on: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val icon = remember(system.id) { RetroIcons.of(context, system) }
    val sub = listOfNotNull(system.brand, system.year.takeIf { it > 0 }?.toString()).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .deckRing(interaction, shape)
            .clip(shape)
            .background(if (on) DeckPalette.primary.copy(alpha = 0.14f) else DeckPalette.surface)
            .border(1.dp, if (on) DeckPalette.primary else DeckPalette.line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != 0) RetroIcon(icon, Modifier.size(width = 56.dp, height = 40.dp))
        else Text(system.badge, color = DeckPalette.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.size(width = 56.dp, height = 40.dp))
        Column(Modifier.weight(1f)) {
            Text(system.name, color = DeckPalette.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, color = DeckPalette.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
        }
        Tick(on)
    }
}

/** The picker in a Deck dialog, for Settings and the retro library's own empty screen. [onClosed] runs when it goes. */
@Composable
internal fun RetroConsolesDialog(onClosed: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(RetroLibrary.enabledSystems(context)) }
    DeckConfigDialog(
        title = stringResource(R.string.retro_pick_title),
        subtitle = stringResource(R.string.retro_pick_lede),
        onDismiss = onClosed,
    ) {
        RetroConsolePicker(selected, { selected = it; RetroLibrary.setEnabledSystems(context, it) }, Modifier.fillMaxWidth())
        DeckButton(stringResource(R.string.retro_pick_done), modifier = Modifier.padding(top = 10.dp), onClick = onClosed)
    }
}

/** First-run setup, the retro step: pick the consoles. */
@Composable
internal fun RetroStep(modifier: Modifier) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(RetroLibrary.enabledSystems(ctx)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.verticalScroll(rememberScrollState())) {
        StepHeading(RETRO, stringResource(R.string.retro_step_title), stringResource(R.string.retro_step_lede))
        RetroConsolePicker(selected, { selected = it; RetroLibrary.setEnabledSystems(ctx, it) }, Modifier.fillMaxWidth())
    }
}

/** First-run setup, the retro step's side: where the ROMs are. */
@Composable
internal fun RetroFolderCard(modifier: Modifier) {
    val ctx = LocalContext.current
    var root by remember { mutableStateOf(RetroLibrary.romRoot(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedPath(result.data)?.takeIf { it.isNotBlank() }?.let {
                RetroLibrary.setRomRoot(ctx, it)
                root = it
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        Text(stringResource(R.string.retro_step_folder_title), color = DeckPalette.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(root ?: stringResource(R.string.retro_step_folder_none), color = DeckPalette.onSurfaceVariant, fontSize = 14.sp)
        DeckButton(stringResource(R.string.retro_choose_folder), primary = false) {
            launcher.launch(InAppFilePicker.buildDirIntent(ctx, ctx.getString(R.string.retro_choose_folder), root))
        }
        Text(stringResource(R.string.retro_step_folder_note), color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
    }
}
