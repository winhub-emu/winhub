package com.winhub.hernandez.store

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.blsteam.BlPlayerProfile
import com.winhub.hernandez.store.blsteam.BlSteamEngine
import com.winhub.hernandez.ui.theme.LocalAccentDim
import `in`.dragonbra.javasteam.enums.EPersonaState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The **Profile** tab — the signed-in user's own Steam profile, the one surface the app never had.
 *
 * Reads the same [SteamFriendsStore.fetchProfile] the friend profile screen uses, pointed at our own
 * SteamID, so the engine's player-profile call (level / showcased badge / equipped items / playtime
 * / recently played) lights up here and on a friend with one implementation.
 *
 * Writes go straight to the engine: rename → `setPersonaName`, the status chips → `setPersonaState`,
 * with the JavaSteam handler as the fallback while the Rust engine is off.
 *
 * **Privacy sections are independent.** Level and the showcased badge are public and render even for
 * a limited profile; the counts / playtime / recently-played only exist when *game details* are
 * public. Anything null is simply not drawn — nothing invents a zero. In particular there is no
 * badge *collection* or XP total anywhere in the protocol we can verify, so the Badges rail shows
 * the single showcased badge plus equipped decoration and hides itself when there is neither.
 */
@Composable
fun SteamProfileTab(
    wide: Boolean,
    libraryCount: Int,
    onOpenFriends: () -> Unit,
    onOpenApp: (Int) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val self by SteamFriendsStore.self.collectAsState()
    val friends by SteamFriendsStore.friends.collectAsState()

    // NOT an un-keyed `remember`: at first composition the CM may not have published
    // `steam_id_64` yet, and pinning 0L there would leave the tab stuck on "Not signed in" for the
    // life of the composition. Re-read on every session event instead, the same way
    // SteamFriendsAction tracks the login gate.
    var steamId by remember { mutableStateOf(readSelfSteamId()) }
    DisposableEffect(Unit) {
        val repo = runCatching { SteamRepository.getInstance() }.getOrNull()
        val listener = SteamRepository.SteamEventListener { ev ->
            if (ev.startsWith("LoggedIn") || ev.startsWith("SteamStatus:") ||
                ev == "LoggedOut" || ev == "Connected"
            ) {
                val fresh = readSelfSteamId()
                if (fresh != steamId) {
                    StorefrontLog.i(
                        StorefrontLog.PROFILE,
                        "self SteamID resolved late -> ${StorefrontLog.sid(fresh)} (was ${StorefrontLog.sid(steamId)})",
                    )
                    steamId = fresh
                }
                // Revalidate on SESSION events rather than on every glance at the tab. A session
                // coming up is the moment stale data can actually be improved; opening the tab is
                // not. Forced, because a reconnect is exactly when the TTL should be ignored.
                if (ev.startsWith("LoggedIn") || ev == "Connected") {
                    SteamFriendsStore.refreshSelfProfile(force = true)
                }
            }
        }
        repo?.addListener(listener)
        steamId = readSelfSteamId()
        onDispose { repo?.removeListener(listener) }
    }
    // Collected from the STORE, not held in composition. `remember(steamId) { ... }` here meant
    // leaving the tab threw the profile away, so every return re-rendered blank + spinner before
    // asking — even when the answer was already cached. The store keeps it across tab switches and
    // across process death (per-account disk mirror), so a revisit paints instantly.
    val profile by SteamFriendsStore.selfProfile.collectAsState()
    val refreshing by SteamFriendsStore.selfProfileLoading.collectAsState()
    // A spinner is for the genuine first-ever load ONLY. With anything cached we show it and
    // revalidate silently behind it.
    val loading = refreshing && profile == null

    // Local echo of the persona state so the chips respond instantly; the CM has no read-back for
    // our own chosen state that arrives fast enough to drive a toggle.
    var personaState by remember { mutableStateOf(EPersonaState.Online) }

    // Ask the store to revalidate. It publishes what it already knows first and only hits the
    // network when that is stale, so this is cheap to call on every tab open.
    LaunchedEffect(steamId) {
        if (steamId == 0L) {
            StorefrontLog.w(StorefrontLog.PROFILE, "no SteamID64 on record — showing the signed-out state")
            return@LaunchedEffect
        }
        SteamFriendsStore.refreshSelfProfile()
    }

    // Cached profile is a first-class source here too, so the name doesn't pop in late either.
    val displayName = self?.displayName ?: profile?.personaName
        ?: runCatching { SteamRepository.getInstance().displayName }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "Steam user"

    val onRename: (String) -> Unit = { newName ->
        val trimmed = newName.trim().take(32)
        if (trimmed.isNotBlank() && trimmed != displayName) {
            if (setPersonaName(trimmed, personaState)) {
                StorefrontLog.i(StorefrontLog.PROFILE, "persona name changed (${trimmed.length} chars)")
                onMessage("Persona name changed to $trimmed")
            } else {
                StorefrontLog.w(StorefrontLog.PROFILE, "setPersonaName FAILED — neither engine nor JavaSteam handler is live")
                onMessage("Couldn't change your persona name — not connected to Steam.")
            }
        }
    }

    val onSetState: (EPersonaState) -> Unit = { state ->
        personaState = state
        if (setPersonaState(state)) {
            StorefrontLog.i(StorefrontLog.PROFILE, "persona state -> ${state.name} (code ${state.code()})")
            onMessage("Status set to ${state.name}")
        } else {
            StorefrontLog.w(StorefrontLog.PROFILE, "setPersonaState(${state.name}) FAILED — no live session")
            onMessage("Couldn't change your status — not connected to Steam.")
        }
    }

    if (steamId == 0L) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            StoreNotice(
                title = "Not signed in",
                body = "Sign in to Steam to see your profile, level and playtime.",
            )
        }
        return
    }

    val content = ProfileTabContent(
        self = self,
        displayName = displayName,
        profile = profile,
        loading = loading,
        personaState = personaState,
        libraryCount = libraryCount,
        friendsCount = friends.size,
        steamId = steamId,
        onRename = onRename,
        onSetState = onSetState,
        onOpenFriends = onOpenFriends,
        onOpenApp = onOpenApp,
        onMessage = onMessage,
    )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (wide) ProfileTabLandscape(content) else ProfileTabPortrait(content)
    }
}

/**
 * Everything the two orientation layouts need, bundled so [ProfileTabPortrait] and
 * [ProfileTabLandscape] stay pure layout — the split idiom `SteamFriendProfileScreen` established.
 */
private data class ProfileTabContent(
    val self: SteamFriendsStore.SteamFriend?,
    val displayName: String,
    val profile: SteamFriendsStore.FriendProfile?,
    val loading: Boolean,
    val personaState: EPersonaState,
    val libraryCount: Int,
    val friendsCount: Int,
    val steamId: Long,
    val onRename: (String) -> Unit,
    val onSetState: (EPersonaState) -> Unit,
    val onOpenFriends: () -> Unit,
    val onOpenApp: (Int) -> Unit,
    val onMessage: (String) -> Unit,
)

// ── Layouts ───────────────────────────────────────────────────────────────────────────────────

/** Portrait: one scroll column — identity card, status chips, stat tiles, badges, recently played. */
@Composable
private fun ProfileTabPortrait(c: ProfileTabContent) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .focusGroup(),
    ) {
        IdentityCard(c)
        StatusChips(c)
        StatTiles(c)
        BadgesRail(c)
        RecentlyPlayedList(c)
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Landscape: two columns that both carry their weight.
 *
 * The first cut put only the identity card and the status chips on the left, which left roughly
 * 600px of dead space under them at 821x390dp while the right column did all the work. The tiles
 * moved across: everything that describes WHO you are — identity, status, the four stat tiles, and
 * the showcase when it has art — now groups on the left, and Recently Played owns the right column
 * at full height, which is the one section that genuinely scrolls.
 */
@Composable
private fun ProfileTabLandscape(c: ProfileTabContent) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .focusGroup(),
        ) {
            IdentityCard(c)
            StatusChips(c)
            // 2x2 here, not 1x4: see StatTiles. The showcase below is hidden whenever no badge art
            // resolves, so this block has to carry the column on its own.
            StatTiles(c, columns = 2)
            BadgesRail(c)
            Spacer(Modifier.height(16.dp))
        }
        VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Column(
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .focusGroup(),
        ) {
            RecentlyPlayedList(c, fillsPane = true)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Identity ──────────────────────────────────────────────────────────────────────────────────

/** Banner + avatar (with presence dot) + name/level/Edit + status line + copyable SteamID. */
@Composable
private fun IdentityCard(c: ProfileTabContent) {
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        // Banner: an accent wash, the same gradient treatment ProfileHero uses. When the account has
        // an equipped profile background, that art is drawn over it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.34f), MaterialTheme.colorScheme.surfaceContainer),
                    ),
                ),
        ) {
            c.profile?.equipped?.profileBackground?.imageUrl(large = true)?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-22).dp)
                .padding(horizontal = 13.dp),
        ) {
            // Avatar + presence dot. FriendAvatar is the roster's own avatar composable, reused so
            // our face renders identically to everyone else's.
            Box {
                val me = c.self
                val cachedAvatar = c.profile?.avatarUrl
                when {
                    // Live persona — the richest source (nickname, presence-aware).
                    me != null -> FriendAvatar(friend = me, size = 62.dp)

                    // No persona yet, but the cached profile carries an avatar URL. This is the
                    // whole point of painting from cache: the image starts downloading on the first
                    // frame instead of waiting for the persona round-trip that used to gate it.
                    cachedAvatar != null -> AsyncImage(
                        model = cachedAvatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )

                    else -> Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(dotColor(presenceOf(c.personaState))),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(top = 26.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editing) {
                        PersonaNameField(
                            initial = c.displayName,
                            onDone = { editing = false; c.onRename(it) },
                            onCancel = { editing = false },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = c.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Steam level — public data, but still null when the CM declined it or the
                        // JavaSteam engine is driving. Absent is NOT level 0, so the chip hides.
                        c.profile?.level?.let { lvl ->
                            Spacer(Modifier.width(8.dp))
                            LevelChip(lvl)
                        }
                        Spacer(Modifier.width(8.dp))
                        MiniButton("Edit") { editing = true }
                    }
                }
                Text(
                    text = listOfNotNull(
                        personaLabel(c.personaState),
                        c.profile?.country?.takeIf { it.isNotBlank() },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                c.profile?.memberSince?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(7.dp))
                SteamIdRow(c.steamId) {
                    copyToClipboard(ctx, "Steam ID", c.steamId.toString())
                    c.onMessage("Steam ID copied")
                }
            }
        }
    }
}

/** Inline rename field — replaces the name in place, commits on IME Done, cancels on empty. */
@Composable
private fun PersonaNameField(
    initial: String,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(initial) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    val shape = RoundedCornerShape(6.dp)
    BasicTextField(
        value = text,
        onValueChange = { if (it.length <= 32) text = it },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { if (text.isBlank()) onCancel() else onDone(text) },
        ),
        modifier = modifier
            .focusRequester(requester)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

/**
 * The accent-ringed level pill from the prototype — the STEAM ACCOUNT level. Accent, never a baked
 * gold/blue.
 *
 * Deliberately carries a spelled-out content description: the Badges rail shows a per-badge level
 * that is a different number, and on device the bare "22" beside an account level of 12 read as a
 * contradiction. The badge tile says "Badge level N"; this one announces "Steam account level N".
 */
@Composable
private fun LevelChip(level: Int) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(LocalAccentDim.current)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = "Steam account level $level" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/** Small outlined text button (the prototype's `.mini`) — used for Edit. */
@Composable
private fun MiniButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .steamFocusRing(shape)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** The tappable, copyable SteamID64 row. */
@Composable
private fun SteamIdRow(steamId: Long, onCopy: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = Modifier
            .steamFocusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onCopy)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = steamId.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Copy",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── Status chips ──────────────────────────────────────────────────────────────────────────────

private val PERSONA_CHOICES = listOf(
    EPersonaState.Online to "Online",
    EPersonaState.Away to "Away",
    EPersonaState.Invisible to "Invisible",
    EPersonaState.Offline to "Offline",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChips(c: ProfileTabContent) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PERSONA_CHOICES.forEach { (state, label) ->
            StatusChip(
                label = label,
                selected = c.personaState == state,
                onClick = { c.onSetState(state) },
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .steamFocusRing(shape)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

// ── Stats ─────────────────────────────────────────────────────────────────────────────────────

/**
 * Four tiles: Games / Played / Achievements / Friends. The Friends tile is tappable and jumps to
 * the Friends tab, as the prototype does.
 *
 * "Played" is null when the account's *game details* are private — it renders "—" rather than "0h",
 * because zero hours and hidden hours are different facts. "Achievements" has no cheap global
 * source in this build ([SteamAchievementStore] is per-app), so it is honestly "—".
 */
@Composable
private fun StatTiles(c: ProfileTabContent, columns: Int = 4) {
    // [columns] shapes the same four tiles to the space available: one 1x4 strip across a portrait
    // screen, a 2x2 block in the landscape LEFT column. The 2x2 exists because that column's other
    // vertical filler — the showcase rail — hides itself whenever no badge art resolves (an
    // appId==0 showcased badge, which is the common case), and a 1x4 strip left roughly a third of
    // the column empty. 2x2 costs ~72dp more height and still reads fine at ~377dp wide, so the
    // column holds up both with and without the showcase.
    val stats = listOf(
        ProfileStat(
            value = (c.profile?.gamesCount ?: c.libraryCount.takeIf { it > 0 })?.let { fmtCount(it) } ?: "—",
            label = "Games",
        ),
        ProfileStat(
            value = c.profile?.hoursTotal?.let { "${fmtCount(it.roundToInt())}h" } ?: "—",
            label = "Played",
        ),
        ProfileStat(
            // No global achievement total is exposed anywhere in this build — SteamAchievementStore
            // is per-app. An honest dash beats an invented number.
            value = "—",
            label = "Achiev.",
        ),
        ProfileStat(
            value = if (c.friendsCount > 0) fmtCount(c.friendsCount) else "—",
            label = "Friends",
            onClick = c.onOpenFriends,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.chunked(columns).forEach { rowStats ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowStats.forEach { stat ->
                    StatTile(
                        value = stat.value,
                        label = stat.label,
                        modifier = Modifier.weight(1f),
                        onClick = stat.onClick,
                    )
                }
                // Pad a short final row so tiles keep their column width instead of stretching.
                repeat(columns - rowStats.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One stat tile's content, so [StatTiles] can re-flow the same four into any column count. */
private data class ProfileStat(
    val value: String,
    val label: String,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.steamFocusRing(shape) else Modifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label.uppercase(Locale.getDefault()),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

// ── Badges ────────────────────────────────────────────────────────────────────────────────────

/**
 * The Badges rail, honestly scoped.
 *
 * Steam's badge *collection* and XP total have no verifiable RPC in this build — only the single
 * showcased ("favorite") badge and the equipped profile decoration. So the rail shows those and
 * **hides itself entirely** when the account has neither. Nothing here fabricates a badge list, and
 * the header carries no count or XP figure because we cannot know either.
 */
@Composable
private fun BadgesRail(c: ProfileTabContent) {
    val badge = c.profile?.favoriteBadge
    val equipped = c.profile?.equipped ?: BlPlayerProfile.EquippedItems.NONE
    val equippedTiles = listOfNotNull(
        equipped.avatarFrame?.let { it to "Avatar frame" },
        equipped.animatedAvatar?.let { it to "Animated avatar" },
        equipped.profileBackground?.let { it to "Profile background" },
        equipped.miniProfileBackground?.let { it to "Mini background" },
        equipped.profileModifier?.let { it to "Profile modifier" },
        equipped.steamDeckKeyboardSkin?.let { it to "Keyboard skin" },
    )

    // A showcased badge only earns a tile when we can actually draw something.
    //
    // The badge payload is badgeId / communityItemId / appId / level — it does NOT carry an icon
    // URL, and real badge art lives at a per-item hash we have no RPC to resolve. For a GAME badge
    // (appId > 0) the game's own capsule is honest art for it, labelled as the game's badge. For a
    // non-game badge (Years of Service, sale badges — appId == 0) there is nothing truthful to
    // draw, so the tile is omitted instead of rendering the empty box the device build showed.
    val badgeArt = if (badge != null && badge.appId > 0) capsuleCandidates(badge.appId) else emptyList()
    if (badge != null && badgeArt.isEmpty()) {
        StorefrontLog.i(
            StorefrontLog.PROFILE,
            "showcased badge present (badgeId=${badge.badgeId}, level=${badge.level}) but appId=0 — " +
                "no resolvable art, tile omitted",
        )
    }
    if (badgeArt.isEmpty() && equippedTiles.isEmpty()) return

    StoreSectionHeader("Badges & Showcase", "Showcased on your profile")
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (badge != null && badgeArt.isNotEmpty()) {
            item(key = "favorite_badge") {
                BadgeTile(
                    candidates = badgeArt,
                    caption = "Game badge",
                    // Explicitly "Badge level": this is the level of THIS badge, which is a
                    // different number from the account level in the chip beside the persona name.
                    // The device build showed "Level 22" here next to an account level of 12 and
                    // read as a contradiction.
                    sub = if (badge.level > 0) "Badge level ${badge.level}" else null,
                    onClick = { c.onOpenApp(badge.appId) },
                )
            }
        }
        items(equippedTiles, key = { it.second }) { (item, label) ->
            BadgeTile(
                candidates = listOfNotNull(item.imageUrl(large = false), item.imageUrl(large = true)),
                caption = item.itemTitle ?: item.name ?: label,
                sub = null,
                onClick = null,
            )
        }
    }
}

/**
 * One showcase tile. Walks [candidates] on load failure and — once they are all exhausted — removes
 * ITSELF rather than leaving the empty bordered square the device build rendered. A tile that can't
 * show its art has nothing to say.
 */
@Composable
private fun BadgeTile(
    candidates: List<String>,
    caption: String,
    sub: String?,
    onClick: (() -> Unit)?,
) {
    var attempt by remember(candidates) { mutableStateOf(0) }
    if (candidates.isEmpty() || attempt >= candidates.size) return

    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(84.dp)
            .then(if (onClick != null) Modifier.steamFocusRing(shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape),
        ) {
            AsyncImage(
                model = candidates[attempt],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
                onError = { attempt += 1 },
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Recently played ───────────────────────────────────────────────────────────────────────────

/**
 * Recently played, derived from owned-game playtime exactly as the friend profile does. Absent when
 * the account's game details are private — and absent is NOT "zero games", so the section is simply
 * not drawn rather than claiming anything.
 */
@Composable
private fun RecentlyPlayedList(c: ProfileTabContent, fillsPane: Boolean = false) {
    // In landscape this section IS the right column, so its loading and empty states have to fill
    // the pane rather than collapsing to nothing and leaving a blank half-screen.
    val paneBox = if (fillsPane) Modifier.fillMaxSize() else Modifier.fillMaxWidth()

    if (c.loading && c.profile == null) {
        Box(
            modifier = paneBox.padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        return
    }
    val games = c.profile?.recentGames.orEmpty()
    if (games.isEmpty()) {
        if (!fillsPane) return
        // `gamesPublic == false` is indistinguishable from "owns nothing" over the wire, so the
        // copy commits to neither.
        Box(modifier = paneBox, contentAlignment = Alignment.Center) {
            StoreNotice(
                title = "No recent play data",
                body = "Steam only shares playtime when your game details are public. " +
                    "Anything you've played recently will show up here once it does.",
            )
        }
        return
    }

    StoreSectionHeader("Recently Played", "By playtime")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        games.forEach { g -> RecentRow(g) { c.onOpenApp(g.appId) } }
    }
}

@Composable
private fun RecentRow(game: SteamFriendsStore.RecentGame, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .steamFocusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        StoreCapsule(
            appId = game.appId,
            title = game.name,
            modifier = Modifier.width(88.dp),
            shape = RoundedCornerShape(5.dp),
        )
        Text(
            text = game.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${fmtCount(game.hours.roundToInt())}h",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

// ── Persona plumbing ──────────────────────────────────────────────────────────────────────────

/**
 * Push a new persona state. Rust engine first (`setPersonaState` takes the raw EPersonaState code),
 * JavaSteam's `SteamFriends` handler as the fallback. Returns false when neither is live.
 */
private fun setPersonaState(state: EPersonaState): Boolean {
    runCatching { BlSteamEngine.session() }.getOrNull()?.let { session ->
        return runCatching { session.setPersonaState(state.code()); true }.getOrDefault(false)
    }
    val sf = runCatching { SteamRepository.getInstance().steamFriends }.getOrNull() ?: return false
    return runCatching { sf.setPersonaState(state); true }.getOrDefault(false)
}

/**
 * Rename. The engine's `setPersonaName` carries the state alongside the name (the wire message sets
 * both), so the caller's current state passes through unchanged; JavaSteam's `SteamFriends`
 * handler has its own name-only setter and covers the flag-off path. False = neither is live.
 */
private fun setPersonaName(name: String, state: EPersonaState): Boolean {
    runCatching { BlSteamEngine.session() }.getOrNull()?.let { session ->
        return runCatching { session.setPersonaName(name, state.code()); true }.getOrDefault(false)
    }
    val sf = runCatching { SteamRepository.getInstance().steamFriends }.getOrNull() ?: return false
    return runCatching { sf.setPersonaName(name); true }.getOrDefault(false)
}

/** Map the chosen persona state onto the roster's presence buckets for the avatar dot. */
private fun presenceOf(state: EPersonaState): SteamFriendsStore.Presence = when (state) {
    EPersonaState.Online, EPersonaState.LookingToPlay, EPersonaState.LookingToTrade ->
        SteamFriendsStore.Presence.ONLINE
    EPersonaState.Away, EPersonaState.Snooze, EPersonaState.Busy -> SteamFriendsStore.Presence.AWAY
    else -> SteamFriendsStore.Presence.OFFLINE
}

private fun personaLabel(state: EPersonaState): String = when (state) {
    EPersonaState.Online -> "Online"
    EPersonaState.Away -> "Away"
    EPersonaState.Snooze -> "Snooze"
    EPersonaState.Busy -> "Busy"
    EPersonaState.Invisible -> "Invisible"
    EPersonaState.Offline -> "Offline"
    else -> state.name
}

private fun copyToClipboard(ctx: Context, label: String, value: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
    }
}

private fun fmtCount(n: Int): String = "%,d".format(Locale.getDefault(), n)

/** The signed-in account's SteamID64, or 0 when the session hasn't published one yet. */
private fun readSelfSteamId(): Long =
    runCatching { SteamRepository.getInstance().steamId64 }.getOrDefault(0L)
