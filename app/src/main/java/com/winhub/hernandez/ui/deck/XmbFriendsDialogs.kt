package com.winhub.hernandez.ui.deck

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.winhub.hernandez.R
import com.winhub.hernandez.store.ChatScreen
import com.winhub.hernandez.store.DotInGame
import com.winhub.hernandez.store.FriendAvatar
import com.winhub.hernandez.store.SteamFriendsStore
import com.winhub.hernandez.store.SteamUserSearch
import com.winhub.hernandez.ui.DeckDialogBars
import com.winhub.hernandez.ui.DeckDialogScaled
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The windows the XMB Home's Friends tab opens over its list: a friend's chat, a friend's profile and Add friend. Each is a dialog in the
// XMB panel look (the same frame as XmbStoresDialog); B closes it. The chat is the store's own ChatScreen.

/** The frame of the Friends windows: a dimmed window with one panel, at most 760 dp wide, [fullHeight] tall or as tall as its content. */
@Composable
private fun XmbFriendsPanel(
    onDismiss: () -> Unit,
    fullHeight: Boolean,
    onKey: (KeyEvent) -> Boolean = { false },
    takeFocus: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = deckShape(22.dp)
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    // A key reaches the panel's handler only while something inside the dialog holds the focus: the panel's root takes it once, so B / Esc
    // close the window from the first press (a window whose content takes the focus itself, as the profile's first button, passes [takeFocus] false).
    val rootFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DeckDialogBars()
        if (takeFocus) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { rootFocus.requestFocus() }
            }
        }
        DeckDialogScaled {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (e.key == Key.ButtonB || e.key == Key.Escape) {
                            onDismiss()
                            true
                        } else {
                            onKey(e)
                        }
                    }
                    .then(if (takeFocus) Modifier.focusRequester(rootFocus).focusable() else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .then(if (fullHeight) Modifier.fillMaxHeight() else Modifier)
                        .xmbPanel(shape, fill),
                ) { content() }
            }
        }
    }
}

/** [friend]'s chat: the store's ChatScreen in a panel (its back arrow closes it). Closing tells the store the chat is no longer on screen. */
@Composable
internal fun XmbFriendChatDialog(friend: SteamFriendsStore.SteamFriend, onDismiss: () -> Unit) {
    DisposableEffect(Unit) { onDispose { SteamFriendsStore.closeChat() } }
    XmbFriendsPanel(onDismiss, fullHeight = true) {
        ChatScreen(friend = friend, onBack = onDismiss)
    }
}

/**
 * [friend]'s profile: who they are, what they play, the numbers Steam shows (only those their privacy settings give) and their recent games.
 * Message goes to the chat, View on Steam opens their community page. Up / Down scroll it.
 */
@Composable
internal fun XmbFriendProfileDialog(
    friend: SteamFriendsStore.SteamFriend,
    onMessage: () -> Unit,
    onViewOnSteam: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var profile by remember(friend.steamId) { mutableStateOf<SteamFriendsStore.FriendProfile?>(null) }
    var loading by remember(friend.steamId) { mutableStateOf(true) }
    LaunchedEffect(friend.steamId) {
        loading = true
        profile = runCatching { SteamFriendsStore.fetchProfile(friend.steamId) }.getOrNull()
        loading = false
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    XmbFriendsPanel(
        onDismiss,
        fullHeight = false,
        onKey = { e ->
            when (e.key) {
                Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(240f) }; true }
                Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-240f) }; true }
                else -> false
            }
        },
        // Its first button takes the focus (below).
        takeFocus = false,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val p = profile
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FriendAvatar(friend = friend, size = 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            friend.displayName, color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            friend.statusText,
                            color = if (friend.presence == SteamFriendsStore.Presence.IN_GAME) DotInGame else cs.onSurfaceVariant,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val info = listOfNotNull(
                            p?.realName?.takeIf { it.isNotBlank() },
                            p?.country?.takeIf { it.isNotBlank() },
                            p?.level?.let { stringResource(R.string.xmb_fr_level, it) },
                            p?.memberSince?.takeIf { it.isNotBlank() },
                        ).joinToString("  ·  ")
                        if (info.isNotEmpty()) {
                            Text(info, color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                when {
                    loading && p == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.xmb_fr_loading), color = cs.onSurfaceVariant, fontSize = 13.sp)
                    }
                    p == null || p.isEssentiallyEmpty ->
                        Text(stringResource(R.string.xmb_fr_profile_private), color = cs.onSurfaceVariant, fontSize = 13.sp)
                    else -> {
                        p.summary?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = cs.onSurface, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                        val stats = listOfNotNull(
                            p.gamesCount?.let { stringResource(R.string.xmb_fr_stat_games) to "%,d".format(it) },
                            p.hoursTotal?.let { stringResource(R.string.xmb_fr_stat_hours) to "%,d".format(it.roundToInt()) },
                            p.hoursTwoWeeks?.let { stringResource(R.string.xmb_fr_stat_two_weeks) to "%,d".format(it.roundToInt()) },
                        )
                        if (stats.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                stats.forEach { (label, value) ->
                                    Column {
                                        Text(value, color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(label, color = cs.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (p.recentGames.isNotEmpty()) {
                            Text(stringResource(R.string.xmb_fr_recent), color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                p.recentGames.forEach { game ->
                                    Column(Modifier.width(120.dp)) {
                                        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant)) {
                                            if (game.coverUrl != null) {
                                                AsyncImage(
                                                    model = game.coverUrl, contentDescription = game.name,
                                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                        Text(
                                            game.name, color = cs.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                                        )
                                        Text(
                                            stringResource(R.string.xmb_fr_hours, game.hours.roundToInt()),
                                            color = cs.onSurfaceVariant, fontSize = 11.sp, maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                DeckButton(stringResource(R.string.xmb_fr_message), null, onMessage, primary = true, modifier = Modifier.focusRequester(first))
                DeckButton(stringResource(R.string.xmb_fr_view_on_steam), null, onViewOnSteam)
                Spacer(Modifier.weight(1f))
                DeckButton(stringResource(R.string.xmb_fr_close), null, onDismiss, glyph = "B")
            }
        }
    }
}

/**
 * Add friend: your friend code to share (Copy), a search of Steam by name (an Add per player) and adding by a friend's code. Each sends a
 * friend request; the store answers with a toast (see SteamFriendsStore.addFeedback, which the page shows).
 */
@Composable
internal fun XmbAddFriendDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val myCode = remember { SteamFriendsStore.selfFriendCode() }
    val friends by SteamFriendsStore.friends.collectAsState()
    val outgoing by SteamFriendsStore.outgoingRequests.collectAsState()
    val friendIds = remember(friends) { friends.map { it.steamId }.toHashSet() }
    val outgoingIds = remember(outgoing) { outgoing.map { it.steamId }.toHashSet() }
    var justRequested by remember { mutableStateOf(setOf<Long>()) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SteamUserSearch.Result>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    // Debounced search of the community by name.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        results = withContext(Dispatchers.IO) { SteamUserSearch.search(q) }
        searching = false
    }

    // Up / Down move between the fields and buttons (Compose's own focus, which also scrolls the focused one into view).
    XmbFriendsPanel(onDismiss, fullHeight = false) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.xmb_fr_add_title), color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Search by name.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.xmb_fr_add_search_label), color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    XmbTapToEdit(Modifier.fillMaxWidth()) { fieldModifier, stopEditing ->
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.xmb_fr_add_search_hint)) },
                            trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = xmbDoneActions(stopEditing),
                            modifier = fieldModifier,
                        )
                    }
                    if (results.isEmpty() && query.trim().length >= 2 && !searching) {
                        Text(stringResource(R.string.xmb_fr_add_no_players), color = cs.onSurfaceVariant, fontSize = 12.sp)
                    }
                    results.forEach { r ->
                        val isFriend = r.steamId64 in friendIds
                        val pending = r.steamId64 in outgoingIds || r.steamId64 in justRequested
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant)) {
                                if (r.avatarUrl != null) {
                                    AsyncImage(model = r.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    r.personaName, color = cs.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        when {
                                            isFriend -> R.string.xmb_fr_result_friend
                                            pending -> R.string.xmb_fr_result_pending
                                            else -> R.string.xmb_fr_result_player
                                        },
                                    ),
                                    color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1,
                                )
                            }
                            if (!isFriend && !pending) {
                                DeckButton(
                                    stringResource(R.string.xmb_fr_add_button), null,
                                    {
                                        SteamFriendsStore.addFriendById(r.steamId64)
                                        justRequested = justRequested + r.steamId64
                                    },
                                )
                            }
                        }
                    }
                }
                // Add by a friend's code.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.xmb_fr_add_code_label), color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        XmbTapToEdit(Modifier.weight(1f)) { fieldModifier, stopEditing ->
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { codeInput = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.xmb_fr_add_code_hint)) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = xmbDoneActions(stopEditing),
                                modifier = fieldModifier,
                            )
                        }
                        DeckButton(
                            stringResource(R.string.xmb_fr_add_button), null,
                            {
                                if (codeInput.isNotBlank()) {
                                    SteamFriendsStore.addByFriendCode(codeInput)
                                    codeInput = ""
                                }
                            },
                        )
                    }
                }
                // Your own code, to share.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.xmb_fr_add_my_code), color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(myCode ?: "—", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    if (myCode != null) {
                        val copied = stringResource(R.string.xmb_fr_code_copied)
                        DeckButton(
                            stringResource(R.string.xmb_fr_copy), null,
                            {
                                clipboard.setText(AnnotatedString(myCode))
                                Toast.makeText(ctx, copied, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                DeckButton(stringResource(R.string.xmb_fr_done), null, onDismiss, primary = true, glyph = "B")
            }
        }
    }
}
