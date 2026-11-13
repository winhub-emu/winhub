package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.ui.deck.XmbFocusGroup
import com.winhub.hernandez.ui.deck.XmbGroupItem
import com.winhub.hernandez.ui.deck.XmbHighlightItem
import com.winhub.hernandez.ui.deck.rememberXmbHighlightItem
import com.winhub.hernandez.ui.deck.rememberXmbHome
import com.winhub.hernandez.ui.deck.xmbGlassCard
import com.winhub.hernandez.ui.deck.xmbHighlight
import com.winhub.hernandez.ui.deck.xmbInner
import com.winhub.hernandez.ui.deck.xmbOnGlass
import com.winhub.hernandez.ui.theme.OnSurface
import com.winhub.hernandez.ui.theme.OnSurfaceVariant

// Shared pieces for the Classic screens (Containers, Components, Controls, Files, Logs, Saves, Wrappers, Adreno tools) when
// the XMB interface hosts them: glass cards that pop, the two-level focus of a card with buttons in it, the flat focus of a
// row, and the accent ink of the text. Every one of them is a plain pass-through outside the XMB interface (a stock Card, the
// screen's own colours, no extra modifier), so Classic and plain Deck compose exactly as before.
//
//   card with buttons:   XmbGroupCard(group, ...) { ... Button(modifier = Modifier.xmbBtn(shape, first = true)) ... }
//   card/row to click:   val item = rememberXmbHighlightItem()
//                        Row(Modifier.xmbStop(shape, item).clickable(onClick)) { XmbInk(item) { ... } }
//   list of cards:       LazyColumn(contentPadding = xmbListPadding(...))   // 12 dp+ between cards, room for the shadow

/** A card's shadow reaches 6 dp above and 14 dp below it (xmbShadowLayers): a list needs at least this at its ends. */
internal val XMB_LIST_ROOM_TOP = 8.dp
internal val XMB_LIST_ROOM_BOTTOM = 16.dp

/** At least [need] of room: a list that already has more keeps it. */
internal fun xmbRoomAtLeast(have: Dp, need: Dp): Dp = have.coerceAtLeast(need)

/**
 * A list's content padding: [base] as it was, except on the XMB glass, where the top and the bottom edge get room for the cards'
 * shadow (the list's viewport would clip it). Cards in the list keep >= 12 dp between them through their own outer padding.
 */
@Composable
internal fun xmbListPadding(base: PaddingValues = PaddingValues(0.dp)): PaddingValues {
    if (!xmbOnGlass()) return base
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateStartPadding(layoutDirection),
        top = xmbRoomAtLeast(base.calculateTopPadding(), XMB_LIST_ROOM_TOP),
        end = base.calculateEndPadding(layoutDirection),
        bottom = xmbRoomAtLeast(base.calculateBottomPadding(), XMB_LIST_ROOM_BOTTOM),
    )
}

/** The screen's own text colour, or the theme's `onSurface` in the XMB interface (so the accent ink of [XmbInk] reaches it). */
@Composable
internal fun xmbOnSurface(): Color =
    if (rememberXmbHome()) MaterialTheme.colorScheme.onSurface else OnSurface

/** [xmbOnSurface] for the secondary text and icons. */
@Composable
internal fun xmbOnSurfaceVariant(): Color =
    if (rememberXmbHome()) MaterialTheme.colorScheme.onSurfaceVariant else OnSurfaceVariant

/**
 * A list card: on the XMB glass a glass panel that pops (shadow, top hairline, lighter fill, [shape]'s corners); anywhere else
 * the stock Material card in [classicColor] with [classicBorder], as the screens had it.
 */
@Composable
internal fun XmbListCard(
    modifier: Modifier = Modifier,
    shape: Shape,
    classicColor: Color,
    classicBorder: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (xmbOnGlass()) {
        // [accent]: the card is the selected one, it keeps a faint accent fill and a thin accent edge.
        val primary = MaterialTheme.colorScheme.primary
        val selected = if (accent) Modifier.background(primary.copy(alpha = 0.12f)).border(1.dp, primary, shape) else Modifier
        Column(modifier.xmbGlassCard(shape).then(selected).padding(contentPadding), content = content)
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = classicColor),
            border = classicBorder,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * A column-built card with buttons in it (the Components and Controls screens draw theirs as a Column with a border and a
 * background rather than as a Card). XMB interface: the same two-level stop as [XmbGroupCard], the card a glass panel, its
 * [contentPadding] inside; [onActivate] makes A run it directly (a card without buttons), [accent] marks the selected one.
 * Elsewhere it is `Column(classicModifier)`, exactly as before. [classicModifier] is the whole classic look (width, border,
 * background and padding).
 */
@Composable
internal fun XmbGroupColumn(
    group: XmbFocusGroup,
    shape: Shape,
    classicModifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onActivate: (() -> Unit)? = null,
    accent: Boolean = false,
    margin: Dp = 0.dp,
    vGap: Dp = XMB_CARD_GAP,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!rememberXmbHome()) {
        Column(classicModifier, content = content)
        return
    }
    XmbGroupItem(group, shape, Modifier.fillMaxWidth().padding(horizontal = margin, vertical = vGap), onActivate) {
        XmbListCard(
            Modifier.fillMaxWidth(), shape, MaterialTheme.colorScheme.surface, null, contentPadding, accent, content,
        )
    }
}

/**
 * [XmbGroupColumn] for a card or a list row laid out as a Row: [card] true is a card of its own (glass panel with the shadow),
 * false a row inside a panel (only the two-level stop, inset a little from the panel's edge). Elsewhere it is
 * `Row(classicModifier)`, exactly as before.
 */
@Composable
internal fun XmbGroupRow(
    group: XmbFocusGroup,
    shape: Shape,
    classicModifier: Modifier,
    contentPadding: PaddingValues,
    card: Boolean,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    rowModifier: Modifier = Modifier,
    margin: Dp = 0.dp,
    vGap: Dp = XMB_CARD_GAP,
    accent: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    if (!rememberXmbHome()) {
        Row(classicModifier, verticalAlignment = verticalAlignment, content = content)
        return
    }
    if (card) {
        XmbGroupItem(group, shape, Modifier.fillMaxWidth().padding(horizontal = margin, vertical = vGap)) {
            XmbListCard(Modifier.fillMaxWidth(), shape, MaterialTheme.colorScheme.surface, null, contentPadding, accent) {
                Row(Modifier.fillMaxWidth().then(rowModifier), verticalAlignment = verticalAlignment, content = content)
            }
        }
    } else {
        XmbGroupItem(group, shape, Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
            Row(Modifier.fillMaxWidth().then(rowModifier).padding(contentPadding), verticalAlignment = verticalAlignment, content = content)
        }
    }
}

/**
 * A column-built panel that is not a focus stop itself (its rows are): a glass panel on the XMB glass, `Column(classicModifier)`
 * elsewhere. See [XmbGroupColumn] for [classicModifier] and [contentPadding].
 */
@Composable
internal fun XmbPanelColumn(
    shape: Shape,
    classicModifier: Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!xmbOnGlass()) {
        Column(classicModifier, verticalArrangement = verticalArrangement, content = content)
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = XMB_CARD_GAP).xmbGlassCard(shape).padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
}

/** Each side of a card's outer margin in the XMB interface: with the 10 dp the lists put between cards that is 16 dp, over the 12 dp the shadow needs. */
internal val XMB_CARD_GAP = 3.dp

/**
 * A card with buttons in it. In the XMB interface the whole card is one D-pad stop (the highlight outlines all of it, its text
 * turns accent) and A enters it; the buttons inside carry [xmbBtn] and take focus only after that, B returns to the card. [modifier]
 * is the OUTER one (width and the margin round the card, 12 dp or more between cards leaves room for the shadow); the card
 * itself fills it. Everywhere else it is the stock Card with that modifier, exactly as before.
 */
@Composable
internal fun XmbGroupCard(
    group: XmbFocusGroup,
    modifier: Modifier,
    shape: Shape,
    classicColor: Color,
    classicBorder: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!rememberXmbHome()) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = classicColor),
            border = classicBorder,
            content = content,
        )
        return
    }
    XmbGroupItem(group, shape, modifier) {
        XmbListCard(Modifier.fillMaxWidth(), shape, classicColor, classicBorder, content = content)
    }
}

/**
 * An inner button of an [XmbGroupCard] (or of a row): the highlight outline glides to it, and it can take focus only once the
 * card was entered. [first] on the one A lands on. Nothing outside the XMB interface.
 */
@Composable
internal fun Modifier.xmbBtn(shape: Shape, first: Boolean = false, tint: Color = Color.Unspecified): Modifier {
    if (!rememberXmbHome()) return this
    return this.xmbHighlight(shape, tint = tint).xmbInner(first = first)
}

/**
 * The main action of a card as an inner stop: the card's own content area, clickable, and the one A lands on after entering
 * the card (its buttons follow). For a card whose click was on the whole card, which in the XMB interface is the group and
 * must not itself be a button. Nothing (no click either) outside the XMB interface: there the card keeps its own clickable.
 */
@Composable
internal fun Modifier.xmbMainAction(shape: Shape, onClick: () -> Unit): Modifier {
    if (!rememberXmbHome()) return this
    return this.xmbBtn(shape, first = true).clickable(onClick = onClick)
}

/**
 * A clickable area that is an inner stop of an enclosing card in the XMB interface (the highlight lands on it, [first] marks the
 * one A reaches first), and a plain clickable elsewhere.
 */
@Composable
internal fun Modifier.xmbClickOrBtn(shape: Shape, first: Boolean = false, onClick: () -> Unit): Modifier {
    if (!rememberXmbHome()) return this.clickable(onClick = onClick)
    return this.xmbBtn(shape, first).clickable(onClick = onClick)
}

/**
 * A flat focus stop: the highlight outline lands on this element when it holds focus. Put it BEFORE the clickable (the clickable
 * stays the one focus target, A clicks it) and hand the same [item] to `XmbInk(item)` around the element's text and icons.
 * Nothing outside the XMB interface.
 */
@Composable
internal fun Modifier.xmbStop(
    shape: Shape,
    item: XmbHighlightItem = rememberXmbHighlightItem(),
    wholeSubtree: Boolean = false,
    tint: Color = Color.Unspecified,
): Modifier {
    if (!rememberXmbHome()) return this
    return this.xmbHighlight(shape, item, tint = tint, wholeSubtree = wholeSubtree)
}

/**
 * Takes the element out of the D-pad order in the XMB interface (it stays tappable): for a clickable inside an [XmbGroupCard]
 * that must not be a second stop. Put it BEFORE the clickable. Nothing outside the XMB interface.
 */
@Composable
internal fun Modifier.xmbNoFocus(): Modifier {
    if (!rememberXmbHome()) return this
    return this.focusProperties { canFocus = false }
}

/** The keys a popup menu does not already treat as "back": the controller's B (Back and Escape dismiss a Compose popup themselves). */
internal fun xmbMenuBackKey(key: Key): Boolean = key == Key.ButtonB

/**
 * For a dropdown menu's modifier: the controller's B closes the menu. The menu is a window of its own, so the shell never sees
 * the key; without this B does nothing there. Key-down and key-up are both consumed so nothing underneath also acts on it.
 * Focus stays where it was on the button that opened the menu, so an item's group is left as it was. Nothing outside the XMB
 * interface.
 */
@Composable
internal fun Modifier.xmbMenuBack(onClose: () -> Unit): Modifier {
    if (!rememberXmbHome()) return this
    return this.onPreviewKeyEvent { k: KeyEvent ->
        if (!xmbMenuBackKey(k.key)) {
            false
        } else {
            if (k.type == KeyEventType.KeyDown && k.nativeKeyEvent.repeatCount == 0) onClose()
            true
        }
    }
}
