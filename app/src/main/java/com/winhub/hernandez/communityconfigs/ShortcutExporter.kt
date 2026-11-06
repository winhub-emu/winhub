package com.winhub.hernandez.communityconfigs

import android.content.Context
import android.os.Build
import com.winhub.hernandez.BuildConfig
import com.winhub.hernandez.container.Shortcut
import java.util.Random

/**
 * PHASE 3 step 1 — the thin Android adapter around the pure [ConfigExporter] core. Resolves a
 * shortcut's EFFECTIVE settings (per-game override, else container default — mirroring how
 * {@link CommunityConfigApply}'s {@code currentOrDefault} reads them), fills the device/version
 * provenance, generates a fresh {@code upload_token}, and hands everything to [ConfigExporter.export].
 *
 * Produces the artifact ONLY — no UI, no file I/O, no network. Later Phase-3 steps (share sheet,
 * {@code POST /upload}) consume [ExportResult].
 */
object ShortcutExporter {

    /**
     * The export artifact: the config [json], the BannerHub-shaped [fileName] to store/upload it under,
     * and the sanitized [game] slug the worker keys uploads by.
     */
    data class ExportResult(
        val json: String,
        val fileName: String,
        val game: String,
    )

    /**
     * Build a shareable config from [shortcut]. [context] is used only for the GPU-renderer soc probe.
     * Safe off the main thread (pure reads + string work). Never touches the filesystem or network.
     */
    fun fromShortcut(shortcut: Shortcut, context: Context): ExportResult {
        val container = shortcut.container

        // Effective settings = shortcut override, else the container default (the value the game runs
        // with). Composite k=v lists (dxwrapperConfig / graphicsDriverConfig) and the scalars with a
        // clean container getter fall back; execArgs / inputType are read as-is (shortcut-scoped).
        val effective = LinkedHashMap<String, String>()
        put(effective, "dxwrapper", orDefault(shortcut, "dxwrapper", container?.getDXWrapper()))
        put(effective, "dxwrapperConfig", orDefault(shortcut, "dxwrapperConfig", container?.getDXWrapperConfig()))
        put(effective, "graphicsDriverConfig", orDefault(shortcut, "graphicsDriverConfig", container?.getGraphicsDriverConfig()))
        put(effective, "graphicsDriver", orDefault(shortcut, "graphicsDriver", container?.getGraphicsDriver()))
        put(effective, "emulator", orDefault(shortcut, "emulator", container?.getEmulator()))
        put(effective, "fexcoreVersion", orDefault(shortcut, "fexcoreVersion", container?.getFEXCoreVersion()))
        put(effective, "audioDriver", orDefault(shortcut, "audioDriver", container?.getAudioDriver()))
        put(effective, "wineVersion", orDefault(shortcut, "wineVersion", container?.getWineVersion()))
        put(effective, "envVars", orDefault(shortcut, "envVars", container?.getEnvVars()))
        put(effective, "inputType", shortcut.getExtra("inputType"))
        put(effective, "execArgs", shortcut.getExtra("execArgs"))

        // The additive bl_ext overlay — the ~28 extra shortcut settings the pc_* format can't carry.
        // Fields with a clean container getter resolve EFFECTIVE (shortcut override, else container
        // default); the rest have no matching/clean container getter and are read shortcut-scoped only.
        // (emulator is already resolved above for the FEX gate; ConfigExporter picks it up from there.)
        put(effective, "screenSize", orDefault(shortcut, "screenSize", container?.getScreenSize()))
        put(effective, "renderer", orDefault(shortcut, "renderer", container?.getRenderer()))
        // Vulkan renderer settings (added 2026-07: per-game native / Colors=swapRB / present mode).
        // Effective value: shortcut override, else the container default. Applied generically on import
        // by the bl_ext loop in ShortcutConfig -> resolvedRendererNative/SwapRB/PresentMode read them.
        put(effective, "native", orDefault(shortcut, "native", container?.let { if (it.isRendererNative()) "true" else "false" }))
        put(effective, "swapRB", orDefault(shortcut, "swapRB", container?.let { if (it.getRendererSwapRB()) "true" else "false" }))
        put(effective, "presentMode", orDefault(shortcut, "presentMode", container?.getRendererPresentMode()))
        put(effective, "fullscreenMode", orDefault(shortcut, "fullscreenMode", container?.getFullscreenMode()?.toString()))
        put(effective, "frameGenEngine", orDefault(shortcut, "frameGenEngine", container?.getFrameGenEngine()))
        put(effective, "box64Version", orDefault(shortcut, "box64Version", container?.getBox64Version()))
        put(effective, "box64Preset", orDefault(shortcut, "box64Preset", container?.getBox64Preset()))
        put(effective, "fexcorePreset", orDefault(shortcut, "fexcorePreset", container?.getFEXCorePreset()))
        put(effective, "cpuList", orDefault(shortcut, "cpuList", container?.getCPUList()))
        put(effective, "wincomponents", orDefault(shortcut, "wincomponents", container?.getWinComponents()))
        put(effective, "midiSoundFont", orDefault(shortcut, "midiSoundFont", container?.getMIDISoundFont()))
        put(effective, "lc_all", orDefault(shortcut, "lc_all", container?.getLC_ALL()))
        put(effective, "reshadeLoadout", orDefault(shortcut, "reshadeLoadout", container?.getReshadeLoadout()))
        put(effective, "reshadeMode", orDefault(shortcut, "reshadeMode", container?.getReshadeMode()))
        put(effective, "reshadeParams", orDefault(shortcut, "reshadeParams", container?.getReshadeParams()))
        put(effective, "reshadeEffect", orDefault(shortcut, "reshadeEffect", container?.getReshadeEffect()))
        // No clean/matching container getter for these — read shortcut-scoped only (getExtra). e.g.
        // startupSelection's container form is a byte, which does not match the extra's string.
        put(effective, "renderScale", shortcut.getExtra("renderScale"))
        put(effective, "fpsLimiterEnabled", shortcut.getExtra("fpsLimiterEnabled"))
        put(effective, "sharpnessEffect", shortcut.getExtra("sharpnessEffect"))
        put(effective, "sharpnessLevel", shortcut.getExtra("sharpnessLevel"))
        put(effective, "sharpnessDenoise", shortcut.getExtra("sharpnessDenoise"))
        put(effective, "startupSelection", shortcut.getExtra("startupSelection"))
        put(effective, "exclusiveXInput", shortcut.getExtra("exclusiveXInput"))
        put(effective, "disableXinput", shortcut.getExtra("disableXinput"))
        // Controller identity: the game's own, else the container's (Generic is stored as absent).
        put(effective, "controllerIdentity", orDefault(shortcut, "controllerIdentity",
            container?.getControllerIdentity()?.takeIf { it != com.winhub.hernandez.core.ControllerIdentity.GENERIC }))
        put(effective, "simTouchScreen", shortcut.getExtra("simTouchScreen"))
        put(effective, "numControllers", shortcut.getExtra("numControllers"))
        put(effective, "controlsProfile", shortcut.getExtra("controlsProfile"))
        put(effective, "autoCloseOnExit", shortcut.getExtra("autoCloseOnExit"))
        // Fast OpenGL: effective = this game's override, else the container's switch (default on).
        put(effective, com.winhub.hernandez.core.FastOpenGL.EXTRA,
            if (com.winhub.hernandez.core.FastOpenGL.requested(
                    container?.getExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA),
                    shortcut.getExtra(com.winhub.hernandez.core.FastOpenGL.EXTRA))) "1" else "0")
        // Show OpenGL FPS / OpenGL vsync off: the same effective resolve (default off).
        for (sw in listOf(com.winhub.hernandez.core.FastOpenGL.FPS_HUD, com.winhub.hernandez.core.FastOpenGL.VSYNC_OFF)) {
            put(effective, sw.extra,
                if (sw.requested(container?.getExtra(sw.extra), shortcut.getExtra(sw.extra))) "1" else "0")
        }

        // Community-config coverage pass (2026-07). Container-level settings resolve EFFECTIVE exactly
        // as dxwrapperConfig/screenSize above do — shortcut override via orDefault, else the container
        // getter — because the launch path reads each back as a per-shortcut override (fpsCounterConfig
        // and vibration were wired to honor the shortcut in this same pass; frameGenModel already did).
        put(effective, "fpsCounterConfig", orDefault(shortcut, "fpsCounterConfig", container?.getFPSCounterConfig()))
        put(effective, "frameGenModel", orDefault(shortcut, "frameGenModel", container?.getFrameGenModel()?.toString()))
        put(effective, "vibrationMode", orDefault(shortcut, "vibrationMode", container?.getVibrationMode()?.toString()))
        put(effective, "vibrationIntensity", orDefault(shortcut, "vibrationIntensity", container?.getVibrationIntensity()?.toString()))
        // Motion aim (gyro) per-game keys — effective = shortcut override, else the container getter,
        // mirroring XServerDisplayActivity's launch resolution. Deadzone/smoothing are deliberately
        // container/device-scoped (the hand, not the game) and are NOT carried.
        put(effective, "gyroEnabled", orDefault(shortcut, "gyroEnabled", container?.let { if (it.isGyroEnabled()) "1" else "0" }))
        put(effective, "gyroTarget", orDefault(shortcut, "gyroTarget", container?.getGyroTarget()?.toString()))
        put(effective, "gyroActivator", orDefault(shortcut, "gyroActivator", container?.getGyroActivator()?.toString()))
        put(effective, "gyroActivationMode", orDefault(shortcut, "gyroActivationMode", container?.getGyroActivationMode()?.toString()))
        put(effective, "gyroMode", orDefault(shortcut, "gyroMode", container?.getGyroMode()?.toString()))
        put(effective, "gyroSensitivity", orDefault(shortcut, "gyroSensitivity", container?.getGyroSensitivity()?.toString()))
        put(effective, "gyroInvertX", orDefault(shortcut, "gyroInvertX", container?.let { if (it.isGyroInvertX()) "1" else "0" }))
        put(effective, "gyroInvertY", orDefault(shortcut, "gyroInvertY", container?.let { if (it.isGyroInvertY()) "1" else "0" }))
        // In-game refresh cap — effective shortcut-or-container (tri-state unlock resolved to 1/0).
        put(effective, "maxGameRefreshRate", orDefault(shortcut, "maxGameRefreshRate", container?.getMaxGameRefreshRate()?.toString()))
        put(effective, "unlockGameRefreshRate", orDefault(shortcut, "unlockGameRefreshRate", container?.let { if (it.isUnlockGameRefreshRate()) "1" else "0" }))
        // #168 custom startup service set — effective shortcut-or-container (honored only when Custom).
        put(effective, "startupServices", orDefault(shortcut, "startupServices", container?.getStartupServices()))
        // Per-game upscaler override — a sticky extra on both shortcut and container (no dedicated getter).
        put(effective, "scalingMode", orDefault(shortcut, "scalingMode", container?.getExtra("scalingMode")))
        // Direct XInput (HERMES) + "Allow in-game switching" — effective shortcut-or-container, 1/0.
        put(effective, "hermesXInput", orDefault(shortcut, "hermesXInput", container?.let { if (it.isHermesXInput()) "1" else "0" }))
        put(effective, "hermesXInputSwitching", orDefault(shortcut, "hermesXInputSwitching", container?.let { if (it.isHermesXInputSwitching()) "1" else "0" }))

        val device = Build.MANUFACTURER + " " + Build.MODEL
        val soc = DeviceIdentity.gpu(context) ?: DeviceIdentity.soc()
        // Optional account attribution (Phase 2): stamp the signed-in username/avatar into meta.uploader
        // when logged in; an anonymous export leaves both null (no uploader block), unchanged behaviour.
        val account = AccountManager.current(context)
        val meta = ConfigExporter.ExportMeta(
            appSource = "bannerlator",
            device = device,
            soc = soc,
            version = BuildConfig.VERSION_NAME,
            uploadToken = newUploadToken(),
            uploaderName = account?.username,
            uploaderAvatarUrl = account?.avatarUrl,
            steamAppId = shortcut.getExtra("steamAppId").takeIf { it.isNotBlank() },
        )

        val json = ConfigExporter.export(effective, meta)
        val fileName = ConfigExporter.fileName(
            game = shortcut.name,
            mfr = Build.MANUFACTURER,
            model = Build.MODEL,
            soc = soc ?: "",
            epochSeconds = System.currentTimeMillis() / 1000L,
        )
        // The worker keys uploads by the sanitized game slug (same transform the file name uses).
        val game = shortcut.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        return ExportResult(json, fileName, game)
    }

    /** Variable-length lowercase hex, non-crypto — the exact format BannerHub's exporter emits. */
    private fun newUploadToken(): String =
        java.lang.Long.toHexString(Random().nextLong() and Long.MAX_VALUE)

    /** Shortcut override, or the container default when the shortcut has none. */
    private fun orDefault(shortcut: Shortcut, key: String, default: String?): String {
        val v = shortcut.getExtra(key)
        return if (v.isNotBlank()) v else (default ?: "")
    }

    private fun put(map: MutableMap<String, String>, key: String, value: String) {
        if (value.isNotBlank()) map[key] = value
    }
}
