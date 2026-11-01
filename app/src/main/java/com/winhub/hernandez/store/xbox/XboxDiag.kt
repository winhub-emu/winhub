package com.winhub.hernandez.store.xbox

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Xbox store diagnostics: logcat (tag "BannerXbox") and a small rolling file,
 * Download/Bannerlator/xbox_diag.txt, to hand over when something fails. Only step names, error classes,
 * exit codes and the backend's own allow-listed diagnostic lines go here; never account names, tokens or
 * game data.
 */
object XboxDiag {
    private const val TAG = "BannerXbox"
    private const val MAX_BYTES = 512 * 1024L
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun file(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Bannerlator/xbox_diag.txt")

    @Synchronized fun log(app: Context?, message: String) {
        // Lines are allow-listed where they are made; the shared scrubber is the backstop (account names, emails, tokens).
        val line = com.winhub.hernandez.store.SteamLogRedactor.redact(message)
        Log.i(TAG, line)
        runCatching {
            val f = file()
            f.parentFile?.mkdirs()
            if (f.length() > MAX_BYTES) {
                val old = File(f.path + ".1"); old.delete(); f.renameTo(old)
            }
            f.appendText("${time.format(Date())} [${android.os.Process.myPid()}] $line\n")
        }
    }
}
