package com.winhub.hernandez.core;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads what is actually on disk under the chosen log location and groups it by game, so the Log
 * Manager can list "God of War — 4 files, 6.2 MB, 12 min ago" without the UI knowing anything about
 * folder layout.
 *
 * Everything here is derived from the filesystem rather than tracked in a database: logs are written
 * by DXVK and Wine as much as by us, a user can delete or copy them from a file manager at any time,
 * and a stored index would drift from reality the moment they did.
 */
public final class LogInventory {

    private LogInventory() {}

    /** One game's worth of logs (or the {@code _app} bucket). */
    public static final class Entry {
        public final String name;          // display name = folder name
        public final File dir;
        public final int fileCount;        // current-run files only, archives excluded
        public final long totalBytes;      // including archived runs
        public final long lastModified;
        public final boolean isAppBucket;  // the "_app" folder: logcat + crash reports
        public final int archivedRuns;
        /** Loose files in the log root — pre-folder logs, or everything when per-game is off. */
        public final boolean isLooseBucket;

        Entry(String name, File dir, int fileCount, long totalBytes, long lastModified,
              boolean isAppBucket, int archivedRuns) {
            this(name, dir, fileCount, totalBytes, lastModified, isAppBucket, archivedRuns, false);
        }

        Entry(String name, File dir, int fileCount, long totalBytes, long lastModified,
              boolean isAppBucket, int archivedRuns, boolean isLooseBucket) {
            this.name = name;
            this.dir = dir;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.lastModified = lastModified;
            this.isAppBucket = isAppBucket;
            this.archivedRuns = archivedRuns;
            this.isLooseBucket = isLooseBucket;
        }
    }

    /**
     * All log groups, newest activity first. When per-game folders are off there is a single
     * "All logs" entry pointing at the flat directory, so the same list UI works either way.
     */
    public static List<Entry> scan(Context context) {
        List<Entry> out = new ArrayList<>();
        File base = LogLocation.resolveLogDir(context);
        if (base == null || !base.isDirectory()) return out;

        File[] kids = base.listFiles();
        if (kids == null) return out;

        // Only list folders that actually contain logs WE wrote. The log root is shared: on the
        // default app-data location it also holds ReShade/, and on a custom location every folder
        // the user happens to have. Listing those as "games" would be wrong, and would hand a
        // future delete button a path to something we never created.
        boolean sawFolder = false;
        for (File f : kids) {
            if (!f.isDirectory()) continue;
            if (!containsOurLogs(f)) continue;
            sawFolder = true;
            out.add(describe(f, LogLocation.APP_FOLDER.equals(f.getName())));
        }

        // Loose files sitting directly in the log dir: either per-game folders are off, or these are
        // logs from before the folders existed. Either way they are real and must be listable — we
        // deliberately do not migrate or delete anything a previous version wrote.
        List<File> loose = new ArrayList<>();
        for (File f : kids) if (f.isFile() && isOurs(f.getName())) loose.add(f);
        if (!loose.isEmpty()) {
            long bytes = 0, newest = 0;
            for (File f : loose) { bytes += f.length(); newest = Math.max(newest, f.lastModified()); }
            out.add(new Entry(sawFolder ? "Older logs" : "All logs", base,
                    loose.size(), bytes, newest, false, 0, true));
        }

        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return Long.compare(b.lastModified, a.lastModified);
            }
        });
        return out;
    }

    /**
     * The log group for one game, or null when it has none yet. Used by the per-shortcut "View logs"
     * entry, which has a game name and nothing else to go on.
     *
     * Deliberately narrow: it resolves ONLY a per-game folder. With per-game folders off every game
     * writes into the same flat directory under names we cannot attribute back to a shortcut, so
     * there is no honest answer — the caller is expected to say so rather than open a viewer full of
     * some other game's logs. {@link LogLocation#resolveGameLogDir} is not used because it CREATES
     * the folder, and merely asking whether logs exist must not.
     */
    public static Entry forGame(Context context, String gameName) {
        if (gameName == null || gameName.trim().isEmpty()) return null;
        if (!LogLocation.isPerGameEnabled(context)) return null;
        File base = LogLocation.resolveLogDir(context);
        if (base == null || !base.isDirectory()) return null;

        File dir = new File(base, LogLocation.sanitizeFolderName(gameName));
        if (!dir.isDirectory() || !containsOurLogs(dir)) return null;
        return describe(dir, false);
    }

    /**
     * True when a directory holds at least one file this app produced — a current-run log, a crash
     * report, or an archived run. Cheap and shallow on purpose: it runs for every folder in the log
     * root each time the screen opens.
     */
    private static boolean containsOurLogs(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile() && isOurs(f.getName())) return true;   // isOurs covers crash reports too
            if (f.isDirectory() && LogRotation.ARCHIVE_DIR.equals(f.getName())) return true;
            if (f.isDirectory() && LogRotation.WAYLAND_DIR.equals(f.getName()) && !waylandLogs(f).isEmpty()) return true;
        }
        return false;
    }

    /** The Wayland session logs in a game folder's {@code wayland/} (only {@link LogRotation#isOurWaylandLog} names), newest first. */
    private static List<File> waylandLogs(File waylandDir) {
        List<File> out = new ArrayList<>();
        File[] files = waylandDir == null ? null : waylandDir.listFiles();
        if (files != null) for (File f : files) if (f.isFile() && LogRotation.isOurWaylandLog(f.getName())) out.add(f);
        Collections.sort(out, (a, b) -> b.getName().compareTo(a.getName()));
        return out;
    }

    private static Entry describe(File dir, boolean appBucket) {
        File[] files = dir.listFiles();
        int count = 0;
        long bytes = 0, newest = dir.lastModified();
        int archives = 0;
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    if (LogRotation.ARCHIVE_DIR.equals(f.getName())) {
                        File[] runs = f.listFiles(File::isDirectory);
                        archives = runs == null ? 0 : runs.length;
                        bytes += sizeOfTree(f);
                    } else if (LogRotation.WAYLAND_DIR.equals(f.getName())) {
                        for (File w : waylandLogs(f)) { bytes += w.length(); newest = Math.max(newest, w.lastModified()); }
                    }
                    continue;
                }
                if (!isOurs(f.getName())) continue;
                count++;
                bytes += f.length();
                newest = Math.max(newest, f.lastModified());
            }
        }
        return new Entry(dir.getName(), dir, count, bytes, newest, appBucket, archives);
    }

    /** One run of a game: the current one, or an archived launch under {@code previous/}. */
    public static final class Run {
        public final File dir;
        public final boolean current;
        /** When the run happened, from the archive folder's timestamp name. */
        public final long millis;
        /** The {@code wayland/} folder: its files are the Wayland session logs, newest first. */
        public final boolean wayland;

        Run(File dir, boolean current, long millis) {
            this(dir, current, millis, false);
        }

        Run(File dir, boolean current, long millis, boolean wayland) {
            this.dir = dir;
            this.current = current;
            this.millis = millis;
            this.wayland = wayland;
        }
    }

    /**
     * Every run whose logs are still on disk: the current one first, then archived launches newest
     * first. This is what "keep last 5" actually produces, and without it the viewer could only ever
     * show the newest launch — which is the wrong one whenever a game worked yesterday and doesn't
     * today.
     */
    public static List<Run> runsIn(File groupDir) {
        List<Run> out = new ArrayList<>();
        if (groupDir == null || !groupDir.isDirectory()) return out;
        out.add(new Run(groupDir, true, groupDir.lastModified()));
        // The game's Wayland session logs: one entry, listed after the current run.
        File wl = new File(groupDir, LogRotation.WAYLAND_DIR);
        List<File> wlLogs = waylandLogs(wl);
        if (!wlLogs.isEmpty()) out.add(new Run(wl, false, wlLogs.get(0).lastModified(), true));

        File[] archived = new File(groupDir, LogRotation.ARCHIVE_DIR).listFiles(File::isDirectory);
        if (archived == null) return out;

        List<Run> old = new ArrayList<>();
        for (File f : archived) old.add(new Run(f, false, stampToMillis(f.getName(), f.lastModified())));
        // Sort on the folder NAME's timestamp, not mtime, which a copy or a file manager rewrites.
        Collections.sort(old, new Comparator<Run>() {
            @Override public int compare(Run a, Run b) { return Long.compare(b.millis, a.millis); }
        });
        out.addAll(old);
        return out;
    }

    /** {@code yyyy-MM-dd_HH-mm-ss} as written by LogRotation, falling back to the folder's mtime. */
    private static long stampToMillis(String name, long fallback) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                    .parse(name).getTime();
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Current-run log files in a group, newest first. Archives are not included. */
    public static List<File> filesIn(File dir) {
        if (dir != null && LogRotation.WAYLAND_DIR.equals(dir.getName())) return waylandLogs(dir);
        List<File> out = new ArrayList<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile() && isOurs(f.getName())) out.add(f);
        Collections.sort(out, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    /**
     * Delete the logs THIS APP wrote in one group, and nothing else.
     *
     * The first version of this method took a directory and deleteTree()'d it, which was only ever
     * safe while scan() never returned a folder we did not create — and scan() did exactly that
     * (ReShade/), while the "Older logs" group points at the shared log root, which holds
     * steam_debug.txt and friends, and on a CUSTOM location is a folder full of the user's own
     * files. So this walks the folder and removes only names on {@link LogRotation#isOurRunLog}'s
     * allowlist plus our own crash reports, recurses ONLY into the {@code previous/} archive we
     * created, and removes the folder itself solely when we have emptied it and it is not the log
     * root. Anything else in there survives, by construction rather than by good intentions.
     *
     * @return how many files were actually deleted.
     */
    public static int deleteGroup(Context context, Entry entry) {
        if (entry == null || entry.dir == null || !entry.dir.isDirectory()) return 0;
        int deleted = 0;
        File[] files = entry.dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    // Only our own archive folder, never an arbitrary subdirectory.
                    if (LogRotation.ARCHIVE_DIR.equals(f.getName())) deleted += deleteTree(f);
                    else if (LogRotation.WAYLAND_DIR.equals(f.getName())) {
                        // Only the session logs the compositor wrote; the folder goes once it is empty.
                        for (File w : waylandLogs(f)) if (w.delete()) deleted++;
                        File[] left = f.listFiles();
                        if (left != null && left.length == 0) //noinspection ResultOfMethodCallIgnored
                            f.delete();
                    }
                    continue;
                }
                if (isOurs(f.getName()) && f.delete()) deleted++;
            }
        }
        File base = LogLocation.resolveLogDir(context);
        if (base != null && !entry.dir.equals(base)) {
            File[] left = entry.dir.listFiles();
            if (left != null && left.length == 0) //noinspection ResultOfMethodCallIgnored
                entry.dir.delete();
        }
        return deleted;
    }

    /** How many files {@link #deleteGroup} would remove — so the confirmation can say a number. */
    public static int deletableCount(Entry entry) {
        if (entry == null || entry.dir == null) return 0;
        int n = 0;
        File[] files = entry.dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    if (LogRotation.ARCHIVE_DIR.equals(f.getName())) n += countTree(f);
                    else if (LogRotation.WAYLAND_DIR.equals(f.getName())) n += waylandLogs(f).size();
                } else if (isOurs(f.getName())) n++;
            }
        }
        return n;
    }

    /**
     * A file THIS APP wrote — and the ONLY definition of that in this class. Every delete, every
     * listing, and everything {@link #filesIn} hands to {@code LogReport} for zipping goes through
     * here.
     *
     * There used to be a second, looser one: {@code isLog}, matching any {@code .log} or
     * {@code .txt}. That is precisely the shape {@link LogRotation#isOurRunLog} was made public to
     * prevent — its javadoc says so — and it had been fixed on the delete path but not on the
     * listing or the report path. Since the report path zips what it finds for the user to attach
     * to a PUBLIC issue, on a shared log root that meant other subsystems' {@code .txt} files, and
     * on a user-chosen one the user's own documents, going into a bundle we called safe to post.
     */
    private static boolean isOurs(String name) {
        String n = name.toLowerCase(Locale.US);
        if (LogRotation.isOurRunLog(n)) return true;
        // Wayland session logs (wayland/ folder; its own exact name pattern, not part of the run rotation).
        if (LogRotation.isOurWaylandLog(n)) return true;
        // Crash reports are ours and are .txt, which the run-log allowlist deliberately excludes.
        return n.startsWith("crash_") && n.endsWith(".txt");
    }

    private static int deleteTree(File f) {
        if (f == null) return 0;
        int n = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += deleteTree(k);
        if (f.isFile() && f.delete()) n++;
        else if (f.isDirectory()) //noinspection ResultOfMethodCallIgnored
            f.delete();
        return n;
    }

    private static int countTree(File f) {
        if (f == null) return 0;
        if (f.isFile()) return 1;
        int n = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += countTree(k);
        return n;
    }

    public static long totalBytes(List<Entry> entries) {
        long t = 0;
        for (Entry e : entries) t += e.totalBytes;
        return t;
    }

    public static String humanBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024f);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / (1024f * 1024f));
        return String.format(Locale.US, "%.2f GB", b / (1024f * 1024f * 1024f));
    }

    private static long sizeOfTree(File f) {
        if (f == null) return 0;
        if (f.isFile()) return f.length();
        File[] kids = f.listFiles();
        long t = 0;
        if (kids != null) for (File k : kids) t += sizeOfTree(k);
        return t;
    }

}
