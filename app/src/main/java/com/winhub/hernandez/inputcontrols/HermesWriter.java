package com.winhub.hernandez.inputcontrols;

import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * App side of the HERMES controller-input transport: publishes the complete state of one
 * player slot (0..3) for the guest evdev layers (libfakeinput.so) to read.
 *
 * <p>Each slot is one fixed-size shared file, {@code <imagefs>/dev/hermes/pad<slot>}, holding
 * the current pad state (never an append-only stream, so it can never grow or lose framing).
 * Readers diff it against what they last delivered: button edges are replayed in order (a press
 * and a release between two guest reads are both delivered), sticks are coalesced to the latest
 * value. The native half lives in libhermes.so; its per-slot state is process-wide and outlives
 * writer objects, so a reconnect or a new session continues from the same state.
 *
 * <p>The public surface mirrors the previous writer class (same names and meaning), so callers
 * can switch by renaming the type:
 * <ul>
 *   <li>{@link #writeGamepadState} takes absolute state; an unchanged state publishes nothing and
 *       wakes nobody. It is allocation-free, never blocks and does no file I/O.</li>
 *   <li>{@link #softRelease} / {@link #reset} neutralise without an unplug; {@link #destroy} is an
 *       unplug (every reader attached to the slot sees a removal) and deletes the discovery node.</li>
 *   <li>A publish that cannot reach the backing keeps the state; the next successful publish (or
 *       the backing being recreated, which the native side retries at most once a second) leaves
 *       the guest with the full current state. {@link #lastPublishFailed()} reports it.</li>
 * </ul>
 * Instances are thread-safe; the static members are too.
 */
public final class HermesWriter {
    private static final String TAG = "HermesWriter";

    public static final int MAX_SLOTS = 4;
    /** Sibling of {@code dev/input} that holds the slot files. */
    public static final String BACKING_DIR_NAME = "hermes";

    // Indices for getSlotStat (match hermes_core::writer::STAT_*).
    public static final int STAT_PUBLISHES = 0;
    public static final int STAT_EDGES = 1;
    public static final int STAT_WAKE_CALLS = 2;
    public static final int STAT_WOKEN = 3;
    public static final int STAT_UNPLUGS = 4;
    public static final int STAT_READER_ATTACHES = 5;
    public static final int STAT_READER_REATTACHES = 6;
    public static final int STAT_READER_TAPS = 7;
    public static final int STAT_READER_COALESCED = 8;
    public static final int STAT_READER_RESYNCS = 9;
    public static final int STAT_READER_FRAMES = 10;
    public static final int STAT_READER_EVENTS = 11;
    public static final int STAT_READER_REMOVALS = 12;
    public static final int STAT_INSTANCE = 13;
    public static final int STAT_FAILED_PUBLISHES = 14;
    public static final int STAT_HAS_BACKING = 15;
    /** Stick-only frames readers held back (paced to HERMES_AXIS_HZ) before delivering them. */
    public static final int STAT_READER_PACED_FRAMES = 16;
    /** Publishes readers merged into a later frame instead of delivering one each. */
    public static final int STAT_READER_MERGED_AXIS = 17;
    /** The stick-frame rate (Hz) a reader last paced to; under frame credits the game's frame rate. */
    public static final int STAT_READER_AXIS_HZ = 18;
    /** Auto pacing: times a reader lost a fresh frame feed and fell back to the fixed 60 Hz. */
    public static final int STAT_READER_AUTO_FALLBACKS = 19;
    /** The game's last observed frame interval (ns, 0 = none, or a gap over 1 s). */
    public static final int STAT_FRAME_INTERVAL_NS = 20;
    /** Game frames counted by the feed so far (this app process): the frame credits. */
    public static final int STAT_GAME_FRAMES = 21;
    /**
     * How a reader last paced stick-only frames: 1 frame credits (the game's frame rate governs),
     * 2 60 Hz fallback, 3 fixed, 4 unpaced, 5 frame credits capped by the auto ceiling
     * (HERMES_AXIS_AUTO_MAX, the game presents faster than that).
     */
    public static final int STAT_READER_AXIS_MODE = 22;
    /** Futex wakes the frame feed issued (only while a reader waited for a frame). */
    public static final int STAT_FRAME_WAKES = 23;
    /** Wake-ups of the readers' per-slot wake threads, summed over every reader process. */
    public static final int STAT_READER_HELPER_WAKES = 24;
    /** The auto-mode ceiling (Hz, HERMES_AXIS_AUTO_MAX) the readers pace under. */
    public static final int STAT_READER_AUTO_MAX = 25;
    /** Control block (format 1.5): the control gen a reader last applied (0 = none yet). */
    public static final int STAT_READER_CTL_GEN_APPLIED = 26;
    /** The control gen whose controller identity the last reader attach took. */
    public static final int STAT_READER_ID_GEN_ATTACHED = 27;
    /** Identity queries (hermes_identity / hermes_slot_identity) readers answered. */
    public static final int STAT_READER_IDENTITY_READS = 28;
    /** This slot's own control gen (what the readers' applied gen converges to). */
    public static final int STAT_CONTROL_GEN = 29;
    /** Force feedback (format 1.6), all reader processes: effects uploaded (new or updated). */
    public static final int STAT_READER_FF_UPLOADS = 30;
    /** Force-feedback effects started (played with a count). */
    public static final int STAT_READER_FF_PLAYS = 31;
    /** Rumble datagrams the guest's force-feedback engine sent to the app. */
    public static final int STAT_READER_FF_DATAGRAMS = 32;
    /** Anti-buzz guard fades begun (a constant force held steady for 2 s, faded out). */
    public static final int STAT_READER_FF_GUARD_FADES = 33;
    private static final int STAT_COUNT = 34;

    // Values for setSessionControl (match hermes_core::layout::CTL_*).
    /** Stick pacing: leave it to the guest environment (HERMES_AXIS_HZ / _AUTO_MAX). */
    public static final int AXIS_MODE_UNSET = 0;
    /** Stick pacing: one stick update per game frame, at most {@code autoMax} per second. */
    public static final int AXIS_MODE_AUTO = 1;
    /** Stick pacing: a fixed {@code axisHz} (30..1000). */
    public static final int AXIS_MODE_FIXED = 2;
    /** Stick pacing: every stick change goes out at once. */
    public static final int AXIS_MODE_UNPACED = 3;
    /** XInput DLL: unset (= direct). */
    public static final int XINPUT_MODE_UNSET = 0;
    public static final int XINPUT_MODE_DIRECT = 1;
    public static final int XINPUT_MODE_FORWARD = 2;
    /** xinputFlags bit: forwarding to the next XInput implementation is available. */
    public static final int XINPUT_FLAG_FORWARD_AVAILABLE = 1;
    /** xinputFlags bit: hide the pads from XInput. */
    public static final int XINPUT_FLAG_HIDDEN = 2;
    /** Force feedback (setFfMode): unset, which the guest treats as on. */
    public static final int FF_MODE_UNSET = 0;
    /** Force feedback on: DirectInput rumble, periodic, constant, ramp and condition effects. */
    public static final int FF_MODE_ON = 1;
    /** Force feedback off: rumble effects only. */
    public static final int FF_MODE_OFF = 2;

    // Each slot's counters as they stood when releaseAllRingSlots() retired its file, for the
    // session-end diagnostics that may run after the release.
    private static final long[][] RELEASED_STATS = new long[MAX_SLOTS][STAT_COUNT];

    private static final boolean NATIVE_OK;

    static {
        boolean ok = false;
        try {
            System.loadLibrary("hermes");
            ok = true;
        } catch (Throwable t) {
            Log.e(TAG, "libhermes.so could not be loaded; controller input is unavailable", t);
        }
        NATIVE_OK = ok;
    }

    private static native boolean nativePrepare(String backingDir, int slot);
    private static native void nativeRelease(int slot);
    private static native boolean nativeOpen(int slot, String backingDir);
    private static native boolean nativePublish(int slot, int buttons, float lx, float ly, float rx,
                                                float ry, float lt, float rt, int dpad);
    private static native boolean nativeSetOverlay(int slot, int bits);
    private static native boolean nativeReset(int slot);
    private static native boolean nativeUnplug(int slot);
    private static native long nativeStat(int slot, int which);
    private static native void nativeGameFrame();
    private static native boolean nativeSetSlotIdentity(int slot, int bustype, int vendor, int product,
                                                        int version, String key, String name);
    private static native boolean nativeClearSlotIdentity(int slot);
    private static native void nativeSetSessionControl(int axisMode, int axisHz, int autoMax,
                                                       int xinputMode, int xinputFlags);
    private static native long nativeGetControlGen(int slot);
    private static native void nativeSetFfMode(int mode);

    // ------------------------------------------------------------------------------------------
    // static (process-wide)

    /** {@code <imagefs>/dev/hermes} for {@code <imagefs>/dev/input}. */
    public static File backingDir(File fakeInputDir) {
        File parent = fakeInputDir.getAbsoluteFile().getParentFile();
        return new File(parent != null ? parent : fakeInputDir, BACKING_DIR_NAME);
    }

    /** The slot file a guest attaches to. */
    public static File backingFile(File fakeInputDir, int slot) {
        return new File(backingDir(fakeInputDir), "pad" + slot);
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < MAX_SLOTS;
    }

    /** Bit N set = slot N's backing exists and is live. */
    private static synchronized int prepareSlots(File fakeInputDir, int slotCount) {
        if (!NATIVE_OK || fakeInputDir == null) return 0;
        File dir = backingDir(fakeInputDir);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            Log.w(TAG, "cannot create " + dir);
            return 0;
        }
        int count = Math.max(0, Math.min(slotCount, MAX_SLOTS));
        String path = dir.getAbsolutePath();
        int mask = 0;
        for (int slot = 0; slot < count; slot++) {
            if (nativePrepare(path, slot)) mask |= 1 << slot;
            else Log.w(TAG, "could not prepare " + new File(dir, "pad" + slot));
        }
        return mask;
    }

    /**
     * Make sure the backing of slots {@code 0..slotCount-1} (clamped to 4) exists and is live,
     * so readers can attach before any pad connects. Idempotent.
     */
    public static void prepareRingSlots(File fakeInputDir, int slotCount) {
        prepareSlots(fakeInputDir, slotCount);
    }

    /**
     * Prepare all 4 slots and return the {@code FAKE_EVDEV_MEMFD_PATHS} value
     * ({@code <slot>=<absolute path>;...}), or null if nothing could be prepared.
     */
    public static String getRingEnv(File fakeInputDir) {
        int mask = prepareSlots(fakeInputDir, MAX_SLOTS);
        if (mask == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            if ((mask & (1 << slot)) == 0) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(slot).append('=').append(backingFile(fakeInputDir, slot).getAbsolutePath());
        }
        return sb.toString();
    }

    /**
     * Retire and delete every slot's backing (session end). Readers still attached show a neutral
     * pad and re-attach by themselves when the next session prepares new files; this is not an
     * unplug. The writers' state is kept.
     */
    public static synchronized void releaseAllRingSlots() {
        if (!NATIVE_OK) return;
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            if (nativeStat(slot, STAT_HAS_BACKING) != 0) {
                for (int which = 0; which < STAT_COUNT; which++) {
                    RELEASED_STATS[slot][which] = nativeStat(slot, which);
                }
            }
            nativeRelease(slot);
        }
    }

    /**
     * The game presented a frame. Call it once for every frame the game's own window presents (not
     * the desktop shell, not frames a frame generator adds, not HUD redraws), whether or not any HUD
     * is shown: readers in auto pacing (the default) send one stick-only update per game frame, so a
     * game that takes one input report per rendered frame never falls behind. Cheap enough for the
     * present threads: one JNI call, a few atomic stores; a futex wake only while a reader waits for
     * the frame; no allocation, no lock.
     */
    public static void onGameFrame() {
        if (NATIVE_OK) nativeGameFrame();
    }

    /**
     * The controller identity the guest reports for {@code slot} (evdev bus type, vendor, product,
     * version; {@code key} a short ASCII tag such as "xbox360", at most 15 characters; {@code name}
     * the device name, at most 63 UTF-8 bytes, cut at a character boundary). Kept for the session
     * and written into every file the slot gets, so it survives file recreation.
     *
     * <p><b>Ordering contract:</b> call it, and let it return, BEFORE the slot's discovery node is
     * created. A reader captures the identity when it attaches and never changes it under an open
     * handle, so a node made first can be opened with the previous identity. Changing it later
     * affects the next attach only. Returns false for a bad slot or without the native library.
     */
    public static boolean setSlotIdentity(int slot, int bustype, int vendor, int product, int version,
                                          String key, String name) {
        if (!NATIVE_OK || !validSlot(slot)) return false;
        return nativeSetSlotIdentity(slot, bustype, vendor, product, version, key, name);
    }

    /** No identity for {@code slot}: readers fall back to their own default (same ordering rule). */
    public static boolean clearSlotIdentity(int slot) {
        if (!NATIVE_OK || !validSlot(slot)) return false;
        return nativeClearSlotIdentity(slot);
    }

    /**
     * Session settings for all four slots, applied live: readers pick up a stick-pacing change at
     * their next pacing decision (within a few milliseconds; no input is lost or replayed), and
     * the XInput DLL reads its mode and flags. {@code axisMode} is one of AXIS_MODE_*; AXIS_MODE_UNSET
     * keeps the guest environment's pacing. {@code axisHz}: the fixed rate (30..1000).
     * {@code autoMax}: the auto ceiling (20..240, 0 = 60). {@code xinputMode}: XINPUT_MODE_*;
     * {@code xinputFlags}: XINPUT_FLAG_* bits. Out-of-range values are clamped.
     */
    public static void setSessionControl(int axisMode, int axisHz, int autoMax, int xinputMode,
                                         int xinputFlags) {
        if (NATIVE_OK) nativeSetSessionControl(axisMode, axisHz, autoMax, xinputMode, xinputFlags);
    }

    /**
     * DirectInput force feedback for all four slots: one of FF_MODE_* (anything else = unset). With
     * it on, the guest offers DirectInput games rumble, periodic, constant, ramp and condition
     * effects and mixes them into the pad's two motors (vibration arrives as the usual rumble
     * datagrams); off offers rumble effects only. A guest handle reads the mode when the game opens
     * the device, so set it with the session settings, before the game starts. HERMES_DINPUT_FF=1
     * or 0 in the guest environment overrides it. Bumps every slot's control gen when it changes.
     */
    public static void setFfMode(int mode) {
        if (NATIVE_OK) nativeSetFfMode(mode);
    }

    /**
     * The slot's control gen: bumped by every identity, session-control or force-feedback mode
     * change; 0 if unavailable.
     */
    public static long getControlGen(int slot) {
        if (!NATIVE_OK || !validSlot(slot)) return 0;
        return nativeGetControlGen(slot);
    }

    /** A counter for diagnostics (see the STAT_* constants), or 0. */
    public static long getSlotStat(int slot, int which) {
        if (!NATIVE_OK || !validSlot(slot)) return 0;
        return nativeStat(slot, which);
    }

    /**
     * {@link #getSlotStat} of the slot's live file, or, once {@link #releaseAllRingSlots()} has
     * retired it at session end, the value it had then (0 if it never had a file).
     */
    public static synchronized long getSessionStat(int slot, int which) {
        if (!NATIVE_OK || !validSlot(slot) || which < 0 || which >= STAT_COUNT) return 0;
        if (nativeStat(slot, STAT_HAS_BACKING) != 0) return nativeStat(slot, which);
        return RELEASED_STATS[slot][which];
    }

    // ------------------------------------------------------------------------------------------
    // one writer per player slot

    private final String fakeInputPath;
    private final int slot;
    private volatile boolean open;
    private boolean destroyed;
    private volatile boolean lastPublishFailed;

    /** Writer for player slot {@code slot} (0..3). {@code fakeInputPath} is the dev/input dir. */
    public HermesWriter(String fakeInputPath, int slot) {
        this.fakeInputPath = fakeInputPath;
        this.slot = slot;
    }

    private File node() {
        return new File(fakeInputPath, "event" + slot);
    }

    /**
     * "This slot now has a controller": create the discovery node {@code event<slot>} and make
     * the slot live for readers. Idempotent. False after {@link #destroy()} or on failure.
     */
    public synchronized boolean open() {
        if (destroyed || !NATIVE_OK || !validSlot(slot) || fakeInputPath == null) return false;
        if (open) return true;
        File node = node();
        try {
            if (!node.exists() && !node.createNewFile() && !node.exists()) {
                Log.w(TAG, "could not create " + node);
                return false;
            }
        } catch (IOException e) {
            Log.w(TAG, "could not create " + node, e);
            return false;
        }
        File dir = backingDir(new File(fakeInputPath));
        if (!dir.isDirectory()) dir.mkdirs();
        if (!nativeOpen(slot, dir.getAbsolutePath())) return false;
        open = true;
        return true;
    }

    private void noteResult(boolean ok) {
        if (!ok && !lastPublishFailed) {
            Log.w(TAG, "slot " + slot + ": publish could not reach the backing; state kept, retrying");
        }
        lastPublishFailed = !ok;
    }

    /**
     * "This is the slot's complete current state." Absolute, not a delta: only what changed since
     * the last publish reaches the guest, and an unchanged state produces nothing. No reference to
     * {@code state} is kept.
     */
    public synchronized void writeGamepadState(GamepadState state) {
        if (state == null || (!open && !open())) return;
        int dpad = (state.dpad[0] ? 1 : 0) | (state.dpad[1] ? 2 : 0)
                | (state.dpad[2] ? 4 : 0) | (state.dpad[3] ? 8 : 0);
        noteResult(nativePublish(slot, state.buttons, state.thumbLX, state.thumbLY,
                state.thumbRX, state.thumbRY, state.triggerL, state.triggerR, dpad));
    }

    /**
     * OR the app's own Guide (BTN_MODE) and/or A over whatever the pad holds on this slot and
     * publish now. Clearing never releases a button the user is physically holding; every edge
     * reaches the guest, in order.
     */
    public synchronized void setSystemButtons(boolean guide, boolean a) {
        if (!open && !open()) return;
        noteResult(nativeSetOverlay(slot, (guide ? 1 : 0) | (a ? 2 : 0)));
    }

    /** Neutralise: every button released, every axis 0, published at once. Stays connected. */
    public synchronized void reset() {
        if (!open && !open()) return;
        noteResult(nativeReset(slot));
    }

    /** Neutralise, then mark this writer closed. Not an unplug: readers stay attached and valid. */
    public synchronized void softRelease() {
        reset();
        close();
    }

    /**
     * "This controller was unplugged": neutralise, make every reader attached to the slot see a
     * removal, delete the discovery node. The writer is dead afterwards ({@link #open()} returns
     * false); a reconnect uses a new writer.
     */
    public synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        open = false;
        if (NATIVE_OK && validSlot(slot)) nativeUnplug(slot);
        if (fakeInputPath != null && validSlot(slot)) {
            File node = node();
            if (node.exists() && !node.delete()) Log.w(TAG, "could not delete " + node);
        }
    }

    /** Marks the writer closed. No guest-visible effect. */
    public synchronized void close() {
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    /** True if the most recent publish could not reach the backing (it is retried). */
    public boolean lastPublishFailed() {
        return lastPublishFailed;
    }

    public int getSlot() {
        return slot;
    }
}
