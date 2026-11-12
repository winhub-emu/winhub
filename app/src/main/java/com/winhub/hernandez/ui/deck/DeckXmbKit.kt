package com.winhub.hernandez.ui.deck

import android.content.Context
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.Build
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.ui.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

// Shared pieces of the XMB interface style (Deck with the XMB home), for the Deck shell and for the store and download
// screens, which are activities of their own.

/** True while the stores run in the XMB store style (Settings > Store style). Read from the preferences directly: a store screen can be the first one in its process. */
internal fun isXmbHome(context: Context): Boolean = StoreStyle.isXmb(context)

/** [isXmbHome] for a screen, read once when it opens (the setting can't change while a store screen is up). */
@Composable
internal fun rememberXmbHome(): Boolean {
    val context = LocalContext.current
    return remember(context) { isXmbHome(context) }
}

// Decoded drawables kept for the process: the XMB home draws the store logos (large PNGs) on every visit, and decoding them
// with painterResource did that on the main thread each time Home opened.
private val xmbLogoCache = ConcurrentHashMap<Int, ImageBitmap>()

/** The longest side (px) a logo is decoded at: plenty for a card, well under the 960 px files. */
private const val XMB_LOGO_EDGE = 512

private fun decodeXmbLogo(resources: Resources, res: Int): ImageBitmap? = xmbLogoCache[res] ?: run {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeResource(resources, res, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= XMB_LOGO_EDGE) sample *= 2
    BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()?.also { xmbLogoCache[res] = it }
}

/**
 * [res] decoded off the main thread at no more than [XMB_LOGO_EDGE] px on its long side. Null until it is ready, and ready at
 * once on every later visit.
 */
@Composable
internal fun rememberXmbLogo(@DrawableRes res: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    return produceState<ImageBitmap?>(xmbLogoCache[res], res) {
        if (value == null) value = withContext(Dispatchers.IO) { decodeXmbLogo(resources, res) }
    }.value
}

/** Decodes [res] ahead of time so the XMB home's first frame finds its logos ready. */
internal suspend fun warmXmbLogos(context: Context, res: List<Int>) {
    val resources = context.resources
    withContext(Dispatchers.IO) { res.forEach { decodeXmbLogo(resources, it) } }
}

@Composable
internal fun rememberScreenScale(): ScreenScale {
    val cfg = LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp) { ScreenScale(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat()) }
}

/**
 * XMB-home window for a store or download screen, the way the Deck shell sets its own: laid out edge to edge and under
 * the camera hole, with the status and navigation bars hidden (a swipe in from the edge shows them for a moment),
 * re-hidden whenever the screen resumes and put back when it closes. Pair it with [xmbFrame] for the content.
 */
@Composable
internal fun XmbScreenWindow(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(enabled, activity, lifecycleOwner) {
        val window = activity?.window
        if (!enabled || window == null) return@DisposableEffect onDispose {}
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val cutoutBefore = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val hide = {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        hide()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hide() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.also { it.layoutInDisplayCutoutMode = cutoutBefore }
            }
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}

/**
 * The XMB-home frame: as wide as the camera strip, kept on both sides so a page sits evenly between the edges. Content
 * pads by it horizontally; backgrounds and bars run to the edges.
 */
@Composable
internal fun xmbFrame(): Dp {
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    val dir = LocalLayoutDirection.current
    return maxOf(cutout.calculateStartPadding(dir), cutout.calculateEndPadding(dir))
}

/**
 * Tap-to-edit text fields (XMB home). D-pad focus on a search bar only highlights it; A / Enter / DPad-centre or a tap
 * starts typing (keyboard up), and the keyboard's own action, Back or B ends it and leaves focus on the bar's outer
 * stop, so passing over a bar never opens the keyboard. This is the one implementation: [XmbTapToEdit] wraps an
 * ordinary field, and the store search bar builds on the same pieces ([xmbEditStop], [xmbEditField], [XmbEditTapCatcher]).
 */
@Stable
internal class XmbEditState(private val keyboard: SoftwareKeyboardController?) {
    var editing by mutableStateOf(false)
    var lit by mutableStateOf(false)
    private var fieldFocused = false
    val outer = FocusRequester()
    val field = FocusRequester()

    /** The bar is highlighted: focused, or being typed into. */
    val active: Boolean get() = lit || editing

    fun start() {
        editing = true
    }

    fun stop() {
        editing = false
        keyboard?.hide()
        runCatching { outer.requestFocus() }
    }

    /** Leaving the text field (a tap elsewhere) ends typing; the first report (not focused yet) does not. */
    fun onFieldFocus(focused: Boolean) {
        if (fieldFocused && !focused) editing = false
        fieldFocused = focused
    }
}

@Composable
internal fun rememberXmbEdit(): XmbEditState {
    val keyboard = LocalSoftwareKeyboardController.current
    val state = remember(keyboard) { XmbEditState(keyboard) }
    // The XMB keyboard is a window of its own, so its B / Done never reach the bar's key handler: when it closes, typing ends here.
    val xmbKeyboard = keyboard as? XmbKeyboardState
    if (xmbKeyboard != null) {
        LaunchedEffect(state, xmbKeyboard) {
            var wasActive = false
            snapshotFlow { xmbKeyboard.active }.collect { active ->
                if (wasActive && !active && state.editing) state.stop()
                wasActive = active
            }
        }
    }
    LaunchedEffect(state.editing) {
        if (state.editing) {
            withFrameNanos { }
            runCatching { state.field.requestFocus() }
            keyboard?.show()
        }
    }
    return state
}

/** The bar's outer focus stop: A starts typing, B / Back ends it, and its focus lights the bar. Put it before the bar's clip / background. */
internal fun Modifier.xmbEditStop(e: XmbEditState): Modifier = this
    .onPreviewKeyEvent { k ->
        when {
            !e.editing && k.type == KeyEventType.KeyDown &&
                (k.key == Key.ButtonA || k.key == Key.Enter || k.key == Key.NumPadEnter || k.key == Key.DirectionCenter) -> {
                e.start(); true
            }
            e.editing && (k.key == Key.Back || k.key == Key.ButtonB || k.key == Key.Escape) -> {
                if (k.type == KeyEventType.KeyUp) e.stop()
                true
            }
            else -> false
        }
    }
    .onFocusChanged { e.lit = it.hasFocus }
    .focusRequester(e.outer)

/** The text field itself: it takes focus only while typing. */
internal fun Modifier.xmbEditField(e: XmbEditState): Modifier = this
    .onFocusChanged { e.onFieldFocus(it.isFocused) }
    .focusRequester(e.field)
    .focusProperties { canFocus = e.editing }

/** Not typing: the text field can't take focus, so a tap on it is caught here and starts typing. */
@Composable
internal fun BoxScope.XmbEditTapCatcher(e: XmbEditState) {
    if (!e.editing) {
        Box(
            Modifier
                .matchParentSize()
                .focusProperties { canFocus = false }
                .clickable { e.start() },
        )
    }
}

/**
 * A tap-to-edit field's keyboard actions: the keyboard's Done ends typing in the XMB home, and everywhere else the field
 * keeps its default action. Call it inside the [XmbTapToEdit] content, with the [stopEditing] it was handed.
 */
@Composable
internal fun xmbDoneActions(stopEditing: () -> Unit): KeyboardActions =
    if (rememberXmbHome()) KeyboardActions(onDone = { stopEditing() }) else KeyboardActions.Default

/**
 * Wraps an ordinary text field so it is tap-to-edit while the XMB home is on, and passes it straight through otherwise.
 * Give [modifier] the field's whole modifier; [content] uses [fieldModifier] as the field's modifier (outside the XMB
 * home that is [modifier] itself, so the field is exactly as before) and calls [stopEditing] when the field's own action
 * (Search / Done) should end typing. The wrapper draws the accent ring when the field is focused or being typed into.
 * Pass an [edit] state of your own to start typing from code (see [XmbEditState.start]); without one, a state is made
 * only while the XMB home is on, so the other interfaces compose nothing extra.
 */
@Composable
internal fun XmbTapToEdit(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    edit: XmbEditState? = null,
    content: @Composable (fieldModifier: Modifier, stopEditing: () -> Unit) -> Unit,
) {
    if (!rememberXmbHome()) {
        content(modifier) {}
        return
    }
    val state = edit ?: rememberXmbEdit()
    Box(
        modifier
            .xmbEditStop(state)
            .clip(shape)
            .clickable { state.start() }
            .then(if (state.active) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
    ) {
        content(Modifier.fillMaxWidth().xmbEditField(state)) { state.stop() }
        XmbEditTapCatcher(state)
    }
}
