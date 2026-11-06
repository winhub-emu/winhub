package com.winhub.hernandez.communityconfigs

import com.winhub.hernandez.winhandler.WinHandler
import org.json.JSONArray
import org.json.JSONObject

/**
 * PHASE 3 step 1 — the reverse of [ConfigTranslator]: turn a Bannerlator shortcut's effective
 * {@code [Extra Data]} settings back into a shareable community-config JSON in the GameHub
 * {@code pc_*} format that BOTH BannerHub and Bannerlator can read.
 *
 * This object is the PURE CORE — deterministic and Android-free so the round-trip
 * (export then {@link ConfigTranslator#translate}) can be exercised on the JVM. All non-deterministic
 * inputs (the {@code upload_token}, the file-name timestamp, the device/soc/version strings) are
 * produced by the Android adapter [ShortcutExporter] and passed in via [ExportMeta] / [fileName].
 *
 * The mapping is the exact inverse of [ConfigTranslator] (same keys, inverse value transforms):
 *   dxwrapperConfig.version → pc_ls_DXVK; dxwrapperConfig.vkd3dVersion → pc_ls_VK3k;
 *   graphicsDriverConfig.version → pc_ls_GPU_DRIVER_; emulator+fexcoreVersion → pc_set_constant_95;
 *   wineVersion → pc_ls_CONTAINER_LIST; graphicsDriver → pc_ls_GRAPHICS_WRAPPER (plain scalar);
 *   audioDriver → pc_ls_AUDIO_DRIVER; inputType →
 *   pc_ls_update_enable_xinput; envVars → pc_ls_environment_variable; execArgs → pc_ls_boot_option.
 * Each component-bearing value is a JSON STRING with a {@code name} (matching BannerHub's own
 * export shape, so {@link ConfigTranslator#jname} reads it back). Only present/non-blank source
 * fields emit a key; {@code settings_count} / {@code components_count} are computed from what emitted.
 */
object ConfigExporter {

    /**
     * The shortcut {@code [Extra Data]} keys carried VERBATIM (raw key, raw value) inside the additive
     * {@code settings.bl_ext} block — the ~28 settings the {@code pc_*} format can't represent. Emitted
     * only when present/non-blank, so a config with none of them (or a BannerHub-origin config) never
     * grows a bl_ext object. Read back by {@link ConfigTranslator#translate} as a scalar overlay, leaving
     * BannerHub-origin configs (which have no bl_ext) completely unaffected. The DXVK {@code async} flag
     * is NOT here — it is lifted out of {@code dxwrapperConfig} separately and stored under {@code async}.
     */
    val BL_EXT_KEYS: List<String> = listOf(
        // dxwrapper = the raw DX-wrapper CHOICE (dxvk / dxvk+vkd3d / wined3d / vegas). The pc_* path only
        // INFERS this from the DXVK/VKD3D versions, so a "vegas" (or any non-inferrable) wrapper choice
        // would be lost on import; carrying it verbatim makes every wrapper — incl. VEGAS — round-trip.
        "dxwrapper",
        "screenSize", "renderer", "renderScale", "fullscreenMode", "frameGenEngine",
        // Vulkan renderer per-game settings (added 2026-07): Native Rendering, Colors (swapRB), present mode.
        "native", "swapRB", "presentMode",
        "fpsLimiterEnabled", "sharpnessEffect", "sharpnessLevel", "sharpnessDenoise", "reshadeLoadout",
        "reshadeMode", "reshadeParams", "reshadeEffect", "emulator", "box64Version", "box64Preset",
        "fexcorePreset", "cpuList", "startupSelection", "inputType", "exclusiveXInput", "disableXinput",
        // Controller identity (generic / xbox360 / ... ; core.ControllerIdentity) with the other controller keys.
        "controllerIdentity",
        "simTouchScreen", "numControllers", "controlsProfile", "wincomponents", "midiSoundFont",
        "lc_all", "autoCloseOnExit",
        // Fast OpenGL ("1" / "0"); an importer whose driver can't do it stays on the old path at
        // launch (core.FastOpenGL).
        "fastOpenGL",
        // Show OpenGL FPS / OpenGL vsync off ("1" / "0"; GALLIUM_HUD=fps / vblank_mode=0 at launch).
        "glFpsHud", "glVsyncOff",
        // Community-config coverage pass (2026-07): per-game HUD blob + motion/refresh/frame-gen/
        // vibration/upscaler overrides that were previously dropped. Each round-trips as a scalar the
        // import write loop applies verbatim; the launch path in XServerDisplayActivity honors every one
        // as a per-shortcut override (see the resolved*/shortcut.getExtra reads there).
        //   fpsCounterConfig — the WHOLE HUD KeyValueSet (hudStyle incl. Fusion, hudSize, hudLocked,
        //   every show* chip, temp unit, colors/outline/opacity/scale, engine/wrapper/dxver).
        "fpsCounterConfig",
        // Motion aim (gyro). Deadzone/smoothing are intentionally NOT here — they describe the hand and
        // the device, not the game, and stay container-scoped (matching the launch resolver).
        "gyroEnabled", "gyroTarget", "gyroActivator", "gyroActivationMode", "gyroMode",
        "gyroSensitivity", "gyroInvertX", "gyroInvertY",
        // In-game refresh cap (both keys; unlock resolved to 1/0, cap 0 = no ceiling).
        "maxGameRefreshRate", "unlockGameRefreshRate",
        // #168 custom startup service set (only consumed when startupSelection = Custom); frame-gen
        // interpolation model; dual-motor vibration; sticky per-game upscaler override.
        "startupServices", "frameGenModel", "vibrationMode", "vibrationIntensity", "scalingMode",
        // Drawer graphics quick-settings (#382): the sharpness/CAS/HDR sliders + deband toggles
        // that are remembered per game alongside scalingMode. Each round-trips as a scalar.
        "upscaleSharpness", "casEnabled", "casSharpness", "hdrEnabled",
        "debandEnabled", "debandStrength",
        // Direct XInput (HERMES) and its "Allow in-game switching" ("1" / "0"; core.HermesXInput).
        "hermesXInput", "hermesXInputSwitching",
    )

    /**
     * The non-deterministic provenance the adapter supplies. Kept out of the pure core so [export]
     * stays reproducible for the same inputs.
     *
     *  - [appSource] — OUR namespace, always {@code "bannerlator"} (never {@code "bannerhub"}).
     *  - [device] / [soc] — the hardware the config was captured on ({@code Build.MANUFACTURER MODEL}
     *    and the GPU-renderer probe), matching BannerHub's {@code detectSoc} meaning.
     *  - [version] — the app version string, written as {@code meta.bh_version} (informational).
     *  - [uploadToken] — variable-length lowercase hex, generated in the adapter (Random is fine there);
     *    embedded in {@code meta} for later describe/delete recovery.
     *  - [uploaderName] / [uploaderAvatarUrl] — the signed-in account attribution (Phase 2), when the user
     *    is logged in; both null for an anonymous export (no {@code meta.uploader} block is then written).
     */
    data class ExportMeta(
        val appSource: String,
        val device: String?,
        val soc: String?,
        val version: String?,
        val uploadToken: String,
        val uploaderName: String? = null,
        val uploaderAvatarUrl: String? = null,
        // Authoritative Steam appid for the game, when the shortcut carries one (its `steamAppId`
        // extra). Stamped into meta.steam_appid so the backend can aggregate this upload under the
        // correct appid-keyed canonical game instead of guessing from the (renameable) name slug —
        // the export-side counterpart to the appid-first import match. Null for non-Steam titles.
        val steamAppId: String? = null,
    )

    /**
     * Serialize [effective] (resolved shortcut {@code [Extra Data]} keys) into a
     * {@code {meta, settings, components}} config JSON string. Pure and deterministic: identical
     * [effective] + [meta] always produce semantically identical output. Never throws on ordinary input.
     */
    fun export(effective: Map<String, String>, meta: ExportMeta): String {
        val settings = JSONObject()
        val components = JSONArray()

        // DXVK / VKD3D live inside the comma-delimited dxwrapperConfig k=v list.
        val dxwCfg = effective["dxwrapperConfig"].orEmpty()
        val dxvk = subValue(dxwCfg, ",", "version")
        val vkd3d = subValue(dxwCfg, ",", "vkd3dVersion")
        // Turnip lives inside the semicolon-delimited graphicsDriverConfig k=v list.
        val turnip = subValue(effective["graphicsDriverConfig"].orEmpty(), ";", "version")
        // FEXCore only when the shortcut actually selects it as the x86 translator.
        val fex = if (effective["emulator"] == "fexcore") effective["fexcoreVersion"].nonBlank() else null
        // Proton / Wine is container-scoped; still emitted so the config is self-describing.
        val proton = effective["wineVersion"].nonBlank()

        if (dxvk != null) { putNamed(settings, "pc_ls_DXVK", dxvk); addComponent(components, dxvk, "DXVK") }
        if (vkd3d != null) { putNamed(settings, "pc_ls_VK3k", vkd3d); addComponent(components, vkd3d, "VKD3D") }
        if (turnip != null) { putNamed(settings, "pc_ls_GPU_DRIVER_", turnip); addComponent(components, turnip, "GPU") }
        if (fex != null) { putNamed(settings, "pc_set_constant_95", fex); addComponent(components, fex, "FEXCore") }
        if (proton != null) putNamed(settings, "pc_ls_CONTAINER_LIST", proton)

        // graphicsDriver = the top-level wrapper selection ("wrapper-original" / "wrapper-leegao" /
        // "wrapper-bcn_layer" / …). A plain SCALAR string, NOT a {"name":…} component — it picks which
        // wrapper .tzst is extracted, not a downloadable build — so no components[] entry is added.
        effective["graphicsDriver"].nonBlank()?.let { settings.put("pc_ls_GRAPHICS_WRAPPER", it) }

        // audioDriver → "1" for pulseaudio else "0" (inverse of the translator's read).
        effective["audioDriver"].nonBlank()?.let {
            settings.put("pc_ls_AUDIO_DRIVER", if (it == "pulseaudio") "1" else "0")
        }
        // inputType is a BITMASK (FLAG_INPUT_TYPE_XINPUT = 0x04, FLAG_INPUT_TYPE_DINPUT = 0x08), NOT a
        // 1/0 flag — an XInput-on shortcut reads "4", never "1". Export the boolean from the XInput bit.
        // (The raw inputType is also carried in bl_ext below, so DInput/combined states round-trip exactly
        // on our configs; this boolean is the BannerHub-compatible fallback.)
        effective["inputType"].nonBlank()?.let {
            val v = it.toIntOrNull() ?: WinHandler.DEFAULT_INPUT_TYPE.toInt()
            settings.put("pc_ls_update_enable_xinput", (v and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0)
        }
        // Scrub credential/identity vars (WN_STEAM_TOKEN/USERNAME/STEAMID, JWTs, emails, SteamID64s, …)
        // out of the env list BEFORE it leaves the device — the primary of the three scrub boundaries.
        // A config whose env was entirely credentials scrubs to blank and emits no key (nonBlank gate).
        EnvVarScrub.scrub(effective["envVars"]).nonBlank()?.let { settings.put("pc_ls_environment_variable", it) }
        effective["execArgs"].nonBlank()?.let { settings.put("pc_ls_boot_option", it) }

        // Additive namespaced overlay — the ~28 shortcut extras the pc_* format can't carry, stored raw
        // (raw key, raw value, no transform) so ConfigTranslator can overlay them straight back. Only
        // present/non-blank fields emit a key; the whole block is omitted when nothing qualifies, so
        // BannerHub-origin configs never gain a bl_ext object. The FULL dxwrapperConfig comma-list is
        // carried verbatim under "dxwrapperConfig" (every DXVK sub-key: async, vulkanVersion, and any
        // other tuning knobs) — pc_ls_DXVK/pc_ls_VK3k still carry the version tokens for install
        // resolution; this string carries the rest. graphicsDriverConfig is deliberately NOT carried
        // (device-specific gpuName/BCn settings must not travel with a shared config).
        val blExt = JSONObject()
        for (key in BL_EXT_KEYS) {
            effective[key].nonBlank()?.let { blExt.put(key, it) }
        }
        dxwCfg.nonBlank()?.let { blExt.put("dxwrapperConfig", it) }
        // bcnCompatSparse — the "Emulate sparse binding (DX12)" toggle for the "Wrapper + compat + bcn"
        // driver. It lives INSIDE graphicsDriverConfig (which is otherwise deliberately NOT carried), but
        // unlike the rest of that string (device gpuName + BCn format tuning) it is a GAME property — does
        // THIS DX12 title use tiled/sparse resources — so it IS portable. Lift just this one sub-key out
        // into bl_ext when the user opted in ("1"); it is re-injected into graphicsDriverConfig on import
        // (NOT overlaid as a scalar). The rest of graphicsDriverConfig still never travels.
        subValue(effective["graphicsDriverConfig"].orEmpty(), ";", "bcnCompatSparse")
            ?.takeIf { it == "1" }?.let { blExt.put("bcnCompatSparse", it) }
        if (blExt.length() > 0) settings.put("bl_ext", blExt)

        val metaObj = JSONObject()
        metaObj.put("app_source", meta.appSource)
        meta.device.nonBlank()?.let { metaObj.put("device", it) }
        meta.soc.nonBlank()?.let { metaObj.put("soc", it) }
        meta.version.nonBlank()?.let { metaObj.put("bh_version", it) }
        meta.steamAppId.nonBlank()?.let { metaObj.put("steam_appid", it) }
        // Optional signed-in attribution (Phase 2). Only written when logged in; the config-detail page
        // reads it back to show "by <username>", falling back to "Anonymous user" when it's absent.
        meta.uploaderName.nonBlank()?.let { name ->
            val uploader = JSONObject().put("name", name)
            meta.uploaderAvatarUrl.nonBlank()?.let { uploader.put("avatarUrl", it) }
            metaObj.put("uploader", uploader)
        }
        metaObj.put("upload_token", meta.uploadToken)
        metaObj.put("settings_count", settings.length())
        metaObj.put("components_count", components.length())

        val root = JSONObject()
        root.put("meta", metaObj)
        root.put("settings", settings)
        root.put("components", components)
        return root.toString(2)
    }

    /**
     * Build the config file name exactly as BannerHub's {@code BhSettingsExporter} does — five fields
     * sanitized {@code [^a-zA-Z0-9_\-] -> _}, then a trailing UNIX-SECONDS timestamp:
     * {@code <safeGame>-<safeMfr>-<safeModel>-<safeSoc>-<unixSeconds>.json}. The timestamp is passed in
     * (from the adapter) so the core stays deterministic.
     */
    fun fileName(game: String, mfr: String, model: String, soc: String, epochSeconds: Long): String =
        "${safe(game)}-${safe(mfr)}-${safe(model)}-${safe(soc)}-$epochSeconds.json"

    /** Wrap a version token as a {@code {"name":"<token>"}} JSON STRING value, matching BannerHub. */
    private fun putNamed(settings: JSONObject, key: String, name: String) {
        settings.put(key, JSONObject().put("name", name).toString())
    }

    /** Informational component entry (name + type only — we never invent download URLs). */
    private fun addComponent(arr: JSONArray, name: String, type: String) {
        arr.put(JSONObject().put("name", name).put("type", type))
    }

    /** Value of one sub-key inside a delimited {@code k=v} list, or null when absent/blank. */
    private fun subValue(list: String, delim: String, key: String): String? {
        if (list.isBlank()) return null
        for (part in list.split(delim)) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            if (part.substring(0, eq).trim() == key) return part.substring(eq + 1).nonBlank()
        }
        return null
    }

    private fun safe(s: String): String = s.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

    /** Trimmed value, or null when null/blank — the single "only emit present fields" gate. */
    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
