package com.winhub.hernandez.store

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.winhub.hernandez.util.InAppFilePicker
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// Status-dot colours — recognisable Steam-ish presence palette, held explicitly so both themes match.
// `internal` so the in-app friend-profile screen (SteamFriendProfileScreen.kt) reuses the exact palette.
internal val DotInGame = Color(0xFF90BA3C)
internal val DotOnline = Color(0xFF57CBDE)
internal val DotAway = Color(0xFFE0A82E)
internal val DotOffline = Color(0xFF6D7883)
internal val DotUnknown = Color(0xFF4A525B)     // relay hasn't confirmed this friend's presence yet

// List chrome — thin gray lines, pop-up-menu style, read as subtle grays on the dark surface.
private val RowDivider = Color(0x1AFFFFFF)      // between friends within a section
private val SectionDivider = Color(0x2EFFFFFF)  // between sections (In game / Online / Offline)
private val MenuOutline = Color(0x40FFFFFF)     // pop-up menu / dialog outline stroke
private val UnreadBg = Color(0xFFE53935)        // unread-count badge fill

internal fun dotColor(p: SteamFriendsStore.Presence): Color = when (p) {
    SteamFriendsStore.Presence.IN_GAME -> DotInGame
    SteamFriendsStore.Presence.ONLINE -> DotOnline
    SteamFriendsStore.Presence.AWAY -> DotAway
    SteamFriendsStore.Presence.OFFLINE -> DotOffline
    SteamFriendsStore.Presence.UNKNOWN -> DotUnknown
}

// Section order + labels, top to bottom. `internal`: the in-game drawer's Friends tab (ui/XServerFriendsTab.kt)
// groups the same way, with the row / header / bubble composables below. "Status unknown" only ever
// appears during a SteamLite game, for friends the in-game relay hasn't confirmed yet (never Offline
// by default — see SteamFriendsStore.agentMergePersona).
internal val PRESENCE_ORDER = listOf(
    SteamFriendsStore.Presence.IN_GAME to "In game",
    SteamFriendsStore.Presence.ONLINE to "Online",
    SteamFriendsStore.Presence.AWAY to "Away",
    SteamFriendsStore.Presence.OFFLINE to "Offline",
    SteamFriendsStore.Presence.UNKNOWN to "Status unknown",
)

// ── Friends list ────────────────────────────────────────────────────────────────

/** The top app bar (back / title / connection pill / add / refresh / settings) shared by the single-pane
 *  list, the landscape two-pane layout (where it spans the top above both panes), and the off-state.
 *  [onAdd] / [onRefresh] are optional so the off-state can show a lean bar (back + pill + cog) — the
 *  add / refresh actions only render when a handler is supplied. */
@Composable
private fun FriendsTopBar(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    // Off when this bar is hosted inside the storefront's Friends tab, where the tab strip / rail
    // is the way out and a back arrow would be dead chrome.
    showBackButton: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackButton) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "Friends",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        SteamConnectionPill()
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add friend",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Friends & chat settings",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Single-pane friends list — the portrait / phone default: the top app bar over the roster, a tap
 * opening a friend's chat full-screen. Behaviour is unchanged from before the responsive split; the
 * landscape [FriendsTwoPane] reuses the same [FriendsTopBar] + [FriendsListBody] pieces.
 */
@Composable
fun FriendsListScreen(
    available: Boolean,
    onBack: () -> Unit,
    onOpenChat: (SteamFriendsStore.SteamFriend) -> Unit,
    onOpenProfile: (SteamFriendsStore.SteamFriend) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FriendsTopBar(
            onBack = onBack,
            onSettings = { showSettings = true },
            onAdd = { showAdd = true },
            onRefresh = { SteamFriendsStore.refresh() },
        )
        if (showAdd) {
            AddFriendDialog(onDismiss = { showAdd = false })
        }
        if (showSettings) {
            SteamSocialSettingsSheet(onDismiss = { showSettings = false })
        }
        FriendsListBody(
            available = available,
            selectedFriendId = null,
            showFilter = false,
            onOpenChat = onOpenChat,
            onOpenProfile = onOpenProfile,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Landscape master-detail: the roster docks to a ~26% left pane (always visible) and the selected
 * friend's [ChatScreen] fills the ~74% right pane (or an empty-state prompt when nothing is picked).
 * Selecting a friend just swaps the right pane — there is no "close chat" step — so back exits the
 * whole screen (handled by the host). The app bar spans the top above both panes.
 */
@Composable
fun FriendsTwoPane(
    available: Boolean,
    selectedFriend: SteamFriendsStore.SteamFriend?,
    onSelectFriend: (SteamFriendsStore.SteamFriend) -> Unit,
    onOpenProfile: (SteamFriendsStore.SteamFriend) -> Unit,
    onBack: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FriendsTopBar(
            onBack = onBack,
            onSettings = { showSettings = true },
            onAdd = { showAdd = true },
            onRefresh = { SteamFriendsStore.refresh() },
        )
        if (showAdd) {
            AddFriendDialog(onDismiss = { showAdd = false })
        }
        if (showSettings) {
            SteamSocialSettingsSheet(onDismiss = { showSettings = false })
        }
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            FriendsListBody(
                available = available,
                selectedFriendId = selectedFriend?.steamId,
                showFilter = true,
                onOpenChat = onSelectFriend,
                onOpenProfile = onOpenProfile,
                modifier = Modifier
                    .weight(0.26f)
                    .fillMaxHeight(),
            )
            VerticalDivider(thickness = 1.dp, color = SectionDivider)
            Box(
                modifier = Modifier
                    .weight(0.74f)
                    .fillMaxHeight(),
            ) {
                if (selectedFriend != null) {
                    // No per-chat back arrow in two-pane — the list is always on-screen.
                    ChatScreen(friend = selectedFriend, onBack = {}, showBackButton = false)
                } else {
                    EmptyChatState()
                }
            }
        }
    }
}

/**
 * The friends/chat OFF-state — shown (in both portrait and landscape) when the feature is opted out
 * [SteamFriendsStore.socialEnabled] == false. Keeps the top bar (back + connection pill + the settings
 * cog) so the user isn't stranded, and centers a short explainer + an Enable button that flips the
 * master opt-in on. Enabling flips [SteamFriendsStore.socialEnabled], which live-swaps this for the
 * roster / two-pane; the mirrored store cog stays in sync through the same flow. The add / refresh
 * actions are omitted here — there's nothing to add to or refresh while dormant.
 */
@Composable
internal fun FriendsOffState(onBack: () -> Unit, showBackButton: Boolean = true) {
    val ctx = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        FriendsTopBar(onBack = onBack, onSettings = { showSettings = true }, showBackButton = showBackButton)
        if (showSettings) {
            SteamSocialSettingsSheet(onDismiss = { showSettings = false })
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Steam friends & chat is off",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Turn it on to see your friends list and their presence, and to send and receive chat " +
                    "messages. While it's off, no online status is shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { SteamFriendsStore.setSocialEnabled(ctx, true) }) {
                Text("Enable")
            }
        }
    }
}

/** Right-pane placeholder shown until a friend is picked in the landscape two-pane layout. */
@Composable
private fun EmptyChatState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Select a friend to start chatting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * All / Online / In-Game — the storefront Friends tab's filter chips, applied on top of the
 * existing presence grouping rather than replacing it (a friend still lands in their In game /
 * Online / Away / Offline section; the chips just narrow which sections have anything in them).
 */
enum class FriendPresenceFilter(val label: String) {
    ALL("All"),
    ONLINE("Online"),
    IN_GAME("In-Game"),
    ;

    fun accepts(p: SteamFriendsStore.Presence): Boolean = when (this) {
        ALL -> true
        // "Online" means reachable — in-game and away count, offline and unconfirmed don't.
        ONLINE -> p == SteamFriendsStore.Presence.IN_GAME ||
            p == SteamFriendsStore.Presence.ONLINE ||
            p == SteamFriendsStore.Presence.AWAY
        IN_GAME -> p == SteamFriendsStore.Presence.IN_GAME
    }
}

/**
 * The roster body: connect / loading states and the grouped, collapsible friend list with the pinned
 * friend-requests block, long-press menu, and unread badges. Extracted so the single-pane and the
 * landscape two-pane layouts share one implementation. [selectedFriendId] highlights the open chat's
 * row (two-pane only); [showFilter] adds a client-side "Search friends" box atop the list;
 * [presenceFilter] applies the storefront tab's All/Online/In-Game chips.
 *
 * `internal` (was private) so the storefront's Friends tab composes this exact roster instead of
 * growing a second one — it wants its own header/chips above it, not [FriendsListScreen]'s app bar.
 */
@Composable
internal fun FriendsListBody(
    available: Boolean,
    selectedFriendId: Long?,
    showFilter: Boolean,
    onOpenChat: (SteamFriendsStore.SteamFriend) -> Unit,
    onOpenProfile: (SteamFriendsStore.SteamFriend) -> Unit,
    modifier: Modifier = Modifier,
    presenceFilter: FriendPresenceFilter = FriendPresenceFilter.ALL,
) {
    val friends by SteamFriendsStore.friends.collectAsState()
    val incomingReq by SteamFriendsStore.incomingRequests.collectAsState()
    val outgoingReq by SteamFriendsStore.outgoingRequests.collectAsState()
    var menuFriend by remember { mutableStateOf<SteamFriendsStore.SteamFriend?>(null) }
    var filter by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    // One-shot Add-a-friend feedback (request sent / lookup / errors) as a toast.
    val addFeedback by SteamFriendsStore.addFeedback.collectAsState()
    LaunchedEffect(addFeedback) {
        addFeedback?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            SteamFriendsStore.clearAddFeedback()
        }
    }

    menuFriend?.let { f ->
        FriendActionsDialog(
            friend = f,
            onDismiss = { menuFriend = null },
            onMessage = { menuFriend = null; onOpenChat(f) },
            onJoin = {
                menuFriend = null
                runCatching {
                    ctx.startActivity(
                        Intent(ctx, SteamGameDetailActivity::class.java)
                            .putExtra(SteamGameDetailActivity.EXTRA_APP_ID, f.gameAppId),
                    )
                }
            },
            onProfile = {
                // Open our own in-app profile screen instead of jumping to the browser. The browser
                // option now lives on that screen's "View full profile on Steam" row / overflow.
                menuFriend = null
                onOpenProfile(f)
            },
            onRemove = {
                menuFriend = null
                SteamFriendsStore.removeFriend(f.steamId)
            },
        )
    }

    Column(modifier = modifier) {
        if (showFilter) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                placeholder = { Text("Search friends") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        when {
            !available -> ConnectState()
            friends.isEmpty() && incomingReq.isEmpty() && outgoingReq.isEmpty() -> LoadingState()
            else -> {
                // The filter (landscape-only; always blank in single-pane) narrows the roster by name.
                // The pinned request block is hidden while a filter is active so search focuses on friends.
                val q = filter.trim()
                val shownFriends = friends
                    .filter { presenceFilter.accepts(it.presence) }
                    .let { list ->
                        if (q.isEmpty()) list
                        else list.filter { it.displayName.contains(q, ignoreCase = true) }
                    }
                val grouped = shownFriends.groupBy { it.presence }
                val unread by SteamFriendsStore.unread.collectAsState()
                // Per-section collapse (arrow folds a group away); persisted so it's remembered.
                val collapsedSections by SteamFriendsStore.collapsedSections.collectAsState()
                // Live "typing…" on a friend's row — a 1s ticker expires it (same as the chat header).
                val typingMap by SteamFriendsStore.typing.collectAsState()
                var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
                val listState = rememberLazyListState()
                // A freshly opened list always starts at the top.
                LaunchedEffect(Unit) { listState.scrollToItem(0) }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // Pending friend-request invites, pinned at the very top (hidden while filtering).
                    if (q.isEmpty() && (incomingReq.isNotEmpty() || outgoingReq.isNotEmpty())) {
                        item(key = "req_hdr") { RequestsHeader(incomingReq.size + outgoingReq.size) }
                        items(incomingReq, key = { "in_${it.steamId}" }) { f ->
                            IncomingRequestRow(
                                friend = f,
                                onAccept = { SteamFriendsStore.acceptRequest(f.steamId) },
                                onDecline = { SteamFriendsStore.declineRequest(f.steamId) },
                            )
                            FriendDivider()
                        }
                        items(outgoingReq, key = { "out_${it.steamId}" }) { f ->
                            OutgoingRequestRow(friend = f, onCancel = { SteamFriendsStore.cancelRequest(f.steamId) })
                            FriendDivider()
                        }
                    }
                    // Filtered/searched down to nothing: say so, rather than showing a blank column.
                    if (shownFriends.isEmpty()) {
                        item(key = "no_match") {
                            Text(
                                text = if (q.isNotEmpty()) "No friends match “$q”."
                                else "Nobody is ${presenceFilter.label.lowercase()} right now.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                            )
                        }
                    }
                    var firstSection = q.isNotEmpty() || (incomingReq.isEmpty() && outgoingReq.isEmpty())
                    for ((presence, label) in PRESENCE_ORDER) {
                        val group = grouped[presence].orEmpty()
                        if (group.isEmpty()) continue
                        val isCollapsed = presence in collapsedSections
                        val showTop = !firstSection
                        firstSection = false
                        item(key = "hdr_$presence") {
                            SectionHeader(
                                label = label,
                                count = group.size,
                                collapsed = isCollapsed,
                                showTopDivider = showTop,
                                onToggle = { SteamFriendsStore.setSectionCollapsed(presence, !isCollapsed) },
                            )
                        }
                        if (!isCollapsed) {
                            itemsIndexed(group, key = { _, f -> f.steamId }) { index, friend ->
                                FriendRow(
                                    friend = friend,
                                    unread = unread[friend.steamId] ?: 0,
                                    selected = friend.steamId == selectedFriendId,
                                    typing = (typingMap[friend.steamId] ?: 0L) > nowMs,
                                    onClick = { onOpenChat(friend) },
                                    onLongClick = { menuFriend = friend },
                                )
                                if (index < group.lastIndex) FriendDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendActionsDialog(
    friend: SteamFriendsStore.SteamFriend,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onJoin: () -> Unit,
    onProfile: () -> Unit,
    onRemove: () -> Unit,
) {
    val inGame = friend.presence == SteamFriendsStore.Presence.IN_GAME && friend.gameAppId != 0
    var confirmRemove by remember { mutableStateOf(false) }
    // Compact, outlined pop-up menu that hugs its options with a divider between each.
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MenuOutline),
            tonalElevation = 6.dp,
            modifier = Modifier.width(280.dp),
        ) {
            Column {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                )
                HorizontalDivider(thickness = 1.dp, color = SectionDivider)
                MenuActionRow("Message", onClick = onMessage)
                if (inGame) {
                    HorizontalDivider(thickness = 1.dp, color = RowDivider)
                    MenuActionRow("Join ${friend.gameName ?: "game"}", onClick = onJoin)
                }
                HorizontalDivider(thickness = 1.dp, color = RowDivider)
                MenuActionRow("View Steam profile", onClick = onProfile)
                HorizontalDivider(thickness = 1.dp, color = RowDivider)
                if (!confirmRemove) {
                    MenuActionRow("Remove friend", tint = MaterialTheme.colorScheme.error) { confirmRemove = true }
                } else {
                    MenuActionRow("Tap again to confirm removal", tint = MaterialTheme.colorScheme.error, onClick = onRemove)
                }
            }
        }
    }
}

/** A single full-width, compact, tappable row inside a pop-up menu. */
@Composable
private fun MenuActionRow(
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

/** Pinned header for the pending friend-requests block at the top of the list. */
@Composable
private fun RequestsHeader(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Friend requests — $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** An incoming friend request: someone wants to add you. Accept adds them; Decline ignores it. */
@Composable
private fun IncomingRequestRow(
    friend: SteamFriendsStore.SteamFriend,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(friend = friend, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Wants to add you",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAccept, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            Text("Accept")
        }
        TextButton(onClick = onDecline, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text("Decline")
        }
    }
}

/** An outgoing (pending) friend request we've sent. Cancel withdraws it. */
@Composable
private fun OutgoingRequestRow(
    friend: SteamFriendsStore.SteamFriend,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(friend = friend, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Pending — request sent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            Text("Cancel")
        }
    }
}

/**
 * Steam's own Add-a-friend methods, mirrored: your Friend Code (copyable), add by a friend's code,
 * a live community search by name (avatar + Add per result), and — where the session supports it — a
 * shareable Quick Invite link.
 */
@Composable
private fun AddFriendDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val myCode = remember { SteamFriendsStore.selfFriendCode() }

    var codeInput by remember { mutableStateOf("") }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SteamUserSearch.Result>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val outgoing by SteamFriendsStore.outgoingRequests.collectAsState()
    val friends by SteamFriendsStore.friends.collectAsState()
    val outgoingIds = remember(outgoing) { outgoing.map { it.steamId }.toHashSet() }
    val friendIds = remember(friends) { friends.map { it.steamId }.toHashSet() }
    var justRequested by remember { mutableStateOf(setOf<Long>()) }

    // Quick-invite state, hoisted so both the portrait single-column and the landscape two-column
    // arrangements render the same section.
    val scope = rememberCoroutineScope()
    var inviteLink by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    fun generateInvite() {
        if (generating) return
        generating = true
        scope.launch {
            val link = withContext(Dispatchers.IO) { SteamQuickInvite.create() }
            inviteLink = link
            generating = false
            if (link == null) Toast.makeText(ctx, "Couldn't create an invite link", Toast.LENGTH_SHORT).show()
        }
    }

    // Debounced community search.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        val r = withContext(Dispatchers.IO) { SteamUserSearch.search(q) }
        results = r
        searching = false
    }

    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= 600

    // ── Section blocks. Identical content; only their arrangement changes with orientation (one tall
    // scroll column in portrait, two side-by-side columns in landscape). Pure layout, no logic change.
    val friendCodeSection: @Composable () -> Unit = {
        Column {
            Text("Your Friend Code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = myCode ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = {
                        myCode?.let {
                            clipboard.setText(AnnotatedString(it))
                            Toast.makeText(ctx, "Friend code copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = myCode != null,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) { Text("Copy") }
            }
            Text(
                "Share this so friends can add you — it's your Steam account number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    val addByCodeSection: @Composable () -> Unit = {
        Column {
            Text("Add by Friend Code", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    singleLine = true,
                    placeholder = { Text("Enter a Friend Code") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { SteamFriendsStore.addByFriendCode(codeInput); codeInput = "" },
                    enabled = codeInput.isNotBlank(),
                ) { Text("Add") }
            }
            Text(
                "Enter a friend's code to send a request. A full SteamID64 works too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    val searchSection: @Composable () -> Unit = {
        Column {
            Text("Search Steam by name", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search for a player…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                ) {
                    results.forEachIndexed { i, r ->
                        if (i > 0) FriendDivider()
                        SearchResultRow(
                            result = r,
                            state = when {
                                r.steamId64 in friendIds -> ResultState.FRIEND
                                r.steamId64 in outgoingIds || r.steamId64 in justRequested -> ResultState.PENDING
                                else -> ResultState.ADD
                            },
                            onAdd = {
                                SteamFriendsStore.addFriendById(r.steamId64)
                                justRequested = justRequested + r.steamId64
                            },
                        )
                    }
                }
            } else if (query.trim().length >= 2 && !searching) {
                Text(
                    "No players found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "Tap Add next to the right person — avatars & names help you pick the correct account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    val quickInviteSection: @Composable () -> Unit = {
        Column {
            Text("Or send a Quick Invite", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val link = inviteLink
            if (link == null) {
                Button(onClick = { generateInvite() }, enabled = !generating) {
                    Text(if (generating) "Generating…" else "Generate invite link")
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = link,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString(link))
                                Toast.makeText(ctx, "Invite link copied", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) { Text("Copy") }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { generateInvite() }, enabled = !generating) {
                        Text(if (generating) "Generating…" else "Generate new link")
                    }
                }
            }
            Text(
                "A one-time link to share by message. Your friend is added instantly when they open it. Expires after 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    AlertDialog(
        modifier = if (landscape) {
            Modifier
                .widthIn(max = 800.dp)
                .fillMaxWidth(0.92f)
                .border(1.dp, MenuOutline, RoundedCornerShape(28.dp))
        } else {
            Modifier.border(1.dp, MenuOutline, RoundedCornerShape(28.dp))
        },
        // Landscape needs a wider-than-platform-default dialog for the two columns; portrait keeps the default.
        properties = if (landscape) DialogProperties(usePlatformDefaultWidth = false) else DialogProperties(),
        shape = RoundedCornerShape(28.dp),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Add a friend")
            }
        },
        text = {
            if (landscape) {
                // Two columns side-by-side: friend code + add-by-code on the left, search + quick invite
                // on the right. Each column scrolls independently as a safety net for long search results.
                // Cap the height to the (short) landscape screen so the dialog never overflows.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (configuration.screenHeightDp * 0.72f).dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(end = 16.dp),
                    ) {
                        friendCodeSection()
                        SectionGap()
                        addByCodeSection()
                    }
                    VerticalDivider(thickness = 1.dp, color = SectionDivider)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp),
                    ) {
                        searchSection()
                        SectionGap()
                        quickInviteSection()
                    }
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    friendCodeSection()
                    SectionGap()
                    addByCodeSection()
                    SectionGap()
                    searchSection()
                    SectionGap()
                    quickInviteSection()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private enum class ResultState { ADD, PENDING, FRIEND }

@Composable
private fun SearchResultRow(result: SteamUserSearch.Result, state: ResultState, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (result.avatarUrl != null) {
                AsyncImage(
                    model = result.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.personaName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when (state) {
                ResultState.FRIEND -> "Already your friend"
                ResultState.PENDING -> "Request pending"
                ResultState.ADD -> "Steam profile"
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        when (state) {
            ResultState.ADD -> Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) { Text("Add") }
            ResultState.PENDING -> TextButton(onClick = {}, enabled = false) { Text("Pending") }
            ResultState.FRIEND -> TextButton(onClick = {}, enabled = false) { Text("Friend") }
        }
    }
}

@Composable
private fun SectionGap() {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(thickness = 1.dp, color = SectionDivider)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun ConnectState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Not connected to Steam",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Connect to Steam to see your friends and chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { runCatching { SteamRepository.getInstance().reconnectNow() } }) {
            Text("Connect")
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Loading friends…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SectionHeader(
    label: String,
    count: Int,
    collapsed: Boolean,
    showTopDivider: Boolean,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTopDivider) {
            HorizontalDivider(thickness = 1.dp, color = SectionDivider)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label — $count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand $label" else "Collapse $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (collapsed) 180f else 0f),
            )
        }
    }
}

/** Thin gray line between friends within a section (pop-up-menu style, inset from the edges). */
@Composable
internal fun FriendDivider() {
    HorizontalDivider(
        thickness = 0.7.dp,
        color = RowDivider,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** Unread-count pill (e.g. a friend has sent messages you haven't opened). */
@Composable
internal fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .widthIn(min = 20.dp)
            .height(20.dp)
            .clip(CircleShape)
            .background(UnreadBg)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun FriendRow(
    friend: SteamFriendsStore.SteamFriend,
    unread: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    typing: Boolean = false,
) {
    // Selected (two-pane) rows get an orange left bar + a faint accent tint, matching the mockup.
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(accent.copy(alpha = 0.14f)) else Modifier)
            .drawBehind { if (selected) drawRect(color = accent, size = Size(3.dp.toPx(), size.height)) }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(friend = friend, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (typing) "typing…" else friend.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    typing -> MaterialTheme.colorScheme.primary
                    friend.presence == SteamFriendsStore.Presence.IN_GAME -> DotInGame
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unread > 0) {
            Spacer(Modifier.width(8.dp))
            UnreadBadge(unread)
        }
    }
}

/** Circular avatar: Steam CDN image over an initials chip (initials show if the image is absent/fails),
 *  with a presence dot in the lower-right corner. `internal` so the friend-profile screen reuses it. */
@Composable
internal fun FriendAvatar(friend: SteamFriendsStore.SteamFriend, size: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(friend.displayName),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            val url = friend.avatarUrl
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            }
        }
        // Presence dot, bottom-right, ringed by the background so it reads against the avatar.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.30f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(dotColor(friend.presence)),
            )
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

// ── Chat ────────────────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    friend: SteamFriendsStore.SteamFriend,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    val session by SteamFriendsStore.chat.collectAsState()
    // Only show messages once openChat has switched the shared flow to this friend.
    val messages = if (session.steamId == friend.steamId) session.messages else emptyList()

    LaunchedEffect(friend.steamId) { SteamFriendsStore.openChat(friend.steamId) }

    // Typing indicator: a friend is "typing" until their expiry passes; a 1s ticker re-evaluates.
    val typingMap by SteamFriendsStore.typing.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
    val isTyping = (typingMap[friend.steamId] ?: 0L) > nowMs
    val self by SteamFriendsStore.self.collectAsState()

    // App's built-in File Manager in pick mode (InAppFilePicker/FilePickerActivity), filtered to images.
    // The picked path's bytes are read + uploaded off the main thread; SteamFriendsStore.sendImage
    // handles the token mint + community-host upload.
    val ctx = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val path = if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedPath(result.data) else null
        if (path != null) {
            val name = path.substringAfterLast('/')
            Thread {
                try {
                    val bytes = java.io.File(path).readBytes()
                    if (bytes.isNotEmpty()) SteamFriendsStore.sendImage(friend.steamId, bytes, name)
                } catch (_: Throwable) {
                }
            }.start()
        }
    }

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val chatScope = rememberCoroutineScope()
    // Re-pin to the newest message whenever the message area SHRINKS (keyboard opening via adjustResize +
    // imePadding) so the latest chat stays visible above the keyboard. Height-based, because IME insets
    // aren't exposed to Compose under this Activity's config (adjustResize, non-edge-to-edge).
    var lastListHeight by remember { mutableStateOf(0) }
    // Open at the first unread message (captured before openChat clears the count), else the bottom.
    // New messages arriving while viewing animate to the bottom.
    val unreadAtOpen = remember(friend.steamId) { SteamFriendsStore.unread.value[friend.steamId] ?: 0 }
    var didInitialScroll by remember(friend.steamId) { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!didInitialScroll) {
            val target = if (unreadAtOpen in 1..messages.size) messages.size - unreadAtOpen else messages.size - 1
            listState.scrollToItem(target.coerceIn(0, messages.size - 1))
            didInitialScroll = true
        } else {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),   // keep the composer above the on-screen keyboard
    ) {
        // Header: back + avatar + name/status.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }
            FriendAvatar(friend = friend, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isTyping) "typing…" else friend.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isTyping -> MaterialTheme.colorScheme.primary
                        friend.presence == SteamFriendsStore.Presence.IN_GAME -> DotInGame
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Messages.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Text(
                    text = "No messages yet. Say hello!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { sz ->
                            // Message area shrank (keyboard opened) → pin to the newest message.
                            if (sz.height < lastListHeight && messages.isNotEmpty()) {
                                chatScope.launch { listState.animateScrollToItem(messages.size - 1) }
                            }
                            lastListHeight = sz.height
                        },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages) { msg -> MessageBubble(msg, friend, self) }
                }
            }
        }

        // Composer (emoji quick-picker + input + send).
        var showEmoji by remember { mutableStateOf(false) }
        Column {
            if (showEmoji) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(EMOJIS) { e ->
                        Text(
                            text = e,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { draft += e }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showEmoji = !showEmoji }) {
                    Text("😊", fontSize = 20.sp)
                }
                IconButton(
                    onClick = {
                        pickImage.launch(
                            InAppFilePicker.buildIntent(ctx, InAppFilePicker.IMAGES, "Send an image"),
                        )
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "Send image",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Paste from the system clipboard: an image is sent as a chat image, text is inserted
                // into the draft. Works around the composer field not accepting keyboard rich-content
                // ("does not support image pasting here") on this Compose version.
                IconButton(
                    onClick = {
                        try {
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            val item = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                            val uri = item?.uri
                            val mime = uri?.let { ctx.contentResolver.getType(it) }
                            if (uri != null && mime?.startsWith("image/") == true) {
                                val sid = friend.steamId
                                Thread {
                                    try {
                                        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            SteamFriendsStore.sendImage(sid, bytes, "pasted.${mime.substringAfter('/')}")
                                        }
                                    } catch (_: Throwable) {}
                                }.start()
                            } else {
                                val text = item?.coerceToText(ctx)?.toString()
                                if (!text.isNullOrEmpty()) draft += text
                                else Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Throwable) {
                            Toast.makeText(ctx, "Couldn't paste", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = "Paste from clipboard",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; if (it.isNotEmpty()) SteamFriendsStore.sendTyping(friend.steamId) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        val body = draft.trim()
                        if (body.isNotEmpty()) {
                            SteamFriendsStore.sendMessage(friend.steamId, body)
                            draft = ""
                        }
                    }),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val body = draft.trim()
                        if (body.isNotEmpty()) {
                            SteamFriendsStore.sendMessage(friend.steamId, body)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// A small, dependency-free set of common emojis for the composer quick-picker (the system keyboard's
// full emoji set still works too — this is just a fast tap-to-insert strip).
private val EMOJIS = listOf(
    "😀", "😂", "😁", "😊", "😍", "😎", "🤔", "😅", "😭", "😡",
    "👍", "👎", "👌", "🙏", "👏", "💪", "🔥", "🎉", "❤️", "💯",
    "🎮", "🕹️", "😴", "🤝", "👀", "✅", "❌", "⭐", "😢", "🤣",
)

@Composable
internal fun MessageBubble(
    msg: SteamFriendsStore.ChatMessage,
    friend: SteamFriendsStore.SteamFriend,
    self: SteamFriendsStore.SteamFriend?,
) {
    val mine = msg.fromSelf
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        // Received: friend's avatar on the left, next to the bubble.
        if (!mine) {
            FriendAvatar(friend = friend, size = 30.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            // Sender name heading above each bubble ("You" for our own messages).
            Text(
                text = if (mine) (self?.displayName ?: "You") else friend.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                val imageUrl = remember(msg.text) { imageUrlOrNull(msg.text) }
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Image",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mine) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (msg.timestampSec > 0) {
                    Text(
                        text = timeLabel(msg.timestampSec),
                        fontSize = 10.sp,
                        color = (if (mine) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .align(if (mine) Alignment.End else Alignment.Start),
                    )
                }
            }
        }
        // Sent: our own avatar on the right, mirroring the friend's on the left.
        if (mine && self != null) {
            Spacer(Modifier.width(8.dp))
            FriendAvatar(friend = self, size = 30.dp)
        }
    }
}

private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun timeLabel(sec: Long): String = TIME_FMT.format(Date(sec * 1000L))

/** Best-effort display name for a picked image [uri] (only used as Steam upload metadata). */
private fun displayNameFromUri(ctx: android.content.Context, uri: Uri): String {
    return try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)?.takeIf { it.isNotBlank() } else null
            } else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "image.jpg"
    } catch (_: Throwable) {
        "image.jpg"
    }
}

private val URL_RE = Regex("""https?://\S+""")

/** If the whole message is essentially one image URL, return it (render as an image); else null. */
private fun imageUrlOrNull(text: String): String? {
    val t = text.trim()
    val url = URL_RE.find(t)?.value ?: return null
    if (t != url) return null // mixed text + link stays text
    val bare = url.substringBefore('?').lowercase()
    val isImgExt = bare.endsWith(".jpg") || bare.endsWith(".jpeg") || bare.endsWith(".png") ||
        bare.endsWith(".gif") || bare.endsWith(".webp")
    val isSteamImg = url.contains("steamusercontent.com") || url.contains("steamcdn") || url.contains("/ugc/")
    return if (isImgExt || isSteamImg) url else null
}
