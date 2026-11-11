package com.winhub.hernandez.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.communityconfigs.CommunityConfigApply
import com.winhub.hernandez.communityconfigs.ConfigLibrary
import com.winhub.hernandez.communityconfigs.ConfigTranslator
import com.winhub.hernandez.communityconfigs.GameDoctor
import com.winhub.hernandez.communityconfigs.InstalledComponents
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.GPUInformation
import com.winhub.hernandez.ui.components.clickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/*
 * Game Doctor, shared by every place that can show it: the XMB column (XmbGameDoctor.kt) and the
 * dialog below, which any screen opens through [LocalGameDoctor]. Both drive one [GameDoctorSession],
 * so a fix, its snapshot and its undo behave identically wherever they were started.
 */

/** State and actions of one doctor visit. Compose-observable; [scope] outlives the UI that shows it. */
internal class GameDoctorSession(
    private val ctx: Context,
    val shortcut: Shortcut,
    private val scope: CoroutineScope,
) {
    var report by mutableStateOf<GameDoctor.Report?>(null)
        private set
    var note by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var changed by mutableStateOf(false)
        private set
    val applied = mutableStateListOf<String>()
    private var snapshotted = false

    fun load(onLoaded: (GameDoctor.Report) -> Unit = {}) {
        scope.launch {
            val library = ConfigLibrary.load(ctx).getOrNull()
            val result = withContext(Dispatchers.IO) { GameDoctor.diagnose(ctx, shortcut, library) }
            report = result
            onLoaded(result)
        }
    }

    /** Blocking: runs on IO. The first change of a visit snapshots the shortcut so it can be undone. */
    private fun ensureSnapshot() {
        if (!snapshotted) snapshotted = GameDoctor.snapshot(ctx, shortcut)
    }

    fun runTweak(tweak: GameDoctor.Tweak, onApplied: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
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
                onApplied()
            } else {
                note = "Couldn't change that setting."
            }
            busy = false
        }
    }

    fun runPick(pick: GameDoctor.Pick, onApplied: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            val result = applyShared(pick)
            if (result.ok && result.changed.isNotEmpty()) {
                changed = true
                applied += pickKey(pick)
                onApplied()
            }
            val needs = result.missingComponents.map { it.label } + result.missingDrivers.map { "Turnip ${it.wanted}" }
            note = result.message + if (needs.isEmpty()) "" else " Still needs: ${needs.joinToString(", ")}."
            busy = false
        }
    }

    /** Puts the snapshot back. The in-memory shortcut is stale after that, so [onUndone] should reload the games. */
    fun undo(onUndone: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { GameDoctor.restore(ctx, shortcut) }
            busy = false
            if (ok) onUndone() else note = "There is nothing to undo."
        }
    }

    fun pickKey(pick: GameDoctor.Pick): String = "cfg:${pick.entry.file}"

    private suspend fun applyShared(pick: GameDoctor.Pick): CommunityConfigApply.ConfigApplyResult {
        val text = ConfigLibrary.fetchConfig(pick.game.id, pick.entry.file)
            ?: return CommunityConfigApply.ConfigApplyResult(false, "Couldn't download that config. Check your connection and try again.")
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return CommunityConfigApply.ConfigApplyResult(false, "That config is damaged and can't be read.")
        }
        return withContext(Dispatchers.IO) {
            ensureSnapshot()
            CommunityConfigApply.apply(
                shortcut = shortcut,
                config = ConfigTranslator.translate(json),
                installed = InstalledComponents.read(ctx),
                containerWineVersion = shortcut.container?.wineVersion,
                isAdreno = GPUInformation.isAdrenoGPU(ctx),
            )
        }
    }
}

/** Set by [ProvideGameDoctor]: call it with a shortcut to open the doctor for it. Null where nothing hosts it. */
internal val LocalGameDoctor = staticCompositionLocalOf<((Shortcut) -> Unit)?> { null }

/** Hosts the doctor dialog once for everything below, so any screen's menu can open it with [LocalGameDoctor]. */
@Composable
internal fun ProvideGameDoctor(onChanged: () -> Unit = {}, content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<Shortcut?>(null) }
    val open = remember { { shortcut: Shortcut -> target = shortcut } }
    CompositionLocalProvider(LocalGameDoctor provides open) { content() }
    target?.let { GameDoctorDialog(it, onClose = { target = null }, onChanged = onChanged) }
}

/** The doctor as a Deck dialog: D-pad walks the rows, B closes. [onChanged] fires if anything was changed or undone. */
@Composable
internal fun GameDoctorDialog(shortcut: Shortcut, onClose: () -> Unit, onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val session = remember(shortcut.file.absolutePath) { GameDoctorSession(ctx, shortcut, scope) }
    LaunchedEffect(session) { session.load() }
    val close = {
        if (session.changed) onChanged()
        onClose()
    }

    DeckConfigDialog(title = "Game Doctor", subtitle = shortcut.name, onDismiss = close) {
        val r = session.report
        if (r == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            ) {
                CircularProgressIndicator(Modifier.size(26.dp), color = DeckPalette.primary, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading the last run, settings and shared configs…", color = DeckPalette.onSurfaceVariant, fontSize = 14.sp)
            }
            return@DeckConfigDialog
        }
        session.note?.let { DeckConfigNote(it) }
        if (session.changed) {
            DoctorRow(
                label = "Undo all changes", detail = "Back to how it was before the doctor",
                icon = Icons.Filled.History,
                disabled = if (session.busy) "Working…" else null,
            ) { session.undo { onChanged(); onClose() } }
        }
        r.findings.forEach { f ->
            DoctorHeading(
                when (f.level) {
                    GameDoctor.Level.PROBLEM -> "Problem"
                    GameDoctor.Level.WARNING -> "Heads up"
                    GameDoctor.Level.TIP -> "Good to know"
                },
            )
            DeckConfigNote("${f.title}. ${f.detail}", error = f.level == GameDoctor.Level.PROBLEM)
            f.tweaks.forEach { t ->
                DoctorRow(
                    label = t.label, detail = t.detail, icon = Icons.Filled.Build,
                    disabled = when {
                        t.key in session.applied -> "Applied"
                        session.busy -> "Working…"
                        else -> null
                    },
                ) { session.runTweak(t) }
            }
        }
        if (r.picks.isNotEmpty()) {
            DoctorHeading("Shared configs for this game")
            r.picks.forEach { p ->
                val parts = listOf(
                    p.entry.driver.takeIf { it.isNotBlank() }?.let { "Driver $it" },
                    p.entry.dxvk.takeIf { it.isNotBlank() }?.let { "DXVK $it" },
                    p.entry.vkd3d.takeIf { it.isNotBlank() }?.let { "VKD3D $it" },
                    p.entry.fex.takeIf { it.isNotBlank() }?.let { "FEX $it" },
                ).filterNotNull().joinToString(" · ")
                DoctorRow(
                    label = listOf(p.entry.device, p.entry.soc).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Shared config" } +
                        if (p.matchesDevice) "  ✓ your device" else "",
                    detail = parts.ifBlank { null }, icon = Icons.Filled.Download,
                    disabled = when {
                        session.pickKey(p) in session.applied -> "Applied"
                        session.busy -> "Working…"
                        else -> null
                    },
                ) { session.runPick(p) }
            }
        }
        if (r.tweaks.isNotEmpty()) {
            DoctorHeading("Try something else")
            r.tweaks.forEach { t ->
                DoctorRow(
                    label = t.label, detail = t.detail, icon = Icons.Filled.Build,
                    disabled = when {
                        t.key in session.applied -> "Applied"
                        session.busy -> "Working…"
                        else -> null
                    },
                ) { session.runTweak(t) }
            }
        }
    }
}

@Composable
private fun DoctorHeading(text: String) {
    Text(
        text = text.uppercase(),
        color = DeckPalette.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp),
    )
}

/** One tappable fix. [disabled] doubles as the reason shown under it, so state never rests on colour alone. */
@Composable
private fun DoctorRow(label: String, detail: String?, icon: ImageVector, disabled: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(DeckPalette.surfaceVariant.copy(alpha = if (disabled == null) 0.6f else 0.3f))
            .then(
                if (disabled == null) Modifier.clickable(onClick = onClick)
                else Modifier.focusProperties { canFocus = false },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = DeckPalette.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (detail != null) Text(detail, color = DeckPalette.onSurfaceVariant, fontSize = 12.5.sp)
            if (disabled != null) Text(disabled, color = DeckPalette.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
