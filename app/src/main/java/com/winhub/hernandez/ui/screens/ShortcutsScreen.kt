@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.winhub.hernandez.ui.screens

import com.winhub.hernandez.core.PresetScope
import com.winhub.hernandez.core.PresetOverrides
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.media.MediaScannerConnection
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.focusable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.components.Button
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.Divider
import com.winhub.hernandez.ui.components.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.winhub.hernandez.ui.components.RadioButton
import com.winhub.hernandez.communityconfigs.AccountManager
import com.winhub.hernandez.store.SteamFriendsAction
import com.winhub.hernandez.communityconfigs.CanonicalDevice
import com.winhub.hernandez.communityconfigs.CanonicalGame
import com.winhub.hernandez.communityconfigs.CommunityConfigApply
import com.winhub.hernandez.communityconfigs.CommunityConfigRef
import com.winhub.hernandez.communityconfigs.DeviceIdentity
import com.winhub.hernandez.communityconfigs.EnvVarScrub
import com.winhub.hernandez.communityconfigs.ShortcutExporter
import com.winhub.hernandez.communityconfigs.UploadedConfigsStore.UploadedConfig
import com.winhub.hernandez.communityconfigs.GameMatcher
import com.winhub.hernandez.communityconfigs.ShortcutConfig
import com.winhub.hernandez.communityconfigs.WorkerConfigEntry
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import com.winhub.hernandez.ui.AccountAvatar
import com.winhub.hernandez.ui.AccountUiBus
import com.winhub.hernandez.ui.ComponentReturnBus
import com.winhub.hernandez.ui.EmulatorLabels
import com.winhub.hernandez.ui.LocalTopBarActions
import com.winhub.hernandez.ui.LocalTopBarTransparent
import com.winhub.hernandez.ui.Screen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import androidx.compose.ui.viewinterop.AndroidView
import com.winhub.hernandez.R
import com.winhub.hernandez.SettingsFragment
import com.winhub.hernandez.XServerDisplayActivity
import com.winhub.hernandez.XrActivity
import com.winhub.hernandez.box64.Box64Preset
import com.winhub.hernandez.box64.Box64PresetManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.GameDetails
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.reshade.ReshadeManager
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.WrapperManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.Downloader
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.screens.adrenodownload.AdrenoDriverDownloadSheet
import com.winhub.hernandez.ui.screens.adrenodownload.RemoteDriverEntry
import com.winhub.hernandez.ui.screens.adrenodownload.RemoteDriverRepository
import com.winhub.hernandez.core.CopyGameToDriveC
import com.winhub.hernandez.core.DefaultVersion
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.GameFolderScanner
import com.winhub.hernandez.core.CustomSaveVault
import com.winhub.hernandez.core.GameSaveBackup
import com.winhub.hernandez.core.KeyValueSet
import com.winhub.hernandez.ui.components.ContainerGlossarySheet
import com.winhub.hernandez.ui.components.DraggableAddButton
import com.winhub.hernandez.ui.components.EmuAccountConflictDialog
import com.winhub.hernandez.ui.theme.DangerRed
import com.winhub.hernandez.core.LogInventory
import com.winhub.hernandez.core.LogLocation
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.contentdialog.DXVKConfigDialog
import com.winhub.hernandez.core.WineInfo
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.core.WineUtils
import com.winhub.hernandez.util.InAppFilePicker
import com.winhub.hernandez.fexcore.FEXCorePreset
import com.winhub.hernandez.fexcore.FEXCorePresetManager
import com.winhub.hernandez.inputcontrols.ControlsProfile
import com.winhub.hernandez.inputcontrols.InputControlsManager
import com.winhub.hernandez.midi.MidiManager
import com.winhub.hernandez.store.GoldbergComponent
import com.winhub.hernandez.store.GoldbergMode
import com.winhub.hernandez.store.GoldbergPatcher
import com.winhub.hernandez.store.SteamDatabase
import com.winhub.hernandez.store.SteamGameUpdater
import com.winhub.hernandez.store.SteamLiteComponent
import com.winhub.hernandez.store.EaSupport
import com.winhub.hernandez.store.steamscript.InstallScriptExecutor
import com.winhub.hernandez.store.SteamLoginActivity
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.SteamSessionManager
import com.winhub.hernandez.store.StarLaunchBridge
import com.winhub.hernandez.store.SteamSaveManagerActivity
import com.winhub.hernandez.store.SteamLibraryArt
import com.winhub.hernandez.store.SteamStoreSearch
import com.winhub.hernandez.store.compose.ContainerPickerDialog
import com.winhub.hernandez.ui.theme.Divider as DividerColor
import com.winhub.hernandez.ui.theme.LocalAccentDim
import com.winhub.hernandez.ui.theme.OnSurface
import com.winhub.hernandez.ui.theme.OnSurfaceVariant
import com.winhub.hernandez.ui.theme.Surface as SurfaceColor
import com.winhub.hernandez.ui.theme.SurfaceVariant
import com.winhub.hernandez.ui.theme.SurfaceVariant as SurfaceVariantColor
import com.winhub.hernandez.widget.CPUListView
import com.winhub.hernandez.ui.components.EnvVarsEditor
import com.winhub.hernandez.ui.components.AudioSettingsDialog
import com.winhub.hernandez.ui.components.audioConfigFromEnv
import com.winhub.hernandez.ui.components.audioConfigToEnv
import com.winhub.hernandez.ui.components.PlayerSlotsEditor
import com.winhub.hernandez.ui.components.CollapsibleRail
import com.winhub.hernandez.ui.components.RailItem
import com.winhub.hernandez.ui.components.RailLink
import com.winhub.hernandez.ui.components.RailSection
import com.winhub.hernandez.ui.components.RailTopTabs
import com.winhub.hernandez.ui.components.rememberRailState
import com.winhub.hernandez.winhandler.WinHandler
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.lang.reflect.Field
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ShortcutsScreen(
    vm: ShortcutsViewModel = viewModel(),
    onNavigate: ((Screen) -> Unit)? = null,
) {
    val shortcuts by vm.shortcuts.collectAsState(initial = emptyList())
    val sortOrder by vm.sortOrder.collectAsState()
    val viewMode by vm.viewMode.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity

    var confirmRemove by remember { mutableStateOf<Shortcut?>(null) }
    // Multi-select. Keyed by file path rather than by Shortcut because refresh() rebuilds the
    // objects, and a set of stale instances would silently stop matching anything.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var confirmRemoveSelected by remember { mutableStateOf(false) }
    var cloneTarget by remember { mutableStateOf<Shortcut?>(null) }
    // Save Backup (custom-import games): a picked .zip awaiting a target-container choice, plus the
    // label of the game the restore was launched from (shown in the container picker title).
    var restoreZipUri by remember { mutableStateOf<Uri?>(null) }
    var restoreForName by remember { mutableStateOf("") }
    // Emulator account ids a restore held back because the container already runs a different one.
    var emuConflicts by remember { mutableStateOf<List<GameSaveBackup.EmuIdConflict>>(emptyList()) }
    // The shortcut whose "Back up saves" layout-choice dialog is open (Winlator vs GameHub).
    var backupFormatShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var settingsShortcut by remember { mutableStateOf<Shortcut?>(null) }
    // "Copy to Drive C…" target — the game whose folder is being copied onto the container's C:
    // drive (and then repointed). Both entry points (the ⋮ menu item and the editor's Storage row)
    // set this; the shared CopyToDriveCCoordinator below owns the whole confirm→copy→repoint flow.
    var copyToDriveCTarget by remember { mutableStateOf<Shortcut?>(null) }
    // "Change executable…" target — the game being repointed at a different .exe/.lnk (launcher →
    // real exe, dx11 ↔ dx9, a config tool). Both entry points set it; ChangeExecutableCoordinator
    // owns the pick → args-choice → rewrite flow via the shared CopyGameToDriveC.setShortcutExe.
    var changeExeTarget by remember { mutableStateOf<Shortcut?>(null) }
    var gameDetailsShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var propertiesShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var logsShortcut by remember { mutableStateOf<Shortcut?>(null) }
    // XMB view: a nested menu is open (hides the + button) / its "Manage wrappers" row asked for the dialog.
    var xmbNested by remember { mutableStateOf(false) }
    var showWrapperManagerXmb by remember { mutableStateOf(false) }
    // Steam launch-method popup (feature M3): the Steam-origin shortcut whose SteamLite-vs-Goldberg
    // chooser is open (null = closed). A Steam game routes through this before launching UNLESS it
    // already has a remembered choice (launchMode set + launchModeRemembered=="1").
    var launchChoiceFor by remember { mutableStateOf<Shortcut?>(null) }
    // EA support (see EaSupport): an EA title either needs its one-time EA Desktop setup, is unsupported
    // (Javelin anti-cheat), or launches straight through SteamLite with the EA chain armed.
    var eaSetupFor by remember { mutableStateOf<Shortcut?>(null) }
    var eaUnsupportedFor by remember { mutableStateOf<Shortcut?>(null) }
    var eaSetupBusy by remember { mutableStateOf(false) }
    val eaScope = rememberCoroutineScope()
    // SteamLite launch pre-flight (session → network → cloud saves → update check, BEFORE the container opens):
    // the RealSteam game whose "Getting Steam ready" dialog is up (null = none). Every RealSteam
    // launch — the popup pick and a remembered pick — routes through it; Goldberg/Raw never do.
    var preflightFor by remember { mutableStateOf<Shortcut?>(null) }
    // Download-on-launch progress overlay: the game we're about to launch once its picked component
    // (SteamLite for RealSteam, or Goldberg) finishes downloading, plus a label + 0..1 fraction.
    // null target = nothing downloading.
    var componentDownloadFor by remember { mutableStateOf<Shortcut?>(null) }
    var componentDownloadLabel by remember { mutableStateOf("") }
    var componentDownloadProgress by remember { mutableFloatStateOf(0f) }
    // RealSteam manual maintenance (the launch popup's Verify / Update buttons — SteamLite roadmap #3):
    // the game whose maintenance run is in flight (null = none), its progress (<0 = indeterminate
    // "checking", 0..1 while working), a label, a dialog title, and the cancel handle. This is NO LONGER
    // on the launch path — update/verify are explicit now, so a run never launches the game.
    var steamUpdateFor by remember { mutableStateOf<Shortcut?>(null) }
    var steamUpdateProgress by remember { mutableFloatStateOf(-1f) }
    var steamUpdateLabel by remember { mutableStateOf("") }
    var steamUpdateTitle by remember { mutableStateOf("") }
    var steamUpdateHandle by remember { mutableStateOf<SteamGameUpdater.UpdateHandle?>(null) }
    // "Check for updates" is check-THEN-offer (never auto-applies): a cheap [checkForUpdate] probe runs
    // first, and when it finds a delta (or can't tell from cached data) this holds the game + its status so
    // the confirm dialog below can offer to apply it. null = no offer pending.
    var steamUpdateOffer by remember { mutableStateOf<Pair<Shortcut, SteamGameUpdater.UpdateStatus>?>(null) }
    // Manual RealSteam maintenance: run a delta [update] or a full "verify integrity" [verify] pass for the
    // game, reusing the shared progress modal. Standalone — it never launches the game; the outcome
    // surfaces as a toast. Cancellable via steamUpdateHandle (the modal's Cancel button).
    fun runSteamMaintenance(s: Shortcut, verify: Boolean) {
        val appId = steamAppIdOf(s)
        steamUpdateTitle = if (verify) "Verifying game files" else "Updating game"
        steamUpdateLabel = if (verify) "Verifying ${s.name}…" else "Checking ${s.name} for updates…"
        steamUpdateProgress = -1f
        steamUpdateFor = s
        val progress = SteamGameUpdater.ProgressCallback { frac, label ->
            steamUpdateProgress = frac; steamUpdateLabel = label
        }
        val done = SteamGameUpdater.DoneCallback { result, msg ->
            steamUpdateFor = null
            steamUpdateHandle = null
            // Every terminal result except a user cancel is worth a one-line toast (offline / failed /
            // "Files verified" / "Updated" / "Already up to date"). A launch never follows.
            if (result != SteamGameUpdater.Result.CANCELLED && msg.isNotBlank()) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
        steamUpdateHandle =
            if (verify) SteamGameUpdater.verifyFiles(context, appId, progress, done)
            else SteamGameUpdater.updateNow(context, appId, progress, done)
    }
    // "Check for updates": a cheap, network-free [checkForUpdate] probe (shown in the shared progress modal
    // as the animated "Checking…" indeterminate phase), then BRANCH — we never auto-apply. Up-to-date /
    // not-installed just inform via a toast; an available (or can't-tell-offline) result opens the confirm
    // dialog, whose [Update now] / [Check online] hands off to runSteamMaintenance (the authoritative
    // updateNow pass). The onResult lands on the main thread (see checkForUpdate's doc).
    fun checkForUpdatesThenOffer(s: Shortcut) {
        val appId = steamAppIdOf(s)
        steamUpdateTitle = "Checking for updates"
        steamUpdateLabel = "Checking ${s.name}…"
        steamUpdateProgress = -1f   // opens in the animated indeterminate state, never a static 0%.
        steamUpdateFor = s
        steamUpdateHandle = SteamGameUpdater.checkForUpdate(context, appId) { status ->
            steamUpdateFor = null
            steamUpdateHandle = null
            when (status.state) {
                SteamGameUpdater.State.UP_TO_DATE -> {
                    val b = if (status.installedBuild > 0L) " (build ${status.installedBuild})" else ""
                    Toast.makeText(context, "${s.name} is up to date$b", Toast.LENGTH_LONG).show()
                }
                SteamGameUpdater.State.NOT_INSTALLED ->
                    Toast.makeText(context, "${s.name} isn't installed", Toast.LENGTH_LONG).show()
                SteamGameUpdater.State.UPDATE_AVAILABLE, SteamGameUpdater.State.UNKNOWN ->
                    steamUpdateOffer = s to status
            }
        }
    }
    // Goldberg (offline emulator) launch: persist the sub-mode, download the component on demand,
    // patch the install (resolved off-main from the Room steam_games row) and launch. Shared by the
    // popup's Goldberg pick and the pre-flight's "Launch with Goldberg" fallback.
    fun launchWithGoldberg(s: Shortcut, gm: GoldbergMode) {
        val appId = steamAppIdOf(s)
        SteamPrefs.init(context)
        SteamPrefs.setGoldbergMode(appId, gm)
        // Resolve the on-disk install dir (Room steam_games row) off the main thread, then
        // patch the tier and launch. Mirrors SteamGameDetailActivity.onGoldbergModeSelected.
        val applyThenLaunch = {
            Thread({
                val installDir = runCatching {
                    SteamDatabase.getInstance(context).getGame(appId)?.installDir
                }.getOrNull().orEmpty()
                activity.runOnUiThread {
                    if (installDir.isEmpty()) {
                        // Nothing to patch (unresolved install dir) — launch as-is.
                        launchShortcutNow(activity, s)
                    } else {
                        GoldbergPatcher.applyModeAsync(context, appId, installDir, s.name, gm) { _, _ ->
                            launchShortcutNow(activity, s)
                        }
                    }
                }
            }, "goldberg-apply-launch").start()
        }
        if (!GoldbergComponent.isInstalled(context)) {
            componentDownloadFor = s
            componentDownloadLabel = "Steam Emulator (Goldberg)"
            componentDownloadProgress = 0f
            GoldbergComponent.downloadAsync(
                context,
                { f -> componentDownloadProgress = f },
                { ok, msg ->
                    componentDownloadFor = null
                    if (ok) applyThenLaunch()
                    else Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
            )
        } else applyThenLaunch()
    }
    // SteamLite (RealSteam) launch: ensure the SteamLite package is present (download on demand if
    // not), then open the pre-flight dialog — the session/cloud/update checks run THERE, in the
    // library, so a dead sign-in or a stale build is reported before the container ever opens.
    fun launchWithSteamLite(s: Shortcut) {
        if (!SteamLiteComponent.isInstalled(context)) {
            componentDownloadFor = s
            componentDownloadLabel = "SteamLite (Real Steam / VAC)"
            componentDownloadProgress = 0f
            SteamLiteComponent.downloadAsync(
                context,
                { f -> componentDownloadProgress = f },
                { ok, msg ->
                    componentDownloadFor = null
                    if (ok) preflightFor = s
                    else Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
            )
        } else {
            preflightFor = s
        }
    }
    // The single launch choke point for the game grid/list. Every game opens the source-adaptive
    // launch-method popup first (Steam → SteamLite/Goldberg/Raw; Epic/GOG/Custom → Raw-only), UNLESS the
    // user already picked a method AND ticked "Remember" for it — a remembered pick launches DIRECTLY via
    // launchShortcutNow (the launchMode extra is honored by the launch pipeline; "Raw" is a plain launch).
    // A remembered RealSteam pick still goes through the SteamLite pre-flight (it is the launch's
    // session check, not part of the method choice).
    fun requestLaunch(shortcut: Shortcut) {
        // EA-published Steam titles have exactly one working path: the genuine client (SteamLite) via
        // EA Desktop's launcher chain. Skip the method popup, make sure the prefix is set up (wine-mono +
        // EA Desktop, one-time), and refuse titles that ship EA Javelin anti-cheat (kernel driver).
        if (isSteamOriginShortcut(shortcut)) {
            val ea = EaSupport.detectForShortcut(shortcut)
            if (ea != null) {
                if (ea.javelinAntiCheat) { eaUnsupportedFor = shortcut; return }
                // Persist: the launch pipeline re-reads the .desktop file, so an unsaved extra is a
                // plain Raw launch (device test #8 — the game started without the Steam client).
                shortcut.putExtra("launchMode", "RealSteam")
                shortcut.putExtra("launchModeRemembered", "1")
                shortcut.saveData()
                val installDir = EaSupport.installDirOf(shortcut)
                if (installDir == null) { launchWithSteamLite(shortcut); return }
                eaScope.launch {
                    val ready = withContext(Dispatchers.IO) {
                        try { EaSupport.prefixReady(context, shortcut.container, installDir) } catch (t: Throwable) { true }
                    }
                    if (ready) launchWithSteamLite(shortcut) else eaSetupFor = shortcut
                }
                return
            }
        }
        val remembered = shortcut.getExtra("launchMode", "").isNotEmpty() &&
            shortcut.getExtra("launchModeRemembered", "") == "1"
        when {
            remembered && shortcut.getExtra("launchMode", "") == "RealSteam" && isSteamOriginShortcut(shortcut) ->
                launchWithSteamLite(shortcut)
            remembered -> launchShortcutNow(activity, shortcut)
            else -> launchChoiceFor = shortcut
        }
    }
    // A SteamLite launch that failed inside the container (the launch overlay's "Retry" / "Launch
    // with Goldberg" buttons) records a pending relaunch before the session's normal exit restarts
    // the app; pick it up here, once the library is loaded, and re-enter the matching launch flow.
    LaunchedEffect(shortcuts) {
        if (shortcuts.isEmpty()) return@LaunchedEffect
        val pending = SteamSessionManager.takePendingRelaunch(context) ?: return@LaunchedEffect
        val s = shortcuts.firstOrNull { it.file.path == pending.shortcutPath } ?: return@LaunchedEffect
        when (pending.mode) {
            SteamSessionManager.RelaunchMode.STEAMLITE -> launchWithSteamLite(s)
            SteamSessionManager.RelaunchMode.GOLDBERG -> {
                SteamPrefs.init(context)
                val gm = SteamPrefs.getGoldbergMode(steamAppIdOf(s)).let { if (it == GoldbergMode.OFF) GoldbergMode.REGULAR else it }
                launchWithGoldberg(s, gm)
            }
        }
    }
    var showSortMenu by remember { mutableStateOf(false) }
    var showImportContainerPicker by remember { mutableStateOf(false) }
    var pendingImportContainerIndex by remember { mutableStateOf(-1) }
    // Bulk games-folder import: pick one folder holding many game folders, scan each for its exe,
    // then confirm the findings before anything is written to the container.
    var showImportMethodPicker by remember { mutableStateOf(false) }
    var folderScanRunning by remember { mutableStateOf(false) }
    var folderScanResults by remember { mutableStateOf<List<GameFolderScanner.Candidate>>(emptyList()) }
    var folderScanSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var folderScanRoot by remember { mutableStateOf("") }
    var folderImportRunning by remember { mutableStateOf(false) }
    // Manual exe override for a scanned game. The scanner keeps every runner-up, so correcting a
    // pick is a choice from a list rather than a rescan.
    var exePickerFor by remember { mutableStateOf<GameFolderScanner.Candidate?>(null) }
    var exeBrowseForPath by remember { mutableStateOf("") }
    // When checked, the shortcut import uses the system SAF picker instead of the in-app File Manager.
    var importUseSystemPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDialogName by remember { mutableStateOf("") }          // current on-disk base (Save's oldName)
    var renameDialogContainerIndex by remember { mutableStateOf(-1) }
    // Confirm-game dialog (upgraded rename dialog) state.
    var confirmNameField by remember { mutableStateOf("") }          // editable name text field
    var confirmNameEdited by remember { mutableStateOf(false) }      // user typed → stop auto-overwriting
    var confirmAppId by remember { mutableStateOf<Int?>(null) }      // detected/selected Steam appId
    var steamSearchResults by remember { mutableStateOf<List<SteamStoreSearch.SteamSuggestion>>(emptyList()) }
    var steamSearching by remember { mutableStateOf(false) }
    var scrapeTarget by remember { mutableStateOf<Shortcut?>(null) }
    val scrapeCovers = remember { mutableStateListOf<Pair<Bitmap, String>>() }
    var scrapeLoading by remember { mutableStateOf(false) }
    var communityTarget by remember { mutableStateOf<Shortcut?>(null) }
    var communityResult by remember { mutableStateOf<CommunityMatchResult?>(null) }
    var communityLoading by remember { mutableStateOf(false) }
    var communitySearch by remember(communityTarget) { mutableStateOf("") }
    var communitySearchResults by remember(communityTarget) { mutableStateOf<List<CanonicalGame>>(emptyList()) }
    // Catalog browser (catalog-first entry from the header) + the shared Phase 2 apply flow.
    var showCommunityBrowser by remember { mutableStateOf(false) }
    var applyPicker by remember { mutableStateOf<CommunityPick?>(null) }
    var applyMismatch by remember { mutableStateOf<Pair<Shortcut, CommunityPick>?>(null) }
    var applyBusy by remember { mutableStateOf(false) }
    var applyResult by remember { mutableStateOf<CommunityConfigApply.ConfigApplyResult?>(null) }
    // The shortcut the current result was applied to — threaded through so a post-install component
    // fixup can write the resolved version sub-field back to the right shortcut.
    var applyTarget by remember { mutableStateOf<Shortcut?>(null) }
    // Missing component the user tapped "Install" on → opens its single-type download sheet.
    var installSheetFor by remember { mutableStateOf<CommunityConfigApply.MissingComponent?>(null) }
    // Missing GPU driver the user tapped "Browse all drivers" on → opens the adrenotools driver browser.
    var driverSheetFor by remember { mutableStateOf<CommunityConfigApply.MissingDriver?>(null) }
    // Phase 3 step 2 — LOCAL export/import.
    // The generated export artifact awaiting a Share / Save-to-Downloads choice (null = no export sheet).
    var exportResult by remember { mutableStateOf<ShortcutExporter.ExportResult?>(null) }
    // The shortcut a freshly-picked import file applies to; null means it came from the catalog browser
    // (no target yet) so the picked file is stashed in [importedConfigUri] and a target picker is shown.
    var importPendingTarget by remember { mutableStateOf<Shortcut?>(null) }
    var importedConfigUri by remember { mutableStateOf<Uri?>(null) }
    // Phase 3 (online sharing) — UPLOAD. uploadingConfig gates the busy state; uploadStarted flips the
    // button text from "Preparing…" to "Uploading…" once the real upload begins (after any replace
    // confirm). When the user already shared a config for this game the worker gate is surfaced as a
    // replace-confirm: (existing record, proceed, cancel) — Replace calls proceed(), Cancel calls cancel()
    // so the parked coroutine unwinds cleanly.
    var uploadingConfig by remember { mutableStateOf(false) }
    var uploadStarted by remember { mutableStateOf(false) }
    var replaceUploadPrompt by remember { mutableStateOf<Triple<UploadedConfig, () -> Unit, () -> Unit>?>(null) }
    // Phase 3 (online sharing) — MY UPLOADS. showMyUploads opens the manager dialog; myUploads is the
    // loaded list (null = still loading). The list is expandable (single-expand via expandedUploadSha);
    // the expanded row's inline description editor shares uploadDescText / uploadDescLoading (reloaded on
    // expand). deleteUploadRow drives the delete-confirm sub-dialog.
    var showMyUploads by remember { mutableStateOf(false) }
    // Phase 2 (optional accounts) — the "My account" sheet. Opened from the globe browser's person icon;
    // hosts create/login/reset when logged out and profile + "My uploads" + "Log out" when signed in.
    var showMyAccount by remember { mutableStateOf(false) }
    var myUploads by remember { mutableStateOf<List<MyUploadRow>?>(null) }
    var deleteUploadRow by remember { mutableStateOf<MyUploadRow?>(null) }
    var expandedUploadSha by remember { mutableStateOf<String?>(null) }
    var uploadDescText by remember { mutableStateOf("") }
    var uploadDescLoading by remember { mutableStateOf(false) }
    // A tapped config row → small "Apply to game… | View details" chooser. The pair carries the picked
    // config (a specific uploaded file, or a device-row fallback) plus the in-context shortcut (non-null
    // from the per-shortcut sheet, null from the catalog browser where a target hasn't been chosen yet).
    var configAction by remember { mutableStateOf<Pair<CommunityPick, Shortcut?>?>(null) }
    // The config whose read-only detail page is open (same pick + optional-context-shortcut pair).
    var detailFor by remember { mutableStateOf<Pair<CommunityPick, Shortcut?>?>(null) }
    // Labels of missing components/drivers that resolved after an install (→ checkmark instead of a
    // button). Drivers are namespaced "driver:<wanted>" so they can't collide with component labels.
    val resolvedMissing = remember(applyResult) { mutableStateListOf<String>() }
    // Any install sheet (component OR driver) open → hide EVERY community dialog layer so the
    // ModalBottomSheet isn't rendered behind an AlertDialog's window; they reappear when it closes.
    // The chooser + detail layers join the predicate so the lower community dialogs (match/browser/
    // picker/result) don't stack behind them; the chooser/detail themselves are gated on installSheetOpen.
    val installSheetOpen = installSheetFor != null || driverSheetFor != null
    val communityDialogsGated = installSheetOpen || configAction != null || detailFor != null
    val scope = rememberCoroutineScope()

    // Shared apply runner — used by both the catalog browser and the per-shortcut sheet. Dispatches by
    // pick kind: a specific uploaded file applies THAT file; a device-row fallback applies the
    // best-for-device pick (offline path). Same downstream applyResult → smart-install flow either way.
    val runCommunityApply: (Shortcut, CommunityPick) -> Unit = { sc, pick ->
        applyBusy = true
        applyResult = null
        applyTarget = sc
        val onDone: (CommunityConfigApply.ConfigApplyResult) -> Unit = { res ->
            applyBusy = false
            applyResult = res
        }
        when (pick) {
            is CommunityPick.File -> vm.applyCommunityConfigFile(sc, pick.ref, onDone)
            is CommunityPick.Device -> vm.applyCommunityConfig(sc, pick.game, pick.device, onDone)
        }
    }
    // Kick off the real apply for a config: with an in-context shortcut (per-shortcut sheet) run it
    // straight; without one (browser) fall to the target picker. Reused by BOTH the chooser's "Apply to
    // game…" and the detail dialog's "Apply" so details never duplicates the apply/install flow.
    val startConfigApply: (CommunityPick, Shortcut?) -> Unit = { pick, sc ->
        if (sc != null) runCommunityApply(sc, pick) else applyPicker = pick
    }
    // Pick a target shortcut for a browser-selected config; warn when its game doesn't match.
    val chooseApplyTarget: (Shortcut, CommunityPick) -> Unit = { sc, pick ->
        applyPicker = null
        if (GameMatcher.match(sc.name, listOf(pick.game)).isNotEmpty()) runCommunityApply(sc, pick)
        else applyMismatch = sc to pick
    }

    // Phase 3 step 2 — IMPORT runner. Read + translate + apply an imported file to [sc], funnelling
    // into the SAME applyBusy → applyResult → smart-install flow a browsed config takes. A malformed
    // file returns a clean ok=false result (shown by the existing "Couldn't apply" dialog), never a crash.
    val runImport: (Shortcut, Uri) -> Unit = { sc, uri ->
        applyBusy = true
        applyResult = null
        applyTarget = sc
        vm.importConfigFile(uri, sc) { res ->
            applyBusy = false
            applyResult = res
        }
    }

    // Phase 3 step 2 — EXPORT. Write the generated config to cacheDir/community_configs/export/<file>
    // off-main, then hand it off. Share uses the app's existing FileProvider (${applicationId}.tileprovider,
    // the same authority the save-share + updater use); Save copies it to public Downloads and toasts.
    val shareExport: (ShortcutExporter.ExportResult) -> Unit = { res ->
        exportResult = null
        scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "community_configs/export").apply { mkdirs() }
            val file = File(dir, res.fileName)
            file.writeText(res.json)
            withContext(Dispatchers.Main) {
                try {
                    val authority = context.packageName + ".tileprovider"
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, res.game)
                        putExtra(Intent.EXTRA_TEXT, "WinHub config for ${res.game}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Share config"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Couldn't share the config.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val saveExportToDownloads: (ShortcutExporter.ExportResult) -> Unit = { res ->
        exportResult = null
        scope.launch(Dispatchers.IO) {
            val ok = try {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                // Exported configs live under Download/bannerlator/game-configs/ (created if absent).
                val exportDir = File(downloads, "bannerlator/game-configs")
                if (!exportDir.exists()) exportDir.mkdirs()
                val out = File(exportDir, res.fileName)
                out.writeText(res.json)
                out.setReadable(true, false)
                MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
                out.absolutePath
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (ok != null) Toast.makeText(context, "Saved to $ok", Toast.LENGTH_LONG).show()
                else Toast.makeText(context, "Couldn't save the config.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Shared "Scrape cover" action so both grid tiles and list rows fire the same flow.
    val scrapeCoverFor: (Shortcut) -> Unit = { shortcut ->
        scrapeTarget = shortcut
        scrapeCovers.clear()
        scrapeLoading = true
        scope.launch(Dispatchers.IO) {
            val json = StarLaunchBridge.sgdbFetchGridsJson(shortcut.name)
            val covers = mutableListOf<Pair<Bitmap, String>>()
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val thumbUrl = obj.optString("thumb", "")
                    val fullUrl = obj.optString("url", "")
                    if (thumbUrl.isNotEmpty() && fullUrl.isNotEmpty()) {
                        val conn = java.net.URL(thumbUrl).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10000
                        conn.readTimeout = 10000
                        val bmp = BitmapFactory.decodeStream(conn.inputStream)
                        conn.disconnect()
                        if (bmp != null) covers.add(bmp to fullUrl)
                    }
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Main) {
                scrapeCovers.clear()
                scrapeCovers.addAll(covers)
                scrapeLoading = false
            }
        }
    }

    // Shared "Community configs" action — opens the sheet and kicks off the offline-first match.
    val communityConfigsFor: (Shortcut) -> Unit = { shortcut ->
        communityTarget = shortcut
        communityResult = null
        communityLoading = true
        vm.matchCommunityConfigs(shortcut) { result ->
            communityResult = result
            communityLoading = false
        }
    }

    // Post-install fixup shared by the inline installer and the "Browse all versions" fallback sheet:
    // re-read what's on disk off-main, surgically auto-apply the resolved version to the target
    // shortcut, then mark the row done + refresh. Same behaviour the download sheet's onContentChanged had.
    val applyAfterInstall: (CommunityConfigApply.MissingComponent) -> Unit = { mc ->
        val target = applyTarget
        if (target != null) {
            scope.launch {
                val resolved = withContext(Dispatchers.IO) {
                    val installed = com.winhub.hernandez.communityconfigs.InstalledComponents.read(context)
                    // Try the exact wanted version first. If the user installed a CLOSEST build instead
                    // (e.g. a date-stamped FEX like "Fex-20260103" that has no exact catalog match), the
                    // wanted string never re-resolves — so fall back to the NEWEST installed build of this
                    // type, i.e. the one that was just installed, and apply that.
                    if (CommunityConfigApply.applyResolvedComponent(target, mc, installed)) {
                        true
                    } else {
                        val newest = CommunityConfigApply.installedTypeKey(mc.type)?.let { installed.newestToken(it) }
                        newest != null &&
                            CommunityConfigApply.applyResolvedComponent(target, mc.copy(wanted = newest), installed)
                    }
                }
                if (resolved) {
                    if (mc.label !in resolvedMissing) resolvedMissing.add(mc.label)
                    vm.refresh()
                    Toast.makeText(context, "Installed and applied to \"${target.name}\".", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "Installed, but couldn't auto-apply — open \"Browse all versions\" to finish.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    fun handleShortcutImport(uri: Uri) {
        if (pendingImportContainerIndex >= 0) {
            val result = vm.importShortcut(pendingImportContainerIndex, uri, context)
            when (result) {
                is ImportResult.Success -> {
                    renameDialogContainerIndex = pendingImportContainerIndex
                    renameDialogName = result.shortcutName
                    confirmNameField = result.shortcutName
                    confirmNameEdited = false
                    confirmAppId = result.appId
                    steamSearchResults = emptyList()
                    steamSearching = false
                    showRenameDialog = true
                }
                is ImportResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            pendingImportContainerIndex = -1
        }
    }
    // System SAF picker (secondary).
    val importFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleShortcutImport(uri)
    }

    // ── Save Backup / Restore (custom-import games only) ──────────────────────────────────────────
    // In-app file picker for a backup .zip; on pick we hold the uri and show a target-container picker.
    // (GameSaveBackup.restore auto-detects the layout — GameHub steamuser <-> our xuser — so a GameHub
    // or Bannerlator save both restore through this one path; no format prompt needed.)
    val restoreSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedUri(result.data)?.let { restoreZipUri = it }
    }

    // "Back up saves" → first pick the archive layout (Winlator vs GameHub), mirroring the Containers
    // backup menu; the chosen layout runs in runCustomBackup below.
    fun startSaveBackup(shortcut: Shortcut) {
        backupFormatShortcut = shortcut
    }

    // Back up this game's saves into the shared per-game folder via the one shared impl
    // (CustomSaveVault.manualBackup) so the ⋮ menu and the Save Manager agree; the dialog/UX + toasts
    // stay here. manualBackup discovers roots (with whole-container fallback) and zips off the main
    // thread, posting its result on the main thread.
    fun runCustomBackup(shortcut: Shortcut, layout: GameSaveBackup.BackupLayout) {
        val name = shortcut.name
        Toast.makeText(context, "Backing up saves for \"$name\"…", Toast.LENGTH_SHORT).show()
        CustomSaveVault.manualBackup(context, shortcut.container, shortcut, layout) { r ->
            if (r.wholeContainer && r.ok) {
                Toast.makeText(context, "No per-game saves detected — backed up the whole container.", Toast.LENGTH_LONG).show()
            }
            Toast.makeText(
                context,
                if (r.ok) "Backed up ${r.fileCount} files → ${r.path?.substringAfterLast('/')}"
                else "Backup failed: ${r.error ?: "unknown error"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Restore: pick a .zip (SAF) → then choose the target container (ContainerPickerDialog) → restore.
    fun startSaveRestore(shortcut: Shortcut) {
        restoreForName = shortcut.name
        restoreSaveLauncher.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.SAVE, "Select a save .zip"))
    }
    // Built-in in-app file picker (primary).
    val importFileInAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedUri(result.data)?.let { handleShortcutImport(it) }
    }
    // Games-folder picker. Uses buildDirIntent (a real absolute path, works on SD) rather than SAF,
    // which hands back /mnt/media_rw/... paths the scanner can't read.
    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val path = if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedPath(result.data) else null
        if (path == null) {
            pendingImportContainerIndex = -1
            return@rememberLauncherForActivityResult
        }
        val containerIndex = pendingImportContainerIndex
        folderScanRoot = path
        folderScanRunning = true
        scope.launch {
            // Filesystem-heavy: a large library on SD walks a lot of directories.
            val found = withContext(Dispatchers.IO) { vm.scanGamesFolder(containerIndex, path) }
            folderScanResults = found
            // Pre-select everything importable; duplicates stay off and can't be ticked.
            folderScanSelected = found.filter { !it.alreadyAdded }.map { it.exe.absolutePath }.toSet()
            folderScanRunning = false
            if (found.isEmpty()) {
                Toast.makeText(context, "No games found in that folder", Toast.LENGTH_LONG).show()
                pendingImportContainerIndex = -1
            }
        }
    }
    // Phase 3 step 2 — config-import picker (in-app File Manager, `.json` only). A known
    // [importPendingTarget] applies straight to that shortcut; otherwise (from the catalog browser)
    // the picked file is stashed and a target picker is shown.
    val importConfigInAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedUri(result.data)?.let { uri ->
                val target = importPendingTarget
                if (target != null) runImport(target, uri) else importedConfigUri = uri
            }
        }
    }
    // Launch the config-import picker for [target] (null = from the browser → pick a target afterwards).
    val launchConfigImport: (Shortcut?) -> Unit = { target ->
        importPendingTarget = target
        importConfigInAppLauncher.launch(
            InAppFilePicker.buildIntent(context, InAppFilePicker.JSON, "Select a config .json")
        )
    }
    // Open the My-uploads manager (shared by the per-game dialog button AND the My-account sheet).
    val openMyUploads: () -> Unit = {
        myUploads = null
        expandedUploadSha = null
        showMyUploads = true
        vm.loadMyUploads { myUploads = it }
    }
    // Open the My-account sheet (Phase 2), the globe browser's person-icon entry point.
    val openMyAccount: () -> Unit = { showMyAccount = true }
    // Phase 3: the nav-drawer's profile header lands here then flips this one-shot flag — open the sheet.
    LaunchedEffect(AccountUiBus.openMyAccountRequested) {
        if (AccountUiBus.openMyAccountRequested) {
            AccountUiBus.openMyAccountRequested = false
            showMyAccount = true
        }
    }
    // Tier-2 session-return: after an installer-based component install finishes and MainActivity has
    // routed us here, re-open the originating shortcut's settings (defaults to the Win Components tab,
    // where its recommended-components chips live). Keyed on the shortcuts list too, since it loads
    // async — wait for it, then resolve by container + base name. Best-effort: no match ⇒ stay on Games.
    LaunchedEffect(ComponentReturnBus.openShortcutSettings, shortcuts) {
        val target = ComponentReturnBus.openShortcutSettings ?: return@LaunchedEffect
        if (shortcuts.isEmpty()) return@LaunchedEffect // still loading (or none) — retry when it changes
        ComponentReturnBus.openShortcutSettings = null
        shortcuts.firstOrNull {
            it.container.id == target.containerId && it.name == target.shortcutBase
        }?.let { settingsShortcut = it }
    }

    val topBarActions = LocalTopBarActions.current
    // The XMB view draws its own backdrop, so it asks for a see-through top bar (MainActivity only
    // honours that on the Games route). Dropped when leaving XMB or this screen.
    val topBarTransparent = LocalTopBarTransparent.current
    LaunchedEffect(viewMode, shortcuts.isEmpty()) {
        topBarTransparent.value = viewMode == ShortcutViewMode.XMB && shortcuts.isNotEmpty()
    }
    DisposableEffect(Unit) { onDispose { topBarTransparent.value = false } }
    // LaunchedEffect — not SideEffect — so this runs in the same dispatcher queue as
    // MainActivity's route-change clear (which is a LaunchedEffect). Parent enqueues
    // first and runs first (clears); we enqueue second and run after (sets). A
    // SideEffect would run synchronously during commit, getting steamrolled by the
    // parent's clear when it fires post-commit.
    LaunchedEffect(viewMode, selectionMode, selectedPaths) {
        topBarActions.value = {
            // Steam friends + chat — only renders when signed in to Steam (login-gated internally).
            SteamFriendsAction()
            IconButton(onClick = { showCommunityBrowser = true }) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = "Community configs",
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            if (selectionMode) {
                Text(
                    "${selectedPaths.size}",
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(end = 2.dp),
                )
                IconButton(
                    onClick = { confirmRemoveSelected = true },
                    enabled = selectedPaths.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove selected",
                        tint = if (selectedPaths.isEmpty())
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                        else DangerRed,
                    )
                }
            }
            IconButton(onClick = {
                selectionMode = !selectionMode
                if (!selectionMode) selectedPaths = emptySet()
            }) {
                Icon(
                    imageVector = if (selectionMode) Icons.Filled.Close else Icons.Filled.Checklist,
                    contentDescription = if (selectionMode) "Cancel selection" else "Select shortcuts",
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            // One button cycling list → grid → compact grid → XMB. The icon shows what you get NEXT,
            // matching how the two-state version behaved.
            IconButton(onClick = { vm.cycleViewMode() }) {
                Icon(
                    imageVector = when (viewMode) {
                        ShortcutViewMode.LIST -> Icons.Filled.GridView
                        ShortcutViewMode.GRID -> Icons.Filled.Apps
                        ShortcutViewMode.GRID_COMPACT -> Icons.Filled.ViewList
                        ShortcutViewMode.XMB -> Icons.Filled.GridView
                    },
                    contentDescription = when (viewMode) {
                        ShortcutViewMode.LIST -> "Grid view"
                        ShortcutViewMode.GRID -> "Compact grid view"
                        ShortcutViewMode.GRID_COMPACT -> "List view"
                        ShortcutViewMode.XMB -> "Grid view"
                    },
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Sort", tint = androidx.compose.ui.graphics.Color.White)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    val orders = listOf(
                        ShortcutSortOrder.NAME_ASC  to "Name A→Z",
                        ShortcutSortOrder.NAME_DESC to "Name Z→A",
                        ShortcutSortOrder.CONTAINER to "Container",
                    )
                    orders.forEach { (order, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = if (sortOrder == order)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = { vm.setSortOrder(order); showSortMenu = false },
                        )
                    }
                }
            }
        }
    }

    // Refresh list whenever this screen resumes (consistent with ContainersScreen and SavesScreen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // XMB options column = a card's ⋮ menu, reordered for a D-pad (common first, Remove last). Items with
    // a `menu` open as nested XMB columns (XmbGameSettings.kt / XmbSettingsPhase2*.kt / XmbGameTools*.kt)
    // instead of pop-ups; the three that hand off to Android or a full screen keep their handlers.
    val xmbHost = XmbGameHost(
        remove = { vm.remove(it, context) },
        containers = { vm.containers() },
        openWrapperManager = { showWrapperManagerXmb = true },
    )
    val xmbActionsFor: (Shortcut) -> List<XmbAction> = { shortcut ->
        buildList {
            add(XmbAction("Settings", Icons.Filled.Settings, "Display, graphics, controller…", menu = { xmbSettingsMenu(it, shortcut, xmbHost) }))
            add(XmbAction("Game Details", Icons.Filled.Edit, "Name, genres, year, description", menu = { xmbGameDetailsMenu(it, shortcut) }))
            if (isSteamOriginShortcut(shortcut)) {
                add(XmbAction("Cloud Saves", Icons.Filled.CloudSync, "Steam Cloud sync — opens its own screen") { launchSaveManager(context, steamAppIdOf(shortcut)) })
            }
            if (isCustomShortcut(shortcut)) {
                add(XmbAction("Back up saves", Icons.Filled.Archive, menu = { xmbBackupSavesMenu(it, shortcut) }))
                add(XmbAction("Restore saves", Icons.Filled.Unarchive, menu = { xmbRestoreSavesMenu(it, shortcut) }))
            }
            add(XmbAction("Community configs", Icons.Filled.Public, "Shared settings for this game", menu = { xmbCommunityConfigsMenu(it, shortcut) }))
            add(XmbAction("Scrape cover", Icons.Filled.Search, menu = { xmbScrapeCoverMenu(it, shortcut) }))
            add(XmbAction("View logs", Icons.Filled.Description, menu = { xmbLogsMenu(it, shortcut) }))
            add(XmbAction("Clone to container", Icons.Filled.ContentCopy, menu = { xmbCloneMenu(it, shortcut, xmbHost) }))
            add(XmbAction("Copy to Drive C…", Icons.Filled.DriveFileMove, menu = { xmbCopyToDriveCMenu(it, shortcut) }))
            add(XmbAction("Change executable…", Icons.Filled.SwapHoriz, menu = { xmbChangeExeMenu(it, shortcut) }))
            add(XmbAction("Add to home screen", Icons.Filled.AddToHomeScreen, "Android's pin prompt") { addToHomeScreen(context, shortcut) })
            add(XmbAction("Export", Icons.Filled.Upload) { exportShortcut(context, shortcut) })
            add(XmbAction("Properties", Icons.Filled.Info, "Times played, playtime", menu = { xmbPropertiesMenu(it, shortcut) }))
            add(XmbAction("Remove", Icons.Filled.Delete, "Asks to confirm first", danger = true, menu = { xmbRemoveMenu(it, shortcut, xmbHost) }))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (shortcuts.isEmpty()) {
                Text(
                    text = "No shortcuts yet.",
                    color = OnSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                AnimatedContent(targetState = viewMode, label = "layout") { mode ->
                    if (mode == ShortcutViewMode.XMB) {
                        ShortcutsXmbView(
                            shortcuts = shortcuts,
                            selectionMode = selectionMode,
                            selectedPaths = selectedPaths,
                            onToggleSelect = { selectedPaths = selectedPaths.toggle(it.file.path) },
                            onExitSelection = { selectionMode = false; selectedPaths = emptySet() },
                            onPlay = { requestLaunch(it) },
                            actionsFor = xmbActionsFor,
                            onNavigate = onNavigate,
                            sdBadge = { shortcut ->
                                if (remember(shortcut) { WinePath.isOnRemovableStorage(shortcut.container, shortcut.path) }) {
                                    SdCardBadge(Modifier.padding(start = 6.dp))
                                }
                            },
                            onReloadGames = { vm.refresh() },
                            onNestedChange = { xmbNested = it },
                        )
                    } else if (mode != ShortcutViewMode.LIST) {
                        // Compact keeps a CONSTANT tile size across orientation: derive the column
                        // count from the shortest screen edge so portrait resolves to exactly 4 and
                        // landscape flows to more columns of the SAME width (was Fixed(4) → tiles
                        // ballooned to giants in landscape). The original grid stays adaptive.
                        val cfg = LocalConfiguration.current
                        val compactCols = (cfg.screenWidthDp.toFloat() /
                            (minOf(cfg.screenWidthDp, cfg.screenHeightDp) / 4f)).roundToInt().coerceAtLeast(4)
                        LazyVerticalGrid(
                            columns = if (mode == ShortcutViewMode.GRID_COMPACT) GridCells.Fixed(compactCols)
                                      else GridCells.Adaptive(minSize = 120.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shortcuts, key = { it.file.path }) { shortcut ->
                                ShortcutGridItem(
                                    shortcut = shortcut,
                                    selectionMode = selectionMode,
                                    selected = shortcut.file.path in selectedPaths,
                                    onRun = {
                                        if (selectionMode) selectedPaths = selectedPaths.toggle(shortcut.file.path)
                                        else requestLaunch(shortcut)
                                    },
                                    onSettings = { settingsShortcut = shortcut },
                                    onRemove = { confirmRemove = shortcut },
                                    onClone = { cloneTarget = shortcut },
                                    onCopyToDriveC = { copyToDriveCTarget = shortcut },
                                    onChangeExe = { changeExeTarget = shortcut },
                                    onAddToHome = { addToHomeScreen(context, shortcut) },
                                    onExport = { exportShortcut(context, shortcut) },
                                    onProperties = { propertiesShortcut = shortcut },
                                    onScrapeCover = { scrapeCoverFor(shortcut) },
                                    onCommunityConfigs = { communityConfigsFor(shortcut) },
                                    onGameDetails = { gameDetailsShortcut = shortcut },
                                    onViewLogs = { logsShortcut = shortcut },
                                    onCloudSaves = if (isSteamOriginShortcut(shortcut))
                                        ({ launchSaveManager(context, steamAppIdOf(shortcut)) }) else null,
                                    onBackupSaves = if (isCustomShortcut(shortcut))
                                        ({ startSaveBackup(shortcut) }) else null,
                                    onRestoreSaves = if (isCustomShortcut(shortcut))
                                        ({ startSaveRestore(shortcut) }) else null,
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(shortcuts, key = { it.file.path }) { shortcut ->
                                val itemRun = {
                                    if (selectionMode) selectedPaths = selectedPaths.toggle(shortcut.file.path)
                                    else requestLaunch(shortcut)
                                }
                                val itemSettings = { settingsShortcut = shortcut }
                                val itemRemove = { confirmRemove = shortcut }
                                val itemClone = { cloneTarget = shortcut }
                                val itemCopyToDriveC = { copyToDriveCTarget = shortcut }
                                val itemChangeExe = { changeExeTarget = shortcut }
                                val itemAddToHome = { addToHomeScreen(context, shortcut) }
                                val itemExport = { exportShortcut(context, shortcut) }
                                val itemProperties = { propertiesShortcut = shortcut }
                                ShortcutItemLayoutL(
                                    shortcut = shortcut,
                                    selectionMode = selectionMode,
                                    selected = shortcut.file.path in selectedPaths,
                                    onRun = itemRun,
                                    onSettings = itemSettings,
                                    onRemove = itemRemove,
                                    onClone = itemClone,
                                    onCopyToDriveC = itemCopyToDriveC,
                                    onChangeExe = itemChangeExe,
                                    onAddToHome = itemAddToHome,
                                    onExport = itemExport,
                                    onProperties = itemProperties,
                                    onScrapeCover = { scrapeCoverFor(shortcut) },
                                    onCommunityConfigs = { communityConfigsFor(shortcut) },
                                    onGameDetails = { gameDetailsShortcut = shortcut },
                                    onViewLogs = { logsShortcut = shortcut },
                                    onCloudSaves = if (isSteamOriginShortcut(shortcut))
                                        ({ launchSaveManager(context, steamAppIdOf(shortcut)) }) else null,
                                    onBackupSaves = if (isCustomShortcut(shortcut))
                                        ({ startSaveBackup(shortcut) }) else null,
                                    onRestoreSaves = if (isCustomShortcut(shortcut))
                                        ({ startSaveRestore(shortcut) }) else null,
                                )
                            }
                        }
                    }
                }
            }
            // Long-press and slide along the bottom to move it off a card's buttons. Hidden while an XMB
            // menu is open (it would sit on top of the menu's rows).
            if (!(viewMode == ShortcutViewMode.XMB && xmbNested)) DraggableAddButton(
                prefKey = "games",
                onClick = { showImportContainerPicker = true },
                outerPadding = 16.dp,
                buttonModifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add Shortcut",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }

    /**
     * Swaps the chosen exe for one scanned game. The user has now made the call explicitly, so the
     * "check this one" flag is cleared, the previous pick joins the alternatives (in case they want
     * to go back), and the selection set is re-keyed since it is keyed by exe path.
     */
    fun replaceScannedExe(target: GameFolderScanner.Candidate, newExe: File) {
        val oldKey = target.exe.absolutePath
        val newKey = newExe.absolutePath
        folderScanResults = folderScanResults.map { c ->
            if (c.exe.absolutePath != oldKey) c else c.copy(
                exe = newExe,
                uncertain = false,
                alternatives = (listOf(c.exe) + c.alternatives)
                    .distinctBy { it.absolutePath }
                    .filter { it.absolutePath != newKey },
            )
        }
        if (oldKey in folderScanSelected) folderScanSelected = folderScanSelected - oldKey + newKey
    }

    // "Browse…" result from the exe-override dialog: any file the user points at wins outright.
    val importExeBrowseLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val path = if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedPath(result.data) else null
        val target = folderScanResults.firstOrNull { it.exe.absolutePath == exeBrowseForPath }
        if (path != null && target != null) replaceScannedExe(target, File(path))
        exeBrowseForPath = ""
    }

    // Manual exe override — every candidate exe found in that game's folder, best-ranked first.
    exePickerFor?.let { target ->
        val options = (listOf(target.exe) + target.alternatives).distinctBy { it.absolutePath }
        OutlinedAlertDialog(
            // The platform default width truncates exe names and their subfolder paths, which are
            // the whole point of this list.
            modifier = Modifier.fillMaxWidth(0.94f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { exePickerFor = null },
            title = {
                Column {
                    Text("Choose the game's .exe")
                    Text(
                        target.name,
                        color = OnSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(options, key = { it.absolutePath }) { exe ->
                        val current = exe.absolutePath == target.exe.absolutePath
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    replaceScannedExe(target, exe)
                                    exePickerFor = null
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current, onClick = {
                                replaceScannedExe(target, exe)
                                exePickerFor = null
                            })
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    exe.name,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // Where it sits inside the game folder — the thing that actually
                                // distinguishes two same-named exes (x86 vs x64, bin/ vs root).
                                val rel = exe.absolutePath
                                    .removePrefix(target.folder.absolutePath)
                                    .removePrefix("/")
                                Text(
                                    if (rel.contains('/')) rel.substringBeforeLast('/') else "(folder root)",
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        }
                    }
                }
            },
            confirmButton = {
                // Escape hatch: the real exe can be missing from the list if it was filtered as an
                // installer/helper or sits deeper than the scan goes.
                TextButton(onClick = {
                    exeBrowseForPath = target.exe.absolutePath
                    exePickerFor = null
                    importExeBrowseLauncher.launch(
                        InAppFilePicker.buildIntent(context, InAppFilePicker.SHORTCUT, "Select the game's .exe")
                    )
                }) { Text("Browse…") }
            },
            dismissButton = {
                TextButton(onClick = { exePickerFor = null }) { Text("Cancel") }
            },
        )
    }

    // How to add: one exe (the original flow) or a whole folder of game folders.
    if (showImportMethodPicker) {
        OutlinedAlertDialog(
            onDismissRequest = {
                showImportMethodPicker = false
                pendingImportContainerIndex = -1
            },
            title = { Text("Add games") },
            text = {
                Column {
                    MenuOptionCard(
                        title = "Add game EXE",
                        subtitle = "Pick one game's .exe file",
                        icon = Icons.Default.InsertDriveFile,
                    ) {
                        showImportMethodPicker = false
                        // A container is already chosen here — hand the in-app picker its C: drive so
                        // the user can pick a game living on C: (games picked under drive_c import as
                        // C:\… via WinePath, running FROM the container's C:). Null-safe: only when the
                        // C: drive actually exists on disk. The system picker (SAF) has no C: notion.
                        val driveC = pendingImportContainerIndex.takeIf { it >= 0 }
                            ?.let { vm.containers().getOrNull(it) }
                            ?.let { File(it.rootDir, ".wine/drive_c") }
                            ?.takeIf { it.isDirectory }
                        if (importUseSystemPicker) importFileLauncher.launch("*/*")
                        else importFileInAppLauncher.launch(
                            InAppFilePicker.buildIntent(context, InAppFilePicker.SHORTCUT, "Select .exe / .desktop / .lnk", driveCPath = driveC?.absolutePath)
                        )
                    }
                    MenuOptionCard(
                        title = "Add games folder",
                        subtitle = "Pick the folder holding all your games — each one is scanned for its .exe",
                        icon = Icons.Default.Folder,
                    ) {
                        showImportMethodPicker = false
                        // Same C: hand-off as the single-exe path: let the folder picker browse the
                        // chosen container's C: drive (scanned games under drive_c import as C:\…).
                        val driveC = pendingImportContainerIndex.takeIf { it >= 0 }
                            ?.let { vm.containers().getOrNull(it) }
                            ?.let { File(it.rootDir, ".wine/drive_c") }
                            ?.takeIf { it.isDirectory }
                        importFolderLauncher.launch(
                            InAppFilePicker.buildDirIntent(context, "Select your games folder", driveCPath = driveC?.absolutePath)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showImportMethodPicker = false
                    pendingImportContainerIndex = -1
                }) { Text("Cancel") }
            },
        )
    }

    // Scanning progress — a big library on SD takes a moment.
    if (folderScanRunning) {
        OutlinedAlertDialog(
            onDismissRequest = {},
            title = { Text("Scanning for games") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(folderScanRoot, color = OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {},
        )
    }

    // Confirm screen — nothing is written until the user accepts this list.
    if (folderScanResults.isNotEmpty() && !folderScanRunning) {
        val importable = folderScanResults.filter { !it.alreadyAdded }
        val selectedCount = folderScanSelected.size
        OutlinedAlertDialog(
            // Each row carries art, a checkbox, three lines of text and a Change action; at the
            // platform default width the titles and the exe name truncate to the point of being
            // unreadable, which defeats a screen whose whole job is letting the user check them.
            modifier = Modifier.fillMaxWidth(0.94f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = {
                if (!folderImportRunning) {
                    folderScanResults = emptyList()
                    folderScanSelected = emptySet()
                    pendingImportContainerIndex = -1
                }
            },
            title = {
                Column {
                    Text("Found ${importable.size} game${if (importable.size == 1) "" else "s"}")
                    val skipped = folderScanResults.size - importable.size
                    if (skipped > 0) {
                        Text(
                            "$skipped already added",
                            color = OnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(folderScanResults, key = { it.exe.absolutePath }) { candidate ->
                        ScannedGameRow(
                            candidate = candidate,
                            checked = candidate.exe.absolutePath in folderScanSelected,
                            enabled = !candidate.alreadyAdded && !folderImportRunning,
                            onToggle = {
                                val key = candidate.exe.absolutePath
                                folderScanSelected = if (key in folderScanSelected) {
                                    folderScanSelected - key
                                } else {
                                    folderScanSelected + key
                                }
                            },
                            onChangeExe = { exePickerFor = candidate },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedCount > 0 && !folderImportRunning,
                    onClick = {
                        val containerIndex = pendingImportContainerIndex
                        val chosen = folderScanResults.filter { it.exe.absolutePath in folderScanSelected }
                        folderImportRunning = true
                        scope.launch {
                            val summary = withContext(Dispatchers.IO) {
                                vm.importScannedGames(containerIndex, chosen, context)
                            }
                            folderImportRunning = false
                            folderScanResults = emptyList()
                            folderScanSelected = emptySet()
                            pendingImportContainerIndex = -1
                            val message = if (summary.failed == 0) {
                                "Added ${summary.added} game${if (summary.added == 1) "" else "s"}"
                            } else {
                                "Added ${summary.added}, ${summary.failed} failed — ${summary.failures.firstOrNull().orEmpty()}"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                ) {
                    Text(if (folderImportRunning) "Adding…" else "Add $selectedCount")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !folderImportRunning,
                    onClick = {
                        folderScanResults = emptyList()
                        folderScanSelected = emptySet()
                        pendingImportContainerIndex = -1
                    },
                ) { Text("Cancel") }
            },
        )
    }

    // Import container picker
    if (showImportContainerPicker) {
        val containers = vm.containers()
        OutlinedAlertDialog(
            onDismissRequest = { showImportContainerPicker = false },
            title = { Text("Select container") },
            text = {
                Column {
                    if (containers.isEmpty()) {
                        Text("No containers found.", color = OnSurfaceVariant)
                    } else {
                        // Scroll the container list so it can't be clipped when there are many
                        // containers and vertical space is tight (landscape). "Pick via system…"
                        // stays pinned below the scroll area so it's always reachable.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            containers.forEachIndexed { index, c ->
                                MenuOptionCard(
                                    title = c.name,
                                    icon = Icons.Default.Folder,
                                ) {
                                    showImportContainerPicker = false
                                    pendingImportContainerIndex = index
                                    // Ask HOW to add before asking WHAT to add: one exe, or a whole
                                    // folder of game folders.
                                    showImportMethodPicker = true
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Checkbox(checked = importUseSystemPicker, onCheckedChange = { importUseSystemPicker = it })
                            Text("Pick via system…", color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImportContainerPicker = false }) { Text("Cancel") } },
        )
    }

    // Follow the importer's async Steam-name auto-rename inside an open confirm dialog: keep the
    // Save target (renameDialogName = the on-disk base) in sync, and update the editable field
    // unless the user has already typed into it. Race-free — all on the main thread, one file writer.
    val importedNameUpdate by vm.importedNameUpdate.collectAsState()
    LaunchedEffect(importedNameUpdate) {
        val update = importedNameUpdate ?: return@LaunchedEffect
        if (showRenameDialog && renameDialogName == update.oldBase) {
            renameDialogName = update.newBase
            if (!confirmNameEdited) confirmNameField = update.newBase
        }
        vm.consumeImportedNameUpdate()
    }

    // Confirm game after import: editable name + "Search Steam" picker (fixes launcher-named
    // shortcuts + wrong cover art). Reuses the existing rename mechanism on Save.
    if (showRenameDialog) {
        val confirmContainer = vm.containers().getOrNull(renameDialogContainerIndex)
        OutlinedAlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Confirm game") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = confirmNameField,
                        onValueChange = { confirmNameField = it; confirmNameEdited = true },
                        label = { Text("Game name") },
                        singleLine = true,
                        trailingIcon = {
                            if (confirmNameField.isNotEmpty()) {
                                IconButton(onClick = { confirmNameField = ""; confirmNameEdited = true }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = {
                            val query = confirmNameField.trim()
                            if (query.isEmpty() || confirmContainer == null) return@Button
                            steamSearching = true
                            steamSearchResults = emptyList()
                            scope.launch(Dispatchers.IO) {
                                val results = SteamStoreSearch.searchByName(query)
                                withContext(Dispatchers.Main) {
                                    steamSearchResults = results
                                    steamSearching = false
                                }
                            }
                        },
                        enabled = !steamSearching && confirmNameField.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (steamSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Searching…")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Search Steam")
                        }
                    }

                    if (steamSearchResults.isNotEmpty()) {
                        Text(
                            "Tap a result to set the name + cover art:",
                            color = OnSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        steamSearchResults.forEach { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        // Set name + record appId; edit-guard off so the async
                                        // auto-rename won't clobber the user's explicit pick.
                                        confirmNameField = hit.name
                                        confirmNameEdited = true
                                        confirmAppId = hit.appId
                                        // Apply this appId's cover art to the current on-disk shortcut.
                                        if (confirmContainer != null) {
                                            val base = renameDialogName
                                            scope.launch(Dispatchers.IO) {
                                                val bmp = applySteamCover(confirmContainer, base, hit.appId)
                                                if (bmp != null) withContext(Dispatchers.Main) {
                                                    vm.reloadShortcut(
                                                        File(confirmContainer.getDesktopDir(), "$base.desktop").path,
                                                        bmp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SteamResultThumbnail(hit.appId)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(hit.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("App ID: ${hit.appId}", fontSize = 11.sp, color = OnSurfaceVariant)
                                }
                            }
                        }
                    } else if (!steamSearching) {
                        Text(
                            "Not the right game? Edit the name and tap Search Steam to pick the correct one.",
                            color = OnSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }

                    // Recommended components — the redists this game bundles, one-tap installable into
                    // its container. Detection runs off-main-thread inside the section (Pillar 2/2.2).
                    if (confirmContainer != null) {
                        val importedExe = remember(confirmContainer, renameDialogName) {
                            runCatching {
                                val sc = Shortcut(
                                    confirmContainer,
                                    File(confirmContainer.getDesktopDir(), "$renameDialogName.desktop"),
                                )
                                WinePath.resolveAndroidPath(confirmContainer, sc.path)
                            }.getOrNull()
                        }
                        if (importedExe != null) {
                            Divider(color = DividerColor)
                            RecommendedComponentsSection(
                                container = confirmContainer,
                                exeFile = importedExe,
                                shortcutBaseName = renameDialogName,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = confirmNameField.trim()
                    // Record the confirmed appId on the current file first, so it rides through the rename.
                    confirmAppId?.let { id ->
                        confirmContainer?.let { c -> recordSteamAppId(c, renameDialogName, id) }
                    }
                    if (name.isNotEmpty()) {
                        vm.renameImportedShortcut(renameDialogContainerIndex, renameDialogName, name)
                    }
                    showRenameDialog = false
                    Toast.makeText(context, "Shortcut imported.", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    Toast.makeText(context, "Shortcut imported.", Toast.LENGTH_SHORT).show()
                }) { Text("Skip") }
            },
        )
    }

    // Remove confirmation
    confirmRemove?.let { s ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove shortcut?") },
            text = { Text("Remove \"${s.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val ok = vm.remove(s, context)
                    confirmRemove = null
                    Toast.makeText(
                        context,
                        if (ok) "Shortcut removed." else "Failed to remove shortcut.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }

    // Bulk remove. Counts rather than names: a list of twenty titles is not something anyone
    // reads, and the number is the part that decides whether you meant it.
    if (confirmRemoveSelected) {
        val targets = shortcuts.filter { it.file.path in selectedPaths }
        OutlinedAlertDialog(
            onDismissRequest = { confirmRemoveSelected = false },
            title = { Text("Remove ${targets.size} shortcut${if (targets.size == 1) "" else "s"}?") },
            text = {
                Text(
                    if (targets.size == 1) "Remove \"${targets.first().name}\"?"
                    else "Remove these ${targets.size} shortcuts? The games themselves are left " +
                         "on disk — only the shortcuts go."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    var ok = 0
                    targets.forEach { if (vm.remove(it, context)) ok++ }
                    confirmRemoveSelected = false
                    selectedPaths = emptySet()
                    selectionMode = false
                    Toast.makeText(
                        context,
                        if (ok == targets.size) "Removed $ok shortcut${if (ok == 1) "" else "s"}."
                        else "Removed $ok of ${targets.size} — the rest could not be deleted.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) { Text("Remove", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveSelected = false }) { Text("Cancel") }
            },
        )
    }

    // Clone-to-container dialog
    cloneTarget?.let { s ->
        val containers = vm.containers()
        OutlinedAlertDialog(
            onDismissRequest = { cloneTarget = null },
            title = { Text("Select container") },
            text = {
                // Scroll so a long container list isn't clipped in landscape / on short screens.
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    containers.forEach { c ->
                        Text(
                            text = c.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val ok = s.cloneToContainer(c)
                                    cloneTarget = null
                                    Toast.makeText(
                                        context,
                                        if (ok) "Shortcut cloned." else "Failed to clone shortcut.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    if (ok) vm.refresh()
                                }
                                .padding(vertical = 12.dp),
                            color = OnSurface,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { cloneTarget = null }) { Text("Cancel") } },
        )
    }

    // "Copy to Drive C…" — the shared confirm→copy→repoint flow, fed by the ⋮ menu item and the
    // editor's Storage row. Owns its own dialogs/progress; clears the target and refreshes on finish.
    CopyToDriveCCoordinator(
        target = copyToDriveCTarget,
        onFinished = { copyToDriveCTarget = null; vm.refresh() },
    )

    // "Change executable…" — pick a different .exe/.lnk in the game's folder and repoint the shortcut
    // at it (shared Exec-rewrite with copy-to-C). Fed by the ⋮ item and the editor's Executable row.
    ChangeExecutableCoordinator(
        target = changeExeTarget,
        onFinished = { changeExeTarget = null; vm.refresh() },
    )

    // Save Restore: a backup .zip has been picked — choose the TARGET container to restore it into,
    // then hand off to GameSaveBackup.restore (which unzips into that container and remaps the user).
    restoreZipUri?.let { uri ->
        ContainerPickerDialog(
            gameName = restoreForName,
            containers = vm.containers(),
            onDismiss = { restoreZipUri = null },
            onSelected = { chosen ->
                restoreZipUri = null
                Toast.makeText(context, "Restoring saves into \"${chosen.name}\"…", Toast.LENGTH_SHORT).show()
                GameSaveBackup.restore(context, uri, chosen) { r ->
                    Toast.makeText(
                        context,
                        if (r.ok) "Restored ${r.filesWritten} files to \"${chosen.name}\""
                        else "Restore failed: ${r.error ?: "unknown error"}",
                        Toast.LENGTH_LONG,
                    ).show()
                    emuConflicts = r.emuConflicts
                }
            },
        )
    }

    EmuAccountConflictDialog(conflicts = emuConflicts) { applied, _ ->
        emuConflicts = emptyList()
        if (applied > 0) {
            Toast.makeText(
                context,
                "Emulator account switched to the backup's — relaunch the game",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Save Backup: choose the archive layout before backing up (mirrors the Containers backup menu's
    // GameHub / Winlator-native choice), then run the scoped backup into the per-game folder.
    backupFormatShortcut?.let { s ->
        OutlinedAlertDialog(
            onDismissRequest = { backupFormatShortcut = null },
            title = { Text(stringResource(R.string.save_backup_format_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.save_backup_format_prompt),
                        color = OnSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                backupFormatShortcut = null
                                runCustomBackup(s, GameSaveBackup.BackupLayout.WINLATOR)
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.save_backup_format_winlator), color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.save_backup_format_winlator_sub), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                backupFormatShortcut = null
                                runCustomBackup(s, GameSaveBackup.BackupLayout.GAMEHUB)
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.save_backup_format_gamehub), color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.save_backup_format_gamehub_sub), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { backupFormatShortcut = null }) { Text("Cancel") } },
        )
    }

    // "View logs" — straight to this game's logs, no trip through App Settings.
    //
    // Three outcomes, and it matters that they read differently: per-game folders off (nothing is
    // filed per game, so we cannot show "this game's" logs at all), on but nothing captured yet, or
    // the viewer. Silently opening an empty viewer for the first two would look like a bug.
    logsShortcut?.let { s ->
        val entry = remember(s.name) { LogInventory.forGame(context, s.name) }
        val perGameOff = remember { !LogLocation.isPerGameEnabled(context) }
        if (entry != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { logsShortcut = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LogViewerScreen(entry = entry, onClose = { logsShortcut = null })
                }
            }
        } else {
            OutlinedAlertDialog(
                onDismissRequest = { logsShortcut = null },
                title = { Text("No logs for ${s.name}") },
                text = {
                    Text(
                        if (perGameOff)
                            "Per-game log folders are turned off, so everything is written to one " +
                            "shared folder and logs can't be traced back to a single game. Turn them " +
                            "on in Settings › Logs, then play ${s.name} once."
                        else
                            "Nothing has been captured for ${s.name} yet. Logs are written while a " +
                            "game runs — play it once, then check back here."
                    )
                },
                confirmButton = { TextButton(onClick = { logsShortcut = null }) { Text("OK") } },
            )
        }
    }

    // Shortcut properties dialog
    propertiesShortcut?.let { s ->
        val playtimePrefs = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
        val playtimeKey = "${s.name}_playtime"
        val playCountKey = "${s.name}_play_count"
        val totalMs = playtimePrefs.getLong(playtimeKey, 0L)
        val playCount = playtimePrefs.getInt(playCountKey, 0)
        val seconds = (totalMs / 1000) % 60
        val minutes = (totalMs / (1000 * 60)) % 60
        val hours   = (totalMs / (1000 * 60 * 60)) % 24
        val days    = (totalMs / (1000 * 60 * 60 * 24))
        val formatted = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
        var didReset by remember { mutableStateOf(false) }
        OutlinedAlertDialog(
            onDismissRequest = { propertiesShortcut = null },
            title = { Text("Properties") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (didReset) "Number of times played: 0" else "Number of times played: $playCount")
                    Text(if (didReset) "Playtime: 0d 00h 00m 00s" else "Playtime: $formatted")
                    Button(
                        onClick = {
                            playtimePrefs.edit().remove(playtimeKey).remove(playCountKey).apply()
                            didReset = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Reset Properties") }
                }
            },
            confirmButton = { TextButton(onClick = { propertiesShortcut = null }) { Text("Close") } }
        )
    }

    // Scrape cover dialog
    val sc = scrapeTarget
    if (sc != null) {
        OutlinedAlertDialog(
            onDismissRequest = { scrapeTarget = null },
            title = { Text("Scrape cover for \"${sc.name}\"") },
            text = {
                if (scrapeLoading) {
                    Text("Searching SteamGridDB...", color = OnSurfaceVariant)
                } else if (scrapeCovers.isEmpty()) {
                    Text("No covers found.", color = OnSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        scrapeCovers.forEach { (bmp, url) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch(Dispatchers.IO) {
                                            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                                            conn.connectTimeout = 15000
                                            conn.readTimeout = 20000
                                            val full = BitmapFactory.decodeStream(conn.inputStream)
                                            conn.disconnect()
                                            if (full != null) {
                                                sc.saveCustomCoverArt(full)
                                                sc.icon = full
                                                val iconsDir = sc.container.getIconsDir(64)
                                                if (iconsDir != null) {
                                                    if (!iconsDir.exists()) iconsDir.mkdirs()
                                                    val iconName = kotlin.runCatching { sc.file.readLines().firstOrNull { it.startsWith("Icon=") }?.substringAfter("Icon=")?.trim() }.getOrNull() ?: sc.name
                                                    FileUtils.saveBitmapToFile(full, File(iconsDir, iconName + ".png"))
                                                }
                                            }
                                            withContext(Dispatchers.Main) {
                                                scrapeTarget = null
                                                vm.reloadShortcut(sc.file.path, full)
                                                Toast.makeText(context, "Cover saved.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 120.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { scrapeTarget = null }) { Text("Cancel") } },
        )
    }

    // Community configs dialog (Phase 1 — match + suggest, read-only)
    if (!communityDialogsGated) communityTarget?.let { s ->
        val dismiss = { communityTarget = null; communityResult = null }
        val communityDialogShape = RoundedCornerShape(28.dp)
        OutlinedAlertDialog(
            onDismissRequest = dismiss,
            // Drop the dialog surface a notch below the cards' surfaceContainer fill so the config
            // cards + their 1dp outline separate from the background the same way the game/container
            // cards do on the main screen (default surfaceContainerHigh washed the outline out).
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = communityDialogShape,
            // Outline the whole dialog box (AlertDialog has no border param) so it reads as a bordered
            // panel like the catalog browser, matched to the dialog's rounded shape.
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, communityDialogShape),
            title = { Text("Community configs") },
            text = {
                val result = communityResult
                val game = result?.match
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Phase 3 step 2 — local export/import for THIS shortcut. Share generates a config file
                // and opens the Share/Save sheet; Import picks a `.json` and applies it straight to `s`.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { vm.exportShortcutConfig(s) { exportResult = it } },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share config")
                    }
                    OutlinedButton(
                        onClick = { launchConfigImport(s) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
                // Phase 3 (online sharing) — UPLOAD this shortcut's effective config to OUR community repo
                // (ns=bannerlator). Disabled + spinner while in flight; if the user already shared one for
                // this game, [replaceUploadPrompt] surfaces a replace-confirm before the upload proceeds.
                OutlinedButton(
                    onClick = {
                        uploadingConfig = true
                        uploadStarted = false
                        vm.uploadShortcutConfig(
                            s,
                            onExisting = { existing, proceed, cancel ->
                                replaceUploadPrompt = Triple(existing, proceed, cancel)
                            },
                            onStart = { uploadStarted = true },
                            onResult = { ok, msg ->
                                uploadingConfig = false
                                uploadStarted = false
                                replaceUploadPrompt = null
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            },
                        )
                    },
                    enabled = !uploadingConfig,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uploadingConfig) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (uploadStarted) "Uploading…" else "Preparing…")
                    } else {
                        Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Upload to community")
                    }
                }
                // Manage the configs the user has shared (list / delete / edit description). Reinstall-
                // proof: the list hydrates from the durable manifest when SharedPreferences is empty.
                OutlinedButton(
                    onClick = openMyUploads,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("My uploads")
                }
                Divider(color = DividerColor)
                OutlinedTextField(
                    value = communitySearch,
                    onValueChange = { q ->
                        communitySearch = q
                        if (q.trim().length >= 2) vm.searchCommunityGames(q) { communitySearchResults = it }
                        else communitySearchResults = emptyList()
                    },
                    label = { Text("Search all games") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (communitySearch.trim().length >= 2) {
                    if (communitySearchResults.isEmpty()) {
                        Text("No games match \"$communitySearch\".", color = OnSurfaceVariant)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            communitySearchResults.forEach { cg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Manually correcting the game via search also sticks for
                                            // next time (issue #167), same as tapping a tie alternative.
                                            vm.rememberCommunityGame(s, cg)
                                            vm.selectCommunityGame(cg) { communityResult = it }
                                            communitySearch = ""
                                            communitySearchResults = emptyList()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = cg.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${cg.configCount}", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                    CommunityStoreBadge(isSteam = cg.isSteam)
                                }
                            }
                        }
                    }
                } else {
                when {
                    communityLoading -> Text("Matching \"${s.name}\"…", color = OnSurfaceVariant)
                    game == null -> Text("No auto-match for \"${s.name}\" — search above to pick one.", color = OnSurfaceVariant)
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Genuine tie (e.g. "Dragon Age" → Inquisition vs Veilguard): let the user
                            // pick the right game rather than silently trusting the top candidate. The
                            // choice is remembered per shortcut, so this only asks once. (issue #167)
                            val tieOptions = result?.alternatives.orEmpty()
                            if (tieOptions.size > 1) {
                                Text(
                                    text = "Which game is this?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                )
                                tieOptions.forEach { alt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                vm.rememberCommunityGame(s, alt)
                                                vm.selectCommunityGame(alt) { communityResult = it }
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = alt.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = OnSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        CommunityStoreBadge(isSteam = alt.isSteam)
                                    }
                                }
                                Divider(color = DividerColor)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = game.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                CommunityStoreBadge(isSteam = game.isSteam)
                            }
                            val devWord = if (game.devices.size == 1) "device" else "devices"
                            val cfgWord = if (game.configCount == 1) "config" else "configs"
                            Text(
                                text = "${game.configCount} $cfgWord · ${game.devices.size} $devWord",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                            Text(
                                text = "Your device: ${deviceHeaderLabel(DeviceIdentity.deviceModel(), result?.userHardwareLabel)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                            // "Matches my device" — filters the per-config list to configs whose
                            // device/soc match your hardware. Mirrors the catalog browser's chip; enabled
                            // only when we actually detected a SoC/GPU to compare against.
                            val uSoc = result?.userSoc
                            val uGpu = result?.userGpu
                            var matchesMine by rememberSaveable(game.identity) { mutableStateOf(false) }
                            FilterChip(
                                selected = matchesMine,
                                onClick = { matchesMine = !matchesMine },
                                label = { Text("Matches my device") },
                                enabled = uSoc != null || uGpu != null,
                            )
                            Divider(color = DividerColor)
                            // One card per uploaded config from the worker (already votes-desc). Offline /
                            // bucket miss → fall back to the per-device index rows so apply-by-device still
                            // works (no vote counts in that mode). Whole card taps → the chooser; the
                            // in-context shortcut `s` is carried so details can preview the diff.
                            // Also look under THIS shortcut's own folder (sanitized the SAME way the
                            // exporter keys uploads) so the user's OWN Bannerlator upload shows up even
                            // though it isn't in the canonical index yet.
                            val myFolder = s.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                            val cfg = rememberGameConfigs(vm, game, extraBannerlatorFolders = listOf(myFolder))
                            when {
                                cfg.loading -> Text("Loading configs…", color = OnSurfaceVariant)
                                cfg.entries.isNotEmpty() -> {
                                    val shown = if (!matchesMine) cfg.entries
                                        else cfg.entries.filter {
                                            GameMatcher.hardwareMatchesUser(uSoc, uGpu, listOf(it.second.device, it.second.soc))
                                        }
                                    if (shown.isEmpty()) {
                                        Text("No uploaded configs match your device.", color = OnSurfaceVariant)
                                    } else {
                                        shown.forEach { (folder, e) ->
                                            val isMatch = (uSoc != null || uGpu != null) &&
                                                GameMatcher.hardwareMatchesUser(uSoc, uGpu, listOf(e.device, e.soc))
                                            CommunityConfigEntryCard(entry = e, isMatch = isMatch) {
                                                configAction = CommunityPick.File(
                                                    game,
                                                    CommunityConfigRef(game, folder, e.filename, e.sha.ifBlank { null }, ns = if (e.appSource == "bannerlator") "bannerlator" else ""),
                                                    e,
                                                ) to s
                                            }
                                        }
                                    }
                                }
                                result?.rankedDevices.isNullOrEmpty() -> Text("No device configs listed.", color = OnSurfaceVariant)
                                else -> {
                                    Text(
                                        "Showing device configs (vote counts unavailable offline).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OnSurfaceVariant,
                                    )
                                    val hw = result?.userHardwareLabel?.lowercase()
                                    val devs = result?.rankedDevices.orEmpty().let { list ->
                                        if (!matchesMine) list
                                        else list.filter { GameMatcher.deviceMatchesUser(it, uSoc, uGpu) }
                                    }
                                    devs.forEach { d ->
                                        val isMatch = hw != null && (
                                            (d.soc.isNotBlank() && (hw.contains(d.soc.lowercase()) || d.soc.lowercase().contains(hw))) ||
                                            (d.gpu.isNotBlank() && (hw.contains(d.gpu.lowercase()) || d.gpu.lowercase().contains(hw)))
                                        )
                                        CommunityCard(onClick = { configAction = CommunityPick.Device(game, d) to s }) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = d.model.ifBlank { "Unknown device" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isMatch) MaterialTheme.colorScheme.primary else OnSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                val sub = listOf(d.gpu, d.soc).filter { it.isNotBlank() }.joinToString(" · ")
                                                if (sub.isNotEmpty()) {
                                                    Text(
                                                        text = sub,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = OnSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
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
                }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = dismiss) { Text("Close") } },
        )

        // Replace-confirm for an already-shared config (surfaced by uploadShortcutConfig's onExisting).
        // Replace calls proceed() to resume the upload; Cancel calls cancel() so the parked coroutine
        // unwinds cleanly, then clears the busy state.
        replaceUploadPrompt?.let { (_, proceed, cancel) ->
            val dismissReplace = {
                replaceUploadPrompt = null
                uploadingConfig = false
                uploadStarted = false
                cancel()
            }
            OutlinedAlertDialog(
                onDismissRequest = dismissReplace,
                title = { Text("Replace your shared config?") },
                text = {
                    Text(
                        "You already shared a config for \"${s.name}\". Replace it?",
                        color = OnSurface,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        replaceUploadPrompt = null
                        proceed()
                    }) { Text("Replace") }
                },
                dismissButton = {
                    TextButton(onClick = dismissReplace) { Text("Cancel") }
                },
            )
        }
    }

    // Phase 3 (online sharing) — MY UPLOADS manager. A summary header + expandable list of the user's OWN
    // shared configs (reinstall-proof: hydrates from the durable manifest when SharedPreferences is empty).
    // Expanding a row (single-expand via expandedUploadSha) reveals its inline description editor +
    // Save / Delete. The expanded row's description is (re)loaded from the worker by this LaunchedEffect.
    LaunchedEffect(expandedUploadSha, showMyUploads) {
        val sha = expandedUploadSha
        val row = if (showMyUploads && sha != null) myUploads?.firstOrNull { it.record.sha == sha } else null
        if (row != null) {
            uploadDescLoading = true
            uploadDescText = ""
            vm.loadMyUploadDescription(row) { uploadDescText = it; uploadDescLoading = false }
        }
    }
    if (showMyUploads) {
        val myUploadsShape = RoundedCornerShape(28.dp)
        val closeMyUploads = { showMyUploads = false; expandedUploadSha = null }
        OutlinedAlertDialog(
            onDismissRequest = closeMyUploads,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = myUploadsShape,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, myUploadsShape),
            title = { Text("My uploads") },
            text = {
                when (val rows = myUploads) {
                    null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading…", color = OnSurfaceVariant)
                    }
                    else -> if (rows.isEmpty()) {
                        Text("You haven't shared any configs yet.", color = OnSurfaceVariant)
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Summary header — aggregate across every uploaded config.
                            Text(
                                "Shared ${rows.size} config${if (rows.size == 1) "" else "s"} · ↓ ${rows.sumOf { it.downloads }} · ★ ${rows.sumOf { it.votes }}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFE0701C),
                            )
                            rows.forEach { row ->
                                val expanded = expandedUploadSha == row.record.sha
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        // Collapsed header — tapping anywhere on it toggles expand.
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                expandedUploadSha = if (expanded) null else row.record.sha
                                            },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    row.record.game,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OnSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                    .format(java.util.Date(row.record.date))
                                                val sub = listOf(row.record.device, row.record.soc, dateStr)
                                                    .filter { it.isNotBlank() }.joinToString(" · ") +
                                                    if (!row.stillOnline) " · offline" else ""
                                                Text(sub, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("★${row.votes}  ↓${row.downloads}", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                                            Icon(
                                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = if (expanded) "Collapse" else "Expand",
                                            )
                                        }
                                        if (expanded) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                if (row.stillOnline) "● Online" else "● Removed",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (row.stillOnline) Color(0xFF3BA55D) else MaterialTheme.colorScheme.error,
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = uploadDescText,
                                                onValueChange = { if (it.length <= 500) uploadDescText = it },
                                                label = { Text(if (uploadDescLoading) "Loading description…" else "Description") },
                                                enabled = !uploadDescLoading,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text("${uploadDescText.length}/500", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                            Spacer(Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                OutlinedButton(onClick = { deleteUploadRow = row }) {
                                                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Delete")
                                                }
                                                Spacer(Modifier.weight(1f))
                                                Button(
                                                    enabled = !uploadDescLoading,
                                                    onClick = {
                                                        val text = uploadDescText
                                                        vm.editMyUploadDescription(row, text) { ok ->
                                                            Toast.makeText(
                                                                context,
                                                                if (ok) "Description updated." else "Couldn't reach the server.",
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        }
                                                    },
                                                ) { Text("Save") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = closeMyUploads) { Text("Close") } },
        )
    }

    // Delete-confirm for one of the user's uploads.
    deleteUploadRow?.let { row ->
        OutlinedAlertDialog(
            onDismissRequest = { deleteUploadRow = null },
            title = { Text("Delete shared config?") },
            text = { Text("Delete your shared config for \"${row.record.game}\"?", color = OnSurface) },
            confirmButton = {
                TextButton(onClick = {
                    deleteUploadRow = null
                    vm.deleteMyUpload(row) { ok ->
                        if (ok) {
                            myUploads = myUploads?.filterNot { it.record.sha == row.record.sha }
                            Toast.makeText(context, "Deleted your shared config.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Couldn't reach the server.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteUploadRow = null }) { Text("Cancel") } },
        )
    }

    // Catalog browser (Part A) — full-catalog entry from the header button.
    if (showCommunityBrowser && !communityDialogsGated) {
        CommunityCatalogBrowser(
            vm = vm,
            onDismiss = { showCommunityBrowser = false },
            // No in-context shortcut from the browser (null) → chooser's "Apply to game…" runs the picker.
            onPick = { pick -> configAction = pick to null },
            // My account — the global entry point (Phase 2); the sheet hosts the "My uploads" button.
            onMyAccount = openMyAccount,
        )
    }

    // Phase 2 (optional accounts) — the My-account sheet. Its "My uploads" button dismisses this and opens
    // the existing My-uploads manager (the per-game dialog still opens My uploads directly, unchanged).
    if (showMyAccount) {
        MyAccountDialog(
            vm = vm,
            onDismiss = { showMyAccount = false },
            onOpenMyUploads = {
                showMyAccount = false
                openMyUploads()
            },
            onLoggedIn = {
                // Phase 4 (cross-device recovery): if the My-uploads sheet is already open behind the
                // account dialog, reload it so the just-restored entries appear immediately.
                if (showMyUploads) vm.loadMyUploads { myUploads = it }
            },
        )
    }

    // Config-row chooser — "Apply to game… | View details" before any apply happens. Gated only on an
    // open install sheet (it IS one of the layers communityDialogsGated hides beneath itself).
    if (!installSheetOpen) configAction?.let { (pick, ctxShortcut) ->
        val subtitle = when (pick) {
            is CommunityPick.File -> "Config from ${pick.entry.device.ifBlank { pick.entry.soc.ifBlank { "that device" } }}."
            is CommunityPick.Device -> "Config from ${pick.device.model.ifBlank { "that device" }}."
        }
        OutlinedAlertDialog(
            onDismissRequest = { configAction = null },
            title = { Text(pick.game.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            },
            confirmButton = {
                TextButton(onClick = {
                    configAction = null
                    startConfigApply(pick, ctxShortcut)
                }) { Text("Apply to game…") }
            },
            dismissButton = {
                TextButton(onClick = {
                    configAction = null
                    detailFor = pick to ctxShortcut
                }) { Text("View details") }
            },
        )
    }

    // Read-only Community Config detail page. Loads fetch+translate (+preview when a shortcut is in
    // context) via the VM, then renders provenance + "what it sets" + the pre-apply diff. Apply reuses
    // the same startConfigApply → applyResult → smart-install flow, so details never duplicates apply.
    if (!installSheetOpen) detailFor?.let { (pick, ctxShortcut) ->
        var detail by remember(pick, ctxShortcut) { mutableStateOf<CommunityConfigDetail?>(null) }
        var detailLoading by remember(pick, ctxShortcut) { mutableStateOf(true) }
        var detailFailed by remember(pick, ctxShortcut) { mutableStateOf(false) }
        LaunchedEffect(pick, ctxShortcut) {
            detailLoading = true
            detailFailed = false
            val onDetail: (CommunityConfigDetail?) -> Unit = { d ->
                detail = d
                detailLoading = false
                detailFailed = d == null
            }
            when (pick) {
                is CommunityPick.File -> vm.loadCommunityConfigDetail(pick.ref, ctxShortcut, onDetail)
                is CommunityPick.Device -> vm.loadCommunityConfigDetail(pick.game, pick.device, ctxShortcut, onDetail)
            }
        }
        // Provenance fallback device — the real device row for a device pick, or one synthesized from
        // the uploaded config's own device/soc so the detail page reads identically.
        val provDevice = when (pick) {
            is CommunityPick.File -> CanonicalDevice(pick.entry.device, "", pick.entry.soc)
            is CommunityPick.Device -> pick.device
        }
        CommunityConfigDetailDialog(
            game = pick.game,
            device = provDevice,
            detail = detail,
            loading = detailLoading,
            failed = detailFailed,
            vm = vm,
            onApply = {
                detailFor = null
                startConfigApply(pick, ctxShortcut)
            },
            onDismiss = { detailFor = null },
        )
    }

    // Apply-target picker — choose which of your shortcuts to apply a browser-selected config to.
    if (!communityDialogsGated) applyPicker?.let { pick ->
        val shortcutList = vm.currentShortcuts()
        val fromLabel = when (pick) {
            is CommunityPick.File -> pick.entry.device.ifBlank { pick.entry.soc.ifBlank { "a device" } }
            is CommunityPick.Device -> pick.device.model.ifBlank { "a device" }
        }
        OutlinedAlertDialog(
            onDismissRequest = { applyPicker = null },
            title = { Text("Apply to game…") },
            text = {
                if (shortcutList.isEmpty()) {
                    Text("You have no shortcuts yet.", color = OnSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            "Config from $fromLabel for \"${pick.game.name}\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        shortcutList.forEach { sc ->
                            Text(
                                text = sc.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chooseApplyTarget(sc, pick) }
                                    .padding(vertical = 12.dp),
                                color = OnSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { applyPicker = null }) { Text("Cancel") } },
        )
    }

    // Mismatch confirmation — target shortcut's game doesn't match the config's game.
    applyMismatch?.let { (sc, pick) ->
        OutlinedAlertDialog(
            onDismissRequest = { applyMismatch = null },
            title = { Text("Different game") },
            text = { Text("This config is for \"${pick.game.name}\" — apply to \"${sc.name}\" anyway?") },
            confirmButton = {
                TextButton(onClick = {
                    applyMismatch = null
                    runCommunityApply(sc, pick)
                }) { Text("Apply anyway") }
            },
            dismissButton = { TextButton(onClick = { applyMismatch = null }) { Text("Cancel") } },
        )
    }

    // Phase 3 step 2 — EXPORT hand-off. After a config artifact is generated, offer the two local
    // sinks: Share (ACTION_SEND via the app FileProvider) or Save to the public Downloads folder.
    exportResult?.let { res ->
        OutlinedAlertDialog(
            onDismissRequest = { exportResult = null },
            title = { Text("Share this game's config") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A config file for \"${res.game}\" is ready.", color = OnSurface)
                    Text(res.fileName, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { saveExportToDownloads(res) }) {
                        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save to Downloads")
                    }
                    TextButton(onClick = { shareExport(res) }) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { exportResult = null }) { Text("Cancel") } },
        )
    }

    // Phase 3 step 2 — IMPORT target picker. Reached only from the catalog browser (no in-context
    // shortcut): mirrors the apply-target picker — pick which shortcut the imported file applies to.
    importedConfigUri?.let { uri ->
        val shortcutList = vm.currentShortcuts()
        OutlinedAlertDialog(
            onDismissRequest = { importedConfigUri = null },
            title = { Text("Apply imported config to…") },
            text = {
                if (shortcutList.isEmpty()) {
                    Text("You have no shortcuts yet.", color = OnSurfaceVariant)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    ) {
                        shortcutList.forEach { sc ->
                            Text(
                                text = sc.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        importedConfigUri = null
                                        runImport(sc, uri)
                                    }
                                    .padding(vertical = 12.dp),
                                color = OnSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { importedConfigUri = null }) { Text("Cancel") } },
        )
    }

    // Applying spinner (blocking) while the config is fetched + merged.
    if (applyBusy) {
        OutlinedAlertDialog(
            onDismissRequest = {},
            title = { Text("Applying config") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Fetching and merging…", color = OnSurfaceVariant)
                }
            },
            confirmButton = {},
        )
    }

    // Result summary — what changed + install / Proton advisories. Hidden while a component-install
    // sheet is open (a ModalBottomSheet renders behind an AlertDialog's window); it reappears — with
    // any new checkmark — once the sheet closes, since applyResult / resolvedMissing persist.
    if (!communityDialogsGated) applyResult?.let { res ->
        // Downloadable catalog for the inline installer — one fetch per result, per missing type.
        // null value = still loading (row shows a spinner instead of a button).
        val installCm = remember(res) { ContentsManager(context) }
        var remoteByType by remember(res) {
            mutableStateOf<Map<ContentProfile.ContentType, List<ContentProfile>>?>(null)
        }
        LaunchedEffect(res) {
            if (res.missingComponents.isEmpty()) { remoteByType = emptyMap(); return@LaunchedEffect }
            val types = res.missingComponents.map { it.type }.toSet()
            remoteByType = withContext(Dispatchers.IO) {
                val json = Downloader.downloadString(ContentsManager.REMOTE_PROFILES)
                if (json != null) installCm.setRemoteProfiles(json) else installCm.syncContents()
                types.associateWith { t ->
                    (installCm.getProfiles(t) ?: emptyList()).filter { it.remoteUrl != null }
                }
            }
        }
        OutlinedAlertDialog(
            onDismissRequest = { applyResult = null },
            title = { Text(if (res.ok) "Config applied" else "Couldn't apply") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(res.message, color = OnSurface)
                    if (res.changed.isNotEmpty()) {
                        Divider(color = DividerColor)
                        Text("Changed", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                        res.changed.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                    }
                    // Missing components — smart inline installer: exact-match confirm, or a shortlist of
                    // the closest catalog versions, with "Browse all versions" as the full-menu fallback.
                    if (res.missingComponents.isNotEmpty()) {
                        Divider(color = DividerColor)
                        Text("Needs a component", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                        res.missingComponents.forEach { mc ->
                            SmartComponentInstallRow(
                                mc = mc,
                                done = mc.label in resolvedMissing,
                                candidates = remoteByType?.get(mc.type) ?: emptyList(),
                                catalogLoading = remoteByType == null,
                                cm = installCm,
                                onBrowseAll = { installSheetFor = mc },
                                onProfileInstalled = { applyAfterInstall(mc) },
                            )
                        }
                    }
                    // Missing GPU driver(s) — smart inline installer over all 5 adrenotools repos:
                    // every exact-version repo-variant as its own quick-install, then closest others,
                    // then the full driver browser. Adreno-only (the apply engine gates emission).
                    if (res.missingDrivers.isNotEmpty()) {
                        Divider(color = DividerColor)
                        Text("Needs a GPU driver", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                        res.missingDrivers.forEach { md ->
                            SmartDriverInstallRow(
                                md = md,
                                vm = vm,
                                done = ("driver:" + md.wanted) in resolvedMissing,
                                onBrowseAll = { driverSheetFor = md },
                                onApplied = { driverId ->
                                    val target = applyTarget
                                    if (target != null) {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                CommunityConfigApply.applyResolvedDriver(target, driverId)
                                            }
                                            if (ok) {
                                                if (("driver:" + md.wanted) !in resolvedMissing) {
                                                    resolvedMissing.add("driver:" + md.wanted)
                                                }
                                                vm.refresh()
                                                Toast.makeText(context, "Driver installed and applied to \"${target.name}\".", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Installed, but couldn't apply the driver.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (res.advisories.isNotEmpty()) {
                        Divider(color = DividerColor)
                        Text("Heads up", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                        res.advisories.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { applyResult = null }) { Text("Done") } },
        )
    }

    // Install a config's missing component via the app's normal single-type download sheet. When a
    // build gets installed we re-resolve it against what's now on disk and, if it resolves, surgically
    // write the version sub-field back to the target shortcut (same merge the apply engine uses).
    installSheetFor?.let { mc ->
        ContentDownloadSheet(
            contentType = mc.type,
            onDismiss = { installSheetFor = null },
            onContentChanged = { applyAfterInstall(mc) },
        )
    }

    // "Browse all drivers" fallback — the full adrenotools driver browser (5 source chips). When a
    // driver installs, surgically write its id back to the target shortcut and mark the row done.
    driverSheetFor?.let { md ->
        AdrenoDriverDownloadSheet(
            onDismiss = { driverSheetFor = null },
            onDriverInstalled = { driverId ->
                driverSheetFor = null
                val target = applyTarget
                if (target != null) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            CommunityConfigApply.applyResolvedDriver(target, driverId)
                        }
                        if (ok) {
                            if (("driver:" + md.wanted) !in resolvedMissing) {
                                resolvedMissing.add("driver:" + md.wanted)
                            }
                            vm.refresh()
                            Toast.makeText(context, "Driver applied to \"${target.name}\".", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )
    }

    // Compose shortcut settings dialog
    settingsShortcut?.let { s ->
        ShortcutSettingsDialogScreen(
            shortcut = s,
            onDismiss = { settingsShortcut = null; vm.refresh() },
            // "Move to Drive C" (Storage row, General tab): close the editor and hand this game to
            // the shared coordinator. Same destination as the ⋮ "Copy to Drive C…" item.
            onMoveToDriveC = { settingsShortcut = null; copyToDriveCTarget = s },
            // "Change executable…" (Executable row): close the editor and hand off to the coordinator.
            onChangeExe = { settingsShortcut = null; changeExeTarget = s },
        )
    }

    // Game Details editor (Edit Game): name + Steam link/search + genres/description/year/metacritic.
    if (showWrapperManagerXmb) WrapperManagerDialog(onDismiss = { showWrapperManagerXmb = false })
    gameDetailsShortcut?.let { s ->
        GameDetailsSheet(
            shortcut = s,
            onDismiss = { gameDetailsShortcut = null },
            onSaved = { vm.refresh() },
        )
    }

    // ── Steam launch-method popup (M3): SteamLite (real Steam / VAC) vs Goldberg (offline) ──────────
    eaUnsupportedFor?.let { s ->
        AlertDialog(
            onDismissRequest = { eaUnsupportedFor = null },
            title = { Text("Not supported: EA anti-cheat") },
            text = {
                Text(
                    "\"${s.name}\" ships EA Javelin anti-cheat, which needs a Windows kernel driver. " +
                        "It cannot run under Wine on any Android emulator, so WinHub won't start the EA setup for it."
                )
            },
            confirmButton = { TextButton(onClick = { eaUnsupportedFor = null }) { Text("OK") } },
        )
    }
    eaSetupFor?.let { s ->
        AlertDialog(
            onDismissRequest = { if (!eaSetupBusy) eaSetupFor = null },
            title = { Text("Set up EA Desktop") },
            text = {
                Text(
                    "\"${s.name}\" is an EA title: it launches through EA Desktop, which isn't installed in this " +
                        "container yet. WinHub will open one setup session (wine-mono first if the container " +
                        "lacks it) and run EA's installer — follow its prompts when it shows them. The session closes " +
                        "by itself when the installer finishes and the app comes back. Then launch the game again and " +
                        "sign in to EA when it asks. This happens once per container."
                )
            },
            confirmButton = {
                TextButton(enabled = !eaSetupBusy, onClick = {
                    eaSetupBusy = true
                    eaScope.launch {
                        // Resolve with the SAME derivation that decided to show this dialog
                        // (EaSupport.installDirOf): a legacy shortcut written before steamAppId was stamped
                        // (pre-2026-08 downloads), or a drive-letter path the strict resolver can't map, used
                        // to dead-end here with a misleading "install folder" toast (reported on NFS Heat, 3.0.7).
                        val installDir = withContext(Dispatchers.IO) {
                            runCatching { EaSupport.installDirOf(s) }.getOrNull()
                        }
                        val exe = withContext(Dispatchers.IO) {
                            val resolved = runCatching { WinePath.resolveAndroidPath(s.container, s.path)?.absolutePath }.getOrNull()
                            // runForShortcut locates the depot from the exe's steam_games/ segment, so prefer a
                            // path inside the resolved depot when the direct mapping lacks that segment.
                            resolved?.takeIf { InstallScriptExecutor.locateInstallDir(File(it)) != null }
                                ?: installDir?.let { File(it, s.path.replace('\\', '/').substringAfterLast('/')).absolutePath }
                                ?: resolved
                        }
                        val appId = withContext(Dispatchers.IO) {
                            runCatching { EaSupport.resolveSteamAppId(s, installDir) }.getOrDefault(0)
                        }
                        if (exe != null && appId > 0) {
                            withContext(Dispatchers.IO) {
                                try { InstallScriptExecutor.runForShortcut(context, s.container, appId, exe, true) }
                                catch (t: Throwable) { android.util.Log.w("ShortcutsScreen", "EA setup failed", t) }
                            }
                        } else {
                            android.util.Log.w("ShortcutsScreen", "EA setup: cannot start for '${s.name}' — exe=$exe appId=$appId path='${s.path}' container=${s.container.id}")
                            Toast.makeText(
                                context,
                                if (exe == null) "Couldn't locate the game's install folder (${s.path})"
                                else "Couldn't work out this game's Steam app id — re-add it from the Steam library",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        eaSetupBusy = false
                        eaSetupFor = null
                    }
                }) { Text(if (eaSetupBusy) "Starting…" else "Set up") }
            },
            dismissButton = { TextButton(enabled = !eaSetupBusy, onClick = { eaSetupFor = null }) { Text("Cancel") } },
        )
    }
    launchChoiceFor?.let { s ->
        val appId = steamAppIdOf(s)
        LaunchMethodSheet(
            shortcut = s,
            onDismiss = { launchChoiceFor = null },
            // Verify runs a full re-validate pass directly. "Check for updates" is check-THEN-offer: probe
            // first, then a confirm dialog lets the user choose to apply — it never auto-updates. Both
            // dismiss the sheet first (so no dialog is layered behind the ModalBottomSheet's window).
            onUpdateFiles = { launchChoiceFor = null; checkForUpdatesThenOffer(s) },
            onVerifyFiles = { launchChoiceFor = null; runSteamMaintenance(s, verify = true) },
            onLaunch = { mode, goldbergMode, remember, controllerPassthrough, vacLaunch ->
                // Persist the choice on the shortcut's [Extra Data] so a remembered pick skips the popup
                // next time (contract literals: launchMode ∈ RealSteam/Goldberg/Raw, launchModeRemembered="1").
                s.putExtra("launchMode", mode)
                s.putExtra("launchModeRemembered", if (remember) "1" else "0")
                // Per-game "Controller passthrough" (read only on RealSteam launches; inert otherwise).
                s.putExtra("controllerPassthrough", if (controllerPassthrough) "1" else "0")
                // Per-game "Requires secure (VAC) launch" override: "" = follow app-info detection, "1"/"0".
                s.putExtra("steamVacLaunch", vacLaunch)
                s.saveData()
                launchChoiceFor = null
                when (mode) {
                    "Goldberg" -> launchWithGoldberg(s, goldbergMode ?: GoldbergMode.REGULAR)
                    "Stream" -> streamXboxShortcut(activity, s)
                    "Raw" -> {
                        // Raw: run the game's .exe directly with no Steam layer (Epic/GOG/Custom, or a
                        // Steam game the user chose to run raw). The launchMode="Raw" extra is inert to the
                        // launch pipeline (only "RealSteam" stages the agent), so this is a plain launch.
                        launchShortcutNow(activity, s)
                    }
                    else -> {
                        // RealSteam (SteamLite): SteamLite package on demand, then the pre-flight dialog
                        // (session → network → cloud saves → update check) and only then the container. Update/
                        // verify remain the popup's manual buttons; the pre-flight only OFFERS an update.
                        launchWithSteamLite(s)
                    }
                }
            },
        )
    }

    // ── SteamLite pre-flight ("Getting Steam ready") — runs BEFORE XServerDisplayActivity ──────────
    preflightFor?.let { s ->
        val appId = steamAppIdOf(s)
        val installDir = remember(s) {
            runCatching { SteamDatabase.getInstance(context).getGame(appId)?.installDir }.getOrNull().orEmpty()
        }
        val savePrefs = remember { context.getSharedPreferences("save_manager_prefs", Context.MODE_PRIVATE) }
        SteamPreflightDialog(
            shortcut = s,
            request = SteamSessionManager.PreflightRequest(
                appId = appId,
                installDir = installDir,
                gameName = s.name,
                pullCloudSaves = savePrefs.getBoolean("auto_download_steam_on_launch", true),
            ),
            onLaunch = { preflightFor = null; launchShortcutNow(activity, s, preflightDone = true) },
            onDismiss = { preflightFor = null },
            onSignIn = {
                preflightFor = null
                context.startActivity(Intent(context, SteamLoginActivity::class.java))
            },
            onGoldberg = {
                preflightFor = null
                SteamPrefs.init(context)
                launchWithGoldberg(s, SteamPrefs.getGoldbergMode(appId).let { if (it == GoldbergMode.OFF) GoldbergMode.REGULAR else it })
            },
            onUpdate = { preflightFor = null; runSteamMaintenance(s, verify = false) },
        )
    }

    // Blocking progress dialog while the picked component downloads before launch (SteamLite / Goldberg).
    componentDownloadFor?.let {
        OutlinedAlertDialog(
            onDismissRequest = { /* keep up until the download finishes */ },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Downloading $componentDownloadLabel", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { componentDownloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(componentDownloadProgress.coerceIn(0f, 1f) * 100).toInt()}% — the game launches when this finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
    }

    // RealSteam manual maintenance: progress while a user-triggered Update or Verify pass runs.
    // Cancellable — cancelling aborts the pass and stays in the library (a run never launches the game).
    steamUpdateFor?.let {
        OutlinedAlertDialog(
            onDismissRequest = { /* modal until it finishes or is cancelled */ },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(steamUpdateTitle, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    if (steamUpdateProgress < 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { steamUpdateProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        // Prefix a live "N%" once real download progress starts; the indeterminate
                        // "checking/setup" phase (fraction < 0) shows just the phase label.
                        if (steamUpdateProgress >= 0f)
                            "${(steamUpdateProgress.coerceIn(0f, 1f) * 100).toInt()}% — $steamUpdateLabel"
                        else steamUpdateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { steamUpdateHandle?.cancel() }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            },
        )
    }

    // "Check for updates" outcome: a delta is (or might be) due — offer to apply it. Never auto-updates;
    // [Update now] / [Check online] runs the authoritative updateNow pass, [Later] / [Cancel] does nothing.
    steamUpdateOffer?.let { (s, status) ->
        val available = status.state == SteamGameUpdater.State.UPDATE_AVAILABLE
        OutlinedAlertDialog(
            onDismissRequest = { steamUpdateOffer = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    if (available) "Update available" else "Check online?",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    if (available) {
                        if (status.installedBuild > 0L && status.liveBuild > 0L)
                            "${s.name}: build ${status.installedBuild} → ${status.liveBuild}. Update now?"
                        else "A newer build of ${s.name} is available. Update now?"
                    } else {
                        "Couldn't check ${s.name} from cached data. Do an online check now " +
                            "(and update if it's behind)?"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { steamUpdateOffer = null; runSteamMaintenance(s, verify = false) }) {
                    Text(if (available) "Update now" else "Check online", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { steamUpdateOffer = null }) {
                    Text(if (available) "Later" else "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

// Small "WINHUB" source pill for configs shared through our own repo (app_source=bannerlator), so
// users can tell them apart from BannerHub-sourced configs. Subtle orange fill, same pill shape as
// [CommunityStoreBadge].
@Composable
private fun BannerlatorSourceBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE0701C))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "WINHUB",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

// Small Steam / Title provenance badge for the community-config sheet header.
@Composable
internal fun CommunityStoreBadge(isSteam: Boolean) {
    val bg = if (isSteam) MaterialTheme.colorScheme.primary else SurfaceVariantColor
    val fg = if (isSteam) MaterialTheme.colorScheme.onPrimary else OnSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (isSteam) "STEAM" else "TITLE",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

// Map a worker account error code to a human message. Reset reads "invalid" as a bad recovery key; every
// other flow reads it as bad credentials; unknown / "network" collapses to a connection message.
private fun accountErrorMessage(code: String, isReset: Boolean): String = when (code) {
    "invalid_username" -> "Usernames are 3–20 characters: letters, numbers, _ or -."
    "username_reserved" -> "That username is reserved — pick another."
    "weak_password" -> "Password must be at least 6 characters."
    "username_taken" -> "That username is taken."
    "rate_limited" -> "Too many attempts — please wait a bit and try again."
    "invalid" -> if (isReset) "That recovery key isn't right for this username." else "Wrong username or password."
    else -> "Couldn't reach the server. Check your connection and try again."
}

/** Phase 3 (optional accounts) — the worker's typed avatar-upload rejections, in plain language. */
private fun avatarErrorMessage(code: String): String = when (code) {
    "bad_type" -> "That image type isn't supported — pick a JPEG, PNG, or WebP."
    "bad_size" -> "That image is too large — pick a smaller one."
    "bad_image" -> "That file isn't a valid image."
    "not_signed_in" -> "Sign in first to set a profile picture."
    else -> "Couldn't reach the server. Check your connection and try again."
}

/**
 * Phase 3 (optional accounts) — decode [uri] downscaled (longest side ~512px via [ImageUtils]) and
 * re-encode to a JPEG ByteArray that fits the worker's 512KB cap, dropping quality until it does. Runs
 * off the main thread (bitmap work + IO); returns null on any read/decode failure so the caller degrades
 * to a toast rather than crashing.
 */
private fun compressAvatar(context: Context, uri: Uri): ByteArray? {
    return try {
        val bitmap = com.winhub.hernandez.core.ImageUtils.getBitmapFromUri(context, uri, 512) ?: return null
        val maxBytes = 512 * 1024
        var quality = 85
        var bytes: ByteArray
        do {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 35)
        if (bytes.size > maxBytes) null else bytes
    } catch (e: Exception) {
        null
    }
}

// Phase 2 (optional accounts) — the "My account" sheet. Accounts are OPTIONAL: they only let a user
// recover / attribute the community configs they share. Styled like the other community dialogs (outlined
// box, orange accent). Three states:
//  - LOGGED OUT: a Create / Login tab pair, plus a "Forgot password?" reset (username + recovery key +
//    new password). Passwords go only to the worker over HTTPS — never logged, never stored locally.
//  - AFTER CREATE: the one-time recovery key with a Copy button + an "I've saved it" confirm.
//  - LOGGED IN: the avatar (tap or "Change picture" → system image picker → ≤512KB JPEG → upload) + the
//    username, "Show my recovery key", "My uploads", and "Log out".
@Composable
internal fun MyAccountDialog(
    vm: ShortcutsViewModel,
    onDismiss: () -> Unit,
    onOpenMyUploads: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shape = RoundedCornerShape(28.dp)
    val orange = Color(0xFFE0701C)

    // Local mirror of the (non-Compose) AccountManager state; re-read after every action that changes it.
    var account by remember { mutableStateOf(AccountManager.current(context)) }
    // Non-null right after a successful create → show the one-time recovery key before anything else.
    var justCreated by remember { mutableStateOf<AccountManager.CreateData?>(null) }

    // Shared form state (logged-out). Passwords live ONLY in this transient field state, never persisted.
    var tab by remember { mutableStateOf(0) } // 0 = Create, 1 = Login
    var showReset by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var recoveryKeyInput by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Logged-in: reveal the saved recovery key on demand.
    var revealRecovery by remember { mutableStateOf(false) }

    // Phase 3 (optional accounts) — AVATAR. avatarBusy gates the upload spinner. The cache-bust now lives in
    // AccountManager's avatar_version (Phase 4): uploadAvatar bumps it, so account.displayAvatarUrl below
    // changes and every avatar surface (this dialog + the ☰ + the drawer + the browser 👤) refetches in
    // lockstep. The picker compresses to ≤512KB JPEG off-main, then uploads via AccountManager.
    val scope = rememberCoroutineScope()
    var avatarBusy by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        avatarBusy = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { compressAvatar(context, uri) }
            if (bytes == null) {
                avatarBusy = false
                Toast.makeText(context, "Couldn't read that image.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                AccountManager.uploadAvatar(context, bytes, "image/jpeg")
            }
            avatarBusy = false
            when (result) {
                is AccountManager.AccountResult.Success -> {
                    // uploadAvatar already bumped avatar_version → re-reading the account yields a fresh
                    // displayAvatarUrl, and refresh() propagates it to the ☰ / drawer / browser 👤 too.
                    account = AccountManager.current(context)
                    AccountUiBus.refresh(context)
                    Toast.makeText(context, "Profile picture updated.", Toast.LENGTH_SHORT).show()
                }
                is AccountManager.AccountResult.Error ->
                    Toast.makeText(context, avatarErrorMessage(result.code), Toast.LENGTH_LONG).show()
            }
        }
    }

    @Composable
    fun ErrorText() {
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    OutlinedAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = orange)
                Text("My account")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    // --- AFTER CREATE: one-time recovery key ---------------------------------------
                    justCreated != null -> {
                        val data = justCreated!!
                        Text(
                            "Account \"${data.username}\" created.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface,
                        )
                        Text("Your recovery key", style = MaterialTheme.typography.labelLarge, color = orange)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, orange),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                data.recoveryKey,
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                        Text(
                            "⚠️ Save this — it's the only way to reset your password if you forget it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(data.recoveryKey))
                                Toast.makeText(context, "Recovery key copied.", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy")
                            }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                justCreated = null
                                account = AccountManager.current(context)
                                AccountUiBus.refresh(context)
                            }) { Text("I've saved it") }
                        }
                    }

                    // --- LOGGED IN: profile + actions ---------------------------------------------
                    account != null -> {
                        val acc = account!!
                        // Versioned URL from AccountManager (Phase 4) — bumped on every picture change.
                        val displayAvatarUrl = acc.displayAvatarUrl
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                AccountAvatar(
                                    avatarUrl = displayAvatarUrl,
                                    size = 48.dp,
                                    fallbackTint = orange,
                                    modifier = Modifier.clickable(enabled = !avatarBusy) {
                                        avatarPicker.launch("image/*")
                                    },
                                )
                                if (avatarBusy) {
                                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    acc.username,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(
                                    onClick = { avatarPicker.launch("image/*") },
                                    enabled = !avatarBusy,
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                ) { Text("Change picture", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        Divider(color = DividerColor)
                        OutlinedButton(
                            onClick = { revealRecovery = !revealRecovery },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (revealRecovery) "Hide my recovery key" else "Show my recovery key") }
                        if (revealRecovery) {
                            val key = AccountManager.recoveryKey(context)
                            if (key != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, orange),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(key, style = MaterialTheme.typography.titleMedium, color = OnSurface, modifier = Modifier.weight(1f))
                                        IconButton(onClick = {
                                            clipboard.setText(AnnotatedString(key))
                                            Toast.makeText(context, "Recovery key copied.", Toast.LENGTH_SHORT).show()
                                        }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy recovery key", modifier = Modifier.size(18.dp)) }
                                    }
                                }
                            } else {
                                Text(
                                    "No recovery key is saved on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant,
                                )
                            }
                        }
                        OutlinedButton(onClick = onOpenMyUploads, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("My uploads")
                        }
                        OutlinedButton(
                            onClick = {
                                AccountManager.logout(context)
                                account = null
                                revealRecovery = false
                                username = ""; password = ""; error = null
                                AccountUiBus.refresh(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Log out") }
                    }

                    // --- LOGGED OUT: reset flow ---------------------------------------------------
                    showReset -> {
                        Text("Reset password", style = MaterialTheme.typography.labelLarge, color = orange)
                        Text(
                            "Enter your username, your recovery key, and a new password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; error = null },
                            label = { Text("Username") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = recoveryKeyInput,
                            onValueChange = { recoveryKeyInput = it; error = null },
                            label = { Text("Recovery key") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it; error = null },
                            label = { Text("New password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ErrorText()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showReset = false; error = null }, enabled = !busy) { Text("Back") }
                            Spacer(Modifier.weight(1f))
                            Button(
                                enabled = !busy && username.isNotBlank() && recoveryKeyInput.isNotBlank() && newPassword.isNotBlank(),
                                onClick = {
                                    busy = true; error = null
                                    vm.resetAccountPassword(username.trim(), recoveryKeyInput.trim(), newPassword) { result ->
                                        busy = false
                                        when (result) {
                                            is AccountManager.AccountResult.Success -> {
                                                account = AccountManager.current(context)
                                                AccountUiBus.refresh(context)
                                                showReset = false
                                                password = ""; newPassword = ""; recoveryKeyInput = ""
                                                Toast.makeText(context, "Password reset — you're signed in.", Toast.LENGTH_SHORT).show()
                                            }
                                            is AccountManager.AccountResult.Error ->
                                                error = accountErrorMessage(result.code, isReset = true)
                                        }
                                    }
                                },
                            ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Reset") }
                        }
                    }

                    // --- LOGGED OUT: create / login tabs ------------------------------------------
                    else -> {
                        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = orange) {
                            Tab(selected = tab == 0, onClick = { tab = 0; error = null }, text = { Text("Create") })
                            Tab(selected = tab == 1, onClick = { tab = 1; error = null }, text = { Text("Log in") })
                        }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; error = null },
                            label = { Text("Username") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (tab == 0) {
                            Text(
                                "This is just to recover your shared configs — use a throwaway password, not one you use elsewhere.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                        ErrorText()
                        Button(
                            enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                busy = true; error = null
                                if (tab == 0) {
                                    vm.createAccount(username.trim(), password) { result ->
                                        busy = false
                                        when (result) {
                                            is AccountManager.AccountResult.Success -> {
                                                justCreated = result.data
                                                password = ""
                                            }
                                            is AccountManager.AccountResult.Error ->
                                                error = accountErrorMessage(result.code, isReset = false)
                                        }
                                    }
                                } else {
                                    vm.loginAccount(username.trim(), password) { result ->
                                        busy = false
                                        when (result) {
                                            is AccountManager.AccountResult.Success -> {
                                                account = AccountManager.current(context)
                                                AccountUiBus.refresh(context)
                                                password = ""
                                                // Phase 4: the login already folded the account's uploads into
                                                // the local store — refresh My uploads if that sheet is open.
                                                onLoggedIn()
                                                Toast.makeText(context, "Signed in.", Toast.LENGTH_SHORT).show()
                                            }
                                            is AccountManager.AccountResult.Error ->
                                                error = accountErrorMessage(result.code, isReset = false)
                                        }
                                    }
                                }
                            },
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(if (tab == 0) "Create account" else "Log in")
                        }
                        TextButton(
                            onClick = { showReset = true; error = null },
                            enabled = !busy,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Forgot password? Reset with recovery key") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { if (!busy) onDismiss() }) { Text("Close") } },
    )
}

// One missing-component row on the "Config applied" screen — the SMART inline installer. Resolves the
// config's wanted version against the downloadable catalog and offers the shortest path:
//  - exact match  → "Install" → a small confirm → inline download+install → auto-apply → checkmark.
//  - no exact      → "Install" reveals the ~3 closest versions (press = install, no confirm) plus a
//                    "Browse all versions…" link that opens the full single-type download sheet.
// Download + install reuse the same downloadToCache / installContent path as the sheet; the actual
// version write-back (auto-apply) happens in the parent via [onProfileInstalled].
@Composable
private fun SmartComponentInstallRow(
    mc: CommunityConfigApply.MissingComponent,
    done: Boolean,
    candidates: List<ContentProfile>,
    catalogLoading: Boolean,
    cm: ContentsManager,
    onBrowseAll: () -> Unit,
    onProfileInstalled: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val installedBlue = Color(0xFF4FC3F7) // intentional: matches the sheet's installed/in-use status blue

    val shortlist = remember(candidates, mc.wanted) {
        CommunityConfigApply.rankVersions(mc.wanted, candidates)
    }

    var expanded by remember { mutableStateOf(false) }                       // shortlist revealed (no-exact case)
    var confirmProfile by remember { mutableStateOf<ContentProfile?>(null) } // exact-match confirm
    var busy by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }                     // false = downloading phase
    var progress by remember { mutableStateOf(0f) }

    fun install(profile: ContentProfile) {
        confirmProfile = null
        expanded = false
        busy = true
        installing = false
        progress = 0f
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                downloadToCache(context, profile) { frac -> activity?.runOnUiThread { progress = frac } }
            }
            if (uri == null) {
                busy = false
                Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            installing = true
            progress = 0f
            // installContent already marshals onProgress / onDone back to the UI thread.
            installContent(context, cm, uri, onProgress = { f, _ -> progress = maxOf(progress, f) }) { ok ->
                busy = false
                if (ok) onProfileInstalled()
                else Toast.makeText(context, "Install failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "• ${mc.label}",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            when {
                done -> Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Installed",
                    tint = installedBlue, modifier = Modifier.size(20.dp),
                )
                busy || catalogLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> {
                    val exact = shortlist.exact
                    TextButton(onClick = {
                        when {
                            exact != null -> confirmProfile = exact
                            shortlist.closest.isEmpty() -> onBrowseAll()
                            else -> expanded = !expanded
                        }
                    }) { Text("Install", color = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        // Progress line under the label while downloading / installing.
        if (busy) {
            val frac = progress.coerceIn(0f, 1f)
            Text(
                if (installing) "Installing…" else "Downloading ${(frac * 100).toInt()}%…",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp),
            )
            LinearProgressIndicator(
                progress = frac,
                modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 2.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Shortlist (no exact match) — the ~3 closest versions + "Browse all versions".
        if (!busy && !done && expanded && shortlist.exact == null) {
            Column(
                modifier = Modifier.padding(start = 10.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                shortlist.closest.forEach { p ->
                    Text(
                        "Install ${p.verName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { install(p) }
                            .padding(vertical = 6.dp),
                    )
                }
                Text(
                    "Browse all versions…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = false; onBrowseAll() }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }

    // Exact-match confirm dialog (stacks over the result dialog).
    confirmProfile?.let { p ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmProfile = null },
            title = { Text("Install ${p.verName}?") },
            text = { Text("Download and install ${mc.type} ${p.verName}, then apply it to this shortcut?") },
            confirmButton = { TextButton(onClick = { install(p) }) { Text("Install") } },
            dismissButton = { TextButton(onClick = { confirmProfile = null }) { Text("Cancel") } },
        )
    }
}

// One missing-GPU-driver row on the "Config applied" screen — the SMART inline adrenotools installer.
// Mirrors [SmartComponentInstallRow] but the catalog is the 5 remote Turnip repos (fetched on expand)
// and the pick axis is repo-source at a given mesa version:
//  - "Install" fetches+ranks, then reveals EVERY exact-version repo-variant as its own quick-install
//    (labelled "<source> · <displayName>" so identical versions are distinguishable), the ~3 closest
//    OTHER versions, and a "Browse all drivers…" link to the full driver browser.
//  - each quick-install → a small confirm → inline download+install → auto-apply → checkmark.
// Only reached on Adreno GPUs (the apply engine only emits MissingDriver there).
@Composable
private fun SmartDriverInstallRow(
    md: CommunityConfigApply.MissingDriver,
    vm: ShortcutsViewModel,
    done: Boolean,
    onBrowseAll: () -> Unit,
    onApplied: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedBlue = Color(0xFF4FC3F7) // matches the component row's installed/in-use status blue
    val repo = remember { RemoteDriverRepository(context) }

    val label = remember(md) {
        buildString {
            append("config wants ")
            append(md.wanted)
            md.current?.let { append("; you have ").append(it) }
        }
    }

    var loading by remember { mutableStateOf(false) }                         // fetching + ranking repos
    var shortlist by remember { mutableStateOf<CommunityConfigApply.DriverShortlist?>(null) }
    var expanded by remember { mutableStateOf(false) }                        // variants revealed
    var confirmEntry by remember { mutableStateOf<RemoteDriverEntry?>(null) } // per-variant confirm
    var busy by remember { mutableStateOf(false) }                           // download/install running
    var installing by remember { mutableStateOf(false) }                     // false = downloading phase
    var progress by remember { mutableStateOf(0) }                           // 0..100

    // Decide what to reveal once the shortlist is known: no options at all → open the full browser.
    fun reveal(sl: CommunityConfigApply.DriverShortlist) {
        if (sl.exactMatches.isEmpty() && sl.closest.isEmpty()) onBrowseAll() else expanded = true
    }

    fun onInstallClick() {
        val sl = shortlist
        if (sl != null) { reveal(sl); return }
        if (loading) return
        loading = true
        vm.fetchDriverShortlist(md.wanted) { fetched ->
            shortlist = fetched
            loading = false
            reveal(fetched)
        }
    }

    fun install(entry: RemoteDriverEntry) {
        confirmEntry = null
        expanded = false
        busy = true
        installing = false
        progress = 0
        scope.launch {
            repo.downloadEntry(entry) { pct -> progress = pct }.fold(
                onSuccess = { file ->
                    installing = true
                    val driverId = withContext(Dispatchers.IO) {
                        AdrenotoolsManager(context).installDriver(Uri.fromFile(file))
                    }
                    file.delete()
                    busy = false
                    if (driverId.isNotEmpty()) onApplied(driverId)
                    else Toast.makeText(context, "Install failed — invalid driver package", Toast.LENGTH_LONG).show()
                },
                onFailure = { t ->
                    busy = false
                    Toast.makeText(context, "Download failed: ${t.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "• $label",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            when {
                done -> Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Installed",
                    tint = installedBlue, modifier = Modifier.size(20.dp),
                )
                busy || loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else -> TextButton(onClick = { onInstallClick() }) {
                    Text("Install", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Progress line under the label while downloading / installing.
        if (busy) {
            val frac = (progress / 100f).coerceIn(0f, 1f)
            Text(
                if (installing) "Installing…" else "Downloading $progress%…",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp),
            )
            LinearProgressIndicator(
                progress = frac,
                modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 2.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Revealed options: exact-version repo-variants first (each its own quick-install), then the
        // closest OTHER versions, then the full browser.
        val sl = shortlist
        if (!busy && !done && expanded && sl != null) {
            Column(
                modifier = Modifier.padding(start = 10.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                sl.exactMatches.forEach { e ->
                    Text(
                        "Install ${e.source} · ${e.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmEntry = e }
                            .padding(vertical = 6.dp),
                    )
                }
                sl.closest.forEach { e ->
                    Text(
                        "Install ${e.source} · ${e.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmEntry = e }
                            .padding(vertical = 6.dp),
                    )
                }
                Text(
                    "Browse all drivers…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = false; onBrowseAll() }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }

    // Per-variant confirm dialog (stacks over the result dialog).
    confirmEntry?.let { e ->
        OutlinedAlertDialog(
            onDismissRequest = { confirmEntry = null },
            title = { Text("Quick install ${e.displayName}?") },
            text = { Text("Download and install this Turnip driver from ${e.source}, then apply it to this shortcut?") },
            confirmButton = { TextButton(onClick = { install(e) }) { Text("Install") } },
            dismissButton = { TextButton(onClick = { confirmEntry = null }) { Text("Cancel") } },
        )
    }
}

private enum class CatalogStoreFilter { ALL, STEAM, TITLE }
private enum class CatalogSort { CONFIGS, NAME, DEVICES }

// Full-catalog browser (Part A) — a catalog-first entry from the header. Lists every game in the
// community index with search / device + store filters / sort; a tapped game opens its per-device
// config list (user's-hardware first), and a device row starts the Phase 2 apply flow.
@Composable
internal fun CommunityCatalogBrowser(
    vm: ShortcutsViewModel,
    onDismiss: () -> Unit,
    onPick: (CommunityPick) -> Unit,
    onMyAccount: () -> Unit,
) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf<CommunityCatalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    // True while a manual index refresh is in flight (disables the button + shows a spinner).
    var refreshing by remember { mutableStateOf(false) }
    // Filters/search/selection survive rotation (rememberSaveable) so the user keeps their place;
    // the drilled-in game is keyed by its identity string (CanonicalGame isn't itself saveable).
    var query by rememberSaveable { mutableStateOf("") }
    var matchesMyDevice by rememberSaveable { mutableStateOf(false) }
    var storeFilter by rememberSaveable { mutableStateOf(CatalogStoreFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(CatalogSort.CONFIGS) }
    var selectedIdentity by rememberSaveable { mutableStateOf<String?>(null) }

    // Controller D-pad model — index-based with a SINGLE focus target on the panel (the same philosophy
    // Big Picture uses). gameFocus walks the visible game list; configFocus walks the drilled game's
    // published picks; drilledPicks is that in-render-order list the device panel hands up so A can apply
    // picks[configFocus]. Everything here is inert for touch/phone users: the handler consumes ONLY the
    // D-pad/A/B keys and returns false otherwise, so taps + the soft keyboard are unaffected.
    var gameFocus by remember { mutableStateOf(0) }
    var configFocus by remember { mutableStateOf(0) }
    var drilledPicks by remember { mutableStateOf<List<CommunityPick>>(emptyList()) }
    // Two-pane zone model (only meaningful at the top level, i.e. NOT drilled into a game):
    //   RIGHT (leftZone=false) = the game list — Up/Down walk it, A drills in, LEFT crosses to controls.
    //   LEFT  (leftZone=true)  = the controls column, walked top-to-bottom by [leftRow]:
    //        0 = Search field · 1 = store-filter group · 2 = Matches-my-device · 3 = sort group.
    //   For the two chip GROUPS, Left/Right cycles the focused chip; a further RIGHT past the last chip
    //   crosses to the list. On Search / Matches (single controls) RIGHT crosses to the list directly.
    //   [storeChipFocus]/[sortChipFocus] seed from the current selection so focus starts on it.
    var leftZone by remember { mutableStateOf(false) }
    var leftRow by remember { mutableStateOf(0) }
    var storeChipFocus by remember { mutableStateOf(storeFilter.ordinal) }
    var sortChipFocus by remember { mutableStateOf(sort.ordinal) }
    val browserFocus = remember { FocusRequester() }
    // A on the search control hands focus to the field + pops the soft keyboard; B pulls it back down
    // first (so the first B closes the keyboard, a second B closes/backs out of the browser).
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Reliable "keyboard is up" signal (WindowInsets.isImeVisible is unreliable in this dialog's window):
    // true while the search field holds focus. B closes the keyboard first, then closes the browser.
    var searchFieldFocused by remember { mutableStateOf(false) }
    val gameListState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.getCommunityCatalog { catalog = it; loading = false } }
    // Seed focus so the panel receives D-pad from the first frame (it's its own Dialog window, so the
    // Big Picture root handler never sees these keys).
    LaunchedEffect(Unit) { runCatching { browserFocus.requestFocus() } }
    // Reset the drilled cursor AND drop the previous game's picks the instant we drill in/out, so A can't
    // apply a stale pick in the frame before the new device panel republishes its list.
    LaunchedEffect(selectedIdentity) { configFocus = 0; drilledPicks = emptyList() }

    val userSoc = catalog?.userSoc
    val userGpu = catalog?.userGpu
    val games = catalog?.games ?: emptyList()
    val selectedGame = selectedIdentity?.let { id -> games.firstOrNull { it.identity == id } }

    val visible: List<CanonicalGame> = remember(games, query, matchesMyDevice, storeFilter, sort, userSoc, userGpu) {
        val base = if (query.trim().length >= 2) GameMatcher.search(query, games, limit = 200) else games
        val filtered = base.asSequence()
            .filter { g ->
                when (storeFilter) {
                    CatalogStoreFilter.ALL -> true
                    CatalogStoreFilter.STEAM -> g.isSteam
                    CatalogStoreFilter.TITLE -> !g.isSteam
                }
            }
            .filter { g ->
                !matchesMyDevice || g.devices.any { GameMatcher.deviceMatchesUser(it, userSoc, userGpu) }
            }
            .toList()
        // Preserve search relevance while a query is active; otherwise honour the chosen sort.
        if (query.trim().length >= 2) filtered
        else when (sort) {
            CatalogSort.CONFIGS -> filtered.sortedByDescending { it.configCount }
            CatalogSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            CatalogSort.DEVICES -> filtered.sortedByDescending { it.devices.size }
        }
    }

    // Keep the game-list cursor in range as filters change, and keep the highlighted game on-screen.
    LaunchedEffect(visible.size) { if (gameFocus > visible.lastIndex) gameFocus = visible.lastIndex.coerceAtLeast(0) }
    LaunchedEffect(gameFocus, selectedGame, visible.size) {
        if (selectedGame == null && visible.isNotEmpty())
            runCatching { gameListState.animateScrollToItem(gameFocus.coerceIn(0, visible.lastIndex)) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .focusRequester(browserFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val drilled = selectedGame != null
                    when (event.key) {
                        Key.DirectionUp -> {
                            when {
                                drilled -> { if (configFocus > 0) configFocus-- }
                                leftZone -> { if (leftRow > 0) leftRow-- }
                                else -> { if (gameFocus > 0) gameFocus-- }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            when {
                                drilled -> { if (configFocus < drilledPicks.lastIndex) configFocus++ }
                                leftZone -> { if (leftRow < 3) leftRow++ }
                                else -> { if (gameFocus < visible.lastIndex) gameFocus++ }
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            when {
                                drilled -> {} // config list is single-column; nothing to the left
                                !leftZone -> leftZone = true // cross from the game list to the controls
                                leftRow == 1 -> { if (storeChipFocus > 0) storeChipFocus-- }
                                leftRow == 3 -> { if (sortChipFocus > 0) sortChipFocus-- }
                                else -> {} // Search / Matches: already at the left edge
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            when {
                                drilled -> {}
                                !leftZone -> {} // already on the game list (rightmost)
                                // Chip groups: step right through the chips, then cross to the list.
                                leftRow == 1 -> { if (storeChipFocus < 2) storeChipFocus++ else leftZone = false }
                                leftRow == 3 -> { if (sortChipFocus < 2) sortChipFocus++ else leftZone = false }
                                else -> leftZone = false // Search / Matches: cross straight to the list
                            }
                            true
                        }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                            when {
                                drilled -> drilledPicks.getOrNull(configFocus)?.let(onPick)
                                leftZone -> when (leftRow) {
                                    0 -> { searchFocus.requestFocus(); keyboard?.show() } // Search: pop the keyboard
                                    1 -> storeFilter = when (storeChipFocus) {
                                        0 -> CatalogStoreFilter.ALL
                                        1 -> CatalogStoreFilter.STEAM
                                        else -> CatalogStoreFilter.TITLE
                                    }
                                    2 -> matchesMyDevice = !matchesMyDevice
                                    3 -> sort = when (sortChipFocus) {
                                        0 -> CatalogSort.CONFIGS
                                        1 -> CatalogSort.NAME
                                        else -> CatalogSort.DEVICES
                                    }
                                }
                                else -> visible.getOrNull(gameFocus)?.let { selectedIdentity = it.identity }
                            }
                            true
                        }
                        // B / Back: first close the keyboard if it's up; else up a level when drilled,
                        // otherwise close the browser.
                        Key.ButtonB, Key.Back -> {
                            when {
                                searchFieldFocused -> { keyboard?.hide(); browserFocus.requestFocus() }
                                drilled -> selectedIdentity = null
                                else -> onDismiss()
                            }
                            true
                        }
                        else -> false
                    }
                },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            // Outline the whole popup so the panel reads as a distinct bordered box (like the cards)
            // instead of bleeding edge-to-edge into the background behind the dialog.
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            // Game-list controls (device line + search + filter/sort chips + count). Extracted as a
            // local composable so portrait (stacked) and landscape (left column) share one definition.
            @Composable
            fun ListControls(modifier: Modifier) {
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Your device: ${deviceHeaderLabel(catalog?.deviceModel, catalog?.hardwareLabel)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    // Left-pane control 0 — Search (D-pad highlight-reachable; text entry stays touch/IME).
                    DpadHighlight(focused = leftZone && leftRow == 0) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search all games") },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                                .focusRequester(searchFocus)
                                .onFocusChanged { searchFieldFocused = it.isFocused }
                                // Guaranteed B handling while the field holds focus: close the keyboard
                                // and hand focus back to the browser for D-pad nav.
                                .onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back)) {
                                        keyboard?.hide(); browserFocus.requestFocus(); true
                                    } else false
                                },
                        )
                    }
                    // Left-pane control 1 — store filter group (Left/Right cycles the focused chip).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        DpadHighlight(focused = leftZone && leftRow == 1 && storeChipFocus == 0) {
                            FilterChip(selected = storeFilter == CatalogStoreFilter.ALL, onClick = { storeFilter = CatalogStoreFilter.ALL; leftZone = true; leftRow = 1; storeChipFocus = 0 }, label = { Text("All") })
                        }
                        DpadHighlight(focused = leftZone && leftRow == 1 && storeChipFocus == 1) {
                            FilterChip(selected = storeFilter == CatalogStoreFilter.STEAM, onClick = { storeFilter = CatalogStoreFilter.STEAM; leftZone = true; leftRow = 1; storeChipFocus = 1 }, label = { Text("Steam") })
                        }
                        DpadHighlight(focused = leftZone && leftRow == 1 && storeChipFocus == 2) {
                            FilterChip(selected = storeFilter == CatalogStoreFilter.TITLE, onClick = { storeFilter = CatalogStoreFilter.TITLE; leftZone = true; leftRow = 1; storeChipFocus = 2 }, label = { Text("Title") })
                        }
                    }
                    // Left-pane control 2 — Matches my device.
                    DpadHighlight(focused = leftZone && leftRow == 2) {
                        FilterChip(
                            selected = matchesMyDevice,
                            onClick = { matchesMyDevice = !matchesMyDevice; leftZone = true; leftRow = 2 },
                            label = { Text("Matches my device") },
                            enabled = userSoc != null || userGpu != null,
                        )
                    }
                    // Left-pane control 3 — sort group (Left/Right cycles the focused chip).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sort:", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                        DpadHighlight(focused = leftZone && leftRow == 3 && sortChipFocus == 0) {
                            FilterChip(selected = sort == CatalogSort.CONFIGS, onClick = { sort = CatalogSort.CONFIGS; leftZone = true; leftRow = 3; sortChipFocus = 0 }, label = { Text("Configs") })
                        }
                        DpadHighlight(focused = leftZone && leftRow == 3 && sortChipFocus == 1) {
                            FilterChip(selected = sort == CatalogSort.NAME, onClick = { sort = CatalogSort.NAME; leftZone = true; leftRow = 3; sortChipFocus = 1 }, label = { Text("Name") })
                        }
                        DpadHighlight(focused = leftZone && leftRow == 3 && sortChipFocus == 2) {
                            FilterChip(selected = sort == CatalogSort.DEVICES, onClick = { sort = CatalogSort.DEVICES; leftZone = true; leftRow = 3; sortChipFocus = 2 }, label = { Text("Devices") })
                        }
                    }
                    Text(
                        "${visible.size} game${if (visible.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                }
            }

            // The scrollable game list (or its empty state). `modifier` sizes the LazyColumn.
            @Composable
            fun GameList(modifier: Modifier) {
                if (visible.isEmpty()) {
                    Text(
                        "No games match your filters.",
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        state = gameListState,
                        modifier = modifier,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(visible, key = { _, g -> g.identity }) { index, g ->
                            // The D-pad highlight border wraps the row; touch users see nothing extra. Only
                            // shown while the RIGHT (list) zone is active, so focus reads as being in one
                            // place at a time. A tap also snaps the cursor back to the list.
                            DpadHighlight(focused = !leftZone && index == gameFocus) {
                                CommunityGameRow(game = g, onClick = { leftZone = false; gameFocus = index; selectedIdentity = g.identity })
                            }
                        }
                    }
                }
            }

            Column {
                // Title bar (with a back affordance when drilled into a game).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedGame != null) {
                        IconButton(onClick = { selectedIdentity = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        text = selectedGame?.name ?: "Community configs",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = if (selectedGame == null) 4.dp else 0.dp),
                    )
                    // Force-refresh the community index (bypass the 24h cache) so freshly-folded uploads
                    // appear now. Only at the top level; spinner + disabled while refreshing.
                    if (selectedGame == null) {
                        IconButton(
                            onClick = {
                                refreshing = true
                                vm.refreshCommunityIndex { fresh ->
                                    refreshing = false
                                    if (fresh != null) {
                                        catalog = fresh
                                        Toast.makeText(
                                            context,
                                            "Community index refreshed (${fresh.games.size} games)",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Couldn't refresh the community index.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            enabled = !refreshing,
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh community index")
                            }
                        }
                    }
                    // My account — the global entry point (Phase 2). Opens the SAME My-account sheet as the
                    // nav-drawer, and reads the SAME AccountUiBus state (Phase 4) so signed-in + a picture →
                    // this 👤 shows the user's avatar (versioned, in lockstep with the ☰ / drawer). The 🌐
                    // globe that OPENS this browser stays a globe — only this person icon becomes the avatar.
                    if (selectedGame == null) {
                        val myAvatarUrl = AccountUiBus.account?.displayAvatarUrl
                        IconButton(onClick = onMyAccount) {
                            if (myAvatarUrl != null) {
                                AccountAvatar(avatarUrl = myAvatarUrl, size = 28.dp)
                            } else {
                                Icon(Icons.Filled.AccountCircle, contentDescription = "My account")
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Divider(color = DividerColor)

                // Landscape (wide): controls/header become a left column so the scrollable list keeps
                // the full height. Portrait (narrow): the original single-column top-to-bottom stack.
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 600.dp
                    when {
                        loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        selectedGame != null -> CommunityDevicePanel(
                            vm = vm,
                            game = selectedGame,
                            userSoc = userSoc,
                            userGpu = userGpu,
                            hardwareLabel = catalog?.hardwareLabel,
                            deviceModel = catalog?.deviceModel,
                            onPick = onPick,
                            wide = wide,
                            focusedIndex = configFocus,
                            onPicks = { drilledPicks = it },
                        )
                        games.isEmpty() -> Text(
                            "No community configs available yet (offline, or the index hasn't been fetched).",
                            color = OnSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                        wide -> Row(modifier = Modifier.fillMaxSize()) {
                            ListControls(
                                modifier = Modifier
                                    .width(320.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(DividerColor))
                            GameList(modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        else -> Column(modifier = Modifier.fillMaxSize()) {
                            ListControls(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            Divider(color = DividerColor)
                            GameList(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

// Draws the app's controller-focus border (primary, rounded to the card shape) around a game/config
// card when it's the D-pad-highlighted item, otherwise a plain passthrough. Kept as a wrapper (rather
// than a `focused` param on each card) so the touch/phone path renders byte-identically when nothing
// is focused. Same visual idiom as Big Picture's RailButton/CoverCard focus border.
@Composable
internal fun DpadHighlight(focused: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.then(
            if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            else Modifier
        ),
    ) { content() }
}

// Shared thin outlined card for the community browser's game + config rows. Matches the app's
// FileManager/Containers card idiom (surfaceContainer fill, 1dp outline, rounded 10dp) but with a
// tighter vertical rhythm so the rows read as a compact list. The whole card is the tap target.
@Composable
internal fun CommunityCard(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

// What the chooser / detail / apply flow acts on when a config card is tapped. A [File] is a specific
// uploaded config from the worker (with votes/downloads, applied exactly); a [Device] is the offline
// fallback — a canonical device row whose best-matching file is resolved at apply time (no vote counts).
internal sealed class CommunityPick {
    abstract val game: CanonicalGame

    data class File(
        override val game: CanonicalGame,
        val ref: CommunityConfigRef,
        val entry: WorkerConfigEntry,
    ) : CommunityPick()

    data class Device(
        override val game: CanonicalGame,
        val device: CanonicalDevice,
    ) : CommunityPick()
}

// Async state of the per-game worker fetch. [entries] is the merged, deduped, votes-desc list across
// ALL the game's folders; each entry is paired with the folder (`/list` key) it came from so its
// per-entry [CommunityConfigRef.workerGame] is correct. [loading] gates the spinner.
private data class GameConfigsState(
    val loading: Boolean,
    val entries: List<Pair<String, WorkerConfigEntry>>,
)

// Fetch (once per [game]) the uploaded configs for a game from the worker and expose them as Compose
// state. Shared by the per-shortcut sheet and the catalog browser's device panel. [extraBannerlatorFolders]
// (the per-shortcut sheet passes the shortcut's own sanitized folder name) is queried in the bannerlator
// namespace ONLY, so a user's own upload is surfaced even before it lands in the canonical index.
@Composable
private fun rememberGameConfigs(
    vm: ShortcutsViewModel,
    game: CanonicalGame,
    extraBannerlatorFolders: List<String> = emptyList(),
): GameConfigsState {
    var loading by remember(game) { mutableStateOf(true) }
    var entries by remember(game) { mutableStateOf<List<Pair<String, WorkerConfigEntry>>>(emptyList()) }
    LaunchedEffect(game) {
        loading = true
        vm.fetchGameConfigs(game, extraBannerlatorFolders) { list ->
            entries = list
            loading = false
        }
    }
    return GameConfigsState(loading, entries)
}

// One card per uploaded config: primary = the device it was captured on (soc/filename fallback),
// sub-line = soc · date, and a `★ votes  ↓ downloads` stats row (same iconography as the detail page).
// The primary line is emphasized in the theme's primary colour when this config matches your hardware.
@Composable
internal fun CommunityConfigEntryCard(entry: WorkerConfigEntry, isMatch: Boolean, onClick: () -> Unit) {
    CommunityCard(onClick = onClick) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.device.ifBlank { entry.soc.ifBlank { entry.filename } },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMatch) MaterialTheme.colorScheme.primary else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOf(entry.soc, entry.date).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (entry.appSource == "bannerlator") BannerlatorSourceBadge()
            Text("★ ${entry.votes}", style = MaterialTheme.typography.labelMedium, color = OnSurface)
            Text("↓ ${entry.downloads}", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
        }
    }
}

// One game card in the catalog browser: name, Steam/Title badge, config + device counts.
@Composable
private fun CommunityGameRow(game: CanonicalGame, onClick: () -> Unit) {
    CommunityCard(onClick = onClick) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val cfgWord = if (game.configCount == 1) "config" else "configs"
            val devWord = if (game.devices.size == 1) "device" else "devices"
            Text(
                text = "${game.configCount} $cfgWord · ${game.devices.size} $devWord",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
            )
        }
        CommunityStoreBadge(isSteam = game.isSteam)
    }
}

// Compose the "Your device" header value: "<model> · <soc/gpu>" when both slots are known, otherwise
// the literal "Unresolved" fills whichever slot we couldn't detect — so the user can always tell which
// value was found and which wasn't. The both-missing case collapses to a single "Unresolved" (never
// "Unresolved · Unresolved"). Display-only; the caller decides which values to pass and this never
// affects device matching.
internal fun deviceHeaderLabel(model: String?, hardware: String?): String {
    val m = model?.takeIf { it.isNotBlank() }
    val hw = hardware?.takeIf { it.isNotBlank() }
    return when {
        m != null && hw != null -> "$m · $hw"
        m != null -> "$m · Unresolved"
        hw != null -> "Unresolved · $hw"
        else -> "Unresolved"
    }
}

// Per-uploaded-config list for a browser-selected game: one card per config the worker returns (votes
// desc), each whole-row-tappable → the Apply-to-game… | View-details chooser. A "Matches my device"
// toggle filters to configs matching your hardware. Offline / bucket miss falls back to the per-device
// index rows (apply-by-device, no vote counts). Header sits on top in portrait, in the left column in
// landscape; the two-column landscape layout is preserved.
@Composable
private fun CommunityDevicePanel(
    vm: ShortcutsViewModel,
    game: CanonicalGame,
    userSoc: String?,
    userGpu: String?,
    hardwareLabel: String?,
    deviceModel: String?,
    onPick: (CommunityPick) -> Unit,
    wide: Boolean,
    // Controller D-pad support (defaulted so the phone caller stays untouched): [focusedIndex] draws a
    // highlight border on the config card at that position, and [onPicks] publishes the CURRENT visible
    // config list — in render order — up to the browser so A can apply the highlighted one by index.
    focusedIndex: Int = -1,
    onPicks: (List<CommunityPick>) -> Unit = {},
) {
    val cfg = rememberGameConfigs(vm, game)
    val fallback = remember(game, userSoc, userGpu) { GameMatcher.rankDevices(game.devices, userSoc, userGpu) }
    var matchesMyDevice by rememberSaveable(game.identity) { mutableStateOf(false) }
    val hwEnabled = userSoc != null || userGpu != null

    val shownEntries = remember(cfg.entries, matchesMyDevice, userSoc, userGpu) {
        if (!matchesMyDevice) cfg.entries
        else cfg.entries.filter { GameMatcher.hardwareMatchesUser(userSoc, userGpu, listOf(it.second.device, it.second.soc)) }
    }
    // The offline per-device fallback list, filtered the same way the render below does — hoisted so the
    // published picks match exactly what ConfigList draws.
    val shownDevs = remember(fallback, matchesMyDevice, userSoc, userGpu) {
        if (!matchesMyDevice) fallback else fallback.filter { GameMatcher.deviceMatchesUser(it, userSoc, userGpu) }
    }
    // The flat, in-render-order list of picks the config list currently shows: worker entries when we have
    // them, else the device fallback. Published to the browser so its D-pad handler can apply picks[index].
    val orderedPicks = remember(shownEntries, shownDevs, cfg.entries, game) {
        if (cfg.entries.isNotEmpty()) shownEntries.map { (folder, e) ->
            CommunityPick.File(
                game,
                CommunityConfigRef(game, folder, e.filename, e.sha.ifBlank { null }, ns = if (e.appSource == "bannerlator") "bannerlator" else ""),
                e,
            )
        } else shownDevs.map { CommunityPick.Device(game, it) }
    }
    LaunchedEffect(orderedPicks) { onPicks(orderedPicks) }

    // Header (counts + store badge + your-device + the "Matches my device" toggle). Sits on top in
    // portrait, in the left column in landscape.
    @Composable
    fun Header() {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cfgWord = if (game.configCount == 1) "config" else "configs"
            val devWord = if (game.devices.size == 1) "device" else "devices"
            Text(
                "${game.configCount} $cfgWord · ${game.devices.size} $devWord",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            CommunityStoreBadge(isSteam = game.isSteam)
        }
        Text(
            "Your device: ${deviceHeaderLabel(deviceModel, hardwareLabel)}",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
        )
        FilterChip(
            selected = matchesMyDevice,
            onClick = { matchesMyDevice = !matchesMyDevice },
            label = { Text("Matches my device") },
            enabled = hwEnabled,
        )
    }

    // The config cards (whole-row tap → chooser). `modifier` provides the scroll container.
    @Composable
    fun ConfigList(modifier: Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                cfg.loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Loading configs…", color = OnSurfaceVariant)
                    }
                }
                cfg.entries.isNotEmpty() -> {
                    if (shownEntries.isEmpty()) {
                        Text("No uploaded configs match your device.", color = OnSurfaceVariant)
                    } else {
                        shownEntries.forEachIndexed { idx, (folder, e) ->
                            val isMatch = hwEnabled &&
                                GameMatcher.hardwareMatchesUser(userSoc, userGpu, listOf(e.device, e.soc))
                            DpadHighlight(focused = idx == focusedIndex) {
                                CommunityConfigEntryCard(entry = e, isMatch = isMatch) {
                                    onPick(
                                        CommunityPick.File(
                                            game,
                                            CommunityConfigRef(game, folder, e.filename, e.sha.ifBlank { null }, ns = if (e.appSource == "bannerlator") "bannerlator" else ""),
                                            e,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                fallback.isEmpty() -> Text("No configs listed.", color = OnSurfaceVariant)
                else -> {
                    // Offline fallback: per-device index rows (best-matching file resolved at apply).
                    Text(
                        "Showing device configs (vote counts unavailable offline).",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                    val hw = hardwareLabel?.lowercase()
                    shownDevs.forEachIndexed { idx, d ->
                        val isMatch = hw != null && (
                            (d.soc.isNotBlank() && (hw.contains(d.soc.lowercase()) || d.soc.lowercase().contains(hw))) ||
                            (d.gpu.isNotBlank() && (hw.contains(d.gpu.lowercase()) || d.gpu.lowercase().contains(hw)))
                        )
                        DpadHighlight(focused = idx == focusedIndex) {
                            CommunityCard(onClick = { onPick(CommunityPick.Device(game, d)) }) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = d.model.ifBlank { "Unknown device" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isMatch) MaterialTheme.colorScheme.primary else OnSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val sub = listOf(d.gpu, d.soc).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (sub.isNotEmpty()) {
                                        Text(sub, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (wide) {
        // Landscape: header pinned in a left column, config list scrolls on the right at full height.
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { Header() }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(DividerColor))
            ConfigList(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    } else {
        // Portrait: the original single scrolling column (header, divider, then the config rows).
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header()
            Divider(color = DividerColor)
            ConfigList(modifier = Modifier.fillMaxWidth())
        }
    }
}

// Human display name for a config's meta.app_source — the actual project that produced it. BannerHub
// and BannerHub Lite are distinct apps writing "bannerhub" / "bannerhub_lite"; ours would be "bannerlator".
internal fun communitySourceLabel(appSource: String?): String = when (appSource?.lowercase()?.trim()) {
    "bannerhub" -> "BannerHub"
    "bannerhub_lite" -> "BannerHub Lite"
    "bannerlator" -> "WinHub"
    null, "" -> "BannerHub"
    else -> appSource.split('_', ' ').filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

// Turn a translated config into "what it sets" lines in OUR component terms (the same fields the apply
// engine consumes). Only present fields are listed; Proton/wineVersion is advisory (container-only) so
// it is surfaced separately, not here.
internal fun configSummaryLines(config: ShortcutConfig): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    config.dxwrapperConfig["version"]?.takeIf { it.isNotBlank() }?.let { out.add("DXVK" to it) }
    config.dxwrapperConfig["vkd3dVersion"]?.takeIf { it.isNotBlank() }?.let { out.add("VKD3D" to it) }
    config.dxwrapperConfig["async"]?.let { out.add("DXVK async" to if (it == "1") "on" else "off") }
    config.dxwrapperConfig["anisotropy"]?.takeIf { it.isNotBlank() && it != "0" }?.let { out.add("Anisotropic filtering" to "${it}x") }
    config.dxwrapperConfig["lodBias"]?.takeIf { it.isNotBlank() && it != "0" }?.let { out.add("Texture sharpness" to it) }
    config.graphicsDriverConfig["version"]?.takeIf { it.isNotBlank() }?.let { out.add("Turnip driver" to it) }
    config.scalars["dxwrapper"]?.takeIf { it.isNotBlank() }?.let { out.add("DX wrapper" to it) }
    config.scalars["emulator"]?.let { emu ->
        val fex = config.scalars["fexcoreVersion"]
        out.add("x86 translator" to if (emu == "fexcore" && !fex.isNullOrBlank()) "FEXCore $fex" else emu)
    }
    config.scalars["audioDriver"]?.takeIf { it.isNotBlank() }?.let { out.add("Audio driver" to it) }
    config.scalars["inputType"]?.let { out.add("XInput" to if (((it.toIntOrNull() ?: 0) and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0) "on" else "off") }
    config.scalars["screenSize"]?.takeIf { it.isNotBlank() }?.let { out.add("Resolution" to it) }
    config.scalars["renderer"]?.takeIf { it.isNotBlank() }?.let { out.add("Renderer" to it) }
    config.scalars["execArgs"]?.takeIf { it.isNotBlank() }?.let { out.add("Launch args" to it) }
    // Scrub credentials/identity out before displaying — a config uploaded before the export-side scrub
    // existed can still carry a WN_STEAM_TOKEN/USERNAME/STEAMID, and the details view must not show it.
    config.scalars["envVars"]?.let { EnvVarScrub.scrub(it) }?.takeIf { it.isNotBlank() }?.let { out.add("Env vars" to it) }
    return out
}

// Read-only Community Config detail page. Renders only data we already fetch: provenance ([detail.meta]),
// the config in our own component terms ([configSummaryLines]), and (when a target shortcut was in
// context) the non-mutating pre-apply diff ([detail.preview]). Apply is delegated to the caller, which
// hands it to the shared apply → applyResult → smart-install flow — this page never applies/installs itself.
@Composable
private fun CommunityConfigDetailDialog(
    game: CanonicalGame,
    device: CanonicalDevice,
    detail: CommunityConfigDetail?,
    loading: Boolean,
    failed: Boolean,
    vm: ShortcutsViewModel,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Live social state, seeded from [detail] (re-seeds when the async load lands). Vote dedup is local
    // per-sha in a `banner_config_votes` prefs file, mirroring BannerHub's `bh_config_votes`; the worker
    // also enforces one vote / IP / 24h.
    val votePrefs = remember { context.getSharedPreferences("banner_config_votes", Context.MODE_PRIVATE) }
    var votes by remember(detail) { mutableStateOf(detail?.votes ?: 0) }
    var comments by remember(detail) { mutableStateOf(detail?.comments ?: emptyList()) }
    var voted by remember(detail) {
        mutableStateOf(detail?.sha?.let { votePrefs.getBoolean(it, false) } ?: false)
    }
    var voting by remember(detail) { mutableStateOf(false) }
    var commentText by remember(detail) { mutableStateOf("") }
    var commenting by remember(detail) { mutableStateOf(false) }

    // The live social block: ★ votes · ↓ downloads, uploader description, an Upvote button (local +
    // worker dedup), the comment thread, and a compact add-comment field. Only rendered when a worker
    // /list entry matched this file ([workerGame] != null) — otherwise there's no social data to show.
    @Composable
    fun Social(d: CommunityConfigDetail) {
        Divider(color = DividerColor)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("★ $votes", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
            Text("↓ ${d.downloads}", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
        }
        if (d.description.isNotBlank()) {
            Text(d.description, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        }
        d.sha?.let { sha ->
            OutlinedButton(
                onClick = {
                    val g = d.workerGame ?: return@OutlinedButton
                    if (voted || voting) return@OutlinedButton
                    voting = true
                    vm.voteConfig(sha, g, d.fileName) { newVotes ->
                        voting = false
                        if (newVotes != null) {
                            votes = newVotes
                            voted = true
                            votePrefs.edit().putBoolean(sha, true).apply()
                        } else {
                            Toast.makeText(context, "Couldn't record your vote.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !voted && !voting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                if (voting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (voted) "Voted ✓" else "Upvote")
                }
            }
        }

        Text("Comments", style = MaterialTheme.typography.labelLarge, color = OnSurface)
        if (comments.isEmpty()) {
            Text("No comments yet.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
        } else {
            comments.forEach { c ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    val head = listOf(c.device, c.date).filter { it.isNotBlank() }.joinToString(" · ")
                    if (head.isNotEmpty()) {
                        Text(head, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                    Text(c.text, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
            }
        }
        // Add a comment (worker caps text at 500 chars).
        val workerGame = d.workerGame
        if (workerGame != null) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { if (it.length <= 500) commentText = it },
                label = { Text("Add a comment") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        val text = commentText.trim()
                        if (text.isEmpty() || commenting) return@TextButton
                        commenting = true
                        val dev = Build.MANUFACTURER + "_" + Build.MODEL
                        vm.addConfigComment(workerGame, d.fileName, text, dev) { refreshed ->
                            commenting = false
                            if (refreshed != null) {
                                comments = refreshed
                                commentText = ""
                            } else {
                                Toast.makeText(context, "Couldn't post your comment.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = commentText.isNotBlank() && !commenting,
                ) { Text(if (commenting) "Sending…" else "Send") }
            }
        }
    }

    // Provenance — game · device · soc · uploaded date · BannerHub source badge. Prefers the config's
    // own meta, falling back to the catalog device row when a field is blank.
    @Composable
    fun Provenance(modifier: Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                game.name,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = detail?.meta
            val dev = meta?.device ?: device.model.ifBlank { null }
            val soc = meta?.soc ?: device.soc.ifBlank { null }
            val hw = listOfNotNull(dev, device.gpu.ifBlank { null }, soc).distinct().joinToString(" · ")
            if (hw.isNotEmpty()) {
                Text(hw, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            meta?.uploadedDate?.let {
                Text("Uploaded $it", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunityStoreBadge(isSteam = game.isSteam)
                if (meta != null) {
                    // Name the actual source project (meta.app_source distinguishes BannerHub vs
                    // BannerHub Lite vs a future Bannerlator upload), with the version appended if present.
                    val label = "From ${communitySourceLabel(meta.appSource)}" + (meta.bhVersion?.let { " $it" } ?: "")
                    Surface(
                        color = SurfaceVariantColor,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            // Uploader attribution (Phase 2 label + Phase 3 avatar). A signed-in upload carries
            // meta.uploader → "by <username>" plus its avatar when set; an anonymous config has none →
            // "Anonymous user" with the person-icon placeholder (AccountAvatar's null fallback).
            if (meta != null) {
                val uploaderLabel = meta.uploaderName?.let { "by $it" } ?: "Anonymous user"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccountAvatar(
                        avatarUrl = meta.uploaderAvatarUrl,
                        size = 16.dp,
                        fallbackTint = OnSurfaceVariant,
                    )
                    Text(uploaderLabel, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                }
            }
        }
    }

    // "What this config sets" + (when previewed) the pre-apply diff against the in-context shortcut.
    @Composable
    fun Body(modifier: Modifier) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Loading config…", color = OnSurfaceVariant)
                    }
                }
                failed || detail == null -> {
                    Text(
                        "Couldn't fetch this config (offline, or no matching file in the repo).",
                        color = OnSurfaceVariant,
                    )
                }
                else -> {
                    Text("What this config sets", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                    val lines = configSummaryLines(detail.config)
                    if (lines.isEmpty()) {
                        Text("Nothing this app can set.", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    } else {
                        lines.forEach { (label, value) ->
                            Text(
                                "• $label: $value",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                    detail.config.advisories["wineVersion"]?.let { proton ->
                        Text(
                            "• Proton (container-only): $proton",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }

                    // Pre-apply diff — only when a target shortcut was in context.
                    detail.preview?.let { pre ->
                        Divider(color = DividerColor)
                        Text("Changes to \"${game.name}\"", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                        Text(pre.message, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        if (pre.changed.isNotEmpty()) {
                            Text("Would change", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                            pre.changed.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                        }
                        if (pre.missingComponents.isNotEmpty()) {
                            Text("Needs a component", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                            pre.missingComponents.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                        }
                        if (pre.missingDrivers.isNotEmpty()) {
                            Text("Needs a GPU driver", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                            pre.missingDrivers.forEach {
                                val had = it.current?.let { c -> " (you have $c)" } ?: ""
                                Text("• Turnip ${it.wanted}$had", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                        }
                        if (pre.advisories.isNotEmpty()) {
                            Text("Heads up", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                            pre.advisories.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant) }
                        }
                    }

                    // Live social layer (votes / downloads / description / comments) — only when a
                    // worker /list entry matched this file; otherwise there's nothing to show.
                    if (detail.workerGame != null) Social(detail)
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            // Outline the whole popup so the panel reads as a distinct bordered box (like the cards)
            // instead of bleeding edge-to-edge into the background behind the dialog.
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Config details",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Divider(color = DividerColor)

                // Landscape (wide): provenance pinned left, "what it sets" + diff scroll on the right.
                // Portrait (narrow): a single top-to-bottom scroll of both.
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (maxWidth >= 600.dp) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Provenance(
                                modifier = Modifier
                                    .width(320.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            )
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(DividerColor))
                            Body(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Provenance(modifier = Modifier.fillMaxWidth())
                            Divider(color = DividerColor)
                            Body(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                Divider(color = DividerColor)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    TextButton(onClick = onApply, enabled = detail != null) { Text("Apply") }
                }
            }
        }
    }
}

// Game-list card (poster cover + primary chips + muted secondary line, issue #19). A tall
// ─────────────────────────────────────────────────────────────────────────────
// Steam Save Manager entry point (Games-tab ⋮ menu). A shortcut is "Steam-origin" when it was
// tagged at creation (storeSource=steam) or, for pre-tagging shortcuts, when its exec path lives
// under the steam_games install root. The linked appId reuses the existing `steamAppId` extra.
// `internal` (not `private`) so the couch UI (BigPictureScreen) shares the exact same Steam-origin gate
// and appId reader as the phone UI — the launch-method popup fires on the same set of games on both.
internal fun isSteamOriginShortcut(shortcut: Shortcut): Boolean {
    if (shortcut.getExtra("storeSource") == "steam") return true
    return shortcut.path.contains("steam_games", ignoreCase = true)
}

internal fun steamAppIdOf(shortcut: Shortcut): Int =
    shortcut.getExtra("steamAppId", "").toIntOrNull() ?: 0

// A shortcut is "custom" (exe/folder import) when it is NOT a genuine Steam-library game. Steam games
// get the "Cloud Saves" item; custom games get the local-only "Back up / Restore saves" items.
internal fun isCustomShortcut(shortcut: Shortcut): Boolean = !isSteamOriginShortcut(shortcut)

internal fun launchSaveManager(context: Context, focusAppId: Int) {
    context.startActivity(
        Intent(context, SteamSaveManagerActivity::class.java)
            .putExtra(SteamSaveManagerActivity.EXTRA_FOCUS_APP_ID, focusAppId),
    )
}

// 3:4 cover on the left, name + container · resolution subtitle in the middle. Components are
// split by how often you check them: renderer, DXVK and frame-gen are bright chips; driver,
// VKD3D and x86 backend sit on a calm muted line with a colour dot each. "Calm but complete."
@Composable
private fun ShortcutItemLayoutL(
    shortcut: Shortcut,
    selectionMode: Boolean,
    selected: Boolean,
    onRun: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
    onClone: () -> Unit,
    onCopyToDriveC: () -> Unit,
    onChangeExe: () -> Unit,
    onAddToHome: () -> Unit,
    onExport: () -> Unit,
    onProperties: () -> Unit,
    onScrapeCover: () -> Unit,
    onCommunityConfigs: () -> Unit,
    onGameDetails: () -> Unit,
    onViewLogs: () -> Unit,
    onCloudSaves: (() -> Unit)? = null,
    onBackupSaves: (() -> Unit)? = null,
    onRestoreSaves: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    // Resolved component metadata (shortcut override → container default). Shared with the launch
    // overlay via buildLaunchSpec() so the card and the launch screen can never drift.
    val spec = buildLaunchSpec(shortcut, context)
    val subtitle = spec.meta

    // Floating card to match the Containers list (rounded surfaceVariant panel + outline
    // border + side margins) instead of a flat edge-to-edge row.
    Card(
        onClick = onRun,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) DangerRed else MaterialTheme.colorScheme.outline,
        ),
    ) {
      // Full-bleed Steam wallpaper behind the row. The Amethyst surfaceVariant card stays underneath
      // as the fallback for custom imports (no appId) and while the image is still loading.
      Box {
        val wallAppId = remember(shortcut) { steamAppIdOf(shortcut) }
        if (wallAppId > 0) {
            RowWallpaper(
                appId = wallAppId,
                // Clip explicitly: the backdrop is a full rectangle and must not spill past the
                // card's 12dp corners regardless of how the Card clips its own content.
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.55f),
                            )
                        )
                    ),
            )
        }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // Checkbox replaces nothing — it is inserted ahead of the cover, so the row keeps its
        // shape and the cover does not jump when selection mode turns on.
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp).size(22.dp),
            )
        }
        // 3:4 poster cover (same as layout A); fall back to a glyph tile.
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceVariantColor),
            contentAlignment = Alignment.Center,
        ) {
            if (shortcut.icon != null) {
                Image(
                    bitmap = shortcut.icon.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Store badges beside the name (thumbnail too small to overlay): EPIC (storeSource==epic)
            // then EOS, then GOG (storeSource==gog or gog_games exec path).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shortcut.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                ShortcutBadgeOverlay(
                    showSteam = remember(shortcut) { isSteamOriginShortcut(shortcut) },
                    showEa = remember(shortcut) { EaSupport.isTagged(shortcut) },
                    showEpic = remember(shortcut) { shortcut.getExtra("storeSource") == "epic" },
                    showEos = rememberEosBadge(shortcut),
                    showGog = remember(shortcut) { isGogShortcut(shortcut) },
                    showAmazon = remember(shortcut) { isAmazonShortcut(shortcut) },
                    showXbox = remember(shortcut) { isXboxShortcut(shortcut) },
                    showCustom = remember(shortcut) { isCustomOriginShortcut(shortcut) },
                                    showLinux = false,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (remember(shortcut) { WinePath.isOnRemovableStorage(shortcut.container, shortcut.path) }) {
                    Spacer(Modifier.width(6.dp))
                    SdCardBadge()
                }
            }
        }
        ShortcutOverflowButton(
            onSettings = onSettings,
            onRemove = onRemove,
            onClone = onClone,
            onCopyToDriveC = onCopyToDriveC,
            onChangeExe = onChangeExe,
            onAddToHome = onAddToHome,
            onExport = onExport,
            onProperties = onProperties,
            onScrapeCover = onScrapeCover,
            onCommunityConfigs = onCommunityConfigs,
            onGameDetails = onGameDetails,
            onViewLogs = onViewLogs,
            onCloudSaves = onCloudSaves,
            onBackupSaves = onBackupSaves,
            onRestoreSaves = onRestoreSaves,
        )
      }
      }
    }
}

// Wide Steam wallpaper used as the list row's backdrop. Candidates run best-first: the PICS art
// snapshot (a real URL the engine already stored) then the public CDN paths, so a 404 on one
// falls through to the next and a total miss simply leaves the card's own colour showing.
@Composable
private fun RowWallpaper(appId: Int, modifier: Modifier) {
    val context = LocalContext.current
    val candidates = remember(appId) {
        listOfNotNull(
            SteamLibraryArt.of(appId)?.libraryHero,
            SteamLibraryArt.of(appId)?.header,
            "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_hero.jpg",
            SteamStoreSearch.headerUrl(appId),
        )
    }
    var attempt by remember(appId) { mutableStateOf(0) }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(candidates[attempt.coerceIn(0, candidates.lastIndex)])
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { if (attempt < candidates.lastIndex) attempt++ },
        modifier = modifier,
    )
}

// Shared overflow (⋮) button + menu for the list-view cards.
@Composable
private fun ShortcutOverflowButton(
    onSettings: () -> Unit,
    onRemove: () -> Unit,
    onClone: () -> Unit,
    onCopyToDriveC: () -> Unit,
    onChangeExe: () -> Unit,
    onAddToHome: () -> Unit,
    onExport: () -> Unit,
    onProperties: () -> Unit,
    onScrapeCover: () -> Unit,
    onCommunityConfigs: () -> Unit,
    onGameDetails: () -> Unit,
    onViewLogs: () -> Unit,
    onCloudSaves: (() -> Unit)? = null,
    onBackupSaves: (() -> Unit)? = null,
    onRestoreSaves: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = OnSurfaceVariant)
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                onClick = { menuExpanded = false; onSettings() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Remove") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { menuExpanded = false; onRemove() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Clone to container") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { menuExpanded = false; onClone() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Copy to Drive C…") },
                leadingIcon = { Icon(Icons.Filled.DriveFileMove, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menuExpanded = false; onCopyToDriveC() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Change executable…") },
                leadingIcon = { Icon(Icons.Filled.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menuExpanded = false; onChangeExe() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Add to home screen") },
                leadingIcon = { Icon(Icons.Filled.AddToHomeScreen, null) },
                onClick = { menuExpanded = false; onAddToHome() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Export") },
                leadingIcon = { Icon(Icons.Filled.Upload, null) },
                onClick = { menuExpanded = false; onExport() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Game Details") },
                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menuExpanded = false; onGameDetails() },
            )
            // Steam-origin only — opens the Save Manager focused on this game.
            if (onCloudSaves != null) {
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Cloud Saves") },
                    leadingIcon = { Icon(Icons.Filled.CloudSync, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { menuExpanded = false; onCloudSaves() },
                )
            }
            // Custom-import games only — local save backup/restore (the non-Steam equivalent).
            if (onBackupSaves != null) {
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Back up saves") },
                    leadingIcon = { Icon(Icons.Filled.Archive, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { menuExpanded = false; onBackupSaves() },
                )
            }
            if (onRestoreSaves != null) {
                MenuItemDivider()
                DropdownMenuItem(
                    text = { Text("Restore saves") },
                    leadingIcon = { Icon(Icons.Filled.Unarchive, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { menuExpanded = false; onRestoreSaves() },
                )
            }
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Scrape cover") },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menuExpanded = false; onScrapeCover() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Community configs") },
                leadingIcon = { Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { menuExpanded = false; onCommunityConfigs() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("View logs") },
                leadingIcon = { Icon(Icons.Filled.Description, null) },
                onClick = { menuExpanded = false; onViewLogs() },
            )
            MenuItemDivider()
            DropdownMenuItem(
                text = { Text("Properties") },
                leadingIcon = { Icon(Icons.Filled.Info, null) },
                onClick = { menuExpanded = false; onProperties() },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutGridItem(
    shortcut: Shortcut,
    selectionMode: Boolean,
    selected: Boolean,
    onRun: () -> Unit,
    onSettings: () -> Unit,
    onRemove: () -> Unit,
    onClone: () -> Unit,
    onCopyToDriveC: () -> Unit,
    onChangeExe: () -> Unit,
    onAddToHome: () -> Unit,
    onExport: () -> Unit,
    onProperties: () -> Unit,
    onScrapeCover: () -> Unit,
    onCommunityConfigs: () -> Unit,
    onGameDetails: () -> Unit,
    onViewLogs: () -> Unit,
    onCloudSaves: (() -> Unit)? = null,
    onBackupSaves: (() -> Unit)? = null,
    onRestoreSaves: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceColor)
            .border(
                width = if (selected) 3.dp else 2.dp,
                brush = if (selected)
                    Brush.linearGradient(listOf(DangerRed, DangerRed))
                else Brush.linearGradient(
                    // accent-family gradient: dim → accent → accent, so the grid-tile border follows the theme
                    colors = listOf(LocalAccentDim.current, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary),
                ),
                shape = RoundedCornerShape(8.dp),
            )
            .combinedClickable(onClick = onRun, onLongClick = { menuExpanded = true }),
    ) {
        // Cover image fills the entire tile
        if (shortcut.icon != null) {
            Image(
                bitmap = shortcut.icon.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            )
        }

        // Removable-storage marker, over the art's top corner so it never fights the title scrim.
        if (remember(shortcut) { WinePath.isOnRemovableStorage(shortcut.container, shortcut.path) }) {
            SdCardBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
        }

        // Store badges overlaid top-left on the cover: EPIC (storeSource==epic) then EOS, then GOG
        // (storeSource==gog or gog_games exec path).
        ShortcutBadgeOverlay(
            showSteam = remember(shortcut) { isSteamOriginShortcut(shortcut) },
            showEa = remember(shortcut) { EaSupport.isTagged(shortcut) },
            showEpic = remember(shortcut) { shortcut.getExtra("storeSource") == "epic" },
            showEos = rememberEosBadge(shortcut),
            showGog = remember(shortcut) { isGogShortcut(shortcut) },
            showAmazon = remember(shortcut) { isAmazonShortcut(shortcut) },
            showXbox = remember(shortcut) { isXboxShortcut(shortcut) },
            showCustom = remember(shortcut) { isCustomOriginShortcut(shortcut) },
            showLinux = false,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
        )

        // Gradient scrim + name/container at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column {
                Text(
                    text = shortcut.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!shortcut.container?.name.isNullOrEmpty()) {
                    Text(
                        text = shortcut.container?.name ?: "",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The Linux entry is the runtime's own tile, so it carries the runtime's state:
                // while an install runs, the same "Downloading 45% · about 2 min left" the Contents
                // tab shows, from the same source; otherwise a chip when a newer build is waiting.
            }
        }

        // Selection badge. Only while selecting — a permanent empty circle on every tile would
        // read as part of the artwork.
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) DangerRed
                        else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        // Long-press context menu
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Filled.Settings, null) }, onClick = { menuExpanded = false; onSettings() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menuExpanded = false; onRemove() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Clone to container") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menuExpanded = false; onClone() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Copy to Drive C…") }, leadingIcon = { Icon(Icons.Filled.DriveFileMove, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onCopyToDriveC() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Change executable…") }, leadingIcon = { Icon(Icons.Filled.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onChangeExe() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Add to home screen") }, leadingIcon = { Icon(Icons.Filled.AddToHomeScreen, null) }, onClick = { menuExpanded = false; onAddToHome() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Export") }, leadingIcon = { Icon(Icons.Filled.Upload, null) }, onClick = { menuExpanded = false; onExport() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Game Details") }, leadingIcon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onGameDetails() })
            // Steam-origin only — opens the Save Manager focused on this game.
            if (onCloudSaves != null) {
                MenuItemDivider()
                DropdownMenuItem(text = { Text("Cloud Saves") }, leadingIcon = { Icon(Icons.Filled.CloudSync, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onCloudSaves() })
            }
            // Custom-import games only — local save backup/restore (the non-Steam equivalent).
            if (onBackupSaves != null) {
                MenuItemDivider()
                DropdownMenuItem(text = { Text("Back up saves") }, leadingIcon = { Icon(Icons.Filled.Archive, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onBackupSaves() })
            }
            if (onRestoreSaves != null) {
                MenuItemDivider()
                DropdownMenuItem(text = { Text("Restore saves") }, leadingIcon = { Icon(Icons.Filled.Unarchive, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onRestoreSaves() })
            }
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Scrape cover") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onScrapeCover() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Community configs") }, leadingIcon = { Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menuExpanded = false; onCommunityConfigs() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("View logs") }, leadingIcon = { Icon(Icons.Filled.Description, null) }, onClick = { menuExpanded = false; onViewLogs() })
            MenuItemDivider()
            DropdownMenuItem(text = { Text("Properties") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { menuExpanded = false; onProperties() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
/**
 * "Edit Game" details editor — a full-screen dialog (matching [ShortcutSettingsDialogScreen]'s idiom)
 * that lets the user set a shortcut's name + link it to a Steam app and accumulate editorial details
 * (genres, description, release year, metacritic) shown on the launch overlay. Ported from the
 * BannersComponentInjector `GameEditSheet`, adapted to this app's shortcut/extras model.
 *
 * Save is best-effort and entirely off the main thread: renames the shortcut if the name changed
 * (via [ExeShortcutImporter.renameShortcutFiles], which moves cover/icon too), writes the detail
 * extras ([GameDetails.writeTo]), and re-applies the Steam cover for the linked appId ([applySteamCover]).
 * Nothing here throws to the caller. Seeded via remember(shortcut) so re-opening for a different game
 * reseeds cleanly (the compose-state "key on the config" rule).
 */
@Composable
internal fun GameDetailsSheet(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = shortcut.container

    // Seed all editable state from the shortcut's current on-disk details (keyed on shortcut).
    val initial = remember(shortcut) { GameDetails.from(shortcut) }
    var nameField by remember(shortcut) { mutableStateOf(shortcut.name) }
    var genresField by remember(shortcut) { mutableStateOf(initial.genres.joinToString(", ")) }
    var descField by remember(shortcut) { mutableStateOf(initial.description ?: "") }
    var yearField by remember(shortcut) { mutableStateOf(initial.releaseYear ?: "") }
    var metaField by remember(shortcut) { mutableStateOf(initial.metacritic?.toString() ?: "") }
    var linkedAppId by remember(shortcut) { mutableStateOf(initial.steamAppId) }

    var searchResults by remember(shortcut) { mutableStateOf<List<SteamStoreSearch.SteamSuggestion>>(emptyList()) }
    var searching by remember(shortcut) { mutableStateOf(false) }
    var searchError by remember(shortcut) { mutableStateOf<String?>(null) }
    var filling by remember(shortcut) { mutableStateOf(false) }
    var saving by remember(shortcut) { mutableStateOf(false) }

    fun doSearch() {
        val query = nameField.trim()
        if (query.isEmpty()) return
        searching = true
        searchError = null
        searchResults = emptyList()
        scope.launch(Dispatchers.IO) {
            val results = SteamStoreSearch.searchByName(query)
            withContext(Dispatchers.Main) {
                searchResults = results
                if (results.isEmpty()) searchError = "No results found for \"$query\""
                searching = false
            }
        }
    }

    // Tapping a result auto-fills every field from Steam and links the appId. If the details fetch
    // fails (network), we still link the appId so the cover applies and the user can fill fields by hand.
    fun fillFromSteam(appId: Int) {
        filling = true
        searchResults = emptyList()
        scope.launch(Dispatchers.IO) {
            val info = SteamStoreSearch.fetchDetails(appId)
            withContext(Dispatchers.Main) {
                if (info != null) {
                    nameField = info.name
                    genresField = info.genres.joinToString(", ")
                    descField = info.shortDescription ?: ""
                    yearField = info.releaseYear ?: ""
                    metaField = info.metacritic?.toString() ?: ""
                }
                linkedAppId = appId
                filling = false
            }
        }
    }

    fun save() {
        if (saving) return
        saving = true
        val oldBase = shortcut.name
        val newBase = nameField.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        val genres = genresField.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val metacritic = metaField.trim().toIntOrNull()?.takeIf { it in 1..100 }
        val year = yearField.trim().takeIf { it.isNotBlank() }
        val desc = descField.trim().takeIf { it.isNotBlank() }
        val appId = linkedAppId
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Rename if the name changed (moves .desktop/.lnk + icon + cover, rewrites extras).
                var base = oldBase
                if (newBase.isNotBlank() && newBase != oldBase &&
                    ExeShortcutImporter.renameShortcutFiles(container, oldBase, newBase)
                ) {
                    base = newBase
                }
                val file = File(container.getDesktopDir(), "$base.desktop")
                if (file.isFile) {
                    // 2. Persist the editorial details (steamAppId included / cleared on unlink).
                    GameDetails(
                        steamAppId = appId,
                        genres = genres,
                        description = desc,
                        releaseYear = year,
                        metacritic = metacritic,
                    ).writeTo(Shortcut(container, file))
                    // 3. Re-apply the Steam cover for the linked appId (re-reads disk, so the detail
                    //    extras written in step 2 are preserved). No-op / cover untouched when unlinked.
                    if (appId != null && appId > 0) applySteamCover(container, base, appId)
                }
            } catch (_: Exception) {
                // Best-effort — never crash the shortcuts screen on a save.
            }
            withContext(Dispatchers.Main) {
                saving = false
                onSaved()
                onDismiss()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header: Close · title · Save.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = OnSurface)
                    }
                    Text(
                        text = "Edit Game",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { save() }, enabled = !saving) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Divider(color = DividerColor)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Linked Steam app (cover + appId + Unlink).
                    linkedAppId?.let { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SteamResultThumbnail(id)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Linked to Steam App ID:", fontSize = 11.sp, color = OnSurfaceVariant)
                                Text(
                                    "$id",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TextButton(
                                    onClick = { linkedAppId = null },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(28.dp),
                                ) { Text("Unlink", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        Divider(color = DividerColor)
                    }

                    // Game name + Search Steam.
                    Text("Game Name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariant)
                    OutlinedTextField(
                        value = nameField,
                        onValueChange = { nameField = it; searchResults = emptyList(); searchError = null },
                        placeholder = { Text("Enter game name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (nameField.isNotBlank()) {
                                IconButton(onClick = { nameField = ""; searchResults = emptyList() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { doSearch() },
                            enabled = nameField.isNotBlank() && !searching,
                        ) {
                            if (searching) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("Search Steam", fontSize = 13.sp)
                        }
                        if (filling) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Loading…", fontSize = 12.sp, color = OnSurfaceVariant)
                        }
                    }
                    searchError?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                    if (searchResults.isNotEmpty()) {
                        Text("Tap a result to auto-fill all fields:", fontSize = 11.sp, color = OnSurfaceVariant)
                        searchResults.forEach { hit ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fillFromSteam(hit.appId) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SteamResultThumbnail(hit.appId)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(hit.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("App ID: ${hit.appId}", fontSize = 11.sp, color = OnSurfaceVariant)
                                }
                            }
                        }
                    }

                    Divider(color = DividerColor)

                    // Genres.
                    Text("Genres", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariant)
                    OutlinedTextField(
                        value = genresField,
                        onValueChange = { genresField = it },
                        placeholder = { Text("e.g. Action, RPG, Strategy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Comma-separated", fontSize = 10.sp) },
                    )

                    // Description.
                    Text("Description", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariant)
                    OutlinedTextField(
                        value = descField,
                        onValueChange = { descField = it },
                        placeholder = { Text("Short description shown on the launch screen") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Release year + Metacritic.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Release Year", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = yearField,
                                onValueChange = { if (it.length <= 4) yearField = it.filter { c -> c.isDigit() } },
                                placeholder = { Text("e.g. 2023") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Metacritic", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = metaField,
                                onValueChange = { if (it.length <= 3) metaField = it.filter { c -> c.isDigit() } },
                                placeholder = { Text("1–100") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("Leave blank to hide", fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// internal (not private) so the Big Picture screen can reuse the exact same shortcut editor dialog.
// ─────────────────────────────────────────────────────────────────────────────
// Controller / D-pad navigation for the Shortcut editor. Same MANUAL, single-root model as Big
// Picture's GameCommunitySheet / CommunityCatalogBrowser: ONE focusable root owns the keys; a per-frame
// ORDERED id list (`dpadIds`, rebuilt in the dialog body from current visibility so conditional rows
// survive) drives Up/Down; each control publishes its A / Left / Right actions into [actions] via a
// SideEffect so they always reflect current state (only the FOCUSED control's action is ever invoked,
// and every control re-publishes whenever the focus id changes because it reads it). Inert for touch:
// [focusedId] is null until the first D-pad key, and every control keeps its own onClick/onValueChange.
private class ControlActions(
    val activate: () -> Unit = {},
    val onLeft: (() -> Unit)? = null,
    val onRight: (() -> Unit)? = null,
)

private class SettingsDpad {
    var focusedId by mutableStateOf<String?>(null)
    var openId by mutableStateOf<String?>(null)      // the dropdown whose menu is open (sub-nav target)
    var menuIndex by mutableStateOf(0)
    var imeFieldId by mutableStateOf<String?>(null)  // a text field currently holding IME focus
    var menuOptions: List<String> = emptyList()
    var menuOnSelect: (String) -> Unit = {}
    val actions = HashMap<String, ControlActions>()
    val rootFocus = FocusRequester()
    // Previous/next section with wrap-around, published by [DpTabs]; L1/R1 call it from anywhere.
    var tabStep: ((Int) -> Unit)? = null

    fun isFocused(id: String) = focusedId == id
    fun openMenu(id: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
        openId = id; menuOptions = options; menuOnSelect = onSelect
        menuIndex = options.indexOf(selected).coerceAtLeast(0)
    }
    fun closeMenu() { openId = null }
}

// Root key handler for the whole editor window (placed on the dialog Surface). Consumes ONLY the
// DPAD / A / B keys and returns false otherwise, so phone touch, scrolling and the soft keyboard are
// unaffected. [ids] is a lambda so the handler always reads the freshest ordered id list.
private fun Modifier.settingsDpad(dp: SettingsDpad, ids: () -> List<String>, onDismiss: () -> Unit): Modifier =
    this
        .focusRequester(dp.rootFocus)
        .focusable()
        .onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // A focused text field owns the keys (its own on-field handler does B / close-keyboard).
            if (dp.imeFieldId != null) return@onPreviewKeyEvent false
            // Open dropdown → its options get Up/Down + A(select) + B(close JUST the menu).
            if (dp.openId != null) {
                return@onPreviewKeyEvent when (ev.key) {
                    Key.DirectionUp -> { if (dp.menuIndex > 0) dp.menuIndex--; true }
                    Key.DirectionDown -> { if (dp.menuIndex < dp.menuOptions.lastIndex) dp.menuIndex++; true }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                        dp.menuOptions.getOrNull(dp.menuIndex)?.let(dp.menuOnSelect); dp.closeMenu(); true
                    }
                    Key.ButtonB, Key.Back -> { dp.closeMenu(); true }
                    Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            }
            val order = ids()
            val idx = order.indexOf(dp.focusedId)
            when (ev.key) {
                // No cursor yet (first press): land on the first real control, not the title close button.
                Key.DirectionUp, Key.DirectionDown -> if (idx < 0) {
                    dp.focusedId = order.getOrNull(1) ?: order.firstOrNull(); true
                } else if (ev.key == Key.DirectionUp) {
                    dp.focusedId = if (idx <= 0) order.firstOrNull() else order[idx - 1]; true
                } else {
                    dp.focusedId = if (idx < order.lastIndex) order[idx + 1] else order.getOrNull(idx); true
                }
                Key.DirectionLeft -> { dp.focusedId?.takeIf { it in order }?.let { dp.actions[it]?.onLeft?.invoke() }; true }
                Key.DirectionRight -> { dp.focusedId?.takeIf { it in order }?.let { dp.actions[it]?.onRight?.invoke() }; true }
                Key.ButtonA, Key.Enter, Key.DirectionCenter -> { dp.focusedId?.takeIf { it in order }?.let { dp.actions[it]?.activate?.invoke() }; true }
                Key.ButtonB, Key.Back -> { onDismiss(); true }
                // Shoulder buttons switch sections from anywhere (the landscape rail is otherwise
                // only reachable at the very end of the D-pad order).
                Key.ButtonL1 -> { dp.tabStep?.invoke(-1); true }
                Key.ButtonR1 -> { dp.tabStep?.invoke(1); true }
                else -> false
            }
        }

// Scrolls the focused control into view as the D-pad cursor moves down/up past the fold. The editor's
// body is one verticalScroll Column, which acts as the bring-into-view responder, so requesting it on the
// focused control makes the Column follow the highlight. Keyed on this control's own focused state so it
// only fires on the frame it gains focus.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.dpadBringIntoView(dp: SettingsDpad, id: String): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val focused = dp.focusedId == id
    LaunchedEffect(focused) { if (focused) runCatching { requester.bringIntoView() } }
    return this.bringIntoViewRequester(requester)
}

// ── Per-control wrappers. Each publishes its action(s) via SideEffect and renders with the focus
// highlight. `onLeftId`/`onRightId` move focus to a laterally-adjacent sibling (custom W|H, gfx
// driver|wrapper button, Cancel|OK). ──
@Composable
private fun DpField(
    dp: SettingsDpad, id: String, value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, singleLine: Boolean = false, onLeftId: String? = null, onRightId: String? = null,
) {
    val fr = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    SideEffect {
        dp.actions[id] = ControlActions(
            activate = { runCatching { fr.requestFocus() }; keyboard?.show() },
            onLeft = onLeftId?.let { target -> { dp.focusedId = target } },
            onRight = onRightId?.let { target -> { dp.focusedId = target } },
        )
    }
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = singleLine,
        modifier = modifier
            .dpadBringIntoView(dp, id)
            .focusRequester(fr)
            .onFocusChanged { st -> if (st.isFocused) dp.imeFieldId = id else if (dp.imeFieldId == id) dp.imeFieldId = null }
            .then(if (dp.isFocused(id)) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall) else Modifier)
            // On-field B fallback: first B closes the keyboard and hands focus back to the root.
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Back || ev.key == Key.ButtonB)) {
                    keyboard?.hide(); runCatching { dp.rootFocus.requestFocus() }; dp.imeFieldId = null; true
                } else false
            },
    )
}

@Composable
private fun DpDrop(
    dp: SettingsDpad, id: String, label: String, options: List<String>, selected: String, onSelect: (String) -> Unit,
    enabled: Boolean = true, disabledOptions: Set<String> = emptySet(), modifier: Modifier = Modifier,
    onLeftId: String? = null, onRightId: String? = null,
) {
    SideEffect {
        dp.actions[id] = ControlActions(
            activate = { if (enabled) dp.openMenu(id, options, selected, onSelect) },
            onLeft = onLeftId?.let { target -> { dp.focusedId = target } },
            onRight = onRightId?.let { target -> { dp.focusedId = target } },
        )
    }
    LabeledDropdown(
        label = label, options = options, selectedOption = selected, onSelect = onSelect,
        enabled = enabled, disabledOptions = disabledOptions, modifier = modifier.dpadBringIntoView(dp, id),
        focused = dp.isFocused(id),
        expandedOverride = dp.openId == id,
        onExpandedChange = { want -> if (want) dp.openMenu(id, options, selected, onSelect) else dp.closeMenu() },
        highlightedIndex = if (dp.openId == id) dp.menuIndex else -1,
    )
}

@Composable
private fun DpSwitch(dp: SettingsDpad, id: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    SideEffect { dp.actions[id] = ControlActions(activate = { if (enabled) onCheckedChange(!checked) }) }
    DpadHighlight(focused = dp.isFocused(id), modifier = Modifier.dpadBringIntoView(dp, id)) { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }
}

@Composable
private fun DpCheck(dp: SettingsDpad, id: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SideEffect { dp.actions[id] = ControlActions(activate = { onCheckedChange(!checked) }) }
    DpadHighlight(focused = dp.isFocused(id), modifier = Modifier.dpadBringIntoView(dp, id)) { Checkbox(checked = checked, onCheckedChange = onCheckedChange) }
}

// Labels for the six root perf keys (extraData name -> display label), matching PerfRootApplier.KEY_*.
private val ROOT_PERF_LABELS = mapOf(
    "rootCpuGovernorPerf" to "CPU governor → performance",
    "rootCpuFreqLockMax" to "Lock CPU frequency to max",
    "rootAllCoresOnline" to "Keep all cores online",
    "rootGpuMaxClockLock" to "Lock GPU to max clock",
    "rootThermalDisable" to "Disable thermal throttling",
    "rootFanMax" to "Fan to maximum",
)

/** Per-game override value to persist, or null (clear the extra) when it equals the global default. */
internal fun perfExtraOrNull(value: Boolean, global: Boolean): String? =
    if (value == global) null else if (value) "1" else "0"

/** A per-game perf toggle row with an override/inherit indicator and a per-toggle Reset. */
@Composable
private fun PerfEditRow(dp: SettingsDpad, id: String, label: String, checked: Boolean, global: Boolean, onChange: (Boolean) -> Unit) {
    val overridden = checked != global
    // Compact single-line row: switch + label, with a trailing state hint. Overridden → an accent
    // "Reset" tap; otherwise a faint "default" marker. (Full explanation lives in the section "?" help.)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
    ) {
        DpSwitch(dp, id, checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (overridden) Text(
            "● Reset", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onChange(global) }.padding(start = 8.dp)
        ) else Text(
            "default", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun DpSlider(
    dp: SettingsDpad, id: String, value: Float, onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>, step: Float, steps: Int = 0, modifier: Modifier = Modifier,
) {
    SideEffect {
        dp.actions[id] = ControlActions(
            onLeft = { onValueChange((value - step).coerceIn(valueRange.start, valueRange.endInclusive)) },
            onRight = { onValueChange((value + step).coerceIn(valueRange.start, valueRange.endInclusive)) },
        )
    }
    DpadHighlight(focused = dp.isFocused(id), modifier = Modifier.dpadBringIntoView(dp, id)) {
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, modifier = modifier)
    }
}

@Composable
private fun DpButton(
    dp: SettingsDpad, id: String, onActivate: () -> Unit, modifier: Modifier = Modifier,
    onLeftId: String? = null, onRightId: String? = null, content: @Composable () -> Unit,
) {
    SideEffect {
        dp.actions[id] = ControlActions(
            activate = onActivate,
            onLeft = onLeftId?.let { target -> { dp.focusedId = target } },
            onRight = onRightId?.let { target -> { dp.focusedId = target } },
        )
    }
    DpadHighlight(focused = dp.isFocused(id), modifier = modifier.dpadBringIntoView(dp, id)) { content() }
}

@Composable
private fun DpTabs(dp: SettingsDpad, id: String, selected: Int, count: Int, onSelect: (Int) -> Unit, content: @Composable () -> Unit) {
    SideEffect {
        dp.actions[id] = ControlActions(
            onLeft = { if (selected > 0) onSelect(selected - 1) },
            onRight = { if (selected < count - 1) onSelect(selected + 1) },
        )
        dp.tabStep = { step -> if (count > 0) onSelect(((selected + step) % count + count) % count) }
    }
    DpadHighlight(focused = dp.isFocused(id), modifier = Modifier.dpadBringIntoView(dp, id)) { content() }
}

/**
 * The "?" a Linux entry's rows get. It draws nothing unless [show], so the ordinary Windows form keeps
 * exactly the help buttons it already has — and, like every other "?" in this editor, it is not a
 * D-pad target, so it can never be focused whether the row beside it is drawn or not.
 */
@Composable
private fun LinuxHelp(show: Boolean, textResId: Int, onOpen: (Int) -> Unit) {
    if (!show) return
    IconButton(onClick = { onOpen(textResId) }) {
        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun ShortcutSettingsDialogScreen(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    // Non-null when the host can run the "Copy to Drive C" flow (the Games list). The Storage row's
    // "Move to Drive C" button calls this; it stays hidden for a game already on C:.
    onMoveToDriveC: (() -> Unit)? = null,
    // Non-null when the host can run the "Change executable" flow (the Games list). The Executable
    // row's "Change…" button calls this.
    onChangeExe: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val res = context.resources
    // Controller / D-pad state for this editor (see the SettingsDpad model above).
    val dp = remember { SettingsDpad() }

    // Epic Online Services (EOS) auth — only relevant for Epic-origin shortcuts. Default ON.
    val isEpicShortcut = remember { shortcut.getExtra("storeSource") == "epic" }
    // An entry the Linux runtime owns (the Steam client and the games it starts). Nothing below the
    // gamescope session's proot reads the Wine half of this editor - no prefix, no wineserver, no
    // dxwrapper, no guest launcher exist there (XServerDisplayActivity.setupLinuxSession) - so those
    // rows are conditioned off rather than shown as settings that quietly do nothing. Stored values
    // are never touched: an entry converted either way keeps whatever it had.
    val isLinuxEntry = false
    var epicEosEnabled by remember { mutableStateOf(shortcut.getExtra("epicEos", "1") != "0") }
    // Manual override: force the -epicovt ownership-token path even when the auto
    // DenuvoDetector misses an obfuscated Denuvo exe. Default OFF.
    var epicOvtForce by remember { mutableStateOf(shortcut.getExtra("epicOvtForce", "0") == "1") }
    // Launch offline — skip EOS online auth for this game (keeps its Epic identity args). Default OFF.
    var epicOffline by remember { mutableStateOf(shortcut.getExtra("epicOffline", "0") == "1") }
    // Epic Friends Overlay (Phase 3) — provision Epic's real EOS overlay into the prefix so the game's
    // own EOS SDK renders friends/notifications on Shift+F3. Default OFF (opt-in per game).
    var epicOverlayEnabled by remember { mutableStateOf(shortcut.getExtra("epicOverlay", "0") == "1") }
    // Xbox game whose install ships Microsoft's xgameruntime.dll: use the app's (Xodus Nexus) instead. Default ON.
    val xboxShipsRuntime = remember {
        shortcut.getExtra("storeSource") == "xbox" && shortcut.getExtra("xboxProductId").let { id ->
            !id.isNullOrEmpty() && runCatching { com.winhub.hernandez.store.xbox.XboxRuntime.shipsOwnRuntime(context, id) }.getOrDefault(false)
        }
    }
    var xboxNexusRuntime by remember { mutableStateOf(shortcut.getExtra(com.winhub.hernandez.store.xbox.XboxRuntime.NEXUS_RUNTIME_EXTRA, "1") != "0") }

    // Async-loaded state
    var isArm64EC by remember { mutableStateOf(false) }
    // isArm64EC is resolved asynchronously (WineInfo load below). Until it lands we must not
    // relabel the Emulator field, or an arm64ec container would flash "Box64" on first frame.
    var archLoaded by remember { mutableStateOf(false) }
    var box64Versions by remember { mutableStateOf(listOf<String>()) }
    var box64Presets by remember { mutableStateOf(listOf<Box64Preset>()) }
    var fexCoreVersions by remember { mutableStateOf(listOf<String>()) }
    var fexCorePresets by remember { mutableStateOf(listOf<FEXCorePreset>()) }
    var controlsProfiles by remember { mutableStateOf(listOf<ControlsProfile>()) }
    var midiList by remember { mutableStateOf(listOf<String>()) }

    // Screen size
    val screenSizeEntries = remember { res.getStringArray(R.array.screen_size_entries).toList() }
    val rawScreenSize = remember { shortcut.getExtra("screenSize", shortcut.container.getScreenSize()) }
    var selectedScreenSize by remember {
        val display = screenSizeEntries.firstOrNull {
            StringUtils.parseIdentifier(it).equals(rawScreenSize, ignoreCase = true)
        }
        mutableStateOf(display ?: "Custom")
    }
    var customWidth by remember {
        mutableStateOf(if (rawScreenSize.contains("x")) rawScreenSize.substringBefore("x") else "800")
    }
    var customHeight by remember {
        mutableStateOf(if (rawScreenSize.contains("x")) rawScreenSize.substringAfter("x") else "600")
    }

    // Screen alignment (#413) — per-game override of the container's letterbox pinning. Sentinel model:
    // "" = inherit container (Container Default), "0"/"1"/"2" = Center/Top/Bottom (Container.ALIGN_*).
    // Launch reads shortcut.getExtra("screenAlignment") and falls back to the container when empty/absent,
    // so a true inherit is a CLEARED extra — not a snapshot of the container's current value.
    var screenAlignment by remember { mutableStateOf(shortcut.getExtra("screenAlignment", "")) }

    // Graphics driver — bundled entries + user-imported wrappers (issue #132 Step 2), built via the
    // SHARED WrapperManager.driverEntries helper so this list matches ContainerDetailViewModel's
    // exactly (dynamic-dropdown drift is the feature's top-ranked risk). Keyed on wrapperRefreshKey
    // so a wrapper imported/deleted via the manager appears without reopening the editor.
    var wrapperRefreshKey by remember { mutableStateOf(0) }
    val graphicsDriverEntries = remember(wrapperRefreshKey) {
        WrapperManager.driverEntries(context, res.getStringArray(R.array.graphics_driver_entries))
    }
    var selectedGfxDriver by remember {
        val id = shortcut.getExtra("graphicsDriver", shortcut.container.graphicsDriver)
        mutableStateOf(graphicsDriverEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: graphicsDriverEntries.firstOrNull() ?: id)
    }
    var graphicsDriverConfig by remember {
        mutableStateOf(shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()))
    }

    // DX wrapper
    val dxWrapperEntries = remember { res.getStringArray(R.array.dxwrapper_entries).toList() }
    var selectedDxWrapper by remember {
        val id = shortcut.getExtra("dxwrapper", shortcut.container.getDXWrapper())
        mutableStateOf(dxWrapperEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: dxWrapperEntries.firstOrNull() ?: id)
    }
    var dxWrapperConfig by remember {
        mutableStateOf(shortcut.getExtra("dxwrapperConfig", shortcut.container.getDXWrapperConfig()))
    }

    // Renderer (host): Vulkan or EGL. Anything else (legacy ids) is Vulkan.
    var selectedRenderer by remember {
        val id = shortcut.getExtra("renderer", shortcut.container.renderer)
        mutableStateOf(when (id.lowercase()) {
            "egl" -> "EGL"
            "surfacedirect" -> "SurfaceDirect"
            else -> "Vulkan"
        })
    }


    // Deck mode is only turned on through a warning that says what it breaks.
    // Unreal Engine HDR override (per-game, same extra name as the container's): "" = the container's,
    // else off / dx12 / dx11. Both backends; see core.UnrealHdr.
    var unrealHdrOverride by remember { mutableStateOf(com.winhub.hernandez.core.UnrealHdr.shortcutChoice(shortcut)) }

    // ── TV tab (display.ExternalDisplay) — per game only, deliberately with NO container default: which
    // screen a game belongs on is a property of the game. Launching the session ON the TV is what opens
    // the compositor's HDR gate there (the gate reads the activity's own display), so HDR is not a
    // setting in this tab at all — it is reported from whatever the display says.
    var tvLaunch by remember { mutableStateOf(com.winhub.hernandez.display.ExternalDisplay.launchOnTv(shortcut)) }
    // What was STORED when the dialog opened — only this decides whether the tab is shown for a game
    // whose TV is currently unplugged (see tvTabVisible below).
    val tvLaunchStored = remember { com.winhub.hernandez.display.ExternalDisplay.launchOnTv(shortcut) }
    var tvModeId by remember { mutableStateOf(com.winhub.hernandez.display.ExternalDisplay.modeId(shortcut)) }
    var tvMatchRes by remember { mutableStateOf(com.winhub.hernandez.display.ExternalDisplay.matchResolution(shortcut)) }
    // The tab has to appear and disappear with the cable WITHOUT reopening the dialog, and every value
    // on it (name, mode list, HDR) is read live — so a DisplayListener bumps a tick that re-reads the
    // display on the next composition. Unregistered with the dialog.
    var tvDisplayTick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { tvDisplayTick++ }
            override fun onDisplayRemoved(displayId: Int) { tvDisplayTick++ }
            override fun onDisplayChanged(displayId: Int) { tvDisplayTick++ }
        }
        runCatching {
            dm?.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        onDispose { runCatching { dm?.unregisterDisplayListener(listener) } }
    }
    val tvDisplay = remember(tvDisplayTick) { com.winhub.hernandez.display.ExternalDisplay.find(context) }

    // Gyro (motion aim) per-game overrides — seeded from the shortcut extra, falling back to the
    // container's value. Only the game-facing half lives here (deadzone/smoothing stay container-wide,
    // they're hand-tremor/latency settings, not game settings). These are ALWAYS written on save —
    // there's no "inherit" sentinel because enabled=false is a legitimate override — so once this
    // dialog has been saved for a shortcut, changing the container's gyro defaults only affects
    // NEW shortcuts.
    var gyroEnabled by remember {
        mutableStateOf(shortcut.getExtra("gyroEnabled",
            if (shortcut.container.isGyroEnabled) "1" else "0") == "1")
    }
    var gyroTarget by remember {
        mutableStateOf(shortcut.getExtra("gyroTarget",
            shortcut.container.gyroTarget.toString()).toIntOrNull() ?: Container.GYRO_TARGET_DEFAULT)
    }
    var gyroActivator by remember {
        mutableStateOf(shortcut.getExtra("gyroActivator",
            shortcut.container.gyroActivator.toString()).toIntOrNull() ?: Container.GYRO_ACTIVATOR_DEFAULT)
    }
    var gyroActivationMode by remember {
        mutableStateOf(shortcut.getExtra("gyroActivationMode",
            shortcut.container.gyroActivationMode.toString()).toIntOrNull() ?: Container.GYRO_ACTIVATION_MODE_DEFAULT)
    }
    var gyroMode by remember {
        mutableStateOf(shortcut.getExtra("gyroMode",
            shortcut.container.gyroMode.toString()).toIntOrNull() ?: Container.GYRO_MODE_DEFAULT)
    }
    var gyroSensitivity by remember {
        mutableStateOf(shortcut.getExtra("gyroSensitivity",
            shortcut.container.gyroSensitivity.toString()).toFloatOrNull() ?: Container.GYRO_SENSITIVITY_DEFAULT)
    }
    var gyroInvertX by remember {
        mutableStateOf(shortcut.getExtra("gyroInvertX",
            if (shortcut.container.isGyroInvertX) "1" else "0") == "1")
    }
    var gyroInvertY by remember {
        mutableStateOf(shortcut.getExtra("gyroInvertY",
            if (shortcut.container.isGyroInvertY) "1" else "0") == "1")
    }

    // Vulkan renderer per-game overrides (native / Colors=swapRB / present mode) — default to the
    // container's values; only shown + relevant when this shortcut runs on the Vulkan renderer.
    // Stored via the same "native"/"swapRB"/"presentMode" extras the launch resolver reads.
    var vkNative by remember {
        mutableStateOf(shortcut.getExtra("native",
            if (shortcut.container.isRendererNative()) "true" else "false") == "true")
    }
    var vkSwapRB by remember {
        mutableStateOf(shortcut.getExtra("swapRB",
            if (shortcut.container.getRendererSwapRB()) "true" else "false") == "true")
    }
    var vkPresentMode by remember {
        mutableStateOf(shortcut.getExtra("presentMode", shortcut.container.getRendererPresentMode()))
    }
    // Compositor (present-layer) Vulkan driver — per-game override, defaults to the container's value.
    // This is the driver the present layer runs on (System or an installed Turnip), NOT the guest
    // graphics driver above. Launch already resolves shortcut.getExtra("rendererDriverId", ...).
    var vkRendererDriverId by remember {
        mutableStateOf(shortcut.getExtra("rendererDriverId", shortcut.container.getRendererDriverId()))
    }

    // Render scale (supersampling) — per-game override, defaults to the container's "renderScale"
    // extra. Stored via the shortcut "renderScale" extra. "1.0" = Off.
    var renderScale by remember {
        mutableStateOf(shortcut.getExtra("renderScale", shortcut.container.getExtra("renderScale", "1.0")))
    }

    // Single guest-side refresh control (per-game override). Backed by two extras that the merged
    // "In-game refresh rate" dropdown drives together: unlockGameRefreshRate ("" inherit / "1" / "0")
    // and maxGameRefreshRate ("" inherit / "0" unlimited / "N" cap). Both empty = inherit container.
    // See Container.isUnlockGameRefreshRate / getMaxGameRefreshRate.
    var maxGameRefreshRate by remember {
        mutableStateOf(shortcut.getExtra("maxGameRefreshRate", ""))
    }
    var unlockGameRefreshRate by remember {
        mutableStateOf(shortcut.getExtra("unlockGameRefreshRate", ""))
    }

    // Frame Generation engine (off / LSFG Native) — per-game override. Legacy "bionic" and
    // "lsfg" overrides show and save as LSFG Native.
    val fgEngines = remember { listOf("off", "lsfg-native") }
    var frameGenEngine by remember {
        mutableStateOf(shortcut.getExtra("frameGenEngine", shortcut.container.frameGenEngine)
            .let { if (it == "bionic" || it == "lsfg") "lsfg-native" else it }
            .let { if (it == "off") shortcut.container.frameGenEngine else it })
    }
    val lsfgDllAvailable = remember { File(context.filesDir, "lsfg-vk/Lossless.dll").isFile }

    // FPS limiter — per-game override.
    var fpsLimiterEnabled by remember {
        mutableStateOf(
            shortcut.getExtra("fpsLimiterEnabled", if (shortcut.container.isFpsLimiterEnabled) "1" else "0") == "1"
        )
    }

    // Power-user performance toggles — per-game overrides. Effective seed = per-game override (if the
    // shortcut already has the key) else the GLOBAL default (App Settings > Performance). No container
    // level. Saving writes the key ONLY when it DIFFERS from the global default, else clears it so the
    // game re-inherits (see the save block below).
    var sustainedPerfMode by remember {
        mutableStateOf(shortcut.getExtra("sustainedPerfMode",
            if (com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value) "1" else "0") == "1")
    }
    var perfPriorityBoost by remember {
        mutableStateOf(shortcut.getExtra("perfPriorityBoost",
            if (com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value) "1" else "0") == "1")
    }
    var preferBigCores by remember {
        mutableStateOf(shortcut.getExtra("preferBigCores",
            if (com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value) "1" else "0") == "1")
    }
    // Root six — same override/inherit treatment, kept in an observable map keyed by extraData name.
    val rootOverrides = remember {
        mutableStateMapOf<String, Boolean>().apply {
            for (k in com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS)
                put(k, shortcut.getExtra(k, if (com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(k)) "1" else "0") == "1")
        }
    }
    // The 9 power-user perf toggles live in a collapsed "Performance" section to keep this dialog short.
    var perfExpanded by rememberSaveable { mutableStateOf(false) }

    // Audio driver. DirectAudio only loads on the arm64ec Proton builds in
    // DirectAudioSupport.SUPPORTED_BUILD_TOKENS (7 as of driver v1.3.2); a shortcut
    // can't override the Wine version (container-only), so support is fixed by the container's layer.
    // Grey the option out off those layers and coerce a stale saved pick back to the default so the
    // dropdown never shows an unselectable value as selected.
    val audioDriverEntries = remember { res.getStringArray(R.array.audio_driver_entries).toList() }
    // A Linux entry's DirectAudio is the relay driver inside the Linux runtime, wired to Valve's
    // own ARM64 Proton by the session - no layer of ours is involved, so the container's Wine
    // version says nothing about it. Its settings container has none, and gating on it greyed
    // the option out (device-seen) for a driver that is device-proven on that path.
    val directAudioSupported = remember {
        isLinuxEntry || com.winhub.hernandez.core.DirectAudioSupport.isSupported(shortcut.container.wineVersion)
    }
    val directAudioEntry = remember {
        audioDriverEntries.firstOrNull { StringUtils.parseIdentifier(it) == "directaudio" }
    }
    var selectedAudioDriver by remember {
        var id = shortcut.getExtra("audioDriver", shortcut.container.audioDriver)
        if (id == "directaudio" && !directAudioSupported) id = Container.DEFAULT_AUDIO_DRIVER
        mutableStateOf(audioDriverEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: audioDriverEntries.firstOrNull() ?: id)
    }

    // Emulator
    val emulatorEntries = remember { res.getStringArray(R.array.emulator_entries).toList() }
    var selectedEmulator by remember {
        val id = shortcut.getExtra("emulator", shortcut.container.emulator) ?: ""
        mutableStateOf(emulatorEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: emulatorEntries.firstOrNull() ?: id)
    }

    // MIDI
    var selectedMidi by remember {
        mutableStateOf(shortcut.getExtra("midiSoundFont", shortcut.container.getMIDISoundFont()))
    }

    // Basic text fields
    var name by remember { mutableStateOf(shortcut.name) }
    var execArgs by remember { mutableStateOf(shortcut.getExtra("execArgs")) }
    var lcAll by remember { mutableStateOf(shortcut.getExtra("lc_all", shortcut.container.getLC_ALL())) }

    // Checkboxes / switches
    // Per-game fullscreen aspect-ratio override (#71): -1 = use container default, else
    // Container.FULLSCREEN_OFF/FIT/STRETCH. Migrates the legacy per-game "fullscreenStretched".
    var fullscreenModeOverride by remember {
        mutableStateOf(
            run {
                val m = shortcut.getExtra("fullscreenMode")
                when {
                    m.isNotEmpty() -> m.toIntOrNull() ?: -1
                    shortcut.getExtra("fullscreenStretched", "") == "1" -> com.winhub.hernandez.container.Container.FULLSCREEN_STRETCH
                    else -> -1
                }
            }
        )
    }
    // Close the session when this game exits — per-game override, defaults to the container's setting (ON).
    var autoCloseOnExit by remember {
        mutableStateOf(shortcut.getExtra("autoCloseOnExit", shortcut.container.getExtra("autoCloseOnExit", "1")) == "1")
    }
    var exclusiveXInput by remember {
        val v = shortcut.getExtra("exclusiveXInput")
        mutableStateOf(if (v.isEmpty()) shortcut.container.isExclusiveXInput else v == "1")
    }
    val initialInputType = remember {
        shortcut.getExtra("inputType", shortcut.container.getInputType().toString()).toIntOrNull()
            ?: shortcut.container.getInputType()
    }
    var enableXInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0) }
    var enableDInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0) }
    var disabledXInput by remember { mutableStateOf(shortcut.getExtra("disableXinput", "0") == "1") }
    var simTouchScreen by remember { mutableStateOf(shortcut.getExtra("simTouchScreen", "0") == "1") }

    // Per-game controller->player-slot pins. EMPTY string = no shortcut override (inherit the container's
    // Player Slots); any non-empty value means this shortcut OWNS the pins. Same JSON schema (and editor)
    // as the container editor and the in-game Players tab — mutated only via WinHandler.parse/build. The
    // launch resolver reads this extra first, else the container's (resolvedControllerSlotOverridesJson).
    var controllerSlotOverridesJson by remember { mutableStateOf(shortcut.getExtra("controllerSlotOverrides", "")) }

    // #333 per-game auto-hide override. "" = inherit the container; "1"/"0" = explicit on/off for this
    // game. The launch resolver (resolvedAutoHideControlsOnPad) reads this extra first, else the container.
    var autoHideControlsOnPad by remember { mutableStateOf(shortcut.getExtra("autoHideControlsOnPad", "")) }

    // Num controllers
    val numControllersEntries = remember { res.getStringArray(R.array.num_controllers_entries).toList() }
    var selectedNumControllers by remember {
        val n = (shortcut.getExtra("numControllers", "1").toIntOrNull() ?: 1).coerceIn(1, numControllersEntries.size)
        mutableStateOf(numControllersEntries.getOrElse(n - 1) { numControllersEntries.first() })
    }

    // Box64 / FEXCore / controls
    // Both fall back to the container's value, and a container can have none: the Linux
    // runtime's settings container carries no Box64 or FEX at all (a Linux session runs neither),
    // and on a fresh install nothing seeds them. A null here reached .isEmpty() below and crashed
    // the editor the moment it opened for the Steam (Linux) entry.
    var selectedBox64Version by remember {
        mutableStateOf(shortcut.getExtra("box64Version", shortcut.container.getBox64Version()) ?: "")
    }
    var selectedBox64PresetIndex by remember { mutableIntStateOf(0) }
    var selectedFexCoreVersion by remember {
        mutableStateOf(shortcut.getExtra("fexcoreVersion", shortcut.container.getFEXCoreVersion()) ?: "")
    }
    var selectedFexCorePresetIndex by remember { mutableIntStateOf(0) }
    var selectedControlsProfileIndex by remember { mutableIntStateOf(0) }

    // Startup selection
    val startupSelectionEntries = remember { res.getStringArray(R.array.startup_selection_entries).toList() }
    var selectedStartupSelection by remember {
        val idx = (shortcut.getExtra("startupSelection", shortcut.container.getStartupSelection().toString())
            .toIntOrNull() ?: 0).coerceIn(0, startupSelectionEntries.lastIndex)
        mutableStateOf(startupSelectionEntries.getOrElse(idx) { startupSelectionEntries.first() })
    }
    // Custom-startup per-service enabled set (raw names). Inherits the container default when the
    // shortcut has no override, same fallback pattern as startupSelection above.
    var startupServicesEnabled by remember {
        mutableStateOf(
            WineUtils.parseStartupServicesCsv(
                shortcut.getExtra("startupServices", shortcut.container.startupServices)
            ).toSet()
        )
    }

    // Sharpness
    val sharpnessEffectEntries = remember { res.getStringArray(R.array.vkbasalt_sharpness_entries).toList() }
    var selectedSharpnessEffect by remember {
        val v = shortcut.getExtra("sharpnessEffect", "None")
        mutableStateOf(sharpnessEffectEntries.firstOrNull { it == v } ?: sharpnessEffectEntries.firstOrNull() ?: v)
    }
    var sharpnessLevel by remember {
        mutableIntStateOf(shortcut.getExtra("sharpnessLevel", "100").toIntOrNull() ?: 100)
    }
    var sharpnessDenoise by remember {
        mutableIntStateOf(shortcut.getExtra("sharpnessDenoise", "100").toIntOrNull() ?: 100)
    }

    // ReShade multi-effect LOADOUT (vkBasalt drop-in). Scan the user folder; ReshadeLoadoutState holds
    // the ordered effects, per-effect enabled + params, and the solo/stack mode. Loaded from the
    // shortcut override → container default (migrating a legacy single effect + flat params). Serialized
    // back to the reshadeLoadout/reshadeMode/reshadeParams extras on save. reshadeEffects is mutable so
    // a catalog download can rescan the drop-in folder and surface the new effect.
    var reshadeEffects by remember { mutableStateOf(ReshadeManager.scanEffects(context)) }
    val reshadeLoadout = remember { ReshadeLoadoutState() }
    // Initial load (once): resolve the shortcut override else the container default.
    LaunchedEffect(Unit) {
        reshadeLoadout.init(
            reshadeEffects,
            shortcut.getExtra("reshadeLoadout", shortcut.container.getReshadeLoadout()).ifEmpty { null },
            shortcut.getExtra("reshadeMode", shortcut.container.getReshadeMode()),
            shortcut.getExtra("reshadeParams", shortcut.container.getReshadeParams()).ifEmpty { null },
            shortcut.getExtra("reshadeEffect", shortcut.container.getReshadeEffect()),
        )
    }

    // Win components
    val winComponents = remember {
        val raw = shortcut.getExtra("wincomponents", shortcut.container.getWinComponents())
        mutableStateListOf<WinComponentEntry>().also { list ->
            for (parts in KeyValueSet(raw)) {
                val key = parts[0]; val idx = parts[1].toIntOrNull() ?: 0
                val resId = res.getIdentifier(key, "string", context.packageName)
                val label = if (resId != 0) res.getString(resId) else key
                list.add(WinComponentEntry(key, idx, label))
            }
        }
    }

    // Env vars live in dialog-level state (not in the tab) so switching tabs can't drop
    // in-progress edits; written back to the shortcut's extras in save() below.
    // Sync per-game override (extra "syncMode"; core.SyncSupport). null = follow the container. The
    // stored value is the extra, else what this game's own env string said before the selector
    // existed (WINEESYNC=0 → wineserver, WINENTSYNC=1 → ntsync, WINEESYNC=1 → esync). Greyed against
    // the layer the game runs on — the container's (a shortcut has no layer of its own); the probe
    // reads ntdll.so off-main and is cached per layer, so it's keyed on the wine version.
    val syncLayer = shortcut.container.wineVersion ?: ""
    val containerSyncStored = remember(shortcut.container) {
        com.winhub.hernandez.core.SyncSupport.storedMode(
            shortcut.container.getExtra(com.winhub.hernandez.core.SyncMode.EXTRA, ""), shortcut.container.envVars)
    }
    var syncCaps by remember(syncLayer) { mutableStateOf(com.winhub.hernandez.core.SyncSupport.peek(syncLayer)) }
    LaunchedEffect(syncLayer) {
        syncCaps = withContext(Dispatchers.IO) {
            com.winhub.hernandez.core.SyncSupport.capsFor(context, null, syncLayer)
        }
    }
    var syncOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.SyncSupport.storedMode(
            shortcut.getExtra(com.winhub.hernandez.core.SyncMode.EXTRA, ""), shortcut.getExtra("envVars")))
    }
    val containerSyncEffective = syncCaps?.resolve(containerSyncStored)
        ?: containerSyncStored ?: com.winhub.hernandez.core.SyncMode.ESYNC
    val syncEffective = syncOverride?.let { o -> syncCaps?.resolve(o) ?: o } ?: containerSyncEffective
    val syncOverriding = syncOverride != null && syncEffective != containerSyncEffective
    val syncOverrideUnavailable = syncOverride != null && syncCaps?.isAvailable(syncOverride!!) == false
    val pickSync: (String) -> Unit = { m ->
        if (syncCaps?.isAvailable(m) != false && com.winhub.hernandez.core.SyncMode.normalize(m) != null)
            syncOverride = if (m == containerSyncEffective) null else m
    }
    // Fast OpenGL per-game override (extra "fastOpenGL"; core.FastOpenGL): "" = follow the container,
    // "1" / "0" = this game only. Locked with one reason when the game's driver isn't a Turnip or the
    // APK has no X11 GL set. Any layer works: the launch picks the EGL or GLX route.
    var fastGlOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.FastOpenGL.normalize(
            shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA)) ?: "")
    }
    val fastGlDriverVersion = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
        .getVersion(graphicsDriverConfig) ?: ""
    val fastGlDriverOk = remember(fastGlDriverVersion) {
        com.winhub.hernandez.core.FastOpenGL.driverUsable(context, fastGlDriverVersion)
    }
    val fastGlBundled = remember { com.winhub.hernandez.core.X11Egl.isBundled(context) }
    val fastGlLocked = com.winhub.hernandez.core.FastOpenGL.unavailableReason(fastGlDriverOk, fastGlBundled)
    // Show OpenGL FPS / OpenGL vsync off per-game overrides (extras "glFpsHud" / "glVsyncOff"): same
    // "" / "1" / "0" as Fast OpenGL, never locked.
    var glFpsHudOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.FastOpenGL.normalize(
            shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.FPS_HUD.extra)) ?: "")
    }
    var glVsyncOffOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.FastOpenGL.normalize(
            shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF.extra)) ?: "")
    }
    // The sync variables left the env string for the Sync selector (read into syncOverride above),
    // so they're dropped here and on save.
    var envVarsStr by remember {
        mutableStateOf(com.winhub.hernandez.core.SyncSupport.stripSyncVars(shortcut.getExtra("envVars")))
    }
    var showScAudioSettings by remember { mutableStateOf(false) }
    // The game's folder on the Android side, derived from the shortcut's Exec= path, so the
    // editor can look for DLLs the game ships. Null when the drive letter isn't mapped.
    val gameDir = remember(shortcut) {
        runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path)?.parentFile }
            .getOrNull()
    }
    // The game's .exe on the Android side — feeds DependencyDetector's game-root resolution for the
    // "Recommended components" chips in the Win Components tab.
    val gameExe = remember(shortcut) {
        runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path) }.getOrNull()
    }

    // AndroidView refs
    val cpuListViewRef = remember { mutableStateOf<CPUListView?>(null) }

    // Icon
    var iconBitmap by remember { mutableStateOf<Bitmap?>(shortcut.icon) }

    // Sub-dialog show states
    var showGfxConfig by remember { mutableStateOf(false) }
    // Shared with the Graphics tab (its launcher lives there; the dialog renders at dialog-body level).
    var showWrapperManager by remember { mutableStateOf(false) }
    var showDxvkConfig by remember { mutableStateOf(false) }
    var showWineD3DConfig by remember { mutableStateOf(false) }
    // Per-field "?" help (helpRes) + the newcomer glossary ("What is all this?"), mirrored from the
    // container editor. null = hidden; glossaryQuery == "" opens the glossary unfiltered.
    var helpRes by remember { mutableStateOf<Int?>(null) }
    // Raw-string twin of helpRes (HelpTextDialog).
    var helpText by remember { mutableStateOf<String?>(null) }
    var glossaryQuery by remember { mutableStateOf<String?>(null) }
    var showBox64DownloadSheet by remember { mutableStateOf(false) }
    var showFexCoreDownloadSheet by remember { mutableStateOf(false) }
    var showDxvkDownloadSheet by remember { mutableStateOf(false) }
    var showVegasDownloadSheet by remember { mutableStateOf(false) }
    var showVkd3dDownloadSheet by remember { mutableStateOf(false) }
    var showD7vkDownloadSheet by remember { mutableStateOf(false) }

    // Tab
    var selectedTab by remember { mutableIntStateOf(0) }
    // Tab positions: General(0), Win Components(1), Env Vars(2), Advanced(3), Controller(4), TV(5). The
    // old top "general" scrolling block became the General tab; its controller half became Controller.
    // TV is appended only while an external display is plugged in OR this game is already set to launch
    // on one — so a setting left behind by a since-unplugged TV is never hidden where it can't be turned
    // off. It comes and goes live with the cable (tvDisplayTick above).
    // tvLaunchStored, not the live switch: turning the switch off with no TV plugged in must not yank
    // the tab out from under the finger that just moved it. It goes when the dialog is next opened.
    val tvTabVisible = tvDisplay != null || tvLaunchStored || tvLaunch
    // selectedTab is the CONTENT index in the when() below, and tabIndices is which of those the tab
    // strip offers, in order. The two were the same list until a Linux entry had to drop a whole tab:
    // Win Components is Wine DLL-override plumbing and a gamescope session has no prefix to override.
    // Keeping selectedTab a content index means the when() branches never have to be renumbered.
    val tabIndices = remember(tvTabVisible, isLinuxEntry) {
        listOf(0, 6) + (if (isLinuxEntry) emptyList() else listOf(1)) + listOf(2, 3, 4) +
            (if (tvTabVisible) listOf(5) else emptyList())
    }
    val tabTitles = remember(tabIndices) {
        tabIndices.map {
            when (it) {
                0 -> "General"; 1 -> "Win Components"; 2 -> "Env Vars"
                3 -> "Advanced"; 4 -> "Controller"; 6 -> "Graphics"; else -> "TV"
            }
        }
    }
    // Unplugging a TV on a game that never wanted one takes the tab away under the user's finger; land
    // them back on General rather than on an index that no longer has content.
    LaunchedEffect(tabIndices) { if (selectedTab !in tabIndices) selectedTab = tabIndices.first() }

    // Icon picker
    fun applyIconFromUri(uri: Uri) {
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching
            shortcut.iconFile?.let { f ->
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            shortcut.icon = bitmap
            iconBitmap = bitmap
        }
    }
    // System SAF picker (secondary).
    val iconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { applyIconFromUri(it) } }
    // Built-in in-app image picker (primary).
    val iconPickerInAppLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedUri(result.data)?.let { applyIconFromUri(it) }
    }
    var showIconPickMenu by remember { mutableStateOf(false) }

    // Load async data
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cm = ContentsManager(context)
            cm.syncContents()
            val wineInfo = WineInfo.fromIdentifier(context, cm, shortcut.container.wineVersion)
            val arm64ec = wineInfo.isArm64EC()

            val b64Type = if (arm64ec) ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                          else ContentProfile.ContentType.CONTENT_TYPE_BOX64
            val b64Arr = if (arm64ec) res.getStringArray(R.array.wowbox64_version_entries).toMutableList()
                         else res.getStringArray(R.array.box64_version_entries).toMutableList()
            for (p in cm.getProfiles(b64Type)) {
                val n = ContentsManager.getEntryName(p)
                b64Arr.add(n.substring(n.indexOf('-') + 1))
            }

            val fexList = res.getStringArray(R.array.fexcore_version_entries).toMutableList()
            for (p in cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)) {
                val n = ContentsManager.getEntryName(p)
                fexList.add(n.substring(n.indexOf('-') + 1))
            }

            val b64Presets = Box64PresetManager.getPresets("box64", context)
            val fexPresets = FEXCorePresetManager.getPresets(context)
            val profiles = InputControlsManager(context).getProfiles(true)

            val midi = mutableListOf("-- ${context.getString(R.string.disabled)} --", MidiManager.DEFAULT_SF2_FILE)
            val sfDir = File(context.filesDir, MidiManager.SF_DIR)
            if (sfDir.exists()) sfDir.listFiles()?.forEach { midi.add(it.name) }

            withContext(Dispatchers.Main) {
                isArm64EC = arm64ec
                archLoaded = true
                box64Versions = b64Arr
                fexCoreVersions = fexList
                box64Presets = b64Presets
                fexCorePresets = fexPresets
                controlsProfiles = profiles
                midiList = midi

                val b64Id = shortcut.getExtra("box64Preset", shortcut.container.getBox64Preset())
                selectedBox64PresetIndex = b64Presets.indexOfFirst { it.id == b64Id }.coerceAtLeast(0)

                val fexId = shortcut.getExtra("fexcorePreset", shortcut.container.getFEXCorePreset())
                selectedFexCorePresetIndex = fexPresets.indexOfFirst { it.id == fexId }.coerceAtLeast(0)

                val cpId = shortcut.getExtra("controlsProfile", "0").toIntOrNull() ?: 0
                selectedControlsProfileIndex = if (cpId == 0) 0
                    else profiles.indexOfFirst { it.id == cpId }.let { if (it >= 0) it + 1 else 0 }

                if (selectedBox64Version.isEmpty()) selectedBox64Version = b64Arr.firstOrNull() ?: ""
            }
        }
    }

    // Save
    fun save() {
        val newName = name.trim()
        if (newName.isNotEmpty() && newName != shortcut.name) {
            renameShortcut(shortcut, newName)
        }

        val screenSize = if (selectedScreenSize == "Custom") {
            val w = customWidth.trim(); val h = customHeight.trim()
            if (w.matches(Regex("[0-9]+")) && h.matches(Regex("[0-9]+"))) {
                val wi = w.toInt(); val hi = h.toInt()
                if (wi % 2 == 0 && hi % 2 == 0) "${wi}x${hi}" else Container.DEFAULT_SCREEN_SIZE
            } else Container.DEFAULT_SCREEN_SIZE
        } else {
            StringUtils.parseIdentifier(selectedScreenSize)
        }

        var finalInputType = 0
        if (enableXInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        if (enableDInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()

        val wincomps = winComponents.joinToString(",") { "${it.key}=${it.selectedIndex}" }
        val envVars = com.winhub.hernandez.core.SyncSupport.stripSyncVars(envVarsStr)
        val cpuList = cpuListViewRef.value?.getCheckedCPUListAsString() ?: shortcut.getExtra("cpuList", shortcut.container.getCPUList(true))

        val b64PresetId = box64Presets.getOrElse(selectedBox64PresetIndex) { null }?.id ?: Box64Preset.COMPATIBILITY
        val fexPresetId = fexCorePresets.getOrElse(selectedFexCorePresetIndex) { null }?.id ?: FEXCorePreset.COMPATIBILITY
        val ctrlProfileId = if (selectedControlsProfileIndex == 0) 0
            else controlsProfiles.getOrElse(selectedControlsProfileIndex - 1) { null }?.id ?: 0

        val midiVal = if (midiList.isNotEmpty() && selectedMidi == midiList.firstOrNull()) "" else selectedMidi
        val startupIdx = startupSelectionEntries.indexOf(selectedStartupSelection).coerceAtLeast(0)
        val numCtrl = (numControllersEntries.indexOf(selectedNumControllers) + 1).coerceAtLeast(1)

        with(shortcut) {
            putExtra("execArgs", execArgs.ifEmpty { null })
            if (isEpicShortcut) putExtra("epicEos", if (epicEosEnabled) "1" else "0")
            if (isEpicShortcut) putExtra("epicOvtForce", if (epicOvtForce) "1" else "0")
            if (isEpicShortcut) putExtra("epicOffline", if (epicOffline) "1" else "0")
            if (isEpicShortcut) putExtra("epicOverlay", if (epicOverlayEnabled) "1" else "0")
            if (xboxShipsRuntime) putExtra(com.winhub.hernandez.store.xbox.XboxRuntime.NEXUS_RUNTIME_EXTRA, if (xboxNexusRuntime) "1" else "0")
            putExtra("screenSize", screenSize)
            // #413: "" (Container Default) -> null -> putExtra removes the key, so launch re-inherits the
            // container each time (true inherit, not a snapshot). "0"/"1"/"2" store the Center/Top/Bottom override.
            putExtra("screenAlignment", screenAlignment.ifEmpty { null })
            putExtra("graphicsDriver", StringUtils.parseIdentifier(selectedGfxDriver))
            putExtra("graphicsDriverConfig", graphicsDriverConfig)
            // Unreal Engine HDR override: "" clears the extra (container default).
            putExtra(com.winhub.hernandez.core.UnrealHdr.EXTRA, unrealHdrOverride.ifEmpty { null })
            // TV tab (display.ExternalDisplay) — per game, no container fallback, so these are always
            // written as a plain "1"/"0" (there is no inherit sentinel to preserve). tvModeId 0 = leave
            // the TV on whatever output mode it is already running.
            putExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_LAUNCH, if (tvLaunch) "1" else "0")
            putExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_MODE_ID, tvModeId.toString())
            putExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_MATCH_RES, if (tvMatchRes) "1" else "0")
            putExtra("renderer", StringUtils.parseIdentifier(selectedRenderer))
            // Gyro per-game overrides (read by the launch resolver in XServerDisplayActivity).
            putExtra("gyroEnabled", if (gyroEnabled) "1" else "0")
            putExtra("gyroTarget", gyroTarget.toString())
            putExtra("gyroActivator", gyroActivator.toString())
            putExtra("gyroActivationMode", gyroActivationMode.toString())
            putExtra("gyroMode", gyroMode.toString())
            putExtra("gyroSensitivity", gyroSensitivity.toString())
            putExtra("gyroInvertX", if (gyroInvertX) "1" else "0")
            putExtra("gyroInvertY", if (gyroInvertY) "1" else "0")
            // Vulkan per-game overrides (read by resolvedRendererNative/SwapRB/PresentMode at launch).
            putExtra("native", if (vkNative) "true" else "false")
            putExtra("swapRB", if (vkSwapRB) "true" else "false")
            putExtra("presentMode", vkPresentMode)
            putExtra("rendererDriverId", vkRendererDriverId)
            putExtra("renderScale", if (renderScale == "1.0") null else renderScale)
            // "In-game refresh rate" per-game override: both extras written together, "" = inherit the
            // container (store null so the extra is cleared, not left empty → keeps the shortcut default).
            putExtra("maxGameRefreshRate", maxGameRefreshRate.ifEmpty { null })
            putExtra("unlockGameRefreshRate", unlockGameRefreshRate.ifEmpty { null })
            // Override-when-different, like the rows below: stamping the inherited value here
            // turned the container's then-current engine into a permanent per-game override.
            // "Off" means inherit too (the resolver reads it the same as an absent extra), so it
            // clears the extra instead of pinning the game off against a later LSFG container.
            if (frameGenEngine != "off" && frameGenEngine != shortcut.container.frameGenEngine) {
                putExtra("frameGenEngine", frameGenEngine)
            } else {
                putExtra("frameGenEngine", null)
            }
            putExtra("frameGenEngineExplicit", null)
            putExtra("fpsLimiterEnabled", if (fpsLimiterEnabled) "1" else "0")
            // Override-when-different: write the per-game key only when it differs from the global
            // default; otherwise null clears the extra so the game re-inherits (hasExtra=false).
            putExtra("sustainedPerfMode", perfExtraOrNull(sustainedPerfMode, com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value))
            putExtra("perfPriorityBoost", perfExtraOrNull(perfPriorityBoost, com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value))
            putExtra("preferBigCores", perfExtraOrNull(preferBigCores, com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value))
            for (rk in com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS)
                putExtra(rk, perfExtraOrNull(rootOverrides[rk] ?: false, com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(rk)))
            putExtra("dxwrapper", StringUtils.parseIdentifier(selectedDxWrapper))
            putExtra("dxwrapperConfig", dxWrapperConfig)
            // Belt-and-suspenders: never persist Audio=directaudio for a layer that can't load it (the
            // grey-out blocks a fresh pick; this catches an already-set one edited without touching it).
            putExtra("audioDriver", StringUtils.parseIdentifier(selectedAudioDriver).let {
                if (it == "directaudio" && !directAudioSupported) Container.DEFAULT_AUDIO_DRIVER else it
            })
            putExtra("emulator", StringUtils.parseIdentifier(selectedEmulator))
            putExtra("midiSoundFont", midiVal.ifEmpty { null })
            putExtra("lc_all", lcAll)
            // #71: write the per-game mode override (or null = use container default) and clear the
            // legacy boolean so it can never shadow the new key.
            putExtra("fullscreenMode", if (fullscreenModeOverride < 0) null else fullscreenModeOverride.toString())
            putExtra("fullscreenStretched", null)
            putExtra("autoCloseOnExit", if (autoCloseOnExit) "1" else "0")
            putExtra("inputType", finalInputType.toString())
            putExtra("exclusiveXInput", if (exclusiveXInput) "1" else "0")
            putExtra("disableXinput", if (disabledXInput) "1" else null)
            putExtra("simTouchScreen", if (simTouchScreen) "1" else "0")
            // Empty = clear the extra so the game re-inherits the container's Player Slots.
            putExtra("controllerSlotOverrides", controllerSlotOverridesJson.ifEmpty { null })
            // #333: empty = re-inherit the container's auto-hide setting.
            putExtra("autoHideControlsOnPad", autoHideControlsOnPad.ifEmpty { null })
            putExtra("numControllers", numCtrl.toString())
            putExtra("box64Version", selectedBox64Version)
            putExtra("box64Preset", b64PresetId)
            putExtra("fexcoreVersion", selectedFexCoreVersion)
            putExtra("fexcorePreset", fexPresetId)
            putExtra("controlsProfile", if (ctrlProfileId > 0) ctrlProfileId.toString() else null)
            putExtra("startupSelection", startupIdx.toString())
            // Persist the Custom enabled set alongside the selection (launch reads it only when
            // the selection is Custom). Written regardless so switching presets keeps the picks.
            putExtra("startupServices", startupServicesEnabled.joinToString(","))
            putExtra("sharpnessEffect", selectedSharpnessEffect)
            putExtra("sharpnessLevel", sharpnessLevel.toString())
            putExtra("sharpnessDenoise", sharpnessDenoise.toString())
            putExtra("reshadeLoadout", reshadeLoadout.loadoutJsonOrNull())
            putExtra("reshadeMode", reshadeLoadout.mode)
            putExtra("reshadeParams", reshadeLoadout.paramsJsonOrNull())
            putExtra("reshadeEffect", reshadeLoadout.firstEffectName())
            putExtra("wincomponents", wincomps)
            putExtra("envVars", envVars.ifEmpty { null })
            // Sync override: only a pick that differs from the container's is stored (null clears the
            // extra = follow the container). A pick the layer can't run saves as what it resolves to.
            putExtra(com.winhub.hernandez.core.SyncMode.EXTRA,
                syncOverride?.let { o -> syncCaps?.resolve(o) ?: o }?.takeIf { it != containerSyncEffective })
            // OpenGL overrides: "" clears the extra (follow the container). Kept as picked while Fast is
            // locked, so a later driver change brings the choice back.
            putExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA, fastGlOverride.ifEmpty { null })
            putExtra(com.winhub.hernandez.core.FastOpenGL.FPS_HUD.extra, glFpsHudOverride.ifEmpty { null })
            putExtra(com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF.extra, glVsyncOffOverride.ifEmpty { null })
            putExtra("cpuList", cpuList)
            saveData()
        }
    }

    // Panel refresh rates (drives whether the "In-game refresh rate" row exists) — hoisted so the D-pad
    // order list below can account for that conditional row.
    val panelRates = remember {
        com.winhub.hernandez.widget.XServerView.getSupportedRefreshRates(
            if (android.os.Build.VERSION.SDK_INT >= 30) context.display
            else (context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as android.view.WindowManager).defaultDisplay)
    }

    // The ORDERED, currently-visible focusable ids (mirrors the render conditionals below). Rebuilt each
    // recomposition so conditional rows (custom W/H, SF/Vulkan blocks, refresh, MIDI, gyro block) drop in
    // and out of the D-pad order automatically. Now that each tab is its own screen, only the SELECTED
    // tab's controls are in the order — the tab selector ("tabs", Left/Right switches tabs) plus the
    // title-close and OK/Cancel are always present. Tab content on Win/Env/Advanced is touch-navigable.
    val dpadIds = buildList {
        add("titleX")
        when (selectedTab) {
            0 -> { // General
                add("name")
                if (!isLinuxEntry) add("execArgs")
                add("screenSize")
                if (selectedScreenSize == "Custom") { add("customW"); add("customH") }
                add("screenAlignment")
                add("selectIcon")
                // The Wine/X11 graphics stack is not registered for a Linux entry, so the D-pad
                // cursor can never land on a row that isn't drawn (see the render conditionals).
                if (!isLinuxEntry) {
                    add(com.winhub.hernandez.core.UnrealHdr.EXTRA)
                    if (panelRates.isNotEmpty()) add("refresh")
                }
                add("frameGen"); add("fpsLimiter"); add("audio")
                if (!isLinuxEntry) {
                    add("emulator")
                    if (midiList.isNotEmpty()) add("midi")
                    add("lcAll")
                    add("syncMode")
                }
                add("fullscreen")
                if (!isLinuxEntry) add("autoClose")
            }
            6 -> { // Graphics — wrapper manager, DX Wrapper, Renderer, render scale
                add("gfxDriver")   // compositor/display driver (moved from General)
                if (!isLinuxEntry) add("gfxConfig")
                if (!isLinuxEntry) add("gfxWrapper")
                if (!isLinuxEntry) {
                    add("dxWrapper"); add("dxConfig"); add("renderer")
                    if (selectedRenderer == "Vulkan") { add("vkNative"); add("vkColors"); add("vkPresent"); add("vkDriver") }
                    add("renderScale")
                    add("openglHelp"); add("fastOpenGL"); add("glFpsHud"); add("glVsyncOff")
                }
            }
            4 -> { // Controller
                if (!isLinuxEntry) { add("enableXInput"); add("enableDInput"); add("exclusiveXInput"); add("disableXInput") }
                add("simTouch")
                if (!isLinuxEntry) add("numControllers")
                add("gyroEnabled")
                if (gyroEnabled) {
                    add("gyroMode"); add("gyroTarget"); add("gyroActivator")
                    if (gyroActivator != Container.GYRO_ACTIVATOR_ALWAYS) add("gyroActivationMode")
                    add("gyroSensitivity"); add("gyroInvertX"); add("gyroInvertY")
                }
            }
            5 -> { // TV — only reachable while the tab exists (see tvTabVisible)
                add("tvLaunch")
                // A display advertising a single mode renders as a read-only row, so it takes no focus.
                if (com.winhub.hernandez.display.ExternalDisplay.selectableModes(tvDisplay).size > 1) add("tvModeId")
                add("tvMatchRes")
            }
        }
        add("tabs"); add("cancel"); add("ok")
    }
    // After a section switch (L1/R1 or touch) the D-pad cursor may point at a control the new section
    // doesn't have: move it to that section's first control (index 1, just past the title close).
    // Untouched for touch users, whose cursor stays null.
    LaunchedEffect(selectedTab) {
        // A pad user (cursor exists) lands on the new section's first control every time; touch users
        // have no cursor and are left alone.
        if (dp.focusedId != null) dp.focusedId = dpadIds.getOrNull(1)
    }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Seed the root focus from INSIDE the dialog window: from the host composable the focus target
        // was not attached yet, the request threw and was swallowed, and Android's default focus search
        // then dropped the first D-pad press onto a text field (which owns the keys) - the "move the
        // D-pad around until it works" glitch. Retry briefly until the Surface below is attached.
        LaunchedEffect(Unit) {
            for (attempt in 0 until 20) {
                if (runCatching { dp.rootFocus.requestFocus() }.isSuccess) break
                kotlinx.coroutines.delay(50)
            }
        }
        Surface(
            // A DEFINITE height (not fillMaxHeight fraction): inside a Dialog the window height is
            // effectively unbounded, so fillMaxHeight(fraction) silently no-ops and the Surface
            // wrapped its content — which left a dead band under the footer and kept the weighted
            // content from expanding. A concrete dp height propagates bounded constraints, so the
            // inner Column fills it and the footer pins flush to the bottom, mirroring the top bar.
            modifier = Modifier.fillMaxWidth(0.95f)
                .height((LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .settingsDpad(dp, { dpadIds }, onDismiss),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            // Fill the fixed-height Surface so the weighted content region actually expands and the
            // footer pins to the real bottom edge. Without this the Column wrapped its content and
            // floated near the top, leaving a large dead band under the OK/Cancel bar.
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar — compact: minimal vertical padding + a shrunk close button so the
                // header (and its footer twin below) don't eat vertical space, most noticeable in
                // landscape where height is scarce.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(shortcut.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    DpButton(dp, "titleX", onActivate = onDismiss) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }
                Divider(color = DividerColor)

                // Scrollable tab content — ONE region shared by both orientations (portrait pins the
                // tab strip above it; landscape puts the rail beside it). Scrolls internally so the
                // Cancel/OK footer stays pinned. Branch labels are tab indices: General(0),
                // Win Components(1), Env Vars(2), Advanced(3), Controller(4). General and Controller are
                // authored next to each other because both came from the old single top form; the
                // Controller TAB sits last (index 4).
                val contentScroll = rememberScrollState()
                val mainContent: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(contentScroll).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (selectedTab) {
                            6 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Graphics tab (always 2nd): Graphics Driver, wrapper manager, DX Wrapper,
                    // Renderer, render scale.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DpDrop(
                            dp, "gfxDriver",
                            label = stringResource(R.string.graphics_driver),
                            options = graphicsDriverEntries,
                            selected = selectedGfxDriver,
                            onSelect = { selectedGfxDriver = it },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { helpRes = R.string.help_graphics_driver }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }

                    if (!isLinuxEntry) {
                        DpButton(dp, "gfxConfig", onActivate = { showGfxConfig = true }, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { showGfxConfig = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("${stringResource(R.string.graphics_driver)}: ${GraphicsDriverConfigDialog.getVersion(graphicsDriverConfig)}")
                            }
                        }
                    }
                    if (!isLinuxEntry) {
                        DpButton(dp, "gfxWrapper", onActivate = { showWrapperManager = true }) {
                            OutlinedButton(onClick = { showWrapperManager = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.wrapper_manager_open))
                            }
                        }
                    }
                    if (!isLinuxEntry) {
                    // DX Wrapper
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DpDrop(
                            dp, "dxWrapper",
                            label = stringResource(R.string.dxwrapper),
                            options = dxWrapperEntries,
                            selected = selectedDxWrapper,
                            onSelect = { newWrapper ->
                                val wasVegas = StringUtils.parseIdentifier(selectedDxWrapper).contains("vegas")
                                val isVegas = StringUtils.parseIdentifier(newWrapper).contains("vegas")
                                selectedDxWrapper = newWrapper
                                // Strip dxvkConfigFile when leaving VEGAS — prevents stale
                                // VEGAS config path from leaking into plain DXVK+VKD3D.
                                if (wasVegas && !isVegas) {
                                    val cfg = DXVKConfigDialog.parseConfig(dxWrapperConfig)
                                    val path = cfg.get("dxvkConfigFile")
                                    if (path.isNotEmpty()) {
                                        val stripped = dxWrapperConfig.split(",").filter { !it.startsWith("dxvkConfigFile=") }.joinToString(",")
                                        dxWrapperConfig = stripped
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { helpRes = R.string.dxwrapper_help_content }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    DpButton(dp, "dxConfig", onActivate = {
                        val w = StringUtils.parseIdentifier(selectedDxWrapper)
                        if (w.contains("dxvk") || w.contains("vegas")) showDxvkConfig = true
                        else showWineD3DConfig = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val w = StringUtils.parseIdentifier(selectedDxWrapper)
                                if (w.contains("dxvk") || w.contains("vegas")) showDxvkConfig = true
                                else showWineD3DConfig = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("DX Wrapper Config") }
                    }

                    // Renderer (host) — Vulkan is the only host renderer.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DpDrop(
                            dp, "renderer",
                            label = stringResource(R.string.renderer),
                            options = listOf("Vulkan", "EGL", "SurfaceDirect"),
                            selected = selectedRenderer,
                            onSelect = { selectedRenderer = it },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { helpRes = R.string.help_renderer }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Vulkan renderer per-game overrides — only relevant when this game runs on Vulkan.
                    if (selectedRenderer == "Vulkan") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.renderer_native), Modifier.weight(1f))
                            IconButton(onClick = { helpRes = R.string.help_renderer_native }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                            DpSwitch(dp, "vkNative", checked = vkNative, onCheckedChange = { vkNative = it })
                        }
                        // Colors = the game buffer's channel order. BGRA (default) presents as-is; RGBA
                        // swaps R/B (routes through the compositor — native can't swap). Per-game so one
                        // game can differ from the container / its siblings.
                        val vkColorOrders = listOf("BGRA", "RGBA")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpDrop(
                                dp, "vkColors",
                                label = stringResource(R.string.renderer_colors),
                                options = vkColorOrders,
                                selected = if (vkSwapRB) "RGBA" else "BGRA",
                                onSelect = { vkSwapRB = (it == "RGBA") },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { helpRes = R.string.help_renderer_colors }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                        }
                        // Present mode is ignored under Native Rendering (direct scanout), so grey it out.
                        val vkPmValues = listOf("fifo", "mailbox", "immediate")
                        val vkPmLabels = listOf(
                            stringResource(R.string.renderer_present_mode_fifo),
                            stringResource(R.string.renderer_present_mode_mailbox),
                            stringResource(R.string.renderer_present_mode_immediate)
                        )
                        val vkPmIdx = vkPmValues.indexOf(vkPresentMode).coerceAtLeast(0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpDrop(
                                dp, "vkPresent",
                                label = stringResource(R.string.renderer_present_mode),
                                options = vkPmLabels,
                                selected = vkPmLabels[vkPmIdx],
                                onSelect = { vkPresentMode = vkPmValues[vkPmLabels.indexOf(it)] },
                                enabled = !vkNative,
                                modifier = (if (vkNative) Modifier.alpha(0.5f) else Modifier).weight(1f)
                            )
                            IconButton(onClick = { helpRes = R.string.renderer_present_mode_help_content }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                        }
                        // Compositor (present-layer) Vulkan driver: System or an installed Turnip. Applies
                        // regardless of native mode, so not gated on vkNative. Matches the container cog.
                        val vkDriverOptions = remember {
                            val installed = try {
                                com.winhub.hernandez.contents.AdrenotoolsManager(context).enumarateInstalledDrivers()
                            } catch (e: Exception) { arrayListOf<String>() }
                            (listOf("system") + installed).distinct()
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpDrop(
                                dp, "vkDriver",
                                label = stringResource(R.string.renderer_driver_id),
                                options = vkDriverOptions,
                                selected = if (vkDriverOptions.contains(vkRendererDriverId)) vkRendererDriverId else "system",
                                onSelect = { vkRendererDriverId = it },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { helpRes = R.string.help_renderer_driver }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                        }
                        // FG temporarily forces Mailbox; caption the field so FIFO-while-FG-selected isn't confusing.
                        if (frameGenEngine != "off") {
                            Text(
                                stringResource(R.string.renderer_present_mode_fg_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Render scale (supersampling) — per-game override of the container default.
                    run {
                        val renderScaleValues = listOf("1.0", "1.25", "1.5", "2.0")
                        val renderScaleLabels = listOf("Off", "1.25x", "1.5x", "2x")
                        val rsIdx = renderScaleValues.indexOf(renderScale).coerceAtLeast(0)
                        DpDrop(
                            dp, "renderScale",
                            label = "Render scale (supersampling)",
                            options = renderScaleLabels,
                            selected = renderScaleLabels[rsIdx],
                            onSelect = { renderScale = renderScaleValues[renderScaleLabels.indexOf(it)] }
                        )
                    }

                    // OpenGL card (per-game): Fast / Show FPS / Vsync off, each `Auto | On | Off` (Auto =
                    // follow the container), under one "?" and a summary of what the game will get. Fast
                    // is locked to Auto with a short reason. D-pad: the "?" and each row are stops;
                    // Left/Right step the pills, A goes back to Auto. Launch-only (EGL vs GLX is fixed
                    // when the game starts), so there is no drawer twin.
                    run {
                        val glOptions = com.winhub.hernandez.ui.components.OPENGL_OVERRIDES
                        val fpsSw = com.winhub.hernandez.core.FastOpenGL.FPS_HUD
                        val vsyncSw = com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF
                        val pickFastGl: (String) -> Unit = { v -> if (fastGlLocked == null || v.isEmpty()) fastGlOverride = v }
                        val pickFps: (String) -> Unit = { v -> glFpsHudOverride = v }
                        val pickVsync: (String) -> Unit = { v -> glVsyncOffOverride = v }
                        val stepActions = { value: String, pick: (String) -> Unit ->
                            ControlActions(
                                activate = { pick("") },
                                onLeft = { glOptions.getOrNull(glOptions.indexOf(value) - 1)?.let(pick) },
                                onRight = { glOptions.getOrNull(glOptions.indexOf(value) + 1)?.let(pick) },
                            )
                        }
                        SideEffect {
                            dp.actions["openglHelp"] = ControlActions(activate = { helpRes = R.string.help_opengl_settings })
                            dp.actions["fastOpenGL"] = stepActions(fastGlOverride, pickFastGl)
                            dp.actions["glFpsHud"] = stepActions(glFpsHudOverride, pickFps)
                            dp.actions["glVsyncOff"] = stepActions(glVsyncOffOverride, pickVsync)
                        }
                        val containerFast = shortcut.container.isFastOpenGL()
                        val containerFps = fpsSw.containerOn(shortcut.container.getExtra(fpsSw.extra))
                        val containerVsync = vsyncSw.containerOn(shortcut.container.getExtra(vsyncSw.extra))
                        val fastEffective = fastGlLocked == null &&
                            com.winhub.hernandez.core.FastOpenGL.requested(if (containerFast) "1" else "0", fastGlOverride)
                        com.winhub.hernandez.ui.components.OpenGLCard(
                            onHelp = { helpRes = R.string.help_opengl_settings },
                            summary = com.winhub.hernandez.ui.components.openGLSummary(
                                fastEffective,
                                fpsSw.requested(if (containerFps) "1" else "0", glFpsHudOverride),
                                vsyncSw.requested(if (containerVsync) "1" else "0", glVsyncOffOverride)),
                            helpFocused = dp.isFocused("openglHelp"),
                            modifier = Modifier.dpadBringIntoView(dp, "openglHelp"),
                        ) {
                            com.winhub.hernandez.ui.components.OpenGLCardRow(
                                com.winhub.hernandez.ui.components.OPENGL_FAST_ICON, "Fast",
                                com.winhub.hernandez.ui.components.openGLFastNote(fastGlLocked),
                                focused = dp.isFocused("fastOpenGL"),
                                modifier = Modifier.dpadBringIntoView(dp, "fastOpenGL"),
                            ) {
                                com.winhub.hernandez.ui.components.OpenGLPills(fastGlOverride,
                                    autoOn = containerFast, locked = fastGlLocked != null, onPick = pickFastGl)
                            }
                            com.winhub.hernandez.ui.components.OpenGLCardRow(
                                com.winhub.hernandez.ui.components.OPENGL_FPS_ICON, "Show FPS", null,
                                focused = dp.isFocused("glFpsHud"),
                                modifier = Modifier.dpadBringIntoView(dp, "glFpsHud"),
                            ) {
                                com.winhub.hernandez.ui.components.OpenGLPills(glFpsHudOverride,
                                    autoOn = containerFps, locked = false, onPick = pickFps)
                            }
                            com.winhub.hernandez.ui.components.OpenGLCardRow(
                                com.winhub.hernandez.ui.components.OPENGL_VSYNC_ICON, "Vsync off", null,
                                focused = dp.isFocused("glVsyncOff"),
                                modifier = Modifier.dpadBringIntoView(dp, "glVsyncOff"),
                            ) {
                                com.winhub.hernandez.ui.components.OpenGLPills(glVsyncOffOverride,
                                    autoOn = containerVsync, locked = false, onPick = pickVsync)
                            }
                        }
                    }
                    }
                            }
                            0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Name
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DpField(
                            dp, "name",
                            value = name,
                            onValueChange = { name = it },
                            label = stringResource(R.string.name),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        LinuxHelp(isLinuxEntry, R.string.help_linux_name) { helpRes = it }
                    }

                    // Exec Args — appended to the Wine start command. A Linux entry's Exec line is
                    // never run as written (linuxSessionArgs decides from the extras), so there is
                    // nothing for arguments to be appended to.
                    if (!isLinuxEntry) {
                        DpField(
                            dp, "execArgs",
                            value = execArgs,
                            onValueChange = { execArgs = it },
                            label = stringResource(R.string.exec_arguments),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Storage — where this game's files currently live, and (unless it's already on
                    // C:) a one-tap "Move to Drive C" that copies the folder onto native app storage.
                    // Games that stream assets over FUSE-backed shared storage stall; from C: they run.
                    // Both this and Executable below describe a Windows install under a drive letter;
                    // a Linux entry has neither, and the client owns where its games live.
                    if (!isLinuxEntry) {
                    val storageLabel = remember(shortcut) { CopyGameToDriveC.storageLabel(shortcut) }
                    val alreadyOnC = remember(shortcut) { CopyGameToDriveC.parse(shortcut).onDriveC }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Storage", fontSize = 14.sp)
                            Text(
                                storageLabel,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // "Move to Drive C" removed from the editor — the same action lives in the
                        // game card's ⋮ menu ("Copy to Drive C…"), which is one tap away.
                    }
                    }

                    // Executable — the .exe this shortcut launches. The "Change…" button was removed
                    // from here: "Change executable…" is already in the game card's ⋮ menu.
                    if (onChangeExe != null && !isLinuxEntry) {
                        val currentExeName = remember(shortcut) {
                            CopyGameToDriveC.parse(shortcut).exeWin.substringAfterLast('\\').substringAfterLast('/')
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Executable", fontSize = 14.sp)
                                Text(
                                    currentExeName.ifEmpty { "Unknown" },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Epic Online Services (EOS) auth toggle — only for Epic-origin shortcuts.
                    // When ON, the launcher injects real-Epic auth args (-EpicPortal + a fresh
                    // exchange code) so EOS-requiring titles authenticate against the user's
                    // logged-in Epic account. Harmless for non-EOS Epic games.
                    if (isEpicShortcut) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Epic Online Services (EOS) auth", fontSize = 14.sp)
                                Text(
                                    "Sign EOS games in with your Epic account",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = epicEosEnabled, onCheckedChange = { epicEosEnabled = it })
                        }
                        // Force the ownership-token (-epicovt) path for Denuvo EOS games
                        // the auto-detector misses (obfuscated exes). Default OFF.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Force Denuvo ownership token", fontSize = 14.sp)
                                Text(
                                    "Force the ownership token for Denuvo games we don't auto-detect",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // Sub-option of EOS auth: only meaningful when EOS auth is on.
                            Switch(checked = epicOvtForce, onCheckedChange = { epicOvtForce = it },
                                   enabled = epicEosEnabled)
                        }
                        // Launch offline: skip the online EOS auth exchange (drops the -AUTH_* args)
                        // but keep the game's Epic identity/portal args. Default OFF.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Launch offline", fontSize = 14.sp)
                                Text(
                                    "Skip Epic sign-in for this game (no online features)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            // Sub-option of EOS auth: offline has nothing to skip when EOS auth is off.
                            Switch(checked = epicOffline, onCheckedChange = { epicOffline = it },
                                   enabled = epicEosEnabled)
                        }
                        // Epic Friends Overlay (Phase 3): provision Epic's real EOS overlay into the
                        // prefix; the game's own EOS SDK renders it on Shift+F3. Default OFF.
                        // Dark-launched: hidden behind FeatureFlags.EPIC_OVERLAY_ENABLED so no shortcut
                        // can be set to epicOverlay=1 through the UI until the DXVK wrapper lands.
                        if (com.winhub.hernandez.FeatureFlags.EPIC_OVERLAY_ENABLED) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Epic Friends Overlay", fontSize = 14.sp)
                                    Text(
                                        "Experimental — in-game Epic overlay on Shift+F3 (a draggable pill " +
                                                "can summon it too). Needs the container to allow full services.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Switch(checked = epicOverlayEnabled, onCheckedChange = { epicOverlayEnabled = it })
                            }
                        }
                    }
                    if (xboxShipsRuntime) XboxRuntimeRow(xboxNexusRuntime) { xboxNexusRuntime = it }

                    // Screen size
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DpDrop(
                            dp, "screenSize",
                            label = stringResource(R.string.screen_size),
                            options = screenSizeEntries,
                            selected = selectedScreenSize,
                            onSelect = { selectedScreenSize = it },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { helpRes = if (isLinuxEntry) R.string.help_linux_screen_size else R.string.help_screen_size }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (selectedScreenSize == "Custom") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DpField(
                                dp, "customW",
                                value = customWidth,
                                onValueChange = { customWidth = it },
                                label = "Width",
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                onRightId = "customH"
                            )
                            DpField(
                                dp, "customH",
                                value = customHeight,
                                onValueChange = { customHeight = it },
                                label = "Height",
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                onLeftId = "customW"
                            )
                        }
                    }

                    // Screen alignment (#413) — per-game override. Sentinel value "" = inherit the
                    // container (Container Default); "0"/"1"/"2" = Center/Top/Bottom. Reuses the same
                    // strings the container editor + in-game drawer show, plus the shared "Use container
                    // default" inherit label used by the refresh dropdown above.
                    run {
                        val saValues = listOf("", "0", "1", "2")
                        val saLabels = listOf(
                            stringResource(R.string.use_container_default),
                            stringResource(R.string.screen_alignment_center),
                            stringResource(R.string.screen_alignment_top),
                            stringResource(R.string.screen_alignment_bottom)
                        )
                        val saIdx = saValues.indexOf(screenAlignment).coerceAtLeast(0)
                        // Applies to the X11 session.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DpDrop(
                                dp, "screenAlignment",
                                label = stringResource(R.string.screen_alignment),
                                options = saLabels,
                                selected = saLabels[saIdx],
                                onSelect = { screenAlignment = saValues[saLabels.indexOf(it)] },
                                modifier = Modifier.weight(1f)
                            )
                            LinuxHelp(isLinuxEntry, R.string.help_linux_screen_alignment) { helpRes = it }
                        }
                    }

                    // Icon
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        iconBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DpButton(dp, "selectIcon", onActivate = { showIconPickMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { showIconPickMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Select Icon")
                                }
                            }
                            DropdownMenu(expanded = showIconPickMenu, onDismissRequest = { showIconPickMenu = false }) {
                                DropdownMenuItem(text = { Text("Browse files") }, onClick = {
                                    showIconPickMenu = false
                                    iconPickerInAppLauncher.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.IMAGES, "Select icon image"))
                                })
                                DropdownMenuItem(text = { Text("Pick via system…") }, onClick = {
                                    showIconPickMenu = false
                                    iconPickerLauncher.launch("image/*")
                                })
                            }
                        }
                        LinuxHelp(isLinuxEntry, R.string.help_linux_icon) { helpRes = it }
                    }

                    // Everything from here to the Frame Generation row is Wine-side rendering: the
                    // Unreal HDR fix swaps DLLs in the prefix, the DX wrapper and its config are the
                    // prefix's D3D translation, and Renderer / render scale / in-game refresh belong to
                    // the X server and its Wine registry keys. A gamescope session has no prefix and no
                    // X server, and the Linux runtime brings its own D3D stack.
                    if (!isLinuxEntry) {

                    // Unreal Engine HDR removed from the editor (useless): the per-game extra and
                    // In-game refresh rate — single per-game override of the container default. Options:
                    // Use container default (inherit) / Locked (60) / <rate> Hz / Unlimited. Drives the
                    // two underlying extras (unlock + cap) together; empty = inherit.
                    run {
                        // panelRates is hoisted to the dialog body (so the D-pad order can see this row).
                        if (panelRates.isNotEmpty()) {
                            // Only rates ABOVE 60 are cap options — "Locked (60)" already covers 60.
                            val ratesAbove60 = panelRates.filter { it > 60 }
                            // Sentinel value model: "" = inherit, "locked" = Locked(60), "0" = Unlimited,
                            // "N" = cap N. Maps to the (unlock, cap) extra pair on select.
                            val rrValues = listOf("", "locked", "0") + ratesAbove60.map { it.toString() }
                            val rrLabels = listOf(
                                stringResource(R.string.use_container_default),
                                stringResource(R.string.in_game_refresh_locked),
                                stringResource(R.string.max_game_refresh_rate_unlimited)) +
                                ratesAbove60.map { "$it Hz" }
                            val currentValue = when {
                                unlockGameRefreshRate.isEmpty() && maxGameRefreshRate.isEmpty() -> ""
                                unlockGameRefreshRate == "0" -> "locked"
                                else -> maxGameRefreshRate.ifEmpty { "0" }
                            }
                            val rrIdx = rrValues.indexOf(currentValue).coerceAtLeast(0)
                            DpDrop(
                                dp, "refresh",
                                label = stringResource(R.string.in_game_refresh_rate),
                                options = rrLabels,
                                selected = rrLabels[rrIdx],
                                onSelect = {
                                    when (val v = rrValues[rrLabels.indexOf(it)]) {
                                        ""       -> { unlockGameRefreshRate = "";  maxGameRefreshRate = "" }
                                        "locked" -> { unlockGameRefreshRate = "0"; maxGameRefreshRate = "0" }
                                        else     -> { unlockGameRefreshRate = "1"; maxGameRefreshRate = v }
                                    }
                                }
                            )
                        }
                    }

                    } // end of the Wine-side rendering rows (see isLinuxEntry above)

                    // Frame Generation engine — per-game override (lsfg grayed without Lossless.dll).
                    run {
                        val fgLabels = listOf(
                            stringResource(R.string.frame_generation_off),
                            stringResource(R.string.frame_generation_lsfg_native)
                        )
                        val fgIdx = fgEngines.indexOf(frameGenEngine).coerceAtLeast(0)
                        // FG's mailbox/present-mode delivery only exists on the Vulkan host renderer, so
                        // gate the whole dropdown on Vulkan (grey it out otherwise) — combined with the
                        // existing lsfg-DLL option gate. See ContainerDetailScreen for the rationale.
                        // A Linux session always presents through the Vulkan compositor, whatever
                        // its entry's renderer field says.
                        val fgVulkan = isLinuxEntry || selectedRenderer == "Vulkan"
                        val fgShown = fgLabels[fgIdx]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpDrop(
                                dp, "frameGen",
                                label = stringResource(R.string.frame_generation),
                                options = fgLabels,
                                selected = fgShown,
                                onSelect = { frameGenEngine = fgEngines[fgLabels.indexOf(it)] },
                                enabled = fgVulkan,
                                disabledOptions = buildSet {
                                    if (!lsfgDllAvailable) add(fgLabels[1])   // LSFG Native — needs an imported Lossless.dll
                                },
                                modifier = (if (!fgVulkan) Modifier.alpha(0.5f) else Modifier).weight(1f)
                            )
                            IconButton(onClick = { helpRes = R.string.help_frame_generation }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                        }
                        if (!fgVulkan) {
                            Text(
                                text = stringResource(R.string.frame_generation_requires_vulkan),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!lsfgDllAvailable) {
                            Text(
                                text = stringResource(R.string.frame_generation_lsfg_needs_dll),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // FPS limiter — per-game override.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DpSwitch(dp, "fpsLimiter", checked = fpsLimiterEnabled, onCheckedChange = { fpsLimiterEnabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.fps_limiter), modifier = Modifier.weight(1f))
                        IconButton(onClick = { helpRes = if (isLinuxEntry) R.string.help_linux_fps_limiter else R.string.help_fps_limiter }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Power-user performance toggles — collapsed into an expandable "Performance" section
                    // (closed by default) so the shortcut dialog stays short. Each toggle is a compact row;
                    // a per-game toggle is only saved when it differs from the App Settings global default.
                    val anyPerfOverride = sustainedPerfMode != com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value ||
                        perfPriorityBoost != com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value ||
                        preferBigCores != com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value ||
                        com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS.any { (rootOverrides[it] ?: false) != com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(it) }
                    val perfOverrideCount = (if (sustainedPerfMode != com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value) 1 else 0) +
                        (if (perfPriorityBoost != com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value) 1 else 0) +
                        (if (preferBigCores != com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value) 1 else 0) +
                        com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS.count { (rootOverrides[it] ?: false) != com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(it) }

                    // Performance — one row opens the full dashboard menu (same as in-game), bound to
                    // this shortcut's saved-per-game toggles. Replaces the old inline toggle list; the
                    // save block below still persists whatever the dialog set (override-when-different).
                    var showPerfDialog by remember { mutableStateOf(false) }
                    // Styled to match the other outlined menu fields (notched "Performance" label +
                    // border + trailing chevron). The read-only field shows the override status; a
                    // transparent overlay Box catches taps so the whole field opens the dashboard
                    // dialog instead of focusing the text field.
                    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = if (perfOverrideCount > 0) "$perfOverrideCount overridden" else "Global defaults",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Performance") },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Open") },
                            // Accent-colored outline so this opener stands out from the grey menu fields.
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.primary,
                                focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                                unfocusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.matchParentSize().clickable { showPerfDialog = true })
                    }

                    if (showPerfDialog) {
                        com.winhub.hernandez.ui.PerformanceDashboardDialogPerGame(
                            sustained = sustainedPerfMode, onSustained = { sustainedPerfMode = it },
                            priority = perfPriorityBoost, onPriority = { perfPriorityBoost = it },
                            bigCores = preferBigCores, onBigCores = { preferBigCores = it },
                            rootValue = { rootOverrides[it] ?: false },
                            onRoot = { k, v -> rootOverrides[k] = v },
                            onResetAll = {
                                sustainedPerfMode = com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value
                                perfPriorityBoost = com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value
                                preferBigCores = com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value
                                for (rk in com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS)
                                    rootOverrides[rk] = com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(rk)
                            },
                            onDismiss = { showPerfDialog = false },
                        )
                    }

                    // Audio driver
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DpDrop(
                            dp, "audio",
                            label = stringResource(R.string.audio_driver),
                            options = audioDriverEntries,
                            selected = selectedAudioDriver,
                            disabledOptions = if (!directAudioSupported && directAudioEntry != null) setOf(directAudioEntry) else emptySet(),
                            onSelect = {
                                selectedAudioDriver = it
                                // DirectAudio is experimental — warn on select (reuses the HelpDialog surface).
                                if (StringUtils.parseIdentifier(it) == "directaudio") helpRes = R.string.directaudio_experimental_warning
                            },
                            modifier = Modifier.weight(1f)
                        )
                        val scAudioId = StringUtils.parseIdentifier(selectedAudioDriver)
                        if (scAudioId == "pulseaudio" || scAudioId == "alsa" || scAudioId == "directaudio") {
                            IconButton(onClick = { showScAudioSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Audio settings", modifier = Modifier.size(18.dp))
                            }
                        }
                        IconButton(onClick = { helpRes = if (isLinuxEntry) R.string.help_linux_audio_driver else R.string.help_audio_driver }) {
                            Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (!directAudioSupported && directAudioEntry != null) {
                        Text(
                            "DirectAudio requires Proton ${com.winhub.hernandez.core.DirectAudioSupport.SUPPORTED_LABEL}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                    if (showScAudioSettings) {
                        AudioSettingsDialog(
                            initial = audioConfigFromEnv(envVarsStr, StringUtils.parseIdentifier(selectedAudioDriver)),
                            scopeLabel = "this game",
                            latencyLive = true,
                            driverLabel = when (StringUtils.parseIdentifier(selectedAudioDriver)) {
                                "alsa" -> "ALSA"; "pulseaudio" -> "PulseAudio"; "directaudio" -> "DirectAudio"
                                else -> StringUtils.parseIdentifier(selectedAudioDriver)
                            },
                            driverId = StringUtils.parseIdentifier(selectedAudioDriver),
                            onDismiss = { showScAudioSettings = false },
                            onSave = { cfg ->
                                envVarsStr = audioConfigToEnv(envVarsStr, cfg, StringUtils.parseIdentifier(selectedAudioDriver))
                                showScAudioSettings = false
                            }
                        )
                    }

                    // Microphone (DirectAudio only). Opt-in per game, default OFF — a knowingly-granted
                    // permission. ON seeds BANNER_AUDIO_DIRECT_MIC=1 into this shortcut's env; the
                    // DirectAudio driver opens the AAudio INPUT stream itself when the flag is present
                    // (the app never records). Greyed off DirectAudio, since only that driver consumes the
                    // flag today. Keyed on the live envVarsStr so it can't capture a stale value and drift.
                    run {
                        val micCtx = LocalContext.current
                        val micDriverActive = StringUtils.parseIdentifier(selectedAudioDriver) == "directaudio" && directAudioSupported
                        val micOn = com.winhub.hernandez.core.DirectAudioSupport.isMicEnabledInEnv(envVarsStr)
                        val micPermLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            if (granted) {
                                envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(envVarsStr, true)
                            } else {
                                envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(envVarsStr, false)
                                Toast.makeText(micCtx, "Microphone permission denied — mic stays off.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                enabled = micDriverActive,
                                checked = micOn && micDriverActive,
                                onCheckedChange = { want ->
                                    if (want) {
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(micCtx, android.Manifest.permission.RECORD_AUDIO)
                                                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(envVarsStr, true)
                                        } else {
                                            micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    } else {
                                        envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(envVarsStr, false)
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Microphone",
                                    color = if (micDriverActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (micDriverActive) "Let games use the mic (DirectAudio captures input)"
                                    else "Available on the DirectAudio driver",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                )
                            }
                            LinuxHelp(isLinuxEntry, R.string.help_linux_microphone) { helpRes = it }
                        }
                    }

                    // Emulator — display-only relabel (see EmulatorLabels). On arm64ec the non-FEX
                    // backend is wowbox64, and on an x86_64 container this picker is inert (the
                    // launcher always runs bin/box64) so the disabled field must read Box64 instead
                    // of the stored "FEXCore" default. What save() persists is unchanged.
                    // It names which x86 backend the Wine launcher runs under, and the Linux runtime
                    // brings its own FEX; the preset it honours is on the Advanced tab.
                    if (!isLinuxEntry) {
                    val emulatorShown =
                        if (archLoaded && !isArm64EC) EmulatorLabels.box64EntryOf(emulatorEntries)
                        else EmulatorLabels.display(selectedEmulator, isArm64EC)
                    DpDrop(
                        dp, "emulator",
                        label = "Emulator",
                        options = EmulatorLabels.options(emulatorEntries, isArm64EC),
                        selected = emulatorShown,
                        onSelect = {
                            selectedEmulator = EmulatorLabels.fromDisplay(it, emulatorEntries, isArm64EC)
                        },
                        enabled = isArm64EC
                    )

                    // MIDI
                    if (midiList.isNotEmpty()) {
                        val midiDisplay = midiList.firstOrNull { it == selectedMidi } ?: midiList.first()
                        DpDrop(
                            dp, "midi",
                            label = "MIDI Sound Font",
                            options = midiList,
                            selected = midiDisplay,
                            onSelect = { selectedMidi = it }
                        )
                    }

                    // LC_ALL — goes into the Wine environment (setupXEnvironment), which the gamescope
                    // path returns before ever building; the session's locale comes from the runtime.
                    DpField(
                        dp, "lcAll",
                        value = lcAll,
                        onValueChange = { lcAll = it },
                        label = "LC_ALL",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    } // end of the Wine launcher rows (Emulator / MIDI / LC_ALL)

                    // Sync (esync / ntsync / fsync / wineserver) — follows the container unless this game
                    // picks otherwise. D-pad: Left/Right step through the runnable modes, A goes back
                    // to the container's.
                    if (!isLinuxEntry) {
                        val runnable = com.winhub.hernandez.core.SyncMode.ALL.filter {
                            syncCaps?.isAvailable(it) ?: (it != com.winhub.hernandez.core.SyncMode.FSYNC && it != com.winhub.hernandez.core.SyncMode.NTSYNC)
                        }
                        SideEffect {
                            dp.actions["syncMode"] = ControlActions(
                                activate = { syncOverride = null },
                                onLeft = { runnable.getOrNull(runnable.indexOf(syncEffective) - 1)?.let(pickSync) },
                                onRight = { runnable.getOrNull(runnable.indexOf(syncEffective) + 1)?.let(pickSync) },
                            )
                        }
                        com.winhub.hernandez.ui.components.SyncModeSelector(
                            selected = syncEffective,
                            caps = syncCaps,
                            helper = when {
                                syncOverrideUnavailable && syncCaps != null ->
                                    com.winhub.hernandez.ui.components.syncSwitchedBackNotice(syncOverride!!, syncLayer, syncCaps!!)
                                syncOverriding -> "$syncEffective for this game only (container: $containerSyncEffective)."
                                else -> "Following the container: $containerSyncEffective."
                            },
                            onPick = pickSync,
                            focused = dp.isFocused("syncMode"),
                            onUseContainer = if (syncOverriding) ({ syncOverride = null }) else null,
                            onHelp = { helpRes = R.string.help_sync_mode },
                            modifier = Modifier.dpadBringIntoView(dp, "syncMode"),
                        )
                    }

                    // Fullscreen aspect-ratio mode (#71) — per-game override. Index 0 = use the
                    // container default; indices 1..5 map to Container.FULLSCREEN_OFF/FIT/STRETCH/FILL/INTEGER.
                    val fsOverrideLabels = listOf(
                        stringResource(R.string.fullscreen_mode_default),
                        stringResource(R.string.fullscreen_mode_off),
                        stringResource(R.string.fullscreen_mode_fit),
                        stringResource(R.string.fullscreen_mode_stretch),
                        stringResource(R.string.fullscreen_mode_fill),
                        stringResource(R.string.fullscreen_mode_integer)
                    )
                    val fsOverrideIdx = if (fullscreenModeOverride < 0) 0 else (fullscreenModeOverride + 1)
                        .coerceIn(1, fsOverrideLabels.size - 1)
                    // Applies to the X11 session.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DpDrop(
                            dp, "fullscreen",
                            label = stringResource(R.string.fullscreen_mode),
                            options = fsOverrideLabels,
                            selected = fsOverrideLabels[fsOverrideIdx],
                            onSelect = { sel ->
                                val idx = fsOverrideLabels.indexOf(sel)
                                fullscreenModeOverride = if (idx <= 0) -1 else idx - 1
                            },
                            modifier = Modifier.weight(1f)
                        )
                        LinuxHelp(isLinuxEntry, R.string.help_linux_fullscreen_mode) { helpRes = it }
                    }

                    // Close the session when this game exits (per-game override; container default is ON).
                    // The watcher that arms it lives past the gamescope early return and watches for a
                    // Windows exe leaving the prefix, so it never fires for a Linux entry.
                    if (!isLinuxEntry) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpCheck(dp, "autoClose", checked = autoCloseOnExit, onCheckedChange = { autoCloseOnExit = it })
                            Text("Close when game exits")
                        }
                    }
                            }
                            4 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Input section
                    SectionBox(title = "Input") {
                        // XInput/DInput, exclusive input and the controller count are how WinHandler
                        // presents pads to a Wine game and what it writes into the prefix's joystick
                        // registry keys. A Linux session's pads arrive as fake-evdev devices instead,
                        // so those rows are conditioned off and the ones that still act on the host
                        // side (touchscreen mode, on-screen-control auto-hide, player slots, gyro) stay.
                        if (!isLinuxEntry) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpSwitch(
                                dp, "enableXInput",
                                checked = enableXInput,
                                onCheckedChange = { enableXInput = it },
                                enabled = exclusiveXInput
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.enable_xinput_for_wine_game), modifier = Modifier.weight(1f))
                            IconButton(onClick = { helpRes = R.string.help_xinput }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpSwitch(
                                dp, "enableDInput",
                                checked = enableDInput,
                                onCheckedChange = { enableDInput = it },
                                enabled = exclusiveXInput
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.enable_dinput_for_wine_game), modifier = Modifier.weight(1f))
                            IconButton(onClick = { helpRes = R.string.help_dinput }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpSwitch(
                                dp, "exclusiveXInput",
                                checked = exclusiveXInput,
                                onCheckedChange = { checked ->
                                    exclusiveXInput = checked
                                    if (!checked) { enableXInput = true; enableDInput = true }
                                    else if (enableXInput && enableDInput) enableDInput = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Exclusive Input", modifier = Modifier.weight(1f))
                            IconButton(onClick = { helpRes = R.string.help_exclusive_xinput }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpCheck(dp, "disableXInput", checked = disabledXInput, onCheckedChange = { disabledXInput = it })
                            Text("Disable XInput")
                        }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpCheck(dp, "simTouch", checked = simTouchScreen, onCheckedChange = { simTouchScreen = it })
                            Text("Touchscreen Mode")
                            LinuxHelp(isLinuxEntry, R.string.help_linux_touchscreen_mode) { helpRes = it }
                        }
                        if (!isLinuxEntry) {
                            DpDrop(
                                dp, "numControllers",
                                label = "Num Controllers",
                                options = numControllersEntries,
                                selected = selectedNumControllers,
                                onSelect = { selectedNumControllers = it }
                            )
                        }

                        // #333 per-game auto-hide override (tri-state): inherit container / On / Off.
                        Spacer(Modifier.height(12.dp))
                        run {
                            val autoHideLabels = listOf("Use container default", "On", "Off")
                            val autoHideIdx = when (autoHideControlsOnPad) { "1" -> 1; "0" -> 2; else -> 0 }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                LabeledDropdown(
                                    label = "Hide on-screen controls when a controller connects",
                                    options = autoHideLabels,
                                    selectedOption = autoHideLabels[autoHideIdx],
                                    onSelect = {
                                        autoHideControlsOnPad = when (autoHideLabels.indexOf(it)) { 1 -> "1"; 2 -> "0"; else -> "" }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                LinuxHelp(isLinuxEntry, R.string.help_linux_autohide_controls) { helpRes = it }
                            }
                        }

                        // Player Slots (per-game override). Empty override = inherit the container's
                        // Player Slots; touching any slot makes this shortcut own the pins. The "Use
                        // container default" button clears the override so it re-inherits. Editing an
                        // empty override starts from an all-auto ("{}") view.
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Player Slots", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            IconButton(onClick = { helpRes = if (isLinuxEntry) R.string.help_linux_player_slots else R.string.help_player_slots }) {
                                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                            }
                            if (controllerSlotOverridesJson.isNotEmpty()) {
                                TextButton(onClick = { controllerSlotOverridesJson = "" }) {
                                    Text("Use container default")
                                }
                            }
                        }
                        if (controllerSlotOverridesJson.isEmpty()) {
                            Text(
                                "Inheriting the container's Player Slots. Pin a controller below to set a per-game override.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PlayerSlotsEditor(
                            savedOverridesJson = controllerSlotOverridesJson.ifEmpty { "{}" },
                            onOverridesChange = { controllerSlotOverridesJson = it },
                        )

                        // Gyro (motion aim) per-game override. Deadzone/smoothing are deliberately
                        // absent — those stay on the container (Container Settings -> Gyro).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DpSwitch(dp, "gyroEnabled", checked = gyroEnabled, onCheckedChange = { gyroEnabled = it })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.gyro_enabled), modifier = Modifier.weight(1f))
                            LinuxHelp(isLinuxEntry, R.string.help_linux_gyro_enabled) { helpRes = it }
                        }
                        if (gyroEnabled) {
                            // Same pairing rule as the container editor: Tilt to Aim and the Mouse
                            // target can't coexist (a held tilt is a constant pointer delta), so each
                            // selection knocks the other back to a working value.
                            val gyroModeLabels = listOf(
                                stringResource(R.string.gyro_mode_rate),
                                stringResource(R.string.gyro_mode_orientation),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DpDrop(
                                    dp, "gyroMode",
                                    label = stringResource(R.string.gyro_mode_label),
                                    options = gyroModeLabels,
                                    selected = gyroModeLabels.getOrElse(gyroMode) {
                                        gyroModeLabels[Container.GYRO_MODE_DEFAULT]
                                    },
                                    onSelect = { opt ->
                                        gyroMode = gyroModeLabels.indexOf(opt).coerceAtLeast(0)
                                        if (gyroMode == Container.GYRO_MODE_ORIENTATION && gyroTarget == Container.GYRO_TARGET_MOUSE)
                                            gyroTarget = Container.GYRO_TARGET_DEFAULT
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { helpRes = R.string.help_gyro_mode }) {
                                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val gyroTargetLabels = listOf(
                                stringResource(R.string.gyro_target_right_stick),
                                stringResource(R.string.gyro_target_left_stick),
                                stringResource(R.string.gyro_target_mouse),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DpDrop(
                                    dp, "gyroTarget",
                                    label = stringResource(R.string.gyro_target_label),
                                    options = gyroTargetLabels,
                                    selected = gyroTargetLabels.getOrElse(gyroTarget) {
                                        gyroTargetLabels[Container.GYRO_TARGET_DEFAULT]
                                    },
                                    onSelect = { opt ->
                                        gyroTarget = gyroTargetLabels.indexOf(opt).coerceAtLeast(0)
                                        if (gyroTarget == Container.GYRO_TARGET_MOUSE)
                                            gyroMode = Container.GYRO_MODE_RATE
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { helpRes = R.string.help_gyro_target }) {
                                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                                }
                            }
                            val gyroActivatorLabels = listOf(
                                stringResource(R.string.gyro_activator_l1),
                                stringResource(R.string.gyro_activator_l2),
                                stringResource(R.string.gyro_activator_r1),
                                stringResource(R.string.gyro_activator_r3),
                                stringResource(R.string.gyro_activator_always),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DpDrop(
                                    dp, "gyroActivator",
                                    label = stringResource(R.string.gyro_activator_label),
                                    options = gyroActivatorLabels,
                                    selected = gyroActivatorLabels.getOrElse(gyroActivator) {
                                        gyroActivatorLabels[Container.GYRO_ACTIVATOR_DEFAULT]
                                    },
                                    onSelect = { opt -> gyroActivator = gyroActivatorLabels.indexOf(opt).coerceAtLeast(0) },
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { helpRes = R.string.help_gyro_activator }) {
                                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                                }
                            }
                            // Hold vs Toggle for that button — hidden under "Always On", which has no
                            // button to latch (same call the container editor makes).
                            if (gyroActivator != Container.GYRO_ACTIVATOR_ALWAYS) {
                                val gyroActivationModeLabels = listOf(
                                    stringResource(R.string.gyro_activation_hold),
                                    stringResource(R.string.gyro_activation_toggle),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DpDrop(
                                        dp, "gyroActivationMode",
                                        label = stringResource(R.string.gyro_activation_mode_label),
                                        options = gyroActivationModeLabels,
                                        selected = gyroActivationModeLabels.getOrElse(gyroActivationMode) {
                                            gyroActivationModeLabels[Container.GYRO_ACTIVATION_MODE_DEFAULT]
                                        },
                                        onSelect = { opt -> gyroActivationMode = gyroActivationModeLabels.indexOf(opt).coerceAtLeast(0) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { helpRes = R.string.help_gyro_activation_mode }) {
                                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${stringResource(R.string.gyro_sensitivity_label)}: ${"%.1f".format(gyroSensitivity)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { helpRes = R.string.help_gyro_sensitivity }) {
                                    Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                                }
                            }
                            DpSlider(
                                dp, "gyroSensitivity",
                                value = gyroSensitivity,
                                onValueChange = { gyroSensitivity = it },
                                valueRange = 0.1f..10f,
                                step = 0.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DpSwitch(dp, "gyroInvertX", checked = gyroInvertX, onCheckedChange = { gyroInvertX = it })
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.gyro_invert_x), modifier = Modifier.weight(1f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DpSwitch(dp, "gyroInvertY", checked = gyroInvertY, onCheckedChange = { gyroInvertY = it })
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.gyro_invert_y), modifier = Modifier.weight(1f))
                            }
                        }
                    }

                            }
                            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RecommendedComponentsSection(
                                container = shortcut.container,
                                exeFile = gameExe,
                                gameDir = gameDir,
                                shortcutBaseName = shortcut.name,
                            )
                            ScWinComponentsTab(winComponents)
                        }
                            2 -> ScEnvVarsTab(envVarsStr, { envVarsStr = it }, gameDir, isLinuxEntry)
                            3 -> ScAdvancedTab(
            shortcut = shortcut,
            onPresetListChanged = {
                box64Presets = Box64PresetManager.getPresets("box64", context)
                fexCorePresets = FEXCorePresetManager.getPresets(context)
            },
            isArm64EC = isArm64EC,
            isLinuxEntry = isLinuxEntry,
            box64Versions = box64Versions,
            selectedBox64Version = selectedBox64Version,
            onBox64VersionChange = { selectedBox64Version = it },
            box64Presets = box64Presets,
            selectedBox64PresetIndex = selectedBox64PresetIndex,
            onBox64PresetIndexChange = { selectedBox64PresetIndex = it },
            fexCoreVersions = fexCoreVersions,
            selectedFexCoreVersion = selectedFexCoreVersion,
            onFexVersionChange = { selectedFexCoreVersion = it },
            fexCorePresets = fexCorePresets,
            selectedFexPresetIndex = selectedFexCorePresetIndex,
            onFexPresetIndexChange = { selectedFexCorePresetIndex = it },
            controlsProfiles = controlsProfiles,
            selectedControlsProfileIndex = selectedControlsProfileIndex,
            onControlsProfileChange = { selectedControlsProfileIndex = it },
            startupSelectionEntries = startupSelectionEntries,
            selectedStartupSelection = selectedStartupSelection,
            onStartupChange = { selectedStartupSelection = it },
            startupServicesEnabled = startupServicesEnabled,
            onStartupServiceToggle = { raw, on ->
                startupServicesEnabled =
                    if (on) startupServicesEnabled + raw else startupServicesEnabled - raw
            },
            cpuListViewRef = cpuListViewRef,
            initialCpuList = shortcut.getExtra("cpuList", shortcut.container.getCPUList(true)),
            onCpuListSnapshot = { shortcut.putExtra("cpuList", it) },
            sharpnessEffectEntries = sharpnessEffectEntries,
            selectedSharpnessEffect = selectedSharpnessEffect,
            onSharpnessEffectChange = { selectedSharpnessEffect = it },
            sharpnessLevel = sharpnessLevel,
            onSharpnessLevelChange = { sharpnessLevel = it },
            sharpnessDenoise = sharpnessDenoise,
            onSharpnessDenoiseChange = { sharpnessDenoise = it },
            reshadeLoadout = reshadeLoadout,
            reshadeEffects = reshadeEffects,
            onReshadeCatalogChanged = {
                reshadeEffects = ReshadeManager.scanEffects(context)
                reshadeLoadout.reconcile(reshadeEffects)
            },
            reshadeSupported = StringUtils.parseIdentifier(selectedDxWrapper).let { it.contains("dxvk") || it.contains("vegas") },
            onShowBox64DownloadSheet = { showBox64DownloadSheet = true },
            onShowFexCoreDownloadSheet = { showFexCoreDownloadSheet = true }
        )
                            5 -> ScTvTab(
                                dp = dp,
                                display = tvDisplay,
                                // This game's own render resolution, for the "instead of…" subtitle —
                                // taken from the LIVE General-tab state, not the saved extra, so the
                                // two tabs can't disagree while the dialog is open.
                                gameScreenSize = if (selectedScreenSize == "Custom") "${customWidth}x${customHeight}"
                                                 else StringUtils.parseIdentifier(selectedScreenSize),
                                tvLaunch = tvLaunch,
                                onTvLaunchChange = { tvLaunch = it },
                                tvModeId = tvModeId,
                                onTvModeIdChange = { tvModeId = it },
                                tvMatchRes = tvMatchRes,
                                onTvMatchResChange = { tvMatchRes = it },
                            )
                        }
                    }
                }

                // ── Responsive tab layout ────────────────────────────────────────────────────────
                // Portrait: the tab strip is pinned across the TOP as a text-only uppercase strip
                // (no icons — mirrors the container editor's top tab bar via the shared RailTopTabs).
                // Landscape: the shared collapsible left rail
                // beside the content. Left/Right on the focused "tabs" node still switches tabs for
                // D-pad/controller users in both orientations, and L1/R1 switch from anywhere.
                val railState = rememberRailState("shortcut")
                val railItems = tabTitles.mapIndexed { position, tab ->
                    val contentIndex = tabIndices[position]
                    RailItem(tab, shortcutTabIcon(tab), contentIndex == selectedTab) { selectedTab = contentIndex }
                }
                // The newcomer glossary link moved to the drawer: Community → Help (more room there).
                val railLinks = emptyList<RailLink>()
                if (isPortrait) {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Left/Right and L1/R1 step through the strip, so DpTabs works in POSITIONS.
                        DpTabs(dp, "tabs", selected = tabIndices.indexOf(selectedTab).coerceAtLeast(0),
                               count = tabTitles.size, onSelect = { selectedTab = tabIndices[it] }) {
                            RailTopTabs(items = railItems, links = railLinks)
                        }
                        // Weighted so the internally-scrolling content takes exactly the space left
                        // between the pinned tab strip and the pinned footer (never overflows it).
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { mainContent() }
                    }
                } else {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DpTabs(dp, "tabs", selected = tabIndices.indexOf(selectedTab).coerceAtLeast(0),
                               count = tabTitles.size, onSelect = { selectedTab = tabIndices[it] }) {
                            CollapsibleRail(
                                state = railState,
                                title = shortcut.name,
                                links = railLinks,
                                sections = listOf(RailSection(header = null, items = railItems)),
                            )
                        }
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) { mainContent() }
                    }
                }

                Divider(color = DividerColor)
                // Compact footer — reduced padding + shrunk button height so the OK/Cancel bar
                // matches the slimmed title bar above and frees vertical room for content.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DpButton(dp, "cancel", onActivate = onDismiss, onRightId = "ok") {
                        TextButton(
                            onClick = onDismiss,
                            // Definite height overrides TextButton's internal 40dp defaultMinSize
                            // floor (which heightIn(min=…) can't lower), so the footer bar is genuinely
                            // short — roughly matching the slim title bar at the top.
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) { Text(stringResource(android.R.string.cancel)) }
                    }
                    Spacer(Modifier.width(8.dp))
                    DpButton(dp, "ok", onActivate = { save(); onDismiss() }, onLeftId = "cancel") {
                        TextButton(
                            onClick = { save(); onDismiss() },
                            // Definite height overrides TextButton's internal 40dp defaultMinSize
                            // floor (which heightIn(min=…) can't lower), so the footer bar is genuinely
                            // short — roughly matching the slim title bar at the top.
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) { Text(stringResource(android.R.string.ok)) }
                    }
                }
            }
        }

    // Config dialogs + download sheets are composed INSIDE the settings Dialog's window so the
    // ModalBottomSheet (ContentDownloadSheet) renders on top of it. When they were outside the
    // Dialog, the bottom sheet's window appeared BEHIND the settings dialog (couldn't be used).
    if (showGfxConfig) {
        GraphicsDriverConfigDialog(
            graphicsDriver = StringUtils.parseIdentifier(selectedGfxDriver),
            initialConfig = graphicsDriverConfig,
            onConfirm = { graphicsDriverConfig = it; showGfxConfig = false },
            onDismiss = { showGfxConfig = false }
        )
    }
    val isVegasCfg = StringUtils.parseIdentifier(selectedDxWrapper).contains("vegas")
    // See ContainerDetailScreen: relax the #113 DXVK-2.x filter only for the Mali "Wrapper + compat
    // + bcn" driver, so per-game shortcuts can also reach the DXVK 1.10.3 workaround (#137).
    val relaxDxvkFilter = StringUtils.parseIdentifier(selectedGfxDriver) == "wrapper-compat-bcn"
    if (showDxvkConfig) {
        DxvkConfigDialog(
            isArm64EC = isArm64EC,
            isVegas = isVegasCfg,
            relaxDxvkFilter = relaxDxvkFilter,
            initialConfig = dxWrapperConfig,
            onConfirm = { dxWrapperConfig = it; showDxvkConfig = false },
            onDismiss = { showDxvkConfig = false },
            onDownloadDxvk = { showDxvkConfig = false; if (isVegasCfg) showVegasDownloadSheet = true else showDxvkDownloadSheet = true },
            onDownloadVkd3d = { showDxvkConfig = false; showVkd3dDownloadSheet = true },
            onDownloadD7vk = { showDxvkConfig = false; showD7vkDownloadSheet = true }
        )
    }
    if (showWineD3DConfig) {
        WineD3DConfigDialog(
            initialConfig = dxWrapperConfig,
            onConfirm = { dxWrapperConfig = it; showWineD3DConfig = false },
            onDismiss = { showWineD3DConfig = false }
        )
    }

    // Per-field "?" help + newcomer glossary — composed INSIDE the settings Dialog's window (like the
    // config dialogs above) so HelpDialog / the glossary ModalBottomSheet render on top of it.
    helpRes?.let { HelpDialog(it) { helpRes = null } }
    helpText?.let { HelpTextDialog(it) { helpText = null } }
    if (showWrapperManager) WrapperManagerDialog(onDismiss = {
        showWrapperManager = false
        wrapperRefreshKey++ // pick up a just-imported/deleted wrapper
    })
    glossaryQuery?.let { ContainerGlossarySheet(initialQuery = it, onDismiss = { glossaryQuery = null }) }

    if (showBox64DownloadSheet) {
        // Arch-match the download sheet to the selector above it: arm64ec containers use WOWBox64,
        // everything else Box64 (mirrors ContainerDetailScreen). Was hardcoded to Box64, so the
        // "download more" button on an arm64ec shortcut opened the wrong content type.
        ContentDownloadSheet(
            contentType = if (isArm64EC)
                com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
            else
                com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            onDismiss = { showBox64DownloadSheet = false },
            onContentChanged = {}
        )
    }
    if (showFexCoreDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            onDismiss = { showFexCoreDownloadSheet = false },
            onContentChanged = {}
        )
    }
    if (showDxvkDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_DXVK,
            onDismiss = { showDxvkDownloadSheet = false },
            onContentChanged = {}
        )
    }
    if (showVkd3dDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
            onDismiss = { showVkd3dDownloadSheet = false },
            onContentChanged = {}
        )
    }
    if (showD7vkDownloadSheet) {
        ContentDownloadSheet(
            contentType = com.winhub.hernandez.contents.ContentProfile.ContentType.CONTENT_TYPE_D7VK,
            onDismiss = { showD7vkDownloadSheet = false },
            onContentChanged = {}
        )
    }
    if (showVegasDownloadSheet) {
        VegasDownloadSheet(
            onDismiss = { showVegasDownloadSheet = false },
            onContentChanged = {}
        )
    }
    } // settings Dialog
}

/**
 * Deck-native, full-screen replacement for [ShortcutSettingsDialogScreen]. Same stored fields and the
 * same save() semantics (the full ~70 shortcut extras), but rendered in the Deck style (DeckPalette /
 * DeckGroup / DeckChoiceRow / DeckToggle / DeckTextRow) so the per-game editor matches the rest of
 * Deck mode instead of the classic Material dialog.
 *
 * The heavy sub-editors are reused verbatim (ScWinComponentsTab, ScEnvVarsTab, ScAdvancedTab, ScTvTab,
 * PlayerSlotsEditor and every config/help/audio dialog), so no setting is lost.
 */
@Composable
internal fun DeckShortcutSettingsScreen(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val res = context.resources

    val isEpicShortcut = remember { shortcut.getExtra("storeSource") == "epic" }
    val isLinuxEntry = false

    // Launch method is owned by the launch pipeline (DeckLaunchStack), which reads these two extras.
    // Exposed here so a remembered choice can be changed without deleting the shortcut.
    var launchMode by remember { mutableStateOf(shortcut.getExtra("launchMode", "")) }
    var launchModeRemembered by remember { mutableStateOf(shortcut.getExtra("launchModeRemembered", "") == "1") }
    var showLaunchMethod by remember { mutableStateOf(false) }

    var epicEosEnabled by remember { mutableStateOf(shortcut.getExtra("epicEos", "1") != "0") }
    var epicOvtForce by remember { mutableStateOf(shortcut.getExtra("epicOvtForce", "0") == "1") }
    var epicOffline by remember { mutableStateOf(shortcut.getExtra("epicOffline", "0") == "1") }
    var epicOverlayEnabled by remember { mutableStateOf(shortcut.getExtra("epicOverlay", "0") == "1") }
    // Xbox game whose install ships Microsoft's xgameruntime.dll: use the app's (Xodus Nexus) instead. Default ON.
    val xboxShipsRuntime = remember {
        shortcut.getExtra("storeSource") == "xbox" && shortcut.getExtra("xboxProductId").let { id ->
            !id.isNullOrEmpty() && runCatching { com.winhub.hernandez.store.xbox.XboxRuntime.shipsOwnRuntime(context, id) }.getOrDefault(false)
        }
    }
    var xboxNexusRuntime by remember { mutableStateOf(shortcut.getExtra(com.winhub.hernandez.store.xbox.XboxRuntime.NEXUS_RUNTIME_EXTRA, "1") != "0") }

    var isArm64EC by remember { mutableStateOf(false) }
    var archLoaded by remember { mutableStateOf(false) }
    var box64Versions by remember { mutableStateOf(listOf<String>()) }
    var box64Presets by remember { mutableStateOf(listOf<Box64Preset>()) }
    var fexCoreVersions by remember { mutableStateOf(listOf<String>()) }
    var fexCorePresets by remember { mutableStateOf(listOf<FEXCorePreset>()) }
    var controlsProfiles by remember { mutableStateOf(listOf<ControlsProfile>()) }
    var midiList by remember { mutableStateOf(listOf<String>()) }

    val screenSizeEntries = remember { res.getStringArray(R.array.screen_size_entries).toList() }
    val rawScreenSize = remember { shortcut.getExtra("screenSize", shortcut.container.getScreenSize()) }
    var selectedScreenSize by remember {
        val display = screenSizeEntries.firstOrNull {
            StringUtils.parseIdentifier(it).equals(rawScreenSize, ignoreCase = true)
        }
        mutableStateOf(display ?: "Custom")
    }
    var customWidth by remember {
        mutableStateOf(if (rawScreenSize.contains("x")) rawScreenSize.substringBefore("x") else "800")
    }
    var customHeight by remember {
        mutableStateOf(if (rawScreenSize.contains("x")) rawScreenSize.substringAfter("x") else "600")
    }
    var screenAlignment by remember { mutableStateOf(shortcut.getExtra("screenAlignment", "")) }

    var wrapperRefreshKey by remember { mutableStateOf(0) }
    val graphicsDriverEntries = remember(wrapperRefreshKey) {
        WrapperManager.driverEntries(context, res.getStringArray(R.array.graphics_driver_entries))
    }
    var selectedGfxDriver by remember {
        val id = shortcut.getExtra("graphicsDriver", shortcut.container.graphicsDriver)
        mutableStateOf(graphicsDriverEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: graphicsDriverEntries.firstOrNull() ?: id)
    }
    var graphicsDriverConfig by remember {
        mutableStateOf(shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()))
    }

    val dxWrapperEntries = remember { res.getStringArray(R.array.dxwrapper_entries).toList() }
    var selectedDxWrapper by remember {
        val id = shortcut.getExtra("dxwrapper", shortcut.container.getDXWrapper())
        mutableStateOf(dxWrapperEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: dxWrapperEntries.firstOrNull() ?: id)
    }
    var dxWrapperConfig by remember {
        mutableStateOf(shortcut.getExtra("dxwrapperConfig", shortcut.container.getDXWrapperConfig()))
    }

    var selectedRenderer by remember {
        val id = shortcut.getExtra("renderer", shortcut.container.renderer)
        mutableStateOf(when (id.lowercase()) {
            "egl" -> "EGL"
            "surfacedirect" -> "SurfaceDirect"
            else -> "Vulkan"
        })
    }
    var unrealHdrOverride by remember { mutableStateOf(com.winhub.hernandez.core.UnrealHdr.shortcutChoice(shortcut)) }

    var vkNative by remember {
        mutableStateOf(shortcut.getExtra("native",
            if (shortcut.container.isRendererNative()) "true" else "false") == "true")
    }
    var vkSwapRB by remember {
        mutableStateOf(shortcut.getExtra("swapRB",
            if (shortcut.container.getRendererSwapRB()) "true" else "false") == "true")
    }
    var vkPresentMode by remember {
        mutableStateOf(shortcut.getExtra("presentMode", shortcut.container.getRendererPresentMode()))
    }
    var vkRendererDriverId by remember {
        mutableStateOf(shortcut.getExtra("rendererDriverId", shortcut.container.getRendererDriverId()))
    }
    var renderScale by remember {
        mutableStateOf(shortcut.getExtra("renderScale", shortcut.container.getExtra("renderScale", "1.0")))
    }
    var maxGameRefreshRate by remember { mutableStateOf(shortcut.getExtra("maxGameRefreshRate", "")) }
    var unlockGameRefreshRate by remember { mutableStateOf(shortcut.getExtra("unlockGameRefreshRate", "")) }

    val fgEngines = remember { listOf("off", "lsfg-native") }
    var frameGenEngine by remember {
        mutableStateOf(shortcut.getExtra("frameGenEngine", shortcut.container.frameGenEngine)
            .let { if (it == "bionic" || it == "lsfg") "lsfg-native" else it }
            .let { if (it == "off") shortcut.container.frameGenEngine else it })
    }
    val lsfgDllAvailable = remember { File(context.filesDir, "lsfg-vk/Lossless.dll").isFile }

    var fpsLimiterEnabled by remember {
        mutableStateOf(
            shortcut.getExtra("fpsLimiterEnabled", if (shortcut.container.isFpsLimiterEnabled) "1" else "0") == "1"
        )
    }
    var sustainedPerfMode by remember {
        mutableStateOf(shortcut.getExtra("sustainedPerfMode",
            if (com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value) "1" else "0") == "1")
    }
    var perfPriorityBoost by remember {
        mutableStateOf(shortcut.getExtra("perfPriorityBoost",
            if (com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value) "1" else "0") == "1")
    }
    var preferBigCores by remember {
        mutableStateOf(shortcut.getExtra("preferBigCores",
            if (com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value) "1" else "0") == "1")
    }
    val rootOverrides = remember {
        mutableStateMapOf<String, Boolean>().apply {
            for (k in com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS)
                put(k, shortcut.getExtra(k, if (com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(k)) "1" else "0") == "1")
        }
    }

    val audioDriverEntries = remember { res.getStringArray(R.array.audio_driver_entries).toList() }
    val directAudioSupported = remember {
        isLinuxEntry || com.winhub.hernandez.core.DirectAudioSupport.isSupported(shortcut.container.wineVersion)
    }
    val directAudioEntry = remember {
        audioDriverEntries.firstOrNull { StringUtils.parseIdentifier(it) == "directaudio" }
    }
    var selectedAudioDriver by remember {
        var id = shortcut.getExtra("audioDriver", shortcut.container.audioDriver)
        if (id == "directaudio" && !directAudioSupported) id = Container.DEFAULT_AUDIO_DRIVER
        mutableStateOf(audioDriverEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: audioDriverEntries.firstOrNull() ?: id)
    }

    val emulatorEntries = remember { res.getStringArray(R.array.emulator_entries).toList() }
    var selectedEmulator by remember {
        val id = shortcut.getExtra("emulator", shortcut.container.emulator) ?: ""
        mutableStateOf(emulatorEntries.firstOrNull { StringUtils.parseIdentifier(it) == id }
            ?: emulatorEntries.firstOrNull() ?: id)
    }

    var selectedMidi by remember {
        mutableStateOf(shortcut.getExtra("midiSoundFont", shortcut.container.getMIDISoundFont()))
    }

    var name by remember { mutableStateOf(shortcut.name) }
    var execArgs by remember { mutableStateOf(shortcut.getExtra("execArgs")) }
    var lcAll by remember { mutableStateOf(shortcut.getExtra("lc_all", shortcut.container.getLC_ALL())) }

    var fullscreenModeOverride by remember {
        mutableStateOf(
            run {
                val m = shortcut.getExtra("fullscreenMode")
                when {
                    m.isNotEmpty() -> m.toIntOrNull() ?: -1
                    shortcut.getExtra("fullscreenStretched", "") == "1" -> Container.FULLSCREEN_STRETCH
                    else -> -1
                }
            }
        )
    }
    var autoCloseOnExit by remember {
        mutableStateOf(shortcut.getExtra("autoCloseOnExit", shortcut.container.getExtra("autoCloseOnExit", "1")) == "1")
    }
    var exclusiveXInput by remember {
        val v = shortcut.getExtra("exclusiveXInput")
        mutableStateOf(if (v.isEmpty()) shortcut.container.isExclusiveXInput else v == "1")
    }
    val initialInputType = remember {
        shortcut.getExtra("inputType", shortcut.container.getInputType().toString()).toIntOrNull()
            ?: shortcut.container.getInputType()
    }
    var enableXInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0) }
    var enableDInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0) }
    var disabledXInput by remember { mutableStateOf(shortcut.getExtra("disableXinput", "0") == "1") }
    var simTouchScreen by remember { mutableStateOf(shortcut.getExtra("simTouchScreen", "0") == "1") }
    var controllerSlotOverridesJson by remember { mutableStateOf(shortcut.getExtra("controllerSlotOverrides", "")) }
    var autoHideControlsOnPad by remember { mutableStateOf(shortcut.getExtra("autoHideControlsOnPad", "")) }

    val numControllersEntries = remember { res.getStringArray(R.array.num_controllers_entries).toList() }
    var selectedNumControllers by remember {
        val n = (shortcut.getExtra("numControllers", "1").toIntOrNull() ?: 1).coerceIn(1, numControllersEntries.size)
        mutableStateOf(numControllersEntries.getOrElse(n - 1) { numControllersEntries.first() })
    }

    var selectedBox64Version by remember {
        mutableStateOf(shortcut.getExtra("box64Version", shortcut.container.getBox64Version()) ?: "")
    }
    var selectedBox64PresetIndex by remember { mutableIntStateOf(0) }
    var selectedFexCoreVersion by remember {
        mutableStateOf(shortcut.getExtra("fexcoreVersion", shortcut.container.getFEXCoreVersion()) ?: "")
    }
    var selectedFexCorePresetIndex by remember { mutableIntStateOf(0) }
    var selectedControlsProfileIndex by remember { mutableIntStateOf(0) }

    val startupSelectionEntries = remember { res.getStringArray(R.array.startup_selection_entries).toList() }
    var selectedStartupSelection by remember {
        val idx = (shortcut.getExtra("startupSelection", shortcut.container.getStartupSelection().toString())
            .toIntOrNull() ?: 0).coerceIn(0, startupSelectionEntries.lastIndex)
        mutableStateOf(startupSelectionEntries.getOrElse(idx) { startupSelectionEntries.first() })
    }
    var startupServicesEnabled by remember {
        mutableStateOf(
            WineUtils.parseStartupServicesCsv(
                shortcut.getExtra("startupServices", shortcut.container.startupServices)
            ).toSet()
        )
    }

    val sharpnessEffectEntries = remember { res.getStringArray(R.array.vkbasalt_sharpness_entries).toList() }
    var selectedSharpnessEffect by remember {
        val v = shortcut.getExtra("sharpnessEffect", "None")
        mutableStateOf(sharpnessEffectEntries.firstOrNull { it == v } ?: sharpnessEffectEntries.firstOrNull() ?: v)
    }
    var sharpnessLevel by remember {
        mutableIntStateOf(shortcut.getExtra("sharpnessLevel", "100").toIntOrNull() ?: 100)
    }
    var sharpnessDenoise by remember {
        mutableIntStateOf(shortcut.getExtra("sharpnessDenoise", "100").toIntOrNull() ?: 100)
    }

    var reshadeEffects by remember { mutableStateOf(ReshadeManager.scanEffects(context)) }
    val reshadeLoadout = remember { ReshadeLoadoutState() }
    LaunchedEffect(Unit) {
        reshadeLoadout.init(
            reshadeEffects,
            shortcut.getExtra("reshadeLoadout", shortcut.container.getReshadeLoadout()).ifEmpty { null },
            shortcut.getExtra("reshadeMode", shortcut.container.getReshadeMode()),
            shortcut.getExtra("reshadeParams", shortcut.container.getReshadeParams()).ifEmpty { null },
            shortcut.getExtra("reshadeEffect", shortcut.container.getReshadeEffect()),
        )
    }

    val winComponents = remember {
        val raw = shortcut.getExtra("wincomponents", shortcut.container.getWinComponents())
        mutableStateListOf<WinComponentEntry>().also { list ->
            for (parts in KeyValueSet(raw)) {
                val key = parts[0]; val idx = parts[1].toIntOrNull() ?: 0
                val resId = res.getIdentifier(key, "string", context.packageName)
                val label = if (resId != 0) res.getString(resId) else key
                list.add(WinComponentEntry(key, idx, label))
            }
        }
    }

    // Sync per-game override (extra "syncMode"; core.SyncSupport). null = follow the container. The
    // stored value is the extra, else what this game's own env string said before the selector
    // existed (WINEESYNC=0 → wineserver, WINENTSYNC=1 → ntsync, WINEESYNC=1 → esync). Greyed against
    // the layer the game runs on — the container's (a shortcut has no layer of its own); the probe
    // reads ntdll.so off-main and is cached per layer, so it's keyed on the wine version.
    val syncLayer = shortcut.container.wineVersion ?: ""
    val containerSyncStored = remember(shortcut.container) {
        com.winhub.hernandez.core.SyncSupport.storedMode(
            shortcut.container.getExtra(com.winhub.hernandez.core.SyncMode.EXTRA, ""), shortcut.container.envVars)
    }
    var syncCaps by remember(syncLayer) { mutableStateOf(com.winhub.hernandez.core.SyncSupport.peek(syncLayer)) }
    LaunchedEffect(syncLayer) {
        syncCaps = withContext(Dispatchers.IO) {
            com.winhub.hernandez.core.SyncSupport.capsFor(context, null, syncLayer)
        }
    }
    var syncOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.SyncSupport.storedMode(
            shortcut.getExtra(com.winhub.hernandez.core.SyncMode.EXTRA, ""), shortcut.getExtra("envVars")))
    }
    val containerSyncEffective = syncCaps?.resolve(containerSyncStored)
        ?: containerSyncStored ?: com.winhub.hernandez.core.SyncMode.ESYNC
    val syncEffective = syncOverride?.let { o -> syncCaps?.resolve(o) ?: o } ?: containerSyncEffective
    val syncOverriding = syncOverride != null && syncEffective != containerSyncEffective
    val syncOverrideUnavailable = syncOverride != null && syncCaps?.isAvailable(syncOverride!!) == false
    val pickSync: (String) -> Unit = { m ->
        if (syncCaps?.isAvailable(m) != false && com.winhub.hernandez.core.SyncMode.normalize(m) != null)
            syncOverride = if (m == containerSyncEffective) null else m
    }
    // Fast OpenGL per-game override (extra "fastOpenGL"; core.FastOpenGL): "" = follow the container,
    // "1" / "0" = this game only. Locked with one reason when the game's driver isn't a Turnip or the
    // APK has no X11 GL set. Any layer works: the launch picks the EGL or GLX route.
    var fastGlOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.FastOpenGL.normalize(
            shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA)) ?: "")
    }
    val fastGlDriverVersion = com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
        .getVersion(graphicsDriverConfig) ?: ""
    val fastGlDriverOk = remember(fastGlDriverVersion) {
        com.winhub.hernandez.core.FastOpenGL.driverUsable(context, fastGlDriverVersion)
    }
    val fastGlBundled = remember { com.winhub.hernandez.core.X11Egl.isBundled(context) }
    val fastGlLocked = com.winhub.hernandez.core.FastOpenGL.unavailableReason(fastGlDriverOk, fastGlBundled)
    // Show OpenGL FPS / OpenGL vsync off per-game overrides (extras "glFpsHud" / "glVsyncOff"): same
    // "" / "1" / "0" as Fast OpenGL, never locked.
    var glFpsHudOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.FastOpenGL.normalize(
            shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.FPS_HUD.extra)) ?: "")
    }
    var glVsyncOffOverride by remember {
        mutableStateOf(com.winhub.hernandez.core.FastOpenGL.normalize(
            shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF.extra)) ?: "")
    }
    // The sync variables left the env string for the Sync selector (read into syncOverride above),
    // so they're dropped here and on save.
    var envVarsStr by remember {
        mutableStateOf(com.winhub.hernandez.core.SyncSupport.stripSyncVars(shortcut.getExtra("envVars")))
    }
    var showScAudioSettings by remember { mutableStateOf(false) }
    val gameDir = remember(shortcut) {
        runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path)?.parentFile }.getOrNull()
    }
    val gameExe = remember(shortcut) {
        runCatching { WinePath.resolveAndroidPath(shortcut.container, shortcut.path) }.getOrNull()
    }

    val cpuListViewRef = remember { mutableStateOf<CPUListView?>(null) }
    var iconBitmap by remember { mutableStateOf<Bitmap?>(shortcut.icon) }

    var showGfxConfig by remember { mutableStateOf(false) }
    var showWrapperManager by remember { mutableStateOf(false) }
    var showDxvkConfig by remember { mutableStateOf(false) }
    var showWineD3DConfig by remember { mutableStateOf(false) }
    var helpRes by remember { mutableStateOf<Int?>(null) }
    var helpText by remember { mutableStateOf<String?>(null) }
    var glossaryQuery by remember { mutableStateOf<String?>(null) }
    var showBox64DownloadSheet by remember { mutableStateOf(false) }
    var showFexCoreDownloadSheet by remember { mutableStateOf(false) }
    var showDxvkDownloadSheet by remember { mutableStateOf(false) }
    var showVkd3dDownloadSheet by remember { mutableStateOf(false) }
    var showD7vkDownloadSheet by remember { mutableStateOf(false) }
    var showPerfDialog by remember { mutableStateOf(false) }
    var showIconPickMenu by remember { mutableStateOf(false) }
    var dxvkRefreshKey by remember { mutableStateOf(0) }
    var showStockConfigSheet by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Mods tab: programs started with the game, in order, with a wait each.
    val startPrograms = remember { StartProgramsState(shortcut.getExtra(com.winhub.hernandez.core.StartPrograms.EXTRA, "")) }
    val gameMods = rememberGameModsSetting(shortcut)
    var tvLaunch by remember { mutableStateOf(com.winhub.hernandez.display.ExternalDisplay.launchOnTv(shortcut)) }
    val tvLaunchStored = remember { com.winhub.hernandez.display.ExternalDisplay.launchOnTv(shortcut) }
    var tvModeId by remember { mutableStateOf(com.winhub.hernandez.display.ExternalDisplay.modeId(shortcut)) }
    var tvMatchRes by remember { mutableStateOf(com.winhub.hernandez.display.ExternalDisplay.matchResolution(shortcut)) }
    var tvDisplayTick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { tvDisplayTick++ }
            override fun onDisplayRemoved(displayId: Int) { tvDisplayTick++ }
            override fun onDisplayChanged(displayId: Int) { tvDisplayTick++ }
        }
        runCatching { dm?.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper())) }
        onDispose { runCatching { dm?.unregisterDisplayListener(listener) } }
    }
    val tvDisplay = remember(tvDisplayTick) { com.winhub.hernandez.display.ExternalDisplay.find(context) }

    var gyroEnabled by remember {
        mutableStateOf(shortcut.getExtra("gyroEnabled", if (shortcut.container.isGyroEnabled) "1" else "0") == "1")
    }
    var gyroTarget by remember {
        mutableStateOf(shortcut.getExtra("gyroTarget", shortcut.container.gyroTarget.toString()).toIntOrNull() ?: Container.GYRO_TARGET_DEFAULT)
    }
    var gyroActivator by remember {
        mutableStateOf(shortcut.getExtra("gyroActivator", shortcut.container.gyroActivator.toString()).toIntOrNull() ?: Container.GYRO_ACTIVATOR_DEFAULT)
    }
    var gyroActivationMode by remember {
        mutableStateOf(shortcut.getExtra("gyroActivationMode", shortcut.container.gyroActivationMode.toString()).toIntOrNull() ?: Container.GYRO_ACTIVATION_MODE_DEFAULT)
    }
    var gyroMode by remember {
        mutableStateOf(shortcut.getExtra("gyroMode", shortcut.container.gyroMode.toString()).toIntOrNull() ?: Container.GYRO_MODE_DEFAULT)
    }
    var gyroSensitivity by remember {
        mutableStateOf(shortcut.getExtra("gyroSensitivity", shortcut.container.gyroSensitivity.toString()).toFloatOrNull() ?: Container.GYRO_SENSITIVITY_DEFAULT)
    }
    var gyroInvertX by remember {
        mutableStateOf(shortcut.getExtra("gyroInvertX", if (shortcut.container.isGyroInvertX) "1" else "0") == "1")
    }
    var gyroInvertY by remember {
        mutableStateOf(shortcut.getExtra("gyroInvertY", if (shortcut.container.isGyroInvertY) "1" else "0") == "1")
    }

    fun applyIconFromUri(uri: Uri) {
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching
            shortcut.iconFile?.let { f ->
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
            shortcut.icon = bitmap
            iconBitmap = bitmap
        }
    }
    val iconPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { applyIconFromUri(it) }
    }
    val iconPickerInAppLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedUri(result.data)?.let { applyIconFromUri(it) }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cm = ContentsManager(context)
            cm.syncContents()
            val wineInfo = WineInfo.fromIdentifier(context, cm, shortcut.container.wineVersion)
            val arm64ec = wineInfo.isArm64EC()

            val b64Type = if (arm64ec) ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                          else ContentProfile.ContentType.CONTENT_TYPE_BOX64
            val b64Arr = if (arm64ec) res.getStringArray(R.array.wowbox64_version_entries).toMutableList()
                         else res.getStringArray(R.array.box64_version_entries).toMutableList()
            for (p in cm.getProfiles(b64Type)) {
                val n = ContentsManager.getEntryName(p)
                b64Arr.add(n.substring(n.indexOf('-') + 1))
            }

            val fexList = res.getStringArray(R.array.fexcore_version_entries).toMutableList()
            for (p in cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)) {
                val n = ContentsManager.getEntryName(p)
                fexList.add(n.substring(n.indexOf('-') + 1))
            }

            val b64Presets = Box64PresetManager.getPresets("box64", context)
            val fexPresets = FEXCorePresetManager.getPresets(context)
            val profiles = InputControlsManager(context).getProfiles(true)

            val midi = mutableListOf("-- ${context.getString(R.string.disabled)} --", MidiManager.DEFAULT_SF2_FILE)
            val sfDir = File(context.filesDir, MidiManager.SF_DIR)
            if (sfDir.exists()) sfDir.listFiles()?.forEach { midi.add(it.name) }

            withContext(Dispatchers.Main) {
                isArm64EC = arm64ec
                archLoaded = true
                box64Versions = b64Arr
                fexCoreVersions = fexList
                box64Presets = b64Presets
                fexCorePresets = fexPresets
                controlsProfiles = profiles
                midiList = midi

                val b64Id = shortcut.getExtra("box64Preset", shortcut.container.getBox64Preset())
                selectedBox64PresetIndex = b64Presets.indexOfFirst { it.id == b64Id }.coerceAtLeast(0)

                val fexId = shortcut.getExtra("fexcorePreset", shortcut.container.getFEXCorePreset())
                selectedFexCorePresetIndex = fexPresets.indexOfFirst { it.id == fexId }.coerceAtLeast(0)

                val cpId = shortcut.getExtra("controlsProfile", "0").toIntOrNull() ?: 0
                selectedControlsProfileIndex = if (cpId == 0) 0
                    else profiles.indexOfFirst { it.id == cpId }.let { if (it >= 0) it + 1 else 0 }

                if (selectedBox64Version.isEmpty()) selectedBox64Version = b64Arr.firstOrNull() ?: ""
            }
        }
    }

    fun save() {
        val newName = name.trim()
        if (newName.isNotEmpty() && newName != shortcut.name) {
            renameShortcut(shortcut, newName)
        }

        val screenSize = if (selectedScreenSize == "Custom") {
            val w = customWidth.trim(); val h = customHeight.trim()
            if (w.matches(Regex("[0-9]+")) && h.matches(Regex("[0-9]+"))) {
                val wi = w.toInt(); val hi = h.toInt()
                if (wi % 2 == 0 && hi % 2 == 0) "${wi}x${hi}" else Container.DEFAULT_SCREEN_SIZE
            } else Container.DEFAULT_SCREEN_SIZE
        } else {
            StringUtils.parseIdentifier(selectedScreenSize)
        }

        var finalInputType = 0
        if (enableXInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        if (enableDInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()

        val wincomps = winComponents.joinToString(",") { "${it.key}=${it.selectedIndex}" }
        val envVars = com.winhub.hernandez.core.SyncSupport.stripSyncVars(envVarsStr)
        val cpuList = cpuListViewRef.value?.getCheckedCPUListAsString() ?: shortcut.getExtra("cpuList", shortcut.container.getCPUList(true))

        val b64PresetId = box64Presets.getOrElse(selectedBox64PresetIndex) { null }?.id ?: Box64Preset.COMPATIBILITY
        val fexPresetId = fexCorePresets.getOrElse(selectedFexCorePresetIndex) { null }?.id ?: FEXCorePreset.COMPATIBILITY
        val ctrlProfileId = if (selectedControlsProfileIndex == 0) 0
            else controlsProfiles.getOrElse(selectedControlsProfileIndex - 1) { null }?.id ?: 0

        val midiVal = if (midiList.isNotEmpty() && selectedMidi == midiList.firstOrNull()) "" else selectedMidi
        val startupIdx = startupSelectionEntries.indexOf(selectedStartupSelection).coerceAtLeast(0)
        val numCtrl = (numControllersEntries.indexOf(selectedNumControllers) + 1).coerceAtLeast(1)

        with(shortcut) {
            putExtra(com.winhub.hernandez.core.StartPrograms.EXTRA, startPrograms.encoded())
            gameMods.write { key, value -> putExtra(key, value) }
            putExtra("execArgs", execArgs.ifEmpty { null })
            if (isEpicShortcut) putExtra("epicEos", if (epicEosEnabled) "1" else "0")
            if (isEpicShortcut) putExtra("epicOvtForce", if (epicOvtForce) "1" else "0")
            if (isEpicShortcut) putExtra("epicOffline", if (epicOffline) "1" else "0")
            if (isEpicShortcut) putExtra("epicOverlay", if (epicOverlayEnabled) "1" else "0")
            if (xboxShipsRuntime) putExtra(com.winhub.hernandez.store.xbox.XboxRuntime.NEXUS_RUNTIME_EXTRA, if (xboxNexusRuntime) "1" else "0")
            putExtra("screenSize", screenSize)
            putExtra("screenAlignment", screenAlignment.ifEmpty { null })
            putExtra("graphicsDriver", StringUtils.parseIdentifier(selectedGfxDriver))
            putExtra("graphicsDriverConfig", graphicsDriverConfig)
            putExtra(com.winhub.hernandez.core.UnrealHdr.EXTRA, unrealHdrOverride.ifEmpty { null })
            putExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_LAUNCH, if (tvLaunch) "1" else "0")
            putExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_MODE_ID, tvModeId.toString())
            putExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_MATCH_RES, if (tvMatchRes) "1" else "0")
            putExtra("renderer", StringUtils.parseIdentifier(selectedRenderer))
            putExtra("gyroEnabled", if (gyroEnabled) "1" else "0")
            putExtra("gyroTarget", gyroTarget.toString())
            putExtra("gyroActivator", gyroActivator.toString())
            putExtra("gyroActivationMode", gyroActivationMode.toString())
            putExtra("gyroMode", gyroMode.toString())
            putExtra("gyroSensitivity", gyroSensitivity.toString())
            putExtra("gyroInvertX", if (gyroInvertX) "1" else "0")
            putExtra("gyroInvertY", if (gyroInvertY) "1" else "0")
            putExtra("native", if (vkNative) "true" else "false")
            putExtra("swapRB", if (vkSwapRB) "true" else "false")
            putExtra("presentMode", vkPresentMode)
            putExtra("rendererDriverId", vkRendererDriverId)
            putExtra("renderScale", if (renderScale == "1.0") null else renderScale)
            putExtra("maxGameRefreshRate", maxGameRefreshRate.ifEmpty { null })
            putExtra("unlockGameRefreshRate", unlockGameRefreshRate.ifEmpty { null })
            if (frameGenEngine != "off" && frameGenEngine != shortcut.container.frameGenEngine) {
                putExtra("frameGenEngine", frameGenEngine)
            } else {
                putExtra("frameGenEngine", null)
            }
            putExtra("frameGenEngineExplicit", null)
            putExtra("fpsLimiterEnabled", if (fpsLimiterEnabled) "1" else "0")
            putExtra("sustainedPerfMode", perfExtraOrNull(sustainedPerfMode, com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value))
            putExtra("perfPriorityBoost", perfExtraOrNull(perfPriorityBoost, com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value))
            putExtra("preferBigCores", perfExtraOrNull(preferBigCores, com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value))
            for (rk in com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS)
                putExtra(rk, perfExtraOrNull(rootOverrides[rk] ?: false, com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(rk)))
            putExtra("dxwrapper", StringUtils.parseIdentifier(selectedDxWrapper))
            putExtra("dxwrapperConfig", dxWrapperConfig)
            putExtra("audioDriver", StringUtils.parseIdentifier(selectedAudioDriver).let {
                if (it == "directaudio" && !directAudioSupported) Container.DEFAULT_AUDIO_DRIVER else it
            })
            putExtra("emulator", StringUtils.parseIdentifier(selectedEmulator))
            putExtra("midiSoundFont", midiVal.ifEmpty { null })
            putExtra("lc_all", lcAll)
            putExtra("fullscreenMode", if (fullscreenModeOverride < 0) null else fullscreenModeOverride.toString())
            putExtra("fullscreenStretched", null)
            putExtra("autoCloseOnExit", if (autoCloseOnExit) "1" else "0")
            putExtra("inputType", finalInputType.toString())
            putExtra("exclusiveXInput", if (exclusiveXInput) "1" else "0")
            putExtra("disableXinput", if (disabledXInput) "1" else null)
            putExtra("simTouchScreen", if (simTouchScreen) "1" else "0")
            putExtra("controllerSlotOverrides", controllerSlotOverridesJson.ifEmpty { null })
            putExtra("autoHideControlsOnPad", autoHideControlsOnPad.ifEmpty { null })
            putExtra("numControllers", numCtrl.toString())
            putExtra("box64Version", selectedBox64Version)
            putExtra("box64Preset", b64PresetId)
            putExtra("fexcoreVersion", selectedFexCoreVersion)
            putExtra("fexcorePreset", fexPresetId)
            putExtra("controlsProfile", if (ctrlProfileId > 0) ctrlProfileId.toString() else null)
            putExtra("startupSelection", startupIdx.toString())
            putExtra("startupServices", startupServicesEnabled.joinToString(","))
            putExtra("sharpnessEffect", selectedSharpnessEffect)
            putExtra("sharpnessLevel", sharpnessLevel.toString())
            putExtra("sharpnessDenoise", sharpnessDenoise.toString())
            putExtra("reshadeLoadout", reshadeLoadout.loadoutJsonOrNull())
            putExtra("reshadeMode", reshadeLoadout.mode)
            putExtra("reshadeParams", reshadeLoadout.paramsJsonOrNull())
            putExtra("reshadeEffect", reshadeLoadout.firstEffectName())
            putExtra("wincomponents", wincomps)
            putExtra("envVars", envVars.ifEmpty { null })
            // Sync override: only a pick that differs from the container's is stored (null clears the
            // extra = follow the container). A pick the layer can't run saves as what it resolves to.
            putExtra(com.winhub.hernandez.core.SyncMode.EXTRA,
                syncOverride?.let { o -> syncCaps?.resolve(o) ?: o }?.takeIf { it != containerSyncEffective })
            // OpenGL overrides: "" clears the extra (follow the container). Kept as picked while Fast is
            // locked, so a later driver change brings the choice back.
            putExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA, fastGlOverride.ifEmpty { null })
            putExtra(com.winhub.hernandez.core.FastOpenGL.FPS_HUD.extra, glFpsHudOverride.ifEmpty { null })
            putExtra(com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF.extra, glVsyncOffOverride.ifEmpty { null })
            putExtra("cpuList", cpuList)
            saveData()
        }
    }

    val panelRates = remember {
        com.winhub.hernandez.widget.XServerView.getSupportedRefreshRates(
            if (android.os.Build.VERSION.SDK_INT >= 30) context.display
            else (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay)
    }

    val tvTabVisible = tvDisplay != null || tvLaunchStored || tvLaunch
    val tabIndices = remember(tvTabVisible) {
        listOf(0, 6, 1, 2, 3, 4, 7) + (if (tvTabVisible) listOf(5) else emptyList())
    }
    val tabTitles = remember(tabIndices) {
        tabIndices.map {
            when (it) {
                0 -> "General"; 1 -> "Win Components"; 2 -> "Env Vars"
                3 -> "Advanced"; 4 -> "Controller"; 6 -> "Graphics"; 7 -> "Mods"; else -> "TV"
            }
        }
    }
    LaunchedEffect(tabIndices) { if (selectedTab !in tabIndices) selectedTab = tabIndices.first() }

    // Category-navigation layout — the SAME setting the container editor honours
    // (Settings → Appearance → Container Editor Layout): "tabs" | "rail" | "pages".
    // Portrait ignores the preference: tabs always run across the top as a Deck strip ("portrait").
    val portraitDeck = LocalDeckPortrait.current
    val layoutPref = remember(portraitDeck) {
        if (portraitDeck) "portrait" else PreferenceManager.getDefaultSharedPreferences(context)
            .getString("container_editor_layout", "rail")?.takeIf { it != "tabs" } ?: "rail"
    }
    // "pages" layout: whether a category page is open (false = the category list page).
    var pagesCategoryOpen by remember { mutableStateOf(false) }

    // "popup" = config editors open in a DeckConfigDialog (the original shortcut style);
    // "dropdown" = the classic container style, inline under the row. Shared with the container
    // editor through the same preference.
    val configEditorStyle = remember {
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString("config_editor_style", "popup") ?: "popup"
    }
    var configSection by remember { mutableStateOf<String?>(null) }

    Surface(color = DeckPalette.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (layoutPref == "pages" && pagesCategoryOpen) pagesCategoryOpen = false
                    else onDismiss()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = DeckPalette.onBackground)
                }
                Text(
                    shortcut.name,
                    color = DeckPalette.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                DeckButton("Save") { save(); onDismiss() }
            }

            if (layoutPref == "portrait") {
                DeckPortraitTabStrip(
                    labels = tabTitles,
                    icons = tabTitles.map { shortcutTabIcon(it) },
                    selected = tabIndices.indexOf(selectedTab).coerceAtLeast(0),
                    onSelect = { selectedTab = tabIndices[it] },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (layoutPref == "tabs") {
                DeckStretchyTabs(
                    labels = tabTitles,
                    selected = tabIndices.indexOf(selectedTab).coerceAtLeast(0),
                    onSelect = { selectedTab = tabIndices[it] },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    scroll = true,
                    fillWidth = false,
                    focusable = true,
                )
            }

            if (layoutPref == "pages" && !pagesCategoryOpen) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tabIndices.forEachIndexed { i, id ->
                        DeckEditorCategoryRow(
                            label = tabTitles[i],
                            selected = id == selectedTab,
                            onClick = { selectedTab = id; pagesCategoryOpen = true },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (layoutPref == "rail") {
                    DeckEditorCategoryRail(
                        labels = tabTitles,
                        selected = tabIndices.indexOf(selectedTab).coerceAtLeast(0),
                        onSelect = { selectedTab = tabIndices[it] },
                        modifier = Modifier
                            .width(200.dp)
                            .fillMaxHeight()
                            .padding(start = 16.dp),
                    )
                }
                // A tab switch removes the focused control with the old page: put the pad on the new
                // page's first control (top-left in focus order) instead of a lost focus.
                val tabContentFocus = remember { FocusRequester() }
                var firstTabShown by remember { mutableStateOf(true) }
                LaunchedEffect(selectedTab, pagesCategoryOpen) {
                    if (firstTabShown) { firstTabShown = false; return@LaunchedEffect }
                    kotlinx.coroutines.delay(80)
                    runCatching { tabContentFocus.requestFocus() }
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(tabContentFocus).focusGroup()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (selectedTab) {
                        0 -> {
                            DeckGroup("Game") {
                                DeckTextRow("Name", value = name, onValueChange = { name = it })
                                if (!isLinuxEntry) {
                                    DeckTextRow("Exec arguments", value = execArgs, onValueChange = { execArgs = it })
                                }
                                DeckRow(
                                    label = "Launch method",
                                    hint = if (!launchModeRemembered) "Ask every launch" else when (launchMode) {
                                        "RealSteam" -> "SteamLite (real Steam client)"
                                        "Goldberg" -> "Goldberg (offline emulator)"
                                        "Raw" -> "Raw .exe (no Steam layer)"
                                        else -> "Ask every launch"
                                    },
                                    configPanel = true,
                                    onConfigClick = { showLaunchMethod = true },
                                ) {}
                                val storageLabel = remember(shortcut) { CopyGameToDriveC.storageLabel(shortcut) }
                                DeckRow("Storage", hint = storageLabel) {
                                    val exeName = remember(shortcut) {
                                        CopyGameToDriveC.parse(shortcut).exeWin.substringAfterLast('\\').substringAfterLast('/')
                                    }
                                    Text(exeName.ifEmpty { "Unknown" }, color = DeckPalette.onSurfaceVariant, fontSize = 12.5.sp)
                                }
                                DeckRow("Icon") {
                                    DeckButton("Select Icon", primary = false) { showIconPickMenu = true }
                                    Box {
                                        DropdownMenu(expanded = showIconPickMenu, onDismissRequest = { showIconPickMenu = false }) {
                                            DropdownMenuItem(text = { Text("Browse files") }, onClick = {
                                                showIconPickMenu = false
                                                iconPickerInAppLauncher.launch(InAppFilePicker.buildIntent(context, InAppFilePicker.IMAGES, "Select icon image"))
                                            })
                                            DropdownMenuItem(text = { Text("Pick via system…") }, onClick = {
                                                showIconPickMenu = false
                                                iconPickerLauncher.launch("image/*")
                                            })
                                        }
                                    }
                                }
                                iconBitmap?.let { bmp ->
                                    DeckRow("Current icon") {
                                        Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
                                    }
                                }
                            }
                            if (isEpicShortcut) {
                                DeckGroup("Epic Online Services") {
                                    DeckRow("EOS auth", hint = "Sign EOS games in with your Epic account") {
                                        DeckToggle(checked = epicEosEnabled, onChange = { epicEosEnabled = it })
                                    }
                                    DeckRow("Force Denuvo ownership token", hint = "For Denuvo games we don't auto-detect") {
                                        DeckToggle(checked = epicOvtForce && epicEosEnabled, onChange = { epicOvtForce = it })
                                    }
                                    DeckRow("Launch offline", hint = "Skip Epic sign-in for this game") {
                                        DeckToggle(checked = epicOffline && epicEosEnabled, onChange = { epicOffline = it })
                                    }
                                    if (com.winhub.hernandez.FeatureFlags.EPIC_OVERLAY_ENABLED) {
                                        DeckRow("Epic Friends Overlay", hint = "Experimental in-game overlay on Shift+F3") {
                                            DeckToggle(checked = epicOverlayEnabled, onChange = { epicOverlayEnabled = it })
                                        }
                                    }
                                }
                            }
                            if (xboxShipsRuntime) {
                                DeckGroup("Xbox") {
                                    DeckRow("Use the app's Xbox runtime", hint = "This game ships Microsoft's own Xbox runtime, which needs Windows' Gaming Services. Turn off only to try the game's own.") {
                                        DeckToggle(checked = xboxNexusRuntime, onChange = { xboxNexusRuntime = it })
                                    }
                                }
                            }
                            DeckGroup("Display") {
                                DeckChoiceRow(
                                    label = "Screen size",
                                    options = screenSizeEntries.map { it to it },
                                    selected = selectedScreenSize,
                                    onPick = { selectedScreenSize = it },
                                )
                                if (selectedScreenSize == "Custom") {
                                    DeckTextRow("Width", value = customWidth, onValueChange = { customWidth = it }, width = 140.dp)
                                    DeckTextRow("Height", value = customHeight, onValueChange = { customHeight = it }, width = 140.dp)
                                }
                                run {
                                    val saValues = listOf("", "0", "1", "2")
                                    val saLabels = listOf(
                                        stringResource(R.string.use_container_default),
                                        stringResource(R.string.screen_alignment_center),
                                        stringResource(R.string.screen_alignment_top),
                                        stringResource(R.string.screen_alignment_bottom),
                                    )
                                    DeckChoiceRow(
                                        label = stringResource(R.string.screen_alignment),
                                        options = saValues.zip(saLabels),
                                        selected = saValues.getOrElse(saValues.indexOf(screenAlignment).coerceAtLeast(0)) { "" },
                                        onPick = { screenAlignment = it },
                                    )
                                }
                                if (!isLinuxEntry && panelRates.isNotEmpty()) {
                                    val ratesAbove60 = panelRates.filter { it > 60 }
                                    val rrValues = listOf("", "locked", "0") + ratesAbove60.map { it.toString() }
                                    val rrLabels = listOf(
                                        stringResource(R.string.use_container_default),
                                        stringResource(R.string.in_game_refresh_locked),
                                        stringResource(R.string.max_game_refresh_rate_unlimited),
                                    ) + ratesAbove60.map { "$it Hz" }
                                    val currentValue = when {
                                        unlockGameRefreshRate.isEmpty() && maxGameRefreshRate.isEmpty() -> ""
                                        unlockGameRefreshRate == "0" -> "locked"
                                        else -> maxGameRefreshRate.ifEmpty { "0" }
                                    }
                                    DeckChoiceRow(
                                        label = stringResource(R.string.in_game_refresh_rate),
                                        options = rrValues.zip(rrLabels),
                                        selected = rrValues.getOrElse(rrValues.indexOf(currentValue).coerceAtLeast(0)) { "" },
                                        onPick = { v ->
                                            when (v) {
                                                "" -> { unlockGameRefreshRate = ""; maxGameRefreshRate = "" }
                                                "locked" -> { unlockGameRefreshRate = "0"; maxGameRefreshRate = "0" }
                                                else -> { unlockGameRefreshRate = "1"; maxGameRefreshRate = v }
                                            }
                                        },
                                    )
                                }
                                val fsOverrideLabels = listOf(
                                    stringResource(R.string.fullscreen_mode_default),
                                    stringResource(R.string.fullscreen_mode_off),
                                    stringResource(R.string.fullscreen_mode_fit),
                                    stringResource(R.string.fullscreen_mode_stretch),
                                    stringResource(R.string.fullscreen_mode_fill),
                                    stringResource(R.string.fullscreen_mode_integer),
                                )
                                val fsOverrideIdx = if (fullscreenModeOverride < 0) 0
                                    else (fullscreenModeOverride + 1).coerceIn(1, fsOverrideLabels.size - 1)
                                DeckChoiceRow(
                                    label = stringResource(R.string.fullscreen_mode),
                                    options = fsOverrideLabels.indices.toList().map { it.toString() to fsOverrideLabels[it] },
                                    selected = fsOverrideIdx.toString(),
                                    onPick = { sel ->
                                        val idx = sel.toIntOrNull() ?: 0
                                        fullscreenModeOverride = if (idx <= 0) -1 else idx - 1
                                    },
                                )
                                if (!isLinuxEntry) {
                                    DeckRow("Close when game exits") {
                                        DeckToggle(checked = autoCloseOnExit, onChange = { autoCloseOnExit = it })
                                    }
                                }
                            }
                            DeckGroup("Frame Generation") {
                                val fgLabels = listOf(
                                    stringResource(R.string.frame_generation_off),
                                    stringResource(R.string.frame_generation_lsfg_native),
                                )
                                val fgVulkan = isLinuxEntry || selectedRenderer == "Vulkan"
                                DeckChoiceRow(
                                    label = stringResource(R.string.frame_generation),
                                    hint = if (!fgVulkan) stringResource(R.string.frame_generation_requires_vulkan)
                                           else if (!lsfgDllAvailable) stringResource(R.string.frame_generation_lsfg_needs_dll)
                                           else null,
                                    options = fgEngines.zip(fgLabels),
                                    selected = frameGenEngine,
                                    onPick = { frameGenEngine = it },
                                )
                                DeckRow(stringResource(R.string.fps_limiter)) {
                                    DeckToggle(checked = fpsLimiterEnabled, onChange = { fpsLimiterEnabled = it })
                                }
                                run {
                                    val perfOverrideCount =
                                        (if (sustainedPerfMode != com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value) 1 else 0) +
                                        (if (perfPriorityBoost != com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value) 1 else 0) +
                                        (if (preferBigCores != com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value) 1 else 0) +
                                        com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS.count { (rootOverrides[it] ?: false) != com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(it) }
                                    DeckRow(
                                        label = "Performance",
                                        hint = if (perfOverrideCount > 0) "$perfOverrideCount overridden" else "Global defaults",
                                    ) {
                                        DeckButton("Configure", primary = false) { showPerfDialog = true }
                                    }
                                }
                            }
                            DeckGroup("Audio") {
                                DeckChoiceRow(
                                    label = stringResource(R.string.audio_driver),
                                    hint = if (!directAudioSupported && directAudioEntry != null)
                                        "DirectAudio requires Proton ${com.winhub.hernandez.core.DirectAudioSupport.SUPPORTED_LABEL}" else null,
                                    options = audioDriverEntries.map { it to it },
                                    selected = selectedAudioDriver,
                                    onPick = { selectedAudioDriver = it },
                                )
                                val scAudioId = StringUtils.parseIdentifier(selectedAudioDriver)
                                if (scAudioId == "pulseaudio" || scAudioId == "alsa" || scAudioId == "directaudio") {
                                    DeckRow("Audio settings") {
                                        DeckButton("Configure", primary = false) { showScAudioSettings = true }
                                    }
                                }
                                run {
                                    val micDriverActive = scAudioId == "directaudio" && directAudioSupported
                                    val micOn = com.winhub.hernandez.core.DirectAudioSupport.isMicEnabledInEnv(envVarsStr)
                                    val micPermLauncher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.RequestPermission()
                                    ) { granted ->
                                        envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(envVarsStr, granted)
                                        if (!granted) Toast.makeText(context, "Microphone permission denied — mic stays off.", Toast.LENGTH_SHORT).show()
                                    }
                                    DeckRow(
                                        label = "Microphone",
                                        hint = if (micDriverActive) "Let games use the mic (DirectAudio captures input)"
                                               else "Available on the DirectAudio driver",
                                    ) {
                                        DeckToggle(checked = micOn && micDriverActive, onChange = { want ->
                                            if (micDriverActive) {
                                                if (want) {
                                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                                                            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                        envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(envVarsStr, true)
                                                    } else {
                                                        micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                    }
                                                } else {
                                                    envVarsStr = com.winhub.hernandez.core.DirectAudioSupport.withMicEnabled(envVarsStr, false)
                                                }
                                            }
                                        })
                                    }
                                }
                            }
                            if (!isLinuxEntry) {
                                DeckGroup("Launcher") {
                                    val emulatorShown =
                                        if (archLoaded && !isArm64EC) EmulatorLabels.box64EntryOf(emulatorEntries)
                                        else EmulatorLabels.display(selectedEmulator, isArm64EC)
                                    DeckChoiceRow(
                                        label = "Emulator",
                                        options = EmulatorLabels.options(emulatorEntries, isArm64EC).map { it to it },
                                        selected = emulatorShown,
                                        onPick = { selectedEmulator = EmulatorLabels.fromDisplay(it, emulatorEntries, isArm64EC) },
                                    )
                                    if (midiList.isNotEmpty()) {
                                        DeckChoiceRow(
                                            label = "MIDI Sound Font",
                                            options = midiList.map { it to it },
                                            selected = midiList.firstOrNull { it == selectedMidi } ?: midiList.first(),
                                            onPick = { selectedMidi = it },
                                        )
                                    }
                                    DeckTextRow("LC_ALL", value = lcAll, onValueChange = { lcAll = it })
                                    // Sync: follows the container unless this game picks otherwise. Only
                                    // modes the layer can run are listed (fsync never is on Android).
                                    DeckChoiceRow(
                                        label = "Sync",
                                        hint = when {
                                            syncOverrideUnavailable && syncCaps != null ->
                                                com.winhub.hernandez.ui.components.syncSwitchedBackNotice(syncOverride!!, syncLayer, syncCaps!!)
                                            syncOverriding -> "$syncEffective for this game only (container: $containerSyncEffective)."
                                            else -> "Following the container: $containerSyncEffective."
                                        },
                                        options = listOf("" to "Container default ($containerSyncEffective)") +
                                            com.winhub.hernandez.core.SyncMode.ALL
                                                .filter {
                                                    it != com.winhub.hernandez.core.SyncMode.FSYNC &&
                                                        (syncCaps?.isAvailable(it) ?: (it != com.winhub.hernandez.core.SyncMode.NTSYNC))
                                                }
                                                .map { it to it },
                                        selected = if (syncOverriding) syncEffective else "",
                                        onPick = { if (it.isEmpty()) syncOverride = null else pickSync(it) },
                                    )
                                }
                            }
                        }
                        6 -> {
                            DeckGroup("Graphics") {
                                DeckChoiceRow(
                                    label = stringResource(R.string.graphics_driver),
                                    options = graphicsDriverEntries.map { it to it },
                                    selected = selectedGfxDriver,
                                    configPanel = configEditorStyle != "dropdown",
                                    onConfigClick = if (configEditorStyle == "dropdown") null else ({ showGfxConfig = true }),
                                    onPick = { selectedGfxDriver = it },
                                )
                                if (!isLinuxEntry) {
                                    if (configEditorStyle == "dropdown") {
                                        DeckExpandableRow(
                                            label = "Driver Config",
                                            hint = GraphicsDriverConfigDialog.getVersion(graphicsDriverConfig),
                                            expanded = configSection == "gfxDriver",
                                            onToggle = { configSection = if (configSection == "gfxDriver") null else "gfxDriver" },
                                        ) {
                                            DeckGraphicsDriverConfigEditor(
                                                graphicsDriver = StringUtils.parseIdentifier(selectedGfxDriver),
                                                initialConfig = graphicsDriverConfig,
                                                onConfirm = { graphicsDriverConfig = it; configSection = null },
                                                onChange = { graphicsDriverConfig = it },
                                            )
                                        }
                                    } else {
                                        DeckRow(
                                            label = stringResource(R.string.graphics_driver),
                                            hint = GraphicsDriverConfigDialog.getVersion(graphicsDriverConfig),
                                        ) {
                                            DeckButton("Configure", primary = false) { showGfxConfig = true }
                                        }
                                    }
                                    DeckRow("Wrapper Manager", hint = stringResource(R.string.wrapper_manager_open)) {
                                        DeckButton("Manage", primary = false) { showWrapperManager = true }
                                    }
                                    DeckChoiceRow(
                                        label = stringResource(R.string.dxwrapper),
                                        options = dxWrapperEntries.map { it to it },
                                        selected = selectedDxWrapper,
                                        configPanel = configEditorStyle != "dropdown",
                                        onConfigClick = if (configEditorStyle == "dropdown") null else ({
                                            val w = StringUtils.parseIdentifier(selectedDxWrapper)
                                            if (w.contains("dxvk") || w.contains("vegas")) showDxvkConfig = true else showWineD3DConfig = true
                                        }),
                                        onPick = { newWrapper ->
                                            val wasVegas = StringUtils.parseIdentifier(selectedDxWrapper).contains("vegas")
                                            val isVegas = StringUtils.parseIdentifier(newWrapper).contains("vegas")
                                            selectedDxWrapper = newWrapper
                                            if (wasVegas && !isVegas) {
                                                val cfg = DXVKConfigDialog.parseConfig(dxWrapperConfig)
                                                if (cfg.get("dxvkConfigFile").isNotEmpty()) {
                                                    dxWrapperConfig = dxWrapperConfig.split(",").filter { !it.startsWith("dxvkConfigFile=") }.joinToString(",")
                                                }
                                            }
                                        },
                                    )
                                    if (configEditorStyle == "dropdown") {
                                        val wrapperIsDxvk = StringUtils.parseIdentifier(selectedDxWrapper)
                                            .let { it.contains("dxvk") || it.contains("vegas") }
                                        DeckExpandableRow(
                                            label = "DX Wrapper Config",
                                            hint = if (wrapperIsDxvk) "DXVK / VKD3D tuning" else "WineD3D tuning",
                                            expanded = configSection == "dxWrapper",
                                            onToggle = { configSection = if (configSection == "dxWrapper") null else "dxWrapper" },
                                        ) {
                                            if (wrapperIsDxvk) {
                                                val isVegasCfg = StringUtils.parseIdentifier(selectedDxWrapper).contains("vegas")
                                                DeckDxvkConfigEditor(
                                                    isArm64EC = isArm64EC,
                                                    isVegas = isVegasCfg,
                                                    containerRootDir = shortcut.container.rootDir,
                                                    relaxDxvkFilter = StringUtils.parseIdentifier(selectedGfxDriver) == "wrapper-compat-bcn",
                                                    initialConfig = dxWrapperConfig,
                                                    onConfirm = { dxWrapperConfig = it; configSection = null },
                                                    onChange = { dxWrapperConfig = it },
                                                    onOpenConfigDownload = if (isVegasCfg) { { showStockConfigSheet = true } } else null,
                                                    refreshKey = dxvkRefreshKey,
                                                    onDownloadDxvk = { showDxvkDownloadSheet = true },
                                                    onDownloadVkd3d = { showVkd3dDownloadSheet = true },
                                                    onDownloadD7vk = { showD7vkDownloadSheet = true },
                                                )
                                            } else {
                                                DeckWineD3DConfigEditor(
                                                    initialConfig = dxWrapperConfig,
                                                    onConfirm = { dxWrapperConfig = it; configSection = null },
                                                    onChange = { dxWrapperConfig = it },
                                                )
                                            }
                                        }
                                    } else {
                                        DeckRow("DX Wrapper Config") {
                                            DeckButton("Configure", primary = false) {
                                                val w = StringUtils.parseIdentifier(selectedDxWrapper)
                                                if (w.contains("dxvk") || w.contains("vegas")) showDxvkConfig = true else showWineD3DConfig = true
                                            }
                                        }
                                    }
                                    DeckChoiceRow(
                                        label = stringResource(R.string.renderer),
                                        options = listOf("Vulkan" to "Vulkan"),
                                        selected = selectedRenderer,
                                        onPick = { selectedRenderer = it },
                                    )
                                    if (selectedRenderer == "Vulkan") {
                                        DeckRow(stringResource(R.string.renderer_native)) {
                                            DeckToggle(checked = vkNative, onChange = { vkNative = it })
                                        }
                                        DeckChoiceRow(
                                            label = stringResource(R.string.renderer_colors),
                                            options = listOf("BGRA", "RGBA").map { it to it },
                                            selected = if (vkSwapRB) "RGBA" else "BGRA",
                                            onPick = { vkSwapRB = (it == "RGBA") },
                                        )
                                        run {
                                            val vkPmValues = listOf("fifo", "mailbox", "immediate")
                                            val vkPmLabels = listOf(
                                                stringResource(R.string.renderer_present_mode_fifo),
                                                stringResource(R.string.renderer_present_mode_mailbox),
                                                stringResource(R.string.renderer_present_mode_immediate),
                                            )
                                            DeckChoiceRow(
                                                label = stringResource(R.string.renderer_present_mode),
                                                options = vkPmValues.zip(vkPmLabels),
                                                selected = vkPresentMode,
                                                onPick = { vkPresentMode = it },
                                            )
                                        }
                                        val vkDriverOptions = remember {
                                            val installed = try {
                                                com.winhub.hernandez.contents.AdrenotoolsManager(context).enumarateInstalledDrivers()
                                            } catch (e: Exception) { arrayListOf<String>() }
                                            (listOf("system") + installed).distinct()
                                        }
                                        DeckChoiceRow(
                                            label = stringResource(R.string.renderer_driver_id),
                                            options = vkDriverOptions.map { it to it },
                                            selected = if (vkDriverOptions.contains(vkRendererDriverId)) vkRendererDriverId else "system",
                                            onPick = { vkRendererDriverId = it },
                                        )
                                        if (frameGenEngine != "off") {
                                            Text(
                                                stringResource(R.string.renderer_present_mode_fg_note),
                                                color = DeckPalette.onSurfaceVariant,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                    run {
                                        val renderScaleValues = listOf("1.0", "1.25", "1.5", "2.0")
                                        val renderScaleLabels = listOf("Off", "1.25x", "1.5x", "2x")
                                        DeckChoiceRow(
                                            label = "Render scale (supersampling)",
                                            options = renderScaleValues.zip(renderScaleLabels),
                                            selected = renderScale,
                                            onPick = { renderScale = it },
                                        )
                                    }
                                }
                            }
                            if (!isLinuxEntry) {
                                // OpenGL (per-game): each `Auto | On | Off`, Auto = follow the container.
                                // Fast is locked to Auto with a short reason when it can't run.
                                DeckGroup("OpenGL") {
                                    val fpsSw = com.winhub.hernandez.core.FastOpenGL.FPS_HUD
                                    val vsyncSw = com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF
                                    val containerFast = shortcut.container.isFastOpenGL()
                                    val containerFps = fpsSw.containerOn(shortcut.container.getExtra(fpsSw.extra))
                                    val containerVsync = vsyncSw.containerOn(shortcut.container.getExtra(vsyncSw.extra))
                                    fun glOptions(autoOn: Boolean) = listOf(
                                        "" to "Auto (" + (if (autoOn) "On" else "Off") + ")", "1" to "On", "0" to "Off")
                                    DeckChoiceRow(
                                        label = com.winhub.hernandez.core.FastOpenGL.TITLE,
                                        hint = com.winhub.hernandez.ui.components.openGLFastNote(fastGlLocked)
                                            ?.replaceFirstChar { it.uppercase() }
                                            ?: com.winhub.hernandez.core.FastOpenGL.HINT,
                                        options = if (fastGlLocked != null) glOptions(false).take(1) else glOptions(containerFast),
                                        selected = if (fastGlLocked != null) "" else fastGlOverride,
                                        onPick = { if (fastGlLocked == null) fastGlOverride = it },
                                    )
                                    DeckChoiceRow(
                                        label = fpsSw.title,
                                        hint = fpsSw.hint,
                                        options = glOptions(containerFps),
                                        selected = glFpsHudOverride,
                                        onPick = { glFpsHudOverride = it },
                                    )
                                    DeckChoiceRow(
                                        label = vsyncSw.title,
                                        hint = vsyncSw.hint,
                                        options = glOptions(containerVsync),
                                        selected = glVsyncOffOverride,
                                        onPick = { glVsyncOffOverride = it },
                                    )
                                }
                            }
                        }
                        4 -> {
                            DeckGroup("Input") {
                                if (!isLinuxEntry) {
                                    DeckRow(stringResource(R.string.enable_xinput_for_wine_game)) {
                                        DeckToggle(checked = enableXInput, onChange = { enableXInput = it })
                                    }
                                    DeckRow(stringResource(R.string.enable_dinput_for_wine_game)) {
                                        DeckToggle(checked = enableDInput, onChange = { enableDInput = it })
                                    }
                                    DeckRow("Exclusive Input") {
                                        DeckToggle(checked = exclusiveXInput, onChange = { checked ->
                                            exclusiveXInput = checked
                                            if (!checked) { enableXInput = true; enableDInput = true }
                                            else if (enableXInput && enableDInput) enableDInput = false
                                        })
                                    }
                                    DeckRow("Disable XInput") {
                                        DeckToggle(checked = disabledXInput, onChange = { disabledXInput = it })
                                    }
                                }
                                DeckRow("Touchscreen Mode") {
                                    DeckToggle(checked = simTouchScreen, onChange = { simTouchScreen = it })
                                }
                                if (!isLinuxEntry) {
                                    DeckChoiceRow(
                                        label = "Num Controllers",
                                        options = numControllersEntries.map { it to it },
                                        selected = selectedNumControllers,
                                        onPick = { selectedNumControllers = it },
                                    )
                                }
                            }
                            DeckGroup("Overlay") {
                                run {
                                    val autoHideLabels = listOf("Use container default", "On", "Off")
                                    val autoHideValues = listOf("", "1", "0")
                                    val autoHideIdx = when (autoHideControlsOnPad) { "1" -> 1; "0" -> 2; else -> 0 }
                                    DeckChoiceRow(
                                        label = "Hide on-screen controls when a controller connects",
                                        options = autoHideValues.zip(autoHideLabels),
                                        selected = autoHideValues[autoHideIdx],
                                        onPick = { autoHideControlsOnPad = it },
                                    )
                                }
                                if (controllerSlotOverridesJson.isNotEmpty()) {
                                    DeckRow("Player Slots", hint = "Inheriting shows the container's Player Slots") {
                                        DeckButton("Use container default", primary = false) { controllerSlotOverridesJson = "" }
                                    }
                                } else {
                                    DeckRow("Player Slots", hint = "Inheriting the container's Player Slots. Pin a controller below to override.") {}
                                }
                                PlayerSlotsEditor(
                                    savedOverridesJson = controllerSlotOverridesJson.ifEmpty { "{}" },
                                    onOverridesChange = { controllerSlotOverridesJson = it },
                                )
                            }
                            DeckGroup("Gyro") {
                                DeckRow(stringResource(R.string.gyro_enabled)) {
                                    DeckToggle(checked = gyroEnabled, onChange = { gyroEnabled = it })
                                }
                                if (gyroEnabled) {
                                    val gyroModeLabels = listOf(
                                        stringResource(R.string.gyro_mode_rate),
                                        stringResource(R.string.gyro_mode_orientation),
                                    )
                                    DeckChoiceRow(
                                        label = stringResource(R.string.gyro_mode_label),
                                        options = gyroModeLabels.indices.toList().map { it.toString() to gyroModeLabels[it] },
                                        selected = gyroMode.coerceIn(0, gyroModeLabels.lastIndex).toString(),
                                        onPick = { opt ->
                                            gyroMode = opt.toIntOrNull() ?: 0
                                            if (gyroMode == Container.GYRO_MODE_ORIENTATION && gyroTarget == Container.GYRO_TARGET_MOUSE)
                                                gyroTarget = Container.GYRO_TARGET_DEFAULT
                                        },
                                    )
                                    val gyroTargetLabels = listOf(
                                        stringResource(R.string.gyro_target_right_stick),
                                        stringResource(R.string.gyro_target_left_stick),
                                        stringResource(R.string.gyro_target_mouse),
                                    )
                                    DeckChoiceRow(
                                        label = stringResource(R.string.gyro_target_label),
                                        options = gyroTargetLabels.indices.toList().map { it.toString() to gyroTargetLabels[it] },
                                        selected = gyroTarget.coerceIn(0, gyroTargetLabels.lastIndex).toString(),
                                        onPick = { opt ->
                                            gyroTarget = opt.toIntOrNull() ?: 0
                                            if (gyroTarget == Container.GYRO_TARGET_MOUSE) gyroMode = Container.GYRO_MODE_RATE
                                        },
                                    )
                                    val gyroActivatorLabels = listOf(
                                        stringResource(R.string.gyro_activator_l1),
                                        stringResource(R.string.gyro_activator_l2),
                                        stringResource(R.string.gyro_activator_r1),
                                        stringResource(R.string.gyro_activator_r3),
                                        stringResource(R.string.gyro_activator_always),
                                    )
                                    DeckChoiceRow(
                                        label = stringResource(R.string.gyro_activator_label),
                                        options = gyroActivatorLabels.indices.toList().map { it.toString() to gyroActivatorLabels[it] },
                                        selected = gyroActivator.coerceIn(0, gyroActivatorLabels.lastIndex).toString(),
                                        onPick = { gyroActivator = it.toIntOrNull() ?: 0 },
                                    )
                                    if (gyroActivator != Container.GYRO_ACTIVATOR_ALWAYS) {
                                        val gyroActivationModeLabels = listOf(
                                            stringResource(R.string.gyro_activation_hold),
                                            stringResource(R.string.gyro_activation_toggle),
                                        )
                                        DeckChoiceRow(
                                            label = stringResource(R.string.gyro_activation_mode_label),
                                            options = gyroActivationModeLabels.indices.toList().map { it.toString() to gyroActivationModeLabels[it] },
                                            selected = gyroActivationMode.coerceIn(0, gyroActivationModeLabels.lastIndex).toString(),
                                            onPick = { gyroActivationMode = it.toIntOrNull() ?: 0 },
                                        )
                                    }
                                    DeckSliderRow(
                                        label = stringResource(R.string.gyro_sensitivity_label),
                                        value = gyroSensitivity,
                                        range = 0.1f..10f,
                                        format = { "%.1f".format(it) },
                                        onChange = { gyroSensitivity = it },
                                    )
                                    DeckRow(stringResource(R.string.gyro_invert_x)) {
                                        DeckToggle(checked = gyroInvertX, onChange = { gyroInvertX = it })
                                    }
                                    DeckRow(stringResource(R.string.gyro_invert_y)) {
                                        DeckToggle(checked = gyroInvertY, onChange = { gyroInvertY = it })
                                    }
                                }
                            }
                        }
                        1 -> {
                            RecommendedComponentsSection(
                                container = shortcut.container,
                                exeFile = gameExe,
                                gameDir = gameDir,
                                shortcutBaseName = shortcut.name,
                            )
                            DeckWinComponentsTab(winComponents)
                        }
                        2 -> DeckEnvVarsTab(envVarsStr, { envVarsStr = it }, gameDir, isLinuxEntry)
                        3 -> DeckAdvancedTab(
                            shortcut = shortcut,
                            onPresetListChanged = {
                                box64Presets = Box64PresetManager.getPresets("box64", context)
                                fexCorePresets = FEXCorePresetManager.getPresets(context)
                            },
                            isArm64EC = isArm64EC,
                            isLinuxEntry = isLinuxEntry,
                            box64Versions = box64Versions,
                            selectedBox64Version = selectedBox64Version,
                            onBox64VersionChange = { selectedBox64Version = it },
                            box64Presets = box64Presets,
                            selectedBox64PresetIndex = selectedBox64PresetIndex,
                            onBox64PresetIndexChange = { selectedBox64PresetIndex = it },
                            fexCoreVersions = fexCoreVersions,
                            selectedFexCoreVersion = selectedFexCoreVersion,
                            onFexVersionChange = { selectedFexCoreVersion = it },
                            fexCorePresets = fexCorePresets,
                            selectedFexPresetIndex = selectedFexCorePresetIndex,
                            onFexPresetIndexChange = { selectedFexCorePresetIndex = it },
                            controlsProfiles = controlsProfiles,
                            selectedControlsProfileIndex = selectedControlsProfileIndex,
                            onControlsProfileChange = { selectedControlsProfileIndex = it },
                            startupSelectionEntries = startupSelectionEntries,
                            selectedStartupSelection = selectedStartupSelection,
                            onStartupChange = { selectedStartupSelection = it },
                            startupServicesEnabled = startupServicesEnabled,
                            onStartupServiceToggle = { raw, on ->
                                startupServicesEnabled =
                                    if (on) startupServicesEnabled + raw else startupServicesEnabled - raw
                            },
                            cpuListViewRef = cpuListViewRef,
                            initialCpuList = shortcut.getExtra("cpuList", shortcut.container.getCPUList(true)),
                            onCpuListSnapshot = { shortcut.putExtra("cpuList", it) },
                            sharpnessEffectEntries = sharpnessEffectEntries,
                            selectedSharpnessEffect = selectedSharpnessEffect,
                            onSharpnessEffectChange = { selectedSharpnessEffect = it },
                            sharpnessLevel = sharpnessLevel,
                            onSharpnessLevelChange = { sharpnessLevel = it },
                            sharpnessDenoise = sharpnessDenoise,
                            onSharpnessDenoiseChange = { sharpnessDenoise = it },
                            reshadeLoadout = reshadeLoadout,
                            reshadeEffects = reshadeEffects,
                            onReshadeCatalogChanged = {
                                reshadeEffects = ReshadeManager.scanEffects(context)
                                reshadeLoadout.reconcile(reshadeEffects)
                            },
                            reshadeSupported = StringUtils.parseIdentifier(selectedDxWrapper).let { it.contains("dxvk") || it.contains("vegas") },
                            onShowBox64DownloadSheet = { showBox64DownloadSheet = true },
                            onShowFexCoreDownloadSheet = { showFexCoreDownloadSheet = true },
                        )
                        7 -> DeckModsTab(gameMods, startPrograms)
                        5 -> DeckTvTab(
                            display = tvDisplay,
                            gameScreenSize = if (selectedScreenSize == "Custom") "${customWidth}x${customHeight}"
                                             else StringUtils.parseIdentifier(selectedScreenSize),
                            tvLaunch = tvLaunch,
                            onTvLaunchChange = { tvLaunch = it },
                            tvModeId = tvModeId,
                            onTvModeIdChange = { tvModeId = it },
                            tvMatchRes = tvMatchRes,
                            onTvMatchResChange = { tvMatchRes = it },
                        )
                    }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeckButton("Cancel", primary = false) { onDismiss() }
                DeckButton("Save") { save(); onDismiss() }
            }
        }
    }

    if (showGfxConfig) {
        DeckConfigDialog(title = stringResource(R.string.graphics_driver), onDismiss = { showGfxConfig = false }) {
            DeckGraphicsDriverConfigEditor(
                graphicsDriver = StringUtils.parseIdentifier(selectedGfxDriver),
                initialConfig = graphicsDriverConfig,
                onConfirm = { graphicsDriverConfig = it; showGfxConfig = false },
                onChange = { graphicsDriverConfig = it },
            )
        }
    }
    if (showDxvkConfig) {
        val isVegasCfg = StringUtils.parseIdentifier(selectedDxWrapper).contains("vegas")
        DeckConfigDialog(title = "DX Wrapper Config", onDismiss = { showDxvkConfig = false }) {
            if (StringUtils.parseIdentifier(selectedDxWrapper).let { it.contains("dxvk") || it.contains("vegas") }) {
                DeckDxvkConfigEditor(
                    isArm64EC = isArm64EC,
                    isVegas = isVegasCfg,
                    containerRootDir = shortcut.container.rootDir,
                    relaxDxvkFilter = StringUtils.parseIdentifier(selectedGfxDriver) == "wrapper-compat-bcn",
                    initialConfig = dxWrapperConfig,
                    onConfirm = { dxWrapperConfig = it; showDxvkConfig = false },
                    onChange = { dxWrapperConfig = it },
                    onOpenConfigDownload = if (isVegasCfg) { { showStockConfigSheet = true } } else null,
                    refreshKey = dxvkRefreshKey,
                    onDownloadDxvk = { showDxvkDownloadSheet = true },
                    onDownloadVkd3d = { showVkd3dDownloadSheet = true },
                    onDownloadD7vk = { showD7vkDownloadSheet = true },
                )
            } else {
                DeckWineD3DConfigEditor(
                    initialConfig = dxWrapperConfig,
                    onConfirm = { dxWrapperConfig = it; showDxvkConfig = false },
                    onChange = { dxWrapperConfig = it },
                )
            }
        }
    }
    if (showWineD3DConfig) {
        DeckConfigDialog(title = "DX Wrapper Config", onDismiss = { showWineD3DConfig = false }) {
            DeckWineD3DConfigEditor(
                initialConfig = dxWrapperConfig,
                onConfirm = { dxWrapperConfig = it; showWineD3DConfig = false },
                onChange = { dxWrapperConfig = it },
            )
        }
    }
    if (showStockConfigSheet) {
        DeckStockConfigSheet(
            onDismiss = { showStockConfigSheet = false; dxvkRefreshKey++ },
            onContentChanged = { dxvkRefreshKey++ },
        )
    }
    helpRes?.let { HelpDialog(it) { helpRes = null } }
    helpText?.let { HelpTextDialog(it) { helpText = null } }
    if (showWrapperManager) DeckWrapperManagerDialog(onDismiss = {
        showWrapperManager = false
        wrapperRefreshKey++
    })
    glossaryQuery?.let { DeckGlossarySheet(initialQuery = it, onDismiss = { glossaryQuery = null }) }

    if (showBox64DownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(if (isArm64EC) ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                          else ContentProfile.ContentType.CONTENT_TYPE_BOX64),
            onDismiss = { showBox64DownloadSheet = false },
            onContentChanged = {},
        )
    }
    if (showFexCoreDownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE),
            onDismiss = { showFexCoreDownloadSheet = false },
            onContentChanged = {},
        )
    }
    if (showDxvkDownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(ContentProfile.ContentType.CONTENT_TYPE_DXVK),
            onDismiss = { showDxvkDownloadSheet = false },
            onContentChanged = {},
        )
    }
    if (showVkd3dDownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(ContentProfile.ContentType.CONTENT_TYPE_VKD3D),
            onDismiss = { showVkd3dDownloadSheet = false },
            onContentChanged = {},
        )
    }
    if (showD7vkDownloadSheet) {
        DeckContentDownloadSheet(
            contentTypes = listOf(ContentProfile.ContentType.CONTENT_TYPE_D7VK),
            onDismiss = { showD7vkDownloadSheet = false },
            onContentChanged = {},
        )
    }
    if (showScAudioSettings) {
        DeckAudioSettingsDialog(
            initial = audioConfigFromEnv(envVarsStr, StringUtils.parseIdentifier(selectedAudioDriver)),
            scopeLabel = "this game",
            latencyLive = true,
            driverLabel = when (StringUtils.parseIdentifier(selectedAudioDriver)) {
                "alsa" -> "ALSA"; "pulseaudio" -> "PulseAudio"; "directaudio" -> "DirectAudio"
                else -> StringUtils.parseIdentifier(selectedAudioDriver)
            },
            driverId = StringUtils.parseIdentifier(selectedAudioDriver),
            onDismiss = { showScAudioSettings = false },
            onSave = { cfg ->
                envVarsStr = audioConfigToEnv(envVarsStr, cfg, StringUtils.parseIdentifier(selectedAudioDriver))
                showScAudioSettings = false
            },
        )
    }
    if (showPerfDialog) {
        DeckPerformanceDialog(
            sustained = sustainedPerfMode, onSustained = { sustainedPerfMode = it },
            priority = perfPriorityBoost, onPriority = { perfPriorityBoost = it },
            bigCores = preferBigCores, onBigCores = { preferBigCores = it },
            rootValue = { rootOverrides[it] ?: false },
            onRoot = { k, v -> rootOverrides[k] = v },
            onResetAll = {
                sustainedPerfMode = com.winhub.hernandez.perf.PerformanceSettings.sustainedPerfMode.value
                perfPriorityBoost = com.winhub.hernandez.perf.PerformanceSettings.perfPriorityBoost.value
                preferBigCores = com.winhub.hernandez.perf.PerformanceSettings.preferBigCores.value
                for (rk in com.winhub.hernandez.perf.PerfRootApplier.ROOT_KEYS)
                    rootOverrides[rk] = com.winhub.hernandez.perf.PerformanceSettings.rootDefaultValue(rk)
            },
            onDismiss = { showPerfDialog = false },
        )
    }
    if (showLaunchMethod) {
        DeckLaunchMethodDialog(
            shortcut = shortcut,
            onDismiss = { showLaunchMethod = false },
            heading = "Launch method",
            confirmLabel = "Save",
            onLaunch = { mode, gm, remember ->
                shortcut.putExtra("launchMode", mode)
                shortcut.putExtra("launchModeRemembered", if (remember) "1" else "0")
                if (gm != null) {
                    SteamPrefs.init(context)
                    SteamPrefs.setGoldbergMode(steamAppIdOf(shortcut), gm)
                }
                launchMode = mode
                launchModeRemembered = remember
                showLaunchMethod = false
            },
        )
    }
}

/** The Material icon for each shortcut-settings tab (mirrors the container editor's tabIcon glyphs;
 *  Controller gets a gamepad). Used by both the portrait top bar and the landscape rail. */
private fun shortcutTabIcon(title: String): ImageVector = when (title) {
    "General" -> Icons.Filled.Settings
    "Win Components" -> Icons.Filled.Widgets
    "Env Vars" -> Icons.Filled.Extension
    "Graphics" -> Icons.Filled.Memory
    "Advanced" -> Icons.Filled.Tune
    "Controller" -> Icons.Filled.SportsEsports
    "TV" -> Icons.Filled.Tv
    "Mods" -> Icons.Filled.Apps
    else -> Icons.Filled.Settings
}

/**
 * The "TV" tab — start this game's session ON a connected external display. See
 * com.winhub.hernandez.display.ExternalDisplay.
 *
 * Everything about the display (its name, its output modes, its HDR) is READ LIVE here and never
 * stored: the caller re-reads it on every DisplayManager event, so unplugging the cable updates this
 * tab (and takes it away) without the dialog being reopened.
 *
 * HDR is shown rather than asked. The compositor's HDR gate is decided from the display the session
 * lands on (XServerDisplayActivity.hdrTargetDisplay), so a game started on an HDR10 TV simply gets HDR
 * and one started on a panel without it does not — there is nothing here for the user to switch, only
 * a reason to read when the answer is no.
 *
 * [display] null = the tab is only here because this game still has the setting stored.
 * [gameScreenSize] this game's own render resolution ("1280x720"), for the "Match the TV's" subtitle.
 */
@Composable
private fun DeckWinComponentsTab(components: androidx.compose.runtime.snapshots.SnapshotStateList<WinComponentEntry>) {
    val directx = components.filter { it.key.startsWith("direct") }
    val general = components.filterNot { it.key.startsWith("direct") }
    val options = listOf("Builtin (Wine)", "Native (Windows)")
    val optionPairs = options.indices.toList().map { it.toString() to options[it] }
    fun select(comp: WinComponentEntry, value: String) {
        val i = components.indexOfFirst { it.key == comp.key }
        if (i >= 0) components[i] = components[i].copy(selectedIndex = (value.toIntOrNull() ?: 0).coerceAtLeast(0))
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (directx.isNotEmpty()) {
            DeckGroup("DirectX") {
                directx.forEach { comp ->
                    DeckChoiceRow(
                        label = comp.label,
                        options = optionPairs,
                        selected = comp.selectedIndex.coerceIn(0, options.lastIndex).toString(),
                        onPick = { select(comp, it) },
                    )
                }
            }
        }
        if (general.isNotEmpty()) {
            DeckGroup("General") {
                general.forEach { comp ->
                    DeckChoiceRow(
                        label = comp.label,
                        options = optionPairs,
                        selected = comp.selectedIndex.coerceIn(0, options.lastIndex).toString(),
                        onPick = { select(comp, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckEnvVarsTab(
    envVars: String,
    onEnvVarsChange: (String) -> Unit,
    gameDir: File?,
    isLinuxEntry: Boolean = false,
) {
    DeckGroup("Environment variables") {
        EnvVarsEditor(
            value = envVars,
            onValueChange = onEnvVarsChange,
            modifier = Modifier.fillMaxWidth(),
            gameDir = gameDir,
            preferDllsHelp = if (isLinuxEntry) stringResource(R.string.help_linux_prefer_game_dlls) else null,
        )
    }
}

@Composable
private fun DeckAdvancedTab(
    shortcut: Shortcut,
    onPresetListChanged: () -> Unit,
    isArm64EC: Boolean,
    isLinuxEntry: Boolean = false,
    box64Versions: List<String>,
    selectedBox64Version: String,
    onBox64VersionChange: (String) -> Unit,
    box64Presets: List<Box64Preset>,
    selectedBox64PresetIndex: Int,
    onBox64PresetIndexChange: (Int) -> Unit,
    fexCoreVersions: List<String>,
    selectedFexCoreVersion: String,
    onFexVersionChange: (String) -> Unit,
    fexCorePresets: List<FEXCorePreset>,
    selectedFexPresetIndex: Int,
    onFexPresetIndexChange: (Int) -> Unit,
    controlsProfiles: List<ControlsProfile>,
    selectedControlsProfileIndex: Int,
    onControlsProfileChange: (Int) -> Unit,
    startupSelectionEntries: List<String>,
    selectedStartupSelection: String,
    onStartupChange: (String) -> Unit,
    startupServicesEnabled: Set<String>,
    onStartupServiceToggle: (String, Boolean) -> Unit,
    cpuListViewRef: MutableState<CPUListView?>,
    initialCpuList: String,
    onCpuListSnapshot: (String) -> Unit,
    sharpnessEffectEntries: List<String>,
    selectedSharpnessEffect: String,
    onSharpnessEffectChange: (String) -> Unit,
    sharpnessLevel: Int,
    onSharpnessLevelChange: (Int) -> Unit,
    sharpnessDenoise: Int,
    onSharpnessDenoiseChange: (Int) -> Unit,
    reshadeLoadout: ReshadeLoadoutState = ReshadeLoadoutState(),
    reshadeEffects: List<ReshadeManager.ReshadeEffect> = emptyList(),
    onReshadeCatalogChanged: () -> Unit = {},
    reshadeSupported: Boolean = true,
    onShowBox64DownloadSheet: () -> Unit = {},
    onShowFexCoreDownloadSheet: () -> Unit = {},
) {
    val context = LocalContext.current
    var presetRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        onDispose {
            cpuListViewRef.value?.let { onCpuListSnapshot(it.checkedCPUListAsString) }
            cpuListViewRef.value = null
        }
    }
    val emulatorLabel = if (isArm64EC) "WOWBox64" else "Box64"

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!isLinuxEntry) {
            DeckGroup(emulatorLabel) {
                DeckChoiceRow(
                    label = "$emulatorLabel Version",
                    options = box64Versions.map { it to it },
                    selected = box64Versions.firstOrNull { it == selectedBox64Version } ?: selectedBox64Version,
                    onPick = onBox64VersionChange,
                )
                DeckRow("$emulatorLabel Downloads") {
                    DeckButton("Download", primary = false) { onShowBox64DownloadSheet() }
                }
                val presetNames = box64Presets.map { it.name }
                val b64Id = box64Presets.getOrNull(selectedBox64PresetIndex)?.id ?: ""
                val b64Customised = remember(presetRevision, b64Id) {
                    b64Id.isNotEmpty() && PresetOverrides.isCustomised(
                        context, false, b64Id, PresetScope.SHORTCUT, shortcut.container, shortcut
                    )
                }
                DeckChoiceRow(
                    label = "$emulatorLabel Preset",
                    hint = if (b64Customised) "Customised for this game" else null,
                    options = presetNames.indices.toList().map { it.toString() to presetNames[it] },
                    selected = selectedBox64PresetIndex.coerceIn(0, (presetNames.size - 1).coerceAtLeast(0)).toString(),
                    onPick = { onBox64PresetIndexChange(it.toIntOrNull() ?: 0) },
                )
                PresetEditorRow(
                    kind = PresetKind.BOX64,
                    selectedPresetId = b64Id,
                    scope = PresetScope.SHORTCUT,
                    container = shortcut.container,
                    shortcut = shortcut,
                    onSelect = { id ->
                        box64Presets.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(onBox64PresetIndexChange)
                    },
                    onListChanged = { onPresetListChanged(); presetRevision++ },
                    onValuesChanged = { presetRevision++ },
                )
            }
        }
        if (isArm64EC || isLinuxEntry) {
            DeckGroup("FEXCore") {
                if (!isLinuxEntry) {
                    DeckChoiceRow(
                        label = stringResource(R.string.fexcore_version),
                        options = fexCoreVersions.map { it to it },
                        selected = fexCoreVersions.firstOrNull { it == selectedFexCoreVersion } ?: selectedFexCoreVersion,
                        onPick = onFexVersionChange,
                    )
                    DeckRow("FEXCore Downloads") {
                        DeckButton("Download", primary = false) { onShowFexCoreDownloadSheet() }
                    }
                }
                val fexNames = fexCorePresets.map { it.name }
                val fexId = fexCorePresets.getOrNull(selectedFexPresetIndex)?.id ?: ""
                val fexCustomised = remember(presetRevision, fexId) {
                    fexId.isNotEmpty() && PresetOverrides.isCustomised(
                        context, true, fexId, PresetScope.SHORTCUT, shortcut.container, shortcut
                    )
                }
                DeckChoiceRow(
                    label = stringResource(R.string.fexcore_preset),
                    hint = if (fexCustomised) "Customised for this game" else null,
                    options = fexNames.indices.toList().map { it.toString() to fexNames[it] },
                    selected = selectedFexPresetIndex.coerceIn(0, (fexNames.size - 1).coerceAtLeast(0)).toString(),
                    onPick = { onFexPresetIndexChange(it.toIntOrNull() ?: 0) },
                )
                PresetEditorRow(
                    kind = PresetKind.FEXCORE,
                    selectedPresetId = fexId,
                    scope = PresetScope.SHORTCUT,
                    container = shortcut.container,
                    shortcut = shortcut,
                    onSelect = { id ->
                        fexCorePresets.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(onFexPresetIndexChange)
                    },
                    onListChanged = { onPresetListChanged(); presetRevision++ },
                    onValuesChanged = { presetRevision++ },
                )
            }
        }
        run {
            val profileNames = mutableListOf(stringResource(R.string.none))
            profileNames.addAll(controlsProfiles.map { it.getName() })
            DeckChoiceRow(
                label = "Controls Profile",
                options = profileNames.indices.toList().map { it.toString() to profileNames[it] },
                selected = selectedControlsProfileIndex.coerceIn(0, profileNames.lastIndex).toString(),
                onPick = { onControlsProfileChange(it.toIntOrNull() ?: 0) },
            )
        }
        if (!isLinuxEntry) {
            DeckGroup("Startup") {
                DeckChoiceRow(
                    label = stringResource(R.string.startup_selection),
                    options = startupSelectionEntries.indices.toList().map { it.toString() to startupSelectionEntries[it] },
                    selected = startupSelectionEntries.indexOf(selectedStartupSelection).coerceAtLeast(0).toString(),
                    onPick = { onStartupChange(startupSelectionEntries.getOrElse(it.toIntOrNull() ?: 0) { startupSelectionEntries.first() }) },
                )
                if (startupSelectionEntries.indexOf(selectedStartupSelection) == Container.STARTUP_SELECTION_CUSTOM.toInt()) {
                    DeckStartupServiceRows(
                        enabled = startupServicesEnabled,
                        onToggle = onStartupServiceToggle,
                    )
                }
            }
        }
        DeckGroup(stringResource(R.string.processor_affinity)) {
            AndroidView(
                factory = { ctx ->
                    CPUListView(ctx).also { cpv ->
                        cpv.setCheckedCPUList(initialCpuList)
                        cpuListViewRef.value = cpv
                    }
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
        }
        if (!isLinuxEntry) {
            DeckGroup("Sharpness (VKBasalt)") {
                DeckChoiceRow(
                    label = "Effect",
                    options = sharpnessEffectEntries.map { it to it },
                    selected = selectedSharpnessEffect,
                    onPick = onSharpnessEffectChange,
                )
                DeckSliderRow(
                    label = "Level",
                    value = sharpnessLevel.toFloat(),
                    range = 0f..100f,
                    format = { "${it.toInt()}%" },
                    onChange = { onSharpnessLevelChange(it.toInt()) },
                )
                DeckSliderRow(
                    label = "Denoise",
                    value = sharpnessDenoise.toFloat(),
                    range = 0f..100f,
                    format = { "${it.toInt()}%" },
                    onChange = { onSharpnessDenoiseChange(it.toInt()) },
                )
            }
            DeckGroup("ReShade loadout") {
                ReshadeLoadoutEditor(
                    state = reshadeLoadout,
                    effects = reshadeEffects,
                    supported = reshadeSupported,
                    onCatalogChanged = onReshadeCatalogChanged,
                )
            }
        }
    }
}

@Composable
private fun DeckTvTab(
    display: android.view.Display?,
    gameScreenSize: String,
    tvLaunch: Boolean,
    onTvLaunchChange: (Boolean) -> Unit,
    tvModeId: Int,
    onTvModeIdChange: (Int) -> Unit,
    tvMatchRes: Boolean,
    onTvMatchResChange: (Boolean) -> Unit,
) {
    val modes = com.winhub.hernandez.display.ExternalDisplay.selectableModes(display)
    val hdrReason = if (display == null) null else com.winhub.hernandez.display.ExternalDisplay.hdrUnavailableReason(display)
    val hdrOk = display != null && hdrReason == null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DeckGroup("External display") {
            DeckRow(
                label = if (display != null) com.winhub.hernandez.display.ExternalDisplay.title(display) else "No external display",
                hint = if (display != null) com.winhub.hernandez.display.ExternalDisplay.summary(display)
                       else "Nothing is plugged in. This game is still set to launch on a TV, so the tab stays here.",
            ) {}
            DeckRow(
                label = "Launch this game on the TV",
                hint = com.winhub.hernandez.display.ExternalDisplay.HELP_LAUNCH,
            ) {
                DeckToggle(checked = tvLaunch, onChange = onTvLaunchChange)
            }
            if (modes.size > 1) {
                val values = listOf(0) + modes.map { it.modeId }
                val labels = listOf("Default (leave the TV as it is)") +
                    modes.map { com.winhub.hernandez.display.ExternalDisplay.modeLabel(it) }
                DeckChoiceRow(
                    label = "Output mode",
                    hint = "What the TV is told to run at.",
                    options = values.indices.toList().map { it.toString() to labels[it] },
                    selected = values.indexOf(tvModeId).coerceAtLeast(0).toString(),
                    onPick = { onTvModeIdChange(values.getOrElse(it.toIntOrNull() ?: 0) { 0 }) },
                )
            } else {
                DeckRow(
                    label = "Output mode",
                    hint = if (display != null) "This screen advertises one output mode."
                           else "Plug a screen in to choose an output mode.",
                ) {
                    Text(
                        com.winhub.hernandez.display.ExternalDisplay.modeLabel(modes.firstOrNull()).ifEmpty { "—" },
                        color = DeckPalette.onSurfaceVariant,
                        fontSize = 12.5.sp,
                    )
                }
            }
            DeckRow(
                label = "Match the TV's resolution",
                hint = run {
                    val tvRes = com.winhub.hernandez.display.ExternalDisplay.resolutionOf(
                        com.winhub.hernandez.display.ExternalDisplay.effectiveMode(display, tvModeId))
                    if (tvRes.isEmpty()) com.winhub.hernandez.display.ExternalDisplay.HELP_MATCH_RES
                    else "Render at ${tvRes.replace("x", "×")} instead of this game's ${gameScreenSize.replace("x", "×")}."
                },
            ) {
                DeckToggle(checked = tvMatchRes, onChange = onTvMatchResChange)
            }
            DeckRow(
                label = "Use HDR on the TV",
                hint = hdrReason ?: "Automatic. This screen accepts HDR10, so HDR output switches on for this game.",
            ) {
                Text(
                    if (hdrOk) "On" else "Off",
                    color = if (hdrOk) DeckPalette.good else DeckPalette.onSurfaceVariant,
                    fontSize = 12.5.sp,
                )
            }
        }
        DeckGroup("Notes") {
            DeckRow(com.winhub.hernandez.display.ExternalDisplay.NOTE_UNPLUG) {}
            DeckRow(com.winhub.hernandez.display.ExternalDisplay.NOTE_TOUCH) {}
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScTvTab(
    dp: SettingsDpad,
    display: android.view.Display?,
    gameScreenSize: String,
    tvLaunch: Boolean,
    onTvLaunchChange: (Boolean) -> Unit,
    tvModeId: Int,
    onTvModeIdChange: (Int) -> Unit,
    tvMatchRes: Boolean,
    onTvMatchResChange: (Boolean) -> Unit,
) {
    val modes = com.winhub.hernandez.display.ExternalDisplay.selectableModes(display)
    val hdrReason = if (display == null) null else com.winhub.hernandez.display.ExternalDisplay.hdrUnavailableReason(display)
    val hdrOk = display != null && hdrReason == null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── The display, as Android describes it right now ──────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Tv, contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (display != null) com.winhub.hernandez.display.ExternalDisplay.title(display)
                            else "No external display",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (display != null) com.winhub.hernandez.display.ExternalDisplay.summary(display)
                            else "Nothing is plugged in. This game is still set to launch on a TV, so the tab stays here.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (display != null) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        com.winhub.hernandez.display.ExternalDisplay.hdrChips(display).forEach { chip ->
                            val on = com.winhub.hernandez.display.ExternalDisplay.isHdrFormatChip(chip) && hdrOk
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    chip,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── The one switch that matters ─────────────────────────────────────────────────────────
        TvSettingRow(
            title = "Launch this game on the TV",
            subtitle = com.winhub.hernandez.display.ExternalDisplay.HELP_LAUNCH,
        ) {
            DpSwitch(dp, "tvLaunch", checked = tvLaunch, onCheckedChange = onTvLaunchChange)
        }

        // ── Output mode: what the TV is asked to run at ─────────────────────────────────────────
        // A display that advertises exactly one mode has nothing to choose, so it reads back as a
        // plain row (and stays out of the D-pad order — see dpadIds).
        if (modes.size > 1) {
            val values = listOf(0) + modes.map { it.modeId }
            val labels = listOf("Default (leave the TV as it is)") +
                modes.map { com.winhub.hernandez.display.ExternalDisplay.modeLabel(it) }
            DpDrop(
                dp, "tvModeId",
                label = "Output mode",
                options = labels,
                selected = labels[values.indexOf(tvModeId).coerceAtLeast(0)],
                onSelect = { onTvModeIdChange(values[labels.indexOf(it)]) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "What the TV is told to run at.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TvSettingRow(
                title = "Output mode",
                subtitle = if (display != null) "This screen advertises one output mode."
                           else "Plug a screen in to choose an output mode.",
            ) {
                Text(
                    com.winhub.hernandez.display.ExternalDisplay.modeLabel(modes.firstOrNull()).ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Render resolution ───────────────────────────────────────────────────────────────────
        TvSettingRow(
            title = "Match the TV's resolution",
            subtitle = run {
                val tvRes = com.winhub.hernandez.display.ExternalDisplay.resolutionOf(
                    com.winhub.hernandez.display.ExternalDisplay.effectiveMode(display, tvModeId))
                if (tvRes.isEmpty()) com.winhub.hernandez.display.ExternalDisplay.HELP_MATCH_RES
                else "Render at ${tvRes.replace("x", "×")} instead of this game's " +
                     "${gameScreenSize.replace("x", "×")}. More pixels costs frame rate."
            },
        ) {
            DpSwitch(dp, "tvMatchRes", checked = tvMatchRes, onCheckedChange = onTvMatchResChange)
        }

        // ── HDR: reported, never asked ──────────────────────────────────────────────────────────
        TvSettingRow(
            title = "Use HDR on the TV",
            subtitle = hdrReason
                ?: "Automatic. This screen accepts HDR10, so HDR output switches on for this game.",
            dimSubtitle = !hdrOk,
        ) {
            // Deliberately a bare Switch, not a DpSwitch: it is a readout, so it must not be
            // reachable with the D-pad or invite a tap.
            Switch(checked = hdrOk, onCheckedChange = {}, enabled = false)
        }

        // ── The two things to know before the first try ─────────────────────────────────────────
        TvNote(com.winhub.hernandez.display.ExternalDisplay.NOTE_UNPLUG)
        TvNote(com.winhub.hernandez.display.ExternalDisplay.NOTE_TOUCH)
    }
}

/** One TV-tab row: title + explanation on the left, its control on the right (the approved design). */
@Composable
private fun TvSettingRow(
    title: String,
    subtitle: String,
    dimSubtitle: Boolean = false,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (dimSubtitle) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        control()
    }
}

/** A quiet boxed note under the TV rows (the mockup's "info" blocks). */
@Composable
private fun TvNote(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ScWinComponentsTab(components: androidx.compose.runtime.snapshots.SnapshotStateList<WinComponentEntry>) {
    val directx = components.filter { it.key.startsWith("direct") }
    val general = components.filterNot { it.key.startsWith("direct") }
    val options = listOf("Builtin (Wine)", "Native (Windows)")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (directx.isNotEmpty()) {
            SectionBox(title = "DirectX") {
                directx.forEach { comp ->
                    LabeledDropdown(
                        label = comp.label,
                        options = options,
                        selectedOption = options.getOrElse(comp.selectedIndex) { options[0] },
                        onSelect = { opt ->
                            val i = components.indexOfFirst { it.key == comp.key }
                            if (i >= 0) components[i] = components[i].copy(selectedIndex = options.indexOf(opt).coerceAtLeast(0))
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (general.isNotEmpty()) {
            SectionBox(title = "General") {
                general.forEach { comp ->
                    LabeledDropdown(
                        label = comp.label,
                        options = options,
                        selectedOption = options.getOrElse(comp.selectedIndex) { options[0] },
                        onSelect = { opt ->
                            val i = components.indexOfFirst { it.key == comp.key }
                            if (i >= 0) components[i] = components[i].copy(selectedIndex = options.indexOf(opt).coerceAtLeast(0))
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// A shortcut stores only the variables explicitly set on it; the container's own values are
// merged underneath at launch (XServerDisplayActivity), so an empty editor here still inherits
// everything from the container. Nothing is seeded from the container into the shortcut.
@Composable
private fun ScEnvVarsTab(
    envVars: String,
    onEnvVarsChange: (String) -> Unit,
    gameDir: File?,
    /** A Linux (gamescope) entry: this tab carries its own "?" for the variables and the DLL toggle. */
    isLinuxEntry: Boolean = false,
) {
    // Own helpRes, like every other tab composable here (the dialog's is out of scope).
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }
    Column {
        if (isLinuxEntry) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Environment variables", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                LinuxHelp(true, R.string.help_linux_env_vars) { helpRes = it }
            }
        }
        EnvVarsEditor(
            value = envVars,
            onValueChange = onEnvVarsChange,
            modifier = Modifier.fillMaxWidth(),
            gameDir = gameDir,
            // The toggle lives inside the shared editor, so its Linux wording is handed in as text.
            preferDllsHelp = if (isLinuxEntry) stringResource(R.string.help_linux_prefer_game_dlls) else null
        )
    }
}

@Composable
private fun ScAdvancedTab(
    /** The shortcut being edited — preset edits made here belong to THIS game and are stored on it. */
    shortcut: Shortcut,
    /** A preset was added / duplicated / removed / imported, so both lists need re-reading. */
    onPresetListChanged: () -> Unit,
    isArm64EC: Boolean,
    /**
     * An entry the Linux runtime owns. Its session reads the FEXCore preset and the controls profile
     * and pins CPU affinity through the compositor, but has no prefix for the x86 Wine backend,
     * Wine's startup services or the vkBasalt/ReShade layers to act on.
     */
    isLinuxEntry: Boolean = false,
    box64Versions: List<String>,
    selectedBox64Version: String,
    onBox64VersionChange: (String) -> Unit,
    box64Presets: List<Box64Preset>,
    selectedBox64PresetIndex: Int,
    onBox64PresetIndexChange: (Int) -> Unit,
    fexCoreVersions: List<String>,
    selectedFexCoreVersion: String,
    onFexVersionChange: (String) -> Unit,
    fexCorePresets: List<FEXCorePreset>,
    selectedFexPresetIndex: Int,
    onFexPresetIndexChange: (Int) -> Unit,
    controlsProfiles: List<ControlsProfile>,
    selectedControlsProfileIndex: Int,
    onControlsProfileChange: (Int) -> Unit,
    startupSelectionEntries: List<String>,
    selectedStartupSelection: String,
    onStartupChange: (String) -> Unit,
    startupServicesEnabled: Set<String>,
    onStartupServiceToggle: (String, Boolean) -> Unit,
    cpuListViewRef: MutableState<CPUListView?>,
    initialCpuList: String,
    onCpuListSnapshot: (String) -> Unit,
    sharpnessEffectEntries: List<String>,
    selectedSharpnessEffect: String,
    onSharpnessEffectChange: (String) -> Unit,
    sharpnessLevel: Int,
    onSharpnessLevelChange: (Int) -> Unit,
    sharpnessDenoise: Int,
    onSharpnessDenoiseChange: (Int) -> Unit,
    reshadeLoadout: ReshadeLoadoutState = ReshadeLoadoutState(),
    reshadeEffects: List<ReshadeManager.ReshadeEffect> = emptyList(),
    onReshadeCatalogChanged: () -> Unit = {},
    reshadeSupported: Boolean = true,
    onShowBox64DownloadSheet: () -> Unit = {},
    onShowFexCoreDownloadSheet: () -> Unit = {},
) {
    val context = LocalContext.current
    // Bumped when a preset's values or the preset list change, so the "customised" badges
    // re-evaluate — that state lives on the Shortcut, which Compose cannot observe by itself.
    var presetRevision by remember { mutableIntStateOf(0) }
    // Flush legacy CPUListView selection back to the parent (Shortcut extras)
    // before the tab leaves composition, so a tab switch doesn't drop edits.
    DisposableEffect(Unit) {
        onDispose {
            cpuListViewRef.value?.let { onCpuListSnapshot(it.checkedCPUListAsString) }
            cpuListViewRef.value = null
        }
    }
    // On arm64ec containers the x86 backend is WOWBox64, not Box64 — label it correctly (matching
    // the container editor). The dropdown data already reads the right content type; only the label
    // was hardcoded "Box64".
    val emulatorLabel = if (isArm64EC) "WOWBox64" else "Box64"
    // Per-field "?" help — this tab is its own composable, so it carries its own helpRes
    // (mirrors the container editor's per-composable HelpDialog pattern).
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Box64/WOWBox64 runs x86 Windows code inside the prefix. The Linux runtime's x86 games go
        // through its own FEX, so this whole section (and its per-game preset) reaches nothing there.
        if (!isLinuxEntry) {
        SectionBox(title = emulatorLabel) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabeledDropdown(
                    label = "$emulatorLabel Version",
                    options = box64Versions,
                    selectedOption = box64Versions.firstOrNull { it == selectedBox64Version } ?: selectedBox64Version,
                    onSelect = onBox64VersionChange,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onShowBox64DownloadSheet,
                    modifier = Modifier.size(40.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Download $emulatorLabel", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            val presetNames = box64Presets.map { it.name }
            val b64Id = box64Presets.getOrNull(selectedBox64PresetIndex)?.id ?: ""
            val b64Customised = remember(presetRevision, b64Id) {
                b64Id.isNotEmpty() && PresetOverrides.isCustomised(
                    context, false, b64Id, PresetScope.SHORTCUT, shortcut.container, shortcut
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledDropdown(
                    label = "$emulatorLabel Preset",
                    options = presetNames,
                    selectedOption = presetNames.getOrElse(selectedBox64PresetIndex) { "" },
                    onSelect = { opt -> onBox64PresetIndexChange(presetNames.indexOf(opt).coerceAtLeast(0)) },
                    modifier = Modifier.weight(1f)
                )
                if (b64Customised) PresetCustomBadge()
            }
            // Scoped to this game: the shared preset, its container and every other game stay as
            // they are. Values ride this dialog's OK, like every other field here.
            PresetEditorRow(
                kind = PresetKind.BOX64,
                selectedPresetId = b64Id,
                scope = PresetScope.SHORTCUT,
                container = shortcut.container,
                shortcut = shortcut,
                onSelect = { id ->
                    box64Presets.indexOfFirst { it.id == id }
                        .takeIf { it >= 0 }?.let(onBox64PresetIndexChange)
                },
                onListChanged = { onPresetListChanged(); presetRevision++ },
                onValuesChanged = { presetRevision++ },
            )
        }
        }

        // A Linux session hands the FEXCore preset to the games the Steam client launches whatever the
        // container's Windows architecture is, so the section is offered there even off arm64ec.
        if (isArm64EC || isLinuxEntry) {
            SectionBox(title = "FEXCore") {
                // The version picks which FEX build is staged into the container for the Wine
                // launcher; the Linux runtime carries its own, so only the preset is offered there.
                if (!isLinuxEntry) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabeledDropdown(
                        label = stringResource(R.string.fexcore_version),
                        options = fexCoreVersions,
                        selectedOption = fexCoreVersions.firstOrNull { it == selectedFexCoreVersion } ?: selectedFexCoreVersion,
                        onSelect = onFexVersionChange,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = R.string.help_fexcore_version }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = onShowFexCoreDownloadSheet,
                        modifier = Modifier.size(40.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Download FEXCore", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                }
                val fexNames = fexCorePresets.map { it.name }
                val fexId = fexCorePresets.getOrNull(selectedFexPresetIndex)?.id ?: ""
                val fexCustomised = remember(presetRevision, fexId) {
                    fexId.isNotEmpty() && PresetOverrides.isCustomised(
                        context, true, fexId, PresetScope.SHORTCUT, shortcut.container, shortcut
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledDropdown(
                        label = stringResource(R.string.fexcore_preset),
                        options = fexNames,
                        selectedOption = fexNames.getOrElse(selectedFexPresetIndex) { "" },
                        onSelect = { opt -> onFexPresetIndexChange(fexNames.indexOf(opt).coerceAtLeast(0)) },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { helpRes = if (isLinuxEntry) R.string.help_linux_fexcore_preset else R.string.help_fexcore_preset }) {
                        Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
                    }
                    if (fexCustomised) PresetCustomBadge()
                }
                PresetEditorRow(
                    kind = PresetKind.FEXCORE,
                    selectedPresetId = fexId,
                    scope = PresetScope.SHORTCUT,
                    container = shortcut.container,
                    shortcut = shortcut,
                    onSelect = { id ->
                        fexCorePresets.indexOfFirst { it.id == id }
                            .takeIf { it >= 0 }?.let(onFexPresetIndexChange)
                    },
                    onListChanged = { onPresetListChanged(); presetRevision++ },
                    onValuesChanged = { presetRevision++ },
                )
            }
        }

        val profileNames = mutableListOf(stringResource(R.string.none))
        profileNames.addAll(controlsProfiles.map { it.getName() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LabeledDropdown(
                label = "Controls Profile",
                options = profileNames,
                selectedOption = profileNames.getOrElse(selectedControlsProfileIndex) { profileNames.first() },
                onSelect = { opt -> onControlsProfileChange(profileNames.indexOf(opt).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f)
            )
            LinuxHelp(isLinuxEntry, R.string.help_linux_controls_profile) { helpRes = it }
        }

        // Which Wine services the prefix starts with. A gamescope session starts no wineserver at all.
        if (!isLinuxEntry) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LabeledDropdown(
                label = stringResource(R.string.startup_selection),
                options = startupSelectionEntries,
                selectedOption = selectedStartupSelection,
                onSelect = onStartupChange,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { helpRes = R.string.help_startup_selection }) {
                Icon(Icons.Default.Help, contentDescription = "What is this?", modifier = Modifier.size(18.dp))
            }
        }

        // Custom per-service toggles — only shown when "Custom" (index 3) is selected. Shares the
        // container editor's list composable so the two screens can't drift.
        if (startupSelectionEntries.indexOf(selectedStartupSelection) == Container.STARTUP_SELECTION_CUSTOM.toInt()) {
            StartupServicesToggleList(
                enabled = startupServicesEnabled,
                onToggle = onStartupServiceToggle
            )
        }
        }

        SectionBox(title = stringResource(R.string.processor_affinity)) {
            AndroidView(
                factory = { ctx ->
                    CPUListView(ctx).also { cpv ->
                        cpv.setCheckedCPUList(initialCpuList)
                        cpuListViewRef.value = cpv
                    }
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            )
        }
        

        // vkBasalt (sharpness) and the ReShade loadout are Vulkan layers switched on through the Wine
        // environment, which the gamescope path never builds; the client's games load neither.
        if (!isLinuxEntry) {
        SectionBox(title = "Sharpness (VKBasalt)") {
            LabeledDropdown(
                label = "Effect",
                options = sharpnessEffectEntries,
                selectedOption = selectedSharpnessEffect,
                onSelect = onSharpnessEffectChange
            )
            Spacer(Modifier.height(8.dp))
            Text("Level: $sharpnessLevel%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = sharpnessLevel.toFloat(),
                onValueChange = { onSharpnessLevelChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 99
            )
            Text("Denoise: $sharpnessDenoise%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = sharpnessDenoise.toFloat(),
                onValueChange = { onSharpnessDenoiseChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 99
            )
        }

        // ReShade multi-effect loadout (vkBasalt drop-in). Multi-select picker + solo/stack mode + a
        // collapsible per-effect param block. Only applies to DXVK/VKD3D (Vulkan) games — a hint shows.
        SectionBox(title = "ReShade loadout") {
            ReshadeLoadoutEditor(
                state = reshadeLoadout,
                effects = reshadeEffects,
                supported = reshadeSupported,
                onCatalogChanged = onReshadeCatalogChanged,
            )
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Non-composable helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Small portrait thumbnail for a Steam search result: loads the 600x900 library cover via Coil,
 * falling back to the landscape header when the portrait 404s, then to a plain placeholder box.
 * Disk cache is disabled so Coil never serves a cached 404 for a since-published cover.
 */
@Composable
private fun SteamResultThumbnail(appId: Int) {
    val context = LocalContext.current
    var useHeader by remember(appId) { mutableStateOf(false) }
    val url = if (useHeader) SteamStoreSearch.headerUrl(appId) else SteamStoreSearch.coverUrl(appId)
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 34.dp, height = 50.dp)
            .clip(RoundedCornerShape(4.dp)),
        loading = {
            Box(Modifier.fillMaxSize().background(OnSurfaceVariant.copy(alpha = 0.1f)))
        },
        error = {
            if (!useHeader) useHeader = true
            else Box(Modifier.fillMaxSize().background(OnSurfaceVariant.copy(alpha = 0.15f)))
        },
    )
}

/** Record the resolved Steam [appId] as a shortcut extra (rides with the .desktop through renames). */
private fun recordSteamAppId(container: Container, base: String, appId: Int) {
    val f = File(container.getDesktopDir(), "$base.desktop")
    if (!f.isFile) return
    runCatching { Shortcut(container, f).apply { putExtra("steamAppId", appId.toString()); saveData() } }
}

/** Best-effort blocking image download (call off the main thread). Null on any failure. */
private fun downloadBitmapOrNull(url: String): Bitmap? = try {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 20000
    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
    val bmp = if (conn.responseCode in 200..299) BitmapFactory.decodeStream(conn.inputStream) else null
    conn.disconnect()
    bmp
} catch (_: Exception) { null }

/**
 * Apply the Steam [appId] to the shortcut named [base] in [container]: records the appId as a
 * shortcut extra (seeds later redist detection; it rides with the .desktop through any rename) and
 * sets its cover art — Steam CDN 600x900 portrait, falling back to the landscape header. Writes
 * both customCoverArt and the grid-tile icon PNG (keyed on the current base). Returns the bitmap or null.
 */
internal fun applySteamCover(container: Container, base: String, appId: Int): Bitmap? {
    val shortcutFile = File(container.getDesktopDir(), "$base.desktop")
    if (!shortcutFile.isFile) return null
    val bmp = downloadBitmapOrNull(SteamStoreSearch.coverUrl(appId))
        ?: downloadBitmapOrNull(SteamStoreSearch.headerUrl(appId))
    return try {
        val shortcut = Shortcut(container, shortcutFile)
        shortcut.putExtra("steamAppId", appId.toString())
        if (bmp != null) {
            shortcut.saveCustomCoverArt(bmp) // persists cover + saveData() (writes the extra too)
            container.getIconsDir(64)?.let { iconsDir ->
                if (!iconsDir.exists()) iconsDir.mkdirs()
                FileUtils.saveBitmapToFile(bmp, File(iconsDir, "$base.png"))
            }
        } else {
            shortcut.saveData() // no art, but still persist the recorded appId
        }
        bmp
    } catch (_: Exception) { null }
}

private fun renameShortcut(shortcut: Shortcut, newName: String) {
    val parent = shortcut.file.parentFile ?: return
    val oldFile = shortcut.file
    val newFile = File(parent, "$newName.desktop")
    if (!newFile.isFile && oldFile.renameTo(newFile)) {
        runCatching {
            val field: Field = Shortcut::class.java.getDeclaredField("file")
            field.isAccessible = true
            field.set(shortcut, newFile)
        }
        val lnk = File(parent, "${shortcut.name}.lnk")
        if (lnk.isFile) lnk.renameTo(File(parent, "$newName.lnk"))
    }
}

/** Add if absent, drop if present — the whole of what tapping a card in selection mode does. */
private fun Set<String>.toggle(path: String): Set<String> =
    if (path in this) this - path else this + path

// The real launch — builds the XServerDisplayActivity intent (or the XR path) for a shortcut. Steam-
// origin games funnel through the launch-method popup (see requestLaunch) BEFORE reaching here; every
// other game comes straight in.
// [preflightDone] = the SteamLite pre-flight already pulled cloud saves; the activity skips its own pull.
internal fun launchShortcutNow(activity: Activity, shortcut: Shortcut, preflightDone: Boolean = false) {
    if (launchXboxOutsideWine(activity, shortcut)) return
    if (!XrActivity.isEnabled(activity)) {
        val intent = Intent(activity, XServerDisplayActivity::class.java).apply {
            putExtra("container_id", shortcut.container.id)
            putExtra("shortcut_path", shortcut.file.path)
            putExtra("shortcut_name", shortcut.name)
            putExtra("disableXinput", shortcut.getExtra("disableXinput", "0"))
            if (preflightDone) putExtra(SteamSessionManager.EXTRA_PREFLIGHT_DONE, true)
        }
        if (!launchOnExternalDisplay(activity, shortcut, intent)) activity.startActivity(intent)
    } else {
        XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.path)
    }
}

/**
 * "Launch this game on the TV" (the shortcut's TV tab — see display.ExternalDisplay): start the session
 * ON the external display instead of the handheld. Returns true when it did, false when the caller
 * should launch normally.
 *
 * This is what makes HDR work on a TV: the compositor's HDR gate is decided from the display the
 * activity's window is on, so a session started here asks the TV about HDR10 rather than the handheld
 * panel. setLaunchDisplayId is allowed for any app on a public display (a plugged-in HDMI/DP screen),
 * so there is no root and no permission behind it — but the system may still decline, and a display can
 * vanish between the check and the call. Anything going wrong falls back to an ordinary launch with a
 * toast rather than leaving the user with no game.
 */
internal fun launchOnExternalDisplay(activity: Activity, shortcut: Shortcut, intent: Intent): Boolean {
    if (!com.winhub.hernandez.display.ExternalDisplay.launchOnTv(shortcut)) return false
    val display = com.winhub.hernandez.display.ExternalDisplay.find(activity) ?: return false
    val options = com.winhub.hernandez.display.ExternalDisplay.launchOptions(display.displayId) ?: return false
    // The caller's intent is handed back to it untouched if this fails, so remember what it built:
    // NEW_TASK is ours to add for the TV launch, and ours to take away again.
    val callerHadNewTask = (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
    return try {
        // A task on another display needs its own task; the session also has to be told WHICH display it
        // was aimed at, so it can tell "the TV was unplugged" from any other configuration change.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_DISPLAY_ID, display.displayId)
        // The handheld's companion screen goes up BEFORE the session, never after: Android focuses the
        // display whose activity was started LAST, and the physical controller follows that focus - so
        // starting it the other way round would take the pad off the TV at launch, the very problem the
        // screen is there to explain. It carries a copy of the finished intent, which is what its "Send
        // input back to the TV" button replays. The session takes the screen down again itself if the
        // system declines the TV (a refusal nothing here can see) or when the game ends.
        com.winhub.hernandez.display.TvCompanionActivity.show(activity, shortcut.name, display, Intent(intent))
        activity.startActivity(intent, options)
        true
    } catch (_: Exception) {
        // Refused (an untrusted/private display, a vendor policy): drop the TV bits and let the caller
        // start it the ordinary way, but say why the game is not on the TV. BOTH bits — the extra and
        // NEW_TASK — or the ordinary startActivity below runs a singleTask activity with a flag no
        // normal launch sets, which is its own source of odd task/back-stack behaviour.
        // The companion went up first, so take it down: this game is about to open on the handheld.
        com.winhub.hernandez.display.TvCompanionActivity.dismiss("the TV launch was refused")
        intent.removeExtra(com.winhub.hernandez.display.ExternalDisplay.EXTRA_DISPLAY_ID)
        if (!callerHadNewTask) intent.removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Toast.makeText(
            activity,
            "Couldn't start this game on ${com.winhub.hernandez.display.ExternalDisplay.title(display)} — opening on the handheld instead.",
            Toast.LENGTH_LONG,
        ).show()
        false
    }
}

private fun addToHomeScreen(context: Context, shortcut: Shortcut) {
    if (shortcut.getExtra("uuid").isEmpty()) shortcut.genUUID()
    try {
        val sm = ContextCompat.getSystemService(context, ShortcutManager::class.java)
        if (sm != null && sm.isRequestPinShortcutSupported) {
            val intent = Intent(context, XServerDisplayActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("container_id", shortcut.container.id)
                putExtra("shortcut_path", shortcut.file.path)
            }
            val bmp: Bitmap = shortcut.icon
                ?: BitmapFactory.decodeResource(context.resources, com.winhub.hernandez.R.drawable.icon_wine)
            val info = ShortcutInfo.Builder(context, shortcut.getExtra("uuid"))
                .setShortLabel(shortcut.name)
                .setLongLabel(shortcut.name)
                .setIcon(Icon.createWithBitmap(bmp))
                .setIntent(intent)
                .build()
            sm.requestPinShortcut(info, null)
        }
    } catch (_: Exception) {}
}

internal fun exportShortcut(context: Context, shortcut: Shortcut) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val uriString = prefs.getString("shortcuts_export_path_uri", null)

    val shortcutsDir: File = if (uriString != null) {
        val folderUri = Uri.parse(uriString)
        val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
        if (pickedDir == null || !pickedDir.canWrite()) {
            Toast.makeText(context, "Cannot write to the selected folder", Toast.LENGTH_SHORT).show()
            return
        }
        File(FileUtils.getFilePathFromUri(context, folderUri))
    } else {
        File(SettingsFragment.DEFAULT_SHORTCUT_EXPORT_PATH)
    }

    if (!shortcutsDir.exists() && !shortcutsDir.mkdirs()) {
        Toast.makeText(context, "Failed to create default directory", Toast.LENGTH_SHORT).show()
        return
    }

    val exportFile = File(shortcutsDir, shortcut.file.name)
    val fileExists = exportFile.exists()

    try {
        val lines = mutableListOf<String>()
        var containerIdFound = false
        BufferedReader(FileReader(shortcut.file)).use { reader ->
            reader.lineSequence().forEach { line ->
                if (line.startsWith("container_id:")) {
                    lines += "container_id:${shortcut.container.id}"
                    containerIdFound = true
                } else {
                    lines += line
                }
            }
        }
        if (!containerIdFound) lines += "container_id:${shortcut.container.id}"

        FileWriter(exportFile, false).use { w ->
            lines.forEach { w.write("$it\n") }
        }

        Toast.makeText(
            context,
            if (fileExists) "Shortcut updated at ${exportFile.path}" else "Shortcut exported to ${exportFile.path}",
            Toast.LENGTH_LONG,
        ).show()
    } catch (_: IOException) {
        Toast.makeText(context, "Failed to export shortcut", Toast.LENGTH_LONG).show()
    }
}


/**
 * Marks a game whose files live on removable storage (SD card / USB) rather than internal.
 *
 * Worth surfacing because it explains behaviour the user would otherwise have to guess at: a game
 * on a card is slower to load, and it disappears entirely if the card is removed.
 */
@Composable
private fun EosBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1A73E8))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "EOS",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Marks a shortcut whose store source is the Epic Games Store (storeSource=epic). Deliberately a
 * neutral dark-grey pill so it reads as distinct from the blue EOS badge when both appear together.
 */
@Composable
private fun EpicBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A2A))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "EPIC",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Marks a shortcut whose store source is GOG (storeSource=gog, or — for untagged legacy GOG installs
 * — an exec path under `gog_games`). GOG-brand purple pill, sized like the EPIC/EOS pills.
 */
@Composable
private fun GogBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF7A2FBB))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "GOG",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Marks a shortcut whose store source is Steam (storeSource=steam, or — for untagged legacy Steam
 * installs — an exec path under `steam_games`; see [isSteamOriginShortcut]). Steam-brand dark navy
 * pill, sized identically to the EPIC/EOS/GOG pills.
 */
@Composable
private fun SteamBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1B2838))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "STEAM",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Marks a user-added game — imported via the "+" button or File Manager → Add as shortcut — as
 * distinct from a store-library game. Teal pill, sized like the STEAM/EPIC/EOS/GOG pills. See
 * [isCustomOriginShortcut] for the detection rule.
 */
@Composable
private fun XboxBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF107C10))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "XBOX",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AmazonBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFE47911))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "AMAZON",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** The Linux runtime's own pill: these entries are not user-added games and are not from a store. */
@Composable
private fun LinuxBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF5C4B8A)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "LINUX",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

/** Amazon-brand orange pill, sized identically to the EPIC/EOS/GOG/STEAM/CUSTOM pills. */
@Composable
private fun CustomBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A8C82))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "CUSTOM",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * True when a shortcut is a GOG game. GOG shortcuts are UNTAGGED (StarLaunchBridge stamps no
 * `storeSource` for the legacy GOG overload), so the load-bearing signal is the exec path living
 * under `gog_games` (installs at `imagefs/gog_games/…` → `Z:\gog_games\…`); the `storeSource==gog`
 * branch is defensive / forward-compat. Mirrors CustomSaveVault.isCustom's GOG exclusion.
 */
private fun isGogShortcut(shortcut: Shortcut): Boolean =
    shortcut.getExtra("storeSource") == "gog" ||
        (shortcut.path?.contains("gog_games", ignoreCase = true) == true)

/**
 * True for a user-added game (the "+" button or File Manager → Add as shortcut). New manual imports
 * are tagged `storeSource=custom` (ExeShortcutImporter.writeExeShortcut); older untagged imports are
 * inferred as custom when they match NO recognized store — no store tag, and not on the legacy
 * `steam_games`/`gog_games` exec paths. Any shortcut carrying a non-custom store tag (steam/epic/gog/…)
 * is excluded, so store-library games never show the CUSTOM badge.
 */
private fun isCustomOriginShortcut(shortcut: Shortcut): Boolean {
    val src = shortcut.getExtra("storeSource", "")
    if (src.isNotEmpty() && src != "custom") return false
    if (isSteamOriginShortcut(shortcut)) return false
    if (isGogShortcut(shortcut)) return false
    if (isAmazonShortcut(shortcut)) return false
    return true
}

/**
 * True when a shortcut is an Amazon Games title. New Amazon shortcuts are tagged
 * `storeSource=amazon` (StarLaunchBridge store overload); pre-tagging ones are recognised by the exec
 * path living under the Amazon install root (`imagefs/Amazon/<title>/…` → `Z:\Amazon\…`).
 */
@Composable
private fun XboxRuntimeRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Use the app's Xbox runtime", fontSize = 14.sp)
            Text(
                "This game ships Microsoft's own Xbox runtime, which needs Windows' Gaming Services. " +
                        "Turn off only to try the game's own.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

internal fun isXboxShortcut(shortcut: Shortcut): Boolean = shortcut.getExtra("storeSource") == "xbox"

/** Launch method "Stream" on an Xbox shortcut: Xbox Cloud Gaming, nothing starts in Wine. */
internal fun streamXboxShortcut(activity: Activity, shortcut: Shortcut) {
    activity.startActivity(com.winhub.hernandez.store.xbox.XboxCloudPlayerActivity.intent(activity,
        shortcut.getExtra("xboxProductId", ""), shortcut.name ?: ""))
}

/**
 * An Xbox entry whose launch does not start Wine: a remembered Stream pick (Xbox Cloud Gaming), or an entry
 * added to Games before it was downloaded (its launch opens the game's store page to install it). True when handled.
 */
internal fun launchXboxOutsideWine(activity: Activity, shortcut: Shortcut): Boolean {
    if (!isXboxShortcut(shortcut)) return false
    if (shortcut.getExtra("launchMode", "") == "Stream" && shortcut.getExtra("launchModeRemembered", "") == "1") {
        streamXboxShortcut(activity, shortcut)
        return true
    }
    if (com.winhub.hernandez.store.xbox.XboxCloudShortcuts.isNotInstalled(shortcut)) {
        com.winhub.hernandez.store.xbox.XboxCloudShortcuts.openDownload(activity, shortcut)
        return true
    }
    return false
}

internal fun isAmazonShortcut(shortcut: Shortcut): Boolean {
    if (shortcut.getExtra("storeSource") == "amazon") return true
    val p = shortcut.path ?: return false
    return AMAZON_ROOT_RE.containsMatchIn(p)
}

private val AMAZON_ROOT_RE = Regex("""(^|[\\/])Amazon[\\/]""")

/**
 * EPIC + EOS + GOG pills clustered for the top-left corner of a shortcut's cover art. Caller aligns
 * and insets this (Alignment.TopStart, ~6dp); each pill keeps its own opaque background for contrast.
 */
// The stages of the "Copy to Drive C" flow, in order: confirm the source root, resolve a
// destination collision, run the background copy, then offer to delete the original.
private enum class CopyToCPhase { CONFIRM, OVERWRITE, COPYING, DELETE_ORIGINAL }

/**
 * Shared coordinator for "Copy to Drive C…". Fed a [target] shortcut by either entry point (the ⋮
 * menu item or the editor's Storage-row button); [onFinished] clears that target and refreshes the
 * list. Owns the whole flow and all its dialogs:
 *   1. Locate the exe on disk. Already on C: (or unmappable) → toast + finish, no dialogs.
 *   2. Confirm-source: propose the plausible game root, show its size, let the user Change folder…
 *      Validates the chosen folder is an ancestor of the exe so the repoint is correct.
 *   3. Free-space check against the data partition (drive_c lives under filesDir).
 *   4. Background copy (cancelable progress) into drive_c/Games/<name>, with an overwrite prompt.
 *   5. Repoint the shortcut to C:\Games\<name>\… — ONLY after a fully successful copy.
 *   6. Offer to delete the original folder (Move) vs keep it (Copy).
 * On cancel or any failure the partial destination is deleted and the shortcut is left untouched.
 */
@Composable
internal fun CopyToDriveCCoordinator(
    target: Shortcut?,
    onFinished: () -> Unit,
) {
    if (target == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Parsed once per opened game. exeAndroid == null => the drive letter isn't mapped, so the
    // files can't be located; onDriveC => it's already where we'd copy it to.
    val info = remember(target) { CopyGameToDriveC.parse(target) }
    val actionable = info.exeAndroid != null && !info.onDriveC

    // Step 1 short-circuits — fire a toast and bail before any dialog shows.
    LaunchedEffect(target) {
        if (info.onDriveC) {
            Toast.makeText(context, "\"${target.name}\" already runs from Drive C.", Toast.LENGTH_SHORT).show()
            onFinished()
        } else if (info.exeAndroid == null) {
            Toast.makeText(context, "Couldn't locate this game's files on disk.", Toast.LENGTH_LONG).show()
            onFinished()
        }
    }
    if (!actionable) return
    val exeAndroid = info.exeAndroid!!

    var phase by remember(target) { mutableStateOf(CopyToCPhase.CONFIRM) }
    // Chosen/confirmed source root — defaults to the plausible game root, user can Change folder….
    var sourceRoot by remember(target) { mutableStateOf(CopyGameToDriveC.defaultSourceRoot(exeAndroid)) }
    var sourceSize by remember(target) { mutableStateOf<Long?>(null) } // null = still computing
    var validationError by remember(target) { mutableStateOf<String?>(null) }
    // Copy progress + cooperative cancel handle (read by copyTree on the IO thread).
    val cancelFlag = remember(target) { java.util.concurrent.atomic.AtomicBoolean(false) }
    var progress by remember(target) { mutableStateOf<CopyGameToDriveC.Progress?>(null) }

    fun fmt(bytes: Long) = android.text.format.Formatter.formatShortFileSize(context, bytes)

    // Size the source folder (background) and re-validate ancestry whenever the root changes.
    LaunchedEffect(sourceRoot) {
        validationError = if (!CopyGameToDriveC.isAncestor(sourceRoot, exeAndroid)) {
            "That folder doesn't contain this game's .exe. Pick the folder the game runs from."
        } else null
        sourceSize = null
        val size = withContext(Dispatchers.IO) { CopyGameToDriveC.folderSize(sourceRoot) }
        sourceSize = size
    }

    // Folder picker (real absolute path via buildDirIntent) so the user can navigate to the true root.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            InAppFilePicker.pickedPath(result.data)?.let { sourceRoot = File(it) }
        }
    }

    // Kick the background copy of [sourceRoot] into [dest]; [cleanFirst] wipes an existing dest
    // (overwrite). Drives phase COPYING → DELETE_ORIGINAL on success, or finishes on cancel/failure.
    fun startCopy(dest: File, cleanFirst: Boolean) {
        cancelFlag.set(false)
        val total = sourceSize ?: 0L
        val src = sourceRoot
        progress = CopyGameToDriveC.Progress(0, total, "")
        phase = CopyToCPhase.COPYING
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    if (cleanFirst) dest.deleteRecursively()
                    CopyGameToDriveC.copyTree(src, dest, total, { cancelFlag.get() }) { p -> progress = p }
                    CopyGameToDriveC.repoint(target, src, dest)
                        ?: throw java.io.IOException("Couldn't repoint the shortcut to the copied files.")
                }
            }
            outcome.fold(
                onSuccess = { phase = CopyToCPhase.DELETE_ORIGINAL },
                onFailure = { e ->
                    if (e is CopyGameToDriveC.CancelledException) {
                        Toast.makeText(context, "Copy cancelled — nothing was changed.", Toast.LENGTH_SHORT).show()
                    } else {
                        android.util.Log.e("CopyToDriveC", "copy/repoint failed for ${target.name}", e)
                        Toast.makeText(
                            context,
                            "Copy failed: ${e.message ?: "unknown error"}. Nothing was changed.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    onFinished()
                },
            )
        }
    }

    // Validate ancestry + free space, resolve a destination collision, then start.
    fun confirmAndCopy() {
        if (!CopyGameToDriveC.isAncestor(sourceRoot, exeAndroid)) {
            validationError = "That folder doesn't contain this game's .exe. Pick the folder the game runs from."
            return
        }
        val size = sourceSize ?: return // still sizing — Copy is disabled, but guard anyway
        if (!CopyGameToDriveC.hasRoomFor(context, size)) {
            validationError = "Not enough space on Drive C. Needs ${fmt(size)} plus headroom, but only " +
                "${fmt(CopyGameToDriveC.freeBytes(context))} is free."
            return
        }
        val dest = CopyGameToDriveC.destRootFor(target.container, sourceRoot.name)
        if (dest.exists()) phase = CopyToCPhase.OVERWRITE else startCopy(dest, cleanFirst = false)
    }

    when (phase) {
        CopyToCPhase.CONFIRM -> OutlinedAlertDialog(
            onDismissRequest = onFinished,
            title = { Text("Copy \"${target.name}\" to Drive C") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Copies the game onto this container's C: drive (native app storage) and points " +
                            "the shortcut there. Fixes games that stall streaming from shared storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    Text("Folder to copy", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                    Text(
                        sourceRoot.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        text = when {
                            validationError != null -> "Size —"
                            sourceSize == null -> "Calculating size…"
                            else -> "Size: ${fmt(sourceSize!!)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        "Pick the folder that holds everything the game needs, not just the .exe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    validationError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                    }
                    OutlinedButton(onClick = {
                        val driveC = File(target.container.rootDir, ".wine/drive_c").takeIf { it.isDirectory }
                        folderPicker.launch(
                            InAppFilePicker.buildDirIntent(
                                context, "Select the game's folder", initialDir = sourceRoot.absolutePath,
                                driveCPath = driveC?.absolutePath,
                            )
                        )
                    }) {
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Change folder…")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmAndCopy() },
                    enabled = validationError == null && sourceSize != null,
                ) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = onFinished) { Text("Cancel") } },
        )

        CopyToCPhase.OVERWRITE -> OutlinedAlertDialog(
            onDismissRequest = onFinished,
            title = { Text("Already on Drive C") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "A folder named \"${sourceRoot.name}\" already exists under C:\\${CopyGameToDriveC.GAMES_SUBDIR}. " +
                            "Overwrite it, or keep both by copying to a new folder?",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        val dest = CopyGameToDriveC.autoRenamedDest(
                            CopyGameToDriveC.destRootFor(target.container, sourceRoot.name)
                        )
                        startCopy(dest, cleanFirst = false)
                    }) { Text("Keep both (new folder)") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startCopy(CopyGameToDriveC.destRootFor(target.container, sourceRoot.name), cleanFirst = true)
                }) { Text("Overwrite") }
            },
            dismissButton = { TextButton(onClick = onFinished) { Text("Cancel") } },
        )

        CopyToCPhase.COPYING -> {
            val p = progress
            val total = p?.totalBytes ?: 0L
            val done = p?.copiedBytes ?: 0L
            OutlinedAlertDialog(
                onDismissRequest = {}, // no accidental dismiss mid-copy; use Cancel
                title = { Text("Copying to Drive C…") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (total > 0L) {
                            LinearProgressIndicator(
                                progress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${fmt(done)} / ${fmt(total)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        p?.currentFile?.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { cancelFlag.set(true) }) { Text("Cancel") }
                },
            )
        }

        CopyToCPhase.DELETE_ORIGINAL -> OutlinedAlertDialog(
            onDismissRequest = {
                Toast.makeText(context, "\"${target.name}\" now runs from Drive C.", Toast.LENGTH_SHORT).show()
                onFinished()
            },
            title = { Text("Copied to Drive C") },
            text = {
                Text(
                    "\"${target.name}\" now runs from Drive C. Delete the original folder to free " +
                        "${sourceSize?.let { fmt(it) } ?: "space"}, or keep it as a backup?",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val src = sourceRoot
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { src.deleteRecursively() } }
                        Toast.makeText(context, "Moved to Drive C — original deleted.", Toast.LENGTH_SHORT).show()
                        onFinished()
                    }
                }) { Text("Delete original", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = {
                    Toast.makeText(context, "Copied to Drive C — original kept.", Toast.LENGTH_SHORT).show()
                    onFinished()
                }) { Text("Keep original") }
            },
        )
    }
}

/**
 * "Change executable" coordinator. Given a [target] shortcut, opens the in-app file picker at the
 * current exe's folder (filtered to .exe/.lnk/.desktop, with the container's Drive-C rail available),
 * then repoints the shortcut at the picked file through the shared [CopyGameToDriveC.setShortcutExe]
 * — asking first whether to keep or clear the existing launch arguments. [onFinished] clears the
 * target and refreshes the list (Shortcut.path is final — same reload pattern as copy-to-C). The
 * shortcut is touched ONLY on a valid, mappable pick; anything else warns and aborts unchanged.
 */
@Composable
internal fun ChangeExecutableCoordinator(
    target: Shortcut?,
    onFinished: () -> Unit,
) {
    if (target == null) return
    val context = LocalContext.current

    val info = remember(target) { CopyGameToDriveC.parse(target) }
    // A picked exe awaiting the keep/clear-args choice (null until then).
    var pendingExe by remember(target) { mutableStateOf<File?>(null) }

    // Extensions accepted as a launch target (matches the InAppFilePicker.SHORTCUT filter).
    fun isSupported(name: String) =
        name.substringAfterLast('.', "").lowercase() in setOf("exe", "lnk", "desktop")

    // Write the new exe via the shared helper, toast the outcome, and finish. argsSuffix "" clears
    // the launch args; a leading-space suffix keeps them.
    fun applyExe(exe: File, argsSuffix: String) {
        val win = CopyGameToDriveC.setShortcutExe(target, exe, argsSuffix)
        if (win == null) {
            Toast.makeText(
                context,
                "That file isn't on a drive this container can reach. Keep the game on storage the " +
                    "container maps (internal/SD, or its C: drive).",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            Toast.makeText(context, "\"${target.name}\" now launches $win", Toast.LENGTH_LONG).show()
        }
        onFinished()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            onFinished(); return@rememberLauncherForActivityResult
        }
        val file = InAppFilePicker.pickedPath(result.data)?.let { File(it) }
        if (file == null || !file.isFile || !isSupported(file.name)) {
            Toast.makeText(context, "Pick a .exe, .lnk, or .desktop file.", Toast.LENGTH_LONG).show()
            onFinished(); return@rememberLauncherForActivityResult
        }
        // Ask about launch args only when there are some to keep; otherwise apply straight away.
        if (info.argsSuffix.isBlank()) applyExe(file, "") else pendingExe = file
    }

    // Launch the picker once when this target opens, seeded at the current exe's folder.
    LaunchedEffect(target) {
        val driveC = File(target.container.rootDir, ".wine/drive_c").takeIf { it.isDirectory }
        picker.launch(
            InAppFilePicker.buildIntent(
                context,
                InAppFilePicker.SHORTCUT,
                "Select the game's .exe",
                initialDir = info.exeAndroid?.parentFile?.absolutePath,
                driveCPath = driveC?.absolutePath,
            )
        )
    }

    // Keep-or-clear the launch arguments (default keep). Mirrors the backup-format chooser idiom.
    pendingExe?.let { exe ->
        OutlinedAlertDialog(
            onDismissRequest = onFinished,
            title = { Text("Launch arguments") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "This shortcut launches with arguments:",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        info.argsSuffix.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { applyExe(exe, info.argsSuffix) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text("Keep arguments", color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Launch the new .exe with the same arguments",
                            color = OnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { applyExe(exe, "") }
                            .padding(vertical = 8.dp),
                    ) {
                        Text("Clear arguments", color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Launch the new .exe with no arguments",
                            color = OnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onFinished) { Text("Cancel") } },
        )
    }
}

/**
 * Marks a Steam title that runs through EA Desktop (shortcut tag `eaSupport=1`, see [EaSupport]) —
 * it launches via SteamLite and needs the one-time EA setup. EA-brand red pill, sized like the others.
 */
@Composable
private fun EaBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFC8102E))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "EA",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ShortcutBadgeOverlay(
    showSteam: Boolean = false,
    showEpic: Boolean,
    showEos: Boolean,
    showGog: Boolean,
    showAmazon: Boolean = false,
    showXbox: Boolean = false,
    showCustom: Boolean = false,
    showEa: Boolean = false,
    showLinux: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (!showSteam && !showEpic && !showEos && !showGog && !showAmazon && !showXbox && !showCustom && !showEa
        && !showLinux) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showSteam) SteamBadge()
        if (showEa) EaBadge()
        if (showEpic) EpicBadge()
        if (showEos) EosBadge()
        if (showGog) GogBadge()
        if (showAmazon) AmazonBadge()
        if (showXbox) XboxBadge()
        if (showCustom) CustomBadge()
        if (showLinux) LinuxBadge()
    }
}

/**
 * Reads the cached {@code eos} identification extra for a shortcut and, if it has never
 * been computed, kicks a bounded background filesystem scan (off the render path) that
 * caches the result on the shortcut. Returns the current badge state, which flips to
 * true once a scan finds EOS markers. Source-agnostic: works for any store origin.
 */
@Composable
private fun rememberEosBadge(shortcut: Shortcut): Boolean {
    var eos by remember(shortcut.path) { mutableStateOf(shortcut.getExtra("eos") == "1") }
    LaunchedEffect(shortcut.path) {
        if (!shortcut.hasExtra("eos")) {
            com.winhub.hernandez.store.EpicEosDetector.scanShortcutIfNeeded(shortcut) {
                eos = shortcut.getExtra("eos") == "1"
            }
        }
    }
    return eos
}

@Composable
private fun SdCardBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.SdCard,
            contentDescription = "On SD card",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "SD",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * A selectable option card, styled to match the File Manager's rows and the game cards behind the
 * dialog — outlined rounded rectangle, optional leading icon, title over a dimmer subtitle — so the
 * import menus read as part of the same surface rather than as bare dialog text.
 */
@Composable
private fun MenuOptionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = OnSurface, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * One row of the bulk-import confirm list: cover art, resolved title, and the folder it came from.
 *
 * Games already in the container are shown dimmed and can't be ticked, so re-scanning the same
 * library after adding a few titles reads as "these are already here" rather than silently
 * duplicating them. Uncertain picks are badged instead of hidden — the scanner still chose its best
 * candidate, but the user gets told which ones are worth a second look before committing.
 */
@Composable
private fun ScannedGameRow(
    candidate: GameFolderScanner.Candidate,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onChangeExe: () -> Unit,
) {
    val alpha = if (candidate.alreadyAdded) 0.45f else 1f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(enabled = enabled, onClick = onToggle),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(candidate.coverUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(alpha),
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize().background(SurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp) }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize().background(SurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        candidate.name.take(1).uppercase(),
                        color = OnSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).alpha(alpha)) {
            Text(
                candidate.name,
                color = OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                candidate.folder.name,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                candidate.alreadyAdded -> Text(
                    "Already added",
                    color = OnSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                // Flagged rows name the exe, because that is what the user is being asked to judge.
                // Capped at two lines: a long name like AIO-Graphics-Test-64bit.exe otherwise wraps
                // to three and makes the cards uneven.
                candidate.uncertain -> Text(
                    "Check this one — ${candidate.exe.name}",
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
                else -> Text(
                    candidate.exe.name,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        // Available on EVERY row, not just flagged ones — a confident pick can still be the wrong
        // one, and the user is the only one who actually knows.
        if (!candidate.alreadyAdded) {
            TextButton(onClick = onChangeExe, enabled = enabled) { Text("Change") }
        }
    }
    }
}
