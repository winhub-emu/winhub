package com.winhub.hernandez.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.AmazonMainActivity
import com.winhub.hernandez.store.EpicMainActivity
import com.winhub.hernandez.store.GogMainActivity
import com.winhub.hernandez.store.SteamMainActivity
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.store.xbox.XboxMainActivity

private class PortraitStore(val name: String, val blurb: String, val screen: Screen, val activity: Class<*>)

private val portraitStores = listOf(
    PortraitStore("Steam", "Your Steam library", Screen.Steam, SteamMainActivity::class.java),
    PortraitStore("Epic Games", "Library and free games", Screen.Epic, EpicMainActivity::class.java),
    PortraitStore("GOG", "DRM-free games", Screen.Gog, GogMainActivity::class.java),
    PortraitStore("Xbox", "Game Pass and PC games", Screen.Xbox, XboxMainActivity::class.java),
    PortraitStore("Amazon Games", "Prime Gaming library", Screen.Amazon, AmazonMainActivity::class.java),
)

/**
 * Portrait Stores page: one big touch row per store, each opening that store's classic screen
 * (the full-screen activity), not the Deck-styled store UI.
 */
@Composable
internal fun PortraitStoresScreen() {
    val context = LocalContext.current
    val signedIn = remember { portraitStores.associate { it.screen to deckSignedIn(context, it.screen) } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("Stores", color = Deck.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Your game libraries", color = Deck.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        portraitStores.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { store ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Deck.surface)
                            .clickable { context.startActivity(Intent(context, store.activity)) }
                            .padding(16.dp),
                    ) {
                        // The same logo and brand colours as the landscape store picker.
                        val logo = rememberDeckLogo(deckStoreLogo(store.screen))
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.verticalGradient(deckStoreBrand(store.screen))),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (logo != null) {
                                Image(
                                    bitmap = logo,
                                    contentDescription = store.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(0.8f),
                                )
                            } else {
                                Text(store.name.take(1).uppercase(), color = Deck.onBackground, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Column {
                            Text(store.name, color = Deck.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (signedIn[store.screen] == true) "Signed in" else "Sign in",
                                color = Deck.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        PortraitFeaturedOnSteam()
        DeckDownloadingNow()
    }
}
