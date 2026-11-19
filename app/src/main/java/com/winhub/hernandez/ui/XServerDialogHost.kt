package com.winhub.hernandez.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import com.winhub.hernandez.ui.dialogs.ActiveWindowsDialog
import com.winhub.hernandez.ui.dialogs.CastDialog
import com.winhub.hernandez.ui.dialogs.ControllerTestDialog
import com.winhub.hernandez.ui.dialogs.DebugDialogContent
import com.winhub.hernandez.ui.dialogs.FirstWaylandLaunchDialog
import com.winhub.hernandez.ui.dialogs.InputControlsDialog
import com.winhub.hernandez.ui.dialogs.NewTaskDialog
import com.winhub.hernandez.ui.dialogs.VibrationDialog
import com.winhub.hernandez.ui.overlays.AchievementPillStackOverlay
import com.winhub.hernandez.ui.overlays.ControllerToastOverlay
import com.winhub.hernandez.ui.overlays.ExternalModeOverlay
import com.winhub.hernandez.ui.overlays.FgResetOverlay
import com.winhub.hernandez.ui.overlays.MagnifierOverlay
import com.winhub.hernandez.ui.overlays.PauseBoxOverlay
import com.winhub.hernandez.ui.theme.WinlatorTheme

fun setupDialogHost(view: ComposeView) {
    view.setContent {
        WinlatorTheme {
            XServerDialogHost()
        }
    }
}

@Composable
fun XServerDialogHost() {
    val state = XServerDialogState
    val activeDialog     by state.activeDialog.collectAsState()
    val magnifierVisible by state.magnifierVisible.collectAsState()
    val paused           by state.paused.collectAsState()
    val controllerToast  by state.controllerToast.collectAsState()
    val playingOnExternal by state.playingOnExternal.collectAsState()
    val menuOpen by state.menuOpen.collectAsState()
    val fgResetPaused by state.fgResetPaused.collectAsState()
    val achievementPills by state.achievementPills.collectAsState()
    when (activeDialog) {
        XServerDialogState.ActiveDialog.VIBRATION      -> VibrationDialog(state)
        XServerDialogState.ActiveDialog.DEBUG          -> DebugDialogContent(state)
        XServerDialogState.ActiveDialog.INPUT_CONTROLS -> InputControlsDialog(state)
        XServerDialogState.ActiveDialog.ACTIVE_WINDOWS -> ActiveWindowsDialog(state)
        XServerDialogState.ActiveDialog.NEW_TASK       -> NewTaskDialog(state)
        XServerDialogState.ActiveDialog.CAST           -> CastDialog(state)
        XServerDialogState.ActiveDialog.CONTROLLER_TEST -> ControllerTestDialog(state)
        XServerDialogState.ActiveDialog.FIRST_WAYLAND_LAUNCH -> FirstWaylandLaunchDialog(state)
        XServerDialogState.ActiveDialog.NONE           -> Unit
    }

    // On-handheld "game is on the TV" indicator (the phone would otherwise be a black screen).
    // Hidden while the side menu is open so it doesn't overlap the drawer.
    if (playingOnExternal && !menuOpen) ExternalModeOverlay(state)

    if (magnifierVisible) MagnifierOverlay(state)

    // Centered pause indicator — above the game surface, shown whenever the guest is frozen
    // (ReShade freeze-frame preview OR a manual Pause). Tap to fully resume.
    if (paused) PauseBoxOverlay(state)

    // Frame-gen change → full presentation reset: the guest is frozen and the surface torn down;
    // tapping Resume rebuilds the surface + resumes the guest so frame gen restarts clean.
    if (fgResetPaused) FgResetOverlay(state)

    // Controller-status toast (P5b): top-right, below the Fusion HUD; non-interactive, auto-dismissing.
    if (controllerToast != null) ControllerToastOverlay(state)

    // Achievement pill stack: gold cards down the top-right when a Steam achievement unlocks in-game;
    // non-interactive, each pill auto-dismisses on its own ~4.5s lifecycle.
    if (achievementPills.isNotEmpty()) AchievementPillStackOverlay(state)
}
