package com.winhub.hernandez.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

object AppThemeState {
    private lateinit var themePrefs: SharedPreferences

    private val _presetIndex = MutableStateFlow(DEFAULT_PRESET_INDEX)
    val presetIndex: StateFlow<Int> = _presetIndex

    // Compose-observable snapshot of the resolved preset. DeckPalette reads this during
    // composition, so changing the theme recomposes every Deck widget live. Kept separate from
    // the StateFlow above because Deck drawing code needs a plain snapshot, not a Flow.
    private val _deckPreset = mutableStateOf(themePresets.first())
    val deckPreset: ThemePreset get() = _deckPreset.value

    private val _customAccent = MutableStateFlow(Color(0xFF0055FF))
    val customAccent: StateFlow<Color> = _customAccent

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    /** Whether the drawer's "Stores" section (GOG, Epic, Amazon, Steam) is shown. */
    private val _showStores = MutableStateFlow(true)
    val showStores: StateFlow<Boolean> = _showStores

    /** The two drawer storage cards, toggled independently. */
    private val _showInternalStorage = MutableStateFlow(true)
    val showInternalStorage: StateFlow<Boolean> = _showInternalStorage

    private val _showSdStorage = MutableStateFlow(true)
    val showSdStorage: StateFlow<Boolean> = _showSdStorage

    /** Global interface scale (Compose density multiplier) and font scale, applied in
     *  WinlatorTheme. 1.0 = unchanged; clamped 0.5..1.5 by the setters. */
    private val _uiScale = MutableStateFlow(0.9f)
    val uiScale: StateFlow<Float> = _uiScale

    private val _fontScale = MutableStateFlow(0.9f)
    val fontScale: StateFlow<Float> = _fontScale

    /** General tab style for Deck Mode: "horizontal" (top strip, default) or "vertical" (left rail). */
    private val _generalTabStyle = MutableStateFlow("horizontal")
    val generalTabStyle: StateFlow<String> = _generalTabStyle

    /** Opacity of the Deck Mode top tab bar's background, 0..1.0 (1.0 = solid, the old look). */
    private val _topBarOpacity = MutableStateFlow(1f)
    val topBarOpacity: StateFlow<Float> = _topBarOpacity

    // The preset whose background/surface colors back the custom accent
    private val _customBaseIndex = MutableStateFlow(0)

    private fun resolvePreset(index: Int): ThemePreset {
        val base = if (index == CUSTOM_PRESET_INDEX)
            themePresets.getOrElse(_customBaseIndex.value) { themePresets.first() }
        else
            themePresets.getOrElse(index) { themePresets.first() }
        if (index != CUSTOM_PRESET_INDEX) return base
        val accent = _customAccent.value
        val onAccent = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
        return base.copy(
            primary = accent,
            onPrimary = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            accentDim = lerp(accent, Color(0xFF000000), 0.55f),
            secondaryDim = lerp(accent, Color(0xFF000000), 0.55f),
        )
    }

    private fun refreshDeckPreset() {
        _deckPreset.value = resolvePreset(_presetIndex.value)
    }

    val colorScheme: kotlinx.coroutines.flow.Flow<ColorScheme> =
        combine(_presetIndex, _customAccent, _isDarkMode) { index, accent, dark ->
            val preset = if (index == CUSTOM_PRESET_INDEX)
                themePresets.getOrElse(_customBaseIndex.value) { themePresets.first() }
            else
                themePresets.getOrElse(index) { themePresets.first() }
            val override = if (index == CUSTOM_PRESET_INDEX) accent else null
            if (dark) preset.toColorScheme(accentOverride = override)
            else      preset.toLightColorScheme(accentOverride = override)
        }

    /** Dim accent paired with [colorScheme]. For real presets it's the preset's own
     *  accentDim (AMOLED = exact #002277 so the default is unchanged); for a custom accent
     *  it's derived from the live accent so dim fills/borders follow the picker too. */
    val accentDim: kotlinx.coroutines.flow.Flow<Color> =
        combine(_presetIndex, _customAccent) { index, accent ->
            if (index == CUSTOM_PRESET_INDEX) lerp(accent, Color(0xFF000000), 0.55f)
            else themePresets.getOrElse(index) { themePresets.first() }.accentDim
        }

    fun currentAccentDimSnapshot(): Color {
        val index = _presetIndex.value
        return if (index == CUSTOM_PRESET_INDEX) lerp(_customAccent.value, Color(0xFF000000), 0.55f)
               else themePresets.getOrElse(index) { themePresets.first() }.accentDim
    }

    fun init(context: Context) {
        themePrefs = context.getSharedPreferences("winlator_theme", Context.MODE_PRIVATE)

        // One-time migration: before 2026-06-30 there were 8 presets and "Custom" was index 7.
        // New named presets are inserted before Custom, pushing it to the new last index, so any
        // user previously on Custom (the only thing index 7 could mean back then) is remapped.
        if (!themePrefs.getBoolean("preset_schema_v2", false)) {
            if (themePrefs.getInt("preset_index", 1) == 7 && CUSTOM_PRESET_INDEX != 7) {
                themePrefs.edit().putInt("preset_index", CUSTOM_PRESET_INDEX).apply()
            }
            themePrefs.edit().putBoolean("preset_schema_v2", true).apply()
        }

        // One-time reset: the preset list was rebuilt for the seven-theme picker, so every old
        // index (which pointed into the previous, much longer list) is meaningless. Start those
        // users on Marcescence; afterwards the picker's choice persists normally.
        if (!themePrefs.getBoolean("preset_schema_v3", false)) {
            themePrefs.edit()
                .putInt("preset_index", DEFAULT_PRESET_INDEX)
                .putBoolean("preset_schema_v3", true)
                .apply()
        }
        _presetIndex.value = themePrefs.getInt("preset_index", DEFAULT_PRESET_INDEX)
            .coerceIn(0, themePresets.size - 1)
        val savedAccent = themePrefs.getInt("custom_accent", Color(0xFF0055FF).toArgb())
        _customAccent.value = Color(savedAccent)
        _customBaseIndex.value = themePrefs.getInt("custom_base_index", 1).coerceIn(0, CUSTOM_PRESET_INDEX)
        _isDarkMode.value = true
        _showStores.value = themePrefs.getBoolean("show_stores", true)
        _showInternalStorage.value = themePrefs.getBoolean("show_internal_storage", true)
        _showSdStorage.value = themePrefs.getBoolean("show_sd_storage", true)
        _uiScale.value = themePrefs.getFloat("ui_scale", 0.9f).coerceIn(0.5f, 1.5f)
        _fontScale.value = themePrefs.getFloat("font_scale", 0.9f).coerceIn(0.5f, 1.5f)
        _generalTabStyle.value = if (themePrefs.getBoolean("general_tab_style_horizontal", true)) "horizontal" else "vertical"
        _topBarOpacity.value = themePrefs.getFloat("top_bar_opacity", 1f).coerceIn(0f, 1f)
        refreshDeckPreset()
    }

    /** Show or hide the drawer's Stores section. Default on, so nothing changes until asked. */
    fun setShowStores(show: Boolean) {
        _showStores.value = show
        themePrefs.edit().putBoolean("show_stores", show).apply()
    }

    fun setShowInternalStorage(show: Boolean) {
        _showInternalStorage.value = show
        themePrefs.edit().putBoolean("show_internal_storage", show).apply()
    }

    fun setShowSdStorage(show: Boolean) {
        _showSdStorage.value = show
        themePrefs.edit().putBoolean("show_sd_storage", show).apply()
    }

    /** Global Compose density multiplier. Clamped 0.5..1.5 so a stray value can't leave
     *  the UI unusable. Applied live in WinlatorTheme. */
    fun setUiScale(scale: Float) {
        val clamped = scale.coerceIn(0.5f, 1.5f)
        _uiScale.value = clamped
        themePrefs.edit().putFloat("ui_scale", clamped).apply()
    }

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.5f, 1.5f)
        _fontScale.value = clamped
        themePrefs.edit().putFloat("font_scale", clamped).apply()
    }

    /** Deck Mode general tab style: "horizontal" (top strip, default) or "vertical" (left rail). */
    fun setGeneralTabStyle(style: String) {
        val s = if (style == "vertical") "vertical" else "horizontal"
        _generalTabStyle.value = s
        themePrefs.edit().putBoolean("general_tab_style_horizontal", s == "horizontal").apply()
    }

    fun setTopBarOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        _topBarOpacity.value = clamped
        themePrefs.edit().putFloat("top_bar_opacity", clamped).apply()
    }

    fun setPreset(index: Int) {
        _presetIndex.value = index.coerceIn(0, themePresets.size - 1)
        themePrefs.edit().putInt("preset_index", _presetIndex.value).apply()
        refreshDeckPreset()
    }

    fun setCustomAccent(color: Color) {
        // Snapshot the current base only when leaving a real preset for custom mode
        if (_presetIndex.value != CUSTOM_PRESET_INDEX) {
            _customBaseIndex.value = _presetIndex.value
            themePrefs.edit().putInt("custom_base_index", _customBaseIndex.value).apply()
        }
        _customAccent.value = color
        _presetIndex.value = CUSTOM_PRESET_INDEX
        themePrefs.edit()
            .putInt("custom_accent", color.toArgb())
            .putInt("preset_index", CUSTOM_PRESET_INDEX)
            .apply()
        refreshDeckPreset()
    }

    fun currentColorSchemeSnapshot(): ColorScheme {
        val index = _presetIndex.value
        val preset = if (index == CUSTOM_PRESET_INDEX)
            themePresets.getOrElse(_customBaseIndex.value) { themePresets.first() }
        else
            themePresets.getOrElse(index) { themePresets.first() }
        val override = if (index == CUSTOM_PRESET_INDEX) _customAccent.value else null
        return if (_isDarkMode.value) preset.toColorScheme(accentOverride = override)
               else                   preset.toLightColorScheme(accentOverride = override)
    }

    /** Java-friendly entry point: returns the current accent (primary) color as an
     *  ARGB int. Used by the remaining legacy AndroidView widgets (CPUListView) so
     *  they can tint their CheckBox/ToggleButton drawables to match the Compose
     *  accent picker. */
    @JvmStatic
    fun getCurrentAccentArgb(): Int = currentColorSchemeSnapshot().primary.toArgb()
}
