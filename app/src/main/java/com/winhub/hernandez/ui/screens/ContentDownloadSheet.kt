package com.winhub.hernandez.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import com.winhub.hernandez.ui.components.Switch
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.Downloader
import com.winhub.hernandez.core.TarCompressorUtils
import com.winhub.hernandez.store.download.ContentDownloadPhase
import com.winhub.hernandez.store.download.ContentDownloadRegistry
import com.winhub.hernandez.store.download.ContentDownloadState
import com.winhub.hernandez.store.download.InstallProgressDialog
import com.winhub.hernandez.store.download.formatEta
import com.winhub.hernandez.store.download.startContentDownload
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.screens.adrenodownload.AdrenoDriverListPane
import com.winhub.hernandez.ui.screens.contents.RemoteSourceRepository
import kotlin.math.absoluteValue
import com.winhub.hernandez.util.ImportEtaTracker
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Adrenotools-style download menu for the given content type(s).
 * Works on any screen (including inside other dialogs).
 * After install/remove, calls [onContentChanged] so the parent can refresh version lists.
 * [inUseKey] (optional) marks the version the container currently uses (matched best-effort).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDownloadSheet(
    contentTypes: List<ContentProfile.ContentType>,
    onDismiss: () -> Unit,
    onContentChanged: () -> Unit,
    inUseKey: String? = null,
) {
    val context = LocalContext.current
    val cm = remember { ContentsManager(context) }

    // Landscape turns the sheet into a master-detail "WIN Components" browser: the left rail lists ALL
    // installable component categories (see RAIL_TYPES) and the passed contentTypes only decides which
    // one is selected first. Portrait stays byte-for-byte scoped to the passed contentTypes as today.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val effectiveTypes = remember(isLandscape, contentTypes) { if (isLandscape) RAIL_TYPES else contentTypes }

    // GPU Drivers is a landscape-rail-only category with NO ContentProfile.ContentType — it lives in the
    // adrenotools subsystem, so it's kept out of effectiveTypes/loadProfiles and special-cased: selecting
    // it swaps the right pane to the shared AdrenoDriverListPane. driverRefreshKey re-reads the installed
    // count for the rail badge after an install here.
    var gpuDriversSelected by remember { mutableStateOf(false) }
    var showDriverManage by remember { mutableStateOf(false) }
    var driverRefreshKey by remember { mutableStateOf(0) }
    val adrenoDriverCount = remember(driverRefreshKey) {
        runCatching { AdrenotoolsManager(context).enumarateInstalledDrivers().size }.getOrDefault(0)
    }

    var profiles by remember { mutableStateOf<List<ContentProfile>>(emptyList()) }
    // Component download/install state now lives on a PROCESS-lifetime registry (see
    // ContentDownloadController) rather than composition-scoped state, so it keeps advancing while
    // the app is backgrounded and the sheet re-attaches to an in-flight download on reopen.
    val contentStates by ContentDownloadRegistry.states.collectAsState()
    // The catalog progress popup holds its OWN snapshot (not a live registry read): while the registry
    // entry lives we keep [catalogShown] synced to it (live progress + the terminal flip); once the
    // launcher's finally removes the entry (~2s after done) we KEEP the last snapshot so the finished
    // popup stays up until the user taps Done/Close. Seeded on tap; re-attaches to any still-running
    // download when the sheet (re)enters composition.
    var catalogKey by remember { mutableStateOf<String?>(null) }
    var catalogShown by remember { mutableStateOf<ContentDownloadState?>(null) }
    LaunchedEffect(contentStates) {
        val k = catalogKey
        if (k == null) {
            contentStates.entries.firstOrNull { !it.value.terminal }?.let {
                catalogKey = it.key
                catalogShown = it.value
            }
        } else {
            contentStates[k]?.let { catalogShown = it }
        }
    }
    // The content-card install dialog for LOCAL-FILE import (catalog downloads render from the
    // the held catalog snapshot instead) — null when idle. Uses the SAME shared popup + the SAME
    // ContentDownloadState model as the catalog path, so both look identical.
    var installDialog by remember { mutableStateOf<ContentDownloadState?>(null) }
    var showInfoProfile by remember { mutableStateOf<ContentProfile?>(null) }
    var confirmRemoveProfile by remember { mutableStateOf<ContentProfile?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoadingRemote by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    // When more than one content type is shown (Wine + Proton), chips at the top filter the list.
    var selectedType by remember(contentTypes) { mutableStateOf(contentTypes.first()) }

    // ── Community-repo source toggle (opt-in; persisted globally). OFF = Official contents.json only.
    val srcPrefs = remember { context.getSharedPreferences(COMMUNITY_PREFS, Context.MODE_PRIVATE) }
    var includeCommunity by remember { mutableStateOf(srcPrefs.getBoolean(COMMUNITY_KEY, false)) }
    var communityGroups by remember { mutableStateOf<List<CommunityGroup>>(emptyList()) }
    var communityLoading by remember { mutableStateOf(false) }
    // Which groups are shown via the chip row. Official is always available and can't be emptied out.
    var activeSources by remember { mutableStateOf(setOf(OFFICIAL_KEY)) }
    var selectAllOnLoad by remember { mutableStateOf(includeCommunity) }

    LaunchedEffect(effectiveTypes, refreshKey) {
        val json = withContext(Dispatchers.IO) {
            Downloader.downloadString(ContentsManager.REMOTE_PROFILES)
        }
        if (json != null) cm.setRemoteProfiles(json) else cm.syncContents()
        loadProfiles(cm, effectiveTypes) { profiles = it }
        isLoadingRemote = false
    }

    // Fetch community versions for the ACTIVE type from the Contents-screen COMPONENT sources — only
    // when the toggle is on (network-heavy), lazily and per type. Driver sources are never consulted.
    LaunchedEffect(includeCommunity, selectedType, refreshKey) {
        if (!includeCommunity) {
            communityGroups = emptyList(); communityLoading = false
            return@LaunchedEffect
        }
        communityLoading = true
        val typeStr = selectedType.toString()
        val groups = withContext(Dispatchers.IO) {
            val repo = RemoteSourceRepository(context)
            repo.getAllSources()
                // The hub lists the Official catalog as a built-in repository; this sheet already renders
                // that same contents.json as its own Official group, so skip it here (never "community").
                .filter { !it.isOfficial && sourceSupportsType(it, typeStr) }
                .mapNotNull { s ->
                    val items = runCatching { repo.fetchFromSource(s, typeStr) }.getOrDefault(emptyList())
                        .map { it.toProfile(selectedType) }
                        .distinctBy { it.remoteUrl }
                    if (items.isEmpty()) null else CommunityGroup(s.name, items)
                }
        }
        communityGroups = groups
        if (selectAllOnLoad) {
            activeSources = setOf(OFFICIAL_KEY) + groups.map { it.name }
            selectAllOnLoad = false
        }
        communityLoading = false
    }

    // Manual "install from file" picker. Handles both the in-app picker (selectedFile path,
    // wrapped as a file:// Uri) and the system SAF picker (result.data.data).
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        (result.data?.data ?: InAppFilePicker.pickedUri(result.data))?.let { uri ->
            // Version/desc aren't known until the archive is parsed — seed the card with the filename +
            // (single-type screens) the content type, then let the % bar carry the rest.
            val fname = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: "Content file"
            installDialog = ContentDownloadState(
                key = "localfile",
                title = fname,
                type = contentTypes.singleOrNull()?.toString(),
                verName = fname,
                phase = ContentDownloadPhase.INSTALLING,
            )
            installContent(context, cm, uri, onProgress = { f, _ ->
                installDialog = installDialog?.copy(fraction = f, phase = ContentDownloadPhase.INSTALLING)
            }) { ok ->
                if (ok) {
                    installDialog = installDialog?.copy(fraction = 1f, phase = ContentDownloadPhase.DONE)
                    loadProfiles(cm, effectiveTypes) { profiles = it }
                    refreshKey++
                    onContentChanged()
                } else {
                    installDialog = installDialog?.copy(phase = ContentDownloadPhase.ERROR, error = "Install failed.")
                }
            }
        }
    }

    // Info sub-dialog
    showInfoProfile?.let { profile ->
        OutlinedAlertDialog(
            onDismissRequest = { showInfoProfile = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Content Info", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                androidx.compose.foundation.rememberScrollState().let { scroll ->
                    Column(Modifier.verticalScroll(scroll)) {
                        InfoField("Type", profile.type.toString())
                        InfoField("Version", profile.verName)
                        InfoField("Code", profile.verCode.toString())
                        if (!profile.desc.isNullOrEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(profile.desc, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfoProfile = null }) { Text("OK") } }
        )
    }

    // Remove confirmation
    confirmRemoveProfile?.let { profile ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmRemoveProfile = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Remove content?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Remove \"${profile.verName}\"?", color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = {
                TextButton(onClick = {
                    cm.removeContent(profile)
                    cm.syncContents()
                    loadProfiles(cm, effectiveTypes) { profiles = it }
                    confirmRemoveProfile = null
                    onContentChanged()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveProfile = null }) { Text("Cancel") } }
        )
    }

    // Error sub-dialog
    errorMsg?.let { msg ->
        OutlinedAlertDialog(
            onDismissRequest = { errorMsg = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            text = { Text(msg, color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("OK") } }
        )
    }

    // Content-card install dialog (shows Content-Info fields + a live 0..100% bar). Blocks dismiss
    // while the install is running; the finished popup STAYS UP until the user taps Done/Close.
    // Catalog download → render from the held snapshot (see catalogShown above) so it reflects live
    // phase+percent even after backgrounding, re-attaches on reopen, and persists past the registry's
    // linger-removal. The local-file import dialog only shows when no catalog card is active.
    val catalogState = catalogShown
    if (catalogState != null) {
        InstallProgressDialog(
            state = catalogState,
            // Shared cancel path — same as the Contents hub.
            onCancel = { ContentDownloadRegistry.requestCancel(catalogState.key) },
            onDismiss = {
                ContentDownloadRegistry.remove(catalogState.key)
                catalogShown = null
                catalogKey = null
            },
        )
    } else {
        installDialog?.let { st ->
            InstallProgressDialog(
                state = st,
                // The local-file import runs on an Activity-bound executor (not the registry), so there's
                // no job to cancel — dismiss is the best we can do here.
                onCancel = { installDialog = null },
                onDismiss = { installDialog = null },
            )
        }
    }
    // Catalog DONE: refresh the profile list + notify the parent. Fires once per transition to DONE
    // (keyed on the phase); the popup itself stays up until the user closes it (no auto-dismiss).
    LaunchedEffect(catalogState?.phase) {
        if (catalogState?.phase == ContentDownloadPhase.DONE) {
            loadProfiles(cm, effectiveTypes) { profiles = it }
            refreshKey++
            onContentChanged()
        }
    }

    // Shared row-download seed: same process-lifetime pipeline for official + community (community
    // installs from the bridged profile's remoteUrl). Reused by both the portrait sheet + landscape pane.
    val startDownload: (ContentProfile, String) -> Unit = { profile, key ->
        startContentDownload(context.applicationContext, profile)
        catalogKey = key
        catalogShown = ContentDownloadRegistry.get(key)
    }
    // Install-from-file launchers (progress lives in the content-card dialog). Shared by both layouts.
    val onBrowseFiles = {
        filePicker.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.WCP, "Select content file"))
    }
    val onPickSystem = {
        filePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        })
    }
    val multiType = contentTypes.size > 1

    if (isLandscape) {
        // A default ModalBottomSheet is too short in landscape to give the rail + detail pane room, so
        // the landscape layout is a full-height Dialog (same usePlatformDefaultWidth=false idiom used by
        // PerformanceDashboardDialog). Dismiss plumbs straight to onDismiss — the info/remove/error/
        // progress sub-dialogs above overlay it unchanged.
        val cs = MaterialTheme.colorScheme
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
                shape = RoundedCornerShape(24.dp),
                color = cs.surface,
                contentColor = cs.onSurface,
                border = BorderStroke(1.dp, cs.outline),
            ) {
                Column(Modifier.fillMaxSize()) {
                    // ── Header (landscape): compact single line — title + install-from-file. Subtitle
                    // dropped and padding tightened so the rail + panes get the vertical room. ──
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("WIN Components", color = cs.onSurface, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(16.dp))
                        // Component-source filter lives in the header (next to the title) in landscape, so the
                        // whole rail is free for categories.
                        HeaderSourceControl(
                            modifier = Modifier.weight(1f),
                            includeCommunity = includeCommunity,
                            communityGroups = communityGroups,
                            activeSources = activeSources,
                            onToggle = { on ->
                                includeCommunity = on
                                srcPrefs.edit().putBoolean(COMMUNITY_KEY, on).apply()
                                if (on) selectAllOnLoad = true else activeSources = setOf(OFFICIAL_KEY)
                            },
                            onChipToggle = { key ->
                                activeSources = activeSources.toMutableSet().apply {
                                    if (contains(key)) remove(key) else add(key)
                                    if (isEmpty()) add(OFFICIAL_KEY)
                                }
                            },
                        )
                        InstallFromFileButton(onBrowseFiles, onPickSystem, iconButtonModifier = Modifier.size(36.dp))
                    }
                    Divider(color = cs.outline)

                    // ── Master-detail body ──
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        ComponentRail(
                            modifier = Modifier.width(238.dp).fillMaxHeight().background(cs.surfaceContainer),
                            selectedType = selectedType,
                            gpuSelected = gpuDriversSelected,
                            countFor = { t -> profiles.count { it.type == t } },
                            gpuDriverCount = adrenoDriverCount,
                            onSelectType = { selectedType = it; gpuDriversSelected = false },
                            onSelectGpu = { gpuDriversSelected = true },
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(cs.outline))
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                          if (gpuDriversSelected) {
                            // ── GPU Drivers pane: the adrenotools driver browser (its own subsystem, no
                            // ContentProfile), rendered by the SHARED AdrenoDriverListPane so a driver
                            // installed here lands exactly where the standalone sheet puts it. Header mirrors
                            // the component detail header; the Manage-sources (Tune) button lives here and
                            // drives the pane's own dialog via showDriverManage. ──
                            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp)) {
                                // Compact single line: "GPU Drivers   Turnip / adrenotools" + manage button.
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("GPU Drivers", color = cs.onSurface,
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Turnip / adrenotools", color = cs.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { showDriverManage = true }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Filled.Tune, contentDescription = "Manage sources",
                                            tint = cs.onSurfaceVariant)
                                    }
                                }
                            }
                            AdrenoDriverListPane(
                                showManage = showDriverManage,
                                onManageDismiss = { showDriverManage = false },
                                onDriverInstalled = { driverRefreshKey++; onContentChanged() },
                                cardStyle = true,
                            )
                          } else {
                            // Detail header: selected component + (if it's in the passed scope) its active
                            // version + a one-line role. Active only shows for the type the user opened.
                            val activeVer = inUseKey?.takeIf { it.isNotEmpty() && selectedType in contentTypes }
                            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp)) {
                                // Compact single line: "<type>   active: <ver>  ·  <role>".
                                val role = roleFor(selectedType)
                                val sub = buildString {
                                    if (activeVer != null) append("active: $activeVer")
                                    if (role.isNotEmpty()) { if (isNotEmpty()) append("  ·  "); append(role) }
                                }
                                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                                    Text(selectedType.toString(), color = cs.onSurface,
                                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (sub.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(sub, color = cs.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            // Source control lives in the header in landscape (see HeaderSourceControl) so
                            // the version list gets the pane's full height and the rail is all categories.
                            ComponentVersionSections(
                                isLoadingRemote = isLoadingRemote,
                                profiles = profiles,
                                selectedType = selectedType,
                                filterByType = true, // rail always scopes the pane to one type
                                communityGroups = communityGroups,
                                activeSources = activeSources,
                                includeCommunity = includeCommunity,
                                communityLoading = communityLoading,
                                contentStates = contentStates,
                                inUseKey = inUseKey,
                                onDownload = startDownload,
                                onInfo = { showInfoProfile = it },
                                onRemove = { confirmRemoveProfile = it },
                            )
                          }
                        }
                    }
                    Divider(color = cs.outline)

                    // ── Footer (landscape): compact — terse hint + Close, minimal vertical padding so the
                    // reclaimed height goes to the version list. ──
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Tap a version to install",
                            color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Close", color = cs.primary)
                        }
                    }
                }
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(bottom = 12.dp)) {
                // Title + "install from file"
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (multiType) "Compatibility Layer" else "${contentTypes.first()} Downloads",
                        color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    // Install-from-file entry point. Progress now lives in the content-card dialog.
                    InstallFromFileButton(onBrowseFiles, onPickSystem)
                }
                Spacer(Modifier.height(12.dp))

                // Wine / Proton chips (only when more than one type is shown).
                if (multiType) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        contentTypes.forEach { t -> TypeChip(t.toString(), t == selectedType) { selectedType = t } }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Divider(color = MaterialTheme.colorScheme.outline)

                // ── Component source control: Official (contents.json) default, opt-in community repos.
                SourceToggleBox(
                    includeCommunity = includeCommunity,
                    communityGroups = communityGroups,
                    activeSources = activeSources,
                    onToggle = { on ->
                        includeCommunity = on
                        srcPrefs.edit().putBoolean(COMMUNITY_KEY, on).apply()
                        if (on) selectAllOnLoad = true else activeSources = setOf(OFFICIAL_KEY)
                    },
                    onChipToggle = { key ->
                        activeSources = activeSources.toMutableSet().apply {
                            if (contains(key)) remove(key) else add(key)
                            if (isEmpty()) add(OFFICIAL_KEY) // never let the user empty the list
                        }
                    },
                )

                ComponentVersionSections(
                    isLoadingRemote = isLoadingRemote,
                    profiles = profiles,
                    selectedType = selectedType,
                    filterByType = multiType,
                    communityGroups = communityGroups,
                    activeSources = activeSources,
                    includeCommunity = includeCommunity,
                    communityLoading = communityLoading,
                    contentStates = contentStates,
                    inUseKey = inUseKey,
                    onDownload = startDownload,
                    onInfo = { showInfoProfile = it },
                    onRemove = { confirmRemoveProfile = it },
                )

                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

// ── Single-type overload ──────────────────────────────────────────────────────
@Composable
fun ContentDownloadSheet(
    contentType: ContentProfile.ContentType,
    onDismiss: () -> Unit,
    onContentChanged: () -> Unit,
    inUseKey: String? = null,
) = ContentDownloadSheet(listOf(contentType), onDismiss, onContentChanged, inUseKey)

// ── Row (outlined card matching the FileManager / CommunityCard idiom: surfaceContainer fill,
// 1dp outline, rounded 10dp; Memory icon, name, trailing cloud / state) ──
@Composable
private fun DownloadContentItem(
    profile: ContentProfile,
    isLocal: Boolean,
    isInUse: Boolean,
    isDownloading: Boolean,
    isInstalling: Boolean,
    progress: Float?,
    installProgress: Float?,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
    // Community provenance: a small colored source pill + (if the same version is already installed)
    // an Installed badge instead of a redundant download action.
    sourceLabel: String? = null,
    sourceColor: Color = Color.Unspecified,
    communityInstalled: Boolean = false,
) {
    val busy = isDownloading || isInstalling
    val installedBlue = Color(0xFF4FC3F7) // intentional: distinct installed/in-use status blue, not the accent
    val cs = MaterialTheme.colorScheme
    // Whole card is tappable to download when it's an available (not-installed, not-busy) entry —
    // matches the adrenotools EntryRow behaviour.
    val rowClickable = !busy && !isLocal && !communityInstalled
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rowClickable) Modifier.clickable(onClick = onDownload) else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(1.dp, cs.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Memory, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.verName, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (sourceLabel != null && sourceColor != Color.Unspecified) {
                            Spacer(Modifier.width(8.dp))
                            SourcePill(sourceLabel, sourceColor)
                        }
                    }
                    val sub = when {
                        isInUse -> "In use"
                        isLocal || communityInstalled -> "Installed"
                        !profile.desc.isNullOrEmpty() -> profile.desc
                        else -> null
                    }
                    if (sub != null) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isInUse) installedBlue else cs.onSurfaceVariant,
                        )
                    }
                }
                when {
                    busy -> {}
                    isLocal -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Installed", tint = installedBlue,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Icon(Icons.Filled.Info, contentDescription = "Info", tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).clickable(onClick = onInfo))
                        Spacer(Modifier.width(14.dp))
                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFFEF5350), // intentional: destructive-action red
                            modifier = Modifier.size(20.dp).clickable(onClick = onRemove))
                    }
                    communityInstalled -> Icon(Icons.Filled.CheckCircle, contentDescription = "Installed",
                        tint = installedBlue, modifier = Modifier.size(20.dp))
                    else -> Icon(Icons.Filled.CloudDownload, contentDescription = "Download", tint = cs.primary,
                        modifier = Modifier.size(22.dp))
                }
            }
            // 0→100 determinate bar for both phases — blue "Downloading", green "Installing".
            if (busy) {
                Spacer(Modifier.height(6.dp))
                val frac = (if (isInstalling) installProgress else progress)?.coerceIn(0f, 1f) ?: 0f
                val barColor = if (isInstalling) Color(0xFF4CAF50) else cs.primary // intentional: green = "installing" phase, distinct from blue download phase
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isInstalling) "Installing" else "Downloading",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text("${(frac * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(progress = frac, modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = barColor, trackColor = cs.surfaceContainerHighest)
            }
        }
    }
}

@Composable
private fun TypeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) cs.onPrimary else cs.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun InfoField(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun isInUse(profile: ContentProfile, inUseKey: String?): Boolean {
    if (inUseKey.isNullOrEmpty()) return false
    return inUseKey == ContentsManager.getEntryName(profile) ||
        inUseKey == profile.verName ||
        inUseKey == "${profile.type}-${profile.verName}-${profile.verCode}"
}

private fun loadProfiles(
    cm: ContentsManager,
    contentTypes: List<ContentProfile.ContentType>,
    onResult: (List<ContentProfile>) -> Unit,
) {
    cm.syncContents()
    val all = mutableListOf<ContentProfile>()
    for (type in contentTypes) cm.getProfiles(type)?.let { all.addAll(it) }
    onResult(all.distinctBy { ContentsManager.getEntryName(it) })
}

// internal (not private): the community-config inline installer reuses this same download path.
internal fun downloadToCache(context: Context, profile: ContentProfile, onProgress: (Float) -> Unit): Uri? {
    // Stable per-component temp name (NOT temp_<millis>, which orphans partials): a fixed name
    // lets an interrupted download resume via HTTP Range on the next attempt instead of
    // restarting from zero. Range-aware Downloader appends to this file when a partial exists.
    val safe = ContentsManager.getEntryName(profile).replace(Regex("[^A-Za-z0-9._-]"), "_")
    val f = File(context.cacheDir, "content_dl_$safe.part")
    return if (Downloader.downloadFile(profile.remoteUrl, f, /* resume = */ true) { frac -> onProgress(frac) }) Uri.fromFile(f) else null
}

// internal (not private): reused by the community-config inline installer (same install path).
internal fun installContent(
    context: Context,
    cm: ContentsManager,
    uri: Uri,
    onProgress: (fraction: Float, etaText: String) -> Unit,
    onDone: (Boolean) -> Unit,
) {
    val activity = context.findActivity()
    if (activity == null) { onDone(false); return }
    // Byte-accurate denominator = the compressed source size (file uris only; 0 for content uris).
    val total = uri.path?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L
    val etaTracker = ImportEtaTracker()
    Executors.newSingleThreadExecutor().execute {
        try {
            val progress = TarCompressorUtils.OnReadProgressListener { read, tot ->
                if (tot > 0) {
                    val p = etaTracker.update(read, tot)
                    activity.runOnUiThread { onProgress((read.toFloat() / tot).coerceIn(0f, 1f), formatEta(p.etaSeconds)) }
                }
            }
            cm.extraContentFile(uri, total, progress, object : ContentsManager.OnInstallFinishedCallback {
                var phase = 0
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    // A component that's already installed is NOT a failure — the caller's post-install
                    // apply re-resolves against what's on disk and finds it. Report success so the
                    // community inline installer writes the version to the shortcut instead of showing
                    // a misleading "install failed" on a build the user already has.
                    if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST) {
                        Log.i("CommunityConfigs", "Component already installed (ERROR_EXIST) — treating as success")
                        activity.runOnUiThread { onProgress(1f, ""); onDone(true) }
                        return
                    }
                    // Every other reason previously surfaced as a bare "fails" with nothing in the log.
                    Log.w("CommunityConfigs", "Component install failed: $reason", e)
                    activity.runOnUiThread { onDone(false) }
                }
                override fun onSucceed(profile: ContentProfile) {
                    try {
                        if (phase == 0) {
                            phase = 1
                            cm.finishInstallContent(profile, this)
                        } else {
                            cm.syncContents()
                            activity.runOnUiThread { onProgress(1f, ""); onDone(true) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        activity.runOnUiThread { onDone(false) }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            activity.runOnUiThread { onDone(false) }
        }
    }
}

// ── Community source unification (opt-in) ─────────────────────────────────────

private const val COMMUNITY_PREFS = "component_download_prefs"
private const val COMMUNITY_KEY = "include_community"
private const val OFFICIAL_KEY = "__official__"

/** Official-source green, shared with the Contents hub's repository badge. */
internal val OfficialSourceColor = Color(0xFF37C26B)
private val OfficialColor = OfficialSourceColor
private val CommunityPalette = listOf(
    Color(0xFF3D9BFF), Color(0xFFB98CFF), Color(0xFFFF7A18),
    Color(0xFF4DD0E1), Color(0xFFFFB300), Color(0xFF66BB6A),
)
private fun sourceColorFor(name: String): Color =
    CommunityPalette[name.hashCode().absoluteValue % CommunityPalette.size]

/** A community repo's versions for the active type, bridged to installable ContentProfiles. */
private data class CommunityGroup(val name: String, val profiles: List<ContentProfile>)

/** One rendered group in the sheet: Official (contents.json) or a community source. */
private data class SheetSection(
    val label: String,
    val community: Boolean,
    val color: Color,
    val rows: List<ContentProfile>,
)

/** True if [source] lists this sheet's [typeStr] (case-insensitive; fex/fexcore bridged). Empty = include. */
private fun sourceSupportsType(source: RemoteSourceRepository.RemoteSource, typeStr: String): Boolean {
    if (source.supportedTypes.isEmpty()) return true
    val t = typeStr.lowercase()
    return source.supportedTypes.any { st ->
        val s = st.lowercase()
        s == t || (t.contains("fex") && s.contains("fex"))
    }
}

/**
 * Bridge a RemoteItem → an installable ContentProfile (remoteUrl set), so the existing
 * startContentDownload / ContentsManager pipeline installs it unchanged. verCode is derived from the
 * URL only to make the registry/entry key unique — the real type/version/code come from the archive's
 * own profile.json at extract time, so this synthetic code never affects where it installs.
 */
private fun RemoteSourceRepository.RemoteItem.toProfile(type: ContentProfile.ContentType): ContentProfile =
    ContentProfile().also {
        it.type = type
        it.verName = versionName
        it.verCode = downloadUrl.hashCode().absoluteValue
        it.remoteUrl = downloadUrl
        it.desc = description
    }

/** Normalized identity for cross-checking an installed version against a community duplicate. */
private fun normVer(type: String, ver: String?): String =
    (type + (ver ?: "")).lowercase().filter { it.isLetterOrDigit() }

@Composable
private fun SourceToggleBox(
    includeCommunity: Boolean,
    communityGroups: List<CommunityGroup>,
    activeSources: Set<String>,
    onToggle: (Boolean) -> Unit,
    onChipToggle: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        border = BorderStroke(1.dp, cs.outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Hub, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Include community repos", style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Add versions from the Contents screen's sources",
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Switch(checked = includeCommunity, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = cs.primary))
            }
            if (includeCommunity) {
                Divider(color = cs.outline.copy(alpha = 0.4f))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourceChip("Official", OfficialColor, OFFICIAL_KEY in activeSources) { onChipToggle(OFFICIAL_KEY) }
                    communityGroups.forEach { g ->
                        SourceChip(g.name, sourceColorFor(g.name), g.name in activeSources) { onChipToggle(g.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) cs.primary else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 12.dp),
    ) {
        if (!selected) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.onPrimary else cs.onSurface,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SourceGroupHeader(section: SheetSection) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(6.dp)).background(section.color))
        Spacer(Modifier.width(9.dp))
        Text(section.label, style = MaterialTheme.typography.labelLarge, color = cs.onSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        SourceTagBadge(if (section.community) "Community" else "Official", section.color)
        Spacer(Modifier.weight(1f))
        Text("${section.rows.size}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

/** The "Official" / "Community" tag pill — one look for the sheet's group headers and the hub's repo rows. */
@Composable
internal fun SourceTagBadge(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SourcePill(label: String, color: Color) {
    Text(
        label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

// ── Install-from-file affordance (shared by the portrait sheet header + landscape dialog header) ──
@Composable
private fun InstallFromFileButton(
    onBrowseFiles: () -> Unit,
    onPickSystem: () -> Unit,
    // Landscape passes a smaller size to shrink the compact header; portrait keeps the default 48dp target.
    iconButtonModifier: Modifier = Modifier,
) {
    var showPickMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showPickMenu = true }, modifier = iconButtonModifier) {
            Icon(Icons.Filled.FolderOpen, contentDescription = "Install from file",
                tint = MaterialTheme.colorScheme.primary)
        }
        // Outlined-card look to match the content rows / FileManager idiom (shared helper — this
        // Material3 build has no DropdownMenu shape/border params).
        DropdownMenu(
            expanded = showPickMenu,
            onDismissRequest = { showPickMenu = false },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            DropdownMenuItem(text = { Text("Browse files") }, onClick = { showPickMenu = false; onBrowseFiles() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Pick via system…") }, onClick = { showPickMenu = false; onPickSystem() })
        }
    }
}

// ── Shared version list (Official + active community sources). Extracted so the portrait sheet and the
// landscape detail pane render identical rows/sections from the same state. ColumnScope so the inner
// list can weight-fill the remaining height in either container. ──
@Composable
private fun ColumnScope.ComponentVersionSections(
    isLoadingRemote: Boolean,
    profiles: List<ContentProfile>,
    selectedType: ContentProfile.ContentType,
    // True when the loaded `profiles` may hold more than the selected type (multiType portrait, or the
    // full-rail landscape): filter down to selectedType. Single-type portrait passes false (no filter).
    filterByType: Boolean,
    communityGroups: List<CommunityGroup>,
    activeSources: Set<String>,
    includeCommunity: Boolean,
    communityLoading: Boolean,
    contentStates: Map<String, ContentDownloadState>,
    inUseKey: String?,
    onDownload: (ContentProfile, String) -> Unit,
    onInfo: (ContentProfile) -> Unit,
    onRemove: (ContentProfile) -> Unit,
) {
    if (isLoadingRemote) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        val official = remember(profiles, selectedType, filterByType) {
            profiles
                .filter { !filterByType || it.type == selectedType }
                .sortedByDescending { p -> if (p.remoteUrl == null) 1 else 0 }
        }
        // Versions already installed locally (normalized type+version) → a community duplicate
        // shows an Installed badge instead of a redundant second download.
        val installedKeys = remember(official) {
            official.filter { it.remoteUrl == null }
                .map { normVer(it.type.toString(), it.verName) }.toSet()
        }
        // Ordered sections: Official first, then each ACTIVE community source (in source order).
        val sections = remember(official, communityGroups, activeSources, includeCommunity) {
            buildList {
                if (OFFICIAL_KEY in activeSources && official.isNotEmpty())
                    add(SheetSection("Official", community = false, color = OfficialColor, rows = official))
                if (includeCommunity) communityGroups.forEach { g ->
                    if (g.name in activeSources)
                        add(SheetSection(g.name, community = true, color = sourceColorFor(g.name), rows = g.profiles))
                }
            }
        }
        val totalRows = sections.sumOf { it.rows.size }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (totalRows == 0 && !communityLoading) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No content available.", color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "count") {
                        val nSrc = sections.size
                        Text(
                            "Showing $totalRows version${if (totalRows != 1) "s" else ""} from " +
                                "$nSrc source${if (nSrc != 1) "s" else ""}" +
                                (if (!includeCommunity) " · community repos off" else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        )
                    }
                    sections.forEachIndexed { si, section ->
                        item(key = "hdr-$si-${section.label}") { SourceGroupHeader(section) }
                        itemsIndexed(
                            section.rows,
                            // Collision-proof: section+index prefix so an official + community
                            // duplicate of the same version can never share a LazyColumn key.
                            key = { ri, p -> "row-$si-$ri-${p.remoteUrl ?: ContentsManager.getEntryName(p)}" },
                        ) { _, profile ->
                            val key = ContentsManager.getEntryName(profile)
                            val isLocal = profile.remoteUrl == null
                            val cds = contentStates[key]
                            val communityInstalled = section.community &&
                                normVer(profile.type.toString(), profile.verName) in installedKeys
                            DownloadContentItem(
                                profile = profile,
                                isLocal = isLocal,
                                isInUse = isInUse(profile, inUseKey),
                                isDownloading = cds?.phase == ContentDownloadPhase.DOWNLOADING,
                                isInstalling = cds?.phase == ContentDownloadPhase.INSTALLING,
                                progress = if (cds?.phase == ContentDownloadPhase.DOWNLOADING) cds.fraction else null,
                                installProgress = if (cds?.phase == ContentDownloadPhase.INSTALLING) cds.fraction else null,
                                sourceLabel = if (section.community) section.label else null,
                                sourceColor = section.color,
                                communityInstalled = communityInstalled,
                                onDownload = { onDownload(profile, key) },
                                onInfo = { onInfo(profile) },
                                onRemove = { onRemove(profile) },
                            )
                        }
                    }
                    if (communityLoading) item(key = "community-loading") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Landscape master-detail rail ──────────────────────────────────────────────

/**
 * One category button in the landscape rail. Most map to a real installable [ContentProfile.ContentType]
 * (loaded through this sheet's ContentsManager pipeline). A null [type] marks a SPECIAL category whose
 * pane is a DIFFERENT subsystem — currently only GPU Drivers (adrenotools), which has no ContentType and
 * renders the shared AdrenoDriverListPane instead of ComponentVersionSections.
 */
private data class RailCat(
    val label: String,
    val icon: ImageVector,
    val role: String,
    val type: ContentProfile.ContentType?,
)
private data class RailGroup(val header: String, val cats: List<RailCat>)

/** A content-profile-backed rail category (label = the type's display name). */
private fun railCat(type: ContentProfile.ContentType, icon: ImageVector, role: String) =
    RailCat(label = type.toString(), icon = icon, role = role, type = type)

// VEGAS is intentionally absent (its own VegasDownloadSheet). GPU Drivers IS present but special-cased:
// type = null keeps it OUT of RAIL_TYPES (so loadProfiles/effectiveTypes never try to load a non-existent
// ContentProfile for it); the right pane renders AdrenoDriverListPane when it's the selected category.
private val RAIL_GROUPS = listOf(
    RailGroup("Compatibility", listOf(
        railCat(ContentProfile.ContentType.CONTENT_TYPE_WINE, Icons.Filled.Extension, "Windows API layer"),
        railCat(ContentProfile.ContentType.CONTENT_TYPE_PROTON, Icons.Filled.Bolt, "Steam Play runtime"),
    )),
    RailGroup("Graphics", listOf(
        railCat(ContentProfile.ContentType.CONTENT_TYPE_DXVK, Icons.Filled.GridView, "D3D9/10/11 → Vulkan"),
        railCat(ContentProfile.ContentType.CONTENT_TYPE_VKD3D, Icons.Filled.Layers, "D3D12 → Vulkan"),
        railCat(ContentProfile.ContentType.CONTENT_TYPE_D7VK, Icons.Filled.ViewInAr, "D3D7/8 → Vulkan"),
        RailCat("GPU Drivers", Icons.Filled.Memory, "Turnip / adrenotools", type = null),
    )),
    RailGroup("x86 Translation", listOf(
        railCat(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, Icons.Filled.DeveloperBoard, "arm64ec x86 emu"),
        railCat(ContentProfile.ContentType.CONTENT_TYPE_BOX64, Icons.Filled.Dns, "x86_64 → ARM64"),
        railCat(ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64, Icons.Filled.Widgets, "x86 on arm64ec"),
    )),
)

/** The full set of REAL types the landscape rail exposes; profiles for all of these are loaded together.
 *  Special (type = null) categories like GPU Drivers are excluded — they don't come from ContentsManager. */
private val RAIL_TYPES: List<ContentProfile.ContentType> = RAIL_GROUPS.flatMap { g -> g.cats.mapNotNull { it.type } }

private fun roleFor(type: ContentProfile.ContentType): String =
    RAIL_GROUPS.flatMap { it.cats }.firstOrNull { it.type == type }?.role ?: ""

@Composable
private fun ComponentRail(
    modifier: Modifier,
    selectedType: ContentProfile.ContentType,
    gpuSelected: Boolean,
    countFor: (ContentProfile.ContentType) -> Int,
    gpuDriverCount: Int,
    onSelectType: (ContentProfile.ContentType) -> Unit,
    onSelectGpu: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
        // Scrollable category list takes the remaining height; the source filter is pinned below it.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            RAIL_GROUPS.forEachIndexed { gi, group ->
                Text(
                    group.header.uppercase(),
                    style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 12.dp, top = if (gi == 0) 4.dp else 14.dp, bottom = 8.dp),
                )
                group.cats.forEach { cat ->
                    val type = cat.type
                    if (type != null) {
                        // Content-profile category: selection + count from the loaded profiles.
                        RailCategory(cat, selected = !gpuSelected && type == selectedType, count = countFor(type)) {
                            onSelectType(type)
                        }
                    } else {
                        // Special (GPU Drivers): selection + count come from the adrenotools subsystem.
                        RailCategory(cat, selected = gpuSelected, count = gpuDriverCount, onClick = onSelectGpu)
                    }
                }
            }
        }
    }
}

// Header-pinned component-source control (landscape). Sits next to the "WIN Components" title: Hub icon +
// short label + switch, with the active-source chips in a scrollable row to the right when enabled. Keeps the
// whole rail free for categories. Same state/callbacks as the portrait SourceToggleBox.
@Composable
private fun HeaderSourceControl(
    modifier: Modifier = Modifier,
    includeCommunity: Boolean,
    communityGroups: List<CommunityGroup>,
    activeSources: Set<String>,
    onToggle: (Boolean) -> Unit,
    onChipToggle: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Hub, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Community repos", style = MaterialTheme.typography.labelMedium, color = cs.onSurface,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Switch(checked = includeCommunity, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = cs.primary))
        if (includeCommunity) {
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceChip("Official", OfficialColor, OFFICIAL_KEY in activeSources) { onChipToggle(OFFICIAL_KEY) }
                communityGroups.forEach { g ->
                    SourceChip(g.name, sourceColorFor(g.name), g.name in activeSources) { onChipToggle(g.name) }
                }
            }
        }
    }
}

@Composable
private fun RailCategory(cat: RailCat, selected: Boolean, count: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) cs.primary.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, cs.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
    ) {
        // Left accent bar (selected only) — matches the mock; a fixed-width slot keeps rows aligned.
        Box(Modifier.width(3.dp).height(24.dp).clip(RoundedCornerShape(2.dp))
            .background(if (selected) cs.primary else Color.Transparent))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                .background(if (selected) cs.primary else cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(cat.icon, contentDescription = null,
                tint = if (selected) cs.onPrimary else cs.primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(cat.label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cat.role, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = if (selected) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
