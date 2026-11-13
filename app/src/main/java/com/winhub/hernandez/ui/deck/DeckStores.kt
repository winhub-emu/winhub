package com.winhub.hernandez.ui.deck

import android.content.Context
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.ui.screens.deckSignedIn

/** The five storefronts, in the order the Steam-style Home's Stores page lists them. [screen] is what the shell opens for a store. */
internal enum class DeckStore(val label: String, val screen: Screen, val owns: (Shortcut) -> Boolean) {
    STEAM("Steam", Screen.Steam, { s -> s.getExtra("storeSource") == "steam" || s.getExtra("steamAppId").isNotBlank() }),
    EPIC("Epic Games", Screen.Epic, { s -> s.getExtra("storeSource") == "epic" }),
    GOG("GOG", Screen.Gog, { s -> s.getExtra("storeSource") == "gog" || s.file.path.contains("gog_games") }),
    AMAZON("Amazon Games", Screen.Amazon, { s -> s.getExtra("storeSource") == "amazon" || s.file.path.contains("/amazon/") }),
    XBOX("Xbox", Screen.Xbox, { s -> s.getExtra("storeSource") == "xbox" }),
}

internal fun isSignedIn(context: Context, store: DeckStore): Boolean =
    runCatching { deckSignedIn(context, store.screen) }.getOrDefault(false)
