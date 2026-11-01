package com.winhub.hernandez.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import com.winhub.hernandez.ui.screens.SectionBox
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.ui.screens.HelpTextDialog
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Environment-variables editor — Compose replacement for the legacy EnvVarsView
// AndroidView. Shared verbatim by the container editor (ContainerDetailScreen)
// and the per-game shortcut editor (ShortcutsScreen), so the two can never drift.
//
// Storage format is unchanged: a single space-separated "NAME=VALUE" string, as
// parsed by core/EnvVars.putAll() (split on spaces, then on the FIRST '='). A
// value therefore can NOT contain a space — every value written here is stripped
// of spaces, exactly like the legacy view did.
// ─────────────────────────────────────────────────────────────────────────────

internal enum class EnvVarType { CHECKBOX, SELECT, SELECT_MULTIPLE, NUMBER, TEXT }

/**
 * One catalog entry. For CHECKBOX, [options] is exactly [offValue, onValue].
 *
 * [defaultValue] is what a freshly added row starts at. Without it a CHECKBOX starts at
 * options[0] — "off" — which is wrong for anything the underlying component enables by
 * default: adding the variable to look at it would silently turn the feature off.
 */
internal data class KnownEnvVar(
    val name: String,
    val type: EnvVarType,
    val options: List<String> = emptyList(),
    val defaultValue: String? = null,
)

/**
 * The catalog of environment variables we know how to render with a typed control.
 * Single source of truth (ported from the old EnvVarsView.knownEnvVars Java array) —
 * both editors and the add-picker read this list and nothing else.
 */
internal object KnownEnvVars {
    val all: List<KnownEnvVar> = listOf(
        KnownEnvVar("ZINK_DESCRIPTORS", EnvVarType.SELECT, listOf("auto", "lazy", "cached", "notemplates")),
        KnownEnvVar("ZINK_DEBUG", EnvVarType.SELECT_MULTIPLE, listOf("nir", "spirv", "tgsi", "validation", "sync", "compact", "noreorder")),
        KnownEnvVar("MESA_SHADER_CACHE_DISABLE", EnvVarType.CHECKBOX, listOf("false", "true")),
        KnownEnvVar("mesa_glthread", EnvVarType.CHECKBOX, listOf("false", "true")),
        KnownEnvVar("WINEESYNC", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("FD_DEV_FEATURES", EnvVarType.SELECT_MULTIPLE, listOf("enable_tp_ubwc_flag_hint=1", "storage_8bit=1")),
        KnownEnvVar("TU_DEBUG", EnvVarType.SELECT_MULTIPLE, listOf(
            "forcecb", "nocb", "startup", "deck_emu", "nir", "nobin", "sysmem", "gmem", "forcebin", "layout",
            "noubwc", "nomultipos", "nolrz", "nolrzfc", "perf", "perfc", "flushall", "syncdraw",
            "push_consts_per_stage", "rast_order", "unaligned_store", "log_skip_gmem_ops", "dynamic", "bos",
            "3d_load", "fdm", "noconform", "rd")),
        KnownEnvVar("IR3_SHADER_DEBUG", EnvVarType.SELECT_MULTIPLE, listOf("nouboopt", "nopreamble", "noearlypreamble")),
        KnownEnvVar("DXVK_HUD", EnvVarType.SELECT_MULTIPLE, listOf(
            "scale=0.5", "scale=0.7", "scale=1.0", "opacity=0.5", "opacity=0.7", "devinfo", "fps", "frametimes",
            "submissions", "drawcalls", "pipelines", "descriptors", "memory", "gpuload", "version", "api", "cs",
            "compiler", "samplers",
            // VEGAS-build HUD items (HudVegasItem governor line + HudCommitItem). Registered
            // inside VEGAS DXVK builds' HudItemSet; vanilla DXVK silently ignores unknown
            // tokens, so listing them unconditionally is safe.
            "vegas", "commit")),
        // NOTE: the old VEGAS_HUD env var was removed — nothing in any shipped VEGAS build
        // ever read it (verified against cf04e7f source). Its tokens live in DXVK_HUD now.
        KnownEnvVar("MESA_EXTENSION_MAX_YEAR", EnvVarType.NUMBER),
        KnownEnvVar("WRAPPER_MAX_IMAGE_COUNT", EnvVarType.TEXT),
        KnownEnvVar("MESA_GL_VERSION_OVERRIDE", EnvVarType.TEXT),
        KnownEnvVar("PULSE_LATENCY_MSEC", EnvVarType.NUMBER),
        KnownEnvVar("WINE_DO_NOT_CREATE_DXGI_DEVICE_MANAGER", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WINE_NEW_MEDIASOURCE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("GALLIUM_HUD", EnvVarType.SELECT_MULTIPLE, listOf("simple", "fps", "frametime")),
        // DXVK tuning
        KnownEnvVar("DXVK_DISABLE_TIMELINE_SEMAPHORES", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("DXVK_FRAME_RATE", EnvVarType.NUMBER),
        KnownEnvVar("DXVK_STATE_CACHE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("DXVK_LOG_LEVEL", EnvVarType.SELECT, listOf("none", "error", "warn", "info", "debug")),
        // VKD3D (D3D12) tuning
        KnownEnvVar("VKD3D_SHADER_MODEL", EnvVarType.SELECT, listOf("6_6", "6_5", "6_4", "6_3", "6_2", "6_1", "6_0", "5_1")),
        KnownEnvVar("VKD3D_FEATURE_LEVEL", EnvVarType.SELECT, listOf("12_2", "12_1", "12_0", "11_1", "11_0")),
        KnownEnvVar("VKD3D_CONFIG", EnvVarType.SELECT_MULTIPLE, listOf(
            "dxr", "dxr11", "force_static_cbv", "no_upload_hvv", "force_host_cached", "single_queue",
            "force_bindless_texel_buffer")),
        KnownEnvVar("VKD3D_DEBUG", EnvVarType.SELECT, listOf("none", "err", "fixme", "warn", "trace")),
        // Wine tuning
        KnownEnvVar("WINEFSYNC", EnvVarType.CHECKBOX, listOf("0", "1")),
        // Our own NtYieldExecution gate (proton-wine fcbe8302). Off by default in the layer; this
        // is the only way to discover it from inside the app.
        KnownEnvVar("WINE_FAST_YIELD", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("FEX_DISKCACHE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WINEDEBUG", EnvVarType.SELECT, listOf("-all", "fixme-all", "+seh", "+relay", "warn+all")),
        KnownEnvVar(DllOverrides.VAR, EnvVarType.TEXT),
        KnownEnvVar("WINE_FULLSCREEN_FSR", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WINE_FULLSCREEN_FSR_STRENGTH", EnvVarType.NUMBER),
        // Mesa / Turnip present + driver
        KnownEnvVar("MESA_VK_WSI_PRESENT_MODE", EnvVarType.SELECT, listOf("fifo", "mailbox", "immediate", "relaxed")),
        KnownEnvVar("MESA_NO_ERROR", EnvVarType.CHECKBOX, listOf("0", "1")),
        // GPU selection / present latency
        KnownEnvVar("DXVK_FILTER_DEVICE_NAME", EnvVarType.TEXT),
        KnownEnvVar("VKD3D_SWAPCHAIN_LATENCY_FRAMES", EnvVarType.NUMBER),
        // DXVK extras
        KnownEnvVar("DXVK_ASYNC", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("DXVK_GPLASYNCCACHE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("DXVK_CONFIG", EnvVarType.TEXT),
        KnownEnvVar("DXVK_CONFIG_FILE", EnvVarType.TEXT),
        // Wine extras
        KnownEnvVar("WINE_DISABLE_FULLSCREEN_HACK", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WINE_X11FORCEGLX", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WINE_GST_NO_GL", EnvVarType.CHECKBOX, listOf("0", "1")),
        // Mali BCn decode layer (leegao / Fcharan libbcn_layer.so). On Adreno the app keeps these
        // OFF automatically (native BCn); setting them here only matters on Mali/non-Qualcomm GPUs.
        KnownEnvVar("ENABLE_BCN_COMPUTE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_COMPUTE_AUTO", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_COMPUTE_IMAGE_VIEW", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_TRANSCODE_TO_ASTC", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_TRANSCODE_TO_ETC1", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_TRANSCODE_TO_ETC2", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_PROFILE_TRANSFERS", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_ASTC_TRY_2P", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_ASTC_ONLY_2P", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_ASTC_USE_LARGE_STEPS", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BCN_LAYER_LOG_LEVEL", EnvVarType.SELECT, listOf("error", "info,error", "warn,error", "info,warn,error")),
        KnownEnvVar("BCN_MAX_STAGING_CACHE_MB", EnvVarType.NUMBER),
        KnownEnvVar("BCN_QUEUE_THROTTLE_LIMIT", EnvVarType.NUMBER),
        // Mali wrapper (Vulkan ICD). WRAPPER_EMULATE_BCN: 0=off 1=default 2=full 3=auto.
        KnownEnvVar("WRAPPER_EMULATE_BCN", EnvVarType.SELECT, listOf("0", "1", "2", "3")),
        KnownEnvVar("WRAPPER_BCN_ASTC", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_USE_BCN_CACHE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_BCN_GPU", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_NO_BCN_THREAD", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_SAFE_CREATE_DEVICE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_DIAG", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_REDUCE_DEPTH_FORMAT", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_DISABLE_PRESENT_WAIT", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_DISABLE_PLACED", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_NO_PATCH_OPCONSTCOMP", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("WRAPPER_DRIVER_ID", EnvVarType.NUMBER),
        KnownEnvVar("WRAPPER_VMEM_MAX_SIZE", EnvVarType.NUMBER),
        // Wine guest VA-reservation cap in MB (default 16384). Caps heavy AAA titles that reserve
        // hundreds of GB of address space and OOM the X server. NOTE: only honoured by a Wine build
        // patched to read WINEVMEMMAXSIZE.
        KnownEnvVar("WINEVMEMMAXSIZE", EnvVarType.NUMBER),
        KnownEnvVar("WRAPPER_VK_VERSION", EnvVarType.TEXT),
        KnownEnvVar("WRAPPER_EXTENSION_BLACKLIST", EnvVarType.TEXT),
        KnownEnvVar("WRAPPER_DEVICE_NAME", EnvVarType.TEXT),
        KnownEnvVar("WRAPPER_RESOURCE_TYPE", EnvVarType.TEXT),
        // Mali DX12 compat layer (leegao compat_layer; Valhall Mali r32p1+).
        KnownEnvVar("ENABLE_DXVK_MALI_COMPAT_LAYER", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("COMPAT_EMULATE_SPARSE_BINDING", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("COMPAT_EMULATE_PUSH_DESCRIPTORS", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("COMPAT_EMULATE_NULL_DESCRIPTORS", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("COMPAT_FORCE_MASKING", EnvVarType.CHECKBOX, listOf("0", "1")),
        // ── DirectAudio (winedirectaudio.drv) ────────────────────────────────────────────
        // The driver reads these from the guest environment at stream open; the audio cog
        // exposes none of them. Buffer values are OUR side of the delay only — Android adds
        // a fixed ~21 ms on top, so _MS=12 is ~33 ms to the ear and one burst (~4 ms) is the
        // ~25 ms floor no app on Android goes below.
        //
        // Deliberately NOT listed: _PERF, _ADAPTIVE, _BF, _MBF. The audio cog rewrites those
        // from the selected preset on every launch, so offering them here would hand out a
        // control that loses a fight the user cannot see. _MS/_MAXMS override _BF/_MBF inside
        // the driver, which is why they are the ones worth setting by hand.
        KnownEnvVar("BANNER_AUDIO_DIRECT_MS", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_MAXMS", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_DECAY", EnvVarType.CHECKBOX, listOf("0", "1"), defaultValue = "1"),
        KnownEnvVar("BANNER_AUDIO_DIRECT_PERIOD_MS", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_MINPERIOD_MS", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_EXCLUSIVE", EnvVarType.CHECKBOX, listOf("0", "1")),
        KnownEnvVar("BANNER_AUDIO_DIRECT_WATCHDOG", EnvVarType.CHECKBOX, listOf("0", "1"), defaultValue = "1"),
        KnownEnvVar("BANNER_AUDIO_DIRECT_STALL_MS", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_DECAY_QUIET_MS", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_DECAY_PUNISH_MS", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_DECAY_MAXBACKOFF", EnvVarType.NUMBER),
        KnownEnvVar("BANNER_AUDIO_DIRECT_LOG", EnvVarType.CHECKBOX, listOf("0", "1")),
    )

    private val byName = all.associateBy { it.name }

    fun find(name: String): KnownEnvVar? = byName[name]

    val names: List<String> = all.map { it.name }
}

/**
 * Helpers for the "Prefer game-folder DLLs" toggle, which is a *view* over the
 * WINEDLLOVERRIDES environment variable rather than a field of its own — there is
 * exactly one source of truth and hand-editing the variable keeps working.
 *
 * WINEDLLOVERRIDES grammar: entries separated by ';', each `dll[,dll2]=order`,
 * where order is a comma-separated list of 'n' (native) / 'b' (builtin). The
 * toggle's own signature is a single-key entry with the value "n,b".
 */
internal object DllOverrides {
    const val VAR = "WINEDLLOVERRIDES"

    /**
     * The DLLs a game may legitimately ship next to its EXE (wrappers, loaders, ASI
     * hosts, mod launchers) and that Wine is happy to load native-first.
     *
     * Graphics DLLs (d3d9/d3d10/d3d11/d3d12/dxgi) are deliberately NOT here: DXVK and
     * VKD3D own those, and forcing native would load the game's stub over the wrapper
     * and break rendering outright.
     */
    val PREFER_GAME_FOLDER: List<String> =
        listOf("version", "winmm", "dinput", "dinput8", "dsound", "xinput1_3", "msacm32", "dbghelp")

    private const val SIGNATURE = "n,b"

    /** One `dll[,dll2]=order` entry. [order] is null for a bare entry with no '='. */
    private data class Entry(val keys: List<String>, val order: String?) {
        fun render(): String = keys.joinToString(",") + if (order != null) "=$order" else ""
        fun has(dll: String): Boolean = keys.any { it.equals(dll, ignoreCase = true) }
    }

    private fun parse(raw: String): MutableList<Entry> =
        raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part ->
                val i = part.indexOf('=')
                if (i < 0) Entry(part.split(',').map { it.trim() }.filter { it.isNotEmpty() }, null)
                else Entry(
                    part.substring(0, i).split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    part.substring(i + 1).trim()
                )
            }
            .filter { it.keys.isNotEmpty() }
            .toMutableList()

    private fun render(entries: List<Entry>): String = entries.joinToString(";") { it.render() }

    private fun isNativeFirst(order: String?): Boolean {
        val first = order?.split(',')?.firstOrNull()?.trim()?.lowercase() ?: return false
        return first == "n" || first == "native"
    }

    private fun isSignature(e: Entry): Boolean =
        e.keys.size == 1 &&
            PREFER_GAME_FOLDER.any { it.equals(e.keys[0], ignoreCase = true) } &&
            e.order?.replace(" ", "")?.lowercase() == SIGNATURE

    /** True when every DLL in the safe list already resolves native-first. */
    fun isEnabled(overrides: String): Boolean {
        if (overrides.isBlank()) return false
        val entries = parse(overrides)
        return PREFER_GAME_FOLDER.all { dll ->
            entries.any { it.has(dll) && isNativeFirst(it.order) }
        }
    }

    // ── Toggle helpers ───────────────────────────────────────────────────────
    // Everything below is per-DLL. The master switch drives them over a list (the
    // detected set, or the whole safe-list when nothing was detected); each per-DLL
    // row drives exactly one. They share one grammar, so a folder that ships only
    // version.dll is flipped without touching winmm, dsound, etc., and a hand-written
    // override for a DLL we don't manage is left alone.

    /** True when [dll] alone resolves native-first in [overrides]. */
    fun isEnabled(overrides: String, dll: String): Boolean {
        if (overrides.isBlank()) return false
        return parse(overrides).any { it.has(dll) && isNativeFirst(it.order) }
    }

    /** True when every DLL in [dlls] resolves native-first (empty list ⇒ false). */
    fun isEnabled(overrides: String, dlls: List<String>): Boolean =
        dlls.isNotEmpty() && dlls.all { isEnabled(overrides, it) }

    /** Turn a single [dll] native-first, splitting it out of any group it shared. */
    fun enable(overrides: String, dll: String): String {
        val entries = parse(overrides)
        val idx = entries.indexOfFirst { it.has(dll) }
        if (idx < 0) {
            entries += Entry(listOf(dll), SIGNATURE)
        } else {
            val existing = entries[idx]
            if (!isNativeFirst(existing.order)) {
                if (existing.keys.size == 1) {
                    entries[idx] = Entry(listOf(dll), SIGNATURE)
                } else {
                    entries[idx] = existing.copy(keys = existing.keys.filterNot { it.equals(dll, ignoreCase = true) })
                    entries += Entry(listOf(dll), SIGNATURE)
                }
            }
        }
        return render(entries)
    }

    /** Enable every DLL in [dlls], folding each in over whatever the user already wrote. */
    fun enable(overrides: String, dlls: List<String>): String =
        dlls.fold(overrides) { acc, dll -> enable(acc, dll) }

    /**
     * Turn a single [dll] back off: drop it from wherever it currently resolves
     * native-first, then put back whatever [baseline] said about it (so a hand-written
     * `version=b,n` we split apart comes back). Only reachable when the row is on, i.e.
     * the DLL is native-first, so a builtin-first override the user typed is never lost.
     */
    fun disable(overrides: String, baseline: String, dll: String): String {
        val entries = parse(overrides).mapNotNull { e ->
            if (!e.has(dll)) e
            else e.keys.filterNot { it.equals(dll, ignoreCase = true) }
                .takeIf { it.isNotEmpty() }?.let { e.copy(keys = it) }
        }.toMutableList()
        parse(baseline).firstOrNull { it.has(dll) }?.let { b ->
            if (entries.none { it.has(dll) }) entries += Entry(listOf(dll), b.order)
        }
        return render(entries)
    }

    /** Disable every DLL in [dlls], restoring each from [baseline]. */
    fun disable(overrides: String, baseline: String, dlls: List<String>): String =
        dlls.fold(overrides) { acc, dll -> disable(acc, baseline, dll) }

    /**
     * The user-authored value a toggle restores to when switched off: everything the editor
     * found EXCEPT our own signature entries. Those markers — written by a previous session
     * or earlier in this one — are ours, not user intent, and are ALWAYS stripped. The old
     * code only stripped them when the whole safe-list was on, so a lone `version=n,b` (the
     * common single-proxy case) stayed in the baseline and switching it off just restored it,
     * leaving the toggle stuck on. Now toggling a DLL off deletes its entry outright (master
     * off deletes every entry it added), while a hand-written builtin-first or grouped entry
     * is preserved and comes back intact. A hand-written override byte-identical to our
     * signature is indistinguishable from ours and shares their fate.
     */
    fun baselineOf(overrides: String): String =
        render(parse(overrides).filterNot { isSignature(it) })
}

// ─────────────────────────────────────────────────────────────────────────────

/** One editable row. [id] keeps Compose identity stable while the name is edited. */
private data class EnvRow(val id: Int, val name: String, val value: String)

private fun parseRows(raw: String, nextId: () -> Int): List<EnvRow> =
    raw.split(" ")
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else EnvRow(nextId(), part.substring(0, i), part.substring(i + 1))
        }

private fun renderRows(rows: List<EnvRow>): String =
    // Keep every NAMED row — an empty value ("NAME=") is valid env-var syntax and must NOT be
    // silently dropped. The old `&& it.value.isNotBlank()` discarded freshly-added custom variables
    // (which start with an empty value) so they vanished on save even though they showed in the list.
    rows.filter { it.name.isNotBlank() }
        .joinToString(" ") { "${it.name}=${it.value}" }

/**
 * The shared environment-variables editor.
 *
 * @param value  the current space-separated "NAME=VALUE" string.
 * @param onValueChange called with the new string on every edit — the caller owns the state,
 *        so nothing has to be flushed back when this composable leaves composition.
 * @param gameDir  when non-null (shortcut editor), the game's folder is scanned off the main
 *        thread for shipped Windows DLLs and an actionable hint is offered. The container
 *        editor has no single game folder and passes null.
 */
@Composable
internal fun EnvVarsEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    gameDir: File? = null,
    /**
     * Help text for the "Prefer game-folder DLLs" toggle, shown behind a "?" beside it. Null (every
     * caller but the Linux-entry shortcut editor) draws no button, so the toggle reads as it always has.
     * Passed as text rather than a resource id because what it has to say depends on the caller.
     */
    preferDllsHelp: String? = null,
) {
    var showPreferDllsHelp by remember { mutableStateOf(false) }
    if (showPreferDllsHelp && preferDllsHelp != null) HelpTextDialog(preferDllsHelp) { showPreferDllsHelp = false }
    val idSource = remember { intArrayOf(0) }
    val nextId = { idSource[0]++ }
    val rows: SnapshotStateList<EnvRow> = remember { mutableStateListOf<EnvRow>().apply { addAll(parseRows(value, nextId)) } }
    // What we last pushed out, so an external change to `value` (a different container being
    // loaded into the same ViewModel) re-seeds the rows while our own edits do not.
    var lastEmitted by remember { mutableStateOf(value) }
    // The WINEDLLOVERRIDES value as it stood when the editor opened — see DllOverrides.disable().
    var dllBaseline by remember {
        mutableStateOf(DllOverrides.baselineOf(rows.firstOrNull { it.name == DllOverrides.VAR }?.value ?: ""))
    }

    var showAddPicker by remember { mutableStateOf(false) }
    var rawMode by remember { mutableStateOf(false) }
    var foundDlls by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(value) {
        if (value != lastEmitted) {
            rows.clear()
            rows.addAll(parseRows(value, nextId))
            lastEmitted = value
            dllBaseline = DllOverrides.baselineOf(rows.firstOrNull { it.name == DllOverrides.VAR }?.value ?: "")
        }
    }

    // Detection hint (shortcut editor only): does the game ship any of the safe-list DLLs?
    // Directory listing is I/O, so it runs off the main thread; an unreadable folder simply
    // yields no hint. We never apply anything on our own — some games ship a dsound.dll or
    // dbghelp.dll that actually works better on builtin, so flipping this without the user
    // asking could regress a game that runs fine today.
    LaunchedEffect(gameDir) {
        val dir = gameDir ?: return@LaunchedEffect
        foundDlls = withContext(Dispatchers.IO) {
            runCatching {
                val present = dir.list()?.map { it.lowercase() }?.toSet() ?: emptySet()
                DllOverrides.PREFER_GAME_FOLDER.filter { "$it.dll" in present }
            }.getOrDefault(emptyList())
        }
    }

    fun emit() {
        val rendered = renderRows(rows)
        lastEmitted = rendered
        onValueChange(rendered)
    }

    fun setRow(index: Int, newValue: String) {
        rows[index] = rows[index].copy(value = newValue.trim().replace(" ", ""))
        emit()
    }

    fun putVar(name: String, newValue: String) {
        val index = rows.indexOfFirst { it.name == name }
        if (newValue.isEmpty()) {
            if (index >= 0) rows.removeAt(index)
        } else if (index >= 0) {
            rows[index] = rows[index].copy(value = newValue)
        } else {
            rows.add(EnvRow(nextId(), name, newValue))
        }
        emit()
    }

    val overrides = rows.firstOrNull { it.name == DllOverrides.VAR }?.value ?: ""
    // The master switch governs the DLLs actually detected next to the EXE; with no detection
    // (the container editor, or an unreadable/empty folder) it falls back to the full safe-list
    // so its behaviour is unchanged from before per-DLL rows existed.
    val masterScope = if (foundDlls.isNotEmpty()) foundDlls else DllOverrides.PREFER_GAME_FOLDER
    val allPreferred = DllOverrides.isEnabled(overrides, masterScope)
    val anyPreferred = masterScope.any { DllOverrides.isEnabled(overrides, it) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── Compatibility (pinned to the top) ────────────────────────────────
        // Hidden while the raw text editor is open: there the user is authoring the
        // whole string by hand, and a helper that rewrites it under them would fight
        // the cursor.
        if (!rawMode) SectionBox(title = "Compatibility") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    // Master = select-all/none over whatever is in scope. "On" only when every
                    // scoped DLL is native-first; a partial state reads as off, and the caption
                    // plus the per-DLL rows below make that unambiguous.
                    checked = allPreferred,
                    onCheckedChange = { on ->
                        putVar(
                            DllOverrides.VAR,
                            if (on) DllOverrides.enable(overrides, masterScope)
                            else DllOverrides.disable(overrides, dllBaseline, masterScope)
                        )
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Prefer game-folder DLLs")
                    Text(
                        "Loads the game's own copies of " + DllOverrides.PREFER_GAME_FOLDER.joinToString(", ") +
                            " instead of Wine's. Needed by many wrappers and mod loaders. Graphics DLLs are " +
                            "never included — DXVK/VKD3D own those.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Mixed state: some but not all scoped DLLs are on. The Switch is binary to
                    // match every other toggle in the app; this line keeps it honest at a glance.
                    if (anyPreferred && !allPreferred) {
                        Text(
                            "Some game-folder DLLs on — see below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (preferDllsHelp != null) {
                    IconButton(onClick = { showPreferDllsHelp = true }) {
                        Icon(
                            Icons.Outlined.HelpOutline,
                            contentDescription = "About Prefer game-folder DLLs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // One toggle per DLL actually detected in the folder. Each flips only its own
            // entry, so a game that ships just version.dll never gets winmm/dsound forced too.
            if (foundDlls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Detected in the game folder — toggle each individually:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                foundDlls.forEach { dll ->
                    val on = DllOverrides.isEnabled(overrides, dll)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = on,
                            onCheckedChange = { checked ->
                                putVar(
                                    DllOverrides.VAR,
                                    if (checked) DllOverrides.enable(overrides, dll)
                                    else DllOverrides.disable(overrides, dllBaseline, dll)
                                )
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("$dll.dll", modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Variables ────────────────────────────────────────────────────────
        SectionBox(title = "Environment Variables") {
            if (rawMode) {
                // Escape hatch: edit the whole string by hand. Same format that gets stored,
                // so anything the typed controls can't express can still be entered here.
                var rawText by remember { mutableStateOf(renderRows(rows)) }
                // Tokens that aren't NAME=VALUE. The add-picker can't produce these — it splits the
                // name and value for you — but this box stores exactly what is typed, so a missing
                // '=' lands in the saved string. The launcher skips such tokens rather than failing,
                // which means the variable silently does nothing; say so here instead, while the
                // cursor is still in the box. Leading '=' counts: there is no name to bind to.
                val malformed = rawText.split(" ").filter { it.isNotBlank() && it.indexOf('=') <= 0 }
                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        // Keep the row model in step so the typed controls (and the
                        // compatibility toggle) are already correct on the way back out.
                        rows.clear()
                        rows.addAll(parseRows(it, nextId))
                        lastEmitted = it
                        onValueChange(it)
                    },
                    label = { Text("NAME=VALUE, separated by spaces") },
                    isError = malformed.isNotEmpty(),
                    supportingText = if (malformed.isEmpty()) null else {
                        {
                            Text(
                                "Ignored — needs NAME=VALUE: " + malformed.joinToString(", ") +
                                    ". Values can't contain spaces.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
            } else if (rows.isEmpty()) {
                Text(
                    "No environment variables set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                rows.forEachIndexed { index, row ->
                    if (index > 0) MenuItemDivider()
                    key(row.id) {
                        EnvVarRow(
                            row = row,
                            onValueChange = { setRow(index, it) },
                            onRemove = { rows.removeAt(index); emit() }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAddPicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Variable")
            }
            TextButton(onClick = { rawMode = !rawMode }) {
                Text(if (rawMode) "Done" else "Edit as text")
            }
        }
    }

    if (showAddPicker) {
        AddEnvVarPicker(
            existingNames = rows.map { it.name }.toSet(),
            onAdd = { raw ->
                // Accept "NAME=VALUE" as well as a bare "NAME": split on the FIRST '=' so a custom
                // var typed as "mytest=12345" is stored as name + value, not one unparseable token
                // with a blank value (which used to be dropped on save). A bare NAME keeps its known
                // default, or "" for a custom one — an empty value is valid and now persists.
                val cleaned = raw.trim().replace(" ", "")
                val eq = cleaned.indexOf('=')
                val name  = if (eq >= 0) cleaned.substring(0, eq) else cleaned
                val value = if (eq >= 0) cleaned.substring(eq + 1) else defaultValueFor(cleaned)
                if (name.isNotEmpty() && rows.none { it.name == name }) {
                    rows.add(EnvRow(nextId(), name, value))
                    emit()
                }
                showAddPicker = false
            },
            onDismiss = { showAddPicker = false }
        )
    }
}

/** Seed value for a freshly added variable so the row renders with a sane control state. */
private fun defaultValueFor(name: String): String {
    val known = KnownEnvVars.find(name) ?: return ""
    known.defaultValue?.let { return it }
    return when (known.type) {
        EnvVarType.CHECKBOX -> known.options.getOrElse(0) { "0" }
        EnvVarType.SELECT -> known.options.firstOrNull() ?: ""
        else -> ""
    }
}

@Composable
private fun EnvVarRow(
    row: EnvRow,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val known = KnownEnvVars.find(row.name)
    val type = known?.type ?: EnvVarType.TEXT
    val context = LocalContext.current
    val help = remember(row.name) { envVarHelp(context, row.name) }
    var showHelp by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            when (type) {
                EnvVarType.CHECKBOX -> {
                    val offValue = known?.options?.getOrNull(0) ?: "0"
                    val onValue = known?.options?.getOrNull(1) ?: "1"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = row.value == onValue || row.value == "1" || row.value == "true",
                            onCheckedChange = { onValueChange(if (it) onValue else offValue) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(row.name, modifier = Modifier.weight(1f))
                    }
                }
                EnvVarType.SELECT -> {
                    // Editable combobox, not a plain dropdown: the presets are a shortcut, not
                    // the whole vocabulary. WINEDEBUG in particular is routinely set to something
                    // like "+loaddll,+seh,+module" that no preset list can cover, so a typed value
                    // outside the list is kept verbatim and never coerced back onto a preset.
                    EnvValueField(
                        name = row.name,
                        value = row.value,
                        onValueChange = onValueChange,
                        presets = known?.options.orEmpty()
                    )
                }
                EnvVarType.SELECT_MULTIPLE -> {
                    MultiSelectGroup(
                        name = row.name,
                        options = known?.options.orEmpty(),
                        value = row.value,
                        onValueChange = onValueChange
                    )
                }
                EnvVarType.NUMBER, EnvVarType.TEXT -> {
                    EnvValueField(
                        name = row.name,
                        value = row.value,
                        onValueChange = onValueChange,
                        numeric = type == EnvVarType.NUMBER
                    )
                }
            }
        }
        // "?" only when we actually have something to say — a variable with no help string shows
        // no button rather than one that does nothing when tapped.
        if (help != null) {
            IconButton(onClick = { showHelp = true }) {
                Icon(
                    Icons.Outlined.HelpOutline,
                    contentDescription = "About ${row.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove ${row.name}")
        }
    }
    if (showHelp && help != null) HelpTextDialog(help) { showHelp = false }
}

/**
 * Per-variable help, looked up by name: env_var_help__&lt;name lowercased&gt;. Resolved by name rather
 * than resource id because the catalog is data, not code — a variable added to KnownEnvVars picks
 * its help up automatically once the string exists, and shows no "?" until it does.
 */
private fun envVarHelp(context: android.content.Context, name: String): String? =
    StringUtils.getString(context, "env_var_help__" + name.lowercase(java.util.Locale.ENGLISH))

/**
 * The typed value field shared by SELECT, TEXT and NUMBER rows.
 *
 * When [presets] is non-empty the field is an editable combobox: the text area takes focus and
 * the keyboard on tap, and the trailing arrow opens the preset list — picking one replaces the
 * text. Anything typed that isn't a preset is stored as-is.
 *
 * Values can't contain a space, because core/EnvVars splits the stored string on spaces before
 * splitting on the first '='. Rather than corrupt such a value on save, spaces are dropped as
 * they're typed and the field says so — the constraint is surfaced instead of applied silently.
 */
@Composable
private fun EnvValueField(
    name: String,
    value: String,
    onValueChange: (String) -> Unit,
    presets: List<String> = emptyList(),
    numeric: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var spaceDropped by remember { mutableStateOf(false) }

    val supporting: (@Composable () -> Unit)? = if (!spaceDropped) null else {
        @Composable {
            Text(
                "Spaces aren't allowed here — variables are separated by spaces.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    val arrow: (@Composable () -> Unit)? = if (presets.isEmpty()) null else {
        @Composable {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Presets for $name")
            }
        }
    }

    Box {
        OutlinedTextField(
            value = value,
            onValueChange = { typed ->
                spaceDropped = typed.contains(' ')
                onValueChange(typed)
            },
            label = { Text(name) },
            singleLine = true,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number)
                              else KeyboardOptions.Default,
            supportingText = supporting,
            trailingIcon = arrow,
            modifier = Modifier.fillMaxWidth()
        )
        if (presets.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.outlinedMenuCard()
            ) {
                presets.forEachIndexed { index, preset ->
                    if (index > 0) MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text(preset) },
                        onClick = {
                            spaceDropped = false
                            onValueChange(preset)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** Above this many options a SELECT_MULTIPLE group starts collapsed behind a disclosure. */
private const val MULTI_SELECT_COLLAPSE_ABOVE = 6

/**
 * A SELECT_MULTIPLE chip group. Small groups render as a plain grid, exactly as before.
 * Big ones (TU_DEBUG is ~30 options, DXVK_HUD ~20) would otherwise fill the screen with a wall
 * of chips, so they start collapsed showing only the chips that are actually enabled.
 *
 * Collapsed chips are drawn as static labels, not FilterChips: a tap on a collapsed chip that
 * silently deselected it would be a destructive action hidden behind a summary view. Only the
 * disclosure control reacts, and expanding restores the normal selectable grid.
 *
 * Expansion is remembered per row, so toggling a chip doesn't fold the group back up. It lives
 * in the row's own composition slot (rows are keyed by a stable id), which means it survives
 * recomposition but deliberately resets to collapsed each time the tab is opened.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectGroup(
    name: String,
    options: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val selected = value.split(",").filter { it.isNotBlank() }
    val collapsible = options.size > MULTI_SELECT_COLLAPSE_ABOVE
    var expanded by remember { mutableStateOf(false) }
    val showGrid = expanded || !collapsible

    Column {
        if (collapsible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $name" else "Expand $name",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showGrid) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { option ->
                    val isOn = option in selected
                    FilterChip(
                        selected = isOn,
                        onClick = {
                            val next = if (isOn) selected - option else selected + option
                            onValueChange(next.joinToString(","))
                        },
                        label = { Text(option, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        } else if (selected.isEmpty()) {
            Text(
                "None selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selected.forEach { option ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            option,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Searchable add-picker with separate Name and Value boxes. The catalog is well past 30 entries,
 * so a flat list is unusable — the Name box filters the catalog AND doubles as the free-form name
 * for a variable we don't know about; the Value box supplies its value (no '=' needed, though a
 * pasted "NAME=VALUE" in the Name box is split for you). A live preview states whether it will save.
 */
@Composable
private fun AddEnvVarPicker(
    existingNames: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameQuery by remember { mutableStateOf("") }
    var valueQuery by remember { mutableStateOf("") }
    val pickerContext = LocalContext.current
    // Help text for the candidate whose "?" was tapped, shown over the picker so the list stays
    // put behind it — you read what a variable does, dismiss, and carry on choosing.
    var pickerHelp by remember { mutableStateOf<String?>(null) }
    val rawName = nameQuery.trim().replace(" ", "")
    // TWO boxes — Name and Value — so users never have to type an '='. The Name box still also
    // accepts a pasted "NAME=VALUE" (split on the FIRST '='); otherwise the Value box supplies it.
    val eqIdx = rawName.indexOf('=')
    val name  = if (eqIdx >= 0) rawName.substring(0, eqIdx) else rawName
    val value = if (eqIdx >= 0) rawName.substring(eqIdx + 1) else valueQuery.trim().replace(" ", "")
    val alreadyExists = name.isNotEmpty() && name in existingNames
    val isKnownName   = name in KnownEnvVars.names
    // Offer the explicit Add action for a custom name, or whenever a value was supplied (so a known
    // name can be added with a value directly). A bare known name is added from the list below.
    val showAddTyped  = name.isNotEmpty() && !alreadyExists && (value.isNotEmpty() || !isKnownName)
    val candidates = remember(name, existingNames) {
        KnownEnvVars.names
            .filter { it !in existingNames }
            .filter { name.isBlank() || it.contains(name, ignoreCase = true) }
    }
    // Live "will it save?" feedback under the boxes. null = nothing to say. Pair = (message, isError).
    val previewMsg: Pair<String, Boolean>? = when {
        rawName.isEmpty() && valueQuery.isBlank() -> null
        name.isEmpty() -> "Won't be saved — enter a variable name" to true
        alreadyExists -> "\"$name\" is already in the list" to true
        value.isEmpty() -> "Saves as  $name=  (empty value)" to false
        else -> "Saves as  $name = $value" to false
    }
    // If the user typed "=" in the Name box we still split it correctly (name before, value after) —
    // reassure them rather than error: we roll with it AND teach the two-box way.
    val splitNote = if (eqIdx >= 0 && name.isNotEmpty())
        "No \"=\" needed — the Value box handles that. Split it for you." else null
    // What the Add actions hand back — onAdd re-splits on the first '=' (so an empty value is fine).
    fun combined(varName: String) = if (value.isEmpty()) varName else "$varName=$value"

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add environment variable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nameQuery,
                    onValueChange = { nameQuery = it },
                    label = { Text("Name (search or type)") },
                    placeholder = { Text("e.g. DXVK_FRAME_RATE") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = valueQuery,
                    onValueChange = { valueQuery = it },
                    label = { Text("Value (optional)") },
                    placeholder = { Text("e.g. 60") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (previewMsg != null) {
                    Text(
                        previewMsg.first,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (previewMsg.second) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (splitNote != null) {
                    Text(
                        splitNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (showAddTyped) {
                        TextButton(onClick = { onAdd(combined(name)) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Add  $name${if (value.isNotEmpty()) " = $value" else ""}",
                                modifier = Modifier.weight(1f))
                        }
                        if (candidates.isNotEmpty()) MenuItemDivider()
                    }
                    candidates.forEachIndexed { index, known ->
                        if (index > 0) MenuItemDivider()
                        // "?" here as well as on the added row: this list is where you decide
                        // WHETHER to add something, so needing to add it blind just to read what
                        // it does — then delete it again — is the wrong way round.
                        val candidateHelp = remember(known) { envVarHelp(pickerContext, known) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onAdd(combined(known)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(known, modifier = Modifier.weight(1f))
                            }
                            if (candidateHelp != null) {
                                IconButton(onClick = { pickerHelp = candidateHelp }) {
                                    Icon(
                                        Icons.Outlined.HelpOutline,
                                        contentDescription = "About $known",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (candidates.isEmpty() && !showAddTyped) {
                        Text(
                            "No matches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
    // Composed AFTER the picker so it draws on top of it — a dialog composed first ends up behind
    // the one that follows, which would look like the "?" did nothing.
    pickerHelp?.let { text -> HelpTextDialog(text) { pickerHelp = null } }
}
