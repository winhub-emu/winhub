package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import com.winhub.hernandez.ui.components.FilledTonalButton
import java.io.File
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.winhub.hernandez.ui.components.DraggableAddButton
import com.winhub.hernandez.ui.LocalTopBarActions
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import com.winhub.hernandez.R
import com.winhub.hernandez.XServerDisplayActivity
import com.winhub.hernandez.XrActivity
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerLayerUpdater
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.SteamFriendsAction
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.GameSaveBackup
import com.winhub.hernandez.util.InAppFilePicker
import com.winhub.hernandez.core.SaveLocator
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.store.UninstallResultBar
import com.winhub.hernandez.ui.components.EmuAccountConflictDialog
import com.winhub.hernandez.store.download.InstallProgressDialog
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.winhub.hernandez.ui.theme.OnSurface
import com.winhub.hernandez.ui.theme.OnSurfaceVariant
import com.winhub.hernandez.ui.theme.SurfaceVariant as SurfaceVariantColor
import com.winhub.hernandez.xenvironment.ImageFs

@Composable
fun ContainersScreen(
    onNavigateToDetail: (containerId: Int?) -> Unit,
    onOpenDeck: () -> Unit = {},
    vm: ContainersViewModel = viewModel(),
) {
    val containers by vm.containers.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val message by vm.message.collectAsState()
    val layerUpdates by vm.layerUpdates.collectAsState()
    val layerUpdateRemote by vm.layerUpdateRemote.collectAsState()
    val layerDownload by vm.layerDownload.collectAsState()
    val layerSnapshots by vm.layerSnapshots.collectAsState()
    val layerCurrentMissing by vm.layerCurrentMissing.collectAsState()
    val layerBusy by vm.layerBusy.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    // Surface one-shot VM messages (e.g. duplicate success/failure) as a Toast.
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.messageShown()
        }
    }

    // Refresh list whenever this screen resumes (e.g. returning from ContainerDetail)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Confirm-dialog state
    var confirmDialog by remember { mutableStateOf<ConfirmAction?>(null) }
    var storageInfoContainer by remember { mutableStateOf<Container?>(null) }
    var showImportPicker by remember { mutableStateOf(false) }

    // Backup / Restore game save flow (see SaveFlow). The engine posts its result on the main
    // thread, so we just flip these bits of Compose state as the flow advances.
    var saveFlow by remember { mutableStateOf<SaveFlow?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    // Emulator account ids a restore held back because the container already runs a different one.
    var emuConflicts by remember { mutableStateOf<List<GameSaveBackup.EmuIdConflict>>(emptyList()) }
    var pendingRestoreContainer by remember { mutableStateOf<Container?>(null) }

    // Restore a GameHub backup .zip. Shared for both the in-app picker (file:// path) and SAF.
    fun handleRestoreUri(uri: Uri?) {
        val target = pendingRestoreContainer
        pendingRestoreContainer = null
        if (uri != null && target != null) {
            saveFlow = SaveFlow.Confirm(target, uri, GameSaveBackup.gameNameFromUri(context, uri))
        }
    }

    // System SAF picker (secondary).
    val restorePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> handleRestoreUri(uri) }

    // Built-in in-app file picker (primary).
    val restoreInAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleRestoreUri(
            if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedUri(result.data) else null
        )
    }

    val topBarActions = LocalTopBarActions.current
    // LaunchedEffect — not SideEffect — so this runs in the same dispatcher queue as
    // MainActivity's route-change clear (parent enqueues first, we enqueue second and
    // run after). A SideEffect would set during commit and the parent's post-commit
    // clear would steamroll it on first navigation to this screen.
    LaunchedEffect(Unit) {
        topBarActions.value = {
            // Deck Mode — DroidDeck front end on its own full-bleed route.
            IconButton(onClick = onOpenDeck) {
                Icon(Icons.Filled.SportsEsports, contentDescription = "Deck mode", tint = androidx.compose.ui.graphics.Color.White)
            }
            // Steam friends + chat — only renders when signed in to Steam (login-gated internally).
            SteamFriendsAction()
            // New Container Defaults — opens the SAME container editor in "defaults mode" (the ✓ saves
            // the field state as the seed for future new containers) via the EDIT_DEFAULTS_ID sentinel.
            // Containers screen only. Sits next to the import action.
            IconButton(onClick = { onNavigateToDetail(ContainerDetailViewModel.EDIT_DEFAULTS_ID) }) {
                Icon(Icons.Filled.Settings, contentDescription = "New container defaults", tint = androidx.compose.ui.graphics.Color.White)
            }
            IconButton(onClick = { showImportPicker = true }) {
                Icon(Icons.Filled.FileDownload, contentDescription = "Import container", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (containers.isEmpty() && !isLoading) {
            Text(
                text = "No containers yet. Tap + to create one.",
                color = OnSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(containers, key = { it.id }) { container ->
                    ContainerItem(
                        container = container,
                        onRun = {
                            if (!XrActivity.isEnabled(context)) {
                                val intent = Intent(context, XServerDisplayActivity::class.java)
                                intent.putExtra("container_id", container.id)
                                if (container.displayBackend ==
                                        com.winhub.hernandez.container.Container.DISPLAY_BACKEND_WAYLAND)
                                    intent.putExtra("wayland_mode", true)
                                context.startActivity(intent)
                            } else {
                                XrActivity.openIntent(activity, container.id, null)
                            }
                        },
                        onEdit = { onNavigateToDetail(container.id) },
                        onDuplicate = {
                            confirmDialog = ConfirmAction.Duplicate(container)
                        },
                        onRemove = {
                            confirmDialog = ConfirmAction.Remove(container)
                        },
                        onExport = {
                            vm.exportContainer(container) { path ->
                                val msg = if (path != null)
                                    "Exported to $path"
                                else
                                    "Export failed or already exists"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        onInfo = { storageInfoContainer = container },
                        onBackupRestore = { saveFlow = SaveFlow.Fork(container) },
                        layerUpdate = layerUpdates[container.id],
                        layerUpdateRemote = layerUpdateRemote[container.id],
                        layerSnapshot = layerSnapshots[container.id],
                        onUpdateLayer = { target ->
                            confirmDialog = ConfirmAction.UpdateLayer(
                                container, target,
                                revertable = container.id !in layerCurrentMissing,
                                remote = layerUpdateRemote[container.id]?.takeIf { it.entryName == target },
                            )
                        },
                        onRevertLayer = { snapshot -> confirmDialog = ConfirmAction.RevertLayer(container, snapshot) },
                        onLayerHelp = { target ->
                            confirmDialog = ConfirmAction.LayerHelp(
                                container, target, layerSnapshots[container.id],
                                remote = layerUpdateRemote[container.id]?.takeIf { it.entryName == target },
                            )
                        },
                    )
                }
            }
        }

        // FAB — long-press and slide it along the bottom to get it off a card's play/overflow
        // buttons. Position is remembered per screen.
        DraggableAddButton(
            prefKey = "containers",
            onClick = {
                if (ImageFs.find(context).isValid()) onNavigateToDetail(null)
            },
            buttonModifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add container", tint = MaterialTheme.colorScheme.onPrimary)
        }

        // Loading overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Themed, auto-dismissing result bar for the backup/restore flow (avoids the
        // black-box system Toast on this ROM). Lives inside this full-screen Box so it
        // overlays the list at the bottom.
        resultMessage?.let { msg ->
            UninstallResultBar(message = msg, onTimeout = { resultMessage = null })
        }
        } // end inner Box(weight)
    } // end Column

    // Import picker dialog
    if (showImportPicker) {
        val backups = remember { vm.availableBackups() }
        OutlinedAlertDialog(
            onDismissRequest = { showImportPicker = false },
            title = { Text("Import Container") },
            text = {
                if (backups.isEmpty()) {
                    Text("No exported containers found in Downloads/Winlator/Backups/Containers/.")
                } else {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        backups.forEach { dir ->
                            TextButton(
                                onClick = {
                                    showImportPicker = false
                                    vm.importContainer(dir) {
                                        Toast.makeText(context, "Container imported: ${dir.name}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(dir.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportPicker = false }) { Text("Cancel") }
            },
        )
    }

    // Confirm dialogs
    confirmDialog?.let { action ->
        when (action) {
            is ConfirmAction.Duplicate -> {
                OutlinedAlertDialog(
                    onDismissRequest = { confirmDialog = null },
                    title = { Text("Duplicate container?") },
                    text = { Text("Duplicate \"${action.container.name}\"?") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDialog = null
                            vm.duplicate(action.container) {}
                        }) { Text("Duplicate") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDialog = null }) { Text("Cancel") }
                    },
                )
            }
            is ConfirmAction.Remove -> {
                OutlinedAlertDialog(
                    onDismissRequest = { confirmDialog = null },
                    title = { Text("Remove container?") },
                    text = { Text("Remove \"${action.container.name}\" permanently?") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDialog = null
                            vm.remove(action.container, context) {}
                        }) { Text("Remove") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDialog = null }) { Text("Cancel") }
                    },
                )
            }
            is ConfirmAction.UpdateLayer -> {
                val newLabel = ContainerLayerUpdater.codeLabel(action.target)
                val oldLabel = ContainerLayerUpdater.codeLabel(action.container.wineVersion)
                val remote = action.remote
                OutlinedAlertDialog(
                    onDismissRequest = { confirmDialog = null },
                    title = { Text(if (remote != null) "Download and update layer to $newLabel?" else "Update layer to $newLabel?") },
                    text = {
                        Text(
                            "\"${action.container.name}\" moves from ${action.container.wineVersion} to ${action.target}.\n\n" +
                                (if (remote != null)
                                    "The $newLabel layer is not on this device yet: it is downloaded from the catalog" +
                                        layerSizeHint(remote) + " and installed first, then the container is updated.\n\n"
                                else "") +
                                "What changes: the Wine/Proton layer files (system32/syswow64 builtin DLLs are refreshed; " +
                                "DXVK, FEX/Box64 and game-installed files are left as they are). Wine finishes its own " +
                                "prefix update on the next launch.\n\n" +
                                "What is kept: games, saves, shortcuts and container settings.\n\n" +
                                (if (action.revertable)
                                    "The $oldLabel layer stays installed and a backup of the registry is taken, so you can " +
                                        "revert from this menu. "
                                else
                                    "The $oldLabel layer is no longer installed on this device, so this update cannot be " +
                                        "reverted afterwards (a registry backup is still taken). ") +
                                "Make sure nothing is running in this container."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDialog = null
                            if (remote != null) vm.downloadAndUpdateLayer(action.container, remote)
                            else vm.updateLayer(action.container, action.target)
                        }) { Text(if (remote != null) "Download & update" else "Update") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDialog = null }) { Text("Cancel") }
                    },
                )
            }
            is ConfirmAction.LayerHelp -> {
                val newLabel = ContainerLayerUpdater.codeLabel(action.target)
                OutlinedAlertDialog(
                    onDismissRequest = { confirmDialog = null },
                    title = { Text("About layer updates") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                "A layer is the set of Wine/Proton files a container runs on. " +
                                    "\"${action.container.name}\" runs on ${action.container.wineVersion}; a newer build " +
                                    "of the same layer, ${action.target}, is " +
                                    (if (action.remote != null) "available in the catalog. The $newLabel layer will be " +
                                        "downloaded" + layerSizeHint(action.remote) + " first."
                                    else "installed.") + "\n\n" +
                                    "What the update changes: only Wine's own files inside the container are refreshed to " +
                                    "the new version. DXVK, FEX/Box64 and anything a game installed are left as they are; " +
                                    "Wine finishes its own prefix update on the next launch.\n\n" +
                                    "What is kept: installed games, saves, shortcuts and container settings.\n\n" +
                                    "The old layer stays installed and a backup of the registry is taken first, so " +
                                    "\"Revert layer\" is available from this container's menu afterwards.\n\n" +
                                    "Close the game before updating."
                            )
                            if (action.snapshot != null) {
                                Text(
                                    text = "A ${ContainerLayerUpdater.codeLabel(action.snapshot.oldEntry)} snapshot from an " +
                                        "earlier update exists — revert is available in the menu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDialog = ConfirmAction.UpdateLayer(
                                action.container, action.target,
                                revertable = action.container.id !in layerCurrentMissing,
                                remote = action.remote,
                            )
                        }) { Text(if (action.remote != null) "Download & update to $newLabel…" else "Update to $newLabel…") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDialog = null }) { Text("Close") }
                    },
                )
            }
            is ConfirmAction.RevertLayer -> {
                val oldLabel = ContainerLayerUpdater.codeLabel(action.snapshot.oldEntry)
                OutlinedAlertDialog(
                    onDismissRequest = { confirmDialog = null },
                    title = { Text("Revert layer to $oldLabel?") },
                    text = {
                        Text(
                            "\"${action.container.name}\" goes back from ${action.container.wineVersion} to " +
                                "${action.snapshot.oldEntry}.\n\n" +
                                "The registry is restored from the backup taken before the update (registry changes made " +
                                "since then are discarded) and the builtin DLLs are refreshed from the $oldLabel layer. " +
                                "Games, saves, shortcuts and container settings are kept. Requires the $oldLabel layer to " +
                                "still be installed."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDialog = null
                            vm.revertLayer(action.container, action.snapshot)
                        }) { Text("Revert") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDialog = null }) { Text("Cancel") }
                    },
                )
            }
        }
    }

    layerBusy?.let { SaveFlowProgressDialog(message = it) }
    // Catalog download+install that precedes a remote layer update — the same popup the component
    // sheet and the Contents hub use (live phase/percent, Cancel while running). On success the VM
    // closes it itself and continues with the update; a failure stays up until closed.
    layerDownload?.let { st ->
        InstallProgressDialog(
            state = st,
            onCancel = { vm.cancelLayerDownload() },
            onDismiss = { vm.layerDownloadDismissed() },
        )
    }

    // Storage info dialog
    storageInfoContainer?.let { container ->
        StorageInfoDialog(container = container, onDismiss = { storageInfoContainer = null })
    }

    // ---- Backup / Restore game save flow ----
    when (val flow = saveFlow) {
        null -> {}
        is SaveFlow.Fork -> OutlinedAlertDialog(
            onDismissRequest = { saveFlow = null },
            title = { Text("Game saves") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(flow.container.name, color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    SaveFlowCaution()
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = { saveFlow = SaveFlow.BackupScope(flow.container) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Back up this save", modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { saveFlow = SaveFlow.RestoreSource(flow.container) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore a save", modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { saveFlow = null }) { Text("Cancel") } },
        )
        is SaveFlow.RestoreSource -> OutlinedAlertDialog(
            onDismissRequest = { saveFlow = null },
            title = { Text("Restore a save") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text("Choose the backup source.", color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = {
                            pendingRestoreContainer = flow.container
                            saveFlow = null
                            restoreInAppLauncher.launch(
                                InAppFilePicker.buildIntent(context, InAppFilePicker.SAVE, "Select backup (.zip)")
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("GameHub backup (.zip)", modifier = Modifier.weight(1f)) }
                    TextButton(
                        onClick = {
                            pendingRestoreContainer = flow.container
                            saveFlow = null
                            restorePickerLauncher.launch("application/zip")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pick via system…", modifier = Modifier.weight(1f)) }
                }
            },
            confirmButton = { TextButton(onClick = { saveFlow = SaveFlow.Fork(flow.container) }) { Text("Back") } },
        )
        is SaveFlow.Confirm -> OutlinedAlertDialog(
            onDismissRequest = { saveFlow = null },
            title = { Text("Restore game save?") },
            text = {
                Text(
                    "Restore the GameHub backup of \"${flow.gameName}\" into container " +
                        "\"${flow.container.name}\"?\n\nThis may overwrite existing save data."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = flow.container
                    val uri = flow.uri
                    val name = flow.gameName
                    saveFlow = null
                    busyMessage = "Restoring $name…"
                    GameSaveBackup.restore(context, uri, c) { r ->
                        busyMessage = null
                        resultMessage = if (r.ok) "Restored ${r.filesWritten} files to \"${c.name}\""
                        else "Restore failed: ${r.error ?: "unknown error"}"
                        emuConflicts = r.emuConflicts
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { saveFlow = null }) { Text("Cancel") } },
        )
        is SaveFlow.BackupScope -> OutlinedAlertDialog(
            onDismissRequest = { saveFlow = null },
            title = { Text(stringResource(R.string.save_backup_scope_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.save_backup_scope_prompt), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = { saveFlow = SaveFlow.BackupFormat(flow.container, null, flow.container.name) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.save_backup_scope_whole), modifier = Modifier.weight(1f)) }
                    TextButton(
                        onClick = { saveFlow = SaveFlow.GamePicker(flow.container) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${stringResource(R.string.save_backup_scope_game)} ▸", modifier = Modifier.weight(1f)) }
                }
            },
            confirmButton = { TextButton(onClick = { saveFlow = SaveFlow.Fork(flow.container) }) { Text("Back") } },
        )
        is SaveFlow.GamePicker -> {
            val shortcuts = remember(flow.container.id) { vm.shortcutsFor(flow.container) }
            OutlinedAlertDialog(
                onDismissRequest = { saveFlow = null },
                title = { Text(stringResource(R.string.save_backup_pick_game_title)) },
                text = {
                    if (shortcuts.isEmpty()) {
                        Text(stringResource(R.string.save_backup_pick_game_empty), color = OnSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(shortcuts, key = { it.file.path }) { sc ->
                                GamePickerRow(sc) {
                                    saveFlow = SaveFlow.GameSaves(
                                        container = flow.container,
                                        gameName = sc.name,
                                        gameKey = SaveLocator.gameKey(sc.wmClass, sc.file.name),
                                        exePath = sc.path ?: "",
                                        wmClass = sc.wmClass ?: "",
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { saveFlow = SaveFlow.BackupScope(flow.container) }) { Text("Back") } },
            )
        }
        is SaveFlow.GameSaves -> GameSavesDialog(
            container = flow.container,
            gameName = flow.gameName,
            gameKey = flow.gameKey,
            exePath = flow.exePath,
            wmClass = flow.wmClass,
            onBack = { saveFlow = SaveFlow.GamePicker(flow.container) },
            onCancel = { saveFlow = null },
            onNext = { roots ->
                saveFlow = SaveFlow.BackupFormat(flow.container, roots, flow.gameName)
            },
        )
        is SaveFlow.BackupFormat -> OutlinedAlertDialog(
            onDismissRequest = { saveFlow = null },
            title = { Text(stringResource(R.string.save_backup_format_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.save_backup_format_prompt), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    BackupFormatOption(
                        title = stringResource(R.string.save_backup_format_gamehub),
                        subtitle = stringResource(R.string.save_backup_format_gamehub_sub),
                    ) { runScopedBackup(context, flow, GameSaveBackup.BackupLayout.GAMEHUB, { saveFlow = null }, { busyMessage = it }, { resultMessage = it }) }
                    BackupFormatOption(
                        title = stringResource(R.string.save_backup_format_winlator),
                        subtitle = stringResource(R.string.save_backup_format_winlator_sub),
                    ) { runScopedBackup(context, flow, GameSaveBackup.BackupLayout.WINLATOR, { saveFlow = null }, { busyMessage = it }, { resultMessage = it }) }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveFlow = SaveFlow.BackupScope(flow.container) }) { Text("Back") }
            },
        )
    }

    busyMessage?.let { SaveFlowProgressDialog(message = it) }

    EmuAccountConflictDialog(conflicts = emuConflicts) { applied, _ ->
        emuConflicts = emptyList()
        if (applied > 0) resultMessage = "Emulator account switched to the backup's — relaunch the game"
    }
}

@Composable
private fun ContainerItem(
    container: Container,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit,
    onInfo: () -> Unit,
    onBackupRestore: () -> Unit,
    layerUpdate: String? = null,
    layerUpdateRemote: ContainerLayerUpdater.CatalogCandidate? = null,
    layerSnapshot: ContainerLayerUpdater.Snapshot? = null,
    onUpdateLayer: (target: String) -> Unit = {},
    onRevertLayer: (snapshot: ContainerLayerUpdater.Snapshot) -> Unit = {},
    onLayerHelp: (target: String) -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Layer-update target for both the card button and the overflow menu: an INSTALLED newer layer
    // wins over a catalog-only one (no download needed then).
    val layerTarget = layerUpdate ?: layerUpdateRemote?.entryName
    val layerNeedsDownload = layerUpdate == null && layerUpdateRemote != null

    val subtitle = listOf(container.wineVersion, container.screenSize)
        .filter { it.isNotEmpty() }.joinToString(" · ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            // Containers have no poster art, so the container glyph sits straight on the card
            // (no framed tile — it read as a black square, by request).
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.icon_menu_container),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Info column: name, wineVersion · resolution subtitle.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = container.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // A newer build of this container's layer line is installed — or, failing that, in the
                // online catalog (ContainerLayerUpdater): compact update button + "?" explainer right
                // under the layer subtitle. The catalog case adds a download glyph in front of the
                // label (the layer is fetched first). The overflow menu carries the same action (and
                // Revert) for discoverability.
                if (layerTarget != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        // Outline a shade lighter than the tonal fill so the pair reads as one control
                        // against the dark card (user request on the r1 screenshot).
                        val layerOutline = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        FilledTonalButton(
                            onClick = { onUpdateLayer(layerTarget) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, layerOutline),
                            modifier = Modifier.height(26.dp),
                        ) {
                            if (layerNeedsDownload) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = "Download needed",
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = "Update layer \u2192 ${ContainerLayerUpdater.codeLabel(layerTarget)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { onLayerHelp(layerTarget) },
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HelpOutline,
                                contentDescription = "About layer updates",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play button (moved before settings)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onRun),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.icon_popup_menu_run),
                    contentDescription = "Run",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Settings button
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = OnSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { menuExpanded = false; onDuplicate() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menuExpanded = false; onRemove() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Export") },
                        leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                        onClick = { menuExpanded = false; onExport() },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Backup / Restore save") },
                        leadingIcon = { Icon(Icons.Filled.SettingsBackupRestore, null) },
                        onClick = { menuExpanded = false; onBackupRestore() },
                    )
                    if (layerTarget != null) {
                        val menuTarget: String = layerTarget
                        MenuItemDivider()
                        DropdownMenuItem(
                            text = {
                                val label = ContainerLayerUpdater.codeLabel(menuTarget)
                                Text(if (layerNeedsDownload) "Download & update layer to $label…" else "Update layer to $label…")
                            },
                            leadingIcon = { Icon(if (layerNeedsDownload) Icons.Filled.CloudDownload else Icons.Filled.Upgrade, null) },
                            onClick = { menuExpanded = false; onUpdateLayer(menuTarget) },
                        )
                    }
                    if (layerSnapshot != null) {
                        MenuItemDivider()
                        DropdownMenuItem(
                            text = { Text("Revert layer to ${ContainerLayerUpdater.codeLabel(layerSnapshot.oldEntry)}…") },
                            leadingIcon = { Icon(Icons.Filled.SettingsBackupRestore, null) },
                            onClick = { menuExpanded = false; onRevertLayer(layerSnapshot) },
                        )
                    }
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Info") },
                        leadingIcon = { Icon(Icons.Filled.Info, null) },
                        onClick = { menuExpanded = false; onInfo() },
                    )
                }
            }
        }
    }
}

private sealed class ConfirmAction {
    data class Duplicate(val container: Container) : ConfirmAction()
    data class Remove(val container: Container) : ConfirmAction()
    /**
     * In-place layer update to [target] (ContainerLayerUpdater). [remote] is set when the target is a
     * catalog-only build that has to be downloaded and installed first.
     */
    data class UpdateLayer(
        val container: Container,
        val target: String,
        val revertable: Boolean = true,
        val remote: ContainerLayerUpdater.CatalogCandidate? = null,
    ) : ConfirmAction()
    /** Revert a previous layer update using its [snapshot]. */
    data class RevertLayer(val container: Container, val snapshot: ContainerLayerUpdater.Snapshot) : ConfirmAction()
    /** The "?" explainer next to the card's update button. */
    data class LayerHelp(
        val container: Container,
        val target: String,
        val snapshot: ContainerLayerUpdater.Snapshot?,
        val remote: ContainerLayerUpdater.CatalogCandidate? = null,
    ) : ConfirmAction()
}

/** " (~340 MB)" when the catalog download size is known, "" otherwise. */
private fun layerSizeHint(remote: ContainerLayerUpdater.CatalogCandidate): String =
    remote.sizeBytes?.let { " (~${ContainerLayerUpdater.formatSize(it)})" } ?: ""

/** Steps of the Backup / Restore game-save flow launched from a container's overflow menu. */
private sealed class SaveFlow {
    /** Direction picker: back up vs restore. */
    data class Fork(val container: Container) : SaveFlow()
    /** Back up → scope picker: whole container vs a specific game. */
    data class BackupScope(val container: Container) : SaveFlow()
    /** Back up → pick which installed game to scope to. */
    data class GamePicker(val container: Container) : SaveFlow()
    /** Back up → discover + confirm the chosen game's save folders. */
    data class GameSaves(
        val container: Container,
        val gameName: String,
        val gameKey: String,
        val exePath: String,
        val wmClass: String,
    ) : SaveFlow()
    /** Restore → choose a backup source (GameHub backup today; more later). */
    data class RestoreSource(val container: Container) : SaveFlow()
    /** Restore → a .zip has been picked; confirm before overwriting. */
    data class Confirm(val container: Container, val uri: android.net.Uri, val gameName: String) : SaveFlow()
    /**
     * Back up → choose the output layout. [roots] == null is a whole-container backup; otherwise
     * the profile-relative subtrees to include. [gameName] names the resulting zip.
     */
    data class BackupFormat(
        val container: Container,
        val roots: List<String>?,
        val gameName: String?,
    ) : SaveFlow()
}

/** Fires a scoped (or whole-container) backup off the UI thread and routes the result to the bars. */
private fun runScopedBackup(
    context: android.content.Context,
    flow: SaveFlow.BackupFormat,
    layout: GameSaveBackup.BackupLayout,
    dismiss: () -> Unit,
    setBusy: (String?) -> Unit,
    setResult: (String?) -> Unit,
) {
    val name = flow.gameName ?: flow.container.name
    dismiss()
    setBusy("Backing up $name…")
    GameSaveBackup.backup(context, flow.container, flow.roots, flow.gameName, layout) { r ->
        setBusy(null)
        setResult(
            if (r.ok) "Saved ${r.fileCount} files → ${r.path?.substringAfterLast('/')}"
            else "Backup failed: ${r.error ?: "unknown error"}"
        )
    }
}

/** One selectable row in the game picker: cover art (fallback icon) + game name. */
@Composable
private fun GamePickerRow(shortcut: Shortcut, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = shortcut.coverArt ?: shortcut.icon
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceVariantColor),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.icon_wine),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            shortcut.name,
            color = OnSurface,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A two-line format option in the BackupFormat dialog. */
@Composable
private fun BackupFormatOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Discover → confirm for a single game. On enter it either restores the remembered folder set from
 * the sidecar (pre-ticked, skip discovery) or scans the container for candidates. The user ticks
 * folders, can add one manually (browser rooted at the xuser profile, escape-guarded), and on Next
 * the selection is persisted to the sidecar and handed to the format step.
 */
@Composable
private fun GameSavesDialog(
    container: Container,
    gameName: String,
    gameKey: String,
    exePath: String,
    wmClass: String,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onNext: (List<String>) -> Unit,
) {
    val rows = remember(gameKey) { mutableStateListOf<SaveLocator.Candidate>() }
    val checked = remember(gameKey) { mutableStateListOf<String>() }
    var loading by remember(gameKey) { mutableStateOf(true) }
    var fromSidecar by remember(gameKey) { mutableStateOf(false) }
    var rescan by remember(gameKey) { mutableStateOf(0) }
    var showManualAdd by remember(gameKey) { mutableStateOf(false) }
    var escapeError by remember(gameKey) { mutableStateOf(false) }

    LaunchedEffect(gameKey, rescan) {
        loading = true
        val (list, sidecar) = withContext(Dispatchers.IO) {
            val saved = if (rescan == 0) SaveLocator.loadMap(container, gameKey) else null
            if (saved != null && saved.roots.isNotEmpty()) {
                saved.roots.map { rel ->
                    SaveLocator.Candidate(rel, rel.substringAfterLast('/'), 100, SaveLocator.sizeOf(container, rel))
                } to true
            } else {
                SaveLocator.discover(container, gameName, exePath, wmClass) to false
            }
        }
        rows.clear(); rows.addAll(list)
        checked.clear(); checked.addAll(list.map { it.relPath })
        fromSidecar = sidecar
        loading = false
    }

    if (loading) {
        SaveFlowProgressDialog(message = stringResource(R.string.save_backup_discovering))
        return
    }

    if (showManualAdd) {
        ManualAddDialog(
            container = container,
            onCancel = { showManualAdd = false },
            onPick = { dir ->
                showManualAdd = false
                val rel = SaveLocator.relativizeUnderProfile(container, dir)
                if (rel == null) {
                    escapeError = true
                } else {
                    escapeError = false
                    if (rows.none { it.relPath == rel }) {
                        rows.add(SaveLocator.Candidate(rel, dir.name, 100, SaveLocator.sizeOf(container, rel)))
                    }
                    if (!checked.contains(rel)) checked.add(rel)
                }
            },
        )
    }

    // Warn if any two ticked folders are nested (manual add can create that; shared subtree copied once).
    val overlap = checked.any { a -> checked.any { b -> a != b && (a.startsWith("$b/")) } }

    OutlinedAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(gameName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(
                        when {
                            rows.isEmpty() -> R.string.save_backup_confirm_none
                            fromSidecar -> R.string.save_backup_confirm_remembered
                            else -> R.string.save_backup_confirm_prompt
                        }
                    ),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(4.dp))
                rows.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked.contains(c.relPath)) checked.remove(c.relPath) else checked.add(c.relPath)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked.contains(c.relPath),
                            onCheckedChange = {
                                if (it) { if (!checked.contains(c.relPath)) checked.add(c.relPath) }
                                else checked.remove(c.relPath)
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(c.relPath, color = OnSurface, style = MaterialTheme.typography.bodyMedium)
                            Text(StringUtils.formatBytes(c.sizeBytes), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (escapeError) {
                    Text(stringResource(R.string.save_backup_add_folder_escape), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (overlap) {
                    Text(stringResource(R.string.save_backup_overlap_note), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.size(4.dp))
                Row {
                    TextButton(onClick = { showManualAdd = true }) { Text(stringResource(R.string.save_backup_add_folder)) }
                    if (fromSidecar) {
                        TextButton(onClick = { rescan += 1 }) { Text(stringResource(R.string.save_backup_rescan)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = checked.isNotEmpty(),
                onClick = {
                    val roots = checked.toList()
                    // Persist the taught mapping off the UI thread, then advance to the format step.
                    Thread { SaveLocator.saveMap(container, gameKey, gameName, roots) }.start()
                    onNext(roots)
                },
            ) { Text(stringResource(R.string.save_backup_next)) }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
    )
}

/**
 * Minimal in-app folder browser rooted at the container's xuser profile. Used for "add folder…"
 * because SAF can't enumerate the app-private internal storage where container profiles live; the
 * root confinement makes an escape impossible by construction (and the caller re-checks anyway).
 */
@Composable
private fun ManualAddDialog(container: Container, onPick: (File) -> Unit, onCancel: () -> Unit) {
    val root = remember(container.id) { SaveLocator.profileDir(container) }
    var cur by remember(container.id) { mutableStateOf(root) }
    val subDirs = remember(cur.path) {
        cur.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    OutlinedAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.save_backup_add_folder_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp)) {
                Text(
                    "…/" + (if (cur.path == root.path) "" else cur.relativeTo(root).path.replace(File.separatorChar, '/')),
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(6.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    if (cur.path != root.path) {
                        item {
                            TextButton(onClick = { cur.parentFile?.let { cur = it } }, modifier = Modifier.fillMaxWidth()) {
                                Text("..", modifier = Modifier.weight(1f), color = OnSurface)
                            }
                        }
                    }
                    items(subDirs, key = { it.path }) { d ->
                        TextButton(onClick = { cur = d }, modifier = Modifier.fillMaxWidth()) {
                            Text(d.name, modifier = Modifier.weight(1f), color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cur.path != root.path, onClick = { onPick(cur) }) {
                Text(stringResource(R.string.save_backup_add_folder_here))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/**
 * Caution shown on the Backup/Restore fork: save handling is not exhaustive (some games write
 * saves outside the folders we scan) so users should verify a backup before wiping originals.
 */
@Composable
private fun SaveFlowCaution() {
    // Translucent accent tint (not an opaque fill) so it composits over the dialog's own
    // surface and reads as part of the theme instead of a black slab.
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.40f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "⚠  " + stringResource(R.string.save_flow_caution_title),
                color = OnSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.save_flow_caution_body),
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Blocking, non-dismissable spinner shown while a backup/restore runs off the UI thread. */
@Composable
private fun SaveFlowProgressDialog(message: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun StorageInfoDialog(container: Container, onDismiss: () -> Unit) {
    var driveCSize by remember { mutableLongStateOf(0L) }
    var cacheSize  by remember { mutableLongStateOf(0L) }
    var totalSize  by remember { mutableLongStateOf(0L) }
    val internalStorageSize = remember { FileUtils.getInternalStorageSize() }
    val progress = if (internalStorageSize > 0)
        ((totalSize.toFloat() / internalStorageSize) * 100f).coerceIn(0f, 100f)
    else 0f

    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    LaunchedEffect(container) {
        val rootDir   = container.getRootDir()
        val driveCDir = File(rootDir, ".wine/drive_c")
        val cacheDir  = File(rootDir, ".cache")
        launch(Dispatchers.IO) {
            FileUtils.getSizeAsync(driveCDir) { size ->
                handler.post { driveCSize += size; totalSize += size }
            }
        }
        launch(Dispatchers.IO) {
            FileUtils.getSizeAsync(cacheDir) { size ->
                handler.post { cacheSize += size; totalSize += size }
            }
        }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage Info") },
        text = {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            ) {
                // Left column — Drive C / Cache / Total sizes
                Column(
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Drive C", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(StringUtils.formatBytes(driveCSize), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = androidx.compose.ui.Modifier.size(6.dp))
                    Text("Cache", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(StringUtils.formatBytes(cacheSize), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = androidx.compose.ui.Modifier.size(6.dp))
                    Text("Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(StringUtils.formatBytes(totalSize), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                // Right column — circular progress + label
                Column(
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = androidx.compose.ui.Modifier.size(100.dp),
                            strokeWidth = 10.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text("${progress.toInt()}%", fontSize = 16.sp)
                    }
                    Spacer(modifier = androidx.compose.ui.Modifier.size(6.dp))
                    Text(
                        "Estimated used space",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                FileUtils.clear(File(container.getRootDir(), ".cache"))
                container.putExtra("desktopTheme", null)
                container.saveData()
                onDismiss()
            }) { Text("Clear Cache") }
        },
    )
}
