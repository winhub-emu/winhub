package com.winhub.hernandez.ui.deck

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.dp

/** The thin light outline of the card, tab or cover under the cursor on the XMB Home and its tabs page (the Steam Deck's focus ring). */
internal val XMB_FOCUS_OUTLINE = Color.White.copy(alpha = 0.92f)

/**
 * What a page of the XMB Home's tabs page (Stores, Downloads, Friends: XmbTabsPage.kt) gets from its host.
 *
 * The tabs page has two focus areas: the pill bar of tabs along the top, and the page's content under it. One focus target at the
 * host holds the keys (the pages take no Android focus of their own), so a page draws its own cursor and moves it from the keys the
 * host hands it:
 *
 *  - [contentFocused]: true while the content, not the pill bar, holds the cursor. Draw the highlight (the thin light outline) only
 *    then. Read it in composition; it changes when the cursor enters or leaves the page.
 *  - [onKey]: the page's key handler, called for every key-down (with repeats) while [contentFocused], before the host does anything
 *    with it. Return true to take the key. Register it with [XmbTabsPageKeys] (it also unregisters). Not asked: Left / Right on
 *    the bar, L1 / R1 (the shell's tabs), L2 / R2 (they switch the page's tab from anywhere), Y (search).
 *    What the host does with a key the page left: Up goes back to the bar, B (and Escape) closes the tabs page, and the other D-pad
 *    keys and A / Enter are swallowed; anything else goes on to the shell. So a page must take Up itself while its cursor is below
 *    its top (and call [toBar] or return false when it is at the top).
 *  - [toBar]: moves the cursor up to the pill bar (what an unhandled Up does).
 *  - [contentPadding]: the room to keep clear: the side margins (the camera hole in landscape), a small gap under the pill bar and,
 *    at the bottom, the shell's floating Back / Search hints (LocalXmbHintInset). The page's own box fills the whole area under the
 *    pill bar and the host adds no padding, so a page can run under the hints (a scrolling list may; a page of fixed cards keeps clear
 *    of them); apply this to the content.
 *
 * Only the host changes these (the setters are internal for it).
 */
@Stable
internal class XmbTabsPageFocus {
    var contentFocused: Boolean by mutableStateOf(false)
        internal set

    var contentPadding: PaddingValues by mutableStateOf(PaddingValues(0.dp))
        internal set

    /** The page's key handler (see the class doc). Set it with [XmbTabsPageKeys]; one page at a time holds it. */
    var onKey: ((KeyEvent) -> Boolean)? = null

    /**
     * True for a page that is made of ordinary focusable content (the Social tab embeds the Social Hub's own screens): the host then
     * hands the page real Android focus on entering it, passes every key on to it, and takes Up back to the pill bar only when
     * nothing above the focused control is left. Set it with [XmbTabsPagePassthrough].
     */
    var passthrough: Boolean = false

    /** A passthrough page's answer to B: true when it closed a layer of its own (an open post), false to let B go up to the pill bar. */
    var innerBack: (() -> Boolean)? = null

    /** Moves the cursor from the page's content up to the pill bar. */
    fun toBar() {
        contentFocused = false
    }
}

/**
 * Registers [onKey] as [focus]'s key handler while this is composed (the latest lambda is always the one called) and takes it back
 * when it leaves, unless another page has registered since (a tab switch shows the old and the new page at once for a moment).
 */
@Composable
internal fun XmbTabsPageKeys(focus: XmbTabsPageFocus, onKey: (KeyEvent) -> Boolean) {
    val latest by rememberUpdatedState(onKey)
    DisposableEffect(focus) {
        val mine: (KeyEvent) -> Boolean = { e -> latest(e) }
        focus.onKey = mine
        onDispose { if (focus.onKey === mine) focus.onKey = null }
    }
}

/** Marks this page as [XmbTabsPageFocus.passthrough] while it is composed; [innerBack] is asked first when B is pressed. */
@Composable
internal fun XmbTabsPagePassthrough(focus: XmbTabsPageFocus, innerBack: () -> Boolean = { false }) {
    val latest by rememberUpdatedState(innerBack)
    DisposableEffect(focus) {
        val mine: () -> Boolean = { latest() }
        focus.passthrough = true
        focus.innerBack = mine
        onDispose {
            focus.passthrough = false
            if (focus.innerBack === mine) focus.innerBack = null
        }
    }
}
