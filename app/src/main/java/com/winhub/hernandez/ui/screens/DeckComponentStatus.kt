package com.winhub.hernandez.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.core.ComponentUpdates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Updater page's second half: Proton, GPU driver and FEXCore, each with what is installed, what the
 * catalog has newest, and an Update (or Install) button that opens the same popup the Deck offers on
 * start. Checks when the page opens and again on Check, and after a popup closes so the versions refresh.
 */
@Composable
internal fun DeckComponentStatusGroup() {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<ComponentUpdates.Status>>(emptyList()) }
    var checking by remember { mutableStateOf(true) }
    var round by remember { mutableStateOf(0) }
    var updating by remember { mutableStateOf<ComponentUpdates.Offer?>(null) }

    LaunchedEffect(round) {
        checking = true
        rows = withContext(Dispatchers.IO) { ComponentUpdates.statusAll(context) }
        checking = false
    }

    DeckGroup(title = "Layers and components") {
        DeckRow(
            label = if (checking) "Checking…" else "Check components",
            hint = "Proton, graphics driver and FEXCore",
        ) {
            DeckButton("Check", primary = false) { if (!checking) round++ }
        }
        rows.forEach { row ->
            DeckRow(
                label = row.kind,
                hint = "Installed: ${row.current}" +
                    (row.latest?.let { "  ·  Newest: $it" } ?: if (checking) "" else "  ·  Newest: can't reach the catalog"),
            ) {
                val offer = row.offer
                when {
                    offer != null ->
                        DeckButton(if (row.current == "Not installed") "Install" else "Update", primary = true) { updating = offer }
                    row.latest != null ->
                        Text("Up to date", color = DeckPalette.good, fontSize = 14.sp)
                    else -> Text("", fontSize = 14.sp)
                }
            }
        }
    }

    updating?.let { offer ->
        DeckComponentUpdateDialog(offer, onClose = { updating = null; round++ })
    }
}
