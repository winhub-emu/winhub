package com.winhub.hernandez.store;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.widget.TextView;
import android.widget.Toast;

import com.winhub.hernandez.R;
import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.ContainerManager;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.core.FileUtils;
import com.winhub.hernandez.core.WinePath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * Launch bridge for store integrations (GOG / Epic / Amazon).
 *
 * Presents a container picker dialog after a game is downloaded, writes a
 * .desktop shortcut into the chosen Wine container's desktop directory, and
 * downloads + saves the game's cover art as both the shortcut icon (shown in
 * the Shortcuts grid) and as customCoverArt in [Extra Data].
 *
 * Cover art resolution strategy:
 *   1. Use the store-provided URL (passed in by the caller — Epic gives tall
 *      portrait DieselGameBoxTall ~1200x1600; GOG uses SteamGridDB 600x900
 *      first then falls back to its icon CDN).
 *   2. If the store URL is absent or fails to download, fall back to
 *      SteamGridDB autocomplete → grids API (600x900 portrait cover).
 *
 * Shortcut format (Winlator .desktop):
 *   [Desktop Entry]
 *   Name=<game name>
 *   Exec=wine <Z:\path\to\game.exe>
 *   Icon=<safeName>
 *   Type=Application
 *   StartupWMClass=explorer
 *
 *   [Extra Data]
 *   customCoverArtPath=<absolute path to PNG>
 */
public final class StarLaunchBridge {

    private static final String TAG = "BH_BRIDGE";

    /**
     * Optional per-game Epic metadata stamped into a shortcut's [Extra Data] block at
     * creation. Drives the EOS Phase-1 launch-arg injection (see EpicLaunchArgs). Null for
     * non-Epic shortcuts.
     */
    public static final class EpicMeta {
        public final String appName;
        public final String namespace;
        public final String catalogItemId;
        public EpicMeta(String appName, String namespace, String catalogItemId) {
            this.appName       = appName       != null ? appName       : "";
            this.namespace     = namespace     != null ? namespace     : "";
            this.catalogItemId = catalogItemId != null ? catalogItemId : "";
        }
    }
    private static final String SGDB_KEY = "cf89227f12c773bb1117b6b109ae1659";

    private StarLaunchBridge() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Delivers the container list to the caller on the main thread. */
    public interface ContainersCallback {
        void onContainers(ArrayList<Container> containers);
    }

    /** Delivers a shortcut-write outcome to the caller on the main thread. */
    public interface ResultCallback {
        void onResult(boolean success, String message);
    }

    /**
     * Fired on the main thread right after the user picks a container in {@link #addToLauncher},
     * with the chosen container. Lets a store (GOG) hook post-add work — e.g. prompting to install
     * the game's required redistributables into that just-chosen prefix.
     */
    public interface PostAddCallback {
        void onAdded(Container container);
    }

    /**
     * Loads the Wine container list on a worker thread and delivers it on the
     * main thread. Never delivers null — failures deliver an empty list.
     * Used by the Compose add-to-shortcuts flow (Steam store).
     */
    public static void loadContainers(Activity activity, ContainersCallback cb) {
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            ArrayList<Container> containers = null;
            try {
                containers = new ContainerManager(activity).getContainers();
            } catch (Exception e) {
                Log.e(TAG, "loadContainers failed", e);
            }
            ArrayList<Container> result = (containers != null) ? containers : new ArrayList<>();
            h.post(() -> cb.onContainers(result));
        }, "store-launcher-picker").start();
    }

    /**
     * Show a container picker, write a shortcut, then download cover art.
     *
     * @param activity     calling Activity
     * @param gameName     display name (used as shortcut filename and title)
     * @param exePath      absolute Android path to the .exe (under imagefs/)
     * @param coverArtUrl  URL of the game's cover art image, or null to fall
     *                     back to SteamGridDB
     */
    public static void addToLauncher(Activity activity,
                                     String gameName,
                                     String exePath,
                                     String coverArtUrl) {
        addToLauncher(activity, gameName, exePath, coverArtUrl, null);
    }

    /**
     * As above, but stamps Epic per-game metadata (storeSource=epic, epicAppName,
     * epicSandboxId, epicCatalogId, epicEos=1) into the shortcut so EOS Phase-1 launch-arg
     * injection can scope to this game. Pass {@code epic == null} for non-Epic stores.
     */
    public static void addToLauncher(Activity activity,
                                     String gameName,
                                     String exePath,
                                     String coverArtUrl,
                                     EpicMeta epic) {
        addToLauncher(activity, gameName, exePath, coverArtUrl, epic, null);
    }

    /**
     * As above, but invokes {@code postAdd} on the main thread with the chosen container right after
     * the shortcut is written — the GOG redist trigger hooks here to offer prerequisite install into
     * the just-chosen prefix. {@code postAdd} may be null.
     */
    public static void addToLauncher(Activity activity,
                                     String gameName,
                                     String exePath,
                                     String coverArtUrl,
                                     EpicMeta epic,
                                     PostAddCallback postAdd) {
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                ContainerManager manager = new ContainerManager(activity);
                ArrayList<Container> containers = manager.getContainers();

                if (containers == null || containers.isEmpty()) {
                    h.post(() -> showToast(activity,
                            "No Wine container found — create one first in the Containers screen."));
                    return;
                }

                String[] names = new String[containers.size()];
                for (int i = 0; i < containers.size(); i++) {
                    String n = containers.get(i).getName();
                    names[i] = (n != null && !n.isEmpty()) ? n : "Container " + (i + 1);
                }

                ArrayList<Container> finalContainers = containers;
                h.post(() -> new AlertDialog.Builder(activity, R.style.StoreAlertDialogDark)
                        .setTitle("Add \"" + gameName + "\" to…")
                        .setItems(names, (dialog, which) -> {
                                Container chosen = finalContainers.get(which);
                                writeShortcut(activity, chosen, gameName, exePath, coverArtUrl, epic);
                                if (postAdd != null) postAdd.onAdded(chosen);
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

            } catch (Exception e) {
                Log.e(TAG, "addToLauncher failed", e);
                h.post(() -> showToast(activity,
                        "Error loading containers: " + e.getMessage()));
            }
        }, "store-launcher-picker").start();
    }

    /**
     * Store-tagged variant for the non-Steam, non-Epic stores (GOG, Amazon). Stamps
     * {@code storeSource=<store>} into the shortcut so the Games tab can badge it without
     * guessing from the exec path, and — when {@code preferSgdbCover} — resolves the cover as a
     * SteamGridDB 600x900 poster FIRST, falling back to the store's own {@code coverArtUrl}.
     * Amazon only publishes a square icon, so its tiles would otherwise be the odd ones out.
     */
    public static void addToLauncher(Activity activity,
                                     String gameName,
                                     String exePath,
                                     String coverArtUrl,
                                     String storeSource,
                                     boolean preferSgdbCover) {
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                ContainerManager manager = new ContainerManager(activity);
                ArrayList<Container> containers = manager.getContainers();
                if (containers == null || containers.isEmpty()) {
                    h.post(() -> showToast(activity,
                            "No Wine container found — create one first in the Containers screen."));
                    return;
                }
                String[] names = new String[containers.size()];
                for (int i = 0; i < containers.size(); i++) {
                    String n = containers.get(i).getName();
                    names[i] = (n != null && !n.isEmpty()) ? n : "Container " + (i + 1);
                }
                ArrayList<Container> finalContainers = containers;
                h.post(() -> new AlertDialog.Builder(activity, R.style.StoreAlertDialogDark)
                        .setTitle("Add \"" + gameName + "\" to…")
                        .setItems(names, (dialog, which) -> {
                            Container chosen = finalContainers.get(which);
                            writeShortcutAsync(activity, chosen, gameName, exePath, coverArtUrl, 0, null,
                                    storeSource, preferSgdbCover,
                                    (success, message) -> showToast(activity, message));
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
            } catch (Exception e) {
                Log.e(TAG, "addToLauncher(store) failed", e);
                h.post(() -> showToast(activity, "Error loading containers: " + e.getMessage()));
            }
        }, "store-launcher-picker").start();
    }

    /**
     * Convenience overload — falls back to SteamGridDB for cover art.
     */
    public static void addToLauncher(Activity activity, String gameName, String exePath) {
        addToLauncher(activity, gameName, exePath, null);
    }

    /**
     * Writes a .desktop shortcut (plus cover art) into {@code container} on a
     * worker thread and reports the outcome via {@code cb} on the main thread.
     * Same logic as the legacy toast path — callers decide how to surface the
     * result (the Compose Steam flow shows an M3 dialog).
     */
    public static void writeShortcutAsync(Activity activity,
                                          Container container,
                                          String gameName,
                                          String exePath,
                                          String coverArtUrl,
                                          ResultCallback cb) {
        // Legacy overload (GOG / Epic / Amazon paths): no Steam appId → shortcut is left untagged.
        writeShortcutAsync(activity, container, gameName, exePath, coverArtUrl, 0, cb);
    }

    /**
     * As above, but tags the written shortcut with its store origin when a Steam
     * {@code steamAppId} is known (> 0): {@code storeSource=steam} + {@code steamAppId=<id>}
     * in the [Extra Data] block. This makes the Games-tab "Cloud Saves" menu gate robust and
     * hands the Save Manager the appId directly. A {@code steamAppId} of 0 leaves the shortcut
     * untagged (non-Steam stores).
     */
    public static void writeShortcutAsync(Activity activity,
                                          Container container,
                                          String gameName,
                                          String exePath,
                                          String coverArtUrl,
                                          int steamAppId,
                                          ResultCallback cb) {
        writeShortcutAsync(activity, container, gameName, exePath, coverArtUrl, steamAppId, null, cb);
    }

    /**
     * As above, but also stamps Epic per-game metadata into the [Extra Data] block when
     * {@code epic != null} (mutually exclusive with a Steam tag). Enables EOS Phase-1
     * launch-arg injection scoped to this game.
     */
    public static void writeShortcutAsync(Activity activity,
                                          Container container,
                                          String gameName,
                                          String exePath,
                                          String coverArtUrl,
                                          int steamAppId,
                                          EpicMeta epic,
                                          ResultCallback cb) {
        writeShortcutAsync(activity, container, gameName, exePath, coverArtUrl, steamAppId, epic, null, false, cb);
    }

    /**
     * The core writer. {@code storeSource} (e.g. "gog", "amazon") is stamped as
     * {@code storeSource=} when no Steam appId / Epic meta claims the shortcut first;
     * {@code preferSgdbCover} tries the SteamGridDB poster before the store's own URL.
     */
    public static void writeShortcutAsync(Activity activity,
                                          Container container,
                                          String gameName,
                                          String exePath,
                                          String coverArtUrl,
                                          int steamAppId,
                                          EpicMeta epic,
                                          String storeSource,
                                          boolean preferSgdbCover,
                                          ResultCallback cb) {
        Handler h = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                File desktopDir = container.getDesktopDir();
                if (desktopDir == null) {
                    h.post(() -> cb.onResult(false,
                            "Container desktop directory not found."));
                    return;
                }
                if (!desktopDir.exists() && !desktopDir.mkdirs()) {
                    h.post(() -> cb.onResult(false,
                            "Could not create container desktop directory."));
                    return;
                }

                // Sanitise game name → safe filename
                String safeName = gameName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (safeName.isEmpty()) safeName = "game";

                File shortcutFile = new File(desktopDir, safeName + ".desktop");

                // Build the shortcut's Exec= path. No WINEPREFIX in Exec=; Winlator derives WINEPREFIX
                // from the container object via container_id. Both branches emit 4 backslashes per
                // separator so StringUtils.unescape()'s two-pass strip yields a valid X:\path\game.exe.
                //
                //   • A game under imagefs/ is reachable as the container's fixed Z: drive, so keep the
                //     historical Z:\… mapping for it (byte-identical to before).
                //   • A game installed OFF imagefs — the "Install to SD card" option parks it on the
                //     physical card — has no fixed letter, so resolve it through the container's drive
                //     map exactly like the "+" add-game importer: WinePath.resolveWindowsPath maps its
                //     storage volume to a drive letter (reusing a pre-declared one like F:, else
                //     auto-mounting a fresh letter) AND persists container.drives itself, so the letter
                //     survives to launch time with no extra mount code. escapeForExec applies the
                //     4-backslash separators.
                String imageFsRoot = new java.io.File(activity.getFilesDir(), "imagefs").getAbsolutePath();
                String execPath;
                if (exePath.startsWith(imageFsRoot)) {
                    String relPath = exePath.substring(imageFsRoot.length());
                    if (relPath.startsWith("/")) relPath = relPath.substring(1);
                    execPath = "Z:\\\\\\\\" + relPath.replace("/", "\\\\\\\\");
                } else {
                    String winPath = WinePath.INSTANCE.resolveWindowsPath(container, exePath);
                    execPath = WinePath.INSTANCE.escapeForExec(winPath);
                }

                // Icon= references a PNG saved in container.getIconsDir(64) by saveCoverArt().
                String content = "[Desktop Entry]\n"
                        + "Name=" + gameName + "\n"
                        + "Exec=wine " + execPath + "\n"
                        + "Icon=" + safeName + "\n"
                        + "Type=Application\n"
                        + "StartupWMClass=explorer\n"
                        + "\n"
                        + "[Extra Data]\n";

                // Tag Steam-origin shortcuts so the Games-tab Cloud Saves menu can gate on
                // storeSource and the Save Manager can read the appId straight off the shortcut.
                if (steamAppId > 0) {
                    content += "storeSource=steam\n"
                            + "steamAppId=" + steamAppId + "\n";
                    // EA-published title (EA Desktop launcher chain): stamp it on the shortcut like the
                    // store tags above, so the Games tab knows it needs the EA setup / SteamLite path
                    // without re-deriving anything from paths at launch time (EaSupport.detectForShortcut
                    // reads this first). eaAntiCheat=1 marks EA Javelin titles (unsupported under Wine).
                    try {
                        java.io.File eaDir = com.winhub.hernandez.store.steamscript.InstallScriptExecutor.INSTANCE
                                .locateInstallDir(new File(exePath));
                        com.winhub.hernandez.store.EaSupport.Profile ea = com.winhub.hernandez.store.EaSupport.detect(eaDir);
                        if (ea != null) {
                            content += com.winhub.hernandez.store.EaSupport.EXTRA_EA + "=1\n";
                            if (ea.getJavelinAntiCheat()) content += com.winhub.hernandez.store.EaSupport.EXTRA_JAVELIN + "=1\n";
                            Log.i(TAG, "EA title tagged on shortcut: " + gameName + (ea.getJavelinAntiCheat() ? " (Javelin anti-cheat)" : ""));
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "EA detection at shortcut write failed for " + gameName, t);
                    }
                } else if (epic != null && !epic.appName.isEmpty()) {
                    // EOS Phase 1: tag Epic-origin shortcuts so EpicLaunchArgs can scope the
                    // real-Epic auth args to this game. epicEos=1 default (non-EOS games ignore
                    // the args harmlessly); user can toggle it off in the shortcut settings.
                    content += "storeSource=epic\n"
                            + "epicAppName=" + epic.appName + "\n"
                            + "epicSandboxId=" + epic.namespace + "\n"
                            + "epicCatalogId=" + epic.catalogItemId + "\n"
                            + "epicEos=1\n";
                    // Seed the unified "eos" identification extra from the post-install detector
                    // result, but ONLY if it has actually run — otherwise leave it unset so the
                    // ShortcutsScreen background scan computes it (writing a premature "0" would
                    // make that scan skip and cache a false negative).
                    if (EpicEosDetector.hasBeenScanned(activity, epic.appName)) {
                        content += "eos=" + (EpicEosDetector.isEosCached(activity, epic.appName) ? "1" : "0") + "\n";
                    }
                } else if (storeSource != null && storeSource.startsWith("xbox:")) {
                    // Xbox: the product id travels with the shortcut; at launch XServerDisplayActivity
                    // prepares a licensed launch view for it and starts that view's executable.
                    content += "storeSource=xbox\n"
                            + "xboxProductId=" + storeSource.substring(5) + "\n";
                } else if (storeSource != null && !storeSource.isEmpty()) {
                    // GOG / Amazon: an explicit tag, so the badge never has to guess from the path.
                    content += "storeSource=" + storeSource + "\n";
                }

                try (FileWriter fw = new FileWriter(shortcutFile)) {
                    fw.write(content);
                }

                Log.d(TAG, "Wrote shortcut: " + shortcutFile.getPath());

                // Steam install-recipe (local stages): a depot's installScript.vdf Registry + Copy Files
                // (EA entitlement keys + Origin licence files) land in the chosen container now — the
                // first moment (container, appId, installDir) all exist. The Run Process step (EA Desktop
                // installer, needs a Wine session) is driven from the Games tab's EA setup flow instead of
                // restarting the app mid-add. Best-effort — a failure never blocks the shortcut.
                if (steamAppId > 0) {
                    try {
                        com.winhub.hernandez.store.steamscript.InstallScriptExecutor.applyLocalStagesForLaunch(
                                activity, container, steamAppId, exePath);
                    } catch (Throwable t) {
                        Log.w(TAG, "installScript local stages failed for " + gameName, t);
                    }
                }

                // Resolve cover art URL: fix protocol-relative, then try store URL,
                // fall back to SteamGridDB if needed.
                String artUrl;
                if (preferSgdbCover) {
                    // Poster first (600x900 SteamGridDB grid, the same shape Steam / GOG tiles carry),
                    // the store's own image only if SGDB has nothing.
                    artUrl = sgdbFetchCover(gameName);
                    if (artUrl == null || artUrl.isEmpty()) artUrl = normalizeUrl(coverArtUrl);
                } else {
                    artUrl = normalizeUrl(coverArtUrl);
                    if (artUrl == null || artUrl.isEmpty()) {
                        Log.d(TAG, "No store cover art URL — trying SteamGridDB for: " + gameName);
                        artUrl = sgdbFetchCover(gameName);
                    }
                }

                if (artUrl != null && !artUrl.isEmpty()) {
                    saveCoverArt(activity, container, shortcutFile, safeName, artUrl);
                } else {
                    Log.d(TAG, "No cover art found for: " + gameName);
                }

                h.post(() -> cb.onResult(true,
                        "\"" + gameName + "\" added to Shortcuts.\n"
                                + "Open the side menu → Shortcuts to launch and configure it."));

            } catch (Exception e) {
                Log.e(TAG, "writeShortcut failed for " + gameName, e);
                h.post(() -> cb.onResult(false,
                        "Failed to add shortcut: " + e.getMessage()));
            }
        }, "store-write-shortcut").start();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Legacy path (GOG / Epic / Amazon): write the shortcut, toast the result. */
    private static void writeShortcut(Activity activity,
                                      Container container,
                                      String gameName,
                                      String exePath,
                                      String coverArtUrl) {
        writeShortcut(activity, container, gameName, exePath, coverArtUrl, null);
    }

    private static void writeShortcut(Activity activity,
                                      Container container,
                                      String gameName,
                                      String exePath,
                                      String coverArtUrl,
                                      EpicMeta epic) {
        writeShortcutAsync(activity, container, gameName, exePath, coverArtUrl, 0, epic,
                (success, message) -> showToast(activity, message));
    }

    /**
     * Shows a Toast with an explicit custom view. With targetSdk 28 toasts are
     * app-rendered and inherit {@code android:colorBackground} from AppTheme,
     * which this app forces to #000000 — the stock toast then draws black text
     * on a black pill. An explicit white-on-dark-grey view stays readable.
     */
    private static void showToast(Context ctx, String message) {
        float density = ctx.getResources().getDisplayMetrics().density;

        TextView tv = new TextView(ctx);
        tv.setText(message);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        int padH = Math.round(16 * density);
        int padV = Math.round(10 * density);
        tv.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6323232);
        bg.setCornerRadius(16 * density);
        tv.setBackground(bg);

        Toast toast = new Toast(ctx.getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(tv);
        toast.show();
    }

    /**
     * Downloads cover art from {@code url}, saves it as the shortcut icon in
     * {@code container.getIconsDir(64)} (so ShortcutsFragment displays it), and
     * also registers it via {@link Shortcut#saveCustomCoverArt(Bitmap)} so the
     * detail view can use the full-resolution copy.
     *
     * If the store URL fails, a SteamGridDB lookup is attempted automatically
     * before giving up.
     */
    public static void saveCoverArt(Context ctx, Container container,
                                    File shortcutFile, String safeName,
                                    String url) {
        saveCoverArt(ctx, container, shortcutFile, safeName, url, null);
    }

    /**
     * As {@link #saveCoverArt(Context, Container, File, String, String)} but, when a Steam
     * {@code steamAppId} is known, tries the exact SteamGridDB "by Steam appid" grid first —
     * far more reliable than the name search. Order: store {@code url} → SGDB-by-appid →
     * SGDB-by-name ({@code safeName}, the proper title already written to the shortcut).
     */
    public static void saveCoverArt(Context ctx, Container container,
                                    File shortcutFile, String safeName,
                                    String url, Integer steamAppId) {
        Bitmap bmp = downloadBitmap(url);

        // Exact match by Steam appid (skips the fuzzy name search entirely).
        if (bmp == null && steamAppId != null && steamAppId > 0) {
            String appIdUrl = sgdbFetchCoverBySteamAppId(steamAppId);
            if (appIdUrl != null && !appIdUrl.isEmpty()) {
                bmp = downloadBitmap(appIdUrl);
            }
        }

        // If still nothing, try SteamGridDB by the (now proper) name as last resort.
        if (bmp == null) {
            Log.w(TAG, "Store/appid cover art unavailable for " + safeName + ", trying SteamGridDB name search");
            String sgdbUrl = sgdbFetchCover(safeName);
            if (sgdbUrl != null && !sgdbUrl.isEmpty()) {
                bmp = downloadBitmap(sgdbUrl);
            }
        }

        if (bmp == null) {
            Log.w(TAG, "All cover art sources failed for " + safeName);
            return;
        }

        try {
            // Save to icons dir — this is what ShortcutsFragment reads via Icon= field.
            File iconsDir = container.getIconsDir(64);
            if (iconsDir != null) {
                if (!iconsDir.exists()) iconsDir.mkdirs();
                File iconFile = new File(iconsDir, safeName + ".png");
                FileUtils.saveBitmapToFile(bmp, iconFile);
                Log.d(TAG, "Saved icon to: " + iconFile.getPath());
            }

            // Also register as customCoverArt so the shortcut detail view has it.
            Shortcut shortcut = new Shortcut(container, shortcutFile);
            shortcut.saveCustomCoverArt(bmp);
            Log.d(TAG, "Cover art saved for " + safeName);
        } catch (Exception e) {
            Log.w(TAG, "Cover art save failed for " + safeName + ": " + e.getMessage());
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    /** Prepends https: to protocol-relative URLs (//cdn.example.com/…). */
    private static String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        return url.startsWith("//") ? "https:" + url : url;
    }

    /** Downloads a URL and decodes it as a Bitmap. Returns null on any failure. */
    private static Bitmap downloadBitmap(String url) {
        try {
            Log.d(TAG, "Downloading cover art: " + url);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(20_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "Cover art HTTP " + code + " for " + url);
                conn.disconnect();
                return null;
            }
            Bitmap bmp;
            try (InputStream is = conn.getInputStream()) {
                bmp = BitmapFactory.decodeStream(is);
            }
            conn.disconnect();
            if (bmp == null) Log.w(TAG, "Cover art decode returned null for " + url);
            return bmp;
        } catch (Exception e) {
            Log.w(TAG, "downloadBitmap failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Searches SteamGridDB for a 600x900 portrait cover matching {@code title}.
     * Returns the image URL or an empty string on failure.
     */
    private static String sgdbFetchCover(String title) {
        try {
            String encoded = java.net.URLEncoder.encode(title, "UTF-8");
            String searchJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/search/autocomplete/" + encoded);
            if (searchJson == null) return "";
            JSONArray results = new JSONObject(searchJson).optJSONArray("data");
            if (results == null || results.length() == 0) return "";
            int gameId = results.getJSONObject(0).getInt("id");

            String gridsJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/grids/game/" + gameId
                            + "?dimensions=600x900&mimes=image/jpeg,image/png&limit=1");
            if (gridsJson == null) return "";
            JSONArray grids = new JSONObject(gridsJson).optJSONArray("data");
            if (grids == null || grids.length() == 0) return "";
            String imgUrl = grids.getJSONObject(0).optString("url", "");
            Log.d(TAG, "SteamGridDB cover for \"" + title + "\": " + imgUrl);
            return imgUrl;
        } catch (Exception e) {
            Log.w(TAG, "sgdbFetchCover failed for \"" + title + "\": " + e.getMessage());
            return "";
        }
    }

    /**
     * Fetches a 600x900 portrait cover for a Steam {@code appId} directly from SteamGridDB's
     * by-platform endpoint (no name search — exact game). Returns the image URL or "".
     */
    private static String sgdbFetchCoverBySteamAppId(int appId) {
        try {
            String gridsJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/grids/steam/" + appId
                            + "?dimensions=600x900&mimes=image/jpeg,image/png&limit=1");
            if (gridsJson == null) return "";
            JSONArray grids = new JSONObject(gridsJson).optJSONArray("data");
            if (grids == null || grids.length() == 0) return "";
            String imgUrl = grids.getJSONObject(0).optString("url", "");
            Log.d(TAG, "SteamGridDB cover for steam appId " + appId + ": " + imgUrl);
            return imgUrl;
        } catch (Exception e) {
            Log.w(TAG, "sgdbFetchCoverBySteamAppId failed for " + appId + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * The SteamGridDB token to use: the user's own key when they have enabled one in Settings
     * ("enable_custom_api_key" / "custom_api_key", written by both SettingsFragment and
     * SettingsScreen), otherwise the bundled {@link #SGDB_KEY}.
     *
     * NOTE: at the time of writing this is the ONLY reader of that preference — the setting is
     * offered in two settings screens and saved, but the two older SteamGridDB call sites still use
     * the bundled key unconditionally. Routing them through here too is a separate change.
     *
     * Never logged, and never returned to Kotlin — callers pass it straight to {@link #httpGet}.
     */
    private static String sgdbKey(Context ctx) {
        if (ctx == null) return SGDB_KEY;
        try {
            android.content.SharedPreferences p =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx);
            if (p.getBoolean("enable_custom_api_key", false)) {
                String custom = p.getString("custom_api_key", "");
                if (custom != null && !custom.trim().isEmpty()) return custom.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "custom SteamGridDB key unreadable, using the bundled one");
        }
        return SGDB_KEY;
    }

    /**
     * LAST-RESORT capsule art for a Steam {@code appId}, in the store's 92:43 LANDSCAPE shape.
     *
     * Sibling of {@link #sgdbFetchCoverBySteamAppId}, which asks for the 600x900 PORTRAIT cover
     * used by the games wall. The storefront's capsules are 92:43, and 460x215 (Steam's own header
     * size) is exactly that ratio — a portrait cover stretched into a capsule slot looks worse than
     * the themed placeholder it would replace, so the dimensions filter here is deliberately
     * landscape-only and must stay that way.
     *
     * Queried by Steam appId through SteamGridDB's by-platform endpoint, so there is no fuzzy
     * name-matching and no wrong-game hits. BLOCKING — call off the main thread. Returns the image
     * URL, or "" for "no art" / any failure, which the caller treats as a negative result.
     */
    public static String sgdbFetchCapsuleBySteamAppId(Context ctx, int appId) {
        if (appId <= 0) return "";
        try {
            String gridsJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/grids/steam/" + appId
                            + "?dimensions=460x215,920x430"
                            + "&types=static&nsfw=false&mimes=image/jpeg,image/png&limit=1",
                    sgdbKey(ctx));
            if (gridsJson == null) return "";
            JSONArray grids = new JSONObject(gridsJson).optJSONArray("data");
            if (grids == null || grids.length() == 0) return "";
            return grids.getJSONObject(0).optString("url", "");
        } catch (Exception e) {
            Log.w(TAG, "sgdbFetchCapsuleBySteamAppId failed for " + appId + ": " + e.getMessage());
            return "";
        }
    }

    /**
     * Searches SteamGridDB for all available covers matching {@code title}
     * and returns a JSON array of {thumb, url} objects, or "[]" on failure.
     */
    public static String sgdbFetchGridsJson(String title) {
        try {
            String encoded = java.net.URLEncoder.encode(title, "UTF-8");
            String searchJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/search/autocomplete/" + encoded);
            if (searchJson == null) return "[]";
            JSONArray results = new JSONObject(searchJson).optJSONArray("data");
            if (results == null || results.length() == 0) return "[]";
            int gameId = results.getJSONObject(0).getInt("id");

            String gridsJson = httpGet(
                    "https://www.steamgriddb.com/api/v2/grids/game/" + gameId
                            + "?dimensions=600x900&mimes=image/jpeg,image/png");
            if (gridsJson == null) return "[]";
            JSONArray grids = new JSONObject(gridsJson).optJSONArray("data");
            if (grids == null || grids.length() == 0) return "[]";

            JSONArray out = new JSONArray();
            for (int i = 0; i < grids.length(); i++) {
                JSONObject g = grids.getJSONObject(i);
                JSONObject entry = new JSONObject();
                entry.put("thumb", g.optString("thumb", ""));
                entry.put("url", g.optString("url", ""));
                out.put(entry);
            }
            Log.d(TAG, "SteamGridDB found " + out.length() + " covers for \"" + title + "\"");
            return out.toString();
        } catch (Exception e) {
            Log.w(TAG, "sgdbFetchGridsJson failed for \"" + title + "\": " + e.getMessage());
            return "[]";
        }
    }

    private static String httpGet(String url) {
        return httpGet(url, SGDB_KEY);
    }

    /**
     * Same request, with an explicit SteamGridDB bearer token so a caller that has a Context can
     * pass the user's own key (see {@link #sgdbKey}). Split out rather than duplicated so there is
     * still exactly ONE SteamGridDB HTTP path in the app.
     *
     * The token is never logged, here or anywhere else.
     */
    private static String httpGet(String url, String bearer) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Authorization", "Bearer " + bearer);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() != 200) { conn.disconnect(); return null; }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
