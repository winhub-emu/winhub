package com.winhub.hernandez.ui.simple

import androidx.compose.ui.graphics.Color
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.deck.DeckStore

/** A store's logo, the two colours of its tile, and whether the logo is a white mark on a coloured tile. */
internal fun storeLogo(st: DeckStore): Triple<Int, List<Color>, Boolean> = when (st) {
    DeckStore.STEAM -> Triple(R.drawable.store_logo_steam, listOf(Color(0xFF171A21), Color(0xFF1B2838)), false)
    DeckStore.EPIC -> Triple(R.drawable.store_logo_epic, listOf(Color(0xFF121212), Color(0xFF202020)), false)
    DeckStore.GOG -> Triple(R.drawable.store_logo_gog, listOf(Color(0xFF5C2D91), Color(0xFF3B1C60)), false)
    DeckStore.AMAZON -> Triple(R.drawable.store_logo_amazon, listOf(Color(0xFF232F3E), Color(0xFF131A22)), false)
    DeckStore.XBOX -> Triple(R.drawable.store_logo_xbox, listOf(Color(0xFF107C10), Color(0xFF0B5A0B)), true)
}
