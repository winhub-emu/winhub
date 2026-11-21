package com.winhub.hernandez.ui.screens

import android.app.Application
import android.content.Context
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.winhub.hernandez.communityconfigs.AccountManager
import com.winhub.hernandez.communityconfigs.CanonicalDevice
import com.winhub.hernandez.communityconfigs.CanonicalGame
import com.winhub.hernandez.communityconfigs.CommunityConfigApply
import com.winhub.hernandez.communityconfigs.CommunityConfigFetcher
import com.winhub.hernandez.communityconfigs.CommunityConfigRef
import com.winhub.hernandez.communityconfigs.CommunityConfigRepository
import com.winhub.hernandez.communityconfigs.CommunityConfigWorker
import com.winhub.hernandez.communityconfigs.WorkerComment
import com.winhub.hernandez.communityconfigs.WorkerConfigEntry
import com.winhub.hernandez.communityconfigs.ConfigMeta
import com.winhub.hernandez.communityconfigs.ConfigTranslator
import com.winhub.hernandez.communityconfigs.ShortcutConfig
import com.winhub.hernandez.communityconfigs.DeviceIdentity
import com.winhub.hernandez.communityconfigs.GameMatcher
import com.winhub.hernandez.communityconfigs.InstalledComponents
import com.winhub.hernandez.communityconfigs.ShortcutExporter
import com.winhub.hernandez.communityconfigs.UploadedConfigsStore
import com.winhub.hernandez.communityconfigs.UploadedConfigsStore.UploadedConfig
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.GPUInformation
import com.winhub.hernandez.core.GameFolderScanner
import com.winhub.hernandez.core.GameIdentifier
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.store.UbisoftSupport
import com.winhub.hernandez.ui.screens.adrenodownload.DriverSources
import com.winhub.hernandez.ui.screens.adrenodownload.RemoteDriverRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections

enum class ShortcutSortOrder { NAME_ASC, NAME_DESC, CONTAINER }

/**
 * How the games library is laid out. GRID is the original adaptive grid; GRID_COMPACT fixes four
 * columns so more covers fit at once; XMB is the PS3-style cross media bar (games across, that
 * game's options down). Ordinals are persisted — append, never reorder.
 */
enum class ShortcutViewMode { LIST, GRID, GRID_COMPACT, XMB }

sealed class ImportResult {
    /** [appId] = the Steam appId identified on disk (if any), so the confirm dialog can seed
     *  its "Search Steam" box and apply the right cover art. */
    data class Success(val shortcutName: String, val appId: Int? = null) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/** Outcome of a bulk games-folder import. [failures] carries one line per game that couldn't be
 *  written, so a partial success can say which ones need attention rather than just a count. */
data class BulkImportSummary(val added: Int, val failed: Int, val failures: List<String>)

/** An asynchronous auto-rename applied by the importer's background thread (Steam name upgrade),
 *  surfaced so an open confirm/rename dialog can keep its Save target and pre-filled name in sync. */
data class ImportedNameUpdate(val oldBase: String, val newBase: String)

/**
 * Result of matching a shortcut against the community-config index. [match] is null when nothing
 * plausibly overlapped (clean empty state); [rankedDevices] surfaces the user's-hardware rows first.
 */
data class CommunityMatchResult(
    val query: String,
    val match: CanonicalGame?,
    val rankedDevices: List<CanonicalDevice>,
    val userHardwareLabel: String?,
    // Raw detected hardware — kept alongside the display label so the sheet's per-config list can drive
    // the "Matches my device" filter (which matches SoC/GPU against each config's device/soc strings).
    val userSoc: String? = null,
    val userGpu: String? = null,
    // Genuine ambiguity: the candidates that tied [match] on score (top-first, [match] included, capped).
    // Empty when the top match is unambiguous OR the game was chosen/remembered — the UI only draws the
    // "Which game is this?" picker when size > 1. (issue #167)
    val alternatives: List<CanonicalGame> = emptyList(),
)

/**
 * The whole community catalog plus the detected hardware, delivered to the catalog browser. Loaded
 * off the main thread, offline-first; [games] is empty when the index is unavailable (empty state).
 */
data class CommunityCatalog(
    val games: List<CanonicalGame>,
    val userSoc: String?,
    val userGpu: String?,
    val hardwareLabel: String?,
    // Friendly device name (manufacturer + model), DISPLAY-ONLY — shown alongside [hardwareLabel] in
    // the "Your device" header. Never used for matching; [hardwareLabel] remains the sole match key.
    val deviceModel: String?,
)

/**
 * One row in the "My uploads" list: the locally-recorded [record] (reinstall-proof, from the manifest)
 * plus the LIVE [votes] / [downloads] re-read from the worker. [stillOnline] is false when the worker no
 * longer lists this sha/filename (deleted server-side, or unreachable) — the delete path then just prunes
 * the local record.
 */
data class MyUploadRow(
    val record: UploadedConfig,
    val votes: Int,
    val downloads: Int,
    val stillOnline: Boolean,
)

/**
 * Everything the read-only Community Config detail page renders — provenance ([meta]), the config in
 * our own shortcut terms ([config]), and, when a target shortcut was supplied, the non-mutating
 * pre-apply diff ([preview]). All of it comes from the same fetch+translate the apply path uses; the
 * detail page adds NO new network surface. [preview] is null when no shortcut was given (the browser
 * "View details" with no chosen target) or when the fetch failed.
 */
data class CommunityConfigDetail(
    val game: CanonicalGame,
    val device: CanonicalDevice,
    val fileName: String,
    val meta: ConfigMeta,
    val config: ShortcutConfig,
    val preview: CommunityConfigApply.ConfigApplyResult?,
    // Live social layer from the configs worker (best-effort; blank/empty when offline or unmatched).
    // [workerGame] is the game key the matching /list entry lived under — reused for vote/comment so
    // they land on the same KV bucket; null when no /list entry matched [fileName].
    val sha: String? = null,
    val workerGame: String? = null,
    val votes: Int = 0,
    val downloads: Int = 0,
    val description: String = "",
    val comments: List<WorkerComment> = emptyList(),
)

class ShortcutsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("shortcuts_prefs", Context.MODE_PRIVATE)

    private val _shortcuts = MutableStateFlow<List<Shortcut>>(emptyList())

    // Emitted when the importer's background thread upgrades a shortcut's name to Steam's
    // authoritative title. The Shortcuts screen observes this to follow the rename in an open
    // confirm dialog. One-shot: consumed via [consumeImportedNameUpdate].
    private val _importedNameUpdate = MutableStateFlow<ImportedNameUpdate?>(null)
    val importedNameUpdate: StateFlow<ImportedNameUpdate?> = _importedNameUpdate
    fun consumeImportedNameUpdate() { _importedNameUpdate.value = null }

    private val _sortOrder = MutableStateFlow(
        ShortcutSortOrder.entries[
            prefs.getInt("sort_order", ShortcutSortOrder.NAME_ASC.ordinal)
                .coerceIn(0, ShortcutSortOrder.entries.size - 1)
        ]
    )
    val sortOrder: StateFlow<ShortcutSortOrder> = _sortOrder

    // Three layouts now, so the old is_grid_view boolean cannot express it. Read the boolean once
    // to seed the new key, so anyone already on grid stays on grid instead of being reset to list.
    private val _viewMode = MutableStateFlow(
        ShortcutViewMode.entries[
            prefs.getInt(
                "view_mode",
                if (prefs.getBoolean("is_grid_view", false)) ShortcutViewMode.GRID.ordinal
                else ShortcutViewMode.LIST.ordinal
            ).coerceIn(0, ShortcutViewMode.entries.size - 1)
        ].let { if (it == ShortcutViewMode.XMB) ShortcutViewMode.GRID else it }
    )
    val viewMode: StateFlow<ShortcutViewMode> = _viewMode

    val shortcuts: kotlinx.coroutines.flow.Flow<List<Shortcut>> =
        combine(_shortcuts, _sortOrder) { list, order ->
            val sorted = when (order) {
                ShortcutSortOrder.NAME_ASC   -> list.sortedBy { it.name.lowercase() }
                ShortcutSortOrder.NAME_DESC  -> list.sortedByDescending { it.name.lowercase() }
                ShortcutSortOrder.CONTAINER  -> list.sortedBy { (it.container?.name ?: "").lowercase() }
            }
            sorted
        }

    private val manager = ContainerManager(app)

    private val communityRepo = CommunityConfigRepository(app)

    init {
        refresh()
    }

    /**
     * Matches [shortcut] against the community-config index off the main thread and delivers the
     * result back on the main thread. Offline-first: served from cache instantly, refreshed in the
     * background; when the index is unavailable [CommunityMatchResult.match] is null (empty state).
     */
    fun matchCommunityConfigs(shortcut: Shortcut, onResult: (CommunityMatchResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val games = communityRepo.getGames()
                val ranked = GameMatcher.match(shortcut.name, games)

                // Honor a remembered per-shortcut pick FIRST: once the user has told us which game a
                // generic name (e.g. "Dragon Age") refers to, resolve straight to it with no picker.
                val rememberedId = shortcut.getExtra("communityGameIdentity", "")
                val remembered = rememberedId.takeIf { it.isNotBlank() }
                    ?.let { id -> games.firstOrNull { it.identity == id } }

                // Then an AUTHORITATIVE Steam appId, when the shortcut carries one. Steam-identified
                // games persist their appid in the `steamAppId` extra (via the cover-art flow; it
                // rides the .desktop through renames), and the canonical index is keyed BY appid
                // (CanonicalGame.identity), so this is an exact, unambiguous match that beats fuzzy
                // name matching and needs no "Which game is this?" picker. Non-Steam titles or a
                // not-yet-resolved appid simply fall through to the name match below. A user's
                // remembered pick still wins over the appid (explicit override).
                val appId = shortcut.getExtra("steamAppId", "").takeIf { it.isNotBlank() }
                val byAppId = if (remembered != null) null
                    else appId?.let { id -> games.firstOrNull { it.steamAppId == id } }

                val best = remembered ?: byAppId ?: ranked.firstOrNull()?.game
                // Surface genuine ties as alternatives: candidates whose score sits within a tiny
                // epsilon of the top score, top-first (top match included), capped. size <= 1 → no
                // ambiguity, so no picker. Suppressed entirely once we have a DEFINITIVE identity —
                // a remembered pick or an exact appId match.
                val alternatives = if (remembered != null || byAppId != null) emptyList() else {
                    val top = ranked.firstOrNull()?.score
                    if (top == null) emptyList()
                    else ranked.filter { top - it.score <= TIE_EPSILON }
                        .map { it.game }
                        .take(MAX_TIE_ALTERNATIVES)
                        .let { if (it.size <= 1) emptyList() else it }
                }

                val userSoc = DeviceIdentity.soc()
                val userGpu = DeviceIdentity.gpu(getApplication())
                val devices = best?.let { GameMatcher.rankDevices(it.devices, userSoc, userGpu) } ?: emptyList()
                CommunityMatchResult(
                    query = shortcut.name,
                    match = best,
                    rankedDevices = devices,
                    userHardwareLabel = userSoc ?: userGpu,
                    userSoc = userSoc,
                    userGpu = userGpu,
                    alternatives = alternatives,
                )
            }
            onResult(result)
        }
    }

    /**
     * Remember, per shortcut, which canonical game a (possibly ambiguous) name maps to — so the
     * "Which game is this?" picker (and a manual search-pick) only has to be answered once. Persists
     * onto the shortcut's own extra data via [Shortcut.saveData]; [matchCommunityConfigs] reads it back
     * on the next open. Off the main thread. (issue #167)
     */
    fun rememberCommunityGame(shortcut: Shortcut, game: CanonicalGame) {
        viewModelScope.launch(Dispatchers.IO) {
            shortcut.putExtra("communityGameIdentity", game.identity)
            shortcut.saveData()
        }
    }

    /** Free-text search across the whole community DB — the manual-pick fallback when auto-match misses. */
    fun searchCommunityGames(query: String, onResult: (List<CanonicalGame>) -> Unit) {
        viewModelScope.launch {
            val games = withContext(Dispatchers.IO) { GameMatcher.search(query, communityRepo.getGames()) }
            onResult(games)
        }
    }

    /** Build the same suggest view for a game the user picked manually from search. */
    fun selectCommunityGame(game: CanonicalGame, onResult: (CommunityMatchResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val userSoc = DeviceIdentity.soc()
                val userGpu = DeviceIdentity.gpu(getApplication())
                CommunityMatchResult(
                    query = game.name,
                    match = game,
                    rankedDevices = GameMatcher.rankDevices(game.devices, userSoc, userGpu),
                    userHardwareLabel = userSoc ?: userGpu,
                    userSoc = userSoc,
                    userGpu = userGpu,
                )
            }
            onResult(result)
        }
    }

    /**
     * Loads the full community catalog + detected hardware for the catalog browser, off the main
     * thread. Offline-first (cache-served); empty [CommunityCatalog.games] when unavailable.
     */
    fun getCommunityCatalog(onResult: (CommunityCatalog) -> Unit) {
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) {
                val games = communityRepo.getGames()
                val userSoc = DeviceIdentity.soc()
                val userGpu = DeviceIdentity.gpu(getApplication())
                CommunityCatalog(games, userSoc, userGpu, userSoc ?: userGpu, DeviceIdentity.deviceModel())
            }
            onResult(catalog)
        }
    }

    /**
     * FORCE-refresh the community index (bypasses the in-mem + disk/24h cache) so the catalog browser
     * picks up freshly-folded uploads on demand. Delivers a rebuilt [CommunityCatalog] on the main
     * thread so the browser re-renders with the fresh games list, or null when the refresh failed
     * (offline / bad body) — the caller then keeps the previously loaded index and shows a toast.
     */
    fun refreshCommunityIndex(onResult: (CommunityCatalog?) -> Unit) {
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) {
                val games = communityRepo.refreshIndex() ?: return@withContext null
                val userSoc = DeviceIdentity.soc()
                val userGpu = DeviceIdentity.gpu(getApplication())
                CommunityCatalog(games, userSoc, userGpu, userSoc ?: userGpu, DeviceIdentity.deviceModel())
            }
            onResult(catalog)
        }
    }

    /**
     * Fetch every uploaded config for [game] across ALL of its worker folders and merge them. A
     * canonical game aggregates several BannerHub folder names under one appid, so we must query each
     * folder (in parallel) and CONCATENATE — querying only the first non-empty folder drops the rest.
     * Each entry is paired with the folder (`/list` key) it came from so its [CommunityConfigRef.workerGame]
     * is correct PER ENTRY (vote/comment/download then hit the right KV bucket). The merged set is
     * deduped by sha (globally unique per repo file; folder+filename when sha is blank) and re-sorted
     * votes-desc then date-desc, because the worker only sorts WITHIN a folder. Falls back to the game
     * name as a key only when [CanonicalGame.folders] is empty. Empty on offline / bucket miss → the UI
     * falls back to per-device rows.
     *
     * BOTH namespaces are read: for every canonical folder we query BannerHub (no ns) AND our own
     * `bannerlator` repo in parallel; each folder in [extraBannerlatorFolders] is queried in the
     * `bannerlator` namespace ONLY (the per-shortcut sheet passes the shortcut's own sanitized folder
     * so the user's OWN upload — which isn't in the canonical index yet — is still found). Every entry
     * keeps its `appSource` so the UI can badge Bannerlator-shared configs.
     */
    fun fetchGameConfigs(
        game: CanonicalGame,
        extraBannerlatorFolders: List<String> = emptyList(),
        onResult: (List<Pair<String, WorkerConfigEntry>>) -> Unit,
    ) {
        viewModelScope.launch {
            val merged = withContext(Dispatchers.IO) {
                val keys = game.folders.ifEmpty { listOf(game.name) }.distinct()
                val extras = extraBannerlatorFolders.filter { it.isNotBlank() }.distinct()
                // Per canonical folder: BannerHub (default) + our namespaced repo, both in parallel.
                // Per extra folder: our namespaced repo only.
                val jobs = ArrayList<kotlinx.coroutines.Deferred<Pair<String, List<WorkerConfigEntry>>>>()
                for (key in keys) {
                    jobs.add(async { key to CommunityConfigWorker.list(key) })
                    jobs.add(async { key to CommunityConfigWorker.list(key, "bannerlator") })
                }
                for (key in extras) {
                    jobs.add(async { key to CommunityConfigWorker.list(key, "bannerlator") })
                }
                val perFolder = jobs.awaitAll()
                val seen = HashSet<String>()
                val out = ArrayList<Pair<String, WorkerConfigEntry>>()
                for ((folder, list) in perFolder) {
                    for (entry in list) {
                        val dedupKey = entry.sha.ifBlank { "$folder/${entry.filename}" }
                        if (seen.add(dedupKey)) out.add(folder to entry)
                    }
                }
                out.sortedWith(
                    compareByDescending<Pair<String, WorkerConfigEntry>> { it.second.votes }
                        .thenByDescending { it.second.date }
                )
            }
            onResult(merged)
        }
    }

    /** Snapshot of the current shortcut list — the target picker for "Apply to game…". */
    fun currentShortcuts(): List<Shortcut> = _shortcuts.value

    /**
     * Full Phase 2 apply: fetch the config for [game]+[device], translate it, resolve components
     * against what's installed, SURGICALLY merge into [shortcut], persist, and report back. All IO is
     * off the main thread; every failure returns a clean [CommunityConfigApply.ConfigApplyResult].
     */
    fun applyCommunityConfig(
        shortcut: Shortcut,
        game: CanonicalGame,
        device: CanonicalDevice,
        onResult: (CommunityConfigApply.ConfigApplyResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val fetched = CommunityConfigFetcher.fetchForDevice(game, device)
                    ?: return@withContext CommunityConfigApply.ConfigApplyResult(
                        ok = false,
                        message = "Couldn't fetch a config for ${device.model.ifBlank { "that device" }} " +
                            "(offline, or no matching file in the repo).",
                    )
                val config = ConfigTranslator.translate(fetched.json)
                val installed = InstalledComponents.read(getApplication())
                CommunityConfigApply.apply(
                    shortcut = shortcut,
                    config = config,
                    installed = installed,
                    containerWineVersion = shortcut.container?.getWineVersion(),
                    isAdreno = GPUInformation.isAdrenoGPU(getApplication()),
                )
            }
            if (result.ok && result.changed.isNotEmpty()) refresh()
            onResult(result)
        }
    }

    /**
     * Per-uploaded-config twin of [applyCommunityConfig]: fetch THAT exact file (via the worker's
     * `/download`), translate it, resolve components against what's installed, SURGICALLY merge into
     * [shortcut], persist, and report back. Same downstream flow as [applyCommunityConfig] — only the
     * fetch differs (an exact file instead of the best-for-device pick). All IO is off the main thread.
     */
    fun applyCommunityConfigFile(
        shortcut: Shortcut,
        ref: CommunityConfigRef,
        onResult: (CommunityConfigApply.ConfigApplyResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val fetched = CommunityConfigFetcher.fetchForFile(ref.workerGame, ref.filename, ref.ns)
                    ?: return@withContext CommunityConfigApply.ConfigApplyResult(
                        ok = false,
                        message = "Couldn't fetch that config (offline, or it's no longer in the repo).",
                    )
                val config = ConfigTranslator.translate(fetched.json)
                val installed = InstalledComponents.read(getApplication())
                CommunityConfigApply.apply(
                    shortcut = shortcut,
                    config = config,
                    installed = installed,
                    containerWineVersion = shortcut.container?.getWineVersion(),
                    isAdreno = GPUInformation.isAdrenoGPU(getApplication()),
                )
            }
            if (result.ok && result.changed.isNotEmpty()) refresh()
            onResult(result)
        }
    }

    /**
     * PHASE 3 step 2 — EXPORT. Resolve [shortcut]'s effective settings into a shareable config
     * artifact via [ShortcutExporter]. Off the main thread (pure reads + string work); the caller
     * hands the returned [ShortcutExporter.ExportResult] to the share sheet / Save-to-Downloads path.
     */
    fun exportShortcutConfig(
        shortcut: Shortcut,
        onResult: (ShortcutExporter.ExportResult) -> Unit,
    ) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { ShortcutExporter.fromShortcut(shortcut, getApplication()) }
            onResult(res)
        }
    }

    /**
     * PHASE 3 (online sharing) — UPLOAD. Share [shortcut]'s effective config to OUR community repo
     * (namespace {@code bannerlator}, never seen by BannerHub users). Builds the same artifact
     * [exportShortcutConfig] does, then, off the main thread: base64s the JSON and POSTs it to the
     * worker's {@code /upload}. Records the result in [UploadedConfigsStore] (reinstall-proof) so a
     * later share can offer to replace it.
     *
     * If the user already has an upload for this game, [onExisting] is invoked on the main thread with
     * that record plus a {@code proceed} and a {@code cancel} lambda; the flow SUSPENDS until the UI
     * calls one. {@code cancel()} exits the coroutine cleanly (nothing uploaded, no result toast) rather
     * than leaving it parked. On a confirmed replace the old upload is best-effort deleted first.
     * [onStart] fires on the main thread the instant the actual upload begins (after any confirm), so the
     * button can switch its busy text from "Preparing…" to "Uploading…". [onResult] is delivered on the
     * main thread.
     */
    fun uploadShortcutConfig(
        shortcut: Shortcut,
        onExisting: (UploadedConfig, proceed: () -> Unit, cancel: () -> Unit) -> Unit,
        onStart: () -> Unit,
        onResult: (ok: Boolean, message: String) -> Unit,
    ) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val res = withContext(Dispatchers.IO) { ShortcutExporter.fromShortcut(shortcut, app) }
            // The provenance the store records lives in the config's own meta (written by ConfigExporter).
            val meta = try { JSONObject(res.json).optJSONObject("meta") } catch (e: Exception) { null }
            val token = meta?.optString("upload_token", "")?.trim().orEmpty()
            val soc = meta?.optString("soc", "")?.trim().orEmpty()
            val device = meta?.optString("device", "")?.trim().orEmpty()
            if (token.isEmpty()) {
                onResult(false, "Upload failed — check your connection and try again.")
                return@launch
            }

            val existing = withContext(Dispatchers.IO) { UploadedConfigsStore.forGame(app, res.game) }
            if (existing != null) {
                // Ask the UI to confirm the replace, then wait here until it calls proceed()/cancel().
                // cancel() completes the gate with false so the coroutine unwinds instead of hanging.
                val gate = CompletableDeferred<Boolean>()
                onExisting(existing, { gate.complete(true) }, { gate.complete(false) })
                if (!gate.await()) return@launch
            }

            // Past the (optional) confirm — the real upload starts now, so flip the button text.
            onStart()

            val ok = withContext(Dispatchers.IO) {
                val b64 = Base64.encodeToString(res.json.toByteArray(), Base64.NO_WRAP)
                // Attribute the upload to the signed-in account when logged in (Phase 2); null = anonymous.
                // The uploader name/avatar is already stamped into res.json's meta by ShortcutExporter.
                val session = AccountManager.session(app)
                val uploaded = CommunityConfigWorker.upload(res.game, res.fileName, b64, token, session = session)
                    ?: return@withContext false
                // Replace confirmed: retire the previous upload (best-effort — ignore failure).
                if (existing != null) {
                    CommunityConfigWorker.deleteUpload(existing.sha, existing.game, existing.filename, existing.token)
                }
                UploadedConfigsStore.add(
                    app,
                    UploadedConfig(
                        game = res.game,
                        filename = res.fileName,
                        sha = uploaded.sha,
                        token = token,
                        soc = soc,
                        device = device,
                        date = System.currentTimeMillis(),
                    ),
                )
                true
            }
            if (ok) onResult(true, "Shared \"${res.game}\" with the community.")
            else onResult(false, "Upload failed — check your connection and try again.")
        }
    }

    /**
     * PHASE 2 (optional accounts) — CREATE. Register a new account off the main thread; on success
     * [AccountManager.createAccount] has already persisted the session + recovery backup locally, so the
     * UI only needs the [AccountManager.CreateData] to show the one-time recovery key. Delivered on main.
     */
    fun createAccount(
        username: String,
        password: String,
        onResult: (AccountManager.AccountResult<AccountManager.CreateData>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AccountManager.createAccount(getApplication(), username, password)
            }
            onResult(result)
        }
    }

    /**
     * PHASE 2 (optional accounts) — LOGIN. Sign in off the main thread; on success the session is already
     * persisted locally by [AccountManager.login]. PHASE 4 (cross-device recovery): on success we also fold
     * the account's server-side upload registry into [UploadedConfigsStore] (see [restoreUploads]) so "My
     * uploads" — and, because each entry carries its {@code token}, the delete / edit-description actions —
     * work on a freshly-installed device. A restore failure only logs; it never blocks the login. Delivered
     * on the main thread.
     */
    fun loginAccount(
        username: String,
        password: String,
        onResult: (AccountManager.AccountResult<AccountManager.LoginData>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val res = AccountManager.login(getApplication(), username, password)
                if (res is AccountManager.AccountResult.Success) restoreUploads(res.data.uploads)
                res
            }
            onResult(result)
        }
    }

    /**
     * PHASE 4 (optional accounts) — CROSS-DEVICE RECOVERY. Rebuild [UploadedConfig] rows from an account's
     * server-side [uploads] registry and [UploadedConfigsStore.merge] them into the local store (dedup by
     * sha; existing local records win). {@code token} is carried straight through so Delete / Edit work on
     * the restored rows; {@code date} comes from the entry's {@code ts}; {@code soc}/{@code device} are a
     * best-effort tail-parse of the filename ({@code <game>-<mfr>-<model>-<soc>-<ts>.json}) and left blank
     * when ambiguous — they're display-only. Best-effort: any failure logs and is swallowed so it can never
     * break login. Runs on the caller's IO context.
     */
    private fun restoreUploads(uploads: List<AccountManager.AccountUpload>) {
        try {
            if (uploads.isEmpty()) return
            val records = uploads.mapNotNull { u ->
                if (u.sha.isBlank()) return@mapNotNull null
                val (soc, device) = provenanceFromFilename(u.filename)
                UploadedConfig(
                    game = u.game,
                    filename = u.filename,
                    sha = u.sha,
                    token = u.token,
                    soc = soc,
                    device = device,
                    // Registry ts is UNIX seconds; the store keeps millis (0 when the worker omitted it).
                    date = if (u.ts > 0L) u.ts * 1000L else 0L,
                )
            }
            UploadedConfigsStore.merge(getApplication(), records)
        } catch (e: Exception) {
            Log.w("CommunityConfigs", "Upload registry restore failed", e)
        }
    }

    /**
     * Best-effort split of a community-config file name — {@code <game>-<mfr>-<model>-<soc>-<unixSeconds>
     * .json} — into display-only {@code soc} and {@code device} ("mfr model"). Fields may themselves
     * contain hyphens, so this is only reliable from the tail: it requires an all-digits trailing segment
     * and at least the five expected fields, and returns blanks otherwise. Never throws.
     */
    private fun provenanceFromFilename(filename: String): Pair<String, String> {
        return try {
            val base = filename.removeSuffix(".json")
            if (base.isBlank()) return "" to ""
            val parts = base.split("-")
            // Need game + mfr + model + soc + ts, and the tail must be the numeric timestamp.
            if (parts.size < 5 || parts.last().toLongOrNull() == null) return "" to ""
            val soc = parts[parts.size - 2].trim()
            val model = parts[parts.size - 3].trim()
            val mfr = parts[parts.size - 4].trim()
            val device = listOf(mfr, model).filter { it.isNotEmpty() }.joinToString(" ")
            soc to device
        } catch (e: Exception) {
            "" to ""
        }
    }

    /**
     * PHASE 2 (optional accounts) — RESET. Reset the password with the recovery key off the main thread;
     * on success [AccountManager.resetPassword] logs the user in with the fresh session. Delivered on main.
     */
    fun resetAccountPassword(
        username: String,
        recoveryKey: String,
        newPassword: String,
        onResult: (AccountManager.AccountResult<AccountManager.ResetData>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                AccountManager.resetPassword(getApplication(), username, recoveryKey, newPassword)
            }
            onResult(result)
        }
    }

    /**
     * PHASE 3 (online sharing) — MY UPLOADS. Read the user's own upload records from
     * [UploadedConfigsStore] (which hydrates from the durable manifest on a fresh install, so this list
     * survives a reinstall), then re-read each one's LIVE votes / downloads from the worker
     * ({@code list(game, "bannerlator")}, matched by sha then filename). Missing on the server →
     * {@code stillOnline = false}, stats 0 (it may have been deleted, or we're offline). All IO off the
     * main thread; delivered on the main thread.
     */
    fun loadMyUploads(onResult: (List<MyUploadRow>) -> Unit) {
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                UploadedConfigsStore.all(getApplication()).map { rec ->
                    val live = CommunityConfigWorker.list(rec.game, "bannerlator")
                    val match = live.firstOrNull { it.sha == rec.sha }
                        ?: live.firstOrNull { it.filename == rec.filename }
                    MyUploadRow(
                        record = rec,
                        votes = match?.votes ?: 0,
                        downloads = match?.downloads ?: 0,
                        stillOnline = match != null,
                    )
                }
            }
            onResult(rows)
        }
    }

    /**
     * Delete one of the user's own uploads. Retires it server-side via
     * {@code deleteUpload(sha, game, filename, token)} (skipped when it's already gone), then prunes the
     * local record so the manifest + SP cache no longer list it. Returns false ONLY when the server
     * delete was attempted and failed (network) — the caller keeps the row and toasts. All IO off-main.
     */
    fun deleteMyUpload(row: MyUploadRow, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val serverOk = if (!row.stillOnline) true else CommunityConfigWorker.deleteUpload(
                    row.record.sha, row.record.game, row.record.filename, row.record.token,
                )
                if (serverOk) UploadedConfigsStore.remove(getApplication(), row.record.sha)
                serverOk
            }
            onDone(ok)
        }
    }

    /**
     * Load the current uploader description for [row] (to prefill the edit field), via {@code /desc?sha=}.
     * Empty string when none / offline. Off the main thread.
     */
    fun loadMyUploadDescription(row: MyUploadRow, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { CommunityConfigWorker.desc(row.record.sha) }
            onResult(text)
        }
    }

    /**
     * Set/replace the uploader description for [row] via {@code /describe} (authorized by the upload
     * token that minted it). [text] is clamped to the worker's 500-char limit. Returns true on success.
     * Off the main thread.
     */
    fun editMyUploadDescription(row: MyUploadRow, text: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                CommunityConfigWorker.describe(row.record.sha, row.record.token, text.take(500))
            }
            onDone(ok)
        }
    }

    /**
     * PHASE 3 step 2 — IMPORT. Read a config file at [uri], translate it, resolve components against
     * what's installed, then SURGICALLY merge into [target] — the identical apply path a browsed
     * config takes, so smart-install works for imported files too. All IO is off the main thread; a
     * missing/unreadable/malformed file returns a clean [CommunityConfigApply.ConfigApplyResult]
     * (ok=false) instead of throwing.
     */
    fun importConfigFile(
        uri: Uri,
        target: Shortcut,
        onResult: (CommunityConfigApply.ConfigApplyResult) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                        ?: return@withContext CommunityConfigApply.ConfigApplyResult(
                            ok = false,
                            message = "Couldn't read that file.",
                        )
                    val config = ConfigTranslator.translate(JSONObject(text))
                    val installed = InstalledComponents.read(getApplication())
                    CommunityConfigApply.apply(
                        shortcut = target,
                        config = config,
                        installed = installed,
                        containerWineVersion = target.container?.getWineVersion(),
                        isAdreno = GPUInformation.isAdrenoGPU(getApplication()),
                    )
                } catch (e: Exception) {
                    CommunityConfigApply.ConfigApplyResult(
                        ok = false,
                        message = "That file isn't a valid config (${e.message ?: "parse error"}).",
                    )
                }
            }
            if (result.ok && result.changed.isNotEmpty()) refresh()
            onResult(result)
        }
    }

    /**
     * Per-uploaded-config twin of [loadCommunityConfigDetail]: load the read-only detail for THAT exact
     * file (via `/download`), including the live social layer keyed off [ref] (sha / votes / downloads /
     * description / comments). Returns null on a fetch/translate failure so the UI shows the same clean
     * "couldn't fetch" message. The [CommunityConfigDetail.device] is synthesized from the config's own
     * meta so the detail page's provenance reads identically to the device-picked path.
     */
    fun loadCommunityConfigDetail(
        ref: CommunityConfigRef,
        target: Shortcut?,
        onResult: (CommunityConfigDetail?) -> Unit,
    ) {
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                val fetched = CommunityConfigFetcher.fetchForFile(ref.workerGame, ref.filename, ref.ns)
                    ?: return@withContext null
                val config = ConfigTranslator.translate(fetched.json)
                val meta = ConfigMeta.parse(fetched.json.optJSONObject("meta"), fetched.fileName)
                val preview = target?.let {
                    CommunityConfigApply.preview(
                        shortcut = it,
                        config = config,
                        installed = InstalledComponents.read(getApplication()),
                        containerWineVersion = it.container?.getWineVersion(),
                        isAdreno = GPUInformation.isAdrenoGPU(getApplication()),
                    )
                }
                // Live social layer for the exact file: sha comes off the ref when present, otherwise
                // (and for votes/downloads) from this file's /list entry under the same worker key.
                var sha = ref.sha
                var votes = 0
                var downloads = 0
                CommunityConfigWorker.list(ref.workerGame, ref.ns).firstOrNull { it.filename == ref.filename }?.let { e ->
                    if (sha.isNullOrBlank()) sha = e.sha.ifBlank { null }
                    votes = e.votes
                    downloads = e.downloads
                }
                val description = sha?.let { CommunityConfigWorker.desc(it) } ?: ""
                val comments = CommunityConfigWorker.comments(ref.workerGame, ref.filename)
                val device = CanonicalDevice(model = meta.device ?: "", gpu = "", soc = meta.soc ?: "")
                CommunityConfigDetail(
                    ref.game, device, fetched.fileName, meta, config, preview,
                    sha = sha, workerGame = ref.workerGame, votes = votes, downloads = downloads,
                    description = description, comments = comments,
                )
            }
            onResult(detail)
        }
    }

    /**
     * Read-only twin of [applyCommunityConfig] for the detail page: fetch [game]+[device]'s config,
     * translate it, parse its provenance ([ConfigMeta]) and — when [target] is non-null — compute the
     * non-mutating pre-apply diff via [CommunityConfigApply.preview] (NOTHING is written or persisted).
     * All IO is off the main thread; returns null on a fetch/translate failure so the UI shows the same
     * clean "couldn't fetch" message the apply path does.
     */
    fun loadCommunityConfigDetail(
        game: CanonicalGame,
        device: CanonicalDevice,
        target: Shortcut?,
        onResult: (CommunityConfigDetail?) -> Unit,
    ) {
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) {
                val fetched = CommunityConfigFetcher.fetchForDevice(game, device) ?: return@withContext null
                val config = ConfigTranslator.translate(fetched.json)
                val meta = ConfigMeta.parse(fetched.json.optJSONObject("meta"), fetched.fileName)
                val preview = target?.let {
                    CommunityConfigApply.preview(
                        shortcut = it,
                        config = config,
                        installed = InstalledComponents.read(getApplication()),
                        containerWineVersion = it.container?.getWineVersion(),
                        isAdreno = GPUInformation.isAdrenoGPU(getApplication()),
                    )
                }
                // Resolve the live social layer: find this file's /list entry (across the game's
                // folders, then its name) to get sha + votes + downloads, then desc + comments. All
                // best-effort — any miss just leaves the social section empty; never fails the page.
                var sha: String? = null
                var workerGame: String? = null
                var votes = 0
                var downloads = 0
                val candidates = (game.folders + game.name).distinct()
                for (key in candidates) {
                    val entry = CommunityConfigWorker.list(key)
                        .firstOrNull { it.filename == fetched.fileName } ?: continue
                    sha = entry.sha.ifBlank { null }
                    workerGame = key
                    votes = entry.votes
                    downloads = entry.downloads
                    break
                }
                val description = sha?.let { CommunityConfigWorker.desc(it) } ?: ""
                val comments = workerGame?.let { CommunityConfigWorker.comments(it, fetched.fileName) } ?: emptyList()
                CommunityConfigDetail(
                    game, device, fetched.fileName, meta, config, preview,
                    sha = sha, workerGame = workerGame, votes = votes, downloads = downloads,
                    description = description, comments = comments,
                )
            }
            onResult(detail)
        }
    }

    /**
     * POST an upvote for [sha] (bucketed under [game]/[filename]) and hand back the new count, or null
     * on failure. Local per-sha dedup lives in the UI (a `banner_config_votes` prefs file), matching
     * BannerHub; the worker also enforces one vote per IP per 24h.
     */
    fun voteConfig(sha: String, game: String, filename: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            val votes = withContext(Dispatchers.IO) { CommunityConfigWorker.vote(sha, game, filename) }
            onResult(votes)
        }
    }

    /**
     * POST a comment then re-fetch the thread so the UI shows it immediately. [onResult] gets the
     * refreshed list on success, or null when the post failed (UI keeps the field populated to retry).
     */
    fun addConfigComment(
        game: String,
        filename: String,
        text: String,
        device: String,
        onResult: (List<WorkerComment>?) -> Unit,
    ) {
        viewModelScope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                if (CommunityConfigWorker.postComment(game, filename, text, device))
                    CommunityConfigWorker.comments(game, filename)
                else null
            }
            onResult(refreshed)
        }
    }

    /**
     * Fetch every remote Turnip/adrenotools driver source in parallel, flatten to one list, and rank
     * it against [wanted] (exact repo-variants + closest few by mesa version). All IO off the main
     * thread; a source that fails to fetch is skipped, not fatal. Fed by the inline driver installer
     * on the "Config applied" screen.
     */
    fun fetchDriverShortlist(
        wanted: String,
        onResult: (CommunityConfigApply.DriverShortlist) -> Unit,
    ) {
        viewModelScope.launch {
            val shortlist = withContext(Dispatchers.IO) {
                val repo = RemoteDriverRepository(getApplication())
                val entries = DriverSources.allSources(getApplication())
                    .map { src -> async { repo.fetchEntries(src).getOrDefault(emptyList()) } }
                    .awaitAll()
                    .flatten()
                CommunityConfigApply.rankDrivers(wanted, entries)
            }
            onResult(shortlist)
        }
    }

    fun setSortOrder(order: ShortcutSortOrder) {
        _sortOrder.value = order
        prefs.edit().putInt("sort_order", order.ordinal).apply()
    }

    fun setViewMode(mode: ShortcutViewMode) {
        val safe = if (mode == ShortcutViewMode.XMB) ShortcutViewMode.GRID else mode
        _viewMode.value = safe
        prefs.edit().putInt("view_mode", safe.ordinal).apply()
    }

    /** Cycles list → grid → compact grid → list, driven by the single header button. */
    fun cycleViewMode() {
        val modes = ShortcutViewMode.entries.filter { it != ShortcutViewMode.XMB }
        val next = modes[(modes.indexOf(_viewMode.value) + 1).mod(modes.size)]
        setViewMode(next)
    }

    // Only containers still present on disk (with a ".container" config). A deleted container can
    // linger in the manager's in-memory list; operating on it recreates its directory via
    // getDesktopDir().mkdirs(), which then breaks future container creation. Filtering here keeps
    // stale entries out of the picker AND out of import/clone. (issue #45)
    private fun liveContainers() = manager.getContainers().filter { it.configFile.isFile }

    /**
     * Scans [folderPath] for games, ready for the bulk-import confirm screen.
     *
     * Blocking and filesystem-heavy — call from a background dispatcher. Games already present in
     * the target container come back flagged rather than removed, so re-scanning the same library
     * after adding a few titles shows what was skipped instead of silently duplicating everything.
     */
    fun scanGamesFolder(containerIndex: Int, folderPath: String): List<GameFolderScanner.Candidate> {
        val containers = liveContainers()
        if (containerIndex < 0 || containerIndex >= containers.size) return emptyList()
        return GameFolderScanner.scan(File(folderPath), importedExePaths(containers[containerIndex]))
    }

    /** Canonical paths of every exe already imported into [container], for duplicate detection. */
    private fun importedExePaths(container: Container): Set<String> {
        val desktopDir = runCatching { container.getDesktopDir() }.getOrNull() ?: return emptySet()
        val files = desktopDir.listFiles { f -> f.isFile && f.name.endsWith(".desktop") } ?: return emptySet()
        return files.mapNotNullTo(HashSet()) { f ->
            runCatching {
                val exec = f.readLines().firstOrNull { it.startsWith("Exec=") } ?: return@runCatching null
                // Exec=wine <win path>, written with 4-backslash separators by escapeForExec.
                val winPath = exec.removePrefix("Exec=").removePrefix("wine ").trim()
                    .replace("\\\\\\\\", "\\").trim('"')
                WinePath.resolveAndroidPath(container, winPath)?.canonicalPath
            }.getOrNull()
        }
    }

    /**
     * Writes a shortcut for each confirmed [candidates] entry, reusing the same importer the
     * single-exe "+" flow uses so bulk-added games are indistinguishable from hand-added ones
     * (same naming, same Steam-name upgrade, same cover-art chain).
     *
     * Blocking — call from a background dispatcher. One failure does not abort the rest.
     */
    fun importScannedGames(
        containerIndex: Int,
        candidates: List<GameFolderScanner.Candidate>,
        context: Context,
    ): BulkImportSummary {
        val containers = liveContainers()
        if (containerIndex < 0 || containerIndex >= containers.size) {
            return BulkImportSummary(0, candidates.size, listOf("That container no longer exists."))
        }
        val container = containers[containerIndex]
        var added = 0
        val failures = mutableListOf<String>()
        for (c in candidates) {
            try {
                ExeShortcutImporter.addToShortcuts(
                    context, container, c.exe, c.name, c.appId,
                    onCoverArtReady = { refresh() },
                )
                added++
            } catch (e: Exception) {
                Log.e(TAG, "Bulk import failed for ${c.name}", e)
                failures += "${c.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        refresh()
        return BulkImportSummary(added, failures.size, failures)
    }

    fun importShortcut(containerIndex: Int, uri: Uri, context: Context): ImportResult {
        val containers = liveContainers()
        if (containerIndex < 0 || containerIndex >= containers.size) {
            return ImportResult.Error("That container no longer exists. Pull to refresh and pick another.")
        }
        val container = containers[containerIndex]

        // file:// (in-app picker) exposes no DocumentFile metadata, so read the name off the path.
        val sourceName = (if (uri.scheme == "file") uri.path?.substringAfterLast('/')
            else DocumentFile.fromSingleUri(context, uri)?.name)
            ?: return ImportResult.Error("Could not read picked file.")
        val ext = sourceName.substringAfterLast('.', "").lowercase()

        return when (ext) {
            "exe" -> importExe(container, uri, sourceName, context)
            "desktop", "lnk" -> importShortcutFile(container, uri, sourceName, ext, context)
            else -> ImportResult.Error("Unsupported file type: .$ext (pick a .exe, .desktop, or .lnk).")
        }
    }

    private fun importExe(container: Container, uri: Uri, sourceName: String, context: Context): ImportResult {
        val realPath = resolveLocalPath(context, uri)
            ?: return ImportResult.Error("EXE must be on local storage. Cloud / SAF locations aren't supported.")
        val exeFile = File(realPath)
        if (!exeFile.isFile) {
            return ImportResult.Error("Could not access EXE on disk: $realPath")
        }
        // Identify the game from its on-disk footprint (steam_appid.txt / Steam & GOG
        // manifests / PE version info) so the shortcut gets the real title — which is
        // then what the SGDB cover-art search runs on — instead of the raw exe filename.
        val identity = GameIdentifier.identify(exeFile)
        val fallbackName = sourceName.substringBeforeLast('.', sourceName)
        val displayName = identity.name?.takeIf { it.isNotBlank() } ?: fallbackName
        Log.d(TAG, "importExe: identified '$displayName' (appId=${identity.appId}, source=${identity.source})")
        return try {
            // Delegate to the shared importer so the "+" flow and the File Manager's
            // "Add to shortcuts" action write shortcuts identically. The proper name is
            // written first; cover art (SGDB by appId → by name) resolves on a background
            // thread and calls back via onCoverArtReady once the icon lands.
            val shortcutFile = ExeShortcutImporter.addToShortcuts(
                context, container, exeFile, displayName, identity.appId,
                onCoverArtReady = { refresh() },
                onNameResolved = { oldBase, newBase ->
                    // Background thread upgraded the name to Steam's authoritative title; publish
                    // it so an open confirm dialog follows the on-disk rename. Fires on the main thread.
                    _importedNameUpdate.value = ImportedNameUpdate(oldBase, newBase)
                    refresh()
                },
            )
            refresh()
            ImportResult.Success(shortcutFile.nameWithoutExtension, identity.appId)
        } catch (e: WinePath.NoFreeDriveLetterException) {
            Log.e(TAG, "No free drive letter for ${e.mountPath}", e)
            ImportResult.Error(
                "This container has no free drive letters left. Remove a drive you no longer " +
                    "need in the container's Drives tab, or move the game somewhere an " +
                    "existing drive already covers."
            )
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write EXE shortcut", e)
            ImportResult.Error("Failed to write shortcut: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun importShortcutFile(
        container: Container,
        uri: Uri,
        sourceName: String,
        ext: String,
        context: Context,
    ): ImportResult {
        val destDir = container.getDesktopDir()
        if (!destDir.exists()) destDir.mkdirs()
        val dest = File(destDir, sourceName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return ImportResult.Error("Could not open picked file.")
            if (ext == "desktop") {
                val lines = dest.readLines().map { line ->
                    if (line.startsWith("container_id:")) "container_id:${container.id}" else line
                }
                // A stale "not Ubisoft" stamp is dropped so this container works it out again.
                dest.writeText(UbisoftSupport.dropStaleStamp(lines.joinToString("\n") + "\n"))
            }
            refresh()
            ImportResult.Success(dest.nameWithoutExtension)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import shortcut file", e)
            ImportResult.Error("Failed to import: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun resolveLocalPath(ctx: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme != "content") return null
        return try {
            if (!DocumentsContract.isDocumentUri(ctx, uri)) return null
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":", limit = 2)
            val type = split[0]
            val rel = if (split.size > 1) split[1] else ""
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    if ("primary".equals(type, ignoreCase = true)) {
                        "${Environment.getExternalStorageDirectory()}/$rel"
                    } else {
                        "/storage/$type/$rel"
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "URI path resolution failed for $uri", e)
            null
        }
    }

    fun refresh() {
        // Re-scan the home dir first: this manager is constructed once and lives for the whole
        // session, so a container created in the container editor (its own ContainerManager
        // instance) is otherwise absent from our in-memory list until the ViewModel is rebuilt.
        // That's the "new container doesn't show in the add-game picker until you launch/restart"
        // bug — refresh() runs on ON_RESUME, so re-scanning here surfaces it on return to the tab.
        manager.reloadContainers()
        val raw = manager.loadShortcuts()
        // filter out corrupted entries (matches original Fragment logic)
        val kept = raw.filter { it != null && it.file != null && it.file.name.isNotEmpty() }
        _shortcuts.value = kept
    }

    /** Replaces a shortcut in the live list, optionally applying a specific icon. */
    fun reloadShortcut(filePath: String, icon: Bitmap? = null) {
        _shortcuts.value = _shortcuts.value.map { s ->
            if (s.file.path == filePath) {
                val loaded = Shortcut(s.container, s.file)
                loaded.icon = icon ?: loaded.icon ?: s.icon
                loaded
            } else s
        }
    }

    fun remove(shortcut: Shortcut, context: Context): Boolean {
        val deleted = shortcut.file.delete()
        val lnkPath = shortcut.file.path.substringBeforeLast('.') + ".lnk"
        val lnk = File(lnkPath)
        if (lnk.exists()) lnk.delete()
        if (deleted) {
            disableOnScreen(context, shortcut)
            refresh()
        }
        return deleted
    }

    fun cloneToContainer(shortcut: Shortcut, containerIndex: Int): Boolean {
        val containers = liveContainers()
        if (containerIndex < 0 || containerIndex >= containers.size) return false
        val result = shortcut.cloneToContainer(containers[containerIndex])
        if (result) refresh()
        return result
    }

    fun containers() = liveContainers()

    fun renameImportedShortcut(containerIndex: Int, oldName: String, newName: String) {
        val safe = newName.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        if (oldName == safe || safe.isBlank()) return
        val containers = liveContainers()
        if (containerIndex < 0 || containerIndex >= containers.size) return
        // Robust rename (moves .desktop/.lnk + icon PNGs + cover-art PNG, rewrites Icon= and
        // customCoverArtPath) so a renamed import keeps its scraped art. Shared with the importer's
        // auto-rename via ExeShortcutImporter.renameShortcutFiles so the two never drift.
        if (ExeShortcutImporter.renameShortcutFiles(containers[containerIndex], oldName, safe)) {
            refresh()
        }
    }

    companion object {
        private const val TAG = "ShortcutsImport"

        // A shortcut name genuinely ties several canonical games when their match scores sit within
        // this epsilon of the top score → the "Which game is this?" picker (issue #167). Capped so a
        // very generic name can't spill a huge list into the sheet.
        private const val TIE_EPSILON = 1e-6
        private const val MAX_TIE_ALTERNATIVES = 6

        fun disableOnScreen(context: Context, shortcut: Shortcut) {
            try {
                val sm = ContextCompat.getSystemService(context, ShortcutManager::class.java)
                sm?.disableShortcuts(
                    Collections.singletonList(shortcut.getExtra("uuid")),
                    context.getString(com.winhub.hernandez.R.string.shortcut_not_available),
                )
            } catch (_: Exception) {}
        }
    }
}
