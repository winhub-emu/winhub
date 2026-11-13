package com.winhub.hernandez.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.winhub.hernandez.ui.deck.LocalDeckDensity
import com.winhub.hernandez.ui.deck.XmbHighlightHost
import com.winhub.hernandez.ui.deck.XmbKeyboardWindow
import com.winhub.hernandez.ui.deck.rememberXmbHome

/**
 * The body of a dialog drawn in the Deck density: a dialog is a window of its own, so it needs the Deck scale handed to it again.
 * With [host] the XMB highlight host wraps it, so the gliding outline works inside the dialog.
 */
@Composable
fun DeckDialogScaled(host: Boolean = true, content: @Composable () -> Unit) {
    val deck = LocalDeckDensity.current
    val xmbHost = host && rememberXmbHome()
    XmbKeyboardWindow {
        val body: @Composable () -> Unit = {
            // A dialog is its own window: left-to-right there too, whatever the phone's language.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (xmbHost) XmbHighlightHost(enabled = true) { content() } else content()
            }
        }
        if (deck == null) body() else CompositionLocalProvider(LocalDensity provides deck, content = body)
    }
}

/** The Deck shell hides Android's navigation bar over a dialog's window too; WinHub's dialogs keep their own bars, so there is nothing to hide. */
@Composable
fun DeckDialogBars() {}
