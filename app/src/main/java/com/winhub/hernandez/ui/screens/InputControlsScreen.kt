package com.winhub.hernandez.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    // Section (0=On-Screen, 1=Controller, 2=Assign, 3=Device) lives in the Deck tab host so the shell's
    // L1/R1 and the rail drive the same index.
    val deckTabs = LocalDeckTabs.current
    LaunchedEffect(deckTabs) { deckTabs.bind(4) }
    val selectedTab = deckTabs.index
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
        DeckConfigDialog(title = "New profile", onDismiss = { promptCreateName = false }, compact = true) {
            DeckConfigTextField("Profile name", name, { name = it })
            DeckDialogButtons("Create", enabled = name.isNotBlank(), onCancel = { promptCreateName = false }) {
                currentProfile = manager.createProfile(name)
                refreshProfiles()
                refreshControllers()
                promptCreateName = false
            }
        }
    }

    if (promptRenameOldName != null) {
        var name by remember { mutableStateOf(promptRenameOldName ?: "") }
        DeckConfigDialog(title = "Rename profile", onDismiss = { promptRenameOldName = null }, compact = true) {
            DeckConfigTextField("Profile name", name, { name = it })
            DeckDialogButtons("Save", enabled = name.isNotBlank(), onCancel = { promptRenameOldName = null }) {
                currentProfile?.setName(name)
                currentProfile?.save()
                refreshProfiles()
                promptRenameOldName = null
            }
        }
    }

    pendingConfirmation?.let { (messageRes, action) ->
        DeckConfigDialog(title = "Are you sure?", onDismiss = { pendingConfirmation = null }, compact = true) {
            DeckConfigNote(stringResource(messageRes))
            DeckDialogButtons(stringResource(R.string.ok), onCancel = { pendingConfirmation = null }) {
                pendingConfirmation = null
                action()
            }
        }
    }

    // Import source chooser. Open File = the in-app file manager (primary); system picker is secondary.
    if (showImportChooser) {
        val armImport = {
            importProfileCallback = { imported ->
                currentProfile = imported
                refreshProfiles()
                refreshControllers()
            }
        }
        DeckConfigDialog(
            title = stringResource(R.string.import_control_profile),
            onDismiss = { showImportChooser = false },
            compact = true,
        ) {
            Column(Modifier.fillMaxWidth()) {
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
                    DeckMenuDivider()
                    ImportSourceRow(
                        icon = Icons.Default.PhoneAndroid,
                        title = stringResource(R.string.pick_via_system),
                        subtitle = "Android document picker",
                    ) {
                        showImportChooser = false
                        armImport()
                        importLauncher.launch(arrayOf("*/*"))
                    }
                    DeckMenuDivider()
                    ImportSourceRow(
                        icon = Icons.Default.CloudDownload,
                        title = stringResource(R.string.download_file),
                        subtitle = "Community profiles from the online list",
                    ) {
                        showImportChooser = false
                        showDownloadDialog = true
                    }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                DeckButton("Cancel", primary = false) { showImportChooser = false }
            }
        }
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
            DeckConfigDialog(title = "Profiles", onDismiss = { showDownloadDialog = false }, compact = true) {
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = DeckPalette.primary)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    DeckButton("Cancel", primary = false) { showDownloadDialog = false }
                }
            }
        } else {
            // DeckConfigDialog is one scroll surface, so a long list never overlaps the button bar.
            DeckConfigDialog(
                title = "Download profiles",
                subtitle = "Tick the profiles to import",
                onDismiss = { showDownloadDialog = false },
            ) {
                    Column {
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
                                Text(item, color = DeckPalette.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                // View example: fetch the .icp and draw its on-screen layout (no import).
                                IconButton(onClick = { previewItem = item }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Visibility, contentDescription = "View example",
                                        tint = DeckPalette.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    DeckDialogButtons(
                        "Download",
                        enabled = selectedItems.isNotEmpty(),
                        onCancel = { showDownloadDialog = false },
                    ) {
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
                    }
            }
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
            DeckConfigDialog(
                title = itemName.removeSuffix(".icpx").removeSuffix(".icp"),
                onDismiss = { previewItem = null },
                compact = true,
            ) {
                DeckConfigNote("Preview of the layout on the game screen (not imported yet)")
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    val pp = previewProfile
                    when {
                        pp != null -> LayoutPreviewFrame(profile = pp, onTap = null)
                        previewFailed -> Text(stringResource(R.string.unable_to_download_file),
                            color = DeckPalette.error, fontSize = 13.sp)
                        else -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = DeckPalette.primary) }
                    }
                }
                DeckDialogButtons("Select", cancelLabel = "Close", onCancel = { previewItem = null }) {
                    val idx = items.indexOf(itemName)
                    if (idx >= 0) selectedItems = selectedItems + idx
                    previewItem = null
                }
            }
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

    // ── Deck layout ─────────────────────────────────────────────────
    // Header and rail stay put; only the section body scrolls. The shell's L1/R1 and the rail both
    // drive deckTabs, so a pad and a finger land on the same section.
    val sections = listOf("On-Screen", "Controller", "Assign", "Device")
    val displayText = if (selectedProfileIdx > 0 && selectedProfileIdx - 1 < profiles.size)
        profiles[selectedProfileIdx - 1].getName() else "No profile selected"
    val hasProfile = currentProfile != null

    // Shared by the Edit Layout button AND tapping the preview.
    val openLayoutEditor: () -> Unit = {
        val profile = currentProfile
        if (profile != null) {
            val intent = Intent(context, ControlsEditorActivity::class.java)
            intent.putExtra("profile_id", profile.id)
            context.startActivity(intent)
            (context as? Activity)?.overridePendingTransition(
                com.winhub.hernandez.R.anim.slide_in_up,
                com.winhub.hernandez.R.anim.slide_out_down
            )
        } else AppUtils.showToast(context, R.string.no_profile_selected)
    }
    val openBindings: (String) -> Unit = { controllerId ->
        val profile = currentProfile
        if (profile != null) {
            val intent = Intent(context, ExternalControllerBindingsActivity::class.java)
            intent.putExtra("profile_id", profile.id)
            intent.putExtra("controller_id", controllerId)
            context.startActivity(intent)
            (context as? Activity)?.overridePendingTransition(
                com.winhub.hernandez.R.anim.slide_in_up, com.winhub.hernandez.R.anim.slide_out_down
            )
        } else AppUtils.showToast(context, R.string.no_profile_selected)
    }

    // The frame draws the shared sliding focus ring that every other Deck page has.
    DeckDragFrame(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp)) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .deckPageBackground(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Controls",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeckPalette.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "How you play",
                        fontSize = 13.sp,
                        color = DeckPalette.onSurfaceVariant,
                    )
                }
                DeckButton("New profile") { promptCreateName = true }
            }
            Spacer(Modifier.height(10.dp))

            DeckRailLayout(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                rail = {
                    if (LocalDeckPortrait.current) {
                        DeckPortraitChips(sections, selectedTab) { deckTabs.select(it) }
                    } else {
                        DeckEditorCategoryRail(
                            labels = sections,
                            selected = selectedTab,
                            onSelect = { deckTabs.select(it) },
                            padFocus = false,
                            modifier = Modifier.width(200.dp).fillMaxHeight(),
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                },
            ) { bodyModifier ->
                // key() swaps the scroll state per section so each opens at the top.
                key(selectedTab) {
                    Column(
                        modifier = bodyModifier
                            .focusGroup()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DeckGroup("Profile") {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DeckPalette.primary),
                                    ) {
                                        Text(
                                            if (hasProfile) displayText.trim().take(1).uppercase() else "?",
                                            color = DeckPalette.onPrimary,
                                            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            displayText, color = DeckPalette.onBackground,
                                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        // Badges say what the profile HOLDS before you act on it.
                                        if (hasProfile && (profileHasLayout || profileBinds > 0)) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (profileHasLayout) ProfileBadge(
                                                    "Layout",
                                                    fg = DeckPalette.primary,
                                                    bg = DeckPalette.primary.copy(alpha = 0.15f),
                                                )
                                                if (profileBinds > 0) ProfileBadge(
                                                    "$profileBinds binds",
                                                    fg = DeckPalette.good,
                                                    bg = DeckPalette.good.copy(alpha = 0.15f),
                                                )
                                            }
                                        }
                                    }
                                    DeckValueChip("Switch", showProfileDropdown) { showProfileDropdown = true }
                                    IconButton(onClick = { showOverflow = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.MoreVert, "Profile actions", tint = DeckPalette.onSurfaceVariant)
                                    }
                                }

                                // Profile picker (▾).
                                DropdownMenu(
                                    expanded = showProfileDropdown,
                                    onDismissRequest = { showProfileDropdown = false },
                                    modifier = Modifier.background(DeckPalette.surfaceVariant),
                                ) {
                                    DropdownMenuItem(text = { Text("-- Select Profile --", color = DeckPalette.onBackground) }, onClick = {
                                        selectedProfileIdx = 0; loadProfile(0); showProfileDropdown = false
                                    })
                                    profiles.forEachIndexed { i, p ->
                                        DeckMenuDivider()
                                        DropdownMenuItem(text = { Text(p.getName(), color = DeckPalette.onBackground) }, onClick = {
                                            selectedProfileIdx = i + 1; loadProfile(i + 1); showProfileDropdown = false
                                        })
                                    }
                                }

                                // Overflow (⋯): profile CRUD (create/rename/duplicate/delete) + Share / Transfer group.
                                DropdownMenu(
                                    expanded = showOverflow,
                                    onDismissRequest = { showOverflow = false },
                                    modifier = Modifier.background(DeckPalette.surfaceVariant),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New profile", color = DeckPalette.onBackground) },
                                        leadingIcon = { Icon(Icons.Default.Add, null, tint = DeckPalette.onSurfaceVariant) },
                                        onClick = { showOverflow = false; promptCreateName = true },
                                    )
                                    DeckMenuDivider()
                                    DropdownMenuItem(
                                        text = { Text("Rename", color = DeckPalette.onBackground) },
                                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = DeckPalette.onSurfaceVariant) },
                                        onClick = {
                                            showOverflow = false
                                            if (currentProfile != null) promptRenameOldName = currentProfile?.getName()
                                            else AppUtils.showToast(context, R.string.no_profile_selected)
                                        },
                                    )
                                    DeckMenuDivider()
                                    DropdownMenuItem(
                                        text = { Text("Duplicate", color = DeckPalette.onBackground) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = DeckPalette.onSurfaceVariant) },
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
                                    DeckMenuDivider()
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = DeckPalette.onBackground) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = DeckPalette.onSurfaceVariant) },
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
                                    DeckMenuDivider()
                                    // Share / Transfer group header (disabled label, matches the app's grouped-menu idiom).
                                    DropdownMenuItem(
                                        enabled = false,
                                        text = {
                                            Text("Share / Transfer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                                color = DeckPalette.onSurfaceVariant)
                                        },
                                        onClick = {},
                                    )
                                    DeckMenuDivider()
                                    // Arrow direction reads as data flow relative to the app: import = INTO the app (down),
                                    // export = OUT of the app (up).
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.import_control_profile), color = DeckPalette.onBackground) },
                                        leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = DeckPalette.onSurfaceVariant) },
                                        onClick = { showOverflow = false; showImportChooser = true },
                                    )
                                    DeckMenuDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_control_profile_icpx), color = DeckPalette.onBackground) },
                                        leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = DeckPalette.onSurfaceVariant) },
                                        onClick = {
                                            showOverflow = false
                                            if (currentProfile != null) {
                                                val exported = manager.exportProfile(currentProfile!!)
                                                if (exported != null) AppUtils.showToast(context,
                                                    "${context.getString(R.string.profile_exported_to)} ${exported.path}")
                                            } else AppUtils.showToast(context, R.string.no_profile_selected)
                                        },
                                    )
                                    DeckMenuDivider()
                                    // Legacy ICP export (was a tooltip'd OutlinedButton; the tooltip copy now reads as the item label).
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_control_profile_icp_legacy), color = DeckPalette.onBackground) },
                                        leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = DeckPalette.onSurfaceVariant) },
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
                        }

                        when (selectedTab) {
                            // ── On-Screen: the touch overlay layout ──────────────
                            0 -> DeckGroup("On-screen controls") {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        "Touch overlay drawn on top of the game. Tap the preview to edit it.",
                                        color = DeckPalette.onSurfaceVariant, fontSize = 12.sp,
                                    )
                                    val cp = currentProfile
                                    if (cp != null && profileHasLayout) {
                                        OnScreenLayoutPreview(profileId = cp.id, refreshKey = layoutRev, onOpenEditor = openLayoutEditor)
                                    } else {
                                        Text(
                                            if (cp == null) "Pick a profile to see its touch overlay."
                                            else "No on-screen layout yet. Edit layout to add one.",
                                            color = DeckPalette.onSurfaceVariant, fontSize = 14.sp,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                        )
                                    }
                                    DeckButton("Edit layout", modifier = Modifier.fillMaxWidth(), onClick = openLayoutEditor)
                                }
                            }

                            // ── Controller: physical pads (test/bind + Default + per-pad) ──
                            1 -> {
                                // Opens the live pad-picture test (highlights, verified checklist, rumble Identify).
                                // No profile needed: it just reads the pad.
                                DeckHeroCard(
                                    title = "Test and bind controllers",
                                    subtitle = "Remap buttons and check every input registers",
                                ) { showControllerTest = true }

                                DeckGroup("Connected controllers") {
                                    // #333: the Default / Any Controller binding template. Newly connected
                                    // controllers inherit it, so a fresh pad is never blank. Always shown.
                                    DeckPadRow(
                                        tint = DeckPalette.onSurfaceVariant,
                                        title = "Default / Any controller",
                                        subtitle = "New controllers inherit these bindings",
                                        onClick = { openBindings(ControlsProfile.DEFAULT_CONTROLLER_ID) },
                                    ) {
                                        Icon(
                                            Icons.Default.ChevronRight, "Edit bindings",
                                            tint = DeckPalette.secondary, modifier = Modifier.size(18.dp),
                                        )
                                    }

                                    if (controllers.isEmpty()) {
                                        Text(
                                            "No controllers yet. Plug in or pair a pad and it shows up here.",
                                            color = DeckPalette.onSurfaceVariant, fontSize = 14.sp,
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        )
                                    } else {
                                        for (controller in controllers) {
                                            val bindingsCount = controller.getControllerBindingCount()
                                            val connected = controller.isConnected()
                                            // Battery (API 29+) + own-vibrator test rumble for a connected pad.
                                            val padDevice = if (connected)
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
                                            val status = (if (connected) "Connected" else "Not connected") +
                                                " · $bindingsCount bindings" +
                                                (if (padBattery >= 0) " · Battery $padBattery%" else "")

                                            DeckPadRow(
                                                tint = if (connected) DeckPalette.good else DeckPalette.error,
                                                title = controller.getName(),
                                                subtitle = status,
                                                onClick = { openBindings(controller.getId()) },
                                            ) {
                                                // Test rumble: pulse this pad's own vibrator (no game running at rest).
                                                if (padHasVibrator && padDevice != null) {
                                                    IconButton(onClick = { pulsePad(padDevice) }, modifier = Modifier.size(48.dp)) {
                                                        Icon(Icons.Default.Vibration, "Test rumble", tint = DeckPalette.onSurfaceVariant)
                                                    }
                                                }
                                                // #333: copy bindings from the Default template or another controller onto this one.
                                                Box {
                                                    IconButton(onClick = { copyMenuForId = controller.getId() }, modifier = Modifier.size(48.dp)) {
                                                        Icon(Icons.Default.ContentCopy, "Copy bindings", tint = DeckPalette.onSurfaceVariant)
                                                    }
                                                    DropdownMenu(
                                                        expanded = copyMenuForId == controller.getId(),
                                                        onDismissRequest = { copyMenuForId = null },
                                                                                                                modifier = Modifier.background(DeckPalette.surfaceVariant)
                                                    ) {
                                                        DropdownMenuItem(text = { Text("From Default / Any Controller", color = DeckPalette.onBackground) }, onClick = {
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
                                                            DeckMenuDivider()
                                                            DropdownMenuItem(text = { Text("From ${other.getName()}", color = DeckPalette.onBackground) }, onClick = {
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
                                                    }, modifier = Modifier.size(48.dp)) {
                                                        Icon(Icons.Default.Delete, "Remove", tint = DeckPalette.onSurfaceVariant)
                                                    }
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
                                SteamControllerSection()
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
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
            .border(1.dp, DeckPalette.line2, RoundedCornerShape(12.dp)),
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
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .deckOuterFocusStroke(interaction, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, null, tint = DeckPalette.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = DeckPalette.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = DeckPalette.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

/** Cancel + confirm pair at the foot of a Deck dialog; a disabled confirm stays visible but inert. */
@Composable
private fun DeckDialogButtons(
    confirm: String,
    onCancel: () -> Unit,
    enabled: Boolean = true,
    cancelLabel: String = "Cancel",
    onConfirm: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        DeckButton(cancelLabel, primary = false) { onCancel() }
        Spacer(Modifier.width(8.dp))
        DeckButton(confirm, modifier = Modifier.alpha(if (enabled) 1f else 0.42f)) { if (enabled) onConfirm() }
    }
}

@Composable
private fun DeckMenuDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
}

/** A small pill badge for the Profile bar (🖐 Layout / 🎮 N binds). Explicit fg/bg so callers pick
 *  the accent (theme primary) vs green (physical-binds) tint to match the screen's color language. */
@Composable
private fun ProfileBadge(text: String, fg: Color, bg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
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
 * Device-level gyroscope calibration. Only the zero-rate bias lives here — it's a property of the
 * hardware, not of a game; sensitivity/target/activator/invert stay with the container.
 */
@Composable
private fun GyroscopeSection() {
    val context = LocalContext.current
    val sensor = remember { GyroCalibrator.getSensor(context) }

    if (sensor == null) {
        DeckGroup("Gyroscope") {
            DeckRow(label = "No gyroscope on this device") {}
        }
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

    DeckGroup("Gyroscope") {
        Text(
            "Rest the device on a flat surface and calibrate to remove the gyroscope's resting drift.",
            color = DeckPalette.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
        )
        val summary = when {
            !calibrated -> "Not calibrated"
            abs(biasX) < GyroCalibrator.NEGLIGIBLE_BIAS && abs(biasY) < GyroCalibrator.NEGLIGIBLE_BIAS ->
                "Very little drift detected — your device already compensates"
            else -> "Bias removed: x %.4f, y %.4f rad/s".format(biasX, biasY)
        }
        DeckRow(label = summary, hint = status.takeIf { it.isNotEmpty() }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckButton(if (activeRun == null) "Calibrate" else "Calibrating…") {
                    if (activeRun != null) return@DeckButton
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
                }
                DeckButton("Reset", primary = false) {
                    if (!calibrated || activeRun != null) return@DeckButton
                    GyroCalibrator.clearBias(context)
                    status = "Calibration reset"
                    refreshBias()
                }
            }
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

    DeckGroup("Steam Controller") {
        Text(
            "Reads the Steam Controller directly, the way Steam does, so it works as a normal gamepad in " +
                "games. Pair it in Android's Bluetooth settings first, or connect it by USB. Other " +
                "controllers are not affected. Takes effect the next time a game starts.",
            color = DeckPalette.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
        )
        DeckRow(
            label = "Steam Controller support",
            control = {
                DeckToggle(enabled) { on ->
                    enabled = on
                    com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSteamControllerEnabled(context, on)
                    if (on) requestBluetooth()
                }
            },
        )
        if (enabled) {
            // Order = the labels; values are SteamControllerBackend.TRACKPAD_MOUSE_*.
            val trackpadLabels = listOf("Right trackpad", "Left trackpad", "Both trackpads", "Off")
            val trackpadModes = listOf(
                SteamControllerBackend.TRACKPAD_MOUSE_RIGHT, SteamControllerBackend.TRACKPAD_MOUSE_LEFT,
                SteamControllerBackend.TRACKPAD_MOUSE_BOTH, SteamControllerBackend.TRACKPAD_MOUSE_OFF,
            )
            DeckChoiceRow(
                label = "Trackpad mouse",
                hint = "Which trackpad moves the mouse. Clicking it is a left click; with both, the left trackpad clicks right.",
                options = trackpadLabels.map { it to it },
                selected = trackpadLabels[trackpadModes.indexOf(trackpadMode).coerceAtLeast(0)],
                onPick = {
                    trackpadMode = trackpadModes[trackpadLabels.indexOf(it).coerceAtLeast(0)]
                    com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSteamTrackpadMouseMode(context, trackpadMode)
                },
            )
            if (!bluetoothGranted) {
                DeckRow(
                    label = "Bluetooth permission is off",
                    hint = "Only a USB-connected controller will work.",
                ) {
                    if (Build.VERSION.SDK_INT >= 31) DeckButton("Allow", primary = false) { requestBluetooth() }
                }
            }
        }
    }

    if (enabled) {
        val paddleOptions = remember { steamPaddleOptions() }
        val paddleLabels = remember { paddleOptions.map { it.first } }
        val paddleChoices = remember(paddleLabels) { paddleLabels.map { it to it } }
        var paddles by remember {
            mutableStateOf(com.winhub.hernandez.ui.components.GlobalControllerPrefs.getSteamPaddleBindings(context).toList())
        }
        DeckGroup("Extra buttons") {
            Text(
                "What the four back buttons and the … button do, in every game. To change what the other " +
                    "buttons do, bind them on the Controller tab under Default / Any Controller (the Steam " +
                    "Controller uses those bindings).",
                color = DeckPalette.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp),
            )
            STEAM_PADDLE_NAMES.forEachIndexed { i, name ->
                DeckChoiceRow(
                    label = name,
                    options = paddleChoices,
                    selected = paddleOptions.firstOrNull { it.second == paddles[i] }?.first ?: paddleLabels[0],
                    onPick = { label ->
                        val binding = paddleOptions.firstOrNull { it.first == label }?.second ?: Binding.NONE
                        paddles = paddles.toMutableList().also { it[i] = binding }
                        com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSteamPaddleBinding(context, i, binding)
                    },
                )
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

    DeckGroup("Player slots") {
        DeckRow(
            label = "Defaults for new containers",
            hint = "Assign two devices to one player to share control. Existing containers keep their own settings.",
            control = {
                IconButton(onClick = { helpRes = R.string.help_player_slots }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Help, contentDescription = "What is this?",
                        tint = DeckPalette.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            },
        )
        val onScreenModeLabels = listOf("Keep on-screen player", "Yield Player 1 to pad", "Share the player")
        DeckChoiceRow(
            label = "On-screen priority",
            options = onScreenModeLabels.map { it to it },
            selected = onScreenModeLabels.getOrElse(onScreenMode) { onScreenModeLabels[0] },
            onPick = {
                onScreenMode = onScreenModeLabels.indexOf(it).coerceAtLeast(0)
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.setOnScreenMode(context, onScreenMode)
            },
        )
        DeckRow(
            label = "Hide on-screen controls when a controller connects",
            control = {
                DeckToggle(autoHideOnPad) { on ->
                    autoHideOnPad = on
                    com.winhub.hernandez.ui.components.GlobalControllerPrefs.setAutoHideControlsOnPad(context, on)
                }
            },
        )
        PlayerSlotsEditor(
            savedOverridesJson = slotOverridesJson,
            onOverridesChange = {
                slotOverridesJson = it
                com.winhub.hernandez.ui.components.GlobalControllerPrefs.setSlotOverridesJson(context, it)
            },
            deck = true,
        )
    }
}

/** Accent call-to-action card (Deck blue) for the section's one primary action. */
@Composable
private fun DeckHeroCard(title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = DeckPalette.groupShape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.primary)
            .deckRing(interaction, shape, focusColor = DeckPalette.onPrimary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(Icons.Default.Gamepad, null, tint = DeckPalette.onPrimary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckPalette.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DeckPalette.onPrimary.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

/** One controller row: tinted pad tile, name + status, hairline underneath. [trailing] holds its actions. */
@Composable
private fun DeckPadRow(
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .deckRing(interaction, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(shape)
                .background(DeckPalette.surfaceVariant),
        ) {
            Icon(Icons.Default.Gamepad, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = DeckPalette.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = DeckPalette.onSurfaceVariant, fontSize = 12.sp)
        }
        trailing()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
}
