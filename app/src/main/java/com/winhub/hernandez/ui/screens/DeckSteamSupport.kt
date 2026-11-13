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
