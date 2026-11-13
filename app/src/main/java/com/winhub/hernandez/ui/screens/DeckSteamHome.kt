package com.winhub.hernandez.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.winhub.hernandez.ui.SocialHubScreen
import com.winhub.hernandez.ui.moveFocusSafely
import kotlinx.coroutines.delay

/** The pages under the Steam-style Home, in pill-bar order (the numbers are what [DeckSteamHome] gets as `pageTab`). */
internal enum class SteamTab(val label: String, val icon: ImageVector) {
    STORES("Stores", Icons.Filled.Storefront),
    DOWNLOADS("Downloads", Icons.Filled.Download),
    FRIENDS("Friends", Icons.Filled.Person),
    SOCIAL("Social", Icons.Filled.People),
}

private val SteamPillInk = Color.White.copy(alpha = 0.78f)
private val SteamPillFill = Color.Black.copy(alpha = 0.5f)

private fun isConfirmKey(k: Key): Boolean =
    k == Key.ButtonA || k == Key.Enter || k == Key.NumPadEnter || k == Key.DirectionCenter

/**
 * The Steam-style Home (Settings > Appearance > Home style): the chosen game's name and facts up top, one low row of
 * covers under them with the All Games card last, and the peek tabs (Stores, Downloads, Friends, Social) under the row.
 * Down, a swipe up or a tap on a peek tab slides the tabs page up over Home; B, Up from its bar or the bar's close
 * brings Home back on the same game. The whole Home is ONE focus target ([focus]) so the pad never fights per-tile focus.
 */
@Composable
internal fun DeckSteamHome(
    modifier: Modifier = Modifier,
    entries: List<HubEntry>,
    loaded: Boolean,
    selected: Int,
    allFocused: Boolean,
    favs: Set<String>,
    details: List<Pair<String, String>>,
    info: (@Composable (Int) -> Unit)?,
    allCovers: List<Bitmap>,
    storeCards: List<DeckHomeCard>,
    pageOpen: Boolean,
    pageTab: Int,
    barFocus: FocusRequester,
    onBarFocus: (Boolean) -> Unit,
    onPage: (Boolean) -> Unit,
    onPageTab: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onAllFocus: (Boolean) -> Unit,
    onOpenAll: () -> Unit,
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    onFavorite: () -> Unit,
    onAdd: () -> Unit,
    focus: FocusRequester,
    enabled: Boolean,
    launching: Boolean,
    launchT: () -> Float,
) {
    val n = entries.size
    // The All Games card is one more slot after the last game.
    val slots = if (n > 0) n + 1 else 0
    val pos = if (allFocused && n > 0) n else selected.coerceIn(0, (n - 1).coerceAtLeast(0))
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var vAcc by remember { mutableFloatStateOf(0f) }

    fun move(to: Int) {
        if (slots == 0) return
        val t = to.coerceIn(0, slots - 1)
        if (t == pos) return
        DeckSound.play(DeckSound.MOVE)
        if (t >= n) {
            // The last game stays the selection behind the All Games card.
            if (selected != n - 1) onSelect(n - 1)
            onAllFocus(true)
        } else {
            onAllFocus(false)
            if (t != selected) onSelect(t)
        }
    }

    // The row's contentPadding centres the chosen slot; scrolling it to the start of the list puts it in the middle.
    LaunchedEffect(pos, slots) { if (slots > 0) listState.animateScrollToItem(pos) }

    fun handleKey(e: KeyEvent): Boolean {
        if (!enabled || pageOpen || e.type != KeyEventType.KeyDown) return false
        if (launching) return true
        val fresh = e.nativeKeyEvent.repeatCount == 0
        return when {
            e.key == Key.DirectionLeft -> { move(pos - 1); true }
            e.key == Key.DirectionRight -> { move(pos + 1); true }
            e.key == Key.DirectionDown -> { onPage(true); true }
            e.key == Key.DirectionUp -> true
            isConfirmKey(e.key) -> {
                if (fresh && loaded) {
                    if (n == 0) onAdd() else if (allFocused) onOpenAll() else onPlay()
                }
                true
            }
            e.key == Key.ButtonX -> {
                if (fresh && loaded) { if (n > 0 && !allFocused) onOptions() else onAdd() }
                true
            }
            e.key == Key.ButtonY -> { if (fresh && n > 0 && !allFocused) onFavorite(); true }
            else -> false
        }
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - launchT() }
                .focusProperties { canFocus = enabled && !pageOpen }
                .focusRequester(focus)
                .onPreviewKeyEvent { handleKey(it) }
                .focusable()
                .draggable(
                    rememberDraggableState { delta -> vAcc += delta },
                    Orientation.Vertical,
                    onDragStarted = { vAcc = 0f },
                    onDragStopped = {
                        if (vAcc < -with(density) { 40.dp.toPx() } && !pageOpen && enabled) onPage(true)
                    },
                ),
        ) {
            val coverH = (maxHeight * 0.30f).coerceIn(92.dp, 190.dp)
            val coverW = coverH * 2f / 3f
            val sidePad = ((maxWidth - coverW) / 2f).coerceAtLeast(0.dp)
            val tall = maxHeight >= 400.dp
            val current = entries.getOrNull(selected)

            // The chosen game: name, facts, and (room permitting) its genres and description.
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 56.dp)
                    .widthIn(max = maxWidth * 0.55f),
            ) {
                when {
                    !loaded -> Unit
                    n == 0 -> {
                        Text("Your library is empty", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("Press A, or tap here, to add a game", color = SteamPillInk, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 6.dp).pointerInput(Unit) { detectTapGestures { onAdd() } })
                    }
                    allFocused -> {
                        Text("All games", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text("$n in your library - A to browse them all", color = SteamPillInk, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    current != null -> {
                        Text(
                            current.title,
                            color = Color.White,
                            fontSize = if (tall) 34.sp else 28.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = if (tall) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (details.isNotEmpty()) Text(
                            details.take(3).joinToString("   ") { (k, v) -> "$k: $v" },
                            color = SteamPillInk,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (tall) info?.invoke(2)
                    }
                }
            }

            // The covers: the chosen one larger, with the theme-colour stroke and glow; the rest stay visible either side.
            if (slots > 0) LazyRow(
                state = listState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
                    .fillMaxWidth()
                    .height(coverH * 1.2f + 72.dp),
                contentPadding = PaddingValues(horizontal = sidePad, vertical = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                itemsIndexed(entries) { i, e ->
                    SteamTile(chosen = !allFocused && i == selected, w = coverW, h = coverH, onTap = {
                        if (i == pos) { if (loaded) onPlay() } else move(i)
                    }) {
                        SteamCoverFace(e, e.game?.shortcutPath in favs)
                    }
                }
                item {
                    SteamTile(chosen = allFocused, w = coverW, h = coverH, onTap = {
                        if (allFocused) onOpenAll() else move(n)
                    }) {
                        SteamAllFace(n, allCovers)
                    }
                }
            }

            // Peek tabs and the pad hints under the row.
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SteamTab.values().forEach { tab ->
                        SteamPill(tab, selected = false, ringed = false) { onPageTab(tab.ordinal); onPage(true) }
                    }
                }
                Text(
                    if (n > 0) "A Play    X Options    Y Favourite    Down More" else "A Add game    Down More",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = pageOpen,
            enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(160)),
        ) {
            SteamTabsPage(
                tab = pageTab.coerceIn(0, SteamTab.values().lastIndex),
                barFocus = barFocus,
                onBarFocus = onBarFocus,
                onTab = onPageTab,
                onClose = { onPage(false) },
                storeCards = storeCards,
            )
        }
    }
}

/** One slot of the covers row: its place, the chosen one's enlargement, glow and ring, and a tap target. */
@Composable
private fun SteamTile(
    chosen: Boolean,
    w: Dp,
    h: Dp,
    onTap: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val accent = Deck.primary
    val scale by animateFloatAsState(if (chosen) 1.2f else 1f, tween(180), label = "steamTileScale")
    val tapNow by rememberUpdatedState(onTap)
    val tileShape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(w, h)
            .zIndex(if (chosen) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
                shadowElevation = if (chosen) 14.dp.toPx() else 0f
                ambientShadowColor = accent
                spotShadowColor = accent
                shape = tileShape
                clip = false
            }
            .drawBehind {
                if (chosen) {
                    val radius = maxOf(size.width, size.height) * 0.7f
                    drawCircle(
                        brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.4f), Color.Transparent), center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
                }
            }
            .clip(tileShape)
            .background(Deck.surface)
            .pointerInput(Unit) { detectTapGestures { tapNow() } },
    ) {
        content()
        if (chosen) Box(Modifier.fillMaxSize().border(3.dp, accent, tileShape))
    }
}

@Composable
private fun BoxScope.SteamCoverFace(entry: HubEntry, favourite: Boolean) {
    val bmp = entry.bitmap
    if (bmp != null) {
        Image(
            bitmap = remember(bmp) { bmp.asImageBitmap() },
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val hue = hueOf(entry.title) * 360f
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.hsv(hue, 0.5f, 0.45f), Color.hsv(hue, 0.6f, 0.22f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                entry.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
    if (favourite) Icon(
        Icons.Filled.Star,
        contentDescription = "Favourite",
        tint = Color(0xFFFFD54F),
        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
    )
}

/** The All Games card: a mosaic of the first covers under its label. */
@Composable
private fun BoxScope.SteamAllFace(count: Int, covers: List<Bitmap>) {
    Column(Modifier.fillMaxSize()) {
        for (r in 0..1) Row(Modifier.weight(1f).fillMaxWidth()) {
            for (c in 0..1) {
                val bmp = covers.getOrNull(r * 2 + c)
                Box(Modifier.weight(1f).fillMaxSize()) {
                    if (bmp != null) Image(
                        bitmap = remember(bmp) { bmp.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Apps, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Text("All games", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text("$count", color = SteamPillInk, fontSize = 11.sp)
        }
    }
}

/** A tab's pill; [selected] fills it dark and [ringed] adds the theme-colour ring (the pill bar has focus). */
@Composable
private fun SteamPill(tab: SteamTab, selected: Boolean, ringed: Boolean, onClick: () -> Unit) {
    val tapNow by rememberUpdatedState(onClick)
    val accent = Deck.primary
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) SteamPillFill else Color.Black.copy(alpha = 0.3f))
            .then(if (selected && ringed) Modifier.border(2.dp, accent, CircleShape) else Modifier)
            .pointerInput(Unit) { detectTapGestures { tapNow() } }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = null, tint = if (selected) Color.White else SteamPillInk, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            tab.label,
            color = if (selected) Color.White else SteamPillInk,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * The page that slides up over Home: a pill bar (one focus target: Left/Right change the tab, Down or A walk into the
 * page, Up or B close it) over the tab's page. Up from the top of a page lands on the bar again.
 */
@Composable
private fun SteamTabsPage(
    tab: Int,
    barFocus: FocusRequester,
    onBarFocus: (Boolean) -> Unit,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    storeCards: List<DeckHomeCard>,
) {
    val focusManager = LocalFocusManager.current
    val tabs = SteamTab.values()
    var barFocused by remember { mutableStateOf(false) }
    // The page has just been composed: give it a frame to attach before taking focus.
    LaunchedEffect(Unit) {
        delay(90)
        runCatching { barFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Deck.background.copy(alpha = 0.96f))
            // Taps on the page never fall through to the Home under it.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 52.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .focusRequester(barFocus)
                    .onFocusChanged { barFocused = it.isFocused; onBarFocus(it.isFocused) }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val fresh = e.nativeKeyEvent.repeatCount == 0
                        when {
                            e.key == Key.DirectionLeft -> {
                                if (tab > 0) { DeckSound.play(DeckSound.MOVE); onTab(tab - 1) }
                                true
                            }
                            e.key == Key.DirectionRight -> {
                                if (tab < tabs.lastIndex) { DeckSound.play(DeckSound.MOVE); onTab(tab + 1) }
                                true
                            }
                            e.key == Key.DirectionUp || e.key == Key.ButtonB || e.key == Key.Escape -> {
                                if (fresh) onClose()
                                true
                            }
                            e.key == Key.DirectionDown || isConfirmKey(e.key) -> {
                                if (e.key == Key.DirectionDown || fresh) focusManager.moveFocusSafely(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { t ->
                    SteamPill(t, selected = t.ordinal == tab, ringed = barFocused) { onTab(t.ordinal) }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tabs[tab]) {
                SteamTab.STORES -> SteamStoresPane(storeCards)
                SteamTab.DOWNLOADS -> DeckDownloadsPanel(onBack = onClose)
                SteamTab.FRIENDS -> DeckSteamFriendsPane()
                SteamTab.SOCIAL -> SocialHubScreen(deck = true, onExit = null)
            }
        }
        Text(
            "A Select    B Back    Left/Right Tabs    Up Close",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 5.dp),
        )
    }
}

/** Stores tab: Add game, then each store (signed in or not), as a row of cards the pad walks along. */
@Composable
private fun SteamStoresPane(cards: List<DeckHomeCard>) {
    LazyRow(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(cards) { card -> SteamStoreCard(card) }
    }
}

@Composable
private fun SteamStoreCard(card: DeckHomeCard) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "steamStoreScale")
    val tapNow by rememberUpdatedState(card.onClick)
    val cardShape = RoundedCornerShape(10.dp)
    val logo = if (card.logo != null) rememberDeckLogo(card.logo) else null
    Box(
        Modifier
            .size(220.dp, 138.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(cardShape)
            .background(Brush.linearGradient(card.colors))
            .then(
                if (focused) Modifier.border(3.dp, Deck.primary, cardShape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), cardShape),
            )
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && isConfirmKey(e.key)) {
                    if (e.nativeKeyEvent.repeatCount == 0) tapNow()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { tapNow() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            if (logo != null) Image(
                bitmap = logo,
                contentDescription = card.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(46.dp).widthIn(max = 150.dp),
            ) else Icon(card.icon, contentDescription = card.label, tint = Color.White, modifier = Modifier.size(40.dp))
            Text(
                card.label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                card.subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
