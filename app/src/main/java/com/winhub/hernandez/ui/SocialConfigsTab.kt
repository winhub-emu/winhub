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
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
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
import com.winhub.hernandez.communityconfigs.ConfigLibrary
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
import com.winhub.hernandez.ui.screens.Deck
import com.winhub.hernandez.ui.screens.DeckHintBar
import com.winhub.hernandez.ui.screens.DeckRow
import com.winhub.hernandez.ui.screens.DeckStoreHeader
import com.winhub.hernandez.ui.screens.DeckStoreSideTabs
import com.winhub.hernandez.ui.screens.DeckStretchyTabs
import com.winhub.hernandez.ui.screens.LocalDeckMode
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
 * Landscape Deck lays the page out like a store: identity and the sub-tabs down the left, the page beside them.
 * [onExit] (Full XMB) is where B goes from the top level, back to Home.
 */
@Composable
internal fun SocialHubScreen(deck: Boolean = false, onExit: (() -> Unit)? = null) {
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
    val storeStyle = deck && !portraitDeck
    val layout = if (portraitDeck) "tabs" else if (storeStyle) "store" else if (chosen == "rail" && narrow) "tabs" else chosen
    val labels = listOf(stringResource(R.string.social_hub_subtab_feed), stringResource(R.string.social_hub_subtab_configs), stringResource(R.string.social_hub_subtab_profile))
    val select = { i: Int ->
        tab = i
        if (deck) host.select(i)
    }
    val account by SocialHubStore.account.collectAsState()
    val exitNow by rememberUpdatedState(onExit)
    // Pad B goes through the Deck shell, which asks the page for the back step: Configs / Profile -> Feed, then Home.
    DisposableEffect(host, storeStyle, tab, onExit != null) {
        val step: () -> Boolean = {
            val exit = exitNow
            when {
                tab != 0 -> { select(0); true }
                exit != null -> { exit(); true }
                else -> false
            }
        }
        if (storeStyle) host.onBack = step
        onDispose { if (host.onBack === step) host.onBack = null }
    }
    val pane: @Composable () -> Unit = {
        when (tab) {
            0 -> SocialContent(showTitle = !storeStyle)
            1 -> SocialConfigsContent()
            else -> SocialProfileContent(onGoFeed = { select(0) })
        }
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
        if (deck && !storeStyle) com.winhub.hernandez.ui.screens.PortraitScreenTitle("Social Hub", horizontal = 0.dp)
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
        if (layout == "store") {
            val tabFocus = remember { FocusRequester() }
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                ) {
                    DeckStoreHeader(
                        name = "Social",
                        fill = Color(0xFF134E4A),
                        ink = Color(0xFF7FE0D6),
                        accent = Color(0xFF2DD4BF),
                        signedIn = account != null,
                    )
                    DeckStoreSideTabs(
                        labels = listOf("Feed", "Configs", "Profile"),
                        selected = tab,
                        onSelect = select,
                        selectedFocus = tabFocus,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) { pane() }
            }
            // Skipped on short landscape screens, where the page needs the room more.
            if (LocalConfiguration.current.screenHeightDp >= 400) DeckHintBar(
                hints = buildList {
                    add("A" to "Select")
                    add("L2/R2" to "Switch tab")
                    if (onExit != null) add("B" to "Home") else if (tab != 0) add("B" to "Feed")
                },
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
        } else if (layout == "pages" && !pagesOpen) {
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
                    Box(Modifier.weight(1f).fillMaxWidth()) { pane() }
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
    // Curated library (configs/ in the Social Hub repo): games first, then that game's configs.
    var libraryTab by rememberSaveable { mutableStateOf(true) }
    var libGames by remember { mutableStateOf<List<ConfigLibrary.Game>?>(null) }
    var libLoading by remember { mutableStateOf(false) }
    var libFailed by remember { mutableStateOf(false) }
    var libGameId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLibrary by remember { mutableStateOf<Pair<ConfigLibrary.Game, ConfigLibrary.Entry>?>(null) }

    fun loadLibrary(force: Boolean) {
        libLoading = true
        scope.launch {
            val r = ConfigLibrary.load(ctx, force)
            r.onSuccess { libGames = it }
            // Keep a list that is already on screen when a refresh fails.
            libFailed = r.isFailure && libGames == null
            libLoading = false
        }
    }

    LaunchedEffect(Unit) {
        SocialHubStore.init(ctx)
        SocialHubStore.refreshConfigs()
        loadLibrary(false)
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
    // Pad B closes an open config first (the Deck shell asks this layer before it leaves the page).
    val deckBack = com.winhub.hernandez.ui.screens.LocalDeckBack.current
    DisposableEffect(open != null) {
        val close: () -> Boolean = { closeDetail(); true }
        if (open != null) deckBack.onBack = close
        onDispose { if (deckBack.onBack === close) deckBack.onBack = null }
    }

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

    fun startImport(title: String, exe: File, loadJson: suspend () -> String?) {
        importing = ImportUi(title, "", done = false, ok = false)
        scope.launch {
            val outcome = try {
                val json = loadJson()
                if (json == null) SocialConfigs.ImportOutcome(false, "Couldn't download that config. Check your connection and try again.")
                else SocialConfigs.importAsCronos(ctx, json, title, exe) { line ->
                    importing = importing?.copy(status = line)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "import failed", e)
                SocialConfigs.ImportOutcome(false, e.message ?: "Import failed.")
            }
            importing = ImportUi(title, outcome.message, done = true, ok = outcome.ok)
        }
    }

    val pickExeTitle = stringResource(R.string.social_hub_configs_pick_exe)
    val pickExe = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val post = pendingImport
        val lib = pendingLibrary
        pendingImport = null
        pendingLibrary = null
        val path = if (result.resultCode == Activity.RESULT_OK) InAppFilePicker.pickedPath(result.data) else null
        if (path == null) return@rememberLauncherForActivityResult
        if (post != null) {
            val json = SocialHubFormat.configJson(post.body) ?: return@rememberLauncherForActivityResult
            startImport(post.title, File(path)) { json }
        } else if (lib != null) {
            val (game, entry) = lib
            startImport(game.name, File(path)) { ConfigLibrary.fetchConfig(game.id, entry.file) }
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
                if (LocalDeckMode.current) {
                    SocialSectionLabel(
                        stringResource(R.string.social_hub_subtab_configs),
                        libGames?.size?.takeIf { libraryTab && it > 0 }?.let { "$it games" }
                            ?: configs.size.takeIf { !libraryTab && it > 0 }?.let { "$it shared" },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        stringResource(R.string.social_hub_subtab_configs),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                IconButton(onClick = { SocialHubStore.refreshConfigs(); loadLibrary(true) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.social_hub_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SocialButton(
                    stringResource(R.string.social_hub_configs_upload),
                    onClick = { showUpload = true },
                    enabled = account != null,
                    icon = Icons.Filled.Add,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    text = stringResource(R.string.social_hub_configs_tab_library),
                    checked = libraryTab,
                    onClick = { libraryTab = true },
                )
                ToggleChip(
                    text = stringResource(R.string.social_hub_configs_tab_shared),
                    checked = !libraryTab,
                    onClick = { libraryTab = false },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (libraryTab) {
                LibraryBody(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    games = libGames,
                    loading = libLoading,
                    failed = libFailed,
                    openId = libGameId,
                    onOpen = { libGameId = it },
                    userHw = userHw,
                    onImport = { game, entry ->
                        pendingLibrary = game to entry
                        pickExe.launch(InAppFilePicker.buildIntent(ctx, InAppFilePicker.SHORTCUT, pickExeTitle))
                    },
                )
            } else {
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
    val json = remember(post.number) { SocialHubFormat.configJson(post.body).orEmpty() }
    val parts = remember(post.number) { SocialConfigs.componentNames(json) }
    val (device, soc) = remember(post.number) { SocialConfigs.hardware(json) }
    val now = remember { System.currentTimeMillis() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .socialCard()
            .deckDragSelf()
            .clickable(onClick = onClick)
            .padding(12.dp),
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
    SocialEmpty(stringResource(title), stringResource(hint))
}

/**
 * The curated library: first the games (searchable), then that game's configs with the ones that
 * match this device on top. [onImport] hands the pick back so the host can ask for the game's exe.
 */
@Composable
private fun LibraryBody(
    modifier: Modifier,
    games: List<ConfigLibrary.Game>?,
    loading: Boolean,
    failed: Boolean,
    openId: String?,
    onOpen: (String?) -> Unit,
    userHw: Pair<String?, String?>?,
    onImport: (ConfigLibrary.Game, ConfigLibrary.Entry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var matchesMyDevice by rememberSaveable { mutableStateOf(false) }
    val open = openId?.let { id -> games?.firstOrNull { it.id == id } }
    BackHandler(enabled = open != null) { onOpen(null) }

    fun matches(entry: ConfigLibrary.Entry): Boolean =
        userHw?.let { (mySoc, myGpu) -> GameMatcher.hardwareMatchesUser(mySoc, myGpu, listOf(entry.device, entry.soc)) } ?: false

    Column(modifier) {
        when {
            games == null && (loading || !failed) -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.social_hub_configs_library_loading), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            games == null ->
                EmptyNote(R.string.social_hub_configs_library_error, R.string.social_hub_configs_library_error_hint)
            open != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onOpen(null) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.social_hub_configs_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            open.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.social_hub_configs_library_count, open.configs.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // This device's matches first; the rest keep the index order (grouped by chipset).
                val ordered = remember(open.id, userHw) { open.configs.sortedByDescending { matches(it) } }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(ordered, key = { it.file }) { entry ->
                        LibraryConfigRow(entry, matches(entry)) { onImport(open, entry) }
                    }
                }
            }
            else -> {
                SocialSearchField(query = query, onQueryChange = { query = it }, hint = R.string.social_hub_configs_search_hint)
                Spacer(Modifier.height(6.dp))
                ToggleChip(
                    text = stringResource(R.string.social_hub_configs_matches_device),
                    checked = matchesMyDevice,
                    onClick = { matchesMyDevice = !matchesMyDevice },
                )
                Spacer(Modifier.height(8.dp))
                val visible = remember(games, query, matchesMyDevice, userHw) {
                    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    games.filter { g ->
                        val textOk = tokens.all { g.name.lowercase().contains(it) || g.id.lowercase().replace('_', ' ').contains(it) }
                        textOk && (!matchesMyDevice || g.configs.any(::matches))
                    }
                }
                if (visible.isEmpty()) {
                    EmptyNote(R.string.social_hub_configs_no_match, R.string.social_hub_configs_no_match_hint)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(visible, key = { it.id }) { game -> LibraryGameRow(game) { onOpen(game.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGameRow(game: ConfigLibrary.Game, onClick: () -> Unit) {
    val chips = remember(game.id) {
        game.configs.map { it.soc.replace(" (TM)", "") }.filter { it.isNotBlank() }.distinct().take(3).joinToString(" • ")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .socialCard()
            .deckDragSelf()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            game.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.social_hub_configs_library_count, game.configs.size) +
                if (chips.isNotEmpty()) "  •  $chips" else "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryConfigRow(entry: ConfigLibrary.Entry, matchesDevice: Boolean, onClick: () -> Unit) {
    val hw = listOf(entry.device, entry.soc).filter { it.isNotBlank() }.joinToString(" • ")
    val parts = listOf(
        entry.driver.takeIf { it.isNotBlank() }?.let { "Driver $it" },
        entry.dxvk.takeIf { it.isNotBlank() }?.let { "DXVK $it" },
        entry.vkd3d.takeIf { it.isNotBlank() }?.let { "VKD3D $it" },
        entry.fex.takeIf { it.isNotBlank() }?.let { "FEX $it" },
    ).filterNotNull()
    val line = listOf(entry.proton, entry.resolution).filter { it.isNotBlank() }.joinToString(" • ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .socialCard(highlighted = matchesDevice)
            .deckDragSelf()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        if (matchesDevice) {
            Text(
                "✓ " + stringResource(R.string.social_hub_configs_library_match),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            hw.ifBlank { "Unknown device" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (line.isNotEmpty()) {
            Text(line, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" • "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** On/off filter pill; the check mark (not just the colour) shows the state. */
@Composable
private fun ToggleChip(text: String, checked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val deck = LocalDeckMode.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                else if (deck) Deck.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .border(
                1.dp,
                if (checked && deck) Deck.primary else if (deck) Deck.line else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape,
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
            SocialButton(
                likeLabel,
                onClick = likeClick,
                modifier = Modifier.weight(1f),
                primary = liked,
                enabled = signedIn && !liking,
                icon = Icons.Filled.ThumbUp,
            )
            SocialButton(stringResource(R.string.social_hub_configs_import), onClick = onImport, modifier = Modifier.weight(1f))
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
