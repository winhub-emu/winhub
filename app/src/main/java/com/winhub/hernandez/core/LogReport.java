package com.winhub.hernandez.core;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a bug report out of one game's logs: a redacted zip the user can attach, plus the issue
 * body text that goes with it.
 *
 * Why this is two steps rather than one. GitHub has no API for attaching a file to an issue — the
 * only upload path is the web UI's own, tied to a signed-in session. A URL can prefill the title
 * and body and nothing else. So the app writes the zip somewhere the browser's file picker can
 * reach, prefills everything it knows, and the attach itself is one tap in the GitHub form.
 *
 * Everything in the zip goes through {@link LogcatCapture#redact}, line by line. Be precise about
 * what that buys, because this bundle is destined for a PUBLIC tracker and the wording we show the
 * user has to match the code: it strips e-mail addresses, token-shaped blobs, SteamID64s and any
 * secret {@code SteamRepository} registered — which is the signed-in account name and refresh
 * token, and nothing when no one has signed in. It does NOT strip a person's name out of a file
 * path, and deliberately so; paths are usually what makes a log diagnosable. The user-facing note
 * this class emits says exactly that rather than promising "usernames" wholesale.
 *
 * What goes IN the zip is decided by {@link LogInventory#filesIn}, which is allowlist-driven. It
 * must stay that way: the log root can be a folder the user chose, full of files that are none of
 * our business.
 */
public final class LogReport {

    private static final String TAG = "LogReport";
    private static final String REPO = "winhub-emu/winhub";
    /**
     * Per-file cap. A {@code +seh} Wine log runs to tens of MB, but it is also extremely uniform,
     * so it compresses at roughly 140:1 in the zip — 8 MB of it lands as tens of KB. Being stingy
     * here costs diagnosis and saves nothing.
     */
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    /** How much of an over-cap file is taken from the front: config header + startup sequence. */
    private static final long HEAD_BYTES = 1L * 1024 * 1024;
    /** GitHub prefills through a GET; a body far past this starts getting refused by browsers. */
    private static final int MAX_BODY_CHARS = 6000;

    private LogReport() {}

    /** What was built: the zip on disk, and what went into it. */
    public static final class Bundle {
        public final File zip;
        public final List<String> included;
        public final String facts;   // markdown block: device, app, and whatever the logs revealed

        Bundle(File zip, List<String> included, String facts) {
            this.zip = zip;
            this.included = included;
            this.facts = facts;
        }
    }

    /**
     * Zip up a run's logs, redacted, into public Downloads so the browser's picker can see them.
     *
     * @param runDir     the run to report — current, or one of the archived launches
     * @param includeApp also attach the app logcat and any crash reports
     */
    public static Bundle build(Context context, LogInventory.Entry entry, File runDir, boolean includeApp) {
        File reports = new File(Environment.getExternalStorageDirectory(), "Download/bannerlator/reports");
        //noinspection ResultOfMethodCallIgnored
        reports.mkdirs();

        String stem = LogLocation.sanitizeFolderName(entry.isAppBucket ? "app" : entry.name);
        File zip = new File(reports, stem + "-" + LogcatCapture.timestamp() + ".zip");

        List<String> included = new ArrayList<>();
        StringBuilder scanned = new StringBuilder();

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (File f : LogInventory.filesIn(runDir)) {
                String text = readContentRedacted(f);
                addEntry(out, stem + "/" + f.getName(), text);
                included.add(f.getName());
                scanned.append(text.length() > 8000 ? text.substring(0, 8000) : text).append('\n');
            }
            if (includeApp) {
                File appDir = LogLocation.resolveAppLogDir(context);
                if (appDir != null && !appDir.equals(runDir)) {
                    File[] appFiles = appDir.listFiles(f -> f.isFile()
                            && (f.getName().equals("logcat.log")
                                || f.getName().startsWith(CrashReporter.PREFIX)));
                    if (appFiles != null) {
                        for (File f : appFiles) {
                            addEntry(out, "_app/" + f.getName(), readContentRedacted(f));
                            included.add(f.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "could not build report bundle", e);
            return null;
        }

        return new Bundle(zip, included, facts(context, scanned.toString(), included, zip));
    }

    /** The markdown block that goes into the issue body. */
    private static String facts(Context context, String scanned, List<String> included, File zip) {
        StringBuilder b = new StringBuilder();
        b.append("### System\n\n```\n").append(LogcatCapture.deviceHeader(context)).append("```\n\n");

        // These all have to be anchored to the shape the writing layer actually uses. An unanchored
        // "Device *: *(.+)" matched our OWN logcat header ("Device: AYANEO AYANEO Pocket FIT") and
        // reported the handheld as the GPU; an unanchored "DXVK: *v?" matched "DXVK: Read 46 valid
        // state cache entries" and reported the version as "Read".
        //   DXVK   "info:    Device : Adreno (TM) 750" / "info:    Driver : turnip Mesa driver …"
        //          "info:  DXVK: v3.0-gplasync"
        //   VKD3D  "0144:info:vkd3d_get_vk_version: vkd3d-proton - applicationVersion: 3.0.1."
        String gpu = firstMatch(scanned, "(?m)^info: +Device *: *(\\S.*?) *$");
        String driver = firstMatch(scanned, "(?m)^info: +Driver *: *(\\S.*?) *$");
        String dxvk = firstMatch(scanned, "(?m)^info: +DXVK: *v([\\w.\\-]+)");
        // (?m) is load-bearing and was missing here while the three above had it: without it, `$`
        // anchors to the end of the ENTIRE concatenated scan buffer rather than end-of-line, so a
        // version sitting on line 4 of vkd3d-proton.log could never match. Issues #191 and #192
        // both went out with no VKD3D line despite the log being attached and holding the version
        // — and on a D3D12 title like PRAGMATA that is the single most relevant field.
        String vkd3d = firstMatch(scanned,
                "(?m)^.*vkd3d-proton *-? *applicationVersion: *([\\d][\\w.\\-]*?)\\.? *$");

        // No DXVK log in the bundle (a native-Vulkan or wined3d run, or an app-only report) still
        // deserves a real GPU line — ask the driver directly rather than leaving it out.
        if (gpu == null) {
            try {
                gpu = GPUInformation.getRenderer(null, null);
            } catch (Throwable ignored) {
            }
        }

        // Normalize whichever source won. DXVK reports the driver's raw device string, which on a
        // wrapper container reads "Wrapper(Adreno (TM) 750)" — a GPU line should name the GPU, and
        // the wrapper is already evident from the Driver line right below it.
        if (gpu != null) {
            try {
                String model = GPUInformation.extractModelName(gpu);
                if (model != null && !model.trim().isEmpty()) gpu = model.trim();
            } catch (Throwable ignored) {
            }
            if (gpu.trim().isEmpty()) gpu = null;
        }
        if (gpu != null || driver != null || dxvk != null || vkd3d != null) {
            b.append("### From the logs\n\n");
            if (gpu != null) b.append("- GPU: `").append(gpu).append("`\n");
            if (driver != null) b.append("- Driver: `").append(driver).append("`\n");
            if (dxvk != null) b.append("- DXVK: `").append(dxvk).append("`\n");
            if (vkd3d != null) b.append("- VKD3D: `").append(vkd3d).append("`\n");
            b.append('\n');
        }

        b.append("### Attached\n\n");
        for (String name : included) b.append("- `").append(name).append("`\n");
        // Say what the redactor ACTUALLY does. The old wording promised "usernames" — but the only
        // username it can strip is the signed-in Steam account name, and only once SteamRepository
        // has registered it; a Windows or Android path with a person's name in it is untouched,
        // deliberately, because those paths are usually the thing that makes a log diagnosable.
        // Overstating this on a public tracker is worse than saying nothing.
        b.append("\n_Scrubbed as they are written: e-mail addresses, auth tokens, and the " +
                "signed-in Steam account name. File paths are left intact so they stay useful — " +
                "give them a glance if one of your folder names identifies you. " +
                "Attach the zip below — it is at `Download/bannerlator/reports/")
         .append(zip.getName()).append("`._\n");
        return b.toString();
    }

    /** github.com/…/issues/new with the title and body prefilled. */
    public static String issueUrl(String title, String description, String facts) {
        StringBuilder body = new StringBuilder();
        if (description != null && !description.trim().isEmpty()) {
            body.append("### What happened\n\n").append(description.trim()).append("\n\n");
        }
        body.append(facts);
        String text = body.toString();
        if (text.length() > MAX_BODY_CHARS) text = text.substring(0, MAX_BODY_CHARS) + "\n…";

        return "https://github.com/" + REPO + "/issues/new"
                + "?title=" + Uri.encode(title == null || title.trim().isEmpty() ? "Bug report" : title.trim())
                + "&body=" + Uri.encode(text);
    }

    private static void addEntry(ZipOutputStream out, String name, String text) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes());
        out.closeEntry();
    }

    /**
     * A file's content for the bundle: redacted, and trimmed from the MIDDLE if it is huge.
     *
     * This used to keep the tail alone, which lost the two things a triager reads first. Issue #191
     * is the proof: a {@code +seh} run produced a 72 MB wine_debug.log, the tail-2 MB rule threw
     * away 97% of it, and what survived was ~12,900 copies of one repeated trace line. Gone with
     * the head were the {@code WINEDEBUG}/{@code WINEPREFIX}/container/shortcut fields that
     * XServerDisplayActivity writes at the top precisely so a report explains its own setup.
     *
     * So: keep the head AND the tail. The head carries the configuration and the startup sequence,
     * the tail carries the failure. What goes missing is the repetitive middle, which is where the
     * bulk lives and the least information is.
     *
     * The cap is also far more generous than it was, because the old one bought nothing: #191's
     * 2 MB of repeated text compressed to a 15 KB zip entry, about 140:1. Log text this uniform
     * costs almost nothing to carry.
     */
    private static String readContentRedacted(File f) {
        try {
            long len = f.length();
            if (len <= MAX_FILE_BYTES) return redactByLine(readRange(f, 0, (int) len));

            String head = readRange(f, 0, (int) HEAD_BYTES);
            String tail = readRange(f, len - (MAX_FILE_BYTES - HEAD_BYTES),
                    (int) (MAX_FILE_BYTES - HEAD_BYTES));
            // Both cuts land mid-line; drop the partial so neither section starts or ends ragged.
            int lastNl = head.lastIndexOf('\n');
            if (lastNl >= 0) head = head.substring(0, lastNl + 1);
            int firstNl = tail.indexOf('\n');
            if (firstNl >= 0) tail = tail.substring(firstNl + 1);

            String note = String.format(Locale.US,
                    "\n[… %d KB of %d KB omitted from the middle — this report keeps the first "
                            + "%d KB and the last %d KB …]\n\n",
                    (len - MAX_FILE_BYTES) / 1024, len / 1024,
                    HEAD_BYTES / 1024, (MAX_FILE_BYTES - HEAD_BYTES) / 1024);
            return redactByLine(head) + note + redactByLine(tail);
        } catch (Throwable t) {
            // Throwable, not Exception: the cap is 8 MB and redacting builds a second copy, so a
            // low-RAM device can OOM here. That must produce a report with one explanatory entry
            // in it, not take the app down — same principle as the rest of this feature, where
            // logging is never allowed to break the thing it is logging.
            return "(could not read " + f.getName() + ": " + t + ")";
        }
    }

    private static String readRange(File f, long from, int count) throws Exception {
        byte[] bytes = new byte[Math.max(count, 0)];
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(Math.max(from, 0));
            raf.readFully(bytes);
        }
        return new String(bytes);
    }

    /**
     * Redact line by line, never as one blob.
     *
     * {@link LogcatCapture#redact} answers a failure by returning "[line withheld]" — correct for
     * the per-line caller it was written for, catastrophic when handed a whole file, because one
     * throw would collapse megabytes of log into a single sentence and the report would look
     * complete. Splitting first bounds the blast radius of a failure to the line that caused it,
     * and it is what LogcatCapture's own comment argues for on speed grounds anyway.
     */
    private static String redactByLine(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 64);
        int start = 0;
        while (start <= text.length()) {
            int nl = text.indexOf('\n', start);
            int end = nl < 0 ? text.length() : nl;
            sb.append(LogcatCapture.redact(text.substring(start, end)));
            if (nl < 0) break;
            sb.append('\n');
            start = nl + 1;
        }
        return sb.toString();
    }

    private static String firstMatch(String haystack, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(haystack);
            if (m.find()) {
                String s = m.group(1).trim();
                return s.length() > 80 ? s.substring(0, 80) : s;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
