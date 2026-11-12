package com.winhub.hernandez.ui.screens

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager

/**
 * Deck Home layout (Settings > Appearance, landscape): Classic keeps Home to the games, Full XMB also
 * carries the stores and Social as cards above them and drops their tabs from the rail.
 * Any other saved value (including the old "xmb" / "classic") is Classic.
 */
internal object DeckHomeStyle {
    const val KEY = "home_style"
    const val CLASSIC = "classic"
    const val FULL_XMB = "full_xmb"

    fun isFull(prefs: SharedPreferences): Boolean = prefs.getString(KEY, CLASSIC) == FULL_XMB
}

/** True while Full XMB is chosen; follows the setting live, so the rail updates the moment it is flipped. */
@Composable
internal fun rememberFullXmb(): Boolean {
    val prefs = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    var full by remember { mutableStateOf(DeckHomeStyle.isFull(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == DeckHomeStyle.KEY) full = DeckHomeStyle.isFull(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return full
}
