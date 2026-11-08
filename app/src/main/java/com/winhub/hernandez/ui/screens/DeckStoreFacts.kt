package com.winhub.hernandez.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.store.GameFacts
import com.winhub.hernandez.store.SteamAchievement
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One title's Metacritic + HowLongToBeat answers, loaded once off the main thread. */
internal data class DeckScores(
    val metacritic: GameFacts.Metacritic? = null,
    val hltb: GameFacts.Hltb? = null,
    val loading: Boolean = false,
)

@Composable
internal fun rememberDeckScores(title: String): DeckScores {
    val ctx = LocalContext.current.applicationContext
    var scores by remember(title) {
        mutableStateOf(DeckScores(loading = title.isNotBlank()))
    }
    LaunchedEffect(title) {
        if (title.isBlank()) {
            scores = DeckScores()
            return@LaunchedEffect
        }
        scores = withContext(Dispatchers.IO) {
            DeckScores(
                metacritic = runCatching { GameFacts.metacritic(ctx, title) }.getOrNull(),
                hltb = runCatching { GameFacts.hltb(ctx, title) }.getOrNull(),
                loading = false,
            )
        }
    }
    return scores
}

private fun openFactUrl(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun metacriticTint(score: Int) = when {
    score >= 75 -> androidx.compose.ui.graphics.Color(0xFF66CC33)
    score >= 50 -> androidx.compose.ui.graphics.Color(0xFFFFCC33)
    else -> androidx.compose.ui.graphics.Color(0xFFFF6874)
}

/**
 * The DroidDeck "facts" row: a colour-coded Metacritic badge and HowLongToBeat's main / 100%
 * times. Both are links to the real page; nothing renders until an answer exists.
 */
@Composable
internal fun DeckScoresRow(scores: DeckScores, modifier: Modifier = Modifier) {
    val mc = scores.metacritic
    val hltb = scores.hltb
    if (mc == null && hltb == null) return
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 2.dp),
    ) {
        if (mc != null) {
            Column(
                modifier = Modifier
                    .clip(shape)
                    .background(Deck.surfaceVariant)
                    .border(1.dp, Deck.line, shape)
                    .clickable { openFactUrl(ctx, mc.url) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 34.dp, height = 26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(metacriticTint(mc.score)),
                    ) {
                        Text(
                            text = mc.score.toString(),
                            color = androidx.compose.ui.graphics.Color(0xFF0A0B0D),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "METACRITIC",
                        color = Deck.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
        if (hltb != null) {
            val runs = listOfNotNull(
                hltb.main.takeIf { it > 0 }?.let { "MAIN" to it },
                hltb.completionist.takeIf { it > 0 }?.let { "100%" to it },
            )
            if (runs.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .clip(shape)
                        .background(Deck.surfaceVariant)
                        .border(1.dp, Deck.line, shape)
                        .clickable { openFactUrl(ctx, hltb.url) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "HOW LONG TO BEAT",
                        color = Deck.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for ((label, secs) in runs) {
                            val hours = GameFacts.hours(secs)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = hours ?: "${maxOf(1, secs / 60)}",
                                    color = Deck.onBackground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = (if (hours != null) "h " else "m ") + label,
                                    color = Deck.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The game description, exactly as the store published it (cleaned), capped to a scrollable card. */
@Composable
internal fun DeckAboutCard(description: String, modifier: Modifier = Modifier) {
    if (description.isBlank()) return
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Deck.surface)
            .border(1.dp, Deck.line, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(
            text = "ABOUT",
            color = Deck.primary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.heightIn(max = 150.dp)) {
            Text(
                text = description,
                color = Deck.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.verticalScroll(scroll),
            )
        }
    }
}

/**
 * Steam achievements, laid out like the old detail page's icon grid but in Deck language:
 * progress line, a thin bar, then a horizontally scrolling row of icon tiles.
 */
@Composable
internal fun DeckAchievementsRow(
    achievements: List<SteamAchievement>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!loading && achievements.isEmpty()) return
    val shape = RoundedCornerShape(12.dp)
    val done = achievements.count { it.unlocked }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(shape)
            .background(Deck.surface)
            .border(1.dp, Deck.line, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ACHIEVEMENTS",
                color = Deck.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (loading) "Loading…" else "$done / ${achievements.size}",
                color = Deck.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Deck.surfaceVariant),
        ) {
            if (done > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(done.toFloat() / achievements.size)
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Deck.primary),
                )
            }
        }
        if (achievements.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(achievements, key = { it.apiName }) { a ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Deck.surfaceVariant)
                            .border(1.dp, Deck.line, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp),
                        ) {
                            val model: Any? = if (a.unlocked) {
                                a.localIconPath?.takeIf { it.isNotBlank() }?.let(::File)
                                    ?: a.iconUrl.takeIf { it.isNotBlank() }
                            } else {
                                a.localIconGrayPath?.takeIf { it.isNotBlank() }?.let(::File)
                                    ?: a.iconGrayUrl.takeIf { it.isNotBlank() }
                            }
                            AsyncImage(
                                model = model,
                                contentDescription = a.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(44.dp).alpha(if (a.unlocked) 1f else 0.55f),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = a.displayName,
                            color = if (a.unlocked) Deck.onBackground else Deck.onSurfaceVariant,
                            fontSize = 9.sp,
                            fontWeight = if (a.unlocked) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
