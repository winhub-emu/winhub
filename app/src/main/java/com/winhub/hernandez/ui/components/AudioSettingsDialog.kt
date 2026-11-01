package com.winhub.hernandez.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.screens.HelpDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Shared audio config used by the editor cog (per-container / per-game) and the in-game side menu.
 * Fixes crackling (AAudio buffer underruns) with an adaptive, self-sizing buffer, plus manual presets
 * and fine-tuning. Sink-side fields (perfMode/adaptive/buffer) apply LIVE in-game via sink recreate;
 * latencyMsec is the guest winepulse buffer, fixed at connect → applies next launch.
 *
 * ── CONFIG MODEL & NO-BLEED CONTRACT (read before changing any audio-config path) ──────────────────
 * Strict hierarchy, isolated on THREE axes at once — engine (ALSA≠Pulse), scope (container≠shortcut≠
 * other games), and store role (persistent≠runtime). Nothing bleeds in any direction. Two stores:
 *
 *   1. PER-SCOPE ENV — engine-scoped keys BANNER_AUDIO_ALSA_* / BANNER_AUDIO_PULSE_* in a container's or
 *      a shortcut's env. This is the ONLY PERSISTENT store. [audioConfigToEnv] writes just THIS engine's
 *      prefix (leaves the other engine + non-audio env intact); [audioConfigFromEnv] reads it with an
 *      engine-aware default. Set by the editor cog AND by in-game saves (which the activity writes into
 *      the launching SHORTCUT's env only — never the container, never another game). Shortcut overrides
 *      container by normal env-merge precedence.
 *
 *   2. PER-ENGINE PREFS — [audioPrefsName]: "banner_audio_alsa" / "banner_audio_pulseaudio". EPHEMERAL
 *      runtime only: the engine reads it while a game runs (ALSA: applyAlsaAudioConfig; Pulse:
 *      resolveSinkArgs) and the in-game tab reads/writes it live. XServerDisplayActivity reseeds it IN
 *      FULL every launch from store #1 (resolved shortcut→container→engine-default), so it holds NO
 *      cross-launch memory — persistence lives only in #1, per scope.
 *
 * INVARIANTS — do not break:
 *   • Engine keys/files are ALWAYS engine-scoped (BANNER_AUDIO_<ENG>_* / banner_audio_<engine>). Never a
 *     bare shared "banner_audio" / "BANNER_AUDIO_*", or the two engines start sharing state again.
 *   • The runtime prefs (#2) is reseeded every launch and must never be treated as persistence.
 *   • In-game saves persist to the launching SHORTCUT's env only (per-game); never the container/others.
 *   • Any pre-config DEFAULT is engine-aware (ALSA→NONE/"stable", Pulse→"auto"), matching across
 *     [audioConfigFromEnv], [loadAudioConfig] and seedAudioPrefsForLaunch.
 */
data class AudioConfig(
    val preset: String = PRESET_AUTO,
    val perfMode: Int = 1,          // 0=NONE, 1=LOW_LATENCY, 2=POWER_SAVING
    val adaptive: Boolean = true,
    val bufferFrames: Int = 0,      // 0 = auto (framesPerBurst*2)
    val maxBufferFrames: Int = 0,   // 0 = device capacity
    val latencyMsec: Int = 100      // guest winepulse buffer
) {
    /** module-aaudio-sink argument string (matches PulseAudioComponent.resolveSinkArgs). */
    fun toSinkArgs(): String {
        val sb = StringBuilder("performance_mode=$perfMode adaptive=${if (adaptive) 1 else 0}")
        if (bufferFrames > 0) sb.append(" buffer_frames=$bufferFrames")
        if (maxBufferFrames > 0) sb.append(" max_buffer_frames=$maxBufferFrames")
        return sb.toString()
    }
}

const val PRESET_AUTO = "auto"
const val PRESET_LOW = "low"
const val PRESET_BALANCED = "balanced"
const val PRESET_STABLE = "stable"
const val PRESET_CUSTOM = "custom"
const val AUDIO_PREFS = "banner_audio"

data class AudioPreset(
    val id: String, val emoji: String, val name: String, val badge: String?, val desc: String,
    val cfg: AudioConfig, val pos: Float, val note: String,
    /** Long-form "?" help, shown in the app's standard HelpDialog. */
    val helpRes: Int
)

val AUDIO_PRESETS = listOf(
    AudioPreset(PRESET_AUTO, "✨", "Auto / Smart", "Recommended",
        "Starts low-latency, grows the buffer only if it hears crackle.",
        AudioConfig(PRESET_AUTO, 1, true, 0, 0, 100), 0.46f,
        "Aims for the lowest delay your device can hold without crackling.", R.string.help_audio_preset_auto),
    AudioPreset(PRESET_LOW, "⚡", "Low latency", null,
        "Tightest sync. May crackle under heavy load.",
        // adaptive TRUE. This rung pinned the buffer with no way to grow, so a title that
        // could not hold it crackled for the whole session with nothing to recover it -
        // the one genuinely dangerous cell in the preset matrix, and on every engine, not
        // just DirectAudio. A buffer that grows on underrun is strictly safer than one
        // that cannot, so this can only add recovery where there was none. Turning
        // adaptation off is still reachable, from Custom or the engine's ADAPTIVE env key,
        // which is where a deliberate choice belongs - not hidden inside a preset named
        // for its latency.
        AudioConfig(PRESET_LOW, 1, true, 0, 0, 40), 0.10f,
        "Minimal delay; switch to Auto if heavy scenes crackle.", R.string.help_audio_preset_low),
    AudioPreset(PRESET_BALANCED, "⚖️", "Balanced", null,
        "Calmer buffer with an adaptive safety net.",
        AudioConfig(PRESET_BALANCED, 2, true, 0, 0, 100), 0.60f,
        "Smooth all-rounder for most games.", R.string.help_audio_preset_balanced),
    AudioPreset(PRESET_STABLE, "🛡️", "Stable (no crackle)", null,
        "Biggest safety margin for weak devices / demanding games.",
        AudioConfig(PRESET_STABLE, 0, true, 0, 0, 144), 0.90f,
        "Maximum crackle protection; most audio delay.", R.string.help_audio_preset_stable),
    AudioPreset(PRESET_CUSTOM, "🎛️", "Custom", "Fine-tune",
        "Set every knob yourself.",
        AudioConfig(PRESET_CUSTOM, 1, true, 0, 0, 100), 0.46f,
        "Manual control of every buffer knob.", R.string.help_audio_preset_custom)
)

/* ---- persistence: PER-ENGINE prefs (each engine remembers its own; no cross-engine bleed) ---- */
/** Prefs file for an engine. ALSA and PulseAudio get separate files so their settings never mix. */
fun audioPrefsName(driverId: String): String = when (driverId) {
    "alsa" -> "${AUDIO_PREFS}_alsa"
    "directaudio" -> "${AUDIO_PREFS}_directaudio"
    else -> "${AUDIO_PREFS}_pulseaudio"
}

/** Engines that default to AAudio PERFORMANCE_MODE_NONE (proven crackle-free): ALSA and DirectAudio,
 *  both of which drive AAudio directly. PulseAudio defaults to LOW_LATENCY/Auto. */
private fun engineNoneDefault(driverId: String) = driverId == "alsa" || driverId == "directaudio"

/**
 * The preset a scope starts on. ALSA keeps Stable - its own floor is unmeasured and it
 * reaches Android through an extra server hop. DirectAudio moves to Auto: its Stable rung
 * is 83 ms of total latency, and 33 ms was measured holding through seven minutes of
 * gameplay with a single underrun, so starting on Stable spent 50 ms to protect against
 * something no tested title does.
 */
fun defaultPresetFor(driverId: String): String =
    if (driverId == "alsa") PRESET_STABLE else PRESET_AUTO

/** DirectAudio's shipping buffer, in ms (DA_DEFAULT_MS in winedirectaudio.drv). ~33 ms to the ear
 *  once Android's fixed ~21 ms output latency is added. */
const val DIRECT_DEFAULT_MS = 12

/** Android's own mixer + HAL output latency, measured at 21 ms on the reference device. Every app
 *  pays it, so it is added when showing a total the user can compare against what they hear. */
const val ANDROID_OUTPUT_MS = 21

/**
 * Default for the latency field, which means different things per engine. For PulseAudio it is the
 * guest winepulse buffer (PULSE_LATENCY_MSEC, 100 ms). For DirectAudio it is the driver's own buffer
 * target, and 100 ms there would be eight times the driver's default - so the two cannot share a
 * number. ALSA ignores the field entirely.
 */
fun defaultLatencyMsec(driverId: String): Int =
    if (driverId == "directaudio") DIRECT_DEFAULT_MS else 100

/** True where the latency field drives the DirectAudio buffer (BANNER_AUDIO_DIRECT_MS). */
fun latencyIsDirectBuffer(driverId: String) = driverId == "directaudio"

/**
 * What DirectAudio ACTUALLY applies for a named preset.
 *
 * The generic preset objects below describe the PulseAudio sink; DirectAudio maps the same names to
 * its own buffer sizes and always forces LOW_LATENCY (device-proven: NONE runs a normal-priority
 * thread the guest preempts under box64/FEX, which is audibly choppy). This is the single source of
 * truth for that mapping - the dialog reads it to show the greyed fine-tune rows, and
 * XServerDisplayActivity.applyDirectAudioConfig reads it to build the launch environment, so what is
 * on screen cannot drift from what is used.
 *
 * latencyMsec is the ms equivalent of the buffer (frames / 48), so the latency row reads correctly
 * for a preset even though a preset applies its buffer in frames.
 */
fun directPresetConfig(preset: String): AudioConfig = when (preset) {
    // perf is 1 (LOW_LATENCY) in every case, deliberately - see above.
    // The ladder is device-measured (DiRT 3, FNAF Security Breach, DRAGON QUEST VII on
    // an Adreno 750). Totals in the comments are what a player hears: the buffer plus
    // Android's fixed ~21 ms mixer/HAL cost, which nothing can go under.
    //
    // Low is one hardware burst - the floor - and now runs ADAPTIVE. It used to pin the
    // buffer with no safety net, which is the one genuinely dangerous cell in the matrix:
    // a title that cannot hold it crackles for the whole session with no recovery. Two of
    // three titles measured could not hold 4 ms unaided; with adaptive they grow a step
    // and decay back down when it passes.
    PRESET_LOW      -> AudioConfig(PRESET_LOW, 1, true, 192, 0, 4)      // 25 ms total - the floor
    PRESET_AUTO     -> AudioConfig(PRESET_AUTO, 1, true, 576, 0, 12)    // 33 ms total - the default
    PRESET_BALANCED -> AudioConfig(PRESET_BALANCED, 1, true, 1248, 0, 26) // 47 ms total
    else            -> AudioConfig(PRESET_STABLE, 1, true, 3000, 0, 63) // 83 ms total - most margin
}

/**
 * What ALSA actually applies for a named preset.
 *
 * The shared preset objects set no buffer at all (bufferFrames = 0), so on ALSA three of the five
 * rungs collapsed to nothing but a performance mode - Auto, Balanced and Stable differed only in
 * that one field. These give the upper rungs a real buffer.
 *
 * DELIBERATELY CONSERVATIVE. Auto and Low keep 0, which means the native client's own default of
 * framesPerBurst * 2 - device-adaptive, and lower than any fixed number we could pick here (8 ms on
 * the reference device, where a hardcoded 1248 frames would be 26 ms). Only the two rungs that exist
 * to be SAFER get an explicit, larger buffer, so a wrong guess costs latency rather than crackle.
 * These sizes are a sensible ladder, not measurements: the ALSA path has an extra server hop that has
 * never been characterised the way DirectAudio's has.
 *
 * Performance mode and adaptive are left exactly as the shared preset defines them - changing those
 * would alter behaviour for every existing ALSA user, which is not what adding buffer values is for.
 */
fun alsaPresetConfig(preset: String): AudioConfig {
    val base = AUDIO_PRESETS.first { it.id == preset }.cfg
    return when (preset) {
        PRESET_BALANCED -> base.copy(bufferFrames = 1152)   // 24 ms
        PRESET_STABLE   -> base.copy(bufferFrames = 2304)   // 48 ms
        else            -> base                              // auto/low: native default (burst * 2)
    }
}

/**
 * The config to DISPLAY. Custom shows what the user set; a named preset shows what the engine will
 * really apply, which on DirectAudio is not the preset object. Never what gets SAVED - a preset saves
 * its id and the engine re-derives from that at launch.
 */
fun effectiveConfigFor(cfg: AudioConfig, driverId: String): AudioConfig = when {
    cfg.preset == PRESET_CUSTOM -> cfg
    driverId == "directaudio" -> directPresetConfig(cfg.preset)
    driverId == "alsa" -> alsaPresetConfig(cfg.preset)
    else -> cfg
}

/** ALSA never reads the latency field: nothing in the native client or its env consumes it. */
fun latencyUsedBy(driverId: String) = driverId != "alsa"

fun loadAudioConfig(ctx: Context, driverId: String): AudioConfig {
    val p = ctx.getSharedPreferences(audioPrefsName(driverId), Context.MODE_PRIVATE)
    val none = engineNoneDefault(driverId)
    val defPreset = defaultPresetFor(driverId)
    return AudioConfig(
        preset = p.getString("preset", defPreset) ?: defPreset,
        perfMode = p.getInt("perf_mode", if (none) 0 else 1),  // ALSA/DirectAudio -> NONE, Pulse -> LOW_LATENCY
        adaptive = p.getBoolean("adaptive", true),
        bufferFrames = p.getInt("buffer_frames", 0),
        maxBufferFrames = p.getInt("max_buffer_frames", 0),
        latencyMsec = p.getInt("latency_msec", defaultLatencyMsec(driverId))
    )
}

fun saveAudioConfig(ctx: Context, driverId: String, c: AudioConfig) {
    ctx.getSharedPreferences(audioPrefsName(driverId), Context.MODE_PRIVATE).edit()
        .putString("preset", c.preset).putInt("perf_mode", c.perfMode)
        .putBoolean("adaptive", c.adaptive).putInt("buffer_frames", c.bufferFrames)
        .putInt("max_buffer_frames", c.maxBufferFrames).putInt("latency_msec", c.latencyMsec)
        .apply()
}

/* ---- persistence: per-scope env, ENGINE-SCOPED keys (BANNER_AUDIO_ALSA_* / BANNER_AUDIO_PULSE_*) ----
 * This is the PERSISTENT store (per container / per shortcut). Engine-scoped so one scope can hold
 * independent ALSA and Pulse configs, and so the two engines can never read each other's keys. The
 * per-engine prefs file (loadAudioConfig above) is only the EPHEMERAL runtime the engine reads while a
 * game runs — reseeded from this env every launch (see XServerDisplayActivity.seedAudioPrefsForLaunch),
 * so nothing persists globally and no config bleeds across games/containers/engines. */
private fun engTag(driverId: String) = when (driverId) {
    "alsa" -> "ALSA"
    "directaudio" -> "DIRECT"
    else -> "PULSE"
}

/** Write cfg into the scope's env under THIS engine's key prefix only, preserving the OTHER engine's
 *  audio keys and all non-audio env — so setting ALSA never disturbs the scope's Pulse config. */
fun audioConfigToEnv(existingEnv: String, c: AudioConfig, driverId: String): String {
    val pfx = "BANNER_AUDIO_${engTag(driverId)}_"
    val keep = existingEnv.split(" ").filter { it.isNotBlank() && !it.startsWith(pfx) }
    val add = mutableListOf(
        "${pfx}PRESET=${c.preset}", "${pfx}PERF=${c.perfMode}",
        "${pfx}ADAPTIVE=${if (c.adaptive) 1 else 0}", "${pfx}LAT=${c.latencyMsec}"
    )
    if (c.bufferFrames > 0) add.add("${pfx}BF=${c.bufferFrames}")
    if (c.maxBufferFrames > 0) add.add("${pfx}MBF=${c.maxBufferFrames}")
    return (keep + add).joinToString(" ")
}

/**
 * Read a scope's env into an AudioConfig for the given engine. Reads only THIS engine's keys; DEFAULTS
 * (when the scope has no config for this engine yet) are engine-appropriate — ALSA "Stable"/perf=0 (its
 * proven crackle-free mode), Pulse "Auto"/perf=1 — matching seedAudioPrefsForLaunch's launch defaults.
 */
fun audioConfigFromEnv(env: String, driverId: String = ""): AudioConfig {
    val m = env.split(" ").filter { it.contains("=") }.associate {
        val i = it.indexOf('='); it.substring(0, i) to it.substring(i + 1)
    }
    val pfx = "BANNER_AUDIO_${engTag(driverId)}_"
    val def = AudioConfig()
    val none = engineNoneDefault(driverId)
    val defPerf = if (none) 0 else def.perfMode          // ALSA/DirectAudio -> NONE, Pulse -> LOW_LATENCY (Auto)
    val defPreset = defaultPresetFor(driverId)
    return AudioConfig(
        preset = m["${pfx}PRESET"] ?: defPreset,
        perfMode = m["${pfx}PERF"]?.toIntOrNull() ?: defPerf,
        adaptive = (m["${pfx}ADAPTIVE"]?.toIntOrNull() ?: 1) != 0,
        bufferFrames = m["${pfx}BF"]?.toIntOrNull() ?: 0,
        maxBufferFrames = m["${pfx}MBF"]?.toIntOrNull() ?: 0,
        latencyMsec = m["${pfx}LAT"]?.toIntOrNull() ?: defaultLatencyMsec(driverId)
    )
}

/**
 * The audio popup. `scopeLabel` names the target ("this container" / "this game" / "live · this
 * session"). `latencyLive`=false shows the guest-buffer row tagged "next launch" (in-game).
 */
@Composable
fun AudioSettingsDialog(
    initial: AudioConfig,
    scopeLabel: String,
    latencyLive: Boolean,
    driverLabel: String = "",
    driverId: String = "",
    onDismiss: () -> Unit,
    onSave: (AudioConfig) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var cfg by remember { mutableStateOf(initial) }
    val custom = cfg.preset == PRESET_CUSTOM
    val curPreset = AUDIO_PRESETS.first { it.id == cfg.preset }
    // On DirectAudio the latency field is a real control (it sets the driver's buffer), not a
    // winepulse-only knob, so a preset must not stamp its own Pulse-shaped value over it.
    val directBuffer = latencyIsDirectBuffer(driverId)
    // Per-row "?" help, same pattern as the container/shortcut editors: null = closed, else the
    // string res to show. HelpDialog renders on top of this dialog.
    var helpRes by remember { mutableStateOf<Int?>(null) }
    fun applyPreset(p: AudioPreset) {
        cfg = if (directBuffer) p.cfg.copy(latencyMsec = cfg.latencyMsec) else p.cfg
    }

    helpRes?.let { HelpDialog(it) { helpRes = null } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.fillMaxSize()) {
                // header
                Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("🎧 Audio settings", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = cs.onSurface, modifier = Modifier.weight(1f))
                    // Engine badge — which audio engine these settings actually hit (dropdown value in
                    // the editors; the driver chosen at launch in-game). Makes "correct settings" explicit.
                    if (driverLabel.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = cs.primary.copy(alpha = 0.16f),
                            modifier = Modifier.padding(end = 8.dp)) {
                            Text("🔊 $driverLabel", color = cs.primary, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("✕", color = cs.onSurface, fontSize = 16.sp) }
                }
                Text("Balance crackle-free vs low delay  ·  $scopeLabel",
                    color = cs.onSurfaceVariant, fontSize = 12.5.sp,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 10.dp))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                    // tradeoff meter
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("◀ Lower delay", color = cs.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("Fewer crackles ▶", color = cs.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp)
                                .background(cs.surface, RoundedCornerShape(6.dp))) {
                                Box(Modifier.fillMaxWidth(curPreset.pos).height(8.dp)
                                    .background(cs.primary, RoundedCornerShape(6.dp)))
                            }
                            Text(curPreset.note, color = cs.onSurfaceVariant, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 9.dp))
                        }
                    }

                    Text("PRESET", color = cs.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(4.dp, 14.dp, 0.dp, 8.dp))
                    AUDIO_PRESETS.forEach { p ->
                        val sel = p.id == cfg.preset
                        Surface(shape = RoundedCornerShape(16.dp),
                            color = if (sel) cs.primary.copy(alpha = 0.14f) else cs.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .border(1.5.dp, if (sel) cs.primary else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { applyPreset(p) }) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) {
                                RadioButton(selected = sel, onClick = { applyPreset(p) })
                                Text(p.emoji, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
                                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(p.name, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                                        p.badge?.let {
                                            Spacer(Modifier.width(8.dp))
                                            Text(it.uppercase(), color = cs.primary, fontSize = 10.sp,
                                                modifier = Modifier.border(1.dp, cs.primary, RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 7.dp, vertical = 1.dp))
                                        }
                                        Spacer(Modifier.weight(1f))
                                        // Inside the card, but its own click target - tapping "?" explains the
                                        // preset without selecting it, which matters when you are deciding.
                                        IconButton(onClick = { helpRes = p.helpRes }, modifier = Modifier.size(30.dp)) {
                                            Icon(Icons.Default.Help, contentDescription = "What is \"${p.name}\"?",
                                                 tint = cs.onSurfaceVariant, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                    Text(p.desc, color = cs.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Text(if (custom) "FINE-TUNE" else "FINE-TUNE — what this preset applies (Custom to edit)",
                        color = cs.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(4.dp, 16.dp, 0.dp, 8.dp))
                    val ftAlpha = if (custom) 1f else 0.4f
                    // Greyed rows must show what the engine will actually use, not the preset object:
                    // on DirectAudio a named preset means a specific buffer and a forced LOW_LATENCY,
                    // so showing the generic preset values would state the wrong thing in the one panel
                    // whose job is telling you what the preset does. Edits still go to `cfg`; this is
                    // only what is rendered, and it equals `cfg` whenever the rows are editable.
                    val shown = effectiveConfigFor(cfg, driverId)
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp).alpha(ftAlpha)) {
                            // Output mode
                            FtRow("Output mode", "AAudio performance mode", cs,
                                onHelp = { helpRes = R.string.help_audio_perf_mode }) {
                                Row {
                                    // DirectAudio must NOT offer POWER_SAVING: on this engine its AAudio
                                    // power-saving stream errors and reopens ~1/s under box64/FEX (device-
                                    // proven 2026-08-14), a wasteful churn. Every DirectAudio preset already
                                    // forces LOW_LATENCY for the same reason, so drop "Power" here too.
                                    val perfOpts = if (driverId == "directaudio")
                                        listOf("None" to 0, "Low lat." to 1)
                                    else listOf("None" to 0, "Low lat." to 1, "Power" to 2)
                                    perfOpts.forEach { (lbl, v) ->
                                        val on = shown.perfMode == v
                                        Text(lbl, color = if (on) cs.onPrimary else cs.onSurfaceVariant, fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 3.dp)
                                                .background(if (on) cs.primary else cs.surface, RoundedCornerShape(9.dp))
                                                .clickable(enabled = custom) { cfg = cfg.copy(perfMode = v) }
                                                .padding(horizontal = 10.dp, vertical = 7.dp))
                                    }
                                }
                            }
                            FtRow("Adaptive buffer", "Auto-grow only if it hears crackle", cs,
                                onHelp = { helpRes = R.string.help_audio_adaptive }) {
                                Switch(checked = shown.adaptive, enabled = custom,
                                    onCheckedChange = { cfg = cfg.copy(adaptive = it) })
                            }
                            // DirectAudio: this is the driver's own buffer (BANNER_AUDIO_DIRECT_MS), so it
                            // needs a range that can reach the hardware floor - one 4 ms burst - and a
                            // label that states the total, since Android adds a fixed ~21 ms nobody can
                            // go below. PulseAudio keeps the guest winepulse buffer it always had.
                            val latencyUsed = latencyUsedBy(driverId)
                            FtRow(
                                if (directBuffer) "Audio buffer" else "Guest buffer",
                                // ALSA reaches AAudio through the app's own server, which takes its sizing
                                // from the buffer rows below - there is no guest-side latency for this row
                                // to set, so say that rather than leaving a live-looking slider that does
                                // nothing.
                                if (!latencyUsed) "not used by ALSA — set the buffer rows below"
                                else (if (directBuffer) "≈${shown.latencyMsec + ANDROID_OUTPUT_MS} ms total · ${shown.latencyMsec} ms buffer"
                                      else "winepulse · ${shown.latencyMsec} ms") +
                                    if (!latencyLive) "  · next launch" else "", cs,
                                onHelp = { helpRes = when {
                                    !latencyUsed -> R.string.help_audio_latency_unused
                                    directBuffer -> R.string.help_audio_direct_buffer
                                    else -> R.string.help_audio_guest_buffer
                                } }) {
                                if (directBuffer)
                                    Slider(value = shown.latencyMsec.toFloat(), valueRange = 4f..120f, steps = 28,
                                        enabled = custom && latencyUsed,
                                        onValueChange = { cfg = cfg.copy(latencyMsec = (it / 4).toInt() * 4) },
                                        modifier = Modifier.width(140.dp))
                                else
                                    Slider(value = shown.latencyMsec.toFloat(), valueRange = 20f..200f, steps = 17,
                                        enabled = custom && latencyUsed,
                                        onValueChange = { cfg = cfg.copy(latencyMsec = (it / 10).toInt() * 10) },
                                        modifier = Modifier.width(140.dp))
                            }
                            FtStepper("Initial sink buffer", "frames · 0 = auto",
                                if (shown.bufferFrames > 0) "${shown.bufferFrames}" else "auto", custom, cs,
                                onHelp = { helpRes = R.string.help_audio_buffer_frames }) {
                                cfg = cfg.copy(bufferFrames = (cfg.bufferFrames + it * 256).coerceAtLeast(0))
                            }
                            FtStepper("Max sink buffer", "growth cap · 0 = device max",
                                if (shown.maxBufferFrames > 0) "${shown.maxBufferFrames}" else "device max", custom, cs,
                                onHelp = { helpRes = R.string.help_audio_max_buffer_frames }) {
                                cfg = cfg.copy(maxBufferFrames = (cfg.maxBufferFrames + it * 256).coerceAtLeast(0))
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = { onSave(cfg) }, modifier = Modifier.weight(1f)) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun FtRow(name: String, hint: String, cs: ColorScheme, onHelp: (() -> Unit)? = null,
                  control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = cs.onSurfaceVariant, fontSize = 11.5.sp)
        }
        // The "?" stays enabled while the row is greyed out: not being allowed to change a setting is
        // the moment you most want to know what it does.
        if (onHelp != null) IconButton(onClick = onHelp, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Default.Help, contentDescription = "What is \"$name\"?",
                 tint = cs.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
        control()
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(cs.surface))
}

@Composable
private fun FtStepper(name: String, hint: String, value: String, enabled: Boolean, cs: ColorScheme,
                      onHelp: (() -> Unit)? = null, onStep: (Int) -> Unit) {
    FtRow(name, hint, cs, onHelp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onStep(-1) }, enabled = enabled,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("−") }
            Text(value, color = cs.primary, fontSize = 13.sp,
                modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 6.dp))
            OutlinedButton(onClick = { onStep(1) }, enabled = enabled,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.size(34.dp)) { Text("+") }
        }
    }
}
