package com.winhub.hernandez.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SETUP_STEPS = listOf(
    "System files",
    "Proton 9 arm64ec",
    "DXVK 2.4.1 gplasync",
    "FEXCore 2610 PPA unix",
    "VKD3D 3.0.1",
)

/**
 * First-open / ImageFS-reinstall surface. Landscape Deck layout: pulsing WinHub wordmark on the
 * left, one progress surface on the right that walks ImageFS and then each pinned component.
 * Everything is read-only until setup finishes; the Proceed button then takes controller focus.
 */
@Composable
fun SplashScreen(
    progress: Int,
    status: String,
    step: Int,
    degraded: Boolean,
    showProceed: Boolean = false,
    onProceed: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeckPalette.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DeckPalette.primary.copy(alpha = 0.13f),
                            Color.Transparent,
                        ),
                        radius = 1100f,
                    ),
                ),
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 56.dp, vertical = 36.dp),
                ) {
                    SplashBrand(Modifier.weight(1f))
                    SplashProgress(
                        progress = progress,
                        status = status,
                        step = step,
                        degraded = degraded,
                        showProceed = showProceed,
                        onProceed = onProceed,
                        modifier = Modifier.widthIn(max = 460.dp).fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                ) {
                    SplashBrand()
                    Spacer(Modifier.height(28.dp))
                    SplashProgress(
                        progress = progress,
                        status = status,
                        step = step,
                        degraded = degraded,
                        showProceed = showProceed,
                        onProceed = onProceed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashBrand(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "winhub")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "winhubPulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "winhubGlow",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.78f)
                .height(190.dp)
                .graphicsLayer { alpha = glow }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            DeckPalette.primary.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        ) {
            Row {
                Text(
                    text = "Win",
                    color = Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                )
                Text(
                    text = "Hub",
                    color = DeckPalette.primary,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                )
            }
            Text(
                text = "SYSTEM SETUP",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.2.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SplashProgress(
    progress: Int,
    status: String,
    step: Int,
    degraded: Boolean,
    showProceed: Boolean,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0, 100)
    val animated by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(320),
        label = "setupProgress",
    )
    val proceedFocus = remember { FocusRequester() }
    LaunchedEffect(showProceed) {
        if (showProceed) {
            delay(90)
            runCatching { proceedFocus.requestFocus() }
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Text(
            text = "Setting up",
            color = DeckPalette.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = status,
            color = DeckPalette.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DeckPalette.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(DeckPalette.primary, lerp(DeckPalette.primary, Color.White, 0.45f)),
                        ),
                    ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                text = "$clamped%",
                color = DeckPalette.onBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "STEP ${(step + 1).coerceAtMost(SETUP_STEPS.size)} OF ${SETUP_STEPS.size}",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
        }

        Spacer(Modifier.height(20.dp))
        SETUP_STEPS.forEachIndexed { index, label ->
            SetupStepRow(
                label = label,
                done = step > index,
                active = step == index,
            )
            if (index != SETUP_STEPS.lastIndex) Spacer(Modifier.height(9.dp))
        }

        if (degraded) {
            Text(
                text = "Some components were skipped. You can retry them later from Contents.",
                color = DeckPalette.error,
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        if (showProceed) {
            Spacer(Modifier.height(22.dp))
            SplashProceedButton(onProceed = onProceed, focusRequester = proceedFocus)
        }
    }
}

@Composable
private fun SetupStepRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    when {
                        done -> DeckPalette.primary
                        active -> DeckPalette.primary.copy(alpha = 0.22f)
                        else -> Color.Transparent
                    },
                )
                .border(
                    width = 1.5.dp,
                    color = when {
                        done -> DeckPalette.primary
                        active -> DeckPalette.primary
                        else -> DeckPalette.line2
                    },
                    shape = CircleShape,
                ),
        ) {
            when {
                done -> Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(DeckPalette.onPrimary),
                )
                active -> Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(DeckPalette.primary),
                )
            }
        }
        Text(
            text = label,
            color = if (done || active) DeckPalette.onBackground else DeckPalette.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun SplashProceedButton(
    onProceed: () -> Unit,
    focusRequester: FocusRequester,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onProceed()
                    true
                } else false
            }
            .clip(RoundedCornerShape(12.dp))
            .background(DeckPalette.primary)
            .deckOuterFocusStroke(
                interaction = interaction,
                shape = RoundedCornerShape(12.dp),
                focusColor = DeckPalette.onPrimary,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onProceed,
            ),
    ) {
        Text(
            text = "Proceed",
            color = DeckPalette.onPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
