package com.winhub.hernandez.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.store.download.StoreSection
import com.winhub.hernandez.store.download.StoreStatusText

/** The Amazon **Profile** tab: identity (best-effort name), session, library counts, web links. */
@Composable
fun AmazonProfileTab(
    profile: AmazonUserData.Profile?,
    loading: Boolean,
    wide: Boolean,
    deviceSerial: String,
    tokenMinutesLeft: Long,
    libraryCount: Int,
    installedCount: Int,
    updatableCount: Int,
    onOpenWeb: (url: String, title: String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = profile?.name?.ifBlank { null } ?: "Amazon Games account"
    val identity: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StoreAvatar(emptyList(), name, 84.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                profile?.email?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (tokenMinutesLeft > 0) add("Session ~${tokenMinutesLeft} min left")
                if (deviceSerial.isNotBlank()) add("Device ${deviceSerial.takeLast(6)}")
            }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoreFilterChip("Prime Gaming", false) { onOpenWeb("https://gaming.amazon.com/home", "Prime Gaming") }
                StoreFilterChip(if (loading) "Refreshing…" else "Refresh", false, onClick = onRefresh)
            }
        }
    }
    val stats: @Composable () -> Unit = {
        val tiles = buildList {
            add("Games" to libraryCount)
            add("Installed" to installedCount)
            if (updatableCount > 0) add("Updates" to updatableCount)
        }
        val cols = if (wide) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, n) -> StatTile(label, n, Modifier.weight(1f)) }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    val about: @Composable () -> Unit = {
        StoreSection(title = "About Amazon Games here") {
            StoreStatusText(
                "Games you claim on Prime Gaming download through Amazon's distribution service and " +
                    "run without the Amazon Games app. Amazon exposes no friends or catalog API to " +
                    "third-party clients, so those live on the website.",
            )
        }
    }

    if (wide) {
        Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(14.dp),
            ) { identity() }
            Column(
                modifier = Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 14.dp),
            ) {
                Box(Modifier.padding(horizontal = 14.dp)) { stats() }
                Spacer(Modifier.height(16.dp))
                about()
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.padding(14.dp)) { identity() }
            Box(Modifier.padding(horizontal = 14.dp)) { stats() }
            Spacer(Modifier.height(16.dp))
            about()
            Spacer(Modifier.height(24.dp))
        }
    }
}
