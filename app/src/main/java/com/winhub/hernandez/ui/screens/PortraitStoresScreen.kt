package com.winhub.hernandez.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Stores", color = Deck.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        portraitStores.forEach { store ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Deck.surface)
                    .clickable { context.startActivity(Intent(context, store.activity)) }
                    .padding(horizontal = 14.dp),
            ) {
                // The same logo and brand colours as the landscape store picker.
                val logo = rememberDeckLogo(deckStoreLogo(store.screen))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
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
                        Text(store.name.take(1).uppercase(), color = Deck.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(store.name, color = Deck.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(store.blurb, color = Deck.onSurfaceVariant, fontSize = 14.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Deck.onSurfaceVariant)
            }
        }
        PortraitFeaturedOnSteam()
    }
}
