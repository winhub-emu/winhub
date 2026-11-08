package com.winhub.hernandez.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.winhub.hernandez.store.AmazonLaunchHelper
import com.winhub.hernandez.store.SteamAchievement
import com.winhub.hernandez.store.SteamAchievementStore
import com.winhub.hernandez.store.SteamDatabase
import com.winhub.hernandez.store.SteamDepotDownloader
import com.winhub.hernandez.store.SteamForegroundService
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.SteamRepository
import com.winhub.hernandez.store.SteamSdInstall
import com.winhub.hernandez.store.SteamStoreSearch
import com.winhub.hernandez.store.StoreDownloadTier
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadScope
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.formatDownloadSize
import com.winhub.hernandez.store.download.formatDownloadSpeed
import com.winhub.hernandez.store.download.formatEta
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a Deck Steam card hands to [DeckSteamInstall]: the app plus display strings. */
internal data class DeckSteamTarget(val appId: Int, val title: String, val art: String?)

/**
 * In-Deck Steam install. Starts the REAL [SteamDepotDownloader] (depot/manifest/credential work
 * all stay internal) and reads live progress straight from the shared [DownloadRegistry] StateFlow
 * the downloader already writes. Install opens a Deck options dialog — speed tier (the shared
 * [StoreDownloadTier], so the store settings row and this picker are one setting) and SD-card
 * target when [SteamSdInstall.detect] finds one — then the running screen offers Pause/Resume
 * (the same pause handle + [SteamDepotDownloader.resumeApp] pair the legacy detail page uses) and
 * a status line with MB/GB, speed and ETA. A title that is not in the library (or a signed-out
 * session) shows "Open in Steam" instead of a doomed Install.
 */
@Composable
internal fun DeckSteamInstall(target: DeckSteamTarget, onBack: () -> Unit, onOpenStore: (() -> Unit)? = null) {
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val main = remember { Handler(Looper.getMainLooper()) }
    val entries by DownloadRegistry.entries.collectAsState()
    val row = entries.firstOrNull { it.key == "${Store.STEAM}:${target.appId}" }
    var starting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var handle by remember { mutableStateOf<SteamDepotDownloader.DownloadControl?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var speedTier by remember { mutableStateOf(StoreDownloadTier.get(context)) }
    var sdTarget by remember { mutableStateOf<SteamSdInstall.SdTarget?>(null) }
    var sdEnabled by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var notReadyMsg by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<SteamStoreSearch.SteamGameDetails?>(null) }
    var achievements by remember { mutableStateOf<List<SteamAchievement>?>(null) }
    var dbGame by remember { mutableStateOf<SteamDatabase.GameRow?>(null) }
    val installed = dbGame?.isInstalled == true

    val active = row != null && (
        row.state == DownloadState.DOWNLOADING ||
            row.state == DownloadState.QUEUED ||
            row.state == DownloadState.PAUSED
        )
    val started = starting || active
    val finished = row?.state == DownloadState.INSTALLED
    val rowError =
        if (row?.state == DownloadState.FAILED) row.error ?: "Download failed" else null
    val error = if (started) null else (errorText ?: notReadyMsg ?: rowError)
    val canInstall = !checking && notReadyMsg == null

    // Bytes the status line reports: compressed fetched over the network when the row knows it,
    // else the uncompressed bytes written to disk — the same MB/GB the manager shows.
    val bytePart = when {
        row == null -> ""
        row.downloadTotal > 0 ->
            " - ${formatDownloadSize(row.downloadDone)} / ${formatDownloadSize(row.downloadTotal)}"
        row.installTotal > 0 ->
            " - ${formatDownloadSize(row.installDone)} / ${formatDownloadSize(row.installTotal)}"
        else -> ""
    }
    val status = when {
        row == null -> "Starting..."
        row.state == DownloadState.QUEUED -> "Queued...$bytePart"
        row.state == DownloadState.PAUSED -> "Paused$bytePart"
        else -> {
            val sp = if (row.speedBps > 0) " - ${formatDownloadSpeed(row.speedBps)}" else ""
            val et = if (row.etaSeconds >= 0) " - ${formatEta(row.etaSeconds)}" else ""
            "${row.pct}%$bytePart$sp$et"
        }
    }

    // A live registry row supersedes the local "starting" flag (it also clears it after a
    // pre-row precondition error is retried once the row appears).
    LaunchedEffect(row != null) {
        if (row != null) starting = false
    }

    // Library gate, off the main thread: signed out / not owned are answers the screen must give
    // before the user taps Install. A failed check never blocks (null): the engine re-checks anyway.
    LaunchedEffect(target.appId) {
        val msg = withContext(Dispatchers.IO) {
            try {
                val repo = SteamRepository.getInstance()
                when {
                    !repo.isLoggedIn -> "Steam not signed in"
                    repo.database.getGame(target.appId) == null -> "Not in your library yet."
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }
        }
        checking = false
        notReadyMsg = msg
    }

    // Store facts (description / release year) and Steam achievements — one IO pass per page.
    LaunchedEffect(target.appId) {
        val loaded = withContext(Dispatchers.IO) {
            val row = runCatching {
                SteamRepository.getInstance().database.getGame(target.appId)
            }.getOrNull()
            val info = runCatching { SteamStoreSearch.fetchDetails(target.appId) }.getOrNull()
            val ach = runCatching {
                if (!SteamRepository.getInstance().isLoggedIn) emptyList()
                else SteamAchievementStore.fetch(context, target.appId)
                    .ifEmpty { SteamAchievementStore.cached(context, target.appId) }
            }.getOrDefault(emptyList())
            Triple(row, info, ach)
        }
        dbGame = loaded.first
        details = loaded.second
        achievements = loaded.third
    }

    // A finished download flips the DB row to installed; refresh so the Add Game button agrees.
    LaunchedEffect(finished) {
        if (finished) {
            dbGame = withContext(Dispatchers.IO) {
                runCatching { SteamRepository.getInstance().database.getGame(target.appId) }.getOrNull()
            }
        }
    }

    // SD-card detection is a volume walk — keep it off the main thread, once per screen.
    LaunchedEffect(Unit) {
        sdTarget = withContext(Dispatchers.IO) {
            runCatching { SteamSdInstall.detect(context) }.getOrNull()
        }
    }

    // The real start: chosen tier + optional SD root, persisted so the settings row agrees.
    val start: () -> Unit = {
        errorText = null
        starting = true
        pickerOpen = false
        val tier = speedTier
        val sd = sdTarget
        val root = if (sdEnabled && sd != null) sd.steamGamesBase.path else null
        StoreDownloadTier.set(appCtx, tier)
        DownloadScope.io.launch {
            try {
                DownloadRegistry.init(appCtx)
                if (SteamPrefs.isLoggedIn) SteamForegroundService.start(appCtx)
                val repo = SteamRepository.getInstance()
                val err = when {
                    !repo.isLoggedIn -> "Steam not signed in"
                    repo.database.getGame(target.appId) == null -> "Game not in your library yet"
                    else -> null
                }
                if (err != null) {
                    main.post { starting = false; errorText = err }
                    return@launch
                }
                val control = SteamDepotDownloader.installApp(
                    target.appId, appCtx, speedTier = tier, installRoot = root,
                )
                main.post { handle = control }
            } catch (t: Throwable) {
                main.post { starting = false; errorText = t.message ?: "Failed to start download" }
            }
        }
    }

    // Pause: the held control when this screen started the download (or resumed it), else the
    // registry's own handle. Resume: gated on the DB row being DL_PAUSED exactly like the legacy
    // detail page, so a double-resume can never spawn a second download.
    val pauseAction: (() -> Unit)? = if (row != null && row.supportsPause) {
        {
            val h = handle
            when {
                // PAUSED first: a held control can only pause (its CAS is already set) — resume
                // must go through the DB-gated resumeApp, same as the legacy detail page.
                row.state == DownloadState.PAUSED -> {
                    DownloadScope.io.launch {
                        try {
                            val dlRow = SteamRepository.getInstance().database.getDownload(target.appId)
                            if (dlRow != null && dlRow.status == SteamDatabase.DL_PAUSED) {
                                val c = SteamDepotDownloader.resumeApp(
                                    target.appId, appCtx, StoreDownloadTier.get(appCtx),
                                )
                                main.post { handle = c }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                h != null -> {
                    h.pause.run()
                    handle = null
                }
                else -> row.pause?.invoke()
            }
        }
    } else {
        null
    }

    if (pickerOpen) {
        Dialog(onDismissRequest = { pickerOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Deck.surface)
                    .border(1.dp, Deck.line, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                Text(
                    text = "Install options",
                    color = Deck.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.size(10.dp))
                DeckChoiceRow(
                    label = "Download speed",
                    hint = "Ceiling the download may ramp to",
                    options = StoreDownloadTier.ALL.map { t -> t.toString() to StoreDownloadTier.label(t) },
                    selected = speedTier.toString(),
                    onPick = { v -> v.toIntOrNull()?.let { speedTier = it } },
                )
                val sd = sdTarget
                if (sd != null) {
                    DeckRow(
                        label = "Install to SD card",
                        hint = "${sd.label} - ${SteamSdInstall.fmtBytes(sd.freeBytes)} free",
                        control = { DeckToggle(sdEnabled) { sdEnabled = it } },
                    )
                }
                Spacer(Modifier.size(14.dp))
                DeckButton("Install", primary = true) { start() }
                Spacer(Modifier.size(8.dp))
                DeckButton("Cancel", primary = false) { pickerOpen = false }
            }
        }
    }

    DeckInstallShell(
        title = target.title,
        art = target.art,
        dev = dbGame?.developer ?: "",
        onBack = onBack,
        progress = row?.pct ?: 0,
        status = status,
        finished = finished,
        error = error,
        started = started,
        canInstall = canInstall,
        onPauseResume = pauseAction,
        paused = row?.state == DownloadState.PAUSED,
        onOpenStore = if (!checking && (notReadyMsg != null || errorText != null)) onOpenStore else null,
        storeLabel = "Steam",
        description = details?.shortDescription ?: "",
        releaseDate = details?.releaseYear ?: "",
        achievements = achievements,
        achievementsLoading = achievements == null,
        installed = installed,
        addGame = DeckAddRequest(
            title = target.title,
            store = "Steam",
            coverUrl = "https://shared.steamstatic.com/store_item_assets/steam/apps/" +
                "${target.appId}/library_600x900.jpg",
            appId = target.appId,
            resolveExe = {
                val game = runCatching {
                    SteamRepository.getInstance().database.getGame(target.appId)
                }.getOrNull()
                if (game == null || !game.isInstalled || game.installDir.isBlank()) {
                    null
                } else {
                    val files = mutableListOf<File>()
                    AmazonLaunchHelper.collectExe(File(game.installDir), files)
                    val lower = game.name.lowercase()
                    files.sortWith { a, b ->
                        AmazonLaunchHelper.scoreExe(b, lower) - AmazonLaunchHelper.scoreExe(a, lower)
                    }
                    files.firstOrNull()?.absolutePath
                }
            },
        ),
        onStart = { pickerOpen = true },
        onCancel = {
            val h = handle
            if (h != null) {
                h.cancel.run()
                handle = null
            } else {
                row?.cancel?.invoke()
            }
            starting = false
        },
    )
}
