package com.winhub.hernandez.ui.screens

import com.winhub.hernandez.container.GameDetails
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.SteamStoreSearch

/**
 * Fills a shortcut's Game Details (genres, release year, score, short description) from the Steam store when it has none.
 * The Steam app is [knownAppId], the one the shortcut is already linked to, or else the first store-search hit for its
 * name; a guessed match fills the details but is not linked, so the cover and redist logic are not pointed at it.
 * Blocking (network and disk): call off the main thread. True when details were written.
 */
internal fun fillGameDetailsFromSteam(shortcut: Shortcut, knownAppId: Int?): Boolean {
    val have = GameDetails.from(shortcut)
    if (have.hasDisplayableDetails()) return false
    val linked = knownAppId?.takeIf { it > 0 } ?: have.steamAppId
    val id = linked ?: SteamStoreSearch.searchByName(shortcut.name).firstOrNull()?.appId ?: return false
    val info = SteamStoreSearch.fetchDetails(id) ?: return false
    GameDetails(
        steamAppId = linked,
        genres = info.genres,
        description = info.shortDescription,
        releaseYear = info.releaseYear,
        metacritic = info.metacritic,
    ).writeTo(shortcut)
    return true
}
