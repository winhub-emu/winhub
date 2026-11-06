package com.winhub.hernandez.core;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.winhandler.WinHandler;

/**
 * "DirectInput games can see the controller": the one switch that replaced Enable XInput / Enable
 * DInput / Exclusive Input. At launch it decides the HKCU\Software\Wine\DirectInput\Joysticks entries
 * (WineUtils.setJoystickRegistryKeys, core.JoystickRegistry): on = no entry (visible, as the old Exclusive
 * Input off wrote), off = "disabled" (hidden). "override" only where visibility is forced for the launch.
 *
 * <p>It is stored in the same two values older builds read, so a downgrade keeps the choice (an older
 * build turns a stored "on" into "override", not into no entry): the
 * container's {@code exclusiveXInput} / {@code inputType} fields and the game's extras of the same
 * names (also what community configs carry). Reading maps the old combinations:
 * <ul>
 *   <li>Exclusive off: on (old builds wrote no entries, and on writes none again)</li>
 *   <li>Exclusive on + DInput on: on</li>
 *   <li>Exclusive on + DInput off: off (the old default, so a new container starts off)</li>
 * </ul>
 * Writing always sets Exclusive on with the DInput bit as the switch. The XInput bit is never read
 * (WinHandler.inputType is stored but unused); it is kept set, as the old default had it.
 */
public final class DInputVisibility {
    public static final String EXTRA_EXCLUSIVE = "exclusiveXInput";
    public static final String EXTRA_INPUT_TYPE = "inputType";

    private DInputVisibility() {}

    /** The switch for stored Exclusive / inputType values (old ones included). */
    public static boolean fromStored(boolean exclusive, int inputType) {
        return !exclusive || (inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) != 0;
    }

    /** The inputType written for a switch value (always with Exclusive on). */
    public static int inputTypeFor(boolean visible) {
        return WinHandler.FLAG_INPUT_TYPE_XINPUT | (visible ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0);
    }

    public static boolean forContainer(Container container) {
        return container != null
                ? fromStored(container.isExclusiveXInput(), container.getInputType())
                : fromStored(true, WinHandler.DEFAULT_INPUT_TYPE);
    }

    public static void writeTo(Container container, boolean visible) {
        container.setExclusiveXInput(true);
        container.setInputType(inputTypeFor(visible));
    }

    /**
     * The value for a game from raw extras (null or empty = not set by the game), each falling back
     * to the container's own field, exactly as the launch has always read them.
     */
    public static boolean fromExtras(String exclusiveExtra, String inputTypeExtra, Container container) {
        boolean exclusive = container == null || container.isExclusiveXInput();
        int inputType = container != null ? container.getInputType() : WinHandler.DEFAULT_INPUT_TYPE;
        if (exclusiveExtra != null && !exclusiveExtra.isEmpty()) {
            exclusive = exclusiveExtra.equals("1") || exclusiveExtra.equalsIgnoreCase("true");
        }
        if (inputTypeExtra != null && !inputTypeExtra.trim().isEmpty()) {
            try {
                inputType = Integer.parseInt(inputTypeExtra.trim());
            } catch (NumberFormatException ignored) {
                // keep the container's
            }
        }
        return fromStored(exclusive, inputType);
    }

    /** The value a game launches with: its own extras over the container's fields. */
    public static boolean forGame(Container container, Shortcut shortcut) {
        if (shortcut == null) return forContainer(container);
        return fromExtras(shortcut.getExtra(EXTRA_EXCLUSIVE), shortcut.getExtra(EXTRA_INPUT_TYPE), container);
    }

    /** True when the game carries a value of its own (either extra). */
    public static boolean gameOwns(Shortcut shortcut) {
        return shortcut != null && (!shortcut.getExtra(EXTRA_EXCLUSIVE).isEmpty()
                || !shortcut.getExtra(EXTRA_INPUT_TYPE).trim().isEmpty());
    }

    /** Writes the game's own value; {@code null} clears it so the game follows its container. */
    public static void writeTo(Shortcut shortcut, Boolean visible) {
        if (visible == null) {
            shortcut.putExtra(EXTRA_EXCLUSIVE, null);
            shortcut.putExtra(EXTRA_INPUT_TYPE, null);
        } else {
            shortcut.putExtra(EXTRA_EXCLUSIVE, "1");
            shortcut.putExtra(EXTRA_INPUT_TYPE, String.valueOf(inputTypeFor(visible)));
        }
    }
}
