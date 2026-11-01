package com.winhub.hernandez

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.inputcontrols.Binding
import com.winhub.hernandez.inputcontrols.ControlsProfile
import com.winhub.hernandez.inputcontrols.ExternalController
import com.winhub.hernandez.inputcontrols.ExternalControllerBinding
import com.winhub.hernandez.inputcontrols.InputControlsManager
import com.winhub.hernandez.math.Mathf
import com.winhub.hernandez.ui.AppTopBar
import com.winhub.hernandez.ui.screens.LabeledDropdown
import com.winhub.hernandez.ui.theme.WinlatorTheme

/**
 * Lets the user map physical-controller inputs to keyboard/mouse/gamepad bindings.
 *
 * Kept as an Activity (not a pure composable) because it overrides the input-dispatch
 * methods to capture real device input: pressing a button / moving a stick / pulling a
 * trigger on a connected gamepad auto-adds a binding row. Compose is hosted via
 * setContent { WinlatorTheme { ... } }, mirroring the app's other standalone Compose
 * screens. The list is backed by a Compose snapshot state so an auto-added row from
 * physical input recomposes the LazyColumn immediately.
 */
class ExternalControllerBindingsActivity : ComponentActivity() {
    private lateinit var profile: ControlsProfile
    private lateinit var controller: ExternalController

    // Compose snapshot state mirroring controller.getControllerBindingAt(i) order, so the
    // list recomposes the instant a binding is auto-added by physical input.
    private val bindings = mutableStateListOf<ExternalControllerBinding>()

    // Scroll-to / flash request channel from the (non-composition) input dispatch methods.
    // changeSignal is bumped on every auto-add/auto-touch so LaunchedEffect re-fires even
    // when the same keycode is pressed twice; targetIndex names the affected row.
    private var changeSignal by mutableIntStateOf(0)
    private var targetIndex by mutableIntStateOf(-1)

    // Track trigger state to only register on rising edge.
    private var l2WasPressed = false
    private var r2WasPressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileId = intent.getIntExtra("profile_id", 0)
        profile = InputControlsManager.loadProfile(this, ControlsProfile.getProfileFile(this, profileId))
        val controllerId = intent.getStringExtra("controller_id")

        var c = profile.getController(controllerId)
        if (c == null) {
            c = profile.addController(controllerId)
            profile.save()
        }
        controller = c

        // Seed the snapshot list from the controller's existing bindings.
        bindings.clear()
        for (i in 0 until controller.controllerBindingCount) bindings.add(controller.getControllerBindingAt(i))

        setContent {
            WinlatorTheme {
                BindingsScreen()
            }
        }
    }

    // ── Physical-controller capture (auto-add) — unchanged behavior from the View version ──

    private fun updateControllerBinding(keyCode: Int, binding: Binding) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return

        var controllerBinding = controller.getControllerBinding(keyCode)
        val position: Int
        if (controllerBinding == null) {
            controllerBinding = ExternalControllerBinding()
            controllerBinding.keyCode = keyCode
            controllerBinding.binding = binding

            controller.addControllerBinding(controllerBinding)
            profile.save()
            // Append to the snapshot list (controller appends too, so indices match).
            bindings.add(controllerBinding)
            position = controller.getPosition(controllerBinding)
        } else {
            position = controller.getPosition(controllerBinding)
        }

        // Reproduce the old recyclerView.scrollToPosition + animateItemView flash.
        targetIndex = position
        changeSignal++
    }

    private fun processJoystickInput() {
        val axes = intArrayOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        )
        val values = floatArrayOf(
            controller.state.thumbLX, controller.state.thumbLY,
            controller.state.thumbRX, controller.state.thumbRY,
            controller.state.getDPadX().toFloat(), controller.state.getDPadY().toFloat(),
        )

        for (i in axes.indices) {
            val sign: Byte = Mathf.sign(values[i])
            if (sign.toInt() != 0) {
                val keyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], sign)
                updateControllerBinding(keyCode, Binding.NONE)
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val device: InputDevice? = event.device
        if (device != null && ExternalController.isGameController(device)
            && controller.updateStateFromMotionEvent(event)
        ) {
            // Higher threshold for binding registration to avoid false triggers.
            val l2Value = Math.max(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE),
            )
            val r2Value = Math.max(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS),
            )

            // Only register L2/R2 on rising edge (first press) past 80% threshold.
            val l2Pressed = l2Value > 0.8f
            if (l2Pressed && !l2WasPressed) {
                updateControllerBinding(KeyEvent.KEYCODE_BUTTON_L2, Binding.NONE)
            }
            l2WasPressed = l2Pressed

            val r2Pressed = r2Value > 0.8f
            if (r2Pressed && !r2WasPressed) {
                updateControllerBinding(KeyEvent.KEYCODE_BUTTON_R2, Binding.NONE)
            }
            r2WasPressed = r2Pressed

            processJoystickInput()
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isGamepadKeyCode(keyCode)) {
            updateControllerBinding(keyCode, Binding.NONE)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isGamepadKeyCode(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isGamepadKeyCode(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            keyCode == KeyEvent.KEYCODE_BUTTON_B ||
            keyCode == KeyEvent.KEYCODE_BUTTON_X ||
            keyCode == KeyEvent.KEYCODE_BUTTON_Y ||
            keyCode == KeyEvent.KEYCODE_BUTTON_L1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_L2 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R2 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL ||
            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR ||
            keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
            keyCode == KeyEvent.KEYCODE_BUTTON_MODE ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER
    }

    // ─────────────────────────────── Compose UI ───────────────────────────────

    private fun typeIndexFor(b: Binding): Int = when {
        b.isMouse -> 1
        b.isGamepad -> 2
        else -> 0 // keyboard (Binding.NONE counts as keyboard, matching the View version)
    }

    private fun labelsForType(type: Int): List<String> = when (type) {
        0 -> Binding.keyboardBindingLabels()
        1 -> Binding.mouseBindingLabels()
        else -> Binding.gamepadBindingLabels()
    }.toList()

    private fun valuesForType(type: Int): List<Binding> = when (type) {
        0 -> Binding.keyboardBindingValues()
        1 -> Binding.mouseBindingValues()
        else -> Binding.gamepadBindingValues()
    }.toList()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun BindingsScreen() {
        val listState = rememberLazyListState()

        // Animate scroll to the row affected by physical input, each time it fires.
        LaunchedEffect(changeSignal) {
            val idx = targetIndex
            if (changeSignal > 0 && idx in 0 until bindings.size) {
                listState.animateScrollToItem(idx)
            }
        }

        Scaffold(
            topBar = {
                // Shared app top bar (surface bg + onSurface text), same as every other
                // screen, so the header follows the theme like the rest of the app.
                AppTopBar(
                    title = controller.name,
                    showBack = true,
                    onNavClick = { finish() },
                )
            },
        ) { innerPadding ->
            if (bindings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.press_any_button_on_your_controller),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                ) {
                    itemsIndexed(bindings, key = { _, item -> item.keyCode }) { index, item ->
                        BindingRow(index = index, item = item)
                    }
                }
            }
        }
    }

    @Composable
    private fun BindingRow(index: Int, item: ExternalControllerBinding) {
        // Per-row state keyed on the binding object so it re-seeds if the row is reused for
        // a different binding (Compose-state gotcha: key on the config, don't capture once).
        var typeIndex by remember(item) { mutableIntStateOf(typeIndexFor(item.binding)) }
        var valueLabel by remember(item) { mutableStateOf(item.binding.toString()) }

        val typeEntries = stringArrayResource(R.array.binding_type_entries).toList()
        val valueLabels = labelsForType(typeIndex)
        val valueValues = valuesForType(typeIndex)

        // Flash highlight on the affected row (the old animateItemView). Faithful clone of
        // the File-Manager card otherwise — at flash 0f the color IS surfaceContainer.
        val baseColor = MaterialTheme.colorScheme.surfaceContainer
        val accent = MaterialTheme.colorScheme.primary
        val flash = remember(item) { Animatable(0f) }
        LaunchedEffect(changeSignal) {
            if (changeSignal > 0 && index == targetIndex) {
                flash.snapTo(0.4f)
                flash.animateTo(0f, animationSpec = tween(durationMillis = 450))
            }
        }
        val containerColor = lerp(baseColor, accent, flash.value)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Input label — onSurface so it stays legible (the XML version had no
                    // textColor and rendered dark-on-black/invisible).
                    Text(
                        text = item.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Binding TYPE dropdown.
                        LabeledDropdown(
                            label = "Type",
                            options = typeEntries,
                            selectedOption = typeEntries.getOrElse(typeIndex) { typeEntries.first() },
                            onSelect = { label ->
                                val newType = typeEntries.indexOf(label)
                                if (newType >= 0 && newType != typeIndex) {
                                    typeIndex = newType
                                    // Switching type lands on the new type's first value and
                                    // persists it — matching the View version's behavior.
                                    val first = valuesForType(newType).firstOrNull() ?: Binding.NONE
                                    if (first != item.binding) {
                                        item.binding = first
                                        profile.save()
                                    }
                                    valueLabel = first.toString()
                                }
                            },
                            modifier = Modifier.weight(0.45f),
                        )
                        // Binding VALUE dropdown (entries depend on the selected type).
                        LabeledDropdown(
                            label = "Binding",
                            options = valueLabels,
                            selectedOption = if (valueLabel in valueLabels) valueLabel
                                             else valueLabels.firstOrNull() ?: "",
                            onSelect = { label ->
                                val idx = valueLabels.indexOf(label)
                                if (idx >= 0) {
                                    val newBinding = valueValues[idx]
                                    if (newBinding != item.binding) {
                                        item.binding = newBinding
                                        profile.save()
                                    }
                                    valueLabel = label
                                }
                            },
                            modifier = Modifier.weight(0.55f),
                        )
                    }
                }

                IconButton(onClick = {
                    controller.removeControllerBinding(item)
                    profile.save()
                    bindings.remove(item)
                }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove binding",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
