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

    /** Blocking (network): call off the main thread. Null when there is nothing to offer or the catalog is unreachable. */
    fun check(context: Context): Available? {
        if (checkedThisRun) return null
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
        checkedThisRun = true

        val all = contents.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON) ?: return null
        // An installed profile's verName is the catalog row's versionName, so "<versionName>-<verCode>" lines them up.
        val installed = all.filter { it.remoteUrl.isNullOrEmpty() }.map { entryKey(it.verName, it.verCode) }.toSet()
        val geRows = all.filter { !it.remoteUrl.isNullOrEmpty() && isGeArm64ec(it) }
        val have = geRows.filter { entryKey(it.versionName, it.verCode) in installed }.maxWithOrNull(ORDER) ?: return null
        val newest = geRows.maxWithOrNull(ORDER) ?: return null
        if (ORDER.compare(newest, have) <= 0) return null

        val key = entryKey(newest.versionName, newest.verCode)
        if (PreferenceManager.getDefaultSharedPreferences(app).getString(PREF_SKIP, null) == key) return null
        Log.i(TAG, "Newer GE-Proton available: $key (installed ${entryKey(have.versionName, have.verCode)})")
        return Available(
            profile = newest,
            key = key,
            entryName = "${ContentProfile.ContentType.CONTENT_TYPE_PROTON}-$key",
            label = label(newest),
            installedLabel = label(have),
            sizeBytes = ContainerLayerUpdater.probeRemoteSize(newest.remoteUrl),
        )
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
     * is left as it was and counted in [SwitchResult.failed]. Blocking: call off the main thread.
     */
    fun switchAll(context: Context, entryName: String): SwitchResult {
        val app = context.applicationContext
        val contents = ContentsManager(app).apply { syncContents() }
        val updater = ContainerLayerUpdater(app)
        var moved = 0
        val failed = ArrayList<String>()
        for (c in switchCandidates(app, entryName)) {
            updater.switchLine(contents, c, entryName).fold(
                onSuccess = { moved++ },
                onFailure = { failed += (it.message ?: "\"${c.name}\" could not be switched") },
            )
        }
        return SwitchResult(moved, failed)
    }

    data class SwitchResult(val moved: Int, val failed: List<String>)

    private fun isGeArm64ec(p: ContentProfile): Boolean =
        p.verName.orEmpty().startsWith(GE_PREFIX) && p.versionName.orEmpty().endsWith(ARM64EC_SUFFIX)

    private fun entryKey(versionName: String?, verCode: Int) = "${versionName.orEmpty()}-$verCode"

    private fun label(p: ContentProfile) = "$GE_PREFIX ${p.versionName.orEmpty().removeSuffix(ARM64EC_SUFFIX)} arm64ec"

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
