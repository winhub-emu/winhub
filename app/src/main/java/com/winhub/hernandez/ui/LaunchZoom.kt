package com.winhub.hernandez.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.ui.screens.DECK_LAUNCH_ZOOM_MS
import com.winhub.hernandez.ui.screens.DeckEase
import kotlinx.coroutines.launch

/**
 * The launch zoom for things that are not a Home cover (a container run from the Storage tab): a sheet of
 * the mark's blue grows from [LaunchZoom.run]'s rectangle to the whole screen, the same way Home's cover
 * does, and stays until the Deck is back in front. Host [LaunchZoomOverlay] once, above the Deck's screens.
 */
internal object LaunchZoom {
    /** Window pixels of what was pressed; null = idle. */
    var origin by mutableStateOf<Rect?>(null)
        private set
    val t = Animatable(0f)

    /** Grows the sheet from [from] to the full screen; returns when it covers it. */
    suspend fun run(from: Rect) {
        origin = from
        t.snapTo(0f)
        t.animateTo(1f, tween(DECK_LAUNCH_ZOOM_MS, easing = DeckEase))
    }

    /** Lifts the sheet (the game's window closed and the Deck is back). */
    suspend fun release() {
        if (origin == null) return
        t.animateTo(0f, tween(220, easing = DeckEase))
        origin = null
    }
}

@Composable
internal fun LaunchZoomOverlay() {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        // Coming back from the game resumes the Deck: lift the zoom.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { LaunchZoom.release() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val from = LaunchZoom.origin ?: return
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Eats every touch so nothing underneath reacts while the game starts.
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { offset = it.positionInWindow() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = LaunchZoom.t.value
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = lerp(from.width / size.width, 1f, t)
                    scaleY = lerp(from.height / size.height, 1f, t)
                    translationX = lerp(from.left - offset.x, 0f, t)
                    translationY = lerp(from.top - offset.y, 0f, t)
                    alpha = (t * 6f).coerceIn(0f, 1f)
                    // The layer is stretched unevenly; undo it on average so the corners stay round.
                    shape = RoundedCornerShape((4.dp.toPx() * (1f - t) / ((scaleX + scaleY) / 2f)).coerceAtLeast(0f))
                    clip = true
                }
                .background(WinHubBlue),
        )
    }
}
