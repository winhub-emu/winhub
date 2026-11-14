package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winhub.hernandez.R
import com.winhub.hernandez.XServerDisplayActivity
import com.winhub.hernandez.XrActivity
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.container.ContainerManager
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.contentdialog.GraphicsDriverConfigDialog
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.store.deckTileFocus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A game installed in its own dedicated (Cronos) container. */
private data class DeckCronosItem(
    val container: Container,
    val shortcut: Shortcut,
    val hub: HubGame?,
    val store: String,
    val gameDir: File?,
)

/**
 * Deck Mode's Storage screen: two controller-first sub-tabs. Cronos lists games that live in
 * their own dedicated container (cover, title, game size for store installs, container size) with
 * remove/uninstall actions; Containers is the classic container list (run / edit / duplicate /
 * remove / export / properties) rendered as DroidDeck cards.
 */
@Composable
internal fun DeckContainersScreen(onEditContainer: (Int) -> Unit, vm: ContainersViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val containers by vm.containers.collectAsState()
    val message by vm.message.collectAsState()
    val tabs = LocalDeckTabs.current
    val subtab = tabs.index
    var propertiesContainer by remember { mutableStateOf<Container?>(null) }
    var removeGameFor by remember { mutableStateOf<DeckCronosItem?>(null) }
    var removeContainerFor by remember { mutableStateOf<DeckCronosItem?>(null) }
    var cronos by remember { mutableStateOf<List<DeckCronosItem>>(emptyList()) }
    var cronosLoading by remember { mutableStateOf(true) }
    var cronosEpoch by remember { mutableStateOf(0) }
    // The shell owns the page row: L1/R1 step these sub-tabs, so the pills never take D-pad focus.
    LaunchedEffect(tabs) { tabs.bind(2) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.messageShown()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(containers, cronosEpoch) {
        cronosLoading = true
        cronos = withContext(Dispatchers.IO) { loadDeckCronos(context, containers) }
        cronosLoading = false
    }
    // Cronos containers are only reachable through the Cronos tab.
    val plainContainers = remember(containers, cronos) {
        val cronosIds = cronos.map { it.container.id }.toSet()
        containers.filter { it.id !in cronosIds }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .deckPageBackground(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text(
                text = "Storage",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (subtab == 0) "${cronos.size} games on their own container"
                else "${plainContainers.size} installed",
                fontSize = 13.sp,
                color = DeckPalette.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            DeckRailLayout(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                rail = {
                    if (LocalDeckPortrait.current) {
                        DeckPortraitChips(listOf("Cronos", "Containers"), subtab) { tabs.select(it) }
                    } else {
                        DeckEditorCategoryRail(
                            labels = listOf("Cronos", "Containers"),
                            selected = subtab,
                            onSelect = { tabs.select(it) },
                            modifier = Modifier.width(200.dp).fillMaxHeight(),
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                },
            ) { bodyModifier ->
            AnimatedContent(
                targetState = subtab,
                transitionSpec = {
                    val forward = targetState >= initialState
                    (fadeIn(tween(300, delayMillis = 60, easing = DeckEase)) +
                        slideInHorizontally(tween(420, delayMillis = 60, easing = DeckEase)) {
                            if (forward) it / 24 else -it / 24
                        })
                        .togetherWith(
                            fadeOut(tween(170, easing = DeckEase)) +
                                slideOutHorizontally(tween(170, easing = DeckEase)) {
                                    if (forward) -it / 40 else it / 40
                                },
                        )
                },
                modifier = bodyModifier.focusGroup(),
                label = "storageTab",
            ) { which ->
                if (which == 0) {
                    DeckCronosTab(
                        games = cronos,
                        loading = cronosLoading,
                        onOpen = { onEditContainer(it.container.id) },
                        onRemoveGame = { removeGameFor = it },
                        onRemoveContainer = { removeContainerFor = it },
                    )
                } else {
                    DeckStorageContainersTab(
                        containers = plainContainers,
                        onNew = { onEditContainer(-1) },
                        onDefaults = { onEditContainer(-2) },
                        onEdit = onEditContainer,
                        onRun = { container ->
                            if (!XrActivity.isEnabled(context)) {
                                val intent = Intent(context, XServerDisplayActivity::class.java)
                                intent.putExtra("container_id", container.id)
                                if (container.displayBackend == Container.DISPLAY_BACKEND_WAYLAND) {
                                    intent.putExtra("wayland_mode", true)
                                }
                                context.startActivity(intent)
                            } else {
                                XrActivity.openIntent(activity, container.id, null)
                            }
                        },
                        onDuplicate = { vm.duplicate(it) {} },
                        onRemove = { vm.remove(it, context) {} },
                        onExport = { container ->
                            vm.exportContainer(container) { path ->
                                val msg = if (path != null) "Exported to $path"
                                else "Export failed or already exists"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        onInfo = { propertiesContainer = it },
                    )
                }
            }
            }
        }
    }

    propertiesContainer?.let { current ->
        DeckPropertiesDialog(container = current, onDismiss = { propertiesContainer = null })
    }

    removeGameFor?.let { item ->
        val store = item.store.isNotEmpty()
        DeckConfirmDialog(
            title = if (store) "Uninstall game?" else "Remove game?",
            message = if (store)
                "\"${item.shortcut.name}\" and its installed files will be deleted. Its container stays."
            else
                "The shortcut and hub entry for \"${item.shortcut.name}\" will be removed. Its container stays.",
            confirmLabel = if (store) "Uninstall" else "Remove",
            danger = store,
            onDismiss = { removeGameFor = null },
            onConfirm = {
                removeGameFor = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        HubGamesStore.remove(context, item.hub ?: deckHubGameOf(item))
                        runCatching { item.shortcut.file.delete() }
                        runCatching { File(item.shortcut.file.path.substringBeforeLast('.') + ".lnk").delete() }
                        if (store) item.gameDir?.let { dir ->
                            if (dir.isDirectory && deckIsStoreInstallDir(dir)) {
                                runCatching { FileUtils.delete(dir) }
                            }
                        }
                    }
                    cronosEpoch++
                    vm.refresh()
                }
            },
        )
    }

    removeContainerFor?.let { item ->
        DeckConfirmDialog(
            title = "Remove container?",
            message = "The container \"${item.container.name}\" and everything in it will be deleted.",
            confirmLabel = "Remove Container",
            danger = true,
            onDismiss = { removeContainerFor = null },
            onConfirm = {
                removeContainerFor = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        HubGamesStore.remove(context, item.hub ?: deckHubGameOf(item))
                    }
                    vm.remove(item.container, context) { cronosEpoch++ }
                }
            },
        )
    }
}

/** The Cronos sub-tab: dedicated-container games, biggest UI cards in the Storage screen. */
@Composable
private fun DeckCronosTab(
    games: List<DeckCronosItem>,
    loading: Boolean,
    onOpen: (DeckCronosItem) -> Unit,
    onRemoveGame: (DeckCronosItem) -> Unit,
    onRemoveContainer: (DeckCronosItem) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DeckPalette.primary)
        }
        games.isEmpty() -> Text(
            text = "No Cronos games yet. Add one game per container from Home and it will show here.",
            fontSize = 14.sp,
            color = DeckPalette.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        else -> DeckDragFrame(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(games, key = { it.shortcut.file.absolutePath }) { item ->
                    Box(Modifier.deckDragItem(item.shortcut.file.absolutePath, selfOnly = true)) {
                        DeckCronosCard(
                            item = item,
                            onOpen = { onOpen(item) },
                            onRemoveGame = { onRemoveGame(item) },
                            onRemoveContainer = { onRemoveContainer(item) },
                        )
                    }
                }
            }
        }
    }
}

/** The Containers sub-tab: the classic container list, unchanged actions. */
@Composable
private fun DeckStorageContainersTab(
    containers: List<Container>,
    onNew: () -> Unit,
    onDefaults: () -> Unit,
    onEdit: (Int) -> Unit,
    onRun: (Container) -> Unit,
    onDuplicate: (Container) -> Unit,
    onRemove: (Container) -> Unit,
    onExport: (Container) -> Unit,
    onInfo: (Container) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeckPalette.primary)
                    .clickable { onNew() }
                    .padding(horizontal = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = DeckPalette.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "New",
                        color = DeckPalette.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeckPalette.surface)
                    .border(1.dp, DeckPalette.line, RoundedCornerShape(12.dp))
                    .clickable { onDefaults() }
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Defaults",
                    color = DeckPalette.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (containers.isEmpty()) {
            Text(
                text = "No containers yet.",
                fontSize = 15.sp,
                color = DeckPalette.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            DeckDragFrame(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(containers, key = { it.id }) { container ->
                        Box(Modifier.deckDragItem("container-${container.id}", selfOnly = true)) {
                            DeckContainerCard(
                                name = container.name,
                                subtitle = listOf(container.wineVersion, container.screenSize)
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" · "),
                                chips = deckContainerChips(container),
                                onRun = { onRun(container) },
                                onEdit = { onEdit(container.id) },
                                onDuplicate = { onDuplicate(container) },
                                onRemove = { onRemove(container) },
                                onExport = { onExport(container) },
                                onInfo = { onInfo(container) },
                                onBackupRestore = { /* Backup/Restore opens a fork flow in the normal tab */ },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Graphics driver, DXVK, VKD3D and x86 backend of a container, each in its own colour (same as the game cards). */
private fun deckContainerChips(container: Container): List<Pair<String, Color>> = buildList {
    val driver = GraphicsDriverConfigDialog.getVersion(container.getGraphicsDriverConfig().orEmpty())
        ?.takeIf { it.isNotBlank() } ?: container.getGraphicsDriver()
    if (!driver.isNullOrBlank()) add("Driver $driver" to ChipDriverColor)
    val (dxvk, vkd3d) = parseDxwrapperConfig(container.getDXWrapperConfig().orEmpty())
    if (dxvk.isNotEmpty()) {
        val name = if (container.getDXWrapper().orEmpty().contains("vegas")) "Vegas" else "DXVK"
        add("$name $dxvk" to ChipDxvkColor)
    }
    if (vkd3d.isNotEmpty() && !vkd3d.equals("none", ignoreCase = true)) add("VKD3D $vkd3d" to ChipVkd3dColor)
    val emulator = container.getEmulator().orEmpty()
    val fexVersion = container.getFEXCoreVersion()
    if (emulator.equals("fexcore", ignoreCase = true) && !fexVersion.isNullOrBlank()) {
        add("FEX $fexVersion" to ChipCpuColor)
    } else if (emulator.isNotEmpty()) {
        add(emulator to ChipCpuColor)
    }
}

/** Tinted pill for one component; 12sp so it stays readable from the couch. */
@Composable
private fun DeckSpecChip(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** One Cronos game: cover, title, store size + container size, remove/uninstall actions. */
@Composable
private fun DeckCronosCard(
    item: DeckCronosItem,
    onOpen: () -> Unit,
    onRemoveGame: () -> Unit,
    onRemoveContainer: () -> Unit,
) {
    val cover = rememberDeckCover(deckCoverPath(item))
    val containerSize = rememberDeckDirSize(item.container.rootDir)
    val gameSize = rememberDeckDirSize(item.gameDir)
    val store = item.store.isNotEmpty()
    val interaction = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeckPalette.groupShape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
            .padding(12.dp),
    ) {
        // Cover + details open the game; the remove buttons beside them are separate focus targets,
        // and each one rings on its own: the card never lights up for a button inside it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .deckOuterFocusStroke(interaction, RoundedCornerShape(10.dp))
                .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
        ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 84.dp, height = 112.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DeckPalette.surfaceVariant),
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = DeckPalette.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.shortcut.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = DeckPalette.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.container.name,
                fontSize = 12.sp,
                color = DeckPalette.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (store) {
                Text(
                    text = "Game  " + if (gameSize >= 0) StringUtils.formatBytes(gameSize) else "…",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeckPalette.onBackground,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = "Container  " + if (containerSize >= 0) StringUtils.formatBytes(containerSize) else "…",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeckPalette.onSurfaceVariant,
            )
        }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeckOutlineButton(if (store) "Uninstall Game" else "Remove Game", onRemoveGame)
            DeckDangerButton("Remove Container", onRemoveContainer)
        }
    }
}

@Composable
private fun DeckOutlineButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DeckPalette.surfaceVariant)
            .border(1.dp, DeckPalette.line2, RoundedCornerShape(12.dp))
            .deckOuterFocusStroke(interaction, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text = text,
            color = DeckPalette.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Red-tinted destructive action (Remove Container). */
@Composable
internal fun DeckDangerButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DeckPalette.error.copy(alpha = 0.12f))
            .border(1.dp, DeckPalette.error, RoundedCornerShape(12.dp))
            .deckOuterFocusStroke(interaction, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text = text,
            color = DeckPalette.error,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Deck-styled confirmation used by the destructive Cronos actions. */
@Composable
private fun DeckConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DeckConfigDialog(title = title, onDismiss = onDismiss) {
        DeckConfigNote(message)
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            DeckButton("Cancel", primary = false) { onDismiss() }
            Spacer(Modifier.width(8.dp))
            if (danger) DeckDangerButton(confirmLabel, onConfirm)
            else DeckButton(confirmLabel) { onConfirm() }
        }
    }
}

/**
 * Builds the Cronos list: containers created by the Cronos flow (tagged [Container.EXTRA_CRONOS])
 * that hold exactly one game shortcut. Containers made before the tag existed are recognised once
 * by the Cronos shape (one shortcut named like its container) and tagged, so they keep showing here.
 */
private fun loadDeckCronos(context: Context, containers: List<Container>): List<DeckCronosItem> {
    return runCatching {
        val manager = ContainerManager(context).also { it.reloadContainers() }
        val byContainer = LinkedHashMap<Int, MutableList<Shortcut>>()
        for (shortcut in manager.loadShortcuts()) {
            val container = shortcut.container ?: continue
            byContainer.getOrPut(container.id) { mutableListOf() }.add(shortcut)
        }
        val hubs = HubGamesStore.load(context).associateBy { it.shortcutPath }
        byContainer.values.mapNotNull { list ->
            val shortcut = list.singleOrNull() ?: return@mapNotNull null
            val container = shortcut.container ?: return@mapNotNull null
            val live = containers.firstOrNull { it.id == container.id } ?: container
            if (!live.isCronos) {
                if (!shortcut.name.equals(live.name, ignoreCase = true)) return@mapNotNull null
                live.putExtra(Container.EXTRA_CRONOS, "1")
                live.saveData()
            }
            val store = deckStoreSource(shortcut)
            val gameDir = if (store.isNotEmpty()) {
                runCatching {
                    WinePath.resolveAndroidPath(live, shortcut.path)
                        ?.let { deckGameRoot(it.parentFile ?: it) }
                }.getOrNull()
            } else null
            DeckCronosItem(
                container = live,
                shortcut = shortcut,
                hub = hubs[shortcut.file.absolutePath],
                store = store,
                gameDir = gameDir,
            )
        }.sortedBy { it.shortcut.name.lowercase() }
    }.getOrDefault(emptyList())
}

private fun deckHubGameOf(item: DeckCronosItem) = HubGame(
    name = item.shortcut.name,
    containerId = item.container.id,
    shortcutPath = item.shortcut.file.absolutePath,
    appId = null,
    coverPath = null,
)

/** Store tag from Extra Data, or the install path for legacy shortcuts written without one. */
private fun deckStoreSource(shortcut: Shortcut): String {
    val tagged = runCatching { shortcut.getExtra("storeSource", "") }.getOrNull().orEmpty()
    if (tagged.isNotEmpty() && tagged != "custom") return tagged
    val path = shortcut.path.lowercase()
    return when {
        path.contains("steam_games") || path.contains("steamapps") -> "steam"
        path.contains("epic_games") -> "epic"
        path.contains("gog_games") -> "gog"
        path.contains("amazon") -> "amazon"
        else -> ""
    }
}

/** Never delete anything that does not look like a store's own games folder. */
private fun deckIsStoreInstallDir(dir: File): Boolean {
    if (dir.name.isBlank() || dir.parentFile == null) return false
    val path = dir.absolutePath.lowercase()
    return listOf("steam_games", "steamapps", "epic_games", "gog_games", "/amazon/", "/steam/")
        .any { path.contains(it) }
}

/**
 * The install root of a store game: walk up until the direct parent is a store root
 * (steam_games / epic_games / gog_games / Amazon / steamapps common). Never guesses past it.
 */
private fun deckGameRoot(dir: File): File {
    var current = dir
    while (true) {
        val parent = current.parentFile ?: return dir
        val name = parent.name.lowercase()
        if (name == "steam_games" || name == "epic_games" || name == "gog_games" ||
            name == "amazon" || name == "common"
        ) return current
        current = parent
    }
}

/** The best cover available: user-picked art, hub art, then the container's saved cover. */
private fun deckCoverPath(item: DeckCronosItem): String? {
    item.shortcut.customCoverArtPath?.takeIf { it.isNotEmpty() && File(it).isFile }?.let { return it }
    item.hub?.coverPath?.takeIf { it.isNotEmpty() && File(it).isFile }?.let { return it }
    val fallback = File(item.container.rootDir, "app_data/cover_arts/${item.shortcut.name}.png")
    return fallback.takeIf { it.isFile }?.absolutePath
}

@Composable
private fun rememberDeckCover(path: String?): ImageBitmap? =
    remember(path) {
        path?.let { file ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 320) sample *= 2
                BitmapFactory.decodeFile(
                    file,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )?.asImageBitmap()
            }.getOrNull()
        }
    }

/** Directory size computed once off the main thread; -1 while unknown/absent. */
@Composable
private fun rememberDeckDirSize(dir: File?): Long {
    var size by remember(dir?.absolutePath) { mutableStateOf(-1L) }
    LaunchedEffect(dir?.absolutePath) {
        size = if (dir == null || !dir.exists()) -1L
        else withContext(Dispatchers.IO) { deckDirSize(dir) }
    }
    return size
}

private fun deckDirSize(dir: File): Long {
    var total = 0L
    val stack = ArrayDeque<File>()
    stack.addLast(dir)
    while (stack.isNotEmpty()) {
        val file = stack.removeLast()
        if (file.isDirectory) file.listFiles()?.forEach { stack.addLast(it) }
        else total += file.length()
    }
    return total
}

/** One container as a DroidDeck card: 14dp surface, name + subtitle, blue play pill, overflow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckContainerCard(
    name: String,
    subtitle: String,
    chips: List<Pair<String, Color>>,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit,
    onInfo: () -> Unit,
    onBackupRestore: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeckPalette.groupShape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // Same container glyph as the normal Containers tab; tinted so it follows the Deck theme.
        Icon(
            painter = painterResource(R.drawable.icon_menu_container),
            contentDescription = null,
            tint = DeckPalette.primary,
            modifier = Modifier.padding(end = 12.dp).size(28.dp),
        )

        // Only the name area opens the editor: a clickable on the whole card would swallow Play and
        // the actions button, which D-pad focus could then never reach. Each part rings on its own.
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .deckOuterFocusStroke(interaction, RoundedCornerShape(10.dp))
                .clickable(interactionSource = interaction, indication = null, onClick = onEdit)
                .padding(end = 12.dp),
        ) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeckPalette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = DeckPalette.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    chips.forEach { (text, color) -> DeckSpecChip(text, color) }
                }
            }
        }

        // Run: the blue play pill (DroidDeck's PrimaryButton shape).
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 52.dp, height = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DeckPalette.primary)
                .clickable { onRun() },
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Run",
                tint = DeckPalette.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        Box {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { menuOpen = true },
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Actions",
                    tint = DeckPalette.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            // A dialog, not a dropdown: a popup window has no focus owner, so the D-pad could never walk its items.
            if (menuOpen) Dialog(
                onDismissRequest = { menuOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .heightIn(max = 320.dp)
                        .clip(DeckPalette.groupShape)
                        .background(DeckPalette.surfaceVariant)
                        .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
                        .deckDialogNav { menuOpen = false }
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    DeckMenuItem("Edit", Icons.Filled.Edit) { menuOpen = false; onEdit() }
                    DeckMenuItem("Duplicate", Icons.Filled.ContentCopy) { menuOpen = false; onDuplicate() }
                    DeckMenuItem("Remove", Icons.Filled.Delete) { menuOpen = false; onRemove() }
                    DeckMenuItem("Export", Icons.Filled.FileUpload) { menuOpen = false; onExport() }
                    DeckMenuItem("Backup / Restore save", Icons.Filled.SettingsBackupRestore) { menuOpen = false; onBackupRestore() }
                    DeckMenuItem("Properties", Icons.Filled.Info) { menuOpen = false; onInfo() }
                }
            }
        }
    }
}

@Composable
private fun DeckMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = DeckPalette.onBackground,
                fontSize = 14.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DeckPalette.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun DeckPropRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.5.sp,
            color = DeckPalette.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DeckPalette.onBackground,
        )
    }
}

/** DroidDeck-styled Properties sheet: identity header + live storage usage + Clear Cache. */
@Composable
internal fun DeckPropertiesDialog(container: Container, onDismiss: () -> Unit) {
    var driveCSize by remember { mutableStateOf(0L) }
    var cacheSize by remember { mutableStateOf(0L) }
    var cacheEpoch by remember { mutableStateOf(0) }
    val internalStorageSize = remember { FileUtils.getInternalStorageSize() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val totalSize = driveCSize + cacheSize
    val progress = if (internalStorageSize > 0)
        (totalSize.toFloat() / internalStorageSize).coerceIn(0f, 1f)
    else 0f

    LaunchedEffect(container, cacheEpoch) {
        driveCSize = 0
        cacheSize = 0
        FileUtils.getSizeAsync(File(container.rootDir, ".wine/drive_c")) { size ->
            handler.post { driveCSize += size }
        }
        FileUtils.getSizeAsync(File(container.rootDir, ".cache")) { size ->
            handler.post { cacheSize += size }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
                .deckDialogNav(onDismiss)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Properties",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckPalette.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = container.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DeckPalette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(container.wineVersion, container.screenSize)
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    fontSize = 12.5.sp,
                    color = DeckPalette.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DeckPalette.line),
                )
                Spacer(Modifier.height(4.dp))

                DeckPropRow("Drive C", StringUtils.formatBytes(driveCSize))
                DeckPropRow("Cache", StringUtils.formatBytes(cacheSize))
                DeckPropRow("Total", StringUtils.formatBytes(totalSize))

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DeckPalette.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(DeckPalette.primary),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeckPalette.onBackground,
                    )
                }
                Text(
                    text = "Estimated used space",
                    fontSize = 11.sp,
                    color = DeckPalette.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DeckButton("Clear Cache", primary = false) {
                        FileUtils.clear(File(container.rootDir, ".cache"))
                        container.putExtra("desktopTheme", null)
                        container.saveData()
                        cacheEpoch++
                    }
                    Spacer(Modifier.weight(1f))
                    DeckButton("Close", primary = true) { onDismiss() }
                }
            }
        }
    }
}
