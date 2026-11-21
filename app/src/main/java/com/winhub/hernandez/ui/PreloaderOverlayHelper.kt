package com.winhub.hernandez.ui

import android.view.MotionEvent
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.winhub.hernandez.core.PreloaderState
import com.winhub.hernandez.ui.theme.WinlatorTheme

/**
 * Attaches a full-screen Compose PreloaderOverlay to the decor view of any AppCompatActivity.
 * Call this once from onCreate() after setContentView().
 * Works in both Compose-hosted and traditional View-based activities.
 *
 * The overlay is added as the LAST child of the decor view, so it draws above everything the
 * activity laid out below it (the X server surface, the on-screen controls, the HUD). Two things
 * keep that cover complete until the loading screen is dismissed:
 *   - the overlay's own backdrop is fully opaque (see XmbWaveBackdrop);
 *   - touches are eaten here while a status is on screen, because a Compose view only consumes
 *     what its own children handle — an unhandled tap would otherwise fall through this sibling
 *     to the container surface and the virtual controls underneath. When nothing is showing the
 *     view stays touch-transparent, so the running session below is never blocked.
 */
object PreloaderOverlayHelper {

    /** Int extra: the theme colour Home's launch zoom ended on; the loading screen opens from it. */
    const val EXTRA_ZOOM_COLOR = "launch_zoom_color"

    @JvmStatic
    fun attach(activity: AppCompatActivity) {
        // androidx.compose.ui.platform.ComposeView is FINAL, so it cannot be subclassed to
        // swallow unhandled touches. The touch sink therefore lives on a thin FrameLayout host
        // that wraps the Compose content view: the host dispatches to the overlay child first
        // and only then falls back to "a status is on screen, so eat the event anyway".
        val overlay = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WinlatorTheme {
                    PreloaderOverlay()
                }
            }
        }
        val host = object : FrameLayout(activity) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                val handled = super.dispatchTouchEvent(event)
                return handled || PreloaderState.isVisible()
            }
        }
        val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
        host.addView(overlay, FrameLayout.LayoutParams(matchParent, matchParent))
        val params = FrameLayout.LayoutParams(matchParent, matchParent)
        val decor = activity.window.decorView as ViewGroup
        decor.addView(host, params)
        if (activity.intent?.hasExtra(EXTRA_ZOOM_COLOR) == true) {
            beginReveal(activity, activity.intent.getIntExtra(EXTRA_ZOOM_COLOR, 0))
        }
    }

    /**
     * The launch zoom ended on [color]: keep that colour up from the first frame (the window's own background
     * too, so there is no black flash), and let the loading screen turn it into the mark's dots (PreloaderOverlay).
     * The state is cleared when the reveal ends or the activity goes, so no later launch inherits a cover.
     */
    private fun beginReveal(activity: AppCompatActivity, color: Int) {
        val previousBackground = activity.window.decorView.background
        activity.window.setBackgroundDrawable(ColorDrawable(color))
        PreloaderState.onRevealEnd = Runnable {
            if (previousBackground != null) activity.window.setBackgroundDrawable(previousBackground)
        }
        PreloaderState.beginReveal(color)
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                PreloaderState.endReveal()
            }
        })
    }
}
