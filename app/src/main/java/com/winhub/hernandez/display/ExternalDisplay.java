package com.winhub.hernandez.display;

import android.app.ActivityOptions;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;

import com.winhub.hernandez.container.Shortcut;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Launch this game on the TV" — everything the game-shortcut editor, XMB game settings and the
 * launch paths must agree on about a plugged-in external screen.
 *
 * <p><b>Why the session STARTS on the TV rather than being moved there.</b> The compositor's HDR gate
 * is decided from the display the activity's own window is on ({@code XServerDisplayActivity
 * .hdrTargetDisplay()} → {@code getWindowManager().getDefaultDisplay()}). A session started on the
 * handheld therefore asks the handheld panel whether HDR10 is possible and closes the gate there, even
 * with an HDR TV plugged in; started ON the TV it asks the TV. Device-proven: Tetris Effect ran HDR10 on
 * a TCL TV at 51 fps (DX12) / 56 fps (DX11) from a launch aimed at that display, while the same game
 * launched normally stayed SDR because the handheld panel has no HDR. {@link #launchOptions(int)} is the
 * app's version of that: {@link ActivityOptions#setLaunchDisplayId(int)}, which any app may use for a
 * PUBLIC display — a wired HDMI/DP screen is public and trusted, so this needs no root and no permission.
 *
 * <p>This is deliberately NOT the old, switched-off "Play on TV"
 * ({@link ExternalDisplayController}, {@code FeatureFlags.TV_OUTPUT_ENABLED}): that one reparents the
 * X11 view into a {@code Presentation} after the fact, which does nothing for a Wayland session and
 * cannot open the HDR gate. Nothing here touches it.
 *
 * <p><b>Where the settings live.</b> Per game only, as shortcut extras ({@link #EXTRA_LAUNCH},
 * {@link #EXTRA_MODE_ID}, {@link #EXTRA_MATCH_RES}) — there is no container-level default, by request:
 * whether a game belongs on the TV is a property of the game, not of its prefix. HDR is not a setting
 * here at all: it follows the display the session lands on and is only reported.
 *
 * <p>Every method is null-safe and never throws — a missing, odd or vanishing display must never crash
 * a launch or the settings dialog, it must only mean "no TV".
 */
public final class ExternalDisplay {
    private ExternalDisplay() {}

    /** Shortcut extra: "1" = start this game's session on the external display. Default "0". */
    public static final String EXTRA_LAUNCH = "tvLaunch";
    /** Shortcut extra: the {@link Display.Mode} id to ask the TV for; "0" = the display's default. */
    public static final String EXTRA_MODE_ID = "tvModeId";
    /** Shortcut extra: "1" = render at the TV's resolution instead of the game's own screen size. Default "0". */
    public static final String EXTRA_MATCH_RES = "tvMatchRes";

    /** Intent extra: the display id a session was aimed at, so it knows which screen losing it means. */
    public static final String EXTRA_DISPLAY_ID = "tv_display_id";

    /** Shown under the switch in every editor (one line, XMB subtitles are single-line). */
    public static final String HELP_LAUNCH =
            "The game opens on the TV instead of the handheld screen.";
    public static final String HELP_MATCH_RES =
            "Renders at the TV's resolution instead of this game's own. Off by default: more pixels costs frame rate.";
    /** The two things a first-time user has to be told before they pull a cable or reach for the screen. */
    public static final String NOTE_UNPLUG =
            "Pull the cable and the game pauses, then comes back to the handheld screen. Resume it from the drawer.";
    public static final String NOTE_TOUCH =
            "Touch doesn't reach the TV. Use the controller or a mouse, including for the drawer and the HUD.";

    // ── finding the display ──────────────────────────────────────────────────────────────────────────

    /**
     * The external screen to launch on, or null when there is none.
     *
     * <p>Copied (not shared) from {@code ExternalDisplayController.findPresentationDisplay()} so this
     * class stays self-contained while the old feature stays frozen — including its two guards, which
     * were both earned on real devices:
     * <ul>
     *   <li>"HiddenDisplay" is a virtual overlay some OEMs expose; never target it.</li>
     *   <li>Never target the display the caller is ALREADY on. Samsung DeX publishes its virtual
     *       desktop as a DISPLAY_CATEGORY_PRESENTATION display while the app runs on it, so without
     *       this a DeX session would "launch on the TV" onto itself (issue #339). A real USB-C→HDMI
     *       screen has a different display id, so genuine external output is unaffected.</li>
     * </ul>
     */
    public static Display find(Context context) {
        if (context == null) return null;
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) return null;
            int currentId = currentDisplayId(context);
            Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            if (displays == null) return null;
            for (Display d : displays) {
                if (d == null) continue;
                if ("HiddenDisplay".equals(d.getName())) continue;
                if (d.getDisplayId() == currentId) continue;
                return d;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** The display id the caller's own window is on, {@link Display#DEFAULT_DISPLAY} when unknowable. */
    @SuppressWarnings("deprecation")
    public static int currentDisplayId(Context context) {
        if (context == null) return Display.DEFAULT_DISPLAY;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Display d = context.getDisplay();
                if (d != null) return d.getDisplayId();
            }
        } catch (Throwable ignored) {
            // getDisplay() throws on a non-visual context (application context) — fall through.
        }
        try {
            android.view.WindowManager wm =
                    (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) return wm.getDefaultDisplay().getDisplayId();
        } catch (Throwable ignored) {}
        return Display.DEFAULT_DISPLAY;
    }

    /** Is a usable external screen plugged in right now? */
    public static boolean isConnected(Context context) {
        return find(context) != null;
    }

    /** The live {@link Display} for an id, or null when it has gone away. */
    public static Display byId(Context context, int displayId) {
        if (context == null || displayId < 0) return null;
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            return dm != null ? dm.getDisplay(displayId) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ── what to show about it ────────────────────────────────────────────────────────────────────────

    /** The platform's name for the screen ("HDMI Screen", "Wireless display", a DeX desktop…). */
    public static String name(Display display) {
        if (display == null) return "External display";
        try {
            String n = display.getName();
            if (n != null && !n.trim().isEmpty()) return n.trim();
        } catch (Throwable ignored) {}
        return "External display";
    }

    /**
     * The TV's own name from its EDID ("TCL 55S451") when the platform exposes one, else null.
     * {@link Display#getName()} is usually the generic connector name, so the card shows this as the
     * title and the connector name underneath — the way a TV identifies itself on its own input list.
     *
     * <p>Read by reflection (Display.getDeviceProductInfo / DeviceProductInfo.getName, API 29+), the same
     * way {@link DisplayHdrInfo#highestHdrSdrRatio(Display)} reads its Android 16 getter: the method is
     * not in this module's compile SDK stubs, and a display that answers nothing must degrade to the
     * connector name rather than fail to build or throw.
     */
    public static String productName(Display display) {
        if (display == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            Object info = Display.class.getMethod("getDeviceProductInfo").invoke(display);
            if (info == null) return null;
            Object n = info.getClass().getMethod("getName").invoke(info);
            if (!(n instanceof String)) return null;
            String s = ((String) n).trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The card's heading: the TV's own name when it gives one, else the connector name. */
    public static String title(Display display) {
        String product = productName(display);
        return product != null ? product : name(display);
    }

    /** The card's second line: "HDMI Screen · 1920×1080 · 60 Hz" (the connector, then the active mode). */
    public static String summary(Display display) {
        if (display == null) return "";
        String mode = modeLabel(activeMode(display));
        String kind = name(display);
        // When the title already IS the connector name (no EDID product name) don't say it twice.
        if (kind.equals(title(display))) kind = "External display";
        return mode.isEmpty() ? kind : kind + " · " + mode;
    }

    /** The mode the display is running now, or null. */
    public static Display.Mode activeMode(Display display) {
        if (display == null) return null;
        try {
            return display.getMode();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Every output mode the display advertises, newest platform answer, never null. */
    public static List<Display.Mode> supportedModes(Display display) {
        List<Display.Mode> out = new ArrayList<>();
        if (display == null) return out;
        try {
            Display.Mode[] modes = display.getSupportedModes();
            if (modes != null) for (Display.Mode m : modes) if (m != null) out.add(m);
        } catch (Throwable ignored) {}
        if (out.isEmpty()) {
            Display.Mode active = activeMode(display);
            if (active != null) out.add(active);
        }
        return out;
    }

    /**
     * The modes worth OFFERING: {@link #supportedModes(Display)} with duplicate labels dropped.
     *
     * <p>TVs routinely advertise several ids that come out as the same "1920×1080 · 60 Hz" (different
     * colour depths or EDID entries behind the same picture). The pickers select by their label, so
     * leaving those in would make two rows that read identically and pick whichever came first —
     * keeping the first id per distinct label means the list says what it does.
     */
    public static List<Display.Mode> selectableModes(Display display) {
        List<Display.Mode> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (Display.Mode m : supportedModes(display)) {
            if (seen.add(modeLabel(m))) out.add(m);
        }
        return out;
    }

    /** The mode with this id on this display, or null (id 0 = "the display's default", never a match). */
    public static Display.Mode modeById(Display display, int modeId) {
        if (display == null || modeId <= 0) return null;
        for (Display.Mode m : supportedModes(display)) {
            try { if (m.getModeId() == modeId) return m; } catch (Throwable ignored) {}
        }
        return null;
    }

    /** The mode a session will actually run in: the stored choice when the display still has it, else active. */
    public static Display.Mode effectiveMode(Display display, int modeId) {
        Display.Mode chosen = modeById(display, modeId);
        return chosen != null ? chosen : activeMode(display);
    }

    /** "1920×1080 · 60 Hz" (the multiplication sign, matching the approved design), "" for null. */
    public static String modeLabel(Display.Mode mode) {
        if (mode == null) return "";
        try {
            return mode.getPhysicalWidth() + "×" + mode.getPhysicalHeight()
                    + " · " + Math.round(mode.getRefreshRate()) + " Hz";
        } catch (Throwable t) {
            return "";
        }
    }

    /** "1920x1080" for a mode — the encoding {@code ScreenInfo} / the screenSize extra use; "" for null. */
    public static String resolutionOf(Display.Mode mode) {
        if (mode == null) return "";
        try {
            int w = mode.getPhysicalWidth(), h = mode.getPhysicalHeight();
            if (w <= 0 || h <= 0) return "";
            // X servers and Wine desktops want even dimensions (same rule as the resolution picker).
            if ((w & 1) == 1) w--;
            if ((h & 1) == 1) h--;
            return w + "x" + h;
        } catch (Throwable t) {
            return "";
        }
    }

    // ── HDR (reported, never asked) ──────────────────────────────────────────────────────────────────

    /** What the platform says about this display's HDR right now. Never null (see {@link DisplayHdrInfo}). */
    public static DisplayHdrInfo hdr(Display display) {
        return DisplayHdrInfo.read(display);
    }

    /**
     * null when the game will get real HDR on this screen, else the reason it will not — the words the
     * greyed "Use HDR on the TV" row shows. Deliberately the same shape of answer as
     * {@link WaylandHdr#unavailableReason(Display)}, which is what the launch path actually gates on.
     */
    public static String hdrUnavailableReason(Display display) {
        if (display == null) return "No external screen is connected.";
        return WaylandHdr.unavailableReason(display);
    }

    /** "HDR10", "HLG", "peak 500 nits"… — the chips on the display card, in the design's order. */
    public static List<String> hdrChips(Display display) {
        List<String> chips = new ArrayList<>();
        DisplayHdrInfo info = hdr(display);
        if (info.hasHdr && !"unknown".equals(info.formats) && !"none".equals(info.formats)) {
            for (String f : info.formats.split(",")) {
                String t = f.trim();
                if (!t.isEmpty()) chips.add(t);
            }
        } else {
            chips.add("no HDR reported");
        }
        if (info.maxLuminance > 0f) chips.add("peak " + nits(info.maxLuminance));
        return chips;
    }

    /** True for a chip that names an HDR format the display really accepts (drawn in the "on" colour). */
    public static boolean isHdrFormatChip(String chip) {
        return chip != null && !chip.startsWith("peak ") && !chip.equals("no HDR reported");
    }

    private static String nits(float v) {
        if (v < 0f || Float.isNaN(v) || Float.isInfinite(v)) return "unknown";
        return (Math.abs(v - Math.round(v)) < 0.05f)
                ? Math.round(v) + " nits"
                : String.format(Locale.US, "%.1f nits", v);
    }

    // ── the per-game settings ────────────────────────────────────────────────────────────────────────

    /** Does this game want to start on the TV? (The stored setting alone — it says nothing about cables.) */
    public static boolean launchOnTv(Shortcut shortcut) {
        if (shortcut == null) return false;
        return "1".equals(shortcut.getExtra(EXTRA_LAUNCH, "0"));
    }

    /** The stored output-mode id, 0 = the display's default. */
    public static int modeId(Shortcut shortcut) {
        if (shortcut == null) return 0;
        try {
            return Integer.parseInt(shortcut.getExtra(EXTRA_MODE_ID, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Should the session render at the TV's resolution rather than the game's own? Default off. */
    public static boolean matchResolution(Shortcut shortcut) {
        if (shortcut == null) return false;
        return "1".equals(shortcut.getExtra(EXTRA_MATCH_RES, "0"));
    }

    // ── launching ────────────────────────────────────────────────────────────────────────────────────

    /**
     * The {@code startActivity} options bundle that puts the new session on {@code displayId}, or null
     * when the platform refuses to build one (the caller then launches normally).
     *
     * <p>{@link ActivityOptions#setLaunchDisplayId(int)} is allowed for any app on a PUBLIC display, and
     * a plugged-in HDMI/DP screen is public and trusted — no root, no extra permission. The system can
     * still decline (a private/untrusted display, a vendor policy), which is why every caller falls back.
     */
    public static Bundle launchOptions(int displayId) {
        if (displayId < 0) return null;
        try {
            return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle();
        } catch (Throwable t) {
            return null;
        }
    }
}
