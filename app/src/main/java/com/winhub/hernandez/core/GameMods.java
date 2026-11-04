package com.winhub.hernandez.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-game mods (game settings > Mods, in-game drawer > Mods): DLLs WinHub downloads from
 * GitHub releases (core.GameModCache) and places next to the game EXE at launch.
 *
 * <ul>
 * <li>REFramework (praydog, MIT): The412Banner/REFramework release arm64ec-r2, as dinput8.dll, only for the
 * RE Engine EXEs REFramework knows (core.ReFramework), 64-bit.</li>
 * <li>Special K (Kaldaien / Andon M. Coleman and contributors, SpecialKO, GPL-3.0): The412Banner/SpecialK
 * release arm64ec-r2, SpecialK64.dll or SpecialK32.dll by the EXE's machine type, as dxgi.dll / d3d11.dll /
 * d3d9.dll / d3d8.dll / opengl32.dll (picked from the EXE's imports, or chosen per game).</li>
 * </ul>
 *
 * Per game, off by default ({@link Mod#extra} = "1" turns a mod on). A marker file per mod records the
 * sha256 (and the DLL name) of the file we placed, so only our file is ever replaced or removed; a game
 * folder's own DLL of the same name is never touched. Pure Java (no Android calls), so it is unit-tested.
 */
public final class GameMods {
    private GameMods() {}

    public static final int MACHINE_X64 = 0x8664;
    public static final int MACHINE_X86 = 0x14c;

    /** One downloadable build: release URL, sha256 and size. */
    public static final class Asset {
        public final String url;
        public final String sha256;
        public final long size;

        Asset(String url, String sha256, long size) {
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
        }

        /** The release tag in {@link #url} (".../releases/download/<tag>/file"), or "" when it has none. */
        public String tag() {
            String key = "/releases/download/";
            int i = url.indexOf(key);
            if (i < 0) return "";
            int start = i + key.length();
            int end = url.indexOf('/', start);
            return end > start ? url.substring(start, end) : "";
        }
    }

    public static final class Mod {
        public final String id;
        public final String name;
        /** Shortcut extra: "1" = on for this game. */
        public final String extra;
        /** Marker file in the game folder. */
        public final String marker;
        final Asset x64;
        final Asset x86;
        /** The DLL name in the game folder, or null when it is picked per game. */
        final String fixedDll;

        Mod(String id, String name, String extra, String marker, Asset x64, Asset x86, String fixedDll) {
            this.id = id;
            this.name = name;
            this.extra = extra;
            this.marker = marker;
            this.x64 = x64;
            this.x86 = x86;
            this.fixedDll = fixedDll;
        }
    }

    public static final Mod REFRAMEWORK = new Mod(
            "reframework", "REFramework", ReFramework.EXTRA, ReFramework.MARKER,
            new Asset("https://github.com/The412Banner/REFramework/releases/download/arm64ec-r2/dinput8.dll",
                    "636e819dd0a0dd7664a26f1c1a2b4ca268222155e92cd79679b84d36f3f18930", 23091712L),
            null, ReFramework.DLL);

    public static final Mod SPECIAL_K = new Mod(
            "specialk", "Special K", "specialk", ".bannerlator-specialk",
            new Asset("https://github.com/The412Banner/SpecialK/releases/download/arm64ec-r2/SpecialK64.dll",
                    "831e4f190fb37988335413b5079d8c43dffe02bb298f1cf1cd91b252e41d50b8", 15884800L),
            new Asset("https://github.com/The412Banner/SpecialK/releases/download/arm64ec-r2/SpecialK32.dll",
                    "05dcaf51f2b4de1645569c495849946e7c9483826af507510ddfc9dcbf40420d", 13351424L),
            null);

    public static final List<Mod> ALL = Collections.unmodifiableList(Arrays.asList(REFRAMEWORK, SPECIAL_K));

    /** Shortcut extra: Special K's DLL name for this game ("" or absent = picked from the EXE). */
    public static final String EXTRA_SPECIALK_DLL = "specialkDll";
    /** The names Special K can be loaded as. */
    public static final List<String> SPECIALK_DLLS = Collections.unmodifiableList(
            Arrays.asList("dxgi.dll", "d3d11.dll", "d3d9.dll", "d3d8.dll", "opengl32.dll"));

    public static Mod byId(String id) {
        for (Mod m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    /** The build of {@code mod} for an EXE of this machine type, or null when there is none. */
    public static Asset asset(Mod mod, int machine) {
        if (machine == MACHINE_X64) return mod.x64;
        if (machine == MACHINE_X86) return mod.x86;
        return null;
    }

    /** {@code mod} can be offered for this EXE (path and PE machine type). */
    public static boolean supports(Mod mod, String exePath, int machine) {
        if (asset(mod, machine) == null) return false;
        return mod != REFRAMEWORK || ReFramework.isSupportedExe(exePath);
    }

    /** The DLL name {@code mod} goes in as: fixed, the per-game {@code choice}, or picked from the EXE. */
    public static String targetDll(Mod mod, String choice, Set<String> imports, int machine) {
        if (mod.fixedDll != null) return mod.fixedDll;
        String c = choice == null ? "" : choice.trim().toLowerCase(Locale.ROOT);
        if (SPECIALK_DLLS.contains(c)) return c;
        return autoDll(imports, machine);
    }

    /**
     * Special K's name from the EXE's imports: Direct3D 9 / 8 and OpenGL games by their DLL, DXGI-era
     * games as dxgi.dll. An EXE that imports none of them loads its renderer at run time: a 32-bit one is
     * taken as Direct3D 9, a 64-bit one as DXGI.
     */
    static String autoDll(Set<String> imports, int machine) {
        Set<String> i = imports == null ? Collections.<String>emptySet() : imports;
        boolean dxgi = i.contains("dxgi.dll") || i.contains("d3d11.dll") || i.contains("d3d12.dll") || i.contains("d3d10.dll")
                || i.contains("d3d10_1.dll");
        if (!dxgi && i.contains("d3d9.dll")) return "d3d9.dll";
        if (!dxgi && i.contains("d3d8.dll")) return "d3d8.dll";
        if (!dxgi && i.contains("opengl32.dll")) return "opengl32.dll";
        return !dxgi && machine == MACHINE_X86 ? "d3d9.dll" : "dxgi.dll";
    }

    /** {@code existing} plus {@code <dll>=n,b}, unless it already names that DLL (then unchanged). */
    public static String overrides(String existing, String dll) {
        String e = existing == null ? "" : existing.trim();
        String base = dll.toLowerCase(Locale.ROOT);
        if (base.endsWith(".dll")) base = base.substring(0, base.length() - 4);
        if (named(e).containsKey(base)) return e;
        return e.isEmpty() ? base + "=n,b" : e + ";" + base + "=n,b";
    }

    // ── The EXE: machine type and imports ────────────────────────────────────────────────────────

    /** The PE machine type of {@code exe} ({@link #MACHINE_X64}, {@link #MACHINE_X86}, ...), or 0. */
    public static int peMachine(File exe) {
        if (exe == null || !exe.isFile()) return 0;
        try (RandomAccessFile f = new RandomAccessFile(exe, "r")) {
            long pe = peHeader(f);
            if (pe < 0) return 0;
            f.seek(pe + 4);
            return u16(f);
        } catch (IOException e) {
            return 0;
        }
    }

    /** The DLL names {@code exe} imports, lowercase (empty when unreadable). */
    public static Set<String> peImports(File exe) {
        Set<String> out = new LinkedHashSet<>();
        if (exe == null || !exe.isFile()) return out;
        try (RandomAccessFile f = new RandomAccessFile(exe, "r")) {
            long pe = peHeader(f);
            if (pe < 0) return out;
            f.seek(pe + 6);
            int sections = u16(f);
            f.seek(pe + 20);
            int optSize = u16(f);
            long opt = pe + 24;
            f.seek(opt);
            int magic = u16(f);
            long dirs = opt + (magic == 0x20b ? 112 : 96);
            f.seek(dirs + 8);   // data directory 1: imports
            long importRva = u32(f);
            if (importRva == 0) return out;
            long secTable = opt + optSize;
            long[][] secs = new long[Math.min(sections, 96)][];
            for (int s = 0; s < secs.length; s++) {
                f.seek(secTable + s * 40L + 8);
                long vsize = u32(f), va = u32(f), rawSize = u32(f), raw = u32(f);
                secs[s] = new long[]{va, Math.max(vsize, rawSize), raw};
            }
            long desc = rvaToOffset(secs, importRva);
            for (int n = 0; desc > 0 && n < 512; n++, desc += 20) {
                f.seek(desc + 12);
                long nameRva = u32(f);
                if (nameRva == 0) break;
                long nameOff = rvaToOffset(secs, nameRva);
                if (nameOff <= 0) continue;
                f.seek(nameOff);
                StringBuilder sb = new StringBuilder();
                for (int c; sb.length() < 64 && (c = f.read()) > 0; ) sb.append((char) c);
                if (sb.length() > 0) out.add(sb.toString().toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            // unreadable: no imports
        }
        return out;
    }

    private static long peHeader(RandomAccessFile f) throws IOException {
        if (f.length() < 0x40) return -1;
        f.seek(0);
        if (f.read() != 'M' || f.read() != 'Z') return -1;
        f.seek(0x3c);
        long pe = u32(f);
        if (pe <= 0 || pe + 24 > f.length()) return -1;
        f.seek(pe);
        if (f.read() != 'P' || f.read() != 'E' || f.read() != 0 || f.read() != 0) return -1;
        return pe;
    }

    private static long rvaToOffset(long[][] secs, long rva) {
        for (long[] s : secs) if (s != null && rva >= s[0] && rva < s[0] + s[1]) return s[2] + (rva - s[0]);
        return -1;
    }

    private static int u16(RandomAccessFile f) throws IOException {
        int a = f.read(), b = f.read();
        if ((a | b) < 0) throw new IOException("eof");
        return a | (b << 8);
    }

    private static long u32(RandomAccessFile f) throws IOException {
        long a = f.read(), b = f.read(), c = f.read(), d = f.read();
        if ((a | b | c | d) < 0) throw new IOException("eof");
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    // ── The DLL in the game folder ───────────────────────────────────────────────────────────────

    /** What {@link #install} found and did. */
    public static final class Result {
        /** Our DLL is in place (false: the game folder has its own file of that name, or nothing). */
        public final boolean ours;
        /** A file of that name is in the folder (ours or the game's own), so the DLL override applies. */
        public final boolean present;
        public final String note;

        Result(boolean ours, boolean present, String note) {
            this.ours = ours;
            this.present = present;
            this.note = note;
        }
    }

    /** {@code name} in {@code dir}, any letter case, or null. */
    static File find(File dir, String name) {
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null) return null;
        for (File f : files) if (f.isFile() && f.getName().equalsIgnoreCase(name)) return f;
        return null;
    }

    /** The marker of {@code mod} in {@code dir}: [sha256, dll name or null], or null when there is none. */
    static String[] readMarker(File dir, Mod mod) {
        try {
            File m = new File(dir, mod.marker);
            if (!m.isFile()) return null;
            String[] parts = new String(Files.readAllBytes(m.toPath()), StandardCharsets.UTF_8).trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) return null;
            return new String[]{parts[0], parts.length > 1 ? parts[1] : mod.fixedDll};
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeMarker(File dir, Mod mod, String sha, String dll) throws IOException {
        // REFramework's marker keeps its original one-field format, so older app builds still read it.
        String text = mod.fixedDll != null ? sha : sha + " " + dll;
        try (OutputStream os = new FileOutputStream(new File(dir, mod.marker))) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** True when {@code dir} already holds our {@code dll} with sha256 {@code sha} (no download needed). */
    public static boolean inPlace(File dir, String dll, String sha) {
        try {
            File f = find(dir, dll);
            return f != null && f.length() > 0 && sha256(f).equals(sha);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Puts {@code source} (sha256 {@code sha}) into {@code dir} as {@code dll}. A file of that name that is not
     * ours (no marker, or a different sha256) is left alone. If the marker says we placed {@code mod} under
     * another name before, that file is removed first.
     */
    public static Result install(File dir, Mod mod, String dll, File source, String sha) throws Exception {
        String[] marker = readMarker(dir, mod);
        if (marker != null && marker[1] != null && !marker[1].equalsIgnoreCase(dll)) {
            File old = find(dir, marker[1]);
            if (old != null && sha256(old).equals(marker[0])) old.delete();
            new File(dir, mod.marker).delete();
            marker = null;
        }
        File existing = find(dir, dll);
        if (existing != null) {
            String have = sha256(existing);
            if (have.equals(sha)) {
                if (marker == null || !sha.equals(marker[0])) writeMarker(dir, mod, sha, dll);
                return new Result(true, true, dll + " already in place");
            }
            if (marker == null || !have.equals(marker[0]))
                return new Result(false, true, "the game folder has its own " + existing.getName() + " (not ours): left as it is");
            if (source == null) return new Result(true, true, "an older " + dll + " of ours kept (no download available)");
            copy(source, existing);   // ours from an older release: update it
            writeMarker(dir, mod, sha, dll);
            return new Result(true, true, dll + " updated");
        }
        if (source == null) return new Result(false, false, "no " + mod.name + " download available: not loaded");
        copy(source, new File(dir, dll));
        writeMarker(dir, mod, sha, dll);
        return new Result(true, true, dll + " placed");
    }

    /** Removes the file we placed for {@code mod} (sha256 == marker); a note, or null when nothing to do. */
    public static String remove(File dir, Mod mod) throws Exception {
        String[] marker = readMarker(dir, mod);
        if (marker == null) return null;
        File m = new File(dir, mod.marker);
        File dll = marker[1] != null ? find(dir, marker[1]) : null;
        if (dll != null && sha256(dll).equals(marker[0])) {
            if (!dll.delete()) return "could not remove " + dll.getName();
            m.delete();
            return "removed our " + dll.getName();
        }
        m.delete();
        return dll != null ? "the game folder's own " + dll.getName() + " left as it is" : null;
    }

    /** The DLL names a WINEDLLOVERRIDES value sets (lowercase, no ".dll") and the order it gives each. */
    static Map<String, String> named(String overrides) {
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

    static void copy(File from, File to) throws IOException {
        ReFramework.copy(from, to);
    }
}
