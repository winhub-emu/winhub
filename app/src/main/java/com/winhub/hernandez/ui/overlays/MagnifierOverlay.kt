package com.winhub.hernandez.ui.overlays

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winhub.hernandez.ui.components.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.winhub.hernandez.ui.XServerDialogState
import kotlin.math.roundToInt

@Composable
fun MagnifierOverlay(state: XServerDialogState) {
    val zoom by state.magnifierZoom.collectAsState()

    var offsetX by remember { mutableFloatStateOf(100f) }
    var offsetY by remember { mutableFloatStateOf(100f) }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                // A Compose Dialog window applies FLAG_DIM_BEHIND by default, which dims the WHOLE
                // game surface behind the tiny magnifier control. Clear it (+ dimAmount 0) so the
                // magnified frame stays at full brightness — matches PauseBoxOverlay. Renderer-
                // independent: this is a window flag over the game surface, so it fixes GL AND Vulkan.
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                )
                setDimAmount(0f)
                setGravity(Gravity.TOP or Gravity.START)
                val attrs = attributes
                attrs.x = offsetX.roundToInt()
                attrs.y = offsetY.roundToInt()
                attributes = attrs
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .wrapContentSize()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            Text(
                text = "Magnifier",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${(zoom * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { state.onMagnifierZoom?.invoke(-0.25f) }) {
                    Text("−")
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = { state.onMagnifierZoom?.invoke(0.25f) }) {
                    Text("+")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { state.onMagnifierHide?.run() }) {
                    Text("✕", fontSize = 12.sp)
                }
            }
        }
    }
}
