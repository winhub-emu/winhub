package com.winhub.hernandez.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The programs a game starts alongside it (a trainer, Cheat Engine, ...), in start order. Stored in the
 * shortcut extra {@value #EXTRA} as {@code delay>path} entries joined by {@code |}. Both separators are
 * illegal in Windows paths, so a path never needs escaping. {@code delay} is the seconds to wait before
 * starting that program: counted from winhandler.exe being up for the first, from the previous program's
 * start for the rest.
 */
public final class StartPrograms {
    public static final String EXTRA = "startPrograms";
    public static final int DEFAULT_DELAY_SEC = 10;
    public static final int MAX_DELAY_SEC = 600;

    private static final char ENTRY_SEP = '|';
    private static final char DELAY_SEP = '>';

    private StartPrograms() {}

    public static final class Entry {
        /** A Windows path, e.g. {@code F:\Trainers\Trainer.exe}. */
        public final String path;
        public final int delaySec;

        public Entry(String path, int delaySec) {
            this.path = path;
            this.delaySec = clampDelay(delaySec);
        }
    }

    public static int clampDelay(int seconds) {
        return Math.max(0, Math.min(MAX_DELAY_SEC, seconds));
    }

    /** The entries stored in {@code raw}; blank paths are dropped. Never null. */
    public static List<Entry> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<Entry> out = new ArrayList<>();
        for (String part : raw.split("\\" + ENTRY_SEP)) {
            int sep = part.indexOf(DELAY_SEP);
            int delay = DEFAULT_DELAY_SEC;
            String path = part;
            if (sep >= 0) {
                path = part.substring(sep + 1);
                try {
                    delay = Integer.parseInt(part.substring(0, sep).trim());
                } catch (NumberFormatException e) {
                    // A damaged delay falls back to the default rather than dropping the program.
                    delay = DEFAULT_DELAY_SEC;
                }
            }
            path = path.trim();
            if (!path.isEmpty()) out.add(new Entry(path, delay));
        }
        return out;
    }

    /** The extra value for {@code entries}, or null when there is nothing to start (clears the extra). */
    public static String encode(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            String path = e.path.replace(String.valueOf(ENTRY_SEP), "").replace(String.valueOf(DELAY_SEP), "").trim();
            if (path.isEmpty()) continue;
            if (sb.length() > 0) sb.append(ENTRY_SEP);
            sb.append(e.delaySec).append(DELAY_SEP).append(path);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
