package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AssistChip
import com.winhub.hernandez.ui.components.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.util.InAppFilePicker
import com.winhub.hernandez.core.ExitReasonReporter
import com.winhub.hernandez.core.LogInventory
import com.winhub.hernandez.core.LogLocation
import com.winhub.hernandez.core.LogcatCapture
import com.winhub.hernandez.ui.theme.DangerRed
import kotlinx.coroutines.launch
import java.io.File

/**
 * App Settings → Log Manager. One place for everything logging: where logs go, which ones are
 * produced, how many past runs are kept, and what is on disk right now per game.
 *
 * Mirrors the Performance screen deliberately — same card layout, same always-live "?" help button
 * on every toggle. Here the help copy leads with the PERFORMANCE COST, because two of these
 * genuinely slow games down and users need to know that before leaving one on.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LogManagerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var perGame by remember { mutableStateOf(prefs.getBoolean(LogLocation.PREF_PER_GAME, true)) }
    var keepLast by remember {
        mutableStateOf(prefs.getInt(LogLocation.PREF_KEEP_LAST, LogLocation.DEFAULT_KEEP_LAST))
    }
    var wineDebug by remember { mutableStateOf(prefs.getBoolean("enable_wine_debug", false)) }
    var box64Logs by remember { mutableStateOf(prefs.getBoolean("enable_box64_logs", false)) }
    var dxvkLogs by remember { mutableStateOf(prefs.getBoolean("enable_dxvk_logs", true)) }
    var controllerLogs by remember {
        mutableStateOf(prefs.getBoolean(com.winhub.hernandez.core.ControllerLog.PREF_KEY, false))
    }
    var steamLite by remember { mutableStateOf(prefs.getBoolean("enable_steamlite_logs", false)) }
    var logcat by remember { mutableStateOf(prefs.getBoolean("enable_logcat", true)) }
    var crashReports by remember { mutableStateOf(prefs.getBoolean("enable_crash_reports", true)) }
    var exitAutosave by remember { mutableStateOf(prefs.getBoolean(ExitReasonReporter.PREF_AUTOSAVE, false)) }
    var rustSteamEngine by remember {
        mutableStateOf(prefs.getBoolean(com.winhub.hernandez.store.blsteam.BlSteamEngineFlag.PREF_KEY,
            com.winhub.hernandez.store.blsteam.BlSteamEngineFlag.DEFAULT))
    }
    var rustEpicEngine by remember {
        mutableStateOf(prefs.getBoolean(com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.PREF_KEY_EPIC,
            com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.DEFAULT))
    }
    var rustGogEngine by remember {
        mutableStateOf(prefs.getBoolean(com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.PREF_KEY_GOG,
            com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.DEFAULT))
    }
    var rustAmazonEngine by remember {
        mutableStateOf(prefs.getBoolean(com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.PREF_KEY_AMAZON,
            com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.DEFAULT))
    }

    // Location + channels moved here from the old Settings › Logs section, which this screen
    // replaces. They used to be saved by the Settings "Save" FAB; here every change is written
    // immediately, matching the Performance screen and removing the risk of two screens holding
    // the same preference and one overwriting the other on save.
    var locationMode by remember {
        mutableStateOf(prefs.getString(LogLocation.PREF_MODE, LogLocation.MODE_DOCUMENTS) ?: LogLocation.MODE_DOCUMENTS)
    }
    var customPath by remember {
        mutableStateOf(prefs.getString(LogLocation.PREF_CUSTOM_PATH, "") ?: "")
    }
    var showLocationMenu by remember { mutableStateOf(false) }
    var channels by remember {
        mutableStateOf(
            (prefs.getString("wine_debug_channels", com.winhub.hernandez.SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS)
                ?: com.winhub.hernandez.SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS)
                .split(",").filter { it.isNotBlank() }
        )
    }

    // Bumped after any destructive/refreshing action to force a re-scan of the filesystem.
    // Declared before saveMode() below, which increments it — a Kotlin local function can only
    // capture locals declared above it.
    var refreshTick by remember { mutableStateOf(0) }

    fun saveMode(mode: String) {
        locationMode = mode
        prefs.edit().putString(LogLocation.PREF_MODE, mode).apply()
        refreshTick++
    }
    fun saveChannels(list: List<String>) {
        channels = list
        prefs.edit().putString("wine_debug_channels", list.joinToString(",")).apply()
    }

    val dirLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            InAppFilePicker.pickedPath(result.data)?.let { path ->
                customPath = path
                prefs.edit().putString(LogLocation.PREF_CUSTOM_PATH, path).apply()
                saveMode(LogLocation.MODE_CUSTOM)
            }
        }
    }

    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var exitReport by remember { mutableStateOf<String?>(null) }
    var showExplainAll by remember { mutableStateOf(false) }
    var showKeepMenu by remember { mutableStateOf(false) }
    // Folder the embedded File Manager is showing, or null when it is closed.
    var browseDir by remember { mutableStateOf<File?>(null) }
    // Group open in the log viewer, and the two destructive confirmations.
    var viewing by remember { mutableStateOf<LogInventory.Entry?>(null) }
    var confirmDelete by remember { mutableStateOf<LogInventory.Entry?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val entries = remember(refreshTick, perGame) { LogInventory.scan(context) }

    fun putBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    var storeDlTier by remember { mutableStateOf(com.winhub.hernandez.store.StoreDownloadTier.get(context)) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Log Manager",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // ── Where logs go ────────────────────────────────────────────
            SectionLabel("Where logs go")
            LogCard {
                PickRow(
                    label = when (locationMode) {
                        LogLocation.MODE_DOWNLOAD -> "Download"
                        LogLocation.MODE_CUSTOM -> if (customPath.isNotEmpty()) "Custom folder" else "Choose folder…"
                        // MODE_DOCUMENTS (default) and the retired MODE_APP_DATA both resolve to Documents.
                        else -> "Documents"
                    },
                    sub = LogLocation.resolveLogDir(context)?.absolutePath ?: "—",
                    action = "Change",
                    onClick = { showLocationMenu = true }
                )
                Box {
                    // Same card, leading icons and dividers as the File Manager's drive menu —
                    // one menu style across the app, from the shared MenuStyle helpers.
                    DropdownMenu(
                        expanded = showLocationMenu,
                        onDismissRequest = { showLocationMenu = false },
                        modifier = Modifier.outlinedMenuCard(),
                    ) {
                        MenuRow("Download", Icons.Default.Download) {
                            saveMode(LogLocation.MODE_DOWNLOAD); showLocationMenu = false
                        }
                        MenuItemDivider()
                        MenuRow("Documents", Icons.Default.Description) {
                            saveMode(LogLocation.MODE_DOCUMENTS); showLocationMenu = false
                        }
                        MenuItemDivider()
                        MenuRow("Choose folder…", Icons.Default.FolderOpen) {
                            showLocationMenu = false
                            dirLauncher.launch(InAppFilePicker.buildDirIntent(context, "Select log folder"))
                        }
                    }
                }
                LogToggle("Folder for each game", perGame,
                    hint = "Each game keeps its own folder",
                    onInfo = { info = "Folder for each game" to LogCopy.PER_GAME }) {
                    perGame = it; putBool(LogLocation.PREF_PER_GAME, it); refreshTick++
                }
            }

            // ── What to record ───────────────────────────────────────────
            SectionLabel("What to record")
            LogCard {
                LogToggle("Wine debug", wineDebug,
                    hint = "Slows games down — for diagnosing a problem",
                    onInfo = { info = "Wine debug" to LogCopy.WINE }) {
                    wineDebug = it; putBool("enable_wine_debug", it)
                }
                if (wineDebug) {
                    WineChannelGroup(
                        selected = channels,
                        onChange = { saveChannels(it) },
                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                    )
                }
                LogToggle("Box64 / FEXCore", box64Logs,
                    hint = "Slows games down at higher verbosity",
                    onInfo = { info = "Box64 / FEXCore" to LogCopy.BOX64 }) {
                    box64Logs = it; putBool("enable_box64_logs", it)
                }
                LogToggle("DXVK & VKD3D", dxvkLogs,
                    hint = "Safe to leave on — mostly written at startup",
                    onInfo = { info = "DXVK & VKD3D" to LogCopy.DXVK }) {
                    dxvkLogs = it; putBool("enable_dxvk_logs", it)
                }
                LogToggle("Controller input", controllerLogs,
                    hint = "For controls that don't work — written when the game exits",
                    onInfo = { info = "Controller input" to LogCopy.CONTROLLER }) {
                    controllerLogs = it; putBool(com.winhub.hernandez.core.ControllerLog.PREF_KEY, it)
                }
                LogToggle("SteamLite (Steam client)", steamLite,
                    hint = "Only when a game launches through SteamLite",
                    onInfo = { info = "SteamLite (Steam client)" to LogCopy.STEAMLITE }) {
                    steamLite = it; putBool("enable_steamlite_logs", it)
                }
                LogToggle("Android logcat", logcat,
                    hint = "WinHub's own output only",
                    onInfo = { info = "Android logcat" to LogCopy.LOGCAT }) {
                    logcat = it; putBool("enable_logcat", it)
                }
                LogToggle("Crash reports", crashReports,
                    hint = "Nothing runs until something crashes",
                    onInfo = { info = "Crash reports" to LogCopy.CRASH }) {
                    crashReports = it; putBool("enable_crash_reports", it)
                }
                // Exit reasons: reads Android's own record of why the process last died — the only way
                // to see a NATIVE crash (which dies in another process and never reaches logcat or the
                // Java crash reporter) on an unrooted device. Toggle only auto-saves on launch; the
                // system keeps the record either way, so the manual button below works even when off.
                LogToggle(
                    if (ExitReasonReporter.isSupported()) "Exit reasons (auto-save on launch)"
                    else "Exit reasons (needs Android 11+)",
                    exitAutosave,
                    hint = "Records native crashes without root",
                    enabled = ExitReasonReporter.isSupported(),
                    onInfo = { info = "Exit reasons" to LogCopy.EXIT_REASONS }) {
                    exitAutosave = it; putBool(ExitReasonReporter.PREF_AUTOSAVE, it)
                }
                // Developer switch for the native Rust Steam engine (docs/STEAM_RUST_ENGINE_PLAN.md).
                // Read once per process by SteamRepository.initialize(), so it takes effect on the
                // next app start. OFF = JavaSteam as today; ON = the full Steam stack (auth, session,
                // library, downloads, cloud, achievements, social, presence) runs on libblsteam.so and
                // the engine's steam_engine.txt is folded into the SteamLite log bundle (Phase 3b-4).
                LogToggle("Native Steam engine (restart required)", rustSteamEngine,
                    hint = "On by default — the whole Steam session (sign-in, library, downloads, cloud, achievements, friends) runs on the native engine; its log joins the SteamLite bundle. Off = legacy Java engine",
                    onInfo = { info = "Rust Steam engine" to LogCopy.RUST_ENGINE }) {
                    rustSteamEngine = it
                    putBool(com.winhub.hernandez.store.blsteam.BlSteamEngineFlag.PREF_KEY, it)
                }
                // Per-store switches for the native download engines (docs/RUST_STORE_ENGINES.md).
                // Read at download start by each store's Java manager, so a flip applies to the next
                // download — no restart. OFF = that manager's existing Java fetch loop.
                LogToggle("Rust engine: Epic downloads", rustEpicEngine,
                    hint = "On by default — Epic file chunks are fetched by the native engine; takes effect on the next download. Off = Java downloader",
                    onInfo = { info = "Rust store engines" to LogCopy.RUST_STORE_ENGINES }) {
                    rustEpicEngine = it
                    putBool(com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.PREF_KEY_EPIC, it)
                }
                LogToggle("Rust engine: GOG downloads", rustGogEngine,
                    hint = "On by default — GOG depot chunks are fetched by the native engine; takes effect on the next download. Off = Java downloader",
                    onInfo = { info = "Rust store engines" to LogCopy.RUST_STORE_ENGINES }) {
                    rustGogEngine = it
                    putBool(com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.PREF_KEY_GOG, it)
                }
                LogToggle("Rust engine: Amazon downloads", rustAmazonEngine,
                    hint = "On by default — Amazon Games files are fetched by the native engine; takes effect on the next download. Off = Java downloader",
                    onInfo = { info = "Rust store engines" to LogCopy.RUST_STORE_ENGINES }) {
                    rustAmazonEngine = it
                    putBool(com.winhub.hernandez.store.blsteam.BlStoreEngineFlag.PREF_KEY_AMAZON, it)
                }
                // One app-wide speed tier for the three store engines (Steam keeps its per-download
                // picker). Tap cycles Slow → Medium → Fast → Blazing; it is only the ceiling the
                // adaptive window may ramp to, so a weak link still settles below it. Next download.
                Spacer(Modifier.height(4.dp))
                PickRow(
                    "Store download speed",
                    "Ceiling for the Rust Epic / GOG / Amazon engines. Tap to change; applies to the next download.",
                    action = com.winhub.hernandez.store.StoreDownloadTier.label(storeDlTier),
                    onInfo = { info = "Rust store engines" to LogCopy.RUST_STORE_ENGINES },
                ) {
                    storeDlTier = com.winhub.hernandez.store.StoreDownloadTier.next(storeDlTier)
                    com.winhub.hernandez.store.StoreDownloadTier.set(context, storeDlTier)
                }

                // Outlined rather than a filled button: the design keeps solid accent for switches
                // only, and this is an occasional action, not the point of the screen.
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CardAction(
                        "Capture logcat now",
                        modifier = Modifier.weight(1f).alpha(if (logcat) 1f else 0.4f)
                    ) {
                        if (!logcat) return@CardAction
                        // Runtime.exec + up to 10000 lines + a redaction pass + a file write: far too
                        // much for the UI thread (its own docs say so). Off to IO, refresh after.
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                LogcatCapture.captureToFile(context, LogcatCapture.DEFAULT_LINES)
                            }
                            refreshTick++
                        }
                    }
                    InfoDot { info = "Capture logcat now" to LogCopy.CAPTURE_NOW }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CardAction(
                        "Read last crash",
                        modifier = Modifier.weight(1f).alpha(if (ExitReasonReporter.isSupported()) 1f else 0.4f)
                    ) {
                        if (!ExitReasonReporter.isSupported()) return@CardAction
                        // Reads a system stream + writes a file — off the main thread. We show the text
                        // AND save it to the exit-reasons folder so it can be shared like any other log.
                        scope.launch {
                            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                ExitReasonReporter.captureToFile(context)
                                ExitReasonReporter.capture(context)
                            }
                            exitReport = text
                            refreshTick++
                        }
                    }
                    InfoDot { info = "Exit reasons" to LogCopy.EXIT_REASONS }
                }
            }

            // ── Housekeeping ─────────────────────────────────────────────
            SectionLabel("Housekeeping")
            LogCard {
                Box {
                    PickRow(
                        label = "Keep last",
                        sub = if (keepLast == 0) "No history — each run replaces the last"
                              else "$keepLast launch${if (keepLast == 1) "" else "es"} per game",
                        action = "▾",
                        onInfo = { info = "Keep last launches" to LogCopy.KEEP_LAST },
                        onClick = { showKeepMenu = true }
                    )
                    DropdownMenu(
                        expanded = showKeepMenu,
                        onDismissRequest = { showKeepMenu = false },
                        modifier = Modifier.outlinedMenuCard(),
                    ) {
                        listOf(0, 1, 3, 5, 10, 20, 50).forEachIndexed { i, n ->
                            if (i > 0) MenuItemDivider()
                            MenuRow(
                                if (n == 0) "No history" else "$n launch${if (n == 1) "" else "es"}",
                                if (n == 0) Icons.Default.HistoryToggleOff else Icons.Default.History
                            ) {
                                keepLast = n
                                prefs.edit().putInt(LogLocation.PREF_KEEP_LAST, n).apply()
                                showKeepMenu = false
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total size", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Text(
                            LogInventory.humanBytes(LogInventory.totalBytes(entries)) +
                                " across ${entries.size} " + if (entries.size == 1) "folder" else "folders",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                        )
                    }
                    Text("Browse", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { browseDir = LogLocation.resolveLogDir(context) }
                            .padding(horizontal = 8.dp, vertical = 6.dp))
                    Text("Clear all", color = DangerRed, fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { if (entries.isNotEmpty()) confirmClearAll = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .alpha(if (entries.isEmpty()) 0.4f else 1f))
                }
            }

            // ── Logs by game ─────────────────────────────────────────────
            SectionLabel("Logs by game")
            if (entries.isEmpty()) {
                LogCard {
                    Text("No logs yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            } else {
                entries.forEach { e ->
                    GameLogCard(
                        entry = e,
                        onView = { viewing = e },
                        onShare = { shareLogGroup(context, e) },
                        onDelete = { confirmDelete = e }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Explain the log types",
                color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                modifier = Modifier
                    .clickable { showExplainAll = true }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            )
            Text(
                // Third copy of this claim, and the one that overstated it most — "safe to share"
                // full stop. Same correction as LogReport and LogViewerScreen: name what is actually
                // stripped, and say plainly that paths are kept, because a path is often the thing
                // that makes a log worth having.
                "E-mail addresses, auth tokens and your Steam account name are scrubbed as logs are " +
                    "written. File paths are kept so they stay useful for debugging — glance over " +
                    "them before sharing if a folder name identifies you. Logcat only ever contains " +
                    "WinHub's own output.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // The File Manager, opened at a log folder. This host block is what "Browse" needs to do
    // anything at all — without it browseDir is written and never read, which is exactly how the
    // button ended up dead. It also re-scans on close, since the File Manager can delete or move.
    browseDir?.let { dir ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { browseDir = null; refreshTick++ },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // A Dialog window is transparent and the File Manager draws no background of its own —
            // without this Surface it renders on top of whatever is behind the dialog.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                FileManagerScreen(initialDir = dir)
            }
        }
    }

    viewing?.let { entry ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewing = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LogViewerScreen(entry = entry, onClose = { viewing = null })
            }
        }
    }

    exitReport?.let { report ->
        OutlinedAlertDialog(
            onDismissRequest = { exitReport = null },
            title = { Text("Last crash / exit reasons") },
            text = {
                Column(modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Saved with your logs under the \"" + ExitReasonReporter.FOLDER +
                            "\" folder. Exit #0 is the most recent; a NATIVE_CRASH entry includes the " +
                            "signal and the top backtrace frames.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        report,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = { TextButton(onClick = { exitReport = null }) { Text("Close") } }
        )
    }

    confirmDelete?.let { entry ->
        val count = remember(entry.dir) { LogInventory.deletableCount(entry) }
        OutlinedAlertDialog(
            onDismissRequest = { confirmDelete = null },
            // The loose bucket is already named "Older logs"/"All logs", so "Delete ${name} logs?"
            // read "Delete Older logs logs?". Only a game name needs the word appending.
            title = {
                Text(
                    when {
                        entry.isAppBucket -> "Delete app & crash logs?"
                        entry.isLooseBucket -> "Delete ${entry.name.replaceFirstChar { it.lowercase() }}?"
                        else -> "Delete ${entry.name} logs?"
                    }
                )
            },
            text = {
                Text(
                    if (count == 0)
                    // Reachable: the loose bucket counts every log-shaped file it finds, but only
                    // the ones we wrote are deletable. A folder holding nothing but a user's own
                    // files lands here, and must not imply we are about to touch them.
                        "Nothing here was written by WinHub, so there is nothing to delete. " +
                            "The files in this folder are yours and are left alone."
                    else
                        "Deletes $count log file${if (count == 1) "" else "s"}, including any kept " +
                            "history.\n\nOnly files WinHub wrote are removed — anything else in " +
                            "that folder is left alone."
                )
            },
            confirmButton = {
                if (count > 0) {
                    TextButton(onClick = {
                        val n = LogInventory.deleteGroup(context, entry)
                        confirmDelete = null
                        refreshTick++
                        android.widget.Toast.makeText(
                            context, "Deleted $n file${if (n == 1) "" else "s"}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Delete", color = DangerRed) }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(if (count == 0) "OK" else "Cancel")
                }
            }
        )
    }

    if (confirmClearAll) {
        val count = remember(refreshTick) { entries.sumOf { LogInventory.deletableCount(it) } }
        OutlinedAlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear all logs?") },
            text = {
                Text(
                    "Deletes $count log file${if (count == 1) "" else "s"} across ${entries.size} " +
                        "folder${if (entries.size == 1) "" else "s"}, including kept history.\n\n" +
                        "Only files WinHub wrote are removed. Other files in your log folder — " +
                        "including anything you put there yourself — are left alone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    var n = 0
                    entries.forEach { n += LogInventory.deleteGroup(context, it) }
                    confirmClearAll = false
                    refreshTick++
                    android.widget.Toast.makeText(
                        context, "Deleted $n file${if (n == 1) "" else "s"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }) { Text("Delete all", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") } }
        )
    }

    info?.let { (title, body) -> PerfInfoDialog(title = title, body = body, onDismiss = { info = null }) }
    if (showExplainAll) {
        PerfInfoDialog(title = "What each log is for", body = LogCopy.explainAll(),
            onDismiss = { showExplainAll = false })
    }
}

/** Small uppercase section heading that sits ABOVE its card, as in the design. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        letterSpacing = 0.08.em,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

/** Untitled card — the heading lives above it now. */
@Composable
private fun LogCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) { content() }
}

/** A row that reads as a value with its detail underneath and an action on the right. */
@Composable
private fun PickRow(
    label: String,
    sub: String,
    action: String,
    onInfo: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2)
        }
        if (onInfo != null) InfoDot(onInfo)
        Text(action, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
    }
}

/** A drive-menu-style row: leading icon tinted primary, label, whole row clickable. */
@Composable
private fun MenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        },
        onClick = onClick,
    )
}

/** The channels people actually turn on. The other 500 are behind the search field. */
private val COMMON_WINE_CHANNELS = listOf(
    "err", "warn", "fixme", "seh", "module", "loaddll", "process", "thread",
    "sync", "file", "reg", "heap", "ntdll", "d3d", "dxgi", "vulkan", "opengl", "relay"
)

/**
 * Wine debug channels, built like the DXVK_HUD group in the env-var editor: a disclosure header,
 * the selection shown as static labels while collapsed, and a FilterChip grid when open.
 *
 * One deliberate difference. DXVK_HUD has ~20 options and TU_DEBUG ~30, so those can show every
 * chip when expanded; Wine has **521**, which is a wall of chips nobody can read and a slow
 * composition besides. So the expanded grid shows the channels that actually get used, and a
 * search field reaches the rest — the same escape hatch the env-var editor's own add-picker uses
 * for its oversized catalog. Anything already selected is always shown, however obscure.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WineChannelGroup(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var browseAll by remember { mutableStateOf(false) }

    val all = remember {
        try {
            val arr = org.json.JSONArray(FileUtils.readString(context, "wine_debug_channels.json"))
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Selected first so a channel can always be switched back off, then the common set, then
    // whatever the query matches.
    val shown = remember(query, selected, all) {
        if (query.isBlank()) (selected + COMMON_WINE_CHANNELS).distinct()
        else all.filter { it.contains(query.trim(), ignoreCase = true) }.take(60)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
        ) {
            Text(
                "Channels",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                if (expanded) "Collapse channels" else "Expand channels",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        if (!expanded) {
            // Static labels, not chips: a tap that silently deselected a channel from a summary
            // view would be a destructive action hidden behind a collapsed control.
            if (selected.isEmpty()) {
                Text("None selected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp))
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    selected.forEach { ch ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(ch, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp)
                        }
                    }
                }
            }
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search all ${all.size} channels") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            shown.forEach { ch ->
                val isOn = ch in selected
                FilterChip(
                    selected = isOn,
                    onClick = { onChange(if (isOn) selected - ch else selected + ch) },
                    label = { Text(ch, fontSize = 11.sp) }
                )
            }
            // Last chip in the flow, so it reads as "…and the rest are through here" rather than
            // as another channel. Searching only helps once you know a name to type; this is the
            // way in for someone who doesn't.
            if (all.isNotEmpty()) {
                AssistChip(
                    onClick = { browseAll = true },
                    label = { Text("Browse all ${all.size}…", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.ListAlt, null, modifier = Modifier.size(14.dp))
                    },
                )
            }
        }
        if (query.isNotBlank() && shown.isEmpty()) {
            Text("No channel matches \"$query\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }

        Text(
            "Reset to defaults",
            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
            modifier = Modifier
                .clickable {
                    onChange(com.winhub.hernandez.SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS
                        .split(",").filter { it.isNotBlank() })
                    query = ""
                }
                .padding(top = 6.dp, bottom = 2.dp)
        )
    }

    if (browseAll) {
        AllChannelsDialog(
            all = all,
            selected = selected,
            onChange = onChange,
            onDismiss = { browseAll = false },
        )
    }
}

/**
 * Every Wine channel, browsable. The chip grid above deliberately shows only the ~18 people use, and
 * the search field only helps if you already know a name to type — this is the way in for someone
 * who doesn't know what exists.
 *
 * Grouped by family rather than listed alphabetically: 521 names in one column is technically "all
 * of them" and practically unreadable, whereas "Sound" holding 15 entries is something a user can
 * actually shop from. "What's this?" turns on a one-line explanation under every row, which is the
 * difference between a list of names and a reference — it is off by default because with it on each
 * row is three lines tall and scanning gets slower.
 */
@Composable
private fun AllChannelsDialog(
    all: List<String>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    var selectedOnly by remember { mutableStateOf(false) }

    // Grouping is pure string work over 521 items, so it is cheap — but it must not rerun on every
    // recomposition (every keystroke, every toggle), hence the key on the list identity alone.
    val grouped = remember(all) {
        all.groupBy { WineChannelInfo.categoryOf(it) }
            .toList()
            .sortedBy { (cat, _) ->
                WineChannelInfo.CATEGORY_ORDER.indexOf(cat)
                    .let { if (it < 0) WineChannelInfo.CATEGORY_ORDER.size else it }
            }
            // Within a category: the everyday ones first, then ones we can explain, then the rest
            // alphabetically. Plain alphabetical put `debug_buffer` at the top of "Errors and
            // tracing" and pushed err/warn/fixme/seh below the fold — the exact channels someone
            // opening this list came for. Sorting by "has a description" alone does not fix it,
            // because debug_buffer has one too; COMMON_WINE_CHANNELS is the actual priority order.
            // Deliberately NOT selected-first: rows would jump under the finger as you tick them.
            .map { (cat, names) ->
                cat to names.sortedWith(
                    compareBy<String> {
                        val i = COMMON_WINE_CHANNELS.indexOf(it)
                        if (i < 0) COMMON_WINE_CHANNELS.size else i
                    }
                        .thenByDescending { WineChannelInfo.hasDetail(it) }
                        .thenBy { it }
                )
            }
    }

    val visible = remember(grouped, query, selectedOnly, selected) {
        val q = query.trim()
        grouped.mapNotNull { (cat, names) ->
            val kept = names.filter { ch ->
                (q.isBlank() || ch.contains(q, ignoreCase = true) ||
                    cat.contains(q, ignoreCase = true)) &&
                    (!selectedOnly || ch in selected)
            }
            if (kept.isEmpty()) null else cat to kept
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            // Nearly full-bleed. This device is a landscape handheld, and at 0.9 height with the
            // title, search field and chips each on their own row the list got a single visible
            // row — the one part of the dialog that matters. The header is now one row and the
            // list takes everything left over.
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.95f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("All Wine channels", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${all.size} available · ${selected.size} on",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilterChip(
                        selected = showHelp,
                        onClick = { showHelp = !showHelp },
                        label = { Text("What's this?", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Outlined.HelpOutline, null, modifier = Modifier.size(14.dp))
                        },
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = selectedOnly,
                        onClick = { selectedOnly = !selectedOnly },
                        label = { Text("On only", fontSize = 11.sp) },
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search", fontSize = 12.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )

                if (visible.isEmpty()) {
                    Text(
                        if (selectedOnly && query.isBlank()) "No channels are on."
                        else "Nothing matches \"$query\".",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        visible.forEach { (category, names) ->
                            item(key = "hdr-$category") {
                                Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                                    Text(
                                        category.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    if (showHelp) {
                                        Text(
                                            WineChannelInfo.categoryBlurb(category),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            items(names, key = { "ch-$it" }) { ch ->
                                val isOn = ch in selected
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onChange(if (isOn) selected - ch else selected + ch)
                                        }
                                        .padding(vertical = 3.dp),
                                ) {
                                    Checkbox(
                                        checked = isOn,
                                        onCheckedChange = {
                                            onChange(if (isOn) selected - ch else selected + ch)
                                        },
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 2.dp)) {
                                        Text(
                                            ch,
                                            fontSize = 13.sp,
                                            color = if (isOn) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                        )
                                        if (showHelp) {
                                            Text(
                                                WineChannelInfo.describe(ch),
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * One game's logs: icon, name, what is on disk, then View / Share / Delete as in the design.
 * Tapping the name expands the file list.
 */
@Composable
private fun GameLogCard(
    entry: LogInventory.Entry,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        entry.isAppBucket -> Icons.Default.PhoneAndroid
                        // Loose files in the log root are not a game and must not look like one.
                        entry.isLooseBucket -> Icons.Default.History
                        entry.dir.name.startsWith("Container") -> Icons.Default.Settings
                        else -> Icons.Default.SportsEsports
                    },
                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text(if (entry.isAppBucket) "App & crash logs" else entry.name,
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                Text(
                    buildString {
                        append("${entry.fileCount} file${if (entry.fileCount == 1) "" else "s"}")
                        append(" · ").append(LogInventory.humanBytes(entry.totalBytes))
                        if (entry.archivedRuns > 0) append(" · ${entry.archivedRuns} kept")
                        append(" · ").append(relativeTime(entry.lastModified))
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            LogInventory.filesIn(entry.dir).forEach { f ->
                Text("• ${f.name}  (${LogInventory.humanBytes(f.length())})",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            }
        }
        // Divider then a three-up action row, as in the design.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp)
        ) {
            CardAction("View", modifier = Modifier.weight(1f), primary = true, onClick = onView)
            CardAction("Share", modifier = Modifier.weight(1f), onClick = onShare)
            CardAction("Delete", modifier = Modifier.weight(1f), danger = true, onClick = onDelete)
        }
    }
}

/** One of the three per-card actions. */
@Composable
private fun CardAction(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val fg = when {
        danger -> DangerRed
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(7.dp))
            .border(
                1.dp,
                if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(7.dp)
            )
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 12.sp)
    }
}

/** The design's one non-theme colour: destructive actions read as red in both light and dark. */

/** "12 min ago" / "yesterday" — a timestamp is not what anyone is looking for in this list. */
private fun relativeTime(millis: Long): String {
    if (millis <= 0) return "—"
    val mins = (System.currentTimeMillis() - millis) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} hr ago"
        mins < 60 * 48 -> "yesterday"
        else -> "${mins / (60 * 24)} days ago"
    }
}

@Composable
private fun LogToggle(
    label: String,
    checked: Boolean,
    hint: String? = null,
    enabled: Boolean = true,
    onInfo: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier.alpha(0.4f))
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            if (hint != null) {
                Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        if (onInfo != null) InfoDot(onInfo)
        Spacer(Modifier.width(4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Always-live "?" — a locked or off toggle must still be explainable. */
@Composable
private fun InfoDot(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Outlined.HelpOutline, "What's this?",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/**
 * Help copy. Every entry leads with the performance cost, because that is the thing a user needs to
 * decide with — two of these are genuinely expensive to leave on.
 */
private object LogCopy {
    const val PER_GAME =
        "No performance cost.\n\n" +
        "Gives each game its own folder inside your log location, named after the game, so one " +
        "game's logs don't get mixed up with another's. Turn it off to keep everything in a single " +
        "folder like older versions did.\n\n" +
        "Logs written before you turned this on are left exactly where they are."

    const val WINE =
        "⚠️ SLOWS GAMES DOWN. Only turn this on to diagnose a problem, then turn it back off.\n\n" +
        "Records what Wine itself is doing. Every message is written to disk as the game runs, and " +
        "Wine produces a lot of them — the \"seh\" channel alone logs an entry every time the game " +
        "raises an exception, which normal Windows programs do constantly. Adding heavier channels " +
        "such as \"relay\" can make a game many times slower.\n\n" +
        "This is the log to enable when a game won't start, crashes, or dies at launch."

    const val BOX64 =
        "⚠️ SLOWS GAMES DOWN at higher verbosity.\n\n" +
        "Records what the x86 translator (Box64 or FEXCore) is doing while it converts the game's " +
        "PC instructions to run on your phone. Useful for a game that crashes in a way Wine's own " +
        "log doesn't explain, or one that misbehaves only under one emulator.\n\n" +
        "Leave off for normal play."

    const val DXVK =
        "Little to no performance cost.\n\n" +
        "Records what the graphics translation layers report — DXVK for DirectX 9/10/11, VKD3D for " +
        "DirectX 12. Almost everything they write happens while the game is starting: which GPU and " +
        "driver were picked, which features are missing, why a shader failed.\n\n" +
        "This is the log to check for black screens, missing textures or a game that refuses a " +
        "graphics feature. Safe to leave on."

    const val CONTROLLER =
        "Little to no performance cost.\n\n" +
        "Records how the controller reached the game, for when the controls don't work. It covers " +
        "XInput games (Direct XInput and Wine's own XInput) and DirectInput games, plus vibration. " +
        "It notes the controller settings the game launched with, which controller went to which " +
        "player, every in-game change in Controls › Pad, and how much input the app sent and the game " +
        "actually read.\n\n" +
        "Everything lands in a single \"controller.txt\" next to that game's other logs, written when " +
        "the game exits. It opens with a checklist (PASS / FAIL / WARN) for each player, so you can see " +
        "where input stopped: the app got nothing from the controller, Wine never opened it, the game " +
        "never read it, or Direct XInput never loaded. Send that file when you report a controller " +
        "problem — play long enough to try the controls, then quit the game normally.\n\n" +
        "Wine games only; the Linux runtime keeps its own controller diagnostics. Nothing is recorded " +
        "while this is off."

    const val STEAMLITE =
        "Little to no performance cost.\n\n" +
        "Collects the REAL Steam client's own logs — the ones it writes for itself while a game runs " +
        "under SteamLite (the online / VAC launch path). That covers your login and network connection, " +
        "which game it launched, downloads, cloud saves and achievements. Steam writes these small files " +
        "as it goes, so leaving this on costs you essentially nothing.\n\n" +
        "It ONLY produces anything when a game is actually launched through SteamLite. A normal launch, " +
        "or a Goldberg (offline) launch, writes nothing at all — so there is no output to find unless you " +
        "played online through the real client.\n\n" +
        "Everything is gathered into a single \"steamlite.txt\" next to that game's other logs, and it " +
        "opens with a plain-English summary of what ran plus an auto-scan that flags common problems " +
        "(a VAC-insecure launch, a login taken over by another device, a cloud-save conflict) so you " +
        "don't have to read the raw logs.\n\n" +
        "Your Steam account name and auth tokens are scrubbed out as it is written, and your Steam ID is " +
        "partially masked — but a masked Steam ID may still remain, which is normal and safe to share."

    const val LOGCAT =
        "No performance cost.\n\n" +
        "Android's own system log, as it relates to WinHub. It is captured on demand — when you " +
        "tap \"Capture logcat now\", or when the app crashes — not continuously, so switching it on " +
        "does not cost you any frames.\n\n" +
        "It contains WINHUB'S OUTPUT ONLY. Android does not let an app read other apps' logs " +
        "without root, and we deliberately don't offer a system-wide capture even with root: it " +
        "would sweep up other apps' private data into a file you're likely to share. If you have " +
        "root and need the full system log, use a dedicated logcat reader."

    const val CRASH =
        "No performance cost — nothing runs until something actually crashes.\n\n" +
        "If WinHub itself crashes, a report is saved with your device model, Android version, " +
        "app version, what went wrong, and the last few hundred lines of the app's log. That is " +
        "exactly what's needed to work out a crash after the fact.\n\n" +
        "Reports are kept with your other logs under \"App & crash logs\"."

    const val CAPTURE_NOW =
        "Takes a snapshot of WinHub's recent Android log right now and saves it with your other " +
        "logs.\n\n" +
        "Useful when something went wrong but the app didn't crash — capture it while the problem is " +
        "fresh, then share it."

    const val EXIT_REASONS =
        "Reads Android's own record of why WinHub last shut down or crashed — no root needed.\n\n" +
        "This is the ONLY way to see a NATIVE crash on an unrooted device: those die in a separate " +
        "system process, so they never show up in the logcat capture or the Java crash report, and " +
        "the app just restarts looking fine. For a native crash this shows the signal (e.g. SIGSEGV) " +
        "and the top backtrace frames — the actual cause, not a hint.\n\n" +
        "The system keeps this record across the crash, so \"Read last crash\" works even if you only " +
        "turn this on afterwards. The toggle just controls whether a report is also saved " +
        "automatically each time the app starts. Needs Android 11 or newer."

    const val KEEP_LAST =
        "How many past runs to keep for each game.\n\n" +
        "When a game launches, the previous run's logs are moved into a \"previous\" folder rather " +
        "than being overwritten, and anything older than this many runs is deleted. The current " +
        "run's files keep their normal names, so the newest log is always the obvious one.\n\n" +
        "Set it to 0 to keep no history at all."

    const val RUST_ENGINE =
        "On by default — no performance cost in game.\n\n" +
        "Runs the whole Steam side of WinHub (sign-in, library, downloads, cloud saves, " +
        "achievements, friends) on the app's native engine. Needs an app restart to take effect.\n\n" +
        "Turn it off only if something Steam-related misbehaves: that switches back to the legacy " +
        "Java engine for this release — nothing is lost. While the native engine is on, its own log " +
        "is added to the SteamLite bundle so a problem can be traced."

    const val RUST_STORE_ENGINES =
        "On by default — no performance cost in game.\n\n" +
        "Fetches Epic, GOG and Amazon game files with the app's native download engine (the same " +
        "adaptive multi-connection fetcher the Steam engine uses). Everything else about a download " +
        "— what is installed, resume, shortcuts, cloud saves — is unchanged.\n\n" +
        "Each switch is read when a download starts, so flipping one applies to the next download " +
        "of that store without a restart. Turn a store off if its downloads misbehave: that store " +
        "goes back to its Java downloader — nothing is lost."

    fun explainAll(): String = buildString {
        append("Costs performance while on\n\n")
        append("• Wine debug\n$WINE\n\n")
        append("• Box64 / FEXCore\n$BOX64\n\n")
        append("\nSafe to leave on\n\n")
        append("• DXVK & VKD3D\n$DXVK\n\n")
        append("• SteamLite (Steam client)\n$STEAMLITE\n\n")
        append("• Android logcat\n$LOGCAT\n\n")
        append("• Crash reports\n$CRASH\n\n")
        append("\nOrganisation\n\n")
        append("• Folder for each game\n$PER_GAME\n\n")
        append("• Keep last runs\n$KEEP_LAST\n\n")
        append("\nDeveloper\n\n")
        append("• Rust Steam engine\n$RUST_ENGINE\n\n")
        append("• Rust store engines (Epic / GOG / Amazon downloads)\n$RUST_STORE_ENGINES\n")
    }
}
