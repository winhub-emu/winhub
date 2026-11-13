package com.winhub.hernandez.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

// Logic the friends screens share: SteamFriendsActivity (the standalone screen) and the XMB Home's Friends tab
// (ui/deck/XmbFriendsPage.kt). Nothing here draws anything.

/** Wires the Steam prefs and the friends store to the app context. Both are idempotent; call it before reading the store's flows. */
internal fun initSteamFriends(ctx: Context) {
    runCatching { SteamPrefs.init(ctx) }
    runCatching { SteamFriendsStore.init(ctx) }
}

/** A friend's community profile page. */
internal fun steamProfileUrl(steamId: Long): String = "https://steamcommunity.com/profiles/$steamId"

/** Opens [steamId]'s community profile in the browser. Never throws (no browser to open it). */
internal fun openSteamProfileInBrowser(ctx: Context, steamId: Long) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(steamProfileUrl(steamId)))) }
}

/** How many of [friends] are reachable: in a game, online or away (the same rule as the "Online" chip of the storefront's Friends tab). */
internal fun friendsOnlineCount(friends: List<SteamFriendsStore.SteamFriend>): Int =
    friends.count { FriendPresenceFilter.ONLINE.accepts(it.presence) }

/** The number of online friends, live: 0 while the friends feature is off. Wires the store first, so it can be collected from anywhere. */
internal fun steamOnlineFriendsCount(ctx: Context): Flow<Int> {
    initSteamFriends(ctx)
    return combine(SteamFriendsStore.friends, SteamFriendsStore.socialEnabled) { friends, enabled ->
        if (enabled) friendsOnlineCount(friends) else 0
    }.distinctUntilChanged()
}

/**
 * Live "signed in to Steam" flag ([SteamPrefs.isLoggedIn]): re-evaluated on the repository's event bus, so it flips
 * without an app restart when the user signs in or out.
 */
@Composable
internal fun rememberSteamLoggedIn(): Boolean {
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
    return loggedIn
}

/**
 * The online-friends count for a badge (the XMB Home's Friends tab): 0 while signed out or while the friends feature is off.
 * Compose-side wrapper of [steamOnlineFriendsCount].
 */
@Composable
internal fun rememberSteamOnlineFriends(): Int {
    val ctx = LocalContext.current
    val loggedIn = rememberSteamLoggedIn()
    val count by remember(ctx) { steamOnlineFriendsCount(ctx) }.collectAsState(initial = 0)
    return if (loggedIn) count else 0
}
