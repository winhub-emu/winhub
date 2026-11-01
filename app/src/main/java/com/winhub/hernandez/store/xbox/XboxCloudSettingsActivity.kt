package com.winhub.hernandez.store.xbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.store.DeckStoreTheme
import com.winhub.hernandez.ui.theme.WinlatorTheme
import com.winhub.hernandez.store.download.StoreBadge
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreDetailHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Xbox › Profile › Cloud gaming settings: the player's defaults (the same panel as the in-player menu). */
class XboxCloudSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        setContent {
            WinlatorTheme {
            DeckStoreTheme {
                var settings by remember { mutableStateOf(XboxCloudSettings.load(this)) }
                var regions by remember { mutableStateOf(emptyList<String>()) }
                // The region names come from the cloud sign-in (the store account; no web sign-in).
                LaunchedEffect(Unit) {
                    regions = withContext(Dispatchers.IO) {
                        runCatching {
                            val account = XboxAccountStore.signedInAs(applicationContext) ?: return@runCatching emptyList()
                            XboxCloudSession.get(applicationContext, account).regionList.map { it.name }
                        }.getOrDefault(emptyList())
                    }
                }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                    StoreDetailHeader(onBack = { finish() }, storeBadge = { StoreBadge(Store.XBOX) })
                    Text("Cloud gaming", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(horizontal = 16.dp))
                    XboxCloudSettingsPanel(settings, regions, live = false) { changed ->
                        settings = changed
                        XboxCloudSettings.save(this@XboxCloudSettingsActivity, changed)
                    }
                }
            }
            }
        }
    }
}
