package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.ui.screens.adrenodownload.AdrenoDriverDownloadSheet
import com.winhub.hernandez.util.InAppFilePicker

@Composable
fun AdrenoToolsScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val manager = remember { AdrenotoolsManager(activity) }
    val cs = MaterialTheme.colorScheme

    // Mutable list drives the UI
    var drivers by remember { mutableStateOf(manager.enumarateInstalledDrivers().toList()) }

    var confirmInstallPrompt by remember { mutableStateOf(false) }
    var confirmRemoveIndex by remember { mutableStateOf<Int?>(null) }
    var showDownloadSheet by remember { mutableStateOf(false) }

    // File picker for installing a GPU driver .zip
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // In-app picker returns a path (wrapped as file:// Uri); SAF returns result.data.data.
            (result.data?.data ?: InAppFilePicker.pickedUri(result.data))?.let { uri ->
                val driverId = manager.installDriver(uri)
                if (driverId.isNotEmpty()) {
                    drivers = drivers + driverId
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.surface)
    ) {

        // Top row: Install button + Download-online icon button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Button(
                onClick = { confirmInstallPrompt = true },
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Install GPU driver")
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showDownloadSheet = true }) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Download GPU drivers from online sources",
                    tint = cs.primary,
                )
            }
        }

        Divider(color = cs.outline.copy(alpha = 0.4f))

        if (drivers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No GPU drivers installed.", color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(drivers) { index, driverId ->
                    DriverItem(
                        name = manager.getDriverName(driverId),
                        version = manager.getDriverVersion(driverId),
                        onRemove = { confirmRemoveIndex = index },
                    )
                    Divider(color = cs.outline.copy(alpha = 0.25f))
                }
            }
        }
    }

    // Confirm: install
    if (confirmInstallPrompt) {
        OutlinedAlertDialog(
            onDismissRequest = { confirmInstallPrompt = false },
            title = { Text(context.getString(R.string.install_drivers_message)) },
            text = { Text(context.getString(R.string.install_drivers_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmInstallPrompt = false
                    filePicker.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.DRIVER, "Select GPU driver"))
                }) { Text("Browse files") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmInstallPrompt = false
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        filePicker.launch(intent)
                    }) { Text("Pick via system…") }
                    TextButton(onClick = { confirmInstallPrompt = false }) {
                        Text(context.getString(android.R.string.cancel))
                    }
                }
            },
        )
    }

    // Confirm: remove
    confirmRemoveIndex?.let { idx ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmRemoveIndex = null },
            title = { Text("Remove driver?") },
            text = { Text("Remove \"${manager.getDriverName(drivers[idx])}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = drivers[idx]
                    manager.removeDriver(id)
                    drivers = drivers.toMutableList().also { it.removeAt(idx) }
                    confirmRemoveIndex = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveIndex = null }) { Text("Cancel") }
            },
        )
    }

    if (showDownloadSheet) {
        AdrenoDriverDownloadSheet(
            onDismiss = { showDownloadSheet = false },
            onDriverInstalled = { driverId ->
                if (driverId.isNotEmpty() && driverId !in drivers) {
                    drivers = drivers + driverId
                }
            },
        )
    }
}

@Composable
private fun DriverItem(
    name: String,
    version: String,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Memory,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            Text(text = version, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = cs.onSurfaceVariant)
        }
    }
}
