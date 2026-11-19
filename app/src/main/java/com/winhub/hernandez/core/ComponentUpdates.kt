package com.winhub.hernandez.core

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerLayerUpdater
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.ui.screens.contents.ContentsInstaller
import com.winhub.hernandez.ui.screens.warmWaylandAdapterReport
import com.winhub.hernandez.ui.screens.withGraphicsDriverVersion

/**
 * The "update layer" idea for the three things a container is built from: the Proton layer, the GPU
 * driver and the FEXCore build. Each [Offer] knows how to install the newer one and how to move the
 * containers that use an older one, with an undo for what it moved. [checkAll] is blocking (network).
 */
object ComponentUpdates {
    private const val TAG = "ComponentUpdates"
    private const val KIND_PROTON = "Proton"
    private const val KIND_DRIVER = "Driver"
    private const val KIND_FEX = "FEXCore"

    /** [undo] puts back what was moved; null when nothing moved. */
    class SwitchResult(val moved: Int, val failed: List<String>, val undo: (() -> Unit)?)

    /**
     * [install] returns the id of what it installed (what a container stores), or null on failure.
     * [candidates] and [switchAll] are blocking.
     */
    class Offer(
        val kind: String,
        val label: String,
        val installedLabel: String,
        val sizeBytes: Long?,
        val install: suspend (onStatus: (String) -> Unit) -> String?,
        val candidates: () -> Int,
        val switchAll: (installedId: String) -> SwitchResult,
        val skip: () -> Unit,
    )

    /** One pass per app run once the catalog has answered, so leaving and re-entering the Deck never asks again. */
    @Volatile private var doneThisRun = false

    fun checkAll(context: Context): List<Offer> {
        if (doneThisRun) return emptyList()
        val app = context.applicationContext
        val out = ArrayList<Offer>()
        // The Proton check loads the catalog that the FEXCore check reads from, so it goes first.
        guarded("Proton") { ProtonUpdateChecker.check(app)?.let { protonOffer(app, it) } }?.let { out += it }
        doneThisRun = ContentsManager.getCachedRemoteProfiles() != null
        guarded("driver") { driverOffer(app) }?.let { out += it }
        guarded("FEXCore") { fexOffer(app) }?.let { out += it }
        return out
    }

    private inline fun <T> guarded(what: String, block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "$what update check failed", e)
        null
    }

    // ── Proton ────────────────────────────────────────────────────────────────────────────────

    private fun protonOffer(app: Context, a: ProtonUpdateChecker.Available) = Offer(
        kind = KIND_PROTON,
        label = a.label,
        installedLabel = a.installedLabel,
        sizeBytes = a.sizeBytes,
        install = { onStatus ->
            val ok = ContentsInstaller.installAwait(app, a.profile.remoteUrl, onStatus)
            // The wcp's own profile.json names the entry; only report it when that is what landed.
            if (ok && ContentsManager(app).apply { syncContents() }.getProfileByEntryName(a.entryName) != null) a.entryName else null
        },
        candidates = { ProtonUpdateChecker.switchCandidates(app, a.entryName).size },
        switchAll = { id -> ProtonUpdateChecker.switchAll(app, id) },
        skip = { ProtonUpdateChecker.skip(app, a) },
    )

    // ── GPU driver ────────────────────────────────────────────────────────────────────────────

    private fun driverOffer(app: Context): Offer? {
        val up = DriverUpdateChecker.check(app) ?: return null
        if (skipped(app, KIND_DRIVER) == up.build.label) return null
        return Offer(
            kind = KIND_DRIVER,
            label = up.build.label,
            installedLabel = up.have.label,
            sizeBytes = ContainerLayerUpdater.probeRemoteSize(up.build.url),
            install = { onStatus -> ContentsInstaller.installDriverAwait(app, up.build.url, onStatus) },
            candidates = { driverCandidates(app, up.build).size },
            switchAll = { id -> switchDrivers(app, up.build, id) },
            skip = { setSkipped(app, KIND_DRIVER, up.build.label) },
        )
    }

    /** Containers on an installed driver of the same family that is older than [build]. */
    private fun driverCandidates(app: Context, build: DriverUpdateChecker.Build): List<Container> {
        val older = DriverUpdateChecker.installed(app)
            .filter { it.family == build.family && DriverUpdateChecker.compareRank(it.rank, build.rank) < 0 }
            .map { it.id }.toSet()
        if (older.isEmpty()) return emptyList()
        return ContainerManager(app).containers.filter { GraphicsDriverConfigDialog.getVersion(it.graphicsDriverConfig) in older }
    }

    private fun switchDrivers(app: Context, build: DriverUpdateChecker.Build, newId: String): SwitchResult {
        val updater = ContainerLayerUpdater(app)
        val previous = LinkedHashMap<Container, String>()
        val failed = ArrayList<String>()
        for (c in driverCandidates(app, build)) {
            if (updater.isRunning(c)) {
                failed += "\"${c.name}\" is running."
                continue
            }
            previous[c] = c.graphicsDriverConfig
            c.graphicsDriverConfig = withGraphicsDriverVersion(c.graphicsDriverConfig, newId)
            c.saveData()
        }
        // A Wayland container's adapter report is cached per driver; make the new driver's now (in the
        // background), or Auto runs without it and is slower until the container is next saved.
        if (previous.keys.any { it.isWaylandBackend }) warmWaylandAdapterReport(app, "version=$newId")
        val undo: (() -> Unit)? = if (previous.isEmpty()) null else ({
            for ((c, old) in previous) {
                c.graphicsDriverConfig = old
                c.saveData()
            }
        })
        return SwitchResult(previous.size, failed, undo)
    }

    // ── FEXCore ───────────────────────────────────────────────────────────────────────────────

    private val FEX_RELEASE = Regex("(\\d{4})(?:\\.(\\d+))?")

    /**
     * [year, revision] of a FEXCore build of the PPA-unix variant (the one first run installs), else null:
     * the plain, nightly and gn-cs2 builds are other variants and are never compared against it.
     */
    internal fun fexRank(name: String?): List<Int>? {
        if (name == null || !name.contains("PPA-unix") || name.contains("gn", ignoreCase = true)) return null
        val m = FEX_RELEASE.find(name) ?: return null
        return listOf(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0)
    }

    private fun newestInstalledFex(app: Context): ContentProfile? {
        val installed = ContentsManager(app).apply { syncContents() }.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE).orEmpty()
        return installed.filter { fexRank(it.verName) != null }
            .maxWithOrNull(Comparator { a, b -> DriverUpdateChecker.compareRank(fexRank(a.verName)!!, fexRank(b.verName)!!) })
    }

    private fun fexOffer(app: Context): Offer? {
        val remote = ContentsManager.getCachedRemoteProfiles() ?: return null
        val have = newestInstalledFex(app) ?: return null
        val newest = remote.filter { it.type == ContentProfile.ContentType.CONTENT_TYPE_FEXCORE && fexRank(it.verName) != null && !it.remoteUrl.isNullOrEmpty() }
            .maxWithOrNull(Comparator { a, b -> DriverUpdateChecker.compareRank(fexRank(a.verName)!!, fexRank(b.verName)!!) })
            ?: return null
        val newRank = fexRank(newest.verName)!!
        if (DriverUpdateChecker.compareRank(newRank, fexRank(have.verName)!!) <= 0) return null
        if (skipped(app, KIND_FEX) == newest.verName) return null
        return Offer(
            kind = KIND_FEX,
            label = newest.verName,
            installedLabel = have.verName,
            sizeBytes = ContainerLayerUpdater.probeRemoteSize(newest.remoteUrl),
            install = { onStatus ->
                if (ContentsInstaller.installAwait(app, newest.remoteUrl, onStatus)) {
                    // What a container stores is the entry name without its type: "FEXCore-2611.1-PPA-unix-0".
                    newestInstalledFex(app)?.takeIf { DriverUpdateChecker.compareRank(fexRank(it.verName)!!, newRank) >= 0 }
                        ?.let { ContentsManager.getEntryName(it).substringAfter('-') }
                } else null
            },
            candidates = { fexCandidates(app, newRank).size },
            switchAll = { id -> switchFex(app, newRank, id) },
            skip = { setSkipped(app, KIND_FEX, newest.verName) },
        )
    }

    /** Containers on an older PPA-unix FEXCore build. Containers on another variant keep theirs. */
    private fun fexCandidates(app: Context, newRank: List<Int>): List<Container> =
        ContainerManager(app).containers.filter {
            val rank = fexRank(it.getFEXCoreVersion())
            rank != null && DriverUpdateChecker.compareRank(rank, newRank) < 0
        }

    private fun switchFex(app: Context, newRank: List<Int>, newId: String): SwitchResult {
        val updater = ContainerLayerUpdater(app)
        val previous = LinkedHashMap<Container, String>()
        val failed = ArrayList<String>()
        for (c in fexCandidates(app, newRank)) {
            if (updater.isRunning(c)) {
                failed += "\"${c.name}\" is running."
                continue
            }
            previous[c] = c.getFEXCoreVersion()
            c.setFEXCoreVersion(newId)
            c.saveData()
        }
        val undo: (() -> Unit)? = if (previous.isEmpty()) null else ({
            for ((c, old) in previous) {
                c.setFEXCoreVersion(old)
                c.saveData()
            }
        })
        return SwitchResult(previous.size, failed, undo)
    }

    // ── Skip memory ───────────────────────────────────────────────────────────────────────────

    private fun skipKey(kind: String) = "component_update_skip_$kind"

    private fun skipped(app: Context, kind: String): String? =
        PreferenceManager.getDefaultSharedPreferences(app).getString(skipKey(kind), null)

    private fun setSkipped(app: Context, kind: String, label: String) {
        PreferenceManager.getDefaultSharedPreferences(app).edit().putString(skipKey(kind), label).apply()
    }
}
