package com.winhub.hernandez.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal class DeckConsoleItem(
    val label: String,
    val icon: ImageVector,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

private val consoleRowH = 52.dp
private val consoleRowGap = 6.dp

/**
 * Classic Home's game menu, console style: a full-screen dim with the game title top centre and the
 * options as a centred list. D-pad Up/Down move, A selects, B (or Y) closes; touch taps a row, a tap
 * outside closes. [onDismiss] always runs before the chosen item's action.
 */
@Composable
internal fun DeckConsoleGameMenu(
    title: String,
    subtitle: String,
    items: List<DeckConsoleItem>,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "consoleMenuAppear")
    var sel by remember { mutableIntStateOf(0) }
    val soundGate = remember { KeyRepeatGate() }
    val root = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { root.requestFocus() } }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // Keep the chosen row centred in the list; the ends clamp so a short list never scrolls.
    LaunchedEffect(sel) {
        val rowPx = with(density) { consoleRowH.roundToPx() }
        val stride = rowPx + with(density) { consoleRowGap.roundToPx() }
        val viewport = listState.layoutInfo.viewportSize.height
        listState.animateScrollToItem(sel, -minOf((viewport - rowPx) / 2, sel * stride).coerceAtLeast(0))
    }

    fun choose(i: Int) {
        val item = items.getOrNull(i) ?: return
        onDismiss()
        item.onClick()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear }
                .background(Color.Black.copy(alpha = 0.6f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .focusRequester(root)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    deckPageKeySound(e, soundGate)
                    if ((e.key == Key.ButtonB || e.key == Key.ButtonY || e.key == Key.Back || e.key == Key.Escape) && e.nativeKeyEvent.repeatCount == 0) {
                        DeckSound.play(DeckSound.BACK)
                    }
                    val last = items.lastIndex
                    when (e.key) {
                        Key.DirectionUp -> { if (last >= 0) sel = if (sel > 0) sel - 1 else last; true }
                        Key.DirectionDown -> { if (last >= 0) sel = if (sel < last) sel + 1 else 0; true }
                        Key.DirectionLeft, Key.DirectionRight -> true
                        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            if (e.nativeKeyEvent.repeatCount == 0) choose(sel)
                            true
                        }
                        Key.ButtonB, Key.ButtonY, Key.Back, Key.Escape -> {
                            if (e.nativeKeyEvent.repeatCount == 0) onDismiss()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            // A centered rounded card instead of a full-screen takeover; a tap on the dim area closes it.
            val cardShape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 12.dp)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(0.8f)
                    .clip(cardShape)
                    .background(Color(0xFF12151B))
                    .border(1.dp, DeckPalette.line2, cardShape)
                    // Swallow taps on the card so only the dim area dismisses.
                    .pointerInput(Unit) { detectTapGestures { } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp, start = 20.dp, end = 20.dp),
                )
                Text(
                    subtitle.uppercase(),
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, start = 20.dp, end = 20.dp),
                )
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(consoleRowGap),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    itemsIndexed(items) { i, item ->
                        DeckConsoleRow(item, focused = i == sel, onTap = { sel = i; choose(i) })
                    }
                }
                Text(
                    "↑↓  Navigate      A  Select      B  Back",
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DeckConsoleRow(item: DeckConsoleItem, focused: Boolean, onTap: () -> Unit) {
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "consoleRowScale")
    val accent = if (item.danger) DeckPalette.error else DeckPalette.primary
    val content = when {
        focused && item.danger -> Color.Black
        focused -> DeckPalette.onPrimary
        item.danger -> DeckPalette.error
        else -> Color.White
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(consoleRowH)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (focused) Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.7f)))
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f))),
            )
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White.copy(alpha = 0.85f) else DeckPalette.line, shape)
            .pointerInput(onTap) { detectTapGestures { onTap() } }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            item.label,
            color = content,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
