package com.winhub.hernandez.store

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.ui.SocialConfigsContent
import com.winhub.hernandez.ui.SocialContent
import com.winhub.hernandez.ui.SocialProfileContent

/**
 * The Social Hub as a full-screen XMB page (Settings > Store style = XMB): the stores' slim rail with Feed, Configs and
 * Profile, and the page beside it on the glass. Opened from Home's Social card and the Social tab.
 */
class SocialHubActivity : ComponentActivity() {
    // XMB: L2 / R2 (buttons or analog triggers) step through the rail's tabs; see StoreXmbInput.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        StoreXmbInput.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        StoreXmbInput.onMotion(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        setContent {
            StoreXmbTheme { SocialHubXmbHost(onClose = { finish() }) }
        }
    }
}

private val SOCIAL_TABS = listOf(
    StoreSectionTab("feed", "Feed", Icons.Filled.Forum),
    StoreSectionTab("configs", "Configs", Icons.Filled.Tune),
    StoreSectionTab("profile", "Profile", Icons.Filled.Person),
)

@Composable
private fun SocialHubXmbHost(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val account by com.winhub.hernandez.store.SocialHubStore.account.collectAsState()
    val openPost by com.winhub.hernandez.store.SocialHubStore.openPost.collectAsState()
    LaunchedEffect(Unit) { com.winhub.hernandez.store.SocialHubStore.init(ctx) }

    StoreSectionHost(
        storeName = "Social Hub",
        tabs = SOCIAL_TABS,
        selected = tab,
        onSelect = { tab = it },
        onClose = onClose,
        statusSlot = {
            Text(
                text = account?.let { "@${it.login}" } ?: "Not signed in",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
        railStatusSlot = { StoreAvatar(listOfNotNull(account?.avatarUrl), account?.login ?: "S", 36.dp) },
        actions = {
            IconButton(onClick = { SocialHubStore.refresh(); SocialHubStore.refreshConfigs() }, modifier = Modifier.steamFocusRing()) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
        },
        railActions = {
            IconButton(
                onClick = { SocialHubStore.refresh(); SocialHubStore.refreshConfigs() },
                modifier = Modifier.steamFocusRing().xmbRailStop(),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
            }
        },
    ) { _, mod ->
        // Registered after the host's own handler, so an open post closes before the page does.
        BackHandler(enabled = tab == 0 && openPost != null) { SocialHubStore.openPost(null) }
        Box(mod.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            when (tab) {
                0 -> SocialContent(showTitle = false)
                1 -> SocialConfigsContent()
                else -> SocialProfileContent(onGoFeed = { tab = 0 })
            }
        }
    }
}
