package com.winhub.hernandez.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.XServerDialogState
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import kotlinx.coroutines.delay

@Composable
fun TaskManagerDialog(state: XServerDialogState) {
    val processes by state.tmProcesses.collectAsState()
    val cpuCores  by state.tmCpuCores.collectAsState()
    val cpuTitle  by state.tmCpuTitle.collectAsState()
    val memTitle  by state.tmMemTitle.collectAsState()
    val memInfo   by state.tmMemInfo.collectAsState()
    val count     by state.tmCount.collectAsState()

    // Refresh loop: calls Activity to update process list every second
    LaunchedEffect(Unit) {
        while (true) {
            state.onTmRefresh?.run()
            delay(1000L)
        }
    }

    // Notify Activity when dialog is dismissed so it can clear the WinHandler listener
    DisposableEffect(Unit) {
        onDispose { state.onTmDismissed?.run() }
    }

    Dialog(
        onDismissRequest = {
            state.onTmDismissed?.run()
            state.dismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Task Manager", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Processes: $count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Process list
                if (processes.isEmpty()) {
                    Text(
                        text = "No processes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        items(processes, key = { it.pid }) { proc ->
                            ProcessRow(proc, state)
                            HorizontalDivider()
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // CPU info
                Text(cpuTitle, style = MaterialTheme.typography.labelMedium)
                cpuCores.forEach { core ->
                    Text(core, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(4.dp))

                // Memory info
                Text(memTitle, style = MaterialTheme.typography.labelMedium)
                Text(memInfo, style = MaterialTheme.typography.bodySmall)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        state.onTmDismissed?.run()
                        state.dismiss()
                        state.onTmNewTask?.run()
                    }) { Text("New Task…") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        state.onTmDismissed?.run()
                        state.dismiss()
                    }) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(proc: XServerDialogState.TmProcess, state: XServerDialogState) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Icon
        if (proc.icon != null) {
            Image(
                bitmap = proc.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.taskmgr_process),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = proc.name + if (proc.wow64) " *32" else "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "PID ${proc.pid}  •  ${proc.formattedMemory}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.outlinedMenuCard()
            ) {
                DropdownMenuItem(
                    text = { Text("Bring to Front") },
                    onClick = {
                        menuExpanded = false
                        state.onTmBringToFront?.invoke(proc.name, proc.pid)
                        state.dismiss()
                    }
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("End Process") },
                    onClick = {
                        menuExpanded = false
                        state.onTmKillProcess?.invoke(proc.name)
                    }
                )
            }
        }
    }
}
