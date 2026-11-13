package com.winhub.hernandez.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.winhub.hernandez.R
import com.winhub.hernandez.store.DownloadActions
import com.winhub.hernandez.store.DownloadCoverArt
import com.winhub.hernandez.store.download.DownloadEntry
import com.winhub.hernandez.store.download.DownloadRegistry
import com.winhub.hernandez.store.download.DownloadState
import com.winhub.hernandez.store.download.Store
import com.winhub.hernandez.store.download.StoreStyle
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.screens.XMB_ARMED_MS
import com.winhub.hernandez.ui.screens.XMB_TWO_COL_GAP_DP
import com.winhub.hernandez.ui.screens.XMB_TWO_COL_MAX_WIDTH_DP
import com.winhub.hernandez.ui.screens.XMB_TWO_COL_TO_BAR
import com.winhub.hernandez.ui.screens.XmbDlAction
import com.winhub.hernandez.ui.screens.XmbDownloadRing
import com.winhub.hernandez.ui.screens.XmbDlSection
import com.winhub.hernandez.ui.screens.xmbClick
import com.winhub.hernandez.ui.screens.xmbDlActions
import com.winhub.hernandez.ui.screens.xmbDlCards
import com.winhub.hernandez.ui.screens.xmbDlCursor
import com.winhub.hernandez.ui.screens.xmbDlGroups
import com.winhub.hernandez.ui.screens.xmbDlInnerClamp
import com.winhub.hernandez.ui.screens.xmbDlLines
import com.winhub.hernandez.ui.screens.xmbDlNeedsConfirm
import com.winhub.hernandez.ui.screens.xmbDlProgress
import com.winhub.hernandez.ui.screens.xmbDlQuickAction
import com.winhub.hernandez.ui.screens.xmbDlSizeText
import com.winhub.hernandez.ui.screens.xmbDlSlot
import com.winhub.hernandez.ui.screens.xmbDlSpeedText
import com.winhub.hernandez.ui.screens.xmbRingOf
import com.winhub.hernandez.ui.screens.xmbTwoColCol
import com.winhub.hernandez.ui.screens.xmbTwoColColumns
import com.winhub.hernandez.ui.screens.xmbTwoColSide
import com.winhub.hernandez.ui.screens.xmbTwoColVertical
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The thin light outline of the card (and the button) under the cursor, as on the Steam Home's covers. */
private val XMB_DL_OUTLINE = Color.White.copy(alpha = 0.92f)

/** The outline of a card whose buttons hold the cursor: dimmer, the button carries the bright one. */
private val XMB_DL_OUTLINE_INNER = Color.White.copy(alpha = 0.45f)

/** Light text for titles, and the dimmer one for the rest. */
private val XMB_DL_INK = Color.White.copy(alpha = 0.95f)
private val XMB_DL_DIM = Color.White.copy(alpha = 0.64f)

/** Progress and "installed" green (the same green as the A hint), the amber of a pause or an update, the red of a failure. */
private val XMB_DL_GREEN = Color(0xFF3DDC84)
private val XMB_DL_AMBER = Color(0xFFFFAA00)
private val XMB_DL_RED = Color(0xFFFF8A80)

/**
 * The ring of the Downloads tab (see [xmbRingOf]), live: null while no download is running, paused or queued. The progress moves in whole
 * percents, so the Home does not recompose on every byte the registry reports; the ring itself animates between them.
 */
@Composable
internal fun rememberXmbDownloadRing(): XmbDownloadRing? {
    val flow = remember {
        DownloadRegistry.entries
            .map { list -> xmbRingOf(list)?.let { r -> r.copy(progress = (r.progress * 100f).toInt() / 100f) } }
            .distinctUntilChanged()
    }
    val ring by flow.collectAsStateWithLifecycle(initialValue = xmbRingOf(DownloadRegistry.entries.value))
    return ring
}

/**
 * The Downloads tab of the XMB Home's tabs page: the cross-store Download Manager as a page of the Home rather than a window of its own.
 * Sections (downloading, queued, stopped, installed) under full-width headings, their cards two to a line (one on a narrow screen), all of
 * a width: cover, name, the store's mark, a progress bar with the sizes and the speed, and the card's buttons. [focus] is what the host
 * gives a page (see [XmbTabsPageFocus]).
 *
 * The D-pad: Left / Right between the two columns, Up / Down between the lines (across the headings; into a shorter line it lands on its
 * last card, and keeps the column for the next line), Up from the first line goes to the pill bar. A steps into a card's buttons, where
 * Left / Right move between them and A presses one; B steps back out (and, from a card, closes the page as the host does). X presses the card's main
 * button without stepping in (pause / resume, start next, add to games, dismiss). A press that cannot be undone (cancel a download,
 * uninstall a game) asks for a second one. Everything is tappable too; a tap on the card opens the game's page in its store.
 */
@Composable
internal fun XmbDownloadsPage(focus: XmbTabsPageFocus) {
    val context = LocalContext.current
    val actions = remember(context) { context.findActivity()?.let { DownloadActions(it) } }
    // Seeds the durable library and the store libraries before the first frame reads the registry (as the Activity does in onCreate), so the
    // page does not flash its empty state; the calls are idempotent, so once per process or once per composition is the same.
    remember(context) { runCatching { DownloadActions.prepare(context) } }

    val entries by DownloadRegistry.entries.collectAsStateWithLifecycle()
    val groups = remember(entries) { xmbDlGroups(entries) }
    val cards = remember(groups) { xmbDlCards(groups) }
    val keys = remember(cards) { cards.map { it.key } }
    val indexOf = remember(keys) { keys.withIndex().associate { (i, k) -> k to i } }
    // The waiting downloads in the order they start: a card's place in it is its "#n", and anything after the first can start next.
    val queueKeys = remember(groups) { groups.firstOrNull { it.section == XmbDlSection.QUEUED }?.entries?.map { it.key } ?: emptyList() }

    // The cursor: the card it is on (by key, so it stays on it as the list changes under it) and, when a card's buttons hold it, which one.
    var selKey by remember { mutableStateOf<String?>(null) }
    var selIdx by remember { mutableIntStateOf(0) }
    var inner by remember { mutableIntStateOf(-1) }
    // The column the cursor last chose, which a move up or down keeps (through a one-card line, say).
    var wantCol by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf<XmbDlAction?>(null) }
    val cursor = xmbDlCursor(keys, selKey, selIdx)
    // The buttons of the card under the cursor: when a card changes state under it (downloading, then failed) they can be fewer than the one
    // the cursor was on, or lose the one waiting for its second press.
    val selButtons = cards.getOrNull(cursor)?.let { xmbDlActions(it, queueKeys.indexOf(it.key) > 0) } ?: emptyList()
    LaunchedEffect(selButtons) {
        inner = xmbDlInnerClamp(inner, selButtons.size)
        if (armed != null && armed !in selButtons) armed = null
    }

    // The list changed: keep the cursor on its card, or the nearest one when that has gone (a game uninstalled, a failure dismissed).
    LaunchedEffect(keys) {
        val before = selKey
        val at = xmbDlCursor(keys, selKey, selIdx)
        selIdx = at.coerceAtLeast(0)
        selKey = keys.getOrNull(at)
        if (selKey != before) { inner = -1; armed = null }
    }
    // The cursor leaving the page (up to the pill bar) leaves the card's buttons too.
    LaunchedEffect(focus.contentFocused) {
        if (!focus.contentFocused) { inner = -1; armed = null }
    }
    // A button that asked for a second press gives up waiting.
    LaunchedEffect(armed) {
        if (armed != null) {
            delay(XMB_ARMED_MS)
            armed = null
        }
    }

    fun select(i: Int) {
        val at = i.coerceIn(0, keys.lastIndex)
        if (keys.getOrNull(at) != selKey) { inner = -1; armed = null }
        selIdx = at
        selKey = keys.getOrNull(at)
    }

    fun perform(e: DownloadEntry, a: XmbDlAction) {
        if (xmbDlNeedsConfirm(e, a) && armed != a) {
            armed = a
            return
        }
        armed = null
        val acts = actions ?: return
        when (a) {
            XmbDlAction.PAUSE, XmbDlAction.RESUME -> acts.pauseResume(e)
            XmbDlAction.CANCEL -> acts.cancel(e)
            XmbDlAction.DETAILS -> acts.openDetail(e)
            XmbDlAction.START_NEXT -> acts.moveToTop(e)
            XmbDlAction.LAUNCH -> acts.launch(e)
            XmbDlAction.UNINSTALL -> acts.uninstall(e)
            XmbDlAction.DISMISS -> acts.dismiss(e)
        }
    }

    val ss = rememberScreenScale()
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val pad = focus.contentPadding
    val listState = rememberLazyListState()

    // The page's box runs down to the screen's edge so the list can scroll under the hints: only the top and the sides are padded here,
    // the room for the hints is the last item. The width it is left decides the columns (the keys need them, so they are registered here).
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = minOf(maxWidth - pad.calculateStartPadding(dir) - pad.calculateEndPadding(dir), XMB_TWO_COL_MAX_WIDTH_DP.dp)
        val columns = xmbTwoColColumns(avail.value)
        val lines = remember(groups, columns) { xmbDlLines(groups, columns) }

        // A tap puts the cursor on the card, and its column is the one a move up or down keeps.
        fun pick(i: Int) {
            select(i)
            wantCol = xmbTwoColCol(lines, i).coerceAtLeast(0)
        }

        XmbTabsPageKeys(focus) { e ->
            val n = cards.size
            val first = e.nativeKeyEvent.repeatCount == 0
            val at = xmbDlCursor(keys, selKey, selIdx)
            val entry = cards.getOrNull(at)
            val canMoveUp = entry != null && queueKeys.indexOf(entry.key) > 0
            val buttons = if (entry == null) emptyList<XmbDlAction>() else xmbDlActions(entry, canMoveUp)
            when (e.key) {
                // Along the lines of cards (headings are skipped), on the column the cursor last chose.
                Key.DirectionUp, Key.DirectionDown -> {
                    val up = e.key == Key.DirectionUp
                    if (n == 0) {
                        if (up) focus.toBar()
                    } else {
                        val to = xmbTwoColVertical(lines, at, down = !up, wantCol = wantCol)
                        if (to == XMB_TWO_COL_TO_BAR) focus.toBar() else select(to)
                    }
                    true
                }
                // Between the columns; once A has stepped into a card's buttons, between those instead.
                Key.DirectionLeft, Key.DirectionRight -> {
                    val d = if (e.key == Key.DirectionRight) 1 else -1
                    if (inner >= 0) {
                        val next = (inner + d).coerceIn(0, (buttons.size - 1).coerceAtLeast(0))
                        if (next != inner) {
                            inner = next
                            armed = null
                        }
                    } else if (n > 0) {
                        val to = xmbTwoColSide(lines, at, d)
                        if (to != at) pick(to)
                    }
                    true
                }
                // A held key must not press a button twice.
                Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    if (first && entry != null) {
                        if (inner < 0) {
                            if (buttons.isNotEmpty()) inner = 0
                        } else {
                            buttons.getOrNull(inner)?.let { perform(entry, it) }
                        }
                    }
                    true
                }
                Key.ButtonX -> {
                    if (first && entry != null) xmbDlQuickAction(entry, canMoveUp)?.let { perform(entry, it) }
                    true
                }
                // Out of the buttons first; from the card B is the host's (it closes the page).
                Key.ButtonB, Key.Escape -> if (inner >= 0) {
                    inner = -1
                    armed = null
                    true
                } else {
                    false
                }
                else -> false
            }
        }

        // The card under the cursor scrolls into view, with its section's heading when it is on the section's first line.
        val slot = xmbDlSlot(groups, cursor, columns)
        val topPx = with(density) { ss.dp(6f).roundToPx() }
        val bottomPx = with(density) { (pad.calculateBottomPadding() + ss.dp(8f)).roundToPx() }
        LaunchedEffect(slot?.item, slot?.first, focus.contentFocused) {
            val s = slot
            if (focus.contentFocused && s != null) listState.xmbDlReveal(if (s.first) s.header else s.item, s.item, topPx, bottomPx)
        }

        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ss.dp(4f))) {
                    Text(
                        stringResource(R.string.xmb_dl_empty_title),
                        color = XMB_DL_INK, fontSize = ss.sp(18f), fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.xmb_dl_empty_body),
                        color = XMB_DL_DIM, fontSize = ss.sp(13f), textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = pad.calculateTopPadding(), start = pad.calculateStartPadding(dir), end = pad.calculateEndPadding(dir)),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxHeight().widthIn(max = XMB_TWO_COL_MAX_WIDTH_DP.dp).fillMaxWidth()) {
                    groups.forEach { g ->
                        // A heading spans the full width; the cards under it sit [columns] to a line, equally wide.
                        item(key = "h_${g.section.name}") { XmbDlHeading(g.section, g.entries.size, ss) }
                        items(g.entries.chunked(columns), key = { line -> "r_${line.first().key}" }) { line ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = ss.dp(XMB_TWO_COL_GAP_DP / 2)).height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(ss.dp(XMB_TWO_COL_GAP_DP)),
                            ) {
                                line.forEach { e ->
                                    val i = indexOf[e.key] ?: -1
                                    val here = focus.contentFocused && i == cursor
                                    val position = queueKeys.indexOf(e.key) + 1
                                    XmbDlCard(
                                        entry = e,
                                        position = position,
                                        canMoveUp = position > 1,
                                        focused = here,
                                        inner = if (here) inner else -1,
                                        // Also shown to a finger (the bar holds the cursor then).
                                        armed = if (e.key == selKey) armed else null,
                                        ss = ss,
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        onOpen = { pick(i); actions?.openDetail(e) },
                                        onAction = { a -> pick(i); perform(e, a) },
                                    )
                                }
                                // A short last line keeps the width of a card: the one under it is not stretched.
                                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    item(key = "tail") { Spacer(Modifier.height(pad.calculateBottomPadding() + ss.dp(8f))) }
                }
            }
        }
        // The flows of launching, uninstalling and the Epic cancel raise their dialogs and the result bar here.
        if (actions != null) actions.Dialogs()
    }
}

/**
 * Scrolls the list so the items [from]..[to] (a card, and its section's heading when it is the first) are in view with [topPx] / [bottomPx]
 * to spare: the least it takes when they are on screen already, else it takes [from] to the top.
 */
private suspend fun LazyListState.xmbDlReveal(from: Int, to: Int, topPx: Int, bottomPx: Int) {
    val shown = layoutInfo.visibleItemsInfo
    val a = shown.firstOrNull { it.index == from }
    val b = shown.firstOrNull { it.index == to }
    if (a == null || b == null) {
        animateScrollToItem(from, -topPx)
        return
    }
    val under = topPx - a.offset
    val over = b.offset + b.size - (layoutInfo.viewportEndOffset - bottomPx)
    if (under > 0) animateScrollBy(-under.toFloat()) else if (over > 0) animateScrollBy(over.toFloat())
}

private fun XmbDlSection.labelRes(): Int = when (this) {
    XmbDlSection.ACTIVE -> R.string.xmb_dl_section_active
    XmbDlSection.QUEUED -> R.string.xmb_dl_section_queued
    XmbDlSection.STOPPED -> R.string.xmb_dl_section_stopped
    XmbDlSection.INSTALLED -> R.string.xmb_dl_section_installed
}

private fun XmbDlAction.labelRes(armed: Boolean): Int = when (this) {
    XmbDlAction.PAUSE -> R.string.xmb_dl_act_pause
    XmbDlAction.RESUME -> R.string.xmb_dl_act_resume
    XmbDlAction.CANCEL -> if (armed) R.string.xmb_dl_act_cancel_sure else R.string.xmb_dl_act_cancel
    XmbDlAction.DETAILS -> R.string.xmb_dl_act_details
    XmbDlAction.START_NEXT -> R.string.xmb_dl_act_start_next
    XmbDlAction.LAUNCH -> R.string.xmb_dl_act_launch
    XmbDlAction.UNINSTALL -> if (armed) R.string.xmb_dl_act_uninstall_sure else R.string.xmb_dl_act_uninstall
    XmbDlAction.DISMISS -> R.string.xmb_dl_act_dismiss
}

private fun Store.mark(): StoreMark = when (this) {
    Store.STEAM -> StoreMark.STEAM
    Store.EPIC -> StoreMark.EPIC
    Store.GOG -> StoreMark.GOG
    Store.AMAZON -> StoreMark.AMAZON
    Store.XBOX -> StoreMark.XBOX
}

// A section's heading: its name in small bold capitals (as the pill bar's tabs) and how many cards are under it.
@Composable
private fun XmbDlHeading(section: XmbDlSection, count: Int, ss: ScreenScale) {
    Row(
        Modifier.fillMaxWidth().padding(start = ss.dp(2f), top = ss.dp(12f), bottom = ss.dp(4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ss.dp(8f)),
    ) {
        Text(
            stringResource(section.labelRes()).uppercase(),
            color = XMB_DL_DIM, fontSize = ss.sp(11f), letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        )
        Text(count.toString(), color = XMB_DL_DIM.copy(alpha = 0.6f), fontSize = ss.sp(11f), fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * One download or installed game: its cover, name and store mark, what state it is in (a progress bar with sizes and speed while it
 * downloads) and its buttons. [position] is its place in the queue (0 when it is not queued), [canMoveUp] that it can be started next.
 * [focused] ringed card, [inner] the button holding the cursor (-1: the card itself), [armed] the button waiting for its second press.
 */
@Composable
private fun XmbDlCard(
    entry: DownloadEntry,
    position: Int,
    canMoveUp: Boolean,
    focused: Boolean,
    inner: Int,
    armed: XmbDlAction?,
    ss: ScreenScale,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onAction: (XmbDlAction) -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val src = remember { MutableInteractionSource() }
    val artH = ss.dp(92f)
    val buttons = xmbDlActions(entry, canMoveUp)
    val quick = xmbDlQuickAction(entry, canMoveUp)
    val outline = when {
        !focused -> Color.White.copy(alpha = 0.10f)
        inner >= 0 -> XMB_DL_OUTLINE_INNER
        else -> XMB_DL_OUTLINE
    }
    Row(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = if (focused) 0.16f else 0.08f))
            .border(if (focused) 1.5.dp else 1.dp, outline, shape)
            .xmbClick(src, onOpen)
            .padding(ss.dp(10f)),
        horizontalArrangement = Arrangement.spacedBy(ss.dp(12f)),
    ) {
        DownloadCoverArt(entry, Modifier.size(width = artH * 0.75f, height = artH).clip(RoundedCornerShape(4.dp)))
        Column(Modifier.weight(1f).heightIn(min = artH), verticalArrangement = Arrangement.spacedBy(ss.dp(3f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ss.dp(8f))) {
                Text(
                    entry.name.ifEmpty { "${StoreStyle.label(entry.store)} ${entry.id}" },
                    color = XMB_DL_INK, fontSize = ss.sp(15f), fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                XmbStoreMark(entry.store.mark(), size = ss.dp(18f))
            }
            when (entry.state) {
                DownloadState.DOWNLOADING, DownloadState.PAUSED -> {
                    val p = xmbDlProgress(entry)
                    val paused = entry.state == DownloadState.PAUSED
                    Text(
                        stringResource(if (paused) R.string.xmb_dl_status_paused else R.string.xmb_dl_status_downloading, p.percent),
                        color = if (paused) XMB_DL_AMBER else XMB_DL_INK, fontSize = ss.sp(12f), maxLines = 1,
                    )
                    XmbDlBar(p.fraction, paused, ss)
                    val sizes = xmbDlSizeText(entry)
                    val speed = if (paused) "" else xmbDlSpeedText(entry)
                    if (sizes.isNotEmpty() || speed.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ss.dp(8f))) {
                            Text(sizes, color = XMB_DL_DIM, fontSize = ss.sp(11f), maxLines = 1, modifier = Modifier.weight(1f))
                            Text(speed, color = XMB_DL_DIM, fontSize = ss.sp(11f), maxLines = 1)
                        }
                    }
                }
                DownloadState.QUEUED -> Text(
                    stringResource(R.string.xmb_dl_status_queued, position.coerceAtLeast(1)),
                    color = XMB_DL_DIM, fontSize = ss.sp(12f), maxLines = 1,
                )
                DownloadState.INSTALLED -> Text(
                    stringResource(if (entry.updateAvailable) R.string.xmb_dl_status_update else R.string.xmb_dl_status_installed),
                    color = if (entry.updateAvailable) XMB_DL_AMBER else XMB_DL_GREEN, fontSize = ss.sp(12f), maxLines = 1,
                )
                DownloadState.FAILED -> Text(
                    entry.error?.let { stringResource(R.string.xmb_dl_status_failed_why, it) } ?: stringResource(R.string.xmb_dl_status_failed),
                    color = XMB_DL_RED, fontSize = ss.sp(12f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                DownloadState.CANCELLED -> Text(
                    stringResource(R.string.xmb_dl_status_cancelled),
                    color = XMB_DL_DIM, fontSize = ss.sp(12f), maxLines = 1,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ss.dp(6f)),
            ) {
                buttons.forEachIndexed { i, a ->
                    XmbDlButton(
                        modifier = Modifier.weight(1f, fill = false),
                        action = a,
                        armed = armed == a,
                        focused = focused && inner == i,
                        filled = a == quick,
                        showX = focused && inner < 0 && a == quick,
                        ss = ss,
                        onClick = { onAction(a) },
                    )
                }
            }
        }
    }
}

// The progress bar: one fill over a faint track, green while it downloads and amber while it is paused.
@Composable
private fun XmbDlBar(fraction: Float, paused: Boolean, ss: ScreenScale) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(maxOf(ss.dp(5f), 4.dp))
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(if (paused) XMB_DL_AMBER else XMB_DL_GREEN),
        )
    }
}

// A card's button: a pill with the thin light outline while it holds the cursor, at least 48 dp to touch. The card's main button
// (the one X presses) is a touch lighter, and wears the X while the card holds the cursor; [armed] asks for the second press.
@Composable
private fun XmbDlButton(
    modifier: Modifier,
    action: XmbDlAction,
    armed: Boolean,
    focused: Boolean,
    filled: Boolean,
    showX: Boolean,
    ss: ScreenScale,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    val danger = armed || action == XmbDlAction.CANCEL || action == XmbDlAction.UNINSTALL
    Box(
        modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).xmbClick(src, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(Color.White.copy(alpha = if (armed) 0.24f else if (filled) 0.20f else 0.10f))
                .border(1.5.dp, if (focused) XMB_DL_OUTLINE else Color.Transparent, shape)
                .padding(horizontal = ss.dp(10f), vertical = ss.dp(7f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ss.dp(6f)),
        ) {
            Text(
                stringResource(action.labelRes(armed)),
                color = if (danger) XMB_DL_RED else Color.White,
                fontSize = ss.sp(12f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showX) DeckGlyph("X", GlyphKind.X)
        }
    }
}
