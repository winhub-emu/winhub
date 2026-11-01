package com.winhub.hernandez.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.FriendAvatar
import com.winhub.hernandez.store.FriendDivider
import com.winhub.hernandez.store.FriendRow
import com.winhub.hernandez.store.InGameFriendsSource
import com.winhub.hernandez.store.MessageBubble
import com.winhub.hernandez.store.PRESENCE_ORDER
import com.winhub.hernandez.store.SectionHeader
import com.winhub.hernandez.store.SteamAgentFriendsBridge
import com.winhub.hernandez.store.SteamFriendsStore
import com.winhub.hernandez.store.dotColor
import com.winhub.hernandez.ui.theme.LocalAccentDim
import kotlinx.coroutines.delay

// ───── Friends tab (in-game drawer) ─────
// Steam friends + 1:1 text chat while the game keeps running underneath. Renders straight off
// SteamFriendsStore's flows — the same roster / unread / typing / chat the full Friends screen uses,
// with its row, header and bubble composables (store/SteamFriendsScreen.kt, `internal`). Which live
// session feeds those flows (agent relay during a SteamLite game vs the app's own session) is
// InGameFriendsSource's problem; the tab only asks it whether a source is live. Text only — image
// send / paste / emoji strip / profiles stay on the full screen.
//
// Chat-open bookkeeping: the store treats the "active" thread as read (no unread bump, no shade
// notification). The drawer's ComposeView stays composed while the drawer is closed, so the thread is
// opened only while the drawer is actually open (XServerDialogState.menuOpen) and closed the moment
// it isn't — a message that lands while the drawer is shut still counts as unread + notifies exactly
// as before, and shows as the dot on the rail button.

private val UnreadDot = Color(0xFFE53935)

private const val HELP_TEXT =
    "Chat with Steam friends while you play. Shown when friends/chat is enabled; during SteamLite " +
        "games it works through the in-game Steam client."

/** Rail button: the TabIconButton look with a People glyph and an unread dot in the corner. */
@Composable
internal fun FriendsTabButton(isSelected: Boolean, unread: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val accentDim = LocalAccentDim.current
    val bgBrush = if (isSelected)
        Brush.verticalGradient(listOf(accent, accentDim))
    else
        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))

    val borderColor = if (isSelected) accent.copy(alpha = 0.6f) else Color(0xFF333333)
    val tintColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush, RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Canvas(Modifier.size(44.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.25f), Color.Transparent),
                        radius = size.minDimension / 2f
                    ),
                    radius = size.minDimension / 2f
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.People,
            contentDescription = "Friends",
            tint = tintColor,
            modifier = Modifier.size(22.dp),
        )
        if (unread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(UnreadDot)
            )
        }
    }
}

@Composable
internal fun FriendsContent(state: XServerDrawerState) {
    val src by InGameFriendsSource.state.collectAsState()
    val selectedId by InGameFriendsSource.selectedFriendId.collectAsState()
    val friends by SteamFriendsStore.friends.collectAsState()
    val relayPresence by SteamAgentFriendsBridge.presence.collectAsState()
    var showHelp by remember { mutableStateOf(false) }

    // The source vanished entirely (signed out mid-game): the rail button is gone too, so don't
    // leave an orphaned pane up.
    LaunchedEffect(src.kind) { if (!src.tabVisible) state.selectTab(TabType.GRAPHICS) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: title + "?" help, and which session is feeding the tab.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Friends",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            HelpDot { showHelp = true }
            Spacer(Modifier.weight(1f))
            Text(
                when (src.kind) {
                    InGameFriendsSource.Kind.AGENT_RELAY -> "via in-game Steam"
                    InGameFriendsSource.Kind.APP_SESSION -> "via app session"
                    else -> ""
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        // Relay warm-up: the in-game client asks Steam about each friend after the roster arrives
        // (agent p3c); until every friend is confirmed the unconfirmed ones sit under "Status unknown"
        // (or keep their last-known state), never "Offline".
        if (src.kind == InGameFriendsSource.Kind.AGENT_RELAY && !relayPresence.complete) {
            Text(
                "presence: ${relayPresence.known} of ${relayPresence.total} known",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
        Spacer(Modifier.height(8.dp))

        if (showHelp) {
            AlertDialog(
                onDismissRequest = { showHelp = false },
                title = { Text("Friends") },
                text = { Text(HELP_TEXT) },
                confirmButton = { TextButton(onClick = { showHelp = false }) { Text("OK") } },
            )
        }

        when {
            src.kind == InGameFriendsSource.Kind.RELAY_STOPPED -> Text(
                InGameFriendsSource.RELAY_STOPPED_TEXT,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            selectedId != 0L -> {
                // Prefer the live roster entry (presence updates); fall back to the store's snapshot.
                val friend = friends.firstOrNull { it.steamId == selectedId }
                    ?: SteamFriendsStore.friendById(selectedId)
                InGameChatThread(friend = friend, onBack = { InGameFriendsSource.selectFriend(0L) })
            }
            else -> InGameFriendsList(friends = friends) { InGameFriendsSource.selectFriend(it.steamId) }
        }
    }
}

/** The small circular "?" that opens the tab's help (same look as the launch sheet's HelpDot). */
@Composable
private fun HelpDot(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(16.dp).clip(CircleShape)
            .background(cs.surfaceVariant)
            .border(1.dp, cs.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("?", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

/** Grouped roster (In game / Online / Away / Offline), same sections + collapse state as the full screen. */
@Composable
private fun InGameFriendsList(
    friends: List<SteamFriendsStore.SteamFriend>,
    onOpen: (SteamFriendsStore.SteamFriend) -> Unit,
) {
    val unread by SteamFriendsStore.unread.collectAsState()
    val collapsedSections by SteamFriendsStore.collapsedSections.collectAsState()
    val typingMap by SteamFriendsStore.typing.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }

    if (friends.isEmpty()) {
        Text(
            "Waiting for your friends list…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        return
    }
    val grouped = friends.groupBy { it.presence }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        var firstSection = true
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
                        typing = (typingMap[friend.steamId] ?: 0L) > nowMs,
                        onClick = { onOpen(friend) },
                        onLongClick = { onOpen(friend) },   // no per-friend menu in-game
                    )
                    if (index < group.lastIndex) FriendDivider()
                }
            }
        }
    }
}

/** One conversation: header (back / avatar / name / status), history + incoming, text send box. */
@Composable
private fun InGameChatThread(friend: SteamFriendsStore.SteamFriend, onBack: () -> Unit) {
    val menuOpen by XServerDialogState.menuOpen.collectAsState()
    val session by SteamFriendsStore.chat.collectAsState()
    val self by SteamFriendsStore.self.collectAsState()
    val messages = if (session.steamId == friend.steamId) session.messages else emptyList()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Active only while the drawer is open (see the file comment); closed on the way out so a
    // message that lands while the drawer is shut is unread + notifies as usual.
    LaunchedEffect(friend.steamId, menuOpen) {
        if (menuOpen) {
            SteamFriendsStore.openChat(friend.steamId)
        } else {
            SteamFriendsStore.closeChat()
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    DisposableEffect(friend.steamId) { onDispose { SteamFriendsStore.closeChat() } }

    // Typing indicator: the friend is "typing" until their expiry passes; a 1 s ticker re-evaluates.
    val typingMap by SteamFriendsStore.typing.collectAsState()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(1000) } }
    val isTyping = (typingMap[friend.steamId] ?: 0L) > nowMs

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to friends",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            FriendAvatar(friend = friend, size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isTyping) "typing…" else friend.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isTyping) MaterialTheme.colorScheme.primary else dotColor(friend.presence),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages) { msg -> MessageBubble(msg, friend, self) }
                }
            }
        }

        // Send box. Text only in-game; the soft keyboard attaches to this field like any other
        // Compose field in the app (the game keeps running underneath).
        var draft by remember(friend.steamId) { mutableStateOf("") }
        val send = {
            val body = draft.trim()
            if (body.isNotEmpty()) {
                SteamFriendsStore.sendMessage(friend.steamId, body)
                draft = ""
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it; if (it.isNotEmpty()) SteamFriendsStore.sendTyping(friend.steamId) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = send, enabled = draft.isNotBlank()) {
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
