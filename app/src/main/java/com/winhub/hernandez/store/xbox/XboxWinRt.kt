package com.winhub.hernandez.store.xbox

import android.content.Context
import com.winhub.hernandez.core.WineRegistryEditor
import java.io.File

/**
 * Windows Runtime classes a 64-bit Xbox game activates that some container prefixes only register in the
 * 32-bit view. Sign-in (XUserAddAsync) parses Xbox Live's endpoint list through
 * Windows.Foundation.Collections.PropertySet; wintypes.dll implements it, but prefixes updated by a newer Wine
 * got it only under Wow6432Node, so RoGetActivationFactory failed with 0x80040154 and games that sign in at
 * start sat on a black screen (BALL x PIT). Every class registered in the 32-bit view whose DLL is in the
 * 64-bit system32 is added to the 64-bit view as well. Runs before Wine starts (the registry file is idle).
 */
object XboxWinRt {
    private const val ROOT = "Software\\Microsoft\\WindowsRuntime\\ActivatableClassId\\"
    private val WOW64_KEY = Regex("""^\[Software\\\\Wow6432Node\\\\Microsoft\\\\WindowsRuntime\\\\ActivatableClassId\\\\([^\]\\]+)\]""")
    private val NATIVE_KEY = Regex("""^\[Software\\\\Microsoft\\\\WindowsRuntime\\\\ActivatableClassId\\\\([^\]\\]+)\]""")
    private val DLL_PATH = Regex("""^"DllPath"="C:\\\\windows\\\\system32\\\\([^"\\]+)"""", RegexOption.IGNORE_CASE)

    /** Registers [cls] in the 64-bit view as [dll] in system32, when it is not registered there. */
    fun require(app: Context, prefix: File, cls: String, dll: String) {
        val systemReg = File(prefix, "system.reg")
        val present = runCatching { systemReg.readLines() }.getOrNull()?.any { NATIVE_KEY.find(it)?.groupValues?.get(1) == cls } ?: return
        if (present) return
        WineRegistryEditor(systemReg).use { editor ->
            editor.setCreateKeyIfNotExist(true)
            editor.setStringValue(ROOT + cls, "DllPath", "C:\\windows\\system32\\$dll")
        }
        XboxDiag.log(app, "registered $cls ($dll) in the 64-bit view")
    }

    @JvmStatic fun ensure(app: Context, prefix: File) {
        val systemReg = File(prefix, "system.reg")
        val system32 = File(prefix, "drive_c/windows/system32")
        val lines = runCatching { systemReg.readLines() }.getOrNull() ?: return
        val native = HashSet<String>()
        val wow64 = LinkedHashMap<String, String>()
        var current: String? = null
        for (line in lines) {
            NATIVE_KEY.find(line)?.let { native += it.groupValues[1]; current = null; return@let }
            if (line.startsWith("[")) current = WOW64_KEY.find(line)?.groupValues?.get(1)
            else current?.let { cls -> DLL_PATH.find(line)?.let { wow64[cls] = it.groupValues[1] } }
        }
        val missing = wow64.filter { (cls, dll) -> cls !in native && File(system32, dll).isFile }
        if (missing.isEmpty()) return
        WineRegistryEditor(systemReg).use { editor ->
            editor.setCreateKeyIfNotExist(true)
            for ((cls, dll) in missing) editor.setStringValue(ROOT + cls, "DllPath", "C:\\windows\\system32\\$dll")
        }
        XboxDiag.log(app, "registered ${missing.size} WinRT class(es) in the 64-bit view: ${missing.keys.joinToString(", ").take(300)}")
    }
}
