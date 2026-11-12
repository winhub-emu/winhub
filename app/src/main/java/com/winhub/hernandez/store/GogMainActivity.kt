package com.winhub.hernandez.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.ui.theme.WinlatorTheme

/**
 * The GOG section's host — flipped from a login card + "View Game Library" button to a
 * **store-first** three-tab shell: **Store / Library / Profile** (friend count lives on Profile — GOG friends are a Galaxy-only roster nobody uses, so no tab), the same shape as the
 * Steam section ([SteamMainActivity]) over the shared [StoreSectionHost].
 *
 * The login gate is unchanged (`bh_gog_prefs.access_token`), as is the pending-exe bounce the
 * launcher relies on. The full games screen ([GogGamesActivity]) still exists one tap away for
 * its list view, in-list installs and DLC sync; owned titles open [GogGameDetailActivity], catalog
 * titles open [StoreCatalogDetailActivity], and claims go through [StoreWebActivity].
 */
class GogMainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "gog_tab"
        private const val REQ_WEB = 4101
    }

    private var isLoggedIn by mutableStateOf(false)
    private var username by mutableStateOf("")
    private var startTab by mutableStateOf(0)
    private var launchedLogin = false
    /** Bumped when the WebView closes so the host re-syncs the library (a claim may have landed). */
    private var webReturnTick by mutableStateOf(0)

    // XMB home: L2 / R2 (buttons or analog triggers) step through the rail's tabs; see StoreXmbInput.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        StoreXmbInput.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
        StoreXmbInput.onMotion(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        DownloadRegistry.init(this)
        startTab = intent?.getIntExtra(EXTRA_TAB, 0) ?: 0
        refreshView()
        setContent {
            StoreXmbTheme {
                DeckStoreTheme {
                if (isLoggedIn) {
                    GogStorefrontHost(
                        initialTab = startTab,
                        username = username,
                        webReturnTick = webReturnTick,
                        onOpenWeb = { url, title -> startActivityForResult(StoreWebActivity.intent(this, url, title), REQ_WEB) },
                        onSignOut = { signOut() },
                        onClose = { finish() },
                    )
                } else {
                    GogLoginScreen(onLoginClick = {
                        launchedLogin = true
                        startActivity(Intent(this@GogMainActivity, GogLoginActivity::class.java))
                    })
                }
            
                }}
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WEB) webReturnTick++
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("bh_gog_prefs", 0)
        if (prefs.getString("pending_gog_exe", null) != null) {
            finish()
            return
        }
        refreshView()
    }

    private fun refreshView() {
        val prefs = getSharedPreferences("bh_gog_prefs", 0)
        val token = prefs.getString("access_token", null)
        isLoggedIn = token != null
        username = if (isLoggedIn) prefs.getString("username", "Unknown") ?: "Unknown" else ""
    }

    private fun signOut() {
        getSharedPreferences("bh_gog_prefs", 0).edit().clear().apply()
        refreshView()
    }
}

// ── Host ──────────────────────────────────────────────────────────────────────────────────────

private val GOG_TABS = listOf(
    StoreSectionTab("store", "Store", Icons.Filled.Storefront),
    StoreSectionTab("library", "Library", Icons.Filled.VideoLibrary),
    StoreSectionTab("profile", "Profile", Icons.Filled.AccountCircle),
)

@Composable
private fun GogStorefrontHost(
    initialTab: Int,
    username: String,
    webReturnTick: Int,
    onOpenWeb: (url: String, title: String) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, GOG_TABS.size - 1)) }
    var message by remember { mutableStateOf<String?>(null) }
    val library = rememberGogLibrary(onMessage = { message = it })

    var profile by remember { mutableStateOf(GogUserData.cached(ctx)) }
    var profileLoading by remember { mutableStateOf(false) }
    var profileTick by remember { mutableStateOf(0) }
    LaunchedEffect(profileTick) {
        profileLoading = true
        val fresh = runCatching { GogUserData.fetch(ctx) }.getOrNull()
        if (fresh != null) profile = fresh
        profileLoading = false
    }
    // Back from the store web page: a free claim may have landed — re-sync the owned list.
    LaunchedEffect(webReturnTick) { if (webReturnTick > 0) library.refresh(true) }

    fun openOwned(gameId: String) {
        val g = library.games.firstOrNull { it.gameId == gameId }
        val i = Intent(ctx, GogGameDetailActivity::class.java)
            .putExtra("game_id", gameId)
            .putExtra("title", g?.title ?: "")
            .putExtra("image_url", g?.imageUrl ?: "")
            .putExtra("description", g?.description ?: "")
            .putExtra("developer", g?.developer ?: "")
            .putExtra("category", g?.category ?: "")
            .putExtra("generation", g?.generation ?: 0)
        ctx.startActivity(i)
    }

    val counts = mapOf("library" to library.games.size)

    StoreSectionHost(
        storeName = "GOG",
        tabs = GOG_TABS,
        selected = tab,
        onSelect = { tab = it },
        onClose = onClose,
        counts = counts,
        statusSlot = {
            if (username.isNotBlank()) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        railStatusSlot = {
            StoreAvatar(GogUserData.avatarCandidates(profile?.avatar), username.ifBlank { "G" }, 36.dp)
        },
        actions = {
            IconButton(onClick = { library.refresh(true) }, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync library", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = { ctx.startActivity(Intent(ctx, GogGamesActivity::class.java)) },
                modifier = Modifier.steamFocusRing(),
            ) {
                Icon(Icons.Filled.ViewList, contentDescription = "Full library screen", tint = MaterialTheme.colorScheme.primary)
            }
            DownloadsButton()
            IconButton(onClick = onSignOut, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = MaterialTheme.colorScheme.primary)
            }
        },
        railActions = {
            IconButton(onClick = { library.refresh(true) }, modifier = Modifier.steamFocusRing().xmbRailStop()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync library", tint = MaterialTheme.colorScheme.primary)
            }
            DownloadsButton(Modifier.xmbRailStop(ring = true))
            StoreRailOverflow(
                items = listOf<Triple<String, ImageVector, () -> Unit>>(
                    Triple("Full library screen", Icons.Filled.ViewList, { ctx.startActivity(Intent(ctx, GogGamesActivity::class.java)) }),
                ),
                onSignOut = onSignOut,
            )
        },
        message = message,
        onMessageTimeout = { message = null },
    ) { wide, mod ->
        when (GOG_TABS[tab].key) {
            "store" -> GogStoreTab(
                wide = wide,
                ownedIds = library.ownedIds,
                installedIds = library.installedIds,
                onOpenOwned = ::openOwned,
                onOpenCatalog = { item -> ctx.startActivity(StoreCatalogDetailActivity.intent(ctx, item)) },
                onOpenWeb = onOpenWeb,
                onMessage = { message = it },
                modifier = mod,
            )
            "library" -> GogLibraryTab(
                state = library,
                wide = wide,
                onOpen = { openOwned(it.gameId) },
                onOpenFullLibrary = { ctx.startActivity(Intent(ctx, GogGamesActivity::class.java)) },
                modifier = mod,
            )
            else -> GogProfileTab(
                profile = profile,
                loading = profileLoading,
                wide = wide,
                username = username,
                libraryCount = library.games.size,
                installedCount = library.installedIds.size,
                onOpenWeb = onOpenWeb,
                onRefresh = { profileTick++ },
                modifier = mod,
            )
        }
    }
}

/**
 * The rail's overflow: extra actions plus Sign out, so a ~390dp-tall landscape rail is not eaten
 * by a stack of icons. Shared by the GOG and Epic hosts.
 */
@Composable
fun StoreRailOverflow(
    items: List<Triple<String, ImageVector, () -> Unit>>,
    onSignOut: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.steamFocusRing().xmbRailStop()) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions", tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { (label, icon, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { expanded = false; action() },
                )
            }
            if (items.isNotEmpty()) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            DropdownMenuItem(
                text = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { expanded = false; onSignOut() },
            )
        }
    }
}

// ── Login gate ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun GogLoginScreen(onLoginClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "GOG.com",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Sign in to browse the GOG store, claim free games and download your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLoginClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.steamFocusRing(RoundedCornerShape(8.dp)),
            ) { Text("Login with GOG") }
        }
    }
}
