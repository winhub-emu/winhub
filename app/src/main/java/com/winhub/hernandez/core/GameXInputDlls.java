package com.winhub.hernandez.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * XInput DLLs a game ships next to its EXE, against Direct XInput (core.HermesXInput).
 *
 * <p>Wine looks in the game's folder before system32, so with our {@code xinput*=n,b} override a game's
 * own copy (often Microsoft's DirectX xinput1_3.dll, which needs the Windows XUSB driver Wine doesn't
 * have) loads instead of ours, and the game sees no pads. Wine can't prefer a system32 native over an
 * app-folder native, so per name:
 * <ul>
 *   <li>a name the user's WINEDLLOVERRIDES already sets (e.g. "Prefer game-folder DLLs",
 *       {@code xinput1_3=n,b}) stays the user's: the game's copy loads if that says native first;</li>
 *   <li>a name the game ships gets {@code =b}, Wine's builtin xinput, exactly as before Direct XInput;</li>
 *   <li>every other name gets our {@code n,b}.</li>
 * </ul>
 * With the per-game option {@value #EXTRA_OVER_GAME} on, the game's copy is renamed to
 * {@code <file>}{@value #DISABLED_SUFFIX} for the session instead (so ours loads), recorded in a manifest
 * first, and renamed back at session end, at the next app start, or before the next launch. Nothing is
 * ever deleted or overwritten. Pure Java (no Android calls), so it is unit-tested; callers log the lines
 * it returns.
 */
public final class GameXInputDlls {
    private GameXInputDlls() {}

    /** Per-game extra: "1" = move the game's own xinput DLLs aside for the session so Direct XInput is used. */
    public static final String EXTRA_OVER_GAME = "hermesXInputOverGameDll";
    public static final String DISABLED_SUFFIX = ".hermes-disabled";
    /** In the app's files dir: original path = moved-aside path, for every file still moved aside. */
    public static final String MANIFEST_NAME = "hermes-xinput-moved.properties";

    private static final String OURS = "n,b";
    private static final String BUILTIN = "b";
    private static final Object LOCK = new Object();
    /** A session in this process moved a file aside (the app-start restore must not undo it). */
    private static boolean movedThisProcess;

    // ── Which names the game ships ───────────────────────────────────────────────────────────────

    /**
     * The game's own copies of our DLL names in {@code dirs} (first match per name, any letter case),
     * keyed by the lowercase name without ".dll". A file whose sha256 is in {@code ourShas} (one of our
     * own DLLs copied there) doesn't count. Unreadable folders are skipped.
     */
    public static Map<String, File> findShipped(Collection<File> dirs, Set<String> ourShas) {
        Map<String, File> found = new LinkedHashMap<>();
        for (File dir : dirs) {
            File[] files = dir != null ? dir.listFiles() : null;
            if (files == null) continue;
            for (String dll : HermesXInput.DLLS) {
                if (found.containsKey(dll)) continue;
                for (File f : files) {
                    if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).equals(dll + ".dll")) continue;
                    String sha = null;
                    try { sha = sha256(f); } catch (Exception ignored) {}
                    if (sha != null && ourShas != null && ourShas.contains(sha)) continue;
                    found.put(dll, f);
                    break;
                }
            }
        }
        return found;
    }

    // ── WINEDLLOVERRIDES ─────────────────────────────────────────────────────────────────────────

    /** The dll names a WINEDLLOVERRIDES value sets (lowercase, no ".dll") and the order it gives each. */
    public static Map<String, String> named(String overrides) {
        Map<String, String> named = new LinkedHashMap<>();
        String e = overrides == null ? "" : overrides.trim();
        for (String entry : e.split(";")) {
            int eq = entry.indexOf('=');
            String dlls = eq >= 0 ? entry.substring(0, eq) : entry;
            String order = eq >= 0 ? entry.substring(eq + 1).trim() : "";
            for (String d : dlls.split(",")) {
                String n = d.trim().toLowerCase(Locale.ROOT);
                if (n.endsWith(".dll")) n = n.substring(0, n.length() - 4);
                if (!n.isEmpty() && !named.containsKey(n)) named.put(n, order);
            }
        }
        return named;
    }

    /** True when a WINEDLLOVERRIDES order loads a native (the game's) copy first. */
    public static boolean nativeFirst(String order) {
        String first = order == null ? "" : order.split(",")[0].trim().toLowerCase(Locale.ROOT);
        return first.equals("n") || first.equals("native");
    }

    /** What {@link #overrides} wrote, per name. */
    public static final class Overrides {
        public final String value;
        /** Names the user's own entry sets (kept as they are). */
        public final List<String> kept = new ArrayList<>();
        /** Names that get our n,b. */
        public final List<String> ours = new ArrayList<>();
        /** Names that get Wine's builtin (=b) because the game ships its own copy. */
        public final List<String> builtin = new ArrayList<>();

        Overrides(String value) { this.value = value; }
    }

    /**
     * {@code existing} (a WINEDLLOVERRIDES value, may be null/empty) with an entry for each of our DLL
     * names it doesn't set: {@code =b} for the names in {@code gameShipped}, our {@code n,b} for the rest.
     */
    public static Overrides overrides(String existing, Set<String> gameShipped) {
        String e = existing == null ? "" : existing.trim();
        Map<String, String> named = named(e);
        List<String> kept = new ArrayList<>(), ours = new ArrayList<>(), builtin = new ArrayList<>();
        for (String d : HermesXInput.DLLS) {
            if (named.containsKey(d)) kept.add(d);
            else if (gameShipped != null && gameShipped.contains(d)) builtin.add(d);
            else ours.add(d);
        }
        StringBuilder v = new StringBuilder(e);
        if (!ours.isEmpty()) append(v, String.join(",", ours) + "=" + OURS);
        if (!builtin.isEmpty()) append(v, String.join(",", builtin) + "=" + BUILTIN);
        Overrides o = new Overrides(v.toString());
        o.kept.addAll(kept);
        o.ours.addAll(ours);
        o.builtin.addAll(builtin);
        return o;
    }

    private static void append(StringBuilder v, String entry) {
        if (v.length() > 0 && v.charAt(v.length() - 1) != ';') v.append(';');
        v.append(entry);
    }

    // ── Moving the game's copy aside (the per-game option) ───────────────────────────────────────

    /**
     * Renames {@code file} to {@code <file>}{@value #DISABLED_SUFFIX}, recorded in {@code manifest} before
     * the rename. False (and the file stays) when that name is already taken or the rename fails.
     */
    public static boolean moveAside(File manifest, File file, List<String> log) {
        synchronized (LOCK) {
            File aside = new File(file.getParentFile(), file.getName() + DISABLED_SUFFIX);
            if (aside.exists()) {
                log.add("left " + file + " in place: " + aside.getName() + " already exists (never overwritten)");
                return false;
            }
            Properties m = load(manifest);
            m.setProperty(file.getAbsolutePath(), aside.getAbsolutePath());
            try {
                store(m, manifest);
            } catch (Exception ex) {
                log.add("left " + file + " in place: could not record it (" + ex + ")");
                return false;
            }
            if (!file.renameTo(aside)) {
                m.remove(file.getAbsolutePath());
                try { store(m, manifest); } catch (Exception ignored) {}
                log.add("left " + file + " in place: could not rename it");
                return false;
            }
            movedThisProcess = true;
            log.add("moved " + file + " aside as " + aside.getName() + " for this session");
            return true;
        }
    }

    /**
     * {@link #restoreAll} for the app's start: a no-op once a session in this process has moved a file
     * aside (a launch restores leftovers itself before deciding its own).
     */
    public static List<String> restoreAtAppStart(File manifest) {
        synchronized (LOCK) {
            if (movedThisProcess) return new ArrayList<>();
            return restoreAll(manifest);
        }
    }

    /**
     * Every file the manifest says is moved aside, back under its own name. A file whose own name is
     * taken again (the game was updated or repaired) keeps its moved-aside copy, which is then no longer
     * tracked. Returns one line per file; empty when nothing was moved aside.
     */
    public static List<String> restoreAll(File manifest) {
        List<String> log = new ArrayList<>();
        synchronized (LOCK) {
            if (!manifest.isFile()) return log;
            Properties m = load(manifest);
            for (String original : new ArrayList<>(m.stringPropertyNames())) {
                File orig = new File(original);
                File aside = new File(m.getProperty(original));
                if (!aside.exists()) {
                    log.add(orig + ": nothing to put back (" + aside.getName() + " is gone)");
                } else if (orig.exists()) {
                    log.add(orig + " exists again; " + aside.getName() + " left beside it, untouched");
                } else if (aside.renameTo(orig)) {
                    log.add("put back " + orig);
                } else {
                    log.add("could not put back " + orig + " (still " + aside.getName() + "); retried next time");
                    continue;
                }
                m.remove(original);
            }
            try {
                if (m.isEmpty()) manifest.delete();
                else store(m, manifest);
            } catch (Exception ex) {
                log.add("could not update " + manifest + ": " + ex);
            }
        }
        return log;
    }

    // ── Files ────────────────────────────────────────────────────────────────────────────────────

    private static Properties load(File f) {
        Properties p = new Properties();
        if (f.isFile()) {
            try (InputStream in = new FileInputStream(f)) { p.load(in); } catch (Exception ignored) {}
        }
        return p;
    }

    private static void store(Properties p, File f) throws Exception {
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
        File tmp = new File(dir, f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, "WinHub: game xinput DLLs moved aside for Direct XInput (core/GameXInputDlls)");
            out.getFD().sync();
        }
        if (!tmp.renameTo(f)) throw new java.io.IOException("could not write " + f);
    }

    static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }
}
