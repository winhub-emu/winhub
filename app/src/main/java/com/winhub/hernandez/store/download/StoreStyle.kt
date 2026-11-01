package com.winhub.hernandez.store.download

import androidx.compose.ui.graphics.Color

/**
 * Per-store display accents for the Download Manager UI (Phase 3 will use these to
 * tint a store chip / progress bar / badge so a mixed list reads at a glance).
 *
 * NOTE: these are sensible brand-recognisable placeholders — Steam blue, Epic blue,
 * GOG purple, Amazon orange. If the design spec pins exact hex values, reconcile
 * [accent] here with it (single source of truth for store colour). Values mirror the
 * ARGB-int accessor pattern in [com.winhub.hernandez.ui.theme.AppThemeState] so any
 * legacy AndroidView widget can tint too.
 */
object StoreStyle {

    /** Compose accent colour for a store. */
    fun accent(store: Store): Color = when (store) {
        Store.STEAM  -> Color(0xFF66C0F4) // Steam blue
        Store.EPIC   -> Color(0xFF0078F2) // Epic blue
        Store.GOG    -> Color(0xFF9B4DCA) // GOG purple
        Store.AMAZON -> Color(0xFFFF9900) // Amazon orange
        Store.XBOX   -> Color(0xFF107C10) // Xbox green
    }

    /** ARGB int for legacy AndroidView widgets (matches AppThemeState.getCurrentAccentArgb). */
    fun accentArgb(store: Store): Int = when (store) {
        Store.STEAM  -> 0xFF66C0F4.toInt()
        Store.EPIC   -> 0xFF0078F2.toInt()
        Store.GOG    -> 0xFF9B4DCA.toInt()
        Store.AMAZON -> 0xFFFF9900.toInt()
        Store.XBOX   -> 0xFF107C10.toInt()
    }

    /** Short display label for a store chip. */
    fun label(store: Store): String = when (store) {
        Store.STEAM  -> "Steam"
        Store.EPIC   -> "Epic"
        Store.GOG    -> "GOG"
        Store.AMAZON -> "Amazon"
        Store.XBOX   -> "Xbox"
    }
}
