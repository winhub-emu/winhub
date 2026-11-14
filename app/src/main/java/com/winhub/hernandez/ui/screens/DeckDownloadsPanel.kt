package com.winhub.hernandez.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.SteamDatabase
import com.winhub.hernandez.store.SteamDepotDownloader
import com.winhub.hernandez.store.SteamRepository
import com.winhub.hernandez.store.StoreDownloadTier
import com.winhub.hernandez.store.StoreMatureSetting
import com.winhub.hernandez.store.download.DownloadEntry
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadScope
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.formatDownloadSize
import com.winhub.hernandez.store.download.formatDownloadSpeed
import com.winhub.hernandez.store.download.formatEta
import kotlinx.coroutines.launch

/**
 * The Downloads chip's in-Deck destination: every store's real [DownloadRegistry] queue with the
 * MB/GB it has fetched and written, Pause/Resume/Cancel/Dismiss on the rows that support them,
 * and the Store settings group (Show mature content + the shared download-speed tier) right
 * under the queue — DroidDeck's Downloads section, in Graphite.
 */
@Composable
internal fun DeckDownloadsPanel(onBack: () -> Unit) {
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val main = remember { Handler(Looper.getMainLooper()) }
    val entries by DownloadRegistry.entries.collectAsState()
    var showMature by remember { mutableStateOf(StoreMatureSetting.get(context)) }
    var dlTier by remember { mutableStateOf(StoreDownloadTier.get(appCtx)) }
    // The control this panel's own Resume created — kept so the next Pause hits the LIVE
    // download instead of the stale handle a finished run left in the registry row.
    var steamHandle by remember { mutableStateOf<SteamDepotDownloader.DownloadControl?>(null) }
    var steamHandleApp by remember { mutableStateOf(-1) }

    val onPause: (DownloadEntry) -> Unit = { row ->
        val h = steamHandle
        if (h != null && steamHandleApp == row.id.toIntOrNull()) {
            h.pause.run()
            steamHandle = null
        } else {
            row.pause?.invoke()
        }
    }
    val onResume: (DownloadEntry) -> Unit = { row ->
        val appId = row.id.toIntOrNull()
        if (appId != null && row.store == Store.STEAM) {
            DownloadScope.io.launch {
                try {
                    val dlRow = SteamRepository.getInstance().database.getDownload(appId)
                    if (dlRow != null && dlRow.status == SteamDatabase.DL_PAUSED) {
                        val c = SteamDepotDownloader.resumeApp(
                            appId, appCtx, StoreDownloadTier.get(appCtx),
                        )
                        main.post { steamHandle = c; steamHandleApp = appId }
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(deckPageBackground())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp),
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = null,
                tint = Deck.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text("Back", color = Deck.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(14.dp))
        Text(
            text = "Downloads",
            color = Deck.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = "What every store is installing",
            color = Deck.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.size(14.dp))

        if (entries.isEmpty()) {
            Text("No downloads yet.", color = Deck.onSurfaceVariant, fontSize = 13.sp)
        } else {
            // Active first (pause/resume still works), then whatever the queue holds.
            val sorted = entries.sortedByDescending { it.isActive }
            sorted.forEach { row ->
                DeckDownloadRow(row, onPause = { onPause(row) }, onResume = { onResume(row) })
                Spacer(Modifier.size(10.dp))
            }
        }

        Spacer(Modifier.size(6.dp))
        DeckGroup("Store settings") {
            DeckRow(
                label = "Show mature content",
                hint = "Storefront 17+/18+ titles; owned games always show",
                control = {
                    DeckToggle(showMature) { v ->
                        showMature = v
                        StoreMatureSetting.set(context, v)
                    }
                },
            )
            DeckChoiceRow(
                label = "Download speed",
                hint = "Ceiling the next store download may ramp to",
                options = StoreDownloadTier.ALL.map { t -> t.toString() to StoreDownloadTier.label(t) },
                selected = dlTier.toString(),
                onPick = { v ->
                    v.toIntOrNull()?.let { t ->
                        dlTier = t
                        StoreDownloadTier.set(appCtx, t)
                    }
                },
            )
        }
    }
}

/** One registry row: cover, name, state, MB/GB + speed + ETA, progress and the live actions. */
@Composable
private fun DeckDownloadRow(
    row: DownloadEntry,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val cover = row.cover
    // Steam rows carry a bare appId as their cover — resolve it to the public header image.
    val art: String? = when {
        row.store == Store.STEAM && !cover.isNullOrEmpty() && cover.all { it.isDigit() } ->
            "https://shared.steamstatic.com/store_item_assets/steam/apps/${row.id}/header.jpg"
        else -> cover
    }
    val storeName = when (row.store) {
        Store.STEAM -> "Steam"
        Store.EPIC -> "Epic"
        Store.GOG -> "GOG"
        Store.AMAZON -> "Amazon"
        Store.XBOX -> "Xbox"
    }
    val stateLabel = when (row.state) {
        DownloadState.QUEUED ->
            if (row.queuePosition > 0) "Queued #${row.queuePosition}" else "Queued"
        DownloadState.DOWNLOADING -> "Downloading"
        DownloadState.PAUSED -> "Paused"
        DownloadState.INSTALLED -> "Installed"
        DownloadState.FAILED -> row.error ?: "Failed"
        DownloadState.CANCELLED -> "Cancelled"
    }
    val bytePart = when {
        row.downloadTotal > 0 ->
            "${formatDownloadSize(row.downloadDone)} / ${formatDownloadSize(row.downloadTotal)} downloaded"
        row.installTotal > 0 ->
            "${formatDownloadSize(row.installDone)} / ${formatDownloadSize(row.installTotal)} on disk"
        else -> null
    }
    val speedPart = if (row.state == DownloadState.DOWNLOADING && row.speedBps > 0) {
        formatDownloadSpeed(row.speedBps)
    } else {
        null
    }
    val etaPart = if (row.state == DownloadState.DOWNLOADING && row.etaSeconds >= 0) {
        formatEta(row.etaSeconds)
    } else {
        null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Deck.surface)
            .border(1.dp, Deck.line, shape)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Deck.surfaceVariant),
        ) {
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = row.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.name,
                color = Deck.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = "$storeName - $stateLabel",
                color = if (row.state == DownloadState.FAILED) Color(0xFFFF6B6B) else Deck.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(bytePart, speedPart, etaPart).joinToString(" - ")
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    color = Deck.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.isActive) {
                Spacer(Modifier.size(6.dp))
                LinearProgressIndicator(
                    progress = { (row.pct.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Deck.primary,
                    trackColor = Deck.surfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.isActive && row.supportsPause) {
                    DeckButton(
                        text = if (row.state == DownloadState.PAUSED) "Resume" else "Pause",
                        primary = false,
                    ) {
                        if (row.state == DownloadState.PAUSED) onResume() else onPause()
                    }
                }
                if (row.isActive) {
                    DeckButton("Cancel", primary = false) { row.cancel?.invoke() }
                } else {
                    DeckButton("Dismiss", primary = false) { DownloadRegistry.remove(row.key) }
                }
            }
        }
    }
}
