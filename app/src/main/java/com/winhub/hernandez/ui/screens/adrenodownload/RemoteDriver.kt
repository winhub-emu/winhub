package com.winhub.hernandez.ui.screens.adrenodownload

import android.content.Context

/**
 * One feed within a driver source. A source can chain several feeds; [RemoteDriverRepository.fetchEntries]
 * fetches them IN ORDER and concatenates the results (feed 1 on top, later feeds below), deduped by URL.
 */
sealed class DriverFeed {
    /** The existing format: a JSON array of `{verName, remoteUrl}` at [url]. */
    data class Json(val url: String) : DriverFeed()

    /**
     * A GitHub repo whose releases' `.zip` assets are each a driver. [assetPattern] (a regex, matched
     * anywhere in the asset name) keeps only those assets, for repos that attach several packages of one
     * driver to each release; null = every `.zip`.
     */
    data class GithubReleases(val owner: String, val repo: String, val assetPattern: String? = null) : DriverFeed()
}

data class RemoteDriverSource(
    val name: String,
    val feeds: List<DriverFeed>,
    val builtIn: Boolean = false,
) {
    companion object {
        /** Convenience for the common single-JSON-feed source. */
        fun json(name: String, url: String, builtIn: Boolean = false) =
            RemoteDriverSource(name, listOf(DriverFeed.Json(url)), builtIn)
    }
}

data class RemoteDriverEntry(
    val source: String,
    val displayName: String,
    val downloadUrl: String,
)

object DriverSources {
    private const val NIGHTLIES = "https://raw.githubusercontent.com/The412Banner/Nightlies/refs/heads/main"

    /** The bundled sources, in display order. All [builtIn]. */
    val BUILT_IN: List<RemoteDriverSource> = listOf(
        RemoteDriverSource.json("Kimchi", "$NIGHTLIES/kimchi_drivers.json", builtIn = true),
        // Lists Maxes' curated MTR json first, then every WinNative-Emu/Drivers release .zip below.
        RemoteDriverSource(
            name = "MTR / WinNative Drivers",
            feeds = listOf(
                DriverFeed.Json("$NIGHTLIES/mtr_drivers.json"),
                DriverFeed.GithubReleases("WinNative-Emu", "Drivers"),
            ),
            builtIn = true,
        ),
        RemoteDriverSource.json("Banners-Turnip", "$NIGHTLIES/banners-turnip_drivers.json", builtIn = true),
        RemoteDriverSource.json("StevenMXZ", "$NIGHTLIES/stevenmxz_drivers.json", builtIn = true),
        RemoteDriverSource.json("whitebelyash", "$NIGHTLIES/white_drivers.json", builtIn = true),
    )

    /**
     * PanVK (Mesa) drivers for Arm Mali GPUs. Listed only on a Mali GPU ([builtIns]); everywhere else
     * the source list is exactly [BUILT_IN].
     */
    val MALI_BUILT_IN: List<RemoteDriverSource> = listOf(
        // Each release carries one package per launcher; the -bannerlator.zip is the one made for us
        // (same driver as -adrenotools.zip, plus a README and an ICD json for loading it without the wrapper).
        RemoteDriverSource(
            name = "PanVK-kbase (Mali)",
            feeds = listOf(DriverFeed.GithubReleases("zenithblue-oss", "panvk-kbase-android", "(?i)-bannerlator\\.zip$")),
            builtIn = true,
        ),
        RemoteDriverSource(
            name = "FristOneRR PanVK (Mali)",
            feeds = listOf(DriverFeed.GithubReleases("FristOneRR", "FristOneRR-Panvk-Driver")),
            builtIn = true,
        ),
        // Built for the Mali-G615.
        RemoteDriverSource(
            name = "PanVK G615 (GunaCharanTeja)",
            feeds = listOf(DriverFeed.GithubReleases("GunaCharanTeja", "panvk-kbase-android", "(?i)-adrenotools\\.zip$")),
            builtIn = true,
        ),
    )

    /** The built-in sources for this device: the Mali ones first on a Mali GPU, then [BUILT_IN]. */
    fun builtIns(): List<RemoteDriverSource> =
        if (com.winhub.hernandez.core.MaliPanvk.isMaliGpu()) MALI_BUILT_IN + BUILT_IN else BUILT_IN

    /**
     * The live source list the UI + smart installer use: built-ins whose name the user hasn't disabled
     * (in original order), followed by the user's custom sources. Backed by [DriverSourceStore].
     */
    fun allSources(context: Context): List<RemoteDriverSource> {
        val store = DriverSourceStore(context)
        val disabled = store.disabledBuiltIns()
        return builtIns().filter { it.name !in disabled } + store.customSources()
    }
}
