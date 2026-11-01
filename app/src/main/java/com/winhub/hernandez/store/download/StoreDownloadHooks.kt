package com.winhub.hernandez.store.download

/**
 * Thin, store-agnostic write shim over [DownloadRegistry] for the Java-fed store
 * Activities (Amazon now; GOG / Epic later).
 *
 * Steam wires the registry directly from Kotlin ([com.winhub.hernandez.store.SteamDepotDownloader]),
 * so it doesn't need this. The other stores drive their downloads from a Java engine
 * (`AmazonDownloadManager` etc.) with only an Activity-side callback seam to hook — and
 * three of them would otherwise copy-paste the same `"$store:$id"` key construction and
 * the same `upsert`/`update` boilerplate. This object centralises exactly that:
 *   - one place builds the store-qualified key (must match [DownloadEntry.key]);
 *   - one place maps a store's simple "pct + aggregate bytes" callbacks into the
 *     normalized [DownloadEntry] the shared registry + Compose screen expect.
 *
 * All calls are no-ops-safe: [DownloadRegistry.update] silently ignores an absent key,
 * so an out-of-order progress/complete tick can never NPE. ARCHITECTURE: this stays in
 * the `download` package and imports zero store engines — the store Activities import it,
 * never the reverse.
 *
 * As well as feeding the in-app Download Manager, every call here also drives the shared
 * [DownloadForegroundService] so all non-Steam stores get a shade notification + a process
 * kept alive across backgrounding, for free: [registerDownload]/[tick] push the notification
 * line, and the terminal transitions ([markInstalled]/[markCancelled]/[markFailed]) drop it.
 */
object StoreDownloadHooks {

    /** Store-qualified registry key — MUST match [DownloadEntry.key] ("AMAZON:$id"). */
    private fun key(store: Store, id: String): String = "$store:$id"

    /**
     * Build the shade line from the entry's current state and push it to the foreground
     * service. Reads the entry back from the registry (populated by the caller immediately
     * before) so name + pct + bytes are always in sync with what the Manager card shows.
     * No-op until [DownloadRegistry.init] has captured an application Context.
     */
    private fun pushNotification(store: Store, id: String) {
        val k = key(store, id)
        val e = DownloadRegistry.get(k) ?: return
        val line = buildString {
            append(e.name.ifEmpty { "${e.store} ${e.id}" })
            append(" — ").append(e.pct).append('%')
            if (e.installTotal > 0L) {
                append("  (")
                append(formatDownloadSize(e.installDone)).append(" / ")
                append(formatDownloadSize(e.installTotal)).append(')')
            }
        }
        DownloadForegroundService.setProgress(k, line)
    }

    /**
     * Publish a freshly-started download as a live DOWNLOADING entry. Wires the store's
     * cancel (and optional pause) handles onto the entry so the Download Manager screen can
     * drive them. [installTotal] is the manifest install size if known up front (0 = fill it
     * in from the first [tick]). Stores with no separate compressed stage leave the download
     * byte pair at 0 (one honest bar). Uses `upsert` so a re-download replaces any stale row.
     */
    fun registerDownload(
        store: Store,
        id: String,
        name: String,
        cover: String? = null,
        supportsPause: Boolean = false,
        installTotal: Long = 0L,
        pause: (() -> Unit)? = null,
        cancel: () -> Unit,
    ) {
        DownloadRegistry.upsert(
            DownloadEntry(
                store = store,
                id = id,
                name = name,
                cover = cover,
                state = DownloadState.DOWNLOADING,
                installTotal = installTotal,
                supportsPause = supportsPause,
                pause = pause,
                cancel = cancel,
            )
        )
        // Bring up the shared FGS (keeps the process alive past Activity death) and post the
        // first shade line. start() is idempotent; both no-op cleanly before init() ran.
        DownloadRegistry.appContext()?.let { DownloadForegroundService.start(it) }
        pushNotification(store, id)
    }

    /**
     * Progress tick. [pct] is the overall percentage; the byte pairs mirror the two-bar
     * model ([installDone]/[installTotal] = uncompressed-on-disk, [downloadDone]/[downloadTotal]
     * = compressed-over-network). Stores that expose only aggregate download bytes map them
     * into the install pair and leave the download pair at 0. No-op if the entry is gone.
     */
    fun tick(
        store: Store,
        id: String,
        pct: Int,
        installDone: Long = 0L,
        installTotal: Long = 0L,
        downloadDone: Long = 0L,
        downloadTotal: Long = 0L,
        /** Network speed in bytes/s and seconds left, when the store's downloader reports them (0 / -1 = unknown). */
        speedBps: Long = 0L,
        etaSeconds: Long = -1L,
    ) {
        DownloadRegistry.update(key(store, id)) {
            it.copy(
                state = DownloadState.DOWNLOADING,
                pct = pct,
                installDone = installDone,
                installTotal = installTotal,
                downloadDone = downloadDone,
                downloadTotal = downloadTotal,
                speedBps = speedBps,
                etaSeconds = etaSeconds,
            )
        }
        pushNotification(store, id)
    }

    /**
     * Terminal success → INSTALLED (the only state the registry persists to the durable
     * library, so the game survives process death in the Library section). [bytes] fills the
     * bar to 100%; 0 keeps whatever totals the last [tick] set.
     */
    fun markInstalled(store: Store, id: String, installPath: String, bytes: Long = 0L) {
        DownloadRegistry.update(key(store, id)) {
            it.copy(
                state = DownloadState.INSTALLED,
                pct = 100,
                installPath = installPath,
                installDone = if (bytes > 0L) bytes else it.installTotal,
                installTotal = if (bytes > 0L) bytes else it.installTotal,
                updateAvailable = false,   // a just-installed / just-updated game is up to date
            )
        }
        DownloadForegroundService.finish(key(store, id))   // drop shade line; FGS self-stops when empty
    }

    /** Terminal failure. Kept in-memory for the session so the UI can offer retry/dismiss. */
    fun markFailed(store: Store, id: String, error: String) {
        DownloadRegistry.update(key(store, id)) {
            it.copy(state = DownloadState.FAILED, error = error)
        }
        DownloadForegroundService.finish(key(store, id))
    }

    /**
     * User-cancelled a download. Mark CANCELLED first (so a live collector sees the
     * transition), then drop the row — the files + install dir are gone, mirroring how Steam's
     * cancel path clears its registry entry.
     */
    fun markCancelled(store: Store, id: String) {
        val k = key(store, id)
        DownloadRegistry.update(k) { it.copy(state = DownloadState.CANCELLED) }
        DownloadRegistry.remove(k)
        DownloadForegroundService.finish(k)
    }

    /**
     * Uninstall: drop the INSTALLED library row (and its durable-pref entry) so the Library
     * section clears immediately — same effect as Steam's uninstall clearing its library entry.
     */
    fun markUninstalled(store: Store, id: String) {
        DownloadRegistry.removeLibraryEntry(key(store, id))
    }
}
