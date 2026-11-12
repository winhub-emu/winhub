package com.winhub.hernandez.core;

import android.content.Context;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.contents.ContentsManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Detects whether a wine/Proton layer can drive the embedded Wayland compositor (the per-container
 * "Display backend = Wayland" choice and the per-game "Force Wayland" override).
 *
 * A layer is Wayland-capable iff its install dir contains
 *   - lib/wine/aarch64-unix/winewayland.so (x86_64 layers: lib/wine/x86_64-unix/winewayland.so).
 * The layer's own Wayland Turnip (lib/libvulkan_freedreno_wayland.so) is no longer required: the
 * bundled Wayland adapter (core.WaylandAdapter) is the default game driver and loads the container's
 * own AdrenoTools driver, so a layer without its own Turnip still runs. The Turnip is only needed by
 * the bundled-variant choices under the game driver's Advanced section.
 * The bundled main wine ({@link WineInfo#MAIN_WINE_VERSION}) is never capable.
 *
 * Both the editors (container, shortcut, XMB) and the launch path gate on this so a container or
 * game can never be left believing it is on Wayland with a layer that cannot do it. Result cached per
 * layer identifier; conservative: a missing dir or file counts as NOT capable.
 */
public final class WineWaylandSupport {
    private WineWaylandSupport() {}

    /** Shared help text: which layer provides Wayland and where to get it. */
    public static final String LAYER_HINT =
            "the Wayland Proton layer (Proton 11.0-2.1 arm64ec, wcp from the Wayland test kit; " +
            "install it on the Contents screen with Install from file)";

    private static final Map<String, Boolean> cache = new HashMap<>();

    /** Capability for the layer described by an already-resolved {@link WineInfo}. */
    public static boolean isWaylandCapable(WineInfo wineInfo) {
        if (wineInfo == null || wineInfo.path == null || wineInfo.path.isEmpty()) return false;
        if (WineInfo.isMainWineVersion(wineInfo.identifier())) return false;
        return isWaylandCapable(wineInfo.identifier(), wineInfo.path);
    }

    /** Capability for a wine version identifier (resolves its install dir via WineInfo). */
    public static boolean isWaylandCapable(Context context, ContentsManager contentsManager, String identifier) {
        if (identifier == null || identifier.isEmpty() || WineInfo.isMainWineVersion(identifier)) return false;
        synchronized (cache) {
            Boolean cached = cache.get(identifier);
            if (cached != null) return cached;
        }
        try {
            return isWaylandCapable(WineInfo.fromIdentifier(context, contentsManager, identifier));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Convenience for callers without a ContentsManager at hand (shortcut editor, XMB settings). The
     * cache is consulted first so the manager (and its directory scan) is only built on a miss.
     */
    public static boolean isWaylandCapable(Context context, String identifier) {
        if (identifier == null || identifier.isEmpty() || WineInfo.isMainWineVersion(identifier)) return false;
        synchronized (cache) {
            Boolean cached = cache.get(identifier);
            if (cached != null) return cached;
        }
        try {
            ContentsManager contentsManager = new ContentsManager(context);
            contentsManager.syncContents();
            return isWaylandCapable(context, contentsManager, identifier);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Does this CONTAINER effectively run on Wayland? It selected the Wayland backend AND its layer
     * can drive it. Same rule the container editor shows (ContainerDetailViewModel.isWaylandBackend)
     * and the launch path enforces — kept here in one place so read-only surfaces (the container and
     * game cards) can't drift from the editors and print the X11 setting as if it were fact.
     */
    public static boolean runsOnWayland(Context context, Container container) {
        if (container == null) return false;
        return container.isWaylandBackend() && isWaylandCapable(context, container.getWineVersion());
    }

    /**
     * Does this SHORTCUT effectively run on Wayland? Its own "displayBackend" override decides, else
     * the container's choice, gated by the same layer capability — mirrors the shortcut editor's
     * {@code effectiveWaylandShortcut} and XmbGameSettings' {@code waylandGame}.
     */
    public static boolean runsOnWayland(Context context, Shortcut shortcut) {
        if (shortcut == null) return false;
        Container container = shortcut.container;
        if (container == null) return false;
        String backend = shortcut.getExtra("displayBackend", "");
        if (backend.isEmpty()) return runsOnWayland(context, container);
        return Container.DISPLAY_BACKEND_WAYLAND.equals(backend)
                && isWaylandCapable(context, container.getWineVersion());
    }

    /** Drop every cached verdict — call after a layer is installed or removed. */
    public static void invalidate() {
        synchronized (cache) { cache.clear(); }
    }

    /**
     * Why this layer cannot run Wayland, in words for the editors; null when it can. Names the folder
     * that was checked, so a layer installed somewhere unexpected is easy to spot.
     */
    public static String problem(Context context, ContentsManager contentsManager, String identifier) {
        if (identifier == null || identifier.isEmpty()) return "No Wine/Proton layer is selected.";
        if (WineInfo.isMainWineVersion(identifier)) return "The bundled Wine cannot run Wayland; pick the Wayland Proton layer.";
        try {
            WineInfo info = WineInfo.fromIdentifier(context, contentsManager, identifier);
            if (info == null || info.path == null || info.path.isEmpty())
                return "\"" + identifier + "\" is not installed (no folder found for it).";
            File a = new File(info.path, "lib/wine/aarch64-unix/winewayland.so");
            File b = new File(info.path, "lib/wine/x86_64-unix/winewayland.so");
            if (a.isFile() || b.isFile()) return null;
            return "winewayland.so is missing in " + info.path + "/lib/wine/aarch64-unix (or x86_64-unix).";
        } catch (Exception e) {
            return "The layer could not be read: " + e.getMessage();
        }
    }

    private static boolean isWaylandCapable(String cacheKey, String layerPath) {
        synchronized (cache) {
            Boolean cached = cache.get(cacheKey);
            if (cached != null) return cached;
        }
        // arm64ec ships aarch64-unix; x86_64 ships x86_64-unix. Try both.
        File winewayland = new File(layerPath, "lib/wine/aarch64-unix/winewayland.so");
        if (!winewayland.isFile()) winewayland = new File(layerPath, "lib/wine/x86_64-unix/winewayland.so");

        boolean capable = winewayland.isFile();
        synchronized (cache) { cache.put(cacheKey, capable); }
        return capable;
    }
}
