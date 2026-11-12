package com.winhub.hernandez.store

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadScope
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.INSTALLED_GREEN
import com.winhub.hernandez.store.download.InfoChip
import com.winhub.hernandez.store.download.MediaTab
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreMedia
import com.winhub.hernandez.store.download.StoreActionButton
import com.winhub.hernandez.store.download.StoreActionRow
import com.winhub.hernandez.store.download.StoreBadge
import com.winhub.hernandez.store.download.StoreDetailHeader
import com.winhub.hernandez.store.download.StoreDetailState
import com.winhub.hernandez.store.download.StoreHero
import com.winhub.hernandez.store.download.StoreProgressBar
import com.winhub.hernandez.store.download.StoreDownloadHooks
import com.winhub.hernandez.store.download.StoreSection
import com.winhub.hernandez.store.download.StoreStatusText
import com.winhub.hernandez.store.download.StoreDetailScaffold
import com.winhub.hernandez.store.download.StoreGearItem
import com.winhub.hernandez.store.download.StorePrimaryAction
import com.winhub.hernandez.ui.theme.WinlatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EpicGameDetailActivity : ComponentActivity() {

    companion object {
        const val RESULT_REFRESH = 100
        private const val TAG = "BH_EPIC_DETAIL"
        private const val REQUEST_FOLDER_PICKER = 200
    }

    private var prefs: SharedPreferences? = null

    private var appName: String? = null
    private var title: String? = null
    private var description: String? = null
    private var developer: String? = null
    private var artCover: String? = null
    private var namespace: String? = null
    private var catalogItemId: String? = null

    private var exeNameText by mutableStateOf("")
    private var installBtnText by mutableStateOf("Install")
    private var installBtnColor by mutableIntStateOf(0xFF1A73E8.toInt())
    private var launchBtnVisible by mutableStateOf(false)
    private var installBtnVisible by mutableStateOf(true)
    private var setExeBtnVisible by mutableStateOf(false)
    private var uninstallBtnVisible by mutableStateOf(false)
    private var usesEos by mutableStateOf(false)
    private var progressVisible by mutableStateOf(false)
    private var progressValue by mutableIntStateOf(0)
    private var progressLabelText by mutableStateOf("")
    private var progressLabelVisible by mutableStateOf(false)
    private var sizeText by mutableStateOf("Fetching\u2026")
    private var cancelDownload: Runnable? = null

    private var updateStatusText by mutableStateOf("")
    private var checkUpdatesEnabled by mutableStateOf(true)
    private var updateBtnVisible by mutableStateOf(false)

    private var verifyStatusText by mutableStateOf("")
    private var verifyEnabled by mutableStateOf(true)

    private var dlcJson by mutableStateOf<String?>(null)

    private var cloudSaveDirText by mutableStateOf("No save folder set")
    private var cloudSaveDirColor by mutableIntStateOf(0xFF445566.toInt())
    private var cloudSaveStatusText by mutableStateOf("")
    private var cloudSaveStatusVisible by mutableStateOf(false)
    private var cloudButtonsEnabled by mutableStateOf(true)

    // Themed auto-dismiss bar — system Toasts render as an unreadable black box on this ROM
    // (targetSDK 28); reuse the shared UninstallResultBar for readable feedback.
    private var resultBarMsg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.winhub.hernandez.ui.deck.isXmbHome(this)) com.winhub.hernandez.core.AppOrientation.apply(this)
        prefs = getSharedPreferences("bh_epic_prefs", 0)

        // Cross-store Download Manager (Phase C): Epic can download without the Steam foreground
        // service ever running, so init the registry + seed/self-heal the installed library here
        // (idempotent) — mirrors Amazon/GOG.
        DownloadRegistry.init(this)
        EpicLibrarySync.seed(this)

        appName = intent.getStringExtra("app_name")
        title = intent.getStringExtra("title")
        description = intent.getStringExtra("description")
        developer = intent.getStringExtra("developer")
        artCover = intent.getStringExtra("art_cover")
        namespace = intent.getStringExtra("namespace")
        catalogItemId = intent.getStringExtra("catalog_item_id")

        if (appName == null) { finish(); return }

        val savedDir = prefs!!.getString("epic_save_dir_$appName", null)
        if (savedDir != null) {
            cloudSaveDirText = shortenPath(savedDir)
            cloudSaveDirColor = 0xFFCCCCCC.toInt()
        } else {
            // No manual pick — show the auto-resolved folder so this surface agrees
            // with the Save Manager Epic tab (both use EpicCloudSavePaths).
            refreshResolvedSaveDirLabel()
        }

        dlcJson = catalogItemId?.let { prefs!!.getString("epic_dlcs_$it", null) }

        setContent {
            StoreXmbTheme {
                DeckStoreTheme {
                EpicGameDetailScreen(
                    appName = appName!!,
                    title = title ?: "",
                    description = description ?: "",
                    developer = developer ?: "",
                    artCover = artCover ?: "",
                    namespace = namespace ?: "",
                    catalogItemId = catalogItemId ?: "",
                    exeNameText = exeNameText,
                    installBtnText = installBtnText,
                    launchBtnVisible = launchBtnVisible,
                    installBtnVisible = installBtnVisible,
                    setExeBtnVisible = setExeBtnVisible,
                    uninstallBtnVisible = uninstallBtnVisible,
                    usesEos = usesEos,
                    progressVisible = progressVisible,
                    progressValue = progressValue,
                    progressLabelText = progressLabelText,
                    progressLabelVisible = progressLabelVisible,
                    sizeText = sizeText,
                    updateStatusText = updateStatusText,
                    checkUpdatesEnabled = checkUpdatesEnabled,
                    updateBtnVisible = updateBtnVisible,
                    verifyStatusText = verifyStatusText,
                    verifyEnabled = verifyEnabled,
                    dlcJson = dlcJson,
                    cloudSaveDirText = cloudSaveDirText,
                    cloudSaveStatusText = cloudSaveStatusText,
                    cloudSaveStatusVisible = cloudSaveStatusVisible,
                    cloudButtonsEnabled = cloudButtonsEnabled,
                    onBack = { finish() },
                    onLaunchClick = { pendingLaunchExe() },
                    onInstallClick = { onInstallClicked() },
                    onSetExeClick = { onSetExeClicked() },
                    onUninstallClick = { confirmUninstall() },
                    onCheckUpdates = { doCheckUpdate() },
                    onUpdateClick = {
                        updateBtnVisible = false
                        updateStatusText = "Updating\u2026"
                        startInstallInternal()
                    },
                    onVerifyRepair = { doVerifyRepair() },
                    onDlcInstall = { dlcApp, dlcNs, dlcCat, dlcTitle ->
                        dlcInstall(dlcApp, dlcNs, dlcCat, dlcTitle)
                    },
                    onCloudBrowse = {
                        startActivityForResult(
                            Intent(this@EpicGameDetailActivity, FolderPickerActivity::class.java),
                            REQUEST_FOLDER_PICKER,
                        )
                    },
                    onCloudUpload = { cloudUpload() },
                    onCloudDownload = { cloudDownload() },
                )
                resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
            
                }}
        }

        refreshActionState()
        observeRegistry()
        loadInstallSize()
    }

    override fun onBackPressed() {
        // Leaving the detail page no longer cancels the download: it keeps running on DownloadScope
        // with the foreground-service notification, and can still be cancelled from the Download
        // Manager. Mirrors Amazon/GOG. (Epic's cancel is best-effort anyway — see startInstallInternal.)
        super.onBackPressed()
    }

    /**
     * Make the detail page a live reflection of [DownloadRegistry] for THIS game. Without this,
     * opening the page while a download is live (started from the games list, or after the Activity
     * was recreated) showed "Install" even though the DL-manager card + shade notification were
     * progressing — the page only read install prefs. Runs on the main dispatcher (lifecycleScope
     * default) so the Compose state writes are main-thread-safe. Mirrors GogGameDetailActivity.
     */
    private fun observeRegistry() {
        val an = appName ?: return
        val myKey = "${Store.EPIC}:$an"
        lifecycleScope.launch {
            DownloadRegistry.entries.collect { list ->
                val e = list.firstOrNull { it.key == myKey }
                if (e != null && (e.state == DownloadState.DOWNLOADING || e.state == DownloadState.PAUSED)) {
                    progressVisible = true
                    progressValue = e.pct
                    // Epic is pct-only → live "$pct%" label (matches the DL card + notification, and
                    // the local onProgress below, so a list-started/reopened DL reads identically).
                    progressLabelText = "Downloading… ${e.pct}%"
                    progressLabelVisible = true
                    installBtnVisible = true
                    installBtnText = "Cancel"
                    installBtnColor = 0xFFCC3333.toInt()
                    launchBtnVisible = false
                    setExeBtnVisible = false
                    uninstallBtnVisible = false
                    // Route Cancel to the registry entry so it works for a list-started download —
                    // but ONLY if we don't already hold the local canceller (a locally-started
                    // download sets it in startInstallInternal); guards against any recursion.
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FOLDER_PICKER && resultCode == RESULT_OK && data != null) {
            val selectedPath = data.getStringExtra("path")
            if (!selectedPath.isNullOrEmpty()) {
                prefs!!.edit().putString("epic_save_dir_$appName", selectedPath).apply()
                cloudSaveDirText = shortenPath(selectedPath)
                cloudSaveDirColor = 0xFFCCCCCC.toInt()
                cloudButtonsEnabled = true
                resultBarMsg = "Save folder set"
            }
        }
    }

    private fun refreshActionState() {
        val exe = prefs!!.getString("epic_exe_$appName", null)
        val dir = prefs!!.getString("epic_dir_$appName", null)
        val installed = exe != null

        exeNameText = if (installed) ".exe: ${File(exe!!).name}" else ""
        launchBtnVisible = installed
        installBtnVisible = !installed
        setExeBtnVisible = installed
        uninstallBtnVisible = dir != null

        if (!installed) installBtnText = "Install"

        // EOS badge: reflect cached flag now; lazily scan (upgraders / pre-feature installs) and
        // refresh the state when the background walk finishes. Never scans on the render path.
        val an = appName
        if (an != null && installed) {
            val ctx = applicationContext
            usesEos = EpicEosDetector.isEosCached(ctx, an)
            val scanRoot = dir?.let { File(it) }
            EpicEosDetector.scanIfNeeded(ctx, an, scanRoot) {
                runOnUiThread { usesEos = EpicEosDetector.isEosCached(ctx, an) }
            }
        } else {
            usesEos = false
        }
    }

    private fun onInstallClicked() {
        if (installBtnText == "Cancel") {
            // Ask what to do with the partial download before cancelling.
            AlertDialog.Builder(this)
                .setTitle("Cancel download?")
                .setMessage("Keep the partial download so you can resume later, or delete all " +
                        "downloaded files for this game?")
                .setPositiveButton("Keep files") { _, _ ->
                    appName?.let { EpicCancelPolicy.setDeleteOnCancel(it, false) }
                    cancelDownload?.run(); cancelDownload = null
                }
                .setNegativeButton("Delete files") { _, _ ->
                    appName?.let { EpicCancelPolicy.setDeleteOnCancel(it, true) }
                    cancelDownload?.run(); cancelDownload = null
                }
                .setNeutralButton("Keep downloading", null)
                .show()
            return
        }
        startInstallInternal()
    }

    private fun startInstallInternal() {
        val an = appName ?: return
        EpicCancelPolicy.clear(an)   // fresh download → default to keep-on-cancel until the dialog says otherwise
        installBtnText = "Cancel"
            installBtnColor = 0xFFCC3333.toInt()
        progressVisible = true
        progressLabelVisible = true
        launchBtnVisible = false
        setExeBtnVisible = false
        progressLabelText = ""

        // CANCEL: install() now polls this flag inside its parallel chunk pool + assemble loop, so
        // flipping it stops the download promptly (no new chunk starts, in-flight chunks are
        // interrupted). Partial files stay on disk and resume cleanly via the #3 delta on retry.
        val cancelled = AtomicBoolean(false)
        cancelDownload = Runnable { cancelled.set(true) }

        // Publish into the cross-store Download Manager (shade notification + process kept alive).
        // Epic reports pct only \u2192 single honest bar (byte pairs left at 0).
        StoreDownloadHooks.registerDownload(
            store = Store.EPIC,
            id = an,
            name = title ?: an,
            cover = artCover,
            supportsPause = false,
            installTotal = prefs!!.getLong("epic_size_$an", 0L),
            cancel = { cancelled.set(true) },
        )

        // applicationContext + DownloadScope.io (NOT lifecycleScope): install() is a synchronous
        // blocking call, so the download now survives this Activity being destroyed / backgrounded.
        // Registry hooks are Activity-independent; only the mutableState UI writes are lifecycle-guarded.
        val appCtx = applicationContext
        DownloadScope.io.launch {
            try {
                val token = EpicCredentialStore.getValidAccessToken(appCtx)
                if (token == null) { onInstallError("Login required"); return@launch }

                if (!isDestroyed && !isFinishing) runOnUiThread { progressLabelText = "Fetching manifest\u2026" }
                val manifestJson = EpicApiClient.getManifestApiJson(token, namespace, catalogItemId, an)
                if (manifestJson == null) { onInstallError("Failed to fetch manifest"); return@launch }

                var sanitized = (title ?: "").replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
                if (sanitized.isEmpty()) sanitized = "epic_${an.hashCode()}"
                val installDir = File(File(filesDir, "imagefs/epic_games"), sanitized)
                prefs!!.edit().putString("epic_dir_$an", installDir.absolutePath).apply()

                // Feature #2 — download required(base) + the container language's files only.
                val installTags = EpicInstallTags.tagsForCurrentContainer(appCtx)

                val ok = EpicDownloadManager.install(
                    appCtx,
                    manifestJson,
                    token,
                    installDir.absolutePath,
                    installTags,
                    cancelled,
                ) { _, pct ->
                    // Freeze the card/label the moment the user hits Cancel; install() also polls
                    // `cancelled` internally and returns false promptly.
                    if (!cancelled.get()) {
                        StoreDownloadHooks.tick(Store.EPIC, an, pct)
                        if (!isDestroyed && !isFinishing) runOnUiThread {
                            if (isDestroyed || isFinishing) return@runOnUiThread
                            progressValue = pct
                            progressLabelText = "Downloading\u2026 $pct%"
                        }
                    }
                }

                if (cancelled.get()) {
                    // Delete the partial download if the user chose "Delete files" on any Cancel dialog.
                    if (EpicCancelPolicy.consumeDeleteOnCancel(an)) {
                        try { deleteDir(installDir) } catch (_: Exception) {}
                        // Also clear the install record (exe/dir/version/size prefs) so the action row
                        // recomputes to "not installed" — otherwise a stale exe pref keeps Launch/Set
                        // .exe/Uninstall showing after the files are gone.
                        EpicInstallState.purge(applicationContext, an)
                    }
                    onInstallCancelled(); return@launch
                }
                if (!ok) { onInstallError("Download failed"); return@launch }

                try {
                    val vid = JSONObject(manifestJson).optString("versionId", "")
                    if (vid.isNotEmpty()) {
                        prefs!!.edit().putString("epic_manifest_version_$an", vid).apply()
                    }
                } catch (_: Exception) {}

                val exeFiles = mutableListOf<File>()
                AmazonLaunchHelper.collectExe(installDir, exeFiles)
                if (exeFiles.isEmpty()) { onInstallError("No executable found"); return@launch }

                val lowerTitle = (title ?: "").lowercase()
                exeFiles.sortWith { a, b ->
                    AmazonLaunchHelper.scoreExe(b, lowerTitle) - AmazonLaunchHelper.scoreExe(a, lowerTitle)
                }

                // Completion NEVER shows a dialog: auto-record the best-scored exe (list already
                // sorted best-first) and finalize \u2014 mirrors the Amazon/GOG fix that unwedged the
                // 100%-stuck card when the user wasn't on the detail page. Exe choice stays
                // available via "Set .exe\u2026".
                prefs!!.edit().putString("epic_exe_$an", exeFiles[0].absolutePath).apply()
                // Detect EOS SDK presence so the library can show an "EOS" badge.
                EpicEosDetector.scanAsync(appCtx, an, installDir, null)
                onInstallComplete()
            } catch (e: Exception) {
                if (!cancelled.get()) onInstallError(e.message ?: "Unknown error")
            }
        }
    }

    private fun onInstallComplete() {
        cancelDownload = null
        // Finalize into the registry regardless of which screen is showing (fixes the 100% wedge).
        appName?.let { an ->
            prefs!!.getString("epic_dir_$an", null)?.let { dir ->
                StoreDownloadHooks.markInstalled(
                    store = Store.EPIC,
                    id = an,
                    installPath = dir,
                    bytes = prefs!!.getLong("epic_size_$an", 0L),
                )
            }
        }
        if (!isDestroyed && !isFinishing) runOnUiThread {
            if (isDestroyed || isFinishing) return@runOnUiThread
            progressVisible = false
            progressLabelVisible = false
            setResult(RESULT_REFRESH)
            refreshActionState()
        }
    }

    private fun onInstallError(msg: String) {
        cancelDownload = null
        appName?.let { StoreDownloadHooks.markFailed(Store.EPIC, it, msg) }
        if (!isDestroyed && !isFinishing) runOnUiThread {
            if (isDestroyed || isFinishing) return@runOnUiThread
            progressVisible = false
            progressLabelVisible = false
            installBtnColor = 0xFF1A73E8.toInt()
            // Reflect the true install state (a failed fresh download is NOT installed → Install-only).
            refreshActionState()
            resultBarMsg = "Error: $msg"
        }
    }

    private fun onInstallCancelled() {
        cancelDownload = null
        appName?.let { StoreDownloadHooks.markCancelled(Store.EPIC, it) }
        if (!isDestroyed && !isFinishing) runOnUiThread {
            if (isDestroyed || isFinishing) return@runOnUiThread
            progressVisible = false
            progressLabelVisible = false
            installBtnColor = 0xFF1A73E8.toInt()
            // Recompute the action row from the true install state instead of hardcoding
            // Launch/Set .exe visible: delete purged the record → Install-only; keep left it
            // in place → whatever the game's real state is.
            refreshActionState()
        }
    }

    private fun confirmUninstall() {
        AlertDialog.Builder(this)
            .setTitle("Uninstall $title?")
            .setMessage("This will delete all installed game files.")
            .setPositiveButton("Uninstall") { _, _ ->
                val an = appName ?: return@setPositiveButton
                val dir = prefs!!.getString("epic_dir_$an", null) ?: return@setPositiveButton
                lifecycleScope.launch(Dispatchers.IO) {
                    deleteDir(File(dir))
                    // Purge the FULL native install record via the canonical helper + clear the DL
                    // manager's registry/library row, so the store list and Download Manager stay in
                    // sync. Mirrors Amazon/GOG.
                    EpicInstallState.purge(applicationContext, an)
                    StoreDownloadHooks.markUninstalled(Store.EPIC, an)
                    runOnUiThread {
                        setResult(RESULT_REFRESH)
                        refreshActionState()
                        resultBarMsg = "$title uninstalled"
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pendingLaunchExe() {
        val exe = prefs!!.getString("epic_exe_$appName", null) ?: return
        // Mirror the Epic games-list Launch (StarLaunchBridge container picker). The old hardcoded
        // LandscapeLauncherMainActivity component doesn't exist in this app (com.winhub.hernandez)
        // and crashed with ActivityNotFoundException — identical to the Amazon detail bug.
        StarLaunchBridge.addToLauncher(
            this, title ?: appName ?: "Game", exe, artCover ?: "",
            StarLaunchBridge.EpicMeta(appName ?: "", namespace ?: "", catalogItemId ?: ""),
        )
    }

    private fun onSetExeClicked() {
        val dir = prefs!!.getString("epic_dir_$appName", null) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(File(dir), exeFiles)
            if (exeFiles.isEmpty()) {
                runOnUiThread { resultBarMsg = "No .exe files found" }
                return@launch
            }
            val candidates = exeFiles.map { it.absolutePath }
            runOnUiThread {
                showExePicker(candidates) { selected ->
                    if (!selected.isNullOrEmpty()) {
                        prefs!!.edit().putString("epic_exe_$appName", selected).apply()
                        refreshActionState()
                        setResult(RESULT_REFRESH)
                        resultBarMsg = "Exe set: ${File(selected).name}"
                    }
                }
            }
        }
    }

    private fun loadInstallSize() {
        val cached = prefs!!.getLong("epic_size_$appName", -1L)
        if (cached > 0) {
            sizeText = formatBytes(cached)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val token = EpicCredentialStore.getValidAccessToken(this@EpicGameDetailActivity)
            // Size reflects the language-filtered download set (Feature #2), so it matches what
            // actually installs.
            val installTags = EpicInstallTags.tagsForCurrentContainer(applicationContext)
            val size = if (token != null)
                EpicDownloadManager.fetchInstallSizeBytes(token, namespace, catalogItemId, appName, installTags)
            else -1L
            if (size > 0) prefs!!.edit().putLong("epic_size_$appName", size).apply()
            val finalSize = size
            runOnUiThread {
                sizeText = if (finalSize > 0) formatBytes(finalSize) else "Unknown"
            }
        }
    }

    private fun doCheckUpdate() {
        updateStatusText = "Checking\u2026"
        checkUpdatesEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = EpicCredentialStore.getValidAccessToken(this@EpicGameDetailActivity)
                if (token == null) {
                    runOnUiThread {
                        checkUpdatesEnabled = true
                        updateStatusText = "Login required."
                    }
                    return@launch
                }
                val manifestJson = EpicApiClient.getManifestApiJson(token, namespace, catalogItemId, appName)
                var latestVer: String? = null
                if (manifestJson != null) {
                    try { latestVer = JSONObject(manifestJson).optString("versionId", null) } catch (_: Exception) {}
                }
                val latest = latestVer
                runOnUiThread {
                    checkUpdatesEnabled = true
                    if (latest.isNullOrEmpty()) {
                        updateStatusText = "Could not reach update server."
                        return@runOnUiThread
                    }
                    val stored = prefs!!.getString("epic_manifest_version_$appName", null)
                    if (stored == null) {
                        prefs!!.edit().putString("epic_manifest_version_$appName", latest).apply()
                        updateStatusText = "Up to date \u2713"
                        updateBtnVisible = false
                    } else if (stored == latest) {
                        updateStatusText = "Up to date \u2713"
                        updateBtnVisible = false
                    } else {
                        updateStatusText = "Update available!\nInstalled: ${stored.substring(0, minOf(12, stored.length))}\u2026  \u2192  Latest: ${latest.substring(0, minOf(12, latest.length))}\u2026"
                        updateBtnVisible = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    checkUpdatesEnabled = true
                    updateStatusText = "Check failed: ${e.message}"
                }
            }
        }
    }

    /**
     * Feature #3 — verify installed files against the latest manifest and repair damage. Re-hashes
     * every language-selected file on disk; if any are missing or corrupt, re-runs the install,
     * whose delta path re-downloads exactly those files (chunk-level SHA-1 verified). Runs on
     * DownloadScope.io so it survives the Activity being backgrounded, like the install.
     */
    private fun doVerifyRepair() {
        val an = appName ?: return
        val dir = prefs!!.getString("epic_dir_$an", null)
        if (dir == null) { resultBarMsg = "Not installed"; return }
        verifyEnabled = false
        verifyStatusText = "Verifying…"
        val appCtx = applicationContext
        DownloadScope.io.launch {
            try {
                val token = EpicCredentialStore.getValidAccessToken(appCtx)
                if (token == null) {
                    runOnUiThread { verifyEnabled = true; verifyStatusText = "Login required." }
                    return@launch
                }
                val manifestJson = EpicApiClient.getManifestApiJson(token, namespace, catalogItemId, an)
                if (manifestJson == null) {
                    runOnUiThread { verifyEnabled = true; verifyStatusText = "Could not fetch manifest." }
                    return@launch
                }
                val installTags = EpicInstallTags.tagsForCurrentContainer(appCtx)
                val result = EpicDownloadManager.verifyInstall(manifestJson, token, dir, installTags) { msg, _ ->
                    if (!isDestroyed && !isFinishing) runOnUiThread { verifyStatusText = msg }
                }
                if (result == null) {
                    runOnUiThread { verifyEnabled = true; verifyStatusText = "Verify failed." }
                    return@launch
                }
                if (!result.needsRepair()) {
                    runOnUiThread {
                        verifyEnabled = true
                        verifyStatusText = "All ${result.ok}/${result.checked} files verified ✓"
                    }
                    return@launch
                }
                val damaged = result.corrupt + result.missing
                runOnUiThread { verifyStatusText = "Repairing $damaged file(s)…" }
                val ok = EpicDownloadManager.install(appCtx, manifestJson, token, dir, installTags) { _, pct ->
                    if (!isDestroyed && !isFinishing) runOnUiThread { verifyStatusText = "Repairing… $pct%" }
                }
                runOnUiThread {
                    verifyEnabled = true
                    verifyStatusText = if (ok) "Repaired $damaged file(s) ✓" else "Repair failed."
                }
            } catch (e: Exception) {
                runOnUiThread { verifyEnabled = true; verifyStatusText = "Verify error: ${e.message}" }
            }
        }
    }

    private fun dlcInstall(dlcApp: String, dlcNs: String, dlcCat: String, dlcTitle: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = EpicCredentialStore.getValidAccessToken(this@EpicGameDetailActivity)
                if (token == null) {
                    runOnUiThread { resultBarMsg = "Login required" }
                    return@launch
                }

                val manifestJson = EpicApiClient.getManifestApiJson(token, dlcNs, dlcCat, dlcApp)
                if (manifestJson == null) {
                    runOnUiThread { resultBarMsg = "Failed to fetch manifest for DLC" }
                    return@launch
                }

                var sanitized = dlcTitle.replace(Regex("[^a-zA-Z0-9 \\-_]"), "").trim()
                if (sanitized.isEmpty()) sanitized = "dlc_${dlcApp.hashCode()}"
                val installDir = File(File(filesDir, "imagefs/epic_games"), sanitized)
                prefs!!.edit().putString("epic_dir_$dlcApp", installDir.absolutePath).apply()

                val ok = EpicDownloadManager.install(
                    this@EpicGameDetailActivity,
                    manifestJson,
                    token,
                    installDir.absolutePath,
                ) { _, _ -> }
                if (!ok) {
                    runOnUiThread { resultBarMsg = "DLC download failed" }
                    return@launch
                }

                val exeFiles = mutableListOf<File>()
                AmazonLaunchHelper.collectExe(installDir, exeFiles)
                if (exeFiles.isNotEmpty()) {
                    val lowerT = dlcTitle.lowercase()
                    exeFiles.sortWith { a, b ->
                        AmazonLaunchHelper.scoreExe(b, lowerT) - AmazonLaunchHelper.scoreExe(a, lowerT)
                    }
                    prefs!!.edit().putString("epic_exe_$dlcApp", exeFiles[0].absolutePath).apply()
                }

                runOnUiThread {
                    setResult(RESULT_REFRESH)
                    refreshActionState()
                    resultBarMsg = "$dlcTitle installed"
                }
            } catch (e: Exception) {
                runOnUiThread { resultBarMsg = "DLC install error: ${e.message}" }
            }
        }
    }

    /**
     * Resolve this game's Epic save folder: prefer the auto-resolver
     * ([EpicCloudSavePaths]: container match + CloudSaveFolder token-expand, same as the
     * Save Manager Epic tab), and fall back to a manually-picked folder
     * (`epic_save_dir_<appName>`) only if resolve yields nothing.
     * Does file I/O (shortcut load + prefix walk) \u2014 MUST be called off the main thread.
     */
    private fun resolveEpicSaveDir(): File? {
        val an = appName ?: return null
        // Explicit manual pick (Browse) wins as a user override.
        prefs!!.getString("epic_save_dir_$an", null)?.let { return File(it) }
        // Otherwise auto-resolve exactly like the Save Manager Epic tab.
        val installDir = prefs!!.getString("epic_dir_$an", null)
        val container = try {
            EpicCloudSavePaths.resolveContainer(this, an, installDir)
        } catch (e: Exception) { null }
        return EpicCloudSavePaths.resolveSaveDirectory(this, an, container)
    }

    /** Populate the Cloud Saves label from the auto-resolver (off-main) when no manual folder is set. */
    private fun refreshResolvedSaveDirLabel() {
        if (appName == null) return
        Thread {
            val dir = resolveEpicSaveDir()
            runOnUiThread {
                if (dir != null && dir.exists()) {
                    cloudSaveDirText = "Auto: " + shortenPath(dir.absolutePath)
                    cloudSaveDirColor = 0xFFCCCCCC.toInt()
                }
            }
        }.start()
    }

    private fun cloudUpload() {
        cloudButtonsEnabled = false
        cloudSaveStatusText = "Resolving save folder\u2026"
        cloudSaveStatusVisible = true
        Thread {
            val dir = resolveEpicSaveDir()
            if (dir == null || !dir.exists()) {
                runOnUiThread { cloudSaveStatusText = "No save folder found for this game"; cloudButtonsEnabled = true }
                return@Thread
            }
            runOnUiThread { cloudSaveStatusText = "Preparing upload\u2026" }
            EpicCloudSaveManager.uploadSaves(this, appName!!, dir, object : EpicCloudSaveManager.Callback {
                override fun onStatus(msg: String) { runOnUiThread { cloudSaveStatusText = msg } }
                override fun onDone(msg: String) { runOnUiThread { cloudSaveStatusText = msg; cloudButtonsEnabled = true } }
                override fun onError(msg: String) { runOnUiThread { cloudSaveStatusText = "Error: $msg"; cloudButtonsEnabled = true } }
            })
        }.start()
    }

    private fun cloudDownload() {
        cloudButtonsEnabled = false
        cloudSaveStatusText = "Resolving save folder\u2026"
        cloudSaveStatusVisible = true
        Thread {
            val dir = resolveEpicSaveDir()
            if (dir == null) {
                runOnUiThread { cloudSaveStatusText = "No save folder found for this game"; cloudButtonsEnabled = true }
                return@Thread
            }
            runOnUiThread { cloudSaveStatusText = "Preparing download\u2026" }
            EpicCloudSaveManager.downloadSaves(this, appName!!, dir, object : EpicCloudSaveManager.Callback {
                override fun onStatus(msg: String) { runOnUiThread { cloudSaveStatusText = msg } }
                override fun onDone(msg: String) { runOnUiThread { cloudSaveStatusText = msg; cloudButtonsEnabled = true } }
                override fun onError(msg: String) { runOnUiThread { cloudSaveStatusText = "Error: $msg"; cloudButtonsEnabled = true } }
            })
        }.start()
    }

    private fun showExePicker(candidates: List<String>, onSelected: (String?) -> Unit) {
        val labels = candidates.map { path ->
            val f = File(path)
            val parent = f.parentFile
            (if (parent != null) "${parent.name}/${f.name}" else f.name)
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select game executable")
            .setItems(labels) { _, which -> onSelected(candidates[which]) }
            .setCancelable(false)
            .show()
    }

    private fun deleteDir(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { f -> if (f.isDirectory) deleteDir(f) else f.delete() }
        dir.delete()
    }

    private fun shortenPath(path: String): String {
        val parts = path.split("/")
        if (parts.size <= 3) return path
        return "\u2026/${parts[parts.size - 2]}/${parts[parts.size - 1]}"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.0f MB".format(bytes / 1_048_576.0)
        else -> "$bytes B"
    }
}

// Mounted on the shared Steam-style scaffold (StoreDetailScaffold): hero → name → ONE primary
// button (Install / Launch, or the read-only download fill) + ⚙ gear → pill tabs
// (Details · DLC · Cloud saves). Every former action is still reachable through the gear; the
// handlers are UNCHANGED — only the layout moved.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpicGameDetailScreen(
    appName: String,
    title: String,
    description: String,
    developer: String,
    artCover: String,
    namespace: String,
    catalogItemId: String,
    exeNameText: String,
    installBtnText: String,
    launchBtnVisible: Boolean,
    installBtnVisible: Boolean,
    setExeBtnVisible: Boolean,
    uninstallBtnVisible: Boolean,
    usesEos: Boolean,
    progressVisible: Boolean,
    progressValue: Int,
    progressLabelText: String,
    progressLabelVisible: Boolean,
    sizeText: String,
    updateStatusText: String,
    checkUpdatesEnabled: Boolean,
    updateBtnVisible: Boolean,
    verifyStatusText: String,
    verifyEnabled: Boolean,
    dlcJson: String?,
    cloudSaveDirText: String,
    cloudSaveStatusText: String,
    cloudSaveStatusVisible: Boolean,
    cloudButtonsEnabled: Boolean,
    onBack: () -> Unit,
    onLaunchClick: () -> Unit,
    onInstallClick: () -> Unit,
    onSetExeClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onCheckUpdates: () -> Unit,
    onUpdateClick: () -> Unit,
    onVerifyRepair: () -> Unit,
    onDlcInstall: (String, String, String, String) -> Unit,
    onCloudBrowse: () -> Unit,
    onCloudUpload: () -> Unit,
    onCloudDownload: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("bh_epic_prefs", 0)
    var tab by remember { mutableStateOf(0) }
    val installed = prefs.getString("epic_exe_$appName", null) != null
    val downloading = progressVisible && installBtnText == "Cancel"

    val primary = when {
        downloading -> StorePrimaryAction.Progress(
            label = if (progressLabelVisible && progressLabelText.isNotBlank()) progressLabelText else "Downloading… $progressValue%",
            fraction = progressValue / 100f,
        )
        launchBtnVisible -> StorePrimaryAction.Button("Launch", onLaunchClick)
        else -> StorePrimaryAction.Button(installBtnText, onInstallClick, enabled = installBtnVisible)
    }
    val gear = buildList {
        // onInstallClick doubles as Cancel (keep / delete dialog) while the label reads "Cancel".
        if (downloading) add(StoreGearItem("🗑", "Cancel download", danger = true, onClick = onInstallClick))
        if (setExeBtnVisible) add(StoreGearItem("🎯", "Set .exe…", onClick = onSetExeClick))
        if (installed) {
            add(StoreGearItem("🔄", "Check for updates", enabled = checkUpdatesEnabled, onClick = onCheckUpdates))
            add(StoreGearItem("🩹", "Verify / repair files", enabled = verifyEnabled, onClick = onVerifyRepair))
        }
        if (uninstallBtnVisible) add(StoreGearItem("🗑️", "Uninstall", danger = true, onClick = onUninstallClick))
    }
    val dlcArr = remember(dlcJson) {
        if (dlcJson.isNullOrEmpty() || dlcJson == "[]") null else runCatching { org.json.JSONArray(dlcJson) }.getOrNull()
    }
    val dlcCount = dlcArr?.length() ?: 0

    // Media tab: the store offer for this library game (namespace + catalogItemId) → screenshots
    // and trailers, through the shared cache so a re-open / rotation paints without a request.
    // A failed lookup is cached as a short-TTL miss (retried next open); a title with nothing is
    // remembered for hours. The tab is offered only once something is there.
    var media by remember { mutableStateOf<StoreMedia?>(null) }
    var mediaLoading by remember { mutableStateOf(true) }
    LaunchedEffect(namespace, catalogItemId) {
        if (namespace.isBlank() || catalogItemId.isBlank()) { mediaLoading = false; return@LaunchedEffect }
        media = withContext(Dispatchers.IO) {
            StoreMediaCache.get(context, Store.EPIC, catalogItemId) ?: run {
                val fetched = EpicStoreCatalog.libraryGameMedia(namespace, catalogItemId)
                (fetched ?: StoreMedia.EMPTY).also {
                    StoreMediaCache.put(context, Store.EPIC, catalogItemId, it, miss = fetched == null)
                }
            }
        }
        mediaLoading = false
    }
    val hasMedia = media?.isEmpty == false
    // Appended LAST so the Details / DLC / Cloud saves indices never move.
    val tabs = if (hasMedia) listOf("Details", "DLC", "Cloud saves", "Media") else listOf("Details", "DLC", "Cloud saves")
    if (tab >= tabs.size) tab = 0

    StoreDetailScaffold(
        onBack = onBack,
        title = title,
        storeBadge = { StoreBadge(Store.EPIC) },
        hero = {
            if (artCover.isNotEmpty()) {
                AsyncImage(
                    model = artCover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            StoreBadgeOverlay(
                showEpic = true,
                showEos = usesEos,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
        },
        subtitle = if (exeNameText.isNotEmpty()) {
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
            media?.takeIf { hasMedia }?.let { put(3, "${it.count}") }
        },
    ) {
        when (tab) {
            0 -> Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip(sizeText)
                        if (developer.isNotEmpty()) InfoChip(developer)
                        if (appName.isNotEmpty()) InfoChip("App: $appName")
                        val releaseDate = prefs.getString("epic_release_$appName", null)
                        if (!releaseDate.isNullOrEmpty()) InfoChip(formatDateStatic(releaseDate))
                        if (usesEos) InfoChip("EOS online services")
                    }
                    if (description.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        val plain = Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT).toString().trim()
                        Text(
                            text = plain,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                StoreSection(title = "Updates") {
                    if (!installed) {
                        StoreStatusText("Install the game first to check for updates.")
                    } else {
                        val displayText = if (updateStatusText.isNotEmpty()) updateStatusText
                        else {
                            val storedVer = prefs.getString("epic_manifest_version_$appName", null)
                            if (storedVer != null) "Installed: ${storedVer.substring(0, minOf(14, storedVer.length))}…"
                            else "Version not recorded — tap Check to verify"
                        }
                        StoreStatusText(displayText)
                        Spacer(Modifier.height(8.dp))
                        if (updateBtnVisible) {
                            StoreActionButton(
                                text = "Update Now",
                                onClick = onUpdateClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        StoreActionButton(
                            text = "Check for Updates",
                            onClick = onCheckUpdates,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = checkUpdatesEnabled,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (verifyStatusText.isNotEmpty()) {
                            StoreStatusText(verifyStatusText)
                            Spacer(Modifier.height(8.dp))
                        }
                        StoreActionButton(
                            text = "Verify / Repair Files",
                            onClick = onVerifyRepair,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = verifyEnabled,
                        )
                    }
                }
            }

            1 -> Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                StoreSection(title = "DLC") {
                    if (dlcArr == null) {
                        StoreStatusText("No DLCs in your library for this game")
                    } else if (dlcArr.length() == 0) {
                        StoreStatusText("No DLCs in your library for this game")
                    } else {
                        Text(
                            text = "${dlcArr.length()} DLC${if (dlcArr.length() == 1) "" else "s"} owned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        for (i in 0 until dlcArr.length()) {
                            val dlc = dlcArr.optJSONObject(i) ?: continue
                            val dlcApp = dlc.optString("app", "")
                            val dlcNs = dlc.optString("ns", "")
                            val dlcCat = dlc.optString("cat", "")
                            val dlcTitle = dlc.optString("title", "Unknown DLC")
                            val dlcInstalled = prefs.getString("epic_exe_$dlcApp", null) != null

                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dlcTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (dlcInstalled) {
                                        Text(
                                            text = "✓ Installed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = INSTALLED_GREEN,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                if (dlcApp.isNotEmpty() && dlcNs.isNotEmpty() && dlcCat.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    StoreActionButton(
                                        text = if (dlcInstalled) "Reinstall" else "Install",
                                        onClick = { onDlcInstall(dlcApp, dlcNs, dlcCat, dlcTitle) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                StoreSection(title = "Cloud Saves") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = cloudSaveDirText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        StoreActionButton(text = "Browse", onClick = onCloudBrowse)
                    }
                    if (cloudSaveStatusVisible) {
                        Spacer(Modifier.height(8.dp))
                        StoreStatusText(cloudSaveStatusText)
                    }
                    Spacer(Modifier.height(8.dp))
                    StoreActionButton(
                        text = "Upload Saves",
                        onClick = onCloudUpload,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cloudButtonsEnabled,
                    )
                    Spacer(Modifier.height(8.dp))
                    StoreActionButton(
                        text = "Download Saves",
                        onClick = onCloudDownload,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = cloudButtonsEnabled,
                    )
                }
            }

            else -> MediaTab(
                media = media,
                loading = mediaLoading,
                storeLabel = "Epic Games Store",
                onOpenVideo = { MediaPlayback.openVideo(context, it) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun formatDateStatic(iso: String): String {
    if (iso.length < 10) return iso
    val parts = iso.substring(0, 10).split("-")
    if (parts.size != 3) return iso.substring(0, 10)
    return try {
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        if (month < 1 || month > 12) return iso.substring(0, 10)
        "${months[month - 1]} $day, $year"
    } catch (_: Exception) {
        iso.substring(0, 10)
    }
}
