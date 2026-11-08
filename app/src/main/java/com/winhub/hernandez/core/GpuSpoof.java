package com.winhub.hernandez.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The GPU-name spoof as the Wayland backend applies it — shared by the Wayland driver-settings
 * editors and the launch path.
 *
 * <p>On X11 the driver config's {@code gpuName} becomes WRAPPER_DEVICE_NAME / _ID / WRAPPER_VENDOR_ID,
 * which the X11 wrapper Vulkan ICD reports to everything above it. Wayland games render on the Proton's
 * bundled Wayland Turnip with no wrapper, so the same choice is handed to DXVK instead:
 * {@code dxgi.custom*} for D3D10/11 and for D3D12 (vkd3d-proton takes the adapter from DXVK's DXGI),
 * {@code d3d9.custom*} for D3D9 and D3D8 (DXVK's D3D9 has its own adapter options). The same
 * {@code gpuName} key is used on both backends, so the choice survives switching.
 *
 * <p>It reaches DXVK by TWO routes, because neither one is enough on its own:
 * <ul>
 * <li>A generated dxvk.conf named by DXVK_CONFIG_FILE ({@link #configFileText}) — the only route that
 *     can carry the GPU NAME. DXVK's {@code parseUserConfigLine} ends an unquoted value at the first
 *     whitespace but keeps everything between quotes, so {@code dxgi.customDeviceDesc = "NVIDIA GeForce
 *     GTX 1080"} arrives whole. Every DXVK ever shipped reads DXVK_CONFIG_FILE; DXVK_CONFIG is only
 *     read from 2.5 on (on 2.4.1 it is ignored outright, so the file is the whole delivery there).
 * <li>DXVK_CONFIG, for the ids and the memory cap ({@link #mergeDxvkConfig}). Only a bare token is safe
 *     in it: every env string that passes back through {@link EnvVars}'s string form is re-split on
 *     spaces, and on device the quoted name never arrived — DXVK logged a DXVK_CONFIG that began
 *     mid-string and applied none of the ids — while the same options with no spaces and no quotes
 *     applied in full and the game showed them. So this value is written with no whitespace at all:
 *     {@code dxgi.customVendorId=10de;dxgi.customDeviceId=1b80}, and the name is left to the file.
 * </ul>
 *
 * <p>DXVK parses the file first and DXVK_CONFIG after it, keeping the LAST value it sees for a key, and
 * a {@code [exe]} piece scopes the keys after it to one exe. Our keys therefore go FIRST and in the
 * global scope, and a key the user already sets globally (in their own DXVK_CONFIG or config file) is
 * left out of BOTH routes so theirs is what applies.
 */
public final class GpuSpoof {
    private GpuSpoof() {}

    /** The "no spoof" entry of the GPU name list: report the real GPU. */
    public static final String DEVICE = "Device";

    /** The option that carries the GPU NAME — the one value the environment cannot deliver. */
    public static final String KEY_DXGI_DEVICE_DESC = "dxgi.customDeviceDesc";

    public static final int VENDOR_NVIDIA = 0x10de;
    public static final int VENDOR_AMD = 0x1002;

    /** One gpu_cards.json entry. {@code deviceId} is -1 when the list's value isn't a 16-bit PCI id. */
    public static final class Card {
        public final String name;
        public final int vendorId;
        public final int deviceId;

        Card(String name, int vendorId, int deviceId) {
            this.name = name;
            this.vendorId = vendorId;
            this.deviceId = deviceId;
        }
    }

    /** True when {@code gpuName} asks for a spoof (anything but empty / "Device"). */
    public static boolean isSpoofing(String gpuName) {
        return gpuName != null && !gpuName.isEmpty() && !DEVICE.equals(gpuName);
    }

    private static volatile List<Card> cards;

    /** gpu_cards.json in list order (the X11 dialog's GPU name list), read once per process. */
    public static List<Card> cards(Context context) {
        List<Card> c = cards;
        if (c != null) return c;
        List<Card> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(FileUtils.readString(context, "gpu_cards.json"));
            for (int i = 0; i < arr.length(); i++) list.add(card(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        cards = list;
        return list;
    }

    /** "Device" followed by every card name, duplicates dropped: what the GPU name pickers offer. */
    public static List<String> names(Context context) {
        List<String> r = new ArrayList<>();
        r.add(DEVICE);
        for (Card c : cards(context)) if (!r.contains(c.name)) r.add(c.name);
        return r;
    }

    /**
     * The gpu_cards.json entry for {@code name}: the entry with exactly that name, else the first one
     * whose name contains it (X11's lookup, WineD3DConfigDialog.getDeviceIdFromGPUName). Exact first
     * because the list has names that contain other names ("GTX 560" / "GTX 560 Ti"), where X11's
     * first-contains lookup lands on the longer one. Null for "Device" or a name not in the list.
     */
    public static Card find(Context context, String name) {
        if (!isSpoofing(name)) return null;
        Card contains = null;
        for (Card c : cards(context)) {
            if (c.name.equals(name)) return c;
            if (contains == null && c.name.contains(name)) contains = c;
        }
        return contains;
    }

    private static Card card(JSONObject o) {
        long vendor = o.optLong("vendorID", -1);
        long device = o.optLong("deviceID", -1);
        return new Card(o.optString("name", ""),
                vendor >= 0 && vendor <= 0xffff ? (int) vendor : -1,
                device >= 0 && device <= 0xffff ? (int) device : -1);
    }

    /** The PCI vendor the spoof reports, or -1 for "Device" / unknown. */
    public static int vendor(Context context, String gpuName) {
        if (!isSpoofing(gpuName)) return -1;
        Card c = find(context, gpuName);
        if (c != null && c.vendorId >= 0) return c.vendorId;
        String n = gpuName.toUpperCase(Locale.ROOT);
        if (n.startsWith("NVIDIA")) return VENDOR_NVIDIA;
        if (n.startsWith("AMD") || n.startsWith("ATI")) return VENDOR_AMD;
        return -1;
    }

    /** The warning for what an NVIDIA or AMD name can make a game do, or null for anything else. */
    public static String vendorWarning(Context context, String gpuName) {
        int v = vendor(context, gpuName);
        if (v == VENDOR_NVIDIA) return "NVIDIA: some games will now try NVAPI, DLSS or Reflex.";
        if (v == VENDOR_AMD) return "AMD: some games take AMD AGS paths (a known crash for Unreal Engine DirectX 11 games).";
        return null;
    }

    /** The {@code gpuName} stored in a graphicsDriverConfig ("k=v;k=v"), "Device" when absent. */
    public static String gpuNameOf(String graphicsDriverConfig) {
        if (graphicsDriverConfig != null) {
            for (String kv : graphicsDriverConfig.split(";")) {
                if (kv.startsWith("gpuName=")) {
                    String v = kv.substring("gpuName=".length());
                    return v.isEmpty() ? DEVICE : v;
                }
            }
        }
        return DEVICE;
    }

    /** The four lowercase hex digits DXVK's parsePciId() accepts (no "0x"; any other length is ignored). */
    public static String pciHex(int id) {
        return String.format(Locale.ROOT, "%04x", id & 0xffff);
    }

    /**
     * The options for this spoof and memory cap, in the order they are written — the generated config
     * file takes all of them, DXVK_CONFIG only the ones the environment can carry. {@code card}
     * null = no spoof; {@code maxDeviceMemoryMb} <= 0 = no cap. The cap is DXGI's reported adapter
     * memory ({@code dxgi.maxDeviceMemory}); D3D9's {@code d3d9.maxAvailableMemory} is left alone
     * because D3D9 also enforces it as an allocation limit.
     *
     * <p>The name is quoted for DXVK's file parser, which would otherwise stop at the space after the
     * first word. A quote or a ';' inside the name is dropped rather than escaped: a '"' would end the
     * quoted run early, and a ';' would split the value in half on the DXVK_CONFIG route.
     */
    public static LinkedHashMap<String, String> dxvkOptions(Card card, int maxDeviceMemoryMb) {
        LinkedHashMap<String, String> o = new LinkedHashMap<>();
        if (card != null) {
            String desc = "\"" + card.name.replace("\"", "").replace(";", "") + "\"";
            for (String api : new String[] {"dxgi", "d3d9"}) {
                if (card.vendorId >= 0) o.put(api + ".customVendorId", pciHex(card.vendorId));
                if (card.deviceId >= 0) o.put(api + ".customDeviceId", pciHex(card.deviceId));
                o.put(api + ".customDeviceDesc", desc);
            }
        }
        if (maxDeviceMemoryMb > 0) o.put("dxgi.maxDeviceMemory", String.valueOf(maxDeviceMemoryMb));
        return o;
    }

    /** What {@link #mergeDxvkConfig} did. */
    public static final class Merge {
        /** The new DXVK_CONFIG value ("" when there is nothing to set). */
        public final String value;
        /** Our options that survived, in write order: what the generated config file is built from. */
        public final LinkedHashMap<String, String> options = new LinkedHashMap<>();
        /** Our keys that also fitted in DXVK_CONFIG (a subset of {@link #options}: see {@link #envSafe}). */
        public final List<String> added = new ArrayList<>();
        /** Our keys left out because the user already sets them, with the user's value ("key=value"). */
        public final List<String> kept = new ArrayList<>();

        Merge(String value) { this.value = value; }
    }

    /**
     * Works out what each route delivers: {@code ours} minus every key the user already sets in the
     * global scope of {@code existing} (their DXVK_CONFIG, may be null or empty) or of
     * {@code configFile} (their own config file's contents, may be null) — DXVK keeps the last value it
     * parses, so a key of theirs would win anyway; leaving ours out keeps the session log honest about
     * what actually went. Keys they set only inside an [exe] section still get ours: their scoped value
     * comes later and wins for that exe.
     *
     * <p>{@link Merge#value} is the new DXVK_CONFIG — our survivors that {@link #envSafe} accepts, in
     * DXVK's "key=value;key=value" form with NO whitespace anywhere (the transport splits on spaces),
     * followed by {@code existing} unchanged. Ours go first so they still arrive when something the
     * user put in DXVK_CONFIG has a space in it and cuts the value short.
     */
    public static Merge mergeDxvkConfig(String existing, String configFile, Map<String, String> ours) {
        Map<String, String> theirs = globalKeys(existing, ";");
        if (configFile != null) {
            for (Map.Entry<String, String> e : globalKeys(configFile, "\n").entrySet())
                theirs.putIfAbsent(e.getKey(), e.getValue());
        }
        StringBuilder sb = new StringBuilder();
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        List<String> added = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (Map.Entry<String, String> e : ours.entrySet()) {
            if (theirs.containsKey(e.getKey())) {
                kept.add(e.getKey() + "=" + theirs.get(e.getKey()));
                continue;
            }
            options.put(e.getKey(), e.getValue());
            if (!envSafe(e.getValue())) continue;   // the quoted GPU name: file route only
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
            added.add(e.getKey());
        }
        String rest = existing == null ? "" : existing.trim();
        if (!rest.isEmpty()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(rest);
        }
        Merge m = new Merge(sb.toString());
        m.options.putAll(options);
        m.added.addAll(added);
        m.kept.addAll(kept);
        return m;
    }

    /**
     * True when {@code value} is safe to send in an environment variable: one token, no whitespace, no
     * quote and no ';'. {@link EnvVars} re-splits a NAME=VALUE string on spaces wherever an environment
     * round-trips through its string form, ';' is the DXVK_CONFIG separator, and a quoted spaced value
     * demonstrably did not reach DXVK intact — so anything else is cut short somewhere between here and
     * the game. A GPU name has spaces by nature, which is why it travels in the config file instead.
     */
    public static boolean envSafe(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\'' || c == ';' || Character.isWhitespace(c)) return false;
        }
        return true;
    }

    /**
     * The generated dxvk.conf: one {@code key = value} line per surviving option, then the user's own
     * config file ({@code userText}, null when they have none) appended verbatim so selecting one keeps
     * working — it is merged, never replaced. Ours go first and theirs follows: none of their global
     * keys are in {@code options} (see {@link #mergeDxvkConfig}), so where the two ever name the same
     * option THEIRS is what applies, whether they set it globally or for one exe.
     */
    public static String configFileText(Map<String, String> options, String userPath, String userText) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated for this session by the graphics driver settings.\n");
        sb.append("# Rewritten on every launch - edit the settings, not this file.\n");
        for (Map.Entry<String, String> e : options.entrySet())
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        if (userText != null) {
            sb.append("\n# --- your own config file, applied on top of the lines above")
              .append(isConfigFilePath(userPath) ? ": " + userPath : "").append(" ---\n");
            sb.append(userText);
            if (!userText.endsWith("\n")) sb.append('\n');
        }
        return sb.toString();
    }

    /** key -> value of every "key = value" piece before the first [section] piece. */
    private static Map<String, String> globalKeys(String text, String separator) {
        Map<String, String> keys = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return keys;
        for (String piece : text.split(separator)) {
            String p = piece.trim();
            if (p.isEmpty() || p.startsWith("#")) continue;
            if (p.startsWith("[")) break;
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String k = p.substring(0, eq).trim();
            if (!k.isEmpty()) keys.putIfAbsent(k, p.substring(eq + 1).trim());
        }
        return keys;
    }

    /** True when {@code path} names a config file the user actually chose — the DX wrapper config's
     *  "None" / "0" placeholders and an empty value are not one. */
    public static boolean isConfigFilePath(String path) {
        return path != null && !path.isEmpty() && !path.equals("0") && !path.equals("None");
    }

    /** The text of DXVK_CONFIG_FILE when it names a readable file on the Android side, else null —
     *  including for a file that can only be read from inside the guest. A caller that cannot read
     *  their file must leave their DXVK_CONFIG_FILE pointing at it, or their whole config disappears. */
    public static String readConfigFile(String path) {
        if (!isConfigFilePath(path)) return null;
        try {
            File f = new File(path);
            if (!f.isFile() || f.length() > 1 << 20) return null;
            return FileUtils.readString(f);
        } catch (Exception e) {
            return null;
        }
    }
}
