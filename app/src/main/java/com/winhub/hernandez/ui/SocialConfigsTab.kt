package com.winhub.hernandez.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import com.winhub.hernandez.R
import com.winhub.hernandez.communityconfigs.DeviceIdentity
import com.winhub.hernandez.communityconfigs.GameMatcher
import com.winhub.hernandez.communityconfigs.SocialConfigs
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.SocialHubFormat
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.store.SocialHubStore.Post
import com.winhub.hernandez.ui.screens.ControllerTextField
import com.winhub.hernandez.ui.screens.DeckDragFrame
import com.winhub.hernandez.ui.screens.DeckEditorCategoryRail
import com.winhub.hernandez.ui.screens.DeckEditorCategoryRow
import com.winhub.hernandez.ui.screens.DeckRow
import com.winhub.hernandez.ui.screens.DeckStretchyTabs
import com.winhub.hernandez.ui.screens.LocalDeckTabs
import com.winhub.hernandez.ui.screens.deckDragSelf
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SocialConfigsTab"

/**
 * Social Hub with two sub-tabs: Feed (the existing [SocialContent]) and Game Configurations. The
 * sub-tabs follow the user's Settings layout choice (side rail / top tabs / category pages), like
 * Contents and Settings. In Deck mode the Deck tab host drives them (L2/R2); elsewhere LB/RB do.
 */
@Composable
internal fun SocialHubScreen(deck: Boolean = false) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pagesOpen by rememberSaveable { mutableStateOf(false) }
    val host = LocalDeckTabs.current
    if (deck) {
        LaunchedEffect(host) { host.bind(3) }
        LaunchedEffect(host.index) { tab = host.index }
    }
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    // A 200dp rail would eat a phone in portrait, so narrow screens fall back to the top strip.
    val narrow = LocalConfiguration.current.screenWidthDp < 560
    val chosen = prefs.getString("settings_layout", "rail") ?: "rail"
    val portraitDeck = com.winhub.hernandez.ui.screens.LocalDeckPortrait.current
    val layout = if (portraitDeck) "tabs" else if (chosen == "rail" && narrow) "tabs" else chosen
    val labels = listOf(stringResource(R.string.social_hub_subtab_feed), stringResource(R.string.social_hub_subtab_configs), stringResource(R.string.social_hub_subtab_profile))
    val select = { i: Int ->
        tab = i
        if (deck) host.select(i)
    }

    // B goes up one level: category page -> category list; Configs -> Feed.
    BackHandler(enabled = layout == "pages" && pagesOpen) { pagesOpen = false }
    BackHandler(enabled = layout != "pages" && tab != 0) { select(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (portraitDeck) Modifier.padding(horizontal = 16.dp) else Modifier)
            .onPreviewKeyEvent { e ->
                if (deck || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    // L2/R2 like Deck's sub-tabs, plus the bumpers.
                    Key.ButtonL1, Key.ButtonL2 -> { select((tab - 1).coerceAtLeast(0)); true }
                    Key.ButtonR1, Key.ButtonR2 -> { select((tab + 1).coerceAtMost(2)); true }
                    else -> false
                }
            },
    ) {
        if (deck) com.winhub.hernandez.ui.screens.PortraitScreenTitle("Social Hub", horizontal = 0.dp)
        if (layout == "tabs") {
            if (portraitDeck) {
                com.winhub.hernandez.ui.screens.DeckPortraitChips(labels, tab, select)
            } else {
                            DeckStretchyTabs(
                    labels = labels,
                    selected = tab,
                    onSelect = select,
                    focusable = !deck,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        if (layout == "pages" && !pagesOpen) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, label ->
                    DeckEditorCategoryRow(label = label, selected = i == tab) {
                        select(i)
                        pagesOpen = true
                    }
                }
            }
        } else {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (layout == "rail") {
                    DeckEditorCategoryRail(
                        labels = labels,
                        selected = tab,
                        onSelect = select,
                        // Deck switches sub-tabs with L2/R2 and keeps D-pad focus in the page.
                        padFocus = !deck,
                        modifier = Modifier.width(180.dp).fillMaxHeight().padding(end = 12.dp),
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (layout == "pages") {
                        DeckRow(
                            label = "← All categories",
                            hint = "Back to the category list",
                            onConfigClick = { pagesOpen = false },
                            control = {},
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> SocialContent()
                            1 -> SocialConfigsContent()
                            else -> SocialProfileContent(onGoFeed = { select(0) })
                        }
                    }
                }
            }
        }
    }
}

private data class ImportUi(val name: String, val status: String, val done: Boolean, val ok: Boolean)

@Composable
private fun SocialConfigsContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by SocialHubStore.account.collectAsState()
    val configs by SocialHubStore.configs.collectAsState()
    val loading by SocialHubStore.configsLoading.collectAsState()

    // Number, not Post: the open config is re-read from the list so likes/replies stay live.
    var detailNumber by remember { mutableStateOf<Int?>(null) }
    var showUpload by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var matchesMyDevice by rememberSaveable { mutableStateOf(false) }
    // (soc, gpu) of this device; the GPU probe is native, so it stays off the main thread.
    var userHw by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    var importing by remember { mutableStateOf<ImportUi?>(null) }
    // The config waiting for the user to pick the game's exe.
    var pendingImport by remember { mutableStateOf<Post?>(null) }

    LaunchedEffect(Unit) {
        SocialHubStore.init(ctx)
        SocialHubStore.refreshConfigs()
    }
    LaunchedEffect(Unit) {
        userHw = withContext(Dispatchers.IO) { DeviceIdentity.soc() to DeviceIdentity.gpu(ctx) }
    }
    // Leaving the tab with a thread open must not leave the store pointing at it.
    DisposableEffect(Unit) { onDispose { SocialHubStore.openPost(null) } }

    val open = detailNumber?.let { n -> configs.firstOrNull { it.number == n } }
    val closeDetail = {
        detailNumber = null
        SocialHubStore.openPost(null)
    }
    BackHandler(enabled = open != null) { closeDetail() }

    val visible = remember(configs, query, matchesMyDevice, userHw) {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        configs.filter { post ->
            val json = SocialHubFormat.configJson(post.body).orEmpty()
            val (device, soc) = SocialConfigs.hardware(json)
            val haystack = (listOf(post.title, post.author, device, soc, SocialHubFormat.configNote(post.body)) +
                SocialConfigs.componentNames(json)).joinToString(" ").lowercase()
            val textOk = tokens.all { haystack.contains(it) }
            val hwOk = !matchesMyDevice ||
                (userHw?.let { (mySoc, myGpu) -> GameMatcher.hardwareMatchesUser(mySoc, myGpu, listOf(device, soc)) } ?: false)
            textOk && hwOk
        }
    }

    val pickExeTitle = stringResource(R.string.social_hub_configs_pick_exe)
    val pickExe = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val post = pendingImport
        pendingImport = null
        val path = if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedPath(result.data) else null
        val json = SocialHubFormat.configJson(post?.body)
        if (post == null || path == null || json == null) return@rememberLauncherForActivityResult
        importing = ImportUi(post.title, "", done = false, ok = false)
        scope.launch {
            val outcome = try {
                SocialConfigs.importAsCronos(ctx, json, post.title, File(path)) { line ->
                    importing = importing?.copy(status = line)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "import failed", e)
                SocialConfigs.ImportOutcome(false, e.message ?: "Import failed.")
            }
            importing = ImportUi(post.title, outcome.message, done = true, ok = outcome.ok)
        }
    }

    if (open != null) {
        val note = SocialHubFormat.configNote(open.body)
        DeckDragFrame(Modifier.fillMaxSize()) {
        SocialThread(
            accountLogin = account?.login.orEmpty(),
            post = open.copy(body = note),
            onBack = closeDetail,
            extra = {
                ConfigActions(
                    post = open,
                    signedIn = account != null,
                    onImport = {
                        pendingImport = open
                        pickExe.launch(InAppFilePicker.buildIntent(ctx, InAppFilePicker.SHORTCUT, pickExeTitle))
                    },
                )
            },
        )
        }
    } else {
        DeckDragFrame(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.social_hub_subtab_configs),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { SocialHubStore.refreshConfigs() }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.social_hub_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { showUpload = true }, enabled = account != null, modifier = Modifier.heightIn(min = 48.dp).deckDragSelf()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.social_hub_configs_upload), fontSize = 14.sp)
                }
            }
            if (account == null) {
                Text(
                    stringResource(R.string.social_hub_configs_sign_in_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            SocialSearchField(query = query, onQueryChange = { query = it }, hint = R.string.social_hub_configs_search_hint)
            Spacer(Modifier.height(6.dp))
            ToggleChip(
                text = stringResource(R.string.social_hub_configs_matches_device),
                checked = matchesMyDevice,
                onClick = { matchesMyDevice = !matchesMyDevice },
            )
            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && configs.isEmpty() -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.social_hub_configs_loading), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    configs.isEmpty() -> EmptyNote(R.string.social_hub_configs_empty, R.string.social_hub_configs_empty_hint)
                    visible.isEmpty() -> EmptyNote(R.string.social_hub_configs_no_match, R.string.social_hub_configs_no_match_hint)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.number }) { post ->
                            ConfigRow(post) {
                                detailNumber = post.number
                                SocialHubStore.openPost(post.number)
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showUpload) ConfigUploadDialog(onDismiss = { showUpload = false })

    importing?.let { ui ->
        AlertDialog(
            onDismissRequest = { if (ui.done) importing = null },
            title = { Text(ui.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                if (ui.done) {
                    Text(ui.status, fontSize = 14.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(ui.status.ifBlank { stringResource(R.string.social_hub_configs_working) }, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                if (ui.done) TextButton(onClick = { importing = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun ConfigRow(post: Post, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val json = remember(post.number) { SocialHubFormat.configJson(post.body).orEmpty() }
    val parts = remember(post.number) { SocialConfigs.componentNames(json) }
    val (device, soc) = remember(post.number) { SocialConfigs.hardware(json) }
    val now = remember { System.currentTimeMillis() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .deckDragSelf()
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = com.winhub.hernandez.store.rememberShownAvatar(post.authorLogin, post.authorAvatar),
                contentDescription = null,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                com.winhub.hernandez.store.rememberShownName(post.authorLogin, post.author),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                SocialHubFormat.relativeTime(post.createdAtMs, now),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            post.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val hw = listOf(device, soc).filter { it.isNotBlank() }.joinToString(" • ")
        if (hw.isNotBlank()) {
            Text(hw, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                parts.joinToString(" • "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
         Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.social_hub_configs_stats, post.likes, post.commentCount),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyNote(@androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes hint: Int) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(hint),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** On/off filter pill; the check mark (not just the colour) shows the state. */
@Composable
private fun ToggleChip(text: String, checked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .deckDragSelf()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            if (checked) "✓ $text" else text,
            fontSize = 14.sp,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Like + Import for the open config, shown between the post card and the replies. */
@Composable
private fun ConfigActions(post: Post, signedIn: Boolean, onImport: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember(post.number) { SocialHubFormat.configJson(post.body).orEmpty() }
    val parts = remember(post.number) { SocialConfigs.componentNames(json) }
    val (device, soc) = remember(post.number) { SocialConfigs.hardware(json) }
    var myLike by remember(post.number) { mutableStateOf<Long?>(null) }
    var liking by remember { mutableStateOf(false) }

    LaunchedEffect(post.number, signedIn) { myLike = SocialHubStore.myLikeId(post.number) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val hw = listOf(device, soc).filter { it.isNotBlank() }.joinToString(" • ")
        if (hw.isNotBlank()) Text(hw, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!signedIn) {
            Text(stringResource(R.string.social_hub_configs_sign_in_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (parts.isNotEmpty()) {
            Text(parts.joinToString(" • "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val liked = myLike != null
            val likeClick = {
                liking = true
                scope.launch {
                    val r = SocialHubStore.toggleLike(post.number, myLike)
                    if (r.error != null) Toast.makeText(ctx, r.error, Toast.LENGTH_LONG).show() else myLike = r.reactionId
                    liking = false
                }
                Unit
            }
            val likeLabel = stringResource(if (liked) R.string.social_hub_configs_liked else R.string.social_hub_configs_like, post.likes)
            if (liked) {
                Button(onClick = likeClick, enabled = signedIn && !liking, modifier = Modifier.heightIn(min = 48.dp).weight(1f).deckDragSelf()) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(likeLabel, fontSize = 14.sp)
                }
            } else {
                OutlinedButton(onClick = likeClick, enabled = signedIn && !liking, modifier = Modifier.heightIn(min = 48.dp).weight(1f).deckDragSelf()) {
                    Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(likeLabel, fontSize = 14.sp)
                }
            }
            Button(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp).weight(1f).deckDragSelf()) {
                Text(stringResource(R.string.social_hub_configs_import), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ConfigUploadDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by SocialHubStore.busy.collectAsState()

    var shortcuts by remember { mutableStateOf<List<Shortcut>?>(null) }
    var selected by remember { mutableStateOf<Shortcut?>(null) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        shortcuts = withContext(Dispatchers.IO) {
            try {
                ContainerManager(ctx).also { it.reloadContainers() }.loadShortcuts().sortedBy { it.name.lowercase() }
            } catch (e: Exception) {
                Log.w(TAG, "loadShortcuts failed", e)
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.social_hub_configs_upload_title)) },
        text = {
            val list = shortcuts
            val pick = selected
            when {
                list == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.social_hub_configs_loading_games), fontSize = 14.sp)
                }
                pick != null -> Column {
                    Text(pick.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ControllerTextField(
                        value = note,
                        onValueChange = { note = it },
                        hint = stringResource(R.string.social_hub_configs_note_hint),
                        minHeight = 120.dp,
                        enabled = !busy,
                    )
                    error?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                list.isEmpty() -> Text(stringResource(R.string.social_hub_configs_no_games), fontSize = 14.sp)
                else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(list, key = { it.file.absolutePath }) { s ->
                        var focused by remember { mutableStateOf(false) }
                        Text(
                            s.name,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                                .onFocusChanged { focused = it.isFocused }
                                .clickable { selected = s }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val pick = selected
            if (pick != null) {
                Button(
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        error = null
                        scope.launch {
                            val err = try {
                                val exported = withContext(Dispatchers.IO) { SocialConfigs.export(ctx, pick) }
                                SocialHubStore.createConfig(exported.title, note, exported.json)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "export failed", e)
                                e.message ?: "Could not export this game's settings."
                            }
                            if (err == null) onDismiss() else error = err
                        }
                    },
                ) { Text(stringResource(R.string.social_hub_configs_share), fontSize = 14.sp) }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (selected != null && !busy) selected = null else if (!busy) onDismiss() },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (selected != null) R.string.social_hub_configs_back else R.string.social_hub_configs_close))
            }
        },
    )
}
