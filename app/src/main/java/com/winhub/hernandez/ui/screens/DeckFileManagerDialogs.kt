package com.winhub.hernandez.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.unpack.SevenZip
import com.winhub.hernandez.ui.components.clickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Deck dialogs for the file manager. Each opens in its own window, so they ride DeckConfigDialog
 * (focus parked inside, D-pad walks the rows, B closes). The classic screen keeps its Material
 * dialogs; FileManagerScreen picks between the two.
 */

/** A compact Deck dialog: [content], then Cancel / confirm pills when labels are given. */
@Composable
internal fun DeckFmDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    confirmLabel: String? = null,
    danger: Boolean = false,
    dismissLabel: String? = "Cancel",
    onConfirm: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    DeckConfigDialog(title = title, subtitle = subtitle, onDismiss = onDismiss, compact = true) {
        content()
        if (confirmLabel != null || dismissLabel != null) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                if (dismissLabel != null) DeckButton(dismissLabel, primary = false) { onDismiss() }
                if (confirmLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    if (danger) DeckDangerButton(confirmLabel, onConfirm)
                    else DeckButton(confirmLabel) { onConfirm() }
                }
            }
        }
    }
}

@Composable
private fun FmMenuRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val tint = if (danger) DeckPalette.error else DeckPalette.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = if (danger) DeckPalette.error else DeckPalette.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A row with a round check on the right: on = filled primary. Never colour alone: the tick is drawn. */
@Composable
private fun FmCheckRow(label: String, icon: ImageVector, checked: Boolean, onClick: () -> Unit) {
    FmMenuRow(label, icon, onClick) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) DeckPalette.primary else androidx.compose.ui.graphics.Color.Transparent)
                .border(2.dp, if (checked) DeckPalette.primary else DeckPalette.onSurfaceVariant, CircleShape),
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = "On", tint = DeckPalette.onPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FmSectionLabel(text: String) {
    Text(
        text = text,
        color = DeckPalette.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
    )
}

// ── Item menu ──

internal class DeckFmMenuActions(
    val select: () -> Unit,
    val properties: () -> Unit,
    val toggleFavorite: () -> Unit,
    val run: () -> Unit,
    val addToShortcuts: () -> Unit,
    val unpack: () -> Unit,
    val fastExtract: () -> Unit,
    val rename: () -> Unit,
    val copy: () -> Unit,
    val cut: () -> Unit,
    val delete: () -> Unit,
)

@Composable
internal fun DeckFmContextMenu(
    file: File,
    isFavorite: Boolean,
    a: DeckFmMenuActions,
    onDismiss: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }
    val isDir = file.isDirectory
    val lower = file.name.lowercase()
    val runnable = !isDir && (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".msi") || lower.endsWith(".sh"))
    // Content-aware: extension or a cheap magic-byte sniff (reads a few header bytes, once per open).
    val isInno = remember(file.absolutePath) { SevenZip.isInnoSetup(file) }
    val archive = remember(file.absolutePath) { isInno || SevenZip.looksLikeArchive(file) }
    val go: (() -> Unit) -> () -> Unit = { action -> { onDismiss(); action() } }

    DeckConfigDialog(
        title = file.name,
        subtitle = (if (isDir) "Folder" else StringUtils.formatBytes(file.length())) +
            "  ·  " + dateFormat.format(Date(file.lastModified())),
        onDismiss = onDismiss,
        compact = true,
    ) {
        FmMenuRow("Select", Icons.Filled.Checklist, go(a.select))
        FmMenuRow("Properties", Icons.Filled.Info, go(a.properties))
        // Favorites are directories: only folders get the pin toggle.
        if (isDir) {
            FmMenuRow(
                if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                go(a.toggleFavorite),
            )
        }
        if (runnable) FmMenuRow("Run", Icons.Filled.PlayArrow, go(a.run))
        // Only real PE executables can become a permanent Games tile (the importer execs `wine <path>`).
        if (!isDir && lower.endsWith(".exe")) FmMenuRow("Add to Shortcuts", Icons.Filled.Add, go(a.addToShortcuts))
        if (archive) {
            FmMenuRow(if (isInno) "Unpack / Install…" else "Unpack Archive…", Icons.Filled.Unarchive, go(a.unpack))
            FmMenuRow("Fast Extract", Icons.Filled.Bolt, go(a.fastExtract))
        }
        FmMenuRow("Rename", Icons.Filled.Edit, go(a.rename))
        FmMenuRow("Copy", Icons.Filled.FileCopy, go(a.copy))
        FmMenuRow("Cut", Icons.Filled.ContentCut, go(a.cut))
        FmMenuRow("Delete", Icons.Filled.Delete, go(a.delete), danger = true)
    }
}

// ── Sort and view ──

@Composable
internal fun DeckFmOptionsDialog(
    sortBy: String,
    sortDesc: Boolean,
    gridView: Boolean,
    compactRows: Boolean,
    showHidden: Boolean,
    onSort: (String) -> Unit,
    onGrid: () -> Unit,
    onCompact: () -> Unit,
    onHidden: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    DeckFmDialog(title = "Sort & view", onDismiss = onDismiss, dismissLabel = "Done") {
        FmSectionLabel("SORT BY")
        listOf("name" to "Name", "date" to "Date modified", "size" to "Size").forEach { (key, label) ->
            val on = sortBy == key
            FmMenuRow(
                label = label,
                icon = if (on) Icons.Filled.Check else Icons.Filled.Folder,
                onClick = { onSort(key) },
            ) {
                // Tapping the active field flips direction; the arrow says which way it runs.
                if (on) Text(
                    text = if (sortDesc) "Z → A  ↓" else "A → Z  ↑",
                    color = DeckPalette.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        FmSectionLabel("LAYOUT")
        FmMenuRow(
            label = if (gridView) "Show as list" else "Show as grid",
            icon = if (gridView) Icons.Filled.ViewList else Icons.Filled.GridView,
            onClick = onGrid,
        )
        if (!gridView) FmCheckRow("Compact rows", Icons.Filled.ViewList, compactRows, onCompact)
        FmCheckRow("Show hidden files", Icons.Filled.Folder, showHidden, onHidden)
        FmSectionLabel("FOLDER")
        FmMenuRow("Refresh", Icons.Filled.Refresh, onClick = { onDismiss(); onRefresh() })
    }
}

// ── Pick a container ──

@Composable
internal fun DeckFmChoiceDialog(
    title: String,
    labels: List<String>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    DeckFmDialog(title = title, onDismiss = onDismiss) {
        labels.forEachIndexed { i, label ->
            FmMenuRow(label, Icons.Filled.Inventory2, onClick = { onPick(i) })
        }
    }
}

// ── Paste conflict ──

@Composable
internal fun DeckFmConflictDialog(
    conflict: File,
    batch: Boolean,
    applyToAll: Boolean,
    onApplyToAll: (Boolean) -> Unit,
    onChoice: (ConflictChoice) -> Unit,
    onCancel: () -> Unit,
) {
    val isDir = conflict.isDirectory
    DeckFmDialog(
        title = "\"${conflict.name}\" already exists",
        subtitle = if (isDir) "Merge adds and replaces files inside the existing folder."
        else "Overwrite replaces the existing file.",
        onDismiss = onCancel,
    ) {
        if (batch) FmCheckRow("Apply to all conflicts", Icons.Filled.Checklist, applyToAll) { onApplyToAll(!applyToAll) }
        FmMenuRow(
            label = if (isDir) "Merge" else "Overwrite",
            icon = Icons.Filled.Edit,
            onClick = { onChoice(if (isDir) ConflictChoice.MERGE else ConflictChoice.OVERWRITE) },
        )
        FmMenuRow("Keep both", Icons.Filled.FileCopy, onClick = { onChoice(ConflictChoice.KEEP_BOTH) })
        FmMenuRow("Skip", Icons.Filled.Close, onClick = { onChoice(ConflictChoice.SKIP) })
    }
}

// ── Properties ──

@Composable
internal fun DeckFmPropertiesDialog(
    file: File,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }
    var attrs by remember(file.absolutePath) { mutableStateOf<FileAttrState?>(null) }
    // Guards against a second toggle landing while the first is still being applied off-thread.
    var busy by remember(file.absolutePath) { mutableStateOf(false) }
    LaunchedEffect(file.absolutePath) {
        attrs = withContext(Dispatchers.IO) { readFileAttrs(file) }
    }

    DeckFmDialog(
        title = "Properties",
        subtitle = file.name,
        onDismiss = onDismiss,
        confirmLabel = "Done",
        dismissLabel = null,
        onConfirm = onDismiss,
    ) {
        FmInfoLine("Location", file.parent ?: "—")
        FmInfoLine(
            "Type",
            if (file.isDirectory) "Folder"
            else file.extension.uppercase().let { if (it.isBlank()) "File" else "$it file" },
        )
        if (!file.isDirectory) FmInfoLine("Size", StringUtils.formatBytes(file.length()))
        FmInfoLine("Modified", dateFormat.format(Date(file.lastModified())))
        Spacer(Modifier.height(6.dp))

        val state = attrs
        val readOnlyEnabled = state != null && !busy
        DeckRow(
            label = "Read-only",
            hint = "Stops games (and Wine) from overwriting or deleting this file.",
        ) {
            Box(Modifier.alpha(if (readOnlyEnabled) 1f else 0.4f)) {
                DeckToggle(checked = state?.readOnly == true) { want ->
                    if (!readOnlyEnabled) return@DeckToggle
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { setReadOnly(file, want) }
                        busy = false
                        if (ok) {
                            attrs = attrs?.copy(readOnly = want)
                            onChanged()
                        } else {
                            Toast.makeText(context, "Couldn't change Read-only", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        val hiddenSupported = state?.hiddenSupported == true
        val hiddenEnabled = state != null && hiddenSupported && !busy
        DeckRow(
            label = "Hidden",
            hint = if (state != null && !hiddenSupported) "This storage can't store the hidden flag."
            else "Marks the file hidden in Windows (Wine's hidden attribute).",
        ) {
            Box(Modifier.alpha(if (hiddenEnabled) 1f else 0.4f)) {
                DeckToggle(checked = state?.hidden == true) { want ->
                    if (!hiddenEnabled) return@DeckToggle
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { setHidden(file, want) }
                        busy = false
                        if (ok) {
                            attrs = attrs?.copy(hidden = want)
                            onChanged()
                        } else {
                            // The write failed after all: disable the toggle rather than lie.
                            attrs = attrs?.copy(hiddenSupported = false)
                            Toast.makeText(context, "Couldn't change Hidden on this storage", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FmInfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp)) {
        Text(label, color = DeckPalette.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(84.dp))
        Text(
            value,
            color = DeckPalette.onBackground,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
