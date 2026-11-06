package com.winhub.hernandez.core;

import android.content.Context;
import android.util.Log;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * "Direct XInput (HERMES)": the HERMES-XInput DLLs in a prefix, so XInput games read the pad
 * straight from the HERMES slot files instead of through Wine's winebus/hidclass report queue.
 *
 * <p><b>The bundle.</b> CI builds the DLLs from {@code app/src/main/cpp/hermes/xinput} (one source,
 * one DLL per XInput name: xinput1_1, 1_2, 1_3, 1_4, 9_1_0 and xinputuap, each with its own export
 * table) for x86_64, i386 and ARM64X into the assets folder {@code hermes-xinput/}, with a
 * manifest.json of their sha256s. Nothing binary is committed; a build without that step carries no
 * bundle and the setting does nothing.
 *
 * <p><b>The prefix.</b> While the setting is on, every launch makes sure system32 holds the 64-bit
 * build and syswow64 the i386 build. In an x86_64 prefix (box64) that is the x86_64 build. In an
 * arm64ec prefix it is the ARM64X build: one image with an ARM64EC view, which x64 games load, and a
 * native ARM64 view, which Wine's own ARM64 programs load (control.exe with joy.cpl, explorer, ...);
 * an x64 or ARM64EC-only image there fails to load in every native ARM64 process (c000007b), so
 * {@code HERMES_XINPUT_ARCH=x86_64} (the x64 build there instead) is for diagnosis only,
 * and the session gets {@code WINEDLLOVERRIDES} xinput1_1,...,xinputuap=n,b. Whatever was in a slot
 * before (Wine's placeholder dlls) is moved to {@code <prefix>/winhub-hxinput/backup/} once,
 * every copy goes through a temporary file and a rename, and what was installed is recorded in
 * {@code installed.properties} before it is put in place, so an interrupted launch leaves nothing the
 * next one cannot finish or undo. With the setting off, the next launch puts the prefix's own files
 * back and nothing is overridden, so Wine's builtin xinput is used. A slot counts as ours only when
 * its sha256 is one we installed; a file that replaced ours since is never deleted.
 *
 * <p><b>Switch.</b> Per container ({@link Container#isHermesXInput()}, default off), per game (the
 * shortcut extra {@value #EXTRA_KEY}, "1"/"0", empty = the container's), and for testing the env var
 * {@value #ENV_SWITCH}=1/0 in the container's or the game's environment, which wins over both.
 */
public final class HermesXInput {
    private HermesXInput() {}

    private static final String TAG = "HermesXInput";

    static final String ASSET_DIR = "hermes-xinput";
    public static final String ENV_SWITCH = "HERMES_XINPUT";
    public static final String ENV_ARCH = "HERMES_XINPUT_ARCH";
    public static final String EXTRA_KEY = "hermesXInput";

    /** The DLL names it stands in for (and that the WINEDLLOVERRIDES entry covers). */
    public static final String[] DLLS = {"xinput1_1", "xinput1_2", "xinput1_3", "xinput1_4", "xinput9_1_0", "xinputuap"};
    private static final String OVERRIDE_MODE = "n,b";

    private static final String STATE_DIR = "winhub-hxinput";
    private static final String MARKER = "installed.properties";

    // ── Which way this launch goes ───────────────────────────────────────────────────────────────

    /** On or off for this launch: env var (game's, then container's) > game extra > container. */
    public static boolean effective(Container container, Shortcut shortcut) {
        String env = null;
        if (shortcut != null) env = envValue(shortcut.getExtra("envVars"));
        if (env == null && container != null) env = envValue(container.getEnvVars());
        if ("1".equals(env)) return true;
        if ("0".equals(env)) return false;
        if (shortcut != null) {
            String extra = shortcut.getExtra(EXTRA_KEY);
            if ("1".equals(extra)) return true;
            if ("0".equals(extra)) return false;
        }
        return container != null && container.isHermesXInput();
    }

    /** {@value #ENV_ARCH} from the game's or the container's env vars, or null. */
    public static String archOverride(Container container, Shortcut shortcut) {
        String v = null;
        if (shortcut != null) v = envGet(shortcut.getExtra("envVars"), ENV_ARCH);
        if (v == null && container != null) v = envGet(container.getEnvVars(), ENV_ARCH);
        return v;
    }

    private static String envValue(String raw) {
        String v = envGet(raw, ENV_SWITCH);
        if (v == null) return null;
        v = v.trim().toLowerCase(Locale.ROOT);
        if (v.equals("1") || v.equals("on") || v.equals("true")) return "1";
        if (v.equals("0") || v.equals("off") || v.equals("false")) return "0";
        return null;
    }

    private static String envGet(String raw, String key) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            EnvVars e = new EnvVars(raw);
            return e.has(key) ? e.get(key) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ── The bundle ───────────────────────────────────────────────────────────────────────────────

    static final class Bundle {
        final String version;
        /** "x86_64/xinput1_4.dll" → sha256 */
        final Map<String, String> files;

        Bundle(String version, Map<String, String> files) {
            this.version = version;
            this.files = files;
        }

        boolean has(String arch) {
            for (String d : DLLS) if (!files.containsKey(arch + "/" + d + ".dll")) return false;
            return true;
        }
    }

    private static volatile Bundle cachedBundle;
    private static volatile boolean bundleRead;

    static Bundle bundle(Context context) {
        if (bundleRead) return cachedBundle;
        Bundle b = null;
        try {
            JSONObject m = new JSONObject(FileUtils.readString(context, ASSET_DIR + "/manifest.json"));
            JSONArray arr = m.getJSONArray("files");
            Map<String, String> files = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.getJSONObject(i);
                files.put(f.getString("asset"), f.getString("sha256").toLowerCase(Locale.ROOT));
            }
            if (!files.isEmpty()) b = new Bundle(m.optString("version", "?"), files);
        } catch (Exception e) {
            Log.w(TAG, "no bundled HERMES-XInput in this build: " + e);
        }
        cachedBundle = b;
        bundleRead = true;
        return b;
    }

    // ── Live mode (the HERMES control block) ────────────────────────────────────────────────────

    /** "Allow in-game switching": per-game extra ("1"/"0", empty = the container's), container setting. */
    public static final String EXTRA_SWITCHING = "hermesXInputSwitching";
    /** Wine's own xinput, renamed, beside our DLLs: the target of forward mode. */
    public static final String FORWARD_DLL = "hermes_wine_xinput.dll";

    /** Control block xinput_mode (HermesWriter.setSessionControl). */
    public static final int XINPUT_MODE_UNSET = 0;
    public static final int XINPUT_MODE_DIRECT = 1;
    public static final int XINPUT_MODE_FORWARD = 2;
    /** Control block xinput_flags. */
    public static final int XINPUT_FLAG_FORWARD_AVAILABLE = 1;
    public static final int XINPUT_FLAG_HIDDEN = 2;

    /** An xinput_mode for a log line. */
    public static String modeName(int mode) {
        switch (mode) {
            case XINPUT_MODE_UNSET: return "unset (direct)";
            case XINPUT_MODE_DIRECT: return "direct";
            case XINPUT_MODE_FORWARD: return "forward (Wine's XInput)";
            default: return "mode " + mode;
        }
    }

    /** "Allow in-game switching" for this launch: game extra > container. Default on. */
    public static boolean switchingEffective(Container container, Shortcut shortcut) {
        if (shortcut != null) {
            String extra = shortcut.getExtra(EXTRA_SWITCHING);
            if ("1".equals(extra)) return true;
            if ("0".equals(extra)) return false;
        }
        return container != null && container.isHermesXInputSwitching();
    }

    /** The control-block values a launch writes (xinputMode, xinputFlags of setSessionControl). */
    public static final class LaunchControl {
        public final int xinputMode;
        public final int xinputFlags;

        LaunchControl(int xinputMode, int xinputFlags) {
            this.xinputMode = xinputMode;
            this.xinputFlags = xinputFlags;
        }
    }

    /**
     * What to write into the control block at env prep for the result of {@link #syncForLaunch}:
     * without our DLLs in place, nothing (UNSET, 0); otherwise FORWARD_AVAILABLE when Wine's xinput sits
     * beside them, XI_HIDDEN when the controller should be invisible to XInput (DirectInput only), and the
     * mode: direct when Direct XInput is on (or nothing else is possible), forward when it is off.
     */
    public static LaunchControl launchControlMode(Result sync, boolean xinputHidden) {
        if (sync == null || !sync.installed) return new LaunchControl(XINPUT_MODE_UNSET, 0);
        int flags = (sync.forwardAvailable ? XINPUT_FLAG_FORWARD_AVAILABLE : 0) | (xinputHidden ? XINPUT_FLAG_HIDDEN : 0);
        int mode = sync.directOn || !sync.forwardAvailable ? XINPUT_MODE_DIRECT : XINPUT_MODE_FORWARD;
        return new LaunchControl(mode, flags);
    }

    /** The xinput_mode for a live flip of Direct XInput (only meaningful when {@code sync.forwardAvailable}). */
    public static int liveMode(boolean directOn) {
        return directOn ? XINPUT_MODE_DIRECT : XINPUT_MODE_FORWARD;
    }

    /** The sha256 of every DLL in this build's bundle (to tell our own files from a game's copy). */
    public static Set<String> bundledShas(Context context) {
        Bundle b = bundle(context);
        return b != null ? new java.util.HashSet<>(b.files.values()) : java.util.Collections.emptySet();
    }

    // ── The prefix ───────────────────────────────────────────────────────────────────────────────

    /** What {@link #sync} / {@link #syncForLaunch} did, for the session log and the control block. */
    public static final class Result {
        /** Our DLLs are wanted for this launch (Direct XInput on, or in-game switching possible). */
        public final boolean on;
        /** Our DLLs are in place, so the override may be set. */
        public final boolean installed;
        /** One line for the session log, or null when there is nothing to say (off, never on). */
        public final String detail;
        /** Direct XInput (the setting) for this launch. */
        public final boolean directOn;
        /** "Allow in-game switching" for this launch. */
        public final boolean switching;
        /** Wine's own xinput is in place as {@value #FORWARD_DLL}: forward mode is possible. */
        public final boolean forwardAvailable;

        Result(boolean on, boolean installed, String detail) {
            this(on, installed, detail, on, false, false);
        }

        Result(boolean on, boolean installed, String detail, boolean directOn, boolean switching, boolean forwardAvailable) {
            this.on = on;
            this.installed = installed;
            this.detail = detail;
            this.directOn = directOn;
            this.switching = switching;
            this.forwardAvailable = forwardAvailable;
        }
    }

    /**
     * Bring the prefix at {@code prefixDir} (the .wine folder) in line with the setting. Safe on every
     * launch and after an interrupted one. Never throws. (Without in-game switching; see
     * {@link #syncForLaunch}.)
     *
     * @param arm64ec the container's Wine is arm64ec (system32 then gets the ARM64X build)
     * @param arch    {@value #ENV_ARCH} ("x86_64" forces the x64 build into an arm64ec prefix), or null
     */
    public static Result sync(Context context, File prefixDir, boolean on, boolean arm64ec, String arch) {
        return syncImpl(context, prefixDir, on, on, false, arm64ec, arch, null);
    }

    /**
     * The launch entry point: Direct XInput and "Allow in-game switching" resolved for this game.
     * With switching on, our DLLs go in whether Direct XInput is on or off, and Wine's own xinput1_4
     * from the container's Wine layer ({@code wineRoot}: the layer folder holding lib/wine) is put beside
     * them as {@value #FORWARD_DLL} (system32: x86_64-windows, or aarch64-windows when it is an ARM64X
     * image, for arm64ec layers; syswow64: i386-windows), so the mode can change mid-game. If Wine's xinput
     * cannot be used, switching is off for this launch (and with Direct XInput off, nothing of ours is
     * installed). Then call {@link #launchControlMode} and hand its values to
     * {@code HermesWriter.setSessionControl}.
     */
    public static Result syncForLaunch(Context context, File prefixDir, Container container, Shortcut shortcut,
                                       boolean arm64ec, File wineRoot) {
        boolean direct = effective(container, shortcut);
        boolean switching = switchingEffective(container, shortcut);
        String arch = archOverride(container, shortcut);
        Forward fwd = null;
        String why = null;
        if (switching) {
            fwd = forwardSources(wineRoot, arm64ec);
            if (fwd.sys32 == null) {
                why = fwd.problem;
                fwd = null;
            }
        }
        Result r = syncImpl(context, prefixDir, direct || fwd != null, direct, switching, arm64ec, arch, fwd);
        if (why == null) return r;
        String detail = (r.detail != null ? r.detail + "; " : "") + "in-game switching unavailable: " + why;
        return new Result(r.on, r.installed, detail, direct, false, false);
    }

    private static Result syncImpl(Context context, File prefixDir, boolean on, boolean direct, boolean switching,
                                   boolean arm64ec, String arch, Forward fwd) {
        File windows = new File(prefixDir, "drive_c/windows");
        File state = new File(prefixDir, STATE_DIR);
        File marker = new File(state, MARKER);
        try {
            return on ? install(context, windows, state, marker, arm64ec, arch, direct, switching, fwd)
                      : restore(windows, state, marker);
        } catch (Throwable t) {
            Log.w(TAG, "sync failed", t);
            return new Result(on, false, (on ? "could not install HERMES-XInput: " : "could not restore the prefix's xinput dlls: ") + t,
                    direct, false, false);
        }
    }

    /** Wine's builtin xinput1_4 for each side of the prefix, or why it can't be the forward target. */
    static final class Forward {
        File sys32, wow64;
        String problem;
    }

    static Forward forwardSources(File wineRoot, boolean arm64ec) {
        Forward f = new Forward();
        if (wineRoot == null) {
            f.problem = "the Wine layer folder is unknown";
            return f;
        }
        File lib = new File(wineRoot, "lib/wine");
        if (!lib.isDirectory()) lib = new File(wineRoot, "lib64/wine");
        if (!lib.isDirectory()) lib = wineRoot;
        File sys = new File(lib, (arm64ec ? "aarch64-windows" : "x86_64-windows") + "/xinput1_4.dll");
        if (!sys.isFile()) {
            f.problem = "no " + sys.getParentFile().getName() + "/xinput1_4.dll in " + lib;
            return f;
        }
        try {
            int machine = peMachine(sys);
            if (arm64ec) {
                // Native ARM64 processes load its ARM64 view, x64 games its ARM64EC view: it must be ARM64X.
                if (machine != 0xaa64 || !hasChpe(sys)) {
                    f.problem = sys + " is not an ARM64X image (machine 0x" + Integer.toHexString(machine) + ")";
                    return f;
                }
            } else if (machine != 0x8664) {
                f.problem = sys + " is not an x86_64 image (machine 0x" + Integer.toHexString(machine) + ")";
                return f;
            }
        } catch (Exception e) {
            f.problem = "cannot read " + sys + ": " + e;
            return f;
        }
        f.sys32 = sys;
        File wow = new File(lib, "i386-windows/xinput1_4.dll");
        try {
            if (wow.isFile() && peMachine(wow) == 0x14c) f.wow64 = wow;
        } catch (Exception ignored) {
        }
        return f;
    }

    /** One file this sync puts at {@code slot} (relative to drive_c/windows): an APK asset or a file. */
    private static final class Item {
        final String slot, asset, sha;
        final File file;

        Item(String slot, String asset, File file, String sha) {
            this.slot = slot;
            this.asset = asset;
            this.file = file;
            this.sha = sha;
        }
    }

    private static Result install(Context context, File windows, File state, File marker, boolean arm64ec, String archOverride,
                                  boolean direct, boolean switching, Forward fwd) throws Exception {
        Bundle b = bundle(context);
        if (b == null)
            return new Result(true, false, "not in this build (no bundled HERMES-XInput), nothing installed", direct, false, false);
        // x86_64 prefix: x86_64. arm64ec prefix: ARM64X (see the class comment), unless forced to x86_64.
        boolean forceX64 = "x86_64".equalsIgnoreCase(archOverride != null ? archOverride.trim() : "");
        String sys64 = arm64ec && !forceX64 ? "arm64x" : "x86_64";
        if (!b.has(sys64))
            return new Result(true, false, "the bundle has no " + sys64 + " build, nothing installed", direct, false, false);
        List<Item> items = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String[][] dirs = {{"system32", sys64}, {"syswow64", "i386"}};
        for (String[] dir : dirs) {
            if (!new File(windows, dir[0]).isDirectory()) {
                // A prefix without the folder (no syswow64 = no 32-bit side) doesn't get one made for us.
                skipped.add(dir[0] + " (not in this prefix)");
                continue;
            }
            if (!b.has(dir[1])) {
                skipped.add(dir[0] + " (no " + dir[1] + " build bundled)");
                continue;
            }
            for (String dll : DLLS) {
                String asset = dir[1] + "/" + dll + ".dll";
                items.add(new Item(dir[0] + "/" + dll + ".dll", asset, null, b.files.get(asset)));
            }
            File src = fwd == null ? null : "system32".equals(dir[0]) ? fwd.sys32 : fwd.wow64;
            if (src != null) items.add(new Item(dir[0] + "/" + FORWARD_DLL, null, src, sha256(src)));
        }
        if (items.isEmpty())
            return new Result(true, false, "nothing installed: " + String.join(", ", skipped), direct, false, false);
        Properties m = load(marker);
        List<String> did = new ArrayList<>();
        boolean forwardPlaced = false;
        Set<String> wanted = new LinkedHashSet<>();
        for (Item it : items) {
            wanted.add(it.slot);
            File target = new File(windows, it.slot);
            File folder = target.getParentFile();
            File backup = new File(state, "backup/" + it.slot);
            String have = target.isFile() ? sha256(target) : null;
            boolean ours = have != null && isOurs(have, it.slot, m);
            // An original we haven't kept yet: keep it before anything overwrites it (once).
            if (have != null && !ours && !backup.isFile()) {
                copyAtomically(new FileInputStream(target), backup);
                m.setProperty(it.slot, "backup");
                store(m, marker);
            } else if (m.getProperty(it.slot) == null) {
                m.setProperty(it.slot, backup.isFile() ? "backup" : "absent");
                store(m, marker);
            }
            if (it.file != null && it.slot.startsWith("system32/")) forwardPlaced = true;
            if (it.sha.equals(have)) continue;
            File tmp = new File(folder, "." + target.getName() + ".winhub-tmp");
            String sha;
            try (InputStream in = it.asset != null ? context.getAssets().open(ASSET_DIR + "/" + it.asset) : new FileInputStream(it.file)) {
                sha = copyTo(in, tmp);
            }
            if (!it.sha.equals(sha)) {
                tmp.delete();
                throw new IllegalStateException((it.asset != null ? it.asset + " in the APK" : it.file.toString())
                        + " does not match its expected hash");
            }
            addSha(m, it.slot, sha);
            store(m, marker);
            if (!tmp.renameTo(target)) {
                tmp.delete();
                throw new java.io.IOException("could not put " + it.slot + " in place");
            }
            did.add(it.slot);
        }
        // Slots we installed before but don't want now (Wine's xinput after switching was turned off):
        // the prefix's own file back, or none.
        List<String> removed = new ArrayList<>();
        for (String slot : slotPaths(m)) {
            if (wanted.contains(slot)) continue;
            restoreSlot(windows, state, marker, m, slot);
            removed.add(slot);
        }
        m.setProperty("version", b.version);
        m.setProperty("system32", sys64);
        store(m, marker);
        String detail = "HERMES-XInput " + b.version + " (system32 " + sys64 + "): "
                + (did.isEmpty() ? "in place" : "installed " + did.size() + " file(s)")
                + (forwardPlaced ? "; Wine's xinput beside it as " + FORWARD_DLL + " (in-game switching, launch mode "
                        + (direct ? "direct" : "forward") + ")" : "")
                + (removed.isEmpty() ? "" : "; removed " + String.join(", ", removed))
                + (skipped.isEmpty() ? "" : "; skipped " + String.join(", ", skipped));
        Log.i(TAG, detail);
        return new Result(true, true, detail, direct, switching && forwardPlaced, forwardPlaced);
    }

    /** One slot back to the prefix's own file (or none), unless something else replaced ours since. */
    private static boolean restoreSlot(File windows, File state, File marker, Properties m, String slot) throws Exception {
        File target = new File(windows, slot);
        File backup = new File(state, "backup/" + slot);
        boolean back;
        if (target.isFile() && !isOurs(sha256(target), slot, m)) {
            // Not ours: the original is already back (an interrupted restore) or something replaced
            // our dll since (a Wine update). Leave it; the backup is older than what is there.
            if (backup.isFile()) backup.delete();
            back = false;
        } else {
            if (target.isFile() && !target.delete()) throw new java.io.IOException("could not remove " + slot);
            if (backup.isFile()) {
                if (!backup.renameTo(target)) {
                    copyAtomically(new FileInputStream(backup), target);
                    backup.delete();
                }
            }
            back = true;
        }
        m.remove(slot);
        m.remove(slot + ".sha256");
        store(m, marker);
        return back;
    }

    private static Result restore(File windows, File state, File marker) throws Exception {
        if (!marker.isFile()) return new Result(false, false, null); // never installed here
        Properties m = load(marker);
        List<String> back = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (String slot : slotPaths(m)) {
            if (restoreSlot(windows, state, marker, m, slot)) back.add(slot);
            else kept.add(slot);
        }
        marker.delete();
        deleteEmptyDirs(new File(state, "backup"));
        state.delete(); // only if empty
        String detail = "HERMES-XInput removed: " + back.size() + " file(s) back to the prefix's own (Wine's builtin xinput)"
                + (kept.isEmpty() ? "" : "; left as is (not ours): " + String.join(", ", kept));
        Log.i(TAG, detail);
        return new Result(false, false, detail);
    }

    private static boolean isOurs(String sha, String slot, Properties m) {
        if (sha == null) return false;
        for (String h : m.getProperty(slot + ".sha256", "").split(",")) if (h.equals(sha)) return true;
        return false;
    }

    private static void addSha(Properties m, String slot, String sha) {
        String key = slot + ".sha256";
        String rec = m.getProperty(key, "");
        for (String h : rec.split(",")) if (h.equals(sha)) return;
        m.setProperty(key, rec.isEmpty() ? sha : rec + "," + sha);
    }

    private static List<String> slotPaths(Properties m) {
        List<String> r = new ArrayList<>();
        for (String k : m.stringPropertyNames()) if (k.endsWith(".dll")) r.add(k);
        return r;
    }

    // ── PE headers (is Wine's xinput an ARM64X image?) ───────────────────────────────────────────

    private static byte[] head(File f, int max) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), max)];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            return b;
        }
    }

    /** The PE header's machine (0x8664 x86_64, 0x14c i386, 0xaa64 ARM64 incl. ARM64X), or -1. */
    static int peMachine(File f) throws Exception {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(head(f, 4096)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if (b.limit() < 0x40) return -1;
        int pe = b.getInt(0x3c);
        if (pe < 0 || pe + 6 > b.limit() || b.getInt(pe) != 0x00004550) return -1;
        return b.getShort(pe + 4) & 0xffff;
    }

    /** True if the image's load config carries a CHPE metadata pointer (ARM64EC code: with machine
     *  0xaa64 that makes it an ARM64X image, loadable by native ARM64 and by x64 processes). */
    static boolean hasChpe(File f) throws Exception {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(head(f, 16 << 20)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int pe = b.getInt(0x3c);
        int nsec = b.getShort(pe + 6) & 0xffff;
        int optSize = b.getShort(pe + 20) & 0xffff;
        int opt = pe + 24;
        if ((b.getShort(opt) & 0xffff) != 0x20b) return false; // PE32+
        int lcRva = b.getInt(opt + 112 + 10 * 8);
        if (lcRva == 0) return false;
        int sec = opt + optSize;
        for (int i = 0; i < nsec; i++) {
            int h = sec + i * 40;
            int va = b.getInt(h + 12), vsize = b.getInt(h + 8), raw = b.getInt(h + 20), rawSize = b.getInt(h + 16);
            if (lcRva >= va && lcRva < va + Math.max(vsize, rawSize)) {
                int off = raw + (lcRva - va);
                if (off + 0xd0 > b.limit()) return false;
                int size = b.getInt(off);
                return size >= 0xd0 && b.getLong(off + 0xc8) != 0;
            }
        }
        return false;
    }

    // ── Environment ──────────────────────────────────────────────────────────────────────────────

    /**
     * {@code existing} (a WINEDLLOVERRIDES value, may be null/empty) with "xinput1_1,...=n,b" added for
     * the dlls it doesn't already name: an override the user set for one stays theirs (reported via
     * {@code keptOut}). Grouped entries ("a,b=n") are understood.
     */
    public static String withDllOverrides(String existing, List<String> keptOut) {
        Set<String> named = new LinkedHashSet<>();
        String e = existing == null ? "" : existing.trim();
        for (String entry : e.split(";")) {
            int eq = entry.indexOf('=');
            String dlls = eq >= 0 ? entry.substring(0, eq) : entry;
            for (String d : dlls.split(",")) {
                String n = d.trim().toLowerCase(Locale.ROOT);
                if (n.endsWith(".dll")) n = n.substring(0, n.length() - 4);
                if (!n.isEmpty()) named.add(n);
            }
        }
        List<String> add = new ArrayList<>();
        for (String d : DLLS) {
            if (named.contains(d)) { if (keptOut != null) keptOut.add(d); }
            else add.add(d);
        }
        if (add.isEmpty()) return e;
        String ours = String.join(",", add) + "=" + OVERRIDE_MODE;
        if (e.isEmpty()) return ours;
        return e.endsWith(";") ? e + ours : e + ";" + ours;
    }

    // ── Files ────────────────────────────────────────────────────────────────────────────────────

    private static Properties load(File marker) {
        Properties p = new Properties();
        if (marker.isFile()) {
            try (InputStream in = new FileInputStream(marker)) { p.load(in); } catch (Exception ignored) {}
        }
        return p;
    }

    private static void store(Properties p, File marker) throws Exception {
        File dir = marker.getParentFile();
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        File tmp = new File(dir, marker.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, "WinHub: HERMES-XInput installed into this prefix (see core/HermesXInput)");
            out.getFD().sync();
        }
        if (!tmp.renameTo(marker)) throw new java.io.IOException("could not write " + marker);
    }

    private static String copyAtomically(InputStream in, File dst) throws Exception {
        File dir = dst.getParentFile();
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        File tmp = new File(dir, "." + dst.getName() + ".winhub-tmp");
        String sha;
        try (InputStream src = in) { sha = copyTo(src, tmp); }
        if (!tmp.renameTo(dst)) {
            tmp.delete();
            throw new java.io.IOException("could not write " + dst);
        }
        return sha;
    }

    private static String copyTo(InputStream in, File dst) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
            }
            out.getFD().sync();
        }
        return hex(md.digest());
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte x : d) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    private static void deleteEmptyDirs(File dir) {
        File[] kids = dir.listFiles();
        if (kids != null) for (File k : kids) if (k.isDirectory()) deleteEmptyDirs(k);
        dir.delete(); // only succeeds when empty
    }
}
