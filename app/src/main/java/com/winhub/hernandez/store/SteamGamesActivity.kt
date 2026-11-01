package com.winhub.hernandez.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.ui.theme.WinlatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Standalone Steam Library screen.
 *
 * The grid, the detailed list, the launch / uninstall / add-to-shortcuts flows and the library
 * state machine all moved to [SteamLibraryTab] / [rememberSteamLibrary] when the Steam section went
 * store-first, so this Activity is now a thin host: back arrow, title, the view toggle and refresh,
 * over the same composable the storefront's Library tab renders. Kept as its own Activity because
 * [QrLoginActivity], [SteamLoginActivity] and existing intents still target it directly.
 */
class SteamGamesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Honour the user's App-orientation preference (Appearance -> AUTO / PORTRAIT / LANDSCAPE).
        // The whole Steam section previously ignored it: these activities pinned themselves in the
        // manifest and never asked. Applied before any content so the first frame is already in the
        // requested orientation. The game's XServerDisplayActivity is deliberately NOT touched.
        com.winhub.hernandez.core.AppOrientation.apply(this)
        SteamPrefs.init(this)
        SteamRepository.getInstance().initialize(this)
        // Load the persisted social opt-in so the friends action reflects it.
        SteamFriendsStore.init(this)

        setContent {
            WinlatorTheme {
                DeckStoreTheme { SteamLibraryScreen(onBack = { finish() }) 
                }}
        }
    }
}

@Composable
private fun SteamLibraryScreen(onBack: () -> Unit) {
    val library = rememberSteamLibrary()
    val layout = rememberStorefrontLayout()
    val ctx = LocalContext.current
    var viewMode by remember { mutableStateOf("grid") }
    // Shares the persisted preference with the storefront's Library tab, so the chip is the same
    // wherever the library is opened from.
    var typeFilter by remember {
        mutableStateOf(
            runCatching { LibraryTypeFilter.fromName(SteamPrefs.getLibraryTypeFilter(ctx)) }
                .getOrDefault(LibraryTypeFilter.GAMES),
        )
    }
    var message by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.steamFocusRing()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Steam Library",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                SteamStatusPill(status = library.steamStatus, onReconnect = library.reconnect)
                SteamFriendsAction(tint = MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewMode = if (viewMode == "grid") "list" else "grid" },
                    modifier = Modifier.steamFocusRing(),
                ) {
                    Icon(
                        imageVector = if (viewMode == "grid") Icons.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = if (viewMode == "grid") "List view" else "Grid view",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = library.refresh, modifier = Modifier.steamFocusRing()) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                DownloadsButton()
            }

            Text(
                text = library.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SteamLibraryTab(
                state = library,
                wide = layout.wide,
                viewMode = viewMode,
                typeFilter = typeFilter,
                onTypeFilter = { f ->
                    typeFilter = f
                    runCatching { SteamPrefs.setLibraryTypeFilter(ctx, f.name) }
                },
                onOpenApp = { openSteamGameDetail(ctx, it) },
                onMessage = { message = it },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        message?.let { StoreMessageBar(it) { message = null } }
    }
}

// ── Shared Steam art loader ───────────────────────────────────────────────────────────────────

/**
 * Portrait library cover for [appId], falling back to the landscape header capsule.
 *
 * `internal` (not private): the cross-store Download Manager (store.download package) reuses this
 * exact Steam poster loader for its list cards, so a downloading/installed row looks identical to a
 * Library row, and [SteamLibraryTab]'s detailed list mode draws it too.
 */
@Composable
internal fun GameCoverArt(appId: Int, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(appId) {
        loaded = false
        bitmap = withContext(Dispatchers.IO) {
            tryBitmap("https://shared.steamstatic.com/store_item_assets/steam/apps/$appId/library_600x900.jpg")
                ?: tryBitmap("https://shared.steamstatic.com/store_item_assets/steam/apps/$appId/header.jpg")
        }
        loaded = true
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else if (loaded) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
    }
}

private suspend fun tryBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 10_000
        conn.connect()
        if (conn.responseCode == 200) BitmapFactory.decodeStream(conn.inputStream) else null
    } catch (_: Exception) { null }
}
