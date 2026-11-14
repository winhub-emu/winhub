package com.winhub.hernandez.ui.deck

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Side tab rails in the XMB interface (the page-tab rail, the store rail, the settings category list):
//
//   Up / Down      moves focus to the next tab AND selects it: no A needed (touch taps select as before)
//   Right          into the content, as before; the tab keeps its selected look (faint accent fill), only the gliding outline
//                  leaves it (the look depends on `selected`, never on focus)
//   Left (back)    from the content's left edge lands on the SELECTED tab, not on the nearest one
//
// How it works. A rail marks its container with [xmbRailGroup] and each tab with [xmbRailItem]; both share one [XmbRailState].
//  * The container notes when an Up / Down key goes down inside it (it never consumes the key).
//  * A tab that gains focus decides from that ([xmbRailFocusAct]): an Up / Down key a moment ago means focus MOVED inside the
//    rail, so the tab is selected; no such key means focus ENTERED the rail from outside (Left from the content, a first
//    focus), so, unless this already is the selected tab, focus is handed on to the selected tab and nothing is selected.
//    The decision is made from the key rather than from "did the rail have focus before", which would depend on the order in
//    which Compose delivers the group's and the tab's focus events.
//  * The selected tab carries [XmbRailState.selectedRequester], which is what the redirect asks for focus. A selected tab that
//    is not composed (scrolled out of a lazy list) just makes the request fail quietly: focus stays where the search put it.
//  * The selection runs a few ms after the move and is dropped by the next focus gain, so holding Up / Down across many tabs
//    switches the content only for the tab the key stops on, and a heavy page is not rebuilt per tab.
//
// Compose 1.6's `FocusProperties.enter` (experimental, behaviour for D-pad searches not verifiable without a device) is
// deliberately not used: the key-based redirect above needs only stable API.

/** How long after an Up / Down key a tab gaining focus still counts as "focus moved inside the rail". */
internal const val XMB_RAIL_MOVE_WINDOW_MS = 400L

/** The wait between landing on a tab by D-pad and switching to it. */
internal const val XMB_RAIL_SELECT_DELAY_MS = 70L

/** What a tab does when it gains focus. */
internal enum class XmbRailAct { NONE, SELECT, REDIRECT }

/**
 * A tab gained focus. The selected tab: nothing to do. Another tab with an Up / Down key just before ([moveFresh]): focus
 * moved inside the rail, select it. Another tab without one: focus entered the rail, send it to the selected tab.
 */
internal fun xmbRailFocusAct(selected: Boolean, moveFresh: Boolean): XmbRailAct = when {
    selected -> XmbRailAct.NONE
    moveFresh -> XmbRailAct.SELECT
    else -> XmbRailAct.REDIRECT
}

/** [stamp] (uptime of the last Up / Down key in the rail, 0 = none yet) is at most [windowMs] before [now]. */
internal fun xmbRailMoveFresh(now: Long, stamp: Long, windowMs: Long = XMB_RAIL_MOVE_WINDOW_MS): Boolean =
    stamp > 0L && now >= stamp && now - stamp <= windowMs

/** The keys that move between the tabs of a vertical rail (Tab too: a keyboard walks the tabs with it, and must not be bounced back). */
internal fun xmbIsRailMoveKey(key: Key): Boolean = key == Key.DirectionUp || key == Key.DirectionDown || key == Key.Tab

/** The state of one rail: make it with [rememberXmbRailState], hand it to [xmbRailGroup] and every [xmbRailItem]. */
@Stable
internal class XmbRailState(internal val scope: CoroutineScope, val selectedRequester: FocusRequester = FocusRequester()) {
    // [selectedRequester]: on the selected tab (only that one carries it); a host that wants to focus that tab itself passes its own.

    /** Uptime (ms) of the last Up / Down key down inside the rail; 0 = none. */
    internal var lastMoveAt = 0L

    private var pending: Job? = null

    internal fun noteKey(key: Key) {
        if (xmbIsRailMoveKey(key)) lastMoveAt = SystemClock.uptimeMillis()
    }

    internal fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    internal fun selectSoon(action: () -> Unit) {
        pending?.cancel()
        pending = scope.launch {
            delay(XMB_RAIL_SELECT_DELAY_MS)
            action()
        }
    }
}

@Composable
internal fun rememberXmbRailState(requester: FocusRequester? = null): XmbRailState {
    val scope = rememberCoroutineScope()
    return remember(scope, requester) { if (requester != null) XmbRailState(scope, requester) else XmbRailState(scope) }
}

/**
 * Marks the container of a rail's tabs (and of any actions in it, so Up / Down from those onto a tab counts as a move). Only
 * listens: no key is consumed. Not in the XMB interface: the modifier unchanged.
 */
@Composable
internal fun Modifier.xmbRailGroup(state: XmbRailState): Modifier {
    if (!rememberXmbHome()) return this
    return this.onPreviewKeyEvent { k ->
        if (k.type == KeyEventType.KeyDown) state.noteKey(k.key)
        false
    }
}

/**
 * One tab of a rail. Put it right where the tab's focus ring is registered: BEFORE the clickable / focusable it observes.
 * [selected] is whether this is the tab shown; [onSelect] switches to it (the same callback as a tap: it must be a no-op for
 * a tab that is already selected). No [state] or not the XMB interface: the modifier unchanged.
 */
@Composable
internal fun Modifier.xmbRailItem(state: XmbRailState?, selected: Boolean, onSelect: () -> Unit): Modifier {
    if (state == null || !rememberXmbHome()) return this
    val selectedNow by rememberUpdatedState(selected)
    val selectNow by rememberUpdatedState(onSelect)
    return this
        .then(if (selected) Modifier.focusRequester(state.selectedRequester) else Modifier)
        .onFocusChanged { st ->
            if (st.isFocused) {
                state.cancelPending()
                when (xmbRailFocusAct(selectedNow, xmbRailMoveFresh(SystemClock.uptimeMillis(), state.lastMoveAt))) {
                    XmbRailAct.SELECT -> state.selectSoon { if (!selectedNow) selectNow() }
                    XmbRailAct.REDIRECT -> state.scope.launch { runCatching { state.selectedRequester.requestFocus() } }
                    XmbRailAct.NONE -> Unit
                }
            }
        }
}

/**
 * The colour a SELECTED tab's text keeps whether or not the outline is on it (the colour the ink would turn it to while hot),
 * so the tab looks the same when the outline moves on into the content. Null outside the XMB interface.
 */
@Composable
internal fun xmbSelectedInk(): Color? {
    if (!rememberXmbHome()) return null
    val cs = MaterialTheme.colorScheme
    val glass = rememberXmbGlass()
    return remember(cs.primary, cs.onSurface, glass) { xmbInkColor(cs.primary, cs.onSurface, glass.panel) }
}
