package com.winhub.hernandez.store

import android.content.Context
import android.util.Log

/**
 * The game to start once the Ubisoft Connect setup has finished.
 *
 * The setup is one container session ([com.winhub.hernandez.store.steamscript.InstallScriptExecutor]); when its batch is
 * done the session exits and the app RESTARTS (`AppUtils.restartApplication` ends the process), so an in-memory marker
 * would be gone. The dialog records the shortcut here the moment the setup session has been launched; the library
 * screen that comes back after the restart ([com.winhub.hernandez.ui.screens.ShortcutLauncher.resumePendingRelaunch], the
 * same hook the SteamLite relaunch uses) reads it once, checks that Connect is now in the prefix, and starts the game
 * through the normal launch flow. If Connect is not there the game is not started and the user is told.
 *
 * One shot: the marker is deleted before anything is launched, so nothing can start the game twice or loop. It
 * lives 30 minutes (the setup session's own cap is 15), is dropped by any other launch from the screens, and a marker
 * written by THIS process is never acted on (the setup session may still be running in it): only a restarted app acts.
 * Best-effort: every failure here means "no pending launch", never a crash.
 */
object UbisoftPendingLaunch {
    private const val TAG = "UbisoftPendingLaunch"
    private const val PREFS = "ubisoft_pending_launch"
    private const val KEY_PATH = "path"
    private const val KEY_AT = "at"

    /** The setup session's longest run is 15 minutes (its batch caps the wait), plus the boot and the exit. */
    const val TTL_MS = 30L * 60L * 1000L

    /** A recorded setup: the shortcut file's path and when the session was launched (epoch millis). */
    class Pending(val shortcutPath: String, val startedAt: Long)

    /** What to do with a pending launch (see [verdict]). */
    enum class Verdict {
        /** The marker was written by this very process: the setup session may still be running. Leave it alone. */
        WAIT,

        /** Too old (or from the future): drop it, say nothing. */
        EXPIRED,

        /** Connect is in the prefix: start the game. */
        LAUNCH,

        /** The session is over but Connect is not there (failed, cancelled, killed): do not start the game, tell the user. */
        NOT_FINISHED,
    }

    /** True once a setup was recorded by this process; a restarted app starts with false. */
    @Volatile
    private var recordedHere = false

    /**
     * What to do with a pending launch. Pure. [recordedInThisProcess] = the marker was written by this process,
     * [ageMs] = how long ago, [connectInstalled] = Ubisoft Connect is in the container's prefix now.
     */
    @JvmStatic
    fun verdict(recordedInThisProcess: Boolean, ageMs: Long, connectInstalled: Boolean): Verdict = when {
        recordedInThisProcess -> Verdict.WAIT
        ageMs < 0L || ageMs > TTL_MS -> Verdict.EXPIRED
        connectInstalled -> Verdict.LAUNCH
        else -> Verdict.NOT_FINISHED
    }

    /** Records that a setup session was launched for the shortcut file [shortcutPath]. Replaces an earlier marker. */
    @JvmStatic
    fun record(context: Context, shortcutPath: String) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PATH, shortcutPath)
                .putLong(KEY_AT, System.currentTimeMillis())
                .apply()
            recordedHere = true
            Log.i(TAG, "pending launch recorded for $shortcutPath")
        } catch (t: Throwable) {
            Log.w(TAG, "record failed", t)
        }
    }

    /** The pending launch, or null. Does not remove it. */
    @JvmStatic
    fun peek(context: Context): Pending? {
        return try {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val path = p.getString(KEY_PATH, null)?.takeIf { it.isNotEmpty() } ?: return null
            Pending(path, p.getLong(KEY_AT, 0L))
        } catch (t: Throwable) {
            Log.w(TAG, "peek failed", t)
            null
        }
    }

    /** True when the marker (if any) was written by this process, see [Verdict.WAIT]. */
    @JvmStatic
    fun recordedInThisProcess(): Boolean = recordedHere

    /** Removes the pending launch. A read of one small preference and nothing more when there is none. */
    @JvmStatic
    fun clear(context: Context) {
        try {
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.contains(KEY_PATH)) p.edit().clear().apply()
            recordedHere = false
        } catch (t: Throwable) {
            Log.w(TAG, "clear failed", t)
        }
    }
}
