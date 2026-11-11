package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.winhub.hernandez.communityconfigs.CommunityConfigApply
import com.winhub.hernandez.communityconfigs.ConfigLibrary
import com.winhub.hernandez.communityconfigs.ConfigTranslator
import com.winhub.hernandez.communityconfigs.GameDoctor
import com.winhub.hernandez.communityconfigs.InstalledComponents
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.GPUInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Game Doctor as an XMB column: what went wrong in the last run, with one-tap fixes, shared configs
// for this chip, and a list of safe things to try next. Every change is snapshotted first, so
// "Undo all changes" puts the shortcut back exactly as it was before the doctor touched it.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

internal fun xmbGameDoctorMenu(xmb: XmbScope, shortcut: Shortcut): XmbMenu {
    val ctx = xmb.context
    var report by mutableStateOf<GameDoctor.Report?>(null)
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var changed by mutableStateOf(false)
    val applied = mutableStateListOf<String>()
    var snapshotted = false
    lateinit var menu: XmbMenu

    /** Blocking: runs on IO. The first change of a visit snapshots the shortcut so it can be undone. */
    fun ensureSnapshot() {
        if (!snapshotted) snapshotted = GameDoctor.snapshot(ctx, shortcut)
    }

    fun runTweak(tweak: GameDoctor.Tweak) {
        if (busy) return
        busy = true
        xmb.scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ensureSnapshot()
                try {
                    tweak.change(shortcut)
                    shortcut.saveData()
                    true
                } catch (e: RuntimeException) {
                    false
                }
            }
            if (ok) {
                applied += tweak.key
                changed = true
                note = "Done: ${tweak.label}. Launch the game to test it. Undo is at the top if it didn't help."
                xmb.saved()
            } else {
                note = "Couldn't change that setting."
            }
            busy = false
        }
    }

    fun runPick(pick: GameDoctor.Pick) {
        if (busy) return
        busy = true
        xmb.scope.launch {
            val result = applyShared(ctx, shortcut, pick) { withContext(Dispatchers.IO) { ensureSnapshot() } }
            if (result.ok && result.changed.isNotEmpty()) {
                changed = true
                applied += "cfg:${pick.entry.file}"
                xmb.saved()
            }
            val needs = result.missingComponents.map { it.label } +
                result.missingDrivers.map { "Turnip ${it.wanted}" }
            note = result.message + if (needs.isEmpty()) "" else " Still needs: ${needs.joinToString(", ")}."
            busy = false
        }
    }

    fun undo() {
        if (busy) return
        busy = true
        xmb.scope.launch {
            val ok = withContext(Dispatchers.IO) { GameDoctor.restore(ctx, shortcut) }
            busy = false
            if (ok) {
                xmb.toast("Back to how it was")
                xmb.reloadGames()
                xmb.popToRoot()
            } else {
                note = "There is nothing to undo."
            }
        }
    }

    menu = XmbMenu(
        title = "Game Doctor",
        icon = Icons.Filled.Healing,
        initialKey = "busy",
        onClose = { if (changed) xmb.reloadGames() },
    ) {
        val r = report
        if (r == null) {
            listOf<XmbRow>(
                XmbRow.Header("title", "Game Doctor"),
                XmbRow.Info("busy", "Reading the last run, settings and shared configs…", Icons.Filled.HourglassEmpty),
            )
        } else buildList<XmbRow> {
            add(XmbRow.Header("title", shortcut.name))
            note?.let { addAll(XmbTools.para("note", it, Icons.Filled.CheckCircle, ctx)) }
            if (changed) {
                add(
                    XmbRow.Action(
                        key = "undo", label = "Undo all changes", icon = Icons.Filled.History,
                        subtitle = "Back to how it was before the doctor",
                        disabledReason = if (busy) "Working…" else null,
                    ) { undo() }
                )
            }
            r.findings.forEachIndexed { i, f ->
                val (label, icon) = when (f.level) {
                    GameDoctor.Level.PROBLEM -> "Problem" to Icons.Filled.Warning
                    GameDoctor.Level.WARNING -> "Heads up" to Icons.Filled.Info
                    GameDoctor.Level.TIP -> "Good to know" to Icons.Filled.Info
                }
                add(XmbRow.Header("h$i", label))
                addAll(XmbTools.para("f$i", "${f.title}. ${f.detail}", icon, ctx))
                f.tweaks.forEachIndexed { j, t ->
                    add(tweakRow("f$i.t$j", t, applied, busy) { runTweak(t) })
                }
            }
            if (r.picks.isNotEmpty()) {
                add(XmbRow.Header("cfg", "Shared configs for this game"))
                r.picks.forEachIndexed { i, p ->
                    val key = "cfg:${p.entry.file}"
                    val parts = listOf(
                        p.entry.driver.takeIf { it.isNotBlank() }?.let { "Driver $it" },
                        p.entry.dxvk.takeIf { it.isNotBlank() }?.let { "DXVK $it" },
                        p.entry.vkd3d.takeIf { it.isNotBlank() }?.let { "VKD3D $it" },
                        p.entry.fex.takeIf { it.isNotBlank() }?.let { "FEX $it" },
                    ).filterNotNull().joinToString(" · ")
                    add(
                        XmbRow.Action(
                            key = "pick$i",
                            label = listOf(p.entry.device, p.entry.soc).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Shared config" },
                            icon = Icons.Filled.Download,
                            subtitle = parts.ifBlank { null },
                            value = if (p.matchesDevice) "Your device" else "Other device",
                            disabledReason = when {
                                key in applied -> "Applied"
                                busy -> "Working…"
                                else -> null
                            },
                        ) { runPick(p) }
                    )
                }
            }
            if (r.tweaks.isNotEmpty()) {
                add(XmbRow.Header("more", "Try something else"))
                r.tweaks.forEachIndexed { i, t ->
                    add(tweakRow("try$i", t, applied, busy) { runTweak(t) })
                }
            }
        }
    }

    xmb.scope.launch {
        val library = ConfigLibrary.load(ctx).getOrNull()
        val result = withContext(Dispatchers.IO) { GameDoctor.diagnose(ctx, shortcut, library) }
        report = result
        // Land on the first thing the user can do, not on the loading line that just went away.
        menu.selKey = result.findings.firstNotNullOfOrNull { f -> if (f.tweaks.isEmpty()) null else "f${result.findings.indexOf(f)}.t0" }
            ?: if (result.picks.isNotEmpty()) "pick0" else if (result.tweaks.isNotEmpty()) "try0" else null
    }
    return menu
}

private fun tweakRow(
    key: String,
    tweak: GameDoctor.Tweak,
    applied: List<String>,
    busy: Boolean,
    onClick: () -> Unit,
): XmbRow = XmbRow.Action(
    key = key,
    label = tweak.label,
    icon = Icons.Filled.Build,
    subtitle = tweak.detail,
    disabledReason = when {
        tweak.key in applied -> "Applied"
        busy -> "Working…"
        else -> null
    },
    onClick = onClick,
)

/** Download one shared config and merge it into [shortcut]; [beforeChange] snapshots first. */
private suspend fun applyShared(
    context: Context,
    shortcut: Shortcut,
    pick: GameDoctor.Pick,
    beforeChange: suspend () -> Unit,
): CommunityConfigApply.ConfigApplyResult {
    val text = ConfigLibrary.fetchConfig(pick.game.id, pick.entry.file)
        ?: return CommunityConfigApply.ConfigApplyResult(false, "Couldn't download that config. Check your connection and try again.")
    val json = try {
        JSONObject(text)
    } catch (e: JSONException) {
        return CommunityConfigApply.ConfigApplyResult(false, "That config is damaged and can't be read.")
    }
    beforeChange()
    return withContext(Dispatchers.IO) {
        CommunityConfigApply.apply(
            shortcut = shortcut,
            config = ConfigTranslator.translate(json),
            installed = InstalledComponents.read(context),
            containerWineVersion = shortcut.container?.wineVersion,
            isAdreno = GPUInformation.isAdrenoGPU(context),
        )
    }
}
