package com.winhub.hernandez.ui.screens.contents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.ui.screens.adrenodownload.DriverFeed
import com.winhub.hernandez.ui.screens.adrenodownload.DriverSourceStore
import com.winhub.hernandez.ui.screens.adrenodownload.DriverSources
import com.winhub.hernandez.ui.screens.adrenodownload.RemoteDriverRepository
import com.winhub.hernandez.ui.screens.adrenodownload.RemoteDriverSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row in the Contents source list. Component sources (DXVK/VKD3D/…) are backed by
 * [RemoteSourceRepository]; GPU-driver sources are backed by the SAME adrenotools registry the
 * AdrenoTools screen uses ([DriverSources]/[DriverSourceStore]), so the two screens list — and
 * share edits to — the same driver repos. Wrapping both in a sealed type disambiguates same-named
 * sources (e.g. the component "StevenMXZ" vs the driver "StevenMXZ").
 */
sealed interface HubSource {
    val name: String
    val displayFormat: String
    val typePills: List<String>
    val driverOnly: Boolean
    /** true → the remove action is "Hide default"; false → "Remove". */
    val removeIsHide: Boolean
    /** The built-in Official catalog (contents.json) — gets the green Official badge. */
    val isOfficial: Boolean

    data class Component(val source: RemoteSourceRepository.RemoteSource) : HubSource {
        override val name get() = source.name
        override val displayFormat get() = if (source.isOfficial) "contents.json" else source.format.name.replace('_', ' ')
        override val typePills get() = source.supportedTypes
        override val driverOnly get() = false
        override val removeIsHide get() = !source.isCustom
        override val isOfficial get() = source.isOfficial
    }

    data class Driver(val source: RemoteDriverSource) : HubSource {
        override val name get() = source.name
        override val displayFormat get() = "GPU driver source"
        override val typePills get() = listOf(ContentsTypes.GPU_DRIVERS)
        override val driverOnly get() = true
        override val removeIsHide get() = source.builtIn
        override val isOfficial get() = false
    }
}

/**
 * Drives the Contents hub: the source list, per-source catalogs, cross-source search, the
 * keep-raw toggle, the My Files library, and installed/saved status for badges.
 */
class ContentsHubViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RemoteSourceRepository(app)
    private val driverRepo = RemoteDriverRepository(app)
    val library = ComponentLibrary(app)

    private val appCtx: Application get() = getApplication()

    data class CatalogItem(
        val type: String,
        val displayName: String,
        val versionName: String,
        val downloadUrl: String,
        val sourceName: String,
        val publishedAt: String?,
        val sizeBytes: Long?,
        val isDriver: Boolean = ContentsTypes.isDriver(type),
    ) {
        val fileName: String
            get() = downloadUrl.substringAfterLast('/').substringBefore('?')
                .ifBlank { "${versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.wcp" }
    }

    private val _sources = MutableStateFlow<List<HubSource>>(emptyList())
    val sources: StateFlow<List<HubSource>> = _sources.asStateFlow()

    private val _keepRaw = MutableStateFlow(library.keepRaw())
    val keepRaw: StateFlow<Boolean> = _keepRaw.asStateFlow()

    private val _showOfficial = MutableStateFlow(repo.showOfficial())
    val showOfficial: StateFlow<Boolean> = _showOfficial.asStateFlow()

    private val _selected = MutableStateFlow<HubSource?>(null)
    val selected: StateFlow<HubSource?> = _selected.asStateFlow()

    private val _detailTypes = MutableStateFlow<List<String>>(emptyList())
    val detailTypes: StateFlow<List<String>> = _detailTypes.asStateFlow()

    private val _detailItems = MutableStateFlow<List<CatalogItem>>(emptyList())
    val detailItems: StateFlow<List<CatalogItem>> = _detailItems.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CatalogItem>>(emptyList())
    val searchResults: StateFlow<List<CatalogItem>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _installedKeys = MutableStateFlow<Set<String>>(emptySet())
    val installedKeys: StateFlow<Set<String>> = _installedKeys.asStateFlow()

    private val _installedDriverNorms = MutableStateFlow<Set<String>>(emptySet())

    private val _savedKeys = MutableStateFlow(library.savedKeys())
    val savedKeys: StateFlow<Set<String>> = _savedKeys.asStateFlow()

    private val _savedOnlyKeys = MutableStateFlow(library.savedOnlyKeys())
    val savedOnlyKeys: StateFlow<Set<String>> = _savedOnlyKeys.asStateFlow()

    private val _savedFolders = MutableStateFlow<Map<String, List<ComponentLibrary.SavedFile>>>(emptyMap())
    val savedFolders: StateFlow<Map<String, List<ComponentLibrary.SavedFile>>> = _savedFolders.asStateFlow()

    private val _baseDisplay = MutableStateFlow(library.baseDisplay())
    val baseDisplay: StateFlow<String> = _baseDisplay.asStateFlow()

    // Lazily-fetched driver entries for cross-source search (drivers aren't in the component cache).
    private var driverSearchCache: List<CatalogItem>? = null

    init {
        reloadSources()
        refreshStatus()
        refreshFolders()
    }

    // ── Sources ─────────────────────────────────────────────────────────────────
    fun reloadSources() {
        // Component sources first, then the shared GPU-driver registry (same list AdrenoTools shows).
        _sources.value = repo.getAllSources().map { HubSource.Component(it) } +
            DriverSources.allSources(appCtx).map { HubSource.Driver(it) }
        driverSearchCache = null
    }

    fun refreshBase() {
        _baseDisplay.value = library.baseDisplay()
    }

    // ── Keep-raw ────────────────────────────────────────────────────────────────
    fun setKeepRaw(value: Boolean) {
        library.setKeepRaw(value)
        _keepRaw.value = value
    }

    // ── Official catalog visibility (hub only; the container/shortcut sheet is unaffected) ──
    fun setShowOfficial(value: Boolean) {
        repo.setShowOfficial(value)
        _showOfficial.value = value
        val sel = _selected.value
        if (!value && sel is HubSource.Component && sel.source.isOfficial) selectSource(null)
        reloadSources()
    }

    // ── Save archive only ───────────────────────────────────────────────────────
    /** Downloads [item]'s archive straight into My Files (no install). [force] re-downloads a saved one. */
    fun saveArchive(item: CatalogItem, force: Boolean = false) {
        if (!force && isSaved(item)) return
        ContentsInstaller.saveOnly(appCtx, item.type, item.sourceName, item.versionName, item.downloadUrl) {
            refreshStatus(); refreshFolders()
        }
    }

    // ── Selection + per-source catalog ──────────────────────────────────────────
    fun selectSource(source: HubSource?) {
        _selected.value = source
        if (source == null) {
            _detailTypes.value = emptyList()
            _detailItems.value = emptyList()
            return
        }
        loadDetail(source)
    }

    fun refreshSelected() {
        RemoteSourceRepository.clearCache()
        driverSearchCache = null
        _selected.value?.let { loadDetail(it) }
    }

    private fun loadDetail(source: HubSource) {
        _detailLoading.value = true
        _detailItems.value = emptyList()
        _detailTypes.value = emptyList()
        viewModelScope.launch {
            val (types, items) = withContext(Dispatchers.IO) {
                when (source) {
                    is HubSource.Component -> {
                        val ts = resolveTypes(source.source)
                        val its = ts.flatMap { type ->
                            runCatching { repo.fetchFromSource(source.source, type) }.getOrDefault(emptyList())
                                .map { it.toCatalog(type) }
                        }
                        ts to its
                    }
                    is HubSource.Driver -> {
                        val its = runCatching { driverRepo.fetchEntries(source.source).getOrDefault(emptyList()) }
                            .getOrDefault(emptyList())
                            .map { entry ->
                                CatalogItem(
                                    type = ContentsTypes.GPU_DRIVERS,
                                    displayName = entry.displayName,
                                    versionName = entry.displayName,
                                    downloadUrl = entry.downloadUrl,
                                    sourceName = source.source.name,
                                    publishedAt = null,
                                    sizeBytes = null,
                                )
                            }
                        listOf(ContentsTypes.GPU_DRIVERS) to its
                    }
                }
            }
            _detailTypes.value = types
            // Dedupe by download URL: community catalogs (and loose contains-matching) can list a
            // single asset twice, which would crash the LazyColumn on a duplicate key.
            _detailItems.value = items.distinctBy { it.downloadUrl }
            _detailLoading.value = false
            refreshStatus()
        }
    }

    /** Display-canonical, de-duplicated type list for a component source (GPU keywords collapsed). */
    private suspend fun resolveTypes(source: RemoteSourceRepository.RemoteSource): List<String> {
        val raw = if (source.supportedTypes.isNotEmpty()) source.supportedTypes
        else runCatching { repo.discoverTypes(source) }.getOrDefault(ContentsTypes.ALL)
        val seen = LinkedHashSet<String>()
        raw.forEach { seen.add(canonicalType(it)) }
        return seen.toList()
    }

    private fun canonicalType(key: String): String {
        if (ContentsTypes.isDriver(key) || RemoteSourceRepository.GPU_DRIVER_KEYWORDS.any { it.equals(key, true) }) {
            return ContentsTypes.GPU_DRIVERS
        }
        if (key.equals("fex", true) || key.equals("fexcore", true)) return "FEXCore"
        return ContentProfile.ContentType.getTypeByName(key)?.toString()
            ?: key.replaceFirstChar { it.uppercaseChar() }
    }

    private fun RemoteSourceRepository.RemoteItem.toCatalog(type: String) = CatalogItem(
        type = type,
        displayName = displayName,
        versionName = versionName,
        downloadUrl = downloadUrl,
        sourceName = sourceName,
        publishedAt = publishedAt,
        sizeBytes = sizeBytes,
    )

    // ── Component-tab category browse (category → that family's items, all sources) ──
    private val _categoryType = MutableStateFlow<String?>(null)
    val categoryType: StateFlow<String?> = _categoryType.asStateFlow()

    private val _categoryItems = MutableStateFlow<List<CatalogItem>>(emptyList())
    val categoryItems: StateFlow<List<CatalogItem>> = _categoryItems.asStateFlow()

    private val _categoryLoading = MutableStateFlow(false)
    val categoryLoading: StateFlow<Boolean> = _categoryLoading.asStateFlow()

    fun selectCategory(type: String?) {
        _categoryType.value = type
        if (type == null) {
            _categoryItems.value = emptyList()
            return
        }
        loadCategory(type)
    }

    private fun loadCategory(type: String) {
        _categoryLoading.value = true
        _categoryItems.value = emptyList()
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                val comp = if (ContentsTypes.isDriver(type)) {
                    // GPU drivers live in the shared driver registry, not the component cache.
                    driverEntriesForSearch()
                } else {
                    repo.getAllSources().flatMap { src ->
                        runCatching { repo.fetchFromSource(src, type) }.getOrDefault(emptyList())
                            .map { it.toCatalog(type) }
                    }
                }
                comp.distinctBy { it.downloadUrl }
            }
            _categoryItems.value = items
            _categoryLoading.value = false
            refreshStatus()
        }
    }

    // ── Cross-source search (components + shared driver registry) ────────────────
    fun setQuery(q: String) {
        _query.value = q
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _searching.value = true
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                if (!RemoteSourceRepository.hasCache()) {
                    val compTypes = ContentsTypes.ALL.filter { !ContentsTypes.isDriver(it) }
                    runCatching { repo.refreshAllCache(repo.getAllSources(), compTypes) }
                }
                val comp = RemoteSourceRepository.searchCache(q)
                    .map { it.item.toCatalog(canonicalType(it.componentType)) }
                val tokens = q.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val drv = driverEntriesForSearch().filter { item ->
                    val hay = "${item.displayName} ${ContentsTypes.GPU_DRIVERS} ${item.sourceName}".lowercase()
                    tokens.all { hay.contains(it) }
                }
                (comp + drv).distinctBy { it.downloadUrl }
            }
            _searchResults.value = results
            _searching.value = false
            refreshStatus()
        }
    }

    private suspend fun driverEntriesForSearch(): List<CatalogItem> {
        driverSearchCache?.let { return it }
        val list = DriverSources.allSources(appCtx).flatMap { s ->
            runCatching { driverRepo.fetchEntries(s).getOrDefault(emptyList()) }.getOrDefault(emptyList())
                .map { entry ->
                    CatalogItem(ContentsTypes.GPU_DRIVERS, entry.displayName, entry.displayName,
                        entry.downloadUrl, s.name, null, null)
                }
        }.distinctBy { it.downloadUrl }
        driverSearchCache = list
        return list
    }

    // ── Status (installed / saved) ──────────────────────────────────────────────
    fun refreshStatus() {
        _savedKeys.value = library.savedKeys()
        _savedOnlyKeys.value = library.savedOnlyKeys()
        viewModelScope.launch {
            val (keys, driverNorms) = withContext(Dispatchers.IO) { computeInstalled() }
            _installedKeys.value = keys
            _installedDriverNorms.value = driverNorms
        }
    }

    private fun computeInstalled(): Pair<Set<String>, Set<String>> {
        val ctx = getApplication<Application>()
        val keys = mutableSetOf<String>()
        val driverNorms = mutableSetOf<String>()
        runCatching {
            val cm = ContentsManager(ctx)
            cm.syncContents()
            ContentProfile.ContentType.values().forEach { ct ->
                cm.getProfiles(ct)?.forEach { p ->
                    if (p.remoteUrl == null && p.verName != null) {
                        keys.add(ContentsTypes.normalize(ct.toString()) + "::" + ContentsTypes.normalize(p.verName))
                    }
                }
            }
        }
        runCatching {
            AdrenotoolsManager(ctx).enumarateInstalledDrivers().forEach { name ->
                val n = ContentsTypes.normalize(name)
                driverNorms.add(n)
                keys.add(ContentsTypes.normalize(ContentsTypes.GPU_DRIVERS) + "::" + n)
            }
        }
        return keys to driverNorms
    }

    /** True if the item's version is already installed (components: exact key; drivers: fuzzy contains). */
    fun isInstalled(item: CatalogItem): Boolean {
        val key = ContentsTypes.normalize(item.type) + "::" + ContentsTypes.normalize(item.versionName)
        if (key in _installedKeys.value) return true
        if (item.isDriver) {
            val v = ContentsTypes.normalize(item.versionName)
            val d = ContentsTypes.normalize(item.displayName)
            return _installedDriverNorms.value.any { it == v || it == d || (v.isNotEmpty() && it.contains(v)) }
        }
        return false
    }

    fun isSaved(item: CatalogItem): Boolean =
        library.keyFor(item.type, item.fileName) in _savedKeys.value

    fun isSavedOnly(item: CatalogItem): Boolean =
        library.keyFor(item.type, item.fileName) in _savedOnlyKeys.value

    // ── My Files ────────────────────────────────────────────────────────────────
    fun refreshFolders() {
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) { library.listSaved() }
            _savedFolders.value = map
            _savedKeys.value = library.savedKeys()
            _savedOnlyKeys.value = library.savedOnlyKeys()
            _baseDisplay.value = library.baseDisplay()
        }
    }

    /** A My Files archive was installed (Reinstall): it is no longer "saved only, not installed". */
    fun markInstalledFromSaved(file: ComponentLibrary.SavedFile) {
        library.markInstalledFromSaved(file.type, file.name)
        refreshStatus()
        refreshFolders()
    }

    fun deleteSaved(file: ComponentLibrary.SavedFile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.delete(file) }
            refreshFolders()
        }
    }

    // ── Source management ───────────────────────────────────────────────────────
    /** Adds a component source (Contents store). */
    fun addComponentSource(source: RemoteSourceRepository.RemoteSource) {
        repo.addCustomSource(source)
        reloadSources()
    }

    /** Adds a GPU-driver source through the SHARED adrenotools store (also visible in AdrenoTools). */
    fun addDriverSource(name: String, feed: DriverFeed) {
        DriverSourceStore(appCtx).addCustom(name, feed)
        reloadSources()
    }

    fun removeSource(source: HubSource) {
        when (source) {
            is HubSource.Component -> repo.removeSource(source.source)
            is HubSource.Driver -> {
                val store = DriverSourceStore(appCtx)
                if (source.source.builtIn) store.setBuiltInEnabled(source.source.name, false)
                else store.removeCustom(source.source.name)
            }
        }
        if (_selected.value == source) selectSource(null)
        reloadSources()
    }

    fun restoreDefaultSources() {
        repo.restoreDefaultSources()
        _showOfficial.value = repo.showOfficial()
        val store = DriverSourceStore(appCtx)
        DriverSources.builtIns().forEach { store.setBuiltInEnabled(it.name, true) }
        reloadSources()
    }

    fun exportRepoListJson(): String = repo.exportRepoListJson()

    fun importRepoListJson(json: String, merge: Boolean): Boolean = runCatching {
        repo.applyRepoListImport(repo.parseRepoListJson(json), merge)
        reloadSources()
        true
    }.getOrDefault(false)

    /** Human browse URL for the source's "Open source page" menu action. */
    fun browseUrl(source: HubSource): String = when (source) {
        is HubSource.Component -> repo.getBrowseUrl(source.source)
        is HubSource.Driver -> when (val f = source.source.feeds.firstOrNull()) {
            is DriverFeed.GithubReleases -> "https://github.com/${f.owner}/${f.repo}"
            is DriverFeed.Json -> f.url.let { url ->
                if (url.contains("raw.githubusercontent.com/")) {
                    val parts = url.substringAfter("raw.githubusercontent.com/").split("/")
                    if (parts.size >= 2) "https://github.com/${parts[0]}/${parts[1]}" else url
                } else url
            }
            null -> ""
        }
    }
}
