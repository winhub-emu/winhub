package com.winhub.hernandez.ui

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.R
import com.winhub.hernandez.communityconfigs.SocialConfigs
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.store.SocialHubFormat
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.store.SocialHubStore.Post
import com.winhub.hernandez.ui.screens.LocalDeckTabs
import com.winhub.hernandez.util.InAppFilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SocialConfigsTab"

/**
 * Social Hub with two sub-tabs: Feed (the existing [SocialContent]) and Game Configurations. In Deck
 * mode the sub-tab follows the shared Deck tab host (L2/R2); elsewhere LB/RB switch it.
 */
@Composable
internal fun SocialHubScreen(deck: Boolean = false) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val host = LocalDeckTabs.current
    if (deck) {
        LaunchedEffect(host) { host.bind(2) }
        LaunchedEffect(host.index) { tab = host.index }
    }
    BackHandler(enabled = tab == 1) {
        tab = 0
        if (deck) host.select(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (deck || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.ButtonL1 -> { tab = 0; true }
                    Key.ButtonR1 -> { tab = 1; true }
                    else -> false
                }
            },
    ) {
        SocialTabPills(tab) {
            tab = it
            if (deck) host.select(it)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) SocialContent() else SocialConfigsContent()
        }
    }
}

@Composable
private fun SocialTabPills(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(stringResource(R.string.social_hub_subtab_feed), stringResource(R.string.social_hub_subtab_configs))
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(12.dp)
            val active = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    )
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        shape,
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

    var detail by remember { mutableStateOf<Post?>(null) }
    var showUpload by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<ImportUi?>(null) }
    // The config waiting for the user to pick the game's exe.
    var pendingImport by remember { mutableStateOf<Post?>(null) }

    LaunchedEffect(Unit) {
        SocialHubStore.init(ctx)
        SocialHubStore.refreshConfigs()
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
            Button(onClick = { showUpload = true }, enabled = account != null, modifier = Modifier.heightIn(min = 48.dp)) {
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
                configs.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.social_hub_configs_empty),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.social_hub_configs_empty_hint),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(configs, key = { it.number }) { post -> ConfigRow(post) { detail = post } }
                }
            }
        }
    }

    detail?.let { post ->
        ConfigDetailDialog(
            post = post,
            onDismiss = { detail = null },
            onImport = {
                detail = null
                pendingImport = post
                pickExe.launch(InAppFilePicker.buildIntent(ctx, InAppFilePicker.SHORTCUT, pickExeTitle))
            },
            onDelete = {
                detail = null
                scope.launch { SocialHubStore.deletePost(post.number)?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
            },
        )
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val json = remember(post.number) { SocialHubFormat.configJson(post.body).orEmpty() }
    val parts = remember(post.number) { SocialConfigs.componentNames(json) }
    val (device, soc) = remember(post.number) { SocialConfigs.hardware(json) }
    val now = remember { System.currentTimeMillis() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(12.dp),
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
    }
}

@Composable
private fun ConfigDetailDialog(post: Post, onDismiss: () -> Unit, onImport: () -> Unit, onDelete: () -> Unit) {
    val json = remember(post.number) { SocialHubFormat.configJson(post.body).orEmpty() }
    val parts = remember(post.number) { SocialConfigs.componentNames(json) }
    val (device, soc) = remember(post.number) { SocialConfigs.hardware(json) }
    val note = remember(post.number) { SocialHubFormat.configNote(post.body) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(post.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.social_hub_configs_by, post.author), fontSize = 14.sp)
                val hw = listOf(device, soc).filter { it.isNotBlank() }.joinToString(" • ")
                if (hw.isNotBlank()) Text(hw, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (note.isNotBlank()) Text(note, fontSize = 14.sp)
                if (parts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.social_hub_configs_components, parts.joinToString("\n")),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.social_hub_configs_import_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onImport, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.social_hub_configs_import), fontSize = 14.sp)
            }
        },
        dismissButton = {
            Row {
                if (post.isMine) {
                    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.social_hub_delete))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.social_hub_configs_close))
                }
            }
        },
    )
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
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        label = { Text(stringResource(R.string.social_hub_configs_note_hint)) },
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
