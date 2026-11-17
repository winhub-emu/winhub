package com.winhub.hernandez.retro

import android.content.Context
import androidx.annotation.DrawableRes

/**
 * Console illustrations (res/drawable*, "ic_sys_<key>"): KyleBing's retro-game-console-icons (GPL-3.0) plus flat icons in the same
 * style for the consoles that pack lacks. Looked up by name, so adding a console needs no code.
 */
internal object RetroIcons {
    // Our system id -> the pack's key, only where the two differ (a direct id match resolves on its own).
    private val ALIAS = mapOf(
        "nes" to "fc", "snes" to "sfc", "genesis" to "md", "sms" to "ms", "ps1" to "ps", "atari2600" to "atari", "gc" to "gamecube",
        "fbneo" to "arcade", "pcengine" to "pce", "pcenginecd" to "pcecd", "wonderswan" to "ws", "wonderswancolor" to "wsc",
        "virtualboy" to "vb", "colecovision" to "col", "intellivision" to "itv", "neogeocd" to "neocd", "pico8" to "pico",
    )

    private val cache = HashMap<String, Int>()

    /** The illustration of [system], or 0 when there is none. */
    @DrawableRes
    fun of(context: Context, system: RetroSystem): Int = synchronized(cache) {
        cache.getOrPut(system.id) {
            val keys = listOfNotNull(system.iconKey, ALIAS[system.id], system.id)
            keys.firstNotNullOfOrNull { key ->
                context.resources.getIdentifier("ic_sys_$key", "drawable", context.packageName).takeIf { it != 0 }
            } ?: 0
        }
    }
}
