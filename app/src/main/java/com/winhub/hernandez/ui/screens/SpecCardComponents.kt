package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.theme.OnSurfaceVariant

// Shared component-spec card theme used by the Shortcuts (game) list and the Containers
// list. The card splits a container/shortcut's graphics + runtime components by how often
// you check them: renderer, DXVK and frame-gen are bright "primary" chips; driver, VKD3D
// and the x86 backend sit on a calm muted "secondary" line with a colour dot each.

// Colour-coded component chips.
internal val ChipRendColor = Color(0xFF36D1DC)
internal val ChipDriverColor = Color(0xFFFFB02E)
internal val ChipDxvkColor = Color(0xFF5BD6A6)
internal val ChipVkd3dColor = Color(0xFFC08CFF)
internal val ChipFgColor = Color(0xFFFF6FAE)
internal val ChipCpuColor = Color(0xFFFF8A5C)
internal val ChipEosColor = Color(0xFF1A73E8) // Epic Online Services blue (matches the library EOS badge)

// Parse a dxwrapperConfig string ("version=2.4,vkd3dVersion=2.8,...") into (DXVK, VKD3D) versions.
internal fun parseDxwrapperConfig(cfg: String): Pair<String, String> {
    val map = cfg.split(",").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
    }.toMap()
    return (map["version"] ?: "") to (map["vkd3dVersion"] ?: "")
}

// What a Wayland container/shortcut really renders through. The stored renderer id is the X11
// setting and nothing runs it on Wayland: every frame goes through the embedded compositor, which
// is always Vulkan. The editors spell this out as "Vulkan (Wayland compositor)"; a chip has room
// for two words.
internal const val WAYLAND_RENDERER_CHIP = "Vulkan (Wayland)"

// Map a renderer id to a display label, or "" if unknown. Legacy "opengl"/"gl" ids degrade to Vulkan.
// Pass [wayland] = does this container/shortcut effectively run on Wayland (WineWaylandSupport
// .runsOnWayland - never re-derive it here): the stored id is then meaningless and the chip shows
// the compositor instead. Every renderer id is an X11 present path, so the chip names the backend
// on both sides and two cards can be told apart at a glance.
internal fun rendererLabelOf(renderer: String, wayland: Boolean): String =
    if (wayland) WAYLAND_RENDERER_CHIP else when (renderer.lowercase()) {
        "vulkan", "opengl", "gl" -> "Vulkan (X11)"
        "egl" -> "EGL (X11)"
        "surfacedirect" -> "SurfaceDirect (X11)"
        else -> ""
    }

// Map a frame-gen engine id (bionic/lsfg/lsfg-native/off) to a display label,
// or "" if off/unknown.
internal fun frameGenLabelOf(engine: String): String = when (engine) {
    "bionic" -> "Win-FG Native"
    "lsfg" -> "LSFG-VK"
    "lsfg-native" -> "LSFG Native"
    else -> ""
}

// The two spec rows shared by both cards: bright primary chips, then the muted secondary
// dot-line. Both rows hide themselves when they have nothing to show.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpecChipRows(
    rendererLabel: String,
    dxvkVersion: String,
    frameGenLabel: String,
    driverLabel: String,
    vkd3dVersion: String,
    backendLabel: String,
    eosEnabled: Boolean = false,
) {
    val hasPrimary = rendererLabel.isNotEmpty() || dxvkVersion.isNotEmpty() || frameGenLabel.isNotEmpty() || eosEnabled
    if (hasPrimary) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            // Renderer + DXVK are the app's headline component chips: route them to the
            // theme accent so a preset/custom accent recolors them app-wide. Frame-gen keeps
            // its own pink identity (genuine "frame-gen active" status, not an accent surface).
            if (rendererLabel.isNotEmpty()) CompChip(rendererLabel, MaterialTheme.colorScheme.primary)
            if (dxvkVersion.isNotEmpty()) CompChip("DXVK $dxvkVersion", MaterialTheme.colorScheme.primary)
            if (frameGenLabel.isNotEmpty()) CompChip(frameGenLabel, ChipFgColor)
            // EOS auth-injection active for this Epic launch — its own blue identity chip.
            if (eosEnabled) CompChip("EOS", ChipEosColor)
        }
    }
    val secondary = buildList {
        if (driverLabel.isNotEmpty()) add(driverLabel to ChipDriverColor)
        if (vkd3dVersion.isNotEmpty()) add("VKD3D $vkd3dVersion" to ChipVkd3dColor)
        if (backendLabel.isNotEmpty()) add(backendLabel to ChipCpuColor)
    }
    if (secondary.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 7.dp),
        ) {
            secondary.forEach { (text, color) -> SecondarySpec(text, color) }
        }
    }
}

// A muted secondary spec: small colour dot + dimmed label.
@Composable
internal fun SecondarySpec(text: String, color: Color, textColor: Color = OnSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.85f)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// A bright primary chip: a pill with a tinted translucent background.
@Composable
internal fun CompChip(text: String, color: Color, textColor: Color = color) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
