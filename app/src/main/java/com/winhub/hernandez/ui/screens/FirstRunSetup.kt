package com.winhub.hernandez.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.store.GitHubAuthClient
import com.winhub.hernandez.store.SocialHubStore
import com.winhub.hernandez.ui.components.ColorPicker
import com.winhub.hernandez.ui.screens.contents.ContentsHubViewModel
import com.winhub.hernandez.ui.screens.contents.ContentsTypes
import com.winhub.hernandez.ui.screens.contents.HubSource
import com.winhub.hernandez.ui.screens.contents.RemoteSourceRepository
import com.winhub.hernandez.ui.theme.AppThemeState
import com.winhub.hernandez.ui.theme.CUSTOM_PRESET_INDEX
import com.winhub.hernandez.ui.theme.themePresets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private val WIZARD_STEPS = listOf("Layout", "Theme", "Contents", "Social Hub", "Install")
private const val LAST_STEP = 3
private const val LAYOUT = 0
private const val THEME = 1
private const val CONTENTS = 2
private const val SOCIAL = 3

/** One ticked extra, with where it came from so the install plan can say so. */
internal data class ExtraPick(val content: FirstRunContent, val type: String, val source: String)

/**
 * Wizard state lives here, not in composition: choosing a layout rotates the activity, which
 * would otherwise throw the user back to the first step.
 */
internal class FirstRunViewModel : ViewModel() {
    val step = MutableStateFlow(0)
    val extras = MutableStateFlow<Map<String, ExtraPick>>(emptyMap())
    val repo = MutableStateFlow<String?>(null)
    val socialSkipped = MutableStateFlow(false)

    fun toggle(item: ContentsHubViewModel.CatalogItem, label: String) {
        extras.update { current ->
            if (item.downloadUrl in current) current - item.downloadUrl
            else current + (item.downloadUrl to ExtraPick(
                FirstRunContent(label, item.downloadUrl, item.isDriver), item.type, item.sourceName,
            ))
        }
    }
}

/**
 * First-open wizard shown before the system files and components are installed: layout, theme,
 * extra contents (the Contents tab's browser) and Social Hub sign-in. [onInstall] receives the
 * extras to install after the four pinned components.
 */
@Composable
internal fun FirstRunSetup(onInstall: (List<FirstRunContent>) -> Unit) {
    val vm: FirstRunViewModel = viewModel()
    val hub: ContentsHubViewModel = viewModel()
    val step by vm.step.collectAsState()
    val extras by vm.extras.collectAsState()
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val editTracker = remember { DeckEditTracker() }
    val stepFocus = remember { FocusRequester() }

    val category by hub.categoryType.collectAsState()
    val query by hub.query.collectAsState()

    fun browserAtRoot() = query.isBlank() && category == null && vm.repo.value == null
    fun browserUp() {
        when {
            query.isNotBlank() -> hub.setQuery("")
            vm.repo.value != null -> vm.repo.value = null
            else -> hub.selectCategory(null)
        }
    }
    fun goTo(target: Int) {
        if (target in 0..LAST_STEP) vm.step.value = target
    }
    fun next() {
        if (step < LAST_STEP) {
            goTo(step + 1)
        } else {
            // The Contents tab shares this view model; leave it on its family list.
            hub.setQuery("")
            hub.selectCategory(null)
            onInstall(extras.values.map { it.content })
        }
    }
    // B goes up one level: out of a repository, out of a family, then to the previous step.
    // The first step has nothing behind it and must not close the app.
    fun back() {
        if (step == CONTENTS && !browserAtRoot()) browserUp() else goTo(step - 1)
    }

    BackHandler(enabled = true) { back() }
    // The layout step starts on the orientation the device is held in. Save that now, so a user who
    // just taps Next gets the Deck layout the preview showed instead of the unset default.
    LaunchedEffect(Unit) {
        if (AppOrientation.mode(ctx) == AppOrientation.AUTO) {
            AppOrientation.setMode(ctx, initialOrientation(ctx, config.screenWidthDp > config.screenHeightDp))
        }
    }
    LaunchedEffect(step) {
        delay(120)
        runCatching { stepFocus.requestFocus() }
    }

    CompositionLocalProvider(LocalDeckEditTracker provides editTracker) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .deckPageBackground()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || editTracker.editing) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.ButtonB -> { back(); true }
                        Key.ButtonL1 -> { goTo(step - 1); true }
                        Key.ButtonR1 -> { goTo(step + 1); true }
                        else -> false
                    }
                },
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(DeckPalette.primary.copy(alpha = 0.16f), Color.Transparent),
                        radius = 1200f,
                    ),
                ),
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (wide) 40.dp else 20.dp, vertical = if (wide) 22.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    WizardHeader(step, wide)
                    Box(Modifier.weight(1f).fillMaxWidth().focusRequester(stepFocus).focusGroup()) {
                        StepBody(vm, hub, step, wide)
                    }
                    WizardFooter(step, wide, extras.size, onBack = { back() }, onNext = { next() })
                }
            }
        }
    }
}

// ── Chrome ────────────────────────────────────────────────────────────────────

@Composable
private fun WizardHeader(step: Int, wide: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "WINHUB",
            color = DeckPalette.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 3.sp,
            modifier = Modifier.weight(1f),
        )
        WIZARD_STEPS.forEachIndexed { i, label ->
            val done = i < step
            val on = i == step
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp)
                            .size(width = if (wide) 22.dp else 10.dp, height = 2.dp)
                            .background(if (i <= step) DeckPalette.primary else DeckPalette.line2),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                on -> DeckPalette.primary
                                done -> DeckPalette.primary.copy(alpha = 0.16f)
                                else -> DeckPalette.surface
                            },
                        )
                        .border(1.dp, if (on || done) DeckPalette.primary else DeckPalette.line2, CircleShape),
                ) {
                    if (done) {
                        Icon(Icons.Filled.Check, null, tint = DeckPalette.primary, modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            "${i + 1}",
                            color = if (on) DeckPalette.onPrimary else DeckPalette.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (wide) {
                    Text(
                        label,
                        color = if (on) DeckPalette.onBackground else DeckPalette.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardFooter(step: Int, wide: Boolean, extraCount: Int, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (wide) {
            DeckHintBar(
                hints = listOf("A" to "Select", "B" to "Back", "LB RB" to "Step"),
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = if (step == CONTENTS) "$extraCount extra${if (extraCount == 1) "" else "s"} selected" else "",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (step > 0) DeckButton("Back", primary = false, onClick = onBack)
        DeckButton(if (step == LAST_STEP) "Install" else "Next", primary = true, onClick = onNext)
    }
}

@Composable
private fun StepBody(vm: FirstRunViewModel, hub: ContentsHubViewModel, step: Int, wide: Boolean) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val orientation = remember(ctx) { mutableStateOf(initialOrientation(ctx, config.screenWidthDp > config.screenHeightDp)) }
    val portraitChosen = orientation.value == AppOrientation.PORTRAIT
    val extras by vm.extras.collectAsState()

    val left: @Composable (Modifier) -> Unit = { mod ->
        when (step) {
            LAYOUT -> LayoutStep(orientation.value, mod) { mode ->
                orientation.value = mode
                AppOrientation.setMode(ctx, mode)
                ctx.findActivity()?.let { AppOrientation.apply(it) }
            }
            THEME -> ThemeStep(wide, mod)
            CONTENTS -> ContentsStep(vm, hub, mod)
            else -> SocialStep(vm, mod)
        }
    }
    val right: @Composable (Modifier) -> Unit = { mod ->
        when (step) {
            LAYOUT, THEME -> MiniDeckPreview(portrait = portraitChosen, compact = !wide, modifier = mod)
            CONTENTS -> InstallPlan(extras.values.toList(), mod)
            else -> SocialPerks(mod)
        }
    }

    if (wide) {
        val rightWeight = if (step == CONTENTS) 0.8f else 1f
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp), modifier = Modifier.fillMaxSize()) {
            left(Modifier.weight(if (step == CONTENTS) 1.5f else 1.05f).fillMaxHeight())
            right(Modifier.weight(rightWeight).fillMaxHeight())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            if (step == LAYOUT || step == THEME) right(Modifier.fillMaxWidth().height(180.dp))
            left(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun StepHeading(step: Int, title: String, lede: String, recommended: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "STEP ${step + 1} OF ${WIZARD_STEPS.size}",
                color = DeckPalette.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
            )
            if (recommended) DeckBadge("Recommended", DeckPalette.primary)
        }
        Text(title, color = DeckPalette.onBackground, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(lede, color = DeckPalette.onSurfaceVariant, fontSize = 15.sp)
    }
}

// ── Step 1: layout ────────────────────────────────────────────────────────────

@Composable
private fun LayoutStep(selected: String, modifier: Modifier, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier.verticalScroll(rememberScrollState())) {
        StepHeading(LAYOUT, "How do you play?", "Pick the layout that fits how you hold your device. The preview shows the Deck home.")
        WizChoice(
            title = "Landscape",
            body = "Controller, dock or TV. Side rail, wide grid, button hints along the bottom.",
            selected = selected == AppOrientation.LANDSCAPE,
            onClick = { onPick(AppOrientation.LANDSCAPE) },
        ) { OrientationGlyph(portrait = false) }
        WizChoice(
            title = "Portrait",
            body = "One hand on a phone. Chips replace the rail, big touch targets.",
            selected = selected == AppOrientation.PORTRAIT,
            onClick = { onPick(AppOrientation.PORTRAIT) },
        ) { OrientationGlyph(portrait = true) }
        Text("Change it any time in Settings, App Orientation.", color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun OrientationGlyph(portrait: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DeckPalette.surfaceVariant)
            .border(1.dp, DeckPalette.line2, RoundedCornerShape(12.dp)),
    ) {
        Box(
            Modifier
                .size(width = if (portrait) 18.dp else 30.dp, height = if (portrait) 30.dp else 18.dp)
                .border(2.dp, DeckPalette.primary, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun WizChoice(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .deckRing(interaction, shape)
            .clip(shape)
            .background(if (selected) DeckPalette.primary.copy(alpha = 0.14f) else DeckPalette.surface)
            .border(1.dp, if (selected) DeckPalette.primary else DeckPalette.line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckPalette.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(body, color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
        }
        Tick(selected)
    }
}

@Composable
private fun Tick(on: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (on) DeckPalette.primary else Color.Transparent)
            .border(2.dp, if (on) DeckPalette.primary else DeckPalette.line2, CircleShape),
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = DeckPalette.onPrimary, modifier = Modifier.size(15.dp))
    }
}

// ── Step 2: theme ─────────────────────────────────────────────────────────────

@Composable
private fun ThemeStep(wide: Boolean, modifier: Modifier) {
    val selected by AppThemeState.presetIndex.collectAsState()
    val customAccent by AppThemeState.customAccent.collectAsState()
    val columns = if (wide) 4 else 2
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier.verticalScroll(rememberScrollState())) {
        StepHeading(THEME, "Pick your look", "The whole app follows this theme. This screen already does, so try a few.")
        themePresets.withIndex().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (index, preset) ->
                    val isCustom = index == CUSTOM_PRESET_INDEX
                    ThemeCard(
                        name = preset.name,
                        colors = if (isCustom) listOf(preset.surface, preset.line, customAccent)
                        else listOfNotNull(preset.surface, preset.line, preset.primary, preset.secondary.takeIf { it != preset.primary }),
                        background = preset.background,
                        selected = selected == index,
                        modifier = Modifier.weight(1f),
                    ) { if (isCustom) AppThemeState.setCustomAccent(customAccent) else AppThemeState.setPreset(index) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (selected == CUSTOM_PRESET_INDEX) {
            ColorPicker(initialColor = customAccent, onColorChanged = { AppThemeState.setCustomAccent(it) })
        }
        Text("Change it any time in Settings.", color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun ThemeCard(
    name: String,
    colors: List<Color>,
    background: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .deckRing(interaction, shape)
            .clip(shape)
            .background(if (selected) DeckPalette.primary.copy(alpha = 0.14f) else DeckPalette.surface)
            .border(1.dp, if (selected) DeckPalette.primary else DeckPalette.line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .padding(6.dp),
        ) {
            colors.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = DeckPalette.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Icon(Icons.Filled.Check, null, tint = DeckPalette.primary, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Preview device (steps 1 and 2) ────────────────────────────────────────────

private val PREVIEW_COVERS = listOf(
    0xFF6A3D7A to 0xFF1D2A52, 0xFFC26B3A to 0xFF3A1D52, 0xFF2E6F8E to 0xFF10243A, 0xFF8E3A52 to 0xFF2A1020,
    0xFF3A8E5A to 0xFF0F2A1C, 0xFF8E7A3A to 0xFF2A2210, 0xFF4A3A8E to 0xFF14102A, 0xFF8E3A3A to 0xFF2A1010,
)

@Composable
private fun MiniDeckPreview(portrait: Boolean, compact: Boolean, modifier: Modifier) {
    val ratio by animateFloatAsState(if (portrait) 9f / 19f else 16f / 9f, tween(320), label = "previewRatio")
    val landscapeHeight = if (compact) 120.dp else 230.dp
    val portraitHeight = if (compact) 170.dp else 330.dp
    val height by animateDpAsState(if (portrait) portraitHeight else landscapeHeight, tween(320), label = "previewHeight")
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .height(height)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF050607))
                    .border(1.dp, DeckPalette.line2, RoundedCornerShape(22.dp))
                    .padding(7.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeckPalette.background),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
                        Text("WINHUB", color = DeckPalette.onBackground, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        repeat(if (portrait) 2 else 4) { i ->
                            Box(
                                Modifier
                                    .padding(start = 3.dp)
                                    .size(width = 18.dp, height = 8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (i == 0) DeckPalette.primary.copy(alpha = 0.3f) else DeckPalette.surface),
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DeckPalette.line))
                    Row(Modifier.weight(1f).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (!portrait) {
                            Column(Modifier.width(34.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(4) { i ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(11.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (i == 0) DeckPalette.primary.copy(alpha = 0.25f) else DeckPalette.surface),
                                    )
                                }
                            }
                        }
                        val cols = if (portrait) 2 else 4
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PREVIEW_COVERS.take(cols * 2).chunked(cols).forEachIndexed { r, rowCovers ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    rowCovers.forEachIndexed { c, (a, b) ->
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .aspectRatio(3f / 4f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Brush.linearGradient(listOf(Color(a), Color(b))))
                                                .then(if (r == 0 && c == 0) Modifier.border(2.dp, DeckPalette.primary, RoundedCornerShape(6.dp)) else Modifier),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!portrait) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().background(DeckPalette.surface).padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            repeat(3) { Box(Modifier.size(width = 22.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(DeckPalette.line2)) }
                        }
                    }
                }
            }
            Text(
                if (portrait) "PORTRAIT" else "LANDSCAPE",
                color = DeckPalette.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

// ── Step 3: extra contents ────────────────────────────────────────────────────

@Composable
private fun ContentsStep(vm: FirstRunViewModel, hub: ContentsHubViewModel, modifier: Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        StepHeading(
            CONTENTS, "Extra contents",
            "The same browser as the Contents tab. Proton 9, DXVK, FEXCore and VKD3D are always installed. Tick anything else you want now.",
        )
        ContentsBrowser(vm, hub, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun ContentsBrowser(vm: FirstRunViewModel, hub: ContentsHubViewModel, modifier: Modifier) {
    val query by hub.query.collectAsState()
    val results by hub.searchResults.collectAsState()
    val searching by hub.searching.collectAsState()
    val category by hub.categoryType.collectAsState()
    val catalog by hub.categoryItems.collectAsState()
    val loading by hub.categoryLoading.collectAsState()
    val sources by hub.sources.collectAsState()
    val repo by vm.repo.collectAsState()
    val extras by vm.extras.collectAsState()
    val pinned = remember { FIRST_RUN_CONTENTS.map { it.url }.toSet() }

    Column(modifier) {
        DeckSearchField(query) { hub.setQuery(it) }
        Spacer(Modifier.height(10.dp))
        when {
            query.isNotBlank() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (searching) "Searching..." else "${results.size} result${if (results.size == 1) "" else "s"}",
                        color = DeckPalette.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    DeckIconAction(Icons.Filled.Close, "Clear search") { hub.setQuery("") }
                }
                Spacer(Modifier.height(8.dp))
                if (searching) BrowserNote("Loading...")
                else if (results.isEmpty()) BrowserNote("No component matches \"$query\".")
                else CatalogList(results, true, pinned, extras, vm)
            }
            category != null -> {
                val title = deckContentCategories.firstOrNull { it.type == category }?.title ?: category.orEmpty()
                val repoItems = if (repo == null) catalog else catalog.filter { it.sourceName == repo }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeckIconAction(Icons.Filled.ArrowBack, if (repo == null) "All component families" else "Back to repositories") {
                        if (repo == null) hub.selectCategory(null) else vm.repo.value = null
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (repo == null) title else "$title - $repo",
                            color = DeckPalette.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (repo == null) "Choose a repository"
                            else "${repoItems.size} release${if (repoItems.size == 1) "" else "s"}",
                            color = DeckPalette.onSurfaceVariant, fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> BrowserNote("Loading...")
                    catalog.isEmpty() -> BrowserNote("No releases found for $title.")
                    repo == null -> {
                        val names = catalog.map { it.sourceName }.toSet()
                        val driverFamily = ContentsTypes.isDriver(category.orEmpty())
                        val repos = sources.filter { it.name in names && it.driverOnly == driverFamily }
                        if (repos.isEmpty()) BrowserNote("No repository offers $title.")
                        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                            items(repos, key = { "${it.name}-${it.driverOnly}" }) { source ->
                                WizSourceCard(source) { vm.repo.value = source.name }
                            }
                        }
                    }
                    else -> CatalogList(repoItems, false, pinned, extras, vm)
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(deckContentCategories, key = { it.type }) { entry ->
                    DeckCategoryCard(entry) { vm.repo.value = null; hub.selectCategory(entry.type) }
                }
            }
        }
    }
}

@Composable
private fun CatalogList(
    list: List<ContentsHubViewModel.CatalogItem>,
    showSource: Boolean,
    pinned: Set<String>,
    picked: Map<String, ExtraPick>,
    vm: FirstRunViewModel,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        items(list, key = { "${it.sourceName}-${it.type}-${it.downloadUrl}" }) { item ->
            val title = catalogTitle(item)
            WizCatalogCard(
                item = item,
                title = title,
                showSource = showSource,
                included = item.downloadUrl in pinned,
                checked = item.downloadUrl in picked,
            ) { vm.toggle(item, title) }
        }
    }
}

private fun catalogTitle(item: ContentsHubViewModel.CatalogItem): String {
    val name = item.displayName.trim()
    val version = item.versionName.trim()
    return if (version.isEmpty() || name == version || name.contains(version, ignoreCase = true)) name else "$name $version"
}

@Composable
private fun BrowserNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DeckPalette.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))
    }
}

@Composable
private fun WizSourceCard(source: HubSource, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .deckRing(interaction, shape)
            .clip(shape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(DeckPalette.primary.copy(alpha = 0.12f)),
        ) {
            Icon(
                if (source.driverOnly) Icons.Filled.Memory else Icons.Filled.Folder,
                null, tint = DeckPalette.primary, modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    source.name, color = DeckPalette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (source.isOfficial) {
                    Spacer(Modifier.width(8.dp))
                    DeckBadge("Official", DeckPalette.primary)
                }
            }
            Text(source.displayFormat, color = DeckPalette.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (source.typePills.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    source.typePills.take(4).forEach { DeckBadge(it, DeckPalette.onSurfaceVariant) }
                    if (source.typePills.size > 4) DeckBadge("+${source.typePills.size - 4}", DeckPalette.onSurfaceVariant)
                }
            }
        }
    }
}

/** The Contents tab's catalog card with a checkbox where Download & Install would be. */
@Composable
private fun WizCatalogCard(
    item: ContentsHubViewModel.CatalogItem,
    title: String,
    showSource: Boolean,
    included: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    val on = checked || included
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .deckRing(interaction, shape)
            .clip(shape)
            .background(if (on) DeckPalette.primary.copy(alpha = 0.08f) else DeckPalette.surface)
            .border(1.dp, if (on) DeckPalette.primary else DeckPalette.line, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = !included, onClick = onToggle)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (item.isDriver) DeckPalette.primary.copy(alpha = 0.14f) else DeckPalette.surfaceVariant),
            ) {
                Icon(
                    deckContentTypeIcon(item.type), null,
                    tint = if (item.isDriver) DeckPalette.primary else DeckPalette.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (showSource) {
                    Text(
                        item.sourceName.uppercase(), color = DeckPalette.primary, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(title, color = DeckPalette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeckMeta(Icons.Filled.Inventory2, item.fileName)
                    item.sizeBytes?.let { DeckMeta(Icons.Filled.SdStorage, RemoteSourceRepository.formatFileSize(it)) }
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) DeckPalette.primary else Color.Transparent)
                    .border(2.dp, if (on) DeckPalette.primary else DeckPalette.line2, RoundedCornerShape(8.dp)),
            ) {
                if (on) Icon(Icons.Filled.Check, null, tint = DeckPalette.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckBadge(item.type, DeckPalette.onSurfaceVariant)
            if (included) DeckBadge("Included", DeckPalette.good)
        }
    }
}

@Composable
private fun InstallPlan(picked: List<ExtraPick>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.surface)
                .border(1.dp, DeckPalette.line, DeckPalette.groupShape)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text("INSTALL PLAN", color = DeckPalette.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                Text("${1 + FIRST_RUN_CONTENTS.size + picked.size} items", color = DeckPalette.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            PlanRow("System files", "Always", false)
            FIRST_RUN_CONTENTS.forEach { PlanRow(it.label, "Included", false) }
            picked.forEach { PlanRow(it.content.label, "${it.type} from ${it.source}", true) }
            if (picked.isEmpty()) {
                Text("No extras selected. You can add some later in Contents.", color = DeckPalette.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Text("Needs an internet connection. Wi-Fi recommended.", color = DeckPalette.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun PlanRow(label: String, tag: String, extra: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (extra) DeckPalette.primary else DeckPalette.primary.copy(alpha = 0.16f))
                .border(1.dp, DeckPalette.primary, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = DeckPalette.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(tag, color = DeckPalette.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
    }
}

// ── Step 4: Social Hub ────────────────────────────────────────────────────────

@Composable
private fun SocialStep(vm: FirstRunViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    val account by SocialHubStore.account.collectAsState()
    val login by SocialHubStore.loginState.collectAsState()
    val skipped by vm.socialSkipped.collectAsState()
    LaunchedEffect(Unit) { SocialHubStore.init(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier.verticalScroll(rememberScrollState())) {
        StepHeading(SOCIAL, "Connect to Social Hub", "Find configs that already work on your device, and share yours.", recommended = true)
        val acct = account
        when {
            acct != null -> ConnectedCard(acct) { SocialHubStore.signOut() }
            login is SocialHubStore.LoginState.Requesting -> StatusCard("Asking GitHub for a code...", null)
            login is SocialHubStore.LoginState.AwaitingUser -> {
                val waiting = login as SocialHubStore.LoginState.AwaitingUser
                CodeCard(waiting, onOpen = { openUrl(ctx, waiting.verificationUri) }, onCancel = { SocialHubStore.cancelLogin() })
            }
            login is SocialHubStore.LoginState.Failed -> StatusCard(
                (login as SocialHubStore.LoginState.Failed).message, "Try again",
            ) { SocialHubStore.beginLogin() }
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (skipped) {
                    Text("Skipped for now. You can connect any time from the Social tab.", color = DeckPalette.onSurfaceVariant, fontSize = 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeckButton("Connect with GitHub", primary = true) { vm.socialSkipped.value = false; SocialHubStore.beginLogin() }
                    if (!skipped) DeckButton("Not now", primary = false) { vm.socialSkipped.value = true }
                }
            }
        }
        Text(
            "Sign-in goes through GitHub. WinHub can only comment on issues in the public social-hub repo, nothing else.",
            color = DeckPalette.onSurfaceVariant, fontSize = 13.sp,
        )
    }
}

@Composable
private fun ConnectedCard(account: GitHubAuthClient.Account, onDisconnect: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(DeckPalette.groupShape)
                .background(DeckPalette.good.copy(alpha = 0.10f))
                .border(1.dp, DeckPalette.good.copy(alpha = 0.45f), DeckPalette.groupShape)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Check, null, tint = DeckPalette.good, modifier = Modifier.size(26.dp))
            Column {
                Text("Connected as @${account.login}", color = DeckPalette.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Configs, posts and votes are ready.", color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        DeckButton("Disconnect", primary = false, onClick = onDisconnect)
    }
}

@Composable
private fun CodeCard(state: SocialHubStore.LoginState.AwaitingUser, onOpen: () -> Unit, onCancel: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeckPalette.groupShape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line2, DeckPalette.groupShape)
            .padding(16.dp),
    ) {
        Text("On any device, open ${state.verificationUri.removePrefix("https://")} and enter:", color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
        Text(state.userCode, color = DeckPalette.primary, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
        Text(state.status, color = DeckPalette.onSurfaceVariant, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeckButton("Open GitHub", primary = true, onClick = onOpen)
            DeckButton("Cancel", primary = false, onClick = onCancel)
        }
    }
}

@Composable
private fun StatusCard(message: String, action: String?, onAction: () -> Unit = {}) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeckPalette.groupShape)
            .background(DeckPalette.surface)
            .border(1.dp, DeckPalette.line2, DeckPalette.groupShape)
            .padding(16.dp),
    ) {
        Text(message, color = DeckPalette.onBackground, fontSize = 14.sp)
        if (action != null) DeckButton(action, primary = true, onClick = onAction)
    }
}

@Composable
private fun SocialPerks(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center) {
        DeckGroup("What you get") {
            DeckRow("Import configs", "Community settings for your game and hardware, in one tap.") {}
            DeckRow("Share your own", "Publish what works and see who used it.") {}
            DeckRow("Join in", "Post, reply, vote and follow what is working right now.") {}
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun initialOrientation(context: Context, landscapeNow: Boolean): String =
    when (AppOrientation.mode(context)) {
        AppOrientation.PORTRAIT -> AppOrientation.PORTRAIT
        AppOrientation.LANDSCAPE -> AppOrientation.LANDSCAPE
        else -> if (landscapeNow) AppOrientation.LANDSCAPE else AppOrientation.PORTRAIT
    }

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser to open GitHub with.", Toast.LENGTH_SHORT).show()
    }
}
