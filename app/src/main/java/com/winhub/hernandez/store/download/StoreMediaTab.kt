package com.winhub.hernandez.store.download

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.winhub.hernandez.store.MediaPlayback
import com.winhub.hernandez.store.StoreNotice
import kotlin.math.abs

/**
 * The shared "Media" tab of the four store game-detail pages (Steam · GOG · Epic · Amazon) and the
 * store-only catalog page: a row of trailer posters, a row of screenshot thumbnails, a full-screen
 * swipe/zoom viewer for the screenshots and one playback handoff for the videos.
 *
 * The MODEL is the contract every store's fetcher fills in ([StoreMedia]); the TAB renders it and
 * owns the screenshot viewer. Video playback is delegated through [MediaTab]'s `onOpenVideo` so the
 * host page decides (every page routes it to [MediaPlayback.openVideo], which plays direct mp4/webm
 * in-app and hands YouTube ids to the store WebView with autoplay).
 *
 * Layout rules: thumbnails only in the strip, the full-size image only in the viewer; rows are
 * `LazyRow`s so they nest inside the scaffold's vertical scroll. The strip is full-bleed (16dp
 * content padding) rather than inside a card so a 300dp tile still fits in portrait and the next
 * tile peeks in either orientation.
 */

/** One screenshot: [thumb] for the strip, [full] for the viewer. Both absolute https URLs. */
data class MediaImage(val thumb: String, val full: String)

/** A trailer. [poster] is the tile image (null → a plain play tile), [title] the caption. */
sealed interface MediaVideo {
    val poster: String?
    val title: String

    /** A directly playable stream (mp4 / webm). Steam and Amazon. */
    data class Direct(val url: String, override val poster: String?, override val title: String) : MediaVideo

    /** A YouTube video by id. GOG (and Epic hero videos). */
    data class YouTube(val id: String, override val poster: String?, override val title: String) : MediaVideo
}

/** Everything a store published for one title. Screenshot lists are capped at [MAX_SCREENSHOTS]. */
data class StoreMedia(val screenshots: List<MediaImage>, val videos: List<MediaVideo>) {
    val isEmpty: Boolean get() = screenshots.isEmpty() && videos.isEmpty()

    /** Total count for the tab badge. */
    val count: Int get() = screenshots.size + videos.size

    companion object {
        val EMPTY = StoreMedia(emptyList(), emptyList())
        const val MAX_SCREENSHOTS = 24
    }
}

private val TILE_W = 300.dp
private val TILE_H = 169.dp

/**
 * The tab body. [media] null while [loading] → spinner; null/empty afterwards → a [StoreNotice]
 * naming [storeLabel]; otherwise the Videos row (when any) and the Screenshots row (when any).
 * The screenshot viewer is hosted here; its open state survives rotation (the detail activities
 * recreate on orientation change).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTab(
    media: StoreMedia?,
    loading: Boolean,
    storeLabel: String,
    onOpenVideo: (MediaVideo) -> Unit,
) {
    val context = LocalContext.current
    var viewerIndex by rememberSaveable { mutableStateOf(-1) }

    Column(modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
        when {
            media == null && loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            media == null || media.isEmpty -> StoreNotice(
                title = "No media",
                body = "$storeLabel published no videos or screenshots for this title.",
            )

            else -> {
                if (media.videos.isNotEmpty()) {
                    MediaRowTitle("Videos")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(media.videos, key = { i, v -> "v$i:${v.title}" }) { _, video ->
                            MediaTile(
                                image = video.poster,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onOpenVideo(video) },
                                    onLongClick = { MediaPlayback.openExternal(context, video) },
                                ),
                            ) {
                                // Centered play badge + a caption on a bottom fade.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("▶", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
                                        )
                                        .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 8.dp),
                                ) {
                                    Text(
                                        text = video.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                if (media.screenshots.isNotEmpty()) {
                    MediaRowTitle("Screenshots")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(media.screenshots, key = { i, s -> "s$i:${s.thumb}" }) { index, shot ->
                            MediaTile(
                                image = shot.thumb,
                                modifier = Modifier.clickable { viewerIndex = index },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    val shots = media?.screenshots.orEmpty()
    if (viewerIndex >= 0 && shots.isNotEmpty()) {
        ScreenshotViewer(
            images = shots,
            startIndex = viewerIndex.coerceIn(0, shots.size - 1),
            onDismiss = { viewerIndex = -1 },
        )
    }
}

/** Section caption for a strip — same typography as `StoreSection`'s title, at the page inset. */
@Composable
private fun MediaRowTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** One 300x169 rounded tile with an optional overlay (the play badge / caption). */
@Composable
private fun MediaTile(
    image: String?,
    modifier: Modifier = Modifier,
    overlay: @Composable (BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .width(TILE_W)
            .height(TILE_H)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(modifier),
    ) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (overlay != null) overlay()
    }
}

// ── Screenshot viewer ─────────────────────────────────────────────────────────────────────────

/**
 * Full-screen screenshot viewer: a [HorizontalPager] over the full-size images with pinch zoom,
 * pan while zoomed, double-tap 1x ↔ 2.5x and a "3 / 12" counter. At 1x a single-finger drag is
 * left to the pager (swipe between shots); pinch or a drag while zoomed is taken by the page.
 * The dialog window is stretched to the whole screen and the system bars are hidden, so the pager
 * fills the display in portrait and landscape alike.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScreenshotViewer(
    images: List<MediaImage>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    if (images.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FullScreenDialogWindow()
        val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, images.size - 1)) { images.size }
        var currentZoomed by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage) { currentZoomed = false }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 1,
                userScrollEnabled = !currentZoomed,
                key = { images[it].full },
            ) { page ->
                ZoomableImage(
                    url = images[page].full,
                    onZoomedChanged = { zoomed -> if (page == pagerState.currentPage) currentZoomed = zoomed },
                )
            }

            // Chrome: close (top-right) + counter (bottom-center), inset from the cutout/bars.
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
                if (images.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${images.size}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** Stretch the hosting dialog window to the full display and hide the system bars. */
@Composable
private fun FullScreenDialogWindow() {
    val view = LocalView.current
    SideEffect {
        runCatching {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@runCatching
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** One pager page: the full image, fit to the page, with zoom / pan / double-tap. */
@Composable
private fun ZoomableImage(url: String, onZoomedChanged: (Boolean) -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var boxW by remember { mutableIntStateOf(0) }
    var boxH by remember { mutableIntStateOf(0) }

    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = (boxW * (s - 1f)) / 2f
        val maxY = (boxH * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun applyTransform(newScale: Float, newOffset: Offset) {
        val s = newScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = s
        offset = if (s <= MIN_ZOOM) Offset.Zero else clampOffset(newOffset, s)
        onZoomedChanged(s > MIN_ZOOM)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxW = it.width; boxH = it.height }
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > MIN_ZOOM) {
                            applyTransform(MIN_ZOOM, Offset.Zero)
                        } else {
                            val center = Offset(boxW / 2f, boxH / 2f)
                            applyTransform(DOUBLE_TAP_ZOOM, (center - tap) * (DOUBLE_TAP_ZOOM - 1f))
                        }
                    },
                )
            }
            .pointerInput(url) {
                detectZoomPan(isZoomed = { scale > MIN_ZOOM }) { centroid, pan, zoom ->
                    val newScale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val center = Offset(boxW / 2f, boxH / 2f)
                    // Keep the point under the fingers fixed while zooming, then add the pan.
                    val effective = newScale / scale
                    val newOffset = (offset - (centroid - center)) * effective + (centroid - center) + pan
                    applyTransform(newScale, newOffset)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/**
 * Pinch-zoom + pan detector that stays out of the pager's way: a single finger at 1x is NOT
 * consumed (the pager swipes); two fingers, or one finger while [isZoomed], drive [onGesture]
 * (centroid, pan, zoom) and consume the moves. Gives up when another detector consumed the event.
 */
private suspend fun PointerInputScope.detectZoomPan(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoomAcc = 1f
        var panAcc = Offset.Zero
        var pastSlop = false
        val slop = viewConfiguration.touchSlop
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed == 0) break
            if (event.changes.any { it.isConsumed }) break
            val multi = pressed > 1
            if (!multi && !isZoomed()) continue
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (!pastSlop) {
                zoomAcc *= zoom
                panAcc += pan
                val zoomMotion = abs(1f - zoomAcc) * event.calculateCentroidSize(useCurrent = false)
                if (zoomMotion > slop || panAcc.getDistance() > slop) pastSlop = true
            }
            if (pastSlop) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (zoom != 1f || pan != Offset.Zero) onGesture(centroid, pan, zoom)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        }
    }
}
