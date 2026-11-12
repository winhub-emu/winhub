package com.winhub.hernandez.store

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import com.winhub.hernandez.R
import com.winhub.hernandez.core.CopyGameToDriveC
import com.winhub.hernandez.ui.deck.XmbKeyboardWindow
import com.winhub.hernandez.ui.deck.xmbSolidBackground
import com.winhub.hernandez.ui.screens.HelpDialog
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import androidx.compose.material3.ModalBottomSheet
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import com.winhub.hernandez.ui.components.Button
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.winhub.hernandez.ui.components.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winhub.hernandez.store.compose.AddResultDialog
import com.winhub.hernandez.store.compose.AddShortcutResult
import com.winhub.hernandez.store.compose.AddToShortcutsRequest
import com.winhub.hernandez.store.compose.DeckAddToShortcuts
import com.winhub.hernandez.store.compose.openShortcutsScreen
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.MediaTab
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreMedia
import com.winhub.hernandez.store.download.formatDownloadSpeed
import com.winhub.hernandez.store.download.formatEta
import com.winhub.hernandez.ui.theme.WinlatorTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** Semantic action carried by the install button; the colour resolves inside the composable
 *  (install/retry = primary, cancel/uninstall = error) so theme presets recolor it live. */
private enum class InstallAction { INSTALL, CANCEL, UNINSTALL, RETRY }

/** Semantic pause-button mode: PAUSE renders as a calm surfaceVariant container, RESUME as primary. */
private enum class PauseAction { PAUSE, RESUME }

/** Semantic install status; colour resolves inside the composable (installed = green,
 *  failed = error, everything else = onSurfaceVariant). */
private enum class GameStatus { NOT_INSTALLED, INSTALLED, CANCELLED, FAILED }

/** The four granular save moves across the three tiers (Cloud ⇄ Library ⇄ Container), plus the two
 *  end-to-end combos the primary UI now leads with: SYNC_FROM = Download+Apply (cloud → into game),
 *  SYNC_TO = Collect+Upload (game → to cloud). The granular four stay for the Advanced expander. */
private enum class CloudMove { DOWNLOAD, UPLOAD, APPLY, COLLECT, SYNC_FROM, SYNC_TO }

/** A pending cloud-save confirm dialog: which move, the target container's label, and (for the
 *  moves that need it) a non-blocking staleness warning line — null when there's nothing to warn. */
private data class CloudConfirm(
    val move: CloudMove,
    val containerLabel: String,
    val stalenessWarning: String?,
)

/** The labeled size breakdown shown under the info chips. Empty strings render nothing. */
private data class SizeBreakdown(
    val downloadLabel: String = "",   // "Download: 4.5 GB"
    val picsLabel: String = "",       // "PICS estimate (Steam): 8.4 GB"
    val freeLabel: String = "",       // "Free space: 23.1 GB" (+ " — won't fit" when applicable)
    val fits: Boolean = true,         // false → render freeLabel in the error color
)

/** One beta branch as shown in the branch picker (a UI projection of SteamDatabase.BranchRow). */
private data class BranchDisplay(
    val name: String,
    val description: String,   // Steam-authored blurb, may be empty
    val timeUpdated: Long,     // epoch seconds of the last build (0 = unknown)
    val pwdRequired: Boolean,  // password-protected beta
    val unlocked: Boolean,     // selectable now (public, or a verified password on file)
)

/** One entry in the DLC tab's full owned-DLC list. `tag` is the human delivery label derived from
 *  the DB `kind` ("Installs with game" for depot/app, "Owned — no separate download" for
 *  entitlement, "Owned" while unresolved, "Not owned" for unowned). DISPLAY-ONLY. */
private data class DlcTabEntry(
    val appId: Int,
    val name: String,
    val owned: Boolean,
    val tag: String,
)

/** Map the steam_dlc `kind` + ownership to the tab's delivery label. */
private fun dlcKindTag(kind: String, owned: Boolean): String = when {
    !owned                                 -> "Not owned"
    kind == "depot" || kind == "app"       -> "Installs with game"
    kind == "entitlement"                  -> "Owned — no separate download"
    else                                   -> "Owned"   // owned but not yet resolved
}

/** Format a branch's build timestamp (epoch seconds) as a readable date; "" when unknown. */
private fun formatBranchUpdated(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    return try {
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(epochSeconds * 1000L))
    } catch (_: Throwable) { "" }
}

class SteamGameDetailActivity : ComponentActivity(), SteamRepository.SteamEventListener {

    companion object {
        const val EXTRA_APP_ID = "steam_app_id"
    }

    private var appId: Int = 0
    private var game by mutableStateOf<SteamGame?>(null)

    @Volatile private var downloadHandle: SteamDepotDownloader.DownloadControl? = null
    private var lastSpeedTier = DownloadSpeedConfig.DEFAULT_TIER  // 24 = Fast

    // UI state
    private var nameText by mutableStateOf("Loading…")
    private var typeText by mutableStateOf("GAME")
    // Headline chip = on-disk footprint (estimate with "~", or the real measured size once installed).
    private var sizeText by mutableStateOf("Size unknown")
    // Breakdown lines under the chips: download (compressed), PICS estimate (labeled), free space.
    private var sizeBreakdown by mutableStateOf(SizeBreakdown())
    // "Includes DLC: <names>" — owned DLC that WILL download (excluded ones dropped); "" hides the line.
    private var includedDlcText by mutableStateOf("")
    // DLC picker: all owned DLC bundled with the game (appId → name), the user's opt-out set, and
    // whether the picker sheet is open. Tapping the "Includes DLC" line opens the sheet.
    private var dlcEntries by mutableStateOf<Map<Int, String>>(emptyMap())
    private var excludedDlc by mutableStateOf<Set<Int>>(emptySet())
    private var showDlcSheet by mutableStateOf(false)
    // Beta-branch selector: known branches (public + betas), the currently selected branch, and the
    // picker-sheet state. Only surfaced when the app actually exposes a non-public branch. branchCheck*
    // drive the "enter access code" flow for locked betas.
    private var branches by mutableStateOf<List<BranchDisplay>>(emptyList())
    private var selectedBranch by mutableStateOf("public")
    private var showBranchSheet by mutableStateOf(false)
    private var branchCheckBusy by mutableStateOf(false)
    private var branchCheckMessage by mutableStateOf<String?>(null)
    // One-shot guard so the manifest-true size resolve fires at most once per detail view.
    private var sizeResolveStarted = false
    private var statusText by mutableStateOf("Not installed")
    private var gameStatus by mutableStateOf(GameStatus.NOT_INSTALLED)
    private var installBtnText by mutableStateOf("Install")
    private var installAction by mutableStateOf(InstallAction.INSTALL)
    private var installBtnEnabled by mutableStateOf(true)
    private var pauseBtnText by mutableStateOf("Pause")
    private var pauseAction by mutableStateOf(PauseAction.PAUSE)
    private var pauseBtnEnabled by mutableStateOf(false)
    private var launchBtnEnabled by mutableStateOf(false)
    private var progressVisible by mutableStateOf(false)
    private var progressValue by mutableIntStateOf(0)
    // Lighter "download" (network) fill that leads the solid install fill. On paused/DB-restored
    // views (compressed progress isn't persisted) it mirrors the install fraction.
    private var downloadProgressValue by mutableIntStateOf(0)
    private var progressText by mutableStateOf("")
    private var progressTextVisible by mutableStateOf(false)

    private var showSpeedPicker by mutableStateOf(false)
    // Removable SD card detected when the download dialog opens (null = none, so the SD option is
    // hidden). Detected off the UI thread in onInstallClicked before the picker is shown.
    private var sdTarget by mutableStateOf<SteamSdInstall.SdTarget?>(null)
    // ── Storage move (gear → Move to SD card / Move to internal storage) ─────────────────────
    // The direction on offer for THIS game, recomputed off the UI thread whenever the install state
    // changes; null hides the gear row (not installed, folder gone, or no card present).
    private var moveStorageLabel by mutableStateOf<String?>(null)
    // The built plan, shown in the confirm dialog. Null = no dialog.
    private var movePlan by mutableStateOf<MoveGameStorage.Plan?>(null)
    // Live progress while a move runs; null = not running.
    private var moveProgress by mutableStateOf<MoveGameStorage.Progress?>(null)
    private var moveGameName by mutableStateOf("")
    // Set true to ask a running move's copy loop to stop at the next file/buffer boundary.
    @Volatile private var moveCancelled = false

    // Non-null while an uninstall is deleting files → shows the blocking progress spinner.
    private var uninstallingName by mutableStateOf<String?>(null)
    // Non-null briefly after an uninstall → themed auto-dismiss confirmation bar (not a Toast).
    private var uninstallResult by mutableStateOf<String?>(null)
    private var showExePicker by mutableStateOf<ExePickerDataGame?>(null)
    private var addToShortcuts by mutableStateOf<AddToShortcutsRequest?>(null)
    private var addResult by mutableStateOf<AddShortcutResult?>(null)
    // Destructive "Cancel & delete download" confirm — gates the guaranteed wipe+reset (gear item).
    // The partial is only nuked once the user confirms; dismiss keeps the download exactly as-is.
    private var showCancelDeleteConfirm by mutableStateOf(false)

    // Goldberg (Steam emulator) state — only meaningful once the game is installed.
    // The component is ONE global download shared by every game; the tier toggle
    // only lights up once it's installed.
    private var goldbergMode by mutableStateOf(GoldbergMode.OFF)
    private var goldbergBusy by mutableStateOf(false)
    private var goldbergMessage by mutableStateOf<String?>(null)
    private var goldbergInstalled by mutableStateOf(false)
    private var goldbergDownloading by mutableStateOf(false)
    private var goldbergDownloadProgress by mutableFloatStateOf(0f)
    private var goldbergSizeLabel by mutableStateOf("")
    // A catalog bump must reach EXISTING installs, not just new ones: we track the
    // latest catalog version and whether the on-disk copy is behind it, so the
    // popup can offer an "Update" (re-download) even when it's already installed.
    private var goldbergCatalogVersion by mutableIntStateOf(0)
    private var goldbergOutdated by mutableStateOf(false)

    private var steamStatus by mutableStateOf(SteamRepository.getInstance().status)

    // Steam Cloud saves — three-tier save library (Cloud ⇄ Library ⇄ Container). Only meaningful
    // once the game is installed AND a Steam session is live (SteamRepository.getSteamCloud() != null).
    private var cloudVisible by mutableStateOf(false)
    private var cloudBusy by mutableStateOf(false)
    private var cloudStatus by mutableStateOf<String?>(null)
    // Container resolution for the Apply/Collect tier. cloudResolved flips true once the async
    // resolveContainer() returns; cloudContainerReady = the game has a launch container (else the
    // section shows the "set up first" state). cloudContainerLabel names it in every confirm dialog.
    private var cloudResolved by mutableStateOf(false)
    private var cloudContainerReady by mutableStateOf(false)
    private var cloudContainerLabel by mutableStateOf<String?>(null)
    // Pending per-move confirm dialog (move + container label + optional staleness warning).
    private var cloudConfirm by mutableStateOf<CloudConfirm?>(null)
    // One-time third-party disclaimer (persisted in steam_prefs). pendingCloudMove is the move the
    // user tried to run before accepting; it's dispatched once they tap "I understand".
    private var showCloudDisclaimer by mutableStateOf(false)
    private var pendingCloudMove by mutableStateOf<CloudMove?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour the user's App-orientation preference (Appearance -> AUTO / PORTRAIT / LANDSCAPE).
        // The whole Steam section previously ignored it: these activities pinned themselves in the
        // manifest and never asked. Applied before any content so the first frame is already in the
        // requested orientation. The game's XServerDisplayActivity is deliberately NOT touched.
        com.winhub.hernandez.core.AppOrientation.apply(this)
        appId = intent.getIntExtra(EXTRA_APP_ID, 0)
        if (appId == 0) { finish(); return }

        SteamPrefs.init(this)
        SteamRepository.getInstance().initialize(this)
        // This page can be entered cold (drawer -> Save Manager -> a card) without the Library tab
        // ever having run, so point the PICS art store at a Context here too. init() only restores
        // the on-disk mirror — no snapshot read, no network — so it's safe on the main thread.
        SteamLibraryArt.init(this)
        // Lazy-connect: opening a detail page directly (e.g. drawer → Save Manager → tap a card) skips
        // the store home that starts SteamForegroundService, so the CM connection would stay down and
        // the status badge read offline. Ensure it here when signed in — idempotent (start/connect
        // guard double-connect), and a no-op for users without a saved Steam account.
        if (SteamPrefs.isLoggedIn) SteamForegroundService.start(this)

        setContent {
            StoreXmbTheme {
                DeckStoreTheme {
                SteamGameDetailScreen(
                    appId = appId,
                    signedIn = SteamPrefs.isLoggedIn,
                    steamStatus = steamStatus,
                    onReconnect = { SteamRepository.getInstance().reconnectNow() },
                    nameText = nameText,
                    typeText = typeText,
                    sizeText = sizeText,
                    sizeBreakdown = sizeBreakdown,
                    includedDlcText = includedDlcText,
                    dlcEntries = dlcEntries,
                    excludedDlc = excludedDlc,
                    showDlcSheet = showDlcSheet,
                    onDlcLineClick = { if (dlcEntries.isNotEmpty()) showDlcSheet = true },
                    onToggleDlc = { toggleDlc(it) },
                    onDismissDlcSheet = { showDlcSheet = false },
                    branches = branches,
                    selectedBranch = selectedBranch,
                    showBranchSheet = showBranchSheet,
                    branchCheckBusy = branchCheckBusy,
                    branchCheckMessage = branchCheckMessage,
                    onBranchLineClick = { showBranchSheet = true },
                    onSelectBranch = { selectBranch(it) },
                    onCheckBranchPassword = { submitBranchPassword(it) },
                    onDismissBranchSheet = { showBranchSheet = false; branchCheckMessage = null },
                    statusText = statusText,
                    gameStatus = gameStatus,
                    installBtnText = installBtnText,
                    installAction = installAction,
                    installBtnEnabled = installBtnEnabled,
                    pauseBtnText = pauseBtnText,
                    pauseAction = pauseAction,
                    pauseBtnEnabled = pauseBtnEnabled,
                    launchBtnEnabled = launchBtnEnabled,
                    progressVisible = progressVisible,
                    progressValue = progressValue,
                    downloadProgressValue = downloadProgressValue,
                    progressText = progressText,
                    progressTextVisible = progressTextVisible,
                    goldbergMode = goldbergMode,
                    goldbergBusy = goldbergBusy,
                    goldbergInstalled = goldbergInstalled,
                    goldbergDownloading = goldbergDownloading,
                    goldbergDownloadProgress = goldbergDownloadProgress,
                    goldbergSizeLabel = goldbergSizeLabel,
                    goldbergOutdated = goldbergOutdated,
                    goldbergCatalogVersion = goldbergCatalogVersion,
                    onGoldbergDownloadClick = { onGoldbergDownloadClicked() },
                    onGoldbergModeSelected = { onGoldbergModeSelected(it) },
                    cloudVisible = cloudVisible,
                    cloudBusy = cloudBusy,
                    cloudStatus = cloudStatus,
                    cloudResolved = cloudResolved,
                    cloudContainerReady = cloudContainerReady,
                    cloudContainerLabel = cloudContainerLabel,
                    cloudLibraryPath = SteamCloudSavePaths.libraryDir(this, appId).absolutePath,
                    onCloudSyncFrom = { onCloudMoveRequested(CloudMove.SYNC_FROM) },
                    onCloudSyncTo = { onCloudMoveRequested(CloudMove.SYNC_TO) },
                    onCloudDownload = { onCloudMoveRequested(CloudMove.DOWNLOAD) },
                    onCloudApply = { onCloudMoveRequested(CloudMove.APPLY) },
                    onCloudCollect = { onCloudMoveRequested(CloudMove.COLLECT) },
                    onCloudUpload = { onCloudMoveRequested(CloudMove.UPLOAD) },
                    onCloudSetUp = { onLaunchClicked() },
                    onBack = { finish() },
                    onInstallClick = { onInstallClicked() },
                    onPauseResumeClick = { onPauseResumeClicked() },
                    onCancelDeleteClick = { showCancelDeleteConfirm = true },
                    onLaunchClick = { onLaunchClicked() },
                    moveStorageLabel = moveStorageLabel,
                    onMoveStorageClick = { onMoveStorageClicked() },
                )

                // One-time third-party disclaimer — gates the FIRST cloud action (any game). On accept
                // we persist the flag and dispatch the move the user was trying to run.
                if (showCloudDisclaimer) {
                    OutlinedAlertDialog(
                        onDismissRequest = { showCloudDisclaimer = false; pendingCloudMove = null },
                        title = { Text("Third-party cloud sync") },
                        text = {
                            Text(
                                "Steam Cloud save syncing here is handled by a third-party tool, not " +
                                    "official Steam. Managing game saves this way can corrupt or lose " +
                                    "your saves. Use at your own risk."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showCloudDisclaimer = false
                                setCloudDisclaimerAccepted()
                                pendingCloudMove?.let { prepareCloudConfirm(it) }
                                pendingCloudMove = null
                            }) { Text("I understand") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showCloudDisclaimer = false
                                pendingCloudMove = null
                            }) { Text("Cancel") }
                        },
                    )
                }

                // Per-move confirm dialog — names the target container, states direction + semantics,
                // folds in the staleness warning (Apply/Upload) and the third-party reminder.
                cloudConfirm?.let { confirm ->
                    CloudConfirmDialog(
                        confirm = confirm,
                        onConfirm = {
                            cloudConfirm = null
                            executeCloudMove(confirm.move)
                        },
                        onDismiss = { cloudConfirm = null },
                    )
                }

                if (showSpeedPicker) {
                    DownloadSpeedPickerDialog(
                        selectedIndex = when (lastSpeedTier) {
                            DownloadSpeedConfig.TIER_SLOW    -> 0
                            DownloadSpeedConfig.TIER_MEDIUM  -> 1
                            DownloadSpeedConfig.TIER_FAST    -> 2
                            DownloadSpeedConfig.TIER_BLAZING -> 3
                            else -> 2  // Fast
                        },
                        sdTarget = sdTarget,
                        onDismiss = { showSpeedPicker = false },
                        onDownload = { tier, debugLog, installToSd ->
                            showSpeedPicker = false
                            lastSpeedTier = tier
                            installBtnEnabled = false
                            installBtnText = "Starting…"
                            // SD toggle → install under <sd>/bannerlator/steam_games; else internal default.
                            val installRoot = if (installToSd) sdTarget?.steamGamesBase?.absolutePath else null
                            downloadHandle = SteamDepotDownloader.installApp(
                                appId, applicationContext, lastSpeedTier, debugLog, installRoot)
                        },
                    )
                }

                // Destructive confirm for the gear's "Cancel & delete download". Names the game and
                // spells out that this stops the download, deletes every downloaded file and resets it
                // for a fresh, full download. The wipe runs ONLY on confirm; dismiss keeps the partial.
                if (showCancelDeleteConfirm) {
                    val gName = game?.name?.takeIf { it.isNotBlank() } ?: "this game"
                    OutlinedAlertDialog(
                        onDismissRequest = { showCancelDeleteConfirm = false },
                        title = { Text("Cancel & delete download?") },
                        text = {
                            Text(
                                "This stops the download for \"$gName\", deletes every file already " +
                                    "downloaded, and resets it so the next tap starts a fresh, full " +
                                    "download from the beginning. This can't be undone."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showCancelDeleteConfirm = false
                                performCancelDeleteReset()
                            }) { Text("Delete & reset", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCancelDeleteConfirm = false }) {
                                Text("Keep download")
                            }
                        },
                    )
                }

                showExePicker?.let { data ->
                    ExePickerDialogGame(
                        gameName = data.gameName,
                        candidates = data.candidates,
                        onDismiss = { showExePicker = null },
                        onSelected = { chosen ->
                            showExePicker = null
                            startAddToShortcuts(data.gameName, chosen, data.coverUrl)
                        },
                    )
                }

                addToShortcuts?.let { req ->
                    DeckAddToShortcuts(req, appId) { addToShortcuts = null; game?.let { resolveCloudContainer(it) } }
                }

                addResult?.let { result ->
                    AddResultDialog(
                        result = result,
                        onOpenShortcuts = {
                            addResult = null
                            openShortcutsScreen(this@SteamGameDetailActivity)
                        },
                        onDismiss = { addResult = null },
                    )
                }

                goldbergMessage?.let { msg ->
                    OutlinedAlertDialog(
                        onDismissRequest = { goldbergMessage = null },
                        title = { Text("Steam Emulator (Goldberg)") },
                        text = { Text(msg) },
                        confirmButton = {
                            TextButton(onClick = { goldbergMessage = null }) { Text("OK") }
                        },
                    )
                }

                uninstallingName?.let { UninstallProgressDialog(it) }

                // Storage move: confirm sheet, then the blocking progress dialog. Both are driven
                // from activity state so a recomposition (rotation, tab change) can't lose a move
                // that is already running on its worker thread.
                movePlan?.let { plan ->
                    MoveStorageDialog(
                        plan = plan,
                        onConfirm = { startMove(plan) },
                        onDismiss = { movePlan = null },
                    )
                }
                moveProgress?.let { p ->
                    MoveProgressDialog(
                        gameName = moveGameName,
                        progress = p,
                        onCancel = { moveCancelled = true },
                    )
                }

                uninstallResult?.let { UninstallResultBar(it) { uninstallResult = null } }
            
                }}
        }

        SteamRepository.getInstance().addListener(this)
        loadGame()
    }

    override fun onDestroy() {
        SteamRepository.getInstance().removeListener(this)
        super.onDestroy()
    }

    override fun onEvent(event: String) {
        when {
            event.startsWith("SteamStatus:") -> {
                val name = event.substringAfter("SteamStatus:")
                steamStatus = try { SteamRepository.SteamStatus.valueOf(name) } catch (e: Exception) { steamStatus }
            }
            event.startsWith("DownloadProgress:") -> {
                // Format: DownloadProgress:appId:installDone:installTotal:downloadDone:downloadTotal:etaSec:speedBps
                val parts = event.split(":")
                val id    = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (id != appId) return
                val iDone  = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                val iTotal = parts.getOrNull(3)?.toLongOrNull() ?: 1L
                val dDone  = parts.getOrNull(4)?.toLongOrNull() ?: iDone
                val dTotal = parts.getOrNull(5)?.toLongOrNull() ?: iTotal
                val etaSec = parts.getOrNull(6)?.toLongOrNull() ?: -1L
                val speed  = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                val iPct   = if (iTotal > 0) (iDone * 100 / iTotal).toInt().coerceIn(0, 100) else 0
                val dPct   = if (dTotal > 0) (dDone * 100 / dTotal).toInt().coerceIn(0, 100) else 0
                progressVisible = true
                progressValue = iPct               // solid install fill (bytes on disk)
                downloadProgressValue = dPct        // lighter download fill (bytes fetched)
                progressTextVisible = true
                // %/size text is the INSTALL fraction — what's actually on disk; append speed + ETA.
                val speedEta = buildString {
                    val s = formatDownloadSpeed(speed); if (s.isNotEmpty()) append("  ·  $s")
                    val e = formatEta(etaSec);          if (e.isNotEmpty()) append("  ·  $e")
                }
                progressText = "Downloading… $iPct%  (${fmtSize(iDone)} / ${fmtSize(iTotal)})$speedEta"
                installBtnEnabled = true
                installBtnText = "Cancel"
                installAction = InstallAction.CANCEL
                pauseBtnEnabled = true
                pauseBtnText = "Pause"
                pauseAction = PauseAction.PAUSE
            }
            event.startsWith("DownloadPaused:") -> {
                val id = event.substringAfter("DownloadPaused:").toIntOrNull() ?: return
                if (id != appId) return
                downloadHandle = null
                val dlRow = SteamRepository.getInstance().database.getDownload(appId)
                val done  = dlRow?.bytesDownloaded ?: 0L
                val total = dlRow?.bytesTotal ?: 0L
                val pct   = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                progressVisible = true
                progressValue = pct
                downloadProgressValue = pct   // compressed not persisted — mirror install
                progressTextVisible = true
                progressText = "Paused — $pct%  (${fmtSize(done)} / ${fmtSize(total)})"
                installBtnEnabled = true
                installBtnText = "Cancel"
                installAction = InstallAction.CANCEL
                pauseBtnEnabled = true
                pauseBtnText = "Resume"
                pauseAction = PauseAction.RESUME
            }
            event.startsWith("DownloadQueued:") -> {
                val id = event.substringAfter("DownloadQueued:").toIntOrNull() ?: return
                if (id != appId) return
                // Waiting behind the active download (managed queue). Keep the facade in downloadHandle
                // so Cancel still works (it removes the item from the queue); pause/resume is
                // meaningless while queued. Auto-advances to the DownloadProgress: handler once it starts.
                progressVisible = true
                progressValue = 0
                downloadProgressValue = 0
                progressTextVisible = true
                progressText = "Queued…"
                installBtnEnabled = true
                installBtnText = "Cancel"
                installAction = InstallAction.CANCEL
                resetPauseBtn()
            }
            event.startsWith("DownloadComplete:") -> {
                val id = event.substringAfter("DownloadComplete:").toIntOrNull() ?: return
                if (id != appId) return
                downloadHandle = null
                progressVisible = false
                progressTextVisible = false
                resetPauseBtn()
                loadGame()
            }
            event.startsWith("DownloadCancelled:") -> {
                val id = event.substringAfter("DownloadCancelled:").toIntOrNull() ?: return
                if (id != appId) return
                // Cancel now means "delete + fresh restart" (performCancelDeleteReset). This async
                // engine event lands AFTER that reset for the live-download case, so settle on the same
                // pristine NOT_INSTALLED state rather than overriding it with a lingering "cancelled".
                downloadHandle = null
                progressVisible = false
                progressValue = 0
                downloadProgressValue = 0
                progressTextVisible = false
                progressText = ""
                statusText = "Not installed"
                gameStatus = GameStatus.NOT_INSTALLED
                installBtnEnabled = true
                installBtnText = "Install"
                installAction = InstallAction.INSTALL
                launchBtnEnabled = false
                resetPauseBtn()
            }
            event.startsWith("DownloadFailed:") -> {
                val parts = event.split(":")
                val id = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (id != appId) return
                val reason = parts.drop(2).joinToString(":")
                val logPath = SteamDepotDownloader.debugLogPath
                downloadHandle = null
                progressVisible = false
                progressTextVisible = false
                statusText = "Download failed: $reason\nDebug log: $logPath"
                gameStatus = GameStatus.FAILED
                installBtnEnabled = true
                installBtnText = "Retry"
                installAction = InstallAction.RETRY
                resetPauseBtn()
            }
        }
    }

    /**
     * Fire-and-forget: resolve the app's manifest-true install size off the UI thread, once. On
     * success, drop the "~" and show the real size. Silent + degrades to the estimate on any failure
     * (not-logged-in / download active / CM timeout — all handled inside DepotSizeResolver).
     */
    private fun maybeResolveRealSize() {
        if (sizeResolveStarted) return
        sizeResolveStarted = true
        Thread {
            val s = try { DepotSizeResolver.resolveBlocking(appId) } catch (_: Throwable) { null }
            if (s != null && s.complete && s.realInstallBytes > 0L) {
                runOnUiThread { game?.let { refreshSizeUi(it) } }   // real sizes landed → drop "~", update breakdown
            }
        }.apply { isDaemon = true; name = "SteamDetailSizeResolve" }.start()
    }

    /**
     * Compute the size section from the DB (pure, UI-thread-safe): the headline on-disk FOOTPRINT
     * (block-rounded estimate, "~" until resolved) plus the labeled breakdown — download (compressed),
     * the PICS estimate (explicitly labeled as Steam's), and free space with a "won't fit" flag. For an
     * installed game the estimate is replaced by the REAL measured footprint (async, best-effort).
     */
    private fun refreshSizeUi(g: SteamGame) {
        recomputeSizeDisplay(g)
        maybeResolveRealSize()
        if (g.isInstalled && g.installDir.isNotEmpty()) measureInstalledFootprint(g)
    }

    /**
     * Pure recompute of the size chips/breakdown from the depot rows, DROPPING any DLC the user opted
     * out of — so unchecking DLC lowers the shown download / on-disk / PICS numbers live. Sums per
     * depot (real sizes where resolved, PICS otherwise) instead of the app-level totals so exclusions
     * are honoured. Safe to call on the UI thread (pure DB read).
     */
    private fun recomputeSizeDisplay(g: SteamGame) {
        val rows = try { SteamRepository.getInstance().database.getDepotManifests(g.appId) } catch (_: Throwable) { emptyList() }
        val kept = rows.filter { it.depotId !in excludedDlc }
        var pics = 0L; var install = 0L; var download = 0L; var disk = 0L
        var anyResolved = false; var allResolved = kept.isNotEmpty()
        for (r in kept) {
            pics += r.sizeBytes
            if (r.realSizeBytes > 0L) {
                anyResolved = true
                install  += r.realSizeBytes
                download += r.realDownloadBytes
                disk     += if (r.realDiskBytes > 0L) r.realDiskBytes else r.realSizeBytes
            } else {
                allResolved = false
                install += r.sizeBytes
                disk    += r.sizeBytes
            }
        }
        // Empty depot table (not synced) → fall back to the app-level PICS size.
        if (rows.isEmpty()) { pics = g.sizeBytes; disk = g.sizeBytes }
        val resolved  = allResolved && anyResolved
        val footprint = if (resolved && disk > 0L) disk else pics

        sizeText = when {
            footprint > 0L && resolved -> "${fmtSize(footprint)} on disk"
            footprint > 0L             -> "~${fmtSize(footprint)} on disk"
            else                       -> "Size unknown"
        }

        // Download (compressed): the resolved per-depot sum is exclusion-aware; before resolve fall
        // back to the app-level PICS download estimate (not exclusion-aware, shown only until resolve).
        val downloadBytes = if (resolved && download in 1..maxOf(pics, download)) download
                            else try { SteamRepository.getInstance().getSelectedDownloadSize(g.appId) } catch (_: Throwable) { 0L }

        val free = try { freeInstallBytes() } catch (_: Throwable) { -1L }
        val fits = g.isInstalled || free < 0L || footprint <= 0L || free >= footprint
        sizeBreakdown = SizeBreakdown(
            downloadLabel = if (downloadBytes > 0L) "Download: ${fmtSize(downloadBytes)}" else "",
            picsLabel     = if (pics > 0L) "PICS estimate (Steam): ${fmtSize(pics)}" else "",
            freeLabel     = if (free >= 0L) "Free space: ${fmtSize(free)}" + (if (!fits) " — won't fit" else "") else "",
            fits          = fits,
        )
    }

    /** The "Includes DLC:" label from the owned DLC minus the user's opt-outs. "" hides the line
     *  (no owned DLC); "DLC: none selected" when everything's unchecked (keeps the line tappable). */
    private fun buildIncludedDlcText(): String {
        if (dlcEntries.isEmpty()) return ""
        val included = dlcEntries.filterKeys { it !in excludedDlc }.values
        return if (included.isEmpty()) "DLC: none selected"
               else "Includes DLC: " + included.joinToString(", ")
    }

    /** Toggle a DLC's opt-out state, persist it, and refresh the line. */
    private fun toggleDlc(dlcAppId: Int) {
        val g = game ?: return
        val next = excludedDlc.toMutableSet()
        if (dlcAppId in next) next.remove(dlcAppId) else next.add(dlcAppId)
        excludedDlc = next
        try { SteamPrefs.setExcludedDlc(g.appId, next) } catch (_: Throwable) {}
        includedDlcText = buildIncludedDlcText()
        recomputeSizeDisplay(g)   // drop the unticked DLC from the shown download/on-disk/PICS sizes
    }

    // ── Beta-branch selector ─────────────────────────────────────────────────

    /** Load the app's branches (+ unlock state) from the DB and normalise the selected branch. */
    private fun refreshBranchState(g: SteamGame) {
        val repo = SteamRepository.getInstance()
        branches = try {
            val unlocked = repo.database.getUnlockedBranchNames(g.appId).toSet()
            repo.getBranches(g.appId).map {
                BranchDisplay(
                    name = it.branchName,
                    description = it.description,
                    timeUpdated = it.timeUpdated,
                    pwdRequired = it.pwdRequired,
                    unlocked = !it.pwdRequired || it.branchName in unlocked,
                )
            }
        } catch (_: Throwable) { emptyList() }
        selectedBranch = try { SteamPrefs.getSelectedBranch(g.appId) } catch (_: Throwable) { "public" }
        // If the stored branch is no longer selectable (lost unlock / removed branch), fall back to
        // public so we never try to install a branch the user can't currently access.
        if (selectedBranch != "public" && branches.none { it.name == selectedBranch && it.unlocked }) {
            selectedBranch = "public"
            try { SteamPrefs.setSelectedBranch(g.appId, "public") } catch (_: Throwable) {}
        }
    }

    /** Persist + reflect the chosen branch. */
    private fun selectBranch(name: String) {
        try { SteamPrefs.setSelectedBranch(appId, name) } catch (_: Throwable) {}
        selectedBranch = name
    }

    /** Verify a beta access code off the main thread; on success re-derive the unlock state. */
    private fun submitBranchPassword(password: String) {
        if (password.isBlank() || branchCheckBusy) return
        branchCheckBusy = true
        branchCheckMessage = null
        // Serialize onto the library worker so this CM round-trip stays off the pump (same pattern
        // as DepotSizeResolver's manifest fetches).
        SteamRepository.getInstance().submitLibraryWork {
            val ok = try { SteamRepository.getInstance().checkBranchPassword(appId, password) }
                     catch (_: Throwable) { false }
            runOnUiThread {
                branchCheckBusy = false
                if (ok) {
                    branchCheckMessage = "Access code accepted"
                    game?.let { refreshBranchState(it) }
                } else {
                    branchCheckMessage = "Invalid access code (or not connected to Steam)"
                }
            }
        }
    }

    /** Available bytes on the partition the games install to. */
    private fun freeInstallBytes(): Long {
        val base = File(filesDir, "imagefs/steam_games")
        val dir  = if (base.exists()) base else filesDir
        val st   = android.os.StatFs(dir.path)
        return st.availableBytes
    }

    /** Real on-disk footprint of an installed game: sum each file rounded up to a block. Best-effort. */
    private fun measureInstalledFootprint(g: SteamGame) {
        Thread {
            val dir = File(g.installDir)
            if (!dir.exists()) return@Thread
            var sum = 0L
            try {
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        val len = f.length()
                        sum += ((len + DepotSizeResolver.DEFAULT_BLOCK_BYTES - 1) /
                                DepotSizeResolver.DEFAULT_BLOCK_BYTES) * DepotSizeResolver.DEFAULT_BLOCK_BYTES
                    }
                }
            } catch (_: Throwable) { return@Thread }
            if (sum > 0L) runOnUiThread { sizeText = "${fmtSize(sum)} on disk" }
        }.apply { isDaemon = true; name = "SteamDetailDiskMeasure" }.start()
    }

    private fun resetPauseBtn() {
        pauseBtnEnabled = false
        pauseBtnText = "Pause"
        pauseAction = PauseAction.PAUSE
    }

    private fun loadGame() {
        val row = SteamRepository.getInstance().database.getGame(appId) ?: run { finish(); return }
        game = SteamGame.fromGameRow(row)
        refreshUI()

        val dlRow = SteamRepository.getInstance().database.getDownload(appId)
        // Installed-first reconciliation (MUST precede the "no live worker → PAUSED" flip below):
        // a finished download can leave a stale steam_downloads row behind (e.g. a zero-work
        // re-download that completed with no progress events, or a completion that predates
        // row-clearing). If the game is already installed AND no worker is live for it, that row is
        // meaningless — the install lives in steam_games (is_installed=1) and refreshUI() has
        // already painted Installed — so clear it and keep that state instead of flipping to a
        // phantom downloading/paused UI. The liveness check (isDownloading/isQueued) is what keeps
        // a genuine in-place update/verify of an installed game (SteamGameUpdater still holds a live
        // worker + row, is_installed stays 1) from being wiped — that falls through to the live
        // DL_DOWNLOADING branch. The PAUSED reconciliation below stays for the NOT-installed case.
        if (dlRow != null && game?.isInstalled == true &&
            !SteamDepotDownloader.isDownloading(appId) && !DownloadQueue.isQueued(appId)) {
            SteamRepository.getInstance().database.deleteDownload(appId)
            return
        }
        if (dlRow != null) {
            val pct = if (dlRow.bytesTotal > 0) (dlRow.bytesDownloaded * 100 / dlRow.bytesTotal).toInt().coerceIn(0, 100) else 0
            // DB restore only has install bytes — mirror them onto the download fill.
            downloadProgressValue = pct
            when (dlRow.status) {
                SteamDatabase.DL_DOWNLOADING -> {
                    if (SteamDepotDownloader.isDownloading(appId)) {
                        progressVisible = true
                        progressValue = pct
                        progressTextVisible = true
                        progressText = "Downloading… $pct%"
                        installBtnEnabled = true
                        installBtnText = "Cancel"
                        installAction = InstallAction.CANCEL
                        pauseBtnEnabled = true
                        pauseBtnText = "Pause"
                        pauseAction = PauseAction.PAUSE
                    } else {
                        // Interrupted download: the DB says "downloading" but no live worker exists — the
                        // process was killed mid-download (e.g. a backgrounded WiFi-throttle stall the OEM
                        // task-killer later reaped; the worker never ran its terminal, so no fail/cancel).
                        // Do NOT delete the row — that discards resumable partial progress and leaves
                        // nothing to resume on foreground. Flip it to PAUSED so the partial files + install
                        // dir survive and the user can Resume (resumeApp) from where it stopped.
                        SteamRepository.getInstance().database.markDownloadPaused(appId, dlRow.bytesDownloaded)
                        progressVisible = true
                        progressValue = pct
                        downloadProgressValue = pct
                        progressTextVisible = true
                        progressText = "Paused — $pct%  (${fmtSize(dlRow.bytesDownloaded)} / ${fmtSize(dlRow.bytesTotal)})"
                        installBtnEnabled = true
                        installBtnText = "Cancel"
                        installAction = InstallAction.CANCEL
                        pauseBtnEnabled = true
                        pauseBtnText = "Resume"
                        pauseAction = PauseAction.RESUME
                    }
                }
                SteamDatabase.DL_PAUSED -> {
                    progressVisible = true
                    progressValue = pct
                    downloadProgressValue = pct
                    progressTextVisible = true
                    progressText = "Paused — $pct%  (${fmtSize(dlRow.bytesDownloaded)} / ${fmtSize(dlRow.bytesTotal)})"
                    installBtnEnabled = true
                    installBtnText = "Cancel"
                    installAction = InstallAction.CANCEL
                    pauseBtnEnabled = true
                    pauseBtnText = "Resume"
                    pauseAction = PauseAction.RESUME
                }
                SteamDatabase.DL_QUEUED -> {
                    // Waiting in the managed queue. Mirror the DL_DOWNLOADING liveness check with
                    // isQueued: a `queued` row that isn't actually in the live queue is stale (process
                    // died) — keep it as a resumable PAUSED row rather than dropping it (see else).
                    if (DownloadQueue.isQueued(appId)) {
                        progressVisible = true
                        progressValue = 0
                        downloadProgressValue = 0
                        progressTextVisible = true
                        progressText = "Queued…"
                        installBtnEnabled = true
                        installBtnText = "Cancel"
                        installAction = InstallAction.CANCEL
                        resetPauseBtn()
                    } else {
                        // Stale queued row: the DB says "queued" but it isn't in the live queue — the
                        // process died before it started (or while it waited). Keep it resumable instead
                        // of deleting: flip to PAUSED so a Resume re-enqueues it (any partial bytes + the
                        // install dir are preserved; a fresh 0-byte row just restarts).
                        SteamRepository.getInstance().database.markDownloadPaused(appId, dlRow.bytesDownloaded)
                        progressVisible = true
                        progressValue = pct
                        downloadProgressValue = pct
                        progressTextVisible = true
                        progressText = "Paused — $pct%  (${fmtSize(dlRow.bytesDownloaded)} / ${fmtSize(dlRow.bytesTotal)})"
                        installBtnEnabled = true
                        installBtnText = "Cancel"
                        installAction = InstallAction.CANCEL
                        pauseBtnEnabled = true
                        pauseBtnText = "Resume"
                        pauseAction = PauseAction.RESUME
                    }
                }
            }
        }
    }

    private fun refreshUI() {
        val g = game ?: return
        nameText = g.name.ifEmpty { "App ${g.appId}" }
        typeText = g.type.uppercase()
        // Paint the manifest-TRUE size instantly if it's already resolved (no "~"), otherwise the PICS
        // "~estimate". A background resolve then drops the "~" once the real size lands. cached() is a
        // pure DB read; resolve() is gated off the UI thread + off active downloads inside the resolver.
        refreshSizeUi(g)
        dlcEntries = try { SteamRepository.getInstance().database.getIncludedDlcEntries(g.appId) } catch (_: Throwable) { emptyMap() }
        excludedDlc = try { SteamPrefs.getExcludedDlc(g.appId) } catch (_: Throwable) { emptySet() }
        includedDlcText = buildIncludedDlcText()
        refreshBranchState(g)
        maybeResolveRealSize()

        refreshMoveStorageOffer(g)

        if (g.isInstalled) {
            statusText = "Installed"
            gameStatus = GameStatus.INSTALLED
            installBtnText = "Uninstall"
            installAction = InstallAction.UNINSTALL
            installBtnEnabled = true
            launchBtnEnabled = true
            // Cloud saves need a live Steam session (the SteamCloud handle is only bound after login).
            cloudVisible = SteamRepository.getInstance().steamCloud != null
            // Resolve the game's launch container off the UI thread AFTER the game row is bound —
            // drives the Apply/Collect tier and the "set up first" gate.
            if (cloudVisible) resolveCloudContainer(g)
            goldbergMode = SteamPrefs.getGoldbergMode(appId)
            goldbergInstalled = GoldbergComponent.isInstalled(this)
            // Fetch the catalog in the background whether or not it's installed:
            // the size label feeds the Download/Update button, and the catalog
            // version decides whether an EXISTING install is out-of-date (a
            // catalog bump must reach it, not just brand-new installs).
            if (goldbergCatalogVersion == 0) {
                GoldbergComponent.loadCatalogAsync { cat ->
                    if (cat != null) {
                        if (cat.fileSize > 0) goldbergSizeLabel = fmtSize(cat.fileSize)
                        goldbergCatalogVersion = cat.version
                        goldbergOutdated = GoldbergComponent.isOutdated(this, cat.version)
                    }
                }
            }
        } else {
            statusText = "Not installed"
            gameStatus = GameStatus.NOT_INSTALLED
            installBtnText = "Install"
            installAction = InstallAction.INSTALL
            installBtnEnabled = true
            launchBtnEnabled = false
            cloudVisible = false
            cloudResolved = false
            cloudContainerReady = false
            cloudContainerLabel = null
        }
    }

    // ── Storage move ────────────────────────────────────────────────────────────────────────

    /**
     * Recomputes the gear row's direction for this game off the UI thread
     * ([MoveGameStorage.offerLabel] hits the database, the filesystem and the volume list). Hidden
     * entirely while a download is in flight — the installer is writing into the very folder a move
     * would be copying.
     */
    private fun refreshMoveStorageOffer(g: SteamGame) {
        if (!g.isInstalled || downloadHandle != null) { moveStorageLabel = null; return }
        Thread {
            val label = try {
                MoveGameStorage.offerLabel(this, g.appId)
            } catch (t: Throwable) {
                Log.w("MoveGameStorage", "move offer check failed", t); null
            }
            runOnUiThread { moveStorageLabel = label }
        }.apply { isDaemon = true; name = "MoveOfferCheck" }.start()
    }

    /** Gear → move: build the plan (folder walk + shortcut scan) off the UI thread, then confirm. */
    private fun onMoveStorageClicked() {
        val g = game ?: return
        if (MoveGameStorage.moveInProgress) {
            uninstallResult = "A move is already running"
            return
        }
        moveGameName = g.name.ifEmpty { "App ${g.appId}" }
        moveProgress = MoveGameStorage.Progress(MoveGameStorage.Phase.VERIFYING) // "working…" spinner
        Thread {
            val result = runCatching { MoveGameStorage.buildPlan(this, g.appId) }
            runOnUiThread {
                moveProgress = null
                result.onSuccess { movePlan = it }.onFailure {
                    uninstallResult = it.message ?: "Couldn't work out where to move this game"
                }
            }
        }.apply { isDaemon = true; name = "MovePlan" }.start()
    }

    /** Runs a confirmed plan on a worker thread, reporting progress back to the dialog. */
    private fun startMove(plan: MoveGameStorage.Plan) {
        movePlan = null
        moveCancelled = false
        moveProgress = MoveGameStorage.Progress(MoveGameStorage.Phase.COPYING, 0L, plan.sizeBytes)
        Thread {
            val result = runCatching {
                MoveGameStorage.execute(this, plan, { moveCancelled }) { p ->
                    runOnUiThread { moveProgress = p }
                }
            }
            runOnUiThread {
                moveProgress = null
                result.onSuccess {
                    uninstallResult = "${plan.gameName} moved to ${plan.to.label}"
                }.onFailure { t ->
                    uninstallResult = when (t) {
                        is CopyGameToDriveC.CancelledException -> "Move cancelled — nothing was changed"
                        else -> t.message ?: "The move failed — the game is still where it was"
                    }
                }
                // Repaint from the DB either way: on success the install dir, size and the gear's
                // direction all changed; on failure this re-asserts the unchanged state.
                loadGame()
            }
        }.apply { isDaemon = true; name = "MoveGameStorage" }.start()
    }

    private fun onInstallClicked() {
        val g = game ?: return

        // A live OR paused download → the destructive "Cancel & delete → fresh restart". BOTH branches
        // now converge on the SAME confirm-gated guaranteed reset (performCancelDeleteReset), so the old
        // divergence is gone: the active branch used to skip db.deleteDownload() (leaving a stale
        // DL_DOWNLOADING row), and each deleted only the DB-row dir. In practice the gear's
        // "Cancel & delete download" item calls the confirm directly; this keeps any other caller that
        // reaches here in a cancel state on the same confirmed path (never an unconfirmed wipe).
        if (downloadHandle != null) { showCancelDeleteConfirm = true; return }
        val db = SteamRepository.getInstance().database
        val dlRow = db.getDownload(appId)
        if (dlRow != null && dlRow.status == SteamDatabase.DL_PAUSED) { showCancelDeleteConfirm = true; return }
        // A still-queued download (page reopened onto it, so no facade in downloadHandle): pull it
        // from the queue directly — no files to wipe, so skip the destructive delete-and-reset confirm.
        // The DownloadCancelled: event it emits settles the UI back to Install.
        if (dlRow != null && dlRow.status == SteamDatabase.DL_QUEUED && DownloadQueue.isQueued(appId)) {
            DownloadQueue.cancel(appId); return
        }

        if (g.isInstalled) {
            uninstallingName = g.name
            val installDir = g.installDir

            // Then delete the game's files. Split out so the pre-uninstall save backup can run first
            // and this proceeds regardless of whether that backup succeeded.
            val proceedUninstall = {
                StoreUninstaller.run(
                    installDir = installDir,
                    mark = { SteamRepository.getInstance().database.markUninstalled(appId) },
                ) { ok ->
                    uninstallingName = null
                    uninstallResult = if (ok) "${g.name} uninstalled" else "Couldn't fully remove ${g.name}"
                    loadGame()
                }
            }

            // Best-effort: snapshot this game's saves into the local Library (Container -> Library)
            // BEFORE its files are deleted, so the Library is current at removal time (it lives on
            // external storage and already survives uninstall — this just makes it CURRENT). Collect
            // only — nothing is uploaded to the cloud. If the collect fails (e.g. never set up in a
            // container), log it and uninstall anyway — the backup must never block removal.
            SteamCloudSaveManager.collectFromContainer(this, appId, installDir,
                object : SteamCloudSaveManager.Callback {
                    override fun onStatus(message: String) {}
                    override fun onDone(summary: String) {
                        Log.i("BH_SAVE_SYNC", "pre-uninstall collect (appId $appId): $summary")
                        runOnUiThread { proceedUninstall() }
                    }
                    override fun onError(message: String) {
                        Log.w("BH_SAVE_SYNC", "pre-uninstall collect failed (appId $appId): $message")
                        runOnUiThread { proceedUninstall() }
                    }
                })
        } else {
            // Detect a removable SD card off the UI thread (StorageRoots does Binder + filesystem
            // work), then open the download dialog. The SD "Install to…" option is shown only when a
            // card is actually present; internal stays the default.
            Thread {
                val sd = try { SteamSdInstall.detect(this) } catch (_: Throwable) { null }
                runOnUiThread {
                    sdTarget = sd
                    showSpeedPicker = true
                }
            }.apply { isDaemon = true; name = "SteamSdDetect" }.start()
        }
    }

    /**
     * GUARANTEED "Cancel & delete → fresh restart" for a wedged/stuck/looping OR paused Steam download.
     * Nukes every trace of the partial so the NEXT Install is a genuine fresh full download, never a
     * resume onto dead chunks. Runs only after the destructive confirm (see showCancelDeleteConfirm).
     *
     * The sequence, in order:
     *   1. cancel the live download handle if present — flips the engine's `cancelled` flag and closes
     *      the downloader (async). Its own finally then ALSO deletes the DB row + drops the registry
     *      row (all idempotent with the synchronous cleanup below); it does NOT delete files, so the
     *      on-disk wipe here is the sole file-deleter.
     *   2. ALWAYS delete the steam_downloads row NOW (don't wait on the async engine finally) — this is
     *      the fix for the old active-branch bug that left a stale DL_DOWNLOADING row. The download row
     *      (not the game row) is where a wedged download records its dir, so read it first.
     *   3. clear the game row's installed flag / install_dir (defensive — a wedged DOWNLOAD never sets
     *      these; a not-installed game is already blank — but a hard reset must leave nothing behind),
     *      and drop the cross-store Download-Manager registry row.
     *   4. delete every on-disk partial off the UI thread: the recorded DB-row dir AND the defensively
     *      recomputed internal (imagefs/steam_games/<safeName>) and SD
     *      (<sd>/bannerlator/steam_games/<safeName>) candidates — using the SAME safe-name the installer
     *      derives, so paths match exactly and a stale/missing row still wipes the bytes. Deleting the
     *      install dir also removes DepotDownloader's own <installDir>/.DepotDownloader/{depot.config,
     *      staging} store (it lives INSIDE the install dir), so the engine can't skip already-fetched
     *      chunks on the next run either. A short grace + second sweep defeats the race where the
     *      still-closing engine flushes a few more chunks after the first delete.
     *   5. reset the UI to pristine NOT_INSTALLED + InstallAction.INSTALL, exactly the not-installed
     *      entry state, so the next tap routes to onInstallClicked's fresh installApp(isResume=false).
     */
    private fun performCancelDeleteReset() {
        val db = SteamRepository.getInstance().database

        // (2, read-first) Authoritative on-disk location for THIS download (internal or SD), captured
        // BEFORE the row is deleted. The game row's install_dir stays blank until a successful
        // markInstalled, so the steam_downloads row is the only record of a wedged download's dir.
        val rowDir = db.getDownload(appId)?.installDir?.takeIf { it.isNotBlank() }

        // (1) Cancel the live handle (async close; its finally re-deletes the row + drops the registry —
        // all idempotent). No-op if the download is paused (no live handle).
        downloadHandle?.let { try { it.cancel.run() } catch (_: Throwable) {} }
        downloadHandle = null

        // (2, 3) Drop the app's resume state synchronously: the DB download row, the game's installed
        // flag/dir, and the Download-Manager registry row.
        db.deleteDownload(appId)
        db.markUninstalled(appId)   // is_installed=0, install_dir="" (idempotent; invalidates game cache)
        try { DownloadRegistry.remove("${Store.STEAM}:$appId") } catch (_: Throwable) {}

        // (4) Wipe every on-disk partial off the UI thread. Recompute internal + SD candidates with the
        // SAME sanitisation SteamDepotDownloader uses (row.name → safeName) so the paths match exactly.
        val gName = game?.name ?: ""
        Thread {
            val targets = LinkedHashSet<File>()
            rowDir?.let { targets.add(File(it)) }
            val safeName = gName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
            if (safeName.isNotEmpty()) {
                // Internal default: imagefs/steam_games/<safeName>.
                targets.add(File(File(filesDir, "imagefs/steam_games"), safeName))
                // SD default: <sd>/bannerlator/steam_games/<safeName>. detect() does Binder + filesystem
                // work, so it must run here (off the UI thread), not in the caller.
                val sd = try { SteamSdInstall.detect(this) } catch (_: Throwable) { null }
                sd?.let { targets.add(File(it.steamGamesBase, safeName)) }
            }
            fun sweep() = targets.forEach { d ->
                try { if (d.exists()) d.deleteRecursively() } catch (_: Throwable) {}
            }
            sweep()
            try { Thread.sleep(1500L) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            sweep()   // second pass sweeps anything the closing engine re-wrote mid-cancel
        }.apply { isDaemon = true; name = "SteamCancelDeleteWipe" }.start()

        // (5) Pristine NOT_INSTALLED — mirrors refreshUI()'s not-installed branch, so the next tap is a
        // fresh installApp(). (The async DownloadCancelled: event, if the engine emits one for the live
        // case, lands on the same pristine state — see that handler.)
        progressVisible = false
        progressValue = 0
        downloadProgressValue = 0
        progressTextVisible = false
        progressText = ""
        statusText = "Not installed"
        gameStatus = GameStatus.NOT_INSTALLED
        installBtnText = "Install"
        installAction = InstallAction.INSTALL
        installBtnEnabled = true
        launchBtnEnabled = false
        resetPauseBtn()
    }

    private fun onPauseResumeClicked() {
        val handle = downloadHandle
        if (handle != null) {
            handle.pause.run()
            downloadHandle = null
            pauseBtnText = "Resume"
            pauseAction = PauseAction.RESUME
            pauseBtnEnabled = true
            installBtnText = "Cancel"
            installBtnEnabled = true
            val cur = progressText
            if (cur.startsWith("Downloading")) progressText = cur.replace("Downloading", "Pausing")
        } else {
            val dlRow = SteamRepository.getInstance().database.getDownload(appId) ?: return
            if (dlRow.status != SteamDatabase.DL_PAUSED) return
            pauseBtnEnabled = false
            pauseBtnText = "Resuming…"
            installBtnEnabled = false
            installBtnText = "Starting…"
            downloadHandle = SteamDepotDownloader.resumeApp(appId, applicationContext, lastSpeedTier)
        }
    }

    private fun onLaunchClicked() {
        val g = game ?: return
        if (!g.isInstalled || g.installDir.isEmpty()) {
            uninstallResult = "Game not installed"
            return
        }
        val installDir = File(g.installDir)
        Thread {
            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(installDir, exeFiles)
            if (exeFiles.isEmpty()) {
                runOnUiThread {
                    uninstallResult = "No .exe found in install directory"
                }
                return@Thread
            }
            val lowerTitle = g.name.lowercase()
            exeFiles.sortWith { a, b ->
                AmazonLaunchHelper.scoreExe(b, lowerTitle) - AmazonLaunchHelper.scoreExe(a, lowerTitle)
            }
            // Prefer the app's PUBLISHED portrait cover from PICS; the constructed URL is the
            // fallback for anything the library snapshot doesn't carry.
            val coverUrl = SteamLibraryArt.libraryCapsule(g.appId)
                ?: "https://shared.steamstatic.com/store_item_assets/steam/apps/${g.appId}/library_600x900.jpg"

            if (exeFiles.size == 1) {
                runOnUiThread { startAddToShortcuts(g.name, exeFiles[0].absolutePath, coverUrl) }
                return@Thread
            }
            val candidates = exeFiles.map { it.absolutePath }
            runOnUiThread {
                showExePicker = ExePickerDataGame(g.name, candidates, coverUrl)
            }
        }.start()
    }

    // ── Steam Cloud saves — three-tier save library (Cloud ⇄ Library ⇄ Container) ────────────
    // Persisted one-time third-party disclaimer, keyed globally (any game). Stored in the same
    // steam_prefs store SteamPrefs uses; SteamPrefs itself is owned by a parallel workstream so we
    // read/write the flag directly here rather than adding a field to it.
    private fun cloudDisclaimerAccepted(): Boolean =
        getSharedPreferences("steam_prefs", MODE_PRIVATE)
            .getBoolean("cloud_saves_disclaimer_accepted", false)

    private fun setCloudDisclaimerAccepted() {
        getSharedPreferences("steam_prefs", MODE_PRIVATE)
            .edit().putBoolean("cloud_saves_disclaimer_accepted", true).apply()
    }

    /**
     * Resolve the game's launch container (via its shortcut) off the UI thread, then publish whether
     * it's set up + its label. Seeds the Apply/Collect tier state — called AFTER the game row is bound.
     */
    private fun resolveCloudContainer(g: SteamGame) {
        Thread {
            val container = try {
                SteamCloudSavePaths.resolveContainer(this, appId, g.installDir)
            } catch (_: Throwable) { null }
            val label = container?.let {
                try { SteamCloudSavePaths.containerLabel(it) } catch (_: Throwable) { null }
            }
            runOnUiThread {
                cloudContainerReady = container != null
                cloudContainerLabel = label
                cloudResolved = true
            }
        }.apply { isDaemon = true; name = "SteamCloudResolve" }.start()
    }

    /** Entry point for all four buttons: gate on the one-time disclaimer, then build the confirm. */
    private fun onCloudMoveRequested(move: CloudMove) {
        if (cloudBusy) return
        if (!cloudDisclaimerAccepted()) {
            pendingCloudMove = move
            showCloudDisclaimer = true
            return
        }
        prepareCloudConfirm(move)
    }

    /**
     * Build the per-move confirm. Apply and Upload first sample the container-vs-Library freshness
     * (off the UI thread) so the dialog can carry a non-blocking "Collect first?" warning when the
     * container holds newer saves than the Library. Download/Collect need no staleness check.
     */
    private fun prepareCloudConfirm(move: CloudMove) {
        val g = game ?: return
        val label = cloudContainerLabel ?: return   // gated by cloudContainerReady, but guard anyway
        if (move == CloudMove.APPLY || move == CloudMove.UPLOAD) {
            Thread {
                val stale = try {
                    SteamCloudSaveManager.staleness(this, appId, g.installDir)
                } catch (_: Throwable) { null }
                val warning = stale?.let { cloudStalenessWarning(it) }
                runOnUiThread { cloudConfirm = CloudConfirm(move, label, warning) }
            }.apply { isDaemon = true; name = "SteamCloudStale" }.start()
        } else {
            cloudConfirm = CloudConfirm(move, label, null)
        }
    }

    /**
     * Warn when the CONTAINER holds newer saves than the Library (the user played but forgot to
     * Collect): Apply would push a stale Library over newer container progress, and Upload would push
     * that stale Library to the cloud. Non-blocking — the user can still proceed.
     */
    private fun cloudStalenessWarning(s: SteamCloudSaveManager.Staleness): String? =
        if (s.containerFileCount > 0 && s.containerNewestMtime > s.libraryNewestMtime)
            "⚠️ This container has newer saves than your Library — Collect first?"
        else null

    /** Dispatch the confirmed move to the frozen manager API. Each runs on its own worker thread. */
    private fun executeCloudMove(move: CloudMove) {
        if (cloudBusy) return
        val g = game ?: return
        cloudBusy = true
        cloudStatus = when (move) {
            CloudMove.DOWNLOAD  -> "Preparing download…"
            CloudMove.UPLOAD    -> "Preparing upload…"
            CloudMove.APPLY     -> "Applying to container…"
            CloudMove.COLLECT   -> "Collecting from container…"
            CloudMove.SYNC_FROM -> "Syncing from Cloud…"
            CloudMove.SYNC_TO   -> "Syncing to Cloud…"
        }
        val cb = object : SteamCloudSaveManager.Callback {
            override fun onStatus(message: String) { runOnUiThread { cloudStatus = message } }
            override fun onDone(summary: String) { runOnUiThread { cloudStatus = summary; cloudBusy = false } }
            override fun onError(message: String) { runOnUiThread { cloudStatus = "Error: $message"; cloudBusy = false } }
        }
        when (move) {
            CloudMove.DOWNLOAD  -> SteamCloudSaveManager.downloadToLibrary(this, appId, cb)
            CloudMove.UPLOAD    -> SteamCloudSaveManager.uploadFromLibrary(this, appId, cb)
            CloudMove.APPLY     -> SteamCloudSaveManager.applyToContainer(this, appId, g.installDir, cb)
            CloudMove.COLLECT   -> SteamCloudSaveManager.collectFromContainer(this, appId, g.installDir, cb)
            // Combos — chained Download+Apply / Collect+Upload; the manager guards not-set-up itself
            // and reports progress across both phases via onStatus.
            CloudMove.SYNC_FROM -> SteamCloudSaveManager.syncFromCloud(this, appId, g.installDir, cb)
            CloudMove.SYNC_TO   -> SteamCloudSaveManager.syncToCloud(this, appId, g.installDir, cb)
        }
    }

    /** Compose add-to-shortcuts flow: load containers, then show the M3 picker. */
    private fun startAddToShortcuts(gameName: String, exePath: String, coverUrl: String?) {
        // If this game is in Cold Client Loader mode, the shortcut must launch the
        // Goldberg loader beside the exe instead of the game exe. Every other mode
        // (OFF/REGULAR/EXPERIMENTAL) returns exePath unchanged.
        val launchExe = GoldbergPatcher.resolveLaunchExe(this, appId, exePath)
        StarLaunchBridge.loadContainers(this) { containers ->
            addToShortcuts = AddToShortcutsRequest(gameName, launchExe, coverUrl, containers)
        }
    }

    /**
     * Downloads the ONE global Goldberg component (ReShade-style: catalog →
     * .tzst → MD5 verify → extract to imagefs/opt/goldberg). Once installed,
     * every game's detail page shows the tier toggle ready — no re-download.
     */
    private fun onGoldbergDownloadClicked() {
        // Allow a re-download when an update is available (installed but outdated);
        // block only a redundant download of an already up-to-date copy.
        if (goldbergDownloading) return
        if (goldbergInstalled && !goldbergOutdated) return
        goldbergDownloading = true
        goldbergDownloadProgress = 0f
        GoldbergComponent.downloadAsync(
            this,
            progress = { fraction -> goldbergDownloadProgress = fraction },
            done = { success, message ->
                goldbergDownloading = false
                goldbergInstalled = GoldbergComponent.isInstalled(this)
                // Re-evaluate against the freshly-written version marker so the
                // "Update" affordance clears once the new build is on disk.
                goldbergOutdated = GoldbergComponent.isOutdated(this, goldbergCatalogVersion)
                goldbergMode = SteamPrefs.getGoldbergMode(appId)
                goldbergMessage = message
            },
        )
    }

    /**
     * Applies the chosen Goldberg tier on a worker thread, then persists it.
     * OFF restores the game to pristine. The patcher surfaces the N/A case
     * ("doesn't use the Steam API") and the not-bundled case as result messages.
     */
    private fun onGoldbergModeSelected(mode: GoldbergMode) {
        val g = game ?: return
        if (goldbergBusy || mode == goldbergMode) return
        if (!g.isInstalled || g.installDir.isEmpty()) return
        goldbergBusy = true
        GoldbergPatcher.applyModeAsync(this, appId, g.installDir, g.name, mode) { success, message ->
            goldbergBusy = false
            if (success) goldbergMode = mode
            goldbergMessage = message
        }
    }

    private fun fmtSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
        else                    -> "%.0f KB".format(bytes / 1024.0)
    }
}

private data class ExePickerDataGame(
    val gameName: String,
    val candidates: List<String>,
    val coverUrl: String,
)

/** The detail page's top-level tabs (styled like the action buttons). Order = strip order. */
private enum class DetailTab(val label: String) {
    DETAILS("Details"),
    ACHIEVEMENTS("Achievements"),
    DLC("DLC"),
    CLOUD("Cloud saves"),
    /** Store screenshots + trailers; only in the strip once the appdetails fetch found any. */
    MEDIA("Media"),
}

// ── Achievements/tab mockup palette ─────────────────────────────────────────────────────────────
// The tab strip + achievements body follow an approved fixed-colour mockup, NOT MaterialTheme — so
// these literal values match the spec 1:1 (alpha channels folded into the ARGB hex where the mockup
// used rgba()). Everything else on the page keeps the app theme.
// Non-gold roles now follow the user's theme (composable getters reading MaterialTheme.colorScheme),
// so tabs / buttons / surfaces / lines recolor with the active preset instead of the mockup blue.
private val AchvAccent: Color          @Composable get() = MaterialTheme.colorScheme.primary
private val AchvTabActiveBg: Color     @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
private val AchvTabActiveText: Color   @Composable get() = MaterialTheme.colorScheme.primary
private val AchvTabActiveBorder: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
private val AchvTabActiveBadge: Color  @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
private val AchvInk: Color             @Composable get() = MaterialTheme.colorScheme.onSurface
private val AchvInk2: Color            @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val AchvInk3: Color            @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
private val AchvCard: Color            @Composable get() = MaterialTheme.colorScheme.surface
private val AchvCard2: Color           @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val AchvLine: Color            @Composable get() = MaterialTheme.colorScheme.outline
private val AchvLineSoft: Color        @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
private val AchvBadgeBg: Color         @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
private val AchvTrackBg: Color         @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
private val AchvTileUnlockedTop: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val AchvTileUnlockedBot: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val AchvTileLockedTop: Color   @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val AchvTileLockedBot: Color   @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

// Achievement GOLD — the "earned" identity (Steam-like). Deliberately fixed (NOT themed): used ONLY
// for the progress-bar fill, the unlocked tile ring + ✓ badge, the "Unlocked" pill, and the legend.
private val AchvGold            = Color(0xFFE8B652)
private val AchvGoldDim         = Color(0xFFCAA03E)
private val AchvUnlockedBorder  = Color(0x8CE8B652) // rgba(232,182,82,.55)
private val AchvChkGlyph        = Color(0xFF1A1204)
private val AchvPillOnBg        = Color(0x21E8B652) // rgba(232,182,82,.13)
private val AchvPillOnBorder    = Color(0x66E8B652) // rgba(232,182,82,.40)

// --- Composable Screens ---

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SteamGameDetailScreen(
    appId: Int,
    signedIn: Boolean,
    steamStatus: SteamRepository.SteamStatus,
    onReconnect: () -> Unit,
    nameText: String,
    typeText: String,
    sizeText: String,
    sizeBreakdown: SizeBreakdown,
    includedDlcText: String,
    dlcEntries: Map<Int, String>,
    excludedDlc: Set<Int>,
    showDlcSheet: Boolean,
    onDlcLineClick: () -> Unit,
    onToggleDlc: (Int) -> Unit,
    onDismissDlcSheet: () -> Unit,
    branches: List<BranchDisplay>,
    selectedBranch: String,
    showBranchSheet: Boolean,
    branchCheckBusy: Boolean,
    branchCheckMessage: String?,
    onBranchLineClick: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onCheckBranchPassword: (String) -> Unit,
    onDismissBranchSheet: () -> Unit,
    statusText: String,
    gameStatus: GameStatus,
    installBtnText: String,
    installAction: InstallAction,
    installBtnEnabled: Boolean,
    pauseBtnText: String,
    pauseAction: PauseAction,
    pauseBtnEnabled: Boolean,
    launchBtnEnabled: Boolean,
    progressVisible: Boolean,
    progressValue: Int,
    downloadProgressValue: Int,
    progressText: String,
    progressTextVisible: Boolean,
    goldbergMode: GoldbergMode,
    goldbergBusy: Boolean,
    goldbergInstalled: Boolean,
    goldbergDownloading: Boolean,
    goldbergDownloadProgress: Float,
    goldbergSizeLabel: String,
    goldbergOutdated: Boolean,
    goldbergCatalogVersion: Int,
    onGoldbergDownloadClick: () -> Unit,
    onGoldbergModeSelected: (GoldbergMode) -> Unit,
    cloudVisible: Boolean,
    cloudBusy: Boolean,
    cloudStatus: String?,
    cloudResolved: Boolean,
    cloudContainerReady: Boolean,
    cloudContainerLabel: String?,
    cloudLibraryPath: String,
    onCloudSyncFrom: () -> Unit,
    onCloudSyncTo: () -> Unit,
    onCloudDownload: () -> Unit,
    onCloudApply: () -> Unit,
    onCloudCollect: () -> Unit,
    onCloudUpload: () -> Unit,
    onCloudSetUp: () -> Unit,
    onBack: () -> Unit,
    onInstallClick: () -> Unit,
    onPauseResumeClick: () -> Unit,
    onCancelDeleteClick: () -> Unit,
    onLaunchClick: () -> Unit,
    /** Gear → "Move to SD card" / "Move to internal storage". Null while the game isn't movable
     *  (not installed, folder missing, or no card to move it to) — the row is then hidden. */
    moveStorageLabel: String?,
    onMoveStorageClick: () -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(DetailTab.DETAILS) }
    // Anchored gear (⚙) dropdown beside the primary action button.
    var gearMenuExpanded by remember { mutableStateOf(false) }
    // Goldberg (Steam emulator) popup — opened from the gear menu (installed games only).
    var goldbergDialogOpen by remember { mutableStateOf(false) }

    // Achievements are loaded ONCE at the screen level so the tab count badge (done/total) and the
    // Achievements tab body share a single source of truth. Same strategy as the old section: fetch
    // on IO, fall back to the offline cache; not-signed-in short-circuits. Unlocked-first (stable).
    var achLoading by remember { mutableStateOf(true) }
    var achievements by remember { mutableStateOf<List<SteamAchievement>>(emptyList()) }
    LaunchedEffect(appId, signedIn) {
        if (!signedIn) { achievements = emptyList(); achLoading = false; return@LaunchedEffect }
        achLoading = true
        val list = withContext(Dispatchers.IO) {
            val fetched = try { SteamAchievementStore.fetch(context, appId) } catch (_: Throwable) { emptyList() }
            val base = if (fetched.isNotEmpty()) fetched
                       else try { SteamAchievementStore.cached(context, appId) } catch (_: Throwable) { emptyList() }
            base.sortedByDescending { it.unlocked }
        }
        achievements = list
        achLoading = false
    }

    // Media tab — store screenshots + trailers from appdetails, ONE request per page open (cache hits
    // and misses both count against Steam's rate limit, so the shared StoreMediaCache answers first;
    // a 429/transport failure is cached for two minutes only). The tab appears once non-empty.
    var media by remember(appId) { mutableStateOf<StoreMedia?>(null) }
    var mediaLoading by remember(appId) { mutableStateOf(true) }
    LaunchedEffect(appId) {
        mediaLoading = true
        val id = appId.toString()
        media = withContext(Dispatchers.IO) {
            StoreMediaCache.get(context, Store.STEAM, id) ?: run {
                val fetched = try {
                    SteamStoreSearch.fetchMedia(appId, SteamRegion.storeCountryCode(context))
                } catch (_: Throwable) { null }
                StoreMediaCache.put(context, Store.STEAM, id, fetched ?: StoreMedia.EMPTY, miss = fetched == null)
                fetched ?: StoreMedia.EMPTY
            }
        }
        mediaLoading = false
    }
    val mediaTabVisible = media?.isEmpty == false
    val detailTabs = remember(mediaTabVisible) {
        DetailTab.values().filter { it != DetailTab.MEDIA || mediaTabVisible }
    }
    LaunchedEffect(mediaTabVisible) {
        if (!mediaTabVisible && selectedTab == DetailTab.MEDIA) selectedTab = DetailTab.DETAILS
    }

    // DLC tab — the FULL owned-DLC catalogue (broader than the depot-bundled `dlcEntries` picker set:
    // it also lists DLC that are owned but ship no separate download, e.g. Risk of Rain 2's DLC). Loaded
    // like achievements: paint from the cached steam_dlc rows, kick a best-effort PICS resolve for
    // names/kinds off the UI thread, then re-read. `dlcHasAny` distinguishes "no DLC" from "not resolved
    // yet". This never touches the depot-bundled picker/size path (still driven by `dlcEntries`).
    var dlcTabRows by remember(appId) { mutableStateOf<List<DlcTabEntry>>(emptyList()) }
    var dlcHasAny by remember(appId) { mutableStateOf(false) }
    var dlcTabLoading by remember(appId) { mutableStateOf(true) }
    LaunchedEffect(appId, signedIn) {
        dlcTabLoading = true
        val repo = SteamRepository.getInstance()
        fun readRows(): Pair<List<DlcTabEntry>, Boolean> = try {
            val db = repo.database
            val rows = db.getDlcRows(appId).map {
                DlcTabEntry(it.dlcAppId, it.name, it.owned, dlcKindTag(it.kind, it.owned))
            }
            rows to db.hasAnyDlc(appId)
        } catch (_: Throwable) { emptyList<DlcTabEntry>() to false }

        // Instant paint from cache.
        val (cached, cachedHas) = withContext(Dispatchers.IO) { readRows() }
        dlcTabRows = cached; dlcHasAny = cachedHas
        // Best-effort resolve (self-heal + names/kinds), then re-read. No-op when signed out.
        withContext(Dispatchers.IO) {
            try { repo.resolveOwnedDlc(appId, 15_000L) } catch (_: Throwable) {}
        }
        val (fresh, freshHas) = withContext(Dispatchers.IO) { readRows() }
        dlcTabRows = fresh; dlcHasAny = freshHas
        dlcTabLoading = false
    }

    // The tile tapped in the icon-only grid → drives the caption bar. Reset when the game changes.
    var selectedAch by remember(appId) { mutableStateOf<SteamAchievement?>(null) }

    // The Achievements caption bar is pinned to the screen bottom (outside the page scroll) so it
    // stays visible while the icon grid scrolls above it. We measure its height and reserve that much
    // bottom padding in the scroll so the last grid rows aren't hidden behind it.
    val density = LocalDensity.current
    var captionHeightPx by remember { mutableIntStateOf(0) }
    val showPinnedCaption =
        selectedTab == DetailTab.ACHIEVEMENTS && signedIn && !achLoading && achievements.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.weight(1f))
            SteamStatusPill(status = steamStatus, onReconnect = onReconnect)
            DownloadsButton()
        }

        // Hero image with a subtle gradient into the background at the bottom
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            // The SAME resolve chain the storefront cards use: PICS published art -> constructed
            // CDN hosts -> appdetails -> SteamGridDB -> themed placeholder.
            //
            // Previously this page built ONE constructed URL of its own, so a game that showed art
            // in the Library showed nothing here — and when that URL 404'd the `else` branch left a
            // spinner running forever, which is the one failure mode the chain must never produce.
            // aspectRatio = null because this hero is a fixed-height band, not a 92:43 card.
            StoreCapsule(
                appId = appId,
                title = nameText,
                modifier = Modifier.fillMaxSize(),
                aspectRatio = null,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, xmbSolidBackground()),
                        ),
                    ),
            )
        }

        // Game name — persistent header. Stays above the action row + tab strip (mirrors the
        // mockup's fixed game header, which doesn't change as the tab body below it swaps).
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                text = nameText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // (Download progress is shown ON the large primary button below — 2-layer fill + an info
        // line beneath it — so there's a single progress indicator, not a separate under-title bar.)

        // Primary action + gear menu — a single state-driven button (per the mockup) replacing the
        // old Install/Pause/Launch trio. EVERY former action stays reachable: the button drives
        // install / retry / add-to-shortcuts (and shows download progress read-only), while the gear
        // dropdown carries pause/resume, cancel, uninstall, branch, DLC and Goldberg. The underlying
        // handlers (onInstallClick/onPauseResumeClick/onLaunchClick) are UNCHANGED — only the trigger.
        val downloading = progressVisible   // installAction == CANCEL (active or paused download)
        val primaryShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Primary button ──────────────────────────────────────────────────────────────────
            if (downloading) {
                // Read-only download button: the SINGLE progress indicator. Same 2-layer fill the old
                // under-title bar used — lighter back layer = bytes fetched (downloadProgressValue),
                // solid front layer = bytes on disk (progressValue) — now on the primary button, in
                // theme primary. Pause/cancel live in the gear, so the button itself is non-actionable.
                val installFrac  = (progressValue / 100f).coerceIn(0f, 1f)
                val downloadFrac = (downloadProgressValue / 100f).coerceIn(0f, 1f)
                val label = if (pauseAction == PauseAction.RESUME) "Paused — $progressValue%"
                            else "Downloading… $progressValue%"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(primaryShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, AchvLine, primaryShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // Download (network) fill — lighter, underneath.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .fillMaxWidth(downloadFrac)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    )
                    // Install (on-disk) fill — solid, on top.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .fillMaxWidth(installFrac)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = label,
                        color = AchvInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            } else {
                // Accent action button: Add to shortcuts (installed) / Retry / Install.
                val (label, enabled, onPrimary) = when (installAction) {
                    InstallAction.UNINSTALL -> Triple("Add to shortcuts", launchBtnEnabled, onLaunchClick)
                    else                    -> Triple(installBtnText, installBtnEnabled, onInstallClick) // INSTALL / RETRY / transient
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(primaryShape)
                        .background(if (enabled) AchvAccent else AchvAccent.copy(alpha = 0.4f))
                        .clickable(enabled = enabled) { onPrimary() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            // ── Gear (⚙) dropdown ───────────────────────────────────────────────────────────────
            Box {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(primaryShape)
                        .background(AchvCard)
                        .border(1.dp, AchvLine, primaryShape)
                        .clickable { gearMenuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", fontSize = 18.sp, color = AchvAccent)
                }
                DropdownMenu(
                    expanded = gearMenuExpanded,
                    onDismissRequest = { gearMenuExpanded = false },
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    // Downloading → pause/resume + cancel lead, then the common items; a divider sits
                    // between every option (matching the app's other outlined menus).
                    if (downloading) {
                        val pauseEmoji = if (pauseAction == PauseAction.RESUME) "▶️" else "⏸️"
                        GearMenuItem(pauseEmoji, pauseBtnText, enabled = pauseBtnEnabled,
                            onClick = { gearMenuExpanded = false; onPauseResumeClick() })
                        MenuItemDivider()
                        GearMenuItem("🗑", "Cancel & delete download", danger = true, // hard delete + fresh-restart reset
                            onClick = { gearMenuExpanded = false; onCancelDeleteClick() })
                        MenuItemDivider()
                    }
                    GearMenuItem("🌿", "Choose branch",
                        onClick = { gearMenuExpanded = false; onBranchLineClick() })
                    MenuItemDivider()
                    GearMenuItem("🧩", "Manage DLC", enabled = dlcEntries.isNotEmpty(),
                        onClick = { gearMenuExpanded = false; onDlcLineClick() })
                    // Storage move — only for an installed game that has somewhere to go. Hidden
                    // (not greyed) otherwise, since "no SD card" isn't a state the user can act on.
                    if (moveStorageLabel != null) {
                        MenuItemDivider()
                        GearMenuItem("📦", moveStorageLabel,
                            onClick = { gearMenuExpanded = false; onMoveStorageClick() })
                    }
                    // Goldberg mode moved to the pre-launch launch-method popup (SteamLite vs Goldberg,
                    // see LaunchMethodSheet) — no longer a gear item here.
                    // Installed → Uninstall at the bottom.
                    if (installAction == InstallAction.UNINSTALL) {
                        MenuItemDivider()
                        GearMenuItem("🗑️", "Uninstall", danger = true, // installAction == UNINSTALL
                            onClick = { gearMenuExpanded = false; onInstallClick() })
                    }
                }
            }
        }

        // Download info line — moved out from under the (removed) small bar to directly under the
        // large button: "…% (done / total) · speed · ETA". Same content/format + gate as before.
        if (downloading && progressTextVisible) {
            Text(
                text = progressText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }

        // Tab strip — Details · Achievements (done/total) · DLC · Cloud saves. Styled like the
        // action buttons and horizontally scrollable, per the mockup.
        SteamDetailTabs(
            tabs = detailTabs,
            selected = selectedTab,
            achDone = achievements.count { it.unlocked },
            achTotal = achievements.size,
            mediaCount = media?.count ?: 0,
            onSelect = { selectedTab = it },
        )

        // Tab body — every old section routed into its tab; behaviour unchanged, only the layout
        // moved (sections became tab bodies).
        when (selectedTab) {
            // Details = the former info block: type/size chips, size breakdown, branch selector and
            // install status. (Goldberg moved to a popup opened from the gear menu.)
            DetailTab.DETAILS -> Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InfoChip(typeText)
                        Spacer(Modifier.width(8.dp))
                        InfoChip(sizeText)
                    }
                    // Labeled size breakdown: download (compressed), PICS estimate (Steam), free space.
                    if (sizeBreakdown.downloadLabel.isNotEmpty() ||
                        sizeBreakdown.picsLabel.isNotEmpty() ||
                        sizeBreakdown.freeLabel.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column {
                            if (sizeBreakdown.downloadLabel.isNotEmpty()) Text(
                                text = sizeBreakdown.downloadLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (sizeBreakdown.picsLabel.isNotEmpty()) Text(
                                text = sizeBreakdown.picsLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (sizeBreakdown.freeLabel.isNotEmpty()) Text(
                                text = sizeBreakdown.freeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (sizeBreakdown.fits) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    // Beta-branch selector — only when the app actually exposes a non-public branch.
                    // Switching branches installs that branch's build on the next install or update.
                    if (branches.any { it.name != "public" }) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Branch: " + if (selectedBranch == "public") "public (default)" else selectedBranch,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(
                            onClick = onBranchLineClick,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Text("Change branch")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (gameStatus) {
                            GameStatus.INSTALLED -> Color(0xFF4CAF50) // semantic installed-green
                            GameStatus.FAILED    -> MaterialTheme.colorScheme.error
                            else                 -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // Achievements = the new icon-only mockup view (progress + grid + caption + legend).
            DetailTab.ACHIEVEMENTS -> AchievementsTabBody(
                signedIn = signedIn,
                loading = achLoading,
                achievements = achievements,
                selected = selectedAch,
                onSelect = { selectedAch = it },
            )

            // DLC = the FULL owned-DLC list (each tagged by how its content is delivered) + the existing
            // depot-bundled picker. `dlcTabRows` broadens what the tab DISPLAYS to every owned DLC;
            // `dlcEntries` (depot-bundled subset) still drives the "Choose DLC" download picker/size.
            DetailTab.DLC -> Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            ) {
                val ownedDlc = dlcTabRows.filter { it.owned }
                val unownedDlc = dlcTabRows.filter { !it.owned }
                when {
                    // Still resolving and nothing cached yet — brief, only on a never-resolved game.
                    dlcTabLoading && dlcTabRows.isEmpty() && !dlcHasAny -> Text(
                        text = "Loading DLC…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Resolved and the game genuinely lists no DLC.
                    !dlcHasAny -> Text(
                        text = "No DLC available for this game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Has DLC, but the user owns none of it.
                    ownedDlc.isEmpty() -> Text(
                        text = "You don't own any DLC for this game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Text(
                            text = "Your DLC",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        ownedDlc.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                InfoChip(entry.tag)
                            }
                        }
                        // Depot-bundled owned DLC can be opted out of the download — keep the existing
                        // picker (gated on the depot-bundled subset; entitlement DLC have nothing to toggle).
                        if (dlcEntries.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = includedDlcText.ifEmpty { "Choose which owned DLC download with this game." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = onDlcLineClick,
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text("Choose DLC") }
                        }
                    }
                }
                // Optional: DLC the game has but the user doesn't own, greyed, for context.
                if (unownedDlc.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Not owned",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    unownedDlc.forEach { entry ->
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        )
                    }
                }
            }

            // Media = the shared store Media tab (trailers + screenshots, viewer, playback handoff).
            DetailTab.MEDIA -> MediaTab(
                media = media,
                loading = mediaLoading,
                storeLabel = "Steam",
                onOpenVideo = { MediaPlayback.openVideo(context, it) },
            )

            // Cloud saves = the existing three-tier manager when it's available (installed + a live
            // Steam session); otherwise a compact status line explaining what's needed.
            DetailTab.CLOUD -> {
                if (cloudVisible) {
                    CloudSavesSection(
                        busy = cloudBusy,
                        status = cloudStatus,
                        resolved = cloudResolved,
                        containerReady = cloudContainerReady,
                        containerLabel = cloudContainerLabel,
                        libraryPath = cloudLibraryPath,
                        onSyncFromCloud = onCloudSyncFrom,
                        onSyncToCloud = onCloudSyncTo,
                        onDownload = onCloudDownload,
                        onApply = onCloudApply,
                        onCollect = onCloudCollect,
                        onUpload = onCloudUpload,
                        onSetUp = onCloudSetUp,
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    ) {
                        Text(
                            text = "Steam Cloud Saves",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (!signedIn) "Sign in to Steam to sync this game's cloud saves."
                                   else "Install this game to sync its cloud saves with your Steam Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Reserve scroll space equal to the pinned caption's height (Achievements tab only), so the
        // last grid rows can scroll clear of the pinned bar below.
        if (showPinnedCaption) {
            Spacer(Modifier.height(with(density) { captionHeightPx.toDp() }))
        }
    }

    // Pinned achievements caption — lifted OUT of the page scroll, kept at the screen bottom while
    // the icon grid scrolls above it. Only for the Achievements tab's grid state.
    if (showPinnedCaption) {
        AchievementCaptionBar(
            selected = selectedAch,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .onSizeChanged { captionHeightPx = it.height },
        )
    }
    }

    // DLC picker sheet — choose which owned DLC download with the game (opt-out).
    if (showDlcSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = onDismissDlcSheet, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                Text(
                    text = "DLC to download",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Unchecked DLC won't download with the game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
                dlcEntries.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleDlc(id) }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = id !in excludedDlc, onCheckedChange = { onToggleDlc(id) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDismissDlcSheet,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Done") }
            }
        }
    }

    // Beta-branch picker sheet — pick which branch to download/install. Selectable branches (public +
    // any unlocked beta) get a radio; a locked beta needs a verified access code first.
    // Ported from GameNative (GPL-3.0): SteamAppScreen branch picker + password field.
    if (showBranchSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = onDismissBranchSheet, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                Text(
                    text = "Choose a branch",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Switching branches downloads and installs that branch's build the next " +
                        "time you install or update this game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
                branches.forEach { b ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = b.unlocked) { onSelectBranch(b.name) }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(
                            selected = selectedBranch == b.name,
                            enabled = b.unlocked,
                            onClick = { onSelectBranch(b.name) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = (if (b.name == "public") "public (default)" else b.name) +
                                    (if (b.pwdRequired && !b.unlocked) "  🔒" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (b.description.isNotEmpty()) Text(
                                text = b.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val updated = formatBranchUpdated(b.timeUpdated)
                            if (updated.isNotEmpty()) Text(
                                text = "Updated $updated",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Access-code entry for locked betas. Steam returns every valid beta password for the
                // app in one response, so a single correct code can unlock more than one branch.
                if (branches.any { it.pwdRequired && !it.unlocked }) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Unlock a password-protected beta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    var code by remember { mutableStateOf("") }
                    // The sheet is a window of its own, so its field takes the XMB keyboard hook here.
                    XmbKeyboardWindow {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            singleLine = true,
                            enabled = !branchCheckBusy,
                            label = { Text("Beta access code") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }
                    branchCheckMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("Access code accepted"))
                                Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Button(
                        onClick = { onCheckBranchPassword(code) },
                        enabled = !branchCheckBusy && code.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (branchCheckBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Unlock branch")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDismissBranchSheet,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Done") }
            }
        }
    }

    // Goldberg (Steam emulator) popup — the former inline Details section, now a gear-menu dialog.
    if (goldbergDialogOpen) {
        GoldbergModeDialog(
            installed = goldbergInstalled,
            downloading = goldbergDownloading,
            downloadProgress = goldbergDownloadProgress,
            sizeLabel = goldbergSizeLabel,
            outdated = goldbergOutdated,
            catalogVersion = goldbergCatalogVersion,
            mode = goldbergMode,
            busy = goldbergBusy,
            onDownloadClick = onGoldbergDownloadClick,
            onModeSelected = onGoldbergModeSelected,
            onDismiss = { goldbergDialogOpen = false },
        )
    }
}

/** One row of the gear (⚙) dropdown: a leading emoji + a label. `danger` recolors destructive
 *  items (Cancel / Uninstall). Caller's `onClick` dismisses the menu then runs the real handler. */
@Composable
private fun GearMenuItem(
    emoji: String,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val base = if (danger) MaterialTheme.colorScheme.error else AchvInk
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = if (enabled) base else base.copy(alpha = 0.4f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        },
        leadingIcon = { Text(emoji, fontSize = 14.sp) },
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * The detail page's tab strip — pill tabs styled like the action buttons (fixed mockup palette, not
 * MaterialTheme). Horizontally scrollable. The Achievements tab carries a `done/total` count badge
 * once its list has loaded (hidden while empty / not signed in).
 */
@Composable
private fun SteamDetailTabs(
    tabs: List<DetailTab>,
    selected: DetailTab,
    achDone: Int,
    achTotal: Int,
    mediaCount: Int,
    onSelect: (DetailTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            val isSel = tab == selected
            val badge = when {
                tab == DetailTab.ACHIEVEMENTS && achTotal > 0 -> "$achDone/$achTotal"
                tab == DetailTab.MEDIA && mediaCount > 0 -> "$mediaCount"
                else -> null
            }
            val shape = RoundedCornerShape(9.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(shape)
                    .background(if (isSel) AchvTabActiveBg else AchvCard)
                    .border(1.dp, if (isSel) AchvTabActiveBorder else AchvLineSoft, shape)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
            ) {
                Text(
                    text = tab.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSel) AchvTabActiveText else AchvInk2,
                    maxLines = 1,
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) AchvTabActiveBadge else AchvBadgeBg)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) AchvTabActiveText else AchvInk3,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Achievements tab body — the approved icon-only mockup: a progress block (label + gold fraction +
 * gold gradient bar + "N% complete"), a 5-column square-icon grid (colour = unlocked with a gold
 * border + ✓ badge, greyscaled = locked, a lock glyph + badge for hidden-and-locked), and a caption
 * bar that names the tapped tile (status pill + description + unlock date) with a legend. The list is
 * loaded by the caller (shared with the tab count badge); this renders the not-signed-in / loading /
 * empty / grid states. The 5-col grid is a chunked weighted-Row grid so it nests in the page's
 * verticalScroll (a LazyVerticalGrid can't).
 */
@Composable
private fun AchievementsTabBody(
    signedIn: Boolean,
    loading: Boolean,
    achievements: List<SteamAchievement>,
    selected: SteamAchievement?,
    onSelect: (SteamAchievement) -> Unit,
) {
    // Long-press on a tile opens the app's "?" help popup for that achievement (additive to the
    // tap→caption behaviour). Scoped to this tab body; switching tabs drops it back to null.
    var dialogAchv by remember { mutableStateOf<SteamAchievement?>(null) }

    when {
        !signedIn -> Text(
            text = "Sign in to Steam to view achievements.",
            fontSize = 13.sp,
            color = AchvInk2,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AchvAccent,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text("Loading achievements…", fontSize = 13.sp, color = AchvInk2)
        }
        achievements.isEmpty() -> Text(
            text = "This game has no achievements.",
            fontSize = 13.sp,
            color = AchvInk2,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        else -> {
            val total = achievements.size
            val done = achievements.count { it.unlocked }
            val pct = SteamAchievementStore.percentUnlocked(achievements)
            Column(modifier = Modifier.fillMaxWidth()) {
                // Progress block (padded to match the rest of the page content).
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    AchievementProgressBlock(done = done, total = total, pct = pct)
                }
                // Icon grid — rows of 5 evenly-weighted square tiles; last row left-aligned.
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    achievements.chunked(5).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            for (a in rowItems) {
                                AchievementTile(
                                    a = a,
                                    selected = a == selected,
                                    onClick = { onSelect(a) },
                                    onLongClick = { dialogAchv = a },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(5 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                // NOTE: the caption/legend bar is rendered by the SCREEN, pinned to the bottom of
                // the page (outside this scroll) so it stays visible while the grid scrolls.
            }
        }
    }

    // Long-press help popup — rendered whenever a tile has been long-pressed.
    dialogAchv?.let { achv -> AchievementHelpDialog(achv, onDismiss = { dialogAchv = null }) }
}

/** Progress block: "Achievements" label + gold `done` in the fraction + gold-gradient bar + "N%". */
@Composable
private fun AchievementProgressBlock(done: Int, total: Int, pct: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Achievements", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AchvInk)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = AchvGold, fontWeight = FontWeight.Bold)) { append("$done") }
                    append(" / $total unlocked")
                },
                fontSize = 13.sp,
                color = AchvInk2,
            )
        }
        Spacer(Modifier.height(8.dp))
        val frac = (pct / 100f).coerceIn(0f, 1f)
        val trackShape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(trackShape)
                .background(AchvTrackBg)
                .border(1.dp, AchvLineSoft, trackShape),
        ) {
            if (frac > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .clip(trackShape)
                        .background(Brush.horizontalGradient(listOf(AchvGoldDim, AchvGold))),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$pct% complete",
            fontSize = 11.sp,
            color = AchvInk3,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/**
 * One square icon tile. Unlocked → colour icon + gold border/glow + ✓ badge. Locked (not hidden) →
 * the SAME colour icon greyscaled (saturation-0 ColorMatrix) at ~0.5 alpha, more reliable than the
 * CDN gray asset. Hidden-and-locked → a lock glyph instead of the art + a small lock badge. A tapped
 * tile takes an accent border. Kept square via aspectRatio(1f) so it nests in a vertical scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AchievementTile(
    a: SteamAchievement,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenLocked = a.hidden && !a.unlocked
    val shape = RoundedCornerShape(11.dp)
    val bg = if (a.unlocked) Brush.linearGradient(listOf(AchvTileUnlockedTop, AchvTileUnlockedBot))
             else Brush.linearGradient(listOf(AchvTileLockedTop, AchvTileLockedBot))
    val borderColor = when {
        selected   -> AchvAccent
        a.unlocked -> AchvUnlockedBorder
        else       -> AchvLineSoft
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (hiddenLocked) {
            Text("🔒", fontSize = 22.sp)
        } else {
            // Colour icon for both states; grey it for locked (prefer local file, fall back to URL).
            val model: Any? = a.localIconPath?.takeIf { it.isNotEmpty() }?.let { File(it) }
                ?: a.iconUrl.takeIf { it.isNotEmpty() }
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = a.displayName.ifEmpty { a.apiName },
                    contentScale = ContentScale.Crop,
                    alpha = if (a.unlocked) 1f else 0.5f,
                    colorFilter = if (a.unlocked) null
                                  else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                    modifier = Modifier.fillMaxSize().clip(shape),
                )
            }
        }
        if (a.unlocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(AchvGold),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Black, color = AchvChkGlyph)
            }
        } else if (hiddenLocked) {
            Text(
                text = "🔒",
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
    }
}

/**
 * Long-press help popup for a tile — rendered through the app's standard [OutlinedAlertDialog] (the
 * same rounded/outlined "?" look, with a "Got it" confirm) so it matches every other help dialog.
 * The body mirrors the mockup's `.dlg` block: a centered column of the achievement's full art
 * (78dp, gold-bordered + tinted when unlocked, greyscaled when locked, a lock glyph when hidden and
 * locked), its name, a status pill (gold "Unlocked · <date>" / muted "Locked" / "Hidden"), and the
 * description (a generic hint for a blank hidden-and-locked one).
 */
@Composable
private fun AchievementHelpDialog(a: SteamAchievement, onDismiss: () -> Unit) {
    val hiddenLocked = a.hidden && !a.unlocked
    val iconShape = RoundedCornerShape(16.dp)
    val pillShape = RoundedCornerShape(20.dp)
    val statusLabel = when {
        a.unlocked && a.unlockTimeSec > 0L -> "Unlocked · ${formatUnlockDate(a.unlockTimeSec)}"
        a.unlocked                         -> "Unlocked"
        hiddenLocked                       -> "Hidden"
        else                               -> "Locked"
    }
    val desc = when {
        a.description.isNotEmpty() -> a.description
        hiddenLocked               -> "Keep playing to reveal this one."
        else                       -> ""
    }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Icon — 78dp, 16dp corner. The dimmed (locked) look is applied to the whole box.
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .alpha(if (a.unlocked) 1f else 0.55f)
                        .clip(iconShape)
                        .background(Brush.linearGradient(listOf(AchvTileUnlockedTop, AchvTileUnlockedBot)))
                        .border(1.dp, if (a.unlocked) AchvUnlockedBorder else AchvLineSoft, iconShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hiddenLocked) {
                        Text("🔒", fontSize = 40.sp)
                    } else {
                        val model: Any? = a.localIconPath?.takeIf { it.isNotEmpty() }?.let { File(it) }
                            ?: a.iconUrl.takeIf { it.isNotEmpty() }
                        if (model != null) {
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                colorFilter = if (a.unlocked) null
                                              else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                                modifier = Modifier.fillMaxSize().clip(iconShape),
                            )
                        } else {
                            Text("🏆", fontSize = 40.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (hiddenLocked) "Hidden achievement" else a.displayName.ifEmpty { a.apiName },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AchvInk,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(if (a.unlocked) AchvPillOnBg else AchvBadgeBg)
                        .border(1.dp, if (a.unlocked) AchvPillOnBorder else AchvLineSoft, pillShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (a.unlocked) AchvGold else AchvInk3,
                    )
                }
                if (desc.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = desc,
                        fontSize = 13.sp,
                        color = AchvInk2,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

/**
 * The caption bar under the icon-only grid (edge-to-edge, top border + card-2 bg). With nothing
 * selected it shows the default hint; a tapped tile shows its name + status pill + description (and
 * unlock date when unlocked). Always ends with the Unlocked / Locked / Hidden legend.
 */
@Composable
private fun AchievementCaptionBar(selected: SteamAchievement?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(AchvLineSoft))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AchvCard2)
                .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 14.dp),
        ) {
            if (selected == null) {
                Text("Tap an icon to see details", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AchvInk)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "The grid shows icons only — completed ones in full colour, locked ones greyed out.",
                    fontSize = 12.sp,
                    color = AchvInk2,
                    lineHeight = 17.sp,
                )
            } else {
                val hiddenLocked = selected.hidden && !selected.unlocked
                val name = if (hiddenLocked) "Hidden achievement" else selected.displayName.ifEmpty { selected.apiName }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AchvInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    AchvStatusPill(unlocked = selected.unlocked)
                }
                Spacer(Modifier.height(4.dp))
                val desc = when {
                    hiddenLocked                      -> "Keep playing to reveal this one."
                    selected.description.isNotEmpty() -> selected.description
                    selected.unlocked                 -> "Unlocked."
                    else                              -> "Locked — not yet earned."
                }
                Text(desc, fontSize = 12.sp, color = AchvInk2, lineHeight = 17.sp)
                if (selected.unlocked && selected.unlockTimeSec > 0L) {
                    Spacer(Modifier.height(3.dp))
                    Text("Unlocked ${formatUnlockDate(selected.unlockTimeSec)}", fontSize = 11.sp, color = AchvGold)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AchvLegendItem(swatch = { AchvLegendSwatch(unlocked = true) }, label = "Unlocked")
                AchvLegendItem(swatch = { AchvLegendSwatch(unlocked = false) }, label = "Locked")
                AchvLegendItem(swatch = { Text("🔒", fontSize = 11.sp) }, label = "Hidden")
            }
        }
    }
}

/** Status pill for the caption bar: gold "Unlocked" vs muted "Locked". */
@Composable
private fun AchvStatusPill(unlocked: Boolean) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (unlocked) AchvPillOnBg else AchvBadgeBg)
            .border(1.dp, if (unlocked) AchvPillOnBorder else AchvLineSoft, shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (unlocked) "Unlocked" else "Locked",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (unlocked) AchvGold else AchvInk3,
        )
    }
}

/** One legend entry: a swatch (or glyph) + a caption in ink-3. */
@Composable
private fun AchvLegendItem(swatch: @Composable () -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(label, fontSize = 11.sp, color = AchvInk3)
    }
}

/** A 12dp legend swatch: unlocked (gold-bordered bright gradient) vs locked (dim greyed gradient). */
@Composable
private fun AchvLegendSwatch(unlocked: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(shape)
            .background(
                if (unlocked) Brush.linearGradient(listOf(AchvTileUnlockedTop, AchvTileUnlockedBot))
                else Brush.linearGradient(listOf(AchvTileLockedTop, AchvTileLockedBot)),
                alpha = if (unlocked) 1f else 0.5f,
            )
            .border(1.dp, if (unlocked) AchvUnlockedBorder else AchvLine, shape),
    )
}

/** Format an achievement unlock time (epoch seconds) as a readable date; "" when unknown. */
private fun formatUnlockDate(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    return try {
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(epochSeconds * 1000L))
    } catch (_: Throwable) { "" }
}

/**
 * Goldberg popup — opened from the gear menu (installed games only). Wraps the [GoldbergSection]
 * content (title + description + at-your-own-risk note + the component download button when it isn't
 * installed yet + the Off/Regular/Experimental/ColdClient mode picker) in the app's standard
 * [OutlinedAlertDialog], with a "Done" confirm. All Goldberg behaviour is unchanged — just relocated
 * from the inline Details section into this dialog.
 */
@Composable
private fun GoldbergModeDialog(
    installed: Boolean,
    downloading: Boolean,
    downloadProgress: Float,
    sizeLabel: String,
    outdated: Boolean,
    catalogVersion: Int,
    mode: GoldbergMode,
    busy: Boolean,
    onDownloadClick: () -> Unit,
    onModeSelected: (GoldbergMode) -> Unit,
    onDismiss: () -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            // Bound + scroll the body so the picker + download button always fit (short landscape too).
            val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                GoldbergSection(
                    installed = installed,
                    downloading = downloading,
                    downloadProgress = downloadProgress,
                    sizeLabel = sizeLabel,
                    outdated = outdated,
                    catalogVersion = catalogVersion,
                    mode = mode,
                    busy = busy,
                    onDownloadClick = onDownloadClick,
                    onModeSelected = onModeSelected,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

/**
 * Opt-in "Steam Emulator (Goldberg)" section. Goldberg is ONE global download
 * shared by every game: until it's installed, this shows a "Download Steam
 * Emulator" button (with progress + MD5-verified extract); once installed it
 * shows the tier selector. Tiers are escalating fallbacks. The helper text is
 * deliberately honest: Goldberg only lets a game *start* without Steam — it
 * can't reach a publisher's own online servers. Rendered inside [GoldbergModeDialog].
 */
@Composable
private fun GoldbergSection(
    installed: Boolean,
    downloading: Boolean,
    downloadProgress: Float,
    sizeLabel: String,
    outdated: Boolean,
    catalogVersion: Int,
    mode: GoldbergMode,
    busy: Boolean,
    onDownloadClick: () -> Unit,
    onModeSelected: (GoldbergMode) -> Unit,
) {
    val options = listOf(
        GoldbergMode.OFF to "Off",
        GoldbergMode.REGULAR to "Regular",
        GoldbergMode.EXPERIMENTAL to "Experimental",
        GoldbergMode.COLDCLIENT to "Cold Client Loader",
    )
    // Rendered inside the Goldberg popup (OutlinedAlertDialog provides the card), so this is a plain
    // content column with no card wrapper of its own.
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Steam Emulator (Goldberg)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Lets games that require Steam start without it. " +
                "Won't fix online-only games that can't reach their own servers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Please note: this is not a fix for all Steam games that require a " +
                "Steam client to run. It is not a guaranteed fix-all — use at your own risk!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        // Download when not installed yet; Update when an installed copy is behind
        // the catalog (a bump must reach existing installs, not just new ones).
        val showDownloadButton = !installed || outdated
        if (showDownloadButton) {
            Spacer(Modifier.height(12.dp))
            if (downloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Downloading… ${(downloadProgress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (installed && outdated) {
                    // A newer build is available — small hint above the Update button.
                    Text(
                        text = if (catalogVersion > 0) "Update available — gbe_fork v$catalogVersion"
                            else "Update available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                val sizeSuffix = if (sizeLabel.isNotEmpty()) " (~$sizeLabel)" else ""
                val verb = if (installed) "Update" else "Download"
                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("$verb Steam Emulator$sizeSuffix", maxLines = 1) }
            }
        }

        // No tier selector until the component is actually on disk. An outdated
        // (but installed) copy still shows the picker below the Update button.
        if (!installed) return@Column

        Spacer(Modifier.height(if (showDownloadButton) 12.dp else 2.dp))
        Text(
            text = "Regular works for most games; try Experimental, then " +
                "Cold Client Loader if a game still won't start.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        options.forEach { (optMode, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy) { onModeSelected(optMode) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(
                    selected = mode == optMode,
                    onClick = { onModeSelected(optMode) },
                    enabled = !busy,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Applying…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Steam Cloud saves — the three-tier "save library" manager (Cloud ⇄ Library ⇄ Container). Four
 * directional moves grouped into a Cloud row (Download / Upload) and a Container row (Apply / Collect):
 *   Download  Cloud → Library      Apply    Library → Container
 *   Upload    Library → Cloud      Collect  Container → Library
 * Every button routes through the caller, which shows a per-move confirm naming the target container.
 * If the game has no launch container yet (containerReady == false) the section shows a "set up first"
 * state offering the add-to-container flow instead of the buttons. Shown only when signed in
 * (caller's `cloudVisible`); a live Steam session is required for the Cloud row.
 */
@Composable
private fun CloudSavesSection(
    busy: Boolean,
    status: String?,
    resolved: Boolean,
    containerReady: Boolean,
    containerLabel: String?,
    libraryPath: String,
    onSyncFromCloud: () -> Unit,
    onSyncToCloud: () -> Unit,
    onDownload: () -> Unit,
    onApply: () -> Unit,
    onCollect: () -> Unit,
    onUpload: () -> Unit,
    onSetUp: () -> Unit,
) {
    // The four granular moves live under a collapsed "Advanced" expander; the primary UI is the two
    // end-to-end combos. Collapsed by default so re-opening the page starts simple.
    var advancedExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = "Steam Cloud Saves",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Sync this game's saves with your Steam Cloud in one step, either direction. A local " +
                "Library you own is kept as an internal middle copy along the way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Always-visible third-party disclaimer note.
        Spacer(Modifier.height(6.dp))
        Text(
            text = "⚠️ Third-party cloud sync — use at your own risk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        when {
            // Resolution still in flight — avoid flashing the "set up first" state before we know.
            !resolved -> {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Checking this game's container…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // No launch container — Apply/Collect have nowhere to go. Offer the add-to-container flow.
            !containerReady -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Set this game up in a container first. Its saves follow whichever " +
                        "container you add it to — then you can sync them with the cloud here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSetUp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Set up in a container", maxLines = 1) }
            }

            else -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Container: ${containerLabel ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Library: $libraryPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Primary: the two end-to-end combos, each with a one-line description underneath.
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onSyncFromCloud,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("⬇ Sync from Cloud", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Bring your Steam Cloud saves down and into this game.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onSyncToCloud,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("⬆ Sync to Cloud", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Send this game's current saves up to Steam Cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Advanced expander — the original four granular moves, hidden by default.
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { advancedExpanded = !advancedExpanded }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        text = if (advancedExpanded) "▾ Advanced" else "▸ Advanced",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "individual Download / Apply / Collect / Upload",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (advancedExpanded) {
                    // Cloud row — Download (Cloud → Library) / Upload (Library → Cloud, additive).
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Cloud",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDownload,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("⬇ Download", maxLines = 1, overflow = TextOverflow.Ellipsis) }

                        OutlinedButton(
                            onClick = onUpload,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("⬆ Upload", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }

                    // Container row — Apply (Library → Container) / Collect (Container → Library).
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Container",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onApply,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("→ Apply", maxLines = 1, overflow = TextOverflow.Ellipsis) }

                        OutlinedButton(
                            onClick = onCollect,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("← Collect", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }

                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = status ?: "Working…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (!status.isNullOrEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Per-move confirm dialog. Names the target container, states the direction + additive/overwrite
 * semantics, surfaces the (non-blocking) staleness warning for Apply/Upload, and repeats the
 * third-party reminder. Confirm label matches the move.
 */
@Composable
private fun CloudConfirmDialog(
    confirm: CloudConfirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val container = confirm.containerLabel
    val (title, body, action) = when (confirm.move) {
        CloudMove.DOWNLOAD -> Triple(
            "Download from Cloud?",
            "Download this game's Steam Cloud saves into your local save Library. This replaces the " +
                "Library's copy of those files with the cloud versions. It doesn't touch the game's " +
                "container — use Apply afterwards to put them where the game reads them.",
            "Download",
        )
        CloudMove.APPLY -> Triple(
            "Apply to container?",
            "Copy the saves from your Library into $container (this game's container). This overwrites " +
                "that container's copy of those files with your Library copy. Your Library isn't changed.",
            "Apply",
        )
        CloudMove.COLLECT -> Triple(
            "Collect from container?",
            "Copy the saves from $container (this game's container) into your Library, capturing any " +
                "progress you've made. This overwrites the Library's copy of those files. The container " +
                "isn't changed.",
            "Collect",
        )
        CloudMove.UPLOAD -> Triple(
            "Upload to Cloud?",
            "Upload the saves in your local Library to your Steam Cloud. This only ADDS or REPLACES " +
                "cloud files — it never deletes anything from the cloud. If the Library is empty, " +
                "nothing is sent. Make sure your Library has your latest saves (Collect after playing).",
            "Upload",
        )
        CloudMove.SYNC_FROM -> Triple(
            "Sync from Cloud?",
            "Bring your Steam Cloud saves down and into $container (this game's container). This " +
                "overwrites this container's saves with the cloud copy.",
            "Sync from Cloud",
        )
        CloudMove.SYNC_TO -> Triple(
            "Sync to Cloud?",
            "Send this game's current saves from $container up to your Steam Cloud. Make sure this is " +
                "the container with your latest saves — this only ADDS or REPLACES cloud files, it " +
                "never deletes anything from the cloud.",
            "Sync to Cloud",
        )
    }
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                if (confirm.stalenessWarning != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = confirm.stalenessWarning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Third-party cloud sync — use at your own risk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(action) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun InfoChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadSpeedPickerDialog(
    selectedIndex: Int,
    sdTarget: SteamSdInstall.SdTarget?,
    onDismiss: () -> Unit,
    onDownload: (speedTier: Int, debugLog: Boolean, installToSd: Boolean) -> Unit,
) {
    // Tiers mirror GameNative: cores × ratio scales download + decompress concurrency.
    // Higher tiers download faster but use more RAM/CPU during decompression.
    val options = listOf(
        "Slow — lowest RAM/CPU" to DownloadSpeedConfig.TIER_SLOW,
        "Medium — balanced" to DownloadSpeedConfig.TIER_MEDIUM,
        "Fast — recommended" to DownloadSpeedConfig.TIER_FAST,
        "Blazing — fastest, highest RAM/CPU" to DownloadSpeedConfig.TIER_BLAZING,
    )
    var selected by remember { mutableIntStateOf(selectedIndex) }
    // Per-download, not persisted — defaults off each time (scoped to this one download).
    var debugLog by remember { mutableStateOf(false) }
    // SD-card install opt-in — off by default; only offered when a removable card is present.
    var installToSd by remember { mutableStateOf(false) }

    // Per-"?" help — a general one on the header plus one per speed tier. Reuses the shared
    // HelpDialog (a centered, scrollable Compose dialog); it layers on top of whichever layout
    // is showing, so it's rendered once here before the landscape/portrait branch.
    var helpRes by remember { mutableStateOf<Int?>(null) }
    helpRes?.let { HelpDialog(it) { helpRes = null } }
    fun tierHelpRes(tier: Int): Int = when (tier) {
        DownloadSpeedConfig.TIER_SLOW -> R.string.help_download_speed_slow
        DownloadSpeedConfig.TIER_MEDIUM -> R.string.help_download_speed_medium
        DownloadSpeedConfig.TIER_FAST -> R.string.help_download_speed_fast
        else -> R.string.help_download_speed_blazing
    }

    // Landscape gets a WIDE two-column variant. In landscape the portrait Column (4 radios +
    // debug checkbox +warning + SD checkbox +warning) stacks past the short screen height and
    // clips the action buttons with no scroll, so split it into two scrollable columns under a
    // pinned action bar. Portrait is unchanged. Same state + callback as below — only the layout
    // differs. (State is remembered above, before this branch, so both paths share it.)
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        val cs = MaterialTheme.colorScheme
        // Match ExePicker/PerformanceDashboard: bound to the CURRENT (short) landscape height so
        // the card + its pinned bar always fit; the columns scroll inside.
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.94f).dp
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .widthIn(max = 720.dp)
                    .heightIn(max = maxH),
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.22f)),
            ) {
                Column(Modifier.heightIn(max = maxH)) {
                    // Header — no download-size/on-disk values are in scope in this composable, so
                    // per the mock's subline we keep the header to the title only rather than
                    // inventing numbers (portrait shows the same title). A general "?" sits at the
                    // end of the header row.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    ) {
                        Text(
                            text = "Download speed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { helpRes = R.string.help_download_speed }) {
                            Icon(
                                Icons.Default.Help,
                                contentDescription = "What is this?",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(cs.outline.copy(alpha = 0.20f)),
                    )

                    // ── Two scrollable columns; the action bar below sits OUTSIDE this Row. ──
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        // LEFT — speed tiers with an inline 4-segment RAM/CPU load meter.
                        Column(
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        ) {
                            Text(
                                text = "SPEED & RESOURCE USE",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            // Iterate the SAME options list — tier value + selection index are
                            // unchanged; only the display name/hint are derived for the wide layout.
                            options.forEachIndexed { index, (_, tier) ->
                                val isSel = selected == index
                                val name = when (tier) {
                                    DownloadSpeedConfig.TIER_SLOW -> "Slow"
                                    DownloadSpeedConfig.TIER_MEDIUM -> "Medium"
                                    DownloadSpeedConfig.TIER_FAST -> "Fast"
                                    else -> "Blazing"
                                }
                                val hint = when (tier) {
                                    DownloadSpeedConfig.TIER_SLOW ->
                                        "Lowest RAM / CPU — gentlest on the device"
                                    DownloadSpeedConfig.TIER_MEDIUM ->
                                        "Balanced throughput and resource use"
                                    DownloadSpeedConfig.TIER_FAST ->
                                        "Best speed for most devices"
                                    else -> "Fastest — highest RAM / CPU on decompress"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSel) cs.primary.copy(alpha = 0.16f)
                                            else Color.Transparent,
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSel) cs.primary.copy(alpha = 0.55f)
                                            else cs.outline.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable { selected = index }
                                        .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                                ) {
                                    RadioButton(
                                        selected = isSel,
                                        onClick = { selected = index },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = cs.onSurface,
                                            )
                                            // Fast is the recommended default — flag it by tier
                                            // value (not a hard index) so it tracks the list.
                                            if (tier == DownloadSpeedConfig.TIER_FAST) {
                                                Spacer(Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(cs.primary)
                                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                                ) {
                                                    Text(
                                                        text = "RECOMMENDED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = cs.onPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            // Meter fills Slow=1 … Blazing=4.
                                            SpeedLoadMeter(filled = index + 1)
                                        }
                                        Text(
                                            text = hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                        )
                                    }
                                    // Per-tier "?" — has its own onClick, so tapping it opens
                                    // help without selecting the row.
                                    IconButton(onClick = { helpRes = tierHelpRes(tier) }) {
                                        Icon(
                                            Icons.Default.Help,
                                            contentDescription = "What is this?",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Column divider.
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(cs.outline.copy(alpha = 0.20f)),
                        )

                        // RIGHT — the two option checkboxes + their contextual warnings. Same
                        // conditions as portrait: debug warning only when checked; the whole SD
                        // block only when sdTarget != null, its warning only when checked.
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                        ) {
                            Text(
                                text = "OPTIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { debugLog = !debugLog }
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = debugLog,
                                    onCheckedChange = { debugLog = it },
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Log debug session",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = cs.onSurface,
                                    )
                                    Text(
                                        text = "Writes a detailed log to help diagnose download problems.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                    if (debugLog) {
                                        Spacer(Modifier.height(6.dp))
                                        DownloadDialogWarning(
                                            text = "⚠️ Don't post this log publicly. Share it only directly " +
                                                "with the developer or someone you trust — unless you're " +
                                                "debugging it yourself.",
                                        )
                                    }
                                }
                            }

                            if (sdTarget != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { installToSd = !installToSd }
                                        .padding(vertical = 4.dp),
                                ) {
                                    Checkbox(
                                        checked = installToSd,
                                        onCheckedChange = { installToSd = it },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Install to SD card (frees internal space)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurface,
                                        )
                                        Text(
                                            text = "${sdTarget.label} · ${SteamSdInstall.fmtBytes(sdTarget.freeBytes)} free",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = cs.onSurfaceVariant,
                                        )
                                        if (installToSd) {
                                            Spacer(Modifier.height(6.dp))
                                            DownloadDialogWarning(
                                                text = "⚠️ The SD card is slower than internal storage. Some " +
                                                    "games stall on intro movies or asset loads from the card — " +
                                                    "if that happens, open the shortcut and use \"Copy game to " +
                                                    "Drive C\" to move it onto fast internal storage.",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Pinned action bar — OUTSIDE the scroll, so Cancel/Download never clip. ──
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(cs.outline.copy(alpha = 0.20f)),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                onDownload(options[selected].second, debugLog, installToSd)
                            },
                        ) { Text("Download") }
                    }
                }
            }
        }
        return
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Download speed", modifier = Modifier.weight(1f))
                IconButton(onClick = { helpRes = R.string.help_download_speed }) {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = "What is this?",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        text = {
            Column {
                options.forEachIndexed { index, (label, tier) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = index }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = selected == index,
                            onClick = { selected = index },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // Per-tier "?" — its own onClick opens help without changing selection.
                        IconButton(onClick = { helpRes = tierHelpRes(tier) }) {
                            Icon(
                                Icons.Default.Help,
                                contentDescription = "What is this?",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Verbose diagnostics toggle — off by default; writes a detailed steam_debug.txt
                // for THIS download only. Failures are always traced to logcat regardless.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { debugLog = !debugLog }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = debugLog,
                        onCheckedChange = { debugLog = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Log debug session",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Writes a detailed log to help diagnose download problems.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Shown only when the box is ticked. The log is scrubbed of credentials, but is a
                // diagnostic file — steer users away from posting it in public.
                if (debugLog) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "⚠️ Don't post this log publicly. Share it only directly with the " +
                            "developer or someone you trust — unless you're debugging it yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    )
                }

                // SD-card install toggle — only when a removable card is present. Internal is the fast
                // default; the card frees internal space but is FUSE-backed and slower, so it's an
                // explicit opt-in with a one-time warning (and the Copy-to-Drive-C escape hatch).
                if (sdTarget != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { installToSd = !installToSd }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = installToSd,
                            onCheckedChange = { installToSd = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Install to SD card (frees internal space)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${sdTarget.label} · ${SteamSdInstall.fmtBytes(sdTarget.freeBytes)} free",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (installToSd) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "⚠️ The SD card is slower than internal storage. Some games stall on " +
                                "intro movies or asset loads from the card — if that happens, open the " +
                                "shortcut and use \"Copy game to Drive C\" to move it onto fast internal " +
                                "storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(options[selected].second, debugLog, installToSd) }) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** 4-segment RAM/CPU load meter used by the landscape [DownloadSpeedPickerDialog] tiers —
 *  [filled] of 4 boxes lit (Slow=1 … Blazing=4). Display-only. */
@Composable
private fun SpeedLoadMeter(filled: Int) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < filled) cs.primary else cs.surfaceVariant),
            )
        }
    }
}

/** Inline red ⚠️ warning box used by the landscape [DownloadSpeedPickerDialog] options column —
 *  same wording as portrait, boxed to read compactly beside its checkbox. */
@Composable
private fun DownloadDialogWarning(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cs.error.copy(alpha = 0.12f))
            .border(1.dp, cs.error.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = cs.error,
        )
    }
}

@Composable
private fun ExePickerDialogGame(
    gameName: String,
    candidates: List<String>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select executable for \"$gameName\"") },
        text = {
            // HL2 (and many Source games) ship dozens of bin/*.exe SDK tools, so this list can be
            // long — bound its height and make it scrollable, or the real game exe is unreachable.
            // Cap at ~half the CURRENT screen height so it fits + scrolls in both portrait and the
            // much shorter landscape (a fixed dp cap could overflow a short landscape dialog).
            val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
            Column(
                modifier = Modifier
                    .heightIn(max = maxListHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                candidates.forEach { path ->
                    val f = java.io.File(path)
                    val parent = f.parentFile
                    val label = if (parent != null) "${parent.name}/${f.name}" else f.name
                    TextButton(
                        onClick = { onSelected(path) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label, modifier = Modifier.weight(1f)) }
                }
            }
        },
        confirmButton = {},
    )
}
