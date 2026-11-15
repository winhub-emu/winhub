package com.winhub.hernandez.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.winhub.hernandez.ui.LocalXmbHintInset
import com.winhub.hernandez.ui.deck.DeckStore
import com.winhub.hernandez.ui.deck.StoreMark
import com.winhub.hernandez.ui.deck.XMB_FOCUS_OUTLINE
import com.winhub.hernandez.ui.deck.XmbStoreMark
import com.winhub.hernandez.ui.deck.rememberScreenScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

// The Steam-style Home's store shelves (L2 / R2 past Favourites): the covers row of the Home, with one store's whole owned library
// on it, installed games first. A opens that game's page in its store (and only that page: B comes back to this row), Down opens
// the tabs page as it does on the other shelves. The geometry is the Home's own (XmbSteamLogic.kt); the row is drawn here because
// the Home's row is made of the shortcuts a game has once it is installed, and these games may be only owned.

@Composable
internal fun XmbStoreShelfHome(
    store: DeckStore,
    signedIn: Boolean,
    tabs: List<XmbAction>,
    tabSel: Int,
    badges: Map<XmbSteamTab, Int>,
    downloadRing: XmbDownloadRing?,
    canTakeFocus: () -> Boolean,
    focusTick: Int,
    onSignIn: () -> Unit,
) {
    val context = LocalContext.current
    val ss = rememberScreenScale()
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    // The shelf is read again whenever Home comes back to the front: a game installed on its page loses its download mark.
    var reload by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) reload++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var games by remember(store) { mutableStateOf<List<DeckOwnedGame>?>(null) }
    LaunchedEffect(store, signedIn, reload) {
        games = if (!signedIn) emptyList() else withContext(Dispatchers.IO) { deckShelfGames(context, store.screen) }
    }
    val list = games
    val n = list?.size ?: 0

    var target by remember(store) { mutableIntStateOf(0) }
    val pos = remember(store) { mutableFloatStateOf(0f) }
    LaunchedEffect(n) { target = target.coerceIn(0, (n - 1).coerceAtLeast(0)) }
    LaunchedEffect(target, store) {
        animate(pos.floatValue, target.toFloat(), animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)) { v, _ -> pos.floatValue = v }
    }

    // A step along the row sounds like one on the other shelves; a held key sounds no faster than the gate lets it.
    val soundGate = remember { KeyRepeatGate() }
    fun stepTo(to: Int, fresh: Boolean) {
        val next = to.coerceIn(0, (n - 1).coerceAtLeast(0))
        if (next == target) return
        target = next
        if (soundGate.allow(fresh)) DeckSound.play(DeckSound.MOVE)
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(focusTick) { if (canTakeFocus()) runCatching { focus.requestFocus() } }
    val hintInset = LocalXmbHintInset.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { stepTo(target - 1, e.nativeKeyEvent.repeatCount == 0); true }
                    Key.DirectionRight -> { stepTo(target + 1, e.nativeKeyEvent.repeatCount == 0); true }
                    // Down opens the tabs page on its last used tab; a held Down opens it once.
                    Key.DirectionDown -> {
                        if (tabs.isNotEmpty() && e.nativeKeyEvent.repeatCount == 0) tabs[tabSel.coerceIn(0, tabs.lastIndex)].onClick()
                        true
                    }
                    Key.DirectionUp -> true
                    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            DeckSound.play(DeckSound.SELECT)
                            if (!signedIn) onSignIn() else list?.getOrNull(target)?.open?.invoke(context)
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        val clipTop = ss.dp(XMB_PEEK_CLIP).value
        val sl = remember(maxWidth, maxHeight, clipTop, hintInset, ss) {
            XmbSteamLayout(maxWidth.value, maxHeight.value, clipTop, max(hintInset.value, 16f * ss.factor), ss.factor, 48f, 0f)
        }
        val shown = list.orEmpty()
        val row = remember(sl, if (list == null) SKELETON_CARDS else n) { sl.row(if (list == null) SKELETON_CARDS else n) }
        val current = shown.getOrNull(target)

        StoreShelfBackdrop(current?.art, (sl.tabsTop - XMB_STEAM_FADE_LEAD * ss.factor) / maxHeight.value.coerceAtLeast(1f))

        val base by remember { derivedStateOf { floor(pos.floatValue).toInt() } }
        val slots = if (list == null) SKELETON_CARDS else n
        // Only the covers near the cursor are composed; each one's place is read from `pos` in its graphics layer.
        Box(
            Modifier
                .fillMaxSize()
                .draggable(
                    rememberDraggableState { },
                    Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val pitchPx = with(density) { (sl.fW + sl.gap).dp.toPx() }
                        target = (pos.floatValue - velocity / pitchPx * 0.14f).roundToInt().coerceIn(0, (n - 1).coerceAtLeast(0))
                    },
                ),
        ) {
            if (slots > 0) for (i in xmbSteamWindow(base.toFloat(), slots, sl.w, sl.fW + sl.gap)) {
                if (list == null) {
                    key("skeleton_$i") { StoreShelfSkeletonCard(i, pos, sl, row, slots) }
                } else {
                    val game = list[i]
                    key(i, game.title) {
                        StoreShelfCard(game, store, i, pos, sl, row, accent, i == target, slots) {
                            if (i != target) { target = i; DeckSound.play(DeckSound.MOVE) } else { DeckSound.play(DeckSound.SELECT); game.open(context) }
                        }
                    }
                }
            }
        }

        if (current != null) StoreShelfTitle(current, store, sl, row, target, pos, ss)
        if (list != null && n == 0) {
            StoreShelfMessage(
                title = if (signedIn) "No ${store.label} games yet" else "${store.label} isn't signed in",
                body = if (signedIn) "Sync this store's library from Stores and its games will show up here."
                else "Sign in to see everything you own on ${store.label}. Its library stays on this shelf, apart from the other stores.",
                action = if (signedIn) null else "Sign in to ${store.label}",
                onAction = onSignIn,
                top = sl.cardTop + sl.fH / 2f - 52f,
            )
        }
        if (tabs.isNotEmpty()) {
            XmbSteamTabs(
                tabs, tabSel.coerceAtLeast(0), tabSel >= 0, true, badges, downloadRing, sl, accent, ss, { 0f },
            ) { i -> tabs[i].onClick() }
        }
    }
}

/** The shelf's backdrop: the focused game's art, blurred and dimmed, under the Home's gradient to dark navy. */
@Composable
private fun StoreShelfBackdrop(art: String?, fadeFrom: Float) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = art, animationSpec = tween(600), label = "storeShelfArt") { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(10.dp).alpha(0.6f),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(xmbSteamBottomFade(fadeFrom.coerceIn(0.25f, 0.97f))))
    }
}

// A card's place in the row, read in the graphics layer so a swipe re-draws without recomposing. All cards share a bottom edge, so the
// focused (larger) one grows upward.
private fun Modifier.storeShelfSlot(index: Int, pos: androidx.compose.runtime.MutableFloatState, sl: XmbSteamLayout, row: XmbSteamRow, count: Int, focused: Boolean): Modifier = this
    .zIndex(if (focused) 1f else 0f)
    .size(sl.cardW(index, count).dp, sl.fH.dp)
    .graphicsLayer {
        val p = pos.floatValue
        val s = xmbSteamScale(index, p)
        translationX = row.x(index, p).dp.toPx()
        translationY = sl.cardTop.dp.toPx()
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(0f, 1f)
    }

@Composable
private fun StoreShelfCard(
    game: DeckOwnedGame,
    store: DeckStore,
    index: Int,
    pos: androidx.compose.runtime.MutableFloatState,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    accent: Color,
    focused: Boolean,
    count: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(XMB_STEAM_CORNER)
    val src = remember { MutableInteractionSource() }
    val markSize = (sl.fW * 0.20f).coerceIn(13f, 22f).dp
    Box(
        Modifier
            .storeShelfSlot(index, pos, sl, row, count, focused)
            .clip(shape)
            .background(Color(0xFF111111))
            .then(if (focused) Modifier.border(1.5.dp, XMB_FOCUS_OUTLINE, shape) else Modifier)
            .xmbClick(src, onClick),
    ) {
        if (game.art != null) {
            AsyncImage(
                model = game.art,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            XmbMonogram(game.title, accent)
        }
        XmbStoreMark(store.mark(), Modifier.align(Alignment.BottomEnd).padding(4.dp), markSize)
        // Owned but not installed: the download mark, where the Steam Deck draws its own.
        if (!game.installed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size((sl.fW * 0.2f).coerceIn(15f, 22f).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Not installed", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun StoreShelfSkeletonCard(
    index: Int,
    pos: androidx.compose.runtime.MutableFloatState,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    count: Int,
) {
    Box(
        Modifier
            .storeShelfSlot(index, pos, sl, row, count, false)
            .clip(RoundedCornerShape(XMB_STEAM_CORNER))
            .background(Color(0xFF141A26)),
    )
}

/** Under the focused card, left aligned with it: the game's name and where it comes from and whether it is installed. */
@Composable
private fun StoreShelfTitle(
    game: DeckOwnedGame,
    store: DeckStore,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    target: Int,
    pos: androidx.compose.runtime.MutableFloatState,
    ss: com.winhub.hernandez.ui.deck.ScreenScale,
) {
    val context = LocalContext.current
    AnimatedContent(
        targetState = game,
        contentKey = { it.title },
        transitionSpec = { xmbTitleTransition() },
        label = "storeShelfTitle",
        modifier = Modifier
            .offset(0.dp, sl.titleY.dp - ss.dp(XMB_STEAM_TITLE_NUDGE))
            .width(sl.titleW.dp)
            .graphicsLayer {
                translationX = sl.titleX(row, target, pos.floatValue).dp.toPx() + ss.dp(XMB_STEAM_TITLE_INSET).toPx()
            },
    ) { g ->
        Column {
            Text(
                g.title, color = Color.White, fontSize = ss.sp(16f), fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = ss.dp(XMB_STEAM_TITLE_NUDGE)),
                style = XMB_STEAM_TEXT.copy(lineHeight = ss.sp(15f), lineHeightStyle = XMB_STEAM_TOP),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(2.dp))
                Text(
                    listOfNotNull(
                        store.label,
                        if (g.installed) "Installed" else "Not installed",
                        g.sizeBytes.takeIf { it > 0L }?.let { Formatter.formatShortFileSize(context, it) },
                    ).joinToString(" · ").uppercase(),
                    color = XMB_STEAM_MUTED, fontSize = ss.sp(10f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = XMB_STEAM_TEXT.copy(lineHeight = ss.sp(10f), lineHeightStyle = XMB_STEAM_TIGHT),
                )
            }
        }
    }
}

/** What stands where the covers would be when the shelf has none: signed out, or nothing synced yet. */
@Composable
private fun StoreShelfMessage(title: String, body: String, action: String?, onAction: () -> Unit, top: Float) {
    val cs = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().offset(y = top.dp).padding(horizontal = 48.dp),
    ) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, style = XMB_STEAM_TEXT)
        Text(body, color = XMB_STEAM_MUTED, fontSize = 14.sp, textAlign = TextAlign.Center, style = XMB_STEAM_TEXT)
        if (action != null) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .height(44.dp)
                    .clip(shape)
                    .background(cs.primary)
                    .xmbClick(remember { MutableInteractionSource() }, onAction)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, color = cs.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

private const val SKELETON_CARDS = 9

internal fun DeckStore.mark(): StoreMark = when (this) {
    DeckStore.STEAM -> StoreMark.STEAM
    DeckStore.EPIC -> StoreMark.EPIC
    DeckStore.GOG -> StoreMark.GOG
    DeckStore.AMAZON -> StoreMark.AMAZON
    DeckStore.XBOX -> StoreMark.XBOX
}

/**
 * Which shelf L2 / R2 are on, in the Steam style (its Home hides the shelf tabs): small letter-spaced names in the shape of the
 * text tabs below the covers, the current one in a thin white outline. A tap picks a shelf; the strip never takes D-pad focus.
 */
@Composable
internal fun XmbShelfStrip(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ss = rememberScreenScale()
    val accent = MaterialTheme.colorScheme.primary
    // Below the shell's floating tab bar, which takes XMB_PEEK_CLIP at the top.
    Row(modifier.padding(top = ss.dp(XMB_PEEK_CLIP + 6f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(12.dp)
            // The stores are set a little apart from the shelves of the games on this device.
            if (i == 3) Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(shape)
                    .background(if (on) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, Color.White, shape) else Modifier)
                    .xmbClick(remember { MutableInteractionSource() }) { onSelect(i) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    label.uppercase(),
                    color = if (on) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = ss.sp(10f),
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    style = XMB_STEAM_TEXT,
                )
            }
        }
    }
}
