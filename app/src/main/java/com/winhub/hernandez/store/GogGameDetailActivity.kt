package com.winhub.hernandez.store

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.INSTALLED_GREEN
import com.winhub.hernandez.store.download.StoreDownloadHooks
import com.winhub.hernandez.store.download.InfoChip
import com.winhub.hernandez.store.download.MediaTab
import com.winhub.hernandez.store.download.MediaVideo
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreMedia
import com.winhub.hernandez.store.download.StoreActionButton
import com.winhub.hernandez.store.download.StoreActionRow
import com.winhub.hernandez.store.download.StoreBadge
import com.winhub.hernandez.store.download.StoreDetailHeader
import com.winhub.hernandez.store.download.StoreDetailState
import com.winhub.hernandez.store.download.StoreHero
import com.winhub.hernandez.store.download.StoreProgressBar
import com.winhub.hernandez.store.download.StoreSection
import com.winhub.hernandez.store.download.StoreStatusText
import com.winhub.hernandez.store.download.StoreDetailScaffold
import com.winhub.hernandez.store.download.StoreGearItem
import com.winhub.hernandez.store.download.StorePrimaryAction
import com.winhub.hernandez.ui.theme.WinlatorTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class GogGameDetailActivity : ComponentActivity(), GogRedistInstaller.Host {

    /** Redist install feedback → the shared themed bar (system Toasts are an unreadable black box
     *  on this ROM). Posted on the main thread; GogRedistInstaller already calls on main. */
    override fun onRedistMessage(msg: String) { runOnUiThread { resultBarMsg = msg } }


    companion object {
        const val RESULT_REFRESH = 100
        private const val TAG = "BH_GOG_DETAIL"
        private const val REQUEST_FOLDER_PICKER = 200

        fun formatBytes(bytes: Long): String {
            if (bytes >= 1_073_741_824L) return "%.1f GB".format(bytes / 1_073_741_824.0)
            return "%.0f MB".format(bytes / 1_048_576.0)
        }

        private fun formatDate(iso: String?): String {
            if (iso == null || iso.length < 10) return iso ?: ""
            val parts = iso.substring(0, 10).split("-")
            if (parts.size != 3) return iso.substring(0, 10)
            try {
                val year = parts[0].toInt()
                val month = parts[1].toInt()
                val day = parts[2].toInt()
                val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                if (month < 1 || month > 12) return iso.substring(0, 10)
                return "${months[month - 1]} $day, $year"
            } catch (_: Exception) {
                return iso.substring(0, 10)
            }
        }

        private fun shortenPath(path: String): String {
            val parts = path.split("/")
            if (parts.size <= 3) return path
            return "\u2026/${parts[parts.size - 2]}/${parts[parts.size - 1]}"
        }
    }

    private lateinit var prefs: android.content.SharedPreferences

    private var gameId by mutableStateOf("")
    private var title by mutableStateOf("")
    private var imageUrl by mutableStateOf("")
    private var description by mutableStateOf("")
    private var developer by mutableStateOf("")
    private var category by mutableStateOf("")
    private var generation by mutableIntStateOf(0)

    private var headerBitmap by mutableStateOf<Bitmap?>(null)
    private var cancelDownload by mutableStateOf<Runnable?>(null)

    private var exeNameText by mutableStateOf("")
    private var exeNameVisible by mutableStateOf(false)
    private var launchVisible by mutableStateOf(false)
    private var installVisible by mutableStateOf(true)
    private var installBtnText by mutableStateOf("Install")
    private var installBtnColor by mutableIntStateOf(0xFF5533CC.toInt())
    private var setExeVisible by mutableStateOf(false)
    private var uninstallVisible by mutableStateOf(false)
    private var copyVisible by mutableStateOf(false)
    // gap#3: this gen2 game declared GOG redistributable dependencies (gog_deps_<gameId>), so the
    // detail page shows a manual "Install prerequisites" action.
    private var prereqsVisible by mutableStateOf(false)
    private var progressVisible by mutableStateOf(false)
    private var progressValue by mutableIntStateOf(0)
    private var progressLabel by mutableStateOf("")
    private var progressLabelVisible by mutableStateOf(false)
    private var sizeText by mutableStateOf("Fetching\u2026")

    private var updatesInstalled by mutableStateOf(false)
    private var updateStatusText by mutableStateOf("")
    private var checkUpdateEnabled by mutableStateOf(true)
    private var updateBtnVisible by mutableStateOf(false)

    private var dlcJson by mutableStateOf<String?>(null)
    // Media tab: GOG's public product page screenshots + trailers (StoreMediaCache first).
    private var media by mutableStateOf<StoreMedia?>(null)
    private var mediaLoading by mutableStateOf(true)
    // gap#5 DLC install: per-DLC row state (id → Install / Installing…% / Installed), observable so
    // the DLC section re-renders as each install progresses. Base-installed gate (DLC interleaves
    // into the base install dir, so it needs the base game present first).
    private val dlcStates = androidx.compose.runtime.mutableStateMapOf<String, DlcRowUi>()
    private var dlcBaseInstalled by mutableStateOf(false)

    private var cloudSaveDir by mutableStateOf<String?>(null)
    private var cloudSaveDirText by mutableStateOf("No save folder set")
    private var cloudSaveDirColor by mutableIntStateOf(0xFF555577.toInt())
    private var cloudSaveStatusText by mutableStateOf("")
    private var cloudSaveStatusVisible by mutableStateOf(false)
    private var cloudBtnsEnabled by mutableStateOf(true)

    private var showExePicker by mutableStateOf<ExePickerStateGame?>(null)

    // Themed auto-dismiss bar — system Toasts render as an unreadable black box on this ROM
    // (targetSDK 28); reuse the shared UninstallResultBar for readable feedback.
    private var resultBarMsg by mutableStateOf<String?>(null)
    private val cancelFlag = AtomicBoolean(false)

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val selectedPath = result.data!!.getStringExtra("path")
            if (selectedPath != null && selectedPath.isNotEmpty()) {
                prefs.edit().putString("gog_save_dir_$gameId", selectedPath).apply()
                cloudSaveDir = selectedPath
                cloudSaveDirText = shortenPath(selectedPath)
                cloudSaveDirColor = 0xFFCCCCCC.toInt()
                cloudBtnsEnabled = true
                resultBarMsg = "Save folder set"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("bh_gog_prefs", 0)

        // Cross-store Download Manager (Phase B): GOG can download without the Steam foreground
        // service ever running, so init the registry + seed/self-heal the installed library here
        // (idempotent) — mirrors AmazonGameDetailActivity.
        DownloadRegistry.init(this)
        GogLibrarySync.seed(this)

        val i = intent
        gameId = i.getStringExtra("game_id") ?: ""
        if (gameId.isEmpty()) { finish(); return }
        title = i.getStringExtra("title") ?: ""
        imageUrl = i.getStringExtra("image_url") ?: ""
        description = i.getStringExtra("description") ?: ""
        developer = i.getStringExtra("developer") ?: ""
        category = i.getStringExtra("category") ?: ""
        generation = i.getIntExtra("generation", 0)

        dlcJson = prefs.getString("gog_dlcs_$gameId", null)
        initDlcStates()
        updateStatusText = prefs.getString("gog_build_$gameId", null)?.let {
            "Installed build: ${it.substring(0, minOf(12, it.length))}\u2026"
        } ?: "Build ID not recorded \u2014 tap Check to verify"
        updatesInstalled = prefs.getString("gog_exe_$gameId", null) != null
        if (!updatesInstalled) updateStatusText = ""
        checkUpdateEnabled = updatesInstalled

        cloudSaveDir = prefs.getString("gog_save_dir_$gameId", null)
        if (cloudSaveDir != null) {
            // Explicit manual pick (Browse) — show it as the override.
            cloudSaveDirText = shortenPath(cloudSaveDir!!)
            cloudSaveDirColor = 0xFFCCCCCC.toInt()
        } else {
            // No manual pick — show the auto-resolved folder (GogCloudSavePaths), agreeing with the
            // Save Manager GOG tab. Resolved off-main; label updates when ready.
            cloudSaveDirText = "No save folder set"
            cloudSaveDirColor = 0xFF555577.toInt()
            refreshResolvedSaveDirLabel()
        }
        // Buttons stay enabled: the folder is resolved (manual or auto) at click time.
        cloudBtnsEnabled = true

        refreshActionState()
        observeRegistry()
        loadInstallSize()
        loadMedia()

        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                GogGameDetailScreen(
                    headerBitmap = headerBitmap,
                    title = title,
                    generation = generation,
                    developer = developer,
                    category = category,
                    description = description,
                    exeNameText = exeNameText,
                    exeNameVisible = exeNameVisible,
                    launchVisible = launchVisible,
                    installVisible = installVisible,
                    installBtnText = installBtnText,
                    setExeVisible = setExeVisible,
                    uninstallVisible = uninstallVisible,
                    copyVisible = copyVisible,
                    progressVisible = progressVisible,
                    progressValue = progressValue,
                    progressLabel = progressLabel,
                    progressLabelVisible = progressLabelVisible,
                    sizeText = sizeText,
                    updatesInstalled = updatesInstalled,
                    updateStatusText = updateStatusText,
                    checkUpdateEnabled = checkUpdateEnabled,
                    updateBtnVisible = updateBtnVisible,
                    dlcJson = dlcJson,
                    dlcStates = dlcStates,
                    dlcBaseInstalled = dlcBaseInstalled,
                    onInstallDlc = { id, dlcTitle -> installDlc(id, dlcTitle) },
                    cloudSaveDirText = cloudSaveDirText,
                    cloudSaveDirColor = cloudSaveDirColor,
                    cloudSaveStatusText = cloudSaveStatusText,
                    cloudSaveStatusVisible = cloudSaveStatusVisible,
                    cloudBtnsEnabled = cloudBtnsEnabled,
                    onBack = { finish() },
                    onLaunch = {
                        val exe = prefs.getString("gog_exe_$gameId", null)
                        // Add to the chosen container; the prereqs-aware path then offers to install the
                        // game's required GOG redistributables (gap#3) into that same prefix.
                        if (exe != null) GogLaunchHelper.addToLauncherWithPrereqs(
                            this@GogGameDetailActivity, title, exe, imageUrl, gameId)
                    },
                    prereqsVisible = prereqsVisible,
                    onInstallPrereqs = {
                        GogRedistInstaller.promptContainerAndInstall(this@GogGameDetailActivity, gameId)
                    },
                    onInstall = { onInstallClicked() },
                    onSetExe = {
                        val dir = prefs.getString("gog_dir_$gameId", null) ?: return@GogGameDetailScreen
                        val installPath = GogInstallPath.getInstallDir(this@GogGameDetailActivity, dir)
                        lifecycleScope.launch(Dispatchers.IO) {
                            val candidates = GogDownloadManager.collectExeCandidates(installPath)
                            if (candidates.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    resultBarMsg = "No .exe files found"
                                }
                                return@launch
                            }
                            withContext(Dispatchers.Main) {
                                showExePicker = ExePickerStateGame(candidates) { selected ->
                                    if (selected != null && selected.isNotEmpty()) {
                                        prefs.edit().putString("gog_exe_$gameId", selected).apply()
                                        refreshActionState()
                                        setResult(RESULT_REFRESH)
                                        resultBarMsg = "Exe set: ${File(selected).name}"
                                    }
                                }
                            }
                        }
                    },
                    onUninstall = { confirmUninstall() },
                    onCopy = {
                        resultBarMsg = "Copying\u2026"
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dest = GogDownloadManager.copyToDownloads(this@GogGameDetailActivity, gameId)
                            withContext(Dispatchers.Main) {
                                resultBarMsg = if (dest != null) "Copied to: $dest"
                                else "Copy failed \u2014 check storage permission"
                            }
                        }
                    },
                    onCheckUpdate = { doCheckUpdate() },
                    onVerifyRepair = { doVerifyRepair() },
                    onUpdateNow = {
                        updateBtnVisible = false
                        updateStatusText = "Updating\u2026"
                        startInstall()
                    },
                    onBrowseCloud = {
                        val intent = Intent(this@GogGameDetailActivity, FolderPickerActivity::class.java)
                        folderPickerLauncher.launch(intent)
                    },
                    onUploadSaves = { cloudSync(up = true) },
                    onDownloadSaves = { cloudSync(up = false) },
                    media = media,
                    mediaLoading = mediaLoading,
                    onOpenVideo = { MediaPlayback.openVideo(this@GogGameDetailActivity, it) },
                )

                showExePicker?.let { state ->
                    ExePickerDialogGame(
                        candidates = state.candidates,
                        onSelected = { path ->
                            state.onSelected(path)
                            showExePicker = null
                        },
                        onDismiss = { showExePicker = null },
                    )
                }
                resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
            
                }}
        }

        loadHeaderImage()
    }

    override fun onBackPressed() {
        // Leaving the detail page no longer cancels an in-flight download: it keeps running (the
        // GOG engine owns its own thread and the DownloadForegroundService StoreDownloadHooks
        // started keeps the process alive), and can still be cancelled from the Download Manager
        // (reachable by tapping the shade notification). Mirrors AmazonGameDetailActivity.
        super.onBackPressed()
    }

    /** `//host/img` → `https://host/img`; blank → null. Matches loadHeaderImage's normalization. */
    private fun coverUrl(): String? {
        if (imageUrl.isEmpty()) return null
        return if (imageUrl.startsWith("//")) "https:$imageUrl" else imageUrl
    }

    private fun loadHeaderImage() {
        if (imageUrl.isEmpty()) return
        val url = if (imageUrl.startsWith("//")) "https:$imageUrl" else imageUrl
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "GOG Galaxy")
                if (conn.responseCode == 200) {
                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    if (bmp != null) headerBitmap = bmp
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }


    /** Media tab source: the shared cache, else GOG's public product page (one request per open). */
    private fun loadMedia() {
        lifecycleScope.launch {
            mediaLoading = true
            val ctx = this@GogGameDetailActivity
            media = withContext(Dispatchers.IO) {
                StoreMediaCache.get(ctx, Store.GOG, gameId) ?: run {
                    val fetched = runCatching { GogStoreCatalog.product(gameId)?.media }.getOrNull()
                    StoreMediaCache.put(ctx, Store.GOG, gameId, fetched ?: StoreMedia.EMPTY, miss = fetched == null)
                    fetched ?: StoreMedia.EMPTY
                }
            }
            mediaLoading = false
        }
    }

    private fun loadInstallSize() {
        val cached = prefs.getLong("gog_size_$gameId", -1)
        if (cached > 0) { sizeText = formatBytes(cached); return }
        lifecycleScope.launch(Dispatchers.IO) {
            val token = prefs.getString("access_token", null)
            val size = GogDownloadManager.fetchInstallSizeBytes(gameId, token)
            if (size > 0) prefs.edit().putLong("gog_size_$gameId", size).apply()
            withContext(Dispatchers.Main) {
                sizeText = if (size > 0) formatBytes(size) else "Unknown"
            }
        }
    }

    private fun onInstallClicked() {
        val lbl = installBtnText
        if (lbl == "Cancel") {
            cancelDownload?.run()
            cancelDownload = null
            return
        }
        startInstall()
    }

    private fun startInstall(verify: Boolean = false) {
        installBtnText = "Cancel"
        installBtnColor = 0xFFCC3333.toInt()
        progressVisible = true
        progressLabelVisible = true
        launchVisible = false
        setExeVisible = false

        // Publish this download into the cross-store Download Manager registry. This also starts
        // the shared DownloadForegroundService (shade notification + process kept alive across
        // backgrounding). GOG reports pct only → single honest bar (byte pairs left at 0). Cancel
        // routes to the same Runnable the detail page's Cancel uses.
        StoreDownloadHooks.registerDownload(
            store = Store.GOG,
            id = gameId,
            name = title,
            cover = coverUrl(),
            supportsPause = false,
            installTotal = prefs.getLong("gog_size_$gameId", 0L),
            cancel = { cancelDownload?.run() },
        )

        // applicationContext (NOT this Activity): the GOG engine spawns its own download thread and
        // keeps a reference to the Context for its whole run, so the download survives this Activity
        // being destroyed / the app backgrounded without leaking the Activity. Registry hooks below
        // run on the engine's callback thread and are Activity-independent; only the mutableState UI
        // writes are guarded with !isDestroyed && !isFinishing.
        val cb: GogDownloadManager.Callback = object : GogDownloadManager.Callback {
            override fun onProgress(msg: String, pct: Int) {
                StoreDownloadHooks.tick(Store.GOG, gameId, pct)
                if (!isDestroyed && !isFinishing) runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    progressValue = pct
                    // Same live-% label the registry collector shows, so a locally-started
                    // download reads identically to a list-started one (no flicker between the
                    // engine status string and the "$pct%" the Manager/notification show).
                    progressLabel = "Downloading… $pct%"
                }
            }
            override fun onComplete(exePath: String) {
                cancelDownload = null
                // Finalize into the registry regardless of which screen is showing (the engine
                // already wrote gog_exe_/gog_dir_ before this fires). No blocking dialog — mirrors
                // the Amazon completion fix that unwedged the 100%-stuck card.
                val exe = prefs.getString("gog_exe_$gameId", null)
                val dir = prefs.getString("gog_dir_$gameId", null)
                if (exe != null && dir != null) {
                    val installAbs = GogInstallPath.getInstallDir(applicationContext, dir).absolutePath
                    StoreDownloadHooks.markInstalled(
                        store = Store.GOG,
                        id = gameId,
                        installPath = installAbs,
                        bytes = prefs.getLong("gog_size_$gameId", 0L),
                    )
                } else {
                    StoreDownloadHooks.markFailed(Store.GOG, gameId, "No executable found")
                }
                if (!isDestroyed && !isFinishing) runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    progressValue = 100
                    progressVisible = false
                    progressLabelVisible = false
                    setResult(RESULT_REFRESH)
                    refreshActionState()
                }
            }
            override fun onError(msg: String) {
                cancelDownload = null
                StoreDownloadHooks.markFailed(Store.GOG, gameId, msg)
                if (!isDestroyed && !isFinishing) runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    progressVisible = false
                    progressLabelVisible = false
                    installBtnText = "Install"
                    installBtnColor = 0xFF5533CC.toInt()
                    launchVisible = prefs.getString("gog_exe_$gameId", null) != null
                    setExeVisible = prefs.getString("gog_dir_$gameId", null) != null
                    resultBarMsg = "Error: $msg"
                }
            }
            override fun onCancelled() {
                cancelDownload = null
                StoreDownloadHooks.markCancelled(Store.GOG, gameId)
                if (!isDestroyed && !isFinishing) runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    progressVisible = false
                    progressLabelVisible = false
                    installBtnText = "Install"
                    installBtnColor = 0xFF5533CC.toInt()
                    launchVisible = prefs.getString("gog_exe_$gameId", null) != null
                    setExeVisible = prefs.getString("gog_dir_$gameId", null) != null
                }
            }
            override fun onSelectExe(candidates: MutableList<String>?, onSelected: Consumer<String>?) {
                // Completion must NEVER block on a dialog (it would wedge the DL-manager card at
                // 100% when the user isn't on this screen). Auto-pick the best (first) candidate —
                // the exe choice is available later via "Set .exe…". Mirrors the Amazon fix.
                if (onSelected != null) onSelected.accept(candidates?.firstOrNull() ?: "")
            }
        }
        cancelDownload = if (verify)
            GogDownloadManager.verifyRepair(applicationContext, makeGogGame(), cb)
        else
            GogDownloadManager.startDownload(applicationContext, makeGogGame(), cb)
    }

    /**
     * Verify installed files and re-download only those missing or failing MD5 (gen2). Reuses the
     * whole install flow + progress UI; the engine's size+MD5 skip-logic makes the re-run a repair.
     */
    private fun doVerifyRepair() {
        updateStatusText = "Verifying files…"
        startInstall(verify = true)
    }

    private fun confirmUninstall() {
        val dlgTitle = title
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Uninstall $dlgTitle?")
            .setMessage("This will delete all installed game files.")
            .setPositiveButton("Uninstall") { _, _ -> doUninstall() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doUninstall() {
        val dirName = prefs.getString("gog_dir_$gameId", null) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val installPath = GogInstallPath.getInstallDir(this@GogGameDetailActivity, dirName)
            deleteDir(installPath)
            // Purge the FULL native install record via the one canonical helper, and clear the DL
            // manager's registry/library row — so the store list, this page, and the cross-store
            // Download Manager all agree the game is gone. Mirrors AmazonGameDetailActivity.
            GogInstallState.purge(applicationContext, gameId)
            StoreDownloadHooks.markUninstalled(Store.GOG, gameId)
            withContext(Dispatchers.Main) {
                setResult(RESULT_REFRESH)
                refreshActionState()
                resultBarMsg = "$title uninstalled"
            }
        }
    }

    /**
     * Make the detail page a live reflection of [DownloadRegistry] for THIS game. Without this,
     * opening the page while a download is live (started from the games list, or after the Activity
     * was recreated) showed "Install" even though the DL-manager card + shade notification were
     * progressing — the page only read install prefs. Runs on the main dispatcher (lifecycleScope
     * default) so the Compose state writes are main-thread-safe.
     */
    private fun observeRegistry() {
        val myKey = "${Store.GOG}:$gameId"
        lifecycleScope.launch {
            DownloadRegistry.entries.collect { list ->
                val e = list.firstOrNull { it.key == myKey }
                if (e != null && (e.state == DownloadState.DOWNLOADING || e.state == DownloadState.PAUSED)) {
                    progressVisible = true
                    progressValue = e.pct
                    // Live percentage on the detail page, matching the Download Manager card and
                    // the notification (GOG is pct-only). Registry-driven so it stays live for a
                    // list-started / reopened download, not just the one this Activity launched.
                    progressLabel = "Downloading… ${e.pct}%"
                    progressLabelVisible = true
                    installVisible = true
                    installBtnText = "Cancel"
                    installBtnColor = 0xFFCC3333.toInt()
                    launchVisible = false
                    setExeVisible = false
                    uninstallVisible = false
                    copyVisible = false
                    // Route Cancel to the registry entry so it works for a list-started download —
                    // but ONLY if we don't already hold the real engine canceller (a locally-started
                    // download sets it in startInstall). Overwriting it would make the registry
                    // entry's cancel (which calls cancelDownload) recurse into itself.
                    if (cancelDownload == null) cancelDownload = Runnable { e.cancel?.invoke() }
                } else {
                    // No active entry (absent / INSTALLED / FAILED / CANCELLED): settle from prefs.
                    progressVisible = false
                    progressLabelVisible = false
                    refreshActionState()
                }
            }
        }
    }

    private fun refreshActionState() {
        val exe = prefs.getString("gog_exe_$gameId", null)
        val dir = prefs.getString("gog_dir_$gameId", null)
        val installed = exe != null && dir != null

        if (installed) {
            exeNameText = ".exe: ${File(exe).name}"
            exeNameVisible = true
        } else {
            exeNameVisible = false
        }

        launchVisible = installed
        installVisible = !installed
        if (!installed) installBtnText = "Install"
        if (!installed) installBtnColor = 0xFF5533CC.toInt()
        setExeVisible = installed
        uninstallVisible = installed
        copyVisible = installed
        // gap#5: DLC needs the base install dir present. Gate the DLC Install buttons on it.
        dlcBaseInstalled = installed
        // Offer the manual prereq install once the game is installed AND it declared GOG deps.
        prereqsVisible = installed && GogRedistInstaller.hasPrereqs(this, gameId)
    }

    /** Seed each owned DLC's row state from its per-DLC installed marker (Installed on return). */
    private fun initDlcStates() {
        dlcStates.clear()
        val json = dlcJson ?: return
        val arr = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val id = arr.optJSONObject(i)?.optString("id") ?: continue
            if (id.isEmpty()) continue
            val installed = GogDownloadManager.isDlcInstalled(this, gameId, id)
            dlcStates[id] = DlcRowUi(installing = false, installed = installed, pct = 0)
        }
    }

    /**
     * gap#5 — drive one owned DLC's install. Records progress into [dlcStates] so its row shows
     * Installing…pct% then Installed; a 403/failure surfaces on the themed result bar and reverts
     * the row to Install. Fail-soft: never touches the base game.
     */
    private fun installDlc(dlcId: String, dlcTitle: String) {
        dlcStates[dlcId] = DlcRowUi(installing = true, installed = false, pct = 0)
        val cb = object : GogDownloadManager.Callback {
            override fun onProgress(msg: String, pct: Int) {
                if (!isDestroyed && !isFinishing) runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    dlcStates[dlcId] = DlcRowUi(installing = true, installed = false, pct = pct)
                }
            }
            override fun onComplete(exePath: String) {
                if (!isDestroyed && !isFinishing) runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    dlcStates[dlcId] = DlcRowUi(installing = false, installed = true, pct = 100)
                    resultBarMsg = "$dlcTitle installed"
                }
            }
            override fun onError(msg: String) {
                if (!isDestroyed && !isFinishing) runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    dlcStates[dlcId] = DlcRowUi(installing = false, installed = false, pct = 0)
                    resultBarMsg = if (msg.contains("own", ignoreCase = true))
                        "You may not own this DLC" else "DLC install failed: $msg"
                }
            }
        }
        GogDownloadManager.installDlc(applicationContext, makeGogGame(), dlcId, dlcTitle, cb)
    }

    private fun doCheckUpdate() {
        updateStatusText = "Checking\u2026"
        checkUpdateEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = prefs.getString("access_token", null)
                val url = "https://content-system.gog.com/products/$gameId/os/windows/builds?generation=2"
                val conn = URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "GOG Galaxy")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")

                var body = ""
                if (conn.responseCode == 200) {
                    val br = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    val sb = StringBuilder()
                    var line: String?
                    while (br.readLine().also { line = it } != null) sb.append(line)
                    body = sb.toString()
                }
                conn.disconnect()

                var latestBuild: String? = null
                if (body.isNotEmpty()) {
                    val j = org.json.JSONObject(body)
                    val items = j.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            if ("windows" == item.optString("os")) {
                                latestBuild = item.optString("build_id")
                                break
                            }
                        }
                    }
                }

                val latest = latestBuild
                withContext(Dispatchers.Main) {
                    checkUpdateEnabled = true
                    if (latest == null) {
                        updateStatusText = "Could not reach update server."
                        return@withContext
                    }
                    val stored = prefs.getString("gog_build_$gameId", null)
                    if (stored == null) {
                        prefs.edit().putString("gog_build_$gameId", latest).apply()
                        updateStatusText = "Up to date (build ${latest.substring(0, minOf(12, latest.length))}\u2026)"
                        updateBtnVisible = false
                    } else if (stored == latest) {
                        updateStatusText = "Up to date \u2713"
                        updateBtnVisible = false
                    } else {
                        updateStatusText = "Update available!\nInstalled: ${stored.substring(0, minOf(10, stored.length))}\u2026  \u2192  Latest: ${latest.substring(0, minOf(10, latest.length))}\u2026"
                        updateBtnVisible = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    checkUpdateEnabled = true
                    updateStatusText = "Check failed: ${e.message}"
                }
            }
        }
    }

    private fun makeGogGame() = GogGame(
        gameId,
        title.ifEmpty { "" },
        imageUrl.ifEmpty { "" },
        description.ifEmpty { "" },
        developer.ifEmpty { "" },
        category.ifEmpty { "" },
        generation,
    )

    private fun showCloudStatus(msg: String) {
        cloudSaveStatusText = msg
        cloudSaveStatusVisible = true
    }

    /**
     * Resolve this game's GOG save folder: a manual pick (Browse) wins as a user override; otherwise
     * auto-resolve via [GogCloudSavePaths] (container match + cloud-storage location token-expand,
     * same as the Save Manager GOG tab). Does file I/O + a possible remote-config fetch — MUST be
     * called off the main thread.
     */
    private fun resolveGogSaveDir(): File? {
        // Explicit manual pick (Browse) wins as a user override.
        prefs.getString("gog_save_dir_$gameId", null)?.takeIf { it.isNotEmpty() }?.let { return File(it) }
        val (_, dir) = GogCloudSavePaths.resolve(this, gameId)
        return dir
    }

    /** Populate the Cloud Saves label from the auto-resolver (off-main) when no manual folder is set. */
    private fun refreshResolvedSaveDirLabel() {
        Thread {
            val dir = resolveGogSaveDir()
            runOnUiThread {
                if (dir != null && cloudSaveDir == null) {
                    cloudSaveDirText = "Auto: " + shortenPath(dir.absolutePath)
                    cloudSaveDirColor = 0xFFCCCCCC.toInt()
                }
            }
        }.start()
    }

    /** Resolve the save folder off-main (manual override or auto), then drive the transport. */
    private fun cloudSync(up: Boolean) {
        cloudBtnsEnabled = false
        showCloudStatus("Resolving save folder…")
        Thread {
            val dir = resolveGogSaveDir()
            if (dir == null) {
                runOnUiThread {
                    showCloudStatus("No save folder found for this game — add it to a container, or set one with Browse")
                    cloudBtnsEnabled = true
                }
                return@Thread
            }
            runOnUiThread { showCloudStatus(if (up) "Preparing upload…" else "Preparing download…") }
            val cb = object : GogCloudSaveManager.Callback {
                override fun onStatus(msg: String) { runOnUiThread { showCloudStatus(msg) } }
                override fun onDone(msg: String) { runOnUiThread { showCloudStatus(msg); cloudBtnsEnabled = true } }
                override fun onError(msg: String) { runOnUiThread { showCloudStatus("Error: $msg"); cloudBtnsEnabled = true } }
            }
            if (up) GogCloudSaveManager.uploadSaves(this, gameId, dir, cb)
            else GogCloudSaveManager.downloadSaves(this, gameId, dir, cb)
        }.start()
    }

    private fun deleteDir(dir: File) {
        if (dir == null || !dir.exists()) return
        val files = dir.listFiles()
        if (files != null) for (f in files) {
            if (f.isDirectory) deleteDir(f) else f.delete()
        }
        dir.delete()
    }

}

private data class ExePickerStateGame(
    val candidates: List<String>,
    val onSelected: (String) -> Unit,
)

/** gap#5 — one DLC row's install state: Install (idle) → Installing…pct% → Installed. */
private data class DlcRowUi(
    val installing: Boolean,
    val installed: Boolean,
    val pct: Int,
)

// ── Composable Screen ──────────────────────────────────────────────────────
//
// Mounted on the shared Steam-style scaffold (StoreDetailScaffold): hero → name → ONE primary
// button (Install / Launch, or the read-only download fill) + ⚙ gear → pill tabs
// (Details · DLC · Cloud saves). Every former action is still reachable: the gear carries
// cancel / set-exe / copy / updates / verify / prerequisites / uninstall. The handlers are
// UNCHANGED — only the layout moved.

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GogGameDetailScreen(
    headerBitmap: Bitmap?,
    title: String,
    generation: Int,
    developer: String,
    category: String,
    description: String,
    exeNameText: String,
    exeNameVisible: Boolean,
    launchVisible: Boolean,
    installVisible: Boolean,
    installBtnText: String,
    setExeVisible: Boolean,
    uninstallVisible: Boolean,
    copyVisible: Boolean,
    prereqsVisible: Boolean,
    progressVisible: Boolean,
    progressValue: Int,
    progressLabel: String,
    progressLabelVisible: Boolean,
    sizeText: String,
    updatesInstalled: Boolean,
    updateStatusText: String,
    checkUpdateEnabled: Boolean,
    updateBtnVisible: Boolean,
    dlcJson: String?,
    dlcStates: Map<String, DlcRowUi>,
    dlcBaseInstalled: Boolean,
    onInstallDlc: (String, String) -> Unit,
    cloudSaveDirText: String,
    cloudSaveDirColor: Int,
    cloudSaveStatusText: String,
    cloudSaveStatusVisible: Boolean,
    cloudBtnsEnabled: Boolean,
    onBack: () -> Unit,
    onLaunch: () -> Unit,
    onInstall: () -> Unit,
    onSetExe: () -> Unit,
    onUninstall: () -> Unit,
    onCopy: () -> Unit,
    onInstallPrereqs: () -> Unit,
    onCheckUpdate: () -> Unit,
    onVerifyRepair: () -> Unit,
    onUpdateNow: () -> Unit,
    onBrowseCloud: () -> Unit,
    onUploadSaves: () -> Unit,
    onDownloadSaves: () -> Unit,
    media: StoreMedia?,
    mediaLoading: Boolean,
    onOpenVideo: (MediaVideo) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val downloading = progressVisible && installBtnText == "Cancel"

    val primary = when {
        downloading -> StorePrimaryAction.Progress(
            label = if (progressLabelVisible && progressLabel.isNotBlank()) progressLabel else "Downloading… $progressValue%",
            fraction = progressValue / 100f,
        )
        launchVisible -> StorePrimaryAction.Button("Launch", onLaunch)
        else -> StorePrimaryAction.Button(installBtnText, onInstall, enabled = installVisible)
    }
    val gear = buildList {
        // onInstall doubles as Cancel while the button label reads "Cancel" (see onInstallClicked).
        if (downloading) add(StoreGearItem("🗑", "Cancel download", danger = true, onClick = onInstall))
        if (setExeVisible) add(StoreGearItem("🎯", "Set .exe…", onClick = onSetExe))
        if (copyVisible) add(StoreGearItem("📁", "Copy to Downloads", onClick = onCopy))
        if (updatesInstalled) {
            add(StoreGearItem("🔄", "Check for updates", enabled = checkUpdateEnabled, onClick = onCheckUpdate))
            add(StoreGearItem("🩹", "Verify / repair files", enabled = checkUpdateEnabled, onClick = onVerifyRepair))
        }
        if (prereqsVisible) add(StoreGearItem("🧰", "Install prerequisites", onClick = onInstallPrereqs))
        if (uninstallVisible) add(StoreGearItem("🗑️", "Uninstall", danger = true, onClick = onUninstall))
    }
    val dlcCount = remember(dlcJson) {
        if (dlcJson.isNullOrEmpty() || dlcJson == "[]") 0
        else runCatching { org.json.JSONArray(dlcJson).length() }.getOrDefault(0)
    }
    // "Media" is appended only once the fetch found any, so the fixed indices above stay valid.
    val mediaVisible = media?.isEmpty == false
    val tabs = buildList {
        add("Details"); add("DLC"); add("Cloud saves")
        if (mediaVisible) add("Media")
    }

    StoreDetailScaffold(
        onBack = onBack,
        title = title,
        storeBadge = { StoreBadge(Store.GOG) },
        hero = {
            if (headerBitmap != null) {
                Image(
                    bitmap = headerBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
        },
        subtitle = if (exeNameVisible) {
            {
                Spacer(Modifier.height(4.dp))
                StoreStatusText(exeNameText, StoreDetailState.INSTALLED)
            }
        } else null,
        primary = primary,
        gear = gear,
        tabs = tabs,
        selectedTab = tab,
        onSelectTab = { tab = it },
        tabBadges = buildMap {
            if (dlcCount > 0) put(1, "$dlcCount")
            if (mediaVisible) put(3, "${media?.count ?: 0}")
        },
    ) {
        when (tab) {
            0 -> Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip(sizeText)
                        if (developer.isNotEmpty()) InfoChip(developer)
                        if (category.isNotEmpty()) InfoChip(category)
                        if (generation > 0) InfoChip("Gen $generation")
                    }
                    if (description.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT).toString().trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                StoreSection(title = "Updates") {
                    GogUpdatesContent(
                        installed = updatesInstalled,
                        updateStatusText = updateStatusText,
                        checkUpdateEnabled = checkUpdateEnabled,
                        updateBtnVisible = updateBtnVisible,
                        onCheckUpdate = onCheckUpdate,
                        onVerifyRepair = onVerifyRepair,
                        onUpdateNow = onUpdateNow,
                    )
                }
                if (prereqsVisible) {
                    StoreSection(title = "Prerequisites") {
                        StoreStatusText(
                            "This game needs Microsoft runtimes (Visual C++, .NET, …). Install them into a " +
                                "container's Wine prefix so the game can start. A brief setup window appears for each."
                        )
                        Spacer(Modifier.height(8.dp))
                        StoreActionButton(
                            text = "Install prerequisites",
                            onClick = onInstallPrereqs,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            1 -> Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                StoreSection(title = "DLC") {
                    GogDlcContent(
                        dlcJson = dlcJson,
                        baseInstalled = dlcBaseInstalled,
                        states = dlcStates,
                        onInstall = onInstallDlc,
                    )
                }
            }

            2 -> Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                StoreSection(title = "Cloud Saves") {
                    GogCloudSavesContent(
                        saveDirText = cloudSaveDirText,
                        saveDirColor = cloudSaveDirColor,
                        statusText = cloudSaveStatusText,
                        statusVisible = cloudSaveStatusVisible,
                        btnsEnabled = cloudBtnsEnabled,
                        onBrowse = onBrowseCloud,
                        onUpload = onUploadSaves,
                        onDownload = onDownloadSaves,
                    )
                }
            }

            3 -> MediaTab(
                media = media,
                loading = mediaLoading,
                storeLabel = "GOG.com",
                onOpenVideo = onOpenVideo,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Store sections ────────────────────────────────────────────────────────

@Composable
private fun GogUpdatesContent(
    installed: Boolean,
    updateStatusText: String,
    checkUpdateEnabled: Boolean,
    updateBtnVisible: Boolean,
    onCheckUpdate: () -> Unit,
    onVerifyRepair: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    // Not installed yet — just the muted hint, no buttons.
    if (!installed) {
        StoreStatusText("Install the game first to check for updates.")
        return
    }

    StoreStatusText(updateStatusText)
    Spacer(Modifier.height(8.dp))
    if (updateBtnVisible) {
        StoreActionButton(
            text = "Update Now",
            onClick = onUpdateNow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
    StoreActionButton(
        text = "Check for Updates",
        onClick = onCheckUpdate,
        modifier = Modifier.fillMaxWidth(),
        enabled = checkUpdateEnabled,
    )
    Spacer(Modifier.height(8.dp))
    // Re-verify installed files against the manifest (size + MD5); re-downloads only the
    // missing/corrupt ones. Gated on the same installed+idle signal as Check for Updates.
    StoreActionButton(
        text = "Verify / Repair Files",
        onClick = onVerifyRepair,
        modifier = Modifier.fillMaxWidth(),
        enabled = checkUpdateEnabled,
    )
}

@Composable
private fun GogDlcContent(
    dlcJson: String?,
    baseInstalled: Boolean,
    states: Map<String, DlcRowUi>,
    onInstall: (String, String) -> Unit,
) {
    val arr = remember(dlcJson) {
        if (dlcJson.isNullOrEmpty() || dlcJson == "[]") null
        else runCatching { org.json.JSONArray(dlcJson) }.getOrNull()
    }
    if (arr == null || arr.length() == 0) {
        StoreStatusText("No DLCs in your library for this game")
        return
    }

    Text(
        text = "${arr.length()} DLC${if (arr.length() == 1) "" else "s"} owned",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = if (baseInstalled) "Install a DLC to add its files to your game."
        else "Install the game first, then you can add its DLC.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 3.dp, bottom = 6.dp),
    )
    for (i in 0 until arr.length()) {
        val dlc = arr.optJSONObject(i) ?: continue
        val dlcId = dlc.optString("id", "")
        val dlcTitle = dlc.optString("title", "Unknown DLC")
        val state = states[dlcId]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dlcTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            when {
                state?.installed == true -> Text(
                    text = "Installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = INSTALLED_GREEN,
                    fontWeight = FontWeight.Bold,
                )
                state?.installing == true -> Text(
                    text = "Installing… ${state.pct}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                !baseInstalled -> Text(
                    text = "Install the game first",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> StoreActionButton(
                    text = "Install",
                    onClick = { if (dlcId.isNotEmpty()) onInstall(dlcId, dlcTitle) },
                    enabled = dlcId.isNotEmpty(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GogCloudSavesContent(
    saveDirText: String,
    saveDirColor: Int,
    statusText: String,
    statusVisible: Boolean,
    btnsEnabled: Boolean,
    onBrowse: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
) {
    // saveDirColor carries the "no folder set" sentinel — map it to a muted token.
    val dirMuted = saveDirColor == 0xFF555577.toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = saveDirText,
            style = MaterialTheme.typography.bodySmall,
            color = if (dirMuted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        StoreActionButton(text = "Browse", onClick = onBrowse)
    }
    if (statusVisible) {
        Spacer(Modifier.height(8.dp))
        StoreStatusText(statusText)
    }
    Spacer(Modifier.height(8.dp))
    StoreActionButton(
        text = "Upload Saves",
        onClick = onUpload,
        modifier = Modifier.fillMaxWidth(),
        enabled = btnsEnabled,
    )
    Spacer(Modifier.height(8.dp))
    StoreActionButton(
        text = "Download Saves",
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth(),
        enabled = btnsEnabled,
    )
}

@Composable
private fun ExePickerDialogGame(
    candidates: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select game executable") },
        text = {
            Column {
                candidates.forEach { path ->
                    val f = File(path)
                    val parent = f.parentFile
                    val label = if (parent != null) "${parent.name}/${f.name}" else f.name
                    TextButton(
                        onClick = { onSelected(path) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label, modifier = Modifier.weight(1f), color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
    )
}
