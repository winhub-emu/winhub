package com.winhub.hernandez

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.OutlinedTextField
import com.winhub.hernandez.ui.components.RadioButton
import androidx.compose.material3.Surface
import com.winhub.hernandez.ui.components.Switch
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.winhub.hernandez.inputcontrols.Binding
import com.winhub.hernandez.inputcontrols.ControlElement
import com.winhub.hernandez.inputcontrols.ControlsProfile
import com.winhub.hernandez.inputcontrols.CustomIconManager
import com.winhub.hernandez.inputcontrols.InputControlsManager
import com.winhub.hernandez.ui.components.ColorPicker
import com.winhub.hernandez.ui.theme.AppThemeState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val EditorMaxSliderWidth = 300.dp
private val EditorPadding = 12.dp
private val EditorSpacing = 8.dp
private val EditorBackground get() = AppThemeState.deckPreset.surface
private val EditorSurface get() = AppThemeState.deckPreset.surfaceVariant
private val EditorAccent get() = AppThemeState.deckPreset.primary
private val EditorText = Color.White
private val EditorSubText = Color.White.copy(alpha = 0.7f)
private val EditorTextValue = Color.White.copy(alpha = 0.9f)
private val EditorShape = RoundedCornerShape(EditorPadding)
private val SmallShape = RoundedCornerShape(10.dp)
private val EditorMinScalePercent = (ControlElement.MIN_SCALE * 100f).roundToInt()
private val EditorMaxScalePercent = (ControlElement.MAX_SCALE * 100f).roundToInt()

private data class PickerIcon(
    val id: Int,
    val bitmap: Bitmap?,
    val isCustom: Boolean = false,
)

private data class CustomIconDeleteRequest(
    val iconId: Int,
    val usage: InputControlsManager.CustomIconUsage?,
    val deleteFailed: Boolean = false,
)

@Composable
fun ControlsEditorSettingsPane(
    element: ControlElement,
    profile: ControlsProfile,
    onInvalidate: () -> Unit,
    customIconManager: CustomIconManager,
    customIconReloadKey: Int,
    context: Context,
    onClose: () -> Unit,
    onPickCustomIcon: () -> Unit,
    onDeleteCustomIcon: (Int) -> Boolean,
) {
    val typeOptions = listOf(
        stringResource(R.string.control_type_button),
        stringResource(R.string.control_type_d_pad),
        stringResource(R.string.control_type_range_button),
        stringResource(R.string.control_type_stick),
        stringResource(R.string.control_type_trackpad),
        stringResource(R.string.control_type_dynamic_stick),
        stringResource(R.string.control_type_mouse_area),
        stringResource(R.string.control_type_button_grid),
        stringResource(R.string.control_type_expandable_button),
    )
    val shapeOptions = listOf(
        stringResource(R.string.control_shape_circle),
        stringResource(R.string.control_shape_rectangle),
        stringResource(R.string.control_shape_rounded_rectangle),
        stringResource(R.string.control_shape_square),
    )
    val rangeOptions = listOf(
        stringResource(R.string.control_range_a_z),
        stringResource(R.string.control_range_0_9),
        stringResource(R.string.control_range_f1_f12),
        stringResource(R.string.control_range_numpad_0_9),
    )
    val expandableLayoutOptions = listOf(
        stringResource(R.string.expandable_layout_radial),
        stringResource(R.string.expandable_layout_list),
    )
    val expandableDirectionOptions = listOf(
        stringResource(R.string.direction_up),
        stringResource(R.string.direction_right),
        stringResource(R.string.direction_down),
        stringResource(R.string.direction_left),
    )
    val bindingOptions = remember { Binding.values().toList() }
    val holdKeyOptions = remember {
        buildList {
            add(context.getString(R.string.none))
            for (binding in Binding.values()) {
                if (binding == Binding.NONE) continue
                if (binding.isKeyboard() || (binding.isMouse() && !binding.isMouseMove())) {
                    add(binding.toString())
                }
            }
        }
    }

    val builtInIcons = remember(context) { loadBuiltInIcons(context) }
    val customIcons = remember(customIconManager, customIconReloadKey) { loadCustomIcons(customIconManager) }

    var typeIndex by remember { mutableStateOf(element.getType().ordinal) }
    var shapeIndex by remember { mutableStateOf(element.getShape().ordinal) }
    var rangeIndex by remember { mutableStateOf(element.getRange().ordinal) }
    var orientationIndex by remember { mutableStateOf(element.getOrientation().toInt().coerceIn(0, 1)) }
    var scaleValue by remember {
        mutableStateOf(
            (element.getScale() * 100f).roundToInt()
                .coerceIn(EditorMinScalePercent, EditorMaxScalePercent)
        )
    }
    var detectionWidth by remember { mutableStateOf(element.getAreaWidth().coerceAtLeast(200)) }
    var detectionHeight by remember { mutableStateOf(element.getAreaHeight().coerceAtLeast(200)) }
    var stickRadius by remember { mutableStateOf(element.getStickRadius().coerceAtLeast(60)) }
    var mouseAreaWidth by remember { mutableStateOf(element.getAreaWidth().coerceAtLeast(200)) }
    var mouseAreaHeight by remember { mutableStateOf(element.getAreaHeight().coerceAtLeast(200)) }
    var mouseSensitivity by remember { mutableStateOf((element.getMouseSensitivity() * 10f).roundToInt().coerceIn(1, 50)) }
    var deadZonePct by remember { mutableStateOf((element.deadZone * 100f).roundToInt().coerceIn(0, 50)) }
    var gridRows by remember { mutableStateOf(element.getGridRows().coerceAtLeast(1)) }
    var gridCols by remember { mutableStateOf(element.getGridCols().coerceAtLeast(1)) }
    var gridCellShapeIndex by remember { mutableStateOf(element.getGridCellShape().ordinal) }
    var gridSpacingPct by remember { mutableStateOf((element.getGridSpacing() * 100f).roundToInt()) }
    var gridMultitouchEnabled by remember { mutableStateOf(element.isGridMultitouchEnabled()) }
    var holdKeyIndex by remember { mutableStateOf(holdKeyOptions.indexOf(element.getHoldKey().toString()).coerceAtLeast(0)) }
    var toggleSwitch by remember { mutableStateOf(element.isToggleSwitch()) }
    var swipeable by remember { mutableStateOf(element.isSwipeable()) }
    var customText by remember { mutableStateOf(element.getText()) }
    var selectedIconId by remember { mutableStateOf(element.getIconId().toInt() and 0xFF) }
    var customIconTintEnabled by remember { mutableStateOf(element.isCustomIconTintEnabled()) }
    var customIconAsButton by remember { mutableStateOf(element.isCustomIconAsButton()) }
    var bindingCount by remember { mutableStateOf(element.getBindingCount().coerceAtLeast(1)) }
    var groupId by remember { mutableStateOf(element.groupId ?: "") }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var bindingDialogIndex by remember(element) { mutableStateOf(-1) }
    var bindingDialogTitle by remember(element) { mutableStateOf("") }
    var pendingTypeIndex by remember(element) { mutableStateOf<Int?>(null) }
    var expandableChildCount by remember { mutableStateOf(element.getExpandableChildCount().coerceAtLeast(1)) }
    var expandableLayoutIndex by remember { mutableStateOf(element.getExpandableLayout().ordinal) }
    var expandableDirectionIndex by remember { mutableStateOf(element.getExpandableDirection().ordinal) }
    var customAreaAppearanceEnabled by remember { mutableStateOf(element.isCustomAreaAppearanceEnabled()) }
    var customAreaColor by remember { mutableStateOf(element.getCustomAreaColor()) }
    var customAreaOpacity by remember { mutableStateOf((element.getCustomAreaOpacity() * 100f).roundToInt()) }
    var showAreaColorPicker by remember { mutableStateOf(false) }
    var customIconDeleteRequest by remember { mutableStateOf<CustomIconDeleteRequest?>(null) }

    fun saveAndInvalidate() {
        val normalizedGroupId = groupId.trim()
        if (normalizedGroupId.isNotBlank() && profile.getGroup(normalizedGroupId) == null) {
            profile.addGroup(normalizedGroupId)
        }
        element.groupId = normalizedGroupId.ifBlank { null }
        element.deadZone = deadZonePct / 100f
        profile.save()
        onInvalidate()
    }

    fun prepareGridForFill() {
        val total = element.getGridRows().coerceAtLeast(1) * element.getGridCols().coerceAtLeast(1)
        if (element.getBindingCount() != total) element.setBindingCount(total)
    }

    fun fillGrid(keys: Array<Binding>) {
        prepareGridForFill()
        for (index in 0 until element.getBindingCount()) {
            element.setBindingAt(index, keys.getOrElse(index) { Binding.NONE })
            element.setCombo(index, null)
        }
        bindingCount = element.getBindingCount().coerceAtLeast(1)
        profile.save()
        onInvalidate()
    }

    val qwertyKeys = remember {
        arrayOf(
            Binding.KEY_Q, Binding.KEY_W, Binding.KEY_E, Binding.KEY_R, Binding.KEY_T, Binding.KEY_Y, Binding.KEY_U, Binding.KEY_I, Binding.KEY_O, Binding.KEY_P,
            Binding.KEY_A, Binding.KEY_S, Binding.KEY_D, Binding.KEY_F, Binding.KEY_G, Binding.KEY_H, Binding.KEY_J, Binding.KEY_K, Binding.KEY_L,
            Binding.KEY_Z, Binding.KEY_X, Binding.KEY_C, Binding.KEY_V, Binding.KEY_B, Binding.KEY_N, Binding.KEY_M,
        )
    }
    val functionKeys = remember {
        arrayOf(
            Binding.KEY_F1, Binding.KEY_F2, Binding.KEY_F3, Binding.KEY_F4,
            Binding.KEY_F5, Binding.KEY_F6, Binding.KEY_F7, Binding.KEY_F8,
            Binding.KEY_F9, Binding.KEY_F10, Binding.KEY_F11, Binding.KEY_F12,
        )
    }
    val numPadKeys = remember {
        arrayOf(
            Binding.KEY_KP_7, Binding.KEY_KP_8, Binding.KEY_KP_9, Binding.KEY_KP_ADD,
            Binding.KEY_KP_4, Binding.KEY_KP_5, Binding.KEY_KP_6, Binding.KEY_MINUS,
            Binding.KEY_KP_1, Binding.KEY_KP_2, Binding.KEY_KP_3, Binding.KEY_ENTER,
            Binding.KEY_KP_0, Binding.KEY_PERIOD, Binding.KEY_BKSP, Binding.KEY_ESC,
        )
    }

    fun openBindingDialog(index: Int, title: String) {
        bindingDialogIndex = index
        bindingDialogTitle = title
    }

    fun saveBindingSlot(index: Int, binding: Binding, combo: List<Binding>, blockTouchscreenMouseButtons: Boolean) {
        val cleanCombo = combo.filter { it != Binding.NONE }.distinct().take(ControlElement.MAX_COMBO_BINDINGS)
        val mainBinding = if (binding != Binding.NONE) binding else cleanCombo.lastOrNull() ?: Binding.NONE
        element.setBindingAt(index, mainBinding)
        element.setCombo(index, if (cleanCombo.isEmpty()) null else cleanCombo.toTypedArray())
        element.setBlocksTouchscreenMouseButtonsAt(index, blockTouchscreenMouseButtons)
        if (mainBinding == Binding.SHOW_ANDROID_KEYBOARD || cleanCombo.contains(Binding.SHOW_ANDROID_KEYBOARD)) {
            element.setToggleSwitch(false)
            toggleSwitch = false
        }
        saveAndInvalidate()
    }

    fun syncFromElement() {
        typeIndex = element.getType().ordinal
        shapeIndex = element.getShape().ordinal
        rangeIndex = element.getRange().ordinal
        orientationIndex = element.getOrientation().toInt().coerceIn(0, 1)
        scaleValue = (element.getScale() * 100f).roundToInt()
            .coerceIn(EditorMinScalePercent, EditorMaxScalePercent)
        detectionWidth = element.getAreaWidth().coerceAtLeast(200)
        detectionHeight = element.getAreaHeight().coerceAtLeast(200)
        stickRadius = element.getStickRadius().coerceAtLeast(60)
        mouseAreaWidth = element.getAreaWidth().coerceAtLeast(200)
        mouseAreaHeight = element.getAreaHeight().coerceAtLeast(200)
        mouseSensitivity = (element.getMouseSensitivity() * 10f).roundToInt().coerceIn(1, 50)
        deadZonePct = (element.deadZone * 100f).roundToInt().coerceIn(0, 50)
        gridRows = element.getGridRows().coerceAtLeast(1)
        gridCols = element.getGridCols().coerceAtLeast(1)
        gridCellShapeIndex = element.getGridCellShape().ordinal
        gridSpacingPct = (element.getGridSpacing() * 100f).roundToInt()
        gridMultitouchEnabled = element.isGridMultitouchEnabled()
        holdKeyIndex = holdKeyOptions.indexOf(element.getHoldKey().toString()).coerceAtLeast(0)
        toggleSwitch = element.isToggleSwitch()
        swipeable = element.isSwipeable()
        customText = element.getText()
        selectedIconId = element.getIconId().toInt() and 0xFF
        customIconTintEnabled = element.isCustomIconTintEnabled()
        customIconAsButton = element.isCustomIconAsButton()
        bindingCount = element.getBindingCount().coerceAtLeast(1)
        expandableChildCount = element.getExpandableChildCount().coerceAtLeast(1)
        expandableLayoutIndex = element.getExpandableLayout().ordinal
        expandableDirectionIndex = element.getExpandableDirection().ordinal
        customAreaAppearanceEnabled = element.isCustomAreaAppearanceEnabled()
        customAreaColor = element.getCustomAreaColor()
        customAreaOpacity = (element.getCustomAreaOpacity() * 100f).roundToInt().coerceIn(0, 100)
        groupId = element.groupId ?: ""
    }

    val selectedType = ControlElement.Type.values()[typeIndex]

    LaunchedEffect(element, customIconReloadKey) {
        syncFromElement()
    }

    LaunchedEffect(customText) {
        delay(300)
        if (element.getText() == customText) profile.save()
    }
    DisposableEffect(element) {
        onDispose { profile.save() }
    }

    MaterialTheme(colorScheme = controlsEditorColorScheme()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EditorBackground)
                .padding(EditorPadding),
            verticalArrangement = Arrangement.spacedBy(EditorSpacing),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(android.R.string.cancel),
                    tint = EditorText,
                )
            }
        }

        SettingsSection(title = stringResource(R.string.type), visible = true) {
            SettingSpinner(
                label = stringResource(R.string.type),
                options = typeOptions,
                selectedIndex = typeIndex,
                onSelected = { index ->
                    if (index != typeIndex) pendingTypeIndex = index
                },
            )
        }

        if (pendingTypeIndex != null) {
            AlertDialog(
                onDismissRequest = { pendingTypeIndex = null },
                title = { Text(stringResource(R.string.change_control_type)) },
                text = { Text(stringResource(R.string.change_control_type_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        val index = pendingTypeIndex ?: return@TextButton
                        element.setType(ControlElement.Type.values()[index])
                        pendingTypeIndex = null
                        syncFromElement()
                        saveAndInvalidate()
                    }) { Text(stringResource(R.string.confirm_change)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTypeIndex = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        SettingsSection(
            title = stringResource(R.string.shape),
            visible = selectedType == ControlElement.Type.BUTTON || selectedType == ControlElement.Type.EXPANDABLE_BUTTON,
        ) {
            SettingSpinner(
                label = stringResource(R.string.shape),
                options = shapeOptions,
                selectedIndex = shapeIndex,
                onSelected = { index ->
                    element.setShape(ControlElement.Shape.values()[index])
                    shapeIndex = index
                    saveAndInvalidate()
                },
            )
        }

        if (selectedType == ControlElement.Type.RANGE_BUTTON) {
            SettingSpinner(
                label = stringResource(R.string.range),
                options = rangeOptions,
                selectedIndex = rangeIndex,
                onSelected = { index ->
                    element.setRange(ControlElement.Range.values()[index])
                    rangeIndex = index
                    saveAndInvalidate()
                },
            )

            SettingRadioGroup(
                label = stringResource(R.string.orientation),
                options = listOf(stringResource(R.string.horizontal), stringResource(R.string.vertical)),
                selectedIndex = orientationIndex,
                onSelected = { index ->
                    element.setOrientation(index.toByte())
                    orientationIndex = index
                    saveAndInvalidate()
                },
            )

            NumberPickerRow(
                label = stringResource(R.string.columns),
                value = bindingCount,
                minValue = 3,
                maxValue = 8,
                onValueChange = { value ->
                    element.setBindingCount(value)
                    bindingCount = value
                    saveAndInvalidate()
                },
            )
        }

        LabeledSlider(
            label = stringResource(R.string.scale),
            value = scaleValue,
            rangeStart = EditorMinScalePercent,
            rangeEnd = EditorMaxScalePercent,
            suffix = "%",
            onValueChange = { value ->
                val rounded = ((value / 5f).roundToInt() * 5)
                    .coerceIn(EditorMinScalePercent, EditorMaxScalePercent)
                element.setScale(rounded / 100f)
                scaleValue = rounded
                onInvalidate()
            },
            onValueChangeFinished = ::saveAndInvalidate,
        )

        if (selectedType == ControlElement.Type.STICK || selectedType == ControlElement.Type.DYNAMIC_STICK || selectedType == ControlElement.Type.D_PAD) {
            LabeledSlider(
                label = stringResource(R.string.dead_zone),
                value = deadZonePct,
                rangeStart = 0,
                rangeEnd = 50,
                suffix = "%",
                onValueChange = { value ->
                    element.deadZone = value / 100f
                    deadZonePct = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
        }

        SettingsSection(title = stringResource(R.string.detection_area), visible = selectedType == ControlElement.Type.DYNAMIC_STICK) {
            LabeledSlider(
                label = stringResource(R.string.area_width),
                value = detectionWidth,
                rangeStart = 200,
                rangeEnd = 2000,
                suffix = "px",
                onValueChange = { value ->
                    element.setAreaWidth(value)
                    detectionWidth = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
            LabeledSlider(
                label = stringResource(R.string.area_height),
                value = detectionHeight,
                rangeStart = 200,
                rangeEnd = 2000,
                suffix = "px",
                onValueChange = { value ->
                    element.setAreaHeight(value)
                    detectionHeight = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
            LabeledSlider(
                label = stringResource(R.string.stick_radius),
                value = stickRadius,
                rangeStart = 60,
                rangeEnd = 400,
                suffix = "px",
                onValueChange = { value ->
                    element.setStickRadius(value)
                    stickRadius = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
        }

        SettingsSection(title = stringResource(R.string.detection_area), visible = selectedType == ControlElement.Type.MOUSE_AREA) {
            LabeledSlider(
                label = stringResource(R.string.area_width),
                value = mouseAreaWidth,
                rangeStart = 200,
                rangeEnd = 2000,
                suffix = "px",
                onValueChange = { value ->
                    element.setAreaWidth(value)
                    mouseAreaWidth = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
            LabeledSlider(
                label = stringResource(R.string.area_height),
                value = mouseAreaHeight,
                rangeStart = 200,
                rangeEnd = 2000,
                suffix = "px",
                onValueChange = { value ->
                    element.setAreaHeight(value)
                    mouseAreaHeight = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
            LabeledSlider(
                label = stringResource(R.string.mouse_sensitivity),
                value = mouseSensitivity,
                rangeStart = 1,
                rangeEnd = 50,
                suffix = "x",
                format = { value -> String.format(Locale.getDefault(), "%.1fx", value / 10f) },
                onValueChange = { value ->
                    element.setMouseSensitivity(value / 10f)
                    mouseSensitivity = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
        }

        SettingsSection(title = stringResource(R.string.mouse_sensitivity), visible = selectedType == ControlElement.Type.TRACKPAD) {
            LabeledSlider(
                label = null,
                value = mouseSensitivity,
                rangeStart = 1,
                rangeEnd = 50,
                suffix = "x",
                format = { value -> String.format(Locale.getDefault(), "%.1fx", value / 10f) },
                onValueChange = { value ->
                    element.setMouseSensitivity(value / 10f)
                    mouseSensitivity = value
                    onInvalidate()
                },
                onValueChangeFinished = ::saveAndInvalidate,
            )
        }

        SettingsSection(
            title = stringResource(R.string.area_appearance),
            visible = selectedType == ControlElement.Type.DYNAMIC_STICK || selectedType == ControlElement.Type.MOUSE_AREA,
        ) {
            SettingSwitch(
                label = stringResource(R.string.independent_area_appearance),
                checked = customAreaAppearanceEnabled,
                visible = true,
                onCheckedChange = { enabled ->
                    customAreaAppearanceEnabled = enabled
                    element.setCustomAreaAppearanceEnabled(enabled)
                    saveAndInvalidate()
                },
            )
            AnimatedVisibility(visible = customAreaAppearanceEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(EditorSpacing)) {
                    AreaColorRow(
                        color = customAreaColor,
                        onClick = { showAreaColorPicker = true },
                    )
                    LabeledSlider(
                        label = stringResource(R.string.area_opacity),
                        value = customAreaOpacity,
                        rangeStart = 0,
                        rangeEnd = 100,
                        suffix = "%",
                        onValueChange = { value ->
                            customAreaOpacity = value
                            element.setCustomAreaOpacity(value / 100f)
                            onInvalidate()
                        },
                        onValueChangeFinished = ::saveAndInvalidate,
                    )
                }
            }
        }

        if (showAreaColorPicker) {
            AlertDialog(
                onDismissRequest = { showAreaColorPicker = false },
                title = { Text(stringResource(R.string.area_color)) },
                text = {
                    ColorPicker(
                        initialColor = Color(customAreaColor),
                        onColorChanged = { color ->
                            customAreaColor = color.toArgb()
                            element.setCustomAreaColor(customAreaColor)
                            saveAndInvalidate()
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showAreaColorPicker = false }) {
                        Text(stringResource(R.string.ok), color = EditorAccent)
                    }
                },
            )
        }

        SettingsSection(
            title = stringResource(R.string.hold_key),
            visible = selectedType == ControlElement.Type.TRACKPAD || selectedType == ControlElement.Type.MOUSE_AREA || selectedType == ControlElement.Type.STICK || selectedType == ControlElement.Type.DYNAMIC_STICK,
        ) {
            SettingSpinner(
                label = null,
                options = holdKeyOptions,
                selectedIndex = holdKeyIndex,
                onSelected = { index ->
                    holdKeyIndex = index
                    val binding = if (index == 0) Binding.NONE else holdKeyBindingFromLabel(holdKeyOptions[index])
                    element.setHoldKey(binding)
                    saveAndInvalidate()
                },
            )
        }

        SettingsSection(title = stringResource(R.string.button_grid), visible = selectedType == ControlElement.Type.BUTTON_GRID) {
            SettingSwitch(
                label = stringResource(R.string.grid_multitouch),
                checked = gridMultitouchEnabled,
                visible = true,
                onCheckedChange = { enabled ->
                    gridMultitouchEnabled = enabled
                    element.setGridMultitouchEnabled(enabled)
                    saveAndInvalidate()
                },
            )
            NumberPickerRow(
                label = stringResource(R.string.grid_rows),
                value = gridRows,
                minValue = 1,
                maxValue = 8,
                onValueChange = { value ->
                    gridRows = value
                    element.setGridRows(value)
                    element.setBindingCount(value * gridCols)
                    bindingCount = element.getBindingCount().coerceAtLeast(1)
                    saveAndInvalidate()
                },
            )
            NumberPickerRow(
                label = stringResource(R.string.grid_cols),
                value = gridCols,
                minValue = 1,
                maxValue = 16,
                onValueChange = { value ->
                    gridCols = value
                    element.setGridCols(value)
                    element.setBindingCount(gridRows * value)
                    bindingCount = element.getBindingCount().coerceAtLeast(1)
                    saveAndInvalidate()
                },
            )
            SettingSpinner(
                label = stringResource(R.string.cell_shape),
                options = shapeOptions,
                selectedIndex = gridCellShapeIndex,
                onSelected = { index ->
                    element.setGridCellShape(ControlElement.Shape.values()[index])
                    gridCellShapeIndex = index
                    saveAndInvalidate()
                },
            )
            LabeledSlider(
                label = stringResource(R.string.grid_spacing),
                value = gridSpacingPct,
                rangeStart = 0,
                rangeEnd = 100,
                suffix = "%",
                onValueChange = { value ->
                    gridSpacingPct = value
                    element.setGridSpacing(value / 100f)
                    onInvalidate()
                },
                onValueChangeFinished = { profile.save() },
            )
            QuickFillBar(
                onQwerty = { fillGrid(qwertyKeys) },
                onFunctionKeys = { fillGrid(functionKeys) },
                onNumPad = { fillGrid(numPadKeys) },
                onClear = {
                    prepareGridForFill()
                    for (index in 0 until element.getBindingCount()) {
                        element.setBindingAt(index, Binding.NONE)
                        element.setCombo(index, null)
                    }
                    saveAndInvalidate()
                },
            )
        }

        SettingsSection(
            title = stringResource(R.string.expandable_button_settings),
            visible = selectedType == ControlElement.Type.EXPANDABLE_BUTTON,
        ) {
            NumberPickerRow(
                label = stringResource(R.string.child_button_count),
                value = expandableChildCount,
                minValue = 1,
                maxValue = ControlElement.MAX_EXPANDABLE_CHILDREN,
                onValueChange = { value ->
                    expandableChildCount = value
                    element.setExpandableChildCount(value)
                    bindingCount = element.getBindingCount()
                    saveAndInvalidate()
                },
            )
            SettingSpinner(
                label = stringResource(R.string.expandable_layout),
                options = expandableLayoutOptions,
                selectedIndex = expandableLayoutIndex,
                onSelected = { index ->
                    expandableLayoutIndex = index
                    element.setExpandableLayout(ControlElement.ExpandableLayout.values()[index])
                    saveAndInvalidate()
                },
            )
            if (expandableLayoutIndex == ControlElement.ExpandableLayout.LIST.ordinal) {
                SettingSpinner(
                    label = stringResource(R.string.expandable_direction),
                    options = expandableDirectionOptions,
                    selectedIndex = expandableDirectionIndex,
                    onSelected = { index ->
                        expandableDirectionIndex = index
                        element.setExpandableDirection(ControlElement.ExpandableDirection.values()[index])
                        saveAndInvalidate()
                    },
                )
            }
        }

        if (selectedType != ControlElement.Type.MOUSE_AREA && selectedType != ControlElement.Type.RANGE_BUTTON) {
            SettingsSection(title = stringResource(R.string.bindings), visible = true) {
                if (selectedType == ControlElement.Type.BUTTON) {
                    val label = stringResource(R.string.binding)
                    BindingValueRow(
                        label = label,
                        value = element.getBindingAt(0),
                        combo = element.getCombo(0)?.toList().orEmpty(),
                        onSet = { openBindingDialog(0, label) },
                    )
                } else if (selectedType == ControlElement.Type.D_PAD || selectedType == ControlElement.Type.STICK || selectedType == ControlElement.Type.TRACKPAD || selectedType == ControlElement.Type.DYNAMIC_STICK) {
                    val labels = listOf(
                        stringResource(R.string.binding_up),
                        stringResource(R.string.binding_right),
                        stringResource(R.string.binding_down),
                        stringResource(R.string.binding_left),
                    )
                    labels.forEachIndexed { index, label ->
                        BindingValueRow(
                            label = label,
                            value = element.getBindingAt(index),
                            combo = element.getCombo(index)?.toList().orEmpty(),
                            onSet = { openBindingDialog(index, label) },
                        )
                    }
                } else if (selectedType == ControlElement.Type.BUTTON_GRID) {
                    val total = gridRows * gridCols
                    for (index in 0 until total) {
                        val row = index / gridCols + 1
                        val col = index % gridCols + 1
                        val label = stringResource(R.string.binding_grid_cell_label, row, col)
                        BindingValueRow(
                            label = label,
                            value = element.getBindingAt(index),
                            combo = element.getCombo(index)?.toList().orEmpty(),
                            onSet = { openBindingDialog(index, label) },
                        )
                    }
                } else if (selectedType == ControlElement.Type.EXPANDABLE_BUTTON) {
                    for (index in 0 until expandableChildCount) {
                        val label = stringResource(R.string.child_button_label, index + 1)
                        BindingValueRow(
                            label = label,
                            value = element.getBindingAt(index),
                            combo = element.getCombo(index)?.toList().orEmpty(),
                            onSet = { openBindingDialog(index, label) },
                        )
                    }
                }
            }
        }

        if (bindingDialogIndex >= 0) {
            val index = bindingDialogIndex
            BindingSetupDialog(
                title = bindingDialogTitle,
                initialBinding = element.getBindingAt(index),
                initialCombo = element.getCombo(index)?.toList().orEmpty(),
                initialBlockTouchscreenMouseButtons = element.blocksTouchscreenMouseButtonsAt(index),
                options = bindingOptions,
                onDismiss = { bindingDialogIndex = -1 },
                onSave = { binding, combo, blockTouchscreenMouseButtons ->
                    saveBindingSlot(index, binding, combo, blockTouchscreenMouseButtons)
                    bindingDialogIndex = -1
                },
            )
        }

        SettingSwitch(
            label = stringResource(R.string.toggle_switch),
            checked = toggleSwitch,
            visible = selectedType == ControlElement.Type.BUTTON
                && !element.isAndroidKeyboardButton,
            onCheckedChange = { checked ->
                toggleSwitch = checked
                element.setToggleSwitch(checked)
                saveAndInvalidate()
            },
        )

        SettingSwitch(
            label = stringResource(R.string.input_controls_editor_swipeable),
            checked = swipeable,
            visible = selectedType == ControlElement.Type.BUTTON
                || selectedType == ControlElement.Type.D_PAD,
            onCheckedChange = { checked ->
                swipeable = checked
                element.setSwipeable(checked)
                saveAndInvalidate()
            },
        )

        SettingsSection(
            title = stringResource(R.string.custom_text),
            visible = selectedType == ControlElement.Type.BUTTON || selectedType == ControlElement.Type.EXPANDABLE_BUTTON,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EditorSpacing)) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { value ->
                        val text = value.take(8)
                        customText = text
                        element.setText(text)
                        onInvalidate()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.custom_text), color = EditorSubText) },
                    placeholder = { Text(stringResource(R.string.none), color = EditorSubText) },
                    colors = outlinedTextFieldColors(),
                )

                Text(
                    text = stringResource(R.string.icon),
                    color = EditorSubText,
                    fontWeight = FontWeight.Bold,
                )
                IconPicker(
                    icons = builtInIcons,
                    selectedId = selectedIconId,
                    onSelected = { id ->
                        selectedIconId = id
                        element.setIconId(id)
                        saveAndInvalidate()
                    },
                )

                Text(
                    text = stringResource(R.string.custom_icons),
                    color = EditorSubText,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onPickCustomIcon,
                        shape = SmallShape,
                    ) {
                        Text(text = stringResource(R.string.add), color = EditorAccent)
                    }
                }
                Text(
                    text = stringResource(R.string.long_press_custom_icon_to_delete),
                    color = EditorSubText,
                )
                IconPicker(
                    icons = customIcons,
                    selectedId = selectedIconId,
                    onSelected = { id ->
                        selectedIconId = id
                        element.setIconId(id)
                        saveAndInvalidate()
                    },
                    onLongPress = { id ->
                        val usage = if (profile.save()) {
                            InputControlsManager.getCustomIconUsage(context, id)
                        } else {
                            null
                        }
                        customIconDeleteRequest = CustomIconDeleteRequest(id, usage)
                    },
                )
                SettingSwitch(
                    label = stringResource(R.string.tint_custom_icon),
                    checked = customIconTintEnabled,
                    visible = selectedIconId >= CustomIconManager.CUSTOM_ICON_ID_OFFSET,
                    onCheckedChange = { checked ->
                        customIconTintEnabled = checked
                        element.setCustomIconTintEnabled(checked)
                        saveAndInvalidate()
                    },
                )
                SettingSwitch(
                    label = stringResource(R.string.use_custom_icon_as_button),
                    checked = customIconAsButton,
                    visible = selectedIconId >= CustomIconManager.CUSTOM_ICON_ID_OFFSET,
                    onCheckedChange = { checked ->
                        customIconAsButton = checked
                        element.setCustomIconAsButton(checked)
                        saveAndInvalidate()
                    },
                )
            }
        }

        customIconDeleteRequest?.let { request ->
            val usage = request.usage
            val canDelete = usage?.controlCount == 0 && !request.deleteFailed
            val message = when {
                request.deleteFailed -> stringResource(R.string.unable_to_delete_custom_icon)
                usage == null -> stringResource(R.string.unable_to_check_custom_icon_usage)
                usage.controlCount > 0 -> context.resources.getQuantityString(
                    R.plurals.custom_icon_in_use,
                    usage.controlCount,
                    usage.controlCount,
                )
                else -> stringResource(R.string.delete_custom_icon_confirmation)
            }
            AlertDialog(
                onDismissRequest = { customIconDeleteRequest = null },
                title = {
                    Text(
                        text = stringResource(
                            if (canDelete) R.string.delete_custom_icon else R.string.custom_icon_not_deleted
                        ),
                        color = EditorText,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(EditorSpacing)) {
                        Text(text = message, color = EditorSubText)
                        if (usage != null && usage.profileNames.isNotEmpty()) {
                            Text(
                                text = context.resources.getQuantityString(
                                    R.plurals.custom_icon_profiles_in_use,
                                    usage.profileNames.size,
                                    usage.profileNames.size,
                                ),
                                color = EditorTextValue,
                                fontWeight = FontWeight.SemiBold,
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(usage.profileNames) { profileName ->
                                    Surface(
                                        color = EditorSurface,
                                        shape = SmallShape,
                                    ) {
                                        Text(
                                            text = profileName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            color = EditorTextValue,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (!canDelete) {
                            customIconDeleteRequest = null
                        } else if (onDeleteCustomIcon(request.iconId)) {
                            customIconDeleteRequest = null
                        } else {
                            customIconDeleteRequest = request.copy(deleteFailed = true)
                        }
                    }) {
                        Text(
                            text = stringResource(if (canDelete) R.string.remove else R.string.ok),
                            color = EditorAccent,
                        )
                    }
                },
                dismissButton = {
                    if (canDelete) {
                        TextButton(onClick = { customIconDeleteRequest = null }) {
                            Text(text = stringResource(R.string.cancel), color = EditorTextValue)
                        }
                    }
                },
            )
        }

        SettingsSection(title = stringResource(R.string.group_label), visible = true) {
            val groupNames = profile.getGroups().keys.toList()
            val noneLabel = stringResource(R.string.none)
            val createLabel = stringResource(R.string.create_new_group)
            val assignedGroupId = groupId.trim()
            var assignedGroupVisible by remember(assignedGroupId, customIconReloadKey) {
                mutableStateOf(assignedGroupId.isEmpty() || profile.isGroupVisible(assignedGroupId))
            }
            val groupOptions = buildList {
                add(noneLabel)
                addAll(groupNames)
                add(createLabel)
            }
            val selectedGroupIndex = when {
                groupId.isBlank() -> 0
                else -> (groupNames.indexOf(groupId) + 1).takeIf { it > 0 } ?: 0
            }

            SettingSpinner(
                label = null,
                options = groupOptions,
                selectedIndex = selectedGroupIndex,
                onSelected = { index ->
                    when (index) {
                        0 -> {
                            groupId = ""
                            saveAndInvalidate()
                        }
                        groupOptions.lastIndex -> {
                            showCreateGroupDialog = true
                            newGroupName = groupId
                        }
                        else -> {
                            groupId = groupOptions[index]
                            saveAndInvalidate()
                        }
                    }
                },
            )

            if (assignedGroupId.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(
                                if (assignedGroupVisible) R.drawable.icon_eye else R.drawable.icon_eye_off
                            ),
                            contentDescription = stringResource(R.string.group_visibility),
                            tint = EditorTextValue,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.group_visibility), color = EditorSubText)
                    }

                    Switch(
                        checked = assignedGroupVisible,
                        onCheckedChange = { checked ->
                            assignedGroupVisible = checked
                            profile.setGroupVisible(assignedGroupId, checked)
                            profile.save()
                            onInvalidate()
                        },
                    )
                }
            }

            if (showCreateGroupDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showCreateGroupDialog = false
                        newGroupName = ""
                    },
                    title = { Text(text = stringResource(R.string.create_new_group), color = EditorText) },
                    text = {
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(text = stringResource(R.string.group_name), color = EditorSubText) },
                            colors = outlinedTextFieldColors(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val trimmedName = newGroupName.trim()
                            if (trimmedName.isNotEmpty()) {
                                if (profile.getGroup(trimmedName) == null) {
                                    profile.addGroup(trimmedName)
                                }
                                groupId = trimmedName
                                showCreateGroupDialog = false
                                newGroupName = ""
                                saveAndInvalidate()
                            }
                        }) {
                            Text(text = stringResource(R.string.create), color = EditorAccent)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showCreateGroupDialog = false
                            newGroupName = ""
                        }) {
                            Text(text = stringResource(R.string.cancel), color = EditorAccent)
                        }
                    },
                )
            }
        }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String?,
    value: Int,
    rangeStart: Int,
    rangeEnd: Int,
    suffix: String,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { current -> "$current$suffix" },
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label != null) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = EditorSubText,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = format(value),
                color = EditorTextValue,
                fontWeight = FontWeight.Medium,
            )
        }

        androidx.compose.material3.Slider(
            modifier = Modifier.widthIn(max = EditorMaxSliderWidth),
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(rangeStart, rangeEnd)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = rangeStart.toFloat()..rangeEnd.toFloat(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSpinner(
    label: String?,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.getOrNull(selectedIndex).orEmpty()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(text = label, color = EditorSubText)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = outlinedTextFieldColors(),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .background(EditorSurface, EditorShape),
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (index == selectedIndex) EditorAccent else EditorText,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRadioGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = EditorSubText)
        Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing), verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier.selectable(
                        selected = index == selectedIndex,
                        onClick = { onSelected(index) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = index == selectedIndex,
                        onClick = { onSelected(index) },
                    )
                    Text(text = option, color = EditorTextValue)
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = EditorSubText)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun AreaColorRow(color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.area_color), color = EditorSubText)
        Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format(Locale.US, "#%06X", color and 0xFFFFFF),
                color = EditorTextValue,
            )
            Surface(
                modifier = Modifier.size(36.dp),
                color = Color(color),
                shape = SmallShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
            ) {}
        }
    }
}

@Composable
private fun NumberPickerRow(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, color = EditorSubText)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EditorSpacing)) {
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }
                    if (filtered.isEmpty()) return@OutlinedTextField
                    filtered.toIntOrNull()?.let { parsed ->
                        onValueChange(parsed.coerceIn(minValue, maxValue))
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = outlinedTextFieldColors(),
            )
            TextButton(onClick = { onValueChange((value - 1).coerceAtLeast(minValue)) }, shape = SmallShape) {
                Text(text = "−", color = EditorAccent)
            }
            TextButton(onClick = { onValueChange((value + 1).coerceAtMost(maxValue)) }, shape = SmallShape) {
                Text(text = "+", color = EditorAccent)
            }
        }
    }
}

@Composable
private fun QuickFillBar(
    onQwerty: () -> Unit,
    onFunctionKeys: () -> Unit,
    onNumPad: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EditorSpacing),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing)) {
            FillChip(text = stringResource(R.string.fill_qwerty), onClick = onQwerty, modifier = Modifier.weight(1f))
            FillChip(text = stringResource(R.string.fill_function_keys), onClick = onFunctionKeys, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EditorSpacing)) {
            FillChip(text = stringResource(R.string.fill_numpad), onClick = onNumPad, modifier = Modifier.weight(1f))
            FillChip(text = stringResource(R.string.clear), onClick = onClear, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconPicker(
    icons: List<PickerIcon>,
    selectedId: Int,
    onSelected: (Int) -> Unit,
    onLongPress: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnailSizePx = with(LocalDensity.current) { 32.dp.roundToPx() }
    val glyphTint = Color(AppThemeState.getCurrentAccentArgb())
    val deleteLabel = stringResource(R.string.delete_custom_icon)
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EditorSpacing),
    ) {
        items(icons, key = { it.id }) { icon ->
            val selected = icon.id == selectedId
            val border = if (selected) BorderStroke(2.dp, EditorAccent) else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            val customBitmap = remember(icon.id, icon.isCustom, thumbnailSizePx) {
                if (icon.isCustom) loadCustomIconThumbnail(context, icon.id, thumbnailSizePx) else null
            }
            DisposableEffect(customBitmap) {
                onDispose {
                    customBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
                }
            }
            val bitmap = icon.bitmap ?: customBitmap
            val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
            Surface(
                shape = EditorShape,
                color = if (selected) EditorSurface else Color.Transparent,
                border = border,
                modifier = Modifier
                    .size(48.dp)
                    .combinedClickable(
                        onLongClickLabel = if (onLongPress != null) deleteLabel else null,
                        onLongClick = onLongPress?.let { callback -> { callback(icon.id) } },
                        onClick = { onSelected(icon.id) },
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (imageBitmap != null) {
                        if (icon.isCustom) {
                            CustomIconPreviewBackground(Modifier.size(36.dp))
                        }
                        androidx.compose.foundation.Image(
                            bitmap = imageBitmap,
                            contentDescription = stringResource(R.string.icon_content_description, icon.id),
                            modifier = Modifier.size(32.dp),
                            colorFilter = if (icon.isCustom) null else ColorFilter.tint(glyphTint, BlendMode.SrcIn),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomIconPreviewBackground(modifier: Modifier = Modifier) {
    val lightCell = Color(0xFFE0E0E0)
    val darkCell = Color(0xFF707070)
    val borderColor = Color.White.copy(alpha = 0.45f)
    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val cellCount = 4
        val cellWidth = size.width / cellCount
        val cellHeight = size.height / cellCount
        for (row in 0 until cellCount) {
            for (column in 0 until cellCount) {
                drawRect(
                    color = if ((row + column) % 2 == 0) lightCell else darkCell,
                    topLeft = Offset(column * cellWidth, row * cellHeight),
                    size = Size(cellWidth, cellHeight),
                )
            }
        }
        val borderWidth = 1.dp.toPx()
        drawRect(
            color = borderColor,
            topLeft = Offset(borderWidth / 2, borderWidth / 2),
            size = Size(size.width - borderWidth, size.height - borderWidth),
            style = Stroke(width = borderWidth),
        )
    }
}

@Composable
private fun BindingValueRow(
    label: String,
    value: Binding,
    combo: List<Binding>,
    onSet: () -> Unit,
) {
    val summary = bindingSummary(value, combo, stringResource(R.string.none))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EditorSurface.copy(alpha = 0.72f),
        shape = SmallShape,
        border = BorderStroke(1.dp, EditorAccent.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(EditorSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = label, color = EditorSubText)
                Text(text = summary, color = EditorTextValue, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onSet, shape = SmallShape) {
                Text(text = stringResource(R.string.set_binding), color = EditorAccent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BindingSetupDialog(
    title: String,
    initialBinding: Binding,
    initialCombo: List<Binding>,
    initialBlockTouchscreenMouseButtons: Boolean,
    options: List<Binding>,
    onDismiss: () -> Unit,
    onSave: (Binding, List<Binding>, Boolean) -> Unit,
) {
    val cleanInitialCombo = remember(initialCombo) {
        initialCombo.filter { it != Binding.NONE }.distinct().take(ControlElement.MAX_COMBO_BINDINGS)
    }
    val initialSelection = remember(initialBinding, cleanInitialCombo) {
        if (initialBinding != Binding.NONE) initialBinding else cleanInitialCombo.lastOrNull() ?: Binding.NONE
    }
    var selectedBinding by remember(title, initialSelection) { mutableStateOf(initialSelection) }
    var typedValue by remember(title, initialSelection) { mutableStateOf("") }
    var combo by remember(title, cleanInitialCombo) { mutableStateOf(cleanInitialCombo) }
    var blockTouchscreenMouseButtons by remember(title, initialBlockTouchscreenMouseButtons) {
        mutableStateOf(initialBlockTouchscreenMouseButtons)
    }
    var filterCategory by remember(title) { mutableStateOf("All") }

    fun optionsForCategory(category: String): List<Binding> {
        return when (category) {
            "Keyboard" -> Binding.keyboardBindingValues().toList()
            "Mouse" -> Binding.mouseBindingValues().toList()
            "Gamepad" -> Binding.gamepadBindingValues().toList()
            else -> options // "All"
        }
    }

    val filteredOptions = remember(filterCategory, options) {
        optionsForCategory(filterCategory)
    }
    val optionLabels = remember(filteredOptions) { filteredOptions.map { it.toString() } }
    val selectedIndex = filteredOptions.indexOf(selectedBinding).coerceAtLeast(0)
    val typedBinding = bindingFromTypedValue(typedValue)
    val addCandidate = typedBinding ?: selectedBinding
    val canAddCandidate = combo.size < ControlElement.MAX_COMBO_BINDINGS &&
        addCandidate != Binding.NONE && !combo.contains(addCandidate)
    val hasTouchscreenMouseButton = (combo + selectedBinding).any {
        it == Binding.MOUSE_LEFT_BUTTON || it == Binding.MOUSE_RIGHT_BUTTON
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.binding_setup_title, title), color = EditorText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(EditorSpacing),
            ) {
                val filterCategories = listOf("All", "Keyboard", "Mouse", "Gamepad")
                val filterLabels = listOf(
                    stringResource(R.string.binding_filter_all),
                    stringResource(R.string.binding_filter_keyboard),
                    stringResource(R.string.binding_filter_mouse),
                    stringResource(R.string.binding_filter_gamepad),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    filterCategories.zip(filterLabels).forEach { (category, label) ->
                        val isSelected = category == filterCategory
                        TextButton(
                            onClick = {
                                val nextOptions = optionsForCategory(category)
                                filterCategory = category
                                if (selectedBinding !in nextOptions) {
                                    selectedBinding = nextOptions.firstOrNull() ?: Binding.NONE
                                }
                                typedValue = ""
                            },
                            shape = SmallShape,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (isSelected) EditorAccent else EditorSubText,
                            ),
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) EditorAccent else EditorSubText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = typedValue,
                    onValueChange = { value ->
                        val nextValue = value.take(1)
                        typedValue = nextValue
                        bindingFromTypedValue(nextValue)?.let { selectedBinding = it }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.binding_text_value)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = outlinedTextFieldColors(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EditorSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SettingSpinner(
                            label = stringResource(R.string.binding_selector),
                            options = optionLabels,
                            selectedIndex = selectedIndex,
                            onSelected = { index ->
                                selectedBinding = filteredOptions.getOrNull(index) ?: Binding.NONE
                                typedValue = ""
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            if (canAddCandidate) combo = combo + addCandidate
                        },
                        enabled = canAddCandidate,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_to_combo),
                            tint = if (canAddCandidate) EditorAccent else EditorSubText,
                        )
                    }
                }
                Text(text = stringResource(R.string.combo_bindings), color = EditorSubText)
                if (combo.isEmpty()) {
                    Text(text = stringResource(R.string.none), color = EditorTextValue)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        combo.forEachIndexed { index, binding ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(EditorSpacing),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = binding.toString(),
                                    modifier = Modifier.weight(1f),
                                    color = EditorTextValue,
                                )
                                IconButton(onClick = { combo = combo.filterIndexed { itemIndex, _ -> itemIndex != index } }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.remove),
                                        tint = EditorSubText,
                                    )
                                }
                            }
                        }
                    }
                }
                if (hasTouchscreenMouseButton) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.mouse_binding_priority),
                                color = EditorTextValue,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.mouse_binding_priority_description),
                                color = EditorSubText,
                            )
                        }
                        Switch(
                            checked = blockTouchscreenMouseButtons,
                            onCheckedChange = { blockTouchscreenMouseButtons = it },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(typedBinding ?: selectedBinding, combo, blockTouchscreenMouseButtons)
            }) {
                Text(text = stringResource(R.string.save), color = EditorAccent)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(Binding.NONE, emptyList(), true) }) {
                    Text(text = stringResource(R.string.clear), color = EditorAccent)
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(android.R.string.cancel), color = EditorTextValue)
                }
            }
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(EditorSpacing),
        ) {
            Text(
                text = title,
                color = EditorText,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}

@Composable
private fun FillChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = SmallShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = EditorAccent,
        ),
    ) {
        Text(text = text, color = EditorAccent)
    }
}

private fun bindingSummary(value: Binding, combo: List<Binding>, noneLabel: String): String {
    val cleanCombo = combo.filter { it != Binding.NONE }
    if (cleanCombo.isNotEmpty()) return cleanCombo.joinToString(" + ") { it.toString() }
    return if (value != Binding.NONE) value.toString() else noneLabel
}

private fun bindingFromTypedValue(value: String): Binding? {
    val char = value.firstOrNull() ?: return null
    val bindingName = when {
        char in 'a'..'z' -> "KEY_${char.uppercaseChar()}"
        char in 'A'..'Z' -> "KEY_$char"
        char in '0'..'9' -> "KEY_$char"
        char == ' ' -> "KEY_SPACE"
        char == '[' -> "KEY_BRACKET_LEFT"
        char == ']' -> "KEY_BRACKET_RIGHT"
        char == '\\' -> "KEY_BACKSLASH"
        char == '/' -> "KEY_SLASH"
        char == ';' -> "KEY_SEMICOLON"
        char == ',' -> "KEY_COMMA"
        char == '.' -> "KEY_PERIOD"
        char == '\'' -> "KEY_APOSTROPHE"
        char == '+' -> "KEY_KP_ADD"
        char == '-' -> "KEY_MINUS"
        char == '`' -> "KEY_GRAVE"
        else -> return null
    }
    return Binding.values().firstOrNull { it.name == bindingName }
}

private fun holdKeyBindingFromLabel(label: String): Binding {
    for (binding in Binding.values()) {
        if (binding.toString() == label) return binding
    }
    return Binding.NONE
}

private fun loadBuiltInIcons(context: Context): List<PickerIcon> {
    val icons = mutableListOf<PickerIcon>()
    try {
        val filenames = context.assets.list("inputcontrols/icons/") ?: emptyArray()
        for (filename in filenames) {
            val id = filename.substringBefore('.').toIntOrNull() ?: continue
            val bitmap = context.assets.open("inputcontrols/icons/$filename").use { BitmapFactory.decodeStream(it) }
            icons.add(PickerIcon(id = id, bitmap = bitmap))
        }
    } catch (_: Exception) {
    }
    return icons.sortedBy { it.id }
}

private fun loadCustomIcons(customIconManager: CustomIconManager): List<PickerIcon> {
    return customIconManager.getCustomIconIds().map { id ->
        PickerIcon(id = id.toInt(), bitmap = null, isCustom = true)
    }
}

private fun loadCustomIconThumbnail(context: Context, id: Int, targetSize: Int): Bitmap? {
    val file = File(File(context.filesDir, "custom_icons"), "$id.png")
    if (!file.isFile) return null

    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetSize ||
            bounds.outHeight / (sampleSize * 2) >= targetSize
        ) {
            sampleSize *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    } catch (_: OutOfMemoryError) {
        null
    }
}

@Composable
private fun controlsEditorColorScheme() = darkColorScheme(
    primary = EditorAccent,
    onPrimary = Color.Black,
    background = EditorBackground,
    onBackground = EditorText,
    surface = EditorBackground,
    onSurface = EditorText,
    surfaceVariant = EditorSurface,
    onSurfaceVariant = EditorSubText,
    outline = Color.White.copy(alpha = 0.24f),
)

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = EditorText,
    unfocusedTextColor = EditorText,
    focusedBorderColor = EditorAccent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
    cursorColor = EditorAccent,
    focusedLabelColor = EditorSubText,
    unfocusedLabelColor = EditorSubText,
    focusedContainerColor = EditorBackground,
    unfocusedContainerColor = EditorBackground,
)
