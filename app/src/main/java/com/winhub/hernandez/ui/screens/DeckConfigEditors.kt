@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.contentdialog.DXVKConfigDialog
import com.winhub.hernandez.contentdialog.VegasKeyKnowledge
import com.winhub.hernandez.contentdialog.VegasTierPresets
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.ui.components.DllOverrides
import com.winhub.hernandez.ui.components.EnvVarType
import com.winhub.hernandez.ui.components.KnownEnvVars
import com.winhub.hernandez.ui.findActivity
import com.winhub.hernandez.ui.moveFocusOrStep
import com.winhub.hernandez.ui.moveFocusSafely
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Deck-native config editors — replace the Material "old UI" dialogs the Deck
// container editor used to embed. Everything here is drawn with the Deck widget
// kit (DeckWidgets.kt) and rendered INLINE inside a DeckExpandableRow, so no
// classic dialog ever pops up in Deck mode. The classic ContainerDetailScreen
// keeps its own dialogs; these composables are Deck-only.
// ─────────────────────────────────────────────────────────────────────────────

/** Small caption line used inside Deck config panels. */
@Composable
internal fun DeckConfigNote(text: String, error: Boolean = false) {
    Text(
        text = text,
        color = if (error) DeckPalette.error else DeckPalette.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** A titled sub-card, used to group the rows of an inline config editor. */
@Composable
internal fun DeckConfigPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title.uppercase(),
            color = DeckPalette.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DeckPalette.surface)
                .border(1.dp, DeckPalette.line, RoundedCornerShape(12.dp)),
            content = content,
        )
    }
}

/** Label + editable text field in Deck style, supporting multi-line values. */
@Composable
internal fun DeckConfigTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    isError: Boolean = false,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val edit = rememberDeckEditState()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = label,
            color = DeckPalette.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                readOnly = !edit.editing,
                isError = isError,
                keyboardOptions = when {
                    numeric -> KeyboardOptions(keyboardType = KeyboardType.Number)
                    password -> KeyboardOptions(keyboardType = KeyboardType.Password)
                    else -> KeyboardOptions.Default
                }.copy(imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() }),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DeckPalette.surfaceVariant,
                    unfocusedContainerColor = DeckPalette.surfaceVariant,
                    focusedTextColor = DeckPalette.onBackground,
                    unfocusedTextColor = DeckPalette.onBackground,
                    focusedIndicatorColor = DeckPalette.primary,
                    unfocusedIndicatorColor = DeckPalette.line2,
                    errorIndicatorColor = DeckPalette.error,
                    cursorColor = DeckPalette.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = edit.editing }
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
                    .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier)
                    .clip(RoundedCornerShape(10.dp)),
            )
            if (!edit.editing) DeckEditOverlay(edit, Modifier.matchParentSize())
        }
    }
}

/** A tappable accent chip used for multi-select option groups. */
@Composable
internal fun DeckConfigChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) DeckPalette.secondary else DeckPalette.surface)
            .border(1.dp, if (selected) DeckPalette.secondary else DeckPalette.line2, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) DeckPalette.onSecondary else DeckPalette.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** A row of chips that wraps, labelled with an eyebrow. */
@Composable
internal fun DeckConfigChipGrid(label: String, chips: List<Triple<String, Boolean, () -> Unit>>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            text = label,
            color = DeckPalette.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { (text, on, toggle) ->
                DeckConfigChip(text, on, toggle)
            }
        }
    }
}

/** A [DeckChoiceRow] with an optional trailing cloud-download action (version rows). */
@Composable
internal fun DeckChoiceRowWithDownload(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDownload: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            DeckChoiceRow(label = label, options = options, selected = selected, onPick = onPick)
        }
        if (onDownload != null) {
            IconButton(onClick = onDownload, modifier = Modifier.padding(end = 6.dp)) {
                Icon(Icons.Filled.Download, contentDescription = "Download $label", tint = DeckPalette.primary)
            }
        }
    }
}

/**
 * Controller bridge for a [Dialog] window. A Dialog owns its own window, so the Activity-level
 * stick→D-pad rewrite and the A/B face-button rewrite never reach it. The Compose focus system can
 * also fail to take focus before the window is attached, leaving D-pad dead. Both listeners sit on
 * the dialog's decor view as a fallback: they only run when Compose did not handle the event.
 */
/**
 * Popups are vertical forms: Up/Down follow the focus order, so no row can be jumped over by the
 * geometric beam search (the "1 - skip - 2" step); Left/Right keep the geometric search.
 */
private fun androidx.compose.ui.focus.FocusManager.stepForm(dir: FocusDirection): Boolean = when (dir) {
    FocusDirection.Up -> moveFocusSafely(FocusDirection.Previous)
    FocusDirection.Down -> moveFocusSafely(FocusDirection.Next)
    else -> moveFocusOrStep(dir)
}

private var lastStepDir: FocusDirection? = null
private var lastStepMs = 0L

/**
 * A pad's hat reaches the popup as BOTH a D-pad key and a hat-axis motion; walking on each moved the
 * focus twice per press (1 - skip - 2). The same direction twice inside 40 ms is one press.
 */
private fun deckStepIsDuplicate(dir: FocusDirection): Boolean {
    val now = SystemClock.uptimeMillis()
    val dup = dir == lastStepDir && now - lastStepMs < 40L
    if (!dup) { lastStepDir = dir; lastStepMs = now }
    return dup
}

@Composable
private fun DeckDialogControllerBridge(
    rootFocus: FocusRequester,
    editTracker: DeckEditTracker,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current

    fun walk(dir: FocusDirection): Boolean {
        if (deckStepIsDuplicate(dir)) return true
        if (focusManager.stepForm(dir)) return true
        // Nothing in the dialog owns Compose focus yet: give the root focus, then walk.
        runCatching { view.requestFocus() }
        runCatching { rootFocus.requestFocus() }
        return focusManager.stepForm(dir)
    }

    val motion = remember(view, focusManager, rootFocus) {
        var heldKey = 0
        var lastMs = 0L
        android.view.View.OnGenericMotionListener { _, event ->
            val source = event.source
            val pad = (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            if (pad) {
                var x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                var y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                if (x == 0f && y == 0f) {
                    x = event.getAxisValue(MotionEvent.AXIS_X)
                    y = event.getAxisValue(MotionEvent.AXIS_Y)
                }
                val dz = 0.6f
                val dir = when {
                    x <= -dz && kotlin.math.abs(x) >= kotlin.math.abs(y) -> 1 // left
                    x >= dz && kotlin.math.abs(x) >= kotlin.math.abs(y) -> 2 // right
                    y <= -dz -> 3 // up
                    y >= dz -> 4 // down
                    else -> 0
                }
                val now = SystemClock.uptimeMillis()
                if (dir == 0) {
                    heldKey = 0
                } else if (dir != heldKey || now - lastMs > 380L) {
                    heldKey = dir
                    lastMs = now
                    walk(
                        when (dir) {
                            1 -> FocusDirection.Left
                            2 -> FocusDirection.Right
                            3 -> FocusDirection.Up
                            else -> FocusDirection.Down
                        }
                    )
                }
            }
            // Never consume: Compose/system still gets the event.
            false
        }
    }

    val keys = remember(view, focusManager, rootFocus) {
        android.view.View.OnKeyListener { _, keyCode, event ->
            if (event.action != android.view.KeyEvent.ACTION_DOWN) return@OnKeyListener false
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> { walk(FocusDirection.Up); true }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { walk(FocusDirection.Down); true }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { walk(FocusDirection.Left); true }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { walk(FocusDirection.Right); true }
                android.view.KeyEvent.KEYCODE_BUTTON_A -> {
                    // No Compose node handled A: focus the first control, then activate it.
                    runCatching { view.requestFocus() }
                    runCatching { rootFocus.requestFocus() }
                    focusManager.moveFocusSafely(FocusDirection.Next)
                    val now = SystemClock.uptimeMillis()
                    view.dispatchKeyEvent(
                        android.view.KeyEvent(
                            now, now, android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0
                        )
                    )
                    view.dispatchKeyEvent(
                        android.view.KeyEvent(
                            now, now, android.view.KeyEvent.ACTION_UP,
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0
                        )
                    )
                    true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_B,
                android.view.KeyEvent.KEYCODE_BACK -> {
                    if (editTracker.editing) false else { onDismiss(); true }
                }
                else -> false
            }
        }
    }

    SideEffect {
        val root = view.rootView
        root.setOnGenericMotionListener(motion)
        root.setOnKeyListener(keys)
    }
}

/**
 * The Deck config popup. Near-fullscreen (96% × 94%) so long editors breathe, with a modern Deck
 * header + close, a hairline, and a single scroll surface. Controller-first: it owns focus on open,
 * D-pad walks every row/pill/toggle, and B closes (leaving a text field first if one is editing).
 */
@Composable
internal fun DeckConfigDialog(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // These must come from the dialog's own composition. Reading them outside the Dialog
        // captured the *screen behind* focus manager, so a D-pad press inside the popup walked
        // the container tab underneath it ("popup jumps to another page").
        val focusManager = LocalFocusManager.current
        val editTracker = LocalDeckEditTracker.current
        val dialogView = LocalView.current
        val rootFocus = remember { FocusRequester() }
        DeckDialogControllerBridge(rootFocus, editTracker, onDismiss)
        // post() runs after the dialog window is attached; requestFocus before that silently
        // fails and leaves the D-pad on the screen behind.
        LaunchedEffect(dialogView) {
            dialogView.post {
                runCatching { rootFocus.requestFocus() }
                // Enter the content so the first press lands on a real control, not the root.
                runCatching { focusManager.moveFocusSafely(FocusDirection.Next) }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(24.dp))
                .background(DeckPalette.surface)
                .border(1.dp, DeckPalette.line2, RoundedCornerShape(24.dp))
                .focusRequester(rootFocus)
                .focusable()
                .focusGroup()
                .onPreviewKeyEvent { e ->
                    when {
                        e.type != KeyEventType.KeyDown -> false
                        e.key == Key.ButtonB || e.key == Key.Back -> {
                            // While a text field is editing, B leaves the field instead of closing.
                            if (editTracker.editing) false else { onDismiss(); true }
                        }
                        e.key == Key.ButtonA -> {
                            // The Activity rewrites A→DPAD_CENTER, but a Dialog window never sees
                            // that rewrite, so activate the focused control from here.
                            val now = SystemClock.uptimeMillis()
                            dialogView.dispatchKeyEvent(
                                android.view.KeyEvent(
                                    now, now, android.view.KeyEvent.ACTION_DOWN,
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0
                                )
                            )
                            dialogView.dispatchKeyEvent(
                                android.view.KeyEvent(
                                    now, now, android.view.KeyEvent.ACTION_UP,
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0
                                )
                            )
                            true
                        }
                        else -> false
                    }
                }
                .onKeyEvent { e ->
                    // Bubbles up from the focused child, so a focused text field keeps its own keys
                    // and everything else still walks the popup with the D-pad.
                    if (e.type != KeyEventType.KeyDown || editTracker.editing) return@onKeyEvent false
                    val dir = when (e.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionRight -> FocusDirection.Right
                        else -> null
                    } ?: return@onKeyEvent false
                    // A direction with no geometric target (the root just took focus, or the
                    // content is a single column) falls back to Next so the popup still walks.
                    deckStepIsDuplicate(dir) || focusManager.stepForm(dir)
                },
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = title,
                        color = DeckPalette.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = DeckPalette.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = DeckPalette.onSurfaceVariant)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                content = content,
            )
        }
    }
}

/** A "?" chip that opens the shared help dialog for a string resource. */
@Composable
internal fun DeckHelpButton(text: String) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }) {
        Icon(
            Icons.Outlined.HelpOutline,
            contentDescription = "What is this?",
            tint = DeckPalette.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
    if (show) HelpTextDialog(text) { show = false }
}

// ─────────────────────────────────────────────────────────────────────────────
// Environment variables — a Deck-native, structured editor. Storage format is
// unchanged: a single space-separated "NAME=VALUE" string (core/EnvVars).
// ─────────────────────────────────────────────────────────────────────────────

private data class DeckEnvRow(val id: Int, val name: String, val value: String)

private fun parseDeckEnvRows(raw: String, nextId: () -> Int): List<DeckEnvRow> =
    raw.split(" ").filter { it.isNotBlank() }.mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else DeckEnvRow(nextId(), part.substring(0, i), part.substring(i + 1))
    }

private fun renderDeckEnvRows(rows: List<DeckEnvRow>): String =
    rows.filter { it.name.isNotBlank() }.joinToString(" ") { "${it.name}=${it.value}" }

private fun deckEnvHelp(context: android.content.Context, name: String): String? =
    StringUtils.getString(context, "env_var_help__" + name.lowercase(java.util.Locale.ENGLISH))

private fun deckDefaultValueFor(name: String): String {
    val known = KnownEnvVars.find(name) ?: return ""
    known.defaultValue?.let { return it }
    return when (known.type) {
        EnvVarType.CHECKBOX -> known.options.getOrElse(0) { "0" }
        EnvVarType.SELECT -> known.options.firstOrNull() ?: ""
        else -> ""
    }
}

@Composable
internal fun DeckEnvVarsEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    gameDir: File? = null,
) {
    val context = LocalContext.current
    val idSource = remember { intArrayOf(0) }
    val nextId = { idSource[0]++ }
    val rows: SnapshotStateList<DeckEnvRow> = remember {
        mutableStateListOf<DeckEnvRow>().apply { addAll(parseDeckEnvRows(value, nextId)) }
    }
    var lastEmitted by remember { mutableStateOf(value) }
    var dllBaseline by remember {
        mutableStateOf(DllOverrides.baselineOf(rows.firstOrNull { it.name == DllOverrides.VAR }?.value ?: ""))
    }
    var rawMode by remember { mutableStateOf(false) }
    var foundDlls by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAddPicker by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != lastEmitted) {
            rows.clear()
            rows.addAll(parseDeckEnvRows(value, nextId))
            lastEmitted = value
            dllBaseline = DllOverrides.baselineOf(rows.firstOrNull { it.name == DllOverrides.VAR }?.value ?: "")
        }
    }
    LaunchedEffect(gameDir) {
        val dir = gameDir ?: return@LaunchedEffect
        foundDlls = withContext(Dispatchers.IO) {
            runCatching {
                val present = dir.list()?.map { it.lowercase() }?.toSet() ?: emptySet()
                DllOverrides.PREFER_GAME_FOLDER.filter { "$it.dll" in present }
            }.getOrDefault(emptyList())
        }
    }

    fun emit() {
        val rendered = renderDeckEnvRows(rows)
        lastEmitted = rendered
        onValueChange(rendered)
    }

    fun setValue(index: Int, newName: String, newValue: String) {
        rows[index] = rows[index].copy(name = newName, value = newValue)
        emit()
    }

    fun putVar(name: String, newValue: String) {
        val index = rows.indexOfFirst { it.name == name }
        if (newValue.isEmpty()) {
            if (index >= 0) rows.removeAt(index)
        } else if (index >= 0) {
            rows[index] = rows[index].copy(value = newValue)
        } else {
            rows.add(DeckEnvRow(nextId(), name, newValue))
        }
        emit()
    }

    val overrides = rows.firstOrNull { it.name == DllOverrides.VAR }?.value ?: ""
    val masterScope = if (foundDlls.isNotEmpty()) foundDlls else DllOverrides.PREFER_GAME_FOLDER
    val allPreferred = DllOverrides.isEnabled(overrides, masterScope)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DeckConfigPanel("Compatibility") {
            DeckRow(
                label = "Prefer game-folder DLLs",
                hint = "Loads the game's own copies of ${DllOverrides.PREFER_GAME_FOLDER.joinToString(", ")}",
                control = {
                    DeckToggle(allPreferred) { on ->
                        putVar(
                            DllOverrides.VAR,
                            if (on) DllOverrides.enable(overrides, masterScope)
                            else DllOverrides.disable(overrides, dllBaseline, masterScope),
                        )
                    }
                },
            )
            foundDlls.forEach { dll ->
                DeckRow(
                    label = "$dll.dll",
                    hint = "Detected in the game folder",
                    control = {
                        DeckToggle(DllOverrides.isEnabled(overrides, dll)) { checked ->
                            putVar(
                                DllOverrides.VAR,
                                if (checked) DllOverrides.enable(overrides, dll)
                                else DllOverrides.disable(overrides, dllBaseline, dll),
                            )
                        }
                    },
                )
            }
        }

        DeckConfigPanel("Environment Variables") {
            if (rawMode) {
                var rawText by remember { mutableStateOf(renderDeckEnvRows(rows)) }
                val malformed = rawText.split(" ").filter { it.isNotBlank() && it.indexOf('=') <= 0 }
                DeckConfigTextField(
                    label = "NAME=VALUE, separated by spaces",
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        rows.clear()
                        rows.addAll(parseDeckEnvRows(it, nextId))
                        lastEmitted = it
                        onValueChange(it)
                    },
                    singleLine = false,
                    minHeight = 120.dp,
                    isError = malformed.isNotEmpty(),
                )
                if (malformed.isNotEmpty()) {
                    DeckConfigNote("Ignored — needs NAME=VALUE: ${malformed.joinToString(", ")}", error = true)
                }
            } else if (rows.isEmpty()) {
                DeckConfigNote("No environment variables set")
            } else {
                rows.forEachIndexed { index, row ->
                    key(row.id) {
                        DeckEnvVarRow(
                            row = row,
                            onValueChange = { setValue(index, row.name, it) },
                            onRemove = { rows.removeAt(index); emit() },
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            DeckButton("Add Variable", primary = true) { showAddPicker = true }
            DeckButton(if (rawMode) "Done" else "Edit as text", primary = false) { rawMode = !rawMode }
        }
    }

    if (showAddPicker) {
        DeckAddEnvVarPicker(
            existingNames = rows.map { it.name }.toSet(),
            onAdd = { raw ->
                val cleaned = raw.trim().replace(" ", "")
                val eq = cleaned.indexOf('=')
                val name = if (eq >= 0) cleaned.substring(0, eq) else cleaned
                val v = if (eq >= 0) cleaned.substring(eq + 1) else deckDefaultValueFor(cleaned)
                if (name.isNotEmpty() && rows.none { it.name == name }) {
                    rows.add(DeckEnvRow(nextId(), name, v))
                    emit()
                }
                showAddPicker = false
            },
            onDismiss = { showAddPicker = false },
        )
    }
}

@Composable
private fun DeckEnvVarRow(
    row: DeckEnvRow,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val known = KnownEnvVars.find(row.name)
    val type = known?.type ?: EnvVarType.TEXT
    val help = remember(row.name) { deckEnvHelp(context, row.name) }
    var showHelp by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                when (type) {
                    EnvVarType.CHECKBOX -> {
                        val offValue = known?.options?.getOrNull(0) ?: "0"
                        val onValue = known?.options?.getOrNull(1) ?: "1"
                        val on = row.value == onValue || row.value == "1" || row.value == "true"
                        DeckRow(
                            label = row.name,
                            control = { DeckToggle(on) { onValueChange(if (it) onValue else offValue) } },
                        )
                    }
                    EnvVarType.SELECT_MULTIPLE -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(row.name, color = DeckPalette.onBackground, fontSize = 13.sp)
                            val selected = row.value.split(",").filter { it.isNotBlank() }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                known?.options.orEmpty().forEach { option ->
                                    val isOn = option in selected
                                    DeckConfigChip(option, isOn) {
                                        val next = if (isOn) selected - option else selected + option
                                        onValueChange(next.joinToString(","))
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val presets = known?.options.orEmpty()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            DeckConfigTextField(
                                label = row.name,
                                value = row.value,
                                onValueChange = { onValueChange(it.replace(" ", "")) },
                                numeric = type == EnvVarType.NUMBER,
                            )
                            if (presets.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    presets.forEach { preset ->
                                        DeckConfigChip(preset, preset == row.value) { onValueChange(preset) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (help != null) {
                IconButton(onClick = { showHelp = true }) {
                    Icon(
                        Icons.Outlined.HelpOutline,
                        contentDescription = "About ${row.name}",
                        tint = DeckPalette.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove ${row.name}", tint = DeckPalette.error)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
    }
    if (showHelp && help != null) HelpTextDialog(help) { showHelp = false }
}

@Composable
private fun DeckAddEnvVarPicker(
    existingNames: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameQuery by remember { mutableStateOf("") }
    var valueQuery by remember { mutableStateOf("") }
    val rawName = nameQuery.trim().replace(" ", "")
    val eqIdx = rawName.indexOf('=')
    val name = if (eqIdx >= 0) rawName.substring(0, eqIdx) else rawName
    val value = if (eqIdx >= 0) rawName.substring(eqIdx + 1) else valueQuery.trim().replace(" ", "")
    val alreadyExists = name.isNotEmpty() && name in existingNames
    val isKnownName = name in KnownEnvVars.names
    val showAddTyped = name.isNotEmpty() && !alreadyExists && (value.isNotEmpty() || !isKnownName)
    val candidates = remember(name, existingNames) {
        KnownEnvVars.names
            .filter { it !in existingNames }
            .filter { name.isBlank() || it.contains(name, ignoreCase = true) }
    }

    DeckConfigDialog(title = "Add environment variable", onDismiss = onDismiss) {
        DeckConfigTextField("Name (search or type)", nameQuery, { nameQuery = it })
        DeckConfigTextField("Value (optional)", valueQuery, { valueQuery = it })
        when {
            rawName.isNotEmpty() && name.isEmpty() -> DeckConfigNote("Won't be saved — enter a name", error = true)
            alreadyExists -> DeckConfigNote("\"$name\" is already in the list", error = true)
            name.isNotEmpty() && value.isEmpty() -> DeckConfigNote("Saves as  $name =  (empty value)")
            name.isNotEmpty() -> DeckConfigNote("Saves as  $name = $value")
        }
        Spacer(Modifier.height(4.dp))
        if (showAddTyped) {
            DeckButton("Add  $name${if (value.isNotEmpty()) " = $value" else ""}") {
                onAdd(if (value.isEmpty()) name else "$name=$value")
            }
            Spacer(Modifier.height(6.dp))
        }
        candidates.forEach { known ->
            DeckRow(
                label = known,
                control = {
                    DeckButton("Add", primary = false) {
                        onAdd(if (value.isEmpty()) known else "$known=$value")
                    }
                },
            )
        }
        if (candidates.isEmpty() && !showAddTyped) DeckConfigNote("No matches")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WineD3D config — Deck-native.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DeckWineD3DConfigEditor(
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val config = remember(initialConfig) { com.winhub.hernandez.contentdialog.WineD3DConfigDialog.parseConfig(initialConfig) }

    val csmtOptions = listOf("Enabled" to "Enabled", "Disabled" to "Disabled")
    val ssmOptions = listOf("Enabled" to "Enabled", "Disabled" to "Disabled")
    val ormOptions = listOf("fbo" to "fbo", "backbuffer" to "backbuffer")
    val rendOptions = listOf("gl" to "gl", "vulkan" to "vulkan", "gdi" to "gdi")
    val ddraEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.ddrawrapper_entries).toList() }
    val videoMemEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.video_memory_size_entries).toList() }
    var gpuNames by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val names = com.winhub.hernandez.contentdialog.WineD3DConfigDialog.loadGpuNames(context)
            withContext(Dispatchers.Main) { gpuNames = names }
        }
    }

    var csmt by remember { mutableStateOf(if (config.get("csmt") == "3") "Enabled" else "Disabled") }
    var gpuName by remember { mutableStateOf(config.get("gpuName")) }
    var ddra by remember { mutableStateOf(ddraEntries.firstOrNull { StringUtils.parseIdentifier(it) == config.get("ddrawrapper") } ?: ddraEntries.first()) }
    var videoMem by remember {
        val stored = config.get("videoMemorySize")
        mutableStateOf(videoMemEntries.firstOrNull { StringUtils.parseNumber(it) == stored } ?: videoMemEntries.first())
    }
    var ssm by remember { mutableStateOf(if (config.get("strict_shader_math") == "1") "Enabled" else "Disabled") }
    var orm by remember { mutableStateOf(config.get("OffscreenRenderingMode").ifEmpty { "fbo" }) }
    var renderer by remember { mutableStateOf(config.get("renderer").ifEmpty { "gl" }) }

    DeckConfigPanel("WineD3D Configuration") {
        DeckChoiceRow(label = "CSMT", options = csmtOptions, selected = csmt, onPick = { csmt = it })
        DeckChoiceRow(
            label = "GPU Name",
            options = gpuNames.map { it to it },
            selected = gpuName,
            onPick = { gpuName = it },
        )
        DeckChoiceRow(
            label = "DDraw Wrapper",
            options = ddraEntries.map { it to it },
            selected = ddra,
            onPick = { ddra = it },
        )
        DeckChoiceRow(
            label = "Max Device Memory",
            options = videoMemEntries.map { it to it },
            selected = videoMem,
            onPick = { videoMem = it },
        )
        DeckChoiceRow(label = "Strict Shader Math", options = ssmOptions, selected = ssm, onPick = { ssm = it })
        DeckChoiceRow(label = "Offscreen Rendering Mode", options = ormOptions, selected = orm, onPick = { orm = it })
        DeckChoiceRow(label = "Renderer", options = rendOptions, selected = renderer, onPick = { renderer = it })
    }
    fun buildConfig(): String {
        val cfg = com.winhub.hernandez.contentdialog.WineD3DConfigDialog.parseConfig(initialConfig)
        cfg.put("csmt", if (csmt == "Enabled") "3" else "0")
        cfg.put("strict_shader_math", if (ssm == "Enabled") "1" else "0")
        cfg.put("OffscreenRenderingMode", orm)
        cfg.put("gpuName", gpuName)
        cfg.put("ddrawrapper", StringUtils.parseIdentifier(ddra))
        cfg.put("videoMemorySize", StringUtils.parseNumber(videoMem))
        cfg.put("renderer", renderer)
        return cfg.toString()
    }

    // Live commit: every change reaches the host immediately, so the screen's own Save button is
    // enough. "Done" only collapses the panel (it never gates the edit).
    LaunchedEffect(Unit) {
        snapshotFlow { buildConfig() }.collect { onChange?.invoke(it) }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        DeckButton("Done") { onConfirm(buildConfig()) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Renderer / Vulkan config — Deck-native.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DeckVulkanConfigEditor(
    initialConfig: String,
    frameGenSelected: Boolean,
    onConfirm: (String) -> Unit,
    onChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val cfg = remember(initialConfig) { parseVulkanConfig(initialConfig) }
    var nativeRender by remember { mutableStateOf(cfg["native"] == "true") }
    var presentMode by remember { mutableStateOf(cfg["presentMode"] ?: "fifo") }
    var driverId by remember { mutableStateOf(cfg["driverId"] ?: "system") }
    val filterMode = remember { cfg["filterMode"]?.toIntOrNull() ?: 0 }
    var swapRB by remember { mutableStateOf(cfg["swapRB"] == "true") }

    val rendererDriverOptions = remember {
        val installed = try {
            com.winhub.hernandez.contents.AdrenotoolsManager(context).enumarateInstalledDrivers()
        } catch (e: Exception) { arrayListOf<String>() }
        (listOf("system") + installed).distinct()
    }

    DeckConfigPanel("Renderer Configuration") {
        DeckRow(
            label = "Native Rendering",
            hint = "Direct scanout straight to the display",
            control = { DeckToggle(nativeRender) { nativeRender = it } },
        )
        DeckChoiceRow(
            label = "Present Mode",
            hint = if (nativeRender) "Ignored under native rendering" else null,
            options = listOf(
                "fifo" to "FIFO (vsync)",
                "mailbox" to "Mailbox",
                "immediate" to "Immediate",
            ),
            selected = presentMode,
            onPick = { presentMode = it },
        )
        if (frameGenSelected) {
            DeckConfigNote("Frame generation temporarily forces Mailbox while active.")
        }
        DeckChoiceRow(
            label = "Renderer Driver",
            hint = "Vulkan driver the present layer runs on",
            options = rendererDriverOptions.map { it to it },
            selected = if (rendererDriverOptions.contains(driverId)) driverId else "system",
            onPick = { driverId = it },
        )
        DeckChoiceRow(
            label = "Colors",
            hint = "Game buffer channel order",
            options = listOf("BGRA" to "BGRA", "RGBA" to "RGBA"),
            selected = if (swapRB) "RGBA" else "BGRA",
            onPick = { swapRB = it == "RGBA" },
        )
    }
    fun buildConfig(): String =
        "native=$nativeRender;presentMode=$presentMode;driverId=$driverId;filterMode=$filterMode;swapRB=$swapRB"

    // Live commit: every change reaches the host immediately, so the screen's own Save button is
    // enough. "Done" only collapses the panel (it never gates the edit).
    LaunchedEffect(Unit) {
        snapshotFlow { buildConfig() }.collect { onChange?.invoke(it) }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        DeckButton("Done") { onConfirm(buildConfig()) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FPS counter / HUD config — Deck-native.
// ─────────────────────────────────────────────────────────────────────────────

private fun parseDeckFpsConfig(s: String): Map<String, String> {
    if (s.isEmpty()) return emptyMap()
    val map = mutableMapOf<String, String>()
    s.split(",").forEach { part ->
        val eq = part.indexOf('=')
        if (eq >= 0) map[part.substring(0, eq)] = part.substring(eq + 1)
    }
    return map
}

@Composable
internal fun DeckFpsConfigEditor(
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onChange: ((String) -> Unit)? = null,
) {
    val cfg = remember(initialConfig) { parseDeckFpsConfig(initialConfig) }
    val diagContext = LocalContext.current
    fun bool(k: String, fallbackKey: String, d: String) =
        cfg.getOrDefault(k, cfg.getOrDefault(fallbackKey, d)) == "1"

    var hudEnabled by remember { mutableStateOf(bool("hudEnabled", "hudEnabled", "1")) }
    val hudMode = remember { cfg.getOrDefault("hudMode", "vertical") }
    val styles = listOf("classic", "gamehub", "gamenative", "fusion")
    var hudStyle by remember { mutableStateOf(cfg.getOrDefault("hudStyle", "fusion")) }
    val gameHub = hudStyle == "gamehub"
    val gameNative = hudStyle == "gamenative"
    val fusion = hudStyle == "fusion"
    val rich = gameHub || gameNative || fusion
    val fusionSizes = listOf("full", "tiles", "pill", "minimal", "mega")
    var fusionSize by remember { mutableStateOf(cfg.getOrDefault("hudSize", "pill")) }
    val fusionChips = com.winhub.hernandez.widget.fusionhud.FusionSize.from(fusionSize).supportedChips()
    val gpuModelDefault = if (cfg.getOrDefault("hudStyle", "fusion") == "fusion") "1" else "0"
    val clockDefault = if (cfg.getOrDefault("hudStyle", "fusion") == "fusion") "1" else "0"

    var showFPS by remember { mutableStateOf(bool("showFPS", "showFPS", "1")) }
    var showGraph by remember { mutableStateOf(bool("showFPSGraph", "showFPSGraph", "0")) }
    var showCPU by remember { mutableStateOf(bool("showCPUUsage", "showCPULoad", "1")) }
    var showGPU by remember { mutableStateOf(bool("showGPULoad", "showGPULoad", "1")) }
    var showRAM by remember { mutableStateOf(bool("showRAM", "showRAM", "1")) }
    var showPower by remember { mutableStateOf(bool("showPower", "showPower", "1")) }
    var showTemp by remember { mutableStateOf(bool("showTemp", "showBatteryTemp", "1")) }
    var showEngine by remember { mutableStateOf(bool("showEngine", "showRenderer", "1")) }
    var showGpuModel by remember { mutableStateOf(bool("showGpuModel", "showGpuModel", gpuModelDefault)) }
    var dualBattery by remember { mutableStateOf(bool("hudDualBattery", "hudDualBattery", "0")) }
    var showGpuTemp by remember { mutableStateOf(bool("showGpuTemp", "showGpuTemp", "0")) }
    var showBattery by remember { mutableStateOf(bool("showBattery", "showBattery", "0")) }
    var showRuntime by remember { mutableStateOf(bool("showRuntime", "showRuntime", "0")) }
    var showClock by remember { mutableStateOf(bool("showClock", "showClock", clockDefault)) }
    var showCpuGraph by remember { mutableStateOf(bool("showCPUGraph", "showCPUGraph", "0")) }
    var showGpuGraph by remember { mutableStateOf(bool("showGPUGraph", "showGPUGraph", "0")) }
    var showVram by remember { mutableStateOf(bool("showVram", "showVram", "1")) }
    var showLow001 by remember { mutableStateOf(bool("showLow001", "showLow001", "1")) }
    var fpsDecimal by remember { mutableStateOf(bool("fpsDecimal", "fpsDecimal", "1")) }
    var hudLocked by remember { mutableStateOf(bool("hudLocked", "hudLocked", "0")) }
    var showPerCore by remember { mutableStateOf(bool("showPerCore", "showPerCore", "1")) }
    var showSwap by remember { mutableStateOf(bool("showSwap", "showSwap", "1")) }
    var showNet by remember { mutableStateOf(bool("showNet", "showNet", "1")) }
    var showResolution by remember { mutableStateOf(bool("showResolution", "showResolution", "1")) }
    var showProton by remember { mutableStateOf(bool("showProton", "showProton", "1")) }
    var showWrapper by remember { mutableStateOf(bool("showWrapper", "showWrapper", "1")) }
    var showDxVer by remember { mutableStateOf(bool("showDxVer", "showDxVer", "1")) }
    var showSession by remember { mutableStateOf(bool("showSession", "showSession", "1")) }
    var tempUnitF by remember { mutableStateOf(cfg.getOrDefault("tempUnit", "c").equals("f", true)) }
    var tempBands by remember { mutableStateOf(cfg.getOrDefault("tempBands", "1") != "0") }
    var tempAuto by remember { mutableStateOf(cfg.getOrDefault("tempAuto", "1") != "0") }
    var tempRedCpu by remember { mutableStateOf(cfg.getOrDefault("tempRedCpu", "90").toIntOrNull() ?: 90) }
    var tempRedGpu by remember { mutableStateOf(cfg.getOrDefault("tempRedGpu", "90").toIntOrNull() ?: 90) }
    var tempRedBat by remember { mutableStateOf(cfg.getOrDefault("tempRedBat", "48").toIntOrNull() ?: 48) }
    var hudScale by remember { mutableStateOf(cfg.getOrDefault("hudScale", com.winhub.hernandez.container.Container.DEFAULT_HUD_SCALE.toString()).toIntOrNull() ?: com.winhub.hernandez.container.Container.DEFAULT_HUD_SCALE) }
    var hudOpacity by remember { mutableStateOf(cfg.getOrDefault("hudOpacity", "80").toIntOrNull() ?: 80) }
    var hudTransparency by remember { mutableStateOf(cfg.getOrDefault("hudTransparency", "0").toIntOrNull() ?: 0) }
    val skins = listOf("classic", "neon", "mono")
    val colors = listOf("soft", "mid", "vivid")
    var skin by remember { mutableStateOf(cfg.getOrDefault("hudSkin", "classic")) }
    var color by remember { mutableStateOf(cfg.getOrDefault("hudColor", "mid")) }
    var outlineValue by remember { mutableStateOf(com.winhub.hernandez.widget.perfhud.parseHudOutline(cfg.getOrDefault("hudOutline", "40"))) }
    var outlineAccent by remember { mutableStateOf(cfg.getOrDefault("hudOutlineAccent", "1") == "1") }

    fun i(v: Boolean) = if (v) "1" else "0"
    fun buildConfig(): String = listOf(
        "hudStyle=$hudStyle", "hudEnabled=${i(hudEnabled)}", "hudSize=$fusionSize", "hudLocked=${i(hudLocked)}",
        "showVram=${i(showVram)}", "showLow001=${i(showLow001)}", "fpsDecimal=${i(fpsDecimal)}",
        "showPerCore=${i(showPerCore)}", "showSwap=${i(showSwap)}", "showNet=${i(showNet)}",
        "showResolution=${i(showResolution)}", "showProton=${i(showProton)}", "showWrapper=${i(showWrapper)}",
        "showDxVer=${i(showDxVer)}", "showSession=${i(showSession)}", "hudMode=$hudMode",
        "showFPS=${i(showFPS)}", "showFPSGraph=${i(showGraph)}", "showCPUUsage=${i(showCPU)}", "showCPULoad=${i(showCPU)}",
        "showGPULoad=${i(showGPU)}", "showRAM=${i(showRAM)}", "showPower=${i(showPower)}",
        "showTemp=${i(showTemp)}", "showBatteryTemp=${i(showTemp)}", "showEngine=${i(showEngine)}",
        "showRenderer=${i(showEngine)}", "showGpuModel=${i(showGpuModel)}", "hudDualBattery=${i(dualBattery)}",
        "showGpuTemp=${i(showGpuTemp)}", "showBattery=${i(showBattery)}", "showRuntime=${i(showRuntime)}",
        "showClock=${i(showClock)}", "showCPUGraph=${i(showCpuGraph)}", "showGPUGraph=${i(showGpuGraph)}",
        "tempUnit=${if (tempUnitF) "f" else "c"}", "tempBands=${i(tempBands)}", "tempAuto=${i(tempAuto)}",
        "tempRedCpu=$tempRedCpu", "tempRedGpu=$tempRedGpu", "tempRedBat=$tempRedBat",
        "hudSkin=$skin", "hudColor=$color", "hudOutline=$outlineValue", "hudOutlineAccent=${if (outlineAccent) 1 else 0}",
        "hudScale=$hudScale", "hudOpacity=$hudOpacity", "hudTransparency=$hudTransparency",
    ).joinToString(",")

    DeckConfigPanel("HUD") {
        DeckRow(label = "Show HUD", control = { DeckToggle(hudEnabled) { hudEnabled = it } })
        DeckChoiceRow(
            label = "HUD style",
            options = listOf(
                "classic" to "Classic", "gamehub" to "GameHub",
                "gamenative" to "GameNative", "fusion" to "Fusion",
            ),
            selected = hudStyle,
            onPick = { hudStyle = it },
        )
        if (fusion) {
            DeckChoiceRow(
                label = "HUD size",
                options = fusionSizes.map { DeckPalette.titleCase(it) to it },
                selected = fusionSize,
                onPick = { fusionSize = it },
            )
        }
        DeckConfigNote("Tip: tap the HUD in-game to switch vertical/horizontal layout.")

        val metricChips = buildList<Triple<String, Boolean, () -> Unit>> {
            fun via(label: String, styleOk: Boolean): Boolean = if (fusion) label in fusionChips else styleOk
            if (via("FPS", true)) add(Triple("FPS", showFPS, { showFPS = !showFPS }))
            if (via("FPS graph", rich)) add(Triple("FPS graph", showGraph, { showGraph = !showGraph }))
            if (via("CPU", true)) add(Triple("CPU", showCPU, { showCPU = !showCPU }))
            if (!fusion && gameNative) add(Triple("CPU graph", showCpuGraph, { showCpuGraph = !showCpuGraph }))
            if (via("GPU", true)) add(Triple("GPU", showGPU, { showGPU = !showGPU }))
            if (!fusion && gameNative) add(Triple("GPU graph", showGpuGraph, { showGpuGraph = !showGpuGraph }))
            if (via("VRAM", false)) add(Triple("VRAM", showVram, { showVram = !showVram }))
            if (via("RAM", true)) add(Triple("RAM", showRAM, { showRAM = !showRAM }))
            if (via("Power", true)) add(Triple("Power", showPower, { showPower = !showPower }))
            if (via("Temp", true)) add(Triple("Temp", showTemp, { showTemp = !showTemp }))
            if (via("GPU temp", gameNative)) add(Triple("GPU temp", showGpuTemp, { showGpuTemp = !showGpuTemp }))
            if (via("Battery", gameNative)) add(Triple("Battery", showBattery, { showBattery = !showBattery }))
            if (!fusion && gameNative) add(Triple("Runtime", showRuntime, { showRuntime = !showRuntime }))
            if (via("0.01% low", false)) add(Triple("0.01% low", showLow001, { showLow001 = !showLow001 }))
            if (via("FPS .1", false)) add(Triple("FPS .1", fpsDecimal, { fpsDecimal = !fpsDecimal }))
            if (via("Per-core", false)) add(Triple("Per-core", showPerCore, { showPerCore = !showPerCore }))
            if (via("Swap", false)) add(Triple("Swap", showSwap, { showSwap = !showSwap }))
            if (via("Network", false)) add(Triple("Network", showNet, { showNet = !showNet }))
            if (via("Resolution", false)) add(Triple("Resolution", showResolution, { showResolution = !showResolution }))
            if (via("Proton", false)) add(Triple("Proton", showProton, { showProton = !showProton }))
            if (via("Wrapper", false)) add(Triple("Wrapper", showWrapper, { showWrapper = !showWrapper }))
            if (via("DX ver", false)) add(Triple("DX ver", showDxVer, { showDxVer = !showDxVer }))
            if (via("Session", false)) add(Triple("Session", showSession, { showSession = !showSession }))
            if (via("Clock", gameNative)) add(Triple("Clock", showClock, { showClock = !showClock }))
            if (via("Engine", true)) add(Triple("Engine", showEngine, { showEngine = !showEngine }))
            if (via("GPU model", rich)) add(Triple("GPU model", showGpuModel, { showGpuModel = !showGpuModel }))
            if (!fusion && gameHub) add(Triple("Dual battery", dualBattery, { dualBattery = !dualBattery }))
            add(Triple("Lock in place", hudLocked, { hudLocked = !hudLocked }))
        }
        DeckConfigChipGrid("Metrics", metricChips)
    }

    if (showTemp || ((gameNative || fusion) && (showGpuTemp || showBattery))) {
        DeckConfigPanel("Temperature Display") {
            val bandMode = if (!tempBands) 0 else if (tempAuto) 1 else 2
            DeckChoiceRow(
                label = "Temp unit",
                options = listOf("0" to "\u00B0C", "1" to "\u00B0F"),
                selected = if (tempUnitF) "1" else "0",
                onPick = { tempUnitF = it == "1" },
            )
            DeckChoiceRow(
                label = "Danger colors",
                options = listOf("0" to "Off", "1" to "Auto", "2" to "Manual"),
                selected = bandMode.toString(),
                onPick = {
                    val m = it.toInt()
                    tempBands = m != 0
                    tempAuto = m != 2
                },
            )
            if (bandMode == 2) {
                DeckSliderRow("CPU red point", null, tempRedCpu.toFloat(), 50f..110f, { "${it.toInt()}\u00B0C" }) { tempRedCpu = it.toInt() }
                if ((gameNative || fusion) && showGpuTemp) {
                    DeckSliderRow("GPU red point", null, tempRedGpu.toFloat(), 50f..110f, { "${it.toInt()}\u00B0C" }) { tempRedGpu = it.toInt() }
                }
                DeckSliderRow("Battery red point", null, tempRedBat.toFloat(), 35f..60f, { "${it.toInt()}\u00B0C" }) { tempRedBat = it.toInt() }
            }
        }
    }

    DeckConfigPanel("Appearance") {
        DeckSliderRow("HUD Scale", null, hudScale.toFloat(), 50f..150f, { "${it.toInt()}%" }) { hudScale = it.toInt().coerceAtLeast(50) }
        if (rich) {
            DeckSliderRow("HUD Opacity", null, hudOpacity.toFloat(), 0f..100f, { "${it.toInt()}%" }) { hudOpacity = it.toInt() }
            if (gameHub) {
                DeckChoiceRow(
                    label = "HUD skin",
                    options = listOf("classic" to "Classic", "neon" to "Neon", "mono" to "Mono"),
                    selected = skin,
                    onPick = { skin = it },
                )
            }
            DeckChoiceRow(
                label = "HUD color",
                options = listOf("soft" to "Soft", "mid" to "Mid", "vivid" to "Vivid"),
                selected = color,
                onPick = { color = it },
            )
            DeckSliderRow("HUD outline", null, outlineValue.toFloat(), 0f..100f, { it.toInt().toString() }) { outlineValue = it.toInt() }
            DeckRow(
                label = "Outline color",
                hint = if (outlineAccent) "Accent" else "Gray",
                control = { DeckToggle(outlineAccent) { outlineAccent = it } },
            )
        } else {
            DeckSliderRow("HUD Transparency", null, hudTransparency.toFloat(), 0f..50f, { it.toInt().toString() }) { hudTransparency = it.toInt() }
        }
    }

    // Live commit: every change reaches the host immediately, so the screen's own Save button is
    // enough. "Done" only collapses the panel (it never gates the edit).
    LaunchedEffect(Unit) {
        snapshotFlow { buildConfig() }.collect { onChange?.invoke(it) }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckButton("Export diagnostics", primary = false) {
            com.winhub.hernandez.widget.exportHudDiagnostics(diagContext)
        }
        DeckButton("Done") { onConfirm(buildConfig()) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Graphics driver config — Deck-native (Turnip / wrapper tuning).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DeckGraphicsDriverConfigEditor(
    graphicsDriver: String,
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onChange: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val cfg = remember(initialConfig) {
        initialConfig.split(";").associate { elem ->
            val parts = elem.split("=")
            parts[0] to if (parts.size > 1) parts[1] else ""
        }
    }

    var version by remember { mutableStateOf(cfg["version"] ?: "") }
    var vulkanVersion by remember { mutableStateOf(cfg["vulkanVersion"] ?: "1.4") }
    var gpuName by remember { mutableStateOf(cfg["gpuName"] ?: "Device") }
    var presentMode by remember { mutableStateOf(cfg["presentMode"] ?: "mailbox") }
    var resourceType by remember { mutableStateOf(cfg["resourceType"] ?: "auto") }
    var bcnEmulation by remember { mutableStateOf(cfg["bcnEmulation"] ?: "auto") }
    var bcnEmulationType by remember { mutableStateOf(cfg["bcnEmulationType"] ?: "software") }
    var bcnEmulationCache by remember { mutableStateOf(cfg["bcnEmulationCache"] ?: "0") }
    var bcnEmulationAstc by remember { mutableStateOf(cfg["bcnEmulationAstc"] == "1") }
    var syncFrame by remember { mutableStateOf(cfg["syncFrame"] == "1") }
    var disablePresentWait by remember { mutableStateOf(cfg["disablePresentWait"] == "1") }
    var fdDevFeatures by remember { mutableStateOf(cfg["fdDevFeatures"] == "1") }
    var turnipGmem by remember { mutableStateOf(cfg["turnipGmem"] ?: "auto") }
    val initialTurnipTokens = remember(initialConfig) {
        (cfg["turnipTokens"] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    var turnipForceCb by remember { mutableStateOf("forcecb" in initialTurnipTokens) }
    var turnipNoCb by remember { mutableStateOf("nocb" in initialTurnipTokens) }
    var turnipSysmem by remember { mutableStateOf("sysmem" in initialTurnipTokens) }
    var turnipDeckEmu by remember { mutableStateOf("deck_emu" in initialTurnipTokens) }
    var turnipSectionExpanded by remember { mutableStateOf(false) }

    val caps = remember(graphicsDriver) { com.winhub.hernandez.contents.WrapperManager(context).capsFor(graphicsDriver) }
    val isImported = remember(graphicsDriver) { com.winhub.hernandez.contents.WrapperManager(context).isImported(graphicsDriver) }
    val isIntegratedBcn = (caps.hasIcd && caps.hasBcnLayer) || (isImported && caps.hasIcd)
    val showBcnLayerSettings = caps.hasBcnLayer && (isImported || !caps.hasIcd)
    val isQualcomm = remember { com.winhub.hernandez.core.GPUInformation.getVendorID(null, null) == 0x5143 }
    val gpuModel = remember { com.winhub.hernandez.core.GPUInformation.extractModelName(com.winhub.hernandez.core.GPUInformation.getRenderer(null, context)) ?: "" }
    val isBcnLayer = graphicsDriver == "wrapper-bcn_layer" || graphicsDriver == "wrapper-compat-bcn"
    val isCompatDriver = graphicsDriver == "wrapper-compat-bcn"
    var bcnSectionExpanded by remember { mutableStateOf(false) }
    var bcnLayerAuto by remember { mutableStateOf(cfg["bcnLayerAuto"]?.let { it == "1" } ?: true) }
    var bcnTranscodeEtc2 by remember { mutableStateOf(cfg["bcnTranscodeEtc2"] == "1") }
    var bcnTranscodeAstc by remember { mutableStateOf(cfg["bcnTranscodeAstc"] == "1") }
    var bcnImageView by remember { mutableStateOf(cfg["bcnImageView"]?.let { it == "1" } ?: true) }
    var bcnDebugLog by remember { mutableStateOf(cfg["bcnDebugLog"] == "1") }
    var bcnCompatSparse by remember { mutableStateOf(cfg["bcnCompatSparse"] == "1") }
    var compatUseGamenative by remember { mutableStateOf(cfg["compatUseGamenative"] == "1") }

    var detectedKeys by remember(graphicsDriver) { mutableStateOf<List<String>>(emptyList()) }
    val detectedValues = remember(graphicsDriver) { mutableStateMapOf<String, String>() }
    LaunchedEffect(graphicsDriver, isImported) {
        if (!isImported) { detectedKeys = emptyList(); return@LaunchedEffect }
        val wm = com.winhub.hernandez.contents.WrapperManager(context)
        val hidden = withContext(Dispatchers.IO) { wm.hiddenKeys(graphicsDriver) }
        val keys = withContext(Dispatchers.IO) { wm.detectedEnvKeys(graphicsDriver) }
            .filter {
                it !in com.winhub.hernandez.contents.WrapperManager.HANDLED_ENV_KEYS &&
                    !com.winhub.hernandez.contents.WrapperManager.isDebugEnvKey(it) &&
                    !com.winhub.hernandez.contents.WrapperManager.isDriverInternalEnvKey(it) && it !in hidden
            }
        keys.forEach { k ->
            val def = com.winhub.hernandez.contents.WrapperSettingsDictionary.defFor(k)
            detectedValues[k] = when (def.type) {
                com.winhub.hernandez.contents.WrapperSettingsDictionary.Type.TOGGLE -> if (cfg[k] == "1") "1" else "0"
                else -> cfg[k] ?: ""
            }
        }
        detectedKeys = keys
    }

    val deviceMemoryEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.device_memory_entries).toList() }
    var selectedMemoryEntry by remember {
        val storedNum = cfg["maxDeviceMemory"] ?: "0"
        mutableStateOf(deviceMemoryEntries.firstOrNull { StringUtils.parseNumber(it) == storedNum } ?: deviceMemoryEntries.first())
    }

    var driverVersions by remember { mutableStateOf(listOf<String>()) }
    var gpuNames by remember { mutableStateOf(listOf("Device")) }
    var allExtensions by remember { mutableStateOf(listOf<String>()) }
    val initialBlacklist = remember(initialConfig) {
        (cfg["blacklistedExtensions"] ?: "").split(",").filter { it.isNotEmpty() }.toSet()
    }
    var blacklisted by remember { mutableStateOf(initialBlacklist) }
    var showAllDrivers by remember { mutableStateOf(false) }
    var showExtPicker by remember { mutableStateOf(false) }
    var isCustomDriver by remember { mutableStateOf(false) }

    LaunchedEffect(showAllDrivers) {
        val atVersions = withContext(Dispatchers.IO) {
            com.winhub.hernandez.contents.AdrenotoolsManager(context).enumarateInstalledDrivers()
        }
        val gpuList = withContext(Dispatchers.IO) {
            val list = mutableListOf("Device")
            try {
                val json = com.winhub.hernandez.core.FileUtils.readString(context, "gpu_cards.json")
                val arr = org.json.JSONArray(json)
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i).getString("name"))
            } catch (_: Exception) {}
            list
        }
        val wrapperVersions = context.resources
            .getStringArray(com.winhub.hernandez.R.array.wrapper_graphics_driver_version_entries)
            .let { arr ->
                if (showAllDrivers) arr.toList()
                else withContext(Dispatchers.IO) {
                    graphicsProbeMutex.withLock { arr.filter { com.winhub.hernandez.core.GPUInformation.isDriverSupported(it, context) } }
                }
            }
        val allVersions = wrapperVersions + atVersions
        gpuNames = gpuList
        if (version.isEmpty()) {
            version = wrapperVersions.firstOrNull { it.equals(com.winhub.hernandez.core.DefaultVersion.WRAPPER_ADRENO, ignoreCase = true) }
                ?: wrapperVersions.firstOrNull { it.equals(com.winhub.hernandez.core.DefaultVersion.WRAPPER, ignoreCase = true) }
                ?: wrapperVersions.firstOrNull() ?: version
        }
        // Never silently drop a stored version that isn't in the (filtered) list. An uninstalled or
        // GPU-filtered-out driver must round-trip: otherwise merely opening this panel and tapping
        // Apply overwrote the container's real driver version with the default (turnip-sdk36).
        driverVersions = if (version.isNotEmpty() && allVersions.none { it.equals(version, ignoreCase = true) })
            listOf(version) + allVersions else allVersions
    }

    LaunchedEffect(version) {
        if (version.isEmpty()) { allExtensions = emptyList(); isCustomDriver = false; return@LaunchedEffect }
        val probeUnsafe = withContext(Dispatchers.IO) {
            graphicsProbeMutex.withLock {
                val mgr = com.winhub.hernandez.contents.AdrenotoolsManager(context)
                val installed = mgr.enumarateInstalledDrivers()
                if (installed.none { it.equals(version, ignoreCase = true) }) false
                else !mgr.getLibraryName(version).startsWith("libvulkan", ignoreCase = true)
            }
        }
        if (probeUnsafe) {
            allExtensions = emptyList(); isCustomDriver = true
            if (version != cfg["version"]) blacklisted = emptySet()
            return@LaunchedEffect
        }
        val exts = withContext(Dispatchers.IO) {
            graphicsProbeMutex.withLock {
                com.winhub.hernandez.core.GPUInformation.enumerateExtensions(version, context)?.toList() ?: emptyList()
            }
        }
        allExtensions = exts
        isCustomDriver = exts.isEmpty()
        if (version != cfg["version"]) blacklisted = emptySet()
    }

    if (showExtPicker) {
        ExtensionPickerDialog(
            extensions = allExtensions,
            blacklisted = blacklisted,
            onDismiss = { showExtPicker = false },
            onConfirm = { newBlacklist -> blacklisted = newBlacklist; showExtPicker = false },
        )
    }

    val vulkanVersions = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.vulkan_version_entries).toList() }
    val presentModeEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.present_mode_entries).toList() }
    val resourceTypeEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.resource_type_entries).toList() }
    val bcnEmulationEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.bcn_emulation_entries).toList() }
    val bcnTypeEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.bcn_emulation_type_entries).toList() }
    val bcnCacheEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.bcn_emulation_cache_entries).toList() }

    DeckConfigPanel("Graphics Driver") {
        DeckChoiceRow("Vulkan Version", null, vulkanVersions.map { it to it }, vulkanVersion) { vulkanVersion = it }
        DeckChoiceRow("Driver Version", "Turnip / custom Vulkan driver", driverVersions.map { it to it }, version) { version = it }
        DeckRow(
            label = "Show Incompatible Drivers",
            hint = "List drivers your GPU can't load",
            control = { DeckToggle(showAllDrivers) { showAllDrivers = it } },
        )
        if (isCustomDriver) {
            DeckConfigNote("Custom Qualcomm (Adreno) driver — extensions load at game start; that's expected.")
        } else if (allExtensions.isNotEmpty()) {
            val enabled = allExtensions.size - blacklisted.size
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Available Extensions ($enabled/${allExtensions.size})", color = DeckPalette.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                DeckButton("Manage", primary = false) { showExtPicker = true }
            }
        }
        DeckChoiceRow("GPU Name", null, gpuNames.map { it to it }, gpuName) { gpuName = it }
        DeckChoiceRow("Max Device Memory", null, deviceMemoryEntries.map { it to it }, selectedMemoryEntry) { selectedMemoryEntry = it }
        DeckChoiceRow("Present Mode", null, presentModeEntries.map { it to it }, presentMode) { presentMode = it }
        DeckChoiceRow("Resource Type", null, resourceTypeEntries.map { it to it }, resourceType) { resourceType = it }
    }

    DeckConfigPanel("BCn / Texture Emulation") {
        if (gpuModel.isNotEmpty() || isQualcomm) {
            DeckConfigNote((if (gpuModel.isNotEmpty()) "GPU: $gpuModel — " else "") + "BCn/compat layers apply to Mali and other non-Qualcomm GPUs")
        }
        DeckChoiceRow("BCn Emulation", null, bcnEmulationEntries.map { it to it }, bcnEmulation) { bcnEmulation = it }
        DeckChoiceRow("BCn Emulation Type", null, bcnTypeEntries.map { it to it }, bcnEmulationType) { bcnEmulationType = it }
        DeckChoiceRow("BCn Emulation Cache", null, bcnCacheEntries.map { it to it }, bcnEmulationCache) { bcnEmulationCache = it }
        if (isIntegratedBcn) {
            DeckRow(label = "BCn → ASTC transcode", control = { DeckToggle(bcnEmulationAstc) { bcnEmulationAstc = it } })
        }
        if (!isBcnLayer && bcnEmulationType == "compute" && (bcnEmulation == "auto" || bcnEmulation == "full")) {
            DeckRow(label = "Decode BCn to ASTC", control = { DeckToggle(bcnTranscodeAstc) { bcnTranscodeAstc = it } })
        }
        DeckRow(label = "Sync Every Frame", control = { DeckToggle(syncFrame) { syncFrame = it } })
        DeckRow(label = "Disable Present Wait", control = { DeckToggle(disablePresentWait) { disablePresentWait = it } })
        DeckRow(label = "OneUI / HyperOS Fix", control = { DeckToggle(fdDevFeatures) { fdDevFeatures = it } })
    }

    DeckConfigPanel("Turnip") {
        DeckChoiceRow(
            label = "GMEM (tiled rendering)",
            hint = "Auto applies gmem only on Adreno 710/720/722",
            options = listOf(
                "auto" to "Auto (Adreno 710/720/722)",
                "on" to "Force On",
                "off" to "Force Off",
            ),
            selected = turnipGmem,
            onPick = { turnipGmem = it },
        )
        DeckExpandableRow(
            label = "Advanced Turnip (TU_DEBUG)",
            hint = "Expert debug tokens — off by default",
            expanded = turnipSectionExpanded,
            onToggle = { turnipSectionExpanded = !turnipSectionExpanded },
        ) {
            DeckRow(label = "forcecb", hint = "Force concurrent binning", control = { DeckToggle(turnipForceCb) { turnipForceCb = it } })
            DeckRow(label = "nocb", hint = "Disable concurrent binning", control = { DeckToggle(turnipNoCb) { turnipNoCb = it } })
            val sysmemBlocked = turnipGmem == "on"
            DeckRow(
                label = "sysmem",
                hint = if (sysmemBlocked) "Disabled — GMEM Force On overrides sysmem" else "Force sysmem (bypass GMEM)",
                control = { DeckToggle(turnipSysmem && !sysmemBlocked) { if (!sysmemBlocked) turnipSysmem = it } },
            )
            DeckRow(label = "deck_emu", hint = "Advertise as SteamDeck", control = { DeckToggle(turnipDeckEmu) { turnipDeckEmu = it } })
            DeckConfigNote("deck_emu requires a Banners-Turnip driver (ignored on stock Turnip).")
        }
    }

    if (showBcnLayerSettings) {
        DeckConfigPanel("BCn Layer Settings") {
            if (isQualcomm) DeckConfigNote("No effect on Adreno (native BCn) — applies to Mali/non-Qualcomm GPUs")
            DeckRow(label = "Force decode on all GPUs", control = { DeckToggle(bcnLayerAuto) { bcnLayerAuto = it } })
            DeckRow(label = "Transcode to ETC2", control = { DeckToggle(bcnTranscodeEtc2) { bcnTranscodeEtc2 = it } })
            DeckRow(label = "Transcode to ASTC", control = { DeckToggle(bcnTranscodeAstc) { bcnTranscodeAstc = it } })
            DeckRow(label = "Storage image path", control = { DeckToggle(bcnImageView) { bcnImageView = it } })
            DeckRow(label = "Debug log", control = { DeckToggle(bcnDebugLog) { bcnDebugLog = it } })
            if (isCompatDriver) {
                DeckRow(label = "Use GameNative engine", control = { DeckToggle(compatUseGamenative) { compatUseGamenative = it } })
                DeckRow(label = "Emulate sparse binding", control = { DeckToggle(bcnCompatSparse) { bcnCompatSparse = it } })
            }
        }
    }

    if (isImported) {
        DeckConfigPanel("Detected Settings (advanced)") {
            DeckConfigNote("Scanned from this wrapper — values are passed to the wrapper as-is.")
            if (detectedKeys.isEmpty()) {
                DeckConfigNote("No extra settings detected.")
            } else {
                detectedKeys.forEach { key ->
                    val def = com.winhub.hernandez.contents.WrapperSettingsDictionary.defFor(key)
                    val current = detectedValues[key] ?: ""
                    when (def.type) {
                        com.winhub.hernandez.contents.WrapperSettingsDictionary.Type.TOGGLE ->
                            DeckRow(label = def.label, hint = def.hint.ifBlank { null }, control = { DeckToggle(current == "1") { detectedValues[key] = if (it) "1" else "0" } })
                        com.winhub.hernandez.contents.WrapperSettingsDictionary.Type.DROPDOWN -> {
                            val selected = current.ifEmpty { def.default }.ifEmpty { def.choices.firstOrNull() ?: "" }
                            DeckChoiceRow(label = def.label, hint = def.hint.ifBlank { null }, options = def.choices.map { it to it }, selected = selected) { detectedValues[key] = it }
                        }
                        com.winhub.hernandez.contents.WrapperSettingsDictionary.Type.SLIDER -> {
                            val fv = current.toFloatOrNull() ?: def.default.toFloatOrNull() ?: def.min
                            DeckSliderRow(def.label, def.hint.ifBlank { null }, fv, def.min..(if (def.max > def.min) def.max else def.min + 1f), { it.toInt().toString() }) {
                                detectedValues[key] = it.toInt().toString()
                            }
                        }
                        com.winhub.hernandez.contents.WrapperSettingsDictionary.Type.TEXT ->
                            DeckConfigTextField(def.label, current, { detectedValues[key] = it })
                    }
                }
            }
        }
    }

    fun buildConfig(): String {
        val config = "vulkanVersion=$vulkanVersion;" +
            "version=$version;" +
            "blacklistedExtensions=${blacklisted.joinToString(",")};" +
            "maxDeviceMemory=${StringUtils.parseNumber(selectedMemoryEntry)};" +
            "presentMode=$presentMode;" +
            "syncFrame=${if (syncFrame) "1" else "0"};" +
            "disablePresentWait=${if (disablePresentWait) "1" else "0"};" +
            "resourceType=$resourceType;" +
            "bcnEmulation=$bcnEmulation;" +
            "bcnEmulationType=$bcnEmulationType;" +
            "bcnEmulationCache=$bcnEmulationCache;" +
            "bcnEmulationAstc=${if (bcnEmulationAstc) "1" else "0"};" +
            "bcnLayerAuto=${if (bcnLayerAuto) "1" else "0"};" +
            "bcnTranscodeEtc2=${if (bcnTranscodeEtc2) "1" else "0"};" +
            "bcnTranscodeAstc=${if (bcnTranscodeAstc) "1" else "0"};" +
            "bcnImageView=${if (bcnImageView) "1" else "0"};" +
            "bcnDebugLog=${if (bcnDebugLog) "1" else "0"};" +
            "bcnCompatSparse=${if (bcnCompatSparse) "1" else "0"};" +
            "compatUseGamenative=${if (compatUseGamenative) "1" else "0"};" +
            "gpuName=$gpuName" +
            ";fdDevFeatures=${if (fdDevFeatures) "1" else "0"}" +
            ";turnipGmem=$turnipGmem" +
            ";turnipTokens=" + buildList {
                if (turnipForceCb) add("forcecb")
                if (turnipNoCb) add("nocb")
                if (turnipSysmem && turnipGmem != "on") add("sysmem")
                if (turnipDeckEmu) add("deck_emu")
            }.joinToString(",")
        val detectedPart = detectedKeys.joinToString("") { key ->
            val v = (detectedValues[key] ?: "").replace(";", "").replace("=", "")
            ";$key=$v"
        }
        return config + detectedPart
    }

    // Live commit: every change reaches the host immediately, so the screen's own Save button is
    // enough. "Done" only collapses the panel (it never gates the edit).
    LaunchedEffect(Unit) {
        snapshotFlow { buildConfig() }.collect { onChange?.invoke(it) }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        DeckButton("Done") { onConfirm(buildConfig()) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DXVK / VKD3D / VEGAS config — Deck-native, rendered inline (never a popup).
// Same config keys and file semantics as the classic DxvkConfigDialog.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DeckDxvkConfigEditor(
    isArm64EC: Boolean,
    isVegas: Boolean,
    containerRootDir: java.io.File?,
    relaxDxvkFilter: Boolean,
    initialConfig: String,
    onConfirm: (String) -> Unit,
    onChange: ((String) -> Unit)? = null,
    onOpenConfigDownload: (() -> Unit)? = null,
    refreshKey: Int = 0,
    onDownloadDxvk: (() -> Unit)? = null,
    onDownloadVkd3d: (() -> Unit)? = null,
    onDownloadD7vk: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember(initialConfig) { DXVKConfigDialog.parseConfig(initialConfig) }
    val activity = context.findActivity()

    val BROWSE_CONFIG_MARKER = "Browse for file…"

    val allDxvkVersions = remember { mutableStateOf(listOf<String>()) }
    val vkd3dVersions = remember { mutableStateOf(listOf<String>()) }
    val configSourceEntries = remember { mutableStateOf(listOf<String>()) }
    val d7vkVersions = remember { mutableStateOf(listOf(DXVKConfigDialog.D7VK_BUNDLED)) }
    val stockSources = remember { mutableStateOf(listOf<DXVKConfigDialog.StockSource>()) }
    val installedVegasVersions = remember { mutableStateOf(listOf<String>()) }
    val customEntries = remember { mutableStateOf(listOf<String>()) }
    var useDefaults by remember { mutableStateOf(true) }
    var selectedStock by remember { mutableStateOf<String?>(null) }
    var selectedCustom by remember { mutableStateOf<String?>(null) }
    var stockEdited by remember { mutableStateOf(false) }
    var toggleVersion by remember { mutableStateOf(0) }
    var valuePickerRow by remember { mutableStateOf<VegasKeyKnowledge.EditRow?>(null) }
    var customValueDraft by remember { mutableStateOf("") }
    var pendingDeleteKey by remember { mutableStateOf<String?>(null) }
    var showAddKey by remember { mutableStateOf(false) }
    var addKeyDraft by remember { mutableStateOf("") }
    var addValueDraft by remember { mutableStateOf("") }
    var tierChoice by remember { mutableStateOf<Int?>(null) }
    val tierCtx = LocalContext.current
    val gpuModel = remember { VegasTierPresets.readGpuModel(tierCtx) }
    val detectedTier = remember { VegasTierPresets.classifyModel(gpuModel) }
    var showBackups by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(refreshKey) {
        withContext(Dispatchers.IO) {
            val cm = ContentsManager(context)
            cm.syncContents()
            val versions = if (isVegas) DXVKConfigDialog.loadVegasVersionList(context, cm)
            else DXVKConfigDialog.loadDxvkVersionList(context, cm, isArm64EC)
            val vkd3d = DXVKConfigDialog.loadVkd3dVersionList(context, cm)
            val d7vk = DXVKConfigDialog.loadD7vkVersionList(context, cm)
            val cfgsrc = DXVKConfigDialog.loadVegasConfigSourceList(context)
            val stock = if (isVegas) DXVKConfigDialog.loadVegasStockSources(context, cm) else listOf()
            val installed = if (isVegas)
                cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VEGAS)
                    ?.mapNotNull { it.verName }?.distinct().orEmpty()
            else listOf()
            withContext(Dispatchers.Main) {
                // Preserve a stored DXVK/VEGAS version even when it's no longer installed, so
                // opening the panel and tapping Apply can't silently revert it to the first
                // installed build (e.g. 1.11.1-sarek).
                val storedDxvkVersion = config.get("version")
                allDxvkVersions.value =
                    if (storedDxvkVersion.isNotEmpty() && versions.none { it == storedDxvkVersion })
                        listOf(storedDxvkVersion) + versions
                    else versions
                vkd3dVersions.value = vkd3d
                d7vkVersions.value = d7vk
                configSourceEntries.value = cfgsrc
                stockSources.value = stock
                installedVegasVersions.value = installed
                val stored = config.get("dxvkConfigFile")
                val stockMatch = stock.firstOrNull { it.file.absolutePath == stored }
                val customBase = cfgsrc.filter { it != "None" }
                customEntries.value = if (stored.isNotEmpty() && stored !in customBase) customBase + stored else customBase
                when {
                    stored.isEmpty() -> useDefaults = true
                    stockMatch != null -> { selectedStock = stockMatch.displayLabel(); selectedCustom = null; useDefaults = false }
                    else -> { selectedCustom = stored; selectedStock = null; useDefaults = false }
                }
            }
        }
    }

    var selectedVkd3d by remember { mutableStateOf(config.get("vkd3dVersion").ifEmpty { "None" }) }

    val storedDxvkForFilter = config.get("version")
    val filteredDxvk = remember(selectedVkd3d, allDxvkVersions.value, relaxDxvkFilter, storedDxvkForFilter) {
        val base = if (selectedVkd3d != "None" && !relaxDxvkFilter) {
            allDxvkVersions.value.filter { v -> val major = DXVKConfigDialog.tryGetMajor(v); major == null || major >= 2 }
        } else allDxvkVersions.value
        // Keep the stored version visible even if the VKD3D major-version filter would hide it.
        if (storedDxvkForFilter.isNotEmpty() && base.none { it == storedDxvkForFilter })
            listOf(storedDxvkForFilter) + base else base
    }
    var selectedDxvk by remember(allDxvkVersions.value) {
        val stored = config.get("version")
        mutableStateOf(allDxvkVersions.value.firstOrNull { it == stored } ?: allDxvkVersions.value.firstOrNull() ?: stored)
    }
    val dxvkType = remember(selectedDxvk) { DXVKConfigDialog.getDXVKType(selectedDxvk) }

    val framerateEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.dxvk_framerate_entries).toList() }
    val featureLevelEntries = remember { DXVKConfigDialog.VKD3D_FEATURE_LEVEL.toList() }
    val ddraEntries = remember { context.resources.getStringArray(com.winhub.hernandez.R.array.ddrawrapper_entries).toList() }
    val anisotropyLabels = remember { listOf("Game default", "2x", "4x", "8x", "16x") }
    val lodBiasLabels = remember {
        listOf("Game default", "Auto (match scaling mode)", "Sharper (-0.25)", "Sharper (-0.5)", "Sharper (-0.75)", "Sharpest (-1.0)")
    }
    var selectedFramerate by remember {
        val stored = config.get("framerate")
        mutableStateOf(framerateEntries.firstOrNull { StringUtils.parseNumber(it) == stored } ?: framerateEntries.first())
    }
    var selectedFeatureLevel by remember { mutableStateOf(featureLevelEntries.firstOrNull { it == config.get("vkd3dLevel") } ?: featureLevelEntries.first()) }
    var selectedDdra by remember { mutableStateOf(ddraEntries.firstOrNull { StringUtils.parseIdentifier(it) == config.get("ddrawrapper") } ?: ddraEntries.first()) }
    var selectedD7vk by remember(d7vkVersions.value) {
        val stored = config.get("d7vkVersion")
        mutableStateOf(d7vkVersions.value.firstOrNull { it == stored } ?: DXVKConfigDialog.D7VK_BUNDLED)
    }
    var asyncEnabled by remember { mutableStateOf(config.get("async") == "1") }
    var asyncCacheEnabled by remember { mutableStateOf(config.get("asyncCache") == "1") }
    var selectedAnisotropy by remember {
        val i = DXVKConfigDialog.ANISOTROPY_VALUES.indexOf(config.get("anisotropy"))
        mutableStateOf(anisotropyLabels[if (i >= 0) i else 0])
    }
    var selectedLodBias by remember {
        val i = DXVKConfigDialog.LOD_BIAS_VALUES.indexOf(config.get("lodBias"))
        mutableStateOf(lodBiasLabels[if (i >= 0) i else 0])
    }

    val vegasKnowledge = remember {
        val k = DXVKConfigDialog.loadVegasKeyKnowledge(context)
        val saved = context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE)
            .getString("released_tail", null)?.split("|")?.filter { it.isNotBlank() }
        if (!saved.isNullOrEmpty()) k?.mergeReleasedTail(saved)
        k
    }
    val vegasCatalog = remember {
        val cat = DXVKConfigDialog.loadVegasKeyCatalog(context)
        if (cat != null) {
            val tail = context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE)
                .getString("catalog_tail", null)?.split("|")?.filter { it.isNotBlank() }
            if (!tail.isNullOrEmpty()) cat.mergeTailTags(tail)
        }
        cat
    }
    val activeStockTag = remember(selectedStock, stockSources.value) {
        stockSources.value.firstOrNull { it.verName == selectedStock || it.displayLabel() == selectedStock }?.tag
    }
    val forkFilterPrefs = remember { context.getSharedPreferences("vegas_config_ui", Context.MODE_PRIVATE) }
    var forkFilter by remember { mutableStateOf(forkFilterPrefs.getBoolean("forkFilter", false)) }

    val stockPathForSelected = remember(selectedStock, stockSources.value) {
        selectedStock?.let { s -> stockSources.value.firstOrNull { it.verName == s || it.displayLabel() == s }?.file?.absolutePath }
    }
    val sidecarFallback = remember(selectedStock, stockSources.value) {
        stockSources.value.firstOrNull { it.verName == selectedStock || it.displayLabel() == selectedStock }
            ?.verName?.removePrefix("vegas-")?.substringBefore(" ·")
            ?: selectedStock?.removePrefix("vegas-")?.substringBefore(" ·")
    }
    val sidecarBase = remember(activeStockTag, sidecarFallback) {
        (activeStockTag?.takeIf { it.isNotBlank() } ?: sidecarFallback)?.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
    val sidecarPath = remember(containerRootDir, sidecarBase) {
        if (sidecarBase == null) null
        else {
            val dir = if (containerRootDir != null) java.io.File(java.io.File(containerRootDir, "vegas"), "configs")
            else java.io.File(context.filesDir, "vegas-defaults/configs")
            java.io.File(dir, sidecarBase + ".user.conf").absolutePath
        }
    }
    val sidecarExists = remember(toggleVersion, sidecarPath) {
        sidecarPath != null && java.io.File(sidecarPath).isFile
    }
    val resolvedSidecarPath = remember(sidecarPath, toggleVersion) {
        if (sidecarPath != null && java.io.File(sidecarPath).isFile) sidecarPath else sidecarPath
    }
    val liveFile = remember(useDefaults, selectedStock, selectedCustom, stockPathForSelected, resolvedSidecarPath, sidecarExists) {
        when {
            useDefaults -> null
            selectedStock != null && sidecarExists && resolvedSidecarPath != null -> java.io.File(resolvedSidecarPath)
            selectedStock != null -> stockPathForSelected?.let { java.io.File(it) }
            selectedCustom != null -> java.io.File(selectedCustom!!)
            else -> null
        }
    }
    val livePath = liveFile?.absolutePath ?: ""
    val backupsList = remember(liveFile, toggleVersion) {
        val dir = liveFile?.parentFile; val name = liveFile?.name
        if (dir == null || name == null || !dir.isDirectory) emptyList()
        else dir.listFiles { f -> f.isFile && f.name.startsWith(name + ".bak") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    val configSourceText = remember(useDefaults, liveFile, toggleVersion) {
        if (useDefaults || liveFile == null) ""
        else if (liveFile.isFile) runCatching { liveFile.readText() }.getOrDefault("") else ""
    }
    val configRows = remember(vegasKnowledge, configSourceText, selectedDxvk) {
        if (vegasKnowledge != null) vegasKnowledge.editRows(configSourceText, selectedDxvk)
        else VegasKeyKnowledge.editRowsUnclassified(configSourceText)
    }
    val baselineRowsForSelected = remember(activeStockTag, vegasKnowledge, stockSources.value, selectedDxvk) {
        if (selectedStock == null) emptyList()
        else {
            val f = stockSources.value.firstOrNull { it.tag == activeStockTag }?.file
            if (f == null || !f.isFile) emptyList()
            else (vegasKnowledge?.editRows(runCatching { f.readText() }.getOrDefault(""), selectedDxvk)
                ?: VegasKeyKnowledge.editRowsUnclassified(runCatching { f.readText() }.getOrDefault("")))
        }
    }
    val pendingRows = remember(baselineRowsForSelected, configRows) {
        val activeKeys = configRows.map { it.key }.toSet()
        baselineRowsForSelected.filter { it.key !in activeKeys }
    }
    val activeForceTier = remember(configRows, toggleVersion) {
        configRows.firstOrNull { it.key == "vegas.forceTier" }?.value?.toIntOrNull()
    }

    fun commitConfigWrite(isStockPath: Boolean, transform: (String) -> String?) {
        val target = liveFile ?: return
        val intendedSidecar = sidecarPath
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (!target.isFile) return@withContext false
                val text = runCatching { target.readText() }.getOrNull() ?: return@withContext false
                val next = transform(text) ?: return@withContext false
                val autoBak = java.io.File(target.absolutePath + ".bak")
                if (!autoBak.isFile) runCatching { java.nio.file.Files.copy(target.toPath(), autoBak.toPath()) }
                if (!target.parentFile.isDirectory) target.parentFile.mkdirs()
                if (isStockPath && !sidecarExists && intendedSidecar != null) {
                    val s = java.io.File(intendedSidecar!!)
                    if (!s.parentFile.exists() && !s.parentFile.mkdirs()) return@withContext false
                    runCatching { s.writeText(next) }.isSuccess
                } else {
                    runCatching { target.writeText(next) }.isSuccess
                }
            }
            if (ok) { if (isStockPath) stockEdited = true; toggleVersion++ }
            else if (activity != null) Toast.makeText(activity, "Failed to update config file", Toast.LENGTH_SHORT).show()
        }
    }

    fun applyToggle(key: String, value: String, enable: Boolean) {
        if (useDefaults || liveFile == null) return
        val isStockPath = selectedStock != null
        if (isStockPath && activeStockTag != null && vegasCatalog != null && vegasCatalog.isWrongFamily(key, activeStockTag)) return
        commitConfigWrite(isStockPath) { text ->
            VegasKeyKnowledge.toggleLine(text, key, enable) ?: if (enable) VegasKeyKnowledge.setLine(text, key, value) else text
        }
    }
    fun applyValue(key: String, value: String) {
        if (useDefaults || liveFile == null) return
        commitConfigWrite(selectedStock != null) { text -> VegasKeyKnowledge.setLine(text, key, value) }
    }
    fun applyAddKey(key: String, value: String) {
        if (useDefaults || liveFile == null) return
        commitConfigWrite(selectedStock != null) { text -> VegasKeyKnowledge.setLine(text, key, value) }
    }
    fun applyDeleteKey(key: String) {
        if (useDefaults || liveFile == null) return
        commitConfigWrite(selectedStock != null) { text -> VegasKeyKnowledge.removeLine(text, key) }
    }
    fun restoreBackup(backup: java.io.File) {
        val target = liveFile ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (!target.isFile) return@withContext false
                val content = runCatching { backup.readText() }.getOrNull() ?: return@withContext false
                if (!runCatching { target.writeText(content) }.isSuccess) return@withContext false
                if (backup.name == target.name + ".bak") backup.delete()
                true
            }
            if (ok) { stockEdited = true; toggleVersion++ }
        }
    }
    fun manualBackup() {
        val target = liveFile ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (!target.isFile) return@withContext false
                var bak = java.io.File(target.absolutePath + ".bak-manual")
                var n = 2
                while (bak.isFile) { bak = java.io.File(target.absolutePath + ".bak-manual-" + n); n++ }
                runCatching { java.nio.file.Files.copy(target.toPath(), bak.toPath()) }.isSuccess
            }
            if (ok) toggleVersion++
        }
    }

    val pickCustomConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val path = InAppFilePicker.pickedPath(result.data)
            if (path != null) {
                selectedCustom = path; selectedStock = null; useDefaults = false
                if (path !in customEntries.value) customEntries.value = customEntries.value + path
            }
        }
    }

    val isDxvkLike = selectedDxvk.let { it.contains("dxvk") || it.contains("vegas") || it.isNotEmpty() }

    DeckConfigPanel(if (isVegas) "VEGAS / VKD3D" else "DXVK / VKD3D") {
        DeckChoiceRowWithDownload("VKD3D Version", vkd3dVersions.value.map { it to it }, selectedVkd3d, { selectedVkd3d = it }, onDownloadVkd3d)
        if (isVegas) {
            if (filteredDxvk.isEmpty()) {
                DeckConfigNote("No VEGAS build installed — download one via the config manager.")
            } else {
                DeckChoiceRow("VEGAS Version", null, filteredDxvk.map { it to it }, selectedDxvk) { selectedDxvk = it }
            }
            if (onOpenConfigDownload != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Manage VEGAS builds", color = DeckPalette.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    DeckButton("Download", primary = false) { onOpenConfigDownload() }
                }
            }
        } else {
            DeckChoiceRowWithDownload("DXVK Version", filteredDxvk.map { it to it }, selectedDxvk, { selectedDxvk = it }, onDownloadDxvk)
        }
        if (dxvkType != DXVKConfigDialog.DXVK_TYPE_NONE) {
            DeckRow(label = "Async", hint = "Compile shaders on background threads", control = { DeckToggle(asyncEnabled) { asyncEnabled = it } })
        }
        if (dxvkType == DXVKConfigDialog.DXVK_TYPE_GPLASYNC) {
            DeckRow(label = "Async Cache", control = { DeckToggle(asyncCacheEnabled) { asyncCacheEnabled = it } })
        }
        DeckChoiceRow("Frame Rate", null, framerateEntries.map { it to it }, selectedFramerate) { selectedFramerate = it }
    }

    DeckConfigPanel("Texture Filtering") {
        DeckChoiceRow("Anisotropic Filtering", null, anisotropyLabels.mapIndexed { i, l -> l to l }, selectedAnisotropy) { selectedAnisotropy = it }
        DeckChoiceRow("Texture Sharpness", null, lodBiasLabels.mapIndexed { i, l -> l to l }, selectedLodBias) { selectedLodBias = it }
        DeckConfigNote("DirectX 9–11 games only. Applies the next time the game starts.")
    }

    DeckConfigPanel("API") {
        DeckChoiceRow("Feature Level", null, featureLevelEntries.map { it to it }, selectedFeatureLevel) { selectedFeatureLevel = it }
        DeckChoiceRow("DDraw Wrapper", null, ddraEntries.map { it to it }, selectedDdra) { selectedDdra = it }
        if (StringUtils.parseIdentifier(selectedDdra) == "d7vk") {
            DeckChoiceRowWithDownload("D7VK Version", d7vkVersions.value.map { it to it }, selectedD7vk, { selectedD7vk = it }, onDownloadD7vk)
        }
    }

    if (isVegas) {
        DeckConfigPanel("Performance Tier") {
            val staged = tierChoice
            val effectiveTier: Int? = staged ?: activeForceTier?.let { if (it == 0) null else it }
            DeckConfigNote(
                when {
                    gpuModel == null -> "GPU model unreadable — tier is manual"
                    detectedTier != null -> "$gpuModel · auto Tier $detectedTier"
                    else -> "GPU: $gpuModel — no tier suggestion"
                }
            )
            DeckConfigChipGrid(
                "Tier",
                buildList {
                    add(Triple("Auto${if (detectedTier != null) " · T$detectedTier" else ""}", effectiveTier == null, { tierChoice = null }))
                    VegasTierPresets.TIERS.forEach { t -> add(Triple(t.label, effectiveTier == t.number, { tierChoice = t.number })) }
                },
            )
            val tp = staged ?: return@DeckConfigPanel
            DeckConfigNote("will write: vegas.forceTier = $tp")
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                DeckButton("Apply tier") {
                    if (!useDefaults) { applyValue("vegas.forceTier", tp.toString()); tierChoice = null }
                }
            }
        }

        DeckConfigPanel("Config File") {
            DeckConfigNote("Pick a stock config then edit it — your changes become your own copy. Or point at any .conf.")
            DeckRow(
                label = "Use defaults (no config file)",
                control = { DeckToggle(useDefaults) { useDefaults = it } },
            )
            if (!useDefaults) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        DeckChoiceRow(
                            "Stock Config",
                            null,
                            stockSources.value.map { it.displayLabel() to it.displayLabel() },
                            selectedStock ?: "",
                        ) { s -> selectedStock = s; selectedCustom = null; useDefaults = false }
                    }
                    if (onOpenConfigDownload != null) {
                        IconButton(onClick = onOpenConfigDownload) {
                            Icon(Icons.Filled.Download, contentDescription = "Download stock configs", tint = DeckPalette.primary)
                        }
                    }
                }
                if (stockSources.value.isEmpty()) DeckConfigNote("No installed VEGAS package ships a config.")
                DeckChoiceRow(
                    "Custom Config File",
                    null,
                    (customEntries.value + BROWSE_CONFIG_MARKER).map { it to it },
                    selectedCustom ?: "",
                ) { c ->
                    if (c == BROWSE_CONFIG_MARKER) {
                        pickCustomConfigLauncher.launch(InAppFilePicker.buildIntent(context, emptyArray(), "Select config file"))
                    } else {
                        selectedCustom = c; selectedStock = null; useDefaults = false
                    }
                }
                if (livePath.isNotEmpty()) DeckConfigNote("📍 $livePath")
                if (liveFile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (backupsList.isEmpty()) "No backups yet" else "Backups: ${backupsList.size}",
                            color = DeckPalette.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f),
                        )
                        DeckButton("Backup now", primary = false) { manualBackup() }
                        if (backupsList.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            DeckButton("Restore…", primary = false) { showBackups = true }
                        }
                    }
                }
                if (configSourceText.isNotEmpty()) {
                    DeckRow(
                        label = "Fork-feature filter",
                        hint = "Show only fork-specific keys",
                        control = {
                            DeckToggle(forkFilter) {
                                forkFilter = it
                                forkFilterPrefs.edit().putBoolean("forkFilter", it).apply()
                            }
                        },
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                        DeckButton("Add key", primary = false) { addKeyDraft = ""; addValueDraft = ""; showAddKey = true }
                    }
                    val visibleRows = remember(configRows, forkFilter, selectedDxvk, vegasKnowledge) {
                        configRows.filter { r ->
                            if (forkFilter && vegasKnowledge?.isForkKey(r.key) != true) false else true
                        }
                    }
                    visibleRows.forEach { row ->
                        val gated = vegasKnowledge != null && vegasKnowledge.isGated(row.key, selectedDxvk)
                        val baseBadge = vegasKnowledge?.badgeFor(row.key, selectedDxvk) ?: "unclassified"
                        if (isBooleanKey(row.key, stockSources.value, vegasKnowledge, selectedDxvk)) {
                            DeckRow(
                                label = row.key,
                                hint = baseBadge,
                                control = { DeckToggle(row.enabled) { applyToggle(row.key, row.value, it) } },
                            )
                        } else {
                            DeckRow(
                                label = row.key,
                                hint = baseBadge,
                                onConfigClick = { if (!gated) { valuePickerRow = row; customValueDraft = row.value } },
                                configPanel = true,
                                control = {
                                    Text(row.value, color = DeckPalette.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                                },
                            )
                        }
                        if (!gated) {
                            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { pendingDeleteKey = row.key }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete ${row.key}", tint = DeckPalette.error)
                                }
                            }
                        }
                    }
                    if (selectedStock != null && pendingRows.isNotEmpty()) {
                        DeckConfigNote("New in this baseline — tap Add to insert", error = false)
                        pendingRows.forEach { row ->
                            DeckRow(
                                label = row.key,
                                hint = row.value,
                                control = { DeckButton("Add", primary = false) { applyToggle(row.key, row.value, true) } },
                            )
                        }
                    }
                    if (stockEdited && !useDefaults) {
                        DeckConfigNote(if (sidecarExists) "Edited · saved to your own copy" else "Edited · saved to the live config file")
                    }
                }
            }
        }
    }

    fun buildConfig(): String {
        val cfg = DXVKConfigDialog.parseConfig(initialConfig)
        cfg.put("version", selectedDxvk)
        cfg.put("framerate", StringUtils.parseNumber(selectedFramerate))
        cfg.put("async", if (asyncEnabled && dxvkType != DXVKConfigDialog.DXVK_TYPE_NONE) "1" else "0")
        cfg.put("asyncCache", if (asyncCacheEnabled && dxvkType == DXVKConfigDialog.DXVK_TYPE_GPLASYNC) "1" else "0")
        cfg.put("anisotropy", DXVKConfigDialog.ANISOTROPY_VALUES[anisotropyLabels.indexOf(selectedAnisotropy).coerceAtLeast(0)])
        cfg.put("lodBias", DXVKConfigDialog.LOD_BIAS_VALUES[lodBiasLabels.indexOf(selectedLodBias).coerceAtLeast(0)])
        cfg.put("vkd3dVersion", selectedVkd3d)
        cfg.put("vkd3dLevel", selectedFeatureLevel)
        cfg.put("ddrawrapper", StringUtils.parseIdentifier(selectedDdra))
        cfg.put("d7vkVersion", selectedD7vk)
        cfg.put("dxvkConfigFile", livePath)
        return cfg.toString()
    }

    // Live commit: every change reaches the host immediately, so the screen's own Save button is
    // enough. "Done" only closes the panel (it never gates the edit).
    LaunchedEffect(Unit) {
        snapshotFlow { buildConfig() }.collect { onChange?.invoke(it) }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        DeckButton("Done") { onConfirm(buildConfig()) }
    }

    if (showAddKey) {
        DeckConfigDialog(title = "Add config entry", onDismiss = { showAddKey = false }) {
            val keyValid = VegasKeyKnowledge.isValidConfigKey(addKeyDraft.trim())
            DeckConfigTextField("Key (e.g. dxvk.maxFrameLatency)", addKeyDraft, { addKeyDraft = it }, isError = addKeyDraft.isNotBlank() && !keyValid)
            DeckConfigTextField("Value", addValueDraft, { addValueDraft = it })
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                DeckButton("Add") {
                    if (addKeyDraft.isNotBlank() && addValueDraft.isNotBlank() && keyValid) {
                        applyAddKey(addKeyDraft.trim(), addValueDraft.trim())
                        showAddKey = false
                    }
                }
            }
        }
    }

    valuePickerRow?.let { row ->
        val baseline = baselineRowsForSelected.firstOrNull { it.key == row.key }
        DeckConfigDialog(title = row.key, onDismiss = { valuePickerRow = null }) {
            DeckConfigNote("Values used in stock configs — pick one, reset to stock, or type your own.")
            val opts = linkedSetOf<String>()
            if (row.value.isNotEmpty()) opts.add(row.value)
            baselineRowsForSelected.filter { it.key == row.key }.forEach { opts.add(it.value) }
            opts.forEach { v ->
                DeckRow(label = v, control = { DeckButton("Pick", primary = false) { applyValue(row.key, v); customValueDraft = v } })
            }
            if (baseline != null && (baseline.value != row.value || baseline.enabled != row.enabled)) {
                DeckRow(label = "Reset to stock (${baseline.value})", control = { DeckButton("Reset", primary = false) { applyValue(row.key, baseline.value); customValueDraft = baseline.value } })
            }
            DeckConfigTextField("Custom value", customValueDraft, { customValueDraft = it })
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                DeckButton("Apply") { if (customValueDraft.isNotBlank()) applyValue(row.key, customValueDraft.trim()) }
            }
        }
    }

    pendingDeleteKey?.let { delKey ->
        DeckConfigDialog(title = "Remove key?", onDismiss = { pendingDeleteKey = null }) {
            DeckConfigNote("Are you sure you want '$delKey' removed? You can restore it via Backup → Restore or Add key.")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                DeckButton("Remove", primary = true) {
                    applyDeleteKey(delKey); pendingDeleteKey = null
                    if (activity != null) Toast.makeText(activity, "Removed $delKey — Restore to undo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showBackups) {
        DeckConfigDialog(title = "Restore a backup", onDismiss = { showBackups = false }) {
            if (backupsList.isEmpty()) DeckConfigNote("No backups yet — they appear after the first edit.")
            backupsList.forEach { b ->
                DeckRow(
                    label = b.name,
                    hint = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(b.lastModified()),
                    control = { DeckButton("Restore", primary = false) { restoreTarget = b; showBackups = false } },
                )
            }
        }
    }

    restoreTarget?.let { backup ->
        DeckConfigDialog(title = "Restore this backup?", onDismiss = { restoreTarget = null }) {
            DeckConfigNote("The live config file will be replaced by '${backup.name}'. The current state is backed up first.")
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                DeckButton("Restore") { restoreBackup(backup); restoreTarget = null }
            }
        }
    }
}

private fun isBooleanKey(
    key: String,
    stockSources: List<DXVKConfigDialog.StockSource>,
    knowledge: VegasKeyKnowledge?,
    version: String,
): Boolean {
    val values = mutableSetOf<String>()
    for (src in stockSources) {
        val f = src.file
        if (!f.isFile) continue
        val rows = (knowledge?.editRows(runCatching { f.readText() }.getOrDefault(""), src.verName)
            ?: VegasKeyKnowledge.editRowsUnclassified(runCatching { f.readText() }.getOrDefault("")))
        for (r in rows) if (r.key == key && r.enabled && r.value.isNotEmpty()) values.add(r.value)
    }
    return values.isNotEmpty() && values.all { it == "0" || it == "1" }
}
