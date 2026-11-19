package com.winhub.hernandez.ui.dialogs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.winhub.hernandez.ui.XServerDialogState
import com.winhub.hernandez.ui.components.TextButton
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog

/**
 * Shown once per container, before its first launch on Wayland. Wine sets the container up the first
 * time it runs, which takes a while with only the wallpaper and taskbar showing; closing the app in
 * that window can hang it. Back / B counts as Cancel.
 */
@Composable
fun FirstWaylandLaunchDialog(state: XServerDialogState) {
    fun choose(launch: Boolean) {
        val callback = state.onFirstWaylandLaunchChoice
        state.onFirstWaylandLaunchChoice = null
        state.dismiss()
        callback?.invoke(launch)
    }

    OutlinedAlertDialog(
        onDismissRequest = { choose(false) },
        title = { Text("First launch of this container") },
        text = {
            Text(
                "Wine sets this container up the first time it runs on Wayland. That can take about 30 " +
                    "seconds, and you will only see the wallpaper and taskbar until it is done. It happens " +
                    "once per container.\n\nPlease don't close the app while it works. Closing it early " +
                    "can make the app hang.",
            )
        },
        confirmButton = { TextButton(onClick = { choose(true) }) { Text("Launch") } },
        dismissButton = { TextButton(onClick = { choose(false) }) { Text("Cancel") } },
    )
}
