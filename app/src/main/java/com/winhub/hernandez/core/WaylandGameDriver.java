package com.winhub.hernandez.core;

import android.content.Context;
import android.util.Log;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.contents.WaylandGameDriverManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Which Vulkan driver the GAME renders on when the display backend is Wayland. On Wayland
 * winewayland sets VK_ICD_FILENAMES itself, so the compositor's Turnip (the "Compositor driver"
 * picker) never reaches the game; the Proton layer bundles three Wayland Turnip variants and
 * honours two env vars, which this class emits:
 * <ul>
 *   <li>{@link #ENV_VARIANT} {@code = a7xx | a8xx | a8xx-perf | a8xx-gen8 | a8xx-smxz | a8xx-white | a8xx-upstream} — pick a bundled variant; unset (or any other
 *       value) = the plain bundled driver. plain covers Adreno 6xx + 730/740/750; a7xx covers
 *       710/720/722; a8xx covers 830/840.</li>
 *   <li>{@link #ENV_ICD} {@code = /abs/path/icd.json} — an IMPORTED Wayland-built driver
 *       ({@link WaylandGameDriverManager}); when set it wins over the variant.</li>
 * </ul>
 * The stored choice is {@link Container#getWaylandGameDriver()} (extra {@code waylandGameDriver}),
 * overridable per shortcut by the same-named extra ("" = container default). Values:
 * {@code adapter} (new containers) · {@code auto} (no value stored = every older container) ·
 * {@code bundled} · {@code bundled-a7xx} · {@code bundled-a8xx…} · {@code imported:<id>}. Only
 * consulted when the launch resolved to Wayland.
 * <p>
 * {@code adapter} = the bundled {@link WaylandAdapter} as the game's ICD ({@link #ENV_ICD}); it
 * runs on the container's own AdrenoTools graphics driver, so one pick drives compositor and game.
 * It needs a Mesa AdrenoTools driver underneath ({@link WaylandAdapter#unusableReason}); on
 * "System", a Qualcomm blob or a missing driver it resolves to a bundled variant by GPU instead
 * (logged), except "System" on an Arm Mali GPU, where it runs on the phone's own driver
 * ({@link WaylandAdapter#isMaliSystem}). {@code auto} resolves exactly like {@code adapter} once the launch knows the graphics
 * driver, so every container that never chose is on the adapter too — at resolution time only, the
 * stored value is never rewritten.
 */
public final class WaylandGameDriver {
    private WaylandGameDriver() {}

    private static final String TAG = "WaylandGameDriver";

    public static final String ENV_VARIANT = "BANNER_WAYLAND_VK_VARIANT";
    public static final String ENV_ICD = "BANNER_WAYLAND_VK_ICD";

    /** Variant tokens as the Proton expects them; "" = plain (env var left unset). */
    public static final String VARIANT_PLAIN = "";
    public static final String VARIANT_A7XX = "a7xx";
    public static final String VARIANT_A8XX = "a8xx";
    public static final String VARIANT_A8XX_PERF = "a8xx-perf"; // WinNative "Performance" tuning: KGSL PWR_MAX held
    public static final String VARIANT_A8XX_GEN8 = "a8xx-gen8"; // Banners-Turnip gen8 recipe (a8xx_gen8.patch + shared_mem)
    public static final String VARIANT_A8XX_SMXZ = "a8xx-smxz"; // StevenMXZ Gen8 recipe
    public static final String VARIANT_A8XX_WHITE = "a8xx-white"; // whitebelyash Mainline recipe
    public static final String VARIANT_A8XX_UPSTREAM = "a8xx-upstream"; // pure Mesa main, newest pin, no device patches

    /** Shared editor help text (container, shortcut and XMB editors show the same line). */
    public static final String HELP_TEXT =
            "The Vulkan driver the game renders on when the display backend is Wayland. Adapter = your " +
            "graphics driver above, through the app's Wayland adapter (one pick for everything; the normal " +
            "path). Bundled = the Wayland Turnips inside the Proton, by GPU. Imported drivers must be " +
            "Wayland/Linux builds (a Turnip zip made for Android will not work here).";

    /** The one sentence over the fallback choices in the editors' expander. */
    public static final String FALLBACK_HINT =
            "These are a fallback for a game the adapter cannot run on your graphics driver; the adapter is " +
            "the normal path and is used automatically when nothing else is chosen.";

    /** The editors' expander title for the fallback choices. */
    public static final String FALLBACK_EXPANDER = "Advanced: use a layer-bundled or imported Wayland driver instead";

    /** Editor label of the adapter choice. */
    public static final String ADAPTER_LABEL = "Adapter (uses your graphics driver)";

    /** The launch-time decision. */
    public static final class Resolution {
        public final String choice;   // the stored choice this was resolved from (after any fallback)
        public final String variant;  // VARIANT_* ("" = plain / env unset)
        public final String icdPath;  // absolute icd.json path, or null
        public final boolean adapter; // icdPath is the bundled Wayland adapter's
        Resolution(String choice, String variant, String icdPath) {
            this(choice, variant, icdPath, false);
        }
        Resolution(String choice, String variant, String icdPath, boolean adapter) {
            this.choice = choice; this.variant = variant; this.icdPath = icdPath; this.adapter = adapter;
        }
    }

    // ── GPU → variant ────────────────────────────────────────────────────────────────────────────

    /** Variant for a raw renderer string; a non-Adreno or unparseable GPU maps to plain. */
    public static String variantForRenderer(String renderer) {
        String model = GPUInformation.extractModelName(renderer);
        if (model == null) return VARIANT_PLAIN;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)Adreno\\s*(\\d+)").matcher(model);
        if (!m.find()) return VARIANT_PLAIN;
        String n = m.group(1);
        switch (n) {
            case "710": case "720": case "722":
                return VARIANT_A7XX;
            default:
                // Any Adreno 8xx (830, 840, …): the a8xx build.
                return n.length() == 3 && n.charAt(0) == '8' ? VARIANT_A8XX : VARIANT_PLAIN;
        }
    }

    private static String cachedAutoVariant = null;

    /**
     * The variant Auto resolves to on this device. The first call runs the native renderer probe
     * (GPUInformation.getRenderer) — call it off the main thread (the editors do, under
     * graphicsProbeMutex); the answer is cached for the process since the GPU can't change.
     */
    public static synchronized String autoVariant(Context context) {
        if (cachedAutoVariant == null) {
            String renderer;
            try { renderer = GPUInformation.getRenderer(null, context); }
            catch (Throwable t) { renderer = ""; }
            cachedAutoVariant = variantForRenderer(renderer);
        }
        return cachedAutoVariant;
    }

    /** The cached Auto answer, or null when no probe has run yet (editors show "Auto (by GPU)"). */
    public static synchronized String autoVariantIfKnown() {
        return cachedAutoVariant;
    }

    // ── Launch resolution ────────────────────────────────────────────────────────────────────────

    /** The shortcut's override when set, else the container's stored choice. */
    public static String effectiveChoice(Container container, Shortcut shortcut) {
        String v = shortcut != null ? shortcut.getExtra("waylandGameDriver", "") : "";
        if (v == null || v.isEmpty()) v = container.getWaylandGameDriver();
        return v == null || v.isEmpty() ? Container.WAYLAND_GAME_DRIVER_AUTO : v;
    }

    public static boolean isImported(String choice) {
        return choice != null && choice.startsWith(Container.WAYLAND_GAME_DRIVER_IMPORTED_PREFIX);
    }

    public static String importedId(String choice) {
        return isImported(choice) ? choice.substring(Container.WAYLAND_GAME_DRIVER_IMPORTED_PREFIX.length()) : null;
    }

    /**
     * Resolve a stored choice. An {@code imported:<id>} whose import is gone falls back to Auto
     * (logged); unknown tokens are treated as Auto too, so a stale/typo'd extra can never hide the
     * game behind a missing driver.
     */
    public static Resolution resolve(Context context, String choice) {
        return resolve(context, choice, null);
    }

    /**
     * {@link #resolve(Context, String)} with the effective AdrenoTools graphics driver id, which the
     * {@code adapter} choice needs (null = unknown: adapter resolves to Auto). The adapter resolves
     * to Auto, logged, when it can't sit on that driver or the APK's adapter can't be installed.
     */
    public static Resolution resolve(Context context, String choice, String graphicsDriverId) {
        if (choice == null || choice.isEmpty()) choice = Container.WAYLAND_GAME_DRIVER_AUTO;
        // Auto (= every container that never chose) is the adapter too whenever the launch knows the
        // graphics driver and it is a Turnip; only when the adapter cannot be used does Auto keep its old
        // meaning, a bundled Wayland Turnip by GPU. Resolution-time only: nothing stored is rewritten.
        boolean autoChoice = Container.WAYLAND_GAME_DRIVER_AUTO.equals(choice);
        if (Container.WAYLAND_GAME_DRIVER_ADAPTER.equals(choice) || (autoChoice && graphicsDriverId != null)) {
            String why = graphicsDriverId == null ? "the graphics driver is unknown"
                    : WaylandAdapter.unusableReason(context, graphicsDriverId);
            String icd = why == null ? WaylandAdapter.ensureInstalled(context) : null;
            if (icd != null) return new Resolution(Container.WAYLAND_GAME_DRIVER_ADAPTER, VARIANT_PLAIN, icd, true);
            if (why == null) why = "the bundled adapter could not be installed";
            Log.w(TAG, "Wayland adapter not used" + (autoChoice ? " (auto)" : "") + ": " + why
                    + "; falling back to a bundled Wayland Turnip by GPU");
            choice = Container.WAYLAND_GAME_DRIVER_AUTO;
        }
        if (isImported(choice)) {
            String id = importedId(choice);
            String icd = new WaylandGameDriverManager(context).getIcdPath(id);
            if (icd != null) return new Resolution(choice, VARIANT_PLAIN, icd);
            Log.w(TAG, "imported Wayland game driver '" + id + "' is not installed; falling back to auto");
            choice = Container.WAYLAND_GAME_DRIVER_AUTO;
        }
        switch (choice) {
            case Container.WAYLAND_GAME_DRIVER_BUNDLED:      return new Resolution(choice, VARIANT_PLAIN, null);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A7XX: return new Resolution(choice, VARIANT_A7XX, null);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX: return new Resolution(choice, VARIANT_A8XX, null);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_PERF: return new Resolution(choice, VARIANT_A8XX_PERF, null);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_GEN8: return new Resolution(choice, VARIANT_A8XX_GEN8, null);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_SMXZ: return new Resolution(choice, VARIANT_A8XX_SMXZ, null);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_WHITE: return new Resolution(choice, VARIANT_A8XX_WHITE, null);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_UPSTREAM: return new Resolution(choice, VARIANT_A8XX_UPSTREAM, null);
            case Container.WAYLAND_GAME_DRIVER_AUTO:         break;
            default:
                Log.w(TAG, "unknown waylandGameDriver '" + choice + "'; treating as auto");
                choice = Container.WAYLAND_GAME_DRIVER_AUTO;
        }
        return new Resolution(choice, autoVariant(context), null);
    }

    /**
     * Resolve for this launch and export the env vars (only when {@code waylandMode}); logs the
     * decision on one line. Removes both vars first so a stale value from the container/shortcut
     * env editor can't linger next to the resolved one.
     */
    public static void applyToLaunchEnv(Context context, EnvVars envVars, Container container,
                                        Shortcut shortcut, boolean waylandMode, String graphicsDriverId) {
        if (!waylandMode) return;
        Resolution r = resolve(context, effectiveChoice(container, shortcut), graphicsDriverId);
        envVars.remove(ENV_VARIANT);
        envVars.remove(ENV_ICD);
        if (r.icdPath != null) envVars.put(ENV_ICD, r.icdPath);
        else if (!r.variant.isEmpty()) envVars.put(ENV_VARIANT, r.variant);
        Log.i(TAG, "wayland game driver: " + r.choice + " → variant=" + (r.variant.isEmpty() ? "plain" : r.variant)
                + " icd=" + (r.icdPath != null ? r.icdPath : "none")
                + (r.adapter ? " (adapter " + WaylandAdapter.version(context) + " on " + graphicsDriverId + ")" : ""));
    }

    // ── Editor options (shared by the container, shortcut and XMB editors) ───────────────────────

    public static String variantLabel(String variant) {
        if (VARIANT_A7XX.equals(variant)) return "Bundled a7xx (Adreno 710/720/722)";
        if (VARIANT_A8XX.equals(variant)) return "Bundled a8xx (Adreno 830/840, WinNative Balanced)";
        if (VARIANT_A8XX_PERF.equals(variant)) return "Bundled a8xx Performance (Adreno 830/840, WinNative PWR_MAX)";
        if (VARIANT_A8XX_GEN8.equals(variant)) return "Bundled a8xx gen8 (Adreno 830/840, Banners-Turnip gen8 recipe)";
        if (VARIANT_A8XX_SMXZ.equals(variant)) return "Bundled a8xx SMXZ (Adreno 830/840, StevenMXZ Gen8 V36 recipe)";
        if (VARIANT_A8XX_WHITE.equals(variant)) return "Bundled a8xx WHITE (Adreno 830/840, whitebelyash Mainline v31 recipe)";
        if (VARIANT_A8XX_UPSTREAM.equals(variant)) return "Bundled a8xx upstream (Adreno 830/840, pure Mesa main, no patches)";
        return "Bundled (Adreno 6xx / 730–750)";
    }

    /** Short variant name for the "Auto (by GPU: …)" label. */
    public static String variantShortName(String variant) {
        if (VARIANT_A7XX.equals(variant)) return "Bundled a7xx";
        if (VARIANT_A8XX.equals(variant)) return "Bundled a8xx";
        if (VARIANT_A8XX_PERF.equals(variant)) return "Bundled a8xx Performance";
        if (VARIANT_A8XX_GEN8.equals(variant)) return "Bundled a8xx gen8";
        if (VARIANT_A8XX_SMXZ.equals(variant)) return "Bundled a8xx SMXZ";
        if (VARIANT_A8XX_WHITE.equals(variant)) return "Bundled a8xx WHITE";
        if (VARIANT_A8XX_UPSTREAM.equals(variant)) return "Bundled a8xx upstream";
        return "Bundled";
    }

    /**
     * Stored values in editor order: adapter (or, in an APK without one, Auto = bundled by GPU), the eight
     * bundled variants, then each imported driver. Auto and adapter resolve the same way whenever the APK
     * carries the adapter ({@link #resolve}), so the editors list one of them: see {@link #editorChoice}.
     */
    public static List<String> optionValues(Context context) {
        ArrayList<String> values = new ArrayList<>();
        values.add(WaylandAdapter.isBundled(context) ? Container.WAYLAND_GAME_DRIVER_ADAPTER : Container.WAYLAND_GAME_DRIVER_AUTO);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED_A7XX);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_PERF);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_GEN8);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_SMXZ);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_WHITE);
        values.add(Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_UPSTREAM);
        for (String id : new WaylandGameDriverManager(context).enumerateInstalledDrivers())
            values.add(Container.WAYLAND_GAME_DRIVER_IMPORTED_PREFIX + id);
        return values;
    }

    /**
     * Label for a stored value. {@code autoVariant} is the cached Auto answer (null = not probed
     * yet). An {@code imported:<id>} that is no longer installed says so — the launch path falls
     * back to Auto for it.
     */
    public static String optionLabel(Context context, String value, String autoVariant) {
        if (value == null || value.isEmpty() || Container.WAYLAND_GAME_DRIVER_AUTO.equals(value)) {
            // Auto is the adapter on a Turnip; the bundled-by-GPU name is what it falls back to.
            if (WaylandAdapter.isBundled(context)) return ADAPTER_LABEL;
            return autoVariant == null ? "Auto (by GPU)" : "Auto (by GPU: " + variantShortName(autoVariant) + ")";
        }
        if (Container.WAYLAND_GAME_DRIVER_ADAPTER.equals(value)) return ADAPTER_LABEL;
        switch (value) {
            case Container.WAYLAND_GAME_DRIVER_BUNDLED:      return variantLabel(VARIANT_PLAIN);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A7XX: return variantLabel(VARIANT_A7XX);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX: return variantLabel(VARIANT_A8XX);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_PERF: return variantLabel(VARIANT_A8XX_PERF);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_GEN8: return variantLabel(VARIANT_A8XX_GEN8);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_SMXZ: return variantLabel(VARIANT_A8XX_SMXZ);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_WHITE: return variantLabel(VARIANT_A8XX_WHITE);
            case Container.WAYLAND_GAME_DRIVER_BUNDLED_A8XX_UPSTREAM: return variantLabel(VARIANT_A8XX_UPSTREAM);
        }
        if (isImported(value)) {
            String id = importedId(value);
            WaylandGameDriverManager m = new WaylandGameDriverManager(context);
            if (!m.isInstalled(id)) return id + " (imported, missing — uses Auto)";
            String ver = m.getDriverVersion(id);
            return m.getDriverName(id) + (ver.isEmpty() ? "" : " " + ver) + " (imported)";
        }
        return value;
    }

    /** True for the stored values that resolve to the adapter first: adapter, auto and "" (never chosen). */
    public static boolean isAdapterChoice(String choice) {
        return choice == null || choice.isEmpty()
                || Container.WAYLAND_GAME_DRIVER_ADAPTER.equals(choice)
                || Container.WAYLAND_GAME_DRIVER_AUTO.equals(choice);
    }

    /**
     * The entry of {@link #optionValues} an editor selects for a stored value: auto/"" show as the adapter
     * when the APK carries one (they resolve to it), everything else as itself.
     */
    public static String editorChoice(Context context, String stored) {
        if (isAdapterChoice(stored))
            return WaylandAdapter.isBundled(context) ? Container.WAYLAND_GAME_DRIVER_ADAPTER : Container.WAYLAND_GAME_DRIVER_AUTO;
        return stored;
    }

    /** True when {@code choice} resolves to the adapter AND it can sit on {@code graphicsDriverId} (editor hint). */
    public static boolean adapterActive(Context context, String choice, String graphicsDriverId) {
        return isAdapterChoice(choice)
                && WaylandAdapter.isBundled(context)
                && WaylandAdapter.unusableReason(context, graphicsDriverId) == null;
    }
}
