package com.winhub.hernandez.store

import android.content.Context
import android.util.Log
import com.winhub.hernandez.BuildConfig
import com.winhub.hernandez.store.GitHubAuthClient.Poll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * In-game Social Hub: a GitHub-Issues-backed feed on `winhub-emu/social-hub`.
 *
 * MAPPING
 *  post    = an issue           (title + body; images ride along as markdown URLs in the body)
 *  comment = an issue comment
 *  delete  = soft (body rewritten to [SocialHubFormat.DELETE_MARKER] + issue closed + title
 *            marked) because GitHub exposes no delete-issue API for non-owners
 *  images  = anonymous upload to catbox.moe, embedded as public `https://files.catbox.moe/…`
 *            markdown links (user decision: images public for everybody; the repo Contents API
 *            needs push access and the Gist API cannot store binary, so neither hosts images)
 *
 * Network is OkHttp + blocking calls (same shape as [SteamUserSearch]) on a private IO scope.
 * Nothing here touches Android views — only [init] needs a Context, for the credential file.
 */
object SocialHubStore {

    private const val TAG = "SocialHubStore"
    private const val OWNER = "winhub-emu"
    private const val REPO = "social-hub"
    private const val API = "https://api.github.com"
    private const val USER_AGENT = "WinHub-SocialHub"
    private const val CATBOX_UPLOAD = "https://catbox.moe/user/api.php"

    private const val ISSUES_URL = "$API/repos/$OWNER/$REPO/issues"
    private const val PER_PAGE = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Models ──────────────────────────────────────────────────────────────────────────────────

    data class Post(
        val number: Int,
        val title: String,
        val body: String,
        val author: String,
        val authorAvatar: String,
        val createdAtMs: Long,
        val commentCount: Int,
        /** Author identity drives the delete affordance; "" when unknown. */
        val authorLogin: String,
    ) {
        val isMine: Boolean get() = authorLogin.isNotEmpty() && authorLogin == SocialHubStore.accountOrNull()?.login
        val images: List<String> get() = SocialHubFormat.imageUrls(body)
        val text: String get() = SocialHubFormat.summary(body)
        val isDeleted: Boolean get() = SocialHubFormat.isDeleted(body)
    }

    data class Comment(
        val id: Long,
        val body: String,
        val author: String,
        val authorAvatar: String,
        val createdAtMs: Long,
        val authorLogin: String,
    ) {
        val images: List<String> get() = SocialHubFormat.imageUrls(body)
        val text: String get() = SocialHubFormat.stripImages(body).trim()
        val isDeleted: Boolean get() = SocialHubFormat.isDeleted(body)
    }

    /** Where the sign-in ceremony is, for the welcome / device-code screens. */
    sealed class LoginState {
        object Idle : LoginState()
        /** Device code requested, GitHub has not answered yet. */
        object Requesting : LoginState()
        /** Code issued; [status] is the live poll message shown under it. */
        data class AwaitingUser(
            val userCode: String,
            val verificationUri: String,
            val status: String,
        ) : LoginState()

        data class Failed(val message: String) : LoginState()
    }

    // ── Exposed state ───────────────────────────────────────────────────────────────────────────

    private val _account = MutableStateFlow<GitHubAuthClient.Account?>(null)
    val account: StateFlow<GitHubAuthClient.Account?> = _account

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _configs = MutableStateFlow<List<Post>>(emptyList())
    /** Config issues (label "Configs" or the body marker), newest first. Kept out of [posts]. */
    val configs: StateFlow<List<Post>> = _configs

    private val _configsLoading = MutableStateFlow(false)
    val configsLoading: StateFlow<Boolean> = _configsLoading

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Issue number currently opened in the thread pane; null = feed. */
    private val _openPost = MutableStateFlow<Int?>(null)
    val openPost: StateFlow<Int?> = _openPost

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading

    private var loginJob: Job? = null

    fun accountOrNull(): GitHubAuthClient.Account? = _account.value

    // ── Lifecycle ───────────────────────────────────────────────────────────────────────────────

    /**
     * Call once per session (idempotent). Restores a saved token and, if it still validates,
     * refreshes the feed. Safe to call from a composable's LaunchedEffect.
     */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val saved = GitHubAuthClient.load(appContext ?: return) ?: return
        _account.value = saved
        scope.launch {
            // Validate the token before trusting it; a revoked token just returns null.
            val live = GitHubAuthClient.fetchUser(saved.accessToken)
            if (live == null) {
                _account.value = null
                GitHubAuthClient.clear(appContext ?: return@launch)
            } else {
                _account.value = live
            }
            refresh()
        }
    }

    /**
     * Feed reload. No-op while logged out (nothing to show — the repo is for players).
     *
     * The fetch result is MERGED rather than swapped: GitHub's list endpoint can lag a second or
     * two behind a write, and dropping an item the user just published is what made the feed look
     * like it "didn't refresh". Anything the server has not answered for yet is kept and re-sorted
     * by creation time, so ordering stays newest-first.
     *
     * A failed fetch also keeps what is already on screen (a blip should not blank the feed), and
     * a thread that is currently open has its replies re-read too — the header refresh button is a
     * full-screen refresh, not a feed-only one.
     */
    fun refresh() {
        if (_account.value == null) return
        scope.launch {
            _loading.value = true
            val result = fetchPosts()
            result.fold(
                onSuccess = { fresh ->
                    val unseen = _posts.value.filter { local ->
                        !local.isDeleted && fresh.none { it.number == local.number }
                    }
                    _posts.value = (fresh + unseen).sortedByDescending { it.createdAtMs }
                },
                onFailure = { t ->
                    if (BuildConfig.DEBUG) Log.w(TAG, "refresh", t)
                    _error.value = t.message ?: "Could not load the feed."
                },
            )
            _loading.value = false
            _openPost.value?.let { loadComments(it) }
        }
    }

    /** Configs tab reload. Reads work signed-out (rate-limited); a failed fetch keeps the list on screen. */
    fun refreshConfigs() {
        scope.launch {
            _configsLoading.value = true
            fetchConfigs().fold(
                onSuccess = { _configs.value = it },
                onFailure = { t ->
                    if (BuildConfig.DEBUG) Log.w(TAG, "refreshConfigs", t)
                    _error.value = t.message ?: "Could not load the configs."
                },
            )
            _configsLoading.value = false
        }
    }

    fun dismissError() { _error.value = null }

    // ── Device-flow sign-in ─────────────────────────────────────────────────────────────────────

    /**
     * Kick off device flow: request the code, then poll until the user approves (or the code
     * expires). Cancellable with [cancelLogin]; re-entrant (a retry just restarts the job).
     */
    fun beginLogin() {
        if (loginJob?.isActive == true) return
        _loginState.value = LoginState.Requesting
        loginJob = scope.launch {
            val code = GitHubAuthClient.requestDeviceCode()
            if (code == null) {
                _loginState.value =
                    LoginState.Failed(GitHubAuthClient.lastError ?: "Could not start sign-in.")
                return@launch
            }
            _loginState.value = LoginState.AwaitingUser(
                userCode = code.userCode,
                verificationUri = code.verificationUri,
                status = "Waiting for you to approve in GitHub…",
            )
            val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
            var intervalMs = code.intervalSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(intervalMs)
                when (val poll = GitHubAuthClient.pollForToken(code.deviceCode)) {
                    is Poll.Done -> {
                        _loginState.value = LoginState.AwaitingUser(
                            userCode = code.userCode,
                            verificationUri = code.verificationUri,
                            status = "Signed in — loading your profile…",
                        )
                        val user = GitHubAuthClient.fetchUser(poll.accessToken)
                        if (user == null) {
                            _loginState.value =
                                LoginState.Failed("GitHub accepted the code but rejected the token. Try again.")
                            return@launch
                        }
                        val ctx = appContext
                        if (ctx != null) GitHubAuthClient.save(ctx, user)
                        _account.value = user
                        _loginState.value = LoginState.Idle
                        refresh()
                        return@launch
                    }
                    Poll.Pending -> setLoginStatus("Waiting for you to approve in GitHub…")
                    Poll.SlowDown -> {
                        intervalMs += 5_000L
                        setLoginStatus("Still waiting — GitHub asked us to poll slower.")
                    }
                    Poll.Denied -> {
                        _loginState.value = LoginState.Failed("Sign-in was cancelled in GitHub.")
                        return@launch
                    }
                    Poll.Expired -> {
                        _loginState.value = LoginState.Failed("The code expired. Start again.")
                        return@launch
                    }
                    is Poll.Failed -> {
                        _loginState.value = LoginState.Failed(poll.reason)
                        return@launch
                    }
                }
            }
            _loginState.value = LoginState.Failed("The code expired. Start again.")
        }
    }

    private fun setLoginStatus(status: String) {
        val current = _loginState.value
        if (current is LoginState.AwaitingUser && current.status != status) {
            _loginState.value = current.copy(status = status)
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        _loginState.value = LoginState.Idle
    }

    fun signOut() {
        cancelLogin()
        appContext?.let { GitHubAuthClient.clear(it) }
        _account.value = null
        _posts.value = emptyList()
        _comments.value = emptyList()
        _openPost.value = null
        _error.value = null
    }

    // ── Thread pane ─────────────────────────────────────────────────────────────────────────────

    fun openPost(number: Int?) {
        _openPost.value = number
        _comments.value = emptyList()
        if (number != null) loadComments(number)
    }

    private fun loadComments(number: Int) {
        if (_account.value == null) return
        scope.launch {
            _commentsLoading.value = true
            val result = fetchComments(number)
            _comments.value = result.getOrElse { emptyList() }
            result.exceptionOrNull()?.let { _error.value = it.message ?: "Could not load replies." }
            _commentsLoading.value = false
        }
    }

    // ── Mutations (suspend: the UI awaits them to clear its composer) ───────────────────────────

    /**
     * Create a post. Returns null on success, or a user-facing error string.
     *
     * GitHub returns the created issue in the response body, so it is inserted into the feed
     * immediately instead of waiting on a refetch — the post is visible the moment the dialog
     * closes, with no tab switch needed.
     */
    suspend fun createPost(title: String, body: String): String? = withContext(Dispatchers.IO) {
        mutate("Could not publish.") {
            val json = JSONObject().put("title", title).put("body", body).toString()
            val (code, text) = postJson(ISSUES_URL, json)
            if (code in 200..299) {
                insertPost(text)
                null
            } else githubError(text, code, "Could not publish.")
        }
    }

    /**
     * Publish a config as an issue labelled [SocialHubFormat.CONFIG_LABEL]. The label is best-effort
     * (GitHub drops it for non-collaborators); the body marker is what the Configs tab relies on.
     * Returns null on success, or a user-facing error string.
     */
    suspend fun createConfig(title: String, note: String, configJson: String): String? = withContext(Dispatchers.IO) {
        mutate("Could not publish the config.") {
            val json = JSONObject()
                .put("title", title)
                .put("body", SocialHubFormat.buildConfigBody(note, configJson))
                .put("labels", JSONArray().put(SocialHubFormat.CONFIG_LABEL))
                .toString()
            val (code, text) = postJson(ISSUES_URL, json)
            if (code in 200..299) {
                try {
                    val post = postFromJson(JSONObject(text), System.currentTimeMillis())
                    if (_configs.value.none { it.number == post.number }) _configs.value = listOf(post) + _configs.value
                } catch (t: Throwable) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "createConfig insert", t)
                }
                null
            } else githubError(text, code, "Could not publish the config.")
        }
    }

    /**
     * Reply to the open post. Returns null on success, or a user-facing error string.
     * Same optimistic rule as [createPost]: the created comment is appended locally.
     */
    suspend fun addComment(postNumber: Int, body: String): String? = withContext(Dispatchers.IO) {
        mutate("Could not send your reply.") {
            val json = JSONObject().put("body", body).toString()
            val (code, text) = postJson("$ISSUES_URL/$postNumber/comments", json)
            if (code in 200..299) {
                insertComment(text)
                null
            } else githubError(text, code, "Could not send your reply.")
        }
    }

    /**
     * Soft-delete a post: marker body + closed state + `[deleted]` title. The marker is the
     * authority (the feed filters on it); the state/title changes just make GitHub's own UI honest.
     * Returns null on success, or a user-facing error string.
     */
    suspend fun deletePost(postNumber: Int): String? = withContext(Dispatchers.IO) {
        mutate("Could not delete the post.") {
            val json = JSONObject()
                .put("body", SocialHubFormat.DELETE_MARKER)
                .put("title", SocialHubFormat.DELETED_TITLE)
                .put("state", "closed")
                .toString()
            val (code, text) = patchJson("$ISSUES_URL/$postNumber", json)
            if (code in 200..299) {
                _posts.value = _posts.value.filterNot { it.number == postNumber }
                _configs.value = _configs.value.filterNot { it.number == postNumber }
                null
            } else githubError(text, code, "Could not delete the post.")
        }
    }

    /**
     * Delete a reply. Tries the real DELETE first; if GitHub refuses (only the comment author can,
     * and the token may lack the write scope) it falls back to the same soft-delete marker, which
     * the feed already filters. Returns null on success, or a user-facing error string.
     */
    suspend fun deleteComment(commentId: Long): String? = withContext(Dispatchers.IO) {
        mutate("Could not delete the reply.") {
            val url = "$API/repos/$OWNER/$REPO/issues/comments/$commentId"
            val del = Request.Builder().url(url).headers(authHeaders()).delete().build()
            val (code, text) = execute(del)
            if (code in 200..299) {
                removeComment(commentId)
                null
            } else if (code == 404 || code == 403 || code == 405) {
                val fallback = JSONObject().put("body", SocialHubFormat.DELETE_MARKER).toString()
                val (code2, text2) = patchJson(url, fallback)
                if (code2 in 200..299) {
                    removeComment(commentId)
                    null
                } else githubError(text2, code2, "Could not delete the reply.")
            } else {
                githubError(text, code, "Could not delete the reply.")
            }
        }
    }

    /**
     * Anonymous catbox.moe upload → permanent public URL, or null. Blocking; the caller is already
     * on Dispatchers.IO. Binary-safe (the Gist API is not), no account, no API key.
     */
    suspend fun uploadImage(bytes: ByteArray, fileName: String): String? =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext null
            val mime = when (fileName.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "image/png"
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("reqtype", "fileupload")
                .addFormDataPart("fileToUpload", fileName.ifBlank { "image.png" }, bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .build()
            try {
                val request = Request.Builder()
                    .url(CATBOX_UPLOAD)
                    .header("User-Agent", USER_AGENT)
                    .post(body)
                    .build()
                val (code, text) = execute(request)
                val url = text.trim()
                if (code in 200..299 && url.startsWith("http")) {
                    url
                } else {
                    if (BuildConfig.DEBUG) Log.w(TAG, "catbox HTTP $code: $url")
                    _error.value = "Image upload failed (HTTP $code)."
                    null
                }
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) Log.w(TAG, "uploadImage", t)
                _error.value = "Could not reach the image host."
                null
            }
        }

    // ── GitHub reads ────────────────────────────────────────────────────────────────────────────

    /** Blocking fetch of open issues, newest first. PRs and soft-deleted items are dropped. */
    private fun fetchPosts(): Result<List<Post>> = runCatching {
        val token = _account.value?.accessToken
        val url = "$ISSUES_URL?state=all&per_page=$PER_PAGE&sort=created&direction=desc"
        val request = Request.Builder().url(url).headers(authHeaders(token)).get().build()
        val (code, text) = execute(request)
        if (code !in 200..299) throw IllegalStateException(githubError(text, code, "Could not load the feed."))
        val array = JSONArray(text)
        val now = System.currentTimeMillis()
        val out = ArrayList<Post>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (o.has("pull_request")) continue // the API mixes PRs into /issues
            if (SocialHubFormat.isDeleted(o.optString("body"))) continue
            if (isConfigIssue(o)) continue // shown in the Configs tab, not the feed
            out.add(postFromJson(o, now))
        }
        out
    }

    private fun isConfigIssue(o: JSONObject): Boolean {
        if (SocialHubFormat.isConfig(o.optString("body"))) return true
        val labels = o.optJSONArray("labels") ?: return false
        for (i in 0 until labels.length()) {
            if (labels.optJSONObject(i)?.optString("name").equals(SocialHubFormat.CONFIG_LABEL, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * Config issues = everything labelled "Configs" plus any recent issue carrying the body marker
     * (uploaders without triage rights cannot label). Entries without a parseable config block are dropped.
     */
    private fun fetchConfigs(): Result<List<Post>> = runCatching {
        val token = _account.value?.accessToken
        val now = System.currentTimeMillis()
        val byNumber = LinkedHashMap<Int, Post>()
        val labelled = "$ISSUES_URL?state=all&labels=${SocialHubFormat.CONFIG_LABEL}&per_page=$PER_PAGE&sort=created&direction=desc"
        val recent = "$ISSUES_URL?state=all&per_page=$PER_PAGE&sort=created&direction=desc"
        for ((i, url) in listOf(labelled, recent).withIndex()) {
            val (code, text) = execute(Request.Builder().url(url).headers(authHeaders(token)).get().build())
            if (code !in 200..299) {
                // The labelled query is the primary one; the marker sweep is a bonus and may fail alone.
                if (i == 0) throw IllegalStateException(githubError(text, code, "Could not load the configs."))
                continue
            }
            val array = JSONArray(text)
            for (j in 0 until array.length()) {
                val o = array.getJSONObject(j)
                if (o.has("pull_request")) continue
                val body = o.optString("body")
                if (SocialHubFormat.isDeleted(body) || SocialHubFormat.configJson(body) == null) continue
                if (!isConfigIssue(o)) continue
                val post = postFromJson(o, now)
                byNumber.putIfAbsent(post.number, post)
            }
        }
        byNumber.values.sortedByDescending { it.createdAtMs }
    }

    private fun fetchComments(postNumber: Int): Result<List<Comment>> = runCatching {
        val url = "$ISSUES_URL/$postNumber/comments?per_page=$PER_PAGE"
        val request = Request.Builder().url(url).headers(authHeaders()).get().build()
        val (code, text) = execute(request)
        if (code !in 200..299) throw IllegalStateException(githubError(text, code, "Could not load replies."))
        val array = JSONArray(text)
        val now = System.currentTimeMillis()
        val out = ArrayList<Comment>(array.length())
        for (i in 0 until array.length()) {
            out.add(commentFromJson(array.getJSONObject(i), now))
        }
        out
    }

    // ── Local reconciliation ────────────────────────────────────────────────────────────────────

    /** One issue JSON object → [Post]. Shared by the list fetch and the create response. */
    private fun postFromJson(o: JSONObject, now: Long): Post {
        val user = o.optJSONObject("user")
        val login = user?.optString("login") ?: ""
        return Post(
            number = o.optInt("number"),
            title = o.optString("title").ifBlank { "(untitled)" },
            body = o.optString("body"),
            author = login.ifBlank { "unknown" },
            authorAvatar = user?.optString("avatar_url") ?: "",
            createdAtMs = parseGitHubTime(o.optString("created_at")) ?: now,
            commentCount = o.optInt("comments"),
            authorLogin = login,
        )
    }

    /** One comment JSON object → [Comment]. Shared by the list fetch and the reply response. */
    private fun commentFromJson(o: JSONObject, now: Long): Comment {
        val user = o.optJSONObject("user")
        val login = user?.optString("login") ?: ""
        return Comment(
            id = o.optLong("id"),
            body = o.optString("body"),
            author = login.ifBlank { "unknown" },
            authorAvatar = user?.optString("avatar_url") ?: "",
            createdAtMs = parseGitHubTime(o.optString("created_at")) ?: now,
            authorLogin = login,
        )
    }

    /**
     * Push a freshly created issue onto the top of the feed. Best-effort: any parse failure is
     * swallowed (the next [refresh] reconciles), because the write itself already succeeded.
     */
    private fun insertPost(responseBody: String) {
        try {
            val o = JSONObject(responseBody)
            if (o.has("pull_request")) return
            val post = postFromJson(o, System.currentTimeMillis())
            if (post.isDeleted) return
            val current = _posts.value
            if (current.any { it.number == post.number }) return
            _posts.value = listOf(post) + current
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.w(TAG, "insertPost", t)
        }
    }

    /**
     * Push a freshly created comment onto the end of the open thread. Same best-effort rule;
     * the thread's reply count is bumped too so the feed row stays honest without a refetch.
     */
    private fun insertComment(responseBody: String) {
        try {
            val comment = commentFromJson(JSONObject(responseBody), System.currentTimeMillis())
            val current = _comments.value
            if (current.any { it.id == comment.id }) return
            _comments.value = current + comment
            val open = _openPost.value ?: return
            _posts.value = _posts.value.map { p ->
                if (p.number == open) p.copy(commentCount = p.commentCount + 1) else p
            }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.w(TAG, "insertComment", t)
        }
    }

    /** Drop a deleted reply locally so it disappears without a refetch. */
    private fun removeComment(commentId: Long) {
        _comments.value = _comments.value.filterNot { it.id == commentId }
    }

    // ── HTTP plumbing ───────────────────────────────────────────────────────────────────────────

    private fun authHeaders(token: String? = _account.value?.accessToken): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
            .add("Accept", "application/vnd.github+json")
            .add("X-GitHub-Api-Version", "2022-11-28")
            .add("User-Agent", USER_AGENT)
        // Unauthenticated reads work (60/hr/IP) — the token only appears when we have one.
        if (!token.isNullOrBlank()) builder.add("Authorization", "Bearer $token")
        return builder.build()
    }

    private fun postJson(url: String, json: String): Pair<Int, String> {
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).headers(authHeaders()).post(body).build()
        return execute(request)
    }

    private fun patchJson(url: String, json: String): Pair<Int, String> {
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url(url).headers(authHeaders()).patch(body).build()
        return execute(request)
    }

    private fun execute(request: Request): Pair<Int, String> = try {
        client.newCall(request).execute().use { resp ->
            resp.code to (resp.body?.string() ?: "")
        }
    } catch (t: Throwable) {
        if (BuildConfig.DEBUG) Log.w(TAG, "execute ${request.url}", t)
        -1 to (t.message ?: "network error")
    }

    /**
     * Wrap a mutation so [_busy] is always toggled and the caller always gets an error string
     * instead of an exception. [op] returns null on success or a message on failure.
     */
    private suspend fun mutate(fallback: String, op: suspend () -> String?): String? {
        _busy.value = true
        return try {
            op() ?: null
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.w(TAG, "mutate", t)
            t.message ?: fallback
        } finally {
            _busy.value = false
        }
    }

    /** Turn a GitHub error body into a sentence the drawer can show verbatim. */
    private fun githubError(body: String, code: Int, fallback: String): String {
        if (code == -1) return body.ifBlank { fallback }
        if (code == 401) return "Your GitHub sign-in expired. Sign in again."
        if (code == 403) return "GitHub refused (rate limit or missing permission). Try again shortly."
        if (code == 404) return "Not found — the Social Hub repo may be private or renamed."
        return try {
            val message = JSONObject(body).optString("message")
            if (message.isNotBlank()) "$message (HTTP $code)" else "$fallback (HTTP $code)"
        } catch (_: Throwable) {
            "$fallback (HTTP $code)"
        }
    }

    /** `2026-09-27T15:42:10Z` → epoch ms; SimpleDateFormat (no java.time on minSdk 26). */
    private fun parseGitHubTime(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(iso)?.time
        } catch (_: Throwable) {
            null
        }
    }
}
