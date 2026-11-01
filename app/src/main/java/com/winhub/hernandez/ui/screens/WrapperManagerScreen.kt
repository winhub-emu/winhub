package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.winhub.hernandez.R
import com.winhub.hernandez.contents.WrapperCatalog
import com.winhub.hernandez.contents.WrapperCatalogDownloader
import com.winhub.hernandez.contents.WrapperCatalogEntry
import com.winhub.hernandez.contents.WrapperManager
import com.winhub.hernandez.contents.WrapperSettingsDictionary
import com.winhub.hernandez.core.GPUInformation
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wrapper Version Manager (issue #132). Step 1: a fixed-slot updater — each bundled graphics-wrapper
 * archive gets Update (swap in a newer .tzst of the same name) + Reset (revert to the built-in).
 * Step 2: free-form IMPORTED wrappers — bring your own wrapper, name it, delete it; imports appear in
 * the Graphics Driver dropdown. Overrides + imports live at filesDir/graphics_driver/<name> and win
 * at game launch.
 */
/** Full-screen entry point (reached from the app drawer via Screen.Wrappers). Chrome + scroll only;
 *  the actual slot list / actions live in [WrapperManagerBody] so the inline dialog can reuse them. */
@Composable
fun WrapperManagerScreen() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.surface)
            .verticalScroll(rememberScrollState())
    ) {
        WrapperManagerBody()
    }
}

/**
 * Inline dialog entry point: the same wrapper manager surfaced from the Graphics Driver row's cloud
 * button in the Container editor and the Game/Shortcut editor. Reuses [WrapperManagerBody] verbatim
 * (including its file-picker launcher and toasts) so there is a single install implementation.
 */
@Composable
fun WrapperManagerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        // Use (almost) the full screen width — the default AlertDialog width squeezes the wrapper
        // cards so their labels truncate. usePlatformDefaultWidth=false lets the modifier size it.
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(context.getString(R.string.wrapper_manager_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                WrapperManagerBody()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.wrapper_manager_close))
            }
        },
    )
}

/**
 * The wrapper-manager content, minus any Scaffold/top-bar/scroll chrome: header + Reset-all, the slot
 * cards (Update/Reset), the imported-wrapper cards (Delete), the Import affordance, plus the
 * file-picker launcher and confirm/name dialogs. Callers wrap it in their own scroll container.
 */
@Composable
fun WrapperManagerBody(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // WrapperManager only needs a Context (it calls getApplicationContext). Do NOT cast to Activity:
    // when this body is hosted inside a dialog, LocalContext is a ContextThemeWrapper, not an
    // Activity, and the cast crashes (device-verified). Pass the context straight through.
    val manager = remember { WrapperManager(context) }
    val cs = MaterialTheme.colorScheme

    // Resolve the real GPU once (native probe): vendor 0x5143 = Qualcomm/Adreno, plus a friendly model
    // name for the applicability lines. Wrapped defensively so a probe hiccup never crashes the screen.
    val gpu = remember {
        val vendor = runCatching { GPUInformation.getVendorID(null, null) }.getOrDefault(0)
        val model = runCatching { GPUInformation.extractModelName(GPUInformation.getRenderer(null, null)) }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "this GPU"
        GpuInfo(isQualcomm = vendor == 0x5143, model = model)
    }

    var slots by remember { mutableStateOf(manager.listSlots().toList()) }
    var imported by remember { mutableStateOf(manager.enumerateImported().toList()) }
    // The slot awaiting a picked file (set before the picker launches so the result knows its target).
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    // True when the pending file-pick is for a free-form import (vs a slot override update).
    var importMode by remember { mutableStateOf(false) }
    var confirmInstallPrompt by remember { mutableStateOf(false) }
    var confirmResetAll by remember { mutableStateOf(false) }
    // Import naming: set after an import file is picked AND inspected; drives the inspection+name dialog.
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInspection by remember { mutableStateOf<WrapperManager.Inspection?>(null) }
    var importNameDraft by remember { mutableStateOf("") }
    // Imported wrapper queued for deletion (confirm dialog).
    var deleteTarget by remember { mutableStateOf<WrapperManager.Imported?>(null) }
    // Imported wrapper whose detected-settings hide/show dialog is open (#132, Part B).
    var editSettingsTarget by remember { mutableStateOf<WrapperManager.Imported?>(null) }
    // Step 5 (#132): the curated wrapper catalog browser, layered over this body.
    var showCatalog by remember { mutableStateOf(false) }
    // Bundled-slot Update source chooser (#132): the slot whose "From file / From catalog" chooser is
    // open (null = closed).
    var updateChooserSlot by remember { mutableStateOf<WrapperManager.WrapperSlot?>(null) }
    // When non-null, the catalog browser is open in "replace this slot" mode (installs as an override).
    var catalogTargetSlot by remember { mutableStateOf<WrapperManager.WrapperSlot?>(null) }
    // #132: the live catalog entries (loaded once, off-main). Used to detect when a catalog-installed
    // wrapper has a newer version than what's on device -> an "Update available" badge + one-tap update.
    // Empty/offline/bad-JSON simply yields no badges (WrapperCatalog.loadCached never throws).
    var catalogEntries by remember { mutableStateOf<List<WrapperCatalogEntry>>(emptyList()) }
    // The imported wrapper currently being updated from the catalog (null = none) + its 0..100 progress.
    var updatingId by remember { mutableStateOf<String?>(null) }
    var updateProgress by remember { mutableStateOf(0) }
    // findActivity() safely walks the ContextWrapper chain (returns null in a raw dialog context — do NOT
    // cast, see the note above); used only to marshal download-progress ticks onto the UI thread.
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    // Load the catalog once (network-first, then cache) so imported cards can flag available updates.
    LaunchedEffect(Unit) {
        catalogEntries = withContext(Dispatchers.IO) { WrapperCatalog.loadCached(context).entries }
    }

    fun refresh() {
        slots = manager.listSlots().toList()
        imported = manager.enumerateImported().toList()
    }

    // Re-download the matched catalog entry through the SAME install pipeline. Because the entry carries
    // its catalogId, importWrapper updates the wrapper IN PLACE (rewriting catalogVersion), so refresh()
    // then clears the badge. Progress mirrors the catalog picker (activity.runOnUiThread ticks).
    fun startUpdate(identifier: String, entry: WrapperCatalogEntry) {
        updatingId = identifier
        updateProgress = 0
        scope.launch {
            val ok = WrapperCatalogDownloader.install(context, entry) { pct ->
                activity?.runOnUiThread { updateProgress = pct }
            } != null
            updatingId = null
            if (ok) {
                Toast.makeText(context, R.string.wrapper_updated_toast, Toast.LENGTH_SHORT).show()
                refresh()
            } else {
                Toast.makeText(context, R.string.wrapper_invalid_toast, Toast.LENGTH_LONG).show()
            }
        }
    }

    // #132 feature-c: one-tap update of a BUNDLED/overridden slot to the newer catalog build. Installs
    // the matched catalog entry as this slot's override (recording provenance), then refresh() re-reads
    // the now-current slotCatalogVersion so the badge clears. Keyed by slot.fileName in updatingId.
    fun startSlotUpdate(slot: WrapperManager.WrapperSlot, entry: WrapperCatalogEntry) {
        updatingId = slot.fileName
        updateProgress = 0
        scope.launch {
            val ok = WrapperCatalogDownloader.installToSlot(context, entry, slot.fileName) { pct ->
                activity?.runOnUiThread { updateProgress = pct }
            }
            updatingId = null
            if (ok) {
                Toast.makeText(context, R.string.wrapper_updated_toast, Toast.LENGTH_SHORT).show()
                refresh()
            } else {
                Toast.makeText(context, R.string.wrapper_invalid_toast, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Single file picker, shared by slot-Update and free-form Import (importMode disambiguates).
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val target = pendingFileName
        val wasImport = importMode
        pendingFileName = null
        importMode = false
        if (result.resultCode == Activity.RESULT_OK) {
            // In-app picker returns a path; SAF returns result.data.data.
            val path = InAppFilePicker.pickedPath(result.data)
            val uri = result.data?.data ?: path?.let { InAppFilePicker.asUri(it) }
            if (uri != null) {
                if (wasImport) {
                    // Pre-import INSPECT: validate + scan the picked file WITHOUT installing. Invalid
                    // archives abort here with the usual toast; valid ones open the inspection+name
                    // dialog so the user decides with full capability info before Add.
                    val inspection = manager.inspectUri(uri)
                    if (!inspection.valid) {
                        Toast.makeText(context, R.string.wrapper_invalid_toast, Toast.LENGTH_LONG).show()
                    } else {
                        // Default the display name to the file's base name.
                        val base = (path?.substringAfterLast('/') ?: uri.lastPathSegment?.substringAfterLast('/'))
                            ?.substringBeforeLast('.')
                            ?.takeIf { it.isNotBlank() }
                            ?: "Imported wrapper"
                        importNameDraft = base
                        pendingInspection = inspection
                        pendingImportUri = uri
                    }
                } else if (target != null) {
                    if (manager.installOverride(target, uri)) {
                        Toast.makeText(context, R.string.wrapper_updated_toast, Toast.LENGTH_SHORT).show()
                        refresh()
                    } else {
                        Toast.makeText(context, R.string.wrapper_invalid_toast, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun launchImportPicker() {
        importMode = true
        confirmInstallPrompt = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header + Reset all
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = context.getString(R.string.wrapper_manager_header),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            OutlinedButton(onClick = { confirmResetAll = true }) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.wrapper_reset_all))
            }
        }

        Divider(color = cs.outline.copy(alpha = 0.4f))
        Spacer(Modifier.size(4.dp))

        // Fixed bundled slots.
        slots.forEach { slot ->
            // #132 feature-c: does the live catalog carry a newer build than this slot's installed
            // version? An overridden slot's version comes from slot_catalog.json; a bundled slot's from
            // bundled_wrappers.json. Read inline so an update re-reads fresh and the badge clears.
            val slotCatId = if (slot.isOverridden) manager.slotCatalogId(slot.fileName)
                            else manager.bundledCatalogId(slot.fileName)
            val slotMatched = slotCatId?.let { id -> catalogEntries.firstOrNull { it.id == id } }
            val slotInstalledVer = if (slot.isOverridden) manager.slotCatalogVersion(slot.fileName)
                                   else manager.bundledCatalogVersion(slot.fileName)
            val slotUpdateAvailable = slotMatched != null && slotMatched.version > slotInstalledVer
            WrapperSlotCard(
                slot = slot,
                caps = manager.capsFor(slot.fileName.removeSuffix(".tzst")),
                gpu = gpu,
                manager = manager,
                updateAvailable = slotUpdateAvailable,
                updating = updatingId == slot.fileName,
                updateProgress = updateProgress,
                onUpdateFromCatalog = { slotMatched?.let { startSlotUpdate(slot, it) } },
                onUpdate = {
                    // Offer a source chooser (From file / From catalog) before doing anything.
                    updateChooserSlot = slot
                },
                onReset = {
                    manager.removeOverride(slot.fileName)
                    Toast.makeText(context, R.string.wrapper_reset_toast, Toast.LENGTH_SHORT).show()
                    refresh()
                },
            )
        }

        // Imported (free-form) wrappers.
        imported.forEach { imp ->
            // #132: does the live catalog carry a newer version than this catalog-installed wrapper?
            // Read provenance inline (not remember-cached) so an in-place update re-reads fresh and the
            // badge clears once refresh() reruns. A file import (no catalogId) never matches -> no badge.
            val catId = manager.catalogIdFor(imp.identifier)
            val matchedEntry = catId?.let { id -> catalogEntries.firstOrNull { it.id == id } }
            val updateAvailable = matchedEntry != null &&
                matchedEntry.version > manager.catalogVersionFor(imp.identifier)
            // #132: a catalog-installed import (has provenance) shows a durable "From catalog" chip.
            // Read inline (not remember-cached) so it stays fresh across import/delete/refresh.
            val fromCatalog = catId != null
            ImportedWrapperCard(
                imported = imp,
                manager = manager,
                gpu = gpu,
                updateAvailable = updateAvailable,
                fromCatalog = fromCatalog,
                updating = updatingId == imp.identifier,
                updateProgress = updateProgress,
                onUpdate = { matchedEntry?.let { startUpdate(imp.identifier, it) } },
                onDelete = { deleteTarget = imp },
                onEditSettings = { editSettingsTarget = imp },
            )
        }

        // Import affordance.
        ImportWrapperCard(onClick = { launchImportPicker() })
        // Download-from-catalog affordance (Step 5, #132). Coexists with the file import above.
        DownloadWrapperCard(onClick = { showCatalog = true })
        Spacer(Modifier.size(8.dp))
    }

    // Curated wrapper catalog browser (Step 5). Layered over this body (works when the manager is a
    // full screen AND a dialog). A successful install refreshes the imported list so it appears at once.
    if (showCatalog) {
        WrapperCatalogPicker(
            isQualcomm = gpu.isQualcomm,
            gpuModel = gpu.model,
            onDismiss = { showCatalog = false },
            onInstalled = { refresh() },
        )
    }

    // Bundled-slot Update source chooser (#132): "From file" (existing SAF/in-app picker path) or
    // "From catalog" (opens the catalog browser in replace-this-slot mode). Cancel just dismisses.
    val chooserSlot = updateChooserSlot
    if (chooserSlot != null) {
        OutlinedAlertDialog(
            onDismissRequest = { updateChooserSlot = null },
            title = { Text(context.getString(R.string.wrapper_update)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = context.getString(R.string.wrapper_manager_header),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    OutlinedButton(
                        onClick = {
                            updateChooserSlot = null
                            pendingFileName = chooserSlot.fileName
                            importMode = false
                            confirmInstallPrompt = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(context.getString(R.string.wrapper_update_from_file))
                    }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = {
                            updateChooserSlot = null
                            catalogTargetSlot = chooserSlot
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(context.getString(R.string.wrapper_update_from_catalog))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { updateChooserSlot = null }) {
                    Text(context.getString(android.R.string.cancel))
                }
            },
        )
    }

    // Catalog browser in "replace this slot" mode: a Download installs the entry as this slot's
    // override (marker-validated), then refresh() so the slot's Updated state shows.
    val targetSlot = catalogTargetSlot
    if (targetSlot != null) {
        WrapperCatalogPicker(
            isQualcomm = gpu.isQualcomm,
            gpuModel = gpu.model,
            onDismiss = { catalogTargetSlot = null },
            onInstalled = { refresh() },
            targetSlot = targetSlot.fileName,
            targetSlotLabel = targetSlot.label,
        )
    }

    // Confirm: pick a file (offers in-app picker or system SAF, like AdrenoToolsScreen). Shared by
    // slot-Update and Import; the pending flags decide what happens with the picked file.
    if (confirmInstallPrompt) {
        OutlinedAlertDialog(
            onDismissRequest = { confirmInstallPrompt = false; pendingFileName = null; importMode = false },
            title = { Text(context.getString(R.string.wrapper_update)) },
            text = { Text(context.getString(R.string.wrapper_manager_header)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmInstallPrompt = false
                    filePicker.launch(
                        InAppFilePicker.buildIntent(
                            context,
                            InAppFilePicker.WRAPPER,
                            context.getString(R.string.wrapper_select_title),
                        )
                    )
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
                    TextButton(onClick = {
                        confirmInstallPrompt = false; pendingFileName = null; importMode = false
                    }) {
                        Text(context.getString(android.R.string.cancel))
                    }
                }
            },
        )
    }

    // Pre-import INSPECTION + name (shown after a valid file is picked+inspected). Shows what the
    // wrapper is, its detected capabilities, what it will add / needs to work, then the Name field.
    val importUri = pendingImportUri
    val inspection = pendingInspection
    if (importUri != null && inspection != null) {
        fun closeInspect() { pendingImportUri = null; pendingInspection = null }
        OutlinedAlertDialog(
            onDismissRequest = { closeInspect() },
            title = { Text(context.getString(R.string.wrapper_import_title).removeSuffix("…")) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    // What it is.
                    DetailRow("What it is", importDescription(inspection.caps))
                    DetailRow(
                        "Version",
                        if (inspection.notes.isNotBlank()) "${inspection.version} · ${inspection.notes}"
                        else inspection.version,
                    )
                    val labels = capLabels(inspection.caps)
                    if (labels.isNotEmpty()) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Detected capabilities",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(4.dp))
                        CapabilityChips(labels)
                    }
                    // What will be added / needs to work.
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "What will be added",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(2.dp))
                    importNeeds(inspection.caps, gpu, inspection.version).forEach { line ->
                        Text(
                            "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    // #132 Layer 1: env-var names auto-detected from the binaries (advanced preview).
                    if (inspection.envKeys.isNotEmpty()) {
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "Detected settings (${inspection.envKeys.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            inspection.envKeys.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    // Name field.
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = context.getString(R.string.wrapper_import_name_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    OutlinedTextField(
                        value = importNameDraft,
                        onValueChange = { importNameDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = importNameDraft.trim()
                    val id = StringUtils.parseIdentifier(name)
                    when {
                        name.isEmpty() ->
                            Toast.makeText(context, R.string.wrapper_import_name_empty_toast, Toast.LENGTH_SHORT).show()
                        manager.isReservedIdentifier(id) ->
                            Toast.makeText(context, R.string.wrapper_import_reserved_toast, Toast.LENGTH_LONG).show()
                        else -> {
                            closeInspect()
                            if (manager.importWrapper(importUri, name) != null) {
                                Toast.makeText(context, R.string.wrapper_imported_toast, Toast.LENGTH_SHORT).show()
                                refresh()
                            } else {
                                Toast.makeText(context, R.string.wrapper_invalid_toast, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text(context.getString(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { closeInspect() }) {
                    Text(context.getString(android.R.string.cancel))
                }
            },
        )
    }

    // Confirm: delete an imported wrapper (runs the reference cascade).
    val toDelete = deleteTarget
    if (toDelete != null) {
        OutlinedAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(context.getString(R.string.wrapper_delete_confirm_title)) },
            text = { Text(context.getString(R.string.wrapper_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    manager.deleteImported(toDelete.identifier)
                    deleteTarget = null
                    Toast.makeText(context, R.string.wrapper_reset_toast, Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text(context.getString(R.string.wrapper_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(context.getString(android.R.string.cancel))
                }
            },
        )
    }

    // Edit settings: per-wrapper hide/show of detected settings (#132, Part B).
    val toEdit = editSettingsTarget
    if (toEdit != null) {
        EditWrapperSettingsDialog(
            manager = manager,
            imported = toEdit,
            onDismiss = { editSettingsTarget = null },
        )
    }

    // Confirm: reset all
    if (confirmResetAll) {
        OutlinedAlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text(context.getString(R.string.wrapper_reset_all_confirm_title)) },
            text = { Text(context.getString(R.string.wrapper_reset_all_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    manager.resetAll()
                    confirmResetAll = false
                    Toast.makeText(context, R.string.wrapper_reset_toast, Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text(context.getString(R.string.wrapper_reset_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) {
                    Text(context.getString(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * A compact card in the container-card idiom (RoundedCornerShape(12), surfaceVariant, 1dp outline
 * border) but tighter: a ~40dp icon tile, a weight(1f) info column (title + dimmed subtitle), and a
 * trailing actions slot. Shared frame for slot cards, imported cards, and the import affordance.
 */
// internal (not private): reused by WrapperCatalogPicker in the same package so a catalog card looks
// and expands exactly like a manager card.
@Composable
internal fun WrapperCardFrame(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    highlightSubtitle: Boolean = false,
    onClick: (() -> Unit)? = null,
    // Expandable detail (Part 1): when expandable, tapping the card toggles [expanded] and a chevron
    // is shown; [details] renders inside the card below a divider while expanded.
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    details: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    // Optional small status chip rendered between the title and subtitle (e.g. "Mali only" on a catalog
    // entry that's inert on the current GPU). Empty by default so existing cards are unchanged.
    titleBadge: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val base = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
    // A card is either a plain click affordance (import card) or an expandable detail card; both map
    // to a single clickable action here.
    val clickAction: (() -> Unit)? = onClick ?: (if (expandable) onToggleExpand else null)
    Card(
        modifier = if (clickAction != null) base.clickable(onClick = clickAction) else base,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
        border = BorderStroke(1.dp, cs.outline),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    titleBadge()
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (highlightSubtitle) cs.primary else cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                trailing()
                if (expandable) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (expandable && expanded) {
                Divider(color = cs.outline.copy(alpha = 0.4f))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    details()
                }
            }
        }
    }
}

/** GPU probe result used by the applicability lines. */
private data class GpuInfo(val isQualcomm: Boolean, val model: String)

/** Chip labels for the true capabilities of a wrapper (in a stable order). */
private fun capLabels(caps: WrapperManager.WrapperCaps): List<String> {
    val out = ArrayList<String>()
    if (caps.hasIcd) out.add("Vulkan ICD")
    if (caps.hasBcnLayer) out.add("BCn layer")
    if (caps.hasCompatLayer) out.add("DX12/compat layer")
    return out
}

/** One-line "what is this" summary for the inspection page (a valid import always carries an ICD). */
private fun importDescription(caps: WrapperManager.WrapperCaps): String = when {
    caps.hasIcd && (caps.hasBcnLayer || caps.hasCompatLayer) -> "Vulkan ICD wrapper with extra layers"
    caps.hasIcd -> "Vulkan ICD wrapper"
    caps.hasBcnLayer -> "BCn layer"
    caps.hasCompatLayer -> "DX12 compat layer"
    else -> "Wrapper"
}

/** GPU-applicability one-liner for the detail view; null when there are no GPU-gated layers. */
private fun gpuApplicability(caps: WrapperManager.WrapperCaps, gpu: GpuInfo): String? {
    val layerish = caps.hasBcnLayer || caps.hasCompatLayer
    return when {
        layerish && gpu.isQualcomm ->
            "BCn/compat layers apply to Mali/non-Qualcomm GPUs — inert on this Adreno (${gpu.model})"
        layerish -> "Applies to this GPU (${gpu.model})"
        else -> null
    }
}

/** Plain "what will be added / needs to work" lines for the inspection page. */
private fun importNeeds(caps: WrapperManager.WrapperCaps, gpu: GpuInfo, version: String): List<String> {
    val out = ArrayList<String>()
    if (caps.hasBcnLayer) {
        out.add(
            "Contains a BCn layer → the BCn Layer Settings become available; requires a Mali/non-Qualcomm GPU to take effect" +
                if (gpu.isQualcomm) " (this device is Adreno — inert)." else "."
        )
    }
    if (caps.hasCompatLayer) {
        out.add("Contains a DX12/compat layer → compat options (Mali-branch feature).")
    }
    if (!caps.hasBcnLayer && !caps.hasCompatLayer) {
        out.add("Plain Vulkan ICD wrapper — no extra layer settings.")
    }
    if (version == "Unknown") {
        out.add("No version.txt — version will show Unknown.")
    }
    return out
}

/** A label · value line in the container-card idiom (dimmed label, on-surface value). internal so the
 *  catalog detail box (same package) reuses the exact row style. */
@Composable
internal fun DetailRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Small rounded chips for detected capabilities. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilityChips(labels: List<String>) {
    val cs = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.secondaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * The shared per-wrapper detail box (Part 1) rendered inside an expanded card. Below the capability
 * chips it carries a NESTED, independently-expandable "Environment variables" section that lists the
 * full set of env-var names the wrapper references (issue #132) — read-only, informational.
 *
 * [manager] + [isImported] + [envSourceId] drive that env-var list: for an imported wrapper the keys
 * come from its cached .meta ([WrapperManager.detectedEnvKeys], [envSourceId] = identifier); for a
 * bundled slot they are scanned ON DEMAND from the asset/override ([WrapperManager.scanBundledEnvKeys],
 * [envSourceId] = slot file name).
 */
@Composable
private fun WrapperDetailBox(
    fileName: String,
    stateLabel: String,
    version: String,
    notes: String,
    caps: WrapperManager.WrapperCaps,
    gpu: GpuInfo,
    manager: WrapperManager,
    isImported: Boolean,
    envSourceId: String,
) {
    val cs = MaterialTheme.colorScheme
    DetailRow("File", fileName)
    DetailRow("State", stateLabel)
    DetailRow("Version", version)
    if (notes.isNotBlank()) DetailRow("Notes", notes)
    val labels = capLabels(caps)
    if (labels.isNotEmpty()) {
        Spacer(Modifier.size(8.dp))
        Text(
            "Detected capabilities",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        CapabilityChips(labels)
    }
    gpuApplicability(caps, gpu)?.let {
        Spacer(Modifier.size(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    EnvVarSection(manager = manager, isImported = isImported, envSourceId = envSourceId)
}

/**
 * A nested, independently-expandable "Environment variables" row inside [WrapperDetailBox]. Default
 * collapsed; opening it triggers the (bounded, off-main) scan ONLY when the user asks. Each entry
 * shows the friendly dictionary label ([WrapperSettingsDictionary.defFor]) plus the raw env key in a
 * dim monospace face. Read-only.
 */
@Composable
private fun EnvVarSection(
    manager: WrapperManager,
    isImported: Boolean,
    envSourceId: String,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(envSourceId) { mutableStateOf(false) }
    // null = not yet scanned (show a spinner); non-null = resolved (possibly empty) list.
    var keys by remember(envSourceId) { mutableStateOf<List<String>?>(null) }

    // Scan ON DEMAND: bundled slots are multi-MB zstd tars we skip on screen-open, so we only pay the
    // decompress when the user opens THIS section. Off the main thread; WrapperManager caches bundled
    // scans so a re-expand is instant. Imported wrappers read from their .meta (near-instant) but we
    // still hop off-main for uniformity + the older-import backfill path. Never crashes (empty list).
    LaunchedEffect(expanded, envSourceId) {
        if (expanded && keys == null) {
            keys = withContext(Dispatchers.IO) {
                runCatching {
                    if (isImported) manager.detectedEnvKeys(envSourceId)
                    else manager.scanBundledEnvKeys(envSourceId)
                }.getOrDefault(emptyList())
            }
        }
    }

    Spacer(Modifier.size(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = keys?.let { "Environment variables (${it.size})" } ?: "Environment variables",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
    if (expanded) {
        val resolved = keys
        when {
            resolved == null ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Scanning…",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            resolved.isEmpty() ->
                Text(
                    "No environment variables detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            else ->
                // Height-bounded + scrollable so a long list can't blow out the width-constrained dialog.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    resolved.forEach { key ->
                        EnvVarRow(label = WrapperSettingsDictionary.defFor(key).label, rawKey = key)
                    }
                }
        }
    }
}

/** One read-only env-var entry: friendly label (when the dictionary has one) over the raw key in a
 *  dim monospace face. A generic key (no dictionary hit) shows just the mono key — defFor returns the
 *  key as its own label, so we skip the duplicate line. */
@Composable
private fun EnvVarRow(label: String, rawKey: String) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        if (label != rawKey) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
        }
        Text(
            rawKey,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun WrapperSlotCard(
    slot: WrapperManager.WrapperSlot,
    caps: WrapperManager.WrapperCaps,
    gpu: GpuInfo,
    manager: WrapperManager,
    updateAvailable: Boolean = false,
    updating: Boolean = false,
    updateProgress: Int = 0,
    onUpdateFromCatalog: () -> Unit = {},
    onUpdate: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val stateLabel = context.getString(
        if (slot.isOverridden) R.string.wrapper_updated_label else R.string.wrapper_bundled
    )
    // Collapsed subtitle = filename + version only. The descriptive notes would push this past the
    // 2-line cap and truncate ("Bruno l…"), so they live in the expanded detail box below instead.
    val subtitle = buildString {
        append(slot.fileName)
        append(" · Version: ").append(slot.version).append(" (").append(stateLabel).append(")")
    }
    WrapperCardFrame(
        icon = Icons.Filled.Layers,
        title = slot.label,
        subtitle = subtitle,
        highlightSubtitle = slot.isOverridden,
        expandable = true,
        expanded = expanded,
        onToggleExpand = { expanded = !expanded },
        titleBadge = {
            // #132 feature-c: a newer catalog version is available for this bundled/overridden slot
            // (its catalogVersion is behind the live catalog). Shows progress while an update installs.
            when {
                updating -> {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        context.getString(R.string.wrapper_updating_progress, updateProgress),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                    )
                }
                updateAvailable -> {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            context.getString(R.string.wrapper_update_available),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onTertiaryContainer,
                        )
                    }
                }
            }
        },
        details = {
            WrapperDetailBox(
                fileName = slot.fileName,
                stateLabel = stateLabel,
                version = slot.version,
                notes = slot.notes,
                caps = caps,
                gpu = gpu,
                manager = manager,
                isImported = false,
                envSourceId = slot.fileName,
            )
        },
        trailing = {
            // #132 Part A: contextual ⋮ overflow menu (Update / Reset / Details) replacing the inline
            // action buttons. Bundled slots stay Update/Reset/Details — no settings editing (curated UI).
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = context.getString(R.string.wrapper_more_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    // #132 feature-c: one-tap update to the newer catalog build (only when available).
                    if (updateAvailable && !updating) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.wrapper_update_from_catalog), color = cs.primary) },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onUpdateFromCatalog() },
                        )
                        MenuItemDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(context.getString(R.string.wrapper_update)) },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onUpdate() },
                    )
                    if (slot.isOverridden) {
                        MenuItemDivider()
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.wrapper_reset)) },
                            leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onReset() },
                        )
                    }
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text(context.getString(R.string.wrapper_details)) },
                        leadingIcon = {
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { menuOpen = false; expanded = !expanded },
                    )
                }
            }
        },
    )
}

@Composable
private fun ImportedWrapperCard(
    imported: WrapperManager.Imported,
    manager: WrapperManager,
    gpu: GpuInfo,
    updateAvailable: Boolean = false,
    fromCatalog: Boolean = false,
    updating: Boolean = false,
    updateProgress: Int = 0,
    onUpdate: () -> Unit = {},
    onDelete: () -> Unit,
    onEditSettings: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val subtitle = "${imported.identifier}.tzst · " + context.getString(R.string.wrapper_imported_label)
    // Detected caps + version.txt for this import — read once per card (off the launch path).
    val caps = remember(imported.identifier) { manager.capsFor(imported.identifier) }
    val versionInfo = remember(imported.identifier) {
        manager.readVersionInfo(manager.importedFileFor(imported.identifier))
    }
    WrapperCardFrame(
        icon = Icons.Filled.Layers,
        title = imported.label,
        subtitle = subtitle,
        highlightSubtitle = true,
        expandable = true,
        expanded = expanded,
        onToggleExpand = { expanded = !expanded },
        titleBadge = {
            // #132: badge priority — updating (live progress) > update-available > from-catalog. A
            // catalog-installed import always carries provenance, so "From catalog" is the resting state
            // and the update chip supersedes it when a newer version is out.
            when {
                updating -> {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        context.getString(R.string.wrapper_updating_progress, updateProgress),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary,
                    )
                }
                updateAvailable -> {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            context.getString(R.string.wrapper_update_available),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onTertiaryContainer,
                        )
                    }
                }
                fromCatalog -> {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            context.getString(R.string.wrapper_from_catalog),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onTertiaryContainer,
                        )
                    }
                }
            }
        },
        details = {
            WrapperDetailBox(
                fileName = "${imported.identifier}.tzst",
                stateLabel = context.getString(R.string.wrapper_imported_label),
                version = versionInfo[0],
                notes = versionInfo[1],
                caps = caps,
                gpu = gpu,
                manager = manager,
                isImported = true,
                envSourceId = imported.identifier,
            )
        },
        trailing = {
            // #132 Part A: contextual ⋮ overflow menu (Edit settings / Delete / Details) replacing the
            // inline Delete button. "Edit settings" opens the per-wrapper hide/show dialog (Part B).
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = context.getString(R.string.wrapper_more_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    // #132: in-place update from the catalog, only when a newer version is available.
                    if (updateAvailable && !updating) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.wrapper_update), color = cs.primary) },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onUpdate() },
                        )
                        MenuItemDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(context.getString(R.string.wrapper_edit_settings)) },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onEditSettings() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text(context.getString(R.string.wrapper_delete), color = cs.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = cs.error, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text(context.getString(R.string.wrapper_details)) },
                        leadingIcon = {
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { menuOpen = false; expanded = !expanded },
                    )
                }
            }
        },
    )
}

/**
 * #132 Part B: per-wrapper "Edit settings" — hide/show the detected settings of an IMPORTED wrapper.
 * Lists every detected env key that is ELIGIBLE to be a setting (NOT in HANDLED_ENV_KEYS and NOT a
 * debug/diag key) as a checkbox: checked = shown, unchecked = hidden. The hidden set is persisted per
 * wrapper in its .meta ([WrapperManager.setHiddenKeys]); the config dialog + XSDA emission then skip
 * any hidden key. Read-only debug/handled keys never appear here (they're never settings). Never crashes.
 */
@Composable
private fun EditWrapperSettingsDialog(
    manager: WrapperManager,
    imported: WrapperManager.Imported,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // null = still loading; non-null = eligible keys (detected minus HANDLED minus debug).
    var keys by remember(imported.identifier) { mutableStateOf<List<String>?>(null) }
    // Currently-HIDDEN keys (unchecked). Seeded from .meta, mutated locally, persisted on OK.
    val hidden = remember(imported.identifier) { mutableStateListOf<String>() }

    LaunchedEffect(imported.identifier) {
        val eligible = withContext(Dispatchers.IO) {
            runCatching {
                manager.detectedEnvKeys(imported.identifier).filter {
                    it !in WrapperManager.HANDLED_ENV_KEYS && !WrapperManager.isDebugEnvKey(it) &&
                        !WrapperManager.isDriverInternalEnvKey(it)
                }
            }.getOrDefault(emptyList())
        }
        val stored = withContext(Dispatchers.IO) {
            runCatching { manager.hiddenKeys(imported.identifier) }.getOrDefault(emptySet())
        }
        hidden.clear()
        // Only keep hidden entries that are still eligible keys (stale ids drop off harmlessly).
        hidden.addAll(stored.filter { it in eligible })
        keys = eligible
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.wrapper_edit_settings_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    context.getString(R.string.wrapper_edit_settings_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                val resolved = keys
                when {
                    resolved == null ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Scanning…", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    resolved.isEmpty() ->
                        Text(
                            context.getString(R.string.wrapper_edit_settings_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    else ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            resolved.forEach { key ->
                                val shown = key !in hidden
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { if (shown) hidden.add(key) else hidden.remove(key) }
                                        .padding(vertical = 2.dp),
                                ) {
                                    Checkbox(
                                        checked = shown,
                                        onCheckedChange = { checked ->
                                            if (checked) hidden.remove(key) else hidden.add(key)
                                        },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            WrapperSettingsDictionary.defFor(key).label,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurface,
                                        )
                                        Text(
                                            key,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.onSurfaceVariant,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                manager.setHiddenKeys(imported.identifier, hidden.toSet())
                onDismiss()
            }) { Text(context.getString(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ImportWrapperCard(onClick: () -> Unit) {
    val context = LocalContext.current
    WrapperCardFrame(
        icon = Icons.Filled.Add,
        title = context.getString(R.string.wrapper_import_title),
        subtitle = context.getString(R.string.wrapper_import_subtitle),
        onClick = onClick,
    )
}

@Composable
private fun DownloadWrapperCard(onClick: () -> Unit) {
    val context = LocalContext.current
    WrapperCardFrame(
        icon = Icons.Filled.CloudDownload,
        title = context.getString(R.string.wrapper_catalog_title),
        subtitle = context.getString(R.string.wrapper_catalog_subtitle),
        onClick = onClick,
    )
}
