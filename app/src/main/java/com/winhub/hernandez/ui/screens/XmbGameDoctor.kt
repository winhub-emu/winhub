package com.winhub.hernandez.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import com.winhub.hernandez.communityconfigs.GameDoctor
import com.winhub.hernandez.container.Shortcut

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Game Doctor as an XMB column: what went wrong in the last run, with one-tap fixes, shared configs
// for this chip, and a list of safe things to try next. The state and the actions live in
// GameDoctorSession (GameDoctorUi.kt), shared with the dialog every other screen opens.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

internal fun xmbGameDoctorMenu(xmb: XmbScope, shortcut: Shortcut): XmbMenu {
    val ctx = xmb.context
    val session = GameDoctorSession(ctx.applicationContext, shortcut, xmb.scope)
    lateinit var menu: XmbMenu

    menu = XmbMenu(
        title = "Game Doctor",
        icon = Icons.Filled.Healing,
        initialKey = "busy",
        onClose = { if (session.changed) xmb.reloadGames() },
    ) {
        val r = session.report
        if (r == null) {
            listOf<XmbRow>(
                XmbRow.Header("title", "Game Doctor"),
                XmbRow.Info("busy", "Reading the last run, settings and shared configs…", Icons.Filled.HourglassEmpty),
            )
        } else buildList<XmbRow> {
            add(XmbRow.Header("title", shortcut.name))
            session.note?.let { addAll(XmbTools.para("note", it, Icons.Filled.CheckCircle, ctx)) }
            if (session.changed) {
                add(
                    XmbRow.Action(
                        key = "undo", label = "Undo all changes", icon = Icons.Filled.History,
                        subtitle = "Back to how it was before the doctor",
                        disabledReason = if (session.busy) "Working…" else null,
                    ) {
                        session.undo {
                            xmb.toast("Back to how it was")
                            xmb.reloadGames()
                            xmb.popToRoot()
                        }
                    }
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
                f.tweaks.forEachIndexed { j, t -> add(tweakRow("f$i.t$j", t, session)) }
            }
            if (r.picks.isNotEmpty()) {
                add(XmbRow.Header("cfg", "Shared configs for this game"))
                r.picks.forEachIndexed { i, p ->
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
                                session.pickKey(p) in session.applied -> "Applied"
                                session.busy -> "Working…"
                                else -> null
                            },
                        ) { session.runPick(p) { xmb.saved() } }
                    )
                }
            }
            if (r.tweaks.isNotEmpty()) {
                add(XmbRow.Header("more", "Try something else"))
                r.tweaks.forEachIndexed { i, t -> add(tweakRow("try$i", t, session)) }
            }
        }
    }

    // Land on the first thing the user can do, not on the loading line that just went away.
    session.load { result ->
        menu.selKey = result.findings.firstNotNullOfOrNull { f ->
            if (f.tweaks.isEmpty()) null else "f${result.findings.indexOf(f)}.t0"
        } ?: if (result.picks.isNotEmpty()) "pick0" else if (result.tweaks.isNotEmpty()) "try0" else null
    }
    return menu
}

private fun tweakRow(key: String, tweak: GameDoctor.Tweak, session: GameDoctorSession): XmbRow = XmbRow.Action(
    key = key,
    label = tweak.label,
    icon = Icons.Filled.Build,
    subtitle = tweak.detail,
    disabledReason = when {
        tweak.key in session.applied -> "Applied"
        session.busy -> "Working…"
        else -> null
    },
    onClick = { session.runTweak(tweak) },
)
