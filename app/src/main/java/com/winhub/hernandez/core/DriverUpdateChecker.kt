package com.winhub.hernandez.core

import android.content.Context
import android.util.Log
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.Downloader
import org.json.JSONArray
import org.json.JSONException

/**
 * The StevenMXZ Turnip builds: the newest one for this GPU, and whether a newer one than the installed
 * one exists. A8xx (Adreno 830/840) takes the Gen8 line; every other Adreno takes the A7xx line. The
 * Qualcomm `v8xx` packages, the OneUI and the patched builds in the same feed are not Turnip for this
 * purpose and are never picked: only the two plain filenames below count.
 */
object DriverUpdateChecker {
    private const val TAG = "DriverUpdate"
    private const val FEED = "https://raw.githubusercontent.com/The412Banner/Nightlies/refs/heads/main/stevenmxz_drivers.json"
    private const val RELEASES = "https://github.com/The412Banner/Nightlies/releases/download/stevenmxz-drivers"

    /** Used when the feed is unreachable on first run, so a fresh install still gets the matching driver. */
    private const val PINNED_GEN8 = "$RELEASES/v39_Turnip_Gen8_V39.zip"
    private const val PINNED_A7XX = "$RELEASES/v39_Turnip_v27.0.0-R2.zip"
    private const val PREF_ONEUI = "driver_oneui"

    enum class Family { GEN8, A7XX }

    /** [oneUi]: the build made for HyperOS 3 and One UI, the same version as the plain one with "-Oneui" on its file name. Never offered: the plain build plus the OneUI / HyperOS fix in the wrapper settings covers every chip. */
    class Build(val family: Family, val rank: List<Int>, val label: String, val url: String, val oneUi: Boolean = false)

    /** An installed driver; [id] is what a container's graphicsDriverConfig `version` holds. */
    class Installed(val id: String, val family: Family, val rank: List<Int>, val label: String, val oneUi: Boolean = false)

    class Update(val build: Build, val have: Installed)

    private val GEN8_FILE = Regex("^Turnip_Gen8_V(\\d+)\\.zip$")
    private val A7XX_FILE = Regex("^Turnip_v(\\d+)\\.(\\d+)\\.(\\d+)-R(\\d+)\\.zip$")
    private val GEN8_NAME = Regex("^Turnip Gen8 V(\\d+)")
    private val A7XX_NAME = Regex("^Turnip (\\d+)\\.(\\d+)\\.(\\d+)-R(\\d+)")
    private val ADRENO = Regex("adreno[^0-9]*(\\d{3})", RegexOption.IGNORE_CASE)

    /** Null on a non-Adreno GPU (Mali and the rest keep their own driver flow). */
    fun deviceFamily(context: Context): Family? {
        val renderer: String? = try {
            GPUInformation.getRenderer(null, context)
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "GPU renderer not readable", e)
            null
        }
        val model = ADRENO.find(renderer ?: return null)?.groupValues?.get(1) ?: return null
        return if (model.startsWith("8")) Family.GEN8 else Family.A7XX
    }

    /** Blocking (network). Null when the feed is unreachable or unreadable. */
    fun fetchBuilds(): List<Build>? {
        val json = Downloader.downloadString(FEED) ?: return null
        val out = ArrayList<Build>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val url = array.getJSONObject(i).optString("remoteUrl")
                if (url.isEmpty()) continue
                // "v39_Turnip_Gen8_V39.zip" -> "Turnip_Gen8_V39.zip"
                val file = url.substringAfterLast('/').substringAfter('_')
                GEN8_FILE.find(file)?.let { m ->
                    val n = m.groupValues[1].toInt()
                    out += Build(Family.GEN8, listOf(n), "Turnip Gen8 V$n", url)
                }
                A7XX_FILE.find(file)?.let { m ->
                    val r = m.groupValues.drop(1).map { it.toInt() }
                    out += Build(Family.A7XX, r, "Turnip ${r[0]}.${r[1]}.${r[2]}-R${r[3]}", url)
                }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Driver feed unreadable", e)
            return null
        }
        return out
    }

    /** The user's HyperOS 3 / One UI answer from the first-run setup: new containers start with the OneUI / HyperOS fix on. The driver is the plain Turnip either way. */
    fun oneUiPreferred(context: Context): Boolean =
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext).getBoolean(PREF_ONEUI, false)

    fun setOneUiPreferred(context: Context, value: Boolean) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit().putBoolean(PREF_ONEUI, value).apply()
    }

    /** The newest plain build of [family]. */
    fun newest(builds: List<Build>, family: Family): Build? =
        builds.filter { it.family == family && !it.oneUi }.maxWithOrNull(Comparator<Build> { a, b -> compareRank(a.rank, b.rank) })

    /** The installed StevenMXZ Turnip drivers (by their meta.json name). Blocking (disk). */
    fun installed(context: Context): List<Installed> {
        val manager = AdrenotoolsManager(context.applicationContext)
        val out = ArrayList<Installed>()
        for (id in manager.enumarateInstalledDrivers()) {
            val name = manager.getDriverName(id)
            // The OneUI builds carry the same version as the plain ones; they are told apart, never ranked against them.
            val oneUi = name.contains("oneui", ignoreCase = true)
            GEN8_NAME.find(name)?.let { m ->
                val n = m.groupValues[1].toInt()
                out += Installed(id, Family.GEN8, listOf(n), "Turnip Gen8 V$n", oneUi)
            }
            A7XX_NAME.find(name)?.let { m ->
                val r = m.groupValues.drop(1).map { it.toInt() }
                out += Installed(id, Family.A7XX, r, "Turnip ${r[0]}.${r[1]}.${r[2]}-R${r[3]}" + if (oneUi) " OneUI" else "", oneUi)
            }
        }
        return out
    }

    /** The newest installed driver of [family], the plain build first; a OneUI build someone installed by hand only when there is no plain one. */
    fun newestInstalled(context: Context, family: Family): Installed? {
        val inFamily = installed(context).filter { it.family == family }
        val byRank = Comparator<Installed> { a, b -> compareRank(a.rank, b.rank) }
        return inFamily.filter { !it.oneUi }.maxWithOrNull(byRank) ?: inFamily.maxWithOrNull(byRank)
    }

    /** This GPU's family, its newest installed driver and the newest in the feed (null when the feed is unreachable). */
    class Inspection(val family: Family, val have: Installed?, val newest: Build?)

    /** Null on a non-Adreno GPU. Blocking (network): call off the main thread. */
    fun inspect(context: Context): Inspection? {
        val family = deviceFamily(context) ?: return null
        return Inspection(family, newestInstalled(context, family), fetchBuilds()?.let { newest(it, family) })
    }

    /** Blocking (network). Only for a device that already has a driver of its family installed. */
    fun check(context: Context): Update? {
        val inspection = inspect(context) ?: return null
        val have = inspection.have ?: return null
        val build = inspection.newest ?: return null
        if (compareRank(build.rank, have.rank) <= 0) return null
        Log.i(TAG, "Newer ${inspection.family.name} driver available: ${build.label} (installed ${have.label})")
        return Update(build, have)
    }

    /** What first run installs: the newest build for this GPU, or the pinned one offline. Null on non-Adreno. Blocking. */
    fun firstRunUrl(context: Context): String? {
        val family = deviceFamily(context) ?: return null
        val live = fetchBuilds()?.let { newest(it, family) }?.url
        return live ?: if (family == Family.GEN8) PINNED_GEN8 else PINNED_A7XX
    }

    fun compareRank(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
