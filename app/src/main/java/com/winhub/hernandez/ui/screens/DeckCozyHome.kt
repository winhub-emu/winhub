package com.winhub.hernandez.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.store.rememberShownAvatar
import com.winhub.hernandez.ui.AccountAvatar
import kotlinx.coroutines.delay
import java.util.Date

/** Games per row in the "All games" grid. */
private const val GRID_COLUMNS = 5

/**
 * Landscape Home: the selected game as a hero card on the left, shelves of covers on the right.
 * Every cover, the hero buttons and the Add tile are ordinary focus targets, so the D-pad walks
 * them and the Deck drag ring follows; focusing a cover selects it, A opens its page, Y/X its menu.
 */
@Composable
internal fun DeckCozyHome(
    modifier: Modifier = Modifier,
    entries: List<HubEntry>,
    loaded: Boolean,
    selected: Int,
    favs: Set<String>,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onMenu: (Int) -> Unit,
    onAdd: () -> Unit,
    focus: FocusRequester,
    enabled: Boolean,
) {
    val current = entries.getOrNull(selected)
    val recent = remember(entries) { entries.indices.take(8) }
    val favourites = remember(entries, favs) { entries.indices.filter { entries[it].game?.shortcutPath in favs } }
    // -1 stands for the Add tile that leads the grid.
    val gridRows = remember(entries) { (listOf(-1) + entries.indices).chunked(GRID_COLUMNS) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 18.dp, end = 18.dp, top = 52.dp, bottom = 40.dp)
            .onPreviewKeyEvent { e ->
                if (!enabled || current == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (e.key == Key.ButtonY || e.key == Key.ButtonX) {
                    onMenu(selected)
                    true
                } else {
                    false
                }
            },
    ) {
        if (current == null) {
            if (loaded) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Your shelf is empty", color = Deck.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Add a game from your files, or pick one from a store.",
                        color = Deck.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    HubGradientButton("Add game", Modifier.focusRequester(focus), onClick = onAdd)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                CozyHero(
                    entry = current,
                    onPlay = { onPlay(selected) },
                    onDetails = { onOpen(selected) },
                    onMenu = { onMenu(selected) },
                    focus = focus,
                    modifier = Modifier.width(290.dp).fillMaxHeight(),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    item { CozyTitle("Recently played") }
                    item { CozyShelf("recent", recent, entries, selected, onSelect, onOpen, onMenu) }
                    if (favourites.isNotEmpty()) {
                        item { CozyTitle("Favourites") }
                        item { CozyShelf("fav", favourites, entries, selected, onSelect, onOpen, onMenu) }
                    }
                    item { CozyTitle("All games") }
                    items(gridRows) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { i ->
                                if (i == -1) {
                                    CozyAddTile(onAdd, Modifier.weight(1f))
                                } else {
                                    CozyCover("all:$i", entries[i], selected == i, { onSelect(i) }, { onOpen(i) }, { onMenu(i) }, Modifier.weight(1f))
                                }
                            }
                            repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CozyTitle(text: String) {
    Text(text, color = Deck.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CozyShelf(
    tag: String,
    indices: List<Int>,
    entries: List<HubEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onMenu: (Int) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        items(indices) { i ->
            CozyCover("$tag:$i", entries[i], selected == i, { onSelect(i) }, { onOpen(i) }, { onMenu(i) }, Modifier.width(100.dp))
        }
    }
}

/** The selected game: art with a fade, title, origin and playtime, then Play / Details / more. */
@Composable
private fun CozyHero(
    entry: HubEntry,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onMenu: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hue = remember(entry.title) { hueOf(entry.title) * 360f }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.55f, 0.65f), Color.hsv((hue + 40f) % 360f, 0.60f, 0.30f)))),
    ) {
        entry.bitmap?.let { bmp ->
            Image(
                bitmap = remember(bmp) { bmp.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.30f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
            ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
        ) {
            Text("Pick up where you left off", color = Color(0xFFFFD9BD), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                entry.title,
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hubOriginLabel(entry) + " · " + hubPlaytimeLabel(context, entry.title),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                HubGradientButton("Play", Modifier.focusRequester(focus), onClick = onPlay)
                DeckButton("Details", primary = false, onClick = onDetails)
                CozyIconButton(onMenu)
            }
        }
    }
}

@Composable
private fun CozyIconButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .deckDragSelf()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.White)
    }
}

/** One cover with its name under it; focus selects it and lifts it a little, the drag ring does the outline. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CozyCover(
    key: String,
    entry: HubEntry,
    isSelected: Boolean,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused || isSelected) 1.05f else 1f, label = "cozyCover")
    val hue = remember(entry.title) { hueOf(entry.title) * 360f }
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.58f, 0.62f), Color.hsv((hue + 40f) % 360f, 0.55f, 0.28f))))
                .deckBringIntoView()
                .deckDragItem(key)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onOpen,
                    onLongClick = onMenu,
                ),
        ) {
            val bmp = entry.bitmap
            if (bmp != null) {
                Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    entry.title.take(1).uppercase(),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 6.dp),
                )
            }
        }
        Text(
            entry.title,
            color = Deck.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CozyAddTile(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(20.dp))
                .background(Deck.surface)
                .deckBringIntoView()
                .deckDragSelf()
                .clickable(interactionSource = interaction, indication = null, onClick = onAdd),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Deck.primary, modifier = Modifier.size(30.dp))
            Text("Add game", color = Deck.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** "Win" + accent "Hub", the wordmark at the left of the landscape top bar. */
@Composable
internal fun DeckBarWordmark() {
    Row {
        Text("Win", color = Deck.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Hub", color = Deck.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** The clock and the Social Hub account's avatar, at the right of the landscape top bar. */
@Composable
internal fun DeckBarClockAvatar() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        SocialHubStore.init(context)
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(Date(now)) }
    val gh by SocialHubStore.account.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Deck.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        AccountAvatar(
            avatarUrl = gh?.let { rememberShownAvatar(it.login, it.avatarUrl) },
            size = 32.dp,
            fallbackTint = Deck.onSurfaceVariant,
        )
    }
}
