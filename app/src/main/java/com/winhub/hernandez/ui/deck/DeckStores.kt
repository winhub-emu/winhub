package com.winhub.hernandez.ui.deck

import android.content.Context
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.ui.screens.deckSignedIn
import com.winhub.hernandez.ui.screens.isAmazonShortcut
import com.winhub.hernandez.ui.screens.isSteamOriginShortcut
import com.winhub.hernandez.ui.screens.isXboxShortcut

/** The five storefronts, in the order the Steam-style Home's Stores page lists them. [screen] is what the shell opens for a store. */
internal enum class DeckStore(val label: String, val screen: Screen, val owns: (Shortcut) -> Boolean) {
    // The same rules as the Games screen's store shelves and badges: a store tag, or the install folder the store uses. A game
    // that only has a Steam app id (a custom exe the Home looked up on Steam for its art) is not a Steam game.
    STEAM("Steam", Screen.Steam, { s -> isSteamOriginShortcut(s) }),
    EPIC("Epic Games", Screen.Epic, { s -> s.getExtra("storeSource") == "epic" || isInEpicFolder(s.path) }),
    GOG("GOG", Screen.Gog, { s -> s.getExtra("storeSource") == "gog" || s.path?.contains("gog_games", ignoreCase = true) == true }),
    AMAZON("Amazon Games", Screen.Amazon, { s -> isAmazonShortcut(s) }),
    XBOX("Xbox", Screen.Xbox, { s -> isXboxShortcut(s) }),
}

internal fun isSignedIn(context: Context, store: DeckStore): Boolean =
    runCatching { deckSignedIn(context, store.screen) }.getOrDefault(false)
