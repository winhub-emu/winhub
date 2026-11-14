package com.winhub.hernandez.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

data class ThemePreset(
    val name: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    // Dim accent used for low-emphasis fills/borders/tracks (selected-tab gradient base,
    // unselected chip outlines, switch-on track, accent-button bg). Defaults to a darkened
    // primary so it follows the theme; AMOLED overrides it to the exact legacy #002277 to
    // keep the default look byte-identical.
    val accentDim: Color = lerp(primary, Color(0xFF000000), 0.55f),
    val onSurface: Color = Color(0xFFE0E0E0),
    val onSurfaceVariant: Color = Color(0xFFAAAAAA),
    val onBackground: Color = Color(0xFFFFFFFF),
    val onPrimary: Color = Color(0xFFFFFFFF),
    // Secondary accent. Defaults to the primary so every existing look stays single-tone;
    // Halloween overrides it with a witch purple so orange strokes pair with purple fills.
    val secondary: Color = primary,
    val onSecondary: Color = onPrimary,
    val secondaryDim: Color = accentDim,
    // In-game drawer accent ramps. Defaults derive from the primary; Marcescence pins the
    // original blue values so the default drawer stays byte-identical.
    val drawerDim: Color = lerp(primary, Color(0xFF000000), 0.62f),
    val drawerContainer: Color = lerp(primary, background, 0.80f),
    val drawerOnContainer: Color = lerp(primary, Color(0xFFFFFFFF), 0.80f),
    val drawerTertiary: Color = lerp(primary, Color(0xFFFFFFFF), 0.58f),
    // Deck Mode hairline tokens. Defaults derive from surface -> onSurface; Marcescence pins
    // the original graphite values so its look is byte-identical.
    val line: Color = lerp(surface, onSurface, 0.10f),
    val line2: Color = lerp(surface, onSurface, 0.18f),
    val divider: Color = Color(0xFF404040),
    val error: Color = Color(0xFFCF6679),
    // Success/installed indicator. Green everywhere except Monochrome, which keeps its strict
    // black/white/red language (red stays reserved for destructive actions).
    val success: Color = Color(0xFF4CD37F),
    // Elevated "container" surfaces (Material3's surfaceContainer family) for raised cards,
    // dialogs and buttons-on-cards. Derived defaults lerp `surface` toward `onSurface` so every
    // preset gets a tasteful, automatically-recoloring elevation ramp (low → high → highest).
    // AMOLED overrides these to the rebuilt blue-on-black depth so the default card/dialog look
    // keeps its raised feel instead of flattening into the near-black surface.
    val surfaceContainer: Color = lerp(surface, onSurface, 0.05f),
    val surfaceContainerHigh: Color = lerp(surface, onSurface, 0.09f),
    val surfaceContainerHighest: Color = lerp(surface, onSurface, 0.14f),
) {
    fun toColorScheme(accentOverride: Color? = null): androidx.compose.material3.ColorScheme {
        val accent = accentOverride ?: primary
        // A custom (user-picked) accent can be lighter OR darker than the preset's baked onPrimary
        // assumes. A white accent with the default white onPrimary leaves on-accent icons/text
        // invisible (white-on-white FAB / Play button); symmetrically, a near-black accent on a
        // preset whose onPrimary is itself dark (Monochrome, Phosphor, Royal Gold, Frost) leaves
        // dark-on-dark. So derive on-accent from the accent's luminance for any custom accent:
        // light accent -> black on-accent, dark accent -> white on-accent. Built-in presets keep
        // their designed onPrimary so the default look stays byte-identical. (issue #46)
        val onAccent = when {
            accentOverride == null            -> onPrimary
            accentOverride.luminance() > 0.5f -> Color(0xFF000000)
            else                              -> Color(0xFFFFFFFF)
        }
        // A custom accent replaces the whole two-tone pair; built-in presets keep their
        // designed secondary (identical to primary except Halloween's purple).
        val secondaryAccent = if (accentOverride == null) secondary else accent
        val onSecondaryAccent = if (accentOverride == null) onSecondary else onAccent
        return darkColorScheme(
            primary              = accent,
            onPrimary            = onAccent,
            secondary            = secondaryAccent,
            onSecondary          = onSecondaryAccent,
            secondaryContainer   = secondaryAccent.copy(alpha = 0.30f),
            onSecondaryContainer = onSurface,
            background           = background,
            onBackground         = onBackground,
            surface              = surface,
            onSurface            = onSurface,
            surfaceVariant       = surfaceVariant,
            onSurfaceVariant     = onSurfaceVariant,
            // Elevated container ramp — raised cards/dialogs/buttons read above the flat surface.
            surfaceContainerLowest  = surface,
            surfaceContainerLow     = surfaceContainer,
            surfaceContainer        = surfaceContainer,
            surfaceContainerHigh    = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            // Themed divider/hairline token. Previously unset -> fell through to Material's
            // light-mauve default; now follows the preset's `divider` so drawer dividers stay
            // near-black on AMOLED (matches the legacy look) and recolor with other presets.
            outline              = divider,
            error                = error,
        )
    }

    fun toLightColorScheme(accentOverride: Color? = null): androidx.compose.material3.ColorScheme {
        val accent = accentOverride ?: primary
        // Same symmetric on-accent contrast guard as the dark scheme: light custom accent -> dark
        // on-accent, dark custom accent -> light on-accent, so on-accent content stays legible at
        // either extreme. Non-custom presets keep the light scheme's white on-accent. (issue #46)
        val onAccent = when {
            accentOverride == null            -> Color(0xFFFFFFFF)
            accentOverride.luminance() > 0.5f -> Color(0xFF000000)
            else                              -> Color(0xFFFFFFFF)
        }
        val secondaryAccent = if (accentOverride == null) secondary else accent
        val onSecondaryAccent = if (accentOverride == null) onSecondary else onAccent
        return lightColorScheme(
            primary              = accent,
            onPrimary            = onAccent,
            secondary            = secondaryAccent,
            onSecondary          = onSecondaryAccent,
            secondaryContainer   = secondaryAccent.copy(alpha = 0.20f),
            onSecondaryContainer = Color(0xFF1A1A1A),
            background           = Color(0xFFF5F5F5),
            onBackground         = Color(0xFF1A1A1A),
            surface              = Color(0xFFFFFFFF),
            onSurface            = Color(0xFF1A1A1A),
            surfaceVariant       = Color(0xFFEAEAEA),
            onSurfaceVariant     = Color(0xFF555555),
            surfaceContainerLowest  = Color(0xFFFFFFFF),
            surfaceContainerLow     = Color(0xFFF5F5F5),
            surfaceContainer        = Color(0xFFF0F0F0),
            surfaceContainerHigh    = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFE0E0E0),
            outline              = Color(0xFFCCCCCC),
            error                = Color(0xFFB00020),
        )
    }
}

val themePresets: List<ThemePreset> = listOf(
    // ── Marcescence: the app's default. Same graphite surfaces as the Deck shell, with the
    //    Deck blue accent and its original dark on-accent ink. Pinned line tokens keep the
    //    look byte-identical to the pre-theme-picker build.
    ThemePreset(
        name             = "Marcescence",
        background       = Color(0xFF0A0B0D),
        surface          = Color(0xFF121417),
        surfaceVariant   = Color(0xFF1A1D22),
        primary          = Color(0xFF1A9FFF),
        onPrimary        = Color(0xFF03111F),
        onBackground     = Color(0xFFF2F4F7),
        onSurfaceVariant = Color(0xFF9AA3AF),
        line             = Color(0xFF262A31),
        line2            = Color(0xFF343A43),
        drawerDim        = Color(0xFF0E3D63),
        drawerContainer  = Color(0xFF0F2940),
        drawerOnContainer = Color(0xFFD3ECFF),
        drawerTertiary   = Color(0xFF9ED8FF),
    ),
    ThemePreset(
        name             = "Purple",
        background       = Color(0xFF0F0A14),
        surface          = Color(0xFF171020),
        surfaceVariant   = Color(0xFF20162C),
        primary          = Color(0xFFA855F7),
        onPrimary        = Color(0xFFFFFFFF),
        onBackground     = Color(0xFFF4F0FA),
        onSurfaceVariant = Color(0xFFA99CB8),
        line             = Color(0xFF2A2236),
        line2            = Color(0xFF382D47),
    ),
    ThemePreset(
        name             = "Red",
        background       = Color(0xFF140A0A),
        surface          = Color(0xFF1D1111),
        surfaceVariant   = Color(0xFF281717),
        primary          = Color(0xFFEF4444),
        onPrimary        = Color(0xFFFFFFFF),
        onBackground     = Color(0xFFF7EFEF),
        onSurfaceVariant = Color(0xFFB49A9A),
        line             = Color(0xFF33201F),
        line2            = Color(0xFF442B2A),
    ),
    ThemePreset(
        name             = "Green",
        background       = Color(0xFF0A140D),
        surface          = Color(0xFF101D14),
        surfaceVariant   = Color(0xFF16281C),
        primary          = Color(0xFF22C55E),
        onPrimary        = Color(0xFF04140A),
        onBackground     = Color(0xFFEEF7F1),
        onSurfaceVariant = Color(0xFF94AD9C),
        line             = Color(0xFF1F3326),
        line2            = Color(0xFF2C4636),
    ),
    ThemePreset(
        name             = "Monochrome",
        background       = Color(0xFF000000),
        surface          = Color(0xFF0A0A0A),
        surfaceVariant   = Color(0xFF161616),
        primary          = Color(0xFFFFFFFF),
        onPrimary        = Color(0xFF000000),
        onSurface        = Color(0xFFEDEDED),
        onBackground     = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFF9A9A9A),
        line             = Color(0xFF242424),
        line2            = Color(0xFF333333),
        success          = Color(0xFFE5E5E5),
    ),
    ThemePreset(
        name             = "Pink",
        background       = Color(0xFF140A10),
        surface          = Color(0xFF1D1017),
        surfaceVariant   = Color(0xFF281520),
        primary          = Color(0xFFEC4899),
        onPrimary        = Color(0xFFFFFFFF),
        onBackground     = Color(0xFFF9EEF4),
        onSurfaceVariant = Color(0xFFB89AAB),
        line             = Color(0xFF331F2B),
        line2            = Color(0xFF442B3A),
    ),
    // ── Halloween: candlelit pumpkin. Orange strokes + witch-purple fills; the purple only
    //    appears on low-emphasis surfaces (toggles, slider track, selected chips/rail), never
    //    as body text or a full-bleed background. Destructive red stays red.
    ThemePreset(
        name             = "Halloween",
        background       = Color(0xFF0B0708),
        surface          = Color(0xFF161011),
        surfaceVariant   = Color(0xFF20171A),
        primary          = Color(0xFFFF7518),
        onPrimary        = Color(0xFF1A0B02),
        secondary        = Color(0xFF9D4EDD),
        onSecondary      = Color(0xFFFFFFFF),
        accentDim        = Color(0xFF3A1D63),
        secondaryDim     = Color(0xFF3A1D63),
        onSurface        = Color(0xFFEADFD6),
        onBackground     = Color(0xFFF4EDE7),
        onSurfaceVariant = Color(0xFFB5A498),
        line             = Color(0xFF2C2024),
        line2            = Color(0xFF3A2A2F),
    ),
    // ── Extra dark themes. Added before Custom so every saved index below stays valid.
    ThemePreset(
        name             = "Ocean",
        background       = Color(0xFF0A1416),
        surface          = Color(0xFF0F1D20),
        surfaceVariant   = Color(0xFF15292D),
        primary          = Color(0xFF14B8A6),
        onPrimary        = Color(0xFF031412),
        onBackground     = Color(0xFFEEF7F6),
        onSurfaceVariant = Color(0xFF92AEAC),
        line             = Color(0xFF1E3337),
        line2            = Color(0xFF2A474C),
    ),
    ThemePreset(
        name             = "Amber",
        background       = Color(0xFF120D07),
        surface          = Color(0xFF1C150C),
        surfaceVariant   = Color(0xFF271D11),
        primary          = Color(0xFFF59E0B),
        onPrimary        = Color(0xFF1A1000),
        onBackground     = Color(0xFFF8F1E6),
        onSurfaceVariant = Color(0xFFB5A48C),
        line             = Color(0xFF33261A),
        line2            = Color(0xFF443423),
    ),
    ThemePreset(
        name             = "Sunset",
        background       = Color(0xFF130A12),
        surface          = Color(0xFF1D111C),
        surfaceVariant   = Color(0xFF281825),
        primary          = Color(0xFFFB7185),
        onPrimary        = Color(0xFF1F060C),
        secondary        = Color(0xFF8B5CF6),
        onSecondary      = Color(0xFFFFFFFF),
        secondaryDim     = Color(0xFF3B2468),
        onBackground     = Color(0xFFF9EEF2),
        onSurfaceVariant = Color(0xFFB89AA6),
        line             = Color(0xFF33202F),
        line2            = Color(0xFF452B41),
    ),
    ThemePreset(
        name             = "Nord",
        background       = Color(0xFF242933),
        surface          = Color(0xFF2E3440),
        surfaceVariant   = Color(0xFF363D4C),
        primary          = Color(0xFF88C0D0),
        onPrimary        = Color(0xFF1B2530),
        onSurface        = Color(0xFFD8DEE9),
        onBackground     = Color(0xFFECEFF4),
        onSurfaceVariant = Color(0xFF9AA5B8),
        line             = Color(0xFF424A5C),
        line2            = Color(0xFF4C566A),
        success          = Color(0xFFA3BE8C),
    ),
    ThemePreset(
        name             = "Midnight",
        background       = Color(0xFF000000),
        surface          = Color(0xFF07070C),
        surfaceVariant   = Color(0xFF10101A),
        primary          = Color(0xFF6366F1),
        onPrimary        = Color(0xFFFFFFFF),
        onBackground     = Color(0xFFF2F2FA),
        onSurfaceVariant = Color(0xFF9A9AB0),
        line             = Color(0xFF1A1A26),
        line2            = Color(0xFF26263A),
    ),
    ThemePreset(
        name             = "Christmas",
        background       = Color(0xFF0B0F0C),
        surface          = Color(0xFF121A14),
        surfaceVariant   = Color(0xFF1A261D),
        primary          = Color(0xFFE11D48),
        onPrimary        = Color(0xFFFFFFFF),
        secondary        = Color(0xFF16A34A),
        onSecondary      = Color(0xFFFFFFFF),
        secondaryDim     = Color(0xFF14532D),
        onBackground     = Color(0xFFF6F3EE),
        onSurfaceVariant = Color(0xFFA3B2A6),
        line             = Color(0xFF223427),
        line2            = Color(0xFF2F4736),
    ),
    ThemePreset(
        name             = "Dracula",
        background       = Color(0xFF191A21),
        surface          = Color(0xFF21222C),
        surfaceVariant   = Color(0xFF282A36),
        primary          = Color(0xFFBD93F9),
        onPrimary        = Color(0xFF1E1033),
        secondary        = Color(0xFFFF79C6),
        onSecondary      = Color(0xFF2A0A1D),
        secondaryDim     = Color(0xFF5A2B4A),
        onSurface        = Color(0xFFF8F8F2),
        onBackground     = Color(0xFFF8F8F2),
        onSurfaceVariant = Color(0xFF9DA3C4),
        line             = Color(0xFF343746),
        line2            = Color(0xFF44475A),
        success          = Color(0xFF50FA7B),
    ),
    ThemePreset(
        name             = "Gruvbox",
        background       = Color(0xFF1D2021),
        surface          = Color(0xFF282828),
        surfaceVariant   = Color(0xFF32302F),
        primary          = Color(0xFFFE8019),
        onPrimary        = Color(0xFF1D1100),
        onSurface        = Color(0xFFEBDBB2),
        onBackground     = Color(0xFFEBDBB2),
        onSurfaceVariant = Color(0xFFA89984),
        line             = Color(0xFF3C3836),
        line2            = Color(0xFF504945),
        success          = Color(0xFFB8BB26),
    ),
    ThemePreset(
        name             = "Solarized",
        background       = Color(0xFF00212B),
        surface          = Color(0xFF002B36),
        surfaceVariant   = Color(0xFF073642),
        primary          = Color(0xFF2AA198),
        onPrimary        = Color(0xFF001A1F),
        secondary        = Color(0xFFB58900),
        onSecondary      = Color(0xFF1F1600),
        secondaryDim     = Color(0xFF5C4500),
        onSurface        = Color(0xFFEEE8D5),
        onBackground     = Color(0xFFFDF6E3),
        onSurfaceVariant = Color(0xFF93A1A1),
        line             = Color(0xFF0F4452),
        line2            = Color(0xFF1A5666),
        success          = Color(0xFF859900),
    ),
    ThemePreset(
        name             = "Tokyo Night",
        background       = Color(0xFF16161E),
        surface          = Color(0xFF1A1B26),
        surfaceVariant   = Color(0xFF24283B),
        primary          = Color(0xFF7AA2F7),
        onPrimary        = Color(0xFF0B1226),
        onSurface        = Color(0xFFC0CAF5),
        onBackground     = Color(0xFFC0CAF5),
        onSurfaceVariant = Color(0xFF8A93BC),
        line             = Color(0xFF2F3549),
        line2            = Color(0xFF3B4261),
        success          = Color(0xFF9ECE6A),
    ),
    ThemePreset(
        name             = "Catppuccin",
        background       = Color(0xFF11111B),
        surface          = Color(0xFF1E1E2E),
        surfaceVariant   = Color(0xFF2A2B3C),
        primary          = Color(0xFFCBA6F7),
        onPrimary        = Color(0xFF1E1033),
        onSurface        = Color(0xFFCDD6F4),
        onBackground     = Color(0xFFCDD6F4),
        onSurfaceVariant = Color(0xFFA6ADC8),
        line             = Color(0xFF313244),
        line2            = Color(0xFF45475A),
        success          = Color(0xFFA6E3A1),
    ),
    ThemePreset(
        name             = "Rose Pine",
        background       = Color(0xFF191724),
        surface          = Color(0xFF1F1D2E),
        surfaceVariant   = Color(0xFF26233A),
        primary          = Color(0xFFEBBCBA),
        onPrimary        = Color(0xFF2A1A1A),
        secondary        = Color(0xFFC4A7E7),
        onSecondary      = Color(0xFF1F1330),
        secondaryDim     = Color(0xFF4A3A66),
        onSurface        = Color(0xFFE0DEF4),
        onBackground     = Color(0xFFE0DEF4),
        onSurfaceVariant = Color(0xFF908CAA),
        line             = Color(0xFF2A273F),
        line2            = Color(0xFF393552),
    ),
    ThemePreset(
        name             = "Cyberpunk",
        background       = Color(0xFF08080A),
        surface          = Color(0xFF111114),
        surfaceVariant   = Color(0xFF1B1B20),
        primary          = Color(0xFFFCEE0A),
        onPrimary        = Color(0xFF141200),
        secondary        = Color(0xFF00F0FF),
        onSecondary      = Color(0xFF00181A),
        secondaryDim     = Color(0xFF0A4A50),
        onBackground     = Color(0xFFFAFAF0),
        onSurfaceVariant = Color(0xFFA8A89A),
        line             = Color(0xFF2A2A22),
        line2            = Color(0xFF3A3A2E),
    ),
    ThemePreset(
        name             = "Synthwave",
        background       = Color(0xFF120B22),
        surface          = Color(0xFF1B1230),
        surfaceVariant   = Color(0xFF261A42),
        primary          = Color(0xFFFF2E97),
        onPrimary        = Color(0xFFFFFFFF),
        secondary        = Color(0xFF00E5FF),
        onSecondary      = Color(0xFF00181A),
        secondaryDim     = Color(0xFF0B4A55),
        onBackground     = Color(0xFFF6EEFF),
        onSurfaceVariant = Color(0xFFAE9DCB),
        line             = Color(0xFF33244F),
        line2            = Color(0xFF45306A),
    ),
    ThemePreset(
        name             = "Forest",
        background       = Color(0xFF0C0F08),
        surface          = Color(0xFF14180D),
        surfaceVariant   = Color(0xFF1D2214),
        primary          = Color(0xFF84CC16),
        onPrimary        = Color(0xFF0F1A02),
        onBackground     = Color(0xFFF1F5E6),
        onSurfaceVariant = Color(0xFFA3AD8E),
        line             = Color(0xFF2A3220),
        line2            = Color(0xFF38432B),
    ),
    // Neutral near-black grays with a soft blue accent: the theme the Glass look is tuned on. Its surfaces carry no colour, so
    // the glass derived from them is plain charcoal instead of a tint of the theme's family. The glass panels' fills are fairly
    // light, so onSurfaceVariant is lifted a little by the glass's contrast helper to stay readable on the lightest glass.
    ThemePreset(
        name             = "Charcoal",
        background       = Color(0xFF151515),
        surface          = Color(0xFF1F1F1F),
        surfaceVariant   = Color(0xFF2A2A2A),
        primary          = Color(0xFF8AB4FF),
        onSurface        = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFFC0C0C0),
        onPrimary        = Color(0xFF06142E),
        divider          = Color(0xFF2E2E2E),
    ),
    // Hidden slot backing the legacy custom-accent picker. Not listed in the Appearance picker.
    ThemePreset(
        name          = "Custom",
        background    = Color(0xFF121212),
        surface       = Color(0xFF1E1E1E),
        surfaceVariant= Color(0xFF2A2A2A),
        primary       = Color(0xFF0055FF),
    ),
)

// "Custom" is always the last entry, so its index tracks the list size. New named presets
// are inserted *before* it, so existing saved indices keep pointing at the same preset.
val CUSTOM_PRESET_INDEX = themePresets.size - 1

// The app's default theme. Resolved by name so reordering or adding presets above it can never
// silently point the app at a different colour scheme.
val DEFAULT_PRESET_INDEX = themePresets.indexOfFirst { it.name == "Marcescence" }.coerceAtLeast(0)

/** The theme the Glass look is tuned on (see its entry above). Resolved by name, like the default. */
val CHARCOAL_PRESET_INDEX = themePresets.indexOfFirst { it.name == "Charcoal" }.coerceAtLeast(0)

/** The user-selectable themes (the hidden Custom slot is excluded). */
val selectableThemePresets: List<ThemePreset> = themePresets.filter { it.name != "Custom" }
