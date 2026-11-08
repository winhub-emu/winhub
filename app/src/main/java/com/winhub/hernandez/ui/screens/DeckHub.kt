package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import android.provider.OpenableColumns
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.core.FileUtils
import com.winhub.hernandez.ui.moveFocusSafely
import coil.compose.AsyncImage
import com.winhub.hernandez.store.GameFacts
import com.winhub.hernandez.store.GoldbergComponent
import com.winhub.hernandez.store.GoldbergMode
import com.winhub.hernandez.store.GoldbergPatcher
import com.winhub.hernandez.store.SteamDatabase
import com.winhub.hernandez.store.SteamLiteComponent
import com.winhub.hernandez.store.SteamLoginActivity
import com.winhub.hernandez.store.SteamPrefs
import com.winhub.hernandez.store.SteamSessionManager
import com.winhub.hernandez.store.SteamStoreSearch
import com.winhub.hernandez.store.deckTileFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.min

/** One game on the hub carousel: display art plus the launchable shortcut (null = featured art only). */
internal data class HubEntry(
    val title: String,
    val bitmap: Bitmap?,
    val game: HubGame?,
)

/** A user-added game: where its .desktop lives, which container runs it, and its hub artwork. */
internal data class HubGame(
    val name: String,
    val containerId: Int,
    val shortcutPath: String,
    val appId: Int?,
    val coverPath: String?,
    /** Store this came from ("Steam", "Epic", "GOG", "Amazon"); null when added by hand. */
    val store: String? = null,
    /** Epoch ms when the game was added to the hub; 0 for entries from before this was recorded. */
    val addedAt: Long = 0L,
)

/** filesDir/hub_games.json — the user's Add Games picks, persisted across launches. */
internal object HubGamesStore {
    private const val FILE_NAME = "hub_games.json"

    fun load(context: Context): List<HubGame> {
        return runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.isFile) return emptyList()
            val raw = FileUtils.readString(file) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name")
            if (name.isEmpty()) return@mapNotNull null
            HubGame(
                name = name,
                containerId = o.optInt("containerId", -1),
                shortcutPath = o.optString("shortcutPath"),
                appId = if (o.isNull("appId")) null else o.optInt("appId"),
                coverPath = o.optString("coverPath").takeIf { it.isNotEmpty() },
                store = o.optString("store").takeIf { it.isNotBlank() && !it.equals("null", true) },
                addedAt = o.optLong("addedAt", 0L),
            )
        }
        }.getOrDefault(emptyList())
    }

    fun remove(context: Context, game: HubGame) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val raw = FileUtils.readString(file) ?: return
            val arr = JSONArray(raw)
            val out = JSONArray()
            (0 until arr.length()).forEach { i ->
                val o = arr.optJSONObject(i) ?: return@forEach
                if (o.optString("shortcutPath") != game.shortcutPath) out.put(o)
            }
            FileUtils.writeString(file, out.toString())
        }
    }

    /** Gives [game] a new cover file, keeping its place in the list. */
    fun setCover(context: Context, game: HubGame, coverPath: String) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val raw = FileUtils.readString(file) ?: return
            val arr = JSONArray(raw)
            (0 until arr.length()).forEach { i ->
                val o = arr.optJSONObject(i) ?: return@forEach
                if (o.optString("shortcutPath") == game.shortcutPath) o.put("coverPath", coverPath)
            }
            FileUtils.writeString(file, arr.toString())
        }
    }

    /** Points [old] at its renamed shortcut, keeping its place in the list. */
    fun replace(context: Context, old: HubGame, new: HubGame) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val raw = FileUtils.readString(file) ?: return
            val arr = JSONArray(raw)
            (0 until arr.length()).forEach { i ->
                val o = arr.optJSONObject(i) ?: return@forEach
                if (o.optString("shortcutPath") == old.shortcutPath) {
                    o.put("name", new.name)
                    o.put("shortcutPath", new.shortcutPath)
                }
            }
            FileUtils.writeString(file, arr.toString())
        }
    }

    fun add(context: Context, game: HubGame) {
        runCatching {
            val arr = JSONArray()
            val stamp = System.currentTimeMillis()
            // Re-adding the same shortcut must refresh, not duplicate, its Home card.
            val existing = load(context).filterNot { g ->
                if (game.shortcutPath.isNotBlank()) g.shortcutPath == game.shortcutPath
                else g.name == game.name
            }
            (existing + game).forEach { g ->
                arr.put(JSONObject().apply {
                    put("name", g.name)
                    put("containerId", g.containerId)
                    put("shortcutPath", g.shortcutPath)
                    put("appId", g.appId ?: JSONObject.NULL)
                    put("coverPath", g.coverPath ?: JSONObject.NULL)
                    put("store", g.store ?: JSONObject.NULL)
                    // Legacy entries have no timestamp; only the game being added now gets one.
                    put(
                        "addedAt",
                        when {
                            g.addedAt > 0L -> g.addedAt
                            g.shortcutPath == game.shortcutPath -> stamp
                            else -> 0L
                        },
                    )
                })
            }
            FileUtils.writeString(File(context.filesDir, FILE_NAME), arr.toString())
        }
    }
}

/** SAF uri → a real cache file (identifier/importer need a path). Blocking — call off the main thread. */
/**
 * Resolve a picked SAF uri to the on-disk executable IN PLACE — the same rule the old UI's
 * "+" importer uses, so the exe keeps its whole folder (dlls / data) beside it. Exotic providers
 * that can't be resolved fall back to a copy under Downloads/WinHub.
 */
internal fun resolvePickedGame(context: Context, uri: Uri): File? {
    val real = runCatching {
        if (uri.scheme == "file") return uri.path?.let { File(it) }
        if (uri.scheme != "content") return null
        if (!android.provider.DocumentsContract.isDocumentUri(context, uri)) return null
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        val split = docId.split(":", limit = 2)
        val type = split[0]
        val rel = if (split.size > 1) split[1] else ""
        when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                if ("primary".equals(type, ignoreCase = true)) {
                    "${android.os.Environment.getExternalStorageDirectory()}/$rel"
                } else {
                    "/storage/$type/$rel"
                }
            }
            else -> null
        }
    }.getOrNull()
    if (real != null) {
        val f = File(real)
        if (f.isFile) return f
    }
    return copyPickedGame(context, uri)
}

internal fun copyPickedGame(context: Context, uri: Uri): File? {
    return runCatching {
        val name = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "game.exe"
    // Land the copy on shared storage (Downloads/WinHub): app-private cacheDir is invisible
    // inside the container chroot, which left the shortcut's Exec path unresolvable.
    val preferred = File(
        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
        "WinHub",
    )
    val dir = if (preferred.isDirectory || preferred.mkdirs()) preferred
        else File(context.cacheDir, "hub_pick").apply { mkdirs() }
    val out = File(dir, name.replace('/', '_').ifEmpty { "game.exe" })
    val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream -> out.outputStream().use { stream.copyTo(it) } }
        out
    }.getOrNull()
}

/** Official Steam 600x900 library art for the hub tile, cached under filesDir/hub_covers. Blocking. */
internal fun fetchHubCover(context: Context, appId: Int): String? {
    return runCatching {
        val dir = File(context.filesDir, "hub_covers").apply { mkdirs() }
    val out = File(dir, "$appId.jpg")
    if (out.isFile && out.length() > 5000) return out.absolutePath
    val conn = URL(SteamStoreSearch.coverUrl(appId)).openConnection() as HttpURLConnection
    conn.connectTimeout = 15000
    conn.readTimeout = 30000
    conn.inputStream.use { ins -> out.outputStream().use { ins.copyTo(it) } }
        if (out.length() < 5000) { out.delete(); return null }
        out.absolutePath
    }.getOrNull()
}

/**
 * The Home tab's primary action: a dark-blue → signal-blue gradient pill, the DroidDeck
 * controller-friendly CTA. Used for "Play" on the game page and "Add Games" on home.
 */
@Composable
internal fun HubGradientButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(DeckPalette.primary)
            .deckOuterFocusStroke(
                interaction = interaction,
                shape = RoundedCornerShape(24.dp),
                focusColor = Color.White,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = text,
            color = DeckPalette.onPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * The games library: a 3D coverflow carousel. The selected game sits center, big, outlined in the
 * deck blue; neighbours fan out left and right, rotated toward the center and scaled down with
 * distance — tap or swipe (or D-pad focus + select) to move the selection.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HubCarousel(
    entries: List<HubEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onMenu: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    val current = selected.coerceIn(0, entries.size - 1)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.ButtonStart || event.key == Key.ButtonSelect || event.key == Key.Menu)
                ) {
                    onMenu(current)
                    return@onPreviewKeyEvent true
                }
                false
            }
            .pointerInput(entries.size, current) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = { total = 0f },
                    onDragCancel = { total = 0f },
                ) { _, dx ->
                    total += dx
                    if (total < -60f) {
                        total = 0f
                        onSelect((current + 1).coerceAtMost(entries.size - 1))
                    } else if (total > 60f) {
                        total = 0f
                        onSelect((current - 1).coerceAtLeast(0))
                    }
                }
            },
    ) {
        // One infinite clock for the selected cover's stroke: blue -> light blue, sliding around
        // the frame while the tile is focused.
        val shimmer = rememberInfiniteTransition(label = "hubStroke")
        val strokePhase by shimmer.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing)),
            label = "hubStrokePhase",
        )
        entries.forEachIndexed { index, entry ->
            val delta = index - current
            val distance = abs(delta)
            val isSelected = delta == 0

            val scale by animateFloatAsState(
                if (isSelected) 1.16f else 0.74f - 0.05f * min(distance, 3),
                label = "hubScale",
            )
            val rotY by animateFloatAsState(
                when {
                    isSelected -> 0f
                    delta < 0 -> 42f - 6f * min(distance, 3)
                    else -> -42f + 6f * min(distance, 3)
                },
                label = "hubRot",
            )
            val translateX by animateFloatAsState(delta * 96f, label = "hubX")
            val alphaAnim by animateFloatAsState(
                if (isSelected) 1f else 0.82f - 0.15f * min(distance, 3),
                label = "hubAlpha",
            )

            // Read in composition: the shimmer lambda draws with it, and a theme change must
            // invalidate that lambda instead of waiting for an unrelated recomposition.
            val shimmerAccent = DeckPalette.primary
            Box(
                modifier = Modifier
                    .size(width = 132.dp, height = 198.dp)
                    .zIndex((10 - distance).toFloat())
                    .offset(x = translateX.dp)
                    .graphicsLayer {
                        rotationY = rotY
                        scaleX = scale
                        scaleY = scale
                        alpha = alphaAnim
                        cameraDistance = 12f * density
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeckPalette.surface)
                    .then(
                        if (isSelected) {
                            Modifier.drawWithContent {
                                drawContent()
                                val w = 2.5.dp.toPx()
                                val span = size.width + size.height
                                val x = -span + span * 2f * strokePhase
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            shimmerAccent,
                                            lerp(shimmerAccent, Color.White, 0.55f),
                                            shimmerAccent,
                                        ),
                                        start = Offset(x, 0f),
                                        end = Offset(x + span, size.height),
                                    ),
                                    topLeft = Offset(w / 2f, w / 2f),
                                    size = Size(size.width - w, size.height - w),
                                    cornerRadius = CornerRadius(20.dp.toPx() - w / 2f),
                                    style = Stroke(width = w),
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .combinedClickable(
                        onClick = { if (isSelected) onOpen(index) else onSelect(index) },
                        onLongClick = { onMenu(index) },
                    ),
            ) {
                val bitmap = entry.bitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    val hue = remember(entry.title) { hueOf(entry.title) }
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color.hsv(hue, 0.55f, 0.75f),
                                        Color.hsv((hue + 0.12f) % 1f, 0.60f, 0.45f),
                                    ),
                                ),
                            ),
                    )
                }
                if (isSelected) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0.72f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.45f),
                                ),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * The modern Deck game page a carousel cover opens into: banner, title, origin, Metacritic /
 * HowLongToBeat and hours played, the store blurb, then Play + Settings.
 */
@Composable
internal fun DeckHubGamePage(
    entry: HubEntry,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var description by remember(entry.title) { mutableStateOf<String?>(null) }
    var bannerId by remember(entry.title) { mutableStateOf(entry.game?.appId?.takeIf { it > 0 }) }
    val scores = rememberDeckScores(entry.title)
    val origin = hubOriginLabel(entry)
    val hours = remember(entry.title) { hubPlaytimeLabel(context, entry.title) }

    LaunchedEffect(entry.title) {
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                val appId = bannerId ?: SteamStoreSearch.searchByName(entry.title).firstOrNull()?.appId
                appId to appId?.let { SteamStoreSearch.fetchDetails(it)?.shortDescription }
            }.getOrNull()
        }
        bannerId = resolved?.first ?: bannerId
        description = resolved?.second
    }
    val bannerUrl = bannerId?.let {
        "https://shared.steamstatic.com/store_item_assets/steam/apps/$it/header.jpg"
    }

    var pageIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { pageIn = true }
    val pageScale by animateFloatAsState(
        targetValue = if (pageIn) 1f else 0.9f,
        animationSpec = tween(240),
        label = "hubPageScale",
    )
    val pageAlpha by animateFloatAsState(
        targetValue = if (pageIn) 1f else 0f,
        animationSpec = tween(200),
        label = "hubPageAlpha",
    )
    val pageFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Controller-first: seed the page root, then hand focus to Play so A starts the game.
    LaunchedEffect(Unit) {
        runCatching { pageFocus.requestFocus() }
        kotlinx.coroutines.delay(180)
        runCatching { playFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(pageFocus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val dir = when (e.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                } ?: return@onKeyEvent false
                focusManager.moveFocusSafely(dir)
            }
            .graphicsLayer {
                scaleX = pageScale
                scaleY = pageScale
                alpha = pageAlpha
            }
            .background(DeckPalette.background)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 24.dp, top = 16.dp, bottom = 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(DeckPalette.surface),
            ) {
                val art: Any? = bannerUrl ?: entry.bitmap
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.20f),
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = origin,
                        color = lerp(DeckPalette.primary, Color.White, 0.55f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.title,
                        color = Color.White,
                        fontSize = 32.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.48f))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Back", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckHubStat("Hours played", hours)
                    DeckHubStat("Source", origin)
                }
                DeckScoresRow(scores, Modifier.padding(top = 10.dp))
                description?.takeIf { it.isNotBlank() }?.let { raw ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(DeckPalette.surface)
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "ABOUT",
                            color = DeckPalette.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = GameFacts.cleanText(raw),
                            color = DeckPalette.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubGradientButton(
                        text = "Play",
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = onPlay,
                    )
                    DeckButton("Settings", primary = false, onClick = onSettings)
                }
            }
        }
    }
}

@Composable
private fun DeckHubStat(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DeckPalette.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = DeckPalette.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = DeckPalette.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Local session clock written by XServerDisplayActivity into playtime_stats (ms). */
internal fun hubPlaytimeLabel(context: Context, name: String): String {
    val ms = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
        .getLong("${name}_playtime", 0L)
    if (ms < 60_000L) return "not played yet"
    val hours = ms / 3_600_000f
    return if (hours < 10f) String.format("%.1f h", hours) else "${hours.toInt()} h"
}

/** True once a game has at least a minute of tracked playtime. */
internal fun hubHasPlaytime(context: Context, name: String): Boolean =
    context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
        .getLong("${name}_playtime", 0L) >= 60_000L

/** "Steam" / "Epic" / "GOG" / "Amazon" for store-added games, "Custom" for hand-added ones. */
internal fun hubOriginLabel(entry: HubEntry): String =
    entry.game?.store?.takeIf { it.isNotBlank() && !it.equals("null", true) }
        // An appId alone only means the name matched a Steam title (cover lookup), not that the game is from Steam.
        ?: "Custom"

// ── Where an added game lives: an existing container (Master) vs its own clone (Cronos) ──────────
internal enum class HubAddMode { MASTER, CRONOS }

/**
 * The deck-styled "where should this game live?" menu the moment Add Games is used: Master
 * Containers (a shortcut inside a container you already have) vs Cronos (a fresh dedicated
 * container built just for this game). Picking a mode opens the system file picker.
 */
@Composable
internal fun DeckAddModeMenu(onPick: (HubAddMode) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .deckDialogNav(onDismiss)
                .padding(22.dp),
        ) {
            Text(
                "Add a game",
                color = DeckPalette.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Where should it live?",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )
            DeckAddModeCard(
                title = "Master Containers",
                desc = "Add it as a shortcut inside a container you already have.",
                icon = Icons.Filled.Layers,
            ) { onPick(HubAddMode.MASTER) }
            Spacer(Modifier.height(10.dp))
            DeckAddModeCard(
                title = "Cronos",
                desc = "A fresh dedicated container built just for this game — one game, one container.",
                icon = Icons.Filled.SportsEsports,
            ) { onPick(HubAddMode.CRONOS) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel", color = DeckPalette.onSurfaceVariant)
            }
        }
    }
}

/** One big gradient-edged card for [DeckAddModeMenu]. */
@Composable
private fun DeckAddModeCard(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(DeckPalette.primary.copy(alpha = 0.14f), DeckPalette.surfaceVariant),
                ),
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(DeckPalette.primary.copy(alpha = 0.85f), DeckPalette.primary.copy(alpha = 0.25f)),
                ),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DeckPalette.primary.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckPalette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                desc,
                color = DeckPalette.onSurfaceVariant,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DeckPalette.onSurfaceVariant)
    }
}

/**
 * "Is the game correct?" — the modernized confirm popup for a picked .exe: editable name field,
 * a container pick when there's more than one, and "Search on Steam" to scrape the store
 * metadata (name + official 600x900 art) before the game joins the carousel.
 */
@Composable
internal fun HubConfirmDialog(
    file: File,
    containerNames: List<String>,
    initialName: String,
    initialAppId: Int?,
    mode: HubAddMode = HubAddMode.MASTER,
    onDismiss: () -> Unit,
    onConfirm: (name: String, appId: Int?, containerIndex: Int, coverPath: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var appId by remember { mutableStateOf(initialAppId) }
    var coverPath by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SteamStoreSearch.SteamSuggestion>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var containerIdx by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .deckDialogNav(onDismiss)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Is the game correct?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeckPalette.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = file.name,
                    fontSize = 12.5.sp,
                    color = DeckPalette.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(14.dp))
                DeckConfigTextField("Game name", name, { name = it })

                if (mode == HubAddMode.CRONOS) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeckPalette.primary.copy(alpha = 0.12f))
                            .border(1.dp, DeckPalette.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = DeckPalette.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Cronos — a fresh dedicated container named after this game, with just this game's shortcut.",
                            color = DeckPalette.onSurfaceVariant,
                            fontSize = 11.5.sp,
                        )
                    }
                } else if (containerNames.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeckPalette.surfaceVariant)
                                .clickable { menuOpen = true }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(
                                text = "Container · ${containerNames[containerIdx.coerceIn(0, containerNames.size - 1)]}",
                                fontSize = 13.sp,
                                color = DeckPalette.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            modifier = Modifier.background(DeckPalette.surfaceVariant),
                        ) {
                            containerNames.forEachIndexed { i, label ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(label, color = DeckPalette.onBackground, fontSize = 14.sp) },
                                    onClick = { containerIdx = i; menuOpen = false },
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No container yet — create one in Containers first.",
                        fontSize = 12.5.sp,
                        color = DeckPalette.error,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HubGradientButton("Search on Steam") {
                        if (name.isBlank()) return@HubGradientButton
                        searching = true
                        results = null
                        scope.launch(Dispatchers.IO) {
                            val hits = SteamStoreSearch.searchByName(name)
                            withContext(Dispatchers.Main) {
                                results = hits
                                searching = false
                            }
                        }
                    }
                    if (searching) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(
                            color = DeckPalette.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                results?.let { hits ->
                    Spacer(Modifier.height(10.dp))
                    if (hits.isEmpty()) {
                        Text(
                            text = "No Steam matches — add it with the typed name.",
                            fontSize = 12.5.sp,
                            color = DeckPalette.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 190.dp)) {
                            items(hits) { hit ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            name = hit.name
                                            appId = hit.appId
                                            results = null
                                            scope.launch(Dispatchers.IO) {
                                                val path = fetchHubCover(context, hit.appId)
                                                withContext(Dispatchers.Main) { coverPath = path }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 11.dp),
                                ) {
                                    Text(
                                        text = hit.name,
                                        fontSize = 14.sp,
                                        color = DeckPalette.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Steam",
                                        fontSize = 11.sp,
                                        color = DeckPalette.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                if (coverPath != null && appId != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val cover = remember(coverPath) { BitmapFactory.decodeFile(coverPath) }
                        if (cover != null) {
                            Image(
                                bitmap = cover.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 46.dp, height = 69.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(
                            text = "Steam match found ✓",
                            fontSize = 13.sp,
                            color = DeckPalette.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel", color = DeckPalette.onBackground)
                    }
                    Spacer(Modifier.width(10.dp))
                    HubGradientButton("Add") {
                        onConfirm(name.trim().ifEmpty { file.nameWithoutExtension }, appId, if (mode == HubAddMode.CRONOS) -1 else containerIdx, coverPath)
                    }
                }
            }
        }
    }
}

// ── Deck-styled launch-method menu + the SteamLite/Goldberg launch pipeline behind it ──────────────
// Every shortcut gets the old UI's full method list — SteamLite (real client) / Goldberg (offline
// emulator) / Raw .exe — because the old sheet is reachable from Games for any source too.
@Composable
internal fun DeckLaunchMethodDialog(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    steamOverride: Boolean? = null,
    heading: String = "Choose how to launch",
    confirmLabel: String = "Launch",
    onLaunch: (mode: String, goldbergMode: GoldbergMode?, remember: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val isSteam = remember(shortcut, steamOverride) { steamOverride ?: isSteamOriginShortcut(shortcut) }
    val appId = remember(shortcut) { steamAppIdOf(shortcut) }
    SteamPrefs.init(context)
    var method by remember(shortcut) {
        mutableStateOf(
            when (shortcut.getExtra("launchMode", "")) {
                "Goldberg" -> "Goldberg"
                "Raw" -> "Raw"
                "RealSteam" -> "RealSteam"
                else -> if (isSteam) "RealSteam" else "Raw"
            }.let { if (isSteam) it else "Raw" },
        )
    }
    var goldbergMode by remember(shortcut) {
        mutableStateOf(SteamPrefs.getGoldbergMode(appId).let { if (it == GoldbergMode.OFF) GoldbergMode.REGULAR else it })
    }
    var rememberChoice by remember(shortcut) { mutableStateOf(shortcut.getExtra("launchModeRemembered", "") == "1") }

    val methods = listOf(
        Triple("RealSteam", "SteamLite", "Real Steam client - online, VAC, cloud saves"),
        Triple("Goldberg", "Goldberg", "Offline Steam emulator - no login"),
        Triple("Raw", "Raw .exe", "Run the game directly - no Steam layer"),
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .deckDialogNav(onDismiss)
                .padding(22.dp),
        ) {
            Text(
                shortcut.name,
                color = DeckPalette.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                heading,
                color = DeckPalette.onSurfaceVariant,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp),
            )
            methods.forEach { (mode, title, desc) ->
                val picked = method == mode
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (picked) DeckPalette.primary.copy(alpha = 0.16f) else DeckPalette.surfaceVariant)
                        .then(if (picked) Modifier.border(2.dp, DeckPalette.primary, RoundedCornerShape(20.dp)) else Modifier)
                        .clickable { method = mode }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = if (picked) DeckPalette.primary else DeckPalette.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            color = if (picked) DeckPalette.primary else DeckPalette.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            desc,
                            color = DeckPalette.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    if (picked) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = DeckPalette.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { rememberChoice = !rememberChoice }
                    .padding(vertical = 10.dp),
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (rememberChoice) DeckPalette.primary else Color.Transparent)
                        .border(1.dp, if (rememberChoice) DeckPalette.primary else DeckPalette.line, RoundedCornerShape(6.dp)),
                ) {
                    if (rememberChoice) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = DeckPalette.onPrimary,
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.Center),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Remember this choice",
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Spacer(Modifier.height(14.dp))
            HubGradientButton(
                confirmLabel,
                onClick = { onLaunch(method, if (method == "Goldberg") goldbergMode else null, rememberChoice) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel", color = DeckPalette.onSurfaceVariant)
            }
        }
    }
}

/** How long Home's launch zoom plays (ms) before the game's own window opens. */
internal const val DECK_LAUNCH_ZOOM_MS = 240

/**
 * The launch pipeline behind the deck menu: Goldberg (patch-then-launch, component on demand),
 * SteamLite (component on demand → Steam pre-flight) and Raw (direct), mirroring BigPictureScreen's
 * flow with deck-styled progress chrome. [request] non-null opens the method menu for that shortcut.
 */
@Composable
internal fun DeckLaunchStack(
    activity: Activity,
    request: Shortcut?,
    onMenuClosed: () -> Unit,
    steamOverride: Boolean? = null,
    /** Called as a launch begins; the game's window then waits [DECK_LAUNCH_ZOOM_MS] so the host can play its transition. */
    onLaunching: ((Shortcut) -> Unit)? = null,
) {
    val context = LocalContext.current
    var preflightFor by remember { mutableStateOf<Shortcut?>(null) }
    var componentDownloadFor by remember { mutableStateOf<Shortcut?>(null) }
    var componentDownloadLabel by remember { mutableStateOf("") }
    var componentDownloadProgress by remember { mutableStateOf(0f) }

    fun go(s: Shortcut, preflightDone: Boolean = false) {
        val splash = onLaunching
        if (splash == null) {
            launchShortcut(activity, s, preflightDone)
            return
        }
        splash(s)
        val zoomColor = Deck.primary.toArgb()
        activity.window.decorView.postDelayed(
            {
                if (!activity.isFinishing) {
                    launchShortcut(activity, s, preflightDone, zoomColor)
                    // No system slide: the theme colour carries straight into the loading screen.
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(0, 0)
                }
            },
            DECK_LAUNCH_ZOOM_MS.toLong(),
        )
    }

    fun launchWithGoldberg(s: Shortcut, gm: GoldbergMode) {
        val appId = steamAppIdOf(s)
        SteamPrefs.init(context)
        SteamPrefs.setGoldbergMode(appId, gm)
        val applyThenLaunch = {
            Thread({
                val installDir = runCatching {
                    SteamDatabase.getInstance(context).getGame(appId)?.installDir
                }.getOrNull().orEmpty()
                activity.runOnUiThread {
                    if (installDir.isEmpty()) {
                        go(s)
                    } else {
                        GoldbergPatcher.applyModeAsync(context, appId, installDir, s.name, gm) { _, _ ->
                            go(s)
                        }
                    }
                }
            }, "deck-goldberg-apply-launch").start()
        }
        if (!GoldbergComponent.isInstalled(context)) {
            componentDownloadFor = s
            componentDownloadLabel = "Steam Emulator (Goldberg)"
            componentDownloadProgress = 0f
            GoldbergComponent.downloadAsync(
                context,
                { f -> componentDownloadProgress = f },
                { ok, msg ->
                    componentDownloadFor = null
                    if (ok) applyThenLaunch()
                    else Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
            )
        } else applyThenLaunch()
    }

    fun launchWithSteamLite(s: Shortcut) {
        if (!SteamLiteComponent.isInstalled(context)) {
            componentDownloadFor = s
            componentDownloadLabel = "SteamLite (Real Steam / VAC)"
            componentDownloadProgress = 0f
            SteamLiteComponent.downloadAsync(
                context,
                { f -> componentDownloadProgress = f },
                { ok, msg ->
                    componentDownloadFor = null
                    if (ok) preflightFor = s
                    else Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
            )
        } else {
            preflightFor = s
        }
    }

    request?.let { s ->
        // Honour "Remember this choice" from an earlier launch (same contract the classic launch
        // path uses in ShortcutsScreen/BigPictureScreen): skip the chooser and go straight into the
        // remembered mode. Without this the deck always reopened the method dialog.
        val rememberedMode = s.getExtra("launchMode", "")
        val remembered = rememberedMode.isNotEmpty() && s.getExtra("launchModeRemembered", "") == "1"
        if (remembered) {
            LaunchedEffect(s) {
                // Launch first, then close the menu: closing first would dispose this effect (and
                // cancel the coroutine) before the launch ever ran.
                when (rememberedMode) {
                    "Goldberg" -> {
                        val appId = steamAppIdOf(s)
                        SteamPrefs.init(context)
                        val gm = SteamPrefs.getGoldbergMode(appId)
                            .let { if (it == GoldbergMode.OFF) GoldbergMode.REGULAR else it }
                        launchWithGoldberg(s, gm)
                    }
                    "Raw" -> go(s)
                    else -> launchWithSteamLite(s)
                }
                onMenuClosed()
            }
        } else {
            DeckLaunchMethodDialog(
                shortcut = s,
                onDismiss = onMenuClosed,
                steamOverride = steamOverride,
                onLaunch = { mode, gm, rememberChoice ->
                    onMenuClosed()
                    s.putExtra("launchMode", mode)
                    s.putExtra("launchModeRemembered", if (rememberChoice) "1" else "0")
                    s.saveData()
                    when (mode) {
                        "Goldberg" -> launchWithGoldberg(s, gm ?: GoldbergMode.REGULAR)
                        "Raw" -> go(s)
                        else -> launchWithSteamLite(s)
                    }
                },
            )
        }
    }

    componentDownloadFor?.let {
        Dialog(onDismissRequest = { }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier
                    .fillMaxWidth(0.85f)
                    .clip(DeckPalette.groupShape)
                    .background(DeckPalette.surface)
                    .padding(22.dp),
            ) {
                Text(
                    "Downloading $componentDownloadLabel",
                    color = DeckPalette.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(DeckPalette.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(componentDownloadProgress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(DeckPalette.primary),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(componentDownloadProgress.coerceIn(0f, 1f) * 100).toInt()}% - the game launches when this finishes.",
                    color = DeckPalette.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }

    preflightFor?.let { s ->
        val appId = steamAppIdOf(s)
        val installDir = remember(s) {
            runCatching { SteamDatabase.getInstance(context).getGame(appId)?.installDir }.getOrNull().orEmpty()
        }
        val savePrefs = remember { context.getSharedPreferences("save_manager_prefs", Context.MODE_PRIVATE) }
        SteamPreflightDialog(
            shortcut = s,
            request = SteamSessionManager.PreflightRequest(
                appId = appId,
                installDir = installDir,
                gameName = s.name,
                pullCloudSaves = savePrefs.getBoolean("auto_download_steam_on_launch", true),
            ),
            onLaunch = { preflightFor = null; go(s, preflightDone = true) },
            onDismiss = { preflightFor = null },
            onSignIn = {
                preflightFor = null
                context.startActivity(Intent(context, SteamLoginActivity::class.java))
            },
            onGoldberg = {
                preflightFor = null
                SteamPrefs.init(context)
                launchWithGoldberg(s, SteamPrefs.getGoldbergMode(appId).let { if (it == GoldbergMode.OFF) GoldbergMode.REGULAR else it })
            },
            onUpdate = { _ -> preflightFor = null },
        )
    }
}

/**
 * The deck-styled game menu: long-press a carousel tile (touch) or press Options/Select (controller).
 * Play / Settings (opens this game's container editor) / Add to Home Screen / Properties / Delete.
 */
@Composable
internal fun DeckGameMenu(
    title: String,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onLaunchMethod: () -> Unit,
    onAddToHomeScreen: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val menuScale by animateFloatAsState(if (shown) 1f else 0.88f, label = "gameMenuScale")
    val menuAlpha by animateFloatAsState(if (shown) 1f else 0f, label = "gameMenuAlpha")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.78f)
                .graphicsLayer {
                    scaleX = menuScale
                    scaleY = menuScale
                    alpha = menuAlpha
                }
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .deckDialogNav(onDismiss)
                .padding(vertical = 10.dp),
        ) {
            Text(
                title,
                color = DeckPalette.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
            DeckGameMenuRow("Play", Icons.Filled.PlayArrow, onClick = onPlay)
            DeckGameMenuRow("Launch method\u2026", Icons.Filled.Layers, onClick = onLaunchMethod)
            DeckGameMenuRow("Settings", Icons.Filled.Settings, onClick = onSettings)
            DeckGameMenuRow("Add to Home Screen", Icons.Filled.Home, onClick = onAddToHomeScreen)
            DeckGameMenuRow("Properties", Icons.Filled.Info, onClick = onProperties)
            DeckGameMenuRow("Delete", Icons.Filled.Delete, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun DeckGameMenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) DeckPalette.error else DeckPalette.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (danger) DeckPalette.error else DeckPalette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Post-create Cronos check: the key launch-relevant settings of the container that was just
 * built (wine, graphics driver, DX wrapper + versions, Box64/FEXCore preset, screen, audio) so
 * the user can confirm them or jump straight into the container editor.
 */
@Composable
internal fun DeckCronosReviewDialog(
    container: com.winhub.hernandez.container.Container,
    onKeep: () -> Unit,
    onEdit: () -> Unit,
) {
    val cfg = remember(container) { runCatching { container.getDXWrapperConfig() }.getOrNull() ?: "" }
    val dxvkVer = remember(cfg) { Regex("""(?:^|,)version=([^,]+)""").find(cfg)?.groupValues?.get(1) }
    val vkd3dVer = remember(cfg) { Regex("""(?:^|,)vkd3dVersion=([^,]+)""").find(cfg)?.groupValues?.get(1) }
    // Every value is coerced to a non-null String: a container whose config predates a field (or
    // was created without a defaults profile) hands back null, and Text(null) NPEs the moment this
    // window composes — which is exactly how the post-create Cronos check crashed the app.
    val rows = remember(container, dxvkVer, vkd3dVer) {
        listOf(
            "Wine" to (runCatching { container.wineVersion }.getOrNull() ?: "?"),
            "Graphics driver" to (runCatching { container.graphicsDriver }.getOrNull() ?: "?"),
            "DX wrapper" to buildString {
                append(runCatching { container.getDXWrapper() }.getOrNull() ?: "?")
                dxvkVer?.let { append(" (dxvk ") ; append(it); append(")") }
                vkd3dVer?.let { append(" / vkd3d ") ; append(it) }
            },
            "Emulator" to (runCatching { container.emulator }.getOrNull() ?: "?"),
            "Box64 preset" to (runCatching { container.box64Preset }.getOrNull() ?: "-"),
            "FEXCore preset" to (runCatching { container.getFEXCorePreset() }.getOrNull() ?: "-"),
            "Screen size" to (runCatching { container.screenSize }.getOrNull() ?: "?"),
            "Audio" to (runCatching { container.audioDriver }.getOrNull() ?: "?"),
        )
    }

    Dialog(onDismissRequest = onKeep, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.88f)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .deckDialogNav(onKeep)
                .padding(22.dp),
        ) {
            Text(
                "Cronos created - are these settings right?",
                color = DeckPalette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                runCatching { container.name }.getOrNull() ?: "",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(
                        label,
                        color = DeckPalette.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        modifier = Modifier.width(130.dp),
                    )
                    Text(
                        value,
                        color = DeckPalette.onBackground,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HubGradientButton("Settings are good", onClick = onKeep, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Edit settings", color = DeckPalette.onSurfaceVariant)
            }
        }
    }
}
