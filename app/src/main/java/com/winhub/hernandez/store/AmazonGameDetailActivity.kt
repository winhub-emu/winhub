package com.winhub.hernandez.store

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winhub.hernandez.R
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadScope
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.store.download.formatDownloadSize
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
import com.winhub.hernandez.store.download.StoreDownloadHooks
import com.winhub.hernandez.store.download.StoreHero
import com.winhub.hernandez.store.download.StoreProgressBar
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
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AmazonGameDetailActivity : ComponentActivity() {

    companion object {
        const val RESULT_REFRESH = 100
        private const val TAG = "BH_AMAZON_DETAIL"

        fun formatBytes(bytes: Long): String = when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            else -> "%.0f MB".format(bytes / 1_048_576.0)
        }
    }

    private var prefs: SharedPreferences? = null

    // Extras
    private var productId: String? = null
    private var entitlementId: String? = null
    private var titleText: String? = null
    private var developer: String? = null
    private var publisher: String? = null
    private var artUrl: String? = null
    private var productSku: String? = null

    // UI state
    private var exeNameText by mutableStateOf("")
    private var exeNameVisible by mutableStateOf(false)
    private var progressVisible by mutableStateOf(false)
    private var progressValue by mutableIntStateOf(0)
    private var progressLabel by mutableStateOf("")
    private var progressLabelVisible by mutableStateOf(false)
    private var launchBtnVisible by mutableStateOf(false)
    private var launchBtnEnabled by mutableStateOf(false)
    private var installBtnVisible by mutableStateOf(true)
    private var installBtnText by mutableStateOf("Install")
    private var installBtnColor by mutableIntStateOf(0xFFFF9900.toInt())
    private var installBtnEnabled by mutableStateOf(true)
    private var setExeBtnVisible by mutableStateOf(false)
    private var uninstallBtnVisible by mutableStateOf(false)
    // Themed auto-dismiss confirmation bar. System Toasts render as an unreadable black box on
    // this ROM (targetSDK 28) — same issue Steam hit; reuse its UninstallResultBar for readable
    // uninstall/launch feedback instead of Toast.makeText.
    private var resultBarMsg by mutableStateOf<String?>(null)
    private var sizeText by mutableStateOf("Fetching\u2026")

    // Updates section state
    private var updateStatusText by mutableStateOf("")
    private var checkUpdatesEnabled by mutableStateOf(true)
    private var updateBtnVisible by mutableStateOf(false)

    // DLC state
    private var dlcJson by mutableStateOf("")

    // Cancel download
    private var cancelDownload: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.winhub.hernandez.ui.deck.isXmbHome(this)) com.winhub.hernandez.core.AppOrientation.apply(this)
        prefs = getSharedPreferences("bh_amazon_prefs", 0)

        val i = intent
        productId = i.getStringExtra("product_id")
        entitlementId = i.getStringExtra("entitlement_id")
        titleText = i.getStringExtra("title")
        developer = i.getStringExtra("developer")
        publisher = i.getStringExtra("publisher")
        artUrl = i.getStringExtra("art_url")
        productSku = i.getStringExtra("product_sku")

        if (productId == null) {
            finish()
            return
        }

        // Cross-store Download Manager (Phase A): Amazon can download without the Steam
        // foreground service ever running, so init the registry + seed the installed library
        // here (idempotent) — otherwise DownloadRegistry.libPrefs is uninitialized and this
        // download's INSTALLED row wouldn't persist to the durable library.
        DownloadRegistry.init(this)
        AmazonLibrarySync.seed(this)

        refreshActionState()
        observeRegistry()
        loadDlcData()
        loadInstallSize()
        loadUpdateStatus()

        setContent {
            StoreXmbTheme {
                DeckStoreTheme {
                Box(Modifier.fillMaxSize()) {
                AmazonGameDetailScreen(
                    artUrl = artUrl,
                    titleText = titleText,
                    developer = developer,
                    publisher = publisher,
                    productId = productId,
                    exeNameText = exeNameText,
                    exeNameVisible = exeNameVisible,
                    progressVisible = progressVisible,
                    progressValue = progressValue,
                    progressLabel = progressLabel,
                    progressLabelVisible = progressLabelVisible,
                    launchBtnVisible = launchBtnVisible,
                    launchBtnEnabled = launchBtnEnabled,
                    installBtnVisible = installBtnVisible,
                    installBtnText = installBtnText,
                    installBtnEnabled = installBtnEnabled,
                    setExeBtnVisible = setExeBtnVisible,
                    uninstallBtnVisible = uninstallBtnVisible,
                    sizeText = sizeText,
                    updateStatusText = updateStatusText,
                    checkUpdatesEnabled = checkUpdatesEnabled,
                    updateBtnVisible = updateBtnVisible,
                    dlcJson = dlcJson,
                    onBack = { finish() },
                    onInstallClick = { onInstallClicked() },
                    onSetExeClick = { onSetExeClicked() },
                    onUninstallClick = { onUninstallClicked() },
                    onLaunchClick = { onLaunchClicked() },
                    onCheckUpdatesClick = { onCheckUpdatesClick() },
                    onUpdateNowClick = { onInstallClicked() },
                    onDlcInstall = { eid, pid, dlcTitle -> startDlcInstall(eid, pid, dlcTitle) },
                )
                resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
                }
            
                }}
        }
    }

    override fun onBackPressed() {
        // Leaving the detail page no longer cancels an in-flight download: it keeps running on
        // DownloadScope with the foreground-service notification, and can still be cancelled from
        // the Download Manager (reachable by tapping that notification). This is the whole point
        // of the survive-backgrounding change — backing out mid-install used to silently abort it.
        super.onBackPressed()
    }

    // ── State refresh ─────────────────────────────────────────────────────

    /**
     * Make the detail page a live reflection of [DownloadRegistry] for THIS game. Without this,
     * opening the page while a download is live (started from the games list, or after the Activity
     * was recreated) showed "Install" even though the DL-manager card + shade notification were
     * progressing. Runs on the main dispatcher (lifecycleScope default) so the Compose state writes
     * are main-thread-safe. Mirrors GogGameDetailActivity.observeRegistry.
     */
    private fun observeRegistry() {
        val pid = productId ?: return
        val myKey = "${Store.AMAZON}:$pid"
        lifecycleScope.launch {
            DownloadRegistry.entries.collect { list ->
                val e = list.firstOrNull { it.key == myKey }
                if (e != null && (e.state == DownloadState.DOWNLOADING || e.state == DownloadState.PAUSED)) {
                    progressVisible = true
                    progressValue = e.pct
                    // Live label on the detail page, matching the Manager card. Registry-driven so
                    // it's live for a list-started / reopened download, not just this Activity's.
                    progressLabel = if (e.installTotal > 0 && e.installDone > 0)
                        "${e.pct}%  (${formatDownloadSize(e.installDone)} / ${formatDownloadSize(e.installTotal)})"
                    else "Downloading… ${e.pct}%"
                    progressLabelVisible = true
                    installBtnVisible = true
                    installBtnText = "Cancel"
                    installBtnColor = 0xFFCC3333.toInt()
                    installBtnEnabled = true
                    launchBtnEnabled = false
                    setExeBtnVisible = false
                    uninstallBtnVisible = false
                    // Route Cancel to the registry entry so it works for a list-started download —
                    // but ONLY if we don't already hold the real canceller (a locally-started
                    // download sets it in onInstallClicked); overwriting would make the registry
                    // entry's cancel (which calls cancelDownload) recurse into itself.
                    if (cancelDownload == null) cancelDownload = { e.cancel?.invoke() }
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
        val exe = prefs!!.getString("amazon_exe_$productId", null)
        val dir = prefs!!.getString("amazon_dir_$productId", null)
        val installed = exe != null

        exeNameVisible = installed
        exeNameText = if (installed) ".exe: ${File(exe).name}" else ""

        launchBtnVisible = installed
        launchBtnEnabled = installed
        installBtnVisible = !installed
        if (!installed) installBtnText = "Install"
        setExeBtnVisible = installed
        uninstallBtnVisible = dir != null
    }

    // ── Install ───────────────────────────────────────────────────────────

    private fun onInstallClicked() {
        val pid = productId ?: return
        if (installBtnText == "Cancel") {
            cancelDownload?.invoke()
            cancelDownload = null
            return
        }

        installBtnText = "Cancel"
        installBtnColor = 0xFFCC3333.toInt()
        progressVisible = true
        progressLabelVisible = true
        launchBtnEnabled = false
        setExeBtnVisible = false

        val cancelled = AtomicBoolean(false)
        cancelDownload = { cancelled.set(true) }

        val game = AmazonGame().apply {
            productId = this@AmazonGameDetailActivity.productId ?: ""
            entitlementId = this@AmazonGameDetailActivity.entitlementId ?: ""
            title = this@AmazonGameDetailActivity.titleText ?: ""
            productSku = this@AmazonGameDetailActivity.productSku ?: ""
        }

        // Run on the process-lifetime DownloadScope (NOT lifecycleScope): paired with the
        // DownloadForegroundService that StoreDownloadHooks starts, the download now survives
        // this Activity being destroyed / the app being backgrounded. Use applicationContext
        // throughout so the coroutine never pins the Activity.
        val appCtx = applicationContext
        DownloadScope.io.launch {
            val token = AmazonCredentialStore.getValidAccessToken(appCtx)
            if (token == null) {
                withContext(Dispatchers.Main) {
                    onInstallError("Login required")
                }
                return@launch
            }

            var sanitized = (titleText ?: "").replace("[^a-zA-Z0-9 \\-_]".toRegex(), "").trim()
            if (sanitized.isEmpty()) sanitized = "game_${productId.hashCode()}"
            val installDir = File(File(filesDir, "Amazon"), sanitized)
            prefs!!.edit().putString(
                "amazon_dir_$productId",
                installDir.absolutePath,
            ).apply()

            // Publish this download into the cross-store Download Manager registry. Amazon has
            // no separate compressed stage \u2014 one honest install bar (byte pair below), download
            // pair left at 0. Cancel routes to the same flag the detail page's Cancel uses.
            StoreDownloadHooks.registerDownload(
                store = Store.AMAZON,
                id = pid,
                name = titleText ?: pid,
                cover = artUrl,
                supportsPause = false,
                installTotal = prefs!!.getLong("amazon_size_$pid", 0L),
                cancel = { cancelled.set(true) },
            )

            val ok = AmazonDownloadManager.install(
                appCtx, game, token, installDir,
                { dl, total, _ ->
                    if (cancelled.get()) return@install
                    val pct = if (total > 0) (dl * 100L / total).toInt() else 0
                    // Mirror the exact figures into the DL manager (real aggregate bytes).
                    StoreDownloadHooks.tick(
                        store = Store.AMAZON,
                        id = pid,
                        pct = pct,
                        installDone = dl,
                        installTotal = total,
                    )
                    // Detail-page label mirrors the Manager card ("$pct%  (done / total)");
                    // the raw archive filename (e.g. "level23") is no longer surfaced. Guarded:
                    // this coroutine can now outlive the Activity, so only touch UI while it's live.
                    val label = if (pct <= 0) "Downloading\u2026"
                        else "$pct%  (${formatDownloadSize(dl)} / ${formatDownloadSize(total)})"
                    if (!isDestroyed && !isFinishing) runOnUiThread {
                        if (isDestroyed || isFinishing) return@runOnUiThread
                        progressValue = pct
                        progressLabel = label
                    }
                },
                { cancelled.get() },
            )

            if (cancelled.get()) {
                withContext(Dispatchers.Main) { onInstallCancelled() }
                return@launch
            }
            if (!ok) {
                withContext(Dispatchers.Main) { onInstallError("Download failed") }
                return@launch
            }

            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(installDir, exeFiles)
            if (exeFiles.isEmpty()) {
                withContext(Dispatchers.Main) { onInstallError("No executable found") }
                return@launch
            }

            val lowerTitle = (titleText ?: "").lowercase()
            exeFiles.sortWith { a, b ->
                AmazonLaunchHelper.scoreExe(b, lowerTitle) -
                    AmazonLaunchHelper.scoreExe(a, lowerTitle)
            }

            // Completion NEVER shows a dialog: auto-record the best-scored exe (list already
            // sorted best-first) and finalize. Gating markInstalled behind an exe-picker used to
            // wedge the download at 100% whenever the user wasn't on the detail page when it
            // finished (the dialog queued on a stopped Activity, so the install never finalized).
            // The exe choice, when there's more than one, now happens at Launch instead.
            prefs!!.edit()
                .putString("amazon_exe_$productId", exeFiles[0].absolutePath)
                .apply()
            withContext(Dispatchers.Main) { onInstallComplete() }
        }
    }

    private fun onInstallComplete() {
        cancelDownload = null
        progressVisible = false
        progressLabelVisible = false
        // Terminal success → INSTALLED (persisted to the durable library so it survives
        // process death in the DL manager's Library section).
        productId?.let { pid ->
            val dir = prefs!!.getString("amazon_dir_$pid", null)
            if (dir != null) {
                StoreDownloadHooks.markInstalled(
                    store = Store.AMAZON,
                    id = pid,
                    installPath = dir,
                    bytes = prefs!!.getLong("amazon_size_$pid", 0L),
                )
            }
        }
        setResult(RESULT_REFRESH)
        refreshActionState()
    }

    private fun onInstallError(msg: String) {
        cancelDownload = null
        progressVisible = false
        progressLabelVisible = false
        installBtnText = "Install"
        installBtnColor = 0xFFFF9900.toInt()
        launchBtnEnabled = true
        setExeBtnVisible = true
        productId?.let { StoreDownloadHooks.markFailed(Store.AMAZON, it, msg) }
        // applicationContext: this handler can run after the Activity is gone (download now
        // outlives it), and the registry/notification updates above must still happen.
        resultBarMsg = "Error: $msg"
    }

    private fun onInstallCancelled() {
        cancelDownload = null
        progressVisible = false
        progressLabelVisible = false
        installBtnText = "Install"
        installBtnColor = 0xFFFF9900.toInt()
        launchBtnEnabled = true
        setExeBtnVisible = true
        productId?.let { StoreDownloadHooks.markCancelled(Store.AMAZON, it) }
    }

    // ── Set .exe ──────────────────────────────────────────────────────────

    private fun onSetExeClicked() {
        val dir = prefs!!.getString("amazon_dir_$productId", null) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(File(dir), exeFiles)
            if (exeFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    resultBarMsg = "No .exe files found"
                }
                return@launch
            }
            val candidates = exeFiles.map { it.absolutePath }
            withContext(Dispatchers.Main) {
                showExePicker(candidates) { selected ->
                    if (!selected.isNullOrEmpty()) {
                        prefs!!.edit()
                            .putString("amazon_exe_$productId", selected)
                            .apply()
                        setResult(RESULT_REFRESH)
                        refreshActionState()
                        resultBarMsg = "Exe set: ${File(selected).name}"
                    }
                }
            }
        }
    }

    // ── Uninstall ─────────────────────────────────────────────────────────

    private fun onUninstallClicked() {
        val dir = prefs!!.getString("amazon_dir_$productId", null) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            deleteDir(File(dir))
            // Purge the FULL native install record (exe/dir/manifest-version/size) via the one
            // canonical helper so the store list, detail page and DL manager all agree — not
            // just exe+dir, which left stale manifest/size keys behind.
            productId?.let { pid ->
                AmazonInstallState.purge(applicationContext, pid)
                // Clear the DL manager's Library row too (files + prefs are gone).
                StoreDownloadHooks.markUninstalled(Store.AMAZON, pid)
            }
            withContext(Dispatchers.Main) {
                setResult(RESULT_REFRESH)
                refreshActionState()
                loadUpdateStatus()   // clear the stale "Installed: v…" line now that prefs are purged
                resultBarMsg = "$titleText uninstalled"
            }
        }
    }

    // ── Launch ────────────────────────────────────────────────────────────

    private fun onLaunchClicked() {
        val dir = prefs!!.getString("amazon_dir_$productId", null) ?: return
        val name = titleText ?: productId ?: "Game"
        // The exe choice happens HERE (not at install completion): scan the install dir, and if
        // there's more than one candidate let the user pick before the container picker. Exactly
        // one → straight to the container picker. Uses the same working StarLaunchBridge flow the
        // games-list Launch uses (the old hardcoded LandscapeLauncher component doesn't exist in
        // this app and crashed with ActivityNotFoundException).
        lifecycleScope.launch(Dispatchers.IO) {
            val exeFiles = mutableListOf<File>()
            AmazonLaunchHelper.collectExe(File(dir), exeFiles)
            if (exeFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    resultBarMsg = "No .exe found in install directory"
                }
                return@launch
            }
            val lowerTitle = name.lowercase()
            exeFiles.sortWith { a, b ->
                AmazonLaunchHelper.scoreExe(b, lowerTitle) - AmazonLaunchHelper.scoreExe(a, lowerTitle)
            }
            val candidates = exeFiles.map { it.absolutePath }
            withContext(Dispatchers.Main) {
                if (candidates.size == 1) {
                    StarLaunchBridge.addToLauncher(this@AmazonGameDetailActivity, name, candidates[0], artUrl, "amazon", true)
                } else {
                    showExePicker(candidates, cancelable = true) { chosen ->
                        runOnUiThread {
                            StarLaunchBridge.addToLauncher(this@AmazonGameDetailActivity, name, chosen, artUrl, "amazon", true)
                        }
                    }
                }
            }
        }
    }

    // ── Updates ───────────────────────────────────────────────────────────

    private fun loadUpdateStatus() {
        val storedVer = prefs!!.getString("amazon_manifest_version_$productId", null)
        val installed = prefs!!.getString("amazon_exe_$productId", null) != null
        updateStatusText = if (storedVer != null) {
            "Installed: v${storedVer.take(12)}\u2026"
        } else if (installed) {
            "Version not recorded \u2014 tap Check to verify"
        } else {
            "Install the game first to check for updates."
        }
    }

    private fun onCheckUpdatesClick() {
        updateStatusText = "Checking\u2026"
        checkUpdatesEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = AmazonCredentialStore.getValidAccessToken(this@AmazonGameDetailActivity)
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        checkUpdatesEnabled = true
                        updateStatusText = "Login required."
                    }
                    return@launch
                }
                val spec = AmazonApiClient.getGameDownload(token, entitlementId ?: "")
                val latestVer = spec?.versionId
                withContext(Dispatchers.Main) {
                    checkUpdatesEnabled = true
                    if (latestVer.isNullOrEmpty()) {
                        updateStatusText = "Could not reach update server."
                        return@withContext
                    }
                    val stored = prefs!!.getString(
                        "amazon_manifest_version_$productId", null,
                    )
                    if (stored == null) {
                        prefs!!.edit()
                            .putString("amazon_manifest_version_$productId", latestVer)
                            .apply()
                        updateStatusText = "Up to date \u2713"
                        updateBtnVisible = false
                    } else if (stored == latestVer) {
                        updateStatusText = "Up to date \u2713"
                        updateBtnVisible = false
                    } else {
                        updateStatusText = "Update available!\n" +
                            "Installed: v${stored.take(12)}\u2026 " +
                            " \u2192  Latest: v${latestVer.take(12)}\u2026"
                        updateBtnVisible = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    checkUpdatesEnabled = true
                    updateStatusText = "Check failed: ${e.message}"
                }
            }
        }
    }

    // ── DLC ───────────────────────────────────────────────────────────────

    private fun loadDlcData() {
        dlcJson = productId?.let {
            prefs!!.getString("amazon_dlcs_$it", null)
        } ?: ""
    }

    private fun startDlcInstall(dlcEid: String, dlcPid: String, dlcTitle: String) {
        val dlcGame = AmazonGame().apply {
            entitlementId = dlcEid
            productId = if (dlcPid.isEmpty()) dlcEid else dlcPid
            title = dlcTitle
        }
        val pidKey = dlcGame.productId

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = AmazonCredentialStore.getValidAccessToken(
                    this@AmazonGameDetailActivity,
                )
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        setResult(RESULT_REFRESH)
                        refreshActionState()
                    }
                    return@launch
                }
                var sanitized = dlcTitle.replace("[^a-zA-Z0-9 \\-_]".toRegex(), "").trim()
                if (sanitized.isEmpty()) sanitized = "dlc_${dlcEid.hashCode()}"
                val installDir = File(File(filesDir, "Amazon"), sanitized)
                prefs!!.edit()
                    .putString("amazon_dir_$pidKey", installDir.absolutePath)
                    .apply()

                val ok = AmazonDownloadManager.install(
                    this@AmazonGameDetailActivity, dlcGame, token, installDir,
                    { _, _, _ -> },
                    { false },
                )

                if (!ok) {
                    withContext(Dispatchers.Main) {
                        setResult(RESULT_REFRESH)
                        refreshActionState()
                    }
                    return@launch
                }

                val exeFiles = mutableListOf<File>()
                AmazonLaunchHelper.collectExe(installDir, exeFiles)
                if (exeFiles.isNotEmpty()) {
                    val lowerT = dlcTitle.lowercase()
                    exeFiles.sortWith { a, b ->
                        AmazonLaunchHelper.scoreExe(b, lowerT) -
                            AmazonLaunchHelper.scoreExe(a, lowerT)
                    }
                    prefs!!.edit()
                        .putString("amazon_exe_$pidKey", exeFiles[0].absolutePath)
                        .apply()
                }

                withContext(Dispatchers.Main) {
                    setResult(RESULT_REFRESH)
                    loadDlcData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setResult(RESULT_REFRESH)
                    loadDlcData()
                }
            }
        }
    }

    // ── Size ──────────────────────────────────────────────────────────────

    private fun loadInstallSize() {
        val cached = prefs!!.getLong("amazon_size_$productId", -1L)
        if (cached > 0) {
            sizeText = formatBytes(cached)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val token = AmazonCredentialStore.getValidAccessToken(
                this@AmazonGameDetailActivity,
            )
            val size = if (token != null && !entitlementId.isNullOrEmpty()) {
                AmazonDownloadManager.fetchInstallSizeBytes(token, entitlementId!!)
            } else -1L
            if (size > 0) {
                prefs!!.edit().putLong("amazon_size_$productId", size).apply()
            }
            val finalSize = size
            withContext(Dispatchers.Main) {
                sizeText = if (finalSize > 0) formatBytes(finalSize) else "Unknown"
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun showExePicker(
        candidates: List<String>,
        cancelable: Boolean = false,
        onSelected: (String) -> Unit,
    ) {
        val labels = candidates.map { path ->
            val f = File(path)
            val parent = f.parentFile
            if (parent != null) "${parent.name}/${f.name}" else f.name
        }.toTypedArray()

        // Themed (StoreAlertDialogDark) so it matches the dark app instead of a white light dialog.
        android.app.AlertDialog.Builder(this, R.style.StoreAlertDialogDark)
            .setTitle("Select game executable")
            .setItems(labels) { _, which ->
                lifecycleScope.launch(Dispatchers.IO) {
                    onSelected(candidates[which])
                }
            }
            .setCancelable(cancelable)
            .show()
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

// ─── Composable Screen ─────────────────────────────────────────────────────
//
// Mounted on the shared Steam-style scaffold (StoreDetailScaffold): hero → name → ONE primary
// button (Install / Launch, or the read-only download fill) + ⚙ gear → pill tabs
// (Details · DLC). Every former action is still reachable through the gear; the handlers are
// UNCHANGED — only the layout moved.

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmazonGameDetailScreen(
    artUrl: String?,
    titleText: String?,
    developer: String?,
    publisher: String?,
    productId: String?,
    exeNameText: String,
    exeNameVisible: Boolean,
    progressVisible: Boolean,
    progressValue: Int,
    progressLabel: String,
    progressLabelVisible: Boolean,
    launchBtnVisible: Boolean,
    launchBtnEnabled: Boolean,
    installBtnVisible: Boolean,
    installBtnText: String,
    installBtnEnabled: Boolean,
    setExeBtnVisible: Boolean,
    uninstallBtnVisible: Boolean,
    sizeText: String,
    updateStatusText: String,
    checkUpdatesEnabled: Boolean,
    updateBtnVisible: Boolean,
    dlcJson: String,
    onBack: () -> Unit,
    onInstallClick: () -> Unit,
    onSetExeClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onLaunchClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit,
    onUpdateNowClick: () -> Unit,
    onDlcInstall: (eid: String, pid: String, title: String) -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val downloading = progressVisible && installBtnText == "Cancel"
    val installed = launchBtnVisible

    val primary = when {
        downloading -> StorePrimaryAction.Progress(
            label = if (progressLabelVisible && progressLabel.isNotBlank()) progressLabel else "Downloading… $progressValue%",
            fraction = progressValue / 100f,
        )
        installed -> StorePrimaryAction.Button("Launch", onLaunchClick, enabled = launchBtnEnabled)
        else -> StorePrimaryAction.Button(installBtnText, onInstallClick, enabled = installBtnVisible && installBtnEnabled)
    }
    val gear = buildList {
        // onInstallClick doubles as Cancel while the label reads "Cancel".
        if (downloading) add(StoreGearItem("🗑", "Cancel download", danger = true, onClick = onInstallClick))
        if (setExeBtnVisible) add(StoreGearItem("🎯", "Set .exe…", onClick = onSetExeClick))
        if (installed) add(StoreGearItem("🔄", "Check for updates", enabled = checkUpdatesEnabled, onClick = onCheckUpdatesClick))
        if (updateBtnVisible) add(StoreGearItem("⬆️", "Update now", onClick = onUpdateNowClick))
        if (uninstallBtnVisible) add(StoreGearItem("🗑️", "Uninstall", danger = true, onClick = onUninstallClick))
    }
    val dlcCount = remember(dlcJson) {
        if (dlcJson.isEmpty() || dlcJson == "[]") 0 else runCatching { org.json.JSONArray(dlcJson).length() }.getOrDefault(0)
    }
    // Media tab: Amazon ships screenshots / trailers with the entitlement, so this is a pure
    // library-cache read (no request). Null until a sync has recorded media for this title —
    // then the tab simply is not offered. Parsed off the main thread: the cache is one JSON
    // array for the whole library.
    var media by remember { mutableStateOf<StoreMedia?>(null) }
    LaunchedEffect(productId) {
        media = if (productId.isNullOrEmpty()) null
        else withContext(Dispatchers.IO) { AmazonLibrarySync.cachedMedia(context, productId) }
    }
    val hasMedia = media?.isEmpty == false
    // Appended LAST so the Details / DLC indices never move.
    val tabs = if (hasMedia) listOf("Details", "DLC", "Media") else listOf("Details", "DLC")
    if (tab >= tabs.size) tab = 0
    // Poster for the hero when the library sync has resolved one; the square icon otherwise.
    val heroCandidates = remember(productId, artUrl) {
        listOfNotNull(
            productId?.let { AmazonLibraryRepo.poster(context, it) },
            artUrl?.takeIf { it.isNotBlank() },
        )
    }

    StoreDetailScaffold(
        onBack = onBack,
        title = titleText ?: "",
        storeBadge = { StoreBadge(Store.AMAZON) },
        hero = {
            if (heroCandidates.isNotEmpty()) {
                CatalogArt(
                    candidates = heroCandidates,
                    title = titleText ?: "",
                    modifier = Modifier.fillMaxSize(),
                    aspectRatio = null,
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
            media?.takeIf { hasMedia }?.let { put(2, "${it.count}") }
        },
    ) {
        when (tab) {
            0 -> Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoChip(sizeText)
                        if (!developer.isNullOrEmpty()) InfoChip(developer)
                        if (!publisher.isNullOrEmpty() && publisher != developer) InfoChip(publisher)
                        if (!productId.isNullOrEmpty()) {
                            val dot = productId.lastIndexOf('.')
                            val shortId = if (dot in 0 until productId.length - 1) productId.substring(dot + 1) else productId
                            InfoChip("ID: $shortId")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                StoreSection(title = "Updates") {
                    AmazonUpdatesContent(
                        updateStatusText = updateStatusText,
                        checkUpdatesEnabled = checkUpdatesEnabled,
                        updateBtnVisible = updateBtnVisible,
                        onCheckUpdatesClick = onCheckUpdatesClick,
                        onUpdateNowClick = onUpdateNowClick,
                    )
                }
            }

            1 -> Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                StoreSection(title = "DLC") {
                    AmazonDlcContent(
                        dlcJson = dlcJson,
                        onDlcInstall = onDlcInstall,
                    )
                }
            }

            else -> MediaTab(
                media = media,
                loading = false,
                storeLabel = "Amazon Games",
                onOpenVideo = { MediaPlayback.openVideo(context, it) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Store sections ────────────────────────────────────────────────────────

@Composable
private fun AmazonUpdatesContent(
    updateStatusText: String,
    checkUpdatesEnabled: Boolean,
    updateBtnVisible: Boolean,
    onCheckUpdatesClick: () -> Unit,
    onUpdateNowClick: () -> Unit,
) {
    // Not installed yet — just the muted hint, no buttons.
    if (updateStatusText.startsWith("Install the game")) {
        StoreStatusText(updateStatusText)
        return
    }

    StoreStatusText(updateStatusText)
    Spacer(Modifier.height(8.dp))
    if (updateBtnVisible) {
        StoreActionButton(
            text = "Update Now",
            onClick = onUpdateNowClick,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
    StoreActionButton(
        text = "Check for Updates",
        onClick = onCheckUpdatesClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = checkUpdatesEnabled,
    )
}

@Composable
private fun AmazonDlcContent(
    dlcJson: String,
    onDlcInstall: (eid: String, pid: String, title: String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bh_amazon_prefs", 0) }

    val dlcArr = remember(dlcJson) {
        if (dlcJson.isEmpty() || dlcJson == "[]") null
        else runCatching { org.json.JSONArray(dlcJson) }.getOrNull()
    }
    val parseError = remember(dlcJson) {
        dlcJson.isNotEmpty() && dlcJson != "[]" &&
            runCatching { org.json.JSONArray(dlcJson); false }.getOrDefault(true)
    }

    if (parseError) {
        StoreStatusText("Error reading DLC data")
        return
    }
    if (dlcArr == null || dlcArr.length() == 0) {
        StoreStatusText("No DLCs in your library for this game")
        return
    }

    Text(
        text = "${dlcArr.length()} DLC${if (dlcArr.length() == 1) "" else "s"} owned",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )

    for (i in 0 until dlcArr.length()) {
        val dlc = dlcArr.optJSONObject(i) ?: continue
        val dlcEid = dlc.optString("eid", "")
        val dlcPid = dlc.optString("pid", "")
        val dlcTitleText = dlc.optString("title", "Unknown DLC")

        val dlcInstalled = dlcPid.isNotEmpty() &&
            prefs.getString("amazon_exe_$dlcPid", null) != null

        var dlcStatusText by remember { mutableStateOf("") }
        var dlcBtnText by remember {
            mutableStateOf(if (dlcInstalled) "Reinstall" else "Install")
        }

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
                    text = dlcTitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (dlcInstalled) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.bodyMedium,
                        color = INSTALLED_GREEN,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (dlcStatusText.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                StoreStatusText(dlcStatusText)
            }

            if (dlcEid.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                StoreActionButton(
                    text = dlcBtnText,
                    onClick = {
                        if (dlcBtnText == "Downloading…") return@StoreActionButton
                        dlcBtnText = "Downloading…"
                        dlcStatusText = "Starting…"
                        onDlcInstall(dlcEid, dlcPid, dlcTitleText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = dlcBtnText != "Downloading…",
                )
            }
        }
    }
}
