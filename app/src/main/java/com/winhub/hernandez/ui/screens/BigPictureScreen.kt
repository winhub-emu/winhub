@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.preference.PreferenceManager
import com.winhub.hernandez.R
import com.winhub.hernandez.XServerDisplayActivity
import com.winhub.hernandez.XrActivity
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.communityconfigs.CommunityConfigApply
import com.winhub.hernandez.communityconfigs.CommunityConfigRef
import com.winhub.hernandez.communityconfigs.CanonicalDevice
import com.winhub.hernandez.communityconfigs.CanonicalGame
import com.winhub.hernandez.communityconfigs.DeviceIdentity
import com.winhub.hernandez.communityconfigs.GameMatcher
import com.winhub.hernandez.communityconfigs.UploadedConfigsStore.UploadedConfig
import com.winhub.hernandez.communityconfigs.WorkerConfigEntry
import com.winhub.hernandez.ui.screens.adrenodownload.AdrenoDriverDownloadSheet
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.DownloadManagerActivity
import com.winhub.hernandez.store.GoldbergComponent
import com.winhub.hernandez.store.SteamGameUpdater
import com.winhub.hernandez.store.GoldbergMode
import com.winhub.hernandez.store.GoldbergPatcher
import com.winhub.hernandez.store.SteamDatabase
import com.winhub.hernandez.store.SteamLiteComponent
import com.winhub.hernandez.store.SteamLoginActivity
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.SteamSessionManager
import com.winhub.hernandez.store.StarLaunchBridge
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.ui.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Big Picture — a couch/TV launcher shown at startup instead of the normal UI when the pref is on.
// Full-bleed Compose rebuild of the old XML BigPictureActivity: blurred hero background, a centred
// hero for the selected game and a scrolling cover carousel, all driven controller-first. Reached as
// the Screen.BigPicture NavHost route (see AppNavGraph / MainActivity), so every rail is a plain
// navController.navigate(...).

private enum class BpZone { RAIL, PLAY, CAROUSEL }
private enum class BpSheet { GAME_OPTIONS, TOOLS, POWER }

private val CARD_WIDTH = 84.dp
private val CARD_HEIGHT = 126.dp

@Composable
fun BigPictureScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val manager = remember { ContainerManager(context) }
    var shortcuts by remember { mutableStateOf<List<Shortcut>>(manager.loadShortcuts()) }
    var selectedIndex by remember { mutableStateOf(0) }
    // Community configs — reuses the phone UI's browser + account dialogs (now internal). Applies
    // to the currently selected game; the smart missing-component install sub-flow stays on the
    // phone UI for now, so the result dialog just surfaces the outcome message.
    val communityVm: ShortcutsViewModel = viewModel()
    var showCommunity by remember { mutableStateOf(false) }
    // Per-game community sheet (Options → "Community configs"): scoped to the selected game, unlike the
    // top-rail globe which opens the all-games CommunityCatalogBrowser.
    var showGameCommunity by remember { mutableStateOf(false) }
    // The shortcut a config-import file picker will apply to (set when the picker is launched from the
    // per-game sheet; read back in the launcher callback).
    var communityImportTarget by remember { mutableStateOf<Shortcut?>(null) }
    var showBpAccount by remember { mutableStateOf(false) }
    var communityApplyResult by remember { mutableStateOf<CommunityConfigApply.ConfigApplyResult?>(null) }
    var communityApplying by remember { mutableStateOf(false) }
    // Stage 2 smart install: the pick to re-apply after a missing component/driver is installed,
    // and which install sheet is open.
    var lastCommunityPick by remember { mutableStateOf<CommunityPick?>(null) }
    var bpInstallFor by remember { mutableStateOf<CommunityConfigApply.MissingComponent?>(null) }
    var bpDriverFor by remember { mutableStateOf<CommunityConfigApply.MissingDriver?>(null) }
    // Steam launch-method popup (M3), couch UI: the Steam-origin game whose SteamLite-vs-Goldberg
    // chooser is open, plus the download-on-launch progress overlay (shared for both components).
    var launchChoiceFor by remember { mutableStateOf<Shortcut?>(null) }
    // SteamLite launch pre-flight (session → network → cloud saves → update check BEFORE the container opens);
    // every RealSteam launch routes through it. Mirrors ShortcutsScreen.
    var preflightFor by remember { mutableStateOf<Shortcut?>(null) }
    var componentDownloadFor by remember { mutableStateOf<Shortcut?>(null) }
    var componentDownloadLabel by remember { mutableStateOf("") }
    var componentDownloadProgress by remember { mutableFloatStateOf(0f) }
    // Goldberg launch (persist sub-mode → component on demand → patch → launch); shared by the popup's
    // Goldberg pick and the pre-flight's "Launch with Goldberg" fallback. Mirrors ShortcutsScreen.
    fun launchWithGoldberg(s: Shortcut, gm: GoldbergMode) {
        val appId = steamAppIdOf(s)
        SteamPrefs.init(context)
        SteamPrefs.setGoldbergMode(appId, gm)
        val applyThenLaunch = {
            Thread({
                val installDir = runCatching {
                    SteamDatabase.getInstance(context).getGame(appId)?.installDir
                }.getOrNull().orEmpty()
                activity.runOnUiThread {
                    if (installDir.isEmpty()) {
                        launchShortcut(activity, s)
                    } else {
                        GoldbergPatcher.applyModeAsync(context, appId, installDir, s.name, gm) { _, _ ->
                            launchShortcut(activity, s)
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
    // SteamLite launch: package on demand, then the pre-flight dialog (which starts the container).
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
    // RealSteam manual maintenance (the launch popup's Verify / Update buttons — SteamLite roadmap #3),
    // couch UI: the game whose maintenance run is in flight, its progress (<0 = indeterminate "checking",
    // 0..1 while working), a label, a dialog title, and the cancel handle. NO longer on the launch path —
    // update/verify are explicit now, so a run never launches the game. Mirrors ShortcutsScreen.
    var steamUpdateFor by remember { mutableStateOf<Shortcut?>(null) }
    var steamUpdateProgress by remember { mutableFloatStateOf(-1f) }
    var steamUpdateLabel by remember { mutableStateOf("") }
    var steamUpdateTitle by remember { mutableStateOf("") }
    var steamUpdateHandle by remember { mutableStateOf<SteamGameUpdater.UpdateHandle?>(null) }
    // "Check for updates" is check-THEN-offer (never auto-applies): the cheap probe runs first, and a found
    // delta (or a can't-tell-offline result) parks the game + its status here so the couch confirm sheet
    // below can offer to apply it. null = no offer pending. Mirrors ShortcutsScreen.
    var steamUpdateOffer by remember { mutableStateOf<Pair<Shortcut, SteamGameUpdater.UpdateStatus>?>(null) }
    // Manual RealSteam maintenance: run a delta [update] or a full "verify integrity" [verify] pass,
    // reusing the shared progress modal. Standalone — it never launches the game; the outcome is a toast.
    // Cancellable via steamUpdateHandle.
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
            if (result != SteamGameUpdater.Result.CANCELLED && msg.isNotBlank()) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
        steamUpdateHandle =
            if (verify) SteamGameUpdater.verifyFiles(context, appId, progress, done)
            else SteamGameUpdater.updateNow(context, appId, progress, done)
    }
    // "Check for updates": a cheap, network-free probe (shown in the shared progress modal as the animated
    // "Checking…" indeterminate phase), then BRANCH — never auto-apply. Up-to-date / not-installed inform
    // via a toast; an available (or can't-tell-offline) result opens the couch confirm sheet, whose
    // [Update now] / [Check online] hands off to runSteamMaintenance. onResult lands on the main thread.
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

    // Decoded covers keyed by shortcut name; guarded by a plain in-flight set so we never re-fetch.
    val coverCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val inFlight = remember { HashSet<String>() }

    // Config-import picker for the per-game community sheet's "Import" action. Applies the picked `.json`
    // straight to the stashed target shortcut; the outcome surfaces through the shared apply-result dialog.
    val communityImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            com.winhub.hernandez.util.InAppFilePicker.pickedUri(result.data)?.let { uri ->
                communityImportTarget?.let { target ->
                    // An imported file is a one-off (no CommunityPick), so clear any stale pick that the
                    // missing-component re-apply flow would otherwise pick up.
                    lastCommunityPick = null
                    communityApplying = true
                    communityVm.importConfigFile(uri, target) { res ->
                        communityApplying = false
                        communityApplyResult = res
                        shortcuts = manager.loadShortcuts()
                    }
                }
            }
        }
    }
    val launchConfigImport: (Shortcut) -> Unit = { target ->
        communityImportTarget = target
        communityImportLauncher.launch(
            com.winhub.hernandez.util.InAppFilePicker.buildIntent(
                context, com.winhub.hernandez.util.InAppFilePicker.JSON, "Select a config .json"
            )
        )
    }

    val listState = rememberLazyListState()

    var zone by remember { mutableStateOf(BpZone.CAROUSEL) }
    var railIndex by remember { mutableStateOf(0) } // 0 = Community, 1 = App Settings, 2 = Tools, 3 = Power
    var playIndex by remember { mutableStateOf(0) } // in the PLAY zone: 0 = Play, 1 = Game options

    var activeSheet by remember { mutableStateOf<BpSheet?>(null) }
    var editShortcut by remember { mutableStateOf(false) }

    // A SINGLE focus target on the root Box. No sub-element (rail / Play / carousel) is focusable, so
    // Compose never paints its own default focus ring — every "focus" you see is our own zone/index
    // state drawn as borders. This is what kills the phantom highlight that used to appear over the
    // spec chips when pressing D-pad up.
    val rootFocus = remember { FocusRequester() }
    val grabFocus: () -> Unit = { runCatching { rootFocus.requestFocus() } }

    // Lazily resolve a cover for a shortcut: custom path first, then SteamGridDB (saved as the custom
    // cover so it persists), mirroring the Shortcuts screen scrape flow.
    val ensureCover: (Shortcut) -> Unit = { s ->
        if (!coverCache.containsKey(s.name) && inFlight.add(s.name)) {
            scope.launch(Dispatchers.IO) {
                val img = loadCover(s)
                withContext(Dispatchers.Main) {
                    if (img != null) coverCache[s.name] = img
                    inFlight.remove(s.name)
                }
            }
        }
    }

    // Immersive system bars while on Big Picture; restored on leaving.
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Refresh shortcuts on resume (new games, edited settings/covers) and re-grab focus after we
    // return from launching a game.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shortcuts = manager.loadShortcuts()
                if (selectedIndex > shortcuts.lastIndex) selectedIndex = shortcuts.lastIndex.coerceAtLeast(0)
                grabFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Seed the root focus once so key events route to us from the first frame.
    LaunchedEffect(Unit) { grabFocus() }

    // Re-grab focus after a sheet / dialog closes so D-pad keeps working (the launch-method popup and
    // its download overlay are their own windows too, so wait until they're gone before re-grabbing).
    LaunchedEffect(activeSheet, editShortcut, launchChoiceFor, componentDownloadFor, preflightFor) {
        if (activeSheet == null && !editShortcut && launchChoiceFor == null && componentDownloadFor == null
            && preflightFor == null) grabFocus()
    }

    // Preload the selected cover so the hero + blurred background are ready.
    LaunchedEffect(selectedIndex, shortcuts) {
        shortcuts.getOrNull(selectedIndex)?.let { ensureCover(it) }
    }

    // Keep the selected carousel item roughly centred.
    LaunchedEffect(selectedIndex, shortcuts.size) {
        if (shortcuts.isNotEmpty()) {
            val viewport = listState.layoutInfo.viewportSize.width
            val itemPx = with(density) { CARD_WIDTH.roundToPx() }
            val offset = if (viewport > 0) -(viewport / 2 - itemPx / 2) else 0
            runCatching { listState.animateScrollToItem(selectedIndex.coerceIn(0, shortcuts.lastIndex), offset) }
        }
    }

    val selected = shortcuts.getOrNull(selectedIndex)
    val heroCover = selected?.let { coverCache[it.name] }

    // Every game opens the source-adaptive launch-method popup first (Steam → SteamLite/Goldberg/Raw;
    // Epic/GOG/Custom → Raw-only), unless a remembered choice already exists. Mirrors the phone UI's
    // requestLaunch (ShortcutsScreen): a remembered pick launches DIRECTLY (the launchMode extra is honored
    // by the launch pipeline; "Raw" is a plain launch).
    val onLaunch: () -> Unit = {
        selected?.let { sc ->
            val remembered = sc.getExtra("launchMode", "").isNotEmpty() &&
                sc.getExtra("launchModeRemembered", "") == "1"
            when {
                // A remembered RealSteam pick still runs the SteamLite pre-flight (session check).
                remembered && sc.getExtra("launchMode", "") == "RealSteam" && isSteamOriginShortcut(sc) ->
                    launchWithSteamLite(sc)
                remembered -> launchShortcut(activity, sc)
                else -> launchChoiceFor = sc
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Community overlays own Back first (they sit above sheets). Each of these is its own
                // window (Dialog / ModalBottomSheet) that handles its OWN D-pad internally, so here we
                // only need the safety-net Back handling in case the root ever receives it — everything
                // else stays with the overlay.
                if (showCommunity || showGameCommunity || showBpAccount || communityApplyResult != null
                        || bpInstallFor != null || bpDriverFor != null) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.ButtonB, Key.Back -> {
                            when {
                                bpInstallFor != null -> bpInstallFor = null
                                bpDriverFor != null -> bpDriverFor = null
                                communityApplyResult != null -> communityApplyResult = null
                                showBpAccount -> showBpAccount = false
                                showGameCommunity -> showGameCommunity = false
                                else -> showCommunity = false
                            }
                            true
                        }
                        else -> false
                    }
                }
                // While a sheet is up, only handle the close gesture; everything else is the sheet's.
                if (activeSheet != null || editShortcut) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.ButtonB, Key.Back -> { activeSheet = null; editShortcut = false; true }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        when (zone) {
                            BpZone.CAROUSEL -> if (selectedIndex > 0) selectedIndex--
                            BpZone.RAIL     -> if (railIndex > 0) railIndex--
                            BpZone.PLAY     -> playIndex = 0   // Play
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        when (zone) {
                            BpZone.CAROUSEL -> if (selectedIndex < shortcuts.lastIndex) selectedIndex++
                            BpZone.RAIL     -> if (railIndex < 3) railIndex++
                            BpZone.PLAY     -> playIndex = 1   // Game options
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        zone = when (zone) {
                            BpZone.CAROUSEL -> { playIndex = 0; BpZone.PLAY }
                            BpZone.PLAY     -> BpZone.RAIL
                            BpZone.RAIL     -> BpZone.RAIL
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        zone = when (zone) {
                            BpZone.RAIL     -> { playIndex = 0; BpZone.PLAY }
                            BpZone.PLAY     -> BpZone.CAROUSEL
                            BpZone.CAROUSEL -> BpZone.CAROUSEL
                        }
                        true
                    }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                        when (zone) {
                            // A on the carousel launches the highlighted game (fastest couch path).
                            BpZone.CAROUSEL -> onLaunch()
                            // In the PLAY zone A activates whichever button is highlighted.
                            BpZone.PLAY -> if (playIndex == 0) onLaunch()
                                           else if (selected != null) activeSheet = BpSheet.GAME_OPTIONS
                            BpZone.RAIL -> when (railIndex) {
                                0 -> showCommunity = true
                                1 -> navController.navigate(Screen.Settings.route)
                                2 -> activeSheet = BpSheet.TOOLS
                                3 -> activeSheet = BpSheet.POWER
                            }
                        }
                        true
                    }
                    // Y = game options for the selected game (from carousel or the hero).
                    Key.ButtonY -> {
                        if (selected != null && zone != BpZone.RAIL) activeSheet = BpSheet.GAME_OPTIONS
                        true
                    }
                    Key.ButtonR1 -> { activeSheet = BpSheet.TOOLS; true }
                    Key.ButtonB, Key.Back -> true // stay in Big Picture
                    else -> false
                }
            },
    ) {
        // 1. Blurred hero background, cross-faded on selection change.
        Crossfade(
            targetState = (selected?.name ?: "") to heroCover,
            animationSpec = tween(350),
            label = "bp-hero-bg",
        ) { (_, cover) ->
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.background,
                                )
                            )
                        )
                )
            }
        }
        // Dark scrim for text legibility (top transparent → bottom near-black).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // 2. Foreground: rail on top, hero fills the middle as a WEIGHTED sibling, carousel is a
        // compact bottom strip. Column stacking (not overlapping alignment) guarantees the hero's
        // Play / Game-options buttons can never be drawn under the carousel — the earlier version
        // reserved carousel space with bottom-padding, which overflowed and clipped them.
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            // Global rail (top-end).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                RailButton(
                    icon = Icons.Filled.Public,
                    label = "Community Configs",
                    focused = zone == BpZone.RAIL && railIndex == 0,
                    onClick = { zone = BpZone.RAIL; railIndex = 0; showCommunity = true },
                )
                Spacer(Modifier.width(12.dp))
                RailButton(
                    icon = Icons.Filled.Settings,
                    label = "App Settings",
                    focused = zone == BpZone.RAIL && railIndex == 1,
                    onClick = { zone = BpZone.RAIL; railIndex = 1; navController.navigate(Screen.Settings.route) },
                )
                Spacer(Modifier.width(12.dp))
                RailButton(
                    icon = Icons.Filled.Apps,
                    label = "Tools",
                    focused = zone == BpZone.RAIL && railIndex == 2,
                    onClick = { zone = BpZone.RAIL; railIndex = 2; activeSheet = BpSheet.TOOLS },
                )
                Spacer(Modifier.width(12.dp))
                RailButton(
                    icon = Icons.Filled.PowerSettingsNew,
                    label = "Power",
                    focused = zone == BpZone.RAIL && railIndex == 3,
                    onClick = { zone = BpZone.RAIL; railIndex = 3; activeSheet = BpSheet.POWER },
                )
            }

            if (shortcuts.isEmpty()) {
                // Empty state.
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No games yet", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navController.navigate(Screen.Games.route) }) { Text("Add games") }
                }
            } else {
                // Hero — fills the band between the rail and the carousel (a weighted sibling, so its
                // buttons can never render under the carousel). Cover scales to the band, capped so it
                // doesn't get huge on tablets.
                // Per-game spec = the SAME resolved settings the game cards and the pre-launch screen
                // show (buildLaunchSpec: renderer / driver / DXVK / VKD3D / frame-gen / x86 backend),
                // so Big Picture matches the rest of the app instead of the old wrapper/box64 chips.
                val spec = selected?.let { buildLaunchSpec(it, context) }
                val chips = spec?.let {
                    listOf(
                        "Renderer" to it.rendererLabel,
                        "Driver" to it.driverLabel,
                        "DXVK" to it.dxvkVersion,
                        "VKD3D" to it.vkd3dVersion,
                        "Frame Gen" to it.frameGenLabel,
                        "x86" to it.backendLabel,
                    ).filter { p -> p.second.isNotBlank() }
                }.orEmpty()

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val coverH = maxHeight.coerceAtMost(260.dp)
                    val coverW = coverH * 2f / 3f            // 2:3 poster ratio
                    Row(modifier = Modifier.fillMaxWidth().height(coverH), verticalAlignment = Alignment.CenterVertically) {
                        CoverCard(cover = heroCover, modifier = Modifier.width(coverW).fillMaxHeight(), selected = false)
                        Spacer(Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            // Title + spec take the space ABOVE the buttons and clip if a game has a very
                            // long title or many chips (e.g. GTA IV's 6 chips); the buttons below are
                            // pinned and therefore always visible — the previous overflow clipped them.
                            Column(modifier = Modifier.weight(1f).clipToBounds()) {
                                Text(
                                    text = selected?.name ?: "",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!spec?.meta.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(spec!!.meta, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                val stats = playtimeStats(context, selected?.name ?: "")
                                Text(
                                    "Played ${stats.first}×  ·  ${stats.second}",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                )
                                if (chips.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        chips.forEach { (l, v) -> SpecChip(l, v) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            // Play + Game options: pinned at the bottom of the hero. Each shows our own
                            // focus border when it's the highlighted item in the PLAY zone (Left/Right
                            // toggles), and both stay tappable for touch-only users.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val playFocused = zone == BpZone.PLAY && playIndex == 0
                                val optionsFocused = zone == BpZone.PLAY && playIndex == 1
                                Button(
                                    onClick = { zone = BpZone.PLAY; playIndex = 0; onLaunch() },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .then(
                                            if (playFocused)
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(16.dp))
                                            else Modifier
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                OutlinedButton(
                                    onClick = { zone = BpZone.PLAY; playIndex = 1; if (selected != null) activeSheet = BpSheet.GAME_OPTIONS },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .then(
                                            if (optionsFocused)
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(16.dp))
                                            else Modifier
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Game options", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Carousel — compact bottom strip.
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(shortcuts) { index, s ->
                        val isSel = index == selectedIndex
                        val scale by animateFloatAsState(if (isSel) 1.12f else 1f, label = "bp-card-scale")
                        LaunchedEffect(s.name) { ensureCover(s) }
                        CoverCard(
                            cover = coverCache[s.name],
                            modifier = Modifier
                                .width(CARD_WIDTH)
                                .height(CARD_HEIGHT)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clickable { zone = BpZone.CAROUSEL; selectedIndex = index },
                            selected = isSel,
                        )
                    }
                }
            }
        }
    }

    // ── Sheets ────────────────────────────────────────────────────────────────
    when (activeSheet) {
        BpSheet.GAME_OPTIONS -> selected?.let { s ->
            GameOptionsSheet(
                shortcut = s,
                onDismiss = { activeSheet = null },
                onEditShortcut = { activeSheet = null; editShortcut = true },
                onContainerSettings = {
                    activeSheet = null
                    navController.navigate("container_detail?id=${s.container.id}")
                },
                onChangeCover = { bmp ->
                    s.saveCustomCoverArt(bmp)
                    coverCache[s.name] = bmp.asImageBitmap()
                },
                onRemoveCover = {
                    s.removeCustomCoverArt()
                    coverCache.remove(s.name)
                    inFlight.remove(s.name)
                },
                onCommunityConfigs = {
                    // Open the GAME-SCOPED community sheet for this shortcut (auto-match + this game's
                    // configs), not the all-games browser (that stays on the top-rail globe).
                    selectedIndex = shortcuts.indexOfFirst { it.name == s.name }.coerceAtLeast(0)
                    activeSheet = null
                    showGameCommunity = true
                },
            )
        }
        BpSheet.TOOLS -> ToolsSheet(
            onDismiss = { activeSheet = null },
            onFileManager = { activeSheet = null; navController.navigate(Screen.FileManager.route) },
            onWrappers = { activeSheet = null; navController.navigate(Screen.Wrappers.route) },
            onDownloads = {
                activeSheet = null
                context.startActivity(Intent(context, DownloadManagerActivity::class.java))
            },
        )
        BpSheet.POWER -> PowerSheet(
            onDismiss = { activeSheet = null },
            onExit = {
                activeSheet = null
                navController.navigate(Screen.Games.route) {
                    popUpTo(Screen.BigPicture.route) { inclusive = true }
                }
            },
            onTurnOff = {
                activeSheet = null
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putBoolean("enable_big_picture_mode", false).apply()
                navController.navigate(Screen.Games.route) {
                    popUpTo(Screen.BigPicture.route) { inclusive = true }
                }
            },
            onQuit = { activity.finishAffinity() },
        )
        null -> {}
    }

    if (editShortcut && selected != null) {
        ShortcutSettingsDialogScreen(
            shortcut = selected,
            onDismiss = {
                editShortcut = false
                shortcuts = manager.loadShortcuts()
            },
        )
    }

    // Applies a picked config to the selected game; a re-apply after installing a missing piece
    // reuses this, re-resolving against what's now on disk.
    fun applyCommunityPick(pick: CommunityPick) {
        val target = selected ?: return
        communityApplying = true
        val onDone: (CommunityConfigApply.ConfigApplyResult) -> Unit = { res ->
            communityApplying = false
            communityApplyResult = res
            shortcuts = manager.loadShortcuts()
        }
        when (pick) {
            is CommunityPick.File -> communityVm.applyCommunityConfigFile(target, pick.ref, onDone)
            is CommunityPick.Device -> communityVm.applyCommunityConfig(target, pick.game, pick.device, onDone)
        }
    }

    // ── Community configs ──────────────────────────────────────────────────
    if (showCommunity) {
        CommunityCatalogBrowser(
            vm = communityVm,
            onDismiss = { showCommunity = false },
            onPick = { pick -> lastCommunityPick = pick; applyCommunityPick(pick) },
            onMyAccount = { showBpAccount = true },
        )
    }

    // Per-game community sheet (Options → "Community configs"). Applies a picked config to the SELECTED
    // game via the same applyCommunityPick pipeline as the browser, so the missing-component / driver
    // install + re-apply flow (and the apply-result dialog) is shared. The sheet closes on apply so the
    // result dialog reads clearly over the couch UI.
    if (showGameCommunity) {
        selected?.let { s ->
            GameCommunitySheet(
                vm = communityVm,
                shortcut = s,
                onApply = { pick ->
                    lastCommunityPick = pick
                    showGameCommunity = false
                    applyCommunityPick(pick)
                },
                onImport = { launchConfigImport(s) },
                onDismiss = { showGameCommunity = false },
            )
        } ?: run { showGameCommunity = false }
    }

    if (showBpAccount) {
        MyAccountDialog(
            vm = communityVm,
            onDismiss = { showBpAccount = false },
            onOpenMyUploads = { showBpAccount = false },  // Stage 2: dedicated My-uploads manager
            onLoggedIn = {},
        )
    }

    communityApplyResult?.let { res ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { communityApplyResult = null },
            title = { Text(if (res.ok) "Config applied" else "Couldn't apply") },
            text = {
                Column {
                    Text(res.message, color = Color.White.copy(alpha = 0.85f))
                    if (res.ok) Text(
                        "Applied to \"${selected?.name.orEmpty()}\".",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                    )
                    // Missing components — one tap opens the normal download sheet for that type;
                    // on install we re-apply the same pick, which re-resolves against what's now on
                    // disk and picks the component up.
                    res.missingComponents.forEach { mc ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Needs ${mc.label}${mc.wanted.ifBlank { "" }.let { if (it.isBlank()) "" else " ($it)" }}",
                                color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.TextButton(onClick = { bpInstallFor = mc }) { Text("Install") }
                        }
                    }
                    res.missingDrivers.forEach { md ->
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Needs GPU driver ${md.wanted}",
                                color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.TextButton(onClick = { bpDriverFor = md }) { Text("Install") }
                        }
                    }
                    res.advisories.forEach { Text("• $it", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { communityApplyResult = null }) { Text("Done") }
            },
        )
    }

    // Install a missing component, then re-apply the pick so the version is written back.
    bpInstallFor?.let { mc ->
        ContentDownloadSheet(
            contentType = mc.type,
            onDismiss = { bpInstallFor = null },
            onContentChanged = { lastCommunityPick?.let { applyCommunityPick(it) } },
        )
    }

    // Full adrenotools driver browser for a missing GPU driver; re-apply on install.
    bpDriverFor?.let { _ ->
        AdrenoDriverDownloadSheet(
            onDismiss = { bpDriverFor = null },
            onDriverInstalled = {
                bpDriverFor = null
                lastCommunityPick?.let { applyCommunityPick(it) }
            },
        )
    }

    // ── Steam launch-method popup (M3), couch UI: SteamLite (real Steam / VAC) vs Goldberg (offline) ─
    launchChoiceFor?.let { s ->
        val appId = steamAppIdOf(s)
        LaunchMethodSheet(
            shortcut = s,
            onDismiss = { launchChoiceFor = null },
            // Verify runs a full re-validate pass directly. "Check for updates" is check-THEN-offer: probe
            // first, then a couch confirm sheet lets the user choose to apply — it never auto-updates. Both
            // dismiss the sheet first (so no dialog is layered behind the ModalBottomSheet's window).
            onUpdateFiles = { launchChoiceFor = null; checkForUpdatesThenOffer(s) },
            onVerifyFiles = { launchChoiceFor = null; runSteamMaintenance(s, verify = true) },
            onLaunch = { mode, goldbergMode, remember, controllerPassthrough, vacLaunch ->
                // Persist the choice on the shortcut (contract literals: launchMode ∈ RealSteam/Goldberg/Raw,
                // launchModeRemembered="1"), then stage the picked component and launch.
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
                    "Raw" -> {
                        // Raw: run the game's .exe directly, no Steam layer (Epic/GOG/Custom, or a Steam
                        // game the user chose to run raw). launchMode="Raw" is inert to the launch pipeline
                        // (only "RealSteam" stages the agent), so this is a plain launch.
                        launchShortcut(activity, s)
                    }
                    else -> {
                        // RealSteam (SteamLite): package on demand, then the pre-flight dialog (session →
                        // cloud saves → update check) and only then the container. Update/verify stay the
                        // popup's manual buttons; the pre-flight only OFFERS an update.
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
            onLaunch = { preflightFor = null; launchShortcut(activity, s, preflightDone = true) },
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

    // Blocking progress dialog while the picked component downloads before launch.
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
    // Cancellable — cancelling aborts the pass and stays in Big Picture (a run never launches the game).
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

    // "Check for updates" outcome (couch UI): a delta is (or might be) due — offer to apply it. Reuses the
    // fully D-pad + touch navigable BpSheetScaffold (a focusable bottom sheet), NOT a bare touch dialog.
    // Never auto-updates; [Update now] / [Check online] runs the authoritative updateNow pass.
    steamUpdateOffer?.let { (s, status) ->
        val available = status.state == SteamGameUpdater.State.UPDATE_AVAILABLE
        val title = if (available) {
            if (status.installedBuild > 0L && status.liveBuild > 0L)
                "Update available — build ${status.installedBuild} → ${status.liveBuild}"
            else "Update available for ${s.name}"
        } else {
            "Couldn't check ${s.name} from cached data — check online?"
        }
        BpSheetScaffold(
            title = title,
            rows = listOf(
                BpRow(Icons.Filled.Download, if (available) "Update now" else "Check online") {
                    steamUpdateOffer = null
                    runSteamMaintenance(s, verify = false)
                },
                BpRow(Icons.Filled.Close, if (available) "Later" else "Cancel") {
                    steamUpdateOffer = null
                },
            ),
            onDismiss = { steamUpdateOffer = null },
        )
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f))
            .then(
                if (focused)
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(50))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White,
        )
    }
}

@Composable
private fun CoverCard(
    cover: ImageBitmap?,
    modifier: Modifier = Modifier,
    selected: Boolean,
) {
    Card(
        modifier = modifier.then(
            if (selected)
                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            else Modifier
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 12.dp else 4.dp),
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.cover_art_placeholder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun SpecChip(label: String, value: String) {
    // Compact so the full spec (up to 6 chips, e.g. GTA IV) packs into at most two short rows that
    // clear the pinned Play / Game-options buttons instead of getting clipped.
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, lineHeight = 11.sp)
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun GameOptionsSheet(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    onEditShortcut: () -> Unit,
    onContainerSettings: () -> Unit,
    onChangeCover: (android.graphics.Bitmap) -> Unit,
    onRemoveCover: () -> Unit,
    onCommunityConfigs: () -> Unit,
) {
    val context = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            com.winhub.hernandez.util.InAppFilePicker.pickedUri(result.data)?.let { uri ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)?.let(onChangeCover)
                    }
                }
            }
        }
    }
    // Rows built inline (not remembered) so the passed-in callbacks never go stale. The Change-cover row
    // captures the launcher; the Remove-cover row only appears when there's a custom cover to remove.
    val rows = buildList {
        add(BpRow(Icons.Filled.Edit, "Edit shortcut", onEditShortcut))
        add(BpRow(Icons.Filled.Tune, "Container settings", onContainerSettings))
        add(BpRow(Icons.Filled.Public, "Community configs", onCommunityConfigs))
        add(BpRow(Icons.Filled.Image, "Change cover art") {
            coverPicker.launch(
                com.winhub.hernandez.util.InAppFilePicker.buildIntent(
                    context, com.winhub.hernandez.util.InAppFilePicker.IMAGES, "Select cover art"
                )
            )
        })
        if (!shortcut.customCoverArtPath.isNullOrEmpty()) {
            add(BpRow(Icons.Filled.Delete, "Remove cover art") { onRemoveCover(); onDismiss() })
        }
    }
    BpSheetScaffold(title = shortcut.name, rows = rows, onDismiss = onDismiss)
}

@Composable
private fun ToolsSheet(
    onDismiss: () -> Unit,
    onFileManager: () -> Unit,
    onWrappers: () -> Unit,
    onDownloads: () -> Unit,
) {
    BpSheetScaffold(
        title = "Tools",
        rows = listOf(
            BpRow(Icons.Filled.FolderOpen, "File Manager", onFileManager),
            BpRow(Icons.Filled.Layers, "Manage Wrappers", onWrappers),
            BpRow(Icons.Filled.Download, "Downloads", onDownloads),
        ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun PowerSheet(
    onDismiss: () -> Unit,
    onExit: () -> Unit,
    onTurnOff: () -> Unit,
    onQuit: () -> Unit,
) {
    BpSheetScaffold(
        title = "Power",
        rows = listOf(
            BpRow(Icons.Filled.ExitToApp, "Exit Big Picture", onExit),
            BpRow(Icons.Filled.PowerSettingsNew, "Turn off Big Picture mode", onTurnOff),
            BpRow(Icons.Filled.Close, "Quit", onQuit),
        ),
        onDismiss = onDismiss,
    )
}

// A single config card in the Big Picture community sheet: the pick to apply plus the data to render it
// (a worker [entry] OR an offline [device] row).
private data class BpCommunityCard(
    val pick: CommunityPick,
    val entry: WorkerConfigEntry?,
    val device: CanonicalDevice?,
    val isMatch: Boolean,
)

// Game-scoped community configs for Big Picture — the couch-mode counterpart of the phone Games screen's
// per-shortcut "Community configs" dialog. Unlike the top-rail globe (which opens the ALL-GAMES
// CommunityCatalogBrowser), this is scoped to ONE shortcut: auto-matches the game, lists that game's
// uploaded configs (device-ranked, with a "Matches my device" filter) and applies a picked one straight
// to this shortcut through [onApply] — which routes back to Big Picture's shared applyCommunityPick, so
// the missing-component / driver install + re-apply flow still fires. Share + Upload reuse the same VM
// one-shots the phone dialog uses; Import re-uses Big Picture's config-import launcher via [onImport].
// Fully controller-navigable with the same single-focus, index-based model the rest of Big Picture uses.
//
// DEFERRED (couch-mode TODO): the "My uploads" manager isn't wired here — it needs the full uploads-list
// UI that only lives on the phone screen; it stays reachable from the top globe → My account entry point.
@Composable
private fun GameCommunitySheet(
    vm: ShortcutsViewModel,
    shortcut: Shortcut,
    onApply: (CommunityPick) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Offline-first auto-match for THIS shortcut, then its uploaded configs fetched directly off the VM
    // (so the whole list is plain state the D-pad handler can index) — the same fetch the phone's
    // rememberGameConfigs uses, including this shortcut's own sanitized folder so the user's OWN upload
    // shows up before it lands in the canonical index.
    var match by remember(shortcut) { mutableStateOf<CommunityMatchResult?>(null) }
    var matchLoading by remember(shortcut) { mutableStateOf(true) }
    var entries by remember(shortcut) { mutableStateOf<List<Pair<String, WorkerConfigEntry>>>(emptyList()) }
    var matchesMine by remember(shortcut) { mutableStateOf(false) }
    var search by remember(shortcut) { mutableStateOf("") }
    var searchResults by remember(shortcut) { mutableStateOf<List<CanonicalGame>>(emptyList()) }

    LaunchedEffect(shortcut) {
        matchLoading = true
        vm.matchCommunityConfigs(shortcut) { match = it; matchLoading = false }
    }
    LaunchedEffect(match?.match) {
        val g = match?.match
        if (g != null) {
            val myFolder = shortcut.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            vm.fetchGameConfigs(g, listOf(myFolder)) { entries = it }
        } else entries = emptyList()
    }

    // Upload busy-state + replace-confirm (identical flow to the phone dialog).
    var uploading by remember { mutableStateOf(false) }
    var uploadStarted by remember { mutableStateOf(false) }
    var replacePrompt by remember { mutableStateOf<Triple<UploadedConfig, () -> Unit, () -> Unit>?>(null) }

    val game = match?.match
    val uSoc = match?.userSoc
    val uGpu = match?.userGpu
    val hwEnabled = uSoc != null || uGpu != null
    val searching = search.trim().length >= 2

    // The flat, in-render-order config list. Worker entries when we have them (filtered by the match chip),
    // else the offline per-device fallback. Empty while searching (the results list replaces it).
    val cards: List<BpCommunityCard> = remember(match, entries, matchesMine, searching) {
        val g = game
        when {
            searching || g == null -> emptyList()
            entries.isNotEmpty() -> {
                val shown = if (!matchesMine) entries
                    else entries.filter { GameMatcher.hardwareMatchesUser(uSoc, uGpu, listOf(it.second.device, it.second.soc)) }
                shown.map { (folder, e) ->
                    val isMatch = hwEnabled && GameMatcher.hardwareMatchesUser(uSoc, uGpu, listOf(e.device, e.soc))
                    BpCommunityCard(
                        pick = CommunityPick.File(
                            g,
                            CommunityConfigRef(g, folder, e.filename, e.sha.ifBlank { null }, ns = if (e.appSource == "bannerlator") "bannerlator" else ""),
                            e,
                        ),
                        entry = e,
                        device = null,
                        isMatch = isMatch,
                    )
                }
            }
            else -> {
                val devs = if (!matchesMine) match?.rankedDevices.orEmpty()
                    else match?.rankedDevices.orEmpty().filter { GameMatcher.deviceMatchesUser(it, uSoc, uGpu) }
                devs.map { d ->
                    val isMatch = hwEnabled && GameMatcher.deviceMatchesUser(d, uSoc, uGpu)
                    BpCommunityCard(CommunityPick.Device(g, d), entry = null, device = d, isMatch = isMatch)
                }
            }
        }
    }

    // Share this shortcut's effective config: export off-main, then hand the file to the system share
    // sheet via the app's existing FileProvider authority (the same one the updater / save-share use).
    val shareConfig: () -> Unit = {
        vm.exportShortcutConfig(shortcut) { res ->
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
    }
    val uploadConfig: () -> Unit = {
        uploading = true
        uploadStarted = false
        vm.uploadShortcutConfig(
            shortcut,
            onExisting = { existing, proceed, cancel -> replacePrompt = Triple(existing, proceed, cancel) },
            onStart = { uploadStarted = true },
            onResult = { _, msg ->
                uploading = false
                uploadStarted = false
                replacePrompt = null
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            },
        )
    }

    // 2D D-pad model — the sheet is an ordered list of ROWS; a row has 1 or 2 columns:
    //   row 0        = [Share, Import]          (2 cols)
    //   row 1        = [Upload to community]     (1 col)
    //   row 2        = [Search field]            (1 col)   — highlight-reachable, A is a no-op (text = IME)
    //   rows 3..N    = one config card each      (1 col)
    //   row last     = [Close]                   (1 col)
    // Up/Down move rows (clamped); Left/Right move columns WITHIN a row (no-op on 1-col rows). focusCol is
    // the DESIRED column and is clamped per-row on read, which gives the "remember last column" nicety for
    // free (drop to Upload from Import, come back up, and you land on Import again).
    val rowUpload = 1
    val rowSearch = 2
    val firstCardRow = 3
    val closeRow = firstCardRow + cards.size
    val rowCount = closeRow + 1
    val colsOf: (Int) -> Int = { r -> if (r == 0) 2 else 1 }
    var focusRow by remember { mutableStateOf(0) }
    var focusCol by remember { mutableStateOf(0) }
    val curCol = focusCol.coerceIn(0, colsOf(focusRow) - 1)
    val focusRequester = remember { FocusRequester() }
    // A on the search row hands focus to the field + pops the soft keyboard; B pulls it back down before
    // it dismisses the sheet (so the first B closes the keyboard, a second B closes the sheet).
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Tracks whether the search field actually holds focus — a reliable "keyboard is up" signal that,
    // unlike WindowInsets.isImeVisible, works inside the sheet's own window. B uses it to close the
    // keyboard first (and hand focus back to the sheet) before a later B closes the sheet.
    var searchFieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // Keep the row in range as the list grows/shrinks (match resolves, filter toggles, search opens).
    LaunchedEffect(rowCount) { if (focusRow > rowCount - 1) focusRow = (rowCount - 1).coerceAtLeast(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // Effective column for the CURRENT row (focusCol is the remembered desire).
                    val col = focusCol.coerceIn(0, colsOf(focusRow) - 1)
                    when (event.key) {
                        Key.DirectionUp -> { if (focusRow > 0) focusRow--; true }
                        Key.DirectionDown -> { if (focusRow < rowCount - 1) focusRow++; true }
                        Key.DirectionLeft -> { if (col > 0) focusCol = col - 1; true }
                        Key.DirectionRight -> { if (col < colsOf(focusRow) - 1) focusCol = col + 1; true }
                        Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                            when (focusRow) {
                                0 -> if (col == 0) shareConfig() else onImport()
                                rowUpload -> if (!uploading) uploadConfig()
                                rowSearch -> { searchFocus.requestFocus(); keyboard?.show() }
                                closeRow -> onDismiss()
                                else -> cards.getOrNull(focusRow - firstCardRow)?.let { onApply(it.pick) }
                            }
                            true
                        }
                        // First B closes the keyboard (and returns focus to the sheet for D-pad nav);
                        // once the field no longer holds focus, B dismisses the sheet.
                        Key.ButtonB, Key.Back -> {
                            if (searchFieldFocused) { keyboard?.hide(); focusRequester.requestFocus() } else onDismiss()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            SheetTitle("Community configs")
            Text(
                shortcut.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            // Actions row: Share + Import; Upload full-width below.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DpadHighlight(focused = focusRow == 0 && curCol == 0, modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = shareConfig, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
                DpadHighlight(focused = focusRow == 0 && curCol == 1, modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FileUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DpadHighlight(focused = focusRow == rowUpload) {
                OutlinedButton(onClick = { if (!uploading) uploadConfig() }, enabled = !uploading, modifier = Modifier.fillMaxWidth()) {
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (uploadStarted) "Uploading…" else "Preparing…")
                    } else {
                        Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Upload to community")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DpadHighlight(focused = focusRow == rowSearch) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { q ->
                        search = q
                        if (q.trim().length >= 2) vm.searchCommunityGames(q) { searchResults = it }
                        else searchResults = emptyList()
                    },
                    label = { Text("Search all games") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(searchFocus)
                        .onFocusChanged { searchFieldFocused = it.isFocused }
                        // Guaranteed B handling while the field holds focus (in case the parent's preview
                        // doesn't fire): close the keyboard and hand focus back to the sheet.
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonB || e.key == Key.Back)) {
                                keyboard?.hide(); focusRequester.requestFocus(); true
                            } else false
                        },
                )
            }
            Spacer(Modifier.height(10.dp))
            when {
                // Search results (touch-select) replace the match list while a query is active.
                searching -> {
                    if (searchResults.isEmpty()) {
                        Text("No games match \"$search\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        searchResults.forEach { cg ->
                            CommunityCard(onClick = {
                                // Manually correcting the game also sticks for next time (issue #167).
                                vm.rememberCommunityGame(shortcut, cg)
                                vm.selectCommunityGame(cg) { match = it }
                                search = ""
                                searchResults = emptyList()
                            }) {
                                Text(
                                    cg.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                CommunityStoreBadge(isSteam = cg.isSteam)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                matchLoading -> Text("Matching \"${shortcut.name}\"…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                game == null -> Text("No auto-match for \"${shortcut.name}\" — search above to pick one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    // Genuine tie (e.g. "Dragon Age" → Inquisition vs Veilguard): offer the alternatives
                    // so the user picks the right game instead of silently trusting the top candidate.
                    // Touch-select (not wired into the D-pad row model — kept simple); the choice is
                    // remembered per shortcut so it only asks once. (issue #167)
                    val tieOptions = match?.alternatives.orEmpty()
                    if (tieOptions.size > 1) {
                        Text(
                            "Which game is this?",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        tieOptions.forEach { alt ->
                            CommunityCard(onClick = {
                                vm.rememberCommunityGame(shortcut, alt)
                                vm.selectCommunityGame(alt) { match = it }
                            }) {
                                Text(
                                    alt.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                CommunityStoreBadge(isSteam = alt.isSteam)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            game.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        CommunityStoreBadge(isSteam = game.isSteam)
                    }
                    val devWord = if (game.devices.size == 1) "device" else "devices"
                    val cfgWord = if (game.configCount == 1) "config" else "configs"
                    Text("${game.configCount} $cfgWord · ${game.devices.size} $devWord", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("Your device: ${deviceHeaderLabel(DeviceIdentity.deviceModel(), match?.userHardwareLabel)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    FilterChip(
                        selected = matchesMine,
                        onClick = { matchesMine = !matchesMine },
                        label = { Text("Matches my device") },
                        enabled = hwEnabled,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (cards.isEmpty()) {
                        Text(
                            if (matchesMine) "No uploaded configs match your device." else "No configs listed.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        cards.forEachIndexed { i, c ->
                            DpadHighlight(focused = focusRow == firstCardRow + i) {
                                val entry = c.entry
                                val device = c.device
                                if (entry != null) {
                                    CommunityConfigEntryCard(entry = entry, isMatch = c.isMatch) { onApply(c.pick) }
                                } else if (device != null) {
                                    CommunityCard(onClick = { onApply(c.pick) }) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                device.model.ifBlank { "Unknown device" },
                                                color = if (c.isMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val sub = listOf(device.gpu, device.soc).filter { it.isNotBlank() }.joinToString(" · ")
                                            if (sub.isNotEmpty()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DpadHighlight(focused = focusRow == closeRow) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }

    // Replace-confirm before overwriting an existing upload (mirrors the phone flow: Replace resumes the
    // parked upload coroutine, Cancel unwinds it cleanly so the button doesn't stay stuck busy).
    replacePrompt?.let { (existing, proceed, cancel) ->
        val dismissReplace = {
            replacePrompt = null
            uploading = false
            uploadStarted = false
            cancel()
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = dismissReplace,
            title = { Text("Replace your shared config?") },
            text = { Text("You've already shared a config for \"${existing.game}\". Replace it with this one?") },
            confirmButton = { TextButton(onClick = { replacePrompt = null; proceed() }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = dismissReplace) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    focused: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Our own D-pad highlight (a translucent primary fill), drawn UNDER the clickable so touch
            // still works exactly as before. Same focus idiom as the hero buttons / RailButton.
            .then(
                if (focused) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// One selectable row in a static Big Picture sheet (Game options / Tools / Power).
private data class BpRow(val icon: ImageVector, val label: String, val onClick: () -> Unit)

// Index-based D-pad wiring for a static bottom-sheet row list. A bottom sheet is its OWN window, so the
// root screen's onPreviewKeyEvent never receives its keys — the sheet therefore owns a single focus
// target of its own (same "one focus target, no per-item FocusRequester" philosophy the root uses).
// Up/Down move the highlight, A/Enter/Center activate the highlighted row, B/Back dismiss. Touch stays
// live because every SheetRow keeps its own clickable.
private fun Modifier.bpSheetDpad(
    focusRequester: FocusRequester,
    index: Int,
    count: Int,
    onMove: (Int) -> Unit,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
): Modifier = this
    .focusRequester(focusRequester)
    .focusable()
    .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> { if (index > 0) onMove(index - 1); true }
            Key.DirectionDown -> { if (index < count - 1) onMove(index + 1); true }
            Key.ButtonA, Key.Enter, Key.DirectionCenter -> { onActivate(); true }
            Key.ButtonB, Key.Back -> { onDismiss(); true }
            else -> false
        }
    }

// A static Big Picture sheet: a title + a list of selectable rows, fully D-pad + touch navigable.
@Composable
private fun BpSheetScaffold(title: String, rows: List<BpRow>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        var focusIndex by remember { mutableStateOf(0) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .bpSheetDpad(
                    focusRequester = focusRequester,
                    index = focusIndex,
                    count = rows.size,
                    onMove = { focusIndex = it },
                    onActivate = { rows.getOrNull(focusIndex)?.onClick?.invoke() },
                    onDismiss = onDismiss,
                ),
        ) {
            SheetTitle(title)
            rows.forEachIndexed { i, r ->
                SheetRow(icon = r.icon, label = r.label, focused = i == focusIndex, onClick = r.onClick)
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────

// (play count, formatted playtime) from the same prefs XServerDisplayActivity writes.
private fun playtimeStats(context: Context, name: String): Pair<Int, String> {
    val prefs = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
    val totalMs = prefs.getLong("${name}_playtime", 0L)
    val playCount = prefs.getInt("${name}_play_count", 0)
    val seconds = (totalMs / 1000) % 60
    val minutes = (totalMs / (1000 * 60)) % 60
    val hours   = (totalMs / (1000 * 60 * 60)) % 24
    val days    = (totalMs / (1000 * 60 * 60 * 24))
    return playCount to String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds)
}

// Resolve a cover: existing custom art first, then a SteamGridDB grid (persisted as the custom cover).
// `internal` (not `private`) so the Games-wall screen (GamesWallScreen.kt) reuses the exact same
// local-first → SteamGridDB resolution instead of forking a second copy.
internal fun loadCover(s: Shortcut): ImageBitmap? {
    val custom = s.customCoverArtPath
    if (!custom.isNullOrEmpty()) {
        val f = File(custom)
        if (f.exists()) BitmapFactory.decodeFile(custom)?.let { return it.asImageBitmap() }
    }
    return try {
        val arr = JSONArray(StarLaunchBridge.sgdbFetchGridsJson(s.name))
        if (arr.length() == 0) return null
        val url = arr.getJSONObject(0).optString("url", "")
        if (url.isEmpty()) return null
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val bmp = BitmapFactory.decodeStream(conn.inputStream)
        conn.disconnect()
        if (bmp != null) {
            s.saveCustomCoverArt(bmp)
            bmp.asImageBitmap()
        } else null
    } catch (_: Exception) {
        null
    }
}

// `internal` so the Games-wall screen launches games through the identical entry point.
// [preflightDone] = the SteamLite pre-flight already pulled cloud saves; the activity skips its own pull.
internal fun launchShortcut(activity: Activity, shortcut: Shortcut, preflightDone: Boolean = false, zoomColor: Int? = null) {
    if (!XrActivity.isEnabled(activity)) {
        val intent = Intent(activity, XServerDisplayActivity::class.java).apply {
            // Home's launch zoom ends on this colour; the loading screen opens from it (see PreloaderOverlayHelper).
            if (zoomColor != null) putExtra(com.winhub.hernandez.ui.PreloaderOverlayHelper.EXTRA_ZOOM_COLOR, zoomColor)
            putExtra("container_id", shortcut.container.id)
            putExtra("shortcut_path", shortcut.file.path)
            putExtra("shortcut_name", shortcut.name)
            putExtra("disableXinput", shortcut.getExtra("disableXinput", "0"))
            if (preflightDone) putExtra(SteamSessionManager.EXTRA_PREFLIGHT_DONE, true)
        }
        // Same "Launch this game on the TV" as the shortcut list (shared helper, so the two launch
        // paths can't drift); false = no TV wanted, none connected, or the system refused it.
        if (!launchOnExternalDisplay(activity, shortcut, intent)) activity.startActivity(intent)
    } else {
        XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.path)
    }
}
