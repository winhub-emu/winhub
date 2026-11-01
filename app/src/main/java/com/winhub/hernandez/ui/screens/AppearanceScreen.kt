package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.winhub.hernandez.ui.components.ColorPicker
import com.winhub.hernandez.ui.theme.AppThemeState
import com.winhub.hernandez.ui.theme.CUSTOM_PRESET_INDEX
import com.winhub.hernandez.ui.theme.Divider
import com.winhub.hernandez.ui.theme.OnSurface
import com.winhub.hernandez.ui.theme.OnSurfaceVariant
import com.winhub.hernandez.ui.theme.themePresets

@Composable
fun AppearanceScreen() {
    val selectedIndex by AppThemeState.presetIndex.collectAsState()
    val customAccent by AppThemeState.customAccent.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ── Preset themes ────────────────────────────────────────────────
        SectionLabel("Theme Presets")

        val rows = themePresets.chunked(4)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { preset ->
                    val index = themePresets.indexOf(preset)
                    val isSelected = selectedIndex == index
                    PresetSwatch(
                        preset = preset,
                        isSelected = isSelected,
                        isCustomSlot = index == CUSTOM_PRESET_INDEX,
                        customAccent = customAccent,
                        onClick = { AppThemeState.setPreset(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // pad last row if uneven
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

        // ── Custom accent picker ─────────────────────────────────────────
        SectionLabel("Custom Accent Color")
        Text(
            text = "Selecting a color below switches to Custom preset",
            color = OnSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        ColorPicker(
            initialColor = customAccent,
            onColorChanged = { AppThemeState.setCustomAccent(it) }
        )

        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

        // ── General tab style (Deck Mode navigation) ────────────────
        SectionLabel("General Tab Style")
        Text(
            text = "How the main Home / Storage / Controls tabs are laid out in Deck Mode",
            color = OnSurfaceVariant,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        val tabStyle by AppThemeState.generalTabStyle.collectAsState()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TabStyleChoice("Horizontal", tabStyle == "horizontal", Modifier.weight(1f)) {
                AppThemeState.setGeneralTabStyle("horizontal")
            }
            TabStyleChoice("Vertical", tabStyle == "vertical", Modifier.weight(1f)) {
                AppThemeState.setGeneralTabStyle("vertical")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TabStyleChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val border = if (selected) MaterialTheme.colorScheme.primary else Divider
    val textColor = if (selected) MaterialTheme.colorScheme.primary else OnSurfaceVariant
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(label, color = textColor, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ─── Preset swatch ───────────────────────────────────────────────────────────

@Composable
private fun PresetSwatch(
    preset: com.winhub.hernandez.ui.theme.ThemePreset,
    isSelected: Boolean,
    isCustomSlot: Boolean,
    customAccent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isCustomSlot) customAccent else preset.primary
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(preset.background)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Divider,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Mini UI mockup inside the swatch
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(2.dp)).background(preset.surface))
                Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(preset.surfaceVariant))
                Box(Modifier.width(20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(preset.surfaceVariant))
            }
            if (isSelected) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Text(
            text = preset.name,
            color = if (isSelected) MaterialTheme.colorScheme.primary else OnSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

// ─── Section label ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = OnSurface,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
}
