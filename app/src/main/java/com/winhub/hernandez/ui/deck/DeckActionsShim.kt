package com.winhub.hernandez.ui.deck

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration

/**
 * What the Steam-style Home's pages hand to the Deck shell: the L2/R2 tab step, and the Start "Add game" action. Home
 * provides one and reads these back; outside Home the default instance is simply never read.
 */
@Stable
internal class DeckActions {
    val subTab: MutableState<((Int) -> Unit)?> = mutableStateOf(null)
    val xmbAddGameHint: MutableState<(() -> Unit)?> = mutableStateOf(null)
}

internal val LocalDeckActions = compositionLocalOf { DeckActions() }

private const val DECK_COMPACT_WIDTH_DP = 600
private const val DECK_RAILS_MIN_WIDTH_DP = 480

/** The phone layout: a portrait screen narrower than 600 dp, or a landscape one narrower than 480 dp. */
internal fun isDeckCompact(cfg: Configuration): Boolean =
    cfg.screenWidthDp < if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) DECK_RAILS_MIN_WIDTH_DP else DECK_COMPACT_WIDTH_DP

/** The landscape frame (the Deck shell's side rails); the XMB pages use it to size their top inset. */
@Composable
internal fun deckRails(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && !isDeckCompact(cfg)
}
