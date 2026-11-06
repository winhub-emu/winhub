package com.winhub.hernandez.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What HKCU\Software\Wine\DirectInput\Joysticks should hold for a Controller identity and the
 * "DirectInput games can see the controller" switch. One builder for all three writers, so they can
 * never disagree: the launch (WineUtils.setJoystickRegistryKeys, user.reg before Wine starts), the
 * in-game change (LiveWineRegistry, a .reg imported by regedit into the running registry) and the
 * reconnect that follows it.
 *
 * <p>Wine's DirectInput matches these entries by the pad's name ({@link ControllerIdentity#dinputNames}).
 * Three states, and they are not interchangeable (dlls/dinput device.c device_instance_is_disabled,
 * joystick_hid.c):
 * <ul>
 *   <li>no entry: DirectInput sees the pad as its normal HID gamepad device (the {@code &IG_} one).
 *       What "visible" writes - the old "Exclusive Input off", which wrote none.</li>
 *   <li>"override": DirectInput sees the pad, but opens Wine's internal XInput HID device for it
 *       instead ({@code &IG_} swapped for {@code &XI_}). Only where visibility is forced for the
 *       launch (controller passthrough, the Classic DirectInput pad), as before.</li>
 *   <li>"disabled": DirectInput does not see the pad.</li>
 * </ul>
 * Names not set are deleted, so a switch leaves no stale hide/show rule behind.
 */
public final class JoystickRegistry {
    /** The key, relative to HKEY_CURRENT_USER. */
    public static final String KEY = "Software\\Wine\\DirectInput\\Joysticks";

    private JoystickRegistry() {}

    /** The values to set (in order) and the value names to delete. */
    public static final class Desired {
        public final Map<String, String> set;
        public final List<String> delete;

        Desired(Map<String, String> set, List<String> delete) {
            this.set = Collections.unmodifiableMap(set);
            this.delete = Collections.unmodifiableList(delete);
        }
    }

    /** {@link #desired(String, boolean, boolean)} without a forced visibility. */
    public static Desired desired(String identity, boolean dinputVisible) {
        return desired(identity, dinputVisible, false);
    }

    /**
     * @param forced visibility is forced on for this launch (controller passthrough, the Classic
     *               DirectInput pad): "override", as those launches have always had
     */
    public static Desired desired(String identity, boolean dinputVisible, boolean forced) {
        String value = !dinputVisible ? "disabled" : forced ? "override" : null;
        Map<String, String> set = new LinkedHashMap<>();
        if (value != null) {
            for (String name : ControllerIdentity.dinputNames(identity)) set.put(name, value);
        }
        List<String> delete = new ArrayList<>();
        for (String name : ControllerIdentity.allDinputNames()) {
            if (!set.containsKey(name)) delete.add(name);
        }
        return new Desired(set, delete);
    }

    /** What a launch with these values writes, for a log line. */
    public static String describe(boolean dinputVisible, boolean forced) {
        if (!dinputVisible) return "\"disabled\" (DirectInput does not see the pad)";
        if (forced) return "\"override\" (DirectInput opens Wine's XInput HID device for the pad)";
        return "no entry (DirectInput sees the pad as its normal HID gamepad)";
    }

    /**
     * The same change as a REGEDIT4 file for {@code regedit /S}: deletions first ({@code "name"=-}),
     * then the values, CRLF line ends. Names are plain ASCII; quotes and backslashes are escaped.
     */
    public static String toRegFile(Desired desired) {
        StringBuilder out = new StringBuilder();
        out.append("REGEDIT4\r\n\r\n");
        out.append('[').append("HKEY_CURRENT_USER\\").append(KEY).append("]\r\n");
        for (String name : desired.delete) {
            out.append('"').append(escape(name)).append("\"=-\r\n");
        }
        for (Map.Entry<String, String> e : desired.set.entrySet()) {
            out.append('"').append(escape(e.getKey())).append("\"=\"")
                    .append(escape(e.getValue())).append("\"\r\n");
        }
        out.append("\r\n");
        return out.toString();
    }

    /** Escapes a .reg string: backslash and double quote. */
    static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') out.append('\\');
            out.append(c);
        }
        return out.toString();
    }
}
