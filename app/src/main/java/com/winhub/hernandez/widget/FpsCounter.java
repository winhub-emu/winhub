package com.winhub.hernandez.widget;

import android.content.Context;
import android.os.SystemClock;

import com.winhub.hernandez.xenvironment.ImageFs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single authoritative FPS source shared by every in-game HUD overlay.
 *
 * <p>{@link #tick()} is invoked once per presented frame from the X-server epoll thread, so it is
 * counter math ONLY — no view access. The Fusion HUD reads {@link #getCurrentFPS()} for the number it draws,
 * so there is one place per renderer to feed.
 *
 * <p>FPS math mirrors the historical per-overlay counters (500 ms compute window). Session
 * avg/min/max + length are ported from GameNative's {@code FrameRating}. Thread-safe: {@code tick()}
 * and the session getters are synchronized; {@code lastFPS} is volatile for lock-free reads from the
 * overlay refresh threads.
 */
public class FpsCounter {
    private static final int COMPUTE_WINDOW_MS = 500;
    private static final int READING_INTERVAL_MS = 1000;
    // No present for this long => the bound window stopped rendering (paused, a static menu, or a
    // present path that isn't ticking this window). Report 0 instead of leaving the last value frozen
    // on the HUD. 1.5 s clears well below 1 fps yet never flickers at normal frame rates.
    private static final long STALE_FPS_MS = 1500;

    private long lastTime = 0;
    private int frameCount = 0;
    private volatile float lastFPS = 0;

    // ---- Frametime ring buffer for percentile lows (1% / 0.1% / 0.01%) -----
    // One entry per presented frame = the inter-present interval in ms. Bounded so the 0.01% low has
    // data to work with (needs ~10k frames to be meaningful) without unbounded growth. tick() only
    // appends (cheap); the percentile sort happens lazily on read at the HUD's ~1s cadence, cached so
    // three getters share one sort.
    private static final int FRAMETIME_RING_CAP = 8192;
    private static final float FRAMETIME_MAX_SANE_MS = 10_000f; // ignore pause/alt-tab gaps
    private static final long LOWS_RECOMPUTE_MIN_MS = 250;
    private final float[] frametimeRing = new float[FRAMETIME_RING_CAP];
    private int frametimeHead = 0;   // next write index
    private int frametimeSize = 0;   // valid samples (<= CAP)
    private volatile long lastFrameTime = 0;  // elapsedRealtime of the previous tick (interval + staleness)
    private FrametimeLows cachedLows = FrametimeLows.EMPTY;
    private long cachedLowsTime = 0;

    // ---- Session statistics (ported from GameNative FrameRating) ----------
    private long sessionStartTime = 0;
    private int readingCount = 0;
    private int maxFPS = 0;
    private int minFPS = Integer.MAX_VALUE;
    private long lastReadingTime = 0;
    private long fpsSum = 0;

    /** Count one presented frame. Cheap, thread-safe, no view access. */
    public synchronized void tick() {
        long now = SystemClock.elapsedRealtime();
        if (lastTime == 0) {
            lastTime = now;
            sessionStartTime = now;
        }

        // Per-present frametime sample for the percentile lows. Skip the first tick (no prior time)
        // and implausibly long gaps (paused / alt-tabbed) so a stall doesn't poison the buffer.
        if (lastFrameTime != 0) {
            float intervalMs = now - lastFrameTime;
            if (intervalMs > 0f && intervalMs < FRAMETIME_MAX_SANE_MS) {
                frametimeRing[frametimeHead] = intervalMs;
                frametimeHead = (frametimeHead + 1) % FRAMETIME_RING_CAP;
                if (frametimeSize < FRAMETIME_RING_CAP) frametimeSize++;
            }
        }
        lastFrameTime = now;
        if (now >= lastTime + COMPUTE_WINDOW_MS) {
            lastFPS = ((float) (frameCount * 1000) / (now - lastTime));

            // Session readings sampled at a coarser 1 s cadence for avg/min/max.
            if (lastReadingTime == 0 || now >= lastReadingTime + READING_INTERVAL_MS) {
                int currentFPS = Math.round(lastFPS);
                readingCount++;
                fpsSum += currentFPS;
                if (currentFPS > maxFPS) maxFPS = currentFPS;
                if (currentFPS > 1 && currentFPS < minFPS) minFPS = currentFPS;
                lastReadingTime = now;
            }

            lastTime = now;
            frameCount = 0;
        }
        frameCount++;
    }

    /** Most recent measured FPS, or 0 when no frame has been presented within {@link #STALE_FPS_MS} —
     *  the bound window stopped rendering, so the last value is stale and must not sit frozen on the
     *  HUD (idle menu, paused game, or a present path not ticking this window). Lock-free. */
    public float getCurrentFPS() {
        long last = lastFrameTime;
        if (last != 0L && SystemClock.elapsedRealtime() - last > STALE_FPS_MS) return 0f;
        return lastFPS;
    }

    public synchronized float getAvgFPS() {
        return readingCount == 0 ? 0f : (float) fpsSum / readingCount;
    }

    public synchronized int getMinFPS() {
        return minFPS == Integer.MAX_VALUE ? 0 : minFPS;
    }

    public synchronized int getMaxFPS() { return maxFPS; }

    // ---- Percentile lows (computed lazily from the frametime ring) ----------

    /** Immutable snapshot of the percentile lows + frametime extremes for one refresh. */
    public static final class FrametimeLows {
        public final float low1Fps;    // FPS at the 99th-percentile (worst 1%) frametime
        public final float low01Fps;   // FPS at the 99.9th-percentile (worst 0.1%) frametime
        public final float low001Fps;  // FPS at the 99.99th-percentile (worst 0.01%) frametime
        public final float minMs;      // fastest frame in the buffer
        public final float maxMs;      // slowest frame in the buffer
        public final int sampleCount;

        public static final FrametimeLows EMPTY = new FrametimeLows(0f, 0f, 0f, 0f, 0f, 0);

        FrametimeLows(float low1Fps, float low01Fps, float low001Fps, float minMs, float maxMs, int sampleCount) {
            this.low1Fps = low1Fps;
            this.low01Fps = low01Fps;
            this.low001Fps = low001Fps;
            this.minMs = minMs;
            this.maxMs = maxMs;
            this.sampleCount = sampleCount;
        }
    }

    /**
     * Percentile lows + frametime min/max over the current ring buffer. The buffer is copied under
     * lock and sorted OUTSIDE the lock (so tick() is never blocked on a sort), and the result is
     * cached for {@link #LOWS_RECOMPUTE_MIN_MS} so the three convenience getters below share a single
     * sort per HUD refresh. Cheap enough to call at ~1 Hz over a few-thousand-sample buffer.
     */
    public FrametimeLows getFrametimeLows() {
        long now = SystemClock.elapsedRealtime();
        float[] copy;
        int n;
        synchronized (this) {
            if (cachedLows != FrametimeLows.EMPTY && now - cachedLowsTime < LOWS_RECOMPUTE_MIN_MS) {
                return cachedLows;
            }
            n = frametimeSize;
            if (n < 2) {
                cachedLows = FrametimeLows.EMPTY;
                cachedLowsTime = now;
                return cachedLows;
            }
            copy = new float[n];
            int start = (frametimeHead - n + FRAMETIME_RING_CAP) % FRAMETIME_RING_CAP;
            for (int i = 0; i < n; i++) copy[i] = frametimeRing[(start + i) % FRAMETIME_RING_CAP];
        }
        java.util.Arrays.sort(copy); // ascending frametime → high index = slow frame = low FPS
        FrametimeLows result = new FrametimeLows(
            fpsAtPercentileFrametime(copy, 0.99),
            fpsAtPercentileFrametime(copy, 0.999),
            fpsAtPercentileFrametime(copy, 0.9999),
            copy[0],
            copy[n - 1],
            n);
        synchronized (this) {
            cachedLows = result;
            cachedLowsTime = now;
        }
        return result;
    }

    private static float fpsAtPercentileFrametime(float[] sortedAsc, double p) {
        int n = sortedAsc.length;
        int idx = (int) Math.round(p * (n - 1));
        if (idx < 0) idx = 0;
        if (idx >= n) idx = n - 1;
        float ft = sortedAsc[idx];
        return ft > 0f ? 1000f / ft : 0f;
    }

    /** FPS at the worst-1% frametime, or 0 until there are enough samples. */
    public float getLow1Pct() { return getFrametimeLows().low1Fps; }

    /** FPS at the worst-0.1% frametime, or 0 until there are enough samples. */
    public float getLow01Pct() { return getFrametimeLows().low01Fps; }

    /** FPS at the worst-0.01% frametime — noisy until ~10k frames, which is expected. */
    public float getLow001Pct() { return getFrametimeLows().low001Fps; }

    public synchronized float getSessionLengthSec() {
        return sessionStartTime == 0 ? 0f : (SystemClock.elapsedRealtime() - sessionStartTime) / 1000.0f;
    }

    /** Clear all counters + session stats (called when the game window is unmapped). */
    public synchronized void reset() {
        lastTime = 0;
        frameCount = 0;
        lastFPS = 0;
        sessionStartTime = 0;
        readingCount = 0;
        maxFPS = 0;
        minFPS = Integer.MAX_VALUE;
        lastReadingTime = 0;
        fpsSum = 0;
        // Clear the frametime ring + percentile cache too, so a new game session starts clean.
        frametimeHead = 0;
        frametimeSize = 0;
        lastFrameTime = 0;
        cachedLows = FrametimeLows.EMPTY;
        cachedLowsTime = 0;
    }

    /**
     * Persist a JSON summary of the current session to {@code $TMP/fps_session.json} on a background
     * thread. Ported from GameNative's FrameRating.writeSessionSummary. No-op if no readings yet.
     */
    public void writeSessionSummary(Context context) {
        final int rc;
        final long lengthMs;
        final int max;
        final int min;
        final float avg;
        synchronized (this) {
            if (readingCount == 0) return;
            rc = readingCount;
            lengthMs = sessionStartTime > 0 ? SystemClock.elapsedRealtime() - sessionStartTime : 0;
            max = maxFPS;
            min = minFPS == Integer.MAX_VALUE ? 0 : minFPS;
            avg = (float) fpsSum / readingCount;
        }
        final float lengthSec = lengthMs / 1000.0f;
        final ImageFs imageFs = ImageFs.find(context);
        final File out = new File(imageFs.getTmpDir(), "fps_session.json");
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (!out.exists()) out.createNewFile();
                String json = String.format(Locale.ENGLISH,
                    "{\n" +
                    "  \"length_sec\": %.2f,\n" +
                    "  \"avg_fps\": %.1f,\n" +
                    "  \"max_fps\": %d,\n" +
                    "  \"min_fps\": %d,\n" +
                    "  \"readings\": %d\n" +
                    "}\n",
                    lengthSec, avg, max, min, rc);
                try (FileWriter fw = new FileWriter(out, false)) {
                    fw.write(json);
                    fw.flush();
                }
            } catch (IOException ignored) {
            } finally {
                executor.shutdown();
            }
        });
    }
}
