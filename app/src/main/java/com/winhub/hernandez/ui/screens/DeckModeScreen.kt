package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.winhub.hernandez.core.AppOrientation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.key.KeyEvent
import com.winhub.hernandez.store.download.DownloadRegistry
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlinx.coroutines.Job
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Delete
import androidx.preference.PreferenceManager
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import android.widget.VideoView

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.winhub.hernandez.ui.moveFocusSafely
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.winhub.hernandez.R
import com.winhub.hernandez.XServerDisplayActivity
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.ui.deck.XmbKeyboardHost
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.GameIdentifier
import com.winhub.hernandez.store.SaveManagerScreen
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.deckTileFocus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.winhub.hernandez.ui.moveFocusOrStep
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.io.FileOutputStream
import com.winhub.hernandez.ui.ControllerFocusIndication
import com.winhub.hernandez.ui.SocialHubScreen
import com.winhub.hernandez.ui.theme.AppThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Deck shell palette. Delegates to [DeckPalette] (which is backed by the live theme preset),
 * so the shell, its hosted screens and its popups all recolor together. [attention] stays a fixed
 * amber warning semantic in every theme.
 */
internal object Deck {
    val background get() = DeckPalette.background
    val surface get() = DeckPalette.surface
    val surfaceVariant get() = DeckPalette.surfaceVariant
    val line get() = DeckPalette.line
    val onBackground get() = DeckPalette.onBackground
    val onSurfaceVariant get() = DeckPalette.onSurfaceVariant
    val primary get() = DeckPalette.primary
    val onPrimary get() = DeckPalette.onPrimary
    val secondary get() = DeckPalette.secondary
    val onSecondary get() = DeckPalette.onSecondary
    val attention = Color(0xFFFFB547)
}

/** One tile on the wall: a name (drives its colour), the resolved hue, and its cover art. */
internal class DeckCover(val name: String, val hue: Float, val bitmap: Bitmap? = null)

internal fun hueOf(name: String): Float {
    var h = 0
    for (c in name) h = 31 * h + c.code
    return ((h % 360) + 360) % 360 / 360f
}

/** The rail's real sections — the app's own destinations, in the DroidDeck rail style. */
private class DeckSection(val key: String, val label: String, val icon: ImageVector)

private fun deckSections() = listOf(
    DeckSection("home", "Home", Icons.Filled.Apps),
    DeckSection("containers", "Storage", Icons.Filled.Folder),
    DeckSection("stores", "Stores", Icons.Filled.Storefront),
    DeckSection("file_manager", "Files", Icons.Filled.FolderOpen),
    DeckSection("input_controls", "Controls", Icons.Filled.SportsEsports),
    DeckSection("contents", "Contents", Icons.Filled.Inventory2),
    DeckSection("saves", "Saves", Icons.Filled.Save),
    DeckSection("social_hub", "Social", Icons.Filled.People),
    DeckSection("settings", "Settings", Icons.Filled.Settings),
)

/**
 * Deck Mode — DroidDeck's front end, full-bleed (MainActivity drops the top bar on the `deck`
 * route). The rail lists this app's own sections; each one hosts the REAL screen, so Deck Mode
 * is a shell over working functionality rather than a mock.
 */
@Composable
internal fun DeckModeScreen() {
    val context = LocalContext.current
    // Portrait app-orientation pref: touch-first layout with every tab in a bottom bar.
    val portrait = remember { AppOrientation.mode(context) == AppOrientation.PORTRAIT }
    // Android flips the whole app to touch mode on every screen tap. Compose's `clickable` is
    // non-touch focusable, so that silently drops every shelf card / list row out of the D-pad
    // focus tree until the next hardware key — the hero survives only because it forces
    // canFocus. Deck Mode is controller-first, so pin Compose's input mode to Keyboard: touch
    // still works as before, focus never disappears.
    val platformInputMode = LocalInputModeManager.current
    val deckInputMode = remember(platformInputMode) { DeckInputModeManager(platformInputMode) }
    var introDone by remember { mutableStateOf(DeckBoot.shown) }
    var section by remember { mutableStateOf("home") }
    var graphicPane by remember { mutableStateOf("gpu") }
    var deckDetail by remember { mutableStateOf<Int?>(null) }
    // General tab style: "horizontal" (top strip, default) or "vertical" (left rail).
    val tabStyle by AppThemeState.generalTabStyle.collectAsState()
    val horizontalRail = portrait || tabStyle != "vertical"
    val railSections = remember { deckSections() }

    // Controller-first back stack: B (or system Back) closes the container editor and nothing more. It never
    // jumps to the Home tab and never closes the app or drops to Android's launcher (the BackHandler below
    // swallows the system Back, so the activity is never finished); tabs change with L1/R1.
    val goBack: () -> Unit = {
        if (deckDetail != null) deckDetail = null
    }
    BackHandler { goBack() }

    // The pump sits on the current rail item the moment the New UI opens, so the controller starts
    // on the side bar — then Right/A drops into the pane and the tab drags in behind it.
    val railFocus = remember { FocusRequester() }
    // Right from the Home rail item must land on the hero. The "+" Add button shares the rail's
    // Y band, so the geometric search picked it first and the banner looked unreachable.
    val homeHeroFocus = remember { FocusRequester() }
    val tabs = remember { DeckTabHost() }
    // Top-strip style: the strip is not focusable, so after a section switch park focus on the
    // page's first control (unless the page already took focus itself).
    val focusManager = LocalFocusManager.current
    var contentFocused by remember { mutableStateOf(false) }
    // After a page swap the focused control is gone and the shell itself holds focus, so the D-pad and A do nothing
    // until something is pressed twice. Hand focus straight to the page instead.
    var shellOnly by remember { mutableStateOf(false) }
    LaunchedEffect(shellOnly, section, introDone) {
        if (!shellOnly || !introDone) return@LaunchedEffect
        repeat(8) {
            delay(120)
            if (!shellOnly || DeckConsole.boot || DeckConsole.screensaver) return@LaunchedEffect
            if (section == "home") runCatching { homeHeroFocus.requestFocus() }
            else focusManager.moveFocusSafely(FocusDirection.Next)
        }
    }
    LaunchedEffect(section, horizontalRail) {
        if (!horizontalRail) return@LaunchedEffect
        delay(450)
        if (!contentFocused) focusManager.moveFocusSafely(FocusDirection.Next)
    }
    val backLayer = remember { DeckBackLayer() }
    val editTracker = remember { DeckEditTracker() }
    // L1/R1 (sections) and L2/R2 (sub-tabs) are taken at the Activity, so they work whether or not
    // anything holds Compose focus - no "press A first to lock focus" step.
    val sectionNow by rememberUpdatedState(section)
    DisposableEffect(Unit) {
        DeckHardKeys.handler = handler@{ code ->
            if (DeckConsole.boot) return@handler false
            if (DeckConsole.screensaver) {
                DeckConsole.poke()
                return@handler true
            }
            DeckConsole.poke()
            DeckTouchMode.touch = false
            if (editTracker.editing) return@handler false
            if (code == android.view.KeyEvent.KEYCODE_BUTTON_L1 || code == android.view.KeyEvent.KEYCODE_BUTTON_R1) DeckSound.play(DeckSound.MOVE)
            when (code) {
                android.view.KeyEvent.KEYCODE_BUTTON_L1 -> { stepSection(railSections, sectionNow, -1) { section = it; deckDetail = null }; true }
                android.view.KeyEvent.KEYCODE_BUTTON_R1 -> { stepSection(railSections, sectionNow, 1) { section = it; deckDetail = null }; true }
                android.view.KeyEvent.KEYCODE_BUTTON_START -> { DeckConsole.quickMenu = true; true }
                android.view.KeyEvent.KEYCODE_BUTTON_L2 -> if (tabs.count > 1) { tabs.step(-1); true } else false
                android.view.KeyEvent.KEYCODE_BUTTON_R2 -> if (tabs.count > 1) { tabs.step(1); true } else false
                else -> false
            }
        }
        onDispose { DeckHardKeys.handler = null }
    }
    // Tappable equivalents of the pad buttons for the hint bar.
    val hintBack: () -> Unit = {
        if (backLayer.onBack?.invoke() != true && tabs.onBack?.invoke() != true) goBack()
    }
    val hintStep: (Int) -> Unit = { d -> stepSection(railSections, sectionNow, d) { section = it; deckDetail = null } }
    // Clear the previous page's sub-tabs as the page changes. A SideEffect runs before the new page's own
    // LaunchedEffect bind, so the new page's tabs can never be wiped by this reset arriving late.
    val boundFor = remember { arrayOfNulls<Any>(1) }
    SideEffect {
        val key = section to deckDetail
        if (boundFor[0] != key) { boundFor[0] = key; tabs.bind(0) }
    }
    // Park on the rail ONCE when the shell opens. Keying this on `section` re-parked the rail on
    // every section change, including changes triggered from inside the content, which is what
    // yanked the D-pad back to the sidebar and made the player redo the walk.
    LaunchedEffect(introDone) {
        if (introDone) runCatching { railFocus.requestFocus() }
    }
    // Back from a detail re-parks focus on the rail item we came from. Opening a detail parks
    // focus on its own Back button (see DeckContainerDetailScreen), never on an invisible node.
    LaunchedEffect(deckDetail) {
        if (deckDetail == null && introDone) {
            // Wait one frame: the closing detail is still animating out and owns focus, so an
            // immediate request can be dropped and the D-pad is left with nowhere to go.
            withFrameNanos { }
            runCatching { railFocus.requestFocus() }
        }
    }

    // Truly full screen: the system status/nav bars go with the top bar, restored on leaving.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previousOrientation = activity?.requestedOrientation
        if (!portrait) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null && !portrait) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            previousOrientation?.let { activity?.requestedOrientation = it }
        }
    }

    // DroidDeck's Graphite palette as the theme for everything hosted here, so no Amethyst
    // purple reaches a hosted screen — and the focus outline follows the same blue.
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Deck.primary,
            onPrimary = Deck.onPrimary,
            secondary = Deck.secondary,
            onSecondary = Deck.onSecondary,
            secondaryContainer = Deck.secondary.copy(alpha = 0.30f),
            onSecondaryContainer = Deck.onBackground,
            background = Deck.background,
            onBackground = Deck.onBackground,
            surface = Deck.surface,
            onSurface = Deck.onBackground,
            surfaceVariant = Deck.surfaceVariant,
            onSurfaceVariant = Deck.onSurfaceVariant,
            error = DeckPalette.error,
        ),
    ) {
        CompositionLocalProvider(
            LocalInputModeManager provides deckInputMode,
            LocalIndication provides remember(Deck.primary) { ControllerFocusIndication(Deck.primary) },
            LocalDeckTabs provides tabs,
            LocalDeckBack provides backLayer,
            LocalDeckEditTracker provides editTracker,
            LocalDeckMode provides true,
            LocalDeckPortrait provides portrait,
        ) {
    // Horizontal style puts the sections in a top strip (L1/R1 switch them); vertical keeps the
    // left rail. Both host the same pane content below/right of the nav.
    if (horizontalRail) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Deck.background)
                // Behind the bar too, so a see-through top bar shows the page glow.
                .drawBehind { drawDeckCornerGlow(Deck.primary) }
                // Portrait basic tabs: waves behind the page and the bottom bar; Home adds the drifting glows.
                .then(
                    if (portrait && section in liquidSections && !PortraitPageState.open &&
                        !(section == "containers" && deckDetail != null)) Modifier.liquidBackdrop(glows = section == "home") else Modifier
                )
                .then(if (portrait) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .deckTouchTracker()
                .onPreviewKeyEvent { e ->
                    // Portrait is touch-only: gamepad and D-pad keys do nothing here (landscape keeps both).
                    if (portrait && isPadKey(e.key)) return@onPreviewKeyEvent true
                    if (DeckConsole.boot) {
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.ButtonStart)) DeckConsole.boot = false
                        return@onPreviewKeyEvent true
                    }
                    if (DeckConsole.screensaver) { DeckConsole.poke(); return@onPreviewKeyEvent true }
                    if (e.type == KeyEventType.KeyDown) { DeckTouchMode.touch = false; DeckConsole.poke() }
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.ButtonB -> {
                            DeckSound.play(DeckSound.BACK)
                            if (editTracker.editing) false
                            else if (backLayer.onBack?.invoke() == true) true
                            else if (tabs.onBack?.invoke() == true) true
                            else { goBack(); true }
                        }
                        // L1/R1 switch the top-strip sections.
                        Key.ButtonL1 -> { stepSection(railSections, section, -1) { section = it; deckDetail = null }; true }
                        Key.ButtonR1 -> { stepSection(railSections, section, 1) { section = it; deckDetail = null }; true }
                        // L2/R2 switch the sub-tabs of the current page (Storage, editors, Contents).
                        Key.ButtonL2 -> if (tabs.count > 1) { tabs.step(-1); true } else false
                        Key.ButtonR2 -> if (tabs.count > 1) { tabs.step(1); true } else false
                        else -> false
                    }
                }
                .onFocusChanged { shellOnly = it.isFocused }
                .focusable(),
        ) {
            if (!portrait) DeckTopTabs(
                sections = railSections,
                selected = section,
                onSelect = { next -> section = next; deckDetail = null },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onFocusChanged { contentFocused = it.hasFocus }
                    .clipToBounds(),
            ) {
                DeckPaneContent(
                    section = section,
                    deckDetail = deckDetail,
                    graphicPane = graphicPane,
                    onEditContainer = { id -> section = "containers"; deckDetail = id },
                    onDetailBack = { deckDetail = null },
                    onDetailOpen = { id -> deckDetail = id },
                    onGraphicPane = { graphicPane = it },
                    homeFocus = homeHeroFocus,
                    portrait = portrait,
                )
                if (!portrait) DeckHintBar(Modifier.align(Alignment.BottomEnd), onBack = hintBack, onPrev = { hintStep(-1) }, onNext = { hintStep(1) }, subTabs = tabs.count > 1)
                DeckCornerGlow()
            }
            if (portrait) DeckBottomTabs(
                sections = railSections,
                selected = section,
                onSelect = { next -> section = next; deckDetail = null },
            )
        }
    } else {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background)
            .deckTouchTracker()
            .onPreviewKeyEvent { e ->
                if (DeckConsole.boot) {
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.ButtonA || e.key == Key.Enter || e.key == Key.DirectionCenter || e.key == Key.ButtonStart)) DeckConsole.boot = false
                    return@onPreviewKeyEvent true
                }
                if (DeckConsole.screensaver) { DeckConsole.poke(); return@onPreviewKeyEvent true }
                if (e.type == KeyEventType.KeyDown) { DeckTouchMode.touch = false; DeckConsole.poke() }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonB -> {
                        DeckSound.play(DeckSound.BACK)
                        // A text field that is taking input owns B first (it just closes the IME).
                        if (editTracker.editing) false
                        else if (backLayer.onBack?.invoke() == true) true
                        else if (tabs.onBack?.invoke() == true) true
                        else { goBack(); true }
                    }
                    // Shoulders walk the active tab strip when the page has tabs bound (container
                    // detail, Save Manager, Contents), so switching a category no longer means
                    // walking focus all the way Up to the strip first. Pages without tabs keep the
                    // old wide horizontal focus step.
                    Key.ButtonL1 -> { stepSection(railSections, section, -1) { section = it; deckDetail = null }; true }
                    Key.ButtonR1 -> { stepSection(railSections, section, 1) { section = it; deckDetail = null }; true }
                    Key.ButtonL2 -> if (tabs.count > 1) { tabs.step(-1); true } else false
                    Key.ButtonR2 -> if (tabs.count > 1) { tabs.step(1); true } else false
                    else -> false
                }
            }
            .onFocusChanged { shellOnly = it.isFocused }
            .focusable(),
    ) {
        DeckSideRail(
            selected = section,
            focusRequester = railFocus,
            homeFocus = homeHeroFocus,
            onSelect = { next ->
                section = next
                deckDetail = null
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            DeckPaneContent(
                section = section,
                deckDetail = deckDetail,
                graphicPane = graphicPane,
                onEditContainer = { id -> section = "containers"; deckDetail = id },
                onDetailBack = { deckDetail = null },
                onDetailOpen = { id -> deckDetail = id },
                onGraphicPane = { graphicPane = it },
                homeFocus = homeHeroFocus,
            )
            DeckHintBar(Modifier.align(Alignment.BottomEnd), onBack = hintBack, onPrev = { hintStep(-1) }, onNext = { hintStep(1) }, subTabs = tabs.count > 1)
            DeckCornerGlow()
        }
    }
    }
        DeckConsoleOverlays(onGo = { key -> section = key; deckDetail = null })
        if (!introDone) {
            DeckBootScreen(onDone = { introDone = true })
        }
        }
    }
}

/** The featured library: bundled SteamGridDB vertical grids under assets/covers. */
internal val featuredCovers = listOf(
    "Red Dead Redemption 2" to "covers/red_dead_redemption_2.jpg",
    "Red Dead Redemption" to "covers/red_dead_redemption.jpg",
    "Cyberpunk 2077" to "covers/cyberpunk_2077.jpg",
    "Hogwarts Legacy" to "covers/hogwarts_legacy.jpg",
    "High on Life" to "covers/high_on_life.jpg",
    "High on Life 2" to "covers/high_on_life_2.jpg",
    "Dinkum" to "covers/dinkum.jpg",
    "Titanfall 2" to "covers/titanfall_2.jpg",
    "Terraria" to "covers/terraria.jpg",
    "Star Wars Jedi: Fallen Order" to "covers/star_wars_jedi_fallen_order.jpg",
    "Marvel's Spider-Man 2" to "covers/spider_man_2.jpg",
    "Marvel's Spider-Man Remastered" to "covers/spider_man_remastered.jpg",
    "Marvel's Spider-Man: Miles Morales" to "covers/spider_man_miles_morales.jpg",
    "Ratchet & Clank: Rift Apart" to "covers/ratchet_and_clank_rift_apart.jpg",
                            "Assassin's Creed IV: Black Flag" to "covers/assassins_creed_4_black_flag.jpg",
                        "Assassin's Creed Odyssey" to "covers/assassins_creed_odyssey.jpg",
    "Assassin's Creed Valhalla" to "covers/assassins_creed_valhalla.jpg",
    "Assassin's Creed Mirage" to "covers/assassins_creed_mirage.jpg",
                )

/** Decode a bundled cover at roughly capsule resolution so the wall stays light on RAM. */
/** Best-effort store tag for a hand-added exe, so home doesn't call every Steam game "Custom". */
private fun hubStoreFromPath(path: String): String? {
    val p = path.lowercase()
    return when {
        p.contains("steamapps") || p.contains("steam_games") || p.contains("/steam/") -> "Steam"
        p.contains("epic games") -> "Epic"
        p.contains("gog games") || p.contains("gog galaxy") -> "GOG"
        p.contains("amazon games") -> "Amazon"
        else -> null
    }
}

internal fun decodeCover(context: Context, asset: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 320) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

/** Home: the cover wall behind a bottom-anchored 3D carousel — WinHub wordmark, Add Games. */
@Composable
private fun DeckHome(
    heroFocus: FocusRequester = remember { FocusRequester() },
    onEditContainer: (Int) -> Unit = {},
    portrait: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    // L2/R2 switch the shelves (All / Recent / Favourites / Store games) through the shell's sub-tabs.
    val deckTabs = LocalDeckTabs.current
    // Home style (Settings > Appearance): XMB keeps this Home and swaps the game dropdown for the XMB one.
    val xmbStyle = !portrait && remember { PreferenceManager.getDefaultSharedPreferences(context).getString("home_style", "xmb") != "classic" }
    LaunchedEffect(deckTabs) { deckTabs.bind(4) }
    val shelf = deckTabs.index
    var favs by remember {
        mutableStateOf(context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE).getStringSet("hub_favs", emptySet()).orEmpty())
    }
    var covers by remember { mutableStateOf<List<DeckCover>>(emptyList()) }
    var entries by remember { mutableStateOf<List<HubEntry>?>(null) }
    var containers by remember { mutableStateOf<List<Container>>(emptyList()) }
    var selected by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Pair<File, GameIdentifier.GameIdentity>?>(null) }
    var launchRequest by remember { mutableStateOf<Shortcut?>(null) }
    var launchSteam by remember { mutableStateOf<Boolean?>(null) }
    var launchMethodFor by remember { mutableStateOf<Shortcut?>(null) }
    var launchMethodSteam by remember { mutableStateOf<Boolean?>(null) }
    var addModeMenu by remember { mutableStateOf(false) }
    // Classic style: long-press a cover / Y opens the console-style game menu instead of the dropdown.
    var classicMenu by remember { mutableStateOf(false) }
    // Portrait: tapping a cover opens its game page over the grid.
    var portraitPageOpen by remember { mutableStateOf(false) }
    // The shell hides its liquid backdrop while the page is up (and when Home itself goes away).
    LaunchedEffect(portraitPageOpen) { PortraitPageState.open = portraitPageOpen }
    DisposableEffect(Unit) { onDispose { PortraitPageState.open = false } }
    // Game details / Search covers / Clone / Copy to Drive C / Change executable (classic + portrait menus).
    var gameTool by remember { mutableStateOf<HubToolRequest?>(null) }
    var portraitOptions by remember { mutableStateOf(false) }
    var addMode by remember { mutableStateOf(HubAddMode.MASTER) }
    var cronosBusy by remember { mutableStateOf(false) }
    var cronosName by remember { mutableStateOf("") }
    var homeMode by remember { mutableStateOf(HomeMode.GAMES) }
    // XMB dropdown: A (or a tap on the focused cover) opens the Deck XMB over Home's own background.
    var xmbShortcut by remember { mutableStateOf<Shortcut?>(null) }
    val xmbNav = remember { XmbNavState() }
    var xmbRootSel by remember { mutableStateOf(0) }
    val xmbFocus = remember { FocusRequester() }
    var showWrapperManager by remember { mutableStateOf(false) }
    val xmbNested = xmbStyle && xmbNav.depth > 0
    var propertiesFor by remember { mutableStateOf<Container?>(null) }
    var cronosReviewFor by remember { mutableStateOf<Container?>(null) }
    // A game added via Master Containers is a SHORTCUT inside a container you already have, so its
    // settings must edit that shortcut (per-game overrides), NOT the shared container. Set when the
    // game menu / detail page asks for Settings; the ContainerEditor is still reached from the
    // Storage section for whole-container changes.
    var shortcutSettingsFor by remember { mutableStateOf<Shortcut?>(null) }

    // Open a game's per-shortcut settings (a game added via Master Containers lives as a shortcut in
    // a shared container, so editing the container would change it for every game in it).
    fun openGameSettings(entry: HubEntry) {
        val g = entry.game ?: return
        val c = containers.find { it.id == g.containerId }
        when {
            c == null -> Toast.makeText(context, "Container not found", Toast.LENGTH_SHORT).show()
            !File(g.shortcutPath).isFile -> Toast.makeText(context, "Shortcut not found", Toast.LENGTH_SHORT).show()
            else -> shortcutSettingsFor = Shortcut(c, File(g.shortcutPath))
        }
    }

    fun openGameTool(entry: HubEntry, tool: HubTool) {
        val g = entry.game ?: return
        val c = containers.find { it.id == g.containerId }
        when {
            c == null -> Toast.makeText(context, "Container not found", Toast.LENGTH_SHORT).show()
            !File(g.shortcutPath).isFile -> Toast.makeText(context, "Shortcut not found", Toast.LENGTH_SHORT).show()
            else -> gameTool = HubToolRequest(tool, Shortcut(c, File(g.shortcutPath)))
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            covers = featuredCovers.map { (name, asset) ->
                DeckCover(name, hueOf(name), decodeCover(context, asset))
            }
            val lastPlayed = context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE)
                .getString("last_played", null)
            // The game you last played leads the row, so Home opens on "continue".
            entries = HubGamesStore.load(context).map { g ->
                HubEntry(g.name, g.coverPath?.let { BitmapFactory.decodeFile(it) }, g)
            }.sortedByDescending { it.game?.shortcutPath == lastPlayed }
            selected = 0
            containers = ContainerManager(context).also { it.reloadContainers() }.getContainers().toList()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val file = resolvePickedGame(context, uri)
            val identity = file?.let { runCatching { GameIdentifier.identify(it) }.getOrNull() }
            withContext(Dispatchers.Main) {
                if (file == null) {
                    Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
                } else {
                    pending = file to (identity ?: GameIdentifier.GameIdentity())
                }
            }
        }
    }

    fun playEntry(entry: HubEntry) {
        val game = entry.game
        if (game == null) {
            Toast.makeText(context, "Add this game first — use Add Games", Toast.LENGTH_SHORT).show()
            return
        }
        context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE)
            .edit().putString("last_played", game.shortcutPath).apply()
        launchSteam = game.store == "Steam"
        scope.launch(Dispatchers.IO) {
            val shortcut = runCatching {
                // Read the container fresh from disk for THIS launch. `containers` is the list the
                // Home screen loaded when it opened; using it here launched with a stale config -
                // enabling LSFG in Settings and hitting Play still started the game with the old
                // "off" engine and the drawer said frame gen was off. ContainerManager's fresh
                // load also picks up a container created after Home was built.
                val container = ContainerManager(context).getContainerById(game.containerId)
                    ?: return@runCatching null
                val desktop = File(game.shortcutPath)
                if (!desktop.isFile) return@runCatching null
                Shortcut(container, desktop)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (shortcut == null) {
                    Toast.makeText(context, "That game's shortcut is missing — re-add it", Toast.LENGTH_SHORT).show()
                } else {
                    launchRequest = shortcut
                }
            }
        }
    }

    fun openLaunchMethod(entry: HubEntry) {
        val game = entry.game
        if (game == null) {
            Toast.makeText(context, "Add this game first — use Add Games", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            val shortcut = runCatching {
                val container = ContainerManager(context).getContainerById(game.containerId)
                    ?: return@runCatching null
                val desktop = File(game.shortcutPath)
                if (!desktop.isFile) return@runCatching null
                Shortcut(container, desktop)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (shortcut == null) {
                    Toast.makeText(context, "That game's shortcut is missing — re-add it", Toast.LENGTH_SHORT).show()
                } else {
                    launchMethodSteam = game.store == "Steam"
                    launchMethodFor = shortcut
                }
            }
        }
    }

    fun addToHomeScreen(entry: HubEntry) {
        val g = entry.game ?: return
        val launchIntent = Intent(context, XServerDisplayActivity::class.java).apply {
            putExtra("container_id", g.containerId)
            putExtra("shortcut_path", g.shortcutPath)
            putExtra("shortcut_name", g.name)
        }
        val bmp = entry.bitmap
        val icon = if (bmp != null) {
            Icon.createWithBitmap(Bitmap.createScaledBitmap(bmp, 192, 288, true))
        } else {
            Icon.createWithResource(context, R.mipmap.ic_launcher)
        }
        val info = ShortcutInfo.Builder(context, "hub_${g.containerId}_${g.name.hashCode()}")
            .setShortLabel(g.name)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()
        val sm = context.getSystemService(ShortcutManager::class.java)
        if (sm != null && sm.isRequestPinShortcutSupported) {
            sm.requestPinShortcut(info, null)
        } else {
            Toast.makeText(context, "Pin shortcuts aren't supported here", Toast.LENGTH_SHORT).show()
        }
    }

    fun showProperties(entry: HubEntry) {
        val g = entry.game ?: return
        propertiesFor = containers.find { it.id == g.containerId }
        if (propertiesFor == null) Toast.makeText(context, "Container not found", Toast.LENGTH_SHORT).show()
    }

    fun deleteEntry(entry: HubEntry) {
        val g = entry.game ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { File(g.shortcutPath).delete() }
            g.coverPath?.let { runCatching { File(it).delete() } }
            HubGamesStore.remove(context, g)
            withContext(Dispatchers.Main) {
                val list = entries.orEmpty().filter { it.game?.shortcutPath != g.shortcutPath }
                entries = list
                selected = selected.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                portraitPageOpen = false
                Toast.makeText(context, "Removed \"${g.name}\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Store reads and writes run one after another, so a reload never sees a half-finished rename.
    val storeOps = remember { arrayOfNulls<Job>(1) }
    fun queueStore(block: suspend () -> Unit) {
        val prev = storeOps[0]
        storeOps[0] = scope.launch(Dispatchers.IO) { prev?.join(); block() }
    }
    fun reloadEntries() = queueStore {
        val refreshed = HubGamesStore.load(context).map { g ->
            HubEntry(g.name, g.coverPath?.let { BitmapFactory.decodeFile(it) }, g)
        }
        withContext(Dispatchers.Main) { entries = refreshed }
    }
    // The XMB's Game Details renames a game's shortcut files; its Home entry follows them.
    fun hubRenamed(container: Container, oldBase: String, newBase: String) = queueStore {
        val old = HubGamesStore.load(context).firstOrNull {
            it.containerId == container.id && File(it.shortcutPath).name == "$oldBase.desktop"
        }
        if (old != null) {
            val moved = File(old.shortcutPath).resolveSibling("$newBase.desktop").absolutePath
            HubGamesStore.replace(context, old, old.copy(name = newBase, shortcutPath = moved))
        }
    }

    // Scrape cover saves the new art as the shortcut's custom cover, but Home draws its own stored cover: copy it across.
    fun syncCover(s: Shortcut) = queueStore {
        val art = s.customCoverArtPath?.let { File(it) }?.takeIf { it.isFile } ?: return@queueStore
        val game = HubGamesStore.load(context).firstOrNull { it.shortcutPath == s.file.path } ?: return@queueStore
        val old = game.coverPath?.let { File(it) }
        if (old != null && old.isFile && old.lastModified() >= art.lastModified()) return@queueStore
        val dir = File(context.filesDir, "hub_covers").apply { mkdirs() }
        val out = File(dir, "custom_${s.file.nameWithoutExtension.hashCode()}.png")
        art.copyTo(out, overwrite = true)
        HubGamesStore.setCover(context, game, out.absolutePath)
    }

    val closeSettings: () -> Unit = {
        shortcutSettingsFor = null
        // The editor can rename the game / swap its cover, so re-read the store to keep
        // the carousel tile in sync.
        reloadEntries()
    }
    val backLayer = LocalDeckBack.current
    DisposableEffect(homeMode, shortcutSettingsFor, xmbNested) {
        backLayer.onBack = if (xmbNested) {
            { xmbNav.pop(); true }
        } else if (shortcutSettingsFor != null) {
            { closeSettings(); true }
        } else if (homeMode != HomeMode.GAMES) {
            { homeMode = HomeMode.GAMES; true }
        } else {
            null
        }
        onDispose { backLayer.onBack = null }
    }

    val wallCovers = remember(covers) {
        if (covers.isEmpty()) List(12) { index -> DeckCover("", index / 12f) } else covers
    }
    val lastPlayedPath = context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE).getString("last_played", null)
    val visible = remember(entries, shelf, favs) {
        val all = entries.orEmpty()
        val stats = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
        when (shelf) {
            1 -> all.filter { hubHasPlaytime(context, it.title) || it.game?.shortcutPath == lastPlayedPath }
                .sortedWith(
                    compareByDescending<HubEntry> { it.game?.shortcutPath == lastPlayedPath }
                        .thenByDescending { stats.getLong("${it.title}_playtime", 0L) },
                )
            2 -> all.filter { it.game?.shortcutPath in favs }
            3 -> all.filter { it.game?.store != null }
            else -> all
        }
    }
    LaunchedEffect(shelf) { selected = 0 }
    val sel = selected.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
    val current = visible.getOrNull(sel)
    val currentPath = current?.game?.shortcutPath
    LaunchedEffect(xmbStyle, currentPath, containers) {
        if (!xmbStyle) return@LaunchedEffect
        val g = current?.game
        xmbShortcut = if (g == null) null else withContext(Dispatchers.IO) {
            val c = containers.find { it.id == g.containerId }
            val f = File(g.shortcutPath)
            if (c != null && f.isFile) runCatching { Shortcut(c, f) }.getOrNull() else null
        }
    }
    val appCtx = context
    val coScope = scope
    val xmbScope = remember(xmbNav) {
        object : XmbScope {
            override val context: Context get() = appCtx
            override val scope: kotlinx.coroutines.CoroutineScope get() = coScope
            override fun push(menu: XmbMenu) = xmbNav.push(menu)
            override fun pop() = xmbNav.pop()
            override fun popToRoot() = xmbNav.popTo(0)
            override fun refresh() = xmbNav.bump()
            override fun toast(message: String) { Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show() }
            override fun saved() { xmbNav.savedAt = System.currentTimeMillis() }
            override fun confirm(confirm: XmbConfirm, onOk: () -> Unit) = xmbNav.push(xmbConfirmMenu(xmbNav, confirm, onOk))
            override fun reloadGames() { xmbShortcut?.let { syncCover(it) }; reloadEntries() }
            override fun shortcutRenamed(container: Container, oldBase: String, newBase: String) = hubRenamed(container, oldBase, newBase)
        }
    }
    xmbNav.refocus = { runCatching { xmbFocus.requestFocus() } }
    LaunchedEffect(xmbNested) { if (xmbNested) runCatching { xmbFocus.requestFocus() } }
    val xmbHost = XmbGameHost(
        remove = { s -> visible.firstOrNull { it.game?.shortcutPath == s.file.path }?.let { deleteEntry(it); true } ?: false },
        containers = { containers },
        openWrapperManager = { showWrapperManager = true },
    )
    val xmbSc = xmbShortcut?.takeIf { xmbStyle && it.file.path == currentPath }
    val xmbInfo: (@Composable (Int) -> Unit)? = xmbSc?.let { sc ->
        @Composable { lines: Int ->
            val playtime = remember(sc) { xmbPlaytime(context, sc.name) }
            XmbInfo(sc, playtime, lines)
        }
    }
    // Launch transition: the chosen cover turns the theme colour and zooms to fill the screen while the game's window waits.
    var launching by remember { mutableStateOf(false) }
    val launchT = remember { Animatable(0f) }
    // Flips only at the ends of the zoom, so the frames in between don't recompose Home.
    val launchShown by remember { derivedStateOf { launching || launchT.value > 0.001f } }
    LaunchedEffect(launching) {
        if (launching) {
            launchT.animateTo(1f, tween(DECK_LAUNCH_ZOOM_MS, easing = DeckEase))
            // Safety net: a launch that never opened a window (refused, crashed) hands Home back.
            delay(6000)
            launching = false
        } else {
            launchT.animateTo(0f, tween(220, easing = DeckEase))
        }
    }
    val homeLifecycle = LocalLifecycleOwner.current
    DisposableEffect(homeLifecycle) {
        // Coming back from the game resumes Home: lift the zoom.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) launching = false }
        homeLifecycle.lifecycle.addObserver(observer)
        onDispose { homeLifecycle.lifecycle.removeObserver(observer) }
    }
    val homeFade by animateFloatAsState(if (xmbNested) 0f else 1f, tween(260), label = "homeFade")
    val details = current?.game?.let { g ->
        buildList {
            add("Store" to hubOriginLabel(current))
            containers.find { it.id == g.containerId }?.let { add("Container" to it.name) }
            add("Playtime" to hubPlaytimeLabel(context, current.title))
            g.appId?.let { add("Steam AppID" to it.toString()) }
        }
    }.orEmpty()
    fun toggleFavorite() {
        val path = current?.game?.shortcutPath ?: return
        favs = if (path in favs) favs - path else favs + path
        context.getSharedPreferences("hub_prefs", Context.MODE_PRIVATE).edit().putStringSet("hub_favs", favs).apply()
        Toast.makeText(context, if (path in favs) "Added to favourites" else "Removed from favourites", Toast.LENGTH_SHORT).show()
    }
    val playIntro = remember { !homeIntroPlayed }
    var splash by remember { mutableStateOf(playIntro) }
    val intro = remember { Animatable(if (playIntro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (playIntro) {
            homeIntroPlayed = true
            delay(650)
            splash = false
            intro.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 180f))
        }
    }
    val actions = current?.let { entry ->
        val game = entry.game
        if (xmbSc != null && game != null) buildList<DeckMenuAction> {
            // XMB dropdown: Play, then the Games screen's full list; rows with columns open them over Home.
            add(DeckMenuAction("Play", Icons.Filled.PlayArrow) { playEntry(entry) })
            hubXmbActions(
                context, xmbSc, game, xmbHost,
                HubXmbOps(
                    launchMethod = { openLaunchMethod(entry) },
                    addToHome = { addToHomeScreen(entry) },
                    containerProperties = { showProperties(entry) },
                    delete = { deleteEntry(entry) },
                ),
            ).forEachIndexed { i, a ->
                add(DeckMenuAction(a.label, a.icon, a.danger, a.subtitle, a.menu != null) {
                    val mk = a.menu
                    if (mk != null) { xmbRootSel = i + 1; xmbNav.push(mk(xmbScope)) } else a.onClick()
                })
            }
        } else listOf(
            DeckMenuAction("Play", Icons.Filled.PlayArrow) { playEntry(entry) },
            DeckMenuAction("Launch method", Icons.Filled.Layers) { openLaunchMethod(entry) },
            DeckMenuAction("Settings", Icons.Filled.Settings) { openGameSettings(entry) },
            DeckMenuAction("Game details", Icons.Filled.Edit) { openGameTool(entry, HubTool.DETAILS) },
            DeckMenuAction("Search covers", Icons.Filled.Search) { openGameTool(entry, HubTool.COVERS) },
            DeckMenuAction("Clone to container", Icons.Filled.ContentCopy) { openGameTool(entry, HubTool.CLONE) },
            DeckMenuAction("Copy to Drive C", Icons.Filled.DriveFileMove) { openGameTool(entry, HubTool.DRIVE_C) },
            DeckMenuAction("Change executable", Icons.Filled.SwapHoriz) { openGameTool(entry, HubTool.CHANGE_EXE) },
            DeckMenuAction("Add to Home Screen", Icons.Filled.Home) { addToHomeScreen(entry) },
            DeckMenuAction("Properties", Icons.Filled.Info) { showProperties(entry) },
            DeckMenuAction("Delete", Icons.Filled.Delete, danger = true) { deleteEntry(entry) },
        )
    }.orEmpty()

    val classicItems = current?.takeIf { !xmbStyle }?.let { entry ->
        val fav = entry.game?.shortcutPath in favs
        buildList {
            actions.forEachIndexed { i, a ->
                add(DeckConsoleItem(a.label, a.icon, a.danger, a.onClick))
                // Favourite sits after Settings (Y no longer toggles it in this style).
                if (i == 2 && entry.game != null) {
                    add(DeckConsoleItem(if (fav) "Remove from favourites" else "Add to favourites",
                        if (fav) Icons.Filled.StarBorder else Icons.Filled.Star) { toggleFavorite() })
                }
            }
        }
    }.orEmpty()

    // The row above the games holds the glass Add game card.
    val cards = listOf(
        DeckHomeCard("Add game", "Add games to your library", Icons.Filled.Add,
            listOf(Color(0xFF1B1F2A), Color(0xFF0E1016)), glass = true) { addModeMenu = true },
    )

    // The per-game editor is drawn inline: the carousel gives up focus, then focus moves into the editor.
    val settingsFocus = LocalFocusManager.current

    // Hand focus back to the carousel once any dialog/overlay opened from Home goes away.
    val overlayOpen = pending != null || launchRequest != null || launchMethodFor != null ||
        propertiesFor != null || cronosReviewFor != null || shortcutSettingsFor != null || addModeMenu || xmbNested || showWrapperManager || classicMenu
    var hadOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen && hadOverlay) {
            delay(60)
            runCatching { heroFocus.requestFocus() }
        }
        hadOverlay = overlayOpen
    }

    // XMB style: text fields in the XMB columns type with the pad-friendly keyboard.
    XmbKeyboardHost(xmbStyle) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Portrait lets the shell's liquid backdrop show through.
                .then(if (portrait) Modifier else Modifier.background(Deck.background))
                .clipToBounds(),
        ) {
            if (!portrait) DeckCapsuleWall(
                covers = wallCovers,
                driftMs = if (covers.isEmpty()) 75_000 else 40_000,
            )
            if (!portrait) {
                // The backdrop follows the focused game: its cover, blurred, crossfading as the selection moves.
                Crossfade(targetState = current?.bitmap, animationSpec = tween(600), label = "homeBackdrop") { bmp ->
                    if (bmp != null) {
                        Image(
                            bitmap = remember(bmp) { bmp.asImageBitmap() },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(18.dp).alpha(0.34f),
                        )
                    }
                }
                val coverTint = remember(current?.bitmap) {
                    current?.bitmap?.let { b -> Color(Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)) }
                }
                val tint by animateColorAsState(coverTint ?: Deck.primary, tween(600), label = "homeTint")
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val c = Offset(size.width * 0.2f, size.height * 0.3f)
                        drawCircle(
                            Brush.radialGradient(listOf(tint.copy(alpha = 0.32f), Color.Transparent), c, size.width * 0.7f),
                            size.width * 0.7f,
                            c,
                        )
                    },
                )
            }
            if (!portrait) DeckWallFade()

            if (portrait) PortraitHome(
                entries = visible,
                loaded = entries != null,
                selected = sel,
                favs = favs,
                shelf = shelf,
                onShelf = { deckTabs.select(it) },
                onSelect = { selected = it; portraitPageOpen = true },
                onPlay = { i -> visible.getOrNull(i)?.let { playEntry(it) } },
                onAdd = { addModeMenu = true },
                onMenu = { i -> selected = i; classicMenu = true },
            ) else DeckHomeCarousel(
                modifier = Modifier.graphicsLayer {
                    // An open tool replaces Home: it fades out and lifts a little, as the XMB's root does.
                    alpha = intro.value * homeFade
                    translationY = (1f - intro.value) * 60.dp.toPx() - (1f - homeFade) * 12.dp.toPx()
                },
                entries = visible,
                loaded = entries != null && (visible.isNotEmpty() || entries.orEmpty().isEmpty()),
                selected = sel,
                favs = favs,
                details = details,
                onFavorite = { toggleFavorite() },
                classic = !xmbStyle,
                onMenu = { classicMenu = true },
                onSelect = { selected = it },
                mode = homeMode,
                onMode = { homeMode = it },
                xmbRows = xmbSc != null,
                menuHidden = xmbNested,
                launching = launchShown,
                launchT = { launchT.value },
                info = xmbInfo,
                actions = actions,
                cards = cards,
                onAdd = { addModeMenu = true },
                focus = heroFocus,
                enabled = shortcutSettingsFor == null && !xmbNested && !classicMenu,
            )
            if (portrait && portraitPageOpen && current != null) {
                BackHandler(enabled = shortcutSettingsFor == null && !classicMenu) { portraitPageOpen = false }
                PortraitGamePage(
                    entry = current,
                    favourite = current.game?.shortcutPath in favs,
                    containerName = containers.find { it.id == current.game?.containerId }?.name,
                    onBack = { portraitPageOpen = false },
                    onPlay = { playEntry(current) },
                    onSettings = { openGameSettings(current) },
                    onMenu = { portraitOptions = true },
                )
                if (portraitOptions && classicItems.isNotEmpty()) {
                    PortraitGameMenuSheet(
                        title = current.title,
                        subtitle = hubOriginLabel(current) + " · " + hubPlaytimeLabel(context, current.title),
                        cover = current.bitmap,
                        items = classicItems,
                        onDismiss = { portraitOptions = false },
                    )
                }
            }
            if (entries == null) {
                CircularProgressIndicator(
                    color = DeckPalette.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (!portrait && visible.isEmpty() && entries.orEmpty().isNotEmpty()) {
                Text(
                    if (xmbStyle) "Nothing on this shelf yet - press Y on a game to favourite it" else "Nothing on this shelf yet - open a game's menu (Y) to favourite it",
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Shelves (L2/R2) top centre, status top right.
            if (!portrait) DeckStretchyTabs(
                labels = listOf("All", "Recent", "Favourites", "Store games"),
                selected = shelf,
                onSelect = { deckTabs.select(it) },
                focusable = false,
                fillWidth = false,
                height = 30.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).graphicsLayer { alpha = homeFade * (1f - launchT.value) },
            )
            if (!portrait) DeckStatusStrip(
                Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 12.dp).graphicsLayer { alpha = 1f - launchT.value },
            )
            AnimatedVisibility(
                visible = splash,
                exit = fadeOut(tween(450)),
                modifier = Modifier.align(Alignment.Center),
            ) { DeckWordmark() }

            HubGameToolsHost(
                request = gameTool,
                containers = containers,
                onClose = { gameTool = null },
                onChanged = { sc -> syncCover(sc); reloadEntries() },
            )

            if (classicMenu && portrait && current != null && classicItems.isNotEmpty()) {
                PortraitGameMenuSheet(
                    title = current.title,
                    subtitle = hubOriginLabel(current) + " · " + hubPlaytimeLabel(context, current.title),
                    cover = current.bitmap,
                    items = classicItems,
                    onDismiss = { classicMenu = false },
                )
            } else if (classicMenu && current != null && classicItems.isNotEmpty()) {
                DeckConsoleGameMenu(
                    title = current.title,
                    subtitle = hubOriginLabel(current),
                    items = classicItems,
                    onDismiss = { classicMenu = false },
                )
            }

            if (addModeMenu) {
                DeckAddModeMenu(
                    onPick = { mode ->
                        addMode = mode
                        addModeMenu = false
                        picker.launch(arrayOf("*/*"))
                    },
                    onDismiss = { addModeMenu = false },
                )
            }

            if (cronosBusy) {
                Dialog(onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Column(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .clip(DeckPalette.groupShape)
                            .background(DeckPalette.surface)
                            .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = DeckPalette.primary)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Building Cronos container\n$cronosName…",
                            color = DeckPalette.onBackground,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            propertiesFor?.let { c ->
                DeckPropertiesDialog(container = c, onDismiss = { propertiesFor = null })
            }

            if (showWrapperManager) DeckWrapperManagerDialog(onDismiss = { showWrapperManager = false })

            if (xmbNested) {
                // The XMB dropdown's tools open as nested XMB columns over a dimmed Home.
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { xmbNav.popTo(0) } }
                        .focusRequester(xmbFocus)
                        .focusable()
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            // Typing into a Text row: the field gets the keys; A = done, B = cancel.
                            if (xmbNav.editingKey != null) {
                                return@onPreviewKeyEvent when (e.key) {
                                    Key.ButtonA, Key.DirectionCenter -> { xmbNav.commitEdit(xmbScope); true }
                                    Key.ButtonB, Key.Back -> { xmbNav.pop(); true }
                                    else -> false
                                }
                            }
                            val k = when (e.key) {
                                Key.DirectionUp -> XmbKey.Up
                                Key.DirectionDown -> XmbKey.Down
                                Key.DirectionLeft -> XmbKey.Left
                                Key.DirectionRight -> XmbKey.Right
                                Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> XmbKey.A
                                Key.ButtonB, Key.Back, Key.Escape -> XmbKey.B
                                Key.ButtonL1 -> XmbKey.L1
                                Key.ButtonR1 -> XmbKey.R1
                                else -> null
                            } ?: return@onPreviewKeyEvent false
                            xmbNav.onKey(k, xmbScope)
                        },
                ) {
                    XmbNestedLayer(
                        xmbNav, xmbScope, maxWidth.value, maxHeight.value, maxHeight > maxWidth, Deck.primary,
                        xmbSc?.name.orEmpty(), xmbSc?.icon, actions.map { it.icon }, xmbRootSel,
                    )
                }
            }

            launchMethodFor?.let { s ->
                DeckLaunchMethodDialog(
                    shortcut = s,
                    onDismiss = { launchMethodFor = null; launchMethodSteam = null },
                    steamOverride = launchMethodSteam,
                    heading = "Launch method",
                    confirmLabel = "Save",
                    onLaunch = { mode, gm, remember ->
                        s.putExtra("launchMode", mode)
                        s.putExtra("launchModeRemembered", if (remember) "1" else "0")
                        if (gm != null) {
                            SteamPrefs.init(context)
                            SteamPrefs.setGoldbergMode(steamAppIdOf(s), gm)
                        }
                        s.saveData()
                        launchMethodFor = null
                        launchMethodSteam = null
                        Toast.makeText(context, "Launch method saved", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            shortcutSettingsFor?.let { sc ->
                // The editor is inline, so it needs its own focus owner: the wrapper takes focus when it
                // opens and walks the D-pad itself, like the config popups do.
                val editorRoot = remember { FocusRequester() }
                val editTracker = LocalDeckEditTracker.current
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    runCatching { editorRoot.requestFocus() }
                    settingsFocus.moveFocusSafely(FocusDirection.Next)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(editorRoot)
                        .focusable()
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || editTracker.editing) return@onKeyEvent false
                            val dir = when (e.key) {
                                Key.DirectionUp -> FocusDirection.Up
                                Key.DirectionDown -> FocusDirection.Down
                                Key.DirectionLeft -> FocusDirection.Left
                                Key.DirectionRight -> FocusDirection.Right
                                else -> null
                            } ?: return@onKeyEvent false
                            settingsFocus.moveFocusOrStep(dir)
                        },
                ) {
                    DeckShortcutSettingsScreen(shortcut = sc, onDismiss = closeSettings)
                }
            }

            cronosReviewFor?.let { c ->
                DeckCronosReviewDialog(
                    container = c,
                    onKeep = { cronosReviewFor = null },
                    onEdit = {
                        cronosReviewFor = null
                        onEditContainer(c.id)
                    },
                )
            }

            DeckLaunchStack(
                activity = activity,
                request = launchRequest,
                onMenuClosed = { launchRequest = null; launchSteam = null },
                steamOverride = launchSteam,
                onLaunching = { launching = true },
            )

            pending?.let { (file, identity) ->
                HubConfirmDialog(
                    file = file,
                    containerNames = containers.map { it.name },
                    initialName = identity.name ?: file.nameWithoutExtension,
                    initialAppId = identity.appId,
                    mode = addMode,
                    onDismiss = { pending = null },
                    onConfirm = { name, appId, containerIndex, coverPath ->
                        pending = null
                        if (containerIndex < 0) {
                            // Cronos — a FRESH dedicated container with just this game's shortcut.
                            // Built through ContainerDetailViewModel (the SAME form seeding +
                            // buildCreateData the old UI's New Container FAB uses), so its config
                            // matches a hand-made container exactly. A bare Container(0).getData()
                            // cannot do that: JSONObject.put(name, null) REMOVES the key, so the null
                            // cpuList/cpuListWoW64/emulator/box64Version/fexcoreVersion never reached
                            // the config file and the container died at launch with invalid settings.
                            cronosName = name
                            cronosBusy = true
                            scope.launch(Dispatchers.IO) {
                                val mgr = ContainerManager(context).also { it.reloadContainers() }
                                val data = runCatching {
                                    // Wine pick = the first entry a manual New Container offers
                                    // (installed Wine profiles first, then Proton), with the editor's
                                    // empty-list fallbacks so a device with no content still tries the
                                    // wine a working container already uses.
                                    val cm = ContentsManager(context).also { it.syncContents() }
                                    val wineList =
                                        cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)
                                            .map { ContentsManager.getEntryName(it) } +
                                            cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)
                                                .map { ContentsManager.getEntryName(it) }
                                    val wine = wineList.firstOrNull()
                                        ?: mgr.getContainers().firstOrNull()?.wineVersion
                                        ?: Container(0, mgr).wineVersion
                                    ContainerDetailViewModel.buildFreshCreateData(context, name, wine, true)
                                }.getOrNull()
                                withContext(Dispatchers.Main) {
                                    if (data == null) {
                                        cronosBusy = false
                                        Toast.makeText(context, "Cronos setup failed — couldn't build config", Toast.LENGTH_SHORT).show()
                                        return@withContext
                                    }
                                    mgr.createContainerAsync(data, ContentsManager(context)) { created ->
                                        cronosBusy = false
                                        if (created == null) {
                                            Toast.makeText(context, "Cronos setup failed — couldn't create the Wine prefix", Toast.LENGTH_SHORT).show()
                                            return@createContainerAsync
                                        }
                                        scope.launch(Dispatchers.IO) {
                                            val added = runCatching {
                                                val desktop = ExeShortcutImporter.addToShortcuts(context, created, file, name, appId)
                                                val finalCover = coverPath ?: appId?.let { fetchHubCover(context, it) }
                                                val game = HubGame(
                                                    name, created.id, desktop.absolutePath, appId, finalCover,
                                                    hubStoreFromPath(file.absolutePath),
                                                )
                                                HubGamesStore.add(context, game)
                                                HubEntry(name, finalCover?.let { BitmapFactory.decodeFile(it) }, game)
                                            }.getOrNull()
                                            containers = ContainerManager(context).also { it.reloadContainers() }.getContainers().toList()
                                            withContext(Dispatchers.Main) {
                                                if (added == null) {
                                                    Toast.makeText(context, "Container created, but adding the game failed", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val list = entries.orEmpty() + added
                                                    entries = list
                                                    deckTabs.select(0)
                                                    selected = list.lastIndex
                                                    Toast.makeText(context, "Added \"${added.title}\" to its own Cronos container", Toast.LENGTH_SHORT).show()
                                                    cronosReviewFor = created
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            return@HubConfirmDialog
                        }
                        val container = containers.getOrNull(containerIndex)
                        if (container == null) {
                            Toast.makeText(context, "Create a container first", Toast.LENGTH_SHORT).show()
                            return@HubConfirmDialog
                        }
                        scope.launch(Dispatchers.IO) {
                            val added = runCatching {
                                val desktop = ExeShortcutImporter.addToShortcuts(context, container, file, name, appId)
                                val finalCover = coverPath ?: appId?.let { fetchHubCover(context, it) }
                                val game = HubGame(
                                    name, container.id, desktop.absolutePath, appId, finalCover,
                                    hubStoreFromPath(file.absolutePath),
                                )
                                HubGamesStore.add(context, game)
                                HubEntry(name, finalCover?.let { BitmapFactory.decodeFile(it) }, game)
                            }.getOrNull()
                            withContext(Dispatchers.Main) {
                                if (added == null) {
                                    Toast.makeText(context, "Couldn't add that game", Toast.LENGTH_SHORT).show()
                                } else {
                                    val list = entries.orEmpty() + added
                                    entries = list
                                    deckTabs.select(0)
                                    selected = list.lastIndex
                                    Toast.makeText(context, "Added \"${added.title}\"", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Graphics: pick the pane, then the real screen renders below it. */
@Composable
private fun DeckGraphicsPane(pane: String, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Deck.background),
    ) {
        when (pane) {
            "wrappers" -> WrapperManagerScreen()
            else -> AdrenoToolsScreen()
        }
        DeckStretchyTabs(
            labels = listOf("GPU drivers", "Wrappers"),
            selected = if (pane == "wrappers") 1 else 0,
            onSelect = { onSelect(if (it == 1) "wrappers" else "gpu") },
            focusable = true,
            selectOnFocus = true,
            fillWidth = false,
            height = 38.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 16.dp),
        )
    }
}

/** The wordmark: WinHub in DroidDeck's heavy style — "Win" light, "Hub" in the signal blue. */
@Composable
private fun DeckWordmark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Win",
            color = Deck.onBackground,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
        Text(
            text = "Hub",
            color = Deck.primary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Deck.primary),
        )
    }
}

/** Flags touch input (observed before the children, never consumed) so focus rings can hide. */
private fun Modifier.deckTouchTracker(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed }) { DeckTouchMode.touch = true; DeckConsole.poke() }
        }
    }
}

/** Move the section selection forward/back, wrapping at the ends. */
private fun stepSection(
    sections: List<DeckSection>,
    current: String,
    delta: Int,
    onSelect: (String) -> Unit,
) {
    if (sections.isEmpty()) return
    val idx = sections.indexOfFirst { it.key == current }.coerceAtLeast(0)
    val next = ((idx + delta) % sections.size + sections.size) % sections.size
    onSelect(sections[next].key)
}

/** The pane body shared by both rail styles: section switch + the per-section screens. */
@Composable
private fun DeckPaneContent(
    section: String,
    deckDetail: Int?,
    graphicPane: String,
    onEditContainer: (Int) -> Unit,
    onDetailBack: () -> Unit,
    onDetailOpen: (Int) -> Unit,
    onGraphicPane: (String) -> Unit,
    homeFocus: FocusRequester,
    portrait: Boolean = false,
) {
    // Every tab switch slides the incoming pane up from the bottom while the outgoing one
    // drops away — one motion for the whole shell.
    AnimatedContent(
        targetState = section,
        transitionSpec = {
            (fadeIn(tween(300, delayMillis = 80, easing = DeckEase)) +
                slideInVertically(tween(420, delayMillis = 80, easing = DeckEase)) { it / 24 })
                .togetherWith(
                    fadeOut(tween(170, easing = DeckEase)) +
                        slideOutVertically(tween(170, easing = DeckEase)) { -it / 40 },
                )
        },
        modifier = Modifier.fillMaxSize(),
        label = "deckSection",
    ) { currentSection ->
        when (currentSection) {
            "home" -> DeckHome(heroFocus = homeFocus, onEditContainer = onEditContainer, portrait = portrait)
            "containers" -> AnimatedContent(
                targetState = deckDetail,
                transitionSpec = {
                    (fadeIn(tween(300, delayMillis = 80, easing = DeckEase)) +
                        slideInVertically(tween(420, delayMillis = 80, easing = DeckEase)) { it / 24 })
                        .togetherWith(
                            fadeOut(tween(170, easing = DeckEase)) +
                                slideOutVertically(tween(170, easing = DeckEase)) { -it / 40 },
                        )
                },
                modifier = Modifier.fillMaxSize(),
                label = "deckDetail",
            ) { detailId ->
                if (detailId != null) {
                    // Per-id key: the screen's remembered sections/focus must not survive a
                    // switch from one container (or New / Defaults) to another.
                    key(detailId) {
                        DeckContainerDetailScreen(
                            containerId = detailId,
                            onNavigateBack = onDetailBack,
                        )
                    }
                } else {
                    DeckContainersScreen(onEditContainer = onDetailOpen)
                }
            }
            // Portrait uses the classic store screens; Deck-styled stores stay for landscape.
            "stores" -> if (portrait) PortraitStoresScreen() else DeckStoresScreen()
            "file_manager" -> FileManagerScreen(deckMode = true)
            "graphics" -> DeckGraphicsPane(
                pane = graphicPane,
                onSelect = onGraphicPane,
            )
            "input_controls" -> InputControlsScreen()
            "contents" -> DeckContentsScreen()
            // The full Save Manager (Steam / Custom / Epic / GOG / Settings), in Deck chrome.
            "saves" -> SaveManagerScreen(deckMode = true)
            "social_hub" -> SocialHubScreen(deck = true)
            "settings" -> DeckSettingsScreen()
            // Defensive fallback only; never composes alongside the "home" branch, so it
            // gets its own requester instead of sharing the rail's homeHeroFocus.
            else -> DeckHome(onEditContainer = onEditContainer)
        }
    }
}

/** Controller legend: the one place a player can look to know what L1/R1 do here.
 *  Draw-only text, so it never becomes a focus stop. */
@Composable
private fun DeckLegend(modifier: Modifier = Modifier) {
    Text(
        text = "A Select   B Back   L1/R1 Tabs   L2/R2 Sub-tabs   Stick / D-pad Move",
        color = Deck.onSurfaceVariant.copy(alpha = 0.75f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = modifier.padding(end = 14.dp, bottom = 8.dp),
    )
}

/** DroidDeck's corner light — one circular accent glow just inside the top right of every pane.
 *  Draw-only, so it never intercepts touches meant for the screen underneath. */
@Composable
private fun DeckCornerGlow() {
    val cornerAccent = Deck.primary
    Box(modifier = Modifier.fillMaxSize().drawBehind { drawDeckCornerGlow(cornerAccent) })
}

private fun DrawScope.drawDeckCornerGlow(cornerAccent: Color) {
    val radius = 460.dp.toPx()
    val center = Offset(size.width - radius * 0.35f, radius * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cornerAccent.copy(alpha = 0.24f),
                cornerAccent.copy(alpha = 0.10f),
                cornerAccent.copy(alpha = 0.04f),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * The horizontal top strip: every section's blue icon across the top. The selected one shows its
 * name to the right of the icon; unselected ones are just the icon. L1/R1 (handled by the shell)
 * switch sections, so this strip itself is not a focus target.
 */
private const val SELECTED_TAB_WEIGHT = 2.4f

@Composable
private fun DeckTopTabs(
    sections: List<DeckSection>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val barOpacity by AppThemeState.topBarOpacity.collectAsState()
    val density = LocalDensity.current
    // Every tab owns an equal, fixed cell, so the pill's target is exact and never moves while it
    // glides (no label popping in and re-flowing the strip). The glide is DroidDeck's ChipGlide: the
    // leading edge springs ahead, the trailing edge follows 40 ms later.
    // The strip is NOT a focus target: L1/R1 switch sections, so D-pad focus stays in the page.
    val start = remember { Animatable(0f) }
    val end = remember { Animatable(0f) }
    var placedAt by remember { mutableStateOf(Float.NaN) }
    var lastIndex by remember { mutableIntStateOf(-1) }
    val accent = Deck.primary
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Deck.surface.copy(alpha = barOpacity)),
    ) {
        val index = sections.indexOfFirst { it.key == selected }.coerceAtLeast(0)
        val padPx = with(density) { 8.dp.toPx() }
        // The selected tab is wider (it carries its name); the total weight never changes, so the
        // unit width is constant and the pill's target is exact before the glide even starts.
        val unitPx = (with(density) { maxWidth.toPx() } - padPx * 2f) / (sections.size - 1 + SELECTED_TAB_WEIGHT)
        LaunchedEffect(index, unitPx) {
            val s0 = padPx + index * unitPx
            val e0 = s0 + SELECTED_TAB_WEIGHT * unitPx
            val from = lastIndex
            lastIndex = index
            if (from < 0 || from == index || placedAt != unitPx) {
                placedAt = unitPx
                start.snapTo(s0)
                end.snapTo(e0)
                return@LaunchedEffect
            }
            coroutineScope {
                val forward = index > from
                val lead = if (forward) end else start
                val trail = if (forward) start else end
                launch { lead.animateTo(if (forward) e0 else s0, DeckLead) }
                launch { delay(40); trail.animateTo(if (forward) s0 else e0, DeckTrail) }
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    if (end.value > start.value) {
                        val top = 4.dp.toPx()
                        val corner = CornerRadius(10.dp.toPx())
                        val pill = Size(end.value - start.value, size.height - top * 2f)
                        drawRoundRect(accent.copy(alpha = 0.14f), Offset(start.value, top), pill, corner)
                        drawRoundRect(accent, Offset(start.value, top), pill, corner, style = Stroke(1.5.dp.toPx()))
                    }
                },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEach { item ->
                val current = selected == item.key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(if (current) SELECTED_TAB_WEIGHT else 1f)
                        .clip(RoundedCornerShape(10.dp))
                        .focusProperties { canFocus = false }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(item.key)
                        }
                        .padding(vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (current) Deck.primary else Deck.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    if (current) {
                        Text(
                            text = item.label,
                            color = if (current) Deck.primary else Deck.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = Deck.line.copy(alpha = barOpacity))
}

/**
 * Portrait bottom bar: every section as an icon + label tab (72dp wide, 60dp tall), scrolling
 * sideways when they don't all fit and keeping the selected one centred. Touch only.
 */
@Composable
private fun DeckBottomTabs(
    sections: List<DeckSection>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val itemWidth = 72.dp
    val index = sections.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val pill = RoundedCornerShape(36.dp)
    val itemShape = RoundedCornerShape(30.dp)
    // Floating pill with side and bottom margins; solid, not glass, so it stays readable over any page.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val viewportPx = with(density) { (maxWidth - 12.dp).toPx() }
        val itemPx = with(density) { itemWidth.toPx() }
        LaunchedEffect(index, viewportPx) {
            scroll.animateScrollTo((index * itemPx - (viewportPx - itemPx) / 2f).toInt().coerceAtLeast(0))
        }
        val accent = deckAccentBrush()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, pill)
                .clip(pill)
                .background(Deck.surface)
                .border(1.dp, Deck.line, pill)
                .horizontalScroll(scroll)
                .padding(6.dp),
        ) {
            sections.forEach { item ->
                val current = selected == item.key
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .width(itemWidth)
                        .height(60.dp)
                        .clip(itemShape)
                        .background(if (current) Deck.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .then(if (current) Modifier.border(1.5.dp, accent, itemShape) else Modifier)
                        .focusProperties { canFocus = false }
                        .clickable { onSelect(item.key) },
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (current) Deck.primary else Deck.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = item.label,
                        color = if (current) Deck.primary else Deck.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The left rail: every section on screen down the edge, 92dp wide, rounded 14 items that tint
 * with the signal blue when current — DroidDeck's SideRail, carrying this app's own sections.
 */
@Composable
private fun DeckSideRail(
    selected: String,
    focusRequester: FocusRequester,
    homeFocus: FocusRequester,
    onSelect: (String) -> Unit,
) {
    DeckDragFrame(
        modifier = Modifier
            .width(92.dp)
            .fillMaxHeight()
            .background(Deck.surface),
        shape = RoundedCornerShape(14.dp),
        strokeWidth = 2.5.dp,
        gap = 2.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            deckSections().forEach { item ->
                DeckRailItem(
                    label = item.label,
                    icon = item.icon,
                    current = selected == item.key,
                    modifier = if (selected == item.key) Modifier.focusRequester(focusRequester) else Modifier,
                    // Only Home has a competing focus target (the "+" button) in its beam.
                    rightFocus = if (item.key == "home") homeFocus else null,
                    onFocus = { if (selected != item.key) onSelect(item.key) },
                    onClick = { onSelect(item.key) },
                )
            }
        }
    }
}

@Composable
private fun DeckRailItem(
    label: String,
    icon: ImageVector,
    current: Boolean,
    modifier: Modifier = Modifier,
    rightFocus: FocusRequester? = null,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // The selection tint glides between items instead of snapping — the rail "drags".
    val tint by animateColorAsState(
        targetValue = when {
            current -> Deck.primary.copy(alpha = 0.14f)
            focused -> Deck.surfaceVariant
            else -> Color.Transparent
        },
        animationSpec = tween(260, easing = DeckEase),
        label = "railTint",
    )
    val line by animateColorAsState(
        targetValue = if (current || focused) Deck.primary else Color.Transparent,
        animationSpec = tween(220, easing = DeckEase),
        label = "railLine",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(modifier)
            .padding(bottom = 4.dp)
            .size(width = 80.dp, height = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint)
            .border(
                width = 1.dp,
                color = if (focused) Color.Transparent else line,
                shape = RoundedCornerShape(14.dp),
            )
            .deckBringIntoView()
            .deckDragItem("rail:$label")
            .focusProperties { if (rightFocus != null) right = rightFocus }
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (current) Deck.primary else Deck.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (current) Deck.primary else Deck.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (current) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(width = 3.dp, height = 26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Deck.primary),
            )
        }
    }
}

/** The wall fades behind the words, and toward the bottom where the wordmark sits. */
@Composable
private fun DeckWallFade() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to Deck.background.copy(alpha = 0.97f),
                    0.34f to Deck.background.copy(alpha = 0.86f),
                    0.68f to Deck.background.copy(alpha = 0.25f),
                    1f to Deck.background.copy(alpha = 0.1f),
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Deck.background.copy(alpha = 0.9f))),
    )
}

/**
 * Columns of capsules tilted a few degrees, alternate columns drifting up and down. Each column
 * holds its run of covers twice and moves by exactly one run, so the loop has no seam — the
 * geometry and timings are DroidDeck's CapsuleWall.
 */
@Composable
internal fun DeckCapsuleWall(covers: List<DeckCover>, driftMs: Int, modifier: Modifier = Modifier) {
    val capW = 112.dp
    val capH = 168.dp
    val gap = 14.dp
    val transition = rememberInfiniteTransition(label = "deckwall")
    val drift = transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(driftMs, easing = LinearEasing)),
        label = "drift",
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        val tilt = Math.toRadians(13.0)
        val cosTilt = kotlin.math.cos(tilt).toFloat()
        val sinTilt = kotlin.math.sin(tilt).toFloat()
        val wallW = (maxWidth * cosTilt + maxHeight * sinTilt) * 1.1f
        val wallH = maxOf(maxHeight * 2.4f, (maxWidth * sinTilt + maxHeight * cosTilt) * 1.1f)
        val columns = ((wallW + gap) / (capW + gap)).toInt() + 1
        val perRun = ((wallH + gap) / (capH + gap)).toInt() + 1
        val run = (capH + gap) * perRun
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .offset(x = -(wallW - maxWidth) / 2, y = -(wallH - maxHeight) / 2)
                .requiredSize(wallW, wallH)
                .graphicsLayer { rotationZ = -13f },
        ) {
            repeat(columns) { column ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .graphicsLayer {
                            val shift = run.toPx() * drift.value
                            translationY = if (column % 2 == 0) -shift else shift - run.toPx()
                        },
                ) {
                    repeat(perRun * 2) { index ->
                        val cover = covers[(column * 5 + (index % perRun) * 3) % covers.size]
                        DeckCapsule(cover, RoundedCornerShape(10.dp), Modifier.size(capW, capH))
                    }
                }
            }
        }
    }
}

/** One portrait capsule: the real cover art when bundled, otherwise a hue-tinted stand-in. */
@Composable
private fun DeckCapsule(cover: DeckCover, shape: RoundedCornerShape, modifier: Modifier) {
    if (cover.name.isEmpty()) {
        Box(
            modifier
                .clip(shape)
                .background(Brush.linearGradient(listOf(Deck.surface, Deck.background)))
                .border(1.dp, Deck.line, shape),
        )
        return
    }
    val bitmap = cover.bitmap
    if (bitmap != null) {
        Box(
            modifier
                .clip(shape)
                .background(Deck.surface)
                .border(1.dp, Deck.line.copy(alpha = 0.6f), shape),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = cover.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        return
    }
    val hue = cover.hue
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.hsv(hue, 0.55f, 0.85f),
                        Color.hsv((hue + 0.12f) % 1f, 0.70f, 0.55f),
                    ),
                ),
            )
            .border(1.dp, Deck.line.copy(alpha = 0.6f), shape),
    ) {
        Text(
            text = cover.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .alpha(0.92f),
        )
    }
}


/**
 * Console-style intro: the bundled `deck_intro.mp4` plays full-screen once when Deck Mode opens.
 * Tap to skip; auto-dismisses on completion or error.
 */
@Composable
private fun DeckIntro(onDone: () -> Unit) {
    val context = LocalContext.current
    var videoPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, "deck_intro.mp4")
            if (!out.exists()) {
                context.assets.open("deck_intro.mp4").use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
            }
            videoPath = out.absolutePath
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        videoPath?.let { path ->
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setOnCompletionListener { onDone() }
                        setOnErrorListener { _, _, _ -> onDone(); true }
                        isClickable = false
                        setMediaController(null)
                    }
                },
                update = { view ->
                    if (view.tag != path) {
                        view.tag = path
                        view.setVideoPath(path)
                        view.start()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "Tap to skip",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}

/** The intro (wordmark, row rising into place) plays once per process, not on every visit to Home. */
private var homeIntroPlayed = false

/** Clock, battery, a controller icon while a pad is connected, and a downloads badge. */
@Composable
private fun DeckStatusStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        DownloadRegistry.init(context)
        while (true) {
            delay(15_000)
            tick++
        }
    }
    val clock = remember(tick) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date()) }
    val battery = remember(tick) {
        context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
    }
    val pad = remember(tick) {
        InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.let { (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD } == true
        }
    }
    val downloads by DownloadRegistry.entries.collectAsState()
    val active = downloads.count { it.isActive }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (active > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, contentDescription = "Downloads", tint = Deck.primary, modifier = Modifier.size(16.dp))
                Text("$active", color = Deck.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (pad) Icon(Icons.Filled.SportsEsports, contentDescription = "Controller connected", tint = Deck.onSurfaceVariant, modifier = Modifier.size(16.dp))
        if (battery >= 0) Text("$battery%", color = Deck.onSurfaceVariant, fontSize = 12.sp)
        Text(clock, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Which layer of the XMB home is up: the games, one game's quick menu, or the store cards. */
private enum class HomeMode { GAMES, OPTIONS, STORES }

/** One row of the game's quick menu. */
private class DeckMenuAction(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val subtitle: String? = null,
    val nested: Boolean = false,
    val onClick: () -> Unit,
)

/** A card of the row above the games: a store's logo on its colours, or the glass Add game card. */
private class DeckHomeCard(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val logo: Int? = null,
    val glass: Boolean = false,
    val onClick: () -> Unit,
)

private val deckLogoCache = ConcurrentHashMap<Int, ImageBitmap>()

/** A bundled logo decoded off the main thread (the PNGs are large); instant on later visits. */
@Composable
internal fun rememberDeckLogo(res: Int): ImageBitmap? {
    val resources = LocalContext.current.resources
    return produceState<ImageBitmap?>(deckLogoCache[res], res) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = 2 })
                    ?.asImageBitmap()?.also { deckLogoCache[res] = it }
            }
        }
    }.value
}

/** Frosted-glass surface: a light vertical sheen, a soft specular glow and a bright rim. */
private fun Modifier.deckGlass(shape: Shape, strength: Float = 1f): Modifier = this
    .background(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.20f * strength), Color.White.copy(alpha = 0.06f * strength)),
        ),
        shape,
    )
    .background(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.18f * strength), Color.Transparent),
            center = Offset(80f, 0f),
            radius = 420f,
        ),
        shape,
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.22f),
            ),
        ),
        shape,
    )

/** Soft colour blobs under the glass, so the frost has something to refract. */
private fun Modifier.deckGlassBlobs(accent: Color): Modifier = this.drawBehind {
    val a = Offset(size.width * 0.15f, size.height * 0.2f)
    val b = Offset(size.width * 0.9f, size.height * 0.85f)
    val ra = size.width * 0.8f
    val rb = size.width * 0.9f
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), a, ra), ra, a)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF9DB8D6).copy(alpha = 0.55f), Color.Transparent), b, rb), rb, b)
}

/** The liquid-glass face of the Add game card. */
@Composable
private fun DeckGlassAddFace(accent: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.fillMaxSize().deckGlassBlobs(accent), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().deckGlass(shape, 1.4f))
        // A diagonal specular streak across the frost.
        Box(
            Modifier.matchParentSize().background(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.30f to Color.Transparent,
                    0.42f to Color.White.copy(alpha = 0.26f),
                    0.54f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
        )
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add game",
            tint = Color.White,
            modifier = Modifier.fillMaxSize(0.36f),
        )
    }
}

/** The empty library's Add a game button, in the same glass. */
@Composable
private fun DeckGlassPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .deckGlassBlobs(Deck.primary)
            .deckGlass(shape, 1.2f)
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * Places one item of a row (games or cards) from the row's fractional position: the item at [pos] grows
 * about its left edge and the later ones make room, the earlier ones slide off to the left and fade.
 * Everything is read in the graphics layer, so a swipe or a mode change re-draws without recomposing.
 */
private fun Modifier.deckRowLayer(
    index: Int,
    pos: () -> Float,
    scale: () -> Float,
    centerY: () -> Float,
    grow: () -> Float,
    rowAlpha: () -> Float,
    dimOthers: () -> Float,
    baseWPx: Float,
    baseHPx: Float,
    x0Px: Float,
    gapPx: Float,
    /** How much larger the chosen cover is than the rest (0.35 = 35%). */
    growBy: Float = 0.35f,
    /** XMB look: every cover shares the chosen cover's top edge, and the ones before it stay faintly visible. */
    topAlign: Boolean = false,
): Modifier = graphicsLayer {
    val s0 = scale()
    val bw = baseWPx * s0
    val step = bw + gapPx * s0
    val d = index - pos()
    val near = (1f - abs(d)).coerceIn(0f, 1f)
    val g = grow()
    val s = s0 * (1f + growBy * near * g)
    translationX = x0Px + step * d + growBy * bw * d.coerceIn(0f, 1f) * g
    scaleX = s
    scaleY = s
    if (topAlign) {
        // centerY is the chosen cover's middle; the others hang from its top edge.
        translationY = centerY() - baseHPx * s0 * (1f + growBy * g) / 2f
        transformOrigin = TransformOrigin(0f, 0f)
        alpha = ((if (d >= 0f) 0.82f else 0.82f + d * 0.32f) + 0.18f * near * g).coerceIn(0f, 1f) *
            rowAlpha() * (1f - 0.88f * dimOthers() * (1f - near))
    } else {
        translationY = centerY() - baseHPx / 2f
        transformOrigin = TransformOrigin(0f, 0.5f)
        alpha = (1f + d).coerceIn(0f, 1f) * rowAlpha() * (0.78f + 0.22f * near) * (1f - 0.88f * dimOthers() * (1f - near))
    }
}

/**
 * The XMB home (after the AbuNashmi Deck): the games run along a row with the chosen one enlarged; Down folds
 * the row up under a quick menu, Up lifts the store cards (and the glass Add game card) above the games.
 * The whole pane is ONE focus target so the controller never fights per-tile focus; touch swipes, flings and
 * taps drive the same state.
 */
@Composable
private fun DeckHomeCarousel(
    modifier: Modifier = Modifier,
    entries: List<HubEntry>,
    favs: Set<String>,
    details: List<Pair<String, String>>,
    onFavorite: () -> Unit,
    classic: Boolean,
    onMenu: () -> Unit,
    loaded: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    mode: HomeMode,
    onMode: (HomeMode) -> Unit,
    actions: List<DeckMenuAction>,
    cards: List<DeckHomeCard>,
    onAdd: () -> Unit,
    focus: FocusRequester,
    enabled: Boolean,
    xmbRows: Boolean = false,
    /** The XMB dropdown's tools are open as columns beside it: the rows stay but ignore taps. */
    menuHidden: Boolean = false,
    /** XMB look: the pane beside the open dropdown (playtime, genres, description); gets how many description lines fit. */
    info: (@Composable (Int) -> Unit)? = null,
    /** A launch is under way: input is ignored and the chosen cover turns the theme colour and zooms to full screen, [launchT] 0..1. */
    launching: Boolean = false,
    launchT: () -> Float = { 0f },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val accent = Deck.primary
    val n = entries.size
    val view = LocalView.current
    val settle = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)
    var dwell by remember { mutableStateOf(false) }
    val gPos = remember { mutableFloatStateOf(selected.toFloat()) }
    val cPos = remember { mutableFloatStateOf(0f) }
    var cSel by remember { mutableIntStateOf(0) }
    var gJob by remember { mutableStateOf<Job?>(null) }
    var cJob by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var vAcc by remember { mutableFloatStateOf(0f) }
    val menuT = remember { Animatable(0f) }
    val upT = remember { Animatable(0f) }
    val menuPos = remember { Animatable(0f) }
    var menuIdx by remember { mutableIntStateOf(0) }
    var menuVisible by remember { mutableStateOf(false) }

    fun settleGames(i: Int) {
        val t = i.coerceIn(0, (n - 1).coerceAtLeast(0))
        if (t != selected) onSelect(t)
        gJob?.cancel()
        gJob = scope.launch { animate(gPos.floatValue, t.toFloat(), animationSpec = settle) { v, _ -> gPos.floatValue = v } }
    }

    fun settleCards(i: Int) {
        val t = i.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
        cSel = t
        cJob?.cancel()
        cJob = scope.launch { animate(cPos.floatValue, t.toFloat(), animationSpec = settle) { v, _ -> cPos.floatValue = v } }
    }

    // The selection moved from outside (list loaded, game added or removed): glide to it.
    LaunchedEffect(selected, n) {
        if (!dragging && gJob?.isActive != true && abs(gPos.floatValue - selected) > 0.01f) {
            animate(gPos.floatValue, selected.toFloat(), animationSpec = settle) { v, _ -> gPos.floatValue = v }
        }
    }
    LaunchedEffect(mode) {
        if (mode == HomeMode.OPTIONS) {
            menuIdx = 0
            menuPos.snapTo(0f)
            menuVisible = true
        }
        // Up always lands on the first card, and the peeking row eases back to its start when it closes.
        if (mode != HomeMode.STORES) {
            cJob?.cancel()
            cSel = 0
            cJob = launch { animate(cPos.floatValue, 0f, animationSpec = settle) { v, _ -> cPos.floatValue = v } }
        }
        launch { upT.animateTo(if (mode == HomeMode.STORES) 1f else 0f, settle) }
        menuT.animateTo(if (mode == HomeMode.OPTIONS) 1f else 0f, settle)
        if (mode != HomeMode.OPTIONS) menuVisible = false
    }
    LaunchedEffect(menuIdx) { menuPos.animateTo(menuIdx.toFloat(), tween(180, easing = DeckEase)) }

    // A tick for every move, a firmer one for select/layer changes: view haptics, the system click
    // sound, and a short rumble on the pad that sent the key.
    fun buzz(deviceId: Int, strong: Boolean) {
        view.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
        DeckSound.play(if (strong) DeckSound.SELECT else DeckSound.MOVE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && deviceId >= 0) {
            InputDevice.getDevice(deviceId)?.vibratorManager?.defaultVibrator
                ?.vibrate(VibrationEffect.createOneShot(if (strong) 40L else 15L, if (strong) 160 else 70))
        }
    }

    // Once the selection rests for a moment, the details panel fades in.
    LaunchedEffect(selected, mode) {
        dwell = false
        if (mode == HomeMode.GAMES) {
            delay(900)
            dwell = true
        }
    }

    fun handleKey(e: KeyEvent): Boolean {
                    if (!enabled || e.type != KeyEventType.KeyDown) return false
                    if (launching) return true
                    val fresh = e.nativeKeyEvent.repeatCount == 0
                    val confirm = e.key == Key.ButtonA || e.key == Key.Enter ||
                        e.key == Key.NumPadEnter || e.key == Key.DirectionCenter
                    if (n == 0) {
                        if (confirm && loaded) {
                            if (fresh) onAdd()
                            return true
                        }
                        return false
                    }
                    return when (mode) {
                        HomeMode.GAMES -> when {
                            e.key == Key.ButtonY -> { if (classic) { if (fresh) onMenu() } else onFavorite(); true }
                            e.key == Key.DirectionLeft -> if (selected > 0) { settleGames(selected - 1); true } else false
                            e.key == Key.DirectionRight -> { if (selected < n - 1) settleGames(selected + 1); true }
                            // XMB: A opens the dropdown and Down has nowhere to go; Classic: A plays.
                            e.key == Key.DirectionDown -> true
                            e.key == Key.DirectionUp -> { onMode(HomeMode.STORES); true }
                            confirm -> {
                                if (fresh) { if (classic) actions.firstOrNull()?.onClick?.invoke() else onMode(HomeMode.OPTIONS) }
                                true
                            }
                            else -> false
                        }
                        HomeMode.OPTIONS -> when {
                            e.key == Key.DirectionUp -> { if (menuIdx > 0) menuIdx-- else onMode(HomeMode.GAMES); true }
                            e.key == Key.DirectionDown -> { if (menuIdx < actions.lastIndex) menuIdx++; true }
                            // XMB: Left/Right change the game under the open dropdown, which starts again on its first row.
                            e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> {
                                if (!classic) {
                                    settleGames(if (e.key == Key.DirectionLeft) selected - 1 else selected + 1)
                                    menuIdx = 0
                                }
                                true
                            }
                            confirm -> {
                                if (fresh) {
                                    val action = actions.getOrNull(menuIdx)
                                    // A tool's columns open beside the dropdown, so it stays open then.
                                    if (!(xmbRows && action?.nested == true)) onMode(HomeMode.GAMES)
                                    action?.onClick?.invoke()
                                }
                                true
                            }
                            e.key == Key.ButtonB || e.key == Key.Escape -> { onMode(HomeMode.GAMES); true }
                            else -> false
                        }
                        HomeMode.STORES -> when {
                            e.key == Key.DirectionLeft -> { settleCards(cSel - 1); true }
                            e.key == Key.DirectionRight -> { settleCards(cSel + 1); true }
                            e.key == Key.DirectionDown || e.key == Key.ButtonB || e.key == Key.Escape -> { onMode(HomeMode.GAMES); true }
                            e.key == Key.DirectionUp -> true
                            confirm -> { if (fresh) cards.getOrNull(cSel)?.onClick?.invoke(); true }
                            else -> false
                        }
                    }
    }

    val cardShape = RoundedCornerShape(4.dp)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The store cards' size, and the covers' in Classic.
        val baseH = (maxHeight * 0.34f).coerceIn(96.dp, 168.dp)
        val baseW = baseH * 2f / 3f
        val x0 = 28.dp
        // XMB look (everything but Classic): the XMB home's own numbers, in dp. The chosen cover is fH tall and the
        // rest sH, all hanging from its top edge; at rest the row is drawn up to 1.3x larger, as big as fits under the
        // cards' peek, and the dropdown folds it back to 1x with the options column under the chosen cover.
        val xl = !classic
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val port = hDp > wDp * 1.05f
        val fHDp = if (port) minOf(wDp * 0.56f, hDp * 0.31f) else (hDp * 0.40f).coerceIn(96f, 300f)
        val fWDp = fHDp * 2f / 3f
        val sHDp = fHDp * 0.62f
        val sWDp = sHDp * 2f / 3f
        val gapDp = (fHDp * 0.09f).roundToInt().toFloat()
        // At rest the row is drawn larger: the chosen cover takes 56% of the height (44% in portrait), at most 1.4x the dropdown's size.
        val homeK = minOf(if (port) 1.3f else 1.4f, (if (port) hDp * 0.44f else hDp * 0.56f) / fHDp).coerceAtLeast(1f)
        // The cover before the chosen one stays on screen at the larger size too.
        val crossDp = if (port) 16f + sWDp * 0.42f + gapDp else maxOf(16f + (sWDp + gapDp) * homeK, wDp * 0.13f)
        val optsTopDp = if (port) 34f else (hDp * 0.25f - sHDp / 2f).coerceAtMost(hDp - fHDp - 12f - 3 * 40f - 6f - 12f).coerceAtLeast(34f)
        val rowH = if (xl && port) 46.dp else 40.dp
        val nVis = if (port) ((hDp * 0.38f) / rowH.value).toInt().coerceIn(3, 8)
            else maxOf(2, ((hDp - (optsTopDp + fHDp + 12f) - 12f - 6f) / rowH.value).toInt())
        val titleHDp = fHDp - sHDp - 8f
        val titleLines = if (titleHDp > 58f) 2 else 1
        val titleSp = (if (titleLines == 2) (titleHDp - 18f) / 2.4f else titleHDp - 22f).coerceIn(15f, 26f)
        val gBaseH = if (xl) (sHDp * homeK).dp else baseH
        val gBaseW = gBaseH * 2f / 3f
        val growBy = if (xl) fHDp / sHDp - 1f else 0.35f
        val hPx = with(density) { maxHeight.toPx() }
        val baseWPx = with(density) { baseW.toPx() }
        val baseHPx = with(density) { baseH.toPx() }
        val x0Px = with(density) { x0.toPx() }
        val gapPx = with(density) { 14.dp.toPx() }
        val gBaseWPx = with(density) { gBaseW.toPx() }
        val gBaseHPx = with(density) { gBaseH.toPx() }
        val gX0Px = with(density) { (if (xl) crossDp.dp else x0).toPx() }
        val gGapPx = with(density) { (if (xl) gapDp * homeK else 14f).dp.toPx() }
        val extraPx = with(density) { 6.dp.toPx() }
        // The options column's left edge: its 34 dp icon box sits centred under the chosen cover (less the row's own 8 dp padding).
        val menuX = with(density) { if (xl) (crossDp + fWDp / 2f - 17f - 8f).dp.toPx() else x0Px }
        // Quick menu layout: the row folds to half size at the top, the menu hangs under the chosen cover.
        // The XMB dropdown folds the row less (the Deck XMB keeps its cover large) and hides the neighbours.
        val menuScale = if (xl) 1f / homeK else if (xmbRows) 0.95f else 0.5f
        // The XMB dropdown shows four rows; the cover and the rows are centred as one block so the cover isn't pushed to the top.
        val topMargin = if (xmbRows) ((maxHeight - baseH * menuScale - 12.dp - rowH * 4) / 2).coerceIn(14.dp, 60.dp) else 14.dp
        val optsY = if (xl) with(density) { (optsTopDp + fHDp / 2f).dp.toPx() }
            else baseHPx * (menuScale / 2f) + with(density) { topMargin.toPx() }
        val menuTopPx = if (xl) with(density) { (optsTopDp + fHDp + 12f).dp.toPx() }
            else optsY + baseHPx * (menuScale / 2f) + with(density) { 12.dp.toPx() }
        val titleMax = (
            if (xl) maxWidth - (crossDp + homeK * (fWDp + 16f)).dp - 16.dp
            else maxWidth - x0 - baseW * 1.35f - 40.dp
        ).let { it - (if (maxWidth > 560.dp) 230.dp else 0.dp) }.coerceAtLeast(120.dp)

        val gScale = { 1f - (1f - menuScale) * menuT.value - 0.45f * upT.value }
        val gCenter = { hPx / 2f + (optsY - hPx / 2f) * menuT.value + (hPx / 2f) * upT.value }
        val gGrow = { 1f - upT.value }
        val gAlpha = { 1f - 0.4f * upT.value }
        val gDim = { if (xl) 0f else (if (xmbRows) 1.13f else 0.57f) * menuT.value }
        val gPosNow = { gPos.floatValue }
        val cScale = { 0.55f + 0.45f * upT.value }
        val cCenter = { 2f + (hPx / 2f - 2f) * upT.value }
        val cAlpha = { (0.5f + 0.5f * upT.value) * (1f - menuT.value) }
        val cGrow = { upT.value }
        val noDim = { 0f }
        val cPosNow = { cPos.floatValue }

        val hDrag = rememberDraggableState { delta ->
            if (mode == HomeMode.STORES) {
                cJob?.cancel()
                val p = (cPos.floatValue - delta / ((baseWPx + gapPx) * cScale()))
                    .coerceIn(-0.45f, cards.size - 1 + 0.45f)
                cPos.floatValue = p
                cSel = p.roundToInt().coerceIn(0, cards.size - 1)
            } else if (mode == HomeMode.GAMES && n > 0) {
                gJob?.cancel()
                val p = (gPos.floatValue - delta / ((gBaseWPx + gGapPx) * gScale())).coerceIn(-0.45f, n - 1 + 0.45f)
                gPos.floatValue = p
                val r = p.roundToInt().coerceIn(0, n - 1)
                if (r != selected) onSelect(r)
            }
        }
        val vDrag = rememberDraggableState { d ->
            vAcc += d
            // XMB rows: a vertical drag in the dropdown scrolls it, a row at a time.
            if (xmbRows && mode == HomeMode.OPTIONS) {
                val step = with(density) { rowH.toPx() }
                while (vAcc <= -step) { vAcc += step; menuIdx = (menuIdx + 1).coerceAtMost(actions.lastIndex) }
                while (vAcc >= step) { vAcc -= step; menuIdx = (menuIdx - 1).coerceAtLeast(0) }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .focusProperties { canFocus = enabled }
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    handleKey(e).also { handled ->
                        if (handled && e.type == KeyEventType.KeyDown) {
                            buzz(e.nativeKeyEvent.deviceId, e.key != Key.DirectionLeft && e.key != Key.DirectionRight)
                        }
                    }
                }
                .focusable()
                .draggable(
                    hDrag, Orientation.Horizontal,
                    onDragStarted = { dragging = true },
                    onDragStopped = { velocity ->
                        dragging = false
                        if (mode == HomeMode.STORES) {
                            settleCards((cPos.floatValue - velocity / ((baseWPx + gapPx) * cScale()) * 0.14f).roundToInt())
                        } else if (mode == HomeMode.GAMES && n > 0) {
                            settleGames((gPos.floatValue - velocity / ((gBaseWPx + gGapPx) * gScale()) * 0.14f).roundToInt())
                        }
                    },
                )
                .draggable(
                    vDrag, Orientation.Vertical,
                    onDragStarted = { vAcc = 0f },
                    onDragStopped = {
                        val th = with(density) { 40.dp.toPx() }
                        if (xmbRows && mode == HomeMode.OPTIONS) {
                            // scrolled the dropdown; leave it open
                        } else if (vAcc < -th) {
                            if (mode == HomeMode.GAMES && n > 0) onMode(HomeMode.STORES)
                            else if (mode == HomeMode.OPTIONS) onMode(HomeMode.GAMES)
                        } else if (vAcc > th) {
                            if (mode == HomeMode.GAMES && n > 0) { if (!classic) onMode(HomeMode.OPTIONS) }
                            else if (mode == HomeMode.STORES) onMode(HomeMode.GAMES)
                        }
                    },
                )
                .pointerInput(mode) { detectTapGestures { if (mode != HomeMode.GAMES) onMode(HomeMode.GAMES) } },
        ) {
            // The store cards (and Add game) above the games: small and half-hidden until Up lifts them.
            if (n > 0) {
                cards.forEachIndexed { i, card ->
                    key(card.label) {
                        Box(
                            Modifier
                                .size(baseW, baseH)
                                .deckRowLayer(i, cPosNow, cScale, cCenter, cGrow, cAlpha, noDim, baseWPx, baseHPx, gX0Px, gapPx)
                                .clip(cardShape)
                                .background(Brush.linearGradient(card.colors))
                                .drawWithContent {
                                    drawContent()
                                    val w = (1f - abs(i - cPos.floatValue)).coerceIn(0f, 1f) * upT.value
                                    val sw = 1.dp.toPx() + 1.5.dp.toPx() * w
                                    drawRoundRect(
                                        color = if (w > 0.02f) accent.copy(alpha = w) else Color.White.copy(alpha = 0.14f),
                                        topLeft = Offset(sw / 2f, sw / 2f),
                                        size = Size(size.width - sw, size.height - sw),
                                        cornerRadius = CornerRadius(4.dp.toPx()),
                                        style = Stroke(width = sw),
                                    )
                                }
                                .pointerInput(i, mode, cSel) {
                                    detectTapGestures {
                                        when (mode) {
                                            HomeMode.GAMES -> onMode(HomeMode.STORES)
                                            HomeMode.STORES -> if (i != cSel) settleCards(i) else card.onClick()
                                            HomeMode.OPTIONS -> onMode(HomeMode.GAMES)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val logo = card.logo?.let { rememberDeckLogo(it) }
                            when {
                                card.glass -> DeckGlassAddFace(accent)
                                logo != null -> Image(
                                    bitmap = logo,
                                    contentDescription = card.label,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(0.62f),
                                )
                                else -> Icon(
                                    card.icon,
                                    contentDescription = card.label,
                                    tint = Color.White,
                                    modifier = Modifier.fillMaxSize(0.44f),
                                )
                            }
                        }
                    }
                }
            }

            val lo = (selected - 2).coerceAtLeast(0)
            val hi = (selected + 12).coerceAtMost(n - 1)
            for (i in lo..hi) {
                val entry = entries[i]
                key(i) {
                    Box(
                        Modifier
                            .size(gBaseW, gBaseH)
                            .deckRowLayer(i, gPosNow, gScale, gCenter, gGrow, gAlpha, gDim, gBaseWPx, gBaseHPx, gX0Px, gGapPx, growBy, xl)
                            // The chosen cover glows in the theme colour: a soft halo behind it plus a coloured shadow.
                            .drawBehind {
                                val gl = (1f - abs(i - gPos.floatValue)).coerceIn(0f, 1f) * (1f - upT.value)
                                if (gl > 0.02f) {
                                    val radius = maxOf(size.width, size.height) * 0.68f
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(accent.copy(alpha = 0.4f * gl), Color.Transparent),
                                            center = center, radius = radius,
                                        ),
                                        radius = radius, center = center,
                                    )
                                }
                            }
                            .graphicsLayer {
                                val gl = (1f - abs(i - gPos.floatValue)).coerceIn(0f, 1f) * (1f - upT.value)
                                shadowElevation = 12.dp.toPx() * gl
                                ambientShadowColor = accent
                                spotShadowColor = accent
                                shape = cardShape
                                clip = false
                            }
                            .clip(cardShape)
                            .background(Deck.surface)
                            .drawWithContent {
                                drawContent()
                                val near = (1f - abs(i - gPos.floatValue)).coerceIn(0f, 1f)
                                val w = near * (1f - upT.value)
                                if (w > 0.02f) {
                                    // The layer is drawn scaled: keep the ring ~3 dp on screen and flush with the clipped edge.
                                    val sc = (gScale() * (1f + growBy * near * gGrow())).coerceAtLeast(0.1f)
                                    val sw = 3.dp.toPx() / sc
                                    drawRoundRect(
                                        color = accent.copy(alpha = w),
                                        topLeft = Offset(sw / 2f, sw / 2f),
                                        size = Size(size.width - sw, size.height - sw),
                                        cornerRadius = CornerRadius((4.dp.toPx() - sw / 2f).coerceAtLeast(0f)),
                                        style = Stroke(width = sw),
                                    )
                                }
                            }
                            .pointerInput(i, selected, mode) {
                                detectTapGestures(
                                    onLongPress = {
                                        if (classic) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            if (i != selected) settleGames(i)
                                            onMenu()
                                        }
                                    },
                                    onTap = {
                                        when {
                                            i != selected -> {
                                                if (mode != HomeMode.GAMES) onMode(HomeMode.GAMES)
                                                settleGames(i)
                                            }
                                            // Classic: a tap on the chosen cover plays it; the menu is on hold / Y.
                                            classic -> actions.firstOrNull()?.onClick?.invoke()
                                            mode == HomeMode.GAMES -> onMode(HomeMode.OPTIONS)
                                            else -> onMode(HomeMode.GAMES)
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = entry.bitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        } else {
                            Text(
                                entry.title,
                                color = Deck.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }

            if (n > 0) {
                // The chosen game's name, right of its cover under the rest of the row; beside the cover in the menu.
                AnimatedContent(
                    targetState = selected.coerceIn(0, n - 1),
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(90)) },
                    label = "homeGameTitle",
                    modifier = Modifier
                        .offset {
                            if (xl) {
                                // Right of the chosen cover, under the row of the others, in the dropdown too.
                                val k = homeK * gScale()
                                val coverTop = gCenter() - gBaseHPx * gScale() * (1f + growBy * gGrow()) / 2f
                                IntOffset(
                                    (gX0Px + k * (fWDp + 16f).dp.toPx()).roundToInt(),
                                    (coverTop + gBaseHPx * gScale() + 8.dp.toPx() * k).roundToInt(),
                                )
                            } else {
                                val below = gCenter() + baseHPx * gScale() * 0.5f + 8.dp.toPx()
                                IntOffset(
                                    (x0Px + baseWPx * 1.35f * gScale() + 16.dp.toPx()).roundToInt(),
                                    (below + (gCenter() - 22.dp.toPx() - below) * menuT.value).roundToInt(),
                                )
                            }
                        }
                        .widthIn(max = titleMax)
                        // The tools' columns open where the title sits: it steps aside while one is open.
                        .graphicsLayer { alpha = (1f - upT.value) * (if (menuHidden) 0f else 1f) },
                ) { idx ->
                    val entry = entries.getOrNull(idx)
                    if (entry != null) {
                        val playtime = remember(entry) { hubPlaytimeLabel(context, entry.title) }
                        Column {
                            if (idx == 0 && entry.game != null && hubHasPlaytime(context, entry.title) && (!xl || titleLines == 2)) {
                                Text(
                                    "CONTINUE PLAYING",
                                    color = accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.8.sp,
                                )
                            }
                            Text(
                                (if (entry.game?.shortcutPath in favs) "\u2605 " else "") + entry.title,
                                color = Color.White,
                                fontSize = if (xl) titleSp.sp else 22.sp,
                                lineHeight = if (xl) (titleSp * 1.15f).sp else 25.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = if (xl) titleLines else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                hubOriginLabel(entry).uppercase() + "  ·  " +
                                    if (playtime == "not played yet") playtime else "$playtime played",
                                color = Deck.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // The chosen card's name and details, the same way, once the cards are up.
                AnimatedContent(
                    targetState = cSel,
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(90)) },
                    label = "homeCardTitle",
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (gX0Px + baseWPx * 1.35f * cScale() + 16.dp.toPx()).roundToInt(),
                                (cCenter() + baseHPx * cScale() * 0.5f + 8.dp.toPx()).roundToInt(),
                            )
                        }
                        .widthIn(max = titleMax)
                        .graphicsLayer { alpha = upT.value },
                ) { idx ->
                    val card = cards.getOrNull(idx)
                    if (card != null) {
                        Column {
                            Text(
                                card.label,
                                color = Color.White,
                                fontSize = 22.sp,
                                lineHeight = 25.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                card.subtitle,
                                color = Deck.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (xl && info != null && menuVisible) {
                // Right of the options column (under it in portrait), faded in with the dropdown.
                val colTopDp = optsTopDp + fHDp + 12f
                val infoXDp = if (port) 16f else crossDp + fWDp / 2f - 17f + 34f + 12f + 176f + 12f
                val infoYDp = if (port) colTopDp + nVis * rowH.value + 6f + 10f else colTopDp
                val infoWDp = if (port) wDp - 32f else wDp - infoXDp - 24f
                val infoHDp = if (port) hDp - infoYDp - 84f else hDp - infoYDp - 36f
                if (infoWDp >= 120f && infoHDp >= 40f) {
                    Column(
                        Modifier
                            .offset(infoXDp.dp, infoYDp.dp)
                            .width(infoWDp.dp)
                            .height(infoHDp.dp)
                            .graphicsLayer { alpha = menuT.value }
                            .clipToBounds(),
                    ) { info(if (port) 6 else 3) }
                }
            }

            if (menuVisible) {
                actions.forEachIndexed { idx, action ->
                    val focused = idx == menuIdx
                    val tint = if (action.danger) DeckPalette.error else Deck.onSurfaceVariant
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .graphicsLayer {
                                val rel = idx - menuPos.value
                                translationX = menuX - (1f - menuT.value) * 12.dp.toPx()
                                translationY = menuTopPx + rel * rowH.toPx() + (if (xl) extraPx * rel.coerceIn(0f, 1f) else 0f)
                                // Rows already passed fade out above the focused one, like the XMB.
                                // The XMB dropdown keeps clear of the cover: rows scrolled past vanish at its top edge, and only
                                // nVis rows show below, each a little fainter (the picked one has a gap under it).
                                alpha = if (xl) {
                                    menuT.value * (1f + 3f * rel).coerceIn(0f, 1f) *
                                        (if (rel > 0f) maxOf(0.35f, 1f - 0.13f * rel) else 1f) *
                                        ((menuTopPx + nVis * rowH.toPx() + extraPx - translationY) / rowH.toPx()).coerceIn(0f, 1f)
                                } else {
                                    menuT.value *
                                        (if (xmbRows) (1f + 3f * rel).coerceIn(0f, 1f) else (1f + rel).coerceIn(0f, 1f)) *
                                        (if (xmbRows) ((menuTopPx + 4f * rowH.toPx() - translationY) / rowH.toPx()).coerceIn(0f, 1f) else 1f)
                                }
                            }
                            .height(rowH)
                            .clip(RoundedCornerShape(10.dp))
                            .pointerInput(idx, menuHidden) {
                                // Taps wait while one of the tools' columns is open beside the dropdown.
                                if (!menuHidden) detectTapGestures {
                                    menuIdx = idx
                                    if (!(xmbRows && action.nested)) onMode(HomeMode.GAMES)
                                    action.onClick()
                                }
                            }
                            .padding(horizontal = 8.dp),
                    ) {
                        if (xmbRows) XmbOptionRow(
                            action.label, action.icon, action.subtitle, action.danger, action.nested, focused, accent,
                        ) else {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (focused) accent else Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = if (focused && !action.danger) Deck.onPrimary else tint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            action.label,
                            color = if (action.danger) DeckPalette.error else if (focused) Color.White else Deck.onSurfaceVariant,
                            fontSize = if (focused) 16.sp else 14.sp,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = dwell && mode == HomeMode.GAMES && details.isNotEmpty(),
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 },
                exit = fadeOut(tween(120)),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 44.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .width(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Deck.surface.copy(alpha = 0.78f))
                        .border(1.dp, Deck.line, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    details.forEach { (k, v) ->
                        Column {
                            Text(k.uppercase(), color = Deck.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.2.sp)
                            Text(v, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (loaded && n == 0) DeckGlassPill("Add a game", onAdd, Modifier.align(Alignment.Center))

            Text(
                text = when {
                    n == 0 -> if (loaded) "A  Add a game" else ""
                    mode == HomeMode.OPTIONS -> "A  Select      B  Back"
                    mode == HomeMode.STORES -> "A  Add game      B  Back"
                    classic -> "A  Play    ↑  Add game    Y  Menu    L2 R2  Shelves"
                    else -> "A  Options    ↑  Add game    Y  Favourite    L2 R2  Shelves"
                },
                color = Deck.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 12.dp),
            )

            if (launching) {
                // Eats every touch so nothing underneath reacts while the game starts.
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        },
                ) {
                    // A sheet of the theme colour that starts as the chosen cover (covering it within the first moments),
                    // then grows to the whole screen. Only its layer transform changes, so every frame stays cheap.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val t = launchT()
                                val f = 1f + growBy * gGrow()
                                val cw = gBaseWPx * gScale() * f
                                val ch = gBaseHPx * gScale() * f
                                val left = gX0Px + (gBaseWPx + gGapPx) * gScale() * (selected - gPos.floatValue)
                                val top = gCenter() - ch / 2f
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = lerp(cw / size.width, 1f, t)
                                scaleY = lerp(ch / size.height, 1f, t)
                                translationX = lerp(left, 0f, t)
                                translationY = lerp(top, 0f, t)
                                alpha = (t * 6f).coerceIn(0f, 1f)
                                // The layer is stretched unevenly; undo it on average so the corners stay round.
                                shape = RoundedCornerShape((4.dp.toPx() * (1f - t) / ((scaleX + scaleY) / 2f)).coerceAtLeast(0f))
                                clip = true
                            }
                            .background(accent),
                    )
                }
            }
        }
    }
}

/**
 * Deck Mode reports [InputMode.Keyboard] no matter what Android's touch mode says, so Compose's
 * non-touch-focusable modifiers (`clickable`, `combinedClickable`, …) stay reachable with the
 * controller after the user touches the screen. Input-mode requests are still forwarded to the
 * real manager so keyboard/IME side effects behave normally.
 */
@OptIn(ExperimentalComposeUiApi::class)
private class DeckInputModeManager(
    private val delegate: InputModeManager,
) : InputModeManager {
    override val inputMode: InputMode get() = InputMode.Keyboard

    override fun requestInputMode(inputMode: InputMode): Boolean =
        delegate.requestInputMode(inputMode)
}

private fun isPadKey(k: Key): Boolean = when (k) {
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter,
    Key.ButtonA, Key.ButtonB, Key.ButtonX, Key.ButtonY, Key.ButtonL1, Key.ButtonR1, Key.ButtonL2, Key.ButtonR2,
    Key.ButtonStart, Key.ButtonSelect, Key.ButtonThumbLeft, Key.ButtonThumbRight -> true
    else -> false
}
