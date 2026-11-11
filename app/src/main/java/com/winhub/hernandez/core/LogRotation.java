package com.winhub.hernandez.core;

import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps a bounded history of past runs inside a game's log folder.
 *
 * The current run's files keep their plain names — {@code wine_debug.log}, {@code <game>_d3d11.log} —
 * so "the latest log" is always the obvious file, and anyone who already knows where
 * {@code wine_debug.log} lives still finds it. At the START of a launch the previous run's files are
 * moved into {@code previous/<timestamp>/} and the oldest archives beyond the keep-count are deleted.
 *
 * Rotating on launch rather than on exit is deliberate: a crashed or force-killed game never gets to
 * run exit code, and that is exactly the run whose log matters most.
 *
 * Only files we can attribute to a run are moved — the sweep is shallow, so it never touches the
 * {@code previous/} folder itself or anything a user dropped in there.
 */
public final class LogRotation {

    private static final String TAG = "LogRotation";
    public static final String ARCHIVE_DIR = "previous";
    /** The game folder's Wayland session logs: one {@code wayland-<stamp>.log} per run (compositor.c). */
    public static final String WAYLAND_DIR = "wayland";
    private static final java.util.regex.Pattern WAYLAND_LOG =
            java.util.regex.Pattern.compile("wayland-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.log");

    private LogRotation() {}

    /** A Wayland session log the compositor wrote: {@code wayland-yyyy-MM-dd_HH-mm-ss.log}, nothing looser. */
    public static boolean isOurWaylandLog(String name) {
        return name != null && WAYLAND_LOG.matcher(name).matches();
    }

    /**
     * Before a Wayland session opens its log in {@code waylandDir}: delete the oldest session logs so
     * {@code keepLast} remain, the same history the other logs keep in previous/ (the new session's
     * file is then the current one). Only {@link #isOurWaylandLog} names are touched. Never throws.
     */
    public static void pruneWaylandLogs(File waylandDir, int keepLast) {
        if (waylandDir == null || !waylandDir.isDirectory()) return;
        try {
            File[] logs = waylandDir.listFiles(f -> f.isFile() && isOurWaylandLog(f.getName()));
            if (logs == null || logs.length <= Math.max(keepLast, 0)) return;
            // The stamp in the name sorts chronologically; mtime can be rewritten by a copy.
            Arrays.sort(logs, (a, b) -> b.getName().compareTo(a.getName()));
            for (int i = Math.max(keepLast, 0); i < logs.length; i++) //noinspection ResultOfMethodCallIgnored
                logs[i].delete();
        } catch (Exception e) {
            Log.w(TAG, "wayland prune failed for " + waylandDir, e);
        }
    }

    /**
     * Archive whatever the last run left in {@code gameDir}, then prune to {@code keepLast} archives.
     * A {@code keepLast} of 0 means "no history": the old files are deleted outright.
     * Never throws — logging must not be able to stop a game launching.
     */
    public static void rotate(File gameDir, int keepLast) {
        if (gameDir == null || !gameDir.isDirectory()) return;
        try {
            File[] stale = gameDir.listFiles(f -> f.isFile() && isOurRunLog(f.getName()));
            if (stale == null || stale.length == 0) {
                pruneArchives(gameDir, keepLast);
                return;
            }

            if (keepLast <= 0) {
                for (File f : stale) //noinspection ResultOfMethodCallIgnored
                    f.delete();
                pruneArchives(gameDir, 0);
                return;
            }

            // Stamp the archive with the newest file it contains, not "now" — the folder name then
            // describes when that run actually happened rather than when the next one started.
            long newest = 0;
            for (File f : stale) newest = Math.max(newest, f.lastModified());
            if (newest == 0) newest = System.currentTimeMillis();

            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date(newest));
            File archive = new File(new File(gameDir, ARCHIVE_DIR), stamp);
            if (!archive.mkdirs() && !archive.isDirectory()) {
                Log.w(TAG, "could not create archive dir " + archive);
                return;
            }

            for (File f : stale) {
                File dst = new File(archive, f.getName());
                if (!f.renameTo(dst)) {
                    // Same directory tree, so a rename should always work; if it somehow doesn't,
                    // drop the file rather than leaving last run's output to be appended to.
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            pruneArchives(gameDir, keepLast);
        } catch (Exception e) {
            Log.w(TAG, "rotate failed for " + gameDir, e);
        }
    }

    /** Delete the oldest archives until at most {@code keepLast} remain. */
    private static void pruneArchives(File gameDir, int keepLast) {
        File archiveRoot = new File(gameDir, ARCHIVE_DIR);
        File[] runs = archiveRoot.listFiles(File::isDirectory);
        if (runs == null || runs.length <= Math.max(keepLast, 0)) return;

        // Sort by name: the timestamp format is lexicographically ordered, so this is chronological
        // and does not depend on mtime, which a copy or a file manager can rewrite.
        Arrays.sort(runs, (a, b) -> b.getName().compareTo(a.getName()));
        for (int i = Math.max(keepLast, 0); i < runs.length; i++) deleteTree(runs[i]);
    }

    private static void deleteTree(File f) {
        if (f == null) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    /**
     * Whether a file is one THIS APP wrote for a game run, and may therefore archive or delete.
     *
     * Matching any {@code .log}/{@code .txt} was wrong and destructive: rotate() runs on whatever
     * resolveGameLogDir() returns, which with per-game folders OFF is the flat log root. On the
     * default app-data location that root already holds other subsystems' files — steam_debug.txt,
     * steam_session.txt, bh_epic_debug.txt, bh_gog_debug.txt, bh_amazon_debug.txt — and with a
     * CUSTOM location it is a folder the user chose, which may be full of their own documents.
     * Every launch would have archived them and pruning would then have deleted them for good.
     *
     * So: an explicit allowlist of the names we actually produce. DXVK derives its filenames from
     * the executable, hence the suffix forms rather than exact matches.
     *
     * Public because the Log Manager's delete actions must use exactly THIS list — a second,
     * looser definition of "a log file" is how the destructive bug got in the first time.
     */
    public static boolean isOurRunLog(String name) {
        String n = name.toLowerCase(Locale.US);
        // The one .txt we own per run: SteamLite's combined client-log bundle. It has to be listed
        // BEFORE the ".log only" gate below, and it earns its place on this allowlist the same way
        // everything else does — it is a fixed name WE write, so it is safe to archive/prune/delete
        // even in a shared or user-chosen log root (which is exactly why this list is exhaustive and
        // not a "any .txt" match: see the class javadoc). It is not a crash report, so — unlike
        // crash_*.txt, which lives in the _app bucket — it does belong to a game's per-run rotation.
        if (n.equals(com.winhub.hernandez.core.SteamLiteLogCollector.OUTPUT_NAME)) return true;
        if (n.equals(com.winhub.hernandez.core.ControllerLog.OUTPUT_NAME)) return true;
        if (!n.endsWith(".log")) return false;              // never .txt — that is other people's
        return n.equals("wine_debug.log")
                || n.equals("logcat.log")
                || n.equals("vkd3d-proton.log")
                // _d3d8 was missing until a real log folder was inspected on device: DXVK writes
                // AIO-Graphics-Test-32bit_d3d8.log for a D3D8 title, and without this the file is
                // ours but invisible — never listed, never rotated, never in a report.
                || n.endsWith("_d3d8.log")
                || n.endsWith("_d3d9.log")
                || n.endsWith("_d3d10.log")
                || n.endsWith("_d3d11.log")
                || n.endsWith("_d3d12.log")
                || n.endsWith("_dxgi.log");
    }
}
