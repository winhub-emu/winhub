package com.winhub.hernandez.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.ControllerFocusIndication
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.winhub.hernandez.ui.ControllerNavRoot
import com.winhub.hernandez.ui.screens.DeckDragFrame

private val DefaultColorScheme = darkColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    background       = Background,
    onBackground     = OnBackground,
    surface          = Surface,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline          = Divider,
    error            = Error,
)

private val BricolageFontFamily = FontFamily(
    Font(R.font.bricolage_grotesque),
)

private val Default = Typography()

private val AppTypography = Typography(
    displayLarge   = Default.displayLarge.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    displayMedium  = Default.displayMedium.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    displaySmall   = Default.displaySmall.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    headlineLarge  = Default.headlineLarge.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    headlineMedium = Default.headlineMedium.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    headlineSmall  = Default.headlineSmall.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    titleLarge     = Default.titleLarge.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    titleMedium    = Default.titleMedium.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    titleSmall     = Default.titleSmall.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    bodyLarge      = Default.bodyLarge.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    bodyMedium     = Default.bodyMedium.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    bodySmall      = Default.bodySmall.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    labelLarge     = Default.labelLarge.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    labelMedium    = Default.labelMedium.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
    labelSmall     = Default.labelSmall.copy(fontFamily = BricolageFontFamily, fontWeight = FontWeight(600)),
)

/** Dim accent for low-emphasis fills/borders/tracks. Not part of Material's ColorScheme, so
 *  it rides alongside via this CompositionLocal. Fallback #002277 = the legacy AMOLED value, so
 *  anything reading it outside WinlatorTheme still gets today's default. */
val LocalAccentDim = staticCompositionLocalOf { Color(0xFF002277) }

@Composable
fun WinlatorTheme(content: @Composable () -> Unit) {
    val colorScheme by AppThemeState.colorScheme.collectAsState(initial = AppThemeState.currentColorSchemeSnapshot())
    val accentDim by AppThemeState.accentDim.collectAsState(initial = AppThemeState.currentAccentDimSnapshot())
    val uiScale by AppThemeState.uiScale.collectAsState()
    val fontScale by AppThemeState.fontScale.collectAsState()
    // Fold the user's interface-scale prefs into the ambient density so every WinlatorTheme
    // surface — including the in-game drawer/dialogs — scales together. The native OSC and
    // perf HUD aren't Compose, so they stay fixed on their own.
    val base = LocalDensity.current
    val scaledDensity = Density(base.density * uiScale, base.fontScale * fontScale)
    CompositionLocalProvider(
        LocalAccentDim provides accentDim,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
        ) {
            // Purple focus outline in place of the ripple, plus the D-pad walk root every screen
            // inherits (keys are only dispatched while something in the tree holds focus). Set
            // inside MaterialTheme so nothing deeper can shadow it.
            CompositionLocalProvider(LocalIndication provides remember(colorScheme.primary) { ControllerFocusIndication(colorScheme.primary) }) {
                // One animated focus ring for the whole window; screens with their own frame override it.
                ControllerNavRoot { DeckDragFrame(Modifier.fillMaxSize()) { content() } }
            }
        }
    }
}
