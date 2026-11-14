package com.winhub.hernandez.store

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.ui.deck.XmbPageArtTheme
import com.winhub.hernandez.ui.deck.xmbGlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Steam's game page in the XMB store style (landscape): the shared XMB layout with Steam's own Details tab, size card, status
// and achievements. The state, the handlers and the other tab bodies stay in SteamGameDetailScreen.

private val GoldAccent = Color(0xFFE8B652)

/** What the right column and the Details tab show about the game that the page's own state doesn't carry. */
private data class StoreBlurb(val description: String?, val genres: List<String>, val year: String?)

@Composable
internal fun SteamXmbDetailPage(
    appId: Int,
    title: String,
    steamStatus: SteamRepository.SteamStatus,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
    tabLabels: List<String>,
    tabBadges: List<String?>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    typeText: String,
    sizeText: String,
    /** The download / Steam estimate / free-space lines of the size breakdown ("Download: 4.5 GB"); empty ones are skipped. */
    sizeLines: List<String>,
    freeFits: Boolean,
    statusText: String,
    statusColor: Color,
    achDone: Int,
    achTotal: Int,
    downloading: Boolean,
    installFrac: Float,
    downloadFrac: Float,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    infoLine: String?,
    /** The selected branch's name, or null when the game has no other branch to choose. */
    branchLabel: String?,
    onBranch: () -> Unit,
    gearMenu: @Composable (dismiss: () -> Unit) -> Unit,
    caption: (@Composable () -> Unit)?,
    tabBody: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Same text for the right column and the Details tab; one request per page open, off the main thread.
    var blurb by remember(appId) { mutableStateOf<StoreBlurb?>(null) }
    LaunchedEffect(appId) {
        blurb = withContext(Dispatchers.IO) {
            SteamStoreSearch.fetchDetails(appId)?.let { StoreBlurb(it.shortDescription, it.genres, it.releaseYear) }
        }
    }

    XmbPageArtTheme(enabled = true) {
    XmbDetailPage(
        title = title,
        backLabel = "Library",
        onBack = onBack,
        art = { StoreCapsule(appId = appId, title = title, aspectRatio = null, modifier = Modifier.fillMaxSize()) },
        topBarActions = {
            SteamStatusPill(status = steamStatus, onReconnect = onReconnect)
            DownloadsButton()
        },
        tabLabels = tabLabels,
        tabBadges = tabBadges,
        selectedTab = selectedTab,
        onSelectTab = onSelectTab,
        textFirstTab = true,
        downloading = downloading,
        installFrac = installFrac,
        downloadFrac = downloadFrac,
        primaryLabel = primaryLabel,
        primaryEnabled = primaryEnabled,
        onPrimary = onPrimary,
        infoLine = infoLine,
        branchLabel = branchLabel,
        onBranch = onBranch,
        gearMenu = gearMenu,
        caption = caption,
        info = {
            val sub = listOfNotNull(
                blurb?.genres?.take(2)?.joinToString(" · ")?.takeIf { it.isNotEmpty() },
                blurb?.year,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = cs.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .xmbGlassCard(RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("INSTALL SIZE", color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(sizeText, color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (achTotal > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Achievements", color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("$achDone / $achTotal", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f))) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((achDone.toFloat() / achTotal).coerceIn(0f, 1f))
                                .background(Brush.horizontalGradient(listOf(Color(0xFFCAA03E), GoldAccent))),
                        )
                    }
                }
            }
        },
    ) {
        if (selectedTab == 0) {
            DetailsPane(
                typeText = typeText,
                sizeText = sizeText,
                sizeLines = sizeLines,
                freeFits = freeFits,
                statusText = statusText,
                statusColor = statusColor,
                blurb = blurb,
            )
        } else {
            tabBody()
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsPane(
    typeText: String,
    sizeText: String,
    sizeLines: List<String>,
    freeFits: Boolean,
    statusText: String,
    statusColor: Color,
    blurb: StoreBlurb?,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(typeText, sizeText) + blurb?.genres.orEmpty().take(3)).filter { it.isNotBlank() }.forEach { PaneChip(it) }
        }
        blurb?.description?.let {
            Text(it, color = cs.onSurface, fontSize = 14.sp, lineHeight = 21.sp)
        }
        val lines = sizeLines.filter { it.isNotEmpty() }
        if (lines.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                lines.forEachIndexed { i, line ->
                    val warn = i == lines.lastIndex && !freeFits
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.surfaceVariant)
                            .padding(horizontal = 11.dp, vertical = 9.dp),
                    ) {
                        Text(
                            line.substringAfter(':', line).trim(),
                            color = if (warn) cs.error else cs.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (line.contains(':')) Text(line.substringBefore(':'), color = cs.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
        Text(statusText, color = statusColor, fontSize = 14.sp)
    }
}

@Composable
private fun PaneChip(label: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
