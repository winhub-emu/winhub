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
 * The Amazon Games section's host — flipped from a login card + "View Game Library" button to the
 * same **store-first** three-tab shell the GOG and Epic sections use (**Store / Library /
 * Profile**) over [StoreSectionHost].
 *
 * Login gate ([AmazonCredentialStore.isLoggedIn]) and sign-out (device deregistration) are
 * unchanged. The full games screen ([AmazonGamesActivity]) stays one tap away; every title opens
 * [AmazonGameDetailActivity]; Prime Gaming claims go through [StoreWebActivity].
 */
class AmazonMainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "amazon_tab"
        private const val REQ_WEB = 4103
    }

    private var isLoggedIn by mutableStateOf(false)
    private var deviceSerial by mutableStateOf("")
    private var tokenMinutesLeft by mutableStateOf(0L)
    private var startTab by mutableStateOf(0)
    private var webReturnTick by mutableStateOf(0)
    private var resultBarMsg by mutableStateOf<String?>(null)

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
                    AmazonStorefrontHost(
                        initialTab = startTab,
                        deviceSerial = deviceSerial,
                        tokenMinutesLeft = tokenMinutesLeft,
                        webReturnTick = webReturnTick,
                        externalMessage = resultBarMsg,
                        onExternalMessageShown = { resultBarMsg = null },
                        onOpenWeb = { url, title -> startActivityForResult(StoreWebActivity.intent(this, url, title), REQ_WEB) },
                        onSignOut = { signOut() },
                        onClose = { finish() },
                    )
                } else {
                    AmazonLoginScreen(onLoginClick = {
                        startActivity(Intent(this@AmazonMainActivity, AmazonLoginActivity::class.java))
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
        val loggedIn = AmazonCredentialStore.isLoggedIn(this)
        isLoggedIn = loggedIn
        if (loggedIn) {
            val creds = AmazonCredentialStore.load(this)
            if (creds != null) {
                deviceSerial = creds.deviceSerial ?: ""
                tokenMinutesLeft = (creds.expiresAt - System.currentTimeMillis()) / 60000L
            }
        }
    }

    private fun signOut() {
        val creds = AmazonCredentialStore.load(this)
        if (creds != null && creds.accessToken != null) {
            val token = creds.accessToken
            Thread { AmazonAuthClient.deregisterDevice(token) }.start()
        }
        AmazonCredentialStore.clear(this)
        refreshView()
        resultBarMsg = "Signed out of Amazon Games"
    }
}

// ── Host ──────────────────────────────────────────────────────────────────────────────────────

private val AMAZON_TABS = listOf(
    StoreSectionTab("store", "Store", Icons.Filled.Storefront),
    StoreSectionTab("library", "Library", Icons.Filled.VideoLibrary),
    StoreSectionTab("profile", "Profile", Icons.Filled.AccountCircle),
)

@Composable
private fun AmazonStorefrontHost(
    initialTab: Int,
    deviceSerial: String,
    tokenMinutesLeft: Long,
    webReturnTick: Int,
    externalMessage: String?,
    onExternalMessageShown: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, AMAZON_TABS.size - 1)) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(externalMessage) { if (externalMessage != null) { message = externalMessage; onExternalMessageShown() } }
    val library = rememberAmazonLibrary(onMessage = { message = it })

    var profile by remember { mutableStateOf(AmazonUserData.cached(ctx)) }
    var profileLoading by remember { mutableStateOf(false) }
    var profileTick by remember { mutableStateOf(0) }
    LaunchedEffect(profileTick) {
        profileLoading = true
        val fresh = runCatching { AmazonUserData.fetch(ctx) }.getOrNull()
        if (fresh != null) profile = fresh
        profileLoading = false
    }
    // Back from Prime Gaming: a claim may have landed — re-sync the entitlements.
    LaunchedEffect(webReturnTick) { if (webReturnTick > 0) library.refresh(true) }

    fun openGame(game: AmazonGame) {
        ctx.startActivity(
            Intent(ctx, AmazonGameDetailActivity::class.java)
                .putExtra("product_id", game.productId)
                .putExtra("entitlement_id", game.entitlementId)
                .putExtra("title", game.title)
                .putExtra("developer", game.developer)
                .putExtra("publisher", game.publisher)
                .putExtra("art_url", game.artUrl)
                .putExtra("product_sku", game.productSku),
        )
    }

    StoreSectionHost(
        storeName = "Amazon Games",
        tabs = AMAZON_TABS,
        selected = tab,
        onSelect = { tab = it },
        onClose = onClose,
        counts = mapOf("library" to library.games.size),
        statusSlot = {
            profile?.name?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        railStatusSlot = { StoreAvatar(emptyList(), profile?.name?.ifBlank { null } ?: "A", 36.dp) },
        actions = {
            IconButton(onClick = { library.refresh(true) }, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Sync library", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = { ctx.startActivity(Intent(ctx, AmazonGamesActivity::class.java)) },
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
                    Triple("Full library screen", Icons.Filled.ViewList, { ctx.startActivity(Intent(ctx, AmazonGamesActivity::class.java)) }),
                ),
                onSignOut = onSignOut,
            )
        },
        message = message,
        onMessageTimeout = { message = null },
    ) { wide, mod ->
        when (AMAZON_TABS[tab].key) {
            "store" -> AmazonStoreTab(
                wide = wide,
                state = library,
                onOpen = ::openGame,
                onOpenWeb = onOpenWeb,
                modifier = mod,
            )
            "library" -> AmazonLibraryTab(
                state = library,
                wide = wide,
                onOpen = ::openGame,
                onOpenFullLibrary = { ctx.startActivity(Intent(ctx, AmazonGamesActivity::class.java)) },
                modifier = mod,
            )
            else -> AmazonProfileTab(
                profile = profile,
                loading = profileLoading,
                wide = wide,
                deviceSerial = deviceSerial,
                tokenMinutesLeft = tokenMinutesLeft,
                libraryCount = library.games.size,
                installedCount = library.installedIds.size,
                updatableCount = library.updatableIds.size,
                onOpenWeb = onOpenWeb,
                onRefresh = { profileTick++ },
                modifier = mod,
            )
        }
    }
}

// ── Login gate ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AmazonLoginScreen(onLoginClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Amazon Games",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Sign in to claim Prime Gaming titles and download your Amazon Games library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLoginClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp).steamFocusRing(RoundedCornerShape(8.dp)),
            ) { Text("Login with Amazon") }
        }
    }
}
