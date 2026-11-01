package com.winhub.hernandez.ui.dialogs

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.winhub.hernandez.inputcontrols.ExternalController
import com.winhub.hernandez.inputcontrols.InputControlsManager
import com.winhub.hernandez.ui.XServerDialogState
import com.winhub.hernandez.ui.controllertest.ControllerTestPanel
import com.winhub.hernandez.ui.controllertest.PadArt
import com.winhub.hernandez.ui.controllertest.TestBindToggle
import com.winhub.hernandez.ui.controllertest.VisualControllerBinder
import com.winhub.hernandez.ui.controllertest.padArtOf
import com.winhub.hernandez.ui.screens.MenuItemDivider
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import kotlin.math.min
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// In-game Controls > Players — landscape controller-test popup. The LEFT rail keeps every per-device
// slot control (Auto/P1-4/Ignore dropdown, status subtitle, the OSC row, global Reset Input) and adds
// per-card Identify + Reset; the RIGHT half is the shared ControllerTestPanel (see ui/controllertest),
// fed by the throwaway snapshot the Activity forks while this popup is open.
//
// WHY A Dialog WINDOW: the game renders into a SurfaceView composited ABOVE the host ComposeView, so an
// inline Box would hide behind the frame. The window is FLAG_NOT_FOCUSABLE (so gamepad key/axis events
// fall THROUGH to the Activity's dispatch chokepoints, where the test-mode fork feeds the visualizer)
// but stays TOUCHABLE (finger taps drive this UI). Isolation is armed via onControllerTestActive tied
// to visibility, so the game's real input path is byte-for-byte unchanged whenever the popup is closed.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun ControllerTestDialog(state: XServerDialogState) {
    // Arm/disarm input isolation with the popup's visibility. onDispose also fires on a normal close;
    // the Activity ALSO clears the flag in onPause/onStop for the background-SIGSTOP case.
    DisposableEffect(Unit) {
        state.onControllerTestActive?.invoke(true)
        onDispose {
            state.clearControllerTestSnapshot()
            state.onControllerTestActive?.invoke(false)
        }
    }
    // Devices hot-plug — pull a fresh snapshot when the popup opens.
    LaunchedEffect(Unit) { state.onPlayerSlotsRefresh?.run() }

    // Test ↔ Bind. TEST keeps FLAG_NOT_FOCUSABLE (gamepad falls through to the game Activity's fork for
    // the live highlight + isolation); BIND clears it so the profile-name text field can take focus.
    var bindMode by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { state.dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                if (bindMode) clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                else addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0f)
            }
        }
        ControllerTestScaffold(state, bindMode) { bindMode = it }
    }
}

@Composable
private fun ControllerTestScaffold(
    state: XServerDialogState,
    bindMode: Boolean,
    onBindModeChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    // Neat floating panel (not a fill), mirroring the LaunchMethodSheet landscape scaffold's fit-cap +
    // 16dp margin. Smaller/DPI-friendlier than the original 940×470. Both halves scroll and the pad
    // Canvas is aspect-ratio scaled, so nothing clips unreachably at the reduced size.
    val dialogW = min(720, cfg.screenWidthDp - 32).coerceAtLeast(320).dp
    val dialogH = min(412, cfg.screenHeightDp - 32).coerceAtLeast(240).dp

    var selected by remember { mutableStateOf<String?>(null) }

    // Full-screen scrim that consumes stray taps so they don't poke the game behind the popup.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(16.dp).width(dialogW).height(dialogH)
                .border(1.dp, cs.outline, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            color = cs.surface,
            contentColor = cs.onSurface,
            shadowElevation = 26.dp,
        ) {
            Row(Modifier.fillMaxSize()) {
                PlayerRail(
                    state = state,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(min(320, (dialogW.value * 0.42f).roundToInt()).dp)
                        .background(cs.surfaceVariant.copy(alpha = 0.35f))
                        .padding(12.dp)
                )
                // Vertical divider between the rail and the test/bind panel.
                Box(Modifier.fillMaxHeight().width(1.dp).background(cs.outline))
                Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TestBindToggle(bindMode, onBindModeChange)
                        Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    if (bindMode) {
                        InGameBindPanel(state, Modifier.weight(1f).fillMaxWidth())
                    } else {
                        TestPanel(state = state, selected = selected, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ── LEFT RAIL — player/device cards (superset of the old Players sub-tab) ────────────────────────
@Composable
private fun PlayerRail(
    state: XServerDialogState,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val cs = MaterialTheme.colorScheme
    val rows by state.playerSlots.collectAsState()

    // Keep a valid selection as the list changes (default to the first game controller, else first row).
    LaunchedEffect(rows) {
        if (rows.none { it.descriptor == selected }) {
            onSelect(
                rows.firstOrNull { it.isGameController }?.descriptor
                    ?: rows.firstOrNull()?.descriptor
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        Text("Player slots", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "Pads emulate as Xbox 360 to the game; the picture shows the physical pad you're holding.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (rows.isEmpty()) {
                Text(
                    "No input devices detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            } else {
                rows.forEach { row ->
                    PlayerCard(row, row.descriptor == selected, state) { onSelect(row.descriptor) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        // Global "Reset Input" — rebuild the whole fake-input transport in place (existing recovery).
        OutlinedButton(
            onClick = {
                state.onResetInput?.run()
                state.onPlayerSlotsRefresh?.run()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        ) { Text("Reset Input") }
        Text(
            "Re-handshake controllers & on-screen if input stops responding.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayerCard(
    row: XServerDialogState.PlayerSlotRow,
    selected: Boolean,
    state: XServerDialogState,
    onSelect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    val subtitle = when {
        row.currentSlot >= 0 -> "Currently Player ${row.currentSlot + 1}"
        row.override == XServerDialogState.SLOT_IGNORE -> "Ignored"
        else -> "Unassigned"
    } + if (row.isOnScreen) " · on-screen" else ""

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else cs.surface)
            .border(1.dp, if (selected) accent else cs.outline, RoundedCornerShape(12.dp))
            .clickable { onSelect() }
            .padding(10.dp)
    ) {
        Text(
            row.displayName,
            color = cs.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        SlotPicker(row, state)

        // Identify + per-card Reset appear on the selected card only, to keep the rail uncluttered.
        if (selected) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { state.onControllerIdentify?.invoke(row.currentSlot) },
                    enabled = row.currentSlot >= 0 && row.hasVibrator,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                ) { Text("Identify", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = {
                        // Per-card reset: clear this device's override → Auto, then rebuild the pipeline.
                        state.onPlayerSlotChanged?.invoke(row.descriptor, XServerDialogState.SLOT_AUTO)
                        state.onResetInput?.run()
                        state.onPlayerSlotsRefresh?.run()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                ) { Text("Reset", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun SlotPicker(row: XServerDialogState.PlayerSlotRow, state: XServerDialogState) {
    val options = remember {
        buildList {
            add("Auto" to XServerDialogState.SLOT_AUTO)
            for (i in 0 until 4) add("Player ${i + 1}" to i)
            add("Ignore" to XServerDialogState.SLOT_IGNORE)
        }
    }
    val selectedLabel = options.firstOrNull { it.second == row.override }?.first ?: "Auto"
    var expanded by remember(row.descriptor, row.override) { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
        ) { Text("Slot: $selectedLabel", fontSize = 12.sp) }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt.first) }, onClick = {
                    expanded = false
                    if (opt.second != row.override) {
                        state.onPlayerSlotChanged?.invoke(row.descriptor, opt.second)
                    }
                })
                if (i < options.lastIndex) MenuItemDivider()
            }
        }
    }
}

// The RIGHT half — the shared visualizer, fed by the in-game snapshot flow + the selected device row.
@Composable
private fun TestPanel(state: XServerDialogState, selected: String?, modifier: Modifier) {
    val rows by state.playerSlots.collectAsState()
    val snap by state.controllerTestSnapshot.collectAsState()
    val selectedRow = rows.firstOrNull { it.descriptor == selected }
    var manualArt by remember(selected) { mutableStateOf<PadArt?>(null) }

    ControllerTestPanel(
        snapshot = snap,
        title = "Controller Test",
        subtitle = selectedRow?.displayName ?: "No controller selected",
        resetKey = selected,
        manualArt = manualArt,
        onArtChange = { manualArt = it },
        identifyEnabled = (selectedRow?.currentSlot ?: -1) >= 0 && (selectedRow?.hasVibrator == true),
        onIdentify = { state.onControllerIdentify?.invoke(selectedRow?.currentSlot ?: -1) },
        onDone = { state.dismiss() },
        modifier = modifier,
        nameHint = selectedRow?.displayName,
    )
}

// The RIGHT half in BIND mode — the shared visual binder, editing a profile chosen in-game (defaults
// to the profile the game is running). Profiles come from InputControlsManager; create/rename happen
// here without leaving the game.
@Composable
private fun InGameBindPanel(state: XServerDialogState, modifier: Modifier) {
    val context = LocalContext.current
    val snap by state.controllerTestSnapshot.collectAsState()
    val manager = remember { InputControlsManager(context) }
    var profilesRev by remember { mutableIntStateOf(0) }
    val profiles = remember(profilesRev) { manager.getProfiles().toList() }
    // The Bind tab is the PHYSICAL lane: seed from the ACTIVE physical profile (not the OSC one) so the
    // picker reflects what's live on the pad. -1 = — None — (native passthrough) → edit nothing.
    var selectedProfileId by remember { mutableStateOf(state.physicalProfileId) }
    val selectedProfile = remember(profilesRev, selectedProfileId) {
        if (selectedProfileId < 0) null
        else profiles.firstOrNull { it.id == selectedProfileId } ?: profiles.firstOrNull()
    }
    val padName = remember { ExternalController.getControllers().firstOrNull()?.name }
    var renaming by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }

    VisualControllerBinder(
        profile = selectedProfile,
        art = padArtOf(snap, padName),
        snapshot = snap,
        allProfiles = profiles,
        // The Bind picker IS the physical-lane activation control: selecting a profile activates it on
        // the physical pad (and edits it); — None — reverts the pad to native Xbox passthrough. Both are
        // independent of the OSC lane (Touch tab), so the two run simultaneously with no clobber.
        onSelectProfile = { p ->
            selectedProfileId = p.id
            state.onPhysicalProfileChanged?.invoke(p.id)
        },
        onSelectNone = {
            selectedProfileId = -1
            state.onPhysicalProfileChanged?.invoke(-1)
        },
        onCreateProfile = { name ->
            val p = manager.createProfile(name)
            selectedProfileId = p.id
            profilesRev++
            state.onPhysicalProfileChanged?.invoke(p.id)   // activate the new profile on the pad
            state.onProfilesChanged?.run()                 // keep the Touch dropdown live
        },
        onRenameProfile = { renameText = selectedProfile?.name ?: ""; renaming = true },
        onDeleteProfile = { deleting = true },
        // A live binding edit saves to disk on the binder's OWN manager instance, but the ACTIVE
        // physical lane holds a separate instance resolved off disk at activation — so re-apply it to
        // pull the just-saved bindings into the running pad (onPhysicalProfileChanged re-reads fresh).
        onSaved = { if (selectedProfileId >= 0) state.onPhysicalProfileChanged?.invoke(selectedProfileId) },
        onOpenAllBindings = null,
        modifier = modifier,
    )

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = selectedProfile
                    if (p != null && renameText.isNotBlank()) {
                        p.name = renameText.trim(); p.save(); profilesRev++
                        state.onProfilesChanged?.run()   // keep the Touch dropdown live after a rename
                    }
                    renaming = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }

    if (deleting) {
        val p = selectedProfile
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete profile") },
            text = { Text("Delete \"${p?.name ?: ""}\"? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (p != null) {
                        val wasActive = selectedProfileId == p.id
                        manager.removeProfile(p)
                        selectedProfileId = -1
                        profilesRev++
                        if (wasActive) state.onPhysicalProfileChanged?.invoke(-1) // revert pad to native Xbox
                        state.onProfilesChanged?.run()                            // keep the Touch list live
                    }
                    deleting = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
        )
    }
}
