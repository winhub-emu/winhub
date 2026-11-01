package com.winhub.hernandez.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Always-visible connection/login indicator that lives in the top header of every Steam screen.
 *
 * It is the honest replacement for the (cosmetic, never-updated) foreground-service notification:
 * while the app is foregrounded the Steam CM connection lives inside the SteamRepository singleton
 * in-process, so this pill reflects the real session state without depending on the notification.
 * Tapping it when not-online calls [SteamRepository.reconnectNow]. Every state change is logged to
 * the persistent steam_session.txt by the repository (see SteamRepository.setStatus).
 */
@Composable
fun SteamStatusPill(
    status: SteamRepository.SteamStatus,
    onReconnect: () -> Unit,
) {
    val (dot, label, tappable) = when (status) {
        SteamRepository.SteamStatus.ONLINE ->
            Triple(Color(0xFF3BA55D), "Online", false)
        SteamRepository.SteamStatus.CONNECTING ->
            Triple(Color(0xFFE0A82E), "Connecting…", false)
        SteamRepository.SteamStatus.SIGNED_IN_ELSEWHERE ->
            Triple(Color(0xFFE07B2E), "Signed in elsewhere", true)
        SteamRepository.SteamStatus.OFFLINE ->
            Triple(Color(0xFFCB4B4B), "Offline", true)
        SteamRepository.SteamStatus.SIGNED_OUT ->
            Triple(Color(0xFF9AA0A6), "Signed out", true)
        // A real-Steam (SteamLite) game holds the account in-container; the app's own session is
        // deliberately down and comes back on game exit. Not an error, not tappable (a tap would tug).
        SteamRepository.SteamStatus.PAUSED_FOR_GAME ->
            Triple(Color(0xFF5B8DEF), "Paused — game session active", false)
    }

    val base = Modifier
        .clip(RoundedCornerShape(50))
        .background(Color(0x22FFFFFF))
        .border(1.dp, dot.copy(alpha = 0.5f), RoundedCornerShape(50))
    val mod = if (tappable) base.clickable { onReconnect() } else base

    Row(
        modifier = mod.padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (tappable) {
            Spacer(Modifier.width(4.dp))
            Text(text = "↻", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * App-shell connection indicator: the same [SteamStatusPill], self-wired to the live
 * [SteamRepository] session state and shown ONLY for users who have signed in to Steam before.
 *
 * Drop it anywhere in the app UI (top bar, drawer header). It subscribes to the repository's
 * "SteamStatus:<NAME>" event bus for live updates, renders the current colour/label, and — when the
 * state is offline/signed-out — taps into [SteamRepository.reconnectNow]. Everything is wrapped in
 * runCatching so a not-yet-initialised prefs/repo (e.g. first frame before startup init lands) can
 * never crash the shell; it simply renders nothing until Steam is ready. Login state is re-read on
 * every status event, so signing in from the store makes the pill appear without an app restart.
 */
@Composable
fun SteamConnectionPill() {
    val repo = remember { SteamRepository.getInstance() }
    var status by remember { mutableStateOf(runCatching { repo.status }.getOrDefault(SteamRepository.SteamStatus.OFFLINE)) }
    var loggedIn by remember { mutableStateOf(runCatching { SteamPrefs.isLoggedIn }.getOrDefault(false)) }
    DisposableEffect(Unit) {
        val l = SteamRepository.SteamEventListener { ev ->
            if (ev.startsWith("SteamStatus:")) {
                runCatching { SteamRepository.SteamStatus.valueOf(ev.substringAfter("SteamStatus:")) }
                    .getOrNull()?.let { status = it }
                loggedIn = runCatching { SteamPrefs.isLoggedIn }.getOrDefault(loggedIn)
            }
        }
        repo.addListener(l)
        // Re-sync after registering: a transition between the initial snapshot and this point would
        // otherwise be missed until the next event.
        status = runCatching { repo.status }.getOrDefault(status)
        loggedIn = runCatching { SteamPrefs.isLoggedIn }.getOrDefault(loggedIn)
        onDispose { repo.removeListener(l) }
    }
    if (loggedIn) {
        SteamStatusPill(status = status, onReconnect = { runCatching { repo.reconnectNow() } })
    }
}
