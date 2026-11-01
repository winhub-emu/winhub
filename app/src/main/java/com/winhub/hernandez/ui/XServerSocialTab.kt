package com.winhub.hernandez.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.R
import com.winhub.hernandez.store.SocialHubFormat
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.store.SocialHubStore.Comment
import com.winhub.hernandez.store.SocialHubStore.Post
import com.winhub.hernandez.ui.screens.ControllerTextField
import com.winhub.hernandez.ui.screens.DeckDragFrame
import com.winhub.hernandez.ui.screens.DeckSearchField
import com.winhub.hernandez.ui.screens.XmbWaves
import com.winhub.hernandez.ui.screens.deckDragItem
import com.winhub.hernandez.ui.screens.deckDragSelf
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───── Social Hub screen (app menu) ─────
// A feed over GitHub Issues on winhub-emu/social-hub: posts = issues, replies = issue comments,
// soft-delete = a marker body (GitHub exposes no delete-issue API for non-owners). Renders off
// SocialHubStore's flows — the same store that owns the device-flow sign-in and the credential
// file at Downloads/WinHub-Credentials. See store/SocialHubStore.kt for the mapping, and why
// images ride on catbox.moe (public host) rather than the repo.
//
// The screen owns its own scrolling (a LazyColumn feed/thread with a pinned composer), so it
// does not depend on any host pane's verticalScroll.

@Composable
internal fun SocialContent() {
    val ctx = LocalContext.current
    val account by SocialHubStore.account.collectAsState()
    val posts by SocialHubStore.posts.collectAsState()
    val loading by SocialHubStore.loading.collectAsState()
    val loginState by SocialHubStore.loginState.collectAsState()
    val error by SocialHubStore.error.collectAsState()
    val openPost by SocialHubStore.openPost.collectAsState()

    var showHelp by remember { mutableStateOf(false) }
    // Hoisted above the feed so the query survives opening a post and coming back.
    var query by remember { mutableStateOf("") }

    // Kotlin refuses to smart-cast a local DELEGATED property (`val account by ...`), so the
    // nullable account is snapshotted into a plain local val first — plain vals are smart-castable.
    val accountOrNull = account

    // Restore a saved token (and refresh the feed) once per composition; no-op after the first.
    LaunchedEffect(Unit) { SocialHubStore.init(ctx) }

    // Outer frame for the header buttons; the feed pane has its own for the list.
    DeckDragFrame(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header: title + "?" help, with refresh + identity + sign-out when signed in.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.social_hub_tab_label),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            SocialHelpDot { showHelp = true }
            Spacer(Modifier.weight(1f))
            if (accountOrNull != null) {
                IconButton(onClick = { SocialHubStore.refresh() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.social_hub_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "@${accountOrNull.login}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 92.dp),
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { SocialHubStore.signOut() }, modifier = Modifier.heightIn(min = 48.dp).deckDragSelf()) {
                    Text(
                        stringResource(R.string.social_hub_sign_out),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (showHelp) {
            AlertDialog(
                onDismissRequest = { showHelp = false },
                title = { Text(stringResource(R.string.social_hub_help_title)) },
                text = { Text(stringResource(R.string.social_hub_help_body)) },
                confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.ok)) } },
            )
        }

        error?.let { message ->
            AlertDialog(
                onDismissRequest = { SocialHubStore.dismissError() },
                title = { Text(stringResource(R.string.social_hub_tab_label)) },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { SocialHubStore.dismissError() }) { Text(stringResource(R.string.ok)) } },
            )
        }

        // Panes fill the remaining height only: a bare fillMaxSize here would be measured against
        // the whole column and push the header off-screen.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                accountOrNull == null -> SocialSignIn(loginState)
                openPost != null -> SocialThread(
                    accountLogin = accountOrNull.login,
                    post = posts.firstOrNull { it.number == openPost },
                )
                else -> SocialFeed(
                    posts = posts,
                    loading = loading,
                    query = query,
                    onQueryChange = { query = it },
                    onOpen = { SocialHubStore.openPost(it.number) },
                )
            }
        }
    }
    }
}

// ───── Sign-in / device code ─────

@Composable
private fun SocialSignIn(loginState: SocialHubStore.LoginState) {
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        // Welcome backdrop: the same XMB wave the preloader draws, tinted with the live accent.
        XmbWaves(accent, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))

        when (loginState) {
            is SocialHubStore.LoginState.Requesting -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.social_hub_sign_in_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            is SocialHubStore.LoginState.AwaitingUser -> DeviceCodePane(
                userCode = loginState.userCode,
                verificationUri = loginState.verificationUri,
                status = loginState.status,
                onCancel = { SocialHubStore.cancelLogin() },
            )
            is SocialHubStore.LoginState.Failed -> Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    loginState.message,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                PurpleContinue(stringResource(R.string.social_hub_retry)) {
                    SocialHubStore.beginLogin()
                }
            }
            else -> WelcomePane(onContinue = { SocialHubStore.beginLogin() })
        }
    }
}

@Composable
private fun WelcomePane(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Forum,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.social_hub_tab_label),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.social_hub_welcome_tagline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PurpleContinue(stringResource(R.string.social_hub_continue_github), onContinue)
    }
}

/**
 * The sign-in CTA. Deliberately purple rather than the theme accent: it sits on the XMB wave
 * backdrop, where the drawer's blue accent reads as a second competing colour.
 */
@Composable
private fun PurpleContinue(text: String, onClick: () -> Unit) {
    val hero = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(hero, hero.copy(alpha = 0.75f))))
            .deckDragSelf()
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 11.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun DeviceCodePane(
    userCode: String,
    verificationUri: String,
    status: String,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.social_hub_enter_code),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))
        // The code is the whole point of the screen: big, monospaced, unbroken.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text(
                userCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(verificationUri)))
            } catch (_: Throwable) {
            }
        }, modifier = Modifier.heightIn(min = 48.dp).deckDragSelf()) {
            Text(
                stringResource(R.string.social_hub_open_device),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).deckDragSelf()) {
            Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───── Feed ─────

/** Case-insensitive match of the search query against title, body and author. */
private fun matchesQuery(post: Post, query: String): Boolean =
    post.title.contains(query, ignoreCase = true) ||
        post.body.contains(query, ignoreCase = true) ||
        post.author.contains(query, ignoreCase = true) ||
        post.authorLogin.contains(query, ignoreCase = true)

@Composable
private fun SocialFeed(
    posts: List<Post>,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (Post) -> Unit,
) {
    var showNew by remember { mutableStateOf(false) }

    if (showNew) {
        SocialComposeDialog(onDismiss = { showNew = false })
    }

    // Filtered here rather than in SocialContent so the thread lookup above keeps the full list.
    val visible = remember(posts, query) {
        val needle = query.trim()
        if (needle.isEmpty()) posts else posts.filter { matchesQuery(it, needle) }
    }

    // One frame for the whole pane so the list rows and the New-post button share one smooth ring.
    DeckDragFrame(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (posts.isNotEmpty()) {
            SocialSearchField(query = query, onQueryChange = onQueryChange)
            Spacer(Modifier.height(6.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            if (loading && posts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.social_hub_loading_feed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            } else if (posts.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.social_hub_empty_feed),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.social_hub_empty_feed_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { showNew = true }, modifier = Modifier.heightIn(min = 48.dp).deckDragSelf()) {
                        Text(stringResource(R.string.social_hub_new_post))
                    }
                }
            } else if (visible.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.social_hub_search_empty),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.social_hub_search_empty_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { onQueryChange("") }, modifier = Modifier.heightIn(min = 48.dp).deckDragSelf()) {
                        Text(stringResource(R.string.social_hub_search_clear))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visible, key = { it.number }) { post ->
                        Box(Modifier.deckDragItem("social-post-${post.number}")) {
                            SocialPostRow(post) { onOpen(post) }
                        }
                    }
                }
            }

            if (posts.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(48.dp)
                        .deckDragSelf()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showNew = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.social_hub_new_post),
                        tint = Color.White,
                    )
                }
            }
        }
    }
    }
}

@Composable
internal fun SocialSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    @androidx.annotation.StringRes hint: Int = R.string.social_hub_search_hint,
) {
    // Waits for A before it takes the keyboard, so the D-pad never gets trapped in the text box.
    DeckSearchField(query = query, onQuery = onQueryChange, hint = stringResource(hint))
}

@Composable
private fun SocialPostRow(post: Post, onClick: () -> Unit) {
    // Ticks once a minute so relative ages stay honest without recomposing every frame.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = post.authorAvatar,
                contentDescription = null,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                post.author,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                SocialHubFormat.relativeTime(post.createdAtMs, now),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            post.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                post.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (post.images.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            // One thumbnail per embedded image; the thread shows them full width.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                post.images.take(3).forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.social_hub_attachments),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                    )
                }
                if (post.images.size > 3) {
                    Text(
                        stringResource(R.string.social_hub_images_more, post.images.size - 3),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        val count = post.commentCount
        Text(
            if (count == 1) stringResource(R.string.social_hub_reply_count_one, count)
            else stringResource(R.string.social_hub_reply_count_many, count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

// ───── Thread (post + replies) ─────

@Composable
internal fun SocialThread(
    accountLogin: String,
    post: Post?,
    onBack: () -> Unit = { SocialHubStore.openPost(null) },
    // Extra content between the post card and the replies (the Configs tab puts its actions here).
    extra: @Composable () -> Unit = {},
) {
    val comments by SocialHubStore.comments.collectAsState()
    val commentsLoading by SocialHubStore.commentsLoading.collectAsState()
    val busy by SocialHubStore.busy.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    var confirmDeletePost by remember { mutableStateOf(false) }
    var confirmDeleteComment by remember { mutableStateOf<Comment?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.size - 1)
    }

    // The post vanished underneath us (deleted remotely, or never loaded).
    if (post == null) {
        LaunchedEffect(Unit) { SocialHubStore.openPost(null) }
        return
    }

    val send = {
        val body = draft.trim()
        if (body.isNotEmpty()) {
            scope.launch {
                SocialHubStore.addComment(post.number, body)
                draft = ""
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }

    if (confirmDeletePost) {
        AlertDialog(
            onDismissRequest = { confirmDeletePost = false },
            title = { Text(stringResource(R.string.social_hub_delete_post_title)) },
            text = { Text(stringResource(R.string.social_hub_delete_post_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeletePost = false
                    scope.launch {
                        SocialHubStore.deletePost(post.number)
                        onBack()
                    }
                }) { Text(stringResource(R.string.social_hub_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePost = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    confirmDeleteComment?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteComment = null },
            title = { Text(stringResource(R.string.social_hub_delete_reply_title)) },
            text = { Text(stringResource(R.string.social_hub_delete_reply_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteComment = null
                    scope.launch { SocialHubStore.deleteComment(target.id) }
                }) { Text(stringResource(R.string.social_hub_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteComment = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onBack() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.social_hub_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                post.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (post.authorLogin == accountLogin) {
                IconButton(onClick = { confirmDeletePost = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.social_hub_delete_post),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorAvatar,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    post.author,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (post.text.isNotBlank()) {
                Text(post.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            }
            post.images.forEach { url ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.social_hub_attachments),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.3f)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        extra()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                commentsLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                )
                comments.isEmpty() -> Text(
                    stringResource(R.string.social_hub_no_replies),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(comments, key = { it.id }) { c ->
                        SocialCommentRow(
                            comment = c,
                            onDelete = if (c.authorLogin == accountLogin)
                                ({ confirmDeleteComment = c }) else null,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControllerTextField(
                value = draft,
                onValueChange = { draft = it },
                hint = stringResource(R.string.social_hub_reply_hint),
                modifier = Modifier.weight(1f),
                maxLines = 3,
                enabled = !busy,
                imeAction = ImeAction.Send,
                onImeAction = { send() },
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { send() }, enabled = draft.isNotBlank() && !busy, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.social_hub_send),
                    tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SocialCommentRow(comment: Comment, onDelete: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = comment.authorAvatar,
                contentDescription = null,
                modifier = Modifier.size(18.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                comment.author,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.social_hub_delete_reply),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
        if (comment.text.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(comment.text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        comment.images.forEach { url ->
            Spacer(Modifier.height(6.dp))
            AsyncImage(
                model = url,
                contentDescription = stringResource(R.string.social_hub_attachments),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
            )
        }
    }
}

// ───── New-post composer ─────

@Composable
private fun SocialComposeDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by SocialHubStore.busy.collectAsState()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }

    // Resolved once here: stringResource is @Composable, so it cannot be read inside the
    // picker's plain onClick lambda below.
    val pickTitle = stringResource(R.string.social_hub_pick_image)

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val path = if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedPath(result.data) else null
        if (path != null) {
            uploading = true
            scope.launch {
                try {
                    val file = java.io.File(path)
                    val url = SocialHubStore.uploadImage(file.readBytes(), file.name)
                    if (url != null) body = body.trimEnd() + "\n\n![]($url)\n"
                } catch (_: Throwable) {
                } finally {
                    uploading = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.social_hub_new_post_title)) },
        text = {
            Column {
                ControllerTextField(
                    value = title,
                    onValueChange = { title = it },
                    hint = stringResource(R.string.social_hub_post_title_field),
                    singleLine = true,
                    enabled = !busy && !uploading,
                )
                Spacer(Modifier.height(8.dp))
                ControllerTextField(
                    value = body,
                    onValueChange = { body = it },
                    hint = stringResource(R.string.social_hub_post_body_field),
                    minHeight = 120.dp,
                    enabled = !busy && !uploading,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            pickImage.launch(
                                InAppFilePicker.buildIntent(ctx, InAppFilePicker.IMAGES, pickTitle),
                            )
                        },
                        enabled = !busy && !uploading,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = stringResource(R.string.social_hub_attachments),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.social_hub_uploading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    } else {
                        Text(
                            stringResource(R.string.social_hub_attach_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !busy && !uploading,
                onClick = {
                    scope.launch {
                        val error = SocialHubStore.createPost(title.trim(), body.trim())
                        // No refresh() here: createPost already inserted the new issue locally.
                        // A refetch can race GitHub's list endpoint and drop it again.
                        if (error == null) onDismiss()
                    }
                },
            ) {
                Text(
                    if (busy) stringResource(R.string.social_hub_posting)
                    else stringResource(R.string.social_hub_post_action)
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** The small circular "?" that opens the tab's help (same look as the Friends tab's). */
@Composable
private fun SocialHelpDot(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // 48dp hit area (controller/touch); the visible dot stays small.
    Box(
        Modifier.size(48.dp).deckDragSelf().clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(cs.surfaceVariant)
                .border(1.dp, cs.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "?",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
