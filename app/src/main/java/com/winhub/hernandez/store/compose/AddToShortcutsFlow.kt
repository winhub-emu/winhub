package com.winhub.hernandez.store.compose

import android.app.Activity
import android.content.Intent
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.MainActivity
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.ui.Screen

/**
 * Shared Compose add-to-shortcuts flow for the store activities (Steam today):
 * StarLaunchBridge.loadContainers → [ContainerPickerDialog] →
 * StarLaunchBridge.writeShortcutAsync → [AddResultDialog].
 * Replaces the legacy android.app.AlertDialog picker + Toast results, which
 * clash with the M3 store screens (and the toasts render black-on-black on
 * this theme). Epic/GOG/Amazon still use the legacy path in StarLaunchBridge.
 */

/** State for the container picker step, held in a mutableStateOf by the caller. */
data class AddToShortcutsRequest(
    val gameName: String,
    val exePath: String,
    val coverUrl: String?,
    val containers: List<Container>,
)

/** Outcome of writeShortcutAsync, held in a mutableStateOf by the caller. */
data class AddShortcutResult(
    val gameName: String,
    val success: Boolean,
    val message: String,
)

/** M3 container picker matching the Containers screen list idiom. */
@Composable
fun ContainerPickerDialog(
    gameName: String,
    containers: List<Container>,
    onDismiss: () -> Unit,
    onSelected: (Container) -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add \"$gameName\" to container") },
        text = {
            if (containers.isEmpty()) {
                Text(
                    text = "No containers yet — create one in the Containers screen first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    containers.forEach { container ->
                        // Same name + wineVersion · resolution subtitle as ContainerItem.
                        val name = container.name?.takeIf { it.isNotEmpty() }
                            ?: "Container ${container.id}"
                        val subtitle = listOf(container.wineVersion.orEmpty(), container.screenSize.orEmpty())
                            .filter { it.isNotEmpty() }.joinToString(" · ")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelected(container) }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.icon_menu_container),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** M3 outcome dialog for writeShortcutAsync. */
@Composable
fun AddResultDialog(
    result: AddShortcutResult,
    onOpenShortcuts: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (result.success) {
        OutlinedAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Added to Shortcuts") },
            text = { Text("\"${result.gameName}\" is ready in Shortcuts.") },
            confirmButton = {
                TextButton(onClick = onOpenShortcuts) { Text("Open Shortcuts") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Close") }
            },
        )
    } else {
        OutlinedAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Couldn't add shortcut") },
            text = { Text(result.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            },
        )
    }
}

/**
 * Jumps back to the main app on the Shortcuts (Games) screen. CLEAR_TOP pops
 * the store activities off the task; SINGLE_TOP delivers the extra to the
 * existing MainActivity via onNewIntent, which navigates to the route.
 */
fun openShortcutsScreen(activity: Activity) {
    activity.startActivity(
        Intent(activity, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SCREEN, Screen.Deck.route)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
}
