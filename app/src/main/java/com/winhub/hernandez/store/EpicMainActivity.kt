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
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import com.winhub.hernandez.ui.components.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadsButton
import com.winhub.hernandez.ui.theme.WinlatorTheme

/**
 * The Epic section's host — flipped from a login card + "View Game Library" button to a
 * **store-first** three-tab shell: **Store / Library / Profile** (friend count lives on Profile — no Friends tab, most Epic accounts have an empty roster), over the shared
 * [StoreSectionHost], mirroring the Steam and GOG sections.
 *
 * The login gate is unchanged ([EpicCredentialStore.isLoggedIn]). The full games screen
 * ([EpicGamesActivity]) stays one tap away; owned titles open [EpicGameDetailActivity], catalog
 * titles open [StoreCatalogDetailActivity], and claims go through [StoreWebActivity].
 */
class EpicMainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "epic_tab"
        private const val REQ_WEB = 4102
    }

    private var isLoggedIn by mutableStateOf(false)
    private var displayName by mutableStateOf("")
    private var tokenMinutesLeft by mutableStateOf(0L)
    private var startTab by mutableStateOf(0)
    private var webReturnTick by mutableStateOf(0)
    private var resultBarMsg by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        DownloadRegistry.init(this)
        startTab = intent?.getIntExtra(EXTRA_TAB, 0) ?: 0
        refreshView()
        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                if (isLoggedIn) {
                    EpicStorefrontHost(
                        initialTab = startTab,
                        displayName = displayName,
                        tokenMinutesLeft = tokenMinutesLeft,
                        webReturnTick = webReturnTick,
                        externalMessage = resultBarMsg,
                        onExternalMessageShown = { resultBarMsg = null },
                        onOpenWeb = { url, title -> startActivityForResult(StoreWebActivity.intent(this, url, title), REQ_WEB) },
                        onSignOut = { signOut() },
                        onClose = { finish() },
                    )
                } else {
                    EpicLoginScreen(onLoginClick = {
                        startActivity(Intent(this@EpicMainActivity, EpicLoginActivity::class.java))
                    })
                    resultBarMsg?.let { UninstallResultBar(it) { resultBarMsg = null } }
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
        refreshView()
    }

    private fun refreshView() {
        val loggedIn = EpicCredentialStore.isLoggedIn(this)
        isLoggedIn = loggedIn
        if (loggedIn) {
            val creds = EpicCredentialStore.load(this)
            if (creds != null) {
                displayName = if (!creds.displayName.isNullOrEmpty()) creds.displayName else "Epic Account"
                tokenMinutesLeft = (creds.expiresAt - System.currentTimeMillis()) / 60000L
            }
        }
    }

    private fun signOut() {
        EpicCredentialStore.clear(this)
        refreshView()
        resultBarMsg = "Signed out of Epic Games"
    }
}

// ── Host ──────────────────────────────────────────────────────────────────────────────────────

private val EPIC_TABS = listOf(
    StoreSectionTab("store", "Store", Icons.Filled.Storefront),
    StoreSectionTab("library", "Library", Icons.Filled.VideoLibrary),
    StoreSectionTab("profile", "Profile", Icons.Filled.AccountCircle),
)

@Composable
private fun EpicStorefrontHost(
    initialTab: Int,
    displayName: String,
    tokenMinutesLeft: Long,
    webReturnTick: Int,
    externalMessage: String?,
    onExternalMessageShown: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, EPIC_TABS.size - 1)) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(externalMessage) { if (externalMessage != null) { message = externalMessage; onExternalMessageShown() } }
    val library = rememberEpicLibrary(onMessage = { message = it })

    var profile by remember { mutableStateOf(EpicUserData.cached(ctx)) }
    var profileLoading by remember { mutableStateOf(false) }
    var profileTick by remember { mutableStateOf(0) }
    LaunchedEffect(profileTick) {
        profileLoading = true
        val fresh = runCatching { EpicUserData.fetch(ctx) }.getOrNull()
        if (fresh != null) profile = fresh
        profileLoading = false
    }
    LaunchedEffect(webReturnTick) { if (webReturnTick > 0) library.refresh(true) }

    fun openOwned(game: EpicGame) {
        ctx.startActivity(
            Intent(ctx, EpicGameDetailActivity::class.java)
                .putExtra("app_name", game.appName)
                .putExtra("title", game.title)
                .putExtra("description", game.description)
                .putExtra("developer", game.developer)
                .putExtra("art_cover", game.artCover)
                .putExtra("namespace", game.namespace)
                .putExtra("catalog_item_id", game.catalogItemId),
        )
    }

    fun openOwnedFromCatalog(item: CatalogItem) {
        val itemIds = item.extra["items"]?.split(',').orEmpty().toSet()
        val ns = item.extra["namespace"].orEmpty()
        val game = library.games.firstOrNull { it.catalogItemId in itemIds }
            ?: library.games.firstOrNull { ns.isNotBlank() && it.namespace == ns }
        if (game != null) openOwned(game) else ctx.startActivity(StoreCatalogDetailActivity.intent(ctx, item))
    }

    val counts = mapOf("library" to library.games.size)

    StoreSectionHost(
        storeName = "Epic Games",
        tabs = EPIC_TABS,
        selected = tab,
        onSelect = { tab = it },
        onClose = onClose,
        counts = counts,
        statusSlot = {
            if (displayName.isNotBlank()) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        railStatusSlot = { StoreAvatar(emptyList(), displayName.ifBlank { "E" }, 36.dp) },
        actions = {
            IconButton(onClick = { library.refresh(true) }, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync library", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = { ctx.startActivity(Intent(ctx, EpicGamesActivity::class.java)) },
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
            IconButton(onClick = { library.refresh(true) }, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync library", tint = MaterialTheme.colorScheme.primary)
            }
            DownloadsButton()
            StoreRailOverflow(
                items = listOf<Triple<String, ImageVector, () -> Unit>>(
                    Triple("Full library screen", Icons.Filled.ViewList, { ctx.startActivity(Intent(ctx, EpicGamesActivity::class.java)) }),
                    Triple("Free games page", Icons.Filled.Redeem, { ctx.startActivity(Intent(ctx, EpicFreeGamesActivity::class.java)) }),
                ),
                onSignOut = onSignOut,
            )
        },
        message = message,
        onMessageTimeout = { message = null },
    ) { wide, mod ->
        when (EPIC_TABS[tab].key) {
            "store" -> EpicStoreTab(
                wide = wide,
                ownedItemIds = library.ownedItemIds,
                ownedNamespaces = library.ownedNamespaces,
                installedNamespaces = library.installedNamespaces,
                onOpenOwned = ::openOwnedFromCatalog,
                onOpenCatalog = { item -> ctx.startActivity(StoreCatalogDetailActivity.intent(ctx, item)) },
                onOpenWeb = onOpenWeb,
                onMessage = { message = it },
                modifier = mod,
            )
            "library" -> EpicLibraryTab(
                state = library,
                wide = wide,
                onOpen = ::openOwned,
                onOpenFullLibrary = { ctx.startActivity(Intent(ctx, EpicGamesActivity::class.java)) },
                modifier = mod,
            )
            else -> EpicProfileTab(
                profile = profile,
                loading = profileLoading,
                wide = wide,
                displayName = displayName,
                tokenMinutesLeft = tokenMinutesLeft,
                libraryCount = library.games.size,
                installedCount = library.installedIds.size,
                onOpenWeb = onOpenWeb,
                onRefresh = { profileTick++ },
                modifier = mod,
            )
        }
    }
}

// ── Login gate ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun EpicLoginScreen(onLoginClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Epic Games",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Sign in to browse the Epic Games Store, claim this week's free games and download your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLoginClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp).steamFocusRing(RoundedCornerShape(8.dp)),
            ) { Text("Login with Epic Games") }
        }
    }
}
