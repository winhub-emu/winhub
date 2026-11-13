package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * True while the Steam-style Home is on screen. The shell lets B through to the Home then (its games row and tabs page close
 * their own layers), instead of taking it for "back" itself.
 */
internal object SteamHomeKeys {
    @Volatile
    var active: Boolean = false
}

/** The Steam-style Home with no games yet: a prompt to add one. A adds, Down (or a tap on a tab) opens the tabs page. */
@Composable
internal fun SteamEmptyHome(
    focus: FocusRequester,
    enabled: Boolean,
    onAdd: () -> Unit,
    onTabs: () -> Unit,
) {
    val addNow = rememberUpdatedState(onAdd)
    val tabsNow = rememberUpdatedState(onTabs)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .focusProperties { canFocus = enabled }
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (!enabled || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (e.nativeKeyEvent.repeatCount == 0) addNow.value()
                        true
                    }
                    Key.DirectionDown -> { tabsNow.value(); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(Unit) { detectTapGestures { addNow.value() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your library is empty", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(
                "A  Add a game      Down  Stores, Downloads, Friends, Social",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** What the Steam Home's row is holding open (its options, a nested column, the All Games page): [handler] closes the top one, true when it did. */
internal class SteamBackHolder {
    var handler: (() -> Boolean)? = null
}

/** Lets a held key repeat no faster than [minMs]: a held D-pad key would otherwise step a list (and buzz the pad) 30 times a second. */
internal class KeyRepeatGate(private val minMs: Long = 110L) {
    private var last = 0L

    fun allow(fresh: Boolean): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (!fresh && now - last < minMs) return false
        last = now
        return true
    }
}

/** The sound of a step in an XMB column (nested settings, tools): a move, a choice, a way out, a section change. */
internal fun xmbNavSound(key: XmbKey) {
    when (key) {
        XmbKey.Up, XmbKey.Down, XmbKey.Left, XmbKey.Right -> DeckSound.play(DeckSound.MOVE)
        XmbKey.A -> DeckSound.play(DeckSound.SELECT)
        XmbKey.B -> DeckSound.play(DeckSound.BACK)
        XmbKey.L1, XmbKey.R1 -> DeckSound.play(DeckSound.TAB)
    }
}

/** The sound for a key on a Deck page (Settings, Containers, Contents...), which has no sounds of its own. */
internal fun deckPageKeySound(e: KeyEvent, gate: KeyRepeatGate) {
    if (e.type != KeyEventType.KeyDown) return
    val fresh = e.nativeKeyEvent.repeatCount == 0
    when (e.key) {
        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> if (gate.allow(fresh)) DeckSound.play(DeckSound.MOVE)
        Key.ButtonA, Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> if (fresh) DeckSound.play(DeckSound.SELECT)
        else -> Unit
    }
}
