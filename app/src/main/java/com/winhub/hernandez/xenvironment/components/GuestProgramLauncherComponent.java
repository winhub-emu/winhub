package com.winhub.hernandez.xenvironment.components;

import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Process;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winhub.hernandez.BuildConfig;
import com.winhub.hernandez.box64.Box64Preset;
import com.winhub.hernandez.box64.Box64PresetManager;
import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.ContainerOverlay;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.contents.ContentProfile;
import com.winhub.hernandez.contents.ContentsManager;
import com.winhub.hernandez.core.Callback;
import com.winhub.hernandez.core.ControllerIdentity;
import com.winhub.hernandez.core.DInputVibration;
import com.winhub.hernandez.core.EnvVars;
import com.winhub.hernandez.core.FileUtils;
import com.winhub.hernandez.core.GPUInformation;
import com.winhub.hernandez.core.HermesSessionControl;
import com.winhub.hernandez.core.KeyValueSet;
import com.winhub.hernandez.core.ProcessHelper;
import com.winhub.hernandez.core.StickPacing;
import com.winhub.hernandez.store.SteamLogRedactor;
import com.winhub.hernandez.core.TarCompressorUtils;
import com.winhub.hernandez.core.WineInfo;
import com.winhub.hernandez.core.WinebusRumblePatcher;
import com.winhub.hernandez.fexcore.FEXCoreManager;
import com.winhub.hernandez.fexcore.FEXCorePreset;
import com.winhub.hernandez.fexcore.FEXCorePresetManager;
import com.winhub.hernandez.inputcontrols.HermesWriter;
import com.winhub.hernandez.xconnector.UnixSocketConfig;
import com.winhub.hernandez.xenvironment.EnvironmentComponent;
import com.winhub.hernandez.xenvironment.ImageFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class GuestProgramLauncherComponent extends EnvironmentComponent {
    private String guestExecutable;
    private static int pid = -1;
    private String[] bindingPaths;
    private EnvVars envVars;
    private WineInfo wineInfo;
    private String box64Preset = Box64Preset.COMPATIBILITY;
    private String fexcorePreset = FEXCorePreset.INTERMEDIATE;
    private Callback<Integer> terminationCallback;
    private static final Object lock = new Object();
    private final ContentsManager contentsManager;
    private final ContentProfile wineProfile;
    private Container container;
    private final Shortcut shortcut;

    // Direct XInput's control-block values for this launch (HermesXInput.launchControlMode), set by
    // the activity before start; 0/0 = no Direct XInput (the DLLs read "unset").
    private int hermesXInputMode = 0;
    private int hermesXInputFlags = 0;

    public void setHermesLaunchControl(int xinputMode, int xinputFlags) {
        this.hermesXInputMode = xinputMode;
        this.hermesXInputFlags = xinputFlags;
    }

    public void setWineInfo(WineInfo wineInfo) {
        this.wineInfo = wineInfo;
    }
    public WineInfo getWineInfo() {
        return this.wineInfo;
    }

    public Container getContainer() { return this.container; }
    public void setContainer(Container container) { this.container = container; }

    // When true, the guest uses the Wayland display path (winewayland.drv → embedded compositor)
    // instead of the X11 server. Set by XServerDisplayActivity in wayland_mode.
    private boolean waylandMode = false;
    public void setWaylandMode(boolean v) { this.waylandMode = v; }

    private void extractBox64Files() {
        ImageFs imageFs = environment.getImageFs();
        Context context = environment.getContext();

        // Fallback to default if the shared preference is not set or is empty
        String box64Version = container.getBox64Version();

        if (shortcut != null)
            box64Version = shortcut.getExtra("box64Version", shortcut.container.getBox64Version());

        Log.d("GuestProgramLauncherComponent", "box64Version: " + box64Version);

        File rootDir = imageFs.getRootDir();

        if (!box64Version.equals(container.getExtra("box64Version"))) {
            ContentProfile profile = contentsManager.getProfileByEntryName("box64-" + box64Version);
            if (profile != null)
                contentsManager.applyContent(profile);
            else
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box64/box64-" + box64Version + ".tzst", rootDir);
            container.putExtra("box64Version", box64Version);
            container.saveData();
        }

        // Set execute permissions for box64 just in case
        File box64File = new File(rootDir, "/usr/bin/box64");
        if (box64File.exists()) {
            FileUtils.chmod(box64File, 0755);
        }
    }

    private void extractEmulatorsDlls() {;
        Context context = environment.getContext();
        File rootDir = environment.getImageFs().getRootDir();
        File system32dir = new File(rootDir + "/home/xuser/.wine/drive_c/windows/system32");
        boolean containerDataChanged = false;

        String wowbox64Version = container.getBox64Version();
        String fexcoreVersion = container.getFEXCoreVersion();

        if (shortcut != null) {
            wowbox64Version = shortcut.getExtra("box64Version", shortcut.container.getBox64Version());
            // Cmod-lineage bug: the per-shortcut FEXCore version was saved but never re-read at
            // launch (only box64 was), so a shortcut's FEXCore choice silently fell back to the
            // container default. Honour it here, mirroring box64Version above.
            fexcoreVersion = shortcut.getExtra("fexcoreVersion", shortcut.container.getFEXCoreVersion());
        }

        Log.d("GuestProgramLauncherComponent", "box64Version in use: " + wowbox64Version);
        Log.d("GuestProgramLauncherComponent", "fexcoreVersion in use: " + fexcoreVersion);

        if (!wowbox64Version.equals(container.getExtra("box64Version"))) {
            ContentProfile profile = contentsManager.getProfileByEntryName("wowbox64-" + wowbox64Version);
            if (profile != null)
                contentsManager.applyContent(profile);
            else
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, environment.getContext(), "wowbox64/wowbox64-" + wowbox64Version + ".tzst", system32dir);
            container.putExtra("box64Version", wowbox64Version);
            containerDataChanged = true;
        }

        if (!fexcoreVersion.equals(container.getExtra("fexcoreVersion"))) {
            ContentProfile profile = contentsManager.getProfileByEntryName("fexcore-" + fexcoreVersion);
            if (profile != null)
                contentsManager.applyContent(profile);
            else
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, environment.getContext(), "fexcore/fexcore-" + fexcoreVersion + ".tzst", system32dir);
            container.putExtra("fexcoreVersion", fexcoreVersion);
            containerDataChanged = true;
        }

        // Re-stamp the shared FEX unixlib .so slot to match the DLLs applied above. Done
        // UNCONDITIONALLY (not gated on the version-change cache) so the shared slot always agrees
        // with the effective per-game/per-container FEXCore choice, even after arbitrary flipping.
        reconcileFexUnixlib(fexcoreVersion);

        if (containerDataChanged) container.saveData();
    }

    // The FEX unixlib .so names we own. The truncated variants are defensive: old testing produced
    // symlinks with a dropped trailing char (libarm64ecfe.so / libwow64fe.so) that could linger.
    private static final String[] FEX_UNIXLIB_SO_NAMES = {
            "libarm64ecfex.so", "libwow64fex.so", "libarm64ecfe.so", "libwow64fe.so"
    };

    /**
     * Materialize the effective FEXCore version's native unixlib COMPLETELY: the shared
     * {@code <imagefs>/usr/lib/wine/aarch64-unix/} slot must equal that one version's {@code .so},
     * or be EMPTY when the version is DLL-only. Proton's ntdll loader auto-searches that dir, so a
     * stale {@code .so} left by a previous unix install would be loaded even when the game picked a
     * DLL-only FEX (and could pair a build-A {@code .dll} with a build-B {@code .so}). Recomputed
     * fresh each launch from the resolved {@code fexcoreVersion}, keeping the {@code .dll} (system32)
     * and {@code .so} (shared slot) halves matched to a single version.
     *
     * The {@code .so} source is the SAME per-version staging the DLLs come from: a component's
     * install dir ({@link ContentsManager#getInstallDir}) retains every profile file, including the
     * unixlib {@code .so} (profile target {@code ${libdir}/wine/aarch64-unix/...}). The bundled asset
     * FEX has no profile and is DLL-only, so the slot is simply left cleared for a clean DLL run.
     *
     * NOTE: one game runs at a time (Bannerlator), so a single shared slot is safe. Concurrent
     * multi-instance with different FEX versions would need per-container .so isolation (future work).
     */
    private void reconcileFexUnixlib(String fexcoreVersion) {
        Context context = environment.getContext();
        File soDir = new File(environment.getImageFs().getLibDir(), "wine/aarch64-unix");
        try {
            if (!soDir.exists()) soDir.mkdirs();

            // 1) Strip the fex unixlibs first (never touch the real wine unixlibs: bcrypt.so, etc.).
            for (String name : FEX_UNIXLIB_SO_NAMES) {
                File stale = new File(soDir, name);
                if (stale.exists()) stale.delete();
            }

            // 2) If the effective version is a unixlib component, copy ITS retained .so back in.
            //    Bundled/DLL-only FEX (no profile, or a profile with no .so) leaves the slot empty.
            boolean copied = false;
            ContentProfile profile = contentsManager.getProfileByEntryName("fexcore-" + fexcoreVersion);
            if (profile != null && profile.fileList != null) {
                File installDir = ContentsManager.getInstallDir(context, profile);
                for (ContentProfile.ContentFile cf : profile.fileList) {
                    String base = new File(cf.target).getName();
                    if (base.equals("libarm64ecfex.so") || base.equals("libwow64fex.so")) {
                        File src = new File(installDir, cf.source);
                        if (src.exists()) {
                            File dst = new File(soDir, base);
                            FileUtils.copy(src, dst);
                            FileUtils.chmod(dst, 0755);
                            copied = true;
                        }
                    }
                }
            }

            Log.d("GuestProgramLauncherComponent", "FEX unixlib reconcile: " + fexcoreVersion
                    + " -> " + (copied ? "copied .so" : "DLL-only, cleared"));
        } catch (Exception e) {
            Log.e("GuestProgramLauncherComponent", "FEX unixlib reconcile failed: " + e.getMessage());
        }
    }

    public GuestProgramLauncherComponent(ContentsManager contentsManager, ContentProfile wineProfile, Shortcut shortcut) {
        this.contentsManager = contentsManager;
        this.wineProfile = wineProfile;
        this.shortcut = shortcut;
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (wineInfo.isArm64EC())
                extractEmulatorsDlls();
            else
                extractBox64Files();
            checkDependencies();
            patchWinebusRumbleDuration();
            pid = execGuestProgram();
        }
    }

    /**
     * Force SDL rumble to never auto-expire (TideGear #91 duration patch), applied to the
     * winebus.so wine actually loads for THIS launch's selected Proton/arch. Resolved from
     * the container's live wine selection ({@code imageFs.getWinePath()} == {@code wineInfo.path}
     * == {@code <imagefs>/opt/proton-<version>-<arch>}, pinned at
     * {@code XServerDisplayActivity.setWinePath(wineInfo.path)}), so it tracks Proton 9/10/11
     * automatically instead of hardcoding a version. Runs on every start right before the guest
     * process is spawned; {@link WinebusRumblePatcher#patchDuration} is idempotent and
     * exact-count-guarded, so re-running is cheap and a no-op once patched.
     */
    private void patchWinebusRumbleDuration() {
        try {
            String archDir = wineInfo.isArm64EC() ? "aarch64-unix" : "x86_64-unix";
            File winebus = new File(environment.getImageFs().getWinePath(),
                    "lib/wine/" + archDir + "/winebus.so");
            WinebusRumblePatcher.patchDuration(winebus, archDir);
        } catch (Exception e) {
            // Never let a cosmetic rumble tweak block a game launch.
            Log.w("GuestProgramLauncherComponent", "winebus rumble patch skipped: " + e.getMessage());
        }
    }


    private String checkDependencies() {
        String curlPath = environment.getImageFs().getRootDir().getPath() + "/usr/lib/libXau.so";
        String lddCommand = "ldd " + curlPath;

        StringBuilder output = new StringBuilder("Checking Curl dependencies...\n");

        try {
            java.lang.Process process = Runtime.getRuntime().exec(lddCommand);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errorReader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();
        } catch (Exception e) {
            output.append("Error running ldd: ").append(e.getMessage());
        }

        Log.d("CurlDeps", output.toString()); // Log the full dependency output
        return output.toString();
    }


    @Override
    public void stop() {
        synchronized (lock) {
            if (pid != -1) {
                Process.killProcess(pid);
                pid = -1;
            }
        }
    }

    // Linux PID of the top-level guest process (wine/box64). Used read-only by the runtime-backend
    // HUD chip to probe /proc/<pid>/maps for the FEX unixlib. -1 until the guest is started.
    public static int getPid() {
        return pid;
    }

    public Callback<Integer> getTerminationCallback() {
        return terminationCallback;
    }

    public void setTerminationCallback(Callback<Integer> terminationCallback) {
        this.terminationCallback = terminationCallback;
    }

    public String getGuestExecutable() {
        return guestExecutable;
    }

    public void setGuestExecutable(String guestExecutable) {
        this.guestExecutable = guestExecutable;
    }

    public String[] getBindingPaths() {
        return bindingPaths;
    }

    public void setBindingPaths(String[] bindingPaths) {
        this.bindingPaths = bindingPaths;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = envVars;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public void setFEXCorePreset (String fexcorePreset) { this.fexcorePreset = fexcorePreset; }

    /**
     * Values to apply INSTEAD of looking the preset id up in the shared preset list — set when the
     * container or the game carries its own copy of that preset
     * ({@link com.winhub.hernandez.core.PresetOverrides}). Null (the normal case) keeps the previous
     * behaviour exactly: resolve the id through the preset manager.
     */
    private EnvVars box64PresetVars = null;
    private EnvVars fexcorePresetVars = null;

    public void setBox64PresetVars(EnvVars envVars) { this.box64PresetVars = envVars; }

    public void setFEXCorePresetVars(EnvVars envVars) { this.fexcorePresetVars = envVars; }

    private int execGuestProgram() {
        Context context = environment.getContext();
        ImageFs imageFs = environment.getImageFs();
        File rootDir = imageFs.getRootDir();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enableBox64Logs = preferences.getBoolean("enable_box64_logs", false);
        boolean openWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean shareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);

        if (openWithAndroidBrowser)
            envVars.put("WINE_OPEN_WITH_ANDROID_BROWSER", "1");
        if (shareAndroidClipboard) {
            envVars.put("WINE_FROM_ANDROID_CLIPBOARD", "1");
            envVars.put("WINE_TO_ANDROID_CLIPBOARD", "1");
        }

        EnvVars envVars = new EnvVars();

        addBox64EnvVars(envVars, enableBox64Logs);
        envVars.putAll(fexcorePresetVars != null
                ? fexcorePresetVars : FEXCorePresetManager.getEnvVars(context, fexcorePreset));

        String renderer = GPUInformation.getRenderer(null, null);

        if (renderer.contains("Mali")) //Mali Cope HAHAHAHAHAHA
            envVars.put("BOX64_MMAP32", "0");

        if (envVars.get("BOX64_MMAP32").equals("1") && !wineInfo.isArm64EC()) {
            Log.d("GuestProgramLauncherComponent", "Disabling map memory placed");
            envVars.put("WRAPPER_DISABLE_PLACED", "1");
        }

        // Setting up essential environment variables for Wine
        envVars.put("HOME", imageFs.home_path);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", rootDir.getPath() + "/usr/tmp");
        envVars.put("XDG_DATA_DIRS", rootDir.getPath() + "/usr/share");
        envVars.put("LD_LIBRARY_PATH", rootDir.getPath() + "/usr/lib" + ":" + "/system/lib64");
        envVars.put("XDG_CONFIG_DIRS", rootDir.getPath() + "/usr/etc/xdg");
        envVars.put("GST_PLUGIN_PATH", rootDir.getPath() + "/usr/lib/gstreamer-1.0");
        envVars.put("FONTCONFIG_PATH", rootDir.getPath() + "/usr/etc/fonts");
        envVars.put("VK_LAYER_PATH", rootDir.getPath() + "/usr/share/vulkan/implicit_layer.d" + ":" + rootDir.getPath() + "/usr/share/vulkan/explicit_layer.d");
        envVars.put("WRAPPER_LAYER_PATH", rootDir.getPath() + "/usr/lib");
        envVars.put("WRAPPER_CACHE_PATH", rootDir.getPath() + "/usr/var/cache");
        envVars.put("WINE_NO_DUPLICATE_EXPLORER", "1");
        envVars.put("PREFIX", rootDir.getPath() + "/usr");
        if (waylandMode) {
            // Wayland display path: point Wine at the embedded compositor's socket. winewayland's
            // unixlib runs host-side (no chroot — the guest uses full /data paths), so XDG_RUNTIME_DIR
            // must be the FULL host path where XServerDisplayActivity created the socket
            // (imagefs/tmp/wayland-0), not a bare "/tmp". Skip DISPLAY (winex11.drv is hidden anyway).
            envVars.put("WAYLAND_DISPLAY", "wayland-0");
            // Must match XServerDisplayActivity.startWaylandCompositor: filesDir/.wayland-rt (NOT
            // imagefs/tmp, which setupXEnvironment clears out from under the compositor's socket).
            envVars.put("XDG_RUNTIME_DIR", new File(context.getFilesDir(), ".wayland-rt").getPath());
            // winewayland.so's DT_NEEDED libwayland-client/-egl/xkbcommon/xkbregistry are bundled in
            // the Proton wcp's lib/ with the exact sonames winewayland was linked against (unversioned,
            // e.g. "libxkbcommon.so"). imagefs/usr/lib ships versioned sonames ("libxkbcommon.so.0"),
            // which bionic rejects with a verneed mismatch. So prepend the wcp lib/ to LD_LIBRARY_PATH:
            // the 4 wayland libs resolve there (matching sonames); their transitive deps (libffi,
            // libandroid-support, libc…) still resolve from imagefs/usr/lib further down the path.
            envVars.put("LD_LIBRARY_PATH", imageFs.getWinePath() + "/lib" + ":"
                    + rootDir.getPath() + "/usr/lib" + ":" + "/system/lib64");
        } else {
            envVars.put("DISPLAY", ":0");
        }
        envVars.put("WINE_DISABLE_FULLSCREEN_HACK", "1");
        envVars.put("GST_PLUGIN_FEATURE_RANK", "ximagesink:3000");
        envVars.put("ALSA_CONFIG_PATH", rootDir.getPath() + "/usr/share/alsa/alsa.conf" + ":" + rootDir.getPath() + "/usr/etc/alsa/conf.d/android_aserver.conf");
        envVars.put("ALSA_PLUGIN_DIR", rootDir.getPath() + "/usr/lib/alsa-lib");
        envVars.put("OPENSSL_CONF", rootDir.getPath() + "/usr/etc/tls/openssl.cnf");
        envVars.put("SSL_CERT_FILE", rootDir.getPath() + "/usr/etc/tls/cert.pem");
        envVars.put("SSL_CERT_DIR", rootDir.getPath() + "/usr/etc/tls/certs");
        envVars.put("WINE_X11FORCEGLX", "1");
        envVars.put("WINE_GST_NO_GL", "1");
        envVars.put("SteamGameId", "0");
        envVars.put("PROTON_AUDIO_CONVERT", "0");
        envVars.put("PROTON_VIDEO_CONVERT", "0");
        envVars.put("PROTON_DEMUX", "0");

        String winePath = imageFs.getWinePath() + "/bin";

        Log.d("GuestProgramLauncherComponent", "WinePath is " + winePath);

        envVars.put("PATH", winePath + ":" +
                rootDir.getPath() + "/usr/bin");

 
        envVars.put("ANDROID_SYSVSHM_SERVER", rootDir.getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);

        // Pick the first USABLE IPv4 resolver, not blindly getDnsServers().get(0).
        // The guest netstack can't use an IPv6 link-local server (the %wlan0 scope
        // is meaningless inside the container), and dual-stack networks frequently
        // list an fe80:: address first — taking [0] then handed the guest an
        // unresolvable DNS (connectivity fine, every hostname lookup failed).
        // Fall back to 8.8.4.4 when the active network exposes no usable IPv4 server.
        String primaryDNS = "8.8.4.4";
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Service.CONNECTIVITY_SERVICE);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);
            if (linkProperties != null) {
                for (InetAddress dnsServer : linkProperties.getDnsServers()) {
                    if (dnsServer instanceof Inet4Address
                            && !dnsServer.isLoopbackAddress()
                            && !dnsServer.isAnyLocalAddress()
                            && !dnsServer.isLinkLocalAddress()) {
                        primaryDNS = dnsServer.getHostAddress();
                        break;
                    }
                }
            }
        }
        envVars.put("ANDROID_RESOLV_DNS", primaryDNS);
        envVars.put("WINE_NEW_NDIS", "1");
        // Default-route gateway for the layer's WINE_NEW_NDIS route table (eanet layers): the guest's
        // GetBestRoute()/SIO_ROUTING_INTERFACE_QUERY need a 0.0.0.0/0 entry; the layer falls back to
        // the subnet .1 when this is absent, so older layers are unaffected.
        if (activeNetwork != null) {
            LinkProperties lp = connectivityManager.getLinkProperties(activeNetwork);
            if (lp != null) {
                for (RouteInfo r : lp.getRoutes()) {
                    InetAddress gw = r.getGateway();
                    if (gw instanceof Inet4Address && !gw.isAnyLocalAddress()
                            && r.getDestination() != null && r.getDestination().getPrefixLength() == 0) {
                        envVars.put("WINE_ANDROID_GATEWAY", gw.getHostAddress());
                        break;
                    }
                }
            }
        }

        String ld_preload = "";

        // Check for specific shared memory libraries
        if ((new File(imageFs.getLibDir(), "libandroid-sysvshm.so")).exists()){
            ld_preload = imageFs.getLibDir() + "/libandroid-sysvshm.so";
        }

        // Copy libfakeinput.so
        File fakeinputDest = new File(imageFs.getLibDir(), "libfakeinput.so");
        String nativeLibDir = environment.getContext().getApplicationInfo().nativeLibraryDir;
        File fakeinputSrc = new File(nativeLibDir, "libfakeinput.so");

        Log.d("GuestLauncher", "nativeLibDir: " + nativeLibDir);
        Log.d("GuestLauncher", "fakeinputSrc exists: " + fakeinputSrc.exists());
        Log.d("GuestLauncher", "fakeinputDest: " + fakeinputDest.getAbsolutePath());

        try {
            if (fakeinputSrc.exists()) {
                // Refresh every launch so the guest reader can never skew from the
                // app-side writer (a same-size rebuild defeats length checks).
                // Do NOT reintroduce a `!fakeinputDest.exists()` guard here.
                FileUtils.copy(fakeinputSrc, fakeinputDest);
                Log.d("GuestLauncher", "Copied libfakeinput.so to imagefs");
            } else if (!fakeinputDest.exists()) {
                Log.e("GuestLauncher", "libfakeinput.so NOT FOUND in APK: " + fakeinputSrc.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e("GuestLauncher", "Failed to copy libfakeinput.so: " + e.getMessage());
            e.printStackTrace();
        }

        Log.d("GuestLauncher", "fakeinputDest exists after copy: " + fakeinputDest.exists());
        if (fakeinputDest.exists()) {
            if (!ld_preload.isEmpty()) ld_preload += ":";
            ld_preload += fakeinputDest.getAbsolutePath();
        }

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        devInputDir.mkdirs();

        envVars.put("FAKE_EVDEV_DIR", devInputDir.getAbsolutePath());
        envVars.put("FAKE_EVDEV_VIBRATION", "1");

        // Controller identity (core.ControllerIdentity): FAKE_EVDEV_IDENTITY for the evdev layer, plus
        // the SDL hints, hidraw opt-out and SDL mapping that identity needs. Generic adds nothing, as
        // before. Put here, ahead of the container's and the game's own env vars merged below, so an
        // explicit value there still wins; the hidraw list and the SDL mapping list are combined with
        // an explicit value instead of being replaced by it.
        String padIdentity = ControllerIdentity.effective(container, shortcut);
        Map<String, String> identityEnv = ControllerIdentity.launchEnv(padIdentity);
        for (Map.Entry<String, String> entry : identityEnv.entrySet()) {
            String name = entry.getKey();
            if (this.envVars != null && this.envVars.has(name) && ControllerIdentity.isMergedEnv(name)) {
                this.envVars.put(name, ControllerIdentity.mergeEnv(name, entry.getValue(), this.envVars.get(name)));
            } else {
                envVars.put(name, entry.getValue());
            }
        }

        // Controller input reaches the guest through HERMES: one fixed-size state file per slot
        // (see HermesWriter). Prepare all 4 and hand the native reader their paths, so an open()
        // of /dev/input/eventN attaches to the matching slot. The slot state is process-wide and
        // shared with the WinHandler writers created later in this same process.
        // No discovery node is created here: event<N> exists only while a controller holds slot N
        // (WinHandler's slot writers create and remove them).
        String padPaths = HermesWriter.getRingEnv(devInputDir);
        if (padPaths != null && !padPaths.isEmpty()) {
            envVars.put("FAKE_EVDEV_MEMFD_PATHS", padPaths);
        }
        // HERMES control block, written before the guest starts: every slot's identity (the evdev
        // layer reads it when a reader attaches, ahead of FAKE_EVDEV_IDENTITY, so a reconnect presents a
        // changed one), the session control (stick pacing; Direct XInput's mode and flags) and
        // DirectInput vibration (read when a reader attaches; HERMES_DINPUT_FF in the env vars wins).
        HermesSessionControl.writeSlotIdentities(padIdentity);
        HermesSessionControl.writeSessionControl(StickPacing.forGame(container, shortcut),
                hermesXInputMode, hermesXInputFlags, StickPacing.envOverrides(container, shortcut));
        HermesSessionControl.writeFfMode(DInputVibration.forGame(container, shortcut));
        if (DInputVibration.envOverride(container, shortcut) != null) {
            Log.i("GuestLauncher", "DirectInput vibration: " + DInputVibration.ENV + " in the env vars decides ("
                    + (DInputVibration.effective(container, shortcut) ? "on" : "off") + ")");
        }

        Log.d("GuestLauncher", "Final LD_PRELOAD: " + ld_preload);
        // winewayland.so (unixlib) dlopens libwayland-client/-egl/xkbcommon/xkbregistry. These resolve
        // from imagefs/usr/lib via LD_LIBRARY_PATH exactly like winex11.so's libX11/libXext deps do
        // (proven: winex11.so has the same Termux RUNPATH yet loads fine). So NO wayland-specific
        // LD_PRELOAD — force-preloading them into the main `wine` executable aborts startup
        // ("CANNOT LINK EXECUTABLE"). installWaylandLibs stages the libs into imagefs/usr/lib.
        envVars.put("LD_PRELOAD", ld_preload);

        if (this.envVars.has("MANGOHUD")) {
            this.envVars.remove("MANGOHUD");
        }

        if (this.envVars.has("MANGOHUD_CONFIG")) {
            this.envVars.remove("MANGOHUD_CONFIG");
        }

        // WINEVMEMMAXSIZE (MB) caps the guest Wine VA reservation — the fix for heavy AAA titles
        // (e.g. Deus Ex: MD on EOS) that reserve hundreds of GB of address space up-front and OOM the
        // X server (observed ~489 GB: err:virtual:allocate_virtual_memory ... size 71f6ea0000). It is
        // OPT-IN / default-off: set it per shortcut/container envVars only when a game needs it (it
        // then propagates to the guest verbatim via the external-env merge below, covering both the
        // arm64ec/WOWBox64+FEX and box64 paths). Recognized in the env-var picker (KnownEnvVars).
        // Only effective on a Wine/Proton build patched to READ WINEVMEMMAXSIZE (coffincolors ntdll
        // patch, not in stock Wine); inert otherwise.

        // Merge any additional environment variables from external sources
        if (this.envVars != null) {
            envVars.putAll(this.envVars);
        }

        // One line per launch with the identity the pads actually present (after the merge, so an
        // explicit FAKE_EVDEV_IDENTITY shows up as what it is) and the identity env that went with it.
        {
            String presented = envVars.has(ControllerIdentity.ENV) ? envVars.get(ControllerIdentity.ENV) : "";
            StringBuilder extra = new StringBuilder();
            for (String name : identityEnv.keySet()) {
                if (name.equals(ControllerIdentity.ENV) || !envVars.has(name)) continue;
                extra.append(extra.length() == 0 ? "; env " : ", ").append(name);
                if (!name.equals("SDL_GAMECONTROLLERCONFIG")) extra.append('=').append(envVars.get(name));
            }
            Log.i("GuestLauncher", "Controller identity: " + ControllerIdentity.describe(presented)
                    + " (FAKE_EVDEV_IDENTITY=" + (presented.isEmpty() ? "unset" : presented)
                    + ", from " + ControllerIdentity.source(container, shortcut) + ")" + extra);
        }

        ContainerOverlay.applyLaunchEnv(context, container, envVars);


        String emulator = container.getEmulator();
        if (shortcut != null)
            emulator = shortcut.getExtra("emulator", container.getEmulator());

        // Construct the command without Box64 to the Wine executable
        String command = "";
        String overriddenCommand = envVars.get("GUEST_PROGRAM_LAUNCHER_COMMAND");
        if (!overriddenCommand.isEmpty()) {
            String[] parts = overriddenCommand.split(";");
            for (String part : parts)
                command += part + " ";
            command = command.trim();
        }
        else {
            if (wineInfo.isArm64EC()) {
                command = winePath + "/" + guestExecutable;
                if (emulator.toLowerCase().equals("fexcore"))
                    envVars.put("HODLL", "libwow64fex.dll");
                else
                    envVars.put("HODLL", "wowbox64.dll");
            } else
                command = imageFs.getBinDir() + "/box64 " + guestExecutable;
        }

        // **Maybe remove this: Set execute permissions for box64 if necessary (Glibc/Proot artifact)
        File box64File = new File(rootDir, "/usr/bin/box64");
        if (box64File.exists()) {
            FileUtils.chmod(box64File, 0755);
        }

        // Diagnostic: dump every env var the Wine process actually receives.
        // Log individually to stay under Android's ~4KB per-message truncation limit.
        if (BuildConfig.DEBUG) {
            String[] finalEnv = envVars.toStringArray();
            Log.d("GuestProgramLauncherComponent", "=== FINAL ENV VARS (" + finalEnv.length + " entries) ===");
            for (String entry : finalEnv) {
                Log.d("GuestProgramLauncherComponent", "  " + SteamLogRedactor.redact(entry));
            }
        }

        // Bring a stale prefix up to date BEFORE the session, X11 and Wayland alike, with Wine Mono's
        // download prompt switched off for that step only (see updatePrefixBeforeSession).
        String wineLauncher = wineInfo.isArm64EC() ? winePath + "/wine" : imageFs.getBinDir() + "/box64 wine";
        updatePrefixBeforeSession(envVars, wineLauncher, rootDir, imageFs);

        return ProcessHelper.exec(command, envVars.toStringArray(), rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }

            if (terminationCallback != null)
                terminationCallback.call(status);
        });
    }

    /**
     * Run Wine's prefix update (the wine.inf install ntdll triggers through {@code wineboot --init}
     * in the FIRST process of a session) in a throwaway session of its own - headless on Wayland, on
     * the already-running X server on X11 - before the real launch,
     * whenever the prefix is stale: a new container (no layer's prefixPack ships a stamp except the
     * x86_64 Proton one, which says "disable") and the first launch after its layer changed. Every
     * display backend and every layer; {@code wineLauncher} is {@code <layer>/bin/wine} for arm64ec
     * and {@code box64 wine} otherwise, the same way the session itself is started.
     *
     * Mono: wine.inf's RegisterDlls section registers mscoree.dll, and mscoree's DllRegisterServer
     * runs install_wine_mono(): no C:\windows\mono and no share/wine/mono (no layer ships Wine Mono)
     * means {@code control.exe appwiz.cpl install_mono}, whose last resort is the "Wine Mono Installer"
     * dialog offering a download from winehq.org. That was the prompt on every new container and every
     * layer switch, on every layer. {@code mscoree=d} for this process tree only makes setupapi skip
     * the registration (its COM classes are already in every prefixPack), so no prompt and no network;
     * the game session keeps the container's own overrides untouched, so a .NET game still loads
     * mscoree and the Wine Mono the Components installer put in the prefix. (mshtml needs nothing
     * here: no layer's DllRegisterServer asks for Gecko; that only happens when a program uses it.)
     *
     * Why a separate step at all: the env above must not reach the game, and ntdll hands the FIRST
     * process's environment to the {@code wineboot --init} it spawns, so the update cannot run inside
     * the session with its own env. Wayland also needs it off the desktop's path: on the first launch
     * after a layer install/repoint the prefix's {@code .update-timestamp}
     * no longer matches the layer's {@code wine.inf} mtime, so ntdll blocks explorer (the session's
     * first process) before its main() for the ~7 s install. wineboot's wait dialog then needs a
     * desktop window while none exists, win32u auto-spawns {@code explorer.exe /desktop} on the
     * "Default" desktop, that explorer is closed at once by the server's zero desktop-close timeout
     * (Proton default), and the display cache our explorer builds afterwards carries monitors with
     * no source - win32u's 1024x768 fallback rect - so the virtual desktop came up at 1024x768 (then
     * 1024x1488) instead of the container size; the second launch, prefix now current, was right.
     * Doing the update here, with no display driver at all, keeps it off the desktop's critical
     * path, so the first launch takes the same path as the second.
     *
     * Same test as wineboot's update_timestamp(): the file holds wine.inf's mtime in seconds
     * ("disable" opts out); {@code wineboot -h} exits at argument parsing, the update itself is done
     * by the {@code --init} instance ntdll spawns for the first process. Afterwards the wineserver
     * shuts down by itself (services only, 3 s master-socket timeout) and flushes the registry; we
     * wait for that so the real launch starts from a clean prefix, and only force-terminate as a last
     * resort. X11 used to keep the in-session update; it now takes this step too, for the Mono reason,
     * with DISPLAY kept (unlike Wayland) so nothing else about its update changes.
     */
    private void updatePrefixBeforeSession(EnvVars guestEnv, String wineLauncher, File rootDir, ImageFs imageFs) {
        final String tag = "GuestProgramLauncherComponent";
        try {
            File wineInf = new File(imageFs.getWinePath(), "share/wine/wine.inf");
            File stamp = new File(rootDir, ImageFs.WINEPREFIX + "/.update-timestamp");
            if (!wineInf.isFile()) return;
            long infMtime = wineInf.lastModified() / 1000L;
            String current = stamp.isFile() ? FileUtils.readString(stamp) : "";
            if (current == null) current = "";
            current = current.trim();
            if (current.startsWith("disable")) return;
            long stamped = -1;
            int i = 0;
            while (i < current.length() && Character.isDigit(current.charAt(i))) i++;
            if (i > 0) {
                try { stamped = Long.parseLong(current.substring(0, i)); } catch (NumberFormatException ignored) {}
            }
            if (stamped == infMtime) return;

            EnvVars env = new EnvVars();
            env.putAll(guestEnv);
            if (waylandMode) {
                // No display for this session: wineboot's wait dialog and the explorer win32u spawns for
                // it run on the null driver instead of connecting to the compositor. libwayland falls back
                // to "wayland-0" under XDG_RUNTIME_DIR when WAYLAND_DISPLAY is unset, so drop both.
                env.remove("WAYLAND_DISPLAY");
                env.remove("XDG_RUNTIME_DIR");
                env.remove("DISPLAY");
            }
            // X11 keeps DISPLAY: XServerComponent starts before this component, so the X server is
            // already up and the update runs with the same env it had inside the session.
            // No Wine Mono download prompt (see above). Appended last: in WINEDLLOVERRIDES a later entry
            // for the same dll replaces an earlier one, so this wins over a user's own mscoree entry.
            String overrides = withMscoreeDisabled(env.get("WINEDLLOVERRIDES"));
            env.put("WINEDLLOVERRIDES", overrides);
            Log.i(tag, "prefix update: .update-timestamp \"" + current + "\" != wine.inf mtime " + infMtime
                    + " (" + wineInf.getPath() + "); running Wine's prefix update before the "
                    + (waylandMode ? "Wayland" : "X11") + " session, WINEDLLOVERRIDES=" + overrides);
            long t0 = System.currentTimeMillis();
            int status = ProcessHelper.execAndWait(wineLauncher + " wineboot -h", env.toStringArray(), rootDir, 180_000);
            // Let the wineserver wind down on its own (it flushes the registry on exit).
            long deadline = System.currentTimeMillis() + 30_000;
            while (!ProcessHelper.listRunningWineProcesses().isEmpty() && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (!ProcessHelper.listRunningWineProcesses().isEmpty()) {
                Log.w(tag, "prefix update: wine processes still alive after the prefix update; terminating them");
                ProcessHelper.terminateAllWineProcessesAndWait(3000, true);
            }
            String after = stamp.isFile() ? FileUtils.readString(stamp) : "";
            Log.i(tag, "prefix update: finished in " + (System.currentTimeMillis() - t0) + " ms (wineboot exit "
                    + status + "), .update-timestamp now \"" + (after == null ? "" : after.trim()) + "\"");
        } catch (Throwable t) {
            Log.w(tag, "prefix update: the step before the session failed; launching anyway", t);
        }
    }

    /** {@code overrides} (a WINEDLLOVERRIDES value, possibly empty) with {@code mscoree=d} appended. */
    static String withMscoreeDisabled(String overrides) {
        String o = overrides == null ? "" : overrides.trim();
        while (o.endsWith(";")) o = o.substring(0, o.length() - 1).trim();
        return o.isEmpty() ? "mscoree=d" : o + ";mscoree=d";
    }

    private void addBox64EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX64_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");
        }

        envVars.putAll(box64PresetVars != null ? box64PresetVars
                : Box64PresetManager.getEnvVars("box64", environment.getContext(), box64Preset));
        envVars.put("BOX64_X11GLX", "1");
        envVars.put("BOX64_NORCFILES", "1");
    }

    public void suspendProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.suspendProcess(pid);
        }
    }

    public void resumeProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.resumeProcess(pid);
        }
    }
}
