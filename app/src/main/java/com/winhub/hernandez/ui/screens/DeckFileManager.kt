@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.winhub.hernandez.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.core.PeIconExtractor
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.ui.components.RailItem
import com.winhub.hernandez.ui.components.RailSection
import com.winhub.hernandez.ui.components.clickable
import com.winhub.hernandez.util.FavoritesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The Deck file manager: a locations panel (landscape) or tab strip (portrait), a header card with
 * breadcrumbs + tools, bars for selection / paste / progress, and a card list or tile grid. It is
 * pure UI over a snapshot ([DeckFmUi]) and callbacks ([DeckFmActions]); every behaviour (listing,
 * copy/paste, run, favourites, ...) stays in FileManagerScreen so the classic screen and this one
 * can never drift apart.
 *
 * Pad: A opens / toggles, X opens the item menu, Y toggles the filter, B goes up (wired by the host
 * through LocalDeckBack). Items ring through the shared DeckDragFrame; the touch-only buttons are
 * not focus stops, so the D-pad walks items, not button columns.
 */

/** Everything the screen draws, rebuilt on each recomposition of the host. */
internal class DeckFmUi(
    val pickMode: Boolean,
    val pickDirMode: Boolean,
    val pickerTitle: String?,
    val currentDir: File,
    val currentRoot: File,
    val entries: List<File>,
    val shown: List<File>,
    val loading: Boolean,
    val unreadable: Boolean,
    val sections: List<RailSection>,
    val showFavorites: Boolean,
    val favTick: Int,
    val containers: List<Container>,
    val imagefsDir: File,
    val driveLabel: String,
    val freeSpace: Long,
    val totalSpace: Long,
    val searchQuery: String,
    val showSearch: Boolean,
    val gridView: Boolean,
    val compactRows: Boolean,
    val selectionMode: Boolean,
    val selectedPaths: Set<String>,
    val clipboard: List<File>,
    val isCut: Boolean,
    val busy: Boolean,
    val busyLabel: String,
    val busyDeterminate: Boolean,
    val busyProgress: Float,
    val cancellable: Boolean,
    val listState: LazyListState,
    val pullState: PullToRefreshState,
)

internal class DeckFmActions(
    val goUp: () -> Unit,
    val open: (File) -> Unit,
    val tap: (File) -> Unit,
    val longPress: (File) -> Unit,
    val openMenu: (File) -> Unit,
    val toggleSelectAll: () -> Unit,
    val copySelected: () -> Unit,
    val cutSelected: () -> Unit,
    val deleteSelected: () -> Unit,
    val endSelection: () -> Unit,
    val paste: () -> Unit,
    val clearClipboard: () -> Unit,
    val cancelOperation: () -> Unit,
    val newFolder: () -> Unit,
    val setSearch: (String) -> Unit,
    val toggleSearch: () -> Unit,
    val toggleGrid: () -> Unit,
    val openOptions: () -> Unit,
    val openFavorites: () -> Unit,
    val closeFavorites: () -> Unit,
    val pinCurrent: () -> Unit,
    val jumpTo: (File) -> Unit,
    val unpin: (File) -> Unit,
    val refresh: () -> Unit,
    val pickHere: () -> Unit,
)

// ── File kinds ──

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
private val ARCHIVE_EXTS = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso", "wcp", "cab")
private val MEDIA_EXTS = setOf("mp3", "wav", "ogg", "flac", "mp4", "mkv", "avi", "webm", "mov")
private val TEXT_EXTS = setOf("txt", "log", "ini", "cfg", "conf", "json", "xml", "md", "csv", "reg", "yml", "yaml")

private class FmKind(val icon: ImageVector, val tint: Color)

private fun isRunnableName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".exe") || n.endsWith(".bat") || n.endsWith(".msi") || n.endsWith(".sh")
}

@Composable
private fun fmKind(file: File): FmKind {
    val ext = file.extension.lowercase()
    return when {
        file.isDirectory -> FmKind(Icons.Filled.Folder, DeckPalette.primary)
        isRunnableName(file.name) -> FmKind(Icons.Filled.SportsEsports, ChipDxvkColor)
        ext in ARCHIVE_EXTS -> FmKind(Icons.Filled.Unarchive, ChipDriverColor)
        ext in IMAGE_EXTS -> FmKind(Icons.Filled.Image, ChipVkd3dColor)
        ext in MEDIA_EXTS -> FmKind(Icons.Filled.Movie, ChipFgColor)
        ext in TEXT_EXTS -> FmKind(Icons.Filled.Description, ChipRendColor)
        else -> FmKind(Icons.Filled.InsertDriveFile, DeckPalette.onSurfaceVariant)
    }
}

/** Type-tinted tile with the file's own art: exe icon, image thumbnail, else a kind glyph. */
@Composable
private fun FmThumb(file: File, modifier: Modifier, corner: Dp, iconSize: Dp) {
    val kind = fmKind(file)
    val isImage = !file.isDirectory && file.extension.lowercase() in IMAGE_EXTS
    val isExe = !file.isDirectory && file.name.lowercase().endsWith(".exe")
    var exeIcon by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    if (isExe) {
        LaunchedEffect(file.absolutePath) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(file) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(corner)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(kind.tint.copy(alpha = 0.16f)),
    ) {
        val icon = exeIcon
        when {
            icon != null -> Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(iconSize))
            isImage -> AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Filled.Image),
                error = rememberVectorPainter(Icons.Filled.Image),
                modifier = Modifier.fillMaxSize(),
            )
            else -> Icon(kind.icon, contentDescription = null, tint = kind.tint, modifier = Modifier.size(iconSize))
        }
    }
}

// ── Entry point ──

@Composable
internal fun DeckFileManagerContent(ui: DeckFmUi, act: DeckFmActions) {
    val portrait = LocalDeckPortrait.current
    val editTracker = LocalDeckEditTracker.current
    // The item the pad last landed on, so X can open its menu. Not state: only the key handler reads it.
    val focusedFile = remember { arrayOfNulls<File>(1) }
    val onFocused: (File) -> Unit = { focusedFile[0] = it }

    DeckDragFrame(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || editTracker.editing) return@onPreviewKeyEvent false
                when (e.key) {
                    // X = options for the focused item (unpin in the Favorites view).
                    Key.ButtonX -> {
                        val f = focusedFile[0] ?: return@onPreviewKeyEvent false
                        when {
                            ui.showFavorites -> { act.unpin(f); true }
                            !ui.pickMode && f in ui.shown -> { act.openMenu(f); true }
                            else -> false
                        }
                    }
                    // Y = filter this folder.
                    Key.ButtonY -> if (ui.showFavorites) false else { act.toggleSearch(); true }
                    else -> false
                }
            },
        shape = RoundedCornerShape(14.dp),
    ) {
        if (portrait) {
            Column(Modifier.fillMaxSize()) {
                if (!ui.pickMode) PortraitScreenTitle("Files")
                FmPickerTitle(ui)
                FmLocationStrip(ui, act)
                FmMain(
                    ui, act, portrait = true, onFocused = onFocused,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                FmPickerTitle(ui)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // Room under the list for the shell's controller hint bar.
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (ui.pickMode) 12.dp else 40.dp),
                ) {
                    FmLocationsPanel(ui, act, Modifier.width(224.dp).fillMaxHeight())
                    Spacer(Modifier.width(14.dp))
                    FmMain(ui, act, portrait = false, onFocused = onFocused, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun FmPickerTitle(ui: DeckFmUi) {
    val title = ui.pickerTitle
    if (!ui.pickMode || title.isNullOrEmpty()) return
    Text(
        text = title,
        color = DeckPalette.onBackground,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 2.dp),
    )
}

// ── Locations ──

/** Landscape: Favorites + every storage / quick / pinned location as a tall, icon-led list. */
@Composable
private fun FmLocationsPanel(ui: DeckFmUi, act: DeckFmActions, modifier: Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, shape)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Files",
            color = DeckPalette.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        )
        FmLocationRow(
            RailItem("Favorites", Icons.Filled.Star, ui.showFavorites) { act.openFavorites() },
        )
        ui.sections.forEach { section ->
            val header = section.header?.removePrefix("★ ")
            if (header != null) {
                Text(
                    text = header,
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 2.dp),
                )
            }
            section.items.forEach { FmLocationRow(it) }
        }
    }
}

@Composable
private fun FmLocationRow(item: RailItem) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(
        targetValue = if (item.selected) DeckPalette.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(200),
        label = "fmLocBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .deckDragItem("fm-loc-${item.label}", selfOnly = true)
            .deckBringIntoView()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (item.selected) DeckPalette.primary else DeckPalette.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.label,
            color = if (item.selected) DeckPalette.onBackground else DeckPalette.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Portrait: the same locations as icon tabs across the top. */
@Composable
private fun FmLocationStrip(ui: DeckFmUi, act: DeckFmActions) {
    val all = buildList {
        add(RailItem("Favorites", Icons.Filled.Star, ui.showFavorites) { act.openFavorites() })
        ui.sections.forEach { addAll(it.items) }
    }
    if (all.size < 2) return
    DeckPortraitTabStrip(
        labels = all.map { it.label },
        icons = all.map { it.icon },
        selected = all.indexOfFirst { it.selected }.coerceAtLeast(0),
        onSelect = { all.getOrNull(it)?.onClick?.invoke() },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ── Main column ──

@Composable
private fun FmMain(
    ui: DeckFmUi,
    act: DeckFmActions,
    portrait: Boolean,
    onFocused: (File) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FmHeader(ui, act, portrait)
        if (ui.showSearch && !ui.showFavorites) {
            DeckConfigTextField(
                label = "Filter this folder",
                value = ui.searchQuery,
                onValueChange = act.setSearch,
            )
        }
        if (ui.pickDirMode) FmPickDirBar(ui, act)
        if (ui.busy || ui.selectionMode || ui.clipboard.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ui.busy) FmProgressBar(ui, act)
                if (ui.selectionMode) FmSelectionBar(ui, act)
                if (ui.clipboard.isNotEmpty()) FmPasteBar(ui, act)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.showFavorites -> FmFavorites(ui, act, onFocused)
                else -> FmDirectory(ui, act, portrait, onFocused)
            }
        }
    }
}

// ── Header ──

@Composable
private fun FmCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, shape),
    ) { content() }
}

@Composable
private fun FmHeader(ui: DeckFmUi, act: DeckFmActions, portrait: Boolean) {
    FmCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FmIconButton(
                    icon = Icons.Filled.ArrowUpward,
                    description = "Parent folder",
                    enabled = !ui.showFavorites && ui.currentDir != ui.currentRoot,
                    onClick = act.goUp,
                )
                Spacer(Modifier.width(8.dp))
                if (ui.showFavorites) {
                    Text(
                        text = "Favorites",
                        color = DeckPalette.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                } else {
                    FmBreadcrumbs(ui, act, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            FmToolbar(ui, act, portrait)
            if (!ui.showFavorites) {
                Spacer(Modifier.height(8.dp))
                FmStorageMeter(ui)
            }
        }
    }
}

@Composable
private fun FmBreadcrumbs(ui: DeckFmUi, act: DeckFmActions, modifier: Modifier) {
    val crumbs = remember(ui.currentDir.absolutePath, ui.currentRoot.absolutePath) {
        val list = mutableListOf<File>()
        var cur: File? = ui.currentDir
        while (cur != null) {
            list.add(cur)
            if (cur.absolutePath == ui.currentRoot.absolutePath) break
            cur = cur.parentFile
        }
        list.reversed()
    }
    val scroll = rememberScrollState()
    // The deepest folder is the one that matters: keep the tail of a long path in view.
    LaunchedEffect(crumbs) {
        androidx.compose.runtime.withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(scroll),
    ) {
        crumbs.forEachIndexed { i, dir ->
            val last = i == crumbs.lastIndex
            if (i > 0) {
                Icon(
                    Icons.Filled.ChevronRight, contentDescription = null,
                    tint = DeckPalette.onSurfaceVariant, modifier = Modifier.size(18.dp),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (last) DeckPalette.primary.copy(alpha = 0.16f) else Color.Transparent)
                    // Touch-only jump targets: the pad goes up with B / the arrow, one level at a time.
                    .then(
                        if (last) Modifier
                        else Modifier.focusProperties { canFocus = false }.clickable { act.open(dir) },
                    )
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = if (i == 0) ui.driveLabel else dir.name.ifBlank { dir.absolutePath },
                    color = if (last) DeckPalette.onBackground else DeckPalette.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FmToolbar(ui: DeckFmUi, act: DeckFmActions, portrait: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (portrait && !ui.showFavorites) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!ui.showFavorites) {
            FmIconButton(Icons.Filled.Search, "Filter", active = ui.showSearch, onClick = act.toggleSearch)
            FmIconButton(
                if (ui.gridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                if (ui.gridView) "List view" else "Grid view",
                onClick = act.toggleGrid,
            )
            if (!ui.pickMode) FmIconButton(Icons.Filled.CreateNewFolder, "New folder", onClick = act.newFolder)
            FmIconButton(Icons.Filled.Tune, "Sort and view options", onClick = act.openOptions)
        }
        FmIconButton(
            icon = if (ui.showFavorites) Icons.Filled.Star else Icons.Filled.StarBorder,
            description = if (ui.showFavorites) "Close favorites" else "Open favorites",
            active = ui.showFavorites,
            onClick = if (ui.showFavorites) act.closeFavorites else act.openFavorites,
        )
    }
}

@Composable
private fun FmStorageMeter(ui: DeckFmUi) {
    val folders = remember(ui.entries) { ui.entries.count { it.isDirectory } }
    val files = ui.entries.size - folders
    val used = if (ui.totalSpace > 0) ((ui.totalSpace - ui.freeSpace).toFloat() / ui.totalSpace).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$folders folder${if (folders == 1) "" else "s"} · $files file${if (files == 1) "" else "s"}",
            color = DeckPalette.onSurfaceVariant,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (ui.freeSpace > 0) {
            Text(
                text = "${StringUtils.formatBytes(ui.freeSpace)} free",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 12.5.sp,
                maxLines = 1,
            )
        }
    }
    if (ui.totalSpace > 0) {
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(DeckPalette.line),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(used)
                    .fillMaxHeight()
                    .background(if (used > 0.92f) DeckPalette.error else DeckPalette.primary),
            )
        }
    }
}

@Composable
private fun FmIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) DeckPalette.primary.copy(alpha = 0.18f) else DeckPalette.surfaceVariant.copy(alpha = 0.55f))
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier.focusProperties { canFocus = false },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !enabled -> DeckPalette.onSurfaceVariant.copy(alpha = 0.4f)
                active -> DeckPalette.primary
                else -> DeckPalette.onBackground
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

// ── Bars ──

@Composable
private fun FmBar(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.primary.copy(alpha = 0.12f))
            .border(1.dp, DeckPalette.primary.copy(alpha = 0.5f), shape)
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun FmPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    danger: Boolean = false,
) {
    val fg = when {
        !enabled -> DeckPalette.onSurfaceVariant.copy(alpha = 0.45f)
        primary -> DeckPalette.onPrimary
        danger -> DeckPalette.error
        else -> DeckPalette.onBackground
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(
                when {
                    primary && enabled -> DeckPalette.primary
                    danger && enabled -> DeckPalette.error.copy(alpha = 0.12f)
                    else -> DeckPalette.surfaceVariant
                },
            )
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier.focusProperties { canFocus = false },
            )
            .padding(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun FmSelectionBar(ui: DeckFmUi, act: DeckFmActions) {
    val any = ui.selectedPaths.isNotEmpty()
    val all = any && ui.selectedPaths.size == ui.entries.size
    FmBar {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${ui.selectedPaths.size} selected",
                color = DeckPalette.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FmPill(if (all) "None" else "All", Icons.Filled.Checklist, act.toggleSelectAll)
                FmPill("Copy", Icons.Filled.FileCopy, act.copySelected, enabled = any)
                FmPill("Cut", Icons.Filled.ContentCut, act.cutSelected, enabled = any)
                FmPill("Delete", Icons.Filled.Delete, act.deleteSelected, enabled = any, danger = true)
                FmPill("Done", Icons.Filled.Check, act.endSelection, primary = true)
            }
        }
    }
}

@Composable
private fun FmPasteBar(ui: DeckFmUi, act: DeckFmActions) {
    val what = if (ui.clipboard.size == 1) ui.clipboard.first().name else "${ui.clipboard.size} items"
    FmBar {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${if (ui.isCut) "Move" else "Copy"} $what here?",
                color = DeckPalette.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FmPill(if (ui.isCut) "Move here" else "Paste here", Icons.Filled.ContentPaste, act.paste, enabled = !ui.busy, primary = true)
                FmPill("Cancel", Icons.Filled.Close, act.clearClipboard)
            }
        }
    }
}

@Composable
private fun FmProgressBar(ui: DeckFmUi, act: DeckFmActions) {
    FmBar {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ui.busyLabel + if (ui.busyDeterminate) "  ${(ui.busyProgress * 100).toInt()}%" else "",
                    color = DeckPalette.onBackground,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // A multi-gigabyte copy onto a slow card is exactly when you pick the wrong folder.
                if (ui.cancellable) {
                    Spacer(Modifier.width(8.dp))
                    FmPill("Cancel", Icons.Filled.Close, act.cancelOperation)
                }
            }
            if (ui.busyDeterminate) {
                LinearProgressIndicator(
                    progress = { ui.busyProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = DeckPalette.primary,
                    trackColor = DeckPalette.line,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = DeckPalette.primary,
                    trackColor = DeckPalette.line,
                )
            }
        }
    }
}

@Composable
private fun FmPickDirBar(ui: DeckFmUi, act: DeckFmActions) {
    DeckButton("Select this folder", modifier = Modifier.fillMaxWidth(), onClick = act.pickHere)
}

// ── Directory content ──

@Composable
private fun FmDirectory(ui: DeckFmUi, act: DeckFmActions, portrait: Boolean, onFocused: (File) -> Unit) {
    val atFloor = ui.currentDir == ui.currentRoot
    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(ui.pullState.nestedScrollConnection),
    ) {
        when {
            ui.loading && ui.entries.isEmpty() -> FmStateView(
                icon = Icons.Filled.FolderOpen, title = "Opening folder…", body = "", loading = true,
            )
            ui.unreadable -> FmStateView(
                icon = Icons.Filled.Lock,
                title = "Can't read this folder",
                body = "Android didn't let the app list it. Try another location.",
                actionLabel = if (atFloor) "Try again" else "Go up",
                onAction = if (atFloor) act.refresh else act.goUp,
            )
            ui.entries.isEmpty() -> FmStateView(
                icon = Icons.Filled.FolderOpen,
                title = "Empty folder",
                body = if (ui.pickMode) "Nothing to pick here." else "Copy something in, or create a new folder.",
                actionLabel = if (atFloor) null else "Go up",
                onAction = act.goUp,
            )
            ui.shown.isEmpty() -> FmStateView(
                icon = Icons.Filled.Search,
                title = "No matches",
                body = "Nothing here matches \"${ui.searchQuery}\".",
            )
            ui.gridView -> FmGrid(ui, act, portrait, onFocused)
            else -> FmList(ui, act, onFocused)
        }
        // material3 1.2.0's PullToRefreshContainer draws its indicator even at rest.
        if (ui.pullState.verticalOffset > 0.5f || ui.pullState.isRefreshing) {
            PullToRefreshContainer(state = ui.pullState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun FmList(ui: DeckFmUi, act: DeckFmActions, onFocused: (File) -> Unit) {
    LazyColumn(
        state = ui.listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (ui.searchQuery.isBlank() && ui.currentDir != ui.currentRoot) {
            item(key = "..") { FmParentRow(act.goUp) }
        }
        items(ui.shown, key = { it.absolutePath }) { file ->
            FmRow(file, ui, act, onFocused)
        }
    }
}

@Composable
private fun FmGrid(ui: DeckFmUi, act: DeckFmActions, portrait: Boolean, onFocused: (File) -> Unit) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(ui.currentDir.absolutePath) { gridState.scrollToItem(0) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = if (portrait) 140.dp else 150.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (ui.searchQuery.isBlank() && ui.currentDir != ui.currentRoot) {
            item(key = "..") { FmParentTile(act.goUp) }
        }
        items(ui.shown, key = { it.absolutePath }) { file ->
            FmTile(file, ui, act, onFocused)
        }
    }
}

@Composable
private fun FmParentRow(onTap: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.surface.copy(alpha = 0.6f))
            .border(1.dp, DeckPalette.line, shape)
            .deckDragItem("fm-parent-row", selfOnly = true)
            .deckBringIntoView()
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(DeckPalette.surfaceVariant),
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("..", color = DeckPalette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Parent folder", color = DeckPalette.onSurfaceVariant, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun FmParentTile(onTap: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.surface.copy(alpha = 0.6f))
            .border(1.dp, DeckPalette.line, shape)
            .deckDragItem("fm-parent-tile", selfOnly = true)
            .deckBringIntoView()
            .clickable(onClick = onTap)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Parent folder",
            color = DeckPalette.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** Round check that is never a focus stop: the whole card is the control. */
@Composable
private fun FmCheck(checked: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) DeckPalette.primary else Color.Transparent)
            .border(2.dp, if (checked) DeckPalette.primary else DeckPalette.onSurfaceVariant, CircleShape),
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = DeckPalette.onPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun FmRow(file: File, ui: DeckFmUi, act: DeckFmActions, onFocused: (File) -> Unit) {
    val context = LocalContext.current
    val selected = file.absolutePath in ui.selectedPaths
    val isDir = file.isDirectory
    val shape = RoundedCornerShape(16.dp)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }
    val fav = remember(file.absolutePath, ui.favTick) { isDir && FavoritesStore.isFavorite(context, file.absolutePath) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) DeckPalette.primary.copy(alpha = 0.18f) else DeckPalette.surface)
            .border(1.dp, if (selected) DeckPalette.primary else DeckPalette.line, shape)
            .deckDragItem("fm-row-${file.absolutePath}", selfOnly = true)
            .deckBringIntoView()
            .onFocusChanged { if (it.isFocused) onFocused(file) }
            .combinedClickable(onClick = { act.tap(file) }, onLongClick = { act.longPress(file) })
            .padding(horizontal = 12.dp, vertical = if (ui.compactRows) 6.dp else 10.dp),
    ) {
        if (ui.selectionMode) {
            FmCheck(selected)
            Spacer(Modifier.width(10.dp))
        }
        FmThumb(
            file,
            modifier = Modifier.size(if (ui.compactRows) 36.dp else 46.dp),
            corner = 12.dp,
            iconSize = if (ui.compactRows) 22.dp else 28.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = DeckPalette.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!ui.compactRows) {
                Text(
                    text = (if (isDir) "Folder" else StringUtils.formatBytes(file.length())) +
                        "  ·  " + dateFormat.format(Date(file.lastModified())),
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (fav) {
            Icon(
                Icons.Filled.Star, contentDescription = "Favorite",
                tint = DeckPalette.primary, modifier = Modifier.padding(horizontal = 4.dp).size(18.dp),
            )
        }
        if (!ui.selectionMode && !ui.pickMode) {
            // Touch affordance for the menu; the pad uses X, so this is not a focus stop.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .focusProperties { canFocus = false }
                    .clickable { act.openMenu(file) },
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Actions", tint = DeckPalette.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        } else if (isDir) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DeckPalette.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FmTile(file: File, ui: DeckFmUi, act: DeckFmActions, onFocused: (File) -> Unit) {
    val context = LocalContext.current
    val selected = file.absolutePath in ui.selectedPaths
    val isDir = file.isDirectory
    val shape = RoundedCornerShape(16.dp)
    val fav = remember(file.absolutePath, ui.favTick) { isDir && FavoritesStore.isFavorite(context, file.absolutePath) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) DeckPalette.primary.copy(alpha = 0.18f) else DeckPalette.surface)
            .border(1.dp, if (selected) DeckPalette.primary else DeckPalette.line, shape)
            .deckDragItem("fm-grid-${file.absolutePath}", selfOnly = true)
            .deckBringIntoView()
            .onFocusChanged { if (it.isFocused) onFocused(file) }
            .combinedClickable(onClick = { act.tap(file) }, onLongClick = { act.longPress(file) })
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f)) {
            FmThumb(file, modifier = Modifier.fillMaxSize(), corner = 12.dp, iconSize = 44.dp)
            if (ui.selectionMode) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { FmCheck(selected) }
            }
            if (fav) {
                Icon(
                    Icons.Filled.Star, contentDescription = "Favorite", tint = DeckPalette.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Two reserved lines keep every tile in a row the same height.
        Text(
            text = file.name,
            color = DeckPalette.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (isDir) "Folder" else StringUtils.formatBytes(file.length()),
            color = DeckPalette.onSurfaceVariant,
            fontSize = 11.5.sp,
            maxLines = 1,
        )
    }
}

// ── Favorites ──

@Composable
private fun FmFavorites(ui: DeckFmUi, act: DeckFmActions, onFocused: (File) -> Unit) {
    val context = LocalContext.current
    val favorites = remember(ui.favTick) { FavoritesStore.list(context).map(::File).filter { it.exists() } }
    val pinned = remember(ui.favTick, ui.currentDir.absolutePath) {
        FavoritesStore.isFavorite(context, ui.currentDir.absolutePath)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "pin") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                Text(
                    text = if (pinned) "\"${ui.currentDir.name.ifBlank { ui.driveLabel }}\" is pinned"
                    else "Pin the folder you were in",
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FmPill("Pin folder", Icons.Filled.PushPin, act.pinCurrent, enabled = !pinned, primary = !pinned)
            }
        }
        if (favorites.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillParentMaxHeightCompat()) {
                    FmStateView(
                        icon = Icons.Filled.StarBorder,
                        title = "No favorites yet",
                        body = "Pin a folder from its menu to jump back to it fast.",
                    )
                }
            }
        } else {
            items(favorites, key = { it.absolutePath }) { file ->
                FmFavCard(file, ui, act, onFocused)
            }
        }
    }
}

/** A fixed, generous height for the empty state inside a lazy list (it has no parent size to fill). */
private fun Modifier.fillParentMaxHeightCompat(): Modifier = this.fillMaxWidth().heightIn(min = 260.dp)

@Composable
private fun FmFavCard(file: File, ui: DeckFmUi, act: DeckFmActions, onFocused: (File) -> Unit) {
    val loc = remember(file.absolutePath, ui.containers) { describeLocation(file, ui.containers, ui.imagefsDir) }
    val (badgeBg, badgeFg) = badgeColors(loc)
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, shape)
            .deckDragItem("fm-fav-${file.absolutePath}", selfOnly = true)
            .deckBringIntoView()
            .onFocusChanged { if (it.isFocused) onFocused(file) }
            .combinedClickable(onClick = { act.jumpTo(file) }, onLongClick = { act.unpin(file) })
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(badgeBg.copy(alpha = 0.35f)),
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = DeckPalette.onBackground, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name.ifBlank { file.absolutePath },
                color = DeckPalette.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                Text(
                    text = loc.driveLabel,
                    color = badgeFg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = loc.containerName?.let { "Container: \"$it\"" } ?: when (loc.storage) {
                        FavStorage.INTERNAL -> "Internal storage"
                        FavStorage.SD -> "SD card"
                        FavStorage.CONTAINER -> "System files (shared)"
                        FavStorage.OTHER -> "Storage"
                    },
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = loc.displayPath,
                color = DeckPalette.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // Touch unpin; the pad uses X (or a long-press) on the card.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .focusProperties { canFocus = false }
                .clickable { act.unpin(file) },
        ) {
            Icon(Icons.Filled.Star, contentDescription = "Remove from favorites", tint = DeckPalette.primary, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Loading / empty / error ──

@Composable
private fun FmStateView(
    icon: ImageVector,
    title: String,
    body: String,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp).clip(CircleShape).background(DeckPalette.primary.copy(alpha = 0.12f)),
        ) {
            if (loading) CircularProgressIndicator(color = DeckPalette.primary, modifier = Modifier.size(36.dp))
            else Icon(icon, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = DeckPalette.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(body, color = DeckPalette.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        if (actionLabel != null) {
            Spacer(Modifier.height(14.dp))
            DeckButton(actionLabel, onClick = onAction)
        }
    }
}
