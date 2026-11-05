package com.winhub.hernandez.widget.fusionhud

import java.util.Locale

/**
 * Detail levels of the Fusion HUD, modelled on the Steam Deck's performance overlay: one visual
 * language, four amounts of detail. The config value is the token stored under the `hudSize` key.
 *
 *  1. [FPS]        the FPS number and the engine tag
 *  2. [GRAPH]      adds the frametime graph, the frametime in ms and battery watts
 *  3. [STATS]      adds GPU, CPU, VRAM, RAM, temperatures, clocks and battery
 *  4. [EVERYTHING] adds per-core load, the lows, GPU model, API/wrapper/Proton, resolution, network, swap
 *                  and session time
 *
 * Configs saved before the levels existed carry `minimal`, `pill`, `tiles`, `full` or `mega`; [from]
 * maps them onto the nearest level, so nobody's HUD changes meaning.
 */
enum class FusionSize(val token: String, val label: String) {
    FPS("1", "1 · FPS"),
    GRAPH("2", "2 · Graph"),
    STATS("3", "3 · Stats"),
    EVERYTHING("4", "4 · Everything");

    /** 1 to 4. */
    val level: Int get() = ordinal + 1

    /** Next level in the tap-cycle: 1 → 2 → 3 → 4 → 1. */
    fun next(): FusionSize {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }

    /**
     * The metric toggle chips this level actually renders: the SINGLE source of truth queried by BOTH
     * config surfaces (the Deck HUD editor and the in-game drawer) so the chip list can't drift from what
     * the view draws. Chip ids are the chip LABELS used in the UI. (Hiding a chip is purely UI;
     * buildConfig() still emits every key.)
     */
    fun supportedChips(): Set<String> = when (this) {
        FPS -> FPS_CHIPS
        GRAPH -> GRAPH_CHIPS
        STATS -> STATS_CHIPS
        EVERYTHING -> EVERYTHING_CHIPS
    }

    companion object {
        /** A stored token to a level: "1".."4", or any size name from before the levels. */
        fun from(token: String?): FusionSize = when (token?.trim()?.lowercase(Locale.US)) {
            "1", "minimal" -> FPS
            "2", "pill" -> GRAPH
            "3", "tiles", "full" -> STATS
            "4", "mega" -> EVERYTHING
            else -> STATS
        }

        // Chip-label ids (must match the labels the config UIs build). "Clock" sits on the level 4 TIME row.
        val FPS_CHIPS = setOf("FPS", "FPS .1", "Engine")
        val GRAPH_CHIPS = FPS_CHIPS + setOf("FPS graph", "Power", "Battery")
        val STATS_CHIPS = GRAPH_CHIPS + setOf("GPU", "GPU temp", "CPU", "Temp", "VRAM", "RAM")
        val EVERYTHING_ONLY = setOf(
            "GPU model", "0.01% low", "Per-core", "Swap", "Network", "Resolution", "Proton", "Wrapper",
            "DX ver", "Session", "Clock",
        )
        val EVERYTHING_CHIPS = STATS_CHIPS + EVERYTHING_ONLY
    }
}

/**
 * The HUD outline strength, 0..100. Older configs stored "off", "soft" or "strong"; those map to 0, 40
 * and 70, and a number parses directly.
 */
internal fun parseHudOutline(v: String?): Int = when (v?.trim()?.lowercase(Locale.US)) {
    null -> 40
    "off" -> 0
    "soft" -> 40
    "strong" -> 70
    else -> v.trim().toIntOrNull()?.coerceIn(0, 100) ?: 40
}

/**
 * The Wayland HDR state the Fusion HUD shows on its own line, directly under the latency · display-server
 * line (the host sets it with [FusionHudView.setHdrState]). [NONE] = no line at all: every session whose
 * HDR gate is closed (X11, SDR screens, HDR off) keeps the HUD exactly as it was.
 */
object FusionHdr {
    const val NONE = 0
    const val ON = 1           // HDR frames on screen with HDR headroom
    const val NO_HEADROOM = 2  // HDR frames on screen, but the display gives them no headroom (5 s+)
    const val OFF = 3          // the drawer's HDR output switch is off (tone-mapped to SDR)
    const val READY = 4        // HDR open for the session, no HDR frames on screen right now
    const val TONEMAPPED = 5   // HDR output on, but the frames are shown tone-mapped (frame generation
                               // on a screen that offers no HDR10 swapchain)
    const val NOT_ON_THIS_SCREEN = 6 // HDR open for the session, but the screen the game is on NOW
                               // reports no HDR10 (the TV was unplugged mid-game). The offer to the
                               // game cannot be withdrawn once made, so the gate stays open for the
                               // session — but "ready" would be a lie on a panel that cannot show it.
}
