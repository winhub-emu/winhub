package com.winhub.hernandez.core

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerLayerUpdater
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.contents.Downloader

/**
 * Looks in the Official catalog for a GE-Proton arm64ec newer than the newest one installed.
 *
 * Unlike [ContainerLayerUpdater], which only offers a higher revision of the SAME line, this crosses
 * lines (11.0-7 -> 11.0-9), because GE ships a new version rather than a new revision. It only speaks
 * up for users who already have a GE-Proton arm64ec installed, and it never touches a container:
 * the download just adds the layer to the Contents list.
 */
object ProtonUpdateChecker {
    private const val TAG = "ProtonUpdate"
    private const val PREF_SKIP = "proton_update_skip"
    private const val PREF_GE_KEYS = "proton_ge_keys"
    /** The GE-Proton first run installs; known even before any catalog lookup has run. */
    private const val PINNED_GE_KEY = "11.0-9-arm64ec-16"
    private const val GE_PREFIX = "GE-Proton"
    private const val ARM64EC_SUFFIX = "-arm64ec"

    /** One lookup per app run; a failed (offline) lookup does not count, so the next Deck open retries. */
    @Volatile private var checkedThisRun = false

    /**
     * [profile] is the catalog row the contents downloader takes; [key] is what [skip] remembers;
     * [entryName] is the contents entry the install creates (what a container's wineVersion holds).
     */
    data class Available(
        val profile: ContentProfile,
        val key: String,
        val entryName: String,
        val label: String,
        val installedLabel: String,
        val sizeBytes: Long?,
    )

    private val ORDER = Comparator<ContentProfile> { a, b ->
        val byVersion = compareVersions(a.versionName.orEmpty(), b.versionName.orEmpty())
        if (byVersion != 0) byVersion else a.verCode.compareTo(b.verCode)
    }

    /** The newest installed GE-Proton arm64ec ([have]) and the newest one in the catalog ([newest]); either may be null. */
    class Inspection(val have: ContentProfile?, val newest: ContentProfile?)

    /**
     * Reads the Official catalog (cached for the run, else fetched) and compares it with what is installed.
     * No once-per-run or skip gating: the Updater page uses it directly. Null when the catalog is
     * unreachable. Blocking (network): call off the main thread.
     */
    fun inspect(context: Context): Inspection? {
        val app = context.applicationContext
        val contents = ContentsManager(app)
        val cached = ContentsManager.getCachedRemoteProfiles()
        if (cached != null) {
            contents.setRemoteProfiles(cached)
        } else {
            val json = Downloader.downloadString(ContentsManager.REMOTE_PROFILES)
            if (json == null) {
                Log.w(TAG, "Catalog unreachable; no Proton update check")
                return null
            }
            contents.setRemoteProfiles(json)
        }
        val all = contents.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON) ?: return null
        // An installed profile's verName is the catalog row's versionName, so "<versionName>-<verCode>" lines them up.
        val installed = all.filter { it.remoteUrl.isNullOrEmpty() }.map { entryKey(it.verName, it.verCode) }.toSet()
        val geRows = all.filter { !it.remoteUrl.isNullOrEmpty() && isGeArm64ec(it) }
        // An installed layer is just "Proton-<versionName>-<code>", so which ones are GE is remembered from the catalog.
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putStringSet(PREF_GE_KEYS, geRows.map { entryKey(it.versionName, it.verCode) }.toSet()).apply()
        val have = geRows.filter { entryKey(it.versionName, it.verCode) in installed }.maxWithOrNull(ORDER)
        return Inspection(have, geRows.maxWithOrNull(ORDER))
    }

    /** The offer for [inspection]'s newest build: when it is newer than [Inspection.have], or when none is installed. */
    fun availableFrom(context: Context, inspection: Inspection, allowFresh: Boolean): Available? {
        val newest = inspection.newest ?: return null
        val have = inspection.have
        if (have == null && !allowFresh) return null
        if (have != null && ORDER.compare(newest, have) <= 0) return null
        val key = entryKey(newest.versionName, newest.verCode)
        return Available(
            profile = newest,
            key = key,
            entryName = "${ContentProfile.ContentType.CONTENT_TYPE_PROTON}-$key",
            label = label(newest),
            installedLabel = have?.let { label(it) } ?: "none installed",
            sizeBytes = ContainerLayerUpdater.probeRemoteSize(newest.remoteUrl),
        ).also { Log.i(TAG, "Newer GE-Proton available: $key (installed ${have?.let { h -> entryKey(h.versionName, h.verCode) }})") }
    }

    /**
     * What the Deck's popup offers: once per app run, only to someone who already has a GE-Proton arm64ec,
     * and not for a version they chose to skip. Blocking (network): call off the main thread.
     */
    fun check(context: Context): Available? {
        if (checkedThisRun) return null
        val app = context.applicationContext
        val inspection = inspect(app) ?: return null
        checkedThisRun = true
        val newest = inspection.newest ?: return null
        if (inspection.have == null) return null
        if (PreferenceManager.getDefaultSharedPreferences(app).getString(PREF_SKIP, null) == entryKey(newest.versionName, newest.verCode)) return null
        return availableFrom(app, inspection, allowFresh = false)
    }

    /** The newest installed GE-Proton arm64ec as a label ("GE-Proton 11.0-9 arm64ec"), read from disk, or null. */
    fun installedLabel(context: Context): String? {
        val app = context.applicationContext
        val installed = ContentsManager(app).apply { syncContents() }
            .getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON).orEmpty()
            .filter { it.remoteUrl.isNullOrEmpty() }.map { ContentsManager.getEntryName(it) }
        val entry = geEntriesNewestFirst(app, installed).firstOrNull() ?: return null
        return "$GE_PREFIX ${entry.removePrefix("Proton-").substringBeforeLast('-').removeSuffix(ARM64EC_SUFFIX)} arm64ec"
    }

    /** Entry names ("Proton-11.0-9-arm64ec-16") among [entries] that are GE-Proton arm64ec builds, newest first. */
    fun geEntriesNewestFirst(context: Context, entries: List<String>): List<String> {
        val known = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getStringSet(PREF_GE_KEYS, null).orEmpty() + PINNED_GE_KEY
        return entries.filter { it.startsWith("Proton-") && it.removePrefix("Proton-") in known }
            .sortedWith { a, b -> compareKeys(b.removePrefix("Proton-"), a.removePrefix("Proton-")) }
    }

    private fun compareKeys(a: String, b: String): Int {
        val byVersion = compareVersions(a.substringBeforeLast('-'), b.substringBeforeLast('-'))
        if (byVersion != 0) return byVersion
        return (a.substringAfterLast('-').toIntOrNull() ?: 0).compareTo(b.substringAfterLast('-').toIntOrNull() ?: 0)
    }

    /** "Skip this version": stays quiet until an even newer build appears. */
    fun skip(context: Context, available: Available) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit().putString(PREF_SKIP, available.key).apply()
    }

    /** Containers [switchAll] would move onto [entryName]: on an arm64ec contents layer, not already on it. Blocking (disk). */
    fun switchCandidates(context: Context, entryName: String): List<Container> =
        ContainerManager(context.applicationContext).containers.filter {
            !WineInfo.isMainWineVersion(it.wineVersion) && it.wineVersion.contains("arm64ec") && it.wineVersion != entryName
        }

    /**
     * Moves every [switchCandidates] container onto [entryName] through [ContainerLayerUpdater.switchLine], which
     * snapshots each one first so its "Revert layer" works. A container that cannot move (running, layer gone)
     * is left as it was and counted in [ComponentUpdates.SwitchResult.failed]. Blocking: call off the main thread.
     */
    fun switchAll(context: Context, entryName: String): ComponentUpdates.SwitchResult {
        val app = context.applicationContext
        val contents = ContentsManager(app).apply { syncContents() }
        val updater = ContainerLayerUpdater(app)
        val done = ArrayList<Pair<Container, ContainerLayerUpdater.Snapshot>>()
        val failed = ArrayList<String>()
        for (c in switchCandidates(app, entryName)) {
            updater.switchLine(contents, c, entryName).fold(
                onSuccess = { done += c to it },
                onFailure = { failed += (it.message ?: "\"${c.name}\" could not be switched") },
            )
        }
        val undo: (() -> Unit)? = if (done.isEmpty()) null else ({
            val fresh = ContentsManager(app).apply { syncContents() }
            for ((c, snapshot) in done) updater.revert(fresh, c, snapshot)
        })
        return ComponentUpdates.SwitchResult(done.size, failed, undo)
    }

    private fun isGeArm64ec(p: ContentProfile): Boolean =
        p.verName.orEmpty().startsWith(GE_PREFIX) && p.versionName.orEmpty().endsWith(ARM64EC_SUFFIX)

    private fun entryKey(versionName: String?, verCode: Int) = "${versionName.orEmpty()}-$verCode"

    internal fun label(p: ContentProfile) = "$GE_PREFIX ${p.versionName.orEmpty().removeSuffix(ARM64EC_SUFFIX)} arm64ec"

    /** Numeric, dot/dash aware: 11.0-7.1 > 11.0-7, 11.0-10 > 11.0-9. */
    private fun compareVersions(a: String, b: String): Int {
        val pa = numbers(a)
        val pb = numbers(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = pa.getOrElse(i) { 0L }.compareTo(pb.getOrElse(i) { 0L })
            if (c != 0) return c
        }
        return 0
    }

    private fun numbers(v: String): List<Long> =
        Regex("\\d+").findAll(v.removeSuffix(ARM64EC_SUFFIX)).map { it.value.toLongOrNull() ?: 0L }.toList()
}
