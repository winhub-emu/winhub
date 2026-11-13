package com.winhub.hernandez.ui.deck

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.winhub.hernandez.R
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.screens.XMB_STORES_TO_BAR
import com.winhub.hernandez.ui.screens.xmbClick
import com.winhub.hernandez.ui.screens.xmbStoreRows
import com.winhub.hernandez.ui.screens.xmbStoresGrid
import com.winhub.hernandez.ui.screens.xmbStoresMove
import com.winhub.hernandez.ui.simple.storeLogo

/** The card under the cursor is drawn this many times its size (the Steam Home's cards lift the same way, a touch less than its covers). */
private const val XMB_STORES_FOCUS_SCALE = 1.04f

/**
 * The Stores tab of the XMB Home's tabs page: the five stores as identical square cards, three on the top row and two centred beneath,
 * as large as fit the page (an even gap between them): the store's brand colours with its logo large in the middle, its name and under
 * it whether it is signed in (with how many of its games the library has) or "Sign in". The D-pad moves between the cards (Up from the
 * top row goes to the pill bar) and A, like a tap, opens the store: the store's own screen, its sign-in while signed out. Start (the
 * host's) adds a game. The cursor shows only while the content, not the pill bar, holds it ([XmbTabsPageFocus.contentFocused]).
 */
@Composable
internal fun XmbStoresPage(
    focus: XmbTabsPageFocus,
    signedIn: Map<DeckStore, Boolean>,
    games: List<Shortcut>,
    onStore: (DeckStore) -> Unit,
) {
    val resources = LocalContext.current.resources
    val stores = remember { DeckStore.entries.toList() }
    val rows = remember(stores) { xmbStoreRows(stores.size) }
    // Under each card's name: how many of its games the library has while signed in, else the way in.
    val status = remember(stores, signedIn, games) {
        stores.map { store ->
            if (signedIn[store] == true) {
                val count = games.count(store.owns)
                if (count == 0) resources.getString(R.string.xmb_store_no_games) else resources.getQuantityString(R.plurals.xmb_store_games, count, count)
            } else {
                resources.getString(R.string.xmb_store_card_signed_out)
            }
        }
    }
    var sel by remember { mutableIntStateOf(0) }

    XmbTabsPageKeys(focus) { e ->
        when (e.key) {
            Key.DirectionLeft -> { sel = xmbStoresMove(rows, sel, -1, 0); true }
            Key.DirectionRight -> { sel = xmbStoresMove(rows, sel, 1, 0); true }
            Key.DirectionDown -> { sel = xmbStoresMove(rows, sel, 0, 1); true }
            Key.DirectionUp -> {
                val to = xmbStoresMove(rows, sel, 0, -1)
                if (to == XMB_STORES_TO_BAR) focus.toBar() else sel = to
                true
            }
            // A held key must not open the store twice.
            Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                if (e.nativeKeyEvent.repeatCount == 0) onStore(stores[sel.coerceIn(0, stores.lastIndex)])
                true
            }
            else -> false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(focus.contentPadding)) {
        // Captured here: the lambdas below have their own receivers.
        val boxW = maxWidth.value
        val boxH = maxHeight.value
        val grid = remember(boxW, boxH, stores.size) { xmbStoresGrid(stores.size, boxW, boxH) }
        stores.forEachIndexed { i, store ->
            key(store) {
                XmbStoreCard(
                    store = store,
                    status = status[i],
                    side = grid.side,
                    focused = focus.contentFocused && i == sel,
                    modifier = Modifier.offset(grid.x(i).dp, grid.y(i).dp).size(grid.side.dp),
                    onClick = { sel = i; onStore(store) },
                )
            }
        }
    }
}

// One store's card: its brand colours as a soft vertical gradient (a thin faint edge keeps the dark ones apart from the page), its logo
// large and centred and its name and status under it, all sized from the card's side. The one under the cursor is ringed and a touch larger.
@Composable
private fun XmbStoreCard(
    store: DeckStore,
    status: String,
    side: Float,
    focused: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val (logoRes, colors, whiteLogo) = storeLogo(store)
    val shape = RoundedCornerShape(4.dp)
    val src = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) XMB_STORES_FOCUS_SCALE else 1f, tween(140), label = "xmbStoreCard")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) XMB_FOCUS_OUTLINE else Color.White.copy(alpha = 0.10f), shape)
            .xmbClick(src, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
            Box(Modifier.size((side * 0.42f).dp), contentAlignment = Alignment.Center) {
                rememberXmbLogo(logoRes)?.let { bmp ->
                    Image(
                        bmp, contentDescription = store.label, contentScale = ContentScale.Fit,
                        colorFilter = if (whiteLogo) ColorFilter.tint(Color.White) else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height((side * 0.06f).dp))
            Text(
                store.label, color = Color.White, fontSize = (side * 0.115f).coerceIn(11f, 20f).sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                status, color = Color.White.copy(alpha = 0.72f), fontSize = (side * 0.085f).coerceIn(9f, 14f).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
        }
    }
}
