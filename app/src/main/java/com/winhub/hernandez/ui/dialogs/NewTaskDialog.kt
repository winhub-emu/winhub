package com.winhub.hernandez.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.screens.OutlinedAlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.winhub.hernandez.R
import com.winhub.hernandez.ui.XServerDialogState

// Compose replacement for the old native ContentDialog.prompt(R.string.new_task, "taskmgr.exe").
// The native prompt does not render over the Vulkan/ASR fullscreen SurfaceView, so the New Task
// box was invisible on the default renderer. This AlertDialog reads colorScheme like the other
// host dialogs and runs the exact same submit path on OK (onTmNewTaskSubmit -> winHandler.exec).
@Composable
fun NewTaskDialog(state: XServerDialogState) {
    var command by remember { mutableStateOf("taskmgr.exe") }

    OutlinedAlertDialog(
        onDismissRequest = { state.dismiss() },
        title = { Text(stringResource(R.string.new_task)) },
        text = {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cmd = command.trim()
                if (cmd.isNotEmpty()) state.onTmNewTaskSubmit?.invoke(cmd)
                state.dismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = { state.dismiss() }) { Text("Cancel") }
        }
    )
}
