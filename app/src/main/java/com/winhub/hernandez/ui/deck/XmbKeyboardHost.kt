package com.winhub.hernandez.ui.deck

import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.input.TextInputSession
import java.util.Collections
import java.util.WeakHashMap

// The XMB keyboard hook: while the XMB mode is on, every Compose text field below a [XmbKeyboardHost] types through
// [XmbKeyboardPanel] instead of Android's keyboard. Compose reaches the keyboard through LocalTextInputService (field
// focus, edits) and LocalSoftwareKeyboardController (show / hide), so the host provides its own for both.

/** The keyboard state of the screen being hosted; null outside the XMB mode. Read by [XmbKeyboardWindow]. */
internal val LocalXmbKeyboard = staticCompositionLocalOf<XmbKeyboardState?> { null }

/** One field's input as Compose started it: the window it is in, where its edits go and the keyboard's session for it. */
private class XmbInput(
    val owner: PlatformTextInputService,
    val window: TextInputService?,
    val onEdit: (List<EditCommand>) -> Unit,
    val onAction: (ImeAction) -> Unit,
    val session: XmbKeyboardSession,
)

/**
 * What the XMB keyboard shows and does for one hosted screen: the field being typed into ([session]) and whether the
 * panel is up ([panelOpen]). Doubles as the screen's [SoftwareKeyboardController], and makes the text input service of
 * each window of the screen ([serviceFor]); only one field is typed into at a time, whatever window it is in.
 */
@Stable
internal class XmbKeyboardState : SoftwareKeyboardController {
    var session by mutableStateOf<XmbKeyboardSession?>(null)
        private set
    var panelOpen by mutableStateOf(false)
        private set

    /** The field is typed into with Android's keyboard (the panel's switch). */
    private var systemActive by mutableStateOf(false)

    /** Typing is going on, with the panel or with Android's keyboard. */
    val active: Boolean get() = panelOpen || systemActive

    private var input: XmbInput? = null
    private var systemSession: TextInputSession? = null

    /** Compose asked for the keyboard (or the tap-to-edit helpers did) and no field has started yet. */
    private var showWanted = false

    /** The field stopped while the panel was up: the session is dropped when the panel closes. */
    private var inputEnded = false
    private val windows = Collections.newSetFromMap(WeakHashMap<TextInputService, Boolean>())

    /** The text input service for a window of the screen; [window] is that window's own, which Android's keyboard switch uses. */
    fun serviceFor(window: TextInputService?): TextInputService =
        TextInputService(WindowInput(window)).also { windows.add(it) }

    /** True for a service made by [serviceFor], so a window already hooked is not hooked again. */
    fun isOurs(service: TextInputService?): Boolean = service != null && service in windows

    override fun show() {
        showWanted = true
        openPanel()
    }

    override fun hide() {
        showWanted = false
        panelOpen = false
        endSystem()
        if (inputEnded) drop()
    }

    /** The panel was dismissed or finished. */
    fun closePanel() {
        showWanted = false
        panelOpen = false
        if (inputEnded) drop()
    }

    /** The panel's Android keyboard switch: the same field is handed to the window's own keyboard service. */
    fun useSystemKeyboard() {
        val current = input ?: return
        val window = current.window
        if (inputEnded || window == null) {
            closePanel()
            return
        }
        val s = current.session
        val started = window.startInput(s.value, s.imeOptions, current.onEdit, current.onAction)
        systemSession = started
        systemActive = true
        panelOpen = false
        started.showSoftwareKeyboard()
    }

    /** The screen left the composition (or the XMB mode went off): nothing stays open. */
    fun release() {
        panelOpen = false
        drop()
    }

    private fun begin(next: XmbInput) {
        endSystem()
        input = next
        inputEnded = false
        session = next.session
        if (showWanted) openPanel()
    }

    private fun end(owner: PlatformTextInputService) {
        if (input?.owner !== owner) return
        // The panel's own window took the focus; the field's session stays usable until the panel closes.
        if (panelOpen) inputEnded = true else drop()
    }

    private fun update(owner: PlatformTextInputService, oldValue: TextFieldValue?, newValue: TextFieldValue) {
        val current = input ?: return
        if (current.owner !== owner) return
        current.session.value = newValue
        systemSession?.updateState(oldValue, newValue)
    }

    private fun openPanel() {
        if (input == null || hasHardwareKeyboard()) return
        val system = systemSession
        if (system != null) system.showSoftwareKeyboard() else panelOpen = true
    }

    private fun endSystem() {
        systemSession?.let {
            it.hideSoftwareKeyboard()
            it.dispose()
        }
        systemSession = null
        systemActive = false
    }

    private fun drop() {
        endSystem()
        input = null
        session = null
        showWanted = false
        inputEnded = false
    }

    /** One window's platform side: it starts and stops the shared session only for fields of its own window. */
    private inner class WindowInput(private val window: TextInputService?) : PlatformTextInputService {
        override fun startInput(
            value: TextFieldValue,
            imeOptions: ImeOptions,
            onEditCommand: (List<EditCommand>) -> Unit,
            onImeActionPerformed: (ImeAction) -> Unit,
        ) = begin(XmbInput(this, window, onEditCommand, onImeActionPerformed, XmbKeyboardSession(value, imeOptions, onEditCommand, onImeActionPerformed)))

        override fun stopInput() = end(this)

        override fun showSoftwareKeyboard() = show()

        override fun hideSoftwareKeyboard() = hide()

        override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) = update(this, oldValue, newValue)
    }
}

/**
 * An alphabetic keyboard is attached (Bluetooth / USB). Compose's text field takes its keys itself, so no panel is
 * needed. A gamepad that reports itself as a keyboard does not count.
 */
private fun hasHardwareKeyboard(): Boolean = InputDevice.getDeviceIds().any { id ->
    val device = InputDevice.getDevice(id)
    device != null && !device.isVirtual &&
        device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
        (device.sources and InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD
}

/**
 * Hooks the XMB keyboard into everything below it. [enabled] is the XMB mode (by default the saved setting, read once);
 * while it is off nothing is replaced and the screen types with Android's keyboard as before. Dialogs and sheets are
 * windows of their own that start from Android's input service again: those that use DeckDialogScaled get the hook back
 * through [XmbKeyboardWindow], any other needs it around its content.
 */
@Composable
internal fun XmbKeyboardHost(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (LocalXmbKeyboard.current != null) {
        content()
        return
    }
    val state = remember { XmbKeyboardState() }
    val windowService = LocalTextInputService.current
    val windowController = LocalSoftwareKeyboardController.current
    val service = remember(state, windowService) { state.serviceFor(windowService) }
    DisposableEffect(state, enabled) { onDispose { state.release() } }
    CompositionLocalProvider(
        LocalXmbKeyboard provides (if (enabled) state else null),
        LocalTextInputService provides (if (enabled) service else windowService),
        LocalSoftwareKeyboardController provides (if (enabled) state else windowController),
    ) {
        content()
        val session = state.session
        if (enabled && state.panelOpen && session != null) {
            key(session) { XmbKeyboardPanel(session, onClose = state::closePanel, onSystemKeyboard = state::useSystemKeyboard) }
        }
    }
}

/**
 * Brings the XMB keyboard into a dialog's or sheet's window, which does not inherit the text input service. Does nothing
 * outside the XMB mode or in a window that is already hooked.
 */
@Composable
internal fun XmbKeyboardWindow(content: @Composable () -> Unit) {
    val state = LocalXmbKeyboard.current
    val windowService = LocalTextInputService.current
    if (state == null || state.isOurs(windowService)) {
        content()
        return
    }
    val service = remember(state, windowService) { state.serviceFor(windowService) }
    CompositionLocalProvider(
        LocalTextInputService provides service,
        LocalSoftwareKeyboardController provides state,
        content = content,
    )
}
