package com.winhub.hernandez.ui.deck

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

// Two-level focus for the XMB interface: a card or row is ONE stop of the D-pad (the highlight glides from item to item), and
// the buttons inside it are reached only after A. Modelled on XmbEditState (DeckXmbKit.kt): an outer focus stop, inner
// focusables that can take focus only while `entered` is set.
//
//   D-pad          moves between items (inner buttons are skipped)
//   A              on an item without onActivate: enters it, focus goes to the first inner button
//                  on an item with onActivate: runs it (a toggle row)
//   Left / Right   between the inner buttons (normal 2D focus search)
//   B / Back       on an inner button: back to the item (consumed; the shell does not also go back)
//   Up / Down      leave the item, which resets it
//
// While focus is inside, the highlight outline glides to the inner button and the item keeps a dim static outline. That needs
// the inner buttons to register with the highlight themselves (deckFocusRing, steamFocusRing or xmbHighlight on the button);
// a button that does not leaves the outline on the item.
//
// Usage, e.g. a container card with two buttons (all of it is a plain pass-through outside the XMB interface: the buttons
// stay normally focusable and nothing here changes focus):
//
//   val group = rememberXmbFocusGroup()
//   XmbGroupItem(group, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
//       Column {
//           Text(name)                                              // turns accent-coloured while the card is hot
//           Row {
//               Button(onClick = edit, modifier = Modifier.deckFocusRing(btnShape).xmbInner(group, first = true)) { Text("Edit") }
//               Button(onClick = delete, modifier = Modifier.deckFocusRing(btnShape).xmbInner(group)) { Text("Delete") }
//           }
//       }
//   }
//
// A settings row where A toggles directly (no inner buttons):
//
//   XmbGroupItem(rememberXmbFocusGroup(), shape, onActivate = { enabled = !enabled }) { Row { Text(title); Switch(...) } }
//
// Both A and B are consumed on key-down AND key-up: the shell acts on B key-up (DeckShell) and Home on key-down, so an
// unconsumed half would also fire Back / a second action.

/** What a key means to a group. */
internal enum class XmbGroupKey { ACTIVATE, BACK }

/** What the group does with it. PASS: not ours, let it through. */
internal enum class XmbGroupAct { PASS, ENTER, RUN, EXIT }

/**
 * The group's key decision, as a pure function. [onOuter]: the item itself holds focus (not one of its inner buttons).
 * [entered]: the inner buttons are focusable. [hasAction]: the item has an onActivate.
 *  - A on the item: run its action, or enter. A on an inner button: not ours (that button acts).
 *  - B on an inner button of an entered item: exit. B anywhere else: not ours (the shell goes back).
 */
internal fun xmbGroupKeyAct(key: XmbGroupKey, entered: Boolean, onOuter: Boolean, hasAction: Boolean): XmbGroupAct = when (key) {
    XmbGroupKey.ACTIVATE -> when {
        !onOuter -> XmbGroupAct.PASS
        hasAction -> XmbGroupAct.RUN
        else -> XmbGroupAct.ENTER
    }
    XmbGroupKey.BACK -> if (entered && !onOuter) XmbGroupAct.EXIT else XmbGroupAct.PASS
}

internal fun xmbGroupKeyOf(key: Key): XmbGroupKey? = when (key) {
    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> XmbGroupKey.ACTIVATE
    Key.ButtonB, Key.Back, Key.Escape -> XmbGroupKey.BACK
    else -> null
}

/** The state of one two-level item. Make it with [rememberXmbFocusGroup]. */
@Stable
internal class XmbFocusGroup {
    /** The inner buttons can take focus ([xmbInner] gates them on it). */
    var entered by mutableStateOf(false)

    /** Counts the requests to move focus inside; the item reacts to each one (a retry works even when [entered] already is). */
    var enterTick by mutableIntStateOf(0)

    /** The item's own focus stop. */
    val item = FocusRequester()

    /** Put it on the first inner button ([xmbInner] with `first = true`): focus goes there on A. */
    val first = FocusRequester()

    /** Focus is on the item itself, not on something inside it. */
    internal var onOuter = false

    // Keys whose key-down we acted on: their key-up is eaten too, even though focus has moved by the time it arrives.
    internal val pendingUp = HashSet<Key>()

    fun enter() {
        entered = true
        enterTick += 1
    }

    /** Back to the item: the inner buttons stop being focusable and the item takes focus. */
    fun exit() {
        entered = false
        runCatching { item.requestFocus() }
    }

    internal fun onFocus(isFocused: Boolean, hasFocus: Boolean) {
        onOuter = isFocused
        if (!hasFocus) {
            entered = false
            pendingUp.clear()
        }
    }
}

@Composable
internal fun rememberXmbFocusGroup(): XmbFocusGroup = remember { XmbFocusGroup() }

/** The group of the enclosing [XmbGroupItem], or null (outside the XMB interface, or outside any item). */
internal val LocalXmbGroup = compositionLocalOf<XmbFocusGroup?> { null }

/**
 * Marks an inner button (or a container of them) of a group: it can take focus only after the item was entered. Give the
 * first one `first = true` so A lands on it. [group] defaults to the enclosing item's, which exists only in the XMB
 * interface: elsewhere (or with no group) this is the modifier unchanged, so the button stays normally focusable.
 */
@Composable
internal fun Modifier.xmbInner(group: XmbFocusGroup? = LocalXmbGroup.current, first: Boolean = false): Modifier {
    if (group == null || !rememberXmbHome()) return this
    val requested = if (first) this.focusRequester(group.first) else this
    return requested.focusProperties { canFocus = group.entered }
}

private fun handleGroupKey(group: XmbFocusGroup, action: (() -> Unit)?, k: KeyEvent): Boolean {
    if (k.type == KeyEventType.KeyUp) return group.pendingUp.remove(k.key)
    if (k.type != KeyEventType.KeyDown) return false
    val kind = xmbGroupKeyOf(k.key) ?: return false
    val act = xmbGroupKeyAct(kind, group.entered, group.onOuter, action != null)
    if (act == XmbGroupAct.PASS) return false
    // A held key repeats its key-down: the first one did the work.
    if (k.nativeKeyEvent.repeatCount > 0) return true
    group.pendingUp.add(k.key)
    when (act) {
        XmbGroupAct.ENTER -> group.enter()
        XmbGroupAct.RUN -> action?.invoke()
        XmbGroupAct.EXIT -> group.exit()
        XmbGroupAct.PASS -> Unit
    }
    return true
}

/**
 * One two-level stop (see the notes above). In the XMB interface the whole [content] is a single D-pad stop that the
 * highlight lands on ([shape] is its outline), with the item's text and icons turning accent-coloured while it is hot; the
 * inner buttons carry [xmbInner]. With [onActivate], A runs it instead of entering. Touch is the content's own business:
 * [onActivate] is only the controller's A, the content keeps its clickable / switch for taps.
 *
 * Anywhere else it is a Box around [content] and nothing more, so a call site needs no conditionals.
 */
@Composable
internal fun XmbGroupItem(
    group: XmbFocusGroup,
    shape: Shape,
    modifier: Modifier = Modifier,
    onActivate: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!rememberXmbHome()) {
        Box(modifier, content = content)
        return
    }
    val item = rememberXmbHighlightItem()
    val action by rememberUpdatedState(onActivate)
    val tick = group.enterTick
    LaunchedEffect(group, tick) {
        if (tick > 0) {
            // The same one-frame wait as DeckEntryFocus: the inner buttons' canFocus has to be applied before they are asked.
            withFrameNanos { }
            if (runCatching { group.first.requestFocus() }.isFailure) group.entered = false
        }
    }
    CompositionLocalProvider(LocalXmbGroup provides group, LocalXmbHighlightItem provides item) {
        Box(
            modifier
                .onFocusChanged { group.onFocus(it.isFocused, it.hasFocus) }
                .xmbHighlight(shape, item, wholeSubtree = true, dimWhenInside = true)
                .onPreviewKeyEvent { k -> handleGroupKey(group, action, k) }
                .focusRequester(group.item)
                .focusable(),
        ) {
            XmbInk(item) { content() }
        }
    }
}
