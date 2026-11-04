package com.winhub.hernandez.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.ui.screens.DeckPalette
import com.winhub.hernandez.ui.screens.DeckSound
import kotlinx.coroutines.delay

// Building blocks of the in-game menu's console look: a glass panel, controller focus, tiles, a tab
// rail, the Quick page and the button-hint bar. The tabs' own controls live in XServerDrawer.kt.

/** Controller focus: an accent ring, plus a small lift when [lift] > 1. [onFocus] reports focus in or out. */
@Composable
internal fun Modifier.consoleFocus(shape: Shape, lift: Float = 1f, onFocus: (Boolean) -> Unit = {}): Modifier {
    var focused by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (focused) lift else 1f, tween(150), label = "consoleFocus")
    return this
        .onFocusChanged {
            // A soft tick as the cursor lands on something new, like the Deck's menus.
            if (it.hasFocus && !focused) {
                DeckSound.play(DeckSound.MOVE)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            focused = it.hasFocus
            onFocus(it.hasFocus)
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (focused) Modifier.border(2.dp, DeckPalette.primary, shape) else Modifier)
}

/** The one rounded outline every control in the menu sits in: transparent, a hairline edge, nothing grey behind it. */
internal val ControlShape = RoundedCornerShape(12.dp)
private val SegmentShape = RoundedCornerShape(9.dp)

/** Several options held in ONE transparent rounded rectangle. Fill it with [SegmentCell]s. */
@Composable
internal fun SegmentedFrame(
    modifier: Modifier = Modifier,
    scroll: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .border(1.dp, DeckPalette.line, ControlShape)
            .padding(3.dp)
            .then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * One option inside a [SegmentedFrame]. Transparent; the picked one gets an accent outline and accent
 * text. [weighted] = false for a scrolling frame, where the cells size to their label.
 */
@Composable
internal fun RowScope.SegmentCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 13.sp,
    weighted: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = (if (weighted) Modifier.weight(1f) else Modifier)
            .then(modifier)
            .then(if (enabled) Modifier.consoleFocus(SegmentShape, 1.04f) else Modifier)
            .clip(SegmentShape)
            .then(if (selected) Modifier.border(1.5.dp, DeckPalette.primary, SegmentShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            color = when {
                !enabled -> DeckPalette.onBackground.copy(alpha = 0.4f)
                selected -> DeckPalette.primary
                else -> DeckPalette.onBackground
            },
            fontSize = fontSize,
            lineHeight = fontSize * 1.15f,
            textAlign = TextAlign.Center,
            fontWeight = if (selected && enabled) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** The menu panel: translucent graphite with a soft accent glow at the top and a hairline edge. */
internal fun Modifier.drawerGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(DeckPalette.background.copy(alpha = 0.88f))
    .background(Brush.verticalGradient(listOf(DeckPalette.primary.copy(alpha = 0.16f), Color.Transparent), endY = 700f))
    .border(1.dp, DeckPalette.line2, shape)

/** A 48dp quick-action tile. Its [label] is announced through [onFocus] so the header can show it. */
@Composable
internal fun DrawerTile(
    iconRes: Int,
    label: String,
    tint: Color = DeckPalette.onBackground,
    focusRequester: FocusRequester? = null,
    onFocus: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .consoleFocus(shape, 1.1f, onFocus)
            .clip(shape)
            .border(1.dp, DeckPalette.line, shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Icon(painterResource(iconRes), contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** One tab on the left rail: icon only, accent when it is the open tab, a dot for unread. */
@Composable
internal fun DrawerRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    unread: Int = 0,
    onFocus: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .consoleFocus(shape, 1.08f, onFocus)
            .clip(shape)
            .background(if (selected) DeckPalette.primary.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) DeckPalette.primary else DeckPalette.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(DeckPalette.error),
            )
        }
    }
}

/** The rail icon for a tab. */
internal fun drawerTabIcon(tab: TabType): ImageVector = when (tab) {
    TabType.QUICK -> Icons.Filled.Star
    TabType.GRAPHICS -> Icons.Filled.Tune
    TabType.HUD -> Icons.Filled.Speed
    TabType.RESHADE -> Icons.Filled.AutoAwesome
    TabType.CONTROLS -> Icons.Filled.SportsEsports
    TabType.AUDIO -> Icons.Filled.VolumeUp
    TabType.ADVANCED -> Icons.Filled.Settings
    TabType.FRIENDS -> Icons.Filled.People
    TabType.TV -> Icons.Filled.Tv
    TabType.TASK_MANAGER -> Icons.Filled.Memory
}

/** "21:40 · 87%": the clock and battery, refreshed while the menu is up. */
@Composable
internal fun rememberDrawerClock(): String {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            text = if (level >= 0 && scale > 0) "$time  ·  ${level * 100 / scale}%" else time
            delay(20_000)
        }
    }
    return text
}

/** The Quick page: live cards for the settings used most; each opens the tab that holds it. */
@Composable
internal fun DrawerQuickPage(onOpen: (TabType) -> Unit) {
    val state = XServerDrawerState
    val limiterOn by state.fpsLimiterEnabled.collectAsState()
    val limit by state.fpsLimit.collectAsState()
    val fgOn by state.frameGenEnabled.collectAsState()
    val fgMult by state.frameGenMultiplier.collectAsState()
    val hz by state.currentRefreshRate.collectAsState()
    val vulkan by state.rendererIsVulkan.collectAsState()
    val cards = listOf(
        QuickCard("FPS limit", if (limiterOn) "$limit" else "Off", Icons.Filled.Speed, limiterOn, TabType.HUD),
        QuickCard("Frame generation", if (fgOn) "${fgMult}x" else "Off", Icons.Filled.AutoAwesome, fgOn, TabType.GRAPHICS),
        QuickCard("Refresh rate", if (hz > 0) "$hz Hz" else "Auto", Icons.Filled.Refresh, false, TabType.HUD),
        QuickCard("Renderer", if (vulkan) "Vulkan" else "OpenGL", Icons.Filled.Tune, false, TabType.GRAPHICS),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { c -> QuickCardView(c, Modifier.weight(1f)) { onOpen(c.tab) } }
            }
        }
        Text("Pick a card to open its settings.", color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
    }
}

private class QuickCard(val label: String, val value: String, val icon: ImageVector, val on: Boolean, val tab: TabType)

@Composable
private fun QuickCardView(card: QuickCard, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .consoleFocus(shape, 1.04f)
            .clip(shape)
            .background(if (card.on) DeckPalette.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (card.on) DeckPalette.primary else DeckPalette.line, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(card.icon, contentDescription = null, tint = DeckPalette.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(card.label, color = DeckPalette.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            card.value,
            color = if (card.on) DeckPalette.primary else DeckPalette.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The bottom hint bar: which button does what. */
@Composable
internal fun DrawerHintBar(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        DrawerHint("A", "Select")
        DrawerHint("B", "Close")
        DrawerHint("LB RB", "Tab")
    }
}

@Composable
private fun DrawerHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .border(1.dp, DeckPalette.line, RoundedCornerShape(11.dp))
                .padding(horizontal = 8.dp),
        ) {
            Text(key, color = DeckPalette.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = DeckPalette.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
    }
}

/** A labelled 48dp button for the exit confirmation. */
@Composable
internal fun DrawerPill(
    text: String,
    danger: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val color = if (danger) DeckPalette.error else DeckPalette.onBackground
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(48.dp)
            .consoleFocus(shape, 1.06f)
            .clip(shape)
                        .border(1.dp, if (danger) DeckPalette.error else DeckPalette.line, shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
