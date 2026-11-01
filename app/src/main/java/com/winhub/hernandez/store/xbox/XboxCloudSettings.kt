package com.winhub.hernandez.store.xbox

import android.content.Context
import androidx.preference.PreferenceManager
import com.winhub.hernandez.core.KeyValueSet
import org.json.JSONObject

/**
 * Xbox Cloud Gaming player settings (global). Stream-start settings (resolution, region, language, codec,
 * bitrate, stop quality drops) apply to the next stream; picture effects, stats, volume and rumble also
 * change live from the in-player menu. Picture effects share the Wine games' screen-effect profiles
 * ("screen_effect_profiles": brightness, contrast, gamma, FXAA, CRT, Toon, NTSC; the stream-only values ride
 * along as extra keys).
 */
data class XboxCloudSettings(
    val resolution: String = "1080p",      // 720p | 1080p | 1080p_hq
    val region: String = "",                // "" = Microsoft's choice, else a region name
    val language: String = "",              // "" = the device's language
    val codec: String = "default",          // default | high | normal | low
    val bitrateMbps: Int = 0,               // 0 = no cap
    val preventDrops: Boolean = false,
    val statsLevel: Int = 0,                // performance overlay: 0 off, 1 fps, 2 basic, 3 detailed, 4 full
    val statsLayout: String = "horizontal", // horizontal | vertical
    val volume: Int = 100,                  // 100..300 %
    val rumble: Int = 100,                  // 0..100 %
    val maxFps: Int = 0,                    // 0 = every frame
    val brightness: Int = 0,                // -50..50 (Wine screen-effect units)
    val contrast: Int = 0,                  // -100..100
    val gamma: Float = 1f,                  // 0.5..3
    val saturation: Int = 100,              // 0..200 %
    val sharpen: Int = 0,                   // 0..10
    val sharpenMode: String = "cas",        // cas | usm
    val deband: Int = 0,                    // 0..8
    val fxaa: Boolean = false,
    val toon: Boolean = false,
    val crt: Boolean = false,
    val ntsc: Boolean = false,
    val profile: String = "",               // the screen-effect profile last applied ("" = none)
    val playerEngine: String = "web",       // web | native (WebRTC library + Vulkan compositor, frame generation)
    val frameGen: String = "winfg",         // native engine: off | winfg (2x) | lsfg | lsfg3 | lsfg4 (needs the imported Lossless.dll)
    val fpsLimit: Int = 0,                  // native engine target: 0 = the screen's rate, else 30/45/60/120/144/165
    val fpsCustomOn: Boolean = false,       // use fpsCustom instead of the presets
    val fpsCustom: Int = 60,                // 10..165
    val instantInput: Boolean = true,       // native: send controller changes at once (else every 16 ms)
    val smallBuffer: Boolean = false,       // native: WebRTC shows frames as soon as decoded (no playout delay)
    val fastDecoder: Boolean = true,        // native: the hardware decoder's low-latency mode
    val zeroCopy: Boolean = false,          // native, frame generation off: decode straight onto the screen
) {
    /** The native engine's frame-rate target (0 = the screen's own rate). */
    val fpsTarget get() = if (fpsCustomOn) fpsCustom else fpsLimit

    /** X-MS-Device-Info / play body os name that makes the service pick this resolution (Better xCloud's mapping). */
    val osName get() = when (resolution) { "720p" -> "android"; "1080p_hq" -> "tizen"; else -> "windows" }

    fun toJson(): String = JSONObject()
        .put("resolution", resolution).put("region", region).put("language", language).put("codec", codec)
        .put("bitrateMbps", bitrateMbps).put("preventDrops", preventDrops).put("statsLevel", statsLevel).put("statsLayout", statsLayout).put("volume", volume)
        .put("rumble", rumble).put("maxFps", maxFps).put("brightness", brightness).put("contrast", contrast)
        .put("gamma", gamma.toDouble()).put("saturation", saturation).put("sharpen", sharpen).put("sharpenMode", sharpenMode)
        .put("deband", deband).put("fxaa", fxaa).put("toon", toon).put("crt", crt).put("ntsc", ntsc).put("profile", profile)
        .put("playerEngine", playerEngine).put("frameGen", frameGen)
        .put("fpsLimit", fpsLimit).put("fpsCustomOn", fpsCustomOn).put("fpsCustom", fpsCustom)
        .put("instantInput", instantInput).put("smallBuffer", smallBuffer).put("fastDecoder", fastDecoder).put("zeroCopy", zeroCopy)
        .toString()

    /** The picture-effect part, in the Wine profile format (KeyValueSet). */
    fun effectsKeyValues(): String = "brightness=$brightness,contrast=$contrast,gamma=$gamma,fxaa=$fxaa,crt_shader=$crt," +
        "toon_shader=$toon,ntsc_effect=$ntsc,saturation=$saturation,sharpen=$sharpen,sharpen_mode=$sharpenMode,deband=$deband"

    fun withEffects(values: KeyValueSet, name: String) = copy(
        brightness = values.getFloat("brightness", 0f).toInt().coerceIn(-50, 50),
        contrast = values.getFloat("contrast", 0f).toInt().coerceIn(-100, 100),
        gamma = values.getFloat("gamma", 1f).coerceIn(0.5f, 3f),
        fxaa = values.getBoolean("fxaa", false), crt = values.getBoolean("crt_shader", false),
        toon = values.getBoolean("toon_shader", false), ntsc = values.getBoolean("ntsc_effect", false),
        saturation = values.getFloat("saturation", 100f).toInt().coerceIn(0, 200),
        sharpen = values.getFloat("sharpen", 0f).toInt().coerceIn(0, 10),
        sharpenMode = values.get("sharpen_mode").takeIf { it == "usm" } ?: "cas",
        deband = values.getFloat("deband", 0f).toInt().coerceIn(0, 8),
        profile = name,
    )

    fun withoutEffects() = copy(brightness = 0, contrast = 0, gamma = 1f, saturation = 100, sharpen = 0, sharpenMode = "cas",
        deband = 0, fxaa = false, toon = false, crt = false, ntsc = false, profile = "")

    companion object {
        private const val PREFS = "xbox_cloud"
        private const val KEY = "settings"
        private const val WINE_PROFILES = "screen_effect_profiles"

        fun load(context: Context): XboxCloudSettings = runCatching {
            val json = JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return XboxCloudSettings())
            val d = XboxCloudSettings()
            XboxCloudSettings(
                resolution = json.optString("resolution", d.resolution).takeIf { it in setOf("720p", "1080p", "1080p_hq") } ?: d.resolution,
                region = json.optString("region", d.region), language = json.optString("language", d.language),
                codec = json.optString("codec", d.codec).takeIf { it in setOf("default", "high", "normal", "low") } ?: d.codec,
                bitrateMbps = json.optInt("bitrateMbps", d.bitrateMbps).coerceIn(0, 100),
                preventDrops = json.optBoolean("preventDrops", d.preventDrops), statsLevel = json.optInt("statsLevel", if (json.optBoolean("stats")) 3 else 0).coerceIn(0, 4),
                statsLayout = json.optString("statsLayout", d.statsLayout).takeIf { it == "vertical" } ?: "horizontal",
                volume = json.optInt("volume", d.volume).coerceIn(100, 300), rumble = json.optInt("rumble", d.rumble).coerceIn(0, 100),
                maxFps = json.optInt("maxFps", d.maxFps).coerceIn(0, 120),
                brightness = json.optInt("brightness", d.brightness).coerceIn(-50, 50), contrast = json.optInt("contrast", d.contrast).coerceIn(-100, 100),
                gamma = json.optDouble("gamma", 1.0).toFloat().coerceIn(0.5f, 3f), saturation = json.optInt("saturation", d.saturation).coerceIn(0, 200),
                sharpen = json.optInt("sharpen", d.sharpen).coerceIn(0, 10),
                sharpenMode = json.optString("sharpenMode", d.sharpenMode).takeIf { it == "usm" } ?: "cas",
                deband = json.optInt("deband", d.deband).coerceIn(0, 8),
                fxaa = json.optBoolean("fxaa"), toon = json.optBoolean("toon"), crt = json.optBoolean("crt"), ntsc = json.optBoolean("ntsc"),
                profile = json.optString("profile", ""),
                playerEngine = json.optString("playerEngine", d.playerEngine).takeIf { it == "native" } ?: "web",
                frameGen = json.optString("frameGen", d.frameGen).takeIf { it in setOf("off", "winfg", "lsfg", "lsfg3", "lsfg4") } ?: d.frameGen,
                fpsLimit = json.optInt("fpsLimit", d.fpsLimit).takeIf { it in setOf(0, 30, 45, 60, 120, 144, 165) } ?: 0,
                fpsCustomOn = json.optBoolean("fpsCustomOn", d.fpsCustomOn),
                fpsCustom = json.optInt("fpsCustom", d.fpsCustom).coerceIn(10, 165),
                instantInput = json.optBoolean("instantInput", d.instantInput), smallBuffer = json.optBoolean("smallBuffer", d.smallBuffer),
                fastDecoder = json.optBoolean("fastDecoder", d.fastDecoder), zeroCopy = json.optBoolean("zeroCopy", d.zeroCopy),
            )
        }.getOrDefault(XboxCloudSettings())

        fun save(context: Context, settings: XboxCloudSettings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, settings.toJson()).apply()
        }

        /** The Wine games' screen-effect profiles: name -> settings. */
        fun wineProfiles(context: Context): Map<String, KeyValueSet> =
            PreferenceManager.getDefaultSharedPreferences(context).getStringSet(WINE_PROFILES, emptySet()).orEmpty()
                .mapNotNull { entry -> entry.substringBefore(':').takeIf { it.isNotBlank() }?.let { it to KeyValueSet(entry.substringAfter(':', "")) } }
                .sortedBy { it.first.lowercase() }.toMap()

        /** Saves the current picture effects as a screen-effect profile (shared with the Wine games). */
        fun saveWineProfile(context: Context, name: String, settings: XboxCloudSettings) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val profiles = LinkedHashSet(prefs.getStringSet(WINE_PROFILES, emptySet()).orEmpty())
            profiles.removeAll { it.substringBefore(':') == name }
            profiles.add("$name:${settings.effectsKeyValues()}")
            prefs.edit().putStringSet(WINE_PROFILES, profiles).apply()
        }

        val LANGUAGES = listOf("" to "Device language", "en-US" to "English (US)", "en-GB" to "English (UK)", "de-DE" to "Deutsch",
            "es-ES" to "Español (España)", "es-MX" to "Español (México)", "fr-FR" to "Français", "it-IT" to "Italiano",
            "ja-JP" to "日本語", "ko-KR" to "한국어", "nl-NL" to "Nederlands", "pl-PL" to "Polski", "pt-BR" to "Português (Brasil)",
            "pt-PT" to "Português (Portugal)", "ru-RU" to "Русский", "sv-SE" to "Svenska", "tr-TR" to "Türkçe", "zh-CN" to "中文 (简体)",
            "zh-TW" to "中文 (繁體)")
    }
}
