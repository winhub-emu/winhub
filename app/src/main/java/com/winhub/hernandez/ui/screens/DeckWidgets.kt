package com.winhub.hernandez.ui.screens

import android.os.Handler
import android.view.View
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.winhub.hernandez.store.deckTileFocus
import com.winhub.hernandez.ui.moveFocusOrStep
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.ui.theme.AppThemeState
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One page row at a time (Storage sub-tabs, container editor tabs) binds itself here, so the Deck
 * shell can drive L1/R1 even when focus sits on the side rail. Pills are D-pad focusable too: landing
 * on one selects it, so a store/tab can be picked without touching the screen.
 */
internal class DeckTabHost {
    var count by mutableIntStateOf(0)
        private set
    var index by mutableIntStateOf(0)
        private set

    /** A page can claim the B button (e.g. a repository detail closes before leaving the section). */
    var onBack: (() -> Boolean)? = null

    fun bind(pages: Int) {
        count = pages
        if (index !in 0 until pages) index = 0
    }

    fun select(page: Int) {
        if (page in 0 until count) index = page
    }

    fun step(delta: Int) {
        if (count > 1) index = (index + delta + count) % count
    }
}

internal val LocalDeckTabs = compositionLocalOf { DeckTabHost() }

/**
 * A page/detail opened on top of a Deck section can claim B before the shell leaves the section.
 * The handler returns true when it consumed the press.
 */
internal class DeckBackLayer {
    var onBack: (() -> Boolean)? = null
}

internal val LocalDeckBack = compositionLocalOf { DeckBackLayer() }

/**
 * A focus group with one dragging selection ring: items report their bounds through
 * [deckDragItem], and the ring stretches to the focused one exactly like the store pills.
 * Used by shelves, grids and lists so every D-pad surface shares the same motion.
 */
internal class DeckDragRegistry {
    val bounds = mutableStateMapOf<Any, Rect>()
    var focusedKey: Any? by mutableStateOf(null)
    var rootTopLeft: Offset by mutableStateOf(Offset.Zero)

    /** The window view this frame lives in; items from another window (a Dialog) must not register. */
    var view: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var clearToken = 0
    private var pendingClear: Runnable? = null

    /** Focus landed on [key]: cancel any clear still waiting for the item that lost it. */
    fun focus(key: Any) {
        clearToken++
        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
        focusedKey = key
    }

    /**
     * Focus left [key] but the surface may still hold focus (it moved to a non-drag control, or
     * to a sibling). Wait a beat so a drag item that takes focus in the same pass wins and the
     * ring keeps stretching; if nothing takes it, drop the stale ring.
     */
    fun clearSoon(key: Any) {
        val token = ++clearToken
        pendingClear?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (clearToken == token && focusedKey == key) {
                focusedKey = null
                pendingClear = null
            }
        }
        pendingClear = runnable
        handler.postDelayed(runnable, 80L)
    }

    /** Focus left the whole surface: drop the ring immediately. */
    fun clearNow() {
        clearToken++
        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
        focusedKey = null
    }
}

internal val LocalDeckDrag = compositionLocalOf<DeckDragRegistry?> { null }

/** Marks a focusable item as a member of the surrounding [DeckDragFrame]. */
@Composable
internal fun Modifier.deckDragItem(key: Any): Modifier {
    val registry = LocalDeckDrag.current
    val view = LocalView.current
    DisposableEffect(registry, key) {
        onDispose {
            registry?.bounds?.remove(key)
            // The item that owned the ring left composition (page swap / recycle): drop it now
            // instead of leaving the last painted rect on screen.
            if (registry?.focusedKey == key) registry?.clearNow()
        }
    }
    // A Dialog inherits the frame's registry but has its own window coordinates: skip it there.
    if (registry == null || registry.view !== view) return this
    return this
        .onGloballyPositioned { coords ->
            val local = coords.boundsInRoot().translate(-registry.rootTopLeft.x, -registry.rootTopLeft.y)
            if (registry.bounds[key] != local) registry.bounds[key] = local
        }
        .onFocusChanged { state ->
            if (state.isFocused || state.hasFocus) {
                registry.focus(key)
            } else if (registry.focusedKey == key) {
                registry.clearSoon(key)
            }
        }
}

/** [deckDragItem] with an auto key, for a button that is its own ring target (no stable id needed). */
@Composable
internal fun Modifier.deckDragSelf(): Modifier = deckDragItem(remember { Any() })

private fun Rect.translate(dx: Float, dy: Float): Rect =
    Rect(left + dx, top + dy, right + dx, bottom + dy)

/**
 * Hosts a [LocalDeckDrag] registry and draws the animated ring around the focused item.
 * Place it around a lazy grid/list/row; the ring lives in the frame's coordinate space, so it
 * follows scrolling and stretches between items.
 */
@Composable
internal fun DeckDragFrame(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    strokeWidth: Dp = 2.5.dp,
    gap: Dp = 3.dp,
    color: Color = DeckPalette.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    val registry = remember { DeckDragRegistry() }
    registry.view = LocalView.current
    var hasFocus by remember { mutableStateOf(false) }
    val left = remember { Animatable(0f) }
    val top = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    val bottom = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    val target = registry.focusedKey?.let { registry.bounds[it] }
    LaunchedEffect(target, registry.focusedKey) {
        val t = target ?: return@LaunchedEffect
        if (!armed) {
            left.snapTo(t.left); top.snapTo(t.top); right.snapTo(t.right); bottom.snapTo(t.bottom)
            armed = true
            return@LaunchedEffect
        }
        val movingRight = t.left >= left.value
        val movingDown = t.top >= top.value
        coroutineScope {
            if (movingRight) {
                launch { right.animateTo(t.right, DeckLead) }
                launch { delay(40); left.animateTo(t.left, DeckTrail) }
            } else {
                launch { left.animateTo(t.left, DeckLead) }
                launch { delay(40); right.animateTo(t.right, DeckTrail) }
            }
            if (movingDown) {
                launch { bottom.animateTo(t.bottom, DeckLead) }
                launch { delay(40); top.animateTo(t.top, DeckTrail) }
            } else {
                launch { top.animateTo(t.top, DeckLead) }
                launch { delay(40); bottom.animateTo(t.bottom, DeckTrail) }
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { registry.rootTopLeft = it.positionInRoot() }
            .onFocusChanged { state ->
                hasFocus = state.isFocused || state.hasFocus
                if (!hasFocus) {
                    // Focus left this surface (tab/page swap, or a control outside the frame).
                    // Drop the last item so its stroke cannot remain painted on the new page;
                    // keep `armed` so returning to the frame still stretches instead of snapping.
                    registry.clearNow()
                }
            }
            .drawWithContent {
                drawContent()
                if (hasFocus && armed && !DeckTouchMode.touch && right.value > left.value) {
                    val g = gap.toPx()
                    val radius = shape.topStart.toPx(
                        Size(right.value - left.value, bottom.value - top.value),
                        this,
                    ) + g
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left.value - g, top.value - g),
                        size = Size(
                            right.value - left.value + g * 2f,
                            bottom.value - top.value + g * 2f,
                        ),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width = strokeWidth.toPx()),
                    )
                }
            },
    ) {
        CompositionLocalProvider(LocalDeckDrag provides registry) { content() }
    }
}

/**
 * The Deck selection stroke: drawn *outside* the tile with the tile's radius grown by [gap], so it
 * reads as a rounded ring hugging the artwork instead of a square line painted inside it.
 */
@Composable
internal fun Modifier.deckOuterFocusStroke(
    interaction: InteractionSource,
    shape: RoundedCornerShape,
    strokeWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
    focusColor: Color = DeckPalette.primary,
): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    return drawWithContent {
        drawContent()
        if (focused && !DeckTouchMode.touch) {
            // Drawn INSIDE the bounds: a ring painted outside is clipped away by the caller's
            // .clip(shape) (hero banner, blue buttons, store cards), which is why they had no stroke.
            val sw = strokeWidth.toPx()
            val inset = sw / 2f
            val radius = shape.topStart.toPx(size, this) - inset
            drawRoundRect(
                color = focusColor,
                topLeft = Offset(inset, inset),
                size = Size(size.width - sw, size.height - sw),
                cornerRadius = CornerRadius(radius.coerceAtLeast(0f), radius.coerceAtLeast(0f)),
                style = Stroke(width = sw),
            )
        }
    }
}

/**
 * Outer focus stroke for plain Material controls (IconButton, OutlinedButton, Text chips) that
 * don't expose an InteractionSource. Same geometry as [deckOuterFocusStroke] — the ring sits
 * outside the bounds so it never eats into the control's own border.
 */
@Composable
internal fun Modifier.deckSelfFocusStroke(
    shape: RoundedCornerShape,
    strokeWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
    focusColor: Color = DeckPalette.primary,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (!focused || DeckTouchMode.touch) return@drawWithContent
            // Inset, for the same reason as deckOuterFocusStroke: clips eat an outer ring.
            val sw = strokeWidth.toPx()
            val inset = sw / 2f
            val radius = shape.topStart.toPx(size, this) - inset
            drawRoundRect(
                color = focusColor,
                topLeft = Offset(inset, inset),
                size = Size(size.width - sw, size.height - sw),
                cornerRadius = CornerRadius(radius.coerceAtLeast(0f), radius.coerceAtLeast(0f)),
                style = Stroke(width = sw),
            )
        }
}

/**
 * Scrolls the focused control into view. Compose's focus walker happily targets items that are
 * already scrolled out of a lazy list or a horizontal rail, which on a TV/controller UI reads as
 * "the highlight vanished". Attach this to anything that can take D-pad focus inside a scrollable.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.deckBringIntoView(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged {
            if (it.isFocused) scope.launch { runCatching { requester.bringIntoView() } }
        }
}

/**
 * Dialogs live in their own window, so the Deck shell's root key handler never sees their keys.
 * This makes the dialog body the focus owner: opening it parks focus there, the D-pad walks into
 * the rows/buttons, and B closes the dialog. Apply it to the dialog's root Column/Box.
 */
@Composable
internal fun Modifier.deckDialogNav(onBack: () -> Unit = {}): Modifier {
    val focusManager = LocalFocusManager.current
    val root = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { root.requestFocus() } }
    return this
        .focusRequester(root)
        .focusable()
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape) {
                onBack()
                true
            } else {
                val dir = when (e.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                } ?: return@onPreviewKeyEvent false
                focusManager.moveFocusOrStep(dir)
            }
        }
}

/**
 * The Deck focus frame: every controller-focusable surface (tiles, pills, buttons) turns its
 * hairline outline into a 2dp blue stroke the moment the D-pad lands on it. Touch never sees it.
 */
@Composable
internal fun Modifier.deckFocusBorder(
    interaction: InteractionSource,
    shape: Shape,
    base: Color = DeckPalette.line,
    focusColor: Color = DeckPalette.primary,
): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val ring = focused && !DeckTouchMode.touch
    return border(if (ring) 2.dp else 1.dp, if (ring) focusColor else base, shape)
}

/** DroidDeck's own ease: quick off the mark, then a long glide — the "dragged" feel. */
/**
 * True after the last input was a touch, false after the last key. Every focus ring is hidden while it
 * is set: a touch leaves Compose focus on the tapped control, and that ring used to stay painted.
 */
internal object DeckTouchMode {
    var touch by mutableStateOf(false)
}

/** Activity-level hook for the Deck's shoulder/trigger buttons, so they never depend on Compose focus. */
internal object DeckHardKeys {
    @Volatile var handler: ((Int) -> Boolean)? = null
}

/** DroidDeck's ChipGlide springs: the leading edge snaps ahead, the trailing edge follows 40 ms later. */
internal val DeckLead = androidx.compose.animation.core.spring<Float>(0.62f, 700f)
internal val DeckTrail = androidx.compose.animation.core.spring<Float>(0.78f, 360f)

internal val DeckEase = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * True while rendering inside the Deck shell ("New UI"). Screens hosted by both shells
 * (File Manager, Save Manager) read this to swap their legacy Material surfaces for Deck
 * ones without threading a flag through every private composable.
 */
internal val LocalDeckMode = compositionLocalOf { false }

/**
 * DroidDeck palette, now backed by the live [AppThemeState] preset. Property names are unchanged
 * so the ~500 existing call sites keep working; each getter reads the Compose snapshot state,
 * which means any composable that uses a Deck color recomposes when the theme changes.
 *
 * [error] stays fixed red for destructive actions. [good] follows the preset's success token so
 * Monochrome can stay strictly black/white/red instead of introducing green.
 */
internal object DeckPalette {
    private val p get() = AppThemeState.deckPreset
    val background get() = p.background
    val surface get() = p.surface
    val surfaceVariant get() = p.surfaceVariant
    val line get() = p.line
    val line2 get() = p.line2
    val onBackground get() = p.onBackground
    val onSurfaceVariant get() = p.onSurfaceVariant
    val primary get() = p.primary
    val onPrimary get() = p.onPrimary
    val secondary get() = p.secondary
    val onSecondary get() = p.onSecondary
    val secondaryDim get() = p.secondaryDim
    val drawerDim get() = p.drawerDim
    val drawerContainer get() = p.drawerContainer
    val drawerOnContainer get() = p.drawerOnContainer
    val drawerTertiary get() = p.drawerTertiary
    val good get() = p.success
    val error = Color(0xFFFF8A80)

    /** DroidDeck's GroupShape. */
    val groupShape = RoundedCornerShape(14.dp)

    fun titleCase(value: String): String =
        value.lowercase().split("_").joinToString(" ") { part ->
            part.replaceFirstChar { ch -> ch.uppercaseChar() }
        }
}

/**
 * DroidDeck's settings group — an uppercase eyebrow with a hairline rule, then the rows in a
 * 14dp card. Lifted from DroidDeck ui/SettingsWidgets.kt `SettingsGroup`.
 */
@Composable
internal fun DeckGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = DeckPalette.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(DeckPalette.line),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeckPalette.groupShape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, DeckPalette.groupShape),
        content = content,
    )
}

/**
 * One setting: label and optional hint on the left, [control] on the right, hairline under it.
 * DroidDeck's `SettingsRow`. [configPanel] shows a small blue chevron after the control to signal
 * that a config panel is available behind it.
 */
@Composable
internal fun DeckRow(
    label: String,
    hint: String? = null,
    configPanel: Boolean = false,
    onConfigClick: (() -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    val dragKey = remember { Any() }
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .deckDragItem(dragKey)
            // Tapping the row still opens the panel, but the row must not be a D-pad stop: a ring
            // around the whole row when the real control is the button/toggle is what made focus
            // look like it was "on the entire row". The chevron below is the focusable affordance.
            .then(
                if (onConfigClick != null) Modifier
                    .focusProperties { canFocus = false }
                    .clickable(interactionSource = rowInteraction, indication = null, onClick = onConfigClick)
                else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeckPalette.onBackground,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = DeckPalette.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        control()
        if (configPanel) {
            val chevronInteraction = remember { MutableInteractionSource() }
            val chevronShape = RoundedCornerShape(10.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(40.dp)
                    .clip(chevronShape)
                    .then(
                        if (onConfigClick != null) Modifier
                            .clickable(
                                interactionSource = chevronInteraction,
                                indication = null,
                                onClick = onConfigClick,
                            )
                            .deckSelfFocusStroke(chevronShape)
                        else Modifier,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Has a config panel",
                    tint = DeckPalette.secondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DeckPalette.line),
    )
}

/**
 * A [DeckRow] whose chevron toggles an inline editor that expands BELOW it, pushing the rest of the
 * group's content down (no dialog, no navigation). The chevron flips from right-pointing to
 * down-pointing while open. Tap the header row or the chevron to toggle.
 */
@Composable
internal fun DeckExpandableRow(
    label: String,
    hint: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    control: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "deckExpandChevron")
    val dragKey = remember { Any() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .deckDragItem(dragKey)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeckPalette.onBackground,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        fontSize = 12.5.sp,
                        color = DeckPalette.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            control()
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = DeckPalette.secondary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp)
                    .rotate(rotation),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DeckPalette.line),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeckPalette.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                content = content,
            )
        }
    }
}

/** DroidDeck's on/off switch: a 52×30 pill track with a 22dp knob. */
@Composable
internal fun DeckToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val knob by animateFloatAsState(if (checked) 1f else 0f, label = "deckToggle")
    val track = if (checked) DeckPalette.secondary else DeckPalette.surfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 64.dp)
            .clickable { onChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(track)
                .border(
                    width = 1.dp,
                    color = if (checked) DeckPalette.secondary else DeckPalette.line2,
                    shape = RoundedCornerShape(99.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(22.dp)
                    .graphicsLayer { translationX = knob * 22.dp.toPx() }
                    .clip(CircleShape)
                    .background(if (checked) DeckPalette.onSecondary else DeckPalette.onSurfaceVariant),
            )
        }
    }
}

/** DroidDeck's value chip: the current value in a pill with a caret that flips when open. */
@Composable
internal fun DeckValueChip(text: String, open: Boolean, onClick: () -> Unit) {
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "deckCaret")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .widthIn(min = 100.dp)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DeckPalette.surfaceVariant)
            .border(1.dp, DeckPalette.line2, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeckPalette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(
            text = "▾",
            fontSize = 10.sp,
            color = DeckPalette.onSurfaceVariant,
            modifier = Modifier.rotate(rot),
        )
    }
}

/** DroidDeck's choice row: label + hint with a value chip that opens a list of options. */
@Composable
internal fun DeckChoiceRow(
    label: String,
    hint: String? = null,
    options: List<Pair<String, String>>,
    selected: String,
    configPanel: Boolean = false,
    onConfigClick: (() -> Unit)? = null,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    DeckRow(
        label = label,
        hint = hint,
        configPanel = configPanel,
        onConfigClick = onConfigClick,
    ) {
        Box {
            DeckValueChip(
                text = options.firstOrNull { it.first == selected }?.second ?: selected,
                open = open,
                onClick = { open = !open },
            )
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier.background(DeckPalette.surfaceVariant),
            ) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                color = if (value == selected) DeckPalette.primary else DeckPalette.onBackground,
                                fontSize = 14.sp,
                                fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onPick(value)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/** A value as a track: DroidDeck's SliderRow shape with a Material slider inside. */
@Composable
internal fun DeckSliderRow(
    label: String,
    hint: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    val dragKey = remember { Any() }
    Column(modifier = Modifier.fillMaxWidth().deckDragItem(dragKey)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeckPalette.onBackground,
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        fontSize = 12.5.sp,
                        color = DeckPalette.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                text = format(value),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeckPalette.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = DeckPalette.secondary,
                activeTrackColor = DeckPalette.secondary,
                inactiveTrackColor = DeckPalette.surfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DeckPalette.line),
        )
    }
}

/**
 * A Deck text field waits for A/Enter before it starts editing, so the D-pad never falls into a
 * text box (or raises the IME) while the player is just navigating. B / Back leaves the field and
 * returns the cursor to the same row.
 */
internal class DeckEditState {
    var editing by mutableStateOf(false)
    internal var returnFocus = false
    internal var trackerHeld = false
    internal val fieldFocus = FocusRequester()
}

/**
 * Lets the surrounding screen (or popup) know a field is taking input, so the B button closes the
 * keyboard before it closes the screen. One field can be editing at a time, hence a single flag.
 */
internal class DeckEditTracker {
    private var active by mutableIntStateOf(0)
    val editing: Boolean get() = active > 0
    internal fun hold() { active++ }
    internal fun release() { if (active > 0) active-- }
}

internal val LocalDeckEditTracker = compositionLocalOf { DeckEditTracker() }

@Composable
internal fun rememberDeckEditState(): DeckEditState {
    val state = remember { DeckEditState() }
    val tracker = LocalDeckEditTracker.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.editing) {
        if (state.editing && !state.trackerHeld) {
            state.trackerHeld = true
            tracker.hold()
        } else if (!state.editing && state.trackerHeld) {
            state.trackerHeld = false
            tracker.release()
        }
        if (state.editing) {
            runCatching { state.fieldFocus.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (state.trackerHeld) {
                state.trackerHeld = false
                tracker.release()
            }
        }
    }
    BackHandler(enabled = state.editing) {
        state.returnFocus = true
        state.editing = false
    }
    return state
}

/** Transparent focus target laid over a field while it is not editing. A / Enter starts editing. */
@Composable
internal fun DeckEditOverlay(
    state: DeckEditState,
    modifier: Modifier = Modifier,
    // When set, the overlay (the thing the pad actually focuses) draws the focus stroke itself.
    ring: RoundedCornerShape? = null,
) {
    val focus = remember { FocusRequester() }
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(Unit) {
        if (state.returnFocus) {
            state.returnFocus = false
            runCatching { focus.requestFocus() }
        }
    }
    Box(
        modifier = modifier
            .focusRequester(focus)
            .then(if (ring != null) Modifier.deckOuterFocusStroke(source, ring) else Modifier)
            .clickable(
                interactionSource = source,
                indication = null,
            ) { state.editing = true }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.ButtonA || event.key == Key.Enter)
                ) {
                    state.editing = true
                    true
                } else false
            },
    )
}

/**
 * Deck's stretchy tab strip: the accent pill grows toward the tab being opened, then contracts onto
 * it (the Steam Deck / DroidDeck tab feel) instead of sliding at a fixed width. Callers drive
 * selection with L1/R1; [focusable] keeps the labels on the D-pad path for the store tab bars.
 */
@Composable
internal fun DeckStretchyTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int>? = null,
    dots: List<Color?>? = null,
    focusable: Boolean = false,
    // Off by default: focus only highlights a tab, A selects it. Focus-select made content change
    // under the player while they were just walking past a strip.
    selectOnFocus: Boolean = false,
    scroll: Boolean = false,
    fillWidth: Boolean = true,
    height: Dp = 40.dp,
) {
    val density = LocalDensity.current
    // A scrolling strip must wrap its tabs: filling the width while the Row wraps left the
    // surface/border painted across an empty tail on the right. `spread` already implies
    // "not scrolling", so both flags share one condition.
    val fill = fillWidth && !scroll
    val spread = fill
    val lefts = remember(labels.size) { mutableStateListOf<Float>() }
    val rights = remember(labels.size) { mutableStateListOf<Float>() }
    val start = remember { Animatable(0f) }
    val end = remember { Animatable(0f) }
    var lastSelected by remember { mutableIntStateOf(-1) }
    val scrollState = rememberScrollState()

    val targetLeft = lefts.getOrNull(selected)
    val targetRight = rights.getOrNull(selected)
    // A different label set means the old pixel positions are meaningless; snap instead of
    // sliding the pill in from wherever the previous strip left it.
    LaunchedEffect(labels.size) { lastSelected = -1 }
    LaunchedEffect(selected, targetLeft, targetRight) {
        val l = targetLeft ?: return@LaunchedEffect
        val r = targetRight ?: return@LaunchedEffect
        when {
            lastSelected == -1 -> {
                start.snapTo(l)
                end.snapTo(r)
                lastSelected = selected
            }
            selected == lastSelected -> {
                if (start.value != l || end.value != r) {
                    start.snapTo(l)
                    end.snapTo(r)
                }
            }
            else -> {
                // Remember the requested tab before animating: if the player flips back before the
                // stretch finishes, the cancelled run must not leave `lastSelected` on the old tab
                // (that stale value made the pill snap instead of gliding back).
                lastSelected = selected
                if (l >= start.value) {
                    coroutineScope {
                        launch { end.animateTo(r, DeckLead) }
                        launch { delay(40); start.animateTo(l, DeckTrail) }
                    }
                } else {
                    coroutineScope {
                        launch { start.animateTo(l, DeckLead) }
                        launch { delay(40); end.animateTo(r, DeckTrail) }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .height(height + 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(DeckPalette.surfaceVariant)
            .border(1.dp, DeckPalette.line2, RoundedCornerShape(13.dp))
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .then(if (fill) Modifier.fillMaxSize() else Modifier)
                .then(if (scroll) Modifier.horizontalScroll(scrollState) else Modifier),
        ) {
            Box(Modifier.height(height)) {
                if (end.value > start.value) {
                    Box(
                        Modifier
                            .offset { IntOffset(start.value.roundToInt(), 0) }
                            .width(with(density) { (end.value - start.value).toDp() })
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .border(2.dp, DeckPalette.primary, RoundedCornerShape(10.dp)),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(height)
                        .then(if (spread) Modifier.fillMaxWidth() else Modifier),
                ) {
                    labels.forEachIndexed { index, label ->
                        DeckStretchyTab(
                            label = label,
                            badge = badges?.getOrNull(index) ?: 0,
                            dot = dots?.getOrNull(index),
                            active = index == selected,
                            focusable = focusable,
                            selectOnFocus = selectOnFocus,
                            padding = if (spread) 6.dp else 14.dp,
                            modifier = if (spread) Modifier.weight(1f) else Modifier,
                            onClick = { onSelect(index) },
                            onMeasured = { left, right ->
                                while (lefts.size <= index) {
                                    lefts.add(0f)
                                    rights.add(0f)
                                }
                                if (lefts[index] != left || rights[index] != right) {
                                    lefts[index] = left
                                    rights[index] = right
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckStretchyTab(
    label: String,
    badge: Int,
    dot: Color?,
    active: Boolean,
    focusable: Boolean,
    selectOnFocus: Boolean,
    padding: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMeasured: (Float, Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val ink by animateColorAsState(
        if (active) DeckPalette.primary else DeckPalette.onSurfaceVariant,
        tween(190, easing = DeckEase),
        label = "deckTabInk",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .then(if (focusable) Modifier else Modifier.focusProperties { canFocus = false })
            .clip(RoundedCornerShape(10.dp))
            // With selectOnFocus the stretchy selection pill is already the indicator; a second
            // border on the same tab reads as two cursors. Focus-only strips still need one.
            .then(
                if (focusable && !selectOnFocus)
                    Modifier.deckFocusBorder(interaction, RoundedCornerShape(10.dp), Color.Transparent)
                else Modifier,
            )
            .deckBringIntoView()
            .onFocusChanged { if (focusable && selectOnFocus && it.isFocused && !active) onClick() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .onGloballyPositioned { coords ->
                val left = coords.positionInParent().x
                onMeasured(left, left + coords.size.width)
            }
            .padding(horizontal = padding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (badge > 0) {
                Text(
                    text = " ($badge)",
                    color = ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A label + editable text field in the Deck style (same TextField colors used across Deck). */
@Composable
internal fun DeckTextRow(
    label: String,
    hint: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    width: Dp = 300.dp,
    mask: Boolean = false,
) {
    val edit = rememberDeckEditState()
    DeckRow(label, hint) {
        Box(Modifier.width(width)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                readOnly = !edit.editing,
                visualTransformation = if (mask && value.isNotEmpty()) PasswordVisualTransformation() else VisualTransformation.None,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DeckPalette.surfaceVariant,
                    unfocusedContainerColor = DeckPalette.surfaceVariant,
                    focusedTextColor = DeckPalette.onBackground,
                    unfocusedTextColor = DeckPalette.onBackground,
                    focusedIndicatorColor = DeckPalette.primary,
                    unfocusedIndicatorColor = DeckPalette.line2,
                    cursorColor = DeckPalette.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = edit.editing }
                    .focusRequester(edit.fieldFocus)
                    .onFocusChanged { if (edit.editing && !it.isFocused) edit.editing = false }
                    .onPreviewKeyEvent { event ->
                        if (edit.editing && event.type == KeyEventType.KeyDown &&
                            (event.key == Key.ButtonB || event.key == Key.Back)
                        ) {
                            edit.returnFocus = true
                            edit.editing = false
                            true
                        } else false
                    }
                    .clip(RoundedCornerShape(10.dp)),
            )
            if (!edit.editing) DeckEditOverlay(edit, Modifier.matchParentSize())
        }
    }
}

/** A Deck-styled pill button (primary = filled blue, neutral = surface with hairline). */
@Composable
internal fun DeckButton(
    text: String,
    primary: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) DeckPalette.primary else DeckPalette.surface)
            .border(
                width = 1.dp,
                color = if (primary) DeckPalette.primary else DeckPalette.line,
                shape = RoundedCornerShape(12.dp),
            )
            .deckOuterFocusStroke(
                interaction = interaction,
                shape = RoundedCornerShape(12.dp),
                focusColor = if (primary) DeckPalette.onPrimary else DeckPalette.primary,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            color = if (primary) DeckPalette.onPrimary else DeckPalette.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
