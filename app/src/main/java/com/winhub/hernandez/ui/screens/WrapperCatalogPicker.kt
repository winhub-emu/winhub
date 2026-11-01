package com.winhub.hernandez.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.IconButton
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.R
import com.winhub.hernandez.contents.WrapperCatalog
import com.winhub.hernandez.contents.WrapperCatalogDownloader
import com.winhub.hernandez.contents.WrapperCatalogEntry
import com.winhub.hernandez.contents.WrapperManager
import com.winhub.hernandez.ui.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 5 (issue #132) — the curated wrapper-catalog browser, mirroring the ReShade catalog picker.
 * Layered as a dialog OVER the wrapper manager (which itself may be a dialog): lists every entry from
 * wrappers.json as a card (name / author / license / size / gpuTargets chips) with a Download button +
 * progress. A download routes through [WrapperCatalogDownloader.install] -> WrapperManager.importWrapper,
 * so it lands as a normal import; [onInstalled] lets the manager refresh so the new wrapper appears.
 *
 * GPU applicability: entries whose gpuTargets don't include the current GPU family (and aren't "all")
 * are greyed with a note ("Mali-only — inert on this Adreno") — never hard-blocked; the user may still
 * download. The current family is derived from the manager's already-probed GPU (0x5143 = Adreno).
 *
 * Degrades gracefully: an absent/404 catalog, offline device, or bad JSON -> "No wrappers available
 * yet." and never crashes.
 */
@Composable
fun WrapperCatalogPicker(
    isQualcomm: Boolean,
    gpuModel: String,
    onDismiss: () -> Unit,
    onInstalled: () -> Unit,
    // Target-slot mode (#132): when non-null, a Download installs the entry as THIS bundled slot's
    // override (via WrapperCatalogDownloader.installToSlot) instead of a free-form import. The label is
    // only used for the title/intro. Left null = the free-form "Download wrappers" behaviour (unchanged).
    targetSlot: String? = null,
    targetSlotLabel: String? = null,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var catalog by remember { mutableStateOf<List<WrapperCatalogEntry>>(emptyList()) }
    var source by remember { mutableStateOf(WrapperCatalog.Source.NONE) }
    var loading by remember { mutableStateOf(true) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    // Catalog ids installed this session — the row then shows a "Downloaded" state (the imported
    // identifier is derived from the name, so tracking by catalog id is the simplest reliable signal).
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // DURABLE installed state (#132): catalog ids on device (imports + slot overrides) and the version
    // each is installed at, loaded once on open (WrapperManager reads are local, so this works offline).
    // Merged with the session [installedIds] so a wrapper grabbed THIS open reads installed immediately.
    var installedCatalogIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installedVersions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { WrapperCatalog.loadCached(context) }
        catalog = result.entries
        source = result.source
        // Load the durable installed set + per-id installed version (local reads; never throws).
        val wm = WrapperManager(context)
        val ids = withContext(Dispatchers.IO) {
            runCatching { wm.installedCatalogIds() }.getOrDefault(emptySet())
        }
        installedCatalogIds = ids
        installedVersions = withContext(Dispatchers.IO) {
            runCatching { ids.associateWith { wm.installedCatalogVersion(it) } }.getOrDefault(emptyMap())
        }
        loading = false
    }

    // Only a live NETWORK load lets us download (asset URLs may have moved since a cache was written).
    val offline = source != WrapperCatalog.Source.NETWORK

    fun startDownload(entry: WrapperCatalogEntry) {
        downloadingId = entry.id
        progress = 0
        errorMsg = null
        scope.launch {
            // Target-slot mode installs the entry as a slot override (marker-validated); the free-form
            // mode routes through the normal import pipeline. The two branches differ only in the call.
            val ok = if (targetSlot != null) {
                WrapperCatalogDownloader.installToSlot(context, entry, targetSlot) { pct ->
                    activity?.runOnUiThread { progress = pct }
                }
            } else {
                WrapperCatalogDownloader.install(context, entry) { pct ->
                    activity?.runOnUiThread { progress = pct }
                } != null
            }
            downloadingId = null
            if (ok) {
                installedIds = installedIds + entry.id
                Toast.makeText(
                    context,
                    context.getString(R.string.wrapper_catalog_download_ok, entry.name),
                    Toast.LENGTH_SHORT,
                ).show()
                onInstalled()   // parent rescans slot/imported list -> new wrapper appears
            } else {
                errorMsg = context.getString(R.string.wrapper_catalog_download_failed, entry.name)
            }
        }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        // Use (almost) the full screen width — the default AlertDialog width squeezes the catalog
        // cards so their names truncate to a couple of characters. Matches the manager dialog.
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                if (targetSlot != null)
                    "Replace ${targetSlotLabel ?: targetSlot} from catalog"
                else
                    context.getString(R.string.wrapper_catalog_dialog_title)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // In target-slot mode, a one-line intro clarifies the download replaces this slot's
                // override (rather than adding a new imported wrapper).
                if (targetSlot != null) {
                    Text(
                        "Downloading a wrapper installs it as this slot's override. A wrapper that " +
                            "doesn't match the slot is rejected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (offline && !loading) {
                    Text(
                        context.getString(
                            if (source == WrapperCatalog.Source.CACHE) R.string.wrapper_catalog_offline_cache
                            else R.string.wrapper_catalog_offline
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = cs.primary)
                    }
                    catalog.isEmpty() -> Text(
                        context.getString(R.string.wrapper_catalog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        catalog.forEach { entry ->
                            // Durable state wins; the session set covers a wrapper grabbed just now
                            // (before a reload would refresh the durable set).
                            val durablyInstalled = entry.id in installedCatalogIds
                            val installed = durablyInstalled || entry.id in installedIds
                            // Only a durably-recorded install carries a version to compare against.
                            val updateAvailable = durablyInstalled &&
                                entry.version > (installedVersions[entry.id] ?: 0)
                            WrapperCatalogRow(
                                entry = entry,
                                applicable = isApplicable(entry.gpuTargets, isQualcomm),
                                applicabilityNote = applicabilityNote(entry.gpuTargets, isQualcomm, gpuModel),
                                badgeLabel = applicabilityChip(entry.gpuTargets, isQualcomm),
                                installed = installed,
                                updateAvailable = updateAvailable,
                                busy = downloadingId == entry.id,
                                anyBusy = downloadingId != null,
                                offline = offline,
                                progress = progress,
                                onDownload = { startDownload(entry) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.wrapper_catalog_close))
            }
        },
    )
}

/**
 * A catalog entry rendered in the wrapper-manager card idiom: reuses [WrapperCardFrame] (icon tile +
 * name title + author/size subtitle + trailing Download affordance + tap-to-expand chevron). The
 * expanded box carries the description, license, size, GPU-target chips, the applicability note, and
 * (while downloading) the full progress bar — so a catalog card looks and expands like a manager card.
 *
 * Inapplicable-on-this-GPU rows are dimmed (but still downloadable); a busy/installed row stays full.
 */
@Composable
private fun WrapperCatalogRow(
    entry: WrapperCatalogEntry,
    applicable: Boolean,
    applicabilityNote: String?,
    badgeLabel: String?,
    installed: Boolean,
    updateAvailable: Boolean,
    busy: Boolean,
    anyBusy: Boolean,
    offline: Boolean,
    progress: Int,
    onDownload: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember(entry.id) { mutableStateOf(false) }
    val alpha = if (applicable || busy || installed) 1f else 0.55f
    // author · size in the subtitle; the collapsed card mirrors the manager's slot/import cards.
    val subtitle = buildString {
        if (entry.author.isNotBlank()) append(entry.author)
        val sz = formatSize(entry.fileSize)
        if (sz != null) { if (isNotEmpty()) append(" · "); append(sz) }
    }.ifBlank { entry.license }

    Box(modifier = Modifier.fillMaxWidth().alpha(alpha)) {
        WrapperCardFrame(
            icon = Icons.Filled.CloudDownload,
            title = entry.name,
            subtitle = subtitle,
            expandable = true,
            expanded = expanded,
            onToggleExpand = { expanded = !expanded },
            titleBadge = {
                // #132: a durable "Update available" chip when the catalog carries a newer version than
                // what's installed (the download affordance below stays, to pull the update).
                if (updateAvailable) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            LocalContextString(R.string.wrapper_update_available),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onTertiaryContainer,
                        )
                    }
                }
                // Small status chip under the title on GPU-inapplicable entries (e.g. "Mali only"), so
                // the reason a row is dimmed is visible without expanding it.
                badgeLabel?.let {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(cs.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onTertiaryContainer,
                        )
                    }
                }
            },
            details = {
                WrapperCatalogDetail(
                    entry = entry,
                    applicabilityNote = applicabilityNote,
                    offline = offline,
                    installed = installed,
                    busy = busy,
                    progress = progress,
                )
            },
            trailing = {
                // Compact icon only, so the name column stays readable. The full Download button
                // (with progress) lives in the expanded detail box below.
                when {
                    busy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    // Update available -> keep the download affordance (it pulls the newer version).
                    updateAvailable -> IconButton(enabled = !anyBusy && !offline, onClick = onDownload) {
                        Icon(Icons.Filled.CloudDownload, contentDescription =
                            LocalContextString(R.string.wrapper_catalog_download), tint = cs.primary)
                    }
                    // Installed + current -> a checkmark instead of the download button.
                    installed -> Icon(Icons.Filled.CheckCircle, contentDescription =
                        LocalContextString(R.string.wrapper_installed),
                        tint = cs.primary, modifier = Modifier.size(22.dp))
                    else -> IconButton(enabled = !anyBusy && !offline, onClick = onDownload) {
                        Icon(Icons.Filled.CloudDownload, contentDescription =
                            LocalContextString(R.string.wrapper_catalog_download), tint = cs.primary)
                    }
                }
            },
        )
    }
}

/** The expanded detail box for a catalog card, styled to match [WrapperDetailBox]: description, then
 *  License/Size rows, GPU-target chips, the applicability note, an offline hint, and the full download
 *  progress bar while busy. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WrapperCatalogDetail(
    entry: WrapperCatalogEntry,
    applicabilityNote: String?,
    offline: Boolean,
    installed: Boolean,
    busy: Boolean,
    progress: Int,
) {
    val cs = MaterialTheme.colorScheme
    if (entry.description.isNotBlank()) {
        Text(entry.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
    }
    if (entry.license.isNotBlank()) DetailRow("License", entry.license)
    formatSize(entry.fileSize)?.let { DetailRow("Size", it) }

    // gpuTargets chips.
    if (entry.gpuTargets.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("GPU targets", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entry.gpuTargets.forEach { t ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(t, style = MaterialTheme.typography.labelSmall, color = cs.onSecondaryContainer)
                }
            }
        }
    }

    // GPU-applicability note, only when the entry doesn't target this GPU.
    applicabilityNote?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }

    // Offline hint on rows that can't be fetched.
    if (offline && !installed && !busy) {
        Spacer(Modifier.height(6.dp))
        Text(LocalContextString(R.string.wrapper_catalog_needs_connection),
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }

    // Full progress bar while downloading/installing.
    if (busy) {
        Spacer(Modifier.height(8.dp))
        val frac = (progress.coerceIn(0, 100)) / 100f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                LocalContextString(
                    if (progress >= 100) R.string.wrapper_catalog_installing
                    else R.string.wrapper_catalog_downloading
                ),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Text("$progress%", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(3.dp))
        Divider(color = cs.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = frac,
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = cs.primary,
            trackColor = cs.surfaceContainerHighest,
        )
    }
}

/** Convenience for reading a string resource inside a row composable. */
@Composable
private fun LocalContextString(resId: Int): String = LocalContext.current.getString(resId)

/** An entry applies to the current GPU when it targets "all" or the current family. On a non-Adreno
 *  device (we can't tell Mali/Exynos/PowerVR apart from the vendor id alone) any non-adreno target
 *  counts as applicable; on Adreno only an explicit "adreno" target does. */
private fun isApplicable(targets: List<String>, isQualcomm: Boolean): Boolean {
    if (targets.isEmpty() || targets.contains("all")) return true
    return if (isQualcomm) targets.contains("adreno") else targets.any { it != "adreno" }
}

/** A tiny chip label ("Mali only" / "Adreno only") for an entry that doesn't apply to this GPU, or
 *  null when it applies. Uses the first non-Adreno target family so ["mali","exynos"] reads "Mali only". */
private fun applicabilityChip(targets: List<String>, isQualcomm: Boolean): String? {
    if (isApplicable(targets, isQualcomm)) return null
    return if (isQualcomm) {
        val fam = targets.firstOrNull { it != "adreno" } ?: "Mali"
        fam.replaceFirstChar { it.uppercase() } + " only"
    } else {
        "Adreno only"
    }
}

/** A short "inert on this GPU" note, or null when the entry applies (or targets "all"). */
private fun applicabilityNote(targets: List<String>, isQualcomm: Boolean, gpuModel: String): String? {
    if (isApplicable(targets, isQualcomm)) return null
    return if (isQualcomm)
        "${targets.joinToString("/")}-only — inert on this Adreno ($gpuModel)"
    else
        "Adreno-only — inert on this $gpuModel"
}

/** Human-readable byte size, or null when unknown (0). */
private fun formatSize(bytes: Long): String? {
    if (bytes <= 0) return null
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%d KB".format((bytes / 1024).coerceAtLeast(1L))
}
