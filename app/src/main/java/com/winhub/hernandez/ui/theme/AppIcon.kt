package com.winhub.hernandez.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * The launcher icon: the default is the four blue circles, and each theme has the same icon with its own colour for the circles. Android
 * can't recolour an icon at runtime, so every icon is an `activity-alias` of the main activity in the manifest ("AppIcon_<key>"), exactly
 * one of them enabled, and picking an icon is switching which one. The setting (Settings > Theme > App icon) is the default icon (off), the
 * icon of whatever theme is on, or one fixed icon picked by hand.
 *
 * Switching can make the launcher reload its icons, and a launcher that pins a shortcut to the alias that was on may drop that shortcut.
 */
object AppIcon {
    private const val TAG = "AppIcon"

    /** The preference that holds the choice: [DEFAULT], [FOLLOW_THEME] or the key of one icon. */
    const val KEY = "app_icon"
    const val DEFAULT = "default"
    const val FOLLOW_THEME = "theme"
    private const val ALIAS_PREFIX = "com.winhub.hernandez.AppIcon_"

    // Marcescence, the default theme, keeps the default icon, and Custom has no icon of its own. The keys are the aliases' in the manifest.
    private fun keyOf(presetName: String): String? = when (presetName) {
        "Marcescence" -> DEFAULT
        "Custom" -> null
        else -> presetName.lowercase().replace(' ', '_')
    }

    private val colourIcons: List<Pair<String, String>> =
        themePresets.mapNotNull { p -> keyOf(p.name)?.takeIf { it != DEFAULT }?.let { it to p.name } }

    /** What the setting offers (key to name): the default, following the theme, then one icon per theme. */
    val options: List<Pair<String, String>> = listOf(DEFAULT to "Default (blue)", FOLLOW_THEME to "Follow the theme") + colourIcons

    fun choice(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, DEFAULT) ?: DEFAULT

    /** Saves the choice and puts the icon it means on the launcher. */
    fun setChoice(context: Context, value: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, value).apply()
        apply(context)
    }

    /** Puts the icon the choice means on the launcher (a call to the package manager, so off the main thread). */
    fun apply(context: Context) {
        val app = context.applicationContext
        Thread({ applyNow(app) }, "app-icon").start()
    }

    private fun applyNow(context: Context) {
        val wanted = when (val pick = choice(context)) {
            DEFAULT -> DEFAULT
            FOLLOW_THEME -> themePresets.getOrNull(AppThemeState.presetIndex.value)?.let { keyOf(it.name) } ?: DEFAULT
            else -> pick
        }
        val all = listOf(DEFAULT) + colourIcons.map { it.first }
        val key = if (wanted in all) wanted else DEFAULT
        val pm = context.packageManager
        fun component(k: String) = ComponentName(context.packageName, ALIAS_PREFIX + k)
        // What the manifest says (only the default alias is on) counts until a state has been set.
        fun enabled(k: String) = when (pm.getComponentEnabledSetting(component(k))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> k == DEFAULT
        }
        try {
            if (all.all { enabled(it) == (it == key) }) return
            // The new one goes on first, so the launcher always has an icon for the app.
            pm.setComponentEnabledSetting(component(key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            all.filter { it != key && enabled(it) }.forEach {
                pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No launcher alias for $key", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "The launcher icon could not be switched", e)
        }
    }
}
