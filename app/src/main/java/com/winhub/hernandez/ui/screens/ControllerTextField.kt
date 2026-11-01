package com.winhub.hernandez.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text input that a gamepad can walk past: the field is read-only (and focus lands on a stroked
 * overlay) until A/Enter starts editing; B/Back stops editing and returns to the same spot. Same
 * model as [DeckSearchField], themed with MaterialTheme so it fits the classic Social screens.
 */
@Composable
internal fun ControllerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minHeight: Dp = 48.dp,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val edit = rememberDeckEditState()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            readOnly = !edit.editing,
            textStyle = TextStyle(color = cs.onSurface, fontSize = 14.sp),
            cursorBrush = SolidColor(cs.primary),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onSend = { onImeAction() }, onDone = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .focusProperties { canFocus = edit.editing && enabled }
                .focusRequester(edit.fieldFocus)
                .onFocusChanged { if (edit.editing && !it.isFocused) edit.editing = false }
                .onPreviewKeyEvent { event ->
                    if (edit.editing && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.ButtonB || event.key == Key.Back)
                    ) {
                        edit.returnFocus = true
                        edit.editing = false
                        true
                    } else false
                }
                .clip(shape)
                .background(cs.surfaceVariant.copy(alpha = 0.45f))
                .border(if (focused) 2.dp else 1.dp, if (focused) cs.primary else cs.outline.copy(alpha = 0.45f), shape),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (value.isEmpty()) {
                        Text(hint, fontSize = 14.sp, color = cs.onSurfaceVariant)
                    }
                    inner()
                }
            },
        )
        if (!edit.editing && enabled) DeckEditOverlay(edit, Modifier.matchParentSize(), ring = shape)
    }
}
