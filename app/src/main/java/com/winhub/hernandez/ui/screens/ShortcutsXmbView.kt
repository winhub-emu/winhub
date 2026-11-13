package com.winhub.hernandez.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.InputDevice
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleEventObserver
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.inputcontrols.ExternalController
import com.winhub.hernandez.store.EpicMainActivity
import com.winhub.hernandez.store.GogMainActivity
import com.winhub.hernandez.store.SteamMainActivity
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.Screen
import com.winhub.hernandez.ui.SocialHubScreen
import com.winhub.hernandez.ui.screens.contents.ContentsHubScreen
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.theme.DangerRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One row of the XMB options column: the same entries as a game card's ⋮ menu. */
internal data class XmbAction(
    val label: String,
    val icon: ImageVector,
    val subtitle: String? = null,
    val danger: Boolean = false,
    /** Opens a nested XMB column (Settings, Game Details, tools…) instead of running [onClick]. */
    val menu: ((XmbScope) -> XmbMenu)? = null,
    val onClick: () -> Unit = {},
)

/**
 * Games-tab XMB layout (PS3 cross media bar). Games run along a horizontal bar and the focused
 * one sits enlarged at the "cross"; the column under it holds that game's options, with Play first.
 * Every size is derived from the content box, so portrait and landscape are the same code; a
 * rotation only re-measures. Touch: swipe the bar (with fling), tap a cover to focus it, tap the
 * focused cover to launch, swipe or tap the column. Controller / D-pad: Left/Right games, Down
 * opens the options band at the bottom (Up closes it back to the cover), A select, B back, and
 * L1/R1 move between the four store/menu squares pinned to the top-right corner.
 */
@Composable
internal fun ShortcutsXmbView(
    shortcuts: List<Shortcut>,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    onToggleSelect: (Shortcut) -> Unit,
    onExitSelection: () -> Unit,
    onPlay: (Shortcut) -> Unit,
    actionsFor: (Shortcut) -> List<XmbAction>,
    sdBadge: @Composable (Shortcut) -> Unit,
    /** The game list changed (rename, clone, remove, new exe…): reload it. */
    onReloadGames: () -> Unit = {},
    /** True while a nested menu is open (the Games tab hides its + button). */
    onNestedChange: (Boolean) -> Unit = {},
    /** Open one of the drawer destinations (Containers, File Manager, Social Hub…) from the menu square. */
    onNavigate: ((Screen) -> Unit)? = null,
) {
    if (shortcuts.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val n = shortcuts.size

    // The focused game is remembered by path so it survives rotation, leaving the tab, and a
    // refresh or sort that reorders the list.
    var focusedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var target by remember { mutableIntStateOf(shortcuts.indexOfFirst { it.file.path == focusedPath }.coerceAtLeast(0)) }
    val pos = remember { mutableFloatStateOf(target.toFloat()) } // visual position, fractional mid-swipe
    // -1 = nothing picked in the options band (the cover itself is "focused", so A launches);
    // 0..n = a row of the bottom band is highlighted. [sq] is the top-right square strip (-1 = off).
    var act by remember { mutableIntStateOf(-1) }
    var sq by remember { mutableIntStateOf(-1) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(shortcuts) {
        val i = shortcuts.indexOfFirst { it.file.path == focusedPath }
        val idx = if (i >= 0) i else target.coerceIn(0, n - 1)
        settleJob?.cancel()
        target = idx
        pos.floatValue = idx.toFloat()
    }
    val focused = shortcuts[target.coerceIn(0, n - 1)]
    LaunchedEffect(focused.file.path) { focusedPath = focused.file.path }

    // Moving to another game always lands back on Play, so A launches (like tapping a card).
    fun settleTo(i: Int) {
        val t = i.coerceIn(0, n - 1)
        if (t != target) { target = t; act = -1 }
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(pos.floatValue, t.toFloat(), animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)) { v, _ ->
                pos.floatValue = v
            }
        }
    }

    val playtime = remember(focused) { xmbPlaytime(context, focused.name) }
    val isSelected = focused.file.path in selectedPaths
    val actions = buildList<XmbAction> {
        add(
            if (selectionMode) XmbAction(if (isSelected) "Deselect" else "Select", Icons.Filled.Checklist) { onToggleSelect(focused) }
            else XmbAction("Play", Icons.Filled.PlayArrow, playtime?.let { "$it played" } ?: "Not played yet") { onPlay(focused) }
        )
        addAll(actionsFor(focused))
    }
    val actIdx = act.coerceIn(0, actions.lastIndex)

    // Nested XMB menus (Settings, Game Details, tools…): model in XmbModel.kt, drawing in XmbMenuHost.kt.
    val nav = remember { XmbNavState() }
    val nested = nav.depth > 0
    val reload by rememberUpdatedState(onReloadGames)
    val appCtx = context
    val coScope = scope
    val xmbScope = remember(nav) {
        object : XmbScope {
            override val context: Context get() = appCtx
            override val scope: kotlinx.coroutines.CoroutineScope get() = coScope
            override fun push(menu: XmbMenu) = nav.push(menu)
            override fun pop() = nav.pop()
            override fun popToRoot() = nav.popTo(0)
            override fun refresh() = nav.bump()
            override fun toast(message: String) { Toast.makeText(appCtx, message, Toast.LENGTH_SHORT).show() }
            override fun saved() { nav.savedAt = System.currentTimeMillis() }
            override fun confirm(confirm: XmbConfirm, onOk: () -> Unit) = nav.push(xmbConfirmMenu(nav, confirm, onOk))
            override fun reloadGames() = reload()
        }
    }
    fun runAction(a: XmbAction) { val mk = a.menu; if (mk != null) nav.push(mk(xmbScope)) else a.onClick() }
    // The four top-right squares: three store fronts, then the app menu (drawer destinations).
    fun runSquare(i: Int) {
        sq = -1
        when (i) {
            0 -> context.startActivity(Intent(context, SteamMainActivity::class.java))
            1 -> context.startActivity(Intent(context, GogMainActivity::class.java))
            2 -> context.startActivity(Intent(context, EpicMainActivity::class.java))
            else -> onNavigate?.let { go ->
                nav.push(xmbAppMenu(go) { id ->
                    nav.push(
                        screenMenu(Screen.ContainerDetail) {
                            ContainerDetailScreen(
                                containerId = id ?: -1,
                                onNavigateBack = { nav.pop() },
                            )
                        }
                    )
                })
            }
        }
    }
    LaunchedEffect(nested) { onNestedChange(nested) }
    DisposableEffect(Unit) { onDispose { onNestedChange(false) } }
    BackHandler(enabled = nested) { nav.pop() }

    // Take D-pad focus on entry and again whenever the app comes back (e.g. after a game exits).
    val focusRequester = remember { FocusRequester() }
    nav.refocus = { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) runCatching { focusRequester.requestFocus() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Landscape XMB is a couch front end: the Android status bar and nav buttons are hidden (an edge
    // swipe brings them back briefly). Shown again on rotating to portrait, switching view, or leaving the tab.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    DisposableEffect(landscape, lifecycleOwner) {
        val window = context.findActivity()?.window
        val bars = if (landscape && window != null) WindowCompat.getInsetsController(window, view) else null
        val hideNav = {
            bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        }
        hideNav()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hideNav() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // Space under a see-through top bar: the backdrop fills it, the bar/cover layout starts below it.
    val topInset = LocalTopBarOverlayInset.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000L) } }
    val clock = remember(now / 60_000L) {
        DateUtils.formatDateTime(
            context, now,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_ALL,
        ) + "  " + DateUtils.formatDateTime(context, now, DateUtils.FORMAT_SHOW_TIME)
    }
    val hasPad = rememberGamepadConnectedLegacy()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val m = remember(maxWidth, maxHeight, topInset) { XmbMetrics(maxWidth.value, maxHeight.value - topInset.value) }
        val pitchPx = with(density) { m.pitch.dp.toPx() }
        val rowPx = with(density) { m.rowH.dp.toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        var swipe by remember { mutableFloatStateOf(0f) }
        val swipeBackPx = with(density) { 60.dp.toPx() }
        val hDrag = rememberDraggableState { delta ->
            if (nav.depth > 0) { swipe += delta; return@rememberDraggableState }
            settleJob?.cancel()
            val p = (pos.floatValue - delta / pitchPx).coerceIn(-0.45f, n - 1 + 0.45f)
            pos.floatValue = p
            val r = p.roundToInt().coerceIn(0, n - 1)
            if (r != target) { target = r; act = -1 }
        }
        var vAcc by remember { mutableFloatStateOf(0f) }
        val vDrag = rememberDraggableState { d ->
            if (nav.depth > 0) return@rememberDraggableState
            vAcc += d
            val step = rowPx * 0.9f
            while (vAcc <= -step) { vAcc += step; act = (act + 1).coerceIn(0, actions.lastIndex) }
            while (vAcc >= step) { vAcc -= step; act = (act - 1).coerceAtLeast(-1) }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (nav.depth > 0) {
                        // Typing into a Text row: the field gets the keys; A = done, B = cancel.
                        if (nav.editingKey != null) {
                            return@onPreviewKeyEvent when (e.key) {
                                Key.ButtonA, Key.DirectionCenter -> { nav.commitEdit(xmbScope); true }
                                Key.ButtonB, Key.Back -> { nav.pop(); true }
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
                        return@onPreviewKeyEvent nav.onKey(k, xmbScope)
                    }
                    when (e.key) {
                        Key.DirectionLeft -> { sq = -1; settleTo(target - 1); true }
                        Key.DirectionRight -> { sq = -1; settleTo(target + 1); true }
                        // L1/R1 belong to the store/menu square strip (top-right); scroll with D-pad.
                        Key.ButtonL1 -> { sq = if (sq < 0) 0 else (sq + SQUARE_COUNT - 1) % SQUARE_COUNT; true }
                        Key.ButtonR1 -> { sq = if (sq < 0) 0 else (sq + 1) % SQUARE_COUNT; true }
                        Key.DirectionDown -> when {
                            sq >= 0 -> { sq = -1; true }              // squares → back to the cover row
                            act < 0 -> { act = 0; true }              // cover → open the bottom band on Play
                            else -> { act = (act + 1).coerceAtMost(actions.lastIndex); true }
                        }
                        Key.DirectionUp -> when {
                            sq >= 0 -> true                           // already at the top of the screen
                            act > 0 -> { act -= 1; true }
                            act == 0 -> { act = -1; true }            // back down onto the cover
                            else -> { sq = 0; true }                  // cover → up into the square strip
                        }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> when {
                            sq >= 0 -> { runSquare(sq); true }
                            else -> { runAction(actions[actIdx]); true }
                        }
                        Key.ButtonB, Key.Back -> when {
                            sq >= 0 -> { sq = -1; true }
                            act > 0 -> { act = 0; true }
                            act == 0 -> { act = -1; true }
                            selectionMode -> { onExitSelection(); true }
                            else -> false
                        }
                        else -> false
                    }
                }
                .draggable(
                    hDrag, Orientation.Horizontal,
                    onDragStarted = { swipe = 0f },
                    onDragStopped = { velocity ->
                        if (nav.depth > 0) { if (swipe > swipeBackPx) nav.pop() }
                        else settleTo((pos.floatValue - velocity / pitchPx * 0.14f).roundToInt())
                    },
                ),
        ) {
            // Background: accent glow → the focused cover, blurred → shade → the moving waves.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0f to accent.copy(alpha = 0.50f), 0.48f to accent.copy(alpha = 0.10f), 0.85f to Color.Black,
                        center = Offset(wPx * 0.18f, hPx), radius = max(wPx, hPx) * 1.1f,
                    )
                )
            )
            // The focused game's cover IS the background: full-bleed and unblurred, the way the
            // list rows put wallpaper behind their content. The shades below keep text readable.
            Crossfade(targetState = focused.icon, animationSpec = tween(600), label = "xmbBackdrop") { bmp ->
                if (bmp != null) {
                    Image(
                        bitmap = remember(bmp) { bmp.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.8f),
                    )
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.55f to Color.Black.copy(alpha = 0.1f), 1f to Color.Black.copy(alpha = 0.45f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.9f), 0.55f to Color.Transparent)))
            XmbWaves(accent, Modifier.fillMaxSize())
            // Faint shade behind a see-through top bar so its title and buttons stay readable.
            if (topInset > 0.dp) {
                Box(Modifier.fillMaxWidth().height(topInset + 24.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
            }

            // The focus target starts BELOW the see-through top bar: D-pad focus search skips a target that
            // overlaps the bar, so Down from the top-bar buttons could never come back to the XMB.
            Box(Modifier.fillMaxSize().padding(top = topInset).focusRequester(focusRequester).focusable()) {
                // Top line: controller hints (only with a pad attached) · position · clock.
                Row(
                    Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasPad && nested) {
                        val hi = nav.hint(xmbScope)
                        XmbHint("A", Color(0xFF3DDC84), hi.a)
                        XmbHint("B", Color(0xFFFF5252), "Back")
                        if (!m.port && m.w >= 600f) {
                            XmbKeyHint("▲ ▼", "Move")
                            if (hi.change) XmbKeyHint("◀ ▶", "Change")
                        }
                        if (hi.sections) XmbKeyHint("L1 R1", "Section")
                    } else if (hasPad) {
                        XmbHint("A", Color(0xFF3DDC84), actions[actIdx].label)
                        XmbHint("B", Color(0xFFFF5252), "Back")
                        if (!m.port && m.w >= 600f) {
                            XmbKeyHint("◀ ▶", "Games"); XmbKeyHint("▲ ▼", "Options"); XmbKeyHint("L1 R1", "Stores")
                        }
                    }
                }
                val rootAlpha by animateFloatAsState(if (nested) 0f else 1f, tween(260), label = "xmbRootAlpha")
                // Four clean transparent squares with a thin outline: Steam, GOG, Epic, then the app
                // menu. L1/R1 (or a tap) moves between them, A/tap opens one. They replace the old
                // store-badge cluster.
                Column(
                    Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 8.dp)
                        .graphicsLayer { alpha = rootAlpha },
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XmbSquare(R.drawable.logo_steam, "Steam", sq == 0, accent) { runSquare(0) }
                        XmbSquare(R.drawable.logo_gog, "GOG", sq == 1, accent) { runSquare(1) }
                        XmbSquare(R.drawable.logo_epic, "Epic Games", sq == 2, accent) { runSquare(2) }
                        XmbSquare(null, "Menu", sq == 3, accent) { runSquare(3) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${target + 1} / $n", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        Text(clock, color = Color(0xFFDDDDDD), fontSize = 12.sp, fontWeight = FontWeight.Light)
                    }
                }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = rootAlpha; translationY = (1f - rootAlpha) * -12.dp.toPx() }) {
                // The bar. Only covers near the cross are composed; each one's placement is read from
                // `pos` in its graphics layer, so a swipe re-draws without recomposing.
                val baseIdx by remember { derivedStateOf { floor(pos.floatValue).toInt() } }
                val ahead = ceil((m.w - m.cross) / m.pitch).toInt() + 1
                for (i in (baseIdx - 3).coerceAtLeast(0)..(baseIdx + ahead + 1).coerceAtMost(n - 1)) {
                    val s = shortcuts[i]
                    key(s.file.path) {
                        XmbCover(
                            shortcut = s, index = i, pos = pos, m = m, accent = accent,
                            focused = i == target, selectionMode = selectionMode, selected = s.file.path in selectedPaths,
                            onClick = { if (nav.depth == 0) { if (i == target) { if (selectionMode) onToggleSelect(s) else onPlay(s) } else settleTo(i) } },
                        )
                    }
                }

                // Focused game's name + "container · resolution", left-aligned and sitting just
                // above its cover, Big Picture style.
                AnimatedContent(
                    targetState = focused,
                    contentKey = { it.file.path },
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 6 }) togetherWith fadeOut(tween(90)) },
                    label = "xmbTitle",
                    modifier = Modifier.offset(m.titleX.dp, m.titleY.dp).width(m.titleW.dp),
                ) { s ->
                    val meta = remember(s) { buildLaunchSpec(s, context).meta }
                    Column {
                        Text(
                            s.name, color = Color.White, fontSize = m.titleSize.sp, lineHeight = (m.titleSize * 1.15f).sp,
                            maxLines = m.titleLines, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta, color = Color(0xFFC4C4C4), fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            sdBadge(s)
                        }
                    }
                }

                // The cross: the chosen option always sits in the first slot under the bar. Options
                // you pass slide up behind it and fade; the next few stay visible below.
                Box(
                    Modifier
                        .offset(m.colX.dp, m.colTop.dp)
                        .width((m.iconBox + 12f + m.labelW).dp)
                        .height((m.nVis * m.rowH + m.extra).dp)
                        .draggable(vDrag, Orientation.Vertical, onDragStarted = { vAcc = 0f }),
                ) {
                    actions.forEachIndexed { i, a ->
                        key(a.label) {
                            val d = i - act.coerceAtLeast(0)
                            val vis = d in 0 until m.nVis
                            val y by animateFloatAsState(d * m.rowH + if (d > 0) m.extra else 0f, spring(dampingRatio = 0.9f, stiffness = 500f), label = "xmbRowY")
                            val a2 by animateFloatAsState(if (!vis) 0f else if (d == 0) 1f else max(0.35f, 1f - d * 0.13f), tween(220), label = "xmbRowA")
                            val src = remember { MutableInteractionSource() }
                            XmbActionRow(
                                action = a, selected = i == act, accent = accent, iconBox = m.iconBox,
                                modifier = Modifier
                                    .graphicsLayer { translationY = y.dp.toPx(); alpha = a2 }
                                    .then(if (vis && !nested) Modifier.xmbClick(src) { act = i; runAction(a) } else Modifier),
                            )
                        }
                    }
                }

                if (m.infoW >= 120f && m.infoH >= 40f) {
                    Column(
                        Modifier.offset(m.infoX.dp, m.infoY.dp).width(m.infoW.dp).height(m.infoH.dp).clipToBounds(),
                    ) {
                        XmbInfo(focused, playtime, if (m.port) 6 else 3)
                    }
                }
                }
                if (nested) {
                    XmbNestedLayer(nav, xmbScope, m.w, m.h, m.port, accent, focused.name, focused.icon, actions.map { it.icon }, actIdx)
                }
            }
        }
    }
}

/** How many squares sit in the top-right strip: Steam · GOG · Epic · Menu. */
private const val SQUARE_COUNT = 4

/**
 * One of the four transparent squares in the top-right corner: a thin grey outline at rest, the
 * game's accent outline and a small pop when L1/R1 lands on it. [icon] is a brand logo drawable;
 * null draws the Material gear (the app menu square).
 */
@Composable
private fun XmbSquare(icon: Int?, label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val border by animateColorAsState(if (selected) accent else Color(0x66FFFFFF), tween(180), label = "xmbSquareBorder")
    val sc by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = 0.75f, stiffness = 620f), label = "xmbSquareScale")
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(shape)
            .background(Color.Black.copy(alpha = if (selected) 0.62f else 0.34f))
            .border(1.4.dp, border, shape)
            .xmbClick(src, onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(21.dp).alpha(if (selected) 1f else 0.82f),
            )
        } else {
            Icon(Icons.Filled.Settings, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
        }
    }
}

/** Icon for a drawer destination shown in the app-menu column. */
private fun appIconFor(screen: Screen): ImageVector = when (screen) {
    Screen.Games -> Icons.Filled.PlayArrow
    Screen.Containers -> Icons.Filled.Folder
    Screen.FileManager -> Icons.Filled.FolderOpen
    Screen.Settings -> Icons.Filled.Settings
    Screen.InputControls -> Icons.Filled.SportsEsports
    Screen.Contents -> Icons.Filled.Inventory2
    Screen.Saves -> Icons.Filled.Save
    Screen.SocialHub -> Icons.Filled.People
    Screen.AdrenoTools -> Icons.Filled.Memory
    Screen.Wrappers -> Icons.Filled.Layers
    else -> Icons.Filled.Menu
}

/** A drawer destination rendered as its own XMB column: the tab composable itself, full D-pad. */
internal fun screenMenu(screen: Screen, content: @Composable () -> Unit): XmbMenu = XmbMenu(
    title = screen.label,
    icon = appIconFor(screen),
    panel = XmbScreenPanel(content),
)

/** Graphics: the same two entries the normal view groups under its Graphics section. */
/** The pop-in column behind the fourth square: every destination the nav drawer lists. In XMB mode
 *  the tabs open as XMB columns (full controller) instead of jumping out to the tab. */
private fun xmbAppMenu(onGo: (Screen) -> Unit, onOpenContainer: (Int?) -> Unit): XmbMenu = XmbMenu(
    title = "Menu",
    icon = Icons.Filled.Menu,
    rows = {
        val xs = this
        Screen.drawerItems.map { d ->
            when (d) {
                // Games IS this tab — it is where the column opened from.
                Screen.Games ->
                    XmbRow.Action(key = d.route, label = d.label, icon = appIconFor(d)) {
                        xs.popToRoot()
                        onGo(d)
                    }

                Screen.Settings -> XmbRow.Link(key = d.route, label = d.label, icon = appIconFor(d)) {
                    xmbSettingsMenu(xs)
                }

                Screen.Containers -> XmbRow.Link(key = d.route, label = d.label, icon = appIconFor(d)) {
                    xmbContainersMenu(xs, onOpenContainer)
                }

                Screen.FileManager -> XmbRow.Link(key = d.route, label = d.label, icon = appIconFor(d)) {
                    xmbFileManagerMenu(File("/storage/emulated/0"))
                }

                Screen.InputControls -> XmbRow.Link(key = d.route, label = d.label, icon = appIconFor(d)) {
                    screenMenu(d) { InputControlsScreen() }
                }

                Screen.Contents -> XmbRow.Link(key = d.route, label = d.label, icon = appIconFor(d)) {
                    screenMenu(d) { ContentsHubScreen() }
                }

                Screen.Saves -> XmbRow.Link(key = d.route, label = d.label, icon = appIconFor(d)) {
                    screenMenu(d) { SavesScreen() }
                }

                Screen.SocialHub -> XmbRow.Link(key = d.route, label = d.label, icon = appIconFor(d)) {
                    screenMenu(d) { SocialHubScreen() }
                }

                else ->
                    XmbRow.Action(key = d.route, label = d.label, icon = appIconFor(d)) {
                        xs.popToRoot()
                        onGo(d)
                    }
            }
        } + XmbRow.Link(key = "graphics", label = "Graphics", icon = Icons.Filled.Memory) {
            xmbGraphicsMenu(xs)
        }
    },
)

/** All XMB geometry in dp, from the content box. Portrait = taller than wide. */
private class XmbMetrics(val w: Float, val h: Float) {
    val port = h > w * 1.05f
    val iconBox = 34f
    val rowH = if (port) 46f else 34f
    val extra = 6f
    // The options column is pinned to the BOTTOM edge (Big Picture: title › cover › actions), so the
    // band it occupies is reserved FIRST and the cover row is sized from what is left above it.
    val nVis = if (port) 5 else 3
    val bandH = nVis * rowH + extra + (if (port) 20f else 18f)
    val topZone = if (port) 76f else 74f        // clears the hint line + the four store squares
    val titleBlockH = if (port) 60f else 42f    // game title above the cover + its meta line
    val fHMax = if (port) min(w * 0.62f, h * 0.34f) else h * 0.46f
    val fH = (h - bandH - titleBlockH - topZone - 12f).coerceIn(96f, fHMax)
    val fW = fH * 2f / 3f                  // 2:3, same as the grid covers
    val sH = fH * 0.62f
    val sW = sH * 2f / 3f
    val gap = (fH * 0.09f).roundToInt().toFloat()
    val pitch = sW + gap
    val grow = fW - sW
    val cross = if (port) 16f + sW * 0.42f + gap else max(16f + pitch, w * 0.13f)
    val colX = cross + fW / 2f - iconBox / 2f
    val labelW = if (port) min(220f, w - colX - iconBox - 28f) else 176f
    val top = h - bandH - fH - 6f               // covers sit directly above the options band
    val colTop = h - bandH + 8f                 // options band, bottom-anchored (12dp off the edge)
    // Title sits DIRECTLY above the focused cover, left-aligned to it.
    val titleX = cross
    val titleY = top - titleBlockH - 6f
    val titleW = w - titleX - 16f
    val titleLines = if (port) 2 else 1
    val titleSize = if (port) 17f else 22f
    val infoX = if (port) 16f else colX + iconBox + 12f + labelW + 12f
    val infoY = if (port) topZone + 6f else colTop
    val infoW = if (port) w - 32f else w - infoX - 24f
    val infoH = if (port) (titleY - 12f - infoY).coerceAtLeast(0f) else (bandH - 24f).coerceAtLeast(0f)
}

@Composable
private fun XmbCover(
    shortcut: Shortcut,
    index: Int,
    pos: MutableFloatState,
    m: XmbMetrics,
    accent: Color,
    focused: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .size(m.fW.dp, m.fH.dp)
            .graphicsLayer {
                // u = distance from the cross in covers; the focused one grows to full size and the
                // ones after it are pushed right by the extra width, so spacing stays even mid-swipe.
                val u = index - pos.floatValue
                val t = (1f - abs(u)).coerceAtLeast(0f)
                val sc = (m.sH + (m.fH - m.sH) * t) / m.fH
                val x = m.cross + u * m.pitch + m.grow * u.coerceIn(0f, 1f)
                translationX = x.dp.toPx()
                translationY = m.top.dp.toPx()
                scaleX = sc
                scaleY = sc
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = ((if (u >= 0f) 0.82f else 0.82f + u * 0.32f) + 0.18f * t).coerceIn(0f, 1f)
            }
            .then(if (focused && !selected) Modifier.shadow(14.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(Color(0xFF111111))
            .then(
                when {
                    selected -> Modifier.border(1.5.dp, DangerRed, shape)
                    focused -> Modifier.border(1.dp, accent, shape)
                    else -> Modifier
                }
            )
            .xmbClick(src, onClick),
    ) {
        val bmp = shortcut.icon
        if (bmp != null) {
            Image(
                bitmap = remember(bmp) { bmp.asImageBitmap() },
                contentDescription = shortcut.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            XmbMonogramLegacy(shortcut.name, accent)
        }
        if (selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) DangerRed else Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, if (selected) DangerRed else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// Cover fallback: initials on an accent gradient (no art yet, or a custom game).
@Composable
private fun XmbMonogramLegacy(name: String, accent: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF0A0A0A))))
            .padding(10.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(xmbInitialsLegacy(name), color = Color.White.copy(alpha = 0.92f), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(name, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun XmbActionRow(action: XmbAction, selected: Boolean, accent: Color, iconBox: Float, modifier: Modifier) {
    val ring = if (action.danger) DangerRed else accent
    val boxShape = RoundedCornerShape(10.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(iconBox.dp)
                .clip(boxShape)
                .background(if (selected) ring.copy(alpha = 0.25f) else Color.Transparent)
                .then(if (selected) Modifier.border(1.5.dp, ring, boxShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                action.icon, contentDescription = null, modifier = Modifier.size(19.dp),
                tint = when {
                    selected -> Color.White
                    action.danger -> Color(0xFFE8A197)
                    else -> Color(0xFFCFCFCF)
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                if (action.menu != null) "${action.label}  ›" else action.label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.58f),
                fontSize = if (selected) 16.sp else 13.5.sp,
                fontWeight = if (selected) FontWeight.Normal else FontWeight.Light,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = action.subtitle
            if (selected && sub != null) {
                Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Right / lower pane: store badges, playtime and Game Details.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun XmbInfo(s: Shortcut, playtime: String?, descLines: Int) {
    val details = remember(s) { buildLaunchDetails(s) }
    val factColor = Color(0xFFD0D0D0)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(playtime ?: "Not played yet", color = factColor, fontSize = 12.sp)
        }
        if (details.genres.isNotEmpty()) Text(details.genres.take(3).joinToString(" · "), color = factColor, fontSize = 12.sp)
        details.releaseYear?.takeIf { it.isNotBlank() }?.let { Text(it, color = factColor, fontSize = 12.sp) }
        details.metacritic?.let { score ->
            Text(
                "$score", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(metacriticColorLegacy(score)).padding(horizontal = 5.dp),
            )
        }
    }
    details.description?.takeIf { it.isNotBlank() }?.let {
        Text(
            it, color = Color(0xFFABABAB), fontSize = 12.sp, lineHeight = 17.sp,
            maxLines = descLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp),
        )
    }
}

@Composable
private fun XmbHint(glyph: String, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(17.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(glyph, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFB0B0B0), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun XmbKeyHint(keys: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            keys, color = Color(0xFFDDDDDD), fontSize = 10.sp,
            modifier = Modifier.border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color(0xFFB0B0B0), fontSize = 11.sp, maxLines = 1)
    }
}

private class XmbRibbon(val y: Float, val amp: Float, val k: Float, val cycles: Int, val thick: Float, val alpha: Float)

private val XMB_RIBBONS = listOf(
    XmbRibbon(0.58f, 0.05f, 1.1f, 1, 0.07f, 0.10f),
    XmbRibbon(0.65f, 0.07f, 0.75f, -1, 0.11f, 0.07f),
    XmbRibbon(0.71f, 0.045f, 1.6f, 2, 0.05f, 0.12f),
    XmbRibbon(0.63f, 0.03f, 2.2f, -2, 0.02f, 0.16f),
)

// The XMB wave. Every time term is a whole number of turns per loop, so the 40 s cycle is seamless.
// The phase is read only inside the draw lambda, so this redraws without recomposing.
// internal: shared with the launch preloader, which draws the same field behind its progress.
@Composable
internal fun XmbWaves(accent: Color, modifier: Modifier) {
    val phase by rememberInfiniteTransition(label = "xmbWaves").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "xmbWavePhase",
    )
    val paths = remember { List(XMB_RIBBONS.size * 2) { Path() } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val tau = (2.0 * PI).toFloat()
        val step = 8.dp.toPx()
        XMB_RIBBONS.forEachIndexed { ri, r ->
            val ph = phase * tau * r.cycles
            val base = r.y * h
            val amp = r.amp * h
            val th = r.thick * h
            val edge = paths[ri * 2].apply { reset() }
            val band = paths[ri * 2 + 1].apply { reset() }
            val bottoms = ArrayList<Offset>()
            var x = 0f
            while (x <= w + step) {
                val u = x / w * tau * r.k
                val y = base + amp * sin(u + ph) + amp * 0.5f * sin(u * 0.55f - 2f * ph)
                if (x == 0f) { edge.moveTo(x, y); band.moveTo(x, y) } else { edge.lineTo(x, y); band.lineTo(x, y) }
                bottoms.add(Offset(x, y + th * (0.55f + 0.45f * sin(u * 0.7f + ph))))
                x += step
            }
            for (i in bottoms.indices.reversed()) band.lineTo(bottoms[i].x, bottoms[i].y)
            band.close()
            drawPath(
                band,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = r.alpha), accent.copy(alpha = 0f)),
                    startY = base - amp, endY = base + amp + th,
                ),
            )
            drawPath(edge, Color.White.copy(alpha = min(1f, r.alpha * 2.2f)), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

// Controller hints only make sense with a pad attached; tracks hot-plug.
@Composable
private fun rememberGamepadConnectedLegacy(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val check = {
            connected = InputDevice.getDeviceIds().any { ExternalController.isGameController(InputDevice.getDevice(it)) }
        }
        check()
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = check()
            override fun onInputDeviceRemoved(deviceId: Int) = check()
            override fun onInputDeviceChanged(deviceId: Int) = check()
        }
        im?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose { im?.unregisterInputDeviceListener(listener) }
    }
    return connected
}

// "Xh Ym" from the same prefs XServerDisplayActivity writes; null = never played.
internal fun xmbPlaytime(context: Context, name: String): String? {
    val ms = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE).getLong("${name}_playtime", 0L)
    if (ms <= 0L) return null
    val mins = ms / 60_000L
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

private fun xmbInitialsLegacy(name: String): String {
    val words = name.replace(Regex("[^A-Za-z0-9 ]"), " ").split(' ').filter { it.isNotBlank() }
    val first = words.firstOrNull() ?: return name.trim().take(1).ifEmpty { "?" }
    if (first.length <= 4 && first == first.uppercase() && first.any { it.isLetter() }) return first
    return words.filterNot { it.lowercase() in setOf("the", "of", "and", "a") }
        .take(2).joinToString("") { it.first().uppercase() }
        .ifEmpty { first.take(1).uppercase() }
}

private fun metacriticColorLegacy(score: Int): Color = when {
    score >= 75 -> Color(0xFF66CC33)
    score >= 50 -> Color(0xFFFFCC33)
    else -> Color(0xFFFF4444)
}
