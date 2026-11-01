package com.winhub.hernandez.store

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.roundToInt

/**
 * In-app friend profile — replaces the old "View Steam profile" browser jump (the browser option now
 * lives on the "View full profile on Steam" row / overflow item). Renders the enriched
 * [SteamFriendsStore.FriendProfile] over the app's Frosty dark theme, reusing the roster palette
 * ([DotInGame] … presence dots) and avatar ([FriendAvatar]).
 *
 * Everything is privacy-aware: any null / empty field or section is simply not drawn (same as the Steam
 * client). The hero always renders from the passed [friend] (name / avatar / presence / current game),
 * so the screen is useful the instant it opens while [SteamFriendsStore.fetchProfile] streams the rest.
 *
 * Responsive: portrait is a single scroll column; landscape (≥600dp wide) is a two-column split —
 * identity + status + actions + about on the left, stats + games on the right — matching the mockup.
 */
@Composable
fun FriendProfileScreen(
    friend: SteamFriendsStore.SteamFriend,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onInvite: () -> Unit,
    onRemove: () -> Unit,
    onViewOnSteam: () -> Unit,
    /**
     * Force the single-column layout regardless of device orientation.
     *
     * REQUIRED when this screen is embedded in a pane that is already a fraction of the window —
     * the storefront's Friends tab renders it in a ~70% right pane. Without it the screen re-derives
     * "landscape" from [LocalConfiguration] (which still reports the whole DEVICE as landscape) and
     * splits itself a second time, producing three columns and a dead half-panel with the loading
     * spinner marooned in it. Any screen embedded in a pane needs the same treatment: decide the
     * layout from the space you were GIVEN, never from the device.
     */
    forceSingleColumn: Boolean = false,
) {
    var profile by remember(friend.steamId) { mutableStateOf<SteamFriendsStore.FriendProfile?>(null) }
    var loading by remember(friend.steamId) { mutableStateOf(true) }
    LaunchedEffect(friend.steamId) {
        loading = true
        profile = runCatching { SteamFriendsStore.fetchProfile(friend.steamId) }.getOrNull()
        loading = false
    }

    val configuration = LocalConfiguration.current
    val landscape = !forceSingleColumn &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= 600

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ProfileTopBar(onBack = onBack, onViewOnSteam = onViewOnSteam, onRemove = onRemove)
        if (landscape) {
            ProfileLandscape(friend, profile, loading, onMessage, onInvite, onViewOnSteam, onRemove)
        } else {
            ProfilePortrait(friend, profile, loading, onMessage, onInvite, onViewOnSteam, onRemove)
        }
    }
}

// ── Layouts ───────────────────────────────────────────────────────────────────────

@Composable
private fun ProfilePortrait(
    friend: SteamFriendsStore.SteamFriend,
    profile: SteamFriendsStore.FriendProfile?,
    loading: Boolean,
    onMessage: () -> Unit,
    onInvite: () -> Unit,
    onViewOnSteam: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ProfileHero(friend, profile)
        StatusCard(friend, Modifier.padding(horizontal = 16.dp))
        ActionsRow(onMessage, onInvite, onViewOnSteam, onRemove, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (loading && profile == null) {
            LoadingBlock()
        } else {
            AboutSection(profile?.summary)
            OverviewGrid(profile)
            RecentlyPlayed(profile?.recentGames.orEmpty())
            if (profile != null && profile.isEssentiallyEmpty) PrivacyNote()
            ViewOnSteamRow(onViewOnSteam)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProfileLandscape(
    friend: SteamFriendsStore.SteamFriend,
    profile: SteamFriendsStore.FriendProfile?,
    loading: Boolean,
    onMessage: () -> Unit,
    onInvite: () -> Unit,
    onViewOnSteam: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // LEFT — identity, status, actions, about.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.4f)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        ) {
            ProfileHero(friend, profile)
            StatusCard(friend, Modifier.padding(horizontal = 14.dp))
            ActionsRow(onMessage, onInvite, onViewOnSteam, onRemove, Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            AboutSection(profile?.summary)
            Spacer(Modifier.height(12.dp))
        }
        VerticalDivider(thickness = 1.dp, color = ProfileSectionLine)
        // RIGHT — stats + games (+ privacy / view-on-steam).
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.6f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (loading && profile == null) {
                LoadingBlock()
            } else {
                OverviewGrid(profile)
                RecentlyPlayed(profile?.recentGames.orEmpty())
                if (profile != null && profile.isEssentiallyEmpty) PrivacyNote()
                ViewOnSteamRow(onViewOnSteam)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTopBar(onBack: () -> Unit, onViewOnSteam: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        ProfileMoreButton(onViewOnSteam, onRemove, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The ⋮ overflow (used in the top bar and the actions row): View on Steam + Remove (tap-twice). */
@Composable
private fun ProfileMoreButton(onViewOnSteam: () -> Unit, onRemove: () -> Unit, tint: Color) {
    var expanded by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true; confirmRemove = false }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("View full profile on Steam") },
                onClick = { expanded = false; onViewOnSteam() },
            )
            HorizontalDivider(thickness = 1.dp, color = ProfileRowLine)
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (confirmRemove) "Tap again to confirm removal" else "Remove friend",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    if (!confirmRemove) confirmRemove = true
                    else { expanded = false; onRemove() }
                },
            )
        }
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHero(
    friend: SteamFriendsStore.SteamFriend,
    profile: SteamFriendsStore.FriendProfile?,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth()) {
        // Banner: an accent-tinted gradient wash, matching the mockup's warm header.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.32f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )
        // Identity row, pulled up to overlap the banner.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-26).dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FriendAvatar(friend = friend, size = 78.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(
                    profile?.realName?.takeIf { it.isNotBlank() },
                    profile?.country?.takeIf { it.isNotBlank() },
                ).joinToString("  ·  ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    profile?.level?.let { Chip("★ Level $it") }
                    profile?.memberSince?.let { Chip(it) }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, ProfileRowLine, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ── Status + actions ────────────────────────────────────────────────────────────────

/** Current-game card with a Join button — only when the friend is actually in a game. */
@Composable
private fun StatusCard(friend: SteamFriendsStore.SteamFriend, modifier: Modifier = Modifier) {
    if (friend.presence != SteamFriendsStore.Presence.IN_GAME || friend.gameAppId == 0) return
    val ctx = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, ProfileRowLine, RoundedCornerShape(12.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = "https://shared.steamstatic.com/store_item_assets/steam/apps/${friend.gameAppId}/header.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "In game · ${friend.gameName ?: "Playing"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = DotInGame,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = friend.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(ctx, SteamGameDetailActivity::class.java)
                            .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, friend.gameAppId),
                    )
                }
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) { Text("Join") }
    }
}

@Composable
private fun ActionsRow(
    onMessage: () -> Unit,
    onInvite: () -> Unit,
    onViewOnSteam: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Narrow content padding + softWrap=false: inside the Friends tab's right pane these
        // buttons are only ~110dp wide, and the defaults wrapped "Message" to "Messag / e".
        // They now shrink their padding and ellipsize instead of ever breaking a word.
        Button(
            onClick = onMessage,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) { ActionLabel("Message") }
        Button(
            onClick = onInvite,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) { ActionLabel("Invite") }
        ProfileMoreButton(onViewOnSteam, onRemove, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A button label that ellipsizes rather than wrapping — see [ActionsRow]. */
@Composable
private fun ActionLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge,
    )
}

// ── Sections ────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutSection(summary: String?) {
    if (summary.isNullOrBlank()) return
    Section("About") {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, ProfileRowLine, RoundedCornerShape(12.dp))
                .padding(12.dp),
        )
    }
}

@Composable
private fun OverviewGrid(profile: SteamFriendsStore.FriendProfile?) {
    profile ?: return
    // Level is deliberately NOT a tile: the hero already shows it as the "★ Level N" chip, and a
    // second copy in this row is pure duplication. It mattered most on a limited profile, where
    // games/hours are private and the level tile was the ONLY cell — a lonely "0 LEVEL" beside an
    // otherwise-empty OVERVIEW row, which read as a rendering fault rather than as data.
    //
    // To be clear about that zero: level 0 is REAL for a brand-new account, not a missing value.
    // FriendProfile.level stays nullable precisely so absent and zero can't be confused, and the
    // chip renders the 0 honestly. This row simply isn't the place to say it twice.
    //
    // badges/mutualFriends are permanently null (no verifiable RPC), so in practice this row is
    // games + hours: per-account facts that are genuinely absent on a private profile, and it then
    // hides entirely rather than rendering a sparse row.
    val stats = buildList {
        profile.gamesCount?.let { add("Games" to fmtInt(it)) }
        profile.hoursTotal?.let { add("Hours" to fmtInt(it.roundToInt())) }
        profile.badges?.let { add("Badges" to fmtInt(it)) }
        profile.mutualFriends?.let { add("Mutual" to fmtInt(it)) }
    }
    if (stats.isEmpty()) return
    Section("Overview") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(3).forEach { rowStats ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowStats.forEach { (label, value) ->
                        StatCell(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                    // Pad a short final row so cells keep their column width.
                    repeat(3 - rowStats.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, ProfileRowLine, RoundedCornerShape(12.dp))
            .padding(vertical = 11.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Text(
            text = label.uppercase(Locale.getDefault()),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun RecentlyPlayed(games: List<SteamFriendsStore.RecentGame>) {
    if (games.isEmpty()) return
    Section("Recently played") {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(games, key = { it.appId }) { g -> RecentGameCard(g) }
        }
    }
}

@Composable
private fun RecentGameCard(game: SteamFriendsStore.RecentGame) {
    Column(modifier = Modifier.width(120.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, ProfileRowLine, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (game.coverUrl != null) {
                AsyncImage(
                    model = game.coverUrl,
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                )
            }
        }
        Text(
            text = game.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            text = "${game.hours.roundToInt()} hrs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The dashed "View full profile on Steam ↗" row — the ONE place the old browser jump now lives. */
@Composable
private fun ViewOnSteamRow(onViewOnSteam: () -> Unit) {
    Text(
        text = "View full profile on Steam  ↗",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onViewOnSteam() }
            .border(1.dp, ProfileMenuOutline, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun PrivacyNote() {
    Text(
        text = "This profile is private — only what the friend chooses to share is shown here.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
    )
}

@Composable
private fun LoadingBlock() {
    // heightIn, not just padding: this is the only thing in the pane while the profile loads, so it
    // takes enough height to read as centred instead of hugging the top under the hero.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Loading profile…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Section wrapper: an uppercase muted header + its content, with consistent padding. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 9.dp),
        )
        content()
    }
}

// Line/outline tints, matched to the roster's chrome so the profile reads as the same surface.
private val ProfileRowLine = Color(0x1AFFFFFF)
private val ProfileSectionLine = Color(0x2EFFFFFF)
private val ProfileMenuOutline = Color(0x40FFFFFF)

private fun fmtInt(n: Int): String = "%,d".format(Locale.getDefault(), n)
