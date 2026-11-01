package com.winhub.hernandez.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.contentdialog.VegasKeyCatalog
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.Downloader
import com.winhub.hernandez.store.download.ContentDownloadPhase
import com.winhub.hernandez.store.download.ContentDownloadRegistry
import com.winhub.hernandez.store.download.ContentDownloadState
import com.winhub.hernandez.store.download.startContentDownload
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deck-native component download sheet. Draws with the Deck widget kit (DeckConfigDialog / buttons /
 * bars) instead of the classic Material [ContentDownloadSheet], and reuses the SAME process-lifetime
 * download pipeline ([ContentDownloadRegistry] + [startContentDownload]) so progress survives
 * backgrounding exactly like every other Deck download.
 */
@Composable
internal fun DeckContentDownloadSheet(
    contentTypes: List<ContentProfile.ContentType>,
    onDismiss: () -> Unit,
    onContentChanged: () -> Unit,
    inUseKey: String? = null,
) {
    val context = LocalContext.current
    val cm = remember { ContentsManager(context) }
    val scope = rememberCoroutineScope()
    val types = remember(contentTypes) {
        contentTypes.ifEmpty { listOf(ContentProfile.ContentType.CONTENT_TYPE_DXVK) }
    }
    var selectedType by remember(types) { mutableStateOf(types.first()) }
    var profiles by remember { mutableStateOf<List<ContentProfile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    var removeTarget by remember { mutableStateOf<ContentProfile?>(null) }
    val contentStates by ContentDownloadRegistry.states.collectAsState()
    val handled = remember { mutableStateListOf<String>() }

    LaunchedEffect(selectedType, refreshKey) {
        loading = true
        profiles = withContext(Dispatchers.IO) {
            val json = Downloader.downloadString(ContentsManager.REMOTE_PROFILES)
            if (json != null) cm.setRemoteProfiles(json) else cm.syncContents()
            cm.getProfiles(selectedType)?.distinctBy { ContentsManager.getEntryName(it) } ?: emptyList()
        }
        loading = false
    }

    // Whenever a tracked download reaches a terminal phase, re-read the list and tell the caller so
    // its version dropdowns pick up the freshly installed component.
    LaunchedEffect(contentStates) {
        var changed = false
        contentStates.forEach { (key, st) ->
            if (st.terminal && key !in handled) {
                handled.add(key)
                changed = true
            }
        }
        if (changed) {
            refreshKey++
            onContentChanged()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        (result.data?.data ?: InAppFilePicker.pickedUri(result.data))?.let { uri ->
            val fname = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: "Content file"
            val key = "deck-localfile"
            ContentDownloadRegistry.put(
                ContentDownloadState(
                    key = key,
                    title = fname,
                    type = types.singleOrNull()?.toString(),
                    verName = fname,
                    phase = ContentDownloadPhase.INSTALLING,
                )
            )
            installContent(context, cm, uri, onProgress = { f, _ ->
                ContentDownloadRegistry.update(key) {
                    it.copy(fraction = f, phase = ContentDownloadPhase.INSTALLING)
                }
            }) { ok ->
                ContentDownloadRegistry.update(key) {
                    if (ok) it.copy(fraction = 1f, phase = ContentDownloadPhase.DONE)
                    else it.copy(phase = ContentDownloadPhase.ERROR, error = "Install failed.")
                }
                if (ok) {
                    refreshKey++
                    onContentChanged()
                }
                scope.launch { delay(2500); ContentDownloadRegistry.remove(key) }
            }
        }
    }

    DeckConfigDialog(title = "Download " + types.first().toString().removePrefix("CONTENT_TYPE_").replace('_', ' '), onDismiss = onDismiss) {
        DeckConfigNote("Tap a version to download and install it. Installed builds are marked.")
        when {
            loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = DeckPalette.primary)
                }
            }
            profiles.isEmpty() -> {
                DeckConfigNote("No components available — check your connection and retry.", error = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DeckButton("Retry", primary = false) { refreshKey++ }
                }
            }
            else -> profiles.forEach { profile ->
                DeckDownloadRow(
                    profile = profile,
                    inUse = isDeckInUse(profile, inUseKey),
                    state = contentStates[ContentsManager.getEntryName(profile)],
                    onDownload = { startContentDownload(context.applicationContext, profile) },
                    onRemove = { removeTarget = profile },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DeckButton("Install from file", primary = false) {
                filePicker.launch(InAppFilePicker.buildIntent(context, emptyArray(), "Select component archive"))
            }
            Spacer(Modifier.width(8.dp))
            DeckButton("Close") { onDismiss() }
        }
    }

    removeTarget?.let { profile ->
        DeckConfigDialog(title = "Remove component?", onDismiss = { removeTarget = null }) {
            DeckConfigNote("Remove \"${profile.verName}\" from this device?")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                DeckButton("Remove", primary = true) {
                    scope.launch {
                        withContext(Dispatchers.IO) { cm.removeContent(profile) }
                        removeTarget = null
                        refreshKey++
                        onContentChanged()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckDownloadRow(
    profile: ContentProfile,
    inUse: Boolean,
    state: ContentDownloadState?,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
) {
    val local = profile.remoteUrl == null
    val busy = state != null && !state.terminal
    val done = state?.phase == ContentDownloadPhase.DONE
    val errored = state?.phase == ContentDownloadPhase.ERROR

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = profile.verName ?: "Component",
                    color = DeckPalette.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                val sub = when {
                    inUse -> "In use"
                    local || done -> "Installed"
                    errored -> state?.error ?: "Failed"
                    !profile.desc.isNullOrEmpty() -> profile.desc
                    else -> null
                }
                if (sub != null) {
                    Text(
                        text = sub,
                        color = if (inUse || done || local) DeckPalette.good else DeckPalette.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                    )
                }
            }
            when {
                busy -> Text(
                    text = "${((state?.fraction ?: 0f) * 100).toInt()}%",
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                local || done -> IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = DeckPalette.error)
                }
                else -> IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = "Download", tint = DeckPalette.primary)
                }
            }
        }
        if (busy) {
            val frac = (state?.fraction ?: 0f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(DeckPalette.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(frac).height(4.dp)
                        .background(if (state?.phase == ContentDownloadPhase.INSTALLING) DeckPalette.good else DeckPalette.primary),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
    }
}

private fun isDeckInUse(profile: ContentProfile, inUseKey: String?): Boolean {
    if (inUseKey.isNullOrEmpty()) return false
    return inUseKey == ContentsManager.getEntryName(profile) ||
        inUseKey == profile.verName ||
        inUseKey == "${profile.type}-${profile.verName}-${profile.verCode}"
}

/**
 * Deck-native replacement for the classic Material `StockConfigDownloadSheet`: same release feed
 * ([VegasStockConfigFetcher]) and same prefs-tail growth, drawn with Deck config rows.
 */
@Composable
internal fun DeckStockConfigSheet(
    onDismiss: () -> Unit,
    onContentChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf(listOf<VegasStockConfigFetcher.ReleaseConf>()) }
    var parkedTag by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        val list = withContext(Dispatchers.IO) { VegasStockConfigFetcher.listReleaseConfigs() }
        rows = list
        loading = false
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE)
            val existing = prefs.getString("released_tail", "")?.split('|')
                ?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            var added = false
            for (rel in list) {
                val prefix = rel.tag.removePrefix("v").substringBefore('-')
                for (c in rel.verNames + prefix) if (c.isNotBlank() && existing.add(c)) added = true
            }
            if (added) prefs.edit().putString("released_tail", existing.joinToString("|")).apply()
            val catExisting = prefs.getString("catalog_tail", "")?.split('|')
                ?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            var catAdded = false
            for (rel in list) if (rel.tag.isNotBlank() && catExisting.add(rel.tag)) catAdded = true
            VegasKeyCatalog.capToMax(catExisting)
            if (catAdded) prefs.edit().putString("catalog_tail", catExisting.joinToString("|")).apply()
        }
    }

    DeckConfigDialog(title = "VEGAS stock configs", onDismiss = onDismiss) {
        DeckConfigNote("Tap a version to fetch its config. Builds without a shipped config show none.")
        when {
            loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = DeckPalette.primary)
                }
            }
            rows.isEmpty() -> {
                DeckConfigNote("Couldn't reach the releases feed — check connection and retry.", error = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DeckButton("Retry", primary = false) { retryKey++ }
                }
            }
            else -> rows.forEach { rel ->
                val hasConf = rel.confUrl != null
                val busy = parkedTag == rel.tag
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (hasConf && !busy) Modifier.clickable {
                                parkedTag = rel.tag
                                scope.launch {
                                    val res = withContext(Dispatchers.IO) { VegasStockConfigFetcher.park(context, rel) }
                                    parkedTag = null
                                    when (res) {
                                        is VegasStockConfigFetcher.ParkResult.Ok -> {
                                            onContentChanged()
                                            Toast.makeText(
                                                context,
                                                "Parked as ${res.parkedAs}.conf — now select \"${res.parkedAs}\" under Stock config",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                        is VegasStockConfigFetcher.ParkResult.Fail ->
                                            Toast.makeText(context, "Failed: ${res.reason}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else Modifier,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = if (hasConf) Icons.Filled.CloudDownload else Icons.Filled.Block,
                        contentDescription = null,
                        tint = if (hasConf) DeckPalette.primary else DeckPalette.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = rel.tag.ifEmpty { "(untagged)" },
                            color = DeckPalette.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        val sub = buildString {
                            append(rel.confName ?: "no config asset")
                            if (rel.date.isNotEmpty()) append("  ·  ${rel.date}")
                        }
                        Text(sub, color = DeckPalette.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DeckPalette.primary)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            DeckButton("Close") { onDismiss() }
        }
    }
}
