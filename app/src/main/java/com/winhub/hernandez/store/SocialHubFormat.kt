package com.winhub.hernandez.store

/**
 * Pure-JVM formatting for the in-game Social Hub — no Android imports, so it is unit-testable and
 * trivially compilable on its own.
 *
 * A "post" is a GitHub issue on `winhub-emu/social-hub`; a "comment" is an issue comment. Soft
 * delete works by rewriting the body to [DELETE_MARKER]: GitHub has no delete-issue API for normal
 * users (DELETE /issues/{n} 404s, GraphQL deleteIssue is owner/admin only), so the item is kept but
 * marked, and every reader filters on the marker.
 */
object SocialHubFormat {

    /** Body written over a soft-deleted post/comment. Invisible in GitHub's own markdown render. */
    const val DELETE_MARKER = "<!--winhub-deleted-->"

    /** Title written over a soft-deleted post, so the list is legible even straight on GitHub. */
    const val DELETED_TITLE = "[deleted]"

    /** True when a body (post or comment) was soft-deleted. Null-safe for a missing body. */
    fun isDeleted(body: String?): Boolean = body?.contains(DELETE_MARKER) == true

    /** Markdown image: `![alt](url)`, optional quoted title after the URL. */
    private val mdImage = Regex("""!\[[^\]]*]\(\s*(\S+?)(?:\s+["'][^"']*["'])?\s*\)""")

    /** Bare image URL pasted without `![]()` markup. Duplicates of a markdown hit are de-duplicated. */
    private val bareImage =
        Regex("""https?://\S+?\.(?:png|jpe?g|gif|webp|bmp)(?:\?\S*)?""", RegexOption.IGNORE_CASE)

    /**
     * Image URLs embedded in a body, in document order, de-duplicated. Pulls both markdown images
     * and bare links so a pasted catbox URL renders even without the `![]()` wrapper.
     */
    fun imageUrls(body: String?): List<String> {
        val text = body ?: return emptyList()
        val out = LinkedHashSet<String>()
        mdImage.findAll(text).forEach { out.add(it.groupValues[1].trim()) }
        for (m in bareImage.findAll(text)) out.add(m.value)
        return out.toList()
    }

    /** Body with markdown image syntax stripped, for the text-only rendering pass. */
    fun stripImages(body: String?): String =
        body?.replace(mdImage, "")?.replace(bareImage, "")?.trim() ?: ""

    /**
     * Feed-renderable text: delete marker removed, image markup stripped, whitespace collapsed to
     * single blank lines, capped at [maxChars] with an ellipsis.
     */
    fun summary(body: String?, maxChars: Int = 400): String {
        val cleaned = stripImages(body)
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        if (cleaned.length <= maxChars) return cleaned
        return cleaned.substring(0, maxChars).trimEnd() + "…"
    }

    /**
     * Compact relative timestamp for feed rows: "12s", "4m", "3h", "2d", then a fixed date past
     * a week. [nowMs] is passed in so callers can tick it without another system call.
     */
    fun relativeTime(createdAtMs: Long, nowMs: Long): String {
        if (createdAtMs <= 0L) return ""
        val delta = (nowMs - createdAtMs).coerceAtLeast(0L)
        val seconds = delta / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86400 -> "${seconds / 3600}h"
            seconds < 604800 -> "${seconds / 86400}d"
            else -> formatDate(createdAtMs)
        }
    }

    /** "12 Sep" past a week — SimpleDateFormat rather than java.time (minSdk 26, no desugaring). */
    fun formatDate(epochMs: Long): String = try {
        java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US).format(java.util.Date(epochMs))
    } catch (_: Throwable) {
        ""
    }

    /** Marks an issue body as a shared game config; the label alone cannot be trusted (see [CONFIG_LABEL]). */
    const val CONFIG_MARKER = "<!--winhub-config-->"

    /** Issue label the Configs tab filters on. Non-collaborators cannot set labels, hence the marker too. */
    const val CONFIG_LABEL = "Configs"

    /** Body of a config issue: optional note, the marker, then the config JSON in a fenced block. */
    fun buildConfigBody(note: String, configJson: String): String =
        (if (note.isBlank()) "" else note.trim() + "\n\n") + CONFIG_MARKER + "\n```json\n" + configJson + "\n```\n"

    fun isConfig(body: String?): Boolean = body?.contains(CONFIG_MARKER) == true

    /** The config JSON inside a config issue body, or null when the body has none. */
    fun configJson(body: String?): String? {
        val text = body ?: return null
        val marker = text.indexOf(CONFIG_MARKER)
        if (marker < 0) return null
        val open = text.indexOf("```json", marker)
        if (open < 0) return null
        val start = text.indexOf('\n', open)
        if (start < 0) return null
        val end = text.indexOf("```", start)
        if (end < 0) return null
        return text.substring(start + 1, end).trim().ifBlank { null }
    }

    /** The user's note: everything before the marker. */
    fun configNote(body: String?): String {
        val text = body ?: return ""
        val marker = text.indexOf(CONFIG_MARKER)
        return (if (marker < 0) text else text.substring(0, marker)).trim()
    }

    /** "just now" style label for the composer's upload hint. */
    fun uploadLabel(fileName: String): String = fileName.substringAfterLast('/').ifBlank { "image" }
}
