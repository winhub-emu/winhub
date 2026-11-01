package com.winhub.hernandez.ui.screens.contents

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.winhub.hernandez.contents.AdrenotoolsManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import com.winhub.hernandez.core.TarCompressorUtils
import com.winhub.hernandez.store.download.ContentDownloadPhase
import com.winhub.hernandez.store.download.ContentDownloadRegistry
import com.winhub.hernandez.store.download.ContentDownloadState
import com.winhub.hernandez.store.download.DownloadForegroundService
import com.winhub.hernandez.store.download.DownloadScope
import com.winhub.hernandez.util.ImportEtaTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * Download + install driver for the Contents hub. Mirrors [com.winhub.hernandez.store.download]'s
 * process-lifetime approach (runs on [DownloadScope.io], bracketed by [DownloadForegroundService],
 * progress published to [ContentDownloadRegistry]) so an install survives backgrounding, and adds
 * two things the plain component downloader doesn't do:
 *   1. the optional "Keep raw archive" copy into [ComponentLibrary] BEFORE the archive is consumed;
 *   2. routing GPU-driver items through [AdrenotoolsManager] instead of the `.wcp` pipeline.
 */
object ContentsInstaller {

    private const val TAG = "ContentsInstaller"

    /** Stable registry key for one catalog item. */
    fun keyFor(type: String, sourceName: String, versionName: String): String =
        "contents::$type::$sourceName::$versionName"

    /**
     * Kicks off a download+install for a remote catalog item. Idempotent per key. [onChanged] is
     * invoked on the install thread after a successful install so the caller can refresh badges.
     */
    fun install(
        appContext: Context,
        type: String,
        sourceName: String,
        versionName: String,
        downloadUrl: String,
        keepRaw: Boolean,
        onChanged: () -> Unit = {},
    ) {
        val ctx = appContext.applicationContext
        val key = keyFor(type, sourceName, versionName)
        ContentDownloadRegistry.get(key)?.let { if (!it.terminal) return }

        val fileName = downloadUrl.substringAfterLast('/').substringBefore('?')
            .ifBlank { "${versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.wcp" }
        val isDriver = ContentsTypes.isDriver(type)

        ContentDownloadRegistry.put(
            ContentDownloadState(
                key = key,
                title = versionName,
                type = type,
                verName = versionName,
                phase = ContentDownloadPhase.DOWNLOADING,
                fraction = 0f,
                hasDownload = true,
            ),
        )
        DownloadForegroundService.start(ctx)
        DownloadForegroundService.setProgress(key, "$versionName — Downloading 0%")

        val job = DownloadScope.io.launch {
            val repo = RemoteSourceRepository(ctx)
            val library = ComponentLibrary(ctx)
            var temp: File? = null
            try {
                // ── Download phase ────────────────────────────────────────────
                temp = repo.downloadToTemp(downloadUrl) { msg ->
                    val pct = Regex("(\\d+)%").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val frac = (pct ?: 0) / 100f
                    ContentDownloadRegistry.update(key) {
                        it.copy(phase = ContentDownloadPhase.DOWNLOADING, fraction = frac)
                    }
                    DownloadForegroundService.setProgress(key, "$versionName — $msg")
                }

                // ── Keep raw archive (before install consumes the file) ───────
                if (keepRaw) {
                    runCatching { library.saveRaw(type, fileName, temp!!) }
                        .onFailure { Log.w(TAG, "keep-raw failed for $key", it) }
                }

                // ── Install phase ─────────────────────────────────────────────
                ContentDownloadRegistry.update(key) {
                    it.copy(phase = ContentDownloadPhase.INSTALLING, fraction = 0f)
                }
                DownloadForegroundService.setProgress(key, "$versionName — Installing")

                val ok = if (isDriver) {
                    installDriverBlocking(ctx, Uri.fromFile(temp))
                } else {
                    installComponentBlocking(ctx, Uri.fromFile(temp)) { frac ->
                        ContentDownloadRegistry.update(key) {
                            it.copy(phase = ContentDownloadPhase.INSTALLING, fraction = maxOf(it.fraction, frac))
                        }
                        DownloadForegroundService.setProgress(key, "$versionName — Installing ${(frac * 100).toInt()}%")
                    }
                }

                ContentDownloadRegistry.update(key) {
                    if (ok) it.copy(phase = ContentDownloadPhase.DONE, fraction = 1f)
                    else it.copy(phase = ContentDownloadPhase.ERROR, error = "Install failed.")
                }
                if (ok) runCatching { onChanged() }
            } catch (c: CancellationException) {
                throw c // user Cancel — requestCancel already set the cancelled-terminal state; keep it.
            } catch (e: Exception) {
                Log.w(TAG, "install failed for $key", e)
                ContentDownloadRegistry.update(key) {
                    it.copy(phase = ContentDownloadPhase.ERROR, error = "Install failed.")
                }
            } finally {
                // Best-effort cancel still deletes the temp archive and closes the FGS bracket.
                temp?.let { runCatching { it.delete() } }
                DownloadForegroundService.finish(key)
                ContentDownloadRegistry.clearJob(key)
                val st = ContentDownloadRegistry.get(key)
                val linger = if (st?.phase == ContentDownloadPhase.ERROR && !st.cancelled) 60_000L else 2_000L
                // NonCancellable so the linger + cleanup still run when we're here due to cancellation.
                withContext(NonCancellable) {
                    delay(linger)
                    if (ContentDownloadRegistry.get(key)?.terminal == true) ContentDownloadRegistry.remove(key)
                }
            }
        }
        ContentDownloadRegistry.attachJob(key, job)
    }

    /**
     * First-run helper: downloads [downloadUrl] and installs it to completion, reporting one
     * human-readable status line. Unlike [install] this is suspend and does not touch the download
     * registry or the foreground service — the first-run splash is foreground and owns the only
     * progress surface, so a notification would just duplicate it.
     */
    suspend fun installAwait(
        appContext: Context,
        downloadUrl: String,
        onStatus: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext.applicationContext
        var temp: File? = null
        try {
            temp = RemoteSourceRepository(ctx).downloadToTemp(downloadUrl, onStatus)
            onStatus("Installing")
            installComponentBlocking(ctx, Uri.fromFile(temp!!)) { frac ->
                onStatus("Installing ${(frac * 100).toInt()}%")
            }
        } catch (e: Exception) {
            Log.w(TAG, "first-run install failed for $downloadUrl", e)
            false
        } finally {
            temp?.let { runCatching { it.delete() } }
        }
    }

    /**
     * Like [installAwait] but for an adrenotools GPU driver zip: returns the installed driver id (what a
     * shortcut's graphicsDriverConfig `version` holds), or null when the download/install failed.
     */
    suspend fun installDriverAwait(
        appContext: Context,
        downloadUrl: String,
        onStatus: (String) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val ctx = appContext.applicationContext
        var temp: File? = null
        try {
            temp = RemoteSourceRepository(ctx).downloadToTemp(downloadUrl, onStatus)
            onStatus("Installing")
            AdrenotoolsManager(ctx).installDriver(Uri.fromFile(temp!!))?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "driver install failed for $downloadUrl", e)
            null
        } finally {
            temp?.let { runCatching { it.delete() } }
        }
    }

    /**
     * "Save archive only": downloads the catalog item and files the raw archive in [ComponentLibrary]
     * (My Files, under the Contents save location) — no extract, no install, the app's contents folder
     * is untouched. Deliberately rides the SAME rails as [install]: same registry key (so a row is
     * either installing or saving, never both), the same [DownloadForegroundService] bracket and
     * shade line, the same popup and Cancel path. The state carries [ContentDownloadState.saveOnly]
     * so the row/popup read "Saving" / "Saved" instead of "Installing" / "Installed".
     *
     * Works for every repository and for GPU drivers alike (a driver .zip is filed under
     * `components/GPU Drivers/`, exactly where a keep-raw install would have put it). The outcome is
     * toasted from the main thread; [onChanged] runs after a successful save so badges/My Files refresh.
     */
    fun saveOnly(
        appContext: Context,
        type: String,
        sourceName: String,
        versionName: String,
        downloadUrl: String,
        onChanged: () -> Unit = {},
    ) {
        val ctx = appContext.applicationContext
        val key = keyFor(type, sourceName, versionName)
        ContentDownloadRegistry.get(key)?.let { if (!it.terminal) return }

        val fileName = downloadUrl.substringAfterLast('/').substringBefore('?')
            .ifBlank { "${versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.wcp" }

        ContentDownloadRegistry.put(
            ContentDownloadState(
                key = key,
                title = versionName,
                type = type,
                verName = versionName,
                phase = ContentDownloadPhase.DOWNLOADING,
                fraction = 0f,
                hasDownload = true,
                saveOnly = true,
            ),
        )
        DownloadForegroundService.start(ctx)
        DownloadForegroundService.setProgress(key, "$versionName — Downloading 0%")

        val job = DownloadScope.io.launch {
            val repo = RemoteSourceRepository(ctx)
            val library = ComponentLibrary(ctx)
            var temp: File? = null
            var ok = false
            try {
                temp = repo.downloadToTemp(downloadUrl) { msg ->
                    val pct = Regex("(\\d+)%").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ContentDownloadRegistry.update(key) {
                        it.copy(phase = ContentDownloadPhase.DOWNLOADING, fraction = (pct ?: 0) / 100f)
                    }
                    DownloadForegroundService.setProgress(key, "$versionName — $msg")
                }

                // ── File phase (moved into place when possible; a copy otherwise) ──
                ContentDownloadRegistry.update(key) {
                    it.copy(phase = ContentDownloadPhase.INSTALLING, fraction = 0f)
                }
                DownloadForegroundService.setProgress(key, "$versionName — Saving to My Files")
                ok = library.saveRaw(type, fileName, temp!!, saveOnly = true)

                ContentDownloadRegistry.update(key) {
                    if (ok) it.copy(phase = ContentDownloadPhase.DONE, fraction = 1f)
                    else it.copy(phase = ContentDownloadPhase.ERROR, error = "Save failed.")
                }
                if (ok) runCatching { onChanged() }
                toast(ctx, if (ok) "Saved to ${library.baseDisplay()}$type/$fileName — see My Files"
                           else "Save failed — $versionName")
            } catch (c: CancellationException) {
                throw c // user Cancel — requestCancel already set the cancelled-terminal state; keep it.
            } catch (e: Exception) {
                Log.w(TAG, "save-only failed for $key", e)
                ContentDownloadRegistry.update(key) {
                    it.copy(phase = ContentDownloadPhase.ERROR, error = "Save failed.")
                }
                toast(ctx, "Save failed — $versionName")
            } finally {
                // A moved temp no longer exists; a copied/cancelled/failed one is cleaned up here.
                temp?.let { runCatching { if (it.exists()) it.delete() } }
                DownloadForegroundService.finish(key)
                ContentDownloadRegistry.clearJob(key)
                val st = ContentDownloadRegistry.get(key)
                val linger = if (st?.phase == ContentDownloadPhase.ERROR && !st.cancelled) 60_000L else 2_000L
                withContext(NonCancellable) {
                    delay(linger)
                    if (ContentDownloadRegistry.get(key)?.terminal == true) ContentDownloadRegistry.remove(key)
                }
            }
        }
        ContentDownloadRegistry.attachJob(key, job)
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { runCatching { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() } }
    }

    /** Installs an already-local archive (My Files "install offline" / install-from-file). */
    fun installFromFile(
        appContext: Context,
        type: String,
        displayName: String,
        source: Uri,
        onDone: (Boolean) -> Unit,
    ) {
        val ctx = appContext.applicationContext
        val key = keyFor(type, "file", displayName)
        ContentDownloadRegistry.get(key)?.let { if (!it.terminal) return }
        val isDriver = ContentsTypes.isDriver(type)

        ContentDownloadRegistry.put(
            ContentDownloadState(
                key = key, title = displayName, type = type, verName = displayName,
                phase = ContentDownloadPhase.INSTALLING, fraction = 0f,
            ),
        )
        DownloadForegroundService.start(ctx)
        DownloadForegroundService.setProgress(key, "$displayName — Installing")

        val job = DownloadScope.io.launch {
            var ok = false
            try {
                // Up-front best-effort read of the archive's profile.json so the popup shows the real
                // type/version/desc from the START (one coherent popup), not just at completion. The
                // end-of-extract onProfile update below still runs as a fallback (covers content:// uris).
                prescanFileProfile(source, type, key)
                ok = if (isDriver) {
                    // GPU drivers carry no profile.json and install non-incrementally — the popup shows
                    // the passed type + filename and an indeterminate bar until this flips to Installed.
                    installDriverBlocking(ctx, source)
                } else {
                    installComponentBlocking(
                        ctx, source,
                        // The archive's parsed profile.json is delivered here at phase 0 (before the file
                        // list is committed): surface its real type/version/code/desc into the popup live,
                        // since a raw local file's key/name carried none of that up front.
                        onProfile = { p ->
                            ContentDownloadRegistry.update(key) {
                                it.copy(
                                    type = p.type?.toString() ?: it.type,
                                    verName = p.verName ?: it.verName,
                                    verCode = p.verCode.toString(),
                                    desc = p.desc ?: it.desc,
                                )
                            }
                        },
                    ) { frac ->
                        ContentDownloadRegistry.update(key) {
                            it.copy(phase = ContentDownloadPhase.INSTALLING, fraction = maxOf(it.fraction, frac))
                        }
                        // Mirror the catalog path so the shade shows a live "… — Installing N%" feed too.
                        val nm = ContentDownloadRegistry.get(key)?.verName ?: displayName
                        DownloadForegroundService.setProgress(key, "$nm — Installing ${(frac * 100).toInt()}%")
                    }
                }
                ContentDownloadRegistry.update(key) {
                    if (ok) it.copy(phase = ContentDownloadPhase.DONE, fraction = 1f)
                    else it.copy(phase = ContentDownloadPhase.ERROR, error = "Install failed.")
                }
            } catch (c: CancellationException) {
                throw c // user Cancel — requestCancel already set the cancelled-terminal state; keep it.
            } catch (e: Exception) {
                Log.w(TAG, "install-from-file failed for $key", e)
                ContentDownloadRegistry.update(key) { it.copy(phase = ContentDownloadPhase.ERROR, error = "Install failed.") }
            } finally {
                runCatching { onDone(ok) }
                DownloadForegroundService.finish(key)
                ContentDownloadRegistry.clearJob(key)
                // NonCancellable so the linger + cleanup still run when we're here due to cancellation.
                withContext(NonCancellable) {
                    delay(2_000L)
                    if (ContentDownloadRegistry.get(key)?.terminal == true) ContentDownloadRegistry.remove(key)
                }
            }
        }
        ContentDownloadRegistry.attachJob(key, job)
    }

    /**
     * Best-effort up-front read of the archive's `profile.json` (single entry, NO full extract) so the
     * install popup carries the real type/version/code/desc from the start instead of only at the end.
     * Mirrors [ContentsManager.readProfile]'s field parsing. Never throws; on any miss the end-of-extract
     * [installComponentBlocking] `onProfile` update still fills these in.
     *
     * Guards: skipped for drivers (no profile.json) and for non-file uris — [TarCompressorUtils.readTextFile]
     * needs a real local [File], so a content:// source (or anything that doesn't resolve to an existing
     * file) falls through to the extract-time fallback.
     */
    private fun prescanFileProfile(source: Uri, type: String, key: String) {
        if (ContentsTypes.isDriver(type)) return
        runCatching {
            val file = if (source.scheme == "file" || source.scheme == null) source.path?.let { File(it) } else null
            if (file == null || !file.exists()) return
            // Archives are XZ or ZSTD; try XZ first, fall back to ZSTD (matches the extract pipeline).
            val json = TarCompressorUtils.readTextFile(TarCompressorUtils.Type.XZ, file, ContentsManager.PROFILE_NAME)
                ?: TarCompressorUtils.readTextFile(TarCompressorUtils.Type.ZSTD, file, ContentsManager.PROFILE_NAME)
                ?: return
            val obj = JSONObject(json)
            val typeName = obj.optString(ContentProfile.MARK_TYPE, "").takeIf { it.isNotBlank() }
            // Prefer the canonical enum display name; fall back to the raw string for unknown types.
            val display = typeName?.let { ContentProfile.ContentType.getTypeByName(it)?.toString() ?: it }
            val verName = obj.optString(ContentProfile.MARK_VERSION_NAME, "").takeIf { it.isNotBlank() }
            val verCode = if (obj.has(ContentProfile.MARK_VERSION_CODE)) obj.optInt(ContentProfile.MARK_VERSION_CODE).toString() else null
            val desc = obj.optString(ContentProfile.MARK_DESC, "").takeIf { it.isNotBlank() }
            ContentDownloadRegistry.update(key) {
                it.copy(
                    type = display ?: it.type,
                    verName = verName ?: it.verName,
                    verCode = verCode ?: it.verCode,
                    desc = desc ?: it.desc,
                )
            }
        }
    }

    // ── Blocking installers (Activity-free; never touch runOnUiThread) ──────────

    /** Install an AdrenoTools driver zip. */
    private suspend fun installDriverBlocking(context: Context, uri: Uri): Boolean =
        runCatching { AdrenotoolsManager(context).installDriver(uri).isNotEmpty() }.getOrDefault(false)

    private suspend fun installComponentBlocking(
        context: Context,
        uri: Uri,
        // Invoked once, at phase 0, with the archive's parsed profile.json (type/version/code/desc are
        // all known here) — lets a local-file install fill in metadata the raw file never carried.
        onProfile: (ContentProfile) -> Unit = {},
        onProgress: (Float) -> Unit,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val cm = ContentsManager(context)
        val total = uri.path?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L
        val etaTracker = ImportEtaTracker()
        try {
            val progress = TarCompressorUtils.OnReadProgressListener { read, tot ->
                if (tot > 0) {
                    etaTracker.update(read, tot)
                    onProgress((read.toFloat() / tot).coerceIn(0f, 1f))
                }
            }
            cm.extraContentFile(uri, total, progress, object : ContentsManager.OnInstallFinishedCallback {
                var phase = 0
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    if (reason == ContentsManager.InstallFailedReason.ERROR_EXIST) {
                        onProgress(1f)
                        if (cont.isActive) cont.resume(true)
                        return
                    }
                    Log.w(TAG, "component install failed: $reason", e)
                    if (cont.isActive) cont.resume(false)
                }
                override fun onSucceed(profile: ContentProfile) {
                    try {
                        if (phase == 0) {
                            phase = 1
                            onProfile(profile)
                            cm.finishInstallContent(profile, this)
                        } else {
                            cm.syncContents()
                            onProgress(1f)
                            if (cont.isActive) cont.resume(true)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "component finishInstall failed", e)
                        if (cont.isActive) cont.resume(false)
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "component extract threw", e)
            if (cont.isActive) cont.resume(false)
        }
    }
}
