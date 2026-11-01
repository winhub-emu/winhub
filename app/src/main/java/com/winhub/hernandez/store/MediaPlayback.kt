package com.winhub.hernandez.store

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.winhub.hernandez.store.download.MediaVideo

/**
 * The ONE video handoff every store's Media tab uses, so a trailer behaves the same on the Steam,
 * GOG, Epic and Amazon pages:
 *
 *  - [MediaVideo.Direct] (mp4 / webm) → [MediaVideoActivity], a full-screen in-app player
 *    (`VideoView` + `MediaController`, 16:9 letterbox, sensor orientation, playback survives
 *    rotation). A stream the platform decoder refuses falls through to [openExternal].
 *  - [MediaVideo.YouTube] → the store WebView ([StoreWebActivity]) on the embed URL with autoplay
 *    allowed. A long-press on the tile goes to [openExternal] (the YouTube app / browser).
 */
object MediaPlayback {

    /** Play [video] from the page that owns [ctx] (an Activity). */
    fun openVideo(ctx: Context, video: MediaVideo) {
        when (video) {
            is MediaVideo.Direct -> runCatching {
                ctx.startActivity(MediaVideoActivity.intent(ctx, video.url, video.title))
            }.onFailure { openExternal(ctx, video) }

            is MediaVideo.YouTube -> runCatching {
                ctx.startActivity(
                    StoreWebActivity.intent(
                        ctx,
                        "https://www.youtube.com/embed/${video.id}?autoplay=1&playsinline=1&rel=0",
                        video.title,
                        allowAutoplay = true,
                    ),
                )
            }.onFailure { openExternal(ctx, video) }
        }
    }

    /** Hand [video] to another app: the YouTube app (falling back to the browser) or a video player. */
    fun openExternal(ctx: Context, video: MediaVideo) {
        val intents = when (video) {
            is MediaVideo.YouTube -> listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${video.id}")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}")),
            )
            is MediaVideo.Direct -> listOf(
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(video.url), mimeOf(video.url)),
                Intent(Intent.ACTION_VIEW, Uri.parse(video.url)),
            )
        }
        for (i in intents) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return
        }
    }

    private fun mimeOf(url: String): String =
        if (url.substringBefore('?').endsWith(".webm", ignoreCase = true)) "video/webm" else "video/mp4"
}

/**
 * Full-screen trailer player for direct streams. Black stage, the video letterboxed at its own
 * aspect (16:9 for every store trailer), the platform `MediaController` (tap the video to toggle
 * it) and a small close/title bar over the top edge. Declared `sensor` + `configChanges` so a
 * rotation re-lays-out the same `VideoView` instead of re-buffering; the system bars are hidden
 * (swipe to peek). Any decoder error hands the URL to an external player and closes.
 */
class MediaVideoActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val STATE_POSITION = "position"

        fun intent(ctx: Context, url: String, title: String): Intent =
            Intent(ctx, MediaVideoActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }

    private var videoView: VideoView? = null
    private var resumePositionMs = 0
    private var wasPlaying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (url.isBlank()) { finish(); return }
        resumePositionMs = savedInstanceState?.getInt(STATE_POSITION, 0) ?: 0

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        setContent {
            var buffering by remember { mutableStateOf(true) }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AndroidView(
                    // 16:9 box that fits the width in portrait and the height in landscape; the
                    // VideoView letterboxes any other aspect inside it.
                    modifier = Modifier.aspectRatio(16f / 9f),
                    factory = { context ->
                        VideoView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            val controller = MediaController(context)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnPreparedListener { mp ->
                                buffering = false
                                if (resumePositionMs > 0) mp.seekTo(resumePositionMs)
                                if (wasPlaying) start()
                            }
                            setOnInfoListener { _, what, _ ->
                                when (what) {
                                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
                                    MediaPlayer.MEDIA_INFO_BUFFERING_END,
                                    MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> buffering = false
                                }
                                false
                            }
                            setOnErrorListener { _, _, _ ->
                                buffering = false
                                MediaPlayback.openExternal(this@MediaVideoActivity, MediaVideo.Direct(url, null, title))
                                finish()
                                true
                            }
                            setOnCompletionListener { wasPlaying = false }
                            videoView = this
                            setVideoURI(Uri.parse(url))
                        }
                    },
                )
                if (buffering) {
                    CircularProgressIndicator(color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { finish() },
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { MediaPlayback.openExternal(this@MediaVideoActivity, MediaVideo.Direct(url, null, title)) },
                        modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open in another app", tint = Color.White)
                    }
                }
            }
        }
    }

    override fun onPause() {
        videoView?.let { vv ->
            runCatching {
                resumePositionMs = vv.currentPosition
                wasPlaying = vv.isPlaying
                vv.pause()
            }
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        videoView?.let { vv ->
            runCatching {
                if (resumePositionMs > 0) vv.seekTo(resumePositionMs)
                if (wasPlaying) vv.start()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POSITION, videoView?.let { runCatching { it.currentPosition }.getOrNull() } ?: resumePositionMs)
    }

    override fun onDestroy() {
        videoView?.let { runCatching { it.stopPlayback() } }
        videoView = null
        super.onDestroy()
    }
}
