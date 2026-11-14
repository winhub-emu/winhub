package com.winhub.hernandez.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winhub.hernandez.store.download.StorePrimaryAction
import com.winhub.hernandez.ui.components.clickable
import com.winhub.hernandez.ui.deck.DeckGlyph
import com.winhub.hernandez.ui.deck.GlyphKind
import com.winhub.hernandez.ui.deck.deckLine
import com.winhub.hernandez.ui.deck.xmbFrame
import com.winhub.hernandez.ui.deck.xmbGlassCard
import com.winhub.hernandez.ui.screens.outlinedMenuCard
import kotlinx.coroutines.launch

// The game page of the XMB store style, in landscape, for every store (docs/design/winhub-game-page-design.html): the game's
// art fills the screen, the tabs, the content card and the actions sit at the left, the banner, the title and what the store
// says about the game at the right. Each store keeps its own state, handlers and tab bodies and hands them in as slots.

private val PageGutter = 24.dp
private val ActionHeight = 48.dp
private val CardShape = RoundedCornerShape(14.dp)

/** True where the XMB game page replaces the scrolling one: the XMB store style, with the screen wider than it is tall. */
@Composable
internal fun rememberXmbDetailPage(): Boolean {
    val config = LocalConfiguration.current
    return LocalStoreXmb.current && config.screenWidthDp > config.screenHeightDp
}

@Composable
internal fun XmbDetailPage(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    /** The game's art, filling its box: shown edge to edge behind the page. */
    art: @Composable BoxScope.() -> Unit,
    /** The same art as the banner at the right (a 460:215 box). */
    bannerArt: @Composable BoxScope.() -> Unit = art,
    topBarActions: @Composable RowScope.() -> Unit,
    tabLabels: List<String>,
    tabBadges: List<String?>,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    /** The first tab is plain text with nothing to focus: the pane itself takes focus so the D-pad can scroll it. */
    textFirstTab: Boolean,
    downloading: Boolean,
    installFrac: Float,
    downloadFrac: Float,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    /** "…% (done / total) · speed · ETA" while downloading, shown inside the progress button. */
    infoLine: String?,
    secondary: StorePrimaryAction.Button? = null,
    /** The selected branch's name, or null when there is no branch to choose. */
    branchLabel: String? = null,
    onBranch: () -> Unit = {},
    /** The ⚙ menu's items, or null when there is no menu. */
    gearMenu: (@Composable (dismiss: () -> Unit) -> Unit)?,
    /** Pinned under the card's scrolling body (the achievements caption). */
    caption: (@Composable () -> Unit)? = null,
    /** Under the title at the right. */
    info: @Composable ColumnScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    val tabCount = tabLabels.size
    val firstFocus = remember { FocusRequester() }
    var gearOpen by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) { scroll.scrollTo(0) }
    // Opens on the main action (or the ⚙ while a download runs, when the main button is read-only).
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    fun scrollPage(direction: Int) {
        if (viewportPx > 0) scope.launch { scroll.animateScrollBy(direction * viewportPx * 0.8f) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    // LB / RB switch tab, LT / RT page up / down, X opens the options: the same on every WinHub screen.
                    Key.ButtonL1 -> { if (tabCount > 1) onSelectTab((selectedTab - 1 + tabCount) % tabCount); true }
                    Key.ButtonR1 -> { if (tabCount > 1) onSelectTab((selectedTab + 1) % tabCount); true }
                    Key.ButtonL2 -> { scrollPage(-1); true }
                    Key.ButtonR2 -> { scrollPage(1); true }
                    Key.ButtonX -> if (gearMenu != null) { gearOpen = true; true } else false
                    else -> false
                }
            },
    ) {
        GameArtBackdrop(art)

        // ── Top bar ──────────────────────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = PageGutter, end = PageGutter, top = 12.dp),
        ) {
            val chipShape = RoundedCornerShape(17.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .steamFocusRing(chipShape)
                    .clip(chipShape)
                    .background(cs.surfaceVariant.copy(alpha = 0.6f))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 14.dp),
            ) {
                Text("‹", color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(backLabel, color = cs.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            topBarActions()
        }

        // ── Left: tabs, card, actions ────────────────────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.58f)
                .fillMaxHeight()
                .padding(start = PageGutter, top = 56.dp, bottom = 40.dp),
        ) {
            if (tabCount > 1) TabRail(tabLabels, tabBadges, selectedTab, onSelectTab)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .xmbGlassCard(CardShape)
                    .border(1.dp, deckLine(), CardShape),
            ) {
                val paneFocus = textFirstTab && selectedTab == 0
                var paneFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { viewportPx = it.height }
                        .then(
                            if (paneFocus) Modifier
                                .steamFocusRing(CardShape)
                                .onFocusChanged { paneFocused = it.isFocused }
                                .onKeyEvent { ev ->
                                    if (!paneFocused || ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    val step = with(density) { 96.dp.toPx() }
                                    when {
                                        ev.key == Key.DirectionDown && scroll.canScrollForward -> { scope.launch { scroll.animateScrollBy(step) }; true }
                                        ev.key == Key.DirectionUp && scroll.canScrollBackward -> { scope.launch { scroll.animateScrollBy(-step) }; true }
                                        else -> false
                                    }
                                }
                                .focusable()
                            else Modifier,
                        )
                        .verticalScroll(scroll),
                ) {
                    Column(Modifier.fillMaxWidth()) { body() }
                }
                caption?.invoke()
            }

            // Main action, the other actions and the ⚙ menu: reachable from every tab.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(ActionHeight),
            ) {
                val buttonShape = RoundedCornerShape(12.dp)
                if (downloading) {
                    ProgressButton(
                        installFrac = installFrac,
                        downloadFrac = downloadFrac,
                        label = primaryLabel,
                        infoLine = infoLine,
                        modifier = Modifier.weight(1.25f),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxHeight()
                            .focusRequester(firstFocus)
                            .steamFocusRing(buttonShape)
                            .clip(buttonShape)
                            .background(if (primaryEnabled) cs.primary else cs.primary.copy(alpha = 0.4f))
                            .clickable(enabled = primaryEnabled, onClick = onPrimary),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(cs.onPrimary),
                            ) { Text("A", color = cs.primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                            Text(primaryLabel, color = cs.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                if (secondary != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .steamFocusRing(buttonShape)
                            .clip(buttonShape)
                            .border(1.dp, if (secondary.enabled) cs.primary else deckLine(), buttonShape)
                            .clickable(enabled = secondary.enabled, onClick = secondary.onClick),
                    ) {
                        Text(
                            secondary.label,
                            color = if (secondary.enabled) cs.primary else cs.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                if (branchLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .steamFocusRing(buttonShape)
                            .clip(buttonShape)
                            .background(cs.surfaceVariant.copy(alpha = 0.6f))
                            .border(1.dp, deckLine(), buttonShape)
                            .clickable(onClick = onBranch)
                            .padding(horizontal = 14.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("BRANCH", color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                branchLabel,
                                color = cs.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("▾", color = cs.onSurfaceVariant, fontSize = 16.sp)
                    }
                }

                if (gearMenu != null) {
                    Box {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(ActionHeight)
                                .then(if (downloading) Modifier.focusRequester(firstFocus) else Modifier)
                                .steamFocusRing(buttonShape)
                                .clip(buttonShape)
                                .background(cs.surfaceVariant.copy(alpha = 0.6f))
                                .border(1.dp, deckLine(), buttonShape)
                                .clickable { gearOpen = true },
                        ) { Text("⚙", fontSize = 20.sp, color = cs.primary) }
                        DropdownMenu(
                            expanded = gearOpen,
                            onDismissRequest = { gearOpen = false },
                            modifier = Modifier.outlinedMenuCard(),
                        ) { gearMenu { gearOpen = false } }
                    }
                }
            }
        }

        // ── Right: banner, title, what the store says ────────────────────────────────────────────
        Column(
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.31f)
                .padding(end = PageGutter, top = 56.dp),
        ) {
            val bannerShape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(460f / 215f)
                    .shadow(12.dp, bannerShape)
                    .clip(bannerShape)
                    .background(cs.surfaceVariant)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), bannerShape),
            ) { bannerArt() }
            Text(
                text = title,
                color = cs.onSurface,
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            info()
        }

        // ── Button hints (the Back hint is the store screen's own, at the right) ─────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = PageGutter, bottom = 12.dp)
                .alpha(0.8f),
        ) {
            Hint("A", GlyphKind.A, "Select")
            if (gearMenu != null) Hint("X", GlyphKind.X, "Options")
            if (tabCount > 1) Hint("LB", GlyphKind.BUMPER, "Tabs", second = "RB")
            Hint("LT", GlyphKind.BUMPER, "Scroll", second = "RT")
        }

        overlay()
    }
}

/** The game's art, edge to edge (also under the camera strip), softened a little and darkened toward the left where the text sits. */
@Composable
private fun GameArtBackdrop(art: @Composable BoxScope.() -> Unit) {
    val frame = xmbFrame()
    val frameModifier = Modifier.layout { measurable, c ->
        // The page is padded by the camera frame: reach back out to the screen's edges.
        val extra = frame.roundToPx() * 2
        val w = c.maxWidth + extra
        val p = measurable.measure(Constraints.fixed(w, c.maxHeight))
        layout(c.maxWidth, c.maxHeight) { p.place(-extra / 2, 0) }
    }
    Box(frameModifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }
                .blur(6.dp),
        ) { art() }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0xED0A0A0C),
                    0.38f to Color(0xD10A0A0C),
                    0.66f to Color(0x750A0A0C),
                    1f to Color(0x2E0A0A0C),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x8C000000),
                    0.22f to Color.Transparent,
                    0.70f to Color.Transparent,
                    1f to Color(0xB8000000),
                ),
            ),
        )
    }
}

@Composable
private fun TabRail(labels: List<String>, badges: List<String?>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
    ) {
        DeckGlyph("LB", GlyphKind.BUMPER)
        labels.forEachIndexed { i, label ->
            val isSel = i == selected
            val shape = RoundedCornerShape(9.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .height(36.dp)
                    .steamFocusRing(shape)
                    .onFocusChanged { if (it.isFocused) onSelect(i) }
                    .clip(shape)
                    .background(if (isSel) cs.primary.copy(alpha = 0.17f) else cs.surfaceVariant.copy(alpha = 0.6f))
                    .border(1.dp, if (isSel) cs.primary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), shape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    label,
                    color = if (isSel) cs.primary else cs.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                badges.getOrNull(i)?.let { badge ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSel) cs.primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text(badge, color = if (isSel) cs.primary else cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        DeckGlyph("RB", GlyphKind.BUMPER)
    }
}

/** Download state: one read-only bar (the lighter fill is what has been fetched, the solid one what is on disk). */
@Composable
private fun ProgressButton(installFrac: Float, downloadFrac: Float, label: String, infoLine: String?, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(cs.surfaceContainerHigh)
            .border(1.dp, cs.outline, shape),
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(downloadFrac.coerceIn(0f, 1f)).background(cs.primary.copy(alpha = 0.4f)))
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(installFrac.coerceIn(0f, 1f)).background(cs.primary))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(label, color = cs.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!infoLine.isNullOrEmpty()) {
                Text(infoLine, color = cs.onSurface.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Hint(glyph: String, kind: GlyphKind, label: String, second: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        DeckGlyph(glyph, kind)
        if (second != null) DeckGlyph(second, kind)
        Text(label, color = Color(0xFFDDDDDD), fontSize = 12.sp, maxLines = 1)
    }
}
