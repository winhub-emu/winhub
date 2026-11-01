package com.winhub.hernandez.ui.overlays

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.winhub.hernandez.ui.XServerDialogState

// Centered "Frame generation changed — Resume" card, shown while the guest is frozen and the game
// surface is torn down after an in-game frame-gen change. Tapping Resume fires onFgResetResume, which
// rebuilds the surface (fresh swapchain) and SIGCONTs the guest so frame gen restarts into a clean,
// non-over-queued state (device-proven: only a full surface teardown clears the LSFG black flicker).
//
// WHY A Dialog WINDOW (not an inline Box in the dialog-host ComposeView): the game renders into a
// SurfaceView that composites ABOVE the host ComposeView's window, so anything drawn inline is hidden
// behind it. A Dialog escapes into its own top-level window the compositor stacks above the game
// surface — same trick as PauseBoxOverlay/MagnifierOverlay. Here we KEEP the dim scrim (the surface
// behind is torn down / black) so the pause reads as intentional, and make it modal so a stray touch
// on the (blank) game area can't leak through while paused. Back / tap-outside are inert — the only
// way out is Resume, which guarantees the surface is rebuilt before input returns to the game.
@Composable
fun FgResetOverlay(state: XServerDialogState) {
    Dialog(
        onDismissRequest = { /* inert: resume only via the button, so the surface always rebuilds */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                setGravity(Gravity.CENTER)
                setDimAmount(0.6f)
            }
        }
        FgResetCard(onResume = { state.onFgResetResume?.run() })
    }
}

@Composable
private fun FgResetCard(onResume: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.wrapContentSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp)
        ) {
            Text(
                "Frame generation updated",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Restarting the display so the change applies cleanly.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onResume) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Resume", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
