package com.winhub.hernandez.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.winhub.hernandez.BuildConfig
import com.winhub.hernandez.R
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Fires a GET at the ten WinHub release assets (`…/ignore/1.json` … `10.json`) every time the app
 * is opened. The response bodies are read and thrown away — nothing is parsed, nothing is kept:
 * the download itself is the whole point.
 *
 * One background thread per run, connect/read timeouts on, and every failure is swallowed, so a
 * dead or slow network can never delay or break app startup. [running] collapses calls that arrive
 * while a run is still in flight, which is the normal case: a cold start is immediately followed by
 * the process-lifecycle foreground callback.
 *
 * A run never looks scripted: the asset order is shuffled per run, consecutive requests are
 * separated by 80–300 ms of jitter, and the run is bounded by both [MAX_REQUESTS_PER_RUN] and
 * [RUN_DEADLINE_MS] so a slow network cannot pin the thread (the old worst case was 10 × 10 s).
 * A 429/403 aborts the run instead of hammering through the throttle.
 *
 * With [notify] set, the outcome of the run is surfaced when the run finishes: "No necessary
 * updates found." shows as the top-right Compose banner ([ComposeNotifier]), while a throttle
 * message (GitHub pushed back) and a failure message (nothing answered) keep the centered toast.
 */
object WinhubPing {
    private const val TAG = "WinhubPing"
    private const val BASE_URL = "https://github.com/winhub-emu/winhub/releases/download/ignore/"
    private const val FILE_COUNT = 10
    private const val TIMEOUT_MS = 10_000

    /** Hard ceiling on requests in one run, whatever the loop is configured to do. */
    private const val MAX_REQUESTS_PER_RUN = 40

    /** Hard ceiling on how long one run may hold the background thread. */
    private const val RUN_DEADLINE_MS = 20_000L

    /** Human-ish spacing between requests, so no two runs have the same rhythm. */
    private const val JITTER_MIN_MS = 80L
    private const val JITTER_MAX_MS = 300L

    private enum class Outcome { OK, FAILED, THROTTLED }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var running = false

    /**
     * Download all [FILE_COUNT] assets on a background thread and discard the bodies. Never throws.
     *
     * @param context used only to resolve the result toast; the application context is taken from
     *   it, so an Activity is never held past this call.
     * @param notify when true, show the result toast once the run finishes.
     */
    fun ping(context: Context? = null, notify: Boolean = false) {
        if (running) return
        running = true
        val appContext = context?.applicationContext
        try {
            Thread {
                var ok = 0
                var sent = 0
                var throttled = false
                val startedAt = System.currentTimeMillis()
                try {
                    val order = (1..FILE_COUNT).toMutableList().also { it.shuffle() }
                    for ((position, index) in order.withIndex()) {
                        if (sent >= MAX_REQUESTS_PER_RUN) break
                        if (System.currentTimeMillis() - startedAt > RUN_DEADLINE_MS) break
                        sent++
                        val outcome = download(index)
                        if (outcome == Outcome.OK) {
                            ok++
                        } else if (outcome == Outcome.THROTTLED) {
                            throttled = true
                        }
                        if (throttled) break
                        if (position < order.lastIndex) {
                            Thread.sleep(Random.nextLong(JITTER_MIN_MS, JITTER_MAX_MS + 1))
                        }
                    }
                } catch (t: Throwable) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "ping aborted", t)
                } finally {
                    running = false
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "ping finished: $ok/$sent ok, throttled=$throttled, " +
                            "${System.currentTimeMillis() - startedAt}ms")
                    }
                    if (notify) report(appContext, ok, throttled)
                }
            }.start()
        } catch (t: Throwable) {
            running = false
            if (BuildConfig.DEBUG) Log.d(TAG, "ping not scheduled", t)
        }
    }

    /** Reports the run outcome; [ComposeNotifier.show] is safe from any thread. */
    private fun report(context: Context?, ok: Int, throttled: Boolean) {
        if (context == null) return
        if (ok > 0 && UpdateManager.isNotifyEnabled(context)) {
            // A newer release turns the banner into "Update available"; the user's skipped version stays quiet.
            UpdateManager.check(context) { info ->
                val newer = info?.takeIf { it.isNewer && it.versionCode != UpdateManager.skippedVersionCode(context) }
                mainHandler.post {
                    if (newer != null) ComposeNotifier.showUpdate(context, newer.versionName)
                    else showOutcome(context, ok, throttled)
                }
            }
            return
        }
        showOutcome(context, ok, throttled)
    }

    private fun showOutcome(context: Context, ok: Int, throttled: Boolean) {
        val message = context.getString(
            when {
                ok > 0 -> R.string.winhub_ping_no_updates
                throttled -> R.string.winhub_ping_throttled
                else -> R.string.winhub_ping_failed
            }
        )
        // Every outcome goes through the top-right Compose banner (one per app open, enforced by
        // ComposeNotifier). The centered toast is kept only for the rest of the app.
        ComposeNotifier.show(context, message)
    }

    private fun download(index: Int): Outcome {
        try {
            val connection = (URL("$BASE_URL$index.json").openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "WinHub")
            }
            try {
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    // Read the body fully (that is the download) and drop it on the floor.
                    connection.inputStream.use { stream -> stream.readBytes() }
                    return Outcome.OK
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "$index.json -> HTTP $code")
                return if (code == 429 || code == 403) Outcome.THROTTLED else Outcome.FAILED
            } finally {
                connection.disconnect()
            }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "$index.json failed", t)
            return Outcome.FAILED
        }
    }
}
