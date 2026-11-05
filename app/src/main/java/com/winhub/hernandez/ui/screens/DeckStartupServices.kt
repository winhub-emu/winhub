package com.winhub.hernandez.ui.screens

import androidx.compose.runtime.Composable
import com.winhub.hernandez.core.WineUtils

/**
 * The per-service switches behind the "Custom" startup selection, in the Deck style. Meant to sit inside
 * the Startup group of the Deck container and shortcut editors. The service list and labels come from
 * the same WineUtils source as the classic [StartupServicesToggleList].
 */
@Composable
internal fun DeckStartupServiceRows(enabled: Set<String>, onToggle: (rawName: String, on: Boolean) -> Unit) {
    DeckConfigNote(
        "Custom starts with every service off. Turn on only what you need. " +
            "Disabling Wine Bus/HID can break controllers.",
    )
    WineUtils.STARTUP_SERVICES.forEachIndexed { i, entry ->
        val raw = WineUtils.startupServiceRawName(entry)
        DeckRow(label = WineUtils.STARTUP_SERVICE_LABELS.getOrElse(i) { raw }, hint = raw) {
            DeckToggle(checked = enabled.contains(raw)) { onToggle(raw, it) }
        }
    }
}
