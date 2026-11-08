package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import com.winhub.hernandez.ui.components.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SportsEsports
import com.winhub.hernandez.ui.components.CollapsibleRail
import com.winhub.hernandez.ui.components.RailItem
import com.winhub.hernandez.ui.components.RailSection
import com.winhub.hernandez.ui.components.rememberRailState
import androidx.compose.material3.AlertDialog
import com.winhub.hernandez.ui.components.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.winhub.hernandez.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.winhub.hernandez.ui.components.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.MainActivity
import com.winhub.hernandez.R
import com.winhub.hernandez.XServerDisplayActivity
import com.winhub.hernandez.container.Container
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.core.PeIconExtractor
import com.winhub.hernandez.core.StorageRoots
import com.winhub.hernandez.core.StringUtils
import com.winhub.hernandez.core.GameIdentifier
import com.winhub.hernandez.core.WinePath
import com.winhub.hernandez.util.FavoritesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What to do when a pasted item already exists at the destination.
 *
 * OVERWRITE and MERGE resolve to the same call — `copyWithProgress` recurses into an existing
 * directory and truncates existing files — but they mean different things to the user, so both are
 * offered and the wording is chosen per item type (files overwrite, folders merge).
 */
enum class ConflictChoice { OVERWRITE, MERGE, KEEP_BOTH, SKIP }

/**
 * Ordering for the file list. Folders always lead regardless of direction — a descending sort that
 * buries every folder under the files is never what someone means by "Z to A".
 */
/**
 * Shortens a path from the LEFT, keeping whole segments.
 *
 * Compose's TextOverflow can only ellipsise the tail, which for a path throws away the part that
 * matters — `/storage/emulated/0/Winlator/Game…` tells you nothing about where you are.
 */
private fun elidePathStart(path: String, max: Int): String {
    if (path.length <= max) return path
    val parts = path.split('/').filter { it.isNotEmpty() }
    val out = StringBuilder()
    for (part in parts.asReversed()) {
        if (out.length + part.length + 1 > max - 2) break
        out.insert(0, "/$part")
    }
    return if (out.isEmpty()) "…" + path.takeLast(max - 1) else "…$out"
}

private fun comparatorFor(sortBy: String, desc: Boolean): Comparator<File> {
    val inner: Comparator<File> = when (sortBy) {
        "date" -> compareBy { it.lastModified() }
        // Directory length() is meaningless, so folders sort by name within the size ordering
        // instead of pretending to have one.
        "size" -> compareBy { if (it.isDirectory) -1L else it.length() }
        else -> compareBy { it.name.lowercase() }
    }
    val directed = if (desc) inner.reversed() else inner
    return compareBy<File> { if (it.isDirectory) 0 else 1 }.then(directed)
}

private val FileTypeIcon: Map<String, ImageVector> = mapOf(
    "folder" to Icons.Filled.Folder,
)

// Image extensions that get a real thumbnail (via Coil) instead of the generic file icon.
private val IMAGE_THUMB_EXTS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

// Color-only sweep: the former card-fill / card-stroke / divider / icon-blue / icon-white
// constants were rerouted onto MaterialTheme.colorScheme tokens (surface / outline / primary /
// onSurface) at their use sites so a theme preset/accent recolors them.

// True when [child] is [ancestor] itself or lives anywhere inside it.
private fun isWithin(child: File, ancestor: File): Boolean {
    val c = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
    val a = runCatching { ancestor.canonicalPath }.getOrDefault(ancestor.absolutePath)
    return c == a || c.startsWith(a + File.separator)
}

// ── DOS file attributes (Read-only / Hidden) ──
// The in-container file manager (wfm.exe) exposes these in its Properties dialog; this mirrors the
// same two toggles for files browsed from the app side. No root — we only touch permission bits the
// app owns (container files) and the Wine DOS-attribute xattr.

// FILE_ATTRIBUTE_HIDDEN, as Wine encodes it in the user.DOSATTRIB extended attribute.
private const val FILE_ATTRIBUTE_HIDDEN = 0x2
private const val DOSATTRIB_XATTR = "user.DOSATTRIB"

// Snapshot of a file's two toggleable attributes. hiddenSupported is false when the underlying
// filesystem can't store the DOSATTRIB xattr (the FUSE /storage volumes) — the Hidden toggle is then
// disabled while Read-only keeps working.
private data class FileAttrState(
    val readOnly: Boolean,
    val hidden: Boolean,
    val hiddenSupported: Boolean,
)

// Wine (and Samba) store user.DOSATTRIB as an ASCII hex string ("0x22"), sometimes with trailing
// Samba fields after a separator. We only need the leading DOS-attribute hex, so read from the "0x"
// prefix and stop at the first non-hex character.
private fun parseDosAttrib(raw: ByteArray): Int {
    val s = String(raw, Charsets.US_ASCII).trim()
    val hex = if (s.startsWith("0x") || s.startsWith("0X")) s.substring(2) else s
    val digits = hex.takeWhile { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    return digits.toIntOrNull(16) ?: 0
}

// Read the current read-only + Wine-hidden state. Never throws: an xattr-unsupported filesystem
// comes back hiddenSupported=false (Hidden toggle disabled), Read-only always resolves.
private fun readFileAttrs(file: File): FileAttrState {
    val path = file.absolutePath
    // Read-only reflects the OWNER write bit — that's what Wine maps to FILE_ATTRIBUTE_READONLY.
    val readOnly = runCatching {
        (android.system.Os.stat(path).st_mode and android.system.OsConstants.S_IWUSR) == 0
    }.getOrDefault(!file.canWrite())

    var hidden = false
    var supported = true
    try {
        hidden = (parseDosAttrib(android.system.Os.getxattr(path, DOSATTRIB_XATTR)) and FILE_ATTRIBUTE_HIDDEN) != 0
    } catch (e: android.system.ErrnoException) {
        // ENODATA: xattr namespace works, the attribute just isn't set yet -> still toggleable.
        // ENOTSUP/EOPNOTSUPP/anything else: the fs can't store it -> disable the Hidden toggle.
        supported = e.errno == android.system.OsConstants.ENODATA
    } catch (e: Exception) {
        supported = false
    }
    return FileAttrState(readOnly, hidden, supported)
}

// Toggle read-only by flipping the write bits, preserving every other permission bit. Setting
// read-only clears owner/group/other write (so Wine sees FILE_ATTRIBUTE_READONLY regardless of which
// write bit it checks); clearing it restores owner write. Falls back to File.setWritable if chmod is
// somehow refused. Returns true on success.
private fun setReadOnly(file: File, readOnly: Boolean): Boolean = runCatching {
    val path = file.absolutePath
    val mode = android.system.Os.stat(path).st_mode
    val writeBits = android.system.OsConstants.S_IWUSR or
        android.system.OsConstants.S_IWGRP or android.system.OsConstants.S_IWOTH
    val newMode = if (readOnly) mode and writeBits.inv()
        else mode or android.system.OsConstants.S_IWUSR
    android.system.Os.chmod(path, newMode)
    true
}.getOrElse { file.setWritable(!readOnly, true) }

// Flip Wine's DOS hidden bit in user.DOSATTRIB, preserving the other DOS-attribute bits
// (archive/system/read-only) already encoded there, and synthesizing a minimal value when absent.
// Writes Wine's own "0x%x" format, which Wine reads back natively. Returns false when the fs can't
// store the xattr, so the caller can disable just the Hidden toggle.
private fun setHidden(file: File, hidden: Boolean): Boolean = try {
    val path = file.absolutePath
    var attr = try {
        parseDosAttrib(android.system.Os.getxattr(path, DOSATTRIB_XATTR))
    } catch (e: android.system.ErrnoException) {
        if (e.errno == android.system.OsConstants.ENODATA) 0 else throw e
    }
    attr = if (hidden) attr or FILE_ATTRIBUTE_HIDDEN else attr and FILE_ATTRIBUTE_HIDDEN.inv()
    val value = "0x%x".format(attr).toByteArray(Charsets.US_ASCII)
    android.system.Os.setxattr(path, DOSATTRIB_XATTR, value, 0)
    true
} catch (e: Exception) {
    false
}

// ── Favorites: origin resolution ──

enum class FavStorage { INTERNAL, SD, CONTAINER, OTHER }

data class FavLocation(
    val storage: FavStorage,
    val containerName: String?,   // non-null only for a container's Drive C:
    val driveLabel: String,       // "Drive C:", "Drive Z:", "Internal", "SD card", or "Storage"
    val displayPath: String       // Wine drives: "C:\\...", "Z:\\..."; storage: the unix absolute path
)

// Resolve where [file] lives (storage source + drive + a friendly path) by prefix-matching
// its absolute path. Robust to a missing/renamed container — falls through to OTHER.
fun describeLocation(file: File, containers: List<Container>, imagefsDir: File): FavLocation {
    val abs = file.absolutePath

    for (container in containers) {
        val driveC = File(container.rootDir, ".wine/drive_c").absolutePath
        if (abs == driveC || abs.startsWith("$driveC/")) {
            val remainder = abs.removePrefix(driveC).replace('/', '\\')
            return FavLocation(
                storage = FavStorage.CONTAINER,
                containerName = container.name,
                driveLabel = "Drive C:",
                displayPath = "C:$remainder",
            )
        }
    }

    val imagefs = imagefsDir.absolutePath
    if (abs == imagefs || abs.startsWith("$imagefs/")) {
        val remainder = abs.removePrefix(imagefs).replace('/', '\\')
        // Z:/imagefs is shared across containers — no single owning container.
        return FavLocation(
            storage = FavStorage.CONTAINER,
            containerName = null,
            driveLabel = "Drive Z:",
            displayPath = "Z:$remainder",
        )
    }

    val internal = "/storage/emulated/0"
    if (abs == internal || abs.startsWith("$internal/")) {
        return FavLocation(FavStorage.INTERNAL, null, "Internal", abs)
    }

    if (abs.startsWith("/storage/")) {
        val name = abs.removePrefix("/storage/").substringBefore('/')
        if (name.isNotEmpty() && name != "emulated" && name != "self") {
            return FavLocation(FavStorage.SD, null, "SD card", abs)
        }
    }

    return FavLocation(FavStorage.OTHER, null, "Storage", abs)
}

// Semantic identity colours for the favourite-card drive badge. Intentionally NOT theme
// accent colours — they identify the storage source at a glance. Returns (background, foreground).
private fun badgeColors(loc: FavLocation): Pair<Color, Color> {
    val white = Color(0xFFFFFFFF)
    return when {
        loc.storage == FavStorage.INTERNAL -> Color(0xFF2E5FB0) to white   // blue
        loc.storage == FavStorage.SD -> Color(0xFF2E7D32) to white         // green
        loc.driveLabel == "Drive Z:" -> Color(0xFF6A3FB0) to white         // purple
        loc.storage == FavStorage.CONTAINER -> Color(0xFF8F6A00) to white  // amber (Drive C:)
        else -> Color(0xFF555555) to white                                 // grey
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    // Pick mode (issue #73): reuse this File Manager as a themed file picker. When on, editing/run
    // features are gated off and tapping a matching file returns it via [onPick]. Defaults keep the
    // full-featured File Manager nav destination unchanged.
    pickMode: Boolean = false,
    // Directory-pick mode (issue #70): only folders are listed, files are hidden, and a
    // "Select this folder" action returns the current directory via [onPick]. Implies pickMode.
    pickDirMode: Boolean = false,
    pickExtensions: List<String> = emptyList(),
    initialDir: File? = null,
    pickerTitle: String? = null,
    // The chosen container's C: drive (`<container>/.wine/drive_c`), passed only by the add-a-game
    // flow which already knows the target container. When non-null AND it exists, the picker offers a
    // working "Drive C:" location (rail item + drive-chip menu) so the user can browse/pick a game
    // that lives on C: — some games only boot (or boot faster) from the container's own C: drive. The
    // default landing is unchanged (internal root); C: is an opt-in jump. Null for every other picker.
    driveCPath: File? = null,
    onPick: ((File) -> Unit)? = null,
    // Deck ("New UI") host: swap the Material chrome for Deck pills/cards. Behaviour is identical.
    deckMode: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainActivity = context as? MainActivity
    val scope = rememberCoroutineScope()

    val containerManager = mainActivity?.containerManager
    val containers = remember { containerManager?.getContainers()?.toList() ?: emptyList<Container>() }
    val imagefsDir = remember { File(context.filesDir, "imagefs") }

    // Only matching files are shown in pick mode (directories are always shown). Empty = all files.
    val lowerExts = remember(pickExtensions) { pickExtensions.map { it.lowercase() } }
    fun matchesPickExt(file: File): Boolean {
        if (lowerExts.isEmpty()) return true
        val name = file.name.lowercase()
        return lowerExts.any { name.endsWith(".$it") }
    }

    val pickPrefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val browsePrefs = pickPrefs
    val rootDir = remember {
        // Both modes: honour an explicit caller-supplied start dir (e.g. Log Manager's game-log
        // folder), else open at the INTERNAL STORAGE ROOT. Selection screens (drive-folder pick,
        // local component pick, imports) previously defaulted to Download which — combined with the
        // currentRoot floor below — trapped users in Download with no way up (reported bug).
        initialDir?.takeIf { it.isDirectory } ?: File("/storage/emulated/0")
    }

    var currentDir by remember { mutableStateOf(rootDir) }
    // The up/back FLOOR — back + the up-arrow are disabled while currentDir == currentRoot. It MUST be
    // the VOLUME ROOT of the start dir (internal /storage/emulated/0, or an SD card /storage/XXXX-XXXX),
    // NOT the start dir itself: otherwise opening at any subfolder disables up/back and traps the user
    // there. (Mirrors the volume-root logic in favLocationOf above.)
    fun volumeRootOf(dir: File): File {
        val abs = dir.absolutePath
        val internal = "/storage/emulated/0"
        return when {
            abs == internal || abs.startsWith("$internal/") -> File(internal)
            abs.startsWith("/storage/") -> {
                val name = abs.removePrefix("/storage/").substringBefore('/')
                if (name.isNotEmpty() && name != "emulated" && name != "self") File("/storage/$name") else dir
            }
            else -> dir
        }
    }
    var currentRoot by remember { mutableStateOf(volumeRootOf(rootDir)) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var selectedEntry by remember { mutableStateOf<File?>(null) }
    var showMenuFor by remember { mutableStateOf<File?>(null) }
    // Clipboard holds a LIST so one paste can carry a whole selection. Cut/copy semantics are a
    // flag on the batch rather than per item — mixing the two in one clipboard has no sane meaning.
    var clipboardFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isCutOperation by remember { mutableStateOf(false) }
    // Multi-select. Keyed by absolute path rather than File so a directory reload (which builds
    // fresh File objects) doesn't silently drop the selection.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Paste conflict resolution, surfaced from the IO coroutine and answered by the dialog.
    var pendingConflict by remember { mutableStateOf<File?>(null) }
    var conflictChoice by remember { mutableStateOf<ConflictChoice?>(null) }
    var conflictApplyToAll by remember { mutableStateOf(false) }
    // Set while a copy/move runs so the progress UI can offer a cancel.
    var operationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pendingBulkDelete by remember { mutableStateOf<List<File>>(emptyList()) }
    // Browse controls. Persisted so the list doesn't reset its order every time you open a folder.
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(browsePrefs.getString("fmSortBy", "name") ?: "name") }
    var sortDesc by remember { mutableStateOf(browsePrefs.getBoolean("fmSortDesc", false)) }
    var showHidden by remember { mutableStateOf(browsePrefs.getBoolean("fmShowHidden", true)) }
    var showSortMenu by remember { mutableStateOf(false) }
    // View mode: list of cards (default) or a thumbnail grid. Density applies to the list only —
    // a grid tile has no second line to compact.
    // The grid/list toggle is the SOURCE OF TRUTH in BOTH orientations (it drives the view and its
    // choice persists across rotation). Grid is the default — most useful in landscape, and in
    // portrait GridCells.Adaptive naturally renders fewer columns (~2). Do NOT force portrait to list:
    // that broke the toggle on-device (tapping it did nothing in portrait).
    var gridView by remember { mutableStateOf(browsePrefs.getBoolean("fmGridView", true)) }
    val showGrid = gridView
    var compactRows by remember { mutableStateOf(browsePrefs.getBoolean("fmCompactRows", false)) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    // Properties sheet (basic info + Read-only / Hidden toggles) target; null when closed.
    var propertiesTarget by remember { mutableStateOf<File?>(null) }
    var pendingRun by remember { mutableStateOf<File?>(null) }
    var pendingAddShortcut by remember { mutableStateOf<File?>(null) }
    var isOperationRunning by remember { mutableStateOf(false) }
    var operationLabel by remember { mutableStateOf("") }
    var operationDeterminate by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    // Favorites view: when on, a dedicated bookmarks list replaces the file list.
    // favTick is bumped on any add/remove/toggle so the favorites view + per-row star recompute.
    var showFavorites by remember { mutableStateOf(false) }
    var favTick by remember { mutableIntStateOf(0) }

    // resetScroll: jump to the top of the list (true for navigation; false for in-place reloads
    // after delete/paste/rename/refresh so the user keeps their scroll position).
    fun loadDirectory(dir: File, resetScroll: Boolean = true) {
        currentDir = dir
        // Remember the browsed directory so the next pick resumes here.
        if (pickMode) pickPrefs.edit().putString("lastFilePickerDir", dir.absolutePath).apply()
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                dir.listFiles()?.toList()
                    // Dir-pick mode: folders only. File-pick: folders + matching files. Else: all.
                    ?.filter { if (pickDirMode) it.isDirectory else !pickMode || it.isDirectory || matchesPickExt(it) }
                    // Dotfiles are noise in a storage root (.aya, .$recycle_bin$) but occasionally
                    // the thing you came for, so it's a toggle rather than a permanent filter.
                    ?.filter { showHidden || !it.name.startsWith(".") }
                    ?.sortedWith(comparatorFor(sortBy, sortDesc)) ?: emptyList()
            }
            entries = list
            if (resetScroll) listState.scrollToItem(0)
        }
    }

    // Pull-to-refresh: re-list the current directory, keeping scroll position.
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            loadDirectory(currentDir, resetScroll = false)
            pullState.endRefresh()
        }
    }

    // Jump to a drive's root; pins the Back boundary so we don't climb above it.
    fun openDrive(dir: File) {
        currentRoot = dir
        loadDirectory(dir)
    }

    // Jump to an arbitrary folder (favourite, quick location, caller-supplied start dir) WITHOUT
    // pinning it as the floor: the floor stays the folder's volume root (or the container's C:
    // drive when the target lives inside it), so up/back keep working above the jump target.
    // Pinning the target itself was issue #476: after opening a favourite, the up arrow was greyed
    // out and Back did nothing, because the favourite had become "the root".
    fun jumpTo(dir: File) {
        val dc = driveCPath?.takeIf { it.isDirectory }
        currentRoot = if (dc != null && isWithin(dir, dc)) dc else volumeRootOf(dir)
        loadDirectory(dir)
    }

    // One folder up, bounded at the current floor. Shared by the toolbar arrow, system Back and
    // the ".." row at the top of every listing.
    fun goUp() {
        val parent = currentDir.parentFile
        if (currentDir != currentRoot && parent != null && parent.exists()) loadDirectory(parent)
    }

    LaunchedEffect(Unit) { jumpTo(rootDir) }

    // System/gesture Back: while the Favorites view is open it closes that first; otherwise
    // it goes up one directory. Only at the current drive's root with Favorites closed is it
    // disabled, letting Back propagate to close the File Manager.
    BackHandler(enabled = showFavorites || currentDir != currentRoot) {
        if (showFavorites) {
            showFavorites = false
            return@BackHandler
        }
        goUp()
    }

    fun canRun(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".msi") || name.endsWith(".sh")
    }

    // Launch [file] inside [container] the same way the Games importer does: map the
    // file's folder to a Wine drive letter (persisted on the container), then hand
    // XServerDisplayActivity a shortcut whose Exec is `wine <X:\path>`. We don't add a
    // permanent Games entry — the .desktop lives in app storage, not the container's
    // desktop dir, so it never shows up in the Shortcuts list.
    fun runFileInContainer(file: File, container: Container) {
        scope.launch {
            val shortcutFile = withContext(Dispatchers.IO) {
                runCatching {
                    val winPath = WinePath.resolveWindowsPath(container, file.absolutePath)
                    val escaped = WinePath.escapeForExec(winPath)
                    val desktopDir = File(context.filesDir, "desktops").apply { mkdirs() }
                    val safeName = file.nameWithoutExtension
                        .replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "run" }
                    File(desktopDir, "$safeName.desktop").apply {
                        writeText(
                            buildString {
                                append("[Desktop Entry]\n")
                                append("Name=").append(file.nameWithoutExtension).append("\n")
                                append("Exec=wine ").append(escaped).append("\n")
                                append("Icon=").append(safeName).append("\n")
                                append("Type=Application\n")
                                append("StartupWMClass=explorer\n")
                                append("\ncontainer_id:").append(container.id).append("\n")
                                append("\n[Extra Data]\n")
                            }
                        )
                    }
                }
            }.getOrElse { failure ->
                val message = if (failure is WinePath.NoFreeDriveLetterException) {
                    "No free drive letters left in this container — remove one you don't need in its Drives tab"
                } else {
                    "Couldn't prepare ${file.name} to run"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                return@launch
            }
            val intent = Intent(context, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", container.id)
            intent.putExtra("shortcut_path", shortcutFile.absolutePath)
            intent.putExtra("shortcut_name", shortcutFile.nameWithoutExtension)
            context.startActivity(intent)
        }
    }

    fun runFile(file: File) {
        containerManager ?: return
        when {
            containers.isEmpty() ->
                Toast.makeText(context, "No container available — create one first", Toast.LENGTH_SHORT).show()
            containers.size == 1 -> runFileInContainer(file, containers.first())
            else -> pendingRun = file   // ask which container
        }
    }

    // Create a PERMANENT Games/Shortcuts tile for [file] in [container] — same result as the
    // Games-tab "+" button (ExeShortcutImporter writes into the container's desktop dir, so it
    // shows up in the Shortcuts list and picks up cover art). Unlike runFile's throwaway desktop.
    //
    // Identify the game first, exactly as the "+" flow does. Passing the raw exe basename and no
    // appId quietly cost most of the importer's work: without an appId it skips Steam's own 600x900
    // portrait and the SGDB-by-appId lookup, and never applies Steam's authoritative name — leaving
    // only an SGDB-by-name search running on a string like "dirt3_game", which finds nothing. Same
    // importer, far worse result, purely because the call site under-fed it.
    fun addShortcutInContainer(file: File, container: Container) {
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val identity = runCatching { GameIdentifier.identify(file) }.getOrNull()
                    val displayName = identity?.name?.takeIf { it.isNotBlank() }
                        ?: file.nameWithoutExtension
                    ExeShortcutImporter.addToShortcuts(
                        context, container, file, displayName, identity?.appId,
                    ).nameWithoutExtension
                }.getOrNull()
            }
            if (name == null) {
                Toast.makeText(context, "Couldn't add ${file.name} to Shortcuts", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Added \"$name\" to Shortcuts", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addToShortcuts(file: File) {
        containerManager ?: return
        when {
            containers.isEmpty() ->
                Toast.makeText(context, "No container available — create one first", Toast.LENGTH_SHORT).show()
            containers.size == 1 -> addShortcutInContainer(file, containers.first())
            else -> pendingAddShortcut = file   // ask which container
        }
    }

    // Shared by both the list rows and the grid tiles so the big Fast-Extract when-branch and the
    // Unpack-screen launch aren't duplicated per call site. Both close the context menu first.
    fun launchFastExtract(file: File) {
        showMenuFor = null
        scope.launch {
            when (val o = com.winhub.hernandez.core.unpack.FastExtract.start(context, file)) {
                is com.winhub.hernandez.core.unpack.FastExtract.Outcome.Started ->
                    Toast.makeText(context, "Unpacking ${o.name}…", Toast.LENGTH_SHORT).show()
                com.winhub.hernandez.core.unpack.FastExtract.Outcome.Busy ->
                    Toast.makeText(context, "Another unpack is already in progress", Toast.LENGTH_SHORT).show()
                is com.winhub.hernandez.core.unpack.FastExtract.Outcome.NotArchive ->
                    Toast.makeText(context, "Not a recognized archive — nothing to unpack", Toast.LENGTH_SHORT).show()
                is com.winhub.hernandez.core.unpack.FastExtract.Outcome.OpenScreen -> {
                    o.toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    context.startActivity(
                        com.winhub.hernandez.UnpackArchiveActivity.intent(context, o.archivePath)
                    )
                }
            }
        }
    }

    fun launchUnpack(file: File) {
        showMenuFor = null
        context.startActivity(
            com.winhub.hernandez.UnpackArchiveActivity.intent(context, file.absolutePath)
        )
    }

    // Resolve a non-colliding destination in [dir] for [name] (foo.txt -> "foo (1).txt").
    fun uniqueDestination(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        do {
            candidate = File(dir, "$base ($i)$ext")
            i++
        } while (candidate.exists())
        return candidate
    }

    fun performDelete(file: File) {
        scope.launch {
            isOperationRunning = true
            operationLabel = "Deleting..."
            val ok = withContext(Dispatchers.IO) { FileUtils.delete(file) }
            isOperationRunning = false
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
        }
    }

    /** Waits for the user to answer the conflict dialog for [file]; null if they cancelled it. */
    suspend fun askConflict(file: File): ConflictChoice? {
        pendingConflict = file
        conflictChoice = null
        // Poll rather than plumb a CompletableDeferred through Compose state — the dialog answers
        // by setting conflictChoice, and this coroutine is already off the critical path.
        while (pendingConflict != null && conflictChoice == null) kotlinx.coroutines.delay(50)
        return conflictChoice
    }

    fun performPaste() {
        val sources = clipboardFiles
        if (sources.isEmpty()) return
        val dstDir = currentDir
        val cut = isCutOperation

        operationJob = scope.launch {
            operationProgress = 0f
            operationDeterminate = true
            operationLabel = if (cut) "Moving..." else "Copying..."
            isOperationRunning = true

            var applyToAll: ConflictChoice? = null
            var failed = 0
            var skipped = 0
            var done = 0

            for (src in sources) {
                // Pasting a folder into itself or its own subtree would recurse forever.
                if (src.isDirectory && isWithin(dstDir, src)) {
                    failed++
                    continue
                }
                // Moving into the folder it already sits in is a no-op.
                if (cut && src.parentFile?.absolutePath == dstDir.absolutePath) {
                    skipped++
                    continue
                }

                var dst = File(dstDir, src.name)
                if (dst.exists()) {
                    val choice = applyToAll ?: askConflict(src)?.also {
                        if (conflictApplyToAll) applyToAll = it
                    } ?: run { skipped++; null } ?: continue
                    when (choice) {
                        // Overwrite and Merge both paste onto the real destination: copyWithProgress
                        // recurses into an existing directory and truncates existing files, so the
                        // two differ only in what the user expects, not in what we call.
                        ConflictChoice.OVERWRITE, ConflictChoice.MERGE -> Unit
                        ConflictChoice.KEEP_BOTH -> dst = uniqueDestination(dstDir, src.name)
                        ConflictChoice.SKIP -> { skipped++; continue }
                    }
                }

                // Progress is per item; with a batch the label carries the overall position.
                operationLabel = buildString {
                    append(if (cut) "Moving" else "Copying")
                    if (sources.size > 1) append(" ${done + 1}/${sources.size}")
                    append(" — ").append(src.name)
                }
                var lastPct = -1
                val onProgress = FileUtils.ProgressCallback { copied, total ->
                    val pct = if (total > 0) ((copied * 100) / total).toInt() else 100
                    if (pct != lastPct) {
                        lastPct = pct
                        operationProgress = pct / 100f
                    }
                }
                val target = dst
                val ok = withContext(Dispatchers.IO) {
                    if (cut) FileUtils.moveWithProgress(src, target, onProgress)
                    else FileUtils.copyWithProgress(src, target, onProgress)
                }
                if (ok) done++ else failed++
            }

            isOperationRunning = false
            operationDeterminate = false
            operationJob = null
            clipboardFiles = emptyList()
            isCutOperation = false
            selectionMode = false
            selectedPaths = emptySet()
            loadDirectory(currentDir, resetScroll = false)

            val message = when {
                failed > 0 -> "$done done, $failed failed"
                skipped > 0 -> "$done done, $skipped skipped"
                sources.size > 1 -> "$done items ${if (cut) "moved" else "copied"}"
                else -> null
            }
            if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun performRename(file: File, newName: String) {
        val target = File(file.parentFile, newName)
        if (target.exists()) {
            Toast.makeText(context, "\"$newName\" already exists", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isOperationRunning = true
            operationLabel = "Renaming..."
            val ok = withContext(Dispatchers.IO) { file.renameTo(target) }
            isOperationRunning = false
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun createFolder(parent: File, name: String) {
        val target = File(parent, name)
        if (target.exists()) {
            Toast.makeText(context, "\"$name\" already exists", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            isOperationRunning = true
            operationLabel = "Creating folder..."
            val ok = withContext(Dispatchers.IO) { target.mkdirs() }
            isOperationRunning = false
            loadDirectory(currentDir, resetScroll = false)
            if (!ok) Toast.makeText(context, "Could not create folder", Toast.LENGTH_SHORT).show()
        }
    }

    var showDriveMenu by remember { mutableStateOf(false) }
    var showContainerPicker by remember { mutableStateOf(false) }
    // Re-enumerated whenever we come back to the screen: returning from a container can leave this
    // process on a stale storage view, and the volume set has to be re-read rather than cached.
    var storageTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) storageTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val drives = remember(storageTick) { StorageRoots.list(context) }

    // ── Dialogs ──

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        OutlinedAlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNewFolderDialog = false
                    if (folderName.isNotBlank()) createFolder(currentDir, folderName)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") } },
        )
    }

    if (renameTarget != null) {
        var newName by remember(renameTarget) { mutableStateOf(renameTarget?.name ?: "") }
        OutlinedAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = renameTarget
                    renameTarget = null
                    if (file != null && newName.isNotBlank()) performRename(file, newName)
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } },
        )
    }

    propertiesTarget?.let { file ->
        FilePropertiesDialog(
            file = file,
            onDismiss = { propertiesTarget = null },
            // Attribute changes affect Hidden/read-only which the listing filters/sorts on, so refresh
            // in place (keeping scroll) after any toggle applies.
            onChanged = { loadDirectory(currentDir, resetScroll = false) },
        )
    }

    if (selectedEntry != null && selectedEntry != showMenuFor) {
        val file = selectedEntry ?: return
        OutlinedAlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("Delete?") },
            text = { Text("Delete \"${file.name}\" permanently?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedEntry = null
                    performDelete(file)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { selectedEntry = null }) { Text("Cancel") } },
        )
    }

    if (pendingBulkDelete.isNotEmpty()) {
        val victims = pendingBulkDelete
        OutlinedAlertDialog(
            onDismissRequest = { pendingBulkDelete = emptyList() },
            title = { Text("Delete ${victims.size} item${if (victims.size == 1) "" else "s"}?") },
            text = {
                Column {
                    Text("This can't be undone.")
                    Spacer(Modifier.height(6.dp))
                    // Name a few so an accidental Select-All is obvious before it's too late.
                    victims.take(5).forEach {
                        Text("• ${it.name}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (victims.size > 5) {
                        Text(
                            "…and ${victims.size - 5} more",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulkDelete = emptyList()
                    selectionMode = false
                    selectedPaths = emptySet()
                    operationJob = scope.launch {
                        isOperationRunning = true
                        var failed = 0
                        victims.forEachIndexed { i, f ->
                            operationLabel = "Deleting ${i + 1}/${victims.size} — ${f.name}"
                            if (!withContext(Dispatchers.IO) { FileUtils.delete(f) }) failed++
                        }
                        isOperationRunning = false
                        operationJob = null
                        loadDirectory(currentDir, resetScroll = false)
                        if (failed > 0) {
                            Toast.makeText(context, "$failed couldn't be deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingBulkDelete = emptyList() }) { Text("Cancel") } },
        )
    }

    // Paste conflict — one per colliding item, with "apply to all" for a long batch.
    pendingConflict?.let { conflict ->
        val isDir = conflict.isDirectory
        OutlinedAlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text("\"${conflict.name}\" already exists") },
            text = {
                Column {
                    Text(
                        if (isDir) "Merge adds and replaces files inside the existing folder."
                        else "Overwrite replaces the existing file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    if (clipboardFiles.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.winhub.hernandez.ui.components.Checkbox(
                                checked = conflictApplyToAll,
                                onCheckedChange = { conflictApplyToAll = it },
                            )
                            Text("Apply to all conflicts", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    listOf(
                        (if (isDir) ConflictChoice.MERGE else ConflictChoice.OVERWRITE) to
                            (if (isDir) "Merge" else "Overwrite"),
                        ConflictChoice.KEEP_BOTH to "Keep both",
                        ConflictChoice.SKIP to "Skip",
                    ).forEach { (choice, label) ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { conflictChoice = choice; pendingConflict = null },
                        ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { conflictChoice = ConflictChoice.SKIP; pendingConflict = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showContainerPicker) {
        OutlinedAlertDialog(
            onDismissRequest = { showContainerPicker = false },
            title = { Text("Choose container") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    containers.forEach { container ->
                        Text(
                            text = container.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showContainerPicker = false
                                    openDrive(File(container.rootDir, ".wine/drive_c"))
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (pendingRun != null) {
        val file = pendingRun
        OutlinedAlertDialog(
            onDismissRequest = { pendingRun = null },
            title = { Text("Run in which container?") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    containers.forEach { container ->
                        Text(
                            text = container.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingRun = null
                                    if (file != null) runFileInContainer(file, container)
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingRun = null }) { Text("Cancel") } },
        )
    }

    if (pendingAddShortcut != null) {
        val file = pendingAddShortcut
        OutlinedAlertDialog(
            onDismissRequest = { pendingAddShortcut = null },
            title = { Text("Add to which container?") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    containers.forEach { container ->
                        Text(
                            text = container.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingAddShortcut = null
                                    if (file != null) addShortcutInContainer(file, container)
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingAddShortcut = null }) { Text("Cancel") } },
        )
    }

    CompositionLocalProvider(LocalDeckMode provides deckMode) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The picker has no shell behind it, so it keeps its own background in portrait too.
            .then(
                if (deckMode && !pickMode && LocalDeckPortrait.current) Modifier
                else Modifier.background(if (deckMode) DeckPalette.background else MaterialTheme.colorScheme.background)
            ),
    ) {
        if (deckMode && !pickMode) PortraitScreenTitle("Files")
        // ── Pick-mode title ──
        if (pickMode && !pickerTitle.isNullOrEmpty()) {
            Text(
                text = pickerTitle,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        // Shared collapsible rail: landscape expanded by default, portrait collapsed icon-only. Not
        // shown in pick mode (the themed picker keeps its slim layout). Built each recompose (cheap)
        // so it tracks the current drive/favourites without stale click lambdas.
        val fmRailState = rememberRailState("filemanager")
        fun locItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, dir: File) =
            RailItem(label, icon, !showFavorites && currentRoot.absolutePath == dir.absolutePath) {
                showFavorites = false; openDrive(dir)
            }
        val storageItems = buildList {
            add(locItem("Internal", Icons.Filled.Smartphone, File("/storage/emulated/0")))
            drives.filter { it.removable }.forEach { d ->
                add(RailItem(d.label, Icons.Filled.SdStorage, !showFavorites && currentRoot.absolutePath == d.dir.absolutePath) {
                    showFavorites = false; if (d.readable) openDrive(d.dir)
                })
            }
            // Add-a-game-from-C: (add-game flow only). openDrive() pins currentRoot = drive_c so
            // up/back is bounded at C:\ and the user can't wander up into the Linux prefix. RailItem
            // carries no per-item colour — C: gets the standard accent highlight like Internal/SD
            // (the amber Drive C: identity lives on the Favorites badge, not the rail).
            driveCPath?.takeIf { it.isDirectory }?.let { dc ->
                add(locItem("Drive C:", Icons.Filled.Storage, dc))
            }
        }
        // Quick locations are shortcuts INTO internal storage, not drives: jump there but keep the
        // floor at the storage root so the user can still climb out of Download/Games/Pictures.
        fun quickItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, dir: File) =
            RailItem(label, icon, !showFavorites && currentDir.absolutePath == dir.absolutePath) {
                showFavorites = false; jumpTo(dir)
            }
        val quickItems = buildList {
            File("/storage/emulated/0/Download").takeIf { it.isDirectory }?.let { add(quickItem("Downloads", Icons.Filled.Download, it)) }
            File("/storage/emulated/0/Winlator/Games").takeIf { it.isDirectory }?.let { add(quickItem("Games", Icons.Filled.SportsEsports, it)) }
            File("/storage/emulated/0/Pictures").takeIf { it.isDirectory }?.let { add(quickItem("Pictures", Icons.Filled.Image, it)) }
        }
        val favItems = remember(favTick) { FavoritesStore.list(context).map(::File).filter { it.exists() } }
            .map { d -> RailItem(d.name, Icons.Filled.Star, false) { showFavorites = false; jumpTo(d) } }
        val locationSections = buildList {
            add(RailSection("STORAGE", storageItems))
            if (quickItems.isNotEmpty()) add(RailSection("QUICK", quickItems))
            if (favItems.isNotEmpty()) add(RailSection("★ FAVORITES", favItems))
        }
        // Portrait Deck: the locations (storage, quick folders, favourites) run across the top as a tab strip.
        if (deckMode && LocalDeckPortrait.current && (!pickMode || driveCPath != null)) {
            val topLocations = buildList {
                add(RailItem("★ Favorites", Icons.Filled.Star, showFavorites) { showFavorites = true })
                addAll(storageItems)
                addAll(quickItems)
                addAll(favItems)
            }
            if (topLocations.size > 1) {
                DeckPortraitTabStrip(
                    labels = topLocations.map { it.label },
                    icons = topLocations.map { it.icon },
                    selected = topLocations.indexOfFirst { it.selected },
                    onSelect = { topLocations.getOrNull(it)?.onClick?.invoke() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        // ── Dir-pick action bar: confirm the currently-browsed folder ──
        if (pickDirMode) {
            Button(
                onClick = { onPick?.invoke(currentDir) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Folder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select this folder")
            }
        }
        // ── Path bar ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (deckMode) 12.dp else 0.dp,
                    end = if (deckMode) 12.dp else 0.dp,
                    top = if (deckMode) 6.dp else 0.dp,
                )
                .clip(RoundedCornerShape(if (deckMode) 14.dp else 0.dp))
                .background(if (deckMode) DeckPalette.surface else MaterialTheme.colorScheme.surfaceContainer)
                .then(
                    if (deckMode) Modifier.border(1.dp, DeckPalette.line, RoundedCornerShape(14.dp))
                    else Modifier,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(
                onClick = { goUp() },
                enabled = currentDir != currentRoot,
                modifier = Modifier.deckSelfFocusStroke(RoundedCornerShape(12.dp)),
            ) {
                Icon(Icons.Filled.ArrowBack, "Parent folder", tint = MaterialTheme.colorScheme.primary)
            }

            // describeLocation labels C: by matching against the container list, which is empty in the
            // picker (FilePickerActivity has no MainActivity) — so when we were handed the C: drive
            // directly, label it from that instead of falling through to a generic "Storage".
            val currentDriveLabel = if (driveCPath != null && isWithin(currentDir, driveCPath)) "Drive C:"
                else describeLocation(currentDir, containers, imagefsDir).driveLabel
            // Dim the drive chip while the Favorites list is open (it's not the active context).
            val driveChipAlpha = if (showFavorites) 0.45f else 1f
            Box {
                // The drive/location selector opens the drive dropdown, so give it the same outlined
                // look as the "New Folder" button + the rail location items — it reads as a button, not
                // plain text. Border uses the theme accent token; behaviour unchanged.
                val driveChipShape = RoundedCornerShape(8.dp)
                Text(
                    text = "  $currentDriveLabel  ▾",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = driveChipAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(driveChipShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), driveChipShape)
                        .clickable { showDriveMenu = true }
                        .deckSelfFocusStroke(driveChipShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                DropdownMenu(
                    expanded = showDriveMenu,
                    onDismissRequest = { showDriveMenu = false },
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    DropdownMenuItem(
                        text = { Text("Drive C:") },
                        leadingIcon = {
                            Icon(Icons.Filled.SdStorage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showDriveMenu = false
                            // Prefer the container the picker was launched with (add-a-game flow): its
                            // C: is known even though FilePickerActivity has no MainActivity/container
                            // list (that's why this menu item did nothing in the picker before).
                            val dc = driveCPath?.takeIf { it.isDirectory }
                            if (dc != null) {
                                openDrive(dc)
                            } else if (containers.size == 1) {
                                openDrive(File(containers.first().rootDir, ".wine/drive_c"))
                            } else if (containers.size > 1) {
                                showContainerPicker = true
                            }
                        },
                    )
                    MenuItemDivider()
                    DropdownMenuItem(
                        text = { Text("Drive Z:") },
                        leadingIcon = {
                            Icon(Icons.Filled.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showDriveMenu = false
                            openDrive(imagefsDir)
                        },
                    )
                    drives.forEach { drive ->
                        MenuItemDivider()
                        DropdownMenuItem(
                            text = { Text(drive.label) },
                            leadingIcon = {
                                Icon(
                                    if (drive.removable) Icons.Filled.SdStorage else Icons.Filled.Storage,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showDriveMenu = false
                                if (drive.readable) {
                                    openDrive(drive.dir)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "${drive.label} is mounted but not readable right now",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            if (showFavorites) {
                Text(
                    text = "★ Favorites",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
                // PORTRAIT: hide the current-folder name — it's redundant with the path bar directly
                // below (which shows the full path). The spacer keeps the action icons right-aligned.
                Spacer(Modifier.weight(1f))
            } else {
                // LANDSCAPE: the CURRENT FOLDER, not the full path. A path ellipsised on the right
                // hides its tail — the only part that says where you are ("…/Winlator/Game…"). The
                // full path moves to the line below, where it has room.
                Text(
                    text = currentDir.name.ifBlank { currentDir.absolutePath },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!showFavorites) {
                // New Folder moved off a bottom bar into the toolbar (next to the grid/list toggle),
                // as a compact outlined button, so the file list reclaims that bottom strip.
                if (!pickMode) {
                    OutlinedButton(
                        onClick = { showNewFolderDialog = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp).deckSelfFocusStroke(RoundedCornerShape(10.dp)),
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New Folder", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                    }
                }
                IconButton(modifier = Modifier.deckSelfFocusStroke(RoundedCornerShape(12.dp)), onClick = {
                    gridView = !gridView
                    browsePrefs.edit().putBoolean("fmGridView", gridView).apply()
                }) {
                    Icon(
                        if (gridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                        if (gridView) "List view" else "Grid view",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" },
                    modifier = Modifier.deckSelfFocusStroke(RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.deckSelfFocusStroke(RoundedCornerShape(12.dp)),
                    ) {
                        Icon(Icons.Filled.Sort, "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        listOf("name" to "Name", "date" to "Date modified", "size" to "Size")
                            .forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (sortBy == key) "$label  ${if (sortDesc) "↓" else "↑"}" else label)
                                    },
                                    onClick = {
                                        // Tapping the active field flips direction; a different
                                        // field switches to it ascending.
                                        if (sortBy == key) sortDesc = !sortDesc else { sortBy = key; sortDesc = false }
                                        browsePrefs.edit().putString("fmSortBy", sortBy)
                                            .putBoolean("fmSortDesc", sortDesc).apply()
                                        showSortMenu = false
                                        loadDirectory(currentDir, resetScroll = false)
                                    },
                                )
                            }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        DropdownMenuItem(
                            text = { Text(if (compactRows) "Comfortable rows" else "Compact rows") },
                            onClick = {
                                compactRows = !compactRows
                                browsePrefs.edit().putBoolean("fmCompactRows", compactRows).apply()
                                showSortMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (showHidden) "Hide hidden files" else "Show hidden files") },
                            onClick = {
                                showHidden = !showHidden
                                browsePrefs.edit().putBoolean("fmShowHidden", showHidden).apply()
                                showSortMenu = false
                                loadDirectory(currentDir, resetScroll = false)
                            },
                        )
                    }
                }
            }

            // Star toggle: open/close the dedicated Favorites list.
            IconButton(
                onClick = { showFavorites = !showFavorites },
                modifier = Modifier.deckSelfFocusStroke(RoundedCornerShape(12.dp)),
            ) {
                if (showFavorites) {
                    Icon(Icons.Filled.Star, "Hide favorites", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.StarBorder, "Show favorites", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Search field ── filters the current folder only; it is not a recursive search.
        if (showSearch && !showFavorites) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Filter this folder", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Free space on the volume being browsed — worth knowing before starting a 60 GB copy.
        val freeSpace = remember(currentDir.absolutePath, entries) {
            runCatching { currentDir.usableSpace }.getOrDefault(0L)
        }
        if (!showFavorites) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            ) {
                Text(
                    // Elided from the LEFT: the deepest part of a path is the informative part, so
                    // when it doesn't fit we drop the /storage/emulated/0 prefix, not the tail.
                    text = elidePathStart(currentDir.absolutePath, 52),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    // fill = true: the path takes all remaining width, so the free-space figure
                    // is pinned to the right edge instead of sliding around with the path length.
                    modifier = Modifier.weight(1f),
                )
                if (freeSpace > 0) {
                    Text(
                        "${StringUtils.formatBytes(freeSpace)} free",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ── Selection bar ── replaces the paste banner while picking items.
        if (selectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "${selectedPaths.size} selected",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                // Compact outlined buttons so all five fit one row alongside the count.
                val selBarPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                OutlinedButton(
                    onClick = {
                        selectedPaths = if (selectedPaths.size == entries.size) emptySet()
                        else entries.map { it.absolutePath }.toSet()
                    },
                    contentPadding = selBarPadding,
                ) { Text(if (selectedPaths.size == entries.size) "None" else "All", fontSize = 12.sp) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    enabled = selectedPaths.isNotEmpty(),
                    onClick = {
                        clipboardFiles = entries.filter { it.absolutePath in selectedPaths }
                        isCutOperation = false
                        selectionMode = false
                        selectedPaths = emptySet()
                    },
                    contentPadding = selBarPadding,
                ) { Text("Copy", fontSize = 12.sp) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    enabled = selectedPaths.isNotEmpty(),
                    onClick = {
                        clipboardFiles = entries.filter { it.absolutePath in selectedPaths }
                        isCutOperation = true
                        selectionMode = false
                        selectedPaths = emptySet()
                    },
                    contentPadding = selBarPadding,
                ) { Text("Cut", fontSize = 12.sp) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    enabled = selectedPaths.isNotEmpty(),
                    onClick = { pendingBulkDelete = entries.filter { it.absolutePath in selectedPaths } },
                    contentPadding = selBarPadding,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = { selectionMode = false; selectedPaths = emptySet() },
                    contentPadding = selBarPadding,
                ) { Text("Done", fontSize = 12.sp) }
            }
        }

        // ── Paste banner ──
        if (clipboardFiles.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    .clickable { performPaste() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.ContentPaste, "Paste", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val what = if (clipboardFiles.size == 1) clipboardFiles.first().name
                else "${clipboardFiles.size} items"
                Text(
                    "Paste $what${if (isCutOperation) " (move)" else ""} here",
                    color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { clipboardFiles = emptyList(); isCutOperation = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        // ── Progress overlay ──
        if (isOperationRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val pctText = if (operationDeterminate) "  ${(operationProgress * 100).toInt()}%" else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$operationLabel$pctText",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // A multi-gigabyte copy onto a slow card is exactly when you discover you
                    // picked the wrong folder; without this the only way out was killing the app.
                    if (operationJob != null) {
                        TextButton(onClick = {
                            operationJob?.cancel()
                            operationJob = null
                            isOperationRunning = false
                            operationDeterminate = false
                            loadDirectory(currentDir, resetScroll = false)
                        }) { Text("Cancel", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (operationDeterminate) {
                    LinearProgressIndicator(
                        progress = { operationProgress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // ── Left locations rail (mockup Option 2) + content ──

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
        // ── Favorites list OR file list ──
        if (showFavorites) {
            DeckDragFrame(modifier = Modifier.fillMaxSize()) {
            FavoritesList(
                containers = containers,
                imagefsDir = imagefsDir,
                currentDir = currentDir,
                favTick = favTick,
                onPinCurrent = {
                    FavoritesStore.add(context, currentDir.absolutePath)
                    favTick++
                    Toast.makeText(context, "Added \"${currentDir.name}\" to Favorites", Toast.LENGTH_SHORT).show()
                },
                onJump = { dir ->
                    showFavorites = false
                    jumpTo(dir)
                },
                onUnpin = { dir ->
                    FavoritesStore.remove(context, dir.absolutePath)
                    favTick++
                    Toast.makeText(context, "Removed \"${dir.name}\" from Favorites", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxSize(),
            )
            }
        } else {
        // ── File list (pull down to refresh) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullState.nestedScrollConnection),
        ) {
            val shownEntries = if (searchQuery.isBlank()) entries
            else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }

            if (showGrid && entries.isNotEmpty()) {
                DeckDragFrame(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    if (searchQuery.isBlank() && currentDir != currentRoot) {
                        item(key = "..") { ParentFolderTile(onTap = { goUp() }) }
                    }
                    items(shownEntries, key = { it.absolutePath }) { file ->
                        val isFav = remember(file.absolutePath, favTick) {
                            FavoritesStore.isFavorite(context, file.absolutePath)
                        }
                        FileGridTile(
                            file = file,
                            selectionMode = selectionMode,
                            selected = file.absolutePath in selectedPaths,
                            onLongPress = {
                                if (!pickMode) {
                                    // In selection mode a long-press toggles; otherwise it opens the
                                    // same context menu the list rows show (the tile has no ⋮ button).
                                    if (selectionMode) {
                                        selectedPaths = if (file.absolutePath in selectedPaths)
                                            selectedPaths - file.absolutePath
                                        else selectedPaths + file.absolutePath
                                    } else {
                                        showMenuFor = file
                                    }
                                }
                            },
                            onToggleSelect = {
                                selectedPaths = if (file.absolutePath in selectedPaths)
                                    selectedPaths - file.absolutePath
                                else selectedPaths + file.absolutePath
                            },
                            onTap = {
                                if (file.isDirectory) loadDirectory(file)
                                else if (pickMode) {
                                    if (matchesPickExt(file)) {
                                        pickPrefs.edit().putString("lastFilePickerDir", currentDir.absolutePath).apply()
                                        onPick?.invoke(file)
                                    }
                                } else if (canRun(file)) runFile(file)
                            },
                            onMenu = { showMenuFor = file },
                            menuExpanded = showMenuFor == file,
                            onDismissMenu = { showMenuFor = null },
                            isFavorite = isFav,
                            onSelect = {
                                selectionMode = true
                                selectedPaths = selectedPaths + file.absolutePath
                                showMenuFor = null
                            },
                            onRun = { runFile(file) },
                            onAddToShortcuts = { addToShortcuts(file) },
                            onUnpack = { launchUnpack(file) },
                            onFastExtract = { launchFastExtract(file) },
                            onRename = { renameTarget = file; showMenuFor = null },
                            onCopy = { clipboardFiles = listOf(file); isCutOperation = false; showMenuFor = null },
                            onCut = { clipboardFiles = listOf(file); isCutOperation = true; showMenuFor = null },
                            onDelete = { selectedEntry = file; showMenuFor = null },
                            onToggleFavorite = {
                                val nowFav = FavoritesStore.toggle(context, file.absolutePath)
                                favTick++
                                showMenuFor = null
                                Toast.makeText(
                                    context,
                                    if (nowFav) "Added \"${file.name}\" to Favorites"
                                    else "Removed \"${file.name}\" from Favorites",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onProperties = { propertiesTarget = file; showMenuFor = null },
                        )
                    }
                }
                }
            } else
            DeckDragFrame(modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // ".." as the first row of every listing below the floor — the Android file-manager
                // convention (issue #476); hidden at the root and while a search filter is active.
                if (searchQuery.isBlank() && currentDir != currentRoot) {
                    item(key = "..") { ParentFolderRow(compact = compactRows, onTap = { goUp() }) }
                }
                if (entries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Empty directory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    val shown = shownEntries
                    items(shown, key = { it.absolutePath }) { file ->
                        val isFav = remember(file.absolutePath, favTick) {
                            FavoritesStore.isFavorite(context, file.absolutePath)
                        }
                        FileItemRow(
                            file = file,
                            showActions = !pickMode,
                            compact = compactRows,
                            selectionMode = selectionMode,
                            selected = file.absolutePath in selectedPaths,
                            onLongPress = {
                                // In selection mode a long-press toggles; otherwise it opens the
                                // per-item context menu (matching the grid tiles).
                                if (!pickMode) {
                                    if (selectionMode) {
                                        selectedPaths = if (file.absolutePath in selectedPaths)
                                            selectedPaths - file.absolutePath
                                        else selectedPaths + file.absolutePath
                                    } else {
                                        showMenuFor = file
                                    }
                                }
                            },
                            onToggleSelect = {
                                selectedPaths = if (file.absolutePath in selectedPaths)
                                    selectedPaths - file.absolutePath
                                else selectedPaths + file.absolutePath
                            },
                            onTap = {
                                if (file.isDirectory) loadDirectory(file)
                                else if (pickMode) {
                                    if (matchesPickExt(file)) {
                                        pickPrefs.edit().putString("lastFilePickerDir", currentDir.absolutePath).apply()
                                        onPick?.invoke(file)
                                    }
                                }
                                else if (canRun(file)) runFile(file)
                            },
                            onMenu = { showMenuFor = file },
                            menuExpanded = showMenuFor == file,
                            onDismissMenu = { showMenuFor = null },
                            onSelect = {
                                selectionMode = true
                                selectedPaths = selectedPaths + file.absolutePath
                                showMenuFor = null
                            },
                            onRun = { runFile(file) },
                            onAddToShortcuts = { addToShortcuts(file) },
                            onCopy = { clipboardFiles = listOf(file); isCutOperation = false; showMenuFor = null },
                            onCut = { clipboardFiles = listOf(file); isCutOperation = true; showMenuFor = null },
                            onDelete = { selectedEntry = file; showMenuFor = null },
                            onRename = { renameTarget = file; showMenuFor = null },
                            onFastExtract = { launchFastExtract(file) },
                            onUnpack = { launchUnpack(file) },
                            isFavorite = isFav,
                            onToggleFavorite = {
                                val nowFav = FavoritesStore.toggle(context, file.absolutePath)
                                favTick++
                                showMenuFor = null
                                Toast.makeText(
                                    context,
                                    if (nowFav) "Added \"${file.name}\" to Favorites"
                                    else "Removed \"${file.name}\" from Favorites",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onProperties = { propertiesTarget = file; showMenuFor = null },
                        )
                    }
                }
            }
            }
            // material3 1.2.0's PullToRefreshContainer draws its indicator even at rest;
            // only show it while the user is actively pulling or a refresh is running.
            if (pullState.verticalOffset > 0.5f || pullState.isRefreshing) {
                PullToRefreshContainer(
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        }
            } // end content Box (beside the rail)
            // Locations rail lives on the RIGHT, like the other sections' side navigation.
            // The slim picker normally hides the rail; the add-a-game-from-C: flow (driveCPath set) is
            // the exception — it shows the rail so the "Drive C:" location item is reachable. Pickers
            // that don't pass driveCPath keep the rail hidden exactly as before.
            if ((!pickMode || driveCPath != null) && !deckMode) {
                CollapsibleRail(state = fmRailState, title = "Files", sections = locationSections, outlinedItems = true)
            }
            if (deckMode && (!pickMode || driveCPath != null)) {
                // Deck: every rail target (drives, quick folders, pinned favourites) plus the Favorites
                // view as a vertical list; rows select on focus so D-pad users land on the one they navigate to.
                val deckLocations = buildList {
                    add(RailItem("★ Favorites", Icons.Filled.Star, showFavorites) { showFavorites = true })
                    addAll(storageItems)
                    addAll(quickItems)
                    addAll(favItems)
                }
                if (deckLocations.size > 1 && !LocalDeckPortrait.current) {
                    DeckEditorCategoryRail(
                        labels = deckLocations.map { it.label },
                        selected = deckLocations.indexOfFirst { it.selected },
                        onSelect = { deckLocations.getOrNull(it)?.onClick?.invoke() },
                        selectOnFocus = true,
                        modifier = Modifier.width(if (LocalDeckPortrait.current) 112.dp else 170.dp).fillMaxHeight().padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        } // end rail + content Row
        // (New Folder moved into the top toolbar; the bottom bar was removed to reclaim its strip.)
    }
    }
}

// The context-menu item list shared by the list rows (FileItemRow) and the grid tiles
// (FileGridTile), so both open the identical menu. Gating (isDir/canRun/isInno/looksLikeArchive) is
// recomputed from [file] here — one source of truth for what each item shows. Every item dismisses
// the menu first, then runs its action.
@Composable
private fun FileContextMenuItems(
    file: File,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onRun: () -> Unit,
    onAddToShortcuts: () -> Unit,
    onUnpack: () -> Unit,
    onFastExtract: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProperties: () -> Unit,
    onDismissMenu: () -> Unit,
) {
    val isDir = file.isDirectory
    val canRun = isDir || file.name.lowercase().let { it.endsWith(".exe") || it.endsWith(".bat") || it.endsWith(".msi") || it.endsWith(".sh") }
    // Enter multi-select on this item (replaces the old long-press-only entry point).
    DropdownMenuItem(
        text = { Text("Select") },
        leadingIcon = { Icon(Icons.Filled.Checklist, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onSelect() },
    )
    MenuItemDivider()
    // Properties: basic info + Read-only / Hidden toggles, for ANY file or folder (handy for config
    // files like .txt/.cfg/.ini). Kept near the top since it's a common reason to open this menu.
    DropdownMenuItem(
        text = { Text("Properties") },
        leadingIcon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onProperties() },
    )
    MenuItemDivider()
    // Favorites are directories — only folders get the pin toggle.
    if (isDir) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
            leadingIcon = {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = { onToggleFavorite() },
        )
        MenuItemDivider()
    }
    if (canRun) {
        DropdownMenuItem(
            text = { Text("Run") },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onRun() },
        )
        MenuItemDivider()
    }
    // Only real PE executables can become a permanent Games tile — this reuses the
    // same importer the Games-tab "+" button uses (Exec=wine <path>), which is only
    // correct for .exe, so we don't offer it for .bat/.sh/.msi.
    if (!isDir && file.name.lowercase().endsWith(".exe")) {
        DropdownMenuItem(
            text = { Text("Add to Shortcuts") },
            leadingIcon = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onAddToShortcuts() },
        )
        MenuItemDivider()
    }
    // The SINGLE extraction action: the bundled 7-Zip engine handles a strict superset
    // of everything the old in-app extractor did (zip, 7z, tar, gzip, bzip2, xz, zstd)
    // PLUS disc images (ISO/UDF), RAR, cab, wim, split volumes and 80 GB+ single files.
    // For an InnoSetup repack (Setup.exe + Setup-*.bin) it becomes "Unpack / Install…",
    // where the screen decides between 7-Zip payload extraction and running Setup.exe in
    // a container (FreeArc repacks). The screen also content-sniffs (`7zz l`) so a file
    // is judged by content, not extension.
    val isInno = com.winhub.hernandez.core.unpack.SevenZip.isInnoSetup(file)
    // Content-aware: extension OR a cheap magic-byte sniff, so a .wcp/.bin/renamed
    // archive with an unlisted extension still gets the option (menu opens per row,
    // so this reads only a few header bytes on demand — never `7zz l` per entry).
    if (isInno || com.winhub.hernandez.core.unpack.SevenZip.looksLikeArchive(file)) {
        DropdownMenuItem(
            text = { Text(if (isInno) "Unpack / Install…" else "Unpack Archive…") },
            leadingIcon = { Icon(Icons.Filled.Unarchive, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onUnpack() },
        )
        MenuItemDivider()
        // Convenience: one tap, no screen — pre-fill defaults (new sibling folder, Auto
        // power) and start straight into the progress pill. Same engines/throughput.
        DropdownMenuItem(
            text = { Text("Fast Extract") },
            leadingIcon = { Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary) },
            onClick = { onDismissMenu(); onFastExtract() },
        )
        MenuItemDivider()
    }
    DropdownMenuItem(
        text = { Text("Rename") },
        leadingIcon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onRename() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Copy") },
        leadingIcon = { Icon(Icons.Filled.FileCopy, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onCopy() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Cut") },
        leadingIcon = { Icon(Icons.Filled.ContentCut, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onCut() },
    )
    MenuItemDivider()
    DropdownMenuItem(
        text = { Text("Delete") },
        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = { onDismissMenu(); onDelete() },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FileItemRow(
    file: File,
    showActions: Boolean = true,
    compact: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onTap: () -> Unit,
    onMenu: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onSelect: () -> Unit = {},
    onRun: () -> Unit,
    onAddToShortcuts: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onUnpack: () -> Unit = {},
    onFastExtract: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onProperties: () -> Unit = {},
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val isDir = file.isDirectory
    val isExe = !isDir && file.name.lowercase().let { it.endsWith(".exe") || it.endsWith(".bat") || it.endsWith(".msi") || it.endsWith(".sh") }
    // Image files show a real thumbnail instead of the generic file icon (handy when picking a
    // wallpaper/icon). Coil sizes the decode to the 36dp slot and caches it, so scrolling stays smooth.
    val isImage = !isDir && file.extension.lowercase() in IMAGE_THUMB_EXTS
    val deck = LocalDeckMode.current
    val rowShape = RoundedCornerShape(if (deck) 20.dp else 10.dp)
    // One focus stroke, not two: the Deck ring is drawn outside the card, so the card's own
    // hairline border hides while focused and the generic focus indication is suppressed.
    val rowInteraction = remember { MutableInteractionSource() }
    val rowFocused by rowInteraction.collectIsFocusedAsState()
    val rowIndication = LocalIndication.current

    // For real PE executables, try to pull out the embedded application icon (async, off the main thread).
    var exeIcon by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    if (!isDir && file.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.absolutePath) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(file) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .deckDragItem("fm-row-${file.absolutePath}")
            .then(if (deck) Modifier.deckOuterFocusStroke(rowInteraction, rowShape) else Modifier)
            .deckBringIntoView()
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = if (deck) null else rowIndication,
                // In selection mode a tap toggles instead of opening, so you can rattle through a
                // folder without long-pressing every single row.
                onClick = { if (selectionMode) onToggleSelect() else onTap() },
                onLongClick = onLongPress,
            ),
        shape = rowShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> DeckPalette.primary.copy(alpha = 0.18f)
                deck -> DeckPalette.surface
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = BorderStroke(
            1.dp,
            when {
                deck && rowFocused -> Color.Transparent
                selected -> DeckPalette.primary
                deck -> Color.Transparent
                else -> MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 8.dp),
        ) {
            if (selectionMode) {
                com.winhub.hernandez.ui.components.Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Spacer(Modifier.width(4.dp))
            }
            when {
                // Show the executable's own embedded icon when we managed to extract one.
                exeIcon != null -> Image(
                    bitmap = exeIcon!!,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
                isExe -> Icon(
                    painter = painterResource(R.drawable.icon_menu_container),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
                isDir -> Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
                // Real image preview. Falls back to the generic file icon while loading or on decode failure.
                isImage -> AsyncImage(
                    model = file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                    error = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                else -> Icon(
                    imageVector = Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(if (compact) 24.dp else 36.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (!isDir) append(StringUtils.formatBytes(file.length())).append("  \u2022  ")
                        append(dateFormat.format(Date(file.lastModified())))
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            if (showActions) Box {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.MoreVert, "Actions", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onDismissMenu,
                    modifier = Modifier.outlinedMenuCard(),
                ) {
                    FileContextMenuItems(
                        file = file,
                        isFavorite = isFavorite,
                        onSelect = onSelect,
                        onRun = onRun,
                        onAddToShortcuts = onAddToShortcuts,
                        onUnpack = onUnpack,
                        onFastExtract = onFastExtract,
                        onRename = onRename,
                        onCopy = onCopy,
                        onCut = onCut,
                        onDelete = onDelete,
                        onToggleFavorite = onToggleFavorite,
                        onProperties = onProperties,
                        onDismissMenu = onDismissMenu,
                    )
                }
            }
        }
    }
}

// Dedicated Favorites list that replaces the file list while the star toggle is on.
// Reads the store keyed on [favTick] so it recomposes after any pin/unpin.
@Composable
private fun FavoritesList(
    containers: List<Container>,
    imagefsDir: File,
    currentDir: File,
    favTick: Int,
    onPinCurrent: () -> Unit,
    onJump: (File) -> Unit,
    onUnpin: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val favorites = remember(favTick) {
        FavoritesStore.list(context).map(::File).filter { it.exists() }
    }
    val currentAlreadyPinned = remember(favTick, currentDir.absolutePath) {
        FavoritesStore.isFavorite(context, currentDir.absolutePath)
    }

    LazyColumn(modifier = modifier) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Favorites",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onPinCurrent,
                    enabled = !currentAlreadyPinned,
                ) {
                    Icon(Icons.Filled.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Pin current folder",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No favorites yet — pin a folder with its ⋮ menu to jump back here fast.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
        } else {
            items(favorites, key = { it.absolutePath }) { file ->
                val loc = remember(file.absolutePath, containers) {
                    describeLocation(file, containers, imagefsDir)
                }
                FavoriteCard(
                    file = file,
                    loc = loc,
                    onJump = { onJump(file) },
                    onUnpin = { onUnpin(file) },
                )
            }
        }
    }
}

// A single favourite — matches the FileItemRow card style (surfaceContainer + outline +
// RoundedCornerShape(10.dp)). Shows the folder name, a coloured drive badge + origin text,
// and the full display path; tapping jumps into it, the filled star unpins.
@Composable
private fun FavoriteCard(
    file: File,
    loc: FavLocation,
    onJump: () -> Unit,
    onUnpin: () -> Unit,
) {
    val (badgeBg, badgeFg) = badgeColors(loc)
    val deck = LocalDeckMode.current
    val favShape = RoundedCornerShape(if (deck) 20.dp else 10.dp)
    val favInteraction = remember { MutableInteractionSource() }
    val favFocused by favInteraction.collectIsFocusedAsState()
    val favIndication = LocalIndication.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .deckDragItem("fm-fav-${file.absolutePath}")
            .then(if (deck) Modifier.deckOuterFocusStroke(favInteraction, favShape) else Modifier)
            .deckBringIntoView()
            .clickable(
                interactionSource = favInteraction,
                indication = if (deck) null else favIndication,
                onClick = onJump,
            ),
        shape = favShape,
        colors = CardDefaults.cardColors(
            containerColor = if (deck) DeckPalette.surface else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            if (deck) (if (favFocused) Color.Transparent else DeckPalette.line)
            else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // Origin line: coloured drive badge + source description.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = loc.driveLabel,
                        color = badgeFg,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    when {
                        loc.containerName != null -> Row {
                            Text(
                                text = "Container: ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                            Text(
                                text = "\"${loc.containerName}\"",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        else -> Text(
                            text = when (loc.storage) {
                                FavStorage.INTERNAL -> "Internal storage"
                                FavStorage.SD -> "SD card"
                                FavStorage.CONTAINER -> "System files (shared)"  // Drive Z:
                                FavStorage.OTHER -> "Storage"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = loc.displayPath,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Remove from favorites",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * One entry in the File Manager's grid view: a big thumbnail with the name under it.
 *
 * Deliberately drops size and date — at this width they truncate to noise. The grid is for
 * recognising things by sight (screenshots, covers, game folders); the list stays the view for
 * reading details.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridTile(
    file: File,
    selectionMode: Boolean,
    selected: Boolean,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
    onTap: () -> Unit,
    onMenu: () -> Unit,
    menuExpanded: Boolean = false,
    onDismissMenu: () -> Unit = {},
    isFavorite: Boolean = false,
    onSelect: () -> Unit = {},
    onRun: () -> Unit = {},
    onAddToShortcuts: () -> Unit = {},
    onUnpack: () -> Unit = {},
    onFastExtract: () -> Unit = {},
    onRename: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onDelete: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onProperties: () -> Unit = {},
) {
    val isDir = file.isDirectory
    val isImage = !isDir && file.extension.lowercase() in IMAGE_THUMB_EXTS
    val deck = LocalDeckMode.current
    val tileShape = RoundedCornerShape(if (deck) 20.dp else 10.dp)
    val tileInteraction = remember { MutableInteractionSource() }
    val tileFocused by tileInteraction.collectIsFocusedAsState()
    val tileIndication = LocalIndication.current
    var exeIcon by remember(file.absolutePath) { mutableStateOf<ImageBitmap?>(null) }
    if (!isDir && file.name.lowercase().endsWith(".exe")) {
        LaunchedEffect(file.absolutePath) {
            val bmp = withContext(Dispatchers.IO) { PeIconExtractor.extract(file) }
            if (bmp != null) exeIcon = bmp.asImageBitmap()
        }
    }
    // The tile has no ⋮ button — long-press opens this menu, anchored to the Box around the Card.
    Box {
        Card(
            modifier = Modifier
                .padding(4.dp)
                .deckDragItem("fm-grid-${file.absolutePath}")
                .then(if (deck) Modifier.deckOuterFocusStroke(tileInteraction, tileShape) else Modifier)
                .deckBringIntoView()
                .combinedClickable(
                    interactionSource = tileInteraction,
                    indication = if (deck) null else tileIndication,
                    onClick = { if (selectionMode) onToggleSelect() else onTap() },
                    onLongClick = onLongPress,
                ),
            shape = tileShape,
            colors = CardDefaults.cardColors(
                containerColor = when {
                    selected -> DeckPalette.primary.copy(alpha = 0.18f)
                    deck -> DeckPalette.surface
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
            ),
            border = BorderStroke(
                1.dp,
                when {
                    deck && tileFocused -> Color.Transparent
                    selected -> DeckPalette.primary
                    deck -> Color.Transparent
                    else -> MaterialTheme.colorScheme.outline
                },
            ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    when {
                        exeIcon != null -> Image(bitmap = exeIcon!!, contentDescription = null, modifier = Modifier.size(48.dp))
                        isDir -> Icon(
                            Icons.Filled.Folder, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        isImage -> AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                            error = rememberVectorPainter(Icons.Filled.InsertDriveFile),
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        else -> Icon(
                            Icons.Filled.InsertDriveFile, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    if (selectionMode) {
                        com.winhub.hernandez.ui.components.Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // Always reserve two lines (issue #475): a one-line name used to make its card shorter than
                // its neighbours, so grid rows had ragged heights. Fixed min/max keeps every tile the same size.
                Text(
                    file.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    minLines = 2,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            modifier = Modifier.outlinedMenuCard(),
        ) {
            FileContextMenuItems(
                file = file,
                isFavorite = isFavorite,
                onSelect = onSelect,
                onRun = onRun,
                onAddToShortcuts = onAddToShortcuts,
                onUnpack = onUnpack,
                onFastExtract = onFastExtract,
                onRename = onRename,
                onCopy = onCopy,
                onCut = onCut,
                onDelete = onDelete,
                onToggleFavorite = onToggleFavorite,
                onProperties = onProperties,
                onDismissMenu = onDismissMenu,
            )
        }
    }
}

/**
 * Properties sheet for a single file or folder: basic info plus the two Windows/Wine file attributes
 * the in-container file manager (wfm.exe) also exposes — Read-only and Hidden. Each toggle applies
 * immediately (off the main thread) and refreshes the listing via [onChanged].
 *
 * State is keyed on the file so it always reflects the entry it was opened for. On a filesystem that
 * can't store Wine's DOSATTRIB xattr (the FUSE /storage volumes) only the Hidden toggle is disabled;
 * Read-only keeps working everywhere.
 */
@Composable
private fun FilePropertiesDialog(
    file: File,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var attrs by remember(file.absolutePath) { mutableStateOf<FileAttrState?>(null) }
    // Guards against a second toggle landing while the first is still being applied off-thread.
    var busy by remember(file.absolutePath) { mutableStateOf(false) }
    LaunchedEffect(file.absolutePath) {
        attrs = withContext(Dispatchers.IO) { readFileAttrs(file) }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // ── Basic info ──
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                PropertyLine("Location", file.parent ?: "—")
                PropertyLine(
                    "Type",
                    if (file.isDirectory) "Folder"
                    else file.extension.uppercase().let { if (it.isBlank()) "File" else "$it file" },
                )
                if (!file.isDirectory) PropertyLine("Size", StringUtils.formatBytes(file.length()))
                PropertyLine("Modified", dateFormat.format(Date(file.lastModified())))

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))

                val state = attrs
                // ── Read-only ── checked when the owner can't write (Wine's FILE_ATTRIBUTE_READONLY).
                AttributeToggleRow(
                    label = "Read-only",
                    description = "Stops games (and Wine) from overwriting or deleting this file.",
                    checked = state?.readOnly == true,
                    enabled = state != null && !busy,
                    onToggle = { want ->
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { setReadOnly(file, want) }
                            busy = false
                            if (ok) {
                                attrs = attrs?.copy(readOnly = want)
                                onChanged()
                            } else {
                                Toast.makeText(context, "Couldn't change Read-only", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                Spacer(Modifier.height(2.dp))
                // ── Hidden ── Wine's DOS hidden bit in the user.DOSATTRIB xattr.
                val hiddenSupported = state?.hiddenSupported == true
                AttributeToggleRow(
                    label = "Hidden",
                    description = if (state != null && !hiddenSupported)
                        "This storage can't store the hidden flag."
                    else "Marks the file hidden in Windows (Wine's hidden attribute).",
                    checked = state?.hidden == true,
                    enabled = state != null && hiddenSupported && !busy,
                    onToggle = { want ->
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { setHidden(file, want) }
                            busy = false
                            if (ok) {
                                attrs = attrs?.copy(hidden = want)
                                onChanged()
                            } else {
                                // The write failed after all — disable the toggle rather than lie.
                                attrs = attrs?.copy(hiddenSupported = false)
                                Toast.makeText(context, "Couldn't change Hidden on this storage", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// One "label: value" line in the Properties info block.
@Composable
private fun PropertyLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// A labelled attribute switch with a one-line description, used by the Properties sheet.
@Composable
private fun AttributeToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        com.winhub.hernandez.ui.components.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onToggle,
        )
    }
}

/** The ".." row: first entry of a listing, one folder up. Mirrors the list rows' height. */
@Composable
private fun ParentFolderRow(compact: Boolean, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .deckDragItem("fm-parent-row")
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ArrowUpward,
            contentDescription = "Parent folder",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (compact) 20.dp else 24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text("..", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!compact) Text("Parent folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Grid-mode counterpart of [ParentFolderRow]. */
@Composable
private fun ParentFolderTile(onTap: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(4.dp)
            .deckDragItem("fm-parent-tile")
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.ArrowUpward,
            contentDescription = "Parent folder",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text("..", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
