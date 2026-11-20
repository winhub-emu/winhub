package com.winhub.hernandez.core

import android.content.Context
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.fexcore.FEXCorePreset

/**
 * What a brand-new arm64ec container starts on when the user has not saved their own New Container
 * Defaults: the newest GE-Proton, the GPU driver for this chip, DXVK 2.4.1-1 gplasync, the newest
 * FEXCore and the Performance FEX preset. Each lookup reads what is INSTALLED and returns null when it
 * finds nothing, so the caller keeps its normal default instead of pointing at a missing component.
 * Existing containers are never touched.
 */
object AutoDefaults {
    const val FEX_PRESET = FEXCorePreset.PERFORMANCE
    const val SCREEN_SIZE = "1600x720"

    /** The newest installed GE-Proton arm64ec among [entries] (layer entry names), or null. */
    fun wine(context: Context, entries: List<String>): String? =
        ProtonUpdateChecker.geEntriesNewestFirst(context, entries).firstOrNull()

    /** The newest PPA-unix FEXCore among [entries] (the container's version strings), or null. */
    fun fexVersion(entries: List<String>): String? =
        entries.filter { ComponentUpdates.fexRank(it) != null }
            .maxWithOrNull(Comparator { a, b -> DriverUpdateChecker.compareRank(ComponentUpdates.fexRank(a)!!, ComponentUpdates.fexRank(b)!!) })

    /** The newest installed StevenMXZ Turnip of this GPU's family (Gen8 on A8xx, A7xx on the rest), or null. Blocking (disk). */
    fun graphicsDriverId(context: Context): String? {
        val family = DriverUpdateChecker.deviceFamily(context) ?: return null
        return DriverUpdateChecker.newestInstalled(context, family)?.id
    }

    /** The installed regular DXVK 2.4.1 gplasync build (not the arm64ec or pre-reg ones), as the id the DXVK version setting holds, or null. */
    fun dxvkVersion(contents: ContentsManager): String? {
        val profile = contents.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK).orEmpty().firstOrNull {
            val name = it.verName.orEmpty()
            name.contains("gplasync", ignoreCase = true) && name.contains("2.4.1") &&
                !name.contains("arm64ec", ignoreCase = true) && !name.contains("pre-reg", ignoreCase = true)
        } ?: return null
        return ContentsManager.getEntryName(profile).substringAfter('-')
    }

    /** The installed VKD3D-Proton 3.0.1 arm64ec build, as the id the VKD3D version setting holds, or null. */
    fun vkd3dVersion(contents: ContentsManager): String? {
        val profile = contents.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D).orEmpty().firstOrNull {
            val name = it.verName.orEmpty()
            name.contains("3.0.1") && name.contains("arm64ec", ignoreCase = true)
        } ?: return null
        return com.winhub.hernandez.core.VKD3DVersionItem(profile.verName, profile.verCode).identifier
    }

    /** [config] (`k=v,k=v`) with each key of [values] replaced in place, or appended when absent; the rest untouched. */
    fun withDxKeys(config: String, values: Map<String, String>): String {
        val parts = config.split(",").filter { it.isNotEmpty() }.toMutableList()
        for ((key, value) in values) {
            val i = parts.indexOfFirst { it.startsWith("$key=") }
            if (i >= 0) parts[i] = "$key=$value" else parts.add("$key=$value")
        }
        return parts.joinToString(",")
    }

    /** The DXVK `version` a config (`k=v,k=v`) names, "" when it has none. */
    fun dxvkVersionOf(config: String): String =
        config.split(",").firstOrNull { it.startsWith("version=") }?.substringAfter("version=").orEmpty()

    /** [config] (`k=v,k=v`) with its DXVK `version` replaced, every other key kept as it was. */
    fun withDxvkVersion(config: String, version: String): String = withDxKeys(config, mapOf("version" to version))
}
