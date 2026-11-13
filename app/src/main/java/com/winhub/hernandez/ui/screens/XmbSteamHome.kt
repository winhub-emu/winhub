package com.winhub.hernandez.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.winhub.hernandez.container.Shortcut
import com.winhub.hernandez.ui.deck.ScreenScale
import com.winhub.hernandez.ui.deck.XMB_FOCUS_OUTLINE
import com.winhub.hernandez.ui.deck.XmbTabPill
import com.winhub.hernandez.ui.deck.xmbBadgeEndRoom
import com.winhub.hernandez.ui.deck.XmbStoreMark
import com.winhub.hernandez.ui.deck.storeMarkOf
import com.winhub.hernandez.ui.deck.xmbInkColor
import com.winhub.hernandez.ui.simple.LabArt
import com.winhub.hernandez.ui.simple.LabPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

// The XMB Home in the Steam Deck library's look: the focused game's own landscape art fills the screen under a gradient to dark navy,
// one row of square-cornered covers sits low (the first game as a wide capsule, the focused card a touch larger and ringed with a thin
// light outline, a store mark chip at every card's corner), the focused game's title and "container · resolution" under it, and
// centred plain text tabs near the bottom. The geometry is in XmbSteamLogic.kt; ShortcutsXmbView keeps the focus model, the keys and
// the options column and draws this in place of the centred row.

/** The backdrop's gradient ends in this very dark navy, at [XMB_STEAM_FADE_MAX] of full strength at the bottom edge. */
private val XMB_STEAM_NAVY = Color(0xFF060B16)
private const val XMB_STEAM_FADE_MAX = 0.88f

/** The covers that are not the focused one are dimmed this much (an opaque black overlay, so the backdrop never shows through) while the options are open. */
private const val XMB_STEAM_DIM = 0.45f

/** A soft shadow under the title, the subtitle and the tabs: they sit over art that is no longer darkened. */
private val XMB_STEAM_TEXT = TextStyle(
    shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 6f),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Tight lines: no padding above or below a text's glyphs, so the title and the subtitle under it are exactly their gap apart. */
private val XMB_STEAM_TIGHT = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** The title's line box hugs its glyphs from above: the line is cut from below (the descenders hang out of it), so the subtitle can sit right under. */
private val XMB_STEAM_TOP = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.Both)

/** The subtitle's text: nearly white, a hair under the title's white. */
private val XMB_STEAM_MUTED = Color.White.copy(alpha = 0.9f)

/** The tabs' text: near white; the one under the cursor is pure white in a thin white outline pill. */
private val XMB_STEAM_TAB_INK = Color.White.copy(alpha = 0.97f)

/** The cards' corners are nearly square, as on the Steam Deck. */
private val XMB_STEAM_CORNER = 2.dp

/** A tab's text sits this far (dp) in from the edge of its highlight. */
private const val XMB_STEAM_TAB_PAD = 10f

/**
 * A game's landscape art for the backdrop: the pictures to try in order, made again only when the game or those sources change (two
 * of these are equal when both are, so a refresh that finds the same art is not a new picture to cross-fade to).
 */
private data class XmbSteamHero(val path: String, val models: List<Any>) {
    val pics: List<LabArt.Pic> = models.map { LabArt.Pic(it) }
}

/** The backdrop goes this blurry (dp) while a game's options are open (API 31+; below that it is only dimmed, harder). */
private const val XMB_STEAM_BACKDROP_BLUR = 24f

/**
 * [pics] faded in once one has loaded (nothing shows until then, nor when none loads), so what is under it stays until it is ready.
 * "Loaded" belongs to the sources, not to the list object: a list rebuilt with the same sources (LabArt's tick turns over whenever any
 * game's art is found) must not hide a picture that is already showing, as nothing would load it again.
 */
@Composable
private fun XmbSteamPicture(
    pics: List<LabArt.Pic>,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    var loaded by remember(pics.map { it.model }) { mutableStateOf(false) }
    val a by animateFloatAsState(if (loaded) 1f else 0f, tween(350), label = "xmbSteamArt")
    LabPicture(pics, modifier.graphicsLayer { alpha = a }, contentScale = contentScale, alignment = alignment, onLoaded = { loaded = true })
}

/**
 * The Home backdrop: [game]'s own landscape art (its own pick, Steam's, SteamGridDB's, found by LabArt off the main thread and kept on
 * the device) crossfading as the cursor moves, cropped to fill the whole screen (a landscape hero is scaled to the screen's height, a
 * portrait or low-res one to its width, so no band of anything else shows under it and there is no seam). A dark scrim over the top
 * fifth keeps the shell's header readable, and a soft gradient to dark navy starts at [fadeFrom] (a share of the screen's height: a little
 * above the text tabs, see [XMB_STEAM_FADE_LEAD]) and is darkest, a little under nine tenths opaque, at the bottom edge. [picture] (the cover) is the usual
 * blurred backdrop, which stays underneath: it shows only while the art loads and when there is none. [lift] (0 at rest, 1 with a game's
 * options open, glided over the options' own 300 ms) blurs the art layers by up to [XMB_STEAM_BACKDROP_BLUR] dp and dims them (API 31+;
 * below that, only a stronger dim), so the options' text reads; going back, the backdrop sharpens again.
 */
@Composable
internal fun XmbSteamBackdrop(game: Shortcut?, picture: Any?, fadeFrom: Float, lift: () -> Float = { 0f }) {
    val from = fadeFrom.coerceIn(0.25f, 0.97f)
    val context = LocalContext.current
    val path = game?.file?.path
    LaunchedEffect(path) { if (game != null) withContext(Dispatchers.IO) { LabArt.resolve(context, game) } }
    val tick = LabArt.tick
    val hero = remember(path, tick) { game?.let { g -> XmbSteamHero(g.file.path, LabArt.models(g, "bg")) } }
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(Modifier.fillMaxSize()) {
        // The art layers (the blurred cover and the hero) blur and dim as the options open; the gradient above them stays sharp.
        XmbSteamBlurred(lift, blurs) {
            XmbHomeBackdrop(picture)
            Crossfade(targetState = hero, animationSpec = tween(600), label = "xmbSteamHero") { h ->
                if (h != null) {
                    XmbSteamPicture(h.pics, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = xmbSteamBackdropDim(lift(), blurs) }.background(Color.Black))
        Box(
            Modifier.fillMaxSize().background(
                // Top: a dark scrim for the shell's header. Bottom: from [from] (clear) to the bottom edge, the navy's
                // strength eases in and out over several stops (0, .06, .23, .54, .85, 1 of full), so there is no visible start,
                // band or edge. This box is drawn over the hero art and under the content box (covers, title, tabs), and the shell's
                // hints are drawn over all of it.
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.55f),
                    0.20f to Color.Transparent,
                    from to XMB_STEAM_NAVY.copy(alpha = 0f),
                    from + (1f - from) * 0.15f to XMB_STEAM_NAVY.copy(alpha = XMB_STEAM_FADE_MAX * 0.064f),
                    from + (1f - from) * 0.30f to XMB_STEAM_NAVY.copy(alpha = XMB_STEAM_FADE_MAX * 0.231f),
                    from + (1f - from) * 0.50f to XMB_STEAM_NAVY.copy(alpha = XMB_STEAM_FADE_MAX * 0.538f),
                    from + (1f - from) * 0.75f to XMB_STEAM_NAVY.copy(alpha = XMB_STEAM_FADE_MAX * 0.846f),
                    1f to XMB_STEAM_NAVY.copy(alpha = XMB_STEAM_FADE_MAX),
                ),
            ),
        )
    }
}

/**
 * [content] blurred by up to [XMB_STEAM_BACKDROP_BLUR] dp as [lift] (0..1) goes to 1 (and not at all where blurring is not supported).
 * [lift] is read in the graphics layer, not here, so the 300 ms glide re-draws the layer without recomposing the content each frame.
 */
@Composable
private fun XmbSteamBlurred(lift: () -> Float, blurs: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val radius = if (blurs) xmbSteamBackdropBlur(lift(), XMB_STEAM_BACKDROP_BLUR).dp.toPx() else 0f
            // Like Modifier.blur: the blurred edge is clamped and cut to the box.
            clip = radius > 0.5f
            renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
        },
    ) { content() }
}

/**
 * The row of covers (the whole library, recent first), the focused game's title and the text tabs. [lift] is 0 normally and 1 while
 * the options are open: the card [liftIdx] (the focused one when they opened) glides to the left margin, lifted and larger, with the
 * column opening under it and the other covers continuing in a row to its right (dimmed with an opaque overlay), and the title and
 * tabs fade out. Going back, the row glides to where it rested. [tabsActive] = tab [tabSel], the one the host's tabs page last showed, is
 * highlighted (the tabs are the peek of that page; they never hold the cursor); [tabsEnabled] = they can be tapped; [badges] are the counts and [downloadRing] the Downloads tab's progress ring shown
 * after the tabs. [onCard] / [onTab] are taps.
 */
@Composable
internal fun XmbSteamHome(
    shortcuts: List<Shortcut>,
    pos: MutableFloatState,
    target: Int,
    liftIdx: Int,
    sl: XmbSteamLayout,
    accent: Color,
    ss: ScreenScale,
    focused: Shortcut?,
    sdBadge: @Composable (Shortcut) -> Unit,
    lift: () -> Float,
    tabs: List<XmbAction>,
    tabSel: Int,
    tabsActive: Boolean,
    tabsEnabled: Boolean,
    badges: Map<XmbSteamTab, Int>,
    downloadRing: XmbDownloadRing?,
    onCard: (Int) -> Unit,
    onTab: (Int) -> Unit,
    /** 1 = the row ends with an Add game card (slot [shortcuts].size), whose taps come to [onCard] like a game's. */
    extra: Int = 0,
) {
    val games = shortcuts.size
    val n = games + extra
    val row = remember(sl, n) { sl.row(n) }
    // Only the covers near the cursor are composed; each one's place is read from `pos` in its graphics layer.
    val base by remember { derivedStateOf { floor(pos.floatValue).toInt() } }
    Box(Modifier.fillMaxSize()) {
        for (i in xmbSteamWindow(base.toFloat(), n, sl.w, sl.fW + sl.gap)) {
            if (i >= games) {
                key("steam_add_game") {
                    XmbSteamAddCard(i, pos, sl, row, accent, i == target, liftIdx, lift) { onCard(i) }
                }
                continue
            }
            val s = shortcuts[i]
            key(s.file.path) {
                XmbSteamCover(s, i, pos, sl, row, accent, i == target, i == liftIdx, liftIdx, lift) { onCard(i) }
            }
        }
        XmbSteamTitle(focused, sdBadge, sl, row, target, pos, accent, ss, lift)
        if (tabs.isNotEmpty()) XmbSteamTabs(tabs, tabSel, tabsActive, tabsEnabled, badges, downloadRing, sl, accent, ss, lift, onTab)
    }
}

// A card's place in the row: its size, then its drawn size and position from its distance to the cursor, read in the graphics layer so a
// swipe re-draws without recomposing. All cards share a bottom edge, so the focused (larger) one grows upward. [lift] (0 at rest, 1 with
// the options open) glides the whole row to the options layout: raised, the card [liftIdx] at the left margin and drawn larger, the
// cards after it in a row to its right (see XmbSteamRow.xOpts). Nothing here fades: the dimming is an opaque overlay on the cover.
private fun Modifier.xmbSteamSlot(
    index: Int, pos: MutableFloatState, sl: XmbSteamLayout, row: XmbSteamRow, focused: Boolean, lifted: Boolean, liftIdx: Int, lift: () -> Float,
): Modifier = this
    .zIndex(if (focused || lifted) 1f else 0f)
    .size(sl.cardW(index, row.count).dp, sl.fH.dp)
    .graphicsLayer {
        val p = pos.floatValue
        val l = lift()
        val sc0 = xmbSteamScale(index, p)
        val sc = if (lifted) sc0 + (XMB_STEAM_OPTS_SCALE - sc0) * l else sc0
        translationX = row.xOpts(index, p, liftIdx, XMB_STEAM_OPTS_SCALE, l).dp.toPx()
        translationY = (sl.cardTop - sl.lift * l).dp.toPx()
        scaleX = sc
        scaleY = sc
        transformOrigin = TransformOrigin(0f, 1f)
    }

@Composable
private fun XmbSteamCover(
    shortcut: Shortcut,
    index: Int,
    pos: MutableFloatState,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    accent: Color,
    focused: Boolean,
    lifted: Boolean,
    liftIdx: Int,
    lift: () -> Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(XMB_STEAM_CORNER)
    val src = remember { MutableInteractionSource() }
    val mark = remember(shortcut) { storeMarkOf(shortcut) }
    // The store's mark is small and scales with the card (it is sized in dp inside the card's graphics layer).
    val markSize = (sl.fW * 0.20f).coerceIn(13f, 22f).dp
    Box(
        Modifier
            .xmbSteamSlot(index, pos, sl, row, focused, lifted, liftIdx, lift)
            .clip(shape)
            .background(Color(0xFF111111))
            .then(if (focused) Modifier.border(1.5.dp, XMB_FOCUS_OUTLINE, shape) else Modifier)
            .xmbClick(src, onClick),
    ) {
        if (index == 0) {
            XmbSteamWideArt(shortcut, accent)
        } else {
            val bmp = shortcut.icon
            if (bmp != null) {
                Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = shortcut.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                XmbMonogram(shortcut.name, accent)
            }
        }
        // Where the Steam Deck draws its controller badge: the store's mark straight on the cover (no chip, shadow or elevation behind
        // it), on every card, small, scaled with it.
        XmbStoreMark(mark, Modifier.align(Alignment.BottomEnd).padding(4.dp), markSize)
        // The cards that are not the lifted one are dimmed while the options are open: an opaque black overlay over the whole cover
        // (not a fade of the card, which would show the backdrop through it).
        if (!lifted) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = XMB_STEAM_DIM * lift() }.background(Color.Black))
        }
    }
}

/** The Add game card at the end of the row: a plain dark card with a plus, in the shape and the lift of the covers. */
@Composable
private fun XmbSteamAddCard(
    index: Int,
    pos: MutableFloatState,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    accent: Color,
    focused: Boolean,
    liftIdx: Int,
    lift: () -> Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(XMB_STEAM_CORNER)
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .xmbSteamSlot(index, pos, sl, row, focused, false, liftIdx, lift)
            .clip(shape)
            .background(Brush.verticalGradient(XMB_PLAIN_CARD))
            .then(if (focused) Modifier.border(1.5.dp, XMB_FOCUS_OUTLINE, shape) else Modifier)
            .xmbClick(src, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = accent, modifier = Modifier.size((sl.fW * 0.3f).dp))
            Text(
                "Add game", color = Color.White, fontSize = (sl.fW * 0.11f).coerceIn(10f, 16f).sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.padding(top = 4.dp),
            )
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = XMB_STEAM_DIM * lift() }.background(Color.Black))
    }
}

/**
 * The first game's wide card, in the shape of Steam's capsule: its landscape art (its own pick, what Steam publishes for the app, then
 * its background art). While that loads, and for as long as more may arrive (the lookup of the game's art is still running), the card
 * shows a neutral placeholder with the blurred cover, never the cover cropped to a wide card (a portrait cover cut like that shows a
 * face and nothing else). Only when every landscape source has failed and the lookup is done does the cropped cover show, and once
 * that is decided for this game it stays decided (art that arrives later fades in over it). All of it belongs to the game's path, not
 * to the slot: a game that moves to or from the first place composes this fresh.
 */
@Composable
private fun XmbSteamWideArt(s: Shortcut, accent: Color) {
    val context = LocalContext.current
    val path = s.file.path
    var resolved by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        withContext(Dispatchers.IO) { LabArt.resolveAndWait(context, s) }
        resolved = true
    }
    val tick = LabArt.tick
    val models = remember(path, tick) { xmbSteamWideOrder(LabArt.models(s, "wide"), LabArt.models(s, "bg")) }
    val pics = remember(models) { models.map { LabArt.Pic(it) } }
    var loaded by remember(path, models) { mutableStateOf(false) }
    var failed by remember(path, models) { mutableStateOf(false) }
    var coverDecided by remember(path) { mutableStateOf(false) }
    val cover = xmbWideCoverShows(failed, resolved, coverDecided)
    LaunchedEffect(cover) { if (cover) coverDecided = true }
    val coverA by animateFloatAsState(if (cover) 1f else 0f, tween(250), label = "xmbWideCover")
    val artA by animateFloatAsState(if (loaded) 1f else 0f, tween(350), label = "xmbSteamArt")
    val bmp = s.icon
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2230), Color(0xFF0C1018)))))
        // Modifier.blur does nothing below API 31, where the cover would show as it is: there the placeholder stays plain.
        if (bmp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Image(
                bitmap = remember(bmp) { bmp.asImageBitmap() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f; alpha = 0.55f }.blur(22.dp),
            )
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = coverA }) {
            if (bmp != null) {
                // The whole cover over the blurred one, sharp: cropped to the wide card it would be a zoomed, soft slice of it.
                Image(
                    bitmap = remember(bmp) { bmp.asImageBitmap() },
                    contentDescription = s.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                XmbMonogram(s.name, accent)
            }
        }
        LabPicture(
            pics,
            Modifier.fillMaxSize().graphicsLayer { alpha = artA },
            onNone = { SideEffect { failed = true } },
            onLoaded = { loaded = true },
        )
    }
}

// Under the focused card, left aligned with it (it follows the card as the row glides): its name, bold, and below it "container ·
// resolution" in tiny light capitals behind a small play arrow (an Android game's package name stands in).
@Composable
private fun XmbSteamTitle(
    focused: Shortcut?,
    sdBadge: @Composable (Shortcut) -> Unit,
    sl: XmbSteamLayout,
    row: XmbSteamRow,
    target: Int,
    pos: MutableFloatState,
    accent: Color,
    ss: ScreenScale,
    lift: () -> Float,
) {
    val context = LocalContext.current
    val ink = remember(accent) { xmbInkColor(accent, Color.White, XMB_STEAM_NAVY) }
    AnimatedContent(
        targetState = focused,
        contentKey = { it?.file?.path },
        transitionSpec = { xmbTitleTransition() },
        label = "xmbSteamTitle",
        modifier = Modifier
            .offset(0.dp, sl.titleY.dp - ss.dp(XMB_STEAM_TITLE_NUDGE))
            .width(sl.titleW.dp)
            .graphicsLayer {
                // A little to the right of the focused card's left edge: the text doesn't start flush with the cover.
                translationX = sl.titleX(row, target, pos.floatValue).dp.toPx() + ss.dp(XMB_STEAM_TITLE_INSET).toPx()
                alpha = 1f - lift()
            },
    ) { s ->
        if (s != null) {
            val meta = remember(s) { buildLaunchSpec(s, context).meta }
            Column {
                Text(
                    s.name, color = Color.White, fontSize = ss.sp(16f), fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // Drawn a little lower than its box (the free space above the subtitle): the subtitle stays where it is.
                    modifier = Modifier.offset(y = ss.dp(XMB_STEAM_TITLE_NUDGE)),
                    style = XMB_STEAM_TEXT.copy(lineHeight = ss.sp(15f), lineHeightStyle = XMB_STEAM_TOP),
                )
                // Right under the title's line box (its descenders hang over it): the two nearly touch.
                Row(Modifier.padding(top = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (meta.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ink.copy(alpha = 0.9f), modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            meta.uppercase(), color = XMB_STEAM_MUTED, fontSize = ss.sp(10f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            style = XMB_STEAM_TEXT.copy(lineHeight = ss.sp(10f), lineHeightStyle = XMB_STEAM_TIGHT),
                        )
                    }
                    sdBadge(s)
                }
            }
        }
    }
}

// The plain text tabs, the peek of the tabs page: small, upper case, letter-spaced, evenly spaced and centred on the screen. The one the
// page last showed is pure white in a thin white outline pill on a faint accent fill; the rest are bold light grey. A tab's count
// (a ring for the downloads, the friends online) is drawn as an overlay just right of its name, so it moves nothing.
@Composable
private fun XmbSteamTabs(
    tabs: List<XmbAction>,
    sel: Int,
    active: Boolean,
    enabled: Boolean,
    badges: Map<XmbSteamTab, Int>,
    downloadRing: XmbDownloadRing?,
    sl: XmbSteamLayout,
    accent: Color,
    ss: ScreenScale,
    lift: () -> Float,
    onTap: (Int) -> Unit,
) {
    val tabScroll = rememberScrollState()
    Box(
        Modifier
            .offset(0.dp, sl.tabsTop.dp)
            .fillMaxWidth()
            .height(XMB_STEAM_TABS_H.dp)
            .graphicsLayer { alpha = 1f - lift() },
        contentAlignment = Alignment.Center,
    ) {
        // Wider than the screen (a narrow one), the row scrolls. It takes the horizontal drag only then: a scroll modifier that has
        // nothing to scroll would still swallow a swipe that starts on the tabs, and the games row above could not be swiped from here.
        Row(
            Modifier.horizontalScroll(tabScroll, enabled = tabScroll.maxValue > 0).padding(horizontal = xmbBadgeEndRoom(ss)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { i, tab ->
                key(tab.label) {
                    val on = active && i == sel
                    val src = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .widthIn(min = 48.dp)
                            .then(if (enabled) Modifier.xmbClick(src) { onTap(i) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The badge is an overlay and the highlight is drawn behind: the tab is the size of its label with or without it.
                        XmbTabPill(
                            tab = XMB_PAGE_TABS.getOrNull(i), count = XMB_PAGE_TABS.getOrNull(i)?.let { badges[it] } ?: 0,
                            ring = downloadRing, ss = ss, padEnd = XMB_STEAM_TAB_PAD.dp,
                            fill = if (on) accent.copy(alpha = 0.22f) else Color.Transparent,
                            outline = if (on) Color.White else null,
                            outlineWidth = 1.5.dp,
                            accent = accent,
                        ) {
                            // The tabs are those of XMB_PAGE_TABS, in its order.
                            Text(
                                tab.label.uppercase(),
                                color = if (on) Color.White else XMB_STEAM_TAB_INK,
                                fontSize = ss.sp(11f),
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                style = XMB_STEAM_TEXT,
                                modifier = Modifier.padding(horizontal = XMB_STEAM_TAB_PAD.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
