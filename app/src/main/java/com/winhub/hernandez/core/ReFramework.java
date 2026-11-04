package com.winhub.hernandez.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * REFramework (praydog, MIT, github.com/praydog/REFramework) for RE Engine games: which EXEs it supports and
 * its config. The DLL itself is downloaded from The412Banner/REFramework release arm64ec-r2 and placed by
 * core.GameMods (game settings > Mods). Our build skips x86-64 inline hooks on ARM64EC code
 * (praydog/REFramework#1887), opens the menu with L3 + R3 on a controller, sees the menu key in games whose
 * DirectInput keyboard blocks key messages under Wine (RE2), gives each inline hook its own trampoline
 * memory under ARM64 emulation (startup otherwise hung about half the time), and keeps the game's cursor
 * hidden while it draws its own (one cursor instead of two with "Draw Cursor With Menu Open").
 *
 * <p>When it is on, its config is set so the menu starts closed (the in-game drawer's Mods tab opens it).
 * Pure Java (no Android calls), so it is unit-tested.
 */
public final class ReFramework {
    private ReFramework() {}

    /** Per-game extra: "1" = REFramework on for this game. */
    public static final String EXTRA = "reframework";
    public static final String DLL = "dinput8.dll";
    /** In the game folder: the sha256 of the dinput8.dll we placed (core.GameMods). */
    public static final String MARKER = ".bannerlator-reframework";
    /** REFramework's config file name, the same for every game (REFrameworkConfig.hpp). */
    public static final String CONFIG = "re2_fw_config.txt";
    static final String KEY_REMEMBER = "REFrameworkConfig_RememberMenuState";
    static final String KEY_MENU_OPEN = "REFrameworkConfig_MenuOpen";

    /** EXE names (lowercase, no ".exe") from REFramework's own table, shared/sdk/GameIdentity.cpp. */
    private static final Set<String> STEMS = new HashSet<>(Arrays.asList(
            "re2", "re3", "re4", "re7", "re8", "re9",
            "devilmaycry5", "dmc5",
            "monsterhunterrise", "mhrise", "mhrisesunbreakdemo",
            "streetfighter6", "sf6",
            "dd2",
            "makaimura_gg_re", "makaimura",
            "onimusha2",
            "monsterhunterwilds", "mhwilds",
            "monster_hunter_stories_3_twisted_reflection",
            "monster_hunter_stories_3_twisted_reflection_trial",
            "pragmata",
            "onimushawots", "onimushawots_demo"));

    /** The EXE name of a Windows or Unix path, lowercase, without ".exe" ("" for none). */
    public static String stem(String path) {
        if (path == null) return "";
        String p = path.trim();
        int cut = Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/'));
        String name = (cut >= 0 ? p.substring(cut + 1) : p).toLowerCase(Locale.ROOT);
        return name.endsWith(".exe") ? name.substring(0, name.length() - 4) : "";
    }

    public static boolean isSupportedExe(String path) {
        return STEMS.contains(stem(path));
    }

    // ── REFramework config: menu starts closed ───────────────────────────────────────────────────

    /**
     * {@code text} (REFramework's key=value config, may be empty) with RememberMenuState=true and
     * MenuOpen=false, so the menu starts closed; other lines and the line ending are kept.
     */
    public static String menuStartsClosed(String text) {
        String t = text == null ? "" : text;
        String nl = t.contains("\r\n") ? "\r\n" : (t.isEmpty() ? "\r\n" : "\n");
        StringBuilder out = new StringBuilder();
        boolean remember = false, open = false;
        for (String line : t.split("\r?\n", -1)) {
            if (line.isEmpty()) continue;
            String key = line.contains("=") ? line.substring(0, line.indexOf('=')).trim() : line.trim();
            if (key.equals(KEY_REMEMBER)) { line = KEY_REMEMBER + "=true"; remember = true; }
            else if (key.equals(KEY_MENU_OPEN)) { line = KEY_MENU_OPEN + "=false"; open = true; }
            out.append(line).append(nl);
        }
        if (!remember) out.append(KEY_REMEMBER).append("=true").append(nl);
        if (!open) out.append(KEY_MENU_OPEN).append("=false").append(nl);
        return out.toString();
    }

    /** Writes {@link #menuStartsClosed} into {@code dir}/{@value #CONFIG}; true when the file changed. */
    public static boolean applyMenuStartsClosed(File dir) throws IOException {
        File f = new File(dir, CONFIG);
        String before = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        String after = menuStartsClosed(before);
        if (after.equals(before)) return false;
        try (OutputStream os = new FileOutputStream(f)) { os.write(after.getBytes(StandardCharsets.UTF_8)); }
        return true;
    }

    /** Copies {@code from} over {@code to} through a temporary file next to it. */
    static void copy(File from, File to) throws IOException {
        File tmp = new File(to.getParentFile(), to.getName() + ".bannerlator-tmp");
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        if (to.exists() && !to.delete()) { tmp.delete(); throw new IOException("cannot replace " + to); }
        if (!tmp.renameTo(to)) { tmp.delete(); throw new IOException("cannot rename to " + to); }
    }
}
