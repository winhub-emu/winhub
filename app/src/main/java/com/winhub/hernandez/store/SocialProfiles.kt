package com.winhub.hernandez.store

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.winhub.hernandez.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Custom Social Hub profiles (display name + picture). GitHub's own name/picture can't be changed with
 * our token, so each player keeps ONE comment on the hidden profiles issue holding a small JSON block.
 * GitHub sets a comment's author, so nobody can write a profile for someone else. Every client reads
 * that issue's comments into [profiles] and prefers them over the GitHub name/picture.
 */
object SocialProfiles {

    private const val TAG = "SocialProfiles"
    private const val ISSUES_URL = "https://api.github.com/repos/winhub-emu/social-hub/issues"
    private const val PROFILES_ISSUE = 15
    private const val PREFS = "social_profiles"
    private const val K_CACHE = "cache"
    private const val COMMENT_MARKER = "<!--winhub-profile-->"
    private const val PAGE = 100
    private const val MAX_PAGES = 10
    private const val AVATAR_HOST = "https://files.catbox.moe/"

    /** Body marker of the profiles issue itself; the feed skips any issue carrying it. */
    const val ISSUE_MARKER = "<!--winhub-profiles-->"
    const val MAX_NAME = 24

    data class Profile(val name: String?, val avatar: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val _profiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    /** Lower-cased GitHub login -> profile. */
    val profiles: StateFlow<Map<String, Profile>> = _profiles

    // Comment ids per login, so Save edits the user's comment instead of adding another.
    private val commentIds = HashMap<String, Long>()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val cached = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_CACHE, null) ?: return
        runCatching {
            val o = JSONObject(cached)
            val map = HashMap<String, Profile>()
            for (k in o.keys()) {
                val p = o.getJSONObject(k)
                map[k] = Profile(p.optString("name").ifBlank { null }, p.optString("avatar").ifBlank { null })
            }
            _profiles.value = map
        }.onFailure { if (BuildConfig.DEBUG) Log.w(TAG, "cache", it) }
    }

    fun refresh() {
        scope.launch {
            val map = HashMap<String, Profile>()
            val ids = HashMap<String, Long>()
            for (page in 1..MAX_PAGES) {
                val url = "$ISSUES_URL/$PROFILES_ISSUE/comments?per_page=$PAGE&page=$page"
                val (code, text) = SocialHubStore.execute(Request.Builder().url(url).headers(SocialHubStore.authHeaders()).get().build())
                if (code !in 200..299) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "refresh HTTP $code")
                    return@launch // keep what we have on a failed fetch
                }
                val array = JSONArray(text)
                for (i in 0 until array.length()) {
                    val c = array.getJSONObject(i)
                    val login = c.optJSONObject("user")?.optString("login").orEmpty().lowercase()
                    val parsed = parse(c.optString("body")) ?: continue
                    if (login.isEmpty()) continue
                    map[login] = parsed
                    ids[login] = c.optLong("id")
                }
                if (array.length() < PAGE) break
            }
            synchronized(commentIds) { commentIds.clear(); commentIds.putAll(ids) }
            _profiles.value = map
            persist(map)
        }
    }

    /** Saves [name] / [avatarUrl] as the signed-in user's profile. Blank values clear back to GitHub's. Returns an error or null. */
    suspend fun save(name: String, avatarUrl: String?): String? = withContext(Dispatchers.IO) {
        val account = SocialHubStore.accountOrNull() ?: return@withContext "Sign in to GitHub first."
        val login = account.login.lowercase()
        val cleanName = sanitizeName(name)
        val cleanAvatar = avatarUrl?.takeIf { it.startsWith(AVATAR_HOST) }.orEmpty()
        val json = JSONObject().put("v", 1).put("name", cleanName).put("avatar", cleanAvatar)
        val body = "$COMMENT_MARKER\nProfile for ${account.login}\n```json\n$json\n```"
        val payload = JSONObject().put("body", body).toString()

        var id = synchronized(commentIds) { commentIds[login] }
        // Not cached yet (first save this session): look it up so Save never creates a duplicate comment.
        if (id == null) id = findMyCommentId(login)
        val (code, text) = if (id != null) {
            SocialHubStore.patchJson("https://api.github.com/repos/winhub-emu/social-hub/issues/comments/$id", payload)
        } else {
            SocialHubStore.postJson("$ISSUES_URL/$PROFILES_ISSUE/comments", payload)
        }
        if (code !in 200..299) return@withContext SocialHubStore.githubError(text, code, "Could not save your profile.")
        runCatching { JSONObject(text).optLong("id") }.getOrNull()?.takeIf { it > 0 }?.let { newId ->
            synchronized(commentIds) { commentIds[login] = newId }
        }
        val next = _profiles.value.toMutableMap()
        next[login] = Profile(cleanName.ifBlank { null }, cleanAvatar.ifBlank { null })
        _profiles.value = next
        persist(next)
        null
    }

    private fun findMyCommentId(login: String): Long? {
        for (page in 1..MAX_PAGES) {
            val url = "$ISSUES_URL/$PROFILES_ISSUE/comments?per_page=$PAGE&page=$page"
            val (code, text) = SocialHubStore.execute(Request.Builder().url(url).headers(SocialHubStore.authHeaders()).get().build())
            if (code !in 200..299) return null
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val c = array.getJSONObject(i)
                if (c.optJSONObject("user")?.optString("login").orEmpty().lowercase() == login && parse(c.optString("body")) != null) {
                    return c.optLong("id")
                }
            }
            if (array.length() < PAGE) break
        }
        return null
    }

    fun sanitizeName(raw: String): String =
        raw.filter { !it.isISOControl() }.replace(Regex("\\s+"), " ").trim().take(MAX_NAME)

    private fun parse(body: String): Profile? {
        if (!body.contains(COMMENT_MARKER)) return null
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val o = JSONObject(body.substring(start, end + 1))
            val name = sanitizeName(o.optString("name")).ifBlank { null }
            val avatar = o.optString("avatar").takeIf { it.startsWith(AVATAR_HOST) }
            Profile(name, avatar)
        }.getOrNull()
    }

    private fun persist(map: Map<String, Profile>) {
        val ctx = appContext ?: return
        val o = JSONObject()
        for ((k, v) in map) o.put(k, JSONObject().put("name", v.name.orEmpty()).put("avatar", v.avatar.orEmpty()))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_CACHE, o.toString()).apply()
    }
}

/** The name to show for [login]: their custom profile name, else [fallback] (the GitHub name). */
@Composable
fun rememberShownName(login: String, fallback: String): String {
    val all by SocialProfiles.profiles.collectAsState()
    return all[login.lowercase()]?.name ?: fallback
}

/** The picture to show for [login]: their custom profile picture, else [fallback] (the GitHub picture). */
@Composable
fun rememberShownAvatar(login: String, fallback: String): String {
    val all by SocialProfiles.profiles.collectAsState()
    return all[login.lowercase()]?.avatar ?: fallback
}
