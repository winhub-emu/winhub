package com.winhub.hernandez.ui

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
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

    private const val REVEAL_DELAY_MS = 60L
    private const val REVEAL_MS = 340L

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
            playReveal(activity, decor, activity.intent.getIntExtra(EXTRA_ZOOM_COLOR, 0))
        }
    }

    /** A full-screen sheet of the zoom colour that shrinks away, uncovering the loading screen beneath it. */
    private fun playReveal(activity: AppCompatActivity, decor: ViewGroup, color: Int) {
        // The window's own background is what shows before the first frame: make it the zoom colour too, so there is no black flash.
        val previousBackground = decor.background
        activity.window.setBackgroundDrawable(ColorDrawable(color))
        val sheet = View(decor.context).apply {
            setBackgroundColor(color)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        decor.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        sheet.post {
            sheet.pivotX = sheet.width / 2f
            sheet.pivotY = sheet.height / 2f
            sheet.animate()
                .scaleX(0f).scaleY(0f)
                .setStartDelay(REVEAL_DELAY_MS)
                .setDuration(REVEAL_MS)
                .setInterpolator(PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
                .withEndAction {
                    decor.removeView(sheet)
                    if (previousBackground != null) activity.window.setBackgroundDrawable(previousBackground)
                }
                .start()
        }
    }
}
