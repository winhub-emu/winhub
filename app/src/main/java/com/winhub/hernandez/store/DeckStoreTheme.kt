package com.winhub.hernandez.store

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.winhub.hernandez.ui.deck.StoreStyle
import com.winhub.hernandez.ui.screens.Deck

/**
 * The Deck Graphite palette mapped onto Material3's color slots.
 *
 * Every store screen — Steam/Epic/GOG/Amazon's Store, Library, Friends and Profile tabs, their
 * game cards, chips, buttons and tab rows — is built from Material components that read
 * `MaterialTheme.colorScheme`. Overriding that slot here restyles the whole storefront to the Deck
 * look without rewriting a single tab composable, so the working install/download/auth logic is
 * untouched.
 */
internal val DeckGraphiteScheme = darkColorScheme(
    primary = Deck.primary,
    onPrimary = Deck.onPrimary,
    primaryContainer = Deck.surfaceVariant,
    onPrimaryContainer = Deck.onBackground,
    secondary = Deck.onSurfaceVariant,
    onSecondary = Deck.background,
    secondaryContainer = Deck.surfaceVariant,
    onSecondaryContainer = Deck.onSurfaceVariant,
    tertiary = Deck.attention,
    onTertiary = Deck.background,
    background = Deck.background,
    onBackground = Deck.onBackground,
    surface = Deck.surface,
    onSurface = Deck.onBackground,
    surfaceVariant = Deck.surfaceVariant,
    onSurfaceVariant = Deck.onSurfaceVariant,
    surfaceTint = Deck.primary,
    inverseSurface = Deck.onBackground,
    inverseOnSurface = Deck.background,
    outline = Deck.line,
    outlineVariant = Deck.line,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0A0A),
    errorContainer = Deck.surfaceVariant,
    onErrorContainer = Color(0xFFFFB4B4),
)

/** Applies the Graphite Deck look to a store screen. Nest inside [WinlatorTheme]. */
@Composable
internal fun DeckStoreTheme(content: @Composable () -> Unit) {
    rememberDeckMotion()
    // The XMB store style brings its own glass theme (StoreXmbTheme); the Graphite palette must not paint over it.
    if (StoreStyle.isXmb(LocalContext.current)) content()
    else MaterialTheme(colorScheme = DeckGraphiteScheme, content = content)
}
