package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.GameFacts
import com.winhub.hernandez.store.SteamAchievement

/**
 * Shared scaffold for the in-Deck install/detail screens (GOG / Epic / Amazon / Steam) — the
 * DroidDeck game page: back row, hero banner with the title and credits over a store gradient,
 * Metacritic + HowLongToBeat facts, the install state machine, the store's description and (Steam)
 * achievements. Every store feeds the same inputs; the extra handles default off.
 */
@Composable
internal fun DeckInstallShell(
    title: String,
    art: String?,
    dev: String,
    onBack: () -> Unit,
    progress: Int,
    status: String,
    finished: Boolean,
    error: String?,
    started: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    canInstall: Boolean = true,
    onPauseResume: (() -> Unit)? = null,
    paused: Boolean = false,
    onOpenStore: (() -> Unit)? = null,
    storeLabel: String = "",
    publisher: String = "",
    releaseDate: String = "",
    description: String = "",
    achievements: List<SteamAchievement>? = null,
    achievementsLoading: Boolean = false,
    installed: Boolean = false,
    addGame: DeckAddRequest? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    var addOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }.padding(vertical = 4.dp),
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = null,
                tint = Deck.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text("Back", color = Deck.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(14.dp))

        // ── Hero: art banner with the page's identity burned into its foot, DroidDeck style. ──
        val released = GameFacts.releaseLabel(releaseDate) ?: releaseDate.takeIf { it.isNotBlank() }
        val byline = listOfNotNull(
            dev.takeIf { it.isNotBlank() },
            publisher.takeIf { it.isNotBlank() && !it.equals(dev, ignoreCase = true) },
            released,
        ).joinToString(" · ")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(shape)
                .background(Deck.surface)
                .border(1.dp, Deck.line, shape),
        ) {
            if (!art.isNullOrEmpty()) {
                AsyncImage(
                    model = art,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Deck.background.copy(alpha = 0.35f),
                            1f to Deck.background.copy(alpha = 0.96f),
                        ),
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                val eyebrow = when {
                    installed || finished -> "INSTALLED"
                    storeLabel.isNotBlank() -> storeLabel.uppercase()
                    else -> ""
                }
                if (eyebrow.isNotBlank()) {
                    Text(
                        text = eyebrow,
                        color = Deck.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                    )
                }
                Text(
                    text = title,
                    color = Deck.onBackground,
                    fontSize = 23.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (byline.isNotBlank()) {
                    Text(
                        text = byline,
                        color = Deck.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.size(10.dp))
        DeckScoresRow(rememberDeckScores(title))
        Spacer(Modifier.size(14.dp))

        if (finished || installed) {
            Text("Installed", color = Deck.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (addGame != null) {
                Spacer(Modifier.size(10.dp))
                DeckButton("Add Game", primary = true) { addOpen = true }
            }
            if (onOpenStore != null) {
                Spacer(Modifier.size(8.dp))
                DeckButton("Open in $storeLabel", primary = false) { onOpenStore() }
            }
        } else if (started && error == null) {
            LinearProgressIndicator(
                progress = { (progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = Deck.primary,
                trackColor = Deck.surfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Text(status, color = Deck.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeckButton("Cancel", primary = false) { onCancel() }
                if (onPauseResume != null) {
                    DeckButton(if (paused) "Resume" else "Pause", primary = false) { onPauseResume() }
                }
            }
        } else {
            if (error != null) {
                Text(error, color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(10.dp))
            }
            if (canInstall) {
                DeckButton("Install", primary = true) { onStart() }
            }
            if (onOpenStore != null) {
                if (canInstall) Spacer(Modifier.size(8.dp))
                DeckButton("Open in $storeLabel", primary = !canInstall) { onOpenStore() }
            }
        }

        DeckAboutCard(GameFacts.cleanText(description))
        if (achievements != null || achievementsLoading) {
            DeckAchievementsRow(achievements.orEmpty(), achievementsLoading)
        }
    }
    if (addOpen && addGame != null) {
        DeckStoreAddFlow(request = addGame, onDismiss = { addOpen = false })
    }
}
