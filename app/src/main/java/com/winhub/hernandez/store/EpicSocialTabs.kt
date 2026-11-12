package com.winhub.hernandez.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** The Epic **Profile** tab, fed by one [EpicUserData] fetch. No Friends tab: the roster is empty for
 *  most accounts, so the count and a short list live here instead. */

@Composable
private fun EpicFriendRow(f: EpicUserData.Friend, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .steamFocusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoreAvatar(emptyList(), f.displayName, 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = f.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (f.since.length >= 10) {
                Text(
                    text = "Friends since ${f.since.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun EpicProfileTab(
    profile: EpicUserData.Profile?,
    loading: Boolean,
    wide: Boolean,
    displayName: String,
    tokenMinutesLeft: Long,
    libraryCount: Int,
    installedCount: Int,
    onOpenWeb: (url: String, title: String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = profile?.displayName?.ifBlank { null } ?: displayName.ifBlank { "Epic account" }
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
                profile?.country?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                if (tokenMinutesLeft > 0) add("Session ~${tokenMinutesLeft} min left")
            }.joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoreFilterChip("Account on epicgames.com", false) {
                    onOpenWeb("https://www.epicgames.com/account/personal", "Epic account")
                }
                StoreFilterChip(if (loading) "Refreshing…" else "Refresh", false, onClick = onRefresh)
            }
        }
    }
    val stats: @Composable () -> Unit = {
        val tiles = buildList {
            add("Games" to libraryCount)
            add("Installed" to installedCount)
            profile?.takeIf { it.friendsAvailable }?.let { add("Friends" to it.friends.size) }
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
    val friendsPeek: @Composable () -> Unit = {
        val friends = profile?.friends.orEmpty()
        if (friends.isNotEmpty()) {
            StoreSectionHeader("Epic friends", "${friends.size} · no presence feed")
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                friends.take(if (wide) 6 else 4).forEach { f -> EpicFriendRow(f, Modifier.fillMaxWidth()) }
            }
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
                Spacer(Modifier.height(8.dp))
                friendsPeek()
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.padding(14.dp)) { identity() }
            Box(Modifier.padding(horizontal = 14.dp)) { stats() }
            Spacer(Modifier.height(8.dp))
            friendsPeek()
            Spacer(Modifier.height(24.dp))
        }
    }
}
