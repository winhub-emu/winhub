package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.container.Shortcut

// The XMB Home's details panel beside the options column, compact so that it always fits above the footer hints: the store tag sits on
// the pills' line, the driver / VKD3D / backend dots follow, then the facts row (playtime, genre, year, score) and the description. How
// much of it shows is decided by xmbInfoPlan (XmbSteamLogic.kt) from the room there is, so no row is ever cut: the description shrinks
// and goes first, then the dots, then the pills line. The Classic XMB keeps its own panel (XmbInfo in ShortcutsXmbView.kt).

/**
 * The panel for [s] in a box [availW] x [availH] dp (the caller clips to it): the store tag and pills, the dots line, the facts row
 * ([playtime] first) and at most [descMax] lines of its description. [storeBadges] draws the store tag(s).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun XmbSteamInfo(
    s: Shortcut,
    playtime: String?,
    availW: Float,
    availH: Float,
    descMax: Int,
    storeBadges: @Composable (Shortcut) -> Unit,
) {
    val context = LocalContext.current
    val k = LocalDensity.current.fontScale
    val primary = MaterialTheme.colorScheme.primary
    val spec = remember(s) { buildLaunchSpec(s, context) }
    val details = remember(s) { buildLaunchDetails(s) }
    val android = false

    // An Android entry runs no Wine stack, so it has no pills and no dots, only its store tag.
    val chips = remember(spec, android, primary) {
        if (android) emptyList<Pair<String, Color>>() else buildList<Pair<String, Color>> {
            if (spec.rendererLabel.isNotEmpty()) add(spec.rendererLabel to primary)
            if (spec.dxvkVersion.isNotEmpty()) add("DXVK ${spec.dxvkVersion}" to primary)
            if (spec.frameGenLabel.isNotEmpty()) add(spec.frameGenLabel to ChipFgColor)
            if (spec.eosEnabled) add("EOS" to ChipEosColor)
        }
    }
    val dots = remember(spec, android) {
        if (android) emptyList<Pair<String, Color>>() else buildList<Pair<String, Color>> {
            if (spec.driverLabel.isNotEmpty()) add(spec.driverLabel to ChipDriverColor)
            if (spec.vkd3dVersion.isNotEmpty()) add("VKD3D ${spec.vkd3dVersion}" to ChipVkd3dColor)
            if (spec.backendLabel.isNotEmpty()) add(spec.backendLabel to ChipCpuColor)
        }
    }
    val played = playtime ?: "Not played yet"
    val genres = remember(details) { details.genres.take(3).joinToString(" · ") }
    val year = details.releaseYear?.takeIf { it.isNotBlank() }
    val score = details.metacritic
    val desc = details.description?.takeIf { it.isNotBlank() }

    // How many lines each block takes in this width (estimates, on the wide side: see xmbTextWidth).
    val headerWidths = buildList<Float> {
        add(72f) // the store tag(s)
        chips.forEach { add(xmbTextWidth(it.first.length, 10f, k) + 16f) }
    }
    val dotWidths = dots.map { 11f + xmbTextWidth(it.first.length, 10f, k) }
    val factWidths = buildList<Float> {
        add(17f + xmbTextWidth(played.length, 12f, k))
        if (genres.isNotEmpty()) add(xmbTextWidth(genres.length, 12f, k))
        if (year != null) add(xmbTextWidth(year.length, 12f, k))
        if (score != null) add(10f + xmbTextWidth(3, 11f, k))
    }
    val plan = xmbInfoPlan(
        availH = availH,
        fontScale = k,
        headerLines = xmbFlowLines(headerWidths, 5f, availW),
        dotsLines = xmbFlowLines(dotWidths, 8f, availW),
        factsLines = xmbFlowLines(factWidths, 10f, availW),
        descWanted = if (desc != null) descMax else 0,
    )

    Column(Modifier.fillMaxWidth()) {
        if (plan.header) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            storeBadges(s)
            // A chip is a little lower than a store tag: the same padding above and below lines them up.
            chips.forEach { (text, color) -> Box(Modifier.padding(vertical = 1.5.dp)) { CompChip(text, color, textColor = Color.White) } }
        }
        if (plan.dots) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        ) {
            dots.forEach { (text, color) -> SecondarySpec(text, color, textColor = Color.White) }
        }
        if (plan.facts) {
            val factColor = Color.White
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(played, color = factColor, fontSize = 12.sp, maxLines = 1)
                }
                if (genres.isNotEmpty()) Text(genres, color = factColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (year != null) Text(year, color = factColor, fontSize = 12.sp, maxLines = 1)
                if (score != null) {
                    Text(
                        "$score", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(metacriticColor(score)).padding(horizontal = 5.dp),
                    )
                }
            }
        }
        if (desc != null && plan.descLines > 0) {
            Text(
                desc, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp,
                maxLines = plan.descLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}
