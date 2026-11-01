package com.winhub.hernandez.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import com.winhub.hernandez.ControlsEditorActivity
import com.winhub.hernandez.ExternalControllerBindingsActivity
import com.winhub.hernandez.MainActivity
import com.winhub.hernandez.core.AppUtils
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.GyroCalibrator
import com.winhub.hernandez.core.HttpUtils
import com.winhub.hernandez.inputcontrols.Binding
import com.winhub.hernandez.inputcontrols.ControlsProfile
import com.winhub.hernandez.inputcontrols.ExternalController
import com.winhub.hernandez.inputcontrols.InputControlsManager
import com.winhub.hernandez.inputcontrols.SteamControllerBackend
import com.winhub.hernandez.ui.components.PlayerSlotsEditor
import com.winhub.hernandez.ui.controllertest.SettingsControllerTestDialog
import com.winhub.hernandez.util.InAppFilePicker
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputControlsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? MainActivity
    val manager = remember { InputControlsManager(context) }

    var profiles by remember { mutableStateOf(listOf<ControlsProfile>()) }
    var currentProfile by remember { mutableStateOf<ControlsProfile?>(null) }
    var selectedProfileIdx by remember { mutableStateOf(0) }
    // #333: neverEqualPolicy so a refresh that swaps in the same controllers (equal by id — our
    // ExternalController.equals ignores bindings) still recomposes; otherwise an updated binding count
    // (e.g. inherited from Default) never reaches the UI (the list kept showing a stale "0 Bindings").
    var controllers by remember { mutableStateOf(listOf<ExternalController>(), neverEqualPolicy()) }
    // #333: which controller row's "copy bindings from…" menu is open (by descriptor id), or null.
    var copyMenuForId by remember { mutableStateOf<String?>(null) }

    var showProfileDropdown by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    // Import ICP / ICpx source chooser (Open File / Pick via system / Download File) — Compose dialog.
    var showImportChooser by remember { mutableStateOf(false) }
    // Download Profiles → "view example": the community .icp file name whose layout popover is open.
    var previewItem by remember { mutableStateOf<String?>(null) }
    // Fetched community .icp text by file name, so re-opening a preview (or downloading it) is instant.
    val previewCache = remember { mutableMapOf<String, String>() }
    var isDownloading by remember { mutableStateOf(false) }
    var importProfileCallback by remember { mutableStateOf<((ControlsProfile) -> Unit)?>(null) }
    var promptCreateName by remember { mutableStateOf(false) }
    var promptRenameOldName by remember { mutableStateOf<String?>(null) }
    var pendingConfirmation by remember { mutableStateOf<Pair<Int, () -> Unit>?>(null) }
    // At-rest controller-test dialog (picture + live highlight + verified checklist + native Identify).
    var showControllerTest by remember { mutableStateOf(false) }
    // Tabbed redesign: the Profile bar's ⋯ overflow (profile CRUD + Share / Transfer).
    var showOverflow by remember { mutableStateOf(false) }
    // Selected tab (0=On-Screen, 1=Controller, 2=Assign, 3=Device). rememberSaveable so rotation keeps it.
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    // Extras — content badges for the selected profile (recomputed in refreshControllers()).
    var profileHasLayout by remember { mutableStateOf(false) }
    var profileBinds by remember { mutableStateOf(0) }
    // Bumped on ON_RESUME (e.g. returning from the layout editor) so the On-Screen preview re-reads disk.
    var layoutRev by remember { mutableStateOf(0) }

    fun refreshProfiles() {
        profiles = manager.getProfiles()
        val idx = if (currentProfile != null) {
            val i = profiles.indexOf(currentProfile)
            if (i >= 0) i + 1 else 0
        } else 0
        selectedProfileIdx = idx
    }

    fun refreshControllers() {
        val connected = ExternalController.getControllers()
        val loaded = currentProfile?.loadControllers()?.toMutableList() ?: mutableListOf()
        for (c in connected) if (c !in loaded) loaded.add(c)
        // #333: the Default/Any-Controller template (__default__) has its own dedicated top row, so
        // don't also render it as a regular controller row (that produced a duplicate box once saved).
        controllers = loaded.filter { it.getId() != com.winhub.hernandez.inputcontrols.ControlsProfile.DEFAULT_CONTROLLER_ID }
        // Extras — Profile-bar content badges: 🎮 N binds = total physical bindings across the profile's
        // controllers (incl. the Default template), 🖐 Layout = the profile's file has on-screen elements.
        profileBinds = loaded.sumOf { it.getControllerBindingCount() }
        profileHasLayout = currentProfile?.hasElementsOnDisk() ?: false
    }

    fun loadProfile(position: Int) {
        currentProfile = if (position > 0 && position - 1 < profiles.size) profiles[position - 1] else null
        refreshControllers()
    }

    DisposableEffect(Unit) {
        refreshProfiles()
        refreshControllers()
        // #333: live-refresh the External Controllers list on controller hot-plug while this screen is
        // open (it otherwise only rebuilds on load / profile switch), so connecting or removing a pad
        // updates the list without leaving and re-entering the screen.
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? android.hardware.input.InputManager
        val hotplugListener = object : android.hardware.input.InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { refreshControllers() }
            override fun onInputDeviceRemoved(deviceId: Int) { refreshControllers() }
            override fun onInputDeviceChanged(deviceId: Int) { refreshControllers() }
        }
        inputManager?.registerInputDeviceListener(hotplugListener, android.os.Handler(android.os.Looper.getMainLooper()))
        // #333: also refresh when the screen resumes (e.g. returning from the bindings editor), so a
        // controller's binding count / the Default template reflect edits made in that editor without
        // needing to leave and re-enter this screen.
        val lifecycle = lifecycleOwner.lifecycle
        val resumeObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { refreshControllers(); layoutRev++ }
        }
        lifecycle.addObserver(resumeObserver)
        onDispose {
            inputManager?.unregisterInputDeviceListener(hotplugListener)
            lifecycle.removeObserver(resumeObserver)
        }
    }

    // Shared import logic: read a control profile from any Uri (in-app file:// or SAF content://).
    fun importProfileFromUri(uri: Uri) {
        if (importProfileCallback != null) {
            try {
                val json = FileUtils.readString(context, uri)
                val imported = manager.importProfile(JSONObject(json))
                    ?: throw IllegalArgumentException("Unsupported control profile version")
                importProfileCallback!!(imported)
            } catch (_: Exception) {
                AppUtils.showToast(context, R.string.unable_to_import_profile)
            }
            importProfileCallback = null
        }
    }

    // System SAF picker (secondary).
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importProfileFromUri(uri) }

    // Built-in in-app file picker (primary).
    val importInAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedUri(result.data)?.let { importProfileFromUri(it) }
        }
    }

    if (promptCreateName) {
        var name by remember { mutableStateOf("") }
        OutlinedAlertDialog(
            onDismissRequest = { promptCreateName = false },
            title = { Text("Profile Name") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Enter profile name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        currentProfile = manager.createProfile(name)
                        refreshProfiles()
                        refreshControllers()
                        promptCreateName = false
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { promptCreateName = false }) { Text("Cancel") } }
        )
    }

    if (promptRenameOldName != null) {
        var name by remember { mutableStateOf(promptRenameOldName ?: "") }
        OutlinedAlertDialog(
            onDismissRequest = { promptRenameOldName = null },
            title = { Text("Profile Name") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        currentProfile?.setName(name)
                        currentProfile?.save()
                        refreshProfiles()
                        promptRenameOldName = null
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { promptRenameOldName = null }) { Text("Cancel") } }
        )
    }

    pendingConfirmation?.let { (messageRes, action) ->
        OutlinedAlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingConfirmation = null
                    action()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Import source chooser: replaces the legacy android.app.AlertDialog.Builder list with the app's
    // outlined Compose dialog. Open File = the in-app file manager (primary); system picker is secondary.
    if (showImportChooser) {
        val armImport = {
            importProfileCallback = { imported ->
                currentProfile = imported
                refreshProfiles()
                refreshControllers()
            }
        }
        OutlinedAlertDialog(
            onDismissRequest = { showImportChooser = false },
            title = { Text(stringResource(R.string.import_control_profile)) },
            text = {
                // Scrollable so the rows never collide with the button bar in landscape.
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    ImportSourceRow(
                        icon = Icons.Default.FolderOpen,
                        title = stringResource(R.string.open_file),
                        subtitle = "Browse with the in-app file manager (.icp / .icpx)",
                    ) {
                        showImportChooser = false
                        (context as? Activity)?.let { act ->
                            armImport()
                            importInAppLauncher.launch(
                                InAppFilePicker.buildIntent(
                                    act,
                                    InAppFilePicker.ICP,
                                    act.getString(R.string.select_control_profile),
                                )
                            )
                        }
                    }
                    MenuItemDivider()
                    ImportSourceRow(
                        icon = Icons.Default.PhoneAndroid,
                        title = stringResource(R.string.pick_via_system),
                        subtitle = "Android document picker",
                    ) {
                        showImportChooser = false
                        armImport()
                        importLauncher.launch(arrayOf("*/*"))
                    }
                    MenuItemDivider()
                    ImportSourceRow(
                        icon = Icons.Default.CloudDownload,
                        title = stringResource(R.string.download_file),
                        subtitle = "Community profiles from the online list",
                    ) {
                        showImportChooser = false
                        showDownloadDialog = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImportChooser = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showDownloadDialog) {
        var items by remember { mutableStateOf(listOf<String>()) }
        var selectedItems by remember { mutableStateOf(setOf<Int>()) }
        var isLoadingList by remember { mutableStateOf(true) }

        if (isLoadingList) {
            HttpUtils.download(
                "https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/index.txt"
            ) { content ->
                (context as? Activity)?.runOnUiThread {
                    isLoadingList = false
                    if (content != null) items = content.split("\n").filter { it.isNotBlank() }
                    else AppUtils.showToast(context, R.string.unable_to_load_profile_list)
                }
            }
        }

        if (isLoadingList) {
            OutlinedAlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text("Profiles") },
                text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() } },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") } }
            )
        } else {
            OutlinedAlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text("Download Profiles") },
                text = {
                    // Scroll the profile list inside the dialog: the list can be long
                    // (dozens of .icp files), so without this the bottom rows overflow
                    // the dialog's bounded height and overlap the Cancel/Download bar —
                    // worst in landscape where there's even less vertical room.
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        items.forEachIndexed { i, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedItems = if (i in selectedItems) selectedItems - i
                                    else selectedItems + i
                                }.padding(vertical = 2.dp)
                            ) {
                                com.winhub.hernandez.ui.components.Checkbox(checked = i in selectedItems, onCheckedChange = {
                                    selectedItems = if (it) selectedItems + i else selectedItems - i
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(item, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                // View example: fetch the .icp and draw its on-screen layout (no import).
                                IconButton(onClick = { previewItem = item }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Visibility, contentDescription = "View example",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (selectedItems.isNotEmpty()) {
                            isDownloading = true
                            showDownloadDialog = false
                            val positions = selectedItems.toList()
                            currentProfile = null
                            val processedCount = AtomicInteger()
                            val failedCount = AtomicInteger()
                            for (position in positions) {
                                HttpUtils.download(
                                    "https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/${items[position]}"
                                ) { content ->
                                    val imported = if (content != null) {
                                        try { manager.importProfile(JSONObject(content)) } catch (_: Exception) { null }
                                    } else null
                                    if (imported == null) failedCount.incrementAndGet()
                                    if (processedCount.incrementAndGet() == positions.size) {
                                        (context as? Activity)?.runOnUiThread {
                                            isDownloading = false
                                            refreshProfiles()
                                            refreshControllers()
                                            if (failedCount.get() > 0) {
                                                AppUtils.showToast(
                                                    context,
                                                    context.resources.getQuantityString(
                                                        R.plurals.profiles_not_imported,
                                                        failedCount.get(),
                                                        failedCount.get(),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }) { Text("Download") }
                },
                dismissButton = { TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") } }
            )
        }

        // "View example" popover: stacks above the list. Fetches the community .icp once (cached), builds a
        // throwaway file-less ControlsProfile.createPreview() and draws it through the same read-only
        // LayoutPreviewFrame the On-Screen tab uses, so the thumbnail is pixel-honest with the real overlay.
        previewItem?.let { itemName ->
            var previewProfile by remember(itemName) { mutableStateOf<ControlsProfile?>(null) }
            var previewFailed by remember(itemName) { mutableStateOf(false) }
            LaunchedEffect(itemName) {
                val toProfile: (String?) -> ControlsProfile? = { content ->
                    if (content == null) null
                    else try { ControlsProfile.createPreview(context, JSONObject(content)) } catch (_: Exception) { null }
                }
                val cached = previewCache[itemName]
                if (cached != null) {
                    previewProfile = toProfile(cached)
                    previewFailed = previewProfile == null
                } else {
                    HttpUtils.download(
                        "https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/$itemName"
                    ) { content ->
                        (context as? Activity)?.runOnUiThread {
                            if (content != null) previewCache[itemName] = content
                            if (previewItem == itemName) {
                                previewProfile = toProfile(content)
                                previewFailed = previewProfile == null
                            }
                        }
                    }
                }
            }
            OutlinedAlertDialog(
                onDismissRequest = { previewItem = null },
                title = { Text(itemName.removeSuffix(".icpx").removeSuffix(".icp")) },
                text = {
                    // Scrollable + width-driven aspect box → fits portrait and landscape alike.
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text("Preview — layout on the game screen (not imported yet)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        val pp = previewProfile
                        when {
                            pp != null -> LayoutPreviewFrame(profile = pp, onTap = null)
                            previewFailed -> Text(stringResource(R.string.unable_to_download_file),
                                color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            else -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val idx = items.indexOf(itemName)
                        if (idx >= 0) selectedItems = selectedItems + idx
                        previewItem = null
                    }) { Text("Select") }
                },
                dismissButton = { TextButton(onClick = { previewItem = null }) { Text("Close") } },
            )
        }
    }

    if (showControllerTest) {
        SettingsControllerTestDialog(
            onDismiss = { showControllerTest = false },
            profile = currentProfile,
            allProfiles = profiles,
            onSelectProfile = { p -> loadProfile(profiles.indexOf(p) + 1) },
            onCreateProfile = { name ->
                currentProfile = manager.createProfile(name)
                refreshProfiles()
                refreshControllers()
            },
            onRenameProfile = { promptRenameOldName = currentProfile?.name },
            onBindingsChanged = { refreshControllers() },
            onOpenAllBindings = {
                if (currentProfile != null) {
                    val cid = ExternalController.getControllers().firstOrNull()?.id
                        ?: com.winhub.hernandez.inputcontrols.ControlsProfile.DEFAULT_CONTROLLER_ID
                    val intent = Intent(context, ExternalControllerBindingsActivity::class.java)
                    intent.putExtra("profile_id", currentProfile!!.id)
                    intent.putExtra("controller_id", cid)
                    context.startActivity(intent)
                } else AppUtils.showToast(context, R.string.no_profile_selected)
            },
        )
    }

    // ── Tabbed layout ───────────────────────────────────────────────
    // Profile bar + tab row stay pinned at the top; only the selected tab's content scrolls.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // ── Pinned Profile bar ──────────────────────────────────────
        val displayText = if (selectedProfileIdx > 0 && selectedProfileIdx - 1 < profiles.size)
            profiles[selectedProfileIdx - 1].getName() else "-- Select Profile --"
        val hasProfile = currentProfile != null
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable { showProfileDropdown = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Accent avatar with the selected profile's initial.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (hasProfile) displayText.trim().take(1).uppercase() else "?",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayText, color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    // Content badges: tell you what the selected profile HOLDS before you act on it.
                    if (hasProfile && (profileHasLayout || profileBinds > 0)) {
                        Spacer(Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (profileHasLayout) ProfileBadge(
                                "🖐 Layout",
                                fg = MaterialTheme.colorScheme.primary,
                                bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            )
                            if (profileBinds > 0) ProfileBadge(
                                "🎮 $profileBinds binds",
                                // green = "has physical binds", echoing the connected-pad green used below.
                                fg = Color(0xFF7FCE82),
                                bg = Color(0xFF57B85A).copy(alpha = 0.16f),
                            )
                        }
                    }
                }
                IconButton(onClick = { showProfileDropdown = true }) {
                    Icon(Icons.Default.ArrowDropDown, "Select profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, "Profile actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Profile picker (▾).
            DropdownMenu(
                expanded = showProfileDropdown,
                onDismissRequest = { showProfileDropdown = false },
                modifier = Modifier.outlinedMenuCard(),
            ) {
                DropdownMenuItem(text = { Text("-- Select Profile --") }, onClick = {
                    selectedProfileIdx = 0; loadProfile(0); showProfileDropdown = false
                })
                profiles.forEachIndexed { i, p ->
                    MenuItemDivider()
                    DropdownMenuItem(text = { Text(p.getName()) }, onClick = {
                        selectedProfileIdx = i + 1; loadProfile(i + 1); showProfileDropdown = false
                    })
                }
            }

            // Overflow (⋯): profile CRUD (create/rename/duplicate/delete) + Share / Transfer group.
            DropdownMenu(
                expanded = showOverflow,
                onDismissRequest = { showOverflow = false },
                modifier = Modifier.outlinedMenuCard(),
            ) {
                DropdownMenuItem(
                    text = { Text("New profile") },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    onClick = { showOverflow = false; promptCreateName = true },
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        showOverflow = false
                        if (currentProfile != null) promptRenameOldName = currentProfile?.getName()
                        else AppUtils.showToast(context, R.string.no_profile_selected)
                    },
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = {
                        showOverflow = false
                        val profile = currentProfile
                        if (profile != null) {
                            pendingConfirmation = R.string.do_you_want_to_duplicate_this_profile to {
                                currentProfile = manager.duplicateProfile(profile)
                                refreshProfiles()
                                refreshControllers()
                            }
                        } else AppUtils.showToast(context, R.string.no_profile_selected)
                    },
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = {
                        showOverflow = false
                        val profile = currentProfile
                        if (profile != null) {
                            pendingConfirmation = R.string.do_you_want_to_remove_this_profile to {
                                manager.removeProfile(profile)
                                currentProfile = null
                                refreshProfiles()
                                refreshControllers()
                            }
                        } else AppUtils.showToast(context, R.string.no_profile_selected)
                    },
                )
                MenuItemDivider()
                // Share / Transfer group header (disabled label, matches the app's grouped-menu idiom).
                DropdownMenuItem(
                    enabled = false,
                    text = {
                        Text("Share / Transfer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = {},
                )
                MenuItemDivider()
                // Arrow direction reads as data flow relative to the app: import = INTO the app (down),
                // export = OUT of the app (up).
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_control_profile)) },
                    leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                    onClick = { showOverflow = false; showImportChooser = true },
                )
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_control_profile_icpx)) },
                    leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                    onClick = {
                        showOverflow = false
                        if (currentProfile != null) {
                            val exported = manager.exportProfile(currentProfile!!)
                            if (exported != null) AppUtils.showToast(context,
                                "${context.getString(R.string.profile_exported_to)} ${exported.path}")
                        } else AppUtils.showToast(context, R.string.no_profile_selected)
                    },
                )
                MenuItemDivider()
                // Legacy ICP export (was a tooltip'd OutlinedButton; the tooltip copy now reads as the item label).
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_control_profile_icp_legacy)) },
                    leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                    onClick = {
                        showOverflow = false
                        if (currentProfile != null) {
                            val exported = manager.exportLegacyProfile(currentProfile!!)
                            if (exported != null) AppUtils.showToast(context,
                                "${context.getString(R.string.profile_exported_to)} ${exported.path}")
                        } else AppUtils.showToast(context, R.string.no_profile_selected)
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Segmented tab row (On-Screen · Controller · Assign · Device) ──
        InputControlsTabs(selectedTab) { selectedTab = it }

        Spacer(Modifier.height(12.dp))

        // ── Tab content (scrolls independently; key() resets scroll on tab switch) ──
        // Box carries the weight (valid in the outer ColumnScope); key() swaps the scroll state per tab.
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
          key(selectedTab) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (selectedTab) {
                    // ── On-Screen: the touch overlay layout ──────────────
                    0 -> {
                        // Shared by the Edit Layout button AND tapping the preview.
                        val openLayoutEditor: () -> Unit = {
                            if (currentProfile != null) {
                                val intent = Intent(context, ControlsEditorActivity::class.java)
                                intent.putExtra("profile_id", currentProfile!!.id)
                                context.startActivity(intent)
                                (context as? Activity)?.overridePendingTransition(
                                    com.winhub.hernandez.R.anim.slide_in_up,
                                    com.winhub.hernandez.R.anim.slide_out_down
                                )
                            } else AppUtils.showToast(context, R.string.no_profile_selected)
                        }
                        Text("On-Screen Controls", color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Touch overlay drawn on top of the game.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Button(
                            onClick = openLayoutEditor,
                            // intentional: success green signals the primary "go/edit" action; kept off-theme by design
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit Layout", color = Color.White) } // intentional: white kept for contrast on the green fill

                        // Live read-only preview — only when the selected profile actually has an overlay.
                        val cp = currentProfile
                        if (cp != null && profileHasLayout) {
                            Text("Preview — your overlay on the game",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            OnScreenLayoutPreview(profileId = cp.id, refreshKey = layoutRev, onOpenEditor = openLayoutEditor)
                        } else if (cp != null) {
                            Text("No on-screen layout — tap Edit Layout to add one.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }

                    // ── Controller: physical pads (test/bind + Default + per-pad) ──
                    1 -> {
                        // Test controller: opens the live pad-picture test (button/stick/dpad/trigger highlight +
                        // verified checklist + native rumble Identify). No profile needed — it just reads the pad.
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                .clickable { showControllerTest = true }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Gamepad, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Test and Bind Physical Controllers", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Visually remap buttons + verify every input registers", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), fontSize = 12.sp)
                                }
                            }
                        }

                        Text("Connected controllers", color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                        // #333: the Default / Any Controller binding template — newly connected controllers inherit
                        // these mappings automatically, so a fresh controller is never blank. Always shown.
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (currentProfile != null) {
                                        val intent = Intent(context, ExternalControllerBindingsActivity::class.java)
                                        intent.putExtra("profile_id", currentProfile!!.id)
                                        intent.putExtra("controller_id", com.winhub.hernandez.inputcontrols.ControlsProfile.DEFAULT_CONTROLLER_ID)
                                        context.startActivity(intent)
                                        (context as? Activity)?.overridePendingTransition(
                                            com.winhub.hernandez.R.anim.slide_in_up, com.winhub.hernandez.R.anim.slide_out_down
                                        )
                                    } else AppUtils.showToast(context, R.string.no_profile_selected)
                                }.padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Gamepad, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Default / Any Controller", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                    Text("New controllers inherit these bindings", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }

                        if (controllers.isEmpty()) {
                            Text("No items to display", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                        } else {
                            for (controller in controllers) {
                                val bindingsCount = controller.getControllerBindingCount()
                                // intentional: connected (green) / disconnected (red) are distinct status colors, kept off-theme
                                val tintColor = if (controller.isConnected()) Color(0xFF4CAF50) else Color(0xFFE57373)
                                val accentColor = AColor.parseColor("#4CAF50")
                                // Extras — battery (API 29+) + own-vibrator test rumble for a connected pad.
                                val padDevice = if (controller.isConnected())
                                    android.view.InputDevice.getDevice(controller.getDeviceId()) else null
                                var padBattery = -1
                                if (padDevice != null && android.os.Build.VERSION.SDK_INT >= 29) {
                                    try {
                                        val bs = padDevice.batteryState
                                        if (bs != null && bs.isPresent) {
                                            val cap = bs.capacity
                                            if (cap >= 0f) padBattery = Math.round(cap * 100f)
                                        }
                                    } catch (_: Throwable) {}
                                }
                                val padHasVibrator = padDevice?.vibrator?.hasVibrator() == true

                                Box(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).clickable {
                                        if (currentProfile != null) {
                                            val intent = Intent(context, ExternalControllerBindingsActivity::class.java)
                                            intent.putExtra("profile_id", currentProfile!!.id)
                                            intent.putExtra("controller_id", controller.getId())
                                            context.startActivity(intent)
                                            (context as? Activity)?.overridePendingTransition(
                                                com.winhub.hernandez.R.anim.slide_in_up,
                                                com.winhub.hernandez.R.anim.slide_out_down
                                            )
                                        } else AppUtils.showToast(context, R.string.no_profile_selected)
                                    }.padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Gamepad, null, tint = tintColor, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(controller.getName(), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                            Text(
                                                if (padBattery >= 0) "$bindingsCount Bindings · 🔋 $padBattery%" else "$bindingsCount Bindings",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        }
                                        // Extras — test rumble: pulse this pad's own vibrator (no game running at rest).
                                        if (padHasVibrator && padDevice != null) {
                                            IconButton(onClick = { pulsePad(padDevice) }) {
                                                Icon(Icons.Default.Vibration, "Test rumble", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        // #333: copy bindings from the Default template or another controller onto this one.
                                        Box {
                                            IconButton(onClick = { copyMenuForId = controller.getId() }) {
                                                Icon(Icons.Default.ContentCopy, "Copy bindings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            DropdownMenu(
                                                expanded = copyMenuForId == controller.getId(),
                                                onDismissRequest = { copyMenuForId = null },
                                                // #333 polish: match the shared outlined-menu-card look.
                                                modifier = Modifier.outlinedMenuCard()
                                            ) {
                                                DropdownMenuItem(text = { Text("From Default / Any Controller") }, onClick = {
                                                    // #333: reload from disk first (the editor saves to a separate profile
                                                    // instance), and never apply an EMPTY source — that would wipe the
                                                    // target's bindings.
                                                    currentProfile?.loadControllers()
                                                    val src = currentProfile?.getController(com.winhub.hernandez.inputcontrols.ControlsProfile.DEFAULT_CONTROLLER_ID)
                                                    if (src != null && src.getControllerBindingCount() > 0) {
                                                        val tgt = currentProfile?.addController(controller.getId())
                                                        if (tgt != null) { tgt.copyBindingsFrom(src); currentProfile?.save(); refreshControllers() }
                                                    }
                                                    copyMenuForId = null
                                                })
                                                for (other in controllers) {
                                                    if (other.getId() == controller.getId() || other.getControllerBindingCount() == 0) continue
                                                    MenuItemDivider()
                                                    DropdownMenuItem(text = { Text("From ${other.getName()}") }, onClick = {
                                                        currentProfile?.loadControllers()
                                                        val src = currentProfile?.getController(other.getId())
                                                        if (src != null && src.getControllerBindingCount() > 0) {
                                                            val tgt = currentProfile?.addController(controller.getId())
                                                            if (tgt != null) { tgt.copyBindingsFrom(src); currentProfile?.save(); refreshControllers() }
                                                        }
                                                        copyMenuForId = null
                                                    })
                                                }
                                            }
                                        }
                                        if (bindingsCount > 0) {
                                            IconButton(onClick = {
                                                pendingConfirmation = R.string.do_you_want_to_remove_this_controller to {
                                                    currentProfile?.removeController(controller)
                                                    currentProfile?.save()
                                                    refreshControllers()
                                                }
                                            }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Assign: global default player slots for new containers ──
                    2 -> GlobalPlayerSlotsSection()

                    // ── Device: gyroscope calibration + Steam Controller ──
                    3 -> {
                        GyroscopeSection()
                        Spacer(Modifier.height(16.dp))
                        SteamControllerSection()
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
          }
        }
    }
}

/**
 * Live read-only thumbnail of a profile's on-screen (touch) overlay. Hosts the app's OWN
 * `InputControlsView` (edit-mode OFF) via `AndroidView` — the same widget the editor / in-game use, so
 * the preview is pixel-honest (GAMEHUB style, default overlay opacity). A transparent tap-catcher on
 * top keeps it non-interactive AND opens the editor on tap.
 *
 * GOTCHA: elements materialize their fractional positions against the HOSTING view's size, so we load a
 * FRESH `ControlsProfile` off disk here — laying it out against this small frame must never mutate the
 * pixel positions of the profile object the rest of the screen shares. The draw path is xServer-free
 * (see `InputControlsView.onDraw` / `ControlElement.draw`), so no XServer is needed at rest.
 */
@Composable
private fun OnScreenLayoutPreview(profileId: Int, refreshKey: Int, onOpenEditor: () -> Unit) {
    val context = LocalContext.current
    // Fresh throwaway instance; re-read when the profile changes OR we return from the editor (refreshKey).
    val previewProfile = remember(profileId, refreshKey) {
        InputControlsManager.loadProfile(context, ControlsProfile.getProfileFile(context, profileId))
    }
    LayoutPreviewFrame(profile = previewProfile, onTap = onOpenEditor)
}

/**
 * The read-only overlay thumbnail itself: a game-aspect box drawing [profile] through a non-interactive
 * InputControlsView. Shared by the On-Screen tab (on-disk profile, tap → editor) and the Download Profiles
 * "view example" popover (file-less ControlsProfile.createPreview, no tap action). [profile] must be an
 * instance nobody else lays out — see the GOTCHA on OnScreenLayoutPreview.
 */
@Composable
private fun LayoutPreviewFrame(profile: ControlsProfile?, onTap: (() -> Unit)?) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Game-screen aspect (device long:short, clamped; 16:9 fallback) so it reads as a mini game screen.
    val aspect = remember {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val a = if (w > 0f && h > 0f) maxOf(w, h) / minOf(w, h) else 16f / 9f
        a.coerceIn(1.3f, 2.4f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F0C0B)) // dark "game screen" ground
            .border(1.dp, cs.outline, RoundedCornerShape(12.dp)),
    ) {
        if (profile != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    com.winhub.hernandez.widget.InputControlsView(ctx).apply {
                        setEditMode(false)            // plain overlay: no grid / edit handles
                        setShowTouchscreenControls(true)
                        setOverlayOpacity(0.9f)       // crisp thumbnail
                        // Read-only thumbnail: never interactive on the view itself.
                        isClickable = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                    }
                },
                update = { view ->
                    view.setProfile(profile)          // fresh instance; loads lazily in onDraw once measured
                    view.invalidate()
                },
            )
            // Transparent tap-catcher ON TOP: swallows taps (keeps the render static) and, when given, acts.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .let { m -> if (onTap != null) m.clickable { onTap() } else m }
            )
        }
    }
}

/** One row of the Import source chooser: leading icon, title, muted one-line hint. */
@Composable
private fun ImportSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

/** A small pill badge for the Profile bar (🖐 Layout / 🎮 N binds). Explicit fg/bg so callers pick
 *  the accent (theme primary) vs green (physical-binds) tint to match the screen's color language. */
@Composable
private fun ProfileBadge(text: String, fg: Color, bg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** At-rest test rumble for a connected pad: pulse its OWN vibrator directly (no WinHandler / running
 *  game here), mirroring MainActivity.settingsControllerIdentify — VibratorManager (independent motors,
 *  API 31+) or the single vibrator otherwise. */
private fun pulsePad(device: android.view.InputDevice) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val vm = device.vibratorManager
            val ids = vm?.vibratorIds
            if (vm != null && ids != null && ids.isNotEmpty()) {
                val combo = android.os.CombinedVibration.startParallel()
                for (vid in ids) combo.addVibrator(vid, android.os.VibrationEffect.createOneShot(300L, 200))
                vm.vibrate(combo.combine())
                return
            }
        }
        val v = device.vibrator
        if (v != null && v.hasVibrator()) v.vibrate(android.os.VibrationEffect.createOneShot(300L, 200))
    } catch (_: Throwable) {}
}

/**
 * The 4-way segmented tab row (On-Screen · Controller · Assign · Device). Reads like the in-game
 * Controls tab toggle (TestBindToggle): an outlined pill with the active segment filled in the theme
 * accent (colorScheme.primary / onPrimary), inactive segments transparent with muted labels.
 */
@Composable
private fun InputControlsTabs(selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val labels = listOf("On-Screen", "Controller", "Assign", "Device")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerHigh)
            .border(1.dp, cs.outline, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) cs.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Device-level gyroscope calibration. Only the zero-rate bias lives here — it's a property of the
 * hardware, not of a game; sensitivity/target/activator/invert stay with the container.
 */
@Composable
private fun GyroscopeSection() {
    val context = LocalContext.current
    val sensor = remember { GyroCalibrator.getSensor(context) }

    Text("Gyroscope", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

    if (sensor == null) {
        Text("No gyroscope on this device", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        return
    }

    // Stored state, re-read after every calibrate/reset so the summary line can't drift from prefs.
    var calibrated by remember { mutableStateOf(GyroCalibrator.isCalibrated(context)) }
    var biasX by remember { mutableStateOf(0f) }
    var biasY by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var activeRun by remember { mutableStateOf<GyroCalibrator.Run?>(null) }

    fun refreshBias() {
        val bias = FloatArray(2)
        GyroCalibrator.loadBias(context, bias)
        biasX = bias[0]
        biasY = bias[1]
        calibrated = GyroCalibrator.isCalibrated(context)
    }

    DisposableEffect(Unit) {
        refreshBias()
        // A calibration left running past this screen would keep the sensor registered — and a leaked
        // gyroscope listener drains the battery for as long as the app lives.
        onDispose {
            activeRun?.cancel()
            activeRun = null
        }
    }

    FieldSet {
        Text(
            "Rest the device on a flat surface and calibrate to remove the gyroscope's resting drift.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))

        val summary = when {
            !calibrated -> "Not calibrated"
            abs(biasX) < GyroCalibrator.NEGLIGIBLE_BIAS && abs(biasY) < GyroCalibrator.NEGLIGIBLE_BIAS ->
                "Very little drift detected — your device already compensates"
            else -> "Bias removed: x %.4f, y %.4f rad/s".format(biasX, biasY)
        }
        Text(summary, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)

        if (status.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    status = "Hold still…"
                    activeRun = GyroCalibrator.calibrate(context) { result ->
                        activeRun = null
                        status = when (result) {
                            is GyroCalibrator.Result.Success ->
                                if (result.negligible) "Calibrated — nothing to remove" else "Calibrated"
                            GyroCalibrator.Result.Moved -> "Hold the device still and try again"
                            GyroCalibrator.Result.NotEnoughSamples -> "Couldn't sample — try again"
                            GyroCalibrator.Result.Unavailable -> "Gyroscope unavailable"
                        }
                        refreshBias()
                    }
                },
                enabled = activeRun == null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.weight(1f)
            ) { Text("Calibrate", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp) }
            Button(
                onClick = {
                    GyroCalibrator.clearBias(context)
                    status = "Calibration reset"
                    refreshBias()
                },
                enabled = calibrated && activeRun == null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                modifier = Modifier.weight(1f)
            ) { Text("Reset", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp) }
        }
    }
}

/**
 * Device-level Steam Controller support (SDL3's HIDAPI Steam drivers, see SteamControllerBackend).
 * Unlike the Player-Slots defaults this is LIVE: every game launch reads it. Off by default, and when
 * off SDL is never loaded. Turning it on asks for the Nearby-devices (Bluetooth) permission on
 * Android 12+; without it only a USB-connected controller works (the game session never prompts).
 */
@Composable
private fun SteamControllerSection() {
    val context = LocalContext.current
    var enabled by remember {
        mutableStateOf(com.winhub.hernandez.ui.components.GlobalControllerPrefs.isSteamControllerEnabled(context))
    }
    var trackpadMode by remember {
        mutableStateOf(com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSteamTrackpadMouseMode(context))
    }
    var bluetoothGranted by remember { mutableStateOf(SteamControllerBackend.hasBluetoothPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        bluetoothGranted = SteamControllerBackend.hasBluetoothPermission(context)
    }
    fun requestBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 && !bluetoothGranted)
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    Text("Steam Controller", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    FieldSet {
        Text(
            "Reads the Steam Controller directly, the way Steam does, so it works as a normal gamepad in " +
                "games. Pair it in Android's Bluetooth settings first, or connect it by USB. Other " +
                "controllers are not affected. Takes effect the next time a game starts.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        val onOff = listOf("On", "Off")
        LabeledDropdown(
            label = "Steam Controller support",
            options = onOff,
            selectedOption = if (enabled) onOff[0] else onOff[1],
            onSelect = {
                enabled = it == onOff[0]
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSteamControllerEnabled(context, enabled)
                if (enabled) requestBluetooth()
            },
        )
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            // Order = the labels; values are SteamControllerBackend.TRACKPAD_MOUSE_*.
            val trackpadLabels = listOf("Right trackpad", "Left trackpad", "Both trackpads", "Off")
            val trackpadModes = listOf(
                SteamControllerBackend.TRACKPAD_MOUSE_RIGHT, SteamControllerBackend.TRACKPAD_MOUSE_LEFT,
                SteamControllerBackend.TRACKPAD_MOUSE_BOTH, SteamControllerBackend.TRACKPAD_MOUSE_OFF,
            )
            LabeledDropdown(
                label = "Trackpad mouse",
                options = trackpadLabels,
                selectedOption = trackpadLabels[trackpadModes.indexOf(trackpadMode).coerceAtLeast(0)],
                onSelect = {
                    trackpadMode = trackpadModes[trackpadLabels.indexOf(it).coerceAtLeast(0)]
                    com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSteamTrackpadMouseMode(context, trackpadMode)
                },
            )
            Text(
                "Which trackpad moves the mouse. Clicking it is a left click; with both, the left trackpad " +
                    "clicks right.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("Extra buttons", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "What the four back buttons and the … button do, in every game. To change what the other " +
                    "buttons do, bind them on the Controller tab under Default / Any Controller (the Steam " +
                    "Controller uses those bindings).",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )
            val paddleOptions = remember { steamPaddleOptions() }
            val paddleLabels = remember { paddleOptions.map { it.first } }
            var paddles by remember {
                mutableStateOf(com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSteamPaddleBindings(context).toList())
            }
            STEAM_PADDLE_NAMES.forEachIndexed { i, name ->
                Spacer(Modifier.height(8.dp))
                LabeledDropdown(
                    label = name,
                    options = paddleLabels,
                    selectedOption = paddleOptions.firstOrNull { it.second == paddles[i] }?.first ?: paddleLabels[0],
                    onSelect = { label ->
                        val binding = paddleOptions.firstOrNull { it.first == label }?.second ?: Binding.NONE
                        paddles = paddles.toMutableList().also { it[i] = binding }
                        com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSteamPaddleBinding(context, i, binding)
                    },
                )
            }
            if (!bluetoothGranted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bluetooth permission is off, so only a USB-connected controller will work.",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp
                )
                if (Build.VERSION.SDK_INT >= 31) {
                    TextButton(onClick = { requestBluetooth() }) { Text("Allow Bluetooth") }
                }
            }
        }
    }
}

// SteamControllerBackend's extra-button order.
private val STEAM_PADDLE_NAMES = listOf(
    "L4 (upper left)", "L5 (lower left)", "R4 (upper right)", "R5 (lower right)",
    "… button (between the trackpads)",
)

/** Targets a back button can take: nothing, a gamepad button, a mouse click, or any keyboard key. */
private fun steamPaddleOptions(): List<Pair<String, Binding>> {
    val out = ArrayList<Pair<String, Binding>>()
    out += "Nothing" to Binding.NONE
    out += listOf(
        "A" to Binding.GAMEPAD_BUTTON_A, "B" to Binding.GAMEPAD_BUTTON_B,
        "X" to Binding.GAMEPAD_BUTTON_X, "Y" to Binding.GAMEPAD_BUTTON_Y,
        "LB" to Binding.GAMEPAD_BUTTON_L1, "RB" to Binding.GAMEPAD_BUTTON_R1,
        "LT" to Binding.GAMEPAD_BUTTON_L2, "RT" to Binding.GAMEPAD_BUTTON_R2,
        "L3 (stick click)" to Binding.GAMEPAD_BUTTON_L3, "R3 (stick click)" to Binding.GAMEPAD_BUTTON_R3,
        "Start" to Binding.GAMEPAD_BUTTON_START, "Back" to Binding.GAMEPAD_BUTTON_SELECT,
        "D-Pad Up" to Binding.GAMEPAD_DPAD_UP, "D-Pad Down" to Binding.GAMEPAD_DPAD_DOWN,
        "D-Pad Left" to Binding.GAMEPAD_DPAD_LEFT, "D-Pad Right" to Binding.GAMEPAD_DPAD_RIGHT,
        "Mouse left click" to Binding.MOUSE_LEFT_BUTTON,
        "Mouse right click" to Binding.MOUSE_RIGHT_BUTTON,
        "Mouse middle click" to Binding.MOUSE_MIDDLE_BUTTON,
    )
    for (b in Binding.keyboardBindingValues()) {
        if (b != Binding.NONE && b != Binding.SHOW_ANDROID_KEYBOARD) out += "Key: $b" to b
    }
    return out
}

/**
 * App-drawer (out-of-game) global default Player-Slots view. Edits a GLOBAL default stored in app
 * SharedPreferences (GlobalControllerPrefs) that is COPIED into a container's per-container settings
 * only when that container is CREATED — it is NOT a live launch-time fallback and editing it never
 * touches an already-created container. Reuses the same PlayerSlotsEditor + On-screen-priority dropdown
 * as the container/shortcut editors, writing the identical WinHandler slot-overrides JSON schema.
 */
@Composable
private fun GlobalPlayerSlotsSection() {
    val context = LocalContext.current
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }

    // Seeded from the global pref; every edit writes straight back so the default is always current.
    var slotOverridesJson by remember {
        mutableStateOf(com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSlotOverridesJson(context))
    }
    var onScreenMode by remember {
        mutableStateOf(com.winhub.hernandez.ui.components.GlobalControllerPrefs.getOnScreenMode(context))
    }
    // #333 global default (seeds new containers): auto-hide on-screen controls when a controller connects.
    var autoHideOnPad by remember {
        mutableStateOf(com.winhub.hernandez.ui.components.GlobalControllerPrefs.getAutoHideControlsOnPad(context))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Player Slots", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = { helpRes = R.string.help_player_slots }) {
            Icon(Icons.Default.Help, contentDescription = "What is this?",
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
    }
    FieldSet {
        Text(
            "The default for newly-created containers. Assign two devices to one player to share control. " +
                "Applies to new containers only — existing containers keep their own settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        val onScreenModeLabels = listOf("Keep on-screen player", "Yield Player 1 to pad", "Share the player")
        LabeledDropdown(
            label = "On-screen priority",
            options = onScreenModeLabels,
            selectedOption = onScreenModeLabels.getOrElse(onScreenMode) { onScreenModeLabels[0] },
            onSelect = {
                onScreenMode = onScreenModeLabels.indexOf(it).coerceAtLeast(0)
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.setOnScreenMode(context, onScreenMode)
            },
        )
        Spacer(Modifier.height(8.dp))
        // #333 auto-hide default for new containers. On/Off dropdown (no Switch in this screen).
        val autoHideLabels = listOf("On", "Off")
        LabeledDropdown(
            label = "Hide on-screen controls when a controller connects",
            options = autoHideLabels,
            selectedOption = if (autoHideOnPad) autoHideLabels[0] else autoHideLabels[1],
            onSelect = {
                autoHideOnPad = autoHideLabels.indexOf(it) == 0
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.setAutoHideControlsOnPad(context, autoHideOnPad)
            },
        )
        Spacer(Modifier.height(8.dp))
        PlayerSlotsEditor(
            savedOverridesJson = slotOverridesJson,
            onOverridesChange = {
                slotOverridesJson = it
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSlotOverridesJson(context, it)
            },
        )
    }
}

@Composable
private fun FieldSet(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        content()
    }
}
