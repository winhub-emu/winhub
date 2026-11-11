package com.winhub.hernandez.core;

import android.content.Context;
import android.os.Environment;

import androidx.preference.PreferenceManager;

import java.io.File;

/**
 * Central resolver for the folder every log file is written to (issue #70). The tester wanted the
 * Wine debug log — and the DXVK/DXGI/VKD3D logs — landing somewhere a file manager can actually
 * reach, since the default {@code getExternalFilesDir(null)} (Android/data/&lt;pkg&gt;/files) is
 * hidden on Android 12+.
 *
 * The choice lives in the default SharedPreferences under {@link #PREF_MODE} (one of the MODE_*
 * constants) plus {@link #PREF_CUSTOM_PATH} for the "Choose folder…" case. Everything that opens a
 * log goes through {@link #resolveLogDir(Context)}, which mkdirs()+writability-checks the target and
 * silently falls back to the app-data dir so a bad path can never kill logging.
 */
public final class LogLocation {

    public static final String PREF_MODE = "log_location_mode";
    public static final String PREF_CUSTOM_PATH = "log_location_custom_path";

    /** Give each game its own folder under the chosen location. Default ON. */
    public static final String PREF_PER_GAME = "log_per_game_folders";
    /** How many past launches to keep per game before the oldest are pruned. */
    public static final String PREF_KEEP_LAST = "log_keep_last_runs";
    public static final int DEFAULT_KEEP_LAST = 5;

    /** Folder for logs that belong to no particular game (app logcat, crash reports). */
    public static final String APP_FOLDER = "_app";

    /** RETIRED — getExternalFilesDir(null) (Android/data/&lt;pkg&gt;) crashed MediaProvider FUSE under
     *  log rotation. Kept only so stored prefs still parse; resolveLogDir() migrates it to Documents. */
    public static final String MODE_APP_DATA = "app_data";
    public static final String MODE_DOWNLOAD = "download";    // /sdcard/Download/bannerlator
    public static final String MODE_DOCUMENTS = "documents";  // /sdcard/Documents/bannerlator — default
    public static final String MODE_CUSTOM = "custom";        // user-picked folder

    private LogLocation() {}

    /**
     * Resolve the configured log directory, creating it if needed. Defaults to Documents and, as a
     * LAST-RESORT fallback for an unwritable target, degrades to INTERNAL storage
     * ({@code getFilesDir()}) — never {@code getExternalFilesDir(null)} (Android/data/&lt;pkg&gt;).
     * The Android/data path lives on the restricted-FUSE subtree, and heavy log churn there (keep-N
     * rotation deletes files while they're held open) can crash a device's MediaProvider FUSE daemon,
     * after which vold kills every process holding a descriptor on emulated storage — taking the whole
     * container session down. Documents/Download are ordinary public dirs and don't hit that path.
     */
    public static File resolveLogDir(Context context) {
        // Internal, non-FUSE — always writable, cannot provoke the MediaProvider FUSE crash.
        File fallback = context.getFilesDir();
        try {
            String mode = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PREF_MODE, MODE_DOCUMENTS);
            File storage = Environment.getExternalStorageDirectory();
            File documents = new File(storage, "Documents/bannerlator");
            File dir;
            switch (mode != null ? mode : MODE_DOCUMENTS) {
                case MODE_DOWNLOAD:
                    dir = new File(storage, "Download/bannerlator");
                    break;
                case MODE_CUSTOM:
                    String custom = PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(PREF_CUSTOM_PATH, null);
                    dir = (custom != null && !custom.isEmpty()) ? new File(custom) : documents;
                    break;
                case MODE_APP_DATA:   // RETIRED: crashed MediaProvider FUSE — migrate silently to Documents.
                case MODE_DOCUMENTS:
                default:
                    dir = documents;
                    break;
            }
            if (dir == null) return fallback;
            if (!dir.exists()) dir.mkdirs();
            if (dir.isDirectory() && dir.canWrite()) return dir;
        } catch (Exception e) {
            // ignore — degrade to internal storage below
        }
        return fallback;
    }

    /**
     * Folder for one game's logs: {@code <logDir>/<game name>/}. Everything a launch produces lands
     * here together — the Wine log we write, and the DXVK/VKD3D logs, because we control their
     * destinations too ({@code DXVK_LOG_PATH} is a directory, {@code VKD3D_LOG_FILE} a full path).
     *
     * Named after the shortcut so the folder is recognisable in a file manager; only characters that
     * are genuinely illegal in a filename are stripped, so "Grand Theft Auto IV" stays readable.
     * A blank or unusable name falls back to {@link #APP_FOLDER} rather than writing to the root.
     *
     * Returns the flat log dir unchanged when per-game folders are off, so callers need no branch.
     */
    public static File resolveGameLogDir(Context context, String gameName) {
        File base = resolveLogDir(context);
        try {
            boolean perGame = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(PREF_PER_GAME, true);
            if (!perGame) return base;

            String safe = sanitizeFolderName(gameName);
            File dir = new File(base, safe);
            if (!dir.exists()) dir.mkdirs();
            if (dir.isDirectory() && dir.canWrite()) return dir;
        } catch (Exception e) {
            // fall through — a bad name must never stop a game from launching
        }
        return base;
    }

    /**
     * Folder for one game's Wayland session logs: {@code <game folder>/wayland/} (the flat log root's
     * {@code wayland/} when per-game folders are off), created if needed. Null when it can't be
     * written; the compositor then keeps its old Download/Wayland-logs folder.
     */
    public static File resolveWaylandLogDir(Context context, String gameName) {
        try {
            File dir = new File(resolveGameLogDir(context, gameName), LogRotation.WAYLAND_DIR);
            if (!dir.exists()) dir.mkdirs();
            if (dir.isDirectory() && dir.canWrite()) return dir;
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    /**
     * Strip only what a filesystem actually rejects, and guard the cases that would escape the
     * folder ({@code .}, {@code ..}) or collide with the app folder. Length-capped because some
     * shortcut names are very long and ext4/FAT cap a component at 255 bytes.
     */
    public static String sanitizeFolderName(String name) {
        if (name == null) return APP_FOLDER;
        String safe = name.replaceAll("[/\\\\:*?\"<>|\\x00-\\x1F]", "").trim();
        // Trailing dots and spaces are silently dropped by some filesystems — remove them so the
        // folder we create is the folder we later look for.
        safe = safe.replaceAll("[. ]+$", "");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) return APP_FOLDER;
        if (safe.length() > 120) safe = safe.substring(0, 120).trim();
        return safe;
    }

    /** Folder for logs not tied to a game (app logcat, crash reports). */
    public static File resolveAppLogDir(Context context) {
        File base = resolveLogDir(context);
        boolean perGame;
        try {
            perGame = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(PREF_PER_GAME, true);
        } catch (Exception e) {
            perGame = true;
        }
        if (!perGame) return base;
        File dir = new File(base, APP_FOLDER);
        if (!dir.exists()) dir.mkdirs();
        return (dir.isDirectory() && dir.canWrite()) ? dir : base;
    }

    /**
     * Whether logs are being filed into a folder per game. Callers that DELETE or MOVE files must
     * check this: with it off, the "game log dir" is the shared log root, which may contain other
     * subsystems' files or, on a custom location, the user's own.
     */
    public static boolean isPerGameEnabled(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_PER_GAME, true);
        } catch (Exception e) {
            return false; // unknown => treat as shared, i.e. do not touch anything
        }
    }

    /** Configured number of past runs to keep per game. */
    public static int keepLastRuns(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(PREF_KEEP_LAST, DEFAULT_KEEP_LAST);
        } catch (Exception e) {
            return DEFAULT_KEEP_LAST;
        }
    }
}
