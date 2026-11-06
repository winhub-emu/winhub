package com.winhub.hernandez.core;

import android.util.Log;

import com.winhub.hernandez.inputcontrols.HermesWriter;

/**
 * The app's writes into HERMES's control block (format minor 5): each slot's identity, read by the
 * evdev layer when a reader attaches (so a reconnect presents a new identity), and the session control,
 * read live by the readers (stick pacing) and by the XInput DLLs (Direct / forward / hidden), plus
 * DirectInput vibration, read when a reader attaches. One place, used at launch (before the guest
 * starts) and from the in-game menu.
 */
public final class HermesSessionControl {
    private static final String TAG = "HermesControl";

    private HermesSessionControl() {}

    /** Every slot's identity (generic included: an explicit generic beats the env fallback). */
    public static void writeSlotIdentities(String identity) {
        for (int slot = 0; slot < HermesWriter.MAX_SLOTS; slot++) {
            HermesWriter.setSlotIdentity(slot,
                    ControllerIdentity.SLOT_BUSTYPE,
                    ControllerIdentity.slotVendor(identity, slot),
                    ControllerIdentity.slotProduct(identity, slot),
                    ControllerIdentity.SLOT_VERSION,
                    identity,
                    ControllerIdentity.slotName(identity, slot));
        }
        Log.i(TAG, "slot identity: " + ControllerIdentity.describe(identity));
    }

    /**
     * Pacing and XInput mode for the session. {@code pacingFromEnv}: HERMES_AXIS_* are set in the env
     * vars, so the readers keep using those (mode 0 = unset/env).
     */
    public static void writeSessionControl(StickPacing pacing, int xinputMode, int xinputFlags, boolean pacingFromEnv) {
        int axisMode = HermesWriter.AXIS_MODE_UNSET, axisHz = 0, autoMax = 0;
        if (!pacingFromEnv) {
            switch (pacing.mode) {
                case StickPacing.MODE_FIXED: axisMode = HermesWriter.AXIS_MODE_FIXED; axisHz = pacing.hz; break;
                case StickPacing.MODE_OFF: axisMode = HermesWriter.AXIS_MODE_UNPACED; break;
                default: axisMode = HermesWriter.AXIS_MODE_AUTO; autoMax = pacing.hz; break;
            }
        }
        HermesWriter.setSessionControl(axisMode, axisHz, autoMax, xinputMode, xinputFlags);
        Log.i(TAG, "session control: pacing " + (pacingFromEnv ? "from env vars" : pacing.format())
                + ", xinput mode " + xinputMode + " flags " + xinputFlags);
    }

    /**
     * DirectInput vibration (core.DInputVibration) for every slot: readers take it when a pad is
     * opened, so write it before the guest starts, and before a reconnect's replug in-game.
     * HERMES_DINPUT_FF in the guest's env wins over it.
     */
    public static void writeFfMode(boolean on) {
        applyFfMode(DInputVibration.ffMode(on));
        Log.i(TAG, "DirectInput vibration: " + (on ? "on" : "off"));
    }

    private static void applyFfMode(int mode) {
        try {
            HermesWriter.setFfMode(mode);
        } catch (UnsatisfiedLinkError e) {
            // libhermes.so missing or older than the force-feedback API: the readers keep their default.
            Log.w(TAG, "DirectInput vibration: setFfMode unavailable", e);
        }
    }

    /**
     * No identity and no session settings: the readers go by their environment (FAKE_EVDEV_IDENTITY,
     * HERMES_AXIS_*). For the Linux runtime session, which sets its own env. The record outlives a
     * session in this process, so without this a Linux launch after a Wine game would present that
     * game's identity and pacing. Call it before the slots' discovery nodes are made.
     */
    public static void resetForEnv() {
        for (int slot = 0; slot < HermesWriter.MAX_SLOTS; slot++) {
            HermesWriter.clearSlotIdentity(slot);
        }
        HermesWriter.setSessionControl(HermesWriter.AXIS_MODE_UNSET, 0, 0,
                HermesWriter.XINPUT_MODE_UNSET, 0);
        applyFfMode(DInputVibration.FF_MODE_UNSET);
        Log.i(TAG, "control block cleared: identity, pacing and DirectInput vibration from the env vars");
    }

    /** The XInput mode for a live Direct/Wine choice, when the session can switch. */
    public static int xinputModeFor(boolean direct) {
        return HermesXInput.liveMode(direct);
    }
}
