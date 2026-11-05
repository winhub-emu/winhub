package com.winhub.hernandez.core

import android.content.Context
import android.util.Log
import com.winhub.hernandez.contents.ContentsManager
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * The "Sync" setting: which Wine synchronization primitive a launch uses.
 *
 *  - esync      → WINEESYNC=1 (eventfd). The default on every layer that has it.
 *  - ntsync     → WINENTSYNC=1 + WINEESYNC=1. Only layers built with ntsync carry it; esync stays
 *                 set as the layer's own fallback for when /dev/ntsync can't be opened.
 *  - wineserver → WINEESYNC=0: every wait goes through the wineserver. Slowest, most compatible.
 *  - fsync      → never. futex_waitv is SIGSYS under the app's seccomp filter and the layers compile
 *                 fsync out, so it is shown greyed with the reason and never stored.
 *
 * Stored as the extra [EXTRA] on the container (always) and on a shortcut (only as a per-game
 * override; absent = follow the container). Before this setting existed the choice lived in the env
 * strings (WINEESYNC / WINENTSYNC); [legacyModeFromEnv] reads those when the extra is absent, so an
 * old container keeps what it had. At launch the selector is the only source of truth:
 * [applyToEnv] removes every WINEESYNC / WINENTSYNC / WINEFSYNC the env strings carried and writes
 * the resolved mode's variables.
 */
object SyncMode {
    const val EXTRA = "syncMode"
    const val ESYNC = "esync"
    const val NTSYNC = "ntsync"
    const val FSYNC = "fsync"
    const val WINESERVER = "wineserver"

    /** Pill order in the editors. */
    @JvmField val ALL: List<String> = listOf(ESYNC, NTSYNC, FSYNC, WINESERVER)

    /** A stored/imported value if it is one we can run, else null (fsync is never runnable). */
    @JvmStatic
    fun normalize(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it == ESYNC || it == NTSYNC || it == WINESERVER }
}

/** What one layer can do. wineserver is always possible; fsync never is. */
data class SyncCaps(val esync: Boolean, val ntsync: Boolean) {
    fun isAvailable(mode: String): Boolean = when (mode) {
        SyncMode.ESYNC -> esync
        SyncMode.NTSYNC -> ntsync
        SyncMode.WINESERVER -> true
        else -> false
    }

    /** esync when the layer has it, else wineserver. */
    val defaultMode: String get() = if (esync) SyncMode.ESYNC else SyncMode.WINESERVER

    /** [requested] when this layer can run it, else the layer default. */
    fun resolve(requested: String?): String =
        SyncMode.normalize(requested)?.takeIf { isAvailable(it) } ?: defaultMode

    companion object {
        /** A layer we couldn't read: esync is what every launch used before this setting. */
        @JvmField val UNKNOWN = SyncCaps(esync = true, ntsync = false)
    }
}

object SyncSupport {
    private const val TAG = "SyncSupport"

    private val SYNC_VARS = listOf("WINEESYNC", "WINENTSYNC", "WINEFSYNC")

    // ── Pure mapping (unit-tested) ───────────────────────────────────────────────────────────────

    /**
     * The mode an env string asked for before the selector existed: WINENTSYNC=1 → ntsync,
     * WINEESYNC=0 → wineserver, WINEESYNC=<anything else> → esync, neither → null (no opinion).
     */
    @JvmStatic
    fun legacyModeFromEnv(env: EnvVars?): String? {
        if (env == null) return null
        if (env.has("WINENTSYNC") && env.get("WINENTSYNC").trim() == "1") return SyncMode.NTSYNC
        if (env.has("WINEESYNC")) {
            val v = env.get("WINEESYNC").trim()
            if (v == "0") return SyncMode.WINESERVER
            if (v.isNotEmpty()) return SyncMode.ESYNC
        }
        return null
    }

    /** The stored choice of one scope: its extra, else what its env string implied, else null. */
    @JvmStatic
    fun storedMode(extra: String?, env: String?): String? =
        SyncMode.normalize(extra) ?: legacyModeFromEnv(env?.let { EnvVars(it) })

    /**
     * What a launch asks for: the shortcut's own choice (extra or legacy env), else the container's,
     * else null (= the layer default).
     */
    @JvmStatic
    fun requestedMode(containerExtra: String?, containerEnv: String?, shortcutExtra: String?, shortcutEnv: String?): String? =
        storedMode(shortcutExtra, shortcutEnv) ?: storedMode(containerExtra, containerEnv)

    /**
     * Make [env] carry exactly [mode]'s variables. Drops every WINEESYNC / WINENTSYNC / WINEFSYNC first
     * (WINEFSYNC does nothing on Android and only confuses logs and users).
     */
    @JvmStatic
    fun applyToEnv(env: EnvVars, mode: String) {
        for (name in SYNC_VARS) env.remove(name)
        when (mode) {
            SyncMode.NTSYNC -> { env.put("WINENTSYNC", "1"); env.put("WINEESYNC", "1") }
            SyncMode.WINESERVER -> env.put("WINEESYNC", "0")
            else -> env.put("WINEESYNC", "1")
        }
    }

    /**
     * [envString] without any WINEESYNC / WINENTSYNC / WINEFSYNC token — written back when an editor
     * saves, so the Env Vars tab no longer carries a value the selector has taken over. Every other
     * token is kept byte for byte (no EnvVars round-trip, which would drop malformed tokens).
     */
    @JvmStatic
    fun stripSyncVars(envString: String?): String {
        if (envString.isNullOrEmpty()) return envString ?: ""
        return envString.split(" ")
            .filter { tok -> tok.isNotEmpty() && SYNC_VARS.none { tok.startsWith("$it=") } }
            .joinToString(" ")
    }

    // ── Layer capability detection ───────────────────────────────────────────────────────────────

    // Markers in the layer's unix ntdll.so. "esync: up and running" is esync's startup trace and
    // WINEESYNC its getenv (either means the esync code is compiled in); WINENTSYNC is the getenv of
    // the ntsync opt-in, which only some layers have.
    private val ESYNC_MARKERS = listOf("esync: up and running", "WINEESYNC")
    private const val NTSYNC_MARKER = "WINENTSYNC"

    private class Entry(val stamp: String, val caps: SyncCaps)
    private val byPath = HashMap<String, Entry>()
    private val byIdentifier = HashMap<String, SyncCaps>()

    /** The last answer for this wine version identifier, or null if it was never probed (no I/O). */
    @JvmStatic
    fun peek(identifier: String?): SyncCaps? =
        if (identifier.isNullOrEmpty()) null else synchronized(byIdentifier) { byIdentifier[identifier] }

    /**
     * Capabilities of the layer a wine version identifier resolves to (installed contents entry, or
     * the bundled main Wine — the same resolution WineInfo does). Blocking file I/O: call off the
     * main thread. Builds a ContentsManager when [contentsManager] is null.
     */
    @JvmStatic
    fun capsFor(context: Context, contentsManager: ContentsManager?, identifier: String?): SyncCaps {
        if (identifier.isNullOrEmpty()) return SyncCaps.UNKNOWN
        val caps = try {
            val cm = contentsManager ?: ContentsManager(context).also { it.syncContents() }
            capsForLayerPath(WineInfo.fromIdentifier(context, cm, identifier).path)
        } catch (e: Exception) {
            Log.w(TAG, "caps for $identifier: ${e.message}")
            SyncCaps.UNKNOWN
        }
        synchronized(byIdentifier) { byIdentifier[identifier] = caps }
        return caps
    }

    /**
     * Capabilities of the layer installed at [layerPath]. Cached per directory and re-probed when its
     * profile.json or unix ntdll.so changes (mtime/size). A profile.json "capabilities" array wins over
     * the byte scan; an unreadable ntdll.so reads as [SyncCaps.UNKNOWN].
     */
    @JvmStatic
    fun capsForLayerPath(layerPath: String?): SyncCaps {
        if (layerPath.isNullOrEmpty()) return SyncCaps.UNKNOWN
        val dir = File(layerPath)
        val profile = File(dir, ContentsManager.PROFILE_NAME)
        val ntdll = unixNtdll(dir)
        val stamp = "${profile.lastModified()}:${ntdll?.lastModified() ?: 0}:${ntdll?.length() ?: 0}"
        synchronized(byPath) {
            byPath[layerPath]?.takeIf { it.stamp == stamp }?.let { return it.caps }
        }
        val fromProfile = capsFromProfile(profile)
        val caps = fromProfile ?: capsFromNtdll(ntdll)
        Log.i(TAG, "layer $layerPath: esync=${caps.esync} ntsync=${caps.ntsync} (" +
            (if (fromProfile != null) "profile.json" else ntdll?.path ?: "no ntdll.so") + ")")
        synchronized(byPath) { byPath[layerPath] = Entry(stamp, caps) }
        return caps
    }

    /** Drop every cached verdict — after a layer is installed or removed. */
    @JvmStatic
    fun invalidate() {
        synchronized(byPath) { byPath.clear() }
        synchronized(byIdentifier) { byIdentifier.clear() }
    }

    private fun unixNtdll(dir: File): File? =
        listOf("lib/wine/aarch64-unix/ntdll.so", "lib/wine/x86_64-unix/ntdll.so")
            .map { File(dir, it) }.firstOrNull { it.isFile }

    private fun capsFromProfile(profile: File): SyncCaps? = try {
        if (!profile.isFile) null
        else capsFromProfileJson(JSONObject(profile.readText()))
    } catch (e: Exception) { null }

    /** A profile.json "capabilities": ["esync", "ntsync", …] array, or null when it has none. */
    @JvmStatic
    fun capsFromProfileJson(json: JSONObject): SyncCaps? {
        val arr = json.optJSONArray("capabilities") ?: return null
        val set = (0 until arr.length()).map { arr.optString(it, "").trim().lowercase() }.toSet()
        return SyncCaps(esync = SyncMode.ESYNC in set, ntsync = SyncMode.NTSYNC in set)
    }

    private fun capsFromNtdll(ntdll: File?): SyncCaps {
        if (ntdll == null) return SyncCaps.UNKNOWN
        return try {
            val markers = ESYNC_MARKERS + NTSYNC_MARKER
            val found = BufferedInputStream(FileInputStream(ntdll)).use {
                scanForMarkers(it, markers.map { m -> m.toByteArray(Charsets.US_ASCII) })
            }
            SyncCaps(esync = found[0] || found[1], ntsync = found[2])
        } catch (e: Exception) {
            Log.w(TAG, "scan ${ntdll.path}: ${e.message}")
            SyncCaps.UNKNOWN
        }
    }

    /**
     * Which of [markers] occur anywhere in [input]. Streams in 64 KB chunks, carrying the last
     * (longest marker - 1) bytes over so a marker straddling two chunks is still found. Stops early
     * once every marker has been seen.
     */
    @JvmStatic
    fun scanForMarkers(input: InputStream, markers: List<ByteArray>): BooleanArray {
        val found = BooleanArray(markers.size)
        if (markers.isEmpty()) return found
        val maxLen = markers.maxOf { it.size }
        val buf = ByteArray(64 * 1024 + maxLen)
        var carry = 0
        while (true) {
            val n = input.read(buf, carry, buf.size - carry)
            if (n <= 0) break
            val len = carry + n
            for (i in markers.indices) if (!found[i]) found[i] = indexOf(buf, len, markers[i])
            if (found.all { it }) break
            carry = minOf(maxLen - 1, len)
            System.arraycopy(buf, len - carry, buf, 0, carry)
        }
        return found
    }

    private fun indexOf(buf: ByteArray, len: Int, pat: ByteArray): Boolean {
        if (pat.isEmpty()) return true
        val first = pat[0]
        var i = 0
        val last = len - pat.size
        outer@ while (i <= last) {
            if (buf[i] == first) {
                for (j in 1 until pat.size) if (buf[i + j] != pat[j]) { i++; continue@outer }
                return true
            }
            i++
        }
        return false
    }
}
