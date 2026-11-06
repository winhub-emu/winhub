package com.winhub.hernandez.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.winhub.hernandez.inputcontrols.HermesWriter;
import com.winhub.hernandez.winhandler.WinHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Controller input" log (Settings › Log Manager, off by default): one {@code controller.txt} per
 * launch of a Wine game, next to the game's other logs, written when the game exits. It is what a
 * user sends when the controls don't work, for the HERMES input chain (DirectInput, Wine's XInput
 * and Direct XInput).
 *
 * <p>It opens with a CHECKLIST per player slot - each step of the chain marked PASS / FAIL / WARN /
 * SKIP / INFO from what the app and the guest actually counted - so a reader sees where input
 * stopped: the app sent nothing, Wine never opened the pad, Wine opened it but read nothing, the game
 * never loaded Direct XInput, rumble never arrived. Then the evidence in the same order: the launch's
 * controller setup, the controllers and their slots, the session's controller events (in-game changes,
 * reconnects, Direct XInput mode changes), HERMES's counters per slot, and the guest's controller
 * lines (Direct XInput DLL and the evdev layer).
 *
 * <p>Nothing runs while the switch is off: {@link #begin} leaves no recorder and every static entry
 * point returns at its first null check. With it on, the launch also captures the guest's output,
 * keeping only the controller lines ({@link #GUEST_LINES}), and turns on the evdev layer's
 * diagnostics (FAKE_EVDEV_LOG=1); both are written at attach / probe / loss, not per input event.
 * The Linux runtime has its own (fake-input.txt in its session folder) and does not use this.
 */
public final class ControllerLog {
    private static final String TAG = "ControllerLog";

    /** Output file name inside the per-game log dir (see {@link LogRotation}). */
    public static final String OUTPUT_NAME = "controller.txt";
    /** The Log Manager switch. */
    public static final String PREF_KEY = "enable_controller_logs";

    private static final int MAX_EVENTS = 400;
    private static final int MAX_GUEST_LINES = 600;
    /** A guest line of the same shape (digits ignored) is kept this many times, then only counted. */
    private static final int MAX_SAME_SHAPE = 3;

    private static volatile ControllerLog active;

    private final long startMs = System.currentTimeMillis();
    private final Map<String, String> setup = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private int eventsDropped;
    private final List<String> guest = new ArrayList<>();
    private int guestDropped;
    private final Map<String, Integer> shapes = new LinkedHashMap<>();
    private final long[] rumble = new long[HermesWriter.MAX_SLOTS];
    private final List<String> padsAtStart = new ArrayList<>();
    private final List<String> padsAtEnd = new ArrayList<>();
    private final Set<Integer> slotsUsed = new LinkedHashSet<>();
    private final Set<String> xinputImages = new LinkedHashSet<>();
    private final Set<Integer> xinputAttached = new LinkedHashSet<>();
    private final Set<Integer> xinputNoController = new LinkedHashSet<>();
    private boolean xinputDirect;
    private boolean xinputWine;
    private boolean dinputVisible;
    private boolean dinputVibration;
    private int reconnectsFailed;

    private ControllerLog() {}

    /** True when the Log Manager switch is on. */
    public static boolean isEnabled(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_KEY, false);
    }

    /** Starts this launch's recorder when the switch is on; clears any earlier one either way. */
    public static boolean begin(SharedPreferences prefs) {
        active = isEnabled(prefs) ? new ControllerLog() : null;
        return active != null;
    }

    public static boolean isActive() {
        return active != null;
    }

    /** One line of the launch's controller setup, under {@code key} (a later value replaces it). */
    public static void setup(String key, String value) {
        ControllerLog log = active;
        if (log == null) return;
        synchronized (log) {
            log.setup.put(key, value);
        }
    }

    /** What the checklist needs to know about the launch's choices. */
    public static void launchChoices(boolean directXInput, boolean wineXInput, boolean dinputVisible,
                                     boolean dinputVibration) {
        ControllerLog log = active;
        if (log == null) return;
        synchronized (log) {
            log.xinputDirect = directXInput;
            log.xinputWine = wineXInput;
            log.dinputVisible = dinputVisible;
            log.dinputVibration = dinputVibration;
        }
    }

    /** One timestamped line in the session's controller events. */
    public static void event(String line) {
        ControllerLog log = active;
        if (log == null) return;
        synchronized (log) {
            if (log.events.size() >= MAX_EVENTS) {
                log.eventsDropped++;
                return;
            }
            log.events.add(log.since() + "  " + line);
        }
    }

    /** A controller took {@code slot} (WinHandler): the slot is reported even if it left before the end. */
    public static void slotTaken(int slot, String line) {
        ControllerLog log = active;
        if (log == null) return;
        synchronized (log) {
            if (slot >= 0 && slot < HermesWriter.MAX_SLOTS) log.slotsUsed.add(slot);
        }
        event(line);
    }

    /** A reconnect ended; a failed one is a WARN on the checklist. */
    public static void reconnect(String slots, boolean ok) {
        ControllerLog log = active;
        if (log == null) return;
        synchronized (log) {
            if (!ok) log.reconnectsFailed++;
        }
        event("reconnect of slots " + slots + (ok ? " confirmed" : " NOT confirmed (the game's reader did not come back)"));
    }

    /** A rumble datagram (HXR1) reached the app for {@code slot}: Direct XInput's or the effects engine's. */
    public static void rumble(int slot) {
        ControllerLog log = active;
        if (log == null || slot < 0 || slot >= HermesWriter.MAX_SLOTS) return;
        boolean first;
        synchronized (log) {
            first = log.rumble[slot]++ == 0;
        }
        if (first) event("slot " + slot + ": first rumble request reached the app");
    }

    /** The controllers and their player slots, at the start ({@code atEnd} false) or the end. */
    public static void pads(List<WinHandler.PlayerSlotInfo> slots, boolean atEnd) {
        ControllerLog log = active;
        if (log == null || slots == null) return;
        synchronized (log) {
            List<String> out = atEnd ? log.padsAtEnd : log.padsAtStart;
            out.clear();
            for (WinHandler.PlayerSlotInfo s : slots) {
                if (s.isOnScreen && s.currentSlot < 0) continue;
                String slot = s.currentSlot >= 0 ? "player " + (s.currentSlot + 1) + " (slot " + s.currentSlot + ")"
                        : s.override == WinHandler.SLOT_IGNORE ? "ignored" : "no slot";
                out.add(slot + ": " + s.displayName + (s.isOnScreen ? " [on-screen controls]"
                        : s.isGameController ? "" : " [not a game controller]")
                        + (s.override >= 0 ? " [pinned to player " + (s.override + 1) + "]" : ""));
                if (s.currentSlot >= 0) log.slotsUsed.add(s.currentSlot);
            }
        }
    }

    // ── Guest output ────────────────────────────────────────────────────────────────────────────

    /** Lines the Direct XInput DLL and the evdev layer write (fakeinput.cpp Logger + hermes's logger). */
    private static final Pattern GUEST_LINE = Pattern.compile(
            "HERMES-XInput: |^\\s*fakeinput|^\\s*(Adding controller|Removing controller|Replacing stale controller"
                    + "|Could not attach|Hooking ioctl EVIOCG(ID|NAME)|Unhandled evdev ioctl|EVIOCSFF|EV_FF )");
    private static final Pattern XI_LOADED = Pattern.compile("HERMES-XInput: .* loaded into (.+?) \\(pid \\d+\\)");
    /** The DLL opened the slot's file - on the game's first XInput call, for all four slots, held or not. */
    private static final Pattern XI_ATTACHED = Pattern.compile("HERMES-XInput: slot (\\d+) attached");
    /** The game asked for a slot no controller holds. */
    private static final Pattern XI_NO_CONTROLLER = Pattern.compile("HERMES-XInput: slot (\\d+): .*no controller on this slot");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /** For {@code ProcessHelper.addDebugCallback}: keeps the guest's controller lines, drops the rest. */
    public static final Callback<String> GUEST_LINES = ControllerLog::guestLine;

    private static void guestLine(String line) {
        ControllerLog log = active;
        if (log == null || line == null || !GUEST_LINE.matcher(line).find()) return;
        Matcher loaded = XI_LOADED.matcher(line);
        Matcher attached = XI_ATTACHED.matcher(line);
        Matcher noController = XI_NO_CONTROLLER.matcher(line);
        synchronized (log) {
            if (loaded.find()) log.xinputImages.add(baseName(loaded.group(1)));
            if (attached.find()) {
                try { log.xinputAttached.add(Integer.parseInt(attached.group(1))); } catch (NumberFormatException ignored) {}
            }
            if (noController.find()) {
                try { log.xinputNoController.add(Integer.parseInt(noController.group(1))); } catch (NumberFormatException ignored) {}
            }
            String shape = DIGITS.matcher(line).replaceAll("#");
            int seen = log.shapes.containsKey(shape) ? log.shapes.get(shape) : 0;
            log.shapes.put(shape, seen + 1);
            if (seen >= MAX_SAME_SHAPE || log.guest.size() >= MAX_GUEST_LINES) {
                log.guestDropped++;
                return;
            }
            log.guest.add(log.since() + "  " + line.trim());
        }
    }

    private static String baseName(String path) {
        String p = path.trim();
        int cut = Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/'));
        return cut >= 0 ? p.substring(cut + 1) : p;
    }

    private String since() {
        return String.format(Locale.ROOT, "+%7.1fs", (System.currentTimeMillis() - startMs) / 1000.0);
    }

    // ── Writing ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Writes {@value #OUTPUT_NAME} into {@code perGameLogDir} and ends the recorder. Reads the slots'
     * counters through {@link HermesWriter#getSessionStat}, so it works after the session retired
     * the slot files. Never throws.
     */
    public static void finish(Context context, File perGameLogDir, String gameName) {
        ControllerLog log = active;
        active = null;
        if (log == null || perGameLogDir == null) return;
        try {
            String text;
            synchronized (log) {
                text = log.build(context, gameName);
            }
            perGameLogDir.mkdirs();
            File target = new File(perGameLogDir, OUTPUT_NAME);
            if (FileUtils.writeString(target, text)) Log.i(TAG, "wrote " + target.getAbsolutePath());
        } catch (Throwable t) {
            // Never let log collection break a game exiting.
            Log.w(TAG, "write failed", t);
        }
    }

    private static long stat(int slot, int which) {
        return HermesWriter.getSessionStat(slot, which);
    }

    private String build(Context context, String gameName) {
        StringBuilder out = new StringBuilder();
        out.append(LogcatCapture.deviceHeader(context));
        out.append("===== CONTROLLER INPUT LOG =====\n");
        out.append("Game: ").append(gameName != null ? gameName : "?").append('\n');
        out.append(String.format(Locale.ROOT, "Session length: %.0f s\n", (System.currentTimeMillis() - startMs) / 1000.0));

        // Slots to report: every slot a controller held. Every slot file gets one neutral publish when it
        // is prepared and Direct XInput opens all four, so neither of those makes a slot a player; input
        // (button edges, a reader on its event node) or rumble aimed at it does.
        Set<Integer> slots = new java.util.TreeSet<>(slotsUsed);
        for (int s = 0; s < HermesWriter.MAX_SLOTS; s++) {
            if (stat(s, HermesWriter.STAT_EDGES) > 0 || stat(s, HermesWriter.STAT_READER_ATTACHES) > 0
                    || rumble[s] > 0) slots.add(s);
        }

        out.append("\n===== CHECKLIST =====\n");
        List<String> rows = new ArrayList<>();
        if (slots.isEmpty()) {
            rows.add(fail("PAD", "no controller held a player slot and the app sent no input: Android did not hand "
                    + "WinHub a controller (check Controls › Players; is the pad connected, or set to Ignore?)"));
        }
        for (int s : slots) checklistForSlot(rows, s);
        checklistSession(rows);
        int pass = 0, fail = 0, warn = 0;
        for (String r : rows) {
            out.append(r).append('\n');
            if (r.startsWith("[PASS]")) pass++;
            else if (r.startsWith("[FAIL]")) fail++;
            else if (r.startsWith("[WARN]")) warn++;
        }
        out.append(String.format(Locale.ROOT, "-- %d pass, %d fail, %d warn%s\n", pass, fail, warn,
                fail == 0 ? "" : " — read the first FAIL: the steps after it depend on it"));

        out.append("\n===== SETUP AT LAUNCH =====\n");
        if (setup.isEmpty()) out.append("(none recorded)\n");
        for (Map.Entry<String, String> e : setup.entrySet()) {
            out.append(e.getKey()).append(": ").append(LogcatCapture.redact(e.getValue())).append('\n');
        }

        out.append("\n===== CONTROLLERS =====\n");
        out.append("At the start:\n");
        appendList(out, padsAtStart, "  (none)");
        out.append("At the end:\n");
        appendList(out, padsAtEnd, "  (none)");

        out.append("\n===== SESSION EVENTS =====\n");
        appendList(out, events, "(none)");
        if (eventsDropped > 0) out.append("(+").append(eventsDropped).append(" more not kept)\n");

        out.append("\n===== HERMES COUNTERS =====\n");
        for (int s = 0; s < HermesWriter.MAX_SLOTS; s++) appendCounters(out, s);

        out.append("\n===== GUEST CONTROLLER LINES (Direct XInput DLL, evdev layer) =====\n");
        if (guest.isEmpty()) out.append("(none)\n");
        for (String l : guest) out.append(LogcatCapture.redact(l)).append('\n');
        if (guestDropped > 0) {
            out.append("(+").append(guestDropped).append(" repeated or later lines not kept)\n");
        }
        return out.toString();
    }

    private void checklistForSlot(List<String> rows, int s) {
        String p = "P" + (s + 1) + " ";
        long publishes = stat(s, HermesWriter.STAT_PUBLISHES);
        long failed = stat(s, HermesWriter.STAT_FAILED_PUBLISHES);
        long attaches = stat(s, HermesWriter.STAT_READER_ATTACHES);
        long reattaches = stat(s, HermesWriter.STAT_READER_REATTACHES);
        long frames = stat(s, HermesWriter.STAT_READER_FRAMES);
        long removals = stat(s, HermesWriter.STAT_READER_REMOVALS);

        if (publishes > 0) {
            rows.add(pass(p + "APP", "the app sent " + publishes + " controller updates for slot " + s));
        } else {
            rows.add(fail(p + "APP", "the app sent nothing for slot " + s
                    + ": no input from this controller reached WinHub (or it was moved to another slot)"));
        }
        if (failed > 0) {
            rows.add(warn(p + "APP", failed + " updates could not be written to the slot file"));
        }

        if (attaches > 0) {
            rows.add(pass(p + "WINE", "Wine's controller layer opened the pad " + attaches + " time(s)"
                    + (reattaches > 0 ? ", re-opened " + reattaches : "")
                    + (removals > 0 ? ", closed " + removals : "")
                    + " (this path feeds DirectInput and Wine's XInput)"));
            if (frames > 0) {
                rows.add(pass(p + "READ", "Wine read " + frames + " input frames from the pad"));
            } else if (publishes > 0) {
                rows.add(fail(p + "READ", "Wine opened the pad but read no input from it"));
            } else {
                rows.add(skip(p + "READ", "nothing was sent, so nothing could be read"));
            }
        } else if (xinputDirect && xinputAttached.contains(s)) {
            rows.add(warn(p + "WINE", "Wine's controller layer never opened the pad; Direct XInput read it, "
                    + "so XInput games work, but DirectInput games will not see it"));
        } else {
            rows.add(fail(p + "WINE", "Wine's controller layer never opened the pad (no /dev/input/event" + s
                    + " reader): DirectInput and Wine's XInput cannot see it"));
        }

        if (xinputDirect) {
            if (xinputAttached.contains(s) && xinputNoController.contains(s)) {
                rows.add(warn(p + "XINPUT", "the game called XInput, but Direct XInput found no controller on slot " + s
                        + " when the game asked for it (it may have arrived later)"));
            } else if (xinputAttached.contains(s)) {
                rows.add(pass(p + "XINPUT", "the game called XInput and Direct XInput is reading this player's slot " + s));
            } else if (!xinputImages.isEmpty()) {
                rows.add(warn(p + "XINPUT", "Direct XInput was loaded but never connected to slot " + s
                        + " (the game never asked for this player, or it uses DirectInput)"));
            }
        }

        long ffDatagrams = stat(s, HermesWriter.STAT_READER_FF_DATAGRAMS);
        if (rumble[s] > 0) {
            long viaEngine = Math.min(ffDatagrams, rumble[s]);
            rows.add(pass(p + "RUMBLE", rumble[s] + " rumble requests reached the app (" + viaEngine
                    + " through Wine — DirectInput effects or Wine's XInput — and " + (rumble[s] - viaEngine)
                    + " from Direct XInput)"));
        } else {
            rows.add(skip(p + "RUMBLE", "no rumble requests this session (the game asked for none, or vibration is off)"));
        }

        long uploads = stat(s, HermesWriter.STAT_READER_FF_UPLOADS);
        long plays = stat(s, HermesWriter.STAT_READER_FF_PLAYS);
        if (uploads > 0 || plays > 0) {
            rows.add(info(p + "DINPUT FF", uploads + " DirectInput effects uploaded, " + plays + " played"
                    + (plays == 0 ? " (uploaded but never started)" : "")));
        } else if (!dinputVibration) {
            rows.add(skip(p + "DINPUT FF", "DirectInput vibration is off"));
        }

        long hz = stat(s, HermesWriter.STAT_READER_AXIS_HZ);
        long fallbacks = stat(s, HermesWriter.STAT_READER_AUTO_FALLBACKS);
        if (hz > 0) {
            rows.add(info(p + "STICKS", "stick updates last paced at " + hz + "/s ("
                    + axisMode((int) stat(s, HermesWriter.STAT_READER_AXIS_MODE)) + ")"
                    + (fallbacks > 0 ? ", fell back to fixed 60/s " + fallbacks + " time(s)" : "")));
        }
    }

    private void checklistSession(List<String> rows) {
        if (xinputDirect) {
            if (!xinputImages.isEmpty()) {
                rows.add(pass("XINPUT DLL", "Direct XInput loaded into " + String.join(", ", xinputImages)));
            } else {
                rows.add(warn("XINPUT DLL", "Direct XInput was on but no process loaded it: the game uses DirectInput "
                        + "only, ships its own xinput DLL (see Setup), or loads XInput late"));
            }
        } else if (xinputWine) {
            rows.add(info("XINPUT DLL", "XInput goes through Wine's own XInput (Direct XInput off for this launch)"));
        }
        if (xinputWine && dinputVisible) {
            rows.add(warn("DINPUT", "XInput source is Wine and DirectInput games can see the controller: Wine's own "
                    + "XInput then ignores the controller. Use Direct XInput, or hide the controller from DirectInput"));
        } else {
            rows.add(info("DINPUT", "DirectInput games " + (dinputVisible ? "can" : "cannot") + " see the controller"));
        }
        if (reconnectsFailed > 0) {
            rows.add(warn("LIVE", reconnectsFailed + " in-game reconnect(s) were not confirmed (see Session events)"));
        }
    }

    private static String axisMode(int mode) {
        switch (mode) {
            case 1: return "follows the game's frame rate";
            case 2: return "60/s fallback, no frame feed";
            case 3: return "fixed";
            case 4: return "unpaced";
            case 5: return "capped by the auto ceiling";
            default: return "mode " + mode;
        }
    }

    private static void appendCounters(StringBuilder out, int s) {
        if (stat(s, HermesWriter.STAT_PUBLISHES) == 0 && stat(s, HermesWriter.STAT_READER_ATTACHES) == 0) {
            out.append("slot ").append(s).append(": unused\n");
            return;
        }
        String[] names = {
                "publishes", "button edges", "wake calls", "woken", "unplugs", "reader attaches", "reader re-attaches",
                "reader taps", "reader coalesced", "reader resyncs", "reader frames", "reader events", "reader removals",
                "instance", "failed publishes", "has backing", "paced frames", "merged axis", "axis hz", "auto fallbacks",
                "frame interval ns", "game frames", "axis mode", "frame wakes", "helper wakes", "auto max",
                "control gen applied", "identity gen attached", "identity reads", "control gen", "ff uploads",
                "ff plays", "ff datagrams", "ff guard fades"};
        out.append("slot ").append(s).append(':');
        for (int i = 0; i < names.length; i++) {
            if (i % 6 == 0) out.append("\n ");
            out.append(' ').append(names[i]).append('=').append(stat(s, i));
            if (i % 6 != 5 && i != names.length - 1) out.append(',');
        }
        out.append('\n');
    }

    private static void appendList(StringBuilder out, List<String> lines, String empty) {
        if (lines.isEmpty()) out.append(empty).append('\n');
        for (String l : lines) out.append(l).append('\n');
    }

    private static String pass(String step, String what) { return "[PASS] " + step + ": " + what; }
    private static String fail(String step, String what) { return "[FAIL] " + step + ": " + what; }
    private static String warn(String step, String what) { return "[WARN] " + step + ": " + what; }
    private static String skip(String step, String what) { return "[SKIP] " + step + ": " + what; }
    private static String info(String step, String what) { return "[INFO] " + step + ": " + what; }
}
