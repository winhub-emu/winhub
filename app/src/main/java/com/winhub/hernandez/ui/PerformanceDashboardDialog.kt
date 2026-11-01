package com.winhub.hernandez.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.winhub.hernandez.perf.PerfGpuTurbo
import com.winhub.hernandez.perf.PerfNodeResolver
import com.winhub.hernandez.perf.PerfRevertRegistry
import com.winhub.hernandez.perf.PerfRootApplier
import com.winhub.hernandez.perf.PerformanceSettings
import com.winhub.hernandez.perf.RootManager
import com.winhub.hernandez.perf.TempWatchdog
import com.winhub.hernandez.perf.galaxy.GalaxyPerfManager
import com.winhub.hernandez.perf.galaxy.GalaxyPowerProfile
import com.winhub.hernandez.ui.screens.PerfDisclaimerCopy
import com.winhub.hernandez.ui.screens.PerfDisclaimerDialog
import com.winhub.hernandez.ui.screens.PerfInfoDialog
import com.winhub.hernandez.ui.screens.WatchdogSection
import com.winhub.hernandez.ui.theme.DangerRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-game "Performance" dashboard — the single dialog that REPLACES the old inline toggle stack that
 * lived under Advanced ▸ Performance / Root Performance. It is a pure drop-in: every control binds to
 * the exact same [XServerDrawerState] flows/callbacks and perf-package objects as before, so the
 * apply + persist + per-game-override + revert behaviour is unchanged. This file only re-dresses that
 * wiring as the Form-C dashboard (live gauges → power profiles → controls) and adds:
 *   - a panel-opacity slider so it can be made translucent over a running game, and
 *   - a per-enable acknowledgement gate on the dangerous root toggles (thermal / fan), on top of the
 *     existing scroll-to-accept root-grant gate and the watchdog-off disclaimer.
 *
 * All colours come from [MaterialTheme.colorScheme] (accent = primary = #0055FF) so it tracks the app
 * theme + custom accent automatically; [DangerRed] is the shared danger colour.
 */

/** A dangerous toggle waiting on the user's acknowledgement before it flips on. */
private class PendingDanger(val label: String, val enable: () -> Unit)

@Composable
fun PerformanceDashboardDialog(state: XServerDrawerState, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val opacity by PerformanceSettings.dialogOpacity.collectAsState()

    // ── live shared state (identical sources to the old drawer block) ──
    val rootState by RootManager.state.collectAsState()
    val granted = rootState == RootManager.RootState.GRANTED
    val harnessProven by PerfRevertRegistry.harnessProven.collectAsState()
    val overridden by state.overriddenKeys.collectAsState()
    val toggles by state.rootToggles.collectAsState()
    val readouts by state.rootReadouts.collectAsState()
    val ceilingC by TempWatchdog.ceilingC.collectAsState()

    val sustained by state.sustainedPerfMode.collectAsState()
    val priority by state.perfPriorityBoost.collectAsState()
    val bigCores by state.preferBigCores.collectAsState()

    var pendingDanger by remember { mutableStateOf<PendingDanger?>(null) }
    var showGrantGate by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showGalaxyOffPrompt by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf<String?>(null) }

    // Poll the cheap sysfs/HudMetrics readouts while the dialog is open (same cadence as before).
    LaunchedEffect(Unit) {
        while (true) {
            state.onRootReadoutPoll?.run()
            delay(1500)
        }
    }

    // ── local apply helpers — thin wrappers over the SAME state APIs the old rows used ──
    fun setRoot(key: String, on: Boolean) {
        state.setRootToggle(key, on)
        state.onRootToggleChange?.accept(key, on)
    }
    fun setSustained(on: Boolean) { state.setSustainedPerfMode(on); state.onSustainedPerfModeChange?.run() }
    fun setPriority(on: Boolean) { state.setPerfPriorityBoost(on); state.onPerfPriorityBoostChange?.run() }
    fun setBig(on: Boolean) { state.setPreferBigCores(on); state.onPreferBigCoresChange?.run() }

    // Power profiles are a convenience macro over the existing toggles (no new backend). Dangerous
    // toggles are NEVER auto-enabled — those always require the explicit acknowledgement gate.
    fun applyProfile(p: String) {
        profile = p
        when (p) {
            "Off" -> {
                // Neutralise the entire root perf tier -> stock DVFS. Use this alongside Galaxy
                // Performance so the Samsung SDK is the ONLY thing touching CPU/GPU clocks (the two
                // are independent controllers and their clock/DVFS writes otherwise fight).
                setSustained(false); setPriority(false); setBig(false)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
                if (granted) {
                    setRoot(PerfRootApplier.KEY_CPU_GOVERNOR, false)
                    setRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, false)
                    setRoot(PerfRootApplier.KEY_CORES_ONLINE, false)
                }
            }
            "Battery" -> {
                setSustained(false); setPriority(false); setBig(false)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
                if (granted) {
                    setRoot(PerfRootApplier.KEY_CPU_GOVERNOR, false)
                    setRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, false)
                    setRoot(PerfRootApplier.KEY_CORES_ONLINE, false)
                }
            }
            "Balanced" -> {
                setSustained(true); setPriority(false); setBig(false)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
            }
            "Performance", "Turbo" -> {
                setSustained(true); setPriority(true); setBig(true)
                setRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, true) // no-root on Adreno, sysfs on root
                if (granted) {
                    setRoot(PerfRootApplier.KEY_CPU_GOVERNOR, true)
                    setRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, true)
                    setRoot(PerfRootApplier.KEY_CORES_ONLINE, true)
                }
            }
        }
    }

    val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Kill the platform scrim so the opacity slider genuinely reveals the game/drawer behind us.
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = maxH)
                .graphicsLayer { alpha = opacity },
            shape = RoundedCornerShape(22.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.28f)),
        ) {
            Column(Modifier.heightIn(max = maxH)) {

                // ── Header ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cs.primary.copy(alpha = 0.10f))
                        .padding(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cs.primary.copy(alpha = 0.16f))
                            .border(1.dp, cs.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("⚡", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Performance", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Applies live to this session · auto-reverts on exit",
                            color = cs.onSurfaceVariant, fontSize = 11.sp,
                        )
                    }
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = cs.onSurfaceVariant, fontSize = 15.sp) }
                }

                // ── Opacity slider (in-game translucency) ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Panel opacity", color = cs.onSurfaceVariant, fontSize = 12.sp)
                    Slider(
                        value = opacity,
                        onValueChange = { PerformanceSettings.setDialogOpacity(it) },
                        valueRange = PerformanceSettings.OPACITY_MIN..1f,
                        colors = accentSlider(cs.primary),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("${(opacity * 100).toInt()}%", color = cs.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // ── Scrolling body ──
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 14.dp),
                ) {
                    // Gauges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    ) {
                        val gpu = leadingInt(readouts["gpuMhz"])
                        val temp = leadingInt(readouts["socTemp"])
                        Gauge(Modifier.weight(1f), "GPU clock", readouts["gpuMhz"] ?: "—", "MHz",
                            frac = gpu?.let { (it / 1000f).coerceIn(0f, 1f) } ?: 0f, hot = false)
                        Gauge(Modifier.weight(1f), "SoC temp", readouts["socTemp"] ?: "—", "°C",
                            frac = temp?.let { ((it - 30f) / ((ceilingC - 30).coerceAtLeast(1))).coerceIn(0f, 1f) } ?: 0f,
                            hot = temp != null && temp >= ceilingC)
                        Gauge(Modifier.weight(1f), "Governor", readouts["governor"] ?: "—", "",
                            frac = 0f, hot = false, textOnly = true)
                    }

                    // Power profile
                    SectionHead("Power profile")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        listOf("Off", "Battery", "Balanced", "Performance", "Turbo").forEach { p ->
                            Chip(p, profile == p, Modifier.weight(1f)) { applyProfile(p) }
                        }
                    }

                    // Galaxy Performance (Samsung, no root) — only present on Samsung Galaxy devices
                    // with the Galaxy Performance SDK bundled; otherwise the whole block is hidden.
                    val galaxySupported = remember { GalaxyPerfManager.isSupported() }
                    var galaxyEnabled by remember { mutableStateOf(GalaxyPerfManager.isEnabled()) }
                    var galaxyProfileName by remember {
                        mutableStateOf(GalaxyPerfManager.currentProfile?.name ?: GalaxyPowerProfile.DEFAULT.name)
                    }
                    if (galaxySupported) {
                        SectionHead("Galaxy Performance · Samsung")
                        // Master switch — OFF by default; nothing touches the SoC until turned on.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Enable Galaxy Performance", color = cs.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "No-root CPU/GPU/RAM control via Samsung's SDK. Off by default; saved per game.",
                                    color = cs.onSurfaceVariant, fontSize = 11.sp,
                                )
                            }
                            Switch(
                                checked = galaxyEnabled,
                                onCheckedChange = {
                                    galaxyEnabled = it
                                    GalaxyPerfManager.setEnabled(it)
                                    // Recommend handing clocks to Galaxy alone, unless already Off.
                                    if (it && profile != "Off") showGalaxyOffPrompt = true
                                },
                            )
                        }
                        // Presets appear only once enabled.
                        if (galaxyEnabled) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                GalaxyPowerProfile.PRESETS.forEach { preset ->
                                    Chip(preset.name, galaxyProfileName == preset.name, Modifier.weight(1f)) {
                                        galaxyProfileName = preset.name
                                        GalaxyPerfManager.setProfile(preset)
                                    }
                                }
                            }
                            Text(
                                "Tip: set the Power profile above to \"Off\" so Galaxy alone controls the clocks (they otherwise conflict).",
                                color = cs.onSurfaceVariant, fontSize = 10.sp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
                            )
                        }
                    }

                    // Root status strip
                    RootStrip(granted, rootState) { showGrantGate = true }

                    // Quick toggles (no root)
                    SectionHead("Quick controls · no root")
                    PerfCard {
                        ToggleRowLocal(
                            "Sustained Performance Mode",
                            "Holds a stable clock to avoid thermal throttling.",
                            checked = sustained, enabled = true,
                            overridden = "sustainedPerfMode" in overridden,
                            onInfo = { info = "Sustained Performance Mode" to "Holds a stable clock so the SoC doesn't spike-and-throttle. No root required." },
                            onReset = { state.onResetPerfKey?.accept("sustainedPerfMode") },
                        ) { setSustained(it) }
                        Divider()
                        ToggleRowLocal(
                            "Thread Priority Boost",
                            "Raises emulator thread priority so the scheduler favours the game.",
                            checked = priority, enabled = true,
                            overridden = "perfPriorityBoost" in overridden,
                            onInfo = { info = "Thread Priority Boost" to "Raises the emulator's thread priority so the CPU scheduler favours the game over background work." },
                            onReset = { state.onResetPerfKey?.accept("perfPriorityBoost") },
                        ) { setPriority(it) }
                        Divider()
                        ToggleRowLocal(
                            "Prefer Big Cores",
                            "Routes emulator threads onto the big / prime cluster.",
                            checked = bigCores, enabled = true,
                            overridden = "preferBigCores" in overridden,
                            onInfo = { info = "Prefer Big Cores" to "Pins the emulator's hot threads onto the big / prime CPU cluster instead of the efficiency cores." },
                            onReset = { state.onResetPerfKey?.accept("preferBigCores") },
                        ) { setBig(it) }
                        Divider()
                        // GPU pin: usable without root on Adreno (KGSL turbo), upgrades to sysfs on root.
                        val gpuKey = PerfRootApplier.KEY_GPU_CLOCK_LOCK
                        val gpuEnabled = granted || PerfGpuTurbo.isSupported
                        ToggleRowLocal(
                            "Lock GPU to max clock",
                            if (gpuEnabled) "Adreno: pinned via /dev/kgsl-3d0 — works without root."
                            else "Needs an Adreno GPU, or root on other GPUs.",
                            checked = toggles[gpuKey] ?: false, enabled = gpuEnabled,
                            overridden = gpuKey in overridden,
                            onInfo = { info = "Lock GPU to max clock" to "Pins the GPU to its top power level. On Adreno this uses the app-owned KGSL turbo property (no root); other GPUs use the root sysfs pin." },
                            onReset = { state.onResetPerfKey?.accept(gpuKey) },
                        ) { setRoot(gpuKey, it) }
                    }

                    // Root & danger
                    SectionHead("Root & danger")
                    if (!granted) {
                        Text(
                            "🔒 " + when (rootState) {
                                RootManager.RootState.UNAVAILABLE -> "No root manager detected (Magisk / KernelSU / APatch)."
                                else -> "Grant root above to unlock CPU governor, frequency, cores, thermal & fan."
                            },
                            color = cs.onSurfaceVariant, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                    PerfCard {
                        RootToggle("CPU governor → performance", PerfRootApplier.KEY_CPU_GOVERNOR,
                            toggles, overridden, granted, harnessProven,
                            onInfo = { info = "CPU governor → performance" to "Forces every CPU cluster onto the performance governor for the session." },
                            onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_CPU_GOVERNOR) },
                            onEnableDanger = null, setRoot = { k, v -> setRoot(k, v) })
                        Divider()
                        RootToggle("Lock CPU frequency to max", PerfRootApplier.KEY_CPU_FREQ_LOCK,
                            toggles, overridden, granted, harnessProven,
                            onInfo = { info = "Lock CPU frequency to max" to "Sets scaling_min_freq = scaling_max_freq on every cluster." },
                            onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_CPU_FREQ_LOCK) },
                            onEnableDanger = null, setRoot = { k, v -> setRoot(k, v) })
                        Divider()
                        RootToggle("Keep all cores online", PerfRootApplier.KEY_CORES_ONLINE,
                            toggles, overridden, granted, harnessProven,
                            onInfo = { info = "Keep all cores online" to "Prevents the kernel from hot-plugging CPU cores offline." },
                            onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_CORES_ONLINE) },
                            onEnableDanger = null, setRoot = { k, v -> setRoot(k, v) })
                        Divider()
                        RootToggle("Disable thermal throttling", PerfRootApplier.KEY_THERMAL_DISABLE,
                            toggles, overridden, granted, harnessProven,
                            danger = true,
                            onInfo = { info = "Disable thermal throttling" to "Stops the thermal engine from clamping clocks. The temperature watchdog stays armed and can revert it." },
                            onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_THERMAL_DISABLE) },
                            onEnableDanger = { pendingDanger = PendingDanger("Disable thermal throttling") { setRoot(PerfRootApplier.KEY_THERMAL_DISABLE, true) } },
                            setRoot = { k, v -> setRoot(k, v) })
                        Divider()
                        RootToggle("Fan to maximum", PerfRootApplier.KEY_FAN_MAX,
                            toggles, overridden, granted, harnessProven,
                            danger = true,
                            onInfo = { info = "Fan to maximum" to "Forces active cooling to full speed for the session." },
                            onReset = { state.onResetPerfKey?.accept(PerfRootApplier.KEY_FAN_MAX) },
                            onEnableDanger = { pendingDanger = PendingDanger("Fan to maximum") { setRoot(PerfRootApplier.KEY_FAN_MAX, true) } },
                            setRoot = { k, v -> setRoot(k, v) })
                    }
                    if (granted && !harnessProven) {
                        Text(
                            "Thermal / fan stay locked until safety-revert is verified on this device.",
                            color = cs.onSurfaceVariant, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        )
                    }

                    // Free memory
                    if (granted) {
                        SectionHead("Free memory")
                        PerfCard {
                            ActionRowLocal("Drop file caches",
                                "Frees cached files. The system reclaims cache automatically.") { state.onFreeMemory?.run() }
                            Divider()
                            ActionRowLocal("Deep clean (free app memory)",
                                "Force-closes background apps. Won't touch your game or system.") { state.onDeepClean?.run() }
                        }
                    }

                    // Temperature watchdog (shared, identical + synced with App Settings).
                    // NB: WatchdogSection emits several sibling composables and relies on a Column
                    // parent to stack them — a Box here would overlap them.
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.padding(horizontal = 16.dp)) { WatchdogSection() }

                    // Always-on auto-revert note.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.primary.copy(alpha = 0.06f))
                            .border(1.dp, cs.primary.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text("🛡", fontSize = 14.sp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "Auto-revert on exit / background / crash — always on, can't be disabled.",
                            color = cs.onSurfaceVariant, fontSize = 12.sp,
                        )
                    }

                    // Reset all per-game overrides.
                    if (overridden.isNotEmpty()) {
                        Text(
                            "↺ Reset all game overrides to global",
                            color = cs.primary, fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth().padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { state.onResetAllPerf?.run() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    // ── acknowledgement / info dialogs ──
    pendingDanger?.let { pd ->
        PerfDisclaimerDialog(
            title = pd.label,
            body = PerfDisclaimerCopy.ROOT_RISK,
            confirmLabel = "Enable",
            onConfirm = { pd.enable(); pendingDanger = null },
            onDismiss = { pendingDanger = null },
        )
    }
    if (showGrantGate) {
        PerfDisclaimerDialog(
            title = "Power-user performance — read first",
            body = PerfDisclaimerCopy.ROOT_RISK,
            confirmLabel = "Grant Root",
            onConfirm = { showGrantGate = false; scope.launch { RootManager.requestGrant() } },
            onDismiss = { showGrantGate = false },
        )
    }
    info?.let { (t, b) -> PerfInfoDialog(t, b) { info = null } }

    if (showGalaxyOffPrompt) {
        AlertDialog(
            onDismissRequest = { showGalaxyOffPrompt = false },
            title = { Text("Let Galaxy control performance?") },
            text = {
                Text(
                    "Galaxy Performance and the Power profile above both drive the CPU/GPU clocks, " +
                        "by different mechanisms (Samsung's SDK vs. the root perf tier). Running both at " +
                        "once lets them fight, so clock behaviour can be inconsistent.\n\n" +
                        "Recommended: set the Power profile to \"Off\" so the Samsung SDK alone manages " +
                        "the clocks. Keep both only if you know what you're doing.",
                )
            },
            confirmButton = {
                TextButton(onClick = { applyProfile("Off"); showGalaxyOffPrompt = false }) {
                    Text("Set Power profile to Off")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGalaxyOffPrompt = false }) { Text("Keep both") }
            },
        )
    }
}

/* ───────────────────────── local building blocks (file-scoped) ───────────────────────── */

@Composable
private fun accentSlider(accent: Color) = SliderDefaults.colors(
    thumbColor = accent,
    activeTrackColor = accent,
    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
)

@Composable
private fun SectionHead(title: String) {
    Text(
        title.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun PerfCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    ) { Column { content() } }
}

@Composable
private fun Divider() {
    Box(
        Modifier.fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    )
}

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) cs.primary.copy(alpha = 0.18f) else cs.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, if (on) cs.primary else cs.onSurface.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) cs.primary else cs.onSurfaceVariant,
            fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Gauge(
    modifier: Modifier, label: String, value: String, unit: String,
    frac: Float, hot: Boolean, textOnly: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val bar = if (hot) DangerRed else cs.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, cs.onSurface.copy(alpha = 0.06f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), color = cs.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = if (hot) DangerRed else cs.onSurface,
                    fontSize = if (textOnly) 15.sp else 21.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (unit.isNotEmpty() && value != "—") {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, color = cs.onSurfaceVariant, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            if (!textOnly) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp))
                    .background(cs.onSurface.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(5.dp)
                        .clip(RoundedCornerShape(5.dp)).background(bar))
                }
            }
        }
    }
}

@Composable
private fun RootStrip(granted: Boolean, rootState: RootManager.RootState, onGrant: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, cs.onSurface.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50))
            .background(if (granted) Color(0xFF37C26A) else cs.onSurface.copy(alpha = 0.35f)))
        Spacer(Modifier.width(10.dp))
        Text(
            if (granted) "Root granted · full CPU/GPU control unlocked"
            else "No root · no-root controls still available",
            color = cs.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f),
        )
        if (!granted && rootState != RootManager.RootState.UNAVAILABLE) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(9.dp)).background(cs.primary)
                    .clickable { onGrant() }.padding(horizontal = 13.dp, vertical = 8.dp),
            ) {
                Text(if (rootState == RootManager.RootState.DENIED) "Grant Root (retry)" else "Grant Root",
                    color = cs.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ToggleRowLocal(
    label: String, sub: String, checked: Boolean, enabled: Boolean,
    overridden: Boolean, danger: Boolean = false,
    onInfo: () -> Unit, onReset: () -> Unit, onToggle: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (danger) { Text("⚠ ", color = DangerRed, fontSize = 13.sp) }
                    Text(label, color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.45f), fontSize = 14.sp)
                }
                Text(sub, color = cs.onSurfaceVariant, fontSize = 11.sp)
            }
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(50))
                    .border(1.dp, cs.onSurface.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .clickable { onInfo() },
                contentAlignment = Alignment.Center,
            ) { Text("?", color = cs.onSurfaceVariant, fontSize = 11.sp) }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked, onCheckedChange = onToggle, enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = cs.onPrimary,
                    checkedTrackColor = if (danger) DangerRed else cs.primary,
                ),
            )
        }
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    if (overridden) "● Per-game override" else "○ Using global default",
                    color = if (overridden) cs.primary else cs.onSurfaceVariant, fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                if (overridden) {
                    Text("Reset to global", color = cs.primary, fontSize = 10.sp,
                        modifier = Modifier.clickable { onReset() })
                }
            }
        }
    }
}

/** Root-tier row: gates on grant + (for dangerous keys) the harness, and routes enable through the
 *  acknowledgement gate when [onEnableDanger] is supplied. */
@Composable
private fun RootToggle(
    label: String, key: String,
    toggles: Map<String, Boolean>, overridden: Set<String>,
    granted: Boolean, harnessProven: Boolean, danger: Boolean = false,
    onInfo: () -> Unit, onReset: () -> Unit,
    onEnableDanger: (() -> Unit)?, setRoot: (String, Boolean) -> Unit,
) {
    val gated = PerfRootApplier.isHarnessGated(key) && !harnessProven
    val enabled = granted && !gated
    ToggleRowLocal(
        label = label,
        sub = if (danger) "Root · dangerous — auto-reverts on exit." else "Root control.",
        checked = toggles[key] ?: false, enabled = enabled,
        overridden = key in overridden, danger = danger,
        onInfo = onInfo, onReset = onReset,
    ) { on ->
        if (on && onEnableDanger != null) onEnableDanger() // acknowledgement gate for thermal/fan
        else setRoot(key, on)
    }
}

@Composable
private fun ActionRowLocal(label: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = cs.onSurface, fontSize = 14.sp)
            Text(sub, color = cs.onSurfaceVariant, fontSize = 11.sp)
        }
        Box(
            Modifier.clip(RoundedCornerShape(9.dp)).background(cs.primary)
                .padding(horizontal = 13.dp, vertical = 7.dp),
        ) { Text("Run", color = cs.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** Parse a leading integer out of a readout like "650", "650 MHz", "47°C". Null when none. */
private fun leadingInt(s: String?): Int? =
    s?.trim()?.takeWhile { it.isDigit() }?.takeIf { it.isNotEmpty() }?.toIntOrNull()

/* ═══════════════════ Per-game (pre-launch) variant ═══════════════════ */

/**
 * Pre-launch variant used by the per-game shortcut editor. Same dashboard menu as the in-game
 * [PerformanceDashboardDialog], but bound to the editor's LOCAL pending state (setters below) instead
 * of the live XServerDrawerState — the editor persists these as shortcut extras on OK, override-when-
 * different. Differences from in-game: root toggles are settable regardless of the CURRENT grant (they
 * are saved presets honored at launch), and there is no opacity slider / free-memory / watchdog
 * (those are runtime-only concerns). Gauges show current DEVICE state via a direct HudMetrics poll.
 */
@Composable
fun PerformanceDashboardDialogPerGame(
    sustained: Boolean, onSustained: (Boolean) -> Unit,
    priority: Boolean, onPriority: (Boolean) -> Unit,
    bigCores: Boolean, onBigCores: (Boolean) -> Unit,
    rootValue: (String) -> Boolean, onRoot: (String, Boolean) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val rootState by RootManager.state.collectAsState()
    val granted = rootState == RootManager.RootState.GRANTED
    val ceilingC by TempWatchdog.ceilingC.collectAsState()
    val readouts = rememberLiveReadouts()

    var pendingDanger by remember { mutableStateOf<PendingDanger?>(null) }
    var showGrantGate by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var profile by remember { mutableStateOf<String?>(null) }

    fun globalOf(key: String): Boolean = when (key) {
        "sustainedPerfMode" -> PerformanceSettings.sustainedPerfMode.value
        "perfPriorityBoost" -> PerformanceSettings.perfPriorityBoost.value
        "preferBigCores" -> PerformanceSettings.preferBigCores.value
        else -> PerformanceSettings.rootDefaultValue(key)
    }

    fun applyProfile(p: String) {
        profile = p
        when (p) {
            "Off" -> {
                // Neutralise the root perf tier -> stock DVFS, so Galaxy Performance (Samsung SDK)
                // can own CPU/GPU clocks without the two controllers fighting.
                onSustained(false); onPriority(false); onBigCores(false)
                onRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
                onRoot(PerfRootApplier.KEY_CPU_GOVERNOR, false)
                onRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, false)
                onRoot(PerfRootApplier.KEY_CORES_ONLINE, false)
            }
            "Battery" -> {
                onSustained(false); onPriority(false); onBigCores(false)
                onRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
                onRoot(PerfRootApplier.KEY_CPU_GOVERNOR, false)
                onRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, false)
                onRoot(PerfRootApplier.KEY_CORES_ONLINE, false)
            }
            "Balanced" -> {
                onSustained(true); onPriority(false); onBigCores(false)
                onRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, false)
            }
            "Performance", "Turbo" -> {
                onSustained(true); onPriority(true); onBigCores(true)
                onRoot(PerfRootApplier.KEY_GPU_CLOCK_LOCK, true)
                onRoot(PerfRootApplier.KEY_CPU_GOVERNOR, true)
                onRoot(PerfRootApplier.KEY_CPU_FREQ_LOCK, true)
                onRoot(PerfRootApplier.KEY_CORES_ONLINE, true)
            }
        }
    }

    val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = maxH),
            shape = RoundedCornerShape(22.dp), color = cs.surface,
            border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.28f)),
        ) {
            Column(Modifier.heightIn(max = maxH)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(cs.primary.copy(alpha = 0.10f)).padding(16.dp),
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            .background(cs.primary.copy(alpha = 0.16f))
                            .border(1.dp, cs.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("⚡", fontSize = 17.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Performance", color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Per-game preset · saved on OK · honored at launch",
                            color = cs.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = cs.onSurfaceVariant, fontSize = 15.sp) }
                }

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 14.dp),
                ) {
                    // Gauges (current device state)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    ) {
                        val gpu = leadingInt(readouts["gpuMhz"])
                        val temp = leadingInt(readouts["socTemp"])
                        Gauge(Modifier.weight(1f), "GPU clock", readouts["gpuMhz"] ?: "—", "MHz",
                            frac = gpu?.let { (it / 1000f).coerceIn(0f, 1f) } ?: 0f, hot = false)
                        Gauge(Modifier.weight(1f), "SoC temp", readouts["socTemp"] ?: "—", "°C",
                            frac = temp?.let { ((it - 30f) / ((ceilingC - 30).coerceAtLeast(1))).coerceIn(0f, 1f) } ?: 0f,
                            hot = temp != null && temp >= ceilingC)
                        Gauge(Modifier.weight(1f), "Governor", readouts["governor"] ?: "—", "",
                            frac = 0f, hot = false, textOnly = true)
                    }

                    // Power profile
                    SectionHead("Power profile")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        listOf("Off", "Battery", "Balanced", "Performance", "Turbo").forEach { p ->
                            Chip(p, profile == p, Modifier.weight(1f)) { applyProfile(p) }
                        }
                    }

                    RootStrip(granted, rootState) { showGrantGate = true }

                    // Quick controls (no root)
                    SectionHead("Quick controls · no root")
                    PerfCard {
                        ToggleRowLocal("Sustained Performance Mode",
                            "Holds a stable clock to avoid thermal throttling.",
                            checked = sustained, enabled = true, overridden = sustained != globalOf("sustainedPerfMode"),
                            onInfo = { info = "Sustained Performance Mode" to "Holds a stable clock so the SoC doesn't spike-and-throttle. No root required." },
                            onReset = { onSustained(globalOf("sustainedPerfMode")) }, onToggle = onSustained)
                        Divider()
                        ToggleRowLocal("Thread Priority Boost",
                            "Raises emulator thread priority so the scheduler favours the game.",
                            checked = priority, enabled = true, overridden = priority != globalOf("perfPriorityBoost"),
                            onInfo = { info = "Thread Priority Boost" to "Raises the emulator's thread priority so the CPU scheduler favours the game." },
                            onReset = { onPriority(globalOf("perfPriorityBoost")) }, onToggle = onPriority)
                        Divider()
                        ToggleRowLocal("Prefer Big Cores",
                            "Routes emulator threads onto the big / prime cluster.",
                            checked = bigCores, enabled = true, overridden = bigCores != globalOf("preferBigCores"),
                            onInfo = { info = "Prefer Big Cores" to "Pins the emulator's hot threads onto the big / prime CPU cluster." },
                            onReset = { onBigCores(globalOf("preferBigCores")) }, onToggle = onBigCores)
                        Divider()
                        val gk = PerfRootApplier.KEY_GPU_CLOCK_LOCK
                        ToggleRowLocal("Lock GPU to max clock",
                            "Adreno: no-root KGSL turbo; sysfs pin with root.",
                            checked = rootValue(gk), enabled = true, overridden = rootValue(gk) != globalOf(gk),
                            onInfo = { info = "Lock GPU to max clock" to "Pins the GPU to its top power level (Adreno no-root; other GPUs need root)." },
                            onReset = { onRoot(gk, globalOf(gk)) }, onToggle = { onRoot(gk, it) })
                    }

                    // Root & danger — settable as presets regardless of the current grant.
                    SectionHead("Root & danger")
                    Text("Saved for this game · applied at launch when root is available.",
                        color = cs.onSurfaceVariant, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                    PerfCard {
                        rootPresetRow("CPU governor → performance", PerfRootApplier.KEY_CPU_GOVERNOR,
                            "Forces every cluster to the performance governor.", rootValue, onRoot, { k -> globalOf(k) },
                            setInfo = { info = it }, danger = false, arm = null)
                        Divider()
                        rootPresetRow("Lock CPU frequency to max", PerfRootApplier.KEY_CPU_FREQ_LOCK,
                            "scaling_min_freq = scaling_max_freq on every cluster.", rootValue, onRoot, { k -> globalOf(k) },
                            setInfo = { info = it }, danger = false, arm = null)
                        Divider()
                        rootPresetRow("Keep all cores online", PerfRootApplier.KEY_CORES_ONLINE,
                            "Prevents the kernel from hot-plugging cores offline.", rootValue, onRoot, { k -> globalOf(k) },
                            setInfo = { info = it }, danger = false, arm = null)
                        Divider()
                        rootPresetRow("Disable thermal throttling", PerfRootApplier.KEY_THERMAL_DISABLE,
                            "Runtime watchdog still guards.", rootValue, onRoot, { k -> globalOf(k) },
                            setInfo = { info = it }, danger = true,
                            arm = { pendingDanger = PendingDanger("Disable thermal throttling") { onRoot(PerfRootApplier.KEY_THERMAL_DISABLE, true) } })
                        Divider()
                        rootPresetRow("Fan to maximum", PerfRootApplier.KEY_FAN_MAX,
                            "Forces active cooling to full at launch.", rootValue, onRoot, { k -> globalOf(k) },
                            setInfo = { info = it }, danger = true,
                            arm = { pendingDanger = PendingDanger("Fan to maximum") { onRoot(PerfRootApplier.KEY_FAN_MAX, true) } })
                    }

                    val anyOverride = sustained != globalOf("sustainedPerfMode") ||
                        priority != globalOf("perfPriorityBoost") ||
                        bigCores != globalOf("preferBigCores") ||
                        PerfRootApplier.ROOT_KEYS.any { rootValue(it) != globalOf(it) }
                    if (anyOverride) {
                        Text("↺ Reset all performance toggles to global", color = cs.primary, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(10.dp)).clickable { onResetAll() }
                                .padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }

    pendingDanger?.let { pd ->
        PerfDisclaimerDialog(title = pd.label, body = PerfDisclaimerCopy.ROOT_RISK, confirmLabel = "Enable",
            onConfirm = { pd.enable(); pendingDanger = null }, onDismiss = { pendingDanger = null })
    }
    if (showGrantGate) {
        PerfDisclaimerDialog(title = "Power-user performance — read first", body = PerfDisclaimerCopy.ROOT_RISK,
            confirmLabel = "Grant Root", onConfirm = { showGrantGate = false; scope.launch { RootManager.requestGrant() } },
            onDismiss = { showGrantGate = false })
    }
    info?.let { (t, b) -> PerfInfoDialog(t, b) { info = null } }
}

/** One root-tier preset row: enabled regardless of grant; dangerous keys route enable through [arm]. */
@Composable
private fun rootPresetRow(
    label: String, key: String, sub: String,
    rootValue: (String) -> Boolean, onRoot: (String, Boolean) -> Unit, globalOf: (String) -> Boolean,
    setInfo: (Pair<String, String>) -> Unit, danger: Boolean, arm: (() -> Unit)?,
) {
    ToggleRowLocal(label, sub, checked = rootValue(key), enabled = true,
        overridden = rootValue(key) != globalOf(key), danger = danger,
        onInfo = { setInfo(label to sub) },
        onReset = { onRoot(key, globalOf(key)) },
    ) { on -> if (on && arm != null) arm() else onRoot(key, on) }
}

/** Poll current device GPU MHz / SoC temp / governor (~1.5s) for the pre-launch gauges. */
@Composable
internal fun rememberLiveReadouts(): Map<String, String> {
    val ctx = LocalContext.current
    val hud = remember { com.winhub.hernandez.widget.HudMetrics(ctx) }
    var readouts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        while (true) {
            val m = HashMap<String, String>()
            m["gpuMhz"] = hud.gpuClockMhz?.toString() ?: "—"
            val cpuT = hud.cpuTempC
            val gpuT = hud.gpuTempC
            val soc = if (cpuT != null && gpuT != null) maxOf(cpuT, gpuT) else (cpuT ?: gpuT)
            m["socTemp"] = soc?.toString() ?: "—"
            m["governor"] = readGovernor() ?: "—"
            readouts = m
            delay(1500)
        }
    }
    return readouts
}

internal fun readGovernor(): String? {
    for (c in PerfNodeResolver.cpuCores()) {
        val g = c.governor ?: continue
        try {
            val v = java.io.File(g).readText().trim()
            if (v.isNotEmpty()) return v
        } catch (_: Exception) { /* unreadable core — try the next */ }
    }
    return null
}
