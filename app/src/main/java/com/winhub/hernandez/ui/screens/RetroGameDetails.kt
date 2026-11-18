package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.retro.RetroGame
import com.winhub.hernandez.retro.RetroMeta
import java.io.File

/**
 * The game's details, editable: its name, a description and the usual metadata. [onDone] gets the new details to keep, an empty
 * [RetroMeta] to forget the edits, or null when nothing was changed.
 */
@Composable
internal fun RetroGameDetailsDialog(game: RetroGame, meta: RetroMeta, onDone: (RetroMeta?) -> Unit) {
    // The name field starts on what the library shows now (the edited name, else the one made from the file name).
    var name by remember { mutableStateOf(meta.name.ifBlank { game.title }) }
    var description by remember { mutableStateOf(meta.description) }
    var developer by remember { mutableStateOf(meta.developer) }
    var publisher by remember { mutableStateOf(meta.publisher) }
    var genre by remember { mutableStateOf(meta.genre) }
    var year by remember { mutableStateOf(meta.year) }
    var players by remember { mutableStateOf(meta.players) }
    DeckConfigDialog(
        title = stringResource(R.string.retro_details_title),
        subtitle = File(game.path).name,
        onDismiss = { onDone(null) },
    ) {
        DeckTextRow(stringResource(R.string.retro_details_name), value = name, onValueChange = { name = it }, width = 420.dp)
        DeckTextRow(stringResource(R.string.retro_details_description), value = description, onValueChange = { description = it }, width = 420.dp)
        DeckTextRow(stringResource(R.string.retro_details_developer), value = developer, onValueChange = { developer = it }, width = 420.dp)
        DeckTextRow(stringResource(R.string.retro_details_publisher), value = publisher, onValueChange = { publisher = it }, width = 420.dp)
        DeckTextRow(stringResource(R.string.retro_details_genre), value = genre, onValueChange = { genre = it }, width = 420.dp)
        DeckTextRow(stringResource(R.string.retro_details_year), value = year, onValueChange = { year = it }, width = 420.dp)
        DeckTextRow(stringResource(R.string.retro_details_players), value = players, onValueChange = { players = it }, width = 420.dp)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeckButton(stringResource(R.string.retro_details_reset), primary = false) { onDone(RetroMeta()) }
            DeckButton(stringResource(R.string.retro_details_save)) {
                onDone(
                    RetroMeta(
                        name.trim(), description.trim(), developer.trim(),
                        publisher.trim(), genre.trim(), year.trim(), players.trim(),
                    ),
                )
            }
        }
    }
}
