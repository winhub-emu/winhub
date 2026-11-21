package com.winhub.hernandez.store

import com.winhub.hernandez.core.LogRedactor
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The pure building blocks of the Ubisoft diagnostics report ([UbisoftDiag]): registry section extraction with
 * redaction, folder walks and the before/after diff, text redaction, hashing, tails, `/proc` parsing and the
 * rules that decide which files are copied. No Android classes and no logging here, so all of it is unit tested.
 *
 * Everything that can meet a secret redacts by NAME (a value whose name says password, token, secret, exchange,
 * auth, cookie, session or ticket, or names a person or an account), never by looking at the value; e-mail
 * addresses, GUIDs and the Epic / Ubisoft launch arguments that carry an account are masked by their shape.
 */
object UbisoftDiagScan {
    const val REDACTED = "<redacted>"

    /** A line longer than this is cut before any redaction regex runs, so a hostile or binary-looking line costs bounded time. */
    const val MAX_LINE_CHARS = 8 * 1024

    /** Command lines in the process tables are cut to this many characters (after redaction). */
    private const val CMD_LIMIT = 200

    /** Names whose value is a credential. ONE list: the registry, YAML, key=value and file-name rules are all built from it. */
    private val SECRET_WORDS = listOf("password", "passwd", "token", "secret", "exchange", "auth", "cookie", "session", "ticket")

    /** Names that identify a person or an account (`user` also covers username and userid). Masked in fields and lines, not in registry value names. */
    private val IDENTITY_WORDS = listOf("email", "mail", "user", "account", "profile", "owner", "nickname", "nameOnPlatform", "displayname")

    /** Extra words that make a FILE name look like a credential or identity store. */
    private val FILE_ONLY_WORDS = listOf("secure", "storage", "store", "key", "cert", "pfx", "cache", "ownership", "credential", "login", "wallet")

    private val SECRET_ALT = SECRET_WORDS.joinToString("|")
    private val FIELD_ALT = (SECRET_WORDS + IDENTITY_WORDS).joinToString("|")
    private val SECRET_NAME = Regex(SECRET_ALT, RegexOption.IGNORE_CASE)
    private val SECRET_YAML_KEY = Regex(FIELD_ALT, RegexOption.IGNORE_CASE)

    /** A file name that suggests a credential or identity store: never copied, only listed with its hash. */
    private val SENSITIVE_FILE = Regex((SECRET_WORDS + IDENTITY_WORDS + FILE_ONLY_WORDS).joinToString("|"), RegexOption.IGNORE_CASE)

    /** True when a registry value or setting called [name] must not be shown. */
    @JvmStatic
    fun isSecretName(name: String): Boolean = SECRET_NAME.containsMatchIn(name)

    // ---- Text redaction -------------------------------------------------------------------------

    private val YAML_LINE = Regex("""^(\s*(?:-\s+)?)([^:#\s][^:]*):(\s*)(.*)$""")
    private val AUTH_SCHEME = Regex("""(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val JWT = Regex("""eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*""")
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val GUID = Regex("""\b[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}\b""")

    /** The Epic launch arguments that carry an account (`-epicusername=..`, `-epicovt=..`) and Ubisoft-style `-AUTH_*=..`, quoted or bare. */
    private val LAUNCH_ARG = Regex(
        """(?i)(-(?:epic(?:username|userid|ovt|deploymentid|sandboxid)|auth_\w+))\s*=\s*("[^"]*"|\S+)""",
    )

    /** `name: value` / `name=value` / `"name": "value"` where the name holds a secret or identity word; the value is quoted or a bare run. */
    private val KEY_VALUE = Regex(
        """(?i)\b([\w.-]*(?:$FIELD_ALT)[\w.-]*)(["']?\s*[:=]\s*)("[^"]*"|'[^']*'|[^\s"',;&}]{3,})""",
    )

    /**
     * [text] of a `settings.yaml`: the value of every key whose name matches a secret or identity word (see
     * [SECRET_WORDS], [IDENTITY_WORDS]) is replaced, and so is everything nested under such a key. Layout and comments stay.
     */
    @JvmStatic
    fun redactYaml(text: String): String {
        val out = ArrayList<String>()
        var hiddenIndent = -1
        for (raw in text.split('\n')) {
            val line = capLine(raw)
            val indent = line.length - line.trimStart().length
            if (hiddenIndent >= 0) {
                if (line.isBlank()) { out += line; continue }
                if (indent > hiddenIndent) { out += " ".repeat(indent) + REDACTED; continue }
                hiddenIndent = -1
            }
            val m = YAML_LINE.matchEntire(line.trimEnd('\r'))
            if (m != null && SECRET_YAML_KEY.containsMatchIn(m.groupValues[2])) {
                val value = m.groupValues[4]
                if (value.isBlank() || value.startsWith("#")) {
                    hiddenIndent = indent
                } else {
                    out += m.groupValues[1] + m.groupValues[2] + ":" + m.groupValues[3] + REDACTED
                    continue
                }
            }
            out += line
        }
        return out.joinToString("\n")
    }

    private fun capLine(line: String): String =
        if (line.length > MAX_LINE_CHARS) line.substring(0, MAX_LINE_CHARS) + " [line cut at $MAX_LINE_CHARS chars]" else line

    /** [line] with e-mail addresses replaced; registry values go through this only (a GUID or a path is data there). */
    private fun maskEmails(line: String): String = if (line.indexOf('@') >= 0) EMAIL.replace(line, REDACTED) else line

    /**
     * One line of a log, command line or config file made safe to share: cut at [MAX_LINE_CHARS], then launch
     * arguments, bearer / basic / JWT tokens, e-mail addresses, GUIDs and the values of secret- or identity-named fields
     * replaced, then the app's own scrubbers ([SteamLogRedactor] for the secrets this app registered, [LogRedactor] for
     * the device's own addresses and account names). A line the scrubbers fail on is withheld.
     */
    @JvmStatic
    fun redactLine(line: String): String {
        if (line.length < 8) return line
        return try {
            var s = capLine(line)
            if (s.contains("-epic", ignoreCase = true) || s.contains("-auth_", ignoreCase = true)) {
                s = LAUNCH_ARG.replace(s) { it.groupValues[1] + "=" + REDACTED }
            }
            if (s.contains("eyJ")) s = JWT.replace(s, REDACTED)
            if (s.contains("earer", ignoreCase = true) || s.contains("asic", ignoreCase = true)) s = AUTH_SCHEME.replace(s) { it.groupValues[1] + " " + REDACTED }
            s = maskEmails(s)
            if (s.length >= 36 && s.indexOf('-') >= 0) s = GUID.replace(s, REDACTED)
            s = KEY_VALUE.replace(s) { m ->
                val v = m.groupValues[3]
                val q = v[0]
                val shown = if (v.length >= 2 && (q == '"' || q == '\'') && v.last() == q) "$q$REDACTED$q" else REDACTED
                m.groupValues[1] + m.groupValues[2] + shown
            }
            LogRedactor.redact(SteamLogRedactor.redact(s))
        } catch (t: Throwable) {
            "<redaction failed; line withheld>"
        }
    }

    /** [redactLine] over every line of [text]. */
    @JvmStatic
    fun redactText(text: String): String = text.split('\n').joinToString("\n") { redactLine(it) }

    /** The text of the file called [name] made safe to share: a YAML file by structure first, then every line. */
    @JvmStatic
    fun redactFile(name: String, text: String): String {
        val yaml = name.endsWith(".yaml", ignoreCase = true) || name.endsWith(".yml", ignoreCase = true)
        return redactText(if (yaml) redactYaml(text) else text)
    }

    // ---- Registry sections ----------------------------------------------------------------------

    /** The Ubisoft part of a hive: its [text], the [keys] found (as in the file, single backslashes) and whether it was cut. */
    class RegSections(val text: String, val keys: List<String>, val truncated: Boolean)

    /** A `[key] timestamp` header line's key with the file's doubled backslashes undone, or null for any other line. */
    @JvmStatic
    fun headerKey(line: String): String? {
        if (!line.startsWith("[")) return null
        val end = line.lastIndexOf(']')
        if (end < 1) return null
        return line.substring(1, end).replace("\\\\", "\\")
    }

    /** True for a key path that is Ubisoft's: contains Ubisoft or Uplay (so also `Uninstall\Uplay Install <id>`), any case. */
    @JvmStatic
    fun isUbisoftKey(key: String): Boolean = key.contains("ubisoft", ignoreCase = true) || key.contains("uplay", ignoreCase = true)

    private val REG_VALUE_NAME = Regex("""^"((?:[^"\\]|\\.)*)"=""")

    /**
     * Streams a `system.reg` / `user.reg` and returns every section whose key path is Ubisoft's ([isUbisoftKey]) with
     * its header (timestamp kept), `#time=` line and all values; values whose name is secret-looking are replaced
     * ([isSecretName]). Stops reading at [maxChars] of output. Nothing else is kept in memory.
     */
    @JvmStatic
    fun registrySections(reader: BufferedReader, maxChars: Int): RegSections {
        val out = StringBuilder()
        val keys = ArrayList<String>()
        var inside = false
        var hideContinuation = false
        var truncated = false
        while (true) {
            val line = reader.readLine() ?: break
            val key = headerKey(line)
            if (key != null) {
                inside = isUbisoftKey(key)
                hideContinuation = false
                if (inside) {
                    if (out.length >= maxChars) { truncated = true; break }
                    if (out.isNotEmpty()) out.append('\n')
                    keys += key
                    out.append(line).append('\n')
                }
                continue
            }
            if (!inside) continue
            if (hideContinuation) {
                hideContinuation = line.endsWith("\\")
                continue
            }
            val m = REG_VALUE_NAME.find(line)
            if (m != null && isSecretName(m.groupValues[1])) {
                out.append(line, 0, m.range.last + 1).append(REDACTED).append('\n')
                hideContinuation = line.endsWith("\\")
            } else if (line.isNotEmpty()) {
                out.append(maskEmails(line)).append('\n')
            }
        }
        return RegSections(out.toString(), keys, truncated)
    }

    /** True when [keys] (from [registrySections]) has [key] (single backslashes), ignoring case. */
    @JvmStatic
    fun hasKey(keys: List<String>, key: String): Boolean = keys.any { it.equals(key, ignoreCase = true) }

    // ---- Folder walks ---------------------------------------------------------------------------

    /** One file, folder or link. [kind] is `d`, `f` or `l`; [perms] like `rwxr-xr-x` or `?`; [target] for a link. */
    data class Entry(val size: Long, val mtime: Long, val kind: Char, val perms: String, val target: String?)

    /** How a walk went. */
    class WalkResult {
        var entries = 0
        var omitted = 0
        var depthLimited = 0
        var errors = 0
        var timedOut = false
    }

    /** The one timestamp format of the report, for writing (UTC) and for reading a listing back. */
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

    /** [ms] since the epoch as `yyyy-MM-dd HH:mm:ss` (UTC). */
    @JvmStatic
    fun fmtTime(ms: Long): String = try { TIME_FORMAT.format(Instant.ofEpochMilli(ms)) } catch (e: Exception) { "?" }

    /** [entry] at [path] as one report line: kind, permissions, size, UTC mtime, path (and a link's target). */
    @JvmStatic
    fun formatEntry(path: String, entry: Entry): String {
        val sb = StringBuilder(path.length + 48)
        sb.append(entry.kind).append(' ').append(entry.perms.padEnd(9)).append(' ')
            .append(if (entry.kind == 'd') "         -" else entry.size.toString().padStart(10)).append(' ')
            .append(fmtTime(entry.mtime)).append(' ').append(path)
        if (entry.target != null) sb.append(" -> ").append(entry.target)
        return sb.toString()
    }

    /** A [formatEntry] line read back as (path, entry), or null when it is not one (a `#` header, a blank line). Times are whole seconds. */
    @JvmStatic
    fun parseEntryLine(line: String): Pair<String, Entry>? {
        // kind(1) space perms(9) space size(10) space time(19) space path
        if (line.length < 44 || line[1] != ' ' || line[11] != ' ' || line[22] != ' ' || line[42] != ' ') return null
        val kind = line[0]
        if (kind != 'd' && kind != 'f' && kind != 'l') return null
        val size = if (kind == 'd') 0L else line.substring(12, 22).trim().toLongOrNull() ?: return null
        val mtime = try {
            LocalDateTime.parse(line.substring(23, 42), TIME_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            return null
        }
        var path = line.substring(43)
        var target: String? = null
        if (kind == 'l') {
            val arrow = path.indexOf(" -> ")
            if (arrow >= 0) { target = path.substring(arrow + 4); path = path.substring(0, arrow) }
        }
        return path to Entry(size, mtime, kind, line.substring(2, 11).trim(), target)
    }

    @Volatile
    private var posixWorks = true

    /** The entry for [f], read with one `stat` (not following a link). Null when it vanished or cannot be read. */
    @JvmStatic
    fun entryOf(f: File): Entry? {
        return try {
            val p = f.toPath()
            if (posixWorks) {
                try {
                    val a = Files.readAttributes(p, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    return build(p, a, PosixFilePermissions.toString(a.permissions()))
                } catch (e: UnsupportedOperationException) {
                    posixWorks = false
                }
            }
            val a = Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            build(p, a, "?")
        } catch (e: Exception) {
            null
        }
    }

    private fun build(p: java.nio.file.Path, a: BasicFileAttributes, perms: String): Entry {
        val kind = if (a.isSymbolicLink) 'l' else if (a.isDirectory) 'd' else 'f'
        val target = if (kind == 'l') try { Files.readSymbolicLink(p).toString() } catch (e: Exception) { "?" } else null
        return Entry(if (kind == 'f') a.size() else 0L, a.lastModifiedTime().toMillis(), kind, perms, target)
    }

    /**
     * Walks [root] depth first, children sorted by name, calling [visit] with (path relative to [root] using `/`,
     * entry). Links are listed but never followed. At most [maxEntries] entries are reported (the rest counted in
     * [WalkResult.omitted]), folders below [maxDepth] levels are listed but not entered ([WalkResult.depthLimited]),
     * and the walk stops at [deadlineMs] (wall clock). A missing [root] reports nothing.
     */
    @JvmStatic
    fun walk(root: File, maxDepth: Int, maxEntries: Int, deadlineMs: Long, visit: (String, Entry) -> Unit): WalkResult {
        val result = WalkResult()
        if (!root.isDirectory) return result
        walkDir(root, "", 0, maxDepth, maxEntries, deadlineMs, result, visit)
        return result
    }

    private fun walkDir(
        dir: File, rel: String, depth: Int, maxDepth: Int, maxEntries: Int, deadlineMs: Long,
        result: WalkResult, visit: (String, Entry) -> Unit,
    ) {
        val kids = dir.listFiles()
        if (kids == null) { result.errors++; return }
        kids.sortBy { it.name }
        for (f in kids) {
            if (result.timedOut) return
            if (System.currentTimeMillis() > deadlineMs) { result.timedOut = true; return }
            if (result.entries >= maxEntries) { result.omitted++; continue }
            val e = entryOf(f)
            if (e == null) { result.errors++; continue }
            val path = if (rel.isEmpty()) f.name else rel + "/" + f.name
            result.entries++
            visit(path, e)
            if (e.kind == 'd') {
                if (depth + 1 < maxDepth) walkDir(f, path, depth + 1, maxDepth, maxEntries, deadlineMs, result, visit)
                else result.depthLimited++
            }
        }
    }

    /** `walk` summary as a line for the report. */
    @JvmStatic
    fun describeWalk(r: WalkResult): String =
        "${r.entries} entries" +
            (if (r.omitted > 0) ", at least ${r.omitted} more omitted (entry cap)" else "") +
            (if (r.depthLimited > 0) ", ${r.depthLimited} folder(s) not entered (depth cap)" else "") +
            (if (r.errors > 0) ", ${r.errors} unreadable" else "") +
            (if (r.timedOut) ", STOPPED by the time limit" else "")

    // ---- Before / after -------------------------------------------------------------------------

    /**
     * Compares a walk made later (fed through [onNow]) with the earlier one given as [before]. Memory is the earlier
     * map only. [finish] turns what was not seen again into "removed". Lines are capped at [maxLines] per kind; the
     * counts are always complete.
     */
    class Comparison(before: Map<String, Entry>, private val maxLines: Int = 5000) {
        private val left = HashMap(before)
        val added = ArrayList<String>()
        val changed = ArrayList<String>()
        val removed = ArrayList<String>()
        var addedCount = 0
        var changedCount = 0
        var removedCount = 0
        var unchangedCount = 0

        /** Feeds one entry of the later walk. */
        fun onNow(path: String, now: Entry) {
            val was = left.remove(path)
            if (was == null) {
                addedCount++
                if (added.size < maxLines) added += formatEntry(path, now)
            } else if (was.kind != now.kind || was.size != now.size || was.mtime / 1000 != now.mtime / 1000) {
                changedCount++
                if (changed.size < maxLines) {
                    changed += "${formatEntry(path, now)}   (was ${if (was.kind == 'd') "folder" else was.size.toString() + " bytes"}, ${fmtTime(was.mtime)})"
                }
            } else {
                unchangedCount++
            }
        }

        /** Call once the later walk is over; [complete] false (it was cut short) skips the "removed" list, which would be wrong. */
        fun finish(complete: Boolean) {
            if (!complete) return
            for (path in left.keys.sorted()) {
                removedCount++
                if (removed.size < maxLines) removed += formatEntry(path, left.getValue(path))
            }
            left.clear()
        }

        /** The three lists as report text, [label] naming what was compared. */
        fun render(label: String, complete: Boolean): String {
            val sb = StringBuilder()
            sb.append("diff $label: added=$addedCount changed=$changedCount removed=${if (complete) removedCount.toString() else "unknown (later walk incomplete)"} unchanged=$unchangedCount\n")
            fun block(title: String, lines: List<String>, total: Int) {
                if (total == 0) return
                sb.append("--- ").append(title).append(" (").append(total).append(")\n")
                for (l in lines) sb.append(l).append('\n')
                if (total > lines.size) sb.append("... ").append(total - lines.size).append(" more not listed\n")
            }
            block("added", added, addedCount)
            block("changed (size or modified time)", changed, changedCount)
            if (complete) block("removed", removed, removedCount)
            return sb.toString()
        }
    }

    // ---- Files ----------------------------------------------------------------------------------

    /** The SHA-256 of [f] as lowercase hex, or a short reason when it is over [maxBytes] or unreadable. */
    @JvmStatic
    fun sha256(f: File, maxBytes: Long): String {
        return try {
            if (f.length() > maxBytes) return "(not hashed: over $maxBytes bytes)"
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "(unreadable: ${e.javaClass.simpleName})"
        }
    }

    /** What a tail read found: [text] (a partial first line dropped when [cut]), the file's [size], and whether it looked binary. */
    class Tail(val text: String, val size: Long, val cut: Boolean, val binary: Boolean)

    /** The last [maxBytes] of [f] as text. Never throws; null when [f] cannot be read. */
    @JvmStatic
    fun readTail(f: File, maxBytes: Int): Tail? {
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val len = raf.length()
                val start = maxOf(0L, len - maxBytes)
                val buf = ByteArray((len - start).toInt())
                raf.seek(start)
                raf.readFully(buf)
                if (looksBinary(buf)) return Tail("", len, start > 0, true)
                var text = String(buf, Charsets.UTF_8)
                if (start > 0) {
                    val nl = text.indexOf('\n')
                    if (nl >= 0) text = text.substring(nl + 1)
                }
                Tail(text, len, start > 0, false)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** True when [b] has NUL bytes or mostly control characters in its first 4 KB (so it is not text). */
    @JvmStatic
    fun looksBinary(b: ByteArray): Boolean {
        val n = minOf(b.size, 4096)
        if (n == 0) return false
        var control = 0
        for (i in 0 until n) {
            val c = b[i].toInt() and 0xFF
            if (c == 0) return true
            if (c < 9 || (c in 14..31)) control++
        }
        return control * 10 > n
    }

    /** What to do with a file found under Ubisoft's folders: only an allow-listed text file is ever copied, always redacted. */
    enum class CopyAction { TEXT_REDACTED, HASH_OTHER, HASH_SENSITIVE, HASH_LARGE }

    /** The only extensions copied (as redacted text). Anything else, including no extension, `.dat`, `.db`, `.sqlite`, `.bin` and `.pak`, is hash only. */
    private val TEXT_EXT = setOf("txt", "log", "yaml", "yml", "ini", "cfg", "conf", "json", "xml", "manifest", "html", "md")

    /** Files at or over this size are listed with a hash only. */
    const val MAX_COPY_BYTES = 2L * 1024 * 1024

    /** `settings.yaml` is read at most this far; Connect's real one is a few KB. */
    const val YAML_READ_BYTES = 1_000_000

    /**
     * The rule for [relPath] of [size] bytes: a small allow-listed text file whose NAME does not suggest a credential
     * or identity store is copied with secrets redacted; every other file is listed with its hash only.
     */
    @JvmStatic
    fun copyAction(relPath: String, size: Long): CopyAction {
        if (size >= MAX_COPY_BYTES) return CopyAction.HASH_LARGE
        val name = relPath.substringAfterLast('/')
        if (SENSITIVE_FILE.containsMatchIn(name)) return CopyAction.HASH_SENSITIVE
        return if (name.substringAfterLast('.', "").lowercase() in TEXT_EXT) CopyAction.TEXT_REDACTED else CopyAction.HASH_OTHER
    }

    /** Up to [maxBytes] of [input] as UTF-8 text (reads until the limit or the end). */
    @JvmStatic
    fun readUpTo(input: InputStream, maxBytes: Int): String {
        val buf = ByteArray(maxBytes)
        var n = 0
        while (n < maxBytes) {
            val r = input.read(buf, n, maxBytes - n)
            if (r < 0) break
            n += r
        }
        return String(buf, 0, n, Charsets.UTF_8)
    }

    // ---- Logcat ---------------------------------------------------------------------------------

    private val FEATURE_WORDS = Regex("Ubisoft|Uplay|upc", RegexOption.IGNORE_CASE)

    /** True when a `logcat -v time` [line] is from one of [tags] (`I/Tag(  123): ...`) or mentions Ubisoft, Uplay or upc. */
    @JvmStatic
    fun logcatLineWanted(line: String, tags: Collection<String>): Boolean {
        for (t in tags) if (line.contains("/$t(") || line.contains("/$t :") || line.contains("/$t:")) return true
        return FEATURE_WORDS.containsMatchIn(line)
    }

    // ---- /proc ----------------------------------------------------------------------------------

    /** The fields of a `/proc/<pid>/stat` line that matter for spotting a hang. */
    data class ProcStat(
        val pid: Int, val comm: String, val state: Char, val ppid: Int,
        val utime: Long, val stime: Long, val threads: Int, val vsizeBytes: Long, val rssPages: Long,
    )

    /** [line] parsed ("pid (comm) S ppid ..."; comm may hold spaces and brackets), or null when it is not a stat line. */
    @JvmStatic
    fun parseProcStat(line: String): ProcStat? {
        val open = line.indexOf('(')
        val close = line.lastIndexOf(')')
        if (open < 1 || close < open) return null
        val pid = line.substring(0, open).trim().toIntOrNull() ?: return null
        val f = line.substring(close + 1).trim().split(' ')
        if (f.size < 22) return null
        return try {
            ProcStat(
                pid, line.substring(open + 1, close), f[0][0], f[1].toInt(),
                f[11].toLong(), f[12].toLong(), f[17].toInt(), f[20].toLong(), f[21].toLong(),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** One line for a process table: pid, ppid, state, threads, cpu ticks, rss, name and command line. */
    @JvmStatic
    fun formatProc(s: ProcStat, cmdline: String): String =
        "pid=${s.pid} ppid=${s.ppid} state=${s.state} threads=${s.threads} utime=${s.utime} stime=${s.stime} " +
            "rss=${s.rssPages * 4}kB vsize=${s.vsizeBytes / 1024}kB name=${s.comm} cmd=${redactLine(cmdline).take(CMD_LIMIT)}"

    private val STATUS_KEYS = setOf("State", "Tgid", "PPid", "Threads", "VmSize", "VmRSS", "VmSwap", "voluntary_ctxt_switches", "nonvoluntary_ctxt_switches", "SigBlk", "SigCgt")
    private val WHITESPACE = Regex("\\s+")

    /** The selected lines of a `/proc/<pid>/status` text worth keeping in every sample. */
    @JvmStatic
    fun statusSummary(status: String): String =
        status.lineSequence().filter { l -> l.substringBefore(':') in STATUS_KEYS }
            .joinToString("; ") { l -> l.substringBefore(':') + "=" + l.substringAfter(':').trim().replace(WHITESPACE, " ") }

    // ---- Size limits ----------------------------------------------------------------------------

    /** [text] cut to at most [maxBytes] of UTF-8 (on a character boundary) and whether it was cut. */
    @JvmStatic
    fun capUtf8(text: String, maxBytes: Int): Pair<String, Boolean> {
        if (maxBytes <= 0) return "" to text.isNotEmpty()
        if (text.length * 3 <= maxBytes) return text to false
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text to false
        var end = maxBytes
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return String(bytes, 0, end, Charsets.UTF_8) to true
    }
}
