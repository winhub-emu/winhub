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
 * Deck Home layout (Settings > Appearance, landscape): Full XMB carries the stores and Social as cards above the games
 * and drops their tabs from the rail, and Steam puts one row of covers at the bottom with Stores, Downloads, Friends and
 * Social as pages that slide up over it. Any other saved value (including the old "classic" and "xmb") is Full XMB.
 */
internal object DeckHomeStyle {
    const val KEY = "home_style"
    const val FULL_XMB = "full_xmb"
    const val STEAM = "steam"

    fun read(prefs: SharedPreferences): String {
        return if (prefs.getString(KEY, FULL_XMB) == STEAM) STEAM else FULL_XMB
    }

    fun isFull(prefs: SharedPreferences): Boolean = read(prefs) == FULL_XMB
}

/**
 * What a game's Settings opens from the Steam and Full XMB Home (Settings > Appearance > Home): Classic is the Deck's own
 * settings page over Home, XMB is the nested XMB columns.
 */
internal object GameSettingsStyle {
    const val KEY = "xmb_game_settings_style"
    const val CLASSIC = "classic"
    const val XMB = "xmb"

    fun read(prefs: SharedPreferences): String = if (prefs.getString(KEY, CLASSIC) == XMB) XMB else CLASSIC

    fun isClassic(prefs: SharedPreferences): Boolean = read(prefs) == CLASSIC
}

/** The saved Home style; follows the setting live, so the rail updates the moment it is flipped. */
@Composable
internal fun rememberHomeStyle(): String {
    val prefs = PreferenceManager.getDefaultSharedPreferences(LocalContext.current)
    var style by remember { mutableStateOf(DeckHomeStyle.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == DeckHomeStyle.KEY) style = DeckHomeStyle.read(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return style
}

/** True while Full XMB is chosen. */
@Composable
internal fun rememberFullXmb(): Boolean = rememberHomeStyle() == DeckHomeStyle.FULL_XMB

/** True while the Steam Home style is chosen. */
@Composable
internal fun rememberSteamHome(): Boolean = rememberHomeStyle() == DeckHomeStyle.STEAM
