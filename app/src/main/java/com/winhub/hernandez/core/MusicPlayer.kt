package com.winhub.hernandez.core

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException

/**
 * Background music for the app and, if the user allows it, for games (Settings → General → Music Player).
 *
 * The chosen track is copied into the app's own storage, so it keeps playing if the original moves. It
 * loops. It plays only while the app is on screen (a process-level foreground check, so it never keeps
 * going behind other apps) and in a game only when "Play in games" is on, which it is not by default.
 * It takes no audio focus, so it never pauses the game's own sound.
 *
 * Everything here runs on the main thread except [importTrack], which copies a file.
 */
object MusicPlayer {
    private const val TAG = "MusicPlayer"
    private const val PREF_FILE = "music_file"
    private const val PREF_NAME = "music_name"
    private const val PREF_ENABLED = "music_enabled"
    private const val PREF_IN_GAME = "music_in_game"

    private var appContext: Context? = null
    private var player: MediaPlayer? = null
    private var playerPath: String? = null
    private var prepared = false
    private var appForeground = false
    private var inGame = false

    /** Starts following the app's foreground state. Call once from Application.onCreate. */
    fun install(app: Application) {
        appContext = app.applicationContext
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { appForeground = true; sync() }
            override fun onStop(owner: LifecycleOwner) { appForeground = false; sync() }
        })
    }

    /** True while the game screen is in front. Set by the game activity. */
    fun setInGame(value: Boolean) {
        if (inGame == value) return
        inGame = value
        sync()
    }

    private fun prefs(): SharedPreferences? = appContext?.let { PreferenceManager.getDefaultSharedPreferences(it) }

    private fun trackFile(): File? {
        val path = prefs()?.getString(PREF_FILE, null) ?: return null
        return File(path).takeIf { it.isFile }
    }

    /** The chosen track's display name, or null when there is none. */
    fun trackName(): String? = if (trackFile() != null) prefs()?.getString(PREF_NAME, null) else null

    fun isEnabled(): Boolean = prefs()?.getBoolean(PREF_ENABLED, true) ?: true
    fun playsInGame(): Boolean = prefs()?.getBoolean(PREF_IN_GAME, false) ?: false

    fun setEnabled(value: Boolean) {
        prefs()?.edit()?.putBoolean(PREF_ENABLED, value)?.apply()
        sync()
    }

    fun setPlayInGame(value: Boolean) {
        prefs()?.edit()?.putBoolean(PREF_IN_GAME, value)?.apply()
        sync()
    }

    /**
     * Copies [source] into the app's storage as the music track and makes it the current one. Blocking:
     * call off the main thread, then call [reload] on the main thread. False when the copy fails.
     */
    fun importTrack(context: Context, source: Uri, displayName: String): Boolean {
        val dir = File(context.filesDir, "music")
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val ext = displayName.substringAfterLast('.', "mp3").lowercase().takeIf { it.length in 2..4 } ?: "mp3"
        val tmp = File(dir, "track.part")
        try {
            val input = context.contentResolver.openInputStream(source) ?: return false
            input.use { src -> tmp.outputStream().use { out -> src.copyTo(out) } }
        } catch (e: IOException) {
            Log.w(TAG, "could not copy the track", e)
            tmp.delete()
            return false
        }
        dir.listFiles()?.forEach { if (it.name.startsWith("track.") && it.name != tmp.name) it.delete() }
        val dest = File(dir, "track.$ext")
        if (!tmp.renameTo(dest)) { tmp.delete(); return false }
        prefs()?.edit()
            ?.putString(PREF_FILE, dest.absolutePath)
            ?.putString(PREF_NAME, displayName)
            ?.putBoolean(PREF_ENABLED, true)
            ?.apply()
        return true
    }

    /** Removes the chosen track and stops playback. */
    fun clear(context: Context) {
        releasePlayer()
        File(context.filesDir, "music").listFiles()?.forEach { it.delete() }
        prefs()?.edit()?.remove(PREF_FILE)?.remove(PREF_NAME)?.apply()
    }

    /** Re-reads the track (after [importTrack] replaced the file) and applies the current state. */
    fun reload() {
        releasePlayer()
        sync()
    }

    private fun shouldPlay(): Boolean =
        trackFile() != null && isEnabled() && appForeground && (!inGame || playsInGame())

    /** Starts or pauses playback to match the current state. */
    fun sync() {
        val file = trackFile()
        if (file == null || !shouldPlay()) {
            pausePlayer()
            if (file == null) releasePlayer()
            return
        }
        if (player == null || playerPath != file.absolutePath) startPlayer(file)
        else if (prepared && player?.isPlaying == false) player?.start()
    }

    private fun startPlayer(file: File) {
        releasePlayer()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.isLooping = true
            mp.setOnPreparedListener { p ->
                prepared = true
                // The state may have changed while it was preparing.
                if (shouldPlay()) p.start()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "playback error $what/$extra")
                releasePlayer()
                true
            }
            player = mp
            playerPath = file.absolutePath
            prepared = false
            mp.prepareAsync()
        } catch (e: IOException) {
            Log.w(TAG, "could not open the track", e)
            releasePlayer()
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not start the track", e)
            releasePlayer()
        }
    }

    private fun pausePlayer() {
        val p = player ?: return
        if (!prepared) return
        try {
            if (p.isPlaying) p.pause()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "pause failed", e)
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        val p = player ?: return
        player = null
        playerPath = null
        prepared = false
        try { p.release() } catch (e: RuntimeException) { Log.w(TAG, "release failed", e) }
    }
}
