package com.winhub.hernandez.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.Downloader
import com.winhub.hernandez.contentdialog.VegasTierPresets
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.theme.Surface as SurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import androidx.compose.ui.text.style.TextOverflow

/**
 * Holds info about one downloadable vegas release from GitHub.
 */
private data class VegasRelease(
    val tagName: String,
    val displayName: String,
    val wcpAssetUrl: String?,
    val rawZipAssetUrl: String?,
    val configAssetUrl: String?,
    val body: String?,
)

/**
 * Composable bottom-sheet-style dialog listing available VEGAS releases
 * from isygold/vegas-releases. Downloads + installs via ContentsManager.
 */
@Composable
fun VegasDownloadSheet(
    onDismiss: () -> Unit,
    onContentChanged: () -> Unit,
) {
    val context = LocalContext.current
    val cm = remember { ContentsManager(context) }
    val scope = rememberCoroutineScope()

    var releases by remember { mutableStateOf<List<VegasRelease>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var downloadingTag by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var installing by remember { mutableStateOf(false) }
    var notesDialogTag by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    // Fetch releases from GitHub API — #9 retryable popup
    LaunchedEffect(retryKey) {
        isLoading = true
        errorMsg = null
        releases = emptyList()
        val json = withContext(Dispatchers.IO) {
            Downloader.downloadString("https://api.github.com/repos/isygold/vegas-releases/releases")
        }
        if (json != null) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<VegasRelease>()
                for (i in 0 until arr.length()) {
                    val rel = arr.getJSONObject(i)
                    val tag = rel.getString("tag_name")
                    val name = rel.optString("name", tag)
                    val body = rel.optString("body", "").trim().takeIf { it.isNotBlank() }
                    var wcpUrl: String? = null
                    var zipUrl: String? = null
                    var confUrl: String? = null
                    val assets = rel.optJSONArray("assets")
                    if (assets != null) {
                        for (j in 0 until assets.length()) {
                            val a = assets.getJSONObject(j)
                            val aname = a.getString("name")
                            if (aname.startsWith("vegas-") && aname.endsWith(".wcp")) {
                                wcpUrl = a.getString("browser_download_url")
                            } else if (aname.startsWith("dxvk-") && aname.endsWith(".zip")) {
                                zipUrl = a.getString("browser_download_url")
                            } else if (aname.endsWith(".conf") &&
                                       (aname.startsWith("vegas-config-") || aname == "dxvk.conf")) {
                                // Config is shipped ALONGSIDE the wcp (release asset), never inside it
                                confUrl = a.getString("browser_download_url")
                            }
                        }
                    }
                    if (wcpUrl != null) {
                        list.add(VegasRelease(tag, name, wcpUrl, zipUrl, confUrl, body))
                    }
                }
                releases = list
                if (list.isEmpty()) errorMsg = "No releases found — check GitHub status or retry."
            } catch (e: Exception) {
                errorMsg = "Failed to parse releases: ${e.message} — tap Retry."
            }
        } else {
            errorMsg = "Failed to fetch releases from GitHub (rate limit 60/h or no network) — tap Retry."
        }
        isLoading = false
    }

    // Installing overlay
    if (installing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Installing VEGAS\u2026", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }

    // Error dialog — #9 popup with Retry
    errorMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text("Download failed", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(msg, color = MaterialTheme.colorScheme.onSurface) },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { errorMsg = null; retryKey++ }) { Text("Retry") } }
        )
    }

    // Main dialog
    AlertDialog(
        onDismissRequest = { if (downloadingTag == null && !installing) onDismiss() },
        title = { Text("VEGAS Downloads", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (releases.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (errorMsg != null) "Could not load releases." else "No releases available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (downloadingTag != null) {
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(releases, key = { it.tagName }) { release ->
                        val isDownloading = downloadingTag == release.tagName
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
Column(modifier = Modifier.weight(1f)) {
                            Text(release.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(release.tagName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val verKey = release.tagName.removePrefix("vegas-")
                            val liveNotes = release.body
                            val bundledNotes = VegasTierPresets.BUNDLED_NOTES[verKey]
                            val notes = liveNotes ?: bundledNotes?.joinToString("\n")
                            if (notes != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (liveNotes != null) "\u25CF" else "\u25D0",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (liveNotes != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    TextButton(onClick = {
                                        notesDialogTag = release.tagName
                                    }) {
                                        Text("What's new", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(
                                    notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { notesDialogTag = release.tagName },
                                )
                            }
                        }
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = {
                                        val url = release.wcpAssetUrl ?: return@IconButton
                                        downloadingTag = release.tagName
                                        downloadProgress = 0f
                                        scope.launch {
                                            val uri = withContext(Dispatchers.IO) {
                                                downloadWcp(context, url, release.tagName) { progress ->
                                                    downloadProgress = progress
                                                }
                                            }
                                            downloadingTag = null
                                            if (uri != null) {
                                                installing = true
                                                installWcp(context, cm, uri) { ok, profile ->
                                                    installing = false
                                                    if (ok) {
                                                        cm.syncContents()
                                                        // Config is shipped ALONGSIDE the wcp (same release asset),
                                                        // never inside it — fetch it on the same tap and park it at
                                                        // <contentDir>/VEGAS/configs/. Record provenance (release
                                                        // tag + real asset name) in the .provenance.json sidecar so
                                                        // the stock prober never has to guess the asset shape.
                                                        val confUrl = release.configAssetUrl
                                                        val confName = profile?.verName
                                                        scope.launch {
                                                            withContext(Dispatchers.IO) {
                                                                if (confUrl != null && confName != null) {
                                                                    val assetName = confUrl.substringAfterLast('/', confUrl)
                                                                    val vegasDir = ContentsManager.getContentTypeDir(
                                                                        context, ContentProfile.ContentType.CONTENT_TYPE_VEGAS)
                                                                    val confDir = File(vegasDir, "configs")
                                                                    if (!confDir.exists()) confDir.mkdirs()
                                                                    val parked = File(confDir, "$confName.conf")
                                                                    if (Downloader.downloadFile(confUrl, parked, null)) {
                                                                        recordStockProvenance(
                                                                            confDir, confName,
                                                                            release.tagName, assetName, confUrl,
                                                                        )
                                                                    }
                                                                } else if (confName != null) {
                                                                    // Release ships NO config asset (repo reality: 2 of 5 builds
                                                                    // none) — clear any stale parked config from an earlier tag of
                                                                    // the same verName so the prober never surfaces a baseline
                                                                    // that does not belong to the installed build.
                                                                    val vegasDir = ContentsManager.getContentTypeDir(
                                                                        context, ContentProfile.ContentType.CONTENT_TYPE_VEGAS)
                                                                    val confDir = File(vegasDir, "configs")
                                                                    File(confDir, "$confName.conf").delete()
                                                                    removeStockProvenance(confDir, confName)
                                                                }
                                                            }
                                                            onContentChanged()
                                                            onDismiss()
                                                        }
                                                    } else {
                                                        errorMsg = "Install failed."
                                                    }
                                                }
                                            } else {
                                                errorMsg = "Download failed."
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (downloadingTag == null && !installing) onDismiss() }) { Text("Close") } },
    )

    // Full-body release notes dialog — the inline preview stays at 2 lines by design
    val notesTag = notesDialogTag
    if (notesTag != null) {
        val release = releases.firstOrNull { it.tagName == notesTag }
        val verKey = notesTag.removePrefix("vegas-")
        val notes = release?.body ?: VegasTierPresets.BUNDLED_NOTES[verKey]?.joinToString("\n")
        if (notes != null) {
            AlertDialog(
                onDismissRequest = { notesDialogTag = null },
                title = {
                    Text(
                        "What's new — ${release?.displayName ?: notesTag}",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                text = {
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = { TextButton(onClick = { notesDialogTag = null }) { Text("Close") } },
            )
        } else {
            notesDialogTag = null
        }
    }
}

/** configs/.provenance.json — verName -> {tag, assetName, url, parkedAt}. Written by the
 *  downloader at park time so the stock prober (DXVKConfigDialog.loadVegasStockSources)
 *  never has to guess the asset shape from the filename. */
private fun provenanceFile(confDir: File): File = File(confDir, ".provenance.json")

private fun recordStockProvenance(
    confDir: File,
    verName: String,
    tag: String,
    assetName: String,
    url: String,
) {
    try {
        val f = provenanceFile(confDir)
        val obj = if (f.exists()) JSONObject(f.readText()) else JSONObject()
        obj.put(
            verName,
            JSONObject()
                .put("tag", tag)
                .put("assetName", assetName)
                .put("url", url)
                .put("parkedAt", System.currentTimeMillis()),
        )
        f.writeText(obj.toString())
    } catch (e: Exception) {
        // sidecar is best-effort; the parked file itself remains valid
    }
}

private fun removeStockProvenance(confDir: File, verName: String) {
    try {
        val f = provenanceFile(confDir)
        if (!f.exists()) return
        val obj = JSONObject(f.readText())
        obj.remove(verName)
        if (obj.length() == 0) f.delete() else f.writeText(obj.toString())
    } catch (e: Exception) {
        // best-effort cleanup only
    }
}

/** Download a .wcp file to cache dir and return a content:// URI. */
private fun downloadWcp(context: Context, url: String, tag: String, onProgress: ((Float) -> Unit)? = null): Uri? {
    val f = File(context.cacheDir, "vegas_${tag}.wcp")
    val listener = if (onProgress != null) object : Downloader.ProgressListener {
        override fun onProgress(fraction: Float) { onProgress(fraction) }
    } else null
    return if (Downloader.downloadFile(url, f, listener)) Uri.fromFile(f) else null
}

/** Install a .wcp content package via ContentsManager; reports the installed profile
 *  (needed to name the alongside config file after the package's verName). */
private fun installWcp(
    context: Context,
    cm: ContentsManager,
    uri: Uri,
    onDone: (Boolean, ContentProfile?) -> Unit,
) {
    val activity = context.findActivity()
    if (activity == null) {
        onDone(false, null)
        return
    }
    Executors.newSingleThreadExecutor().execute {
        try {
            cm.extraContentFile(uri, object : ContentsManager.OnInstallFinishedCallback {
                var phase = 0
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    activity.runOnUiThread { onDone(false, null) }
                }
                override fun onSucceed(profile: ContentProfile) {
                    try {
                        if (phase == 0) {
                            phase = 1
                            cm.finishInstallContent(profile, this)
                        } else {
                            activity.runOnUiThread { onDone(true, profile) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        activity.runOnUiThread { onDone(false, null) }
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            activity.runOnUiThread { onDone(false, null) }
        }
    }
}

/**
 * Fetches stock VEGAS config files for ALREADY-INSTALLED builds whose release
 * shipped a .conf asset that never got parked (older install, failed side-fetch).
 * Same source and parking rules as the download sheet's install path: the .conf
 * is a release asset alongside the .wcp (never inside it); it lands at
 * <contentDir>/VEGAS/configs/<verName>.conf with provenance recorded so the
 * stock prober resolves it exactly like a sheet-installed one.
 */
internal object VegasStockConfigFetcher {
    private const val RELEASES_URL = "https://api.github.com/repos/isygold/vegas-releases/releases"

    /** One release that ships (or could ship) a stock .conf asset. */
    data class ReleaseConf(
        val tag: String,
        val date: String,
        val confName: String?,   // null = release ships no config asset
        val confUrl: String?,
        val verNames: List<String>, // verNames derived from this release's wcp assets
    )

    /**
     * Full feed listing for the inline config-download sheet: every release with its
     * date, wcp-derived verNames and .conf asset (when present) — mirroring what the
     * build sheet shows so users can pick per version instead of guessing.
     */
    fun listReleaseConfigs(): List<ReleaseConf> {
        val body = Downloader.downloadString(RELEASES_URL) ?: return emptyList()
        val out = mutableListOf<ReleaseConf>()
        runCatching {
            val arr = org.json.JSONArray(body)
            for (i in 0 until arr.length()) {
                val rel = arr.optJSONObject(i) ?: continue
                var confName: String? = null
                var confUrl: String? = null
                val wcps = mutableListOf<String>()
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val aname = a.optString("name")
                    val url = a.optString("browser_download_url")
                    if (url.isEmpty()) continue
                    when {
                        aname.endsWith(".conf") &&
                            (aname.startsWith("vegas-config-") || aname == "dxvk.conf") -> {
                            confName = aname; confUrl = url
                        }
                        aname.endsWith(".wcp") ->
                            wcps += aname.removePrefix("vegas-").removeSuffix(".wcp")
                    }
                }
                out += ReleaseConf(
                    tag = rel.optString("tag_name", ""),
                    date = rel.optString("published_at", "").take(10),
                    confName = confName,
                    confUrl = confUrl,
                    verNames = wcps,
                )
            }
        }
        return out
    }

    /** Result of a park attempt. */
    sealed class ParkResult {
        data class Ok(val parkedAs: String) : ParkResult()
        data class Fail(val reason: String) : ParkResult()
    }

    /**
     * Download + park one release's config. Target resolution order:
     *   1. this release's own wcp-derived verNames that are INSTALLED;
     *   2. installed verNames sharing the release's numeric version prefix
     *      (tag "v2.7.3-vegas" → "2.7.3", so hash-renamed installs match);
     *   3. any installed build (single-install devices: intent is obvious);
     *   4. fall back to derived verNames (orphan file — pairs on future install).
     * Parking under an installed name is what makes loadVegasStockSources pair
     * the config into the stock dropdown immediately.
     */
    fun park(context: android.content.Context, rel: ReleaseConf): ParkResult {
        if (rel.confUrl == null) return ParkResult.Fail("release ships no config asset")
        val assetName = rel.confName ?: rel.confUrl.substringAfterLast('/', rel.confUrl)
        return runCatching {
            val cm = ContentsManager(context)
            cm.syncContents()
            val installed = cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VEGAS)
                ?.mapNotNull { p -> p.verName?.removePrefix("vegas-") }.orEmpty()

            val prefix = rel.tag.removePrefix("v").substringBefore('-')
            val targets = sequence {
                yieldAll(rel.verNames.filter { it in installed })
                yieldAll(installed.filter { it.startsWith(prefix) })
                if (rel.verNames.isEmpty()) yieldAll(installed)
            }.distinct().toList().ifEmpty { rel.verNames.ifEmpty { listOf(prefix) } }

            val confDir = java.io.File(ContentsManager.getContentTypeDir(
                context, ContentProfile.ContentType.CONTENT_TYPE_VEGAS), "configs")
            if (!confDir.exists()) confDir.mkdirs()
            var last: ParkResult? = null
            for (verName in targets) {
                val parked = java.io.File(confDir, "$verName.conf")
                if (!Downloader.downloadFile(rel.confUrl, parked, null)) {
                    last = ParkResult.Fail("download failed"); continue
                }
                recordStockProvenance(confDir, verName, rel.tag, assetName, rel.confUrl)
                last = ParkResult.Ok(verName)
            }
            last ?: ParkResult.Fail("no target version derived")
        }.getOrElse { ParkResult.Fail(it.message ?: "unknown error") }
    }
}
