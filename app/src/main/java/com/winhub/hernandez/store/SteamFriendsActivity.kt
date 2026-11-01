package com.winhub.hernandez.store

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.ui.theme.WinlatorTheme

/**
 * Standalone host for the Steam friends list + 1:1 text chat. A single, self-contained screen (its
 * own back-stack of "list" ↔ "chat") reachable from three different app surfaces, so it lives in its
 * own Activity rather than being wired into two separate Compose nav graphs. Reads/sends everything
 * through [SteamFriendsStore], which rides the live [SteamRepository] CM session.
 */
class SteamFriendsActivity : ComponentActivity() {

    companion object {
        /** Intent extra (SteamID64 Long): open straight into this friend's chat. 0 / absent = list. */
        const val EXTRA_OPEN_FRIEND = "open_friend_steam_id"
    }

    // The friend to deep-link into, as Compose state so a chat-notification tap that re-delivers via
    // onNewIntent (this Activity is reused, not recreated) re-navigates the running composition.
    private val openFriendId = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour the user's App-orientation preference (Appearance -> AUTO / PORTRAIT / LANDSCAPE).
        // The whole Steam section previously ignored it: these activities pinned themselves in the
        // manifest and never asked. Applied before any content so the first frame is already in the
        // requested orientation. The game's XServerDisplayActivity is deliberately NOT touched.
        com.winhub.hernandez.core.AppOrientation.apply(this)
        SteamPrefs.init(this)
        SteamRepository.getInstance().initialize(this)
        SteamFriendsStore.init(this)
        openFriendId.value = intent?.getLongExtra(EXTRA_OPEN_FRIEND, 0L) ?: 0L
        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                SteamFriendsRoot(
                    onClose = { finish() },
                    openFriendId = openFriendId.value,
                    onFriendOpened = { openFriendId.value = 0L },
                )
            
                }}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getLongExtra(EXTRA_OPEN_FRIEND, 0L)
        if (id != 0L) openFriendId.value = id
        SteamFriendsStore.refresh()
    }

    override fun onStart() {
        super.onStart()
        // Seed the list + go online + request fresh presence whenever the screen is shown. No-op when
        // the session isn't live (e.g. a SteamLite game is holding it) — the UI then shows the
        // connect state.
        SteamFriendsStore.refresh()
    }
}

/**
 * Responsive root for the friends UI.
 *
 * - PORTRAIT (phone default): a single pane that shows the friends list, or — when a friend is tapped
 *   — that friend's full-screen chat, with back stepping list ↔ chat. Unchanged from before.
 * - LANDSCAPE (wide screens, ≥600dp): a 26/75 master-detail — the list always on the left, the
 *   selected friend's chat on the right ([FriendsTwoPane]). Back exits the screen (no close-chat step).
 *
 * The selected-friend state ([openChat]) is shared across both, so rotating keeps the open chat (the
 * Activity handles orientation via configChanges, so this just recomposes with the new configuration).
 */
@Composable
fun SteamFriendsRoot(
    onClose: () -> Unit,
    openFriendId: Long = 0L,
    onFriendOpened: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val socialEnabled by SteamFriendsStore.socialEnabled.collectAsState()
    var openChat by remember { mutableStateOf<SteamFriendsStore.SteamFriend?>(null) }
    // The friend whose in-app profile is open (null = none). A full route over BOTH orientations; back
    // closes it, returning to the list / two-pane. Kept separate from [openChat] so opening a profile
    // never disturbs the active chat / two-pane selection underneath it.
    var profileFriend by remember { mutableStateOf<SteamFriendsStore.SteamFriend?>(null) }
    val available = rememberFriendsAvailable()

    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        configuration.screenWidthDp >= 600

    // When the session becomes live (e.g. the user tapped reconnect) AND the feature is on, pull a
    // fresh roster. refresh() is a no-op while social is off, but keying on socialEnabled means a fresh
    // roster loads the instant the user opts in.
    LaunchedEffect(available, socialEnabled) { if (available && socialEnabled) SteamFriendsStore.refresh() }

    // Deep-link: a chat-notification tap arrives as a SteamID; open that friend's chat directly. This
    // runs the existing openChat → clearUnread → SteamChatNotifier.cancel path, so the tapped
    // notification auto-clears. Resolve once per id (friendById falls back to a placeholder if the
    // roster hasn't published yet); onFriendOpened resets the id so back-navigation isn't hijacked. In
    // landscape this simply selects the friend into the right pane. Ignored while social is off (no
    // notification can have been posted anyway).
    LaunchedEffect(openFriendId, socialEnabled) {
        if (openFriendId != 0L && socialEnabled) {
            openChat = SteamFriendsStore.friendById(openFriendId)
            onFriendOpened()
        }
    }

    // Feature is opt-in and currently OFF: show the clean off-state (message + Enable) with the top bar
    // + cog still shown, in BOTH portrait and landscape, instead of the roster / two-pane. Back leaves.
    if (!socialEnabled) {
        BackHandler(enabled = true) { onClose() }
        FriendsOffState(onBack = onClose)
        return
    }

    // Profile route: takes over the whole screen (portrait AND landscape). Back closes it, dropping
    // straight back to the list / two-pane exactly as it was (the underlying openChat state is
    // untouched). Only this BackHandler is composed while it's open, so it can't clash with the others.
    val pf = profileFriend
    if (pf != null) {
        BackHandler(enabled = true) { profileFriend = null }
        FriendProfileScreen(
            friend = pf,
            onBack = { profileFriend = null },
            // Message opens that friend's chat — portrait shows the full chat, landscape selects it
            // into the right pane — by leaving the profile route with openChat set.
            onMessage = { openChat = pf; profileFriend = null },
            // Invite is a no-op stub for now (game/lobby invites aren't wired to the CM yet).
            onInvite = { Toast.makeText(ctx, "Game invites are coming soon", Toast.LENGTH_SHORT).show() },
            onRemove = { SteamFriendsStore.removeFriend(pf.steamId); profileFriend = null },
            onViewOnSteam = {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/profiles/${pf.steamId}")),
                    )
                }
            },
        )
        return
    }

    if (landscape) {
        // Two-pane: the list is always on-screen, so back leaves the whole screen. Clear the active
        // chat on the way out (as the single-pane chat-exit does) so unread/notify stay live afterwards.
        val exit = { SteamFriendsStore.closeChat(); onClose() }
        BackHandler(enabled = true) { exit() }
        FriendsTwoPane(
            available = available,
            selectedFriend = openChat,
            onSelectFriend = { openChat = it },
            onOpenProfile = { profileFriend = it },
            onBack = exit,
        )
    } else {
        val friend = openChat
        if (friend == null) {
            BackHandler(enabled = true) { onClose() }
            FriendsListScreen(
                available = available,
                onBack = onClose,
                onOpenChat = { openChat = it },
                onOpenProfile = { profileFriend = it },
            )
        } else {
            BackHandler(enabled = true) {
                SteamFriendsStore.closeChat()
                openChat = null
            }
            ChatScreen(
                friend = friend,
                onBack = {
                    SteamFriendsStore.closeChat()
                    openChat = null
                },
            )
        }
    }
}

/**
 * Live "friends available" flag, re-evaluated on every repository session event so the connect/empty
 * state and the roster appear/disappear without a screen restart.
 */
@Composable
private fun rememberFriendsAvailable(): Boolean {
    var available by remember { mutableStateOf(runCatching { SteamFriendsStore.isAvailable() }.getOrDefault(false)) }
    DisposableEffect(Unit) {
        val repo = SteamRepository.getInstance()
        val l = SteamRepository.SteamEventListener {
            available = runCatching { SteamFriendsStore.isAvailable() }.getOrDefault(available)
        }
        repo.addListener(l)
        available = runCatching { SteamFriendsStore.isAvailable() }.getOrDefault(available)
        onDispose { repo.removeListener(l) }
    }
    return available
}

/**
 * Login-gated entry-point icon for the Steam friends screen. Shown ONLY when the user has a Steam
 * session ([SteamPrefs.isLoggedIn]) — the same gate the connection pill uses — and re-evaluates on
 * the repository's event bus so it appears/disappears without an app restart. Dropped into three
 * headers (Steam Library, Games, Containers); [tint] lets each header match its own icon colour.
 */
@Composable
fun SteamFriendsAction(tint: Color = Color.White) {
    val ctx = LocalContext.current
    var loggedIn by remember { mutableStateOf(runCatching { SteamPrefs.isLoggedIn }.getOrDefault(false)) }
    DisposableEffect(Unit) {
        val repo = SteamRepository.getInstance()
        val l = SteamRepository.SteamEventListener { ev ->
            if (ev.startsWith("SteamStatus:") || ev.startsWith("LoggedIn") ||
                ev == "LoggedOut" || ev == "SessionExpired"
            ) {
                loggedIn = runCatching { SteamPrefs.isLoggedIn }.getOrDefault(loggedIn)
            }
        }
        repo.addListener(l)
        loggedIn = runCatching { SteamPrefs.isLoggedIn }.getOrDefault(loggedIn)
        onDispose { repo.removeListener(l) }
    }
    if (!loggedIn) return
    val unread by SteamFriendsStore.unread.collectAsState()
    val incoming by SteamFriendsStore.incomingRequests.collectAsState()
    val socialEnabled by SteamFriendsStore.socialEnabled.collectAsState()
    // The badge covers both unread chats and pending incoming friend requests — but only counts while
    // the feature is enabled (a dormant feature shows no unread/request footprint).
    val total = if (socialEnabled) unread.values.sum() + incoming.size else 0
    Box {
        IconButton(onClick = {
            runCatching { ctx.startActivity(Intent(ctx, SteamFriendsActivity::class.java)) }
        }) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = "Steam friends",
                tint = tint,
            )
        }
        if (total > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
                    .widthIn(min = 16.dp)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (total > 9) "9+" else total.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * The ONE reusable "Steam friends & chat" settings sheet, opened by BOTH cogs — the Steam store header
 * and the friends-screen top bar. Because both surfaces open this same sheet, and it binds to the
 * shared [SteamFriendsStore.socialEnabled] flow + [SteamPrefs] gates, the two are inherently mirrored:
 * a change made in one is live in the other (and in the friends screen's off-state) with no extra
 * plumbing.
 *
 * - "Enable Steam friends & chat" → the master opt-in (default OFF), via [SteamFriendsStore.setSocialEnabled].
 * - "Chat notifications" → shade notifications, greyed while the master is off.
 * - "Notification sound" → sound / heads-up on those notifications, greyed while notifications are off.
 */
@Composable
fun SteamSocialSettingsSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val socialEnabled by SteamFriendsStore.socialEnabled.collectAsState()
    // The notification gates are plain SharedPreferences (not flows), so hold them as local state seeded
    // from prefs and written straight through on toggle — this sheet is their only in-app editor.
    var chatNotifs by remember {
        mutableStateOf(runCatching { SteamPrefs.isChatNotificationsEnabled(ctx) }.getOrDefault(true))
    }
    var chatSound by remember {
        mutableStateOf(runCatching { SteamPrefs.isChatSoundEnabled(ctx) }.getOrDefault(true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Steam friends & chat")
            }
        },
        text = {
            Column {
                SocialSettingRow(
                    title = "Enable Steam friends & chat",
                    subtitle = "See your friends list, presence and chat. Off by default — no online status " +
                        "is shared until you turn this on.",
                    checked = socialEnabled,
                    enabled = true,
                    onCheckedChange = { SteamFriendsStore.setSocialEnabled(ctx, it) },
                )
                Spacer(Modifier.height(4.dp))
                SocialSettingRow(
                    title = "Chat notifications",
                    subtitle = "Show a notification when a friend messages you.",
                    checked = chatNotifs,
                    enabled = socialEnabled,
                    onCheckedChange = { chatNotifs = it; SteamPrefs.setChatNotificationsEnabled(ctx, it) },
                )
                Spacer(Modifier.height(4.dp))
                SocialSettingRow(
                    title = "Notification sound",
                    subtitle = "Play a sound and pop a heads-up for new messages.",
                    checked = chatSound,
                    enabled = socialEnabled && chatNotifs,
                    onCheckedChange = { chatSound = it; SteamPrefs.setChatSoundEnabled(ctx, it) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** One title + subtitle + trailing [Switch] row for [SteamSocialSettingsSheet]; dims when [enabled] is false. */
@Composable
private fun SocialSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val subColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = subColor)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
