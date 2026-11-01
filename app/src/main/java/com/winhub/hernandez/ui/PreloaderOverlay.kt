package com.winhub.hernandez.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.core.Failure
import com.winhub.hernandez.core.Phase
import com.winhub.hernandez.core.PreloaderDetails
import com.winhub.hernandez.core.PreloaderState
import com.winhub.hernandez.ui.screens.Deck
import com.winhub.hernandez.ui.screens.DeckCapsuleWall
import com.winhub.hernandez.ui.screens.DeckCover
import com.winhub.hernandez.ui.screens.decodeCover
import com.winhub.hernandez.ui.screens.featuredCovers
import com.winhub.hernandez.ui.screens.hueOf
import com.winhub.hernandez.ui.theme.AppThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The hero surface is always laid over a dark scrim, so text/accents use fixed light-on-dark
// values that read over any cover art rather than the ambient theme's surface colours.
private val HeroText = Color(0xFFF3F5F8)
private val HeroTextDim = Color(0xFFB6BCC6)
// Live theme accent: every accent in this overlay — launch steps, spinner, hint, the shutdown bar
// and the corner light — follows the selected theme (Marcescence blue by default).
private val StatusAccent get() = AppThemeState.deckPreset.primary
private val HeroAccent get() = StatusAccent

/**
 * The Home tab's look, shared by the launch hero and the shutdown/status screen: the deck
 * background, the drifting wall of bundled cover art and the blue corner light. Fully opaque —
 * the overlay is a sibling drawn over the session's X server surface and its on-screen controls,
 * so nothing may show through while a launch is still loading.
 */
@Composable
private fun DeckCoverWallBackdrop() {
    val context = LocalContext.current
    var covers by remember { mutableStateOf<List<DeckCover>>(emptyList()) }
    LaunchedEffect(Unit) {
        val decoded = withContext(Dispatchers.IO) {
            featuredCovers.map { (name, asset) ->
                DeckCover(name, hueOf(name), decodeCover(context, asset))
            }
        }
        covers = decoded
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background),
    ) {
        DeckCapsuleWall(
            covers = if (covers.isEmpty()) List(12) { index -> DeckCover("", index / 12f) } else covers,
            driftMs = if (covers.isEmpty()) 75_000 else 40_000,
        )
    }
    // The Home pane's blue corner light, same position and falloff.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val radius = 460.dp.toPx()
                val center = Offset(size.width - radius * 0.35f, radius * 0.35f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StatusAccent.copy(alpha = 0.24f),
                            StatusAccent.copy(alpha = 0.10f),
                            StatusAccent.copy(alpha = 0.04f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
    )
}

/**
 * Full-bleed "game hero" launch overlay. The screen is the Home tab's drifting cover wall behind a
 * dark scrim gradient; the game name sits above a stepped progress readout (determinate bar for
 * the measurable app-side setup, an indeterminate spinner for the guest-boot tail) and a failure
 * card.
 * Shows/hides based on PreloaderState.ui. Place at the top of the host Compose hierarchy.
 */
@Composable
fun PreloaderOverlay() {
    val state by PreloaderState.ui.collectAsState()
    val ui = state ?: return

    // Centered status/shutdown screen — cover-wall backdrop + message + slim indeterminate bar.
    if (ui.centered) {
        CenteredStatus(ui.tailLabel.ifEmpty { ui.title }, ui.hint, ui.elapsed, ui.percent)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Background: the Home tab's cover wall + blue corner light. ---
        DeckCoverWallBackdrop()

        // --- Scrim: darken the top a touch and heavily at the bottom for legible text. ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.55f),
                        0.35f to Color.Black.copy(alpha = 0.35f),
                        0.72f to Color.Black.copy(alpha = 0.78f),
                        1.0f to Color.Black.copy(alpha = 0.94f),
                    )
                ),
        )

        val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

        // --- Right-side game details panel: cover + name + genres/year/metacritic + description.
        // Shown only when the launched shortcut has accumulated details (graceful no-op otherwise) and
        // never over the failure card. Anchored top-right so it clears the bottom-left hero text.
        val details = ui.details
        AnimatedVisibility(
            visible = ui.phase != Phase.FAILED && details != null && details.hasAny,
            modifier = insets.align(Alignment.TopEnd).padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            if (details != null) GameDetailsPanel(details, ui.title, ui.coverArt ?: ui.icon)
        }

        // --- Hero content: game name + stepped progress, anchored low. ---
        Column(
            modifier = insets
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            if (ui.title.isNotEmpty()) {
                Text(
                    text = ui.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = HeroText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // "container · resolution" meta line, mirrored 1:1 from the game card (the spec
                // chips themselves were removed from every card by request).
                val spec = ui.spec
                if (spec != null) {
                    if (spec.meta.isNotEmpty()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = spec.meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HeroTextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            when (ui.phase) {
                Phase.SETUP -> SetupProgress(ui.stepIndex, ui.stepTotal, ui.stepLabel)
                Phase.GUEST -> GuestProgress(ui.tailLabel)
                Phase.FAILED -> FailureCard(ui.failure)
            }

            // Not-frozen reassurance line (SETUP/GUEST only; the failure card is self-contained).
            if (ui.phase != Phase.FAILED) {
                AnimatedVisibility(visible = ui.hint != null) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = ui.hint ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = HeroAccent,
                        )
                    }
                }
                // Cancel launch — aborts the launch and tears down the game/container so the user is
                // never stuck on the launch screen. Only during a real launch (not the shutdown message).
                if (ui.cancellable) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = { PreloaderState.onCancel?.run() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeroText),
                    border = BorderStroke(1.dp, HeroText.copy(alpha = 0.45f)),
                ) {
                    Text("Cancel launch")
                }
                }
            }
        }
    }
}

@Composable
private fun SetupProgress(stepIndex: Int, stepTotal: Int, stepLabel: String) {
    // Stage row: uppercase mono label on the left, "N / M" counter on the right.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stepLabel.isNotEmpty()) {
            Text(
                text = stepLabel.trimEnd('…', '.', ' ').uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp,
                color = HeroText.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (stepTotal > 0 && stepIndex > 0) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "$stepIndex / $stepTotal",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = HeroAccent,
            )
        }
    }
    Spacer(Modifier.height(11.dp))
    StepPips(stepIndex, stepTotal)
}

/** Discrete step segments: done = solid accent, current = pulsing accent, pending = faint tint. */
@Composable
private fun StepPips(stepIndex: Int, stepTotal: Int) {
    val transition = rememberInfiniteTransition(label = "pips")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pipPulse",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 1..stepTotal) {
            val color = when {
                i < stepIndex -> HeroAccent
                i == stepIndex -> HeroAccent.copy(alpha = pulse)
                else -> HeroAccent.copy(alpha = 0.16f)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

/**
 * Full-bleed "working…" screen for shutdown and other indeterminate operations (backup, restore,
 * install, create-container). The Home tab's drifting cover wall fills the screen behind a scrim
 * with the blue corner light — exactly the Home pane's look; the message + slim bar sit low.
 */
@Composable
private fun CenteredStatus(message: String, subMessage: String? = null, elapsed: String? = null, percent: Int = -1) {
    Box(modifier = Modifier.fillMaxSize()) {
        // The Home tab's cover wall + blue corner light.
        DeckCoverWallBackdrop()
        // Darken overall a touch and heavily at the bottom so the status text stays legible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.30f),
                        0.45f to Color.Black.copy(alpha = 0.28f),
                        0.78f to Color.Black.copy(alpha = 0.62f),
                        1.0f to Color.Black.copy(alpha = 0.90f),
                    )
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp)
                .padding(bottom = 46.dp),
        ) {
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeroText,
                )
                Spacer(Modifier.height(if (!subMessage.isNullOrEmpty()) 8.dp else 20.dp))
            }
            // Live sub-status (e.g. "Backing up your saves…" / "Uploading: <file>") — lets slow
            // operations like the GOG cloud upload show they're actively working, not frozen.
            AnimatedVisibility(visible = !subMessage.isNullOrEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = subMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HeroText.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            // Determinate while something measurable is happening (the Steam client's download,
            // read out of the session log), the slim indeterminate bar otherwise.
            val barModifier = Modifier
                .width(190.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(4.dp))
            if (percent >= 0) {
                LinearProgressIndicator(
                    progress = { percent.coerceIn(0, 100) / 100f },
                    color = StatusAccent,
                    trackColor = Color.White.copy(alpha = 0.16f),
                    strokeCap = StrokeCap.Round,
                    modifier = barModifier,
                )
            } else {
                LinearProgressIndicator(
                    color = StatusAccent,
                    trackColor = Color.White.copy(alpha = 0.16f),
                    strokeCap = StrokeCap.Round,
                    modifier = barModifier,
                )
            }
            // The clock: a first run is minutes of nothing on screen, and a number that keeps
            // moving is what separates "still working" from "hung" for the person watching.
            AnimatedVisibility(visible = !elapsed.isNullOrEmpty()) {
                Text(
                    text = elapsed ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = HeroText.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/**
 * Right-side details card for the launch hero: a crisp small cover, the game name, a genres line, a
 * release-year + metacritic row, then the short description. Sits on a translucent dark surface so it
 * reads over any cover art. Width-capped and height-scrollable so long descriptions never overflow.
 */
@Composable
private fun GameDetailsPanel(details: PreloaderDetails, title: String, cover: Bitmap?) {
    Surface(
        color = Color.Black.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.42f)
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 92.dp, height = 138.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.height(12.dp))
            }
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = HeroText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Release year · Metacritic pill.
            if (!details.releaseYear.isNullOrEmpty() || details.metacritic != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!details.releaseYear.isNullOrEmpty()) {
                        Text(
                            text = details.releaseYear,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HeroTextDim,
                        )
                        if (details.metacritic != null) Spacer(Modifier.width(10.dp))
                    }
                    details.metacritic?.let { MetacriticPill(it) }
                }
            }
            if (details.genres.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = details.genres.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = HeroAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!details.description.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = details.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = HeroTextDim,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Small Metacritic score badge, coloured green/amber/red by the usual 75/50 thresholds. */
@Composable
private fun MetacriticPill(score: Int) {
    val bg = when {
        score >= 75 -> Color(0xFF6AB04C)
        score >= 50 -> Color(0xFFE1A100)
        else -> Color(0xFFEB4D4B)
    }
    Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun GuestProgress(tailLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            color = HeroAccent,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(20.dp),
        )
        if (tailLabel.isNotEmpty()) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = tailLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = HeroText,
            )
        }
    }
}

/**
 * One-line help for the failure card's extra actions, keyed on the label the activity sends
 * ([com.winhub.hernandez.core.FailureAction] carries no copy of its own). Unknown labels get a
 * neutral line rather than nothing, so a new action is never left unexplained.
 */
private fun actionHelp(label: String): String = when (label) {
    "Retry" -> "runs the Steam checks again and relaunches the game through SteamLite."
    "Launch with Goldberg" -> "relaunches offline with the stand-in Steam — no online play, no VAC."
    "Keep going" -> "ignores this and keeps waiting for the game; online features may not work."
    else -> "does what it says; the log folder has the details if it doesn't help."
}

/** The launch popup's corner "?" (LaunchMethodSheet.HelpDot), on the card's primary accent. */
@Composable
private fun ActionHelpDot(highlighted: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(16.dp).clip(CircleShape)
            .background(if (highlighted) cs.primary.copy(alpha = 0.16f) else cs.surfaceVariant)
            .border(1.dp, if (highlighted) cs.primary.copy(alpha = 0.55f) else cs.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (highlighted) cs.primary else cs.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FailureCard(failure: Failure?) {
    failure ?: return
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    text = "Failed · ${failure.stage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = failure.what,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!failure.detail.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = failure.detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            // Extra actions (SteamLite: "Retry" / "Launch with Goldberg") sit on their own row so
            // the standard Close / Open-log pair below never reflows. A "?" at the row's start
            // toggles a one-line-per-button explainer (keyed on this failure so it resets per card).
            if (failure.actions.isNotEmpty()) {
                var showActionHelp by remember(failure) { mutableStateOf(false) }
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ActionHelpDot(highlighted = showActionHelp) { showActionHelp = !showActionHelp }
                    Spacer(Modifier.weight(1f))
                    failure.actions.forEachIndexed { i, a ->
                        if (i > 0) Spacer(Modifier.width(8.dp))
                        if (a.primary) Button(onClick = { a.run.run() }) { Text(a.label) }
                        else OutlinedButton(onClick = { a.run.run() }) { Text(a.label) }
                    }
                }
                if (showActionHelp) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable { showActionHelp = false }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = failure.actions.joinToString("\n") { "${it.label} — ${actionHelp(it.label)}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (failure.loggingEnabled && !failure.logDir.isNullOrEmpty()) {
                Text(
                    text = "Log saved to ${failure.logDir}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { PreloaderState.onOpenLog?.run() }) {
                        Text("Open log folder")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { PreloaderState.close() }) {
                        Text("Close")
                    }
                }
            } else {
                Text(
                    text = "Enable logging in Settings → Logs and relaunch to capture the cause.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { PreloaderState.close() }) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
