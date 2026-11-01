package com.winhub.hernandez.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.winhub.hernandez.ui.XServerDialogState

@Composable
fun DebugDialogContent(state: XServerDialogState) {
    val logLines  by state.logLines.collectAsState()
    val logPaused by state.logPaused.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty() && !logPaused) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Dialog(
        onDismissRequest = { state.dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Logs",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(logLines) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    if (logLines.isEmpty()) {
                        item {
                            Text(
                                text = "No log output yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = {
                        // Copy the full accumulated log (incl. BCn transfer stats) to the clipboard.
                        clipboard.setText(AnnotatedString(logLines.joinToString("\n")))
                        Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy")
                    }
                    TextButton(onClick = { state.clearLog() }) {
                        Text("Clear")
                    }
                    TextButton(onClick = { state.setLogPaused(!logPaused) }) {
                        Text(if (logPaused) "Resume" else "Pause")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { state.dismiss() }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
