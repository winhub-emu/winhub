package com.winhub.hernandez.winhandler;

import java.util.Arrays;

/**
 * Reconnects the held player slots so the game sees the change of an in-game controller setting
 * (identity, DirectInput visibility, XInput source): the pad leaves and comes back, which every
 * hot-plug aware game handles. Serialized: requests while one runs are coalesced into one more run
 * afterwards (a job reads the wanted state when it applies, so the last request wins).
 *
 * <p>The sequence, on the scheduler's thread (the main thread in the app):
 * <ol start="0">
 *   <li>M = the held slots; "reconnecting" to the listener</li>
 *   <li>neutralise M (published state, system buttons, rumble), then block M: nothing writes to those
 *       slots or creates their writers until the replug</li>
 *   <li>the registry change, if the job has one (regedit; it calls back when done or timed out)</li>
 *   <li>hold neutral for max(2 game frames, 50 ms), so the game reads the release before the removal</li>
 *   <li>unplug M (writer destroy: removal for every reader, node deleted; slot maps kept)</li>
 *   <li>apply the job's change while nothing is attached (identity, session control, XInput mode)</li>
 *   <li>wait the gap (400 ms; SDL ignores a re-add with the same device number until it has
 *       processed the removal)</li>
 *   <li>replug the slots still held, ascending, and republish their contributors' state</li>
 *   <li>confirm: every replugged slot gains a reader attach (50 ms polls, 2 s); one retry of 4–8</li>
 *   <li>"reconnected" (ok or not) to the listener</li>
 * </ol>
 * Pure Java: the clock, the scheduler and the pad operations are interfaces, so a fake clock can
 * drive it in tests.
 */
public final class SlotReconnector {
    public static final long GAP_MS_DEFAULT = 400;
    public static final long GAP_MS_MIN = 250;
    public static final long GAP_MS_MAX = 1000;
    static final long HOLD_MS_MIN = 50;
    static final long HOLD_MS_MAX = 250;
    static final long CONFIRM_POLL_MS = 50;
    static final long CONFIRM_TIMEOUT_MS = 2000;

    public interface Scheduler {
        void post(Runnable task, long delayMs);
        long now();
    }

    /** The pad side (WinHandler). Called on the scheduler's thread. */
    public interface Target {
        /** The slots a controller holds now, ascending. */
        int[] heldSlots();
        /** Publishes neutral state, releases system buttons, stops rumble. */
        void neutralise(int[] slots);
        /** No writes and no writer creation for these slots until {@link #replug}. */
        void block(int[] slots);
        /** The game's frame interval in ms, or 0 when unknown. */
        long frameIntervalMs();
        /** Writer destroy (removal for readers, node deleted); the slot assignment is kept. */
        void unplug(int slot);
        /** Unblocks, recreates the slots' writers (nodes) and republishes their contributors' state. */
        void replug(int[] slots);
        /** HermesWriter STAT_READER_ATTACHES of the slot. */
        long readerAttaches(int slot);
    }

    /** One requested change. Read the wanted state when called, not when requested. */
    public interface Job {
        boolean needsRegistry();
        /** The registry change; call {@code done} once it finished or gave up (any thread is fine). */
        void registry(Runnable done);
        /** Applied while the slots are unplugged. */
        void applyInGap();
    }

    public interface Listener {
        void onReconnecting(int[] slots);
        /** {@code ok}: every replugged slot was opened again by the game. */
        void onReconnected(boolean ok, int[] slots, int attempts);
    }

    private final Scheduler scheduler;
    private final Target target;
    private final Listener listener;
    private long gapMs = GAP_MS_DEFAULT;

    private boolean running;
    private Job pending;
    // The current run.
    private Job job;
    private int[] slots = new int[0];
    private long[] attachBase = new long[0];
    private long confirmStarted;
    private int attempt;
    private int run;

    public SlotReconnector(Scheduler scheduler, Target target, Listener listener) {
        this.scheduler = scheduler;
        this.target = target;
        this.listener = listener;
    }

    /** The unplug gap, clamped to 250..1000 ms. */
    public void setGapMs(long gapMs) {
        this.gapMs = Math.max(GAP_MS_MIN, Math.min(GAP_MS_MAX, gapMs));
    }

    public long getGapMs() {
        return gapMs;
    }

    public boolean isRunning() {
        return running;
    }

    /** Starts now, or once more after the running one (requests meanwhile coalesce into one). */
    public void request(Job newJob) {
        if (newJob == null) return;
        if (running) {
            pending = newJob;
            return;
        }
        start(newJob);
    }

    private void start(Job next) {
        running = true;
        job = next;
        attempt = 1;
        final int thisRun = ++run;
        slots = target.heldSlots();
        if (slots == null) slots = new int[0];
        listener.onReconnecting(slots.clone());
        if (slots.length > 0) {
            target.neutralise(slots);
            target.block(slots);
        }
        if (job.needsRegistry()) {
            final boolean[] once = {false};
            job.registry(() -> scheduler.post(() -> {
                if (once[0] || thisRun != run) return;
                once[0] = true;
                hold(thisRun);
            }, 0));
        } else {
            hold(thisRun);
        }
    }

    private void hold(int thisRun) {
        long frame = Math.max(0, target.frameIntervalMs());
        long holdMs = Math.max(HOLD_MS_MIN, Math.min(HOLD_MS_MAX, 2 * frame));
        if (slots.length == 0) holdMs = 0;
        scheduler.post(() -> { if (thisRun == run) unplug(thisRun); }, holdMs);
    }

    private void unplug(int thisRun) {
        for (int slot : slots) target.unplug(slot);
        job.applyInGap();
        if (slots.length == 0) {
            finish(true);
            return;
        }
        scheduler.post(() -> { if (thisRun == run) replug(thisRun); }, gapMs);
    }

    private void replug(int thisRun) {
        // Only the slots still held: a pad unplugged meanwhile has nothing to come back to.
        int[] held = target.heldSlots();
        slots = intersect(slots, held != null ? held : new int[0]);
        attachBase = new long[slots.length];
        for (int i = 0; i < slots.length; i++) attachBase[i] = target.readerAttaches(slots[i]);
        target.replug(slots);
        if (slots.length == 0) {
            finish(true);
            return;
        }
        confirmStarted = scheduler.now();
        scheduler.post(() -> { if (thisRun == run) confirm(thisRun); }, CONFIRM_POLL_MS);
    }

    private void confirm(int thisRun) {
        boolean all = true;
        for (int i = 0; i < slots.length; i++) {
            if (target.readerAttaches(slots[i]) <= attachBase[i]) { all = false; break; }
        }
        if (all) {
            finish(true);
            return;
        }
        if (scheduler.now() - confirmStarted < CONFIRM_TIMEOUT_MS) {
            scheduler.post(() -> { if (thisRun == run) confirm(thisRun); }, CONFIRM_POLL_MS);
            return;
        }
        if (attempt == 1) {
            // One retry of the unplug/replug, without the registry step.
            attempt = 2;
            target.neutralise(slots);
            target.block(slots);
            hold(thisRun);
            return;
        }
        finish(false);
    }

    private void finish(boolean ok) {
        int[] done = slots.clone();
        int attempts = attempt;
        running = false;
        job = null;
        listener.onReconnected(ok, done, attempts);
        Job next = pending;
        pending = null;
        if (next != null) start(next);
    }

    static int[] intersect(int[] a, int[] b) {
        int[] out = new int[a.length];
        int n = 0;
        for (int x : a) {
            for (int y : b) {
                if (x == y) { out[n++] = x; break; }
            }
        }
        int[] r = Arrays.copyOf(out, n);
        Arrays.sort(r);
        return r;
    }
}
