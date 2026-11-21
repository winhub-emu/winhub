package com.winhub.hernandez.store

import java.io.File

/**
 * What Ubisoft Connect records about a game it installed, read from the `uplay_install.state` file it leaves in the
 * game's folder (Epic Games Store builds of Ubisoft titles ship that file too). The product id and the install
 * language are what Connect writes into the registry when the user picks "Locate installed game" (see
 * [UbisoftInstallRegistry]). The per-language registry values (field 15) are only read for the diagnostics report.
 *
 * The file is Ubisoft's own protobuf (no schema is published; field number first): 1 install hash, 3 install language
 * (`en-US`), 13 game name (a message holding the name as its field 1), 15 (repeated) one registry value per language
 * `{1 registry path, 2 language code, 3 value}`, 22 display name with the trademark sign, 26 language again,
 * **28 product id** (the number in Connect's `Installs\<id>` key). Everything else is skipped.
 *
 * The reader is defensive on purpose, the file comes from a game folder: it never throws, gives up on a file over
 * [MAX_BYTES] or one that is not whole protobuf (a length that runs past the end, a truncated varint, a group
 * wire type), and returns null without a usable product id.
 */
data class UbisoftInstallState(
    val productId: Int,
    /** The game's name (field 13, else field 22), empty when the file has none. */
    val name: String,
    /** Field 22 (the name with its trademark sign) when present, else [name]. */
    val displayName: String,
    /** The install language, e.g. `en-US` (field 3, else 26, else `en-US`). */
    val language: String,
    /** The game's per-language registry values (field 15), in file order. */
    val registry: List<RegistryEntry>,
) {
    /** One `{registry path, language code, value}` entry, e.g. `HKEY_LOCAL_MACHINE\SOFTWARE\Ubisoft\<game>\Language`, `en-US`, `English(US)`. */
    data class RegistryEntry(val path: String, val language: String, val value: String)

    companion object {
        /** Real files are about 3 KB; anything this big is not a state file. */
        const val MAX_BYTES = 1024 * 1024

        private const val DEFAULT_LANGUAGE = "en-US"
        private const val MAX_ENTRIES = 512

        /** [file] parsed, or null when it is missing, unreadable, too big or not a state file. Never throws. */
        @JvmStatic
        fun read(file: File): UbisoftInstallState? = try {
            if (!file.isFile || file.length() > MAX_BYTES) null else parse(file.readBytes())
        } catch (e: Exception) {
            null
        }

        /** [bytes] parsed, or null when they are not a whole state file with a product id above 0. Never throws. */
        @JvmStatic
        fun parse(bytes: ByteArray): UbisoftInstallState? = try {
            parseOrThrow(bytes)
        } catch (e: Exception) {
            null
        }

        /**
         * Field 1 of the state (the install hash) as text when it is printable, else as hex; null when the file has no
         * such field. Only for the diagnostics report, the registry writes do not use it. Never throws.
         */
        @JvmStatic
        fun installHash(bytes: ByteArray): String? {
            var found: String? = null
            try {
                walk(bytes, 0, bytes.size) { field, wire, _, at, len ->
                    if (field == 1 && wire == WIRE_BYTES && found == null) {
                        val raw = bytes.copyOfRange(at, at + minOf(len, 256))
                        found = if (raw.all { it >= 0x20 && it < 0x7f }) String(raw, Charsets.US_ASCII)
                        else raw.joinToString("") { "%02x".format(it) }
                    }
                }
            } catch (e: Exception) {
                return null
            }
            return found
        }

        private fun parseOrThrow(b: ByteArray): UbisoftInstallState? {
            if (b.isEmpty() || b.size > MAX_BYTES) return null
            var productId = 0L
            var language3: String? = null
            var language26: String? = null
            var field13: String? = null
            var field22: String? = null
            val registry = ArrayList<RegistryEntry>()
            val whole = walk(b, 0, b.size) { field, wire, value, at, len ->
                if (wire == WIRE_VARINT) {
                    if (field == 28) productId = value
                } else if (wire == WIRE_BYTES) {
                    when (field) {
                        3 -> language3 = text(b, at, len)
                        26 -> language26 = text(b, at, len)
                        13 -> field13 = text(b, at, len)
                        22 -> field22 = text(b, at, len)
                        15 -> if (registry.size < MAX_ENTRIES) entry(b, at, len)?.let { registry += it }
                    }
                }
            }
            if (!whole || productId <= 0 || productId > Int.MAX_VALUE) return null
            val name = field13?.takeIf { it.isNotBlank() } ?: field22?.takeIf { it.isNotBlank() } ?: ""
            val displayName = field22?.takeIf { it.isNotBlank() } ?: name
            val language = language3?.takeIf { it.isNotBlank() } ?: language26?.takeIf { it.isNotBlank() } ?: DEFAULT_LANGUAGE
            return UbisoftInstallState(productId.toInt(), name, displayName, language, registry)
        }

        private const val WIRE_VARINT = 0
        private const val WIRE_FIXED64 = 1
        private const val WIRE_BYTES = 2
        private const val WIRE_FIXED32 = 5

        /**
         * Walks the fields of `b[from, to)`, calling [visit] with (field number, wire type, varint value, offset and
         * length of a length-delimited payload). Returns false when the bytes stop being whole protobuf: a truncated
         * varint, a length that runs past [to], a fixed-size field cut short, a group or an unknown wire type.
         */
        private fun walk(
            b: ByteArray, from: Int, to: Int,
            visit: (field: Int, wire: Int, value: Long, at: Int, len: Int) -> Unit,
        ): Boolean {
            var pos = from
            while (pos < to) {
                val tag = varint(b, pos, to) ?: return false
                pos = tag.second
                val field = (tag.first ushr 3).toInt()
                if (field <= 0) return false
                when ((tag.first and 7L).toInt()) {
                    WIRE_VARINT -> {
                        val v = varint(b, pos, to) ?: return false
                        pos = v.second
                        visit(field, WIRE_VARINT, v.first, 0, 0)
                    }
                    WIRE_FIXED64 -> {
                        if (to - pos < 8) return false
                        pos += 8
                    }
                    WIRE_BYTES -> {
                        val l = varint(b, pos, to) ?: return false
                        pos = l.second
                        if (l.first < 0 || l.first > to - pos) return false
                        val len = l.first.toInt()
                        visit(field, WIRE_BYTES, 0, pos, len)
                        pos += len
                    }
                    WIRE_FIXED32 -> {
                        if (to - pos < 4) return false
                        pos += 4
                    }
                    else -> return false
                }
            }
            return true
        }

        /** The varint at [from] (at most 10 bytes, all inside [to]) and the position after it, or null. */
        private fun varint(b: ByteArray, from: Int, to: Int): Pair<Long, Int>? {
            var result = 0L
            var shift = 0
            var pos = from
            while (pos < to && shift < 70) {
                val c = b[pos++].toInt() and 0xFF
                result = result or ((c and 0x7F).toLong() shl shift)
                if (c and 0x80 == 0) return result to pos
                shift += 7
            }
            return null
        }

        /**
         * A string field. The name fields may hold a nested message with the text as its field 1 (the tag byte is a
         * control character) instead of the text itself; both read as the text. Null for anything else.
         */
        private fun text(b: ByteArray, at: Int, len: Int): String? {
            if (len == 0) return ""
            if ((b[at].toInt() and 0xFF) < 0x20) return nestedText(b, at, len)
            return plain(b, at, len)
        }

        private fun plain(b: ByteArray, at: Int, len: Int): String? {
            val s = String(b, at, len, Charsets.UTF_8)
            return if (s.any { it < ' ' }) null else s
        }

        /** The text of field 1 inside the message at `b[at, at + len)`, or null when it is not such a message. */
        private fun nestedText(b: ByteArray, at: Int, len: Int): String? {
            var found: String? = null
            val whole = walk(b, at, at + len) { field, wire, _, innerAt, innerLen ->
                if (field == 1 && wire == WIRE_BYTES && found == null) found = plain(b, innerAt, innerLen)
            }
            return if (whole) found else null
        }

        /** A field-15 entry, or null when it has no path. */
        private fun entry(b: ByteArray, at: Int, len: Int): RegistryEntry? {
            var path: String? = null
            var language = ""
            var value = ""
            val whole = walk(b, at, at + len) { field, wire, _, innerAt, innerLen ->
                if (wire == WIRE_BYTES) {
                    val s = plain(b, innerAt, innerLen)
                    when (field) {
                        1 -> path = s
                        2 -> language = s ?: ""
                        3 -> value = s ?: ""
                    }
                }
            }
            val p = path
            return if (whole && !p.isNullOrBlank()) RegistryEntry(p, language, value) else null
        }
    }
}
