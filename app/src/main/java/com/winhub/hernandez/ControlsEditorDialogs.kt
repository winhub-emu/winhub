package com.winhub.hernandez

import androidx.compose.foundation.BorderStroke
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winhub.hernandez.inputcontrols.ControlElement
import com.winhub.hernandez.inputcontrols.ControlsProfile
import com.winhub.hernandez.ui.theme.AppThemeState
import kotlin.math.roundToInt

const val DIALOG_ADD_ELEMENT = "add_element"
const val DIALOG_BACKGROUND_IMAGE = "background_image"
const val DIALOG_GROUP_VISIBILITY = "group_visibility"
const val DIALOG_CUSTOM_ICON_SOURCE = "custom_icon_source"

private val DialogBackground get() = AppThemeState.deckPreset.surface
private val DialogSurface get() = AppThemeState.deckPreset.surfaceVariant
private val DialogAccent get() = AppThemeState.deckPreset.primary
private val DialogText = Color.White
private val DialogSubText = Color.White.copy(alpha = 0.72f)
private val DialogShape = RoundedCornerShape(10.dp)

interface ControlsEditorDialogActions {
    fun onDismiss()
    fun onAddElement(type: ControlElement.Type)
    fun onAddAndroidKeyboardButton()
    fun onPickBackgroundFile()
    fun onPickBackgroundSystem()
    fun onClearBackground()
    fun onBackgroundOpacityChange(opacity: Float)
    fun onGroupVisibilityChange(groupName: String, visible: Boolean)
    fun onPickIconFile()
    fun onPickIconSystem()
}

@Composable
fun ControlsEditorDialogHost(
    dialogMode: String?,
    profile: ControlsProfile?,
    backgroundOpacity: Float,
    actions: ControlsEditorDialogActions,
) {
    MaterialTheme(colorScheme = controlsEditorDialogColorScheme()) {
        when (dialogMode) {
            DIALOG_ADD_ELEMENT -> AddElementDialog(
                onDismiss = actions::onDismiss,
                onAddElement = actions::onAddElement,
                onAddAndroidKeyboardButton = actions::onAddAndroidKeyboardButton,
            )
            DIALOG_BACKGROUND_IMAGE -> BackgroundImageDialog(
                backgroundOpacity = backgroundOpacity,
                onDismiss = actions::onDismiss,
                onPickBackgroundFile = actions::onPickBackgroundFile,
                onPickBackgroundSystem = actions::onPickBackgroundSystem,
                onClearBackground = actions::onClearBackground,
                onBackgroundOpacityChange = actions::onBackgroundOpacityChange,
            )
            DIALOG_GROUP_VISIBILITY -> GroupVisibilityDialog(
                profile = profile,
                onDismiss = actions::onDismiss,
                onGroupVisibilityChange = actions::onGroupVisibilityChange,
            )
            DIALOG_CUSTOM_ICON_SOURCE -> CustomIconSourceDialog(
                onDismiss = actions::onDismiss,
                onPickIconFile = actions::onPickIconFile,
                onPickIconSystem = actions::onPickIconSystem,
            )
        }
    }
}

@Composable
private fun AddElementDialog(
    onDismiss: () -> Unit,
    onAddElement: (ControlElement.Type) -> Unit,
    onAddAndroidKeyboardButton: () -> Unit,
) {
    val items = remember {
        listOf(
            ControlTypeItem(ControlElement.Type.BUTTON, R.drawable.icon_keyboard, R.string.control_type_button),
            ControlTypeItem(ControlElement.Type.BUTTON, R.drawable.icon_keyboard, R.string.control_type_android_keyboard, true),
            ControlTypeItem(ControlElement.Type.D_PAD, R.drawable.icon_gamepad, R.string.control_type_d_pad),
            ControlTypeItem(ControlElement.Type.RANGE_BUTTON, R.drawable.icon_screen_effect, R.string.control_type_range_button),
            ControlTypeItem(ControlElement.Type.STICK, R.drawable.icon_gamepad, R.string.control_type_stick),
            ControlTypeItem(ControlElement.Type.TRACKPAD, R.drawable.icon_mouse, R.string.control_type_trackpad),
            ControlTypeItem(ControlElement.Type.DYNAMIC_STICK, R.drawable.icon_gamepad, R.string.control_type_dynamic_stick),
            ControlTypeItem(ControlElement.Type.MOUSE_AREA, R.drawable.icon_mouse, R.string.control_type_mouse_area),
            ControlTypeItem(ControlElement.Type.BUTTON_GRID, R.drawable.icon_palette, R.string.control_type_button_grid),
            ControlTypeItem(ControlElement.Type.EXPANDABLE_BUTTON, R.drawable.icon_gamepad, R.string.control_type_expandable_button),
        )
    }

    EditorAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.select_control_type),
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items.chunked(2)) { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { item ->
                            ControlTypeCard(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (item.androidKeyboard) onAddAndroidKeyboardButton()
                                    else onAddElement(item.type)
                                },
                            )
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel), color = DialogAccent)
            }
        },
    )
}

@Composable
private fun ControlTypeCard(
    item: ControlTypeItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = DialogSurface,
        shape = DialogShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
            .heightIn(min = 86.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = stringResource(item.labelRes),
                tint = if (item.type == ControlElement.Type.DYNAMIC_STICK) Color(0xFF8B5CF6) else DialogText,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = stringResource(item.labelRes),
                color = DialogText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BackgroundImageDialog(
    backgroundOpacity: Float,
    onDismiss: () -> Unit,
    onPickBackgroundFile: () -> Unit,
    onPickBackgroundSystem: () -> Unit,
    onClearBackground: () -> Unit,
    onBackgroundOpacityChange: (Float) -> Unit,
) {
    var opacity by remember(backgroundOpacity) { mutableStateOf(backgroundOpacity.coerceIn(0f, 1f)) }
    val opacityPercent = (opacity * 100f).roundToInt().coerceIn(0, 100)

    EditorAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.set_background_image),
        text = {
            DialogActionColumn {
                DialogActionButton(text = stringResource(R.string.browse_files), onClick = onPickBackgroundFile)
                DialogActionButton(text = stringResource(R.string.pick_via_system), onClick = onPickBackgroundSystem)
                DialogActionButton(text = stringResource(R.string.clear_background), onClick = onClearBackground)
                Text(
                    text = stringResource(R.string.opacity_percent, opacityPercent),
                    color = DialogSubText,
                    fontWeight = FontWeight.Medium,
                )
                Slider(
                    value = opacity,
                    valueRange = 0f..1f,
                    onValueChange = { value ->
                        opacity = value.coerceIn(0f, 1f)
                        onBackgroundOpacityChange(opacity)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok), color = DialogAccent)
            }
        },
    )
}

@Composable
private fun GroupVisibilityDialog(
    profile: ControlsProfile?,
    onDismiss: () -> Unit,
    onGroupVisibilityChange: (String, Boolean) -> Unit,
) {
    val groups = remember(profile, profile?.getGroups()?.size) {
        profile?.getGroups()?.values?.toList().orEmpty()
    }

    EditorAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.group_visibility),
        text = {
            if (groups.isEmpty()) {
                Text(text = stringResource(R.string.no_groups), color = DialogSubText)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.name }) { group ->
                        GroupVisibilityRow(
                            name = group.name,
                            count = profile?.getGroupElementCount(group.name) ?: 0,
                            visible = group.isVisible,
                            onVisibilityChange = { visible -> onGroupVisibilityChange(group.name, visible) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok), color = DialogAccent)
            }
        },
    )
}

@Composable
private fun GroupVisibilityRow(
    name: String,
    count: Int,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
) {
    var currentVisibility by remember(name, visible) { mutableStateOf(visible) }

    Surface(
        color = DialogSurface,
        shape = DialogShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(if (currentVisibility) R.drawable.icon_eye else R.drawable.icon_eye_off),
                contentDescription = stringResource(R.string.group_visibility),
                tint = DialogText,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, color = DialogText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = stringResource(R.string.group_element_count, count), color = DialogSubText)
            }
            Switch(
                checked = currentVisibility,
                onCheckedChange = {
                    currentVisibility = it
                    onVisibilityChange(it)
                },
            )
        }
    }
}

@Composable
private fun CustomIconSourceDialog(
    onDismiss: () -> Unit,
    onPickIconFile: () -> Unit,
    onPickIconSystem: () -> Unit,
) {
    EditorAlertDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.select_icon_image),
        text = {
            DialogActionColumn {
                DialogActionButton(text = stringResource(R.string.browse_files), onClick = onPickIconFile)
                DialogActionButton(text = stringResource(R.string.pick_via_system), onClick = onPickIconSystem)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel), color = DialogAccent)
            }
        },
    )
}

@Composable
private fun EditorAlertDialog(
    onDismiss: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = DialogText, fontWeight = FontWeight.Bold) },
        text = text,
        confirmButton = confirmButton,
        containerColor = DialogBackground,
        titleContentColor = DialogText,
        textContentColor = DialogSubText,
    )
}

@Composable
private fun DialogActionColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
private fun DialogActionButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DialogShape,
        colors = ButtonDefaults.textButtonColors(contentColor = DialogAccent),
    ) {
        Text(text = text, color = DialogAccent)
    }
}

@Composable
private fun controlsEditorDialogColorScheme() = darkColorScheme(
    primary = DialogAccent,
    onPrimary = Color.Black,
    background = DialogBackground,
    onBackground = DialogText,
    surface = DialogBackground,
    onSurface = DialogText,
    surfaceVariant = DialogSurface,
    onSurfaceVariant = DialogSubText,
    outline = Color.White.copy(alpha = 0.24f),
)

private data class ControlTypeItem(
    val type: ControlElement.Type,
    val iconRes: Int,
    val labelRes: Int,
    val androidKeyboard: Boolean = false,
)
