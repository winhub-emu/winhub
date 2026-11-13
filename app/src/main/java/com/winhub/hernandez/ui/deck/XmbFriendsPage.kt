package com.winhub.hernandez.ui.deck

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.winhub.hernandez.R
import com.winhub.hernandez.store.FriendAvatar
import com.winhub.hernandez.store.SteamFriendsStore
import com.winhub.hernandez.store.SteamMainActivity
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.SteamForegroundService
import com.winhub.hernandez.store.SteamSocialSettingsSheet
import com.winhub.hernandez.store.SteamStoreSearch
import com.winhub.hernandez.store.SteamTab
import com.winhub.hernandez.store.UnreadBadge
import com.winhub.hernandez.store.dotColor
import com.winhub.hernandez.store.friendsOnlineCount
import com.winhub.hernandez.store.initSteamFriends
import com.winhub.hernandez.store.openSteamGameDetail
import com.winhub.hernandez.store.openSteamProfileInBrowser
import com.winhub.hernandez.store.rememberFriendsAvailable
import com.winhub.hernandez.store.rememberSteamLoggedIn
import com.winhub.hernandez.store.rememberSteamOnlineFriends
import com.winhub.hernandez.ui.screens.XMB_ARMED_MS
import com.winhub.hernandez.ui.screens.XMB_TWO_COL_GAP_DP
import com.winhub.hernandez.ui.screens.XMB_TWO_COL_MAX_WIDTH_DP
import com.winhub.hernandez.ui.screens.XMB_TWO_COL_TO_BAR
import com.winhub.hernandez.ui.screens.XmbFriendAction
import com.winhub.hernandez.ui.screens.XmbFriendsCursor
import com.winhub.hernandez.ui.screens.XmbFriendsRow
import com.winhub.hernandez.ui.screens.xmbClick
import com.winhub.hernandez.ui.screens.xmbFriendActions
import com.winhub.hernandez.ui.screens.xmbFriendsButtonCount
import com.winhub.hernandez.ui.screens.xmbFriendsEmpty
import com.winhub.hernandez.ui.screens.xmbFriendsIsCard
import com.winhub.hernandez.ui.screens.xmbFriendsLines
import com.winhub.hernandez.ui.screens.xmbFriendsNormalise
import com.winhub.hernandez.ui.screens.xmbFriendsSideIsButtons
import com.winhub.hernandez.ui.screens.xmbFriendsVertical
import com.winhub.hernandez.ui.screens.xmbFriendsResolve
import com.winhub.hernandez.ui.screens.xmbFriendsRows
import com.winhub.hernandez.ui.screens.xmbTwoColCol
import com.winhub.hernandez.ui.screens.xmbTwoColColumns
import com.winhub.hernandez.ui.screens.xmbTwoColLineOf
import com.winhub.hernandez.ui.screens.xmbTwoColSide

// The Friends tab of the XMB Home's tabs page: the friends window of the old Steam Friends screen, rebuilt as a page. The roster in sections
// (In game, Online, Away, Offline, with the friend requests on top) under full-width headings, each friend a card with their avatar, status,
// the game they are playing and the unread count, the cards two to a line (one on a narrow screen). D-pad: Up / Down along the lines,
// Left / Right between the two columns, A opens a friend's actions (Message, Game page, Profile, Remove) or an incoming request's buttons
// (Accept, Decline) or folds a section away, and once they are open Left / Right move between the buttons, B closes them, X adds a friend.
// On the toolbar Left / Right move between its two buttons. A tap does the same as A. Chat, profile and Add friend open as windows over
// it (XmbFriendsDialogs.kt). Signed out, it shows a Sign in to Steam card instead.
//
// The rules (the rows, the cursor's moves, a friend's actions) are in screens/XmbFriendsLogic.kt; the logic shared with SteamFriendsActivity
// (signed in, friends available, the online count) is in store/SteamFriendsShared.kt.

/** The widest the list gets (dp): a block in the middle on a wide screen, as the Steam Deck's friends page. */
private val XMB_FR_MAX_WIDTH = XMB_TWO_COL_MAX_WIDTH_DP.dp

/** How long the roster shows "Loading" while it is empty, in ms: Steam sends the list a moment after the session is up. */
private const val XMB_FR_ROSTER_WAIT_MS = 6000L

/** The online-friends count for the pill bar's Friends badge (XmbTabsPageHost's badges): 0 while signed out or the friends feature is off. */
@Composable
internal fun rememberXmbFriendsBadge(): Int = rememberSteamOnlineFriends()

/** What a state that shows no roster says: a card with a title, a line and one button (A or a tap does it). */
private class XmbFriendsGate(val title: Int, val sub: Int, val button: Int, val onClick: () -> Unit)

/**
 * The Friends page of the XMB Home's tabs page. [focus] is what the host gives a page (see [XmbTabsPageFocus]): where the cursor is, the room to
 * keep clear, and the key hook for the D-pad.
 */
@Composable
internal fun XmbFriendsPage(focus: XmbTabsPageFocus) {
    val ctx = LocalContext.current
    remember(ctx) { initSteamFriends(ctx) }
    val socialEnabled by SteamFriendsStore.socialEnabled.collectAsState()
    val available = rememberFriendsAvailable()
    val signedInEvents = rememberSteamLoggedIn()
    // The sign-in screens are other activities: the state is read again whenever the app comes back.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                SteamFriendsStore.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val signedIn = remember(signedInEvents, resumeTick) { runCatching { SteamPrefs.isLoggedIn }.getOrDefault(false) }

    // A fresh roster as soon as the session is live and the feature on.
    LaunchedEffect(signedIn, available, socialEnabled) {
        if (signedIn && available && socialEnabled) SteamFriendsStore.refresh()
    }

    val gate = when {
        !signedIn -> XmbFriendsGate(R.string.xmb_fr_signin_title, R.string.xmb_fr_signin_sub, R.string.xmb_fr_signin_button) {
            // The store's own screen on its Friends tab: its sign-in first while signed out (as the Stores tab's Steam card).
            runCatching {
                ctx.startActivity(Intent(ctx, SteamMainActivity::class.java).putExtra(SteamMainActivity.EXTRA_TAB, SteamTab.FRIENDS.name))
            }
        }
        !socialEnabled -> XmbFriendsGate(R.string.xmb_fr_off_title, R.string.xmb_fr_off_sub, R.string.xmb_fr_off_button) {
            SteamFriendsStore.setSocialEnabled(ctx, true)
        }
        !available -> XmbFriendsGate(R.string.xmb_fr_disconnected_title, R.string.xmb_fr_disconnected_sub, R.string.xmb_fr_connect) {
            // Brings the Steam session up (initialises it and connects, as the store screens do on opening): the roster follows by itself.
            runCatching { SteamForegroundService.start(ctx) }
        }
        else -> null
    }
    if (gate != null) XmbFriendsGateCard(focus, gate) else XmbFriendsRoster(focus)
}

// ---------------------------------------------------------------------------------------------------------------------------------
// The states without a roster
// ---------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun XmbFriendsGateCard(focus: XmbTabsPageFocus, gate: XmbFriendsGate) {
    val cs = MaterialTheme.colorScheme
    val ss = rememberScreenScale()
    XmbTabsPageKeys(focus) { e ->
        when (e.key) {
            Key.DirectionUp -> { focus.toBar(); true }
            // A held key must not run it twice.
            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (e.nativeKeyEvent.repeatCount == 0) gate.onClick()
                true
            }
            else -> false
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxSize().padding(focus.contentPadding), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(shape)
                .background(deckCardFill())
                .border(1.dp, deckLine(), shape)
                .padding(horizontal = ss.dp(24f), vertical = ss.dp(22f)),
        ) {
            Icon(Icons.Filled.People, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(ss.dp(40f)))
            Spacer(Modifier.height(ss.dp(10f)))
            Text(
                stringResource(gate.title), color = cs.onSurface, fontSize = ss.sp(18f), fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(ss.dp(6f)))
            Text(stringResource(gate.sub), color = cs.onSurfaceVariant, fontSize = ss.sp(13f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(ss.dp(18f)))
            XmbFriendsButton(stringResource(gate.button), focus.contentFocused, ss, gate.onClick)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// The roster
// ---------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun XmbFriendsRoster(focus: XmbTabsPageFocus) {
    val ctx = LocalContext.current
    val ss = rememberScreenScale()
    val dir = LocalLayoutDirection.current
    val friends by SteamFriendsStore.friends.collectAsState()
    val incoming by SteamFriendsStore.incomingRequests.collectAsState()
    val outgoing by SteamFriendsStore.outgoingRequests.collectAsState()
    val collapsed by SteamFriendsStore.collapsedSections.collectAsState()
    val unread by SteamFriendsStore.unread.collectAsState()
    val rows = remember(friends, incoming, outgoing, collapsed) { xmbFriendsRows(friends, incoming, outgoing, collapsed) }
    val online = remember(friends) { friendsOnlineCount(friends) }

    // The store's answer to adding a friend (request sent, errors) as a toast, whichever window the request came from.
    val addFeedback by SteamFriendsStore.addFeedback.collectAsState()
    LaunchedEffect(addFeedback) {
        addFeedback?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            SteamFriendsStore.clearAddFeedback()
        }
    }

    // The cursor: the row it is on (by key, so it stays on its friend while the list changes under it), the button of the row it is on, and
    // the row whose buttons hold the cursor (a friend's actions, an incoming request's Accept / Decline; Remove waits for its second press).
    // [wantCol] is the column the cursor last chose, which a move up or down keeps (through a heading, say).
    var selKey by remember { mutableStateOf(XmbFriendsRow.Toolbar.key) }
    var sub by remember { mutableIntStateOf(0) }
    var openKey by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var wantCol by remember { mutableIntStateOf(0) }
    val lastIndex = remember { IntArray(1) }
    val selIdx = xmbFriendsResolve(rows, selKey, lastIndex[0])
    SideEffect { lastIndex[0] = selIdx }
    val selRow = rows[selIdx]
    // The row the cursor was on can go (the last friend removed, a request answered): [selIdx] is then the row it fell to, and the key and the
    // button state are put on that row, else the button number (the toolbar's Settings, say) would carry over to a row that has others.
    LaunchedEffect(rows, selKey, selIdx, sub, openKey, confirmRemove) {
        val now = XmbFriendsCursor(selKey, sub, openKey, confirmRemove)
        val fixed = xmbFriendsNormalise(rows, now, selIdx)
        if (fixed != now) {
            selKey = fixed.key
            sub = fixed.sub
            openKey = fixed.openKey
            confirmRemove = fixed.confirmRemove
        }
    }
    // Remove asked for a second press: it gives up waiting.
    LaunchedEffect(confirmRemove) {
        if (confirmRemove) {
            delay(XMB_ARMED_MS)
            confirmRemove = false
        }
    }
    // The roster is not known to be empty until Steam has had time to send it (the store has no "loaded" signal).
    var rosterWaited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(XMB_FR_ROSTER_WAIT_MS)
        rosterWaited = true
    }

    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var chatFriend by remember { mutableStateOf<SteamFriendsStore.SteamFriend?>(null) }
    var profileFriend by remember { mutableStateOf<SteamFriendsStore.SteamFriend?>(null) }
    val listState = rememberLazyListState()

    fun select(index: Int) {
        val row = rows[index.coerceIn(0, rows.lastIndex)]
        if (row.key == selRow.key) return
        selKey = row.key
        sub = 0
        openKey = null
        confirmRemove = false
    }

    // Accept (button 0) or Decline an incoming request.
    fun answerRequest(friend: SteamFriendsStore.SteamFriend, button: Int) {
        openKey = null
        if (button == 0) SteamFriendsStore.acceptRequest(friend.steamId) else SteamFriendsStore.declineRequest(friend.steamId)
    }

    // What A (or a tap) does on [row]'s [button]: on a card with buttons it steps into them first, a second A presses one.
    fun act(row: XmbFriendsRow, button: Int) {
        when (row) {
            is XmbFriendsRow.Toolbar -> if (button == 0) showAdd = true else showSettings = true
            is XmbFriendsRow.Header -> SteamFriendsStore.setSectionCollapsed(row.presence, !row.collapsed)
            is XmbFriendsRow.Incoming -> if (openKey != row.key) {
                openKey = row.key
                sub = 0
            } else {
                answerRequest(row.friend, button)
            }
            is XmbFriendsRow.Outgoing -> SteamFriendsStore.cancelRequest(row.friend.steamId)
            is XmbFriendsRow.RequestsHeader -> {}
            is XmbFriendsRow.Friend -> {
                val f = row.friend
                if (openKey != row.key) {
                    openKey = row.key
                    sub = 0
                    confirmRemove = false
                } else {
                    val actions = xmbFriendActions(f)
                    val action = actions[button.coerceIn(0, actions.lastIndex)]
                    // Pressing anything but Remove gives up a Remove that was waiting for its second press.
                    if (action != XmbFriendAction.REMOVE) confirmRemove = false
                    when (action) {
                        XmbFriendAction.MESSAGE -> { openKey = null; chatFriend = f }
                        XmbFriendAction.GAME -> openSteamGameDetail(ctx, f.gameAppId)
                        XmbFriendAction.PROFILE -> profileFriend = f
                        XmbFriendAction.REMOVE -> if (!confirmRemove) {
                            confirmRemove = true
                        } else {
                            openKey = null
                            confirmRemove = false
                            SteamFriendsStore.removeFriend(f.steamId)
                        }
                    }
                }
            }
        }
    }

    val pad = focus.contentPadding
    // The width the page is left decides the columns (the keys need them, so they are registered inside).
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = minOf(maxWidth - pad.calculateStartPadding(dir) - pad.calculateEndPadding(dir), XMB_TWO_COL_MAX_WIDTH_DP.dp)
        val columns = xmbTwoColColumns(avail.value)
        val lines = remember(rows, columns) { xmbFriendsLines(rows, columns) }

        // A tap puts the cursor on the row, and its column is the one a move up or down keeps.
        fun pick(index: Int) {
            select(index)
            wantCol = xmbTwoColCol(lines, index).coerceAtLeast(0)
        }

        XmbTabsPageKeys(focus) { e ->
            val first = e.nativeKeyEvent.repeatCount == 0
            when (e.key) {
                // Along the lines (the cards two to a line, a heading across both), on the column the cursor last chose.
                Key.DirectionUp, Key.DirectionDown -> {
                    val to = xmbFriendsVertical(rows, lines, selIdx, down = e.key == Key.DirectionDown, wantCol = wantCol)
                    if (to == XMB_TWO_COL_TO_BAR) focus.toBar() else if (to != selIdx) select(to)
                    true
                }
                // Between the columns; on the toolbar, and on a card whose buttons A has stepped into, between the buttons instead.
                Key.DirectionLeft, Key.DirectionRight -> {
                    val d = if (e.key == Key.DirectionRight) 1 else -1
                    val open = openKey == selRow.key
                    if (xmbFriendsSideIsButtons(selRow, open)) {
                        val next = (sub + d).coerceIn(0, xmbFriendsButtonCount(selRow, open) - 1)
                        if (next != sub) {
                            sub = next
                            confirmRemove = false
                        }
                    } else {
                        val to = xmbTwoColSide(lines, selIdx, d)
                        if (to != selIdx) pick(to)
                    }
                    true
                }
                Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    if (first) act(selRow, sub)
                    true
                }
                // B steps back out of a card's buttons; with none held it is the host's (it closes the page).
                Key.ButtonB, Key.Escape -> {
                    if (openKey != null) {
                        openKey = null
                        confirmRemove = false
                        sub = 0
                        true
                    } else {
                        false
                    }
                }
                Key.ButtonX -> {
                    if (first) showAdd = true
                    true
                }
                else -> false
            }
        }

        // The line under the cursor is kept in view: on a move, when its buttons open (the card grows) and when the cursor comes into the list.
        val selLine = xmbTwoColLineOf(lines, selIdx)
        LaunchedEffect(selLine, openKey, focus.contentFocused) {
            if (focus.contentFocused && selLine >= 0) listState.bringRowIntoView(selLine)
        }

        val selectedKey = if (focus.contentFocused) selRow.key else null
        // One row of the roster, full width or as a card (the card fills the cell it is given).
        val rowContent: @Composable (Int) -> Unit = { index ->
            val row = rows[index]
            val on = row.key == selectedKey
            when (row) {
                is XmbFriendsRow.Toolbar -> XmbFriendsToolbar(
                    summary = ctx.resources.getQuantityString(R.plurals.xmb_fr_summary, friends.size, online, friends.size),
                    sub = if (on) sub else -1,
                    ss = ss,
                    onAdd = { pick(index); act(row, 0) },
                    onSettings = { pick(index); act(row, 1) },
                )
                is XmbFriendsRow.RequestsHeader -> XmbFriendsHeading(
                    stringResource(R.string.xmb_fr_section_requests).uppercase() + " · " + row.count, null, false, false, ss, {},
                )
                is XmbFriendsRow.Header -> XmbFriendsHeading(
                    stringResource(sectionLabel(row.presence)).uppercase() + " · " + row.count,
                    dotColor(row.presence), row.collapsed, on, ss,
                ) { pick(index); act(row, 0) }
                is XmbFriendsRow.Incoming -> {
                    val open = openKey == row.key
                    XmbFriendsRequestCard(
                        friend = row.friend,
                        status = stringResource(R.string.xmb_fr_wants_to_add),
                        buttons = listOf(stringResource(R.string.xmb_fr_accept), stringResource(R.string.xmb_fr_decline)),
                        selected = on,
                        open = open,
                        sub = if (on && open) sub else -1,
                        ss = ss,
                        // A tap on a button answers the request at once, as A does from inside.
                    ) { button -> pick(index); answerRequest(row.friend, button) }
                }
                is XmbFriendsRow.Outgoing -> XmbFriendsRequestCard(
                    friend = row.friend,
                    status = stringResource(R.string.xmb_fr_request_sent),
                    buttons = listOf(stringResource(R.string.xmb_fr_cancel)),
                    selected = on,
                    open = false,
                    sub = if (on) 0 else -1,
                    ss = ss,
                ) { button -> pick(index); act(row, button) }
                is XmbFriendsRow.Friend -> {
                    val f = row.friend
                    val open = openKey == row.key
                    XmbFriendCard(
                        friend = f,
                        unread = unread[f.steamId] ?: 0,
                        selected = on,
                        open = open,
                        sub = sub,
                        confirmRemove = confirmRemove,
                        ss = ss,
                        onTap = {
                            // A tap opens or closes the actions, as A does on a closed card.
                            pick(index)
                            openKey = if (openKey == row.key) null else row.key
                            sub = 0
                            confirmRemove = false
                        },
                        onAction = { button -> pick(index); sub = button; act(row, button) },
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = pad,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ss.dp(XMB_TWO_COL_GAP_DP)),
        ) {
            items(lines, key = { line -> rows[line.first()].key }) { line ->
                if (!xmbFriendsIsCard(rows[line.first()])) {
                    rowContent(line.first())
                } else {
                    // The cards of a line are equally wide; a short last line keeps the width of a card.
                    Row(Modifier.xmbFriendsRow(), horizontalArrangement = Arrangement.spacedBy(ss.dp(XMB_TWO_COL_GAP_DP))) {
                        line.forEach { index -> Box(Modifier.weight(1f)) { rowContent(index) } }
                        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (xmbFriendsEmpty(rows)) {
                item(key = "empty") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.xmbFriendsRow().padding(vertical = ss.dp(36f)),
                    ) {
                        val cs = MaterialTheme.colorScheme
                        if (!rosterWaited) {
                            // Steam has not had the time to send the list yet: not "no friends" until it has.
                            Text(
                                stringResource(R.string.xmb_fr_loading), color = cs.onSurfaceVariant, fontSize = ss.sp(13f),
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Text(
                                stringResource(R.string.xmb_fr_empty_title), color = cs.onSurface, fontSize = ss.sp(15f), fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.xmb_fr_empty_sub), color = cs.onSurfaceVariant, fontSize = ss.sp(13f),
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = ss.dp(4f)),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) XmbAddFriendDialog(onDismiss = { showAdd = false })
    if (showSettings) SteamSocialSettingsSheet(onDismiss = { showSettings = false })
    chatFriend?.let { f ->
        XmbFriendChatDialog(friends.firstOrNull { it.steamId == f.steamId } ?: f, onDismiss = { chatFriend = null })
    }
    profileFriend?.let { f ->
        XmbFriendProfileDialog(
            friend = friends.firstOrNull { it.steamId == f.steamId } ?: f,
            onMessage = { chatFriend = f; profileFriend = null },
            onViewOnSteam = { openSteamProfileInBrowser(ctx, f.steamId) },
            onDismiss = { profileFriend = null },
        )
    }
}

private fun sectionLabel(presence: SteamFriendsStore.Presence): Int = when (presence) {
    SteamFriendsStore.Presence.IN_GAME -> R.string.xmb_fr_section_in_game
    SteamFriendsStore.Presence.ONLINE -> R.string.xmb_fr_section_online
    SteamFriendsStore.Presence.AWAY -> R.string.xmb_fr_section_away
    SteamFriendsStore.Presence.OFFLINE -> R.string.xmb_fr_section_offline
    SteamFriendsStore.Presence.UNKNOWN -> R.string.xmb_fr_section_unknown
}

/** Scrolls the list the least that brings item [index] (a line of the roster) fully into the area the page's padding leaves free (a row taller than it: to its top). */
private suspend fun LazyListState.bringRowIntoView(index: Int) {
    // After the layout that follows the change (an opened row is taller).
    withFrameNanos { }
    var tries = 0
    while (tries++ < 3) {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            animateScrollToItem(index)
            continue
        }
        val top = info.viewportStartOffset + info.beforeContentPadding
        val bottom = info.viewportEndOffset - info.afterContentPadding
        val delta = when {
            item.size >= bottom - top || item.offset < top -> item.offset - top
            item.offset + item.size > bottom -> item.offset + item.size - bottom
            else -> 0
        }
        if (delta != 0) animateScrollBy(delta.toFloat())
        return
    }
}

private fun Modifier.xmbFriendsRow(): Modifier = this.widthIn(max = XMB_FR_MAX_WIDTH).fillMaxWidth()

// ---------------------------------------------------------------------------------------------------------------------------------
// The rows
// ---------------------------------------------------------------------------------------------------------------------------------

/** The thin outline of the card or button under the cursor. */
@Composable
private fun xmbFriendsOutline(): androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)

/**
 * A pill button: the one under the cursor is filled with the accent and ringed. [showX] adds the X glyph (the controller's shortcut for it).
 * At least 48 dp tall.
 */
@Composable
private fun XmbFriendsButton(
    label: String,
    focused: Boolean,
    ss: ScreenScale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    showX: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val src = remember { MutableInteractionSource() }
    val ink = if (focused) cs.onPrimary else cs.onSurface
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (focused) cs.primary else cs.onSurface.copy(alpha = 0.12f))
            .then(if (focused) Modifier.border(1.5.dp, xmbFriendsOutline(), shape) else Modifier)
            .xmbClick(src, onClick)
            .padding(horizontal = ss.dp(16f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ss.dp(6f), Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(ss.dp(18f)))
        Text(label, color = ink, fontSize = ss.sp(12.5f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showX) DeckGlyph("X", GlyphKind.X)
    }
}

/** The top row: how many friends are online of how many, and the Add friend / Settings buttons. [sub] is the button the cursor is on, -1 when it is not on this row. */
@Composable
private fun XmbFriendsToolbar(summary: String, sub: Int, ss: ScreenScale, onAdd: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.xmbFriendsRow().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = ss.sp(12.5f), fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        XmbFriendsButton(stringResource(R.string.xmb_fr_add), sub == 0, ss, onAdd, icon = Icons.Filled.Add, showX = true)
        Spacer(Modifier.width(ss.dp(8f)))
        XmbFriendsButton(stringResource(R.string.xmb_fr_settings), sub == 1, ss, onSettings, icon = Icons.Filled.Settings)
    }
}

/**
 * A heading: its text in small capitals and, for a section, the colour of its presence dot and a chevron that shows whether it is folded
 * (a tap or A folds it). The heading of the friend requests is not selectable: it only labels them.
 */
@Composable
private fun XmbFriendsHeading(
    text: String,
    dot: androidx.compose.ui.graphics.Color?,
    collapsed: Boolean,
    selected: Boolean,
    ss: ScreenScale,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val src = remember { MutableInteractionSource() }
    val tappable = dot != null
    Row(
        Modifier
            .xmbFriendsRow()
            .defaultMinSize(minHeight = if (tappable) 48.dp else 32.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(1.5.dp, xmbFriendsOutline(), shape) else Modifier)
            .then(if (tappable) Modifier.xmbClick(src, onClick) else Modifier)
            .padding(horizontal = ss.dp(10f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(ss.dp(9f)).clip(CircleShape).background(dot))
            Spacer(Modifier.width(ss.dp(8f)))
        }
        Text(
            text, color = cs.onSurfaceVariant, fontSize = ss.sp(11.5f), letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (tappable) {
            Icon(
                Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = cs.onSurfaceVariant,
                modifier = Modifier.size(ss.dp(20f)).rotate(if (collapsed) -90f else 0f),
            )
        }
    }
}

/** A friend: the card (avatar, name, status, the game they are playing as small art, the unread count) and, while [open], their actions under it. */
@Composable
private fun XmbFriendCard(
    friend: SteamFriendsStore.SteamFriend,
    unread: Int,
    selected: Boolean,
    open: Boolean,
    sub: Int,
    confirmRemove: Boolean,
    ss: ScreenScale,
    onTap: () -> Unit,
    onAction: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val src = remember { MutableInteractionSource() }
    val inGame = friend.presence == SteamFriendsStore.Presence.IN_GAME
    // With the actions open the buttons carry the cursor and the card keeps a faint ring.
    val ring = when {
        selected && !open -> Modifier.border(1.5.dp, xmbFriendsOutline(), shape)
        open -> Modifier.border(1.dp, cs.onSurface.copy(alpha = 0.35f), shape)
        else -> Modifier.border(1.dp, deckLine(), shape)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ss.dp(6f))) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clip(shape)
                .background(deckCardFill())
                .then(ring)
                .xmbClick(src, onTap)
                .padding(horizontal = ss.dp(12f), vertical = ss.dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FriendAvatar(friend = friend, size = ss.dp(44f))
            Spacer(Modifier.width(ss.dp(12f)))
            Column(Modifier.weight(1f)) {
                Text(
                    friend.displayName, color = cs.onSurface, fontSize = ss.sp(15f),
                    fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    friend.statusText,
                    color = if (inGame) dotColor(friend.presence) else cs.onSurfaceVariant,
                    fontSize = ss.sp(12.5f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (inGame && friend.gameAppId != 0) {
                Spacer(Modifier.width(ss.dp(10f)))
                AsyncImage(
                    model = SteamStoreSearch.headerUrl(friend.gameAppId),
                    contentDescription = friend.gameName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = ss.dp(76f), height = ss.dp(36f)).clip(RoundedCornerShape(4.dp)).background(cs.surfaceVariant),
                )
            }
            if (unread > 0) {
                Spacer(Modifier.width(ss.dp(10f)))
                UnreadBadge(unread)
            }
        }
        if (open) {
            val labels = xmbFriendActions(friend).map { action ->
                when (action) {
                    XmbFriendAction.MESSAGE -> stringResource(R.string.xmb_fr_message)
                    XmbFriendAction.GAME -> stringResource(R.string.xmb_fr_game_page)
                    XmbFriendAction.PROFILE -> stringResource(R.string.xmb_fr_profile)
                    XmbFriendAction.REMOVE ->
                        if (confirmRemove) stringResource(R.string.xmb_fr_remove_confirm) else stringResource(R.string.xmb_fr_remove)
                }
            }
            // Two to a line, equally wide: four buttons side by side would not fit half the page. Left / Right still step through them in order.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ss.dp(6f))) {
                labels.chunked(2).forEachIndexed { r, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ss.dp(8f))) {
                        pair.forEachIndexed { c, label ->
                            val i = r * 2 + c
                            XmbFriendsButton(label, selected && sub == i, ss, { onAction(i) }, modifier = Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * A friend request, in or out: the card (avatar, name, what it is) with its [buttons] (Accept / Decline, or Cancel) on a line of their own
 * under it, equally wide. [selected]: the cursor is on the card; [open]: A has stepped into its buttons (the card keeps a faint ring then);
 * [sub] is the button holding the cursor, -1 when none does.
 */
@Composable
private fun XmbFriendsRequestCard(
    friend: SteamFriendsStore.SteamFriend,
    status: String,
    buttons: List<String>,
    selected: Boolean,
    open: Boolean,
    sub: Int,
    ss: ScreenScale,
    onButton: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val ring = when {
        selected && !open -> Modifier.border(1.5.dp, xmbFriendsOutline(), shape)
        open -> Modifier.border(1.dp, cs.onSurface.copy(alpha = 0.35f), shape)
        else -> Modifier.border(1.dp, deckLine(), shape)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(shape)
            .background(deckCardFill())
            .then(ring)
            .padding(horizontal = ss.dp(12f), vertical = ss.dp(8f)),
        verticalArrangement = Arrangement.spacedBy(ss.dp(6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FriendAvatar(friend = friend, size = ss.dp(44f))
            Spacer(Modifier.width(ss.dp(12f)))
            Column(Modifier.weight(1f)) {
                Text(
                    friend.displayName, color = cs.onSurface, fontSize = ss.sp(15f), fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(status, color = cs.onSurfaceVariant, fontSize = ss.sp(12.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ss.dp(8f))) {
            buttons.forEachIndexed { i, label ->
                XmbFriendsButton(label, sub == i, ss, { onButton(i) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
