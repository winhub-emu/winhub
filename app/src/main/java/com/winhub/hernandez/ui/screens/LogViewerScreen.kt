package com.winhub.hernandez.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.winhub.hernandez.core.LogInventory
import com.winhub.hernandez.core.LogReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Reads one log file inside the Log Manager: pick a file from the chips, follow it live while a
 * game runs, search it, copy or share it.
 *
 * Only the TAIL is loaded. A Wine debug log with the seh channel on reaches tens of megabytes in
 * minutes, and the interesting part of a log is always the end — so we read the last
 * [TAIL_BYTES] and say so rather than trying to hold the file in memory.
 */
internal const val TAIL_BYTES = 256L * 1024
private const val MAX_LINES = 4000

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LogViewerScreen(entry: LogInventory.Entry, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Runs first, then the files inside the chosen run. "Keep last N" means there can be N past
    // launches sitting in previous/ — showing only the newest would hide exactly the run a user
    // is comparing against.
    val runs = remember(entry.dir) { LogInventory.runsIn(entry.dir) }
    var selectedRun by remember { mutableStateOf(runs.firstOrNull()) }
    val files = remember(selectedRun) { LogInventory.filesIn(selectedRun?.dir ?: entry.dir) }
    var selected by remember(selectedRun) { mutableStateOf(files.firstOrNull()) }
    var following by remember { mutableStateOf(false) }
    var wrap by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var truncatedFrom by remember { mutableStateOf(0L) }
    var reportOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()

    // Load, then keep loading while "following" is on. Reading happens on IO — a multi-megabyte
    // tail read on the main thread would jank the whole dialog.
    LaunchedEffect(selected, following) {
        val f = selected ?: return@LaunchedEffect
        do {
            val (text, from) = withContext(Dispatchers.IO) { readTail(f) }
            lines = text
            truncatedFrom = from
            if (following) {
                listState.scrollToItem(maxOf(0, lines.size - 1))
                delay(1000)
            }
        } while (following)
    }

    val shown = remember(lines, query) {
        if (query.isBlank()) lines else lines.filter { it.contains(query, ignoreCase = true) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (entry.isAppBucket) "App & crash logs" else entry.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (files.isEmpty()) {
                Text("This folder has no log files right now.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp))
                return@Column
            }

            // Run switcher, shown only when there is history to switch to.
            if (runs.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    runs.forEach { r ->
                        FileChip(
                            if (r.current) "Current" else runLabel(r.millis),
                            r == selectedRun
                        ) {
                            selectedRun = r
                            query = ""
                            // Following a finished run would poll a file that can never change.
                            if (!r.current) following = false
                        }
                    }
                }
            }

            // One chip per file in the folder — the mockup's file switcher.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                files.forEach { f ->
                    FileChip(f.name, f == selected) { selected = f; query = "" }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { following = !following }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (following) Color(0xFF5FBF6B) else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (following) "following" else "follow",
                        color = if (following) Color(0xFF5FBF6B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                selected?.let { f ->
                    Text("${f.name} · ${LogInventory.humanBytes(f.length())}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }

            if (findOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            // The slot takes ONE child, so the placeholder has to be overlaid
                            // rather than emitted as a sibling.
                            Box {
                                if (query.isEmpty()) {
                                    Text("Find in this log…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (query.isBlank()) "" else "${shown.size} line${if (shown.size == 1) "" else "s"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                    )
                    IconButton(onClick = { findOpen = false; query = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Close search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    }
                }
            }

            // The log body. Fixed dark surface rather than the theme's, because a log is read as a
            // terminal and the accent-tinted card colours make the severity colours hard to tell apart.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0B0807), RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (shown.isEmpty()) {
                    Text(
                        if (query.isBlank()) "This log is empty." else "No lines match \"$query\".",
                        color = Color(0xFF8A7F7A), fontSize = 11.sp
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (truncatedFrom > 0) {
                            item {
                                Text(
                                    "… showing the last ${LogInventory.humanBytes(TAIL_BYTES)} of " +
                                        LogInventory.humanBytes(truncatedFrom),
                                    color = Color(0xFF8A7F7A), fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        items(shown.size) { i ->
                            val line = shown[i]
                            Text(
                                line,
                                color = lineColor(line),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                softWrap = wrap,
                                maxLines = if (wrap) Int.MAX_VALUE else 1,
                                modifier = if (wrap) Modifier.fillMaxWidth()
                                // One shared scroll state so every line moves together — per-line
                                // scroll states would let the log shear sideways.
                                else Modifier.horizontalScroll(hScroll)
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) {
                ViewerAction("Wrap", active = wrap, modifier = Modifier.weight(1f)) { wrap = !wrap }
                ViewerAction("Find", active = findOpen, modifier = Modifier.weight(1f)) {
                    findOpen = !findOpen
                    if (!findOpen) query = ""
                }
                ViewerAction("Copy", modifier = Modifier.weight(1f)) {
                    clipboard.setText(AnnotatedString(shown.joinToString("\n")))
                    Toast.makeText(context, "Copied ${shown.size} lines", Toast.LENGTH_SHORT).show()
                }
                ViewerAction("Share", primary = true, modifier = Modifier.weight(1f)) {
                    selected?.let { shareLogFile(context, it) }
                }
            }

            ViewerAction(
                "Report a problem",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) { reportOpen = true }
        }
    }

    if (reportOpen) {
        ReportDialog(
            entry = entry,
            runDir = selectedRun?.dir ?: entry.dir,
            onDismiss = { reportOpen = false }
        )
    }
}

/**
 * Collects a title and a description, builds the redacted zip, then opens GitHub's new-issue form
 * with everything we know already filled in. The attach itself is the user's tap — see LogReport
 * for why that cannot be automated.
 */
@Composable
private fun ReportDialog(entry: LogInventory.Entry, runDir: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(if (entry.isAppBucket) "" else "${entry.name}: ") }
    var description by remember { mutableStateOf("") }
    var includeApp by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val willAttach = remember(runDir) { LogInventory.filesIn(runDir).map { it.name } }

    OutlinedAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Report a problem") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What went wrong?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Any detail that helps (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Will be attached", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                willAttach.forEach {
                    Text("• $it", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { includeApp = !includeApp }
                ) {
                    Checkbox(checked = includeApp, onCheckedChange = { includeApp = it })
                    Text("Also app logcat and crash reports",
                        color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                }
                Text(
                    "Saved as a zip in Downloads. E-mail addresses, auth tokens and your Steam " +
                        "account name are stripped; file paths are kept so they stay useful for " +
                        "debugging, so glance over them if a folder name identifies you. GitHub " +
                        "can't receive a file from a link, so attach the zip with 📎 once the " +
                        "form opens.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        // Reading, redacting and zipping several megabytes: not on the UI thread.
                        val bundle = withContext(Dispatchers.IO) {
                            LogReport.build(context, entry, runDir, includeApp)
                        }
                        busy = false
                        if (bundle == null) {
                            Toast.makeText(context, "Couldn't build the report.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        onDismiss()
                        Toast.makeText(
                            context, "Saved ${bundle.zip.name} to Downloads", Toast.LENGTH_LONG
                        ).show()
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW,
                                    android.net.Uri.parse(LogReport.issueUrl(title, description, bundle.facts)))
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser to open GitHub with.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) { Text(if (busy) "Working…" else "Continue on GitHub") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FileChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        name,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
private fun ViewerAction(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = primary || active
    Box(
        modifier = modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(7.dp))
            .border(
                1.dp,
                if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(7.dp)
            )
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
    }
}

/**
 * Label for an archived run. Recent ones read better as an age ("2 hr ago"), older ones as a date —
 * "6 days ago" is not something anyone can line up against when a game broke.
 */
internal fun runLabel(millis: Long): String {
    if (millis <= 0) return "earlier"
    val mins = (System.currentTimeMillis() - millis) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} hr ago"
        mins < 60 * 48 -> "yesterday"
        else -> java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.US)
            .format(java.util.Date(millis))
    }
}

/** Severity colouring, matching the mockup: errors red, warnings amber, layer chatter blue. */
internal fun lineColor(line: String): Color {
    val l = line.lowercase()
    return when {
        l.contains(":err:") || l.contains("err:") || l.contains(" error") || l.contains("fatal") ->
            Color(0xFFE58A7C)
        l.contains(":warn:") || l.contains("warn:") || l.contains("unsupported") ->
            Color(0xFFE0B341)
        l.contains("[dxvk]") || l.contains("[vkd3d") || l.contains("esync") || l.contains("fsync") ->
            Color(0xFF7FB2E5)
        else -> Color(0xFFC8BDB8)
    }
}

/**
 * Last [TAIL_BYTES] of the file as lines, plus the original size when we had to truncate (0 when
 * the whole file fitted). A partial first line is dropped rather than shown half-read.
 */
internal fun readTail(f: File): Pair<List<String>, Long> {
    return try {
        val len = f.length()
        if (len == 0L) return emptyList<String>() to 0L
        val from = if (len > TAIL_BYTES) len - TAIL_BYTES else 0L
        val bytes = ByteArray((len - from).toInt())
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(from)
            raf.readFully(bytes)
        }
        var text = String(bytes)
        if (from > 0) text = text.substringAfter('\n', text)
        val all = text.split('\n')
        val capped = if (all.size > MAX_LINES) all.subList(all.size - MAX_LINES, all.size) else all
        capped to (if (from > 0) len else 0L)
    } catch (e: Exception) {
        listOf("Could not read this file: ${e.message}") to 0L
    }
}

/**
 * Share a log through the same FileProvider the config export and updater use. The file is copied
 * into cacheDir first: logs can live on a user-chosen folder outside any path the provider
 * declares, and granting a chooser access to that folder is not something to do casually.
 */
internal fun shareLogFile(context: Context, file: File) {
    try {
        val dir = File(context.cacheDir, "logs/share").apply { mkdirs() }
        val copy = File(dir, file.name)
        file.copyTo(copy, overwrite = true)
        val authority = context.packageName + ".tileprovider"
        val uri = FileProvider.getUriForFile(context, authority, copy)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share log"))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share that log.", Toast.LENGTH_SHORT).show()
    }
}

/** Share every current-run log in a group at once — the card's Share action. */
fun shareLogGroup(context: Context, entry: LogInventory.Entry) {
    val files = LogInventory.filesIn(entry.dir)
    if (files.isEmpty()) {
        Toast.makeText(context, "Nothing to share yet.", Toast.LENGTH_SHORT).show()
        return
    }
    if (files.size == 1) {
        shareLogFile(context, files[0])
        return
    }
    try {
        val dir = File(context.cacheDir, "logs/share").apply { mkdirs() }
        val uris = ArrayList<android.net.Uri>()
        val authority = context.packageName + ".tileprovider"
        files.forEach { f ->
            val copy = File(dir, f.name)
            f.copyTo(copy, overwrite = true)
            uris.add(FileProvider.getUriForFile(context, authority, copy))
        }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, if (entry.isAppBucket) "App & crash logs" else entry.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share logs"))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share those logs.", Toast.LENGTH_SHORT).show()
    }
}
