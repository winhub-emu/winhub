package com.winhub.hernandez.communityconfigs

import android.content.Context
import android.util.Log
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.core.GPUInformation
import com.winhub.hernandez.ui.screens.ContainerDetailViewModel
import com.winhub.hernandez.ui.screens.ExeShortcutImporter
import com.winhub.hernandez.ui.screens.HubGame
import com.winhub.hernandez.ui.screens.HubGamesStore
import com.winhub.hernandez.ui.screens.contents.ContentsInstaller
import com.winhub.hernandez.ui.screens.contents.RemoteSourceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Social Hub game configs (GitHub issues labelled "Configs"). Same config JSON as the Cloudflare
 * Community Configs, plus a `sources` block recording which repository each component came from,
 * so an import can fetch every component from that exact source.
 */
object SocialConfigs {

    private const val TAG = "SocialConfigs"

    /** A slow or failed cover-art lookup must not stall the import forever. */
    private const val COVER_WAIT_MS = 45_000L

    data class Exported(val title: String, val json: String)

    data class ImportOutcome(val ok: Boolean, val message: String)

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    // ── Export ──────────────────────────────────────────────────────────────────────────────────

    /** Serialize [shortcut]'s effective settings into a shareable config, with component sources. */
    fun export(context: Context, shortcut: Shortcut): Exported {
        val root = JSONObject(ShortcutExporter.fromShortcut(shortcut, context).json)
        // The worker's delete-recovery token has no meaning on GitHub and would be public.
        root.optJSONObject("meta")?.remove("upload_token")
        root.put("sources", sourcesFor(context, root))
        return Exported(shortcut.name, root.toString(2))
    }

    /** Component name -> repository it was downloaded from, for every component we have a download record for. */
    private fun sourcesFor(context: Context, root: JSONObject): JSONArray {
        val names = LinkedHashSet<String>()
        root.optJSONArray("components")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(names::add)
        }
        root.optJSONObject("settings")?.optString("pc_ls_CONTAINER_LIST")?.takeIf { it.isNotBlank() }?.let { raw ->
            try {
                JSONObject(raw).optString("name").takeIf { it.isNotBlank() }?.let(names::add)
            } catch (e: JSONException) {
                Log.w(TAG, "unreadable pc_ls_CONTAINER_LIST", e)
            }
        }
        val downloads = RemoteSourceRepository(context).getAllDownloads()
        val out = JSONArray()
        for (name in names) {
            val key = norm(name)
            if (key.isEmpty()) continue
            val hit = downloads.firstOrNull { norm(it.fileName).contains(key) } ?: continue
            out.put(JSONObject().put("name", name).put("source", hit.sourceName))
        }
        return out
    }

    // ── Import ──────────────────────────────────────────────────────────────────────────────────

    /** One-line summary for the list/detail UI: component names the config asks for. */
    fun componentNames(json: String): List<String> = try {
        val arr = JSONObject(json).optJSONArray("components")
        (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
    } catch (e: JSONException) {
        emptyList()
    }

    /** `meta.device` / `meta.soc` of a config, either may be blank. */
    fun hardware(json: String): Pair<String, String> = try {
        val meta = JSONObject(json).optJSONObject("meta")
        (meta?.optString("device").orEmpty()) to (meta?.optString("soc").orEmpty())
    } catch (e: JSONException) {
        "" to ""
    }

    /**
     * Build a fresh dedicated (Cronos) container named [gameName], download every component the config
     * needs that isn't installed (from the recorded source, else any source), add [exe] as the game's
     * shortcut and apply the config to it. Progress lines go to [onStatus].
     */
    suspend fun importAsCronos(
        context: Context,
        configJson: String,
        gameName: String,
        exe: File,
        onStatus: (String) -> Unit,
    ): ImportOutcome {
        val ctx = context.applicationContext
        val root = try {
            JSONObject(configJson)
        } catch (e: JSONException) {
            return ImportOutcome(false, "That config is damaged and can't be read.")
        }
        val config = ConfigTranslator.translate(root)
        val sources = HashMap<String, String>()
        root.optJSONArray("sources")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                sources[o.optString("name")] = o.optString("source")
            }
        }
        val isAdreno = GPUInformation.isAdrenoGPU(ctx)
        val warnings = ArrayList<String>()
        var driverId: String? = null

        // 1. Components first, so the container picks the right Wine and the apply resolves everything.
        for (req in config.components) {
            val installedType = installedTypeOf(req.type)
            val installed = InstalledComponents.read(ctx)
            if (installed.resolve(installedType, req.target, null) is InstalledComponents.Resolution.Match) continue
            if (req.type == "Turnip" && !isAdreno) {
                warnings += "GPU driver ${req.wants} needs an Adreno GPU"
                continue
            }
            onStatus("Looking for ${req.wants}")
            val item = findRemote(ctx, req, sources[req.wants])
            if (item == null) {
                warnings += "${req.wants} not found in any repository"
                continue
            }
            onStatus("Downloading ${req.wants} from ${item.sourceName}")
            if (req.type == "Turnip") {
                val id = ContentsInstaller.installDriverAwait(ctx, item.downloadUrl, onStatus)
                if (id == null) warnings += "${req.wants} failed to install" else driverId = id
            } else if (!ContentsInstaller.installAwait(ctx, item.downloadUrl, onStatus)) {
                warnings += "${req.wants} failed to install"
            }
        }

        // 2. The Cronos container.
        onStatus("Creating container")
        val protonReq = config.components.firstOrNull { it.type == "Proton" }
        val container = createContainer(ctx, gameName, protonReq)
            ?: return ImportOutcome(false, "Couldn't create the container for \"$gameName\".")

        // 3. The game's shortcut. The importer's cover-art thread rewrites the .desktop, so the config is
        //    applied from its completion callback to avoid being clobbered by that write.
        onStatus("Adding $gameName")
        val coverDone = CompletableDeferred<Unit>()
        val desktop = try {
            withContext(Dispatchers.IO) {
                ExeShortcutImporter.addToShortcuts(ctx, container, exe, gameName, null, onCoverArtReady = { coverDone.complete(Unit) })
            }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "addToShortcuts failed", e)
            return ImportOutcome(false, "Container created, but adding the game failed: ${e.message ?: "I/O error"}")
        }
        withTimeoutOrNull(COVER_WAIT_MS) { coverDone.await() }

        // 4. Apply the settings.
        onStatus("Applying settings")
        val result = withContext(Dispatchers.IO) {
            val shortcut = Shortcut(container, desktop)
            val r = CommunityConfigApply.apply(shortcut, config, InstalledComponents.read(ctx), container.wineVersion, isAdreno)
            val id = driverId
            if (r.missingDrivers.isNotEmpty() && id != null) CommunityConfigApply.applyResolvedDriver(shortcut, id)
            r
        }
        withContext(Dispatchers.IO) {
            HubGamesStore.add(ctx, HubGame(gameName, container.id, desktop.absolutePath, null, null))
        }
        if (!result.ok) return ImportOutcome(false, result.message)
        result.missingComponents.forEach { warnings += "${it.label} is still missing" }
        val tail = if (warnings.isEmpty()) "" else " (" + warnings.joinToString("; ") + ")"
        return ImportOutcome(true, "Added \"$gameName\" in its own container$tail")
    }

    private fun installedTypeOf(type: String): String = if (type == "Turnip") "adrenotools" else type

    /** Same fresh-container recipe as Deck's Cronos path; Wine = the config's Proton if installed, else the first available. */
    private suspend fun createContainer(ctx: Context, name: String, protonReq: ComponentRequest?): Container? {
        val manager = withContext(Dispatchers.IO) { ContainerManager(ctx).also { it.reloadContainers() } }
        val data = withContext(Dispatchers.IO) {
            try {
                val cm = ContentsManager(ctx).also { it.syncContents() }
                val wineList =
                    cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE).map { ContentsManager.getEntryName(it) } +
                        cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON).map { ContentsManager.getEntryName(it) }
                val wanted = protonReq?.let { norm(it.target) }.orEmpty()
                val wine = wineList.firstOrNull { wanted.isNotEmpty() && norm(it).contains(wanted) }
                    ?: wineList.firstOrNull()
                    ?: manager.getContainers().firstOrNull()?.wineVersion
                    ?: Container(0, manager).wineVersion
                ContainerDetailViewModel.buildFreshCreateData(ctx, name, wine, true)
            } catch (e: Exception) {
                Log.w(TAG, "buildFreshCreateData failed", e)
                null
            }
        } ?: return null
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                manager.createContainerAsync(data, ContentsManager(ctx)) { created -> cont.resume(created) }
            }
        }
    }

    /**
     * The catalog entry for [req]: the recorded [hint] source first, exact name match preferred over a
     * contains-match, then any other source. Null when nothing matches.
     */
    private suspend fun findRemote(ctx: Context, req: ComponentRequest, hint: String?): RemoteSourceRepository.RemoteItem? {
        val repo = RemoteSourceRepository(ctx)
        val isDriver = req.type == "Turnip"
        val remoteTypes = when (req.type) {
            "Turnip" -> listOf(RemoteSourceRepository.GPU_DRIVER_TYPE)
            "Proton" -> listOf("Proton", "Wine")
            else -> listOf(req.type)
        }
        val wants = norm(req.wants)
        val target = norm(req.target)
        val ordered = repo.getAllSources().sortedBy { if (hint != null && it.name.equals(hint, ignoreCase = true)) 0 else 1 }
        var loose: RemoteSourceRepository.RemoteItem? = null
        for (src in ordered) {
            for (type in remoteTypes) {
                if (!isDriver && src.supportedTypes.isNotEmpty() && src.supportedTypes.none { it.equals(type, ignoreCase = true) }) continue
                val items = try {
                    repo.fetchFromSource(src, type)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "fetch ${src.name}/$type failed", e)
                    emptyList()
                }
                items.firstOrNull { norm(it.versionName) == wants || norm(it.displayName) == wants }?.let { return it }
                if (loose == null && target.length >= 3) {
                    loose = items.firstOrNull { norm(it.versionName).contains(target) || norm(it.displayName).contains(target) }
                }
            }
        }
        return loose
    }
}
