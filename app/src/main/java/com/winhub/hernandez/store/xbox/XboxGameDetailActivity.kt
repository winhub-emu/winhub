package com.winhub.hernandez.store.xbox

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.winhub.hernandez.R
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.store.CatalogArt
import com.winhub.hernandez.store.StarLaunchBridge
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.InfoChip
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreBadge
import com.winhub.hernandez.store.download.StoreDetailScaffold
import com.winhub.hernandez.store.download.StoreDetailState
import com.winhub.hernandez.store.download.StoreGearItem
import com.winhub.hernandez.store.download.StorePrimaryAction
import com.winhub.hernandez.store.download.StoreSection
import com.winhub.hernandez.store.download.StoreStatusText
import com.winhub.hernandez.store.download.formatDownloadSize
import com.winhub.hernandez.store.download.formatDownloadSpeed
import com.winhub.hernandez.store.download.formatEta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One Xbox PC game: Install (with a package choice when the offer has several PC packages), progress from the
 * shared Download Manager, Add to Games (a shortcut in a container of the user's choice, tagged xbox),
 * Update / verify and Uninstall. Playing goes through the shortcut: each launch prepares a licensed launch
 * view (XboxRuntime) inside the Wine session.
 */
class XboxGameDetailActivity : ComponentActivity() {
    private lateinit var game: XboxGame
    private var installed by mutableStateOf<XboxInstallState.Installed?>(null)
    private var downloading by mutableStateOf(false)
    private var progressPct by mutableStateOf(0)
    private var progressLabel by mutableStateOf("")
    private var busy by mutableStateOf<String?>(null)
    private var status by mutableStateOf<String?>(null)
    private var addToShortcuts by mutableStateOf<com.winhub.hernandez.store.compose.AddToShortcutsRequest?>(null)
    private var addResult by mutableStateOf<com.winhub.hernandez.store.compose.AddShortcutResult?>(null)
    /** Containers to pick from for a Games entry's download (opened from its launch options), or null. */
    private var downloadPicker by mutableStateOf<List<com.winhub.hernandez.container.Container>?>(null)

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        DownloadRegistry.init(this)
        val i = intent
        game = XboxGame(i.getStringExtra("id").orEmpty(), i.getStringExtra("title").orEmpty(), i.getStringExtra("hero"),
            i.getStringExtra("poster"), i.getStringExtra("publisher").orEmpty(), i.getStringExtra("developer").orEmpty(),
            i.getStringExtra("description").orEmpty(), i.getLongExtra("size", 0L), i.getStringExtra("market") ?: Xodus.market(),
            i.getStringExtra("access").orEmpty(), i.getBooleanExtra("pc", true), i.getBooleanExtra("cloud", false),
            i.getStringExtra("cloudId"))
        refresh()
        observeRegistry()
        // Opened by Download on a not-installed Games entry: pick the container, then install (the entry is
        // moved there and completed when the install finishes).
        if (intent.getStringExtra(XboxCloudShortcuts.EXTRA_SHORTCUT) != null && savedInstanceState == null &&
            XboxInstallState.get(this, game.id) == null && game.pc && game.access.isNotEmpty()) {
            StarLaunchBridge.loadContainers(this) { containers -> if (!containers.isNullOrEmpty()) downloadPicker = containers }
        }
        setContent {
            com.winhub.hernandez.ui.theme.WinlatorTheme {
            com.winhub.hernandez.store.DeckStoreTheme {
                var tab by androidx.compose.runtime.remember { mutableStateOf(0) }
                val inst = installed
                val primary = when {
                    downloading -> StorePrimaryAction.Progress(progressLabel.ifBlank { "Downloading… $progressPct%" }, progressPct / 100f)
                    busy != null -> StorePrimaryAction.Progress(busy!!, 0f)
                    inst != null -> StorePrimaryAction.Button("Add to Games", ::addToGames)
                    game.access.isEmpty() -> StorePrimaryAction.Button("Not in your library", {}, enabled = false)
                    !game.pc -> StorePrimaryAction.Button("Stream", ::stream)
                    else -> StorePrimaryAction.Button("Install", ::install)
                }
                val secondary = if (game.cloud && game.pc && game.access.isNotEmpty()) StorePrimaryAction.Button("Stream", ::stream) else null
                val gear = buildList {
                    if (inst == null && !downloading && game.cloud && game.access.isNotEmpty() && intent.getStringExtra(XboxCloudShortcuts.EXTRA_SHORTCUT) == null)
                        add(StoreGearItem("➕", "Add to Games (stream now, download later)") {
                            XboxCloudShortcuts.addNotInstalled(this@XboxGameDetailActivity, game) { ok, message ->
                                addResult = com.winhub.hernandez.store.compose.AddShortcutResult(game.title, ok, message,
                                    note = if (ok) "Not installed yet: Stream it now, or Download it from its launch options." else null)
                            }
                        })
                    if (downloading) add(StoreGearItem("🗑", "Cancel download", danger = true) { XboxDownloads.cancel(game.id) })
                    if (inst != null && !downloading) {
                        add(StoreGearItem("🔄", "Update / verify") { update(inst) })
                        add(StoreGearItem("🗑️", "Uninstall", danger = true, onClick = ::confirmUninstall))
                    }
                }
                downloadPicker?.let { containers ->
                    com.winhub.hernandez.store.compose.ContainerPickerDialog(
                        gameName = game.title,
                        containers = containers,
                        onDismiss = { downloadPicker = null },
                        note = { c -> Regex("""Proton-(\d+)""").find(c.wineVersion.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                            ?.takeIf { it < 11 }?.let { "Older Proton: Xbox support uses compatibility modules here. Proton 11 recommended." } },
                        onSelected = { container ->
                            downloadPicker = null
                            XboxCloudShortcuts.rememberDownload(this@XboxGameDetailActivity, game.id,
                                intent.getStringExtra(XboxCloudShortcuts.EXTRA_SHORTCUT)!!,
                                intent.getIntExtra(XboxCloudShortcuts.EXTRA_SHORTCUT_CONTAINER, container.id), container.id)
                            install()
                        },
                    )
                }
                addToShortcuts?.let { req ->
                    com.winhub.hernandez.store.compose.ContainerPickerDialog(
                        gameName = req.gameName,
                        containers = req.containers,
                        onDismiss = { addToShortcuts = null },
                        // Proton older than 11 lacks Windows' PropertySet; Xbox sign-in then uses a compatibility module.
                        note = { c -> Regex("""Proton-(\d+)""").find(c.wineVersion.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                            ?.takeIf { it < 11 }?.let { "Older Proton: Xbox support uses compatibility modules here. Proton 11 recommended." } },
                        onSelected = { container ->
                            addToShortcuts = null
                            StarLaunchBridge.writeShortcutAsync(this@XboxGameDetailActivity, container, req.gameName, req.exePath,
                                req.coverUrl, 0, null, "xbox:${game.id}", false) { success, message ->
                                addResult = com.winhub.hernandez.store.compose.AddShortcutResult(req.gameName, success, message)
                            }
                        },
                    )
                }
                addResult?.let { result ->
                    com.winhub.hernandez.store.compose.AddResultDialog(
                        result = result,
                        onOpenShortcuts = {
                            addResult = null
                            com.winhub.hernandez.store.compose.openShortcutsScreen(this@XboxGameDetailActivity)
                        },
                        onDismiss = { addResult = null },
                    )
                }
                StoreDetailScaffold(
                    onBack = { finish() },
                    title = game.title,
                    storeBadge = { StoreBadge(Store.XBOX) },
                    hero = {
                        val art = listOfNotNull(game.heroUrl, game.posterUrl)
                        if (art.isNotEmpty()) CatalogArt(candidates = art, title = game.title, modifier = Modifier.fillMaxSize(), aspectRatio = null)
                        else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                    },
                    subtitle = {
                        Spacer(Modifier.height(4.dp))
                        when {
                            inst != null -> StoreStatusText("Installed · ${inst.exe.substringAfterLast('/')}${if (inst.version.isNotBlank()) " · v${inst.version}" else ""}", StoreDetailState.INSTALLED)
                            game.gamePass -> StoreStatusText(if (game.pc) "Included with PC Game Pass" else "Included with Game Pass · cloud only")
                            game.owned -> StoreStatusText(if (game.pc) "Owned" else "Owned · cloud only")
                            else -> StoreStatusText("Not owned by this account")
                        }
                        status?.let { Spacer(Modifier.height(2.dp)); StoreStatusText(it, StoreDetailState.FAILED) }
                    },
                    primary = primary,
                    secondary = secondary,
                    gear = gear,
                    tabs = listOf("Details"),
                    selectedTab = tab,
                    onSelectTab = { tab = it },
                ) {
                    Column(Modifier.padding(bottom = 16.dp)) {
                        FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (game.sizeBytes > 0) InfoChip("Up to ${formatDownloadSize(game.sizeBytes)}")
                            if (game.developer.isNotBlank()) InfoChip(game.developer)
                            if (game.publisher.isNotBlank() && game.publisher != game.developer) InfoChip(game.publisher)
                            InfoChip("ID: ${game.id}")
                        }
                        if (game.description.isNotBlank()) StoreSection(title = "About") {
                            Text(game.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StoreSection(title = "How it runs") {
                            if (game.pc) Text("Install: the PC version is downloaded with your license and runs in a Wine container you pick when " +
                                "adding it to Games. Each launch checks the license with Microsoft, so play online the first time. " +
                                "Cloud saves are ${if (XboxMainActivity.cloudSaves(this@XboxGameDetailActivity)) "on" else "off"} (Xbox › Profile).",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (game.cloud) Text("Stream: plays on Microsoft's servers with Xbox Cloud Gaming — nothing to download, " +
                                "but it needs a steady connection and an Xbox plan that includes cloud play. The first stream may ask you to sign in once.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = if (game.pc) 6.dp else 0.dp))
                        }
                    }
                }
            }
            }
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        lifecycleScope.launch {
            installed = withContext(Dispatchers.IO) { XboxInstallState.get(applicationContext, game.id)?.takeIf { File(it.folder, it.exe).isFile } }
        }
    }

    private fun observeRegistry() {
        val key = "${Store.XBOX}:${game.id}"
        lifecycleScope.launch {
            DownloadRegistry.entries.collect { list ->
                val e = list.firstOrNull { it.key == key }
                if (e != null && (e.state == DownloadState.DOWNLOADING || e.state == DownloadState.PAUSED || e.state == DownloadState.QUEUED)) {
                    downloading = true
                    progressPct = e.pct
                    progressLabel = listOf(
                        if (e.installTotal > 0) "${e.pct}%  ·  ${formatDownloadSize((e.installTotal - e.installDone).coerceAtLeast(0))} to go" else "Downloading… ${e.pct}%",
                        formatDownloadSpeed(e.speedBps), formatEta(e.etaSeconds),
                    ).filter { it.isNotEmpty() }.joinToString("  ·  ")
                } else {
                    if (downloading) refresh()
                    downloading = false
                    if (e?.state == DownloadState.FAILED) status = e.error
                }
            }
        }
    }

    private fun install() {
        status = null
        busy = "Checking the PC package…"
        lifecycleScope.launch {
            val choices = withContext(Dispatchers.IO) { runCatching { XboxDownloads.resolve(applicationContext, game) } }
            busy = null
            choices.onFailure { status = it.message ?: "The Xbox package could not be resolved." }.onSuccess { list ->
                if (list.size == 1) XboxDownloads.start(applicationContext, game, list.single().contentId)
                else AlertDialog.Builder(this@XboxGameDetailActivity, R.style.StoreAlertDialogDark)
                    .setTitle("Choose the PC package")
                    .setItems(list.map { it.label }.toTypedArray()) { _, which -> XboxDownloads.start(applicationContext, game, list[which].contentId) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun stream() {
        XboxDiag.log(applicationContext, "cloud stream ${game.id}")
        startActivity(XboxCloudPlayerActivity.intent(this, game))
    }

    private fun update(inst: XboxInstallState.Installed) {
        status = null
        if (inst.contentId.isBlank()) { install(); return }
        XboxDownloads.start(applicationContext, game.copy(market = inst.market, access = inst.access.ifBlank { game.access }), inst.contentId)
    }

    private fun addToGames() {
        val inst = installed ?: return
        // The shared Compose flow (as on the Steam pages): container picker, then "Added to Shortcuts" with Open Shortcuts.
        val exe = File(inst.folder, inst.exe).path
        StarLaunchBridge.loadContainers(this) { containers ->
            addToShortcuts = com.winhub.hernandez.store.compose.AddToShortcutsRequest(game.title, exe, game.posterUrl ?: game.heroUrl, containers)
        }
    }

    private fun confirmUninstall() {
        AlertDialog.Builder(this, R.style.StoreAlertDialogDark)
            .setTitle("Uninstall ${game.title}?")
            .setMessage("The game's files are deleted. Shortcuts that point to it stop working until you install it again.")
            .setPositiveButton("Uninstall") { _, _ ->
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { XboxDownloads.uninstall(applicationContext, game.id) } }
                    r.onFailure { status = it.message }
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }
}
