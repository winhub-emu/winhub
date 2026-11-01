package com.winhub.hernandez.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.R
import kotlinx.coroutines.delay

/**
 * A purple, top-right Compose banner that replaces a Toast for the WinHub update check.
 *
 * The banner is NOT an overlay window: it is attached to whatever Activity is currently resumed
 * (so no `SYSTEM_ALERT_WINDOW` permission and no window-leak risk) and torn down again when the
 * dismiss animation finishes or the Activity goes away. Because [show] can be reached before the
 * first Activity exists — the ping starts in `Application.onCreate` — a message that arrives too
 * early is parked in [pending] and posted the moment something resumes.
 *
 * All mutation happens on the main looper; [show] itself is safe from any thread.
 */
object ComposeNotifier : Application.ActivityLifecycleCallbacks {

    private const val VIEW_TAG = "winhub_compose_notice"
    private const val AUTO_DISMISS_MS = 3_600L
    private const val EXIT_ANIM_MS = 260

    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumed: Activity? = null
    private var installed = false
    private var pending: String? = null

    /** Hard one-per-process limit: the first banner to actually reach the screen wins. */
    @Volatile
    private var shownThisSession = false

    /**
     * Register the lifecycle callbacks. Must run before the first Activity resumes — the ping
     * finishes while `MainActivity` is still starting up, and a late registration would never see
     * that resume, so the message would sit in [pending] forever. Safe to call repeatedly.
     */
    fun install(app: Application) {
        if (installed) return
        app.registerActivityLifecycleCallbacks(this)
        installed = true
    }

    /**
     * Show [message] as a top-right banner. Safe from any thread; no-ops without an Application.
     * Only the FIRST call in a process ever draws — everything after it is dropped, so the update
     * check, "Settings saved!" and the updater results cannot stack over one another.
     */
    @JvmStatic
    fun show(context: Context?, message: String) {
        if (shownThisSession) return
        val app = context?.applicationContext as? Application ?: return
        mainHandler.post {
            if (shownThisSession) return@post
            install(app)
            val host = resumed
            if (host != null && !host.isFinishing && !host.isDestroyed) showOn(host, message)
            else pending = message
        }
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = activity
        pending?.let { message ->
            pending = null
            if (!activity.isFinishing && !activity.isDestroyed) showOn(activity, message)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed === activity) resumed = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed === activity) resumed = null
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        removeExisting(content)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun showOn(host: Activity, message: String) {
        if (shownThisSession) return
        val content = host.findViewById<FrameLayout>(android.R.id.content) ?: return
        shownThisSession = true
        // One banner at a time: a newer message replaces whatever is still on screen.
        removeExisting(content)

        val view = ComposeView(host)
        view.tag = VIEW_TAG
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent { WinhubNotice(message = message) { content.removeView(view) } }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        )
        content.addView(view, params)
    }

    private fun removeExisting(content: FrameLayout) {
        for (i in content.childCount - 1 downTo 0) {
            if (content.getChildAt(i)?.tag == VIEW_TAG) content.removeViewAt(i)
        }
    }

    @Composable
    private fun WinhubNotice(message: String, onGone: () -> Unit) {
        var shown by remember { mutableStateOf(false) }
        // Gate so the exit branch never runs before the banner has actually been shown.
        var started by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            started = true
            shown = true
        }

        // One effect drives both halves: auto-dismiss on the way in, tap-to-dismiss on the way out.
        LaunchedEffect(shown) {
            if (!started) return@LaunchedEffect
            if (shown) {
                delay(AUTO_DISMISS_MS)
                shown = false
            } else {
                delay(EXIT_ANIM_MS.toLong())
                onGone()
            }
        }

        // Top-right gutter: clear of the status bar (0 when the decor already fits it) then 14dp.
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 14.dp, top = 14.dp, end = 14.dp)
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = slideInHorizontally(tween(340)) { it } + fadeIn(tween(300)),
                exit = slideOutHorizontally(tween(EXIT_ANIM_MS)) { it } + fadeOut(tween(EXIT_ANIM_MS)),
            ) {
                NoticeCard(message = message, onClick = { shown = false })
            }
        }
    }

    @Composable
    private fun NoticeCard(message: String, onClick: () -> Unit) {
        val shape = RoundedCornerShape(22.dp)
        val accent = com.winhub.hernandez.ui.theme.AppThemeState.deckPreset.primary

        Column(
            modifier = Modifier
                .width(316.dp)
                .shadow(20.dp, shape, clip = false, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF2E7DE0),
                            Color(0xFF1A5FBF),
                            Color(0xFF0A2A52),
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))
                    ),
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            // Hairline catch-light along the top edge — the "glass" tell on a flat gradient.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f))
            )
            Spacer(Modifier.size(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.winhub_ping_notice_kicker),
                        color = Color.White.copy(alpha = 0.74f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.6.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp,
                        maxLines = 3,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.winhub_ping_notice_sub),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
