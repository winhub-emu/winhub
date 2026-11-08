package com.winhub.hernandez.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.contents.ContentProfile
import com.winhub.hernandez.contents.ContentsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Everything the shared Add Game flow needs from a store: what to call the game, its art, the
 * Steam app id when there is one, and how to find the installed executable (called off the main
 * thread). Stores whose game is not on disk yet simply answer null and the flow says so.
 */
internal data class DeckAddRequest(
    val title: String,
    val store: String? = null,
    val coverUrl: String? = null,
    val appId: Int? = null,
    /** Extra .desktop keys written after the shortcut is created (the Xbox store's storeSource/xboxProductId). */
    val extras: Map<String, String> = emptyMap(),
    val resolveExe: suspend () -> String?,
)

/**
 * The SAME add-a-game flow the Home tab's "Add Games" runs — Master Containers vs Cronos, then the
 * confirm dialog — but seeded from a store game we already know the title + exe of. On success the
 * game is written into the container as a shortcut AND into [HubGamesStore], so it shows up on the
 * Deck home carousel instead of only existing as a .desktop file.
 */
@Composable
internal fun DeckStoreAddFlow(request: DeckAddRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exe by remember(request.title) { mutableStateOf<String?>(null) }
    var resolving by remember(request.title) { mutableStateOf(true) }
    var mode by remember { mutableStateOf<HubAddMode?>(null) }
    var containers by remember { mutableStateOf<List<Container>>(emptyList()) }
    var coverPath by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(request.title) {
        val resolved = withContext(Dispatchers.IO) {
            runCatching { request.resolveExe() }.getOrNull()
        }
        exe = resolved
        resolving = false
        if (resolved == null) {
            Toast.makeText(
                context,
                "Couldn't find the installed game — launch it once from the store first",
                Toast.LENGTH_LONG,
            ).show()
            onDismiss()
            return@LaunchedEffect
        }
        coverPath = withContext(Dispatchers.IO) { cacheStoreCover(context, request.coverUrl, request.title) }
        containers = withContext(Dispatchers.IO) {
            ContainerManager(context).also { it.reloadContainers() }.getContainers().toList()
        }
    }

    if (resolving) {
        DeckAddBusyDialog("Finding the installed game…")
        return
    }
    val path = exe ?: return
    val picked = mode
    if (picked == null) {
        DeckAddModeMenu(onPick = { mode = it }, onDismiss = onDismiss)
        return
    }

    HubConfirmDialog(
        file = File(path),
        containerNames = containers.filter { !it.isCronos }.map { it.name },
        initialName = request.title,
        initialAppId = request.appId,
        mode = picked,
        onDismiss = onDismiss,
        onConfirm = { name, appId, containerIndex, chosenCover ->
            if (busy) return@HubConfirmDialog
            busy = true
            val baseCover = chosenCover ?: coverPath
            if (containerIndex < 0) {
                scope.launch {
                    val added = runCatching {
                        val art = baseCover ?: appId?.let {
                            withContext(Dispatchers.IO) { fetchHubCover(context, it) }
                        }
                        val created = createDeckCronosContainer(context, name) ?: return@runCatching null
                        withContext(Dispatchers.IO) {
                            linkStoreGame(context, created, File(path), name, appId, art, request.store, request.extras)
                        }
                    }.getOrNull()
                    busy = false
                    if (added == null) {
                        Toast.makeText(context, "Cronos setup failed", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            context, "Added \"$name\" to its own Cronos container", Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    }
                }
            } else {
                val container = containers.filter { !it.isCronos }.getOrNull(containerIndex)
                if (container == null) {
                    busy = false
                    Toast.makeText(context, "Create a container first", Toast.LENGTH_SHORT).show()
                    return@HubConfirmDialog
                }
                scope.launch {
                    val added = runCatching {
                        val art = baseCover ?: appId?.let {
                            withContext(Dispatchers.IO) { fetchHubCover(context, it) }
                        }
                        withContext(Dispatchers.IO) {
                            linkStoreGame(context, container, File(path), name, appId, art, request.store, request.extras)
                        }
                    }.getOrNull()
                    busy = false
                    if (added == null) {
                        Toast.makeText(context, "Couldn't add that game", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Added \"$name\"", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }
        },
    )
}

/** Write the shortcut into the container AND register the hub entry the home carousel reads. */
private fun linkStoreGame(
    context: Context,
    container: Container,
    exeFile: File,
    name: String,
    appId: Int?,
    coverPath: String?,
    store: String?,
    extras: Map<String, String> = emptyMap(),
): HubGame {
    val desktop = ExeShortcutImporter.addToShortcuts(context, container, exeFile, name, appId)
    if (extras.isNotEmpty()) {
        com.winhub.hernandez.container.Shortcut(container, desktop).apply {
            extras.forEach { (k, v) -> putExtra(k, v) }
            saveData()
        }
    }
    val game = HubGame(name, container.id, desktop.absolutePath, appId, coverPath, store)
    HubGamesStore.add(context, game)
    return game
}

/** The Home tab's Cronos path, lifted verbatim: a fresh container seeded by the New Container form. */
private suspend fun createDeckCronosContainer(context: Context, name: String): Container? {
    val manager = withContext(Dispatchers.IO) {
        ContainerManager(context).also { it.reloadContainers() }
    }
    val data = withContext(Dispatchers.IO) {
        runCatching {
            val cm = ContentsManager(context).also { it.syncContents() }
            val wineList =
                cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)
                    .map { ContentsManager.getEntryName(it) } +
                    cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_PROTON)
                        .map { ContentsManager.getEntryName(it) }
            val wine = wineList.firstOrNull()
                ?: manager.getContainers().firstOrNull()?.wineVersion
                ?: Container(0, manager).wineVersion
            ContainerDetailViewModel.buildFreshCreateData(context, name, wine, true)
        }.getOrNull()
    } ?: return null
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            manager.createContainerAsync(data, ContentsManager(context)) { created ->
                cont.resume(created)
            }
        }
    }
}

/** Store cover -> a local file the hub carousel can decode, or null when there is no art. */
private fun cacheStoreCover(context: Context, url: String?, key: String): String? {
    if (url.isNullOrBlank()) return null
    val dir = File(context.filesDir, "hub_covers").apply { mkdirs() }
    val out = File(dir, "store_${key.hashCode()}.jpg")
    if (out.isFile && out.length() > 0L) return out.absolutePath
    return runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
        out.takeIf { it.length() > 0L }?.absolutePath
    }.getOrNull()
}

@Composable
private fun DeckAddBusyDialog(text: String) {
    Dialog(onDismissRequest = {}) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
                .deckDialogNav()
                .padding(22.dp),
        ) {
            Text(
                text = text,
                color = DeckPalette.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
