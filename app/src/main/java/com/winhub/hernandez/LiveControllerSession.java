package com.winhub.hernandez;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.core.HermesSessionControl;
import com.winhub.hernandez.core.HermesXInput;
import com.winhub.hernandez.core.ControllerIdentity;
import com.winhub.hernandez.core.ControllerLog;
import com.winhub.hernandez.core.DInputVibration;
import com.winhub.hernandez.core.JoystickRegistry;
import com.winhub.hernandez.core.StickPacing;
import com.winhub.hernandez.inputcontrols.HermesWriter;
import com.winhub.hernandez.ui.XServerDialogState;
import com.winhub.hernandez.winhandler.LiveWineRegistry;
import com.winhub.hernandez.winhandler.SlotReconnector;
import com.winhub.hernandez.winhandler.WinHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The in-game controller settings (Controls › Pad) of a Wine session: Controller type, XInput source,
 * DirectInput visibility, DirectInput vibration and stick updates. Every change is saved through
 * {@link ControllerSaveTarget} (this game, or all games in the container, as the footer says) and
 * reaches the running game in one of three ways:
 * <ul>
 *   <li>live: stick updates (HERMES session control, read by the readers at once)</li>
 *   <li>by reconnecting the controller (WinHandler.reconnectSlots): the identity, DirectInput
 *       visibility, DirectInput vibration (HERMES reads it when the pad is opened again) and, when
 *       the session allows it, the XInput source. Batched: 800 ms after the last change, on
 *       "Reconnect now", or when the menu closes</li>
 *   <li>on the next launch: switching to or from the Classic DirectInput pad (winebus reads "Map
 *       Controllers" once), and the XInput source when in-game switching was off at launch</li>
 * </ul>
 * Main thread.
 */
final class LiveControllerSession {
    private static final String TAG = "LiveController";
    static final long AUTO_RECONNECT_MS = 800;
    static final String NOTE_IDENTITY = "identity";
    static final String NOTE_XINPUT = "xinput";

    /** What the activity shows around a reconnect. */
    interface Host {
        void onReconnecting();
        /** {@code ok}: the game opened the controller again. */
        void onReconnected(boolean ok, String identityLabel);
        /** The game's XInput applied a source switch ({@code ok} false: Wine's XInput was unavailable). */
        void onXInputApplied(boolean direct, boolean ok);
    }

    private final Container container;
    private final Shortcut shortcut;
    private final WinHandler winHandler;
    private final File driveC;
    private final Host host;
    private final ControllerSaveTarget saveTarget;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable autoReconnect = this::reconnectNow;

    // At launch.
    private final String launchIdentity;
    private final String identityLockedReason;
    private final String pacingLockedReason;
    private final String dinputLockedReason;
    private final String vibrationLockedReason;
    private final boolean xinputSwitchable;
    /** Control block xinput_flags written at launch (FORWARD_AVAILABLE, HIDDEN). */
    private final int xinputFlags;
    /** Control block xinput_mode in force. */
    private int xinputMode;

    // Wanted (saved) and applied (what the game sees now).
    private String identity;
    private String appliedIdentity;
    private boolean dinputVisible;
    private boolean appliedDInputVisible;
    private boolean dinputVibration;
    private boolean appliedDInputVibration;
    private boolean xinputDirect;
    private boolean appliedXInputDirect;
    private StickPacing pacing;

    private boolean pending;
    private boolean reconnecting;
    private boolean awaitingXInputStatus;
    private int nowHz;
    private final List<String> nextLaunchOnly = new ArrayList<>();

    /**
     * @param launchIdentity   the identity the session started with (ControllerIdentity.effective)
     * @param launchDInput     the DirectInput visibility written at launch (forced values included)
     * @param dinputForcedWhy  non-null when the launch forced DirectInput on (passthrough, DirectInput pad)
     * @param xinputDirect     Direct XInput (the setting) for this launch
     * @param xinputSwitchable the XInput source can switch in-game (the forwarding copy is installed)
     * @param xinputMode       control block xinput_mode written at launch
     * @param xinputFlags      control block xinput_flags written at launch
     */
    LiveControllerSession(Container container, Shortcut shortcut, WinHandler winHandler, Host host,
                          ControllerSaveTarget saveTarget, String launchIdentity, boolean launchDInput, String dinputForcedWhy,
                          boolean xinputDirect, boolean xinputSwitchable, int xinputMode, int xinputFlags) {
        this.container = container;
        this.shortcut = shortcut;
        this.winHandler = winHandler;
        this.host = host;
        this.saveTarget = saveTarget;
        this.driveC = new File(container.getRootDir(), ".wine/drive_c");
        this.launchIdentity = launchIdentity;
        this.identity = launchIdentity;
        this.appliedIdentity = launchIdentity;
        String source = ControllerIdentity.source(container, shortcut);
        this.identityLockedReason = source.endsWith("env var")
                ? "Set by " + ControllerIdentity.ENV + " in the " + (source.startsWith("game") ? "game's" : "container's") + " env vars."
                : null;
        this.dinputVisible = launchDInput;
        this.appliedDInputVisible = launchDInput;
        this.dinputLockedReason = dinputForcedWhy != null ? "Always on for this launch (" + dinputForcedWhy + ")." : null;
        // The launch wrote this game's setting (GuestProgramLauncherComponent); HERMES_DINPUT_FF in the
        // env vars wins in the guest, so the row then shows that value and can't change.
        this.dinputVibration = DInputVibration.effective(container, shortcut);
        this.appliedDInputVibration = dinputVibration;
        this.vibrationLockedReason = DInputVibration.lockedReason(container, shortcut);
        this.xinputDirect = xinputDirect;
        this.appliedXInputDirect = xinputDirect;
        this.xinputSwitchable = xinputSwitchable;
        this.xinputMode = xinputMode;
        this.xinputFlags = xinputFlags;
        this.pacing = StickPacing.forGame(container, shortcut);
        this.pacingLockedReason = StickPacing.envOverrides(container, shortcut)
                ? "Set by " + StickPacing.ENV_AXIS_HZ + " / " + StickPacing.ENV_AXIS_AUTO_MAX + " in the env vars." : null;

        winHandler.setReconnectListener(new SlotReconnector.Listener() {
            @Override public void onReconnecting(int[] slots) {
                reconnecting = true;
                publish();
                if (slots.length > 0) host.onReconnecting();
            }

            @Override public void onReconnected(boolean ok, int[] slots, int attempts) {
                reconnecting = false;
                publish();
                if (slots.length > 0) host.onReconnected(ok, ControllerIdentity.shortLabel(appliedIdentity));
            }
        });
    }

    /** Wires the drawer callbacks and shows the current values. */
    void attach() {
        // Direct XInput confirms a mode switch once per game process (status datagram); report the first
        // answer after each switch only.
        winHandler.setXInputStatusListener((requested, mode, ok, gen, pid) -> main.post(() -> {
            if (!awaitingXInputStatus) return;
            awaitingXInputStatus = false;
            if (!ok) {
                // Forward was unavailable in the game: it stayed on Direct XInput.
                appliedXInputDirect = true;
                xinputDirect = true;
                publish();
            }
            host.onXInputApplied(mode == com.winhub.hernandez.core.HermesXInput.XINPUT_MODE_DIRECT, ok);
        }));
        XServerDialogState ds = XServerDialogState.INSTANCE;
        ds.onPadIdentity = this::onIdentity;
        ds.onPadXInputDirect = this::onXInputDirect;
        ds.onPadDInputVisible = this::onDInputVisible;
        ds.onPadDInputVibration = this::onDInputVibration;
        ds.onPadPacing = this::onPacing;
        ds.onPadReconnectNow = this::reconnectNow;
        ds.onPadNextLaunch = this::nextLaunch;
        ds.onPadRefresh = this::refreshNowHz;
        publish();
    }

    /** The game ships its own XInput DLL ({@code names}), served by Wine's builtin this launch: say so on Pad. */
    void noteGameShipsXInput(String names) {
        addNote("This game ships its own " + names + ": Wine's XInput serves it, so the XInput source doesn't change it. "
                + "Game settings › Controller: Use Direct XInput over the game's own XInput DLL.");
        publish();
    }

    /** The in-game menu closed: a pending change reconnects now rather than waiting. */
    void onMenuClosed() {
        if (pending) reconnectNow();
    }

    // ── Changes from the drawer ─────────────────────────────────────────────────────────────────

    private void onIdentity(String value) {
        if (identityLockedReason != null) return;
        String next = ControllerIdentity.forWine(value);
        if (next.equals(identity)) return;
        identity = next;
        saveIdentity(next);
        boolean nextIsPad = ControllerIdentity.DINPUT_PAD.equals(next);
        boolean launchIsPad = ControllerIdentity.DINPUT_PAD.equals(launchIdentity);
        logChange("Controller type -> " + next + (nextIsPad != launchIsPad ? " (next launch)" : ""));
        if (nextIsPad != launchIsPad) {
            // winebus reads "Map Controllers" once: the Classic DirectInput pad comes or goes at the next launch.
            addNote(NOTE_IDENTITY);
            publish();
            return;
        }
        nextLaunchOnly.remove(NOTE_IDENTITY);
        markPending();
    }

    private void onDInputVisible(boolean visible) {
        if (dinputLockedReason != null || visible == dinputVisible) return;
        dinputVisible = visible;
        saveDInput(visible);
        logChange("DirectInput games can see the controller -> " + (visible ? "on" : "off"));
        markPending();
    }

    private void onDInputVibration(boolean on) {
        if (vibrationLockedReason != null || on == dinputVibration) return;
        dinputVibration = on;
        saveDInputVibration(on);
        logChange("DirectInput vibration -> " + (on ? "on" : "off"));
        markPending();
    }

    private void onXInputDirect(boolean direct) {
        if (direct == xinputDirect) return;
        xinputDirect = direct;
        saveXInput(direct);
        logChange("XInput source -> " + (direct ? "Direct" : "Wine") + (xinputSwitchable ? "" : " (next launch)"));
        if (!xinputSwitchable) {
            addNote(NOTE_XINPUT);
            publish();
            return;
        }
        nextLaunchOnly.remove(NOTE_XINPUT);
        markPending();
    }

    private void onPacing(String value) {
        if (pacingLockedReason != null) return;
        StickPacing next = StickPacing.parse(value);
        if (next == null || next.equals(pacing)) return;
        pacing = next;
        savePacing(next);
        HermesSessionControl.writeSessionControl(pacing, xinputMode, xinputFlags, false);
        logChange("Stick updates -> " + next.format() + " (live)");
        publish();
    }

    /** An in-game change: logcat, and the controller log when it is on. */
    private static void logChange(String line) {
        Log.i(TAG, line);
        ControllerLog.event("in game: " + line);
    }

    private void markPending() {
        pending = needsReconnect();
        main.removeCallbacks(autoReconnect);
        if (pending) main.postDelayed(autoReconnect, AUTO_RECONNECT_MS);
        publish();
    }

    private boolean needsReconnect() {
        boolean identityChange = !identity.equals(appliedIdentity) && !nextLaunchOnly.contains(NOTE_IDENTITY);
        boolean xinputChange = xinputSwitchable && xinputDirect != appliedXInputDirect;
        return identityChange || dinputVisible != appliedDInputVisible
                || dinputVibration != appliedDInputVibration || xinputChange;
    }

    private void reconnectNow() {
        main.removeCallbacks(autoReconnect);
        if (!needsReconnect()) {
            pending = false;
            publish();
            return;
        }
        pending = false;
        publish();
        winHandler.reconnectSlots(new SlotReconnector.Job() {
            // Read when the reconnect runs, so changes made meanwhile ride along.
            private String targetIdentity;
            private boolean targetDInput;

            @Override public boolean needsRegistry() {
                targetIdentity = nextLaunchOnly.contains(NOTE_IDENTITY) ? appliedIdentity : identity;
                targetDInput = dinputVisible;
                return !targetIdentity.equals(appliedIdentity) || targetDInput != appliedDInputVisible;
            }

            @Override public void registry(Runnable done) {
                JoystickRegistry.Desired desired = JoystickRegistry.desired(targetIdentity, targetDInput,
                        dinputLockedReason != null);
                LiveWineRegistry.apply(winHandler, driveC, desired, (ok, detail) -> done.run());
            }

            @Override public void applyInGap() {
                if (targetIdentity == null) needsRegistry();
                if (!targetIdentity.equals(appliedIdentity)) {
                    HermesSessionControl.writeSlotIdentities(targetIdentity);
                    appliedIdentity = targetIdentity;
                }
                appliedDInputVisible = targetDInput;
                // Before the replug: HERMES reads the mode when the game opens the pad again.
                if (dinputVibration != appliedDInputVibration) {
                    appliedDInputVibration = dinputVibration;
                    HermesSessionControl.writeFfMode(dinputVibration);
                }
                if (xinputSwitchable && xinputDirect != appliedXInputDirect) {
                    appliedXInputDirect = xinputDirect;
                    xinputMode = HermesSessionControl.xinputModeFor(xinputDirect);
                    awaitingXInputStatus = true;
                    HermesSessionControl.writeSessionControl(pacing, xinputMode, xinputFlags, pacingLockedReason != null);
                }
            }
        });
    }

    /** "Next launch": keep the session as it is; the saved values apply when the game starts again. */
    private void nextLaunch() {
        main.removeCallbacks(autoReconnect);
        pending = false;
        if (!identity.equals(appliedIdentity)) addNote(NOTE_IDENTITY);
        if (xinputDirect != appliedXInputDirect) addNote(NOTE_XINPUT);
        if (dinputVisible != appliedDInputVisible) addNote("DirectInput visibility applies on the next launch.");
        if (dinputVibration != appliedDInputVibration) addNote("DirectInput vibration applies on the next launch.");
        publish();
    }

    private void refreshNowHz() {
        int hz = 0;
        for (int slot : winHandler.getUsedSlotsSnapshot())
            hz = (int) Math.max(hz, HermesWriter.getSlotStat(slot, HermesWriter.STAT_READER_AXIS_HZ));
        if (hz != nowHz) {
            nowHz = hz;
            publish();
        }
    }

    private void addNote(String note) {
        if (!nextLaunchOnly.contains(note)) nextLaunchOnly.add(note);
    }

    private void publish() {
        XServerDialogState.INSTANCE.setPad(new XServerDialogState.PadState(
                true,
                identity,
                identityLockedReason,
                true,
                xinputDirect,
                xinputSwitchable,
                dinputVisible,
                dinputLockedReason,
                dinputVibration,
                vibrationLockedReason,
                pacing.format(),
                pacingLockedReason,
                nowHz,
                pending,
                reconnecting,
                new ArrayList<>(nextLaunchOnly)));
    }

    // ── Saving: this game or the container, as the footer's "Save for" says (ControllerSaveTarget) ──

    private void saveIdentity(String value) {
        saveTarget.save(ControllerIdentity.EXTRA, value, () -> container.setControllerIdentity(value));
    }

    private void saveDInput(boolean visible) {
        saveTarget.saveDInput(visible);
    }

    private void saveDInputVibration(boolean on) {
        saveTarget.save(DInputVibration.EXTRA, on ? "1" : "0",
                () -> container.putExtra(DInputVibration.EXTRA, DInputVibration.containerExtra(on)));
    }

    private void saveXInput(boolean direct) {
        saveTarget.save(HermesXInput.EXTRA_KEY, direct ? "1" : "0", () -> container.setHermesXInput(direct));
    }

    private void savePacing(StickPacing value) {
        saveTarget.save(StickPacing.EXTRA, value.format(), () -> container.putExtra(StickPacing.EXTRA, value.format()));
    }
}
