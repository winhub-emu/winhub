package com.winhub.hernandez.store.download

/**
 * Store-agnostic download model shared by the cross-store Download Manager.
 *
 * Phase 1 (this file): the normalized data types every storefront (Steam / Epic /
 * GOG / Amazon) is projected into so a single registry + a single Compose screen
 * can render them uniformly. No store wiring here — see [DownloadRegistry] for the
 * observable registry and the class-doc there for where Phase 2/3 attach.
 */

/** The storefront a download / library entry belongs to. */
enum class Store { STEAM, EPIC, GOG, AMAZON, XBOX }

/**
 * Single, store-agnostic human byte formatter for the whole download stack — the exact
 * tiering the Download Manager card uses (GB / MB / KB). Shared here so the manager card
 * text, the detail-page progress label, and the shade notification all read identically
 * ("810.2 MB", "3.9 GB") instead of each store re-inventing a slightly different format.
 */
fun formatDownloadSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    else                    -> "%.0f KB".format(bytes / 1024.0)
}

/** Download speed, e.g. "12.4 MB/s". <=0 → "". Shared by the manager, detail page and notification. */
fun formatDownloadSpeed(bytesPerSec: Long): String =
    if (bytesPerSec <= 0L) "" else "${formatDownloadSize(bytesPerSec)}/s"

/** Human ETA, e.g. "<1 min left", "~8 min left", "~1 h 20 m left". Negative → "" (unknown). */
fun formatEta(seconds: Long): String = when {
    seconds < 0L    -> ""
    seconds < 60L   -> "<1 min left"
    seconds < 3600L -> "~${seconds / 60} min left"
    else            -> {
        val h = seconds / 3600L; val m = (seconds % 3600L) / 60L
        if (m == 0L) "~$h h left" else "~$h h $m m left"
    }
}

/**
 * Lifecycle of a single download row.
 *
 * QUEUED/DOWNLOADING/PAUSED are the *active* states (in-memory only). INSTALLED is
 * the terminal success state and is the only one persisted to the durable library.
 * FAILED/CANCELLED are terminal non-success states kept in-memory for the current
 * session so the UI can surface a retry / dismiss affordance.
 */
enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, INSTALLED, FAILED, CANCELLED }

/**
 * One normalized download/library row.
 *
 * The two byte pairs mirror the Steam detail page's two-bar model exactly, so the
 * Steam producer ([com.winhub.hernandez.store.SteamDepotDownloader], Phase 2) maps in
 * 1:1 from its `DownloadProgress:appId:iDone:iTotal:dDone:dTotal` event:
 *   - [installDone]/[installTotal]  — uncompressed bytes written to disk
 *   - [downloadDone]/[downloadTotal] — compressed bytes fetched over the network
 * [pct] is the overall percentage (Steam uses the install fraction, clamped 0..99
 * until INSTALLED); other stores that only expose a single percentage can set [pct]
 * and leave the byte pairs at 0.
 *
 * ## Pause / cancel action handles
 * [pause]/[cancel] are held as transient lambdas ON the entry rather than in a side
 * map. Rationale:
 *   - `copy()` (used by every [DownloadRegistry.update]) preserves them for free, so
 *     a producer attaches them once at queue time and they survive every progress
 *     mutation without extra bookkeeping.
 *   - the Compose consumer (Phase 3) reads `entry.cancel` / `entry.pause` directly,
 *     no lookup indirection.
 *   - they are plain object references: cheap to copy, and — critically — NEVER
 *     persisted. Only INSTALLED rows hit prefs, and the library writer serializes
 *     only [name]/[store]/[installPath]/[cover], never these handles.
 * The Steam engine hands back a `DownloadControl(cancel, pause)`; the producer simply
 * wires `cancel = control.cancel::run`, `pause = control.pause::run`.
 *
 * @property id store-native id as a string (Steam appId, Epic appName, GOG gameId,
 *   Amazon productId). Stringly-typed so all stores share one model.
 * @property cover whatever a store needs to render its art — a Steam appId-derived
 *   url, a GOG/Amazon/Epic image url, or null. Nullable string key/url by design.
 * @property installPath filesystem path once INSTALLED (persisted to the library).
 * @property error human-readable failure reason when [state] is FAILED.
 * @property supportsPause whether this store's engine can pause+resume (Steam: true).
 */
data class DownloadEntry(
    val store: Store,
    val id: String,
    val name: String,
    val cover: String? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val pct: Int = 0,
    // Two-bar byte model (matches the Steam detail page). uncompressed-on-disk:
    val installDone: Long = 0L,
    val installTotal: Long = 0L,
    // compressed-fetched-over-network:
    val downloadDone: Long = 0L,
    val downloadTotal: Long = 0L,
    // Smoothed network speed (bytes/s, 0 = unknown) and ETA to completion (seconds, -1 = unknown).
    val speedBps: Long = 0L,
    val etaSeconds: Long = -1L,
    val supportsPause: Boolean = false,
    val installPath: String? = null,
    val error: String? = null,
    /**
     * True when an INSTALLED game has a newer version available from its store. Store-agnostic
     * so the Manager card can render one amber "Update available" marker for any store. Derived
     * per store at seed/produce time (Amazon: cached versionId carries an "_UPDATE_AVAILABLE"
     * suffix; Steam leaves it false for now — no Steam update detection exists yet). Transient:
     * NOT persisted to the durable library (the library writer serializes only name/store/
     * installPath/cover) — it is re-derived on each seed, and a fresh install/update clears it.
     */
    val updateAvailable: Boolean = false,
    /**
     * 1-based position in the managed download queue while [state] is [DownloadState.QUEUED]
     * (1 = next to start); 0 for everything else (active / installed / terminal). Set and kept
     * current by [com.winhub.hernandez.store.DownloadQueue] as items enqueue, dequeue, cancel or
     * reorder. Transient — never persisted (only INSTALLED rows hit prefs). The Download Manager's
     * Queued section renders "#n" from it and orders its list by it (the registry's default sort is
     * by name, which is meaningless for a FIFO).
     */
    val queuePosition: Int = 0,
    // ── Transient action handles — held live by the registry, NEVER persisted ──
    val pause: (() -> Unit)? = null,
    val cancel: (() -> Unit)? = null,
) {
    /** Stable, store-qualified key: e.g. "STEAM:220". Unique across all stores. */
    val key: String get() = "$store:$id"

    /** In one of the three active states (counts toward the ⬇ badge). */
    val isActive: Boolean
        get() = state == DownloadState.QUEUED ||
                state == DownloadState.DOWNLOADING ||
                state == DownloadState.PAUSED
}

/**
 * Durable library row (a serialized INSTALLED [DownloadEntry]). Persisted to the
 * `bh_library`-style pref so installed games survive process death. Kept format-
 * compatible with BannerHub's LibraryEntry (name / store / installPath), with an
 * optional 4th [cover] field appended for stores whose art isn't derivable from id.
 */
data class LibraryEntry(
    val key: String,
    val name: String,
    val store: Store,
    val installPath: String,
    val cover: String? = null,
)
