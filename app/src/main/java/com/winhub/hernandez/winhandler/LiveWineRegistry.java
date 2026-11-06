package com.winhub.hernandez.winhandler;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winhub.hernandez.core.JoystickRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Changes HKCU\Software\Wine\DirectInput\Joysticks while the game runs. user.reg belongs to the running
 * wineserver then (a file edit would be lost or clobbered, and a second {@code wine reg} would risk a
 * second wineserver), so the change is written as {@code C:\bljoy.reg} and imported by Wine's own
 * {@code regedit.exe /S}, started through winhandler.exe ({@link WinHandler#execRaw}). winhandler.exe
 * sends no answer, so the process list is polled until regedit has come and gone (3 s at most).
 * Wine's DirectInput reads the key on every enumeration, so the reconnect that follows sees it.
 */
public final class LiveWineRegistry {
    private static final String TAG = "LiveWineRegistry";
    static final String REG_FILE = "bljoy.reg";
    static final String REGEDIT = "regedit.exe";
    static final String REGEDIT_ARGS = "/S C:\\" + REG_FILE;
    static final long POLL_MS = 100;
    static final long TIMEOUT_MS = 3000;
    /** regedit can be quicker than a poll: never seen and still absent after this = done. */
    static final long UNSEEN_DONE_MS = 600;

    public interface Callback {
        /** Main thread. {@code ok} false = regedit could not be started or did not finish in time. */
        void onDone(boolean ok, String detail);
    }

    private LiveWineRegistry() {}

    /** Main thread. {@code driveC} is the prefix's drive_c. */
    public static void apply(WinHandler winHandler, File driveC, JoystickRegistry.Desired desired, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        File reg = new File(driveC, REG_FILE);
        try (FileOutputStream out = new FileOutputStream(reg)) {
            out.write(JoystickRegistry.toRegFile(desired).getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            Log.w(TAG, "could not write " + reg, e);
            main.post(() -> callback.onDone(false, "could not write " + REG_FILE));
            return;
        }
        if (!winHandler.execRaw(REGEDIT, REGEDIT_ARGS)) {
            main.post(() -> callback.onDone(false, "regedit command too long"));
            return;
        }
        new Watch(winHandler, main, callback).start();
    }

    /**
     * Decides from one complete process list whether regedit has finished. Pure, for tests: feed it the
     * names of each complete round and the time.
     */
    static final class RegeditWatch {
        enum State { WAITING, DONE, TIMED_OUT }

        private final long startedAt;
        private boolean seen;

        RegeditWatch(long now) {
            startedAt = now;
        }

        State onRound(List<String> names, long now) {
            boolean running = false;
            for (String n : names) {
                if (n != null && n.toLowerCase(Locale.ROOT).equals(REGEDIT)) { running = true; break; }
            }
            if (running) seen = true;
            if (!running && (seen || now - startedAt >= UNSEEN_DONE_MS)) return State.DONE;
            if (now - startedAt >= TIMEOUT_MS) return State.TIMED_OUT;
            return State.WAITING;
        }

        boolean timedOut(long now) {
            return now - startedAt >= TIMEOUT_MS;
        }
    }

    private static final class Watch implements OnGetProcessInfoListener {
        private final WinHandler winHandler;
        private final Handler main;
        private final Callback callback;
        private final RegeditWatch watch = new RegeditWatch(android.os.SystemClock.uptimeMillis());
        private final List<String> round = new ArrayList<>();
        private boolean finished;

        Watch(WinHandler winHandler, Handler main, Callback callback) {
            this.winHandler = winHandler;
            this.main = main;
            this.callback = callback;
        }

        void start() {
            winHandler.addProcessInfoListener(this);
            main.postDelayed(this::poll, POLL_MS);
            main.postDelayed(() -> finish(false, "regedit did not finish within " + TIMEOUT_MS + " ms"), TIMEOUT_MS + POLL_MS);
        }

        private void poll() {
            if (finished) return;
            winHandler.listProcesses();
            main.postDelayed(this::poll, POLL_MS);
        }

        @Override
        public void onGetProcessInfo(int index, int count, ProcessInfo processInfo) {
            // Receive thread: collect one complete list, then decide on the main thread.
            final List<String> names;
            synchronized (round) {
                if (index == 0) round.clear();
                if (processInfo != null) round.add(processInfo.name);
                if (count > 0 && index < count - 1) return;
                names = new ArrayList<>(round);
                round.clear();
            }
            main.post(() -> {
                if (finished) return;
                RegeditWatch.State state = watch.onRound(names, android.os.SystemClock.uptimeMillis());
                if (state == RegeditWatch.State.DONE) finish(true, "regedit done");
                else if (state == RegeditWatch.State.TIMED_OUT) finish(false, "regedit did not finish within " + TIMEOUT_MS + " ms");
            });
        }

        private void finish(boolean ok, String detail) {
            if (finished) return;
            finished = true;
            winHandler.removeProcessInfoListener(this);
            main.removeCallbacksAndMessages(null);
            Log.i(TAG, "DirectInput joystick entries " + (ok ? "updated" : "NOT confirmed") + ": " + detail);
            callback.onDone(ok, detail);
        }
    }
}
