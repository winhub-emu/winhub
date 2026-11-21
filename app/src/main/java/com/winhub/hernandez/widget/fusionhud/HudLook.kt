package com.winhub.hernandez.widget.fusionhud

import android.graphics.Color

/**
 * What the Fusion HUD looks like beyond its layout: a neon outline in the theme's accent, the WinHub mark,
 * and text tinted by the theme. Each part follows the theme or takes a colour picked by hand.
 *
 * It travels in the HUD config string as its own keys ([keys]), so the three places that edit that string
 * (the Deck editor, the in-game drawer and the classic dialog) can all carry it without knowing more.
 * [from] reads those keys back; a missing key gives the default, so a config saved before this existed
 * gets the new look.
 */
data class HudLook(
    /** The outline glows (two blurred passes under the sharp one). */
    val neon: Boolean = true,
    /** [OUTLINE_GREY], [OUTLINE_THEME] or [OUTLINE_MANUAL] (the colour in [outlineHex]). */
    val outline: String = OUTLINE_THEME,
    val outlineHex: String = DEFAULT_OUTLINE_HEX,
    /** [LOGO_THEME], [LOGO_MANUAL] (the colour in [logoHex]) or [LOGO_OFF]. The mark's three dots take the colour. */
    val logo: String = LOGO_THEME,
    val logoHex: String = DEFAULT_LOGO_HEX,
    /** [TEXT_DEFAULT] (Fusion's own colours), [TEXT_THEME] or [TEXT_MANUAL] (the colour in [textHex]). */
    val text: String = TEXT_THEME,
    val textHex: String = DEFAULT_TEXT_HEX,
    /** [FPS_SPEED] (green, amber, red by frame rate) or [FPS_THEME] (plain text colour). */
    val fps: String = FPS_THEME,
    val fpsGlow: Boolean = true,
) {
    val outlineColor: Int get() = parse(outlineHex, DEFAULT_OUTLINE_HEX)
    val logoColor: Int get() = parse(logoHex, DEFAULT_LOGO_HEX)
    val textColor: Int get() = parse(textHex, DEFAULT_TEXT_HEX)

    /** The config keys for this look. `hudOutlineAccent` stays in step for anything that still reads the old switch. */
    fun keys(): List<String> = listOf(
        "hudNeon=${if (neon) 1 else 0}",
        "hudOutlineColor=$outline", "hudOutlineHex=$outlineHex",
        "hudLogo=$logo", "hudLogoHex=$logoHex",
        "hudText=$text", "hudTextHex=$textHex",
        "hudFps=$fps", "hudFpsGlow=${if (fpsGlow) 1 else 0}",
        "hudOutlineAccent=${if (outline == OUTLINE_GREY) 0 else 1}",
    )

    companion object {
        const val OUTLINE_GREY = "grey"
        const val OUTLINE_THEME = "theme"
        const val OUTLINE_MANUAL = "manual"
        const val LOGO_THEME = "theme"
        const val LOGO_MANUAL = "manual"
        const val LOGO_OFF = "off"
        const val TEXT_DEFAULT = "default"
        const val TEXT_THEME = "theme"
        const val TEXT_MANUAL = "manual"
        const val FPS_SPEED = "speed"
        const val FPS_THEME = "theme"

        const val DEFAULT_OUTLINE_HEX = "#FF3DF0"
        const val DEFAULT_LOGO_HEX = "#7CFFB2"
        const val DEFAULT_TEXT_HEX = "#FFB020"

        /** Colours offered for a hand-picked outline, logo or text colour (reachable with a controller): hex to name. */
        val PALETTE: List<Pair<String, String>> = listOf(
            "#00E5FF" to "Cyan", "#FF3DF0" to "Magenta", "#7CFFB2" to "Mint", "#FFB020" to "Amber",
            "#FF4D4D" to "Red", "#A78BFA" to "Violet", "#1EA1FF" to "Blue", "#FF7A1A" to "Orange",
            "#FF5CA8" to "Pink", "#FFFFFF" to "White",
        )

        /** Reads the look out of a config; [get] returns the stored value for a key, or null / "" when it has none. */
        fun from(get: (String) -> String?): HudLook {
            fun str(key: String, allowed: Set<String>, default: String): String =
                get(key)?.trim()?.lowercase()?.takeIf { it in allowed } ?: default
            fun flag(key: String, default: Boolean): Boolean = when (get(key)?.trim()) {
                "1" -> true
                "0" -> false
                else -> default
            }
            fun hex(key: String, default: String): String {
                val v = get(key)?.trim().orEmpty().uppercase()
                return if (Regex("#[0-9A-F]{6}").matches(v)) v else default
            }
            return HudLook(
                neon = flag("hudNeon", true),
                outline = str("hudOutlineColor", setOf(OUTLINE_GREY, OUTLINE_THEME, OUTLINE_MANUAL), OUTLINE_THEME),
                outlineHex = hex("hudOutlineHex", DEFAULT_OUTLINE_HEX),
                logo = str("hudLogo", setOf(LOGO_THEME, LOGO_MANUAL, LOGO_OFF), LOGO_THEME),
                logoHex = hex("hudLogoHex", DEFAULT_LOGO_HEX),
                text = str("hudText", setOf(TEXT_DEFAULT, TEXT_THEME, TEXT_MANUAL), TEXT_THEME),
                textHex = hex("hudTextHex", DEFAULT_TEXT_HEX),
                fps = str("hudFps", setOf(FPS_SPEED, FPS_THEME), FPS_THEME),
                fpsGlow = flag("hudFpsGlow", true),
            )
        }

        private fun parse(hex: String, fallback: String): Int =
            try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { Color.parseColor(fallback) }
    }
}
