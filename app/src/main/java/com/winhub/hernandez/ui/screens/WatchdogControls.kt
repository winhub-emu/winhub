package com.winhub.hernandez.ui.screens

import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.RadioButton
import androidx.compose.material3.Slider
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.perf.TempWatchdog
import com.winhub.hernandez.perf.TempWatchdog.ThresholdMode
import com.winhub.hernandez.widget.HudMetrics
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val WATCHDOG_WHATS_THIS =
    "A safety backstop. If your device gets too hot, it automatically undoes the performance " +
    "settings you've turned on and restores your device to how it was.\n\n" +
    "It is NOT what throttles your device. Your device has its own built-in thermal protection " +
    "that lowers clocks when it heats up. The watchdog is separate — it only reverts WinHub's " +
    "changes.\n\n" +
    "It only becomes important once you enable \"Disable thermal throttling,\" which removes your " +
    "device's own protection — then the watchdog is your only software safety net.\n\n" +
    "Presets: Conservative (reverts near your device's normal throttle point) · Balanced " +
    "(recommended — runs hot, reverts before the hard limit) · Aggressive (max performance, little " +
    "margin) · Manual (set your own).\n\n" +
    "Reverting when you exit a game, background the app, or if it crashes is always on and can't be " +
    "disabled."

/**
 * The Temperature Watchdog controls, shared verbatim by the App Settings Performance menu and the
 * in-game drawer so the two surfaces are identical and stay in sync (both bind to the one
 * [TempWatchdog] singleton). Device-wide.
 *
 *  - ON/OFF toggle with a DYNAMIC label showing the resolved ceiling + mode; turning it OFF requires
 *    the hard scroll+checkbox disclaimer, turning it ON does not.
 *  - Aggressiveness preset selector (Conservative / Balanced / Aggressive / Manual). Selecting one of
 *    the three presets shows an informational dialog (single "Got it") with the actual °C on THIS
 *    device. Manual reveals a slider (60–device top trip).
 */
@Composable
fun WatchdogSection() {
    val enabled by TempWatchdog.enabled.collectAsState()
    val mode by TempWatchdog.mode.collectAsState()
    val ceiling by TempWatchdog.ceilingC.collectAsState()

    var showOffDisclaimer by remember { mutableStateOf(false) }
    var showWhatsThis by remember { mutableStateOf(false) }
    var infoMode by remember { mutableStateOf<ThresholdMode?>(null) }

    // Live device temperatures — poll ~1.5s while this section is visible (same HudMetrics reads as
    // the in-game readouts).
    val context = LocalContext.current
    var cpuTemp by remember { mutableStateOf<Int?>(null) }
    var gpuTemp by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val metrics = HudMetrics(context.applicationContext)
        while (true) {
            cpuTemp = metrics.cpuTempC
            gpuTemp = metrics.gpuTempC
            delay(1500)
        }
    }

    // Dynamic ON/OFF row with a "What's this?" info button.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Thermal auto-revert (~$ceiling °C · ${modeLabel(mode)})",
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { showWhatsThis = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Info, "What is the Temperature Watchdog?",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
        Switch(
            checked = enabled,
            onCheckedChange = { on -> if (on) TempWatchdog.setWatchdogEnabled(true) else showOffDisclaimer = true }
        )
    }

    // Live temps.
    Text(
        "CPU ${cpuTemp?.let { "$it °C" } ?: "—"} · GPU ${gpuTemp?.let { "$it °C" } ?: "—"}",
        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium
    )

    // Device default thermal points.
    if (TempWatchdog.hasDeviceTrips) {
        Text(
            "Your device: throttles ~${TempWatchdog.firstTripC} °C · hard limit ${TempWatchdog.topTripC} °C",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
        )
    } else {
        Text(
            "Your device didn't expose thermal limits — using a safe ${TempWatchdog.FALLBACK_CEILING_C} °C fallback.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
        )
    }

    // Preset selector.
    Text("Threshold", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    ThresholdMode.values().forEach { m ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    TempWatchdog.setMode(m)
                    if (m != ThresholdMode.MANUAL) infoMode = m // presets explain themselves
                }
        ) {
            RadioButton(selected = mode == m, onClick = {
                TempWatchdog.setMode(m)
                if (m != ThresholdMode.MANUAL) infoMode = m
            })
            Spacer(Modifier.width(4.dp))
            Text(modeLabel(m) + "  ·  ~${TempWatchdog.resolvedCeilingFor(m)} °C",
                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
    }

    // Manual slider (only in MANUAL mode).
    if (mode == ThresholdMode.MANUAL) {
        val manual by TempWatchdog.manualCeilingC.collectAsState()
        val top = TempWatchdog.topTripC ?: TempWatchdog.MANUAL_MAX_C
        val sliderMax = minOf(TempWatchdog.MANUAL_MAX_C, top).coerceAtLeast(TempWatchdog.MANUAL_MIN_C + 1)
        var sliderVal by remember(manual) { mutableStateOf(manual.toFloat()) }
        val warn = sliderVal.roundToInt() > (top - 5)
        Text(
            "Manual: ${sliderVal.roundToInt()} °C" + if (warn) "  ⚠ close to the hard limit ($top °C)" else "",
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp, fontWeight = FontWeight.Medium
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { TempWatchdog.setManualCeilingC(sliderVal.roundToInt()) },
            valueRange = TempWatchdog.MANUAL_MIN_C.toFloat()..sliderMax.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    // "What is the Temperature Watchdog?" explainer.
    if (showWhatsThis) {
        PerfInfoDialog(
            title = "What is the Temperature Watchdog?",
            body = WATCHDOG_WHATS_THIS,
            onDismiss = { showWhatsThis = false }
        )
    }

    // Info dialog for the selected preset.
    infoMode?.let { m ->
        PerfInfoDialog(
            title = modeLabel(m) + " · ~${TempWatchdog.resolvedCeilingFor(m)} °C",
            body = presetInfo(m),
            onDismiss = { infoMode = null }
        )
    }

    // Hard disclaimer to disarm the watchdog.
    if (showOffDisclaimer) {
        PerfDisclaimerDialog(
            title = "Disable thermal safety?",
            body = PerfDisclaimerCopy.WATCHDOG_OFF,
            confirmLabel = "Turn watchdog OFF",
            onDismiss = { showOffDisclaimer = false },
            onConfirm = { showOffDisclaimer = false; TempWatchdog.setWatchdogEnabled(false) }
        )
    }
}

private fun modeLabel(mode: ThresholdMode): String = when (mode) {
    ThresholdMode.CONSERVATIVE -> "Conservative"
    ThresholdMode.BALANCED -> "Balanced"
    ThresholdMode.AGGRESSIVE -> "Aggressive"
    ThresholdMode.MANUAL -> "Manual"
}

private fun presetInfo(mode: ThresholdMode): String {
    val first = TempWatchdog.firstTripC ?: TempWatchdog.FALLBACK_CEILING_C
    val ceiling = TempWatchdog.resolvedCeilingFor(mode)
    return when (mode) {
        ThresholdMode.CONSERVATIVE ->
            "Reverts near where your device would normally start throttling (~$first°C). " +
                "Safest, least extra performance."
        ThresholdMode.BALANCED ->
            "Lets it run hot but reverts ~10°C before your device's hard limit (~$ceiling°C). Recommended."
        ThresholdMode.AGGRESSIVE ->
            "Maximum sustained clocks, only ~3°C of margin before the hard limit (~$ceiling°C). " +
                "For users who know their device."
        ThresholdMode.MANUAL ->
            "You choose the exact revert temperature."
    }
}
