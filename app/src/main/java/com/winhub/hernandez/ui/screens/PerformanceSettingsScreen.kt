package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.HelpOutline
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.perf.PerfGpuTurbo
import com.winhub.hernandez.perf.PerfRootApplier
import com.winhub.hernandez.perf.PerformanceSettings
import com.winhub.hernandez.perf.RootManager
import kotlinx.coroutines.launch

/**
 * App Settings → Performance menu. Binds the GLOBAL DEFAULTS (non-root three + root six) to the same
 * [PerformanceSettings] flows the in-game drawer reads, so a change here is reflected live in the
 * other surface (two-way sync via one store).
 *
 * Root tier: a real grant gate (scroll-to-bottom + accept disclaimer -> [RootManager.requestGrant]),
 * then the root toggles applied live through [PerfRootApplier] (snapshot-before-write; reverted on
 * exit/background/crash). The two dangerous toggles (thermal disable, fan max) stay disabled until the
 * safety harness is proven. The temperature watchdog is device-wide; turning it OFF requires its own
 * hard disclaimer.
 */
@Composable
fun PerformanceSettingsScreen(onClose: () -> Unit) {
    val sustained by PerformanceSettings.sustainedPerfMode.collectAsState()
    val priority by PerformanceSettings.perfPriorityBoost.collectAsState()
    val bigCores by PerformanceSettings.preferBigCores.collectAsState()
    val rootState by PerformanceSettings.rootState.collectAsState()
    val harnessProven by PerformanceSettings.harnessProven.collectAsState()
    // "Auto deep-clean on launch" (Tier 2) global default — persisted via the same root-default store.
    val autoDeepClean by PerformanceSettings.rootDefaultFlow(PerfRootApplier.KEY_AUTO_DEEP_CLEAN).collectAsState()

    val scope = rememberCoroutineScope()
    var showRootDisclaimer by remember { mutableStateOf(false) }
    // Per-toggle "?" info dialog: (title, body). And the consolidated "Explain toggles" sheet.
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showExplainAll by remember { mutableStateOf(false) }

    val granted = rootState == RootManager.RootState.GRANTED

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Performance",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Text(
                "Global defaults apply to every game unless a game sets its own override " +
                    "(in the game's settings or from the in-game menu).",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
            )

            // ── Non-root global defaults (always editable) ──
            PerfCard(title = "Global defaults") {
                PerfToggle("Sustained Performance Mode", sustained,
                    onInfo = { info = "Sustained Performance Mode" to PerfCopy.SUSTAINED }) { PerformanceSettings.setSustainedPerfMode(it) }
                PerfToggle("Thread Priority Boost", priority,
                    onInfo = { info = "Thread Priority Boost" to PerfCopy.PRIORITY }) { PerformanceSettings.setPerfPriorityBoost(it) }
                PerfToggle("Prefer Big Cores", bigCores,
                    onInfo = { info = "Prefer Big Cores" to PerfCopy.BIG_CORES }) { PerformanceSettings.setPreferBigCores(it) }
                // GPU pin lives here (not in the root card) because it now has a non-root path on
                // Adreno. It still upgrades itself to the stronger sysfs pin when root is granted.
                RootToggle(PerfRootApplier.KEY_GPU_CLOCK_LOCK, "Lock GPU to max clock", granted, harnessProven,
                    onInfo = { info = "Lock GPU to max clock" to PerfCopy.GPU_CLOCK })
                if (!granted && !PerfGpuTurbo.isSupported) {
                    Text("Lock GPU to max clock needs an Adreno (Qualcomm) GPU, or root on other GPUs.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }

            // ── Root tier ──
            PerfCard(title = "Root performance controls") {
                Text("Root status: " + rootStateLabel(rootState),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                when (rootState) {
                    RootManager.RootState.UNAVAILABLE -> {
                        Text("No root manager detected (Magisk / KernelSU / APatch). Root controls stay disabled.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    RootManager.RootState.GRANTED -> { /* toggles below are enabled */ }
                    else -> {
                        // AVAILABLE_NOT_GRANTED or DENIED -> offer (or re-offer) the grant.
                        val label = if (rootState == RootManager.RootState.DENIED) "Grant Root (retry)" else "Grant Root"
                        Button(onClick = { showRootDisclaimer = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                RootToggle(PerfRootApplier.KEY_CPU_GOVERNOR, "CPU governor → performance", granted, harnessProven,
                    onInfo = { info = "CPU governor → performance" to PerfCopy.CPU_GOV })
                RootToggle(PerfRootApplier.KEY_CPU_FREQ_LOCK, "Lock CPU frequency to max", granted, harnessProven,
                    onInfo = { info = "Lock CPU frequency to max" to PerfCopy.CPU_FREQ })
                RootToggle(PerfRootApplier.KEY_CORES_ONLINE, "Keep all cores online", granted, harnessProven,
                    onInfo = { info = "Keep all cores online" to PerfCopy.CORES_ONLINE })
                RootToggle(PerfRootApplier.KEY_THERMAL_DISABLE, "Disable thermal throttling", granted, harnessProven,
                    onInfo = { info = "Disable thermal throttling" to PerfCopy.THERMAL })
                RootToggle(PerfRootApplier.KEY_FAN_MAX, "Fan to maximum", granted, harnessProven,
                    onInfo = { info = "Fan to maximum" to PerfCopy.FAN })

                if (granted && !harnessProven) {
                    Text("Thermal / fan controls are locked until safety-revert is verified on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }

                // ── Free memory (dual tier). Section "?" explains both tiers + the auto toggle. ──
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Free memory", color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    InfoButton { info = "Free memory" to PerfCopy.FREE_MEM_SECTION }
                }

                // TIER 1 — drop file caches (light; near-invisible RAM by design). Root-gated as before.
                Button(
                    onClick = { PerfRootApplier.freeMemoryNow() },
                    enabled = granted,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Drop file caches", color = MaterialTheme.colorScheme.onPrimary) }
                Text("Frees cached files. Little visible RAM; the system reclaims cache automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                // TIER 2 — deep clean (root-only; the real RAM free via `am kill-all`).
                Button(
                    onClick = { PerfRootApplier.deepCleanMemory() },
                    enabled = granted,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Deep clean (free app memory)", color = MaterialTheme.colorScheme.onPrimary) }
                Text("Force-closes background apps to free real memory. Won't touch your game or system.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                // AUTO toggle — persists like the other root defaults; fires at game launch.
                PerfToggle("Auto deep-clean on launch", autoDeepClean, enabled = granted,
                    onInfo = { info = "Auto deep-clean on launch" to PerfCopy.AUTO_DEEP_CLEAN }) {
                    PerformanceSettings.setRootDefault(PerfRootApplier.KEY_AUTO_DEEP_CLEAN, it)
                }
            }

            // ── Temperature watchdog (device-wide; not root-gated). Shared control block, identical
            // and synced with the in-game surface (both bind the one TempWatchdog singleton). ──
            PerfCard(title = "Temperature watchdog") {
                Text(
                    "Auto-reverts all performance settings before the device gets too hot, anchored to " +
                        "your device's own thermal trip points. Keep this on unless you know what you're doing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
                WatchdogSection()
            }

            Text(
                "Auto-revert on game exit, app background and crash is always on and cannot be disabled.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp
            )

            Button(onClick = { showExplainAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Explain toggles", color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    info?.let { (title, body) ->
        PerfInfoDialog(title = title, body = body, onDismiss = { info = null })
    }

    if (showExplainAll) {
        PerfInfoDialog(title = "What the toggles do", body = explainAllBody(), onDismiss = { showExplainAll = false })
    }

    if (showRootDisclaimer) {
        PerfDisclaimerDialog(
            title = "Power-user performance — read first",
            body = PerfDisclaimerCopy.ROOT_RISK,
            confirmLabel = "Grant Root",
            onDismiss = { showRootDisclaimer = false },
            onConfirm = {
                showRootDisclaimer = false
                scope.launch { RootManager.requestGrant() } // fires the su prompt; state updates live
            }
        )
    }
}

/** A PerfRootApplier-owned toggle bound to its global default; applies live via PerfRootApplier. */
@Composable
private fun RootToggle(key: String, label: String, granted: Boolean, harnessProven: Boolean, onInfo: () -> Unit) {
    val checked by PerformanceSettings.rootDefaultFlow(key).collectAsState()
    val gated = PerfRootApplier.isHarnessGated(key) && !harnessProven
    // The GPU pin is usable without root on Adreno (KGSL turbo); everything else needs the grant.
    val usableWithoutRoot = key == PerfRootApplier.KEY_GPU_CLOCK_LOCK && PerfGpuTurbo.isSupported
    val enabled = (granted || usableWithoutRoot) && !gated
    PerfToggle(label, checked, enabled = enabled, onInfo = onInfo) { on ->
        PerformanceSettings.setRootDefault(key, on)
        PerfRootApplier.apply(key, on)
    }
}

private fun rootStateLabel(state: RootManager.RootState): String = when (state) {
    RootManager.RootState.UNKNOWN -> "checking…"
    RootManager.RootState.UNAVAILABLE -> "not available on this device"
    RootManager.RootState.AVAILABLE_NOT_GRANTED -> "available (not granted)"
    RootManager.RootState.GRANTED -> "granted"
    RootManager.RootState.DENIED -> "denied"
}

@Composable
private fun PerfCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun PerfToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onInfo: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // Only the label + switch dim/gate on `enabled`; the "?" stays live so a locked toggle is
        // still explainable.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier.alpha(0.4f))
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
        if (onInfo != null) InfoButton(onInfo)
        Spacer(Modifier.width(4.dp))
        Row(modifier = if (enabled) Modifier else Modifier.alpha(0.4f)) {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

/** Small "?" affordance that opens a soft info dialog. Always live (even for disabled toggles). */
@Composable
private fun InfoButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(Icons.Outlined.HelpOutline, "What's this?",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

private object PerfCopy {
    const val SUSTAINED = "Keeps clock speeds steady instead of spiking and dropping, so long sessions stay smoother. Safe, no root."
    const val PRIORITY = "Gives the game's rendering higher priority than other apps for more CPU time. Safe, no root."
    const val BIG_CORES = "Pins the game to your fastest CPU cores instead of the efficiency cores. Safe, no root."
    const val CPU_GOV = "Forces the CPU to run fast instead of ramping on demand. Faster, more heat and battery. (Root)"
    const val CPU_FREQ = "Pins the CPU at max clock so it never slows down. Top performance, highest heat/battery. (Root)"
    const val CORES_ONLINE = "Stops cores being put to sleep, keeping every core available. (Root)"
    const val GPU_CLOCK =
        "Pins the GPU at top speed so it stops dropping clocks to save power. Smoother and more " +
        "consistent, but hotter and heavier on battery.\n\n" +
        "No root needed on Adreno (Qualcomm) GPUs — this is the same \"Adreno turbo\" trick Switch " +
        "emulators use: it asks the GPU driver directly to stop scaling clocks down, through a device " +
        "file the app is already allowed to open.\n\n" +
        "With root granted, it switches to a stronger system-level clock pin instead. Either way your " +
        "device's own heat protection still applies, and the setting is undone when you exit the game, " +
        "background the app, or it crashes.\n\n" +
        "On non-Adreno GPUs (Mali, Xclipse, PowerVR) this toggle needs root."
    const val THERMAL = "Removes your device's built-in heat protection. ⚠️ Can overheat — the Temperature Watchdog is your only safety net with this on. (Root)"
    const val FAN = "Runs the fan at full speed for max cooling (devices with a fan). (Root)"
    const val FREE_MEM_SECTION =
        "Two ways to free memory, from lightest to strongest.\n\n" +
        "Drop file caches (Tier 1)\n" +
        "Writes to the kernel's drop-caches control to release cached files. This is light and " +
        "near-invisible by design — the system refills the cache almost immediately, so you'll rarely " +
        "see the free-RAM number move. It does NOT close any apps.\n\n" +
        "Deep clean (Tier 2)\n" +
        "The real RAM free. Force-closes background apps to reclaim memory. It uses Android's own " +
        "\"kill background\" command (am kill-all), which only touches apps the system considers safe " +
        "to kill — it will NOT close your running game, this app, or system/persistent apps. This is " +
        "the same kind of thing a phone's built-in \"RAM cleaner\" / Game Turbo does.\n\n" +
        "Auto deep-clean on launch\n" +
        "When on, a deep clean runs automatically each time you start a game or container, so it opens " +
        "with the most free RAM available.\n\n" +
        "Root is required for deep clean (and for dropping caches)."
    const val AUTO_DEEP_CLEAN =
        "Runs a deep clean automatically every time a game/container launches — force-closes safe " +
        "background apps (via am kill-all, never your game or system) to free real RAM before you start. (Root)"
}

/** Consolidated explainer body, grouped by root requirement. */
private fun explainAllBody(): String = buildString {
    append("No root needed\n\n")
    append("• Sustained Performance Mode\n${PerfCopy.SUSTAINED}\n\n")
    append("• Thread Priority Boost\n${PerfCopy.PRIORITY}\n\n")
    append("• Prefer Big Cores\n${PerfCopy.BIG_CORES}\n\n")
    append("• Lock GPU to max clock\n${PerfCopy.GPU_CLOCK}\n\n")
    append("\nRequires root\n\n")
    append("• CPU governor → performance\n${PerfCopy.CPU_GOV}\n\n")
    append("• Lock CPU frequency to max\n${PerfCopy.CPU_FREQ}\n\n")
    append("• Keep all cores online\n${PerfCopy.CORES_ONLINE}\n\n")
    append("• Disable thermal throttling\n${PerfCopy.THERMAL}\n\n")
    append("• Fan to maximum\n${PerfCopy.FAN}\n\n")
    append("• Free memory\n${PerfCopy.FREE_MEM_SECTION}\n")
}
