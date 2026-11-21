package com.winhub.hernandez.core

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.StringReader

/**
 * Gives a prefix a "Tahoma" font family when it has none, on every launch, whatever store the game came from.
 *
 * Wine's dwrite maps Latin text (U+0000-024F) to "Tahoma" as its last-resort family. A prefix without `tahoma.ttf`
 * has no such family, so any DirectWrite client that asks for a font it cannot find ends up asking for Tahoma
 * again. A Chromium / CEF UI (Skia's DirectWrite font manager) then recurses until its UI-thread stack is gone:
 * device-proven with Ubisoft Connect (920 levels, black screen), and any launcher or store front end built on the
 * same stack is exposed to it. Windows ships the font; a prefix whose `tahoma.ttf` is missing does not.
 *
 * The fix is a `"Tahoma"="Arial"` value under `HKCU\Software\Wine\Fonts\Replacements`, which Wine's font code reads
 * when it looks a family up. A prefix that has the real `tahoma.ttf` is left alone ([hasTahomaFont]), and a
 * "Tahoma" replacement of the user's own is kept as it is.
 */
object WineFontFallback {
    private const val TAG = "WineFontFallback"

    /** Where Wine's dwrite reads font family replacements (`HKCU`, as [WineRegistryEditor] spells a key). */
    private const val REPLACEMENTS_KEY = "Software\\Wine\\Fonts\\Replacements"
    private const val MISSING_FONT = "Tahoma"
    private const val SUBSTITUTE_FONT = "Arial"

    /**
     * Adds the Tahoma replacement to [userReg] (a prefix's `user.reg`) when it has none. Call on the launch worker
     * thread after the prefix is prepared and before any Wine process runs: [WineRegistryEditor] rewrites the
     * file, and a running wineserver would overwrite the change when it saves its own copy.
     *
     * A prefix that has the real font (`drive_c/windows/Fonts/tahoma.ttf` next to [userReg]) costs one file stat and
     * nothing else. Otherwise the common case (the replacement is already there) is one streamed read that stops at the
     * key, and nothing is written. Returns true when the registry was changed. Never throws.
     */
    @JvmStatic
    fun ensureTahomaReplacement(userReg: File): Boolean {
        if (!userReg.isFile) return false
        if (hasTahomaFont(userReg)) return false
        return try {
            if (!userReg.bufferedReader(Charsets.UTF_8).use { needsTahomaReplacement(it) }) return false
            WineRegistryEditor(userReg).use { it.setStringValue(REPLACEMENTS_KEY, MISSING_FONT, SUBSTITUTE_FONT) }
            // The editor swallows I/O errors, so look at the file again before saying it worked.
            val done = !userReg.bufferedReader(Charsets.UTF_8).use { needsTahomaReplacement(it) }
            if (done) Log.i(TAG, "Tahoma font replacement added: $MISSING_FONT -> $SUBSTITUTE_FONT")
            else Log.w(TAG, "could not add the Tahoma font replacement - left as is")
            done
        } catch (e: Exception) {
            Log.w(TAG, "could not add the Tahoma font replacement - left as is", e)
            false
        }
    }

    /** Where the real font sits, relative to the prefix folder (the one holding `user.reg`). */
    private const val FONT_FILE = "drive_c/windows/Fonts/tahoma.ttf"

    /** True when the prefix of [userReg] has a real `tahoma.ttf`: Wine then has the family itself and needs no replacement. */
    internal fun hasTahomaFont(userReg: File): Boolean {
        val prefix = userReg.parentFile ?: return false
        return File(prefix, FONT_FILE).isFile
    }

    /**
     * True when [userReg] (the text of a `user.reg`) has no "Tahoma" value under `Software\Wine\Fonts\Replacements`.
     * Line-based: key and value names compare ignoring case, the value's data is not looked at.
     */
    @JvmStatic
    fun tahomaReplacementNeeded(userReg: String): Boolean = needsTahomaReplacement(BufferedReader(StringReader(userReg)))

    /** [tahomaReplacementNeeded] over a stream: stops reading as soon as the answer is known. */
    private fun needsTahomaReplacement(reader: BufferedReader): Boolean {
        val header = "[" + REPLACEMENTS_KEY.replace("\\", "\\\\") + "]"
        val value = "\"$MISSING_FONT\"="
        var inKey = false
        while (true) {
            val line = reader.readLine() ?: return true
            if (line.startsWith("[")) {
                // The next key after Replacements: it had no Tahoma value (sub-keys are keys of their own).
                if (inKey) return true
                inKey = line.startsWith(header, ignoreCase = true)
            } else if (inKey && line.startsWith(value, ignoreCase = true)) return false
        }
    }
}
