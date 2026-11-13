package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.LocalTopBarOverlayInset
import com.winhub.hernandez.ui.deck.DeckFloatingHint
import com.winhub.hernandez.ui.deck.GlyphKind
import com.winhub.hernandez.ui.deck.LibrarySort
import com.winhub.hernandez.ui.deck.PlayStats
import com.winhub.hernandez.ui.deck.XmbStoreMark
import com.winhub.hernandez.ui.deck.gameSortKeyOf
import com.winhub.hernandez.ui.deck.lastPlayedLabel
import com.winhub.hernandez.ui.deck.playtimeLabel
import com.winhub.hernandez.ui.deck.readPlayStats
import com.winhub.hernandez.ui.deck.rememberScreenScale
import com.winhub.hernandez.ui.deck.sortGames
import com.winhub.hernandez.ui.deck.storeLabelOf
import com.winhub.hernandez.ui.deck.storeMarkOf
import com.winhub.hernandez.ui.deck.warmStoreMarks

/** [shortcuts] newest first, for a host whose list is in some other order (the row takes the first [XMB_ROW_LIMIT]). */
internal fun xmbRecentFirst(context: Context, shortcuts: List<Shortcut>): List<Shortcut> {
    val stats = readPlayStats(context, shortcuts)
    return sortGames(shortcuts, LibrarySort.RECENT) { gameSortKeyOf(it, stats) }
}

private const val PREF_VIEW = "xmb_all_games_view"
private const val PREF_SORT = "xmb_all_games_sort"

private enum class AllView(val pref: String, @StringRes val label: Int, val icon: ImageVector) {
    GRID("grid", R.string.xmb_all_view_grid, Icons.Filled.GridView),
    LIST("list", R.string.xmb_all_view_list, Icons.AutoMirrored.Filled.List),
    BIG("big", R.string.xmb_all_view_big, Icons.Filled.ViewDay);

    fun next(): AllView = entries[(ordinal + 1) % entries.size]
}

// The sorts the page offers, in menu order. The names are LibrarySort's, which is what is saved.
private val ALL_SORTS = listOf(
    LibrarySort.RECENT, LibrarySort.NAME, LibrarySort.NAME_DESC, LibrarySort.PLAYTIME, LibrarySort.STORE, LibrarySort.CONTAINER,
)

@StringRes
private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENT -> R.string.xmb_all_sort_recent
    LibrarySort.NAME -> R.string.xmb_all_sort_name
    LibrarySort.NAME_DESC -> R.string.xmb_all_sort_name_desc
    LibrarySort.PLAYTIME -> R.string.xmb_all_sort_playtime
    LibrarySort.STORE -> R.string.xmb_all_sort_store
    LibrarySort.CONTAINER -> R.string.xmb_all_sort_container
}

private val PAGE_TEXT = Color(0xFFEDEDED)
private val PAGE_MUTED = Color(0xFFB4B4B4)

/**
 * Every game, on one full-screen page in the XMB's look (Home's backdrop of the highlighted game, white type, the accent ring). It opens from the
 * All Games card at the end of the games row and closes with Back / B onto the same card. The cursor is one index into the
 * sorted list, moved by the D-pad and by taps; the page owns the keys while it is up. Three views ([AllView]) and the sorts in
 * [ALL_SORTS] are kept between visits. A game runs through [onPlay], the same launch the games row uses.
 *
 * The page has its own header under the see-through top bar: a back arrow, the title and count, and the View / Sort hints. The
 * covers scroll under it and fade into it.
 */
@Composable
internal fun XmbAllGamesPage(
    games: List<Shortcut>,
    onPlay: (Shortcut) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val ss = rememberScreenScale()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var view by remember { mutableStateOf(AllView.entries.firstOrNull { it.pref == prefs.getString(PREF_VIEW, null) } ?: AllView.GRID) }
    var sort by remember { mutableStateOf(ALL_SORTS.firstOrNull { it.name == prefs.getString(PREF_SORT, null) } ?: LibrarySort.RECENT) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuSel by remember { mutableIntStateOf(0) }
    // The cursor is kept by path, so it follows its game through a re-sort, a view change and a refreshed list.
    var selPath by rememberSaveable { mutableStateOf<String?>(null) }

    val stats = remember(games) { readPlayStats(context, games) }
    val sorted = remember(games, stats, sort) { sortGames(games, sort) { gameSortKeyOf(it, stats) } }
    val sel = remember(sorted, selPath) { sorted.indexOfFirst { it.file.path == selPath }.coerceAtLeast(0) }

    val setView = { v: AllView -> view = v; prefs.edit().putString(PREF_VIEW, v.pref).apply() }
    val setSort = { s: LibrarySort -> sort = s; menuOpen = false; prefs.edit().putString(PREF_SORT, s.name).apply() }
    val openMenu = { menuSel = ALL_SORTS.indexOf(sort).coerceAtLeast(0); menuOpen = true }

    LaunchedEffect(Unit) { warmStoreMarks(context) }
    BackHandler { if (menuOpen) menuOpen = false else onClose() }

    // The page owns the keys: it takes focus when it opens and again whenever the app comes back to it (after a game exits).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focusRequester.requestFocus() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) runCatching { focusRequester.requestFocus() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasPad = rememberGamepadConnected()

    val topInset = LocalTopBarOverlayInset.current
    val padH = ss.dp(16f)
    val gap = ss.dp(12f)
    // The header sits under the see-through top bar. The covers scroll under it (the grid starts at the page's top) and fade out
    // into its band.
    val header = remember(topInset, ss.factor) { xmbAllHeader(topInset.value, ss.factor) }
    val bottomPad = 16.dp

    val gridState = rememberLazyGridState()
    val moveSel: (Int) -> Unit = { i -> sorted.getOrNull(i)?.let { selPath = it.file.path } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = maxWidth - padH * 2
        // Grid: always six covers across when each can stay about 80 dp wide (any landscape screen), fewer on a narrow portrait
        // one but never under three. Big art keeps its own cover width, and the list is one column.
        val bigCell = ss.dp(176f)
        val cols = when (view) {
            AllView.GRID -> xmbGridColumns(avail.value, gap.value, ss.dp(80f).value)
            AllView.BIG -> ((avail + gap) / (bigCell + gap)).toInt().coerceAtLeast(1)
            AllView.LIST -> 1
        }
        // The header's hints carry their names only where there is room for them.
        val wide = maxWidth >= 600.dp

        val bottomMarginPx = with(density) { (bottomPad - 8.dp).coerceAtLeast(0.dp).toPx() }
        val topMarginPx = with(density) { header.band.dp.toPx() }
        LaunchedEffect(sel, view, sorted) {
            // The new columns are laid out a frame after this runs. The grid's first item is the header spacer, so a game is at i + 1.
            withFrameNanos { }
            if (sorted.isNotEmpty()) gridState.reveal(sel + 1, topMarginPx, bottomMarginPx)
        }

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (menuOpen) {
                        return@onPreviewKeyEvent when (e.key) {
                            Key.DirectionUp -> { menuSel = (menuSel - 1).coerceAtLeast(0); true }
                            Key.DirectionDown -> { menuSel = (menuSel + 1).coerceAtMost(ALL_SORTS.lastIndex); true }
                            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { setSort(ALL_SORTS[menuSel]); true }
                            Key.ButtonB, Key.Escape, Key.ButtonY -> { menuOpen = false; true }
                            else -> false
                        }
                    }
                    val page = cols * 3
                    when (e.key) {
                        Key.DirectionLeft -> { moveSel(xmbGridMove(sel, XmbMove.LEFT, cols, sorted.size, page)); true }
                        Key.DirectionRight -> { moveSel(xmbGridMove(sel, XmbMove.RIGHT, cols, sorted.size, page)); true }
                        Key.DirectionUp -> { moveSel(xmbGridMove(sel, XmbMove.UP, cols, sorted.size, page)); true }
                        Key.DirectionDown -> { moveSel(xmbGridMove(sel, XmbMove.DOWN, cols, sorted.size, page)); true }
                        Key.ButtonL1 -> { moveSel(xmbGridMove(sel, XmbMove.PAGE_UP, cols, sorted.size, page)); true }
                        Key.ButtonR1 -> { moveSel(xmbGridMove(sel, XmbMove.PAGE_DOWN, cols, sorted.size, page)); true }
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { sorted.getOrNull(sel)?.let(onPlay); true }
                        Key.ButtonX -> { setView(view.next()); true }
                        Key.ButtonY -> { openMenu(); true }
                        Key.ButtonB, Key.Escape -> { onClose(); true }
                        else -> false
                    }
                }
                // Nothing under the page may react to a touch that lands on it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // The home backdrop (black, the highlighted game's art blurred and dimmed, crossfading as the cursor moves), the same
            // composable the games row uses. Nothing is picked when the list is empty.
            XmbHomeBackdrop(sorted.getOrNull(sel)?.icon)

            // The covers start at the page's top and scroll under the header: the first item is a spacer as tall as the header's
            // band, so the first row rests below it, and what scrolls up into the band fades out (an offscreen layer masked by a
            // gradient, as the cards peeking above the games row fade under the tab bar).
            val fadeStartPx = with(density) { header.fadeStart.dp.toPx() }
            val fadeEndPx = with(density) { header.band.dp.toPx() }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(start = padH, end = padH, top = 0.dp, bottom = bottomPad),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = fadeStartPx, endY = fadeEndPx),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                // The row gap follows the spacer too, so it is the band less that gap.
                item(key = "xmb-all-header", span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.fillMaxWidth().height((header.band.dp - gap).coerceAtLeast(0.dp)))
                }
                items(sorted.size, key = { sorted[it].file.path }) { i ->
                    val s = sorted[i]
                    val pick = { selPath = s.file.path; onPlay(s) }
                    val st = stats[s.file.path]
                    when (view) {
                        AllView.GRID -> CoverCell(s, i == sel, accent, markSize = ss.dp(25f), big = false, stat = null, onClick = pick)
                        AllView.BIG -> CoverCell(s, i == sel, accent, markSize = ss.dp(35f), big = true, stat = st, onClick = pick)
                        AllView.LIST -> ListRow(s, i == sel, accent, st, markSize = ss.dp(22.5f), onClick = pick)
                    }
                }
            }

            // The header, drawn over the grid under the top bar: the back arrow and the title and count on the
            // left, then View / Sort as the shell's floating hints (a button badge only with a pad attached, the icon always, the
            // current view / sort name where there is room). The hints pad themselves by 8 dp, so the row starts that much earlier
            // to line up with the grid. The empty tap handler keeps a tap on its blank part from reaching a faded cover under it.
            val hintTap = Modifier.heightIn(min = 40.dp).widthIn(min = 40.dp)
            val backSrc = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = (padH - 12.dp).coerceAtLeast(0.dp), end = (padH - ss.dp(8f)).coerceAtLeast(0.dp), top = header.top.dp)
                    .height(header.height.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Not a D-pad stop (B closes the page, as this does); a 48 dp round tap area around the 24 dp arrow, whose glyph lines
                // up with the grid's left edge.
                Box(
                    Modifier.size(48.dp).clip(CircleShape).xmbClick(backSrc) { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.xmb_all_back),
                        tint = Color(0xFFDDDDDD), modifier = Modifier.size(24.dp),
                    )
                }
                Row(Modifier.weight(1f).padding(start = ss.dp(4f)), verticalAlignment = Alignment.Bottom) {
                    Text(
                        stringResource(R.string.xmb_all_games), color = Color.White, fontSize = ss.sp(16f),
                        fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        context.resources.getQuantityString(R.plurals.xmb_all_games_count, sorted.size, sorted.size),
                        color = PAGE_MUTED, fontSize = ss.sp(11f), maxLines = 1, modifier = Modifier.padding(start = ss.dp(8f), bottom = 1.dp),
                    )
                }
                DeckFloatingHint(
                    "X".takeIf { hasPad }, GlyphKind.X, stringResource(view.label).takeIf { wide }, { setView(view.next()) }, hintTap,
                    icon = view.icon, description = stringResource(R.string.xmb_all_view) + ": " + stringResource(view.label),
                )
                DeckFloatingHint(
                    "Y".takeIf { hasPad }, GlyphKind.Y, stringResource(sort.labelRes()).takeIf { wide }, { openMenu() }, hintTap,
                    icon = Icons.Filled.SwapVert, description = stringResource(R.string.xmb_all_sort) + ": " + stringResource(sort.labelRes()),
                )
            }

            if (menuOpen) {
                // A tap off the menu closes it.
                val off = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxSize().xmbClick(off) { menuOpen = false })
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = (header.top + header.height).dp, end = padH)
                        .widthIn(min = 200.dp)
                        .clip(shape)
                        .background(Color(0xEE16171B))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                        .padding(6.dp),
                ) {
                    ALL_SORTS.forEachIndexed { i, s ->
                        val src = remember { MutableInteractionSource() }
                        val on = i == menuSel
                        Text(
                            stringResource(s.labelRes()),
                            color = if (s == sort) accent else PAGE_TEXT,
                            fontSize = 15.sp,
                            fontWeight = if (s == sort) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                                .xmbClick(src) { setSort(s) }
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                }
            }
        }
    }
}

// Scrolls just far enough to bring item [index] fully into view, leaving [topMarginPx] clear above it (the header band the covers
// scroll under) and [bottomMarginPx] under it (the page's bottom padding); an item that isn't composed at all is
// jumped to first, which puts it at the very top, and then moved down clear of the header. The grid has no top content padding, so
// the item offsets are measured from the grid's top edge.
private suspend fun LazyGridState.reveal(index: Int, topMarginPx: Float, bottomMarginPx: Float) {
    val seen = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    val info = if (seen != null) seen else {
        scrollToItem(index)
        withFrameNanos { }
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }
    val cellTop = info.offset.y.toFloat()
    val d = xmbRevealDelta(cellTop, cellTop + info.size.height, layoutInfo.viewportSize.height.toFloat(), topMarginPx, bottomMarginPx)
    if (d != 0f) animateScrollBy(d)
}

/** A cover with its name under it, for the grid and the big-art views. */
@Composable
private fun CoverCell(s: Shortcut, selected: Boolean, accent: Color, markSize: Dp, big: Boolean, stat: PlayStats?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val src = remember { MutableInteractionSource() }
    val mark = remember(s) { storeMarkOf(s) }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(if (selected) Modifier.shadow(12.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
                .clip(shape)
                .background(Color(0xFF111111))
                .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
                .xmbClick(src, onClick),
        ) {
            val bmp = s.icon
            if (bmp != null) {
                Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = s.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                XmbMonogram(s.name, accent)
            }
            XmbStoreMark(mark, Modifier.align(Alignment.BottomEnd).padding(5.dp), markSize)
        }
        Text(
            s.name, color = if (selected) Color.White else PAGE_TEXT, fontSize = if (big) 14.sp else 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
        if (big) {
            val played = remember(stat) { playtimeLabel(stat?.ms ?: 0L) }
            Text(played, color = PAGE_MUTED, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}

/** One row of the list view: a small cover, the name, and where it is from and how long it has been played. */
@Composable
private fun ListRow(s: Shortcut, selected: Boolean, accent: Color, stat: PlayStats?, markSize: Dp, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(12.dp)
    val coverShape = RoundedCornerShape(6.dp)
    val src = remember { MutableInteractionSource() }
    val mark = remember(s) { storeMarkOf(s) }
    val subtitle = remember(s, stat) {
        listOfNotNull(storeLabelOf(s), playtimeLabel(stat?.ms ?: 0L), lastPlayedLabel(context, stat?.lastPlayed ?: 0L)).joinToString(" · ")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .then(if (selected) Modifier.border(2.dp, accent, shape) else Modifier)
            .xmbClick(src, onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(48.dp).aspectRatio(2f / 3f).clip(coverShape).background(Color(0xFF111111))) {
            val bmp = s.icon
            if (bmp != null) {
                Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = s.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF0A0A0A)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(xmbInitials(s.name), color = Color.White.copy(alpha = 0.92f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
            XmbStoreMark(mark, Modifier.align(Alignment.BottomEnd).padding(2.dp), markSize)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.name, color = if (selected) Color.White else PAGE_TEXT, fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = PAGE_MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
