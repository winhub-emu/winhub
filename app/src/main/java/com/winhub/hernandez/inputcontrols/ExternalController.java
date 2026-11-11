package com.winhub.hernandez.inputcontrols;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class ExternalController {
    public static final byte IDX_BUTTON_A = 0;
    public static final byte IDX_BUTTON_B = 1;
    public static final byte IDX_BUTTON_X = 2;
    public static final byte IDX_BUTTON_Y = 3;
    public static final byte IDX_BUTTON_L1 = 4;
    public static final byte IDX_BUTTON_R1 = 5;
    public static final byte IDX_BUTTON_SELECT = 6;
    public static final byte IDX_BUTTON_START = 7;
    public static final byte IDX_BUTTON_L3 = 8;
    public static final byte IDX_BUTTON_R3 = 9;
    public static final byte IDX_BUTTON_L2 = 10;
    public static final byte IDX_BUTTON_R2 = 11;
    /** The Steam / Guide / Home button. Last, because 0-9 are the snapshot layout the fake-evdev
     *  ring publishes and 10-11 are the triggers-as-buttons that are sent as axes instead. */
    public static final byte IDX_BUTTON_MODE = 12;
    private String name;
    private String id;
    private int deviceId = -1;
    private final ArrayList<ExternalControllerBinding> controllerBindings = new ArrayList<>();
    public final GamepadState state = new GamepadState();
    public final GamepadState remappedState = new GamepadState();
    private final float[] shapedStick = new float[2];
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** Pins the deviceId for a pad with no Android InputDevice (a Steam Controller read through SDL,
     *  see SteamControllerBackend.DEVICE_ID_BASE), so getDeviceId() never scans for it. */
    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public int getDeviceId() {
        if (this.deviceId == -1) {
            for (int deviceId : InputDevice.getDeviceIds()) {
                InputDevice device = InputDevice.getDevice(deviceId);
                if (device != null && device.getDescriptor().equals(id)) {
                    this.deviceId = deviceId;
                    break;
                }
            }
        }
        return this.deviceId;
    }

    public boolean isConnected() {
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && device.getDescriptor().equals(id)) return true;
        }
        return false;
    }

    public ExternalControllerBinding getControllerBinding(int keyCode) {
        for (ExternalControllerBinding controllerBinding : controllerBindings) {
            if (controllerBinding.getKeyCode() == keyCode) return controllerBinding;
        }
        return null;
    }

    public ExternalControllerBinding getControllerBindingAt(int index) {
        return controllerBindings.get(index);
    }

    public void addControllerBinding(ExternalControllerBinding controllerBinding) {
        if (getControllerBinding(controllerBinding.getKeyCode()) == null) controllerBindings.add(controllerBinding);
    }

    public int getPosition(ExternalControllerBinding controllerBinding) {
        return controllerBindings.indexOf(controllerBinding);
    }

    public void removeControllerBinding(ExternalControllerBinding controllerBinding) {
        controllerBindings.remove(controllerBinding);
    }

    public int getControllerBindingCount() {
        return controllerBindings.size();
    }

    /** #333: replace this controller's bindings with a deep copy of another's. Used to seed a newly
     *  added controller from the "Default / Any Controller" template and for the "copy bindings from…"
     *  action, so a fresh controller is never blank. Clones each binding (keyCode + Binding) so the two
     *  controllers never share mutable binding objects. */
    public void copyBindingsFrom(ExternalController other) {
        controllerBindings.clear();
        if (other == null) return;
        for (int i = 0; i < other.getControllerBindingCount(); i++) {
            ExternalControllerBinding src = other.getControllerBindingAt(i);
            if (src == null) continue;
            ExternalControllerBinding copy = new ExternalControllerBinding();
            copy.setKeyCode(src.getKeyCode());
            copy.setBinding(src.getBinding());
            controllerBindings.add(copy);
        }
    }

    public JSONObject toJSONObject() {
        try {
            if (controllerBindings.isEmpty()) return null;
            JSONObject controllerJSONObject = new JSONObject();
            controllerJSONObject.put("id", id);
            controllerJSONObject.put("name", name);

            JSONArray controllerBindingsJSONArray = new JSONArray();
            for (ExternalControllerBinding controllerBinding : controllerBindings) controllerBindingsJSONArray.put(controllerBinding.toJSONObject());
            controllerJSONObject.put("controllerBindings", controllerBindingsJSONArray);

            return controllerJSONObject;
        }
        catch (JSONException e) {
            return null;
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof ExternalController ? ((ExternalController)obj).id.equals(this.id) : super.equals(obj);
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {
        applyRawSticks(
                getCenteredAxis(event, MotionEvent.AXIS_X, historyPos),
                getCenteredAxis(event, MotionEvent.AXIS_Y, historyPos),
                getCenteredAxis(event, MotionEvent.AXIS_Z, historyPos),
                getCenteredAxis(event, MotionEvent.AXIS_RZ, historyPos));

        if (historyPos == -1) {
            float axisX = getCenteredAxis(event, MotionEvent.AXIS_HAT_X, historyPos);
            float axisY = getCenteredAxis(event, MotionEvent.AXIS_HAT_Y, historyPos);

            // The hat only counts while the left stick is idle (shaped value 0).
            state.dpad[0] = axisY == -1.0f && state.thumbLY == 0f;
            state.dpad[1] = axisX == 1.0f && state.thumbLX == 0f;
            state.dpad[2] = axisY == 1.0f && state.thumbLY == 0f;
            state.dpad[3] = axisX == -1.0f && state.thumbLX == 0f;
        }
    }

    /**
     * Runs the raw stick axes through the active deadzone / sensitivity tuning (see
     * {@link StickShaper}) and stores the result in {@link #state}. Synchronized because the shared
     * scratch array is reused on every call.
     */
    public synchronized void applyRawSticks(float leftX, float leftY, float rightX, float rightY) {
        StickShaper.Tuning t = StickShaper.getActive();
        StickShaper.shape(leftX, leftY, t.leftDeadzone, t.leftSensitivity, t.smoothEdge, shapedStick);
        state.thumbLX = shapedStick[0];
        state.thumbLY = shapedStick[1];
        StickShaper.shape(rightX, rightY, t.rightDeadzone, t.rightSensitivity, t.smoothEdge, shapedStick);
        state.thumbRX = shapedStick[0];
        state.thumbRY = shapedStick[1];
    }



    private void processTriggerButton(MotionEvent event) {
        float l = event.getAxisValue(MotionEvent.AXIS_LTRIGGER) == 0f ? event.getAxisValue(MotionEvent.AXIS_BRAKE) : event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
        float r = event.getAxisValue(MotionEvent.AXIS_RTRIGGER) == 0f ? event.getAxisValue(MotionEvent.AXIS_GAS) : event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
        state.triggerL = l;
        state.triggerR = r;
        state.setPressed(IDX_BUTTON_L2, l == 1.0f);
        state.setPressed(IDX_BUTTON_R2, r == 1.0f);
    }

    public boolean updateStateFromMotionEvent(MotionEvent event) {
        if (isJoystickDevice(event)) {
            processTriggerButton(event);
            int historySize = event.getHistorySize();
            for (int i = 0; i < historySize; i++) processJoystickInput(event, i);
            processJoystickInput(event, -1);
            return true;
        }
        return false;
    }


    public boolean updateStateFromKeyEvent(KeyEvent event) {
        boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;
        int keyCode = event.getKeyCode();
        int buttonIdx = getButtonIdxByKeyCode(keyCode);
        if (buttonIdx != -1) {
            if (buttonIdx == IDX_BUTTON_L2) {
                return true;
            } else if (buttonIdx == IDX_BUTTON_R2) {
                return true;
            } else
                state.setPressed(buttonIdx, pressed);
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                state.dpad[0] = pressed && state.thumbLY == 0f;
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                state.dpad[1] = pressed && state.thumbLX == 0f;
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                state.dpad[2] = pressed && state.thumbLY == 0f;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                state.dpad[3] = pressed && state.thumbLX == 0f;
                return true;
        }
        return false;
    }

    public static ArrayList<ExternalController> getControllers() {
        int[] deviceIds = InputDevice.getDeviceIds();
        ArrayList<ExternalController> controllers = new ArrayList<>();
        for (int i = deviceIds.length-1; i >= 0; i--) {
            InputDevice device = InputDevice.getDevice(deviceIds[i]);
            if (isGameController(device)) {
                ExternalController controller = new ExternalController();
                controller.setId(device.getDescriptor());
                controller.setName(device.getName());
                controllers.add(controller);
            }
        }
        return controllers;
    }

    public static ExternalController getController(String id) {
        for (ExternalController controller : getControllers()) if (controller.getId().equals(id)) return controller;
        return null;
    }

    public static ExternalController getController(int deviceId) {
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int i = deviceIds.length-1; i >= 0; i--) {
            if (deviceIds[i] == deviceId || deviceId == 0) {
                InputDevice device = InputDevice.getDevice(deviceIds[i]);
                if (isGameController(device)) {
                    ExternalController controller = new ExternalController();
                    controller.setId(device.getDescriptor());
                    controller.setName(device.getName());
                    controller.deviceId = deviceIds[i];
                    return controller;
                }
            }
        }
        return null;
    }

    public static boolean isGameController(InputDevice device) {
        if (device == null) return false;
        if (device.isVirtual()) return false;
        // Fingerprint-reader / uinput daemons advertise gamepad-like sources on some phones and
        // would otherwise steal slot 0 from the real pad. Reject them by name before the source
        // check (uinput-fpc, goodix_fp, and the generic uinput- prefix cover the known offenders).
        String name = device.getName();
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("uinput-fpc") || lowerName.contains("goodix_fp")
                    || lowerName.contains("uinput-")) {
                return false;
            }
        }

        int sources = device.getSources();
        // Must at least CLAIM to be a gamepad or joystick (mouse-tagged joysticks excluded).
        boolean isGamepadSource = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        boolean isJoystickSource = (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && (sources & InputDevice.SOURCE_MOUSE) == 0;
        if (!isGamepadSource && !isJoystickSource) return false;

        // Positive capability requirement (the AYANEO fix). Some aux "media / back button" boards
        // (e.g. "AYANEO DEVICE": EV=13, keyboard-range keys, NO ABS axes, NO BTN_GAMEPAD keys)
        // inherit a gamepad/joystick source flag purely from their key bitmap and would otherwise
        // grab an XInput player slot. A real controller ALWAYS exposes one of two things, so require
        // at least one of:
        //   (a) real motion axes — analog sticks / hat / secondary sticks (getMotionRange != null), OR
        //   (b) actual gamepad face buttons in the BTN_GAMEPAD range (hasKeys BUTTON_A/B/X/Y/START).
        // (a) covers every analog pad (Xbox / DualShock / etc.); (b) rescues analog-less-but-real
        // pads (arcade / fight sticks, pure-digital retro pads) that have buttons + maybe a hat but
        // no analog sticks. The AYANEO aux board fails BOTH: no ABS axes, and its keys are keyboard
        // scancodes (0x01-0x08), not the BTN_GAMEPAD range (0x130+), so hasKeys(BUTTON_A…) is false.
        // NOTE: WinNative's ExternalController.isGameController stops at the source-flag test above —
        // it has no capability check — so this predicate is intentionally stricter than that tree.
        return hasGamepadMotionAxis(device) || hasGamepadButtons(device);
    }

    /** True if the device exposes a real joystick/gamepad motion axis (analog stick, secondary
     *  stick, or a hat-encoded d-pad). Uses the source-agnostic getMotionRange(axis) overload so a
     *  device with NO ABS bits (like the AYANEO aux board) returns false on every axis. */
    private static boolean hasGamepadMotionAxis(InputDevice device) {
        return device.getMotionRange(MotionEvent.AXIS_X) != null
                || device.getMotionRange(MotionEvent.AXIS_Y) != null
                || device.getMotionRange(MotionEvent.AXIS_Z) != null
                || device.getMotionRange(MotionEvent.AXIS_RX) != null
                || device.getMotionRange(MotionEvent.AXIS_RY) != null
                || device.getMotionRange(MotionEvent.AXIS_RZ) != null
                || device.getMotionRange(MotionEvent.AXIS_HAT_X) != null
                || device.getMotionRange(MotionEvent.AXIS_HAT_Y) != null;
    }

    /** True if the device reports at least one BTN_GAMEPAD-range face button. This is the
     *  discriminator that still rejects the AYANEO aux board (keyboard-range keys only) while
     *  accepting an analog-less arcade / fight stick. */
    private static boolean hasGamepadButtons(InputDevice device) {
        boolean[] have = device.hasKeys(
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_START);
        for (boolean b : have) if (b) return true;
        return false;
    }

    /**
     * Stable physical identity for a device, used to order controllers deterministically at
     * startup. Prefers the descriptor; falls back to name:vendor:product when a device reports
     * no descriptor (some cheap pads), so two such pads still sort consistently.
     */
    public static String getPhysicalDeviceIdentifier(InputDevice device) {
        if (device == null) return "";
        String descriptor = device.getDescriptor();
        if (descriptor != null && !descriptor.isEmpty()) return descriptor;
        return String.format(Locale.US, "%s:%d:%d", device.getName(), device.getVendorId(), device.getProductId());
    }


    /** Physical family a controller belongs to, used ONLY to pick which picture the in-game
     *  controller-test panel draws. The emulated XInput target is always an Xbox 360 pad regardless
     *  of this — classification never changes what the guest sees. */
    public enum PadType { XBOX, PS, GENERIC }

    /** Classify a device into {@link PadType} by vendor id (Sony 0x054C → PS, Microsoft 0x045E →
     *  XBOX) with a name fallback (playstation/dualshock/dualsense → PS, xbox → XBOX). Everything
     *  else is GENERIC. Null-safe. */
    public static PadType classifyType(InputDevice device) {
        if (device == null) return PadType.GENERIC;
        int vendor = device.getVendorId();
        String name = device.getName();
        String lower = name != null ? name.toLowerCase(Locale.US) : "";
        if (vendor == 0x054C || lower.contains("playstation") || lower.contains("dualshock") || lower.contains("dualsense"))
            return PadType.PS;
        if (vendor == 0x045E || lower.contains("xbox"))
            return PadType.XBOX;
        return PadType.GENERIC;
    }

    public float getCenteredAxis(MotionEvent event, int axis, int historyPos) {
        if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) {
            float value = event.getAxisValue(axis);
            return Math.abs(value) == 1.0f ? value : 0.0f;
        }

        InputDevice device = event.getDevice();
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range == null) return 0.0f;

        float flat = range.getFlat();
        float value = historyPos < 0 ? event.getAxisValue(axis) : event.getHistoricalAxisValue(axis, historyPos);

        if (Math.abs(value) <= flat) return 0.0f;

        // Stick axes come back unshaped: the user's deadzone and sensitivity are applied to the X/Y
        // pair together in applyRawSticks.
        if (axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y || axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RZ) {
             return value;
        }

        return 0.0f;
    }





    public static boolean isJoystickDevice(MotionEvent event) {
        return (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && event.getAction() == MotionEvent.ACTION_MOVE;
    }

    public static int getButtonIdxByKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                return IDX_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_B:
                return IDX_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_X:
                return IDX_BUTTON_X;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return IDX_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_L1:
                return IDX_BUTTON_L1;
            case KeyEvent.KEYCODE_BUTTON_R1:
                return IDX_BUTTON_R1;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return IDX_BUTTON_SELECT;
            case KeyEvent.KEYCODE_BUTTON_START:
                return IDX_BUTTON_START;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                return IDX_BUTTON_L3;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return IDX_BUTTON_R3;
            case KeyEvent.KEYCODE_BUTTON_L2:
                return IDX_BUTTON_L2;
            case KeyEvent.KEYCODE_BUTTON_R2:
                return IDX_BUTTON_R2;
            case KeyEvent.KEYCODE_BUTTON_MODE:
                return IDX_BUTTON_MODE;
            default:
                return -1;
        }
    }

}
